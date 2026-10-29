"""Target-index convention for the Rust workbench backend.

The board (``build_combat_view``) hands the frontend each enemy's position in
the FULL encounter array (corpses included) as its ``index``; the frontend
echoes that back as ``target_index``. So the interactive/native path must pass
that index straight through to the engine.

Only the cross-engine parity replay (``run_parity`` & friends) drives off the
C# oracle, whose ``target_index`` is over the ALIVE subset — that path opts
into the alive→full remap via ``target_convention="csharp"``.

Regression: in a slime fight, once an earlier enemy is a corpse, the native
path used to remap a full-array index as if it were alive-subset, shifting the
hit one enemy later — "play Strike on Twig Slime (M)" landed on Leaf Slime (S).
"""

from __future__ import annotations

import pytest

pytest.importorskip("sts2_rs", reason="rust backend requires the sts2_rs extension")

import sts2_rs
from fake_rm import FakeCombatState, FakeRm, stub_combat_view
from workbench.backends import rust_game as rg
from workbench.backends.rust_game import RustGame

# encounter[0] is a corpse; the two live enemies sit at full-array slots 1 and 2
# (this is the slime-fight shape: Twig Slime (S) dead, Twig Slime (M) @1,
# Leaf Slime (S) @2).
_SLOT0_DEAD = {"encounter": [{"is_dead": True}, {"is_dead": False}, {"is_dead": False}]}


class _FakeRm(FakeRm):
    def __init__(self):
        super().__init__(phase="combat")

    def combat_state(self):  # non-None; ``state_to_dict`` is monkeypatched
        return FakeCombatState()


@pytest.fixture
def _patched_state(monkeypatch):
    monkeypatch.setattr(sts2_rs, "state_to_dict", lambda cs: _SLOT0_DEAD)
    monkeypatch.setattr(rg, "build_combat_view", stub_combat_view)


def test_play_card_target_index_native_passthrough(_patched_state):
    # Workbench convention: ``target_index`` is already the full-array slot.
    # With slot 0 a corpse, the board labels the middle live enemy index 1 —
    # the engine must receive 1, not a remapped 2 (the slime mis-target bug).
    g = RustGame()
    g.rm = _FakeRm()
    g.act("play_card", card_index=0, target_index=1)
    assert g.rm.taken[-1]["target_index"] == 1


def test_use_potion_target_index_native_passthrough(_patched_state):
    g = RustGame()
    g.rm = _FakeRm()
    g.act("use_potion", potion_index=0, target_index=1)
    assert g.rm.taken[-1]["target_index"] == 1


def test_play_card_target_index_csharp_remap(_patched_state):
    # Parity convention: ``target_index`` is over the ALIVE subset (C# oracle).
    # alive = [1, 2]; alive-index 1 → full slot 2.
    g = RustGame(target_convention="csharp")
    g.rm = _FakeRm()
    g.act("play_card", card_index=0, target_index=1)
    assert g.rm.taken[-1]["target_index"] == 2
