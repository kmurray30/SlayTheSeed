import type { Score } from "./api";

/** The recommender's best action, mapped to a live-action shape.
 *
 * `unsupported` covers recommender sentinels the combat UI can't dispatch
 * (channel/evoke/pending-choice) — callers should no-op rather than send a
 * bogus action. */
export type BestAction =
  | { kind: "end" }
  | { kind: "play"; cardIndex: number; targetIndex?: number }
  | { kind: "potion"; slot: number; targetIndex?: number }
  | { kind: "unsupported" };

// Sentinel stable_idx values, mirrored from sts2_hint.recommender.
const END_TURN = -1; // "end turn"
const POTION_BASE = -100; // potion slot s -> -100 - s, i.e. range (-200, -100]
const POTION_MIN = -200; // exclusive lower bound for the potion range

/** Decide how the auto-hint latch and on-screen scores should change when the
 *  user clicks the hints toggle. `autoHints` is the state *before* the toggle.
 *
 *  The auto-fetch effect bails while the latch (`autoHintedRef`) is set, so the
 *  latch must be released when hints come back ON — otherwise re-enabling is a
 *  no-op and the toggle looks stuck off (the "enable hints again, stays
 *  disabled" bug). Turning OFF sets the latch (suppress an immediate re-fetch)
 *  and clears the scores already on screen so the off state is visible. */
export function toggleHintsLatch(autoHints: boolean): { latch: boolean; clearScores: boolean } {
  return autoHints ? { latch: true, clearScores: true } : { latch: false, clearScores: false };
}

/** Pick the recommended action from MCTS scores, or null when there are none.
 *  Uses the `is_best` flag, falling back to the highest `win_prob`. */
export function bestAction(scores: Score[]): BestAction | null {
  if (scores.length === 0) return null;
  const best =
    scores.find((s) => s.is_best) ??
    scores.reduce((a, b) => (b.win_prob > a.win_prob ? b : a));
  const si = best.stable_idx;
  const targetIndex = best.target_index ?? undefined;
  if (si === END_TURN) return { kind: "end" };
  // Potion: stable_idx = -100 - slot. Decode the slot for a use_potion action.
  if (si <= POTION_BASE && si > POTION_MIN) return { kind: "potion", slot: POTION_BASE - si, targetIndex };
  // Other negative sentinels (channel -200, evoke -201, choose -300-i) have no
  // combat-UI dispatch path — don't mis-send them as a card index.
  if (si < 0) return { kind: "unsupported" };
  return { kind: "play", cardIndex: si, targetIndex };
}
