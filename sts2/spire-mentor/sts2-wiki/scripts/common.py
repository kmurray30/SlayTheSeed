"""Shared utilities for STS2 data extraction scripts."""

import json
import os
import re
from collections.abc import Iterator
from pathlib import Path


def class_name_to_loc_key(class_name: str) -> str:
    """Convert PascalCase class name to UPPER_SNAKE_CASE localization key.

    Examples:
        SwordBoomerang -> SWORD_BOOMERANG
        DemonForm -> DEMON_FORM
        IronWave -> IRON_WAVE
    """
    result = re.sub(r"(?<=[a-z0-9])([A-Z])", r"_\1", class_name)
    result = re.sub(r"(?<=[A-Z])([A-Z][a-z])", r"_\1", result)
    return result.upper()


def load_localization(loc_dir: str, table: str) -> dict[str, str]:
    """Load a localization JSON file.

    Args:
        loc_dir: Path to localization directory (e.g. .../localization/eng/)
        table: Name of the table (e.g. "cards", "relics") — without .json extension
    """
    path = os.path.join(loc_dir, f"{table}.json")
    with open(path, encoding="utf-8") as f:
        result: dict[str, str] = json.load(f)
        return result


def parse_canonical_vars(cs_content: str) -> list[dict]:
    """Extract DynamicVar declarations from a C# file.

    Handles all known Var types from decompiled STS2 code.
    """
    vars_found: list[dict] = []

    # Simple numeric vars: new XxxVar(Nm) or new XxxVar(N)
    simple_vars = {
        "DamageVar": "Damage",
        "CalculatedDamageVar": "CalculatedDamage",
        "ExtraDamageVar": "ExtraDamage",
        "BlockVar": "Block",
        "CalculatedBlockVar": "CalculatedBlock",
        "CalculationBaseVar": "CalculationBase",
        "CalculationExtraVar": "CalculationExtra",
        "HpLossVar": "HpLoss",
        "SummonVar": "Summon",
        "ForgeVar": "Forge",
        "GoldVar": "Gold",
        "HealVar": "Heal",
        "MaxHpVar": "MaxHp",
        "OstyDamageVar": "OstyDamage",
        "StarsVar": "Stars",
    }
    for var_class, var_type in simple_vars.items():
        for m in re.finditer(rf"new {var_class}\((-?\d+)m?(?:\b|[,)])", cs_content):
            vars_found.append({"type": var_type, "base_value": int(m.group(1))})

    # Vars without 'm' decimal suffix
    int_vars = {
        "EnergyVar": "Energy",
        "CardsVar": "Cards",
        "RepeatVar": "Repeat",
        "IntVar": "Int",
    }
    for var_class, var_type in int_vars.items():
        for m in re.finditer(rf"new {var_class}\((-?\d+)\)", cs_content):
            vars_found.append({"type": var_type, "base_value": int(m.group(1))})
    # CardsVar can also have 'm' suffix
    for m in re.finditer(r"new CardsVar\((-?\d+)m\)", cs_content):
        # Avoid double-counting if already found without 'm'
        val = int(m.group(1))
        if not any(v["type"] == "Cards" and v["base_value"] == val for v in vars_found):
            vars_found.append({"type": "Cards", "base_value": val})

    # PowerVar<T>(Nm)
    for m in re.finditer(r"new PowerVar<(\w+)>\((-?\d+)m", cs_content):
        vars_found.append({"type": m.group(1).replace("Power", ""), "base_value": int(m.group(2))})

    # Named BlockVar: new BlockVar("Name", Nm, ...)
    for m in re.finditer(r'new BlockVar\("(\w+)",\s*(-?\d+)m', cs_content):
        vars_found.append({"type": m.group(1), "base_value": int(m.group(2))})

    # DynamicVar("Name", Nm) — generic named vars
    for m in re.finditer(r'new DynamicVar\("(\w+)",\s*(-?\d+)m', cs_content):
        vars_found.append({"type": m.group(1), "base_value": int(m.group(2))})

    # GenericVar("Name", Nm) — legacy pattern
    for m in re.finditer(r'new GenericVar\("(\w+)",\s*(-?\d+)m', cs_content):
        vars_found.append({"type": m.group(1), "base_value": int(m.group(2))})

    # Named CardsVar / IntVar: new CardsVar("Shivs", 4), new IntVar("Replay", 2m).
    # The unnamed forms above only match a bare number, so these were dropped and
    # their placeholders ({Shivs}, {Replay}, {Increase}, {PlayMax}) rendered "X".
    for var_class in ("CardsVar", "IntVar"):
        for m in re.finditer(rf'new {var_class}\("(\w+)",\s*(-?\d+)m?\s*[,)]', cs_content):
            vars_found.append({"type": m.group(1), "base_value": int(m.group(2))})

    # Vars whose constructor argument is a C# property rather than a literal —
    # The Scythe's `new DamageVar(CurrentDamage, …)`, Genetic Algorithm's
    # `new BlockVar(CurrentBlock, …)`. Both are backed by a field whose
    # initializer carries the starting value the card shows before it grows.
    for var_class, var_type in (("DamageVar", "Damage"), ("BlockVar", "Block")):
        if any(v["type"] == var_type for v in vars_found):
            continue
        pm = re.search(rf"new {var_class}\(([A-Z]\w*)\s*[,)]", cs_content)
        if not pm:
            continue
        prop = pm.group(1)
        field = "_" + prop[0].lower() + prop[1:]
        fm = re.search(rf"\b{re.escape(field)}\s*=\s*(-?\d+)\s*;", cs_content)
        if fm:
            vars_found.append({"type": var_type, "base_value": int(fm.group(1))})

    # CalculatedDamageVar(ValueProp.Move) / CalculatedVar("CalculatedFocus") —
    # a runtime scaling total with no literal in its constructor. Out of combat
    # CalculatedVar.Calculate() multiplies the extra by zero, so the card reads
    # its CalculationBase: Ashen Strike shows "Deal 6 damage.", not "Deal X".
    base = next((v["base_value"] for v in vars_found if v["type"] == "CalculationBase"), None)
    if base is not None:
        for m in re.finditer(
            r'new (Calculated(?:Damage|Block)Var)\(\s*ValueProp\.|new CalculatedVar\("(\w+)"',
            cs_content,
        ):
            name = m.group(2) or m.group(1).removesuffix("Var")
            if not any(v["type"] == name for v in vars_found):
                vars_found.append({"type": name, "base_value": base})

    return vars_found


def parse_referenced_powers(cs_content: str) -> list[str]:
    """Extract power types from Apply<X>, PowerVar<X>, FromPower<X> patterns."""
    powers: list[str] = []
    powers += re.findall(r"FromPower<(\w+)>", cs_content)
    powers += re.findall(r"Apply<(\w+)>", cs_content)
    powers += re.findall(r"PowerVar<(\w+)>", cs_content)
    # Deduplicate preserving order
    seen: set[str] = set()
    result: list[str] = []
    for p in powers:
        if p not in seen:
            seen.add(p)
            result.append(p)
    return result


_CANONICAL_KEYWORDS = re.compile(r"CanonicalKeywords\s*=>(.*?);\s*\n", re.S)


def parse_keywords(cs_content: str) -> list[str]:
    """Extract a card's OWN keywords — the ``CanonicalKeywords`` property only.

    ``CardKeyword.X`` appears in three unrelated places in a card's C# source,
    and only the first is a keyword the card HAS:

    1. ``CanonicalKeywords`` — the card's keyword set (``CardModel.LocalKeywords``
       seeds itself from it). This is what we want.
    2. ``ExtraHoverTips => HoverTipFactory.FromKeyword(CardKeyword.Exhaust)`` —
       a card that Exhausts OTHER cards (Burning Pact, Havoc, Corruption)
       showing the reader what Exhaust means. It does not exhaust itself.
    3. ``OnUpgrade`` ``AddKeyword``/``RemoveKeyword`` — an upgrade-only delta,
       already captured separately as the card's ``upgrades`` entries.

    Scanning the whole file (as this did until 2026-08-24) conflated all three
    and mislabelled 65 of 576 v0.107.1 cards — Feel No Pain, Corruption and
    Dark Embrace all came out "Exhaust", and Afterimage came out "Innate" at
    base rather than on upgrade.
    """
    match = _CANONICAL_KEYWORDS.search(cs_content)
    if not match:
        return []
    # Deduplicate preserving order
    seen: set[str] = set()
    result: list[str] = []
    for k in re.findall(r"CardKeyword\.(\w+)", match.group(1)):
        if k not in seen:
            seen.add(k)
            result.append(k)
    return result


def read_cs_files(directory: str) -> Iterator[tuple[str, str]]:
    """Yield (class_name, file_content) pairs from a directory of .cs files."""
    for filename in sorted(os.listdir(directory)):
        if not filename.endswith(".cs"):
            continue
        filepath = os.path.join(directory, filename)
        with open(filepath, encoding="utf-8") as f:
            content = f.read()
        class_name = filename.removesuffix(".cs")
        yield class_name, content


_COLOR_TAGS = {
    "gold": "desc-gold",
    "red": "desc-red",
    "blue": "desc-blue",
    "green": "desc-green",
    "orange": "desc-orange",
    "purple": "desc-purple",
    "aqua": "desc-aqua",
    "pink": "desc-pink",
}


# Map of inline icon tokens (from raw text OR bracket form) to (alt_text, image_file)
_ICON_IMAGES: dict[str, tuple[str, str]] = {
    "singleStarIcon": ("Star", "star_icon.png"),
    "starIcon": ("Star", "star_icon.png"),
    "goldIcon": ("Gold", "gold_icon.png"),
    "cardIcon": ("Card", "card_icon.png"),
    "potionIcon": ("Potion", "potion_icon.png"),
    "chestIcon": ("Chest", "chest_icon.png"),
    "ironcladEnergyIcon": ("Energy", "ironclad_energy_icon.png"),
    "silentEnergyIcon": ("Energy", "silent_energy_icon.png"),
    "defectEnergyIcon": ("Energy", "defect_energy_icon.png"),
    "regentEnergyIcon": ("Energy", "regent_energy_icon.png"),
    "necrobinderEnergyIcon": ("Energy", "necrobinder_energy_icon.png"),
    "colorlessEnergyIcon": ("Energy", "colorless_energy_icon.png"),
    "watcherEnergyIcon": ("Energy", "watcher_energy_icon.png"),
}


def _render_icon_html(name: str, base_url: str = "/sts2-wiki/") -> str:
    alt, img = _ICON_IMAGES[name]
    return (
        f'<img src="{base_url}images/sprite_fonts/{img}" alt="{alt}" '
        f'class="inline-icon" style="height:1em;vertical-align:-0.15em;display:inline" />'
    )


def rich_text_to_html(text: str, base_url: str = "/sts2-wiki/") -> str:
    """Convert game rich text tags to HTML spans."""
    html = text
    # Replace {iconName} placeholders with <img> tags
    for name in _ICON_IMAGES:
        html = html.replace("{" + name + "}", _render_icon_html(name, base_url))
    # Also handle [star] legacy bracket form
    html = html.replace("[star]", _render_icon_html("singleStarIcon", base_url))
    html = html.replace("[energy]", _render_icon_html("ironcladEnergyIcon", base_url))
    for tag, css_class in _COLOR_TAGS.items():
        html = re.sub(
            rf"\[{tag}\](.*?)\[/{tag}\]",
            rf'<span class="{css_class}">\1</span>',
            html,
        )
    html = re.sub(r"\[/?(?:sine|wave|shake|b|i|jitter|center|thinky_dots)\]", "", html)
    html = html.replace("\n", "<br>")
    return html


def strip_rich_text(text: str) -> str:
    """Strip all game rich text tags for plain text."""
    result = text
    # Convert icon placeholders to readable text
    for name, (alt, _) in _ICON_IMAGES.items():
        result = result.replace("{" + name + "}", alt)
    result = result.replace("[star]", "\u2605")
    result = result.replace("[energy]", "Energy")
    return re.sub(r"\[/?[^\]]*\]", "", result)


def decompiled_dir(base: str, namespace: str) -> str:
    """Get path to a decompiled namespace directory."""
    return os.path.join(base, namespace)


def data_dir(base: str, version: str) -> str:
    """Get path to a versioned data directory, creating it if needed."""
    path = os.path.join(base, version)
    Path(path).mkdir(parents=True, exist_ok=True)
    return path


def write_json(path: str, data: object) -> None:
    """Write JSON data to a file, creating parent directories as needed."""
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def find_loc_key(
    class_name: str,
    loc_data: dict[str, str],
    suffix: str = ".title",
) -> str | None:
    """Find a localization key for a class name, trying multiple strategies.

    Returns the base key (without suffix) or None if not found.
    """
    # Strategy 1: direct conversion
    key = class_name_to_loc_key(class_name)
    if f"{key}{suffix}" in loc_data:
        return key

    # Strategy 2: fuzzy match - normalize both sides
    norm_class = class_name.lower().replace("'", "").replace("!", "")
    for k in loc_data:
        if k.endswith(suffix):
            candidate = k.removesuffix(suffix)
            norm_cand = candidate.lower().replace("_", "").replace("'", "").replace("!", "")
            if norm_class == norm_cand:
                return candidate

    return None
