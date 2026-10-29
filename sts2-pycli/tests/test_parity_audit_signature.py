"""Unit tests for the audit bucket-signature helper.

Pure-Python — no engine. Verifies that coverage trials bucket under their
``coverage_subject`` label (so a divergence is attributed to the one varied
element regardless of which action tripped it), while random trials keep the
legacy action/card-attributed signature.
"""

from __future__ import annotations

import parity_audit as pa


def test_clean_is_clean_regardless_of_subject() -> None:
    assert pa._trial_signature({"category": "clean"}) == "clean"
    assert (
        pa._trial_signature({"category": "clean", "coverage_subject": "card:WHIRLWIND"}) == "clean"
    )


def test_coverage_subject_labels_the_bucket() -> None:
    # even though the action that tripped the diff was end_turn, the bucket
    # is labelled by the varied element
    r = {
        "category": "PLAYER_HP",
        "action": {"action": "end_turn"},
        "coverage_subject": "relic:AKABEKO",
    }
    assert pa._trial_signature(r) == "PLAYER_HP|relic:AKABEKO"


def test_coverage_subject_overrides_card_attribution() -> None:
    r = {
        "category": "MONSTER_STATE",
        "action": {"action": "play_card", "_card_id": "STRIKE_IRONCLAD"},
        "coverage_subject": "relic:PEN_NIB",
    }
    assert pa._trial_signature(r) == "MONSTER_STATE|relic:PEN_NIB"


def test_legacy_signature_when_no_subject() -> None:
    r = {
        "category": "PLAYER_STATE",
        "action": {"action": "play_card", "_card_id": "INFLAME"},
        "coverage_subject": None,
    }
    assert pa._trial_signature(r) == "PLAYER_STATE|INFLAME"


def test_legacy_signature_falls_back_to_action_name() -> None:
    r = {"category": "PLAYER_HP", "action": {"action": "end_turn"}}
    assert pa._trial_signature(r) == "PLAYER_HP|end_turn"
