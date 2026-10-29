#!/usr/bin/env bash
# Browser hint search, WASM: previous engine (base d3ea66a51) vs new (6d1918237),
# re-measured on the shipped wasm build (build_wasm.sh: wasm-release +
# wasm-bindgen 0.2.120 --target web + wasm-opt -O3). Same protocol as the
# site's M4n / M4c (es/site-bench/run.sh m4n/m4c): position BENCH1, 7 timed reps
# per budget (200/800/1,000/2,000/4,000 sims, one global warm-up), ABBA pairs,
# pinned to the 6-core CCD 18-23 (V8's tier-up compiler and GC run on helper
# threads; the wasm itself runs on one JS thread).
#   BENCH_DIR=<scratch> bash measure_wasm.sh [provenance|node|chrome|summary|all]...
#     node    = the wasm in V8 (Node), harness scripts/site-bench/wasm_hint_bench.mjs
#     chrome  = the same page-level search in headless Chromium (Playwright),
#               chrome_bench.html + chrome_bench_page.js served from 127.0.0.1
#     summary = medians per budget and engine, new/base, wasm/native (native =
#               measure_ours.sh's `hint` outputs, $NATIVE/{base,new}-<i>.txt)
#     all     = provenance node chrome summary
# Knobs (env): PAIRS (5), REPS (7), SEED (BENCH1), CPUS (18-23), FORCE=1 (measure
#   on a busy host; flagged in $R/FLAGS.env), WAIT_MAX_MIN (30), R (output dir),
#   NATIVE (native hint dir), PW_ROOT (dir whose node_modules has playwright;
#   default $REPO/sts2-pycli/web).
# Outputs under $R: <stage>/{base,new}-<i>.txt (+ .err), index.tsv (one line per
# run + one `check` line per stage), run.log, PROVENANCE.env, summary.txt.
# A re-run of a stage first moves that stage's previous outputs to $R/prev-<ts>/.
set -uo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$HERE/../env.sh"   # REPO, BENCH_DIR, SB
B2=$BENCH_DIR
D=$B2/wasm; R=${R:-$B2/runs/wasm}
W=$REPO
PW_ROOT=${PW_ROOT:-$W/sts2-pycli/web}
NATIVE=${NATIVE:-$B2/runs/ours/hint}
CPUS=${CPUS:-18-23}
PAIRS=${PAIRS:-5}; REPS=${REPS:-7}; SEED=${SEED:-BENCH1}
LOAD_MAX=${LOAD_MAX:-1.5}; BUSY_MAX_PCT=${BUSY_MAX_PCT:-3}; CPU_BUSY_MAX=${CPU_BUSY_MAX:-5}
WAIT_MAX_MIN=${WAIT_MAX_MIN:-30}; FORCE=${FORCE:-0}
# Run-time hygiene (as run.sh): knobs that change the workload or the runtime.
for v in $(compgen -e); do
  case "$v" in STS2_*|RAYON_*|OMP_*|MKL_*|LD_PRELOAD|MALLOC_*|MIMALLOC_*|GLIBC_TUNABLES|NODE_OPTIONS|NODE_V8_*|PYTHON*) unset "$v" ;; esac
done
export PYTHONDONTWRITEBYTECODE=1
mkdir -p "$R"
SERVER_PID=""
trap '[ -n "$SERVER_PID" ] && kill "$SERVER_PID" 2>/dev/null' EXIT   # only the server this script started

log() { echo "[$(date -u +%FT%TZ)] $*" | tee -a "$R/run.log"; }
order() { if (( $1 % 2 )); then echo "base new"; else echo "new base"; fi; }   # ABBA
busy_pct() { local a b c d e f g h t1 i1 t2 i2
  read -r _ a b c d e f g h _ < /proc/stat; t1=$((a+b+c+d+e+f+g+h)); i1=$((d+e)); sleep 5
  read -r _ a b c d e f g h _ < /proc/stat; t2=$((a+b+c+d+e+f+g+h)); i2=$((d+e))
  echo $(( 100 * ((t2-t1) - (i2-i1)) / (t2-t1) )); }
# gate: copied from ../ours/measure_ours.sh gate()
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
snap() { log "conditions[$1]: load=$(cut -d' ' -f1-3 /proc/loadavg) top=[$(ps -eo pcpu=,comm= --sort=-pcpu | head -4 | tr -s ' ' | tr '\n' ';')]"; }
index() { printf '%s\t%s\t%s\t%s\t%s\n' "$(date -u +%FT%TZ)" "$1" "$2" "$3" "$4" >> "$R/index.tsv"; }
medians() {  # "200:3.95 800:15.39 ..." from one output file
  sed -n 's/^sims=\([0-9]*\) .*median_ms=\([0-9.]*\) .*/\1:\2/p' "$1" | tr '\n' ' ' | sed 's/ $//'; }
same_position() {  # same_position <fileA> <fileB>: same fingerprint and pv0 at all five budgets (as run.sh)
  local f; for f in "$1" "$2"; do
    grep -q '^position ' "$f" 2>/dev/null && [ "$(grep -c '^sims=' "$f")" -eq 5 ] || return 1
  done
  diff <(grep -E '^position|^sims=' "$1" | sed -E 's/cold_ms=[^ ]+ median_ms=[^ ]+ min_ms=[^ ]+ ms_per_sim=[^ ]+ //') \
       <(grep -E '^position|^sims=' "$2" | sed -E 's/cold_ms=[^ ]+ median_ms=[^ ]+ min_ms=[^ ]+ ms_per_sim=[^ ]+ //') > /dev/null; }
check_pkgs() {  # the pkgs being measured are the ones build_wasm.sh recorded
  local T; for T in base new; do
    [ -f "$D/pkg-$T/sts2_workbench_wasm_bg.wasm" ] || { log "missing $D/pkg-$T (run build_wasm.sh)"; return 1; }
  done
  (cd "$D" && md5sum -c --quiet MD5) || { log "pkg md5 != $D/MD5 (rebuilt? re-run build_wasm.sh)"; return 1; }; }
fresh_dir() {  # fresh_dir <stage>: archive a previous run of this stage so nothing gets pooled
  local d=$R/$1 prev; if [ -e "$d" ]; then prev=$R/prev-$(date -u +%Y%m%dT%H%M%SZ); mkdir -p "$prev"; mv "$d" "$prev/"; log "moved previous $1 outputs to $prev"; fi
  mkdir -p "$d"; }
check_stage() {  # check_stage <stage>: every output vs the first base output (base-vs-new and run-to-run), then vs native
  local d=$R/$1 ref f bad=() nat=""
  ref=$d/base-1.txt
  for f in "$d"/base-*.txt "$d"/new-*.txt; do same_position "$ref" "$f" || bad+=("$(basename "$f")"); done
  if [ -f "$NATIVE/new-1.txt" ]; then same_position "$ref" "$NATIVE/new-1.txt" && nat="native:same" || nat="native:DIFFERENT"
  else nat="native:not-measured-yet"; fi
  if [ "${#bad[@]}" = 0 ]; then
    log "$1 check: all $(ls "$d"/base-*.txt "$d"/new-*.txt | wc -l) runs on the same position + pv0 at 5 budgets ($nat): $(grep '^position' "$ref")"
    index "$1" check same "$nat"
  else
    log "$1 check: WARNING position/pv0 differ from base-1 in: ${bad[*]} ($nat)"; index "$1" check DIFFERENT "${bad[*]} $nat"; return 1
  fi; }

provenance() {
  { echo "DATE=$(date -u +%FT%TZ)"; cat "$B2/ours/EXPORTED.env"
    echo "HOST='$(lscpu | sed -n 's/^Model name: *//p'), $(nproc) CPUs, Linux $(uname -r)'"
    echo "CPUS=$CPUS L3=$(cat /sys/devices/system/cpu/cpu${CPUS%%-*}/cache/index3/shared_cpu_list)"
    echo "GOVERNOR=$(cat /sys/devices/system/cpu/cpu${CPUS%%-*}/cpufreq/scaling_governor) BOOST=$(cat /sys/devices/system/cpu/cpufreq/boost 2>/dev/null)"
    echo "NODE=$(node --version) PLAYWRIGHT=$(sed -n 's/^  "version": "\(.*\)",/\1/p' "$PW_ROOT/node_modules/playwright/package.json")"
    grep -h '^build .* start' "$D/logs/build-base.log" "$D/logs/build-new.log" 2>/dev/null | sed 's/^/BUILD: /'
    echo "PROFILE_WASM='wasm-release (release: fat LTO, cu 1, opt 3; panic abort) + wasm-bindgen --target web + wasm-opt -O3 (= shipped, workbench_static.py wasm)'"
    sed 's/^/MD5_PKG: /' "$D/MD5"
    (cd "$SB" && md5sum wasm_hint_bench.mjs chrome_bench.html chrome_bench_page.js chrome_driver.mjs cpu_busy.py) | sed 's/^/HARNESS_MD5: /'
    echo "PARAMS: PAIRS=$PAIRS REPS=$REPS SEED=$SEED FORCE=$FORCE WAIT_MAX_MIN=$WAIT_MAX_MIN"
  } > "$R/PROVENANCE.env"; log "provenance -> $R/PROVENANCE.env"; }

# M4n: the wasm in V8 (Node), no browser.
node_stage() {
  check_pkgs || return 1
  gate node "$CPUS" || return 1
  fresh_dir node; snap node; local i T d=$R/node rc
  for i in $(seq 1 "$PAIRS"); do for T in $(order "$i"); do
    taskset -c "$CPUS" node "$SB/wasm_hint_bench.mjs" "$D/pkg-$T" "$SEED" "$REPS" > "$d/$T-$i.txt" 2> "$d/$T-$i.err"; rc=$?
    index node "$T-$i" "rc=$rc" "$(medians "$d/$T-$i.txt")"; done; done
  snap node-end; check_stage node; }

# M4c: the same page-level search in headless Chromium.
start_server() {  # start_server <dir>: http.server on an ephemeral 127.0.0.1 port; sets SERVER_PID, PORT
  local log=$1.http.log; PORT=""
  python3 -I -u -m http.server 0 --bind 127.0.0.1 --directory "$1" > "$log" 2>&1 & SERVER_PID=$!
  for _ in $(seq 1 100); do
    PORT=$(sed -n 's/^Serving HTTP on [^ ]* port \([0-9]*\) .*/\1/p' "$log"); [ -n "$PORT" ] && break; sleep 0.05
  done
  [ -n "$PORT" ] && kill -0 "$SERVER_PID" 2>/dev/null || { log "http server failed: $(head -c 300 "$log")"; stop_server; return 1; }; }
stop_server() { [ -n "$SERVER_PID" ] && { kill "$SERVER_PID" 2>/dev/null; wait "$SERVER_PID" 2>/dev/null; }; SERVER_PID=""; }
chrome_stage() {
  check_pkgs || return 1
  [ -d "$PW_ROOT/node_modules/playwright" ] || { log "no playwright under $PW_ROOT/node_modules"; return 1; }
  local T i d=$R/chrome rc
  for T in base new; do   # the page dir: the harness page next to a copy of the pkg (copied before the gate)
    mkdir -p "$R/www-$T" && find "$R/www-$T" -mindepth 1 -delete
    cp "$SB/chrome_bench.html" "$SB/chrome_bench_page.js" "$R/www-$T/" && cp -r "$D/pkg-$T" "$R/www-$T/pkg" || return 1
    cmp -s "$D/pkg-$T/sts2_workbench_wasm_bg.wasm" "$R/www-$T/pkg/sts2_workbench_wasm_bg.wasm" || { log "www-$T copy differs"; return 1; }
  done
  gate chrome "$CPUS" || return 1
  fresh_dir chrome; snap chrome
  for i in $(seq 1 "$PAIRS"); do for T in $(order "$i"); do
    start_server "$R/www-$T" || { index chrome "$T-$i" "rc=server" ""; continue; }
    PW_ROOT=$PW_ROOT taskset -c "$CPUS" node "$SB/chrome_driver.mjs" \
      "http://127.0.0.1:$PORT/chrome_bench.html" "$SEED" "$REPS" > "$d/$T-$i.txt" 2> "$d/$T-$i.err"; rc=$?
    stop_server
    index chrome "$T-$i" "rc=$rc port=$PORT $(sed -n 's/^browser=//p' "$d/$T-$i.txt")" "$(medians "$d/$T-$i.txt")"; done; done
  snap chrome-end; check_stage chrome; }

summary() { python3 -I "$HERE/summarize_wasm.py" "$R" "$NATIVE" | tee "$R/summary.txt"; }

all() { provenance; node_stage; chrome_stage; summary; }
[ $# -gt 0 ] || set -- all
for s in "$@"; do
  log "== stage $s (PAIRS=$PAIRS REPS=$REPS FORCE=$FORCE WAIT_MAX_MIN=$WAIT_MAX_MIN R=$R)"
  case "$s" in
    node|chrome) provenance; "${s}_stage" ;;   # provenance facts are static; PARAMS = the latest stage's
    provenance|summary|all) "$s" ;;
    *) echo "usage: $0 [provenance|node|chrome|summary|all]..."; exit 2 ;;
  esac
done
