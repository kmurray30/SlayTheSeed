#!/usr/bin/env bash
# Build the browser workbench wasm pkg (the shipped recipe: sts2-pycli/scripts/
# workbench_static.py `wasm` = cargo --profile wasm-release --target
# wasm32-unknown-unknown -p sts2-workbench-wasm, wasm-bindgen --target web,
# wasm-opt -O3) for both engines from the clean exports in $BENCH_DIR/ours/src-{base,new}
# (../ours/export.sh; read-only: nothing is written into them; own CARGO_TARGET_DIRs here).
#   BENCH_DIR=<scratch> bash build_wasm.sh [base|new|both]     (default both, in parallel)
# Outputs: $BENCH_DIR/wasm/pkg-{base,new}/ and MD5.
# Env: BINDGEN (wasm-bindgen CLI, default on PATH; must equal the Cargo.lock version,
#   0.2.120 for the published trees), WASM_OPT (default
#   $REPO/sts2-pycli/web/node_modules/.bin/wasm-opt, binaryen 132 for the published run).
set -uo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$HERE/../env.sh"   # REPO, BENCH_DIR, SB
B2=$BENCH_DIR
D=$B2/wasm
WASM_OPT=${WASM_OPT:-$REPO/sts2-pycli/web/node_modules/.bin/wasm-opt}
BINDGEN=${BINDGEN:-wasm-bindgen}
JOBS=${JOBS:-12}
WHICH=${1:-both}

# Build hygiene (as es/site-bench/run.sh): nothing from the caller's shell may
# change codegen. RUSTFLAGS would REPLACE .cargo/config.toml's wasm32 rustflags
# (the getrandom backend cfg), so it must be unset, not emptied.
unset RUSTFLAGS CARGO_BUILD_RUSTFLAGS CARGO_ENCODED_RUSTFLAGS CARGO_TARGET_DIR RUSTC_WORKSPACE_WRAPPER
for v in $(compgen -e); do
  case "$v" in CARGO_PROFILE_*|CARGO_BUILD_*|STS2_WHEEL_*|CARGO_INCREMENTAL|RUSTDOCFLAGS|RUSTUP_TOOLCHAIN) unset "$v" ;; esac
done
mkdir -p "$D/logs"

build_tree() {  # build_tree base|new
  local T=$1 SRC=$B2/ours/src-$1/sts2-rs TD=$D/target-$1 OUT=$D/pkg-$1 t0=$SECONDS lockv havev
  lockv=$(grep -A1 '^name = "wasm-bindgen"$' "$SRC/Cargo.lock" | sed -n 's/^version = "\(.*\)"/\1/p')
  havev=$("$BINDGEN" --version | awk '{print $2}')
  [ "$lockv" = "$havev" ] || { echo "wasm-bindgen CLI $havev != $T Cargo.lock $lockv"; return 1; }
  export RUSTC_WRAPPER='' CARGO_TARGET_DIR=$TD
  cd "$SRC" || return 1
  echo "build $T start $(date -u +%T) rustc=$(rustc -V) bindgen=$havev wasm-opt=$("$WASM_OPT" --version)"
  cargo build --locked --profile wasm-release --target wasm32-unknown-unknown -j "$JOBS" -p sts2-workbench-wasm || return 1
  mkdir -p "$OUT.tmp" && find "$OUT.tmp" -mindepth 1 -delete
  "$BINDGEN" --target web --out-dir "$OUT.tmp" "$TD/wasm32-unknown-unknown/wasm-release/sts2_workbench_wasm.wasm" || return 1
  "$WASM_OPT" -O3 -o "$OUT.tmp/sts2_workbench_wasm_bg.wasm" "$OUT.tmp/sts2_workbench_wasm_bg.wasm" || return 1
  [ -d "$OUT" ] && mv "$OUT" "$OUT.old-$(date +%s)"
  mv "$OUT.tmp" "$OUT"
  echo "build $T done after $((SECONDS - t0)) s"
}

case "$WHICH" in
  base|new) build_tree "$WHICH" > "$D/logs/build-$WHICH.log" 2>&1; rc=$? ;;
  both)
    ( build_tree base ) > "$D/logs/build-base.log" 2>&1 & pb=$!
    ( build_tree new )  > "$D/logs/build-new.log"  2>&1 & pn=$!
    rb=0; rn=0; wait $pb || rb=$?; wait $pn || rn=$?
    echo "build exit: base=$rb new=$rn"; rc=$(( rb || rn )) ;;
  *) echo "usage: $0 [base|new|both]"; exit 2 ;;
esac
(cd "$D" && md5sum pkg-*/sts2_workbench_wasm_bg.wasm pkg-*/sts2_workbench_wasm.js 2>/dev/null) > "$D/MD5"
cat "$D/MD5"
exit "$rc"
