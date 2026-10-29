# Relic Extraction Agent

You are extracting game data from decompiled Slay the Spire 2 C# source code to populate a wiki.

## JSON Schema

```json
{
  "class_name": "string — C# class name",
  "title": "string — from localization {LOC_KEY}.title",
  "rarity": "string — Common, Uncommon, Rare, Shop, Event, Starter, Ancient, None",
  "character": "string (optional) — character-specific relic lock, e.g. 'Ironclad'",
  "description": "string — from localization {LOC_KEY}.description, with vars resolved",
  "flavor": "string (optional) — from localization {LOC_KEY}.flavor",
  "notes": "string (optional) — complex mechanics notes"
}
```

## What to Extract

**Rarity**: From `Rarity` property — `RelicRarity.Common`, `.Uncommon`, `.Rare`, `.Shop`, `.Event`, `.Starter`, `.Ancient`.

**Character**: Some relics are locked to specific characters via pool assignment.

**Description**: From localization. Resolve all template vars. **NEVER leave raw `{VarName}` syntax.**

**Flavor**: From localization `{LOC_KEY}.flavor` if it exists.

**Notes**: Add for relics with complex mechanics (e.g., conditional triggers, stacking effects, enchantment application).

## Localization
- Title: `{LOC_KEY}.title`
- Description: `{LOC_KEY}.description`
- Event description: `{LOC_KEY}.eventDescription`
- Flavor: `{LOC_KEY}.flavor`

## Review Phase
Verify: correct rarity, description fully resolved, flavor text included if available.
