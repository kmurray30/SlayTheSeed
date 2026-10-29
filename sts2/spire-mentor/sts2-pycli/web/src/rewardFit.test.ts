import { describe, it, expect } from "vitest";
import { fitRewardCardWidth } from "./rewardFit";
import { cardBoxHeight } from "./cardGeometry";

const LADDER = [199, 149, 119];

/** The composition, as measured on a real screen: everything but the card row
 *  (banner, ledger, Skip plate, paddings) plus one row of cards at `w`. */
const contentAt = (w: number, other: number) => other + cardBoxHeight(w);

describe("fitRewardCardWidth", () => {
  it("keeps the authored width when the scene has room", () => {
    const other = 260;
    expect(fitRewardCardWidth(LADDER, 900, contentAt(199, other), cardBoxHeight(199), 199))
      .toBe(199);
  });

  it("comes down a rung when the composition overflows the scene", () => {
    // The tablet case: ~517px of screen box against a composition that wants
    // ~557. One rung down (149 -> 210px of card) fits with room to spare.
    const other = 277;
    expect(fitRewardCardWidth(LADDER, 517, contentAt(199, other), cardBoxHeight(199), 199))
      .toBe(149);
  });

  it("is stable: re-measuring at the chosen rung returns the same rung", () => {
    const other = 277;
    const first = fitRewardCardWidth(LADDER, 517, contentAt(199, other), cardBoxHeight(199), 199);
    const again = fitRewardCardWidth(
      LADDER, 517, contentAt(first, other), cardBoxHeight(first), first);
    expect(again).toBe(first);
  });

  it("stops at the smallest rung rather than inventing a width", () => {
    // Nothing on the ladder fits a 200px box; a soft card beats no card, and a
    // width off the ladder would be a blurry one.
    expect(fitRewardCardWidth(LADDER, 200, contentAt(199, 277), cardBoxHeight(199), 199))
      .toBe(119);
  });

  it("counts every row when the trio has wrapped", () => {
    // Two rows of cards plus a 16px gap between them. A 720px box would hold
    // one row at the authored width three times over; it holds TWO rows only
    // at 149, and the gap between them has to be paid for as well.
    const rowH = 2 * cardBoxHeight(199) + 16;
    const other = 277;
    expect(fitRewardCardWidth(LADDER, 720, other + rowH, rowH, 199)).toBe(149);
    expect(fitRewardCardWidth(LADDER, 700, other + rowH, rowH, 199)).toBe(119);
  });

  it("holds the authored width until it has been measured", () => {
    // Pre-layout (offsetHeight 0) must not pick a rung off a zero measurement.
    expect(fitRewardCardWidth(LADDER, 0, 0, 0, 199)).toBe(199);
    expect(fitRewardCardWidth(LADDER, 517, 0, 0, 199)).toBe(199);
  });
});
