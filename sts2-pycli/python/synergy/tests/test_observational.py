"""Tests for synergy.observational — pairwise co-occurrence + PMI.

Fixtures use hand-computable counts so the expected pmi values can be
verified without running the full corpus.
"""

from __future__ import annotations

import math
import statistics

import pytest

from synergy.corpus import Deck
from synergy.observational import pmi_table

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _deck(cards: list[str], won: bool = True) -> Deck:
    return Deck(cards=cards, won=won, player_id=1, skill=None)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

# 3-deck corpus:
#   deck 0: A B       → pairs (A,B)
#   deck 1: A B C     → pairs (A,B), (A,C), (B,C)
#   deck 2: A C       → pairs (A,C)
#
# n_decks = 3
# count(A)=3  p_A = 3/3 = 1.0
# count(B)=2  p_B = 2/3
# count(C)=2  p_C = 2/3
# count(A,B)=2  p_AB = 2/3
# count(A,C)=2  p_AC = 2/3
# count(B,C)=1  p_BC = 1/3
#
# pmi(A,B) = log(p_AB / (p_A * p_B)) = log((2/3) / (1.0 * 2/3)) = log(1) = 0
# pmi(A,C) = log((2/3) / (1.0 * 2/3)) = log(1) = 0
# pmi(B,C) = log((1/3) / (2/3 * 2/3)) = log((1/3) / (4/9)) = log(3/4)

_THREE_DECKS = [
    _deck(["A", "B"]),
    _deck(["A", "B", "C"]),
    _deck(["A", "C"]),
]


# ---------------------------------------------------------------------------
# Test 1: pmi values match closed-form hand-computed values
# ---------------------------------------------------------------------------


class TestPmiValues:
    def setup_method(self) -> None:
        self.table = pmi_table(_THREE_DECKS, freq_floor=1)

    def test_pair_ab_present(self) -> None:
        assert ("A", "B") in self.table

    def test_pair_ac_present(self) -> None:
        assert ("A", "C") in self.table

    def test_pair_bc_present(self) -> None:
        assert ("B", "C") in self.table

    def test_pmi_ab(self) -> None:
        row = self.table["A", "B"]
        expected = math.log((2 / 3) / (1.0 * (2 / 3)))  # = 0.0
        assert math.isclose(row.pmi, expected, rel_tol=1e-9)

    def test_pmi_ac(self) -> None:
        row = self.table["A", "C"]
        expected = math.log((2 / 3) / (1.0 * (2 / 3)))  # = 0.0
        assert math.isclose(row.pmi, expected, rel_tol=1e-9)

    def test_pmi_bc(self) -> None:
        row = self.table["B", "C"]
        expected = math.log((1 / 3) / ((2 / 3) * (2 / 3)))  # = log(3/4)
        assert math.isclose(row.pmi, expected, rel_tol=1e-9)

    def test_cooccur_ab(self) -> None:
        assert self.table["A", "B"].cooccur == 2

    def test_cooccur_bc(self) -> None:
        assert self.table["B", "C"].cooccur == 1

    def test_p_a_in_ab_row(self) -> None:
        row = self.table["A", "B"]
        assert math.isclose(row.p_a, 1.0, rel_tol=1e-9)

    def test_p_b_in_ab_row(self) -> None:
        row = self.table["A", "B"]
        assert math.isclose(row.p_b, 2 / 3, rel_tol=1e-9)

    def test_p_ab_in_ab_row(self) -> None:
        row = self.table["A", "B"]
        assert math.isclose(row.p_ab, 2 / 3, rel_tol=1e-9)

    def test_pairs_are_canonical(self) -> None:
        """All keys must satisfy key == tuple(sorted(key))."""
        for pair in self.table:
            assert pair == tuple(sorted(pair)), f"Non-canonical pair: {pair}"


# ---------------------------------------------------------------------------
# Test 2: freq_floor filtering removes low-cooccurrence pairs
# ---------------------------------------------------------------------------


class TestFreqFloor:
    def test_pair_below_floor_is_absent(self) -> None:
        # (B,C) co-occurs 1 time; with freq_floor=2 it should be dropped.
        table = pmi_table(_THREE_DECKS, freq_floor=2)
        assert ("B", "C") not in table

    def test_pair_at_or_above_floor_is_present(self) -> None:
        # (A,B) and (A,C) co-occur 2 times; they survive freq_floor=2.
        table = pmi_table(_THREE_DECKS, freq_floor=2)
        assert ("A", "B") in table
        assert ("A", "C") in table

    def test_default_floor_filters_sparse_pairs(self) -> None:
        # Default freq_floor=50; our 3-deck fixture has max cooccur=2, so
        # the table should be empty.
        table = pmi_table(_THREE_DECKS)
        assert len(table) == 0


# ---------------------------------------------------------------------------
# Test 3: multiplicity deduplication (presence-based, not frequency-based)
# ---------------------------------------------------------------------------


class TestMultiplicityDedup:
    def test_duplicate_cards_count_once_per_deck(self) -> None:
        # Deck with 3 copies of "A" and 2 copies of "B":
        # presence-based → p_AB = 1/1 = 1.0, p_A = 1.0, p_B = 1.0
        # pmi = log(1 / (1 * 1)) = 0.0
        decks = [_deck(["A", "A", "A", "B", "B"])]
        table = pmi_table(decks, freq_floor=1)
        assert ("A", "B") in table
        row = table["A", "B"]
        assert row.cooccur == 1
        assert math.isclose(row.pmi, 0.0, abs_tol=1e-9)


# ---------------------------------------------------------------------------
# Test 5: interaction_lr — controlled logistic-regression interaction
# ---------------------------------------------------------------------------


def _corpus_with_interaction(n: int = 4000, seed: int = 0, n_filler: int = 8) -> list[Deck]:
    """Synthetic corpus with a planted A×B super-additive interaction.

    A and B each appear ~40% of the time, independently.  ``n_filler`` distinct
    FILLER cards each appear independently ~50% of the time (so filler pairs vary
    and clear a freq_floor of 50, but carry no planted interaction).  The win
    log-odds are ``-1 + 0.2·A + 0.2·B + 1.5·(A∧B) + 0.05·(n_fillers − n_filler/2)``:
    small main effects, a large interaction, and a deck-size confound (via filler
    count) the estimators must control out.
    """
    import random

    rng = random.Random(seed)
    decks: list[Deck] = []
    for _ in range(n):
        has_a = rng.random() < 0.4
        has_b = rng.random() < 0.4
        fillers = [f"FILLER{i}" for i in range(n_filler) if rng.random() < 0.5]
        lo = (
            -1.0
            + 0.2 * has_a
            + 0.2 * has_b
            + 1.5 * (has_a and has_b)
            + 0.05 * (len(fillers) - n_filler / 2)
        )
        p = 1.0 / (1.0 + math.exp(-lo))
        cards = list(fillers)
        if has_a:
            cards.append("A")
        if has_b:
            cards.append("B")
        decks.append(
            Deck(cards=cards, won=rng.random() < p, player_id=None, skill=None, ascension=0)
        )
    return decks


class TestInteractionLr:
    def test_recovers_positive_interaction(self) -> None:
        from synergy.observational import interaction_lr

        table = interaction_lr(_corpus_with_interaction(), freq_floor=50, n_boot=50)
        row = table["A", "B"]
        assert row.beta_ab > 0.5  # planted 1.5, shrunk by L2
        assert row.ci[0] > 0  # CI excludes 0
        assert row.n_both > 0
        assert row.n == 4000

    def test_null_pair_ci_straddles_zero(self) -> None:
        from synergy.observational import interaction_lr

        table = interaction_lr(_corpus_with_interaction(), freq_floor=50, n_boot=50)
        # Filler pairs carry no planted interaction; at least one such pair must
        # exist and its CI must straddle zero.
        filler_rows = [
            r for (x, y), r in table.items() if x.startswith("FILLER") and y.startswith("FILLER")
        ]
        assert filler_rows, "expected filler pairs above freq_floor"
        assert any(r.ci[0] < 0 < r.ci[1] for r in filler_rows)

    def test_canonical_pairs_and_empty(self) -> None:
        from synergy.observational import interaction_lr

        assert interaction_lr([], freq_floor=1) == {}
        table = interaction_lr(_corpus_with_interaction(n=800), freq_floor=50, n_boot=10)
        for pair in table:
            assert pair == tuple(sorted(pair))


# ---------------------------------------------------------------------------
# Test 6: fm_interactions — factorization-machine joint interaction estimator
# ---------------------------------------------------------------------------


class TestFmInteractions:
    def test_ranks_planted_pair_high(self) -> None:
        from synergy.observational import fm_interactions

        res = fm_interactions(_corpus_with_interaction(), freq_floor=50, dim=8, epochs=80)
        ab = res.interactions["A", "B"]
        fillers = [
            v
            for (x, y), v in res.interactions.items()
            if x.startswith("FILLER") and y.startswith("FILLER")
        ]
        assert fillers
        assert ab > statistics.median(fillers)

    def test_embeddings_shape(self) -> None:
        from synergy.observational import fm_interactions

        res = fm_interactions(_corpus_with_interaction(n=800), freq_floor=50, dim=8, epochs=20)
        assert "A" in res.embeddings
        assert len(res.embeddings["A"]) == 8
