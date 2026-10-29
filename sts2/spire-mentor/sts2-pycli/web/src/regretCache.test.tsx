import { describe, it, expect } from "vitest";
import { shouldRerunRegret } from "./regretCache";

describe("shouldRerunRegret", () => {
  it("re-runs when there is no cached analysis", () => {
    expect(shouldRerunRegret(false, null, false)).toBe(true);
    expect(shouldRerunRegret(false, null, true)).toBe(true);
  });

  it("reuses the cache when the line matches", () => {
    expect(shouldRerunRegret(true, false, false)).toBe(false);
    expect(shouldRerunRegret(true, true, true)).toBe(false);
  });

  it("re-runs when the cached line differs from the requested one", () => {
    // cached a replay analysis, now asking for the live line (and vice versa)
    expect(shouldRerunRegret(true, false, true)).toBe(true);
    expect(shouldRerunRegret(true, true, false)).toBe(true);
  });
});
