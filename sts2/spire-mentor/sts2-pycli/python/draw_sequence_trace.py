"""Lockstep draw-sequence tracer for C#↔Rust parity cascades.

``parity_audit.run_trial`` drives both engines in lockstep and reports the FIRST
diverging snapshot, then stops. That endpoint is enough to bucket a trial, but it
is the wrong tool for a *draw-order cascade*: by the time a wrong card surfaces at
some ``hand_ids[k]`` / ``draw_ids[k]`` index, the actual desync — a reshuffle or a
draw that happened in a different order several steps earlier — is long past. The
memory taxonomy repeatedly defers these ("needs per-step lockstep draw-sequence
tooling, none exists").

This is that tool. It re-runs the same lockstep driver (`_LiveCsOracle` for C#,
the native Rust state for Rust, `random.Random(seed ^ 0xDEADBEEF)` action
selection — byte-identical to ``_run_trial_core``) but, instead of diffing-and-
returning, it records the ORDERED piles (hand/draw/discard/exhaust) of BOTH
engines at every step and classifies each pile per step as:

* ``match``   — identical ordered list;
* ``order``   — same multiset, different order (the draw-order desync signal);
* ``content`` — different multiset (a card gained/lost/relocated — a stronger
                signal that is NOT pure draw-order).

It then reports the FIRST step where any pile slips, whether that first slip is
order-only or content, the action that produced it, and the RNG-counter streams
that differ at that step (so a shuffle/draw desync can be correlated with the
exact stream). Crucially it does NOT stop at the first slip — it keeps tracing so
you can watch the cascade propagate (an ``order`` slip in ``draw_ids`` becoming a
``content`` slip in ``hand_ids`` two draws later).

Use it to decide whether a deferred ``PILE_COUNT|end_turn`` / ``DRAW_ORDER`` /
``HAND_COST`` cascade is (a) a benign order-only reshuffle artifact where Rust is
correct, or (b) a real engine bug where a card lands in the wrong pile. An
order-only slip that both engines reach with IDENTICAL RNG counters is the former;
a slip that coincides with an RNG-counter divergence, or a ``content`` slip,
points at the latter.

Example::

    bash scripts/wheel-venv.sh run -- \\
        python python/draw_sequence_trace.py --seed 18 --character Defect

    bash scripts/wheel-venv.sh run -- \\
        python python/draw_sequence_trace.py --seed 42 --character Silent --max-turns 8 --full
"""

from __future__ import annotations

import argparse
import dataclasses
import random
from collections import Counter
from typing import Any

import sts2_rs  # type: ignore[import-not-found]
from parity_audit import (
    _drain_rust_choices,
    _enrich_csharp,
    _enrich_rust,
    _LiveCsOracle,
    _rust_init,
)
from parity_fuzz import _apply_rust, _rust_legal
from parity_fuzz_scenario import Scenario, generate_v2

PILES: tuple[str, ...] = ("hand_ids", "draw_ids", "discard_ids", "exhaust_ids")
SHORT = {"hand_ids": "hand", "draw_ids": "draw", "discard_ids": "disc", "exhaust_ids": "exh"}

#: RNG streams advanced only during RUN SETUP (boss/relic-grab-bag/room/ancient
#: generation at run start — `RunRngType.UpFront` etc.) or off un-replicable
#: run-setup pools (potion/orb generation). The synthetic combat scenario
#: (`generate_v2`) never runs full run init, so C# shows a large fixed read-count
#: and Rust shows 0 — a BENIGN absolute-counter offset that does NOT drive combat
#: draws. A divergence confined to these is not the desync to chase; a COMBAT
#: stream (`shuffle`, draws) diverging is.
_RUN_SETUP_STREAMS: frozenset[str] = frozenset(
    {"up_front", "combat_potion_generation", "combat_orbs"}
)


def _combat_rng_diffs(diffs: dict[str, tuple[int, int]]) -> dict[str, tuple[int, int]]:
    """Drop the benign run-setup streams; keep only combat-relevant divergences."""
    return {k: v for k, v in diffs.items() if k not in _RUN_SETUP_STREAMS}


def _pile_status(a: list, b: list) -> str:
    """Classify a single pile pair: ``match`` / ``order`` / ``content``."""
    if a == b:
        return "match"
    if Counter(a) == Counter(b):
        return "order"
    return "content"


def _rng_counters_rust(rs_state: Any) -> dict:
    return sts2_rs.state_to_dict(rs_state.inner).get("rng_counters") or {}


def _counter_diffs(cs_raw: dict, rs_state: Any) -> dict[str, tuple[int, int]]:
    """RNG-counter streams whose read-count differs (C#, Rust)."""
    cs = cs_raw.get("rng_counters") or {}
    rs = _rng_counters_rust(rs_state)
    out: dict[str, tuple[int, int]] = {}
    for k in sorted(set(cs) | set(rs)):
        cv, rv = int(cs.get(k, 0)), int(rs.get(k, 0))
        if cv != rv:
            out[k] = (cv, rv)
    return out


@dataclasses.dataclass
class StepTrace:
    """One lockstep step's ordered piles + per-pile divergence classification."""

    step: int
    turn: int
    action: str
    cs: dict[str, list]
    rs: dict[str, list]
    status: dict[str, str]
    rng_diffs: dict[str, tuple[int, int]]
    note: str = ""

    @property
    def first_slip(self) -> str | None:
        """The worst pile status this step (``content`` > ``order`` > None)."""
        if any(s == "content" for s in self.status.values()):
            return "content"
        if any(s == "order" for s in self.status.values()):
            return "order"
        return None

    @property
    def deck_conserved(self) -> bool:
        """True iff the WHOLE-deck multiset (all four piles) matches between
        engines — the discriminator between a draw-order/reshuffle desync (a
        different hand/draw SPLIT of the same cards) and a real card gain/loss."""
        cs_all: Counter = Counter()
        rs_all: Counter = Counter()
        for k in PILES:
            cs_all += Counter(self.cs[k])
            rs_all += Counter(self.rs[k])
        return cs_all == rs_all


def _piles_of(state: dict) -> dict[str, list]:
    return {k: list(state.get(k) or []) for k in PILES}


def _init_states(sc: Scenario, oracle: _LiveCsOracle) -> tuple[dict, Any]:
    """Init both engines, mirroring ``_run_trial_core``'s roster fallback.

    Builds Rust natively first and only adopts C#'s explicit roster when the
    native spawn diverges — so the trace reflects the SAME Rust state the audit
    would, not an artificially roster-forced one.
    """
    cs_raw = oracle.init()
    cs_monsters = [m["kind"] for m in (cs_raw.get("monsters") or [])]
    cs_move_states = [m.get("move_state") for m in (cs_raw.get("monsters") or [])]
    rs_state = _rust_init(sc, monsters=None, move_states=cs_move_states)
    rs_native = [m.get("kind") for m in (_enrich_rust(rs_state).get("monsters") or [])]
    if rs_native != cs_monsters:
        rs_state = _rust_init(sc, monsters=cs_monsters, move_states=cs_move_states)
    _drain_rust_choices(rs_state)
    return cs_raw, rs_state


def trace_seed(seed: int, character: str, max_turns: int = 12) -> list[StepTrace]:
    """Drive both engines in lockstep and record every step's ordered piles.

    Returns one :class:`StepTrace` per step (index 0 is the init snapshot). The
    action sequence keys off ``seed`` exactly as ``_run_trial_core`` does, so the
    trajectory is the one the audit replays. Stops at ``max_turns``, at combat
    end, or at a legal-action divergence (recorded in the final step's ``note``).
    """
    sc = generate_v2(seed, character)
    oracle = _LiveCsOracle(sc)
    rng = random.Random(seed ^ 0xDEADBEEF)
    steps: list[StepTrace] = []
    try:
        cs_raw, rs_state = _init_states(sc, oracle)
        cs = _enrich_csharp(cs_raw)
        rs = _enrich_rust(rs_state)
        cs_p, rs_p = _piles_of(cs), _piles_of(rs)
        steps.append(
            StepTrace(
                step=0,
                turn=0,
                action="init",
                cs=cs_p,
                rs=rs_p,
                status={k: _pile_status(cs_p[k], rs_p[k]) for k in PILES},
                rng_diffs=_counter_diffs(cs_raw, rs_state),
            )
        )

        turn = 0
        step_i = 1
        while turn < max_turns:
            if (
                cs.get("combat_terminal")
                and rs.get("combat_terminal")
                and cs.get("player_won") == rs.get("player_won")
            ):
                break
            cs_actions = oracle.legal()
            rs_actions = _rust_legal(rs_state)
            if cs_actions != rs_actions:
                steps[-1].note = (
                    f"LEGAL_ACTION divergence before step {step_i}: "
                    f"cs_only={[a for a in cs_actions if a not in rs_actions]} "
                    f"rs_only={[a for a in rs_actions if a not in cs_actions]}"
                )
                break
            if not cs_actions:
                break

            action = rng.choice(cs_actions)
            label = action["action"]
            if label == "play_card":
                ci = action.get("card_index", 0)
                hand = cs.get("hand_ids") or []
                label = f"play[{ci}]={hand[ci] if 0 <= ci < len(hand) else '?'}"

            oracle.apply(action)
            _apply_rust(rs_state, action)
            _drain_rust_choices(rs_state)

            cs_raw = oracle.snapshot()
            cs = _enrich_csharp(cs_raw)
            rs = _enrich_rust(rs_state)
            cs_p, rs_p = _piles_of(cs), _piles_of(rs)
            steps.append(
                StepTrace(
                    step=step_i,
                    turn=cs.get("turn", turn),
                    action=label,
                    cs=cs_p,
                    rs=rs_p,
                    status={k: _pile_status(cs_p[k], rs_p[k]) for k in PILES},
                    rng_diffs=_counter_diffs(cs_raw, rs_state),
                )
            )
            if action["action"] == "end_turn":
                turn = cs.get("turn", turn + 1)
            step_i += 1
    finally:
        oracle.close()
    return steps


def _fmt_pile(name: str, cs: list, rs: list, status: str) -> list[str]:
    if status == "match":
        return []
    mark = {"order": "≈ ORDER", "content": "✗ CONTENT"}[status]
    return [
        f"      {SHORT[name]:4} {mark}",
        f"        C#:   {cs}",
        f"        Rust: {rs}",
    ]


def format_trace(steps: list[StepTrace], full: bool = False) -> str:
    """Render a trace. By default shows only diverging steps (+ the step before
    the first slip); ``full`` shows every step."""
    first_slip_idx = next((i for i, s in enumerate(steps) if s.first_slip), None)
    lines: list[str] = []
    for i, s in enumerate(steps):
        show = full or bool(s.first_slip) or s.note
        # also show the step immediately BEFORE the first slip for context
        if first_slip_idx is not None and i == first_slip_idx - 1:
            show = True
        if not show:
            continue
        tag = ""
        if i == first_slip_idx:
            tag = "   <<< FIRST SLIP"
        if s.first_slip:
            tag += "  [deck " + ("CONSERVED" if s.deck_conserved else "DIFFERS") + "]"
        lines.append(f"step {s.step:3} | turn {s.turn} | {s.action}{tag}")
        for k in PILES:
            lines.extend(_fmt_pile(k, s.cs[k], s.rs[k], s.status[k]))
        if s.rng_diffs:
            lines.append(f"      rng Δ (C#,Rust): {s.rng_diffs}")
        if s.note:
            lines.append(f"      NOTE: {s.note}")
    return "\n".join(lines)


def summarize(steps: list[StepTrace]) -> str:
    """One-paragraph verdict: where/how the trajectory first slips."""
    slip = next((s for s in steps if s.first_slip), None)
    if slip is None:
        tail = steps[-1].note if steps and steps[-1].note else ""
        return f"NO PILE SLIP across {len(steps)} steps — ordered piles match throughout. {tail}".strip()
    piles = [SHORT[k] for k in PILES if slip.status[k] != "match"]
    combat = _combat_rng_diffs(slip.rng_diffs)
    setup = {k: v for k, v in slip.rng_diffs.items() if k in _RUN_SETUP_STREAMS}
    if combat:
        rng = (
            f"COMBAT RNG stream(s) DIVERGE here: {combat} — a real combat-stream desync "
            f"(chase this: which side reads the stream a different number of times at the "
            f"reshuffle). Benign run-setup offsets ignored: {setup or '{}'}."
        )
    elif setup:
        rng = (
            f"Only BENIGN run-setup streams diverge ({setup}) — an absolute-counter offset "
            f"that does NOT drive combat draws. Combat RNG is IN SYNC."
        )
    else:
        rng = "RNG counters MATCH here — pure ORDER slip with fully synced RNG."
    if slip.deck_conserved:
        interp = (
            "whole-deck multiset CONSERVED → a draw-order/reshuffle DESYNC (same cards, "
            "different hand/draw split), NOT a card loss."
        )
    else:
        interp = (
            "whole-deck multiset DIFFERS → a card was genuinely gained/lost/duplicated; "
            "investigate as a possible REAL engine bug (not mere draw order)."
        )
    return (
        f"FIRST SLIP at step {slip.step} (turn {slip.turn}, after `{slip.action}`) "
        f"in pile(s) {piles}.\n{interp}\n{rng}"
    )


def main() -> None:
    ap = argparse.ArgumentParser(description=(__doc__ or "").split("\n", 1)[0])
    ap.add_argument("--seed", type=int, required=True)
    ap.add_argument("--character", default="Silent")
    ap.add_argument("--max-turns", type=int, default=12)
    ap.add_argument("--full", action="store_true", help="show every step, not just diverging ones")
    args = ap.parse_args()

    steps = trace_seed(args.seed, args.character, args.max_turns)
    print(f"=== draw-sequence trace: seed {args.seed} {args.character} ({len(steps)} steps) ===")
    print(format_trace(steps, full=args.full))
    print("\n--- SUMMARY ---")
    print(summarize(steps))


if __name__ == "__main__":
    main()
