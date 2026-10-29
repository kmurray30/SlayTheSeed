"""Tests for synergy.causal_iv — offer-as-instrument Wald-IV (§4b)."""

from __future__ import annotations

import numpy as np
import pytest

from synergy.causal_iv import IvResult, harvest_offers, placebo_declined, wald_iv


def _confounded(seed: int = 0, n: int = 6000):
    # Skill ~ Bernoulli. Possession has TWO channels:
    #   * offer channel — skill-INDEPENDENT (a clean, valid instrument): if
    #     offered, kept with prob 0.8 regardless of skill;
    #   * other channel — skill-DEPENDENT confound: skilled players acquire the
    #     card elsewhere (shops/events) at prob 0.5.
    # Winning depends ONLY on skill (true possession→win effect is ZERO), so the
    # skill-driven other channel confounds a naive possession→win comparison
    # while the random offer instrument stays valid.
    rng = np.random.default_rng(seed)
    skill = rng.random(n) < 0.5
    offered = rng.random(n) < 0.5  # RNG instrument, independent of skill
    via_offer = offered & (rng.random(n) < 0.8)
    via_other = skill & (rng.random(n) < 0.5)
    possessed = via_offer | via_other
    won = rng.random(n) < np.where(skill, 0.6, 0.3)
    return offered.tolist(), possessed.tolist(), won.tolist(), skill


class TestWaldIv:
    def test_iv_recovers_null_where_ols_is_biased(self) -> None:
        offered, possessed, won, _ = _confounded()
        p = np.array(possessed)
        w = np.array(won)
        ols = w[p].mean() - w[~p].mean()  # naive → biased positive
        res = wald_iv(offered, possessed, won)
        assert isinstance(res, IvResult)
        assert ols > 0.05  # confound inflates OLS
        assert abs(res.beta_iv) < 0.06  # IV ~ the true null
        assert not res.weak_instrument

    def test_weak_instrument_flagged_for_staple(self) -> None:
        # Always possessed regardless of offer → no first-stage variation.
        n = 500
        offered = [i % 2 == 0 for i in range(n)]
        possessed = [True] * n
        won = [i % 3 == 0 for i in range(n)]
        res = wald_iv(offered, possessed, won)
        assert res.weak_instrument

    def test_placebo_declined_is_flat(self) -> None:
        offered, possessed, won, _ = _confounded()
        assert abs(placebo_declined(offered, possessed, won)) < 0.06


class TestHarvestOffers:
    def test_extracts_when_present(self) -> None:
        runs = [
            {"win": True, "offers": [{"card": "A", "picked": True}]},
            {
                "win": False,
                "offers": [{"card": "A", "picked": False}, {"card": "B", "picked": True}],
            },
        ]
        off, poss, won = harvest_offers(runs, "A")
        assert off == [True, True]
        assert poss == [True, False]
        assert won == [True, False]

    def test_empty_when_field_absent(self) -> None:
        assert harvest_offers([{"win": True, "deck": []}], "A") == ([], [], [])
