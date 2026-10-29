// Pure data builders for the SimLab chart layer. Kept free of React/Plotly so
// they can be unit-tested in isolation and reused across the chart components.

export interface SeriesInfo {
  /** The original bucket key this series came from. */
  key: string;
  /** Short, human-readable label (only the key parts that vary). */
  label: string;
  /** Stable color assigned by series index. */
  color: string;
}

// A categorical palette (Plotly/D3-ish). Indexed by series position so colors
// stay stable for a given set of buckets regardless of value content.
const PALETTE = [
  "#1f77b4",
  "#ff7f0e",
  "#2ca02c",
  "#d62728",
  "#9467bd",
  "#8c564b",
  "#e377c2",
  "#7f7f7f",
  "#bcbd22",
  "#17becf",
];

/** Color for a series at the given index (wraps around the palette). */
export function seriesColor(index: number): string {
  return PALETTE[index % PALETTE.length];
}

/**
 * Build short labels for a set of 4-part bucket keys (`enc/asc/deck/strategy`).
 *
 * Only the key parts that *vary* across the given keys are included in each
 * label (joined by " · "), so common context isn't repeated on every series.
 * A single key always labels by its strategy. Keys that aren't 4-part fall
 * back to the raw key string. Colors are assigned stably by index.
 */
export function seriesLabels(keys: string[]): SeriesInfo[] {
  if (keys.length === 0) return [];

  const split = keys.map((k) => k.split("/"));
  const allFourPart = split.every((p) => p.length === 4);

  if (!allFourPart) {
    // Mixed / legacy keys: just use the raw key as the label.
    return keys.map((key, i) => ({ key, label: key, color: seriesColor(i) }));
  }

  // Single bucket: label by strategy (index 3).
  if (keys.length === 1) {
    return [{ key: keys[0], label: split[0][3], color: seriesColor(0) }];
  }

  // Determine which of the 4 positions vary across all keys.
  const varying = [0, 1, 2, 3].filter((pos) => {
    const first = split[0][pos];
    return split.some((p) => p[pos] !== first);
  });
  // If nothing varies (identical keys), fall back to strategy so labels aren't
  // empty.
  const positions = varying.length > 0 ? varying : [3];

  return keys.map((key, i) => ({
    key,
    label: positions.map((pos) => split[i][pos]).join(" · "),
    color: seriesColor(i),
  }));
}

/**
 * Step-CDF points from raw values: `x` is the sorted unique values ascending,
 * `y[i]` is the cumulative fraction of values `<= x[i]` (so the last y is 1).
 */
export function cdfPoints(values: number[]): { x: number[]; y: number[] } {
  if (values.length === 0) return { x: [], y: [] };
  const sorted = [...values].sort((a, b) => a - b);
  const n = sorted.length;
  const x: number[] = [];
  const y: number[] = [];
  for (let i = 0; i < n; i++) {
    const v = sorted[i];
    // Advance to the last occurrence of this value, then record its cumulative
    // fraction (count of values <= v over n).
    if (i + 1 < n && sorted[i + 1] === v) continue;
    x.push(v);
    y.push((i + 1) / n);
  }
  return { x, y };
}

/**
 * Render a probability as "1 in N": `oneIn(0.00025)` -> "1 in 4,000".
 * `p <= 0` or `p > 1` -> "—"; `p === 1` -> "always". N is rounded to the
 * nearest integer with locale thousands separators.
 *
 * Used for `worst_p` (the empirical probability of the worst-damage outcome),
 * which in practice is ≪1, so the "1 in N" framing is always meaningful; no
 * special casing is needed for the (unreached) p>0.5 region beyond rounding.
 */
export function oneIn(p: number): string {
  if (!(p > 0) || p > 1) return "—";
  if (p === 1) return "always";
  const n = Math.round(1 / p);
  return `1 in ${n.toLocaleString("en-US")}`;
}
