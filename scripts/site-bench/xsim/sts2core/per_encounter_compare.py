"""Per-encounter random-play win rate and fight length: spire-mentor M1 vs sts2core W1.

Counts only, no timing. spire-mentor's `site_random_bench` prints totals, so this
runs it once per seed (`--seeds 1 --seed-start s`, encounter `s % 22`, about 4 ms
each). Inside one such run the policy seeds are `0..reps-1` for every start state,
not the `i * 1_000_003 + r` of a full run, so its totals differ slightly from the
full run's (2026-10-14: 31.2 % / 21.67 actions per fight against 32.4 % / 21.99).

    python3 per_encounter_compare.py \
        --ours-bin <sts2-rs target>/release/examples/site_random_bench \
        --sts2core-out w1_instance.txt [--sts2core-out w1_engine.txt] [--seeds 2200 --reps 50]

`--sts2core-out` is the saved stdout of `xsim_w1_site` (its last line is the JSON).
"""

from __future__ import annotations

import argparse
import json
import math
import subprocess
from pathlib import Path


def ours_per_encounter(
    binary: str, seeds: int, reps: int, n_enc: int
) -> list[list[int]]:
    acc = [[0, 0, 0] for _ in range(n_enc)]
    for s in range(seeds):
        out = (
            subprocess.run(
                [binary, "--seeds", "1", "--seed-start", str(s), "--reps", str(reps)],
                check=True,
                capture_output=True,
                text=True,
            )
            .stdout.strip()
            .splitlines()[-1]
        )
        r = json.loads(out)
        k = s % n_enc
        acc[k][0] += r["fights"]
        acc[k][1] += r["wins"]
        acc[k][2] += r["decisions"]
    return acc


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--ours-bin", required=True)
    ap.add_argument("--sts2core-out", action="append", required=True)
    ap.add_argument("--seeds", type=int, default=2200)
    ap.add_argument("--reps", type=int, default=50)
    args = ap.parse_args()

    runs = []
    for p in args.sts2core_out:
        d = json.loads(Path(p).read_text().strip().splitlines()[-1])
        runs.append(d)
    ids = runs[0]["encounters"]
    ours = ours_per_encounter(args.ours_bin, args.seeds, args.reps, len(ids))

    tf = sum(v[0] for v in ours)
    print(
        f"ours: fights {tf} win {sum(v[1] for v in ours) / tf:.3f} "
        f"actions/fight {sum(v[2] for v in ours) / tf:.2f}"
    )
    for d in runs:
        print(
            f"sts2core[{d['policy']}]: fights {d['fights']} win {d['win_rate']:.3f} actions/fight {d['actions_per_fight']:.2f}"
        )
    head = f"{'encounter':28s} {'ours':>6s} {'apf':>5s}"
    for d in runs:
        head += f" | {d['policy'][:8]:>8s} {'apf':>5s} {'delta_pp':>8s} {'2se':>4s}"
    print(head)
    for k, enc in enumerate(ids):
        f, w, a = ours[k]
        pw = w / f
        line = f"{enc:28s} {pw:6.3f} {a / f:5.1f}"
        for d in runs:
            e = d["per_encounter"][k]
            assert e["id"] == enc
            qw = e["wins"] / e["fights"]
            se2 = 2 * math.sqrt(pw * (1 - pw) / f + qw * (1 - qw) / e["fights"]) * 100
            line += f" | {qw:8.3f} {e['actions'] / e['fights']:5.1f} {(qw - pw) * 100:+8.1f} {se2:4.1f}"
        print(line)


if __name__ == "__main__":
    main()
