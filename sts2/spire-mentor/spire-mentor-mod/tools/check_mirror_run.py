#!/usr/bin/env python
"""Grade one F7 run for the advisor-mirror change (PR #548).

Counts, per run, how each in-combat card selection was ANSWERED -- the same
three buckets the 82-run `logs/2026-08-12-live-a8` corpus was counted in, so
the numbers are directly comparable:

* ``searched``   -- the combat MCTS answered (the new rung; note "combat MCTS")
* ``heuristic``  -- `realgame_hand_select`'s rarity sort ("heuristic pick")
* ``leftmost``   -- `_unranked`'s admitted arbitrary pick ("NOT a recommendation")

Baseline to beat, from that corpus: 1,521 selections across 82 runs, ALL of
them heuristic or leftmost, median 10 per run, 91% in combat.

The mirror's own counters (reconciles / desyncs / replays landed) come from the
advisor's end-of-run line, which is the honest denominator for "how often was
the carried state actually there when it mattered".

Usage:
    python spire-mentor-mod/tools/check_mirror_run.py <godot.log> [advisor_stdout.log]
"""

from __future__ import annotations

import collections
import re
import sys
from pathlib import Path

#: `[SimAdvisor] Takeover executed on <Screen>: <Card>  [<note>]` -- the mod
#: logs one per EXECUTED decision, and the note is the advice's own
#: self-description. A selection with no note is one the net ranked.
TAKEOVER = re.compile(r"Takeover executed on (\w+): (.*?)\s*\[(.*)\]\s*$")
SOURCE = re.compile(r"selection \(([^)]*)\)")
#: The advisor's end-of-run summary. Matched loosely -- by the labelled
#: numbers rather than by the whole line -- because the line grows: it gained
#: the follow counters and the decline tally after this tool was written, and
#: an anchored pattern silently reported "no end-of-run line found", which
#: reads as a crashed run rather than a stale regex.
MIRROR_NOTE = re.compile(r"advisor mirror: .*")
MIRROR_FIELDS = {
    "reconciles": re.compile(r"(\d+) reconciles"),
    "desyncs": re.compile(r"(\d+) desyncs"),
    "replays_landed": re.compile(r"(\d+)/(\d+) replays"),
    "searched": re.compile(r"(\d+) searched selects"),
}
#: `... | declined: <reason> x<n>, <reason> x<n>` -- why the searched rung did
#: not fire. When `searched` is 0 this is the whole diagnosis.
DECLINES = re.compile(r"\| declined: (.*)$")


def grade(godot_log: Path, advisor_log: Path | None = None) -> int:
    buckets: collections.Counter[str] = collections.Counter()
    screens: collections.Counter[str] = collections.Counter()
    sources: collections.Counter[str] = collections.Counter()

    for line in godot_log.read_text(encoding="utf-8", errors="replace").splitlines():
        m = TAKEOVER.search(line)
        if not m:
            continue
        screen, _card, note = m.groups()
        if "combat MCTS" in note:
            bucket = "searched"
        elif "heuristic pick" in note:
            bucket = "heuristic"
        elif "NOT a recommendation" in note:
            bucket = "leftmost"
        else:
            continue  # a net-ranked screen; not this change's business
        buckets[bucket] += 1
        screens[f"{bucket}/{screen}"] += 1
        src = SOURCE.search(note)
        if src:
            sources[f"{bucket}/{src.group(1)}"] += 1

    total = sum(buckets.values())
    print(f"in-combat/deck selections answered without the net: {total}")
    for name in ("searched", "heuristic", "leftmost"):
        n = buckets[name]
        pct = f"{100 * n / total:.0f}%" if total else "-"
        print(f"  {name:10s} {n:4d}  {pct}")
    if screens:
        print("\nby screen:")
        for k, v in screens.most_common():
            print(f"  {k:52s} {v}")
    if sources:
        print("\nby source:")
        for k, v in sources.most_common(15):
            print(f"  {k:52s} {v}")

    if advisor_log and advisor_log.exists():
        text = advisor_log.read_text(encoding="utf-8", errors="replace")
        notes = MIRROR_NOTE.findall(text)
        if notes:
            last = notes[-1]
            rec = int(m.group(1)) if (m := MIRROR_FIELDS["reconciles"].search(last)) else 0
            des = int(m.group(1)) if (m := MIRROR_FIELDS["desyncs"].search(last)) else 0
            landed, tried = (
                (int(m.group(1)), int(m.group(2)))
                if (m := MIRROR_FIELDS["replays_landed"].search(last))
                else (0, 0)
            )
            rate = f"{1000 * des / rec:.0f}/1k" if rec else "n/a"
            print(
                f"\nmirror: {rec} reconciles, {des} desyncs ({rate}), "
                f"{landed}/{tried} replays landed"
            )
            if why := DECLINES.search(last):
                print(f"  declined: {why.group(1)}")
        else:
            print("\nmirror: no end-of-run line found (run may not have ended cleanly)")

    # A run where the searched rung never fired is a FAILED validation: the
    # mirror is present but never reached the decision it exists for.
    return 0 if buckets["searched"] else 1


def main(argv: list[str]) -> int:
    if not argv:
        print(__doc__)
        return 2
    advisor = Path(argv[1]) if len(argv) > 1 else None
    return grade(Path(argv[0]), advisor)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
