"""Summaries for the training-speed benchmark (stdlib only; run with python3 -I).

  summarize.py <train_iter.jsonl>            # whole-machine iterations
  summarize.py --percore <dir> <nseeds>      # one-core ppo_collect runs

Prints a human table to stderr and ONE JSON line on stdout.
"""

import glob
import hashlib
import json
import os
import re
import statistics
import sys

CONFIGS = ("base-plain", "new-plain", "new-pgo")


def med(xs):
    xs = [x for x in xs if x is not None]
    return round(statistics.median(xs), 2) if xs else None


def train_iter(path):
    rows = [json.loads(x) for x in open(path) if x.strip()]
    out = {"file": path, "runs": len(rows), "configs": {}, "problems": []}
    fps = {}
    for r in rows:
        cfg = next((c for c in CONFIGS if r["label"].startswith(c)), r["label"])
        if not r.get("ok"):
            out["problems"].append(f"{r['label']}: ok=false rc={r.get('rc')}")
        if not r.get("so_verified"):
            out["problems"].append(f"{r['label']}: extension not verified {r.get('loaded_so')}")
        for i in r.get("iterations", []):
            key = (i["it"], i["games"])
            fp = i["fingerprint"] if i["it"] == 0 else i.get("fingerprint_decisions")
            fps.setdefault(key, {}).setdefault(fp, []).append(r["label"])
            c = out["configs"].setdefault(cfg, {}).setdefault(f"it{i['it']}", [])
            c.append(
                {
                    "label": r["label"],
                    "collect_s": i["collect_s"],
                    "train_s": i["train_s"],
                    "iter_s": i["iter_s"],
                    "games": i["games"],
                    "n_transitions": i["n_transitions"],
                    "wall_s": r.get("wall_s"),
                    "cpu_s": (r.get("cpu_user_s") or 0) + (r.get("cpu_sys_s") or 0),
                    "gpu_update_util_mean": (i.get("gpu_update_window") or {}).get("util_mean"),
                    "util_collect": i.get("util_collect"),
                    # trainer + resource tracker + the pool's workers
                    "workers": max(1, (r.get("max_procs_in_tree") or 3) - 2),
                }
            )
    for key, d in fps.items():
        if len(d) > 1:
            out["problems"].append(f"iteration {key[0]} at {key[1]} games: fingerprints differ {d}")
    summ = {}
    for cfg, its in out["configs"].items():
        for it, xs in its.items():
            games = xs[0]["games"]
            s = {
                "n": len(xs),
                "games": games,
                "n_transitions": xs[0]["n_transitions"],
                "collect_s": med([x["collect_s"] for x in xs]),
                "train_s": med([x["train_s"] for x in xs]),
                "iter_s": med([x["iter_s"] for x in xs]),
                "collect_s_all": [x["collect_s"] for x in xs],
                "gpu_update_util_mean": med([x["gpu_update_util_mean"] for x in xs]),
                "util_collect": med([x["util_collect"] for x in xs]),
            }
            if s["collect_s"]:
                s["games_per_s"] = round(games / s["collect_s"], 3)
                s["games_per_hour"] = round(games / s["collect_s"] * 3600, 1)
                s["workers"] = xs[0]["workers"]
                # whole-machine throughput spread over the 24 physical cores (the
                # per-core comparison point for `--percore`'s pinned single worker)
                s["games_per_s_per_physical_core"] = round(games / s["collect_s"] / 24, 4)
                s["s_per_game_per_physical_core"] = round(s["collect_s"] * 24 / games, 2)
            if it == "it0":
                s["cpu_s_per_game_whole_run"] = med([x["cpu_s"] / x["games"] for x in xs if x["games"]])
                s["campaign_150_iters_h"] = round(150 * s["iter_s"] / 3600, 1) if s["iter_s"] else None
            summ[f"{cfg}/{it}"] = s
    out["summary"] = summ
    sp = {}
    for it in ("it0", "it1"):
        b, n, p = (summ.get(f"{c}/{it}") for c in CONFIGS)
        for name, x, y in (("new_plain_vs_base", b, n), ("new_pgo_vs_base", b, p), ("pgo_vs_new_plain", n, p)):
            if x and y:
                for m in ("collect_s", "iter_s"):
                    if x.get(m) and y.get(m):
                        sp[f"{it}/{name}/{m}"] = round(x[m] / y[m], 3)
    out["speedups"] = sp
    del out["configs"]
    for k, s in summ.items():
        print(
            f"{k:18s} n={s['n']} games={s['games']} collect={s['collect_s']}s train={s['train_s']}s "
            f"iter={s['iter_s']}s  all collect={s['collect_s_all']}",
            file=sys.stderr,
        )
    for k, v in sp.items():
        print(f"  speedup {k} = {v}x", file=sys.stderr)
    for p in out["problems"]:
        print("PROBLEM:", p, file=sys.stderr)
    print(json.dumps(out))


def percore(d, n):
    arms = {}
    digests = {}
    problems = []
    for perf in sorted(glob.glob(os.path.join(d, f"*-n{n}-*.perf"))):
        m = re.match(r"(base|new|pgo)-n\d+-(\d+)\.perf$", os.path.basename(perf))
        if not m:
            continue
        arm, rep = m.group(1), m.group(2)
        tc = None
        for line in open(perf):
            f = line.strip().split(",")
            if len(f) > 2 and f[2] == "task-clock":
                tc = float(f[0]) / 1000.0
        txt = perf[: -len(".perf")] + ".txt"
        h = hashlib.md5(open(txt, "rb").read()).hexdigest() if os.path.exists(txt) else None
        arms.setdefault(arm, []).append(tc)
        digests.setdefault(h, []).append(f"{arm}-{rep}")
    if len(digests) != 1:
        problems.append(f"run records differ across arms/reps: {digests}")
    summ = {a: {"n": len(v), "task_clock_s": med(v), "all": v, "s_per_run": med([x / n for x in v if x])} for a, v in arms.items()}
    sp = {}
    if "base" in summ:
        for a in ("new", "pgo"):
            if a in summ and summ[a]["task_clock_s"]:
                sp[f"{a}_vs_base"] = round(summ["base"]["task_clock_s"] / summ[a]["task_clock_s"], 3)
    if "new" in summ and "pgo" in summ and summ["pgo"]["task_clock_s"]:
        sp["pgo_vs_new"] = round(summ["new"]["task_clock_s"] / summ["pgo"]["task_clock_s"], 3)
    for a, s in summ.items():
        print(f"percore {a}: {s}", file=sys.stderr)
    print(f"  speedups {sp}  identical_runs={len(digests) == 1}", file=sys.stderr)
    print(json.dumps({"percore": summ, "nseeds": n, "speedups": sp, "identical_runs": len(digests) == 1, "problems": problems}))


if __name__ == "__main__":
    if sys.argv[1] == "--percore":
        percore(sys.argv[2], int(sys.argv[3]))
    else:
        train_iter(sys.argv[1])
