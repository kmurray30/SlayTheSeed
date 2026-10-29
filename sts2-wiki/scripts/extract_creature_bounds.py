#!/usr/bin/env python3
"""Extract each creature's authored `%Bounds` box from the STS2 PCK.

`NCombatRoom.PositionEnemies` spaces the enemy row by `Visuals.Bounds.Size.X`,
and `NCreatureVisuals._Ready` resolves that box as
``GetNode<Control>("%Bounds")`` inside the creature's own scene —
``scenes/creature_visuals/<slug>.tscn``. Its rect is a plain Godot Control rect:
the parent is the creature root, a `Node2D`, whose anchorable rect is empty, so
the anchors contribute nothing and the box is just::

    w = offset_right  - offset_left
    h = offset_bottom - offset_top

with the creature's origin at (0, 0) — on the ground line, which is why every
authored `offset_top` is negative and `offset_bottom` is usually zero.

Both edges of that box are what this emits. `creature_scales.json` has carried
`scale` and `bounds_h` since the workbench started drawing rigs, but never the
WIDTH, so the row layout had to estimate it from the drawn rig times a
calibrated 1.2 margin (`BOUNDS_MARGIN` in `scene.ts`). With `bounds_w` the row
is the game's, exactly (GH #607).

`bounds_h` used to be hand-derived, and wrongly: 102 of the 123 entries carried
`2 * abs(offset_top)` — a box with half the creature's hitbox BELOW the ground
line, which no scene authors — and fourteen more carried a value near it but on
no formula at all. It now comes off the same rect as the width (GH #611). The
correction is a halving, and it is corroborated on both axes: against the rig
each creature is DRAWN at, the authored box runs a median 1.07x the drawn height
and 1.09x the drawn width, where the old `bounds_h` ran 2.12x the height.

That the anchors contribute nothing is measured, not assumed. Thirty-eight of
the boxes are authored with a full-rect preset (anchors 0,0,1,1); if their
parent's anchorable rect were the viewport rather than empty, those thirty-eight
would come out ~1920 px wider and taller than the other eighty-five relative to
the rig they draw. They do not — 1.18x/1.10x against 1.07x/1.04x — which is what
`Control::get_parent_anchorable_rect` predicts for a `Node2D` parent, since
`CanvasItem::get_anchorable_rect` returns an empty rect for everything that is
not itself a `Control`.

Merging, not rewriting: `creature_scales.json` carries three deliberate
hand-overrides of the authored `scale` (the Mawler, the Skulking Colony and the
Test Subject are drawn at a size their own scene does not ask for) and eleven
entries with no `%Bounds` box to extract at all. ``--merge`` writes `bounds_w`
and `bounds_h` and leaves every other field alone.

Usage (from `sts2-wiki/`)::

    uv run python -m scripts.extract_creature_bounds \\
        ../sts2_binaries/v0.107.1/SlayTheSpire2.pck \\
        site/public/images/creature_scales.json --merge
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from scripts.extract_bg_layers import _f, _vec2, parse_scene
from scripts.extract_rooms import make_reader, read_pck_index

CREATURE_PREFIX = "scenes/creature_visuals/"


def creature_bounds(pck_path: str) -> dict[str, dict[str, float]]:
    """``{slug: {"scale", "bounds_w", "bounds_h"}}`` for every creature scene."""
    index = read_pck_index(pck_path)
    read = make_reader(pck_path, index)

    out: dict[str, dict[str, float]] = {}
    for path in sorted(p for p in index if p.startswith(CREATURE_PREFIX) and p.endswith(".tscn")):
        _, nodes = parse_scene((read(path) or b"").decode("utf-8", "replace"))
        # `%Bounds` and `%Visuals` are `unique_name_in_owner`, so the names are
        # unique within the scene and a flat scan is enough.
        bounds = next((n for n in nodes if n.name == "Bounds"), None)
        visuals = next((n for n in nodes if n.name == "Visuals"), None)
        if bounds is None:
            continue
        entry: dict[str, float] = {
            "bounds_w": round(_f(bounds, "offset_right") - _f(bounds, "offset_left"), 2),
            "bounds_h": round(_f(bounds, "offset_bottom") - _f(bounds, "offset_top"), 2),
        }
        if visuals is not None:
            # Sign is a facing flip, not a size — the consumers all take the
            # magnitude, and `creature_scales.json` keeps the signed value.
            entry["scale"] = _vec2(visuals, "scale", (1.0, 1.0))[0]
        out[Path(path).stem] = entry
    return out


def merge(existing: dict[str, Any], fresh: dict[str, dict[str, float]]) -> dict[str, Any]:
    """Write both authored `%Bounds` edges over what is there, keeping `scale`.

    `scale` is the one field a scene does not always win: three creatures are
    drawn at a hand-picked size their own scene does not ask for (see the module
    docstring), so it is preserved wherever the table already has one. The box
    is the scene's — an entry the table already carries gets its `bounds_w` AND
    `bounds_h` replaced by the authored rect.

    An entry with no `%Bounds` to extract keeps whatever it has: the eight keys
    that are rig slugs rather than scene names, and the three scenes that ship
    no `%Bounds` node (`battleworn_dummy`, both Kaiser Crab boss pages). Nine of
    those eleven already carry `bounds_h: null`; the two that do not (`door`,
    `doormaker`) are room furniture, never combatants.
    """
    out = {k: dict(v) for k, v in existing.items()}
    for slug, entry in fresh.items():
        if slug in out:
            out[slug]["bounds_w"] = entry["bounds_w"]
            out[slug]["bounds_h"] = entry["bounds_h"]
        else:
            # Nothing to preserve for a creature the table has never carried,
            # so it gets the authored row whole.
            out[slug] = dict(entry)
    return {k: out[k] for k in sorted(out)}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pck_path")
    parser.add_argument("out_path")
    parser.add_argument(
        "--merge",
        action="store_true",
        help="write the authored box into an existing creature_scales.json,"
        " keeping its hand-overridden `scale` values, instead of writing a fresh table",
    )
    args = parser.parse_args()

    fresh = creature_bounds(args.pck_path)
    out = Path(args.out_path)
    if args.merge:
        existing = json.loads(out.read_text(encoding="utf-8")) if out.is_file() else {}
        payload: dict[str, Any] = merge(existing, fresh)
        missing = sorted(k for k, v in payload.items() if "bounds_w" not in v)
    else:
        payload = {k: fresh[k] for k in sorted(fresh)}
        missing = []
    out.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"{len(fresh)} creature scenes -> {out} ({len(payload)} entries)")
    if missing:
        print(f"  no %Bounds box to extract, left as-is: {', '.join(missing)}")


if __name__ == "__main__":
    main()
