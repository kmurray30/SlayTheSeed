"""Unit tests for :mod:`ppo_decode` — the A8 §8 group-mass decode.

The Python port of ``sts2-runai/src/ppo/policy.rs::grouped_argmax``. These
mirror the Rust-side ``group_decode_tests`` case for case, including the
audit's measured shop scenario, so the two implementations can't drift: the
live-game advisor (``realgame_ppo``) decodes with this module and the
workbench's ONNX backend decodes with the Rust one, and they must pick the
same action from the same distribution.
"""

from __future__ import annotations

import pytest
from ppo_decode import argmax_first, group_decode_enabled, grouped_argmax, select_index


def _buy(idx: int) -> dict:
    return {"action": "shop_buy_card", "idx": idx}


def _remove(deck_idx: int) -> dict:
    return {"action": "shop_remove_card", "deck_idx": deck_idx}


def test_shop_removal_mass_beats_single_buy() -> None:
    """A8 audit §8 (seed 1520652879 f2), the measured defect.

    Five removal candidates split a 0.667 "remove a starter" preference into
    0.13-ish slices and lose the plain argmax to a single 0.142 buy. The group
    decode pools them and returns the best removal target.
    """
    actions = [_buy(4), _remove(0), _remove(1), _remove(2), _remove(3), _remove(4)]
    probs = [0.142, 0.130, 0.136, 0.133, 0.133, 0.126]
    assert argmax_first(probs) == 0
    assert grouped_argmax(actions, probs) == 2


def test_singleton_screen_matches_plain_argmax() -> None:
    # Distinct buys are genuinely different decisions, never pooled — with
    # nothing to pool the decode must equal the plain argmax.
    actions = [_buy(0), _buy(1), {"action": "shop_buy_relic", "idx": 0}, {"action": "leave_shop"}]
    probs = [0.2, 0.4, 0.3, 0.1]
    assert grouped_argmax(actions, probs) == argmax_first(probs) == 1


def test_within_group_tie_breaks_to_first_member() -> None:
    actions = [{"action": "leave_shop"}, _remove(3), _remove(7)]
    assert grouped_argmax(actions, [0.2, 0.4, 0.4]) == 1


def test_group_tie_breaks_to_first_occurrence() -> None:
    # Equal pooled masses resolve to whichever group appeared first, so the
    # decode is deterministic (first-max, like numpy's argmax).
    actions = [_buy(0), _remove(0), _remove(1)]
    assert grouped_argmax(actions, [0.5, 0.25, 0.25]) == 0


@pytest.mark.parametrize(
    "kind",
    ["transform_deck_card", "enchant_deck_card", "smith_card"],
)
def test_the_other_pooled_kinds_pool_like_removal(kind: str) -> None:
    actions = [{"action": "event_choice", "idx": 0}] + [
        {"action": kind, "idx": i} for i in range(3)
    ]
    probs = [0.31, 0.24, 0.23, 0.22]
    assert argmax_first(probs) == 0
    assert grouped_argmax(actions, probs) == 1


def test_event_driven_deck_mutation_stays_ungrouped() -> None:
    # `remove_deck_card` (the event/select surface) is outside the A8 §8 scope
    # on both sides. Pinned so widening the set is a deliberate, measured
    # change rather than a silent one.
    actions = [{"action": "event_choice", "idx": 0}] + [
        {"action": "remove_deck_card", "idx": i} for i in range(3)
    ]
    probs = [0.31, 0.24, 0.23, 0.22]
    assert grouped_argmax(actions, probs) == 0


def test_seam_defaults_on_and_zero_disables(monkeypatch) -> None:
    monkeypatch.delenv("STS2_RUNAI_PPO_GROUP_DECODE", raising=False)
    assert group_decode_enabled()
    monkeypatch.setenv("STS2_RUNAI_PPO_GROUP_DECODE", "1")
    assert group_decode_enabled()
    # A rollback switch, not a mode enum: only "0" turns it off.
    monkeypatch.setenv("STS2_RUNAI_PPO_GROUP_DECODE", "0")
    assert not group_decode_enabled()


def test_select_index_honours_the_seam(monkeypatch) -> None:
    actions = [_buy(4), _remove(0), _remove(1), _remove(2), _remove(3), _remove(4)]
    probs = [0.142, 0.130, 0.136, 0.133, 0.133, 0.126]
    monkeypatch.delenv("STS2_RUNAI_PPO_GROUP_DECODE", raising=False)
    assert select_index(actions, probs) == 2
    monkeypatch.setenv("STS2_RUNAI_PPO_GROUP_DECODE", "0")
    assert select_index(actions, probs) == 0


def test_mismatched_lengths_raise() -> None:
    with pytest.raises(ValueError):
        grouped_argmax([_buy(0)], [0.5, 0.5])
    with pytest.raises(ValueError):
        argmax_first([])
