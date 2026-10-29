#!/usr/bin/env bash
# Shadow-HP pilot: run headless_eval BOTH shadow-on and shadow-off (wire) over
# the same seeds on the CURRENT wheel, into two trace dirs, so diag_hp_flow.py
# can test whether lossless combat state cuts the per-act combat bleed.
set -u
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1
PY=.venv/Scripts/python.exe
BASE=1000
N=12            # seeds 1000..1011
CHUNK=3         # seeds per worker
SHADOW_DIR=logs/hpprobe/shadow
WIRE_DIR=logs/hpprobe/wire
mkdir -p "$SHADOW_DIR" "$WIRE_DIR"

launch () {  # $1=flag(--shadow|"")  $2=trace_dir  $3=seed_base  $4=runs  $5=tag
  PYTHONPATH=python "$PY" python/headless_eval.py \
    --seed-base "$3" --runs "$4" $1 --trace-dir "$2" \
    --sims-regular 1000 --sims-boss 4000 \
    > "logs/hpprobe/${5}.log" 2>&1
}

pids=()
s=$BASE
while [ $s -lt $((BASE+N)) ]; do
  launch "--shadow" "$SHADOW_DIR" "$s" "$CHUNK" "shadow_$s" &
  pids+=($!)
  launch "" "$WIRE_DIR" "$s" "$CHUNK" "wire_$s" &
  pids+=($!)
  s=$((s+CHUNK))
done
echo "launched ${#pids[@]} workers: ${pids[*]}"
for p in "${pids[@]}"; do wait "$p"; done
echo "ALL WORKERS DONE"
echo "=== shadow traces ==="; ls "$SHADOW_DIR" | wc -l
echo "=== wire traces ===";   ls "$WIRE_DIR"   | wc -l
