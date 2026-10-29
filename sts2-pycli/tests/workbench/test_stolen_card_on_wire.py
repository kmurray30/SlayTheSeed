"""A Thieving Hopper mid-heist must say WHICH card it is holding.

``ThievingHopper.ThieveryMove`` parents a real card node to the thief's
``%StolenCardPos`` marker and ``SwipePower.ExtraHoverTips`` shows the whole
card, so in the game the card you lost is visibly in its hands. The engine
models the same theft as a flat ``CombatState.swipe_stolen`` ledger, which the
wire used to drop entirely: the workbench showed a bare "Swipe 1" pip while the
card silently disappeared from the deck view — and whether you kill the thief
before it escapes (``SwipePower.BeforeDeath`` gives the card back) is the whole
decision the fight is about.
"""

from __future__ import annotations

import pytest

pytest.importorskip("sts2_rs", reason="rust backend requires the sts2_rs extension")

import sts2_rs
from workbench.backends.rust_game import build_combat_view

# A deck of real (non-generated) cards, so the Hopper's candidate filter —
# `DeckVersion != null`, i.e. not combat-generated — always has something to
# take. Rarity decides WHICH one; any of them proves the plumbing.
_DECK = ["STRIKE_IRONCLAD"] * 5 + ["DEFEND_IRONCLAD"] * 4 + ["BASH"]


def _hopper_fight() -> sts2_rs.PyCombatState:
    return sts2_rs.build_scenario_state(
        7,
        "Ironclad",
        _DECK,
        80,
        80,
        [],
        "THIEVING_HOPPER_WEAK",
    )


def _end_turn(cs: sts2_rs.PyCombatState) -> None:
    """Pass the turn to the monsters, resolving any choice the engine opens."""
    cs.apply({"action": "end_turn"})
    for _ in range(20):
        if cs.pending_choice() is None:
            return
        cs.apply(cs.legal_actions()[0])
    raise AssertionError("combat never settled after end_turn")


def test_the_thief_carries_the_card_it_stole() -> None:
    cs = _hopper_fight()
    before = build_combat_view(cs, "Ironclad", legal=cs.legal_actions())
    assert not any(e.get("stolen_cards") for e in before["enemies"]), (
        "nothing is stolen before the Hopper's THIEVERY move resolves"
    )

    # THIEVERY is the Hopper's opening move, so one end_turn commits the theft.
    _end_turn(cs)

    view = build_combat_view(cs, "Ironclad", legal=cs.legal_actions())
    held = [sc for e in view["enemies"] for sc in e.get("stolen_cards") or []]
    assert len(held) == 1, f"exactly one card per THIEVERY; got {held}"
    card = held[0]
    # The display shape the card frame needs: an id the art/manifest resolver
    # keys off, a loc key for `server._enrich`, and the upgrade flag (a stolen
    # Bash+ is not a stolen Bash).
    assert card["id"] in {"STRIKE_IRONCLAD", "DEFEND_IRONCLAD", "BASH"}
    assert card["name"] == f"{card['id']}.title"
    assert card["description"] == f"{card['id']}.description"
    assert card["upgraded"] is False

    # …and it is on the THIEF, not on some other creature in the encounter.
    thieves = [e for e in view["enemies"] if e.get("stolen_cards")]
    assert [t["kind"] for t in thieves] == ["ThievingHopper"]


def test_enemies_that_stole_nothing_keep_their_historical_shape() -> None:
    """The key is omitted, not emitted empty — every other enemy dict is unchanged."""
    cs = _hopper_fight()
    view = build_combat_view(cs, "Ironclad", legal=cs.legal_actions())
    assert view["enemies"], "the scenario must spawn the Hopper"
    for enemy in view["enemies"]:
        assert "stolen_cards" not in enemy
