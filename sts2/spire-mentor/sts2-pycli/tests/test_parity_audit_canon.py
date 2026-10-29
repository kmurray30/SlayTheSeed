"""Card-id canonicalization for cross-engine comparison.

C# reports a card's hand/draw/discard id as ``CARD.BARRICADE`` (bare model
id), while Rust reports the disambiguated ``BARRICADE_CARD``. Both must
canonicalize to the same token or every ``_CARD`` card produces a spurious
hand_ids / draw_ids divergence once the harness can actually load it.
"""

from __future__ import annotations

import parity_audit as pa


def test_strips_csharp_prefix() -> None:
    assert pa._canon_card("CARD.BARRICADE") == "BARRICADE"
    assert pa._canon_card("CARD.STRIKE_IRONCLAD") == "STRIKE_IRONCLAD"


def test_strips_rust_card_suffix() -> None:
    assert pa._canon_card("BARRICADE_CARD") == "BARRICADE"
    assert pa._canon_card("RUPTURE_CARD") == "RUPTURE"


def test_both_engine_forms_agree() -> None:
    # the whole point: C# form and Rust form collapse to one token
    assert pa._canon_card("CARD.BARRICADE") == pa._canon_card("BARRICADE_CARD")
    assert pa._canon_card("CARD.THE_BOMB") == pa._canon_card("THE_BOMB_CARD")


def test_strips_rust_status_suffix() -> None:
    # `_STATUS` is the same split with one member: C# `Sloth` / `CARD.SLOTH`
    # vs Rust `SLOTH_STATUS`. Latent (no scenario lists SLOTH today), but a
    # Knowledge Demon board would otherwise diverge on pile ordering alone.
    assert pa._canon_card("SLOTH_STATUS") == "SLOTH"
    assert pa._canon_card("CARD.SLOTH") == pa._canon_card("SLOTH_STATUS")


def test_at_most_one_suffix_comes_off() -> None:
    # A registry id carries exactly one of the two, so a hypothetical id
    # ending in both must lose only the trailing one.
    assert pa._canon_card("FOO_STATUS_CARD") == "FOO_STATUS"


def test_passthrough_and_none() -> None:
    assert pa._canon_card("WHIRLWIND") == "WHIRLWIND"
    assert pa._canon_card(None) is None
