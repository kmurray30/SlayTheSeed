"""Lockstep step-tracer for a single coverage subject (sts2-rs-gpsvo).

Reproduces parity_audit.run_trial for one coverage_subject and prints, after
every action, the C# vs Rust state (energy / hand / draw / discard / monster
hp+block) AND per-stream RNG cursors, so the first divergence and its cause are
visible turn-by-turn.

Usage:
    scripts/wheel-venv.sh run -- python python/trace_subject.py relic:GREMLIN_HORN
    scripts/wheel-venv.sh run -- python python/trace_subject.py card:CASCADE
"""

from __future__ import annotations

import random
import sys

import sts2_rs  # type: ignore[import-not-found]
from parity_audit import (
    _apply_csharp,
    _apply_rust,
    _csharp_init,
    _csharp_legal,
    _drain_csharp_choices,
    _drain_rust_choices,
    _enrich_csharp,
    _enrich_rust,
    _rng_desync,
    _rust_init,
    _rust_legal,
)
from parity_fuzz_scenario import coverage_scenarios
from parity_fuzz_state import diff
from sts2_client import Game

_RNG = (
    "niche",
    "monster_ai",
    "shuffle",
    "combat_card_generation",
    "combat_card_selection",
    "combat_energy_costs",
    "combat_targets",
)


def _short(ids):
    return [(c or "·").replace("_IRONCLAD", "").replace("_CARD", "") for c in (ids or [])]


def _mons(s):
    def _pw(m):
        ps = m.get("powers") or []
        if not ps:
            return ""
        return "/" + ",".join(f"{p.get('kind', '?')[:9]}{p.get('amount')}" for p in ps)

    return [
        f"hp{m.get('current_hp', m.get('hp'))}/blk{m.get('block', 0)}"
        f"/{m.get('move_state') or m.get('intent') or '?'}{_pw(m)}"
        for m in (s.get("monsters") or [])
    ]


def _rng_line(adapter, cs_raw):
    cs = cs_raw.get("rng_counters") or {}
    rs = sts2_rs.state_to_dict(adapter.inner).get("rng_counters") or {}
    parts = []
    for k in _RNG:
        c, r = cs.get(k), rs.get(k)
        if c or r:
            mark = "" if c == r else "  <<DESYNC"
            parts.append(f"{k}={c}/{r}{mark}")
    return " ".join(parts)


def _snap(tag, cs, rs):
    print(f"  [{tag}]")
    for label, c, r in (
        (
            "php",
            (cs.get("player", {}).get("hp"), cs.get("player", {}).get("block")),
            (rs.get("player", {}).get("hp"), rs.get("player", {}).get("block")),
        ),
        ("energy", cs.get("player", {}).get("energy"), rs.get("player", {}).get("energy")),
        ("hand", _short(cs.get("hand_ids")), _short(rs.get("hand_ids"))),
        ("draw", _short(cs.get("draw_ids")), _short(rs.get("draw_ids"))),
        ("disc", _short(cs.get("discard_ids")), _short(rs.get("discard_ids"))),
        ("mons", _mons(cs), _mons(rs)),
    ):
        flag = "" if c == r else "   <<DIFF"
        print(f"      {label:5} cs={c}")
        print(f"      {'':5} rs={r}{flag}")


def main() -> int:
    subject = sys.argv[1] if len(sys.argv) > 1 else "relic:GREMLIN_HORN"
    sweep = coverage_scenarios()
    idx = next((i for i, sc in enumerate(sweep) if sc.coverage_subject == subject), None)
    if idx is None:
        print(f"subject {subject!r} not found", file=sys.stderr)
        return 2
    sc = sweep[idx]
    seed = idx
    print(
        f"=== {subject}  (seed/idx={seed}) deck={_short(sc.deck)} relics={sc.relics} enc={sc.encounter}"
    )
    rng = random.Random(seed ^ 0xDEADBEEF)

    g = Game()
    cs_raw = _csharp_init(g, sc)
    cs_monsters = [m["kind"] for m in (cs_raw.get("monsters") or [])]
    cs_move_states = [m.get("move_state") for m in (cs_raw.get("monsters") or [])]
    rs_state = _rust_init(sc, monsters=None, move_states=cs_move_states)
    rs_native = [m.get("kind") for m in (_enrich_rust(rs_state).get("monsters") or [])]
    if rs_native != cs_monsters:
        rs_state = _rust_init(sc, monsters=cs_monsters, move_states=cs_move_states)
    _drain_rust_choices(rs_state)

    cs = _enrich_csharp(cs_raw)
    rs = _enrich_rust(rs_state)
    _snap("init", cs, rs)
    print(f"      rng   {_rng_line(rs_state, cs_raw)}")

    # Mirror parity_audit.run_trial's loop EXACTLY: `turn` counts GAME turns
    # (incremented only on end_turn), the loop runs `while turn < max_turns`,
    # and the combat-terminal short-circuit at the top uses the PREVIOUS
    # iteration's cs/rs. Any deviation changes the rng-driven action path.
    max_turns = 8
    step = 0
    turn = 0
    while turn < max_turns:
        if (
            cs.get("combat_terminal")
            and rs.get("combat_terminal")
            and cs.get("player_won") == rs.get("player_won")
        ):
            print("  (combat over — both terminal, same outcome)")
            break
        cs_actions = _csharp_legal(g)
        rs_actions = _rust_legal(rs_state)
        if cs_actions != rs_actions:
            print(
                f"  !! LEGAL_ACTION mismatch @turn{turn} step{step}: cs_only={[a for a in cs_actions if a not in rs_actions]} rs_only={[a for a in rs_actions if a not in cs_actions]}"
            )
            return 0
        if not cs_actions:
            print("  (no actions — combat over)")
            break
        action = rng.choice(cs_actions)
        cid = ""
        if action.get("action") == "play_card":
            hand = cs.get("hand_ids") or []
            ci = action.get("card_index", 0)
            cid = hand[ci] if 0 <= ci < len(hand) else "?"
        print(
            f"\n--- turn{turn} step{step}: {action.get('action')} {cid} idx={action.get('card_index')} tgt={action.get('target_index')}"
        )
        cs_result = _apply_csharp(g, action)
        _drain_csharp_choices(g, cs_result)
        _apply_rust(rs_state, action)
        _drain_rust_choices(rs_state)
        cs_raw = g.get_state()
        cs = _enrich_csharp(cs_raw)
        rs = _enrich_rust(rs_state)
        # Diff on the clean (normalized) dicts FIRST — Rust doesn't emit
        # move_state, so injecting it before diff() would false-flag it.
        divs = diff(cs, rs)
        # Surface per-monster move_state for DISPLAY only (the harness's
        # normalized dict drops it, so move divergences are invisible to
        # diff()). C# from cs_raw["monsters"]; Rust from the raw
        # state_to_dict "encounter" list (move_state debug field), matched
        # by uid since normalize_rust drops removed corpses.
        for cm, raw_m in zip(cs.get("monsters") or [], cs_raw.get("monsters") or []):
            cm["move_state"] = raw_m.get("move_state")
        rs_enc = sts2_rs.state_to_dict(rs_state.inner).get("encounter") or []
        rs_by_uid = {e.get("uid"): e for e in rs_enc}
        for rm in rs.get("monsters") or []:
            e = rs_by_uid.get(rm.get("uid"))
            if e is not None:
                rm["move_state"] = e.get("move_state") or (
                    (e.get("intents") or [{}])[0].get("intent_type")
                )
        _snap(f"after turn{turn} step{step}", cs, rs)
        print(f"      rng   {_rng_line(rs_state, cs_raw)}")
        if divs:
            print(
                f"  >>> FIRST DIVERGENCE @turn{turn} step{step}: {[(d['path'], d['csharp'], d['rust']) for d in divs[:8]]}"
            )
            return 0
        if action.get("action") == "end_turn":
            turn += 1
        step += 1
    print("\n(no divergence in traced window)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
