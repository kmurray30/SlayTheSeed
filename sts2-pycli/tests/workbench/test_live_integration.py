"""End-to-end live play against the real headless game (needs `just build-cs`)."""

from fastapi.testclient import TestClient
from workbench.server import create_app
from workbench.timeline import TimelineStore


def test_start_play_card_end_turn_and_replay_log(tmp_path):
    images = tmp_path / "images"
    images.mkdir()
    logs = tmp_path / "logs"
    logs.mkdir()
    client = TestClient(create_app(images_root=images, logs_dir=logs))

    # Start a deterministic run.
    step = client.post(
        "/api/live/start", json={"character": "Ironclad", "seed": "test", "ascension": 0}
    ).json()
    assert step["decision"]  # some decision surfaced

    # Drive forward until combat, choosing the first available option each time.
    for _ in range(40):
        if step["decision"] == "combat_play":
            break
        opts = step["state"].get("options") or []
        enabled = [o for o in opts if o["enabled"]]
        assert enabled, f"no options at {step['decision']}"
        o = enabled[0]
        step = client.post(
            "/api/live/action", json={"action": o["action"], "args": o["args"]}
        ).json()["step"]
    assert step["decision"] == "combat_play"

    # Play the first playable card (auto-target enemy 0 if needed), then end turn.
    hand = step["state"]["hand"]
    playable = next((c for c in hand if c.get("can_play")), None)
    if playable is not None:
        args = {"card_index": playable["index"]}
        if "Enemy" in (playable.get("target_type") or ""):
            args["target_index"] = step["state"]["enemies"][0]["index"]
        body = client.post("/api/live/action", json={"action": "play_card", "args": args}).json()
        assert body["error"] is None
    end = client.post("/api/live/action", json={"action": "end_turn", "args": {}}).json()
    assert end["error"] is None

    # Quit and confirm the recorded log re-opens in the replay store.
    log_path = client.post("/api/live/quit").json()["log_path"]
    store = TimelineStore.load_jsonl(log_path)
    assert len(store) >= 2
    assert store.get(0)["decision"]  # first recorded step has a decision


def test_seek_to_combat_node_returns_combat_not_unknown(tmp_path):
    """Regression: seeking back to a combat node must return the faithful
    combat decision state, not the abbreviated ``decision="unknown"`` +
    "Continue" serialization that ``get_state()`` yields at the post-map
    transition. Reproduces only against the real game (FakeGame's get_state
    mirrors its act response, so it can't surface this)."""
    images = tmp_path / "images"
    images.mkdir()
    logs = tmp_path / "logs"
    logs.mkdir()
    client = TestClient(create_app(images_root=images, logs_dir=logs))

    step = client.post(
        "/api/live/start", json={"character": "Ironclad", "seed": "test", "ascension": 0}
    ).json()
    # Drive into combat and take a couple of combat actions so there's history.
    combat_node = None
    for _ in range(40):
        if step["decision"] == "combat_play":
            break
        opts = [o for o in (step["state"].get("options") or []) if o["enabled"]]
        assert opts
        step = client.post(
            "/api/live/action", json={"action": opts[0]["action"], "args": opts[0]["args"]}
        ).json()["step"]
    assert step["decision"] == "combat_play"

    tl = client.get("/api/live/timeline").json()
    # The first combat_play node on the active path is what we'll rewind to.
    combat_node = next(
        n for n in tl["nodes"] if n["id"] in tl["active_path"] and n["decision"] == "combat_play"
    )
    # Take one more action so the combat node is genuinely in the past.
    client.post("/api/live/action", json={"action": "end_turn", "args": {}})

    seek = client.post("/api/live/seek", json={"node_id": combat_node["id"]}).json()
    assert seek["step"]["decision"] == "combat_play"
    assert "hand" in seek["step"]["state"]  # a real combat decision, not "Continue"

    # Forking from the seeked combat node must succeed and preserve both lines.
    fork = client.post("/api/live/action", json={"action": "end_turn", "args": {}}).json()
    assert fork["error"] is None
    tl2 = client.get("/api/live/timeline").json()
    parent = next(n for n in tl2["nodes"] if n["id"] == combat_node["id"])
    assert len(parent["child_ids"]) == 2  # original child + the new fork


def test_live_search_tree_returns_a_real_tree(tmp_path):
    """The live search-tree endpoint returns a navigable MCTS tree for the
    current combat state (root + children with visits/q, plus a PV)."""
    images = tmp_path / "images"
    images.mkdir()
    logs = tmp_path / "logs"
    logs.mkdir()
    client = TestClient(create_app(images_root=images, logs_dir=logs))

    step = client.post(
        "/api/live/start", json={"character": "Ironclad", "seed": "test", "ascension": 0}
    ).json()
    # Outside combat the endpoint is 409.
    if step["decision"] != "combat_play":
        assert (
            client.post("/api/live/search_tree", json={"config": {"sims": 50}}).status_code == 409
        )
    for _ in range(40):
        if step["decision"] == "combat_play":
            break
        opts = [o for o in (step["state"].get("options") or []) if o["enabled"]]
        assert opts
        step = client.post(
            "/api/live/action", json={"action": opts[0]["action"], "args": opts[0]["args"]}
        ).json()["step"]
    assert step["decision"] == "combat_play"

    tree = client.post("/api/live/search_tree", json={"config": {"sims": 200}}).json()
    assert "error" not in tree, tree.get("error")
    nodes = tree["nodes"]
    assert len(nodes) > 1
    root = nodes[tree["root"]]
    assert root["parent"] is None
    assert root["visits"] > 0
    assert root["children"], "root should have explored children"
    # Children carry an incoming-edge label and per-node stats.
    child = nodes[root["children"][0]]
    assert child["label"]
    assert "q" in child and "visits" in child
    assert isinstance(tree["pv"], list)
    # Each node carries a state summary (outcome after its action).
    summary = root["summary"]
    assert summary["player_hp"] > 0 and summary["player_max_hp"] > 0
    assert "energy" in summary and "round" in summary
    assert summary["enemies"] and all("hp" in e and "max_hp" in e for e in summary["enemies"])
