"""Post-game WPA run review — the run-level twin of the combat regret review.

Where :mod:`workbench.regret_review` diffs each *combat* move against the MCTS
line, this reviews the *run-level* decisions (map paths, card picks, shops,
rests, events, boss relics): the PPO critic's value ``V(s)`` is recorded live
at every non-combat decision (see ``BranchingLiveSession._node_value``), and
this module turns those values into a curve plus ranked **turning points** —
the decisions after which the run's outlook swung the most (GitHub issue #201;
the offline/agent-run variant lives in :mod:`ppo.wpa`).

This module is the **pure** scoring + rendering core (no HTTP, no game engine,
no net): it consumes ``(node_id, floor, decision, value, taken_label)`` rows
that the live session / replay timeline already carry and emits the review
payload. Orchestration lives in ``BranchingLiveSession.wpa_review`` (live) and
the ``/api/wpa-review`` endpoint (replay).

Value semantics — the load-bearing honesty rule: ``V`` is the PPO critic's
*shaped-return* estimate Φ (graded progress + shaping), **NOT a win
probability**. It ranks *where* the swings were; it must never be rendered as
a percentage. Counterfactual ground truth in win terms is the separate
"verify" action (``BranchingLiveSession.wpa_verify``), which agent-finishes
the run from the flagged decision under both the played and the alternative
pick and compares real outcomes.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass
from typing import Any

#: How many turning points to surface by default.
DEFAULT_TOP_K = 3

#: Ignore swings below this magnitude (Φ units) by default. The curve wobbles
#: a little on every reveal; only sizeable swings are worth coaching.
DEFAULT_MIN_SWING = 0.02

#: Caveats stamped into every payload (phrased for players, from issue #201).
CAVEATS = (
    "The outlook score is the AI's shaped-return estimate of the run — a relative "
    "signal, not a win percentage.",
    "A swing mixes your choice with the luck revealed after it (what the shop stocked, "
    "what the reward offered, how the fight rolled) — use “verify” to check whether the "
    "choice itself mattered.",
    "Judgments are relative to the AI's own play style; a line that is weak for the AI "
    "may still be fine for a human plan.",
)


@dataclass
class WpaPoint:
    """One scored decision on the run's value curve."""

    node_id: int | None  # live branch-tree node (seek target); None for replay steps
    idx: int  # step/depth index within the run
    decision: str  # rendered decision kind (map_select, card_reward, shop, ...)
    floor: int | None
    act: int | None
    value: float  # V(s) at this decision — shaped-return Φ, NOT P(win)
    delta_prev: float  # value - previous point's value (0.0 for the first)
    taken_label: str  # human label of the action taken at this decision

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def build_points(rows: list[dict[str, Any]]) -> list[WpaPoint]:
    """Order-preserving conversion of scored decision rows into curve points.

    Args:
        rows: One dict per scored decision, each with ``value`` (float) plus
            optional ``node_id`` / ``idx`` / ``decision`` / ``floor`` / ``act``
            / ``taken_label``. Rows without a usable ``value`` are skipped.
    """
    points: list[WpaPoint] = []
    prev: float | None = None
    for row in rows:
        value = row.get("value")
        if value is None:
            continue
        value = float(value)
        points.append(
            WpaPoint(
                node_id=row.get("node_id"),
                idx=int(row.get("idx", len(points))),
                decision=str(row.get("decision", "unknown")),
                floor=row.get("floor"),
                act=row.get("act"),
                value=value,
                delta_prev=0.0 if prev is None else value - prev,
                taken_label=str(row.get("taken_label") or "?"),
            )
        )
        prev = value
    return points


def turning_points(
    points: list[WpaPoint],
    *,
    top_k: int = DEFAULT_TOP_K,
    min_swing: float = DEFAULT_MIN_SWING,
) -> list[dict[str, Any]]:
    """Rank decisions by the outlook swing they preceded.

    The swing attributed to point ``i`` is ``V(i+1) − V(i)``: the change
    between facing this decision and facing the next scored one — the choice
    made here plus whatever it revealed (combats in between are unscored, so
    their toll lands in this delta too). The last scored decision has no
    successor and is never flagged.
    """
    tps: list[dict[str, Any]] = []
    for i in range(len(points) - 1):
        swing = points[i + 1].value - points[i].value
        if abs(swing) < min_swing:
            continue
        p = points[i]
        direction = "dropped" if swing < 0 else "rose"
        where = f"floor {p.floor}" if p.floor is not None else f"step {p.idx}"
        gap = points[i + 1].idx - p.idx
        span = " (spans the fight that followed)" if gap > 1 else ""
        tps.append(
            {
                "node_id": p.node_id,
                "idx": p.idx,
                "decision": p.decision,
                "floor": p.floor,
                "act": p.act,
                "taken_label": p.taken_label,
                "value_before": round(p.value, 4),
                "value_after": round(points[i + 1].value, 4),
                "swing": round(swing, 4),
                "message": (
                    f"{where} ({p.decision}): after “{p.taken_label}” your outlook "
                    f"{direction} {swing:+.3f} ({p.value:.3f} → {points[i + 1].value:.3f})"
                    f"{span}"
                ),
            }
        )
    tps.sort(key=lambda t: (-abs(t["swing"]), t["idx"]))
    return tps[:top_k]


def review(
    rows: list[dict[str, Any]],
    *,
    top_k: int = DEFAULT_TOP_K,
    min_swing: float = DEFAULT_MIN_SWING,
    won: bool | None = None,
    reason_if_empty: str = "no value data recorded for this run",
) -> dict[str, Any]:
    """Full review payload from scored decision rows.

    Returns ``{"available", "points", "turning_points", "summary", "caveats"}``
    — with ``available: False`` and a human ``reason`` when fewer than two
    decisions carry a value (no curve to draw).
    """
    points = build_points(rows)
    if len(points) < 2:
        return {
            "available": False,
            "reason": reason_if_empty,
            "points": [p.to_dict() for p in points],
            "turning_points": [],
            "summary": {},
            "caveats": list(CAVEATS),
        }
    tps = turning_points(points, top_k=top_k, min_swing=min_swing)
    deltas = [p.delta_prev for p in points[1:]]
    summary: dict[str, Any] = {
        "n_points": len(points),
        "first_value": round(points[0].value, 4),
        "final_value": round(points[-1].value, 4),
        "biggest_drop": round(min(deltas), 4) if deltas else 0.0,
        "biggest_gain": round(max(deltas), 4) if deltas else 0.0,
        "n_turning_points": len(tps),
    }
    if won is not None:
        summary["won"] = bool(won)
    return {
        "available": True,
        "points": [p.to_dict() for p in points],
        "turning_points": tps,
        "summary": summary,
        "caveats": list(CAVEATS),
    }
