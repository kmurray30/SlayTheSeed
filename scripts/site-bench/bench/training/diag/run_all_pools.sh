#!/usr/bin/env bash
# The three full 960-game iteration-0 replays through a play_batch-shaped pool
# (46 workers, no stall watchdog), one build at a time: pgo, new, base. Run it
# on a quiet host; it records the host's busy share and load before each.
# Env: BENCH_DIR (required), ARMS (default "pgo new base"), PROCS (default 46),
# plus whatever run.sh reads.
set -uo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$HERE/../../env.sh"   # REPO, BENCH_DIR, SB
OUT=$BENCH_DIR/training/diag/out
mkdir -p "$OUT"
busy() {  # whole-machine CPU busy % over 5 s, from /proc/stat
  local a b c d e f g h t1 i1 t2 i2
  read -r _ a b c d e f g h _ < /proc/stat; t1=$((a+b+c+d+e+f+g+h)); i1=$((d+e))
  sleep 5
  read -r _ a b c d e f g h _ < /proc/stat; t2=$((a+b+c+d+e+f+g+h)); i2=$((d+e))
  echo $(( 100 * ((t2-t1) - (i2-i1)) / (t2-t1) ))
}
for arm in ${ARMS:-pgo new base}; do
  echo "[all $(date +%T)] $arm start: busy=$(busy)% load=$(cut -d' ' -f1-3 /proc/loadavg)" | tee -a "$OUT/pool-all.log"
  bash "$HERE/run.sh" pool "$arm" full --procs "${PROCS:-46}" > /dev/null 2>&1
  rc=$?
  echo "[all $(date +%T)] $arm end rc=$rc load=$(cut -d' ' -f1-3 /proc/loadavg)" | tee -a "$OUT/pool-all.log"
done
