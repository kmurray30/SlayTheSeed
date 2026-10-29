"""Leave-one-out combat replay (spec §7b).

Complements the forced-starter dose-response panels, which test decks that never
occur in real play (the distribution-shift failure mode that killed the earlier
PPO-combat arena A/B).  Here each real corpus deck is replayed at fixed seeds,
then re-simulated with one card struck; the Δ outcome is that card's *within-fight
marginal in that exact context*.  Bucketing a card's marginal by partner-present
vs partner-absent yields ``combat_lopo_S(A, B)``, an extra causal edge field.

Caveat (spec §7b): single-trajectory leave-one-out is a within-fight marginal,
not a full counterfactual (the agent would have drafted differently without the
card).  Report it as complementary to ``combat_S``, never a substitute.
"""

from __future__ import annotations

import numpy as np

from synergy.causal_dose import FightFn, dose_point
from synergy.observational import Pair


def _strike_one(deck: list[str], card: str) -> list[str]:
    """Return *deck* with the first copy of *card* removed."""
    out = list(deck)
    if card in out:
        out.remove(card)
    return out


def lopo_marginal(
    fight_fn: FightFn,
    deck: list[str],
    card: str,
    encounters: list[str],
    seeds: list[int],
) -> float:
    """One card's within-fight marginal: ``mean(V(deck)) − mean(V(deck∖card))``.

    ``V = −hp_lost`` (via :func:`synergy.causal_dose.dose_point`); the full deck
    is replayed at the same seed panel with and without one copy of the card.
    Positive → removing the card costs HP (the card was pulling its weight here).
    """
    full = dose_point(fight_fn, [deck], [], encounters, seeds)
    struck = dose_point(fight_fn, [_strike_one(deck, card)], [], encounters, seeds)
    v_full = float(np.mean(full)) if full else 0.0
    v_struck = float(np.mean(struck)) if struck else 0.0
    return v_full - v_struck


def combat_lopo_s(
    fight_fn: FightFn,
    decks: list[list[str]],
    pair: Pair,
    encounters: list[str],
    seeds: list[int],
) -> float | None:
    """Partner-conditioned LOO synergy ``combat_lopo_S(A, B)`` (spec §7b).

    For every deck containing card A, measure A's :func:`lopo_marginal` and bucket
    it by whether partner B is in the same deck.  Returns
    ``mean(marginal_A | B present) − mean(marginal_A | B absent)`` — positive when
    A contributes more with B alongside it.  ``None`` when either bucket is empty
    (the contrast is undefined).
    """
    a, b = pair
    with_b: list[float] = []
    without_b: list[float] = []
    for deck in decks:
        if a not in deck:
            continue
        m = lopo_marginal(fight_fn, deck, a, encounters, seeds)
        (with_b if b in deck else without_b).append(m)
    if not with_b or not without_b:
        return None
    return float(np.mean(with_b)) - float(np.mean(without_b))
