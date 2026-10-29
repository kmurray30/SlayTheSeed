"""Which epoch-gated cards does the world actually show us? (GH #367)

Every character card pool prunes cards behind unrevealed epochs
(``CardPoolModel.GetUnlockedCards`` -> ``FilterThroughEpochs``), and sts2-rs
had no ``UnlockState`` at all — it assumed everything revealed. The engine's
generation pools were therefore one epoch too big wherever the world's save
had an epoch still locked, which shows up as a ``combat_card_generation``
cursor drift of exactly the epoch's contribution.

This is a census, not a policy: it walks the recorded world wires of a mirror
corpus and reports, per epoch, how many times each of its three cards appeared
ANYWHERE the world named a card (piles, choice options, rewards). Epochs are
all-or-nothing, so the signature of an unrevealed epoch is a clean **0 for all
three** while sibling epochs of the same character show up freely.

Absence is weaker evidence than presence — a card can be missing by luck. The
report prints the per-character totals alongside so a "0 of 3" can be read
against how much of that character's pool was seen at all.

Usage::

    python -m diag_epoch_census logs/diag/mirror_regent_a1.jsonl [more.jsonl ...]
"""

from __future__ import annotations

import json
import sys
from collections import Counter
from collections.abc import Iterator
from pathlib import Path
from typing import Any

#: Epoch id -> the three cards it gates, as registry names.
#: Mirrors `sts2_content::unlocks::Epoch::cards` and, through it,
#: `MegaCrit.Sts2.Core.Timeline.Epochs/*Epoch.cs` at v0.107.1.
EPOCH_CARDS: dict[str, tuple[str, str, str]] = {
    "IRONCLAD2_EPOCH": ("MOLTEN_FIST", "CRUELTY", "DOMINATE"),
    "IRONCLAD5_EPOCH": ("CINDER", "PACTS_END", "DRUM_OF_BATTLE"),
    "IRONCLAD7_EPOCH": ("BLOOD_WALL", "TEAR_ASUNDER", "INFERNO"),
    "SILENT2_EPOCH": ("SNAKEBITE", "BUBBLE_BUBBLE", "ACCELERANT"),
    "SILENT5_EPOCH": ("REFLEX", "MASTER_PLANNER", "HAND_TRICK"),
    "SILENT7_EPOCH": ("HIDDEN_DAGGERS", "BLADE_OF_INK", "PHANTOM_BLADES"),
    "DEFECT2_EPOCH": ("LOOP", "NULL", "CONSUMING_SHADOW"),
    "DEFECT5_EPOCH": ("BARRAGE", "FLAK_CANNON", "SMOKESTACK"),
    "DEFECT7_EPOCH": ("TURBO", "SCAVENGE", "HELIX_DRILL"),
    "REGENT2_EPOCH": ("SPOILS_OF_BATTLE", "SWORD_SAGE", "FURNACE"),
    "REGENT5_EPOCH": ("BEGONE", "ARSENAL", "SUPERMASSIVE"),
    "REGENT7_EPOCH": ("PATTER", "HEAVENLY_DRILL", "LUNAR_BLAST"),
    "NECROBINDER2_EPOCH": ("SCOURGE", "OBLIVION", "COUNTDOWN"),
    "NECROBINDER5_EPOCH": ("AFTERLIFE", "SACRIFICE", "CALCIFY"),
    "NECROBINDER7_EPOCH": ("SCULPTING_STRIKE", "VEILPIERCER", "BANSHEES_CRY"),
    "COLORLESS1_EPOCH": ("AUTOMATION", "ENTROPY", "CATASTROPHE"),
    "COLORLESS2_EPOCH": ("ETERNAL_ARMOR", "JACKPOT", "PREP_TIME"),
    "COLORLESS3_EPOCH": ("REND", "BEAT_DOWN", "PROWESS"),
    "COLORLESS4_EPOCH": ("ALCHEMIZE", "NOSTALGIA", "SCRAWL"),
    "COLORLESS5_EPOCH": ("SPLASH", "ANOINTED", "CALAMITY"),
}

#: Which epochs each character's pool filters on, per `*CardPool.cs`'s
#: `IsEpochRevealed<...>` calls. Colorless is shared by every character.
CHARACTER_EPOCHS: dict[str, tuple[str, ...]] = {
    "IRONCLAD": ("IRONCLAD2_EPOCH", "IRONCLAD5_EPOCH", "IRONCLAD7_EPOCH"),
    "SILENT": ("SILENT2_EPOCH", "SILENT5_EPOCH", "SILENT7_EPOCH"),
    "DEFECT": ("DEFECT2_EPOCH", "DEFECT5_EPOCH", "DEFECT7_EPOCH"),
    "REGENT": ("REGENT2_EPOCH", "REGENT5_EPOCH", "REGENT7_EPOCH"),
    "NECROBINDER": ("NECROBINDER2_EPOCH", "NECROBINDER5_EPOCH", "NECROBINDER7_EPOCH"),
}

COLORLESS_EPOCHS = tuple(e for e in EPOCH_CARDS if e.startswith("COLORLESS"))

_TRACKED = {c: e for e, cards in EPOCH_CARDS.items() for c in cards}


def _walk_card_names(node: Any) -> Iterator[str]:
    """Yield every string that sits under a card-ish ``name``/``id`` key.

    Deliberately structure-agnostic: the world wire nests cards under piles,
    choice options, rewards and shop stock, and the point here is coverage,
    not precision about which surface a sighting came from.
    """
    if isinstance(node, dict):
        for key, value in node.items():
            if key in ("name", "id", "card_id") and isinstance(value, str):
                yield value
            else:
                yield from _walk_card_names(value)
    elif isinstance(node, list):
        for item in node:
            yield from _walk_card_names(item)


def census(paths: list[Path]) -> tuple[Counter[str], Counter[str], int]:
    """Return (per-card sightings, per-character record counts, records read)."""
    sightings: Counter[str] = Counter()
    per_character: Counter[str] = Counter()
    records = 0
    for path in paths:
        with path.open("r", encoding="utf-8", errors="replace") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    rec = json.loads(line)
                except json.JSONDecodeError:
                    continue
                records += 1
                # Desync records name it `world_wire`; some other record kinds
                # use a bare `wire`. Take whichever is present.
                wire = rec.get("world_wire") or rec.get("wire") or {}
                klass = ((wire.get("player") or {}).get("class") or "").upper()
                if klass:
                    per_character[klass] += 1
                for name in _walk_card_names(wire):
                    stem = name.split("/")[-1].removeprefix("CARD.")
                    if stem in _TRACKED:
                        sightings[stem] += 1
    return sightings, per_character, records


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__)
        return 2
    paths = [Path(p) for p in argv[1:]]
    missing = [p for p in paths if not p.exists()]
    if missing:
        print(f"no such corpus: {', '.join(str(p) for p in missing)}", file=sys.stderr)
        return 2

    sightings, per_character, records = census(paths)
    print(f"records: {records}")
    print(
        "characters seen: "
        + (", ".join(f"{k}={v}" for k, v in per_character.most_common()) or "none")
    )
    print()

    unrevealed: list[str] = []
    for character, epochs in [*CHARACTER_EPOCHS.items(), ("(colorless)", COLORLESS_EPOCHS)]:
        rows = []
        for epoch in epochs:
            cards = EPOCH_CARDS[epoch]
            counts = [sightings[c] for c in cards]
            seen = sum(1 for n in counts if n > 0)
            rows.append((epoch, cards, counts, seen))
        if not any(sum(c) for _, _, c, _ in rows):
            # Nothing from this character at all — no evidence either way.
            continue
        print(f"== {character}")
        for epoch, cards, counts, seen in rows:
            detail = ", ".join(f"{c}={n}" for c, n in zip(cards, counts))
            verdict = "UNREVEALED?" if seen == 0 else f"{seen}/3 seen"
            if seen == 0:
                unrevealed.append(epoch)
            print(f"   {epoch:<22} {verdict:<12} {detail}")
        print()

    if unrevealed:
        print("candidate locked epochs (0 of 3 sighted):")
        print("   " + " ".join(unrevealed))
        print()
        print("install with, e.g.:")
        print(f"   sts2_rs.set_locked_epochs({unrevealed!r})")
    else:
        print("every epoch of every character seen in this corpus — nothing to lock.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
