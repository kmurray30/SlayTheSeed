#!/usr/bin/env python3
"""Issue #40 experiment driver — win-rate of boss-sim card-reward selection.

Runs full self-played Ironclad runs (MCTS combat + heuristic/tiered meta,
card rewards picked by ``boss_sim_card_reward`` at ``--reward-sims``) over a
seed panel and reports full-run win-rate + Act-1 survival.

Seeds come from ``run_{i}`` → integer ``i`` (matching
``play_full_run_rust.py``'s ``_hash_seed``), so seed ``i`` here is the same
combat/run seed that the stock CLI would use for run ``i``.

Each seed runs in its own process with ``threads=1`` so the sweep is
reproducible (no rayon thread-order nondeterminism) and 4 cores are filled by
4 concurrent seeds rather than one over-subscribed MCTS.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from concurrent.futures import ProcessPoolExecutor, as_completed
from pathlib import Path

# play_run_mcts lives next to this file.
sys.path.insert(0, str(Path(__file__).resolve().parent))


def _run_one(
    seed: int, character: str, reward_sims: int, iters: int, boss_iters: int, max_steps: int
) -> dict:
    import sts2_rs
    from play_full_run_rust import play_run_mcts

    t0 = time.perf_counter()
    try:
        res = play_run_mcts(
            seed,
            character,
            max_steps=max_steps,
            mcts_iters=iters,
            boss_mcts_iters=boss_iters,
            reward_sims=reward_sims or None,
            record_dir=None,
            threads=1,
            verbose=False,
        )
    except Exception as exc:  # keep the sweep alive; record the failure
        return {
            "seed": seed,
            "error": repr(exc),
            "victory": False,
            "elapsed_s": time.perf_counter() - t0,
        }
    res["elapsed_s"] = time.perf_counter() - t0
    return res


def _write_summary(args, seeds: list[int], results: list[dict], wall: float) -> dict:
    """Build the aggregate summary and (atomically) write it to ``args.out``.

    Called after every completed seed so a long run that is stopped early still
    leaves a valid (partial) JSON on disk.
    """
    rows = sorted(results, key=lambda r: r.get("seed", 0))
    n = len(rows)
    wins = sum(1 for r in rows if r.get("victory"))
    errors = sum(1 for r in rows if "error" in r)
    # act_index is 1-based (act1_replay uses act_index > 1 == "survived Act 1").
    reached_act2 = sum(1 for r in rows if (r.get("act") or 0) >= 2)
    reached_act3 = sum(1 for r in rows if (r.get("act") or 0) >= 3)
    summary = {
        "character": args.character,
        "reward_sims": args.reward_sims,
        "iters": args.iters,
        "boss_iters": args.boss_iters,
        "seeds": n,
        "seeds_planned": len(seeds),
        "seed_range": [seeds[0], seeds[-1]],
        "wins": wins,
        "win_rate": wins / n if n else 0.0,
        "reached_act2": reached_act2,
        "act1_survival_rate": reached_act2 / n if n else 0.0,
        "reached_act3": reached_act3,
        "errors": errors,
        "wall_s": wall,
        "results": rows,
    }
    out = Path(args.out)
    tmp = out.with_suffix(out.suffix + ".tmp")
    tmp.write_text(json.dumps(summary, indent=2))
    tmp.replace(out)
    return summary


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--character", default="Ironclad")
    ap.add_argument("--reward-sims", type=int, required=True, help="0 disables boss-sim chooser")
    ap.add_argument("--iters", type=int, default=1000, help="MCTS sims/combat decision")
    ap.add_argument("--boss-iters", type=int, default=10_000, help="MCTS sims/boss decision")
    ap.add_argument("--seeds", type=int, default=200, help="seed count (run_1..run_N)")
    ap.add_argument("--seed-start", type=int, default=1)
    ap.add_argument(
        "--max-steps",
        type=int,
        default=4000,
        help="hard step cap; a normal full run is ~150-350 steps, so 4000 only "
        "trips runaway meta-policy decision loops (counted as a non-win timeout)",
    )
    ap.add_argument("--workers", type=int, default=os.cpu_count() or 4)
    ap.add_argument("--out", required=True, help="JSON results path")
    args = ap.parse_args()

    seeds = list(range(args.seed_start, args.seed_start + args.seeds))
    print(
        f"[exp] {args.character} reward-sims={args.reward_sims} "
        f"iters={args.iters}/{args.boss_iters} seeds={len(seeds)} "
        f"workers={args.workers}",
        flush=True,
    )

    results: list[dict] = []
    t_start = time.perf_counter()
    done = 0
    with ProcessPoolExecutor(max_workers=args.workers) as pool:
        futs = {
            pool.submit(
                _run_one,
                s,
                args.character,
                args.reward_sims,
                args.iters,
                args.boss_iters,
                args.max_steps,
            ): s
            for s in seeds
        }
        for fut in as_completed(futs):
            r = fut.result()
            results.append(r)
            done += 1
            status = (
                "ERR"
                if "error" in r
                else ("WIN" if r.get("victory") else ("TO" if r.get("timeout") else "LOSS"))
            )
            print(
                f"[exp] {done}/{len(seeds)} seed={r.get('seed')} {status} "
                f"act={r.get('act', '-')} floor={r.get('floor', '-')} "
                f"hp={r.get('hp', '-')} ({r.get('elapsed_s', 0):.1f}s)",
                flush=True,
            )
            # Checkpoint after every seed so a stopped long run (e.g. the
            # 10000-sim budget) still leaves usable partial data on disk.
            _write_summary(args, seeds, results, time.perf_counter() - t_start)

    wall = time.perf_counter() - t_start
    summary = _write_summary(args, seeds, results, wall)
    reached_act2 = summary["reached_act2"]
    reached_act3 = summary["reached_act3"]
    wins = summary["wins"]
    n = summary["seeds"]
    errors = summary["errors"]
    print(
        f"[exp] DONE reward-sims={args.reward_sims}: "
        f"wins={wins}/{n} ({summary['win_rate'] * 100:.1f}%) "
        f"act1-survival={reached_act2}/{n} ({summary['act1_survival_rate'] * 100:.1f}%) "
        f"act3-reach={reached_act3} errors={errors} wall={wall:.0f}s -> {args.out}",
        flush=True,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
