"""Gates for the Act-1 axis on arena reports (GH #544).

Act 1 has two models since #553: Overgrowth (the historical one) and
Underdocks. `run_arena --act1` selects one, or rolls it from the seed the way
the real game does. Every row now records the act it ACTUALLY walked, which is
what makes a `roll` report splittable at analysis time.

The gate that matters is on `compare`: a paired win-rate delta is only about
the act if the two arms disagree about the act on EVERY pair. Compare a fixed
arm against a rolled one and you get a delta that blends "act B is harder"
with "these particular seeds are harder" — and n does not fix it.
"""

from __future__ import annotations

import run_arena


def _pairs(a_acts: list[str], b_acts: list[str]) -> list[tuple[dict, dict]]:
    return [({"act1": x}, {"act1": y}) for x, y in zip(a_acts, b_acts)]


def test_two_fixed_arms_on_different_acts_are_a_clean_split() -> None:
    comp = run_arena._act1_composition(_pairs(["overgrowth"] * 4, ["underdocks"] * 4))
    assert comp["kind"] == "split"
    assert comp["pairs_on_the_same_act"] == 0
    assert comp["a"] == {"overgrowth": 4}
    assert comp["b"] == {"underdocks": 4}


def test_two_arms_on_the_same_act_control_for_it() -> None:
    comp = run_arena._act1_composition(_pairs(["underdocks"] * 3, ["underdocks"] * 3))
    assert comp["kind"] == "same"
    assert comp["pairs_on_the_same_act"] == 3


def test_a_fixed_arm_against_a_rolled_arm_is_flagged_mixed() -> None:
    """The conflated comparison this guard exists to catch."""
    comp = run_arena._act1_composition(
        _pairs(["overgrowth"] * 4, ["overgrowth", "underdocks", "underdocks", "underdocks"])
    )
    assert comp["kind"] == "mixed"
    assert comp["pairs_on_the_same_act"] == 1


def test_reports_written_before_the_act_axis_existed_read_as_overgrowth() -> None:
    """Pre-#544 rows carry no `act1` key; they were all Overgrowth. Without
    the default they would classify as `mixed` against a fresh Overgrowth
    arm and print a warning about a difference that does not exist."""
    comp = run_arena._act1_composition([({}, {"act1": "overgrowth"})])
    assert comp["kind"] == "same"
    assert comp["a"] == {"overgrowth": 1}
