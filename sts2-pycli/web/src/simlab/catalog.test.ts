import { describe, expect, it } from "vitest";
import { encounterGroups, nameOf, optionsFor, searchOptions, titleCase } from "./catalog";
import { CATALOG } from "./testMocks";

describe("catalog lookups", () => {
  it("names ids and falls back to title case", () => {
    expect(nameOf(CATALOG, "card", "DEMON_FORM_CARD")).toBe("Demon Form");
    expect(nameOf(CATALOG, "monster", "FuzzyWurmCrawler")).toBe("Fuzzy Wurm Crawler");
    expect(nameOf(CATALOG, "relic", "NOT_THERE")).toBe("Not There");
    expect(titleCase("ShrinkerBeetle")).toBe("Shrinker Beetle");
  });
  it("puts the active character's cards first", () => {
    const cat = { ...CATALOG, cards: [...CATALOG.cards, { id: "NEUTRALIZE", name: "Neutralize", type: "Attack", rarity: "Basic", cost: 0, character: "Silent" }] };
    const ids = optionsFor(cat, "card", "Silent").map((o) => o.id);
    expect(ids[0]).toBe("NEUTRALIZE");
  });
  it("groups encounters by act and tier", () => {
    const groups = encounterGroups(CATALOG);
    expect(groups.map((g) => g.label)).toEqual(["Act 1 · Overgrowth · Weak", "Act 1 · Overgrowth · Boss"]);
  });
});

describe("searchOptions", () => {
  const opts = optionsFor(CATALOG, "card");
  it("ranks prefix matches over substring over subsequence", () => {
    expect(searchOptions(opts, "str")[0].id).toBe("STRIKE_IRONCLAD");
    expect(searchOptions(opts, "form")[0].id).toBe("DEMON_FORM_CARD");
    expect(searchOptions(opts, "dfnd")[0].id).toBe("DEFEND_IRONCLAD");
  });
  it("treats underscores as spaces and is case-insensitive", () => {
    expect(searchOptions(opts, "Demon Form")[0].id).toBe("DEMON_FORM_CARD");
    expect(searchOptions(opts, "zzz")).toEqual([]);
    expect(searchOptions(opts, "", 2)).toHaveLength(2);
  });
});
