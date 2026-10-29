"""The shipped game (C#, headless) on the random-fight workload, many fights per
process, on one core or on the whole machine.

Same fights as `cs_random_fights.py` (and as the Rust `site_random_bench`):
Ironclad A0, the 10-card starter deck at 80/80 HP plus Burning Blood, seed
`SITE<s>` fights Act-1 Overgrowth encounter `s % 22` (the same 22 ids, same
order), uniformly random legal moves (`legal_actions`, the game's own CanPlay
filter) until the fight leaves combat. The difference: a game process plays
MANY fights instead of one, so its JIT and caches stay warm.

How one process replays a fight from an identical start (verified, see below):

  prep    a FRESH process per seed: start_run(SITE<s>, played_save, overgrowth)
          -> write_continue_save (the run on the map, before any room) ->
          enter_room -> get_state digest = the seed's reference start state.
  reset   (untimed, every fight) load_save(seed save) ; select_map_node(the
          map's first node) ; load_save(seed save) ; enter_room(encounter).
          `load_save` is the harness's own teardown + reload (RunManager.CleanUp
          then a new RunState from the save). The `select_map_node` is there
          only for its side effect: it is the one command that clears the
          harness's reward bookkeeping (`_rewardsProcessed`, `_pendingCardReward`,
          `_pendingRewards`), which `load_save` keeps. Without it the
          previous fight's card reward answers every later command, and a
          later won fight skips reward generation. The room it enters is thrown
          away by the second `load_save`.
  check   get_state after the reset must equal the seed's fresh digest (the
          whole start state incl. RNG counters, hand, draw order, monsters);
          the seed-independent setup fingerprint (deck multiset, 80/80 HP,
          energy, relics) must be the same for every fight.
  play    timed exactly as in cs_random_fights.py: first legal_actions call ..
          the response that leaves combat_play.

The game is deterministic given the moves, so fight (s, rep 0) here has the
same length and outcome as fight i=s of `cs_random_fights.py`; `--ref-jsonl`
compares them, and the run digest (FNV-1a over (steps<<1|won) of all timed
fights in global order, the Rust harness's fold) is independent of how the
fights are split over workers.

Modes

  pool    the orchestrator: prep missing saves (untimed, parallel), start N
          workers on their CPU sets, release them together (barrier), collect.
          --workers 1 --cpu-sets 20,44          one process on one physical core
          --workers 24 --pin core               one process per physical core
          --workers 48 --pin thread             one process per hardware thread
          --workers 48 --pin none               unpinned
  worker  one game process: boot, optional warm-up fights (the first W of its
          own list, replayed later in the timed pass), READY, wait for GO, play
          K timed fights. Writes one row per fight; last stdout line is JSON.
  prep    only build the saves.

Each fight row records play wall time, steps, outcome, the game process's CPU
time (all threads) and its game thread's CPU time and context switches, the
.NET tiered-compilation thread's CPU time (background JIT), the driver's own
CPU time, reset time, RSS. `k` is the fight's index within its process (warm-up
fights included), so ms/action against `k` is the JIT warm-up curve.

  STS2_HEADLESS_BIN=<release build>/Sts2Headless STS2_GAME_DIR=<game assemblies> \\
  python -I scripts/site-bench/cs_fights_pool.py pool --pycli sts2-pycli \\
      --saves <dir> --out-dir <dir> --workers 24 --pin core --per-worker 44 --warmup 22

Linux only (/proc, sched_setaffinity). Python stdlib only plus the pycli's
`python/sts2_client.py`.
"""

import argparse
import concurrent.futures
import hashlib
import json
import os
import random
import statistics
import subprocess
import sys
import threading
import time

# sts2-rs crates/sts2-content/src/encounters/act1_overgrowth.rs (ACT1_ENCOUNTERS),
# the same table and order as cs_random_fights.py.
ENCOUNTERS = {
    "weak": ["SHRINKER_BEETLE_WEAK", "FUZZY_WURM_CRAWLER_WEAK", "NIBBITS_WEAK", "SLIMES_WEAK"],
    "normal": [
        "CUBEX_CONSTRUCT_NORMAL", "FLYCONID_NORMAL", "FOGMOG_NORMAL", "INKLETS_NORMAL",
        "MAWLER_NORMAL", "NIBBITS_NORMAL", "OVERGROWTH_CRAWLERS", "RUBY_RAIDERS_NORMAL",
        "SLIMES_NORMAL", "SLITHERING_STRANGLER_NORMAL", "SNAPPING_JAXFRUIT_NORMAL",
        "VINE_SHAMBLER_NORMAL",
    ],
    "elite": ["BYGONE_EFFIGY_ELITE", "BYRDONIS_ELITE", "PHROG_PARASITE_ELITE"],
    "boss": ["VANTOM_BOSS", "CEREMONIAL_BEAST_BOSS", "THE_KIN_BOSS"],
}
ENCS = [e for es in ENCOUNTERS.values() for e in es]
TIER = {e: t for t, es in ENCOUNTERS.items() for e in es}
CLK_TCK = os.sysconf("SC_CLK_TCK")
STEP_CAP = 3000
# Harness log lines that mean a fight did not run cleanly (EndTurn deadlock
# recovery re-runs the enemy turn; a timed-out wait changes timing).
WATCH_RE = r"stuck|Nuclear|timed out|\[FATAL\]"
SCHEMA = "cs_fights_pool/1"


# ----------------------------------------------------------------------------- /proc


def expand_cpus(spec):
    out = []
    for part in str(spec).split(","):
        part = part.strip()
        if not part:
            continue
        if "-" in part:
            a, b = part.split("-")
            out.extend(range(int(a), int(b) + 1))
        else:
            out.append(int(part))
    return out


def siblings(cpu):
    try:
        with open(f"/sys/devices/system/cpu/cpu{cpu}/topology/thread_siblings_list") as fh:
            return sorted(expand_cpus(fh.read()))
    except OSError:
        return [cpu]


def physical_cores():
    """One representative (lowest) CPU id per physical core, in order."""
    seen, reps = set(), []
    for cpu in sorted(os.sched_getaffinity(0) | set(range(os.cpu_count() or 1))):
        if not os.path.exists(f"/sys/devices/system/cpu/cpu{cpu}"):
            continue
        sib = tuple(siblings(cpu))
        if sib not in seen:
            seen.add(sib)
            reps.append(sib[0])
    return reps


def stat_fields(path):
    with open(path) as fh:
        return fh.read().rsplit(")", 1)[1].split()


def cpu_s(pid, tid=None):
    """utime+stime of a process (all threads) or one thread, seconds."""
    try:
        f = stat_fields(f"/proc/{pid}/stat" if tid is None else f"/proc/{pid}/task/{tid}/stat")
        return (int(f[11]) + int(f[12])) / CLK_TCK
    except (OSError, IndexError, ValueError):
        return None


def status(pid, tid=None):
    out = {}
    try:
        p = f"/proc/{pid}/status" if tid is None else f"/proc/{pid}/task/{tid}/status"
        with open(p) as fh:
            for line in fh:
                k, _, v = line.partition(":")
                out[k] = v.strip()
    except OSError:
        pass
    return out


def schedstat(pid, tid):
    """(ns on CPU, ns waiting on a run queue) of one thread, from /proc/.../schedstat."""
    try:
        with open(f"/proc/{pid}/task/{tid}/schedstat") as fh:
            f = fh.read().split()
        return int(f[0]), int(f[1])
    except (OSError, IndexError, ValueError):
        return None, None


def find_threads(pid):
    """{comm: tid} of the game process's threads (comm is cut at 15 chars)."""
    out = {}
    try:
        for tid in os.listdir(f"/proc/{pid}/task"):
            try:
                with open(f"/proc/{pid}/task/{tid}/comm") as fh:
                    out.setdefault(fh.read().strip(), int(tid))
            except OSError:
                pass
    except OSError:
        pass
    return out


def kb(s):
    try:
        return int(s.split()[0])
    except (AttributeError, IndexError, ValueError):
        return None


def proc_stat():
    """{cpu: (total, idle)} plus 'all'."""
    out = {}
    with open("/proc/stat") as fh:
        for line in fh:
            if not line.startswith("cpu"):
                break
            f = line.split()
            vals = [int(x) for x in f[1:9]]
            key = "all" if f[0] == "cpu" else int(f[0][3:])
            out[key] = (sum(vals), vals[3] + vals[4])
    return out


def busy_pct(a, b, cpus=None):
    keys = ["all"] if cpus is None else list(cpus)
    tot = sum(b[k][0] - a[k][0] for k in keys if k in a and k in b)
    idle = sum(b[k][1] - a[k][1] for k in keys if k in a and k in b)
    return round(100.0 * (tot - idle) / tot, 2) if tot > 0 else None


def freq_mhz(cpus):
    """Mean scaling_cur_freq of the CPUs, MHz (None when cpufreq is absent)."""
    vals = []
    for c in cpus:
        try:
            with open(f"/sys/devices/system/cpu/cpu{c}/cpufreq/scaling_cur_freq") as fh:
                vals.append(int(fh.read()) / 1000)
        except (OSError, ValueError):
            pass
    return sum(vals) / len(vals) if vals else None


def md5(path):
    try:
        h = hashlib.md5()
        with open(path, "rb") as fh:
            for chunk in iter(lambda: fh.read(1 << 20), b""):
                h.update(chunk)
        return h.hexdigest()
    except OSError:
        return None


def binary_provenance():
    exe = os.environ.get("STS2_HEADLESS_BIN")
    gd = os.environ.get("STS2_GAME_DIR")
    d = os.path.dirname(exe) if exe else None
    # Program.cs resolves the game assemblies from <exe dir>/lib first.
    dll = os.path.join(d, "lib", "sts2.dll") if d and os.path.exists(os.path.join(d, "lib", "sts2.dll")) else (
        os.path.join(gd, "sts2.dll") if gd else None)
    return {
        "headless_bin": exe,
        "headless_dll_md5": md5(os.path.join(d, "Sts2Headless.dll")) if d else None,
        "sts2_dll": dll,
        "sts2_dll_md5": md5(dll) if dll else None,
        "game_dir": gd,
        "dotnet_env": {k: v for k, v in os.environ.items() if k.startswith(("DOTNET_", "COMPlus_"))},
    }


# ----------------------------------------------------------------------------- state


def digest_state(state):
    return hashlib.sha256(json.dumps(state, sort_keys=True).encode()).hexdigest()[:16]


def setup_fp(state, resp=None):
    """Seed-independent start fingerprint: identical for every fight of the workload."""
    p = state.get("player") or {}
    deck = sorted((state.get("hand_ids") or []) + (state.get("draw_ids") or [])
                  + (state.get("discard_ids") or []))
    fp = {
        "hp": p.get("hp"), "max_hp": p.get("max_hp"), "block": p.get("block"),
        "energy": p.get("energy"), "powers": p.get("powers"), "deck": deck,
        "exhaust": state.get("exhaust_ids"), "relics": state.get("relics"), "turn": state.get("turn"),
    }
    if resp and isinstance(resp.get("player"), dict):
        fp["potions"] = resp["player"].get("potions")
        fp["gold"] = resp["player"].get("gold")
    return fp, hashlib.sha256(json.dumps(fp, sort_keys=True).encode()).hexdigest()[:12]


def fnv_fold(rows):
    d = 0xCBF29CE484222325
    for r in rows:
        d = ((d ^ ((int(r["steps"]) << 1) | int(bool(r["won"])))) * 0x100000001B3) & 0xFFFFFFFFFFFFFFFF
    return f"{d:016x}"


def fight_plan(w, n_workers, per_worker, seed_start, reps_per_seed):
    """Worker w's timed fights. Global fight g = j*N + w, so the N*K fights are the
    same set however they are split; with R=1 fight g is cs_random_fights' i=g."""
    plan = []
    for j in range(per_worker):
        g = j * n_workers + w
        s = seed_start + g // reps_per_seed
        rep = g % reps_per_seed
        plan.append({"g": g, "s": s, "rep": rep, "enc": ENCS[s % len(ENCS)],
                     "rng": 1_000_003 * s + 17 + 7_919 * rep})
    return plan


# ----------------------------------------------------------------------------- prep


def import_game(pycli):
    sys.path.insert(0, os.path.join(pycli, "python"))
    from sts2_client import Game  # noqa: E402

    return Game


def manifest_path(saves):
    return os.path.join(saves, "manifest.jsonl")


def read_manifest(saves):
    out = {}
    try:
        with open(manifest_path(saves)) as fh:
            for line in fh:
                if line.strip():
                    m = json.loads(line)
                    out[m["s"]] = m
    except OSError:
        pass
    return out


def prep_one(Game, saves, s, timeout_s):
    seed = f"SITE{s}"
    enc = ENCS[s % len(ENCS)]
    path = os.path.join(saves, f"{seed}.json")
    row = {"s": s, "seed": seed, "enc": enc, "save": os.path.basename(path)}
    t0 = time.perf_counter()
    g = Game()
    timer = threading.Timer(timeout_s, g.proc.kill)
    timer.start()
    try:
        st = g.start("Ironclad", seed=seed, ascension=0, played_save=True, act1="overgrowth")
        if st.get("decision") != "map_select":
            row["error"] = f"start_run -> {st.get('decision')}"
            return row
        choices = st.get("choices") or []
        if not choices:
            row["error"] = "map_select without choices"
            return row
        row["node"] = {"col": choices[0]["col"], "row": choices[0]["row"], "type": choices[0].get("type")}
        tmp = path + ".tmp"
        r = g.write_continue_save(tmp)
        if not r.get("success"):
            row["error"] = f"write_continue_save -> {json.dumps(r)[:200]}"
            return row
        os.replace(tmp, path)
        resp = g.enter_room("combat", encounter=enc)
        if resp.get("decision") != "combat_play":
            row["error"] = f"enter_room -> {resp.get('decision')}"
            return row
        state = g.get_state()
        row["fresh_digest"] = digest_state(state)
        row["setup_fp"] = setup_fp(state, resp)[1]
        row["monsters"] = [m.get("kind") for m in state.get("monsters") or []]
        row["save_md5"] = md5(path)
        row["prep_s"] = round(time.perf_counter() - t0, 3)
    except Exception as e:  # noqa: BLE001
        row["error"] = repr(e)[:300]
    finally:
        timer.cancel()
        try:
            g.close()
        except Exception:  # noqa: BLE001
            pass
    return row


def prep(pycli, saves, seeds, jobs, timeout_s, log=print):
    """Build the missing seed saves in fresh processes (untimed). Returns the manifest."""
    os.makedirs(saves, exist_ok=True)
    prov = binary_provenance()
    man = read_manifest(saves)
    stale = [s for s, m in man.items() if m.get("sts2_dll_md5") != prov["sts2_dll_md5"]]
    if stale:
        raise SystemExit(f"saves in {saves} were made with another sts2.dll ({len(stale)} seeds); "
                         "use another --saves dir")
    todo = [s for s in seeds if s not in man or "error" in man[s]]
    if not todo:
        return man
    Game = import_game(pycli)
    log(f"prep: {len(todo)} seed saves, {jobs} at a time")
    t0 = time.perf_counter()
    with open(manifest_path(saves), "a") as fh, concurrent.futures.ThreadPoolExecutor(jobs) as ex:
        for row in ex.map(lambda s: prep_one(Game, saves, s, timeout_s), todo):
            row["sts2_dll_md5"] = prov["sts2_dll_md5"]
            fh.write(json.dumps(row) + "\n")
            fh.flush()
            man[row["s"]] = row
    bad = [s for s in todo if "error" in man[s]]
    log(f"prep: done in {time.perf_counter() - t0:.1f} s, {len(bad)} failed {bad[:10]}")
    return man


# ----------------------------------------------------------------------------- worker


class Proc:
    """A booted game process plus the /proc handles the rows need."""

    def __init__(self, Game):
        t0 = time.perf_counter()
        self.g = Game(watch_stderr=WATCH_RE)
        self.boot_s = time.perf_counter() - t0
        self.pid = self.g.proc.pid
        self.gt = find_threads(self.pid).get("STS2-GameThread")

    def snap(self):
        if self.gt is None:
            self.gt = find_threads(self.pid).get("STS2-GameThread")
        gst = status(self.pid, self.gt) if self.gt else {}
        # The tiered-compilation worker (background tier-1 JIT) exits when idle
        # and comes back with a new tid, so it is looked up every time and a
        # per-fight delta is kept only when both ends saw the same thread.
        # Thread times come from schedstat (ns); the process total from
        # /proc/<pid>/stat (clock ticks, 10 ms, but it keeps exited threads).
        jit = find_threads(self.pid).get(".NET Tiered Com")
        gt_run, gt_wait = schedstat(self.pid, self.gt) if self.gt else (None, None)
        jit_run = schedstat(self.pid, jit)[0] if jit else None
        return {
            "cpu": cpu_s(self.pid),
            "gt_cpu": gt_run / 1e9 if gt_run is not None else None,
            "gt_rq_wait": gt_wait / 1e9 if gt_wait is not None else None,
            "jit_tid": jit,
            "jit_cpu": jit_run / 1e9 if jit_run is not None else None,
            "gt_vcsw": int(gst.get("voluntary_ctxt_switches", 0) or 0),
            "gt_nvcsw": int(gst.get("nonvoluntary_ctxt_switches", 0) or 0),
            "drv_cpu": time.process_time(),
        }

    def close(self):
        try:
            self.g.close()
        except Exception:  # noqa: BLE001
            pass


def delta(a, b, key):
    if a.get(key) is None or b.get(key) is None:
        return None
    return round(b[key] - a[key], 4)


def play_fight(P, f, save_text, man_row, timeout_s, k, phase):
    g = P.g
    row = {"g": f["g"], "s": f["s"], "rep": f["rep"], "encounter": f["enc"], "tier": TIER[f["enc"]],
           "k": k, "phase": phase, "pid": P.pid}
    timer = threading.Timer(timeout_s, g.proc.kill)
    timer.start()
    try:
        watch0 = len(g.stderr_watch())
        err0 = g.world_errors()["count"]
        tr = time.perf_counter()
        r1 = g.load_save(json_str=save_text)
        t1 = time.perf_counter()
        node = man_row["node"]
        r2 = g.act("select_map_node", col=node["col"], row=node["row"])
        t2 = time.perf_counter()
        r3 = g.load_save(json_str=save_text)
        t3 = time.perf_counter()
        resp = g.enter_room("combat", encounter=f["enc"])
        t4 = time.perf_counter()
        row["reset_s"] = round(t4 - tr, 5)
        row["reset_parts_s"] = [round(t1 - tr, 5), round(t2 - t1, 5), round(t3 - t2, 5), round(t4 - t3, 5)]
        row["reset_decisions"] = [r1.get("decision") or r1.get("type"), r2.get("decision") or r2.get("type"),
                                  r3.get("decision") or r3.get("type")]
        # r2 only has to have run DoMapSelect (it resets the flags before it moves);
        # the act-1 first node is a Monster room for every seed checked, so r2 is
        # normally combat_play. r3 must be the seed's map again.
        if r3.get("decision") != "map_select" or r2.get("type") == "error":
            row["anomaly"] = f"reset path {row['reset_decisions']}"
        if resp.get("decision") != "combat_play":
            row["error"] = f"enter_room -> {json.dumps(resp)[:300]}"
            return row
        state = g.get_state()
        row["start_digest"] = digest_state(state)
        row["start_same"] = row["start_digest"] == man_row.get("fresh_digest")
        row["setup_fp"] = setup_fp(state, resp)[1]
        row["monsters"] = [m.get("kind") for m in state.get("monsters") or []]
        if not row["start_same"]:
            row["anomaly"] = "start state differs from the seed's fresh start state"
        rng = random.Random(f["rng"])
        steps = 0
        la_s = 0.0
        s0 = P.snap()
        row["t_start"] = time.time()
        t0 = time.perf_counter()
        while resp.get("decision") == "combat_play" and steps < STEP_CAP:
            t_la = time.perf_counter()
            acts = (g.legal_actions() or {}).get("actions") or []
            la_s += time.perf_counter() - t_la
            if not acts:
                row["anomaly"] = "no legal actions"
                break
            a = rng.choice(acts)
            name = a.get("action")
            if name == "end_turn":
                resp = g.end_turn()
            elif name == "play_card":
                resp = g.play_card(a["card_index"], a.get("target_index"))
            else:
                resp = g.act(name, **{kk: v for kk, v in a.items() if kk != "action"})
            steps += 1
            while resp.get("decision") == "card_select" and steps < STEP_CAP:
                n = max(1, int(resp.get("min_select", 1) or 1))
                resp = g.select_cards(",".join(str(j) for j in range(n)))
                steps += 1
        play_s = time.perf_counter() - t0
        row["t_end"] = time.time()
        s1 = P.snap()
        final = resp.get("decision")
        row.update(steps=steps, play_s=round(play_s, 5), la_s=round(la_s, 5), final=final,
                   won=final not in ("game_over", "combat_play", None))
        for key, name in (("cpu", "cpu_s"), ("gt_cpu", "gt_cpu_s"), ("gt_rq_wait", "gt_rq_wait_s"),
                          ("gt_vcsw", "gt_vcsw"), ("gt_nvcsw", "gt_nvcsw"), ("drv_cpu", "drv_cpu_s")):
            row[name] = delta(s0, s1, key)
        if s0["jit_tid"] is None and s1["jit_tid"] is not None:
            row["jit_cpu_s"] = s1["jit_cpu"]  # the worker started during the fight
        elif s0["jit_tid"] is not None and s0["jit_tid"] == s1["jit_tid"]:
            row["jit_cpu_s"] = delta(s0, s1, "jit_cpu")
        elif s0["jit_tid"] is None and s1["jit_tid"] is None:
            row["jit_cpu_s"] = 0.0  # no background JIT worker alive at either end
        else:
            row["jit_cpu_s"] = None  # the worker exited (and maybe restarted) mid-fight
        if row["cpu_s"] is not None and row["gt_cpu_s"] is not None:
            row["other_threads_cpu_s"] = round(row["cpu_s"] - row["gt_cpu_s"], 4)
        if steps:
            row["ms_per_action"] = round(1000 * play_s / steps, 3)
        if final is None:
            row["anomaly"] = f"no decision in response: {json.dumps(resp)[:200]}"
        elif final == "combat_play":
            row["anomaly"] = f"step cap {steps} reached mid-fight"
        st = status(P.pid)
        row["rss_kb"] = kb(st.get("VmRSS"))
        row["hwm_kb"] = kb(st.get("VmHWM"))
        row["threads"] = int(st.get("Threads", 0) or 0)
        hits = g.stderr_watch()[watch0:]
        if hits:
            row["harness_warnings"] = hits[:5]
        row["world_errors"] = g.world_errors()["count"] - err0
    except Exception as e:  # noqa: BLE001 - wedge (watchdog kill -> EOF) or harness error
        row["error"] = repr(e)[:300]
    finally:
        timer.cancel()
    return row


def ok_row(r):
    return "play_s" in r and "error" not in r and "anomaly" not in r


def summarize_rows(rows):
    ok = [r for r in rows if ok_row(r)]
    play = sum(r["play_s"] for r in ok)
    steps = sum(r["steps"] for r in ok)
    cpu_rows = [r for r in ok if r.get("cpu_s") is not None]
    cpu_play = sum(r["play_s"] for r in cpu_rows)

    def tot(key):
        v = [r[key] for r in cpu_rows if r.get(key) is not None]
        return sum(v) if v else None

    out = {
        "fights_ok": len(ok), "fights_failed": len(rows) - len(ok), "decisions": steps,
        "wins": sum(bool(r["won"]) for r in ok),
        "win_rate": round(sum(bool(r["won"]) for r in ok) / len(ok), 4) if ok else None,
        "play_s": round(play, 4),
        "decisions_per_s_playonly": round(steps / play, 3) if play else None,
        "fights_per_s_playonly": round(len(ok) / play, 4) if play else None,
        "ms_per_decision": round(1000 * play / steps, 3) if steps else None,
        "decisions_per_fight": round(steps / len(ok), 3) if ok else None,
        "reset_s_mean": round(statistics.mean(r["reset_s"] for r in ok), 4) if ok else None,
        # load_save / select_map_node / load_save / enter_room; the last two alone
        # are what a reload-and-enter would cost without the flag workaround.
        "reset_parts_s_mean": [round(statistics.mean(r["reset_parts_s"][i] for r in ok), 4) for i in range(4)]
        if ok else None,
        "legal_actions_share_of_play": round(sum(r["la_s"] for r in ok) / play, 4) if play else None,
    }
    if cpu_play and steps:
        # jit_thread = the .NET background tier-1 compiler (a lower bound when its
        # thread exited mid-fight); other_threads = everything but the game thread.
        for key, name in (("cpu_s", "cpu"), ("gt_cpu_s", "gamethread_cpu"),
                          ("other_threads_cpu_s", "other_threads_cpu"), ("jit_cpu_s", "jit_thread_cpu"),
                          ("drv_cpu_s", "driver_cpu")):
            t = tot(key)
            if t is not None:
                out[f"{name}_util_during_play"] = round(t / cpu_play, 4)
                out[f"{name}_ms_per_decision"] = round(1000 * t / steps, 3)
        v = tot("gt_rq_wait_s")
        if v is not None:  # time the game thread was runnable but waited for a CPU
            out["gamethread_runqueue_wait_ms_per_decision"] = round(1000 * v / steps, 3)
        v = tot("gt_vcsw")
        if v is not None:
            out["gamethread_voluntary_switches_per_decision"] = round(v / steps, 2)
        v = tot("gt_nvcsw")
        if v is not None:
            out["gamethread_involuntary_switches_per_decision"] = round(v / steps, 3)
    rss = [r["rss_kb"] for r in rows if r.get("rss_kb")]
    if rss:
        out["rss_mb_first"] = round(rss[0] / 1024, 1)
        out["rss_mb_last"] = round(rss[-1] / 1024, 1)
        out["rss_mb_max"] = round(max(rss) / 1024, 1)
    return out


def worker_main(a):
    cpus = expand_cpus(a.cpus) if a.cpus else None
    if cpus:
        os.sched_setaffinity(0, cpus)  # the game process inherits it
    plan = fight_plan(a.index, a.workers, a.per_worker, a.seed_start, a.reps_per_seed)
    warm = [dict(plan[j % len(plan)]) for j in range(a.warmup)] if plan else []
    man = read_manifest(a.saves)
    texts = {}
    for f in plan:
        m = man.get(f["s"])
        if not m or "error" in m:
            raise SystemExit(f"worker {a.index}: no usable save for seed {f['s']} in {a.saves} (run prep)")
        if f["s"] not in texts:
            with open(os.path.join(a.saves, m["save"])) as fh:
                texts[f["s"]] = fh.read()
    Game = import_game(a.pycli)
    out = open(a.out, "w")
    meta = {"meta": {"schema": SCHEMA, "worker": a.index, "workers": a.workers, "per_worker": a.per_worker,
                     "warmup": a.warmup, "seed_start": a.seed_start, "reps_per_seed": a.reps_per_seed,
                     "cpus": sorted(os.sched_getaffinity(0)), "pinned": bool(cpus),
                     "python": sys.version.split()[0], **binary_provenance()}}
    out.write(json.dumps(meta) + "\n")
    out.flush()
    procs = []

    def boot():
        """Boot a game process and initialise it (untimed): the first load_save
        runs the game's ModelDb initialisation (~0.75 s), which a fresh-process
        fight pays in its untimed setup too."""
        P = Proc(Game)
        f0 = plan[0]
        t0 = time.perf_counter()
        r = P.g.load_save(json_str=texts[f0["s"]])
        init_s = time.perf_counter() - t0
        if P.gt is None:  # the thread can still be unnamed right after "ready"
            P.gt = find_threads(P.pid).get("STS2-GameThread")
        st = status(P.pid)
        procs.append({"pid": P.pid, "boot_s": round(P.boot_s, 4), "init_s": round(init_s, 4),
                      "init_decision": r.get("decision") or r.get("type"), "gt_tid": P.gt,
                      "rss_kb_after_init": kb(st.get("VmRSS")), "threads_after_init": int(st.get("Threads", 0) or 0)})
        return P

    P = boot()
    rows, k = [], 0

    def run(f, phase):
        nonlocal P, k
        r = play_fight(P, f, texts[f["s"]], man[f["s"]], a.timeout, k, phase)
        k += 1
        rows.append(r)
        out.write(json.dumps(r) + "\n")
        out.flush()
        if "error" in r and P.g.proc.poll() is not None:  # wedged and killed: fresh process
            P.close()
            P = boot()
            k = 0

    for f in warm:
        run(f, "warmup")
    if a.barrier:
        print("READY " + json.dumps({"worker": a.index, "pid": P.pid}), flush=True)
        line = sys.stdin.readline().split()
        if not line or line[0] != "GO":
            raise SystemExit(f"worker {a.index}: expected GO, got {line}")
        t_go = float(line[1])
        while time.time() < t_go:
            time.sleep(min(0.002, max(0.0, t_go - time.time())))
    s_go = P.snap()
    t_go_wall = time.time()
    tg = time.perf_counter()
    for f in plan:
        run(f, "timed")
    wall = time.perf_counter() - tg
    s_end = P.snap()
    timed = [r for r in rows if r["phase"] == "timed"]
    summ = {"SUMMARY": True, "worker": a.index, "cpus": sorted(os.sched_getaffinity(0)), "pid": P.pid,
            "procs": procs, "t_go": t_go_wall, "t_end": time.time(), "timed_wall_s": round(wall, 4),
            "timed_proc_cpu_s": delta(s_go, s_end, "cpu"), "timed_driver_cpu_s": delta(s_go, s_end, "drv_cpu"),
            "digest": fnv_fold(sorted([r for r in timed if ok_row(r)], key=lambda r: r["g"])),
            "timed": summarize_rows(timed), "warmup": summarize_rows([r for r in rows if r["phase"] == "warmup"])}
    if wall and summ["timed"]["decisions"]:
        summ["timed"]["decisions_per_s_wall"] = round(summ["timed"]["decisions"] / wall, 3)
        if summ["timed_proc_cpu_s"] is not None:
            summ["timed"]["proc_cpu_util_wall"] = round(summ["timed_proc_cpu_s"] / wall, 4)
    out.write(json.dumps(summ) + "\n")
    out.close()
    P.close()
    print(json.dumps(summ), flush=True)


# ----------------------------------------------------------------------------- pool


def cpu_sets_for(a):
    """Per-worker CPU lists. More workers than sets wrap round-robin, so
    `--workers 96 --pin core` puts 4 processes on every physical core and
    `--workers 8 --cpu-sets 20,44` puts 8 on one core (the harness sleeps ~1 ms
    about 9 times per action, so one process leaves its core mostly idle and
    packing several measures what the core can actually do)."""
    if a.cpu_sets:
        sets = [s.strip() for s in a.cpu_sets.split(";") if s.strip()]
    elif a.pin == "none":
        return [None] * a.workers
    else:
        cores = expand_cpus(a.cores) if a.cores else physical_cores()
        if a.pin == "core":
            sets = [",".join(map(str, siblings(c))) for c in cores]
        elif a.pin == "thread":
            sets = [str(t) for t in cores + [x for c in cores for x in siblings(c) if x != c]]
        else:
            raise SystemExit(f"unknown --pin {a.pin}")
    return [sets[w % len(sets)] for w in range(a.workers)]


def window_stats(timed_rows, t_go, t_first_end, t_last_end):
    """Machine throughput: all timed fights over [GO, last end]; and fights that
    finished while every worker was still busy, over [GO, first worker's end]."""
    ok = [r for r in timed_rows if ok_row(r)]
    steps = sum(r["steps"] for r in ok)
    full = t_last_end - t_go
    in_busy = [r for r in ok if r["t_end"] <= t_first_end]
    busy = t_first_end - t_go
    return {
        "window_s": round(full, 3), "decisions": steps, "fights": len(ok),
        "decisions_per_s": round(steps / full, 2) if full > 0 else None,
        "fights_per_s": round(len(ok) / full, 3) if full > 0 else None,
        "allbusy_window_s": round(busy, 3),
        "allbusy_decisions_per_s": round(sum(r["steps"] for r in in_busy) / busy, 2) if busy > 0 else None,
        "allbusy_fights_per_s": round(len(in_busy) / busy, 3) if busy > 0 else None,
    }


def jit_curve(rows, max_k=60):
    """Median ms/action and game-process CPU ms/action by fight index in the process."""
    by = {}
    for r in rows:
        if ok_row(r) and r["k"] < max_k:
            by.setdefault(r["k"], []).append(r)
    out = []
    for k in sorted(by):
        rs = by[k]
        cpu = [1000 * r["cpu_s"] / r["steps"] for r in rs if r.get("cpu_s") is not None and r["steps"]]
        gt = [1000 * r["gt_cpu_s"] / r["steps"] for r in rs if r.get("gt_cpu_s") is not None and r["steps"]]
        out.append({"k": k, "n": len(rs), "ms_per_action": round(statistics.median(r["ms_per_action"] for r in rs), 3),
                    "cpu_ms_per_action": round(statistics.median(cpu), 3) if cpu else None,
                    "gamethread_cpu_ms_per_action": round(statistics.median(gt), 3) if gt else None})
    return out


def paired_warm_cold(rows):
    """Same fight played cold (warm-up pass) and warm (timed pass) in one process."""
    cold = {(r["pid"], r["g"]): r for r in rows if r["phase"] == "warmup" and ok_row(r)}
    pairs = [(cold[(r["pid"], r["g"])], r) for r in rows
             if r["phase"] == "timed" and ok_row(r) and (r["pid"], r["g"]) in cold]
    if not pairs:
        return None
    same = sum(c["steps"] == w["steps"] and c["won"] == w["won"] for c, w in pairs)
    pc = sum(c["play_s"] for c, _ in pairs)
    pw = sum(w["play_s"] for _, w in pairs)
    out = {"pairs": len(pairs), "same_steps_and_outcome": same,
           "cold_ms_per_action": round(1000 * pc / sum(c["steps"] for c, _ in pairs), 3),
           "warm_ms_per_action": round(1000 * pw / sum(w["steps"] for _, w in pairs), 3),
           "cold_over_warm_wall": round(pc / pw, 3) if pw else None}
    cc = [c.get("cpu_s") for c, _ in pairs]
    cw = [w.get("cpu_s") for _, w in pairs]
    if None not in cc and None not in cw and sum(cw):
        out["cold_over_warm_cpu"] = round(sum(cc) / sum(cw), 3)
    return out


def ref_check(timed_rows, ref_path):
    ref = {}
    with open(ref_path) as fh:
        for line in fh:
            r = json.loads(line)
            if "i" in r and "steps" in r and "error" not in r:
                ref[r["i"]] = (r["steps"], bool(r["won"]))
    match = mism = 0
    examples = []
    for r in timed_rows:
        if ok_row(r) and r["rep"] == 0 and r["s"] in ref:
            if ref[r["s"]] == (r["steps"], bool(r["won"])):
                match += 1
            else:
                mism += 1
                if len(examples) < 5:
                    examples.append({"s": r["s"], "ref": ref[r["s"]], "got": (r["steps"], r["won"])})
    return {"ref": ref_path, "compared": match + mism, "match": match, "mismatch": mism, "examples": examples}


def pool_main(a):
    os.makedirs(a.out_dir, exist_ok=True)
    logf = open(os.path.join(a.out_dir, "pool.log"), "a")

    def log(msg):
        line = f"[{time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}] {msg}"
        print(line, file=sys.stderr, flush=True)
        logf.write(line + "\n")
        logf.flush()

    sets = cpu_sets_for(a)
    n_fights = a.workers * a.per_worker
    seeds = sorted({f["s"] for w in range(a.workers)
                    for f in fight_plan(w, a.workers, a.per_worker, a.seed_start, a.reps_per_seed)})
    prep(a.pycli, a.saves, seeds, a.prep_jobs, a.timeout, log)
    man = read_manifest(a.saves)
    bad = [s for s in seeds if s not in man or "error" in man[s]]
    if bad:
        raise SystemExit(f"prep failed for {len(bad)} seeds: {bad[:10]}")
    fps = {man[s].get("setup_fp") for s in seeds}

    # Host snapshot before the workers start (prep has finished).
    st0 = proc_stat()
    time.sleep(1.0)
    st1 = proc_stat()
    worker_cpus = sorted({c for s in sets if s for c in expand_cpus(s)})
    pre = {"loadavg": open("/proc/loadavg").read().split()[:3], "busy_all_pct_1s": busy_pct(st0, st1),
           "busy_worker_cpus_pct_1s": busy_pct(st0, st1, worker_cpus) if worker_cpus else None}
    log(f"pool: {a.workers} workers x {a.per_worker} timed fights (+{a.warmup} warm-up), "
        f"pin={a.pin if not a.cpu_sets else 'sets'} sets={sets[:4]}{'...' if len(sets) > 4 else ''} pre={pre}")

    me = os.path.abspath(__file__)
    procs, readers = [], []
    for w in range(a.workers):
        cmd = [sys.executable, "-I", me, "worker", "--pycli", a.pycli, "--saves", a.saves,
               "--out", os.path.join(a.out_dir, f"worker-{w:02d}.jsonl"), "--index", str(w),
               "--workers", str(a.workers), "--per-worker", str(a.per_worker), "--warmup", str(a.warmup),
               "--seed-start", str(a.seed_start), "--reps-per-seed", str(a.reps_per_seed),
               "--timeout", str(a.timeout), "--barrier"]
        if sets[w]:
            cmd += ["--cpus", sets[w]]
        p = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, bufsize=1)
        lines, ready = [], threading.Event()

        def reader(p=p, lines=lines, ready=ready):
            for line in p.stdout:
                lines.append(line.rstrip("\n"))
                if line.startswith("READY"):
                    ready.set()
            ready.set()

        t = threading.Thread(target=reader, daemon=True)
        t.start()
        procs.append((p, lines, ready))
        readers.append(t)
    t_boot = time.perf_counter()
    for w, (p, lines, ready) in enumerate(procs):
        if not ready.wait(timeout=max(60.0, a.timeout * max(1, a.warmup) + 120)):
            raise SystemExit(f"worker {w} not ready")
        if p.poll() is not None and not any(x.startswith("READY") for x in lines):
            raise SystemExit(f"worker {w} exited before READY: {lines[-3:]}")
    log(f"pool: all {a.workers} workers READY after {time.perf_counter() - t_boot:.1f} s")
    t_go = time.time() + 0.5
    s_go = proc_stat()
    for p, _, _ in procs:
        p.stdin.write(f"GO {t_go:.6f}\n")
        p.stdin.flush()
    # Clock of the workers' CPUs while they run (boost falls as more cores are busy).
    freqs, stop = [], threading.Event()

    def sample_freq():
        cpus = worker_cpus or list(range(os.cpu_count() or 1))
        while not stop.wait(0.5):
            f = freq_mhz(cpus)
            if f is not None:
                freqs.append(f)

    sampler = threading.Thread(target=sample_freq, daemon=True)
    sampler.start()
    for p, _, _ in procs:
        p.wait()
    stop.set()
    for t in readers:
        t.join(timeout=5)
    s_end = proc_stat()
    t_after = time.time()

    summaries, all_rows = [], []
    for w, (p, lines, _) in enumerate(procs):
        js = [x for x in lines if x.startswith("{")]
        if p.returncode != 0 or not js:
            log(f"worker {w} failed rc={p.returncode} tail={lines[-3:]}")
            continue
        summaries.append(json.loads(js[-1]))
        with open(os.path.join(a.out_dir, f"worker-{w:02d}.jsonl")) as fh:
            for line in fh:
                r = json.loads(line)
                if "g" in r:
                    r["worker"] = w
                    all_rows.append(r)
    timed = [r for r in all_rows if r["phase"] == "timed"]
    ok_timed = sorted([r for r in timed if ok_row(r)], key=lambda r: r["g"])
    t_ends = [s["t_end"] for s in summaries]
    per_worker = []
    for s in summaries:
        t = s["timed"]
        per_worker.append({"worker": s["worker"], "cpus": s["cpus"], "pid": s["pid"],
                           "boot_s": s["procs"][0]["boot_s"], "init_s": s["procs"][0]["init_s"],
                           "rss_mb_after_init": round((s["procs"][0]["rss_kb_after_init"] or 0) / 1024, 1),
                           "restarts": len(s["procs"]) - 1,
                           "fights": t["fights_ok"], "failed": t["fights_failed"],
                           "decisions_per_s_wall": t.get("decisions_per_s_wall"),
                           "decisions_per_s_playonly": t["decisions_per_s_playonly"],
                           "proc_cpu_util_wall": t.get("proc_cpu_util_wall"),
                           "cpu_util_during_play": t.get("cpu_util_during_play"),
                           "rss_mb_max": t.get("rss_mb_max"), "timed_wall_s": s["timed_wall_s"]})
    timed_summary = summarize_rows(timed)
    summ = {
        "SUMMARY": True, "schema": SCHEMA, "mode": "pool", "workers": a.workers, "workers_ok": len(summaries),
        "per_worker_fights": a.per_worker, "warmup": a.warmup, "seed_start": a.seed_start,
        "reps_per_seed": a.reps_per_seed, "fights_planned": n_fights,
        "pin": "sets" if a.cpu_sets else a.pin, "cpu_sets": sets,
        "processes_per_cpu_set": round(a.workers / len(set(sets)), 3) if sets and sets[0] else None,
        "cpus_used": len(worker_cpus) if worker_cpus else os.cpu_count(),
        "digest": fnv_fold(ok_timed),
        "digest_complete": len(ok_timed) == n_fights,
        "start_state_mismatches": sum(1 for r in all_rows if r.get("start_same") is False),
        "setup_fingerprints": sorted(x for x in {r.get("setup_fp") for r in all_rows if r.get("setup_fp")} | fps if x),
        "harness_warning_fights": sum(1 for r in all_rows if r.get("harness_warnings")),
        "world_error_fights": sum(1 for r in all_rows if r.get("world_errors")),
        "timed": timed_summary,
        "machine": window_stats(timed, t_go, min(t_ends), max(t_ends)) if t_ends else None,
        "jit_curve": jit_curve(all_rows),
        "warm_vs_cold_same_fights": paired_warm_cold(all_rows),
        "host": {"pre": pre, "busy_all_pct_timed": busy_pct(s_go, s_end),
                 "busy_worker_cpus_pct_timed": busy_pct(s_go, s_end, worker_cpus) if worker_cpus else None,
                 "loadavg_end": open("/proc/loadavg").read().split()[:3], "t_go": t_go, "t_after": t_after,
                 "freq_mhz_timed": {"mean": round(statistics.mean(freqs)), "min": round(min(freqs)),
                                    "max": round(max(freqs)), "samples": len(freqs)} if freqs else None,
                 "governor": open("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor").read().strip()
                 if os.path.exists("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor") else None},
        "per_worker": per_worker,
        "provenance": {**binary_provenance(), "python": sys.version.split()[0], "harness": me,
                       "harness_md5": md5(me), "client_md5": md5(os.path.join(a.pycli, "python", "sts2_client.py"))},
    }
    if per_worker:
        rates = [w["decisions_per_s_wall"] for w in per_worker if w["decisions_per_s_wall"]]
        summ["per_process_decisions_per_s_wall"] = {
            "median": round(statistics.median(rates), 3), "min": min(rates), "max": max(rates)} if rates else None
        rss = [w["rss_mb_max"] for w in per_worker if w["rss_mb_max"]]
        summ["rss_mb_per_process_max"] = max(rss) if rss else None
        summ["boot_s_median"] = round(statistics.median(w["boot_s"] for w in per_worker), 4)
        summ["init_s_median"] = round(statistics.median(w["init_s"] for w in per_worker), 4)
    groups = {tuple(siblings(c)) for c in (worker_cpus or range(os.cpu_count() or 1))}
    summ["physical_cores_used"] = len(groups)
    if summ["machine"] and summ["machine"]["decisions_per_s"]:
        summ["machine"]["decisions_per_s_per_physical_core"] = round(
            summ["machine"]["decisions_per_s"] / len(groups), 3)
    if a.ref_jsonl:
        summ["ref_check"] = ref_check(timed, a.ref_jsonl)
    with open(os.path.join(a.out_dir, "summary.json"), "w") as fh:
        json.dump(summ, fh, indent=1)
    log(f"pool: done; digest {summ['digest']} complete={summ['digest_complete']} "
        f"machine {summ['machine']}")
    print(json.dumps(summ))


def prep_main(a):
    seeds = list(range(a.seed_start, a.seed_start + a.n_seeds))
    man = prep(a.pycli, a.saves, seeds, a.prep_jobs, a.timeout, lambda m: print(m, file=sys.stderr))
    rows = [man[s] for s in seeds if s in man]
    print(json.dumps({"SUMMARY": True, "mode": "prep", "seeds": len(seeds),
                      "ok": sum("error" not in r for r in rows),
                      "setup_fingerprints": sorted({r.get("setup_fp") for r in rows if r.get("setup_fp")}),
                      "prep_s_median": round(statistics.median(r["prep_s"] for r in rows if "prep_s" in r), 3)
                      if any("prep_s" in r for r in rows) else None}))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    sub = ap.add_subparsers(dest="mode", required=True)

    def common(p):
        p.add_argument("--pycli", required=True, help="sts2-pycli root holding python/sts2_client.py")
        p.add_argument("--saves", required=True, help="directory of per-seed saves + manifest.jsonl")
        p.add_argument("--seed-start", type=int, default=0)
        p.add_argument("--timeout", type=float, default=120.0, help="per-fight / per-prep watchdog, seconds")

    p = sub.add_parser("pool")
    common(p)
    p.add_argument("--out-dir", required=True)
    p.add_argument("--workers", type=int, default=1)
    p.add_argument("--per-worker", type=int, default=22, help="timed fights per worker (K)")
    p.add_argument("--warmup", type=int, default=0,
                   help="untimed warm-up fights per worker: the first W of its own list (replayed when timed)")
    p.add_argument("--reps-per-seed", type=int, default=1,
                   help="fights per seed save (R); 1 = cs_random_fights' plan, 50 = site_random_bench's")
    p.add_argument("--pin", choices=["core", "thread", "none"], default="core")
    p.add_argument("--cores", default="", help="physical cores to use with --pin (default: all, in order)")
    p.add_argument("--cpu-sets", default="", help="explicit per-worker CPU lists, ';'-separated, e.g. '20,44'")
    p.add_argument("--prep-jobs", type=int, default=max(1, (os.cpu_count() or 2) // 2))
    p.add_argument("--ref-jsonl", default="", help="cs_random_fights.py output to compare (steps, won) per seed")
    p = sub.add_parser("worker")
    common(p)
    p.add_argument("--out", required=True)
    p.add_argument("--index", type=int, default=0)
    p.add_argument("--workers", type=int, default=1)
    p.add_argument("--per-worker", type=int, default=22)
    p.add_argument("--warmup", type=int, default=0)
    p.add_argument("--reps-per-seed", type=int, default=1)
    p.add_argument("--cpus", default="")
    p.add_argument("--barrier", action="store_true", help="print READY, wait for 'GO <epoch>' on stdin")
    p = sub.add_parser("prep")
    common(p)
    p.add_argument("--n-seeds", type=int, default=22)
    p.add_argument("--prep-jobs", type=int, default=max(1, (os.cpu_count() or 2) // 2))
    a = ap.parse_args()
    if a.mode == "pool":
        pool_main(a)
    elif a.mode == "worker":
        worker_main(a)
    else:
        prep_main(a)


if __name__ == "__main__":
    main()
