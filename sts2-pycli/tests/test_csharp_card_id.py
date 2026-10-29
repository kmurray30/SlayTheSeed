"""Unit test for the C#-side card-id normalizer.

The Rust content crate disambiguates a card from a same-named power by
appending ``_CARD`` to the card's canonical id (``BARRICADE`` the power vs
``BARRICADE_CARD`` the card). The C# engine namespaces by ``CARD.`` instead,
so its model id is the bare name. When the harness feeds a deck to the C#
``set_player`` it must strip that Rust-only suffix or C# raises
``ModelNotFoundException: Model id=CARD.BARRICADE_CARD not found``.
"""

from __future__ import annotations

import parity_fuzz as pf


def test_strips_card_suffix() -> None:
    assert pf._csharp_card_id("BARRICADE_CARD") == "BARRICADE"
    assert pf._csharp_card_id("THE_BOMB_CARD") == "THE_BOMB"
    assert pf._csharp_card_id("RUPTURE_CARD") == "RUPTURE"


def test_leaves_unsuffixed_ids_untouched() -> None:
    assert pf._csharp_card_id("STRIKE_IRONCLAD") == "STRIKE_IRONCLAD"
    assert pf._csharp_card_id("GANG_UP") == "GANG_UP"
    assert pf._csharp_card_id("BASH") == "BASH"


def test_only_trailing_suffix_stripped() -> None:
    # a hypothetical id that merely contains the token mid-string is untouched
    assert pf._csharp_card_id("CARD_HOLDER") == "CARD_HOLDER"


def test_upgrade_suffix_passes_through() -> None:
    assert pf._csharp_card_id("BASH+1") == "BASH+1"


def test_upgrade_suffix_after_card_suffix_strip() -> None:
    # The _CARD strip must apply to the BASE name even when a +N marker
    # follows it — naive removesuffix would miss it.
    assert pf._csharp_card_id("BARRICADE_CARD+1") == "BARRICADE+1"


def test_strips_status_suffix() -> None:
    # Same split, one member: Rust `SLOTH_STATUS`, C# `Sloth`. Without the
    # strip the send takes the identical `ModelNotFoundException` ->
    # HARNESS_INIT masking path the `_CARD` strip exists to avoid.
    assert pf._csharp_card_id("SLOTH_STATUS") == "SLOTH"
    assert pf._csharp_card_id("SLOTH_STATUS+1") == "SLOTH+1"


def test_at_most_one_suffix_stripped() -> None:
    # A registry id carries exactly one; stripping both would fabricate a
    # model id C# has never heard of.
    assert pf._csharp_card_id("FOO_STATUS_CARD") == "FOO_STATUS"


def test_no_suffix_unchanged() -> None:
    assert pf._csharp_card_id("BASH") == "BASH"
    assert pf._csharp_card_id("BARRICADE_CARD") == "BARRICADE"
