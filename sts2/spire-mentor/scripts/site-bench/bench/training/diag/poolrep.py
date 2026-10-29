"""Replay iteration 0's 960 games through a spawn Pool exactly shaped like
ppo.workers.play_batch (procs workers, apply_async in submission order,
chunksize 1, process reuse), but with NO stall watchdog, and record each
game's start / end timestamps, CPU time and outcome.

Same interpreter / PYTHONPATH / env as replay.py (run via `run.sh pool`).
  poolrep.py --ckpt CK --out OUT.jsonl [--procs 46] [--asc N] [--seeds a,b]
"""

from __future__ import annotations

import argparse
import json
import multiprocessing as mp
import os
import resource
import sys
import time


def timed(arg):
    i, job = arg
    from ppo import workers

    r0 = resource.getrusage(resource.RUSAGE_SELF)
    c0 = time.process_time()
    t0 = time.time()
    row = workers._worker(job)
    t1 = time.time()
    c1 = time.process_time()
    r1 = resource.getrusage(resource.RUSAGE_SELF)
    trans = row.get("transitions") or []
    return {
        "i": i,
        "seed": job[0],
        "asc": job[2],
        "outcome": row.get("outcome"),
        "won": bool(row.get("won")),
        "act1": row.get("act1"),
        "total_floor": (row.get("final") or {}).get("total_floor"),
        "n_transitions": len(trans),
        "error": row.get("error"),
        "wall_s": round(t1 - t0, 3),
        "play_one_wall_s": row.get("wall_s"),
        "cpu_s": round(c1 - c0, 3),
        "utime_s": round(r1.ru_utime - r0.ru_utime, 3),
        "t_start": t0,
        "t_end": t1,
        "pid": os.getpid(),
        "surfaces": [t.get("surface") for t in trans],
        "fine_surfaces": [t.get("fine_surface") for t in trans],
        "final": row.get("final") or {},
    }


def main() -> int:
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from replay import build_acting, jobs_for

    ap = argparse.ArgumentParser()
    ap.add_argument("--ckpt", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--procs", type=int, default=46)
    ap.add_argument("--asc", type=int, default=None)
    ap.add_argument("--seeds", default="")
    a = ap.parse_args()
    acting = os.path.splitext(a.out)[0] + ".acting.pt"
    build_acting(a.ckpt, acting)
    import sts2_rs

    so = getattr(getattr(sts2_rs, "sts2_rs", sts2_rs), "__file__", None)
    jobs = jobs_for(a.ckpt, acting)
    if a.asc is not None:
        jobs = [(i, j) for i, j in jobs if j[2] == a.asc]
    if a.seeds:
        want = {int(s) for s in a.seeds.split(",")}
        jobs = [(i, j) for i, j in jobs if j[0] in want]
    ctx = mp.get_context("spawn")
    t_batch = time.time()
    print(f"[pool] so={so} jobs={len(jobs)} procs={a.procs}", file=sys.stderr, flush=True)
    with open(a.out, "w") as sink:
        sink.write(json.dumps({"meta": True, "so": so, "py_src": os.environ.get("PYTHONPATH"),
                               "procs": a.procs, "n_jobs": len(jobs), "t_batch": t_batch,
                               "loadavg": open("/proc/loadavg").read().split()[:3]}) + "\n")
        with ctx.Pool(processes=a.procs) as pool:
            pending = {pool.apply_async(timed, ((i, j),)): i for i, j in jobs}
            remaining = set(pending)
            n = 0
            last = t_batch
            while remaining:
                just = {ar for ar in remaining if ar.ready()}
                for ar in just:
                    try:
                        rec = ar.get()
                    except Exception as e:  # worker died hard
                        rec = {"i": pending[ar], "outcome": "died", "error": repr(e), "wall_s": None}
                    now = time.time()
                    rec["batch_t_end"] = round(now - t_batch, 3)
                    rec["gap_since_prev_completion_s"] = round(now - last, 3)
                    last = now
                    sink.write(json.dumps(rec) + "\n")
                    sink.flush()
                    n += 1
                    if n % 100 == 0 or len(remaining) - len(just) < 5 or rec.get("outcome") not in ("won", "lost"):
                        print(f"[pool] {n}/{len(jobs)} t={rec['batch_t_end']:.0f}s i={rec['i']} "
                              f"{rec.get('outcome')} wall={rec.get('wall_s')}", file=sys.stderr, flush=True)
                remaining -= just
                if not just:
                    time.sleep(0.1)
        sink.write(json.dumps({"meta_end": True, "batch_wall_s": round(time.time() - t_batch, 3)}) + "\n")
    print(f"[pool] done in {time.time() - t_batch:.1f}s", file=sys.stderr, flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
