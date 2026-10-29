"""Tests for synergy.causal_dose — dose-response panels (§7).

Fight-driving functions take an injectable ``fight_fn`` so no ``synergy_fight``
subprocess is spawned; deterministic fake fights encode planted (super-)additive
structure.
"""

from __future__ import annotations

import numpy as np
import pytest

from synergy.causal_dose import (
    DoseResult,
    additive_prediction,
    convexity_gap,
    cuped_adjust,
    dose_archetype,
    dose_point,
    dose_response,
    permutation_percentile,
    solo_deltas,
    weighted_sample_package,
)

# ---------------------------------------------------------------------------
# Pure primitives
# ---------------------------------------------------------------------------


class TestPrimitives:
    def test_weighted_sample_distinct_and_sized(self) -> None:
        rng = np.random.default_rng(0)
        pkg = weighted_sample_package({"A": 1, "B": 1, "C": 1, "D": 1}, 3, rng)
        assert len(pkg) == 3
        assert len(set(pkg)) == 3

    def test_weighted_sample_caps_at_pool(self) -> None:
        rng = np.random.default_rng(0)
        pkg = weighted_sample_package({"A": 1, "B": 1}, 5, rng)
        assert set(pkg) == {"A", "B"}

    def test_weighted_sample_favors_heavy(self) -> None:
        rng = np.random.default_rng(0)
        heavy = sum("A" in weighted_sample_package({"A": 100, "B": 1}, 1, rng) for _ in range(200))
        assert heavy > 150

    def test_additive_prediction(self) -> None:
        assert additive_prediction(10.0, {"A": 2.0, "B": -1.0}, ["A", "B"]) == pytest.approx(11.0)

    def test_convexity_gap(self) -> None:
        assert convexity_gap(15.0, 11.0) == pytest.approx(4.0)

    def test_permutation_percentile(self) -> None:
        null = [0.0, 1.0, 2.0, 3.0]
        assert permutation_percentile(2.5, null) == pytest.approx(0.75)
        assert permutation_percentile(-1.0, null) == pytest.approx(0.0)

    def test_permutation_percentile_tie_is_midrank(self) -> None:
        # observed 2.0 ties one null value: (#<2=2 + 0.5·1) / 4 = 0.625.
        assert permutation_percentile(2.0, [0.0, 1.0, 2.0, 3.0]) == pytest.approx(0.625)

    def test_cuped_reduces_variance(self) -> None:
        rng = np.random.default_rng(0)
        x = rng.normal(size=500)
        y = 3.0 + 2.0 * x + rng.normal(scale=0.1, size=500)
        adj = cuped_adjust(list(y), list(x))
        assert np.var(adj) < np.var(y)
        assert np.mean(adj) == pytest.approx(float(np.mean(y)), abs=1e-9)

    def test_cuped_zero_variance_covariate_is_identity(self) -> None:
        y = [1.0, 2.0, 3.0]
        assert cuped_adjust(y, [5.0, 5.0, 5.0]) == pytest.approx(y)


# ---------------------------------------------------------------------------
# Fight-driven measurement
# ---------------------------------------------------------------------------


def _additive_fight(values: dict[str, int]):
    """hp_lost = 20 − Σ values[c] for cards in base+inject (lower hp = higher scalar)."""

    def fn(base: list[str], inject: list[str], enc: str, seed: int) -> dict:
        deck = list(base) + list(inject)
        hp = 20 - sum(values.get(c, 0) for c in deck)
        return {"won": hp < 20, "hp_lost": hp, "turns": 5}

    return fn


class TestMeasurement:
    def test_dose_point_panel_size(self) -> None:
        fn = _additive_fight({"A": 3})
        pts = dose_point(fn, [[]], ["A"], ["E1", "E2"], [1, 2, 3])
        assert len(pts) == 6  # 1 baseline × 2 enc × 3 seeds

    def test_solo_deltas_recovers_isolated_effect(self) -> None:
        fn = _additive_fight({"A": 4, "B": 1})
        v0, solo = solo_deltas(fn, [[]], ["A", "B"], ["E1"], [1, 2])
        assert v0 == pytest.approx(-20.0)  # base hp_lost 20 → scalar −20
        assert solo["A"] == pytest.approx(4.0)  # +A cuts 4 hp → +4 scalar
        assert solo["B"] == pytest.approx(1.0)


# ---------------------------------------------------------------------------
# dose_response assembly + verdict
# ---------------------------------------------------------------------------


def _synergy_fight(base_vals: dict[str, int], synergy_set: set[str], bonus: int):
    """Additive per-card plus a super-additive bonus when >=2 synergy cards co-occur."""

    def fn(base: list[str], inject: list[str], enc: str, seed: int) -> dict:
        deck = list(base) + list(inject)
        hp = 20 - sum(base_vals.get(c, 0) for c in deck)
        present = sum(1 for c in synergy_set if c in deck)
        if present >= 2:
            hp -= bonus * (present - 1)
        return {"won": hp < 20, "hp_lost": max(0, hp), "turns": 5}

    return fn


class TestDoseResponse:
    def test_superadditive_archetype_beats_null(self) -> None:
        vals = {c: 2 for c in "ABCXYZ"}
        fn = _synergy_fight(vals, {"A", "B", "C"}, bonus=5)
        res = dose_response(
            fn,
            {"A": 1.0, "B": 1.0, "C": 1.0},
            ["X", "Y", "Z"],
            [[]],
            ["E1"],
            [1, 2, 3],
            K=3,
            target_k=3,
            n_draws=3,
            n_null=30,
            seed=0,
        )
        assert isinstance(res, DoseResult)
        assert res.convexity_gap > 0.0
        assert res.null_percentile >= 0.8

    def test_additive_archetype_near_null_middle(self) -> None:
        vals = {c: 2 for c in "ABCXYZ"}
        fn = _synergy_fight(vals, set(), bonus=0)  # no synergy anywhere
        res = dose_response(
            fn,
            {"A": 1.0, "B": 1.0, "C": 1.0},
            ["X", "Y", "Z"],
            [[]],
            ["E1"],
            [1, 2, 3],
            K=3,
            target_k=3,
            n_draws=3,
            n_null=30,
            seed=0,
        )
        assert res.null_percentile < 0.9


class TestDoseArchetype:
    def test_derives_control_pool_from_geometry(self) -> None:
        # Archetype {A,B,C}; control candidates near A,B,C in embedding space but
        # in a different cluster. dose_archetype should run the panel end to end.
        vals = {c: 2 for c in "ABCXYZ"}
        fn = _synergy_fight(vals, {"A", "B", "C"}, bonus=5)
        emb = {
            "A": np.array([1.0, 0.0]),
            "B": np.array([0.98, 0.1]),
            "C": np.array([0.97, 0.12]),
            "X": np.array([0.9, 0.2]),  # near the A-cluster, different cluster id
            "Y": np.array([0.88, 0.25]),
            "Z": np.array([0.85, 0.3]),
        }
        clusters = {"A": 0, "B": 0, "C": 0, "X": 1, "Y": 1, "Z": 1}
        res = dose_archetype(
            fn,
            ["A", "B", "C"],
            emb,
            clusters,
            [[]],
            ["E1"],
            [1, 2, 3],
            control_n=3,
            K=3,
            target_k=3,
            n_draws=3,
            n_null=20,
        )
        assert isinstance(res, DoseResult)
        assert res.convexity_gap > 0.0
        assert res.n_null > 0  # control pool derived and null drawn
