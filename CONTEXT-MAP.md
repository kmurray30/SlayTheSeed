# Context Map

This repo holds four contexts with independent build systems and genuinely
different vocabularies. Read the map, then read the `CONTEXT.md` for the
context you're working in.

These are glossaries — vocabulary only. For build commands, test gates and
workflow, the per-subproject `CLAUDE.md` files stay authoritative. For how the
pieces fit together, see [ARCHITECTURE.md](./ARCHITECTURE.md).

## Contexts

- [Combat Engine](./sts2-rs/CONTEXT.md) — the Rust simulation, its search, and run-AI serving
- [Integration Hub](./sts2-pycli/CONTEXT.md) — the C# headless oracle, the parity harness, PPO training, the workbench
- [Mechanics Source](./sts2-wiki/CONTEXT.md) — the decompilation, data/art extraction, the static wiki
- [Live-Game Bridge](./spire-mentor-mod/CONTEXT.md) — the Spire Mentor mod inside the running game

## Relationships

- **Mechanics Source → everything**: the decomp is read-only upstream of all
  three others and depends on no sibling. On any question of what the game
  does, it wins over every other context, including this map.
- **Integration Hub → Combat Engine**: an editable path dependency on
  `crates/sts2-py`, exposed to Python as the `sts2_rs` module.
- **Integration Hub ↔ Combat Engine**: the parity harness drives the headless
  oracle and the engine from identical seeds and diffs the result.
- **Integration Hub → Combat Engine**: `champions.toml` generates marked
  regions in Rust source and in the `Dockerfile`. The direction is
  deliberate — see ADR-0001.
- **Live-Game Bridge → Integration Hub**: deploy and packaging only; the mod
  compiles with zero sibling files.

## Shared Language

Slay the Spire 2's own vocabulary, used identically across all four contexts.
These terms are *defined by the game*, not by us — the decomp is authoritative
and a disagreement between this section and the decomp means this section is
wrong.

**Run**:
One playthrough from character selection to death or victory, spanning three acts.
_Avoid_: game, session, playthrough

**Combat**:
A single fight against one encounter, from the first turn to the last enemy dying or the player dying.
_Avoid_: battle, fight, encounter

**Encounter**:
The composition of enemies a combat is fought against, drawn from a per-act pool.
_Avoid_: fight, monster group

**Act**:
One of the three sequential map regions of a run, each ending in a boss.
_Avoid_: chapter, level, stage

**Ascension**:
A difficulty tier from A0 to A10 that layers on stricter run and combat rules.
_Avoid_: difficulty, level

**Character**:
One of the five playable classes: Ironclad, Silent, Defect, Regent, Necrobinder.
_Avoid_: class, hero, archetype

**Card**:
A playable deck member with a cost and an effect. Cards may be upgraded, and an upgraded card is a distinct card from its base copy.
_Avoid_: move, action, play

**Relic**:
A permanent passive item held for the rest of the run once acquired.
_Avoid_: item, artifact, trinket

**Potion**:
A consumable held in a limited number of slots and drunk for a one-off effect.
_Avoid_: consumable, item

**Power**:
A stacking status on a combatant that modifies the rules for the rest of the combat.
_Avoid_: buff, debuff, status, effect

**Intent**:
The action an enemy has committed to for the coming turn, visible to the player before they act.
_Avoid_: telegraph, plan, next move

**Decision point**:
A moment where the game blocks for a choice — a card to play, a path to take, a reward to pick. The unit at which the parity harness compares state and at which an agent acts.
_Avoid_: turn, step, action point

**Build id**:
The game version a run was played under, e.g. `v0.107.1`. Runs from other builds are played under different rules and must be filtered out, never mixed.
_Avoid_: version, patch
