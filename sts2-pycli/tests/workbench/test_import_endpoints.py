"""Endpoint tests for the live-state import.

The rule these pin hardest is the one that costs the user something when it
breaks: a rejected paste must not take away the run they already had. The
import validates everything BEFORE tearing the live session down, so a 422 or
a 409 leaves the previous run playable.
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest

pytest.importorskip("sts2_rs", reason="the state importer needs the sts2_rs extension")

from fake_game import FakeGame  # shared stub from tests/workbench/fake_game.py
from fastapi.testclient import TestClient
from workbench.server import create_app

FIXTURES = Path(__file__).resolve().parents[1] / "fixtures" / "realgame_adapter"


def _client(tmp_path, monkeypatch):
    from workbench import branching

    monkeypatch.setattr(branching, "Game", FakeGame)
    images = tmp_path / "images"
    images.mkdir()
    logs = tmp_path / "logs"
    logs.mkdir()
    # Own SimLab results root: the default is CWD-relative and shared, so a
    # sweep launched here would show up in (and be seen by) every other test's
    # SimLab instance.
    monkeypatch.setenv("STS2_SIMLAB_RESULTS", str(tmp_path / "simlab"))
    return TestClient(create_app(images_root=images, logs_dir=logs))


def _paste() -> str:
    wire = json.loads((FIXTURES / "single_enemy_ironclad.json").read_text(encoding="utf-8"))
    return json.dumps({**wire["state"], "type": "advice_request", "advice_for": "combat_action"})


def test_preview_describes_without_starting_anything(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    r = c.post("/api/live/import-state/preview", json={"payload": _paste()})
    assert r.status_code == 200
    body = r.json()
    assert body["has_combat"] is True
    assert body["run"]["character"] == "Ironclad"
    assert body["run"]["seed"] == "42"
    # It only looked — there is still no live run.
    assert c.get("/api/live/state").status_code == 400


def test_preview_422_on_junk(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    r = c.post("/api/live/import-state/preview", json={"payload": "not json"})
    assert r.status_code == 422
    assert "JSON" in r.json()["detail"]


def test_import_resumes_the_exact_fight(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    r = c.post("/api/live/import-state", json={"payload": _paste(), "lane": "combat"})
    assert r.status_code == 200, r.text
    step = r.json()
    assert step["state"]["decision"] == "combat_play"
    assert step["import"]["lane"] == "combat"
    # The capture predates the RNG block, and the import says so rather than
    # implying a fidelity it does not have.
    assert step["import"]["rng_restored"] is False
    assert step["import"]["warnings"]
    assert c.get("/api/live/state").json()["state"]["decision"] == "combat_play"


def test_a_bad_paste_does_not_cost_the_current_run(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    c.post("/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"})
    before = c.get("/api/live/state").json()

    assert c.post("/api/live/import-state", json={"payload": "{}"}).status_code == 422

    after = c.get("/api/live/state").json()
    assert after["state"]["decision"] == before["state"]["decision"]


def test_a_lane_the_paste_cannot_fill_is_409(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    wire = json.loads((FIXTURES / "single_enemy_ironclad.json").read_text(encoding="utf-8"))
    screen = {
        "type": "advice_request",
        "advice_for": "shop",
        "seed": "42",
        "act": 1,
        "floor": 5,
        "run_state": wire["state"]["run_state"],
        "options": [],
    }
    r = c.post("/api/live/import-state", json={"payload": json.dumps(screen), "lane": "combat"})
    assert r.status_code == 409
    assert "no fight" in r.json()["detail"]


def test_simlab_scenario_needs_an_import(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    assert c.get("/api/live/simlab/scenario").status_code == 409

    c.post("/api/live/import-state", json={"payload": _paste(), "lane": "combat"})
    body = c.get("/api/live/simlab/scenario").json()
    assert body["character"] == "Ironclad"
    assert body["deck_cards"], "the imported deck is the one to sweep"
    assert body["encounter"].startswith("monsters:")


def test_simlab_sweep_from_the_live_board(tmp_path, monkeypatch):
    c = _client(tmp_path, monkeypatch)
    c.post("/api/live/import-state", json={"payload": _paste(), "lane": "combat"})
    r = c.post("/api/live/simlab/sweep", json={"name": "t", "n_seeds": 2, "strategies": ["random"]})
    if r.status_code == 503:
        pytest.skip("SimLab deps are not installed in this environment")
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["sweep_id"]
    assert body["url"].startswith("/simlab.html?sweep=")
    assert len(body["cells"]) == 1


def test_live_position_hands_the_fight_to_simlab(tmp_path, monkeypatch):
    """ "SimLab from here": the active board as a sandbox position, hand in order."""
    c = _client(tmp_path, monkeypatch)
    assert c.get("/api/live/position").status_code == 400
    c.post("/api/live/import-state", json={"payload": _paste(), "lane": "combat"})
    r = c.get("/api/live/position")
    if r.status_code == 503:
        pytest.skip("SimLab deps are not installed in this environment")
    assert r.status_code == 200, r.text
    body = r.json()
    pos = body["position"]
    assert pos["schema"] == "simlab-position/1"
    assert [card["card"] for card in pos["piles"]["hand"]] == [
        "DEFEND_IRONCLAD",
        "DEFEND_IRONCLAD",
        "STRIKE_IRONCLAD",
        "STRIKE_IRONCLAD",
        "BASH",
    ]
    assert pos["monsters"][0]["monster"] == "ShrinkerBeetle"
    assert body["label"]
