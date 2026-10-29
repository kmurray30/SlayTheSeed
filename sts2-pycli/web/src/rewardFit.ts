/** Which reward-card size the "Choose a Card" scene has room for.
 *
 *  The scene is `overflow: hidden` (it is a five-plane painted room, not a
 *  scroll region), so a composition taller than its box does not scroll — it is
 *  CLIPPED, and what gets clipped is the bottom: the feet of the trio and the
 *  whole Skip plate. That is what a tablet in landscape saw, where the screen
 *  box under the chrome is ~517px against a composition that wants ~557.
 *
 *  Rather than re-tune the whole rhythm per screen size, only the trio moves,
 *  and only onto the ladder of CLEAN minifications of the source art (598/3,
 *  598/4, 598/5 — see REWARD_CARD_LADDER): the browser has no mipmaps, so an
 *  arbitrary "whatever fits" width just trades a clipped card for a soft one.
 */
import { cardBoxHeight } from "./cardGeometry";

/**
 * The largest width in `ladder` whose card row still fits `avail`.
 *
 * Everything but the row is taken as measured and width-invariant, which is
 * what makes this stable: the answer does not depend on the width currently
 * rendered, so a re-measure after a rung change returns the same rung rather
 * than flip-flopping between two of them.
 *
 * @param ladder   candidate card widths, largest first
 * @param avail    the scene box's inner height
 * @param contentH the composition's natural height at `cardW`
 * @param rowH     the card row's height at `cardW` (may be several wrapped rows)
 * @param cardW    the width `contentH` / `rowH` were measured at
 */
export function fitRewardCardWidth(
  ladder: readonly number[],
  avail: number,
  contentH: number,
  rowH: number,
  cardW: number,
): number {
  const widest = ladder[0];
  if (!(avail > 0) || !(contentH > 0) || !(rowH > 0)) return widest;
  const cardH = cardBoxHeight(cardW);
  // How many rows the trio wrapped into, and what the gaps between them cost.
  // Both survive a width change (a narrower card cannot need MORE rows), so
  // they can be subtracted out of the budget as constants.
  const rows = Math.max(1, Math.round(rowH / cardH));
  const gaps = Math.max(0, rowH - rows * cardH);
  const other = contentH - rowH; // banner, ledger, Skip plate, the paddings
  const budget = avail - other - gaps;
  return ladder.find((w) => rows * cardBoxHeight(w) <= budget) ?? ladder[ladder.length - 1];
}
