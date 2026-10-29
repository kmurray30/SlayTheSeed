"""Combat potion use for the Rust backend.

The workbench board sends ``use_potion {potion_index, target_index?}`` when the
player drinks a potion. The adapter must translate that to the engine's
``drink_potion`` run-action (``potion_slot`` + optional ``target_index``); before
this mapping existed the verb fell through to the "unsupported" error and the
Use button was a no-op.
"""

from __future__ import annotations

import pytest

pytest.importorskip("sts2_rs", reason="rust backend requires the sts2_rs extension")

from fake_rm import FakeRm, stub_combat_view
from workbench.backends import rust_game as rg
from workbench.backends.rust_game import RustGame


@pytest.fixture
def game(monkeypatch) -> RustGame:
    monkeypatch.setattr(rg, "build_combat_view", stub_combat_view)
    g = RustGame()
    g.rm = FakeRm(phase="combat")  # no pending choice → ordinary play phase
    return g


def test_use_potion_translates_to_drink_potion(game: RustGame) -> None:
    game.act("use_potion", potion_index=1)
    assert game.rm.taken[-1] == {
        "action": "drink_potion",
        "potion_slot": 1,
        "target_index": None,
    }


def test_use_potion_passes_target_index(game: RustGame) -> None:
    game.act("use_potion", potion_index=0, target_index=2)
    assert game.rm.taken[-1] == {
        "action": "drink_potion",
        "potion_slot": 0,
        "target_index": 2,
    }
