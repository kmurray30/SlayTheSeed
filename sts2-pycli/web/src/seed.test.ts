import { describe, it, expect } from "vitest";
import { SEED_ALPHABET, SEED_LENGTH, canonicalizeSeed, generateSeed } from "./seed";

describe("generateSeed", () => {
  it("is 10 characters from the game's alphabet (no I, no O)", () => {
    expect(SEED_ALPHABET).not.toContain("I");
    expect(SEED_ALPHABET).not.toContain("O");
    for (let i = 0; i < 50; i++) {
      const s = generateSeed();
      expect(s).toHaveLength(SEED_LENGTH);
      for (const ch of s) expect(SEED_ALPHABET).toContain(ch);
    }
  });

  it("maps the rng across the whole alphabet, including its last letter", () => {
    expect(generateSeed(() => 0)).toBe("0000000000");
    expect(generateSeed(() => 0.9999999)).toBe("ZZZZZZZZZZ");
  });

  it("differs between calls", () => {
    expect(generateSeed()).not.toBe(generateSeed());
  });
});

describe("canonicalizeSeed", () => {
  it("trims, uppercases and folds O/I to 0/1 like SeedHelper.CanonicalizeSeed", () => {
    expect(canonicalizeSeed("  foo1oi ")).toBe("F00101");
  });
  it("leaves a game-generated seed unchanged", () => {
    expect(canonicalizeSeed("Y3X2YZSBQJ")).toBe("Y3X2YZSBQJ");
  });
});
