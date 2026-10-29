from fake_game import FakeGame
from fastapi.testclient import TestClient
from workbench.server import create_app


def _client(tmp_path, monkeypatch):
    from workbench import branching

    monkeypatch.setattr(branching, "Game", FakeGame)
    monkeypatch.delenv("STS2_BUGREPORT_BUCKET", raising=False)  # force local fallback
    images = tmp_path / "images"
    images.mkdir()
    logs = tmp_path / "logs"
    logs.mkdir()
    return TestClient(create_app(images_root=images, logs_dir=logs))


def test_capture_409_when_nothing_to_capture(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    r = c.post("/api/bugreport", json={"mode": "live"})
    assert r.status_code == 409


def test_capture_then_retrieve_roundtrip(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    c.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    r = c.post("/api/bugreport", json={"mode": "live", "screen": "combat", "note": "bug!"})
    assert r.status_code == 200
    body = r.json()
    assert body["id"].startswith("wb-")
    assert body["url"]  # file:// in local-fallback mode

    got = c.get(f"/api/bugreport/{body['id']}")
    assert got.status_code == 200
    dump = got.json()
    assert dump["schema_version"] == 1
    assert dump["meta"]["id"] == body["id"]
    assert dump["meta"]["mode"] == "live"
    assert dump["meta"]["captured_at"].endswith("Z")
    assert dump["ui"]["screen"] == "combat"
    assert dump["note"] == "bug!"


def test_retrieve_unknown_404(tmp_path, monkeypatch):
    """Valid-format id that doesn't exist → 404."""
    c = _client(tmp_path, monkeypatch)
    assert c.get("/api/bugreport/wb-0123456789abcdef").status_code == 404


def test_retrieve_malformed_id_404(tmp_path, monkeypatch):
    """Malformed id (fails regex) → 404 via ValueError guard."""
    c = _client(tmp_path, monkeypatch)
    assert c.get("/api/bugreport/wb-nope12").status_code == 404
