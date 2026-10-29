"""Summary of measure_wasm.sh: medians per budget and engine, new/base, wasm/native.

  python3 -I summarize_wasm.py <R = runs/wasm> <NATIVE = runs/ours/hint>

Per run, each harness prints the median of REPS timed searches per budget.
A cell here is the MEDIAN OF THOSE PER-RUN MEDIANS over the runs found
(<stage>/<engine>-<i>.txt), with [min-max] and n. new/base is given two ways:
ratio of the cell medians, and the median [min-max] of the per-pair ratios
new_i/base_i (pairs are ABBA-interleaved, so pair i ran back to back).
wasm/native = wasm cell median / native cell median, same engine.
Also re-checks that every file (wasm and native) sits on the same position with
the same pv0 at every budget. Writes summary.tsv next to the outputs.
"""
import glob
import os
import re
import statistics
import sys

R, NATIVE = sys.argv[1], sys.argv[2]
BUDGETS = [200, 800, 1000, 2000, 4000]
LINE = re.compile(r"^sims=(\d+) cold_ms=([\d.]+) median_ms=([\d.]+) min_ms=([\d.]+) ms_per_sim=[\d.]+ pv0=(.*)$")


def parse(path):
    out = {"position": None, "sims": {}, "pv0": {}, "host": ""}
    try:
        lines = open(path).read().splitlines()
    except OSError:
        return out
    for ln in lines:
        if ln.startswith("position "):
            out["position"] = ln
        elif ln.startswith("host=") or ln.startswith("browser="):
            out["host"] += ln.split(" seed=")[0][:120] + " "
        m = LINE.match(ln)
        if m:
            s = int(m.group(1))
            out["sims"][s] = float(m.group(3))
            out["pv0"][s] = m.group(5)
    return out


def runs(d):
    res = {}
    for eng in ("base", "new"):
        for p in sorted(glob.glob(os.path.join(d, f"{eng}-*.txt"))):
            m = re.search(r"-(\d+)\.txt$", p)
            if m:
                res[(eng, int(m.group(1)))] = parse(p)
    return res


sources = {"native": runs(NATIVE), "node": runs(os.path.join(R, "node")), "chrome": runs(os.path.join(R, "chrome"))}


def cell(src, eng, sims):
    v = [r["sims"][sims] for (e, _), r in sources[src].items() if e == eng and sims in r["sims"]]
    return (statistics.median(v), min(v), max(v), len(v)) if v else None


def paired(src, sims):
    rr = sources[src]
    ratios = [rr[("new", i)]["sims"][sims] / rr[("base", i)]["sims"][sims]
              for (e, i) in rr if e == "base" and ("new", i) in rr
              and sims in rr[("base", i)]["sims"] and sims in rr[("new", i)]["sims"]]
    return (statistics.median(ratios), min(ratios), max(ratios), len(ratios)) if ratios else None


def fmt(c):
    return f"{c[0]:8.2f} [{c[1]:.2f}-{c[2]:.2f}] n={c[3]}" if c else "   -"


def fmtr(c):
    return f"{c[0]:.3f} [{c[1]:.3f}-{c[2]:.3f}] n={c[3]}" if c else "-"


print(f"WASM hint search (BENCH1): median of per-run medians, ms  [R={R}  native={NATIVE}]")
flags = os.path.join(R, "FLAGS.env")
if os.path.exists(flags):
    print("FLAGS (measured on a busy host): " + " ".join(open(flags).read().split()))
for src in ("native", "node", "chrome"):
    n = {e: sum(1 for (x, _) in sources[src] if x == e) for e in ("base", "new")}
    hosts = sorted({r["host"].strip() for r in sources[src].values() if r["host"]})
    print(f"  {src:6s}: runs base={n['base']} new={n['new']}  {hosts[0] if hosts else ''}")
print()

tsv = [["budget", "source", "engine", "median_ms", "min_ms", "max_ms", "n", "new_over_base_cells",
        "new_over_base_paired_median", "wasm_over_native"]]
for sims in BUDGETS:
    print(f"sims={sims}")
    for src in ("native", "node", "chrome"):
        b, n = cell(src, "base", sims), cell(src, "new", sims)
        if not (b or n):
            print(f"  {src:6s}  (no runs)")
            continue
        ratio = f"{n[0] / b[0]:.3f}" if (b and n) else "-"
        pr = paired(src, sims)
        print(f"  {src:6s}  base {fmt(b)}   new {fmt(n)}   new/base {ratio} (paired {fmtr(pr)})")
        for eng, c in (("base", b), ("new", n)):
            nat = cell("native", eng, sims)
            wn = f"{c[0] / nat[0]:.3f}" if (c and nat and src != "native") else ""
            if c:
                tsv.append([sims, src, eng, f"{c[0]:.2f}", f"{c[1]:.2f}", f"{c[2]:.2f}", c[3], ratio,
                            f"{pr[0]:.3f}" if pr else "", wn])
    parts = []
    for src in ("node", "chrome"):
        for eng in ("base", "new"):
            c, nat = cell(src, eng, sims), cell("native", eng, sims)
            if c and nat:
                parts.append(f"{src}/{eng} {c[0] / nat[0]:.2f}x")
    print("  wasm/native: " + ("  ".join(parts) if parts else "(native hint runs not found)"))
print()

# Same position + pv0 at every budget, across every file of every source.
ref, bad = None, []
for src, rr in sources.items():
    for (eng, i), r in sorted(rr.items()):
        key = (r["position"], tuple(r["pv0"].get(s) for s in BUDGETS))
        if r["position"] is None or len(r["pv0"]) != len(BUDGETS):
            bad.append(f"{src}/{eng}-{i}:incomplete")
            continue
        if ref is None:
            ref = key
        elif key != ref:
            bad.append(f"{src}/{eng}-{i}:differs")
print("same position + pv0 at all budgets: " + ("yes, all files" if not bad and ref else f"NO: {' '.join(bad) or 'no files'}"))
if ref:
    print(f"  {ref[0]}  pv0@1000={ref[1][2]}")

with open(os.path.join(R, "summary.tsv"), "w") as f:
    f.write("\n".join("\t".join(str(x) for x in row) for row in tsv) + "\n")
