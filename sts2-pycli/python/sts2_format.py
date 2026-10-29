"""Compact text formatters for STS2 game state.

Produces terse, plain-text summaries of decision states for API consumers
(MCP server, agents). Color-free; no terminal escape codes.

The two non-trivial helpers (``_strip_template`` and ``_substitute_vars``)
are independent re-implementations of the SmartFormat / BBCode handling
in ``python/play.py`` (``desc`` + ``resolve_template``). Kept separate so
this module has no dependency on the interactive player.
"""

from __future__ import annotations

import functools
import json
import os
import re
import sys
from typing import Any

# ──────────────────────────────────────────────────────────────────────────────
# Description resolution
# ──────────────────────────────────────────────────────────────────────────────

_BBCODE = re.compile(r"\[/?[^\]]+\]")
_SMARTFORMAT = re.compile(r"\{([^{}]+)\}")
_VAR_REF = re.compile(r"\[([^\]]+)\]")


def _smart_replace(match: re.Match[str], *, upgraded: bool = False) -> str:
    full = match.group(1)
    if full.startswith("IfUpgraded:show:"):
        parts = full[len("IfUpgraded:show:") :].split("|")
        # parts[0] = upgraded text, parts[1] = non-upgraded fallback.
        return parts[0] if upgraded else (parts[1] if len(parts) > 1 else parts[0])
    if full.startswith("IfUpgraded:"):
        parts = full[len("IfUpgraded:") :].split("|")
        return parts[0] if upgraded else (parts[1] if len(parts) > 1 else parts[0])
    if full.startswith("InCombat:"):
        parts = full[len("InCombat:") :].split("|")
        return parts[0].lstrip("\n")
    if "energyIcons" in full:
        return f"[{full.split(':')[0]}]E"
    if "starIcons" in full:
        return f"[{full.split(':')[0]}]*"
    if ":plural:" in full:
        parts = full.split(":")
        var = parts[0]
        plural_parts = ":".join(parts[2:]).split("|")
        first = plural_parts[0]
        second = plural_parts[1] if len(plural_parts) > 1 else first
        return f"[{var}:{first}|{second}]"
    if ":" in full and "|" in full:
        parts_after = ":".join(full.split(":")[1:]).split("|")
        return parts_after[-1]
    return f"[{full.split(':')[0]}]"


def _strip_template(text: str | None, *, upgraded: bool = False) -> str:
    """Strip BBCode tags and convert SmartFormat tokens to ``[VarName]`` placeholders.

    Pass ``upgraded=True`` so ``{IfUpgraded:show:...}`` resolves to the upgraded branch.
    """
    if not text:
        return ""
    text = _BBCODE.sub("", text)

    def repl(m: re.Match[str]) -> str:
        return _smart_replace(m, upgraded=upgraded)

    for _ in range(3):
        text = _SMARTFORMAT.sub(repl, text)
    return text.strip()


def _substitute_vars(text: str, vars_dict: dict[str, Any] | None) -> str:
    """Replace ``[VarName]`` placeholders with values from ``vars_dict`` (case-insensitive).

    The C# side is expected to populate every var referenced in a template; if a key
    is missing this is a bug somewhere upstream. We log to stderr and fall back to
    the bare key (no brackets) so the user-facing output stays readable.
    """
    if not text or not vars_dict:
        # No vars to substitute — leave any [VarName] residue untouched. This is rare
        # in practice (C# always sends populated vars) and tests assert it.
        return text or ""
    lower = {k.lower(): v for k, v in vars_dict.items()}

    def repl(m: re.Match[str]) -> str:
        key = m.group(1)
        if ":" in key and "|" in key:
            var_name, plural_spec = key.split(":", 1)
            val = lower.get(var_name.lower())
            if val is not None:
                forms = plural_spec.split("|")
                singular = forms[0]
                plural = forms[1] if len(forms) > 1 else singular
                try:
                    return singular if int(val) == 1 else plural
                except (TypeError, ValueError):
                    return plural
            print(f"[sts2_format] missing template var: {var_name}", file=sys.stderr)
            return var_name
        val = lower.get(key.lower())
        if val is not None:
            return str(val)
        if key.lower() == "energyprefix":
            return ""
        print(f"[sts2_format] missing template var: {key}", file=sys.stderr)
        return key

    return _VAR_REF.sub(repl, text)


def resolve(
    description: str | None,
    vars_dict: dict[str, Any] | None = None,
    *,
    upgraded: bool = False,
) -> str:
    """Resolve a description string: strip BBCode + SmartFormat, then substitute vars.

    Args:
        description: Raw template text from the C# game (may contain BBCode + SmartFormat).
        vars_dict: Variable values to substitute. Keys are matched case-insensitively.
        upgraded: When ``True``, ``{IfUpgraded:show:…}`` resolves to the upgraded branch.
    """
    return _substitute_vars(_strip_template(description, upgraded=upgraded), vars_dict)


# ──────────────────────────────────────────────────────────────────────────────
# Atom formatters
# ──────────────────────────────────────────────────────────────────────────────


def format_card(card: dict[str, Any], *, show_index: bool = True) -> str:
    """One-line card summary plus an indented description."""
    idx = card.get("index")
    name = card.get("name", "?")
    cost = card.get("cost", "?")
    ctype = card.get("type", "")
    rarity = card.get("rarity")
    is_upgraded = bool(card.get("upgraded"))
    upgraded_marker = "+" if is_upgraded else ""
    head_bits = []
    if show_index and idx is not None:
        head_bits.append(f"[{idx}]")
    head_bits.append(f"({cost})")
    head_bits.append(f"{name}{upgraded_marker}")
    if ctype:
        head_bits.append(ctype)
    if rarity:
        head_bits.append(f"·{rarity}")
    if card.get("can_play") is False:
        head_bits.append("(unplayable)")
    head = " ".join(head_bits)
    body = resolve(card.get("description"), card.get("stats"), upgraded=is_upgraded)
    if body:
        body = "\n".join("    " + line for line in body.splitlines() if line.strip())
        return f"  {head}\n{body}"
    return f"  {head}"


def format_relic(relic: dict[str, Any]) -> str:
    name = relic.get("name", "?")
    body = resolve(relic.get("description"), relic.get("vars"))
    return f"  - {name}: {body}" if body else f"  - {name}"


def format_potion(potion: dict[str, Any]) -> str:
    idx = potion.get("index", "?")
    name = potion.get("name", "?")
    body = resolve(potion.get("description"), potion.get("vars"))
    return f"  [{idx}] {name}: {body}" if body else f"  [{idx}] {name}"


def _format_player_line(player: dict[str, Any]) -> str:
    hp = player.get("hp", 0)
    mhp = player.get("max_hp", 0)
    block = player.get("block", 0)
    gold = player.get("gold", 0)
    deck = player.get("deck_size", 0)
    parts = [f"HP {hp}/{mhp}"]
    if block:
        parts.append(f"Block {block}")
    parts.append(f"Gold {gold}")
    parts.append(f"Deck {deck}")
    return "  " + "  ".join(parts)


def _format_enemy(enemy: dict[str, Any]) -> str:
    idx = enemy.get("index", "?")
    name = enemy.get("name", "?")
    hp = enemy.get("hp", 0)
    mhp = enemy.get("max_hp", 0)
    block = enemy.get("block", 0)
    bits = [f"[{idx}] {name}", f"HP {hp}/{mhp}"]
    if block:
        bits.append(f"Block {block}")
    intents = enemy.get("intents") or []
    if intents:
        intent_strs = []
        for it in intents:
            kind = it.get("type", "?")
            extras = []
            for k in ("damage", "hits", "block", "amount"):
                v = it.get(k)
                if v is not None:
                    extras.append(f"{k}={v}")
            intent_strs.append(kind + (f"({','.join(extras)})" if extras else ""))
        bits.append("intent: " + ", ".join(intent_strs))
    powers = enemy.get("powers") or []
    if powers:
        pstrs = [f"{p.get('name', '?')}({p.get('amount', '?')})" for p in powers]
        bits.append("powers: " + ", ".join(pstrs))
    return "  " + "  ".join(bits)


def _format_osty(osty: dict[str, Any]) -> str:
    """Render Necrobinder's Osty companion (a player-side minion)."""
    if not osty.get("alive"):
        return "Osty:  (dead)"
    hp = osty.get("hp", 0)
    mhp = osty.get("max_hp", 0)
    block = osty.get("block", 0)
    name = osty.get("name", "Osty")
    parts = [f"HP {hp}/{mhp}"]
    if block:
        parts.append(f"Block {block}")
    return f"{name}:  " + "  ".join(parts)


def _format_player_powers(state: dict[str, Any]) -> list[str]:
    powers = state.get("player_powers") or []
    if not powers:
        return []
    out = ["Powers:"]
    for p in powers:
        name = p.get("name", "?")
        amt = p.get("amount", 0)
        body = resolve(
            p.get("description"), {"Amount": abs(amt) if isinstance(amt, (int, float)) else amt}
        )
        line = f"  - {name}"
        if amt:
            line += f" ({amt})"
        if body:
            line += f": {body}"
        out.append(line)
    return out


# ──────────────────────────────────────────────────────────────────────────────
# Per-decision formatters
# ──────────────────────────────────────────────────────────────────────────────


def _ctx_header(state: dict[str, Any], extra: str = "") -> str:
    ctx = state.get("context") or {}
    act = ctx.get("act_name") or f"Act {ctx.get('act', '?')}"
    floor = ctx.get("floor", "?")
    room = ctx.get("room_type", "")
    boss = (ctx.get("boss") or {}).get("name", "")
    head = f"{act}  Floor {floor}"
    if room:
        head += f"  ({room})"
    if boss:
        head += f"  · Boss: {boss}"
    if extra:
        head += f"  · {extra}"
    return head


def _format_combat(state: dict[str, Any]) -> str:
    rnd = state.get("round", 0)
    energy = state.get("energy", 0)
    max_e = state.get("max_energy", 0)
    draw = state.get("draw_pile_count", 0)
    discard = state.get("discard_pile_count", 0)
    exhaust = state.get("exhaust_pile_count", 0)
    lines = [
        _ctx_header(state, f"Round {rnd}"),
        (f"  Energy {energy}/{max_e}  Draw {draw}  Discard {discard}  Exhaust {exhaust}"),
    ]
    player = state.get("player") or {}
    if player:
        lines.append(_format_player_line(player))
        relics = player.get("relics") or []
        if relics:
            lines.append("Relics:")
            lines.extend(format_relic(r) for r in relics)
        potions = [p for p in (player.get("potions") or []) if p]
        if potions:
            lines.append("Potions:")
            lines.extend(format_potion(p) for p in potions)
    osty = state.get("osty")
    if osty:
        lines.append(_format_osty(osty))
    lines.extend(_format_player_powers(state))
    enemies = state.get("enemies") or []
    if enemies:
        lines.append("Enemies:")
        lines.extend(_format_enemy(e) for e in enemies)
    hand = state.get("hand") or []
    if hand:
        lines.append("Hand:")
        lines.extend(format_card(c) for c in hand)
    return "\n".join(lines)


def _format_map_select(state: dict[str, Any]) -> str:
    lines = [_ctx_header(state, "Choose next room")]
    for c in state.get("choices") or []:
        lines.append(f"  - col={c.get('col')} row={c.get('row')}  {c.get('type', '?')}")
    return "\n".join(lines)


def _format_card_reward(state: dict[str, Any]) -> str:
    can_skip = state.get("can_skip", True)
    gold = state.get("gold_earned")
    extra = "Card reward"
    if gold:
        extra += f" (gold +{gold})"
    lines = [_ctx_header(state, extra)]
    for c in state.get("cards") or []:
        lines.append(format_card(c))
    if can_skip:
        lines.append("  (skip allowed)")
    return "\n".join(lines)


def _format_bundle_select(state: dict[str, Any]) -> str:
    lines = [_ctx_header(state, "Choose a bundle")]
    for b in state.get("bundles") or state.get("options") or []:
        idx = b.get("index", "?")
        title = b.get("title") or b.get("name", "?")
        lines.append(f"  [{idx}] {title}")
        cards = b.get("cards") or []
        for c in cards:
            lines.append("    " + format_card(c, show_index=False).strip())
    return "\n".join(lines)


def _format_card_select(state: dict[str, Any]) -> str:
    minn = state.get("min_select", 0)
    maxn = state.get("max_select", 1)
    lines = [_ctx_header(state, f"Select cards (min={minn}, max={maxn})")]
    for c in state.get("cards") or []:
        lines.append(format_card(c))
    return "\n".join(lines)


def _format_event(state: dict[str, Any]) -> str:
    name = state.get("event_name", "Event")
    lines = [_ctx_header(state, name)]
    desc = resolve(state.get("description"))
    if desc:
        lines.append("  " + desc)
    for o in state.get("options") or []:
        idx = o.get("index", "?")
        title = o.get("title", "?")
        body = resolve(o.get("description"), o.get("vars"))
        locked = " (LOCKED)" if o.get("is_locked") else ""
        lines.append(f"  [{idx}] {title}{locked}")
        if body:
            for ln in body.splitlines():
                if ln.strip():
                    lines.append("      " + ln)
    return "\n".join(lines)


def _format_rest_site(state: dict[str, Any]) -> str:
    lines = [_ctx_header(state, "Rest site")]
    for o in state.get("options") or []:
        idx = o.get("index", "?")
        name = o.get("name") or o.get("title", "?")
        body = resolve(o.get("description"), o.get("vars"))
        enabled = o.get("is_enabled", True)
        marker = " (disabled)" if not enabled else ""
        line = f"  [{idx}] {name}{marker}"
        if body:
            line += f": {body}"
        lines.append(line)
    return "\n".join(lines)


def _format_shop(state: dict[str, Any]) -> str:
    lines = [_ctx_header(state, "Shop")]
    slot_labels = (
        ("cards", "Cards:"),
        ("relics", "Relics:"),
        ("potions", "Potions:"),
    )
    for slot_name, slot_label in slot_labels:
        items = state.get(slot_name) or []
        if not items:
            continue
        lines.append(slot_label)
        for it in items:
            idx = it.get("index", "?")
            name = it.get("name", "?")
            cost = it.get("cost", it.get("price", "?"))
            lines.append(f"  [{idx}] {name}  ({cost}g)")
    return "\n".join(lines)


def _format_game_over(state: dict[str, Any]) -> str:
    victory = state.get("victory", False)
    title = "VICTORY" if victory else "DEFEAT"
    lines = [_ctx_header(state, title)]
    player = state.get("player") or {}
    if player:
        lines.append(_format_player_line(player))
    return "\n".join(lines)


_DISPATCH: dict[str, Any] = {
    "combat_play": _format_combat,
    "map_select": _format_map_select,
    "card_reward": _format_card_reward,
    "bundle_select": _format_bundle_select,
    "card_select": _format_card_select,
    "event_choice": _format_event,
    "rest_site": _format_rest_site,
    "shop": _format_shop,
    "game_over": _format_game_over,
}


def format_decision(state: dict[str, Any]) -> str:
    """Return a compact text summary of a decision-state JSON dict."""
    if not isinstance(state, dict):
        return repr(state)
    if state.get("type") == "error":
        return f"ERROR: {state.get('message', 'unknown error')}"
    decision = state.get("decision")
    formatter = _DISPATCH.get(decision)
    if formatter:
        return formatter(state)
    return f"[{decision or state.get('type', '?')}] (no formatter)"


# ──────────────────────────────────────────────────────────────────────────────
# Inspection formatters (counterparts to play.py's `relic`, `potion`, `boss`,
# `deck`, `map` meta-commands).
# ──────────────────────────────────────────────────────────────────────────────


def format_deck(player_or_state: dict[str, Any]) -> str:
    """Render the player's full deck."""
    player = player_or_state.get("player") if "player" in player_or_state else player_or_state
    deck = (player or {}).get("deck") or []
    if not deck:
        return "Deck: (empty)"
    lines = [f"Deck ({len(deck)} cards):"]
    for c in deck:
        lines.append(format_card(c, show_index=False))
    return "\n".join(lines)


def format_map(map_data: dict[str, Any]) -> str:
    """Render the full map: per-row node list + boss + current position."""
    rows = map_data.get("rows") or []
    boss = map_data.get("boss") or {}
    cur = map_data.get("current_coord") or {}
    lines = [
        f"Map: {len(rows)} rows  "
        f"Boss: {boss.get('name', '?')} @ ({boss.get('col')},{boss.get('row')})"
    ]
    if cur:
        lines.append(f"Current: ({cur.get('col')},{cur.get('row')})")
    for ri, row in enumerate(rows):
        cells = []
        for n in row:
            mark = "*" if n.get("current") else ("v" if n.get("visited") else " ")
            cells.append(f"({n.get('col')},{n.get('row')}){mark}{n.get('type', '?')[:3]}")
        lines.append(f"  row {ri}: " + "  ".join(cells))
    return "\n".join(lines)


def _resolve_named_query(items: list[dict[str, Any]], query: str) -> dict[str, Any] | None:
    if not items or not query:
        return None
    if query.isdigit():
        idx = int(query)
        for it in items:
            if it.get("index") == idx:
                return it
        if 0 <= idx < len(items):
            return items[idx]
        return None
    q = query.lower()
    for it in items:
        if q in (it.get("name") or "").lower():
            return it
    return None


def format_relic_detail(player: dict[str, Any], query: str) -> str:
    relics = player.get("relics") or []
    r = _resolve_named_query(relics, query)
    if not r:
        return "No matching relic."
    name = r.get("name", "?")
    body = resolve(r.get("description"), r.get("vars"))
    out = [name]
    if body:
        out.append(body)
    vars_dict = r.get("vars") or {}
    if vars_dict:
        out.append("Values: " + ", ".join(f"{k}={v}" for k, v in vars_dict.items()))
    return "\n".join(out)


def format_potion_detail(player: dict[str, Any], query: str) -> str:
    potions = [p for p in (player.get("potions") or []) if p]
    p = _resolve_named_query(potions, query)
    if not p:
        return "No matching potion."
    idx = p.get("index", "?")
    name = p.get("name", "?")
    body = resolve(p.get("description"), p.get("vars"))
    out = [f"[{idx}] {name}"]
    if body:
        out.append(body)
    return "\n".join(out)


@functools.cache
def _load_loc() -> dict[str, str]:
    """Load + cache the English localization tables once per process.

    Keys are ``<table>:<dotted.key>``.
    """
    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    cache: dict[str, str] = {}
    d = os.path.join(base, "localization_eng")
    if not os.path.isdir(d):
        return cache
    for fname in os.listdir(d):
        if not fname.endswith(".json"):
            continue
        try:
            # `localization_eng/` is UTF-8 (curly quotes, en/em dashes, `→`).
            # Without an explicit encoding, `open` uses the locale default —
            # cp1252 on Windows — and the load dies with UnicodeDecodeError,
            # taking every caller (`format_boss_summary`, the card/relic
            # inspectors) down with it.
            with open(os.path.join(d, fname), encoding="utf-8") as fh:
                data = json.load(fh)
        except (OSError, UnicodeDecodeError, json.JSONDecodeError):
            continue
        table = fname[:-5]
        for k, v in data.items():
            cache.setdefault(f"{table}:{k}", v)
    return cache


def _boss_known_moves(boss_id: str) -> list[str]:
    """Look up a boss's known move titles via the monsters localization table."""
    if not boss_id:
        return []
    monster_key = boss_id[:-5] if boss_id.endswith("_BOSS") else boss_id
    if monster_key == "THE_KIN":
        monster_key = "KIN_PRIEST"
    cache = _load_loc()
    prefix = f"monsters:{monster_key}.moves."
    suffix = ".title"
    out: list[str] = []
    seen: set[str] = set()
    for k, v in cache.items():
        if not k.startswith(prefix) or not k.endswith(suffix):
            continue
        move_id = k[len(prefix) : -len(suffix)]
        if move_id in seen:
            continue
        seen.add(move_id)
        out.append(v)
    return out


def format_boss_summary(state_or_map: dict[str, Any]) -> str:
    """Compact boss summary from either a decision state or a get_map response."""
    boss = (state_or_map.get("context") or {}).get("boss") or state_or_map.get("boss") or {}
    if not boss:
        return "No boss info available."
    name = boss.get("name", "?")
    bid = boss.get("id", "")
    out = [f"Boss: {name}"]
    if bid:
        out.append(f"id: {bid}")
        moves = _boss_known_moves(bid)
        if moves:
            out.append("Known moves: " + ", ".join(moves))
    return "\n".join(out)


def format_relics_list(player: dict[str, Any]) -> str:
    """List every owned relic with its resolved description."""
    relics = (player or {}).get("relics") or []
    if not relics:
        return "No relics."
    lines = [f"Relics ({len(relics)}):"]
    for i, r in enumerate(relics):
        name = r.get("name", "?")
        body = resolve(r.get("description"), r.get("vars"))
        lines.append(f"  [{i}] {name}")
        if body:
            for ln in body.splitlines():
                if ln.strip():
                    lines.append(f"      {ln}")
    return "\n".join(lines)


def format_potions_list(player: dict[str, Any]) -> str:
    """List every owned potion with its resolved description."""
    pots = [p for p in ((player or {}).get("potions") or []) if p]
    if not pots:
        return "No potions."
    lines = [f"Potions ({len(pots)}):"]
    for p in pots:
        idx = p.get("index", "?")
        name = p.get("name", "?")
        body = resolve(p.get("description"), p.get("vars"))
        lines.append(f"  [{idx}] {name}")
        if body:
            for ln in body.splitlines():
                if ln.strip():
                    lines.append(f"      {ln}")
    return "\n".join(lines)


# ──────────────────────────────────────────────────────────────────────────────
# Combat diff formatter
# ──────────────────────────────────────────────────────────────────────────────

_DIFF_SEP = "─" * 44


def _intent_short(intent: dict[str, Any]) -> str:
    """Compact one-token intent: 'Atk 6', 'Atk 6×2=12', 'Def 5', or the type name."""
    itype = intent.get("type", "?")
    if itype == "Attack" and intent.get("damage") is not None:
        dmg = intent.get("damage", 0)
        hits = intent.get("hits", 1) or 1
        if hits > 1:
            return f"Atk {dmg}×{hits}={dmg * hits}"
        return f"Atk {dmg}"
    if intent.get("block") is not None:
        return f"Def {intent['block']}"
    return itype


def _intents_summary(intents: list[dict[str, Any]]) -> str:
    if not intents:
        return ""
    return ", ".join(_intent_short(i) for i in intents)


def _enemy_short(enemy: dict[str, Any], *, show_intent: bool = True) -> str:
    """`Beetle 30/30HP (Atk 6)` style one-line enemy summary."""
    name = enemy.get("name", "?")
    hp = enemy.get("hp", 0)
    mhp = enemy.get("max_hp", 0)
    block = enemy.get("block", 0)
    out = f"{name} {hp}/{mhp} HP"
    if block:
        out += f" +{block}B"
    if show_intent:
        intents = _intents_summary(enemy.get("intents") or [])
        if intents:
            out += f" ({intents})"
    return out


def _has_energy(state: dict[str, Any]) -> bool:
    return "energy" in state or "max_energy" in state


def _pair_enemies(
    prev_list: list[dict[str, Any]],
    curr_list: list[dict[str, Any]],
) -> list[tuple[dict[str, Any], dict[str, Any] | None]]:
    """Pair prev-state enemies with their curr-state counterpart for diffing.

    Each enemy's ``index`` is its position in the alive-only list (assigned by
    the C# serializer via ``Select((e, i) => …)``), so indices shift down when
    an enemy dies mid-action. A naive prev[i]→curr[i] match would then
    misattribute the dead enemy's HP/power changes to its surviving
    neighbour. Relative order is preserved across snapshots though, so we
    greedy-match left-to-right on ``(name, max_hp)``; prev entries with no
    match are defeated.
    """
    pairs: list[tuple[dict[str, Any], dict[str, Any] | None]] = []
    j = 0
    n = len(curr_list)
    for pe in prev_list:
        matched: dict[str, Any] | None = None
        k = j
        while k < n:
            ce = curr_list[k]
            if ce.get("name") == pe.get("name") and ce.get("max_hp") == pe.get("max_hp"):
                matched = ce
                j = k + 1
                break
            k += 1
        pairs.append((pe, matched))
    return pairs


def _power_changes(
    prev_pows: list[dict[str, Any]] | None,
    curr_pows: list[dict[str, Any]] | None,
) -> list[str]:
    pmap: dict[str, int] = {
        p["name"]: p.get("amount", 0) for p in (prev_pows or []) if p.get("name")
    }
    cmap: dict[str, int] = {
        p["name"]: p.get("amount", 0) for p in (curr_pows or []) if p.get("name")
    }
    out: list[str] = []
    for pname in sorted(set(pmap) | set(cmap)):
        diff = cmap.get(pname, 0) - pmap.get(pname, 0)
        if diff > 0:
            out.append(f"+{diff} {pname}")
        elif diff < 0:
            out.append(f"{diff} {pname}")
    return out


def _format_play_card(
    prev: dict[str, Any],
    curr: dict[str, Any],
    args: tuple[Any, ...],
    kw: dict[str, Any],
) -> str:
    card_index: int | None = args[0] if args else kw.get("card_index")
    card_name = "?"
    card_cost: Any = "?"
    for c in prev.get("hand") or []:
        if c.get("index") == card_index:
            card_name = c.get("name", "?")
            card_cost = c.get("cost", "?")
            break

    prev_p = prev.get("player") or {}
    curr_p = curr.get("player") or {}
    prev_hp = prev_p.get("hp", 0)
    curr_hp = curr_p.get("hp", 0)
    prev_block = prev_p.get("block", 0)
    curr_block = curr_p.get("block", 0)

    # On player defeat, combat ends and curr.enemies is empty — every prev enemy
    # would be paired to None and reported as defeated by this card. Suppress
    # enemy attribution in that case so we don't claim e.g. Bloodletting killed
    # the boss.
    combat_ended = curr.get("decision") != "combat_play"
    pairs = (
        []
        if combat_ended and not (curr.get("enemies") or [])
        else _pair_enemies(prev.get("enemies") or [], curr.get("enemies") or [])
    )

    results: list[str] = []
    for pe, ce in pairs:
        delta = pe.get("hp", 0) - (ce.get("hp", 0) if ce else 0)
        if delta > 0:
            e_blocked = max(0, pe.get("block", 0) - ((ce or {}).get("block", 0)))
            note = f", {e_blocked} blocked" if e_blocked else ""
            suffix = " (defeated)" if ce is None else f" ({pe['hp']}→{ce['hp']} HP)"
            results.append(f"{pe.get('name', '?')}: {delta} dmg{note}{suffix}")

    if curr_block > prev_block:
        gained = curr_block - prev_block
        results.append(f"Player: +{gained} block (→{curr_block})")

    pow_changes = _power_changes(prev.get("player_powers"), curr.get("player_powers"))
    for change in pow_changes:
        results.append(f"Player: {change}")

    if curr_hp < prev_hp:
        results.append(f"Player: {prev_hp - curr_hp} HP lost ({prev_hp}→{curr_hp})")

    if not results:
        return ""

    head = f"► ({card_cost}) {card_name}"
    if _has_energy(prev):
        prev_e = prev.get("energy", 0)
        curr_e = curr.get("energy", 0) if curr.get("decision") == "combat_play" else prev_e
        head += f"  ·  E {prev_e}→{curr_e}"

    # Defensive plays: surface what the enemy was about to do so the choice is
    # legible after the fact (player gained block but dealt no damage).
    if curr_block > prev_block and not any("dmg" in r for r in results):
        intent_strs: list[str] = []
        for e in prev.get("enemies") or []:
            for it in e.get("intents") or []:
                if it.get("type") == "Attack" and it.get("damage") is not None:
                    intent_strs.append(f"{e.get('name', '?')} {_intent_short(it)}")
        if intent_strs:
            head += "  [vs " + ", ".join(intent_strs) + "]"

    return head + " → " + "; ".join(results)


def _format_end_turn(prev: dict[str, Any], curr: dict[str, Any]) -> str:
    prev_p = prev.get("player") or {}
    curr_p = curr.get("player") or {}
    prev_hp = prev_p.get("hp", 0)
    curr_hp = curr_p.get("hp", 0)
    prev_block = prev_p.get("block", 0)
    curr_block = curr_p.get("block", 0)
    prev_energy = prev.get("energy", 0)

    prev_enemies_list = prev.get("enemies") or []
    pairs = _pair_enemies(prev_enemies_list, curr.get("enemies") or [])
    attacking_count = sum(
        1
        for e in prev_enemies_list
        if any(it.get("type") == "Attack" and it.get("damage") for it in (e.get("intents") or []))
    )

    enemy_lines: list[str] = []
    for e, ce in pairs:
        name = e.get("name", "?")
        for it in e.get("intents") or []:
            itype = it.get("type", "?")
            idmg = it.get("damage")
            ihits = max(1, it.get("hits", 1))
            if itype == "Attack" and idmg is not None:
                total = idmg * ihits
                if attacking_count == 1:
                    hp_lost = prev_hp - curr_hp
                    blocked = max(0, prev_block - curr_block)
                    parts: list[str] = []
                    if blocked:
                        parts.append(f"{blocked} blocked")
                    if hp_lost > 0:
                        parts.append(f"{hp_lost} HP lost ({prev_hp}→{curr_hp})")
                    detail = ", ".join(parts) if parts else "no damage"
                    enemy_lines.append(f"  {name}: Attack {total} — {detail}")
                else:
                    enemy_lines.append(f"  {name}: Attack {total}")
            else:
                changes = _power_changes(e.get("powers"), (ce or {}).get("powers"))
                pow_str = " (" + ", ".join(changes) + ")" if changes else ""
                enemy_lines.append(f"  {name}: {itype}{pow_str}")

    if attacking_count > 1 and prev_hp != curr_hp:
        hp_lost = prev_hp - curr_hp
        blocked = max(0, prev_block - curr_block)
        parts2: list[str] = []
        if blocked:
            parts2.append(f"{blocked} blocked")
        parts2.append(f"{hp_lost} HP lost ({prev_hp}→{curr_hp})")
        enemy_lines.append("  Total: " + ", ".join(parts2))

    head = "End turn"
    if _has_energy(prev) and prev_energy > 0:
        head += f" · {prev_energy} energy unspent"

    if not enemy_lines:
        return head if _has_energy(prev) and prev_energy > 0 else ""
    return head + "\n" + "Enemy turn:" + "\n" + "\n".join(enemy_lines)


def _format_combat_start(curr: dict[str, Any]) -> str:
    enemies = curr.get("enemies") or []
    if not enemies:
        return ""
    player = curr.get("player") or {}
    hp = player.get("hp", 0)
    mhp = player.get("max_hp", 0)
    head = f"⚔ Combat starts · HP {hp}/{mhp}"
    body = "\n".join(f"  {_enemy_short(e)}" for e in enemies)
    return head + "\n" + body


def _format_combat_end(prev: dict[str, Any], curr: dict[str, Any]) -> str:
    prev_p = prev.get("player") or {}
    curr_p = curr.get("player") or {}
    prev_hp = prev_p.get("hp", 0)
    curr_hp = curr_p.get("hp", 0)
    delta = curr_hp - prev_hp
    rounds = prev.get("round", 0)
    if curr.get("decision") == "game_over" and not curr.get("victory", True):
        outcome = "✗ DEFEAT"
    else:
        outcome = "✓ Combat ends"
    line = f"{outcome} · HP {prev_hp}→{curr_hp}"
    if delta != 0:
        line += f" ({delta:+d})"
    if rounds:
        line += f" · {rounds} rounds"
    return line


def _format_round_start(curr: dict[str, Any]) -> str:
    rnd = curr.get("round", 0)
    energy = curr.get("energy", 0)
    max_e = curr.get("max_energy", 0)
    player = curr.get("player") or {}
    hp = player.get("hp", 0)
    mhp = player.get("max_hp", 0)
    block = player.get("block", 0)

    head = f"Round {rnd} — HP {hp}/{mhp}"
    if block:
        head += f" +{block}B"
    if _has_energy(curr):
        head += f" · E {energy}/{max_e}"

    parts = [head]
    hand = curr.get("hand") or []
    if hand:
        hand_str = ", ".join(
            f"[{c.get('index', '?')}] {c.get('name', '?')}({c.get('cost', '?')})" for c in hand
        )
        parts.append(f"  Hand: {hand_str}")
    enemies = curr.get("enemies") or []
    for e in enemies:
        parts.append(f"  {_enemy_short(e)}")
    return "\n".join(parts)


def _format_card_reward_offered(curr: dict[str, Any]) -> str:
    cards = curr.get("cards") or []
    if not cards:
        return ""
    can_skip = curr.get("can_skip", True)
    parts = ["🎁 Card reward offered:"]
    for c in cards:
        idx = c.get("index", "?")
        name = c.get("name", "?")
        cost = c.get("cost", "?")
        ctype = c.get("type", "")
        rarity = c.get("rarity", "")
        bits = [f"  [{idx}] ({cost}) {name}"]
        if ctype:
            bits.append(ctype)
        if rarity:
            bits.append(f"·{rarity}")
        parts.append(" ".join(bits))
    if can_skip:
        parts.append("  (skippable)")
    return "\n".join(parts)


def _format_card_reward_chosen(
    prev: dict[str, Any],
    action: str,
    args: tuple[Any, ...],
    kw: dict[str, Any],
) -> str:
    cards = prev.get("cards") or []
    if action == "skip_card_reward":
        names = ", ".join(c.get("name", "?") for c in cards) or "—"
        return f"⤬ Skipped card reward: {names}"
    if action == "select_card_reward":
        idx = args[0] if args else kw.get("card_index")
        chosen_name = "?"
        skipped: list[str] = []
        for c in cards:
            if c.get("index") == idx:
                chosen_name = c.get("name", "?")
            else:
                skipped.append(c.get("name", "?"))
        skip_str = f" · skipped: {', '.join(skipped)}" if skipped else ""
        return f"★ Picked: {chosen_name}{skip_str}"
    return ""


def format_state_diff(
    prev: dict[str, Any],
    curr: dict[str, Any] | None,
    action: str,
    action_args: tuple[Any, ...] = (),
    action_kwargs: dict[str, Any] | None = None,
) -> str:
    """Compact before→after narrative to prepend to the next decision summary.

    Covers transitions across the run, not just combat:
    - **Combat start**: entering combat from any other decision — enemy roster, intents.
    - **In-combat**: ``play_card`` / ``end_turn`` results (damage, block, intents).
    - **Round start**: HP/energy/hand/intents snapshot when round advances.
    - **Combat end**: HP delta + rounds taken when leaving combat.
    - **Card reward offered/chosen**: cards seen and which one was picked or skipped.

    Returns ``""`` when nothing meaningful changed.
    """
    if not prev or not curr:
        return ""

    kw = action_kwargs or {}
    sections: list[str] = []

    prev_decision = prev.get("decision")
    curr_decision = curr.get("decision")

    if prev_decision != "combat_play" and curr_decision == "combat_play":
        s = _format_combat_start(curr)
        if s:
            sections.append(s)

    if prev_decision == "combat_play":
        if action == "play_card":
            line = _format_play_card(prev, curr, action_args, kw)
            if line:
                sections.append(line)
        elif action == "end_turn":
            line = _format_end_turn(prev, curr)
            if line:
                sections.append(line)

        if curr_decision != "combat_play":
            s = _format_combat_end(prev, curr)
            if s:
                sections.append(s)
        elif curr.get("round", 0) > prev.get("round", 0):
            s = _format_round_start(curr)
            if s:
                sections.append(s)

    if prev_decision != "card_reward" and curr_decision == "card_reward":
        s = _format_card_reward_offered(curr)
        if s:
            sections.append(s)

    if prev_decision == "card_reward" and action in (
        "select_card_reward",
        "skip_card_reward",
    ):
        s = _format_card_reward_chosen(prev, action, action_args, kw)
        if s:
            sections.append(s)

    if not sections:
        return ""
    return _DIFF_SEP + "\n" + f"\n{_DIFF_SEP}\n".join(sections) + "\n" + _DIFF_SEP


_PILE_LABELS: dict[str, tuple[str, str]] = {
    "draw": ("draw_pile", "Draw pile"),
    "discard": ("discard_pile", "Discard pile"),
    "exhaust": ("exhaust_pile", "Exhaust pile"),
}


def format_pile(state: dict[str, Any], kind: str) -> str:
    """Render a combat pile (``draw``, ``discard`` or ``exhaust``)."""
    spec = _PILE_LABELS.get(kind)
    if spec is None:
        valid = ", ".join(_PILE_LABELS)
        return f"Unknown pile: {kind!r} (expected {valid})"
    state_key, label = spec
    pile = state.get(state_key) or []
    if not pile:
        return f"{label}: (empty)"
    lines = [f"{label} ({len(pile)}):"]
    for card in pile:
        lines.append(format_card(card, show_index=False))
    return "\n".join(lines)
