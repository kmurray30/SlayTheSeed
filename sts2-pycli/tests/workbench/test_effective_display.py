"""The workbench must show EFFECTIVE combat numbers, not base ones (GH #520).

Regression pin for bug report ``wb-b5c453e74f1b80e3``: a Necrobinder facing a
Bygone Effigy with Strength 10 saw the intent rendered as "13" (the base slash)
against a Defend card face reading "5 Block" (base, no Dexterity), while the
engine was really about to deal 23 and grant 10. Under those numbers the
Necrobinder summon cards (7 / 9 Osty HP) look like the better play; under the
real ones Defend mitigates the most. The display gap produced a false
"MCTS is mis-ranking summons" bug report.

Each test builds the state through the wire so it pins the workbench's own
``build_combat_view`` path, not the raw binding.
"""

from __future__ import annotations

from typing import Any

import pytest

pytest.importorskip("sts2_rs", reason="rust backend requires the sts2_rs extension")

import sts2_rs
from workbench.backends.rust_game import build_combat_view


def _card(cid: str, upgraded: bool = False, kind: str = "card") -> dict[str, Any]:
    return {"id": cid, "stable_idx": -1, "kind": kind, "upgraded": upgraded}


def _wire(
    *,
    player_powers: list[dict[str, Any]],
    enemy_powers: list[dict[str, Any]],
    hand: list[dict[str, Any]],
    intent_damage: int = 13,
) -> dict[str, Any]:
    """A minimal Necrobinder-vs-Bygone-Effigy wire state.

    ``intent_damage`` is the BASE slash — ``state_to_dict``'s own convention,
    which is what the workbench's producer emits (``intent_amounts: "base"``).
    """
    return {
        "wire_version": 1,
        "phase": "PlayerPlayPhase",
        "round": 3,
        "turn": 3,
        "side": "Player",
        "is_elite": True,
        "combat_id": 0,
        "intent_amounts": "base",
        "player": {
            "class": "necrobinder",
            "hp": 64,
            "max_hp": 66,
            "block": 0,
            "energy": 3,
            "max_energy": 3,
            "energy_regen_per_turn": 3,
            "gold": 0,
            "relics": [],
            "powers": player_powers,
            "potions": [None, None, None],
            "osty": {"alive": True, "hp": 3, "max_hp": 3, "block": 0},
        },
        "deck": {
            "draw_pile": [],
            "discard_pile": [],
            "hand": hand,
            "exhaust_pile": [],
            "master_deck": list(hand),
        },
        "encounter": [
            {
                "kind": "BygoneEffigy",
                "uid": 100,
                "hp": 111,
                "max_hp": 127,
                "block": 0,
                "powers": enemy_powers,
                "intents": [{"kind": "Attack", "amount": intent_damage, "times": 1}],
                "intent_history": [],
                "move_state": "SLASHES",
                "is_dead": False,
                "is_escaped": False,
            }
        ],
        "rng": {"seed": 1, "ascension": 0},
    }


def _view(wire: dict[str, Any]) -> dict[str, Any]:
    cs = sts2_rs.state_from_dict(wire)
    legal = [
        {"action": "play", "card_index": i, "target_index": None}
        for i in range(len(wire["deck"]["hand"]))
    ]
    return build_combat_view(cs, "Necrobinder", legal=legal)


DEFEND = _card("DEFEND_NECROBINDER")
STRIKE = _card("STRIKE_NECROBINDER")


def test_enemy_intent_damage_is_effective_not_base() -> None:
    """Strength 10 on a 13-base slash must render as 23."""
    view = _view(
        _wire(
            player_powers=[],
            enemy_powers=[{"name": "STRENGTH", "amount": 10}],
            hand=[DEFEND],
        )
    )
    intent = view["enemies"][0]["intents"][0]
    assert intent["damage"] == 23, (
        "the panel must show the hit the engine will actually deal, not the "
        f"base slash (got {intent['damage']})"
    )


def test_enemy_intent_damage_matches_base_when_unmodified() -> None:
    """Control: with no Strength the effective number IS the base one."""
    view = _view(_wire(player_powers=[], enemy_powers=[], hand=[DEFEND]))
    assert view["enemies"][0]["intents"][0]["damage"] == 13


def test_block_card_carries_a_dexterity_aware_preview() -> None:
    """Dexterity 5 turns Defend's 5 base block into 10 granted."""
    view = _view(
        _wire(
            player_powers=[{"name": "DEXTERITY", "amount": 5}],
            enemy_powers=[],
            hand=[DEFEND],
        )
    )
    card = view["hand"][0]
    assert card["stats"]["block"] == 5, "stats.block stays the card's base value"
    assert card.get("block_preview") == 10, (
        "a block card must advertise the block it will really grant "
        f"(got {card.get('block_preview')!r})"
    )


def test_block_preview_absent_without_modifiers() -> None:
    """No Dexterity / Frail: nothing to preview, so the key stays off."""
    view = _view(_wire(player_powers=[], enemy_powers=[], hand=[DEFEND]))
    assert view["hand"][0].get("block_preview") is None


def test_non_block_card_has_no_block_preview() -> None:
    view = _view(
        _wire(
            player_powers=[{"name": "DEXTERITY", "amount": 5}],
            enemy_powers=[],
            hand=[STRIKE],
        )
    )
    assert view["hand"][0].get("block_preview") is None


def test_wb_b5c453_position_shows_every_number_effective() -> None:
    """The reported position end to end: intent 23, Defend 10, Strike 9.

    Necrobinder floor 7, Bygone Effigy with Strength 10, player carrying the
    Speed Potion's Dexterity 5 and Lethality 50. Every number the panel prints
    must be the one the engine will use — that is the whole of GH #520, and
    the three used to read 13 / 5 / 6.
    """
    view = _view(
        _wire(
            player_powers=[
                {"name": "LETHALITY", "amount": 50},
                {"name": "DEXTERITY", "amount": 5},
            ],
            enemy_powers=[{"name": "STRENGTH", "amount": 10}],
            hand=[DEFEND, STRIKE],
        )
    )
    assert view["enemies"][0]["intents"][0]["damage"] == 23
    assert view["hand"][0]["block_preview"] == 10
    assert view["hand"][1]["damage_preview"]["0"] == 9


def test_lossy_scoring_path_reconstructs_the_real_hit() -> None:
    """``reasoning.score`` round-trips this dict back into a combat state.

    That path (``branching``/``server`` replay + regrade, ``live.scores``) feeds
    the view through ``sts2_hint.adapter.translate``, which stamps
    ``intent_amounts: "effective"`` and lets the parser invert Strength out to
    recover the base. Publishing the BASE amount there was not just a wrong
    label: 13 was read as effective, inverted to a base of 3, and the search
    then planned against a 13-damage Effigy instead of the real 23-damage one.
    """
    from sts2_hint import adapter

    view = _view(
        _wire(
            player_powers=[],
            enemy_powers=[{"name": "STRENGTH", "amount": 10}],
            hand=[DEFEND],
        )
    )
    rebuilt = sts2_rs.state_from_dict(adapter.translate(view, character="Necrobinder"))
    intents = sts2_rs.state_to_dict(rebuilt)["encounter"][0]["intents"]
    assert intents[0]["amount"] == 13, "base survives the round trip"
    assert intents[0]["effective_amount"] == 23, (
        "the search must plan against the hit that will really land "
        f"(got {intents[0]['effective_amount']})"
    )

    # And prove it end to end: the reconstructed enemy actually hits for 23.
    before = sts2_rs.state_to_dict(rebuilt)["player"]["hp"]
    rebuilt.apply({"action": "end_turn"})
    after = sts2_rs.state_to_dict(rebuilt)["player"]["hp"]
    assert (before - after) + 3 == 23, "3 of the 23 is soaked by the 3 HP Osty"


def test_damage_preview_folds_in_lethality() -> None:
    """Lethality 50 boosts the turn's FIRST attack by 50%: Strike 6 -> 9.

    The preview is computed with nothing in the Play pile, and Lethality's gate
    requires a card to be mid-play — so an unguarded preview reports the bare 6.
    """
    view = _view(
        _wire(
            player_powers=[{"name": "LETHALITY", "amount": 50}],
            enemy_powers=[],
            hand=[STRIKE],
        )
    )
    preview = view["hand"][0]["damage_preview"]
    assert preview["0"] == 9, (
        f"Lethality's first-attack bonus must reach the preview (got {preview['0']})"
    )


# ---------------------------------------------------------------------------
# …and WHY it is that number (bug report ``wb-4eae3bad6ebd57f8``).
#
# Showing the effective number fixed one confusion and created another: a
# telegraph that already has a debuff baked into it is indistinguishable from
# one that has none. An Ironclad played Uppercut on a Torch Head Amalgam,
# applied Weak, watched the 12x3 telegraph not move, and reported the engine as
# broken. It was right — the enemy already had Weak from an earlier Shockwave,
# and ``WeakPower.StackType = Counter`` makes stacks DURATION, so the flat
# x0.75 was already in the 12. The panel now names the listeners behind the
# number so that is legible without reading the decomp.
# ---------------------------------------------------------------------------


def test_intent_modifiers_name_the_listeners_behind_the_number() -> None:
    """Strength 10 on a 13-base slash renders 23 AND says +10 Strength."""
    view = _view(
        _wire(
            player_powers=[],
            enemy_powers=[{"name": "STRENGTH", "amount": 10}],
            hand=[DEFEND],
        )
    )
    intent = view["enemies"][0]["intents"][0]
    assert intent["damage"] == 23
    assert intent["base_damage"] == 13
    assert [(m["name"], m.get("add"), m.get("mul")) for m in intent["modifiers"]] == [
        ("STRENGTH", 10, None)
    ]
    assert intent["modifiers"][0]["side"] == "enemy", "the ATTACKER's Strength"


def test_weak_stacks_do_not_move_the_telegraph_but_the_weak_is_named() -> None:
    """The bug report's board, reduced: Weak at 1/2/3 stacks is the same hit.

    ``WeakPower.ModifyDamageMultiplicative`` returns the flat
    ``DynamicVars["DamageDecrease"] = 0.75m`` without ever reading ``Amount``
    (``.../Models/Powers/WeakPower.cs``). Pinned across stack counts against
    the no-Weak control, because "the number did not change" is only
    reassuring next to the number it would have been.
    """
    seen = []
    for stacks in (1, 2, 3):
        view = _view(
            _wire(
                player_powers=[],
                enemy_powers=[
                    {"name": "STRENGTH", "amount": 10},
                    {"name": "WEAK", "amount": stacks},
                ],
                hand=[DEFEND],
            )
        )
        intent = view["enemies"][0]["intents"][0]
        seen.append(intent["damage"])
        names = [m["name"] for m in intent["modifiers"]]
        assert names == ["STRENGTH", "WEAK"], f"Weak {stacks}: {names}"
        weak = intent["modifiers"][1]
        assert weak["mul"] == [3, 4], "the flat x0.75, whatever the stack count"

    # floor((13 + 10) * 3/4) = 17, identically at every stack count…
    assert seen == [17, 17, 17]
    # …against the 23 the same telegraph reads with no Weak on the board.
    plain = _view(
        _wire(
            player_powers=[],
            enemy_powers=[{"name": "STRENGTH", "amount": 10}],
            hand=[DEFEND],
        )
    )
    assert plain["enemies"][0]["intents"][0]["damage"] == 23


def test_an_unmodified_telegraph_carries_no_modifiers_key() -> None:
    """Nothing moved the number, so there is nothing to explain."""
    view = _view(_wire(player_powers=[], enemy_powers=[], hand=[DEFEND]))
    intent = view["enemies"][0]["intents"][0]
    assert intent["damage"] == 13
    assert "base_damage" not in intent
    assert "modifiers" not in intent
