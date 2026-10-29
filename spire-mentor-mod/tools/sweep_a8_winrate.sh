#!/usr/bin/env bash
# Live-exe win rate at ascension 8: Ironclad then Silent, 40 graded runs each.
#
# ONE GAME PROCESS PER RUN and one run at a time -- there is a single live game
# on this box, so this lane is inherently serial. Budget ~5-7 min per run
# (2026-08-12 recipe: threads=8, 8000 sims, time_scale 4.0, think_fps 10, plus
# the 90 s thermal cooldown between runs), i.e. ~4 h per character.
#
# `--graded-runs 40` counts runs THE GAME graded (it wrote a run-history
# record). Stalls/crashes are replayed instead of being scored as losses;
# `--max-runs` is the attempt cap that stops a pathological loop.
#
# Progress is visible without attaching: each sweep's climb.log/climb.jsonl.
# Score with tools/score_live_winrate.py.
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PY="$REPO/sts2-pycli/.venv/Scripts/python.exe"
OUT="$REPO/logs/2026-08-12-live-a8"
cd "$REPO" || exit 1
mkdir -p "$OUT"

for character in Ironclad Silent; do
  tag=$(echo "$character" | tr '[:upper:]' '[:lower:]')
  echo "=== $character A8: 40 graded runs, start $(date -u +%FT%TZ)"
  "$PY" spire-mentor-mod/tools/ascension_climb.py \
      --character "$character" --ascension 8 \
      --graded-runs 40 --max-runs 60 \
      --out-dir "$OUT/${tag}-a8"
  echo "=== $character A8 done $(date -u +%FT%TZ)"
  "$PY" spire-mentor-mod/tools/score_live_winrate.py "$OUT/${tag}-a8"
done
