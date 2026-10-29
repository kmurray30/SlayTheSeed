"""Gates for the surrogate's deck-sensitivity report.

`eval_combat_surrogate_net.py` grew a within-`(encounter, ascension)` rank
agreement report, which is what decides whether the PPO screen's surrogate
combat backend may be used for deck-shaped questions at all
(`docs/ppo-screen-playbook.md`). Two things are worth pinning:

- the hand-rolled Spearman agrees with scipy, including under ties (it is
  hand-rolled to keep the gate script free of a scipy dependency it does not
  otherwise have);
- the aggregate actually distinguishes a bucket-mean predictor from one that
  ranks inside the bucket, which is the whole point.
"""

from __future__ import annotations

import os
import sys

import numpy as np
import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "python"))

pytest.importorskip("sklearn")

from eval_combat_surrogate_net import (
    _rankdata,
    _spearman,
    _within_bucket_rank_agreement,
)


def test_spearman_matches_scipy_including_ties():
    scipy_stats = pytest.importorskip("scipy.stats")
    rng = np.random.default_rng(0)
    for _ in range(20):
        n = int(rng.integers(5, 40))
        a = rng.normal(size=n)
        b = rng.normal(size=n)
        assert _spearman(a, b) == pytest.approx(scipy_stats.spearmanr(a, b).statistic, abs=1e-9)
    # Heavy ties: rounding to one decimal collides many values, which is the
    # case a naive rank implementation gets wrong.
    a = np.round(rng.normal(size=60), 1)
    b = np.round(rng.normal(size=60), 1)
    assert _spearman(a, b) == pytest.approx(scipy_stats.spearmanr(a, b).statistic, abs=1e-9)
    assert _rankdata(np.array([3.0, 1.0, 1.0, 2.0])).tolist() == [4.0, 1.5, 1.5, 3.0]


def _buckets(n_buckets: int, per: int) -> list[tuple[int, int]]:
    return [(b, 0) for b in range(n_buckets) for _ in range(per)]


def test_bucket_mean_predictor_scores_near_zero():
    """A predictor that knows only the encounter — the failure mode the report
    exists to catch — must not look good here, however good its overall AUC."""
    rng = np.random.default_rng(1)
    keys = _buckets(6, 20)
    truth = np.array([0.2 * b + rng.normal(scale=0.1) for b, _ in keys])
    # Constant within each bucket ⇒ every bucket is degenerate on the
    # prediction side and is skipped, so nothing is measurable.
    pred = np.array([0.2 * b for b, _ in keys])
    out = _within_bucket_rank_agreement(keys, pred, truth)
    assert out["n_buckets"] == 0
    assert out["skipped_degenerate"] == 6

    # A bucket mean plus pure noise: measurable, but uncorrelated.
    pred_noisy = pred + rng.normal(scale=1e-3, size=len(pred))
    out = _within_bucket_rank_agreement(keys, pred_noisy, truth)
    assert out["n_buckets"] == 6
    assert abs(out["mean_rho"]) < 0.35, out


def test_deck_sensitive_predictor_scores_high():
    rng = np.random.default_rng(2)
    keys = _buckets(6, 20)
    within = rng.normal(size=len(keys))
    truth = np.array([0.2 * b for b, _ in keys]) + within
    pred = np.array([0.2 * b for b, _ in keys]) + within + rng.normal(scale=0.1, size=len(keys))
    out = _within_bucket_rank_agreement(keys, pred, truth)
    assert out["n_buckets"] == 6
    assert out["mean_rho"] > 0.9, out


def test_small_buckets_are_skipped_not_counted_as_zero():
    """A singleton bucket has no within-cell ranking; folding it in as rho=0
    would drag the aggregate toward 'no signal' for a reason unrelated to the
    net."""
    rng = np.random.default_rng(3)
    keys = [*_buckets(1, 20), (99, 0), (98, 0)]
    within = rng.normal(size=20)
    truth = np.concatenate([within, [0.5, 0.7]])
    pred = np.concatenate([within + rng.normal(scale=0.05, size=20), [0.1, 0.9]])
    out = _within_bucket_rank_agreement(keys, pred, truth)
    assert out["n_buckets"] == 1
    assert out["skipped_small"] == 2
    assert out["n_rows"] == 20
    assert out["mean_rho"] > 0.9


def test_weighting_is_by_rows_not_buckets():
    """A 100-row bucket should not be outvoted by a 10-row one."""
    rng = np.random.default_rng(4)
    big_within = rng.normal(size=100)
    small_within = rng.normal(size=10)
    keys = [(0, 0)] * 100 + [(1, 0)] * 10
    truth = np.concatenate([big_within, small_within])
    # Big bucket ranked perfectly, small bucket ranked backwards.
    pred = np.concatenate([big_within, -small_within])
    out = _within_bucket_rank_agreement(keys, pred, truth)
    assert out["n_buckets"] == 2
    # Row-weighted: (100*1 + 10*-1) / 110 ≈ 0.818. Bucket-weighted would be 0.
    assert out["mean_rho"] == pytest.approx((100 * 1.0 + 10 * -1.0) / 110, abs=1e-6)
