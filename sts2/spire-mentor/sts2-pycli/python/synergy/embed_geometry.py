"""PPO card-embedding geometry — spec §5 View 3 + §7 control matcher.

Reads the deployed champion's per-character card-token embedding table straight
from the committed ONNX (``net.emb_card.weight``), builds the L2-normalized
cosine-kNN graph, Louvain-clusters it into the agent's implicit deck taxonomy,
and matches control cards by embedding proximity (near an archetype's cards but
outside its cluster).  Provenance-grade: proximity encodes role/substitutability,
not complementarity — this view never gates a quadrant on its own (spec §5).
"""

from __future__ import annotations

import os
from pathlib import Path

import champions as champion_manifest
import numpy as np
from workbench.ppo_policy import onnx_path

from synergy.card_filter import NON_DECKBUILDABLE

_CARD_EMB_INITIALIZER = "net.emb_card.weight"


# ---------------------------------------------------------------------------
# Champion table IO
# ---------------------------------------------------------------------------


def champion_onnx_path(character: str) -> Path:
    """Resolve the champion ONNX whose embedding table this view reads.

    Delegates to :func:`workbench.ppo_policy.onnx_path`, i.e. to
    ``data/ppo/champions.toml`` — the one manifest every serving lane resolves
    from. This module used to keep its own hardcoded ``{character: filename}``
    table, pinned by nothing, and it silently rotted: by 2026-08-08 it still
    named Ironclad ``asc_it55`` and Defect ``asc_it80`` (retired in #340 and by
    the Defect ascension leg) and had no Regent entry at all, so this view had
    been reading a stale card-embedding table for three of five characters
    through several promotions. Delegating is the fix that cannot rot: a table
    that does not exist cannot drift.

    ``STS2_RUNAI_PPO`` overrides for every character, checked first so the
    engine-wide override keeps working here; ``STS2_WB_PPO_ONNX`` also applies
    via the delegate.

    Raises:
        ValueError: *character* has no entry in the manifest.
        FileNotFoundError: the manifest names a net that is not on disk — a
            distinct failure from "no champion exists", and the one you get in
            a checkout whose ``data/ppo`` is incomplete. It is raised by the
            resolver rather than degrading to an older net, deliberately.
    """
    override = os.environ.get("STS2_RUNAI_PPO")
    if override:
        return Path(override)
    if champion_manifest.get(character) is None:
        raise ValueError(f"no PPO champion for character {character!r}")
    resolved = onnx_path(character)
    assert resolved is not None  # manifest entry exists, so this cannot be None
    return Path(resolved)


def load_card_embeddings(onnx_path: Path) -> dict[str, np.ndarray]:
    """Read the card-token embedding table, mapping card name → 192-d row.

    Rows ``0..len(names)-1`` map to ``ppo.vocab.cards().names[i]``; the trailing
    OOV/pad rows are dropped.

    Raises:
        FileNotFoundError: *onnx_path* does not exist.
        KeyError: the expected embedding initializer is absent.
    """
    if not onnx_path.exists():
        raise FileNotFoundError(f"champion ONNX not found: {onnx_path}")
    import onnx
    import ppo.vocab as vocab
    from onnx import numpy_helper

    model = onnx.load(str(onnx_path))
    table: np.ndarray | None = None
    for init in model.graph.initializer:
        if init.name == _CARD_EMB_INITIALIZER:
            table = numpy_helper.to_array(init)
            break
    if table is None:
        raise KeyError(f"{_CARD_EMB_INITIALIZER} not found in {onnx_path.name}")

    names = vocab.cards().names
    return {name: np.asarray(table[i], dtype=float) for i, name in enumerate(names)}


# ---------------------------------------------------------------------------
# Geometry primitives
# ---------------------------------------------------------------------------


def l2_normalize(emb: dict[str, np.ndarray]) -> dict[str, np.ndarray]:
    """Return unit-norm copies of each vector (a zero vector stays zero)."""
    out: dict[str, np.ndarray] = {}
    for name, v in emb.items():
        n = float(np.linalg.norm(v))
        out[name] = v / n if n > 1e-12 else v.copy()
    return out


def frequency_gate(
    emb: dict[str, np.ndarray],
    card_frequency: dict[str, float],
    min_freq: float,
) -> dict[str, np.ndarray]:
    """Drop cards whose corpus frequency is below *min_freq* (or unknown).

    Undertrained embedding rows (rare cards the agent saw few times) are noise
    (spec §5), so they are excluded from the geometry.  Non-deckbuildable tokens
    (curses / statuses / combat tokens / quest cards) are dropped unconditionally
    — they are not deckbuilding choices (see :mod:`synergy.card_filter`).
    """
    return {
        c: v
        for c, v in emb.items()
        if c not in NON_DECKBUILDABLE and card_frequency.get(c, 0.0) >= min_freq
    }


def cosine_knn_graph(
    emb: dict[str, np.ndarray],
    k: int,
) -> dict[str, list[tuple[str, float]]]:
    """Each card's *k* nearest others by cosine similarity (descending)."""
    names = list(emb)
    if len(names) <= 1:
        return {n: [] for n in names}
    mat = np.stack([emb[n] for n in names])
    norms = np.linalg.norm(mat, axis=1, keepdims=True)
    unit = mat / np.where(norms > 1e-12, norms, 1.0)
    sims = unit @ unit.T
    np.fill_diagonal(sims, -np.inf)
    out: dict[str, list[tuple[str, float]]] = {}
    kk = min(k, len(names) - 1)
    for i, name in enumerate(names):
        order = np.argsort(-sims[i])[:kk]
        out[name] = [(names[j], float(sims[i, j])) for j in order]
    return out


def louvain_clusters(
    knn: dict[str, list[tuple[str, float]]],
    *,
    seed: int = 0,
) -> dict[str, int]:
    """Louvain community id per card over the cosine-weighted kNN graph.

    Edges are undirected with the cosine similarity as weight (negatives clamped
    to 0 so Louvain sees non-negative weights).
    """
    import networkx as nx
    from networkx.algorithms.community import louvain_communities

    g = nx.Graph()
    g.add_nodes_from(knn)
    for src, neighbors in knn.items():
        for dst, sim in neighbors:
            w = max(0.0, sim)
            if g.has_edge(src, dst):
                g[src][dst]["weight"] = max(g[src][dst]["weight"], w)
            else:
                g.add_edge(src, dst, weight=w)
    communities = louvain_communities(g, weight="weight", seed=seed)
    return {node: cid for cid, comm in enumerate(communities) for node in comm}


# ---------------------------------------------------------------------------
# Control matcher (spec §7)
# ---------------------------------------------------------------------------


def control_match(
    archetype_cards: list[str],
    emb: dict[str, np.ndarray],
    clusters: dict[str, int],
    n: int,
    *,
    card_type: dict[str, str] | None = None,
) -> list[str]:
    """Match *n* control cards near an archetype but outside its cluster (§7).

    Controls are the cards nearest the archetype centroid by cosine that are not
    archetype members and lie outside every cluster the archetype occupies —
    "similar role and power, but a different engine".  With *card_type* a control
    must also share a type with at least one archetype card (rarity/type control).
    """
    members = set(archetype_cards)
    present = [c for c in archetype_cards if c in emb]
    if not present:
        return []
    centroid = np.mean([emb[c] for c in present], axis=0)
    cn = float(np.linalg.norm(centroid))
    centroid = centroid / cn if cn > 1e-12 else centroid

    arch_clusters = {clusters.get(c) for c in present}
    arch_types = {card_type[c] for c in present if card_type and c in card_type}

    scored: list[tuple[float, str]] = []
    for c, v in emb.items():
        if c in members or clusters.get(c) in arch_clusters:
            continue
        if card_type is not None and card_type.get(c) not in arch_types:
            continue
        vn = float(np.linalg.norm(v))
        sim = float(centroid @ v / vn) if vn > 1e-12 else -1.0
        scored.append((sim, c))
    scored.sort(key=lambda t: (-t[0], t[1]))
    return [c for _, c in scored[:n]]


# ---------------------------------------------------------------------------
# Assembly — card_embed_geometry_<char>.json
# ---------------------------------------------------------------------------


def _neighbor_jaccard(
    ppo_knn: dict[str, list[tuple[str, float]]],
    fm_knn: dict[str, list[tuple[str, float]]],
) -> dict[str, float | None]:
    """Per-card Jaccard of PPO vs FM kNN neighbor sets (FM = corpus counterpart).

    Records View-3/FM agreement as provenance (spec §5): a card whose learned
    neighbours match its corpus-embedding neighbours is a robust taxonomy anchor.
    ``None`` when the card is absent from the FM embedding.
    """
    out: dict[str, float | None] = {}
    for card, ppo_n in ppo_knn.items():
        fm_n = fm_knn.get(card)
        if fm_n is None:
            out[card] = None
            continue
        a = {n for n, _ in ppo_n}
        b = {n for n, _ in fm_n}
        union = a | b
        out[card] = (len(a & b) / len(union)) if union else 0.0
    return out


def build_geometry(
    character: str,
    card_frequency: dict[str, float],
    *,
    k: int = 15,
    min_freq: float = 0.01,
    seed: int = 0,
    fm_embeddings: dict[str, np.ndarray] | None = None,
) -> dict:
    """Assemble the ``card_embed_geometry_<char>`` document (spec §5 View 3).

    Loads the champion card table, frequency-gates and normalizes it, builds the
    cosine-kNN graph and Louvain taxonomy, and — when *fm_embeddings* is
    supplied — records per-card PPO/FM neighbor agreement as provenance.

    Args:
        character: Character whose champion to read.
        card_frequency: Corpus card frequencies (fraction of decks) for gating.
        k: kNN neighbours per card.
        min_freq: Frequency floor for the gate.
        seed: Louvain seed.
        fm_embeddings: Optional §4 factorization-machine card embeddings (same
            card tokens) — merged as a corpus-trained counterpart.

    Returns:
        ``{character, n_cards, k, clusters, knn, fm_agreement}`` — a JSON-ready
        dict (``knn`` values are ``[[neighbor, sim], ...]`` lists).
    """
    raw = load_card_embeddings(champion_onnx_path(character))
    gated = frequency_gate(raw, card_frequency, min_freq)
    unit = l2_normalize(gated)
    knn = cosine_knn_graph(unit, k)
    clusters = louvain_clusters(knn, seed=seed)

    fm_agreement: dict[str, float | None]
    if fm_embeddings is not None:
        shared = {c: fm_embeddings[c] for c in unit if c in fm_embeddings}
        fm_knn = cosine_knn_graph(l2_normalize(shared), k)
        fm_agreement = _neighbor_jaccard(knn, fm_knn)
    else:
        fm_agreement = dict.fromkeys(unit)

    return {
        "character": character,
        "n_cards": len(unit),
        "k": k,
        "clusters": clusters,
        "knn": {c: [[n, s] for n, s in nbrs] for c, nbrs in knn.items()},
        "fm_agreement": fm_agreement,
    }
