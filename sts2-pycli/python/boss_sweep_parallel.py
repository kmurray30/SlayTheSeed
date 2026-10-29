"""Parallel boss-only sweep — fan the independent Vantom fights across cores.

`boss_only.py` runs fights sequentially on one core; on a many-core box that
wastes ~Ncore-1 cores. Each (run, sim-level) fight is fully independent, so
this driver builds the full grid of cells and runs them through a process pool
(sidestepping the GIL — `mcts_play_to_terminal` is a long Rust call per cell).

Every cell is actually played (no monotone carry-forward), so the resulting
table is fully rigorous: the per-run W/L matrix shows the true result at each
sim level, including any non-monotonic flips. Wall time is bounded by the
slowest single fight, not the sum.

Usage:
  python boss_sweep_parallel.py --sweep 1000,30000,50000,100000,150000 \
      --jobs 40 --out boss_sweep_results.md
"""

from __future__ import annotations

import argparse
import os
import sys
import time
from multiprocessing import Pool

import act1_replay as ar
import boss_only as bo

sts2_rs = ar.sts2_rs

_KNOWN_POTIONS = None


def _known_potions():
    global _KNOWN_POTIONS
    if _KNOWN_POTIONS is None:
        _KNOWN_POTIONS = set(sts2_rs.list_registry_names("potion"))
    return _KNOWN_POTIONS


def _cell(task):
    """Worker: rebuild the scenario and play one (run, sims) cell.

    Returns a flat picklable tuple. Rebuilds the scenario in-process (cheap,
    ~ms) because the PyCombatState handle isn't worth pickling across procs.
    """
    run, sims, seed = task
    s10 = run.seed[:10]
    try:
        pr = bo._prepare_run(run, seed, _known_potions())
        t0 = time.time()
        res = sts2_rs.mcts_play_to_terminal(
            pr["cs"].clone(), sims=sims, max_rollout_depth=10, seed=seed
        )
        wall = (time.time() - t0) * 1000.0
        return (s10, sims, bool(res["player_won"]), res["final_hp"], res["turns"], wall, None)
    except Exception as e:
        return (s10, sims, None, None, None, 0.0, str(e))


def main(argv=None) -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--sweep", default="1000,30000,50000,100000,150000")
    p.add_argument("--jobs", type=int, default=max(1, (os.cpu_count() or 2) - 8))
    p.add_argument("--limit", type=int, default=0)
    p.add_argument("--seed", type=int, default=7)
    p.add_argument("--out", default="boss_sweep_results.md")
    args = p.parse_args(argv)

    sim_levels = [int(x) for x in args.sweep.split(",") if x.strip()]
    runs = [r for r in ar.iter_v103_act1_runs("the-ironclad", False) if r.scraped_won]
    if args.limit:
        runs = runs[: args.limit]

    # Lightweight per-run display metadata (deck/relic/potion/hp) — no cs.
    prepared = []
    for r in runs:
        deck, relics, hp, max_hp = bo.reconstruct_boss_entry(r)
        potions = bo.reconstruct_boss_potions(r, known=_known_potions())
        prepared.append(
            {
                "run": r,
                "deck": len(deck),
                "relics": len(relics),
                "potions": potions,
                "hp": hp,
                "max_hp": max_hp,
            }
        )
    potion_runs = sum(bool(pr["potions"]) for pr in prepared)

    cells = [(r, sims, args.seed) for sims in sim_levels for r in runs]
    print(
        f"boss_sweep_parallel: {len(runs)} runs × {len(sim_levels)} levels "
        f"= {len(cells)} fights; jobs={args.jobs}",
        flush=True,
    )

    results = {pr["run"].seed[:10]: {} for pr in prepared}
    done_per_level = {s: 0 for s in sim_levels}
    n = len(runs)
    errored = 0
    t_start = time.time()

    # Run biggest sim-levels first so the long-pole fights start early and the
    # cheap ones backfill idle workers near the end.
    cells.sort(key=lambda c: -c[1])
    with Pool(processes=args.jobs) as pool:
        for s10, sims, won, fhp, turns, wall, err in pool.imap_unordered(_cell, cells, chunksize=1):
            if err is not None:
                errored += 1
                results[s10][sims] = (None, None, None, 0.0, False)
                print(f"  {s10:<10} sims={sims:>6} ERROR: {err}", flush=True)
            else:
                results[s10][sims] = (won, fhp, turns, wall, False)
                done_per_level[sims] += 1
                tag = "WON " if won else "lost"
                print(
                    f"  {s10:<10} sims={sims:>6} -> {tag} "
                    f"(hp={fhp:>3}, turns={turns:>2}, {wall / 1000:6.1f}s) "
                    f"[{done_per_level[sims]}/{n} @ this level]",
                    flush=True,
                )
            # When a level fully lands, refresh the on-disk report.
            if done_per_level.get(sims, 0) == n:
                _emit(args.out, sim_levels, prepared, results, potion_runs, errored, t_start)

    _emit(args.out, sim_levels, prepared, results, potion_runs, errored, t_start, final=True)
    return 0


def _build_summary(sim_levels, results, n):
    summary = []
    for s in sim_levels:
        cells = [results[k].get(s) for k in results]
        won = sum(1 for c in cells if c and c[0])
        wall = sum(c[3] for c in cells if c)
        summary.append((s, won, n, wall))
    return summary


def _first_win(sim_levels, results):
    fw = {}
    for k, by_s in results.items():
        for s in sim_levels:
            c = by_s.get(s)
            if c and c[0]:
                fw[k] = s
                break
    return fw


def _emit(path, sim_levels, prepared, results, potion_runs, errored, t_start, final=False):
    n = len(prepared)
    summary = _build_summary(sim_levels, results, n)
    first_win = _first_win(sim_levels, results)
    bo._write_report(path, sim_levels, prepared, results, summary, potion_runs, errored, first_win)
    elapsed = time.time() - t_start
    levels_done = [f"{s // 1000}k={w}/{tot}" for s, w, tot, _ in summary if w or _ > 0]
    print(
        f"  ── report refreshed ({elapsed / 60:.1f} min elapsed): " + " ".join(levels_done),
        flush=True,
    )
    if final:
        bo._print_tables(sim_levels, prepared, results, summary, first_win)
        print(f"\n(report written to {path}; total wall {elapsed / 60:.1f} min)")


if __name__ == "__main__":
    sys.exit(main())
