#!/usr/bin/env python3
"""Audit per-entity split files against their aggregate manifest.

The per-entity ``data/<ver>/<type>/<ClassName>.json`` files are an enrichment of
the aggregate ``data/<ver>/<type>.json`` (they add LLM ``notes`` etc.) and must
carry every localization/display field the aggregate has (``title``,
``description``, ``rarity``, …). A partial/botched commit can strip those fields
while keeping ``notes`` — leaving stubs like ``{"class_name": "...", "notes": ...}``
that silently break display consumers (the workbench, the site generator).

This catches that: for each entity in the aggregate, flag a split file that is
missing a display field the aggregate provides. Exit non-zero if any are stub-ish.

Usage:
    uv run python -m scripts.check_split_completeness v0.107.1
    uv run python -m scripts.check_split_completeness v0.107.1 --type relics
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent

# Display/localization fields a split file must keep if the aggregate has them.
# (Parsed structural fields like vars/moves vary by type, so we key off whatever
# the aggregate actually carries rather than a hard-coded per-type list.)
REQUIRED_IF_PRESENT = (
    "title",
    "description",
    "flavor",
    "rarity",
    "usage",
    "target",
    "smart_description",
    "move_pattern",
    "moves",
)

SPLIT_TYPES = (
    "relics",
    "potions",
    "powers",
    "enchantments",
    "monsters",
    "encounters",
    "cards",
)


def _aggregate_entries(version_dir: Path, type_name: str) -> dict[str, dict]:
    agg_path = version_dir / f"{type_name}.json"
    if not agg_path.is_file():
        return {}
    data = json.loads(agg_path.read_text(encoding="utf-8"))
    entries = data if isinstance(data, list) else data.get(type_name, [])
    return {e["class_name"]: e for e in entries if isinstance(e, dict) and "class_name" in e}


def audit_type(version_dir: Path, type_name: str) -> list[str]:
    """Return human-readable problem lines for one entity type (empty = clean)."""
    agg = _aggregate_entries(version_dir, type_name)
    if not agg:
        return []
    split_dir = version_dir / type_name
    problems: list[str] = []
    for class_name, agg_entry in sorted(agg.items()):
        expected = [f for f in REQUIRED_IF_PRESENT if agg_entry.get(f)]
        split_path = split_dir / f"{class_name}.json"
        if not split_path.is_file():
            problems.append(f"{type_name}/{class_name}: MISSING split file")
            continue
        split = json.loads(split_path.read_text(encoding="utf-8"))
        missing = [f for f in expected if not split.get(f)]
        if missing:
            problems.append(f"{type_name}/{class_name}: dropped {', '.join(missing)}")
    return problems


def main() -> None:
    parser = argparse.ArgumentParser(description="Audit split-file completeness vs aggregate")
    parser.add_argument("version", help="e.g. v0.107.1")
    parser.add_argument("--type", choices=SPLIT_TYPES, help="limit to one entity type")
    args = parser.parse_args()

    version_dir = PROJECT_ROOT / "data" / args.version
    if not version_dir.is_dir():
        sys.exit(f"no such version dir: {version_dir}")

    types = [args.type] if args.type else list(SPLIT_TYPES)
    total = 0
    for type_name in types:
        problems = audit_type(version_dir, type_name)
        total += len(problems)
        clean = len(_aggregate_entries(version_dir, type_name)) - len(problems)
        status = "OK" if not problems else f"{len(problems)} INCOMPLETE"
        print(f"{type_name:13s}: {clean:4d} complete, {status}")
        for line in problems[:8]:
            print(f"    - {line}")
        if len(problems) > 8:
            print(f"    … and {len(problems) - 8} more")

    print()
    if total:
        print(f"FAIL: {total} entity split files are incomplete (stub/partial commit).")
        sys.exit(1)
    print("PASS: every split file carries its aggregate's display fields.")


if __name__ == "__main__":
    main()
