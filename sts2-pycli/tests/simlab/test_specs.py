import pytest
import sts2_rs
from simlab.runner import play_combat
from simlab.specs import (
    DECK_PRESETS,
    ScenarioSpec,
    SweepSpec,
    build_combat,
    parse_encounter,
    validate_deck,
    validate_encounter,
)

STARTER = DECK_PRESETS["ironclad_starter"]


def _sweep(**kw):
    base = dict(
        name="t",
        encounters=["MAWLER_NORMAL"],
        ascensions=[0],
        strategies=["random"],
        n_seeds=2,
        decks=[("starter", STARTER)],
    )
    base.update(kw)
    return SweepSpec(**base)


@pytest.mark.parametrize("preset", sorted(DECK_PRESETS))
def test_all_presets_playable(preset: str) -> None:
    row = play_combat(
        ScenarioSpec(
            encounter="MAWLER_NORMAL",
            deck_name=preset,
            deck_cards=tuple(DECK_PRESETS[preset]),
        ),
        "random",
        seed=1,
    )
    assert "error" not in row and not row["stuck"]


def test_starter_preset() -> None:
    deck = DECK_PRESETS["ironclad_starter"]
    assert len(deck) == 10
    assert deck.count("STRIKE_IRONCLAD") == 5
    assert deck.count("BASH") == 1


def test_build_combat_deterministic() -> None:
    spec = ScenarioSpec(encounter="MAWLER_NORMAL", ascension=0)
    a = build_combat(spec, seed=42)
    b = build_combat(spec, seed=42)
    assert a.state_hash() == b.state_hash()
    assert build_combat(spec, 42).state_hash() != build_combat(spec, 43).state_hash()
    d = sts2_rs.state_to_dict(a)
    assert d["player"]["hp"] == spec.hp
    assert d["rng"]["ascension"] == 0


def test_sweep_spec_expansion() -> None:
    sw = SweepSpec(
        name="t",
        encounters=["MAWLER_NORMAL", "SHRINKER_BEETLE_WEAK"],
        ascensions=[0, 9],
        strategies=["random", "min_overblock"],
        n_seeds=3,
    )
    cells = sw.cells()
    # cartesian over encounter x ascension x deck x strategy (default = 1 deck),
    # seeds NOT expanded here
    assert len(cells) == 2 * 2 * 1 * 2
    assert cells[0].scenario.encounter == "MAWLER_NORMAL"


def test_sweep_spec_json_roundtrip() -> None:
    sw = SweepSpec(
        name="t",
        encounters=["MAWLER_NORMAL", "SHRINKER_BEETLE_WEAK"],
        ascensions=[0, 9],
        strategies=["random", "min_overblock"],
        n_seeds=3,
    )
    assert SweepSpec.from_json(sw.to_json()) == sw


def test_sweep_decks_axis_cells():
    sw = _sweep(decks=[("a", STARTER), ("b", STARTER[1:])])
    cells = sw.cells()
    assert len(cells) == 2
    assert {c.scenario.deck_name for c in cells} == {"a", "b"}
    assert cells[1].scenario.deck_cards == tuple(STARTER[1:])


def test_sweep_decks_coerced_to_tuples_and_json_roundtrip():
    sw = _sweep(decks=[["a", list(STARTER)]])
    assert sw.decks == (("a", tuple(STARTER)),)
    again = SweepSpec.from_json(sw.to_json())
    assert again == sw


def test_sweep_rejects_duplicate_deck_names():
    with pytest.raises(ValueError, match="duplicate deck names"):
        _sweep(decks=[("a", STARTER), ("a", STARTER[1:])])


def test_validate_deck_rejects_slashed_name():
    with pytest.raises(ValueError, match="invalid deck name"):
        validate_deck("a/b", STARTER)


def test_from_json_legacy_deck_preset():
    d = _sweep().to_json()
    del d["decks"]
    d["deck_preset"] = "turtle"
    sw = SweepSpec.from_json(d)
    assert sw.decks == (("turtle", tuple(DECK_PRESETS["turtle"])),)


def test_validate_deck_rejects_bad_ids_with_suggestion():
    with pytest.raises(ValueError, match="STRIKE_IRONCLAD"):
        validate_deck("x", ["STRIKE_IRONCLD"])
    with pytest.raises(ValueError, match="empty"):
        validate_deck("x", [])
    validate_deck("x", ["STRIKE_IRONCLAD+1"])  # upgrade suffix stripped


def test_parse_encounter():
    assert parse_encounter("MAWLER_NORMAL") == ("MAWLER_NORMAL", [])
    assert parse_encounter("monsters:Nibbit,Nibbit")[1] == ["Nibbit", "Nibbit"]


def test_validate_encounter():
    validate_encounter("MAWLER_NORMAL")  # plain ids pass through (engine validates at build)
    validate_encounter("monsters:Nibbit")
    with pytest.raises(ValueError, match="Nibbit"):
        validate_encounter("monsters:Nibit")


def test_build_combat_custom_monsters():
    spec = ScenarioSpec(
        encounter="monsters:Nibbit,Nibbit",
        ascension=9,
        deck_name="starter",
        deck_cards=tuple(STARTER),
    )
    d = sts2_rs.state_to_dict(build_combat(spec, seed=7))
    assert len(d["encounter"]) == 2
