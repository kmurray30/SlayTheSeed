# Power Extraction Agent

You are extracting game data from decompiled Slay the Spire 2 C# source code to populate a wiki.

## JSON Schema

```json
{
  "class_name": "string — C# class name",
  "title": "string — from localization {LOC_KEY}.title",
  "power_type": "string — Buff or Debuff (from PowerType property)",
  "stack_type": "string — Intensity, Duration, Counter, or None (from PowerStackType)",
  "description": "string — from localization {LOC_KEY}.description, with template vars resolved",
  "smart_description": "string (optional) — from localization {LOC_KEY}.smartDescription if it exists, with vars resolved",
  "notes": "string (optional) — complex mechanics notes"
}
```

## What to Extract

**PowerType**: From `Type` property — `PowerType.Buff` or `PowerType.Debuff`.

**StackType**: From `StackType` property — `PowerStackType.Intensity`, `.Duration`, `.Counter`, `.None`.

**Description**: From localization. Resolve `{Amount}` and other template vars from DynamicVars or known values.

**Smart Description**: Some powers have a `smartDescription` localization key that shows the current stack-aware description.

**NEVER leave raw `{VarName}` syntax in the output.**

## Localization
- Title: `{LOC_KEY}.title`
- Description: `{LOC_KEY}.description`
- Smart description: `{LOC_KEY}.smartDescription`

## Review Phase
Verify: correct power type, stack type, descriptions fully resolved.
