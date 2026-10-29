#!/usr/bin/env bash
# Smoke tests (seconds each) for OUR harnesses on both engines. Not timings.
#   BENCH_DIR=<scratch> bash smoke.sh 2>&1 | tee "$BENCH_DIR/ours/logs/smoke.log"
# Since GH #762 the harnesses build monsters the way a real run does (default);
# `--legacy-spawn` reproduces the pre-fix harness (bin/<T>/site_random_bench_orig is
# the harness as committed at NEW_SHA; bin/<T>/pre762/, if present, holds binaries
# measured before the fix).
# W2 defaults to the fixture's ascension 10; `--legacy-spawn --ascension 0` is the
# pre-fix W2.
set -uo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$HERE/../env.sh"   # REPO, BENCH_DIR, SB
B2=$BENCH_DIR
O=$B2/ours; SM=$O/smoke; mkdir -p "$SM"
W=$REPO
PY=${PY:-$(command -v python3)}
ENCOUNTERS_FILE=$HERE/../w1_encounters.txt
CPU=${CPU:-20}
strip_t() { sed -E 's/"elapsed_s":[0-9.]+,"fights_per_s":[0-9.]+,"decisions_per_s":[0-9]+//'; }
ident() { sed -E 's/.*"fights":([0-9]+),"wins":([0-9]+),"decisions":([0-9]+),"anomalies":([0-9]+),"digest":"([0-9a-f]+)".*/fights=\1 wins=\2 decisions=\3 anomalies=\4 digest=\5/'; }
pass=0; fail=0
ok()  { echo "PASS $*"; pass=$((pass+1)); }
bad() { echo "FAIL $*"; fail=$((fail+1)); }

echo "== 1. site_random_bench --legacy-spawn: byte-identical to the pre-fix harness (timing stripped)"
for T in base new; do
  for args in "--seeds 44 --reps 5" "--seeds 44 --reps 5 --relics none" "--seeds 30 --reps 3 --mix random" "--seeds 22 --seed-start 100 --reps 4" "--seeds 66 --reps 2 --encounters INKLETS_NORMAL,THE_KIN_BOSS"; do
    taskset -c $CPU "$B2/bin/$T/site_random_bench" $args --legacy-spawn > "$SM/leg.json" 2> "$SM/leg.err"
    case "$args" in *--encounters*) ;; *)   # the HEAD harness predates --encounters
      taskset -c $CPU "$B2/bin/$T/site_random_bench_orig" $args > "$SM/orig.json" 2> "$SM/orig.err"
      if diff <(strip_t < "$SM/orig.json") <(strip_t < "$SM/leg.json") > /dev/null; then ok "$T [$args] legacy == HEAD harness"; else bad "$T [$args] legacy != HEAD harness"; diff "$SM/orig.json" "$SM/leg.json"; fi ;;
    esac
    if [ -x "$B2/bin/$T/pre762/site_random_bench" ]; then
      taskset -c $CPU "$B2/bin/$T/pre762/site_random_bench" $args > "$SM/pre.json" 2> /dev/null
      if diff <(strip_t < "$SM/pre.json") <(strip_t < "$SM/leg.json") > /dev/null; then ok "$T [$args] legacy == pre-#762 binary"; else bad "$T [$args] legacy != pre-#762 binary"; diff "$SM/pre.json" "$SM/leg.json"; fi
    fi
    taskset -c $CPU "$B2/bin/$T/site_random_bench" $args > "$SM/run.json" 2> /dev/null
    grep -q '"spawn":"run"' "$SM/run.json" && ok "$T [$args] default is the run-path start: $(ident < "$SM/run.json")" || bad "$T [$args] default lacks spawn=run"
  done
done

echo "== 2. site_random_bench: base and new identical (run-path default, and legacy)"
for args in "--seeds 220 --reps 5" "--seeds 2200 --reps 1" "--seeds 220 --reps 5 --legacy-spawn" "--seeds 2200 --reps 1 --relics none"; do
  taskset -c $CPU "$B2/bin/base/site_random_bench" $args > "$SM/w1-base.json" 2>/dev/null
  taskset -c $CPU "$B2/bin/new/site_random_bench" $args > "$SM/w1-new.json" 2>/dev/null
  if diff <(strip_t < "$SM/w1-base.json") <(strip_t < "$SM/w1-new.json") > /dev/null; then ok "base==new [$args] $(ident < "$SM/w1-new.json")"; else bad "base!=new [$args]"; cat "$SM/w1-base.json" "$SM/w1-new.json"; fi
done

echo "== 3. --encounters: the full list in default order reproduces the default run; subsets run"
ALL=$(paste -sd, "$ENCOUNTERS_FILE")
for T in base new; do
  taskset -c $CPU "$B2/bin/$T/site_random_bench" --seeds 66 --reps 3 > "$SM/def.json" 2>/dev/null
  taskset -c $CPU "$B2/bin/$T/site_random_bench" --seeds 66 --reps 3 --encounters "$ALL" > "$SM/all.json" 2>/dev/null
  d1=$(sed -E 's/.*"digest":"([0-9a-f]+)".*/\1/' "$SM/def.json"); d2=$(sed -E 's/.*"digest":"([0-9a-f]+)".*/\1/' "$SM/all.json")
  [ -n "$d1" ] && [ "$d1" = "$d2" ] && ok "$T full list == default (digest $d1)" || bad "$T full list digest $d2 != default $d1"
  taskset -c $CPU "$B2/bin/$T/site_random_bench" --seeds 4 --reps 3 --encounters nibbits_weak,VANTOM_BOSS > "$SM/sub-$T.json" 2> "$SM/sub-$T.err" \
    && ok "$T subset: $(cat "$SM/sub-$T.json")" || bad "$T subset run failed"
done
cmp -s <(strip_t < "$SM/sub-base.json") <(strip_t < "$SM/sub-new.json") && ok "subset base==new" || bad "subset base!=new"
"$B2/bin/new/site_random_bench" --seeds 2 --reps 1 --encounters NOT_AN_ENCOUNTER > /dev/null 2> "$SM/badenc.err" \
  && bad "unknown encounter accepted" || ok "unknown encounter refused: $(grep -o 'NOT_AN_ENCOUNTER[^,]*' "$SM/badenc.err" | head -1)"

echo "== 4. xsim_toadpoles (W2) --quick: A10 default, run-path openers, base == new, fixture deal found"
for T in base new; do
  taskset -c $CPU "$B2/bin/$T/xsim_toadpoles" --quick --mode both > "$SM/w2-$T.out" 2> "$SM/w2-$T.err" || bad "$T w2 exit $?"
  echo "   $T: $(cat "$SM/w2-$T.err")"
  echo "   $T: $(tail -n 1 "$SM/w2-$T.out" | cut -c1-400)"
done
strip_w2() { sed -E 's/"elapsed_s":[0-9.]+//g; s/"tx_per_s[a-z_]*":(\[[^]]*\]|[0-9.]+)//g; s/"playouts_per_s_median":[0-9.]+//g'; }
diff <(tail -n 1 "$SM/w2-base.out" | strip_w2) <(tail -n 1 "$SM/w2-new.out" | strip_w2) > /dev/null && ok "w2 base==new" || bad "w2 base!=new"
grep -q '"fixture_deal":true' "$SM/w2-new.out" && ok "w2 fixture deal" || bad "w2 no fixture deal"
grep -q '"ascension":10,"spawn":"run","openers":"SPIKEN_MOVE,WHIRL_MOVE"' "$SM/w2-new.out" && ok "w2 default = A10, front SPIKEN / back WHIRL" || bad "w2 default ascension/openers wrong"
taskset -c $CPU "$B2/bin/new/xsim_toadpoles" --quick --mode clone --content cards > "$SM/w2-cards.out" 2>/dev/null
c1=$(sed -nE 's/^\{"mode":"clone".*"digest":"([0-9a-f]+)".*/\1/p' "$SM/w2-new.out"); c2=$(sed -nE 's/^\{"mode":"clone".*"digest":"([0-9a-f]+)".*/\1/p' "$SM/w2-cards.out")
[ "$c1" = "$c2" ] && ok "w2 --content cards == all (digest $c1)" || bad "w2 content cards digest $c2 != all $c1"
i1=$(sed -nE 's/^\{"mode":"inplace".*"digest":"([0-9a-f]+)".*/\1/p' "$SM/w2-new.out")
[ "$c1" = "$i1" ] && ok "w2 clone == inplace work (digest $c1)" || bad "w2 clone digest $c1 != inplace $i1"
for T in base new; do
  if [ -x "$B2/bin/$T/pre762/xsim_toadpoles" ]; then
    taskset -c $CPU "$B2/bin/$T/pre762/xsim_toadpoles" --quick --mode both > "$SM/w2pre-$T.out" 2>/dev/null
    taskset -c $CPU "$B2/bin/$T/xsim_toadpoles" --quick --mode both --legacy-spawn --ascension 0 > "$SM/w2leg-$T.out" 2>/dev/null
    diff <(head -n 2 "$SM/w2pre-$T.out" | strip_w2) <(head -n 2 "$SM/w2leg-$T.out" | strip_w2) > /dev/null \
      && ok "$T w2 --legacy-spawn --ascension 0 == pre-#762 binary (per-mode lines)" || bad "$T w2 legacy A0 != pre-#762"
  fi
done

echo "== 5. hint_bench: same position and pv0 on both engines (1 rep)"
for T in base new; do taskset -c $CPU "$B2/bin/$T/hint_bench" BENCH1 1 > "$SM/hint-$T.txt" 2>&1; done
pv() { grep -E '^position|^sims=' "$1" | sed -E 's/cold_ms=[^ ]+ median_ms=[^ ]+ min_ms=[^ ]+ ms_per_sim=[^ ]+ //'; }
[ "$(grep -c '^sims=' "$SM/hint-new.txt")" = 5 ] && diff <(pv "$SM/hint-base.txt") <(pv "$SM/hint-new.txt") > /dev/null \
  && ok "hint base==new: $(grep '^position' "$SM/hint-new.txt")" || bad "hint position/pv0 differ"

echo "== 6. advisor_bench: base == new digest (2,000 sims, 1 rep), pin-workers smoke"
for T in base new; do
  taskset -c 6-13 "$PY" -I "$W/scripts/site-bench/advisor_bench.py" --mod-dir "$B2/bin/$T/mod" --label "$T" \
    --threads 1,8 --sims 2000 --reps 1 > "$SM/adv-$T.jsonl" 2> "$SM/adv-$T.err" || bad "$T advisor exit $?"
done
diff <(grep '"digest"' "$SM/adv-base.jsonl" | sed -E 's/.*"threads": ([0-9]+).*"digest": "([0-9a-f]+)".*/\1 \2/') \
     <(grep '"digest"' "$SM/adv-new.jsonl" | sed -E 's/.*"threads": ([0-9]+).*"digest": "([0-9a-f]+)".*/\1 \2/') > /dev/null \
  && ok "advisor base==new: $(grep -o '"threads": [0-9]*.*"digest": "[0-9a-f]*"' "$SM/adv-new.jsonl" | tr '\n' ' ')" || bad "advisor digests differ"
taskset -c 6-13 "$PY" -I "$W/scripts/site-bench/advisor_bench.py" --mod-dir "$B2/bin/new/mod" --label new-pinned \
  --threads 8 --sims 2000 --reps 2 --pin-workers 6-13 > "$SM/adv-pin.jsonl" 2> "$SM/adv-pin.err" \
  && ok "advisor --pin-workers: $(grep pinned_workers "$SM/adv-pin.jsonl")" || { bad "advisor --pin-workers"; cat "$SM/adv-pin.err"; }

echo "== summary: $pass passed, $fail failed"
[ "$fail" = 0 ]
