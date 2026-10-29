"""Combat heuristic recommender — used by the MCP ``recommend_combat`` tool.

Given a ``combat_play`` decision state, returns a single recommended action
(``play_card`` / ``use_potion`` / ``end_turn``) plus reasoning. The agent
calls this for sanity checks on routine turns; it is not a substitute for
careful play against bosses or unknown enemies.

Adapted from the older ``combat_helper.py`` CLI script. The logic is the
same; the entry point now reads the MCP server's cached state directly so
the agent doesn't have to shuttle huge state JSON through the prompt.
"""

from __future__ import annotations

from typing import Any

# Cards we never want to play (statuses / curses / negative effects).
_STATUS_CARDS = frozenset(
    {"Slimed", "Burn", "Wound", "Dazed", "Infection", "Void", "Regret", "Pain"}
)


def _card_name(card: dict[str, Any]) -> str:
    """State may give us a string, a {en, zh} dict, or a localized string."""
    name = card.get("name") or ""
    if isinstance(name, dict):
        return name.get("en") or name.get("zh") or ""
    return name


def calc_incoming(enemies: list[dict[str, Any]]) -> int:
    """Sum total incoming damage across all enemy intents this turn.

    Multi-hit attacks are multiplied out; single-hit damage is capped at 60
    per intent to absorb the occasional cosmetic huge-number from the engine.
    """
    total = 0
    for en in enemies:
        for it in en.get("intents") or []:
            if it.get("type") in ("Attack", "DeathBlow"):
                dmg = min(it.get("damage", 0), 60)
                hits = it.get("hits", 1) or 1
                total += dmg * hits if hits > 1 else dmg
    return total


def enemy_threat(en: dict[str, Any]) -> int:
    """High attack first, low HP as tiebreaker."""
    atk = 0
    for it in en.get("intents") or []:
        if it.get("type") in ("Attack", "DeathBlow"):
            hits = it.get("hits", 1) or 1
            atk += it.get("damage", 0) * (hits if hits > 1 else 1)
    return atk * 100 + (200 - en.get("hp", 0))


def pick_target(enemies: list[dict[str, Any]], card_dmg: int = 6) -> int:
    """Best target for an attack: one-shottable attacker > highest threat."""
    if not enemies:
        return 0
    best_oneshot: int | None = None
    for j, en in enumerate(enemies):
        if (
            en.get("hp", 0) <= card_dmg
            and en.get("intends_attack")
            and (best_oneshot is None or enemy_threat(en) > enemy_threat(enemies[best_oneshot]))
        ):
            best_oneshot = j
    if best_oneshot is not None:
        return best_oneshot
    return max(range(len(enemies)), key=lambda j: enemy_threat(enemies[j]))


def best_card(
    hand: list[dict[str, Any]],
    enemies: list[dict[str, Any]],
    energy: int,
    incoming: int,
    block: int,
    hp: int,
) -> tuple[int, int | None, str] | None:
    """Pick the best card to play right now, scored by priority (lower wins)."""
    block_gap = incoming - block
    lethal = block_gap >= hp
    can_tank = hp > incoming * 2
    need_block = block_gap > 0 and (lethal or not can_tank or block_gap > 15)

    best: tuple[int, int | None] | None = None
    best_p = 999
    best_reason = ""

    for i, c in enumerate(hand):
        if not c.get("can_play") or c.get("cost", 99) > energy:
            continue
        name = _card_name(c)
        cost = c.get("cost", 0)
        ctype = c.get("type", "")
        tt = c.get("target_type", "")
        keywords = c.get("keywords") or []

        if ctype in ("Status", "Curse") or "Unplayable" in keywords:
            continue
        if name in _STATUS_CARDS:
            continue

        is_block = ctype == "Skill" and tt != "AnyEnemy"
        is_attack = tt == "AnyEnemy"

        if cost == 0 and is_block and need_block:
            p, why = 1, "free block, needed"
        elif cost == 0 and ctype == "Power":
            p, why = 2, "free power"
        elif cost == 0 and is_attack:
            p, why = 3, "free attack"
        elif cost == 0:
            p, why = 4, "free 0-cost"
        elif ctype == "Power" and not lethal and incoming <= 15:
            p, why = 8 + cost, "power, safe turn"
        elif ctype == "Power" and not lethal:
            p, why = 15 + cost, "power, moderate danger"
        elif ctype == "Power" and lethal:
            p, why = 50, "power deprioritized (lethal)"
        elif is_block and lethal:
            p, why = 2 + cost, "block (lethal)"
        elif is_block and need_block:
            p, why = 10 + cost, f"block, gap={block_gap}"
        elif is_block and incoming == 0:
            p, why = 45, "block skipped (no incoming)"
        elif is_block and block_gap <= 0:
            p, why = 42, "block skipped (already covered)"
        elif is_block:
            p, why = 25 + cost, "block, normal"
        elif is_attack and not lethal:
            p, why = 12 + cost, "attack"
        elif is_attack and lethal:
            p, why = 40, "attack deprioritized (lethal)"
        else:
            p, why = 20 + cost, "other"

        ti: int | None = None
        if is_attack and enemies:
            card_dmg = (c.get("stats") or {}).get("damage", 6)
            ti = pick_target(enemies, card_dmg)

        if p < best_p:
            best_p, best, best_reason = p, (i, ti), why

    if best is None:
        return None
    return (best[0], best[1], best_reason)


def best_potion(
    potions: list[dict[str, Any]],
    enemies: list[dict[str, Any]],
    hp: int,
    incoming: int,
    block: int,
) -> tuple[int, int | None, str] | None:
    """Recommend a panic potion when in serious danger (only)."""
    if not potions:
        return None
    if not (hp < 20 or (incoming - block) >= hp):
        return None

    for pi, pot in enumerate(potions):
        name = _card_name(pot)
        tt = pot.get("target_type", "")

        if any(tag in name for tag in ("Heal", "Block", "Regen", "Fairy")):
            return (pi, None, f"emergency: use {name}")
        if tt == "AnyEnemy" and enemies:
            return (pi, pick_target(enemies), f"emergency: nuke with {name}")
        if tt in ("Self", "AnyPlayer", ""):
            return (pi, None, f"emergency: use {name}")
    return None


def recommend(state: dict[str, Any]) -> dict[str, Any]:
    """Recommend the next action for a ``combat_play`` decision.

    Output schema:
      {action: "play_card"|"use_potion"|"end_turn",
       args: {...},
       reasoning: "...",
       summary: "incoming=N block=N hp=N energy=N"}

    For non-combat states returns ``{action: None, ...}``.
    """
    decision = state.get("decision") or state.get("type")
    if decision != "combat_play":
        return {
            "action": None,
            "args": {},
            "reasoning": f"not in combat (decision={decision!r})",
        }

    hand = state.get("hand") or []
    enemies = state.get("enemies") or []
    energy = state.get("energy", 0)
    player = state.get("player") or {}
    hp = player.get("hp", 99)
    block = player.get("block", 0)
    potions = player.get("potions") or state.get("potions") or []

    incoming = calc_incoming(enemies)
    summary = f"incoming={incoming} block={block} hp={hp} energy={energy}"

    panic = best_potion(potions, enemies, hp, incoming, block)
    if panic is not None:
        pi, ti, why = panic
        args: dict[str, Any] = {"potion_index": pi}
        if ti is not None:
            args["target_index"] = ti
        return {"action": "use_potion", "args": args, "reasoning": why, "summary": summary}

    card = best_card(hand, enemies, energy, incoming, block, hp)
    if card is None:
        return {"action": "end_turn", "args": {}, "reasoning": "no useful card", "summary": summary}

    ci, ti, why = card
    args = {"card_index": ci}
    if ti is not None:
        args["target_index"] = ti
    return {"action": "play_card", "args": args, "reasoning": why, "summary": summary}
