#!/usr/bin/env bash
# Serial measurement window for the 2026-10 benchmark page. Runs every block in
# order, one at a time; each sub-driver gates on a quiet host before every stage.
# Resumable: a block that finished writes runs/done/<block>; re-running skips it.
#   BENCH_DIR=<scratch> bash measure_all.sh            # everything not yet done
#   BENCH_DIR=<scratch> bash measure_all.sh <block>... # only these blocks (still skips done ones)
# Everything it needs must be built first (README.md, "Reproducing the whole
# benchmark"): ours/export.sh + ours/build.sh, wasm/build_wasm.sh,
# xsim/build_ext.sh, csharp/build_cs.sh + `csharp/measure_cs.sh prep`,
# training/export.sh + training/build.sh, and facts/hardware.md by hand.
set -uo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$HERE/env.sh"   # REPO, BENCH_DIR, SB
B=$BENCH_DIR
W=$REPO
R=$B/runs; DONE=$R/done; mkdir -p "$DONE" "$B/facts"
export LOAD_MAX=${LOAD_MAX:-1.5}
log() { echo "[$(date -u +%FT%TZ)] $*" | tee -a "$R/all.log"; }
snap() { log "conditions[$1]: load=$(cut -d' ' -f1-3 /proc/loadavg) mem_avail_gb=$(awk '/MemAvailable/{print int($2/1048576)}' /proc/meminfo) top=[$(ps -eo pcpu=,comm= --sort=-pcpu | head -4 | tr -s ' ' | tr '\n' ';')]"; }

provenance() {
  bash "$HERE/facts/hw_snapshot.sh" "$W" > "$B/facts/hw_snapshot_measure.out" 2>&1
  bash "$HERE/ours/measure_ours.sh" provenance
  bash "$HERE/xsim/measure_xsim.sh" provenance; }
ours_core()  { bash "$HERE/ours/measure_ours.sh" w1 hint adv w2; }
xsim_core()  { bash "$HERE/xsim/measure_xsim.sh" w1 w2 own; }
perenc() {  # untimed same-work check: our per-encounter baseline with independent seeds
  mkdir -p "$R/perenc"
  for e in $(cat "$HERE/w1_encounters.txt"); do
    taskset -c 20 "$B/bin/new/site_random_bench" --encounters "$e" --seeds 200 --seed-start 0 --reps 50 \
      > "$R/perenc/$e.json" 2> /dev/null
  done
  python3 -I "$HERE/xsim/perenc_compare.py" "$HERE" "$R/perenc" "$R/xsim/w1/sansb-inst-1.out" "$R/xsim/w1/core-inst-1.out" \
    > "$R/perenc/compare.txt" 2>&1; tail -3 "$R/perenc/compare.txt" | tee -a "$R/all.log"; }
scale_rust() {
  bash "$HERE/ours/measure_ours.sh" scale_w1 scale_w2 scale_hint
  SCALE_PRESETS="1core 1core-smt phys24 smt48" bash "$HERE/ours/measure_ours.sh" scale_w1_base
  bash "$HERE/xsim/measure_xsim.sh" scale; }
wasm()       { bash "$HERE/wasm/measure_wasm.sh" node chrome; }
# The published C cells (C1-C6) were run after the training block, gated on the
# whole host only (measure_cs.sh: C_CPU_BUSY_MAX=100, now the default); C6 was
# added then. This block runs the same cells in one go.
cs()         { mkdir -p "$B/csharp" && ( cd "$B/csharp" && bash "$HERE/csharp/measure_cs.sh" A0 A && \
                 WARM=220 bash "$HERE/csharp/measure_cs.sh" B2 B4 B8 B16 C1 C2 C3 C4 C5 C6 ); }
training()   { CORE=20 bash "$HERE/training/measure.sh" percore 3 && GPU=${GPU:-1} bash "$HERE/training/measure.sh" full 2; }
closing() {
  bash "$HERE/facts/hw_snapshot.sh" "$W" > "$B/facts/hw_snapshot_end.out" 2>&1
  bash "$HERE/training/measure.sh" summary > "$R/training_summary.txt" 2>&1
  [ -f "$HERE/wasm/measure_wasm.sh" ] && bash "$HERE/wasm/measure_wasm.sh" summary > "$R/wasm_summary.txt" 2>&1
  return 0; }

BLOCKS=(provenance ours_core xsim_core perenc scale_rust wasm cs training closing)
[ $# -gt 0 ] && BLOCKS=("$@")
log "=== measure_all start: ${BLOCKS[*]} (LOAD_MAX=$LOAD_MAX)"
for b in "${BLOCKS[@]}"; do
  [ -f "$DONE/$b" ] && { log "skip $b (done)"; continue; }
  snap "before $b"; log "== block $b"; t0=$SECONDS
  "$b"; rc=$?
  log "== block $b rc=$rc in $((SECONDS - t0)) s"; snap "after $b"
  [ $rc -eq 0 ] && date -u +%FT%TZ > "$DONE/$b"
done
log "=== measure_all end"
