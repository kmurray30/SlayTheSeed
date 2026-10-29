/** Ascension levels as the game names them.
 *
 *  Text is the game's `ascension.json` (`LEVEL_NN.title` / `.description`);
 *  the cap is `AscensionManager.maxAscensionAllowed = 10` (v0.107.1 decomp) —
 *  the old launcher accepted 20, eleven of which the game cannot play. Every
 *  level stacks the ones below it, which is why the launcher lists the whole
 *  ladder up to the chosen level, as the game's character portrait does. */

export const ASCENSION_MAX = 10;

export type AscensionLevel = { level: number; title: string; description: string };

export const ASCENSION_LEVELS: readonly AscensionLevel[] = [
  { level: 0, title: "No Ascension", description: "Play without any Ascension modifiers." },
  { level: 1, title: "Swarming Elites", description: "[gold]Elites[/gold] spawn more often." },
  { level: 2, title: "Weary Traveler", description: "Ancients only heal [blue]80%[/blue] of your missing HP." },
  { level: 3, title: "Poverty", description: "Enemies and Treasure Chests drop [blue]25%[/blue] less [gold]Gold[/gold]." },
  { level: 4, title: "Tight Belt", description: "Start each run with 1 less potion slot." },
  { level: 5, title: "Ascender's Bane", description: "Start each run [red]Cursed[/red]." },
  { level: 6, title: "Inflation", description: "Removing cards from your deck at the Merchant is more expensive." },
  { level: 7, title: "Scarcity", description: "Rare and Upgraded cards appear less often." },
  { level: 8, title: "Tough Enemies", description: "All enemies are harder to kill." },
  { level: 9, title: "Deadly Enemies", description: "All enemies have deadlier attacks." },
  { level: 10, title: "Double Boss", description: "Fight two bosses at the end of Act 3." },
];

/** The level's own entry, clamped into the ladder. */
export function ascensionLevel(level: number): AscensionLevel {
  const k = Math.max(0, Math.min(ASCENSION_MAX, Math.round(level)));
  return ASCENSION_LEVELS[k];
}

/** Every modifier in force at `level` (A1..level), highest first — the same
 *  list the game's portrait tooltip prints. Empty at A0. */
export function ascensionModifiers(level: number): AscensionLevel[] {
  const k = ascensionLevel(level).level;
  return ASCENSION_LEVELS.slice(1, k + 1).reverse();
}
