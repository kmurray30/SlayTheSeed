"""`/api/bridge/tree` renders the LIVE GAME's combat log.

The mod ships the game's own history rows; the advisor stores them raw (it has
no localization); this endpoint is where they become the same display rows a
simulated run produces, so one panel serves both.
"""

from __future__ import annotations

import io
import json
from typing import Any

from fastapi.testclient import TestClient
from workbench.server import create_app


def _client(tmp_path, monkeypatch, tree: dict[str, Any]):
    """An app whose advisor control plane always answers with ``tree``."""
    import urllib.request

    class _Resp(io.BytesIO):
        def __enter__(self):
            return self

        def __exit__(self, *exc):
            return None

    monkeypatch.setattr(
        urllib.request,
        "urlopen",
        lambda *a, **kw: _Resp(json.dumps(tree).encode("utf-8")),
    )
    images = tmp_path / "images"
    images.mkdir()
    logs = tmp_path / "logs"
    logs.mkdir()
    return TestClient(create_app(images_root=images, logs_dir=logs))


def _node(**over: Any) -> dict[str, Any]:
    return {
        "id": 1,
        "parent_id": 0,
        "idx": 1,
        "decision": "combat_action",
        "label": "f1 · combat action",
        "details": [],
        "error": False,
        "child_ids": [],
        "floor": 1,
        "act": 1,
        **over,
    }


def test_raw_game_rows_become_display_rows(tmp_path, monkeypatch):
    raw = [
        {
            "kind": "damage",
            "round": 1,
            "side": "player",
            "text": "IRONCLAD dealt 6 damage to NIBBIT",
            "source": {"kind": "card", "id": "STRIKE_IRONCLAD", "upgraded": False},
            "dealer": {"kind": "player", "id": "IRONCLAD", "uid": None},
            "receiver": {"kind": "monster", "id": "Nibbit", "uid": None},
            "unblocked": 6,
            "blocked": 0,
        }
    ]
    tree = {
        "nodes": [_node(combat_log_raw=raw, player_id="IRONCLAD")],
        "active_path": [1],
        "active_leaf": 1,
    }
    got = _client(tmp_path, monkeypatch, tree).get("/api/bridge/tree").json()
    (node,) = got["nodes"]
    (row,) = node["combat_log"]
    assert row["text"] == "The Ironclad dealt 6 damage to Nibbit using Strike."
    assert (row["actor_side"], row["target_side"]) == ("player", "enemy")
    # The raw rows are the server's input, not the client's — don't ship both.
    assert "combat_log_raw" not in node
    assert "player_id" not in node


def test_a_node_without_rows_is_passed_through_untouched(tmp_path, monkeypatch):
    tree = {"nodes": [_node()], "active_path": [1], "active_leaf": 1}
    got = _client(tmp_path, monkeypatch, tree).get("/api/bridge/tree").json()
    assert "combat_log" not in got["nodes"][0]


def test_an_unknown_row_kind_still_shows_the_games_own_sentence(tmp_path, monkeypatch):
    # Forward compatibility: the mod maps entry types the workbench has no
    # renderer for to `kind: "other"` and always sends the game's `text`.
    raw = [{"kind": "other", "round": 2, "side": "enemy", "text": "NIBBIT summoned 1"}]
    tree = {
        "nodes": [_node(combat_log_raw=raw, player_id="IRONCLAD")],
        "active_path": [1],
        "active_leaf": 1,
    }
    got = _client(tmp_path, monkeypatch, tree).get("/api/bridge/tree").json()
    assert got["nodes"][0]["combat_log"][0]["text"] == "NIBBIT summoned 1"


def test_a_replayed_step_renders_its_rows_too(tmp_path, monkeypatch):
    """A followed / replayed live run shows the panel from its run log.

    The live-game path writes the mod's rows onto each state record; `/api/step`
    is where they become display rows, so the replay view and the workbench's
    own live sessions end up rendering the identical shape.
    """
    import json as _json

    raw = {
        "kind": "damage",
        "round": 1,
        "side": "player",
        "text": "IRONCLAD dealt 6 damage to NIBBIT",
        "source": {"kind": "card", "id": "STRIKE_IRONCLAD", "upgraded": False},
        "dealer": {"kind": "player", "id": "IRONCLAD", "uid": None},
        "receiver": {"kind": "monster", "id": "Nibbit", "uid": None},
        "unblocked": 6,
        "blocked": 0,
    }
    run = tmp_path / "run.jsonl"
    run.write_text(
        "\n".join(
            _json.dumps(rec)
            for rec in [
                {"step": 0, "type": "meta", "data": {"character": "Ironclad", "seed": "s"}},
                {
                    "step": 0,
                    "type": "state",
                    "data": {
                        "decision": "combat_play",
                        "round": 1,
                        "hand": [],
                        "enemies": [],
                        "combat_log_raw": [raw],
                        "character_id": "IRONCLAD",
                    },
                },
            ]
        ),
        encoding="utf-8",
    )

    c = _client(tmp_path, monkeypatch, {"nodes": [], "active_path": [], "active_leaf": None})
    assert c.post("/api/load", json={"path": str(run)}).status_code == 200
    state = c.get("/api/step/0").json()["state"]
    (row,) = state["combat_log"]
    assert row["text"] == "The Ironclad dealt 6 damage to Nibbit using Strike."
    assert "combat_log_raw" not in state
