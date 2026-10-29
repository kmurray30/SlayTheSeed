"""Unit tests for the named-save module (no game process)."""

from workbench import saves


def test_slugify_filesystem_safe():
    assert saves.slugify("Before Elite!") == "before-elite"
    assert saves.slugify("  spaces  ") == "spaces"
    assert saves.slugify("///") == "save"  # nothing left -> fallback


def test_save_dir_sits_beside_logs(tmp_path):
    logs = tmp_path / "logs"
    assert saves.save_dir(logs) == tmp_path / "saves" / "workbench"


def _write(directory, name="run a", line=None, backend="rust", run_mode=False, seed="s1"):
    return saves.write_save(
        directory,
        name=name,
        character="Ironclad",
        seed=seed,
        ascension=2,
        backend=backend,
        run_mode=run_mode,
        progress={"act": 1, "floor": 4, "hp": 50, "max_hp": 72},
        line=line if line is not None else [{"action": "play_card", "args": {"card_index": 0}}],
    )


def test_write_then_read_round_trip(tmp_path):
    d = saves.save_dir(tmp_path / "logs")
    _write(d)
    payload = saves.read_save(d, "run a")
    assert payload is not None
    assert payload["name"] == "run a"
    assert payload["character"] == "Ironclad"
    assert payload["seed"] == "s1"
    assert payload["ascension"] == 2
    assert payload["backend"] == "rust"
    assert payload["run_mode"] is False
    assert payload["step_count"] == 1
    assert payload["line"] == [{"action": "play_card", "args": {"card_index": 0}}]
    assert payload["progress"] == {"act": 1, "floor": 4, "hp": 50, "max_hp": 72}


def test_list_strips_line_and_sorts_newest_first(tmp_path):
    d = saves.save_dir(tmp_path / "logs")
    p1 = _write(d, name="old")
    p2 = _write(d, name="new")
    # Force a later mtime/timestamp ordering by rewriting the saved_at fields.
    import json

    a = json.loads(p1.read_text())
    a["saved_at"] = "2026-01-01T00:00:00Z"
    p1.write_text(json.dumps(a))
    b = json.loads(p2.read_text())
    b["saved_at"] = "2026-05-01T00:00:00Z"
    p2.write_text(json.dumps(b))

    rows = saves.list_saves(d)
    assert [r["name"] for r in rows] == ["new", "old"]
    assert all("line" not in r for r in rows)  # headers only


def test_overwrite_same_name_keeps_one_file(tmp_path):
    d = saves.save_dir(tmp_path / "logs")
    _write(d, name="dup", line=[{"action": "end_turn", "args": {}}])
    _write(d, name="dup", line=[{"action": "play_card", "args": {"card_index": 3}}])
    rows = saves.list_saves(d)
    assert len(rows) == 1
    assert saves.read_save(d, "dup")["line"] == [{"action": "play_card", "args": {"card_index": 3}}]


def test_list_skips_corrupt_and_bad_schema(tmp_path):
    d = saves.save_dir(tmp_path / "logs")
    d.mkdir(parents=True)
    (d / "garbage.json").write_text("{not json")
    (d / "wrongver.json").write_text('{"schema_version": 999, "name": "x"}')
    _write(d, name="good")
    rows = saves.list_saves(d)
    assert [r["name"] for r in rows] == ["good"]


def test_read_unknown_returns_none(tmp_path):
    d = saves.save_dir(tmp_path / "logs")
    assert saves.read_save(d, "nope") is None


def test_pre_backend_save_reads_without_field(tmp_path):
    # Saves written before the `backend` field existed (schema_version 1, no
    # `backend` key) must still load; the loader defaults them to DEFAULT_BACKEND.
    import json

    d = saves.save_dir(tmp_path / "logs")
    d.mkdir(parents=True)
    (d / "legacy.json").write_text(
        json.dumps(
            {
                "schema_version": saves.SCHEMA_VERSION,
                "name": "legacy",
                "saved_at": "2026-01-01T00:00:00Z",
                "character": "Ironclad",
                "seed": "s1",
                "ascension": 0,
                "progress": {},
                "step_count": 0,
                "line": [],
            }
        )
    )
    payload = saves.read_save(d, "legacy")
    assert payload is not None
    assert payload.get("backend", saves.DEFAULT_BACKEND) == "csharp"


def test_restore_attempts_uses_the_recorded_constructor(tmp_path):
    d = saves.save_dir(tmp_path / "logs")
    _write(d, name="hand played", run_mode=False)
    _write(d, name="autoplayed", run_mode=True, seed="12345")
    assert saves.restore_attempts(saves.read_save(d, "hand played")) == [False]
    assert saves.restore_attempts(saves.read_save(d, "autoplayed")) == [True]


def test_restore_attempts_probes_both_for_legacy_saves(tmp_path):
    """A save written before ``run_mode`` existed must not be guessed at once.

    The numeric seed is the tell of a run-mode ("play to end") run, so that is
    tried first — but a player can type digits, so the other is still tried.
    """
    import json

    d = saves.save_dir(tmp_path / "logs")
    for name, seed in (("legacy digits", "2143235649"), ("legacy text", "ABC123")):
        path = _write(d, name=name, seed=seed)
        payload = json.loads(path.read_text())
        del payload["run_mode"]  # as written by the pre-fix workbench
        path.write_text(json.dumps(payload))
    assert saves.restore_attempts(saves.read_save(d, "legacy digits")) == [True, False]
    assert saves.restore_attempts(saves.read_save(d, "legacy text")) == [False, True]


def test_node_values_round_trip_and_default_to_none(tmp_path):
    """The cached PPO values survive the file; omitting them is legal."""
    d = saves.save_dir(tmp_path / "logs")
    saves.write_save(
        d,
        name="valued",
        character="Ironclad",
        seed="s1",
        ascension=0,
        backend="rust",
        run_mode=False,
        progress={},
        line=[{"action": "play_card", "args": {}}],
        node_values=[0.5, None],
    )
    assert saves.read_save(d, "valued")["node_values"] == [0.5, None]
    _write(d, name="plain")
    assert saves.read_save(d, "plain")["node_values"] is None
