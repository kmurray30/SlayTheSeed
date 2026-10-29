"""``/api/runs`` ordering: newest by MTIME, not by filename.

Follow mode takes ``runs[0]`` whenever the advisor cannot name the file it is
writing, so this ordering decides which run the workbench tails. A filename
sort put ``mcp_*.jsonl`` (the MCP server's tool logs, which share the logs
directory and do not date-prefix) permanently at the head, and the workbench
sat tailing a stale tool log while a real game played (2026-08-22).
"""

from __future__ import annotations

import os
from pathlib import Path

from fastapi.testclient import TestClient
from workbench.server import create_app


def _app(tmp_path: Path):
    images = tmp_path / "images"
    images.mkdir()
    logs = tmp_path / "logs"
    logs.mkdir()
    return create_app(images_root=images, logs_dir=logs), logs


def _write(logs: Path, name: str, mtime: float) -> Path:
    p = logs / name
    p.write_text("{}\n", encoding="utf-8")
    os.utime(p, (mtime, mtime))
    return p


def test_newest_mtime_wins_over_a_higher_sorting_name(tmp_path):
    app, logs = _app(tmp_path)
    # 'm' > '2', so the tool log wins any name sort -- but it is older.
    _write(logs, "mcp_20260822_115125_18156.jsonl", 1_000_000.0)
    live = _write(logs, "20260822_190850_Ironclad_05BG2YQJN8.jsonl", 2_000_000.0)

    runs = TestClient(app).get("/api/runs").json()["runs"]

    assert Path(runs[0]) == live, "Follow would tail the MCP tool log, not the run"


def test_order_is_newest_first_and_complete(tmp_path):
    app, logs = _app(tmp_path)
    for i, name in enumerate(["a.jsonl", "b.jsonl", "c.jsonl"]):
        _write(logs, name, 1_000_000.0 + i)

    runs = [Path(p).name for p in TestClient(app).get("/api/runs").json()["runs"]]

    assert runs == ["c.jsonl", "b.jsonl", "a.jsonl"]


def test_same_mtime_falls_back_to_the_name_for_a_stable_order(tmp_path):
    # Two files written in the same second must not shuffle between requests.
    app, logs = _app(tmp_path)
    _write(logs, "a.jsonl", 1_000_000.0)
    _write(logs, "b.jsonl", 1_000_000.0)

    c = TestClient(app)
    first = c.get("/api/runs").json()["runs"]

    assert [Path(p).name for p in first] == ["b.jsonl", "a.jsonl"]
    assert c.get("/api/runs").json()["runs"] == first
