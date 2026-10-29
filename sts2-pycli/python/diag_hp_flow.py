"""HP-flow attribution: WHERE the rung-3.5 real-DLL loses its ~15 arrival HP.

The deck/HP-arrival diagnostic (`diag_deck_hp_arrival.py`) established that
losing real-DLL runs arrive at every act boss ~13-16 HP LOWER than the
engine-world with a ~matched deck -> the residual is HP attrition, not
deck-building. This script splits that attrition into its *sources* so the
actual lever is named, decomposing each act's HP budget into:

- **combat HP lost** (sum of per-fight ``hp_lost``; robust to the tracer's
  in-combat-pending-choice fragmentation because the sum telescopes),
- **non-combat net HP** (= net HP change over the act + combat loss = all
  rest-heal / event / potion / relic / max-hp recovery folded together),
- **pathing** (fights + elites walked into, counted by distinct combat
  floors so a fragmented fight isn't double counted).

If real-DLL's *combat loss* is the bigger adverse delta -> the lever is
combat (search line quality / mechanical bleed). If its *non-combat
recovery* is lower -> the lever is the meta layer (rest/heal/event/potion
management). If it walks into *more elites* -> the lever is map routing.

The two worlds are string-seed-matched, so per-act sums are apples-to-apples
even though the two tracers use different room-type casing and the DLL floor
numbering is act-offset (we key on the reliable ``act`` field, never floor).

Usage::

    python python/diag_hp_flow.py logs/diag/engine logs/diag/realdll
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from statistics import mean
from typing import Any

# Room-type dialect: engine emits lowercase, the DLL wire TitleCase; "Map" is
# a pure navigation surface (no HP flow) we skip when counting rooms.
_ROOM_ALIASES = {
    "restsite": "rest_site",
    "rest_site": "rest_site",
    "monster": "monster",
    "elite": "elite",
    "boss": "boss",
    "event": "event",
    "shop": "shop",
    "treasure": "treasure",
    "map": "map",
}


def _norm_room(rt: Any) -> str:
    return _ROOM_ALIASES.get(str(rt or "").lower(), str(rt or "").lower())


def load(d: Path) -> dict[int, list[dict[str, Any]]]:
    out: dict[int, list[dict[str, Any]]] = {}
    for p in sorted(d.glob("seed*.jsonl")):
        try:
            seed = int(p.stem[len("seed") :])
        except ValueError:
            continue
        out[seed] = [json.loads(x) for x in p.read_text(encoding="utf-8").splitlines() if x.strip()]
    return out


def per_act(trace: list[dict[str, Any]]) -> dict[int, dict[str, Any]]:
    """Attribute HP flow per act for one run.

    Returns ``{act: {combat_loss, elite_loss, hp_entry, hp_exit, n_fights,
    n_elites, reached_boss}}``. ``hp_entry`` is HP at the first room of the
    act; ``hp_exit`` is HP at the act's boss room if reached, else HP at the
    deepest room of the act (arrival snapshot).
    """
    acts: dict[int, dict[str, Any]] = {}

    def slot(a: int) -> dict[str, Any]:
        return acts.setdefault(
            a,
            {
                "combat_loss": 0,
                "elite_loss": 0,
                "hp_entry": None,
                "hp_exit": None,
                "_last_floor": None,
                "fight_floors": set(),
                "elite_floors": set(),
                "reached_boss": False,
            },
        )

    for r in trace:
        ev = r.get("ev")
        a = r.get("act")
        if not isinstance(a, int):
            continue
        s = slot(a)
        if ev == "room":
            rt = _norm_room(r.get("room_type"))
            if rt == "map":
                continue  # navigation, carries HP but duplicates a floor
            hp = r.get("hp")
            if s["hp_entry"] is None and hp is not None:
                s["hp_entry"] = hp
            f = r.get("floor")
            if isinstance(f, int) and (s["_last_floor"] is None or f >= s["_last_floor"]):
                if hp is not None:
                    s["hp_exit"] = hp
                s["_last_floor"] = f
            if rt == "boss":
                s["reached_boss"] = True
                if hp is not None:
                    s["hp_exit"] = hp  # boss-room HP is the canonical arrival
        elif ev == "combat":
            lost = r.get("hp_lost") or 0
            s["combat_loss"] += lost
            f = r.get("floor")
            is_elite = bool(r.get("is_elite"))
            if isinstance(f, int):
                s["fight_floors"].add(f)
                if is_elite:
                    s["elite_floors"].add(f)
            if is_elite:
                s["elite_loss"] += lost

    for s in acts.values():
        s["n_fights"] = len(s["fight_floors"])
        s["n_elites"] = len(s["elite_floors"])
        # non-combat net HP recovered over the act (heals+ minus event dmg-),
        # everything that isn't fight damage. entry/exit both present or None.
        if s["hp_entry"] is not None and s["hp_exit"] is not None:
            s["noncombat_net"] = (s["hp_exit"] - s["hp_entry"]) + s["combat_loss"]
        else:
            s["noncombat_net"] = None
    return acts


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("engine_dir", type=Path)
    ap.add_argument("realdll_dir", type=Path)
    args = ap.parse_args()

    eng = load(args.engine_dir)
    dll = load(args.realdll_dir)
    seeds = sorted(set(eng) & set(dll))
    print(f"matched seeds: {len(seeds)}\n")

    eng_acts = {s: per_act(eng[s]) for s in seeds}
    dll_acts = {s: per_act(dll[s]) for s in seeds}

    print("=== per-act HP-flow attribution (seeds reaching the act in BOTH worlds) ===")
    print("  combat_loss = HP bled in fights; noncombat = rest/event/potion recovery (net)")
    for act in (1, 2, 3):
        rows = [
            (eng_acts[s][act], dll_acts[s][act])
            for s in seeds
            if act in eng_acts[s] and act in dll_acts[s]
        ]
        # require both worlds to have entered the act with a usable HP bracket
        rows = [
            (e, d)
            for e, d in rows
            if e["noncombat_net"] is not None and d["noncombat_net"] is not None
        ]
        if not rows:
            print(f"\n  act{act}: no seeds reached in both")
            continue
        n = len(rows)

        def m(side: int, key: str, _rows: list = rows) -> float:
            return mean(r[side][key] for r in _rows)

        print(f"\n  act{act}  (n={n})")
        print(
            f"    combat HP lost:      engine {m(0, 'combat_loss'):5.1f}   "
            f"real-DLL {m(1, 'combat_loss'):5.1f}   d={m(1, 'combat_loss') - m(0, 'combat_loss'):+5.1f}"
        )
        print(
            f"      of which elites:   engine {m(0, 'elite_loss'):5.1f}   "
            f"real-DLL {m(1, 'elite_loss'):5.1f}   d={m(1, 'elite_loss') - m(0, 'elite_loss'):+5.1f}"
        )
        print(
            f"    non-combat recovery: engine {m(0, 'noncombat_net'):5.1f}   "
            f"real-DLL {m(1, 'noncombat_net'):5.1f}   d={m(1, 'noncombat_net') - m(0, 'noncombat_net'):+5.1f}"
        )
        print(
            f"    fights walked into:  engine {m(0, 'n_fights'):5.1f}   "
            f"real-DLL {m(1, 'n_fights'):5.1f}   d={m(1, 'n_fights') - m(0, 'n_fights'):+5.1f}"
        )
        print(
            f"      elites:            engine {m(0, 'n_elites'):5.2f}   "
            f"real-DLL {m(1, 'n_elites'):5.2f}   d={m(1, 'n_elites') - m(0, 'n_elites'):+5.2f}"
        )
        # per-act HP budget delta and its attribution
        budget_d = (m(1, "noncombat_net") - m(1, "combat_loss")) - (
            m(0, "noncombat_net") - m(0, "combat_loss")
        )
        combat_share = -(m(1, "combat_loss") - m(0, "combat_loss"))
        recov_share = m(1, "noncombat_net") - m(0, "noncombat_net")
        print(
            f"    -> act HP-budget delta (real-DLL vs engine): {budget_d:+5.1f}  "
            f"[combat {combat_share:+.1f} + recovery {recov_share:+.1f}]"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
