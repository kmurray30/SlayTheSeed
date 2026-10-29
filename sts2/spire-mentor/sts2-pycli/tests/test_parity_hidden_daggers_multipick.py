"""Regression guard for the HIDDEN_DAGGERS multi-pick discard divergence.

Bead ``sts2-rs-hd-multipick-discard-uhsk1``. HIDDEN_DAGGERS' OnPlay opens a
``DiscardFromHand`` choice with ``min == max == 2`` (discard exactly 2) when the
post-play hand holds more than 2 cards. The parity harness drives that choice
via the Rust ``PyCombatState`` ``choose`` binding, and the engine ALSO resolves
it inline (``resolve_inline_auto_play_choices``) during the card play.

Before the fix, both the bridge and the inline resolver collapsed the multi-pick
``selection=[0, 1]`` to a single index ``[0]`` — so Rust discarded only ONE card
while C# discarded two (Silent fuzz seeds 81/214). The fix carries the FULL
selection list through the ``legal_actions`` emit, the ``apply`` ``choose``
binding, ``resolve_inline_auto_play_choices``, and ``_drain_rust_choices``.

This test stacks the deck so the opening hand always holds >= 2 HIDDEN_DAGGERS
(only 3 non-HD cards exist, drawing 5), plays one HD, drains the choice exactly
as the harness does, and asserts EXACTLY 2 cards were discarded (+ HD itself).
"""

from __future__ import annotations

import sts2_rs
from parity_fuzz import _apply_rust, _drain_rust_choices, _rust_init
from parity_fuzz_scenario import Scenario


def _hand_ids(rs) -> list[str]:
    d = sts2_rs.state_to_dict(rs.inner)
    return [c.get("id") for c in (d.get("deck", {}).get("hand") or [])]


def _discard_ids(rs) -> list[str]:
    d = sts2_rs.state_to_dict(rs.inner)
    return [c.get("id") for c in (d.get("deck", {}).get("discard_pile") or [])]


def test_hidden_daggers_multipick_discards_exactly_two() -> None:
    # 5 HD + 3 DEFEND => any 5-card opening hand holds >= 2 HIDDEN_DAGGERS, so
    # after playing one HD the hand still has > 2 cards and the min=max=2
    # DiscardFromHand choice opens (not the <=2 auto-discard branch).
    deck = ("HIDDEN_DAGGERS",) * 5 + ("DEFEND_SILENT",) * 3
    sc = Scenario(
        character="Silent",
        deck=deck,
        hp=70,
        max_hp=70,
        relics=(),
        encounter="MAWLER_NORMAL",
        potions=(),
    )
    rs = _rust_init(sc, monsters=None)

    hand = _hand_ids(rs)
    assert hand.count("HIDDEN_DAGGERS") >= 2, hand
    hd_idx = hand.index("HIDDEN_DAGGERS")

    _apply_rust(rs, {"action": "play_card", "card_index": hd_idx, "target_index": None})
    # The inline resolver runs during the play; drain mirrors the harness for
    # any choice that survives (here the play already auto-resolved it).
    _drain_rust_choices(rs)

    discard = _discard_ids(rs)
    final_hand = _hand_ids(rs)

    # HIDDEN_DAGGERS itself flushes to discard after its OnPlay, plus exactly 2
    # chosen hand cards => 3 in the discard pile. The pre-fix bug discarded only
    # 1 chosen card (2 total), leaving an extra card stranded in hand.
    assert len(discard) == 3, f"expected HD + 2 discards, got {discard}"
    # Exactly 2 Shivs spawned into hand.
    assert final_hand.count("SHIV") == 2, final_hand
