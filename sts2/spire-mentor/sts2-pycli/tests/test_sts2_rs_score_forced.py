"""``sts2_rs.mcts_score_action_forced`` -- the live coach's played-move top-up.

A forced-root search prices ONE move from the position it was chosen from
(GH #494: a low-visit root child's Q is a visit-count artifact). The binding
wraps ``sts2_wire::emit::mcts::score_action_forced``.
"""

from __future__ import annotations

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


def test_score_action_forced_returns_row_with_hp() -> None:
    sims = 200
    row = sts2_rs.mcts_score_action_forced(_state(), {"action": "end_turn"}, sims=sims, seed=1)
    assert row is not None
    assert row["visits"] >= sims * 0.9
    for key in ("p_win", "q", "wins", "losses", "ties", "hp_n"):
        assert key in row
    assert row["action"]["action"] == "end_turn"
    assert isinstance(row["mean_final_hp"], float)
    assert row["hp_n"] > 0


def test_a_card_play_can_be_forced() -> None:
    cs = _state()
    play = next(a for a in cs.legal_actions() if a.get("action") == "play")
    row = sts2_rs.mcts_score_action_forced(cs, play, sims=100, seed=2)
    assert row is not None
    assert row["action"]["card_index"] == play["card_index"]


def test_a_move_that_is_not_legal_here_scores_none() -> None:
    row = sts2_rs.mcts_score_action_forced(
        _state(), {"action": "play", "card_index": 40, "target_index": None}, sims=50
    )
    assert row is None


def test_a_malformed_action_is_refused() -> None:
    with pytest.raises(ValueError):
        sts2_rs.mcts_score_action_forced(_state(), {"verb": "end_turn"}, sims=10)
