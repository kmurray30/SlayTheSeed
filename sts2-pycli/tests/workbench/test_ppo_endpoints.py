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


def _start(c):
    r = c.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    assert r.status_code == 200


def test_run_mode_requires_live_run(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    assert c.post("/api/live/run-mode", json={"off_combat_brain": "ppo"}).status_code == 400


def test_run_mode_toggle_and_validation(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    _start(c)
    r = c.post("/api/live/run-mode", json={"off_combat_brain": "heuristic", "combat_sims": 1000})
    assert r.status_code == 200
    body = r.json()
    assert body["off_combat_brain"] == "heuristic" and body["combat_sims"] == 1000
    assert c.post("/api/live/run-mode", json={"off_combat_brain": "bogus"}).status_code == 400
    assert c.post("/api/live/run-mode", json={"combat_sims": 0}).status_code == 400


def test_ppo_policy_panel_on_fake_backend(tmp_path, monkeypatch):
    # FakeGame has no in-process RunManager -> the panel never applies (409),
    # exactly like auto-action's 409 contract on backends that can't recommend.
    c = _client(tmp_path, monkeypatch)
    assert c.post("/api/live/ppo-policy", json={}).status_code == 400  # no run
    _start(c)
    assert c.post("/api/live/ppo-policy", json={}).status_code == 409


def test_auto_action_with_ppo_selected_still_409s_on_fake_backend(tmp_path, monkeypatch):
    # FakeGame: no rm (PPO can't decide) and no recommended_action (heuristic
    # can't either) -> auto-action keeps its 409 contract with ppo selected.
    c = _client(tmp_path, monkeypatch)
    _start(c)
    assert c.post("/api/live/run-mode", json={"off_combat_brain": "ppo"}).status_code == 200
    assert c.post("/api/live/auto-action").status_code == 409


def test_new_session_defaults(tmp_path, monkeypatch):
    """A fresh live session defaults to off_combat_brain=ppo, ppo_impl=rust."""
    from workbench import branching

    monkeypatch.setattr(branching, "Game", FakeGame)
    sess = branching.BranchingLiveSession(str(tmp_path))
    assert sess.off_combat_brain == "ppo" and sess.ppo_impl == "rust"
