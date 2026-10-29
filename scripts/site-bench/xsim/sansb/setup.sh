#!/usr/bin/env bash
# Build the cross-engine harnesses against sansb/sts2-sim.
#
# usage: setup.sh CLONE_DIR TARGET_DIR [COMMIT]
#
# Clones https://github.com/sansb/sts2-sim into CLONE_DIR if it is not there,
# checks out COMMIT (detached; default: the commit these harnesses target),
# copies the three example harnesses into v0.111.0/engine/examples/, and
# builds the shipped binary plus four examples with the crate's own release
# profile into TARGET_DIR. Nothing is committed to the clone.
set -euo pipefail

if [ $# -lt 2 ]; then
    echo "usage: $0 CLONE_DIR TARGET_DIR [COMMIT]" >&2
    exit 2
fi
clone=$1
target=$2
commit=${3:-c61336cf05d289ae01ab9254beaa62c1b4ee407c}
here=$(cd "$(dirname "$0")" && pwd)

if [ ! -d "$clone/.git" ]; then
    git clone https://github.com/sansb/sts2-sim "$clone"
fi
git -C "$clone" fetch origin
git -C "$clone" checkout --detach "$commit"
echo "sansb/sts2-sim at $(git -C "$clone" rev-parse HEAD)"

cp "$here/xsim_w1_site.rs" "$here/xsim_w2_toadpoles.rs" "$here/xsim_hp_bands.rs" \
    "$clone/v0.111.0/engine/examples/"

cd "$clone/v0.111.0/engine"
# rust-toolchain.toml at the repo root pins rustc 1.97.1.
CARGO_TARGET_DIR="$target" cargo build --locked --release \
    --bin sts-sim \
    --example xsim_w1_site --example xsim_w2_toadpoles \
    --example xsim_hp_bands --example eval_throughput
echo "built into $target/release (sts-sim) and $target/release/examples/"
