"""In-combat pending-choice rendering/routing for the Rust backend.

A card like Headbutt opens an in-combat choice ("put a discard card on top of
your draw pile"); the engine then only offers ``combat_resolve_choice`` actions.
The adapter must surface that as a card_select and route the pick back, or the
board soft-locks (empty/unplayable hand + a useless End-turn button).
"""

from __future__ import annotations

import pytest

pytest.importorskip("sts2_rs", reason="rust backend requires the sts2_rs extension")

from fake_rm import FakeCombatState, FakeRm, stub_combat_view
from workbench.backends import rust_game as rg
from workbench.backends.rust_game import RustGame


class _FakeRm(FakeRm):
    """A combat with an open pending choice ``pc``."""

    def __init__(self, pc: dict | None):
        super().__init__(phase="combat")
        self._cs = FakeCombatState(pc)

    def combat_state(self):
        return self._cs


_PC = {
    "type": "card_select",
    "kind": "put_back",  # engine emits snake_case (choice_kind_python_name)
    "cards": [
        {"id": "STRIKE_IRONCLAD", "upgraded": False},
        {"id": "DEFEND_IRONCLAD", "upgraded": False},
    ],
    "min_select": 1,
    "max_select": 1,
    "can_skip": False,
}


def test_combat_choice_renders_as_card_select(monkeypatch: pytest.MonkeyPatch) -> None:
    # The combat view needs a NATIVE sts2_rs combat state (live-HP copy, bead
    # sts2-rs-oac); stub it — this exercises the pending-choice rendering.
    monkeypatch.setattr(rg, "build_combat_view", stub_combat_view)
    g = RustGame()
    g.rm = _FakeRm(_PC)
    state = g.get_state()
    assert state["decision"] == "card_select"
    assert state["context"] == "put_back"
    assert state["min_select"] == 1 and state["can_skip"] is False
    assert [c["index"] for c in state["cards"]] == [0, 1]
    assert state["cards"][0]["id"] == "STRIKE_IRONCLAD"
    assert state["cards"][0]["name"] == "STRIKE_IRONCLAD.title"


def test_select_cards_routes_to_combat_resolve_choice(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(rg, "build_combat_view", stub_combat_view)
    g = RustGame()
    g.rm = _FakeRm(_PC)
    g.act("select_cards", indices="1")
    assert g.rm.taken[-1] == {"action": "combat_resolve_choice", "option": 1}


def test_skip_select_routes_to_combat_resolve_choice_skip(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(rg, "build_combat_view", stub_combat_view)
    g = RustGame()
    g.rm = _FakeRm({**_PC, "can_skip": True})
    g.act("skip_select")
    assert g.rm.taken[-1] == {"action": "combat_resolve_choice", "option": None}
