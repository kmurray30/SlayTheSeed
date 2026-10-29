#!/usr/bin/env python3
"""Extract the authored `Marker2D` slots of every encounter that has a scene.

Twenty-four encounters declare `HasScene => true`, and for those the game does
NOT run its row algorithm at all — `NCombatRoom.PositionCreaturesWithSlots`
reads each creature's position straight off a `Marker2D` named by its
`Creature.SlotName` in `scenes/encounters/<id>.tscn`::

    ((Control)creature).GlobalPosition =
        EncounterSlots.GetNode<Marker2D>(slotName).GlobalPosition;

That is also the ONLY correct answer for a mid-fight spawn: every encounter that
spawns has slots, and `NCombatRoom.AddCreature` moves a new creature only when
it carries a `SlotName`. So an Ovicopter's eggs, a Living Fog's bombs and a
Phrog Parasite's wrigglers are placed by this table or not at all.

Three things come out of the PCK and the decomp together:

* the marker POSITIONS, in scene px relative to the 1920x1080 scene's centre —
  `NCombatRoom` shifts the instanced scene by ``-Vector2(1920, 1080) * 0.5f``,
  which is the same frame `scene.ts` lays the row out in;
* the `Slots` ORDER, off `EncounterModel.Slots` in the decompiled C#. Marker
  declaration order is not it: the Fabricator's scene declares
  ``bot1, fabricator, bot2, bot3, bot4`` where its model orders
  ``bot1, bot2, fabricator, bot3, bot4``, and the order is what
  `GetNextSlot` hands the next spawn;
* the encounter's ROSTER, off the `ModelDb.Monster<T>()` calls in the same
  class, as the art slugs the workbench identifies a fight by. The combat wire
  carries no encounter id, so the roster is the handle — the same trick
  `BOSS_SCENES` uses to pick a room.

Usage (from `sts2-wiki/`)::

    uv run python -m scripts.extract_encounter_slots \\
        ../sts2_binaries/v0.107.1/SlayTheSpire2.pck \\
        decompiled/v0.107.1 \\
        ../sts2-pycli/web/src/components/encounterSlots.data.ts
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

from scripts.extract_bg_layers import _vec2, parse_scene
from scripts.extract_rooms import make_reader, read_pck_index

ENCOUNTER_PREFIX = "scenes/encounters/"

# The scene the markers are authored in, and its centre — `NCombatRoom` parks
# the encounter scene at `-Vector2(1920, 1080) * 0.5f` so a marker's global
# position is measured from the scene's middle.
SCENE_W = 1920.0
SCENE_H = 1080.0

_SLOTS_RE = re.compile(r"IReadOnlyList<string> Slots =>(?P<body>.*)")
_STRING_RE = re.compile(r'"([^"]*)"')
_MONSTER_RE = re.compile(r"Monster<([A-Za-z0-9_]+)>")
_GENERATE_RE = re.compile(r"GenerateMonsters\(\)\s*\{(?P<body>.*?)\n\t\}", re.DOTALL)
# A slot named in `GenerateMonsters`: a literal, or an index into the model's
# own `Slots` / `_slotNames` list.
_START_RE = re.compile(r'"(?P<lit>[^"]*)"|(?:Slots|_slotNames)\[(?P<idx>\d+)\]')
# The two encounters that hand out slots in a LOOP rather than one by one —
# the Bowlbugs' two workers and the Dense Vegetation's four wrigglers. Neither
# names its slots, so the parse below sees the first and misses the rest.
_LOOP_RE = re.compile(r"(?:for \(int |foreach \(string )")


def _snake(name: str) -> str:
    """`BowlbugRock` -> `bowlbug_rock` — the art slug the workbench files by."""
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def _class_name(encounter_id: str) -> str:
    """`nibbits_normal` -> `NibbitsNormal`, the decompiled class's file stem."""
    return "".join(part.capitalize() for part in encounter_id.split("_"))


def encounter_model(decomp: Path, encounter_id: str) -> tuple[list[str], list[str]]:
    """``(Slots order, roster art slugs)`` from the decompiled encounter class.

    Both are best-effort and both have a safe empty answer: with no `Slots` the
    caller falls back to marker declaration order, and with no roster the
    encounter simply cannot be identified from a board and keeps the row.
    """
    path = decomp / "MegaCrit.Sts2.Core.Models.Encounters" / f"{_class_name(encounter_id)}.cs"
    if not path.is_file():
        return [], []
    text = path.read_text(encoding="utf-8", errors="replace")
    slots: list[str] = []
    m = _SLOTS_RE.search(text)
    if m:
        slots = _STRING_RE.findall(m.group("body"))
    roster = sorted({_snake(n) for n in _MONSTER_RE.findall(text)})
    return slots, roster


def encounter_start(decomp: Path, encounter_id: str, slots: list[str]) -> list[str]:
    """The slots the encounter's OPENING roster stands in, in wire order.

    `GenerateMonsters` returns `(monster, slot)` pairs and the engine then keeps
    its enemy list sorted by slot index (`CombatState.SortEnemiesBySlotName`),
    so this list lines up one-for-one with the enemies the board arrives with.

    It is not simply the first N slots: `TwoTailedRatsNormal` declares five and
    opens on `Slots[2..4]`, so reading the row off the front would stand all
    three rats a slot and a half to the left of where the game stands them.
    """
    path = decomp / "MegaCrit.Sts2.Core.Models.Encounters" / f"{_class_name(encounter_id)}.cs"
    if not path.is_file():
        return []
    body_m = _GENERATE_RE.search(path.read_text(encoding="utf-8", errors="replace"))
    if body_m is None:
        return []
    body = body_m.group("body")
    start: list[str] = []
    for m in _START_RE.finditer(body):
        lit, idx = m.group("lit"), m.group("idx")
        name = lit if lit is not None else (slots[int(idx)] if int(idx) < len(slots) else None)
        if name in slots and name not in start:
            start.append(name)  # type: ignore[arg-type]
    if _LOOP_RE.search(body):
        # A loop fills the rest of the list in `Slots` order — that is what
        # `GetNextSlot` would hand out anyway.
        start += [s for s in slots if s not in start]
    return start


def encounter_slots(pck_path: str, decomp: Path) -> dict[str, Any]:
    index = read_pck_index(pck_path)
    read = make_reader(pck_path, index)

    out: dict[str, Any] = {}
    for path in sorted(p for p in index if p.startswith(ENCOUNTER_PREFIX) and p.endswith(".tscn")):
        encounter_id = Path(path).stem
        _, nodes = parse_scene((read(path) or b"").decode("utf-8", "replace"))
        markers = {
            node.name: _vec2(node, "position", (0.0, 0.0))
            for node in nodes
            if node.type == "Marker2D"
        }
        if not markers:
            continue
        order, roster = encounter_model(decomp, encounter_id)
        # `Slots` first, then any marker the model does not name (the Gremlin
        # Merc's scene carries a `sneaky`/`fat` pair its model fills by name).
        names = [s for s in order if s in markers] + [n for n in markers if n not in order]
        out[encounter_id] = {
            "roster": roster,
            "start": encounter_start(decomp, encounter_id, names) or names,
            "slots": [
                {
                    "name": name,
                    "x": round(markers[name][0] - SCENE_W / 2, 2),
                    "y": round(markers[name][1], 2),
                }
                for name in names
            ],
        }
    return out


def render_ts(table: dict[str, Any]) -> str:
    body = json.dumps(table, indent=2, sort_keys=False)
    return f"""// GENERATED — do not edit by hand.
//
// Regenerate from the game PCK + the decomp (from `sts2-wiki/`):
//
//   uv run python -m scripts.extract_encounter_slots \\
//       ../sts2_binaries/v0.107.1/SlayTheSpire2.pck \\
//       decompiled/v0.107.1 \\
//       ../sts2-pycli/web/src/components/encounterSlots.data.ts
//
// Source of truth: `scenes/encounters/<id>.tscn` in the v0.107.1 PCK and
// `MegaCrit.Sts2.Core.Models.Encounters` in the decomp. See
// `sts2-wiki/scripts/extract_encounter_slots.py`, and `encounterSlots.ts` for
// how a fight is matched to one of these.
import type {{ EncounterSlotTable }} from "./encounterSlots";

export const ENCOUNTER_SLOTS: EncounterSlotTable = {body} as const;
"""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pck_path")
    parser.add_argument("decomp_dir", help="e.g. decompiled/v0.107.1")
    parser.add_argument("out_path", help="`.ts` for the web module, `.json` for raw JSON")
    args = parser.parse_args()

    table = encounter_slots(args.pck_path, Path(args.decomp_dir))
    out = Path(args.out_path)
    if out.suffix == ".json":
        out.write_text(json.dumps(table, indent=2) + "\n", encoding="utf-8")
    else:
        out.write_text(render_ts(table), encoding="utf-8")

    slots = sum(len(e["slots"]) for e in table.values())
    blind = sorted(k for k, e in table.items() if not e["roster"])
    print(f"{len(table)} encounters, {slots} slots -> {out}")
    if blind:
        print(f"  no roster parsed (cannot be matched from a board): {', '.join(blind)}")


if __name__ == "__main__":
    main()
