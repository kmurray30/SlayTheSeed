"""In-combat card selections the trained nets do not cover.

The regression these pin is issue 5 of the 2026-08-06 ascension climb: every
such selection was answered with the leftmost card in hand.
"""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "python"))

import realgame_hand_select as hs


def card(cid: str, name: str, *, rarity: str = "Common", upgrade: int = 0, cost: int = 1, **kw):
    return {
        "id": cid,
        "name": name,
        "rarity": rarity,
        "type": kw.pop("type", "Attack"),
        "upgrade_level": upgrade,
        "cost": cost,
        **kw,
    }


def select(cards, **kw):
    state = {"type": "card_select", "screen": "NPlayerHand", "cards": list(cards)}
    for i, c in enumerate(state["cards"]):
        c.setdefault("index", i)
    state.update(kw)
    return state


# --------------------------------------------------------------------------
# The bug that started this: Burning Pact exhausted the upgraded Strike.
# --------------------------------------------------------------------------


def test_burning_pact_exhausts_the_unupgraded_strike_not_the_upgraded_one():
    """The live hand from run 006, in its original order.

    `Strike+` sat at index 0 and was exhausted; the plain `Strike` at index 1
    is the correct target.
    """
    state = select(
        [
            card("STRIKE_IRONCLAD", "Strike+", rarity="Basic", upgrade=1),
            card("STRIKE_IRONCLAD", "Strike", rarity="Basic"),
            card("BATTLE_TRANCE", "Battle Trance+", rarity="Uncommon", upgrade=1),
            card("SPITE", "Spite", rarity="Common"),
            card("DOMINATE", "Dominate+", rarity="Rare", upgrade=1),
            card("FIGHT_ME", "Fight Me!", rarity="Uncommon"),
        ],
        prompt_key="TO_EXHAUST",
        source_model="BURNING_PACT",
        min_select=1,
        max_select=1,
    )
    advice = hs.rank(state)
    assert advice is not None
    assert advice["best"]["label"] == "Strike"
    assert advice["best"]["index"] == 1


def test_a_curse_outranks_every_other_exhaust_target():
    state = select(
        [
            card("STRIKE_IRONCLAD", "Strike", rarity="Basic"),
            card("ASCENDERS_BANE", "Ascender's Bane", rarity="Curse", type="Curse", is_curse=True),
            card("DEFEND_IRONCLAD", "Defend", rarity="Basic", type="Skill"),
        ],
        prompt_key="TO_EXHAUST",
    )
    advice = hs.rank(state)
    assert advice is not None
    assert advice["best"]["label"] == "Ascender's Bane"


def test_a_status_is_dumped_before_a_basic_but_after_a_curse():
    ordering = [
        hs.card_value(card("C", "c", rarity="Curse", type="Curse")),
        hs.card_value(card("S", "s", rarity="Status", type="Status")),
        hs.card_value(card("B", "b", rarity="Basic")),
        hs.card_value(card("U", "u", rarity="Uncommon")),
        hs.card_value(card("R", "r", rarity="Rare")),
    ]
    assert ordering == sorted(ordering)


# --------------------------------------------------------------------------
# Direction: the same hand must NOT be answered the same way both ways.
# --------------------------------------------------------------------------


def test_dual_wield_copies_the_best_card_not_the_worst():
    hand = [
        card("STRIKE_IRONCLAD", "Strike", rarity="Basic"),
        card("DEMON_FORM", "Demon Form+", rarity="Rare", upgrade=1, type="Power", cost=3),
    ]
    boost = hs.rank(select(hand, prompt_key="DUAL_WIELD.selectionScreenPrompt"))
    dispose = hs.rank(select(hand, prompt_key="TO_EXHAUST"))
    assert boost is not None and dispose is not None
    assert boost["best"]["label"] == "Demon Form+"
    assert dispose["best"]["label"] == "Strike"


@pytest.mark.parametrize(
    ("key", "direction"),
    [
        ("TO_EXHAUST", hs.DISPOSE),
        ("TO_DISCARD", hs.DISPOSE),
        ("ASHWATER.selectionScreenPrompt", hs.DISPOSE),
        ("GAMBLERS_BREW.selectionScreenPrompt", hs.DISPOSE),
        ("PURITY.selectionScreenPrompt", hs.DISPOSE),
        ("DUAL_WIELD.selectionScreenPrompt", hs.BOOST),
        ("NIGHTMARE.selectionScreenPrompt", hs.BOOST),
        ("THINKING_AHEAD.selectionScreenPrompt", hs.BOOST),
    ],
)
def test_prompt_keys_classify(key, direction):
    assert hs.direction_of({"prompt_key": key})[0] == direction


def test_source_model_names_the_direction_when_the_prompt_key_is_missing():
    assert hs.direction_of({"source_model": "ASHWATER"})[0] == hs.DISPOSE


def test_prompt_key_wins_over_source_model():
    """`FromHandForDiscard` delegates to `FromHand` (CardSelectCmd.cs:763).

    The captured `source` is overwritten by that inner call; the prefs -- and
    so the prompt key -- are not. Prompt key is therefore authoritative.
    """
    state = {"prompt_key": "TO_DISCARD", "source_model": "DUAL_WIELD"}
    assert hs.direction_of(state)[0] == hs.DISPOSE


# --------------------------------------------------------------------------
# Refusing to guess is a feature.
# --------------------------------------------------------------------------


def test_an_unclassified_prompt_returns_none_rather_than_guessing():
    state = select(
        [card("A", "A"), card("B", "B")],
        prompt_key="SCULPTING_STRIKE.selectionScreenPrompt",
    )
    assert hs.rank(state) is None
    assert hs.direction_of(state)[0] == hs.UNKNOWN


def test_no_prompt_at_all_is_unknown():
    assert hs.rank(select([card("A", "A")])) is None


def test_empty_selection_is_none():
    assert hs.rank(select([], prompt_key="TO_EXHAUST")) is None


# --------------------------------------------------------------------------
# Shape contract with the mod.
# --------------------------------------------------------------------------


def test_the_reply_carries_the_bridge_index_not_the_list_position():
    """`ChooseHandCard` indexes the offered list, so the mod's own `index`
    field on each card is the one to answer with."""
    state = select(
        [
            card("STRIKE_IRONCLAD", "Strike+", rarity="Basic", upgrade=1),
            card("STRIKE_IRONCLAD", "Strike", rarity="Basic"),
        ],
        prompt_key="TO_EXHAUST",
    )
    state["cards"][0]["index"] = 7
    state["cards"][1]["index"] = 9
    advice = hs.rank(state)
    assert advice is not None
    assert advice["best"]["index"] == 9
    assert [o["index"] for o in advice["options"]] == [7, 9]
    assert sum(1 for o in advice["options"] if o["is_best"]) == 1


def test_the_note_says_it_is_a_heuristic():
    advice = hs.rank(select([card("A", "A")], prompt_key="TO_EXHAUST"))
    assert advice is not None
    assert "heuristic" in advice["note"]


def test_ties_pick_the_leftmost_so_a_resumed_run_repeats_itself():
    state = select(
        [card("STRIKE_IRONCLAD", "Strike", rarity="Basic") for _ in range(3)],
        prompt_key="TO_EXHAUST",
    )
    assert hs.rank(state)["best"]["index"] == 0
