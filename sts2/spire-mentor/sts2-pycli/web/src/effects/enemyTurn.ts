import type { CombatState } from "../api";

// Identify which enemies attacked during the enemy turn, so the board can play
// their attack-lunge clip. The workbench only surfaces player-decision states,
// so a whole enemy turn collapses into one snapshot transition: the player's
// `round` increments. An enemy that was telegraphing an "Attack" intent in the
// PREVIOUS snapshot and is still alive in the next one is one that just struck.
//
// Returns the stable indices of attacking enemies, or [] when this transition
// is not an enemy turn (same round → the player is still mid-turn).
export function attackingEnemies(prev: CombatState, next: CombatState): number[] {
  if (!(next.round > prev.round)) return [];
  const alive = new Set((next.enemies ?? []).map((e) => e.index));
  return (prev.enemies ?? [])
    .filter((e) => alive.has(e.index) && e.intents?.[0]?.type === "Attack")
    .map((e) => e.index);
}
