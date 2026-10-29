"""The treasure-room reveal payload (``workbench.backends.rust_game``).

A chest is auto-resolved by the engine on room entry, so it never reaches the
UI as a decision — the workbench used to show nothing at all for a treasure
room, just three lines in the event log. ``_treasure_reveal`` builds the
display payload that lets the frontend draw the room. These are pure-dict
tests: no ``sts2_rs`` extension needed.
"""

from __future__ import annotations

from typing import Any

from workbench.backends.rust_game import _treasure_reveal

TREASURE = {"room_type": "treasure", "map_coord": (2, 8)}
MONSTER = {"room_type": "monster", "map_coord": (1, 7)}


def snap(gold: int, relics: list[str], room: dict[str, Any] | None) -> dict[str, Any]:
    return {"gold": gold, "relics": relics, "room": room}


def test_entering_a_chest_reports_its_gold_and_relic() -> None:
    reveal = _treasure_reveal(
        snap(60, ["AKABEKO"], MONSTER),
        snap(103, ["AKABEKO", "VENERABLE_TEA_SET"], TREASURE),
        act=2,
    )
    assert reveal is not None
    assert reveal["kind"] == "treasure"
    assert reveal["gold"] == 43
    assert reveal["act"] == 2
    assert reveal["relic"]["name"] == "VENERABLE_TEA_SET.title"


def test_the_chests_own_relic_is_the_first_one_gained() -> None:
    # An `AfterObtained` listener can queue further relics behind the chest's
    # own; `obtain_relic` appends the chest relic first, so that is the one
    # the chest is showing off.
    reveal = _treasure_reveal(
        snap(0, [], MONSTER),
        snap(43, ["VENERABLE_TEA_SET", "BOTTLED_FLAME"], TREASURE),
        act=1,
    )
    assert reveal is not None
    assert reveal["relic"]["name"] == "VENERABLE_TEA_SET.title"


def test_an_empty_chest_still_reveals() -> None:
    # Silver Crucible vetoes the first chest: no relic, no gold, and the engine
    # skips the rolls entirely. That is an outcome worth showing, not a miss.
    reveal = _treasure_reveal(snap(60, [], MONSTER), snap(60, [], TREASURE), act=1)
    assert reveal is not None
    assert reveal["gold"] == 0
    assert reveal["relic"] is None


def test_acting_again_inside_the_same_chest_room_does_not_re_reveal() -> None:
    # An on-obtain card pick resolves while the player still stands in the
    # treasure room. Re-showing the panel there would claim a second, empty
    # chest.
    assert _treasure_reveal(snap(103, ["X"], TREASURE), snap(103, ["X"], TREASURE), act=1) is None


def test_a_second_chest_room_reveals_again() -> None:
    later = {"room_type": "treasure", "map_coord": (4, 12)}
    reveal = _treasure_reveal(snap(103, ["X"], TREASURE), snap(146, ["X", "Y"], later), act=2)
    assert reveal is not None
    assert reveal["gold"] == 43


def test_other_rooms_reveal_nothing() -> None:
    assert _treasure_reveal(snap(60, [], MONSTER), snap(60, [], MONSTER), act=1) is None
    assert _treasure_reveal(snap(60, [], TREASURE), snap(60, [], MONSTER), act=1) is None


def test_no_action_yet_reveals_nothing() -> None:
    # Run start serializes with no prior snapshot; you cannot begin in a chest.
    assert _treasure_reveal(None, snap(99, [], TREASURE), act=1) is None


def test_a_backend_without_current_room_reveals_nothing() -> None:
    assert _treasure_reveal(snap(60, [], None), snap(60, [], None), act=1) is None
