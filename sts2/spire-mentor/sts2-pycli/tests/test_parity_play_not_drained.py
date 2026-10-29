"""The C#-headless async play-drain artifact carve-out.

``_is_csharp_play_not_drained`` marks a trial clean when C# accepted a
``play_card`` it offered as legal but executed NOTHING (its turn signature is
byte-identical before/after) while Rust resolved the play. C#'s
``WaitForActionExecutor`` can return before a played card's async ``OnPlay``
drains, so ``DoPlayCard`` hits its "still in hand after action" guard and the
harness records the frozen pre-play state — a C# artifact, not a Rust bug
(Ironclad fuzz seed 265: EQUILIBRIUM). These pin that the fingerprint fires for
the artifact and, crucially, does NOT fire when C# actually advanced (a real
divergence that must stay visible).
"""

from __future__ import annotations

import parity_audit as pa


def _state(hand_ids, *, hp=50, block=0, energy=3, monsters=None) -> dict:
    return {
        "player": {"hp": hp, "max_hp": 50, "block": block, "energy": energy, "powers": []},
        "monsters": monsters or [],
        "hand_ids": list(hand_ids),
        "draw_ids": [],
        "discard_ids": [],
    }


_PLAY = {"action": "play_card", "card_index": 1, "target_index": None, "_card_id": "EQUILIBRIUM"}


def test_artifact_detected_when_csharp_frozen_and_rust_advanced() -> None:
    # Pre-action: hands match, energy 3, block 0.
    cs_pre = _state(["PILLAGE", "EQUILIBRIUM", "POMMEL_STRIKE"])
    rs_pre = _state(["PILLAGE", "EQUILIBRIUM", "POMMEL_STRIKE"])
    cs_pre_sig = pa._cs_turn_sig(cs_pre)
    rs_pre_sig = pa._cs_turn_sig(rs_pre)
    # Post: C# is BYTE-IDENTICAL (play never executed); Rust resolved it
    # (EQUILIBRIUM left hand → block 13, energy 1).
    cs_post = _state(["PILLAGE", "EQUILIBRIUM", "POMMEL_STRIKE"])
    rs_post = _state(["PILLAGE", "POMMEL_STRIKE"], block=13, energy=1)
    assert pa._is_csharp_play_not_drained(_PLAY, cs_post, rs_post, cs_pre_sig, rs_pre_sig)


def test_not_an_artifact_when_csharp_advanced() -> None:
    # A real cascade: C# DID change (it resolved a turn / took damage). The
    # fingerprint must NOT fire — the divergence stays visible.
    cs_pre = _state(["PILLAGE", "EQUILIBRIUM"], hp=50)
    rs_pre = _state(["PILLAGE", "EQUILIBRIUM"], hp=50)
    cs_pre_sig = pa._cs_turn_sig(cs_pre)
    rs_pre_sig = pa._cs_turn_sig(rs_pre)
    cs_post = _state(["PILLAGE"], hp=22, block=13, energy=1)  # C# advanced too
    rs_post = _state(["PILLAGE"], hp=45, block=13, energy=1)  # but differs from Rust
    assert not pa._is_csharp_play_not_drained(_PLAY, cs_post, rs_post, cs_pre_sig, rs_pre_sig)


def test_not_an_artifact_for_non_play_card() -> None:
    cs_pre = _state(["PILLAGE", "EQUILIBRIUM"])
    rs_pre = _state(["PILLAGE", "EQUILIBRIUM"])
    cs_pre_sig = pa._cs_turn_sig(cs_pre)
    rs_pre_sig = pa._cs_turn_sig(rs_pre)
    cs_post = _state(["PILLAGE", "EQUILIBRIUM"])
    rs_post = _state(["PILLAGE", "EQUILIBRIUM"], block=5)
    end_turn = {"action": "end_turn"}
    assert not pa._is_csharp_play_not_drained(end_turn, cs_post, rs_post, cs_pre_sig, rs_pre_sig)


def test_not_an_artifact_when_rust_also_frozen() -> None:
    # Both engines unchanged (no real divergence to suppress) → False, so the
    # carve-out never claims a clean win where nothing happened.
    cs_pre = _state(["PILLAGE", "EQUILIBRIUM"])
    rs_pre = _state(["PILLAGE", "EQUILIBRIUM"])
    cs_pre_sig = pa._cs_turn_sig(cs_pre)
    rs_pre_sig = pa._cs_turn_sig(rs_pre)
    cs_post = _state(["PILLAGE", "EQUILIBRIUM"])
    rs_post = _state(["PILLAGE", "EQUILIBRIUM"])
    assert not pa._is_csharp_play_not_drained(_PLAY, cs_post, rs_post, cs_pre_sig, rs_pre_sig)
