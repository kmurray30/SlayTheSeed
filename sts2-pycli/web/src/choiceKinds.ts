import type { Step } from "./api";

/** card_select ``context`` values set by the deck-operation flavors (rest-site
 *  Smith, Neow removals/transforms/…) — the card_selects the EV advisor cannot
 *  fork + evaluate. Anything else is an in-combat pending choice, or a
 *  run-level choose screen (Lead Paperweight's 2 Colorless, Hefty Tablet's 3
 *  Rares), both of which it can. */
export const DECK_OP_CONTEXTS = new Set(["upgrade", "remove", "transform", "enchant", "grant"]);

/** True for a card_select that is an in-combat pending choice rather than a
 *  deck operation.
 *
 *  Two screens wear the same `card_select` decision but behave differently
 *  under "play best": an in-combat MULTI-pick is several engine decisions (each
 *  index accumulates and re-opens the same screen) and must be played out to
 *  completion in one press, while a deck op takes one action per press. */
export function isCombatChoiceStep(step: Step): boolean {
  if (step.decision !== "card_select") return false;
  return !DECK_OP_CONTEXTS.has((step.state as { context?: string }).context ?? "");
}
