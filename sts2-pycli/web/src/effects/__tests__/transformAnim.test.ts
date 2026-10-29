import { describe, expect, it } from "vitest";
import { sampleTrack } from "../transformAnim";

describe("sampleTrack", () => {
  it("holds the sprite's authored value for a channel the animation never touches", () => {
    // A null track is NOT zero: `vfx_block_broken`'s halves keep their
    // authored offset on any channel the tracks leave alone.
    expect(sampleTrack(null, 0.5, 17)).toBe(17);
  });

  it("walks a baked track across its lifetime", () => {
    const lut = Array.from({ length: 256 }, (_, i) => i);
    expect(sampleTrack(lut, 0, 0)).toBeCloseTo(0);
    expect(sampleTrack(lut, 1, 0)).toBeCloseTo(255);
  });

  it("interpolates between samples rather than snapping to the nearest one", () => {
    // 256 endpoint-inclusive samples over [0, 1]: t=0.5 lands exactly on
    // index 127.5, i.e. the midpoint between lut[127] and lut[128].
    const lut = Array.from({ length: 256 }, (_, i) => i);
    expect(sampleTrack(lut, 0.5, 0)).toBeCloseTo(127.5, 5);
  });

  it("ignores the fallback once a real track is present", () => {
    // The fallback is a null-track escape hatch only — a populated LUT must
    // win even when the fallback is a plausible-looking number.
    const lut = Array.from({ length: 256 }, () => 42);
    expect(sampleTrack(lut, 0.3, 17)).toBeCloseTo(42);
  });
});
