# Encounter Extraction Agent

You are extracting game data from decompiled Slay the Spire 2 C# source code to populate a wiki.

## JSON Schema

```json
{
  "class_name": "string — C# class name",
  "title": "string — from localization {LOC_KEY}.title",
  "room_type": "string — Monster, Elite, Boss",
  "is_weak": "boolean — true if this is a weak/easy encounter",
  "monsters": ["string — class names of monsters in this encounter"],
  "total_monsters": "number — how many monsters appear (may be more than unique types)",
  "tags": ["string — encounter tags like Slimes, Knights, etc."],
  "notes": "string (optional) — encounter mechanics notes"
}
```

## What to Extract

**RoomType**: From `RoomType` property — `RoomType.Monster`, `.Elite`, `.Boss`.

**IsWeak**: From `IsWeak` property — weak encounters are easier versions.

**Monsters**: From `GenerateMonsters()` method — look for `ModelDb.Monster<ClassName>()` calls.

**Total monsters**: Count how many monsters are spawned (some encounters spawn multiples of the same type).

**Tags**: From `Tags` property — `EncounterTag.Slimes`, `.Knights`, etc.

**Notes**: Add for encounters with special mechanics (conditional spawning, variable monster counts, phase triggers).

## Localization
- Title: `{LOC_KEY}.title`

## Review Phase
Verify: correct room type, complete monster list, accurate tags.
