#!/usr/bin/env python3
"""Every number for the 2026-10 benchmark page, computed from the raw bench2 data.

"bench2" is the BENCH_DIR the drivers in scripts/site-bench/bench/ wrote to.
Stdlib only. Re-run (from anywhere):
    python3 -I scripts/site-bench/bench/results/analyze.py <BENCH_DIR>
    (or with BENCH_DIR set in the environment and no argument)
Writes <BENCH_DIR>/results/results.json (one key per metric: value, unit, n, spread
[min, max], sources, definition) and <BENCH_DIR>/results/RESULTS.md (tables).
Besides the raw runs it reads <BENCH_DIR>/facts/hardware.md (a hand-written
"| Item | Value | Source |" table of the host, see the README) and the published
C# reference rows (../csharp/m2_reference.jsonl; CS_REF_JSONL overrides).
Some prose and checks are specific to the published 2026-10-14 run (its timeline,
its C6 cell, its known caveats); a re-run on other data may need them adjusted.

Conventions
- Rates are MEDIANS over runs (spread = [min, max] of the per-run values).
- Speed-ups: the ratio of medians, and where runs were paired (ABBA order or the
  same rotation round) also the median of the per-pair ratios.
- Source paths are relative to the bench2 directory.
"""

import glob
import hashlib
import json
import os
import re
import statistics
import sys
from datetime import datetime, timedelta, timezone

B = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("BENCH_DIR")
if not B:
    sys.exit("usage: analyze.py <BENCH_DIR>   (or set BENCH_DIR)")
B = os.path.abspath(B)
HERE = os.path.dirname(os.path.abspath(__file__))
R = os.path.join(B, "runs")
OUTD = os.path.join(B, "results")
SCRATCH = os.path.dirname(B)

METRICS = {}
CHECKS = []
FLAGS = []
MD = []


# ----------------------------------------------------------------------------- helpers
def rel(p):
    p = os.path.abspath(p)
    if p.startswith(B + os.sep):
        return os.path.relpath(p, B)
    bench_home = os.path.dirname(HERE)  # scripts/site-bench/bench in the checkout
    if p.startswith(bench_home + os.sep):
        return "scripts/site-bench/bench/" + os.path.relpath(p, bench_home)
    if p.startswith(SCRATCH + os.sep):
        return "<scratch>/" + os.path.relpath(p, SCRATCH)
    return p


def g6(x):
    if x is None:
        return None
    if isinstance(x, int):
        return x
    return float(f"{x:.6g}")


def med(xs):
    xs = [x for x in xs if x is not None]
    return statistics.median(xs) if xs else None


def mm(xs):
    xs = [x for x in xs if x is not None]
    return [g6(min(xs)), g6(max(xs))] if xs else None


def M(key, value, unit, definition, sources=(), n=None, spread=None, **extra):
    d = {
        "value": g6(value) if isinstance(value, float) else value,
        "unit": unit,
        "n": n,
        "spread": spread,
        "sources": list(dict.fromkeys(sources)),
        "definition": definition,
    }
    for k, v in extra.items():
        d[k] = v
    METRICS[key] = d
    return value


def MS(key, xs, unit, definition, sources=(), **extra):
    """Median-of-runs metric."""
    v = med(xs)
    return M(key, v, unit, definition, sources, n=len([x for x in xs if x is not None]), spread=mm(xs),
             runs=[g6(x) for x in xs], **extra)


def CHECK(name, ok, detail):
    CHECKS.append({"check": name, "pass": bool(ok), "detail": detail})
    if not ok:
        FLAGS.append(f"IDENTITY CHECK FAILED: {name}: {detail}")


def last_json(path):
    with open(path) as f:
        lines = [x.strip() for x in f if x.strip().startswith("{")]
    return json.loads(lines[-1])


def perf(path):
    out = {}
    for line in open(path):
        f = line.strip().split(",")
        if len(f) > 2 and f[0] and not line.startswith("#"):
            try:
                out[f[2]] = float(f[0])
            except ValueError:
                pass
    return out


def md5(path):
    return hashlib.md5(open(path, "rb").read()).hexdigest()


def fmt(x, d=None):
    """Human number: thousands separators; d decimals (auto if None)."""
    if x is None:
        return "-"
    if isinstance(x, str):
        return x
    ax = abs(x)
    if d is None:
        d = 0 if ax >= 1000 else (1 if ax >= 100 else (2 if ax >= 10 else (3 if ax >= 1 else 4)))
    return f"{x:,.{d}f}"


def fx(x, d=2):
    return "-" if x is None else f"{x:,.{d}f}x"


def pct(x, d=1):
    return "-" if x is None else f"{100 * x:.{d}f}%"


def h(title, level=2):
    MD.append("")
    MD.append("#" * level + " " + title)
    MD.append("")


def table(head, rows):
    MD.append("| " + " | ".join(head) + " |")
    MD.append("|" + "|".join("---" for _ in head) + "|")
    for r in rows:
        MD.append("| " + " | ".join(str(c) for c in r) + " |")
    MD.append("")


def p(text=""):
    MD.append(text)


def V(key):
    return METRICS[key]["value"]


# ----------------------------------------------------------------------------- expected identities
# Reference identities after the GH #762 harness fix (harnessfix_result.json), the critic's pre-registered
# external digests (prep_result.json critique) and the coordinator's run plan.
EXP = {
    "ours_w1_110k": {"fights": 110000, "wins": 35646, "decisions": 2417920, "digest": "c78f3428460d718b"},
    "ours_w1_440k": {"fights": 440000, "wins": 143036, "decisions": 9667495, "digest": "fd781f2d768f425d"},
    "ours_w2": {"playouts": 240000, "wins": 212766, "transitions": 4479594, "digest": "792d3d36f6eab175"},
    "sansb_w1_inst": "dda07e263c74afe9",
    "core_w1_inst": "2575e7b84eb83af7",
    "sansb_w2_unif": ("5d4f992689b53874", 137468, 4078800),
    "sansb_w2_inst": ("476f9e9905c3e287", 211793, 4623932),
    "core_w2_inst": ("08d6095eb398a250", 214314, 4434416),
    "core_w2_eng": ("3f16caacf0147aa9", 146562, 4117950),
    "adv_1t": "a2833dcde062",
    "adv_8t": "7c30afa17608",
    "hint_position": "enemies=Fuzzy Wurm Crawler:57;hand=DEFEND_IRONCLAD,STRIKE_IRONCLAD,STRIKE_IRONCLAD,DEFEND_IRONCLAD,BASH",
    "cs_setup_fp": "e0c4b44c5f86",
}
PHYS = {"1core": 1, "1core-smt": 1, "ccx": 6, "ccx-smt": 6, "phys12": 12, "phys24": 24, "smt48": 24}
THREADS = {"1core": 1, "1core-smt": 2, "ccx": 6, "ccx-smt": 12, "phys12": 12, "phys24": 24, "smt48": 48}
PRESET_CPUS = {
    "1core": "20 (sibling 44 idle)", "1core-smt": "20+44", "ccx": "18-23 (one L3, 1 thread/core)",
    "ccx-smt": "18-23,42-47 (one L3, both threads)", "phys12": "0-2,6-8,12-14,18-20 (3 cores per L3)",
    "phys24": "0-23 (every core, 1 thread)", "smt48": "0-47 (every hardware thread)",
}

# ============================================================================= 1. hardware / software
def md_table_rows(path):
    rows = {}
    for line in open(path):
        if line.startswith("| ") and not line.startswith("|---"):
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if len(cells) >= 2 and cells[0] not in ("Item",):
                rows[cells[0]] = cells[1]
    return rows


hwmd = os.path.join(B, "facts/hardware.md")
HW = md_table_rows(hwmd)
SNAP = last_json(os.path.join(B, "facts/hw_snapshot_measure.out"))
SNAP_END = last_json(os.path.join(B, "facts/hw_snapshot_end.out"))
PROV = {}
for line in open(os.path.join(R, "ours/PROVENANCE.env")):
    if "=" in line and not line.startswith("MD5") and not line.startswith("HARNESS"):
        k, v = line.rstrip("\n").split("=", 1)
        PROV[k] = v.strip("'")

hws = [rel(hwmd), "facts/hw_snapshot_measure.out", "facts/hw_snapshot_end.out", "runs/ours/PROVENANCE.env"]
M("hw.system", HW["System"], "text", "Server model and board (DMI).", hws)
M("hw.cpu", f"{SNAP['cpu']} (Zen 3 'Milan')", "text", "CPU model (lscpu).", hws)
M("hw.sockets_cores_threads", "1 socket, 24 cores, 48 threads (SMT on), 1 NUMA node", "text", "lscpu.", hws)
M("hw.clock_boost_max_ghz", int(SNAP["cpuinfo_max_khz"]) / 1e6, "GHz", "cpuinfo_max_freq (boost ceiling).", hws)
M("hw.clock_top_pstate_ghz", int(SNAP["scaling_max_khz"]) / 1e6, "GHz",
  "Top non-boost P-state (scaling_max_freq); boost on, governor schedutil.", hws)
M("hw.governor", f"{SNAP['governor']}, boost={SNAP['boost']}", "text", "cpufreq governor and boost flag.", hws)
M("hw.caches", f"L1d/L1i/L2 per core: {HW['L1d / L1i / L2']}; L3: {HW['L3']}", "text", "Cache sizes.", hws)
M("hw.l3_domains", SNAP["l3_domains"], "cpu lists", "L3 domains (CPU ids; SMT sibling of n is n+24).", hws)
M("hw.ram", HW["RAM"], "text", "Installed memory (udev DMI + EDAC).", hws)
M("hw.mem_total_gib", SNAP["mem_total_kb"] / 1048576, "GiB", "MemTotal visible to the OS.", hws)
M("hw.gpus", SNAP["gpus"], "list", "nvidia-smi; training uses ONE of them (GPU index 1).", hws)
M("hw.os", HW["OS / kernel"], "text", "OS and kernel.", hws)
M("hw.thp", SNAP["thp"], "text", "Transparent huge pages.", hws)
M("sw.rust", HW["Rust"], "text", "Rust toolchain used for our engine and sts2core (sansb pins rustc 1.97.1).", hws)
M("sw.release_profile_ours", PROV.get("PROFILE_NATIVE"), "text", "Our native harness build profile.", hws)
M("sw.dotnet", HW[".NET"], "text", ".NET SDK/runtime for the shipped game run headless.", hws)
M("sw.game_dll_md5", "282bfb4ccec5b17c51dea6b37ccb4791", "md5", "sts2.dll (game build v0.107.1) used by the C# harness.",
  hws + ["csharp/runs/results.jsonl"])
M("sw.python", HW["Python (worktree venv)"], "text", "Python.", hws)
M("sw.pytorch", HW["PyTorch"], "text", "PyTorch for the training benchmark.", hws)
M("sw.node", HW["Node"], "text", "Node (WASM benchmark).", hws)
M("sw.chromium", HW["Playwright / Chromium"], "text", "Headless Chromium (WASM benchmark).", hws)
M("sw.wasm_tools", HW["wasm-bindgen / wasm-opt"], "text", "WASM build tools.", hws)
M("sw.perf", HW["perf"], "text", "perf (user-mode cycles/instructions/task-clock).", hws)
M("host.shared", HW.get("Sharing"), "text", "The host is shared; every stage waited for a quiet host first.", hws)

# --- conditions during the window
cond_re = re.compile(r"^\[(\S+)\] conditions\[(.+?)\]: load=(\S+) (\S+) (\S+) mem_avail_gb=(\d+)")
conds = []
for line in open(os.path.join(R, "all.log")):
    m = cond_re.match(line)
    if m:
        conds.append({"t": m[1], "where": m[2], "load1": float(m[3]), "load5": float(m[4]), "load15": float(m[5]),
                      "mem_avail_gb": int(m[6])})
gate_rows = []
q1 = re.compile(r"^\[(\S+)\] quiet\[(.+?)\] load1=(\S+) busy=(\d+)% cpus=(.*)$")
for f in ("ours/run.log", "xsim/run.log", "wasm/run.log"):
    for line in open(os.path.join(R, f)):
        m = q1.match(line.strip())
        if m:
            cm = re.search(r"max=(\d+)", m[5])
            gate_rows.append({"src": f, "t": m[1], "stage": m[2], "load1": float(m[3]), "busy": int(m[4]),
                              "cpu_max": int(cm[1]) if cm else None})
q2 = re.compile(r"^\[(\S+)\] quiet: load=(\S+) busy=(\d+)% cpus\((.+?)\)max=(\d+)%")
cs_log = os.path.join(B, "csharp/runs/measure.log")
for line in open(cs_log):
    m = q2.match(line.strip())
    if m:
        gate_rows.append({"src": "csharp/runs/measure.log", "t": m[1], "stage": "cs:" + m[4], "load1": float(m[2]),
                          "busy": int(m[3]), "cpu_max": int(m[5])})
# training gate lines are local time (+02:00, see train_iter.jsonl started_at); keep those inside the window
train_block = [x for x in open(os.path.join(R, "all.log")) if "== block training" in x and "rc=" not in x][0]
tb_utc = datetime.strptime(train_block[1:21], "%Y-%m-%dT%H:%M:%SZ")
tb_local = (tb_utc + timedelta(hours=2)).strftime("%H:%M:%S")
q3 = re.compile(r"^\[measure (\S+)\] quiet: busy=(\d+)% load=(\S+) ")
tr_log = os.path.join(B, "training/results/measure.log")
forced_train = []
for line in open(tr_log):
    m = q3.match(line.strip())
    if m and m[1] >= tb_local:
        gate_rows.append({"src": "training/results/measure.log", "t": m[1] + "+02:00", "stage": "training",
                          "load1": float(m[3]), "busy": int(m[2]), "cpu_max": None})
    if "NOT QUIET" in line:
        forced_train.append(line.strip())
lg_rows = [g for g in gate_rows if g["stage"] != "training"]
tg_rows = [g for g in gate_rows if g["stage"] == "training"]
gl = [g["load1"] for g in lg_rows]
gb = [g["busy"] for g in lg_rows]
M("host.window_utc", "2026-10-14 10:59:56 to 16:32:40 UTC", "text",
  "Measurement window: first block start in runs/all.log to the end of C# cell C6 (csharp/runs/measure.log).",
  ["runs/all.log", "csharp/runs/measure.log"])
M("host.quiet_gates_passed", len(gate_rows), "count",
  "Quiet-host gate passes logged before timed stages (ours, xsim, wasm, C#, training).",
  ["runs/ours/run.log", "runs/xsim/run.log", "runs/wasm/run.log", "csharp/runs/measure.log", "training/results/measure.log"],
  n=len(gate_rows))
M("host.gate_load1", med(gl), "load average (1 min)",
  "1-min load average at each load-gated pass (Rust, WASM and C# stages; gate: load1 < 1.5 and whole host <= 3% busy).",
  ["runs/*/run.log", "csharp/runs/measure.log"], n=len(gl), spread=mm(gl))
M("host.gate_busy_pct", med(gb), "% of all 48 CPUs busy", "Whole-host busy % over 2-5 s at each load-gated pass (gate: <= 3%).",
  ["runs/*/run.log", "csharp/runs/measure.log"], n=len(gb), spread=mm(gb))
M("host.training_gate_busy_pct", med([g["busy"] for g in tg_rows]), "% of all 48 CPUs busy",
  "Training stages gate on whole-host busy <= 3% only (no load-average condition: the 1-min load still carries the previous "
  "training run's 46 workers).", ["training/results/measure.log"], n=len(tg_rows), spread=mm([g["busy"] for g in tg_rows]),
  load1_spread=mm([g["load1"] for g in tg_rows]))
cl = [c["load1"] for c in conds]
M("host.block_boundary_load1", med(cl), "load average (1 min)",
  "1-min load average at every block boundary in runs/all.log (includes the load our own whole-machine runs left behind).",
  ["runs/all.log"], n=len(cl), spread=mm(cl))
M("host.mem_avail_gb", med([c["mem_avail_gb"] for c in conds]), "GB", "MemAvailable at block boundaries.", ["runs/all.log"],
  n=len(conds), spread=mm([c["mem_avail_gb"] for c in conds]))

# ============================================================================= 2. our engine, one core, W1
W1D = os.path.join(R, "ours/w1")
w1 = {T: {} for T in ("base", "new")}
for T in w1:
    for i in range(1, 8):
        j = last_json(f"{W1D}/{T}-{i}.json")
        pf = perf(f"{W1D}/{T}-{i}.perf")
        w1[T][i] = (j, pf)
w1src = ["runs/ours/w1/{base,new}-{1..7}.json", "runs/ours/w1/{base,new}-{1..7}.perf", "runs/ours/index.tsv"]
for T in w1:
    for i, (j, _) in w1[T].items():
        e = EXP["ours_w1_110k"]
        ok = all(j[k] == e[k] for k in e) and j["anomalies"] == 0 and j.get("spawn") == "run"
        if not ok:
            CHECK(f"ours W1 {T}-{i} identity", False, json.dumps({k: j.get(k) for k in e}))
CHECK("ours W1 1core identity, base == new (14 runs)",
      all(all(j[k] == v for k, v in EXP["ours_w1_110k"].items()) for T in w1 for j, _ in w1[T].values()),
      "every run: 110,000 fights, 35,646 wins, 2,417,920 actions, digest c78f3428460d718b, spawn=run, 0 anomalies")

w1_def = ("W1 random play, one single-threaded process pinned to CPU 20 (SMT sibling 44 idle) = 'one core'. "
          "110,000 fights (2,200 seeds x 50), Ironclad A0 starter deck 80/80 HP + Burning Blood vs the 22 Act-1 "
          "Overgrowth encounters round-robin; timed = clone start state + uniform random legal moves to the end.")
for T, lab in (("base", "previous engine d3ea66a51"), ("new", "new engine 6d1918237")):
    fr = [w1[T][i][0]["fights_per_s"] for i in range(1, 8)]
    ar = [w1[T][i][0]["decisions_per_s"] for i in range(1, 8)]
    MS(f"ours.w1.1core.{T}.fights_per_s", fr, "fights/s", f"{w1_def} {lab}; 7 runs (ABBA with the other engine).", w1src)
    MS(f"ours.w1.1core.{T}.actions_per_s", ar, "actions/s", f"{w1_def} Actions = every apply() call (card plays + end turns). {lab}; 7 runs.", w1src)
    MS(f"ours.w1.1core.{T}.us_per_action", [1e6 / x for x in ar], "µs/action", f"{w1_def} 1e6 / actions_per_s, per run. {lab}.", w1src)
    MS(f"ours.w1.1core.{T}.ns_per_action", [1e9 / x for x in ar], "ns/action", f"{w1_def} 1e9 / actions_per_s, per run. {lab}.", w1src)
    ins = [w1[T][i][1]["instructions:u"] / w1[T][i][0]["decisions"] for i in range(1, 8)]
    cyc = [w1[T][i][1]["cycles:u"] / w1[T][i][0]["decisions"] for i in range(1, 8)]
    ipc = [w1[T][i][1]["instructions:u"] / w1[T][i][1]["cycles:u"] for i in range(1, 8)]
    ghz = [w1[T][i][1]["cycles:u"] / (w1[T][i][1]["task-clock"] * 1e6) for i in range(1, 8)]
    tcpa = [w1[T][i][1]["task-clock"] * 1e6 / w1[T][i][0]["decisions"] for i in range(1, 8)]
    pdef = ("perf stat over the WHOLE process (includes the untimed start-state build, a few % of the run); "
            f"divided by the run's actions. {lab}.")
    MS(f"ours.w1.1core.{T}.perf.instructions_per_action", ins, "instructions/action", pdef, w1src)
    MS(f"ours.w1.1core.{T}.perf.cycles_per_action", cyc, "cycles/action", pdef, w1src)
    MS(f"ours.w1.1core.{T}.perf.ipc", ipc, "instructions/cycle", f"instructions:u / cycles:u, whole process. {lab}.", w1src)
    MS(f"ours.w1.1core.{T}.perf.ghz", ghz, "GHz", f"Effective user clock = cycles:u / task-clock. {lab}.", w1src)
    MS(f"ours.w1.1core.{T}.perf.cpu_ns_per_action", tcpa, "ns CPU/action",
       f"task-clock / actions, whole process incl. untimed setup. {lab}.", w1src)
pairs = [w1["new"][i][0]["decisions_per_s"] / w1["base"][i][0]["decisions_per_s"] for i in range(1, 8)]
M("ours.w1.1core.speedup_new_vs_base.ratio_of_medians",
  V("ours.w1.1core.new.actions_per_s") / V("ours.w1.1core.base.actions_per_s"), "x",
  "Median new actions/s / median previous actions/s (W1, one core). Same work (identical digests).", w1src, n=7)
MS("ours.w1.1core.speedup_new_vs_base.median_pair_ratio", pairs, "x",
   "Median over the 7 ABBA pairs of new/previous actions/s.", w1src)
M("ours.w1.1core.instructions_ratio_base_over_new",
  V("ours.w1.1core.base.perf.instructions_per_action") / V("ours.w1.1core.new.perf.instructions_per_action"), "x",
  "Previous / new user instructions per action: how much of the speed-up is less work rather than higher IPC.", w1src)
j0 = w1["new"][1][0]
M("ours.w1.workload.fights", j0["fights"], "fights", "Fights per W1 run (2,200 seeds x 50 reps).", w1src)
M("ours.w1.workload.actions", j0["decisions"], "actions", "Actions per W1 run.", w1src)
M("ours.w1.workload.win_rate", j0["wins"] / j0["fights"], "fraction", "Random-play win rate in W1 (35,646 / 110,000).", w1src,
  n=j0["fights"])
M("ours.w1.workload.actions_per_fight", j0["decisions"] / j0["fights"], "actions/fight", "Mean actions per W1 fight.", w1src,
  n=j0["fights"])
M("ours.w1.workload.digest", j0["digest"], "digest", "W1 work digest (identical on previous and new engine).", w1src)

# --- relics none vs Burning Blood (xsim w1 stage, same rotation rounds)
XW1 = os.path.join(R, "xsim/w1")


def xw1(tag, i):
    j = last_json(f"{XW1}/{tag}-{i}.out")
    pf = perf(f"{XW1}/{tag}-{i}.perf")
    if "decisions_per_s" in j:
        a, ar, wins = j["decisions"], j["decisions_per_s"], j["wins"]
    else:
        a, ar, wins = j["actions"], j["actions_per_s"], j["wins"]
    return {"fights": j["fights"], "actions": a, "actions_per_s": ar, "fights_per_s": j["fights_per_s"], "wins": wins,
            "digest": j["digest"], "perf": pf, "raw": j}


XTAGS = ["ours", "ours-norelic", "sansb-inst", "sansb-unif", "core-inst", "core-eng"]
X1 = {t: {i: xw1(t, i) for i in range(1, 8)} for t in XTAGS}
xsrc = ["runs/xsim/w1/<tag>-{1..7}.out", "runs/xsim/w1/<tag>-{1..7}.perf", "runs/xsim/index.tsv"]
bb = [X1["ours"][i]["actions_per_s"] for i in range(1, 8)]
nr = [X1["ours-norelic"][i]["actions_per_s"] for i in range(1, 8)]
CHECK("ours W1 --relics none == Burning Blood work (14 runs)",
      all(X1[t][i]["digest"] == EXP["ours_w1_110k"]["digest"] and X1[t][i]["wins"] == 35646 for t in ("ours", "ours-norelic")
          for i in range(1, 8)),
      "Burning Blood does not change random play (heal only after a won fight): same digest c78f3428460d718b")
MS("ours.w1.1core.new.burning_blood.actions_per_s_xsim", bb, "actions/s",
   "Our new engine, W1 with Burning Blood, measured inside the cross-engine rotation (7 rounds, CPU 20).",
   [s.replace("<tag>", "ours") for s in xsrc])
MS("ours.w1.1core.new.relics_none.actions_per_s", nr, "actions/s",
   "Our new engine, W1 with --relics none (no relic dispatch), cross-engine rotation (7 rounds, CPU 20). "
   "The like-for-like row against sansb, whose Burning Blood is inert.", [s.replace("<tag>", "ours-norelic") for s in xsrc])
MS("ours.w1.1core.new.relics_none.fights_per_s", [X1["ours-norelic"][i]["fights_per_s"] for i in range(1, 8)], "fights/s",
   "Our new engine, W1 with --relics none, fights/s; cross-engine rotation (7 rounds, CPU 20).", [s.replace("<tag>", "ours-norelic") for s in xsrc])
MS("ours.w1.relics_none_over_burning_blood.median_pair_ratio", [nr[i] / bb[i] for i in range(7)], "x",
   "Per rotation round: (--relics none actions/s) / (Burning Blood actions/s), same work (identical digest). <1 means the run "
   "WITHOUT the relic is slower: the one live relic costs nothing measurable here; the no-relic build path executes more "
   "instructions per action at lower IPC (cause not investigated).", [s.replace("<tag>", "ours{,-norelic}") for s in xsrc])
M("ours.w1.relics_none_over_burning_blood.instructions_ratio",
  med([X1["ours-norelic"][i]["perf"]["instructions:u"] / X1["ours-norelic"][i]["actions"] for i in range(1, 8)]) /
  med([X1["ours"][i]["perf"]["instructions:u"] / X1["ours"][i]["actions"] for i in range(1, 8)]), "x",
  "Median user instructions per action, --relics none / Burning Blood (whole process).", [s.replace("<tag>", "ours{,-norelic}") for s in xsrc])

# ============================================================================= 3. scaling (our engine + externals)
SCALE_TAGS = {
    "w1-new": ("ours/scale", "decisions", "fights"),
    "w1-base": ("ours/scale", "decisions", "fights"),
    "w2-new": ("ours/scale", "transitions", "playouts"),
    "w1-sansb": ("xsim/scale", "actions", "fights"),
    "w1-sts2core": ("xsim/scale", "actions", "fights"),
}
SC = {}
for tag, (sub, wk, wk2) in SCALE_TAGS.items():
    SC[tag] = {}
    for f in sorted(glob.glob(os.path.join(R, sub, tag, "r*", "*", "summary.json"))):
        rr = int(f.split(os.sep)[-3][1:])
        preset = f.split(os.sep)[-2]
        SC[tag].setdefault(preset, {})[rr] = json.load(open(f))
SC["hint-new"] = {}
for f in sorted(glob.glob(os.path.join(R, "ours/scale/hint-new/r*/*/summary.json"))):
    rr = int(f.split(os.sep)[-3][1:])
    SC["hint-new"].setdefault(f.split(os.sep)[-2], {})[rr] = json.load(open(f))

scale_flags = []


def scale_metrics(tag, pref, label, wk, wk2, unit_wk, unit_wk2):
    sub = SCALE_TAGS[tag][0]
    out = {}
    order = [p for p in PHYS if p in SC[tag]]
    for preset in order:
        reps = SC[tag][preset]
        rs = sorted(reps)
        src = [f"runs/{sub}/{tag}/r{{{','.join(map(str, rs))}}}/{preset}/summary.json"]
        agg = [reps[r]["agg_rate_sum"][wk] for r in rs]
        agg2 = [reps[r]["agg_rate_sum"][wk2] for r in rs]
        aggw = [reps[r]["agg_rate_wall"][wk] for r in rs]
        ppm = [reps[r]["per_proc_rate"][wk]["mean"] for r in rs]
        eff = [(reps[r]["scaling"] or {}).get(wk, {}).get("efficiency", 1.0) if reps[r].get("scaling") else 1.0 for r in rs]
        ghz = [reps[r]["perf"]["ghz_user_median"] for r in rs]
        ipc = [reps[r]["perf"]["ipc_median"] for r in rs]
        ov = [reps[r]["timed_overlap_frac"] for r in rs]
        spread = [reps[r]["per_proc_rate"][wk]["spread_pct"] for r in rs]
        bd = [((reps[r].get("host") or {}).get("busy_pct_during") or {}) for r in rs]
        unsel_mean = [x.get("unselected_mean") for x in bd]
        unsel_max = [x.get("unselected_max") for x in bd]
        same = all(reps[r]["identity"]["same"] is True for r in rs)
        ok_all = all(reps[r]["ok"] for r in rs)
        n_proc = reps[rs[0]]["n"]
        d = (f"{label}: preset {preset} = {n_proc} pinned single-threaded processes on CPUs {PRESET_CPUS[preset]}, "
             f"released together (scale_runner.py); {len(rs)} repeats, each with its own fresh 1-process baseline on CPU 20.")
        k = f"{pref}.{preset}"
        MS(f"{k}.agg_{unit_wk.split('/')[0]}_per_s", agg, unit_wk,
           d + " Aggregate = sum of per-process rates (valid because timed windows overlap, see timed_overlap).", src)
        MS(f"{k}.agg_{unit_wk2.split('/')[0]}_per_s", agg2, unit_wk2, d + " Aggregate, secondary unit.", src)
        MS(f"{k}.agg_wall_{unit_wk.split('/')[0]}_per_s", aggw, unit_wk,
           d + " Total work / (last end - common start): also charges process start, untimed setup and stragglers.", src)
        MS(f"{k}.per_process_{unit_wk.split('/')[0]}_per_s", ppm, unit_wk, d + " Mean per-process rate.", src)
        M(f"{k}.per_physical_core_{unit_wk.split('/')[0]}_per_s", med(agg) / PHYS[preset], unit_wk,
          d + f" Median aggregate / {PHYS[preset]} physical cores used.", src, n=len(rs))
        MS(f"{k}.efficiency_vs_1core", eff, "fraction",
           d + " Aggregate / (N x the same repeat's 1-process rate on CPU 20); per hardware thread for SMT presets.", src)
        MS(f"{k}.ghz", ghz, "GHz", d + " Median over processes of cycles:u / task-clock.", src)
        MS(f"{k}.ipc", ipc, "instructions/cycle", d + " Median over processes.", src)
        MS(f"{k}.timed_overlap", ov, "fraction", d + " Fraction of the union of timed windows covered by all of them.", src)
        MS(f"{k}.per_process_spread_pct", spread, "%", d + " (max-min)/mean of per-process rates.", src)
        if any(x is not None for x in unsel_mean):
            MS(f"{k}.foreign_busy_unselected_mean_pct", unsel_mean, "%",
               d + " Mean busy % of the CPUs NOT used by the run, sampled during it (foreign load).", src)
            MS(f"{k}.foreign_busy_unselected_max_pct", unsel_max, "%", d + " Max busy % of any unused CPU during the run.", src)
        if max(spread) > 10:
            scale_flags.append(f"{tag} {preset}: per-process rate spread up to {max(spread):.1f}% "
                               f"(repeats {[round(x, 1) for x in spread]}); foreign busy on unused CPUs max "
                               f"{[x for x in unsel_max]}")
        out[preset] = {"agg": med(agg), "agg2": med(agg2), "aggw": med(aggw), "eff": med(eff), "ghz": med(ghz),
                       "ipc": med(ipc), "ov": min(ov), "n": n_proc, "reps": rs, "agg_list": agg, "same": same,
                       "ok": ok_all, "ppm": med(ppm), "spread": max(spread)}
        CHECK(f"scale {tag} {preset}: all processes agree (identity.same) and ok", same and ok_all,
              json.dumps(reps[rs[0]]["identity"].get("values")))
    return out


S_NEW = scale_metrics("w1-new", "ours.scale.w1.new", "Our new engine, W1 (440,000 fights per process)",
                      "decisions", "fights", "actions/s", "fights/s")
S_BASE = scale_metrics("w1-base", "ours.scale.w1.base", "Our previous engine, W1 (440,000 fights per process)",
                       "decisions", "fights", "actions/s", "fights/s")
S_W2 = scale_metrics("w2-new", "ours.scale.w2.new", "Our new engine, W2 clone mode at A10 (240,000 playouts per process)",
                     "transitions", "playouts", "actions/s", "playouts/s")
S_SB = scale_metrics("w1-sansb", "xsim.scale.w1.sansb", "sansb/sts2-sim, W1 instance policy (110,000x2 = 220,000 fights per process)",
                     "actions", "fights", "actions/s", "fights/s")
S_CO = scale_metrics("w1-sts2core", "xsim.scale.w1.sts2core", "sts2core, W1 instance policy (880,000 fights per process)",
                     "actions", "fights", "actions/s", "fights/s")
# identity values against references
for tag, exp in (("w1-new", EXP["ours_w1_440k"]), ("w1-base", EXP["ours_w1_440k"])):
    vals = {json.dumps(SC[tag][p][r]["identity"]["values"], sort_keys=True) for p in SC[tag] for r in SC[tag][p]}
    v = json.loads(next(iter(vals)))
    CHECK(f"scale {tag}: one identity across all presets/repeats, equal to the 2200x200 reference",
          len(vals) == 1 and all(v[k] == exp[k] for k in exp), f"{len(vals)} distinct; {v}")
vals = {json.dumps(SC['w2-new'][p][r]["identity"]["values"], sort_keys=True) for p in SC["w2-new"] for r in SC["w2-new"][p]}
v = json.loads(next(iter(vals)))
CHECK("scale w2-new: one identity, equal to the W2 A10 reference", len(vals) == 1 and v["clone.digest"] == EXP["ours_w2"]["digest"]
      and v["clone.wins"] == EXP["ours_w2"]["wins"] and v["transitions"] == EXP["ours_w2"]["transitions"], str(v))
for tag in ("w1-sansb", "w1-sts2core"):
    vals = {json.dumps(SC[tag][p][r]["identity"]["values"], sort_keys=True) for p in SC[tag] for r in SC[tag][p]}
    CHECK(f"scale {tag}: one identity across all presets/repeats (within-engine)", len(vals) == 1, str(vals))

# best whole-machine preset per engine
def best_whole(S):
    c = {p: S[p]["agg"] for p in ("phys24", "smt48") if p in S}
    bp = max(c, key=c.get)
    return bp, c[bp]


for name, S, lab in (("ours.scale.w1.new", S_NEW, "our new engine"), ("ours.scale.w1.base", S_BASE, "our previous engine"),
                     ("ours.scale.w2.new", S_W2, "our new engine, W2"), ("xsim.scale.w1.sansb", S_SB, "sansb"),
                     ("xsim.scale.w1.sts2core", S_CO, "sts2core")):
    bp, bv = best_whole(S)
    M(f"{name}.whole_machine_best.preset", bp, "preset", f"Best of phys24/smt48 by median aggregate actions/s ({lab}).",
      ["runs/*/scale/.../{phys24,smt48}/summary.json"])
    M(f"{name}.whole_machine_best.actions_per_s", bv, "actions/s",
      f"Whole machine (24 cores / 48 threads): best of phys24 and smt48, median aggregate ({lab}).",
      [f"runs/*/scale/.../{bp}/summary.json"], n=len(S[bp]["reps"]), spread=mm(S[bp]["agg_list"]))
    M(f"{name}.whole_machine_best.secondary_per_s", S[bp]["agg2"], "fights/s" if "w2" not in name else "playouts/s",
      f"Whole machine, best preset {bp}, aggregate in the secondary unit (fights/s or playouts/s), median of repeats ({lab}).", [f"runs/*/scale/.../{bp}/summary.json"], n=len(S[bp]["reps"]))
    M(f"{name}.whole_machine_best.per_physical_core_actions_per_s", bv / 24, "actions/s per core",
      f"Best whole-machine aggregate / 24 physical cores ({lab}).", [f"runs/*/scale/.../{bp}/summary.json"])
    M(f"{name}.whole_machine_best.vs_1core", bv / S["1core"]["agg"], "x",
      f"Best whole-machine median aggregate / median 1core rate in the same scale runs ({lab}).",
      [f"runs/*/scale/.../{{1core,{bp}}}/summary.json"])


def smt_gain(S, tag, num, den):
    rs = sorted(set(SC[tag][num]) & set(SC[tag][den]))
    wk = SCALE_TAGS[tag][1]
    return [SC[tag][num][r]["agg_rate_sum"][wk] / SC[tag][den][r]["agg_rate_sum"][wk] for r in rs]


for tag, pref in (("w1-new", "ours.scale.w1.new"), ("w1-base", "ours.scale.w1.base"), ("w1-sansb", "xsim.scale.w1.sansb"),
                  ("w1-sts2core", "xsim.scale.w1.sts2core"), ("w2-new", "ours.scale.w2.new")):
    for num, den in (("1core-smt", "1core"), ("ccx-smt", "ccx"), ("smt48", "phys24")):
        if num in SC[tag] and den in SC[tag]:
            MS(f"{pref}.smt_gain.{num}_over_{den}", smt_gain(None, tag, num, den), "x",
               f"SMT gain: aggregate with both hardware threads per core ({num}) / one thread per core ({den}), "
               "per repeat (same repeat, run minutes apart), median.", [f"runs/*/scale/{tag}/r*/{{{num},{den}}}/summary.json"])

# new vs previous at scale (not interleaved: w1-new ran 11:43-11:57Z, w1-base 12:27-12:41Z)
for preset in ("1core", "1core-smt", "phys24", "smt48"):
    rn, rb = S_NEW[preset], S_BASE[preset]
    M(f"ours.scale.w1.speedup_new_vs_base.{preset}.ratio_of_medians", rn["agg"] / rb["agg"], "x",
      f"New / previous median aggregate actions/s at preset {preset} (3 repeats each; the two engines ran ~45 min apart, "
      "not interleaved).", [f"runs/ours/scale/w1-{{new,base}}/r*/{preset}/summary.json"], n=3)
    rs = sorted(set(SC["w1-new"][preset]) & set(SC["w1-base"][preset]))
    MS(f"ours.scale.w1.speedup_new_vs_base.{preset}.median_repeat_ratio",
       [SC["w1-new"][preset][r]["agg_rate_sum"]["decisions"] / SC["w1-base"][preset][r]["agg_rate_sum"]["decisions"] for r in rs],
       "x", f"Repeat r new / repeat r previous at preset {preset}, median.", [f"runs/ours/scale/w1-{{new,base}}/r*/{preset}/summary.json"])

# hint search scale
HB = ("200", "800", "1000", "2000", "4000")
hint_scale = {}
for preset in ("1core", "ccx", "phys24", "smt48"):
    reps = SC["hint-new"][preset]
    rs = sorted(reps)
    src = [f"runs/ours/scale/hint-new/r{{1,2,3}}/{preset}/summary.json"]
    hint_scale[preset] = {}
    for b in HB:
        sps = [reps[r]["hint"][b]["searches_per_s_sum"] for r in rs]
        mmd = [reps[r]["hint"][b]["median_ms_median"] for r in rs]
        eff = [reps[r]["scaling"][b]["efficiency"] if reps[r].get("scaling") else 1.0 for r in rs]
        d = (f"Browser-hint search (native, one thread per process) at {b} sims, preset {preset} "
             f"({reps[rs[0]]['n']} processes on CPUs {PRESET_CPUS[preset]}), 31 timed reps per budget per process; 3 repeats.")
        MS(f"ours.scale.hint.new.{preset}.sims{b}.searches_per_s", sps, "searches/s", d + " Sum of per-process searches/s.", src)
        MS(f"ours.scale.hint.new.{preset}.sims{b}.median_ms", mmd, "ms", d + " Median over processes of each process's median ms.", src)
        MS(f"ours.scale.hint.new.{preset}.sims{b}.efficiency_vs_1core", eff, "fraction", d + " Per-process efficiency vs 1core.", src)
        hint_scale[preset][b] = (med(sps), med(mmd), med(eff))
    ghz = [reps[r]["perf"]["ghz_user_median"] for r in rs]
    MS(f"ours.scale.hint.new.{preset}.ghz", ghz, "GHz", "Median effective user clock over processes.", src)
    CHECK(f"scale hint-new {preset}: same position + pv0 in every process", all(reps[r]["identity"]["same"] and
          reps[r]["identity"]["distinct_position_pv0"] == 1 for r in rs), "identity.distinct_position_pv0 == 1")
bh = max(("phys24", "smt48"), key=lambda p: hint_scale[p]["1000"][0])
M("ours.scale.hint.new.whole_machine_best.preset", bh, "preset", "Best of phys24/smt48 by searches/s at 1,000 sims.",
  ["runs/ours/scale/hint-new/r*/{phys24,smt48}/summary.json"])
for b in HB:
    M(f"ours.scale.hint.new.whole_machine_best.sims{b}.searches_per_s", hint_scale[bh][b][0], "searches/s",
      f"Whole machine, preset {bh}: independent hint searches per second at {b} sims (median of 3 repeats).",
      [f"runs/ours/scale/hint-new/r*/{bh}/summary.json"], n=3)

# ============================================================================= 4. search (native hint, wasm, advisor)
def parse_hint(path):
    d = {"budgets": {}}
    for line in open(path):
        s = line.strip()
        if s.startswith("position "):
            d["position"] = s.split(" ", 1)[1]
        if s.startswith("browser="):
            d["browser"] = s.split("=", 1)[1]
        m = re.match(r"sims=(\d+) cold_ms=(\S+) median_ms=(\S+) min_ms=(\S+) ms_per_sim=(\S+) pv0=(.*)$", s)
        if m:
            d["budgets"][m[1]] = {"cold": float(m[2]), "median": float(m[3]), "min": float(m[4]), "pv0": m[6]}
    return d


HN = {T: {i: parse_hint(f"{R}/ours/hint/{T}-{i}.txt") for i in range(1, 6)} for T in ("base", "new")}
WN = {T: {i: parse_hint(f"{R}/wasm/node/{T}-{i}.txt") for i in range(1, 6)} for T in ("base", "new")}
WC = {T: {i: parse_hint(f"{R}/wasm/chrome/{T}-{i}.txt") for i in range(1, 6)} for T in ("base", "new")}
allh = [x for D in (HN, WN, WC) for T in D for x in D[T].values()]
CHECK("hint search: same position and pv0 at all 5 budgets in every native/Node/Chromium run, previous and new",
      all(x["position"] == EXP["hint_position"] for x in allh) and
      len({json.dumps({b: x["budgets"][b]["pv0"] for b in HB}, sort_keys=True) for x in allh}) == 1,
      f"{len(allh)} runs; position '{EXP['hint_position']}'")
for D, host, src in ((HN, "native", "runs/ours/hint/{base,new}-{1..5}.txt"), (WN, "node", "runs/wasm/node/{base,new}-{1..5}.txt"),
                     (WC, "chrome", "runs/wasm/chrome/{base,new}-{1..5}.txt")):
    hd = {"native": "native binary (hint_bench), one thread on CPU 20",
          "node": f"WASM in Node {HW['Node'].split(' ')[0]} (V8), process pinned to CPUs 18-23",
          "chrome": "WASM in headless Chromium 149.0.7827.55 (Playwright), pinned to CPUs 18-23"}[host]
    for b in HB:
        for T in ("base", "new"):
            xs = [D[T][i]["budgets"][b]["median"] for i in range(1, 6)]
            MS(f"search.hint.{host}.{T}.sims{b}.ms", xs, "ms",
               f"Browser-hint MCTS search on the fixed BENCH1 position (Fuzzy Wurm Crawler 57 HP, starter hand), {b} sims, "
               f"{hd}; per run = median of 7 timed reps; median over 5 runs ({'previous' if T == 'base' else 'new'} engine).",
               [src, "runs/wasm/index.tsv" if host != "native" else "runs/ours/index.tsv"])
        pr = [D["base"][i]["budgets"][b]["median"] / D["new"][i]["budgets"][b]["median"] for i in range(1, 6)]
        M(f"search.hint.{host}.speedup_new_vs_base.sims{b}.ratio_of_medians",
          V(f"search.hint.{host}.base.sims{b}.ms") / V(f"search.hint.{host}.new.sims{b}.ms"), "x",
          f"Previous ms / new ms at {b} sims ({host}).", [src], n=5)
        MS(f"search.hint.{host}.speedup_new_vs_base.sims{b}.median_pair_ratio", pr, "x",
           f"Median over 5 ABBA pairs of previous/new ms at {b} sims ({host}).", [src])
for host in ("node", "chrome"):
    for b in HB:
        for T in ("base", "new"):
            M(f"search.hint.{host}_over_native.{T}.sims{b}", V(f"search.hint.{host}.{T}.sims{b}.ms") / V(f"search.hint.native.{T}.sims{b}.ms"),
              "x", f"WASM ({host}) median ms / native median ms, same engine ({T}), {b} sims. >1 = WASM slower.",
              [f"runs/wasm/{host}/{T}-*.txt", f"runs/ours/hint/{T}-*.txt"])
wchk = [x for x in open(os.path.join(R, "wasm/index.tsv")) if "\tcheck\t" in x]
CHECK("wasm stage self-checks (index.tsv 'check' lines)", len(wchk) == 2 and all("same\tnative:same" in x for x in wchk),
      "; ".join(x.strip().split("\t", 1)[1] for x in wchk))

# advisor
ADV = {}
for cell, lab in (("1t", "1 thread on CPU 20"), ("8t-l3", "8 root-parallel threads, mask 6-17 = two whole L3 domains (12-worker rayon pool)"),
                  ("8t-pin", "8 threads, every rayon worker pinned 1:1 to CPUs 6-13")):
    ADV[cell] = {}
    for T in ("base", "new"):
        rows = [last_json(f"{R}/ours/adv/{cell}-{T}-{i}.jsonl") for i in range(1, 5)]
        ADV[cell][T] = rows
        MS(f"search.advisor.{cell}.{T}.ms", [r["median_s"] * 1000 for r in rows], "ms",
           f"Real-game advisor MCTS, 8,000 sims, Vantom boss position (A5, seed 0), {lab}; via the PyO3 module "
           f"(Python 3.13); per run = median of 7 reps; 4 runs ({'previous' if T == 'base' else 'new'} engine).",
           [f"runs/ours/adv/{cell}-{T}-{{1..4}}.jsonl"])
        MS(f"search.advisor.{cell}.{T}.sims_per_s", [r["sims_per_s_median"] for r in rows], "sims/s", f"Real-game advisor MCTS, 8,000 sims, {lab}: sims/s per run (median of 7 reps); 4 runs ({'previous' if T == 'base' else 'new'} engine).",
           [f"runs/ours/adv/{cell}-{T}-{{1..4}}.jsonl"])
    pr = [ADV[cell]["base"][i]["median_s"] / ADV[cell]["new"][i]["median_s"] for i in range(4)]
    M(f"search.advisor.{cell}.speedup_new_vs_base.ratio_of_medians",
      V(f"search.advisor.{cell}.base.ms") / V(f"search.advisor.{cell}.new.ms"), "x", "Previous ms / new ms.",
      [f"runs/ours/adv/{cell}-{{base,new}}-*.jsonl"], n=4)
    MS(f"search.advisor.{cell}.speedup_new_vs_base.median_pair_ratio", pr, "x", "Median over 4 ABBA pairs of previous/new ms.",
       [f"runs/ours/adv/{cell}-{{base,new}}-*.jsonl"])
    exp = EXP["adv_1t"] if cell == "1t" else EXP["adv_8t"]
    CHECK(f"advisor {cell}: digest {exp} in all 8 runs (base == new)",
          all(r["digest"] == exp and r["sims"] == 8000 for T in ADV[cell] for r in ADV[cell][T]),
          str(sorted({r['digest'] for T in ADV[cell] for r in ADV[cell][T]})))
M("search.advisor.new.8t_speedup_over_1t.pinned", V("search.advisor.1t.new.ms") / V("search.advisor.8t-pin.new.ms"), "x",
  "New engine: 1-thread ms / 8-thread pinned ms (root-parallel; 8 trees).", ["runs/ours/adv/*-new-*.jsonl"])

# ============================================================================= 5. the shipped game, headless (C#)
CS = {}
for line in open(os.path.join(B, "csharp/runs/results.jsonl")):
    d = json.loads(line)
    CS[d["cell"]] = d
cssrc = ["csharp/runs/results.jsonl", "csharp/runs/measure.log"]
CHECK("C# cells: none forced", all(CS[c]["forced"] == 0 for c in CS), str({c: CS[c]["forced"] for c in CS}))
# A0 = the old site method
A0s = CS["A0"]["summary"]
a0rows = [json.loads(x) for x in open(os.path.join(B, "csharp/runs/A0.jsonl")) if x.startswith('{"i"')]
# The published M2 run's per-fight (steps, won) rows (cs_random_fights.py --per-encounter 3).
REFP = os.environ.get("CS_REF_JSONL") or os.path.join(HERE, "..", "csharp", "m2_reference.jsonl")
refrows = {r["i"]: r for r in (json.loads(x) for x in open(REFP) if x.startswith('{"i"'))}
a0match = sum(1 for r in a0rows if refrows.get(r["i"], {}).get("steps") == r["steps"] and refrows[r["i"]].get("won") == r["won"])
CHECK("C# A0: every fight's steps and outcome equal the published reference rows", a0match == len(a0rows) == 66,
      f"{a0match}/{len(a0rows)} match {rel(REFP)}")
a0cpu = sum(r["cpu_s"] for r in a0rows) / sum(r["steps"] for r in a0rows) * 1000
a0def = ("Cell A0 = the published (old) site method: cs_random_fights.py, a FRESH game process per fight (cold JIT every "
         "fight), 66 fights (3 per encounter), game process + Python driver confined to CPUs 20,44 (one physical core); "
         "play-only wall time (setup untimed).")
M("cs.A0.ms_per_action_wall", A0s["ms_per_decision"], "ms/action", a0def, cssrc + ["csharp/runs/A0.out"], n=A0s["decisions"])
M("cs.A0.actions_per_s", A0s["decisions_per_s_playonly"], "actions/s", a0def, cssrc, n=A0s["decisions"])
M("cs.A0.fights_per_s", A0s["fights_per_s_playonly"], "fights/s", a0def, cssrc, n=A0s["fights_ok"])
M("cs.A0.process_cpu_ms_per_action", a0cpu, "ms CPU/action",
  "Cell A0: sum of per-fight process CPU (10 ms ticks) / actions, cold processes.", ["csharp/runs/A0.jsonl"], n=len(a0rows))
M("cs.A0.cpu_util_during_play", A0s["cpu_util_during_play"], "fraction", "Cell A0: game-process CPU / play wall time.", cssrc)
M("cs.A0.setup_s_median", A0s["setup_s_median"], "s", "Cell A0: median per-fight process start + setup (untimed).", cssrc)

# A = one warm process
As = CS["A"]["summary"]
At, Am = As["timed"], As["machine"]
adef = ("Cell A = ONE warm game process (+ its Python driver) on CPUs 20,44 (one physical core): 220 warm-up fights, then "
        "the same 220 fights timed (seeds 0-219, one fight per seed). The game is driven over JSON/stdio; each fight is "
        "reset by load_save (untimed in 'play-only').")
M("cs.A.ms_per_action_wall_playonly", At["ms_per_decision"], "ms/action", adef + " Play-only wall time per action.", cssrc, n=At["decisions"])
M("cs.A.actions_per_s_playonly", At["decisions_per_s_playonly"], "actions/s", adef + " Play-only.", cssrc, n=At["decisions"])
M("cs.A.actions_per_s_incl_reset", Am["decisions_per_s"], "actions/s", adef + " Whole timed window incl. the ~30 ms reset per fight.",
  cssrc, n=Am["decisions"])
M("cs.A.fights_per_s_incl_reset", Am["fights_per_s"], "fights/s", adef + " Incl. reset.", cssrc, n=Am["fights"])
M("cs.A.process_cpu_ms_per_action", At["cpu_ms_per_decision"], "ms CPU/action",
  adef + " Process CPU (all 9 threads) per action during play: an UPPER BOUND on the game logic (includes the harness's "
         "state-dict/JSON work and background JIT).", cssrc)
M("cs.A.gamethread_cpu_ms_per_action", At["gamethread_cpu_ms_per_decision"], "ms CPU/action",
  adef + " CPU of the STS2-GameThread alone (schedstat) per action; still includes the harness's state-dict building.", cssrc)
M("cs.A.driver_cpu_ms_per_action", At["driver_cpu_ms_per_decision"], "ms CPU/action", adef + " Python driver CPU per action.", cssrc)
M("cs.A.jit_thread_cpu_ms_per_action", At["jit_thread_cpu_ms_per_decision"], "ms CPU/action", adef + " Background tier-1 JIT thread.", cssrc)
M("cs.A.cpu_util_during_play", At["cpu_util_during_play"], "fraction",
  adef + " Process CPU / play wall: the game process is mostly ASLEEP (Thread.Sleep(1) polling in the harness).", cssrc)
M("cs.A.gamethread_voluntary_switches_per_action", At["gamethread_voluntary_switches_per_decision"], "count/action",
  adef + " Voluntary context switches of the game thread per action (polling sleeps).", cssrc)
M("cs.A.win_rate", At["win_rate"], "fraction", adef + " Random-play win rate (220 fights).", cssrc, n=At["fights_ok"])
M("cs.A.actions_per_fight", At["decisions_per_fight"], "actions/fight", adef, cssrc, n=At["fights_ok"])
M("cs.A.rss_mb_max", As["rss_mb_per_process_max"], "MB", adef + " Max RSS of the game process.", cssrc)
M("cs.A.init_s", As["init_s_median"], "s", adef + " First load_save (ModelDb init).", cssrc)
wvc = As["warm_vs_cold_same_fights"]
M("cs.A.jit.cold_over_warm_wall", wvc["cold_over_warm_wall"], "x",
  f"Cell A: the same {wvc['pairs']} fights played cold (first pass in the fresh process) vs warm (second pass): wall ms/action "
  f"{wvc['cold_ms_per_action']} vs {wvc['warm_ms_per_action']}. Steps and outcome identical in {wvc['same_steps_and_outcome']}/{wvc['pairs']}.",
  cssrc, n=wvc["pairs"])
M("cs.A.jit.cold_over_warm_cpu", wvc["cold_over_warm_cpu"], "x", f"Cell A: the same {wvc['pairs']} fights cold vs warm, process CPU per action cold / warm.", cssrc, n=wvc["pairs"])
jc0 = As["jit_curve"][0]
M("cs.A.jit.first_fight_ms_per_action_wall", jc0["ms_per_action"], "ms/action", "Cell A, fight k=0 in the fresh process (coldest).", cssrc)
M("cs.A.jit.first_fight_cpu_ms_per_action", jc0["cpu_ms_per_action"], "ms CPU/action", "Cell A, fight k=0, process CPU.", cssrc)
M("cs.A.jit.first_fight_gamethread_cpu_ms_per_action", jc0["gamethread_cpu_ms_per_action"], "ms CPU/action", "Cell A, fight k=0, game thread.", cssrc)
CHECK("C# A0 vs warm A: identical fights give identical steps/outcome cold and warm",
      wvc["same_steps_and_outcome"] == wvc["pairs"], f"{wvc['same_steps_and_outcome']}/{wvc['pairs']}")

cs_cells = {}
for c in ("A", "B2", "B4", "B8", "B16", "C1", "C2", "C3", "C4", "C5", "C6"):
    s = CS[c]["summary"]
    t, m_, host = s["timed"], s["machine"], s["host"]
    cs_cells[c] = s
    if c == "A":
        continue
    grp = "B" if c.startswith("B") else "C"
    dd = (f"Cell {c}: {s['workers']} warm game processes (+1 Python driver each), pin={s['pin']}, "
          f"{(format(s['processes_per_cpu_set'], 'g') + ' per CPU set') if s['processes_per_cpu_set'] else 'unpinned'}, "
          f"{s['cpus_used']} CPUs = {s['physical_cores_used']} physical core(s); "
          f"{s['warmup']} warm-up fights then {s['per_worker_fights']} timed fights per process ({s['fights_planned']} in total). "
          "Throughput = all actions / the common timed window, INCLUDING the ~30 ms per-fight reset.")
    k = f"cs.{c}"
    M(f"{k}.workers", s["workers"], "processes", dd, cssrc)
    M(f"{k}.actions_per_s", m_["decisions_per_s"], "actions/s", dd, cssrc, n=m_["decisions"])
    M(f"{k}.fights_per_s", m_["fights_per_s"], "fights/s", dd, cssrc, n=m_["fights"])
    M(f"{k}.actions_per_s_allbusy", m_["allbusy_decisions_per_s"], "actions/s",
      dd + " Restricted to the window in which every process was still busy.", cssrc)
    M(f"{k}.actions_per_s_per_physical_core", m_["decisions_per_s_per_physical_core"], "actions/s per core", dd, cssrc)
    M(f"{k}.busy_pct_worker_cpus", host["busy_worker_cpus_pct_timed"], "%",
      dd + " Busy % of the cell's CPUs during the timed window (C3 is unpinned: n/a).", cssrc)
    M(f"{k}.busy_pct_all_cpus", host["busy_all_pct_timed"], "%", dd + " Busy % of all 48 CPUs during the timed window.", cssrc)
    M(f"{k}.mhz_mean", host["freq_mhz_timed"]["mean"], "MHz", dd + " Mean scaling_cur_freq sampled during the window.", cssrc,
      spread=[host["freq_mhz_timed"]["min"], host["freq_mhz_timed"]["max"]], n=host["freq_mhz_timed"]["samples"])
    M(f"{k}.ghz_mean", host["freq_mhz_timed"]["mean"] / 1000, "GHz", dd + " Mean sampled core clock during the window.", cssrc)
    M(f"{k}.process_cpu_ms_per_action", t["cpu_ms_per_decision"], "ms CPU/action", dd + " Process CPU per action during play.", cssrc)
    M(f"{k}.gamethread_cpu_ms_per_action", t["gamethread_cpu_ms_per_decision"], "ms CPU/action", dd + " Game-thread CPU per action.", cssrc)
    M(f"{k}.ms_per_action_wall_per_process", t["ms_per_decision"], "ms/action", dd + " Per-process play-only wall per action (latency).", cssrc)
    M(f"{k}.window_s", m_["window_s"], "s", dd + " Common timed window.", cssrc)
    M(f"{k}.rss_mb_max", s["rss_mb_per_process_max"], "MB", dd, cssrc)

# identities
grp_dig = {"A": ["A"], "B": ["B2", "B4", "B8", "B16"], "C1-3": ["C1", "C2", "C3"], "C4-6": ["C4", "C5", "C6"]}
for g, cells in grp_dig.items():
    ds = {cs_cells[c]["digest"] for c in cells}
    CHECK(f"C# {g}: one digest across cells (same fights, different process layouts)", len(ds) == 1, str(ds))
for c, s in cs_cells.items():
    ok = (s["digest_complete"] and s["start_state_mismatches"] == 0 and s["ref_check"]["match"] == s["ref_check"]["compared"] == 66
          and s["workers_ok"] == s["workers"] and s["setup_fingerprints"] == [EXP["cs_setup_fp"]] and s["world_error_fights"] == 0
          and s["harness_warning_fights"] == 0 and s["timed"]["fights_failed"] == 0)
    CHECK(f"C# {c}: digest complete, 0 start-state mismatches, ref 66/66, all workers ok, 1 setup fingerprint, 0 errors", ok,
          f"digest {s['digest']}, ref {s['ref_check']['match']}/{s['ref_check']['compared']}, workers {s['workers_ok']}/{s['workers']}")

bestB = max(("B2", "B4", "B8", "B16"), key=lambda c: cs_cells[c]["machine"]["decisions_per_s"])
bestC = max(("C1", "C2", "C3", "C4", "C5", "C6"), key=lambda c: cs_cells[c]["machine"]["decisions_per_s"])
bestCab = max(("C1", "C2", "C3", "C4", "C5", "C6"), key=lambda c: cs_cells[c]["machine"]["allbusy_decisions_per_s"])
M("cs.best_one_core.cell", bestB, "cell", "C# saturated ONE physical core: the B cell (2-16 processes on CPUs 20,44) with the "
  "highest actions/s.", cssrc)
M("cs.best_one_core.actions_per_s", cs_cells[bestB]["machine"]["decisions_per_s"], "actions/s",
  f"C# one physical core, saturated (best B cell = {bestB}, incl. reset).", cssrc)
M("cs.best_one_core.fights_per_s", cs_cells[bestB]["machine"]["fights_per_s"], "fights/s", f"Best B cell ({bestB}).", cssrc)
M("cs.best_whole_machine.cell", bestC, "cell", "C# whole machine: the C cell (24-384 processes on all 48 CPUs) with the highest "
  "actions/s (incl. reset).", cssrc)
M("cs.best_whole_machine.actions_per_s", cs_cells[bestC]["machine"]["decisions_per_s"], "actions/s",
  f"C# whole machine, best C cell = {bestC} (incl. reset).", cssrc)
M("cs.best_whole_machine.fights_per_s", cs_cells[bestC]["machine"]["fights_per_s"], "fights/s", f"Best C cell ({bestC}).", cssrc)
M("cs.best_whole_machine.per_physical_core_actions_per_s", cs_cells[bestC]["machine"]["decisions_per_s"] / 24, "actions/s per core",
  f"Best C cell ({bestC}) / 24 physical cores.", cssrc)
M("cs.best_whole_machine.allbusy.cell", bestCab, "cell", "C cell with the highest all-busy-window actions/s (sensitivity).", cssrc)
M("cs.best_whole_machine.allbusy.actions_per_s", cs_cells[bestCab]["machine"]["allbusy_decisions_per_s"], "actions/s",
  "Best C cell by the all-busy window (excludes the ramp-down tail).", cssrc)
M("cs.whole_machine_scaling_vs_best_one_core", cs_cells[bestC]["machine"]["decisions_per_s"] / (24 * cs_cells[bestB]["machine"]["decisions_per_s"]),
  "fraction", f"C#: best C ({bestC}) / (24 x best B ({bestB})).", cssrc)
M("cs.cpu_ms_per_action_range_pool_cells", [min(cs_cells[c]["timed"]["cpu_ms_per_decision"] for c in cs_cells),
                                            max(cs_cells[c]["timed"]["cpu_ms_per_decision"] for c in cs_cells)],
  "ms CPU/action [min,max]", "Process CPU per action across all pool cells A..C6 (context for the CPU-time ratio).", cssrc)
M("cs.gamethread_cpu_ms_per_action_range_pool_cells", [min(cs_cells[c]["timed"]["gamethread_cpu_ms_per_decision"] for c in cs_cells),
                                                       max(cs_cells[c]["timed"]["gamethread_cpu_ms_per_decision"] for c in cs_cells)],
  "ms CPU/action [min,max]", "Game-thread CPU per action across all pool cells.", cssrc)

# the game's random-play outcome on the largest fight set (C4/C5/C6 = the same 8,448 fights)
c5 = cs_cells["C5"]["timed"]
M("cs.random_play.win_rate", c5["win_rate"], "fraction",
  "The shipped game, random play: 8,448 fights (seeds 0-8447, one fight each, encounter = seed mod 22), cells C4/C5/C6 "
  "(identical digest).", cssrc, n=c5["fights_ok"])
M("cs.random_play.actions_per_fight", c5["decisions_per_fight"], "actions/fight", "The shipped game, random play, mean actions per fight over the 8,448 fights of cells C4/C5/C6 (identical digest).", cssrc, n=c5["fights_ok"])
# per encounter (C5 worker rows, timed phase)
cs_enc = {}
for f in glob.glob(os.path.join(B, "csharp/runs/C5/worker-*.jsonl")):
    for line in open(f):
        if '"phase": "timed"' in line:
            r = json.loads(line)
            e = cs_enc.setdefault(r["encounter"], [0, 0, 0])
            e[0] += 1
            e[1] += 1 if r["won"] else 0
            e[2] += r["steps"]
CHECK("C# C5 per-worker rows: 8,448 timed fights, 384 per encounter",
      sum(v[0] for v in cs_enc.values()) == 8448 and all(v[0] == 384 for v in cs_enc.values()),
      f"{sum(v[0] for v in cs_enc.values())} fights over {len(cs_enc)} encounters")

# ============================================================================= 6. ratios Rust vs C#
o1 = V("ours.w1.1core.new.actions_per_s")
o1_us = 1e6 / o1
s1, s1smt = S_NEW["1core"]["agg"], S_NEW["1core-smt"]["agg"]
bw_preset, bw = best_whole(S_NEW)
rsrc = ["runs/ours/w1/new-*.json", "csharp/runs/results.jsonl"]
M("ratio.one_process_wall.cold_A0", o1 / A0s["decisions_per_s_playonly"], "x",
  f"(a) One process, wall time per action: C# cell A0 (fresh process per fight, cold JIT, {A0s['ms_per_decision']} ms/action, "
  f"the OLD site method = the retired '23,000x') / ours one core (W1 new, {o1_us:.3f} µs/action). Mostly harness polling sleep, "
  "not game logic.", rsrc, numerator=f"C# A0 {A0s['ms_per_decision']} ms/action",
  denominator=f"ours 1core new {o1_us:.4f} µs/action")
M("ratio.one_process_wall.warm_A", o1 / At["decisions_per_s_playonly"], "x",
  f"(a) One process, wall time per action: C# cell A (one warm process, play-only {At['ms_per_decision']} ms/action) / ours one core "
  f"({o1_us:.3f} µs/action).", rsrc, numerator=f"C# A {At['ms_per_decision']} ms/action", denominator=f"ours {o1_us:.4f} µs/action")
M("ratio.one_process_wall.warm_A_incl_reset", o1 / Am["decisions_per_s"], "x",
  f"(a') As above but C# including the per-fight reset ({Am['decisions_per_s']} actions/s).", rsrc)
M("ratio.cpu_time.process", At["cpu_ms_per_decision"] * 1e3 / o1_us, "x",
  f"(b) CPU time per action: C# cell A process CPU ({At['cpu_ms_per_decision']} ms, all threads, upper bound on game logic) / ours "
  f"({o1_us:.3f} µs; single-threaded, CPU-bound so wall = CPU).", rsrc,
  numerator=f"C# A process CPU {At['cpu_ms_per_decision']} ms/action", denominator=f"ours {o1_us:.4f} µs/action")
M("ratio.cpu_time.gamethread", At["gamethread_cpu_ms_per_decision"] * 1e3 / o1_us, "x",
  f"(b) CPU time per action, game thread only: C# cell A STS2-GameThread CPU ({At['gamethread_cpu_ms_per_decision']} ms) / ours "
  f"({o1_us:.3f} µs).", rsrc, numerator=f"C# A game-thread CPU {At['gamethread_cpu_ms_per_decision']} ms/action",
  denominator=f"ours {o1_us:.4f} µs/action")
lo = min(cs_cells[c]["timed"]["gamethread_cpu_ms_per_decision"] for c in cs_cells)
loc = min(cs_cells, key=lambda c: cs_cells[c]["timed"]["gamethread_cpu_ms_per_decision"])
M("ratio.cpu_time.gamethread_lowest_cell", lo * 1e3 / o1_us, "x",
  f"(b) Sensitivity: the LOWEST game-thread CPU per action seen in any pool cell ({loc}: {lo} ms) / ours one core "
  "(most generous to C#).", rsrc)
M("ratio.cpu_time.process_range_cells", [g6(min(cs_cells[c]["timed"]["cpu_ms_per_decision"] for c in cs_cells) * 1e3 / o1_us),
                                          g6(max(cs_cells[c]["timed"]["cpu_ms_per_decision"] for c in cs_cells) * 1e3 / o1_us)],
  "x [min,max]", "(b) Process-CPU ratio over every C# pool layout (A, B2-B16, C1-C6) / ours one core.", rsrc)
M("ratio.cpu_time.gamethread_range_cells", [g6(lo * 1e3 / o1_us),
                                             g6(max(cs_cells[c]["timed"]["gamethread_cpu_ms_per_decision"] for c in cs_cells) * 1e3 / o1_us)],
  "x [min,max]", "(b) Game-thread CPU ratio over every C# pool layout / ours one core.", rsrc)
csB = cs_cells[bestB]["machine"]["decisions_per_s"]
M("ratio.saturated_one_core.ours_1thread", o1 / csB, "x",
  f"(c) One physical core, saturated: ours ONE thread (W1 new, CPU 20, sibling idle, {o1:,.0f} actions/s) / C# best B cell "
  f"({bestB}: {cs_cells[bestB]['workers']} game processes + drivers on both threads of that core, {csB} actions/s).", rsrc,
  numerator=f"ours 1core {o1:,.0f} actions/s", denominator=f"C# {bestB} {csB} actions/s")
M("ratio.saturated_one_core.ours_both_threads", s1smt / csB, "x",
  f"(c) One physical core, both threads: ours 1core-smt (2 processes on CPUs 20+44, scale runs, {s1smt:,.0f} actions/s) / C# best "
  f"B ({bestB}, {csB} actions/s). Like for like: both use both hardware threads of one core.",
  ["runs/ours/scale/w1-new/r*/1core-smt/summary.json", "csharp/runs/results.jsonl"],
  numerator=f"ours 1core-smt {s1smt:,.0f} actions/s", denominator=f"C# {bestB} {csB} actions/s")
M("ratio.saturated_one_core.ours_1thread_scale_baseline", s1 / csB, "x",
  "(c) Sensitivity: ours 1core from the scale runs (440k fights) instead of the W1 stage.", ["runs/ours/scale/w1-new/r*/1core/summary.json"])
csC = cs_cells[bestC]["machine"]["decisions_per_s"]
M("ratio.whole_machine", bw / csC, "x",
  f"(d) Whole machine: ours best whole-machine preset ({bw_preset}, {bw:,.0f} actions/s) / C# best C cell ({bestC}: "
  f"{cs_cells[bestC]['workers']} processes, {csC} actions/s incl. reset).",
  [f"runs/ours/scale/w1-new/r*/{bw_preset}/summary.json", "csharp/runs/results.jsonl"],
  numerator=f"ours {bw_preset} {bw:,.0f} actions/s", denominator=f"C# {bestC} {csC} actions/s")
M("ratio.whole_machine.vs_cs_allbusy", bw / cs_cells[bestCab]["machine"]["allbusy_decisions_per_s"], "x",
  f"(d) Sensitivity: C# measured over its all-busy window ({bestCab}).", ["csharp/runs/results.jsonl"])
M("ratio.fights.whole_machine", S_NEW[bw_preset]["agg2"] / cs_cells[bestC]["machine"]["fights_per_s"], "x",
  "(d) in fights/s (fights are the same distribution: win rate and actions/fight agree).", rsrc)
M("ratio.published_23000x_recomputed", 1297150 / 55.79, "x",
  "The previously published ratio (perf doc 2026-10-14): 1,297,150 / 55.79 actions/s = cold one-process wall time. Retired.",
  ["(published site perf doc)"])

# ============================================================================= 7. cross-engine
ENG = {"ours": "ours (new, Burning Blood live)", "ours-norelic": "ours (new, --relics none)",
       "sansb-inst": "sansb/sts2-sim, instance policy", "sansb-unif": "sansb/sts2-sim, its own deduplicated uniform policy",
       "core-inst": "sts2core, instance policy", "core-eng": "sts2core, its own deduplicated engine policy"}
for t in XTAGS:
    rr = [X1[t][i] for i in range(1, 8)]
    ds = {x["digest"] for x in rr}
    CHECK(f"xsim W1 {t}: same digest in all 7 rounds (within-engine)", len(ds) == 1 and all(x["fights"] == 110000 for x in rr), str(ds))
    src = [s.replace("<tag>", t) for s in xsrc]
    dd = (f"W1 per core: {ENG[t]}; one process on CPU 20 (sibling idle), 110,000 fights (2,200 seeds x 50); 7 rounds, "
          "the engine order rotated each round.")
    MS(f"xsim.w1.{t}.actions_per_s", [x["actions_per_s"] for x in rr], "actions/s", dd, src)
    MS(f"xsim.w1.{t}.fights_per_s", [x["fights_per_s"] for x in rr], "fights/s", dd, src)
    MS(f"xsim.w1.{t}.ns_per_action", [1e9 / x["actions_per_s"] for x in rr], "ns/action", dd, src)
    M(f"xsim.w1.{t}.win_rate", rr[0]["wins"] / rr[0]["fights"], "fraction", dd + " Random-play win rate.", src, n=rr[0]["fights"])
    M(f"xsim.w1.{t}.actions_per_fight", rr[0]["actions"] / rr[0]["fights"], "actions/fight", dd, src, n=rr[0]["fights"])
    M(f"xsim.w1.{t}.digest", rr[0]["digest"], "digest", dd, src)
    MS(f"xsim.w1.{t}.ipc", [x["perf"]["instructions:u"] / x["perf"]["cycles:u"] for x in rr], "instructions/cycle", dd + " Whole process.", src)
    MS(f"xsim.w1.{t}.ghz", [x["perf"]["cycles:u"] / (x["perf"]["task-clock"] * 1e6) for x in rr], "GHz", dd + " Whole process.", src)
    MS(f"xsim.w1.{t}.instructions_per_action", [x["perf"]["instructions:u"] / x["actions"] for x in rr], "instructions/action",
       dd + " Whole process (includes each engine's untimed start-state setup).", src)
CHECK("xsim W1 sansb instance digest equals the pre-registered value", X1["sansb-inst"][1]["digest"] == EXP["sansb_w1_inst"],
      X1["sansb-inst"][1]["digest"])
CHECK("xsim W1 sts2core instance digest equals the pre-registered value", X1["core-inst"][1]["digest"] == EXP["core_w1_inst"],
      X1["core-inst"][1]["digest"])
for a, b_ in (("ours", "sansb-inst"), ("ours-norelic", "sansb-inst"), ("ours", "sansb-unif"), ("ours-norelic", "sansb-unif"),
              ("ours", "core-inst"), ("ours", "core-eng"), ("ours-norelic", "core-inst")):
    pr = [X1[a][i]["actions_per_s"] / X1[b_][i]["actions_per_s"] for i in range(1, 8)]
    M(f"xsim.w1.ratio.{a}_over_{b_}.ratio_of_medians", V(f"xsim.w1.{a}.actions_per_s") / V(f"xsim.w1.{b_}.actions_per_s"), "x",
      f"W1 per core, actions/s: {ENG[a]} / {ENG[b_]} (>1 = ours faster).", [xsrc[0]], n=7)
    MS(f"xsim.w1.ratio.{a}_over_{b_}.median_round_ratio", pr, "x", f"W1 per core, actions/s: {ENG[a]} / {ENG[b_]}; median over the 7 rotation rounds of the same-round ratio (paired).", [xsrc[0]])

# W2 cross-engine
XW2 = os.path.join(R, "xsim/w2")


def xw2(tag, i):
    j = last_json(f"{XW2}/{tag}-{i}.out")
    if tag == "ours":
        c = j["clone"]
        return {"tx": j["transitions"], "rate": c["tx_per_s_median"], "playouts_per_s": c["playouts_per_s_median"],
                "wins": c["wins"], "win_rate": c["win_rate"], "apf": c["actions_per_playout"], "digest": c["digest"]}
    if tag == "sansb-own":
        t = j["timing"]
        return {"tx": j["work"]["transitions"], "rate": t["transitions_per_second"], "playouts_per_s": t["complete_playouts_per_second"],
                "wins": None, "win_rate": None, "apf": j["work"]["transitions"] / j["config"]["trajectories"], "digest": j["work"]["checksum"]}
    if tag.startswith("sansb"):
        return {"tx": j["work"]["transitions"], "rate": j["actions_per_s"], "playouts_per_s": j["playouts_per_s"], "wins": j["wins"],
                "win_rate": j["win_rate"], "apf": j["mean_actions_per_playout"], "digest": j["work"]["checksum"]}
    return {"tx": j["transitions"], "rate": j["transitions_per_second"], "playouts_per_s": j["playouts_per_second"], "wins": j["wins"],
            "win_rate": j["win_rate"], "apf": j["mean_actions_per_playout"], "digest": j["checksum"]}


W2TAGS = {"ours": "ours (new), clone-and-apply per step (sansb's semantics), A10", "sansb-own": "sansb's own `sts-sim bench` (uniform, as published)",
          "sansb-unif": "sansb, xsim harness, its deduplicated uniform policy", "sansb-inst": "sansb, instance policy",
          "core-eng": "sts2core, its deduplicated engine policy, A10", "core-inst": "sts2core, instance policy, A10"}
X2 = {t: {i: xw2(t, i) for i in range(1, 6)} for t in W2TAGS}
x2src = ["runs/xsim/w2/<tag>-{1..5}.out", "runs/xsim/index.tsv"]
for t in W2TAGS:
    rr = [X2[t][i] for i in range(1, 6)]
    ds = {(x["digest"], x["tx"], x["wins"]) for x in rr}
    CHECK(f"xsim W2 {t}: same checksum/transitions/wins in all 5 rounds (within-engine)", len(ds) == 1, str(ds))
    src = [s.replace("<tag>", t) for s in x2src]
    dd = (f"W2 per core = sansb's own bench fixture (Ironclad starter 68/80 HP, no relic, vs 2 Toadpoles 26/25 HP; an ASCENSION-10 "
          f"fight because the fixture elides the field), 240,000 playouts, 64-action cap: {W2TAGS[t]}; CPU 20; 5 rotation rounds.")
    MS(f"xsim.w2.{t}.actions_per_s", [x["rate"] for x in rr], "actions/s", dd, src)
    MS(f"xsim.w2.{t}.ns_per_action", [1e9 / x["rate"] for x in rr], "ns/action", dd, src)
    MS(f"xsim.w2.{t}.playouts_per_s", [x["playouts_per_s"] for x in rr], "playouts/s", dd, src)
    M(f"xsim.w2.{t}.win_rate", rr[0]["win_rate"], "fraction", dd, src, n=240000)
    M(f"xsim.w2.{t}.actions_per_playout", rr[0]["apf"], "actions/playout", dd, src, n=240000)
    M(f"xsim.w2.{t}.checksum", rr[0]["digest"], "digest", dd, src)
CHECK("xsim W2 ours: digest/wins/transitions = A10 reference", X2["ours"][1]["digest"] == EXP["ours_w2"]["digest"] and
      X2["ours"][1]["wins"] == EXP["ours_w2"]["wins"] and X2["ours"][1]["tx"] == EXP["ours_w2"]["transitions"], str(X2["ours"][1]))
for t, key in (("sansb-unif", "sansb_w2_unif"), ("sansb-inst", "sansb_w2_inst"), ("core-inst", "core_w2_inst"), ("core-eng", "core_w2_eng")):
    e = EXP[key]
    CHECK(f"xsim W2 {t}: checksum/wins/transitions = reference", (X2[t][1]["digest"], X2[t][1]["wins"], X2[t][1]["tx"]) == e,
          str((X2[t][1]["digest"], X2[t][1]["wins"], X2[t][1]["tx"])))
CHECK("xsim W2 sansb `sts-sim bench` checksum == xsim uniform checksum (same workload as published)",
      X2["sansb-own"][1]["digest"] == X2["sansb-unif"][1]["digest"] and X2["sansb-own"][1]["tx"] == X2["sansb-unif"][1]["tx"],
      f"{X2['sansb-own'][1]['digest']} vs {X2['sansb-unif'][1]['digest']}")
for a, b_ in (("ours", "sansb-inst"), ("ours", "sansb-own"), ("ours", "core-inst"), ("ours", "core-eng")):
    pr = [X2[a][i]["rate"] / X2[b_][i]["rate"] for i in range(1, 6)]
    M(f"xsim.w2.ratio.{a}_over_{b_}.ratio_of_medians", V(f"xsim.w2.{a}.actions_per_s") / V(f"xsim.w2.{b_}.actions_per_s"), "x",
      f"W2 per core, actions/s: {W2TAGS[a]} / {W2TAGS[b_]}.", [x2src[0]], n=5)
    MS(f"xsim.w2.ratio.{a}_over_{b_}.median_round_ratio", pr, "x", f"W2 per core, actions/s: {W2TAGS[a]} / {W2TAGS[b_]}; median over the 5 rotation rounds of the same-round ratio (paired).", [x2src[0]])
# ours W2 in-place vs clone, previous vs new (ours/w2 stage: 2 ABBA pairs x 5 passes per mode)
OW2 = {T: [last_json(f"{R}/ours/w2/{T}-{i}.out") for i in (1, 2)] for T in ("base", "new")}
for T in OW2:
    for mode in ("clone", "inplace"):
        xs = [r[mode]["tx_per_s_median"] for r in OW2[T]]
        MS(f"ours.w2.1core.{T}.{mode}.actions_per_s", xs, "actions/s",
           f"Our {'previous' if T == 'base' else 'new'} engine, W2 at A10, mode {mode} ({'clone-and-apply per step, sansb semantics' if mode == 'clone' else 'mutate in place, our search pattern'}); "
           "per run = median of 5 timed passes; 2 runs (ABBA).", [f"runs/ours/w2/{T}-{{1,2}}.out"])
    CHECK(f"ours W2 {T}: clone == inplace work and = A10 reference",
          all(r["clone"]["digest"] == r["inplace"]["digest"] == EXP["ours_w2"]["digest"] and r["clone"]["wins"] == EXP["ours_w2"]["wins"]
              for r in OW2[T]), str({r["clone"]["digest"] for r in OW2[T]}))
for mode in ("clone", "inplace"):
    M(f"ours.w2.1core.speedup_new_vs_base.{mode}", V(f"ours.w2.1core.new.{mode}.actions_per_s") / V(f"ours.w2.1core.base.{mode}.actions_per_s"),
      "x", f"W2 {mode}: new / previous median actions/s.", ["runs/ours/w2/*.out"], n=2)
M("ours.w2.1core.new.inplace_over_clone", V("ours.w2.1core.new.inplace.actions_per_s") / V("ours.w2.1core.new.clone.actions_per_s"), "x",
  "New engine: in-place / clone-and-apply actions/s on W2 (cost of copying the state every step).", ["runs/ours/w2/new-*.out"])
M("xsim.w2.ratio.ours_inplace_over_sansb-inst", V("ours.w2.1core.new.inplace.actions_per_s") / V("xsim.w2.sansb-inst.actions_per_s"), "x",
  "W2: ours in-place (our native search pattern, ours/w2 stage) / sansb instance (xsim stage). Different stages, same CPU.",
  ["runs/ours/w2/new-*.out", "runs/xsim/w2/sansb-inst-*.out"])
M("xsim.w2.ratio.ours_inplace_over_core-inst", V("ours.w2.1core.new.inplace.actions_per_s") / V("xsim.w2.core-inst.actions_per_s"), "x",
  "W2: ours in-place / sts2core instance (by-value Copy state).", ["runs/ours/w2/new-*.out", "runs/xsim/w2/core-inst-*.out"])

# their own benchmarks
ev = [json.loads(x) for x in open(os.path.join(R, "xsim/own/sansb-eval.jsonl")) if x.strip()]
evs = [x for x in ev if x.get("summary")][0]
M("xsim.own.sansb_eval_throughput.median_transitions_per_s", evs["median"], "transitions/s",
  f"sansb's own eval_throughput: random playouts on its {evs['fights_measured']} certified real-game fixtures (A9/A10 states, all "
  "characters; 0.25 s per fight, no work digest), one thread on CPU 20; median over fights.", ["runs/xsim/own/sansb-eval.jsonl"],
  n=evs["fights_measured"], spread=[g6(evs["min"]), g6(evs["max"])], p10=g6(evs["p10"]), p25=g6(evs["p25"]), p75=g6(evs["p75"]),
  p90=g6(evs["p90"]), not_admitted=evs["fights_not_admitted"])
own = {"1t": [], "24t": [], "48t": [], "1t_in_mt": []}
for kind in ("1t", "24t", "48t"):
    for i in (1, 2, 3):
        txt = open(os.path.join(R, f"xsim/own/core-{kind}-{i}.txt")).read()
        m1 = re.search(r"1 thread : \d+ rollouts, (\d+) steps in ([\d.]+)s", txt)
        mt = re.search(r"(\d+) threads: (\d+) steps in ([\d.]+)s", txt)
        r1 = int(m1[1]) / float(m1[2])
        rt = int(mt[2]) / float(mt[3])
        (own["1t"] if kind == "1t" else own["1t_in_mt"]).append(r1)
        if kind != "1t":
            own[kind].append(rt)
M("xsim.own.sts2core_bench.1t.steps_per_s", med(own["1t"]), "steps/s",
  "sts2core's own bench (28-card deck vs a 300-HP synthetic punching bag, never ends a turn by choice, no relics), 1-thread leg, "
  "taskset -c 20; 3 runs.", ["runs/xsim/own/core-1t-{1,2,3}.txt"], n=3, spread=mm(own["1t"]))
M("xsim.own.sts2core_bench.24t.steps_per_s", med(own["24t"]), "steps/s",
  "sts2core's own bench, multi-thread leg with 24 threads floating in CPUs 0-23 (not pinned 1:1); 3 runs.",
  ["runs/xsim/own/core-24t-{1,2,3}.txt"], n=3, spread=mm(own["24t"]))
M("xsim.own.sts2core_bench.48t.steps_per_s", med(own["48t"]), "steps/s",
  "sts2core's own bench, 48 threads floating in CPUs 0-47; 3 runs.", ["runs/xsim/own/core-48t-{1,2,3}.txt"], n=3, spread=mm(own["48t"]))
M("xsim.own.sts2core_bench.24t_scaling", med(own["24t"]) / med(own["1t"]), "x", "24-thread / 1-thread steps/s.", ["runs/xsim/own/core-*.txt"])
M("xsim.own.sts2core_bench.48t_scaling", med(own["48t"]) / med(own["1t"]), "x", "48-thread / 1-thread steps/s.", ["runs/xsim/own/core-*.txt"])

# cross-engine whole machine
for preset in ("1core", "1core-smt", "ccx", "phys24", "smt48"):
    for eng, S in (("sansb", S_SB), ("sts2core", S_CO)):
        M(f"xsim.scale.w1.ratio.ours_over_{eng}.{preset}", S_NEW[preset]["agg"] / S[preset]["agg"], "x",
          f"W1 aggregate actions/s at preset {preset}: ours new / {eng} instance (medians of 3 repeats each; separate runs).",
          [f"runs/ours/scale/w1-new/r*/{preset}/summary.json", f"runs/xsim/scale/w1-{eng}/r*/{preset}/summary.json"])
for eng, S in (("sansb", S_SB), ("sts2core", S_CO)):
    bp, bv = best_whole(S)
    M(f"xsim.scale.w1.ratio.ours_over_{eng}.whole_machine_best", bw / bv, "x",
      f"Best whole-machine W1 actions/s: ours ({bw_preset}) / {eng} ({bp}).", ["runs/*/scale/.../summary.json"])

# per-encounter agreement
pe = last_json(os.path.join(R, "perenc/compare.txt"))
perenc_rows = pe["rows"]
ours_pe = {}
for f in glob.glob(os.path.join(R, "perenc/*.json")):
    j = json.load(open(f))
    ours_pe[j["encounters"]] = j
for r in perenc_rows:
    o = ours_pe[r["encounter"]]
    CHECK(f"perenc {r['encounter']}: compare.txt ours win = raw json", abs(o["wins"] / o["fights"] - r["ours_win"]) < 1e-4,
          f"{o['wins']}/{o['fights']} vs {r['ours_win']}")


def agree(eng):
    d = [(r["encounter"], 100 * (r[f"{eng}_win"] - r["ours_win"]), r["two_se_pp"], r[f"{eng}_apf"] - r["ours_apf"]) for r in perenc_rows]
    within = [x for x in d if abs(x[1]) <= max(x[2], 1e-9) or (x[1] == 0 and x[2] == 0)]
    out = [x for x in d if x not in within]
    return d, within, out


for eng in ("sansb", "sts2core"):
    d, within, out = agree(eng)
    M(f"xsim.perenc.{eng}.encounters_within_2se", len(within), "of 22",
      f"Per encounter, W1 random-play win rate {eng} (instance policy, 100 seeds x 50) vs ours (200 independent seeds x 50): "
      "count with |diff| <= 2 SE (fight-level SE; understates noise on randomised rosters).", ["runs/perenc/compare.txt", "runs/perenc/*.json"],
      n=22, outside=[{"encounter": x[0], "diff_pp": round(x[1], 2), "two_se_pp": x[2]} for x in out])
    M(f"xsim.perenc.{eng}.max_abs_win_diff_pp", max(abs(x[1]) for x in d), "pp", f"Largest |{eng} - ours| win-rate gap over 22 encounters.",
      ["runs/perenc/compare.txt"], n=22)
    M(f"xsim.perenc.{eng}.max_abs_apf_diff", max(abs(x[3]) for x in d), "actions/fight",
      f"Largest |{eng} - ours| actions-per-fight gap.", ["runs/perenc/compare.txt"], n=22,
      worst=sorted([{"encounter": x[0], "diff": round(x[3], 2)} for x in d], key=lambda z: -abs(z["diff"]))[:4])
# the shipped game vs ours per encounter
zs = []
for e, (n_, w_, st) in sorted(cs_enc.items()):
    o = ours_pe[e]
    pc, po = w_ / n_, o["wins"] / o["fights"]
    se = (pc * (1 - pc) / n_ + po * (1 - po) / o["fights"]) ** 0.5
    z = (pc - po) / se if se > 0 else 0.0
    zs.append({"encounter": e, "cs_win": round(pc, 4), "ours_win": round(po, 4), "diff_pp": round(100 * (pc - po), 2), "z": round(z, 2),
               "cs_apf": round(st / n_, 2), "ours_apf": round(o["decisions"] / o["fights"], 2), "cs_n": n_, "ours_n": o["fights"]})
M("cs_vs_ours.perenc.encounters_within_2se", sum(1 for z in zs if abs(z["z"]) <= 2), "of 22",
  "Per encounter, the shipped game's random-play win rate (C5 timed fights, 384 seeds per encounter, 1 fight each) vs ours "
  "(200 seeds x 50): count with |z| <= 2 (fight-level SE).", ["csharp/runs/C5/worker-*.jsonl", "runs/perenc/*.json"], n=22,
  outside=[z for z in zs if abs(z["z"]) > 2])
M("cs_vs_ours.perenc.max_abs_z", max(abs(z["z"]) for z in zs), "z", "Largest |z| over 22 encounters (game vs ours).",
  ["csharp/runs/C5/worker-*.jsonl", "runs/perenc/*.json"], n=22)
M("cs_vs_ours.perenc.max_abs_apf_diff", max(abs(z["cs_apf"] - z["ours_apf"]) for z in zs), "actions/fight",
  "Largest |game - ours| actions per fight over 22 encounters.", ["csharp/runs/C5/worker-*.jsonl", "runs/perenc/*.json"], n=22)
M("cs_vs_ours.perenc.rows", zs, "table", "Per-encounter game vs ours rows.", ["csharp/runs/C5/worker-*.jsonl", "runs/perenc/*.json"])

# ============================================================================= 8. training
TP = os.path.join(B, "training/percore")
pc = {}
pc_md5 = set()
for arm in ("base", "new", "pgo"):
    pc[arm] = {}
    for i in (1, 2, 3):
        pf = perf(f"{TP}/{arm}-n6-{i}.perf")
        wall = float(re.search(r"wall_s ([\d.]+)", open(f"{TP}/{arm}-n6-{i}.log").read())[1])
        pc[arm][i] = {"tc": pf["task-clock"] / 1000, "ins": pf.get("instructions:u"), "wall": wall}
        pc_md5.add(md5(f"{TP}/{arm}-n6-{i}.txt"))
CHECK("training per-core: the 9 runs (3 arms x 3) produced byte-identical game records", len(pc_md5) == 1, str(pc_md5))
lab = {"base": "previous engine, plain build (what training ran before PR #761)", "new": "new engine, plain build",
       "pgo": "new engine, PGO build (what training runs now)"}
for arm in pc:
    tcs = [pc[arm][i]["tc"] for i in (1, 2, 3)]
    d = ("Training collection, ONE worker pinned to CPU 20: ppo_collect.py plays 6 whole Ironclad runs (seeds 700-705, "
         f"ascensions cycling 0/3/6/10, PPO champion + MCTS 250/1000 sims) with the {lab[arm]}; perf task-clock; 3 runs.")
    MS(f"training.percore.{arm}.task_clock_s", tcs, "CPU s per 6 runs", d, [f"training/percore/{arm}-n6-{{1,2,3}}.perf"])
    MS(f"training.percore.{arm}.cpu_s_per_run", [x / 6 for x in tcs], "CPU s per run", d, [f"training/percore/{arm}-n6-{{1,2,3}}.perf"])
    MS(f"training.percore.{arm}.runs_per_hour_per_core", [6 * 3600 / x for x in tcs], "runs/hour", d + " 6 x 3600 / task-clock.",
       [f"training/percore/{arm}-n6-{{1,2,3}}.perf"])
    MS(f"training.percore.{arm}.instructions", [pc[arm][i]["ins"] for i in (1, 2, 3)], "instructions:u", d,
       [f"training/percore/{arm}-n6-{{1,2,3}}.perf"])
for a, b_ in (("base", "new"), ("base", "pgo"), ("new", "pgo")):
    M(f"training.percore.speedup.{b_}_vs_{a}.ratio_of_medians", V(f"training.percore.{a}.task_clock_s") / V(f"training.percore.{b_}.task_clock_s"),
      "x", f"Per-core collection: {a} task-clock / {b_} task-clock (medians).", ["training/percore/*-n6-*.perf"], n=3)
    MS(f"training.percore.speedup.{b_}_vs_{a}.median_rep_ratio", [pc[a][i]["tc"] / pc[b_][i]["tc"] for i in (1, 2, 3)], "x",
       f"Per-core collection {a} / {b_} task-clock, per repetition (rep 1 order base,new,pgo; rep 2 pgo,new,base; rep 3 base,new,pgo), median.", ["training/percore/*-n6-*.perf"])
M("training.percore.instructions_ratio_base_over_pgo", V("training.percore.base.instructions") / V("training.percore.pgo.instructions"), "x",
  "Previous / PGO user instructions for the same 6 runs.", ["training/percore/*-n6-*.perf"])

TI = [json.loads(x) for x in open(os.path.join(B, "training/results/train_iter.jsonl")) if x.strip()]
cfgs = {"base-plain": [], "new-plain": [], "new-pgo": []}
for r in TI:
    c = next(k for k in cfgs if r["label"].startswith(k))
    cfgs[c].append(r)
tsrc = ["training/results/train_iter.jsonl", "training/runs/<cfg>-r{1,2}/main.stats.jsonl", "training/results/measure.log"]
tlab = {"base-plain": "previous engine d3ea66a51, plain build (what training ran before PR #761)",
        "new-plain": "new engine 6d1918237, plain build", "new-pgo": "new engine 6d1918237, PGO build (what training runs now)"}
TR = {}
for c, rows in cfgs.items():
    its = [r["iterations"][0] for r in rows]
    d = ("Trainer iteration 0, deployed recipe: Ironclad, 960 games, MCTS 250 sims (1,000 at bosses), A0-A10 uniform, act-1 roll, "
         f"46 worker processes on this host, PPO update on one RTX 3090; init = the Ironclad champion; {tlab[c]}; 2 runs (ABBA).")
    src = [s.replace("<cfg>", c) for s in tsrc]
    TR[c] = {
        "collect": MS(f"training.full.{c}.collect_s", [i["collect_s"] for i in its], "s", d + " Collection wall time (pool spawn, all games, teardown).", src),
        "train": MS(f"training.full.{c}.train_s", [i["train_s"] for i in its], "s", d + " PPO update wall time (GPU).", src),
        "iter": MS(f"training.full.{c}.iter_s", [i["iter_s"] for i in its], "s", d + " Whole iteration.", src),
    }
    MS(f"training.full.{c}.games_per_hour", [960 / i["collect_s"] * 3600 for i in its], "games/hour", d + " 960 / collect_s x 3600.", src)
    MS(f"training.full.{c}.games_per_hour_per_core", [960 / i["collect_s"] * 3600 / 24 for i in its], "games/hour per core",
       d + " Whole-machine collection rate / 24 physical cores.", src)
    MS(f"training.full.{c}.cpu_s_per_game", [(r["cpu_user_s"] + r["cpu_sys_s"]) / 960 for r in rows], "CPU s/game",
       d + " (user + sys CPU of the whole process tree, incl. the harness samplers) / 960.", src)
    MS(f"training.full.{c}.util_collect", [i["util_collect"] for i in its], "fraction",
       d + " Whole-machine busy fraction during collection: ~0.5 = collection is tail-bound (stragglers).", src)
    MS(f"training.full.{c}.gpu_util_update_pct", [i["gpu_update_window"]["util_mean"] for i in its], "%", d + " Mean GPU util during the update.", src)
    MS(f"training.full.{c}.gpu_util_collect_pct", [i["gpu_collect_window"]["util_mean"] for i in its], "%", d + " Mean GPU util during collection.", src)
    MS(f"training.full.{c}.campaign_150_iters_h", [150 * i["iter_s"] / 3600 for i in its], "hours",
       d + " Projection: 150 x iter_s (one campaign leg); assumes every iteration costs what iteration 0 does.", src)
    MS(f"training.full.{c}.wall_s", [r["wall_s"] for r in rows], "s", d + " Whole trainer process wall time.", src)
    M(f"training.full.{c}.errors", [i["errors"] for i in its], "games per run", d + " Games that errored (trainer's count).", src)
    M(f"training.full.{c}.n_runs", [i["n_runs"] for i in its], "games per run", d + " Games that produced rows.", src)
    M(f"training.full.{c}.fingerprint", sorted({i["fingerprint"] for i in its}), "digest", d + " Collection fingerprint (iteration 0).", src)
    M(f"training.full.{c}.n_transitions", its[0]["n_transitions"], "transitions", d, src)
    CHECK(f"training full {c}: ok, extension verified (so_verified), expected .so mapped by every worker",
          all(r["ok"] and r["so_verified"] for r in rows), str([(r["label"], r["ok"], r["so_verified"]) for r in rows]))
fp = {c: {i["fingerprint"] for r in rows for i in r["iterations"]} for c, rows in cfgs.items()}
CHECK("training full: new-plain and new-pgo play the identical 960 games (same fingerprint in all 4 runs)",
      fp["new-plain"] == fp["new-pgo"] and len(fp["new-plain"]) == 1, str(fp))
CHECK("training full: the two previous-engine runs agree with each other", len(fp["base-plain"]) == 1, str(fp["base-plain"]))
base_err = [i["errors"] for r in cfgs["base-plain"] for i in r["iterations"]]
st = {c: [json.loads(open(os.path.join(B, f"training/runs/{c}-r{k}/main.stats.jsonl")).readline()) for k in (1, 2)] for c in cfgs}
diffkeys = sorted(k for k in st["base-plain"][0] if k.startswith(("n_runs/", "win_rate/")) and st["base-plain"][0][k] != st["new-plain"][0].get(k))
a4 = (st["base-plain"][0]["n_runs/A4"], st["new-plain"][0]["n_runs/A4"],
      round(st["base-plain"][0]["win_rate/A4"] * st["base-plain"][0]["n_runs/A4"]), round(st["new-plain"][0]["win_rate/A4"] * st["new-plain"][0]["n_runs/A4"]))
CHECK("training full: previous engine fingerprint differs from new ONLY through its 1 errored game",
      base_err == [1, 1] and diffkeys == ["n_runs/A4", "win_rate/A4"] and a4[2] == a4[3],
      f"previous errors={base_err}; per-ascension keys that differ: {diffkeys}; A4 runs {a4[0]} vs {a4[1]}, A4 wins {a4[2]} vs {a4[3]}")
for a, b_ in (("base-plain", "new-plain"), ("base-plain", "new-pgo"), ("new-plain", "new-pgo")):
    for m_ in ("collect_s", "iter_s"):
        M(f"training.full.speedup.{b_}_vs_{a}.{m_}", V(f"training.full.{a}.{m_}") / V(f"training.full.{b_}.{m_}"), "x",
          f"Full iteration: {a} {m_} / {b_} {m_} (medians of 2).", tsrc, n=2)
    rr = []
    for k in (1, 2):
        ra = [r for r in cfgs[a] if r["label"].endswith(f"r{k}")][0]["iterations"][0]["iter_s"]
        rb = [r for r in cfgs[b_] if r["label"].endswith(f"r{k}")][0]["iterations"][0]["iter_s"]
        rr.append(ra / rb)
    MS(f"training.full.speedup.{b_}_vs_{a}.iter_s.per_rep", rr, "x", f"Full iteration {a} / {b_} iter_s, per repetition (r1 order base,new-plain,new-pgo; r2 reversed).", tsrc)
M("training.full.campaign_hours_saved_pgo_vs_previous", V("training.full.base-plain.campaign_150_iters_h") - V("training.full.new-pgo.campaign_150_iters_h"),
  "hours", "Projected 150-iteration campaign leg: previous minus PGO.", tsrc)
M("training.historical_anchor.sept_leg_collect_median_s", 500.5, "s",
  "Champion's own leg ironclad-plr-c1 (Sept 1-2 2026 engine, same 960 x 250/1000 recipe + PLR 0.5): collect median 500.5 s, "
  "iter median 534 s, 23.9 h for 150 iterations; its first row (it400) had util_collect 0.961 (prep facts, main.stats.jsonl in the main checkout).",
  ["prep_result.json (training facts_markdown)"])
M("training.previous_caveat", None, "text",
  "PLACEHOLDER for the diag agent: why the previous engine's two runs each had errors=1 (959/960 games) and what that does to the "
  "previous-engine timing; also whether the previous-engine collect time (~1179 s) vs the Sept historical leg (~500 s) needs a note.",
  [])

for c in cfgs:
    M(f"training.whole_vs_one_core.{c}", V(f"training.full.{c}.games_per_hour") / V(f"training.percore.{c.split('-')[0] if c != 'new-pgo' else 'pgo'}.runs_per_hour_per_core"),
      "x", f"INDICATIVE ({c}): whole-machine collection games/hour (46 workers, 960 games, A0-A10) / one pinned worker's runs/hour "
      "(6 runs, seeds 700-705 at A0/3/6/10). Different games, so only indicative; low because collection is tail-bound "
      "(util_collect ~0.5) and 46 workers share 24 cores.", ["training/results/train_iter.jsonl", "training/percore/*-n6-*.perf"])

# ============================================================================= per core vs whole machine, at a glance
GLANCE = []


def glance(engine, one, one_def, smt, smt_def, whole, whole_def, extra="", one_same_run=None):
    base1 = one_same_run or one
    row = {"engine": engine, "one_core_actions_per_s": g6(one), "one_core_definition": one_def,
           "one_core_both_threads_actions_per_s": g6(smt) if smt else None, "both_threads_definition": smt_def,
           "whole_machine_actions_per_s": g6(whole), "whole_machine_definition": whole_def,
           "whole_machine_per_core_actions_per_s": g6(whole / 24),
           "whole_over_one_core": g6(whole / base1),
           "whole_over_one_core_definition": ("whole machine / the 1core baseline of the SAME scale runs (" + fmt(base1) + " actions/s)")
           if one_same_run else "whole machine / the one-core figure in this row",
           "whole_over_saturated_core": g6(whole / smt) if smt else None, "note": extra}
    GLANCE.append(row)


glance("ours (new)", o1, "W1 stage, 1 process on CPU 20, sibling idle (7 runs)", s1smt, "1core-smt scale preset",
       bw, f"{bw_preset} scale preset", "W1, Burning Blood live", one_same_run=S_NEW["1core"]["agg"])
glance("ours (previous)", V("ours.w1.1core.base.actions_per_s"), "W1 stage, 1 process on CPU 20 (7 runs)", S_BASE["1core-smt"]["agg"],
       "1core-smt scale preset", best_whole(S_BASE)[1], f"{best_whole(S_BASE)[0]} scale preset", "W1", one_same_run=S_BASE["1core"]["agg"])
glance("sansb/sts2-sim", V("xsim.w1.sansb-inst.actions_per_s"), "xsim W1 stage, CPU 20 (7 rounds), instance policy",
       S_SB["1core-smt"]["agg"], "1core-smt scale preset", best_whole(S_SB)[1], f"{best_whole(S_SB)[0]} scale preset",
       "W1 instance policy; Burning Blood inert in sansb; SMT HURTS this engine", one_same_run=S_SB["1core"]["agg"])
glance("sts2core", V("xsim.w1.core-inst.actions_per_s"), "xsim W1 stage, CPU 20 (7 rounds), instance policy",
       S_CO["1core-smt"]["agg"], "1core-smt scale preset", best_whole(S_CO)[1], f"{best_whole(S_CO)[0]} scale preset",
       "W1 instance policy", one_same_run=S_CO["1core"]["agg"])
glance("shipped game (C#, headless)", Am["decisions_per_s"], "cell A: one warm game process + driver on CPUs 20,44 (incl. reset; "
       f"play-only {At['decisions_per_s_playonly']})", csB, f"best B cell ({bestB}: {cs_cells[bestB]['workers']} processes on CPUs 20,44)",
       csC, f"best C cell ({bestC}: {cs_cells[bestC]['workers']} processes)",
       f"one process is {100 * (1 - At['cpu_util_during_play']):.0f}% asleep (Thread.Sleep(1) polling); the saturated figures are the fair per-core / whole-machine numbers")
for g in GLANCE:
    key = re.sub(r"[^a-z0-9]+", "_", g["engine"].lower()).strip("_")
    M(f"glance.{key}", g, "row", "Per core vs whole machine, one row per engine (actions/s).", ["(derived from the metrics above)"])

# ============================================================================= context facts (cited, not measured here)
CONTEXT = {
    "note": "Facts gathered by the prep agents with file:line citations in prep_result.json (facts_markdown per engine) and the "
            "coordinator's task statement; NOT computed by this script. Re-check wording against those sources.",
    "ours": {
        "game_build": "v0.107.1", "scope": "whole single-player runs, all 5 characters, A0-A10",
        "rng": "hand-ported .NET System.Random; all 12 RunRngSet + 3 PlayerRngSet streams",
        "validation": "lockstep against the game's own sts2.dll run headless; Ironclad full fuzz tier 6,104/6,104 clean (2026-06-30); "
                      "whole-run parity 1,232/1,250 clean (2026-07-07, A0/2/5/8/10, 5 characters); no checksummed real-game captures "
                      "yet (#739); open suspected bugs #741 #742 #759 #760 #762",
        "clairvoyance": "deployed searches (run AI, browser hint, real-game advisor) use BeliefDraw since #246 (not clairvoyant); the "
                        "search library's MctsConfig default is still Clairvoyant",
        "content_vs_csharp": "facts/content_diff.out: cards 580 engine ids vs 578 C# classes (576 matched by name), relics 297/298, "
                             "potions 63/64, powers 270/260, monsters 121/121, events 69/68, enchantments 23/23",
        "do_not_quote": "the stale '68-76%' combat-parity figure; 'zero divergences on all five characters' without a date",
    },
    "sansb_sts2_sim": {
        "commit": "c61336cf (2026-10-12)", "license": "MIT", "game_build": "v0.111.0", "scope": "combat only, all 5 characters",
        "rng": "reproduces the game's RNG (all 9 combat streams)",
        "validation": "lockstep against real-game checksums: 1,196/1,206 personal census fights; 594 certified published fixtures "
                      "(597 pairs, 3 refusal); 4,205 certified A9/A10 site uploads",
        "clairvoyance": "its exact solver and site reviews search the TRUE state incl. draw order and RNG (clairvoyant); a draw-blind "
                        "lookahead exists only in examples",
        "completeness": "unported content refused, never approximated; Burning Blood inert (combat-only scope)",
        "engine_design": "deduplicated legal actions; a new state allocated per step (clone-and-apply); no PyO3; WASM yes; "
                         "built with rustc 1.97.1, thin LTO, panic=abort",
        "own_perf_claim": "PERF.md: 1,049,000 transitions/s on an Apple M5 Max (bench fight, best of 5); eval median 302k/s",
    },
    "lain_wood_sts2core": {
        "commit": "3b2d969d (2026-09-25)", "license": "MIT", "game_build": "v0.107.1",
        "scope": "combat only; effectively Ironclad only (derived from its 79 traces and tables, not stated by its docs)",
        "completeness": "117 cards incl. a placeholder, covering 112 of the 128 its saves discovered; 105 enemies incl. 3 synthetic + "
                        "unknown; 103 of 118 relics modelled; 5 of 22 enchantments; Queen boss missing; A8+ values unverified; a "
                        "Cubex Construct content error verified against the C#",
        "rng": "does NOT reproduce the game's RNG (own splitmix streams, by design)",
        "validation": "one-step predictions on 79 own recordings: 1,565 match / 1 mismatch / 9 content missing / 177 skipped; enemy "
                      "next move 85% (353/415)",
        "clairvoyance": "PARTLY: a patched STS2MCP exports the true draw-pile order, used as known until the next reshuffle",
        "authors_caveat": "README: only paired differences from its advisor are trustworthy, not absolute levels",
        "engine_design": "plain-old-data 3,880-byte Copy state stepped by value; zero dependencies; deduplicated legal actions",
        "own_perf_claim": "authors: 3.56-3.61M steps/s on 1 thread, 54.9-56.5M on 32 threads (own machine, 'not a baseline')",
    },
}

# ============================================================================= flags
FLAGS.extend([
    "No FLAGS.env file exists under runs/ or csharp/runs; no 'NOT quiet' / 'SKIP' / FAILED line in runs/ours, runs/xsim, runs/wasm or "
    "csharp/runs/measure.log; every C# result line has forced=0.",
    "C# whole-machine cells C1-C6 were gated on whole-host busy <= 3% and load1 < 1.5 only (CPU_BUSY_MAX=100); the per-CPU gate "
    "never passed because of ambient per-CPU noise from other sessions. Max per-CPU busy at the gate was " +
    ", ".join(f"C{i + 1} {g['cpu_max']}%" for i, g in enumerate(g for g in gate_rows if g["stage"] == "cs:0-47")) +
    ". The Rust whole-machine scale runs used the same whole-host gate (cpus='-').",
    "The first C# block was terminated (runs/all.log: block cs rc=143 at 14:08:54Z) while C1 waited for the strict per-CPU gate; "
    "A0, A, B2-B16 from that block are used; C1-C5 were re-run 15:33-16:22Z after training; C6 (384 processes) was added ad hoc "
    "16:23-16:32Z (csharp/c_cells.log, csharp/c6.log).",
    "Training measure.log has " + str(len(forced_train)) + " 'NOT QUIET ... FORCE=1' lines at 11:12-11:15 local (09:12-09:15Z): "
    "smoke runs (16 games) and the 1-seed per-core smoke (percore/*-n1-*), NOT used for any published number.",
    "runs/wasm/summary.txt and summary.tsv are stale (written 09:58Z before the WASM runs; they say 'no runs'); "
    "the WASM numbers here are recomputed from runs/wasm/{node,chrome}/*.txt. runs/wasm_summary.txt was never written.",
    "Training: the previous engine's two full-iteration runs each report errors=1 (959 of 960 games, one A4 game); its fingerprint "
    "therefore differs from the new engine's. See training.previous_caveat (diag agent).",
    "Training: measured previous-engine collect (~1179 s) is 2.4x the Sept-2026 champion leg's median (500.5 s, older engine, PLR 0.5, "
    "its first row had util_collect 0.96 vs ~0.50 here); new-plain (~536 s) is close to that historical value. Not explained by this analysis.",
    "Training speed-ups contain an algorithmic part: the new code skips the search when a decision has exactly one legal move "
    "(critic report); they are engine + harness speed-ups, not pure engine speed.",
    "Our W1/xsim perf counters (instructions, cycles, IPC, GHz) cover the whole process including the untimed start-state build.",
    "Our --relics none W1 row is ~7.6% SLOWER than the Burning Blood row for identical work (3.1% more instructions per action, "
    "lower IPC), so the earlier assumption 'the BB-vs-none difference is relic-dispatch cost' does not hold; the sansb like-for-like "
    "ratio is therefore quoted both ways (3.1x without, 3.4x with our relic).",
    "C# throughput cells never saturate the CPUs (B16 89% busy, C5 67%, C6 86% with LOWER throughput than C5): the harness polls "
    "with Thread.Sleep(1), so 'best measured' is the saturated figure.",
    "sansb is built with rustc 1.97.1 (its pin), thin LTO, panic=abort; ours and sts2core with rustc 1.96.0 fat LTO; none use "
    "target-cpu or PGO (our training PGO build is the only exception and is labelled).",
    "perenc_compare's 2 SE is fight-level and understates seed-level noise on randomised rosters (harnessfix report): "
    "Slithering Strangler -2.5 pp has block-to-block sd 0.7 pp.",
])
FLAGS.extend("Scale: " + s for s in scale_flags)

# ============================================================================= headline
sb_ratio = V("xsim.w1.ratio.ours-norelic_over_sansb-inst.median_round_ratio")
co_ratio = V("xsim.w1.ratio.ours_over_core-inst.median_round_ratio")
HEAD = [
    {"id": "hw", "keys": ["hw.cpu", "hw.gpus"],
     "wording": f"Measured on one AMD EPYC 7443P (24 cores / 48 threads, Zen 3, up to {V('hw.clock_boost_max_ghz'):.2f} GHz) with 256 GiB "
                "DDR4-3200 and 4x RTX 3090 (training uses one), Ubuntu 24.04, Rust 1.96, .NET 9."},
    {"id": "ours_one_core", "keys": ["ours.w1.1core.new.actions_per_s", "ours.w1.1core.new.fights_per_s"],
     "wording": f"One core: {fmt(V('ours.w1.1core.new.fights_per_s'))} random-play fights/s = {fmt(V('ours.w1.1core.new.actions_per_s'))} "
                f"actions/s ({V('ours.w1.1core.new.ns_per_action'):.0f} ns per action), Ironclad A0 vs all 22 Act-1 encounters."},
    {"id": "new_vs_previous", "keys": ["ours.w1.1core.speedup_new_vs_base.median_pair_ratio"],
     "wording": f"{V('ours.w1.1core.speedup_new_vs_base.median_pair_ratio'):.2f}x the previous engine (12 Oct) on the same fights "
                f"(identical results, 7 ABBA pairs)."},
    {"id": "ours_whole_machine", "keys": ["ours.scale.w1.new.whole_machine_best.actions_per_s"],
     "wording": f"Whole machine ({bw_preset}, {THREADS[bw_preset]} processes): {fmt(S_NEW[bw_preset]['agg2'])} fights/s = "
                f"{V('ours.scale.w1.new.whole_machine_best.actions_per_s') / 1e6:.1f} M actions/s, "
                f"{V('ours.scale.w1.new.whole_machine_best.vs_1core'):.1f}x one core "
                f"(phys24 efficiency {pct(S_NEW['phys24']['eff'], 0)}; SMT adds {100 * (V('ours.scale.w1.new.smt_gain.smt48_over_phys24') - 1):.0f}%)."},
    {"id": "cs_saturated_core", "keys": ["ratio.saturated_one_core.ours_1thread", "ratio.saturated_one_core.ours_both_threads"],
     "wording": f"vs the shipped game run headless, per core: one core of ours does {fmt(V('ratio.saturated_one_core.ours_1thread'), 0)}x the "
                f"actions of the game's best on one core ({cs_cells[bestB]['workers']} game processes sharing it: {fmt(csB, 0)} actions/s); "
                f"{fmt(V('ratio.saturated_one_core.ours_both_threads'), 0)}x with both hardware threads on each side."},
    {"id": "cs_whole_machine", "keys": ["ratio.whole_machine"],
     "wording": f"vs the shipped game, whole machine: {bw / 1e6:.1f} M vs {fmt(csC, 0)} actions/s = "
                f"{fmt(V('ratio.whole_machine'), 0)}x (game: best of 24-384 processes, cell {bestC})."},
    {"id": "cs_cpu_time", "keys": ["ratio.cpu_time.process", "ratio.cpu_time.gamethread"],
     "wording": f"CPU time per action: {At['cpu_ms_per_decision']:.2f} ms in one warm game process ({At['gamethread_cpu_ms_per_decision']:.2f} ms on its "
                f"game thread) vs {o1_us * 1000:.0f} ns = {fmt(V('ratio.cpu_time.process'), 0)}x ({fmt(V('ratio.cpu_time.gamethread'), 0)}x "
                "game thread only); this includes the harness's state/JSON work, and across all tested process layouts the game-thread "
                f"ratio ranges {fmt(V('ratio.cpu_time.gamethread_range_cells')[0], 0)}-{fmt(V('ratio.cpu_time.gamethread_range_cells')[1], 0)}x."},
    {"id": "retire_23000", "keys": ["ratio.one_process_wall.cold_A0", "ratio.one_process_wall.warm_A"],
     "wording": f"The old '23,000x' compared wall time of ONE cold game process per fight ({A0s['ms_per_decision']:.1f} ms/action, now "
                f"{fmt(V('ratio.one_process_wall.cold_A0'), 0)}x) and is mostly the harness's 1 ms polling sleeps; warm it is "
                f"{fmt(V('ratio.one_process_wall.warm_A'), 0)}x. Use the saturated and CPU-time ratios instead."},
    {"id": "xsim_sansb", "keys": ["xsim.w1.ratio.ours-norelic_over_sansb-inst.median_round_ratio"],
     "wording": f"vs sansb/sts2-sim (v0.111.0, combat only, certified against real-game checksums; its solver and reviews are clairvoyant): "
                f"ours is {sb_ratio:.1f}x faster per core on the same random-play workload with no live relic on either side "
                f"({fmt(V('xsim.w1.ours-norelic.actions_per_s'))} vs {fmt(V('xsim.w1.sansb-inst.actions_per_s'))} actions/s; "
                f"{V('xsim.w1.ratio.ours_over_sansb-inst.median_round_ratio'):.1f}x with our Burning Blood live)."},
    {"id": "xsim_sts2core", "keys": ["xsim.w1.ratio.ours_over_core-inst.median_round_ratio"],
     "wording": f"vs lain-wood/sts2core (v0.107.1, combat only, effectively Ironclad-only, partial content, own RNG instead of the game's, "
                f"partly clairvoyant solver): sts2core is {1 / co_ratio:.1f}x FASTER per core than ours "
                f"({fmt(V('xsim.w1.core-inst.actions_per_s'))} vs {fmt(V('xsim.w1.ours.actions_per_s'))} actions/s), stepping a 3,880-byte "
                "plain-old-data state by value."},
    {"id": "browser_hint", "keys": ["search.hint.chrome.new.sims1000.ms", "search.hint.native.new.sims1000.ms"],
     "wording": f"Browser hint search, 1,000 sims: {V('search.hint.chrome.new.sims1000.ms'):.1f} ms in Chromium (WASM) vs "
                f"{V('search.hint.native.new.sims1000.ms'):.1f} ms native "
                f"({V('search.hint.chrome_over_native.new.sims1000'):.2f}x); {V('search.hint.chrome.speedup_new_vs_base.sims1000.median_pair_ratio'):.2f}x "
                "faster than the previous engine."},
    {"id": "training", "keys": ["training.full.speedup.new-pgo_vs_base-plain.iter_s", "training.full.new-pgo.campaign_150_iters_h"],
     "wording": f"Training: one 960-game iteration takes {V('training.full.new-pgo.iter_s'):.0f} s instead of {V('training.full.base-plain.iter_s'):.0f} s "
                f"({V('training.full.speedup.new-pgo_vs_base-plain.iter_s'):.2f}x; collection {V('training.full.speedup.new-pgo_vs_base-plain.collect_s'):.2f}x, "
                f"one core {V('training.percore.speedup.pgo_vs_base.ratio_of_medians'):.2f}x); a 150-iteration leg projects to "
                f"{V('training.full.new-pgo.campaign_150_iters_h'):.0f} h instead of {V('training.full.base-plain.campaign_150_iters_h'):.0f} h "
                "(see the previous-engine error caveat)."},
]

# ============================================================================= RESULTS.md
p("# Benchmark results, 2026-10-14 window")
p()
p(f"Generated by `results/analyze.py` from the raw data under the bench2 directory. Medians over runs; [min-max] over runs. "
  f"Previous engine = d3ea66a51 (12 Oct 2026), new = 6d1918237 (PR #761). {sum(c['pass'] for c in CHECKS)}/{len(CHECKS)} identity checks pass.")

h("Headline (suggested wording for the main page)")
for x in HEAD:
    p(f"- **{x['id']}**: {x['wording']}")

h("1. Hardware and software")
table(["Item", "Value"], [
    ["System", V("hw.system")], ["CPU", V("hw.cpu")], ["Cores", V("hw.sockets_cores_threads")],
    ["Clocks", f"boost up to {V('hw.clock_boost_max_ghz'):.3f} GHz; top P-state {V('hw.clock_top_pstate_ghz'):.2f} GHz; {V('hw.governor')}"],
    ["Caches", V("hw.caches")], ["L3 domains", " / ".join(V("hw.l3_domains"))], ["RAM", V("hw.ram")],
    ["GPUs", "4x " + V("hw.gpus")[0]], ["OS", V("hw.os")], ["Rust", V("sw.rust")], [".NET", V("sw.dotnet")],
    ["Python / torch", f"{V('sw.python')}; {V('sw.pytorch')}"], ["Node / Chromium", f"{V('sw.node')}; {V('sw.chromium')}"],
    ["perf", V("sw.perf")],
])
p(f"Window {V('host.window_utc')}. {V('host.quiet_gates_passed')} quiet-host gate passes: load1 median {V('host.gate_load1'):.2f} "
  f"[{METRICS['host.gate_load1']['spread'][0]}-{METRICS['host.gate_load1']['spread'][1]}], whole-host busy median {V('host.gate_busy_pct')}% "
  f"[{METRICS['host.gate_busy_pct']['spread'][0]}-{METRICS['host.gate_busy_pct']['spread'][1]}]. Block-boundary load1 "
  f"[{METRICS['host.block_boundary_load1']['spread'][0]}-{METRICS['host.block_boundary_load1']['spread'][1]}] (includes our own "
  "whole-machine runs' residual load). MemAvailable ~" + str(V("host.mem_avail_gb")) + " GB. Training gates (busy only): busy "
  f"{METRICS['host.training_gate_busy_pct']['spread']}%, load1 {METRICS['host.training_gate_busy_pct']['load1_spread']} "
  f"(n={METRICS['host.training_gate_busy_pct']['n']}).")

h("Per core vs whole machine, at a glance (actions/s)")
table(["Engine", "one core (1 thread / 1 process)", "one core, both threads (saturated)", "whole machine (best)", "whole machine / 24",
       "whole / one core", "whole / saturated core", "note"],
      [[g["engine"], fmt(g["one_core_actions_per_s"]), fmt(g["one_core_both_threads_actions_per_s"]), fmt(g["whole_machine_actions_per_s"]),
        fmt(g["whole_machine_per_core_actions_per_s"]), fx(g["whole_over_one_core"], 1), fx(g["whole_over_saturated_core"], 1), g["note"]]
       for g in GLANCE])
p("Definitions per row are in results.json (glance.*). Rust rows: 'whole / one core' uses the 1core baseline of the same scale runs. "
  "For the shipped game, 'one core' is one game process + driver on both threads of a core (incl. the per-fight reset); 'saturated' "
  "is the best of 2-16 such processes on that core.")

h("2. Our engine, one core (W1, CPU 20, sibling idle)")
rows = []
for T, labx in (("base", "previous d3ea66a51"), ("new", "new 6d1918237")):
    k = f"ours.w1.1core.{T}"
    rows.append([labx, f"{fmt(V(k + '.fights_per_s'))} [{fmt(METRICS[k + '.fights_per_s']['spread'][0])}-{fmt(METRICS[k + '.fights_per_s']['spread'][1])}]",
                 f"{fmt(V(k + '.actions_per_s'))} [{fmt(METRICS[k + '.actions_per_s']['spread'][0])}-{fmt(METRICS[k + '.actions_per_s']['spread'][1])}]",
                 f"{V(k + '.us_per_action'):.3f}", fmt(V(k + ".perf.instructions_per_action"), 0), fmt(V(k + ".perf.cycles_per_action"), 0),
                 f"{V(k + '.perf.ipc'):.2f}", f"{V(k + '.perf.ghz'):.2f}"])
table(["Engine", "fights/s", "actions/s", "µs/action", "instr/action", "cycles/action", "IPC", "GHz"], rows)
p(f"Speed-up new/previous: ratio of medians {fx(V('ours.w1.1core.speedup_new_vs_base.ratio_of_medians'), 3)}, median of 7 ABBA pair ratios "
  f"{fx(V('ours.w1.1core.speedup_new_vs_base.median_pair_ratio'), 3)} [{METRICS['ours.w1.1core.speedup_new_vs_base.median_pair_ratio']['spread'][0]:.3f}-"
  f"{METRICS['ours.w1.1core.speedup_new_vs_base.median_pair_ratio']['spread'][1]:.3f}]; previous executes "
  f"{fx(V('ours.w1.1core.instructions_ratio_base_over_new'))} the instructions per action. Same work on both: {V('ours.w1.workload.fights'):,} fights, "
  f"win rate {pct(V('ours.w1.workload.win_rate'), 2)}, {V('ours.w1.workload.actions_per_fight'):.2f} actions/fight, digest {V('ours.w1.workload.digest')}.")
p(f"`--relics none` (cross-engine rotation, 7 rounds): {fmt(V('ours.w1.1core.new.relics_none.actions_per_s'))} actions/s vs Burning Blood "
  f"{fmt(V('ours.w1.1core.new.burning_blood.actions_per_s_xsim'))}; none/BB per round {fx(V('ours.w1.relics_none_over_burning_blood.median_pair_ratio'), 3)}, "
  f"instructions per action none/BB {fx(V('ours.w1.relics_none_over_burning_blood.instructions_ratio'), 3)}. Identical digest (the relic does "
  "not change random play), so the no-relic run is SLOWER for the same work: the live relic costs nothing measurable; cause not investigated.")

h("3. Scaling (scale_runner, 3 repeats, medians)")
for name, S, unit2 in (("Our new engine, W1", S_NEW, "fights/s"), ("Our previous engine, W1", S_BASE, "fights/s"),
                       ("Our new engine, W2 (clone, A10)", S_W2, "playouts/s"), ("sansb, W1 instance", S_SB, "fights/s"),
                       ("sts2core, W1 instance", S_CO, "fights/s")):
    p(f"**{name}**")
    p()
    rows = []
    for preset in PHYS:
        if preset not in S:
            continue
        x = S[preset]
        rows.append([preset, PRESET_CPUS[preset], x["n"], fmt(x["agg"]), fmt(x["agg2"]), fmt(x["agg"] / PHYS[preset]),
                     fmt(x["ppm"]), f"{x['eff']:.3f}", f"{x['ghz']:.2f}", f"{x['ipc']:.2f}", f"{x['ov']:.3f}", f"{x['spread']:.1f}%"])
    table(["Preset", "CPUs", "procs", "actions/s (sum)", unit2, "per phys. core", "per process", "eff. vs 1core", "GHz", "IPC",
           "min overlap", "max proc spread"], rows)
for pref, lab2 in (("ours.scale.w1.new", "ours new"), ("ours.scale.w1.base", "ours previous"), ("xsim.scale.w1.sansb", "sansb"),
                   ("xsim.scale.w1.sts2core", "sts2core"), ("ours.scale.w2.new", "ours new W2")):
    parts = []
    for num, den in (("1core-smt", "1core"), ("ccx-smt", "ccx"), ("smt48", "phys24")):
        k = f"{pref}.smt_gain.{num}_over_{den}"
        if k in METRICS:
            parts.append(f"{num}/{den} {fx(V(k), 3)}")
    p(f"- SMT gain, {lab2}: " + ", ".join(parts) + f"; best whole-machine preset {V(pref + '.whole_machine_best.preset')} "
      f"= {fmt(V(pref + '.whole_machine_best.actions_per_s'))} actions/s ({fx(V(pref + '.whole_machine_best.vs_1core'), 1)} its 1core).")
p()
p("New vs previous at scale (ratio of medians; engines ran ~45 min apart): " + ", ".join(
    f"{pr_} {fx(V(f'ours.scale.w1.speedup_new_vs_base.{pr_}.ratio_of_medians'), 3)}" for pr_ in ("1core", "1core-smt", "phys24", "smt48")))
p()
p("**Hint search, independent searches (new engine)**")
p()
rows = []
for preset in ("1core", "ccx", "phys24", "smt48"):
    rows.append([preset] + [f"{fmt(hint_scale[preset][b][0])} ({hint_scale[preset][b][1]:.2f} ms, eff {hint_scale[preset][b][2]:.2f})" for b in HB])
table(["Preset"] + [f"{b} sims: searches/s (median ms, eff.)" for b in HB], rows)

h("4. Search")
rows = []
for b in HB:
    rows.append([b] + [fmt(V(f"search.hint.{host}.{T}.sims{b}.ms"), 2) for host in ("native", "node", "chrome") for T in ("base", "new")] +
                [fx(V(f"search.hint.{host}.speedup_new_vs_base.sims{b}.median_pair_ratio")) for host in ("native", "node", "chrome")] +
                [fx(V(f"search.hint.{host}_over_native.new.sims{b}")) for host in ("node", "chrome")])
table(["sims", "native prev ms", "native new ms", "Node prev ms", "Node new ms", "Chromium prev ms", "Chromium new ms", "native speed-up",
       "Node speed-up", "Chromium speed-up", "Node/native (new)", "Chromium/native (new)"], rows)
p("Speed-ups are medians of 5 ABBA pair ratios (previous ms / new ms). WASM/native = WASM median ms / native median ms, same engine.")
rows = []
for cell, labc in (("1t", "1 thread"), ("8t-l3", "8 threads, mask 6-17"), ("8t-pin", "8 threads pinned 6-13")):
    rows.append([labc, fmt(V(f"search.advisor.{cell}.base.ms"), 1), fmt(V(f"search.advisor.{cell}.new.ms"), 1),
                 fmt(V(f"search.advisor.{cell}.new.sims_per_s")), fx(V(f"search.advisor.{cell}.speedup_new_vs_base.median_pair_ratio"))])
table(["Advisor, 8,000 sims", "previous ms", "new ms", "new sims/s", "speed-up (paired)"], rows)
p(f"Whole machine, independent hint searches at 1,000 sims ({bh}): {fmt(V('ours.scale.hint.new.whole_machine_best.sims1000.searches_per_s'))} searches/s.")

h("5. The shipped game, headless (C#, sts2.dll v0.107.1, .NET 9)")
table(["Cell", "Layout", "ms/action (wall, per process)", "actions/s", "process CPU ms/action", "game-thread CPU ms/action"], [
    ["A0", "fresh process per fight (cold), 1 core", f"{A0s['ms_per_decision']:.2f}", fmt(A0s["decisions_per_s_playonly"], 2), f"{a0cpu:.2f}", "-"],
    ["A", "one warm process, 1 core (play-only)", f"{At['ms_per_decision']:.3f}", f"{fmt(At['decisions_per_s_playonly'], 2)} (incl. reset {fmt(Am['decisions_per_s'], 2)})",
     f"{At['cpu_ms_per_decision']:.3f}", f"{At['gamethread_cpu_ms_per_decision']:.3f}"],
])
p(f"Cell A: process CPU utilisation during play {pct(At['cpu_util_during_play'])}, {At['gamethread_voluntary_switches_per_decision']:.1f} voluntary "
  f"game-thread sleeps per action; JIT: same 220 fights cold vs warm {wvc['cold_over_warm_wall']}x wall, {wvc['cold_over_warm_cpu']}x CPU; "
  f"first fight {jc0['ms_per_action']} ms/action wall, {jc0['cpu_ms_per_action']} ms CPU.")
p()
rows = []
for c in ("B2", "B4", "B8", "B16", "C1", "C2", "C3", "C4", "C5", "C6"):
    s = cs_cells[c]
    rows.append([c, s["workers"], s["pin"], s["physical_cores_used"], fmt(s["machine"]["decisions_per_s"], 1), fmt(s["machine"]["fights_per_s"], 1),
                 fmt(s["machine"]["allbusy_decisions_per_s"], 1), fmt(s["machine"]["decisions_per_s_per_physical_core"], 1),
                 s["host"]["busy_worker_cpus_pct_timed"] if s["host"]["busy_worker_cpus_pct_timed"] is not None else "-", s["host"]["busy_all_pct_timed"], s["host"]["freq_mhz_timed"]["mean"],
                 f"{s['timed']['cpu_ms_per_decision']:.3f}", f"{s['timed']['ms_per_decision']:.2f}"])
table(["Cell", "procs", "pin", "phys cores", "actions/s", "fights/s", "actions/s all-busy", "per core", "busy % (cell CPUs)",
       "busy % (all)", "MHz", "CPU ms/action", "wall ms/action/proc"], rows)
p(f"Best one core: {bestB} {fmt(csB, 1)} actions/s; best whole machine: {bestC} {fmt(csC, 1)} actions/s "
  f"({fmt(csC / 24, 1)} per core; {pct(V('cs.whole_machine_scaling_vs_best_one_core'))} of 24x {bestB}).")
p()
p("Random play, the game vs the engines (W1 workload; the game's 8,448 fights are seeds 0-8447 x 1, the engines' 2,200 seeds x 50):")
p()
table(["Engine", "fights", "win rate", "actions/fight"], [
    ["shipped game (C4/C5/C6)", c5["fights_ok"], pct(c5["win_rate"], 2), f"{c5['decisions_per_fight']:.2f}"],
    ["ours (new)", 110000, pct(V("xsim.w1.ours.win_rate"), 2), f"{V('xsim.w1.ours.actions_per_fight'):.2f}"],
    ["sansb, instance", 110000, pct(V("xsim.w1.sansb-inst.win_rate"), 2), f"{V('xsim.w1.sansb-inst.actions_per_fight'):.2f}"],
    ["sts2core, instance", 110000, pct(V("xsim.w1.core-inst.win_rate"), 2), f"{V('xsim.w1.core-inst.actions_per_fight'):.2f}"],
    ["sansb, own uniform (dedup)", 110000, pct(V("xsim.w1.sansb-unif.win_rate"), 2), f"{V('xsim.w1.sansb-unif.actions_per_fight'):.2f}"],
    ["sts2core, own engine (dedup)", 110000, pct(V("xsim.w1.core-eng.win_rate"), 2), f"{V('xsim.w1.core-eng.actions_per_fight'):.2f}"],
])
p(f"Per encounter, game vs ours: {V('cs_vs_ours.perenc.encounters_within_2se')}/22 within |z|<=2 (max |z| {V('cs_vs_ours.perenc.max_abs_z')}); "
  f"max actions/fight gap {V('cs_vs_ours.perenc.max_abs_apf_diff'):.2f}.")

h("6. Ratios, ours vs the shipped game (numerator / denominator)")
table(["Ratio", "value", "numerator", "denominator"], [
    ["(a) one process wall, cold (old site method)", fx(V("ratio.one_process_wall.cold_A0"), 0), f"C# A0 {A0s['ms_per_decision']} ms/action",
     f"ours 1core {o1_us:.4f} µs/action"],
    ["(a) one process wall, warm", fx(V("ratio.one_process_wall.warm_A"), 0), f"C# A {At['ms_per_decision']} ms/action", f"ours {o1_us:.4f} µs/action"],
    ["(b) CPU per action, process", fx(V("ratio.cpu_time.process"), 0), f"C# A {At['cpu_ms_per_decision']} ms CPU", f"ours {o1_us:.4f} µs"],
    ["(b) CPU per action, game thread", fx(V("ratio.cpu_time.gamethread"), 0), f"C# A {At['gamethread_cpu_ms_per_decision']} ms CPU", f"ours {o1_us:.4f} µs"],
    ["(c) one core saturated, ours 1 thread", fx(V("ratio.saturated_one_core.ours_1thread"), 0), f"ours 1core {fmt(o1)} actions/s", f"C# {bestB} {fmt(csB, 1)}"],
    ["(c) one core, both threads each", fx(V("ratio.saturated_one_core.ours_both_threads"), 0), f"ours 1core-smt {fmt(s1smt)}", f"C# {bestB} {fmt(csB, 1)}"],
    ["(d) whole machine", fx(V("ratio.whole_machine"), 0), f"ours {bw_preset} {fmt(bw)}", f"C# {bestC} {fmt(csC, 1)}"],
    ["(d) whole machine vs C# all-busy window", fx(V("ratio.whole_machine.vs_cs_allbusy"), 0), f"ours {fmt(bw)}",
     f"C# {bestCab} all-busy {fmt(cs_cells[bestCab]['machine']['allbusy_decisions_per_s'], 1)}"],
])
p(f"Sensitivities: game-thread CPU of the most favourable cell gives {fx(V('ratio.cpu_time.gamethread_lowest_cell'), 0)}; ours 1core from the scale "
  f"runs gives (c) {fx(V('ratio.saturated_one_core.ours_1thread_scale_baseline'), 0)}. The retired published figure was "
  f"{fx(V('ratio.published_23000x_recomputed'), 0)} (cold one-process wall).")

h("7. Cross-engine")
rows = []
for t in XTAGS:
    rows.append([ENG[t], fmt(V(f"xsim.w1.{t}.fights_per_s")), fmt(V(f"xsim.w1.{t}.actions_per_s")), f"{V(f'xsim.w1.{t}.ns_per_action'):.0f}",
                 pct(V(f"xsim.w1.{t}.win_rate"), 2), f"{V(f'xsim.w1.{t}.actions_per_fight'):.2f}", f"{V(f'xsim.w1.{t}.ipc'):.2f}",
                 f"{V(f'xsim.w1.{t}.ghz'):.2f}", V(f"xsim.w1.{t}.digest")])
table(["W1 per core (CPU 20, 7 rounds)", "fights/s", "actions/s", "ns/action", "win rate", "actions/fight", "IPC", "GHz", "digest"], rows)
p("Paired (same round) actions/s ratios: " + "; ".join(
    f"{a} / {b_} {fx(V(f'xsim.w1.ratio.{a}_over_{b_}.median_round_ratio'), 3)}" for a, b_ in
    (("ours", "sansb-inst"), ("ours-norelic", "sansb-inst"), ("ours", "core-inst"), ("ours", "core-eng"))))
p()
rows = []
for t in W2TAGS:
    rows.append([W2TAGS[t], fmt(V(f"xsim.w2.{t}.actions_per_s")), f"{V(f'xsim.w2.{t}.ns_per_action'):.0f}", fmt(V(f"xsim.w2.{t}.playouts_per_s")),
                 pct(V(f"xsim.w2.{t}.win_rate"), 2) if V(f"xsim.w2.{t}.win_rate") is not None else "-", f"{V(f'xsim.w2.{t}.actions_per_playout'):.2f}"])
rows.append(["ours (new), in place (our search pattern; ours/w2 stage, 2 runs x 5 passes)", fmt(V("ours.w2.1core.new.inplace.actions_per_s")),
             f"{1e9 / V('ours.w2.1core.new.inplace.actions_per_s'):.0f}", fmt(med([r["inplace"]["playouts_per_s_median"] for r in OW2["new"]])),
             pct(OW2["new"][0]["inplace"]["win_rate"], 2), f"{OW2['new'][0]['inplace']['actions_per_playout']:.2f}"])
table(["W2 per core (sansb fixture, A10, 240k playouts)", "actions/s", "ns/action", "playouts/s", "win rate", "actions/playout"], rows)
p("W2 ratios (paired rounds): " + "; ".join(f"ours/{b_} {fx(V(f'xsim.w2.ratio.ours_over_{b_}.median_round_ratio'), 3)}"
                                           for b_ in ("sansb-inst", "sansb-own", "core-inst", "core-eng")) +
  f"; ours in-place/sansb-inst {fx(V('xsim.w2.ratio.ours_inplace_over_sansb-inst'), 2)}; ours in-place/sts2core-inst "
  f"{fx(V('xsim.w2.ratio.ours_inplace_over_core-inst'), 2)}. Previous-vs-new W2: clone {fx(V('ours.w2.1core.speedup_new_vs_base.clone'), 3)}, "
  f"in place {fx(V('ours.w2.1core.speedup_new_vs_base.inplace'), 3)}.")
p()
p(f"Their own benchmarks: sansb eval_throughput median {fmt(V('xsim.own.sansb_eval_throughput.median_transitions_per_s'))} transitions/s over "
  f"{METRICS['xsim.own.sansb_eval_throughput.median_transitions_per_s']['n']} real-game fixtures (p10 {fmt(METRICS['xsim.own.sansb_eval_throughput.median_transitions_per_s']['p10'])}, "
  f"p90 {fmt(METRICS['xsim.own.sansb_eval_throughput.median_transitions_per_s']['p90'])}); sts2core bench 1 thread "
  f"{V('xsim.own.sts2core_bench.1t.steps_per_s') / 1e6:.2f} M steps/s, 24 threads {V('xsim.own.sts2core_bench.24t.steps_per_s') / 1e6:.1f} M, "
  f"48 threads {V('xsim.own.sts2core_bench.48t.steps_per_s') / 1e6:.1f} M.")
p()
p("Whole-machine W1, ours / external (aggregate actions/s): " + "; ".join(
    f"{preset}: sansb {fx(V(f'xsim.scale.w1.ratio.ours_over_sansb.{preset}'))}, sts2core {fx(V(f'xsim.scale.w1.ratio.ours_over_sts2core.{preset}'))}"
    for preset in ("1core", "1core-smt", "ccx", "phys24", "smt48")))
p()
for eng in ("sansb", "sts2core"):
    p(f"Per encounter vs ours: {eng} {V(f'xsim.perenc.{eng}.encounters_within_2se')}/22 within 2 SE; outside: " +
      (", ".join(f"{x['encounter']} {x['diff_pp']:+.1f} pp (2SE {x['two_se_pp']})" for x in METRICS[f'xsim.perenc.{eng}.encounters_within_2se']['outside']) or "none") +
      f"; max actions/fight gap {V(f'xsim.perenc.{eng}.max_abs_apf_diff'):.2f}.")
p()

h("8. Training")
rows = []
for arm in ("base", "new", "pgo"):
    rows.append([lab[arm], f"{V(f'training.percore.{arm}.task_clock_s'):.2f} [{METRICS[f'training.percore.{arm}.task_clock_s']['spread'][0]:.2f}-{METRICS[f'training.percore.{arm}.task_clock_s']['spread'][1]:.2f}]",
                 f"{V(f'training.percore.{arm}.cpu_s_per_run'):.2f}", f"{V(f'training.percore.{arm}.runs_per_hour_per_core'):.0f}"])
table(["Per core (CPU 20, 6 whole runs, 3 reps)", "task-clock s", "CPU s/run", "runs/hour/core"], rows)
p(f"Per-core speed-ups (ratio of medians): new/previous {fx(V('training.percore.speedup.new_vs_base.ratio_of_medians'), 3)}, "
  f"PGO/previous {fx(V('training.percore.speedup.pgo_vs_base.ratio_of_medians'), 3)}, PGO/new {fx(V('training.percore.speedup.pgo_vs_new.ratio_of_medians'), 3)}; "
  "all 9 runs produced byte-identical game records.")
p()
rows = []
for c in cfgs:
    k = f"training.full.{c}"
    rows.append([tlab[c], f"{V(k + '.collect_s'):.1f}", f"{V(k + '.train_s'):.2f}", f"{V(k + '.iter_s'):.1f}", fmt(V(k + ".games_per_hour")),
                 f"{V(k + '.cpu_s_per_game'):.2f}", f"{V(k + '.util_collect'):.3f}", f"{V(k + '.gpu_util_update_pct'):.1f}",
                 f"{V(k + '.campaign_150_iters_h'):.1f}", f"{METRICS[k + '.errors']['value']}"])
table(["Full iteration 0 (960 games, 46 workers, 1x RTX 3090)", "collect s", "train s", "iter s", "games/h", "CPU s/game", "util_collect",
       "GPU util update %", "150 iters (h)", "errors"], rows)
p("Speed-ups: " + "; ".join(f"{b_} vs {a}: collect {fx(V(f'training.full.speedup.{b_}_vs_{a}.collect_s'), 3)}, iter {fx(V(f'training.full.speedup.{b_}_vs_{a}.iter_s'), 3)}"
                            for a, b_ in (("base-plain", "new-plain"), ("base-plain", "new-pgo"), ("new-plain", "new-pgo"))) + ".")
p("Whole machine vs one worker (INDICATIVE, different games): " + ", ".join(f"{c} {fx(V(f'training.whole_vs_one_core.{c}'), 1)}" for c in cfgs) +
  " (46 workers on 24 cores, collection tail-bound at util_collect ~0.5).")
p(f"Historical anchor: the champion's own Sept-2026 leg had collect median {V('training.historical_anchor.sept_leg_collect_median_s')} s "
  "(older engine, PLR 0.5; its first row had util_collect 0.96).")
p("training.previous_caveat: filled in results.json by apply_xcheck.py (from the watchdog-free replay, training/diag/)")

h("Identity checks")
table(["check", "pass", "detail"], [[c["check"], "PASS" if c["pass"] else "FAIL", c["detail"].replace("|", "/")[:220]] for c in CHECKS])
h("Flags, deviations and skips")
for f in FLAGS:
    p(f"- {f}")

# ============================================================================= write
os.makedirs(OUTD, exist_ok=True)
res = {
    "meta": {
        "generated_utc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "bench2_dir": "<scratch>/bench2 (all source paths are relative to it)",
        "previous_engine": "d3ea66a51f93479d431b1a41202548c528964963 (12 Oct 2026)",
        "new_engine": "6d19182374fab41664ee4d727584960826f25109 (PR #761, not merged) + uncommitted bench harness",
        "conventions": "value = median over runs; spread = [min, max] over runs; n = runs (or fights/actions where stated); "
                       "speed-ups as ratio of medians and, where paired, median of per-pair ratios.",
        "definitions": {
            "one_core": "Rust: one single-threaded process pinned to CPU 20 with its SMT sibling 44 idle. C#: one game process + its "
                        "Python driver confined to CPUs 20,44 (both threads of one physical core).",
            "both_threads_of_one_core": "Rust preset 1core-smt: 2 processes on CPUs 20 and 44. C#: B cells (2-16 processes on 20,44).",
            "whole_machine": "All 24 cores / 48 threads. Rust: best of phys24 (24 processes, one per core) and smt48 (48 processes). "
                             "C#: best of cells C1-C6 (24-384 processes).",
            "W1": "Random-play fights: Ironclad A0, 10-card starter deck, 80/80 HP, Burning Blood, vs the 22 Act-1 Overgrowth "
                  "encounters round-robin; uniform random legal moves (one choice per card instance per target = 'instance' policy "
                  "for the external engines); 2,200 seeds x 50 fights.",
            "W2": "sansb's bench fixture: Ironclad starter vs 2 Toadpoles, 68/80 HP, no relic, an Ascension-10 fight (the fixture elides "
                  "the field), 240,000 playouts, clone-and-apply per step.",
            "actions": "Every state transition: card plays and end turns (ours: every apply() call).",
        },
    },
    "headline": HEAD,
    "at_a_glance": GLANCE,
    "context_facts": CONTEXT,
    "identity_checks": CHECKS,
    "identity_checks_passed": f"{sum(c['pass'] for c in CHECKS)}/{len(CHECKS)}",
    "flags": FLAGS,
    "metrics": METRICS,
}
with open(os.path.join(OUTD, "results.json"), "w") as f:
    json.dump(res, f, indent=1, ensure_ascii=False)
with open(os.path.join(OUTD, "RESULTS.md"), "w") as f:
    f.write("\n".join(MD) + "\n")
print(f"metrics={len(METRICS)} checks={sum(c['pass'] for c in CHECKS)}/{len(CHECKS)} flags={len(FLAGS)}")
for c in CHECKS:
    if not c["pass"]:
        print("FAIL:", c["check"], c["detail"])
