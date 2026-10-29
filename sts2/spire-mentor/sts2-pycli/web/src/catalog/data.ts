export interface CatalogNode {
  id: string;
  label: string;
  frequency: number;
  upgraded_frequency?: number;
  archetype_hint?: string;
  signal?: boolean; // true if this card has ≥1 synergy edge (part of card_order)
  x?: number;   // UMAP x in [0,1], undefined if no embedding
  y?: number;   // UMAP y in [0,1]
  hue?: number; // emergent hue 0-360 from embedding position
}

export interface CatalogEdge {
  pair: [string, string];
  pmi: number;
  econ_S: number | null;
  combat_S: number | null;
  confidence: number | null;
  sample_n: number | null;
  quadrant: "confirmed" | "myth" | "undiscovered" | "noise";
}

export interface Catalog {
  generated_at: string;
  character: string;
  run_count: number;
  nodes: CatalogNode[];
  edges: CatalogEdge[];
  /** Hierarchically-clustered ordering of signal cards (emergent, no hand labels). */
  card_order?: string[];
}

export type SortKey = keyof Omit<CatalogEdge, "pair">;
export type SortDir = "asc" | "desc";
export type QuadrantSet = Set<CatalogEdge["quadrant"]>;

/** Sort edge rows by key, asc/desc, nulls last always. */
export function sortRows(
  rows: CatalogEdge[],
  key: SortKey,
  dir: SortDir,
): CatalogEdge[] {
  return [...rows].sort((a, b) => {
    const av = a[key];
    const bv = b[key];

    // Nulls always go last regardless of direction
    if (av === null && bv === null) return 0;
    if (av === null) return 1;
    if (bv === null) return -1;

    // Both non-null: compare as numbers or strings
    let cmp: number;
    if (typeof av === "number" && typeof bv === "number") {
      cmp = av - bv;
    } else {
      cmp = String(av) < String(bv) ? -1 : String(av) > String(bv) ? 1 : 0;
    }

    return dir === "asc" ? cmp : -cmp;
  });
}

/** Filter to edges whose quadrant is in the enabled set. */
export function filterByQuadrant(
  rows: CatalogEdge[],
  enabled: QuadrantSet,
): CatalogEdge[] {
  return rows.filter((e) => enabled.has(e.quadrant));
}

/** D3 graph data: nodes + links derived from catalog + current quadrant filter. */
export interface GraphNode {
  id: string;
  label: string;
  frequency: number;
}
export interface GraphLink {
  source: string;
  target: string;
  strength: number; // combat_S if present, else pmi
  quadrant: CatalogEdge["quadrant"];
}
export interface GraphData {
  nodes: GraphNode[];
  links: GraphLink[];
  /** Total number of links before the GRAPH_EDGE_CAP was applied. */
  totalLinks: number;
}

/**
 * Clamp a raw edge strength value (combat_S or pmi) to a finite positive range
 * suitable for D3 force simulation. Handles NaN, negative, and out-of-range values.
 * Returns a value in [0.02, 1].
 */
export function boundLinkStrength(raw: number): number {
  const v = Math.abs(raw);
  if (!Number.isFinite(v)) return 0.02;
  return Math.max(0.02, Math.min(1, v));
}

/**
 * Return at most `n` edges, sorted descending by |strength|.
 * Used to cap the graph render while keeping the table complete.
 */
export function topEdgesByStrength(edges: GraphLink[], n: number): GraphLink[] {
  return [...edges]
    .sort((a, b) => Math.abs(b.strength) - Math.abs(a.strength))
    .slice(0, n);
}

const GRAPH_EDGE_CAP = 350;

/** Scale a normalized [0,1] coord to SVG pixel space with padding. */
export function normToPixel(
  v: number,
  minPx: number,
  maxPx: number,
): number {
  return minPx + v * (maxPx - minPx);
}

/** HSL color string from a hue and optional saturation/lightness. */
export function hueToColor(hue: number, s = 70, l = 55): string {
  return `hsl(${hue.toFixed(1)},${s}%,${l}%)`;
}

/**
 * Return the top-K synergy partners of `cardId` from catalog edges, sorted by |pmi| desc.
 * Returns at most K entries with {partnerId, partnerLabel, pmi}.
 */
export function topPartners(
  catalog: Catalog,
  cardId: string,
  k = 5,
): Array<{ partnerId: string; partnerLabel: string; pmi: number }> {
  const nodeById = new Map(catalog.nodes.map((n) => [n.id, n]));
  const relevant = catalog.edges
    .filter((e) => e.pair[0] === cardId || e.pair[1] === cardId)
    .map((e) => {
      const partnerId = e.pair[0] === cardId ? e.pair[1] : e.pair[0];
      const partnerLabel = nodeById.get(partnerId)?.label ?? partnerId;
      return { partnerId, partnerLabel, pmi: e.pmi };
    })
    .sort((a, b) => Math.abs(b.pmi) - Math.abs(a.pmi))
    .slice(0, k);
  return relevant;
}

// ── Heatmap helpers ───────────────────────────────────────────────────────────

export type HeatmapMetric = "pmi" | "econ_S" | "combat_S";

/**
 * Build a Map from canonical pair key → edge for fast O(1) heatmap lookups.
 * Key is always `min(a,b) + "|" + max(a,b)`.
 */
export function buildEdgeLookup(
  edges: CatalogEdge[],
): Map<string, CatalogEdge> {
  const m = new Map<string, CatalogEdge>();
  for (const e of edges) {
    m.set(pairKey(e.pair[0], e.pair[1]), e);
  }
  return m;
}

/** Canonical symmetric pair key. */
export function pairKey(a: string, b: string): string {
  return a < b ? `${a}|${b}` : `${b}|${a}`;
}

/**
 * Get the metric value from an edge (or null when absent/not computed).
 * Returns `undefined` when the pair has no edge at all (below floor — distinct
 * from "zero synergy").
 */
export function edgeMetricValue(
  edge: CatalogEdge,
  metric: HeatmapMetric,
): number | null {
  return edge[metric] as number | null;
}

/**
 * Select the top-N signal cards by frequency from card_order.
 * Preserves the card_order order (block structure) — does NOT re-sort by freq.
 * Falls back to all signal cards when N >= card_order.length.
 */
export function topNByFrequency(
  cardOrder: string[],
  nodeById: Map<string, CatalogNode>,
  n: number,
): string[] {
  if (n >= cardOrder.length) return cardOrder;
  // Sort by frequency desc, take top-N, then restore card_order order
  const byFreq = [...cardOrder].sort(
    (a, b) => (nodeById.get(b)?.frequency ?? 0) - (nodeById.get(a)?.frequency ?? 0),
  );
  const topSet = new Set(byFreq.slice(0, n));
  return cardOrder.filter((id) => topSet.has(id));
}

/**
 * Map a diverging metric value to a CSS color string.
 * Blue (negative) → white (0) → orange-red (positive).
 * `domain` is the symmetric magnitude (values clipped to [-domain, +domain]).
 */
export function divergingColor(value: number | null, domain: number): string {
  if (value === null || !Number.isFinite(value)) return "#2a2a4a"; // neutral/missing
  const d = domain <= 0 ? 1 : domain;
  const t = Math.max(-1, Math.min(1, value / d)); // [-1, 1]
  if (t < 0) {
    // Negative: white → blue
    const intensity = -t; // [0, 1]
    const r = Math.round(255 * (1 - intensity * 0.7));
    const g = Math.round(255 * (1 - intensity * 0.5));
    const b = 255;
    return `rgb(${r},${g},${b})`;
  } else {
    // Positive: white → orange/red
    const intensity = t; // [0, 1]
    const r = 255;
    const g = Math.round(255 * (1 - intensity * 0.65));
    const b = Math.round(255 * (1 - intensity * 0.95));
    return `rgb(${r},${g},${b})`;
  }
}

export function catalogToGraph(
  catalog: Catalog,
  enabled: QuadrantSet,
): GraphData & { totalLinks: number } {
  const visibleEdges = filterByQuadrant(catalog.edges, enabled);

  // Build link list with bounded strengths (raw value preserved for stroke-width)
  const allLinks: GraphLink[] = visibleEdges.map((e) => ({
    source: e.pair[0],
    target: e.pair[1],
    strength: e.combat_S ?? e.pmi,
    quadrant: e.quadrant,
  }));

  const totalLinks = allLinks.length;
  const cappedLinks = topEdgesByStrength(allLinks, GRAPH_EDGE_CAP);

  // Collect node IDs that appear in the capped links
  const usedIds = new Set<string>();
  for (const link of cappedLinks) {
    usedIds.add(link.source);
    usedIds.add(link.target);
  }

  // Build node lookup from catalog
  const nodeById = new Map<string, CatalogNode>();
  for (const n of catalog.nodes) {
    nodeById.set(n.id, n);
  }

  const nodes: GraphNode[] = [];
  for (const id of usedIds) {
    const n = nodeById.get(id);
    nodes.push({
      id,
      label: n?.label ?? id,
      frequency: n?.frequency ?? 0,
    });
  }

  return { nodes, links: cappedLinks, totalLinks };
}
