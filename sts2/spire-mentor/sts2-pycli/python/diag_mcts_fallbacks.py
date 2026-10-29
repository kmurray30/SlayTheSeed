"""Group MCTS ``mcts_error`` fallbacks by signature. (GH #360)

When ``recommend()`` proposes an action the live hand cannot accept, the live
driver logs the error and degrades that ONE decision to the random-legal
policy. The dominant shape is::

    recommend(): play action card_index=X out of range for hand of size Y

i.e. the reconstruction the search ran on had a LONGER hand than the world
does. ``ccf3c7178`` closed the choose-surface hand-retention class (offline
replay: 31/31 ``add_generated_free_to_hand`` records now defer), but instances
survived — and the question this script answers is whether the survivors are
one residual mechanism or several.

It is a quality tax rather than a run-killer (one degraded decision each), so
the useful output is a ranked census, not a stack trace: group by the error
shape, the overshoot (``card_index - hand_len``), the phase/screen the decision
was on, and what the hand actually held.

Usage::

    python -m diag_mcts_fallbacks logs/realgame/2026080*.jsonl
"""

from __future__ import annotations

import glob
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

_OUT_OF_RANGE = re.compile(r"card_index=(?P<idx>\d+) out of range for hand of size (?P<len>\d+)")


def _classify(err: str) -> str:
    """Collapse an error string to a stable signature."""
    if _OUT_OF_RANGE.search(err):
        return "play card_index out of range"
    # Keep other shapes separate rather than lumping them in — the whole point
    # is to see whether the survivors are one mechanism.
    return re.sub(r"\d+", "N", err).strip("'\" ")


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__)
        return 2

    paths: list[Path] = []
    for pattern in argv[1:]:
        expanded = [Path(p) for p in glob.glob(pattern)]
        paths.extend(expanded or [Path(pattern)])
    paths = [p for p in paths if p.exists()]
    if not paths:
        print("no matching logs", file=sys.stderr)
        return 2

    signatures: Counter[str] = Counter()
    overshoot: Counter[int] = Counter()
    hand_len: Counter[int] = Counter()
    by_kind: Counter[str] = Counter()
    by_run: Counter[str] = Counter()
    tail_cards: Counter[str] = Counter()
    powers_present: Counter[str] = Counter()
    relics_present: Counter[str] = Counter()
    examples: dict[str, list[dict[str, Any]]] = defaultdict(list)
    total_steps = 0

    for path in paths:
        with path.open("r", encoding="utf-8", errors="replace") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    rec = json.loads(line)
                except json.JSONDecodeError:
                    continue
                total_steps += 1
                err = rec.get("mcts_error")
                if not err:
                    continue
                sig = _classify(str(err))
                signatures[sig] += 1
                by_run[path.name] += 1
                state = rec.get("state") or {}
                by_kind[str(state.get("type") or "?")] += 1
                m = _OUT_OF_RANGE.search(str(err))
                if m:
                    idx, size = int(m["idx"]), int(m["len"])
                    overshoot[idx - size + 1] += 1
                    hand_len[size] += 1
                    hand = state.get("hand") or []
                    if hand:
                        tail_cards[str(hand[-1].get("id"))] += 1
                    for p in (state.get("player") or {}).get("powers") or []:
                        if isinstance(p, dict) and p.get("id"):
                            powers_present[str(p["id"])] += 1
                    for r in state.get("relics") or []:
                        if isinstance(r, dict) and r.get("id"):
                            relics_present[str(r["id"])] += 1
                if len(examples[sig]) < 3:
                    examples[sig].append(
                        {"run": path.name, "step": rec.get("step"), "error": str(err)}
                    )

    total = sum(signatures.values())
    print(f"logs: {len(paths)}   decisions: {total_steps}   fallbacks: {total}")
    if total_steps:
        print(f"fallback rate: {1000 * total / total_steps:.2f} per 1k decisions")
    print()

    print("== signatures")
    for sig, n in signatures.most_common():
        print(f"   {n:>5}  {sig}")
        for ex in examples[sig]:
            print(f"          e.g. {ex['run']} step {ex['step']}")
    print()

    if overshoot:
        print("== how many cards too long was the mirror's hand?")
        for k, n in sorted(overshoot.items()):
            print(f"   +{k}: {n}")
        print()
        print("== world hand size at the failure")
        for k, n in sorted(hand_len.items()):
            print(f"   {k}: {n}")
        print()
        print("== decision kind")
        for k, n in by_kind.most_common():
            print(f"   {k}: {n}")
        print()
        print("== card sitting at the world hand's tail (top 10)")
        for k, n in tail_cards.most_common(10):
            print(f"   {k}: {n}")
        print()
        print("== powers held at the failure (top 10)")
        for k, n in powers_present.most_common(10):
            print(f"   {k}: {n}")
        print()
        print("== relics held at the failure (top 10)")
        for k, n in relics_present.most_common(10):
            print(f"   {k}: {n}")
        print()

    print("== per-run concentration (top 10)")
    for k, n in by_run.most_common(10):
        print(f"   {n:>4}  {k}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
