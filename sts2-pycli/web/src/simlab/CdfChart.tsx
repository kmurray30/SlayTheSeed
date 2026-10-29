import { useEffect, useRef } from "react";
import Plotly from "plotly.js-dist-min";
import { cdfPoints } from "./chartData";

export interface CdfSeries {
  label: string;
  color: string;
  /** Raw damage values for this bucket. */
  values: number[];
}

/** Step CDF (P(dmg ≤ x)) — one staircase per bucket. */
export function CdfChart({ series, title }: { series: CdfSeries[]; title: string }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const node = ref.current;
    if (!node) return;
    const data: Plotly.Data[] = series.map((s) => {
      const { x, y } = cdfPoints(s.values);
      return {
        x,
        y,
        type: "scatter" as const,
        mode: "lines" as const,
        name: s.label,
        line: { shape: "hv" as const, width: 2.5, color: s.color },
        hovertemplate: "dmg ≤ %{x}<br>P = %{y:.3f}<extra>" + s.label + "</extra>",
      };
    });
    Plotly.react(node, data, {
      title: { text: title },
      xaxis: { title: { text: "damage taken" } },
      yaxis: { title: { text: "P(dmg ≤ x)" }, range: [0, 1.02] },
      // Legend above the plot area (matches DistChart; avoids the x-axis title).
      margin: { t: 80, r: 16 },
      legend: { orientation: "h", x: 0, y: 1.02, yanchor: "bottom" },
    });
    return () => {
      Plotly.purge(node);
    };
  }, [series, title]);
  return <div ref={ref} style={{ width: "100%", height: 420 }} />;
}
