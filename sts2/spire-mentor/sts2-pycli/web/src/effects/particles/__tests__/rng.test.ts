import { describe, it, expect } from "vitest";
import { makeRng } from "../rng";
import { sampleLut, lutSamplePoint } from "../lut";

function take(rng: () => number, n: number): number[] {
  return Array.from({ length: n }, () => rng());
}

describe("makeRng", () => {
  it("is reproducible from a seed", () => {
    expect(take(makeRng(42), 5)).toEqual(take(makeRng(42), 5));
  });

  it("differs between seeds", () => {
    expect(take(makeRng(1), 5)).not.toEqual(take(makeRng(2), 5));
  });

  it("stays in [0, 1)", () => {
    const r = makeRng(7);
    for (let i = 0; i < 10000; i++) {
      const v = r();
      expect(v).toBeGreaterThanOrEqual(0);
      expect(v).toBeLessThan(1);
    }
  });
});

describe("sampleLut", () => {
  it("samples a LUT at its endpoints exactly", () => {
    const lut = [0, 0.5, 1];
    expect(sampleLut(lut, 0)).toBe(0);
    expect(sampleLut(lut, 1)).toBe(1);
  });

  it("clamps outside the domain rather than wrapping", () => {
    const lut = [0, 0.5, 1];
    expect(sampleLut(lut, -1)).toBe(0);
    expect(sampleLut(lut, 2)).toBe(1);
  });

  it("maps index i to t = i / (n - 1), not i / n, at an interior point", () => {
    // Endpoint-inclusive: a 3-entry table's middle entry sits at t = 0.5, so
    // t = 0.25 lands exactly halfway between lut[0] and lut[1]. Under the
    // wrong `i / n` mapping this would come out closer to 0.375, not 0.25.
    const lut = [0, 0.5, 1];
    expect(sampleLut(lut, 0.25)).toBe(0.25);
  });

  it("interpolates linearly between non-endpoint samples", () => {
    const lut = [0, 10, 20, 30];
    // t=0.5 -> pos = 0.5 * 3 = 1.5 -> halfway between lut[1]=10 and lut[2]=20
    expect(sampleLut(lut, 0.5)).toBe(15);
  });

  it("returns the single entry for every t on a 1-entry LUT", () => {
    const lut = [0.42];
    expect(sampleLut(lut, 0)).toBe(0.42);
    expect(sampleLut(lut, 0.5)).toBe(0.42);
    expect(sampleLut(lut, 1)).toBe(0.42);
    expect(sampleLut(lut, -5)).toBe(0.42);
  });

  it("returns 0 for an empty LUT rather than dividing by zero", () => {
    expect(sampleLut([], 0.5)).toBe(0);
  });
});

describe("lutSamplePoint", () => {
  it("collapses to index 0 for length 0 and 1", () => {
    expect(lutSamplePoint(0, 0.5)).toEqual({ i0: 0, i1: 0, f: 0 });
    expect(lutSamplePoint(1, 0.5)).toEqual({ i0: 0, i1: 0, f: 0 });
  });
});
