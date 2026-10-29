#!/usr/bin/env bash
# Round-7 win-rate sweep: DLL arm (v37wr) then engine arm (e37), fresh-vs-fresh
# on the same wheel (e2ab8defe8f153e2) and the same 60 seeds.
#
#   DLL arm    : one process per seed, 10 workers  -> logs/wr_v37wr/s<seed>/
#   engine arm : 10 shards of 6 runs               -> logs/eng_e37/shard_<base>.log
#
# Both arms write progress.log incrementally so a stuck sweep is visible without
# waiting for the tail.
set -uo pipefail

PYCLI="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PY="$PYCLI/.venv/Scripts/python.exe"
cd "$PYCLI" || exit 1

WR_TAG=v37wr
ENG_TAG=e37
SEED_LO=1000
SEED_HI=1059
WORKERS=10

WR_DIR="logs/wr_${WR_TAG}"
ENG_DIR="logs/eng_${ENG_TAG}"
mkdir -p "$WR_DIR" "$ENG_DIR" logs/diag

stamp() { date -u +%Y-%m-%dT%H:%M:%SZ; }

# ---------------------------------------------------------------- DLL arm ---
echo "sweep ${WR_TAG}: seeds ${SEED_LO}..${SEED_HI} workers=${WORKERS} start $(stamp)" \
  | tee "logs/wr_${WR_TAG}_launch.log"

run_seed() {
  local seed="$1"
  STS2_MIRROR_OBSERVE=1 \
  STS2_MIRROR_LOG="logs/diag/mirror_${WR_TAG}.jsonl" \
  "$PY" python/headless_eval.py \
      --runs 1 --character Ironclad --seed-base "$seed" \
      --sims-regular 1000 --sims-boss 4000 \
      --out-dir "${WR_DIR}/s${seed}" \
      > "${WR_DIR}/s${seed}.log" 2>&1
  echo "  seed ${seed} done rc=$? $(date -u +%H:%M:%S)" >> "${WR_DIR}/progress.log"
}

pids=()
for seed in $(seq "$SEED_LO" "$SEED_HI"); do
  run_seed "$seed" &
  pids+=($!)
  while [ "$(jobs -rp | wc -l)" -ge "$WORKERS" ]; do sleep 2; done
done
wait
echo "SWEEP_DONE ${WR_TAG} $(stamp)" | tee -a "logs/wr_${WR_TAG}_launch.log"

# ------------------------------------------------------------- engine arm ---
echo "sweep ${ENG_TAG}: seeds ${SEED_LO}..${SEED_HI} shards=10x6 start $(stamp)" \
  | tee "logs/eng_${ENG_TAG}_launch.log"

run_shard() {
  local base="$1"
  "$PY" python/play_full_run_ppo.py 6 \
      --seed-base "$base" --character Ironclad \
      --sims 1000 --boss-sims 4000 --string-seeds \
      > "${ENG_DIR}/shard_${base}.log" 2>&1
  echo "  shard ${base} done rc=$? $(date -u +%H:%M:%S)" >> "${ENG_DIR}/progress.log"
}

for base in $(seq "$SEED_LO" 6 "$SEED_HI"); do
  run_shard "$base" &
  while [ "$(jobs -rp | wc -l)" -ge "$WORKERS" ]; do sleep 2; done
done
wait
echo "SWEEP_DONE ${ENG_TAG} $(stamp)" | tee -a "logs/eng_${ENG_TAG}_launch.log"
