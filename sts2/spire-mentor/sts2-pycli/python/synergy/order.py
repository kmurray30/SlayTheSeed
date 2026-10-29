"""Hierarchical clustering seriation for card-synergy catalog.

Computes a 1-D card ordering by hierarchical clustering with optimal leaf
ordering — emergent (not hand-labeled).  Cards that co-synergise cluster
together, producing contiguous diagonal blocks in the heatmap.

Usage::

    from synergy.order import compute_card_order

    # obs_pairs: list of {"pair": [a, b], "pmi": float}
    # signal_ids: set of card tokens that have ≥1 edge (no zero-PMI-profile)
    card_order = compute_card_order(obs_pairs, signal_ids=signal_ids)

Algorithm
---------
1. Build the card×card PMI matrix (from :func:`synergy.embed.build_pmi_matrix`).
2. Restrict to ``signal_ids`` — cards whose entire PMI profile is non-zero —
   so the ~350 zero-profile cards don't all blob together.
3. Convert to a cosine-distance matrix.
4. Run ``scipy.cluster.hierarchy.linkage(..., method="average")``.
5. Apply ``optimal_leaf_ordering(Z, dist_condensed)`` for the best
   block-contiguous permutation.
6. Return the ordered list of card tokens.
"""

from __future__ import annotations

import numpy as np
from scipy.cluster.hierarchy import leaves_list, linkage, optimal_leaf_ordering
from scipy.spatial.distance import pdist


def compute_card_order(
    obs_pairs: list[dict],
    *,
    signal_ids: set[str] | None = None,
) -> list[str]:
    """Compute a clustering-seriated 1-D card order from PMI profile similarity.

    Args:
        obs_pairs: The ``pairs`` list from an obs_catalog dict.  Each entry must
            have ``pair`` ([a, b]) and ``pmi`` (float).
        signal_ids: If provided, restrict the matrix to these tokens (drop
            zero-profile cards).  If ``None``, all tokens in ``obs_pairs`` are
            used.

    Returns:
        Ordered list of card tokens where synergistic cards are adjacent.
        If fewer than 2 signal cards exist, returns the sorted list as-is.
    """
    from synergy.embed import build_pmi_matrix

    tok2idx, pmi_mat = build_pmi_matrix(obs_pairs)

    # Build the subset of tokens we care about
    all_tokens = sorted(tok2idx.keys())

    if signal_ids is not None:
        tokens = [t for t in all_tokens if t in signal_ids]
    else:
        tokens = all_tokens

    if len(tokens) < 2:
        return tokens

    # Extract the sub-matrix (rows = signal cards, cols = all cards in dense mat)
    indices = [tok2idx[t] for t in tokens]
    sub_mat = pmi_mat[np.ix_(indices, list(range(pmi_mat.shape[1])))]

    # Filter tokens that are all-zero in this sub-matrix (no signal at all)
    row_norms = np.linalg.norm(sub_mat, axis=1)
    nonzero_mask = row_norms > 0.0
    if not np.any(nonzero_mask):
        return tokens  # all zero — just return sorted

    tokens_nz = [tokens[i] for i in range(len(tokens)) if nonzero_mask[i]]
    if len(tokens_nz) < 2:
        return tokens_nz

    sub_mat_nz = sub_mat[nonzero_mask]

    # Condensed cosine distance
    dist_cond = pdist(sub_mat_nz, metric="cosine")

    # Hierarchical clustering + optimal leaf ordering
    Z = linkage(dist_cond, method="average")
    Z_opt = optimal_leaf_ordering(Z, dist_cond)
    order_indices = leaves_list(Z_opt)

    return [tokens_nz[i] for i in order_indices]
