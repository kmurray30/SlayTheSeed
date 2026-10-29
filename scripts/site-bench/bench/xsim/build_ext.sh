#!/usr/bin/env bash
# Clone (if missing), check out and build the two external engines plus our
# cross-engine harnesses for them, where measure_xsim.sh expects them:
#   sansb/sts2-sim        -> $BENCH_DIR/sansb/target/release/{sts-sim,examples/*}
#   lain-wood/sts2core    -> $BENCH_DIR/sts2core/target/release/{xsim_w1_site,xsim_w2_toadpoles,bench}
#   BENCH_DIR=<scratch> bash build_ext.sh [sansb|sts2core|both]   (default both)
# Env: SANSB_SRC / STS2CORE_SRC (the clones; default $BENCH_DIR/ext/sts2-sim and
#   $BENCH_DIR/ext/sts2core), SANSB_COMMIT / STS2CORE_COMMIT (default: the commits
#   the published page measured), STS2CORE_TOOLCHAIN (default 1.96.0, the stable
#   rustc the published sts2core binaries were built with; sansb pins its own
#   1.97.1 in rust-toolchain.toml).
# Nothing is committed to either clone: the harness files are copied in untracked.
# Harness sources and what they do: ../../xsim/{sansb,sts2core}/README.md.
set -euo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$HERE/../env.sh"   # REPO, BENCH_DIR, SB
SANSB_SRC=${SANSB_SRC:-$BENCH_DIR/ext/sts2-sim}
CORE_SRC=${STS2CORE_SRC:-$BENCH_DIR/ext/sts2core}
SANSB_COMMIT=${SANSB_COMMIT:-c61336cf05d289ae01ab9254beaa62c1b4ee407c}
STS2CORE_COMMIT=${STS2CORE_COMMIT:-3b2d969d025b9e64bc96b9a15928bd2a0d824bf2}
STS2CORE_TOOLCHAIN=${STS2CORE_TOOLCHAIN:-1.96.0}
WHICH=${1:-both}
# Build hygiene: nothing from the caller's shell may change codegen.
unset RUSTFLAGS CARGO_BUILD_RUSTFLAGS CARGO_ENCODED_RUSTFLAGS CARGO_TARGET_DIR RUSTC_WORKSPACE_WRAPPER RUSTC_WRAPPER
mkdir -p "$BENCH_DIR/ext" "$BENCH_DIR/sansb" "$BENCH_DIR/sts2core"

build_sansb() {
  # The sansb harness directory ships its own setup script (clone, checkout,
  # copy the examples in, build with the crate's release profile).
  bash "$SB/xsim/sansb/setup.sh" "$SANSB_SRC" "$BENCH_DIR/sansb/target" "$SANSB_COMMIT"
}

build_sts2core() {
  [ -d "$CORE_SRC/.git" ] || git clone https://github.com/lain-wood/sts2core "$CORE_SRC"
  git -C "$CORE_SRC" fetch origin
  git -C "$CORE_SRC" checkout --detach "$STS2CORE_COMMIT"
  echo "lain-wood/sts2core at $(git -C "$CORE_SRC" rev-parse HEAD)"
  cp "$SB/xsim/sts2core/xsim_w1_site.rs" "$SB/xsim/sts2core/xsim_w2_toadpoles.rs" "$CORE_SRC/src/bin/"
  # W1 finds sts2core's data/*.json through the checkout path compiled into the binary.
  ( cd "$CORE_SRC" && CARGO_TARGET_DIR=$BENCH_DIR/sts2core/target \
      cargo "+$STS2CORE_TOOLCHAIN" build --release --bin xsim_w1_site --bin xsim_w2_toadpoles --bin bench )
  echo "built into $BENCH_DIR/sts2core/target/release"
}

case "$WHICH" in
  sansb) build_sansb ;;
  sts2core) build_sts2core ;;
  both) build_sansb; build_sts2core ;;
  *) echo "usage: $0 [sansb|sts2core|both]"; exit 2 ;;
esac
