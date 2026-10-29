import { describe, it, expect } from "vitest";
import { sortRows, filterByQuadrant, catalogToGraph, boundLinkStrength, topEdgesByStrength, normToPixel, hueToColor, topPartners, buildEdgeLookup, pairKey, topNByFrequency, divergingColor } from "./data";
import type { CatalogEdge, Catalog, GraphLink, CatalogNode } from "./data";

const makeEdge = (
  pair: [string, string],
  overrides: Partial<CatalogEdge> = {},
): CatalogEdge => ({
  pair,
  pmi: 1.0,
  econ_S: 0.5,
  combat_S: 0.6,
  confidence: 0.5,
  sample_n: 100,
  quadrant: "confirmed",
  ...overrides,
});

describe("sortRows", () => {
  it("sorts by pmi asc puts smallest first", () => {
    const rows: CatalogEdge[] = [
      makeEdge(["A", "B"], { pmi: 2.0 }),
      makeEdge(["C", "D"], { pmi: 0.5 }),
      makeEdge(["E", "F"], { pmi: 1.5 }),
    ];
    const result = sortRows(rows, "pmi", "asc");
    expect(result.map((r) => r.pmi)).toEqual([0.5, 1.5, 2.0]);
  });

  it("sorts by pmi desc puts largest first", () => {
    const rows: CatalogEdge[] = [
      makeEdge(["A", "B"], { pmi: 2.0 }),
      makeEdge(["C", "D"], { pmi: 0.5 }),
      makeEdge(["E", "F"], { pmi: 1.5 }),
    ];
    const result = sortRows(rows, "pmi", "desc");
    expect(result.map((r) => r.pmi)).toEqual([2.0, 1.5, 0.5]);
  });

  it("sorts by combat_S desc with nulls always last", () => {
    const rows: CatalogEdge[] = [
      makeEdge(["A", "B"], { combat_S: 0.8 }),
      makeEdge(["C", "D"], { combat_S: null }),
      makeEdge(["E", "F"], { combat_S: 0.3 }),
      makeEdge(["G", "H"], { combat_S: null }),
    ];
    const result = sortRows(rows, "combat_S", "desc");
    expect(result.map((r) => r.combat_S)).toEqual([0.8, 0.3, null, null]);
  });

  it("sorts by combat_S asc with nulls always last", () => {
    const rows: CatalogEdge[] = [
      makeEdge(["A", "B"], { combat_S: 0.8 }),
      makeEdge(["C", "D"], { combat_S: null }),
      makeEdge(["E", "F"], { combat_S: 0.3 }),
    ];
    const result = sortRows(rows, "combat_S", "asc");
    expect(result.map((r) => r.combat_S)).toEqual([0.3, 0.8, null]);
  });

  it("does not mutate the original array", () => {
    const rows: CatalogEdge[] = [
      makeEdge(["A", "B"], { pmi: 2.0 }),
      makeEdge(["C", "D"], { pmi: 0.5 }),
    ];
    const original = [...rows];
    sortRows(rows, "pmi", "asc");
    expect(rows[0].pmi).toBe(original[0].pmi);
  });
});

describe("filterByQuadrant", () => {
  const rows: CatalogEdge[] = [
    makeEdge(["A", "B"], { quadrant: "confirmed" }),
    makeEdge(["C", "D"], { quadrant: "myth" }),
    makeEdge(["E", "F"], { quadrant: "undiscovered" }),
    makeEdge(["G", "H"], { quadrant: "noise" }),
  ];

  it("keeps only confirmed edges when only confirmed is enabled", () => {
    const result = filterByQuadrant(rows, new Set(["confirmed"]));
    expect(result).toHaveLength(1);
    expect(result[0].quadrant).toBe("confirmed");
  });

  it("returns empty array when enabled set is empty", () => {
    const result = filterByQuadrant(rows, new Set());
    expect(result).toHaveLength(0);
  });

  it("returns all edges when full set is provided", () => {
    const result = filterByQuadrant(
      rows,
      new Set(["confirmed", "myth", "undiscovered", "noise"]),
    );
    expect(result).toHaveLength(4);
  });

  it("filters multiple quadrants correctly", () => {
    const result = filterByQuadrant(rows, new Set(["confirmed", "myth"]));
    expect(result).toHaveLength(2);
    expect(result.map((r) => r.quadrant)).toEqual(["confirmed", "myth"]);
  });
});

const sampleCatalog: Catalog = {
  generated_at: "2026-06-21T00:00:00Z",
  character: "Ironclad",
  run_count: 1000,
  nodes: [
    { id: "BARRICADE", label: "Barricade", frequency: 0.18 },
    { id: "BODY_SLAM", label: "Body Slam", frequency: 0.31 },
    { id: "LIMIT_BREAK", label: "Limit Break", frequency: 0.15 },
    { id: "HEAVY_BLADE", label: "Heavy Blade", frequency: 0.24 },
    { id: "RECKLESS_CHARGE", label: "Reckless Charge", frequency: 0.11 },
    { id: "ANGER", label: "Anger", frequency: 0.55 },
  ],
  edges: [
    makeEdge(["BARRICADE", "BODY_SLAM"], { pmi: 2.1, combat_S: 0.85, quadrant: "confirmed" }),
    makeEdge(["LIMIT_BREAK", "HEAVY_BLADE"], { pmi: 2.3, combat_S: null, quadrant: "undiscovered" }),
    makeEdge(["RECKLESS_CHARGE", "ANGER"], { pmi: 0.1, combat_S: 0.24, quadrant: "noise" }),
  ],
};

describe("catalogToGraph", () => {
  it("only includes nodes that appear in visible edges", () => {
    const enabled = new Set<CatalogEdge["quadrant"]>(["confirmed"]);
    const { nodes } = catalogToGraph(sampleCatalog, enabled);
    expect(nodes.map((n) => n.id).sort()).toEqual(["BARRICADE", "BODY_SLAM"]);
  });

  it("excludes nodes from non-enabled quadrant edges", () => {
    const enabled = new Set<CatalogEdge["quadrant"]>(["confirmed"]);
    const { nodes } = catalogToGraph(sampleCatalog, enabled);
    const ids = nodes.map((n) => n.id);
    expect(ids).not.toContain("LIMIT_BREAK");
    expect(ids).not.toContain("HEAVY_BLADE");
  });

  it("uses combat_S as strength when present", () => {
    const enabled = new Set<CatalogEdge["quadrant"]>(["confirmed"]);
    const { links } = catalogToGraph(sampleCatalog, enabled);
    expect(links).toHaveLength(1);
    expect(links[0].strength).toBe(0.85);
  });

  it("falls back to pmi when combat_S is null", () => {
    const enabled = new Set<CatalogEdge["quadrant"]>(["undiscovered"]);
    const { links } = catalogToGraph(sampleCatalog, enabled);
    expect(links).toHaveLength(1);
    expect(links[0].strength).toBe(2.3);
  });

  it("returns no nodes and no links when enabled set is empty", () => {
    const { nodes, links } = catalogToGraph(sampleCatalog, new Set());
    expect(nodes).toHaveLength(0);
    expect(links).toHaveLength(0);
  });

  it("link source and target match edge pair", () => {
    const enabled = new Set<CatalogEdge["quadrant"]>(["confirmed"]);
    const { links } = catalogToGraph(sampleCatalog, enabled);
    expect(links[0].source).toBe("BARRICADE");
    expect(links[0].target).toBe("BODY_SLAM");
  });

  it("populates label from catalog nodes", () => {
    const enabled = new Set<CatalogEdge["quadrant"]>(["confirmed"]);
    const { nodes } = catalogToGraph(sampleCatalog, enabled);
    const barricade = nodes.find((n) => n.id === "BARRICADE");
    expect(barricade?.label).toBe("Barricade");
  });

  it("exposes totalLinks equal to the number of visible edges", () => {
    const enabled = new Set<CatalogEdge["quadrant"]>(["confirmed", "undiscovered", "noise"]);
    const { totalLinks, links } = catalogToGraph(sampleCatalog, enabled);
    // sampleCatalog has confirmed(1) + undiscovered(1) + noise(1) = 3 visible edges
    expect(totalLinks).toBe(3);
    // All 3 are within the 350 cap so links equals total
    expect(links).toHaveLength(3);
  });
});

describe("boundLinkStrength", () => {
  it("clamps a positive value to [0.02, 1]", () => {
    expect(boundLinkStrength(0.5)).toBe(0.5);
    expect(boundLinkStrength(1.5)).toBe(1);
    expect(boundLinkStrength(0)).toBe(0.02);
  });

  it("treats negative values as their absolute value then clamps", () => {
    expect(boundLinkStrength(-0.5)).toBe(0.5);
    expect(boundLinkStrength(-2)).toBe(1);
  });

  it("returns a finite positive value for NaN input", () => {
    const result = boundLinkStrength(NaN);
    expect(Number.isFinite(result)).toBe(true);
    expect(result).toBeGreaterThan(0);
  });

  it("returns a finite positive value for Infinity input", () => {
    const result = boundLinkStrength(Infinity);
    expect(Number.isFinite(result)).toBe(true);
    expect(result).toBeGreaterThan(0);
  });

  it("always returns value in [0.02, 1]", () => {
    const inputs = [-100, -1, -0.001, 0, 0.001, 0.5, 1, 1.5, 100, NaN, Infinity, -Infinity];
    for (const v of inputs) {
      const r = boundLinkStrength(v);
      expect(r).toBeGreaterThanOrEqual(0.02);
      expect(r).toBeLessThanOrEqual(1);
      expect(Number.isFinite(r)).toBe(true);
    }
  });
});

describe("topEdgesByStrength", () => {
  const makeLink = (strength: number): GraphLink => ({
    source: "A",
    target: "B",
    strength,
    quadrant: "noise",
  });

  it("returns at most N edges", () => {
    const edges = [makeLink(3), makeLink(1), makeLink(2), makeLink(4), makeLink(5)];
    expect(topEdgesByStrength(edges, 3)).toHaveLength(3);
  });

  it("sorts by |strength| descending", () => {
    const edges = [makeLink(1), makeLink(-3), makeLink(2)];
    const result = topEdgesByStrength(edges, 3);
    expect(result.map((e) => e.strength)).toEqual([-3, 2, 1]);
  });

  it("returns all edges when N >= length", () => {
    const edges = [makeLink(1), makeLink(2)];
    expect(topEdgesByStrength(edges, 10)).toHaveLength(2);
  });

  it("handles negative strengths correctly (picks largest |strength|)", () => {
    const edges = [makeLink(-5), makeLink(3), makeLink(-1)];
    const result = topEdgesByStrength(edges, 2);
    expect(result.map((e) => e.strength)).toEqual([-5, 3]);
  });

  it("does not mutate the original array", () => {
    const edges = [makeLink(3), makeLink(1)];
    const original = edges.map((e) => e.strength);
    topEdgesByStrength(edges, 1);
    expect(edges.map((e) => e.strength)).toEqual(original);
  });
});

describe("null confidence and sample_n contract", () => {
  // Edges produced without --combat have confidence: null and sample_n: null.
  // Verify they survive sortRows (nulls last) and catalogToGraph without error.
  const nullEdge = makeEdge(["BASH", "STRIKE_R"], {
    confidence: null,
    sample_n: null,
    combat_S: null,
    econ_S: 0.4,
    pmi: 1.2,
    quadrant: "undiscovered",
  });

  it("edge with null confidence and sample_n is a valid CatalogEdge", () => {
    expect(nullEdge.confidence).toBeNull();
    expect(nullEdge.sample_n).toBeNull();
  });

  it("sortRows puts null confidence last regardless of direction", () => {
    const rows: CatalogEdge[] = [
      makeEdge(["A", "B"], { confidence: 0.9 }),
      nullEdge,
      makeEdge(["C", "D"], { confidence: 0.2 }),
    ];
    const asc = sortRows(rows, "confidence", "asc");
    expect(asc[asc.length - 1].confidence).toBeNull();
    const desc = sortRows(rows, "confidence", "desc");
    expect(desc[desc.length - 1].confidence).toBeNull();
  });

  it("sortRows puts null sample_n last", () => {
    const rows: CatalogEdge[] = [
      makeEdge(["A", "B"], { sample_n: 50 }),
      nullEdge,
    ];
    const result = sortRows(rows, "sample_n", "asc");
    expect(result[result.length - 1].sample_n).toBeNull();
  });

  it("catalogToGraph accepts an edge with null confidence/sample_n without error", () => {
    const catalog: Catalog = {
      generated_at: "2026-06-21T00:00:00Z",
      character: "Ironclad",
      run_count: 500,
      nodes: [
        { id: "BASH", label: "Bash", frequency: 0.8 },
        { id: "STRIKE_R", label: "Strike", frequency: 0.95 },
      ],
      edges: [nullEdge],
    };
    const { nodes, links } = catalogToGraph(
      catalog,
      new Set<CatalogEdge["quadrant"]>(["undiscovered"]),
    );
    expect(nodes).toHaveLength(2);
    expect(links).toHaveLength(1);
    // strength falls back to pmi since combat_S is null
    expect(links[0].strength).toBe(1.2);
  });
});

const embeddedCatalog: Catalog = {
  generated_at: "2026-06-21T00:00:00Z",
  character: "Ironclad",
  run_count: 1000,
  nodes: [
    { id: "BASH", label: "Bash", frequency: 0.9, x: 0.1, y: 0.1, hue: 45 },
    { id: "STRIKE_R", label: "Strike", frequency: 0.95, x: 0.9, y: 0.9, hue: 225 },
    { id: "DEFEND_R", label: "Defend", frequency: 0.93, x: 0.5, y: 0.5, hue: 135 },
  ],
  edges: [
    makeEdge(["BASH", "STRIKE_R"], { pmi: 2.0 }),
    makeEdge(["BASH", "DEFEND_R"], { pmi: 1.5 }),
    makeEdge(["STRIKE_R", "DEFEND_R"], { pmi: 0.3 }),
  ],
};

describe("normToPixel", () => {
  it("maps 0 → minPx", () => {
    expect(normToPixel(0, 40, 760)).toBe(40);
  });
  it("maps 1 → maxPx", () => {
    expect(normToPixel(1, 40, 760)).toBe(760);
  });
  it("maps 0.5 → midpoint", () => {
    expect(normToPixel(0.5, 40, 760)).toBe(400);
  });
});

describe("hueToColor", () => {
  it("returns hsl string with given hue", () => {
    expect(hueToColor(120)).toBe("hsl(120.0,70%,55%)");
  });
  it("accepts custom saturation and lightness", () => {
    expect(hueToColor(0, 50, 40)).toBe("hsl(0.0,50%,40%)");
  });
});

describe("topPartners", () => {
  it("returns top K partners by pmi desc", () => {
    const partners = topPartners(embeddedCatalog, "BASH", 5);
    expect(partners.map((p) => p.partnerId)).toEqual(["STRIKE_R", "DEFEND_R"]);
  });

  it("excludes the card itself", () => {
    const partners = topPartners(embeddedCatalog, "BASH", 5);
    expect(partners.some((p) => p.partnerId === "BASH")).toBe(false);
  });

  it("respects k limit", () => {
    const partners = topPartners(embeddedCatalog, "BASH", 1);
    expect(partners).toHaveLength(1);
    expect(partners[0].pmi).toBe(2.0);
  });

  it("returns empty array for card with no edges", () => {
    const result = topPartners(embeddedCatalog, "NONEXISTENT", 5);
    expect(result).toHaveLength(0);
  });

  it("includes partnerLabel from catalog nodes", () => {
    const partners = topPartners(embeddedCatalog, "BASH", 5);
    const strike = partners.find((p) => p.partnerId === "STRIKE_R");
    expect(strike?.partnerLabel).toBe("Strike");
  });
});

// ── Heatmap helpers ───────────────────────────────────────────────────────────

describe("pairKey", () => {
  it("is symmetric (same key regardless of order)", () => {
    expect(pairKey("BASH", "STRIKE_R")).toBe(pairKey("STRIKE_R", "BASH"));
  });
  it("returns a stable canonical key", () => {
    expect(pairKey("A", "B")).toBe("A|B");
    expect(pairKey("B", "A")).toBe("A|B");
    expect(pairKey("Z", "A")).toBe("A|Z");
  });
});

describe("buildEdgeLookup", () => {
  const edges: CatalogEdge[] = [
    makeEdge(["BARRICADE", "BODY_SLAM"], { pmi: 2.1, quadrant: "confirmed" }),
    makeEdge(["ANGER", "LIMIT_BREAK"],   { pmi: 0.4, quadrant: "noise" }),
  ];

  it("looks up an edge by canonical pair key", () => {
    const m = buildEdgeLookup(edges);
    expect(m.get(pairKey("BARRICADE", "BODY_SLAM"))?.pmi).toBeCloseTo(2.1);
    expect(m.get(pairKey("BODY_SLAM", "BARRICADE"))?.pmi).toBeCloseTo(2.1);
  });

  it("returns undefined for a missing pair", () => {
    const m = buildEdgeLookup(edges);
    expect(m.get(pairKey("BARRICADE", "ANGER"))).toBeUndefined();
  });

  it("returns the right edge for each pair", () => {
    const m = buildEdgeLookup(edges);
    expect(m.get(pairKey("ANGER", "LIMIT_BREAK"))?.quadrant).toBe("noise");
  });
});

describe("topNByFrequency", () => {
  const nodes: CatalogNode[] = [
    { id: "A", label: "A", frequency: 0.9 },
    { id: "B", label: "B", frequency: 0.1 },
    { id: "C", label: "C", frequency: 0.5 },
    { id: "D", label: "D", frequency: 0.7 },
  ];
  const nodeById = new Map(nodes.map((n) => [n.id, n]));
  const order = ["A", "B", "C", "D"]; // original card_order

  it("returns top-N by frequency while preserving card_order order", () => {
    const result = topNByFrequency(order, nodeById, 2);
    // top-2 by freq = A (0.9) and D (0.7); must preserve card_order order: [A, D]
    expect(result).toEqual(["A", "D"]);
  });

  it("returns all when N >= length", () => {
    expect(topNByFrequency(order, nodeById, 10)).toEqual(order);
    expect(topNByFrequency(order, nodeById, 4)).toEqual(order);
  });

  it("returns empty when cardOrder is empty", () => {
    expect(topNByFrequency([], nodeById, 5)).toEqual([]);
  });
});

describe("divergingColor", () => {
  it("returns a neutral color for null", () => {
    const c = divergingColor(null, 1);
    expect(c).toBe("#2a2a4a");
  });

  it("positive value → red-orange family (r=255)", () => {
    const c = divergingColor(0.8, 1);
    expect(c).toMatch(/^rgb\(255,/);
  });

  it("negative value → blue family (b=255)", () => {
    const c = divergingColor(-0.8, 1);
    expect(c).toMatch(/,255\)$/);
  });

  it("zero → white (all channels near 255)", () => {
    const c = divergingColor(0, 1);
    const m = c.match(/rgb\((\d+),(\d+),(\d+)\)/);
    expect(m).not.toBeNull();
    if (m) {
      expect(Number(m[1])).toBe(255);
      expect(Number(m[2])).toBe(255);
      expect(Number(m[3])).toBe(255);
    }
  });

  it("clips values outside [-domain, domain] to boundary colors", () => {
    const cHigh = divergingColor(100, 1);
    const cAt1  = divergingColor(1, 1);
    expect(cHigh).toBe(cAt1);
    const cLow  = divergingColor(-100, 1);
    const cAtM1 = divergingColor(-1, 1);
    expect(cLow).toBe(cAtM1);
  });
});
