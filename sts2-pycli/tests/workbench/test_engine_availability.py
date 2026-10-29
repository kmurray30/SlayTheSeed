"""The C# engine must never be a dead-end option.

The deployed workbench image is deliberately C#-free (root ``Dockerfile``,
stage 3: no .NET runtime, no game DLLs, no ``src/Sts2Headless``). Selecting
"C#" in the engine dropdown there made ``Game()`` spawn ``dotnet``, which does
not exist, so ``subprocess.Popen`` raised ``FileNotFoundError`` — a 500 the SPA
never showed. From the player's chair, "Start run" simply did nothing.

Two guards, both here:

* ``/api/capabilities`` tells the SPA which engines this deployment can
  actually run, so the dropdown never offers a dead one.
* ``/api/live/start`` answers an unavailable engine with a 400 that says so,
  not an unhandled OSError.
"""

from __future__ import annotations

import os

import sts2_client
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


# ── sts2_client.headless_available ───────────────────────────────────────────


def test_headless_available_true_for_an_executable_binary(tmp_path, monkeypatch):
    binary = tmp_path / "Sts2Headless"
    binary.write_text("#!/bin/sh\n")
    binary.chmod(0o755)
    monkeypatch.setenv("STS2_HEADLESS_BIN", str(binary))
    assert sts2_client.headless_available() is True


def test_headless_available_false_when_the_pinned_binary_is_missing(tmp_path, monkeypatch):
    monkeypatch.setenv("STS2_HEADLESS_BIN", str(tmp_path / "nope"))
    assert sts2_client.headless_available() is False


def test_headless_available_false_without_dotnet_or_binary(tmp_path, monkeypatch):
    """The deployed container exactly: no built binary, no ``dotnet`` anywhere."""
    monkeypatch.delenv("STS2_HEADLESS_BIN", raising=False)
    monkeypatch.setattr(sts2_client, "HEADLESS_BIN", str(tmp_path / "absent"))
    monkeypatch.setattr(sts2_client.shutil, "which", lambda _name: None)
    monkeypatch.setattr(os.path, "isfile", lambda p: False)
    assert sts2_client.headless_available() is False


# ── /api/capabilities ────────────────────────────────────────────────────────


def test_capabilities_omits_csharp_when_the_engine_cannot_spawn(tmp_path, monkeypatch):
    monkeypatch.setattr(sts2_client, "headless_available", lambda: False)
    c = _client(tmp_path, monkeypatch)
    caps = c.get("/api/capabilities").json()
    assert caps["backends"] == ["rust"]


def test_capabilities_lists_csharp_when_the_engine_is_present(tmp_path, monkeypatch):
    monkeypatch.setattr(sts2_client, "headless_available", lambda: True)
    c = _client(tmp_path, monkeypatch)
    caps = c.get("/api/capabilities").json()
    assert caps["backends"] == ["rust", "csharp"]


# ── /api/live/start ──────────────────────────────────────────────────────────


def test_start_on_a_missing_engine_is_a_400_that_names_it(tmp_path, monkeypatch):
    """Not a 500. The SPA renders the body, so it has to read as an answer."""
    from workbench import branching

    def _no_dotnet(*_a, **_k):
        raise FileNotFoundError(2, "No such file or directory: 'dotnet'")

    c = _client(tmp_path, monkeypatch)
    monkeypatch.setattr(branching, "Game", _no_dotnet)
    r = c.post("/api/live/start", json={"character": "Ironclad", "backend": "csharp"})
    assert r.status_code == 400
    assert "csharp" in r.json()["detail"]


def test_start_on_an_unbuilt_engine_is_a_400_too(tmp_path, monkeypatch):
    """The other real shape of "no engine": ``dotnet`` resolves, so
    :func:`headless_available` says yes, but the oracle was never built
    (``just build-cs``) — the child dies and ``Game`` raises
    ``RuntimeError("EOF from game process")``. Same dead end, same 400."""
    from workbench import branching

    def _eof(*_a, **_k):
        raise RuntimeError("EOF from game process")

    c = _client(tmp_path, monkeypatch)
    monkeypatch.setattr(branching, "Game", _eof)
    r = c.post("/api/live/start", json={"character": "Ironclad", "backend": "csharp"})
    assert r.status_code == 400
    assert "EOF from game process" in r.json()["detail"]


def test_start_still_works_on_an_available_engine(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    r = c.post("/api/live/start", json={"character": "Ironclad", "backend": "csharp"})
    assert r.status_code == 200
