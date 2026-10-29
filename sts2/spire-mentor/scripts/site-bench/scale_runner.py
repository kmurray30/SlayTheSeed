"""Whole-machine scaling: run N copies of one command at once, each pinned to its own CPUs.

    python3 -I scale_runner.py --preset phys24 --out-dir out/ -- <cmd> [args...]

Every child is pinned (sched_setaffinity, the call `taskset` makes) to its own
CPU set before it starts, all children are released at the same instant, and
the runner reports per-process rates, the aggregate rate of the whole machine
and the scaling efficiency against a one-process run.

Common start. The runner forks every child itself. A forked child pins itself,
redirects its output, writes one byte to a "ready" pipe and blocks reading a
"gate" pipe. When all N ready bytes have arrived, the runner records the start
time and closes the gate pipe's only write end: every blocked reader gets EOF
from the same close, and each child then execs the command. Exec, the dynamic
loader and the command's own setup run after the start; nothing runs before
it but the fork, the pin and the redirect. (Popen cannot do this: it waits for
the exec before returning, so a child held before exec would block it.)

Rates. A child prints JSON on its last stdout line. `--work` names its work
counters (default: whichever of fights, decisions, transitions, playouts,
actions are present) and `--elapsed-key` its own timed seconds (default
elapsed_s). Per process: work / its own timed seconds. Aggregate:
* `sum_of_rates`: the sum of the per-process rates, the machine's throughput
  while all copies run (valid when their timed windows overlap; the overlap is
  reported, estimated from each child's end time and its own timed seconds);
* `wall`: total work / (last child's end - common start), which also charges
  process start, untimed setup and the slowest straggler.
`--parser hint_bench` reads hint_bench's text output instead (searches per
second per budget; the sum over processes is the aggregate).

Scaling efficiency = aggregate rate / (N x one-process rate), the one-process
rate coming from `--baseline <summary.json of a 1-process run>` or
`--with-baseline` (runs one copy alone on the first CPU set first).

`--perf` wraps each child in `perf stat` (cycles:u, instructions:u,
task-clock, context switches, migrations) and reports its effective clock
(user cycles / task-clock) and IPC.

Presets read the topology from /sys (L3 domains from
cache/index3/shared_cpu_list, SMT siblings from topology/thread_siblings_list):
  1core      one CPU (--core, default 20), its SMT sibling left idle
  1core-smt  both hardware threads of that core (2 processes)
  ccx        one thread on each core of one L3 domain (--l3 K, default: the
             domain holding --core)
  ccx-smt    every hardware thread of that L3 domain
  physN      N physical cores, one thread each, spread evenly over the L3
             domains (phys12 = 3 per domain on a 4-domain CPU); phys24 = all
  smt48/all  every online hardware thread
  --cpus 0,1,5-7       one process per listed CPU
  --cpu-sets "6-17"    one process per ';'-separated set (multi-threaded children)
`-n N` keeps the first N sets of any of these. `--show-topology` prints them.

Placeholders in the command: {i} (process index), {cpu} (first CPU of its
set), {cpus} (its set as a list), {n} (process count).

Linux only, standard library only. Exit status 0 only if every child exited 0
and printed a parsable result.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import select
import signal
import statistics
import subprocess
import sys
import time

SYS = "/sys/devices/system/cpu"
WORK_DEFAULT = ("fights", "decisions", "transitions", "playouts", "actions")
ELAPSED_DEFAULT = ("elapsed_s", "timing.wall_seconds", "wall_seconds", "elapsed")
IDENTITY_DEFAULT = ("digest", "checksum", "work.checksum", "wins", "decisions", "fights",
                    "transitions", "playouts", "anomalies")
PERF_EVENTS = "cycles:u,instructions:u,task-clock,context-switches,cpu-migrations"


# ---------------------------------------------------------------- topology
def parse_cpulist(s: str) -> list[int]:
    out: list[int] = []
    for part in s.strip().split(","):
        part = part.strip()
        if not part:
            continue
        if "-" in part:
            a, b = part.split("-", 1)
            out.extend(range(int(a), int(b) + 1))
        else:
            out.append(int(part))
    return out


def fmt_cpulist(cpus: list[int]) -> str:
    cpus = sorted(set(cpus))
    runs: list[str] = []
    i = 0
    while i < len(cpus):
        j = i
        while j + 1 < len(cpus) and cpus[j + 1] == cpus[j] + 1:
            j += 1
        runs.append(str(cpus[i]) if i == j else f"{cpus[i]}-{cpus[j]}")
        i = j + 1
    return ",".join(runs)


def read(path: str) -> str:
    with open(path) as f:
        return f.read().strip()


def topology() -> dict:
    online = parse_cpulist(read(f"{SYS}/online"))
    l3: dict[tuple, list[int]] = {}
    sib: dict[int, list[int]] = {}
    for c in online:
        try:
            dom = tuple(parse_cpulist(read(f"{SYS}/cpu{c}/cache/index3/shared_cpu_list")))
        except OSError:
            dom = tuple(online)  # no L3 info: one domain
        l3.setdefault(dom, []).append(c)
        sib[c] = parse_cpulist(read(f"{SYS}/cpu{c}/topology/thread_siblings_list"))
    domains = sorted(l3.values(), key=min)
    primary = sorted({min(sib[c]) for c in online})          # first thread of each core
    dom_primaries = [[p for p in primary if p in d] for d in domains]
    return {"online": online, "domains": domains, "siblings": sib, "primary": primary,
            "dom_primaries": dom_primaries}


def preset_sets(name: str, topo: dict, core: int, l3: int | None) -> list[list[int]]:
    doms, prim = topo["domains"], topo["primary"]
    if l3 is None:
        l3 = next(i for i, d in enumerate(doms) if core in d)
    if name == "1core":
        return [[core]]
    if name == "1core-smt":
        return [[c] for c in topo["siblings"][core]]
    if name == "ccx":
        return [[c] for c in topo["dom_primaries"][l3]]
    if name == "ccx-smt":
        return [[c] for c in sorted(doms[l3])]
    if name in ("smt48", "all"):
        return [[c] for c in topo["online"]]
    m = re.fullmatch(r"phys(\d+)", name)
    if m:
        n = int(m.group(1))
        if n > len(prim):
            raise SystemExit(f"{name}: only {len(prim)} physical cores")
        # Round-robin over the L3 domains, lowest core first in each.
        pools = [list(d) for d in topo["dom_primaries"]]
        out: list[int] = []
        while len(out) < n:
            for p in pools:
                if p and len(out) < n:
                    out.append(p.pop(0))
        return [[c] for c in sorted(out)]
    raise SystemExit(f"unknown preset {name!r}")


def siblings_of(sets: list[list[int]], topo: dict) -> list[int]:
    used = {c for s in sets for c in s}
    return sorted({x for c in used for x in topo["siblings"][c]} - used)


# ---------------------------------------------------------------- host state
def proc_stat() -> dict[int, tuple[int, int]]:
    """cpu -> (busy jiffies, total jiffies)."""
    out = {}
    with open("/proc/stat") as f:
        for line in f:
            if line.startswith("cpu") and line[3].isdigit():
                p = line.split()
                v = [int(x) for x in p[1:9]]
                idle = v[3] + v[4]
                out[int(p[0][3:])] = (sum(v) - idle, sum(v))
    return out


def busy_pct(a: dict, b: dict, cpus: list[int]) -> dict[int, float]:
    r = {}
    for c in cpus:
        db, dt = b[c][0] - a[c][0], b[c][1] - a[c][1]
        r[c] = round(100.0 * db / dt, 1) if dt > 0 else 0.0
    return r


def governor(c: int) -> str | None:
    try:
        return read(f"{SYS}/cpu{c}/cpufreq/scaling_governor")
    except OSError:
        return None


def have_perf() -> bool:
    try:
        return subprocess.run(["perf", "stat", "-x,", "-e", "cycles:u", "-o", os.devnull, "true"],
                              capture_output=True, timeout=20, check=False).returncode == 0
    except (OSError, subprocess.SubprocessError):
        return False


# ---------------------------------------------------------------- one launch
def substitute(cmd: list[str], i: int, cpus: list[int], n: int) -> list[str]:
    rep = {"{i}": str(i), "{cpu}": str(cpus[0]), "{cpus}": fmt_cpulist(cpus), "{n}": str(n)}
    out = []
    for a in cmd:
        for k, v in rep.items():
            a = a.replace(k, v)
        out.append(a)
    return out


class Timeout(Exception):
    pass


def kill_group(pid: int) -> None:
    """SIGKILL a child's whole process group (the child and, under --perf, perf's child)."""
    try:
        os.killpg(pid, signal.SIGKILL)
    except (ProcessLookupError, PermissionError):
        try:
            os.kill(pid, signal.SIGKILL)
        except ProcessLookupError:
            pass


def launch(cmd: list[str], sets: list[list[int]], out_dir: str, tag: str, perf: bool,
           timeout: float | None) -> dict:
    """Fork len(sets) pinned children held at a gate, release them together, reap them."""
    n = len(sets)
    os.makedirs(out_dir, exist_ok=True)
    gate_r, gate_w = os.pipe()
    ready_r, ready_w = os.pipe()
    kids: dict[int, dict] = {}
    for i, cpus in enumerate(sets):
        argv = substitute(cmd, i, cpus, n)
        base = os.path.join(out_dir, f"{tag}-p{i:02d}")
        perf_out = base + ".perf"
        if perf:
            argv = ["perf", "stat", "-x,", "-e", PERF_EVENTS, "-o", perf_out, "--"] + argv
        sys.stdout.flush()
        sys.stderr.flush()
        pid = os.fork()
        if pid == 0:  # child
            try:
                # Own process group, so a kill also reaches perf's child.
                os.setpgid(0, 0)
                os.close(gate_w)
                os.close(ready_r)
                os.sched_setaffinity(0, cpus)
                fo = os.open(base + ".out", os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o644)
                fe = os.open(base + ".err", os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o644)
                fi = os.open(os.devnull, os.O_RDONLY)
                os.dup2(fi, 0)
                os.dup2(fo, 1)
                os.dup2(fe, 2)
                for fd in (fi, fo, fe):
                    os.close(fd)
                os.write(ready_w, b"r")
                os.close(ready_w)
                while os.read(gate_r, 1):  # blocks until the gate's write end closes
                    pass
                os.close(gate_r)
                os.execvp(argv[0], argv)
            except BaseException as e:  # noqa: BLE001 - must never return into the parent's code
                try:
                    os.write(2, f"scale_runner child {i}: {e!r}\n".encode())
                finally:
                    os._exit(127)
        kids[pid] = {"i": i, "cpus": cpus, "argv": argv, "base": base}
    os.close(ready_w)
    os.close(gate_r)

    # Wait until every child is pinned and parked at the gate.
    got = 0
    deadline = time.monotonic() + 60
    while got < n:
        left = deadline - time.monotonic()
        r, _, _ = select.select([ready_r], [], [], max(0.0, left))
        if not r:
            break
        chunk = os.read(ready_r, n)
        if not chunk:
            break
        got += len(chunk)
    os.close(ready_r)
    if got < n:
        for pid in kids:
            kill_group(pid)
        for pid in kids:
            try:
                os.waitpid(pid, 0)
            except ChildProcessError:
                pass
        raise SystemExit(f"only {got}/{n} children reached the start gate")

    stat0 = proc_stat()
    wall0 = time.time()
    t0 = time.monotonic()
    os.close(gate_w)  # release: every child sees EOF on the gate at once
    # Record the affinity each child actually holds (before it can exit).
    for pid, k in kids.items():
        try:
            k["affinity"] = fmt_cpulist(sorted(os.sched_getaffinity(pid)))
        except OSError:
            k["affinity"] = None

    def on_alarm(signum, frame):
        raise Timeout()

    old = signal.signal(signal.SIGALRM, on_alarm)
    if timeout:
        signal.setitimer(signal.ITIMER_REAL, timeout)
    timed_out = False
    try:
        left = set(kids)
        while left:
            pid, status, ru = os.wait4(-1, 0)
            t = time.monotonic()
            if pid in kids:
                k = kids[pid]
                k["end"] = t - t0
                k["status"] = os.waitstatus_to_exitcode(status)
                k["utime"], k["stime"], k["maxrss_kb"] = ru.ru_utime, ru.ru_stime, ru.ru_maxrss
                left.discard(pid)
    except (Timeout, KeyboardInterrupt):
        timed_out = True
        for pid in left:
            kill_group(pid)
        for pid in left:
            try:
                _, status, ru = os.wait4(pid, 0)
                kids[pid].update(end=time.monotonic() - t0, status="killed",
                                 utime=ru.ru_utime, stime=ru.ru_stime, maxrss_kb=ru.ru_maxrss)
            except ChildProcessError:
                pass
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)
        signal.signal(signal.SIGALRM, old)
    stat1 = proc_stat()
    span = max(k.get("end", 0.0) for k in kids.values())
    return {"kids": [kids[p] for p in sorted(kids, key=lambda p: kids[p]["i"])],
            "start_wall": wall0, "span_s": span, "stat0": stat0, "stat1": stat1,
            "timed_out": timed_out}


# ---------------------------------------------------------------- parsing
def dotted(d, key: str):
    for part in key.split("."):
        if not isinstance(d, dict) or part not in d:
            return None
        d = d[part]
    return d


def last_json(path: str):
    try:
        with open(path) as f:
            lines = [ln for ln in f.read().splitlines() if ln.strip()]
    except OSError:
        return None
    if not lines:
        return None
    try:
        return json.loads(lines[-1])
    except json.JSONDecodeError:
        return None


HINT_RE = re.compile(r"^sims=(\d+) cold_ms=([\d.]+) median_ms=([\d.]+) min_ms=([\d.]+) "
                     r"ms_per_sim=([\d.]+) pv0=(.*)$")


def hint_bench_result(path: str):
    """hint_bench's text output -> {position, pv0, median_ms, searches_per_s, sims_per_s}."""
    try:
        with open(path) as f:
            lines = f.read().splitlines()
    except OSError:
        return None
    res = {"position": None, "pv0": {}, "median_ms": {}, "min_ms": {}, "searches_per_s": {},
           "sims_per_s": {}}
    for ln in lines:
        if ln.startswith("position "):
            res["position"] = ln[len("position "):]
        m = HINT_RE.match(ln)
        if m:
            s, med = m.group(1), float(m.group(3))
            res["median_ms"][s] = med
            res["min_ms"][s] = float(m.group(4))
            res["pv0"][s] = m.group(6)
            res["searches_per_s"][s] = round(1000.0 / med, 3) if med > 0 else None
            res["sims_per_s"][s] = round(int(s) * 1000.0 / med) if med > 0 else None
    return res if res["median_ms"] else None


def parse_perf(path: str) -> dict | None:
    try:
        with open(path) as f:
            text = f.read()
    except OSError:
        return None
    out = {}
    for ln in text.splitlines():
        p = ln.split(",")
        if len(p) < 3 or not p[0].replace(".", "", 1).isdigit():
            continue
        ev = p[2]
        val = float(p[0])
        if ev.startswith("cycles"):
            out["cycles_u"] = val
        elif ev.startswith("instructions"):
            out["instructions_u"] = val
        elif ev.startswith("task-clock"):
            out["task_clock_ms"] = val
        elif ev.startswith("context-switches"):
            out["context_switches"] = val
        elif ev.startswith("cpu-migrations"):
            out["cpu_migrations"] = val
    if out.get("cycles_u") and out.get("task_clock_ms"):
        out["ghz_user"] = round(out["cycles_u"] / (out["task_clock_ms"] * 1e6), 3)
    if out.get("cycles_u") and out.get("instructions_u"):
        out["ipc"] = round(out["instructions_u"] / out["cycles_u"], 3)
    return out or None


# ---------------------------------------------------------------- analysis
def analyse(run: dict, a, n_sets: int) -> dict:
    procs = []
    ok = not run["timed_out"]
    for k in run["kids"]:
        p = {"i": k["i"], "cpus": fmt_cpulist(k["cpus"]), "affinity": k.get("affinity"),
             "status": k.get("status"), "end_s": round(k.get("end", 0.0), 4),
             "utime_s": round(k.get("utime", 0.0), 3), "stime_s": round(k.get("stime", 0.0), 3),
             "maxrss_mb": round(k.get("maxrss_kb", 0) / 1024, 1)}
        if a.parser == "hint_bench":
            res = hint_bench_result(k["base"] + ".out")
        else:
            res = last_json(k["base"] + ".out")
        p["result"] = res
        if a.perf:
            p["perf"] = parse_perf(k["base"] + ".perf")
        if k.get("status") != 0 or res is None:
            ok = False
            try:
                with open(k["base"] + ".err") as f:
                    p["stderr_tail"] = f.read()[-600:]
            except OSError:
                pass
        procs.append(p)

    summary: dict = {"ok": ok, "n": n_sets, "span_s": round(run["span_s"], 4), "procs": procs}
    good = [p for p in procs if p["status"] == 0 and p["result"] is not None]

    if a.parser == "hint_bench":
        budgets = sorted({s for p in good for s in p["result"]["median_ms"]}, key=int)
        agg = {}
        for s in budgets:
            ms = [p["result"]["median_ms"][s] for p in good if s in p["result"]["median_ms"]]
            if not ms:
                continue
            sps = [p["result"]["searches_per_s"][s] for p in good if s in p["result"]["searches_per_s"]]
            agg[s] = {"procs": len(ms), "median_ms_median": round(statistics.median(ms), 3),
                      "median_ms_max": round(max(ms), 3),
                      "searches_per_s_sum": round(sum(sps), 2),
                      "sims_per_s_sum": round(sum(sps) * int(s))}
        summary["hint"] = agg
        pos = {json.dumps([p["result"]["position"], p["result"]["pv0"]]) for p in good}
        summary["identity"] = {"distinct_position_pv0": len(pos), "same": len(pos) == 1}
    else:
        work_keys = a.work.split(",") if a.work else [
            k for k in WORK_DEFAULT if good and isinstance(dotted(good[0]["result"], k), (int, float))]
        el_key = a.elapsed_key or next(
            (k for k in ELAPSED_DEFAULT if good and isinstance(dotted(good[0]["result"], k), (int, float))),
            None)
        summary["work_keys"], summary["elapsed_key"] = work_keys, el_key
        totals = {k: sum(dotted(p["result"], k) or 0 for p in good) for k in work_keys}
        summary["total_work"] = totals
        span = run["span_s"]
        summary["agg_rate_wall"] = {k: round(v / span, 2) if span > 0 else None for k, v in totals.items()}
        if el_key:
            rates = {k: [] for k in work_keys}
            windows = []
            for p in good:
                el = dotted(p["result"], el_key)
                if not el:
                    continue
                p["rate"] = {k: round((dotted(p["result"], k) or 0) / el, 2) for k in work_keys}
                for k in work_keys:
                    rates[k].append(p["rate"][k])
                windows.append((p["end_s"] - el, p["end_s"], el))
            summary["agg_rate_sum"] = {k: round(sum(v), 2) for k, v in rates.items()}
            summary["per_proc_rate"] = {
                k: {"mean": round(statistics.fmean(v), 2), "min": min(v), "max": max(v),
                    "spread_pct": (round(100 * (max(v) - min(v)) / statistics.fmean(v), 2)
                                   if statistics.fmean(v) else None)}
                for k, v in rates.items() if v}
            if windows:
                lo = max(w[0] for w in windows)
                hi = min(w[1] for w in windows)
                med = statistics.median(w[2] for w in windows)
                summary["timed_overlap_frac"] = round(max(0.0, hi - lo) / med, 4) if med > 0 else None
                summary["timed_start_spread_s"] = round(max(w[0] for w in windows) - min(w[0] for w in windows), 4)
        ident_keys = a.identity.split(",") if a.identity else list(IDENTITY_DEFAULT)
        ids = {}
        for k in ident_keys:
            vals = {json.dumps(dotted(p["result"], k)) for p in good if dotted(p["result"], k) is not None}
            if vals:
                ids[k] = sorted(vals) if len(vals) > 1 else json.loads(next(iter(vals)))
        # No identity key found is "unknown", never "same" (an absent key is not a match).
        summary["identity"] = {"values": ids,
                               "same": (all(not isinstance(v, list) for v in ids.values())
                                        if ids else None)}

    if a.perf:
        ghz = [p["perf"]["ghz_user"] for p in good if p.get("perf") and p["perf"].get("ghz_user")]
        ipc = [p["perf"]["ipc"] for p in good if p.get("perf") and p["perf"].get("ipc")]
        mig = [p["perf"].get("cpu_migrations", 0) for p in good if p.get("perf")]
        if ghz:
            summary["perf"] = {"ghz_user_median": round(statistics.median(ghz), 3),
                               "ghz_user_min": min(ghz), "ghz_user_max": max(ghz),
                               "ipc_median": round(statistics.median(ipc), 3) if ipc else None,
                               "cpu_migrations_max": max(mig) if mig else None}
    return summary


def efficiency(summary: dict, base: dict | None) -> dict | None:
    if not base or not summary.get("agg_rate_sum"):
        return None
    out = {}
    for k, agg in summary["agg_rate_sum"].items():
        b = (base.get("per_proc_rate") or {}).get(k, {}).get("mean")
        if b:
            n = summary["n"]
            out[k] = {"baseline_1proc_rate": b, "speedup_vs_1proc": round(agg / b, 3),
                      "efficiency": round(agg / (n * b), 4),
                      "per_proc_mean_vs_1proc": round(summary["per_proc_rate"][k]["mean"] / b, 4)}
    return out or None


def efficiency_hint(summary: dict, base: dict | None) -> dict | None:
    if not base or "hint" not in summary or "hint" not in base:
        return None
    out = {}
    for s, agg in summary["hint"].items():
        b = base["hint"].get(s, {}).get("searches_per_s_sum")
        if b:
            out[s] = {"baseline_1proc_searches_per_s": b,
                      "speedup_vs_1proc": round(agg["searches_per_s_sum"] / b, 3),
                      "efficiency": round(agg["searches_per_s_sum"] / (summary["n"] * b), 4)}
    return out


# ---------------------------------------------------------------- main
def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0],
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group()
    g.add_argument("--preset", help="1core, 1core-smt, ccx, ccx-smt, physN (phys12, phys24), smt48/all")
    g.add_argument("--cpus", help="one process per CPU of this list, e.g. 0-5,24-29")
    g.add_argument("--cpu-sets", help="one process per ';'-separated CPU set, e.g. '6-17' or '0-5;6-11'")
    ap.add_argument("-n", type=int, help="keep only the first N sets")
    ap.add_argument("--core", type=int, default=20, help="CPU for 1core/1core-smt; picks the ccx domain")
    ap.add_argument("--l3", type=int, help="L3 domain index for ccx/ccx-smt (default: the one holding --core)")
    ap.add_argument("--out-dir", help="children's stdout/stderr/perf files + summary.json")
    ap.add_argument("--label", default="run")
    ap.add_argument("--perf", action="store_true", help="wrap each child in perf stat")
    ap.add_argument("--parser", choices=("json", "hint_bench"), default="json")
    ap.add_argument("--work", help="comma list of (dotted) work keys in the child's JSON")
    ap.add_argument("--elapsed-key", help="(dotted) key of the child's own timed seconds")
    ap.add_argument("--identity", help="comma list of keys that must agree across children")
    ap.add_argument("--baseline", help="summary.json of a 1-process run (same command)")
    ap.add_argument("--with-baseline", action="store_true",
                    help="first run one copy alone on the first CPU set, then the N-process run")
    ap.add_argument("--timeout", type=float, help="kill all children after this many seconds")
    ap.add_argument("--show-topology", action="store_true")
    ap.add_argument("cmd", nargs=argparse.REMAINDER, help="-- command [args...]")
    a = ap.parse_args()
    if sys.platform != "linux":
        raise SystemExit("Linux only")

    topo = topology()
    if a.show_topology:
        print(json.dumps({
            "online": fmt_cpulist(topo["online"]),
            "l3_domains": [fmt_cpulist(d) for d in topo["domains"]],
            "physical_cores": len(topo["primary"]),
            "presets": {p: ";".join(fmt_cpulist(s) for s in preset_sets(p, topo, a.core, a.l3))
                        for p in ("1core", "1core-smt", "ccx", "ccx-smt", "phys6", "phys12",
                                  "phys24", "smt48")
                        if not (p.startswith("phys") and int(p[4:]) > len(topo["primary"]))},
        }, indent=1))
        return 0

    cmd = a.cmd[1:] if a.cmd and a.cmd[0] == "--" else a.cmd
    if not cmd:
        ap.error("no command (put it after --)")
    if a.cpus:
        sets = [[c] for c in parse_cpulist(a.cpus)]
    elif a.cpu_sets:
        sets = [parse_cpulist(s) for s in a.cpu_sets.split(";") if s.strip()]
    else:
        sets = preset_sets(a.preset or "1core", topo, a.core, a.l3)
    if a.n:
        sets = sets[: a.n]
    bad = [c for s in sets for c in s if c not in topo["online"]]
    if bad:
        raise SystemExit(f"CPUs not online: {bad}")
    flat = [c for s in sets for c in s]
    if len(flat) != len(set(flat)):
        print("WARNING: CPU sets overlap", file=sys.stderr)
    if a.perf and not have_perf():
        raise SystemExit("--perf: `perf stat -e cycles:u` does not work here")
    out_dir = a.out_dir or os.path.join(os.getcwd(), f"scale-{a.label}-{int(time.time())}")
    os.makedirs(out_dir, exist_ok=True)

    host = {"loadavg": read("/proc/loadavg"), "governor": governor(sets[0][0]),
            "idle_siblings": fmt_cpulist(siblings_of(sets, topo)),
            "l3_domains": [fmt_cpulist(d) for d in topo["domains"]]}
    s0 = proc_stat()
    time.sleep(1.0)
    s1 = proc_stat()
    host["busy_pct_1s_before"] = {"selected_max": max(busy_pct(s0, s1, flat).values()),
                                  "machine": round(statistics.fmean(busy_pct(s0, s1, topo["online"]).values()), 1)}

    base_summary = None
    if a.baseline:
        with open(a.baseline) as f:
            base_summary = json.load(f)
    elif a.with_baseline:
        brun = launch(cmd, sets[:1], out_dir, f"{a.label}-baseline", a.perf, a.timeout)
        base_summary = analyse(brun, a, 1)
        with open(os.path.join(out_dir, "baseline.json"), "w") as f:
            json.dump(base_summary, f, indent=1)

    run = launch(cmd, sets, out_dir, a.label, a.perf, a.timeout)
    summary = analyse(run, a, len(sets))
    others = [c for c in topo["online"] if c not in flat]
    during = busy_pct(run["stat0"], run["stat1"], topo["online"])
    host["busy_pct_during"] = {
        "selected_mean": round(statistics.fmean(during[c] for c in flat), 1),
        "unselected_mean": round(statistics.fmean(during[c] for c in others), 1) if others else None,
        "unselected_max": max((during[c] for c in others), default=None)}
    summary.update({
        "label": a.label, "cmd": cmd, "sets": [fmt_cpulist(s) for s in sets],
        "preset": a.preset if not (a.cpus or a.cpu_sets) else None,
        "pinning": "sched_setaffinity before exec (taskset's call), released through a shared gate pipe",
        "perf_events": PERF_EVENTS if a.perf else None, "host": host,
        "start_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(run["start_wall"])),
        "out_dir": out_dir,
    })
    if base_summary is not None:
        summary["baseline_rate"] = base_summary.get("per_proc_rate") or base_summary.get("hint")
        summary["scaling"] = (efficiency_hint(summary, base_summary) if a.parser == "hint_bench"
                              else efficiency(summary, base_summary))
    with open(os.path.join(out_dir, "summary.json"), "w") as f:
        json.dump(summary, f, indent=1)

    # Human-readable digest on stderr; the JSON (without per-process detail) is the last stdout line.
    print(f"[{a.label}] n={len(sets)} span={summary['span_s']}s ok={summary['ok']} "
          f"identity_same={summary.get('identity', {}).get('same')} out={out_dir}", file=sys.stderr)
    for p in summary["procs"]:
        if p["status"] != 0 or p["result"] is None:
            print(f"  FAILED p{p['i']} cpus={p['cpus']} status={p['status']} "
                  f"{(p.get('stderr_tail') or '').strip()[-300:]}", file=sys.stderr)
    for key in ("agg_rate_sum", "agg_rate_wall", "per_proc_rate", "timed_overlap_frac", "hint", "scaling", "perf"):
        if summary.get(key) is not None:
            print(f"  {key}: {json.dumps(summary[key])}", file=sys.stderr)
    compact = {k: v for k, v in summary.items() if k != "procs"}
    print(json.dumps(compact))
    return 0 if summary["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
