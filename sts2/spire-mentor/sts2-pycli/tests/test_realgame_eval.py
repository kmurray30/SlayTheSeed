"""Offline tests for :mod:`realgame_eval`.

Two tiers:

* **Aggregation/report** on synthetic :class:`~realgame_eval.RunOutcome`
  rows -- no network, no engine.
* **Session loop**, against a fake single-connection TCP server (same
  pattern as :mod:`test_realgame_bridge`'s ``FakeServer``) -- proves
  :func:`realgame_eval.play_one_session` genuinely drives one bridge
  session to ``run_complete`` and returns a correctly-populated
  :class:`~realgame_eval.RunOutcome`, without touching the live game.
"""

from __future__ import annotations

import json
import random
import socket
import threading
import time
from collections.abc import Iterator
from pathlib import Path

import pytest
from realgame_bridge import RealGameBridgeClient
from realgame_eval import (
    RunOutcome,
    _world_err_cell,
    aggregate,
    build_report,
    main,
    play_one_session,
    render_markdown,
    write_report,
)

# ---------------------------------------------------------------------------
# Fake server (mirrors tests/test_realgame_bridge.py::FakeServer).
# ---------------------------------------------------------------------------


class FakeServer:
    """A single-connection TCP server under manual, test-driven control."""

    def __init__(self) -> None:
        self._listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._listener.bind(("127.0.0.1", 0))
        self._listener.listen(1)
        self.port = self._listener.getsockname()[1]
        self.conn: socket.socket | None = None
        self._accept_thread = threading.Thread(target=self._accept, daemon=True)
        self._accept_thread.start()

    def _accept(self) -> None:
        conn, _ = self._listener.accept()
        self.conn = conn

    def wait_for_connection(self, timeout: float = 5.0) -> socket.socket:
        deadline = time.monotonic() + timeout
        while self.conn is None:
            if time.monotonic() > deadline:
                raise TimeoutError("client never connected")
            time.sleep(0.01)
        return self.conn

    def send_json_line(self, obj: dict) -> None:
        assert self.conn is not None
        self.conn.sendall(json.dumps(obj).encode("utf-8") + b"\n")

    def recv_line(self, timeout: float = 5.0) -> dict:
        assert self.conn is not None
        self.conn.settimeout(timeout)
        buf = b""
        while b"\n" not in buf:
            chunk = self.conn.recv(8192)
            if not chunk:
                raise ConnectionError("client closed while server was reading")
            buf += chunk
        line, _, _ = buf.partition(b"\n")
        return json.loads(line.decode("utf-8"))

    def close(self) -> None:
        if self.conn is not None:
            self.conn.close()
        self._listener.close()


@pytest.fixture
def fake_server() -> Iterator[FakeServer]:
    server = FakeServer()
    yield server
    server.close()


# ---------------------------------------------------------------------------
# Tier 1: aggregation + report, purely synthetic.
# ---------------------------------------------------------------------------


def _outcome(**overrides) -> RunOutcome:
    base = dict(
        run_index=0,
        policy="random",
        seed=1,
        character="IRONCLAD",
        ascension=0,
        result="victory",
        won=True,
        floor=32,
        act=3,
        decisions=120,
        fallback_count=0,
        duration_s=12.5,
        error=None,
    )
    base.update(overrides)
    return RunOutcome(**base)


class TestAggregate:
    def test_win_rate_and_means(self) -> None:
        rows = [
            _outcome(run_index=0, result="victory", won=True, floor=50, decisions=100),
            _outcome(run_index=1, result="defeat", won=False, floor=20, decisions=60),
        ]
        agg = aggregate(rows)["random"]
        assert agg.runs == 2
        assert agg.wins == 1
        assert agg.win_rate == pytest.approx(0.5)
        assert agg.mean_floor == pytest.approx(35.0)
        assert agg.max_floor == 50
        assert agg.mean_decisions == pytest.approx(80.0)

    def test_multiple_policies_split_correctly(self) -> None:
        rows = [
            _outcome(policy="ppo", won=True),
            _outcome(policy="mcts", won=False),
        ]
        aggs = aggregate(rows)
        assert set(aggs) == {"ppo", "mcts"}
        assert aggs["ppo"].win_rate == pytest.approx(1.0)
        assert aggs["mcts"].win_rate == pytest.approx(0.0)

    def test_requested_policy_with_zero_outcomes_still_present(self) -> None:
        aggs = aggregate([], policies=["ppo", "mcts", "random"])
        assert set(aggs) == {"ppo", "mcts", "random"}
        for agg in aggs.values():
            assert agg.runs == 0
            assert agg.win_rate == pytest.approx(0.0)
            assert agg.mean_floor == pytest.approx(0.0)

    def test_world_errors_roll_up_and_do_not_look_like_run_errors(self) -> None:
        # A swallowed C# throw leaves `error=None` and `result="defeat"` — the
        # run reads as an ordinary loss. `world_error_runs` is the only thing
        # separating "the policy lost" from "the world broke".
        rows = [
            _outcome(result="defeat", won=False, world_errors={"count": 3, "sample": ["boom"]}),
            _outcome(run_index=1, result="defeat", won=False, world_errors={"count": 5}),
            _outcome(run_index=2, result="victory", won=True),
        ]
        agg = aggregate(rows)["random"]
        assert agg.world_error_runs == 2
        assert agg.world_errors == 8
        assert agg.errors == 0  # not conflated with harness-level exceptions

    def test_an_uninstrumented_arm_reports_none_not_zero(self) -> None:
        """`0/0` from a counter that was never wired up is the failure mode.

        `play_one_session` drives the live game over TCP and never goes through
        `sts2_client.Game`, so it has no stderr to classify. Reporting that as a
        clean world is exactly the silently-clean instrument this whole feature
        exists to stop trusting — so the aggregate says `None`, and the table
        renders `n/a`.
        """
        agg = aggregate([_outcome(), _outcome(run_index=1)])["random"]
        assert agg.world_error_runs is None
        assert agg.world_errors is None
        assert _world_err_cell(agg.to_dict()) == "n/a"

    def test_an_instrumented_arm_that_saw_nothing_reports_zero(self) -> None:
        """The other half: clean is NOT the same as uncounted.

        A headless run records `{"count": 0}` even when nothing threw, so a
        genuinely clean sweep still renders a number.
        """
        agg = aggregate([_outcome(world_errors={"count": 0, "sample": []})])["random"]
        assert agg.world_error_runs == 0
        assert agg.world_errors == 0
        assert _world_err_cell(agg.to_dict()) == "0/0"

    def test_errored_run_counted_but_not_crashing(self) -> None:
        rows = [_outcome(result="error: timed out", won=False, floor=None, error="timed out")]
        agg = aggregate(rows)["random"]
        assert agg.errors == 1
        assert agg.mean_floor == pytest.approx(0.0)  # no non-None floors to average


class TestBuildReportAndMarkdown:
    def test_report_shape(self) -> None:
        rows = [_outcome()]
        report = build_report(rows, meta={"generated_at": "now"}, policies=["random"])
        assert report["meta"] == {"generated_at": "now"}
        assert len(report["runs"]) == 1
        assert report["runs"][0]["policy"] == "random"
        assert "random" in report["aggregate"]

    def test_markdown_renders_comparison_and_detail_tables(self) -> None:
        rows = [_outcome(policy="ppo"), _outcome(policy="mcts", won=False, result="defeat")]
        report = build_report(rows, meta={"generated_at": "now"}, policies=["ppo", "mcts"])
        md = render_markdown(report)
        assert "## comparison" in md
        assert "| ppo |" in md
        assert "| mcts |" in md
        assert "## per-run detail" in md

    def test_markdown_empty_runs_note(self) -> None:
        report = build_report([], meta={"generated_at": "now"}, policies=["random"])
        md = render_markdown(report)
        assert "no runs" in md

    def test_write_report_creates_json_and_md(self, tmp_path: Path) -> None:
        report = build_report([_outcome()], meta={"generated_at": "now"}, policies=["random"])
        json_path, md_path = write_report(report, tmp_path / "20260101T000000Z")
        assert json_path.exists()
        assert md_path.exists()
        loaded = json.loads(json_path.read_text(encoding="utf-8"))
        assert loaded["runs"][0]["policy"] == "random"
        assert "## comparison" in md_path.read_text(encoding="utf-8")


class TestDryRunSelfCheck:
    def test_runs_zero_dry_run_writes_zero_row_report(self, tmp_path: Path) -> None:
        rc = main(
            [
                "--policy",
                "random",
                "mcts",
                "ppo",
                "--runs",
                "0",
                "--dry-run",
                "--out-dir",
                str(tmp_path),
            ]
        )
        assert rc == 0
        subdirs = list(tmp_path.iterdir())
        assert len(subdirs) == 1
        report = json.loads((subdirs[0] / "report.json").read_text(encoding="utf-8"))
        assert report["runs"] == []
        assert set(report["aggregate"]) == {"random", "mcts", "ppo"}
        for agg in report["aggregate"].values():
            assert agg["runs"] == 0


# ---------------------------------------------------------------------------
# Tier 2: play_one_session against a fake bridge -- proves the
# connect -> decisions -> run_complete -> disconnect loop for real, offline.
# ---------------------------------------------------------------------------


def _run_fake_random_session(fake_server: FakeServer, results: list) -> None:
    """Background thread: drive one client through a minimal scripted
    combat_action -> map_select -> game_over -> run_complete session."""
    rng = random.Random(0)
    outcome = play_one_session(
        "127.0.0.1", fake_server.port, "random", rng, run_index=7, character="IRONCLAD", seed=3
    )
    results.append(outcome)


class TestPlayOneSessionRandomPolicy:
    def test_full_session_to_run_complete(self, fake_server: FakeServer) -> None:
        results: list[RunOutcome] = []
        t = threading.Thread(
            target=_run_fake_random_session, args=(fake_server, results), daemon=True
        )
        t.start()
        fake_server.wait_for_connection()

        # One combat decision.
        fake_server.send_json_line(
            {
                "type": "combat_action",
                "request_id": "1",
                "state_id": 1,
                "floor": 3,
                "act": 1,
                "player": {"hp": 50, "max_hp": 75, "block": 0, "energy": 3, "max_energy": 3},
                "hand": [],
                "enemies": [],
                "potions": [],
                "run_state": {},
                "round": 1,
            }
        )
        reply = fake_server.recv_line()
        assert reply["action"] == "end_turn"  # empty hand -> nothing playable

        # One map choice.
        fake_server.send_json_line(
            {
                "type": "map_select",
                "request_id": "2",
                "state_id": 2,
                "floor": 4,
                "act": 1,
                "run_state": {},
                "nodes": [{"index": 0, "type": "Monster", "row": 1, "col": 0}],
            }
        )
        reply = fake_server.recv_line()
        assert reply == {"action": "choose", "index": 0, "request_id": "2", "state_id": 2}

        # Terminal sequence: game_over (fire-and-forget) then run_complete.
        fake_server.send_json_line(
            {"type": "game_over", "result": "victory", "message": "Game over", "floor": 51}
        )
        fake_server.send_json_line({"type": "run_complete", "result": "victory", "floor": 51})

        t.join(timeout=5.0)
        assert not t.is_alive(), "session thread did not finish"
        assert len(results) == 1
        outcome = results[0]

        assert outcome.run_index == 7
        assert outcome.policy == "random"
        assert outcome.character == "IRONCLAD"
        assert outcome.seed == 3
        assert outcome.result == "victory"
        assert outcome.won is True
        assert outcome.floor == 51
        assert outcome.decisions == 4  # combat + map + game_over + run_complete
        assert outcome.fallback_count == 0
        assert outcome.error is None
        assert outcome.duration_s >= 0.0

    def test_connection_refused_reports_error_outcome(self) -> None:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.bind(("127.0.0.1", 0))
        free_port = s.getsockname()[1]
        s.close()

        rng = random.Random(0)
        outcome = play_one_session(
            "127.0.0.1",
            free_port,
            "random",
            rng,
            run_index=0,
            reconnect_attempts=1,
            reconnect_delay=0.01,
        )
        # RealGameBridgeClient(reconnect_attempts default) would normally
        # retry for a while; play_one_session must still surface a clean
        # error outcome rather than raising out of the eval sweep.
        assert outcome.error is not None
        assert outcome.result.startswith("error:")
        assert outcome.won is False


def test_realgamebridgeclient_still_importable_for_reference() -> None:
    """Sanity: the driver's reconnect story depends on RealGameBridgeClient
    being a fresh connection per session (see module docstring)."""
    assert RealGameBridgeClient is not None


# ---------------------------------------------------------------------------
# Tier 3: rejection semantics — a bridge `error` means the world NEVER took
# the action (PROTOCOL.md 3a), so the mirror must not advance past it and the
# re-search must not re-send it.
# ---------------------------------------------------------------------------


class FakeMirror:
    """Recording stand-in for combat_mirror.CombatMirror."""

    def __init__(self, *args, **kwargs) -> None:
        self.alive = True
        self.tainted = False
        self.advanced: list = []
        self.dropped: list[str] = []

    def advance(self, engine_action) -> None:
        self.advanced.append(("engine", engine_action))

    def advance_through_choice(self, picks) -> None:
        self.advanced.append(("picks", picks))

    def drop(self, reason: str) -> None:
        self.alive = False
        self.dropped.append(reason)

    class _Stats:
        @staticmethod
        def as_dict() -> dict:
            return {}

    stats = _Stats()


def _combat_state(state_id: int, request_id: str) -> dict:
    return {
        "type": "combat_action",
        "request_id": request_id,
        "state_id": state_id,
        "floor": 3,
        "act": 1,
        "player": {"hp": 50, "max_hp": 75, "block": 0, "energy": 3, "max_energy": 3},
        "hand": [{"id": "STRIKE_IRONCLAD", "playable": True, "target_type": "AnyEnemy"}],
        "enemies": [{"id": "NIBBIT", "is_alive": True}],
        "potions": [],
        "run_state": {"character": "IRONCLAD"},
        "round": 1,
    }


class TestRejectedActionSemantics:
    def test_error_discards_mirror_advance_and_bans_the_command(
        self, fake_server: FakeServer, monkeypatch
    ) -> None:
        """A rejected play must not advance the mirror (the world never took
        it — advancing anyway was a guaranteed desync cascade, 13 times across
        the 20260802T184818Z live sweep), and the re-sent decision must see the
        rejected command in ``banned`` so the deterministic search cannot loop
        on it."""
        import realgame_eval as re_mod
        from sts2_hint.recommender import RecommendConfig

        monkeypatch.setattr(re_mod, "CombatMirror", FakeMirror)

        captured_banned: list = []
        scripted = [
            (
                {"action": "play", "card_index": 0, "target_index": 0},
                None,
                {"action": "play", "card_index": 0, "target_index": 0},
            ),
            ({"action": "end_turn"}, None, {"action": "end_turn"}),
        ]

        def fake_pick(state, cfg, rng, *, sims=None, mirror=None, banned=None):
            captured_banned.append(set(banned) if banned else None)
            return scripted.pop(0)

        monkeypatch.setattr(re_mod, "pick_combat_action_mcts", fake_pick)

        results: list[RunOutcome] = []

        def _session() -> None:
            outcome = play_one_session(
                "127.0.0.1",
                fake_server.port,
                "mcts",
                random.Random(0),
                run_index=0,
                character="IRONCLAD",
                seed=1,
                mcts_cfg=RecommendConfig(),
            )
            results.append(outcome)

        t = threading.Thread(target=_session, daemon=True)
        t.start()
        fake_server.wait_for_connection()

        # Decision 1: the driver plays; the bridge REJECTS it.
        fake_server.send_json_line(_combat_state(1, "1"))
        reply = fake_server.recv_line()
        assert reply["action"] == "play"
        fake_server.send_json_line(
            {"type": "error", "error": "invalid_index", "detail": "card_index 0 out of range"}
        )
        # Re-send of the SAME decision content (fresh ids, per PROTOCOL.md 3a).
        fake_server.send_json_line(_combat_state(2, "2"))
        reply = fake_server.recv_line()
        assert reply["action"] == "end_turn"
        fake_server.send_json_line({"type": "run_complete", "result": "defeat", "floor": 3})

        t.join(timeout=5.0)
        assert not t.is_alive(), "session thread did not finish"
        outcome = results[0]
        assert outcome.result == "defeat"

        # First pick saw no bans; the re-sent decision saw the rejected command.
        assert captured_banned[0] is None
        assert captured_banned[1] == {
            json.dumps({"action": "play", "card_index": 0, "target_index": 0}, sort_keys=True)
        }
        # Mirror-advance accounting is asserted directly in
        # test_mirror_advance_exact_accounting below.
        assert outcome.mirror_stats is not None

    def test_mirror_advance_exact_accounting(self, fake_server: FakeServer, monkeypatch) -> None:
        """Companion to the ban test with direct hold of the mirror instance."""
        import realgame_eval as re_mod
        from sts2_hint.recommender import RecommendConfig

        instances: list[FakeMirror] = []

        class RecordingMirror(FakeMirror):
            def __init__(self, *args, **kwargs) -> None:
                super().__init__(*args, **kwargs)
                instances.append(self)

        monkeypatch.setattr(re_mod, "CombatMirror", RecordingMirror)
        scripted = [
            (
                {"action": "play", "card_index": 0, "target_index": 0},
                None,
                {"action": "play", "card_index": 0, "target_index": 0},
            ),
            ({"action": "end_turn"}, None, {"action": "end_turn"}),
        ]
        monkeypatch.setattr(
            re_mod,
            "pick_combat_action_mcts",
            lambda state, cfg, rng, *, sims=None, mirror=None, banned=None: scripted.pop(0),
        )

        results: list[RunOutcome] = []

        def _session() -> None:
            results.append(
                play_one_session(
                    "127.0.0.1",
                    fake_server.port,
                    "mcts",
                    random.Random(0),
                    run_index=0,
                    mcts_cfg=RecommendConfig(),
                )
            )

        t = threading.Thread(target=_session, daemon=True)
        t.start()
        fake_server.wait_for_connection()

        fake_server.send_json_line(_combat_state(1, "1"))
        fake_server.recv_line()  # the play
        fake_server.send_json_line({"type": "error", "error": "invalid_index", "detail": "x"})
        fake_server.send_json_line(_combat_state(2, "2"))
        fake_server.recv_line()  # the end_turn
        fake_server.send_json_line({"type": "run_complete", "result": "defeat", "floor": 3})
        t.join(timeout=5.0)
        assert not t.is_alive()

        (mirror,) = instances
        # ONLY the accepted end_turn advanced; the rejected play was discarded.
        assert mirror.advanced == [("engine", {"action": "end_turn"})]

    def test_pick_crash_records_error_outcome_instead_of_raising(
        self, fake_server: FakeServer, monkeypatch
    ) -> None:
        """headless parity: a pick-function crash (e.g. a STRICT
        MirrorDivergence) must yield an error RunOutcome, not abort the sweep
        with no report."""
        import realgame_eval as re_mod
        from sts2_hint.recommender import RecommendConfig

        monkeypatch.setattr(re_mod, "CombatMirror", FakeMirror)

        def boom(state, cfg, rng, *, sims=None, mirror=None, banned=None):
            raise RuntimeError("mirror diverged (STRICT)")

        monkeypatch.setattr(re_mod, "pick_combat_action_mcts", boom)

        results: list[RunOutcome] = []

        def _session() -> None:
            results.append(
                play_one_session(
                    "127.0.0.1",
                    fake_server.port,
                    "mcts",
                    random.Random(0),
                    run_index=0,
                    mcts_cfg=RecommendConfig(),
                )
            )

        t = threading.Thread(target=_session, daemon=True)
        t.start()
        fake_server.wait_for_connection()
        fake_server.send_json_line(_combat_state(1, "1"))
        t.join(timeout=5.0)
        assert not t.is_alive(), "session thread did not finish"
        outcome = results[0]
        assert outcome.error is not None
        assert outcome.result.startswith("error:")
        assert "mirror diverged" in outcome.result


class TestBannedWalk:
    def test_pick_combat_action_mcts_skips_banned_action(self, monkeypatch) -> None:
        """The ranking walk must skip a command the game already rejected for
        this state and fall through to the next-best action."""
        import realgame_mcts as rm
        from sts2_hint.recommender import HintTag, RecommendConfig

        state = {
            "type": "combat_action",
            "hand": [
                {"id": "STRIKE_IRONCLAD", "playable": True},
                {"id": "DEFEND_IRONCLAD", "playable": True},
            ],
            "enemies": [],
            "potions": [],
            "run_state": {"character": "IRONCLAD"},
            "round": 1,
        }
        tags = [
            HintTag(stable_idx=0, win_prob=0.9, bucket_label="likely-win", is_best=True),
            HintTag(stable_idx=1, win_prob=0.5, bucket_label="even", is_best=False),
        ]
        monkeypatch.setattr(rm, "to_headless_combat_play", lambda s: s)
        monkeypatch.setattr(rm, "recommend_all", lambda headless, cfg: list(tags))

        banned = {json.dumps({"action": "play", "card_index": 0}, sort_keys=True)}
        action, log_extra, _engine_action = rm.pick_combat_action_mcts(
            state, RecommendConfig(), random.Random(0), banned=banned
        )
        assert log_extra is None
        assert action["action"] == "play"
        assert action["card_index"] == 1  # best action was banned -> next-best

        # Without the ban the best action is chosen.
        action, _, _ = rm.pick_combat_action_mcts(state, RecommendConfig(), random.Random(0))
        assert action["card_index"] == 0


def test_live_meta_records_search_profile_and_mirror_policy(tmp_path) -> None:
    """The clairvoyant confound went unnoticed partly because no live report
    named its search profile. The meta must now carry it (plus the mirror
    policy env), exactly like headless_eval's report."""
    rc = main(["--policy", "mcts", "--runs", "0", "--dry-run", "--out-dir", str(tmp_path)])
    assert rc == 0
    (subdir,) = tmp_path.iterdir()
    meta = json.loads((subdir / "report.json").read_text(encoding="utf-8"))["meta"]
    assert meta["combat_search"] == {
        "hidden_info": "beliefdraw",
        "c_puct": 2.5,
        "draw_widening_c": 2.0,
    }
    assert meta["mirror_policy"] in ("STRICT", "OBSERVE")


def test_the_default_search_profile_is_the_deployed_honest_one(monkeypatch, tmp_path):
    """Until 2026-08-02 this driver silently built its RecommendConfig from the
    dataclass defaults (clairvoyant, c_puct=1.4) while headless_eval and the
    engine arm searched beliefdraw/2.5/2.0 — no live sweep was comparable to
    either. Pin the trio, and the --clairvoyant escape hatch."""
    import realgame_eval as re_mod

    captured: list = []

    def fake_session(host, port, policy, rng, **kw):
        captured.append(kw["mcts_cfg"])
        return RunOutcome(
            run_index=0,
            policy=policy,
            seed=None,
            character=None,
            ascension=None,
            result="run_complete",
            won=False,
            floor=1,
            act=1,
            decisions=1,
            fallback_count=0,
            duration_s=0.1,
        )

    monkeypatch.setattr(re_mod, "play_one_session", fake_session)
    main(["--policy", "mcts", "--runs", "1", "--out-dir", str(tmp_path)])
    cfg = captured.pop()
    assert (cfg.hidden_info, cfg.c_puct, cfg.draw_widening_c) == ("beliefdraw", 2.5, 2.0)

    main(["--policy", "mcts", "--runs", "1", "--clairvoyant", "--out-dir", str(tmp_path)])
    cfg = captured.pop()
    assert (cfg.hidden_info, cfg.c_puct, cfg.draw_widening_c) == ("clairvoyant", 1.4, None)
