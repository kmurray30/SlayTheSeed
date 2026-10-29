"""Summarize a headless_eval --shadow sweep vs the no-shadow baseline.

Reads the sweep's per-run stdout log (or the final report.json when present)
and prints: win rate, per-seed win/loss vs the recorded baseline (flips both
ways), and shadow telemetry (median alive-floors, full-run survival rate,
total combat decisions searched off the lossless native state).

Usage::

    python python/shadow_sweep_summary.py logs/shadow_sweep/shadow_full60.log
    python python/shadow_sweep_summary.py --report logs/headless_eval_shadow_full60/<ts>/report.json
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

# Recorded no-shadow rung-3.5 baseline (seeds 1000-1059, sims 1000/4000,
# honest beliefdraw, veteran): 10/60 wins. See the 2026-07-17/18 re-sweep.
BASELINE_WINS = {1003, 1009, 1014, 1020, 1028, 1029, 1032, 1044, 1051, 1059}
BASELINE_N = 60

_OUTCOME = re.compile(
    r"run \d+ seed (?P<seed>\d+): (?P<result>victory|defeat|max_steps_reached|"
    r"time_cap_reached|stall_detected|error[^ ]*) floor=(?P<floor>\S+)"
)
_SHADOW = re.compile(
    r"run \d+ seed (?P<seed>\d+): shadow alive_floors=(?P<af>\d+) "
    r"combat_searches=(?P<cs>\d+) survived=(?P<sv>True|False)"
)


def from_log(path: Path) -> tuple[dict[int, dict], dict[int, dict]]:
    outcomes: dict[int, dict] = {}
    shadow: dict[int, dict] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        m = _OUTCOME.search(line)
        if m:
            outcomes[int(m["seed"])] = {"result": m["result"], "floor": m["floor"]}
        m = _SHADOW.search(line)
        if m:
            shadow[int(m["seed"])] = {
                "alive_floors": int(m["af"]),
                "combat_searches": int(m["cs"]),
                "survived": m["sv"] == "True",
            }
    return outcomes, shadow


def from_report(path: Path) -> tuple[dict[int, dict], dict[int, dict]]:
    rep = json.loads(path.read_text(encoding="utf-8"))
    outcomes, shadow = {}, {}
    for r in rep.get("runs", []):
        seed = r.get("seed")
        outcomes[seed] = {"result": r.get("result"), "floor": r.get("floor")}
        ss = r.get("shadow_stats")
        if ss:
            shadow[seed] = {
                "alive_floors": ss.get("alive_floors"),
                "combat_searches": ss.get("combat_searches"),
                "survived": ss.get("survived_run"),
            }
    return outcomes, shadow


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("log", type=Path, nargs="*", help="sweep stdout log(s)")
    ap.add_argument("--report", type=Path, help="final report.json instead of a log")
    args = ap.parse_args()

    if args.report:
        outcomes, shadow = from_report(args.report)
    elif args.log:
        outcomes, shadow = {}, {}
        for p in args.log:  # merge across parallel-worker logs
            o, s = from_log(p)
            outcomes.update(o)
            shadow.update(s)
    else:
        ap.error("need a log path or --report")

    done = sorted(outcomes)
    wins = {s for s in done if outcomes[s]["result"] == "victory"}
    print(f"runs completed: {len(done)}/{BASELINE_N}")
    print(f"shadow wins:   {len(wins)}/{len(done)} = {100 * len(wins) / max(1, len(done)):.1f}%")
    base_on_done = BASELINE_WINS & set(done)
    print(f"baseline wins (same seeds): {len(base_on_done)}/{len(done)}")

    gained = sorted(wins - BASELINE_WINS)  # shadow won, baseline lost
    lost = sorted(base_on_done - wins)  # baseline won, shadow lost
    print(f"flips + (shadow win, baseline loss): {gained}")
    print(f"flips - (baseline win, shadow loss): {lost}")

    if shadow:
        af = sorted(v["alive_floors"] for v in shadow.values())
        survived = sum(1 for v in shadow.values() if v["survived"])
        searches = sum(v["combat_searches"] for v in shadow.values())
        print(
            f"\nshadow telemetry ({len(shadow)} runs): "
            f"survived_full_run {survived}/{len(shadow)}, "
            f"alive_floors min/med/max {af[0]}/{af[len(af) // 2]}/{af[-1]}, "
            f"combat_searches total {searches}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
