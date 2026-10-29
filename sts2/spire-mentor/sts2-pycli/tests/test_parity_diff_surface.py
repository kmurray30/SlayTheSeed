"""item#1: the v2 parity diff compares exhaust fate + per-hand-card cost.

Both engines have long emitted ``exhaust_ids`` (RunSimulator.GetStateImpl /
state_dict.rs ``exhaust_pile``) and a per-hand-card resolved cost
(``hand_costs`` / ``cost_override``), but ``_enrich_csharp`` / ``_enrich_rust``
never put them into the dict that :func:`parity_fuzz_state.diff` walks, so a
card landing in the wrong pile (discard-vs-exhaust) or a wrong resolved cost
(Confused, cost reduction) passed as "clean". These tests pin that the enriched
state now carries both surfaces and that ``diff`` fires on a mismatch.
"""

from __future__ import annotations

import parity_audit as pa
from parity_fuzz_state import diff


def _raw_cs(hand_ids, hand_costs, exhaust_ids) -> dict:
    return {
        "player": {"hp": 50, "max_hp": 50, "block": 0, "energy": 3, "powers": []},
        "monsters": [],
        "hand_count": len(hand_ids),
        "hand_ids": hand_ids,
        "hand_costs": hand_costs,
        "draw_count": 0,
        "draw_ids": [],
        "discard_count": 0,
        "discard_ids": [],
        "exhaust_count": len(exhaust_ids),
        "exhaust_ids": exhaust_ids,
        "turn": 1,
        "relics": [],
        "combat_terminal": False,
        "player_won": None,
    }


def test_enrich_csharp_carries_exhaust_and_costs() -> None:
    cs = pa._enrich_csharp(_raw_cs(["CARD.STRIKE_IRONCLAD"], [1], ["CARD.OFFERING"]))
    assert cs["exhaust_ids"] == ["OFFERING"]
    assert cs["hand_costs"] == [1]


def test_diff_fires_on_hand_cost_mismatch() -> None:
    a = _raw_cs(["CARD.WHIRLWIND"], [2], [])
    b = _raw_cs(["CARD.WHIRLWIND"], [1], [])
    divs = diff(pa._enrich_csharp(a), pa._enrich_csharp(b))
    assert any(d["path"].startswith("hand_costs") for d in divs)
    assert pa._classify("hand_costs[0]", True, {"action": "play_card"}) == "HAND_COST"


def test_diff_fires_on_exhaust_fate_mismatch() -> None:
    # same card, but one engine routed it to exhaust and the other did not
    a = _raw_cs([], [], ["CARD.SECOND_WIND"])
    b = _raw_cs([], [], [])
    divs = diff(pa._enrich_csharp(a), pa._enrich_csharp(b))
    assert any(d["path"].startswith("exhaust_ids") for d in divs)
    assert pa._classify("exhaust_ids.len", True, {"action": "play_card"}) == "EXHAUST_FATE"


def _raw_cs_orbs(orbs) -> dict:
    raw = _raw_cs([], [], [])
    raw["orbs"] = orbs
    return raw


def test_orb_surface_is_carried_and_compared() -> None:
    # orb-parity: the canonical state carries the channelled orb belt
    # (kind + Focus-folded passive + evoke), in channel order.
    cs = pa._enrich_csharp(_raw_cs_orbs([{"type": "Frost", "passive": 2, "evoke": 5}]))
    assert cs["orbs"] == [{"kind": "FROST", "passive": 2, "evoke": 5}]
    # No orbs (non-Defect) normalizes to an empty list, so it never diverges.
    assert pa._enrich_csharp(_raw_cs([], [], []))["orbs"] == []


def test_orb_evoke_is_compared_not_dropped() -> None:
    """`evoke` must stay on the surface — dropping it hid the Dark orb bug.

    It was excluded as a "representation mismatch" until 2026-08-03, when it
    turned out both wire legs were off by the same 6 (`DarkOrb._evokeVal` is
    `6 + stored`; Rust emitted the raw `stored` AND parsed it back without
    re-adding). The errors cancelled in every round-trip test, so only a
    surface that compares the value can catch the next one.
    """
    a = pa._enrich_csharp(_raw_cs_orbs([{"type": "Dark", "passive": 6, "evoke": 12}]))
    b = pa._enrich_csharp(_raw_cs_orbs([{"type": "Dark", "passive": 6, "evoke": 6}]))
    assert any(d["path"].startswith("orbs") for d in diff(a, b))


def test_diff_fires_on_orb_count_kind_and_passive() -> None:
    base = [{"type": "Lightning", "passive": 3, "evoke": 8}]
    # extra orb (channel/evoke count divergence)
    a = pa._enrich_csharp(_raw_cs_orbs([*base, {"type": "Frost", "passive": 2, "evoke": 5}]))
    b = pa._enrich_csharp(_raw_cs_orbs(base))
    assert any(d["path"].startswith("orbs") for d in diff(a, b))
    # wrong passive (e.g. Focus not applied)
    c = pa._enrich_csharp(_raw_cs_orbs([{"type": "Lightning", "passive": 5, "evoke": 10}]))
    d = pa._enrich_csharp(_raw_cs_orbs(base))
    divs = diff(c, d)
    assert any(d_["path"].startswith("orbs") for d_ in divs)
    assert pa._classify("orbs[0].passive", True, {"action": "end_turn"}) == "ORB_STATE"


def _raw_cs_osty(osty) -> dict:
    raw = _raw_cs([], [], [])
    if osty is not None:
        raw["osty"] = osty
    return raw


def test_osty_surface_is_carried_and_compared() -> None:
    # osty-parity: a live Necrobinder Osty carries hp/max_hp/block; a dead
    # or absent pet collapses to None on both sides.
    alive = pa._enrich_csharp(_raw_cs_osty({"hp": 9, "max_hp": 9, "block": 2, "alive": True}))
    assert alive["osty"] == {"hp": 9, "max_hp": 9, "block": 2}
    # C# emits {"alive": false} for a Necrobinder with no live pet → None.
    assert pa._enrich_csharp(_raw_cs_osty({"alive": False}))["osty"] is None
    # Non-Necrobinder omits the key entirely → None (never diverges).
    assert pa._enrich_csharp(_raw_cs_osty(None))["osty"] is None


def test_diff_fires_on_osty_summon_amount_and_alive() -> None:
    # Wrong Summon amount (MaxHp) surfaces directly instead of only via a
    # downstream OstyAttack-damage cascade.
    a = pa._enrich_csharp(_raw_cs_osty({"hp": 9, "max_hp": 9, "block": 0, "alive": True}))
    b = pa._enrich_csharp(_raw_cs_osty({"hp": 6, "max_hp": 6, "block": 0, "alive": True}))
    assert any(d["path"].startswith("osty") for d in diff(a, b))
    # Alive-vs-dead (failed summon / revive) is a type mismatch (dict vs None).
    live = pa._enrich_csharp(_raw_cs_osty({"hp": 1, "max_hp": 1, "block": 0, "alive": True}))
    dead = pa._enrich_csharp(_raw_cs_osty({"alive": False}))
    assert any(d["path"].startswith("osty") for d in diff(live, dead))
