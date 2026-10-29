#!/usr/bin/env bash
# Export clean sources of the two engines (git archive; the repo is not touched).
#   BENCH_DIR=<scratch> bash export.sh   -> $BENCH_DIR/ours/src-base (d3ea66a51), src-new (6d1918237)
# BASE_REF / NEW_REF override the two commits (the published page: the defaults).
set -euo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$HERE/../env.sh"   # REPO, BENCH_DIR, SB
O=$BENCH_DIR/ours
BASE_REF=${BASE_REF:-d3ea66a51}
NEW_REF=${NEW_REF:-6d1918237}
bsha=$(git -C "$REPO" rev-parse "$BASE_REF^{commit}")
nsha=$(git -C "$REPO" rev-parse "$NEW_REF^{commit}")
for T in base new; do
  SHA=$bsha; [ "$T" = new ] && SHA=$nsha
  D=$O/src-$T
  if [ -e "$D" ]; then mv "$D" "$O/old-src-$T-$(date +%s)"; fi
  mkdir -p "$D"
  git -C "$REPO" archive --format=tar "$SHA" sts2-rs | tar -x -C "$D"
  echo "exported $T=$SHA -> $D"
done
printf 'BASE_SHA=%s\nNEW_SHA=%s\nEXPORT_DATE=%s\n' "$bsha" "$nsha" "$(date -u +%FT%TZ)" > "$O/EXPORTED.env"
# What differs in the harness-relevant API between the two trees
git -C "$REPO" log --oneline -1 "$bsha"
git -C "$REPO" log --oneline -1 "$nsha"
git -C "$REPO" rev-list --count "$bsha..$nsha"
