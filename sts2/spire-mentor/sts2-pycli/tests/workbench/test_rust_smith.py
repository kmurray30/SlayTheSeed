"""Unit tests for the Rust-backend Smith rest-site rendering.

The engine's Smith pending-choice is exercised by the Rust test suite; here we
test the adapter contract in isolation with a minimal fake ``rm`` so the run
needn't be driven all the way to a rest site.
"""

from __future__ import annotations

import pytest

pytest.importorskip("sts2_rs", reason="rust backend requires the sts2_rs extension")

from fake_rm import FakeRm
from workbench.backends.rust_game import RustGame


class _FakeRm(FakeRm):
    """A rest site in its Smith sub-state."""

    def __init__(self, *, phase: str, actions: list[dict], smith_ids: list[str]):
        super().__init__(phase=phase, actions=actions)
        self._smith_ids = smith_ids
        self.pending_deck_removals = 0

    def smith_options(self) -> list[str]:
        return self._smith_ids


def _smith_game() -> RustGame:
    g = RustGame()
    g.rm = _FakeRm(
        phase="rest_site",
        actions=[
            {"action": "smith_card", "idx": 0},
            {"action": "smith_card", "idx": 1},
        ],
        smith_ids=["StrikeIronclad", "Anger"],
    )
    return g


def test_smith_renders_as_card_select_with_names() -> None:
    g = _smith_game()
    state = g.get_state()
    assert state["decision"] == "card_select"
    assert state["context"] == "upgrade"
    assert state["min_select"] == 1 and state["can_skip"] is False
    cards = state["cards"]
    assert [c["index"] for c in cards] == [0, 1]
    # ids/loc keys are resolvable card identifiers, not "Upgrade card N".
    assert cards[0]["id"] == "STRIKE_IRONCLAD"
    assert cards[0]["name"] == "STRIKE_IRONCLAD.title"
    assert cards[1]["id"] == "ANGER"
    assert cards[1]["description"] == "ANGER.description"


def test_select_cards_routes_to_smith_card_in_rest_sub_state() -> None:
    g = _smith_game()
    g.act("select_cards", indices="1")
    assert g.rm.taken[-1] == {"action": "smith_card", "idx": 1}


def test_select_cards_still_routes_to_removal_when_pending() -> None:
    g = _smith_game()
    g.rm.pending_deck_removals = 1
    g.act("select_cards", indices="2")
    assert g.rm.taken[-1] == {"action": "remove_deck_card", "idx": 2}


def _upgrade_game(pending: int) -> RustGame:
    g = _smith_game()
    g.rm.pending_deck_upgrades = pending
    # The fake never drains the queue, so the re-render needs the upgrade
    # select's accessors.
    g.rm.master_deck_detailed = lambda: []
    g.rm.upgrade_select_deck_indices = lambda: []
    return g


def test_multi_index_deck_upgrade_fans_out_descending() -> None:
    """YummyCookie's upgrade-4 is one C# ``select_cards`` with every index;
    each is a position in the shrinking upgradable subset, so the backend
    applies them highest first."""
    g = _upgrade_game(4)
    g.act("select_cards", indices="0,1,4,15")
    assert g.rm.taken[-4:] == [
        {"action": "upgrade_deck_card", "idx": 15},
        {"action": "upgrade_deck_card", "idx": 4},
        {"action": "upgrade_deck_card", "idx": 1},
        {"action": "upgrade_deck_card", "idx": 0},
    ]


def test_single_index_deck_upgrade_is_one_pick() -> None:
    g = _upgrade_game(1)
    g.act("select_cards", indices="3")
    assert g.rm.taken[-1] == {"action": "upgrade_deck_card", "idx": 3}
