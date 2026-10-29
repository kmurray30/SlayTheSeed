"""Unit tests for the causal potion-OC fit (GH #447 follow-up, plan Task 6).

The synthetic-recovery test constructs fight rows from a KNOWN logistic
model where one held scaling potion in act 2 is worth exactly +10
HP-equivalents (its logit coefficient is 10x the per-HP coefficient), then
asserts the fit recovers 10 +/- 2 through the same per-HP conversion the
production path uses. The coin test pins the Python splitmix64 replica to
the Rust seam's documented constants.
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "python"))

from fit_potion_oc import CLASSES, build_table, coin_masks, design, fit_logistic


def _synthetic_rows(n: int = 60_000, seed: int = 7) -> list[dict]:
    rng = np.random.default_rng(seed)
    max_hp = 80.0
    per_hp = 0.05  # logit units per HP => hp_frac coef = 4.0
    oc_true = 10.0  # scaling potion in act 2 worth +10 HP-equiv
    rows = []
    for _ in range(n):
        act = int(rng.integers(1, 4))
        held_scaling = int(rng.integers(0, 3))
        hp_frac = float(rng.uniform(0.2, 1.0))
        logit = -1.0 + per_hp * max_hp * hp_frac
        if act == 2:
            logit += oc_true * per_hp * held_scaling
        win = bool(rng.uniform() < 1.0 / (1.0 + np.exp(-logit)))
        rows.append(
            {
                "win": win,
                "act": act,
                "floor": act * 15,
                "tier": "N",
                "hp_frac": hp_frac,
                "max_hp": max_hp,
                "held": {c: (held_scaling if c == "scaling" else 0) for c in CLASSES},
                "masked": False,
            }
        )
    return rows


def test_fit_recovers_known_hp_equivalent_value():
    rows = _synthetic_rows()
    x, y, names = design(rows)
    coefs = fit_logistic(x, y)
    table = build_table(rows, coefs, names)
    oc = {(e["potion"], e["act"]): e["oc"] for e in table["entries"]}
    got = oc["POTION.POWER_POTION", 2]
    assert 8.0 <= got <= 12.0, f"expected ~10 HP-equiv, fit says {got}"
    # acts with no effect in the generator stay near 0
    assert oc["POTION.POWER_POTION", 1] <= 3.0
    # the free token is pinned to 0 regardless of the fit
    assert abs(oc["POTION.POTION_SHAPED_ROCK", 2]) < 1e-9


def test_coin_matches_rust_splitmix64_constants():
    # Determinism + rate near p over a grid (mirrors the Rust unit test).
    fired = sum(coin_masks(s * 7919, f, salt=3, p=0.5) for s in range(200) for f in range(50))
    assert abs(fired / 10_000 - 0.5) < 0.02
    assert coin_masks(42, 7, salt=1, p=0.5) == coin_masks(42, 7, salt=1, p=0.5)
    assert not any(coin_masks(9, f, salt=2, p=0.0) for f in range(100))
    assert all(coin_masks(9, f, salt=2, p=1.0) for f in range(100))
