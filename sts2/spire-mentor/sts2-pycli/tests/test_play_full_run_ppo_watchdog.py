"""The engine-world driver's no-progress watchdog (GH #271).

A livelock used to be caught only by ``MAX_STEPS = 100_000``: seed 1055 burned
~54 minutes at floor 27 while its 59 sibling seeds finished in 8-115s, and the
artifact was a single "it stopped" line. The watchdog exists to make that fail
in seconds with a diagnosable dump.

The detector is pure, so it is tested directly rather than by wedging a real
run. The one test that does touch the engine only checks that
``run_fingerprint`` moves when the run moves — the property everything else
rests on.
"""

from __future__ import annotations

import json
from pathlib import Path

from play_full_run_ppo import (
    MAX_STEPS,
    NO_PROGRESS_STEPS,
    NoProgressWatchdog,
    _dump_wedge,
    _end_reason,
    _result_tag,
    default_wedge_dir,
    run_fingerprint,
)

END_TURN = {"action": "end_turn"}


def _drive(wd: NoProgressWatchdog, states: list[str], *, action: dict | None = None) -> int | None:
    """Feed fingerprints in order; return the 1-based step that tripped it."""
    for i, s in enumerate(states, start=1):
        if wd.observe((s,), step=i, action=action if action is not None else END_TURN):
            return i
    return None


class TestEndReason:
    def test_a_wedge_is_its_own_reason(self) -> None:
        assert _end_reason(stalled=False, is_over=False, steps=900, no_progress=True) == (
            "no_progress"
        )

    def test_a_wedged_run_is_stop_not_loss(self) -> None:
        """The whole denominator discipline: a wedge is a MISSING measurement.

        Folding it into the losses is what turned every v7->v20 win count into
        a floor.
        """
        assert _result_tag({"victory": False, "completed": False}) == "STOP"

    def test_the_guard_outranks_a_finished_run(self) -> None:
        """The loop consults the watchdog only while `is_over()` is False, so a
        True here means the break really happened — same reasoning as `stalled`,
        and the opposite of the step cap's tie-break.
        """
        assert _end_reason(stalled=False, is_over=True, steps=900, no_progress=True) == (
            "no_progress"
        )

    def test_an_agent_stall_still_outranks_a_wedge(self) -> None:
        assert _end_reason(stalled=True, is_over=False, steps=900, no_progress=True) == (
            "agent_stalled"
        )

    def test_the_default_keeps_every_existing_caller_intact(self) -> None:
        # `no_progress` is defaulted so older call sites and result dicts read
        # exactly as they did before.
        assert _end_reason(stalled=False, is_over=True, steps=400) == "terminal"
        assert _end_reason(stalled=False, is_over=False, steps=MAX_STEPS) == "max_steps"


class TestWatchdog:
    def test_a_healthy_run_never_trips(self) -> None:
        wd = NoProgressWatchdog(window=10)
        assert _drive(wd, [f"s{i}" for i in range(500)]) is None
        assert wd.stationary_steps == 0

    def test_a_frozen_state_trips_exactly_at_the_window(self) -> None:
        wd = NoProgressWatchdog(window=10)
        # Step 1 is novel and resets the counter, so the 10th *repeat* is
        # step 11.
        assert _drive(wd, ["stuck"] * 50) == 11

    def test_a_two_cycle_trips(self) -> None:
        """The reason novelty is tracked against every state seen, not just the
        previous one.

        `run_stepper`'s `_last_combat_fp` compares consecutive steps, so
        A->B->A->B resets it every single time and it never fires.
        """
        wd = NoProgressWatchdog(window=10)
        tripped = _drive(wd, ["a", "b"] * 50)
        assert tripped is not None
        assert tripped == 12  # a, b novel; 10 stale steps follow

    def test_a_long_cycle_trips_too(self) -> None:
        wd = NoProgressWatchdog(window=20)
        assert _drive(wd, [f"s{i % 7}" for i in range(200)]) is not None

    def test_progress_inside_a_cycle_resets_it(self) -> None:
        """A run revisiting states but still occasionally reaching new ones is
        making progress — a fight that loops through similar states while the
        enemy's HP ticks down must not be called a wedge.
        """
        wd = NoProgressWatchdog(window=10)
        states = []
        for i in range(30):
            states += ["a", "b", "c", "d", "e", "f", "g", "h", f"new{i}"]
        assert _drive(wd, states) is None

    def test_window_zero_disables_it(self) -> None:
        wd = NoProgressWatchdog(window=0)
        assert not wd.enabled
        assert _drive(wd, ["stuck"] * 10_000) is None

    def test_the_default_window_is_far_outside_a_real_run(self) -> None:
        # A full Ironclad run terminates in ~450 steps; the window must not be
        # reachable by ordinary play, and must be well under the step cap.
        assert 100 <= NO_PROGRESS_STEPS < MAX_STEPS


class TestMargin:
    """``peak_stationary_steps`` is how the window's headroom stays visible."""

    def test_it_records_the_worst_streak_not_the_last(self) -> None:
        wd = NoProgressWatchdog(window=100)
        _drive(wd, ["a"] * 8 + ["b", "c", "d"])
        assert wd.stationary_steps == 0  # d was novel
        assert wd.peak_stationary_steps == 7  # 8 x "a" = 1 novel + 7 stale

    def test_a_run_that_never_repeats_has_no_streak(self) -> None:
        wd = NoProgressWatchdog(window=100)
        _drive(wd, [f"s{i}" for i in range(50)])
        assert wd.peak_stationary_steps == 0

    def test_it_survives_the_counter_resetting(self) -> None:
        wd = NoProgressWatchdog(window=100)
        _drive(wd, ["a"] * 20 + ["b"] * 3 + ["c"])
        assert wd.peak_stationary_steps == 19


class _FakeRm:
    """Just the surface `NoProgressWatchdog.report` reads."""

    act_index = 2
    current_hp = 31
    max_hp = 72
    gold = 145

    def phase(self) -> str:
        return "combat"

    def total_floor(self) -> int:
        return 27

    def current_room(self) -> dict:
        return {"room_type": "monster"}


class TestReport:
    def _wedged(self) -> NoProgressWatchdog:
        wd = NoProgressWatchdog(window=5)
        play = {"action": "play_card", "hand_idx": 2, "target_index": 0}
        for i in range(40):
            wd.observe(("a",) if i % 2 else ("b",), step=i + 1, action=play)
        return wd

    def test_it_names_the_repeating_action(self) -> None:
        """The repeating action IS the bug in every livelock seen so far — an
        action the engine reports as advanced while ignoring it.
        """
        rep = self._wedged().report(_FakeRm(), seed=1055, steps=40)
        top = rep["repeated_actions"][0]
        assert top["action"]["action"] == "play_card"
        assert top["count"] > 1

    def test_it_records_where_the_run_stuck(self) -> None:
        rep = self._wedged().report(_FakeRm(), seed=1055, steps=40)
        assert rep["where"] == {
            "phase": "combat",
            "floor": 27,
            "act": 2,
            "room_type": "monster",
            "hp": 31,
            "max_hp": 72,
            "gold": 145,
        }
        assert rep["seed"] == 1055
        assert rep["end_reason"] == "no_progress"

    def test_it_carries_the_margin(self) -> None:
        rep = self._wedged().report(_FakeRm(), seed=1055, steps=40)
        assert rep["peak_stationary_steps"] >= rep["stationary_steps"]

    def test_the_tail_shows_the_cycle_is_short(self) -> None:
        rep = self._wedged().report(_FakeRm(), seed=1055, steps=40)
        # Two states, alternating — that shape is the diagnosis.
        assert rep["distinct_states_in_tail"] == 2
        assert rep["distinct_states_total"] == 2

    def test_the_dump_is_json_and_self_contained(self, tmp_path: Path) -> None:
        rep = self._wedged().report(_FakeRm(), seed=1055, steps=40)
        path = _dump_wedge(rep, 1055, tmp_path)
        assert path is not None and path.exists()
        loaded = json.loads(path.read_text(encoding="utf-8"))
        # Diagnosable from the file alone, without re-running the sweep.
        assert loaded["where"]["floor"] == 27
        assert loaded["repeated_actions"][0]["action"]["action"] == "play_card"
        assert loaded["tail"]

    def test_a_dump_failure_does_not_kill_the_sweep(self, tmp_path: Path) -> None:
        """59 other seeds' results must not be lost to a disk error."""
        blocker = tmp_path / "blocked"
        blocker.write_text("not a directory", encoding="utf-8")
        assert _dump_wedge({"seed": 1}, 1, blocker / "sub") is None

    def test_the_default_dump_dir_is_under_gitignored_logs(self) -> None:
        d = default_wedge_dir()
        assert d.parent.name == "logs"
        assert d.name == "wedges"


class TestFingerprintAgainstTheEngine:
    def test_it_moves_when_the_run_moves(self) -> None:
        """Everything above assumes a productive step changes the fingerprint.

        Neow is the cheapest real decision point: one action, and the run is
        demonstrably somewhere else afterwards.
        """
        import sts2_rs

        rm = sts2_rs.RunManager(seed=1055, character="Ironclad", neow_start=True)
        before = run_fingerprint(rm)
        agent = sts2_rs.PpoRunAgent(seed=1055, sims=16, boss_sims=16)
        moved = False
        for _ in range(40):
            if rm.is_over() or agent.step(rm) is None:
                break
            if run_fingerprint(rm) != before:
                moved = True
                break
        assert moved, "40 applied actions left the fingerprint unchanged"

    def test_it_is_stable_for_an_unchanged_run(self) -> None:
        import sts2_rs

        rm = sts2_rs.RunManager(seed=1055, character="Ironclad", neow_start=True)
        assert run_fingerprint(rm) == run_fingerprint(rm)
