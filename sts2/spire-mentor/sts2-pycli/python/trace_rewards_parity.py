#!/usr/bin/env python3
"""Per-step `rewards`-stream counter diff for the shared-Rewards divergence class.

The act-2 potion-drop / deck `|0|1` divergences (e.g. Ironclad:1000 @287 —
gold cs=391 rs=394, potions cs=[] rs=['SKILLPOTION']) are SYMPTOMS: the
shared C# `PlayerRng.Rewards` stream (card-rarity odds AND potion-reward odds
roll off the same stream) desyncs in an *earlier* combat, then every later roll
lands at a shifted position. The official `Snap` only sees the late symptom.

This driver reuses ``act23_state_parity``'s invincible lockstep policy verbatim
but, instead of stopping at the first *state* divergence, tracks the monotonic
``rng_counters["rewards"]`` read-count on BOTH engines and reports the FIRST step
where the two counters diverge — i.e. the combat where Rust did an extra/missing
read on the Rewards stream. That step's phase + last action localises the bug.

Usage::

    scripts/wheel-venv.sh run -- python python/trace_rewards_parity.py \
        --character Ironclad --seed 1000 --max-steps 320
"""

from __future__ import annotations

import argparse
import random
import sys
from typing import Any

sys.path.insert(0, "python")

from parity_audit import assert_wheel_fresh
from run_parity import Snap, enumerate_choices
from workbench.backends.base import make_backend


def _is_err(view) -> bool:
    return isinstance(view, dict) and (view.get("error") or view.get("type") == "error")


def _cs_rewards(g) -> int | None:
    """C# rewards counter from the full get_state dump (act-view is trimmed)."""
    try:
        rc = g.get_state().get("rng_counters")
        return (
            int(rc["rewards"]) if isinstance(rc, dict) and rc.get("rewards") is not None else None
        )
    except Exception:
        return None


def _rs_rewards(r) -> int | None:
    """Rust rewards counter via the RunManager's own rng_counters() accessor
    (the lockstep act-view / _serialize dict omits rng_counters)."""
    try:
        return int(r.rm.rng_counters()["rewards"])
    except Exception:
        return None


INVINCIBLE_HP = 100_000


def trace(seed: str, character: str, ascension: int, max_steps: int) -> int:
    rng = random.Random(f"act23state:{seed}")  # SAME policy seed as act23_state_parity
    g = make_backend("csharp")()
    r = make_backend("rust", target_convention="csharp")()
    last_action = "<start>"
    first_div: tuple | None = None
    tail = 0
    try:
        cs_view = g.start(character=character, seed=seed, ascension=ascension)
        rs_view = r.start(character=character, seed=seed, ascension=ascension)

        for step in range(max_steps):
            cs_dec = cs_view.get("decision") or cs_view.get("type")
            cs_snap = Snap.of(cs_view, "cs")
            cs_rew, rs_rew = _cs_rewards(g), _rs_rewards(r)

            if cs_rew != rs_rew and first_div is None:
                first_div = (step, cs_snap.phase, last_action, cs_rew, rs_rew)

                def _ev(v):
                    if not isinstance(v, dict):
                        return None
                    for k in ("event", "event_id", "event_name", "name", "title"):
                        if v.get(k):
                            return v.get(k)
                    return {k: v.get(k) for k in ("decision", "type", "options") if k in v}

                print(
                    f"\n>>> REWARDS COUNTER DIVERGE @step {step} phase={cs_snap.phase}\n"
                    f"    last_action = {last_action}\n"
                    f"    cs rewards counter = {cs_rew}   rs rewards counter = {rs_rew}\n"
                    f"    cs view event = {_ev(cs_view)}\n"
                    f"    rs view event = {_ev(rs_view)}\n"
                    f"    cs view keys = {sorted(cs_view) if isinstance(cs_view, dict) else cs_view}\n"
                )
            marker = (
                ""
                if cs_rew == rs_rew
                else "  <-- DIVERGE"
                if first_div and step == first_div[0]
                else "  x"
            )
            print(
                f"[{step:04d}] {cs_snap.phase:12s} rew cs={cs_rew} rs={rs_rew} "
                f"gold cs={cs_snap.gold} rs={Snap.of(rs_view, 'rs').gold} "
                f"last={last_action}{marker}"
            )

            if first_div is not None:
                tail += 1
                if tail > 6:
                    break

            if cs_dec == "game_over":
                break

            choices = enumerate_choices(g, cs_view)
            if not choices:
                print(f"  (no choices for cs decision {cs_dec!r}) — stop")
                break

            if cs_snap.phase != "combat":
                # Backends carry `set_player` (used by act23_state_parity); the
                # GameBackend Protocol doesn't declare it, so go through Any.
                gany: Any = g
                rany: Any = r
                gany.set_player(hp=INVINCIBLE_HP, max_hp=INVINCIBLE_HP)
                rany.set_player(hp=INVINCIBLE_HP, max_hp=INVINCIBLE_HP)

            pool = list(choices)
            advanced = False
            while pool:
                idx = rng.randrange(len(pool))
                verb, kw = pool.pop(idx)
                nxt_cs = g.act(verb, **kw)
                nxt_rs = r.act(verb, **kw)
                if _is_err(nxt_cs) and _is_err(nxt_rs):
                    continue
                if _is_err(nxt_cs) or _is_err(nxt_rs):
                    print(
                        f"  act_reject divergence: {verb} {kw} cs_err={_is_err(nxt_cs)} rs_err={_is_err(nxt_rs)}"
                    )
                    advanced = True
                    cs_view, rs_view = nxt_cs, nxt_rs
                    last_action = f"{verb}{kw}"
                    break
                cs_view, rs_view = nxt_cs, nxt_rs
                last_action = f"{verb}{kw}"
                advanced = True
                break
            if not advanced:
                print("  (all choices both-rejected) — stop")
                break
        if first_div is None:
            print(f"\n✓ rewards counter IDENTICAL through {max_steps} steps")
            return 0
        return 1
    finally:
        for b in (g, r):
            try:
                b.close()
            except Exception:
                pass


def main() -> int:
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    ap.add_argument("--character", default="Ironclad")
    ap.add_argument("--seed", default="1000")
    ap.add_argument("--ascension", type=int, default=0)
    ap.add_argument("--max-steps", type=int, default=320)
    args = ap.parse_args()
    assert_wheel_fresh()
    return trace(args.seed, args.character, args.ascension, args.max_steps)


if __name__ == "__main__":
    sys.exit(main())
