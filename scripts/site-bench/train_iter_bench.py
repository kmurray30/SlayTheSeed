"""One real PPO training iteration (or two), timed: `ppo.trainer` with the
deployed recipe, warm-started from a champion checkpoint, on the whole machine.

  python3 -I train_iter_bench.py --label new-pgo \
      --python <venv>/bin/python --py-src <checkout>/sts2-pycli/python \
      [--mod-dir <dir holding a bare sts2_rs.abi3.so>] [--allow-plain] \
      --ckpt <champion>.pt --save-dir <empty dir> [--iterations 1|2] [--games-per-iter 960]

What runs is exactly `<python> -m ppo.trainer <recipe flags>` with
PYTHONPATH=<mod-dir>:<py-src>, cwd=<save-dir>. The recipe is
docs/ppo-champion-training-playbook.md section 1 (250/1000 sims, 960 games,
A0-A10 uniform, --device cuda, --init-ckpt the champion); the four entropy
knobs, --epochs, --lr-anneal-final and --act1 are read from the checkpoint's
own cfg ("from the parent"). The default worker count is the trainer's own
(--procs 0 = cpu_count - 2). STS2_RUNAI_POTION_SWAP=1 and OMP_NUM_THREADS=1
as in the champion's own launch (sts2-pycli/data/runs/ironclad-plr-c1/
launch.sh); STS2_PPO_FEATURES is unset (the champion was collected at the
default schema: champions.toml features="", GH #723). No PLR: the
playbook recipe has none, and iteration 0 is identical either way (the
replay buffer starts empty).

Both trainer generations work: the pre-#761 one (no wheel guard, no pin) and
the current one (refuses a plain wheel unless --allow-plain, which sets
STS2_ALLOW_PLAIN_WHEEL=1; pins the wheel under <save-dir>/sts2_rs-pin/).

Comparability. Iteration 0's games are a pure function of the trainer's
seed_base and the checkpoint: seeds seed_base+g, ascensions from
rng(seed_base+7919), and every worker seeds its action sampler off the game
seed; workers run torch single-threaded. So two engines that make identical
decisions produce identical iteration-0 rows. The FINGERPRINT is a sha256
over the stats row's collection-only fields (n_runs, errors, n_transitions,
win_rate, avg_floor, per-ascension wins/runs, per-surface decision counts,
act-1 mixture) plus `adv_norm_std`, logged at full float precision, which is
a function of every reward and every value estimate of the batch. It must be
equal across engines before their collect_s may be compared. Iteration 1
collects with the net the GPU update produced, and a CUDA update is not
bit-reproducible (two runs of ONE config differ in adv_norm_std's 9th digit
at iteration 1), so for it compare `fingerprint_decisions`, the same digest
without adv_norm_std. A smaller --games-per-iter plays a prefix of the
960-game iteration 0 (same seeds, same ascensions).

Proof of which extension ran: the harness samples /proc/<pid>/maps of the
trainer and every descendant (the pool workers) and hashes each mapped
sts2_rs*.so; all must equal the expected bytes (the --mod-dir .so, else the
interpreter's own import). The current trainer additionally reports its
workers' build stamps (`wheel`, `wheel_pgo`) in every stats row.

GPU: --gpu auto picks a GPU with no compute process (preferring not GPU 0);
nvidia-smi is sampled every 0.5 s and the utilisation inside each update
window (the `train_s` seconds before the iteration's log line) is reported.

Prints progress to stderr and ONE JSON line on stdout (last line); the full
record also goes to <save-dir>/bench.json, the trainer's output to
<save-dir>/trainer.log. Linux only (/proc, nvidia-smi).
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import re
import resource
import signal
import subprocess
import sys
import threading
import time
from pathlib import Path

RECIPE = {
    "character": "Ironclad",
    "sims": 250,
    "boss_sims": 1000,
    "ascension_weights": "0:1,1:1,2:1,3:1,4:1,5:1,6:1,7:1,8:1,9:1,10:1",
    "save_every": 10,
    "device": "cuda",
}
# Knobs the playbook says to take from the parent checkpoint's cfg.
FROM_PARENT = (
    "entropy_coef",
    "entropy_coef_final",
    "entropy_decay_start",
    "entropy_decay_steps",
    "epochs",
    "lr_anneal_final",
    "act1",
)
# Stats-row fields that depend only on the collected batch (not on the update).
FP_SCALARS = ("n_runs", "errors", "n_transitions", "win_rate", "avg_floor", "adv_norm_std")
FP_PREFIXES = ("win_rate/A", "n_runs/A", "dec_per_run/", "act1_frac/")
# Env vars that would change what the trainer does; the harness sets them itself.
SCRUB_ENV = (
    "PYTHONPATH",
    "PYTHONHOME",
    "STS2_PPO_FEATURES",
    "STS2_WHEEL_PIN",
    "STS2_SKIP_WHEEL_CHECK",
    "STS2_ALLOW_PLAIN_WHEEL",
    "STS2_RS_SRC",
    "STS2_RUNAI_POTION_SWAP",
    "OMP_NUM_THREADS",
    "MKL_NUM_THREADS",
    "CUDA_VISIBLE_DEVICES",
    "WANDB_MODE",
)
IT_LINE = re.compile(r"^it\s*(\d+) .*collect=(\d+)s train=(\d+)s")


def log(msg: str) -> None:
    print(f"[train_iter_bench {time.strftime('%H:%M:%S')}] {msg}", file=sys.stderr, flush=True)


def sha256(path: str | os.PathLike) -> str | None:
    h = hashlib.sha256()
    try:
        with open(path, "rb") as f:
            for chunk in iter(lambda: f.read(1 << 20), b""):
                h.update(chunk)
    except OSError:
        return None
    return h.hexdigest()


def run_text(cmd: list[str], env: dict | None = None, timeout: float = 60) -> str:
    return subprocess.run(
        cmd, env=env, capture_output=True, text=True, timeout=timeout, check=False
    ).stdout


# ------------------------------------------------------------------ host facts
def host_facts() -> dict:
    def read(path: str) -> str:
        try:
            return Path(path).read_text()
        except OSError:
            return ""

    model = next(
        (
            x.split(":", 1)[1].strip()
            for x in read("/proc/cpuinfo").splitlines()
            if x.startswith("model name")
        ),
        "",
    )
    gov = read("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor").strip() or None
    mem_kb = next(
        (
            int(x.split()[1])
            for x in read("/proc/meminfo").splitlines()
            if x.startswith("MemTotal:")
        ),
        None,
    )
    return {
        "cpu_model": model,
        "logical_cpus": os.cpu_count(),
        "governor": gov,
        "mem_gib": round(mem_kb / 2**20, 1) if mem_kb else None,
        "kernel": platform.release(),
        "loadavg": Path("/proc/loadavg").read_text().split()[:3],
    }


def busy_frac(seconds: float = 2.0) -> float | None:
    def snap():
        f = Path("/proc/stat").read_text().splitlines()[0].split()
        v = [int(x) for x in f[1:9]]
        return sum(v), v[3] + v[4]

    try:
        a = snap()
        time.sleep(seconds)
        b = snap()
    except OSError:
        return None
    tot = b[0] - a[0]
    return round(1 - (b[1] - a[1]) / tot, 4) if tot > 0 else None


# ------------------------------------------------------------------ GPU
def gpus() -> list[dict]:
    out = run_text(
        [
            "nvidia-smi",
            "--query-gpu=index,uuid,name,memory.used,utilization.gpu",
            "--format=csv,noheader,nounits",
        ]
    )
    rows = []
    for line in out.strip().splitlines():
        idx, uuid, name, mem, util = (x.strip() for x in line.split(","))
        rows.append(
            {"index": int(idx), "uuid": uuid, "name": name, "mem_mib": int(mem), "util": int(util)}
        )
    return rows


def compute_apps() -> list[dict]:
    out = run_text(
        [
            "nvidia-smi",
            "--query-compute-apps=pid,gpu_uuid,used_memory",
            "--format=csv,noheader,nounits",
        ]
    )
    apps = []
    for line in out.strip().splitlines():
        parts = [x.strip() for x in line.split(",")]
        if len(parts) == 3 and parts[0].isdigit():
            apps.append({"pid": int(parts[0]), "uuid": parts[1], "mem_mib": parts[2]})
    return apps


def pick_gpu(spec: str) -> dict:
    all_g = gpus()
    if not all_g:
        raise SystemExit("no GPU visible to nvidia-smi")
    if spec != "auto":
        g = next((g for g in all_g if g["index"] == int(spec)), None)
        if g is None:
            raise SystemExit(f"GPU {spec} not found: {all_g}")
        busy = [a for a in compute_apps() if a["uuid"] == g["uuid"]]
        if busy:
            log(f"WARNING: GPU {spec} has compute processes: {busy}")
        return g
    busy_uuids = {a["uuid"] for a in compute_apps()}
    free = [g for g in all_g if g["uuid"] not in busy_uuids]
    if not free:
        raise SystemExit(f"every GPU has a compute process: {compute_apps()}")
    # Prefer a GPU that is not 0 (the display GPU here), then the emptiest.
    free.sort(key=lambda g: (g["index"] == 0, g["mem_mib"], g["util"], g["index"]))
    return free[0]


class GpuSampler(threading.Thread):
    """`nvidia-smi -lms 500` on one GPU, each line stamped on arrival."""

    def __init__(self, index: int) -> None:
        super().__init__(daemon=True)
        self.samples: list[tuple[float, int, int, float]] = []  # (mono, util%, mem MiB, W)
        self.proc = subprocess.Popen(
            [
                "nvidia-smi",
                "-i",
                str(index),
                "--query-gpu=utilization.gpu,memory.used,power.draw",
                "--format=csv,noheader,nounits",
                "-lms",
                "500",
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
        )

    def run(self) -> None:
        assert self.proc.stdout is not None
        for line in self.proc.stdout:
            try:
                u, m, w = (x.strip() for x in line.split(","))
                self.samples.append((time.monotonic(), int(u), int(m), float(w)))
            except ValueError:
                continue

    def stop(self) -> None:
        self.proc.terminate()
        try:
            self.proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            self.proc.kill()

    def window(self, t0: float, t1: float) -> dict:
        s = [x for x in self.samples if t0 <= x[0] <= t1]
        if not s:
            return {"n": 0}
        u = [x[1] for x in s]
        return {
            "n": len(s),
            "util_mean": round(sum(u) / len(u), 1),
            "util_max": max(u),
            "busy_frac": round(sum(1 for x in u if x > 0) / len(u), 3),
            "mem_max_mib": max(x[2] for x in s),
            "power_max_w": max(x[3] for x in s),
        }


# ------------------------------------------------------------------ process tree
def descendants(root: int) -> list[int]:
    kids: dict[int, list[int]] = {}
    for d in os.listdir("/proc"):
        if not d.isdigit():
            continue
        try:
            stat = Path(f"/proc/{d}/stat").read_text()
        except OSError:
            continue
        ppid = int(stat.rsplit(")", 1)[1].split()[1])
        kids.setdefault(ppid, []).append(int(d))
    out, todo = [], [root]
    while todo:
        p = todo.pop()
        out.append(p)
        todo.extend(kids.get(p, []))
    return out


def kill_session(sid: int) -> list[int]:
    """SIGKILL every process still in session `sid` (the trainer was started
    with start_new_session, so its leftovers, and only they, carry its pid as
    their session id). Returns the pids killed; normally none."""
    killed = []
    for d in os.listdir("/proc"):
        if not d.isdigit() or int(d) == os.getpid():
            continue
        try:
            fields = Path(f"/proc/{d}/stat").read_text().rsplit(")", 1)[1].split()
        except OSError:
            continue
        if int(fields[3]) == sid:
            try:
                os.kill(int(d), signal.SIGKILL)
                killed.append(int(d))
            except ProcessLookupError:
                pass
    return killed


class ProcSampler(threading.Thread):
    """Every 4 s (a few ms of one core): which sts2_rs*.so files the trainer's
    process tree has mapped, how many processes it has, and whether the
    trainer is on the GPU."""

    def __init__(self, root_pid: int, gpu_uuid: str) -> None:
        super().__init__(daemon=True)
        self.root, self.uuid = root_pid, gpu_uuid
        self.so_paths: dict[str, int] = {}  # path -> number of (pid, sample) sightings
        self.max_procs = 0
        self.max_mapping_procs = 0
        self.on_gpu: set[str] = set()
        self.halt = threading.Event()

    def run(self) -> None:
        n = 0
        while not self.halt.is_set():
            pids = descendants(self.root)
            self.max_procs = max(self.max_procs, len(pids))
            mapping = 0
            for pid in pids:
                try:
                    maps = Path(f"/proc/{pid}/maps").read_text()
                except OSError:
                    continue
                seen = set()
                for line in maps.splitlines():
                    parts = line.split(None, 5)
                    if len(parts) == 6:
                        path = parts[5].strip()
                        if os.path.basename(path).startswith("sts2_rs") and ".so" in path:
                            seen.add(path)
                for p in seen:
                    self.so_paths[p] = self.so_paths.get(p, 0) + 1
                mapping += bool(seen)
            self.max_mapping_procs = max(self.max_mapping_procs, mapping)
            if n % 2 == 0:
                for a in compute_apps():
                    if a["pid"] in pids:
                        self.on_gpu.add(a["uuid"])
            n += 1
            self.halt.wait(4.0)


# ------------------------------------------------------------------ the run
def probe(python: str, env: dict, rs_root: str | None) -> dict:
    """Import sts2_rs (and torch) under the run's env, outside the trainer."""
    code = r"""
import json, sys, os
out = {"python": sys.version.split()[0], "executable": sys.executable}
import sts2_rs
ext = getattr(sts2_rs, "sts2_rs", sts2_rs)
out["so_path"] = getattr(ext, "__file__", None)
bi = getattr(sts2_rs, "build_info", None)
out["build_info"] = dict(bi()) if bi else None
sh = getattr(sts2_rs, "src_hash", None)
out["src_hash"] = sh() if sh else None
rr = sys.argv[1] if len(sys.argv) > 1 else ""
rc = getattr(sts2_rs, "recompute_src_hash", None)
out["src_hash_recomputed"] = rc(rr) if (rc and rr) else None
out["rs_root"] = rr or None
import torch, numpy
out["torch"] = torch.__version__
out["numpy"] = numpy.__version__
out["cuda"] = torch.cuda.is_available()
out["cuda_device"] = torch.cuda.get_device_name(0) if out["cuda"] else None
print(json.dumps(out))
"""
    res = subprocess.run(
        [python, "-c", code, rs_root or ""],
        env=env,
        capture_output=True,
        text=True,
        timeout=300,
        cwd="/",
        check=False,
    )
    if res.returncode != 0:
        raise SystemExit(f"probe failed:\n{res.stderr[-3000:]}")
    return json.loads(res.stdout.strip().splitlines()[-1])


def parent_cfg(python: str, env: dict, ckpt: str) -> dict:
    code = (
        "import json,sys,torch; c=torch.load(sys.argv[1],map_location='cpu',weights_only=True);"
        "print(json.dumps(c.get('cfg') or {}, default=str))"
    )
    res = subprocess.run(
        [python, "-c", code, ckpt], env=env, capture_output=True, text=True, timeout=300, cwd="/"
    )
    if res.returncode != 0:
        raise SystemExit(f"cannot read the checkpoint cfg:\n{res.stderr[-2000:]}")
    return json.loads(res.stdout.strip().splitlines()[-1])


def _digest(fields: dict) -> str:
    blob = json.dumps(fields, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(blob.encode()).hexdigest()[:16]


def fingerprint(row: dict) -> tuple[str, str, dict]:
    """(strict, decisions-only, fields). The strict digest includes
    `adv_norm_std` (every reward and value estimate, full precision); the
    decisions-only digest drops it, so it survives the last-bit differences a
    CUDA update leaves in iteration 1's value estimates."""
    fields = {k: row.get(k) for k in FP_SCALARS}
    fields.update({k: v for k, v in row.items() if k.startswith(FP_PREFIXES)})
    counts = {k: v for k, v in fields.items() if k != "adv_norm_std"}
    return _digest(fields), _digest(counts), fields


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawTextHelpFormatter)
    ap.add_argument("--label", required=True, help="e.g. base-plain, new-plain, new-pgo")
    ap.add_argument("--python", required=True, help="interpreter with torch (+CUDA)")
    ap.add_argument("--py-src", required=True, help="the sts2-pycli/python dir the run imports")
    ap.add_argument(
        "--mod-dir", default="", help="dir with a bare sts2_rs.abi3.so, put FIRST on PYTHONPATH"
    )
    ap.add_argument(
        "--rs-root",
        default="",
        help="sts2-rs source the current trainer's guard checks freshness against "
        "(STS2_RS_SRC); default: <py-src>/../../sts2-rs, the guard's own default",
    )
    ap.add_argument(
        "--allow-plain", action="store_true", help="STS2_ALLOW_PLAIN_WHEEL=1 (current trainer)"
    )
    ap.add_argument("--ckpt", required=True, help="champion checkpoint to --init-ckpt from")
    ap.add_argument(
        "--save-dir", required=True, help="fresh dir; the trainer's --save-path is <dir>/main"
    )
    ap.add_argument("--iterations", type=int, default=1)
    ap.add_argument("--games-per-iter", type=int, default=960)
    ap.add_argument(
        "--procs", type=int, default=0, help="0 = the trainer's default (cpu_count - 2)"
    )
    ap.add_argument("--gpu", default="auto", help="nvidia-smi index, or auto")
    ap.add_argument(
        "--potion-swap", default="1", help="STS2_RUNAI_POTION_SWAP value; '' to leave unset"
    )
    ap.add_argument(
        "--omp-threads", default="1", help="OMP_NUM_THREADS (the champion's launch.sh: 1); '' unset"
    )
    ap.add_argument("--expect-pgo", choices=("true", "false", "any"), default="any")
    ap.add_argument("--keep-ckpts", action="store_true", help="keep the trainer's .pt outputs")
    ap.add_argument("--timeout", type=float, default=4 * 3600)
    ap.add_argument(
        "--dry-run", action="store_true", help="print the command and the probe, do not train"
    )
    ap.add_argument("extra", nargs="*", help="extra trainer args after --")
    a = ap.parse_args()

    save_dir = Path(a.save_dir).resolve()
    save_dir.mkdir(parents=True, exist_ok=True)
    save_path = save_dir / "main"
    if Path(f"{save_path}.stats.jsonl").exists():
        raise SystemExit(f"{save_path}.stats.jsonl exists: use a fresh --save-dir")
    py_src = str(Path(a.py_src).resolve())
    mod_dir = str(Path(a.mod_dir).resolve()) if a.mod_dir else ""
    rs_root = (
        str(Path(a.rs_root).resolve()) if a.rs_root else str(Path(py_src).parents[1] / "sts2-rs")
    )
    current_trainer = Path(py_src, "ppo", "wheel_guard.py").is_file()

    gpu = pick_gpu(a.gpu)
    env = {k: v for k, v in os.environ.items() if k not in SCRUB_ENV}
    env.update(
        {
            "PYTHONPATH": os.pathsep.join(p for p in (mod_dir, py_src) if p),
            "PYTHONDONTWRITEBYTECODE": "1",
            "PYTHONUNBUFFERED": "1",
            "CUDA_DEVICE_ORDER": "PCI_BUS_ID",
            "CUDA_VISIBLE_DEVICES": str(gpu["index"]),
        }
    )
    if a.rs_root:
        env["STS2_RS_SRC"] = rs_root
    if a.allow_plain:
        env["STS2_ALLOW_PLAIN_WHEEL"] = "1"
    if a.potion_swap != "":
        env["STS2_RUNAI_POTION_SWAP"] = a.potion_swap
    if a.omp_threads != "":
        env["OMP_NUM_THREADS"] = a.omp_threads

    pr = probe(a.python, env, rs_root)
    expected_so = pr["so_path"]
    expected_sha = sha256(expected_so)
    pgo = (pr.get("build_info") or {}).get("pgo", False)
    log(
        f"probe: {json.dumps({k: pr[k] for k in ('so_path', 'src_hash', 'src_hash_recomputed', 'torch', 'cuda_device')})}"
    )
    if mod_dir and not expected_so.startswith(mod_dir + os.sep):
        raise SystemExit(f"--mod-dir {mod_dir} is not what imports: {expected_so}")
    if a.expect_pgo != "any" and bool(pgo) != (a.expect_pgo == "true"):
        raise SystemExit(f"expected pgo={a.expect_pgo}, the import reports pgo={pgo}")
    fresh = pr["src_hash"] is not None and pr["src_hash"] == pr["src_hash_recomputed"]
    if current_trainer and not fresh:
        log(
            f"WARNING: src_hash {pr['src_hash']} != {pr['src_hash_recomputed']} over {rs_root}: the guard will refuse"
        )

    cfg = parent_cfg(a.python, env, a.ckpt)
    missing = [k for k in FROM_PARENT if k not in cfg]
    if missing:
        raise SystemExit(f"checkpoint cfg lacks {missing}; pass them explicitly via extra args")
    flags = {
        **RECIPE,
        **{k: cfg[k] for k in FROM_PARENT},
        "iterations": a.iterations,
        "games_per_iter": a.games_per_iter,
        "procs": a.procs,
        "init_ckpt": str(Path(a.ckpt).resolve()),
        "save_path": str(save_path),
    }
    cmd = [a.python, "-m", "ppo.trainer"]
    for k, v in flags.items():
        cmd += [f"--{k.replace('_', '-')}", str(v)]
    cmd += a.extra
    shown_env = {
        k: env[k]
        for k in sorted(env)
        if k.startswith(("STS2_", "CUDA_", "PYTHON", "OMP_", "MKL_", "LD_"))
    }
    record: dict = {
        "bench": "train_iter",
        "label": a.label,
        "trainer": "current (wheel guard + pin)" if current_trainer else "pre-guard",
        "cmd": cmd,
        "cwd": str(save_dir),
        "env": shown_env,
        "parent_cfg_used": {k: cfg[k] for k in FROM_PARENT},
        "probe": pr,
        "expected_so": {
            "path": expected_so,
            "sha256": expected_sha,
            "pgo": bool(pgo),
            "fresh_vs_rs_root": fresh,
        },
        "ckpt": {"path": flags["init_ckpt"], "sha256": sha256(flags["init_ckpt"])},
        "gpu": {"index": gpu["index"], "uuid": gpu["uuid"], "name": gpu["name"]},
        "host": host_facts(),
    }
    log("cmd: " + " ".join(cmd))
    if a.dry_run:
        print(json.dumps(record))
        return 0

    record["host"]["busy_frac_before"] = busy_frac(2.0)
    gs = GpuSampler(gpu["index"])
    gs.start()
    lines: list[tuple[float, str]] = []
    ru0 = resource.getrusage(resource.RUSAGE_CHILDREN)
    t_start = time.monotonic()
    wall_start = time.time()
    with open(save_dir / "trainer.log", "w") as logf:
        proc = subprocess.Popen(
            cmd,
            cwd=save_dir,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
            start_new_session=True,  # its own session: a timeout kills the workers too
        )
        ps = ProcSampler(proc.pid, gpu["uuid"])
        ps.start()

        def reader() -> None:
            assert proc.stdout is not None
            for line in proc.stdout:
                now = time.monotonic()
                lines.append((now, line.rstrip("\n")))
                logf.write(f"{now - t_start:9.2f} {line}")
                logf.flush()
                if IT_LINE.match(line) or line.startswith(
                    ("update device", "wheel", "WHEEL", "REFUS", "ABORT")
                ):
                    log("trainer: " + line.strip())

        rt = threading.Thread(target=reader, daemon=True)
        rt.start()
        try:
            rc = proc.wait(timeout=a.timeout)
        except (subprocess.TimeoutExpired, KeyboardInterrupt) as e:
            log(f"stopping the trainer ({type(e).__name__})")
            os.killpg(proc.pid, signal.SIGTERM)
            try:
                proc.wait(timeout=30)
            except subprocess.TimeoutExpired:
                os.killpg(proc.pid, signal.SIGKILL)
                proc.wait()
            rc = -1
        wall = time.monotonic() - t_start
        rt.join(timeout=10)
        ps.halt.set()
        ps.join(timeout=10)
        gs.stop()
        gs.join(timeout=10)
    ru1 = resource.getrusage(resource.RUSAGE_CHILDREN)
    leftovers = kill_session(proc.pid)
    if leftovers:
        log(f"killed {len(leftovers)} leftover process(es) of the trainer's session: {leftovers}")

    stats_rows = []
    sp = Path(f"{save_path}.stats.jsonl")
    if sp.exists():
        stats_rows = [json.loads(x) for x in sp.read_text().splitlines() if x.strip()]
    it_lines = [(t, IT_LINE.match(s)) for t, s in lines if IT_LINE.match(s)]
    dev_line = next((t for t, s in lines if s.startswith("update device")), None)

    iters = []
    for row in stats_rows:
        it = int(row["iteration"])
        fp, fp_dec, fp_fields = fingerprint(row)
        t_line = next((t for t, m in it_lines if int(m.group(1)) == it), None)
        entry = {
            "it": it,
            "collect_s": row.get("collect_s"),
            "train_s": row.get("train_s"),
            "iter_s": row.get("iter_s"),
            "games": (row.get("n_runs") or 0) + (row.get("errors") or 0),
            "n_runs": row.get("n_runs"),
            "errors": row.get("errors"),
            "n_transitions": row.get("n_transitions"),
            "win_rate": row.get("win_rate"),
            "avg_floor": row.get("avg_floor"),
            "adv_norm_std": row.get("adv_norm_std"),
            "minibatches": row.get("nb"),
            "util_collect": row.get("util_collect"),
            "util_train": row.get("util_train"),
            "wheel_pgo": row.get("wheel_pgo"),
            "wheel_so": (row.get("wheel") or {}).get("so_path"),
            "wheel_src_hash": (row.get("wheel") or {}).get("src_hash"),
            "fingerprint": fp,
            "fingerprint_decisions": fp_dec,
            "fingerprint_note": "must match across engines"
            if it == 0
            else "collected with the GPU-updated net: strict digest may differ, compare fingerprint_decisions",
        }
        if t_line is not None and row.get("train_s") is not None:
            ts = float(row["train_s"])
            entry["gpu_update_window"] = gs.window(t_line - ts - 1.0, t_line)
            if row.get("iter_s") is not None:
                entry["gpu_collect_window"] = gs.window(
                    t_line - float(row["iter_s"]), t_line - ts - 1.0
                )
        iters.append(entry)
        (save_dir / f"fingerprint_it{it}.json").write_text(
            json.dumps(fp_fields, indent=1, sort_keys=True) + "\n"
        )

    loaded = [
        {"path": p, "sha256": sha256(p), "sightings": n} for p, n in sorted(ps.so_paths.items())
    ]
    so_ok = bool(loaded) and all(x["sha256"] == expected_sha for x in loaded)
    pin_info = None
    pins = (
        sorted((save_dir / "sts2_rs-pin").glob("*/pin.json"))
        if (save_dir / "sts2_rs-pin").is_dir()
        else []
    )
    if pins:
        pin_info = [json.loads(p.read_text()) for p in pins]
    record.update(
        {
            "rc": rc,
            "ok": rc == 0 and len(iters) == a.iterations and so_ok,
            "wall_s": round(wall, 1),
            "startup_s": round(dev_line - t_start, 1) if dev_line else None,
            "started_at": time.strftime("%FT%T%z", time.localtime(wall_start)),
            "cpu_user_s": round(ru1.ru_utime - ru0.ru_utime, 1),
            "cpu_sys_s": round(ru1.ru_stime - ru0.ru_stime, 1),
            "iterations": iters,
            "fingerprint_it0": iters[0]["fingerprint"] if iters else None,
            "fingerprint_decisions_it0": iters[0]["fingerprint_decisions"] if iters else None,
            "loaded_so": loaded,
            "so_verified": so_ok,
            "pin": pin_info,
            "leftover_pids_killed": leftovers,
            "max_procs_in_tree": ps.max_procs,
            "max_procs_mapping_sts2_rs": ps.max_mapping_procs,
            "gpu_trainer_seen_on": sorted(ps.on_gpu),
            "gpu_whole_run": gs.window(t_start, t_start + wall),
        }
    )
    record["gpu"]["trainer_on_expected_gpu"] = gpu["uuid"] in ps.on_gpu
    record["host"]["loadavg_after"] = Path("/proc/loadavg").read_text().split()[:3]
    if rc != 0:
        record["tail"] = [s for _, s in lines[-25:]]
    if not a.keep_ckpts:
        for p in save_dir.glob("main*.pt"):
            p.unlink()
    (save_dir / "bench.json").write_text(json.dumps(record, indent=1) + "\n")
    print(json.dumps(record))
    return 0 if record["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
