#!/usr/bin/env python3
"""R3.1.4: Neow ancient_choice parity between scraped corpus runs and
the Rust port of `Neow.GenerateInitialOptions`.

Each captured v0.103.2 single-player run begins at
`first_room_type='ancient'` with a Neow event offering 2 positive
relics + 1 curse. The scraped `.run` files preserve this as
`map_point_history[0][0].player_stats[0].ancient_choice` — a 3-item
list of `{TextKey, was_chosen, ...}` dicts where `TextKey` is the
SCREAMING_SNAKE_CASE relic id.

For each run we:
  1. Build a Rust RunManager from the same C#-canonical string seed
     with `neow_start=True`, which rolls the offers via the
     `EventModel.cs:193` formula (master_seed + 1uL NetId + hash("NEOW")).
  2. Read `RunManager.neow_offers()` → 3-tuple of SCREAMING_SNAKE_CASE
     ids.
  3. Diff order-exactly against the scraped 3-tuple.

Bead `sts2-rs-k8z6m` (R3.1.4 under `sts2-rs-nr8l4`).
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import sts2_rs
from run_files import codex_root, glob_runs, load_run

# Vendored, clone-portable corpus (sts2-rs/data/run-corpus/, git-lfs).
PRIMARY_ROOT = Path(__file__).resolve().parents[2] / "sts2-rs/data/run-corpus/files"
CHAR_ALIASES = {
    "the-ironclad": ["the-ironclad", "ironclad"],
    "the-silent": ["the-silent", "silent"],
    "the-defect": ["the-defect", "defect"],
    "the-necrobinder": ["the-necrobinder", "necrobinder"],
    "the-regent": ["the-regent", "regent"],
}
CHARACTER_RUST_NAME = {
    "the-ironclad": "Ironclad",
}


def iter_v103_neow_runs(character_dir: str, use_codex: bool):
    """Yield (seed, ascension, scraped_offers_3tuple) for each usable
    v0.103.2 single-player run whose first room is an ancient with a
    full 3-relic ancient_choice block."""
    roots = [PRIMARY_ROOT] + ([codex_root()] if use_codex else [])
    aliases = CHAR_ALIASES.get(character_dir, [character_dir])
    files: list[Path] = []
    for root in roots:
        for alias in aliases:
            files.extend(glob_runs(root / alias))
    for f in files:
        try:
            d = load_run(f)
        except (OSError, json.JSONDecodeError):
            continue
        if d.get("build_id") != "v0.103.2":
            continue
        if len(d.get("players", [])) != 1:
            continue
        if d.get("modifiers"):
            # Modifier branches in Neow.GenerateInitialOptions are a
            # separate code path (Neow.cs:282-296); skip until ported.
            continue
        mph = d.get("map_point_history", [[]])[0]
        if not mph or mph[0].get("map_point_type") != "ancient":
            continue
        ps = mph[0].get("player_stats", [])
        if not (ps and ps[0].get("ancient_choice")):
            continue
        offers = [c["TextKey"] for c in ps[0]["ancient_choice"]]
        if len(offers) != 3:
            continue
        yield d["seed"], d.get("ascension", 0), tuple(offers)


FIVE_RELIC_GAP_TEXT_KEYS = frozenset(
    {"HEFTY_TABLET", "NEOWS_BONES", "PHIAL_HOLSTER", "WINGED_BOOTS", "NEOWS_TALISMAN"}
)
"""SCREAMING_SNAKE_CASE TextKeys for the 5 `NeowRelic` variants that
have no `RelicId` mapping yet — `resolve_neow_relic` routes them to
placeholders (Pomander / CursedPearl / LeafyPoultice). See
sts2-core/src/neow_offers.rs module docs for the gap list."""


def _iter_v103_neow_runs_with_choice(character_dir: str, use_codex: bool):
    """Like `iter_v103_neow_runs`, but also yields `(chosen_idx,
    chosen_text_key)` extracted from `ancient_choice[*].was_chosen`.
    Skips runs whose ancient_choice has no `was_chosen=True` flag set
    (incomplete / early-aborted runs)."""
    roots = [PRIMARY_ROOT] + ([codex_root()] if use_codex else [])
    aliases = CHAR_ALIASES.get(character_dir, [character_dir])
    files: list[Path] = []
    for root in roots:
        for alias in aliases:
            files.extend(glob_runs(root / alias))
    for f in files:
        try:
            d = load_run(f)
        except (OSError, json.JSONDecodeError):
            continue
        if d.get("build_id") != "v0.103.2" or d.get("modifiers") or len(d.get("players", [])) != 1:
            continue
        mph_outer = d.get("map_point_history", [])
        if not mph_outer or not mph_outer[0]:
            continue
        first = mph_outer[0][0]
        if first.get("map_point_type") != "ancient":
            continue
        ps = first.get("player_stats", [])
        if not (ps and ps[0].get("ancient_choice")):
            continue
        ac = ps[0]["ancient_choice"]
        if len(ac) != 3:
            continue
        chosen_idx = next((i for i, c in enumerate(ac) if c.get("was_chosen")), None)
        if chosen_idx is None:
            continue
        yield d["seed"], d.get("ascension", 0), tuple(c["TextKey"] for c in ac), chosen_idx


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--character", default="the-ironclad", choices=CHAR_ALIASES.keys())
    parser.add_argument("--codex", action="store_true", help="include spire-codex corpus")
    parser.add_argument("--limit", type=int, default=0, help="stop after N runs (0 = all)")
    parser.add_argument("--show-mismatches", type=int, default=20)
    parser.add_argument(
        "--apply-choice",
        action="store_true",
        help="also drive take_action(EventChoice{idx:was_chosen}) → "
        "PickRelicReward and assert the chosen relic lands in state.relics. "
        "Only runs with `was_chosen=True` on one of the 3 ancient_choice "
        "options are scored; the rest are skipped.",
    )
    args = parser.parse_args()

    rust_char = CHARACTER_RUST_NAME.get(args.character)
    if rust_char is None:
        print(f"character {args.character} not yet wired in sts2-py", file=sys.stderr)
        return 2

    order_hits = set_hits = total = 0
    choice_exact = choice_five_gap = choice_total = 0
    mismatches: list[tuple[str, int, tuple, tuple]] = []
    choice_mismatches: list[tuple[str, int, str, str]] = []

    iterator = (
        _iter_v103_neow_runs_with_choice(args.character, args.codex)
        if args.apply_choice
        else (
            (seed, asc, exp, None)
            for (seed, asc, exp) in iter_v103_neow_runs(args.character, args.codex)
        )
    )
    for seed, asc, exp, chosen_idx in iterator:
        rm = sts2_rs.RunManager(string_seed=seed, character=rust_char, neow_start=True)
        rust = rm.neow_offers()
        if rust is None:
            continue
        total += 1
        if tuple(rust) == exp:
            order_hits += 1
            set_hits += 1
        elif sorted(rust) == sorted(exp):
            set_hits += 1
            mismatches.append((seed, asc, tuple(rust), exp))
        else:
            mismatches.append((seed, asc, tuple(rust), exp))
            if args.limit and total >= args.limit:
                break
            continue
        if args.apply_choice and tuple(rust) == exp and chosen_idx is not None:
            choice_total += 1
            chosen_text_key = exp[chosen_idx]
            if rm.take_action({"action": "event_choice", "idx": chosen_idx}) != "advanced":
                choice_mismatches.append((seed, chosen_idx, chosen_text_key, "choice not Advanced"))
            elif rm.phase() == "card_reward":
                if rm.take_action({"action": "pick_relic_reward"}) != "advanced":
                    choice_mismatches.append(
                        (seed, chosen_idx, chosen_text_key, "pick_relic_reward not Advanced")
                    )
                elif f"RELIC.{chosen_text_key}" in rm.relics():
                    choice_exact += 1
                elif chosen_text_key in FIVE_RELIC_GAP_TEXT_KEYS:
                    choice_five_gap += 1
                else:
                    choice_mismatches.append(
                        (seed, chosen_idx, chosen_text_key, f"relics={rm.relics()}")
                    )
            elif rm.phase() == "map_choice":
                # Resolver ran but queued no relic — shouldn't happen for
                # any of the 3 Neow options at this pin.
                choice_mismatches.append((seed, chosen_idx, chosen_text_key, "no relic queued"))
            else:
                choice_mismatches.append(
                    (seed, chosen_idx, chosen_text_key, f"unexpected phase {rm.phase()}")
                )
        if args.limit and total >= args.limit:
            break

    print(f"== {order_hits}/{total} order-exact match")
    print(f"== {set_hits}/{total} set-match")
    if args.apply_choice:
        print(f"== {choice_exact}/{choice_total} chosen relic in state.relics (exact)")
        print(f"== {choice_five_gap}/{choice_total} 5-relic-gap placeholder (known)")
        if choice_mismatches:
            print(f"\nChoice mismatches ({len(choice_mismatches)}):")
            for seed, idx, tk, why in choice_mismatches[: args.show_mismatches]:
                print(f"  seed={seed} idx={idx} chosen={tk}: {why}")
    if mismatches:
        print(f"\nFirst {min(args.show_mismatches, len(mismatches))} offer mismatches:")
        for seed, asc, rust, exp in mismatches[: args.show_mismatches]:
            print(f"  seed={seed} asc={asc}")
            print(f"    rust = {rust}")
            print(f"    exp  = {exp}")
    ok = order_hits == total
    if args.apply_choice:
        ok = ok and len(choice_mismatches) == 0
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
