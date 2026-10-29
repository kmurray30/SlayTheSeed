"""Contract test for ``scripts/dump_workbench_golden.py``.

Runs ONE tiny scenario (8 steps) through the dumper into a tmp dir and checks
the fixture files, the normalization rules and the hash contract — the hash
recomputed from a stored full response must equal the ``sha256`` recorded in
``requests.jsonl``. Needs the ``sts2_rs`` wheel (``just build-rust``).
"""

from __future__ import annotations

import hashlib
import importlib.util
import json
from pathlib import Path

import pytest

pytest.importorskip("sts2_rs")

_SCRIPT = Path(__file__).resolve().parents[3] / "scripts" / "dump_workbench_golden.py"


@pytest.fixture(autouse=True)
def _undo_determinism_patches(monkeypatch):
    """The script's ``apply_determinism_patches`` mutates process-wide state
    (``STS2_ASSETS_BASE_URL``, ``server._ASSETS_BASE_URL``, ``saves.datetime``,
    the ``ppo_policy`` champion loaders) and has no undo — it is written for
    a one-shot process. Under ``-n auto`` this module shares a worker with
    the rest of the suite, and the art-enrichment tests scheduled after it
    saw ``https://assets.test`` as the art base (20 order-dependent
    failures). Record the originals so pytest restores them."""
    import os

    from workbench import ppo_policy, saves, server

    monkeypatch.setattr(saves, "datetime", saves.datetime)
    monkeypatch.setattr(server, "_ASSETS_BASE_URL", server._ASSETS_BASE_URL)
    monkeypatch.setattr(ppo_policy, "_get_rust_agent", ppo_policy._get_rust_agent)
    monkeypatch.setattr(ppo_policy, "_get_policy", ppo_policy._get_policy)
    for var in ("STS2_ASSETS_BASE_URL", "STS2_WB_PPO_ONNX", "STS2_WB_PPO_CKPT"):
        # `setenv` records the prior value (or its absence) for the undo.
        monkeypatch.setenv(var, os.environ.get(var, ""))
        if var not in os.environ or os.environ[var] == "":
            monkeypatch.delenv(var, raising=False)
    yield
    ppo_policy._reset_for_tests()


def _load_script():
    spec = importlib.util.spec_from_file_location("dump_workbench_golden", _SCRIPT)
    mod = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(mod)
    return mod


def _walk(obj, path=()):
    """Yield ``(key path, value)`` for every node of a decoded JSON value."""
    yield path, obj
    if isinstance(obj, dict):
        for k, v in obj.items():
            yield from _walk(v, (*path, k))
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            yield from _walk(v, (*path, i))


TINY = {
    "name": "tiny",
    "character": "Ironclad",
    "seed": "G0LDEN0001",
    "ascension": 0,
    "act1": "overgrowth",
    "max_steps": 8,
    "neow_option": 0,
    "probes": [
        {"at": 3, "kind": "state"},
        {"at": 4, "kind": "map"},
        {"at": 4, "kind": "timeline"},
        {"at": 5, "kind": "seek", "back": 2},
        {"at": 6, "kind": "save", "name": "golden mid"},
        {"at": 6, "kind": "saves_list"},
        {"at": 7, "kind": "load", "name": "golden mid"},
    ],
}


def test_normalize_contract():
    mod = _load_script()
    raw = {
        "z": 1.23456789,
        "a": {"path": "/tmp/x", "log_path": "/tmp/y", "keep": [1.0, 2, True, None, "é"]},
        "m": [{"b": 2, "a": 1}],
    }
    norm = mod.normalize(raw)
    assert list(norm) == ["a", "m", "z"]
    assert "path" not in norm["a"] and "log_path" not in norm["a"]
    assert repr(norm["z"]) == "1.2346"
    # floats stay floats, ints/bools/None/str untouched
    assert norm["a"]["keep"] == [1.0, 2, True, None, "é"]
    assert isinstance(norm["a"]["keep"][0], float) and isinstance(norm["a"]["keep"][1], int)
    assert list(norm["m"][0]) == ["a", "b"]
    encoded = mod.canonical_json(norm)
    assert encoded == '{"a":{"keep":[1.0,2,true,null,"é"]},"m":[{"a":1,"b":2}],"z":1.2346}'
    assert mod.sha256_of(norm) == hashlib.sha256(encoded.encode("utf-8")).hexdigest()
    assert mod.normalize(norm) == norm  # idempotent


def test_tiny_scenario_dump(tmp_path: Path):
    mod = _load_script()
    out_root = tmp_path / "golden"
    summary = mod.run_scenario(TINY, out_root, full_steps=80)
    assert "error" not in summary
    out = out_root / "tiny"
    assert (out / "requests.jsonl").is_file()
    assert (out / "meta.json").is_file()
    lines = [json.loads(ln) for ln in (out / "requests.jsonl").read_text().splitlines()]
    # start + 8 policy actions + 8 probe requests (seek = timeline + seek),
    # then the replay block (M4): quit, the log listed / loaded, the replay
    # routes over it, "play from here".
    quit_i = next(ln["i"] for ln in lines if ln["route"] == "/api/live/quit")
    assert quit_i == 1 + 8 + 8
    replay_routes = [ln["route"] for ln in lines[quit_i:]]
    for route in (
        "/api/runs",
        "/api/load",
        "/api/timeline",
        "/api/map",
        "/api/step/0",
        "/api/deck/trend",
        "/api/wpa-review",
        "/api/regret-review",
        "/api/mcts-runs",
        "/api/live/from_replay",
    ):
        assert route in replay_routes, route
    assert [ln["i"] for ln in lines] == list(range(len(lines)))
    assert lines[0]["method"] == "POST" and lines[0]["route"] == "/api/live/start"
    assert lines[0]["body"]["seed"] == "G0LDEN0001" and lines[0]["body"]["backend"] == "rust"
    routes = [ln["route"] for ln in lines]
    for route in (
        "/api/live/state",
        "/api/live/map",
        "/api/live/timeline",
        "/api/live/seek",
        "/api/live/save",
        "/api/saves",
        "/api/saves/load",
    ):
        assert route in routes, route
    for ln in lines:
        assert set(ln) == {"i", "method", "route", "body", "status", "sha256", "decision"}
        # The deliberate non-200s: the replay block asks for a step past the
        # end (404) and a search tree off combat (409) — answers the port
        # must match.
        assert (
            ln["status"] == 200
            or ln["route"].startswith("/api/step/")
            or ln["route"] == "/api/search_tree"
        ), ln
        # POSTs carry a dict, except the bodiless quit / verdict-style ones.
        assert (
            ln["body"] is None
            if ln["method"] == "GET"
            else (ln["body"] is None or isinstance(ln["body"], dict))
        )
        assert len(ln["sha256"]) == 64
    # a Step-carrying response records its decision; map/timeline/save/list do not
    assert lines[0]["decision"] and all(
        ln["decision"] is None
        for ln in lines
        if ln["route"] in ("/api/live/map", "/api/live/timeline")
    )
    assert all(ln["decision"] for ln in lines if ln["route"] == "/api/live/action")

    # Every probe (and the start, a first-seen pair) has a stored full body whose
    # normalized form hashes to the recorded sha256 and re-normalizes to itself.
    stored = {int(p.stem) for p in (out / "responses").glob("*.json")}
    # (Up to the quit: the replay block stores its bulky bodies only for a
    # ``full`` scenario — the hash is the gate there.)
    probe_idx = {ln["i"] for ln in lines[:quit_i] if ln["route"] != "/api/live/action"}
    assert probe_idx <= stored
    assert 0 in stored
    for i in sorted(stored):
        text = (out / "responses" / f"{i}.json").read_text(encoding="utf-8")
        body = json.loads(text)
        assert mod.normalize(body) == body
        assert text == mod.pretty_json(body)
        assert mod.sha256_of(body) == lines[i]["sha256"]
        for path, node in _walk(body):
            if isinstance(node, dict):
                assert "path" not in node and "log_path" not in node, path
            if isinstance(node, float):
                assert node == round(node, 4), path
    # the save/list responses carry the pinned clock and no host path
    save_i = next(ln["i"] for ln in lines if ln["route"] == "/api/live/save")
    save_body = json.loads((out / "responses" / f"{save_i}.json").read_text())
    # 6 actions were played, but the seek @5 went 2 nodes back before the 6th,
    # so the active line the save captures is 6 - 2 = 4 actions long.
    assert save_body == {"name": "golden mid", "step_count": 4}
    list_i = next(ln["i"] for ln in lines if ln["route"] == "/api/saves")
    listing = json.loads((out / "responses" / f"{list_i}.json").read_text())
    assert listing["saves"][0]["saved_at"] == mod.FIXED_SAVED_AT
    # art URLs use the pinned base; PPO values are stubbed out
    start = json.loads((out / "responses" / "0.json").read_text())
    assert start["assets_base"] == "https://assets.test"
    tl_i = next(ln["i"] for ln in lines if ln["route"] == "/api/live/timeline")
    timeline = json.loads((out / "responses" / f"{tl_i}.json").read_text())
    assert all(n["value"] is None for n in timeline["nodes"])

    meta = json.loads((out / "meta.json").read_text())
    assert meta["scenario"] == TINY
    assert meta["steps_played"] == 8 and meta["requests"] == len(lines)
    assert meta["patches"]["ppo_disabled"] is True
    assert "decision:event_choice" in meta["coverage"]
    assert "engine" in meta and "python" in meta
