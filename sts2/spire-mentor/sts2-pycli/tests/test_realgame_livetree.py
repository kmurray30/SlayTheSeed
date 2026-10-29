"""LiveRunTree — the advisor-side branch tree of one live run (rewind lane).

Grown from the offline smoke harness that validated the lane before it
shipped (2026-08-21); committed so the tree's contracts — node/timeline wire
shape, snapshot stamping, fork-on-rewind, run-identity resets, the
request-id label correlation, and the turn-undo seek plans — stay pinned.
"""

from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "python"))

from realgame_livetree import LiveRunTree

ENV: dict[str, Any] = {
    "seed": "S1",
    "run_state": {"character": "IRONCLAD"},
    "act": 1,
    "floor": 1,
}


def _entry_snapshot(tree: LiveRunTree, snapshot_id: str, **extra: Any) -> None:
    tree.on_snapshot_saved(
        {
            "type": "snapshot_saved",
            "snapshot_id": snapshot_id,
            "floor": 1,
            "act": 1,
            "post_combat": False,
            **extra,
        }
    )


def _nodes_by_decision(tree: LiveRunTree, decision: str) -> list[dict[str, Any]]:
    return [n for n in tree.timeline()["nodes"] if n["decision"] == decision]


class TestShape:
    def test_root_then_room_entry_then_decisions(self) -> None:
        tree = LiveRunTree()
        _entry_snapshot(tree, "111-0")
        tree.on_decision({**ENV}, "combat_action")
        t = tree.timeline()
        decisions = [n["decision"] for n in t["nodes"]]
        assert decisions == ["root", "room_entry", "combat_action"]
        assert t["active_path"] == [n["id"] for n in t["nodes"]]
        assert t["active_leaf"] == t["nodes"][-1]["id"]

    def test_room_entry_is_seekable_decision_is_not(self) -> None:
        tree = LiveRunTree()
        _entry_snapshot(tree, "111-0")
        tree.on_decision({**ENV}, "card_reward")
        room = _nodes_by_decision(tree, "room_entry")[0]
        reward = _nodes_by_decision(tree, "card_reward")[0]
        assert room["seekable"] and room["snapshot_id"] == "111-0"
        assert not reward["seekable"] and reward["snapshot_id"] is None

    def test_post_combat_snapshot_stamps_the_active_node(self) -> None:
        tree = LiveRunTree()
        _entry_snapshot(tree, "111-0")
        tree.on_decision({**ENV}, "combat_action")
        tree.on_snapshot_saved(
            {"type": "snapshot_saved", "snapshot_id": "111-1", "post_combat": True}
        )
        node = _nodes_by_decision(tree, "combat_action")[0]
        assert node["snapshot_id"] == "111-1" and node["seekable"]


class TestRunIdentity:
    def test_new_seed_resets_the_tree(self) -> None:
        tree = LiveRunTree()
        _entry_snapshot(tree, "111-0")
        tree.on_decision({**ENV}, "combat_action")
        tree.on_decision({**ENV, "seed": "S2"}, "map_select")
        t = tree.timeline()
        assert [n["decision"] for n in t["nodes"]] == ["root", "map_select"]

    def test_first_sighting_of_start_time_keeps_existing_nodes(self) -> None:
        # Resetting on the FIRST start_time sighting discarded the room-entry
        # node the same snapshot had just added (shipped bug, fixed same day).
        tree = LiveRunTree()
        _entry_snapshot(tree, "111-0", run_start_time=111)
        assert len(_nodes_by_decision(tree, "room_entry")) == 1
        _entry_snapshot(tree, "222-0", run_start_time=222)
        t = tree.timeline()
        assert [n["decision"] for n in t["nodes"]] == ["root", "room_entry"]
        assert t["nodes"][1]["snapshot_id"] == "222-0"


class TestLabelCorrelation:
    def test_action_refines_its_own_node_by_request_id(self) -> None:
        """The label race: the action report for decision N may arrive AFTER
        decision N+1 appended a node. The request-id correlation must put
        the play on N, never on N+1."""
        tree = LiveRunTree()
        _entry_snapshot(tree, "111-0")
        tree.on_decision({**ENV, "request_id": "r1"}, "combat_action")
        tree.on_decision({**ENV, "request_id": "r2"}, "combat_action")
        tree.on_human_action({"detail": "Strike -> Cultist", "advice_request_id": "r1"})
        first, second = _nodes_by_decision(tree, "combat_action")
        assert first["label"] == "f1 · Strike -> Cultist"
        assert second["label"] == "f1 · combat action"

    def test_action_without_request_id_refines_newest_unlabeled(self) -> None:
        tree = LiveRunTree()
        _entry_snapshot(tree, "111-0")
        tree.on_decision({**ENV}, "combat_action")
        tree.on_human_action({"detail": "Defend"})
        node = _nodes_by_decision(tree, "combat_action")[0]
        assert node["label"] == "f1 · Defend"

    def test_action_never_relabels_a_room_entry(self) -> None:
        tree = LiveRunTree()
        _entry_snapshot(tree, "111-0")
        tree.on_human_action({"detail": "Strike"})
        room = _nodes_by_decision(tree, "room_entry")[0]
        assert room["label"] == "floor 1"


class TestRewinds:
    def test_restore_moves_active_and_next_decision_forks(self) -> None:
        tree = LiveRunTree()
        _entry_snapshot(tree, "111-0")
        tree.on_decision({**ENV}, "combat_action")
        room = _nodes_by_decision(tree, "room_entry")[0]
        tree.on_snapshot_restored({"type": "snapshot_restored", "snapshot_id": "111-0"})
        assert tree.timeline()["active_leaf"] == room["id"]
        tree.on_decision({**ENV}, "combat_action")
        assert len(tree.timeline()["nodes"][room["id"]]["child_ids"]) == 2 or (
            len([n for n in tree.timeline()["nodes"] if n["parent_id"] == room["id"]]) == 2
        )


class TestTurnUndo:
    def _combat(self, tree: LiveRunTree, turn: int, request_id: str) -> None:
        tree.on_decision({**ENV, "turn": turn, "request_id": request_id}, "combat_action")

    def test_combat_nodes_carry_turns_and_are_seekable(self) -> None:
        tree = LiveRunTree()
        _entry_snapshot(tree, "111-0")
        self._combat(tree, 1, "r1")
        self._combat(tree, 2, "r2")
        turns = [n["turn"] for n in _nodes_by_decision(tree, "combat_action")]
        assert turns == [1, 2]
        assert all(n["seekable"] for n in _nodes_by_decision(tree, "combat_action"))

    def test_seek_plan_prefers_turn_over_snapshot(self) -> None:
        """A fight's last node carries the POST-combat snapshot, but its
        label is a mid-fight play — the tape replay is what matches it."""
        tree = LiveRunTree()
        _entry_snapshot(tree, "111-0")
        self._combat(tree, 1, "r1")
        tree.on_snapshot_saved(
            {"type": "snapshot_saved", "snapshot_id": "111-1", "post_combat": True}
        )
        node = _nodes_by_decision(tree, "combat_action")[0]
        assert tree.seek_plan(node["id"]) == {"action": "undo_turn", "turn": 1}
        room = _nodes_by_decision(tree, "room_entry")[0]
        assert tree.seek_plan(room["id"]) == {
            "action": "restore_snapshot",
            "snapshot_id": "111-0",
        }

    def test_post_combat_tape_stamps_the_fights_nodes(self) -> None:
        tree = LiveRunTree()
        _entry_snapshot(tree, "111-0")
        self._combat(tree, 1, "r1")
        self._combat(tree, 2, "r2")
        tree.on_snapshot_saved(
            {
                "type": "snapshot_saved",
                "snapshot_id": "111-1",
                "post_combat": True,
                "has_tape": True,
            }
        )
        for node in _nodes_by_decision(tree, "combat_action"):
            assert node["tape_id"] == "111-1"
            plan = tree.seek_plan(node["id"])
            assert plan is not None and plan["tape_id"] == "111-1"

    def test_no_tape_means_no_tape_id(self) -> None:
        tree = LiveRunTree()
        _entry_snapshot(tree, "111-0")
        self._combat(tree, 1, "r1")
        tree.on_snapshot_saved(
            {"type": "snapshot_saved", "snapshot_id": "111-1", "post_combat": True}
        )
        node = _nodes_by_decision(tree, "combat_action")[0]
        assert node["tape_id"] is None
        assert tree.seek_plan(node["id"]) == {"action": "undo_turn", "turn": 1}

    def test_combat_undone_moves_active_before_the_turn(self) -> None:
        tree = LiveRunTree()
        _entry_snapshot(tree, "111-0")
        self._combat(tree, 1, "r1")
        self._combat(tree, 2, "r2")
        self._combat(tree, 2, "r3")
        tree.on_combat_undone({"type": "combat_undone", "turn": 2})
        # Start of turn 2 = right after the last turn-1 node.
        turn1 = [n for n in _nodes_by_decision(tree, "combat_action") if n["turn"] == 1]
        assert tree.timeline()["active_leaf"] == turn1[-1]["id"]

    def test_combat_undone_to_turn_one_lands_on_room_entry(self) -> None:
        tree = LiveRunTree()
        _entry_snapshot(tree, "111-0")
        self._combat(tree, 1, "r1")
        tree.on_combat_undone({"type": "combat_undone", "turn": 1})
        room = _nodes_by_decision(tree, "room_entry")[0]
        assert tree.timeline()["active_leaf"] == room["id"]

    def test_combat_undone_with_tape_id_targets_that_fight(self) -> None:
        tree = LiveRunTree()
        _entry_snapshot(tree, "111-0")
        self._combat(tree, 1, "r1")
        self._combat(tree, 2, "r2")
        tree.on_snapshot_saved(
            {
                "type": "snapshot_saved",
                "snapshot_id": "111-1",
                "post_combat": True,
                "has_tape": True,
            }
        )
        _entry_snapshot(tree, "111-2")  # the run moved on to the next room
        tree.on_combat_undone({"type": "combat_undone", "turn": 2, "tape_id": "111-1"})
        turn1 = [n for n in _nodes_by_decision(tree, "combat_action") if n["turn"] == 1]
        assert tree.timeline()["active_leaf"] == turn1[-1]["id"]


class TestCombatLog:
    """The mod ships the game's own combat-log rows on each decision.

    The advisor stores them RAW: it has no localization, so turning ids into
    names is the workbench server's job when it serves the tree. What the tree
    must guarantee is that each node holds only ITS decision's rows, plus the
    character id the renderer needs for the rows that name no creature.
    """

    def test_decision_node_keeps_this_decisions_rows(self) -> None:
        tree = LiveRunTree()
        _entry_snapshot(tree, "111-0")
        first = [{"kind": "damage", "text": "IRONCLAD dealt 6 damage to NIBBIT"}]
        second = [{"kind": "block", "text": "IRONCLAD gained 5 block"}]
        tree.on_decision({**ENV, "combat_log": first}, "combat_action")
        tree.on_decision({**ENV, "combat_log": second}, "combat_action")

        nodes = _nodes_by_decision(tree, "combat_action")
        assert [n["combat_log_raw"] for n in nodes] == [first, second]
        assert all(n["player_id"] == "IRONCLAD" for n in nodes)

    def test_a_decision_without_rows_carries_an_empty_list(self) -> None:
        # An older mod sends no `combat_log` at all; the node must still be
        # well-formed rather than missing the key the renderer reads.
        tree = LiveRunTree()
        _entry_snapshot(tree, "111-0")
        tree.on_decision({**ENV}, "combat_action")
        assert _nodes_by_decision(tree, "combat_action")[0]["combat_log_raw"] == []


class TestLogIdx:
    def test_tree_node_carries_log_idx(self) -> None:
        tree = LiveRunTree()
        tree.on_decision({**ENV}, "combat_action", log_idx=4)
        tree.on_decision({**ENV, "floor": 2}, "map_select")
        combat = _nodes_by_decision(tree, "combat_action")[0]
        mapsel = _nodes_by_decision(tree, "map_select")[0]
        assert combat["log_idx"] == 4
        assert tree.log_idx(combat["id"]) == 4
        assert tree.log_idx(mapsel["id"]) is None
        assert tree.log_idx("x1") is None and tree.log_idx(999) is None
        assert all("log_idx" in n for n in tree.timeline()["nodes"])
