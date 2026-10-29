"""Crosscheck post-pass for <BENCH_DIR>/results/results.json.

    python3 -I scripts/site-bench/bench/results/apply_xcheck.py <BENCH_DIR>

Run it right after analyze.py. Independent of analyze.py: re-derives the
headline numbers from the raw files with its own minimal parsing, records the
comparison under results.json["xcheck"], and applies the crosscheck fixes:
the training typical-iteration figures and the previous engine's caveat from
the watchdog-free replay (training/diag/, read from
<BENCH_DIR>/training/diag/out/), the cross-engine whole-machine ratios,
lower-bound labels, headline wording, flags and the changelog.

It updates results.json only; the RESULTS.md that analyze.py writes does not
carry these additions.

Idempotent: every change is an assignment or a keyed replace. Re-running
analyze.py regenerates results.json WITHOUT these changes; run this script
again afterwards.
"""

import glob
import json
import os
import re
import statistics as st
import sys

B = sys.argv[1]
RJ = os.path.join(B, "results", "results.json")


def perf(p):
    d = {}
    for line in open(p):
        f = line.strip().split(",")
        if len(f) > 3 and f[0] and f[0][0].isdigit():
            d[f[2]] = float(f[0])
    return d


def lastjson(p):
    lines = [ln for ln in open(p) if ln.strip().startswith("{")]
    return json.loads(lines[-1])


mine = {}

# ---- ours W1 one core (runs/ours/w1) ----------------------------------------
w1 = {}
for eng in ("base", "new"):
    rows = [json.load(open(f"{B}/runs/ours/w1/{eng}-{k}.json")) for k in range(1, 8)]
    w1[eng] = rows
    mine[f"ours.w1.1core.{eng}.actions_per_s"] = st.median(r["decisions_per_s"] for r in rows)
    mine[f"ours.w1.1core.{eng}.fights_per_s"] = st.median(r["fights_per_s"] for r in rows)
    mine[f"ours.w1.1core.{eng}.ns_per_action"] = 1e9 / mine[f"ours.w1.1core.{eng}.actions_per_s"]
mine["ours.w1.1core.speedup_new_vs_base.median_pair_ratio"] = st.median(
    n["decisions_per_s"] / b["decisions_per_s"] for n, b in zip(w1["new"], w1["base"])
)

# ---- scale runs (per-process .out files) -------------------------------------
def work(j):
    if "decisions" in j:
        return j["decisions"], j["fights"], j["elapsed_s"]
    if "actions" in j:
        return j["actions"], j["fights"], j["elapsed_s"]
    return j["transitions"], j["playouts"], j["elapsed_s"]


scale = {}
for root, tag in [
    ("runs/ours/scale", "w1-new"),
    ("runs/ours/scale", "w1-base"),
    ("runs/ours/scale", "w2-new"),
    ("runs/xsim/scale", "w1-sansb"),
    ("runs/xsim/scale", "w1-sts2core"),
]:
    for pr in ("1core", "1core-smt", "ccx", "ccx-smt", "phys12", "phys24", "smt48"):
        aggs = []
        for r in sorted(glob.glob(f"{B}/{root}/{tag}/r*")):
            outs = sorted(glob.glob(f"{r}/{pr}/*-p[0-9][0-9]*.out"))
            if not outs:
                continue
            aggs.append(sum(work(lastjson(o))[0] / work(lastjson(o))[2] for o in outs))
        if aggs:
            scale[(tag, pr)] = st.median(aggs)
keymap = {"w1-new": "ours.scale.w1.new", "w1-base": "ours.scale.w1.base", "w2-new": "ours.scale.w2.new",
          "w1-sansb": "xsim.scale.w1.sansb", "w1-sts2core": "xsim.scale.w1.sts2core"}
for (tag, pr), v in scale.items():
    mine[f"{keymap[tag]}.{pr}.agg_actions_per_s"] = v

# ---- cross-engine W1 / W2 one core ------------------------------------------
xw1 = {}
for arm in ("ours", "ours-norelic", "sansb-inst", "sansb-unif", "core-inst", "core-eng"):
    rows = []
    for k in range(1, 8):
        j = lastjson(f"{B}/runs/xsim/w1/{arm}-{k}.out")
        a = j.get("actions", j.get("decisions"))
        rows.append(a / j["elapsed_s"])
    xw1[arm] = rows
    mine[f"xsim.w1.{arm}.actions_per_s"] = st.median(rows)
for a, b in [("ours", "sansb-inst"), ("ours-norelic", "sansb-inst"), ("ours", "core-inst")]:
    mine[f"xsim.w1.ratio.{a}_over_{b}.median_round_ratio"] = st.median(x / y for x, y in zip(xw1[a], xw1[b]))

xw2 = {}
for arm in ("ours", "sansb-own", "sansb-unif", "sansb-inst", "core-eng", "core-inst"):
    rows = []
    for k in range(1, 6):
        j = lastjson(f"{B}/runs/xsim/w2/{arm}-{k}.out")
        if arm == "ours":
            rows.append(j["transitions"] / j["elapsed_s"])
        elif arm == "sansb-own":
            rows.append(j["work"]["transitions"] / j["timing"]["wall_seconds"])
        elif arm.startswith("sansb"):
            rows.append(j["work"]["transitions"] / j["elapsed_s"])
        else:
            rows.append(j["transitions"] / j["wall_seconds"])
    xw2[arm] = rows
    mine[f"xsim.w2.{arm}.actions_per_s"] = st.median(rows)

# ---- C# cells -----------------------------------------------------------------
cs = {}
for cell in ("A", "B2", "B4", "B8", "B16", "C1", "C2", "C3", "C4", "C5", "C6"):
    steps = 0
    cpu = 0.0
    gt = 0.0
    play = 0.0
    tgo, tend = [], []
    for f in sorted(glob.glob(f"{B}/csharp/runs/{cell}/worker-*.jsonl")):
        for line in open(f):
            x = json.loads(line)
            if x.get("SUMMARY"):
                tgo.append(x["t_go"])
                tend.append(x["t_end"])
            elif x.get("phase") == "timed":
                steps += x["steps"]
                cpu += x["cpu_s"]
                gt += x["gt_cpu_s"]
                play += x["play_s"]
    cs[cell] = dict(aps=steps / (max(tend) - min(tgo)), cpu_ms=cpu / steps * 1e3, gt_ms=gt / steps * 1e3,
                    wall_ms=play / steps * 1e3)
    mine[f"cs.{cell}.actions_per_s"] = cs[cell]["aps"]
    mine[f"cs.{cell}.process_cpu_ms_per_action"] = cs[cell]["cpu_ms"]
    mine[f"cs.{cell}.gamethread_cpu_ms_per_action"] = cs[cell]["gt_ms"]
a0 = [json.loads(ln) for ln in open(f"{B}/csharp/runs/A0.jsonl")]
a0 = [r for r in a0 if "steps" in r]
a0_steps = sum(r["steps"] for r in a0)
a0_wall_ms = sum(r["play_s"] for r in a0) / a0_steps * 1e3
a0_cpu_ms = sum(r["cpu_s"] for r in a0) / a0_steps * 1e3
mine["cs.A0.ms_per_action_wall"] = a0_wall_ms
mine["cs.A0.process_cpu_ms_per_action"] = a0_cpu_ms
mine["cs.A.ms_per_action_wall_playonly"] = cs["A"]["wall_ms"]

ours_ns = mine["ours.w1.1core.new.ns_per_action"]
mine["ratio.one_process_wall.cold_A0"] = a0_wall_ms * 1e6 / ours_ns
mine["ratio.one_process_wall.warm_A"] = cs["A"]["wall_ms"] * 1e6 / ours_ns
mine["ratio.cpu_time.process"] = cs["A"]["cpu_ms"] * 1e6 / ours_ns
mine["ratio.cpu_time.gamethread"] = cs["A"]["gt_ms"] * 1e6 / ours_ns
mine["ratio.cpu_time.gamethread_lowest_cell"] = min(c["gt_ms"] for c in cs.values()) * 1e6 / ours_ns
mine["ratio.saturated_one_core.ours_1thread"] = mine["ours.w1.1core.new.actions_per_s"] / cs["B16"]["aps"]
mine["ratio.saturated_one_core.ours_both_threads"] = scale[("w1-new", "1core-smt")] / cs["B16"]["aps"]
best_ours = max(scale[("w1-new", "phys24")], scale[("w1-new", "smt48")])
best_cs = max(cs[c]["aps"] for c in ("C1", "C2", "C3", "C4", "C5", "C6"))
mine["ratio.whole_machine"] = best_ours / best_cs

# ---- hint search + advisor ------------------------------------------------------
def hint(p):
    d = {}
    for line in open(p):
        m = re.match(r"sims=(\d+) cold_ms=[\d.]+ median_ms=([\d.]+)", line)
        if m:
            d[int(m.group(1))] = float(m.group(2))
    return d


H = {}
for host, d in (("native", "runs/ours/hint"), ("node", "runs/wasm/node"), ("chrome", "runs/wasm/chrome")):
    for eng in ("base", "new"):
        H[(host, eng)] = [hint(f"{B}/{d}/{eng}-{k}.txt") for k in range(1, 6)]
        mine[f"search.hint.{host}.{eng}.sims1000.ms"] = st.median(r[1000] for r in H[(host, eng)])
mine["search.hint.chrome.speedup_new_vs_base.sims1000.median_pair_ratio"] = st.median(
    b[1000] / n[1000] for b, n in zip(H[("chrome", "base")], H[("chrome", "new")])
)
for tag in ("1t", "8t-l3", "8t-pin"):
    for eng in ("base", "new"):
        v = []
        for k in range(1, 5):
            rows = [json.loads(ln) for ln in open(f"{B}/runs/ours/adv/{tag}-{eng}-{k}.jsonl") if ln.strip()]
            v.append([r for r in rows if "median_s" in r][-1]["median_s"] * 1e3)
        mine[f"search.advisor.{tag}.{eng}.ms"] = st.median(v)

# ---- training: trainer runs -------------------------------------------------------
tr = {}
for line in open(f"{B}/training/results/train_iter.jsonl"):
    j = json.loads(line)
    it = j["iterations"][0]
    tr.setdefault(j["label"].rsplit("-", 1)[0], []).append(it)
for arm, rows in tr.items():
    mine[f"training.full.{arm}.collect_s"] = st.median(r["collect_s"] for r in rows)
    mine[f"training.full.{arm}.iter_s"] = st.median(r["iter_s"] for r in rows)
    mine[f"training.full.{arm}.campaign_150_iters_h"] = mine[f"training.full.{arm}.iter_s"] * 150 / 3600
train_s = {arm: st.median(r["train_s"] for r in rows) for arm, rows in tr.items()}
pc = {}
for arm in ("base", "new", "pgo"):
    pc[arm] = st.median(perf(f"{B}/training/percore/{arm}-n6-{k}.perf")["task-clock"] / 1e3 for k in (1, 2, 3))
    mine[f"training.percore.{arm}.task_clock_s"] = pc[arm]
mine["training.percore.speedup.pgo_vs_base.ratio_of_medians"] = pc["base"] / pc["pgo"]

# ---- training: diag replay (training/diag/out/pool-*-full.jsonl) ---------------------
armmap = {"base": "base-plain", "new": "new-plain", "pgo": "new-pgo"}
D = {}
for a in ("base", "new", "pgo"):
    rows = [json.loads(ln) for ln in open(f"{B}/training/diag/out/pool-{a}-full.jsonl")]
    meta = [r for r in rows if r.get("meta")][0]
    end = [r for r in rows if r.get("meta_end")][0]
    games = [r for r in rows if "i" in r]
    t0 = meta["t_batch"]
    g703 = [g for g in games if g["i"] == 703][0]
    others_done = max(g["t_end"] for g in games if g["i"] != 703) - t0
    D[a] = dict(
        games=len(games),
        errors=sum(1 for g in games if g.get("error")),
        collect=end["batch_wall_s"],
        others_done=others_done,
        g703_wall=g703["wall_s"],
        g703_start=g703["t_start"] - t0,
        g703_alone=end["batch_wall_s"] - others_done,
        tot_cpu=sum(g["cpu_s"] for g in games),
        med_cpu=st.median(g["cpu_s"] for g in games),
        g703=dict(seed=g703["seed"], asc=g703["asc"], outcome=g703["outcome"], floor=g703["total_floor"],
                  n_transitions=g703["n_transitions"], act1=g703.get("act1")),
    )
    D[a]["typical_iter"] = others_done + train_s[armmap[a]]
    D[a]["typical_150_h"] = D[a]["typical_iter"] * 150 / 3600
    D[a]["typical_games_per_h"] = (len(games) - 1) / others_done * 3600
single = {}
for a, f in (("new", "new-s703"), ("pgo", "pgo-s703")):
    rows = [json.loads(ln) for ln in open(f"{B}/training/diag/out/{f}.jsonl") if ln.strip()]
    single[a] = [r for r in rows if r.get("i") == 703][0]["wall_s"]
base_a4 = [json.loads(ln) for ln in open(f"{B}/training/diag/out/base-a4.jsonl")]
single["base"] = [r for r in base_a4 if r.get("i") == 703][0]["wall_s"]
trace = {}
for a in ("base", "pgo"):
    steps = [json.loads(ln) for ln in open(f"{B}/training/diag/out/trace-{a}-s703.jsonl")]
    steps = [s for s in steps if s.get("kind") == "step"]
    trace[a] = (sum(s["wall_s"] for s in steps), max(s["wall_s"] for s in steps))

# ---- compare with results.json -------------------------------------------------------
R = json.load(open(RJ))
M = R["metrics"]
cmp_rows = []
worst = 0.0
for k, v in sorted(mine.items()):
    ref = M.get(k, {}).get("value")
    if not isinstance(ref, (int, float)):
        cmp_rows.append({"metric": k, "xcheck": round(v, 4), "results_json": ref, "rel_diff": None})
        continue
    rd = (v - ref) / ref if ref else 0.0
    worst = max(worst, abs(rd))
    cmp_rows.append({"metric": k, "xcheck": round(v, 4), "results_json": ref, "rel_diff": round(rd, 6)})


def met(value, unit, sources, definition, n=None, spread=None, **extra):
    d = {"value": value, "unit": unit, "n": n, "spread": spread, "sources": sources, "definition": definition}
    d.update(extra)
    return d


DIAG_SRC = ["training/diag/out/pool-{base,new,pgo}-full.jsonl", "training/diag/out/pool-*-full.log",
            "training/diag/poolrep.py", "training/results/train_iter.jsonl (train_s)"]
REPLAY_DEF = ("Diag replay (training/diag/poolrep.py): the trainer's iteration-0 pool re-played outside the trainer "
              "with NO stall watchdog: 46 workers, the same 960 seeds/ascensions, checkpoint, sims (250/1000) and "
              "environment as the trainer runs. Ran 2026-10-14 16:38-18:10 UTC (after the measurement window), one "
              "full replay at a time, host ~2% busy before each. Validated: replay collect new-plain 533.7 s vs "
              "trainer 535.4/537.6 s, new-PGO 379.7 s vs 378.8/383.5 s; previous: the other 959 games finish at "
              "574.6 s, so a 600 s watchdog fires at ~1,175 s vs the trainer's 1,177.0/1,180.4 s collect.")

caveat = (
    "The previous engine's two trainer runs each finished 959 of 960 games because the trainer's STALL WATCHDOG "
    "(ppo/workers.py run_pool: DEFAULT_RUN_TIMEOUT_S = 600.0 in both the previous and the new trainer source; "
    "pending runs are abandoned once no run has completed for 600 s) dropped one legitimately long game: "
    "submission 703, seed 10,000,703, A4, Act 1 Underdocks, lost at floor 31 after 46 run-level decisions, on "
    "every build. It is heavy search work, not a hang and not a behaviour difference: one step (the floor-16 Act-1 "
    "boss fight, 1,000 sims per decision) takes 1,176.8 s of the game's 1,184.7 s on the previous engine "
    "(222.5 of 225.1 s on new-PGO), and all 960 games match across previous / new-plain / new-PGO (outcome, "
    "final metrics, transition count, decision sequence; diag agent, 960/960). In a no-watchdog replay of the "
    "same pool the other 959 games are done by 574.6 s and game 703 then runs alone until 1,682 s, so a 600 s "
    "watchdog fires at ~1,175 s, matching the trainer's 1,177.0/1,180.4 s collect. Consequences: (1) the "
    "previous engine's trainer iteration-0 time (1,213 s; collect 1,179 s) is a LOWER BOUND for the 960-game "
    "work (same work uncut: ~1,682 s collect, ~1,716 s iteration), and its errors=1 / fingerprint difference is "
    "that one dropped game. (2) In every config about 55% of iteration-0 collection is game 703 running alone "
    "after all other games finished (previous uncut: 66%; previous as cut: the 600 s watchdog wait, 51%); "
    "this, not the engine, is why util_collect is 0.47-0.50 here (real Ironclad campaigns: median 0.94-0.95). "
    "(3) Iteration 0 x 150 is therefore NOT a campaign projection; use training.typical.* (iteration 0 "
    "without game 703: previous 609 s, new-plain 276 s, new-PGO 208 s -> 25.4 / 11.5 / 8.7 h per 150 "
    "iterations). (4) The previous-engine collect (~1,179 s) vs the Sept-2026 leg's 500.5 s median is explained "
    "the same way: 600 s of it is the watchdog wait; the other 959 games finished at ~577-580 s. Context: in the "
    "four Aug-Sep Ironclad campaigns on this host (ironclad-plr-c1, ironclad-underdocks, -v2, -v3; older engine "
    "builds, 762 iterations) 21 iterations (2.8%) hit the same watchdog (errors=1, collect 970-1,170 s) and 19 "
    "more (2.5%) had util_collect < 0.7, so the dropped game is an existing, rare trainer behaviour."
)

M["training.previous_caveat"] = met(
    caveat, "text",
    ["training/diag/out/*", "training/runs/base-plain-r{1,2}/{trainer.log,main.stats.jsonl}",
     "training/src-base/sts2-pycli/python/ppo/workers.py:23", "training/src-new/sts2-pycli/python/ppo/workers.py:29",
     "<main checkout>/sts2-pycli/data/runs/ironclad-{plr-c1,underdocks,underdocks-v2,underdocks-v3}/main.stats.jsonl"],
    "Why the previous engine's two full-iteration runs had errors=1 (959/960) and what it does to the training "
    "numbers. Diag agent's conclusion, re-derived by the crosscheck from the diag replay's raw per-game rows.",
)

LB = ("LOWER BOUND for the 960-game work: the stall watchdog dropped game 703 after 600 s with no completion "
      "(959/960 games); see training.previous_caveat. ")
for k in ("collect_s", "iter_s", "games_per_hour", "games_per_hour_per_core", "wall_s"):
    key = f"training.full.base-plain.{k}"
    if key in M and not M[key]["definition"].startswith("LOWER BOUND"):
        M[key]["definition"] = LB + M[key]["definition"]
        M[key]["label"] = "lower bound (watchdog-truncated, 959/960 games)"
OUT = ("NOT a campaign projection: about 55% of iteration-0 collection is one outlier game (submission 703) "
       "running alone; use training.typical.*.campaign_150_iters_h. ")
for arm in ("base-plain", "new-plain", "new-pgo"):
    key = f"training.full.{arm}.campaign_150_iters_h"
    if not M[key]["definition"].startswith("NOT a campaign"):
        M[key]["definition"] = OUT + M[key]["definition"]
        M[key]["label"] = "iteration 0 x 150 (not a projection)"
    for k in ("util_collect",):
        key = f"training.full.{arm}.{k}"
        if "outlier" not in M[key]["definition"]:
            M[key]["definition"] += (" Low (~0.5) in every config because one outlier game (submission 703) "
                                     "runs alone for ~55% of collection; real campaigns run at a median 0.94-0.95.")
SPD = ("Trainer as measured. The previous engine's time is watchdog-truncated (959/960 games), so this is NOT a "
       "same-work ratio (same work uncut is larger, and is inflated by the outlier game which the new engine speeds "
       "up more than a typical game); see training.typical.speedup.*. ")
for key in [k for k in M if k.startswith("training.full.speedup.") and "base-plain" in k]:
    if not M[key]["definition"].startswith("Trainer as measured."):
        M[key]["definition"] = SPD + M[key]["definition"]
M["training.campaign_hours_saved_note"] = met(
    "training.full.campaign_hours_saved_pgo_vs_previous (33.2 h) is iteration 0 x 150 and inherits the outlier "
    "distortion; the typical-iteration saving is training.typical.campaign_hours_saved_pgo_vs_previous.",
    "text", ["xcheck"], "Supersedes training.full.campaign_hours_saved_pgo_vs_previous for publication.")

for a, arm in armmap.items():
    d = D[a]
    pre = f"training.diag.replay.{arm}"
    M[f"{pre}.collect_s"] = met(round(d["collect"], 1), "s", DIAG_SRC, REPLAY_DEF + " Collect wall time, all 960 games (meta_end batch_wall_s).", n=1)
    M[f"{pre}.games_ok"] = met(d["games"] - d["errors"], "games", DIAG_SRC, REPLAY_DEF + " Games without an error row.", n=1)
    M[f"{pre}.others_done_s"] = met(round(d["others_done"], 1), "s", DIAG_SRC, REPLAY_DEF + " Time from batch start until the last of the 959 games other than submission 703 has finished.", n=1)
    M[f"{pre}.game703_wall_s"] = met(round(d["g703_wall"], 1), "s", DIAG_SRC, REPLAY_DEF + " Wall time of game 703 (seed 10,000,703, A4) inside the pool.", n=1)
    M[f"{pre}.game703_start_s"] = met(round(d["g703_start"], 1), "s", DIAG_SRC, REPLAY_DEF + " When game 703 starts (s after batch start).", n=1)
    M[f"{pre}.game703_alone_s"] = met(round(d["g703_alone"], 1), "s", DIAG_SRC, REPLAY_DEF + " Final stretch of collection with only game 703 running (collect_s - others_done_s).", n=1)
    M[f"{pre}.game703_alone_share"] = met(round(d["g703_alone"] / d["collect"], 3), "fraction", DIAG_SRC, REPLAY_DEF + " Share of collect_s that is game 703 running alone.", n=1)
    M[f"{pre}.total_cpu_s"] = met(round(d["tot_cpu"]), "CPU s", DIAG_SRC, REPLAY_DEF + " Sum of per-game CPU over all 960 games.", n=960)
    M[f"{pre}.median_cpu_s_per_game"] = met(round(d["med_cpu"], 2), "CPU s/game", DIAG_SRC, REPLAY_DEF + " Median per-game CPU.", n=960)
    tp = f"training.typical.{arm}"
    TDEF = (REPLAY_DEF + " TYPICAL ITERATION = iteration 0 without the one outlier game: others_done_s + the trainer's "
            "measured train_s for this config. Derived estimate (outside the quiet-gated window, one replay per "
            "config), not a trainer measurement; game 703 still occupies one of the 46 workers from its start.")
    M[f"{tp}.collect_s"] = met(round(d["others_done"], 1), "s", DIAG_SRC, TDEF + " Collection part.", n=1)
    M[f"{tp}.iter_s"] = met(round(d["typical_iter"], 1), "s", DIAG_SRC, TDEF, n=1)
    M[f"{tp}.campaign_150_iters_h"] = met(round(d["typical_150_h"], 2), "hours", DIAG_SRC,
                                          TDEF + " Projection: 150 x iter_s (one campaign leg); ignores rare "
                                          "outlier iterations (2.8% of past Ironclad iterations hit the watchdog).", n=1)
    M[f"{tp}.games_per_hour"] = met(round(d["typical_games_per_h"]), "games/hour", DIAG_SRC, TDEF + " 959 games / collect_s x 3600.", n=1)
    pcarm = {"base": "base", "new": "new", "pgo": "pgo"}[a]
    rph = 6 * 3600 / pc[pcarm]
    M[f"training.whole_vs_one_core.typical.{arm}"] = met(
        round(d["typical_games_per_h"] / rph, 2), "x", DIAG_SRC + [f"training/percore/{pcarm}-n6-*.perf"],
        "INDICATIVE: typical-iteration games/hour (959 games, 46 workers on 24 cores) / one pinned worker's "
        "runs/hour (training.percore). Different games, so only indicative. Replaces the tail-bound "
        "training.whole_vs_one_core.* for interpretation.")
    M[f"training.diag.single_game703.{arm}.wall_s"] = met(
        round(single[a], 1), "s",
        ["training/diag/out/base-a4.jsonl" if a == "base" else f"training/diag/out/{a}-s703.jsonl"],
        "Game 703 (seed 10,000,703, A4) played on its own (previous: inside an 85-game A4 replay, the last one "
        "running); wall time.", n=1)

for a in ("base", "pgo"):
    arm = armmap[a]
    M[f"training.diag.trace703.{arm}.game_s"] = met(round(trace[a][0], 1), "s", [f"training/diag/out/trace-{a}-s703.jsonl"], "Per-step trace of game 703: total step wall time.", n=1)
    M[f"training.diag.trace703.{arm}.longest_step_s"] = met(round(trace[a][1], 1), "s", [f"training/diag/out/trace-{a}-s703.jsonl"], "Per-step trace of game 703: the single longest run-level step (the floor-16 Act-1 boss fight per the diag agent).", n=1)

M["training.full.base-plain.same_work_collect_s"] = met(
    round(D["base"]["collect"], 1), "s", DIAG_SRC,
    REPLAY_DEF + " Previous engine, the same 960-game work NOT cut off by the watchdog.", n=1)
M["training.full.base-plain.same_work_iter_s"] = met(
    round(D["base"]["collect"] + train_s["base-plain"], 1), "s", DIAG_SRC,
    REPLAY_DEF + " Previous engine, same-work iteration estimate = replay collect + trainer train_s.", n=1)
M["training.diag.replay_validation"] = met(
    {"new-plain": {"replay_collect_s": round(D["new"]["collect"], 1), "trainer_collect_s": [r["collect_s"] for r in tr["new-plain"]]},
     "new-pgo": {"replay_collect_s": round(D["pgo"]["collect"], 1), "trainer_collect_s": [r["collect_s"] for r in tr["new-pgo"]]},
     "base-plain": {"replay_watchdog_fire_s": round(D["base"]["others_done"] + 600, 1),
                    "trainer_collect_s": [r["collect_s"] for r in tr["base-plain"]]}},
    "s", DIAG_SRC, "How well the diag replay reproduces the trainer runs.")

def sp(num, den):
    return round(num / den, 3)

for name, num, den in (("pgo_vs_base", "base", "pgo"), ("new_vs_base", "base", "new"), ("pgo_vs_new", "new", "pgo")):
    M[f"training.typical.speedup.{name}.iter_s"] = met(sp(D[num]["typical_iter"], D[den]["typical_iter"]), "x", DIAG_SRC, "Typical-iteration ratio (iteration 0 without game 703).")
    M[f"training.typical.speedup.{name}.collect_s"] = met(sp(D[num]["others_done"], D[den]["others_done"]), "x", DIAG_SRC, "Typical-collection ratio (the other 959 games).")
    M[f"training.diag.speedup.{name}.total_cpu"] = met(sp(D[num]["tot_cpu"], D[den]["tot_cpu"]), "x", DIAG_SRC, "Ratio of total per-game CPU over all 960 games (replay).")
    M[f"training.diag.speedup.{name}.median_cpu_per_game"] = met(sp(D[num]["med_cpu"], D[den]["med_cpu"]), "x", DIAG_SRC, "Ratio of median per-game CPU (replay).")
    M[f"training.diag.speedup.{name}.same_work_collect"] = met(sp(D[num]["collect"], D[den]["collect"]), "x", DIAG_SRC, "Same-work iteration-0 collect ratio, nothing cut off (inflated by the outlier game, which the new engine speeds up more than a typical game).")
M["training.typical.campaign_hours_saved_pgo_vs_previous"] = met(
    round(D["base"]["typical_150_h"] - D["pgo"]["typical_150_h"], 2), "hours", DIAG_SRC,
    "150 x (typical previous iter_s - typical new-PGO iter_s) / 3600.")

# hint whole-machine-best note
k200 = "ours.scale.hint.new.whole_machine_best.sims200.searches_per_s"
if "phys24" not in M[k200]["definition"]:
    M[k200]["definition"] += (" Note: the preset is chosen at 1,000 sims; at 200 sims phys24 is higher "
                              "(8,177 searches/s).")

# xsim whole-machine best vs best
bo = best_ours
for eng, key in (("sansb", "w1-sansb"), ("sts2core", "w1-sts2core")):
    be = max(scale[(key, "phys24")], scale[(key, "smt48")])
    M[f"xsim.scale.w1.ratio.whole_machine_best.ours_over_{eng}"] = met(
        round(bo / be, 4), "x", ["runs/ours/scale/w1-new/r*/{phys24,smt48}/*.out", f"runs/xsim/scale/{key}/r*/{{phys24,smt48}}/*.out"],
        f"Whole machine, best preset each side (ours smt48; {eng} phys24): ours aggregate actions/s / {eng} aggregate actions/s, W1 (instance policy for {eng}). Same-host, different times.")

# ---- headline wording ------------------------------------------------------------------
t = D
for h in R["headline"]:
    if h["id"] == "training":
        h["keys"] = ["training.typical.new-pgo.iter_s", "training.typical.base-plain.iter_s",
                     "training.typical.speedup.pgo_vs_base.iter_s", "training.typical.new-pgo.campaign_150_iters_h",
                     "training.typical.base-plain.campaign_150_iters_h", "training.diag.speedup.pgo_vs_base.total_cpu",
                     "training.percore.speedup.pgo_vs_base.ratio_of_medians", "training.previous_caveat"]
        h["wording"] = (
            "Training (deployed Ironclad recipe: 960 games, 46 workers, one RTX 3090): a typical iteration takes "
            f"{t['pgo']['typical_iter']:.0f} s instead of {t['base']['typical_iter']:.0f} s "
            f"({t['base']['typical_iter'] / t['pgo']['typical_iter']:.2f}x; total CPU "
            f"{t['base']['tot_cpu'] / t['pgo']['tot_cpu']:.2f}x, one core "
            f"{pc['base'] / pc['pgo']:.2f}x), so a 150-iteration leg projects to about "
            f"{t['pgo']['typical_150_h']:.0f} h instead of about {t['base']['typical_150_h']:.0f} h. "
            "'Typical' = iteration 0 without its one outlier game. Iteration 0 as the trainer ran it took "
            "416 s vs 1,213 s, but about half of it is that one game, and the previous engine's 1,213 s is a lower "
            "bound: its stall watchdog dropped the game after 600 s (959 of 960 games)."
        )
    if h["id"] == "ours_whole_machine":
        h["wording"] = (
            "Whole machine (smt48, 48 processes): 1,409,466 fights/s = 31.0 M actions/s, 23.5x one core in the "
            "same scaling series (whose one-core run did 1.32 M actions/s; 23.7x the 1.30 M one-core figure above) "
            "(phys24 efficiency 95%; SMT adds 3%)."
        )
    if h["id"] == "retire_23000":
        h["keys"] = ["ratio.one_process_wall.cold_A0", "ratio.one_process_wall.warm_A", "cs.A0.process_cpu_ms_per_action",
                     "cs.A.process_cpu_ms_per_action", "cs.A.cpu_util_during_play"]
        h["wording"] = (
            "Retire '23,000x': it compared the wall time of ONE cold game process per fight (17.9 ms/action, now "
            "23,409x). Of those 17.9 ms, about 2.3 ms is warm game CPU, about 7.8 ms is extra first-fight "
            "(JIT/cold-start) CPU and about 7.8 ms is idle time in the harness's 1 ms polling sleeps. A warm "
            "process gives 13,525x (10.4 ms/action, 78% of it asleep). Use the saturated and CPU-time ratios instead."
        )
M["cs.A0.wall_decomposition_ms"] = met(
    {"warm_cpu_ms": round(cs["A"]["cpu_ms"], 2), "extra_cold_cpu_ms": round(a0_cpu_ms - cs["A"]["cpu_ms"], 2),
     "not_on_cpu_ms": round(a0_wall_ms - a0_cpu_ms, 2), "wall_ms": round(a0_wall_ms, 2)},
    "ms/action", ["csharp/runs/A0.jsonl", "csharp/runs/A/worker-00.jsonl"],
    "Decomposition of the cold one-process wall time per action (cell A0): warm game-process CPU (cell A) + extra "
    "cold-start CPU (A0 CPU - A CPU) + time not on CPU (A0 wall - A0 CPU; the harness's Thread.Sleep(1) polling). "
    "A0 and A fights are different subsets of the same seeds, so the split is approximate.")

# ---- flags ---------------------------------------------------------------------------
F = R["flags"]
def replace_flag(prefix, new):
    for i, f in enumerate(F):
        if isinstance(f, str) and f.startswith(prefix):
            F[i] = new
            return
    if new not in F:
        F.append(new)

replace_flag(
    "Training: the previous engine's two full-iteration runs each report errors=1",
    "Training: the previous engine's two full-iteration runs each report errors=1 (959 of 960 games): the trainer's "
    "600 s stall watchdog dropped one legitimately long game (submission 703, seed 10,000,703, A4, a loss at floor "
    "31), so its iteration-0 time is a LOWER BOUND for the same work and its fingerprint differs from the new "
    "engine's only through that game. Explained and re-derived in training.previous_caveat (diag replay).")
replace_flag(
    "Training: measured previous-engine collect (~1179 s)",
    "Training: measured previous-engine collect (~1179 s) vs the Sept-2026 champion leg's median (500.5 s): "
    "EXPLAINED by the diag replay. 600 s of it is the watchdog wait; the other 959 games finished at ~575-580 s. "
    "util_collect ~0.5 in every config is the same outlier game running alone for ~55% of collection.")
replace_flag(
    "Training (diag replay):",
    "Training (diag replay): training.typical.* and training.diag.* come from the diag agent's no-watchdog "
    "replays, run 2026-10-14 16:38-18:10 UTC, AFTER the quiet-gated window, one full replay at a time (host ~2% "
    "busy before each, 15-min load still 22-26 from an earlier job). They reproduce the trainer's new-engine "
    "collect within 0.5% (533.7 vs 535.4/537.6 s; 379.7 vs 378.8/383.5 s) and predict the previous engine's "
    "watchdog cut at ~1,175 s (trainer 1,177/1,180 s), but each is a single replay, not a gated measurement. "
    "'Typical iteration' still has game 703 occupying one of the 46 workers from its start, and ignores the rare "
    "outlier iterations a real campaign contains (2.8% hit the watchdog in past Ironclad campaigns).")
replace_flag(
    "Training per-core numbers:",
    "Training per-core numbers: the diag agent quoted 2.47x / 3.21x for the one-core benchmark; the raw perf "
    "files give 2.46x / 3.16x (ratio of medians of the 3 task-clock reps; 2.46x / 3.14x as the median rep "
    "ratio). The results here use the raw-file values.")
replace_flag(
    "Hint scale: the whole-machine-best preset",
    "Hint scale: the whole-machine-best preset is picked at 1,000 sims (smt48); at 200 sims phys24 is higher "
    "(8,177 vs 7,802 searches/s).")

# ---- changelog + xcheck record ---------------------------------------------------------
R["xcheck"] = {
    "script": "scripts/site-bench/bench/results/apply_xcheck.py (independent of analyze.py)",
    "re_derived_metrics": len(cmp_rows),
    "max_abs_rel_diff": round(worst, 6),
    "rows": cmp_rows,
    "diag_rederived": {armmap[a]: {k: (round(v, 2) if isinstance(v, float) else v) for k, v in D[a].items()} for a in D},
}
R["changelog"] = [
    "xcheck 2026-10-14: every headline number re-derived from the raw files with independent parsing "
    f"({len(cmp_rows)} metrics; max relative difference {worst:.2e}, all from rounding of printed rates).",
    "training.previous_caveat filled from the diag agent's conclusion, re-derived from training/diag/out/*: the "
    "previous engine's iteration 0 was cut by the 600 s stall watchdog (959/960 games).",
    "training.full.base-plain.{collect_s,iter_s,games_per_hour,games_per_hour_per_core,wall_s} labelled LOWER "
    "BOUND; training.full.*.campaign_150_iters_h labelled 'not a projection'; base-plain speed-ups labelled "
    "'not same-work'.",
    "Added training.typical.* (iteration 0 without the outlier game), training.diag.*, "
    "training.full.base-plain.same_work_*, training.whole_vs_one_core.typical.*.",
    "Headline 'training' rewritten on training.typical.*: 208 s vs 609 s (2.93x), ~9 h vs ~25 h per 150 "
    "iterations; the old '416 s vs 1213 s ... 17 h instead of 51 h' compared a truncated previous iteration and "
    "multiplied a one-outlier iteration by 150.",
    "Headline 'retire_23000' corrected: the cold 17.9 ms/action is ~2.3 ms warm CPU + ~7.8 ms cold-start CPU + "
    "~7.8 ms polling sleep, not 'mostly' polling sleeps; added cs.A0.wall_decomposition_ms.",
    "Flags: two training flags rewritten (explained), replay-window flag and hint-preset flag added.",
    "Added xsim.scale.w1.ratio.whole_machine_best.ours_over_{sansb,sts2core} (best preset each side).",
    "Hint scale: whole_machine_best.sims200 definition notes phys24 is higher at 200 sims; flag added.",
    "Headline 'ours_whole_machine': '23.5x one core' now says it is relative to the scaling series' own one-core "
    "run (1.32 M actions/s); against the 1.30 M headline one-core figure it is 23.7x.",
]

json.dump(R, open(RJ, "w"), indent=1, ensure_ascii=False)
print("re-derived", len(cmp_rows), "metrics; max |rel diff|", worst)
for r in cmp_rows:
    if r["rel_diff"] is None or abs(r["rel_diff"]) > 1e-4:
        print("  DIFF", r)
print("typical:", {armmap[a]: (round(D[a]["others_done"], 1), round(D[a]["typical_iter"], 1), round(D[a]["typical_150_h"], 2), round(D[a]["typical_games_per_h"])) for a in D})
print("single703:", single, "trace:", trace)
for h in R["headline"]:
    if h["id"] in ("training", "retire_23000"):
        print(h["id"], "::", h["wording"])
for k in ("xsim.scale.w1.ratio.whole_machine_best.ours_over_sansb", "xsim.scale.w1.ratio.whole_machine_best.ours_over_sts2core",
          "training.whole_vs_one_core.typical.base-plain", "training.whole_vs_one_core.typical.new-plain",
          "training.whole_vs_one_core.typical.new-pgo", "training.typical.campaign_hours_saved_pgo_vs_previous",
          "cs.A0.wall_decomposition_ms"):
    print(k, M[k]["value"])
for k in [k for k in M if k.startswith("training.typical.speedup") or k.startswith("training.diag.speedup")]:
    print(k, M[k]["value"])
