"""Random-play driver for the real-game "SpireMentor" mod (real-game bridge phase 0).

Connects to the bridge mod (:mod:`realgame_bridge`), plays a full run using a
random-but-legal policy for every decision type in ``PROTOCOL.md``, logs every
``(state, action)`` pair to a JSONL file, and prints a one-line progress
summary per decision. Phase-0 gate: this driver should be able to finish a
full run against the live game without desyncing the protocol.

Usage::

    python python/realgame_random.py [--host 127.0.0.1] [--port 9002]
        [--log-dir logs/realgame] [--max-steps N] [--seed N]
        [--character IRONCLAD] [--ascension 0]

``--character``/``--ascension`` only matter if the mod's
``client_run_config`` flag is enabled (see the ``run_config`` bullet below);
``--seed`` doubles as both the random policy's RNG seed and the run seed
offered in ``start_run`` (so a full run is reproducible end-to-end from one
flag).

Random policy per message type (see module docstrings on each ``pick_*``
function for the exact rule and any ambiguity resolutions):
  - ``combat_action``: 15% chance (per decision) to use a usable potion —
    a random valid target if it needs one, occasionally (20% of those)
    discarding instead — else 85% chance to play a random playable card
    (preferring per-card ``valid_target_indices`` when present, falling back
    to any alive enemy), else end the turn.
  - ``map_select``: uniformly random node.
  - ``card_reward``: 70% chance to take a random card, else skip if allowed.
  - ``card_select`` (and any message with ``min_count``/``max_count`` or the
    legacy ``min_select``/``max_select``): 10% chance to ``cancel``/``skip``
    when ``cancelable``, else a random legal-size subset via
    ``{"action":"choose","indices":[...]}`` respecting the bounds (a single
    ``{"action":"choose","index":N}`` when no bounds are present at all).
  - ``card_bundle``: uniformly random *enabled* bundle.
  - ``crystal_sphere``, ``rest_site``, ``shop``, ``event``, ``treasure``,
    ``boss_relic``, ``reward_screen``: uniformly random *enabled* option
    (this naturally reduces to the proceed/leave option whenever it is the
    only one enabled, which covers the "prefer proceed when nothing else is
    available" requirement without special-casing it).
  - ``run_config`` (only sent when ``client_run_config`` is on): replies with
    ``start_run`` using ``--character``/``--ascension`` if given, else the
    ``run_config`` message's own ``defaults``; ``seed`` is always
    ``--seed``'s value (``None`` unless explicitly passed — the mod then
    generates a fresh random seed, matching the old config-driven flow).
  - ``error`` (phase 1b, fire-and-forget): logged to the JSONL as valuable
    corpus, but **not** counted as a step decision — the loop just continues
    and picks up the fresh re-send of the rejected decision on the next
    ``receive_state()``.
  - Unknown message type: logged loudly (``logger.error``) and answered with
    a best-effort ``{"action": "choose", "index": 0}``. The protocol gives no
    way to detect a rejected action from the response stream (the next
    message is just the next state), so there is no automatic
    fall-back-to-``skip``-on-error retry here — if ``choose``/``index: 0``
    is wrong for a message type we don't recognize, that will show up as a
    stall in the driver's progress output, at which point PROTOCOL.md and
    this file need to be updated together.
"""

from __future__ import annotations

import argparse
import itertools
import json
import logging
import os
import random
import sys
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from combat_mirror import skip_arity_from_world
from realgame_bridge import DEFAULT_HOST, DEFAULT_PORT, RealGameBridgeClient

logger = logging.getLogger(__name__)

# Message types that use the shared "list of options, pick one enabled index"
# shape documented under "choice option states" in PROTOCOL.md.
_OPTION_CHOICE_TYPES = frozenset(
    {
        "crystal_sphere",
        "rest_site",
        "shop",
        "event",
        "treasure",
        "boss_relic",
        "reward_screen",
    }
)

# Card/potion target_type strings (per PROTOCOL.md's Card/Potion object
# schemas) that require the player to choose a specific enemy. "RandomEnemy"
# is resolved by the game itself despite the name, and
# "AllEnemies"/"Self"/"None" take no target.
_NEEDS_ENEMY_TARGET = frozenset({"AnyEnemy"})

# Probabilities for the random policy's occasional "exercise a less-common
# branch" behavior (all independent, all per relevant decision).
_POTION_USE_PROBABILITY = 0.15  # of combat_action decisions with a usable potion
_POTION_DISCARD_PROBABILITY = 0.2  # of those potion decisions
_CARD_SELECT_CANCEL_PROBABILITY = 0.10  # of cancelable card_select decisions


def _alive_enemy_indices(state: dict[str, Any]) -> list[int]:
    """Return indices of enemies with ``is_alive`` true (default true)."""
    enemies = state.get("enemies") or []
    return [i for i, e in enumerate(enemies) if e.get("is_alive", True)]


def _usable_potions(state: dict[str, Any]) -> list[dict[str, Any]]:
    """Return ``run_state.potions``/``combat_action.potions`` slots that are
    non-empty and usable right now (``can_use: true``, per the Potion object
    schema — ``usage != "Automatic"``)."""
    potions = state.get("potions") or []
    return [p for p in potions if not p.get("empty") and p.get("can_use")]


def pick_potion_action(
    potion: dict[str, Any], alive: list[int], rng: random.Random
) -> dict[str, Any] | None:
    """Pick a ``potion`` action for an already-chosen usable potion slot.

    ``_POTION_DISCARD_PROBABILITY`` of the time, discards instead of using.
    Otherwise uses it: a random alive-enemy target if ``requires_target``
    (``target_type == "AnyEnemy"``), no ``target_index`` otherwise (the mod
    auto-targets ``Self``/``AnyPlayer``/``AnyAlly`` potions at the player,
    per PROTOCOL.md).

    Returns:
        The action dict, or ``None`` if the potion needs an enemy target and
        none is alive (caller should fall back to a card play/end_turn).
    """
    slot = potion["slot"]
    if rng.random() < _POTION_DISCARD_PROBABILITY:
        return {"action": "potion", "slot": slot, "discard": True}

    if potion.get("requires_target"):
        if not alive:
            return None
        return {"action": "potion", "slot": slot, "target_index": rng.choice(alive)}
    return {"action": "potion", "slot": slot}


def pick_combat_action(state: dict[str, Any], rng: random.Random) -> dict[str, Any]:
    """Pick a random legal combat action.

    ``_POTION_USE_PROBABILITY`` chance to use/discard a random usable potion
    (see :func:`pick_potion_action`); otherwise 85% chance to play a random
    playable card, else end the turn.

    Card legality prefers per-card fields when present: only cards with
    ``playable: true`` are candidates, and an ``AnyEnemy``-target card's
    ``valid_target_indices`` (when the field is present, even if empty) is
    used verbatim instead of "any alive enemy" — this matches
    PROTOCOL.md's `hand[].valid_target_indices`, which is itself already
    filtered through `CardModel.IsValidTarget` and can be narrower than
    "alive" (e.g. untargetable-while-stealthed effects).
    """
    alive = _alive_enemy_indices(state)

    usable_potions = _usable_potions(state)
    if usable_potions and rng.random() < _POTION_USE_PROBABILITY:
        potion_action = pick_potion_action(rng.choice(usable_potions), alive, rng)
        if potion_action is not None:
            return potion_action
        # Fell through: the chosen potion needed a target with none alive —
        # try a card/end_turn instead rather than stalling the decision.

    hand = state.get("hand") or []

    def _card_targets(card: dict[str, Any]) -> list[int] | None:
        """Legal AnyEnemy targets for ``card``, or None if untargeted."""
        if card.get("target_type") not in _NEEDS_ENEMY_TARGET:
            return None
        valid = card.get("valid_target_indices")
        return alive if valid is None else valid

    candidates = [
        i for i, card in enumerate(hand) if card.get("playable") and _card_targets(card) != []
    ]

    if candidates and rng.random() < 0.85:
        card_index = rng.choice(candidates)
        card = hand[card_index]
        targets = _card_targets(card)
        action: dict[str, Any] = {"action": "play", "card_index": card_index}
        if targets is None:
            action["target_index"] = -1
        else:
            action["target_index"] = rng.choice(targets)
        return action

    return {"action": "end_turn"}


#: ``--explore-map`` node-type priorities: prefer the screens a weak random
#: agent rarely survives to see (rest/treasure/shop), then unknowns/events,
#: over plain fights. Types missing from the table rank lowest.
_MAP_EXPLORE_PRIORITY = {
    "RestSite": 3,
    "Treasure": 3,
    "Shop": 3,
    "Unknown": 2,
    "Event": 2,
    "Boss": 1,
    "Elite": 1,
    "Monster": 0,
}


def pick_map_select(
    state: dict[str, Any],
    rng: random.Random,
    *,
    explore: bool = False,
) -> dict[str, Any]:
    """Pick an offered map node — uniformly random, or screen-coverage-biased.

    Args:
        state: The ``map_select`` decision message.
        rng: Seeded RNG source.
        explore: When true, restrict the random pick to the offered nodes
            whose type has the highest :data:`_MAP_EXPLORE_PRIORITY` (ties
            broken uniformly) instead of picking uniformly over all nodes.
    """
    nodes = state.get("nodes") or []
    if not nodes:
        logger.error("map_select with no nodes offered: %s", state)
        return {"action": "choose", "index": 0}
    if explore:
        priority = [_MAP_EXPLORE_PRIORITY.get(str(n.get("type")), 0) for n in nodes]
        best = max(priority)
        candidates = [i for i, p in enumerate(priority) if p == best]
        pos = rng.choice(candidates)
        return {"action": "choose", "index": int(nodes[pos].get("index", pos))}
    return {"action": "choose", "index": rng.randrange(len(nodes))}


def pick_card_reward(state: dict[str, Any], rng: random.Random) -> dict[str, Any]:
    """70% chance to take a random card reward, else skip (if allowed)."""
    cards = state.get("cards") or []
    can_skip = bool(state.get("can_skip"))

    if cards and rng.random() < 0.7:
        return {"action": "choose", "index": rng.randrange(len(cards))}
    if can_skip:
        return {"action": "skip"}
    if cards:
        # Can't skip and the coin flip missed — still must pick something.
        return {"action": "choose", "index": rng.randrange(len(cards))}
    logger.error("card_reward with no cards and can_skip=False: %s", state)
    return {"action": "skip"}


def pick_card_select(state: dict[str, Any], rng: random.Random) -> dict[str, Any]:
    """Pick card(s), respecting bounds and occasionally canceling.

    ``_CARD_SELECT_CANCEL_PROBABILITY`` of the time, if ``cancelable``,
    replies with ``cancel`` (phase 1b spelling) or its pre-existing alias
    ``skip`` instead of a choice. Otherwise picks a random legal-size subset
    respecting ``min_count``/``max_count`` (phase 1b aliases of
    ``min_select``/``max_select``, checked first) via the preferred
    ``{"action":"choose","indices":[...]}`` spelling — or a single
    ``{"action":"choose","index":N}`` when the message carries no
    min/max bounds at all (e.g. a plain deck-view single pick).
    """
    cards = state.get("cards") or []
    n = len(cards)

    min_n = state.get("min_count", state.get("min_select"))
    max_n = state.get("max_count", state.get("max_select"))

    # A bounds-less message is the plain single-pick deck view: no arity, no
    # decline. Settled before the cancel roll so a message that says nothing
    # cannot be talked into a cancel by the normalization below.
    if min_n is None and max_n is None:
        if not cards:
            logger.error("card_select with no cards: %s", state)
            return {"action": "choose", "index": 0}
        return {"action": "choose", "index": rng.randrange(n)}

    # `cancelable` is the world's RAW capture and does not on its own mean
    # "may answer with nothing" — see `combat_mirror.skip_arity_from_world`
    # for why the rule is entry-point-specific (a mandatory
    # FromChooseACardScreen reports min 0 and must still be answered). Shared
    # with both mirrored drivers so the random policy cannot explore a reply
    # the searching drivers consider illegal, or vice versa.
    may_decline, min_normalized = skip_arity_from_world(
        cancelable=state.get("cancelable"),
        source=state.get("source"),
        min_select=int(min_n) if min_n is not None else 0,
        max_select=int(max_n) if max_n is not None else n,
    )
    if may_decline and rng.random() < _CARD_SELECT_CANCEL_PROBABILITY:
        return {"action": rng.choice(["cancel", "skip"])}
    if min_n is not None:
        min_n = min_normalized

    lo = max(0, int(min_n) if min_n is not None else 1)
    hi = min(n, int(max_n) if max_n is not None else max(lo, n))
    hi = max(hi, lo)
    hi = min(hi, n)
    lo = min(lo, hi)
    k = rng.randint(lo, hi) if hi > lo else lo
    indices = sorted(rng.sample(range(n), k)) if k else []
    return {"action": "choose", "indices": indices}


def pick_card_bundle(state: dict[str, Any], rng: random.Random) -> dict[str, Any]:
    """Pick a uniformly random enabled bundle (by its position in ``bundles``)."""
    bundles = state.get("bundles") or []
    enabled = [i for i, b in enumerate(bundles) if b.get("enabled", True)]
    if not enabled:
        logger.error("card_bundle with no enabled bundles: %s", state)
        return {"action": "skip"}
    return {"action": "choose", "index": rng.choice(enabled)}


def pick_option_choice(state: dict[str, Any], rng: random.Random) -> dict[str, Any]:
    """Pick a uniformly random enabled option.

    Shared by ``crystal_sphere``, ``rest_site``, ``shop``, ``event``,
    ``treasure``, ``boss_relic``, and ``reward_screen``. When the only
    enabled option is a proceed/leave action, uniform-random selection
    naturally picks it — satisfying "prefer proceed when nothing else is
    enabled" without needing to special-case action names.

    Options the world flags ``will_kill_player`` are withheld unless every
    option is flagged — the same rule the shipped game's own random auto-player
    applies (``AutoSlay.EventRoomHandler`` filters on
    ``o.Option.WillKillPlayer(o.Event.Owner)`` and keeps the filtered list only
    ``if (list3.Count > 0)``). Only ``event`` carries the flag today; for every
    other surface the filter is a no-op.

    ``is not True`` rather than ``is False`` on purpose: an ABSENT or ``None``
    flag means the world did not tell us (a bridge dialect that predates the
    field, or a probe that threw), and this chooser has no HP-cost parse to fall
    back on. Withholding on unknown would silently narrow every pre-flag
    dialect's option set to nothing meaningful; keeping is what AutoSlay does
    for its own two null cases (``WillKillPlayer == null || Owner == null``).
    """
    options = state.get("options") or []
    enabled = [i for i, o in enumerate(options) if o.get("enabled", True)]
    if not enabled:
        logger.error("%s with no enabled options: %s", state.get("type"), state)
        return {"action": "skip"}
    survivors = [i for i in enabled if options[i].get("will_kill_player") is not True]
    if survivors:
        enabled = survivors
    return {"action": "choose", "index": rng.choice(enabled)}


def pick_run_config(
    state: dict[str, Any],
    character: str | None = None,
    ascension: int | None = None,
    seed: int | None = None,
) -> dict[str, Any]:
    """Reply to a ``run_config`` message with a ``start_run`` action.

    ``character``/``ascension`` fall back to ``run_config.defaults`` (and,
    failing that, the first entry in ``run_config.characters``/ascension 0)
    when not given explicitly — normally these come straight from the CLI's
    ``--character``/``--ascension`` args. ``seed`` has no such fallback: per
    the driver's CLI, it is ``None`` unless the caller explicitly passed
    ``--seed``, matching PROTOCOL.md's "omitted -> fresh random seed"
    semantics for ``start_run``.
    """
    defaults = state.get("defaults") or {}
    chosen_character = character if character is not None else defaults.get("character")
    if chosen_character is None:
        characters = state.get("characters") or []
        if characters:
            chosen_character = characters[0].get("id")
            logger.warning(
                "run_config had no usable character default; falling back to %r",
                chosen_character,
            )
    chosen_ascension = ascension if ascension is not None else defaults.get("ascension", 0)
    return {
        "action": "start_run",
        "character": chosen_character,
        "ascension": chosen_ascension,
        "seed": seed,
    }


def choose_action(
    state: dict[str, Any],
    rng: random.Random,
    *,
    run_config_character: str | None = None,
    run_config_ascension: int | None = None,
    run_config_seed: int | None = None,
    map_explore: bool = False,
) -> dict[str, Any]:
    """Dispatch ``state`` to the matching random-but-legal policy.

    Args:
        state: A decoded state message (must have a ``"type"`` key).
        rng: Seeded RNG source — pass a fixed-seed ``random.Random`` in
            tests for determinism.
        run_config_character: ``--character`` CLI override for a
            ``run_config`` message's ``start_run`` reply (see
            :func:`pick_run_config`).
        run_config_ascension: ``--ascension`` CLI override, same deal.
        run_config_seed: ``--seed`` CLI value to offer as ``start_run``'s
            ``seed`` (``None`` -> fresh random seed).
        map_explore: ``--explore-map`` — bias map choices toward rarely
            seen screen types (see :func:`pick_map_select`).

    Returns:
        The action dict to send back (without ``request_id``/``state_id`` —
        the bridge client auto-echoes both in
        :meth:`RealGameBridgeClient.send_action`).
    """
    msg_type = state.get("type")

    if msg_type == "combat_action":
        return pick_combat_action(state, rng)
    if msg_type == "map_select":
        return pick_map_select(state, rng, explore=map_explore)
    if msg_type == "card_reward":
        return pick_card_reward(state, rng)
    if msg_type == "card_select":
        return pick_card_select(state, rng)
    if msg_type == "card_bundle":
        return pick_card_bundle(state, rng)
    if msg_type == "run_config":
        return pick_run_config(
            state,
            character=run_config_character,
            ascension=run_config_ascension,
            seed=run_config_seed,
        )
    if msg_type in _OPTION_CHOICE_TYPES:
        return pick_option_choice(state, rng)

    logger.error("Unknown message type %r, best-effort response: %s", msg_type, state)
    return {"action": "choose", "index": 0}


def _progress_line(step: int, state: dict[str, Any], action: dict[str, Any]) -> str:
    """Format a concise one-line progress summary for a decision."""
    msg_type = state.get("type", "?")
    floor = state.get("floor")
    act = state.get("act")
    loc = f" floor={floor} act={act}" if floor is not None or act is not None else ""

    player = state.get("player")
    hp = ""
    if isinstance(player, dict) and "hp" in player:
        hp = f" hp={player['hp']}/{player.get('max_hp', '?')}"

    return f"[{step:4d}] {msg_type}{loc}{hp} -> {action}"


class JsonlLogger:
    """Appends one JSON line per record to a timestamped run log."""

    # Process-local monotonic counter so two loggers opened in the same UTC
    # second (sequential short runs in one process) never collide.
    _counter = itertools.count()

    def __init__(self, log_dir: Path) -> None:
        """Create ``log_dir`` if needed and open a new UTC-timestamped file.

        The filename is ``{ts}_{pid}_{n}.jsonl``: the ``{ts}`` prefix keeps
        the timestamp strptime-able for ``shadow_replay.map_seeds_to_jsonl``'s
        window matching, while the ``_pid_n`` suffix makes the path unique
        across concurrent processes (e.g. ``scripts/shadow_hp_pilot.sh``'s 16
        parallel workers, all writing ``logs/headless``) and across
        same-second runs in one process. Previously the bare ``{ts}.jsonl``
        opened in ``"w"`` mode truncated a colliding sibling's file.

        Args:
            log_dir: Directory the log file is created in.
        """
        log_dir.mkdir(parents=True, exist_ok=True)
        ts = datetime.now(UTC).strftime("%Y%m%dT%H%M%SZ")
        self.path = log_dir / f"{ts}_{os.getpid()}_{next(self._counter)}.jsonl"
        self._file = self.path.open("w", encoding="utf-8")

    def log_step(
        self,
        step: int,
        state: dict[str, Any],
        action: dict[str, Any],
        *,
        extra: dict[str, Any] | None = None,
    ) -> None:
        """Append one ``{"step", "ts", "state", "action"}`` record.

        Args:
            step: Decision step counter.
            state: The decision message being answered.
            action: The action sent back.
            extra: Additional top-level fields merged into the record
                (e.g. ``{"mcts_error": "..."}`` — used by
                :mod:`realgame_mcts` to record an MCTS translation/
                recommendation failure on the step where it fell back to
                the random-legal policy). ``None`` (default) adds nothing,
                matching the pre-existing record shape exactly.
        """
        entry = {
            "step": step,
            "ts": datetime.now(UTC).isoformat(),
            "state": state,
            "action": action,
        }
        if extra:
            entry.update(extra)
        self._write(entry)

    def log_terminal(self, step: int, state: dict[str, Any]) -> None:
        """Append the terminal ``game_over``/``run_complete`` state (no action)."""
        entry = {
            "step": step,
            "ts": datetime.now(UTC).isoformat(),
            "state": state,
            "action": None,
        }
        self._write(entry)

    def log_meta(self, **fields: Any) -> None:
        """Append a non-decision ``{"step": None, "ts", "meta"}`` header record.

        Lets a log file name its own run (seed/character/ascension) so
        downstream readers don't have to infer it from filename ordering —
        which breaks under parallel workers all writing one log dir. Carries
        no ``state``/``action``, so every existing reader (which keys on
        those) skips it untouched.
        """
        self._write({"step": None, "ts": datetime.now(UTC).isoformat(), "meta": fields})

    def log_error(self, state: dict[str, Any]) -> None:
        """Append an ``error`` notification (phase 1b).

        Deliberately **not** counted as a step decision (``step: None``) —
        an ``error`` isn't a choice the policy made, it's the mod rejecting
        the previous one and re-sending it; the corpus value is in the
        ``error``/``detail`` payload itself, logged verbatim in ``state``.
        """
        entry = {
            "step": None,
            "ts": datetime.now(UTC).isoformat(),
            "state": state,
            "action": None,
        }
        self._write(entry)

    def _write(self, entry: dict[str, Any]) -> None:
        self._file.write(json.dumps(entry, ensure_ascii=False) + "\n")
        self._file.flush()

    def close(self) -> None:
        """Close the underlying file."""
        self._file.close()


_TERMINAL_TYPES = frozenset({"game_over", "run_complete"})


def run(
    host: str,
    port: int,
    log_dir: Path,
    max_steps: int | None,
    rng: random.Random,
    *,
    character: str | None = None,
    ascension: int | None = None,
    seed: int | None = None,
    map_explore: bool = False,
) -> int:
    """Run the random-play loop to completion (or ``max_steps``).

    Args:
        host: Bridge mod TCP host.
        port: Bridge mod TCP port.
        log_dir: Directory to write the JSONL run log into.
        max_steps: Stop after this many decisions (excluding ``error``
            notifications, which are never counted), or ``None`` to run to
            ``game_over``/``run_complete``.
        rng: Seeded RNG source for the random policy.
        character: ``--character`` override for a ``run_config`` message's
            ``start_run`` reply (only relevant if the mod's
            ``client_run_config`` flag is on).
        ascension: ``--ascension`` override, same deal.
        seed: ``--seed`` value to offer ``start_run`` as the run seed
            (``None`` -> mod generates a fresh random one).
        map_explore: ``--explore-map`` — bias map choices toward rarely
            seen screen types (see :func:`pick_map_select`).

    Returns:
        Process exit code (0 on a clean finish, 1 on a connection error).
    """
    jsonl = JsonlLogger(log_dir)
    step = 0
    result = "unknown"

    print(f"Logging to {jsonl.path}")

    try:
        with RealGameBridgeClient(host=host, port=port) as client:
            while True:
                state = client.receive_state()
                msg_type = state.get("type")

                if msg_type == "pong":
                    # Unsolicited pong (shouldn't normally happen mid-loop);
                    # nothing to respond to.
                    continue

                if msg_type == "error":
                    # Fire-and-forget rejection (phase 1b) -- valuable corpus,
                    # but not a decision the policy made, so it doesn't
                    # increment `step`. The mod always follows this with a
                    # fresh re-send of the decision it rejected, which the
                    # next receive_state() picks up normally.
                    jsonl.log_error(state)
                    print(f"[error] {state.get('error')}: {state.get('detail')}")
                    continue

                if msg_type in _TERMINAL_TYPES:
                    step += 1
                    jsonl.log_terminal(step, state)
                    result = state.get("result") or state.get("message") or msg_type
                    print(f"[{step:4d}] {msg_type} -> result={result!r}")
                    # Only run_complete truly ends the session. The mod sends
                    # game_over first (informational, no response expected) and
                    # run_complete a moment later; exiting on game_over left the
                    # buffered run_complete to be replayed to the NEXT client,
                    # which then exited immediately (2026-07-11 marathon).
                    if msg_type == "run_complete":
                        break
                    continue

                # GH #374: install the live save's real UnlockState off the
                # envelope, superseding the census guess from startup. Latched,
                # so this is a dict lookup after the first carrying message.
                from realgame_adapter import install_unlock_state_from_wire

                install_unlock_state_from_wire(state.get("run_state") or {})

                action = choose_action(
                    state,
                    rng,
                    run_config_character=character,
                    run_config_ascension=ascension,
                    run_config_seed=seed,
                    map_explore=map_explore,
                )
                step += 1
                jsonl.log_step(step, state, action)
                print(_progress_line(step, state, action))
                client.send_action(action)

                if max_steps is not None and step >= max_steps:
                    result = "max_steps_reached"
                    print(f"Stopping after max_steps={max_steps}")
                    break
    except (ConnectionError, TimeoutError) as e:
        logger.error("Bridge connection error after %d steps: %s", step, e)
        result = f"error: {e}"
        print(f"Steps: {step}\nResult: {result}\nLog: {jsonl.path}")
        jsonl.close()
        return 1

    jsonl.close()
    print(f"Steps: {step}\nResult: {result}\nLog: {jsonl.path}")
    return 0


def main(argv: list[str] | None = None) -> int:
    """CLI entry point."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default=DEFAULT_HOST, help="Bridge mod TCP host.")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help="Bridge mod TCP port.")
    parser.add_argument(
        "--log-dir",
        type=Path,
        default=Path("logs/realgame"),
        help="Directory to write the JSONL run log into.",
    )
    parser.add_argument(
        "--max-steps",
        type=int,
        default=None,
        help="Stop after this many decisions (default: run to game_over/run_complete).",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=None,
        help=(
            "Seed for the random policy's RNG, and (if the mod's "
            "client_run_config flag is on) the run seed offered in start_run "
            "(default: unseeded RNG, mod generates its own random run seed)."
        ),
    )
    parser.add_argument(
        "--character",
        default=None,
        help=(
            "Character id to request via start_run when a run_config message "
            "is offered (only relevant if the mod's client_run_config flag is "
            "on; default: run_config's own 'defaults.character')."
        ),
    )
    parser.add_argument(
        "--ascension",
        type=int,
        default=None,
        help=(
            "Ascension level to request via start_run when a run_config "
            "message is offered (default: run_config's own "
            "'defaults.ascension')."
        ),
    )
    parser.add_argument(
        "--explore-map",
        action="store_true",
        help=(
            "Bias map choices toward rarely exercised screen types "
            "(rest sites, treasure, shops) instead of picking uniformly — "
            "for protocol-coverage testing, not fair evals."
        ),
    )
    args = parser.parse_args(argv)

    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")

    # GH #367 — same unlock install as the MCTS driver; this arm reconstructs
    # against the same live save. Override with $STS2_LIVE_LOCKED_EPOCHS.
    from realgame_adapter import install_live_unlock_state

    install_live_unlock_state()

    rng = random.Random(args.seed)
    return run(
        args.host,
        args.port,
        args.log_dir,
        args.max_steps,
        rng,
        character=args.character,
        ascension=args.ascension,
        seed=args.seed,
        map_explore=args.explore_map,
    )


if __name__ == "__main__":
    sys.exit(main())
