"""Tests for synergy.prediction_gate — held-out synergy-predicts-wins exam."""

from __future__ import annotations

import numpy as np
import pytest

from synergy.corpus import Deck
from synergy.prediction_gate import GateResult, prediction_gate


def _deck(cards: list[str], won: bool) -> Deck:
    return Deck(cards=cards, won=won, player_id=None, skill=None, ascension=0)


def _corpus(seed: int, synergy_helps: bool) -> list[Deck]:
    rng = np.random.default_rng(seed)
    decks = []
    for _ in range(600):
        has = rng.random() < 0.5
        cards = (["A", "B"] if has else []) + [f"F{i}" for i in range(int(rng.integers(8, 14)))]
        base = 0.3 + (0.35 if (has and synergy_helps) else 0.0)
        decks.append(_deck(cards, bool(rng.random() < base)))
    return decks


class TestPredictionGate:
    def test_positive_lift_when_synergy_predicts(self) -> None:
        train, test = _corpus(0, True), _corpus(1, True)
        res = prediction_gate(train, test, [("A", "B")])
        assert isinstance(res, GateResult)
        assert res.lift > 0.02
        assert res.n_test == 600

    def test_no_lift_when_synergy_irrelevant(self) -> None:
        train, test = _corpus(0, False), _corpus(1, False)
        res = prediction_gate(train, test, [("A", "B")])
        assert res.lift < 0.03

    def test_degenerate_split_is_neutral(self) -> None:
        train = [_deck(["A", "B"], True), _deck(["A"], True)]  # all wins → single class
        res = prediction_gate(train, [_deck(["A", "B"], True)], [("A", "B")])
        assert res.lift == pytest.approx(0.0)
        assert res.synergy_auc == pytest.approx(0.5)
