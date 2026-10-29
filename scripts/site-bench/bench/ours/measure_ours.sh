#!/usr/bin/env bash
# OUR engine's measurements for the 2026-10 benchmark page (previous = base
# d3ea66a51, new = 6d1918237; binaries from export.sh + build.sh).
# Run on a quiet host, one stage at a time or `all`:
#   BENCH_DIR=<scratch> bash measure_ours.sh [provenance|w1|w1x|w2|hint|adv|scale_w1|scale_w2|scale_hint|scale_w1_base|all]
# Knobs (env): PAIRS_*, CORE (20), FORCE=1 (measure on a busy host, flagged),
#   W1X_ENCOUNTERS=<comma list> (the cross-engine intersection for stage w1x),
#   SCALE_REPEATS (3), SCALE_PRESETS ("1core 1core-smt ccx ccx-smt phys12 phys24 smt48"),
#   PY (the interpreter that loads bin/<T>/mod for the advisor stage; default python3).
# CPU numbers (CORE 20, masks 6-17 / 6-13) are this host's: 24 cores / 48 threads,
# SMT sibling = id + 24, L3 domains 0-5, 6-11, 12-17, 18-23 (+ siblings).
# Everything goes under $R; every stage appends one line per run to $R/index.tsv.
set -uo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$HERE/../env.sh"   # REPO, BENCH_DIR, SB
B2=$BENCH_DIR
O=$B2/ours; BIN=$B2/bin; R=${R:-$B2/runs/ours}
PY=${PY:-$(command -v python3)}
CORE=${CORE:-20}
PAIRS_W1=${PAIRS_W1:-7}; PAIRS_W1X=${PAIRS_W1X:-5}; PAIRS_W2=${PAIRS_W2:-2}; PAIRS_HINT=${PAIRS_HINT:-5}; PAIRS_ADV=${PAIRS_ADV:-4}
W1X_ENCOUNTERS=${W1X_ENCOUNTERS:-}
SCALE_REPEATS=${SCALE_REPEATS:-3}
SCALE_PRESETS=${SCALE_PRESETS:-"1core 1core-smt ccx ccx-smt phys12 phys24 smt48"}
LOAD_MAX=${LOAD_MAX:-1.5}; BUSY_MAX_PCT=${BUSY_MAX_PCT:-3}; CPU_BUSY_MAX=${CPU_BUSY_MAX:-5}
WAIT_MAX_MIN=${WAIT_MAX_MIN:-30}; FORCE=${FORCE:-0}
for v in $(compgen -e); do case "$v" in STS2_*|RAYON_*|OMP_*|MKL_*|LD_PRELOAD|MALLOC_*|MIMALLOC_*|GLIBC_TUNABLES|PYTHON*) unset "$v" ;; esac; done
mkdir -p "$R"
log() { echo "[$(date -u +%FT%TZ)] $*" | tee -a "$R/run.log"; }
order() { if (( $1 % 2 )); then echo "base new"; else echo "new base"; fi; }   # ABBA
busy_pct() { local a b c d e f g h t1 i1 t2 i2
  read -r _ a b c d e f g h _ < /proc/stat; t1=$((a+b+c+d+e+f+g+h)); i1=$((d+e)); sleep 5
  read -r _ a b c d e f g h _ < /proc/stat; t2=$((a+b+c+d+e+f+g+h)); i2=$((d+e))
  echo $(( 100 * ((t2-t1) - (i2-i1)) / (t2-t1) )); }
gate() {  # gate <label> <cpu-list or "">: quiet host (and quiet CPUs + siblings) or FORCE=1 (flagged)
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
index() { printf '%s\t%s\t%s\t%s\n' "$(date -u +%FT%TZ)" "$1" "$2" "$3" >> "$R/index.tsv"; }

provenance() {
  { echo "DATE=$(date -u +%FT%TZ)"; cat "$O/EXPORTED.env"
    echo "HOST='$(lscpu | sed -n 's/^Model name: *//p'), $(nproc) CPUs, Linux $(uname -r)'"
    echo "L3_DOMAINS='$(for c in 0 6 12 18; do cat /sys/devices/system/cpu/cpu$c/cache/index3/shared_cpu_list; done | tr '\n' ' ')'"
    echo "MEMTOTAL_KB=$(awk '/MemTotal/{print $2}' /proc/meminfo)"
    echo "GPUS='$(nvidia-smi --query-gpu=name,memory.total --format=csv,noheader 2>/dev/null | tr '\n' ';')'"
    echo "GOVERNOR=$(cat /sys/devices/system/cpu/cpu$CORE/cpufreq/scaling_governor) BOOST=$(cat /sys/devices/system/cpu/cpufreq/boost 2>/dev/null) MAX_KHZ=$(cat /sys/devices/system/cpu/cpu$CORE/cpufreq/cpuinfo_max_freq)"
    echo "THP=$(tr ' ' '_' < /sys/kernel/mm/transparent_hugepage/enabled)"
    echo "RUSTC='$(rustup run 1.96.0 rustc -V)' PYTHON='$($PY -V 2>&1)'"
    echo "PROFILE_NATIVE='release: fat LTO, codegen-units 1, opt-level 3, panic unwind, no target-cpu, no PGO, glibc malloc'"
    echo "PROFILE_SO='same profile, plain cdylib (maturin-equivalent); new links mimalloc v3 with purge_delay=-1 (A1+A1b), base glibc; no PGO'"
    for T in base new; do sed "s/^/MD5_${T}: /" "$BIN/$T/MD5"; done
    sed "s/^/HARNESS_MD5: /" "$BIN/new/HARNESS_MD5"
    echo "HARNESS_SPAWN='run path (GH #762): W1/W2 monsters built as run_bridge does; --legacy-spawn = pre-fix; W2 at A10'"
  } > "$R/PROVENANCE.env"; log "provenance -> $R/PROVENANCE.env"; }

# W1 (= the site's M1): one pinned core, 110,000 fights per run, ABBA pairs.
w1() { gate w1 "$CORE" || return; local i T d=$R/w1; mkdir -p "$d"
  for i in $(seq 1 "$PAIRS_W1"); do for T in $(order "$i"); do
    pstat "$d/$T-$i.perf" taskset -c "$CORE" "$BIN/$T/site_random_bench" --seeds 2200 --seed-start 0 --reps 50 \
      --relics burning-blood --mix round-robin > "$d/$T-$i.json" 2> "$d/$T-$i.err"
    index w1 "$T-$i" "$(cat "$d/$T-$i.json")"; done; done; }
# W1 on the cross-engine intersection of encounters (W1X_ENCOUNTERS, in OUR order).
w1x() { [ -n "$W1X_ENCOUNTERS" ] || { log "w1x: set W1X_ENCOUNTERS"; return 1; }
  gate w1x "$CORE" || return; local i T d=$R/w1x n; mkdir -p "$d"
  n=$(tr ',' '\n' <<< "$W1X_ENCOUNTERS" | grep -c .)
  for i in $(seq 1 "$PAIRS_W1X"); do for T in $(order "$i"); do
    pstat "$d/$T-$i.perf" taskset -c "$CORE" "$BIN/$T/site_random_bench" --seeds $((100 * n)) --seed-start 0 --reps 50 \
      --relics burning-blood --mix round-robin --encounters "$W1X_ENCOUNTERS" > "$d/$T-$i.json" 2> "$d/$T-$i.err"
    index w1x "$T-$i" "$(cat "$d/$T-$i.json")"; done; done; }
# W2: sansb's Toadpole fixture, 240,000 playouts, 5 timed passes per mode (their floor keeps the best of 5).
# The fixture is an A10 fight (sansb reads its elided ascension as 10), hence --ascension 10.
w2() { gate w2 "$CORE" || return; local i T d=$R/w2; mkdir -p "$d"
  for i in $(seq 1 "$PAIRS_W2"); do for T in $(order "$i"); do
    pstat "$d/$T-$i.perf" taskset -c "$CORE" "$BIN/$T/xsim_toadpoles" --mode both --passes 5 --ascension 10 > "$d/$T-$i.out" 2> "$d/$T-$i.err"
    index w2 "$T-$i" "$(tail -n 1 "$d/$T-$i.out")"; done; done; }
# Browser-hint search, native, one thread (the site's M3).
hint() { gate hint "$CORE" || return; local i T d=$R/hint; mkdir -p "$d"
  for i in $(seq 1 "$PAIRS_HINT"); do for T in $(order "$i"); do
    taskset -c "$CORE" "$BIN/$T/hint_bench" BENCH1 7 > "$d/$T-$i.txt" 2> "$d/$T-$i.err"
    index hint "$T-$i" "$(grep '^sims=1000 ' "$d/$T-$i.txt")"; done; done; }
# Advisor search (8,000 sims): 1 thread on CORE; 8 threads on two whole L3 domains (6-17,
# 12-worker pool); 8 threads with every worker pinned 1:1 (mask 6-13 -> 8 workers).
adv() { local i T d=$R/adv; mkdir -p "$d"
  gate adv1 "$CORE" && for i in $(seq 1 "$PAIRS_ADV"); do for T in $(order "$i"); do
    taskset -c "$CORE" "$PY" -I "$SB/advisor_bench.py" --mod-dir "$BIN/$T/mod" --label "$T-1t" \
      --threads 1 --sims 8000 --reps 7 > "$d/1t-$T-$i.jsonl" 2> "$d/1t-$T-$i.err"
    index adv1t "$T-$i" "$(tail -n 1 "$d/1t-$T-$i.jsonl")"; done; done
  gate adv8l3 "6-17" && for i in $(seq 1 "$PAIRS_ADV"); do for T in $(order "$i"); do
    taskset -c 6-17 "$PY" -I "$SB/advisor_bench.py" --mod-dir "$BIN/$T/mod" --label "$T-8t-l3" \
      --threads 8 --sims 8000 --reps 7 > "$d/8t-l3-$T-$i.jsonl" 2> "$d/8t-l3-$T-$i.err"
    index adv8t_l3 "$T-$i" "$(tail -n 1 "$d/8t-l3-$T-$i.jsonl")"; done; done
  gate adv8pin "6-13" && for i in $(seq 1 "$PAIRS_ADV"); do for T in $(order "$i"); do
    taskset -c 6-13 "$PY" -I "$SB/advisor_bench.py" --mod-dir "$BIN/$T/mod" --label "$T-8t-pin" \
      --threads 8 --sims 8000 --reps 7 --pin-workers 6-13 > "$d/8t-pin-$T-$i.jsonl" 2> "$d/8t-pin-$T-$i.err"
    index adv8t_pin "$T-$i" "$(tail -n 1 "$d/8t-pin-$T-$i.jsonl")"; done; done; }

# Whole machine: N pinned copies released together (scale_runner.py), each preset
# SCALE_REPEATS times, with a fresh 1-process baseline per repeat.
scale_one() {  # scale_one <tag> <T> <parser> <identity> -- cmd...
  local tag=$1 T=$2 parser=$3 ident=$4; shift 5
  local r p d base
  for r in $(seq 1 "$SCALE_REPEATS"); do
    gate "scale-$tag-$r" "" || return
    d=$R/scale/$tag/r$r; mkdir -p "$d"
    base=$d/1core
    python3 -I "$SB/scale_runner.py" --preset 1core --core "$CORE" --perf --parser "$parser" ${ident:+--identity "$ident"} \
      --label "$tag-1core" --out-dir "$base" -- "$@" > "$base.summary" 2> "$base.log"
    index "scale-$tag" "r$r 1core" "$(tail -n 1 "$base.summary" | cut -c1-600)"
    for p in $SCALE_PRESETS; do
      [ "$p" = 1core ] && continue
      python3 -I "$SB/scale_runner.py" --preset "$p" --core "$CORE" --perf --parser "$parser" ${ident:+--identity "$ident"} \
        --baseline "$base/summary.json" --label "$tag-$p" --out-dir "$d/$p" -- "$@" > "$d/$p.summary" 2> "$d/$p.log"
      index "scale-$tag" "r$r $p" "$(tail -n 1 "$d/$p.summary" | cut -c1-600)"
    done
  done; }
# W1 at 440,000 fights per process (~7.5 s on one new core) so start-up is noise.
scale_w1()      { scale_one w1-new  new json "" -- "$BIN/new/site_random_bench"  --seeds 2200 --reps 200; }
scale_w1_base() { scale_one w1-base base json "" -- "$BIN/base/site_random_bench" --seeds 2200 --reps 200; }
scale_w2()      { SCALE_PRESETS=${SCALE_PRESETS_W2:-"1core ccx phys24 smt48"} \
                  scale_one w2-new new json "clone.digest,clone.wins,transitions,playouts" -- "$BIN/new/xsim_toadpoles" --mode clone --passes 1 --ascension 10; }
scale_hint()    { SCALE_PRESETS=${SCALE_PRESETS_HINT:-"1core ccx phys24 smt48"} \
                  scale_one hint-new new hint_bench "" -- "$BIN/new/hint_bench" BENCH1 31; }

all() { provenance; w1; w2; hint; adv; scale_w1; scale_w2; scale_hint; [ -n "$W1X_ENCOUNTERS" ] && w1x; }
[ "${MEASURE_SOURCE_ONLY:-0}" = 1 ] && return 0 2>/dev/null   # `source` for testing: define only
[ $# -gt 0 ] || set -- all
for s in "$@"; do log "== stage $s"; "$s"; done
