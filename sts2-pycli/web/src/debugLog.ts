// Pure formatters for the workbench debug drawer. Kept out of App.tsx so the
// line format is unit-testable without standing up the whole network-driven app.
import type { FaithfulDebug } from "./api";

/** The run settings that head the debug log (engine, brain, search budgets). */
export interface DebugSettings {
  character: string;
  ascension: number;
  seed: string; // raw seed input ("" = random)
  backend: string; // engine
  brain: string; // off-combat brain
  combat: string; // combat preset
  /** Root-parallel workers. Logged because the UI shows the knob, but see
   *  `heuristic_agent.rs`: the play-to-end agent discards it, so this is
   *  always 1 and the search is always single-threaded. */
  threads: number;
  monsterSims: number;
  bossSims: number;
}

/**
 * One-line settings header for the debug drawer, emitted at run start and when
 * the drawer is toggled on mid-run so it always opens with context.
 */
export function debugSettingsLine(s: DebugSettings): string {
  return (
    `⚙ ${s.character} · A${s.ascension} · seed ${s.seed.trim() || "(random)"} · ` +
    `engine ${s.backend} · brain ${s.brain} · combat ${s.combat} · threads ${s.threads} · ` +
    `sims mon ${s.monsterSims} / boss ${s.bossSims}`
  );
}

/**
 * Enrich a run-mode step's one-line summary with the MCTS sim budget actually
 * used for that decision — combat sims / boss flag / seed, or the card-reward
 * boss-sim depth. All values already ride on the `FaithfulDebug` payload; this
 * just surfaces them. Returns the summary unchanged when nothing extra applies.
 */
export function debugStepLine(dbg: FaithfulDebug): string {
  const extra: string[] = [];
  if (dbg.sims != null) extra.push(`sims=${dbg.sims}`);
  if (dbg.is_boss) extra.push("boss");
  if (dbg.seed != null) extra.push(`seed=${dbg.seed}`);
  if (dbg.reward?.sims != null) extra.push(`reward-sims=${dbg.reward.sims}`);
  return extra.length ? `${dbg.summary} · ${extra.join(" ")}` : dbg.summary;
}
