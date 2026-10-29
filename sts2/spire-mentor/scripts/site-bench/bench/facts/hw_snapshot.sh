#!/usr/bin/env bash
# Hardware/software snapshot for the benchmark page. Read-only, no root needed.
# Usage: bash hw_snapshot.sh [checkout]   (default: the checkout this script is in)
# Prints a human-readable dump, then ONE JSON line (last stdout line).
# The checkout's venv (<checkout>/sts2-pycli/.venv) reports torch/numpy/sts2_rs.
set -u
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
W=${1:-$(cd "$HERE/../../../.." && pwd)}
PY=$W/sts2-pycli/.venv/bin/python
c=/sys/devices/system/cpu

echo "== date: $(date -Iseconds)"
echo "== host: $(uname -srvm)"
grep PRETTY_NAME /etc/os-release
echo "== worktree HEAD: $(git -C "$W" rev-parse HEAD)"
echo "== board: $(cat /sys/class/dmi/id/sys_vendor) $(cat /sys/class/dmi/id/product_name) / $(cat /sys/class/dmi/id/board_name), BIOS $(cat /sys/class/dmi/id/bios_version)"
lscpu | grep -E 'Model name|^CPU\(s\)|Thread\(s\) per core|Core\(s\) per socket|Socket|NUMA node|L1d|L1i|L2|L3|CPU max MHz|CPU min MHz|Frequency boost'
echo "== cpufreq: driver=$(cat $c/cpu0/cpufreq/scaling_driver) governors=$(cat $c/cpu*/cpufreq/scaling_governor | sort | uniq -c | xargs) scaling_max_khz=$(cat $c/cpu0/cpufreq/scaling_max_freq) cpuinfo_max_khz=$(cat $c/cpu0/cpufreq/cpuinfo_max_freq) boost=$(cat $c/cpufreq/boost 2>/dev/null || cat $c/cpu0/cpufreq/boost)"
echo "== smt: control=$(cat $c/smt/control) active=$(cat $c/smt/active)"
echo "== thp: enabled=$(cat /sys/kernel/mm/transparent_hugepage/enabled) defrag=$(cat /sys/kernel/mm/transparent_hugepage/defrag)"
echo "== L3 domains:"
for x in $(cat $c/cpu*/cache/index3/shared_cpu_list | sort -u -t- -k1,1n); do echo "   $x"; done
echo "== memory: $(grep MemTotal /proc/meminfo)"
udevadm info /sys/devices/virtual/dmi/id 2>/dev/null \
  | grep -E 'MEMORY_DEVICE_[0-9]+_(SIZE|LOCATOR|TYPE|SPEED_MTS|CONFIGURED_SPEED_MTS|PART_NUMBER|MANUFACTURER|RANK)=' \
  | sed 's/^E: /   /'
echo "== gpus:"
nvidia-smi --query-gpu=index,name,memory.total,driver_version,pcie.link.gen.max,pcie.link.width.max,power.limit --format=csv,noheader 2>&1 | sed 's/^/   /'
nvidia-smi 2>/dev/null | grep -o 'CUDA UMD Version: [0-9.]*\|CUDA Version: [0-9.]*' | head -1 | sed 's/^/   /'
echo "== load: $(cat /proc/loadavg)"
echo "== toolchain:"
( cd "$W/sts2-rs" && rustc -V && cargo -V && rustc -vV | grep LLVM )
cargo nextest --version 2>/dev/null | head -1
dotnet --version 2>/dev/null | sed 's/^/dotnet sdk /'
dotnet --list-runtimes 2>/dev/null | grep NETCore
node -v | sed 's/^/node /'
"$PY" -I -c 'import sys,torch,numpy; print("python", sys.version.split()[0]); print("torch", torch.__version__, "cuda", torch.version.cuda, "cudnn", torch.backends.cudnn.version(), "gpus", torch.cuda.device_count()); print("numpy", numpy.__version__)' 2>&1
"$PY" -I -c 'import sts2_rs; print("sts2_rs build_info", sts2_rs.build_info())' 2>&1
grep '"version"' "$W/sts2-pycli/web/node_modules/playwright/package.json" 2>/dev/null | sed 's/^ */playwright /'
grep -A3 '"name": "chromium-headless-shell"' "$W/sts2-pycli/web/node_modules/playwright-core/browsers.json" 2>/dev/null | grep browserVersion | sed 's/^ */chromium /'
wasm-bindgen --version 2>/dev/null
"$W/sts2-pycli/web/node_modules/.bin/wasm-opt" --version 2>/dev/null

# JSON summary (last line)
"$PY" -I - "$W" <<'EOF'
import json, os, platform, subprocess, sys
W = sys.argv[1]
def rd(p):
    try:
        return open(p).read().strip()
    except OSError:
        return None
def sh(*a):
    try:
        return subprocess.run(a, capture_output=True, text=True, check=False).stdout.strip()
    except OSError:
        return None
c = "/sys/devices/system/cpu"
l3 = sorted({rd(f"{c}/cpu{i}/cache/index3/shared_cpu_list") for i in range(os.cpu_count())},
            key=lambda s: int(s.split("-")[0]))
model = next((l.split(":", 1)[1].strip() for l in open("/proc/cpuinfo") if l.startswith("model name")), None)
print(json.dumps({
    "date": sh("date", "-Iseconds"),
    "worktree_head": sh("git", "-C", W, "rev-parse", "HEAD"),
    "cpu": model,
    "logical_cpus": os.cpu_count(),
    "smt_active": rd(f"{c}/smt/active"),
    "l3_domains": l3,
    "governor": rd(f"{c}/cpu0/cpufreq/scaling_governor"),
    "scaling_max_khz": rd(f"{c}/cpu0/cpufreq/scaling_max_freq"),
    "cpuinfo_max_khz": rd(f"{c}/cpu0/cpufreq/cpuinfo_max_freq"),
    "boost": rd(f"{c}/cpufreq/boost") or rd(f"{c}/cpu0/cpufreq/boost"),
    "thp": rd("/sys/kernel/mm/transparent_hugepage/enabled"),
    "mem_total_kb": int(next(l.split()[1] for l in open("/proc/meminfo") if l.startswith("MemTotal"))),
    "kernel": platform.release(),
    "loadavg": rd("/proc/loadavg"),
    "gpus": sh("nvidia-smi", "--query-gpu=name,memory.total,driver_version", "--format=csv,noheader").splitlines(),
}))
EOF
