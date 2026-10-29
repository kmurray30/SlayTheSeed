"""Held-out prediction gate — the project-level exam (spec §14).

The catalog only earns trust if its *confirmed* synergies predict winning on
**held-out** runs better than the raw baseline.  This module fits a win model
using a synergy feature (count of confirmed pairs the deck holds) on a training
split, evaluates held-out AUC on a test split, and reports the lift over a
size-only baseline.  It reports the number; it does not silently pass.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from synergy.corpus import Deck, deck_size
from synergy.observational import Pair


@dataclass
class GateResult:
    """Held-out prediction-gate verdict (spec §14).

    Attributes:
        synergy_auc: Held-out ROC-AUC of ``won ~ deck_size + synergy_count``.
        baseline_auc: Held-out ROC-AUC of ``won ~ deck_size`` only.
        lift: ``synergy_auc − baseline_auc`` — positive → confirmed synergies
            carry real held-out predictive signal beyond deck size.
        n_train: Training decks used.
        n_test: Test decks scored.
    """

    synergy_auc: float
    baseline_auc: float
    lift: float
    n_train: int
    n_test: int


def _synergy_count(deck: Deck, pairs: list[Pair]) -> int:
    """Number of *pairs* both of whose cards the deck holds."""
    held = set(deck.cards)
    return sum(1 for a, b in pairs if a in held and b in held)


def _features(decks: list[Deck], pairs: list[Pair]) -> tuple[np.ndarray, np.ndarray]:
    """Return ``(X[:, [deck_size, synergy_count]], y=won)``."""
    x = np.array([[float(deck_size(d)), float(_synergy_count(d, pairs))] for d in decks])
    y = np.array([1 if d.won else 0 for d in decks])
    return x, y


def prediction_gate(
    train: list[Deck],
    test: list[Deck],
    confirmed_pairs: list[Pair],
    *,
    seed: int = 0,
) -> GateResult:
    """Fit on *train*, score held-out *test*: does synergy beat a size baseline?

    Args:
        train: Training decks (fit both models here).
        test: Held-out decks (AUC evaluated here).
        confirmed_pairs: The catalog's confirmed synergy pairs (the feature).
        seed: Unused placeholder for a stable signature (LogisticRegression is
            deterministic here); kept for parity with the other estimators.

    Returns:
        A :class:`GateResult`.  Degenerate splits (single-class train/test or
        empty test) yield AUCs of 0.5 and a lift of 0.0.
    """
    from sklearn.linear_model import LogisticRegression
    from sklearn.metrics import roc_auc_score

    _ = seed
    x_tr, y_tr = _features(train, confirmed_pairs)
    x_te, y_te = _features(test, confirmed_pairs)
    n_train, n_test = len(train), len(test)

    if n_test == 0 or np.unique(y_tr).size < 2 or np.unique(y_te).size < 2:
        return GateResult(0.5, 0.5, 0.0, n_train, n_test)

    def _auc(cols: list[int]) -> float:
        clf = LogisticRegression(solver="lbfgs", max_iter=1000)
        clf.fit(x_tr[:, cols], y_tr)
        return float(roc_auc_score(y_te, clf.predict_proba(x_te[:, cols])[:, 1]))

    synergy_auc = _auc([0, 1])  # deck_size + synergy_count
    baseline_auc = _auc([0])  # deck_size only
    return GateResult(
        synergy_auc=synergy_auc,
        baseline_auc=baseline_auc,
        lift=synergy_auc - baseline_auc,
        n_train=n_train,
        n_test=n_test,
    )
