#!/usr/bin/env python3
"""Generate Astro content collection markdown files from extracted event data."""

import argparse
import json
import os
import re
import sys
from pathlib import Path

# Hand-written event enrichments live in scripts/event_enrichments.py, which
# is excluded from public exports — fall back to no enrichments without it.
# The second import path covers plain-script invocation
# (`python scripts/generate_events.py …`), where only scripts/ is on sys.path.
try:
    from scripts.event_enrichments import _EVENT_ENRICHMENTS
except ImportError:
    try:
        from event_enrichments import (  # type: ignore[no-redef, import-not-found]
            _EVENT_ENRICHMENTS,
        )
    except ImportError:
        print(
            "note: event_enrichments.py not found — generating without "
            "hand-written event enrichments",
            file=sys.stderr,
        )
        _EVENT_ENRICHMENTS = {}


def slugify(name: str) -> str:
    s = name.lower()
    s = re.sub(r"[^a-z0-9]+", "-", s)
    return s.strip("-")


def load_generated_ref_map(content_dir: str) -> dict[str, dict[str, str]]:
    """Build a class_name -> {title, slug} map from already-generated content
    markdown files. The page slug is the filename stem, which already accounts
    for collision dedup (e.g. FakeAnchor -> "anchor-fakeanchor"). Used so that
    event card_refs / relic_refs link to the real page slugs rather than the
    guessed slugs produced by the LLM extractor."""
    ref_map: dict[str, dict[str, str]] = {}
    if not os.path.isdir(content_dir):
        return ref_map
    for fname in os.listdir(content_dir):
        if not fname.endswith(".md"):
            continue
        slug = fname.removesuffix(".md")
        class_name = ""
        title = ""
        with open(os.path.join(content_dir, fname)) as f:
            for line in f:
                if line.startswith("class_name:"):
                    class_name = line.split(":", 1)[1].strip().strip('"')
                elif line.startswith("title:"):
                    title = line.split(":", 1)[1].strip().strip('"')
                elif line.startswith("---") and class_name:
                    break
        if class_name:
            ref_map[class_name] = {"title": title or class_name, "slug": slug}
    return ref_map


def resolve_refs(refs: list[dict], ref_map: dict[str, dict[str, str]]) -> list[dict]:
    """Rewrite each ref's title/slug to the canonical generated-page values,
    keyed by class_name. Refs whose class_name has no generated page are left
    unchanged."""
    resolved = []
    for ref in refs:
        cname = ref.get("class_name", "")
        if cname in ref_map:
            resolved.append({"class_name": cname, **ref_map[cname]})
        else:
            resolved.append(ref)
    return resolved


def escape_yaml(value: str) -> str:
    if not value:
        return '""'
    if value.lower() in ("null", "true", "false", "yes", "no", "on", "off", "~"):
        return json.dumps(value)
    if any(c in value for c in ":{}\n[]#&*!|>'\"%@`"):
        return json.dumps(value)
    return value


def render_description_html(desc: str) -> str:
    """Convert game rich text tags to HTML."""
    from scripts.common import rich_text_to_html

    return rich_text_to_html(desc)


def strip_tags(desc: str) -> str:
    """Strip game rich text tags for plain text."""
    from scripts.common import strip_rich_text

    return strip_rich_text(desc)


def _humanize_condition(cond: str) -> str:
    """Convert machine-readable conditions to human-friendly text."""
    import re as re_mod

    # "Gold >= 100" -> "At least 100 Gold"
    m = re_mod.match(r"Gold\s*>=\s*(\d+)", cond)
    if m:
        return f"At least {m.group(1)} Gold"
    # "HP >= 12" -> "At least 12 HP"
    m = re_mod.match(r"HP\s*>=\s*(\d+)", cond)
    if m:
        return f"At least {m.group(1)} HP"
    # "HP > 5" -> "More than 5 HP"
    m = re_mod.match(r"HP\s*>\s*(\d+)", cond)
    if m:
        return f"More than {m.group(1)} HP"
    # "Floor >= 6" -> "Floor 6 or later"
    m = re_mod.match(r"Floor\s*>=\s*(\d+)", cond)
    if m:
        return f"Floor {m.group(1)} or later"
    # "Floor > 6" -> "After floor 6"
    m = re_mod.match(r"Floor\s*>\s*(\d+)", cond)
    if m:
        return f"After floor {m.group(1)}"
    # "Max HP >= N" -> "At least N Max HP"
    m = re_mod.match(r"Max HP\s*>=\s*(\d+)", cond)
    if m:
        return f"At least {m.group(1)} Max HP"
    # "Deck size >= N" -> "At least N cards in deck"
    m = re_mod.match(r"Deck size\s*>=\s*(\d+)", cond)
    if m:
        return f"At least {m.group(1)} cards in deck"
    return cond


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate event content files")
    parser.add_argument("data_dir", help="Path to versioned data directory")
    parser.add_argument("output_dir", help="Path to content/events/ directory")
    args = parser.parse_args()

    data_dir = os.path.expanduser(args.data_dir)
    output_dir = os.path.expanduser(args.output_dir)

    # Load legacy aggregate events.json if present (produced by the old regex
    # extractor). Now that every version has per-entity JSONs from the LLM
    # extractor, this file is optional and used only as a fallback for fields
    # not yet in per-entity data.
    aggregate_path = os.path.join(data_dir, "events.json")
    events: list[dict] = []
    if os.path.exists(aggregate_path):
        with open(aggregate_path) as f:
            events = json.load(f)

    # Load per-entity JSON from data/{version}/events/*.json
    per_entity_dir = os.path.join(data_dir, "events")
    per_entity_data: dict[str, dict] = {}
    if os.path.isdir(per_entity_dir):
        for fname in os.listdir(per_entity_dir):
            if fname.endswith(".json"):
                with open(os.path.join(per_entity_dir, fname)) as f:
                    entity_data = json.load(f)
                cname = entity_data.get("class_name", fname.removesuffix(".json"))
                per_entity_data[cname] = entity_data

    # Merge per-entity data over aggregate data (per-entity takes precedence)
    events_by_class = {e["class_name"]: e for e in events}
    for cname, entity_data in per_entity_data.items():
        if cname in events_by_class:
            events_by_class[cname].update(entity_data)
        else:
            events_by_class[cname] = entity_data
    events = list(events_by_class.values())

    # Build canonical class_name -> {title, slug} maps from the already-generated
    # card and relic content (generate-cards / generate-relics run before this
    # step), so event card_refs / relic_refs link to the real page slugs.
    content_root = os.path.dirname(output_dir)
    card_ref_map = load_generated_ref_map(os.path.join(content_root, "cards"))
    relic_ref_map = load_generated_ref_map(os.path.join(content_root, "relics"))

    # Load override directory path
    overrides_dir = os.path.join(os.path.dirname(os.path.dirname(data_dir)), "overrides", "events")

    out = Path(output_dir)
    if out.exists():
        for p in out.glob("*.md"):
            p.unlink()
    out.mkdir(parents=True, exist_ok=True)

    count = 0
    for event in events:
        slug = slugify(event.get("title", event["class_name"]))
        desc = event.get("description", "")

        # Skip placeholder descriptions
        if desc.lower() in ("placeholder", "todo", "tbd"):
            desc = ""

        # Enrich events with code-defined descriptions and options
        enrichments = _EVENT_ENRICHMENTS.get(event["class_name"])
        if enrichments:
            if enrichments.get("description"):
                desc = enrichments["description"]
            if enrichments.get("options"):
                event["options"] = enrichments["options"]
            if enrichments.get("option_overrides"):
                for opt in event.get("options", []):
                    override = enrichments["option_overrides"].get(opt.get("title", ""))
                    if override:
                        if "title" in override:
                            opt["title"] = override["title"]
                        if "description" in override:
                            opt["description"] = override["description"]

        # Merge "Locked"/"Broke" options into their corresponding unlocked options
        raw_options = event.get("options", [])
        merged_options: list[dict[str, str]] = []
        i = 0
        while i < len(raw_options):
            opt = raw_options[i]
            title = opt.get("title", "")
            if title in ("Locked", "Broke"):
                lock_desc = opt.get("description", "")
                # Locked before its unlocked counterpart: attach to next option
                if i + 1 < len(raw_options) and raw_options[i + 1].get("title", "") not in (
                    "Locked",
                    "Broke",
                ):
                    next_opt = dict(raw_options[i + 1])
                    next_opt["requires"] = lock_desc
                    merged_options.append(next_opt)
                    i += 2
                    continue
                # Orphaned locked option (e.g. ZenWeaver shared lock, EndlessConveyor "Broke")
                # Skip it — the requirement info is already in the option descriptions
                i += 1
                continue
            # Check if the next option is a Locked variant of this one
            if i + 1 < len(raw_options) and raw_options[i + 1].get("title", "") in (
                "Locked",
                "Broke",
            ):
                opt = dict(opt)
                opt["requires"] = raw_options[i + 1].get("description", "")
                merged_options.append(opt)
                i += 2
                continue
            merged_options.append(opt)
            i += 1
        event["options"] = merged_options

        conditions = event.get("conditions", [])
        conditions = [_humanize_condition(c) for c in conditions]
        conditions_str = "; ".join(conditions) if conditions else ""

        lines = ["---"]
        lines.append(f"title: {escape_yaml(event.get('title', event['class_name']))}")
        lines.append(f"class_name: {escape_yaml(event['class_name'])}")
        lines.append(f"description_plain: {escape_yaml(strip_tags(desc))}")
        lines.append(f"description_html: {escape_yaml(render_description_html(desc))}")
        lines.append(f"options: {json.dumps(event.get('options', []))}")
        lines.append(f"acts: {json.dumps(event.get('acts', []))}")
        lines.append(f"conditions: {escape_yaml(conditions_str)}")
        notes = ""
        if enrichments and enrichments.get("notes"):
            notes = enrichments["notes"]
        elif event.get("notes"):
            notes = event["notes"]
        if notes:
            lines.append(f"notes: {escape_yaml(render_description_html(notes))}")
        card_refs = resolve_refs(event.get("card_refs", []), card_ref_map)
        relic_refs = resolve_refs(event.get("relic_refs", []), relic_ref_map)
        lines.append(f"card_refs: {json.dumps(card_refs)}")
        lines.append(f"relic_refs: {json.dumps(relic_refs)}")
        lines.append("---")
        lines.append("")

        filepath = out / f"{slug}.md"
        if filepath.exists():
            slug = f"{slug}-{event['class_name'].lower()}"
            filepath = out / f"{slug}.md"

        # Merge per-page override if it exists
        override_path = os.path.join(overrides_dir, f"{slug}.md")
        if os.path.exists(override_path):
            with open(override_path) as f:
                override_content = f.read().strip()
            if override_content:
                # Parse override: if it has YAML frontmatter, merge fields
                if override_content.startswith("---"):
                    parts = override_content.split("---", 2)
                    if len(parts) >= 3:
                        # Override frontmatter fields into the lines
                        for line in parts[1].strip().split("\n"):
                            if ":" in line:
                                key = line.split(":")[0].strip()
                                # Replace matching line in generated output
                                for idx, gen_line in enumerate(lines):
                                    if gen_line.startswith(f"{key}:"):
                                        lines[idx] = line
                                        break
                        # Append body content
                        body = parts[2].strip()
                        if body:
                            lines.append(body)
                else:
                    # Plain markdown — append after frontmatter
                    lines.append(override_content)

        filepath.write_text("\n".join(lines))
        count += 1

    print(f"Generated {count} event pages in {output_dir}")


if __name__ == "__main__":
    main()
