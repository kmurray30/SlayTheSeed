"""Wall-clock bench against the design's performance budget.

Budget (16 cores): 10k seeds x heuristic-class strategies < 2 min;
mcts-class strategies: budget guided by ~0.5-1s/combat. This bench
extrapolates from small samples.
"""

from __future__ import annotations

import time

from simlab.runner import play_combat
from simlab.specs import ScenarioSpec


def bench(seeds: int = 50) -> dict[str, float]:
    spec = ScenarioSpec(encounter="MAWLER_NORMAL")
    out: dict[str, float] = {}
    for strategy, n in [("min_overblock", seeds), ("mcts", max(3, seeds // 20))]:
        t0 = time.perf_counter()
        for s in range(n):
            play_combat(spec, strategy, s)
        per = (time.perf_counter() - t0) / n
        out[strategy] = per
        print(f"{strategy:<16} {per * 1000:8.1f} ms/combat  ({1 / per:7.1f}/s/core)")
    return out


if __name__ == "__main__":
    bench()
