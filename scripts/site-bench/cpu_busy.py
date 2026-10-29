"""Busy % of specific CPUs (and their SMT siblings) over a short window.

  python3 -I cpu_busy.py <cpu-list e.g. 20 or 6-13 or 12-17,36-41> [seconds]

Prints `max=<pct> cpus=<n:pct,...>`; exit 0. The SMT siblings of every listed CPU
are added from /sys/devices/system/cpu/cpuN/topology/thread_siblings_list, so a
single-thread metric pinned to CPU 20 also checks CPU 44. The October 2026
measurement gated every metric on this (a whole-machine busy % can be low
while one neighbour is hot).
"""
import sys
import time


def expand(spec):
    out = set()
    for part in spec.split(","):
        part = part.strip()
        if not part:
            continue
        if "-" in part:
            a, b = part.split("-")
            out.update(range(int(a), int(b) + 1))
        else:
            out.add(int(part))
    return out


def siblings(cpu):
    try:
        with open(f"/sys/devices/system/cpu/cpu{cpu}/topology/thread_siblings_list") as fh:
            return expand(fh.read())
    except OSError:
        return {cpu}


def snap():
    out = {}
    with open("/proc/stat") as fh:
        for line in fh:
            if line.startswith("cpu") and line[3].isdigit():
                f = line.split()
                vals = [int(x) for x in f[1:9]]  # user nice system idle iowait irq softirq steal
                out[int(f[0][3:])] = (sum(vals), vals[3] + vals[4])
    return out


def main():
    cpus = set()
    for c in expand(sys.argv[1]):
        cpus |= siblings(c)
    secs = float(sys.argv[2]) if len(sys.argv) > 2 else 2.0
    a = snap()
    time.sleep(secs)
    b = snap()
    pct = {}
    for c in sorted(cpus):
        if c in a and c in b:
            tot = b[c][0] - a[c][0]
            idle = b[c][1] - a[c][1]
            pct[c] = 100.0 * (tot - idle) / tot if tot else 0.0
    mx = max(pct.values()) if pct else 100.0
    print(f"max={mx:.0f} cpus=" + ",".join(f"{c}:{v:.0f}" for c, v in pct.items()))


if __name__ == "__main__":
    main()
