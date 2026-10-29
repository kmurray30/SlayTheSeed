"""Combat-parity probe: does the engine reproduce the real DLL's fight outcome?

Tests the H-parity hypothesis behind the rung-3.5 combat-bleed finding
(`docs/experiments/2026-07-19-hp-flow-attribution.md`): the real-DLL runs
arrive at act bosses ~15 HP low ENTIRELY via combat bleed that ESCALATES
with act depth (+0.7 -> +6.1 HP/fight). Two live sub-hypotheses:

- **H-search** — the search picks HP-costlier LINES (lossy state / eval);
- **H-parity** — the engine MISPREDICTS deep-act fights (optimistic on the
  monster kits), so identical lines cost more HP in the DLL than planned.

This probe tests H-parity directly and offline, with NO live game and NO
RNG accumulation: it is a ONE-STEP transition differ. For every recorded
``end_turn`` from a combat decision, it rebuilds the wire state in the
CURRENT engine (``adapter.translate -> state_from_dict``), applies the same
``end_turn``, and diffs the engine's PREDICTED post-enemy-turn player HP
(and living-enemy count) against what the DLL ACTUALLY produced on the next
turn. This is exactly the prior-art differ that caught the intent
double-count bug (state_dict.rs ``unbake_wire_intent``), rebuilt.

Signed HP error = predicted_hp - actual_hp:
  > 0  engine OPTIMISTIC (thinks the player kept MORE HP than reality) ->
       the search under-defends -> HP bleed. This is the H-parity fingerprint.
  < 0  engine pessimistic (rare; e.g. it double-counts a debuff).
Living-enemy delta = pred_alive - actual_alive:
  < 0  engine sees FEWER enemies alive than the DLL -> revive/summon
       blindness (dead-but-reviving segments, summons) -> fight modeled as
       shorter than it is -> under-defense.

Input: dirs of headless per-decision logs (``logs/headless/*.jsonl``,
``{step, state, action}`` records from ``realgame_random.JsonlLogger``). Use
RECENT logs (post-affliction-wire / current wire schema) as ground truth;
the DLL behavior is wheel-independent, the engine prediction uses the
installed wheel.

Usage::

    python python/diag_combat_parity.py logs/headless          # all *.jsonl
    python python/diag_combat_parity.py logs/headless --since 20260719
"""

from __future__ import annotations

import argparse
import glob
import json
import os
from collections import defaultdict
from pathlib import Path
from statistics import mean
from typing import Any

from realgame_adapter import character_display_name


def _alive_pred(enc: list[dict[str, Any]]) -> int:
    return sum(1 for e in enc if not e.get("is_dead") and not e.get("is_escaped"))


def _alive_actual(enemies: list[dict[str, Any]]) -> int:
    # The wire lists living enemies; guard on hp>0 for any that linger at 0.
    return sum(1 for e in enemies if (e.get("hp") or 0) > 0)


def _roster_key(state: dict[str, Any]) -> str:
    names = sorted({str(e.get("name") or "?") for e in (state.get("enemies") or [])})
    return "+".join(names) if names else "?"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("log_dirs", type=Path, nargs="+", help="dir(s) of headless *.jsonl logs")
    ap.add_argument(
        "--since", default=None, help="only files whose basename >= this (e.g. 20260719)"
    )
    args = ap.parse_args()

    # Import the engine lazily so --help works without the wheel.
    import sts2_rs
    from sts2_hint import adapter

    print(f"engine src_hash: {sts2_rs.src_hash()}")

    files: list[str] = []
    for d in args.log_dirs:
        for p in glob.glob(str(d / "*.jsonl")):
            if args.since and os.path.basename(p) < args.since:
                continue
            files.append(p)
    files.sort()
    print(f"log files: {len(files)}")

    # per-act accumulators
    per_act: dict[int, dict[str, list[float]]] = defaultdict(
        lambda: {"signed": [], "abs": [], "alive_diff": []}
    )
    per_roster: dict[str, list[float]] = defaultdict(list)
    # fatal turns: enemy killed the player this end_turn (next state game_over).
    # Did the engine SEE the lethal (pred_hp<=0) or miss it (pred survival)?
    fatal: dict[int, dict[str, int]] = defaultdict(lambda: {"n": 0, "saw": 0, "missed": 0})
    n_tx = n_recon_fail = n_apply_fail = 0

    for fp in files:
        try:
            lines = [
                json.loads(x)
                for x in Path(fp).read_text(encoding="utf-8").splitlines()
                if x.strip()
            ]
        except (OSError, json.JSONDecodeError):
            continue
        for i in range(len(lines) - 1):
            s0 = lines[i].get("state") or {}
            a0 = lines[i].get("action") or {}
            s1 = lines[i + 1].get("state") or {}
            if s0.get("decision") != "combat_play" or a0.get("action") != "end_turn":
                continue
            nxt = s1.get("decision")
            # score the next player-turn combat decision (clean incoming-damage
            # comparison) OR a fatal turn (enemy killed the player -> game_over).
            if nxt not in ("combat_play", "game_over"):
                continue
            hp0 = (s0.get("player") or {}).get("hp")
            if hp0 is None:
                continue
            act = int((s0.get("context") or {}).get("act") or 0)
            char = (
                character_display_name((s0.get("run_state") or {}).get("character")) or "Ironclad"
            )
            try:
                wire = adapter.translate(s0, character=char, combat_id=0, seed=0, ascension=0)
                cs = sts2_rs.state_from_dict(wire)
            except Exception:
                n_recon_fail += 1
                continue
            try:
                cs.apply({"action": "end_turn"})
                d = sts2_rs.state_to_dict(cs)
            except Exception:
                n_apply_fail += 1
                continue
            pred_hp = (d.get("player") or {}).get("hp")
            if pred_hp is None:
                continue
            if nxt == "game_over":
                fr = fatal[act]
                fr["n"] += 1
                fr["saw" if pred_hp <= 0 else "missed"] += 1
                continue
            hp1 = (s1.get("player") or {}).get("hp")
            if hp1 is None:
                continue
            signed = float(pred_hp - hp1)
            enc = d.get("encounter") or []
            alive_diff = float(_alive_pred(enc) - _alive_actual(s1.get("enemies") or []))
            per_act[act]["signed"].append(signed)
            per_act[act]["abs"].append(abs(signed))
            per_act[act]["alive_diff"].append(alive_diff)
            per_roster[_roster_key(s0)].append(signed)
            n_tx += 1

    print(
        f"end_turn transitions scored: {n_tx}  "
        f"(recon-fail {n_recon_fail}, apply-fail {n_apply_fail})\n"
    )
    if not n_tx:
        return 1

    print("=== per-act incoming-damage parity (signed = pred_hp - actual_hp) ===")
    print("  signed>0 => engine OPTIMISTIC (under-predicts HP lost) => bleed fingerprint")
    for act in sorted(per_act):
        s = per_act[act]["signed"]
        a = per_act[act]["abs"]
        ad = per_act[act]["alive_diff"]
        n = len(s)
        opt = sum(1 for x in s if x > 0)
        pess = sum(1 for x in s if x < 0)
        exact = sum(1 for x in s if x == 0)
        revive_blind = sum(1 for x in ad if x < 0)
        print(
            f"  act{act or '?'}: n={n:4d}  mean_signed={mean(s):+5.2f}  mean_abs={mean(a):4.2f}  "
            f"exact={exact / n:4.0%}  optimistic={opt / n:4.0%}  pessimistic={pess / n:4.0%}  "
            f"| enemy-undercount(revive/summon)={revive_blind / n:4.0%}"
        )

    print("\n=== fatal turns (enemy killed the player) — did the engine SEE the lethal? ===")
    for act in sorted(fatal):
        fr = fatal[act]
        if fr["n"]:
            print(
                f"  act{act or '?'}: n={fr['n']:3d}  predicted-death={fr['saw']}  "
                f"MISSED-lethal(predicted survival)={fr['missed']}"
            )

    print("\n=== rosters with the largest optimistic HP bias (mean signed, n>=8) ===")
    ranked = sorted(
        ((mean(v), len(v), k) for k, v in per_roster.items() if len(v) >= 8),
        reverse=True,
    )
    for msigned, n, k in ranked[:15]:
        if msigned <= 0.05:
            break
        print(f"  {msigned:+5.2f}  (n={n:3d})  {k}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
