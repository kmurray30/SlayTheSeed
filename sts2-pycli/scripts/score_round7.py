"""Score the round-7 sweep: DLL arm v37wr and engine arm e37.

Honours the three documented gotchas:
  * report.json lives at s<seed>/<timestamp>_<pid>/report.json, not under s<seed>/
  * runs[0].error must be checked BEFORE scoring — a harness abort reads like a loss
  * engine shard logs print "WIN  seed=" with TWO spaces
"""

from __future__ import annotations

import json
import re
from pathlib import Path

PYCLI = Path(__file__).resolve().parent.parent
SEEDS = range(1000, 1060)


def score_dll(tag: str) -> dict:
    root = PYCLI / "logs" / f"wr_{tag}"
    wins, losses, errors, missing = [], [], [], []
    floors, world_errs = [], 0
    desyncs = decisions = 0
    for seed in SEEDS:
        reports = sorted((root / f"s{seed}").glob("*/report.json"))
        if not reports:
            missing.append(seed)
            continue
        run = json.loads(reports[-1].read_text())["runs"][0]
        if run.get("error"):
            errors.append((seed, run["error"]))
            continue
        world_errs += (run.get("world_errors") or {}).get("count", 0)
        ms = run.get("mirror_stats") or {}
        desyncs += ms.get("desyncs", 0)
        decisions += ms.get("decisions", 0)
        floors.append(run.get("floor") or 0)
        (wins if run.get("won") else losses).append(seed)
    return {
        "wins": wins,
        "losses": losses,
        "errors": errors,
        "missing": missing,
        "world_errors": world_errs,
        "mean_floor": sum(floors) / len(floors) if floors else 0.0,
        "desyncs": desyncs,
        "decisions": decisions,
        "desync_per_1k": 1000 * desyncs / decisions if decisions else 0.0,
    }


def score_engine(tag: str) -> dict:
    root = PYCLI / "logs" / f"eng_{tag}"
    wins, losses = [], []
    for log in sorted(root.glob("shard_*.log")):
        for line in log.read_text(errors="replace").splitlines():
            m = re.search(r"\b(WIN|LOSS)\s+seed=(\d+)", line)
            if m:
                (wins if m.group(1) == "WIN" else losses).append(int(m.group(2)))
    return {"wins": sorted(wins), "losses": sorted(losses)}


def fisher(a: int, b: int, c: int, d: int) -> float:
    """Two-sided Fisher exact p for [[a,b],[c,d]] without scipy."""
    from math import comb

    n = a + b + c + d
    row1, col1 = a + b, a + c

    def p_of(x: int) -> float:
        return comb(row1, x) * comb(n - row1, col1 - x) / comb(n, col1)

    obs = p_of(a)
    lo = max(0, col1 - (n - row1))
    hi = min(row1, col1)
    return sum(p_of(x) for x in range(lo, hi + 1) if p_of(x) <= obs * (1 + 1e-9))


if __name__ == "__main__":
    d = score_dll("v37wr")
    e = score_engine("e37")
    nd, ne = len(d["wins"]) + len(d["losses"]), len(e["wins"]) + len(e["losses"])

    print(f"DLL arm  v37wr : {len(d['wins'])}/{nd} = {100 * len(d['wins']) / max(nd, 1):.1f}%")
    print(f"  mean floor   : {d['mean_floor']:.1f}")
    print(f"  world errors : {d['world_errors']}")
    print(
        f"  desyncs      : {d['desyncs']} over {d['decisions']} decisions"
        f"  = {d['desync_per_1k']:.1f}/1k"
    )
    print(f"  harness errs : {len(d['errors'])}  {[s for s, _ in d['errors']]}")
    print(f"  missing      : {d['missing']}")
    print(f"  win seeds    : {d['wins']}")
    print()
    print(f"engine arm e37 : {len(e['wins'])}/{ne} = {100 * len(e['wins']) / max(ne, 1):.1f}%")
    print(f"  win seeds    : {e['wins']}")
    print()
    if nd and ne:
        p = fisher(len(d["wins"]), len(d["losses"]), len(e["wins"]), len(e["losses"]))
        gap = 100 * len(e["wins"]) / ne - 100 * len(d["wins"]) / nd
        print(f"gap v37wr vs e37 : {gap:+.1f}pp   Fisher p = {p:.3f}")
    # vs the round-5 prior
    p_prior = fisher(len(d["wins"]), len(d["losses"]), 27, 33)
    print(f"v37wr vs v36wr (27/60) : Fisher p = {p_prior:.3f}")
