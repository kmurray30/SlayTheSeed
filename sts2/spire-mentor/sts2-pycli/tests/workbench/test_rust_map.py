"""Map-choice derivation for the Rust backend.

The map view turns the engine's legal ``move`` cols into display choices,
reading each choice's row + type from the map graph's *children of the current
node* rather than from ``map_coord[1] + 1`` arithmetic. The children-based
lookup stays correct when ``map_coord`` is absent (the engine omits it for some
rooms, e.g. events), where it falls back to the start node's children — matching
the moves the engine itself surfaces in that state.
"""

from __future__ import annotations

from typing import Any

from fake_rm import FakeRm
from workbench.backends.rust_game import RustGame

# A tiny three-row map: start (row 0) → two row-1 nodes → their row-2 children.
_FULL_MAP: dict[str, Any] = {
    "rows": [
        [
            {
                "col": 3,
                "row": 0,
                "type": "Ancient",
                "children": [{"col": 0, "row": 1}, {"col": 2, "row": 1}],
            }
        ],
        [
            {"col": 0, "row": 1, "type": "Monster", "children": [{"col": 0, "row": 2}]},
            {"col": 2, "row": 1, "type": "Event", "children": [{"col": 2, "row": 2}]},
        ],
        [
            {"col": 0, "row": 2, "type": "Shop", "children": []},
            {"col": 2, "row": 2, "type": "Treasure", "children": []},
        ],
    ]
}


class _MapRm(FakeRm):
    """A run parked on a map choice with the given legal ``move`` cols."""

    def __init__(self, coord: tuple[int, int] | None, move_cols: list[int]) -> None:
        super().__init__(
            phase="map_choice", actions=[{"action": "move", "col": c} for c in move_cols]
        )
        self._coord = coord

    def map_coord(self) -> tuple[int, int] | None:
        return self._coord

    def full_map(self) -> dict[str, Any]:
        return _FULL_MAP


def _choices(coord: tuple[int, int] | None, move_cols: list[int]) -> list[dict[str, Any]]:
    g = RustGame()
    g.rm = _MapRm(coord, move_cols)
    state = g.get_state()
    assert state["decision"] == "map_select"
    return state["choices"]


def test_choices_from_current_node_children() -> None:
    # Standing on (0, 1); its only child is (0, 2) Shop.
    assert _choices((0, 1), [0]) == [{"col": 0, "row": 2, "type": "Shop"}]


def test_missing_coord_falls_back_to_start_children() -> None:
    # No coord (e.g. just after an event): offer the start node's row-1
    # children with their real rows + types, not a mislabeled row.
    assert _choices(None, [0, 2]) == [
        {"col": 0, "row": 1, "type": "Monster"},
        {"col": 2, "row": 1, "type": "Event"},
    ]


def test_offered_col_absent_from_children_degrades_gracefully() -> None:
    # A col the engine offers but the current node has no edge to keeps the col
    # selectable (move is by col) with unknown row/type rather than crashing.
    assert _choices((0, 1), [7]) == [{"col": 7, "row": None, "type": None}]


class _BossRm:
    """RunManager stand-in whose ``full_map`` carries only a boss node."""

    def __init__(self, boss_kind: str, second_boss_id: str | None = None) -> None:
        self._boss_kind = boss_kind
        self._second_boss_id = second_boss_id

    def full_map(self) -> dict[str, Any]:
        # ``full_map()`` always carries the key; below A10 it is None.
        second = (
            {"col": 0, "row": 2, "type": "Boss", "id": self._second_boss_id}
            if self._second_boss_id is not None
            else None
        )
        return {"rows": [], "boss": {"kind": self._boss_kind}, "second_boss": second}


def _boss_name(kind: str) -> str:
    g = RustGame()
    g.rm = _BossRm(kind)  # type: ignore[assignment]
    return g.get_map()["boss"]["name"]


def _second_boss_name(encounter_id: str) -> str:
    g = RustGame()
    g.rm = _BossRm("ZzTestBoss", encounter_id)  # type: ignore[assignment]
    return g.get_map()["second_boss"]["name"]


def test_queen_boss_node_labeled_queen_not_amalgam() -> None:
    # The Queen boss fight spawns a Torch Head Amalgam alongside the Queen, and
    # the serializer picks the lead monster's kind for the boss node — but the
    # encounter is known as "Queen", so the map node is renamed.
    assert _boss_name("TorchHeadAmalgam") == "Queen"


def test_other_boss_kinds_use_monster_title() -> None:
    # Non-aliased bosses keep their monster title (spaced from the kind).
    assert _boss_name("ZzTestBoss") == "Zz Test Boss"


def test_a10_second_boss_named_from_its_encounter_id() -> None:
    # A10 DoubleBoss: the second boss node fights a DIFFERENT encounter, and
    # the engine identifies it by id only (no pinned lead-monster ``kind`` the
    # way the act boss has). The map has to label it, or both boss nodes wear
    # the act boss's name.
    assert _second_boss_name("QUEEN_BOSS") == "Queen"
    assert _second_boss_name("LAGAVULIN_MATRIARCH_BOSS") == "Lagavulin Matriarch"
    assert _second_boss_name("THE_KIN_BOSS") == "The Kin"


def test_second_boss_null_below_a10() -> None:
    # No second boss node below A10 — the key is there but empty, and naming
    # must not invent one (or trip over the None).
    g = RustGame()
    g.rm = _BossRm("ZzTestBoss")  # type: ignore[assignment]
    assert g.get_map()["second_boss"] is None
