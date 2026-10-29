#!/usr/bin/env bash
# Watchdog-free replays of training iteration 0, behind the "typical iteration"
# figures (apply_xcheck.py reads their output). Needs training/export.sh and
# training/build.sh first.
#
#   run.sh pool   ARM TAG [poolrep.py args]     -> $BENCH_DIR/training/diag/out/pool-ARM-TAG.jsonl
#   run.sh replay ARM TAG [replay.py args]      -> $BENCH_DIR/training/diag/out/ARM-TAG.jsonl
#   run.sh trace  ARM TAG [trace_game.py args]  -> $BENCH_DIR/training/diag/out/trace-ARM-TAG.jsonl
#
# ARM: base (previous engine, plain build), new (new engine, plain) or pgo (new
# engine, PGO build), i.e. $BENCH_DIR/training/mod-ARM with its source tree.
# Same interpreter, PYTHONPATH layout and environment as train_iter_bench.py
# gives the trainer's workers. Env: BENCH_DIR (required), PY (python with torch,
# default $REPO/sts2-pycli/.venv/bin/python), GPU (default 1).
set -euo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$HERE/../../env.sh"   # REPO, BENCH_DIR, SB
T=$BENCH_DIR/training
OUT=$T/diag/out
PY=${PY:-$REPO/sts2-pycli/.venv/bin/python}
GPU=${GPU:-1}
[ $# -ge 3 ] || { echo "usage: run.sh pool|replay|trace base|new|pgo TAG [args...]" >&2; exit 2; }
mode=$1 arm=$2 tag=$3; shift 3
case "$mode" in
  pool)   script=poolrep.py;    name=pool-$arm-$tag ;;
  replay) script=replay.py;     name=$arm-$tag ;;
  trace)  script=trace_game.py; name=trace-$arm-$tag ;;
  *) echo "mode: pool, replay or trace" >&2; exit 2 ;;
esac
case "$arm" in
  base) SRC=$T/src-base/sts2-pycli/python ;;
  new|pgo) SRC=$T/src-new/sts2-pycli/python ;;
  *) echo "arm: base, new or pgo" >&2; exit 2 ;;
esac
mkdir -p "$OUT"
# The extension must be the one training/build.sh recorded.
( cd "$T" && grep " mod-$arm/" MODS.sha256 | sha256sum -c --quiet - )
cd "$OUT"
exec env -u PYTHONHOME -u STS2_PPO_FEATURES -u STS2_WHEEL_PIN -u STS2_SKIP_WHEEL_CHECK \
  -u STS2_RS_SRC -u MKL_NUM_THREADS -u WANDB_MODE \
  PYTHONPATH="$T/mod-$arm:$SRC" PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 \
  STS2_RUNAI_POTION_SWAP=1 OMP_NUM_THREADS=1 STS2_ALLOW_PLAIN_WHEEL=1 \
  CUDA_DEVICE_ORDER=PCI_BUS_ID CUDA_VISIBLE_DEVICES="$GPU" \
  "$PY" "$HERE/$script" --ckpt "$T/ckpt/ppo_fullrun_ironclad_plr_it550.pt" \
  --out "$OUT/$name.jsonl" "$@" 2> >(tee "$OUT/$name.log" >&2)
