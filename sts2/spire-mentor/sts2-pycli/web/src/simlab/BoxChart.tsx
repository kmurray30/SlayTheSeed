import { useEffect, useRef } from "react";
import Plotly from "plotly.js-dist-min";

export interface BoxSeries {
  /** Strategy name — drives trace name + color (one trace per strategy). */
  strategy: string;
  /** Deck name — the x-category boxes are grouped on. */
  deck: string;
  color: string;
  q1: number;
  median: number;
  q3: number;
  /** Lower whisker end (p1). */
  lo: number;
  /** Upper whisker end (p99). */
  hi: number;
  /** Unique values beyond the whiskers. */
  outliers: number[];
}

/**
 * Grouped box plot from *precomputed* stats. One box per bucket, positioned on
 * the x-axis by deck name and colored by strategy, so boxes for the same deck
 * sit side-by-side. Outliers are drawn as a separate marker trace (Plotly is
 * never handed raw `y`, so it can't recompute the quartiles).
 */
export function BoxChart({ series, title }: { series: BoxSeries[]; title: string }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const node = ref.current;
    if (!node) return;
    // One legend entry per strategy; suppress duplicates across decks.
    const seenStrategy = new Set<string>();
    const data: Plotly.Data[] = [];
    for (const s of series) {
      const showInLegend = !seenStrategy.has(s.strategy);
      seenStrategy.add(s.strategy);
      data.push({
        type: "box" as const,
        name: s.strategy,
        legendgroup: s.strategy,
        showlegend: showInLegend,
        x: [s.deck],
        q1: [s.q1],
        median: [s.median],
        q3: [s.q3],
        lowerfence: [s.lo],
        upperfence: [s.hi],
        marker: { color: s.color },
        line: { color: s.color },
        hoverinfo: "y" as const,
      } as Plotly.Data);
      if (s.outliers.length > 0) {
        data.push({
          type: "scatter" as const,
          mode: "markers" as const,
          name: s.strategy,
          legendgroup: s.strategy,
          showlegend: false,
          x: s.outliers.map(() => s.deck),
          y: s.outliers,
          marker: { color: s.color, symbol: "x" as const, size: 7 },
          hovertemplate: "outlier %{y}<extra>" + s.strategy + "</extra>",
        });
      }
    }
    Plotly.react(node, data, {
      title: { text: title },
      boxmode: "group",
      xaxis: { title: { text: "deck" } },
      yaxis: { title: { text: "damage taken" } },
      // Legend above the plot area (matches DistChart).
      margin: { t: 80, r: 16 },
      legend: { orientation: "h", x: 0, y: 1.02, yanchor: "bottom" },
    });
    return () => {
      Plotly.purge(node);
    };
  }, [series, title]);
  return <div ref={ref} style={{ width: "100%", height: 420 }} />;
}
