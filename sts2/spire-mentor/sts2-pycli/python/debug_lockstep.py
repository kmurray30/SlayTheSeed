"""Debug lockstep: replays run_parity's exact policy for one (seed, char) and
prints every chosen action plus rich combat views (enemy names/hp/intents,
hand) so an enemies_hp divergence can be attributed to a specific card play.

Usage: python python/debug_lockstep.py RP7 ironclad [--steps 400] [--from 0]
"""

from __future__ import annotations

import argparse
import random
import sys
from typing import Any

import run_parity as rp


def _fmt_enemies(view: dict[str, Any]) -> str:
    out = []
    for e in view.get("enemies") or []:
        if not isinstance(e, dict):
            out.append(str(e))
            continue
        name = e.get("id") or e.get("name") or "?"
        intent = e.get("intent") or e.get("intent_type") or ""
        dmg = e.get("intent_damage")
        block = e.get("block")
        powers = e.get("powers") or []
        ptxt = ",".join(
            f"{p.get('id') or p.get('name')}={p.get('amount')}" if isinstance(p, dict) else str(p)
            for p in powers
        )
        out.append(
            f"{name}(hp={e.get('hp')}/{e.get('max_hp')} blk={block} int={intent}{f'/{dmg}' if dmg is not None else ''}{f' [{ptxt}]' if ptxt else ''})"
        )
    return " | ".join(out)


def _fmt_event(view: dict[str, Any]) -> str:
    opts = []
    for o in view.get("options") or []:
        if isinstance(o, dict):
            label = o.get("text") or o.get("label") or o.get("text_key") or "?"
            opts.append(f"{o.get('index', '?')}:{label}{' [locked]' if o.get('is_locked') else ''}")
        else:
            opts.append(str(o))
    return f"{view.get('event_name') or '?'} opts=[{' | '.join(opts)}]"


def _fmt_hand(view: dict[str, Any]) -> str:
    p = view.get("player") or {}
    hand = view.get("hand") or p.get("hand") or []
    out = []
    for c in hand:
        if isinstance(c, dict):
            out.append(f"{c.get('id') or c.get('name')}{'+' if c.get('upgraded') else ''}")
        else:
            out.append(str(c))
    return f"E{p.get('energy')} blk={p.get('block')} hand=[{', '.join(out)}]"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("seed")
    ap.add_argument("character")
    ap.add_argument("--ascension", type=int, default=0)
    ap.add_argument("--steps", type=int, default=400)
    ap.add_argument(
        "--from", dest="from_step", type=int, default=0, help="print detail from this step"
    )
    args = ap.parse_args()

    rng = random.Random(f"runparity:{args.seed}")
    g = rp.make_backend("csharp")()
    r = rp.make_backend("rust", target_convention="csharp")()
    cs_view = g.start(character=args.character, seed=args.seed, ascension=args.ascension)
    rs_view = r.start(character=args.character, seed=args.seed, ascension=args.ascension)

    try:
        for step in range(args.steps):
            cs_dec = cs_view.get("decision") or cs_view.get("type")
            cs_snap, rs_snap = rp.Snap.of(cs_view, "cs"), rp.Snap.of(rs_view, "rs")
            fields = rp.diff_snaps(cs_snap, rs_snap)
            detail = step >= args.from_step

            if detail and cs_snap.phase == "combat":
                print(f"[{step:03d}] CS {_fmt_hand(cs_view)}")
                print(f"        CS enemies: {_fmt_enemies(cs_view)}")
                print(f"      RS {_fmt_hand(rs_view)}")
                print(f"        RS enemies: {_fmt_enemies(rs_view)}")
            if detail and cs_snap.phase == "event":
                print(f"[{step:03d}] CS event: {_fmt_event(cs_view)}")
                print(f"      RS event: {_fmt_event(rs_view)}")

            if fields:
                print(f"[{step:03d}] DIVERGE ({cs_snap.phase}):")
                for f in fields:
                    print(f"    - {f}")
                return 1
            if cs_dec == "game_over":
                print(f"[{step:03d}] game_over")
                return 0

            choices = rp.enumerate_choices(g, cs_view)
            if not choices:
                print(f"[{step:03d}] no choices for {cs_dec!r}")
                return 2
            verb, kw = choices[rng.randrange(len(choices))]
            print(f"[{step:03d}] {cs_snap.phase:11s} -> {verb} {kw}")
            cs_view = g.act(verb, **kw)
            rs_view = r.act(verb, **kw)
        print("max_steps")
        return 0
    finally:
        for b in (g, r):
            try:
                b.close()
            except Exception:
                pass


if __name__ == "__main__":
    sys.exit(main())
