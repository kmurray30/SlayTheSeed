"""Live-game branch tree for the advisor's control plane (rewind lane).

A thin tree over the events the advisor already sees — one node per distinct
decision the mod requested advice for, stamped with the mod's snapshot ids
(``snapshot_saved``) so the workbench's ``BranchTree.tsx`` can offer the
seekable ones as rewind targets. A ``snapshot_restored`` moves the active
pointer back to the node holding that snapshot; the next decision then forks
a child there, which is exactly the fork the tidy-tree draws.

Deliberately NOT :class:`workbench.branching.BranchingLiveSession`: that
class owns and drives a deterministic engine backend (its ``seek`` is
replay-from-scratch), while this one only *observes* a human-driven live
game and delegates seeks to the mod's in-process snapshot restore.

``timeline()`` returns the exact ``{nodes, active_path, active_leaf}`` wire
shape the workbench's live views consume (``LiveNode``/``LiveTimeline`` in
``web/src/api.ts``), with two additive fields per node: ``snapshot_id``
(str | None) and ``seekable`` (bool).
"""

from __future__ import annotations

import threading
from typing import Any


class LiveRunTree:
    """Thread-safe branch tree of one live run. All entry points may be
    called from the advisor's socket loop or the control plane's HTTP
    threads."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._reset_locked(run_key=None)

    # -- lifecycle -----------------------------------------------------

    def _reset_locked(self, run_key: Any, start_time: Any = None) -> None:
        # Two independent run identities, because the two event sources carry
        # different fields: decision envelopes have (character, seed) but no
        # start_time; the mod's snapshot events have run_start_time but no
        # run_state. A change in EITHER known identity means a new run; a
        # first sighting (None -> value) just records it -- resetting there
        # discarded the room-entry node the first snapshot had already added.
        self._run_key = run_key
        self._start_time = start_time
        self._nodes: dict[int, dict[str, Any]] = {}
        self._next_id = 0
        self._active: int | None = None
        self._pending_label: int | None = None
        root = self._new_node_locked(parent=None, decision="root", label="run start")
        root["seekable"] = False
        self._active = root["id"]

    def _new_node_locked(
        self,
        parent: int | None,
        decision: str,
        label: str,
        floor: Any = None,
        act: Any = None,
    ) -> dict[str, Any]:
        node = {
            "id": self._next_id,
            "parent_id": parent,
            "idx": 0 if parent is None else self._nodes[parent]["idx"] + 1,
            "decision": decision,
            "label": label,
            "details": [],
            "error": False,
            "child_ids": [],
            "floor": floor,
            "act": act,
            "snapshot_id": None,
            "seekable": False,
            "turn": None,
            "tape_id": None,
            "log_idx": None,
        }
        self._next_id += 1
        self._nodes[node["id"]] = node
        if parent is not None:
            self._nodes[parent]["child_ids"].append(node["id"])
        return node

    def _maybe_switch_run_locked(self, run_key: Any) -> None:
        if run_key is None:
            return
        if self._run_key is None:
            self._run_key = run_key
        elif run_key != self._run_key:
            self._reset_locked(run_key)

    def _maybe_switch_start_time_locked(self, start_time: Any) -> None:
        if start_time is None:
            return
        if self._start_time is None:
            self._start_time = start_time
        elif start_time != self._start_time:
            self._reset_locked(None, start_time)

    @staticmethod
    def _run_key_of(msg: dict[str, Any]) -> Any:
        rs = msg.get("run_state") or {}
        seed = msg.get("seed")
        character = rs.get("character")
        return (character, seed) if character and seed else None

    # -- events (called from the advisor loop) -------------------------

    def on_decision(self, msg: dict[str, Any], advice_for: str, log_idx: int | None = None) -> None:
        """A new advised decision: append a node under the active pointer.

        ``log_idx`` is the replay step index of the decision's ``state`` line
        in the run log (``WorkbenchRunLogger.log_decision``): the node -> step
        join the live coach's "open in workbench" links need, since node ids
        and step indices diverge (root, room entries, rewind forks).
        """
        with self._lock:
            self._maybe_switch_run_locked(self._run_key_of(msg))
            label = advice_for.replace("_", " ")
            floor = msg.get("floor")
            if floor is not None:
                label = f"f{floor} · {label}"
            node = self._new_node_locked(
                parent=self._active,
                decision=advice_for,
                label=label,
                floor=floor,
                act=msg.get("act"),
            )
            turn = msg.get("turn")
            if advice_for == "combat_action" and isinstance(turn, int) and turn >= 1:
                # An undo_turn target: the mod can replay this combat's tape
                # to the start of this node's turn (live tape while the fight
                # is on; the persisted tape once `tape_id` lands post-combat).
                node["turn"] = turn
                node["seekable"] = True
            node["request_id"] = msg.get("request_id")
            # The game's own combat-log rows since the previous decision, as
            # the mod sent them (`SimCombatLog.Drain()`). Stored RAW — ids, no
            # display names — because localization lives in the workbench
            # server, which renders them when it serves the tree. Kept per node
            # for the same reason the simulated side does: the panel
            # concatenates the active path, so seeking back truncates it.
            node["combat_log_raw"] = list(msg.get("combat_log") or [])
            node["player_id"] = (msg.get("run_state") or {}).get("character")
            node["log_idx"] = log_idx
            self._active = node["id"]
            self._pending_label = node["id"]

    def on_human_action(self, msg: dict[str, Any]) -> int | None:
        """Refine a decision node's label with what the human actually did.

        The target is the node the action ANSWERED, not whichever node is
        active: the mod's history poll can deliver the action for decision N
        after the advice request for decision N+1 has already appended a
        node, and refining `_active` then stamps N's play onto N+1 -- while
        N keeps its generic "combat action" label forever (the label race
        the tree was shipped with). The mod stamps the shown advice's id
        onto each report (`advice_request_id`), so the exact node is
        recoverable; reports without one fall back to the newest node still
        awaiting its label.

        Returns the id of the node the action answered (the live coach keys
        its grades by it), or ``None`` when no decision node took it.
        """
        detail = msg.get("detail") or msg.get("action")
        if not detail:
            return None
        with self._lock:
            node = None
            rid = msg.get("advice_request_id")
            if rid:
                node = next(
                    (
                        n
                        for n in sorted(self._nodes.values(), key=lambda n: -n["id"])
                        if n.get("request_id") == rid
                    ),
                    None,
                )
            if node is None:
                target = self._pending_label if self._pending_label is not None else self._active
                node = self._nodes.get(target) if target is not None else None
            self._pending_label = None
            if (
                node is None
                or node["parent_id"] is None
                or node["decision"]
                in (
                    "room_entry",
                    "game_over",
                )
            ):
                return None
            floor = node.get("floor")
            node["label"] = f"f{floor} · {detail}" if floor is not None else str(detail)
            return node["id"]

    def on_snapshot_saved(self, msg: dict[str, Any]) -> None:
        """Stamp the mod's snapshot onto the tree.

        A room-entry snapshot precedes the room's decisions, so it becomes its
        own node ("floor N" waypoint); a post-combat snapshot stamps the
        active node (the fight it concluded).
        """
        snapshot_id = msg.get("snapshot_id")
        if not snapshot_id:
            return
        with self._lock:
            self._maybe_switch_start_time_locked(msg.get("run_start_time"))
            if bool(msg.get("post_combat")):
                node = self._nodes.get(self._active) if self._active is not None else None
                if node is not None and node.get("snapshot_id") is None:
                    node["snapshot_id"] = snapshot_id
                    node["seekable"] = True
                # The mod persisted the fight's replay tape under this
                # snapshot's id (`has_tape`): stamp it up the branch so every
                # combat node of the fight stays replayable after the run
                # moves on (undo_turn {tape_id, turn}).
                if bool(msg.get("has_tape")):
                    cur = node
                    while cur is not None and cur["decision"] != "room_entry":
                        if cur.get("turn") is not None:
                            cur["tape_id"] = snapshot_id
                        parent = cur["parent_id"]
                        cur = self._nodes.get(parent) if parent is not None else None
                return
            floor = msg.get("floor")
            node = self._new_node_locked(
                parent=self._active,
                decision="room_entry",
                label=f"floor {floor}" if floor is not None else "room entry",
                floor=floor,
                act=msg.get("act"),
            )
            node["snapshot_id"] = snapshot_id
            node["seekable"] = True
            self._active = node["id"]

    def on_snapshot_restored(self, msg: dict[str, Any]) -> None:
        """The game rewound: move the active pointer to the snapshot's node.

        The original subtree stays; the next decision forks a sibling branch.
        """
        snapshot_id = msg.get("snapshot_id")
        if not snapshot_id:
            return
        with self._lock:
            for node in self._nodes.values():
                if node.get("snapshot_id") == snapshot_id:
                    self._active = node["id"]
                    return

    def on_combat_undone(self, msg: dict[str, Any]) -> None:
        """The game replayed a combat tape to the start of a turn: move the
        active pointer to the last node BEFORE that turn, so the next
        decision forks there (mirrors on_snapshot_restored).

        With ``tape_id`` the undone fight is the persisted one under that id
        (its nodes may be off the active path after other seeks); without it,
        the current fight on the active path.
        """
        turn = msg.get("turn")
        if not isinstance(turn, int) or turn < 1:
            return
        with self._lock:
            tape_id = msg.get("tape_id")
            if tape_id:
                chain = sorted(
                    (n for n in self._nodes.values() if n.get("tape_id") == tape_id),
                    key=lambda n: n["id"],
                )
            else:
                chain = []
                cur = self._active
                while cur is not None:
                    node = self._nodes[cur]
                    if node["decision"] == "room_entry":
                        break
                    if node.get("turn") is not None:
                        chain.append(node)
                    cur = node["parent_id"]
                chain.reverse()
            if not chain:
                return
            before = [n for n in chain if n["turn"] is not None and n["turn"] < turn]
            if before:
                self._active = before[-1]["id"]
                return
            # Nothing earlier in the fight: land on its room entry.
            cur2 = (
                self._nodes.get(chain[0]["parent_id"])
                if chain[0]["parent_id"] is not None
                else None
            )
            while cur2 is not None and cur2["decision"] != "room_entry":
                parent = cur2["parent_id"]
                cur2 = self._nodes.get(parent) if parent is not None else None
            if cur2 is not None:
                self._active = cur2["id"]

    def on_run_end(self, msg: dict[str, Any]) -> None:
        with self._lock:
            victory = bool(msg.get("victory"))
            node = self._new_node_locked(
                parent=self._active,
                decision="game_over",
                label="victory" if victory else "defeat",
                floor=msg.get("floor"),
                act=msg.get("act"),
            )
            node["error"] = not victory
            self._active = node["id"]

    def snapshot_id_of(self, node_id: int) -> str | None:
        with self._lock:
            node = self._nodes.get(node_id)
            return node.get("snapshot_id") if node else None

    def seek_plan(self, node_id: int) -> dict[str, Any] | None:
        """The bridge verb that lands the game where *node_id* says.

        Combat nodes with a turn prefer ``undo_turn`` over any stamped
        snapshot: the snapshot on a fight's last node is the POST-combat
        save, while the node's label is a mid-fight play -- the tape replay
        is what actually matches the label. Room-entry / post-combat nodes
        restore their snapshot. ``None`` = nothing exact for this node (the
        caller 409s; the UI then walks to the nearest seekable ancestor).
        """
        with self._lock:
            node = self._nodes.get(node_id)
            if node is None:
                return None
            turn = node.get("turn")
            if isinstance(turn, int) and turn >= 1:
                plan: dict[str, Any] = {"action": "undo_turn", "turn": turn}
                if node.get("tape_id"):
                    plan["tape_id"] = node["tape_id"]
                return plan
            if node.get("snapshot_id"):
                return {"action": "restore_snapshot", "snapshot_id": node["snapshot_id"]}
            return None

    # -- projection ----------------------------------------------------

    def log_idx(self, node_id: Any) -> int | None:
        """The run-log replay step of ``node_id`` (``None`` when unknown)."""
        with self._lock:
            node = self._nodes.get(node_id) if isinstance(node_id, int) else None
            return node.get("log_idx") if node is not None else None

    def active_path(self) -> list[int]:
        """Node ids from the root to the active node (the live coach withdraws
        grades for nodes off this path after a rewind)."""
        with self._lock:
            path: list[int] = []
            cur = self._active
            while cur is not None:
                path.append(cur)
                cur = self._nodes[cur]["parent_id"]
            path.reverse()
            return path

    def timeline(self) -> dict[str, Any]:
        """The ``LiveTimeline`` wire shape (plus snapshot_id/seekable)."""
        with self._lock:
            path: list[int] = []
            cur = self._active
            while cur is not None:
                path.append(cur)
                cur = self._nodes[cur]["parent_id"]
            path.reverse()
            return {
                "nodes": [dict(n) for n in self._nodes.values()],
                "active_path": path,
                "active_leaf": self._active,
            }
