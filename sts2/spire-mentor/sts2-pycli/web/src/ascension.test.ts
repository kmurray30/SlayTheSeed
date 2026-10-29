import { describe, it, expect } from "vitest";
import { ASCENSION_LEVELS, ASCENSION_MAX, ascensionLevel, ascensionModifiers } from "./ascension";

describe("ascension ladder", () => {
  it("carries exactly the game's 0..10 levels in order", () => {
    expect(ASCENSION_LEVELS).toHaveLength(ASCENSION_MAX + 1);
    ASCENSION_LEVELS.forEach((l, i) => expect(l.level).toBe(i));
    expect(ascensionLevel(5).title).toBe("Ascender's Bane");
  });
  it("clamps out-of-range levels", () => {
    expect(ascensionLevel(-3).level).toBe(0);
    expect(ascensionLevel(20).level).toBe(ASCENSION_MAX);
  });
  it("stacks every lower level, highest first, and is empty at A0", () => {
    expect(ascensionModifiers(0)).toEqual([]);
    expect(ascensionModifiers(3).map((l) => l.level)).toEqual([3, 2, 1]);
  });
});
