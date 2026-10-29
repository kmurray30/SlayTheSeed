"""PPO event-option payload featurization (GH #237).

The encoder prefers the engine's STRUCTURED effect deltas
(``gold_delta`` / ``hp_delta`` / ``max_hp_delta``) over regex-scraping the
English description, falling back to the parse only when the obs omits them.
These tests are pure-Python (no game engine / wheel): they pin the
``structured_event_payload`` semantics and the ``_build_action_token``
event-choice branch on hand-built obs dicts. The engine-backed
parsed-vs-structured parity sweep lives in
``test_ppo_event_payload_parity.py`` (gated on ``sts2_rs``).
"""

from __future__ import annotations

import sys
from pathlib import Path

PY = str(Path(__file__).resolve().parents[1] / "python")
if PY not in sys.path:
    sys.path.insert(0, PY)

import pytest  # noqa: E402
from ppo.encoder import (  # noqa: E402
    _build_action_token,
    _Ctx,
    event_payload,
    structured_event_payload,
)
from ppo.model import C_GOLD_D, C_HP_D, GOLD_SCALE, SEC_EVENT_OPT  # noqa: E402


def _event_token(options: list[dict], idx: int, max_hp: int = 80):
    """Featurize event_choice `idx` over an event surface with `options`."""
    obs = {"max_hp": max_hp, "event_name": "TrashHeap", "event_options": options}
    actions = [{"action": "event_choice", "idx": i} for i in range(len(options))]
    ctx = _Ctx(obs, actions)
    return _build_action_token(ctx, actions[idx])


# ── structured_event_payload semantics ───────────────────────────────────


def test_structured_payload_none_without_all_three_deltas():
    # Pre-#237 obs (no structured keys) -> None -> caller uses regex.
    assert structured_event_payload({}, 80) is None
    assert structured_event_payload({"gold_delta": 5}, 80) is None
    assert structured_event_payload({"gold_delta": 5, "hp_delta": 0}, 80) is None


def test_structured_payload_gold_and_hp_frac():
    g, h = structured_event_payload({"gold_delta": -50, "hp_delta": -5, "max_hp_delta": 0}, 80)
    assert g == pytest.approx(-50.0)
    assert h == pytest.approx(-5.0 / 80.0)


def test_structured_payload_max_hp_gain_counted_once():
    # GainMaxHp bumps current HP too (engine contract): hp_delta already
    # carries it, so don't double-count max_hp_delta.
    _g, h = structured_event_payload({"gold_delta": 0, "hp_delta": 8, "max_hp_delta": 8}, 80)
    assert h == pytest.approx(8.0 / 80.0)


def test_structured_payload_max_hp_loss_uses_ceiling_drop():
    # LoseMaxHp with current HP below the new ceiling: hp_delta 0 but the
    # ceiling dropped -10 -> reflect the -10.
    _g, h = structured_event_payload({"gold_delta": 0, "hp_delta": 0, "max_hp_delta": -10}, 80)
    assert h == pytest.approx(-10.0 / 80.0)


def test_structured_payload_clamps_to_unit_interval():
    _g, h = structured_event_payload({"gold_delta": 0, "hp_delta": -999, "max_hp_delta": 0}, 80)
    assert h == pytest.approx(-1.0)


# ── _build_action_token event-choice branch ──────────────────────────────


def test_token_uses_structured_deltas():
    opts = [
        {
            "option_id": "dive_in",
            "label": "Dive in",
            "description": "irrelevant",
            "enabled": True,
            "gold_delta": 0,
            "hp_delta": -8,
            "max_hp_delta": 0,
        },
        {
            "option_id": "grab",
            "label": "Grab",
            "description": "irrelevant",
            "enabled": True,
            "gold_delta": 100,
            "hp_delta": 0,
            "max_hp_delta": 0,
        },
    ]
    sec, _cat, cont, _aux = _event_token(opts, 1)
    assert sec == SEC_EVENT_OPT
    assert cont[C_GOLD_D] == pytest.approx(100 * GOLD_SCALE)
    assert cont[C_HP_D] == pytest.approx(0.0)
    _s, _c, cont0, _a = _event_token(opts, 0)
    assert cont0[C_HP_D] == pytest.approx(-8.0 / 80.0)
    assert cont0[C_GOLD_D] == pytest.approx(0.0)


def test_costly_option_does_not_featurize_to_zero():
    """Regression (GH #237 test plan): a known-costly option must NOT
    featurize to a zero payload. The description phrasing here is one the
    regex misses ("It'll cost ya"), so ONLY the structured path saves it."""
    opts = [
        {
            "option_id": "toll",
            "label": "Pay the toll",
            "description": "It'll cost ya 15 gold.",
            "enabled": True,
            "gold_delta": -15,
            "hp_delta": 0,
            "max_hp_delta": 0,
        },
    ]
    _sec, _cat, cont, _aux = _event_token(opts, 0)
    assert cont[C_GOLD_D] == pytest.approx(-15 * GOLD_SCALE)
    assert abs(cont[C_GOLD_D]) > 0.0  # NOT a silent zero
    # The regex alone would have gone dark on this phrasing -> silent zero.
    assert event_payload("Pay the toll. It'll cost ya 15 gold.", 80) == pytest.approx((0.0, 0.0))


def test_token_falls_back_to_regex_without_structured_keys():
    # An option lacking the structured keys (C# backend / old wheel) must
    # reproduce the legacy regex-derived payload exactly.
    opts = [
        {"option_id": "grab", "label": "Grab", "description": "Gain 60 gold.", "enabled": True},
    ]
    _sec, _cat, cont, _aux = _event_token(opts, 0)
    assert cont[C_GOLD_D] == pytest.approx(60 * GOLD_SCALE)
    assert cont[C_HP_D] == pytest.approx(0.0)


def test_structured_and_regex_agree_on_a_clean_option():
    # When the description is regex-friendly, structured and parsed match —
    # so flipping structured on is a no-op on the common case.
    text = "Grab. Gain 60 gold."
    assert event_payload(text, 80) == pytest.approx((60.0, 0.0))
    opts = [
        {
            "option_id": "grab",
            "label": "Grab",
            "description": "Gain 60 gold.",
            "enabled": True,
            "gold_delta": 60,
            "hp_delta": 0,
            "max_hp_delta": 0,
        },
    ]
    _sec, _cat, cont, _aux = _event_token(opts, 0)
    assert cont[C_GOLD_D] == pytest.approx(60 * GOLD_SCALE)
