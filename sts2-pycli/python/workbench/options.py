"""Reduce a non-combat decision-point state to uniform, clickable options.

Each option is a dict ``{"label", "action", "args", "enabled"}`` where
``action``/``args`` are exactly what ``/api/live/action`` forwards to the game.
The mapping mirrors the ``python/play.py`` interactive dispatch loop. Any
decision we do not explicitly handle yields a single ``proceed`` button so a
live run can never get stuck.
"""

from __future__ import annotations

from typing import Any

Option = dict[str, Any]


def _opt(label: str, action: str, args: dict[str, Any], enabled: bool = True) -> Option:
    return {"label": label, "action": action, "args": args, "enabled": enabled}


def normalize_options(state: dict[str, Any]) -> list[Option]:
    """Return the selectable options for *state*, or ``[]`` for combat/terminal.

    Args:
        state: A decision-point state dict (must carry ``decision``).

    Returns:
        Uniform option dicts. Empty for ``combat_play`` (the board handles it)
        and ``game_over`` (terminal).
    """
    decision = state.get("decision", "")
    if decision in ("combat_play", "game_over"):
        return []

    if decision == "map_select":
        out: list[Option] = []
        for ch in state.get("choices", []) or []:
            col, row = ch.get("col"), ch.get("row")
            label = f"{ch.get('type', 'Node')} ({col},{row})"
            out.append(_opt(label, "select_map_node", {"col": col, "row": row}))
        return out

    if decision == "card_reward":
        out = [
            _opt(
                cd.get("name", f"Card {cd.get('index')}"),
                "select_card_reward",
                {"card_index": cd.get("index")},
            )
            for cd in state.get("cards", []) or []
        ]
        if state.get("can_skip", True):
            out.append(_opt("Skip", "skip_card_reward", {}))
        return out

    if decision == "bundle_select":
        return [
            _opt(f"Pack {b.get('index')}", "select_bundle", {"bundle_index": b.get("index")})
            for b in state.get("bundles", []) or []
        ]

    if decision == "card_select":
        out = [
            _opt(
                cd.get("name", f"Card {cd.get('index')}"),
                "select_cards",
                {"indices": str(cd.get("index"))},
            )
            for cd in state.get("cards", []) or []
        ]
        # Skippable two ways, and they are NOT the same flag. ``min_select ==
        # 0`` means "picking nothing satisfies me"; ``can_skip`` is the engine's
        # own ``canSkip`` on a choice that otherwise wants one card — Discovery
        # and the Colorless / Skill / Attack / Power potions are min 1, max 1,
        # can_skip true. Gating on ``min_select`` alone dropped a legal action:
        # the combat search scores a ``choice:skip`` on those screens while
        # neither this list nor the UI offered one (bug wb-31c2d79f995fe1a6).
        if state.get("min_select", 1) == 0 or state.get("can_skip"):
            out.append(_opt("Skip", "skip_select", {}))
        return out

    if decision == "shop":
        out = []
        for cd in state.get("cards", []) or []:
            if cd.get("is_stocked"):
                label = f"Buy {cd.get('name')} ({cd.get('cost', '?')}g)"
                out.append(_opt(label, "buy_card", {"card_index": cd.get("index")}))
        for r in state.get("relics", []) or []:
            if r.get("is_stocked"):
                label = f"Buy {r.get('name')} ({r.get('cost', '?')}g)"
                out.append(_opt(label, "buy_relic", {"relic_index": r.get("index")}))
        for p in state.get("potions", []) or []:
            if p.get("is_stocked"):
                label = f"Buy {p.get('name')} ({p.get('cost', '?')}g)"
                out.append(_opt(label, "buy_potion", {"potion_index": p.get("index")}))
        if state.get("card_removal_cost"):
            out.append(_opt(f"Remove a card ({state['card_removal_cost']}g)", "remove_card", {}))
        out.append(_opt("Leave shop", "leave_room", {}))
        return out

    if decision == "rest_site":
        return [
            _opt(
                o.get("option_id") or o.get("name", f"Option {o.get('index')}"),
                "choose_option",
                {"option_index": o.get("index")},
                enabled=bool(o.get("is_enabled", True)),
            )
            for o in state.get("options", []) or []
        ]

    if decision == "event_choice":
        out = []
        for o in state.get("options", []) or []:
            title = o.get("title") or o.get("name") or f"Option {o.get('index')}"
            label = title if isinstance(title, str) else str(title)
            out.append(
                _opt(
                    label,
                    "choose_option",
                    {"option_index": o.get("index")},
                    enabled=not o.get("is_locked", False),
                )
            )
        return out

    # Safety net: unknown decision -> a single continue button.
    return [_opt("Continue", "proceed", {})]
