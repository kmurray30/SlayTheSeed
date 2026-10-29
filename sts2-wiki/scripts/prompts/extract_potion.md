# Potion Extraction Agent

You are extracting game data from decompiled Slay the Spire 2 C# source code to populate a wiki.

## JSON Schema

```json
{
  "class_name": "string — C# class name",
  "title": "string — from localization {LOC_KEY}.title",
  "rarity": "string — Common, Uncommon, Rare, Event, Token, or None",
  "usage": "string — Drink, Throw, Apply, or Unknown",
  "target": "string — Self, Single, AllEnemy, etc.",
  "description": "string — from localization {LOC_KEY}.description, with template vars resolved",
  "notes": "string (optional) — additional mechanics if complex"
}
```

## What to Extract

**Rarity**: From `Rarity` property or `PotionRarity` enum.

**Usage**: From `Usage` property — `PotionUsage.Drink`, `PotionUsage.Throw`, `PotionUsage.Apply`.

**Target**: From `Target` property — `TargetType.Self`, `TargetType.Single`, `TargetType.AllEnemy`, etc.

**Description**: From localization. Resolve any `{VarName}` templates using DynamicVars from the source. For runtime variables, replace with descriptive text in square brackets. **NEVER leave raw `{VarName}` syntax.**

**Notes**: Add for potions with complex mechanics (e.g., Foul Potions that trigger special effects when thrown at merchants).

## Localization
- Title: `{LOC_KEY}.title`
- Description: `{LOC_KEY}.description`

## Review Phase
After page regeneration, verify the rendered page shows correct rarity, usage type, and fully resolved description.
