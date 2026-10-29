"""Smoke test for the parity-fuzz single-trial driver (Task 8).

Finding a divergence is *success* of the harness — don't assert clean
parity. Crashes (uncaught exceptions) are bugs in the driver itself.
"""

import pytest


@pytest.mark.slow
def test_run_single_trial_completes():
    from parity_fuzz import run_trial

    result = run_trial(seed=1, max_turns=3)
    assert result.seed == 1
    assert result.turns_played >= 0
