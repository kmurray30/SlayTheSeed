"""Tests for sweeping SimLab from a pasted board rather than a fresh fight.

Two properties carry the feature, and they pull against each other:

* every trial must start from the SAME visible position (else it is not this
  position's distribution), and
* trials must DIVERGE (else the distribution is a point and the sweep says
  nothing).

The reseed is what reconciles them — the board is copied, the future dice are
not.
"""

from __future__ import annotations

import json

import pytest

pytest.importorskip("sts2_rs", reason="simlab needs the sts2_rs extension")

from simlab.specs import (
    DECK_PRESETS,
    IMPORTED_ENCOUNTER,
    ScenarioSpec,
    SweepSpec,
    build_combat,
    build_combat_from_state,
    start_state_ascension,
)


@pytest.fixture
def board() -> str:
    """An engine wire dict as JSON — what an import hands SimLab."""
    import sts2_rs

    cs = sts2_rs.build_scenario_state(
        seed=11,
        character="Ironclad",
        deck=list(DECK_PRESETS["ironclad_starter"]),
        hp=71,
        max_hp=80,
        relics=["BURNING_BLOOD"],
        encounter="MAWLER_NORMAL",
        ascension=4,
        potions=[],
    )
    return json.dumps(sts2_rs.state_to_dict(cs))


def _visible(cs) -> dict:
    """The part of a board a player can see — everything but the dice."""
    import sts2_rs

    wire = sts2_rs.state_to_dict(cs)
    wire.pop("rng", None)
    return wire


def test_same_seed_is_the_same_combat(board):
    a = build_combat_from_state(board, 3)
    b = build_combat_from_state(board, 3)
    assert _visible(a) == _visible(b)


def test_every_trial_opens_on_the_identical_position(board):
    """Different seeds, same board — otherwise it is not this position."""
    seen = [_visible(build_combat_from_state(board, s)) for s in (1, 2, 3, 99)]
    assert all(v == seen[0] for v in seen[1:])


def test_the_position_matches_what_was_pasted(board):
    import sts2_rs

    original = json.loads(board)
    rebuilt = sts2_rs.state_to_dict(build_combat_from_state(board, 5))
    assert rebuilt["player"]["hp"] == original["player"]["hp"] == 71
    # Draw ORDER, not just contents.
    assert [c["id"] for c in rebuilt["deck"]["draw_pile"]] == [
        c["id"] for c in original["deck"]["draw_pile"]
    ]
    assert [c["id"] for c in rebuilt["deck"]["hand"]] == [c["id"] for c in original["deck"]["hand"]]


def test_trials_diverge_once_the_dice_are_rolled(board):
    """Without the reseed every trial replays one frozen stream, and the
    distribution collapses to a point."""
    import sts2_rs

    def after_a_turn(seed: int) -> dict:
        cs = build_combat_from_state(board, seed)
        cs.apply({"action": "end_turn"})
        return sts2_rs.state_to_dict(cs)

    outcomes = {json.dumps(after_a_turn(s), sort_keys=True) for s in range(1, 12)}
    assert len(outcomes) > 1, "reseeding must actually change the future"


def test_build_combat_routes_a_start_state_scenario(board):
    spec = ScenarioSpec(
        encounter=IMPORTED_ENCOUNTER,
        deck_name="pasted",
        deck_cards=(),
        start_state=board,
        start_label="pasted",
    )
    assert spec.from_state
    assert _visible(build_combat(spec, 2)) == _visible(build_combat_from_state(board, 2))


def test_ascension_is_read_off_the_board_not_the_sweep(board):
    assert start_state_ascension(board) == 4
    sweep = SweepSpec(
        name="counterfactual",
        encounters=(),
        # Deliberately wrong: the board is the authority for its own fight.
        ascensions=(0,),
        strategies=("random",),
        n_seeds=4,
        start_states=(("pasted", board),),
    )
    (cell,) = sweep.cells()
    assert cell.scenario.ascension == 4


def test_start_states_replace_the_encounter_and_deck_axes(board):
    sweep = SweepSpec(
        name="counterfactual",
        encounters=("MAWLER_NORMAL", "FOGMOG_NORMAL"),
        ascensions=(0, 5),
        strategies=("random", "block_first"),
        n_seeds=4,
        start_states=(("as-is", board), ("after Bash", board)),
    )
    cells = sweep.cells()
    # positions x strategies — NOT multiplied by encounters or ascensions,
    # which the board already fixes.
    assert len(cells) == 4
    assert {c.scenario.deck_name for c in cells} == {"as-is", "after Bash"}
    assert {c.scenario.encounter for c in cells} == {IMPORTED_ENCOUNTER}


def test_duplicate_labels_are_refused(board):
    """Two positions under one label would merge into one distribution."""
    with pytest.raises(ValueError, match="duplicate start-state labels"):
        SweepSpec(
            name="counterfactual",
            encounters=(),
            ascensions=(),
            strategies=("random",),
            n_seeds=4,
            start_states=(("same", board), ("same", board)),
        )


def test_round_trips_through_json(board):
    sweep = SweepSpec(
        name="counterfactual",
        encounters=(),
        ascensions=(),
        strategies=("random",),
        n_seeds=4,
        start_states=(("as-is", board),),
    )
    again = SweepSpec.from_json(sweep.to_json())
    assert again.start_states == sweep.start_states
    assert again.cells()[0].scenario.start_state == board


def test_a_trial_actually_runs_from_the_board(board):
    """End to end through the runner: the row must describe THIS fight."""
    from simlab.runner import play_combat

    spec = ScenarioSpec(
        encounter=IMPORTED_ENCOUNTER,
        ascension=4,
        deck_name="pasted",
        deck_cards=(),
        start_state=board,
        start_label="pasted",
    )
    row = play_combat(spec, "block_first", seed=5)
    assert row["hp_start"] == 71
    assert row["deck"] == "pasted"
    assert row["encounter"] == IMPORTED_ENCOUNTER
    assert row.get("error") is None
