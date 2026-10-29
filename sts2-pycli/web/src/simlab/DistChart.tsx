import { useEffect, useRef } from "react";
import Plotly from "plotly.js-dist-min";

export interface Trace {
  label: string;
  /** Series color shared by the KDE curve and its histogram bars. */
  color: string;
  kde: { x: number[]; y: number[] };
  values: number[];
}

/** Largest per-value probability mass — the height of the tallest unit bin. */
function modalMass(values: number[]): number {
  if (!values.length) return 0;
  const freq = new Map<number, number>();
  let max = 0;
  for (const v of values) {
    const n = (freq.get(v) ?? 0) + 1;
    freq.set(v, n);
    if (n > max) max = n;
  }
  return max / values.length;
}

/** Overlaid KDE curves + transparent histograms per strategy. */
export function DistChart({
  traces,
  title,
  shapes,
  annotations,
}: {
  traces: Trace[];
  title: string;
  /** Optional vertical marker lines (e.g. per-bucket mean/mode). */
  shapes?: Partial<Plotly.Shape>[];
  /** Optional annotations paired with the marker lines. */
  annotations?: Partial<Plotly.Annotations>[];
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const node = ref.current;
    if (!node) return;
    const data: Plotly.Data[] = traces.flatMap((t) => {
      // The backend KDE smears each integer's mass over a ~1-2 damage
      // bandwidth, so its raw peak sits well below the unit-bin bar heights.
      // Rescale the curve so its peak matches the tallest bar — the curve is
      // a shape guide, not a calibrated density, so visual agreement wins.
      const peak = t.kde.y.reduce((a, b) => Math.max(a, b), 0);
      const scale = peak > 0 ? modalMass(t.values) / peak : 1;
      return [
        {
          x: t.values,
          type: "histogram" as const,
          name: `${t.label} (hist)`,
          opacity: 0.25,
          histnorm: "probability density" as const,
          showlegend: false,
          // same group as the KDE curve: one legend click hides/shows both
          legendgroup: t.label,
          marker: { color: t.color },
          xbins: {
            start: t.values.length ? t.values.reduce((a, b) => Math.min(a, b)) - 0.5 : 0,
            end: t.values.length ? t.values.reduce((a, b) => Math.max(a, b)) + 0.5 : 1,
            size: 1,
          },
        },
        {
          x: t.kde.x,
          y: t.kde.y.map((y) => y * scale),
          type: "scatter" as const,
          mode: "lines" as const,
          name: t.label,
          legendgroup: t.label,
          line: { width: 2.5, color: t.color },
        },
      ];
    });
    Plotly.react(node, data, {
      title: { text: title },
      barmode: "overlay",
      xaxis: { title: { text: "damage taken" } },
      // unit bins -> bar height = probability of that damage value; the KDE
      // curve is rescaled to the same peak (see above).
      yaxis: { title: { text: "probability" } },
      // legend goes ABOVE the plot area (anchored to its top edge) — the
      // default horizontal-legend position is below the plot, where it
      // collides with the x-axis title once 4-5 strategies are shown.
      margin: { t: 80, r: 16 },
      legend: {
        orientation: "h",
        x: 0,
        y: 1.02,
        yanchor: "bottom",
        // a legend click toggles the whole group (curve + its hidden-entry bars)
        groupclick: "togglegroup",
      },
      shapes,
      annotations,
    });
    return () => {
      Plotly.purge(node);
    };
  }, [traces, title, shapes, annotations]);
  return <div ref={ref} style={{ width: "100%", height: 420 }} />;
}
