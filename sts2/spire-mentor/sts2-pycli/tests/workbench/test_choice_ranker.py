"""Tests for the fast in-combat choice ranker (``workbench.choice_ranker``).

The cheap counterpart to ``workbench.decision_advisor`` for an in-combat
single-pick choice: one combat MCTS search ranks every option, instead of the
advisor's fork-and-play-out-the-act-per-option rollouts. Pure gating contracts
run without the engine; the search path drives a real ``sts2_rs.RunManager`` to
an in-combat choice and runs a low-sims search, so it needs the built wheel and
skips cleanly when one isn't reached for the seed/build.
"""

from __future__ import annotations

import random

import pytest
from fake_game import FakeGame
from workbench import choice_ranker as cr


# ── pure gating contracts (no engine) ─────────────────────────────────────────
class _FakeCS:
    def __init__(self, pc):
        self._pc = pc

    def pending_choice(self):
        return self._pc


class _FakeRm:
    """Minimal stand-in exercising the engine-free branches of is_rankable."""

    def __init__(self, phase, acts, pending=None):
        self._phase = phase
        self._acts = acts
        self._pending = pending

    def phase(self):
        return self._phase

    def available_actions(self):
        return self._acts

    def combat_state(self):
        return _FakeCS(self._pending) if self._phase == "combat" else None


_CHOICE_ACTS = [
    {"action": "combat_resolve_choice", "option": 0},
    {"action": "combat_resolve_choice", "option": 1},
]


def test_is_rankable_gating():
    assert cr.is_rankable(None) is False
    # Out of combat is never rankable (a single combat search can't model it).
    assert cr.is_rankable(_FakeRm("shop", _CHOICE_ACTS)) is False
    # Plain combat with no pending choice is not a choice.
    assert cr.is_rankable(_FakeRm("combat", [{"action": "end_turn"}], pending=None)) is False
    # Single-pick in-combat choice with two options → rankable.
    assert cr.is_rankable(_FakeRm("combat", _CHOICE_ACTS, pending={"max_select": 1})) is True
    # Multi-pick too: the engine decomposes it into incremental picks + a
    # FINALIZE, so the root children are ordinary rankable actions and each
    # ranking answers "best NEXT pick".
    assert cr.is_rankable(_FakeRm("combat", _CHOICE_ACTS, pending={"max_select": 3})) is True
    # A single-option choice is not a real decision.
    assert (
        cr.is_rankable(
            _FakeRm(
                "combat",
                [{"action": "combat_resolve_choice", "option": 0}],
                pending={"max_select": 1},
            )
        )
        is False
    )


def test_rank_off_choice_returns_error():
    out = cr.rank(_FakeRm("combat", [{"action": "end_turn"}], pending=None))
    assert "error" in out
    assert out["candidates"] == []


def test_best_action_none_when_not_rankable():
    """No search, no pick — the caller keeps its own default."""
    assert cr.best_action(_FakeRm("combat", [{"action": "end_turn"}], pending=None)) is None
    assert cr.best_action(_FakeRm("shop", _CHOICE_ACTS)) is None


# ── engine-backed search path ─────────────────────────────────────────────────
sts2_rs = pytest.importorskip("sts2_rs")

from run_policy import heuristic_run_action  # noqa: E402


def _drive_to_choice(seed, max_steps=6000):
    """Drive a fresh Ironclad run until a single-pick in-combat choice opens."""
    rm = sts2_rs.RunManager(
        string_seed=seed, character="Ironclad", ascension_level=0, neow_start=True
    )
    rng = random.Random(7)
    for _ in range(max_steps):
        if rm.is_over():
            return None
        if rm.phase() == "combat" and cr.is_rankable(rm):
            return rm
        acts = rm.available_actions()
        if not acts:
            return None
        action = rng.choice(acts) if rm.phase() == "combat" else heuristic_run_action(rm, acts, rng)
        rm.take_action(action)
    return None


def test_rank_in_combat_choice_scores_every_option():
    rm = _drive_to_choice("CHOICEHUNT")
    if rm is None:
        pytest.skip("no in-combat single-pick choice reached for this seed/build")
    result = cr.rank(rm, {"sims": 80, "seed": 1})
    assert "error" not in result
    assert result["decision"] == "card_select"
    cands = result["candidates"]
    assert len(cands) >= 2
    # Every option is a choice key the frontend / decision advisor joins on.
    assert all(c["key"].startswith("choice:") for c in cands)
    # Exactly one best, and the search visited the options.
    assert sum(c["is_best"] for c in cands) == 1
    assert any((c["visits"] or 0) > 0 for c in cands)
    for c in cands:
        assert {"key", "label", "win_prob", "q", "visits", "is_best"} <= c.keys()
        if c["win_prob"] is not None:
            assert 0.0 <= c["win_prob"] <= 1.0


def test_rank_does_not_mutate_the_run():
    rm = _drive_to_choice("CHOICEHUNT")
    if rm is None:
        pytest.skip("no in-combat single-pick choice reached for this seed/build")
    before = [a for a in rm.available_actions()]
    cr.rank(rm, {"sims": 40, "seed": 2})
    assert rm.phase() == "combat"
    assert [a for a in rm.available_actions()] == before


# ── through the session ───────────────────────────────────────────────────────
class _NativeGame(FakeGame):
    """A backend whose native run is a real, parked ``RunManager``."""

    def __init__(self, rm):
        super().__init__()
        self._rm = rm

    def native_run(self):
        return self._rm


def test_session_rank_choice_needs_a_native_run():
    """Without a native run the session answers with the error payload the UI
    shows (not a 500), naming the Rust engine."""
    from workbench.branching import BranchingLiveSession

    sess = BranchingLiveSession(logs_dir="/tmp", backend="rust")
    sess.game = FakeGame()
    sess._active_id = 0
    sess._nodes[0] = type("_N", (), {"state": {"decision": "card_select"}})()
    out = sess.rank_choice({})
    assert out is not None and "Rust" in out["error"] and out["candidates"] == []


def test_session_rank_choice_runs_on_in_combat_choice():
    from workbench.branching import BranchingLiveSession

    rm = _drive_to_choice("CHOICEHUNT")
    if rm is None:
        pytest.skip("no in-combat single-pick choice reached for this seed/build")
    sess = BranchingLiveSession(logs_dir="/tmp", backend="rust")
    sess.game = _NativeGame(rm)
    sess._active_id = 0
    sess._nodes[0] = type("_N", (), {"state": {"decision": "card_select"}})()
    out = sess.rank_choice({"sims": 60, "seed": 3})
    assert out is not None and "error" not in out
    assert len(out["candidates"]) >= 2
    assert sum(c["is_best"] for c in out["candidates"]) == 1


def test_best_action_is_the_starred_candidate():
    """``best_action`` and the chips must name the same option.

    They are one contract: the panel stars ``is_best`` and "play best" plays
    ``best_action``. The search is deterministic for a given config, so passing
    the config the chips used makes the two agree by construction.
    """
    rm = _drive_to_choice("CHOICEHUNT")
    if rm is None:
        pytest.skip("no in-combat single-pick choice reached for this seed/build")
    config = {"sims": 80, "seed": 4}
    ranked = cr.rank(rm, config)
    starred = next(c["key"] for c in ranked["candidates"] if c["is_best"])
    action = cr.best_action(rm, config)
    assert action is not None
    assert action["action"] == "combat_resolve_choice"
    opt = action.get("option")
    assert starred == ("choice:skip" if opt is None else f"choice:{opt}")


# ── the "play best (G)" recommendation on an in-combat choice ─────────────────
def test_session_recommends_the_searched_choice_not_option_zero():
    """Regression for bug ``wb-31c2d79f995fe1a6``.

    Neither run-level brain answers in combat, so ``recommended_action`` used to
    return ``None`` here; the endpoint 409'd and the frontend blind-picked the
    first option (card 0) while the panel starred another card. It must now hand
    back the searched pick, mapped to the live endpoint's verb.
    """
    from workbench.branching import BranchingLiveSession

    rm = _drive_to_choice("CHOICEHUNT")
    if rm is None:
        pytest.skip("no in-combat single-pick choice reached for this seed/build")
    sess = BranchingLiveSession(logs_dir="/tmp", backend="rust")
    sess._active_id = 0  # a started session; only the rm matters below
    sess.game = _NativeGame(rm)
    rec = sess.recommended_action()
    assert rec is not None, "in-combat choice must be recommendable"
    assert rec["policy"] == "combat_mcts"
    assert rec["action"] in ("select_cards", "skip_select")
    # …and it is the option the search starred, at the chips' own config.
    starred = next(c["key"] for c in cr.rank(rm, {}).get("candidates", []) if c["is_best"])
    played = "choice:skip" if rec["action"] == "skip_select" else f"choice:{rec['args']['indices']}"
    assert played == starred


def _drive_to_multipick(seed="MULTIPICK"):
    """A live session parked on a real MULTI-pick in-combat choice.

    Purity ("exhaust up to 3 cards in your hand", min 0 / max 3 / can_skip) is
    colorless, so it can be forced into any deck; the engine decomposes it into
    incremental picks plus a FINALIZE.
    """
    import tempfile

    from workbench.branching import BranchingLiveSession

    sess = BranchingLiveSession(logs_dir=tempfile.mkdtemp(), backend="rust")
    out = sess.start("Ironclad", seed, 0)
    state = out.get("state", out)
    for _ in range(10):
        sess.game.rm.force_add_card(card_id="PURITY")
    while state.get("decision") != "combat_play":
        rec = sess.recommended_action()
        if rec is None:
            return None
        state = sess.apply(rec["action"], rec["args"])["state"]
    idx = next((i for i, c in enumerate(state.get("hand") or []) if c["id"] == "PURITY"), None)
    if idx is None:
        return None
    state = sess.apply("play_card", {"card_index": idx})["state"]
    if state.get("decision") != "card_select" or int(state.get("max_select", 1)) <= 1:
        return None
    return sess


def test_multipick_choice_is_recommended_pick_by_pick():
    """A multi-pick choice must be searched too, and run to completion.

    It used to be scoped out of the ranker, so it fell through to the frontend's
    blind first-option fallback — which exhausted whatever card sat first in
    hand AND left the engine mid-accumulation. Each recommendation is now the
    best next pick, until the search chooses FINALIZE and the choice closes.
    """
    sess = _drive_to_multipick()
    if sess is None:
        pytest.skip("no multi-pick in-combat choice reached for this seed/build")
    state = sess._active().state
    assert state["context"] == "exhaust_from_hand"
    assert cr.is_rankable(sess.game.rm) is True

    picks = []
    for _ in range(6):
        rec = sess.recommended_action()
        assert rec is not None, "a legal multi-pick step must stay recommendable"
        assert rec["policy"] == "combat_mcts"
        picks.append(rec["action"])
        state = sess.apply(rec["action"], rec["args"])["state"]
        if state.get("decision") != "card_select":
            break
    # It terminated by finalizing, not by running out of the loop.
    assert picks[-1] == "skip_select"
    assert state["decision"] == "combat_play"


def test_session_recommend_skips_combat_choice_without_a_run_manager():
    """The C# backend has no in-process RunManager — fall through, don't crash."""
    from workbench.branching import BranchingLiveSession

    sess = BranchingLiveSession(logs_dir="/tmp", backend="rust")
    sess.game = FakeGame()  # no native run
    assert sess._combat_choice_recommend() is None
