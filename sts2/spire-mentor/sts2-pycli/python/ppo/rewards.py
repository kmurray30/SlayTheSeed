"""Graded base reward + potential-based shaping (plan §3.3, fork recipe).

Per-step reward = Φ(s') − Φ(s) with Φ(s) = base(s) + shape(s):

- ``base(s)`` — graded progress, strictly monotone in true progress:
  ``0.5 · floors_completed / total_floors(s) + 0.5 · win``.
  Defined for every state including terminals; non-win terminals score
  base only, so a deeper death strictly out-scores a shallower one.
  ``total_floors`` is a per-ascension NORMALIZER, not a cap — run length
  varies (A10's second boss, the Golden Compass relic's longer act map),
  and clipping it flattened the reward exactly at the end of the deepest
  runs. See ``base_value``.
- ``shape(s)`` — PBRS terms on NON-terminal states only (terminal shaping
  un-credited): ``hp_coef·hp_frac + upg_coef·num_upgraded +
  relic_coef·num_relics − offset``. With γ=1 the un-credited terminal makes
  the total return telescope to ``base(terminal) − base(s0) − (shape(s0) −
  offset·0)`` … i.e. base-return ± a per-run constant, so the optimal
  policy is unchanged (Ng, Harada & Russell 1999; fork
  ``compute_shaped_rewards``). ``test_ppo_trainer_units.py`` asserts the
  telescoping identity exactly.

Default coefficients are the fork's STS1 priors (upg 0.035 / offset 0.307,
hp term on); the plan requires refitting them by floor-controlled OLS on
our own episode dumps before the main run (M-P5 prep).

**Before spending effort on the coefficients, run ``ppo.shaping_gate``**
(GH #464). The invariance above is stronger than the docstring's citation
implies: for any critic of the form ``V(s) = m(s) - Phi(s)`` — the form the
fitted critic converges to — every TD residual reduces to ``m(s') - m(s)``,
so ``A_lam`` is independent of these coefficients at EVERY lambda, not just
lambda=1. The gradient estimator, not merely the optimum, is untouched.
Shaping can therefore act only through critic misfit, and the gate measures
how much room that leaves before anyone spends a screen arm on it.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

# Three acts of 15/14/13 rooms → 16+15+14 = 45 counted floors. Verified
# character-independent: `ActConfig` in `sts2-rs/crates/sts2-core/src/acts.rs`
# takes no character parameter, and A0 maps for Ironclad / Silent / Defect /
# Necrobinder are identical in row count.
TOTAL_FLOORS = 45

# A10 (`DoubleBoss`) is the only ascension level that changes run length: it
# attaches a second boss node one row above the first in the final act
# (`runstate.rs::attach_second_boss`, C# `StandardActMap.cs:97-99`), fought as a
# separate combat. Every other level makes the run harder, not longer
# (`AscensionLevel.cs`; A0/A9/A10 map dumps agree — 45 vs 46 rows).
DOUBLE_BOSS_ASCENSION = 10


def total_floors(metrics: dict[str, Any]) -> int:
    """Floors a *typical* completed run reaches at this ascension.

    A normalizer, NOT a bound — see `base_value`. Reads
    `metrics["ascension"]`, which `env.metrics_snapshot` records. Corpora
    recorded before that key existed fall back to A0; `trainer.gae_from_row`,
    `ppo.bc`, `ppo.shaping_fit` and `ppo.shaping_gate` backfill it from the run
    row's own `ascension` field, so the fallback only bites a caller with
    neither.
    """
    asc = int(metrics.get("ascension") or 0)
    return TOTAL_FLOORS + (1 if asc >= DOUBLE_BOSS_ASCENSION else 0)


@dataclass(frozen=True)
class ShapingConfig:
    hp_coef: float = 0.1
    upg_coef: float = 0.035
    relic_coef: float = 0.0
    offset: float = 0.307
    win_bonus: float = 0.5
    progress_weight: float = 0.5


def base_value(metrics: dict[str, Any], won: bool, cfg: ShapingConfig) -> float:
    """Graded progress + win bonus. Strictly increasing in `total_floor`.

    Progress is deliberately NOT clipped to 1.0. Run length is not a constant:
    A10 adds a boss floor, and the **Golden Compass** boss relic replaces the
    next act's map with the fixed 18-row golden-path spine
    (`sts2-content/src/relics/shop_event.rs::listener_golden_compass_modify_generated_map`),
    which is 2 rows longer than a normal act 2 — measured in 8/205 runs that
    reached act 2 (3.9%), taking those runs to floor 47 at A0.

    A `min(1.0, ...)` clip therefore created a flat spot at the END of exactly
    the runs that got furthest: past floor 45 every additional floor scored the
    same, so "a deeper death strictly out-scores a shallower one" — the
    property this function exists to provide — silently stopped holding. Not
    clipping costs nothing (a completed run lands near 1.0 either way, since
    `total_floors` tracks the ascension) and restores strict monotonicity for
    every run length the engine can produce, including ones not yet enumerated.
    """
    floors = float(metrics.get("total_floor") or 0)
    return cfg.progress_weight * (floors / total_floors(metrics)) + (cfg.win_bonus if won else 0.0)


def shape_value(metrics: dict[str, Any], cfg: ShapingConfig) -> float:
    hp_frac = float(metrics.get("hp") or 0) / max(1, int(metrics.get("max_hp") or 1))
    return (
        cfg.hp_coef * hp_frac
        + cfg.upg_coef * float(metrics.get("num_upgraded") or 0)
        + cfg.relic_coef * float(metrics.get("num_relics") or 0)
        - cfg.offset
    )


def potential(metrics: dict[str, Any], cfg: ShapingConfig | None = None) -> float:
    """Φ(s) = base(s) + shape(s) for a NON-terminal state — the potential the
    per-step shaped reward telescopes over. Additive helper for the search
    wrapper (``ppo.search_policy``); the training-path functions above are
    untouched."""
    cfg = cfg or ShapingConfig()
    return base_value(metrics, False, cfg) + shape_value(metrics, cfg)


def shaped_rewards(
    step_metrics: list[dict[str, Any]],
    final_metrics: dict[str, Any],
    won: bool,
    cfg: ShapingConfig | None = None,
) -> tuple[list[float], float]:
    """Per-step rewards (len == len(step_metrics)) + the terminal base value.

    ``step_metrics[t]`` is the snapshot AT decision t (pre-action); the
    potential sequence is [s_0 … s_{T-1}, terminal]. Terminal shaping is
    un-credited (0), non-terminal states carry base+shape.
    """
    cfg = cfg or ShapingConfig()
    base = [base_value(m, False, cfg) for m in step_metrics]
    final_base = base_value(final_metrics, won, cfg)
    shape = [shape_value(m, cfg) for m in step_metrics]
    total = [b + s for b, s in zip(base, shape)] + [final_base]  # terminal shape = 0
    return [total[t + 1] - total[t] for t in range(len(step_metrics))], final_base
