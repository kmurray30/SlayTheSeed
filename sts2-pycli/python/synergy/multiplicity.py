"""Multiplicity control for the synergy catalog (spec §9b).

Post-processing over the per-pair interaction estimates:

* :func:`eb_shrink` — closed-form empirical-Bayes shrinkage toward card-level
  means (counters the winner's-curse ranking bias on noisy small-n pairs).
* :func:`permutation_null` / :func:`empirical_pvalues` — an exact permutation
  null (``won`` shuffled within deck-size × ascension strata) → two-sided
  per-edge p-values.
* :func:`bh_fdr` — Benjamini-Hochberg q-values controlling the false-discovery
  rate across the thousands of pairs × estimators the pipeline screens.
* :func:`multiplicity_layer` — ties them into ``dict[Pair, MultiplicityRow]``
  for :func:`synergy.catalog.build_catalog` to gate the ``undiscovered`` quadrant.

Raw estimates are always reported next to the shrunk / FDR-controlled ones so a
real-but-rare synergy erased by shrinkage stays visible (spec §9b guardrail).
"""

from __future__ import annotations

import dataclasses
from collections import defaultdict
from collections.abc import Callable
from dataclasses import dataclass
from typing import TYPE_CHECKING

import numpy as np

from synergy.corpus import Deck, deck_size
from synergy.observational import Pair

if TYPE_CHECKING:
    from synergy.observational import InteractionRow


# ---------------------------------------------------------------------------
# Benjamini-Hochberg FDR
# ---------------------------------------------------------------------------


def bh_fdr(pvalues: list[float]) -> list[float]:
    """Benjamini-Hochberg step-up q-values, returned in the input order.

    For sorted p-values ``p_(1) ≤ … ≤ p_(m)`` the BH-adjusted value is
    ``q_(i) = min_{k ≥ i} (m / k) · p_(k)``, capped at 1.  The
    cumulative-minimum-from-the-top pass guarantees monotonicity in sorted order.

    Args:
        pvalues: Raw per-edge p-values (any order).

    Returns:
        q-values aligned to *pvalues*; ``[]`` for empty input.
    """
    m = len(pvalues)
    if m == 0:
        return []
    order = sorted(range(m), key=lambda i: pvalues[i])
    q_sorted = [0.0] * m
    running = 1.0
    # Walk from the largest p (rank m) down to rank 1, taking a running minimum.
    for rank in range(m, 0, -1):
        i = order[rank - 1]
        val = (m / rank) * pvalues[i]
        running = min(running, val)
        q_sorted[i] = min(running, 1.0)
    return q_sorted


# ---------------------------------------------------------------------------
# Empirical (permutation) p-values
# ---------------------------------------------------------------------------


def empirical_pvalues(
    observed: dict[Pair, float],
    null: dict[Pair, list[float]],
) -> dict[Pair, float]:
    """Two-sided permutation p-value per pair from its own null draws.

    ``p = (1 + #{|null_j| ≥ |obs|}) / (1 + n_draws)`` — add-one smoothing keeps
    p strictly positive.  A pair absent from *null* (no draws) gets ``p = 1.0``.

    Args:
        observed: Raw estimate per pair.
        null: Permutation null draws per pair (magnitudes compared two-sided).

    Returns:
        Mapping pair → p-value in ``(0, 1]``.
    """
    out: dict[Pair, float] = {}
    for pair, obs in observed.items():
        draws = null.get(pair)
        if not draws:
            out[pair] = 1.0
            continue
        arr = np.abs(np.asarray(draws, dtype=float))
        n_ge = int(np.count_nonzero(arr >= abs(obs)))
        out[pair] = (1 + n_ge) / (1 + len(draws))
    return out


# ---------------------------------------------------------------------------
# Empirical-Bayes shrinkage (card-level random effects)
# ---------------------------------------------------------------------------


def eb_shrink(
    estimates: dict[Pair, float],
    variances: dict[Pair, float],
    *,
    tau2_floor: float = 1e-6,
) -> dict[Pair, float]:
    """Closed-form card-level empirical-Bayes shrinkage of pair estimates.

    Model: ``S_ab ~ Normal(θ_ab, v_ab)`` with prior ``θ_ab ~ Normal(m_ab, τ²)``
    where the prior mean ``m_ab = (μ_a + μ_b) / 2`` borrows strength across a
    pair's two cards (``μ_c`` = precision-weighted mean of card *c*'s pairs) and
    τ² is a single global between-pair variance by method of moments.  The
    posterior mean ``(S/v + m/τ²) / (1/v + 1/τ²)`` pulls noisy (large-v) pairs
    toward ``m_ab`` while precise pairs keep their raw value.

    Args:
        estimates: Raw estimate per pair.
        variances: Sampling variance per pair (``se²``); non-positive → treated
            as a tiny positive floor.
        tau2_floor: Lower bound on the between-pair variance τ².

    Returns:
        Posterior-mean estimate per pair.
    """
    if not estimates:
        return {}
    pairs = list(estimates)
    v = {p: max(float(variances.get(p, 0.0)), 1e-12) for p in pairs}

    # Card means μ_c: precision-weighted over the card's pairs.
    num: dict[str, float] = defaultdict(float)
    den: dict[str, float] = defaultdict(float)
    for p in pairs:
        w = 1.0 / v[p]
        for card in p:
            num[card] += w * estimates[p]
            den[card] += w
    mu = {c: (num[c] / den[c] if den[c] > 0 else 0.0) for c in num}

    prior = {p: 0.5 * (mu[p[0]] + mu[p[1]]) for p in pairs}

    # τ² by method of moments: Var(S) − mean(v), floored.
    s_arr = np.asarray([estimates[p] for p in pairs], dtype=float)
    mean_v = float(np.mean([v[p] for p in pairs]))
    tau2 = max(float(np.var(s_arr)) - mean_v, tau2_floor)

    out: dict[Pair, float] = {}
    for p in pairs:
        wv = 1.0 / v[p]
        wt = 1.0 / tau2
        out[p] = (estimates[p] * wv + prior[p] * wt) / (wv + wt)
    return out


# ---------------------------------------------------------------------------
# Permutation null driver
# ---------------------------------------------------------------------------


def _strata_key(deck: Deck, size_bucket: int = 4) -> tuple[int, object]:
    """Stratum = (deck-size bucket, ascension) — preserves context structure."""
    return (deck_size(deck) // size_bucket, deck.ascension)


def permutation_null(
    decks: list[Deck],
    estimator: Callable[[list[Deck]], dict[Pair, float]],
    *,
    n_perm: int,
    seed: int = 0,
) -> dict[Pair, list[float]]:
    """Permutation null: re-run *estimator* with ``won`` shuffled within strata.

    Shuffling only the outcome labels — within (deck-size bucket, ascension)
    strata — breaks the card↔win association while preserving each deck's
    composition, size, and ascension context (spec §9b: "preserving
    deck-size/context structure").

    Args:
        decks: The real corpus.
        estimator: Point-estimate estimator ``decks → {pair: S}`` (no bootstrap).
        n_perm: Number of permutation replicates.
        seed: RNG seed.

    Returns:
        Per-pair list of *n_perm* null estimates.
    """
    strata: dict[tuple[int, object], list[int]] = defaultdict(list)
    for i, d in enumerate(decks):
        strata[_strata_key(d)].append(i)

    wons = [d.won for d in decks]
    rng = np.random.default_rng(seed)
    null: dict[Pair, list[float]] = defaultdict(list)
    for _ in range(n_perm):
        permuted = list(wons)
        for idxs in strata.values():
            shuffled = list(idxs)
            rng.shuffle(shuffled)
            for src, dst in zip(idxs, shuffled):
                permuted[dst] = wons[src]
        perm_decks = [dataclasses.replace(d, won=permuted[i]) for i, d in enumerate(decks)]
        for pair, s in estimator(perm_decks).items():
            null[pair].append(s)
    return dict(null)


# ---------------------------------------------------------------------------
# Assembly
# ---------------------------------------------------------------------------


@dataclass
class MultiplicityRow:
    """FDR / shrinkage summary for one pair (spec §9b).

    Attributes:
        raw_S: Raw interaction estimate (echoed, never overwritten).
        shrunk_S: Empirical-Bayes posterior mean.
        pvalue: Two-sided permutation p-value.
        qvalue: Benjamini-Hochberg FDR q-value across the scored family.
        n_both: Decks containing both cards.
    """

    raw_S: float
    shrunk_S: float
    pvalue: float
    qvalue: float
    n_both: int


def _variance_from_ci(ci: tuple[float, float]) -> float:
    """Sampling variance from a 95% CI: ``((hi − lo) / (2·1.96))²``."""
    lo, hi = ci
    if not (np.isfinite(lo) and np.isfinite(hi)) or hi <= lo:
        return 1.0
    return ((hi - lo) / (2.0 * 1.96)) ** 2


def multiplicity_layer(
    interaction: dict[Pair, InteractionRow],
    decks: list[Deck],
    estimator: Callable[[list[Deck]], dict[Pair, float]],
    *,
    n_perm: int = 100,
    seed: int = 0,
) -> dict[Pair, MultiplicityRow]:
    """Assemble EB-shrunk estimates + permutation p + BH q per pair.

    Args:
        interaction: ``interaction_lr`` output (raw ``beta_ab`` + CI per pair).
        decks: Corpus (for the permutation null).
        estimator: Point estimator matching *interaction*'s statistic (e.g.
            ``interaction_lr(..., n_boot=0)`` → ``beta_ab``).
        n_perm: Permutation replicates.
        seed: RNG seed.

    Returns:
        ``{pair: MultiplicityRow}``.
    """
    if not interaction:
        return {}
    raw = {p: r.beta_ab for p, r in interaction.items()}
    var = {p: _variance_from_ci(r.ci) for p, r in interaction.items()}
    shrunk = eb_shrink(raw, var)

    null = permutation_null(decks, estimator, n_perm=n_perm, seed=seed)
    pvals = empirical_pvalues(raw, null)

    pairs = list(interaction)
    qvals = bh_fdr([pvals[p] for p in pairs])

    return {
        p: MultiplicityRow(
            raw_S=float(raw[p]),
            shrunk_S=float(shrunk[p]),
            pvalue=float(pvals[p]),
            qvalue=float(q),
            n_both=interaction[p].n_both,
        )
        for p, q in zip(pairs, qvals)
    }
