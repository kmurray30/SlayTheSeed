"""Same-seed headless-vs-real decision-JSON diff harness (real-game bridge phase 2 GATE).

Plan: ``../docs/plans/2026-07-10-real-game-bridge.md``, Phase 2 gate: "same-seed
headless-vs-real decision-JSON diff harness runs and the residual list is
enumerated (this becomes the parity backlog)."

This module drives the SAME deterministic policy (:func:`choose_action`) to
completion on both backends -- the headless C# process (:class:`sts2_client.Game`,
spawned in-process/subprocess exactly like ``tests/conftest.py``) and the live
game (:class:`sts2_realgame.RealGame`, over the SpireMentor TCP protocol) -- for
the same integer seed/character/ascension, records every decision JSON from
both into two canonicalized JSONL trails, and diffs them decision-by-decision.
Divergence is DATA, not failure: the CLI exits 0 even when the trails differ
completely; a nonzero exit means the harness itself couldn't run (process
spawn failed, bridge unreachable, etc).

## The central question: do same-integer-seeds produce the same run content?

Both engines ultimately construct ``RunState`` from a canonicalized seed
STRING:

* Headless (``src/Sts2Headless/RunSimulator.cs::StartRunImpl``) always calls
  ``SeedHelper.CanonicalizeSeed(seed)`` (uppercase, ``O``->``0``, ``I``->``1``,
  trim) on whatever string ``start_run.seed`` it's given, then builds
  ``RunState`` with that string.
* The live game, via the bridge, sets ``NGame.Instance.DebugSeedOverride`` to
  the integer seed formatted as an invariant-culture decimal string
  (``spire-mentor-mod/PROTOCOL.md`` section 4) -- and ``StartRunLobby.cs:619``
  (v0.107.1 decomp) reads ``DebugSeedOverride`` *directly*, bypassing
  ``CanonicalizeSeed`` entirely (that call only fires on the "no override"
  branch). For a purely-numeric seed like ``"12345"`` this is moot --
  canonicalization of an all-digit string is a no-op -- so both paths should
  feed ``RunState`` the identical seed string ``"12345"``.

If ``RunState``'s own seed-string -> ``Rng`` derivation is the same compiled
game logic on both sides (headless wraps the real game minus Godot
rendering), an integer seed SHOULD reproduce the same map/rewards/RNG
stream on both engines. This harness's whole job is to MEASURE that, not
assume it -- see the module docstring's "central question" callout in the
phase-2 task and the CLI's ``meta.seed_note`` report field.

## Deterministic policy

:func:`choose_action` is a pure function ``decision dict -> (action_name,
kwargs) | None`` -- same JSON in, same action out, no hidden state, no RNG.
It is deliberately the simplest "first legal choice" policy, NOT a
random-legal one (contrast :mod:`realgame_random`'s ``choose_action``): a
seeded-random policy would need per-card legality fields
(``can_play``/``target_type``/``valid_target_indices``) that
:mod:`realgame_adapter`'s ``to_headless_combat_play`` (used by
:mod:`sts2_realgame` for every ``combat_play`` decision) does NOT populate
(a real parity gap discovered while building this harness -- see
"Discovered residual" below) -- keying policy behavior off fields that
are silently absent on one backend would make the two trails diverge for
POLICY reasons, contaminating the very parity signal this harness exists to
measure. So:

* ``combat_play``: always ``end_turn`` (never plays a card). This sidesteps
  the missing-legality-field gap entirely and avoids an even worse asymmetry:
  headless surfaces an illegal ``play_card`` as an explicit ``{"type":
  "error", ...}`` reply, while ``RealGame`` transparently drains the bridge's
  ``error`` notification and re-sends the SAME unchanged decision (see
  ``sts2_realgame.RealGame._next_decision``) -- so a policy that occasionally
  guesses wrong would make the two backends visibly disagree about something
  that isn't a real engine parity bug at all, just this harness's own
  handling of illegal actions.
* Every other decision kind picks the first offered/enabled choice (map
  node, event/rest-site option, card reward, bundle, minimal legal
  card-select subset) or ``leave_room``/``skip_*`` when nothing is offered.
  This still exercises every decision kind end-to-end and is trivially
  reproducible.

This is a parity-MEASUREMENT choice, not a strategy: it does not try to win
runs (always-end-turn burns HP every combat) -- ``--max-decisions`` caps run
length, and dying mid-run is fine, it just produces a shorter (but still
comparable) trail on both sides.

## Discovered residual: combat_play hand cards

While wiring this harness, comparing ``src/Sts2Headless/RunSimulator.cs``'s
``SerializeCombatCard`` (headless) against ``realgame_adapter.py``'s
``_translate_card`` (bridge -> headless combat_play) turned up a residual the
``sts2_realgame`` docstring's list does NOT mention: headless hand cards
carry ``can_play``/``target_type`` (``includePlayability: true``); the
bridge-translated hand never does. Recorded here (not silently
ignore-listed) so the differ surfaces ``hand[].can_play``/
``hand[].target_type`` as backlog items on every combat_play step -- exactly
the kind of thing this harness is FOR.
"""

from __future__ import annotations

import argparse
import fnmatch
import json
import logging
import re
import sys
from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from realgame_bridge import DEFAULT_HOST, DEFAULT_PORT
from sts2_client import Game
from sts2_realgame import RealGame

logger = logging.getLogger(__name__)

__all__ = [
    "DEFAULT_IGNORE_PATTERNS",
    "ParityReport",
    "choose_action",
    "diff_trails",
    "flatten_decision",
    "record_trail",
]


# ──────────────────────────────────────────────────────────────────────────────
# Deterministic policy: decision dict -> (action_name, kwargs) | None.
# ──────────────────────────────────────────────────────────────────────────────

_DecisionAction = tuple[str, dict[str, Any]]


def _first_enabled(options: list[dict[str, Any]], *, enabled_key: str) -> dict[str, Any]:
    """First option where ``enabled_key`` is truthy (or absent, default true).

    Falls back to ``options[0]`` if every option is disabled/locked --
    that mirrors the headless UI's own behavior of always allowing SOME
    reply (typically "leave"), and guarantees the policy never stalls.
    """
    for opt in options:
        if opt.get(enabled_key, True):
            return opt
    return options[0]


def choose_action(decision: dict[str, Any]) -> _DecisionAction | None:
    """Pure "first legal choice" policy -- see module docstring for the design.

    Args:
        decision: A headless-schema decision dict (``{"type": "decision",
            "decision": "<kind>", ...}``), as emitted by either
            ``sts2_client.Game`` or ``sts2_realgame.RealGame``.

    Returns:
        ``(action_name, kwargs)`` to pass as ``game.act(action_name,
        **kwargs)`` (both backends share this exact call signature), or
        ``None`` if ``decision`` is terminal (``game_over``) or of a kind
        this policy has no reply for (the caller should stop the trail).
    """
    if decision.get("type") != "decision":
        return None
    kind = decision.get("decision")

    if kind == "map_select":
        choices = decision.get("choices") or []
        if not choices:
            return None
        c = choices[0]
        return "select_map_node", {"col": c.get("col"), "row": c.get("row")}

    if kind == "combat_play":
        # Always end_turn -- see module docstring's "Deterministic policy"
        # section for why this sidesteps a real (and separately documented)
        # adapter gap instead of masking it.
        return "end_turn", {}

    if kind == "event_choice":
        options = decision.get("options") or []
        if not options:
            return "leave_room", {}
        # event_choice options use `is_locked` (true == NOT choosable), the
        # inverse polarity of every other option list's `is_enabled` --
        # normalize by picking the first option with a falsy `is_locked`.
        opt = next((o for o in options if not o.get("is_locked")), options[0])
        return "choose_option", {"option_index": opt.get("index", 0)}

    if kind == "rest_site":
        options = decision.get("options") or []
        if not options:
            return "leave_room", {}
        opt = _first_enabled(options, enabled_key="is_enabled")
        return "choose_option", {"option_index": opt.get("index", 0)}

    if kind == "shop":
        return "leave_room", {}

    if kind == "card_reward":
        cards = decision.get("cards") or []
        if cards:
            return "select_card_reward", {"card_index": 0}
        return "skip_card_reward", {}

    if kind == "card_select":
        cards = decision.get("cards") or []
        min_n = decision.get("min_select") or 0
        if not cards or min_n <= 0:
            return "skip_select", {}
        n = min(int(min_n), len(cards))
        indices = ",".join(str(i) for i in range(n))
        return "select_cards", {"indices": indices}

    if kind == "bundle_select":
        bundles = decision.get("bundles") or []
        if not bundles:
            return None
        return "select_bundle", {"bundle_index": 0}

    if kind == "game_over":
        return None

    logger.error("choose_action: no policy for decision kind %r", kind)
    return None


# ──────────────────────────────────────────────────────────────────────────────
# Trail recording -- backend-agnostic (Game and RealGame share start()/act()).
# ──────────────────────────────────────────────────────────────────────────────


def record_trail(
    game: Game | RealGame,
    *,
    character: str,
    seed: int,
    ascension: int,
    max_decisions: int,
    trail_path: Path | None = None,
    played_save: bool = False,
) -> list[dict[str, Any]]:
    """Run ``choose_action`` to completion (or ``max_decisions``) on ``game``.

    Backend-agnostic: ``sts2_client.Game`` and ``sts2_realgame.RealGame``
    both implement ``start(character=, seed=, ascension=)`` and
    ``act(action_name, **kwargs)`` with identical signatures (that
    equivalence is the entire point of ``RealGame``), so this one function
    drives either.

    Args:
        game: An already-constructed (and, for ``RealGame``, connected)
            backend instance. Not closed here -- caller's responsibility.
        character: Character id/display name, e.g. ``"Ironclad"``.
        seed: Integer run seed (stringified before being passed to
            ``start()`` -- see module docstring's "central question").
        ascension: Ascension level.
        max_decisions: Stop after this many decisions even if the run
            hasn't reached ``game_over`` (safety bound / CLI knob).
        trail_path: If given, append one canonicalized (sorted-keys) JSON
            line per decision, ``{"step", "decision", "action"}``.
        played_save: Forwarded to ``Game.start(played_save=...)`` on the
            headless backend only (``RealGame.start`` has no such concept --
            the live game's save state is whatever it actually is). Makes
            the headless run open on ``map_select`` like a real save with
            NEOW_EPOCH earned-but-not-revealed, instead of the Neow
            blessing altar. See ``sts2_client.Game.start``'s docstring.

    Returns:
        The list of raw decision dicts recorded, in order (index 0 is the
        response to ``start()``).
    """
    fh = trail_path.open("w", encoding="utf-8") if trail_path is not None else None
    decisions: list[dict[str, Any]] = []
    try:
        start_kwargs: dict[str, Any] = {
            "character": character,
            "seed": str(seed),
            "ascension": ascension,
        }
        if played_save and isinstance(game, Game):
            start_kwargs["played_save"] = True
        decision = game.start(**start_kwargs)
        # Save-state alignment (2026-07-11 gate finding): the Neow story
        # event shows once per save. The live game's save already has
        # NEOW_EPOCH obtained-but-not-revealed, so it opens on map_select;
        # a fresh headless save (played_save not set) opens on the Neow
        # event_choice instead. Skip it on backends that expose skip_neow
        # (headless does; RealGame doesn't and never sees it) so both trails
        # start at the same first comparable decision even when played_save
        # wasn't requested. A no-op when played_save=True already avoided
        # the Neow event in the first place.
        if (
            decision.get("decision") == "event_choice"
            and decision.get("event_name") == "Neow"
            and hasattr(game, "skip_neow")
        ):
            logger.info("record_trail: skipping Neow starting event (save-state alignment)")
            decision = game.skip_neow(decision)
        step = 0
        while True:
            decisions.append(decision)
            action = choose_action(decision)
            if fh is not None:
                fh.write(
                    json.dumps(
                        {"step": step, "decision": decision, "action": _action_dict(action)},
                        sort_keys=True,
                        ensure_ascii=False,
                    )
                    + "\n"
                )
                fh.flush()

            if decision.get("type") == "error":
                logger.warning("record_trail: backend returned an error, stopping: %s", decision)
                break
            if action is None:
                break
            step += 1
            if step >= max_decisions:
                logger.info("record_trail: hit --max-decisions=%d, stopping", max_decisions)
                break

            action_name, kwargs = action
            decision = game.act(action_name, **kwargs)
    finally:
        if fh is not None:
            fh.close()
    return decisions


def _action_dict(action: _DecisionAction | None) -> dict[str, Any] | None:
    if action is None:
        return None
    action_name, kwargs = action
    return {"action": action_name, **kwargs}


# ──────────────────────────────────────────────────────────────────────────────
# Diffing -- pure, no I/O; unit-tested directly with synthetic dicts.
# ──────────────────────────────────────────────────────────────────────────────

#: Default ignore-list: exactly the residuals ``sts2_realgame.RealGame``'s
#: own module docstring already declares (see this module's docstring),
#: expressed as ``fnmatch``-style globs matched against the NORMALIZED field
#: path (list indices collapsed to ``[]``, e.g. ``hand[].stats``). Extend via
#: ``--ignore`` on the CLI or the ``extra_ignore_patterns`` kwarg.
DEFAULT_IGNORE_PATTERNS: tuple[str, ...] = (
    "context.act_name",
    "context.boss",
    "context.boss.*",
    "act_name",  # top-level duplicate on map_select
    "*.stats",
    "*.after_upgrade",
    "card_reward.gold_earned",
    "event_name",
)

_INDEX_RE = re.compile(r"\[\d+\]")


def flatten_decision(obj: Any, prefix: str = "") -> list[tuple[str, str, Any]]:
    """Flatten a (possibly nested) decision dict to leaf ``(path, norm_path, value)``.

    ``path`` is the concrete path with real list indices (``hand[2].cost``);
    ``norm_path`` collapses every index to ``[]`` (``hand[].cost``) so the
    diff can aggregate mismatches across lists of differing length/order
    into one backlog entry. Empty dicts/lists are emitted as a leaf with a
    sentinel value (``"<empty-dict>"``/``"<empty-list>"``) so "populated on
    one side, empty on the other" still shows up as a mismatch instead of
    silently vanishing (neither side would otherwise contribute any leaves
    under that path).
    """
    out: list[tuple[str, str, Any]] = []
    if isinstance(obj, dict):
        if not obj:
            out.append((prefix, _INDEX_RE.sub("[]", prefix), "<empty-dict>"))
            return out
        for k, v in obj.items():
            cp = f"{prefix}.{k}" if prefix else str(k)
            out.extend(flatten_decision(v, cp))
    elif isinstance(obj, list):
        if not obj:
            out.append((prefix, _INDEX_RE.sub("[]", prefix), "<empty-list>"))
            return out
        for i, v in enumerate(obj):
            cp = f"{prefix}[{i}]"
            out.extend(flatten_decision(v, cp))
    else:
        out.append((prefix, _INDEX_RE.sub("[]", prefix), obj))
    return out


def _ignored(norm_path: str, patterns: tuple[str, ...]) -> bool:
    return any(fnmatch.fnmatch(norm_path, pat) for pat in patterns)


@dataclass
class ParityReport:
    """Machine-readable diff result -- ``asdict()``-able for the JSON report."""

    total_steps_a: int
    total_steps_b: int
    common_prefix_length: int
    first_structural_divergence: dict[str, Any] | None
    per_field_mismatch_counts: dict[str, int] = field(default_factory=dict)
    unique_mismatching_field_paths: list[str] = field(default_factory=list)
    ignore_patterns: list[str] = field(default_factory=list)
    example_mismatches: dict[str, dict[str, Any]] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {
            "total_steps_a": self.total_steps_a,
            "total_steps_b": self.total_steps_b,
            "common_prefix_length": self.common_prefix_length,
            "first_structural_divergence": self.first_structural_divergence,
            "per_field_mismatch_counts": self.per_field_mismatch_counts,
            "unique_mismatching_field_paths": self.unique_mismatching_field_paths,
            "ignore_patterns": self.ignore_patterns,
            "example_mismatches": self.example_mismatches,
        }


def diff_trails(
    trail_a: list[dict[str, Any]],
    trail_b: list[dict[str, Any]],
    *,
    ignore_patterns: tuple[str, ...] = DEFAULT_IGNORE_PATTERNS,
) -> ParityReport:
    """Decision-by-decision diff of two recorded trails.

    Args:
        trail_a: Decisions from backend A (by CLI convention, headless).
        trail_b: Decisions from backend B (by CLI convention, RealGame).
        ignore_patterns: ``fnmatch`` globs matched against each mismatch's
            NORMALIZED field path (see :func:`flatten_decision`); a
            matching field never contributes to the report.

    Returns:
        A :class:`ParityReport`. ``first_structural_divergence`` is set
        (and the field-level diff stops there) the first time step ``i``'s
        ``decision`` kind differs between the two trails, OR one trail
        simply runs out of steps first (trail length mismatch is itself a
        structural divergence). Field-level mismatches are only computed
        over the common prefix BEFORE that point (or the whole shorter
        trail, if no structural divergence occurs).
    """
    n = min(len(trail_a), len(trail_b))
    first_divergence: dict[str, Any] | None = None

    common_len = n
    for i in range(n):
        ka = (
            trail_a[i].get("decision")
            if trail_a[i].get("type") == "decision"
            else trail_a[i].get("type")
        )
        kb = (
            trail_b[i].get("decision")
            if trail_b[i].get("type") == "decision"
            else trail_b[i].get("type")
        )
        if ka != kb:
            first_divergence = {
                "step": i,
                "a_kind": ka,
                "b_kind": kb,
            }
            common_len = i
            break
    else:
        if len(trail_a) != len(trail_b):
            first_divergence = {
                "step": n,
                "a_kind": (trail_a[n].get("decision") or trail_a[n].get("type"))
                if len(trail_a) > n
                else None,
                "b_kind": (trail_b[n].get("decision") or trail_b[n].get("type"))
                if len(trail_b) > n
                else None,
                "note": "trail length mismatch: one backend ran out of decisions first",
            }
            # common_len already == n (the shorter trail's length)

    mismatch_counts: dict[str, int] = {}
    examples: dict[str, dict[str, Any]] = {}
    for i in range(common_len):
        a_leaves = {p: (norm, v) for p, norm, v in flatten_decision(trail_a[i])}
        b_leaves = {p: (norm, v) for p, norm, v in flatten_decision(trail_b[i])}
        all_paths = set(a_leaves) | set(b_leaves)
        for path in all_paths:
            in_a = path in a_leaves
            in_b = path in b_leaves
            if in_a and in_b:
                norm, va = a_leaves[path]
                _, vb = b_leaves[path]
                if va == vb:
                    continue
            elif in_a:
                norm, va = a_leaves[path]
                vb = "<absent>"
            else:
                norm, vb = b_leaves[path]
                va = "<absent>"
            if _ignored(norm, ignore_patterns):
                continue
            mismatch_counts[norm] = mismatch_counts.get(norm, 0) + 1
            examples.setdefault(norm, {"step": i, "path": path, "a": va, "b": vb})

    return ParityReport(
        total_steps_a=len(trail_a),
        total_steps_b=len(trail_b),
        common_prefix_length=common_len,
        first_structural_divergence=first_divergence,
        per_field_mismatch_counts=dict(sorted(mismatch_counts.items(), key=lambda kv: -kv[1])),
        unique_mismatching_field_paths=sorted(mismatch_counts),
        ignore_patterns=list(ignore_patterns),
        example_mismatches=examples,
    )


# ──────────────────────────────────────────────────────────────────────────────
# CLI -- orchestration only; the differ above never touches a process/socket.
# ──────────────────────────────────────────────────────────────────────────────


def _run_headless(
    *, character: str, seed: int, ascension: int, max_decisions: int, trail_path: Path
) -> list[dict[str, Any]]:
    game = Game()
    try:
        return record_trail(
            game,
            character=character,
            seed=seed,
            ascension=ascension,
            max_decisions=max_decisions,
            trail_path=trail_path,
            # Match the live game's save-state: NEOW_EPOCH earned but never
            # manually revealed on the Timeline screen (see record_trail's
            # played_save docstring and sts2_client.Game.start).
            played_save=True,
        )
    finally:
        game.close()


def _run_real(
    *,
    host: str,
    port: int,
    character: str,
    seed: int,
    ascension: int,
    max_decisions: int,
    trail_path: Path,
    timeout: float,
    reconnect_attempts: int,
) -> list[dict[str, Any]]:
    game = RealGame(
        host=host,
        port=port,
        character=character,
        seed=seed,
        ascension=ascension,
        timeout=timeout,
        reconnect_attempts=reconnect_attempts,
    )
    game.connect()
    try:
        return record_trail(
            game,
            character=character,
            seed=seed,
            ascension=ascension,
            max_decisions=max_decisions,
            trail_path=trail_path,
        )
    finally:
        game.close()


def _print_summary(report: ParityReport, *, headless_path: Path, real_path: Path) -> None:
    print(f"Headless trail: {report.total_steps_a} decisions -> {headless_path}")
    print(f"Real-game trail: {report.total_steps_b} decisions -> {real_path}")
    if report.first_structural_divergence is None:
        print(f"No structural divergence over {report.common_prefix_length} common steps.")
    else:
        d = report.first_structural_divergence
        note = f" ({d['note']})" if "note" in d else ""
        print(
            f"First structural divergence at step {d['step']}: "
            f"headless={d['a_kind']!r} real={d['b_kind']!r}{note}"
        )
    if not report.unique_mismatching_field_paths:
        print("No field-level mismatches over the common prefix.")
        return
    print(
        f"{len(report.unique_mismatching_field_paths)} unique mismatching field path(s) "
        f"over {report.common_prefix_length} common step(s) (parity backlog):"
    )
    for path in report.unique_mismatching_field_paths:
        count = report.per_field_mismatch_counts[path]
        ex = report.example_mismatches.get(path, {})
        print(
            f"  {path}  (x{count})  e.g. step {ex.get('step')}: a={ex.get('a')!r} b={ex.get('b')!r}"
        )


def main(argv: list[str] | None = None) -> int:
    """CLI entry point. Returns a process exit code (see module docstring)."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed", type=int, required=True, help="Integer run seed (both backends).")
    parser.add_argument("--character", default="Ironclad", help="Character id/display name.")
    parser.add_argument("--ascension", type=int, default=0, help="Ascension level (0-10).")
    parser.add_argument("--host", default=DEFAULT_HOST, help="SpireMentor mod TCP host.")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help="SpireMentor mod TCP port.")
    parser.add_argument(
        "--max-decisions",
        type=int,
        default=200,
        help="Stop each backend after this many decisions (default: 200).",
    )
    parser.add_argument(
        "--out", type=Path, default=None, help="Write the machine-readable JSON report here."
    )
    parser.add_argument(
        "--trail-dir",
        type=Path,
        default=Path("logs/realgame_parity"),
        help="Directory the two per-decision JSONL trails are written under "
        "(a timestamped subdirectory is created each run).",
    )
    parser.add_argument(
        "--ignore",
        action="append",
        default=[],
        metavar="GLOB",
        help="Extra fnmatch glob(s) of normalized field paths to ignore, on top of "
        "DEFAULT_IGNORE_PATTERNS. May be passed multiple times.",
    )
    parser.add_argument(
        "--connect-timeout",
        type=float,
        default=60.0,
        help="RealGame socket receive timeout in seconds.",
    )
    parser.add_argument(
        "--reconnect-attempts",
        type=int,
        default=5,
        help="RealGame TCP connect retry budget.",
    )
    parser.add_argument("-v", "--verbose", action="store_true", help="DEBUG-level logging.")
    args = parser.parse_args(argv)

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(levelname)s %(name)s: %(message)s",
    )

    ts = datetime.now(UTC).strftime("%Y%m%dT%H%M%SZ")
    trail_dir = args.trail_dir / f"{ts}_seed{args.seed}"
    trail_dir.mkdir(parents=True, exist_ok=True)
    headless_path = trail_dir / "headless.jsonl"
    real_path = trail_dir / "real.jsonl"

    print(f"[1/2] Running headless backend (seed={args.seed}, character={args.character})...")
    try:
        headless_trail = _run_headless(
            character=args.character,
            seed=args.seed,
            ascension=args.ascension,
            max_decisions=args.max_decisions,
            trail_path=headless_path,
        )
    except Exception as e:  # harness error (process/socket failure), report and exit nonzero
        print(f"HARNESS ERROR (headless backend failed to run): {e}", file=sys.stderr)
        logger.exception("headless backend failed")
        return 2
    print(f"      {len(headless_trail)} decisions recorded -> {headless_path}")

    print(f"[2/2] Running real-game backend ({args.host}:{args.port})...")
    try:
        real_trail = _run_real(
            host=args.host,
            port=args.port,
            character=args.character,
            seed=args.seed,
            ascension=args.ascension,
            max_decisions=args.max_decisions,
            trail_path=real_path,
            timeout=args.connect_timeout,
            reconnect_attempts=args.reconnect_attempts,
        )
    except Exception as e:  # harness error (process/socket failure), report and exit nonzero
        print(f"HARNESS ERROR (real-game backend failed to run): {e}", file=sys.stderr)
        logger.exception("real-game backend failed")
        return 2
    print(f"      {len(real_trail)} decisions recorded -> {real_path}")

    ignore_patterns = tuple(DEFAULT_IGNORE_PATTERNS) + tuple(args.ignore)
    report = diff_trails(headless_trail, real_trail, ignore_patterns=ignore_patterns)

    print()
    _print_summary(report, headless_path=headless_path, real_path=real_path)

    if args.out is not None:
        out_obj = {
            "meta": {
                "seed": args.seed,
                "character": args.character,
                "ascension": args.ascension,
                "max_decisions": args.max_decisions,
                "headless_trail_path": str(headless_path),
                "real_trail_path": str(real_path),
                "generated_at": datetime.now(UTC).isoformat(),
                "seed_note": (
                    "Both backends receive str(seed) as the run seed. Headless "
                    "canonicalizes it via SeedHelper.CanonicalizeSeed (no-op for an "
                    "all-digit string); the live game sets DebugSeedOverride to the "
                    "same digit string directly (StartRunLobby.cs bypasses "
                    "CanonicalizeSeed on the override path). Whether that produces an "
                    "identical RunState/Rng stream on both engines is exactly what "
                    "this report's map_select/card_reward/etc. field diffs measure -- "
                    "see the module docstring's 'central question' section."
                ),
            },
            "report": report.to_dict(),
        }
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(out_obj, indent=2, sort_keys=True), encoding="utf-8")
        print(f"\nMachine-readable report written to {args.out}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
