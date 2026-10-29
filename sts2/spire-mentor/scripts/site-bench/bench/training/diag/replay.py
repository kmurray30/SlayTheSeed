"""Replay PPO iteration 0's collection games one process per game, with
per-game wall time, CPU time and outcome.

Run with the venv python and PYTHONPATH=<mod-dir>:<py-src> (exactly what the
trainer's spawn workers import). Mirrors ppo.trainer.train()'s acting
checkpoint (torch.manual_seed(0); RunPolicyNet(192,3,4); load_compat(init
ckpt); acting_payload(state, cfg)) and ppo.trainer.collect_rows()'s seed /
ascension / budget wiring, then calls ppo.workers._worker -- the trainer's own
per-job entry point, which books any BaseException (incl. a Rust panic) as
outcome='error'.

Unlike the trainer's pool there is no 600 s stall watchdog: each game gets its
own process and a hard per-game cap (--timeout, default 3600 s), so a slow
game finishes and a hung one is recorded as 'hard_timeout' with its elapsed
time.

  replay.py --ckpt CK --out OUT.jsonl [--asc 4] [--seeds a,b,...] [--procs 46]
            [--timeout 3600] [--order submission|longest-first]
"""

from __future__ import annotations

import argparse
import json
import multiprocessing as mp
import os
import resource
import sys
import time

SEED_BASE = 10_000_000
GAMES = 960
ASC_SPEC = "0:1,1:1,2:1,3:1,4:1,5:1,6:1,7:1,8:1,9:1,10:1"
CLI = dict(
    character="Ironclad",
    sims=250,
    boss_sims=1000,
    ascension_weights=ASC_SPEC,
    save_every=10,
    device="cuda",
    entropy_coef=0.02,
    entropy_coef_final=0.0125,
    entropy_decay_start=40,
    entropy_decay_steps=30,
    epochs=2,
    lr_anneal_final=0.3,
    act1="roll",
    iterations=1,
    games_per_iter=GAMES,
    procs=0,
)


def make_cfg(init_ckpt: str, save_path: str):
    from ppo.trainer import PPOConfig

    return PPOConfig(**CLI, init_ckpt=init_ckpt, save_path=save_path)


def build_acting(init_ckpt: str, out_path: str) -> None:
    import torch

    from ppo.model import RunPolicyNet, load_compat
    from ppo.trainer import _cpu_state_dict, acting_payload

    cfg = make_cfg(init_ckpt, out_path.removesuffix(".acting.pt"))
    torch.manual_seed(0)
    net = RunPolicyNet(dim=cfg.dim, n_layers=cfg.n_layers, n_heads=cfg.n_heads)
    ck = torch.load(init_ckpt, map_location="cpu", weights_only=True)
    load_compat(net, ck.get("state_dict", ck))
    torch.save(acting_payload(_cpu_state_dict(net), cfg), out_path)


def jobs_for(init_ckpt: str, acting: str):
    """(index, job tuple) exactly as collect_rows -> play_batch builds them."""
    from ppo.trainer import high_asc_sims_lists, sample_ascensions

    cfg = make_cfg(init_ckpt, "x")
    seeds = [cfg.seed_base + 0 * cfg.games_per_iter + g for g in range(cfg.games_per_iter)]
    asc = sample_ascensions(cfg, 0, len(seeds))
    sims, boss = high_asc_sims_lists(cfg, asc)
    out = []
    for i, (s, a, sm, bsm) in enumerate(zip(seeds, asc, sims, boss)):
        job = (s, "net", a, cfg.character, False, acting, sm, bsm, cfg.combat,
               cfg.surrogate_path or None, None, cfg.act1, None)
        out.append((i, job))
    return out


def child(i: int, job: tuple, conn) -> None:
    from ppo import workers

    # The worker module (new trainer) imports wheel_guard etc.; import cost is
    # outside the timed window, as in a warm pool worker. Warm the NetPolicy
    # cache too (a pool worker loads it once per process).
    workers._net_policy(job[5])
    r0 = resource.getrusage(resource.RUSAGE_SELF)
    c0 = time.process_time()
    t0 = time.time()
    row = workers._worker(job)
    t1 = time.time()
    c1 = time.process_time()
    r1 = resource.getrusage(resource.RUSAGE_SELF)
    trans = row.get("transitions") or []
    fin = row.get("final") or {}
    rec = {
        "i": i,
        "seed": job[0],
        "asc": job[2],
        "outcome": row.get("outcome"),
        "won": bool(row.get("won")),
        "act1": row.get("act1"),
        "total_floor": fin.get("total_floor"),
        "n_transitions": len(trans),
        "n_decisions": row.get("n_decisions"),
        "steps": row.get("steps"),
        "error": row.get("error"),
        "wall_s": round(t1 - t0, 3),
        "play_one_wall_s": row.get("wall_s"),
        "cpu_s": round(c1 - c0, 3),
        "utime_s": round(r1.ru_utime - r0.ru_utime, 3),
        "stime_s": round(r1.ru_stime - r0.ru_stime, 3),
        "maxrss_mib": round(r1.ru_maxrss / 1024, 1),
        "t_start": t0,
        "t_end": t1,
        "pid": os.getpid(),
        # Enough of the transitions to recompute the trainer's fingerprint
        # fields (per-surface decision counts) without shipping features.
        "surfaces": [t.get("surface") for t in trans],
        "fine_surfaces": [t.get("fine_surface") for t in trans],
        "final": fin,
    }
    conn.send(rec)
    conn.close()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--ckpt", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--acting", default="")
    ap.add_argument("--asc", type=int, default=None)
    ap.add_argument("--seeds", default="")
    ap.add_argument("--procs", type=int, default=46)
    ap.add_argument("--timeout", type=float, default=3600.0)
    ap.add_argument("--order", default="submission", choices=["submission", "longest-first"])
    ap.add_argument("--order-from", default="", help="jsonl with wall_s per i (longest-first)")
    a = ap.parse_args()

    acting = a.acting or (os.path.splitext(a.out)[0] + ".acting.pt")
    build_acting(a.ckpt, acting)
    import sts2_rs  # noqa: F401  (record what imports)

    so = getattr(getattr(sts2_rs, "sts2_rs", sts2_rs), "__file__", None)
    jobs = jobs_for(a.ckpt, acting)
    if a.asc is not None:
        jobs = [(i, j) for i, j in jobs if j[2] == a.asc]
    if a.seeds:
        want = {int(s) for s in a.seeds.split(",")}
        jobs = [(i, j) for i, j in jobs if j[0] in want]
    if a.order == "longest-first" and a.order_from:
        dur = {}
        for line in open(a.order_from):
            r = json.loads(line)
            dur[r["i"]] = r["wall_s"]
        jobs.sort(key=lambda ij: -dur.get(ij[0], 0.0))
    print(f"[replay] so={so} jobs={len(jobs)} procs={a.procs} timeout={a.timeout}",
          file=sys.stderr, flush=True)

    ctx = mp.get_context("spawn")
    pending = list(jobs)
    running: dict = {}
    done = 0
    t_batch = time.time()
    with open(a.out, "w") as sink:
        sink.write(json.dumps({"meta": True, "so": so, "py_src": os.environ.get("PYTHONPATH"),
                               "procs": a.procs, "timeout": a.timeout, "n_jobs": len(jobs),
                               "t_batch": t_batch, "loadavg": open("/proc/loadavg").read().split()[:3]}) + "\n")
        while pending or running:
            while pending and len(running) < a.procs:
                i, job = pending.pop(0)
                rd, wr = ctx.Pipe(duplex=False)
                p = ctx.Process(target=child, args=(i, job, wr), daemon=True)
                p.start()
                wr.close()
                running[i] = (p, rd, job, time.time())
            time.sleep(0.05)
            for i in list(running):
                p, rd, job, ts = running[i]
                rec = None
                if rd.poll():
                    try:
                        rec = rd.recv()
                    except EOFError:
                        rec = None
                    p.join(timeout=30)
                    if rec is None:
                        rec = {"i": i, "seed": job[0], "asc": job[2], "outcome": "died",
                               "exitcode": p.exitcode, "wall_s": round(time.time() - ts, 3)}
                elif not p.is_alive():
                    rec = {"i": i, "seed": job[0], "asc": job[2], "outcome": "died",
                           "exitcode": p.exitcode, "wall_s": round(time.time() - ts, 3)}
                elif time.time() - ts > a.timeout:
                    p.kill()
                    p.join(timeout=30)
                    rec = {"i": i, "seed": job[0], "asc": job[2], "outcome": "hard_timeout",
                           "wall_s": round(time.time() - ts, 3)}
                if rec is not None:
                    rec["batch_t_end"] = round(time.time() - t_batch, 3)
                    sink.write(json.dumps(rec) + "\n")
                    sink.flush()
                    del running[i]
                    done += 1
                    if rec["outcome"] not in ("won", "lost") or rec["wall_s"] > 120:
                        print(f"[replay] {done}/{len(jobs)} i={i} seed={rec['seed']} A{rec['asc']} "
                              f"{rec['outcome']} wall={rec['wall_s']}s err={rec.get('error')}",
                              file=sys.stderr, flush=True)
        sink.write(json.dumps({"meta_end": True, "batch_wall_s": round(time.time() - t_batch, 3)}) + "\n")
    print(f"[replay] done {done} in {time.time() - t_batch:.1f}s", file=sys.stderr, flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
