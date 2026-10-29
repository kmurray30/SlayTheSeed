"""Branch-tree / time-travel tests for the live workbench session.

Drives the HTTP API (which uses ``BranchingLiveSession``) with the no-subprocess
``FakeGame`` stub. FakeGame is deterministic: ``start`` → map_select, any ``act``
→ combat_play, so replay-based seek lands on reproducible states.
"""

from fake_game import FakeGame
from fastapi.testclient import TestClient
from workbench.server import create_app


def _client(tmp_path, monkeypatch, game_cls=FakeGame):
    from workbench import branching

    monkeypatch.setattr(branching, "Game", game_cls)
    images = tmp_path / "images"
    images.mkdir()
    logs = tmp_path / "logs"
    logs.mkdir()
    return TestClient(create_app(images_root=images, logs_dir=logs))


def _start(c):
    c.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})


def test_timeline_400_without_run(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    assert c.get("/api/live/timeline").status_code == 400


def test_timeline_grows_with_actions(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    _start(c)
    tl = c.get("/api/live/timeline").json()
    assert len(tl["nodes"]) == 1  # root only
    assert tl["active_path"] == [0]
    assert tl["active_leaf"] == 0

    c.post("/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}})
    tl = c.get("/api/live/timeline").json()
    assert len(tl["nodes"]) == 2
    assert tl["active_path"] == [0, 1]
    assert tl["active_leaf"] == 1


def test_seek_unknown_node_404(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    _start(c)
    assert c.post("/api/live/seek", json={"node_id": 999}).status_code == 404


def test_seek_rewinds_active_leaf_without_new_node(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    _start(c)
    c.post("/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}})
    c.post("/api/live/action", json={"action": "end_turn", "args": {}})
    assert len(c.get("/api/live/timeline").json()["nodes"]) == 3  # root, 1, 2

    r = c.post("/api/live/seek", json={"node_id": 1})
    assert r.status_code == 200
    assert r.json()["step"]["decision"] == "combat_play"
    tl = c.get("/api/live/timeline").json()
    assert tl["active_leaf"] == 1
    assert tl["active_path"] == [0, 1]  # leaf pointer moved back
    assert len(tl["nodes"]) == 3  # seek created nothing


def test_acting_after_seek_forks_a_branch(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    _start(c)
    # root(0) -> select(1) -> end_turn(2)
    c.post("/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}})
    c.post("/api/live/action", json={"action": "end_turn", "args": {}})
    # Rewind to node 1 and act again -> forks a new child (node 3) off node 1.
    c.post("/api/live/seek", json={"node_id": 1})
    c.post("/api/live/action", json={"action": "play_card", "args": {"card_index": 0}})

    tl = c.get("/api/live/timeline").json()
    assert len(tl["nodes"]) == 4  # original line preserved + new branch node
    node1 = next(n for n in tl["nodes"] if n["id"] == 1)
    assert sorted(node1["child_ids"]) == [2, 3]  # both branches hang off node 1
    assert tl["active_leaf"] == 3
    assert tl["active_path"] == [0, 1, 3]


def test_search_tree_400_without_run(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    assert c.post("/api/live/search_tree", json={"config": {}}).status_code == 400


def test_search_tree_409_outside_combat(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    _start(c)  # FakeGame.start -> map_select (non-combat)
    assert c.post("/api/live/search_tree", json={"config": {}}).status_code == 409


def test_quit_log_follows_active_branch(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    _start(c)
    c.post("/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}})
    c.post("/api/live/action", json={"action": "end_turn", "args": {}})
    c.post("/api/live/seek", json={"node_id": 1})
    c.post("/api/live/action", json={"action": "play_card", "args": {"card_index": 0}})
    log_path = c.post("/api/live/quit").json()["log_path"]
    assert log_path and log_path.endswith(".jsonl")


def test_timeline_nodes_carry_floor_act_forward(tmp_path):
    """Each timeline node is tagged with the floor/act it sits on, carried
    forward from its ancestors when its own state omits them (the live log
    draws act/floor separators off these)."""
    from workbench.branching import BranchingLiveSession

    class FloorActGame:
        """A fake whose start state is Act 1 floor 0, then a single action
        advances to floor 1 — and whose *next* state omits floor/act, to prove
        the value is carried forward rather than reset to None."""

        def __init__(self, *, forward_stderr: bool = False):
            self._states = [
                {"decision": "map_select", "floor": 1, "act": 1, "choices": []},
                {"decision": "combat_play", "round": 1, "hand": [], "enemies": []},
            ]
            self._i = 0

        def start(self, character="Ironclad", seed="s", ascension=0, lang="en", act1="overgrowth"):
            return {"decision": "map_select", "floor": 0, "act": 1, "choices": []}

        def act(self, action, **args):
            state = self._states[min(self._i, len(self._states) - 1)]
            self._i += 1
            return state

        def get_state(self):
            return {}

        def get_map(self):
            return {"type": "map", "rows": [], "current_coord": {"col": 0, "row": 0}}

        def close(self):
            pass

    sess = BranchingLiveSession(logs_dir=str(tmp_path), backend="csharp")
    sess._backend_cls = FloorActGame  # type: ignore[assignment]
    sess.start("Ironclad", "s", 0)
    sess.apply("select_map_node", {"col": 0, "row": 0})  # -> floor 1
    sess.apply("play_card", {"card_index": 0})  # state omits floor/act

    nodes = {n["id"]: n for n in sess.timeline()["nodes"]}
    assert (nodes[0]["floor"], nodes[0]["act"]) == (0, 1)  # root
    assert (nodes[1]["floor"], nodes[1]["act"]) == (1, 1)  # advanced to floor 1
    assert (nodes[2]["floor"], nodes[2]["act"]) == (1, 1)  # carried forward


def test_nodes_carry_this_actions_combat_log_rows(tmp_path, monkeypatch):
    """Each node holds only ITS action's slice of the combat log.

    The engine's log is combat-scoped and keeps growing, so a node that carried
    the whole log would repeat every earlier hit — and the panel would show the
    fight twice by the second card. Concatenating the per-node slices along the
    active path is what makes seeking back truncate the panel correctly.
    """
    c = _client(tmp_path, monkeypatch)
    _start(c)
    c.post("/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}})
    c.post("/api/live/action", json={"action": "play_card", "args": {"card_index": 0}})

    nodes = {n["id"]: n for n in c.get("/api/live/timeline").json()["nodes"]}
    assert nodes[0]["combat_log"] == [], "the root precedes every action"
    for node_id in (1, 2):
        rows = nodes[node_id]["combat_log"]
        assert len(rows) == 1, f"node {node_id} holds one action's worth: {rows}"
        assert rows[0]["text"] == "The Ironclad dealt 6 damage to Nibbit using Strike."
        assert rows[0]["actor_side"] == "player"
        assert rows[0]["target"] == "Nibbit"


class _FlashingGame(FakeGame):
    """FakeGame that logs a power TRIGGERING and a card DRAW beside its hit.

    Both kinds are in ``combatlog._VERBOSE_ONLY``, so the default render drops
    both, and ``verbose=True`` would keep both. Emitting one of each is what
    lets the test below tell a NARROW ``keep`` apart from either — plain
    FakeGame, with only a ``damage`` row, cannot distinguish them at all.
    """

    def act(self, action, **args):
        state = super().act(action, **args)
        if self.recording_combat_log:
            self._combat_log += [
                {
                    "kind": "power_flash",
                    "round": 1,
                    "side": "enemy",
                    "source": None,
                    "creature": {"kind": "monster", "id": "Nibbit", "uid": 1},
                    "power": "THORNS",
                },
                {
                    "kind": "card_drawn",
                    "round": 1,
                    "side": "player",
                    "source": None,
                    "card": "DEFEND_IRONCLAD",
                    "upgraded": False,
                    "from_hand_draw": False,
                },
            ]
        return state


def test_nodes_carry_the_view_only_power_flash_rows_and_nothing_else_hidden(tmp_path, monkeypatch):
    """A node's slice adds ``power_flash`` to the game's log, and only that.

    The client takes the effect stream off the TIMELINE NODE's ``combat_log``
    (``combatlog.ts``'s ``activeNode``), not off ``state.combat_log`` — which
    only the live-game bridge writes. So a ``power_flash`` dropped by the
    default render never reaches ``power_flash_vfx``, and the panels re-hide it
    client-side (``readableRows``).

    The absent ``card_drawn`` is the other half: the slice is rendered with a
    NARROW ``keep``, not ``verbose=True``. Were it verbose, the card tape would
    ride into every saved run's JSONL and the client would have to re-hide six
    kinds nothing asked for.
    """
    c = _client(tmp_path, monkeypatch, _FlashingGame)
    _start(c)
    c.post("/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}})

    (node,) = [n for n in c.get("/api/live/timeline").json()["nodes"] if n["id"] == 1]
    kinds = [r["kind"] for r in node["combat_log"]]
    assert kinds == ["damage", "power_flash"], kinds
    flash = node["combat_log"][1]
    # The uid is the join key the client resolves to a board slot.
    assert flash["target_uid"] == 1
    assert flash["noun"] == "Thorns"


def test_timeline_nodes_carry_turn(tmp_path, monkeypatch):
    """Nodes expose the combat round as ``turn`` so the log can group by turn."""
    c = _client(tmp_path, monkeypatch)
    _start(c)
    c.post("/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}})

    nodes = c.get("/api/live/timeline").json()["nodes"]
    assert nodes[0]["turn"] is None  # map_select has no round
    assert nodes[1]["turn"] == 2  # FakeGame's combat state sits at round 2


def test_quit_persists_combat_log_and_load_restores_it(tmp_path, monkeypatch):
    """Ending a run keeps each action's combat-log slice in the JSONL.

    The replay timeline used to rebuild detail from state diffs only, so a
    loaded run lost the per-event granularity the live log had. The quit path
    now writes one ``combat_log`` record per action; /api/timeline hands the
    rows back on the step whose action produced them.
    """
    c = _client(tmp_path, monkeypatch)
    _start(c)
    c.post("/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}})
    c.post("/api/live/action", json={"action": "play_card", "args": {"card_index": 0}})
    log_path = c.post("/api/live/quit").json()["log_path"]

    c.post("/api/load", json={"path": log_path})
    steps = c.get("/api/timeline").json()["steps"]
    assert len(steps) == 3
    # Step i's action produced step i+1's state; the rows ride on step i.
    for i in (0, 1):
        rows = steps[i]["combat_log"]
        assert len(rows) == 1, f"step {i}: {rows}"
        assert rows[0]["text"] == "The Ironclad dealt 6 damage to Nibbit using Strike."
    assert steps[2]["combat_log"] == []  # the leaf has no following action


def test_quit_persists_the_display_label_shown_live(tmp_path, monkeypatch):
    """A step's replay label is the one the LIVE log showed.

    Every autoplayed (native-agent) node is recorded under the sentinel verb
    ``__agent_native__``, which ``render.describe_action`` cannot name — so the
    replay's history rendered a wall of "__agent_native__" where the live log
    had read "Play Strike → Twig Slime". The quit path now writes the node's
    rendered label beside the action, and ``describe_action`` prefers it.
    """
    c = _client(tmp_path, monkeypatch)
    _start(c)
    c.post("/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}})
    live_labels = [n["label"] for n in c.get("/api/live/timeline").json()["nodes"][1:]]
    log_path = c.post("/api/live/quit").json()["log_path"]

    c.post("/api/load", json={"path": log_path})
    steps = c.get("/api/timeline").json()["steps"]
    # Step i's action produced step i+1, so the labels sit on steps[:-1].
    assert [s["label"] for s in steps[:-1]] == live_labels
    assert all("__agent_native__" not in s["label"] for s in steps)


def test_load_reports_a_workbench_run_as_resumable(tmp_path, monkeypatch):
    """A run played in the workbench advertises itself as fully resumable."""
    c = _client(tmp_path, monkeypatch)
    _start(c)
    c.post("/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}})
    log_path = c.post("/api/live/quit").json()["log_path"]

    body = c.post("/api/load", json={"path": log_path}).json()
    assert body["resume"] == {"kind": "run", "reason": None}


def test_play_from_here_resumes_a_recorded_run_at_any_step(tmp_path, monkeypatch):
    """ "Play from here" takes over a recorded run at a NON-combat decision.

    The endpoint used to require a fixture-sourced combat recording, which no
    run played in the workbench ever is — so the button 409'd on every real
    run, and replay mode rendered no error, making it look inert. A workbench
    log now carries its run identity + a replayable action line, so the run
    rebuilds from its seed at whichever step is being viewed.
    """
    c = _client(tmp_path, monkeypatch)
    _start(c)
    c.post("/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}})
    c.post("/api/live/action", json={"action": "play_card", "args": {"card_index": 0}})
    log_path = c.post("/api/live/quit").json()["log_path"]

    c.post("/api/load", json={"path": log_path})
    steps = c.get("/api/timeline").json()["steps"]
    assert steps[0]["decision"] == "map_select", "step 0 is not a combat decision"

    # Resume at the map decision: a live run exists again, positioned there.
    r = c.post("/api/live/from_replay", json={"idx": 0})
    assert r.status_code == 200, r.text
    assert r.json()["decision"] == "map_select"
    tl = c.get("/api/live/timeline").json()
    assert tl["active_path"] == [0], "resuming at step 0 replays nothing"

    # And deeper in: replaying one action lands on the next recorded decision.
    r = c.post("/api/live/from_replay", json={"idx": 1})
    assert r.status_code == 200, r.text
    assert r.json()["decision"] == steps[1]["decision"]
    assert c.get("/api/live/timeline").json()["active_path"] == [0, 1]


def test_play_from_here_explains_itself_on_an_unresumable_log(tmp_path, monkeypatch):
    """A recording with neither a fixture nor a replay line says WHY, once at
    load time (so the button can be disabled) and again on a forced attempt."""
    import json as _json
    from pathlib import Path

    c = _client(tmp_path, monkeypatch)
    _start(c)
    c.post("/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}})
    log_path = c.post("/api/live/quit").json()["log_path"]

    # Strip the fields a pre-2026-08-26 workbench log lacked.
    lines = []
    for raw in Path(log_path).read_text(encoding="utf-8").splitlines():
        entry = _json.loads(raw)
        if entry.get("type") == "meta":
            for key in ("run_mode", "act1", "backend"):
                entry["data"].pop(key, None)
        if entry.get("type") == "action":
            entry["data"].pop("replay", None)
        lines.append(_json.dumps(entry))
    legacy = tmp_path / "legacy.jsonl"
    legacy.write_text("\n".join(lines) + "\n", encoding="utf-8")

    body = c.post("/api/load", json={"path": str(legacy)}).json()
    assert body["resume"]["kind"] is None
    assert "cannot be resumed" in body["resume"]["reason"]

    r = c.post("/api/live/from_replay", json={"idx": 0})
    assert r.status_code == 409
    assert "cannot be resumed" in r.json()["detail"]
