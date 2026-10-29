#!/usr/bin/env bash
# Build the PLAIN sts2_rs extension for base and new, exactly as
# es/site-bench/run.sh build_tree() did (= `maturin develop --release`: release
# profile, fat LTO, cgu 1, features pyo3/extension-module, no PGO, no target-cpu),
# into private target dirs; copy each to mod-<tag>/sts2_rs.abi3.so.
# Also freezes a COPY of the checkout venv's PGO wheel into mod-pgo/: build that
# wheel first with `just sim-engine-pgo` in the checkout (PY's sts2_rs must report
# build_info() pgo=True; measure.sh checks it per run).
#   BENCH_DIR=<scratch> bash build.sh [base|new|pgo|all]   (default all)
set -euo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$HERE/../env.sh"   # REPO, BENCH_DIR, SB
T=$BENCH_DIR/training
WT=$REPO
PY=${PY:-$WT/sts2-pycli/.venv/bin/python}
JOBS=${JOBS:-12}
what=${1:-all}
unset RUSTFLAGS CARGO_BUILD_RUSTFLAGS CARGO_ENCODED_RUSTFLAGS CARGO_TARGET_DIR RUSTC_WORKSPACE_WRAPPER

build_one() {
  local tag=$1 src=$T/src-$1/sts2-rs td=$T/target-$1 out=$T/mod-$1 t0=$SECONDS
  mkdir -p "$out"
  echo "build $tag start $(date -u +%T) src=$src td=$td"
  ( cd "$src" && RUSTC_WRAPPER='' CARGO_TARGET_DIR=$td PYO3_PYTHON=$PY \
      cargo build --locked --release -j "$JOBS" -p sts2-py --features pyo3/extension-module )
  cp "$td/release/libsts2_rs.so" "$out/sts2_rs.abi3.so"
  echo "build $tag done after $((SECONDS - t0)) s"
}

freeze_pgo() {
  # The venv's PGO wheel, byte-for-byte, so a later `uv run` / rebuild in the
  # shared checkout cannot change what the measurement stage loads.
  local so
  so=$("$PY" -c 'import sts2_rs,os; m=getattr(sts2_rs,"sts2_rs",sts2_rs); print(m.__file__)')
  mkdir -p "$T/mod-pgo"
  cp "$so" "$T/mod-pgo/sts2_rs.abi3.so"
  echo "froze PGO wheel $so -> $T/mod-pgo/sts2_rs.abi3.so"
}

case "$what" in
  base) build_one base ;;
  new) build_one new ;;
  pgo) freeze_pgo ;;
  all) freeze_pgo; build_one new; build_one base ;;
  *) echo "usage: $0 [base|new|pgo|all]"; exit 2 ;;
esac
( cd "$T" && sha256sum mod-*/sts2_rs.abi3.so 2>/dev/null ) > "$T/MODS.sha256" || true
cat "$T/MODS.sha256"
