"""Regression: the Defect orb belt must survive the live combat view.

``build_combat_view`` emits ``orbs`` / ``orb_slots`` for a Defect belt, but the
live/replay backend copies only a fixed key list from that
view onto the state it hands the frontend. That list never listed the orb keys,
so the whole belt was dropped and the Defect board rendered no orbs — even
though the offline recorder (which uses the view directly) showed them fine.
This pins the copy so the belt reaches the frontend in both the plain-combat
and the in-combat-choice (card_select) paths.
"""

import workbench.backends.rust_game as rust_game
from fake_rm import FakeCombatState, FakeRm

_ORBS = [
    {"index": 0, "name": "Lightning", "type": "Lightning", "passive": 3, "evoke": 8},
    {"index": 1, "name": "Frost", "type": "Frost", "passive": 2, "evoke": 5},
]
_ORB_SLOTS = 3


def _view_with_orbs(cs, character, legal):
    return {
        "player": {"hp": 68, "max_hp": 75, "block": 0},
        "enemies": [],
        "player_powers": [],
        "round": 1,
        "energy": 3,
        "max_energy": 3,
        "hand": [],
        "draw_pile_count": 0,
        "discard_pile_count": 0,
        "exhaust_pile_count": 0,
        "draw_pile": [],
        "discard_pile": [],
        "exhaust_pile": [],
        "orbs": _ORBS,
        "orb_slots": _ORB_SLOTS,
    }


class _FakeRm(FakeRm):
    def __init__(self, pending=None):
        super().__init__(phase="combat")
        self._pending = pending

    def combat_state(self):
        return FakeCombatState(self._pending)


def _fill(monkeypatch, pending=None):
    monkeypatch.setattr(rust_game, "build_combat_view", _view_with_orbs)
    g = rust_game.RustGame()
    g.rm = _FakeRm(pending)
    g.character = "Defect"
    return g.get_state()


def test_orb_belt_reaches_the_state_in_plain_combat(monkeypatch):
    state = _fill(monkeypatch)
    assert state["orbs"] == _ORBS
    assert state["orb_slots"] == _ORB_SLOTS


def test_orb_belt_survives_an_in_combat_choice(monkeypatch):
    # An in-combat card_select (e.g. a channelled-orb-driven pick) must not drop
    # the belt: the Defect still has its orbs while the choice is open.
    pending = {"cards": [{"id": "ZAP", "upgraded": False}], "min_select": 1, "max_select": 1}
    state = _fill(monkeypatch, pending=pending)
    assert state["decision"] == "card_select"
    assert state["orbs"] == _ORBS
    assert state["orb_slots"] == _ORB_SLOTS


def test_non_defect_combat_has_no_orb_keys(monkeypatch):
    # ``build_combat_view`` omits the keys for a character with no belt; the copy
    # must stay a no-op there (no empty belt leaking onto e.g. the Ironclad).
    monkeypatch.setattr(
        rust_game,
        "build_combat_view",
        lambda cs, character, legal: {
            "player": {"hp": 80, "max_hp": 80, "block": 0},
            "enemies": [],
            "player_powers": [],
            "round": 1,
            "energy": 3,
            "max_energy": 3,
            "hand": [],
            "draw_pile_count": 0,
            "discard_pile_count": 0,
            "exhaust_pile_count": 0,
            "draw_pile": [],
            "discard_pile": [],
            "exhaust_pile": [],
        },
    )
    g = rust_game.RustGame()
    g.rm = _FakeRm()
    g.character = "Ironclad"
    state = g.get_state()
    assert state["decision"] == "combat_play"
    assert "orbs" not in state
    assert "orb_slots" not in state
