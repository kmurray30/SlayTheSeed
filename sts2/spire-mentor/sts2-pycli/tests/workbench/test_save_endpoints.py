"""Endpoint tests for named save/load, driven by the FakeGame stub."""

from fake_game import FakeGame  # shared stub from tests/workbench/fake_game.py
from fastapi.testclient import TestClient
from workbench.server import create_app


def _client(tmp_path, monkeypatch):
    from workbench import branching

    monkeypatch.setattr(branching, "Game", FakeGame)
    images = tmp_path / "images"
    images.mkdir()
    logs = tmp_path / "logs"
    logs.mkdir()
    return TestClient(create_app(images_root=images, logs_dir=logs))


def _start_and_advance(c):
    """Start a run and step into combat so there's a non-trivial active line."""
    c.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    c.post("/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}})


def test_save_400_when_no_run(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    assert c.post("/api/live/save", json={"name": "x"}).status_code == 400


def test_save_400_blank_name(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    _start_and_advance(c)
    assert c.post("/api/live/save", json={"name": "   "}).status_code == 400


def test_save_records_active_line(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    _start_and_advance(c)
    r = c.post("/api/live/save", json={"name": "before elite"})
    assert r.status_code == 200
    body = r.json()
    assert body["name"] == "before elite"
    assert body["step_count"] == 1  # one action since the root (select_map_node)


def test_list_shows_saved_run(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    _start_and_advance(c)
    c.post("/api/live/save", json={"name": "run one"})
    rows = c.get("/api/saves").json()["saves"]
    assert len(rows) == 1
    assert rows[0]["name"] == "run one"
    assert rows[0]["character"] == "Ironclad"
    assert rows[0]["seed"] == "S"  # canonicalized on start (uppercase)
    assert "line" not in rows[0]  # headers only


def test_load_round_trip_restores_position(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    _start_and_advance(c)
    saved = c.post("/api/live/save", json={"name": "mid"}).json()
    # End the run so there's no active session, then load the save back.
    c.post("/api/live/quit")
    assert c.get("/api/live/state").status_code == 400

    r = c.post("/api/saves/load", json={"name": "mid"})
    assert r.status_code == 200
    assert r.json()["decision"] == "combat_play"  # replayed back into combat
    # The reloaded run is now the live session.
    assert c.get("/api/live/state").status_code == 200
    assert saved["step_count"] == 1


def test_load_unknown_404(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    assert c.post("/api/saves/load", json={"name": "ghost"}).status_code == 404


def test_overwrite_same_name(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    _start_and_advance(c)
    c.post("/api/live/save", json={"name": "dup"})
    c.post("/api/live/save", json={"name": "dup"})
    rows = c.get("/api/saves").json()["saves"]
    assert len([r for r in rows if r["name"] == "dup"]) == 1


def test_save_records_the_constructor(tmp_path, monkeypatch):
    """Every save must say HOW its run was started.

    ``load_line`` picks the interactive (text seed) or run-mode (integer seed)
    constructor from this field. Without it the loader guessed interactive for
    everything, so a saved "play to end" run replayed against a different game
    and 409'd with ``invalid_for_phase``.
    """
    c = _client(tmp_path, monkeypatch)
    _start_and_advance(c)
    c.post("/api/live/save", json={"name": "hand played"})
    rows = c.get("/api/saves").json()["saves"]
    assert rows[0]["run_mode"] is False


def test_legacy_csharp_save_with_a_numeric_seed_loads(tmp_path, monkeypatch):
    """Probing run-mode first must not 500 on a C# save.

    ``restore_attempts`` orders a legacy save's two attempts by its seed, and a
    digits-only seed says "try run-mode first" — but run-mode is rust-only, so
    that attempt raises ValueError. It is a rejected attempt, not a fault: the
    loader has to fall through to the interactive constructor.
    """
    import json

    from workbench import saves as saves_mod

    c = _client(tmp_path, monkeypatch)
    c.post("/api/live/start", json={"character": "Ironclad", "seed": "12345", "backend": "csharp"})
    c.post("/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}})
    c.post("/api/live/save", json={"name": "legacy cs"})
    c.post("/api/live/quit")

    path = saves_mod.save_dir(tmp_path / "logs") / "legacy-cs.json"
    payload = json.loads(path.read_text())
    del payload["run_mode"]
    path.write_text(json.dumps(payload))

    r = c.post("/api/saves/load", json={"name": "legacy cs"})
    assert r.status_code == 200, r.json()


def test_save_carries_node_values_and_load_replays_them(tmp_path, monkeypatch):
    """The PPO values are cached in the save, not recomputed on restore.

    Restoring replays the line through the engine, and node creation used to
    call the critic at every non-combat decision — a 320-token transformer
    forward each. On a ~780-step act-3 line that was ~85% of the load and took
    it past two minutes. The values are a pure function of states the replay
    reproduces exactly, so the saving session's copies are stamped back on.
    """
    import json

    from workbench import branching
    from workbench import saves as saves_mod

    c = _client(tmp_path, monkeypatch)
    _start_and_advance(c)
    c.post("/api/live/save", json={"name": "valued"})

    # FakeGame has no RunManager, so the critic yields None for every node;
    # write recognisable values into the save to prove they round-trip.
    path = saves_mod.save_dir(tmp_path / "logs") / "valued.json"
    payload = json.loads(path.read_text())
    assert len(payload["node_values"]) == len(payload["line"]) + 1  # root first
    payload["node_values"] = [0.25, 0.75]
    path.write_text(json.dumps(payload))

    def _boom(self, state):
        raise AssertionError("restore must not run the PPO critic")

    monkeypatch.setattr(branching.BranchingLiveSession, "_node_value", _boom)
    assert c.post("/api/saves/load", json={"name": "valued"}).status_code == 200

    nodes = c.get("/api/live/timeline").json()["nodes"]
    assert [n["value"] for n in sorted(nodes, key=lambda n: n["idx"])] == [0.25, 0.75]


def test_legacy_save_without_node_values_still_computes_them(tmp_path, monkeypatch):
    """A save written before the field existed must not lose its WPA curve."""
    import json

    from workbench import branching
    from workbench import saves as saves_mod

    c = _client(tmp_path, monkeypatch)
    _start_and_advance(c)
    c.post("/api/live/save", json={"name": "legacy"})

    path = saves_mod.save_dir(tmp_path / "logs") / "legacy.json"
    payload = json.loads(path.read_text())
    del payload["node_values"]
    path.write_text(json.dumps(payload))

    monkeypatch.setattr(branching.BranchingLiveSession, "_node_value", lambda self, state: 0.5)
    assert c.post("/api/saves/load", json={"name": "legacy"}).status_code == 200

    nodes = c.get("/api/live/timeline").json()["nodes"]
    assert [n["value"] for n in nodes] == [0.5, 0.5]


def test_listing_omits_the_per_step_fields(tmp_path, monkeypatch):
    """A header carries neither the line nor its parallel value array.

    Both are one entry per step, so leaving either in would put a whole run's
    worth of data per row into the load dialog's listing.
    """
    c = _client(tmp_path, monkeypatch)
    _start_and_advance(c)
    c.post("/api/live/save", json={"name": "headers only"})
    row = c.get("/api/saves").json()["saves"][0]
    assert "line" not in row
    assert "node_values" not in row
