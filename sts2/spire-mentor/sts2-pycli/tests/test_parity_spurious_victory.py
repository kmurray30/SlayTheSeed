"""The C#-headless spurious-terminal artifact carve-out.

``_is_csharp_spurious_victory`` marks a trial clean when C# reports
``player_won`` while its OWN monster roster still holds a living enemy (hp>0)
and Rust keeps combat in progress. A real victory requires every enemy dead, so
C# claiming the player won with a hp>0 monster is internally impossible — the C#
headless mis-set the terminal flag and Rust is correct (Ironclad fuzz seeds
57/76/116/295/297/311: C# combat_terminal/player_won True with 3 monsters alive
at 47/42/86 hp). These pin that the fingerprint fires for the artifact and does
NOT fire for a genuine shared victory or a real mid-combat divergence.
"""

from __future__ import annotations

import parity_audit as pa


def _cs(*, won, monsters, terminal=True) -> dict:
    return {"player_won": won, "combat_terminal": terminal, "monsters": monsters}


def _rs(*, terminal, monsters=None) -> dict:
    return {"player_won": None, "combat_terminal": terminal, "monsters": monsters or []}


def test_artifact_when_csharp_won_with_living_enemy_and_rust_continues() -> None:
    cs = _cs(won=True, monsters=[{"hp": 47}, {"hp": 42}, {"hp": 86}])
    rs = _rs(terminal=False, monsters=[{"hp": 47}, {"hp": 42}, {"hp": 86}])
    assert pa._is_csharp_spurious_victory(cs, rs)


def test_not_artifact_when_csharp_won_with_all_enemies_dead() -> None:
    # A genuine C# victory (every enemy at hp 0) must NOT be suppressed — if Rust
    # disagrees there, that is a real win-detection divergence.
    cs = _cs(won=True, monsters=[{"hp": 0}, {"hp": 0}, {"hp": 0}])
    rs = _rs(terminal=False, monsters=[{"hp": 5}, {"hp": 0}, {"hp": 0}])
    assert not pa._is_csharp_spurious_victory(cs, rs)


def test_not_artifact_when_rust_also_terminal() -> None:
    # Both engines ended combat — the victory-teardown / terminal-skew carve-outs
    # own that case; this one must stay out of it.
    cs = _cs(won=True, monsters=[{"hp": 30}])
    rs = _rs(terminal=True, monsters=[{"hp": 0}])
    assert not pa._is_csharp_spurious_victory(cs, rs)


def test_not_artifact_when_csharp_not_won() -> None:
    cs = _cs(won=None, monsters=[{"hp": 47}], terminal=False)
    rs = _rs(terminal=False, monsters=[{"hp": 47}])
    assert not pa._is_csharp_spurious_victory(cs, rs)
