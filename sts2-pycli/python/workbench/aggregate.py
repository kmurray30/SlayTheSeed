"""Aggregate loss patterns across recorded MCTS runs (JSONL).

CLI: ``python -m workbench.aggregate <logs_dir> [--json]``. Ports the stats from
the deprecated ``sts2-rs/scripts/analyze_vantom_losses.py`` onto the workbench
JSONL log format. The ``aggregate_runs`` function also backs the SPA endpoint.
"""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
from typing import Any


def _parse_run(path: Path) -> dict[str, Any] | None:
    head: dict[str, Any] = {}
    summary: dict[str, Any] = {}
    states: list[dict] = []
    mcts: list[dict] = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        raw = raw.strip()
        if not raw:
            continue
        e = json.loads(raw)
        t, data = e.get("type"), e.get("data") or {}
        if t == "meta" and data.get("summary"):
            summary = data
        elif t == "meta":
            head = data
        elif t == "state":
            states.append(data)
        elif t == "mcts":
            mcts.append(data)
    if not summary:
        return None
    enemy_pcts = []
    for s in states:
        for en in s.get("enemies", []):
            mh = en.get("max_hp") or 0
            if mh:
                enemy_pcts.append(100 * en.get("hp", mh) / mh)
    turn1_q = None
    if mcts and mcts[0].get("root_outcomes"):
        turn1_q = max(r["q"] for r in mcts[0]["root_outcomes"])
    return {
        "path": str(path),
        "stem": head.get("fixture_stem") or path.stem,
        "won": bool(summary.get("final_won")),
        "final_turn": summary.get("final_turn"),
        # Present on full-run logs (workbench play), absent on fixture combats:
        # a run's progress is its floor, and `final_turn` is combat-scoped so it
        # is None for a run that ended anywhere but mid-fight.
        "final_floor": summary.get("final_floor"),
        "turn1_q": turn1_q,
        "enemy_min_pct": min(enemy_pcts) if enemy_pcts else None,
        "all_q": [max(r["q"] for r in m["root_outcomes"]) for m in mcts if m.get("root_outcomes")],
    }


def _tags(run: dict) -> list[str]:
    tags = []
    qs = run.get("all_q") or []
    if qs and max(qs) < -0.5:
        tags.append("hopeless_position")
    if run.get("enemy_min_pct") is not None and run["enemy_min_pct"] > 50:
        tags.append("no_damage")
    if run.get("final_turn") and run["final_turn"] <= 3:
        tags.append("fast_death")
    return tags


def aggregate_runs(logs_dir: str) -> dict[str, Any]:
    runs = [r for p in sorted(Path(logs_dir).glob("*.jsonl")) if (r := _parse_run(p))]
    losses = [r for r in runs if not r["won"]]
    final_turns = Counter(str(r["final_turn"]) for r in losses if r["final_turn"] is not None)
    q_vals = [r["turn1_q"] for r in losses if r["turn1_q"] is not None]
    tag_counts: Counter[str] = Counter()
    for r in losses:
        for tg in _tags(r):
            tag_counts[tg] += 1
    return {
        "n_runs": len(runs),
        "n_losses": len(losses),
        "final_turn_histogram": dict(final_turns),
        "turn1_q": {
            "min": min(q_vals) if q_vals else None,
            "max": max(q_vals) if q_vals else None,
            "mean": sum(q_vals) / len(q_vals) if q_vals else None,
        },
        "pattern_tags": dict(tag_counts),
        "representative_losses": [
            {
                "stem": r["stem"],
                "final_turn": r["final_turn"],
                "turn1_q": r["turn1_q"],
                "enemy_min_pct": r["enemy_min_pct"],
            }
            for r in sorted(losses, key=lambda r: r["turn1_q"] if r["turn1_q"] is not None else 0)[
                :5
            ]
        ],
    }


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("logs_dir")
    ap.add_argument("--json", action="store_true")
    a = ap.parse_args(argv)
    agg = aggregate_runs(a.logs_dir)
    if a.json:
        print(json.dumps(agg, indent=2))
        return 0
    print(f"runs={agg['n_runs']} losses={agg['n_losses']}")
    print("final-turn histogram:", agg["final_turn_histogram"])
    print("turn-1 Q:", agg["turn1_q"])
    print("pattern tags:", agg["pattern_tags"])
    for r in agg["representative_losses"]:
        print(
            f"  {r['stem']}: turn {r['final_turn']}, q0={r['turn1_q']}, enemy_min={r['enemy_min_pct']}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
