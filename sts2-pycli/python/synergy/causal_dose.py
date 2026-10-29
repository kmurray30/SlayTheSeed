"""Combat dose-response panels — spec §7 (Stage 2 confirm).

Upgrades the combat probe from the 4-cell pair second difference to an
archetype dose-response: ``V(k)`` for ``k = 0..K`` weight-sampled archetype
cards, a convexity gap vs the additive (solo first-difference) baseline, sampled
embedding-matched control arms, and a permutation null of random
strength-matched packages → an empirical-percentile verdict.

The Rust ``synergy_fight`` binary already accepts an arbitrary ``inject`` list,
so a dose point is just ``run_fight(base, package, …)``; every fight-driving
function here takes an injectable ``fight_fn`` so the aggregation/convexity/null
logic is unit-testable on synthetic outcomes.
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass

import numpy as np

from synergy.causal_combat import _scalar

# A fight runner: (baseline_deck, inject, encounter_id, seed) -> outcome dict.
FightFn = Callable[[list[str], list[str], str, int], dict]


# ---------------------------------------------------------------------------
# Pure primitives
# ---------------------------------------------------------------------------


def weighted_sample_package(weights: dict[str, float], k: int, rng) -> list[str]:
    """Sample *k* distinct cards without replacement ∝ *weights*."""
    cards = list(weights)
    if k >= len(cards):
        return cards
    w = np.array([max(0.0, weights[c]) for c in cards], dtype=float)
    total = w.sum()
    p = (w / total) if total > 0 else None  # uniform when all-zero
    idx = rng.choice(len(cards), size=k, replace=False, p=p)
    return [cards[i] for i in idx]


def additive_prediction(v0: float, solo: dict[str, float], package: list[str]) -> float:
    """Additive baseline ``v0 + Σ solo[c]`` over the package's cards."""
    return v0 + sum(solo.get(c, 0.0) for c in package)


def convexity_gap(v_measured: float, v_additive: float) -> float:
    """Measured dose value minus its additive prediction (positive → super-additive)."""
    return v_measured - v_additive


def permutation_percentile(observed: float, null: list[float]) -> float:
    """Tie-aware midrank percentile of *observed* in the *null* distribution.

    ``(#{null < observed} + ½·#{null == observed}) / len(null)`` — an exactly
    tied observation lands at the middle of the null ("not distinguishable"),
    so a deterministic additive archetype scores ~0.5 rather than a spurious 1.0.
    High → the observed gap is convex beyond the null of equally-strong packages.
    """
    if not null:
        return 0.0
    arr = np.asarray(null, dtype=float)
    below = float(np.count_nonzero(arr < observed))
    ties = float(np.count_nonzero(arr == observed))
    return (below + 0.5 * ties) / len(arr)


def cuped_adjust(outcomes: list[float], covariate: list[float]) -> list[float]:
    """CUPED residuals ``y − β(x − x̄)`` with ``β = cov(x,y)/var(x)`` (§7).

    Same expectation as *outcomes* (unbiased), lower variance when *covariate*
    (e.g. the Stage-1 PPO value-head prediction) correlates with the outcome.
    Identity when the covariate has zero variance.
    """
    y = np.asarray(outcomes, dtype=float)
    x = np.asarray(covariate, dtype=float)
    vx = float(np.var(x))
    if vx < 1e-12:
        return list(y)
    beta = float(np.cov(x, y, bias=True)[0, 1] / vx)
    return list(y - beta * (x - x.mean()))


# ---------------------------------------------------------------------------
# Fight-driven measurement
# ---------------------------------------------------------------------------


def dose_point(
    fight_fn: FightFn,
    baseline_decks: list[list[str]],
    package: list[str],
    encounters: list[str],
    seeds: list[int],
) -> list[float]:
    """Per-panel-cell outcome scalar for ``baseline ++ package`` (seed-paired)."""
    out: list[float] = []
    for base in baseline_decks:
        for enc in encounters:
            for seed in seeds:
                out.append(_scalar(fight_fn(base, list(package), enc, seed)))
    return out


def solo_deltas(
    fight_fn: FightFn,
    baseline_decks: list[list[str]],
    cards: list[str],
    encounters: list[str],
    seeds: list[int],
) -> tuple[float, dict[str, float]]:
    """Mean base scalar ``v0`` and each card's solo first-difference over the panel."""
    base_pts = dose_point(fight_fn, baseline_decks, [], encounters, seeds)
    v0 = float(np.mean(base_pts)) if base_pts else 0.0
    solo: dict[str, float] = {}
    for c in cards:
        pts = dose_point(fight_fn, baseline_decks, [c], encounters, seeds)
        solo[c] = (float(np.mean(pts)) - v0) if pts else 0.0
    return v0, solo


# ---------------------------------------------------------------------------
# Assembly + verdict
# ---------------------------------------------------------------------------


@dataclass
class DoseResult:
    """Archetype dose-response verdict (spec §7).

    Attributes:
        v_curve: Measured mean dose value ``V(k)`` for ``k = 0..K``.
        additive_curve: Additive (solo-delta) prediction for each ``k``.
        convexity_gap: ``V(target_k) − additive(target_k)`` (positive → super-additive).
        control_mean_gap: Mean convexity gap of the random strength-matched null.
        null_percentile: Percentile of the archetype gap vs the permutation null
            (primary verdict; high → convex beyond random equally-strong packages).
        target_k: The dose level the verdict is read at.
        n_null: Number of null control packages drawn.
    """

    v_curve: list[float]
    additive_curve: list[float]
    convexity_gap: float
    control_mean_gap: float
    null_percentile: float
    target_k: int
    n_null: int


def _package_gap(
    fight_fn: FightFn,
    baseline_decks: list[list[str]],
    package: list[str],
    encounters: list[str],
    seeds: list[int],
    v0: float,
    solo: dict[str, float],
) -> float:
    """Convexity gap of one package: measured mean V minus its additive prediction."""
    pts = dose_point(fight_fn, baseline_decks, package, encounters, seeds)
    v_meas = float(np.mean(pts)) if pts else v0
    return convexity_gap(v_meas, additive_prediction(v0, solo, package))


def _strength_weights(pool: list[str], solo: dict[str, float], target: float) -> dict[str, float]:
    """Weight pool cards by closeness of their solo delta to *target* (strength-match)."""
    return {c: 1.0 / (1.0 + abs(solo.get(c, 0.0) - target)) for c in pool}


def dose_response(
    fight_fn: FightFn,
    archetype_weights: dict[str, float],
    control_pool: list[str],
    baseline_decks: list[list[str]],
    encounters: list[str],
    seeds: list[int],
    *,
    K: int = 5,
    target_k: int = 3,
    n_draws: int = 4,
    n_null: int = 50,
    seed: int = 0,
) -> DoseResult:
    """Measure an archetype dose curve + convexity gap vs a permutation null (§7).

    Args:
        fight_fn: Fight runner (``run_fight`` in production; a mock in tests).
        archetype_weights: ``{card: membership}`` for the archetype (weight-sampled).
        control_pool: Candidate control cards (embedding-matched, near-but-outside
            the archetype cluster; see :func:`synergy.embed_geometry.control_match`).
        baseline_decks, encounters, seeds: The forced-deck fight panel (seed-paired).
        K: Max dose level for the curve.
        target_k: Dose level the verdict is read at.
        n_draws: Weight-sampled packages averaged per dose level.
        n_null: Random strength-matched control packages for the null.
        seed: RNG seed.

    Returns:
        A :class:`DoseResult`.
    """
    rng = np.random.default_rng(seed)
    arch_cards = list(archetype_weights)
    all_cards = list(dict.fromkeys(arch_cards + control_pool))
    v0, solo = solo_deltas(fight_fn, baseline_decks, all_cards, encounters, seeds)

    # Archetype dose curve V(k), k = 0..K (fresh weight-sampled package per draw).
    v_curve: list[float] = []
    additive_curve: list[float] = []
    for k in range(K + 1):
        if k == 0:
            v_curve.append(v0)
            additive_curve.append(v0)
            continue
        meas, add = [], []
        for _ in range(n_draws):
            pkg = weighted_sample_package(archetype_weights, k, rng)
            pts = dose_point(fight_fn, baseline_decks, pkg, encounters, seeds)
            meas.append(float(np.mean(pts)) if pts else v0)
            add.append(additive_prediction(v0, solo, pkg))
        v_curve.append(float(np.mean(meas)))
        additive_curve.append(float(np.mean(add)))

    tk = min(target_k, K)
    arch_gap = convexity_gap(v_curve[tk], additive_curve[tk])

    # Permutation null: random strength-matched control packages of size target_k.
    arch_mean_solo = float(np.mean([solo.get(c, 0.0) for c in arch_cards])) if arch_cards else 0.0
    strength_w = _strength_weights(control_pool, solo, arch_mean_solo)
    null_gaps: list[float] = []
    if control_pool:
        for _ in range(n_null):
            pkg = weighted_sample_package(strength_w, tk, rng)
            null_gaps.append(
                _package_gap(fight_fn, baseline_decks, pkg, encounters, seeds, v0, solo)
            )

    return DoseResult(
        v_curve=v_curve,
        additive_curve=additive_curve,
        convexity_gap=arch_gap,
        control_mean_gap=float(np.mean(null_gaps)) if null_gaps else 0.0,
        null_percentile=permutation_percentile(arch_gap, null_gaps),
        target_k=tk,
        n_null=len(null_gaps),
    )


def dose_archetype(
    fight_fn: FightFn,
    cards: list[str],
    emb: dict[str, np.ndarray],
    clusters: dict[str, int],
    baseline_decks: list[list[str]],
    encounters: list[str],
    seeds: list[int],
    *,
    control_n: int = 12,
    weights: dict[str, float] | None = None,
    **kw,
) -> DoseResult:
    """Run :func:`dose_response` for a discovered archetype (spec §5/§7 glue).

    Derives the control pool via embedding proximity (near the archetype's cards
    but outside its cluster) using :func:`synergy.embed_geometry.control_match`,
    then measures the dose curve.  *weights* defaults to uniform over *cards*
    (candidate memberships are not always retained through the merge step).
    """
    from synergy.embed_geometry import control_match

    control_pool = control_match(cards, emb, clusters, control_n)
    arch_weights = weights or dict.fromkeys(cards, 1.0)
    return dose_response(
        fight_fn, arch_weights, control_pool, baseline_decks, encounters, seeds, **kw
    )
