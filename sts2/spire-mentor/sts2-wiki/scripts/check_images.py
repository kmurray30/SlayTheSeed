#!/usr/bin/env python3
"""Check that all content items have corresponding images.

Usage:
    uv run python -m scripts.check_images data/v0.100.0 site/public/images
"""

import argparse
import json
import re
import sys
from pathlib import Path


def pascal_to_snake(name: str) -> str:
    """Convert PascalCase to snake_case (matches JS regex in utils.ts)."""
    return re.sub(
        r"([A-Z])",
        lambda m: ("_" if m.start() > 0 else "") + m.group(1),
        name,
    ).lower()


# Must match MONSTER_IMAGE_ALIASES in site/src/utils.ts
MONSTER_IMAGE_ALIASES: dict[str, str] = {
    # Aeonglass has no rig in v0.107.1 — the game renders it as a static
    # hourglass placeholder (scenes/creature_visuals/aeonglass.tscn).
    "Aeonglass": "hourglass_placeholder",
    "TorchHeadAmalgam": "amalgam",
    "GlobeHead": "orb_head",
    "Flyconid": "flying_mushrooms",
    "DecimillipedeSegment": "decimillipede",
    "LivingFog": "living_smog",
    "SkulkingColony": "living_shield",
    # Crusher (left arm) and Rocket (right arm) are the two targetable parts of
    # the Kaiser Crab boss — Crusher.cs/Rocket.cs drive the kaiser_crab_left_* /
    # kaiser_crab_right_* SFX and share its sprite. They are NOT the (unused)
    # infested_guardian rig, which belongs to no monster class at all.
    "Crusher": "kaiser_crab",
    "Rocket": "kaiser_crab",
    "Ovicopter": "egg_layer",
    "BowlbugEgg": "bowlbug",
    "BowlbugNectar": "bowlbug",
    "BowlbugRock": "bowlbug",
    "BowlbugSilk": "bowlbug",
    "CalcifiedCultist": "cultists",
    "DampCultist": "cultists",
    "BattleFriendV1": "battleworn_dummy",
    "BattleFriendV2": "battleworn_dummy",
    "BattleFriendV3": "battleworn_dummy",
    "BigDummy": "battleworn_dummy",
}

# Monsters that are test/unused and should be skipped
# Note: TestSubject is a real in-game boss and should NOT be skipped here,
# but it does lack a rendered image so it is listed in NO_IMAGE_MONSTERS below.
SKIP_MONSTERS = {
    "Architect",
    "BigDummy",
    "DeprecatedMonster",
    "MultiAttackMoveMonster",
    "OneHpMonster",
    "SingleAttackMoveMonster",
    "TenHpMonster",
    "TheAdversaryMkOne",
    "TheAdversaryMkThree",
    "TheAdversaryMkTwo",
}

# Monsters without rendered images.
# Some have Spine data but need manual rendering via tools/spine-renderer/.
# Others genuinely have no skeleton data in the game files.
KNOWN_MISSING_MONSTERS = {
    "FakeMerchantMonster",  # Spine rig extracted (fake_merchant_monster); no static sprite
}


# Cards whose atlas sprite metadata (.tres) exists in the game files but
# whose PNG was not produced by the image extractor. These are typically
# brand-new beta cards whose atlas region is defined but not yet rendered.
KNOWN_MISSING_CARDS = {
    "Wither",  # v0.105.1 status card; no atlas sprite
}


# Relics with no image at all. The three v0.105.1 Ancients that used to live
# here (Fishing Rod, Kaleidoscope, Silken Tress) are NOT in the packed atlas
# and never will be: the game reaches them through
# ``AtlasResourceLoader.GetRelicFallbackPath`` -> ``images/relics/beta/``.
# ``extract_images.extract_fallback_sprites`` now walks that same chain, so
# they resolve like any other relic.
KNOWN_MISSING_RELICS: set[str] = set()


def check_cards(data_dir: Path, images_dir: Path) -> list[tuple[str, str]]:
    """Check card images exist."""
    missing = []
    with open(data_dir / "cards.json") as f:
        cards = json.load(f)
    for card in cards:
        if card["class_name"] in KNOWN_MISSING_CARDS:
            continue
        char_dir = card["character"].lower()
        filename = pascal_to_snake(card["class_name"])
        primary = images_dir / "card_atlas" / char_dir / f"{filename}.png"
        beta = images_dir / "card_atlas" / char_dir / "beta" / f"{filename}.png"
        if not primary.exists() and not beta.exists():
            missing.append((card["class_name"], str(primary)))
    return missing


def check_relics(data_dir: Path, images_dir: Path) -> list[tuple[str, str]]:
    """Check relic images exist."""
    missing = []
    with open(data_dir / "relics.json") as f:
        relics = json.load(f)
    for relic in relics:
        if relic["class_name"] in KNOWN_MISSING_RELICS:
            continue
        image = relic.get("image", "")
        if not image:
            continue
        path = images_dir / "relic_atlas" / f"{image}.png"
        if not path.exists():
            missing.append((relic["class_name"], str(path)))
    return missing


def check_potions(data_dir: Path, images_dir: Path) -> list[tuple[str, str]]:
    """Check potion images exist."""
    missing = []
    with open(data_dir / "potions.json") as f:
        potions = json.load(f)
    for potion in potions:
        if "Deprecated" in potion["class_name"]:
            continue
        image = potion.get("image", "")
        if not image:
            continue
        path = images_dir / "potion_atlas" / f"{image}.png"
        if not path.exists():
            missing.append((potion["class_name"], str(path)))
    return missing


def check_monsters(data_dir: Path, images_dir: Path) -> list[tuple[str, str]]:
    """Check monster images exist."""
    missing = []
    with open(data_dir / "monsters.json") as f:
        monsters = json.load(f)
    for monster in monsters:
        cn = monster["class_name"]
        if cn in SKIP_MONSTERS or cn in KNOWN_MISSING_MONSTERS:
            continue
        image_name = MONSTER_IMAGE_ALIASES.get(cn, pascal_to_snake(cn))
        path = images_dir / "monsters" / f"{image_name}.png"
        if not path.exists():
            missing.append((cn, str(path)))
    return missing


def check_stale_monster_skips(data_dir: Path, images_dir: Path) -> list[tuple[str, str]]:
    """Find KNOWN_MISSING_MONSTERS entries whose image actually resolves.

    ``check_monsters`` applies the skip BEFORE the alias lookup, so an entry
    listed here is never verified — a wrong alias behind one is invisible. That
    is how ``Crusher`` sat mapped to ``infested_guardian`` (an orphan rig owned
    by no monster class) while also being skipped as "No spine skel": the arm
    shares the Kaiser Crab's sprite and was never actually missing.

    Only KNOWN_MISSING_MONSTERS is checked. SKIP_MONSTERS means "test/unused
    content the player never sees", which stays skipped whether art exists or
    not.
    """
    stale = []
    with open(data_dir / "monsters.json") as f:
        monsters = json.load(f)
    for monster in monsters:
        cn = monster["class_name"]
        if cn not in KNOWN_MISSING_MONSTERS:
            continue
        path = images_dir / "monsters" / f"{MONSTER_IMAGE_ALIASES.get(cn, pascal_to_snake(cn))}.png"
        if path.exists():
            stale.append((cn, str(path)))
    return stale


def check_powers(data_dir: Path, images_dir: Path) -> list[tuple[str, str]]:
    """Check power images exist."""
    missing = []
    with open(data_dir / "powers.json") as f:
        powers = json.load(f)
    for power in powers:
        filename = pascal_to_snake(power["class_name"])
        path = images_dir / "power_atlas" / f"{filename}.png"
        if not path.exists():
            missing.append((power["class_name"], str(path)))
    return missing


def check_ancients(data_dir: Path, images_dir: Path) -> list[tuple[str, str]]:
    """Check ancient images exist."""
    missing = []
    with open(data_dir / "ancients.json") as f:
        ancients = json.load(f)
    for ancient in ancients:
        if ancient.get("_loc_missing"):
            continue
        # Slug is title lowercased
        slug = re.sub(r"[^a-z0-9]+", "-", ancient["title"].lower()).strip("-")
        path = images_dir / "ancients" / f"{slug}.png"
        if not path.exists():
            missing.append((ancient["class_name"], str(path)))
    return missing


def main() -> None:
    parser = argparse.ArgumentParser(description="Check that content items have images")
    parser.add_argument("data_dir", help="Path to versioned data directory")
    parser.add_argument("images_dir", help="Path to images directory")
    args = parser.parse_args()

    data_dir = Path(args.data_dir)
    images_dir = Path(args.images_dir)

    all_missing: dict[str, list[tuple[str, str]]] = {}

    checkers = [
        ("Cards", check_cards),
        ("Relics", check_relics),
        ("Potions", check_potions),
        ("Monsters", check_monsters),
        ("Powers", check_powers),
        ("Ancients", check_ancients),
    ]

    total_checked = 0
    total_missing = 0

    for label, checker in checkers:
        missing = checker(data_dir, images_dir)
        if missing:
            all_missing[label] = missing
            total_missing += len(missing)
        # Count items checked from data files
        with open(data_dir / f"{label.lower()}.json") as f:
            total_checked += len(json.load(f))

    print(f"Checked {total_checked} items across {len(checkers)} types")

    stale = check_stale_monster_skips(data_dir, images_dir)
    if stale:
        print(f"\n{len(stale)} stale KNOWN_MISSING_MONSTERS entr(ies) — the image exists:\n")
        for class_name, path in stale:
            print(f"    {class_name} -> {path}")
        print("\n  Remove them from KNOWN_MISSING_MONSTERS so they stay verified.\n")

    if all_missing:
        print(f"\n{total_missing} missing image(s):\n")
        for label, items in all_missing.items():
            print(f"  {label} ({len(items)} missing):")
            for class_name, path in items[:10]:
                print(f"    {class_name} -> {path}")
            if len(items) > 10:
                print(f"    ... and {len(items) - 10} more")
        print()
        sys.exit(1)
    elif stale:
        sys.exit(1)
    else:
        print("All images OK")


if __name__ == "__main__":
    main()
