# shellcheck shell=bash disable=SC2034
# Sourced by every driver under scripts/site-bench/bench/. It sets the paths the
# drivers share and nothing else:
#   REPO       the checkout whose sources are exported and built (default: the
#              checkout this file lives in)
#   BENCH_DIR  scratch root for exports, builds, raw results and the analysis
#              (REQUIRED, no default; ~13 GB at the end of a full run)
#   SB         the harness directory, $REPO/scripts/site-bench
# Every other path a driver uses is either below BENCH_DIR or has its own
# variable with a default (PY, GAME_DIR, SANSB_SRC, STS2CORE_SRC, PW_ROOT, ...).
BENCH_HOME=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO=${REPO:-$(cd "$BENCH_HOME/../../.." && pwd)}
: "${BENCH_DIR:?set BENCH_DIR to a scratch directory for builds and results (see scripts/site-bench/README.md)}"
mkdir -p "$BENCH_DIR"
BENCH_DIR=$(cd "$BENCH_DIR" && pwd)
SB=$REPO/scripts/site-bench
export REPO BENCH_DIR
