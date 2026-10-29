/**
 * Where the game stands the fighters in an encounter that authors its own slots.
 *
 * `EncounterModel.HasScene` is true for twenty-four encounters, and for those
 * `NCombatRoom` does NOT run its row algorithm at all:
 *
 *     if (Encounter.HasScene) PositionCreaturesWithSlots(creatures);
 *     else                    PositionEnemies(creatures, cameraScaling);
 *
 * and `PositionCreaturesWithSlots` reads each creature's position off a
 * `Marker2D` named by its `Creature.SlotName`. The row algorithm gets those
 * twenty-four fights approximately right and no more: it spaces evenly along
 * one line, where the authored slots stagger in depth (the Living Fog's five
 * bombs hang in an arc overhead, the Fabricator's bots sit at four corners).
 *
 * It is also the only correct answer for a mid-fight spawn. Every encounter
 * that spawns has slots, and `NCombatRoom.AddCreature` moves a new creature
 * only when it carries a `SlotName` — so an Ovicopter's eggs and a Living Fog's
 * bombs have nowhere else to come from.
 *
 * WHICH free slot a spawn takes is the summoner's business, not the
 * encounter's, and the game does it BOTH ways — see `SPAWN_PICK_LAST`.
 *
 * The markers themselves are extracted, not written here — see
 * `encounterSlots.data.ts` and `sts2-wiki/scripts/extract_encounter_slots.py`.
 */
import { ENCOUNTER_SLOTS } from "./encounterSlots.data";

/** One authored `Marker2D`, in the scene frame `scene.ts` lays fighters out in:
 *  `x` measured from the scene's centre line, `y` absolute (the ground line is
 *  740). `NCombatRoom` shifts the instanced encounter scene by
 *  `-Vector2(1920, 1080) * 0.5f`, which is what makes `x` centre-relative. */
export interface EncounterSlot {
  name: string;
  x: number;
  y: number;
}

export interface EncounterSlots {
  /** Art slugs of every monster the encounter's own class names. Its handle:
   *  the combat wire carries no encounter id, so the roster is what identifies
   *  a fight — the same trick `BOSS_SCENES` uses to pick a room. */
  roster: string[];
  /** The slots the OPENING roster stands in, in the order the wire sends those
   *  enemies (the engine keeps its enemy list sorted by slot index — see
   *  `CombatState.SortEnemiesBySlotName`). Not simply the first N: the
   *  Two-Tailed Rats declare five slots and open on the last three. */
  start: string[];
  /** Every slot, in `EncounterModel.Slots` order — which is the order
   *  `GetNextSlot` hands them to mid-fight spawns. */
  slots: EncounterSlot[];
}

export type EncounterSlotTable = Record<string, EncounterSlots>;

/** Which end of the still-free slot list a mid-fight spawn takes. */
export type SpawnPick = "first" | "last";

/**
 * The encounters whose summoner takes the LAST free slot.
 *
 * `EncounterModel.GetNextSlot` is `Slots.FirstOrDefault(free)` and is what the
 * Fabricator (`Fabricator.cs:115`) and the Living Fog (`LivingFog.cs:91`) call,
 * so "first" is the default. Two summoners bypass it and scan from the other
 * end with their own `Slots.LastOrDefault(free)`:
 *
 *   * `Ovicopter.LayEggsMove` (`Ovicopter.cs:87`) — its three eggs land in
 *     `egg5`, `egg4`, `egg3`, i.e. next to the Ovicopter (which holds the last
 *     slot of all) rather than at the far end of the row.
 *   * `TwoTailedRat.CallForBackup` (`TwoTailedRat.cs:180`) — the fight opens on
 *     `third`/`fourth`/`fifth`, so backup fills `second` then `first`.
 *
 * Note `CanSummon` (`TwoTailedRat.cs:203`) still gates on `GetNextSlot` being
 * non-empty; that is only an "is any slot free" test, and the slot it actually
 * takes is the one `CallForBackup` picks.
 */
const SPAWN_PICK_LAST: ReadonlySet<string> = new Set([
  "ovicopter_normal",
  "two_tailed_rats_normal",
]);

/** A matched encounter: its slot table plus the id it was matched under and
 *  the end of the free list its summoner spawns from. */
export interface MatchedEncounter extends EncounterSlots {
  id: string;
  spawnPick: SpawnPick;
}

/**
 * The slot a mid-fight spawn takes on this board, or undefined when every slot
 * the encounter declares is already occupied.
 *
 * `taken` is the slots held by the creatures ON THE BOARD — C# scans
 * `CombatState.Enemies`, which a dead creature has already left, so a slot
 * whose occupant is gone is free again.
 */
export function nextSpawnSlot(
  entry: MatchedEncounter,
  taken: ReadonlySet<string>,
): string | undefined {
  const free = entry.slots.filter((s) => !taken.has(s.name));
  const pick = entry.spawnPick === "last" ? free[free.length - 1] : free[0];
  return pick?.name;
}

/**
 * Which slotted encounter this board is, or undefined for "not one of them, or
 * not decidable".
 *
 * Scored, not looked up, because two pairs of these encounters field the same
 * monsters and differ only in how many (Exoskeletons Normal/Weak, Bowlbugs
 * Normal/Weak). The ranking is: fewest enemies the encounter does not field,
 * then most that it does, then the tightest slot list that still holds the
 * board. A tie on all three is genuinely ambiguous and returns undefined — the
 * row algorithm is a decent approximation and a confidently WRONG slot list is
 * not, so an unresolved fight keeps the row.
 *
 * `slugsOf` maps an enemy to the art slugs it could be filed under, most
 * authoritative first (`artSlugs` in `roomScenes.ts`).
 */
export function matchEncounter<T>(
  enemies: T[],
  slugsOf: (e: T) => string[],
): MatchedEncounter | undefined {
  if (!enemies.length) return undefined;
  const slugs = enemies.map(slugsOf);
  /** Lexicographic on the ranking key; negative when `a` is the better match. */
  const cmp = (a: number[], b: number[]) => a.findIndex((v, i) => v !== b[i]) < 0
    ? 0 : a[a.findIndex((v, i) => v !== b[i])] - b[a.findIndex((v, i) => v !== b[i])];

  let best: MatchedEncounter | undefined;
  let bestKey: number[] | undefined;
  let tied = false;
  for (const [id, entry] of Object.entries(ENCOUNTER_SLOTS)) {
    if (entry.slots.length < enemies.length) continue;
    const roster = new Set(entry.roster);
    const hits = slugs.filter((s) => s.some((slug) => roster.has(slug))).length;
    if (hits === 0) continue;
    const key = [enemies.length - hits, -hits, entry.slots.length];
    const order = bestKey ? cmp(key, bestKey) : -1;
    if (order < 0) {
      best = { ...entry, id, spawnPick: SPAWN_PICK_LAST.has(id) ? "last" : "first" };
      bestKey = key; tied = false;
    } else if (order === 0) {
      tied = true;
    }
  }
  return tied ? undefined : best;
}

/** The slot named `name`, or undefined. */
export function slotNamed(entry: EncounterSlots, name: string): EncounterSlot | undefined {
  return entry.slots.find((s) => s.name === name);
}
