import { afterEach, describe, expect, it, vi } from "vitest";
import {
  archetypeChipsFor,
  curveMarginal,
  curveV,
  deckMembership,
  isSuperAdditive,
  loadArchetypes,
  type ArchetypeDoc,
} from "./archetype";

const CURVE = {
  targetK: 3,
  points: [
    { k: 0, v: -20, additive: -20 },
    { k: 1, v: -17, additive: -17 },
    { k: 2, v: -13, additive: -14 }, // v(-13) > additive(-14) → super-additive
    { k: 3, v: -6, additive: -11 },
  ],
};

const DOC: ArchetypeDoc = {
  character: "ironclad",
  archetypes: [
    {
      id: "strength",
      label: "Strength Engine",
      tag: "confirmed",
      cards: [
        { token: "INFLAME", weight: 1.0 },
        { token: "LIMIT_BREAK", weight: 0.9 },
        { token: "SPOT_WEAKNESS", weight: 0.6 },
      ],
      curve: CURVE,
    },
    {
      id: "folk",
      label: "Folklore Combo",
      tag: "folklore",
      cards: [
        { token: "INFLAME", weight: 1.0 },
        { token: "HEAVY_BLADE", weight: 0.8 },
      ],
      curve: CURVE,
    },
  ],
};

describe("curveV", () => {
  it("returns the value at an exact k", () => {
    expect(curveV(CURVE, 2)).toBe(-13);
  });
  it("clamps below and above the measured range", () => {
    expect(curveV(CURVE, -5)).toBe(-20);
    expect(curveV(CURVE, 99)).toBe(-6);
  });
});

describe("curveMarginal", () => {
  it("is the k → k+1 gain", () => {
    expect(curveMarginal(CURVE, 2)).toBe(-6 - -13); // 7
  });
  it("is zero past the measured range", () => {
    expect(curveMarginal(CURVE, 3)).toBe(0);
  });
});

describe("isSuperAdditive", () => {
  it("true where v beats the additive prediction", () => {
    expect(isSuperAdditive(CURVE, 2)).toBe(true);
  });
  it("false where v equals additive", () => {
    expect(isSuperAdditive(CURVE, 1)).toBe(false);
  });
});

describe("deckMembership", () => {
  it("reports held pieces and next-piece super-additivity", () => {
    const m = deckMembership(DOC, ["INFLAME", "LIMIT_BREAK", "STRIKE_IRONCLAD"]);
    // folklore is still a membership (deckMembership lists all), but confirmed sorts first.
    const strength = m.find((x) => x.archetype.id === "strength")!;
    expect(strength.k).toBe(2);
    expect(strength.held.sort()).toEqual(["INFLAME", "LIMIT_BREAK"]);
    // piece 3 (k+1) lands at k=3 which is NOT flagged super-additive (v -6 > additive -11 → true)
    expect(strength.superAdditiveNext).toBe(true);
    expect(strength.marginalNext).toBe(-6 - -13); // curve gain 2 → 3
  });

  it("omits archetypes the deck holds no piece of", () => {
    const m = deckMembership(DOC, ["STRIKE_IRONCLAD", "DEFEND_IRONCLAD"]);
    expect(m).toEqual([]);
  });

  it("counts copies toward k", () => {
    const m = deckMembership(DOC, ["INFLAME", "INFLAME"]);
    expect(m.find((x) => x.archetype.id === "strength")!.k).toBe(2);
  });
});

describe("archetypeChipsFor", () => {
  it("chips a weighty member when the deck already holds a piece", () => {
    const chips = archetypeChipsFor(DOC, "LIMIT_BREAK", ["INFLAME"], 0.5);
    const s = chips.find((c) => c.id === "strength")!;
    expect(s.piece).toBe(2); // held 1 (INFLAME) + this = 2nd piece
    expect(s.label).toBe("Strength Engine");
    expect(s.superAdditive).toBe(true); // piece 2 → k=2 is super-additive
  });

  it("excludes folklore archetypes", () => {
    const chips = archetypeChipsFor(DOC, "HEAVY_BLADE", ["INFLAME"], 0.5);
    expect(chips.find((c) => c.id === "folk")).toBeUndefined();
  });

  it("requires the offered card's weight to clear the threshold", () => {
    // SPOT_WEAKNESS weight 0.6 < 0.7 threshold → no chip.
    expect(archetypeChipsFor(DOC, "SPOT_WEAKNESS", ["INFLAME"], 0.7)).toEqual([]);
  });

  it("requires the deck to already hold >= 1 piece", () => {
    // offered INFLAME is a member, but the deck holds no OTHER member → no chip.
    expect(archetypeChipsFor(DOC, "INFLAME", ["STRIKE_IRONCLAD"], 0.5)).toEqual([]);
  });

  it("strips namespace/upgrade from the offered id", () => {
    const chips = archetypeChipsFor(DOC, "CARD.LIMIT_BREAK+1", ["INFLAME"], 0.5);
    expect(chips.find((c) => c.id === "strength")).toBeDefined();
  });
});

describe("loadArchetypes", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("fetches and returns the doc", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({ ok: true, json: async () => DOC }) as Response),
    );
    const doc = await loadArchetypes("testchar-a");
    expect(doc.archetypes).toHaveLength(2);
  });

  it("returns an empty doc when the file is missing", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({ ok: false, status: 404 }) as Response),
    );
    const doc = await loadArchetypes("testchar-b");
    expect(doc.archetypes).toEqual([]);
    expect(doc.character).toBe("testchar-b");
  });

  it("returns an empty doc when the file is the raw discovery output", async () => {
    // `emit-archetypes` emits `{character, candidates}`; only
    // `emit-archetype-catalog` emits the `{character, archetypes}` UI doc.
    // Serving the raw one must degrade like a 404, not throw downstream.
    vi.stubGlobal(
      "fetch",
      vi.fn(
        async () =>
          ({
            ok: true,
            json: async () => ({ character: "testchar-c", candidates: [{ cards: ["BASH"] }] }),
          }) as Response,
      ),
    );
    const doc = await loadArchetypes("testchar-c");
    expect(doc.archetypes).toEqual([]);
    expect(() => deckMembership(doc, ["BASH"])).not.toThrow();
  });
});
