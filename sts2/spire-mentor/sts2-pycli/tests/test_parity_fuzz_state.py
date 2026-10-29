"""Tests for parity_fuzz_state — normalizers + recursive diff.

Task 5 of the parity-fuzz harness plan
(docs/superpowers/plans/2026-05-24-csharp-rust-parity-fuzz.md).
"""

from __future__ import annotations

import pytest
from parity_fuzz_state import (
    _pascal_to_upper_snake,
    _strip_power_suffix,
    diff,
    normalize_csharp,
    normalize_rust,
)

# ---------------------------------------------------------------------------
# diff
# ---------------------------------------------------------------------------


def test_diff_identical_returns_empty():
    a = {"player": {"hp": 50, "max_hp": 75}, "monsters": [{"uid": 0, "hp": 10}]}
    b = {"player": {"hp": 50, "max_hp": 75}, "monsters": [{"uid": 0, "hp": 10}]}
    assert diff(a, b) == []


def test_diff_scalar_mismatch():
    a = {"player": {"hp": 50}}
    b = {"player": {"hp": 48}}
    assert diff(a, b) == [{"path": "player.hp", "csharp": 50, "rust": 48}]


def test_diff_missing_list_element_emits_len_first():
    a = {"monsters": [{"uid": 0, "hp": 10}, {"uid": 1, "hp": 5}]}
    b = {"monsters": [{"uid": 0, "hp": 10}]}
    out = diff(a, b)
    # First entry is the length mismatch at the list path.
    assert out[0] == {"path": "monsters.len", "csharp": 2, "rust": 1}


def test_diff_nested_list_indexed_path():
    a = {"monsters": [{"uid": 0, "powers": [{"kind": "STRENGTH", "amount": 3}]}]}
    b = {"monsters": [{"uid": 0, "powers": [{"kind": "STRENGTH", "amount": 4}]}]}
    out = diff(a, b)
    assert out == [
        {
            "path": "monsters[0].powers[0].amount",
            "csharp": 3,
            "rust": 4,
        }
    ]


def test_diff_type_mismatch_single_entry():
    out = diff({"x": 1}, {"x": "1"})
    assert out == [{"path": "x", "csharp": 1, "rust": "1"}]


def test_diff_dict_keys_sorted_deterministic():
    # b is missing key "a" and has different value at "b"; output ordering
    # should be deterministic (sorted by key at the dict level).
    a = {"b": 2, "a": 1}
    b = {"b": 3, "a": 1}
    out = diff(a, b)
    assert out == [{"path": "b", "csharp": 2, "rust": 3}]


# ---------------------------------------------------------------------------
# _pascal_to_upper_snake
# ---------------------------------------------------------------------------


@pytest.mark.parametrize(
    "src,expected",
    [
        ("ShrinkerBeetle", "SHRINKER_BEETLE"),
        ("BowlbugRock", "BOWLBUG_ROCK"),
        ("Nibbit", "NIBBIT"),
        ("LeafSlimeS", "LEAF_SLIME_S"),
        ("ALREADY_UPPER", "ALREADY_UPPER"),
        ("APIResponse", "API_RESPONSE"),
        ("", ""),
    ],
)
def test_pascal_to_upper_snake(src, expected):
    assert _pascal_to_upper_snake(src) == expected


# ---------------------------------------------------------------------------
# normalize_csharp
# ---------------------------------------------------------------------------


def _csharp_minimal():
    return {
        "type": "ok",
        "player": {
            "hp": 70,
            "max_hp": 75,
            "block": 0,
            "energy": 3,
            "powers": [
                {"kind": "STRENGTH", "amount": 2},
                {"kind": "DEXTERITY", "amount": 1},
            ],
        },
        "monsters": [
            {
                "uid": 1,
                "kind": "SHRINKER_BEETLE",
                "hp": 12,
                "max_hp": 14,
                "block": 0,
                "alive": True,
                "powers": [],
            },
            {
                "uid": 0,
                "kind": "NIBBIT",
                "hp": 8,
                "max_hp": 8,
                "block": 0,
                "alive": True,
                "powers": [],
            },
        ],
        "hand_count": 5,
        "draw_count": 10,
        "discard_count": 2,
        "exhaust_count": 0,
        "turn": 1,
        "relics": ["BURNING_BLOOD", "ANCHOR", "NEOWS_TORMENT"],
        "combat_terminal": False,
        "player_won": None,
    }


def test_normalize_csharp_shape():
    out = normalize_csharp(_csharp_minimal())
    assert out["player"]["hp"] == 70
    assert out["turn"] == 1
    assert out["combat_terminal"] is False
    assert out["player_won"] is None
    assert out["hand_count"] == 5


def test_normalize_csharp_powers_sorted_by_kind():
    out = normalize_csharp(_csharp_minimal())
    kinds = [p["kind"] for p in out["player"]["powers"]]
    assert kinds == sorted(kinds) == ["DEXTERITY", "STRENGTH"]


def test_normalize_csharp_monsters_sorted_by_uid():
    out = normalize_csharp(_csharp_minimal())
    assert [m["uid"] for m in out["monsters"]] == [0, 1]


def test_normalize_csharp_relics_sorted_and_neows_torment_filtered():
    out = normalize_csharp(_csharp_minimal())
    assert out["relics"] == ["ANCHOR", "BURNING_BLOOD"]
    assert "NEOWS_TORMENT" not in out["relics"]


# ---------------------------------------------------------------------------
# normalize_rust
# ---------------------------------------------------------------------------


class _FakePyCombatState:
    """Stand-in for sts2_rs.PyCombatState that exposes state_to_dict()."""

    def __init__(self, d):
        self._d = d

    def state_to_dict(self):
        return self._d


def _rust_minimal_dict():
    """Mirror of Rust state_to_dict shape (see crates/sts2-py/src/state_dict.rs)."""
    return {
        "wire_version": 1,
        "round": 1,
        "turn": 1,
        "player": {
            "hp": 70,
            "max_hp": 75,
            "block": 0,
            "energy": 3,
            "max_energy": 3,
            "relics": [
                {"id": "BURNING_BLOOD"},
                {"id": "ANCHOR"},
            ],
            "powers": [
                {"name": "STRENGTH", "amount": 2},
                {"name": "DEXTERITY", "amount": 1},
            ],
        },
        "deck": {
            "draw_pile": [None] * 10,
            "discard_pile": [None] * 2,
            "hand": [None] * 5,
            "exhaust_pile": [],
        },
        # Emission order is authoritative (sts2-rs-o1mwv): state_to_dict
        # emits monsters in enemy_uids / slot order, which normalize_rust
        # preserves (reassigning uid to the 0-based position). This matches
        # the slot order normalize_csharp produces after its uid sort.
        "encounter": [
            {
                "uid": 0,
                "kind": "Nibbit",
                "hp": 8,
                "max_hp": 8,
                "block": 0,
                "is_dead": False,
                "powers": [],
            },
            {
                "uid": 1,
                "kind": "ShrinkerBeetle",
                "hp": 12,
                "max_hp": 14,
                "block": 0,
                "is_dead": False,
                "powers": [],
            },
        ],
    }


def test_normalize_rust_basic_shape():
    out = normalize_rust(_FakePyCombatState(_rust_minimal_dict()))
    assert out["player"]["hp"] == 70
    assert out["turn"] == 1
    assert out["hand_count"] == 5
    assert out["draw_count"] == 10
    assert out["discard_count"] == 2
    assert out["exhaust_count"] == 0


def test_normalize_rust_kind_pascal_to_upper_snake():
    out = normalize_rust(_FakePyCombatState(_rust_minimal_dict()))
    kinds = [m["kind"] for m in out["monsters"]]
    assert kinds == ["NIBBIT", "SHRINKER_BEETLE"]


def test_normalize_rust_relics_sorted():
    out = normalize_rust(_FakePyCombatState(_rust_minimal_dict()))
    assert out["relics"] == ["ANCHOR", "BURNING_BLOOD"]


def test_normalize_rust_powers_sorted_by_kind():
    out = normalize_rust(_FakePyCombatState(_rust_minimal_dict()))
    kinds = [p["kind"] for p in out["player"]["powers"]]
    assert kinds == ["DEXTERITY", "STRENGTH"]


def test_normalize_rust_strips_power_suffix_symmetric_with_csharp():
    """Rust power ids that keep a ``_POWER`` suffix (FREE_POWER /
    GRAPPLE_POWER / MANGLE_POWER) must be stripped to the canonical short
    form, matching the C# side's ``_sort_powers`` strip. Otherwise the same
    power (ManglePower both sides) spuriously diverges on
    ``powers[].kind`` (C#="MANGLE" vs Rust="MANGLE_POWER"). Surfaced by the
    2026-05-30 coverage sweep (sts2-rs-q6bix triage)."""
    d = _rust_minimal_dict()
    d["encounter"][0]["powers"] = [{"name": "MANGLE_POWER", "amount": 10}]
    out = normalize_rust(_FakePyCombatState(d))
    target = next(m for m in out["monsters"] if m["uid"] == 0)
    assert [p["kind"] for p in target["powers"]] == ["MANGLE"]


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        # C# wraps every power class as `<Name>Power`. A power whose
        # canonical name already ends in "Power" double-suffixes:
        # FreePower → class FreePowerPower → "FREE_POWER_POWER".
        ("FREE_POWER_POWER", "FREE"),  # C# side
        ("FREE_POWER", "FREE"),  # Rust PowerKind::FreePower python_name
        # Single-suffix powers are unaffected (greedy strip is a no-op
        # past the first strip).
        ("MANGLE_POWER", "MANGLE"),
        ("SLOW_POWER", "SLOW"),
        ("STRENGTH", "STRENGTH"),
    ],
)
def test_strip_power_suffix_converges_free_power_double_suffix(raw, expected):
    """C# ``FreePowerPower`` (``FREE_POWER_POWER``) and Rust
    ``FreePower`` (``FREE_POWER``) must both canonicalize to ``FREE``.

    A single ``_POWER`` strip left them at ``FREE_POWER`` (C#) vs ``FREE``
    (Rust) — a spurious ``PLAYER_STATE`` divergence on Synthesis (which
    applies ``FreePower``; v2 sweep seed 2649). The greedy strip converges
    both while staying a no-op for single-suffix powers."""
    assert _strip_power_suffix(raw) == expected


def test_normalize_rust_monster_alive_from_is_dead():
    d = _rust_minimal_dict()
    # Kill the second monster (ShrinkerBeetle); it lands at position/uid 1
    # since normalize_rust reassigns uid to the emission-order index.
    d["encounter"][1]["is_dead"] = True
    out = normalize_rust(_FakePyCombatState(d))
    target = next(m for m in out["monsters"] if m["uid"] == 1)
    assert target["alive"] is False


def test_normalize_rust_combat_terminal_default_false():
    out = normalize_rust(_FakePyCombatState(_rust_minimal_dict()))
    # Rust state_to_dict doesn't expose these — normalizer defaults.
    assert out["combat_terminal"] is False
    assert out["player_won"] is None


def test_normalize_rust_normalizes_csharp_equivalent():
    """Both normalizers should agree on the canonical shape."""
    cs = normalize_csharp(_csharp_minimal())
    rs = normalize_rust(_FakePyCombatState(_rust_minimal_dict()))
    assert diff(cs, rs) == []


def test_normalize_rust_missing_required_key_raises():
    d = _rust_minimal_dict()
    del d["player"]
    with pytest.raises(KeyError, match="player"):
        normalize_rust(_FakePyCombatState(d))


# ---------------------------------------------------------------------------
# Integration (gated)
# ---------------------------------------------------------------------------


@pytest.mark.slow
@pytest.mark.skip(reason="needs scenario builder (Task 7/8)")
def test_integration_both_engines_diff_is_list():
    import sts2_rs
    from sts2_client import Game

    g = Game()
    try:
        # Placeholder — Task 7/8 will wire up a real scenario.
        cs_state = g.send({"command": "get_state"})
        rs_state = sts2_rs.PyCombatState()  # type: ignore[attr-defined]
        out = diff(normalize_csharp(cs_state), normalize_rust(rs_state))
        assert isinstance(out, list)
    finally:
        g.close()
