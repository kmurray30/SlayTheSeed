"""The C#-headless pile DOUBLE-COUNT artifact carve-out.

``_is_csharp_pile_double_count`` marks a trial clean when C#'s snapshot counts
the SAME card instance in two piles at once (a card straddling
hand/draw/discard/exhaust mid-transfer), so C#'s total card count strictly
exceeds Rust's. A single instance cannot occupy two piles, so the C# snapshot is
internally impossible and Rust is correct — the spurious-victory philosophy on
the pile surface (Regent fuzz seed 306: SHINING_STRIKE's OnPlay returns it to
draw-top, the next draw moves it to hand, and C# counts it in BOTH hand and
draw — C# 17 cards / 2 SHINING_STRIKE vs Rust 16 / 1).

These pin that the fingerprint fires for the artifact and, critically, does NOT
fire for a real Rust card-LOSS or a real Rust over-count.
"""

from __future__ import annotations

import parity_audit as pa


def _piles(*, hand=(), draw=(), discard=(), exhaust=()) -> dict:
    return {
        "hand_ids": list(hand),
        "draw_ids": list(draw),
        "discard_ids": list(discard),
        "exhaust_ids": list(exhaust),
    }


def test_artifact_when_card_straddles_two_csharp_piles() -> None:
    # Seed 306 shape: SHINING_STRIKE counted in BOTH C# hand and C# draw-top,
    # while Rust holds the single real copy in hand. C# total exceeds Rust by 1.
    cs = _piles(
        hand=["RESTLESSNESS", "SHINING_STRIKE", "SPLASH"],
        draw=["SHINING_STRIKE", "RESONANCE", "FISTICUFFS"],
        discard=["COLLISION_COURSE"],
        exhaust=["DEBRIS", "DEBRIS"],
    )
    rs = _piles(
        hand=["RESTLESSNESS", "SHINING_STRIKE", "SPLASH"],
        draw=["RESONANCE", "FISTICUFFS"],
        discard=["COLLISION_COURSE"],
        exhaust=["DEBRIS", "DEBRIS"],
    )
    assert pa._is_csharp_pile_double_count(cs, rs)


def test_not_artifact_when_rust_dropped_a_card() -> None:
    # A real Rust card-LOSS: C# legitimately holds STRANGLE in exactly ONE pile
    # (its real home) and Rust is missing it. The surplus card occupies only one
    # C# pile, so the straddle clause fails and the divergence stays visible.
    cs = _piles(hand=["STRIKE", "STRANGLE"], draw=["DEFEND"])
    rs = _piles(hand=["STRIKE"], draw=["DEFEND"])
    assert not pa._is_csharp_pile_double_count(cs, rs)


def test_not_artifact_when_rust_over_counts() -> None:
    # Rust holds a card C# lacks → Rust is the surplus side (a real Rust
    # over-count / phantom dup). Must NOT be suppressed as a C# artifact.
    cs = _piles(hand=["STRIKE"], draw=["DEFEND"])
    rs = _piles(hand=["STRIKE", "STRIKE"], draw=["DEFEND"])
    assert not pa._is_csharp_pile_double_count(cs, rs)


def test_not_artifact_on_conserved_relocation() -> None:
    # A pure in-flight RELOCATION (conserved multiset — the card moved draw->hand
    # with no double-count) is the `_is_inflight_pile_move` carve-out's job, not
    # this one; equal totals mean no surplus, so this predicate stays out.
    cs = _piles(hand=["STRIKE"], draw=["DEFEND", "BASH"])
    rs = _piles(hand=["STRIKE", "DEFEND"], draw=["BASH"])
    assert not pa._is_csharp_pile_double_count(cs, rs)


def test_not_artifact_when_identical() -> None:
    cs = _piles(hand=["STRIKE", "DEFEND"], draw=["BASH"])
    rs = _piles(hand=["STRIKE", "DEFEND"], draw=["BASH"])
    assert not pa._is_csharp_pile_double_count(cs, rs)
