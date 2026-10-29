"""Tests for synergy.multiplicity — FDR + EB-shrinkage post-processing (§9b)."""

from __future__ import annotations

import itertools

import numpy as np
import pytest

from synergy.corpus import Deck
from synergy.multiplicity import (
    MultiplicityRow,
    bh_fdr,
    eb_shrink,
    empirical_pvalues,
    multiplicity_layer,
    permutation_null,
)
from synergy.observational import InteractionRow

# ---------------------------------------------------------------------------
# bh_fdr
# ---------------------------------------------------------------------------


class TestBhFdr:
    def test_empty(self) -> None:
        assert bh_fdr([]) == []

    def test_all_zero_pvalues_give_zero_q(self) -> None:
        assert bh_fdr([0.0, 0.0, 0.0]) == [0.0, 0.0, 0.0]

    def test_single_p_is_itself(self) -> None:
        assert bh_fdr([0.037]) == pytest.approx([0.037])

    def test_monotone_in_sorted_order(self) -> None:
        q = bh_fdr([0.01, 0.02, 0.03, 0.04, 0.05])
        s = sorted(q)
        assert all(a <= b + 1e-12 for a, b in itertools.pairwise(s))

    def test_known_values(self) -> None:
        # p=[0.001,0.008,0.039,0.041,0.042], m=5.
        # raw m/k*p = [0.005,0.02,0.065,0.05125,0.042]; cummin from the top
        # (rank 5 → 1) pulls ranks 3 and 4 down to the rank-5 value 0.042 →
        # [0.005,0.02,0.042,0.042,0.042].
        q = bh_fdr([0.001, 0.008, 0.039, 0.041, 0.042])
        assert q == pytest.approx([0.005, 0.02, 0.042, 0.042, 0.042])

    def test_capped_at_one(self) -> None:
        assert all(v <= 1.0 for v in bh_fdr([0.5, 0.9, 0.95]))

    def test_preserves_input_order(self) -> None:
        q_fwd = bh_fdr([0.001, 0.042])
        q_rev = bh_fdr([0.042, 0.001])
        assert q_rev == pytest.approx(q_fwd[::-1])


# ---------------------------------------------------------------------------
# empirical_pvalues
# ---------------------------------------------------------------------------


class TestEmpiricalPvalues:
    def test_obs_far_beyond_null_is_small(self) -> None:
        null = {("A", "B"): [0.0, 0.1, -0.1, 0.05, -0.05]}
        p = empirical_pvalues({("A", "B"): 5.0}, null)
        # No |null| >= 5 → p = 1/6.
        assert p["A", "B"] == pytest.approx(1.0 / 6.0)

    def test_obs_inside_null_is_large(self) -> None:
        null = {("A", "B"): [1.0, -1.0, 2.0, -2.0, 3.0]}
        p = empirical_pvalues({("A", "B"): 0.0}, null)
        assert p["A", "B"] == pytest.approx(1.0)

    def test_two_sided_uses_magnitude(self) -> None:
        null = {("A", "B"): [0.0, 0.0, 0.0, 0.0]}
        assert empirical_pvalues({("A", "B"): -3.0}, null)["A", "B"] == pytest.approx(0.2)

    def test_missing_null_gives_one(self) -> None:
        assert empirical_pvalues({("A", "B"): 2.0}, {})["A", "B"] == pytest.approx(1.0)


# ---------------------------------------------------------------------------
# eb_shrink
# ---------------------------------------------------------------------------


class TestEbShrink:
    def test_empty(self) -> None:
        assert eb_shrink({}, {}) == {}

    def test_noisy_pair_shrinks_toward_card_mean_more_than_precise(self) -> None:
        # Both cards A and B are precisely ~0 across many pairs; only their
        # shared pair (A,B) is a noisy 2.0 → both card means stay near 0 and the
        # noisy estimate is pulled hard toward that shared ~0 prior.
        est = {("A", f"C{i}"): 0.0 for i in range(6)}
        est.update({("B", f"D{i}"): 0.0 for i in range(6)})
        var = {p: 0.01 for p in est}
        est["A", "B"] = 2.0
        var["A", "B"] = 4.0  # very noisy
        out = eb_shrink(est, var)
        assert out["A", "B"] < 1.0

    def test_precise_estimate_barely_moves(self) -> None:
        est = {("A", "B"): 1.0, ("A", "C"): 1.0, ("A", "D"): 1.0}
        var = {("A", "B"): 1e-6, ("A", "C"): 1e-6, ("A", "D"): 1e-6}
        out = eb_shrink(est, var)
        for p in est:
            assert out[p] == pytest.approx(1.0, abs=1e-3)

    def test_zero_between_variance_shrinks_to_prior(self) -> None:
        est = {("A", "B"): 0.5, ("A", "C"): 0.5, ("B", "C"): 0.5}
        var = {p: 1.0 for p in est}
        out = eb_shrink(est, var)
        for p in est:
            assert out[p] == pytest.approx(0.5, abs=1e-6)

    def test_result_between_raw_and_prior(self) -> None:
        est = {("A", "B"): 3.0, ("A", "C"): 0.0, ("A", "D"): 0.0}
        var = {("A", "B"): 1.0, ("A", "C"): 1.0, ("A", "D"): 1.0}
        out = eb_shrink(est, var)
        assert 0.0 < out["A", "B"] < 3.0


# ---------------------------------------------------------------------------
# permutation_null + multiplicity_layer
# ---------------------------------------------------------------------------


def _mean_diff_estimator(pairs):
    """Cheap point estimator: win-rate(both) − win-rate(not-both) per pair."""

    def est(decks):
        out = {}
        for a, b in pairs:
            both = [d.won for d in decks if a in d.cards and b in d.cards]
            rest = [d.won for d in decks if not (a in d.cards and b in d.cards)]
            mb = float(np.mean(both)) if both else 0.0
            mr = float(np.mean(rest)) if rest else 0.0
            out[a, b] = mb - mr
        return out

    return est


class TestPermutationNull:
    def test_shapes_and_determinism(self) -> None:
        rng = np.random.default_rng(0)
        decks = [
            Deck(
                cards=["A", "B"] if rng.random() < 0.5 else ["A", "C"],
                won=bool(rng.random() < 0.5),
                player_id=None,
                skill=None,
                ascension=0,
            )
            for _ in range(60)
        ]
        est = _mean_diff_estimator([("A", "B")])
        n1 = permutation_null(decks, est, n_perm=20, seed=1)
        n2 = permutation_null(decks, est, n_perm=20, seed=1)
        assert n1["A", "B"] == n2["A", "B"]
        assert len(n1["A", "B"]) == 20


class TestMultiplicityLayerCalibration:
    def _null_corpus(self, seed: int) -> list[Deck]:
        rng = np.random.default_rng(seed)
        decks = []
        for _ in range(400):
            cards = ["A"] if rng.random() < 0.5 else []
            cards += ["B"] if rng.random() < 0.5 else []
            cards += ["C", "D"]
            decks.append(
                Deck(
                    cards=cards,
                    won=bool(rng.random() < 0.5),
                    player_id=None,
                    skill=None,
                    ascension=0,
                )
            )
        return decks

    def test_planted_null_pvalue_not_tiny(self) -> None:
        decks = self._null_corpus(0)
        est = _mean_diff_estimator([("A", "B")])
        inter = {
            ("A", "B"): InteractionRow(
                beta_ab=est(decks)["A", "B"], ci=(-0.1, 0.1), n_both=1, n=len(decks)
            )
        }
        rows = multiplicity_layer(inter, decks, est, n_perm=100, seed=0)
        assert rows["A", "B"].pvalue > 0.1

    def test_planted_positive_pvalue_small(self) -> None:
        rng = np.random.default_rng(1)
        decks = []
        for _ in range(400):
            has_a = rng.random() < 0.5
            has_b = rng.random() < 0.5
            base = 0.2 + (0.5 if (has_a and has_b) else 0.0)
            cards = (["A"] if has_a else []) + (["B"] if has_b else []) + ["C"]
            decks.append(
                Deck(
                    cards=cards,
                    won=bool(rng.random() < base),
                    player_id=None,
                    skill=None,
                    ascension=0,
                )
            )
        est = _mean_diff_estimator([("A", "B")])
        inter = {
            ("A", "B"): InteractionRow(
                beta_ab=est(decks)["A", "B"], ci=(0.2, 0.4), n_both=1, n=len(decks)
            )
        }
        rows = multiplicity_layer(inter, decks, est, n_perm=100, seed=0)
        assert rows["A", "B"].pvalue < 0.1
        assert rows["A", "B"].raw_S > 0.0

    def test_row_carries_raw_and_shrunk(self) -> None:
        decks = self._null_corpus(2)
        est = _mean_diff_estimator([("A", "B")])
        raw = est(decks)["A", "B"]
        inter = {("A", "B"): InteractionRow(beta_ab=raw, ci=(-0.1, 0.1), n_both=1, n=len(decks))}
        rows = multiplicity_layer(inter, decks, est, n_perm=30, seed=0)
        r = rows["A", "B"]
        assert isinstance(r, MultiplicityRow)
        assert r.raw_S == pytest.approx(raw)
        assert 0.0 < r.pvalue <= 1.0
        assert 0.0 <= r.qvalue <= 1.0
