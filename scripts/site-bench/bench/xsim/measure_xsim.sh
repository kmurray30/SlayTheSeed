#!/usr/bin/env bash
# The cross-engine driver for the EXTERNAL Rust engines (sansb/sts2-sim,
# lain-wood/sts2core), with the same quiet gate, perf stat, pinning and preset
# definitions as ../ours/measure_ours.sh, and our new engine interleaved in every
# round so host drift hits every engine alike.
#
#   BENCH_DIR=<scratch> bash measure_xsim.sh [provenance|w1|w2|own|scale|all]
#
# Binaries: ours from ../ours/build.sh ($BENCH_DIR/bin/new); the external engines
# and our harnesses for them from build_ext.sh ($BENCH_DIR/sansb/target,
# $BENCH_DIR/sts2core/target). SANSB_SRC / STS2CORE_SRC are the clones
# (default $BENCH_DIR/ext/sts2-sim and $BENCH_DIR/ext/sts2core).
#
# Stages
#   w1     per-core W1 (CPU $CORE, SMT sibling idle): ours-new (Burning Blood live),
#          ours-new --relics none, sansb instance/uniform, sts2core instance/engine;
#          ROUNDS_W1 rounds, the order rotated every round. 110,000 fights per run.
#   w2     per-core W2 (sansb's Toadpole fixture, 240,000 playouts, one pass each):
#          ours-new clone mode, sansb uniform/instance, sts2core engine/instance,
#          sansb's own `sts-sim bench`; ROUNDS_W2 rounds, rotated. The fixture is an
#          A10 fight (elided ascension = 10 in sansb), so ours and sts2core run --ascension 10.
#   own    each external engine's OWN benchmark as its authors run it:
#          sansb eval_throughput (once), sts2core bench at 1 / 24 / 48 threads (x OWN_REPS).
#   scale  whole machine via scale_runner.py with OUR presets (1core 1core-smt ccx phys24
#          smt48), W1 instance policy, a fresh 1core baseline per repeat. Per-process size
#          is chosen so each process times ~8-12 s on one core.
# Knobs (env): CORE (20), ROUNDS_W1 (7), ROUNDS_W2 (5), OWN_REPS (3), SCALE_REPEATS (3),
#   SCALE_PRESETS, FORCE=1, and the SIZE knobs below (only for smoke tests).
set -uo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$HERE/../env.sh"   # REPO, BENCH_DIR, SB
B2=$BENCH_DIR
R=${R:-$B2/runs/xsim}
OURS=$B2/bin/new
SANSB_DIR=$B2/sansb; SANSB=$SANSB_DIR/target/release
CORE_BIN=$B2/sts2core/target/release
SANSB_SRC=${SANSB_SRC:-$B2/ext/sts2-sim}
CORE_SRC=${STS2CORE_SRC:-$B2/ext/sts2core}
# Neutral working directory for the binaries that do not need one (the published
# run used the system temp directory; the binaries read nothing from their cwd).
NEUTRAL=$R
CORE=${CORE:-20}
ROUNDS_W1=${ROUNDS_W1:-7}; ROUNDS_W2=${ROUNDS_W2:-5}; OWN_REPS=${OWN_REPS:-3}; SCALE_REPEATS=${SCALE_REPEATS:-3}
SCALE_PRESETS=${SCALE_PRESETS:-"1core-smt ccx phys24 smt48"}
# SIZE knobs (defaults = the real measurement; shrink only for smoke tests)
W1_SEEDS=${W1_SEEDS:-2200}; W1_REPS=${W1_REPS:-50}; W2_TRAJ=${W2_TRAJ:-240000}
SCALE_SANSB_REPS=${SCALE_SANSB_REPS:-100}; SCALE_CORE_REPS=${SCALE_CORE_REPS:-400}
EVAL_FIGHTS=${EVAL_FIGHTS:-$SANSB_SRC/v0.111.0/eval/fights}; SKIP_OWN_MT=${SKIP_OWN_MT:-0}
LOAD_MAX=${LOAD_MAX:-1.5}; BUSY_MAX_PCT=${BUSY_MAX_PCT:-3}; CPU_BUSY_MAX=${CPU_BUSY_MAX:-5}
WAIT_MAX_MIN=${WAIT_MAX_MIN:-30}; FORCE=${FORCE:-0}
for v in $(compgen -e); do case "$v" in STS2_*|RAYON_*|OMP_*|MKL_*|LD_PRELOAD|MALLOC_*|MIMALLOC_*|GLIBC_TUNABLES|PYTHON*|RUST_*) unset "$v" ;; esac; done
mkdir -p "$R"
log() { echo "[$(date -u +%FT%TZ)] $*" | tee -a "$R/run.log"; }
busy_pct() { local a b c d e f g h t1 i1 t2 i2
  read -r _ a b c d e f g h _ < /proc/stat; t1=$((a+b+c+d+e+f+g+h)); i1=$((d+e)); sleep 5
  read -r _ a b c d e f g h _ < /proc/stat; t2=$((a+b+c+d+e+f+g+h)); i2=$((d+e))
  echo $(( 100 * ((t2-t1) - (i2-i1)) / (t2-t1) )); }
gate() {  # identical to measure_ours.sh's gate
  local label=$1 cpus=$2 deadline=$(( $(date +%s) + WAIT_MAX_MIN * 60 )) l1 b cb cmax
  while :; do
    l1=$(cut -d' ' -f1 /proc/loadavg); b=$(busy_pct); cb="-"; cmax=0
    if [ -n "$cpus" ]; then cb=$(python3 -I "$SB/cpu_busy.py" "$cpus" 2); cmax=$(sed -n 's/^max=\([0-9]*\).*/\1/p' <<< "$cb"); fi
    if awk -v l="$l1" -v m="$LOAD_MAX" 'BEGIN{exit !(l < m)}' && [ "$b" -lt "$BUSY_MAX_PCT" ] && [ "${cmax:-100}" -lt "$CPU_BUSY_MAX" ]; then
      log "quiet[$label] load1=$l1 busy=${b}% cpus=$cb"; return 0; fi
    if [ "$(date +%s)" -ge "$deadline" ]; then
      log "NOT quiet[$label] load1=$l1 busy=${b}% cpus=$cb"
      [ "$FORCE" = 1 ] && { echo "BUSY_DURING_$label=1" >> "$R/FLAGS.env"; return 0; }; return 1; fi
    log "waiting[$label] load1=$l1 busy=${b}% cpus=$cb"; sleep 25
  done; }
pstat() { local out=$1; shift; perf stat -x, -e cycles:u,instructions:u,task-clock,context-switches,cpu-migrations -o "$out" -- "$@"; }
index() { printf '%s\t%s\t%s\t%s\n' "$(date -u +%FT%TZ)" "$1" "$2" "$(cut -c1-700 <<< "$3")" >> "$R/index.tsv"; }
rotate() { local k=$1; shift; local a=("$@") n=$# i; for ((i = 0; i < n; i++)); do echo "${a[$(( (i + k) % n ))]}"; done; }

provenance() {
  { echo "DATE=$(date -u +%FT%TZ)"
    echo "SANSB_COMMIT=$(git -C "$SANSB_SRC" rev-parse HEAD) SANSB_UNTRACKED='$(git -C "$SANSB_SRC" status --short | tr '\n' ' ')'"
    echo "STS2CORE_COMMIT=$(git -C "$CORE_SRC" rev-parse HEAD) STS2CORE_UNTRACKED='$(git -C "$CORE_SRC" status --short | tr '\n' ' ')'"
    for f in "$SANSB/sts-sim" "$SANSB/examples/xsim_w1_site" "$SANSB/examples/xsim_w2_toadpoles" "$SANSB/examples/eval_throughput" \
             "$CORE_BIN/xsim_w1_site" "$CORE_BIN/xsim_w2_toadpoles" "$CORE_BIN/bench" "$OURS/site_random_bench" "$OURS/xsim_toadpoles"; do
      echo "BIN $(sha256sum "$f" | cut -c1-16) $(readelf -p .comment "$f" 2>/dev/null | grep -o 'rustc version [0-9.]*' | sort -u | tr '\n' ' ') $f"
    done
    echo "PROFILE_SANSB='lto thin, codegen-units 1, panic abort, opt 3, no target-cpu, system malloc (v0.111.0/engine/Cargo.toml:29-36)'"
    echo "PROFILE_STS2CORE='lto fat, codegen-units 1, panic abort, opt 3, no target-cpu, system malloc, zero deps'"
    echo "PROFILE_OURS='lto fat, codegen-units 1, panic unwind, opt 3, no target-cpu, no PGO, glibc malloc'"
  } > "$R/PROVENANCE.env"; log "provenance -> $R/PROVENANCE.env"; }

# One timed run: run <stage> <tag> <round> <cwd> cmd...
run() { local st=$1 tag=$2 i=$3 cwd=$4; shift 4; local d=$R/$st; mkdir -p "$d"
  ( cd "$cwd" && pstat "$d/$tag-$i.perf" taskset -c "$CORE" "$@" > "$d/$tag-$i.out" 2> "$d/$tag-$i.err" ) \
    || log "$st $tag-$i FAILED rc=$? (see $d/$tag-$i.err)"
  index "$st" "$tag-$i" "$(tail -n 1 "$d/$tag-$i.out")"; }

w1_cmd() { case "$1" in
  ours)         run w1 ours "$2" "$NEUTRAL" "$OURS/site_random_bench" --seeds "$W1_SEEDS" --seed-start 0 --reps "$W1_REPS" --relics burning-blood --mix round-robin ;;
  ours-norelic) run w1 ours-norelic "$2" "$NEUTRAL" "$OURS/site_random_bench" --seeds "$W1_SEEDS" --seed-start 0 --reps "$W1_REPS" --relics none --mix round-robin ;;
  sansb-inst)   run w1 sansb-inst "$2" "$SANSB_DIR" "$SANSB/examples/xsim_w1_site" --seeds "$W1_SEEDS" --seed-start 0 --reps "$W1_REPS" --policy instance ;;
  sansb-unif)   run w1 sansb-unif "$2" "$SANSB_DIR" "$SANSB/examples/xsim_w1_site" --seeds "$W1_SEEDS" --seed-start 0 --reps "$W1_REPS" --policy uniform ;;
  core-inst)    run w1 core-inst "$2" "$NEUTRAL" "$CORE_BIN/xsim_w1_site" --seeds "$W1_SEEDS" --reps "$W1_REPS" --policy instance ;;
  core-eng)     run w1 core-eng "$2" "$NEUTRAL" "$CORE_BIN/xsim_w1_site" --seeds "$W1_SEEDS" --reps "$W1_REPS" --policy engine ;;
  esac; }
w1() { gate w1 "$CORE" || return; local i e
  for i in $(seq 1 "$ROUNDS_W1"); do
    for e in $(rotate "$i" ours sansb-inst core-inst ours-norelic sansb-unif core-eng); do w1_cmd "$e" "$i"; done
  done; }

# W2 = sansb's fixture as shipped, which is an ASCENSION-10 fight (its elided
# player.ascension defaults to 10): ours and sts2core run it at A10 explicitly.
w2_cmd() { case "$1" in
  ours)        run w2 ours "$2" "$NEUTRAL" "$OURS/xsim_toadpoles" --mode clone --passes 1 --ascension 10 ;;   # full 240k playouts, sansb's semantics
  sansb-unif)  run w2 sansb-unif "$2" "$SANSB_DIR" "$SANSB/examples/xsim_w2_toadpoles" --trajectories "$W2_TRAJ" --policy uniform ;;
  sansb-inst)  run w2 sansb-inst "$2" "$SANSB_DIR" "$SANSB/examples/xsim_w2_toadpoles" --trajectories "$W2_TRAJ" --policy instance ;;
  sansb-own)   run w2 sansb-own "$2" "$SANSB_DIR" "$SANSB/sts-sim" bench --trajectories "$W2_TRAJ" ;;
  core-eng)    run w2 core-eng "$2" "$NEUTRAL" "$CORE_BIN/xsim_w2_toadpoles" --trajectories "$W2_TRAJ" --policy engine --ascension 10 ;;
  core-inst)   run w2 core-inst "$2" "$NEUTRAL" "$CORE_BIN/xsim_w2_toadpoles" --trajectories "$W2_TRAJ" --policy instance --ascension 10 ;;
  esac; }
w2() { gate w2 "$CORE" || return; local i e
  for i in $(seq 1 "$ROUNDS_W2"); do
    for e in $(rotate "$i" ours sansb-own core-eng sansb-inst core-inst sansb-unif); do w2_cmd "$e" "$i"; done
  done; }

own() { local i d=$R/own; mkdir -p "$d"
  gate own-sansb-eval "$CORE" && ( cd "$SANSB_DIR" && taskset -c "$CORE" "$SANSB/examples/eval_throughput" "$EVAL_FIGHTS" \
      > "$d/sansb-eval.jsonl" 2> "$d/sansb-eval.err" ) && index own sansb-eval "$(tail -n 1 "$d/sansb-eval.jsonl")"
  gate own-core-1t "$CORE" || return
  for i in $(seq 1 "$OWN_REPS"); do
    taskset -c "$CORE" "$CORE_BIN/bench" > "$d/core-1t-$i.txt" 2>&1; index own "core-1t-$i" "$(grep -m2 'steps' "$d/core-1t-$i.txt" | tr '\n' ' ')"
  done
  [ "$SKIP_OWN_MT" = 1 ] && return
  gate own-core-mt "" || return
  for i in $(seq 1 "$OWN_REPS"); do
    taskset -c 0-23 "$CORE_BIN/bench" > "$d/core-24t-$i.txt" 2>&1; index own "core-24t-$i" "$(grep 'threads' "$d/core-24t-$i.txt" | tail -2 | tr '\n' ' ')"
    taskset -c 0-47 "$CORE_BIN/bench" > "$d/core-48t-$i.txt" 2>&1; index own "core-48t-$i" "$(grep 'threads' "$d/core-48t-$i.txt" | tail -2 | tr '\n' ' ')"
  done; }

scale_eng() {  # scale_eng <tag> <cwd> -- cmd...   (same presets and baseline method as measure_ours.sh)
  local tag=$1 cwd=$2; shift 3; local r p d base
  for r in $(seq 1 "$SCALE_REPEATS"); do
    gate "scale-$tag-$r" "" || return
    d=$R/scale/$tag/r$r; mkdir -p "$d"; base=$d/1core
    ( cd "$cwd" && python3 -I "$SB/scale_runner.py" --preset 1core --core "$CORE" --perf --label "$tag-1core" --out-dir "$base" -- "$@" ) \
      > "$base.summary" 2> "$base.log"; index "scale-$tag" "r$r 1core" "$(tail -n 1 "$base.summary")"
    for p in $SCALE_PRESETS; do
      ( cd "$cwd" && python3 -I "$SB/scale_runner.py" --preset "$p" --core "$CORE" --perf --baseline "$base/summary.json" \
          --label "$tag-$p" --out-dir "$d/$p" -- "$@" ) > "$d/$p.summary" 2> "$d/$p.log"
      index "scale-$tag" "r$r $p" "$(tail -n 1 "$d/$p.summary")"
    done
  done; }
scale() {
  scale_eng w1-sansb "$SANSB_DIR" -- "$SANSB/examples/xsim_w1_site" --seeds "$W1_SEEDS" --seed-start 0 --reps "$SCALE_SANSB_REPS" --policy instance
  scale_eng w1-sts2core "$NEUTRAL" -- "$CORE_BIN/xsim_w1_site" --seeds "$W1_SEEDS" --reps "$SCALE_CORE_REPS" --policy instance; }

all() { provenance; w1; w2; own; scale; }
[ $# -gt 0 ] || set -- all
for s in "$@"; do log "== stage $s"; "$s"; done
