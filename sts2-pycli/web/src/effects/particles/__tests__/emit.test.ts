import { describe, it, expect } from "vitest";
import { emissionTimes } from "../emit";
import { makeRng } from "../rng";

describe("emissionTimes", () => {
  const rng = makeRng(0);

  it("emits everything at t=0 when explosiveness is 1", () => {
    const times = emissionTimes(
      { amount: 6, lifetime: 1, explosiveness: 1, randomness: 0 },
      rng,
    );
    expect(times).toEqual([0, 0, 0, 0, 0, 0]);
  });

  it("spreads emission across the lifetime when explosiveness is 0", () => {
    // Godot's DEFAULT. Getting this wrong makes a third of this sub-project's
    // emitters fire as a single pop instead of a stream.
    const times = emissionTimes(
      { amount: 4, lifetime: 1, explosiveness: 0, randomness: 0 },
      rng,
    );
    expect(times).toEqual([0, 0.25, 0.5, 0.75]);
  });

  it("interpolates between the two at 0.9", () => {
    const times = emissionTimes(
      { amount: 4, lifetime: 1, explosiveness: 0.9, randomness: 0 },
      rng,
    );
    expect(times[0]).toBe(0);
    expect(times[3]).toBeGreaterThan(0);
    // Exact arithmetic: 0.75 * (1 - 0.9). IEEE-754 makes 1 - 0.9 come out to
    // 0.09999999999999998, so this is 0.07499999999999998 < 0.075 by
    // floating-point margin only — not headroom the formula was tuned for.
    expect(times[3]).toBeLessThan(0.075);
  });

  it("randomness perturbs lifetime, not emission order", () => {
    const a = emissionTimes(
      { amount: 8, lifetime: 1, explosiveness: 0, randomness: 0.5 },
      makeRng(1),
    );
    expect([...a]).toEqual([...a].sort((x, y) => x - y));
  });

  it("scales emission time by lifetime, not just phase", () => {
    // Every other case here uses lifetime: 1, which can't tell a correct
    // `phase * lifetime` apart from a missing multiply.
    const times = emissionTimes(
      { amount: 4, lifetime: 2, explosiveness: 0, randomness: 0 },
      rng,
    );
    expect(times).toEqual([0, 0.5, 1, 1.5]);
  });
});
