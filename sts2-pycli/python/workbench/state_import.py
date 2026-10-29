"""Parse a state pasted out of the live game into something the workbench can play.

The SpireMentor mod stamps every outbound message with the whole run
(``SimStateExtractor.ApplyEnvelope``): the string seed, the act and floor, the
full ``run_state`` (deck / relics / potions / gold / HP / ascension /
character), the act map graph *including its visited coords and the current
node*, and ``rng`` — the run's RNG cursor for every stream. That is enough to
rebuild the position; this module is the plumbing.

What comes in
-------------
Deliberately tolerant, because the JSON reaches the user by whatever route is
to hand. All of these parse:

* the mod's "Copy state" payload (an ``advice_request``),
* any raw wire message (``combat_action``, ``map_select``, …),
* one record out of an advisor log (``{step, ts, type, data}``) or a workbench
  run log,
* a whole pasted JSONL blob — the newest record carrying a state wins,
* a headless ``combat_play`` decision dict,
* an engine wire dict (``sts2_rs.state_to_dict`` shape) already.

What comes out
--------------
An :class:`ImportedState`: a :class:`RunSnapshot` for the run lane, and — when
the paste carried a fight — ``combat_wire``, the engine wire dict that
``sts2_rs.state_from_dict`` turns into a bit-identical board.

The combat leg reuses the advisor's own path verbatim
(:func:`realgame_adapter.to_headless_combat_play` →
:func:`sts2_hint.adapter.translate`) rather than a second translation that
would drift from it.

Nothing is guessed. A field the paste does not carry becomes a
:attr:`ImportedState.warnings` entry, because a half-restored state that
*looks* whole is the expensive failure here — an import with no ``rng`` block
shares only its seed, and every downstream roll belongs to a different run.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import Any


class StateImportError(ValueError):
    """A pasted state the importer cannot turn into a position.

    The message is shown to the user verbatim, so it says what is wrong with
    the paste and what to paste instead — never a stack trace.
    """


#: Wire message types that describe a decision the player was facing. Anything
#: else (``pong``, ``error``, ``run_complete``) is not a position.
DECISION_SURFACES = frozenset(
    {
        "combat_action",
        "map_select",
        "card_select",
        "card_reward",
        "card_bundle",
        "crystal_sphere",
        "rest_site",
        "shop",
        "event",
        "treasure",
        "boss_relic",
        "reward_screen",
    }
)

#: Log-envelope keys that mark a ``{step, ts, type, data}`` wrapper (the
#: advisor's ``AdvisorLog`` and the workbench's ``GameLogger`` share it).
_LOG_ENVELOPE_MARKERS = ("step", "ts")


@dataclass(frozen=True)
class RunSnapshot:
    """The run-level position, normalized to ids the engine registry knows."""

    seed: str
    character: str
    """Display name (``"Ironclad"``) — what ``RunManager`` wants."""
    character_id: str
    """Wire id (``"IRONCLAD"``) — what the mod sent."""
    ascension: int
    act: int
    floor: int
    hp: int
    max_hp: int
    gold: int
    deck: tuple[tuple[str, bool], ...]
    """``(registry_card_id, upgraded)`` per master-deck card, in engine order."""
    relics: tuple[str, ...]
    potions: tuple[str, ...]
    potion_slots: int
    map: dict[str, Any] | None
    """The act map graph as the mod serialized it, or ``None``."""
    rng: dict[str, Any] | None
    """``{"run_seed": int, "counters": {stream: count}}``, or ``None``."""

    @property
    def coord(self) -> tuple[int, int] | None:
        """The node the player is standing on as ``(col, row)``, if known."""
        cur = (self.map or {}).get("current")
        if not isinstance(cur, dict):
            return None
        col, row = cur.get("col"), cur.get("row")
        if isinstance(col, int) and isinstance(row, int):
            return (col, row)
        return None

    @property
    def visited(self) -> tuple[tuple[int, int], ...]:
        """Every coord this run has entered this act, in the world's own order."""
        raw = (self.map or {}).get("visited")
        if not isinstance(raw, list):
            return ()
        out = []
        for entry in raw:
            if isinstance(entry, dict):
                col, row = entry.get("col"), entry.get("row")
                if isinstance(col, int) and isinstance(row, int):
                    out.append((col, row))
        return tuple(out)


@dataclass(frozen=True)
class ImportedState:
    """One pasted position: the run, and the fight if there was one."""

    surface: str
    """The decision the player was facing (``"combat_action"``, ``"shop"``, …)."""
    run: RunSnapshot
    combat_wire: dict[str, Any] | None
    """Engine wire dict for ``sts2_rs.state_from_dict``, or ``None``."""
    source: str
    """Which paste shape was recognized — shown back to the user."""
    warnings: tuple[str, ...] = ()
    raw: dict[str, Any] = field(default_factory=dict, repr=False)

    @property
    def has_combat(self) -> bool:
        return self.combat_wire is not None

    def fidelity(self) -> dict[str, Any]:
        """What this import reproduces exactly, and what it does not.

        The UI renders this next to the board. Every claim here is one the
        importer can actually stand behind.
        """
        exact = ["seed", "character", "ascension", "deck", "relics", "potions", "gold", "hp"]
        if self.run.map is not None:
            exact.append("map + position")
        if self.combat_wire is not None:
            exact.append("combat board (hand, piles, powers, intents)")
        return {
            "exact": exact,
            "rng_restored": self.run.rng is not None,
            "warnings": list(self.warnings),
            "surface": self.surface,
            "source": self.source,
        }


# ---------------------------------------------------------------------------
# Paste sniffing
# ---------------------------------------------------------------------------


def _load_candidates(text: str) -> list[Any]:
    """Every JSON value the paste contains, oldest first.

    One object, a JSON array, or a JSONL blob — the three shapes a copied log
    actually arrives in. Unparseable lines are skipped rather than fatal: a
    paste that clipped a line in half at one end should still import.
    """
    stripped = text.strip()
    if not stripped:
        raise StateImportError("Nothing to import — the paste is empty.")
    try:
        value = json.loads(stripped)
    except json.JSONDecodeError:
        pass
    else:
        return list(value) if isinstance(value, list) else [value]

    out: list[Any] = []
    for line in stripped.splitlines():
        line = line.strip().rstrip(",")
        if not line:
            continue
        try:
            out.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    if not out:
        raise StateImportError(
            "That does not look like JSON. Paste one wire message, one log "
            "line, or a whole JSONL log."
        )
    return out


def _unwrap(value: Any) -> dict[str, Any] | None:
    """Strip a log envelope off *value*, or ``None`` if it is not a message.

    ``AdvisorLog`` and the workbench's ``GameLogger`` both write
    ``{step, ts, type, data}``; the message is ``data``. Nested wrappers are
    peeled (a run-log line re-logged into another log) but never more than a
    couple of levels — a deeper nest is a different file format, not this one.
    """
    for _ in range(3):
        if not isinstance(value, dict):
            return None
        payload = value.get("data")
        if isinstance(payload, dict) and any(k in value for k in _LOG_ENVELOPE_MARKERS):
            value = payload
            continue
        return value
    return value if isinstance(value, dict) else None


def _surface_of(msg: dict[str, Any]) -> str | None:
    """The decision *msg* describes, or ``None`` if it describes none.

    ``advice_request`` wraps another message type and names it in
    ``advice_for`` — exactly the unwrap ``realgame_advisor`` does before
    dispatching (``state["type"] = advice_for``).
    """
    advice_for = msg.get("advice_for")
    if isinstance(advice_for, str) and advice_for:
        return advice_for
    kind = msg.get("type")
    if isinstance(kind, str) and kind in DECISION_SURFACES:
        return kind
    # A run-level snapshot copied with no screen open: the envelope alone is a
    # position, just not a decision.
    if kind == "run_snapshot":
        return "run_snapshot"
    return None


def _is_engine_wire(value: Any) -> bool:
    """Is *value* already an ``sts2_rs.state_to_dict`` wire dict?

    The tell is the nested ``deck`` object: the mod's dialect keeps its piles
    at the top level (``hand``, ``draw_pile``), the engine's nests them.
    """
    return (
        isinstance(value, dict)
        and isinstance(value.get("deck"), dict)
        and isinstance(value.get("player"), dict)
    )


def _is_headless_combat_play(value: Any) -> bool:
    return (
        isinstance(value, dict)
        and value.get("type") == "decision"
        and value.get("decision") == "combat_play"
    )


# ---------------------------------------------------------------------------
# Wire -> registry ids
# ---------------------------------------------------------------------------


def _registry(kind: str) -> frozenset[str]:
    import sts2_rs

    return frozenset(sts2_rs.list_registry_names(kind))


def _deck_from_wire(cards: list[Any], warnings: list[str]) -> tuple[tuple[str, bool], ...]:
    """``(registry_id, upgraded)`` per master-deck card.

    Cards the engine has no registry entry for are dropped WITH a warning
    rather than silently: a deck that is quietly one card short changes every
    draw, and a counterfactual run on it would be answering a different
    question.
    """
    from sts2_hint.adapter import UnsupportedStateError, registry_card_id

    out: list[tuple[str, bool]] = []
    dropped: list[str] = []
    for i, raw in enumerate(cards):
        if not isinstance(raw, dict):
            continue
        raw_id = raw.get("id")
        if not isinstance(raw_id, str) or not raw_id.strip():
            dropped.append(f"#{i}")
            continue
        try:
            card_id = registry_card_id(raw_id, f"$.run_state.master_deck[{i}]")
        except UnsupportedStateError:
            dropped.append(raw_id)
            continue
        upgraded = bool(raw.get("upgrade_level") or raw.get("upgraded"))
        # An id may already spell its own upgrade (`STRIKE_IRONCLAD+1`). The
        # engine's `force_add_card` takes the BASE id and the upgrade is
        # applied separately, so fold the suffix into the flag rather than
        # handing it an id it would have to re-parse.
        base, plus, _level = card_id.partition("+")
        if plus:
            card_id, upgraded = base, True
        out.append((card_id, upgraded))
    if dropped:
        warnings.append(
            f"{len(dropped)} deck card(s) this engine does not know were left "
            f"out: {', '.join(sorted(set(dropped))[:6])}"
        )
    return tuple(out)


def _ids_from_wire(
    entries: list[Any], kind: str, label: str, warnings: list[str]
) -> tuple[str, ...]:
    """Registry ids off the wire's own ``id`` field, dropping what we lack.

    The wire's ``id`` is the canonical ``Id.Entry`` for both relics and
    potions, which is strictly more reliable than the rendered title (a waxed
    relic's title carries ToyBox's prefix — see
    ``sts2_hint.adapter._relic_id_from_wire``).
    """
    known = _registry(kind)
    out: list[str] = []
    dropped: list[str] = []
    for raw in entries:
        if not isinstance(raw, dict) or raw.get("empty"):
            continue
        raw_id = raw.get("id")
        if not isinstance(raw_id, str) or not raw_id.strip():
            continue
        candidate = raw_id.strip().upper()
        if candidate in known:
            out.append(candidate)
        else:
            dropped.append(candidate)
    if dropped:
        warnings.append(
            f"{len(dropped)} {label} this engine does not model were left out: "
            f"{', '.join(sorted(set(dropped)))}"
        )
    return tuple(out)


def _rng_block(msg: dict[str, Any]) -> dict[str, Any] | None:
    """``{run_seed, counters}`` off the envelope, or ``None``.

    Forwarded all-or-nothing, the same contract the producer
    (``SimStateExtractor.RngState``) and every other consumer already keep: a
    seed without cursors rebuilds every stream at count 0, which destroys more
    lineage than it restores.
    """
    raw = msg.get("rng")
    if not isinstance(raw, dict):
        return None
    run_seed = raw.get("run_seed")
    counters = raw.get("counters")
    if isinstance(run_seed, bool) or not isinstance(run_seed, int):
        return None
    if not isinstance(counters, dict):
        return None
    # A v0.111.0 master seed is a full ``ulong`` (GH #788). A negative integer
    # is a 32-bit seed written signed: its ``uint`` view. Mirrors the Rust
    # ``state_import::rng_block``.
    if run_seed < 0:
        run_seed &= 0xFFFF_FFFF
    elif run_seed > 0xFFFF_FFFF_FFFF_FFFF:
        return None
    clean = {
        str(k): int(v)
        for k, v in counters.items()
        if isinstance(v, int) and not isinstance(v, bool)
    }
    if not clean:
        return None
    return {"run_seed": run_seed, "counters": clean}


def _run_snapshot(msg: dict[str, Any], warnings: list[str]) -> RunSnapshot:
    from realgame_adapter import character_display_name

    rs = msg.get("run_state")
    if not isinstance(rs, dict):
        raise StateImportError(
            "This message carries no run_state, so there is no run to rebuild. "
            "Copy a state while a run is in progress."
        )
    character_id = rs.get("character")
    if not isinstance(character_id, str) or not character_id:
        raise StateImportError("This message names no character, so it was copied outside a run.")
    display = character_display_name(character_id)
    if not display:
        raise StateImportError(f"Unknown character {character_id!r} — this engine has no port.")

    seed = msg.get("seed")
    if not isinstance(seed, str) or not seed.strip():
        raise StateImportError(
            "This message carries no run seed, so the map and every roll would "
            "belong to a different run."
        )

    rng = _rng_block(msg)
    if rng is None:
        warnings.append(
            "No RNG cursors in this paste — the map and deck are exact, but "
            "the next card reward, shop and event will be this engine's rolls, "
            "not the live run's."
        )
    map_graph = msg.get("map")
    if not isinstance(map_graph, dict) or map_graph.get("error"):
        map_graph = None
        warnings.append(
            "No map graph in this paste — the run will start at the act "
            "entrance instead of where you were standing."
        )

    return RunSnapshot(
        seed=seed.strip(),
        character=display,
        character_id=character_id,
        ascension=int(rs.get("ascension") or 0),
        act=int(msg.get("act") or 1),
        floor=int(msg.get("floor") or 0),
        hp=int(rs.get("hp") or 0),
        max_hp=int(rs.get("max_hp") or 0),
        gold=int(rs.get("gold") or 0),
        deck=_deck_from_wire(rs.get("master_deck") or [], warnings),
        relics=_ids_from_wire(rs.get("relics") or [], "relic", "relic(s)", warnings),
        potions=_ids_from_wire(rs.get("potions") or [], "potion", "potion(s)", warnings),
        potion_slots=int(rs.get("potion_slot_count") or 0),
        map=map_graph,
        rng=rng,
    )


def _combat_source(msg: dict[str, Any], surface: str) -> dict[str, Any] | None:
    """The ``combat_action``-shaped payload inside *msg*, if it has one.

    ``combat_action`` is itself one. A ``card_select`` raised by a card mid-play
    embeds the board it belongs to (``SimCombatHandler.BuildEmbeddedCombatState``),
    and that board is the position worth importing.
    """
    if surface == "combat_action":
        return msg
    embedded = msg.get("combat")
    return embedded if isinstance(embedded, dict) else None


def _combat_wire(
    board: dict[str, Any], run: RunSnapshot, warnings: list[str]
) -> dict[str, Any] | None:
    """Translate a live combat board to the engine wire, or warn and give up.

    A failure here is not fatal to the import: the run lane still rebuilds, and
    losing the fight is worth saying out loud rather than crashing the paste.
    """
    from realgame_adapter import to_headless_combat_play
    from sts2_hint import adapter

    try:
        headless = to_headless_combat_play({**board, "type": "combat_action"})
        return adapter.translate(
            headless,
            character=run.character,
            combat_id=0,
            seed=0,
            ascension=run.ascension,
        )
    except adapter.UnsupportedStateError as exc:
        # The adapter refuses a board captured mid-resolution (a card in flight,
        # an unanswered choice) — its message names the offending JSON path.
        warnings.append(
            f"The fight could not be rebuilt ({exc}). Copy at a decision point "
            "rather than mid-animation."
        )
    except (KeyError, TypeError, ValueError) as exc:
        warnings.append(f"The fight could not be rebuilt ({exc}).")
    return None


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------


def parse_pasted_state(text: str) -> ImportedState:
    """Turn pasted JSON into a position the workbench can rebuild.

    Args:
        text: Whatever the user pasted — see the module docstring for the
            shapes accepted.

    Returns:
        The parsed position. ``combat_wire`` is set when the paste carried a
        fight; ``warnings`` names everything that could not be reproduced.

    Raises:
        StateImportError: When the paste is not JSON, carries no run, or
            carries no decision this importer recognizes. The message is
            user-facing.
    """
    candidates = _load_candidates(text)

    # Newest-first: a pasted log's LAST state is the one the user was looking
    # at. Everything before it is history.
    last_error: StateImportError | None = None
    for value in reversed(candidates):
        if _is_engine_wire(value):
            raise StateImportError(
                "That is an engine wire dict, not a live-game state. It has no "
                "run attached (no seed, deck or map), so there is no run to "
                "rebuild — copy the state from the mod instead."
            )
        msg = _unwrap(value)
        if msg is None:
            continue
        if _is_headless_combat_play(msg):
            raise StateImportError(
                "That is a headless combat_play decision, which carries no run "
                "(no seed, deck or map). Copy the state from the mod instead."
            )
        surface = _surface_of(msg)
        if surface is None:
            continue
        warnings: list[str] = []
        try:
            run = _run_snapshot(msg, warnings)
        except StateImportError as exc:
            last_error = exc
            continue
        combat_wire = None
        board = _combat_source(msg, surface)
        if board is not None:
            combat_wire = _combat_wire(board, run, warnings)
        source = (
            "advice_request" if msg.get("advice_for") else str(msg.get("type") or "wire message")
        )
        return ImportedState(
            surface=surface,
            run=run,
            combat_wire=combat_wire,
            source=source,
            warnings=tuple(warnings),
            raw=msg,
        )

    if last_error is not None:
        raise last_error
    raise StateImportError(
        "No game state in that paste. Expected a SpireMentor wire message "
        "(advice_request / combat_action / a screen), an advisor log line, or "
        "a whole advisor log."
    )
