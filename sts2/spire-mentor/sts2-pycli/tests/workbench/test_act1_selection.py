"""The workbench must be able to walk EITHER Act 1, and say which (GH #544).

STS2 ships two acts at index 0 and the real lobby rolls between them off the
``act_selection`` stream — a uniform 50/50 once Underdocks is unlocked and
discovered (``ActModel.GetRandomList``, ``ActModel.cs:558``). The workbench
always built Overgrowth, so for half of all seeds it showed a different act
than the player's game, which is what made act-1 bug reports irreproducible.

Seed ``C9EXV1KTPH`` is the one from the #544 report: the real game showed
Lagavulin Matriarch, the sim said Vantom. It rolls Underdocks, so it doubles as
the regression fixture for the whole axis.
"""

from __future__ import annotations

import pytest
from workbench.backends.rust_game import RustGame, _act_name

REPORTED_SEED = "C9EXV1KTPH"


def _start(act1: str) -> tuple[RustGame, dict]:
    g = RustGame()
    state = g.start(character="Ironclad", seed=REPORTED_SEED, ascension=10, act1=act1)
    return g, state


@pytest.mark.parametrize(
    ("act1", "expected"),
    [("overgrowth", "overgrowth"), ("underdocks", "underdocks")],
)
def test_a_fixed_choice_walks_that_act(act1: str, expected: str) -> None:
    _g, state = _start(act1)
    assert state["act1"] == expected


def test_roll_reproduces_the_real_games_act_for_the_reported_seed() -> None:
    """The seed that opened #544 rolls Underdocks, as the player's game did."""
    _, state = _start("roll")
    assert state["act1"] == "underdocks"


def test_the_state_reports_the_resolved_act_not_the_request() -> None:
    """`"roll"` must never reach the caller: it names a policy, not an act, and
    a bug report saying "act 1" has to say WHICH act 1."""
    _, state = _start("roll")
    assert state["act1"] in {"overgrowth", "underdocks"}


def test_the_map_boss_matches_the_act_that_was_walked() -> None:
    """The act-1 boss PIN was Overgrowth-only in four places, so an Underdocks
    run displayed `Vantom` while fighting Lagavulin Matriarch — the id came
    from `generated_rooms` (right) and the name from a stale pin (wrong).
    Assert on the NAME: the id was already correct while the bug was live."""
    g, _ = _start("underdocks")
    boss = g.get_map()["boss"]
    assert boss["id"] == "LAGAVULIN_MATRIARCH_BOSS"
    assert boss["name"] == "Lagavulin Matriarch"
    assert boss["kind"] == "LagavulinMatriarch"
    # And the host-facing "what am I about to fight?" accessor agrees.
    assert g.rm.pinned_boss_encounter_id() == "LAGAVULIN_MATRIARCH_BOSS"


def test_overgrowth_still_reports_its_own_boss() -> None:
    """The control: the fix must not make every act report Underdocks."""
    g, _ = _start("overgrowth")
    boss = g.get_map()["boss"]
    assert boss["id"] == "VANTOM_BOSS"
    assert boss["name"] == "Vantom"
    assert g.rm.pinned_boss_encounter_id() == "VANTOM_BOSS"


# ── act_name: the field every painted region is keyed off ─────────────────────
#
# The frontend picks the combat backdrop, the card-reward scene, the rest-site
# clearing and the map parchment from `act_name`, each falling back to
# overgrowth when it is absent. The Rust backend emitted no `act_name` at all,
# so the fallback was the only branch ever taken: acts 2 and 3 were painted in
# act 1's jungle ruin and an Underdocks run showed Overgrowth art.


@pytest.mark.parametrize(
    ("act1", "expected"),
    [("overgrowth", "Overgrowth"), ("underdocks", "Underdocks")],
)
def test_the_state_names_which_act_1_it_is_walking(act1: str, expected: str) -> None:
    _, state = _start(act1)
    assert state["act_name"] == expected


def test_act_name_agrees_with_act1_under_a_roll() -> None:
    _, state = _start("roll")
    assert state["act_name"] == state["act1"].capitalize()


@pytest.mark.parametrize(
    ("act_index", "act1", "expected"),
    [
        (1, "overgrowth", "Overgrowth"),
        (1, "underdocks", "Underdocks"),
        # Acts 2 and 3 are alone at their index, so act1 cannot move them.
        (2, "overgrowth", "Hive"),
        (2, "underdocks", "Hive"),
        (3, "overgrowth", "Glory"),
        (3, "underdocks", "Glory"),
    ],
)
def test_every_act_has_its_own_name(act_index: int, act1: str, expected: str) -> None:
    """Reaching act 2/3 through a live run costs a full playthrough, so pin the
    mapping directly — it is what feeds the act-2/3 backdrops."""
    assert _act_name(act_index, act1) == expected


def test_an_unknown_act_is_unnamed_rather_than_guessed() -> None:
    """`None` leaves the frontend on its "Act N" label and its act-1 art; a
    guessed name would paint a confidently wrong room."""
    assert _act_name(4, "overgrowth") is None
    assert _act_name(0, "overgrowth") is None
