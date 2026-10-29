import { describe, it, expect } from "vitest";
import { parseDeckText, formatDeck, unknownCards } from "./deckText";

const STARTER = [
  ...Array<string>(5).fill("STRIKE_IRONCLAD"),
  ...Array<string>(4).fill("DEFEND_IRONCLAD"),
  "BASH",
];
const STARTER_TEXT = "5x STRIKE_IRONCLAD\n4x DEFEND_IRONCLAD\nBASH";

describe("parseDeckText", () => {
  it("parses bare one-per-line ids", () => {
    expect(parseDeckText("BASH\nSTRIKE_IRONCLAD")).toEqual({
      cards: ["BASH", "STRIKE_IRONCLAD"],
      errors: [],
    });
  });

  it("expands the Nx shorthand", () => {
    expect(parseDeckText("3x FOO")).toEqual({
      cards: ["FOO", "FOO", "FOO"],
      errors: [],
    });
  });

  it("accepts a case-insensitive X separator", () => {
    expect(parseDeckText("2X FOO")).toEqual({
      cards: ["FOO", "FOO"],
      errors: [],
    });
  });

  it("skips blank and whitespace-only lines", () => {
    expect(parseDeckText("BASH\n\n   \n\tBASH\t")).toEqual({
      cards: ["BASH", "BASH"],
      errors: [],
    });
  });

  it("reports a zero count as an error naming the line", () => {
    const r = parseDeckText("0x FOO");
    expect(r.cards).toEqual([]);
    expect(r.errors).toHaveLength(1);
    expect(r.errors[0]).toContain("0x FOO");
  });

  it("reports a negative count as an error naming the line", () => {
    const r = parseDeckText("-1x FOO");
    expect(r.cards).toEqual([]);
    expect(r.errors).toHaveLength(1);
    expect(r.errors[0]).toContain("-1x FOO");
  });

  it("reports a missing count (bare 'x FOO') as an error", () => {
    const r = parseDeckText("x FOO");
    expect(r.cards).toEqual([]);
    expect(r.errors).toHaveLength(1);
    expect(r.errors[0]).toContain("x FOO");
  });

  it("never throws on arbitrary input", () => {
    expect(() => parseDeckText("$$$\n12\n  x  \n5x")).not.toThrow();
  });

  it("collects good cards even when one line errors", () => {
    const r = parseDeckText("BASH\n0x FOO\nSTRIKE_IRONCLAD");
    expect(r.cards).toEqual(["BASH", "STRIKE_IRONCLAD"]);
    expect(r.errors).toHaveLength(1);
  });
});

describe("formatDeck", () => {
  it("groups consecutive duplicates into Nx lines, bare for singletons", () => {
    expect(formatDeck(STARTER)).toBe(STARTER_TEXT);
  });

  it("empty deck -> empty string", () => {
    expect(formatDeck([])).toBe("");
  });

  it("only groups *consecutive* duplicates", () => {
    expect(formatDeck(["A", "B", "A"])).toBe("A\nB\nA");
  });
});

describe("parseDeckText / formatDeck round trip", () => {
  it("round-trips the starter deck", () => {
    expect(parseDeckText(formatDeck(STARTER)).cards).toEqual(STARTER);
    expect(formatDeck(parseDeckText(STARTER_TEXT).cards)).toBe(STARTER_TEXT);
  });
});

describe("unknownCards", () => {
  it("strips a trailing +N upgrade suffix before checking", () => {
    expect(unknownCards(["STRIKE_IRONCLAD+1"], ["STRIKE_IRONCLAD"])).toEqual([]);
  });

  it("returns ids whose base is not known", () => {
    expect(unknownCards(["FOO", "BASH"], ["BASH"])).toEqual(["FOO"]);
  });

  it("is order-preserving and deduped", () => {
    expect(unknownCards(["FOO", "BAR", "FOO"], ["BASH"])).toEqual(["FOO", "BAR"]);
  });

  it("empty when all known", () => {
    expect(unknownCards(["BASH", "BASH"], ["BASH"])).toEqual([]);
  });
});
