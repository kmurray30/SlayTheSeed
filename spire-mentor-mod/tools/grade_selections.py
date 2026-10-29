#!/usr/bin/env python
"""Bucket every in-combat selection in a run directory BY SOURCE.

`check_mirror_run.py` grades one run and answers "how many were searched".
This answers the question that decides what to build next: *which sources are
still not searched, and how many of each*.

The distinction matters because the two questions disagreed. On three runs
(n=26) potions looked like the largest residue and power-opened screens like
none at all; on the 82-run `logs/2026-08-12-live-a8` corpus a single power
(`WELL_LAID_PLANS_POWER`, 246) outnumbered every potion combined by 8x. A
per-source table is the only view in which that is visible.

Usage:
    python spire-mentor-mod/tools/grade_selections.py <run-dir> [<run-dir> ...]

Each run dir is a sweep output directory (the one holding `*.godot.log`).
"""

from __future__ import annotations

import collections
import re
import sys
from pathlib import Path

#: `[SimAdvisor] Takeover executed on <Screen>: <Card>  [<note>]`
TAKEOVER = re.compile(r"Takeover executed on (\w+): (.*?)\s*\[(.*)\]\s*$")
#: The heuristic rung names what it could not rank: "for this selection (X)".
SOURCE = re.compile(r"selection \(([^)]*)\)")
#: `... | declined: <reason> x<n>, <reason> x<n>`
DECLINES = re.compile(r"\| declined: (.*)$")


def bucket_of(note: str) -> str:
    """Which rung answered -- or that nothing had to.

    `confirm` and `forced` are NOT rungs and must not sit in the denominator.
    A confirm is the button press that closes a screen whose pick was already
    made and counted; a forced pick is a screen with one option. Counting them
    made run 001-silent-a8-0825-0104 read as 249 selections at 10.8% searched
    when the ranked decisions were 121 at 22% -- and, worse, buried the fact
    that a single source was 78% of everything left.

    `searched` is combat MCTS and `net` is a trained run-policy head. Both
    are real rankings and both belong in the denominator; they are separate
    columns only because they answer different screens. Missing the `net`
    arm made this tool blind in the direction that matters: on
    `logs/f7-mirror-check18` it dropped 52 net-ranked picks into `other`, so
    a deck screen that MOVED from leftmost to the net vanished from the
    report instead of showing up as the improvement it was. A bucket the
    grader does not know is worse than one it scores wrongly -- `other` is
    excluded, so those decisions read as if they never happened.
    """
    if "combat MCTS" in note:
        return "searched"
    if "run-policy net" in note:
        return "net"
    if "heuristic pick" in note:
        return "heuristic"
    if "NOT a recommendation" in note:
        return "leftmost"
    if "confirm" in note:
        return "confirm"
    if "forced" in note:
        return "forced"
    return "other"


#: Buckets that represent a decision somebody had to RANK.
RANKED = ("searched", "net", "heuristic", "leftmost")

#: The rungs that are a real ranking. Anything else in RANKED is a fallback.
RANKERS = ("searched", "net")


def main(argv: list[str]) -> int:
    if not argv:
        print(__doc__)
        return 2

    buckets: collections.Counter[str] = collections.Counter()
    per_source: dict[str, collections.Counter[str]] = collections.defaultdict(
        collections.Counter
    )
    screens: collections.Counter[str] = collections.Counter()
    declines: collections.Counter[str] = collections.Counter()
    logs = 0

    for arg in argv:
        for log in sorted(Path(arg).rglob("*.godot.log")):
            logs += 1
            for line in log.read_text(encoding="utf-8", errors="replace").splitlines():
                if m := TAKEOVER.search(line):
                    screen, _card, note = m.groups()
                    if "CardSelect" not in screen and "PlayerHand" not in screen:
                        continue
                    b = bucket_of(note)
                    buckets[b] += 1
                    screens[screen] += 1
                    s = SOURCE.search(note)
                    per_source[s.group(1) if s else "(unnamed)"][b] += 1
                # The end-of-run tally is cumulative, so only the LAST line of
                # a log counts; collect and overwrite per file.
                if d := DECLINES.search(line):
                    for part in d.group(1).split(", "):
                        if " x" in part:
                            reason, _, n = part.rpartition(" x")
                            if n.isdigit():
                                declines[reason] = max(declines[reason], int(n))

    ranked = sum(buckets[b] for b in RANKED)
    if not ranked:
        print(f"no ranked in-combat selections found in {logs} log(s)")
        return 1

    fellback = sum(buckets[b] for b in RANKED if b not in RANKERS)
    print(
        f"{logs} run log(s), {ranked} RANKED selections "
        f"(in-combat hand + deck screens), {fellback} fell back"
    )
    for b in RANKED:
        if buckets[b]:
            print(f"  {b:10} {buckets[b]:5}  {100 * buckets[b] / ranked:5.1f}%")
    skipped = ", ".join(f"{buckets[b]} {b}" for b in ("confirm", "forced", "other") if buckets[b])
    if skipped:
        print(f"  (not a ranking, excluded: {skipped})")

    print(
        f"\n{'source':34} {'ranked':>7} {'searched':>9} {'net':>5} {'fell back':>10}"
    )
    rows = sorted(
        per_source.items(), key=lambda kv: -sum(kv[1][b] for b in RANKED)
    )
    for src, c in rows:
        n = sum(c[b] for b in RANKED)
        if not n:
            continue
        back = n - sum(c[b] for b in RANKERS)
        print(f"{src:34} {n:7} {c['searched']:9} {c['net']:5} {back:10}")

    print("\nscreens:", dict(screens))
    if declines:
        print("\ndeclines (max per run):")
        for reason, n in declines.most_common():
            print(f"  {n:4}  {reason}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
