"""Verbose step-by-step C# vs Rust parity tracer for a single seed.

Usage:
    PYTHONPATH=python .venv/bin/python python/dump_seed.py <seed> [max_turns]

Mirrors parity_audit.run_trial but prints BOTH engines' full state after
every action, so you can see the surrounding context of the first
divergence (not just the one diff line run_trial stops at).

Prints per step: the chosen action, then for each engine: player hp /
block / energy / powers, and each monster's hp / block / powers, plus
hand/draw/discard/exhaust id-lists. The first diverging field is flagged
with >>>.
"""

from __future__ import annotations

import dataclasses
import json
import random
import sys

from parity_audit import (
    _apply_csharp,
    _apply_rust,
    _csharp_init,
    _csharp_legal,
    _drain_csharp_choices,
    _drain_rust_choices,
    _enrich_csharp,
    _enrich_rust,
    _rust_init,
    _rust_legal,
)
from parity_fuzz_scenario import generate
from parity_fuzz_state import diff
from sts2_client import Game


def _fmt(state: dict) -> str:
    p = state.get("player") or {}
    lines = []
    lines.append(
        f"    player: hp={p.get('hp')} block={p.get('block')} "
        f"energy={p.get('energy')} powers={p.get('powers')}"
    )
    for i, m in enumerate(state.get("monsters") or []):
        lines.append(
            f"    mon[{i}] {m.get('kind', '')}: hp={m.get('hp')} "
            f"block={m.get('block')} alive={m.get('alive')} "
            f"intent={m.get('intent')} powers={m.get('powers')}"
        )
    for pile in ("hand_ids", "draw_ids", "discard_ids", "exhaust_ids"):
        if pile in state:
            lines.append(f"    {pile}: {state.get(pile)}")
    return "\n".join(lines)


def main() -> int:
    seed = int(sys.argv[1])
    max_turns = int(sys.argv[2]) if len(sys.argv) > 2 else 8
    sc = generate(seed)
    print(f"=== seed {seed} ===")
    print(json.dumps(dataclasses.asdict(sc), indent=1))
    rng = random.Random(seed ^ 0xDEADBEEF)
    g = Game()
    try:
        cs_raw = _csharp_init(g, sc)
        cs_monsters = [m["kind"] for m in (cs_raw.get("monsters") or [])]
        rs_state = _rust_init(sc, monsters=cs_monsters)
        _drain_rust_choices(rs_state)
        cs = _enrich_csharp(cs_raw)
        rs = _enrich_rust(rs_state)
        print("\n--- after init ---")
        print("  C#:\n" + _fmt(cs))
        print("  RS:\n" + _fmt(rs))
        d = diff(cs, rs)
        if d:
            print(f"  >>> DIVERGENCE at init: {d[:5]}")
            return 0
        turn = 0
        step = 0
        while turn < max_turns:
            cs_actions = _csharp_legal(g)
            rs_actions = _rust_legal(rs_state)
            if cs_actions != rs_actions:
                print(f"\n--- step {step} turn {turn}: LEGAL_ACTION divergence ---")
                print("  C#-only:", [a for a in cs_actions if a not in rs_actions])
                print("  RS-only:", [a for a in rs_actions if a not in cs_actions])
                return 0
            if not cs_actions:
                break
            action = rng.choice(cs_actions)
            if action["action"] == "play_card":
                ci = action.get("card_index", 0)
                hand = cs.get("hand_ids") or []
                action = dict(action)
                action["_card_id"] = hand[ci] if 0 <= ci < len(hand) else None
            print(f"\n--- step {step} turn {turn}: ACTION {action} ---")
            cs_result = _apply_csharp(g, action)
            _drain_csharp_choices(g, cs_result)
            _apply_rust(rs_state, action)
            _drain_rust_choices(rs_state)
            cs = _enrich_csharp(g.get_state())
            rs = _enrich_rust(rs_state)
            d = diff(cs, rs)
            print("  C#:\n" + _fmt(cs))
            print("  RS:\n" + _fmt(rs))
            if d:
                print(f"  >>> FIRST DIVERGENCE: {json.dumps(d[:8])}")
                return 0
            if action["action"] == "end_turn":
                turn += 1
            step += 1
        print("\n=== clean through max_turns ===")
    finally:
        try:
            g.close()
        except Exception:
            pass
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
