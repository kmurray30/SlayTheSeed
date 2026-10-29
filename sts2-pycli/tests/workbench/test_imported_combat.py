"""Tests for the combat lane of the live-state import.

The backend's contract is narrow and load-bearing: given a wire dict, produce
THAT board, and produce it the same way every time — ``BranchingLiveSession``
re-materializes on every seek and replays the recorded line onto the result, so
a backend that drifts between materializations silently forks the branch tree.
"""

from __future__ import annotations

import pytest

pytest.importorskip("sts2_rs", reason="the combat import needs the sts2_rs extension")

from workbench.backends.imported_combat import ImportedCombatBackend
from workbench.branching import BranchingLiveSession

DECK = ["STRIKE_IRONCLAD"] * 5 + ["DEFEND_IRONCLAD"] * 4 + ["BASH"]


@pytest.fixture
def wire():
    """An engine wire dict — the shape ``state_import`` hands the backend."""
    import sts2_rs

    cs = sts2_rs.build_scenario_state(
        seed=7,
        character="Ironclad",
        deck=DECK,
        hp=63,
        max_hp=80,
        relics=["BURNING_BLOOD"],
        encounter="MAWLER_NORMAL",
        ascension=0,
        potions=[],
    )
    return sts2_rs.state_to_dict(cs)


def test_rebuilds_the_exact_board(wire):
    be = ImportedCombatBackend(wire, "Ironclad")
    state = be.start()

    assert state["decision"] == "combat_play"
    assert state["player"]["hp"] == 63
    assert state["player"]["max_hp"] == 80
    assert len(state["hand"]) == len(wire["deck"]["hand"])
    # Draw ORDER, not just contents: the whole point of importing a board
    # rather than rebuilding a scenario is that the next card is known.
    import sts2_rs

    rebuilt = sts2_rs.state_to_dict(sts2_rs.state_from_dict(wire))
    assert [c["id"] for c in rebuilt["deck"]["draw_pile"]] == [
        c["id"] for c in wire["deck"]["draw_pile"]
    ]
    assert be.combat_state() is not None


def test_materialize_is_deterministic(wire):
    """Two materializations must be the same board — `seek` depends on it."""
    import sts2_rs

    be = ImportedCombatBackend(wire, "Ironclad")
    first = sts2_rs.state_to_dict(be.materialize())
    second = sts2_rs.state_to_dict(be.materialize())
    assert first == second


def test_a_caller_mutating_its_dict_cannot_change_the_import(wire):
    be = ImportedCombatBackend(wire, "Ironclad")
    wire["player"]["hp"] = 1
    assert be.start()["player"]["hp"] == 63


def test_plays_and_rejects_like_every_other_backend(wire):
    be = ImportedCombatBackend(wire, "Ironclad")
    state = be.start()
    energy = state["energy"]

    played = be.act("play_card", card_index=0, target_index=0)
    assert played.get("type") != "error"
    assert played["energy"] < energy

    assert be.act("play_card", card_index=99, target_index=0).get("type") == "error"
    assert be.act("select_map_node", col=0).get("type") == "error"
    # Combat-only: there is no run behind an imported board.
    assert be.get_map() == {}


def test_session_branches_and_seeks_back(wire, tmp_path):
    """The imported board is an ordinary session: hint, play, rewind."""
    sess = BranchingLiveSession.from_imported_combat(
        logs_dir=str(tmp_path / "logs"), loc={}, wire=wire, character="Ironclad"
    )
    start = sess.start("Ironclad", "imported", 0)
    assert start["decision"] == "combat_play"
    root_energy = start["energy"]

    hint = sess.hint({"sims": 8, "c_puct": 1.0, "depth": 10, "seed": 1})
    assert hint is not None and "scores" in hint

    resp = sess.apply("play_card", {"card_index": 0, "target_index": 0})
    assert resp["error"] is None
    assert resp["state"]["energy"] == root_energy - 1

    back = sess.seek(sess._root_id)
    assert back["energy"] == root_energy
    assert back["player"]["hp"] == 63
