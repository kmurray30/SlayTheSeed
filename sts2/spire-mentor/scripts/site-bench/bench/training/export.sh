#!/usr/bin/env bash
# Export the base and new source trees for the training-speed benchmark.
# Read-only git (rev-parse / archive / ls-files); the checkout is never modified.
#   BENCH_DIR=<scratch> bash export.sh   -> $BENCH_DIR/training/{src-base,src-new,ckpt,EXPORTED.env}
# BASE_REF / NEW_REF override the two commits (the published page: the defaults;
# its export ran with NEW_REF=HEAD when HEAD was 6d1918237).
set -euo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$HERE/../env.sh"   # REPO, BENCH_DIR, SB
T=$BENCH_DIR/training
BASE_REF=${BASE_REF:-d3ea66a51}
NEW_REF=${NEW_REF:-6d1918237}
CKPT_NAME=ppo_fullrun_ironclad_plr_it550.pt
mkdir -p "$T/ckpt"

bsha=$(git -C "$REPO" rev-parse "$BASE_REF^{commit}")
nsha=$(git -C "$REPO" rev-parse "$NEW_REF^{commit}")
for tag in base new; do
  sha=$bsha; [ "$tag" = new ] && sha=$nsha
  d=$T/src-$tag
  if [ -e "$d" ]; then mv "$d" "$d.old-$(date +%s)"; fi
  mkdir -p "$d"
  git -C "$REPO" archive --format=tar "$sha" sts2-rs sts2-pycli/python sts2-pycli/data/ppo/champions.toml \
    | tar -x -C "$d"
  echo "$sha" > "$d/EXPORTED_SHA"
  echo "exported $tag=$sha -> $d ($(du -sh "$d" | cut -f1))"
done

# The champion checkpoint, from the NEW commit's tree (a real torch zip, not an LFS pointer).
git -C "$REPO" archive --format=tar "$nsha" "sts2-pycli/data/ppo/$CKPT_NAME" | tar -x -O > "$T/ckpt/$CKPT_NAME"
if head -c 64 "$T/ckpt/$CKPT_NAME" | grep -q 'git-lfs'; then echo "ERROR: checkpoint is an LFS pointer"; exit 1; fi
blob_new=$(git -C "$REPO" rev-parse "$nsha:sts2-pycli/data/ppo/$CKPT_NAME")
blob_base=$(git -C "$REPO" rev-parse "$bsha:sts2-pycli/data/ppo/$CKPT_NAME" 2>/dev/null || echo absent)
{
  echo "BASE_SHA=$bsha"
  echo "NEW_SHA=$nsha"
  echo "CKPT=$T/ckpt/$CKPT_NAME"
  echo "CKPT_BLOB_NEW=$blob_new"
  echo "CKPT_BLOB_BASE=$blob_base"
  echo "CKPT_SHA256=$(sha256sum "$T/ckpt/$CKPT_NAME" | cut -d' ' -f1)"
  echo "EXPORTED_AT=$(date -u +%FT%TZ)"
} > "$T/EXPORTED.env"
cat "$T/EXPORTED.env"
