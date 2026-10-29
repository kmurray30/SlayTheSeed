"""The run-driver seam: every backend is a ``GameBackend``, and the session
branches on what a backend *returns*, never on what attributes it has."""

from __future__ import annotations

import pytest
from fake_game import FakeGame
from workbench.backends import (
    DuckTypedBackend,
    GameBackend,
    UnsupportedOperation,
    adapt_backend,
)
from workbench.branching import BranchingLiveSession


class _FiveVerbs:
    """A bare object speaking only the C# verbs — what ``sts2_client.Game`` is."""

    def __init__(self):
        self.closed = False
        self.extra_calls = 0

    def start(self, *, character, seed, ascension, act1="overgrowth"):
        return {"decision": "map_select", "seed": seed}

    def act(self, action, **args):
        return {"decision": "combat_play"}

    def get_state(self):
        return {"decision": "combat_play"}

    def get_map(self):
        return {"rows": []}

    def close(self):
        self.closed = True

    def set_player(self, **kw):  # a C#-only extra the parity walks use
        self.extra_calls += 1
        return {"ok": True}


def test_adapt_backend_wraps_duck_objects_and_passes_backends_through():
    duck = _FiveVerbs()
    wrapped = adapt_backend(duck)
    assert isinstance(wrapped, DuckTypedBackend)
    fake = FakeGame()
    assert adapt_backend(fake) is fake


def test_duck_adapter_reports_capabilities_honestly():
    g = adapt_backend(_FiveVerbs())
    assert g.native_run() is None
    assert g.combat_state() is None
    assert g.recommended_action() is None
    assert g.card_play_log() == [] and g.combat_log() == []
    g.set_record_card_plays(True)  # accepted as a no-op
    g.set_record_combat_log(True)
    with pytest.raises(UnsupportedOperation):
        g.start_faithful(character="Ironclad", seed=1)


def test_duck_adapter_forwards_the_verbs_and_the_extras():
    duck = _FiveVerbs()
    g = adapt_backend(duck)
    assert g.start(character="Ironclad", seed="s", ascension=0)["seed"] == "s"
    assert g.get_map(first_col=3) == {"rows": []}  # first_col ignored, not a TypeError
    assert g.set_player(hp=1) == {"ok": True}  # falls through to the wrapped object
    g.close()
    assert duck.closed


def test_session_spawns_an_adapted_backend(tmp_path, monkeypatch):
    from workbench import branching

    monkeypatch.setattr(branching, "Game", _FiveVerbs)
    sess = BranchingLiveSession(logs_dir=str(tmp_path), backend="csharp")
    sess.start("Ironclad", "s", 0)
    assert isinstance(sess.game, GameBackend)
    assert sess._native() is None
    with pytest.raises(RuntimeError, match="requires the rust backend"):
        sess._require_native("run-mode autoplay")


def test_session_advisors_answer_without_a_native_run(tmp_path, monkeypatch):
    from workbench import branching

    monkeypatch.setattr(branching, "Game", FakeGame)
    sess = BranchingLiveSession(logs_dir=str(tmp_path), backend="csharp")
    sess.start("Ironclad", "s", 0)
    node = sess._nodes[sess._active_id]
    for decision, call in (
        ("card_reward", lambda: sess.advise_card_reward({})),
        ("card_select", lambda: sess.rank_choice({})),
        ("shop", lambda: sess.advise_decision({})),
    ):
        node.state["decision"] = decision
        out = call()
        assert out is not None and "Rust" in out["error"] and out["candidates"] == []
    assert sess.recommended_action() is None or "action" in sess.recommended_action()


class _OldWheelRun:
    """An in-process ``RunManager`` from an ``sts2_rs`` build predating
    ``reseed_run`` — everything the advisors fork on is missing."""


class _OldWheelBackend(GameBackend):
    """A rust-shaped backend handing out a run the advisors cannot fork."""

    def start(self, *, character, seed, ascension, act1="overgrowth"):
        return {"decision": "map_select"}

    def act(self, action, **args):
        return {"decision": "map_select"}

    def get_state(self):
        return {"decision": "map_select"}

    def get_map(self, first_col=None):
        return {"rows": []}

    def close(self):
        pass

    def native_run(self):
        return _OldWheelRun()


def test_session_advisors_name_the_stale_wheel(tmp_path, monkeypatch):
    """A pre-``reseed_run`` wheel gets the actionable error, not an
    ``AttributeError`` from deep inside the advisor (a 500 in the route)."""
    from workbench import branching

    monkeypatch.setattr(branching, "Game", FakeGame)
    sess = BranchingLiveSession(logs_dir=str(tmp_path), backend="csharp")
    sess.start("Ironclad", "s", 0)
    sess.game = _OldWheelBackend()
    node = sess._nodes[sess._active_id]
    for decision, call in (
        ("card_reward", lambda: sess.advise_card_reward({})),
        ("shop", lambda: sess.advise_decision({})),
    ):
        node.state["decision"] = decision
        out = call()
        assert out is not None
        assert "rebuild the sts2_rs wheel" in out["error"]
        assert out["candidates"] == []
