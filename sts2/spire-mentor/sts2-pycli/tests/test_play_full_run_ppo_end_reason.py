"""The engine-world sweep's non-terminal accounting.

Every win count in the v7 -> v20 series was quoted against a denominator that
silently included runs which never reached a terminal state. On the rung-3.5
side that was 11 SLOTH aborts and 2 Kaiser Crab wedges; on THIS side it is a
``None`` from the agent/stepper, which breaks the loop mid-run and leaves
``player_won`` False — a defeat, as far as the report can tell.

``_end_reason`` and ``_result_tag`` are what keep those apart. Both are pure,
so they are tested directly rather than through a 100k-step run.
"""

from __future__ import annotations

from play_full_run_ppo import MAX_STEPS, _end_reason, _result_tag


class TestEndReason:
    def test_a_finished_run_is_terminal(self) -> None:
        assert _end_reason(stalled=False, is_over=True, steps=400) == "terminal"

    def test_a_none_from_the_agent_is_not_a_defeat(self) -> None:
        # The whole point: this run has player_won=False and is NOT a loss.
        assert _end_reason(stalled=True, is_over=False, steps=400) == "agent_stalled"

    def test_the_step_cap_is_its_own_reason(self) -> None:
        assert _end_reason(stalled=False, is_over=False, steps=MAX_STEPS) == "max_steps"

    def test_terminal_wins_the_tie_against_the_step_cap(self) -> None:
        """A run that ends on exactly step MAX_STEPS COMPLETED.

        Tagging it `max_steps` would drop it from the win-rate denominator while
        `victory` stayed True — it would print WIN and appear in the
        non-terminal list at the same time.
        """
        assert _end_reason(stalled=False, is_over=True, steps=MAX_STEPS) == "terminal"

    def test_a_stall_outranks_everything(self) -> None:
        # `stalled` means the loop broke early; nothing downstream is trustworthy.
        assert _end_reason(stalled=True, is_over=True, steps=MAX_STEPS) == "agent_stalled"

    def test_falling_out_for_no_known_reason_is_not_silently_terminal(self) -> None:
        assert _end_reason(stalled=False, is_over=False, steps=7) == "unknown"


class TestResultTag:
    def test_win_loss_and_error(self) -> None:
        assert _result_tag({"victory": True, "completed": True}) == "WIN "
        assert _result_tag({"victory": False, "completed": True}) == "LOSS"
        assert _result_tag({"victory": False, "completed": False, "error": "boom"}) == "ERR "

    def test_a_non_terminal_run_is_stop_not_loss(self) -> None:
        assert _result_tag({"victory": False, "completed": False}) == "STOP"

    def test_a_result_without_the_key_is_assumed_complete(self) -> None:
        """Back-compat with older result dicts, which carried no `completed`.

        Defaulting to True keeps them reading as WIN/LOSS rather than turning a
        whole historical corpus into STOP.
        """
        assert _result_tag({"victory": False}) == "LOSS"
        assert _result_tag({"victory": True}) == "WIN "
