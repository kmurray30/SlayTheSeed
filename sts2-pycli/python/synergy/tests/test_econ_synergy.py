"""Tests for econ_synergy / EconRow — Task 2.3.

Unit tests are fully stubbed (no Rust binary).
Integration test is skipped if the release binary is absent.
"""

from __future__ import annotations

import math
from pathlib import Path

import pytest

from synergy.causal_econ import EconRow, State, econ_synergy
from synergy.observational import Pair

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

_RS_ROOT = (Path(__file__).resolve().parent / ".." / ".." / ".." / ".." / "sts2-rs").resolve()
_BINARY = _RS_ROOT / "target" / "release" / "synergy_score"

_INTEGRATION_REASON = (
    "release binary synergy_score not found — "
    "run `cargo build --release -p sts2-cli --bin synergy_score` in sts2-rs"
)


def _binary_available() -> bool:
    return _BINARY.exists()


def _make_contexts(n: int = 5) -> list[State]:
    """Return *n* simple fixture contexts (no corpus needed)."""
    return [
        State(
            deck=["STRIKE_IRONCLAD", "BASH"],
            relics=["BURNING_BLOOD"],
            hp=60 + i,
            max_hp=80,
            gold=99,
            ascension=0,
            act_index=2,
            act_floor=3,
        )
        for i in range(n)
    ]


# ---------------------------------------------------------------------------
# Stub scorers
# ---------------------------------------------------------------------------

# Assign a per-card weight so score = sum of weights.
_CARD_WEIGHT: dict[str, float] = {
    "STRIKE_IRONCLAD": 1.0,
    "BASH": 2.0,
    "INFLAME": 3.0,
    "WHIRLWIND": 5.0,
}


def _additive_scorer(states: list[State], no_build: bool = False, **_kwargs) -> list[float]:
    """Returns sum of known card weights — second difference is always 0."""
    return [sum(_CARD_WEIGHT.get(c, 0.5) for c in s.deck) for s in states]


def _superadditive_scorer_factory(a: str, b: str, bonus: float):
    """Returns a scorer that adds *bonus* when BOTH *a* and *b* are present."""

    def _scorer(states: list[State], no_build: bool = False, **_kwargs) -> list[float]:
        results = []
        for s in states:
            base = sum(_CARD_WEIGHT.get(c, 0.5) for c in s.deck)
            cards = set(s.deck)
            synergy_bonus = bonus if (a in cards and b in cards) else 0.0
            results.append(base + synergy_bonus)
        return results

    return _scorer


# ---------------------------------------------------------------------------
# Test 1 — Additive pair: second difference == 0
# ---------------------------------------------------------------------------


class TestAdditiveScorer:
    def test_median_approx_zero(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """With an additive scorer, V(D+A+B) − V(D+A) − V(D+B) + V(D) == 0 exactly."""
        from synergy import causal_econ

        monkeypatch.setattr(causal_econ, "score_states", _additive_scorer)

        pair: Pair = ("INFLAME", "WHIRLWIND")
        contexts = _make_contexts(5)
        row = econ_synergy(pair, contexts)

        assert row.median == pytest.approx(0.0, abs=1e-9)

    def test_p25_and_p75_also_zero(self, monkeypatch: pytest.MonkeyPatch) -> None:
        from synergy import causal_econ

        monkeypatch.setattr(causal_econ, "score_states", _additive_scorer)

        pair: Pair = ("INFLAME", "WHIRLWIND")
        contexts = _make_contexts(5)
        row = econ_synergy(pair, contexts)

        assert row.p25 == pytest.approx(0.0, abs=1e-9)
        assert row.p75 == pytest.approx(0.0, abs=1e-9)


# ---------------------------------------------------------------------------
# Test 2 — Super-additive pair: second difference == bonus
# ---------------------------------------------------------------------------


class TestSuperAdditiveScorer:
    def test_median_equals_bonus(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """With a super-additive scorer, second difference equals the bonus."""
        from synergy import causal_econ

        bonus = 7.0
        pair: Pair = ("INFLAME", "WHIRLWIND")
        monkeypatch.setattr(
            causal_econ,
            "score_states",
            _superadditive_scorer_factory("INFLAME", "WHIRLWIND", bonus),
        )

        contexts = _make_contexts(5)
        row = econ_synergy(pair, contexts)

        assert row.median == pytest.approx(bonus, abs=1e-9)

    def test_median_positive(self, monkeypatch: pytest.MonkeyPatch) -> None:
        from synergy import causal_econ

        pair: Pair = ("INFLAME", "WHIRLWIND")
        monkeypatch.setattr(
            causal_econ,
            "score_states",
            _superadditive_scorer_factory("INFLAME", "WHIRLWIND", 3.5),
        )

        contexts = _make_contexts(5)
        row = econ_synergy(pair, contexts)

        assert row.median > 0.0


# ---------------------------------------------------------------------------
# Test 3 — Quartet slicing / state count / isolation
# ---------------------------------------------------------------------------


class TestQuartetSlicing:
    def test_score_states_called_with_4k_states(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """score_states must receive exactly 4*K states for K contexts."""
        from synergy import causal_econ

        received: list[int] = []

        def _recording_scorer(
            states: list[State], no_build: bool = False, **_kwargs
        ) -> list[float]:
            received.append(len(states))
            return [0.0] * len(states)

        monkeypatch.setattr(causal_econ, "score_states", _recording_scorer)

        K = 7
        contexts = _make_contexts(K)
        pair: Pair = ("INFLAME", "WHIRLWIND")
        row = econ_synergy(pair, contexts)

        assert len(received) == 1, "score_states should be called exactly once"
        assert received[0] == 4 * K
        assert row.n == K

    def test_states_differ_only_in_ab_additions(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """For each context quartet, relics/hp/gold/ascension etc. must be identical;
        only deck differs by A/B additions."""
        from synergy import causal_econ

        captured: list[list[State]] = []

        def _capturing_scorer(
            states: list[State], no_build: bool = False, **_kwargs
        ) -> list[float]:
            captured.append(list(states))
            return [0.0] * len(states)

        monkeypatch.setattr(causal_econ, "score_states", _capturing_scorer)

        K = 3
        A, B = "INFLAME", "WHIRLWIND"
        pair: Pair = (A, B)
        contexts = _make_contexts(K)
        econ_synergy(pair, contexts)

        all_states = captured[0]
        assert len(all_states) == 4 * K

        for i in range(K):
            s_d, s_da, s_db, s_dab = all_states[4 * i : 4 * i + 4]
            ctx = contexts[i]

            # Non-deck fields must match the original context exactly
            for s in (s_d, s_da, s_db, s_dab):
                assert s.relics == ctx.relics
                assert s.hp == ctx.hp
                assert s.max_hp == ctx.max_hp
                assert s.gold == ctx.gold
                assert s.ascension == ctx.ascension
                assert s.act_index == ctx.act_index
                assert s.act_floor == ctx.act_floor

            # Deck relationships: D, D+A, D+B, D+A+B
            assert s_d.deck == ctx.deck
            assert s_da.deck == [*ctx.deck, A]
            assert s_db.deck == [*ctx.deck, B]
            assert s_dab.deck == [*ctx.deck, A, B]

    def test_input_contexts_not_mutated(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """econ_synergy must NOT mutate the input context decks."""
        from synergy import causal_econ

        monkeypatch.setattr(
            causal_econ,
            "score_states",
            lambda states, no_build=False, **_kwargs: [0.0] * len(states),
        )

        contexts = _make_contexts(3)
        original_decks = [list(s.deck) for s in contexts]
        econ_synergy(("INFLAME", "WHIRLWIND"), contexts)

        for s, orig in zip(contexts, original_decks):
            assert s.deck == orig, "econ_synergy must not mutate context decks"


# ---------------------------------------------------------------------------
# Test 4 — p25/p75 distribution sanity
# ---------------------------------------------------------------------------


class TestDistributionSanity:
    def test_p25_le_median_le_p75(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """p25 <= median <= p75 must hold when S values vary across contexts."""
        from synergy import causal_econ

        counter = [0]

        def _varying_scorer(states: list[State], no_build: bool = False, **_kwargs) -> list[float]:
            """Each quartet gets a different S value: S_i = i (0, 1, 2, ...)."""
            n = len(states)
            k = n // 4
            out = []
            for i in range(k):
                # V_D=0, V_DA=0, V_DB=0, V_DAB=i → S = i
                out.extend([0.0, 0.0, 0.0, float(i)])
            return out

        monkeypatch.setattr(causal_econ, "score_states", _varying_scorer)

        K = 10
        contexts = _make_contexts(K)
        pair: Pair = ("INFLAME", "WHIRLWIND")
        row = econ_synergy(pair, contexts)

        assert math.isfinite(row.p25)
        assert math.isfinite(row.median)
        assert math.isfinite(row.p75)
        assert row.p25 <= row.median <= row.p75

    def test_n_equals_context_count(self, monkeypatch: pytest.MonkeyPatch) -> None:
        from synergy import causal_econ

        monkeypatch.setattr(
            causal_econ,
            "score_states",
            lambda states, no_build=False, **_kwargs: [0.0] * len(states),
        )

        K = 8
        row = econ_synergy(("INFLAME", "WHIRLWIND"), _make_contexts(K))
        assert row.n == K

    def test_econrow_fields_exist(self, monkeypatch: pytest.MonkeyPatch) -> None:
        from synergy import causal_econ

        monkeypatch.setattr(
            causal_econ,
            "score_states",
            lambda states, no_build=False, **_kwargs: [0.0] * len(states),
        )

        row = econ_synergy(("INFLAME", "WHIRLWIND"), _make_contexts(4))
        assert hasattr(row, "median")
        assert hasattr(row, "p25")
        assert hasattr(row, "p75")
        assert hasattr(row, "n")


# ---------------------------------------------------------------------------
# Test 5 — Edge cases
# ---------------------------------------------------------------------------


class TestEdgeCases:
    def test_empty_contexts_returns_zero_n(self, monkeypatch: pytest.MonkeyPatch) -> None:
        from synergy import causal_econ

        monkeypatch.setattr(
            causal_econ,
            "score_states",
            lambda states, no_build=False, **_kwargs: [0.0] * len(states),
        )

        row = econ_synergy(("INFLAME", "WHIRLWIND"), [])
        assert row.n == 0

    def test_single_context(self, monkeypatch: pytest.MonkeyPatch) -> None:
        from synergy import causal_econ

        monkeypatch.setattr(
            causal_econ,
            "score_states",
            _superadditive_scorer_factory("INFLAME", "WHIRLWIND", 2.5),
        )

        row = econ_synergy(("INFLAME", "WHIRLWIND"), _make_contexts(1))
        assert row.n == 1
        assert math.isfinite(row.median)

    def test_same_card_both_slots(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """A pair where A==B is unusual but must not crash."""
        from synergy import causal_econ

        monkeypatch.setattr(
            causal_econ,
            "score_states",
            lambda states, no_build=False, **_kwargs: [0.0] * len(states),
        )

        row = econ_synergy(("INFLAME", "INFLAME"), _make_contexts(3))
        assert isinstance(row, EconRow)


# ---------------------------------------------------------------------------
# Integration test
# ---------------------------------------------------------------------------


@pytest.mark.skipif(not _binary_available(), reason=_INTEGRATION_REASON)
class TestEconSynergyIntegration:
    def test_real_pair_finite_row(self) -> None:
        """econ_synergy on a real pair must return a finite EconRow."""
        from synergy.causal_econ import sample_contexts

        # Use fixture decks instead of corpus (faster, no env dependency)
        from synergy.corpus import Deck

        fixture_decks = [
            Deck(
                cards=[
                    "STRIKE_IRONCLAD",
                    "STRIKE_IRONCLAD",
                    "BASH",
                    "DEFEND_IRONCLAD",
                    "INFLAME",
                    "WHIRLWIND",
                ],
                won=True,
                player_id=1,
                skill=None,
            )
        ]
        contexts = sample_contexts(4, decks=fixture_decks, seed=0)
        pair: Pair = ("INFLAME", "WHIRLWIND")
        row = econ_synergy(pair, contexts)

        assert isinstance(row, EconRow)
        assert row.n == len(contexts)
        assert math.isfinite(row.median)
        assert math.isfinite(row.p25)
        assert math.isfinite(row.p75)
        assert row.p25 <= row.median <= row.p75
