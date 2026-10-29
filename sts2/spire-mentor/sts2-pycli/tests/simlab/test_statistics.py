"""Statistical sanity: catches inverted scoring / broken agents.

Uses small samples — assertions are on ordering with generous margins,
not exact values.
"""

import statistics

import pytest
from simlab.runner import play_combat
from simlab.specs import ScenarioSpec


@pytest.mark.slow
def test_min_overblock_beats_random() -> None:
    spec = ScenarioSpec(encounter="MAWLER_NORMAL")
    rand = [play_combat(spec, "random", s)["dmg_taken"] for s in range(40)]
    mob = [play_combat(spec, "min_overblock", s)["dmg_taken"] for s in range(40)]
    assert statistics.fmean(mob) < statistics.fmean(rand)
