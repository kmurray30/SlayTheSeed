"""Tests for the live session debug log (workbench.session_log).

The pure ``snapshot`` renderer is checked directly; the navigation trace
(START / ACTION / SEEK / BRANCH / REJECT) is exercised end-to-end through the
HTTP API with the no-subprocess ``FakeGame`` stub, then read back off disk.
"""

from pathlib import Path

from fake_game import FakeGame
from fastapi.testclient import TestClient
from workbench.server import create_app
from workbench.session_log import snapshot


def _client(tmp_path, monkeypatch):
    from workbench import branching

    monkeypatch.setattr(branching, "Game", FakeGame)
    (tmp_path / "images").mkdir()
    logs = tmp_path / "logs"
    logs.mkdir()
    return TestClient(create_app(images_root=tmp_path / "images", logs_dir=logs)), logs


def _read_session_log(logs: Path) -> str:
    files = list(logs.glob("*_session.log"))
    assert len(files) == 1, f"expected one session log, got {files}"
    return files[0].read_text()


def test_snapshot_renders_player_and_enemies():
    state = {
        "decision": "combat_play",
        "player": {
            "hp": 45,
            "max_hp": 80,
            "block": 6,
            "powers": [{"name": "Dexterity", "amount": 1}],
        },
        "enemies": [
            {"index": 0, "name": "Bygone Effigy", "hp": 68, "block": 0},
            {"index": 1, "name": "Nibbit", "hp": 12, "block": 5},
        ],
    }
    line = snapshot(state)
    assert "decision=combat_play" in line
    assert "you=45/80 blk=6 [Dexterity1]" in line
    assert "0:Bygone Effigy hp=68 blk=0" in line
    assert "1:Nibbit hp=12 blk=5" in line


def test_snapshot_is_defensive():
    assert snapshot(None) == "?"
    assert snapshot({}) == "decision=? you=None blk=0 enemies=[]"


def test_session_log_traces_action_seek_and_branch(tmp_path, monkeypatch):
    c, logs = _client(tmp_path, monkeypatch)
    c.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    # root(0) -> select(1) -> end_turn(2)
    c.post("/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}})
    c.post("/api/live/action", json={"action": "end_turn", "args": {}})
    # Rewind to node 1, then act -> forks a new branch (node 3) off node 1.
    c.post("/api/live/seek", json={"node_id": 1})
    c.post("/api/live/action", json={"action": "play_card", "args": {"card_index": 0}})

    log = _read_session_log(logs)
    assert "START   Ironclad seed=S A0 backend=csharp" in log  # seed canonicalized
    assert "ROOT    node 0" in log
    # First real action is a linear ACTION, not a branch.
    assert "ACTION  node 0 -> 1  select_map_node" in log
    # The seek records the rewind with the source and target node.
    assert "SEEK    node 2 -> 1 (combat_play)" in log
    # Acting after the seek forks; the trace names the line it diverged from.
    assert "BRANCH  node 1 -> 3  play_card" in log
    assert "[forked from line [2]]" in log
    # Pre/post snapshots are attached to each action for life-cycle debugging.
    assert "pre:  decision=" in log
    assert "post: decision=" in log


def test_session_log_records_rejected_action(tmp_path, monkeypatch):
    c, logs = _client(tmp_path, monkeypatch)
    c.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    c.post("/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}})
    # FakeGame rejects card_index 99 -> a REJECT line, no new node.
    c.post("/api/live/action", json={"action": "play_card", "args": {"card_index": 99}})

    log = _read_session_log(logs)
    assert "REJECT" in log
    assert "illegal" in log
