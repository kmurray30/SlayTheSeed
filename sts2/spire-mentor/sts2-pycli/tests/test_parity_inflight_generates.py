"""The mid-play generated-token carve-out for ``_is_inflight_play_pile``.

When C#'s headless snapshot lands MID-PLAY, the just-played card is still in
``play_ids`` and its queued ``OnPlay`` generation has not drained, while Rust
(synchronous) has already spawned the generated card. Strict pile conservation
therefore breaks: Rust holds one extra token. ``_INFLIGHT_GENERATES`` whitelists
the SPECIFIC status token each generator mints (grounded in the C# decomp:
``OnPlay`` → ``AddGeneratedCardToCombat(CreateCard<X>)``), so the surplus is
provably the in-flight card's own unflushed generation — not a dup/loss.

These pin that the fingerprint (a) fires for the in-flight token surplus
(Regent fuzz seed 266: CollisionCourse → Debris) and (b) does NOT fire when the
surplus is an arbitrary card Rust gained — a real divergence that must stay
visible.
"""

from __future__ import annotations

from typing import Any

import parity_audit as pa


class _FakeRust:
    """Minimal stand-in for the Rust adapter ``_is_inflight_play_pile`` reads.

    The predicate calls ``sts2_rs.state_to_dict(rs_state.inner)["deck"]``; we
    monkeypatch ``state_to_dict`` to return a controlled deck, so ``.inner`` is
    just an opaque marker.
    """

    inner = object()


def _deck(*, hand=(), draw=(), discard=(), exhaust=()) -> dict:
    def pile(ids):
        return [{"id": i} for i in ids]

    return {
        "deck": {
            "hand": pile(hand),
            "draw_pile": pile(draw),
            "discard_pile": pile(discard),
            "exhaust_pile": pile(exhaust),
        }
    }


def _patch_rust_deck(monkeypatch: Any, deck: dict) -> _FakeRust:
    monkeypatch.setattr(pa.sts2_rs, "state_to_dict", lambda _inner: deck)
    return _FakeRust()


def test_collision_course_debris_surplus_is_inflight(monkeypatch: Any) -> None:
    # C# snapshot is mid-play: COLLISION_COURSE still in play_ids, Debris not yet
    # generated into Rust-parity piles on the C# side.
    cs_raw = {
        "play_ids": ["CARD.COLLISION_COURSE"],
        "hand_ids": ["STRIKE", "DEFEND"],
        "draw_ids": [],
        "discard_ids": [],
        "exhaust_ids": [],
    }
    # Rust resolved synchronously: COLLISION_COURSE in discard + the minted DEBRIS.
    rs = _patch_rust_deck(
        monkeypatch,
        _deck(hand=["STRIKE", "DEFEND"], discard=["COLLISION_COURSE", "DEBRIS"]),
    )
    assert pa._is_inflight_play_pile(cs_raw, rs) is True


def test_arbitrary_surplus_card_is_not_inflight(monkeypatch: Any) -> None:
    # Same in-flight card, but Rust's surplus is an unrelated card (STRIKE), NOT
    # the token COLLISION_COURSE mints — a genuine divergence that must stay loud.
    cs_raw = {
        "play_ids": ["CARD.COLLISION_COURSE"],
        "hand_ids": ["DEFEND"],
        "draw_ids": [],
        "discard_ids": [],
        "exhaust_ids": [],
    }
    rs = _patch_rust_deck(
        monkeypatch,
        _deck(hand=["DEFEND", "STRIKE"], discard=["COLLISION_COURSE"]),
    )
    assert pa._is_inflight_play_pile(cs_raw, rs) is False


def test_strict_conservation_still_clean(monkeypatch: Any) -> None:
    # No generation: in-flight card simply not yet moved to discard. Bags are
    # equal once play_ids is folded in → clean via the strict-conservation path.
    cs_raw = {
        "play_ids": ["CARD.STRIKE"],
        "hand_ids": ["DEFEND"],
        "draw_ids": [],
        "discard_ids": [],
        "exhaust_ids": [],
    }
    rs = _patch_rust_deck(monkeypatch, _deck(hand=["DEFEND"], discard=["STRIKE"]))
    assert pa._is_inflight_play_pile(cs_raw, rs) is True


def test_empty_play_pile_is_never_inflight(monkeypatch: Any) -> None:
    # A SETTLED C# snapshot has an empty play pile → predicate must bail out.
    cs_raw = {
        "play_ids": [],
        "hand_ids": ["STRIKE"],
        "draw_ids": [],
        "discard_ids": [],
        "exhaust_ids": [],
    }
    rs = _patch_rust_deck(monkeypatch, _deck(hand=["STRIKE", "DEBRIS"]))
    assert pa._is_inflight_play_pile(cs_raw, rs) is False
