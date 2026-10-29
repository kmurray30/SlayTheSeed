"""Pairwise co-occurrence and PMI for the card-synergy catalog.

Computes pointwise mutual information (PMI) over card-pair co-occurrence
across a corpus of :class:`~synergy.corpus.Deck` objects.

Usage::

    from synergy.corpus import load_decks
    from synergy.observational import pmi_table, interaction_lr

    decks = list(load_decks())
    table = pmi_table(decks, freq_floor=50)
    for pair, row in sorted(table.items(), key=lambda kv: -kv[1].pmi)[:20]:
        print(pair, f"pmi={row.pmi:.3f}  n={row.cooccur}")

    inter = interaction_lr(decks, freq_floor=50)
    for pair, row in sorted(inter.items(), key=lambda kv: -kv[1].beta_ab)[:20]:
        print(pair, f"beta_ab={row.beta_ab:.3f}  ci={row.ci}")
"""

from __future__ import annotations

import math
from collections import defaultdict
from collections.abc import Iterable
from dataclasses import dataclass
from itertools import combinations

import numpy as np

from synergy.card_filter import NON_DECKBUILDABLE
from synergy.corpus import Deck, deck_size

# ---------------------------------------------------------------------------
# Public types
# ---------------------------------------------------------------------------

Pair = tuple[str, str]
"""Canonical ordered card pair: always ``tuple(sorted((a, b)))``."""


@dataclass
class ObsRow:
    """Observational statistics for a card pair.

    Attributes:
        pmi: Pointwise mutual information ``log(p_ab / (p_a * p_b))``,
            natural log.
        cooccur: Number of decks that contain both cards (presence-based).
        p_a: Marginal probability of the first card (alphabetically lower).
        p_b: Marginal probability of the second card.
        p_ab: Joint probability of both cards appearing in the same deck.
    """

    pmi: float
    cooccur: int
    p_a: float
    p_b: float
    p_ab: float


# ---------------------------------------------------------------------------
# Core computation
# ---------------------------------------------------------------------------


def pmi_table(
    decks: Iterable[Deck],
    freq_floor: int = 50,
) -> dict[Pair, ObsRow]:
    """Compute pairwise PMI over a deck corpus.

    Each deck contributes at most one count per card (presence-based:
    ``set(deck.cards)`` deduplicates multiplicity).  Pairs with fewer
    than *freq_floor* co-occurrences are omitted from the result.

    Args:
        decks: Iterable of :class:`~synergy.corpus.Deck` objects.
        freq_floor: Minimum co-occurrence count to include a pair.
            Pairs below this threshold are dropped.

    Returns:
        Mapping from canonical :data:`Pair` to :class:`ObsRow`.
        Empty dict when the corpus is empty or no pair meets *freq_floor*.
    """
    card_count: dict[str, int] = defaultdict(int)
    pair_count: dict[Pair, int] = defaultdict(int)
    n_decks = 0

    for deck in decks:
        # Prune curses / statuses / combat-token / quest cards: they are never
        # deckbuilding choices, so they carry no synergy signal (see card_filter).
        unique = sorted(set(deck.cards) - NON_DECKBUILDABLE)
        n_decks += 1
        for card in unique:
            card_count[card] += 1
        for a, b in combinations(unique, 2):
            pair_count[a, b] += 1

    if n_decks == 0:
        return {}

    result: dict[Pair, ObsRow] = {}
    for pair, cooccur in pair_count.items():
        if cooccur < freq_floor:
            continue
        a, b = pair
        p_a = card_count[a] / n_decks
        p_b = card_count[b] / n_decks
        p_ab = cooccur / n_decks
        pmi = math.log(p_ab / (p_a * p_b))
        result[pair] = ObsRow(pmi=pmi, cooccur=cooccur, p_a=p_a, p_b=p_b, p_ab=p_ab)

    return result


# ---------------------------------------------------------------------------
# interaction_lr — controlled logistic-regression interaction (replaces W-L)
# ---------------------------------------------------------------------------


@dataclass
class InteractionRow:
    """Controlled interaction estimate for a card pair.

    Attributes:
        beta_ab: Coefficient on the ``has_A · has_B`` term of a per-pair logistic
            regression ``won ~ has_A + has_B + has_A·has_B + ascension + deck_size``
            (log-odds scale).  Positive → the pair wins more than the sum of its
            marginal contributions, after controlling for ascension and deck size.
        ci: 95% bootstrap percentile CI on ``beta_ab`` (resampling decks).
        n_both: Number of decks containing both cards.
        n: Total decks used in the fit.
    """

    beta_ab: float
    ci: tuple[float, float]
    n_both: int
    n: int


def _standardize(values: list[float | None], missing_fill: float) -> np.ndarray:
    """Z-score a control column; ``None`` entries take *missing_fill* first.

    When the resulting column has zero variance (e.g. a single-ascension corpus)
    the standardized column is all zeros — a neutral control that the regression
    simply ignores.
    """
    arr = np.array([missing_fill if v is None else float(v) for v in values], dtype=float)
    sd = arr.std()
    if sd < 1e-12:  # constant column → neutral (all-zero) control
        return np.zeros_like(arr)
    return (arr - arr.mean()) / sd


def _control_columns(decks: list[Deck]) -> tuple[np.ndarray, np.ndarray]:
    """Return standardized (ascension, deck_size) control columns.

    Missing ascension is filled with the corpus mean of the present values so
    those rows land at ~0 after centering (neutral).
    """
    present = [float(d.ascension) for d in decks if d.ascension is not None]
    asc_mean = float(np.mean(present)) if present else 0.0
    asc_z = _standardize([d.ascension for d in decks], asc_mean)
    size_z = _standardize([float(deck_size(d)) for d in decks], 0.0)
    return asc_z, size_z


def _fit_interaction_beta(X: np.ndarray, y: np.ndarray, l2_C: float) -> float | None:
    """Fit the logistic model and return the interaction coefficient (col 2).

    Returns ``None`` when the target has a single class (an all-win / all-loss
    bootstrap resample), signalling the caller to drop that resample.
    """
    from sklearn.linear_model import LogisticRegression

    if np.unique(y).size < 2:
        return None
    # L2 is the solver default; passing penalty= explicitly is deprecated in
    # scikit-learn 1.8. C controls the (inverse) L2 strength.
    clf = LogisticRegression(C=l2_C, solver="lbfgs", max_iter=1000)
    clf.fit(X, y)
    return float(clf.coef_[0][2])


def interaction_lr(
    decks: Iterable[Deck],
    freq_floor: int = 50,
    *,
    n_boot: int = 200,
    l2_C: float = 1.0,
    seed: int = 0,
) -> dict[Pair, InteractionRow]:
    """Controlled logistic-regression interaction estimate per candidate pair.

    Replaces the structurally-broken wins-minus-losses score.  For each pair
    whose combined co-occurrence meets *freq_floor* (same universe as
    :func:`pmi_table`), fits ``won ~ has_A + has_B + has_A·has_B + ascension +
    deck_size`` over the whole corpus and reports the interaction coefficient
    with a bootstrap CI.

    Weak L2 (``C=l2_C``) regularizes against separation on rare pairs.  Character
    is not a control: callers load one character's corpus at a time, so it is
    constant and drops out.  Deck size is mildly post-treatment (collider risk)
    — this is a *candidate* signal, confirmed downstream by the causal ladder.

    Args:
        decks: Corpus decks (materialized internally).
        freq_floor: Minimum combined co-occurrence for a pair to be scored.
        n_boot: Bootstrap resamples for the CI.
        l2_C: Inverse L2 strength for the logistic fit (smaller → more shrinkage).
        seed: Seed for the bootstrap RNG.

    Returns:
        Mapping from canonical :data:`Pair` to :class:`InteractionRow`.  Empty
        when the corpus is empty or no pair meets *freq_floor*.
    """
    deck_list = list(decks)
    if not deck_list:
        return {}

    universe: set[Pair] = set(pmi_table(deck_list, freq_floor=freq_floor))
    if not universe:
        return {}

    n = len(deck_list)
    y = np.array([1 if d.won else 0 for d in deck_list], dtype=int)
    asc_z, size_z = _control_columns(deck_list)
    card_sets = [set(d.cards) for d in deck_list]

    needed: set[str] = set()
    for a, b in universe:
        needed.add(a)
        needed.add(b)
    present: dict[str, np.ndarray] = {
        c: np.array([1.0 if c in cs else 0.0 for cs in card_sets]) for c in needed
    }

    rng = np.random.default_rng(seed)
    result: dict[Pair, InteractionRow] = {}
    for a, b in universe:
        ha, hb = present[a], present[b]
        hab = ha * hb
        X = np.column_stack([ha, hb, hab, asc_z, size_z])

        beta = _fit_interaction_beta(X, y, l2_C)
        boots: list[float] = []
        for _ in range(n_boot):
            idx = rng.integers(0, n, n)
            bb = _fit_interaction_beta(X[idx], y[idx], l2_C)
            if bb is not None:
                boots.append(bb)
        if boots:
            lo, hi = (float(v) for v in np.percentile(boots, [2.5, 97.5]))
        else:
            lo = hi = float("nan")

        result[a, b] = InteractionRow(
            beta_ab=float(beta) if beta is not None else float("nan"),
            ci=(lo, hi),
            n_both=int(hab.sum()),
            n=n,
        )

    return result


# ---------------------------------------------------------------------------
# fm_interactions — factorization-machine joint interaction estimator
# ---------------------------------------------------------------------------


@dataclass
class FMResult:
    """Output of the factorization-machine estimator.

    Attributes:
        interactions: Pairwise interaction weight ``V[a] · V[b]`` for every pair
            meeting *freq_floor* — estimated jointly, so a rider third card cannot
            be miscredited to A×B the way the per-pair LR can.
        embeddings: Per-card latent vector ``V[card]`` for every card in the
            embedded universe.  Reused by §5 archetype discovery (View 3).
        epochs: Number of gradient-descent epochs run.
    """

    interactions: dict[Pair, float]
    embeddings: dict[str, np.ndarray]
    epochs: int


def fm_interactions(
    decks: Iterable[Deck],
    freq_floor: int = 50,
    *,
    dim: int = 16,
    epochs: int = 60,
    lr: float = 0.05,
    l2: float = 1e-3,
    seed: int = 0,
) -> FMResult:
    """Fit one logistic factorization machine over deck composition → ``won``.

    The card universe is every card appearing in at least *freq_floor* decks.
    Each deck is a multi-hot vector over that universe, plus standardized
    deck-size and ascension covariates carried as linear-only terms (no
    embedding, so pairwise interactions stay ``V[a]·V[b]`` between cards only).

    The FM score is
    ``w0 + w·x + w_size·size + w_asc·asc
      + ½ Σ_f[(Σ_i V[i,f] x_i)² − Σ_i V[i,f]² x_i²]``,
    trained full-batch by gradient descent on binary cross-entropy with L2 on
    ``w`` and ``V``.

    Args:
        decks: Corpus decks (materialized internally).
        freq_floor: Minimum deck count for a card to be embedded / a pair scored.
        dim: Latent embedding dimension.
        epochs: Gradient-descent epochs.
        lr: Learning rate.
        l2: L2 penalty on linear weights and embeddings.
        seed: Seed for embedding initialization.

    Returns:
        :class:`FMResult`.  Empty interactions/embeddings when no card meets the
        floor.
    """
    deck_list = list(decks)
    if not deck_list:
        return FMResult(interactions={}, embeddings={}, epochs=0)

    n_decks = len(deck_list)
    card_sets = [set(d.cards) for d in deck_list]

    # Embedded universe: cards in >= freq_floor decks.
    counts: dict[str, int] = defaultdict(int)
    for cs in card_sets:
        for c in cs:
            counts[c] += 1
    universe_cards = sorted(c for c, k in counts.items() if k >= freq_floor)
    if not universe_cards:
        return FMResult(interactions={}, embeddings={}, epochs=0)
    idx_of = {c: i for i, c in enumerate(universe_cards)}
    n_cards = len(universe_cards)

    # Multi-hot design + covariates + target.
    X = np.zeros((n_decks, n_cards), dtype=float)
    for r, cs in enumerate(card_sets):
        for c in cs:
            j = idx_of.get(c)
            if j is not None:
                X[r, j] = 1.0
    y = np.array([1.0 if d.won else 0.0 for d in deck_list], dtype=float)
    asc_z, size_z = _control_columns(deck_list)

    rng = np.random.default_rng(seed)
    V = rng.normal(0.0, 0.01, size=(n_cards, dim))
    w = np.zeros(n_cards)
    w0 = 0.0
    w_size = 0.0
    w_asc = 0.0

    for _ in range(epochs):
        # Forward.  (X binary ⇒ x_i² = x_i, so Σ_i V[i,f]² x_i² = (X @ V²) row sum.)
        S = X @ V  # (n, dim)
        quad = (X @ np.square(V)).sum(axis=1)
        inter = 0.5 * (np.square(S).sum(axis=1) - quad)
        z = w0 + X @ w + w_size * size_z + w_asc * asc_z + inter
        p = 1.0 / (1.0 + np.exp(-np.clip(z, -30.0, 30.0)))
        g = (p - y) / n_decks  # dL/dz per sample

        # Gradients.
        grad_w0 = g.sum()
        grad_w = X.T @ g + l2 * w
        grad_w_size = float(size_z @ g)
        grad_w_asc = float(asc_z @ g)
        # grad_V[i,f] = Σ_s g[s]·X[s,i]·(S[s,f] − V[i,f]·X[s,i])
        #            = (Xᵀ (g⊙S))[i,f] − V[i,f]·(Xᵀ g)[i]
        grad_V = X.T @ (g[:, None] * S) - V * (X.T @ g)[:, None] + l2 * V

        w0 -= lr * grad_w0
        w -= lr * grad_w
        w_size -= lr * grad_w_size
        w_asc -= lr * grad_w_asc
        V -= lr * grad_V

    embeddings = {c: V[idx_of[c]].copy() for c in universe_cards}
    universe_pairs: set[Pair] = set(pmi_table(deck_list, freq_floor=freq_floor))
    interactions: dict[Pair, float] = {}
    for a, b in universe_pairs:
        if a in idx_of and b in idx_of:
            interactions[a, b] = float(V[idx_of[a]] @ V[idx_of[b]])

    return FMResult(interactions=interactions, embeddings=embeddings, epochs=epochs)
