"""Tests for synergy.causal_lopo — leave-one-out combat replay (§7b)."""

from __future__ import annotations

import pytest

from synergy.causal_lopo import combat_lopo_s, lopo_marginal


def _fight(vals: dict[str, int], synergy: tuple[str, ...] | None = None, bonus: int = 0):
    def fn(base: list[str], inject: list[str], enc: str, seed: int) -> dict:
        deck = list(base) + list(inject)
        hp = 20 - sum(vals.get(c, 0) for c in deck)
        if synergy and all(c in deck for c in synergy):
            hp -= bonus
        return {"won": hp < 20, "hp_lost": max(0, hp), "turns": 5}

    return fn


class TestLopo:
    def test_marginal_recovers_solo_effect(self) -> None:
        fn = _fight({"A": 4})
        m = lopo_marginal(fn, ["A", "X"], "A", ["E1"], [1, 2])
        assert m == pytest.approx(4.0)  # striking A adds 4 hp lost → marginal +4

    def test_combat_lopo_s_positive_under_synergy(self) -> None:
        fn = _fight({"A": 2, "B": 2, "X": 1}, synergy=("A", "B"), bonus=6)
        decks = [["A", "B", "X"], ["A", "X"], ["A", "B"], ["A"]]
        s = combat_lopo_s(fn, decks, ("A", "B"), ["E1"], [1, 2])
        assert s is not None
        assert s > 0

    def test_combat_lopo_s_near_zero_without_synergy(self) -> None:
        fn = _fight({"A": 2, "B": 2, "X": 1})  # purely additive
        decks = [["A", "B", "X"], ["A", "X"], ["A", "B"], ["A"]]
        s = combat_lopo_s(fn, decks, ("A", "B"), ["E1"], [1, 2])
        assert s == pytest.approx(0.0)

    def test_none_when_a_bucket_empty(self) -> None:
        fn = _fight({"A": 2})
        decks = [["A", "B"]]  # no B-absent deck holding A → undefined
        assert combat_lopo_s(fn, decks, ("A", "B"), ["E1"], [1]) is None
