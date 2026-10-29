"""Advisor search throughput: the real-game advisor's MCTS call on one fixed position.

Same position as the August 2026 laptop benchmark behind the old "~49 k sims/s,
10 threads" figure (Ironclad A5, 16-card deck, 55/80 HP, Burning Blood vs
Vantom), with warm-up, repetitions and the advisor's real settings. The
`sts2_rs.mcts_recommend` arguments are the ones the advisor passes
(realgame_advisor.compute_combat_advice with its RecommendConfig defaults):
beliefdraw, draw widening 2.0, heuristic rollouts, depth 200, seed 0,
fair=False, common_random_numbers=False, c_puct from the advisor's
belief_draw_c_puct(sims, threads) rule. Default cell = the advisor's defaults:
8,000 sims, 8 root-parallel threads (MAX_SEARCH_THREADS = 8, --sims 8000).

One warm-up call per cell, then --reps timed calls (median, min, max). Prints a
digest of the result's actions+pv so base and new can be checked for identity.
`--pin-workers CPULIST` (off by default) pins the search's worker threads one
per CPU after the first multi-thread warm-up, which removes the scheduler's
stacking of two workers on one CPU (see README.md, "CPU masks").

  python -I advisor_bench.py --mod-dir <dir with sts2_rs.abi3.so> --label new
"""

import argparse
import hashlib
import json
import os
import platform
import statistics
import sys
import time

DECK = [
    "STRIKE_IRONCLAD", "STRIKE_IRONCLAD", "STRIKE_IRONCLAD", "STRIKE_IRONCLAD",
    "STRIKE_IRONCLAD", "DEFEND_IRONCLAD", "DEFEND_IRONCLAD", "DEFEND_IRONCLAD",
    "DEFEND_IRONCLAD", "BASH", "SHRUG_IT_OFF", "POMMEL_STRIKE", "ANGER",
    "CONFLAGRATION", "BATTLE_TRANCE", "CATASTROPHE",
]
RELICS = ["BURNING_BLOOD"]


def belief_draw_c_puct(sims: int, threads: int) -> float:
    """Mirror of sts2_hint.recommender.belief_draw_c_puct (per-tree budget rule)."""
    per_tree = sims // max(1, threads)
    if per_tree < 500:
        return 0.8
    if per_tree < 1000:
        return 1.41
    return 2.5


def pin_workers(cpulist: str) -> dict:
    """Pin every thread of this process except the main one (the rayon pool's
    workers, the only other threads) 1:1 to the CPUs of `cpulist`."""
    cpus = []
    for part in cpulist.split(","):
        lo, _, hi = part.partition("-")
        cpus.extend(range(int(lo), int(hi or lo) + 1))
    main_tid = os.getpid()
    workers = sorted(int(t) for t in os.listdir("/proc/self/task") if int(t) != main_tid)
    if len(workers) > len(cpus):
        raise SystemExit(f"--pin-workers: {len(workers)} worker threads but only {len(cpus)} CPUs "
                         "(the pool has one worker per CPU of the process mask)")
    for tid, cpu in zip(workers, cpus):
        os.sched_setaffinity(tid, {cpu})
    return {str(tid): cpu for tid, cpu in zip(workers, cpus)}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mod-dir", required=True)
    ap.add_argument("--label", default="")
    ap.add_argument("--encounter", default="VANTOM_BOSS")
    ap.add_argument("--ascension", type=int, default=5)
    ap.add_argument("--threads", default="1,8")
    ap.add_argument("--sims", default="8000")
    ap.add_argument("--reps", type=int, default=7)
    ap.add_argument("--seed", type=int, default=0, help="search seed (the advisor's RecommendConfig.seed is 0)")
    ap.add_argument("--cpuct", choices=("aug", "advisor"), default="advisor",
                    help="aug = fixed 2.5 like the 2026-08-10 script; advisor = belief_draw_c_puct rule")
    ap.add_argument("--pin-workers", default=None, metavar="CPULIST",
                    help="Linux: after the first multi-thread warm-up call, pin each search worker "
                         "thread to its own CPU of this list (e.g. 6-13), so the scheduler cannot "
                         "stack two workers on one CPU. Off by default (the scheduler places them).")
    a = ap.parse_args()

    sys.path.insert(0, os.path.abspath(a.mod_dir))
    import sts2_rs  # noqa: E402

    loaded = os.path.abspath(sts2_rs.__file__)
    if not loaded.startswith(os.path.abspath(a.mod_dir)):
        raise SystemExit(f"loaded the wrong sts2_rs: {loaded}")
    info = getattr(sts2_rs, "build_info", None)
    print(json.dumps({"provenance": {
        "label": a.label, "wheel": loaded,
        "src_hash": sts2_rs.src_hash() if hasattr(sts2_rs, "src_hash") else None,
        "build_info": info() if info else None,
        "python": platform.python_version(),
        # Linux; elsewhere the OS has no per-process affinity call here.
        "affinity": (
            sorted(os.sched_getaffinity(0)) if hasattr(os, "sched_getaffinity") else None
        ),
        "encounter": a.encounter, "ascension": a.ascension, "seed": a.seed,
    }}, default=str), flush=True)

    cs = sts2_rs.build_scenario_state(42, "Ironclad", DECK, 55, 80, RELICS, a.encounter, a.ascension)

    def call(sims, threads, cp):
        t0 = time.perf_counter()
        r = sts2_rs.mcts_recommend(
            cs, sims=sims, c_puct=cp, depth=200, seed=a.seed, threads=threads, fair=False,
            rollout_policy="heuristic", hidden_info="beliefdraw", draw_widening_c=2.0,
            common_random_numbers=False,
        )
        return time.perf_counter() - t0, r

    pinned = None
    for sims in [int(s) for s in a.sims.split(",")]:
        for threads in [int(t) for t in a.threads.split(",")]:
            cp = 2.5 if a.cpuct == "aug" else belief_draw_c_puct(sims, threads)
            _, r = call(sims, threads, cp)  # warm-up (rayon pool, caches)
            if a.pin_workers and threads > 1 and pinned is None:
                pinned = pin_workers(a.pin_workers)  # the warm-up created the rayon pool
                print(json.dumps({"pinned_workers": pinned}), flush=True)
            digest = hashlib.md5(json.dumps({"actions": r.get("actions"), "pv": r.get("pv")},
                                            sort_keys=True, default=str).encode()).hexdigest()[:12]
            ts = [call(sims, threads, cp)[0] for _ in range(a.reps)]
            med = statistics.median(ts)
            print(json.dumps({
                "sims": sims, "threads": threads, "c_puct": cp, "reps": a.reps,
                "median_s": round(med, 5), "min_s": round(min(ts), 5), "max_s": round(max(ts), 5),
                "sims_per_s_median": round(sims / med), "digest": digest,
            }), flush=True)


if __name__ == "__main__":
    main()
