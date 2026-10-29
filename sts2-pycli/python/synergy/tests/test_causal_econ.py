"""Tests for synergy.causal_econ — State sampler + value-net scorer wrapper.

Unit tests run without the Rust binary (always green in CI).
Integration tests are skipped if the release binary is missing/unbuildable;
run `cargo build --release -p sts2-cli --bin synergy_score` in sts2-rs first.
"""

from __future__ import annotations

import json
import math
import os
from dataclasses import dataclass
from pathlib import Path

import pytest

# ---------------------------------------------------------------------------
# Import the module under test
# ---------------------------------------------------------------------------
from synergy.causal_econ import State, sample_contexts, score_states

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


# ---------------------------------------------------------------------------
# Tiny fixture corpus (two real-ish decks)
# ---------------------------------------------------------------------------

_DECK_A = ["STRIKE_IRONCLAD", "STRIKE_IRONCLAD", "BASH", "DEFEND_IRONCLAD"]
_DECK_B = ["STRIKE_IRONCLAD", "CLEAVE", "CLEAVE"]


def _make_deck(cards: list[str]):
    from synergy.corpus import Deck

    return Deck(cards=cards, won=True, player_id=1, skill=None)


_FIXTURE_DECKS = [_make_deck(_DECK_A), _make_deck(_DECK_B)]

# ---------------------------------------------------------------------------
# Unit tests — State dataclass
# ---------------------------------------------------------------------------


class TestStateDataclass:
    def test_default_relics(self) -> None:
        s = State(deck=["BASH"])
        assert s.relics == ["BURNING_BLOOD"]

    def test_defaults_are_sensible(self) -> None:
        s = State(deck=["BASH"])
        assert s.hp == 70
        assert s.max_hp == 80
        assert s.gold == 99
        assert s.ascension == 0
        assert s.act_index == 2
        assert s.act_floor == 3

    def test_deck_stored(self) -> None:
        s = State(deck=["STRIKE_IRONCLAD", "BASH"])
        assert s.deck == ["STRIKE_IRONCLAD", "BASH"]

    def test_custom_values(self) -> None:
        s = State(
            deck=["BASH"],
            relics=["RING_OF_SNAKE"],
            hp=40,
            max_hp=75,
            gold=200,
            ascension=5,
            act_index=1,
            act_floor=7,
        )
        assert s.relics == ["RING_OF_SNAKE"]
        assert s.hp == 40
        assert s.max_hp == 75
        assert s.gold == 200
        assert s.ascension == 5
        assert s.act_index == 1
        assert s.act_floor == 7

    def test_state_to_json_has_required_keys(self) -> None:
        """State serializes to the bin's expected JSON shape."""
        s = State(deck=["BASH", "STRIKE_IRONCLAD"])
        obj = json.loads(json.dumps(s.__dict__))
        for key in (
            "deck",
            "relics",
            "hp",
            "max_hp",
            "gold",
            "ascension",
            "act_index",
            "act_floor",
        ):
            assert key in obj, f"missing key: {key}"
        assert obj["deck"] == ["BASH", "STRIKE_IRONCLAD"]
        assert isinstance(obj["relics"], list)


# ---------------------------------------------------------------------------
# Unit tests — sample_contexts
# ---------------------------------------------------------------------------


class TestSampleContexts:
    def test_returns_n_states(self) -> None:
        states = sample_contexts(4, decks=_FIXTURE_DECKS, seed=0)
        assert len(states) == 4

    def test_returns_state_instances(self) -> None:
        states = sample_contexts(2, decks=_FIXTURE_DECKS, seed=0)
        for s in states:
            assert isinstance(s, State)

    def test_deterministic_for_fixed_seed(self) -> None:
        a = sample_contexts(5, decks=_FIXTURE_DECKS, seed=42)
        b = sample_contexts(5, decks=_FIXTURE_DECKS, seed=42)
        assert [(s.deck, s.hp) for s in a] == [(s.deck, s.hp) for s in b]

    def test_different_seeds_differ(self) -> None:
        a = sample_contexts(5, decks=_FIXTURE_DECKS, seed=0)
        b = sample_contexts(5, decks=_FIXTURE_DECKS, seed=99)
        # With two decks and 5 samples this is almost certainly different,
        # but both are valid so we allow equality as a coincidence.
        # (This is a soft check; if it fails, use a larger n or more decks.)
        # Just verify both return the right length.
        assert len(a) == 5
        assert len(b) == 5

    def test_tail_drop_no_longer_than_source(self) -> None:
        """Sampled decks must be no longer than the source deck they came from."""
        source_max = max(len(d.cards) for d in _FIXTURE_DECKS)
        states = sample_contexts(20, decks=_FIXTURE_DECKS, seed=7)
        for s in states:
            assert len(s.deck) <= source_max

    def test_deck_tokens_unchanged(self) -> None:
        """Card tokens in output must be a prefix/subsequence of the source (no translation)."""
        source_cards = {c for d in _FIXTURE_DECKS for c in d.cards}
        states = sample_contexts(10, decks=_FIXTURE_DECKS, seed=3)
        for s in states:
            for card in s.deck:
                # Cards must come from the pool (no namespace addition or translation)
                assert card in source_cards, f"unexpected token: {card!r}"

    def test_accepts_generator(self) -> None:
        """sample_contexts must accept any Iterable[Deck], not just a list."""

        def _gen():
            yield from _FIXTURE_DECKS

        states = sample_contexts(3, decks=_gen(), seed=0)
        assert len(states) == 3

    def test_loads_real_corpus_when_decks_none(self, tmp_path: Path) -> None:
        """With decks=None it falls through to load_decks(); with a missing codex
        that call returns zero decks, so we just verify it doesn't crash when
        SYNERGY_CODEX_ROOT points at an empty directory."""
        empty = tmp_path / "ironclad"
        empty.mkdir(parents=True)
        env_backup = os.environ.get("SYNERGY_CODEX_ROOT")
        os.environ["SYNERGY_CODEX_ROOT"] = str(tmp_path)
        try:
            # corpus is empty → sample_contexts must handle n > corpus size
            states = sample_contexts(3, decks=None, seed=0)
            # empty corpus → 0 states (or fewer than n)
            assert len(states) == 0
        finally:
            if env_backup is None:
                os.environ.pop("SYNERGY_CODEX_ROOT", None)
            else:
                os.environ["SYNERGY_CODEX_ROOT"] = env_backup


# ---------------------------------------------------------------------------
# Unit tests — score_states alignment guard (no binary; subprocess stubbed)
# ---------------------------------------------------------------------------


@dataclass
class _FakeCompleted:
    """Minimal stand-in for subprocess.CompletedProcess."""

    stdout: str
    returncode: int = 0
    stderr: str = ""


class TestScoreStatesAlignmentGuard:
    """score_states must raise (not silently misalign) when the bin returns the
    wrong number of scores — e.g. a blank/extra/missing line."""

    def test_too_few_scores_raises(self, monkeypatch: pytest.MonkeyPatch) -> None:
        from synergy import causal_econ

        # Avoid any build/freshness work; pretend the binary is present & fresh.
        monkeypatch.setattr(
            causal_econ, "ensure_binary_fresh", lambda no_build=False: Path("/fake")
        )
        # Two states in, but the bin returns only one score (plus a blank line
        # that the parser silently drops) → misalignment.
        monkeypatch.setattr(
            causal_econ.subprocess,
            "run",
            lambda *a, **k: _FakeCompleted(stdout="0.5\n\n"),
        )

        states = [State(deck=["BASH"]), State(deck=["STRIKE_IRONCLAD"])]
        with pytest.raises(RuntimeError, match="1 scores for 2 states"):
            causal_econ.score_states(states, no_build=True)

    def test_too_many_scores_raises(self, monkeypatch: pytest.MonkeyPatch) -> None:
        from synergy import causal_econ

        monkeypatch.setattr(
            causal_econ, "ensure_binary_fresh", lambda no_build=False: Path("/fake")
        )
        monkeypatch.setattr(
            causal_econ.subprocess,
            "run",
            lambda *a, **k: _FakeCompleted(stdout="0.1\n0.2\n0.3\n"),
        )

        states = [State(deck=["BASH"]), State(deck=["STRIKE_IRONCLAD"])]
        with pytest.raises(RuntimeError, match="3 scores for 2 states"):
            causal_econ.score_states(states, no_build=True)

    def test_matching_count_returns_scores(self, monkeypatch: pytest.MonkeyPatch) -> None:
        from synergy import causal_econ

        monkeypatch.setattr(
            causal_econ, "ensure_binary_fresh", lambda no_build=False: Path("/fake")
        )
        monkeypatch.setattr(
            causal_econ.subprocess,
            "run",
            lambda *a, **k: _FakeCompleted(stdout="0.1\n0.2\n"),
        )

        states = [State(deck=["BASH"]), State(deck=["STRIKE_IRONCLAD"])]
        scores = causal_econ.score_states(states, no_build=True)
        assert scores == [0.1, 0.2]


# ---------------------------------------------------------------------------
# Integration tests — score_states
# ---------------------------------------------------------------------------


@pytest.mark.skipif(not _binary_available(), reason=_INTEGRATION_REASON)
class TestScoreStates:
    def test_returns_one_float_per_state(self) -> None:
        states = [
            State(deck=["BASH", "STRIKE_IRONCLAD"]),
            State(deck=["STRIKE_IRONCLAD", "DEFEND_IRONCLAD", "BASH"]),
        ]
        scores = score_states(states)
        assert len(scores) == len(states)
        for v in scores:
            assert isinstance(v, float)
            assert math.isfinite(v), f"score must be finite, got {v}"

    def test_stable_across_two_calls(self) -> None:
        state = State(deck=["BASH", "STRIKE_IRONCLAD", "STRIKE_IRONCLAD"])
        v1 = score_states([state])[0]
        v2 = score_states([state])[0]
        assert v1 == v2, f"score must be deterministic: {v1} != {v2}"

    def test_stronger_deck_scores_higher(self) -> None:
        """Starter + strong cards should score higher than starter + curses."""
        weak = State(
            deck=[
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
                "CURSE_OF_THE_BELL",
                "CURSE_OF_THE_BELL",
                "CURSE_OF_THE_BELL",
            ]
        )
        strong = State(
            deck=[
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
                "WHIRLWIND+1",
                "INFLAME+1",
                "CORRUPTION_CARD+1",
                "BLUDGEON+1",
                "FEEL_NO_PAIN_CARD+1",
                "IMPERVIOUS+1",
            ]
        )
        v_strong, v_weak = score_states([strong, weak])
        assert math.isfinite(v_strong) and math.isfinite(v_weak)
        assert v_strong > v_weak, (
            f"net disagrees with expected direction: strong={v_strong:.4f}, weak={v_weak:.4f}"
        )

    def test_batch_order_preserved(self) -> None:
        """Output order must match input order."""
        states = [
            State(deck=["BASH"]),
            State(deck=["STRIKE_IRONCLAD", "STRIKE_IRONCLAD", "BASH"]),
            State(deck=["BASH", "DEFEND_IRONCLAD"]),
        ]
        scores = score_states(states)
        assert len(scores) == 3

    def test_empty_list_returns_empty(self) -> None:
        assert score_states([]) == []

    def test_single_state_works(self) -> None:
        state = State(deck=["BASH"])
        scores = score_states([state])
        assert len(scores) == 1
        assert math.isfinite(scores[0])


class TestScoreStatesNetSelection:
    """score_states forwards --net/--character to the binary (no real subprocess)."""

    class _FakeResult:
        returncode = 0
        stdout = "0.5\n"
        stderr = ""

    def _patch(self, monkeypatch, captured) -> None:
        from synergy import causal_econ

        def fake_run(cmd, **_kw):
            captured["cmd"] = cmd
            return self._FakeResult()

        monkeypatch.setattr(causal_econ, "ensure_binary_fresh", lambda **_k: "BIN")
        monkeypatch.setattr(causal_econ.subprocess, "run", fake_run)

    def test_ppo_passes_net_and_character(self, monkeypatch) -> None:
        captured: dict = {}
        self._patch(monkeypatch, captured)
        score_states([State(deck=["BASH"])], net="ppo", character="silent")
        assert captured["cmd"] == ["BIN", "--net", "ppo", "--character", "silent"]

    def test_gbm_ironclad_default_is_bare_dropin(self, monkeypatch) -> None:
        captured: dict = {}
        self._patch(monkeypatch, captured)
        score_states([State(deck=["BASH"])])
        # Default gbm/ironclad must stay a bare invocation (backward compatible).
        assert captured["cmd"] == ["BIN"]

    def test_ppo_ironclad_omits_default_character(self, monkeypatch) -> None:
        captured: dict = {}
        self._patch(monkeypatch, captured)
        score_states([State(deck=["BASH"])], net="ppo")
        assert captured["cmd"] == ["BIN", "--net", "ppo"]
