"""Tiny helpers for hand-crafting Sts2Headless ``combat_play`` fixtures.

We don't capture from a live Sts2Headless run here — the headless C#
binary may not be present in every developer's sandbox. Instead each
fixture is a minimal hand-crafted dict that exercises one adapter
path. The shape is anchored to:

* ``docs/sts2headless-combat-state-fields.md`` in sts2-rs
* ``sts2-pycli/src/Sts2Headless/RunSimulator.cs::CombatPlayState``

If a future capture script lands, it can drop JSON files in this dir
and the tests will pick them up via :func:`load_fixture`.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

FIXTURE_DIR = Path(__file__).resolve().parent


def card(
    *,
    index: int,
    cid: str,
    name: str,
    cost: int = 1,
    base_cost: int | None = None,
    persistent_cost: int | None = None,
    played_cost: int | None = None,
    stable_cost: int | None = None,
    ctype: str = "Attack",
    upgraded: bool = False,
    can_play: bool = True,
    target_type: str = "AnyEnemy",
    keywords: list[str] | None = None,
    affliction: str | None = None,
    enchantment: str | None = None,
    enchantment_amount: int | None = None,
) -> dict[str, Any]:
    """Build a minimal CombatCard dict in Sts2Headless shape.

    ``cost`` is the RESOLVED cost (every modifier folded in); ``base_cost``
    is the instance's own cost with only its local modifiers applied. They
    differ exactly when a global power hook is discounting the card, which
    is the case a reconstruction must not bake in permanently. Defaults to
    ``cost`` so existing fixtures describe an unmodified card.

    ``persistent_cost`` is what ``base_cost`` becomes once this turn's cost
    modifiers wear off; the game omits it when it cannot differ, so it
    defaults to absent. ``played_cost`` is the same idea at the other
    transient edge (the card being PLAYED), and ``stable_cost`` is the cost
    once both edges have passed.
    """
    out: dict[str, Any] = {
        "index": index,
        "id": cid,
        "name": name,
        "cost": cost,
        "base_cost": cost if base_cost is None else base_cost,
        "persistent_cost": persistent_cost,
        "played_cost": played_cost,
        "stable_cost": stable_cost,
        "type": ctype,
        "stats": None,
        "description": "",
        "can_play": can_play,
        "target_type": target_type,
    }
    if keywords:
        out["keywords"] = keywords
    if affliction is not None:
        out["affliction"] = affliction
    if enchantment is not None:
        out["enchantment"] = enchantment
    # C# omits the amount when it is 0 (`RunSimulator.cs` card payload), so a
    # `None` here reproduces that shape rather than sending an explicit zero.
    if enchantment_amount is not None:
        out["enchantment_amount"] = enchantment_amount
    if upgraded:
        out["upgraded"] = True
    return out


def enemy(
    *,
    index: int,
    name: str,
    hp: int,
    max_hp: int,
    block: int = 0,
    intents: list[dict[str, Any]] | None = None,
    powers: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    return {
        "index": index,
        "name": name,
        "hp": hp,
        "max_hp": max_hp,
        "block": block,
        "intents": intents or [],
        "intends_attack": any((it.get("type") == "Attack") for it in (intents or [])),
        "powers": powers,
    }


def player_summary(
    *,
    hp: int = 75,
    max_hp: int = 75,
    block: int = 0,
    relics: list[dict[str, Any]] | None = None,
    potions: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    return {
        "name": "Ironclad",
        "hp": hp,
        "max_hp": max_hp,
        "block": block,
        "gold": 99,
        "relics": relics or [],
        "potions": potions or [],
        "deck_size": 0,
        "deck": [],
    }


def base_state(
    *,
    player: dict[str, Any],
    hand: list[dict[str, Any]],
    enemies: list[dict[str, Any]],
    draw_pile: list[dict[str, Any]] | None = None,
    discard_pile: list[dict[str, Any]] | None = None,
    exhaust_pile: list[dict[str, Any]] | None = None,
    master_deck: list[dict[str, Any]] | None = None,
    player_powers: list[dict[str, Any]] | None = None,
    orbs: list[dict[str, Any]] | None = None,
    orb_slots: int | None = None,
    stars: int | None = None,
    osty: dict[str, Any] | None = None,
    energy: int = 3,
    max_energy: int = 3,
    round_: int = 1,
    room_type: str = "Combat",
) -> dict[str, Any]:
    state: dict[str, Any] = {
        "type": "decision",
        "decision": "combat_play",
        "context": {
            "act": 1,
            "act_name": "Overgrowth",
            "floor": 1,
            "room_type": room_type,
        },
        "round": round_,
        "energy": energy,
        "max_energy": max_energy,
        "hand": hand,
        "enemies": enemies,
        "player": player,
        "player_powers": player_powers,
        "draw_pile": draw_pile or [],
        "discard_pile": discard_pile or [],
        "exhaust_pile": exhaust_pile or [],
        "draw_pile_count": len(draw_pile or []),
        "discard_pile_count": len(discard_pile or []),
        "exhaust_pile_count": len(exhaust_pile or []),
    }
    if orbs is not None:
        state["orbs"] = orbs
        state["orb_slots"] = orb_slots if orb_slots is not None else len(orbs)
    if stars is not None:
        state["stars"] = stars
    if osty is not None:
        state["osty"] = osty
    if master_deck is not None:
        state["master_deck"] = master_deck
    return state


def load_fixture(name: str) -> dict[str, Any]:
    path = FIXTURE_DIR / name
    with path.open(encoding="utf-8") as fh:
        return json.load(fh)
