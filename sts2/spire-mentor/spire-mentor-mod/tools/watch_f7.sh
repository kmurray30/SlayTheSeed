#!/usr/bin/env bash
# Watch the live F7 advisor run. Appends a status line every poll to a
# persistent progress file, but only EMITs (notifies) on a real state change:
# progress milestones, a STALL, or the run ending.
#
# Stall detection is the point: an F7 stall is silent -- the game sits on a
# screen nothing describes, the log simply stops growing, and "still running"
# and "wedged forever" look identical. So watch the count of *committed*
# actions (Takeover executed) and alarm when it stops moving. The slowest
# legitimate gap is one act-boss think (4000 sims) plus its animations, tens of
# seconds; STALL_SECS is an order of magnitude above that.
set -u

GLOG="${GLOG:-$APPDATA/SlayTheSpire2/logs/godot.log}"
# advisor_stdout.log moved to %LOCALAPPDATA%\SpireMentor with GH #380.
ALOG="${ALOG:-$LOCALAPPDATA/SpireMentor/advisor_stdout.log}"
OUT="${OUT:-$(dirname "$0")/../../logs/f7_realrun/progress.log}"
STALL_SECS="${STALL_SECS:-300}"
POLL="${POLL:-45}"
mkdir -p "$(dirname "$OUT")"

last_floors=-1
last_alert=""
last_takeovers=-1
last_refused=0
last_move_ts=$(date +%s)

emit() { echo "$1"; echo "$(date '+%H:%M:%S') EMIT $1" >>"$OUT"; }

while true; do
  now=$(date +%s)
  # Both shapes: screen takeovers log "executed on <Screen>:", combat ones log
  # "executed: <card>". Counting only the former makes any long fight look
  # like a stall.
  takeovers=$(grep -c "Takeover executed" "$GLOG" 2>/dev/null) || takeovers=0
  floors=$(grep -c "Takeover executed on NMapScreen:" "$GLOG" 2>/dev/null) || floors=0
  decisions=$(grep -c "\[SimAdvisor\] Decision up:" "$GLOG" 2>/dev/null) || decisions=0
  fails=$(grep -c "\[SimScreenAdvice\].*failed:" "$GLOG" 2>/dev/null) || fails=0
  screen=$(grep "\[SimAdvisor\] Decision up:" "$GLOG" 2>/dev/null | tail -1 | sed 's/.*Decision up: //' | cut -c1-90)
  pwin=$(grep "advice #" "$ALOG" 2>/dev/null | tail -1 | sed 's/.*INFO __main__: //' | cut -c1-70)
  alive=$(tasklist //FI "IMAGENAME eq SlayTheSpire2.exe" 2>/dev/null | grep -c SlayTheSpire2)

  if [ "$takeovers" -ne "$last_takeovers" ]; then
    last_takeovers=$takeovers
    last_move_ts=$now
  fi
  idle=$((now - last_move_ts))

  echo "$(date '+%H:%M:%S') floors=$floors decisions=$decisions actions=$takeovers idle=${idle}s fails=$fails alive=$alive | $screen | $pwin" >>"$OUT"

  # --- terminal / failure signatures -------------------------------------
  alert=""
  if grep -q "has won a run with ancient" "$GLOG" 2>/dev/null; then
    alert="VICTORY after $floors floors: $(grep 'has won a run with ancient' "$GLOG" | tail -1 | cut -c1-140)"
  elif grep -q "Decision up: NGameOverScreen" "$GLOG" 2>/dev/null; then
    alert="RUN OVER (NGameOverScreen) after $floors floors / $decisions decisions. $(grep 'has lost to encounter' "$GLOG" | tail -1 | cut -c1-120)"
  elif [ "$alive" -eq 0 ]; then
    alert="GAME PROCESS GONE after $floors floors / $decisions decisions"
  elif grep -q "has not moved after" "$GLOG" 2>/dev/null; then
    alert="RUNAWAY GUARD: $(grep 'has not moved after' "$GLOG" | tail -1 | cut -c1-160)"
  elif [ "$idle" -ge "$STALL_SECS" ]; then
    # No committed action for STALL_SECS. Report the screen it is sitting on:
    # that name IS the bug report (README, "What F7 can drive").
    alert="STALL: no action for ${idle}s at floor ~$floors. Screen: $screen | last advice: $pwin"
  fi

  if [ -n "$alert" ] && [ "$alert" != "$last_alert" ]; then
    emit "$alert"
    last_alert="$alert"
    case "$alert" in
      VICTORY*|"RUN OVER"*|"GAME PROCESS GONE"*) exit 0 ;;
      STALL*) last_move_ts=$now ;;   # re-arm: report again if it stays stuck
    esac
  fi

  # --- refused actions: a SEPARATE check, never part of the chain above ---
  # (an elif branch that matches once would mask every stall after it)
  # Both shapes: the pre-flight CanPlay check ("the game refuses X") and the
  # post-hoc watchdog ("changed nothing on the board").
  refused=$(grep -cE "changed nothing on the board|the game refuses" "$GLOG" 2>/dev/null) || refused=0
  if [ "$refused" -ne "$last_refused" ]; then
    emit "REFUSED ACTIONS: $refused so far. Last: $(grep -E 'changed nothing on the board|the game refuses|refused recommendations in a row' "$GLOG" | tail -1 | sed 's/.*SimAdvisor\] //' | cut -c1-150)"
    last_refused=$refused
  fi

  # --- progress every 5 floors -------------------------------------------
  if [ "$floors" -ge $((last_floors + 5)) ]; then
    emit "progress: floor ~$floors, $decisions decisions, $fails describer failures | last: $screen | $pwin"
    last_floors=$floors
  fi

  sleep "$POLL"
done
