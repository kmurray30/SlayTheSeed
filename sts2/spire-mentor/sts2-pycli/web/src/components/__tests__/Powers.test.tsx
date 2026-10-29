import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import Powers from "../Powers";
import { powerColumns } from "../../powerRow";
import type { Power } from "../../api";

const powers = (n: number): Power[] =>
  Array.from({ length: n }, (_, i) => ({
    name: `Power ${i}`, amount: i + 1, image: `/p${i}.png`,
  })) as Power[];

const rowsOf = (c: HTMLElement) => {
  const grid = c.querySelector<HTMLElement>("div")!;
  const cols = grid.style.gridTemplateColumns.match(/repeat\((\d+)/)![1];
  return Math.ceil(c.querySelectorAll("img").length / Number(cols));
};

describe("the power row", () => {
  // `NPowerContainer.UpdatePositions` never opens a third row: the row is at
  // least half the powers wide, and past the creature's box the game widens it
  // instead. That cap is what keeps the block off the hand strip below it — a
  // narrow creature with ten powers used to stack five rows, ~140px of icons
  // reaching down through the fan (GH: "enemy buffs hidden behind the hand").
  it("never runs to a third row, at any count or creature width", () => {
    for (const n of [1, 2, 3, 5, 6, 7, 9, 10, 12, 16]) {
      for (const box of [30, 65, 190, 400, 900]) {
        const cols = powerColumns(n, box, 26);
        expect(Math.ceil(n / cols)).toBeLessThanOrEqual(2);
      }
    }
  });

  it("fills a row as wide as the creature's box before wrapping", () => {
    // 26px icons + 5px gaps: six cells fit in 190px, so six powers stay on one
    // row and the seventh splits 4/3 (ceil(7/2) columns).
    expect(powerColumns(6, 190, 26)).toBe(6);
    expect(powerColumns(7, 190, 26)).toBe(6);
    expect(powerColumns(4, 30, 26)).toBe(2);
  });

  it("lays the icons out in that many columns", () => {
    const { container } = render(<Powers powers={powers(10)} boxWidth={65} />);
    expect(container.querySelectorAll("img")).toHaveLength(10);
    expect(rowsOf(container)).toBe(2);
  });
});
