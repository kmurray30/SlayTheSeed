#!/usr/bin/env bash
# Training-speed benchmark driver (needs export.sh + build.sh first).
#
#   measure.sh smoke              # 16 games / 8 workers / 1 iteration, all three configs (~2 min)
#   measure.sh full [REPS]        # the real thing: 960 games, default 46 workers, 1 iteration,
#                                 # configs in ABBA order (base, new-plain, new-pgo, new-pgo, ...)
#   measure.sh percore [PAIRS]    # ppo_collect.py: one worker pinned to CORE, 6 Ironclad runs,
#                                 # base / new / pgo .so under ONE python (src-new), perf task-clock
#   measure.sh summary            # medians, speedups, fingerprint checks of everything recorded
#
# Env: BENCH_DIR (required; this stage lives in $BENCH_DIR/training),
#      PY (python with torch + CUDA, default $REPO/sts2-pycli/.venv/bin/python),
#      GPU (nvidia-smi index, default 1), CORE (default 20; its SMT sibling is 44),
#      ITERS (default 1; 2 = also iteration 1), GAMES / PROCS (full mode overrides),
#      NSEEDS (percore runs per rep, default 6), QUIET_MAX (whole-machine busy % gate,
#      default 3), QUIET_WAIT (s to wait for quiet, default 1800), FORCE=1 (measure anyway).
# Every run appends its harness JSON line to results/*.jsonl; nothing runs in the background.
set -euo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$HERE/../env.sh"   # REPO, BENCH_DIR, SB
T=$BENCH_DIR/training
WT=$REPO
H=$SB
PY=${PY:-$WT/sts2-pycli/.venv/bin/python}
CK=$T/ckpt/ppo_fullrun_ironclad_plr_it550.pt
GPU=${GPU:-1}
CORE=${CORE:-20}
ITERS=${ITERS:-1}
QUIET_MAX=${QUIET_MAX:-3}
QUIET_WAIT=${QUIET_WAIT:-1800}
FORCE=${FORCE:-0}
RES=$T/results
mkdir -p "$RES" "$T/runs" "$T/percore"

log() { echo "[measure $(date +%T)] $*" | tee -a "$RES/measure.log" >&2; }

busy_pct() {  # whole-machine CPU busy % over 5 s, from /proc/stat
  local a b c d e f g h t1 i1 t2 i2
  read -r _ a b c d e f g h _ < /proc/stat; t1=$((a+b+c+d+e+f+g+h)); i1=$((d+e))
  sleep 5
  read -r _ a b c d e f g h _ < /proc/stat; t2=$((a+b+c+d+e+f+g+h)); i2=$((d+e))
  echo $(( 100 * ((t2-t1) - (i2-i1)) / (t2-t1) ))
}

wait_quiet() {  # wait until the whole host is below QUIET_MAX % busy; FORCE=1 proceeds anyway
  local waited=0 b
  while :; do
    b=$(busy_pct)
    if [ "$b" -le "$QUIET_MAX" ]; then log "quiet: busy=${b}% load=$(cut -d' ' -f1-3 /proc/loadavg)"; return 0; fi
    if [ "$waited" -ge "$QUIET_WAIT" ]; then
      if [ "$FORCE" = 1 ]; then log "NOT QUIET (busy=${b}%), FORCE=1: measuring anyway, flag the result"; return 0; fi
      log "NOT QUIET after ${waited}s (busy=${b}%, top: $(ps -eo pcpu=,comm= --sort=-pcpu | head -3 | tr -s ' ' | tr '\n' ';'))"; return 1
    fi
    sleep 25; waited=$((waited + 30))
  done
}

preflight() {
  [ -x "$PY" ] || { log "no interpreter $PY (the checkout's venv was GC'd?): set PY to a python with torch+CUDA"; exit 1; }
  for f in "$T/mod-base/sts2_rs.abi3.so" "$T/mod-new/sts2_rs.abi3.so" "$T/mod-pgo/sts2_rs.abi3.so" "$CK" \
           "$T/src-base/sts2-pycli/python/ppo/trainer.py" "$T/src-new/sts2-pycli/python/ppo/wheel_guard.py"; do
    [ -e "$f" ] || { log "missing $f (run export.sh / build.sh)"; exit 1; }
  done
  ( cd "$T" && sha256sum -c --quiet MODS.sha256 ) || { log "mod-*/sts2_rs.abi3.so changed since build.sh recorded MODS.sha256"; exit 1; }
  if nvidia-smi --query-compute-apps=pid,gpu_uuid --format=csv,noheader | grep -q "$(nvidia-smi -i "$GPU" --query-gpu=uuid --format=csv,noheader)"; then
    log "GPU $GPU has a compute process; pick another with GPU=<idx>"; exit 1
  fi
}

cfg_args() {  # the three configurations
  case "$1" in
    base-plain) echo "--py-src $T/src-base/sts2-pycli/python --mod-dir $T/mod-base --expect-pgo false" ;;
    new-plain)  echo "--py-src $T/src-new/sts2-pycli/python --mod-dir $T/mod-new --allow-plain --expect-pgo false" ;;
    new-pgo)    echo "--py-src $T/src-new/sts2-pycli/python --mod-dir $T/mod-pgo --expect-pgo true" ;;
    *) echo "unknown config $1" >&2; return 1 ;;
  esac
}

run_cfg() {  # run_cfg <config> <tag> <results file> [harness args...]
  local c=$1 tag=$2 out=$3; shift 3
  local d="$T/runs/$c-$tag"
  [ -e "$d/main.stats.jsonl" ] && { log "skip $c-$tag: $d already has a run"; return 0; }
  wait_quiet || return 1
  log "run $c-$tag -> $d"
  # cwd: a neutral directory (the trainer itself runs with cwd=<save-dir>)
  # shellcheck disable=SC2046
  if ( cd "$T/runs" && python3 -I "$H/train_iter_bench.py" --label "$c-$tag" --python "$PY" $(cfg_args "$c") \
        --ckpt "$CK" --save-dir "$d" --gpu "$GPU" --iterations "$ITERS" "$@" \
        > "$d.json" 2> "$d.err" ); then
    tail -1 "$d.json" >> "$out"
    log "$c-$tag: $(tail -1 "$d.json" | python3 -I -c 'import json,sys; r=json.loads(sys.stdin.read()); print([(i["it"], i["collect_s"], i["train_s"], i["iter_s"], i["games"], i["fingerprint"]) for i in r["iterations"]], "so_verified", r["so_verified"])')"
  else
    log "$c-$tag FAILED (see $d.err, $d/trainer.log)"; tail -1 "$d.json" >> "$out" 2>/dev/null || true; return 1
  fi
}

mode=${1:-}
case "$mode" in
  smoke)
    preflight
    QUIET_WAIT=0 FORCE=1
    for c in base-plain new-plain new-pgo; do
      run_cfg "$c" "smoke$(date +%H%M%S)" "$RES/smoke.jsonl" --games-per-iter 16 --procs 8
    done
    python3 -I "$HERE/summarize.py" "$RES/smoke.jsonl"
    ;;
  full)
    preflight
    reps=${2:-2}
    for r in $(seq 1 "$reps"); do
      if [ $((r % 2)) = 1 ]; then order="base-plain new-plain new-pgo"; else order="new-pgo new-plain base-plain"; fi
      for c in $order; do
        run_cfg "$c" "r$r" "$RES/train_iter.jsonl" --games-per-iter "${GAMES:-960}" --procs "${PROCS:-0}"
      done
    done
    python3 -I "$HERE/summarize.py" "$RES/train_iter.jsonl"
    ;;
  percore)
    preflight
    pairs=${2:-3}; n=${NSEEDS:-6}
    for i in $(seq 1 "$pairs"); do
      if [ $((i % 2)) = 1 ]; then order="base new pgo"; else order="pgo new base"; fi
      for arm in $order; do
        f="$T/percore/$arm-n$n-$i"
        [ -e "$f.txt" ] && { log "skip $f (exists)"; continue; }
        wait_quiet || exit 1
        ( cd "$T/percore" && perf stat -x, -e instructions:u,task-clock -o "$f.perf" -- \
            taskset -c "$CORE" env OMP_NUM_THREADS=1 MKL_NUM_THREADS=1 PYTHONDONTWRITEBYTECODE=1 \
            PYTHONPATH="$T/mod-$arm:$T/src-new/sts2-pycli/python" \
            "$PY" "$H/ppo_collect.py" Ironclad "$CK" roll "$f.txt" "$n" 1 > "$f.log" 2>&1 )
        grep -q "^wheel $T/mod-$arm/" "$f.log" || { log "$f did not load mod-$arm"; exit 1; }
        ! grep -q RUN_ERRORS "$f.log" || { log "$f had run errors"; exit 1; }
        log "percore $arm-$i: $(grep wall_s "$f.log") task-clock_ms=$(grep task-clock "$f.perf" | cut -d, -f1)"
      done
    done
    python3 -I "$HERE/summarize.py" --percore "$T/percore" "$n"
    ;;
  summary)
    python3 -I "$HERE/summarize.py" "$RES/train_iter.jsonl"
    python3 -I "$HERE/summarize.py" --percore "$T/percore" "${NSEEDS:-6}"
    ;;
  *) sed -n '2,20p' "$0"; exit 2 ;;
esac
