"""Tests for the combat-only play-from-here backend + session."""

from __future__ import annotations

import pytest

pytest.importorskip("sts2_rs", reason="combat replay requires the sts2_rs extension")

from workbench.backends.combat_replay import CombatReplayBackend
from workbench.branching import BranchingLiveSession
from workbench.record import record_fixture
from workbench.timeline import TimelineStore

_TINY_FIXTURE = """\
schema = 2

[provenance]
encounter = "ENCOUNTER.VANTOM_BOSS"
room_type = "boss"

[player]
class = "Ironclad"
hp = 60
max_hp = 70
deck = ["STRIKE_IRONCLAD", "STRIKE_IRONCLAD", "DEFEND_IRONCLAD", "DEFEND_IRONCLAD", "BASH"]
relics = ["BURNING_BLOOD"]
potions = []

[[monsters]]
kind = "VANTOM"
"""


@pytest.fixture
def fixture_path(tmp_path):
    fx = tmp_path / "tiny_vantom_boss.toml"
    fx.write_text(_TINY_FIXTURE)
    return str(fx)


def test_backend_start_play_and_terminal(fixture_path):
    be = CombatReplayBackend(fixture_path, prefix_actions=[], character="Ironclad")
    state = be.start()
    assert state["decision"] == "combat_play"
    assert state["player"]["name"] == "Ironclad"
    assert state["hand"], "combat should start with a drawn hand"
    assert state["enemies"][0]["name"] == "Vantom"
    # The native combat state is exposed for hints while the fight is live.
    assert be.combat_state() is not None

    # Playing a 1-cost card spends energy and stays in combat.
    e0 = state["energy"]
    played = be.act("play_card", card_index=0, target_index=0)
    assert played.get("type") != "error"
    assert played["energy"] == e0 - 1

    # An out-of-range card is rejected with an error dict (never raises).
    bad = be.act("play_card", card_index=99, target_index=0)
    assert bad.get("type") == "error"

    # An unsupported verb is rejected too.
    assert be.act("select_map_node", col=0).get("type") == "error"


def test_session_from_replay_branches_and_hints(fixture_path, tmp_path):
    # Record a short run so we have a log with fixture_path + native actions.
    log = record_fixture(fixture_path, out_dir=str(tmp_path / "logs"), sims=8, seed=1, max_steps=20)
    store = TimelineStore.load_jsonl(log)
    assert store.fixture_path() is not None

    # Play from the very first decision (no prefix) — branch tree + hints work.
    sess = BranchingLiveSession.from_replay(
        logs_dir=str(tmp_path / "logs"),
        loc={},
        fixture_path=store.fixture_path(),
        prefix_actions=store.native_actions_before(0),
        character=store.character(),
    )
    start = sess.start(store.character(), "replay", 0)
    assert start["decision"] == "combat_play"
    root_energy = start["energy"]

    # A hint runs natively against the live combat state.
    hint = sess.hint({"sims": 8, "c_puct": 1.0, "depth": 10, "seed": 1})
    assert hint is not None and "scores" in hint

    # Play a card, then seek back to the root — replay is deterministic.
    resp = sess.apply("play_card", {"card_index": 0, "target_index": 0})
    assert resp["error"] is None
    assert resp["state"]["energy"] == root_energy - 1
    back = sess.seek(sess._root_id)
    assert back["energy"] == root_energy
