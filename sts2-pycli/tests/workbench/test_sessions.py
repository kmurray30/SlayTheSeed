"""Per-client session isolation: two browsers must not share one run."""

from __future__ import annotations

import json

from fake_game import FakeGame  # shared stub from tests/workbench/fake_game.py
from fastapi.testclient import TestClient
from workbench.server import create_app
from workbench.session import SessionRegistry


def _app(tmp_path, monkeypatch):
    from workbench import branching

    # The live API drives a BranchingLiveSession, which spawns its game via
    # ``branching.Game`` — patch there (not ``live.Game``).
    monkeypatch.setattr(branching, "Game", FakeGame)
    images = tmp_path / "images"
    images.mkdir()
    logs = tmp_path / "logs"
    logs.mkdir()
    return create_app(images_root=images, logs_dir=logs)


# -- HTTP-level isolation ---------------------------------------------------


def test_two_clients_get_isolated_live_runs(tmp_path, monkeypatch):
    app = _app(tmp_path, monkeypatch)
    a = TestClient(app)
    b = TestClient(app)

    # A starts a live run; B never does.
    assert (
        a.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    ).status_code == 200

    # B must NOT see A's run leak into its session (the bug this fixes).
    assert b.get("/api/live/state").status_code == 400
    # A still owns its run.
    assert a.get("/api/live/state").status_code == 200
    # The two browsers were handed distinct session ids.
    assert a.cookies.get("sts2_sid")
    assert a.cookies.get("sts2_sid") != b.cookies.get("sts2_sid")


def test_one_clients_quit_does_not_end_anothers_run(tmp_path, monkeypatch):
    app = _app(tmp_path, monkeypatch)
    a = TestClient(app)
    b = TestClient(app)
    a.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    b.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    # A ends its run; B's must survive (separate sessions, separate games).
    assert a.post("/api/live/quit").status_code == 200
    assert a.get("/api/live/state").status_code == 400
    assert b.get("/api/live/state").status_code == 200


def test_loaded_replay_is_isolated_per_client(tmp_path, monkeypatch):
    app = _app(tmp_path, monkeypatch)
    log = tmp_path / "logs" / "run.jsonl"
    log.write_text(
        json.dumps(
            {
                "step": 1,
                "ts": "2026-05-24T00:00:00",
                "type": "state",
                "data": {"decision": "map_select", "player": {"name": "Ironclad"}},
            }
        )
        + "\n"
    )
    a = TestClient(app)
    b = TestClient(app)
    assert a.post("/api/load", json={"path": str(log)}).status_code == 200
    # A has a loaded run; B does not — the load did not leak across sessions.
    assert a.get("/api/timeline").status_code == 200
    assert b.get("/api/timeline").status_code == 400


def test_cookie_is_issued_on_the_first_response(tmp_path, monkeypatch):
    # The middleware sets the sid cookie on ANY first request — even a stateless
    # one that never resolves a session — so a fresh browser's id is established
    # before its (possibly concurrent) API calls fire. This is what eliminates
    # the orphan-session race.
    app = _app(tmp_path, monkeypatch)
    c = TestClient(app)
    r = c.get("/api/runs")  # stateless endpoint, no get_session dependency
    assert r.status_code == 200
    assert "sts2_sid" in r.cookies  # Set-Cookie present on the very first hit


def test_same_client_keeps_one_session(tmp_path, monkeypatch):
    app = _app(tmp_path, monkeypatch)
    c = TestClient(app)
    c.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    sid = c.cookies.get("sts2_sid")
    assert sid
    # A follow-up request reuses the cookie → same session → the run is found.
    assert c.get("/api/live/state").status_code == 200
    assert c.cookies.get("sts2_sid") == sid


# -- Registry unit behavior -------------------------------------------------


class _FakeLive:
    """Stand-in for a BranchingLiveSession to assert resource release."""

    def __init__(self) -> None:
        self.quit_called = False

    def quit(self) -> dict[str, bool]:
        self.quit_called = True
        return {"ok": True}


def test_registry_eviction_releases_live_resources():
    # ttl < 0 → any session is past its TTL on the next touch.
    reg = SessionRegistry(ttl_seconds=-1)
    sess = reg.get_or_create("x")
    live = _FakeLive()
    sess.live = live
    assert reg.get("x") is None  # expired → reaped
    assert live.quit_called  # ...and its engine subprocess released


def test_registry_get_or_create_is_idempotent():
    reg = SessionRegistry()
    a = reg.get_or_create("same")
    b = reg.get_or_create("same")
    assert a is b  # concurrent first requests sharing a cookie converge


def test_registry_cap_evicts_least_recently_used():
    reg = SessionRegistry(max_sessions=2)
    s1 = reg.get_or_create("s1")
    reg.get_or_create("s2")
    l1 = _FakeLive()
    s1.live = l1
    reg.get_or_create("s3")  # over cap → evict the LRU (s1, the oldest)
    assert reg.get("s1") is None
    assert l1.quit_called
    assert reg.get("s2") is not None


def test_registry_close_all_releases_every_session():
    reg = SessionRegistry()
    sess = reg.get_or_create("x")
    live = _FakeLive()
    sess.live = live
    reg.close_all()
    assert live.quit_called
    assert reg.get("x") is None
