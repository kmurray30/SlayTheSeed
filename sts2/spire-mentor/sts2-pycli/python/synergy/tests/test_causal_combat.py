"""Tests for synergy.causal_combat — Four-cell interaction driver.

Unit tests use a stubbed ``run_fight`` (monkeypatch) so no Rust binary
is needed.  The integration test is skipped when the release binary is
missing; build it with::

    cargo build --release -p sts2-cli --bin synergy_fight

inside the sts2-rs directory.
"""

from __future__ import annotations

import math
from pathlib import Path
from typing import Any

import pytest

from synergy.causal_combat import (
    IRONCLAD_STARTER,
    CombatRow,
    _bootstrap_ci,
    _scalar,
    combat_synergy,
    run_fight,
)

# ---------------------------------------------------------------------------
# Helpers / fixtures
# ---------------------------------------------------------------------------

_RS_ROOT = (Path(__file__).resolve().parent / ".." / ".." / ".." / ".." / "sts2-rs").resolve()
_FIGHT_BINARY = _RS_ROOT / "target" / "release" / "synergy_fight"

_INTEGRATION_REASON = (
    "release binary synergy_fight not found — "
    "run `cargo build --release -p sts2-cli --bin synergy_fight` in sts2-rs"
)


def _binary_available() -> bool:
    return _FIGHT_BINARY.exists()


def _make_outcome(hp_lost: int, won: bool = False, turns: int = 5) -> dict[str, Any]:
    return {"won": won, "hp_lost": hp_lost, "turns": turns}


# ---------------------------------------------------------------------------
# Unit tests — _scalar
# ---------------------------------------------------------------------------


class TestScalar:
    def test_negates_hp_lost(self) -> None:
        # exact integer-valued result; equality is intentional
        assert _scalar({"hp_lost": 20, "won": False, "turns": 3}) == -20.0  # noqa: RUF069

    def test_zero_hp_lost(self) -> None:
        assert _scalar({"hp_lost": 0, "won": True, "turns": 2}) == 0.0  # noqa: RUF069

    def test_large_hp_lost(self) -> None:
        assert _scalar({"hp_lost": 70, "won": False, "turns": 10}) == -70.0  # noqa: RUF069

    def test_returns_float(self) -> None:
        result = _scalar({"hp_lost": 15, "won": False, "turns": 4})
        assert isinstance(result, float)


# ---------------------------------------------------------------------------
# Unit tests — _bootstrap_ci
# ---------------------------------------------------------------------------


class TestBootstrapCi:
    def test_degenerate_single_value(self) -> None:
        lo, hi = _bootstrap_ci([5.0])
        assert lo == hi == 5.0  # noqa: RUF069  # degenerate CI returns the input value exactly

    def test_empty_returns_zero_interval(self) -> None:
        lo, hi = _bootstrap_ci([])
        assert lo == hi == 0.0  # noqa: RUF069  # empty input returns the 0.0 sentinel exactly

    def test_ci_bounds_ordered(self) -> None:
        values = [float(x) for x in range(-10, 11)]
        lo, hi = _bootstrap_ci(values, seed=42)
        assert lo <= hi

    def test_ci_contains_median(self) -> None:
        import statistics

        values = [float(x) for x in range(20)]
        med = statistics.median(values)
        lo, hi = _bootstrap_ci(values, seed=0)
        assert lo <= med <= hi

    def test_finite_bounds(self) -> None:
        values = [1.0, 2.0, 3.0, 4.0, 5.0]
        lo, hi = _bootstrap_ci(values)
        assert math.isfinite(lo) and math.isfinite(hi)


# ---------------------------------------------------------------------------
# Unit tests — combat_synergy with stubbed run_fight
# ---------------------------------------------------------------------------

# Card pair used in unit tests
_PAIR = ("IRON_WAVE", "BODY_SLAM")
_A, _B = _PAIR
_ENC = "BYGONE_EFFIGY_ELITE"
_SEEDS = [1, 2, 3]
_BASELINES = [IRONCLAD_STARTER]


class TestSameSeedCells:
    """RULE 4: all four cells for a given (baseline, enc, seed) triple must
    call run_fight with the SAME seed, and with injects [], [a], [b], [a,b]."""

    def test_same_seed_for_all_four_cells(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """The four run_fight calls for each triple share the same seed."""
        recorded: list[tuple[list[str], str, int]] = []

        def _stub(
            baseline_deck: list[str],
            inject: list[str],
            encounter_id: str,
            seed: int,
            sims: int = 1500,
            depth: int = 50,
            no_build: bool = False,
            character: str | None = None,
        ) -> dict[str, Any]:
            recorded.append((inject, encounter_id, seed))
            return _make_outcome(hp_lost=10)

        import synergy.causal_combat as mod

        monkeypatch.setattr(mod, "run_fight", _stub)

        seeds = [42, 99]
        combat_synergy(
            _PAIR,
            baseline_decks=[IRONCLAD_STARTER],
            encounters=[_ENC],
            seeds=seeds,
            no_build=True,
        )

        # For each seed we expect exactly 4 calls: [], [a], [b], [a,b]
        for seed in seeds:
            calls_for_seed = [(inj, enc, s) for inj, enc, s in recorded if s == seed]
            assert len(calls_for_seed) == 4, (
                f"expected 4 calls for seed={seed}, got {len(calls_for_seed)}"
            )
            injects_seen = [tuple(inj) for inj, _, _ in calls_for_seed]
            assert tuple([]) in injects_seen, "missing base call (inject=[])"
            assert (_A,) in injects_seen, f"missing cell A inject=[{_A}]"
            assert (_B,) in injects_seen, f"missing cell B inject=[{_B}]"
            assert (_A, _B) in injects_seen, f"missing cell AB inject=[{_A},{_B}]"


class TestAdditiveStubInteractionZero:
    """If hp_lost is a pure additive function of injected cards, the second
    difference is zero (no emergent synergy)."""

    def test_additive_stub_gives_zero_interaction(self, monkeypatch: pytest.MonkeyPatch) -> None:
        # Per-card penalties: each injected card costs 5 hp.
        per_card = 5

        def _stub(
            baseline_deck: list[str],
            inject: list[str],
            encounter_id: str,
            seed: int,
            sims: int = 1500,
            depth: int = 50,
            no_build: bool = False,
            character: str | None = None,
        ) -> dict[str, Any]:
            hp_lost = 20 + per_card * len(inject)  # additive
            return _make_outcome(hp_lost=hp_lost)

        import synergy.causal_combat as mod

        monkeypatch.setattr(mod, "run_fight", _stub)

        row = combat_synergy(
            _PAIR,
            baseline_decks=[IRONCLAD_STARTER],
            encounters=[_ENC],
            seeds=_SEEDS,
            no_build=True,
        )

        # sc = -hp_lost, additive stub → interaction = 0
        assert row.median_interaction == pytest.approx(0.0, abs=1e-9)


class TestSuperAdditiveStubPositiveInteraction:
    """A stub that adds a bonus only when BOTH a and b are present should
    produce a positive median_interaction equal to that bonus."""

    _SYNERGY_BONUS = 8  # hp bonus only when both cards injected

    def test_super_additive_stub_positive_interaction(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        bonus = self._SYNERGY_BONUS

        def _stub(
            baseline_deck: list[str],
            inject: list[str],
            encounter_id: str,
            seed: int,
            sims: int = 1500,
            depth: int = 50,
            no_build: bool = False,
            character: str | None = None,
        ) -> dict[str, Any]:
            a_present = _A in inject
            b_present = _B in inject
            # base hp_lost=20; each card individually saves 3 hp; together save
            # an extra `bonus` hp on top.
            hp_lost = 20
            if a_present:
                hp_lost -= 3
            if b_present:
                hp_lost -= 3
            if a_present and b_present:
                hp_lost -= bonus  # super-additive synergy
            return _make_outcome(hp_lost=max(0, hp_lost))

        import synergy.causal_combat as mod

        monkeypatch.setattr(mod, "run_fight", _stub)

        row = combat_synergy(
            _PAIR,
            baseline_decks=[IRONCLAD_STARTER],
            encounters=[_ENC],
            seeds=_SEEDS,
            no_build=True,
        )

        # Expected interaction = bonus (hp saved synergistically → +scalar)
        assert row.median_interaction == pytest.approx(float(bonus), abs=1e-9)
        assert row.median_interaction > 0.0


class TestNIsCorrect:
    """n == len(baselines) * len(encounters) * len(seeds), and CI is finite
    with ci[0] <= median_interaction <= ci[1]."""

    def test_n_matches_panel_size(self, monkeypatch: pytest.MonkeyPatch) -> None:
        def _stub(
            baseline_deck: list[str],
            inject: list[str],
            encounter_id: str,
            seed: int,
            sims: int = 1500,
            depth: int = 50,
            no_build: bool = False,
            character: str | None = None,
        ) -> dict[str, Any]:
            return _make_outcome(hp_lost=10)

        import synergy.causal_combat as mod

        monkeypatch.setattr(mod, "run_fight", _stub)

        baselines = [IRONCLAD_STARTER, ["STRIKE_IRONCLAD", "BASH"]]
        encounters = ["BYGONE_EFFIGY_ELITE", "VANTOM_BOSS"]
        seeds = [1, 2, 3, 4, 5]

        row = combat_synergy(
            _PAIR,
            baseline_decks=baselines,
            encounters=encounters,
            seeds=seeds,
            no_build=True,
        )

        expected_n = len(baselines) * len(encounters) * len(seeds)
        assert row.n == expected_n

    def test_ci_bounds_are_finite_and_ordered(self, monkeypatch: pytest.MonkeyPatch) -> None:
        def _stub(
            baseline_deck: list[str],
            inject: list[str],
            encounter_id: str,
            seed: int,
            sims: int = 1500,
            depth: int = 50,
            no_build: bool = False,
            character: str | None = None,
        ) -> dict[str, Any]:
            return _make_outcome(hp_lost=10 + seed % 5)

        import synergy.causal_combat as mod

        monkeypatch.setattr(mod, "run_fight", _stub)

        row = combat_synergy(
            _PAIR,
            baseline_decks=[IRONCLAD_STARTER],
            encounters=[_ENC, "VANTOM_BOSS"],
            seeds=list(range(1, 11)),
            no_build=True,
        )

        lo, hi = row.ci
        assert math.isfinite(lo) and math.isfinite(hi), f"CI not finite: {row.ci}"
        assert lo <= hi, f"CI bounds inverted: {row.ci}"
        # Median within CI (degenerate intervals allowed when lo == hi == median)
        assert lo <= row.median_interaction <= hi, (
            f"median {row.median_interaction} outside CI {row.ci}"
        )

    def test_single_cell_ci_is_degenerate_not_nan(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """n=1 → CI is (median, median), not NaN."""

        def _stub(
            baseline_deck: list[str],
            inject: list[str],
            encounter_id: str,
            seed: int,
            sims: int = 1500,
            depth: int = 50,
            no_build: bool = False,
            character: str | None = None,
        ) -> dict[str, Any]:
            return _make_outcome(hp_lost=15)

        import synergy.causal_combat as mod

        monkeypatch.setattr(mod, "run_fight", _stub)

        row = combat_synergy(
            _PAIR,
            baseline_decks=[IRONCLAD_STARTER],
            encounters=[_ENC],
            seeds=[42],
            no_build=True,
        )
        assert row.n == 1
        lo, hi = row.ci
        assert math.isfinite(lo) and math.isfinite(hi)
        assert lo == hi


class TestEmptyPanel:
    """Empty inputs return a zero CombatRow without calling run_fight."""

    def test_empty_seeds(self) -> None:
        row = combat_synergy(_PAIR, baseline_decks=[IRONCLAD_STARTER], encounters=[_ENC], seeds=[])
        assert row.n == 0
        assert row.median_interaction == 0.0  # noqa: RUF069  # empty panel -> 0.0 sentinel

    def test_empty_encounters(self) -> None:
        row = combat_synergy(_PAIR, baseline_decks=[IRONCLAD_STARTER], encounters=[], seeds=[1])
        assert row.n == 0

    def test_empty_baselines(self) -> None:
        row = combat_synergy(_PAIR, baseline_decks=[], encounters=[_ENC], seeds=[1])
        assert row.n == 0


# ---------------------------------------------------------------------------
# Integration tests — real binary
# ---------------------------------------------------------------------------


@pytest.mark.skipif(not _binary_available(), reason=_INTEGRATION_REASON)
class TestIntegration:
    """Integration tests that invoke the real synergy_fight binary.

    Use a small config (low sims + small seeds list) to keep wall-clock
    time reasonable — each fight at sims=300 takes ~0.5–2 s in release mode.
    """

    def test_small_run_returns_finite_combat_row(self) -> None:
        """1 baseline × 1 encounter × 2 seeds → CombatRow with finite fields."""
        row = combat_synergy(
            ("IRON_WAVE", "BODY_SLAM"),
            baseline_decks=[IRONCLAD_STARTER],
            encounters=["BYGONE_EFFIGY_ELITE"],
            seeds=[1, 2],
            sims=300,
            depth=30,
        )
        assert row.n == 2
        assert math.isfinite(row.median_interaction), (
            f"median_interaction is not finite: {row.median_interaction}"
        )
        lo, hi = row.ci
        assert math.isfinite(lo) and math.isfinite(hi), f"CI not finite: {row.ci}"
        # Log the numbers for the report
        print(
            f"\n[integration] IRON_WAVE×BODY_SLAM on BYGONE_EFFIGY_ELITE seeds=[1,2] "
            f"sims=300 depth=30:\n"
            f"  median_interaction={row.median_interaction:.2f}  "
            f"CI=({lo:.2f}, {hi:.2f})  n={row.n}"
        )

    def test_run_fight_returns_expected_keys(self) -> None:
        """run_fight returns a dict with won/hp_lost/turns."""
        outcome = run_fight(
            baseline_deck=IRONCLAD_STARTER,
            inject=[],
            encounter_id="BYGONE_EFFIGY_ELITE",
            seed=7,
            sims=300,
            depth=30,
        )
        assert "won" in outcome
        assert "hp_lost" in outcome
        assert "turns" in outcome
        assert isinstance(outcome["hp_lost"], int)
        assert isinstance(outcome["won"], bool)
        assert isinstance(outcome["turns"], int)
        assert outcome["hp_lost"] >= 0
        assert outcome["turns"] >= 0

    def test_directional_block_pair_on_boss(self) -> None:
        """Directional check: IRON_WAVE + BODY_SLAM on VANTOM_BOSS.

        Block-spend pairs are expected to score positively on heavy-hitter
        encounters.  With sims=300/seeds=[1,2,3] the effect is noisy, but
        we log the direction rather than asserting the sign (too few seeds
        for a reliable assert — the full-run catalog uses 10+ seeds).
        """
        row = combat_synergy(
            ("IRON_WAVE", "BODY_SLAM"),
            baseline_decks=[IRONCLAD_STARTER],
            encounters=["VANTOM_BOSS"],
            seeds=[1, 2, 3],
            sims=300,
            depth=30,
        )
        lo, hi = row.ci
        direction = "positive" if row.median_interaction > 0 else "negative/zero"
        print(
            f"\n[integration] IRON_WAVE×BODY_SLAM on VANTOM_BOSS seeds=[1,2,3] sims=300 depth=30:\n"
            f"  median_interaction={row.median_interaction:.2f}  "
            f"CI=({lo:.2f}, {hi:.2f})  n={row.n}  direction={direction}"
        )
        # Soft assertion: just verify the run completed without errors and n is correct
        assert row.n == 3
        assert math.isfinite(row.median_interaction)
