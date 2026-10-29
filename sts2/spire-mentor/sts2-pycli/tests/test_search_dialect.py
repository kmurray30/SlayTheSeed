"""The interactive search runs the run agent's EndTurn widening (GH #758).

PR #467 raised the EndTurn progressive-widening constants to 5.787 / 0.436 for
the run agent only; ``sts2_rs.mcts_recommend`` / ``mcts_search_tree`` -- the
real-game advisor, the workbench hint, the inspector, the coach -- kept the
engine's 0.5 / 0.4. Both now take the dialect from
``MctsConfig::apply_mode_dialect``. This pins it end to end through the
binding: the inspector's ``outcome_cap`` on an EndTurn chance node is the cap
the engine applied, so it shows which pair the search ran with.
"""

from __future__ import annotations

import math

import pytest

sts2_rs = pytest.importorskip("sts2_rs")


def _state():
    return sts2_rs.build_scenario_state(
        3,
        "Ironclad",
        ["STRIKE_IRONCLAD"] * 5 + ["DEFEND_IRONCLAD"] * 5,
        80,
        80,
        [],
        "SHRINKER_BEETLE_WEAK",
    )


def _endturn_nodes(tree: dict) -> list[dict]:
    nodes = [
        n for n in tree["nodes"] if n["node_kind"] == "chance" and n["chance_action"] == "End turn"
    ]
    assert nodes, "the search never expanded an EndTurn chance node"
    return nodes


def _cap(c: float, alpha: float, visits: int) -> int:
    # f32 in the engine; the visit counts here are far from a rounding edge.
    return max(1, math.ceil(c * (visits + 1) ** alpha))


def test_default_search_uses_the_run_agents_endturn_widening() -> None:
    tree = sts2_rs.mcts_search_tree(_state(), sims=400, seed=1)
    for n in _endturn_nodes(tree):
        assert n["outcome_cap"] == _cap(5.787, 0.436, n["visits"])


def test_explicit_endturn_widening_wins() -> None:
    tree = sts2_rs.mcts_search_tree(
        _state(), sims=400, seed=1, endturn_widening_c=0.5, endturn_widening_alpha=0.4
    )
    for n in _endturn_nodes(tree):
        assert n["outcome_cap"] == _cap(0.5, 0.4, n["visits"])


def test_recommend_accepts_the_endturn_knobs() -> None:
    out = sts2_rs.mcts_recommend(
        _state(), sims=64, threads=1, seed=1, endturn_widening_c=0.5, endturn_widening_alpha=0.4
    )
    assert out["actions"]
