"""The sandbox position ↔ engine wire mapping.

What these pin: a position the engine dealt comes back unchanged through the
round trip (so "Built." means what it says); every field a person can edit
reaches the engine; a mod Copy-state paste loads; and what the position cannot
carry is *said*, never dropped in silence.
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest

pytest.importorskip("sts2_rs", reason="the sandbox needs the sts2_rs extension")

from simlab.catalog import STARTERS, catalog, name_of
from simlab.position import (
    SCHEMA,
    PositionError,
    action_label,
    build_position,
    empty_position,
    first_moves,
    mcts_eval,
    new_position,
    normalize_card_id,
    position_from_paste,
    position_from_wire,
    wire_from_position,
)

FIXTURES = Path(__file__).resolve().parents[1] / "fixtures" / "realgame_adapter"


@pytest.fixture(scope="module")
def crawler():
    return new_position(
        character="Ironclad", encounter="FUZZY_WURM_CRAWLER_WEAK", ascension=10, seed=7
    )


# ---------------------------------------------------------------------------
# Round trips
# ---------------------------------------------------------------------------


def test_new_position_is_a_dealt_turn_one(crawler):
    pos = crawler.position
    assert pos["schema"] == SCHEMA
    assert pos["character"] == "Ironclad"
    assert pos["ascension"] == 10
    assert pos["turn"] == 1
    assert pos["encounter"] == "FUZZY_WURM_CRAWLER_WEAK"
    assert len(pos["piles"]["hand"]) == 5
    assert len(pos["piles"]["draw"]) == 5
    assert pos["player"]["relics"] == [{"id": "BURNING_BLOOD", "counter": 0}]
    (m,) = pos["monsters"]
    assert m["monster"] == "FuzzyWurmCrawler"
    assert m["intent"] == "FIRST_ACID_GOOP"
    assert m["hp"] == m["maxHp"] >= 58


def test_build_is_a_fixed_point(crawler):
    built = build_position(crawler.position)
    assert built.position == crawler.position
    assert built.notes == []


@pytest.mark.parametrize(
    ("character", "encounter"),
    [
        ("Silent", "SHRINKER_BEETLE_WEAK"),
        ("Defect", "MAWLER_NORMAL"),
        ("Regent", "VANTOM_BOSS"),
        ("Necrobinder", "MAWLER_NORMAL"),
    ],
)
def test_every_character_round_trips(character, encounter):
    fresh = new_position(character=character, encounter=encounter, seed=3)
    again = build_position(fresh.position)
    assert again.position == fresh.position
    if character == "Defect":
        assert fresh.position["player"]["orbSlots"] == 3
    if character == "Necrobinder":
        assert fresh.position["player"]["osty"] is not None


def test_wire_from_position_passes_the_schema(crawler):
    import sts2_rs

    wire = wire_from_position(crawler.position)
    sts2_rs.parse_state_dict_py(wire)  # raises on a schema violation
    assert wire["player"]["class"] == "ironclad"
    assert wire["player"]["energy_regen_per_turn"] == wire["player"]["max_energy"]
    assert wire["intent_amounts"] == "base"
    (enemy,) = wire["encounter"]
    assert enemy["move_state"] == "FIRST_ACID_GOOP"
    # An intent-less enemy skips its turn (combat_loop), so the telegraph MUST ride along.
    assert enemy["intents"] and enemy["intents"][0]["kind"] == "Attack"


# ---------------------------------------------------------------------------
# Edits reach the engine
# ---------------------------------------------------------------------------


def test_edits_survive_the_build(crawler):
    pos = json.loads(json.dumps(crawler.position))
    pos["player"]["hp"] = 12
    pos["player"]["block"] = 4
    pos["player"]["energy"] = 5
    pos["player"]["potions"] = ["FIRE_POTION"]
    pos["player"]["powers"] = [{"id": "STRENGTH", "amount": 2}]
    pos["piles"]["hand"].append({"card": "DEMON_FORM_CARD", "upgrade": 1, "cost": None})
    pos["piles"]["discard"] = [{"card": "STRIKE_IRONCLAD", "upgrade": 0, "cost": 0}]
    pos["monsters"][0]["hp"] = 20
    pos["monsters"][0]["block"] = 3
    pos["monsters"][0]["intent"] = "INHALE"
    pos["monsters"][0]["powers"] = [{"id": "STRENGTH", "amount": 1}]
    built = build_position(pos).position
    p = built["player"]
    assert (p["hp"], p["block"], p["energy"], p["potions"]) == (12, 4, 5, ["FIRE_POTION"])
    assert {(x["id"], x["amount"]) for x in p["powers"]} == {("STRENGTH", 2)}
    assert built["piles"]["hand"][-1] == {"card": "DEMON_FORM_CARD", "upgrade": 1, "cost": 3}
    assert built["piles"]["discard"][0]["cost"] == 0  # a cost override is an instance cost
    m = built["monsters"][0]
    assert (m["hp"], m["block"], m["intent"]) == (20, 3, "INHALE")
    assert m["powers"] == [{"id": "STRENGTH", "amount": 1, "counter": None}]


def test_draw_order_is_the_order_given(crawler):
    pos = json.loads(json.dumps(crawler.position))
    pos["piles"]["draw"] = [
        {"card": "BASH", "upgrade": 0, "cost": None},
        {"card": "DEFEND_IRONCLAD", "upgrade": 0, "cost": None},
        {"card": "STRIKE_IRONCLAD", "upgrade": 1, "cost": None},
    ]
    built = build_position(pos).position
    assert [(c["card"], c["upgrade"]) for c in built["piles"]["draw"]] == [
        ("BASH", 0),
        ("DEFEND_IRONCLAD", 0),
        ("STRIKE_IRONCLAD", 1),
    ]


def test_unnamed_intent_is_rolled_and_named(crawler):
    pos = json.loads(json.dumps(crawler.position))
    pos["monsters"][0]["intent"] = None
    built = build_position(pos).position
    assert built["monsters"][0]["intent"] in {"FIRST_ACID_GOOP", "ACID_GOOP", "INHALE"}


def test_custom_lineup_and_bridge_move_spelling():
    fresh = new_position(character="Ironclad", monsters=["Nibbit", "Nibbit"], seed=5)
    assert [m["monster"] for m in fresh.position["monsters"]] == ["Nibbit", "Nibbit"]
    pos = json.loads(json.dumps(fresh.position))
    # The C# dialect keeps a `_MOVE` suffix the engine's ids dropped.
    pos["monsters"][0]["intent"] = "HISS_MOVE"
    assert build_position(pos).position["monsters"][0]["intent"] == "HISS"


# ---------------------------------------------------------------------------
# Refusals name the problem
# ---------------------------------------------------------------------------


def test_unknown_card_is_a_position_error_with_the_path(crawler):
    pos = json.loads(json.dumps(crawler.position))
    pos["piles"]["hand"][0]["card"] = "NOT_A_CARD"
    with pytest.raises(PositionError) as exc:
        build_position(pos)
    assert "NOT_A_CARD" in str(exc.value)


def test_unknown_move_lists_the_real_ones(crawler):
    pos = json.loads(json.dumps(crawler.position))
    pos["monsters"][0]["intent"] = "TELEPORT"
    with pytest.raises(PositionError) as exc:
        build_position(pos)
    assert "INHALE" in str(exc.value)


@pytest.mark.parametrize(
    ("patch", "needle"),
    [
        ({"player": {"hp": 90, "maxHp": 80}}, "above max"),
        ({"monsters": []}, "no enemies"),
        ({"piles": {"hand": [], "draw": [], "discard": [], "exhaust": []}}, "no cards"),
    ],
)
def test_shape_errors(crawler, patch, needle):
    pos = json.loads(json.dumps(crawler.position))
    for key, value in patch.items():
        if isinstance(value, dict) and isinstance(pos.get(key), dict):
            pos[key] = {**pos[key], **value}
        else:
            pos[key] = value
    with pytest.raises(PositionError) as exc:
        wire_from_position(pos)
    assert needle in str(exc.value)


def test_finished_fight_is_refused():
    with pytest.raises(PositionError):
        position_from_wire({"phase": "Victory", "player": {}, "deck": {}})


# ---------------------------------------------------------------------------
# Pastes
# ---------------------------------------------------------------------------


def _mod_paste() -> str:
    wire = json.loads((FIXTURES / "single_enemy_ironclad.json").read_text(encoding="utf-8"))
    return json.dumps({**wire["state"], "type": "advice_request", "advice_for": "combat_action"})


def test_mod_copy_state_loads_and_builds():
    pos, _notes, source = position_from_paste(_mod_paste())
    assert source == "advice_request"
    assert [c["card"] for c in pos["piles"]["hand"]] == [
        "DEFEND_IRONCLAD",
        "DEFEND_IRONCLAD",
        "STRIKE_IRONCLAD",
        "STRIKE_IRONCLAD",
        "BASH",
    ]
    (m,) = pos["monsters"]
    assert m["monster"] == "ShrinkerBeetle"
    # The bridge's `SHRINKER_MOVE` came back as the engine's own id.
    assert m["intent"] == "SHRINK"
    assert build_position(pos).position == pos


def test_position_and_engine_wire_pastes(crawler):
    pos, _, source = position_from_paste(json.dumps(crawler.position))
    assert source == "position" and pos["schema"] == SCHEMA
    pos2, _, source2 = position_from_paste(json.dumps(crawler.wire))
    assert source2 == "engine wire"
    assert pos2["piles"]["hand"] == crawler.position["piles"]["hand"]


def test_paste_without_a_fight_says_so():
    with pytest.raises(PositionError):
        position_from_paste("not json at all")


def test_status_ids_normalise():
    assert normalize_card_id("status:Slimed") == "SLIMED"
    assert normalize_card_id("curse:AscendersBane") == "ASCENDERS_BANE"
    assert normalize_card_id("STRIKE_IRONCLAD") == "STRIKE_IRONCLAD"


# ---------------------------------------------------------------------------
# First moves + search
# ---------------------------------------------------------------------------


def test_first_moves_cover_every_legal_action(crawler):
    moves = first_moves(crawler.wire, name_of=name_of)
    labels = [m["label"] for m in moves]
    assert "End turn" in labels
    assert any(lbl.startswith("Play Bash → Fuzzy Wurm Crawler") for lbl in labels)
    for m in moves:
        if m["note"] is None:
            assert build_position(m["position"]).wire["player"]["hp"] == m["wire"]["player"]["hp"]


def test_action_label_shapes(crawler):
    wire = crawler.wire
    assert action_label({"action": "end_turn"}, wire) == "End turn"
    assert action_label(
        {"action": "play", "card_index": 0, "target_index": 0}, wire, name_of
    ).startswith("Play ")
    assert action_label({"action": "potion", "slot": 9}, wire) == "Drink potion 9"


def test_mcts_eval_ranks_moves(crawler):
    out = mcts_eval(crawler.wire, sims=200, name_of=name_of)
    assert out["rows"] and out["rows"][0]["is_best"]
    assert all(0.0 <= r["win_prob"] <= 1.0 for r in out["rows"])


# ---------------------------------------------------------------------------
# Catalog
# ---------------------------------------------------------------------------


def test_catalog_names_everything_the_engine_knows():
    import sts2_rs

    cat = catalog()
    assert {c["id"] for c in cat["cards"]} == set(sts2_rs.list_registry_names("card"))
    assert {m["id"] for m in cat["monsters"]} == set(sts2_rs.list_registry_names("monster"))
    assert all(c["name"] for c in cat["cards"])
    assert all(isinstance(m["moves"], list) for m in cat["monsters"])
    crawler = next(m for m in cat["monsters"] if m["id"] == "FuzzyWurmCrawler")
    assert {mv["id"] for mv in crawler["moves"]} == {"FIRST_ACID_GOOP", "ACID_GOOP", "INHALE"}
    assert next(e for e in cat["encounters"] if e["id"] == "VANTOM_BOSS")["tier"] == "Boss"
    assert name_of("card", "DEMON_FORM_CARD") == "Demon Form"
    assert name_of("power", "STRENGTH") == "Strength"
    assert name_of("relic", "BURNING_BLOOD") == "Burning Blood"


def test_starters_are_real_registry_ids():
    import sts2_rs

    cards = set(sts2_rs.list_registry_names("card"))
    relics = set(sts2_rs.list_registry_names("relic"))
    for character, row in STARTERS.items():
        assert set(row["deck"]) <= cards, character
        assert row["relic"] in relics, character
        built = build_position(
            new_position(character=character, encounter="MAWLER_NORMAL", seed=1).position
        )
        assert built.position["player"]["maxHp"] == row["hp"]


def test_starters_match_the_wiki_when_present():
    from simlab.catalog import wiki_data_dir

    data = wiki_data_dir()
    if data is None or not (data / "v0.107.1" / "characters.json").is_file():
        pytest.skip("no wiki data on this machine")
    rows = json.loads((data / "v0.107.1" / "characters.json").read_text(encoding="utf-8"))
    for row in rows:
        name = row["class_name"]
        if name not in STARTERS:
            continue
        assert STARTERS[name]["hp"] == row["starting_hp"], name
        assert len(STARTERS[name]["deck"]) == len(row["starting_deck"]), name


def test_empty_position_is_well_formed():
    pos = empty_position("Silent")
    assert pos["character"] == "Silent" and pos["piles"]["hand"] == []
