"""Four-cell causal interaction driver for the card-synergy catalog.

Wraps the ``synergy_fight`` Rust binary (Task 3.1) to measure the causal
synergy between two cards via the **second-difference estimator** applied to
forced single-combat outcomes.

**Outcome scalar:** ``-hp_lost``.  Higher is better (less damage taken).
Negating ``hp_lost`` makes the second difference positive when two cards
*jointly* reduce damage beyond their individual contributions.

**RULE 4:** Each (baseline_deck, encounter, seed) triple runs all FOUR cells
(base, +A, +B, +A+B) with the **identical seed**.  The seed controls both the
enemy RNG and MCTS random-play rollouts, so the same combat scenario is
presented to all four configurations.

Usage::

    from synergy.causal_combat import combat_synergy, DEFAULT_ENCOUNTERS

    row = combat_synergy(
        ("IRON_WAVE", "BODY_SLAM"),
        baseline_decks=[IRONCLAD_STARTER],
        encounters=DEFAULT_ENCOUNTERS,
        seeds=list(range(10)),
    )
    print(row.median_interaction, row.ci, row.n)
"""

from __future__ import annotations

import json
import math
import random
import statistics
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

from synergy.observational import Pair

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------

_HERE = Path(__file__).resolve().parent
_RS_ROOT = (_HERE / ".." / ".." / ".." / "sts2-rs").resolve()
_FIGHT_BINARY = _RS_ROOT / "target" / "release" / "synergy_fight"

# ---------------------------------------------------------------------------
# Default panel constants
# ---------------------------------------------------------------------------

#: Encounter ids for the default three-encounter panel.
#: BYGONE_EFFIGY_ELITE — Act 1 elite (reasonable early-game pressure).
#: DECIMILLIPEDE_ELITE — Act 2 elite (mid-game).
#: VANTOM_BOSS — Act 1 boss (heavy-hitter; block-spending pairs score here).
DEFAULT_ENCOUNTERS: list[str] = [
    "BYGONE_EFFIGY_ELITE",
    "DECIMILLIPEDE_ELITE",
    "VANTOM_BOSS",
]

#: Ironclad 10-card starter deck (5×Strike, 4×Defend, Bash).
#: Omit to let the Rust bin default to this automatically (baseline_deck=null),
#: but we materialise it here so callers can build richer baseline pools.
IRONCLAD_STARTER: list[str] = [
    "STRIKE_IRONCLAD",
    "STRIKE_IRONCLAD",
    "STRIKE_IRONCLAD",
    "STRIKE_IRONCLAD",
    "STRIKE_IRONCLAD",
    "DEFEND_IRONCLAD",
    "DEFEND_IRONCLAD",
    "DEFEND_IRONCLAD",
    "DEFEND_IRONCLAD",
    "BASH",
]

# ---------------------------------------------------------------------------
# Build-freshness guard (mirrors causal_econ.py)
# ---------------------------------------------------------------------------


def _newest_source_mtime(rs_root: Path) -> float:
    """Newest mtime over the Rust workspace inputs the synergy_fight binary is built from."""
    newest = 0.0
    for pattern in ("crates/**/*.rs", "crates/**/Cargo.toml"):
        for p in rs_root.glob(pattern):
            try:
                newest = max(newest, p.stat().st_mtime)
            except OSError:
                continue
    for name in ("Cargo.toml", "Cargo.lock"):
        p = rs_root / name
        if p.exists():
            newest = max(newest, p.stat().st_mtime)
    return newest


def _ensure_fight_binary(no_build: bool = False) -> Path:
    """Return the synergy_fight binary path, rebuilding if stale/missing.

    Args:
        no_build: If ``True``, raise :class:`RuntimeError` instead of
            rebuilding when the binary is stale or missing.

    Returns:
        Path to the fresh ``synergy_fight`` binary.

    Raises:
        RuntimeError: When *no_build* is ``True`` and the binary is
            stale or missing.
    """
    stale = not _FIGHT_BINARY.exists() or _FIGHT_BINARY.stat().st_mtime < _newest_source_mtime(
        _RS_ROOT
    )
    if not stale:
        return _FIGHT_BINARY
    if no_build:
        raise RuntimeError(
            f"synergy_fight binary is stale or missing ({_FIGHT_BINARY}).\n"
            "Rebuild with: cargo build --release -p sts2-cli --bin synergy_fight "
            f"(in {_RS_ROOT}) or drop no_build=True."
        )
    print(f"[synergy_fight] rebuilding stale binary: {_FIGHT_BINARY}", file=sys.stderr)
    subprocess.run(
        ["cargo", "build", "--release", "-p", "sts2-cli", "--bin", "synergy_fight"],
        cwd=_RS_ROOT,
        check=True,
    )
    return _FIGHT_BINARY


# ---------------------------------------------------------------------------
# Single-fight runner
# ---------------------------------------------------------------------------


def run_fight(
    baseline_deck: list[str],
    inject: list[str],
    encounter_id: str,
    seed: int,
    sims: int = 1500,
    depth: int = 50,
    no_build: bool = False,
    character: str | None = None,
) -> dict[str, object]:
    """Run one forced-deck fight and return the raw outcome.

    Invokes the ``synergy_fight`` binary exactly once.  The full deck is
    ``baseline_deck ++ inject``; no relics are added (the bin runs relic-free).

    Args:
        baseline_deck: Base card tokens.  Pass ``[]`` or ``IRONCLAD_STARTER``
            (or omit via the bin default if you pass ``None`` internally).
        inject: Cards to append after the baseline.  For cell A this is
            ``[a]``; for cell AB this is ``[a, b]``.
        encounter_id: Upper-snake encounter id; see :data:`DEFAULT_ENCOUNTERS`.
        seed: Engine RNG seed; MUST be the same for all four cells of a given
            (baseline, encounter, seed) triple.
        sims: MCTS simulations per move (default 1500).
        depth: Max rollout depth (default 50).
        no_build: Forwarded to :func:`_ensure_fight_binary`.
        character: Character tag (``ironclad`` default / ``silent`` / ``defect``
            / ``regent`` / ``necrobinder``).  ``None`` → the bin default
            (Ironclad).  Selects the player class + default starter deck.

    Returns:
        A dict with keys ``won`` (bool), ``hp_lost`` (int), ``turns`` (int).

    Raises:
        RuntimeError: Binary stale/missing and *no_build* is ``True``.
        subprocess.CalledProcessError: Binary exits non-zero (unknown card / enc).
        ValueError: Binary output is not valid JSON or missing expected keys.
    """
    binary = _ensure_fight_binary(no_build=no_build)

    spec: dict[str, object] = {
        "baseline_deck": baseline_deck if baseline_deck else None,
        "inject": inject,
        "encounter_id": encounter_id,
        "seed": seed,
        "sims": sims,
        "depth": depth,
    }
    if character is not None:
        spec["character"] = character
    json_input = json.dumps(spec)

    result = subprocess.run(
        [str(binary), "--json", json_input],
        capture_output=True,
        text=True,
    )

    if result.returncode != 0:
        raise subprocess.CalledProcessError(
            result.returncode,
            str(binary),
            output=result.stdout,
            stderr=result.stderr,
        )

    try:
        outcome = json.loads(result.stdout.strip())
    except json.JSONDecodeError as exc:
        raise ValueError(f"synergy_fight returned non-JSON output: {result.stdout!r}") from exc

    for key in ("won", "hp_lost", "turns"):
        if key not in outcome:
            raise ValueError(f"synergy_fight output missing key '{key}': {outcome!r}")

    return outcome


# ---------------------------------------------------------------------------
# Outcome scalar
# ---------------------------------------------------------------------------


def _scalar(outcome: dict[str, object]) -> float:
    """Convert a fight outcome to the primary outcome scalar.

    Scalar = ``-hp_lost``.  Higher values are better (less damage taken).
    Negating makes the second difference positive when A+B together reduce
    damage beyond their individual contributions (i.e. genuine synergy).

    Args:
        outcome: Dict with at least ``hp_lost`` (int) key.

    Returns:
        ``float(-hp_lost)``.
    """
    return float(-int(outcome["hp_lost"]))


# ---------------------------------------------------------------------------
# CombatRow dataclass
# ---------------------------------------------------------------------------


@dataclass
class CombatRow:
    """Aggregated four-cell interaction estimate for a card pair.

    For each (baseline_deck, encounter, seed) triple, the per-cell scalars are::

        sc(base) = -hp_lost(base)
        sc(A)    = -hp_lost(base+A)
        sc(B)    = -hp_lost(base+B)
        sc(AB)   = -hp_lost(base+A+B)

    The per-triple interaction (second difference) is::

        I = sc(AB) - sc(A) - sc(B) + sc(base)

    Positive *I* → the two cards together reduce damage more than expected
    from their individual contributions (synergy).

    Attributes:
        median_interaction: Median of I across all (baseline, encounter, seed)
            triples.
        ci: 95 % bootstrap percentile CI as ``(lower, upper)``.  For n < 2,
            both bounds equal the median.
        n: Number of (baseline_deck, encounter, seed) triples evaluated.
    """

    median_interaction: float
    ci: tuple[float, float]
    n: int


# ---------------------------------------------------------------------------
# Bootstrap CI
# ---------------------------------------------------------------------------

_N_BOOTSTRAP = 2000
_CI_ALPHA = 0.05


def _bootstrap_ci(
    values: list[float],
    n_boot: int = _N_BOOTSTRAP,
    alpha: float = _CI_ALPHA,
    seed: int = 0,
) -> tuple[float, float]:
    """Bootstrap percentile CI for the median.

    Args:
        values: Raw interaction values.
        n_boot: Number of bootstrap resamples.
        alpha: Two-sided coverage level (default 0.05 → 95 % CI).
        seed: RNG seed for reproducibility.

    Returns:
        ``(lower, upper)`` percentile CI.  If ``len(values) < 2``, returns
        ``(median, median)`` as a degenerate interval.
    """
    if len(values) < 2:
        med = statistics.median(values) if values else 0.0
        return (med, med)

    rng = random.Random(seed)
    n = len(values)
    boot_medians: list[float] = []
    for _ in range(n_boot):
        sample = [values[rng.randrange(n)] for _ in range(n)]
        boot_medians.append(statistics.median(sample))

    boot_medians.sort()
    lo_idx = math.floor((alpha / 2) * n_boot)
    hi_idx = math.ceil((1.0 - alpha / 2) * n_boot) - 1
    lo_idx = max(0, lo_idx)
    hi_idx = min(n_boot - 1, hi_idx)
    return (boot_medians[lo_idx], boot_medians[hi_idx])


# ---------------------------------------------------------------------------
# Main entry point
# ---------------------------------------------------------------------------


def combat_synergy(
    pair: Pair,
    baseline_decks: list[list[str]],
    encounters: list[str],
    seeds: list[int],
    sims: int = 1500,
    depth: int = 50,
    no_build: bool = False,
    character: str | None = None,
) -> CombatRow:
    """Compute the four-cell causal interaction for *pair* over a fight panel.

    For each (baseline_deck, encounter, seed) triple, runs **four** fights:

    * **base:**  ``run_fight(baseline_deck, [],    encounter, seed)``
    * **A:**     ``run_fight(baseline_deck, [a],   encounter, seed)``
    * **B:**     ``run_fight(baseline_deck, [b],   encounter, seed)``
    * **AB:**    ``run_fight(baseline_deck, [a,b], encounter, seed)``

    The SAME *seed* is used for all four cells (RULE 4).  Per-triple
    interaction ``I = sc(AB) − sc(A) − sc(B) + sc(base)`` where
    ``sc = -hp_lost``.

    Aggregates all interaction values to ``median_interaction`` and a 95 %
    bootstrap percentile CI.

    Args:
        pair: ``(card_a_token, card_b_token)`` — engine card tokens.
        baseline_decks: List of baseline deck token lists.  Each is used as
            the common context across all encounters and seeds.
        encounters: List of encounter ids (e.g. :data:`DEFAULT_ENCOUNTERS`).
        seeds: List of integer engine seeds.
        sims: MCTS simulations per move, forwarded to the bin.
        depth: Max rollout depth, forwarded to the bin.
        no_build: Forwarded to :func:`run_fight`; set ``True`` to fail fast
            if the binary is not already built.
        character: Character tag forwarded to every fight (see
            :func:`run_fight`).  ``None`` → Ironclad.

    Returns:
        :class:`CombatRow` with ``median_interaction``, ``ci``, and ``n``.
        For an empty panel (no baselines, encounters, or seeds), returns
        ``CombatRow(0.0, (0.0, 0.0), 0)``.
    """
    if not baseline_decks or not encounters or not seeds:
        return CombatRow(median_interaction=0.0, ci=(0.0, 0.0), n=0)

    a, b = pair
    interactions: list[float] = []

    for baseline_deck in baseline_decks:
        for encounter in encounters:
            for seed in seeds:
                out_base = run_fight(
                    baseline_deck, [], encounter, seed, sims, depth, no_build, character
                )
                out_a = run_fight(
                    baseline_deck, [a], encounter, seed, sims, depth, no_build, character
                )
                out_b = run_fight(
                    baseline_deck, [b], encounter, seed, sims, depth, no_build, character
                )
                out_ab = run_fight(
                    baseline_deck, [a, b], encounter, seed, sims, depth, no_build, character
                )

                sc_base = _scalar(out_base)
                sc_a = _scalar(out_a)
                sc_b = _scalar(out_b)
                sc_ab = _scalar(out_ab)

                interactions.append(sc_ab - sc_a - sc_b + sc_base)

    if not interactions:
        return CombatRow(median_interaction=0.0, ci=(0.0, 0.0), n=0)

    med = statistics.median(interactions)
    ci = _bootstrap_ci(interactions)
    return CombatRow(median_interaction=med, ci=ci, n=len(interactions))
