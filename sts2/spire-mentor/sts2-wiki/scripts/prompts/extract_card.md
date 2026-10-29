# Card Extraction Agent

You are extracting game data from decompiled Slay the Spire 2 C# source code to populate a wiki.

## JSON Schema

```json
{
  "class_name": "string — C# class name",
  "title": "string — from localization {LOC_KEY}.title",
  "character": "string — Ironclad, Silent, Defect, Necrobinder, Regent, Colorless, Curse, Status, Event, Token, Quest",
  "energy_cost": "number — base energy cost (0 for free, -1 for X-cost, -2 for unplayable)",
  "type": "string — Attack, Skill, Power, Curse, Status",
  "rarity": "string — Basic, Common, Uncommon, Rare, Special, Curse, Event, Token",
  "target": "string — Self, Single, AllEnemy, All, None",
  "keywords": ["string — Exhaust, Ethereal, Innate, Retain, Replay, Eternal, Unplayable, etc."],
  "vars": [
    {
      "type": "string — Damage, Block, Energy, Cards, HpLoss, Summon, Forge, Repeat, Magic, etc.",
      "base_value": "number",
      "upgraded_value": "number (optional — only if upgrade changes this var)"
    }
  ],
  "description": "string — from localization, with template vars resolved using base values. Use game rich text tags.",
  "upgraded_description": "string (optional) — only if upgrade changes the description text, resolved with upgraded values",
  "upgraded_cost": "number (optional) — only if upgrade changes the cost",
  "x_cost": "boolean — true if the card costs X energy",
  "star_cost": "number (optional) — if the card costs Star resource instead of energy",
  "referenced_powers": [
    {"class_name": "string", "title": "string", "slug": "string"}
  ],
  "notes": "string (optional) — additional mechanics notes"
}
```

## What to Extract

### Basic Properties
From the constructor or property overrides:
- `EnergyCost` / `BaseCost` → energy_cost
- `CardType` → type
- `CardRarity` → rarity
- `TargetType` → target

### Character/Pool
Determined by which card pool the card belongs to. The card pool namespace tells you: `IroncladCardPool` → Ironclad, `SilentCardPool` → Silent, etc.

### Keywords
From `Keywords` property or constructor. Common ones: Exhaust, Ethereal, Innate, Retain, Replay, Eternal, Unplayable.

### Dynamic Variables (vars)
From `CanonicalVars` property. Each `DamageVar`, `BlockVar`, `IntVar`, etc. maps to a var:
- `DamageVar("name", Nm, ...)` → type "Damage", base_value N
- `BlockVar(Nm, ...)` → type "Block", base_value N
- `IntVar("name", Nm)` → type based on the name (Cards, HpLoss, Forge, etc.)

### Description
From localization `{LOC_KEY}.description`. Resolve `{VarName}` using the base var values:
- `{Damage}` → the damage value
- `{Block}` → the block value
- `{VarName:energyIcons()}` → "N Energy"
- `{VarName:plural:singular|plural}` → appropriate form

**NEVER leave raw `{VarName}` syntax in the output.**

### Upgrade
From `OnUpgrade()` method. Common upgrade patterns:
- `UpgradeDamage(N)` → upgraded_value for damage var
- `UpgradeBlock(N)` → upgraded_value for block var
- `UpgradeCost(N)` → upgraded_cost
- Adding/removing keywords
- Changing var values

If the upgrade changes the description text (not just numbers), provide upgraded_description with vars resolved at upgraded values.

### Referenced Powers
From `PowerCmd.Apply<PowerName>` or similar in the card's play methods.

## Localization
- Title: `{LOC_KEY}.title`
- Description: `{LOC_KEY}.description`
- Upgraded description (if different): `{LOC_KEY}.upgradedDescription`

## Review Phase
Verify: correct energy cost, all vars resolved in description, upgrade info accurate, keywords complete.
