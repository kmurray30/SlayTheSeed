"""Play one iteration-0 game exactly like ppo.workers.play_one(policy='net')
and log the wall/CPU time of every env.reset()/env.step() (each one runs the
combats between two run-level decisions), with the floor/HP metrics.

  trace_game.py --ckpt CK --seed S --asc A --out OUT.jsonl
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time


def main() -> int:
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from replay import CLI, build_acting

    ap = argparse.ArgumentParser()
    ap.add_argument("--ckpt", required=True)
    ap.add_argument("--seed", type=int, required=True)
    ap.add_argument("--asc", type=int, required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    acting = os.path.splitext(a.out)[0] + ".acting.pt"
    build_acting(a.ckpt, acting)

    import numpy as np

    from ppo.env import Decision, PpoRunEnv
    from ppo.workers import _net_policy

    env = PpoRunEnv(seed=a.seed, ascension=a.asc, character=CLI["character"], expert=False,
                    sims=CLI["sims"], boss_sims=CLI["boss_sims"], act1=CLI["act1"])
    pol = _net_policy(acting)
    nrng = np.random.default_rng(a.seed)
    with open(a.out, "w") as f:
        def log(kind, dt, dc, out, chosen=None):
            m = getattr(out, "metrics", None) or {}
            rec = {"kind": kind, "wall_s": round(dt, 3), "cpu_s": round(dc, 3),
                   "type": type(out).__name__, "surface": getattr(out, "surface", None),
                   "chosen": chosen,
                   "metrics": {k: m.get(k) for k in ("act", "floor", "total_floor", "hp", "max_hp", "gold", "deck_size")
                               if k in m} or m,
                   "outcome": getattr(out, "outcome", None)}
            f.write(json.dumps(rec, default=str) + "\n")
            f.flush()

        t0, c0 = time.time(), time.process_time()
        out = env.reset()
        log("reset", time.time() - t0, time.process_time() - c0, out)
        while isinstance(out, Decision):
            idx, logp, value, feat = pol.sample(out.obs, out.actions, out.metrics, nrng, greedy=False)
            act = out.actions[idx]
            t0, c0 = time.time(), time.process_time()
            out = env.step(idx)
            log("step", time.time() - t0, time.process_time() - c0, out, chosen=act)
    return 0


if __name__ == "__main__":
    sys.exit(main())
