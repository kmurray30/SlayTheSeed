"""Archetype discovery — spec §5 (NMF View 1 + graph communities View 2 + merge).

Two independent views propose candidate archetypes:

* **View 1 (NMF)** factorizes the win-adjusted, count-capped deck×card matrix into
  components — the *human-played* structure.
* **View 2 (communities)** runs Louvain over the FDR-controlled positive
  causal-pair graph — the *causal* structure.

They are merged and tagged (``confirmed-archetype`` / ``folklore`` /
``undiscovered``) by weighted-Jaccard overlap.  The tag records provenance;
downstream validation (§6–8) decides truth.
"""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass

import numpy as np

from synergy.card_filter import NON_DECKBUILDABLE
from synergy.corpus import Deck, deck_size
from synergy.observational import Pair

# ---------------------------------------------------------------------------
# View 1 — inputs
# ---------------------------------------------------------------------------


def regression_adjusted_weights(decks: list[Deck], *, floor: float = 0.05) -> np.ndarray:
    """Per-deck non-negative win weight adjusted for ascension + deck size.

    Fits logistic ``won ~ ascension_z + deck_size_z`` and returns
    ``clip(won − p_hat, 0, None) + floor``: winning decks are up-weighted by how
    much they beat their covariate-predicted win probability (a "surprising win"
    signal), losing decks fall to the floor so their co-occurrence structure
    still informs NMF without survivorship-poisoning the components (spec §5).
    """
    from sklearn.linear_model import LogisticRegression

    n = len(decks)
    if n == 0:
        return np.zeros(0)
    y = np.array([1.0 if d.won else 0.0 for d in decks])

    def _z(vals: list[float]) -> np.ndarray:
        arr = np.asarray(vals, dtype=float)
        sd = arr.std()
        return (arr - arr.mean()) / sd if sd > 1e-12 else np.zeros_like(arr)

    asc = _z([float(d.ascension) if d.ascension is not None else 0.0 for d in decks])
    size = _z([float(deck_size(d)) for d in decks])
    x = np.column_stack([asc, size])

    if np.unique(y).size < 2:
        p_hat = np.full(n, float(y.mean()))
    else:
        clf = LogisticRegression(solver="lbfgs", max_iter=1000)
        clf.fit(x, y)
        p_hat = clf.predict_proba(x)[:, 1]
    return np.clip(y - p_hat, 0.0, None) + floor


def deck_card_matrix(
    decks: list[Deck],
    *,
    cap: int = 3,
    weights: np.ndarray | None = None,
) -> tuple[np.ndarray, list[str]]:
    """Build the ``[n_decks, n_cards]`` count matrix (counts capped at *cap*).

    Card columns are the sorted union of all deck cards; rows are scaled by
    *weights* when given (the win-adjusted row weighting of View 1).
    """
    # Exclude non-deckbuildable tokens (curses / statuses / combat tokens /
    # quest cards) so they never become NMF archetype columns (see card_filter).
    cards = sorted({c for d in decks for c in d.cards} - NON_DECKBUILDABLE)
    idx = {c: i for i, c in enumerate(cards)}
    mat = np.zeros((len(decks), len(cards)), dtype=float)
    for r, d in enumerate(decks):
        counts: dict[str, int] = defaultdict(int)
        for c in d.cards:
            counts[c] += 1
        for c, k in counts.items():
            col = idx.get(c)
            if col is not None:  # skip non-deckbuildable tokens (not in vocab)
                mat[r, col] = min(k, cap)
    if weights is not None:
        mat = mat * np.asarray(weights, dtype=float)[:, None]
    return mat, cards


# ---------------------------------------------------------------------------
# View 1 — NMF + stability
# ---------------------------------------------------------------------------


def _top_set(comp: dict[str, float], top_k: int) -> frozenset[str]:
    return frozenset(sorted(comp, key=lambda c: -comp[c])[:top_k])


def _jaccard(a: frozenset[str], b: frozenset[str]) -> float:
    u = a | b
    return len(a & b) / len(u) if u else 0.0


def _fit_once(
    matrix: np.ndarray,
    card_names: list[str],
    n_components: int,
    top_k: int,
    random_state: int,
) -> list[dict[str, float]]:
    from sklearn.decomposition import NMF

    # Stochastic ("random") init — NOT nndsvda — so different restarts explore
    # different factorizations; that variation is exactly what the stability
    # filter measures (nndsvda is SVD-deterministic → every restart identical).
    model = NMF(n_components=n_components, init="random", random_state=random_state, max_iter=600)
    model.fit(matrix)
    comps: list[dict[str, float]] = []
    for row in model.components_:
        order = np.argsort(-row)[:top_k]
        peak = float(row[order[0]]) if row[order[0]] > 0 else 1.0
        comps.append({card_names[j]: float(row[j]) / peak for j in order})
    return comps


def nmf_archetypes(
    matrix: np.ndarray,
    card_names: list[str],
    n_components: int,
    *,
    top_k: int = 10,
    n_restarts: int = 10,
    stability_min: float = 0.6,
    seed: int = 0,
) -> list[dict[str, float]]:
    """NMF components surviving the restart stability filter (spec §5).

    Each returned component is a normalized ``{card: membership}`` over its
    top-*top_k* cards.  A reference component is kept when its top-*top_k* set
    reproduces (best-match Jaccard ≥ *stability_min*) in at least half of the
    other restarts.
    """
    if matrix.size == 0 or n_components < 1:
        return []
    ref = _fit_once(matrix, card_names, n_components, top_k, seed)
    others = [
        _fit_once(matrix, card_names, n_components, top_k, seed + r + 1)
        for r in range(n_restarts - 1)
    ]
    stable: list[dict[str, float]] = []
    for comp in ref:
        ref_top = _top_set(comp, top_k)
        agree = 0
        for other in others:
            best = max((_jaccard(ref_top, _top_set(o, top_k)) for o in other), default=0.0)
            if best >= stability_min:
                agree += 1
        if not others or agree >= len(others) / 2:
            stable.append(comp)
    return stable


# ---------------------------------------------------------------------------
# View 2 — positive causal graph + communities
# ---------------------------------------------------------------------------


def positive_causal_edges(
    edges: list[dict],
    *,
    signal_key: str = "combat_S",
    qvalue_max: float | None = None,
) -> list[tuple[Pair, float]]:
    """Filter catalog edges to significant positive-S pairs (View-2 graph input).

    Keeps edges whose *signal_key* is present and ``> 0`` and — when *qvalue_max*
    is set — whose §9b ``qvalue`` is present and ``≤ qvalue_max``.
    """
    out: list[tuple[Pair, float]] = []
    for e in edges:
        s = e.get(signal_key)
        if s is None or s <= 0:
            continue
        if qvalue_max is not None:
            q = e.get("qvalue")
            if q is None or q > qvalue_max:
                continue
        a, b = e["pair"]
        out.append((tuple(sorted((a, b))), float(s)))  # type: ignore[arg-type]
    return out


def graph_communities(
    pos_edges: list[tuple[Pair, float]],
    *,
    min_size: int = 2,
    resolution: float = 1.0,
    seed: int = 0,
) -> list[set[str]]:
    """Louvain communities over the weighted co-play graph (spec §5 View 2).

    *resolution* > 1 favours smaller, tighter communities.  At the default
    ``1.0`` a dense co-play graph collapses into a handful of giant blobs; the
    discovery driver raises it (≈2.0) so each community is a single recognizable
    archetype rather than half the card pool.
    """
    import networkx as nx
    from networkx.algorithms.community import louvain_communities

    g = nx.Graph()
    for (a, b), w in pos_edges:
        g.add_edge(a, b, weight=max(0.0, w))
    if g.number_of_edges() == 0:
        return []
    comms = louvain_communities(g, weight="weight", resolution=resolution, seed=seed)
    return [set(c) for c in comms if len(c) >= min_size]


# ---------------------------------------------------------------------------
# Descriptive labelling + combat validation (post-hoc; NOT clustering inputs)
# ---------------------------------------------------------------------------

# Mechanic → substrings scanned in a card's plain description (+ token for
# Strike).  These NAME an already-discovered cluster; they never steer the
# unsupervised clustering, so recovering "Exhaust" from the data is real signal,
# not a plant.
_MECHANIC_TERMS: list[tuple[str, tuple[str, ...]]] = [
    ("Exhaust", ("exhaust",)),
    ("Strength", ("strength",)),
    ("Vulnerable", ("vulnerable",)),
    ("Weak", ("weak",)),
    ("Block", ("block",)),
    ("Self-damage", ("lose", "hp")),  # "Lose N HP" / "lose HP on your turn"
]


def card_mechanics(token: str, description: str) -> set[str]:
    """Mechanic tags a single card exhibits (from its description + token).

    Purely descriptive: used only to name discovered clusters, never to form
    them.  ``Self-damage`` requires both "lose" and "hp" so plain "Lose Block"
    text does not trigger it.
    """
    text = (description or "").lower()
    tags: set[str] = set()
    for name, terms in _MECHANIC_TERMS:
        if all(t in text for t in terms):
            tags.add(name)
    if "STRIKE" in token.upper():
        tags.add("Strike")
    return tags


def label_cluster(
    cards: list[str],
    card_text: dict[str, dict],
    *,
    base_rates: dict[str, float] | None = None,
    min_frac: float = 0.34,
    min_lift: float = 1.3,
    max_labels: int = 2,
) -> str:
    """Human name for a discovered cluster by mechanic enrichment.

    For each mechanic, ``frac`` = share of member cards exhibiting it.  A
    mechanic labels the cluster when ``frac ≥ min_frac`` and (if *base_rates*
    given) its lift over the pool base-rate ``≥ min_lift``.  The top
    *max_labels* by lift·frac join with " · "; ``"Mixed"`` when none qualify.
    """
    if not cards:
        return "Mixed"
    counts: dict[str, int] = defaultdict(int)
    for tok in cards:
        entry = card_text.get(tok, {})
        for m in card_mechanics(tok, entry.get("description", "")):
            counts[m] += 1
    scored: list[tuple[float, float, str]] = []
    for m, c in counts.items():
        frac = c / len(cards)
        base = (base_rates or {}).get(m, 0.0)
        lift = frac / base if base > 0 else (frac / min_frac if base == 0 else 1.0)
        if frac >= min_frac and (base_rates is None or lift >= min_lift):
            scored.append((lift * frac, frac, m))
    if not scored:
        return "Mixed"
    scored.sort(reverse=True)
    return " · ".join(m for _, _, m in scored[:max_labels])


def mechanic_base_rates(card_text: dict[str, dict]) -> dict[str, float]:
    """Pool-wide share of cards exhibiting each mechanic (label lift denominator)."""
    if not card_text:
        return {}
    counts: dict[str, int] = defaultdict(int)
    for tok, entry in card_text.items():
        for m in card_mechanics(tok, entry.get("description", "")):
            counts[m] += 1
    n = len(card_text)
    return {m: c / n for m, c in counts.items()}


def combat_validation(cards: list[str], edges: list[dict]) -> dict[str, float]:
    """Aggregate a cluster's internal combat_S over catalog edges.

    Returns ``{mean_combat_S, pos_pair_frac, n_member_pairs}`` computed over
    catalog pairs whose *both* endpoints are in the cluster and carry a
    ``combat_S``.  This VALIDATES a discovered archetype (is it combat-strong?);
    it does not participate in discovery.
    """
    member = set(cards)
    vals: list[float] = []
    for e in edges:
        a, b = e["pair"]
        if a in member and b in member:
            s = e.get("combat_S")
            if s is not None:
                vals.append(float(s))
    if not vals:
        return {"mean_combat_S": 0.0, "pos_pair_frac": 0.0, "n_member_pairs": 0}
    pos = sum(1 for v in vals if v > 0)
    return {
        "mean_combat_S": sum(vals) / len(vals),
        "pos_pair_frac": pos / len(vals),
        "n_member_pairs": len(vals),
    }


# ---------------------------------------------------------------------------
# Merge + tag
# ---------------------------------------------------------------------------


@dataclass
class ArchetypeCandidate:
    """A discovered archetype candidate (spec §5).

    Attributes:
        cards: Member card tokens (top-k for NMF, all for a bare community).
        tag: ``confirmed-archetype`` / ``folklore`` / ``undiscovered``.
        jaccard: Best cross-view weighted-Jaccard overlap (0 for unmatched).
        source: ``nmf`` (human-played view) or ``graph`` (causal view).
    """

    cards: list[str]
    tag: str
    jaccard: float
    source: str
    label: str = "Mixed"
    mean_combat_S: float = 0.0
    pos_pair_frac: float = 0.0
    n_member_pairs: int = 0


def weighted_jaccard(a: dict[str, float], b: dict[str, float]) -> float:
    """Weighted-Jaccard overlap ``Σ min / Σ max`` over the union of keys."""
    keys = set(a) | set(b)
    num = sum(min(a.get(k, 0.0), b.get(k, 0.0)) for k in keys)
    den = sum(max(a.get(k, 0.0), b.get(k, 0.0)) for k in keys)
    return num / den if den > 0 else 0.0


def merge_and_tag(
    nmf_components: list[dict[str, float]],
    communities: list[set[str]],
    *,
    jaccard_min: float = 0.3,
) -> list[ArchetypeCandidate]:
    """Merge the two unsupervised views into tagged candidates (spec §5).

    The **co-play communities are the archetypes** (they cluster one recognizable
    mechanic each); the NMF human-play factorization *corroborates* them.  Each
    community's ``jaccard`` is its best top-set overlap with any NMF component:

    * overlap ≥ *jaccard_min* → ``confirmed-archetype`` (both views agree),
    * else → ``undiscovered`` (co-play cluster the NMF factorization did not
      isolate — emergent / niche).

    An NMF component that echoes no community (best overlap < *jaccard_min*) is a
    distinct human-play grouping and is emitted as ``folklore``.
    """
    nmf_sets = [frozenset(comp) for comp in nmf_components]
    out: list[ArchetypeCandidate] = []
    matched: set[int] = set()

    for comm in communities:
        cset = frozenset(comm)
        best_j, best_i = 0.0, -1
        for i, s in enumerate(nmf_sets):
            j = _jaccard(cset, s)
            if j > best_j:
                best_j, best_i = j, i
        if best_i >= 0 and best_j >= jaccard_min:
            matched.add(best_i)
            out.append(ArchetypeCandidate(sorted(comm), "confirmed-archetype", best_j, "graph"))
        else:
            out.append(ArchetypeCandidate(sorted(comm), "undiscovered", best_j, "graph"))

    for i, comp in enumerate(nmf_components):
        if i in matched:
            continue
        cards = sorted(comp, key=lambda c: -comp[c])
        best = max((_jaccard(nmf_sets[i], frozenset(c)) for c in communities), default=0.0)
        if best < jaccard_min:
            out.append(ArchetypeCandidate(cards, "folklore", best, "nmf"))
    return out


def discover_archetypes(
    decks: list[Deck],
    catalog_edges: list[dict],
    *,
    n_components: int,
    top_k: int = 10,
    signal_key: str = "pmi",
    qvalue_max: float | None = None,
    jaccard_min: float = 0.3,
    resolution: float = 2.0,
    n_restarts: int = 10,
    stability_min: float = 0.6,
    card_text: dict[str, dict] | None = None,
    seed: int = 0,
) -> list[ArchetypeCandidate]:
    """Run both discovery views over a corpus + catalog and merge/tag (spec §5).

    Both views are unsupervised: View 1 (NMF) factorizes human co-play, View 2
    (Louvain) clusters the co-play graph — driven by ``signal_key`` (default
    ``pmi``, the observational co-play strength, NOT the noisy per-pair
    ``combat_S``).  Each merged candidate is then *labelled* by mechanic
    enrichment and *validated* by its internal combat_S — both post-hoc, so the
    clusters are found from data, then named and scored, never seeded from a
    hand-written archetype list.
    """
    weights = regression_adjusted_weights(decks)
    matrix, card_names = deck_card_matrix(decks, weights=weights)
    components = nmf_archetypes(
        matrix,
        card_names,
        n_components,
        top_k=top_k,
        n_restarts=n_restarts,
        stability_min=stability_min,
        seed=seed,
    )
    pos = positive_causal_edges(catalog_edges, signal_key=signal_key, qvalue_max=qvalue_max)
    communities = graph_communities(pos, resolution=resolution, seed=seed)
    candidates = merge_and_tag(components, communities, jaccard_min=jaccard_min)

    base = mechanic_base_rates(card_text or {})
    for c in candidates:
        if card_text:
            c.label = label_cluster(c.cards, card_text, base_rates=base)
        val = combat_validation(c.cards, catalog_edges)
        c.mean_combat_S = round(val["mean_combat_S"], 4)
        c.pos_pair_frac = round(val["pos_pair_frac"], 4)
        c.n_member_pairs = int(val["n_member_pairs"])
    return candidates
