import json

from fake_game import FakeGame  # shared stub from tests/workbench/fake_game.py
from fastapi.testclient import TestClient
from workbench.server import create_app


def _client(tmp_path, monkeypatch):
    from workbench import branching

    # The live API drives a BranchingLiveSession, which spawns its game via
    # ``branching.Game`` — patch there (not ``live.Game``).
    monkeypatch.setattr(branching, "Game", FakeGame)
    images = tmp_path / "images"
    images.mkdir()
    logs = tmp_path / "logs"
    logs.mkdir()
    return TestClient(create_app(images_root=images, logs_dir=logs))


def test_start_returns_step_with_options(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    r = c.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    assert r.status_code == 200
    step = r.json()
    assert step["decision"] == "map_select"
    # non-combat decision -> normalized options attached to the state
    assert step["state"]["options"][0]["action"] == "select_map_node"


def test_live_step_meta_carries_floor_and_act(tmp_path, monkeypatch):
    """The docked minimap refetches the act graph only when ``meta.floor`` /
    ``meta.act`` change (the App.tsx effect deps). If the live step omits them
    they stay ``undefined`` forever, the effect never re-fires, and the minimap
    freezes on the first map it fetched."""
    c = _client(tmp_path, monkeypatch)
    start = c.post(
        "/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"}
    ).json()
    assert start["meta"]["floor"] == 0
    assert start["meta"]["act"] == 1
    assert start["meta"]["act_name"] == "Act 1"
    # Advancing to a new floor must change meta so the refetch effect re-fires.
    after = c.post(
        "/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}}
    ).json()["step"]
    assert after["meta"]["floor"] == 1


def test_state_400_when_no_session(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    assert c.get("/api/live/state").status_code == 400


def test_action_advances_and_returns_step(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    c.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    r = c.post("/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}})
    body = r.json()
    assert body["error"] is None
    assert body["step"]["decision"] == "combat_play"


def test_action_unknown_verb_400(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    c.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    r = c.post("/api/live/action", json={"action": "danger", "args": {}})
    assert r.status_code == 400


def test_live_map_400_when_no_session(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    assert c.get("/api/live/map").status_code == 400


def test_live_map_returns_graph(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    c.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    body = c.get("/api/live/map").json()
    assert len(body["graphs"]) == 1
    assert body["graphs"][0]["full_map"]["boss"]["type"] == "Boss"


def test_live_map_available_outside_map_select(tmp_path, monkeypatch):
    """The map button must work at any decision — e.g. during combat — not just
    at a ``map_select`` step (where ``full_map`` is auto-attached to state)."""
    c = _client(tmp_path, monkeypatch)
    c.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    c.post("/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}})
    # Now in combat; the state itself carries no full_map ...
    assert c.get("/api/live/state").json()["state"].get("full_map") is None
    # ... but the on-demand map endpoint still returns the act graph.
    body = c.get("/api/live/map").json()
    assert len(body["graphs"]) == 1


def test_card_advisor_csharp_backend_returns_error_payload(tmp_path, monkeypatch):
    """At a card reward on the (non-forkable) C# backend, the advisor returns a
    graceful error payload — not a 500 — telling the user to use the Rust engine."""
    from workbench import branching
    from workbench.branching import BranchingLiveSession

    monkeypatch.setattr(branching, "Game", FakeGame)
    sess = BranchingLiveSession(logs_dir=str(tmp_path), backend="csharp")
    sess.start("Ironclad", "s", 0)
    # Force the active node to look like a card-reward decision.
    sess._nodes[sess._active_id].state["decision"] = "card_reward"  # type: ignore[index]
    result = sess.advise_card_reward({})
    assert result is not None
    assert "Rust" in result["error"]
    assert result["candidates"] == []


def test_card_advisor_400_when_no_session(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    assert c.post("/api/live/card-advisor", json={}).status_code == 400


def test_card_advisor_409_outside_card_reward(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    c.post(
        "/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"}
    )  # map_select
    assert c.post("/api/live/card-advisor", json={}).status_code == 409


def test_card_ranker_endpoint_removed(tmp_path, monkeypatch):
    """The learned pick-ranker was removed with the ranker net; its eager
    ``/api/live/card-ranker`` endpoint must be gone (404, not 400/409)."""
    c = _client(tmp_path, monkeypatch)
    assert c.post("/api/live/card-ranker", json={}).status_code == 404


def test_card_advisor_stream_400_when_no_session(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    assert c.post("/api/live/card-advisor/stream", json={}).status_code == 400


def test_card_advisor_stream_emits_error_event_outside_reward(tmp_path, monkeypatch):
    """The streaming endpoint can't send a 409 after the body has begun, so
    outside a card reward it streams a single ``error`` event (HTTP 200) instead.
    Exercises the full thread + queue + NDJSON plumbing end-to-end."""
    c = _client(tmp_path, monkeypatch)
    c.post(
        "/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"}
    )  # map_select
    r = c.post("/api/live/card-advisor/stream", json={})
    assert r.status_code == 200
    assert r.headers["content-type"].startswith("application/x-ndjson")
    events = [json.loads(ln) for ln in r.text.splitlines() if ln.strip()]
    assert events and events[-1]["type"] == "error"


def test_auto_action_400_when_no_session(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    assert c.post("/api/live/auto-action").status_code == 400


def test_auto_action_409_when_backend_cannot_recommend(tmp_path, monkeypatch):
    """The C# FakeGame backend has no ``recommended_action`` (no in-process
    RunManager to run the heuristic), so the endpoint reports 409 — the
    frontend then falls back to the first option / MCTS line."""
    c = _client(tmp_path, monkeypatch)
    c.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    assert c.post("/api/live/auto-action").status_code == 409


def test_auto_action_recommends_from_backend(tmp_path, monkeypatch):
    """When the backend exposes ``recommended_action``, the endpoint returns its
    ``{action, args}`` so the frontend can dispatch the policy's best move."""
    from workbench import branching
    from workbench.branching import BranchingLiveSession

    monkeypatch.setattr(branching, "Game", FakeGame)
    sess = BranchingLiveSession(logs_dir=str(tmp_path), backend="csharp")
    sess.start("Ironclad", "s", 0)
    sess.set_run_mode(off_combat_brain="heuristic")
    # Stub a backend recommendation (mirrors the Rust backend's contract).
    sess.game.recommended_action = lambda: ("select_card_reward", {"card_index": 1})  # type: ignore[attr-defined]
    assert sess.recommended_action() == {
        "action": "select_card_reward",
        "args": {"card_index": 1},
    }


def test_hint_409_outside_combat(tmp_path, monkeypatch):
    from workbench import reasoning

    monkeypatch.setattr(
        reasoning,
        "score",
        lambda state, cfg: {"scores": [{"action": "Strike"}], "pv": None, "config": cfg},
    )
    c = _client(tmp_path, monkeypatch)
    c.post(
        "/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"}
    )  # map_select
    assert c.post("/api/live/hint", json={}).status_code == 409
    c.post("/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}})
    r = c.post("/api/live/hint", json={})
    assert r.status_code == 200 and r.json()["scores"][0]["action"] == "Strike"


def test_quit_reports_log_path(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    c.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    r = c.post("/api/live/quit")
    assert r.json()["ok"] is True
    assert r.json()["log_path"].endswith(".jsonl")


def test_quit_then_state_is_400(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    c.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    c.post("/api/live/quit")
    assert c.get("/api/live/state").status_code == 400


def test_action_illegal_move_non_fatal(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    c.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    c.post(
        "/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}}
    )  # -> combat
    r = c.post("/api/live/action", json={"action": "play_card", "args": {"card_index": 99}})
    body = r.json()
    assert body["error"] == "illegal"
    assert body["step"]["decision"] == "combat_play"


def test_action_returns_label(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    c.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    r = c.post(
        "/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}}
    ).json()
    assert r["label"] == "Map → (0,0)"


def test_action_returns_details(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    c.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    r = c.post(
        "/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}}
    ).json()
    assert r["details"] == []
