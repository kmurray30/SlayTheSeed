"""Event HP bleed report — GH #302 item 4.

Reads the JSONL written by `STS2_RUNAI_EVENT_BLEED_LOG` (see
`sts2-runai::policy::MctsCombatPolicy::log_event_choice`) and reports the two
numbers #302's acceptance criteria name:

- **event HP bleed**: HP the policy *chooses* to spend on event options,
  summed over the sweep and normalized per 60 runs so it is comparable to the
  pre-fix ~1.6-1.8k/60-run figure quoted on the issue. Attributed to the meta
  policy's RAW pick, because that is what the net wants; the realized
  (post-veto) total is reported beside it, and the two differ exactly by what
  the #300 guard saved.
- **veto fires**: how often the lethal-event guard had to reroute a pick. #302
  wants this at ~0 — a retrained net should not need saving from itself.

Only *losses* count toward "bleed" (healing events are reported separately as
a gain), because the quantity of interest is the attrition tax, not net flow.

Usage::

    python python/diag_event_bleed.py bleed.jsonl [--label champion]
    python python/diag_event_bleed.py before.jsonl after.jsonl   # paired diff
"""

from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path
from typing import Any

# The issue's baseline is quoted per 60-seed sweep; normalize to it so any
# sweep size is directly comparable to the number on #302.
NORM_RUNS = 60


def load(path: Path) -> list[dict[str, Any]]:
    return [json.loads(x) for x in path.read_text(encoding="utf-8").splitlines() if x.strip()]


def summarize(rows: list[dict[str, Any]]) -> dict[str, Any]:
    """Aggregate one arm's probe log."""
    seeds = {r["seed"] for r in rows}
    n_runs = len(seeds) or 1

    def loss(key: str) -> int:
        """HP *lost* via `key` (positive number), ignoring heals."""
        return -sum(min(0, r[key]) for r in rows if r.get(key) is not None)

    def heal(key: str) -> int:
        return sum(max(0, r[key]) for r in rows if r.get(key) is not None)

    per_event: dict[str, dict[str, int]] = defaultdict(lambda: {"n": 0, "bleed": 0, "vetos": 0})
    for r in rows:
        e = per_event[r.get("event") or "<unbound>"]
        e["n"] += 1
        raw = r.get("raw_hp_delta")
        if raw is not None:
            e["bleed"] += -min(0, raw)
        e["vetos"] += int(bool(r.get("veto_fired")))

    return {
        "n_runs": n_runs,
        "n_decisions": len(rows),
        "decisions_per_run": len(rows) / n_runs,
        "distinct_events": len({r.get("event") for r in rows if r.get("event")}),
        "unbound": sum(1 for r in rows if not r.get("event")),
        "raw_bleed": loss("raw_hp_delta"),
        "raw_bleed_per60": loss("raw_hp_delta") * NORM_RUNS / n_runs,
        "realized_bleed": loss("final_hp_delta"),
        "realized_bleed_per60": loss("final_hp_delta") * NORM_RUNS / n_runs,
        "heal": heal("final_hp_delta"),
        "veto_fires": sum(1 for r in rows if r.get("veto_fired")),
        "per_event": per_event,
    }


def report(label: str, s: dict[str, Any], top: int = 10) -> None:
    print(f"\n=== {label} ===")
    print(f"runs                {s['n_runs']}")
    print(f"event decisions     {s['n_decisions']}  ({s['decisions_per_run']:.2f}/run)")
    print(f"distinct events     {s['distinct_events']}   unbound: {s['unbound']}")
    print(
        f"chosen HP bleed     {s['raw_bleed']}  ->  {s['raw_bleed_per60']:.0f} per {NORM_RUNS} runs"
    )
    print(
        f"realized HP bleed   {s['realized_bleed']}  ->  "
        f"{s['realized_bleed_per60']:.0f} per {NORM_RUNS} runs"
    )
    print(f"HP gained at events {s['heal']}")
    print(f"lethal-veto fires   {s['veto_fires']}")
    ranked = sorted(s["per_event"].items(), key=lambda kv: -kv[1]["bleed"])[:top]
    if ranked:
        print(f"\n  top {len(ranked)} events by chosen bleed:")
        print(f"  {'event':28} {'picks':>6} {'bleed':>7} {'vetos':>6}")
        for name, d in ranked:
            print(f"  {name:28} {d['n']:>6} {d['bleed']:>7} {d['vetos']:>6}")


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("logs", nargs="+", type=Path, help="probe JSONL(s); 2 args = before/after")
    ap.add_argument("--label", action="append", default=None, help="label per log")
    ap.add_argument("--top", type=int, default=10)
    args = ap.parse_args(argv)

    labels = args.label or [p.stem for p in args.logs]
    if len(labels) != len(args.logs):
        ap.error("--label must be given once per log")

    sums = []
    for label, path in zip(labels, args.logs, strict=True):
        s = summarize(load(path))
        report(label, s, args.top)
        sums.append((label, s))

    if len(sums) == 2:
        (la, a), (lb, b) = sums
        d = b["raw_bleed_per60"] - a["raw_bleed_per60"]
        print(f"\n=== {lb} vs {la} (per {NORM_RUNS} runs) ===")
        print(
            f"chosen bleed   {a['raw_bleed_per60']:.0f} -> {b['raw_bleed_per60']:.0f}  ({d:+.0f})"
        )
        print(f"veto fires     {a['veto_fires']} -> {b['veto_fires']}")
        print(f"distinct events {a['distinct_events']} -> {b['distinct_events']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
