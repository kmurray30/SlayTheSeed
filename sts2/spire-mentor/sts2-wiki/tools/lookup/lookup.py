#!/usr/bin/env python3
"""sts2-lookup: Quick reference for STS2 enemies and cards.

Usage:
    lookup enemy <name>       Look up enemy (moves, pattern, HP)
    lookup card <name>        Look up card (cost, effects, target)
    lookup encounter <name>   Look up encounter setup
    lookup -h, --help         Show help

Options:
    --pattern   Show only move pattern (enemies)
    --json      Output raw JSON
    -f, --fuzzy Fuzzy match (Levenshtein)
"""

import json
import os
import re
import sys
from pathlib import Path
from typing import Optional

# Override with STS2_WIKI_DATA when the wiki data/ tree lives elsewhere
# (e.g. running from an exported copy without the committed data).
_data_override = os.environ.get("STS2_WIKI_DATA")
DATA_ROOT = (
    Path(_data_override).expanduser().resolve()
    if _data_override
    else Path(__file__).resolve().parent.parent.parent / "data"
)


def _version_key(name: str) -> tuple[int, ...]:
    """Sort key for "vMAJOR.MINOR.PATCH" directory names."""
    return tuple(int(p) for p in re.findall(r"\d+", name))


# The game version lookups answer for when STS2_VERSION is unset. Pinned
# rather than "highest data/ dir": data/v0.111.0 (the beta port, GH #786)
# lacks the LLM-extracted per-entity folders (monsters/, cards/, ...) these
# lookups read, and v0.107.1 stays the default until the version flip in
# docs/plans/2026-10-17-multi-version-v0.111.md (section 7).
DEFAULT_VERSION = "v0.107.1"


def _latest_version_dir() -> Path:
    """Pick the data/ directory to query.

    STS2_VERSION (e.g. STS2_VERSION=v0.101.0) selects a snapshot; otherwise
    DEFAULT_VERSION, falling back to the highest semver-style directory
    when that one is absent (e.g. an export carrying a single version).
    """
    override = os.environ.get("STS2_VERSION")
    if override:
        return DATA_ROOT / override
    if not DATA_ROOT.is_dir():
        raise SystemExit(f"No wiki data at {DATA_ROOT}; run `just extract` or set STS2_WIKI_DATA")
    if (DATA_ROOT / DEFAULT_VERSION).is_dir():
        return DATA_ROOT / DEFAULT_VERSION
    versions = [p for p in DATA_ROOT.iterdir() if p.is_dir() and p.name.startswith("v")]
    if not versions:
        raise SystemExit(f"No version directories found under {DATA_ROOT}")
    return max(versions, key=lambda p: _version_key(p.name))


def _data_dir() -> Path:
    """Resolved lazily so importing this module never requires a data/ tree.

    Set the module-level DATA_DIR to bypass resolution entirely (tests do).
    """
    global DATA_DIR
    if DATA_DIR is None:
        DATA_DIR = _latest_version_dir()
    return DATA_DIR


# None until first use; assignable directly as an override.
DATA_DIR: Optional[Path] = None

# ── fuzzy matching ──────────────────────────────────────────────


def levenshtein(a: str, b: str) -> int:
    if len(a) < len(b):
        return levenshtein(b, a)
    if len(b) == 0:
        return len(a)
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        curr = [i]
        for j, cb in enumerate(b, 1):
            # subst uses the diagonal (prev[j-1]); del_ uses the cell above (prev[j]).
            subst = prev[j - 1] + (0 if ca == cb else 1)
            ins = curr[j - 1] + 1
            del_ = prev[j] + 1
            curr.append(min(subst, ins, del_))
        prev = curr
    return prev[-1]


def fuzzy_match(query: str, candidates: list[str]) -> Optional[str]:
    """Find best match by Levenshtein distance. Threshold scales with query length."""
    q_lower = query.lower()
    threshold = max(2, len(q_lower) // 2)
    best = None
    best_dist = threshold
    for c in candidates:
        d = levenshtein(q_lower, c.lower())
        if d <= best_dist:
            best_dist = d
            best = c
            if d == 0:
                break
    return best


# ── data loading ────────────────────────────────────────────────


def list_json_files(subdir: str) -> list[str]:
    d = _data_dir() / subdir
    if not d.is_dir():
        return []
    return sorted(f.stem for f in d.glob("*.json"))


def load_json(subdir: str, name: str) -> dict:
    d = _data_dir() / subdir / f"{name}.json"
    if not d.is_file():
        return {}
    return json.loads(d.read_text())


# Cache for title indices to avoid re-scanning per call.
_TITLE_INDEX_CACHE: dict[str, tuple[dict[str, list[str]], dict[str, list[str]]]] = {}

# Strips any trailing parenthetical variant marker, e.g. " (S)", " (Nectar)".
_VARIANT_SUFFIX_RE = re.compile(r"\s*\([^)]*\)\s*$")


def build_title_index(subdir: str) -> tuple[dict[str, list[str]], dict[str, list[str]]]:
    """Return (title_index, base_index).

    title_index: lowercased full title -> [class_name, ...]
    base_index:  lowercased title with trailing (...) variant stripped -> [class_name, ...]
    """
    if subdir in _TITLE_INDEX_CACHE:
        return _TITLE_INDEX_CACHE[subdir]
    title_index: dict[str, list[str]] = {}
    base_index: dict[str, list[str]] = {}
    d = _data_dir() / subdir
    if d.is_dir():
        for f in sorted(d.glob("*.json")):
            try:
                data = json.loads(f.read_text())
            except json.JSONDecodeError:
                continue
            title = data.get("title")
            if not isinstance(title, str) or not title:
                continue
            norm = title.lower()
            title_index.setdefault(norm, []).append(f.stem)
            base = _VARIANT_SUFFIX_RE.sub("", title).strip().lower()
            if base and base != norm:
                base_index.setdefault(base, []).append(f.stem)
    result = (title_index, base_index)
    _TITLE_INDEX_CACHE[subdir] = result
    return result


# ── act / encounter context ─────────────────────────────────────
# Reverse index built from data/<version>/encounters.json so the lookup
# can show "this enemy is an Act 1 (Glory) thing" and catch the case
# where the user is fighting an act-2 enemy but matched something
# similarly-named from act 1.

_ACT_CONTEXT_CACHE: Optional[tuple[dict[str, dict[str, list[str]]], dict[str, list[str]]]] = None


def _act_context() -> tuple[dict[str, dict[str, list[str]]], dict[str, list[str]]]:
    """Return (monster_index, encounter_index).

    monster_index:   class_name -> {"acts": [...], "encounters": [encounter_class_names]}
    encounter_index: encounter class_name -> [acts]
    """
    global _ACT_CONTEXT_CACHE
    if _ACT_CONTEXT_CACHE is not None:
        return _ACT_CONTEXT_CACHE
    monster_index: dict[str, dict[str, list[str]]] = {}
    encounter_index: dict[str, list[str]] = {}
    f = _data_dir() / "encounters.json"
    if f.is_file():
        try:
            encounters = json.loads(f.read_text())
        except json.JSONDecodeError:
            encounters = []
        if isinstance(encounters, list):
            for enc in encounters:
                if not isinstance(enc, dict):
                    continue
                enc_class = enc.get("class_name") or ""
                acts = [a for a in enc.get("acts", []) if isinstance(a, str)]
                if enc_class and acts:
                    encounter_index[enc_class] = acts
                for monster in enc.get("monsters", []):
                    if not isinstance(monster, str):
                        continue
                    entry = monster_index.setdefault(monster, {"acts": [], "encounters": []})
                    for a in acts:
                        if a not in entry["acts"]:
                            entry["acts"].append(a)
                    if enc_class and enc_class not in entry["encounters"]:
                        entry["encounters"].append(enc_class)
    _ACT_CONTEXT_CACHE = (monster_index, encounter_index)
    return _ACT_CONTEXT_CACHE


def _format_encounter_list(encounters: list[str], limit: int = 3) -> str:
    if len(encounters) <= limit:
        return ", ".join(encounters)
    return f"{', '.join(encounters[:limit])}, … (+{len(encounters) - limit} more)"


# ── output formatters ───────────────────────────────────────────


def format_enemy(data: dict, pattern_only: bool = False) -> str:
    if not data:
        return "Not found."

    title = data.get("title", data.get("class_name", "?"))
    hp = data.get("min_hp", "?")
    max_hp = data.get("max_hp", "")
    if str(hp) == str(max_hp):
        hp_str = f"HP {hp}"
    else:
        hp_str = f"HP {hp}-{max_hp}"

    spawn = data.get("powers_on_spawn", [])
    powers_str = f"  Spawns with: {', '.join(spawn)}" if spawn else ""

    moves = data.get("moves", [])
    move_lines = []
    for m in moves:
        title_m = m.get("title", m.get("id", "?"))
        intents = m.get("intents", [])
        effects = m.get("effects", [])
        parts = []
        for intent in intents:
            t = intent.get("type", "")
            if t == "attack":
                dmg = intent.get("damage", "?")
                hits = intent.get("hits", 1)
                if hits > 1:
                    parts.append(f"{dmg}dmg x{hits}")
                else:
                    parts.append(f"{dmg}dmg")
            elif t == "multi_attack":
                dmg = intent.get("damage", "?")
                hits = intent.get("hits", 1)
                parts.append(f"{dmg}dmg x{hits}")
            elif t == "buff":
                parts.append("buff")
            elif t == "debuff":
                parts.append("debuff")
            elif t == "block":
                amt = intent.get("amount", intent.get("value", "?"))
                parts.append(f"{amt}blk")
            elif t == "summon":
                parts.append("summon")
        intent_str = " + ".join(parts)
        effect_str = "; ".join(effects)
        if intent_str:
            move_lines.append(f"  {title_m}: {intent_str} → {effect_str}")
        else:
            move_lines.append(f"  {title_m}: {effect_str}")

    if pattern_only:
        return f"{data.get('move_pattern', 'Unknown pattern')}"

    pattern = data.get("move_pattern", "Unknown pattern")
    notes = data.get("notes", "")

    lines = [f"\n{title}  {hp_str}"]
    if spawn:
        lines.append(powers_str)

    monster_index, _ = _act_context()
    ctx = monster_index.get(data.get("class_name", ""), {})
    acts = ctx.get("acts", [])
    encs = ctx.get("encounters", [])
    if acts:
        lines.append(f"  Acts: {', '.join(acts)}")
    if encs:
        lines.append(f"  Encounters: {_format_encounter_list(encs)}")

    lines.append("\nMoves:")
    lines.extend(move_lines)
    lines.append(f"\nPattern: {pattern}")
    if notes and notes != pattern:
        lines.append(f"\nNotes: {notes}")
    return "\n".join(lines)


def format_card(data: dict) -> str:
    if not data:
        return "Not found."

    title = data.get("title", data.get("class_name", "?"))
    cost = data.get("energy_cost", "?")
    rarity = data.get("rarity", "")
    target = data.get("target", "")
    desc = data.get("description", "")
    upg = data.get("upgraded_description", "")
    character = data.get("character", "")

    header = f"\n{title}"
    if character:
        header += f"  [{character}]"
    header += f"  Cost {cost}"
    if rarity:
        header += f"  {rarity}"
    if target:
        header += f"  →{target}"

    lines = [header]
    lines.append(f"  {desc}")
    if upg:
        lines.append(f"  (upgraded: {upg})")

    notes = data.get("notes", "")
    if notes:
        lines.append(f"\n  Notes: {notes}")

    # Show vars
    vars_ = data.get("vars", [])
    if vars_ and "vars" not in desc.lower():
        for v in vars_:
            vtype = v.get("type", "")
            base = v.get("base_value", "")
            upg_v = v.get("upgraded_value", "")
            if vtype == "Damage":
                lines.append(f"  Damage: {base} (upgraded: {upg_v})")

    return "\n".join(lines)


def format_encounter(data: dict) -> str:
    if not data:
        return "Not found."
    title = data.get("title", data.get("class_name", "?"))
    room = data.get("room_type", "")
    monsters = data.get("monsters", [])
    total = data.get("total_monsters", "")
    notes = data.get("notes", "")
    tags = data.get("tags", [])

    lines = [f"\n{title}  [{room}]  ({total} monster{'s' if total != 1 else ''})"]
    _, encounter_index = _act_context()
    acts = encounter_index.get(data.get("class_name", ""), [])
    if acts:
        lines.append(f"  Acts: {', '.join(acts)}")
    if monsters:
        lines.append(f"  Monsters: {', '.join(monsters)}")
    if tags:
        lines.append(f"  Tags: {', '.join(tags)}")
    if notes:
        lines.append(f"\n  Notes: {notes}")
    return "\n".join(lines)


# ── main ────────────────────────────────────────────────────────


def _dedup(seq: list[str]) -> list[str]:
    return list(dict.fromkeys(seq))


def find_files(name: str, subdir: str, fuzzy: bool) -> list[str]:
    """Resolve a query to one or more class_names.

    Lookup order:
      1. Exact / case-insensitive class_name (file stem)
      2. Exact human title (e.g. "Leaf Slime (S)")
      3. Base name with variant suffix stripped (e.g. "Leaf Slime" -> both sizes)
      4. Substring match on class_name
      5. Substring match on human title
      6. Fuzzy match across class_names and titles (only when --fuzzy)
    """
    candidates = list_json_files(subdir)
    if not candidates:
        return []
    name_lower = name.lower()

    if name in candidates:
        return [name]
    for c in candidates:
        if c.lower() == name_lower:
            return [c]

    title_index, base_index = build_title_index(subdir)

    if name_lower in title_index:
        return _dedup(title_index[name_lower])
    if name_lower in base_index:
        return _dedup(base_index[name_lower])

    if len(name) >= 3:
        stem_matches = [c for c in candidates if name_lower in c.lower()]
        if stem_matches:
            return stem_matches

        title_matches: list[str] = []
        for norm, classes in title_index.items():
            if name_lower in norm:
                title_matches.extend(classes)
        if title_matches:
            return _dedup(title_matches)

    if fuzzy:
        title_keys = list(title_index.keys())
        base_keys = list(base_index.keys())
        matched = fuzzy_match(name, candidates + title_keys + base_keys)
        if matched:
            if matched in title_index:
                return _dedup(title_index[matched])
            if matched in base_index:
                return _dedup(base_index[matched])
            return [matched]
    return []


def main():
    import argparse

    parser = argparse.ArgumentParser(
        description="Quick lookup for STS2 enemies and cards",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Examples:\n"
            "  lookup enemy Nibbit\n"
            "  lookup enemy 'Leaf Slime'        # base name returns both variants\n"
            "  lookup card Bash\n"
            "  lookup enemy Fogmog --pattern\n"
            "  lookup card Conflagration -f\n"
        ),
    )
    parser.add_argument(
        "category",
        choices=["enemy", "card", "encounter", "list"],
        help="Category to look up, or 'list' to show all",
    )
    parser.add_argument(
        "name", nargs="?", default=None, help="Name of the entity (required unless category=list)"
    )
    parser.add_argument("--pattern", action="store_true", help="Show only move pattern (enemies)")
    parser.add_argument("--json", action="store_true", help="Output raw JSON")
    parser.add_argument("-f", "--fuzzy", action="store_true", help="Fuzzy match")
    args = parser.parse_args()

    subdir_map = {
        "enemy": "monsters",
        "card": "cards",
        "encounter": "encounters",
        "list": "monsters",
    }
    subdir = subdir_map[args.category]

    if args.category == "list":
        # Determine subdir from name if given (e.g., list card, list enemy)
        if args.name and args.name in subdir_map:
            subdir = subdir_map[args.name]
        candidates = list_json_files(subdir)
        if args.name and args.name not in subdir_map:
            matches = [c for c in candidates if args.name.lower() in c.lower()]
            if matches:
                print("\n".join(matches))
            else:
                print(f"No matches for '{args.name}'")
        else:
            print(f"{len(candidates)} items")
            print("\n".join(candidates))
        return

    if not args.name:
        print("Error: name is required (or use 'list' category)", file=sys.stderr)
        sys.exit(1)

    names = find_files(args.name, subdir, args.fuzzy)
    if not names:
        print(f"Not found: '{args.name}'", file=sys.stderr)
        # Show closest matches across both stems and human titles.
        candidates = list_json_files(subdir)
        title_index, _ = build_title_index(subdir)
        q = args.name.lower()
        scored = [(c, levenshtein(q, c.lower())) for c in candidates]
        scored.extend((t, levenshtein(q, t)) for t in title_index)
        scored.sort(key=lambda x: x[1])
        seen: set[str] = set()
        top: list[tuple[str, int]] = []
        for label, dist in scored:
            if label in seen:
                continue
            seen.add(label)
            top.append((label, dist))
            if len(top) == 5:
                break
        if top:
            print("Closest matches:", ", ".join(f"{m[0]}({m[1]})" for m in top), file=sys.stderr)
        sys.exit(1)

    if args.json:
        if len(names) == 1:
            print(json.dumps(load_json(subdir, names[0]), indent=2))
        else:
            print(json.dumps([load_json(subdir, n) for n in names], indent=2))
        return

    formatter = {
        "enemy": lambda d: format_enemy(d, args.pattern),
        "card": format_card,
        "encounter": format_encounter,
    }[args.category]

    if len(names) > 1:
        print(f"Matched {len(names)} entries: {', '.join(names)}", file=sys.stderr)
    for i, n in enumerate(names):
        if i > 0:
            print("\n" + "─" * 60)
        print(formatter(load_json(subdir, n)))


if __name__ == "__main__":
    main()
