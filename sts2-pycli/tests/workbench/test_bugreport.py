import pytest
from workbench import bugreport


class _FakeLive:
    character = "Ironclad"
    seed = "s1"
    ascension = 2
    backend = "rust"

    def state(self):
        return {"floor": 3, "act": 1, "options": [{"action": "x"}]}

    def map(self):
        return {"rows": []}

    def timeline(self):
        return {"nodes": [{"idx": 0}], "active_path": [0], "active_leaf": 0}


class _FakeStore:
    def character(self):
        return "Necrobinder"

    def fixture_path(self):
        return "/runs/foo.jsonl"

    def __len__(self):
        return 42

    def get(self, idx):
        # mirrors TimelineStore.get: the full Step at idx, incl. rendered state
        return {"state": {"floor": 9, "act": 2, "step_idx": idx}, "source": "x"}


class _Session:
    def __init__(self, live=None, store=None):
        self.live = live
        self.store = store


def test_build_dump_live():
    d = bugreport.build_dump(
        _Session(live=_FakeLive()),
        mode="live",
        step=None,
        screen="combat",
        overlay=None,
        note="enemy hp wrong",
        run_path=None,
    )
    assert d["schema_version"] == 1
    assert d["meta"]["mode"] == "live"
    assert d["meta"]["character"] == "Ironclad"
    assert d["meta"]["seed"] == "s1"
    assert d["meta"]["ascension"] == 2
    assert d["repro"]["timeline"]["active_leaf"] == 0
    assert d["state"]["game_state"]["floor"] == 3
    assert d["state"]["map"] == {"rows": []}
    assert d["ui"] == {"screen": "combat", "overlay": None}
    assert d["note"] == "enemy hp wrong"


def test_build_dump_replay():
    d = bugreport.build_dump(
        _Session(store=_FakeStore()),
        mode="replay",
        step=7,
        screen="decision",
        overlay="deck",
        note=None,
        run_path="/runs/foo.jsonl",
    )
    assert d["meta"]["mode"] == "replay"
    assert d["meta"]["character"] == "Necrobinder"
    assert d["repro"]["run_file"] == "/runs/foo.jsonl"
    assert d["repro"]["line"] == 7
    assert d["repro"]["step_count"] == 42
    # An ended/loaded run reopens in the replay viewer (engine gone), so a
    # post-session report MUST still carry the rendered state at the viewed step.
    assert d["state"]["game_state"]["floor"] == 9
    assert d["state"]["game_state"]["step_idx"] == 7
    assert d["ui"] == {"screen": "decision", "overlay": "deck"}


def test_build_dump_replay_run_path_none_falls_back_to_fixture():
    d = bugreport.build_dump(
        _Session(store=_FakeStore()),
        mode="replay",
        step=3,
        screen="decision",
        overlay=None,
        note=None,
        run_path=None,
    )
    assert d["repro"]["run_file"] == "/runs/foo.jsonl"  # fixture_path() fallback


def test_build_dump_replay_step_out_of_range_no_state():
    d = bugreport.build_dump(
        _Session(store=_FakeStore()),
        mode="replay",
        step=None,
        screen="map",
        overlay=None,
        note=None,
        run_path="/runs/foo.jsonl",
    )
    assert d["state"]["game_state"] is None  # no valid step -> no snapshot


def test_build_dump_nothing_to_capture():
    with pytest.raises(ValueError, match="nothing to capture"):
        bugreport.build_dump(
            _Session(),
            mode="live",
            step=None,
            screen=None,
            overlay=None,
            note=None,
            run_path=None,
        )


def test_build_dump_no_auth_secrets(monkeypatch):
    monkeypatch.setenv("STS2_AUTH_PASSWORD", "hunter2")
    d = bugreport.build_dump(
        _Session(live=_FakeLive()),
        mode="live",
        step=None,
        screen="combat",
        overlay=None,
        note=None,
        run_path=None,
    )
    assert "hunter2" not in __import__("json").dumps(d)
