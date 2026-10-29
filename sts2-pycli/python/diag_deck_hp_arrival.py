"""Deck/HP-arrival diagnostic: engine-world vs real-DLL, matched seeds.

The Phase-1 --shadow sweep ruled out combat search-state fidelity as the
rung-3.5 win-rate gap driver; the residual points at the meta layer (the
deck/HP a run ARRIVES at the killer fights with). This script tests that
directly: over string-seed-matched runs it diffs deck size + HP at matched
floors, and HP-bleed per fight, between the engine-world (~50%) and the
real-DLL world (~10%). It answers: do losing real-DLL runs arrive with
WEAKER DECKS (a meta-decision problem) or LOWER HP (a combat-bleed problem)?

Inputs: two dirs of per-seed world traces (``seed<N>.jsonl``,
``ev: room|combat|run_end``) — one from ``play_full_run_ppo.py --trace-dir
--string-seeds`` (engine), one from ``headless_eval.py --trace-dir``
(real-DLL). Same schema, so they diff directly.

Usage::

    python python/diag_deck_hp_arrival.py logs/diag/engine logs/diag/realdll
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from statistics import mean, median
from typing import Any

# Global-floor anchors for the three act bosses (approx; act1 ~17, act2 ~33,
# act3 ~48). We snap to the deepest room <= anchor that a run actually reached.
BOSS_FLOORS = {1: 17, 2: 33, 3: 48}


def load_traces(d: Path) -> dict[int, list[dict[str, Any]]]:
    out: dict[int, list[dict[str, Any]]] = {}
    for p in sorted(d.glob("seed*.jsonl")):
        try:
            seed = int(p.stem[len("seed") :])
        except ValueError:
            continue
        out[seed] = [
            json.loads(line) for line in p.read_text(encoding="utf-8").splitlines() if line.strip()
        ]
    return out


def rooms(trace: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [r for r in trace if r.get("ev") == "room"]


def combats(trace: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [r for r in trace if r.get("ev") == "combat"]


def run_end(trace: list[dict[str, Any]]) -> dict[str, Any]:
    for r in reversed(trace):
        if r.get("ev") == "run_end":
            return r
    return {}


def at_floor(trace: list[dict[str, Any]], target: int) -> dict[str, Any] | None:
    """Deepest room record at floor <= target (arrival snapshot for an act)."""
    best = None
    for r in rooms(trace):
        f = r.get("floor")
        if isinstance(f, int) and f <= target and (best is None or f > best["floor"]):
            best = r
    return best


def deepest_common_floor(a: list[dict[str, Any]], b: list[dict[str, Any]]) -> int:
    fa = max((r.get("floor", 0) for r in rooms(a)), default=0)
    fb = max((r.get("floor", 0) for r in rooms(b)), default=0)
    return min(fa, fb)


def snap(trace: list[dict[str, Any]], floor: int) -> dict[str, Any] | None:
    best = None
    for r in rooms(trace):
        f = r.get("floor")
        if isinstance(f, int) and f <= floor and (best is None or f > best["floor"]):
            best = r
    return best


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("engine_dir", type=Path, help="engine-world trace dir")
    ap.add_argument("realdll_dir", type=Path, help="real-DLL trace dir")
    args = ap.parse_args()

    eng = load_traces(args.engine_dir)
    dll = load_traces(args.realdll_dir)
    seeds = sorted(set(eng) & set(dll))
    print(f"matched seeds: {len(seeds)}  (engine {len(eng)}, real-DLL {len(dll)})\n")
    if not seeds:
        return 1

    def wins(traces: dict[int, list[dict[str, Any]]]) -> int:
        return sum(1 for s in seeds if run_end(traces[s]).get("victory"))

    print(f"win rate: engine {wins(eng)}/{len(seeds)}   real-DLL {wins(dll)}/{len(seeds)}")
    ef = [run_end(eng[s]).get("floor") or 0 for s in seeds]
    df = [run_end(dll[s]).get("floor") or 0 for s in seeds]
    print(f"death/end floor (median): engine {median(ef):.0f}   real-DLL {median(df):.0f}\n")

    # (1) Deck size + HP at the DEEPEST COMMON floor per seed — the fair
    # matched-arrival comparison (both worlds definitely reached it).
    print("=== deck size & HP at each seed's deepest COMMON floor ===")
    dsz_e, dsz_d, hp_e, hp_d = [], [], [], []
    deck_smaller = hp_lower = 0
    for s in seeds:
        f = deepest_common_floor(eng[s], dll[s])
        re_, rd = snap(eng[s], f), snap(dll[s], f)
        if not re_ or not rd:
            continue
        de, dd = re_.get("deck_size"), rd.get("deck_size")
        he, hd = re_.get("hp"), rd.get("hp")
        if None in (de, dd, he, hd):
            continue
        dsz_e.append(de)
        dsz_d.append(dd)
        hp_e.append(he)
        hp_d.append(hd)
        deck_smaller += int(rd["deck_size"] < re_["deck_size"])
        hp_lower += int(rd["hp"] < re_["hp"])
    n = len(dsz_e)
    if n:
        print(
            f"  n={n}  deck_size: engine {mean(dsz_e):.1f}  real-DLL {mean(dsz_d):.1f}  "
            f"(d={mean(dsz_d) - mean(dsz_e):+.1f})"
        )
        print(
            f"        HP:      engine {mean(hp_e):.1f}  real-DLL {mean(hp_d):.1f}  "
            f"(d={mean(hp_d) - mean(hp_e):+.1f})"
        )
        print(f"  real-DLL deck SMALLER on {deck_smaller}/{n} seeds; HP LOWER on {hp_lower}/{n}\n")

    # (2) Arrival at each act boss (deepest room <= anchor).
    print("=== arrival at act bosses (deck_size / HP, seeds reaching it in BOTH) ===")
    for act, anchor in BOSS_FLOORS.items():
        rows = []
        for s in seeds:
            re_, rd = at_floor(eng[s], anchor), at_floor(dll[s], anchor)
            if (
                re_
                and rd
                and re_.get("floor", 0) >= (anchor - 6)
                and rd.get("floor", 0) >= (anchor - 6)
            ):
                rows.append((re_, rd))
        if not rows:
            print(f"  act{act} (~floor {anchor}): no seeds reached in both")
            continue
        de = mean(r[0]["deck_size"] for r in rows)
        dd = mean(r[1]["deck_size"] for r in rows)
        he = mean(r[0]["hp"] for r in rows)
        hd = mean(r[1]["hp"] for r in rows)
        print(
            f"  act{act} (~floor {anchor}, n={len(rows)}): "
            f"deck engine {de:.1f} / real-DLL {dd:.1f} (d={dd - de:+.1f});  "
            f"HP engine {he:.1f} / real-DLL {hd:.1f} (d={hd - he:+.1f})"
        )

    # (3) HP-bleed per fight by act — combat-cost signal.
    print("\n=== mean HP lost per combat, by act ===")
    for act in (1, 2, 3):
        be = [c.get("hp_lost", 0) for s in seeds for c in combats(eng[s]) if c.get("act") == act]
        bd = [c.get("hp_lost", 0) for s in seeds for c in combats(dll[s]) if c.get("act") == act]
        if be and bd:
            print(
                f"  act{act}: engine {mean(be):+.1f}/fight (n={len(be)})   "
                f"real-DLL {mean(bd):+.1f}/fight (n={len(bd)})   d={mean(bd) - mean(be):+.1f}"
            )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
