#!/usr/bin/env python3
"""Live viewer for an in-progress MCP-driven STS2 run.

Tails the JSONL log written by ``sts2_mcp.py`` and re-renders the latest
state with ``play.py``-style formatting (HP bars, colors, card details).

Usage:
    python python/mcp_viewer.py                 # newest log in logs/
    python python/mcp_viewer.py --path FILE     # specific log
    python python/mcp_viewer.py --history N     # show N recent actions (default 25)
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from collections import deque
from collections.abc import Callable
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
LOGS = ROOT / "logs"
sys.path.insert(0, str(ROOT / "python"))

import play  # noqa: E402

DISPATCH: dict[str, Callable[[dict[str, Any]], None]] = {
    "combat_play": play.show_combat,
    "map_select": lambda s: play.show_map(s, send_fn=None),
    "card_reward": play.show_card_reward,
    "shop": play.show_shop,
    "rest_site": play.show_rest_site,
    "event_choice": play.show_event,
}


def _clear() -> None:
    # ANSI: clear screen + home cursor; works on Linux/macOS terminals and
    # modern Windows terminals.
    sys.stdout.write("\033[2J\033[H")
    sys.stdout.flush()


def _latest_log(logs_dir: Path) -> Path | None:
    files = sorted(logs_dir.glob("mcp_*.jsonl"), key=lambda p: p.stat().st_mtime)
    return files[-1] if files else None


def _parse_int_arg(args_str: str, key: str) -> int | None:
    """Extract an integer value from a formatted args string like 'card_index=1, target_index=0'."""
    import re

    m = re.search(rf"\b{re.escape(key)}=(-?\d+)", args_str)
    return int(m.group(1)) if m else None


def _parse_str_arg(args_str: str, key: str) -> str | None:
    """Extract a quoted string value from a formatted args string."""
    import re

    m = re.search(rf"\b{re.escape(key)}=(['\"])(.*?)\1", args_str)
    return m.group(2) if m else None


def _lookup_by_index(items: list[dict[str, Any]] | None, idx: int | None) -> dict[str, Any] | None:
    """Find an item by its ``index`` field, falling back to positional indexing."""
    if not items or idx is None:
        return None
    for it in items:
        if it.get("index") == idx:
            return it
    if 0 <= idx < len(items):
        return items[idx]
    return None


def _shop_summary(state: dict[str, Any]) -> str:
    """One-line summary of shop wares: cards/relics/potions with prices."""
    parts: list[str] = []
    for slot, prefix in (("cards", ""), ("relics", "r"), ("potions", "p")):
        items = state.get(slot) or []
        bits: list[str] = []
        for it in items:
            if it.get("is_stocked") is False:
                continue
            idx = it.get("index", "?")
            name = it.get("name", "?")
            cost = it.get("cost", it.get("price", "?"))
            sale = " SALE" if it.get("on_sale") else ""
            bits.append(f"[{prefix}{idx}] {name} ({cost}g{sale})")
        if bits:
            label = {"cards": "Cards", "relics": "Relics", "potions": "Potions"}[slot]
            parts.append(f"\033[2m{label}:\033[0m " + ", ".join(bits))
    removal = state.get("card_removal_cost")
    if removal:
        parts.append(f"\033[2mRemove:\033[0m {removal}g")
    gold = (state.get("player") or {}).get("gold")
    head = "\n           \033[36m🛒 Shop"
    if gold is not None:
        head += f" (you have {gold}g)"
    head += "\033[0m"
    if not parts:
        return head + " — \033[2m(empty)\033[0m"
    return head + "\n           " + "\n           ".join(parts)


def _ctx_label(state: dict[str, Any] | None) -> str:
    """`F12 · Overgrowth` style label from a state's context block.

    Always appended to the top-of-frame header so the current floor is
    visible regardless of which decision renderer (or fallback) runs
    below — non-decision tool calls (lookup, describe_*) and untyped
    decisions (bundle_select, terminal screens) otherwise leave the
    user without floor context.
    """
    if not isinstance(state, dict):
        return ""
    ctx = state.get("context") or {}
    if not isinstance(ctx, dict):
        return ""
    floor = ctx.get("floor")
    act = ctx.get("act_name") or ctx.get("act")
    bits: list[str] = []
    if floor is not None:
        bits.append(f"F{floor}")
    if act:
        bits.append(str(act))
    return " · ".join(bits)


def _state_suffix(state: dict[str, Any]) -> str:
    """`| HP 80/80 +5B | E 2/3` style suffix from a post-action state."""
    player = state.get("player") or {}
    hp = player.get("hp")
    mhp = player.get("max_hp")
    block = player.get("block", 0)
    energy = state.get("energy")
    max_e = state.get("max_energy")
    parts: list[str] = []
    if hp is not None and mhp is not None:
        s = f"HP {hp}/{mhp}"
        if block:
            s += f" +{block}B"
        parts.append(s)
    if energy is not None and max_e is not None:
        parts.append(f"E {energy}/{max_e}")
    return ("  \033[2m| " + " | ".join(parts) + "\033[0m") if parts else ""


def _intent_short(intent: dict[str, Any]) -> str:
    itype = intent.get("type", "?")
    if itype == "Attack" and intent.get("damage") is not None:
        dmg = intent.get("damage", 0)
        hits = intent.get("hits", 1) or 1
        return f"Atk {dmg}×{hits}={dmg * hits}" if hits > 1 else f"Atk {dmg}"
    if intent.get("block") is not None:
        return f"Def {intent['block']}"
    return itype


def _enemy_one_line(enemy: dict[str, Any]) -> str:
    name = enemy.get("name", "?")
    hp = enemy.get("hp", 0)
    mhp = enemy.get("max_hp", 0)
    intents = enemy.get("intents") or []
    s = f"{name} {hp}/{mhp}"
    if intents:
        s += f" ({', '.join(_intent_short(i) for i in intents)})"
    return s


def _enemy_intent_for_player(prev: dict[str, Any]) -> str:
    """Concise note like ' [vs Beetle Atk 6]' when defending against incoming attacks."""
    bits: list[str] = []
    for e in prev.get("enemies") or []:
        for it in e.get("intents") or []:
            if it.get("type") == "Attack" and it.get("damage") is not None:
                bits.append(f"{e.get('name', '?')} {_intent_short(it)}")
    return f"  \033[2m[vs {', '.join(bits)}]\033[0m" if bits else ""


def _combat_transition(prev: dict[str, Any], curr: dict[str, Any]) -> str:
    """Synthesize a combat-start / combat-end suffix when a non-combat action crosses
    the combat boundary."""
    prev_dec = prev.get("decision") if isinstance(prev, dict) else None
    curr_dec = curr.get("decision") if isinstance(curr, dict) else None
    if prev_dec != "combat_play" and curr_dec == "combat_play":
        enemies = curr.get("enemies") or []
        if enemies:
            roster = ", ".join(_enemy_one_line(e) for e in enemies)
            return f"\n           \033[35m⚔ {roster}\033[0m"
    if prev_dec == "combat_play" and curr_dec != "combat_play":
        prev_p = prev.get("player") or {}
        curr_p = curr.get("player") or {}
        ph = prev_p.get("hp")
        ch = curr_p.get("hp")
        rounds = prev.get("round")
        if ph is not None and ch is not None:
            delta = ch - ph
            color = "\033[32m" if delta >= 0 else "\033[31m"
            extra = f", {rounds} {'round' if rounds == 1 else 'rounds'}" if rounds else ""
            return f"  \033[2m✓ HP {ph}→{ch} {color}{delta:+d}\033[0m\033[2m{extra}\033[0m"
    return ""


def _action_summary(entry: dict[str, Any]) -> str:
    """Generate a rich one-liner describing what an action did and its outcome."""
    action = entry.get("action", "?")
    args_str = entry.get("args_str", "")
    prev = entry.get("prev_state") or {}
    curr = entry.get("state") or {}

    if action == "play_card" and isinstance(prev, dict):
        card_idx = _parse_int_arg(args_str, "card_index")
        target_idx = _parse_int_arg(args_str, "target_index")
        hand = prev.get("hand") or []
        card = next((c for c in hand if c.get("index") == card_idx), None)
        if card is None and card_idx is not None and card_idx < len(hand):
            card = hand[card_idx]
        card_name = (card or {}).get("name", "?")
        card_cost = (card or {}).get("cost", "?")
        result = f"play_card: \033[33m({card_cost}) {card_name}\033[0m"

        prev_p = prev.get("player") or {}
        curr_p = curr.get("player") or {} if isinstance(curr, dict) else {}
        prev_block = prev_p.get("block", 0)
        curr_block = curr_p.get("block", 0)

        outcomes: list[str] = []
        if target_idx is not None:
            prev_enemies = prev.get("enemies") or []
            curr_enemies = (curr.get("enemies") or []) if isinstance(curr, dict) else []
            if target_idx < len(prev_enemies):
                e = prev_enemies[target_idx]
                ename = e.get("name", "?")
                prev_hp = e.get("hp")
                curr_e = next((x for x in curr_enemies if x.get("index") == e.get("index")), None)
                if curr_e is None:
                    outcomes.append(f"{ename} \033[31m(killed)\033[0m")
                elif prev_hp is not None:
                    curr_hp = curr_e.get("hp", prev_hp)
                    dmg = prev_hp - curr_hp
                    if dmg > 0:
                        outcomes.append(f"{ename} \033[31m-{dmg}\033[0m ({prev_hp}→{curr_hp})")
                    else:
                        outcomes.append(ename)
        if curr_block > prev_block:
            outcomes.append(f"\033[34m+{curr_block - prev_block} block\033[0m")
        # Player power changes (Strength, etc.)
        pp = {p.get("name"): p.get("amount", 0) for p in (prev.get("player_powers") or [])}
        cp = {p.get("name"): p.get("amount", 0) for p in (curr.get("player_powers") or [])}
        for pname in sorted(set(pp) | set(cp)):
            if not pname:
                continue
            d = cp.get(pname, 0) - pp.get(pname, 0)
            if d > 0:
                outcomes.append(f"\033[35m+{d} {pname}\033[0m")
            elif d < 0:
                outcomes.append(f"\033[35m{d} {pname}\033[0m")

        if outcomes:
            result += " → " + ", ".join(outcomes)

        # If no enemy was targeted but block was gained, surface the incoming threat.
        if curr_block > prev_block and target_idx is None:
            result += _enemy_intent_for_player(prev)
        return result + _state_suffix(curr)

    if action == "end_turn":
        prev_p = prev.get("player", {}) if isinstance(prev, dict) else {}
        curr_p = curr.get("player", {}) if isinstance(curr, dict) else {}
        prev_hp = prev_p.get("hp")
        curr_hp = curr_p.get("hp")
        prev_block = prev_p.get("block", 0)
        curr_block = curr_p.get("block", 0)
        prev_energy = prev.get("energy") if isinstance(prev, dict) else None
        prev_round = prev.get("round") if isinstance(prev, dict) else None
        curr_round = curr.get("round") if isinstance(curr, dict) else None

        bits: list[str] = []
        if prev_round and curr_round and prev_round != curr_round:
            bits.append(f"round {prev_round}→{curr_round}")
        if prev_hp is not None and curr_hp is not None and prev_hp != curr_hp:
            delta = curr_hp - prev_hp
            blocked = max(0, prev_block - curr_block)
            color = "\033[32m" if delta > 0 else "\033[31m"
            took = f"took {-delta} dmg" if delta < 0 else f"gained {delta} HP"
            blk = f", blocked {blocked}" if blocked else ""
            bits.append(f"{took}{blk} ({color}HP {prev_hp}→{curr_hp}\033[0m)")
        elif prev_block > 0:
            bits.append(f"absorbed all {prev_block} dmg")
        if prev_energy is not None and prev_energy > 0:
            bits.append(f"\033[33m{prev_energy} E unspent\033[0m")

        head = "end_turn"
        if bits:
            head += "  (" + ", ".join(bits) + ")"
        return head + _state_suffix(curr) + _combat_transition(prev, curr)

    if action == "choose_option" and isinstance(prev, dict):
        idx = _parse_int_arg(args_str, "option_index")
        options = prev.get("options") or []
        if options:
            parts = []
            for opt in options:
                title = opt.get("title", "?")
                if opt.get("index") == idx:
                    parts.append(f"\033[33m★{title}\033[0m")
                else:
                    parts.append(title)
            event = prev.get("event_name", "")
            prefix = f"[{event}] " if event else ""
            return f"choose_option: {prefix}" + "  |  ".join(parts)

    if action == "select_map_node":
        node_type = curr.get("context", {}).get("room_type", "") if isinstance(curr, dict) else ""
        head = f"select_map_node({args_str})" + (f"  → {node_type}" if node_type else "")
        head += _combat_transition(prev, curr)
        # Show shop wares whenever we land in a shop.
        if isinstance(curr, dict) and curr.get("decision") == "shop":
            head += _shop_summary(curr)
        return head

    if action in ("buy_card", "buy_relic", "buy_potion") and isinstance(prev, dict):
        slot, key, prefix = {
            "buy_card": ("cards", "card_index", ""),
            "buy_relic": ("relics", "relic_index", "r"),
            "buy_potion": ("potions", "potion_index", "p"),
        }[action]
        idx = _parse_int_arg(args_str, key)
        items = prev.get(slot) or []
        item = _lookup_by_index(items, idx)
        if item:
            name = item.get("name", "?")
            cost = item.get("cost", item.get("price", "?"))
            head = f"{action}: \033[33m[{prefix}{idx}] {name} ({cost}g)\033[0m"
        else:
            head = f"{action}({args_str})"
        gold_prev = (prev.get("player") or {}).get("gold")
        gold_curr = (curr.get("player") or {}).get("gold") if isinstance(curr, dict) else None
        if gold_prev is not None and gold_curr is not None and gold_prev != gold_curr:
            head += f"  \033[2m(gold {gold_prev}→{gold_curr})\033[0m"
        return head

    if action in ("use_potion", "discard_potion") and isinstance(prev, dict):
        idx = _parse_int_arg(args_str, "potion_index")
        target_idx = _parse_int_arg(args_str, "target_index")
        potions = (prev.get("player") or {}).get("potions") or []
        potion = _lookup_by_index([p for p in potions if p], idx)
        name = (potion or {}).get("name", "?")
        verb = "use_potion" if action == "use_potion" else "discard_potion"
        head = f"{verb}: \033[33m[{idx}] {name}\033[0m"
        if target_idx is not None:
            enemies = prev.get("enemies") or []
            ename = (_lookup_by_index(enemies, target_idx) or {}).get(
                "name", f"target {target_idx}"
            )
            head += f" → \033[35m{ename}\033[0m"
        if isinstance(curr, dict):
            head += _state_suffix(curr)
        return head

    if action == "select_cards" and isinstance(prev, dict):
        indices_str = _parse_str_arg(args_str, "indices") or ""
        try:
            indices = [int(x) for x in indices_str.split(",") if x.strip()]
        except ValueError:
            indices = []
        cards = prev.get("cards") or []
        chosen_set = set(indices)
        parts: list[str] = []
        for c in cards:
            name = c.get("name", "?")
            tag = f"({c.get('cost', '?')}) {name}"
            parts.append(f"\033[33m★{tag}\033[0m" if c.get("index") in chosen_set else tag)
        if parts:
            return f"select_cards({indices_str}): " + "  |  ".join(parts)
        return f"select_cards({args_str})"

    if action == "select_bundle" and isinstance(prev, dict):
        idx = _parse_int_arg(args_str, "bundle_index")
        bundles = prev.get("bundles") or prev.get("options") or []
        bundle = _lookup_by_index(bundles, idx)
        if bundle:
            title = bundle.get("title") or bundle.get("name", "?")
            cards = bundle.get("cards") or []
            card_names = ", ".join(c.get("name", "?") for c in cards)
            head = f"select_bundle: \033[33m[{idx}] {title}\033[0m"
            if card_names:
                head += f"  ({card_names})"
            return head
        return f"select_bundle({args_str})"

    if action == "skip_select" and isinstance(prev, dict):
        cards = prev.get("cards") or []
        if cards:
            names = ", ".join(c.get("name", "?") for c in cards)
            return f"skip_select: \033[2m({names})\033[0m  →  \033[33m★(skipped)\033[0m"
        return "skip_select()"

    if action == "leave_room" and isinstance(prev, dict):
        if prev.get("decision") == "shop":
            return "leave_room()  \033[2m← exit shop\033[0m"
        return "leave_room()"

    if action == "remove_card":
        return "remove_card()"

    if action == "select_card_reward" and isinstance(prev, dict):
        idx = _parse_int_arg(args_str, "card_index")
        cards = prev.get("cards") or []
        if cards:
            parts = []
            for c in cards:
                name = c.get("name", "?")
                cost = c.get("cost", "?")
                rarity = c.get("rarity", "")
                tag = f"({cost}) {name}"
                if rarity:
                    tag += f"·{rarity}"
                parts.append(f"\033[33m★{tag}\033[0m" if c.get("index") == idx else tag)
            return "select_card_reward: " + "  |  ".join(parts)

    if action == "skip_card_reward" and isinstance(prev, dict):
        cards = prev.get("cards") or []
        if cards:
            names = "  |  ".join(c.get("name", "?") for c in cards)
            return f"skip_card_reward: \033[2m{names}\033[0m  →  \033[33m★(skipped)\033[0m"
        return "skip_card_reward()"

    if action == "describe_boss":
        boss_name = ""
        if isinstance(curr, dict):
            boss_name = (curr.get("context") or {}).get("boss", {}).get("name", "")
        if not boss_name and isinstance(prev, dict):
            boss_name = (prev.get("context") or {}).get("boss", {}).get("name", "")
        return "describe_boss()" + (f"  → \033[33m{boss_name}\033[0m" if boss_name else "")

    if action in ("describe_relic", "describe_potion", "describe_card"):
        return f"{action}({args_str})"

    if action == "proceed":
        return "proceed()" + _combat_transition(prev, curr)

    return f"{action}({args_str})"


def _render(entry: dict[str, Any], history: deque[dict[str, Any]], history_n: int) -> None:
    _clear()
    ts = str(entry.get("ts", "?"))
    state = entry.get("state") or {}

    short_ts = ts.split("T", 1)[-1] if "T" in ts else ts
    ctx = _ctx_label(state)
    ctx_part = f" \033[1m[{ctx}]\033[0m\033[36m" if ctx else ""
    print(f"\033[36m▶ [{short_ts}]{ctx_part} {_action_summary(entry)}\033[0m\n")

    decision = state.get("decision") or state.get("type") if isinstance(state, dict) else None
    renderer = DISPATCH.get(decision) if isinstance(decision, str) else None

    rendered = False
    if renderer is not None:
        try:
            renderer(state)
            rendered = True
        except Exception as e:  # never let render errors crash the viewer
            print(f"\033[31m(render error in {decision}: {e})\033[0m")
    if not rendered:
        # Fallback: print player + decision header. Useful for non-decision
        # tool calls (describe_card, show_deck, etc.) that don't change state.
        if isinstance(state, dict):
            player = state.get("player")
            if isinstance(player, dict):
                play.show_player(player)
            if decision:
                print(f"  decision: {decision}")
        else:
            print("(no cached state)")

    print()
    print("\033[2m" + "─" * 60 + "\033[0m")
    print("\033[2mRecent actions:\033[0m")
    for h in list(history)[-history_n:]:
        h_ts = str(h.get("ts", "?")).split("T", 1)[-1]
        summary = _action_summary(h)
        print(f"  \033[2m[{h_ts}]\033[0m {summary}")
    print("\033[2m" + "─" * 60 + "\033[0m")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--path", help="Path to JSONL log (default: newest in logs/)")
    ap.add_argument("--history", type=int, default=25, help="Recent actions to show")
    ap.add_argument("--poll", type=float, default=0.15, help="Poll interval in seconds")
    args = ap.parse_args()

    path = Path(args.path) if args.path else None
    if path is None:
        path = _latest_log(LOGS)
    if path is None:
        print(f"No mcp_*.jsonl in {LOGS}. Start a run via the MCP server first.")
        sys.exit(1)

    print(f"Tailing {path}\n", file=sys.stderr)
    history: deque[dict[str, Any]] = deque(maxlen=200)
    pos = 0
    last_render = 0.0
    pending: dict[str, Any] | None = None

    while True:
        if not path.exists():
            time.sleep(args.poll)
            continue
        with path.open(encoding="utf-8") as f:
            f.seek(pos)
            for raw in f:
                if not raw.strip():
                    continue
                try:
                    entry = json.loads(raw)
                except json.JSONDecodeError:
                    continue
                history.append(entry)
                pending = entry
            pos = f.tell()

        # Coalesce bursts: only re-render at most ~6×/s.
        now = time.monotonic()
        if pending is not None and (now - last_render) >= 0.15:
            _render(pending, history, args.history)
            pending = None
            last_render = now
        time.sleep(args.poll)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.stdout.write("\n")
