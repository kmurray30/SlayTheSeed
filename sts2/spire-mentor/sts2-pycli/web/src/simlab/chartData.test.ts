import { describe, it, expect } from "vitest";
import { seriesLabels, cdfPoints, oneIn } from "./chartData";

describe("seriesLabels", () => {
  it("single key -> strategy as label", () => {
    const out = seriesLabels(["MAWLER/0/starter/min_overblock"]);
    expect(out).toHaveLength(1);
    expect(out[0].label).toBe("min_overblock");
    expect(out[0].key).toBe("MAWLER/0/starter/min_overblock");
    expect(out[0].color).toMatch(/^#/);
  });

  it("same enc/asc, varying deck+strategy -> 'deck · strategy'", () => {
    const out = seriesLabels([
      "MAWLER/0/starter/block_first",
      "MAWLER/0/aggro/min_overblock",
    ]);
    expect(out.map((s) => s.label)).toEqual([
      "starter · block_first",
      "aggro · min_overblock",
    ]);
  });

  it("varying only strategy -> just strategy", () => {
    const out = seriesLabels([
      "MAWLER/0/starter/block_first",
      "MAWLER/0/starter/min_overblock",
    ]);
    expect(out.map((s) => s.label)).toEqual(["block_first", "min_overblock"]);
  });

  it("varying enc and asc -> 'enc · asc'", () => {
    const out = seriesLabels([
      "MAWLER/0/starter/block_first",
      "VANTOM/2/starter/block_first",
    ]);
    expect(out.map((s) => s.label)).toEqual(["MAWLER · 0", "VANTOM · 2"]);
  });

  it("assigns stable distinct colors by index", () => {
    const out = seriesLabels([
      "MAWLER/0/starter/a",
      "MAWLER/0/starter/b",
      "MAWLER/0/starter/c",
    ]);
    const colors = out.map((s) => s.color);
    expect(new Set(colors).size).toBe(3);
    // Re-running yields the same colors (stable by index).
    const again = seriesLabels([
      "MAWLER/0/starter/a",
      "MAWLER/0/starter/b",
      "MAWLER/0/starter/c",
    ]);
    expect(again.map((s) => s.color)).toEqual(colors);
  });

  it("empty input -> empty", () => {
    expect(seriesLabels([])).toEqual([]);
  });

  it("falls back to the raw key when it is not 4-part", () => {
    const out = seriesLabels(["weird-key", "other-key"]);
    expect(out.map((s) => s.label)).toEqual(["weird-key", "other-key"]);
  });
});

describe("cdfPoints", () => {
  it("[2,2,5] -> x [2,5], y [2/3, 1]", () => {
    expect(cdfPoints([2, 2, 5])).toEqual({ x: [2, 5], y: [2 / 3, 1] });
  });

  it("empty -> empty", () => {
    expect(cdfPoints([])).toEqual({ x: [], y: [] });
  });

  it("sorts and dedupes unsorted input", () => {
    const out = cdfPoints([5, 2, 2]);
    expect(out.x).toEqual([2, 5]);
    expect(out.y).toEqual([2 / 3, 1]);
  });

  it("last y is exactly 1", () => {
    const out = cdfPoints([1, 3, 7, 9]);
    expect(out.y[out.y.length - 1]).toBe(1);
  });
});

describe("oneIn", () => {
  it("0.00025 -> '1 in 4,000'", () => {
    expect(oneIn(0.00025)).toBe("1 in 4,000");
  });

  it("1 -> 'always'", () => {
    expect(oneIn(1)).toBe("always");
  });

  it("0 -> '—'", () => {
    expect(oneIn(0)).toBe("—");
  });

  it("negative or >1 -> '—'", () => {
    expect(oneIn(-0.1)).toBe("—");
    expect(oneIn(1.5)).toBe("—");
  });

  it("rounds to nearest integer with thousands separators", () => {
    expect(oneIn(0.5)).toBe("1 in 2");
    expect(oneIn(0.001)).toBe("1 in 1,000");
  });
});
