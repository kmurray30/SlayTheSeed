#!/usr/bin/env python3
"""Walk the C# and Rust workbench backends in lockstep and report the first
divergence point.

Both backends are driven through the same workbench :class:`GameBackend`
surface (the C# verbs ``select_map_node`` / ``select_card_reward`` /
``end_turn`` / …), so we can issue identical actions to both and check
whether they end up in the same state at each step.

Action policy: at every decision point, pick option **0** (first map child,
first card reward, first event option, first hand card, …). This is dumb on
purpose — the goal isn't to play well, it's to traverse the same edges.

At each step we hash a normalized state signature
(decision, hp/max/gold/floor/deck-size, decision-specific picks like enemy
ids and reward card ids) and report the first index where the two signatures
diverge.

Usage:
    uv run --frozen python python/parity_walk.py --seed Y3X2YZSBQJ --steps 80
"""

from __future__ import annotations

import argparse
import re
import sys
from typing import Any

sys.path.insert(0, "python")

from workbench import canonicalize_seed
from workbench.backends.base import make_backend

# ─── state normalization ──────────────────────────────────────────────────


_NON_ALNUM = re.compile(r"[^A-Z0-9]+")


def _loc_id(s: str | None) -> str:
    """Strip a display string or loc key down to its canonical id.

    ``"Burning Blood"`` → ``"BURNINGBLOOD"``;
    ``"BURNING_BLOOD.title"`` → ``"BURNINGBLOOD"``;
    ``"NEOW.pages.INITIAL.options.SMALL_CAPSULE"`` → ``"SMALLCAPSULE"``.
    """
    if not s:
        return ""
    # Strip a trailing ``.title`` / ``.name`` / ``.description`` loc-key suffix
    # (Rust emits ``BURNING_BLOOD.title``), then take the rightmost
    # ``.``-segment (C# loc keys like ``NEOW.pages.INITIAL.options.SMALL_CAPSULE``).
    for suffix in (".title", ".name", ".description"):
        if s.endswith(suffix):
            s = s[: -len(suffix)]
            break
    tail = s.rsplit(".", 1)[-1]
    return _NON_ALNUM.sub("", tail.upper())


def signature(state: dict[str, Any]) -> dict[str, Any]:
    """Engine-agnostic snapshot of the decision-relevant fields."""
    p = state.get("player") or {}
    sig: dict[str, Any] = {
        "decision": state.get("decision"),
        # ``floor`` is intentionally EXCLUDED from the signature: the two
        # backends serialize non-corresponding counters into this field (C#
        # ``RunState.ActFloor`` vs Rust ``RunState.total_floor``), so it is
        # off-by-one at the pre-first-room ``map_select`` (C#=1, Rust=0) even
        # though both engines are correct — Rust's ``total_floor`` is Neow-
        # uncounted and floor-gated mechanics apply the documented ``+1``
        # offset internally (see events/mod.rs punch_off_allowed). It carries
        # no parity signal here, so comparing it only produces a false
        # divergence at step 1. (sts2-rs-2c0ze.)
        "hp": p.get("hp"),
        "max_hp": p.get("max_hp"),
        "gold": p.get("gold"),
        "block": p.get("block"),
        "deck_size": len(p.get("deck") or []),
        "relics": sorted(_loc_id(r.get("name")) for r in p.get("relics") or []),
    }
    decision = state.get("decision")
    if decision == "map_select":
        # Map nodes are SPATIAL (selected by col/row, see pick_action), so the
        # serialization ORDER of the choice list carries no game meaning — the
        # two backends legitimately enumerate the same node set in different
        # order. Compare as a sorted set so an ordering-only difference is not a
        # false divergence; a genuine difference in the available nodes still
        # trips it.
        sig["map_choices"] = sorted(
            (c.get("col"), c.get("type")) for c in state.get("choices") or []
        )
    elif decision == "card_reward":
        sig["card_ids"] = [_loc_id(c.get("id") or c.get("name")) for c in state.get("cards") or []]
    elif decision in ("event_choice", "card_select"):
        sig["option_titles"] = [
            _loc_id(o.get("text_key") or o.get("title")) for o in state.get("options") or []
        ] or [_loc_id(c.get("id") or c.get("name")) for c in state.get("cards") or []]
    elif decision == "combat_play":
        sig["enemies"] = [
            (_loc_id(e.get("name")), e.get("hp"), e.get("max_hp"))
            for e in state.get("enemies") or []
        ]
        sig["hand"] = [_loc_id(c.get("id") or c.get("name")) for c in state.get("hand") or []]
        sig["energy"] = state.get("energy")
    elif decision == "shop":
        sig["shop_cards"] = [
            _loc_id(c.get("id") or c.get("name")) for c in state.get("cards") or []
        ]
        sig["shop_relics"] = [_loc_id(r.get("name")) for r in state.get("relics") or []]
        sig["shop_potions"] = [_loc_id(p.get("name")) for p in state.get("potions") or []]
    elif decision == "rest_site":
        # The rest-to-heal action is labelled HEAL by the C# backend and REST by
        # the Rust backend — same gameplay option, different option_id string —
        # so alias them to one canonical id, and compare the set (order carries
        # no meaning here either).
        sig["rest_options"] = sorted(
            _REST_OPTION_ALIASES.get(rid, rid)
            for rid in (_loc_id(o.get("option_id")) for o in state.get("options") or [])
        )
    return sig


#: Rest-site option ids that name the same action across the two backends.
_REST_OPTION_ALIASES = {"HEAL": "REST"}


# ─── lockstep policy ──────────────────────────────────────────────────────


def pick_action(state: dict[str, Any]) -> tuple[str, dict[str, Any]] | None:
    """Pick a deterministic 'first option' action for the current decision.

    Returns ``None`` when the run is over or no action is available.
    """
    decision = state.get("decision")
    if decision == "map_select":
        choices = state.get("choices") or []
        if not choices:
            return None
        # Pick a CANONICAL node (lowest col, then row) rather than choices[0]:
        # the two backends enumerate the same node set in different order, so
        # choices[0] would send each engine down a different path and desync the
        # walk. Sorting makes both engines select the same node deterministically.
        c = min(choices, key=lambda n: (n["col"], n.get("row", 0)))
        return "select_map_node", {"col": c["col"], "row": c.get("row", 0)}
    if decision == "card_reward":
        cards = state.get("cards") or []
        if not cards:
            return "skip_card_reward", {}
        return "select_card_reward", {"card_index": 0}
    if decision == "event_choice":
        opts = state.get("options") or []
        if not opts:
            return None
        # Skip locked options if any are unlocked.
        unlocked = [o for o in opts if not o.get("is_locked")]
        pick = unlocked[0] if unlocked else opts[0]
        return "choose_option", {"option_index": pick.get("index", 0)}
    if decision == "card_select":
        cards = state.get("cards") or []
        if not cards:
            if state.get("can_skip"):
                return "skip_select", {}
            return None
        # Supply ALL mandatory picks, not just one: a select may be pick-N-of-M
        # (e.g. RoomFullOfCheese "Gorge" is pick-2-of-8). Sending a single index
        # to a min==max==2 grid forks the walk — Rust faithfully re-surfaces the
        # select for the remaining pick(s) while the C# oracle silently resolves
        # the grid under-count. Pick the first `min_select` cards (>=1 unless the
        # select is genuinely skippable).
        n = int(state.get("min_select", 1) or 0)
        if n <= 0:
            if state.get("can_skip"):
                return "skip_select", {}
            n = 1
        n = min(n, len(cards))
        indices = ",".join(str(cards[i].get("index", i)) for i in range(n))
        return "select_cards", {"indices": indices}
    if decision == "combat_play":
        hand = state.get("hand") or []
        for c in hand:
            if not c.get("can_play"):
                continue
            args: dict[str, Any] = {"card_index": c["index"]}
            if c.get("target_type") not in (None, "None", "Self"):
                enemies = state.get("enemies") or []
                if enemies:
                    args["target_index"] = enemies[0]["index"]
            return "play_card", args
        return "end_turn", {}
    if decision == "shop":
        return "leave_room", {}
    if decision == "rest_site":
        return "choose_option", {"option_index": 0}
    if decision == "game_over":
        return None
    return None


# ─── walker ───────────────────────────────────────────────────────────────


def walk(seed: str, character: str, ascension: int, max_steps: int) -> int:
    seed = canonicalize_seed(seed)
    cs_cls = make_backend("csharp")
    rs_cls = make_backend("rust", target_convention="csharp")
    cs = cs_cls()
    rs = rs_cls()

    try:
        cs_state = cs.start(character=character, seed=seed, ascension=ascension)
        rs_state = rs.start(character=character, seed=seed, ascension=ascension)

        for step in range(max_steps):
            sc, sr = signature(cs_state), signature(rs_state)
            if sc != sr:
                print(f"\n✗ DIVERGED at step {step}")
                print(f"  csharp: {sc}")
                print(f"  rust  : {sr}")
                diffs = [k for k in sorted(set(sc) | set(sr)) if sc.get(k) != sr.get(k)]
                print(f"  diff keys: {diffs}")
                return step

            action = pick_action(cs_state)
            if action is None:
                print(f"\n✓ identical through step {step} (end of walk: decision={sc['decision']})")
                return -1

            verb, args = action
            print(f"  step {step:>3}  {sc['decision']:<14}  → {verb} {args}")
            cs_resp = cs.act(verb, **args)
            rs_resp = rs.act(verb, **args)
            if isinstance(cs_resp, dict) and cs_resp.get("type") == "error":
                print(f"\n! csharp rejected {verb} {args}: {cs_resp.get('message')}")
                return step
            if isinstance(rs_resp, dict) and rs_resp.get("type") == "error":
                print(f"\n! rust rejected {verb} {args}: {rs_resp.get('message')}")
                return step
            cs_state, rs_state = cs_resp, rs_resp

        print(f"\n✓ identical through {max_steps} steps (max reached)")
        return -1
    finally:
        try:
            cs.close()
        except Exception:
            pass
        try:
            rs.close()
        except Exception:
            pass


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--seed", default="Y3X2YZSBQJ")
    ap.add_argument("--character", default="Ironclad")
    ap.add_argument("--ascension", type=int, default=0)
    ap.add_argument("--steps", type=int, default=80)
    args = ap.parse_args()
    return 0 if walk(args.seed, args.character, args.ascension, args.steps) < 0 else 1


if __name__ == "__main__":
    sys.exit(main())
