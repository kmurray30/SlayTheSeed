import json

from fastapi.testclient import TestClient
from workbench.server import create_app


def _state(enemy_hp):
    return {
        "type": "state",
        "step": 0,
        "data": {
            "decision": "combat_play",
            "round": 1,
            "player": {"name": "Ironclad", "hp": 70, "max_hp": 80, "block": 0},
            "enemies": [{"index": 0, "name": "Nibbit", "hp": enemy_hp, "max_hp": 50, "block": 0}],
            "hand": [],
        },
    }


def _write_log(tmp_path):
    lines = [
        _state(50),
        {
            "type": "action",
            "data": {
                "cmd": "action",
                "action": "play_card",
                "args": {"card_index": 0, "target_index": 0},
            },
        },
        _state(44),
    ]
    p = tmp_path / "run.jsonl"
    p.write_text("\n".join(json.dumps(x) for x in lines))
    return str(p)


def test_timeline_includes_details(tmp_path):
    log = _write_log(tmp_path)
    c = TestClient(create_app(images_root=str(tmp_path), logs_dir=str(tmp_path)))
    c.post("/api/load", json={"path": log})
    steps = c.get("/api/timeline").json()["steps"]
    # Step 0's action result is step 1's state -> Nibbit took 6 damage.
    assert steps[0]["details"] == ["Nibbit HP 50→44 (−6)"]
    # The last step has no successor -> no details.
    assert steps[1]["details"] == []


def test_timeline_slices_bridge_raw_combat_log(tmp_path):
    """A bridge-recorded run carries ``combat_log_raw`` in each state.

    No per-action ``combat_log`` records exist in those files; the timeline
    derives each step's slice as the tail its successor's raw log grew by,
    formatted through the same renderer the live panels use.
    """
    hit = {
        "kind": "damage",
        "round": 1,
        "side": "player",
        "text": "IRONCLAD dealt 6 damage to Nibbit",
        "source": {"kind": "card", "id": "STRIKE_IRONCLAD", "upgraded": False},
        "dealer": {"kind": "player", "id": "IRONCLAD", "uid": None},
        "receiver": {"kind": "monster", "id": "Nibbit", "uid": 1},
        "unblocked": 6,
        "blocked": 0,
        "hp_before": 50,
        "hp_after": 44,
    }
    s0, s1 = _state(50), _state(44)
    s0["data"]["combat_log_raw"] = []
    s1["data"]["combat_log_raw"] = [hit]
    lines = [
        s0,
        {"type": "action", "data": {"cmd": "action", "action": "play_card", "args": {}}},
        s1,
    ]
    p = tmp_path / "bridge.jsonl"
    p.write_text("\n".join(json.dumps(x) for x in lines))

    c = TestClient(create_app(images_root=str(tmp_path), logs_dir=str(tmp_path)))
    c.post("/api/load", json={"path": str(p)})
    steps = c.get("/api/timeline").json()["steps"]
    rows = steps[0]["combat_log"]
    assert len(rows) == 1
    assert rows[0]["text"] == "The Ironclad dealt 6 damage to Nibbit using Strike."
    assert steps[1]["combat_log"] == []
