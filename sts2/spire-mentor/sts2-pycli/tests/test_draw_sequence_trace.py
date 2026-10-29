"""Pure-logic tests for the lockstep draw-sequence tracer.

These exercise the classification/summary helpers WITHOUT driving the engines
(no C# subprocess), so they're fast and deterministic. The engine-driven
``trace_seed`` is validated by running the CLI on real seeds.
"""

from __future__ import annotations

import draw_sequence_trace as dst


def _step(cs: dict, rs: dict, *, step: int = 1, action: str = "end_turn", rng=None):
    piles = ("hand_ids", "draw_ids", "discard_ids", "exhaust_ids")
    cs_p = {k: list(cs.get(k, [])) for k in piles}
    rs_p = {k: list(rs.get(k, [])) for k in piles}
    return dst.StepTrace(
        step=step,
        turn=1,
        action=action,
        cs=cs_p,
        rs=rs_p,
        status={k: dst._pile_status(cs_p[k], rs_p[k]) for k in piles},
        rng_diffs=rng or {},
    )


def test_pile_status_match_order_content() -> None:
    assert dst._pile_status(["A", "B"], ["A", "B"]) == "match"
    assert dst._pile_status(["A", "B"], ["B", "A"]) == "order"
    assert dst._pile_status(["A", "B"], ["A", "C"]) == "content"


def test_deck_conserved_true_on_reshuffle_split() -> None:
    # Same whole-deck multiset, different hand/draw split = draw-order desync.
    s = _step(
        {"hand_ids": ["A", "B"], "draw_ids": ["C", "D"]},
        {"hand_ids": ["A", "C"], "draw_ids": ["B", "D"]},
    )
    assert s.first_slip == "content"  # per-pile multiset differs...
    assert s.deck_conserved is True  # ...but whole-deck is conserved


def test_deck_conserved_false_on_real_loss() -> None:
    s = _step(
        {"hand_ids": ["A", "B"], "draw_ids": ["C", "D"]},
        {"hand_ids": ["A"], "draw_ids": ["C", "D"]},
    )
    assert s.deck_conserved is False


def test_first_slip_none_when_all_match() -> None:
    s = _step(
        {"hand_ids": ["A"], "draw_ids": ["B"]},
        {"hand_ids": ["A"], "draw_ids": ["B"]},
    )
    assert s.first_slip is None
    assert s.deck_conserved is True


def test_combat_rng_diffs_drops_run_setup_streams() -> None:
    diffs = {"up_front": (414, 0), "shuffle": (35, 34), "combat_orbs": (3, 0)}
    assert dst._combat_rng_diffs(diffs) == {"shuffle": (35, 34)}


def test_summarize_flags_combat_stream_over_benign_offset() -> None:
    # A conserved reshuffle split with BOTH a benign up_front offset and a real
    # combat `shuffle` desync must point at the combat stream, not up_front.
    s = _step(
        {"hand_ids": ["A", "B"], "draw_ids": ["C", "D"]},
        {"hand_ids": ["A", "C"], "draw_ids": ["B", "D"]},
        rng={"up_front": (414, 0), "shuffle": (35, 34)},
    )
    out = dst.summarize([s])
    assert "CONSERVED" in out
    assert "COMBAT RNG stream(s) DIVERGE" in out
    assert "shuffle" in out


def test_summarize_benign_only_when_just_run_setup() -> None:
    s = _step(
        {"hand_ids": ["A", "B"], "draw_ids": ["C", "D"]},
        {"hand_ids": ["A", "C"], "draw_ids": ["B", "D"]},
        rng={"up_front": (414, 0)},
    )
    out = dst.summarize([s])
    assert "Only BENIGN run-setup streams" in out


def test_summarize_no_slip() -> None:
    s = _step(
        {"hand_ids": ["A"], "draw_ids": ["B"]},
        {"hand_ids": ["A"], "draw_ids": ["B"]},
    )
    assert "NO PILE SLIP" in dst.summarize([s])
