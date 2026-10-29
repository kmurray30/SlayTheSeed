#!/usr/bin/env bash
# C# (shipped game, headless) measurement cells for the 2026-10 benchmark page.
# Serial; every cell waits for a quiet host first. Usage:
#   BENCH_DIR=<scratch> bash measure_cs.sh prep     # untimed: per-seed saves for seeds 0..8447 (once)
#   BENCH_DIR=<scratch> bash measure_cs.sh A0 A B2 B4 B8 B16 C1 C2 C3 C4 C5 C6   # any subset, in this order
#   BENCH_DIR=<scratch> bash measure_cs.sh all      # prep + every cell
# Needs build_cs.sh first ($BENCH_DIR/csharp/bin/cs = a Release Sts2Headless with
# the game's assemblies in lib/, $BENCH_DIR/csharp/pycli = python/sts2_client.py + lib/).
# Results: $RUNS/<cell>/ (per-worker rows, summary.json, pool.log) and one line per
# cell in $RUNS/results.jsonl ({"cell":..., "summary": <the harness's last JSON line>}).
# CPU lists (CORE_SET 20,44 = one physical core; 0-47 = the whole machine) are this
# host's: 24 cores / 48 threads, SMT sibling = id + 24.
set -u
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$HERE/../env.sh"   # REPO, BENCH_DIR, SB
B=$BENCH_DIR/csharp
H=${H:-$SB}
PY=${PY:-$(command -v python3)}
# Per-seed saves depend on the game build: one directory per sts2.dll (md5 prefix).
SAVES=${SAVES:-$B/saves/sts2-$(md5sum "$B/pycli/lib/sts2.dll" 2>/dev/null | cut -c1-8)}
# The published M2 run's per-fight (steps, won) rows, for every pool cell's ref_check
# (cs_random_fights.py --per-encounter 3; cell A0 reproduces them fight for fight).
REF=${REF:-$HERE/m2_reference.jsonl}
RUNS=${RUNS:-$B/runs}
CORE=${CORE:-20}                       # the per-core cells' physical core (sibling 44)
CORE_SET=${CORE_SET:-20,44}
WARM=${WARM:-110}                      # warm-up fights per process before the timed pass
LOAD_MAX=${LOAD_MAX:-1.5}; BUSY_MAX_PCT=${BUSY_MAX_PCT:-3}; CPU_BUSY_MAX=${CPU_BUSY_MAX:-5}
# Whole-machine cells (C1-C6) skip the per-CPU part of the gate by default.
# The strict gate needs every CPU of the cell (here all 48) at <= CPU_BUSY_MAX %
# over 2 s; on a shared host one briefly busy CPU anywhere fails it, so it never
# passed for a 48-CPU cell (ambient per-CPU noise from other sessions). The
# published C cells were therefore gated on load1 < LOAD_MAX and whole-host busy
# <= BUSY_MAX_PCT only (C_CPU_BUSY_MAX=100), the same gate the Rust whole-machine
# scale runs use. C_CPU_BUSY_MAX=5 restores the strict gate.
C_CPU_BUSY_MAX=${C_CPU_BUSY_MAX:-100}
WAIT_MAX_MIN=${WAIT_MAX_MIN:-30}; FORCE=${FORCE:-0}
export STS2_HEADLESS_BIN=$B/bin/cs/Sts2Headless STS2_GAME_DIR=$B/pycli/lib
# No runtime knobs leak in: the cells measure the default .NET 9 runtime (tiered JIT + PGO on).
for v in $(compgen -e); do case "$v" in DOTNET_ROOT) ;; DOTNET_*|COMPlus_*|LD_PRELOAD|MALLOC_*|GLIBC_TUNABLES) unset "$v";; esac; done
mkdir -p "$RUNS"
log() { echo "[$(date -u +%FT%TZ)] $*" | tee -a "$RUNS/measure.log"; }

host_busy() {  # whole-machine busy % over 2 s
  local a b; read -ra a < /proc/stat; sleep 2; read -ra b < /proc/stat   # the first line: cpu ...
  local ta=0 tb=0 i; for i in 1 2 3 4 5 6 7 8; do ta=$((ta + a[i])); tb=$((tb + b[i])); done
  local idle=$(( (b[4] + b[5]) - (a[4] + a[5]) )) tot=$((tb - ta))
  echo $(( tot > 0 ? 100 * (tot - idle) / tot : 0 ))
}
quiet() {  # quiet <cpu-list>: loadavg, whole-host busy and the cell's CPUs (+ SMT siblings)
  local cpus=$1 t0=$SECONDS load busy cb
  FORCED=0
  while :; do
    load=$(cut -d' ' -f1 /proc/loadavg); busy=$(host_busy)
    cb=$(python3 -I "$H/cpu_busy.py" "$cpus" 2 | sed -n 's/^max=\([0-9]*\).*/\1/p')
    if awk -v l="$load" -v m="$LOAD_MAX" 'BEGIN{exit !(l<m)}' && [ "$busy" -le "$BUSY_MAX_PCT" ] && [ "${cb:-100}" -le "$CPU_BUSY_MAX" ]; then
      log "quiet: load=$load busy=$busy% cpus($cpus)max=$cb%"; return 0
    fi
    if (( SECONDS - t0 > WAIT_MAX_MIN * 60 )); then
      log "NOT quiet after $WAIT_MAX_MIN min: load=$load busy=$busy% cpus max=$cb% top=[$(ps -eo pcpu=,comm= --sort=-pcpu | head -3 | tr -s ' ' | tr '\n' ';')]"
      [ "$FORCE" = 1 ] && { log "FORCE=1: measuring anyway (flag the cell)"; FORCED=1; return 0; }
      return 1
    fi
    sleep 20
  done
}
pool() {  # pool <cell> <gate-cpus> <harness args...>
  local cell=$1 gate=$2; shift 2
  quiet "$gate" || { log "SKIP $cell: host busy"; return 1; }
  log "== $cell: $*"
  "$PY" -I "$H/cs_fights_pool.py" pool --pycli "$B/pycli" --saves "$SAVES" --out-dir "$RUNS/$cell" \
      --ref-jsonl "$REF" "$@" > "$RUNS/$cell.out" 2> "$RUNS/$cell.err"
  local rc=$?
  if [ $rc = 0 ] && tail -1 "$RUNS/$cell.out" | grep -q '"SUMMARY"'; then
    printf '{"cell":"%s","forced":%s,"summary":%s}\n' "$cell" "$FORCED" "$(tail -1 "$RUNS/$cell.out")" >> "$RUNS/results.jsonl"
    log "$cell done: $(tail -1 "$RUNS/$cell.out" | python3 -I -c 'import json,sys; d=json.load(sys.stdin); t=d["timed"]; print({"digest": d["digest"], "complete": d["digest_complete"], "start_mism": d["start_state_mismatches"], "ref": [d["ref_check"]["match"], d["ref_check"]["compared"]], "machine_dps": d["machine"]["decisions_per_s"], "per_core_dps": d["machine"].get("decisions_per_s_per_physical_core"), "playonly_ms": t["ms_per_decision"], "cpu_ms": t.get("cpu_ms_per_decision"), "gt_cpu_ms": t.get("gamethread_cpu_ms_per_decision"), "busy_cpus": d["host"]["busy_worker_cpus_pct_timed"], "mhz": d["host"]["freq_mhz_timed"]})')"
  else
    log "$cell FAILED rc=$rc (see $RUNS/$cell.err)"
  fi
}

run_cell() {
  case "$1" in
    prep)  # untimed; ~8.4k fresh game processes (~1 s CPU each)
      log "== prep seeds 0..8447"
      "$PY" -I "$H/cs_fights_pool.py" prep --pycli "$B/pycli" --saves "$SAVES" --n-seeds 8448 --prep-jobs 40 \
          > "$RUNS/prep.out" 2> "$RUNS/prep.err"; log "prep: $(tail -1 "$RUNS/prep.out")" ;;
    A0)    # the published method (a fresh process per fight, cold JIT), on ONE physical core
      quiet "$CORE_SET" || { log "SKIP A0: host busy"; return 1; }
      log "== A0: cs_random_fights.py --per-encounter 3, taskset -c $CORE_SET"
      taskset -c "$CORE_SET" "$PY" -I "$H/cs_random_fights.py" --pycli "$B/pycli" --per-encounter 3 \
          --out "$RUNS/A0.jsonl" > "$RUNS/A0.out" 2> "$RUNS/A0.err"
      printf '{"cell":"A0","forced":%s,"summary":%s}\n' "$FORCED" "$(tail -1 "$RUNS/A0.out")" >> "$RUNS/results.jsonl"
      log "A0: $(tail -1 "$RUNS/A0.out")" ;;
    smoke) pool smoke "$CORE_SET" --workers 2 --cpu-sets "$CORE_SET" --per-worker 1 --warmup 1 ;;  # plumbing pre-flight
    A)   pool A "$CORE_SET" --workers 1 --cpu-sets "$CORE_SET" --per-worker 220 --warmup 220 ;;  # cold pass then the same 220 warm
    # AL (optional, not in "all"): A with the game's localization tables loaded (the Release
    # binary in a sts2-pycli-shaped tree, as the Debug build runs). Same mechanics (same digest);
    # the published M2 and every other cell run WITHOUT them ("[WARN] Localization dir not found").
    # build_cs.sh does not create $B/layout; lay out a sts2-pycli tree there by hand to use it.
    AL)  STS2_HEADLESS_BIN=$B/layout/sts2-pycli/src/Sts2Headless/bin/Release/net9.0/Sts2Headless \
         pool AL "$CORE_SET" --workers 1 --cpu-sets "$CORE_SET" --per-worker 220 --warmup 220 ;;
    # B: M processes sharing ONE physical core, the same 704 fights each time (equal digests)
    B2)  pool B2 "$CORE_SET" --workers 2 --cpu-sets "$CORE_SET" --per-worker 352 --warmup "$WARM" ;;
    B4)  pool B4 "$CORE_SET" --workers 4 --cpu-sets "$CORE_SET" --per-worker 176 --warmup "$WARM" ;;
    B8)  pool B8 "$CORE_SET" --workers 8 --cpu-sets "$CORE_SET" --per-worker 88 --warmup "$WARM" ;;
    B16) pool B16 "$CORE_SET" --workers 16 --cpu-sets "$CORE_SET" --per-worker 44 --warmup "$WARM" ;;
    # C: the whole machine, gated on the whole host only (C_CPU_BUSY_MAX, see above).
    # C1/C2/C3 = the same 5,280 fights; C4/C5/C6 = the same 8,448
    C1)  CPU_BUSY_MAX=$C_CPU_BUSY_MAX pool C1 0-47 --workers 24 --pin core --per-worker 220 --warmup "$WARM" ;;
    C2)  CPU_BUSY_MAX=$C_CPU_BUSY_MAX pool C2 0-47 --workers 48 --pin thread --per-worker 110 --warmup "$WARM" ;;
    C3)  CPU_BUSY_MAX=$C_CPU_BUSY_MAX pool C3 0-47 --workers 48 --pin none --per-worker 110 --warmup "$WARM" ;;
    C4)  CPU_BUSY_MAX=$C_CPU_BUSY_MAX pool C4 0-47 --workers 96 --pin core --per-worker 88 --warmup "$WARM" ;;
    C5)  # 8 processes per core: ~192 x 150-200 MB RSS; skipped below 60 GB available
      local avail; avail=$(awk '/MemAvailable/{print int($2/1048576)}' /proc/meminfo)
      [ "$avail" -ge 60 ] || { log "SKIP C5: only ${avail} GB available"; return 1; }
      CPU_BUSY_MAX=$C_CPU_BUSY_MAX pool C5 0-47 --workers 192 --pin core --per-worker 44 --warmup "$WARM" ;;
    C6)  # 16 processes per core (B16's density on every core): ~384 x 150-200 MB RSS
      local avail6; avail6=$(awk '/MemAvailable/{print int($2/1048576)}' /proc/meminfo)
      [ "$avail6" -ge 100 ] || { log "SKIP C6: only ${avail6} GB available"; return 1; }
      CPU_BUSY_MAX=$C_CPU_BUSY_MAX pool C6 0-47 --workers 384 --pin core --per-worker 22 --warmup "$WARM" ;;
    *) log "unknown cell $1"; return 1 ;;
  esac
}

cells=("$@"); [ "${cells[0]:-}" = all ] && cells=(prep A0 A B2 B4 B8 B16 C1 C2 C3 C4 C5 C6)
log "cells: ${cells[*]} (WARM=$WARM CORE_SET=$CORE_SET C_CPU_BUSY_MAX=$C_CPU_BUSY_MAX FORCE=$FORCE)"
for c in "${cells[@]}"; do run_cell "$c"; done
