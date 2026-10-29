# Mechanics Source

The decompilation of a personally-owned copy of the game, the data and art
extracted from it, and the static wiki generated on top. The upstream leaf:
it depends on no sibling, and everything else consumes it read-only.

Terms shared with the rest of the monorepo live in
[../CONTEXT-MAP.md](../CONTEXT-MAP.md#shared-language).

## Language

**Decomp**:
The C# decompilation under `decompiled/`. The authoritative answer to any question about what the game does — it outranks every other context, this glossary included.
_Avoid_: bare "oracle" (overloaded across this repo), the source, the C#

**Pinned build**:
The single game version the rest of the monorepo targets, currently `v0.107.1`. A second build is retained for archaeology only; content that exists in one and not the other is a real behavioural difference, not a porting mistake.
_Avoid_: current version, latest, the build

**Extraction**:
Pulling structured data and art out of the decompiled game into files the siblings can read.
_Avoid_: scraping, dumping, parsing

**Localization data**:
The extracted English strings that give cards, relics and powers their displayed text. Versioned like anything else — text from one build describing another build's card is a display bug, not a rules bug.
_Avoid_: loc, strings, copy

**Port**:
A mechanic hand-carried from the decomp into the Combat Engine, and audited back against it afterwards.
_Avoid_: translation, implementation, copy
