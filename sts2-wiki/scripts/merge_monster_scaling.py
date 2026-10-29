#!/usr/bin/env python3
"""Backfill ascension-scaling fields into per-entity monster JSON files.

The regex extractor (`scripts/extract_monsters.py`) is the authoritative
source for ascension-scaled values pulled from `AscensionHelper.GetValueIfAscension`
calls in the decompiled C# source. The per-entity JSON files in
`data/{version}/monsters/*.json` came from an earlier LLM run that didn't
yet capture per-ascension scaling and now override the aggregate at
generate time (see scripts/generate_monsters.py).

This script reads the aggregate `monsters.json` and merges the new
scaling fields (`min_hp_base`, `max_hp_base`, `hp_ascension`,
`damage_base`, `hits_base`, `amount_base`, `ascension`) plus the
``base/asc`` effect strings (e.g. "Deal 12/13 damage") into the
per-entity files, while preserving the LLM's narrative additions
(``move_pattern`` as prose, ``notes``, multi-effect descriptions the
regex couldn't reach).
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

# Scaling fields copied from each aggregate intent onto the matching per-entity intent.
INTENT_SCALING_FIELDS = ("damage_base", "hits_base", "amount_base", "ascension")


def merge_intents(per_intents: list[dict], agg_intents: list[dict]) -> bool:
    """Add scaling fields onto per-entity intents (in place).

    Matching is by position; the LLM and regex see the same intent list
    in the same order. Mismatched ``type`` is skipped so we never paste
    a damage scaling onto a block intent.
    """
    changed = False
    for i, per in enumerate(per_intents):
        if i >= len(agg_intents):
            break
        agg = agg_intents[i]
        if agg.get("type") != per.get("type"):
            continue
        for field in INTENT_SCALING_FIELDS:
            if field in agg and per.get(field) != agg[field]:
                per[field] = agg[field]
                changed = True
    return changed


def merge_effects(per_effects: list[str], agg_effects: list[str]) -> tuple[list[str], bool]:
    """Inject scaling forms ("base/asc") into per-entity effect strings.

    The aggregate's effects come from C# source so they reliably mark
    scaling with a slash, but the LLM-authored per-entity strings often
    have nicer phrasing ("Add 3 Infection cards to player's discard
    pile" vs "Add Infection to discard"). Replace per-entity effect at
    index i only when the aggregate has scaling there and per-entity
    doesn't — preserving LLM nuance everywhere else.
    """
    out = list(per_effects)
    changed = False
    for i in range(min(len(out), len(agg_effects))):
        if "/" in agg_effects[i] and "/" not in out[i]:
            out[i] = agg_effects[i]
            changed = True
    return out, changed


def merge_monster(per: dict, agg: dict) -> bool:
    """Merge aggregate scaling info into a per-entity monster dict (in place)."""
    changed = False

    for field in ("min_hp_base", "max_hp_base", "hp_ascension"):
        if field in agg and per.get(field) != agg[field]:
            per[field] = agg[field]
            changed = True

    agg_moves = {mv["id"]: mv for mv in agg.get("moves", [])}
    for mv in per.get("moves", []):
        agg_mv = agg_moves.get(mv["id"])
        if not agg_mv:
            continue
        if merge_intents(mv.get("intents", []), agg_mv.get("intents", [])):
            changed = True
        new_effects, eff_changed = merge_effects(mv.get("effects", []), agg_mv.get("effects", []))
        if eff_changed:
            mv["effects"] = new_effects
            changed = True

    return changed


def main() -> None:
    parser = argparse.ArgumentParser(description="Backfill monster ascension scaling")
    parser.add_argument("data_dir", help="Path to versioned data directory (data/v0.103.2)")
    args = parser.parse_args()

    data_dir = Path(args.data_dir)
    aggregate_path = data_dir / "monsters.json"
    per_entity_dir = data_dir / "monsters"

    aggregate = {m["class_name"]: m for m in json.loads(aggregate_path.read_text())}

    updated = 0
    skipped_no_match = 0
    for path in sorted(per_entity_dir.glob("*.json")):
        per = json.loads(path.read_text())
        cname = per.get("class_name") or path.stem
        agg = aggregate.get(cname)
        if not agg:
            skipped_no_match += 1
            continue
        if merge_monster(per, agg):
            path.write_text(json.dumps(per, indent=2) + "\n")
            updated += 1

    print(
        f"Updated {updated} per-entity files (skipped {skipped_no_match} with no aggregate match)"
    )


if __name__ == "__main__":
    main()
