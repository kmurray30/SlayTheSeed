#!/usr/bin/env bash
# Build OUR engine's native harnesses for both engines from the clean exports
# (export.sh), with the CURRENT checkout's harness files copied into BOTH trees
# so both engines run byte-identical harness code.
#   BENCH_DIR=<scratch> bash build.sh [base|new|both]      (default both, in parallel)
# Outputs: $BENCH_DIR/bin/{base,new}/{site_random_bench,site_random_bench_orig,xsim_toadpoles,
#          hint_bench,mod/sts2_rs.abi3.so,MD5,HARNESS_MD5}
# PY (default python3) is only PYO3_PYTHON for the abi3 extension build.
set -uo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$HERE/../env.sh"   # REPO, BENCH_DIR, SB
B2=$BENCH_DIR
O=$B2/ours
W=$REPO
PY=${PY:-$(command -v python3)}
JOBS=${JOBS:-12}
WHICH=${1:-both}

# Build hygiene (as es/site-bench/run.sh): nothing from the caller's shell may
# change codegen.
unset RUSTFLAGS CARGO_BUILD_RUSTFLAGS CARGO_ENCODED_RUSTFLAGS CARGO_TARGET_DIR RUSTC_WORKSPACE_WRAPPER
for v in $(compgen -e); do
  case "$v" in CARGO_PROFILE_*|CARGO_BUILD_*|STS2_WHEEL_*|CARGO_INCREMENTAL|RUSTDOCFLAGS|RUSTUP_TOOLCHAIN) unset "$v" ;; esac
done

# Harness snapshot: copied once into $O/harness, then into both trees.
mkdir -p "$O/harness" "$O/logs"
cp "$W/sts2-rs/crates/sts2-bench/examples/site_random_bench.rs" \
   "$W/sts2-rs/crates/sts2-bench/examples/xsim_toadpoles.rs" \
   "$W/sts2-rs/crates/sts2-workbench/examples/hint_bench.rs" "$O/harness/"
(cd "$O/harness" && md5sum *.rs) > "$O/harness/MD5"
# The pre-#762 harness (site_random_bench.rs as committed at NEW_SHA), built as
# site_random_bench_orig for smoke.sh's byte-identity check of --legacy-spawn.
if [ ! -f "$O/harness-orig/site_random_bench.rs" ]; then
  mkdir -p "$O/harness-orig"
  nsha=$(sed -n 's/^NEW_SHA=//p' "$O/EXPORTED.env")
  git -C "$W" show "$nsha:sts2-rs/crates/sts2-bench/examples/site_random_bench.rs" > "$O/harness-orig/site_random_bench.rs" || exit 1
fi

build_tree() {
  local T=$1; local SRC=$O/src-$1/sts2-rs TD=$O/target-$1 OUT=$B2/bin/$1 t0=$SECONDS
  mkdir -p "$OUT/mod"
  cp "$O/harness/site_random_bench.rs" "$O/harness/xsim_toadpoles.rs" "$SRC/crates/sts2-bench/examples/"
  # the pre-edit harness, for the byte-identity check of the default output
  cp "$O/harness-orig/site_random_bench.rs" "$SRC/crates/sts2-bench/examples/site_random_bench_orig.rs"
  mkdir -p "$SRC/crates/sts2-workbench/examples"
  cp "$O/harness/hint_bench.rs" "$SRC/crates/sts2-workbench/examples/"
  export RUSTC_WRAPPER='' CARGO_TARGET_DIR=$TD PYO3_PYTHON=$PY
  cd "$SRC" || return 1
  echo "build $T start $(date -u +%T) rustc=$(rustc -V)"
  cargo build --locked --release -j "$JOBS" -p sts2-bench \
      --example site_random_bench --example site_random_bench_orig --example xsim_toadpoles || return 1
  cargo build --locked --release -j "$JOBS" -p sts2-workbench --example hint_bench || return 1
  cargo build --locked --release -j "$JOBS" -p sts2-py --features pyo3/extension-module || return 1
  cp "$TD/release/examples/site_random_bench" "$TD/release/examples/site_random_bench_orig" \
     "$TD/release/examples/xsim_toadpoles" "$TD/release/examples/hint_bench" "$OUT/"
  cp "$TD/release/libsts2_rs.so" "$OUT/mod/sts2_rs.abi3.so"
  (cd "$OUT" && md5sum site_random_bench site_random_bench_orig xsim_toadpoles hint_bench mod/sts2_rs.abi3.so) > "$OUT/MD5"
  cp "$O/harness/MD5" "$OUT/HARNESS_MD5"
  echo "build $T done after $((SECONDS - t0)) s"
}

case "$WHICH" in
  base|new) build_tree "$WHICH" > "$O/logs/build-$WHICH.log" 2>&1; echo "exit $?" ;;
  both)
    ( build_tree base ) > "$O/logs/build-base.log" 2>&1 & pb=$!
    ( build_tree new )  > "$O/logs/build-new.log"  2>&1 & pn=$!
    rb=0; rn=0; wait $pb || rb=$?; wait $pn || rn=$?
    echo "build exit: base=$rb new=$rn"
    [ "$rb" = 0 ] && [ "$rn" = 0 ] ;;
esac
