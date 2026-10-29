"""Offer-as-instrument causal tier (spec §4b).

Being offered a card is an RNG draw that strongly predicts possessing it, is
independent of pilot skill, and plausibly affects winning only through
possession — a natural encouragement design.  The binary-instrument Wald ratio
recovers the causal possession→win effect from *human* play, with a first-stage
F-stat gate (skip staples with no offered-but-declined variation) and a placebo
test (offered-but-declined must be flat, else the exclusion restriction breaks).

This is the only rung that yields causal evidence from human play (metas, skill
styles) rather than agent play; report it alongside the simulator ladder, never
as a substitute.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np


@dataclass
class IvResult:
    """Binary-instrument Wald-IV estimate for one card (spec §4b).

    Attributes:
        beta_iv: Wald ratio ``reduced_form_diff / first_stage_diff`` — the
            causal possession→win effect.
        first_stage_diff: ``E(possess|offered) − E(possess|not offered)`` (γ).
        reduced_form_diff: ``E(won|offered) − E(won|not offered)`` (ρ).
        first_stage_f: First-stage F-stat of the instrument (``t²`` of γ).
        weak_instrument: ``first_stage_f < 10`` (a staple with no offer variation).
        n: Number of runs used.
    """

    beta_iv: float
    first_stage_diff: float
    reduced_form_diff: float
    first_stage_f: float
    weak_instrument: bool
    n: int


def _group_diff(y: np.ndarray, g: np.ndarray) -> float:
    a, b = y[g], y[~g]
    return (float(a.mean()) if a.size else 0.0) - (float(b.mean()) if b.size else 0.0)


def wald_iv(offered: list[bool], possessed: list[bool], won: list[bool]) -> IvResult:
    """Binary-instrument Wald IV of possession → winning (spec §4b).

    Args:
        offered: Whether the card was offered this run (the RNG instrument).
        possessed: Whether the run's deck ended up holding the card.
        won: Whether the run won.

    Returns:
        An :class:`IvResult`.
    """
    o = np.array(offered, dtype=bool)
    p = np.array(possessed, dtype=float)
    w = np.array(won, dtype=float)
    n = int(o.size)
    first = _group_diff(p, o)  # γ: offered → possess
    reduced = _group_diff(w, o)  # ρ: offered → won
    beta = reduced / first if abs(first) > 1e-9 else float("nan")

    # First-stage F = t² of the two-group possession difference.  A staple
    # (possession constant across offer status) has γ = 0 and zero variance →
    # no instrument strength → F = 0 (weak); only a genuine mean gap with zero
    # within-group variance is a perfectly-strong instrument.
    na, nb = int(o.sum()), int((~o).sum())
    if na >= 2 and nb >= 2:
        va, vb = float(p[o].var(ddof=1)), float(p[~o].var(ddof=1))
        se = np.sqrt(va / na + vb / nb)
        if se > 1e-12:
            f = (first / se) ** 2
        else:
            f = float("inf") if abs(first) > 1e-12 else 0.0
    else:
        f = 0.0
    return IvResult(
        beta_iv=float(beta),
        first_stage_diff=float(first),
        reduced_form_diff=float(reduced),
        first_stage_f=float(f),
        weak_instrument=f < 10.0,
        n=n,
    )


def placebo_declined(offered: list[bool], possessed: list[bool], won: list[bool]) -> float:
    """Placebo exclusion test: win effect of the offer among *non-possessors*.

    Compares ``E(won | offered ∧ ¬possess)`` to ``E(won | ¬offered ∧ ¬possess)``
    — both groups end up without the card, so under a valid instrument (offer
    affects winning only through possession) the offer should not move winning
    here: ~0.  Conditioning on non-possession in *both* arms is what isolates the
    exclusion restriction; a non-zero value flags a broken instrument for that
    card.
    """
    o = np.array(offered, dtype=bool)
    p = np.array(possessed, dtype=bool)
    w = np.array(won, dtype=float)
    declined = o & ~p
    never = ~o & ~p
    a, b = w[declined], w[never]
    return (float(a.mean()) if a.size else 0.0) - (float(b.mean()) if b.size else 0.0)


def harvest_offers(runs: list[dict], card: str) -> tuple[list[bool], list[bool], list[bool]]:
    """Per-run ``(offered, possessed, won)`` for *card* from an ``offers`` field.

    Each run's ``offers`` is a list of ``{"card", "picked"}`` records.  Returns
    empty lists when the corpus lacks offer data (the current spire-codex schema)
    — the estimators then need an offer-instrumented corpus or the §4b Rust
    reward-RNG replay.
    """
    offered: list[bool] = []
    possessed: list[bool] = []
    won: list[bool] = []
    for run in runs:
        offers = run.get("offers")
        if not offers:
            continue
        seen = [o for o in offers if o.get("card") == card]
        if not seen:
            continue
        offered.append(True)
        possessed.append(any(o.get("picked") for o in seen))
        won.append(bool(run.get("win")))
    return offered, possessed, won
