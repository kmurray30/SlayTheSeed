#!/usr/bin/env python3
"""R2: diff a scraped corpus run vs a fresh Rust RunManager built from
the same string seed.

For one v0.103.2 Ironclad A0 winning run from
``../sts2_run_scraper/data/files/the-ironclad/``, builds the Rust
state via ``sts2_rs.RunManager(string_seed=...)`` and reports every
observable mismatch (initial HP, gold, relics, deck, first room
type / encounter / reward set, etc.).

The goal is NOT to make it match yet — it's to enumerate the
divergence points so we can file beads to burn them down.

Bead context: sts2-rs-8xlqe (corpus replay follow-up to PyRunManager).
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import sts2_rs
from run_files import glob_runs, load_run

# Vendored, clone-portable corpus (sts2-rs/data/run-corpus/, git-lfs).
SCRAPER_ROOT = Path(__file__).resolve().parents[2] / "sts2-rs/data/run-corpus/files"


def find_targets(build_id: str, character_dir: str, ascension: int, n: int):
    out = []
    for f in glob_runs(SCRAPER_ROOT / character_dir):
        try:
            d = load_run(f)
        except Exception:
            continue
        if d.get("build_id") != build_id:
            continue
        if not d.get("win"):
            continue
        if d.get("was_abandoned"):
            continue
        if d.get("ascension", 0) != ascension:
            continue
        out.append((f, d))
        if len(out) >= n:
            break
    return out


def summarise_run_file(d: dict) -> dict:
    p = d["players"][0]
    first_room = d["map_point_history"][0][0]
    return {
        "seed": d["seed"],
        "ascension": d["ascension"],
        "build_id": d["build_id"],
        "schema_version": d.get("schema_version"),
        "character": p["character"],
        "max_potion_slot_count": p.get("max_potion_slot_count"),
        "first_room_type": first_room["map_point_type"],
        "first_room_model_id": first_room.get("rooms", [{}])[0].get("model_id"),
        "first_room_initial_hp": first_room["player_stats"][0].get("current_hp"),
        "first_room_initial_gold": first_room["player_stats"][0].get("current_gold"),
        "first_room_max_hp": first_room["player_stats"][0].get("max_hp"),
        "first_room_ancient_offers": [
            c["TextKey"] for c in first_room["player_stats"][0].get("ancient_choice", [])
        ],
        "first_room_ancient_chosen": next(
            (
                c["TextKey"]
                for c in first_room["player_stats"][0].get("ancient_choice", [])
                if c.get("was_chosen")
            ),
            None,
        ),
        "neow_cards_gained": [
            c["id"] for c in first_room["player_stats"][0].get("cards_gained", [])
        ],
        "final_deck_size": len(p["deck"]),
        "final_relics_in_order": [r["id"] for r in p["relics"]],
        "acts_completed": len(d["acts"]),
    }


def summarise_rust_state(rm) -> dict:
    return {
        "phase": rm.phase(),
        "current_hp": rm.current_hp,
        "max_hp": rm.max_hp,
        "gold": rm.gold,
        "act_index": rm.act_index,
        "relics": rm.relics(),
        "deck_size": len(rm.master_deck()),
        "deck_first_5": rm.master_deck()[:5],
        "current_room": rm.current_room(),
        "map_coord": rm.map_coord(),
        "available_actions_count": len(rm.available_actions()),
        "available_actions_first_3": rm.available_actions()[:3],
    }


def diff_report(scraped: dict, rust: dict) -> list[str]:
    """Report observable divergences. This is the R2 deliverable."""
    lines: list[str] = []

    def add(label: str, expected, got, note: str = ""):
        ok = expected == got
        marker = "  OK " if ok else "  ✗  "
        line = f"{marker}{label:<35}  expected={expected!r}  got={rust_repr(got)}"
        if note:
            line += f"   [{note}]"
        lines.append(line)

    add(
        "initial_hp",
        scraped["first_room_initial_hp"],
        rust["current_hp"],
        "first_room player_stats.current_hp is post-Neow; Rust is pre-Neow",
    )
    add("max_hp", scraped["first_room_max_hp"], rust["max_hp"])
    add(
        "initial_gold",
        scraped["first_room_initial_gold"],
        rust["gold"],
        "scraped is post-Neow snapshot; Rust starts at content-set default",
    )
    add(
        "first_room_type",
        scraped["first_room_type"],
        rust["current_room"]["room_type"] if rust["current_room"] else None,
    )
    add(
        "first_room_phase",
        "event",
        rust["phase"],
        "C# enters Neow as an Event/Ancient; Rust pin has no Neow → goes straight to map_choice",
    )
    add("starter_relic_burning_blood", True, "BurningBlood" in rust["relics"])
    add(
        "starter_relic_only",
        ["RELIC.BURNING_BLOOD"],
        rust["relics"],
        "before Neow gives extra relic",
    )
    add("starter_deck_size", 10, rust["deck_size"])
    return lines


def rust_repr(v):
    """Repr that doesn't show full lists when too long."""
    if isinstance(v, list) and len(v) > 6:
        return f"[{', '.join(repr(x) for x in v[:3])}, … +{len(v) - 3} more]"
    return repr(v)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--build-id", default="v0.103.2")
    ap.add_argument("--character-dir", default="the-ironclad")
    ap.add_argument("--ascension", type=int, default=0)
    ap.add_argument("-n", "--num", type=int, default=1)
    args = ap.parse_args()

    targets = find_targets(args.build_id, args.character_dir, args.ascension, args.num)
    if not targets:
        print(f"no v{args.build_id} {args.character_dir} A{args.ascension} wins found")
        return 1

    for path, d in targets:
        scraped = summarise_run_file(d)
        print("=" * 72)
        print(f"target: {path}")
        print(
            f"  seed={scraped['seed']}  build={scraped['build_id']}  schema={scraped['schema_version']}"
        )
        print(f"  character={scraped['character']}  ascension={scraped['ascension']}")
        print(
            f"  acts_completed={scraped['acts_completed']}  final_deck_size={scraped['final_deck_size']}"
        )
        print()
        print("--- scraped first-room (Neow) snapshot ---")
        print(
            f"  room_type={scraped['first_room_type']}  model_id={scraped['first_room_model_id']}"
        )
        print(
            f"  pre-Neow HP/MaxHP/Gold = {scraped['first_room_initial_hp']}"
            f" / {scraped['first_room_max_hp']} / {scraped['first_room_initial_gold']}"
        )
        print(f"  ancient_offers={scraped['first_room_ancient_offers']}")
        print(f"  ancient_chosen={scraped['first_room_ancient_chosen']}")
        print(f"  neow_cards_gained={scraped['neow_cards_gained']}")
        print()

        # Build Rust state with the same string seed.
        rm = sts2_rs.RunManager(string_seed=scraped["seed"], character="Ironclad")
        rust = summarise_rust_state(rm)
        print("--- Rust initial state from same string seed ---")
        print(f"  effective int seed: {rm.seed}")
        print(
            f"  phase={rust['phase']}  hp={rust['current_hp']}/{rust['max_hp']}  gold={rust['gold']}"
        )
        print(f"  relics={rust['relics']}")
        print(f"  deck_size={rust['deck_size']}  deck_first_5={rust['deck_first_5']}")
        print(f"  current_room={rust['current_room']}")
        print(f"  available_actions[0:3]={rust['available_actions_first_3']}")
        print()
        print("--- DIFF ---")
        for line in diff_report(scraped, rust):
            print(line)
        print()

    return 0


if __name__ == "__main__":
    sys.exit(main())
