#!/usr/bin/env python
"""Score a fixed-ascension live win-rate sweep (`ascension_climb.py --ascension`).

The ledger row this reads is written per RUN, not per graded run, so the two
numbers that matter are kept apart on purpose:

* **graded** -- the game wrote a run-history record, so it says `win` itself.
  Only these enter the win rate.
* **ungraded** -- the run stalled, crashed or timed out and the game never
  recorded an outcome. Scoring those as losses is the single easiest way to
  understate a live win rate (2026-08-03: two "terminated" runs were both
  victories), so they are reported as harness cost, never as results.

A run whose recorded ascension differs from the one requested is quarantined
rather than scored: it measured a different game.

Usage:
    python spire-mentor-mod/tools/score_live_winrate.py logs/.../ironclad-a8 [...]
"""

from __future__ import annotations

import json
import math
import sys
from collections import Counter
from pathlib import Path


def wilson(wins: int, n: int, z: float = 1.96) -> tuple[float, float]:
    """95% Wilson score interval -- the normal approximation is unusable here.

    At n=40 and p near 0.2 the textbook +-1.96*sqrt(pq/n) interval runs off the
    end of [0,1] and reports a lower bound below zero.
    """
    if n == 0:
        return (0.0, 0.0)
    p = wins / n
    d = 1 + z * z / n
    centre = (p + z * z / (2 * n)) / d
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / d
    return (max(0.0, centre - half), min(1.0, centre + half))


def score(path: Path) -> dict:
    ledger = path / "climb.jsonl" if path.is_dir() else path
    rows = [json.loads(line) for line in ledger.open(encoding="utf-8") if line.strip()]
    graded, ungraded, wrong_asc = [], [], []
    for r in rows:
        if r.get("record_win") is None:
            ungraded.append(r)
        elif (
            r.get("record_ascension") is not None
            and r.get("record_ascension") != r.get("ascension")
        ):
            wrong_asc.append(r)
        else:
            graded.append(r)
    wins = [r for r in graded if r["record_win"]]
    losses = [r for r in graded if not r["record_win"]]
    n = len(graded)
    lo, hi = wilson(len(wins), n)
    return {
        "ledger": str(ledger),
        "character": rows[0]["character"] if rows else "?",
        "ascension": rows[0]["ascension"] if rows else None,
        "runs_played": len(rows),
        "graded": n,
        "wins": len(wins),
        "win_rate": len(wins) / n if n else 0.0,
        "ci95": (lo, hi),
        "ungraded": len(ungraded),
        "ungraded_verdicts": Counter(r["verdict"] for r in ungraded),
        "wrong_ascension": len(wrong_asc),
        "mean_floors_all": sum(r["floors"] for r in graded) / n if n else 0.0,
        "mean_floors_loss": (
            sum(r["floors"] for r in losses) / len(losses) if losses else 0.0
        ),
        "median_minutes": (
            sorted(r["minutes"] for r in graded)[n // 2] if n else 0.0
        ),
        "killers": Counter(r.get("killed_by") or "?" for r in losses).most_common(8),
        "win_seeds": [r.get("seed") for r in wins],
    }


def main(argv: list[str]) -> int:
    if not argv:
        print(__doc__)
        return 2
    for arg in argv:
        s = score(Path(arg))
        lo, hi = s["ci95"]
        print(f"=== {s['character']} A{s['ascension']}  ({s['ledger']})")
        print(
            f"  win rate  {s['wins']}/{s['graded']} = {100 * s['win_rate']:.1f}%"
            f"   95% CI [{100 * lo:.1f}%, {100 * hi:.1f}%]"
        )
        print(
            f"  runs played {s['runs_played']}   ungraded {s['ungraded']} "
            f"{dict(s['ungraded_verdicts']) or ''}   wrong-ascension {s['wrong_ascension']}"
        )
        print(
            f"  floors: mean {s['mean_floors_all']:.1f} (losses "
            f"{s['mean_floors_loss']:.1f})   median run {s['median_minutes']} min"
        )
        print(f"  killed by: {s['killers']}")
        print(f"  winning seeds: {s['win_seeds']}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
