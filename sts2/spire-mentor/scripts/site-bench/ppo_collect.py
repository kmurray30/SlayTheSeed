"""PPO collection path on one worker: the trainer's play_batch (policy=net,
250/1000 sims) over fixed seeds through whichever sts2_rs is first on sys.path.
Prints one line per run with an md5 of the run record, so base and new can be
checked for identical play.

Run BOTH engines with the SAME Python sources (the October 2026 measurement
used a `git archive` of one commit's sts2-pycli/python, never a working
tree): only the extension may differ between the two arms.

  PYTHONPATH=<mod dir>:<sts2-pycli/python> python ppo_collect.py Ironclad <ckpt.pt> roll <out.txt> 6 1
"""
import hashlib
import json
import sys
import time

# Fields that legitimately differ between two engines making identical
# decisions: wall-clock, and (newer workers.py) the identity of the sts2_rs
# build the worker ran (`ppo.wheel_guard.wheel_identity()`: path, src_hash,
# pgo stamps). Hashing them would flag every base/new pair as different.
VOLATILE_KEYS = ("wall_s", "wheel")


def enc(o):
    return o.tolist() if hasattr(o, "tolist") else repr(o)


def main():
    import sts2_rs
    from ppo.workers import play_batch

    ch, ckpt, act1, out = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
    seeds = list(range(700, 700 + int(sys.argv[5])))
    ascs = [(0, 3, 6, 10)[i % 4] for i in range(len(seeds))]
    t0 = time.perf_counter()
    rows = play_batch(seeds, policy="net", ascensions=ascs, character=ch, procs=int(sys.argv[6]),
                      record_decisions=True, ckpt_path=ckpt, sims=250, boss_sims=1000, act1=act1)
    wall = time.perf_counter() - t0
    print("wheel", sts2_rs.__file__)
    print("wall_s", round(wall, 3))
    bad = [r.get("outcome") for r in rows if r.get("outcome") in ("error", "timeout")]
    if bad:
        print("RUN_ERRORS", bad)
    with open(out, "w") as f:
        for r in rows:
            for k in VOLATILE_KEYS:
                r.pop(k, None)
            h = hashlib.md5(json.dumps(r, sort_keys=True, default=enc).encode()).hexdigest()
            print(r.get("seed"), r.get("ascension"), r.get("act1"), r.get("outcome"), r.get("won"),
                  r.get("steps"), h, file=f)


if __name__ == "__main__":
    main()
