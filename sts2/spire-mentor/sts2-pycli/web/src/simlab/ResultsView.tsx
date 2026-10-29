import { useCallback, useEffect, useMemo, useState } from "react";
import type Plotly from "plotly.js-dist-min";
import { api, type Bucket, type Results, type SweepStatus } from "./api";
import { DistChart, type Trace } from "./DistChart";
import { CdfChart, type CdfSeries } from "./CdfChart";
import { BoxChart, type BoxSeries } from "./BoxChart";
import { seriesLabels, oneIn } from "./chartData";
import { card, td, th } from "./styles";
import { ProgressBar, ThroughputSummary } from "./widgets";

// ---------------------------------------------------------------------------
// ResultsView
// ---------------------------------------------------------------------------
interface ParsedKey {
  enc: string;
  asc: string;
  deck: string;
  strategy: string;
  cell: string;
}

/** Cell label: a sweep from a position has no encounter — say what it is. */
function cellLabel(enc: string, asc: string): string {
  return enc === "imported" ? `position @ A${asc}` : `${enc} @ A${asc}`;
}

function parseKey(key: string): ParsedKey | null {
  // New keys are `${enc}/${asc}/${deck}/${strategy}` (4 parts); legacy results
  // may still use the old 3-part `${enc}/${asc}/${strategy}`. Encounter ids and
  // deck names never contain "/" (deck names with "/" are rejected server-side).
  const parts = key.split("/");
  if (parts.some((p) => p === "")) return null;
  if (parts.length === 4) {
    const [enc, asc, deck, strategy] = parts;
    return { enc, asc, deck, strategy, cell: cellLabel(enc, asc) };
  }
  if (parts.length === 3) {
    const [enc, asc, strategy] = parts;
    return { enc, asc, deck: "", strategy, cell: cellLabel(enc, asc) };
  }
  return null;
}

function fmt(n: number, digits = 1): string {
  return Number.isFinite(n) ? n.toFixed(digits) : "—";
}

type ChartTab = "dist" | "cdf" | "box";

/** One series within a cell: a parsed bucket key + its data + display color. */
interface CellSeries {
  key: string;
  label: string;
  color: string;
  deck: string;
  strategy: string;
  bucket: Bucket;
}

const tabButton = (active: boolean): React.CSSProperties => ({
  padding: "4px 12px",
  border: "1px solid var(--border)",
  borderBottom: active ? "1px solid var(--panel)" : "1px solid var(--border)",
  background: active ? "var(--panel)" : "var(--panel-raised)",
  fontWeight: active ? 600 : 400,
  cursor: "pointer",
  borderRadius: "4px 4px 0 0",
  marginRight: 2,
  fontSize: 13,
});

export default function ResultsView({ id, name }: { id: string; name?: string }) {
  const [status, setStatus] = useState<SweepStatus | null>(null);
  const [results, setResults] = useState<Results | null>(null);
  // Baseline is at most one of strategy / deck (mutually exclusive). `null`
  // means "no baseline of that kind". Starts null (not a hardcoded strategy):
  // the first results load must NOT request a baseline the sweep lacks, or the
  // backend 422s and — since the baseline can only be corrected once results
  // load — the view deadlocks (e.g. an all-`mcts` sweep vs the old default
  // "min_overblock"). The effect below picks a valid default once results load.
  const [baseline, setBaseline] = useState<string | null>(null);
  const [baselineDeck, setBaselineDeck] = useState<string | null>(null);
  const [tab, setTab] = useState<ChartTab>("dist");
  const [err, setErr] = useState<string | null>(null);
  // True once Stop is clicked, until the status leaves "running". Drives the
  // button's "stopping…" label so a click gives immediate feedback (the kill +
  // status flip take a moment).
  const [stopping, setStopping] = useState(false);

  const onStop = useCallback(async () => {
    setStopping(true);
    try {
      await api.stop(id);
    } catch (e) {
      // A 404 just means it already finished between render and click — the
      // poll will reflect the terminal status. Surface anything else.
      setErr(String(e));
    }
  }, [id]);

  // Poll status while running, then load results.
  useEffect(() => {
    let cancelled = false;
    setResults(null);
    setStatus(null);
    setErr(null);
    setStopping(false);

    const tick = async () => {
      try {
        const st = await api.status(id);
        if (cancelled) return;
        setErr(null);
        setStatus(st);
        if (st.status === "running") {
          timer = window.setTimeout(tick, 1500);
        }
      } catch (e) {
        if (cancelled) return;
        // Keep polling on a transient fetch error (5s backoff); show it meanwhile.
        setErr(String(e));
        timer = window.setTimeout(tick, 5000);
      }
    };
    let timer = window.setTimeout(tick, 0);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [id]);

  const loadResults = useCallback(async () => {
    try {
      setErr(null);
      setResults(
        await api.results(id, {
          baseline: baselineDeck ? undefined : baseline ?? undefined,
          baselineDeck: baselineDeck ?? undefined,
        }),
      );
    } catch (e) {
      setErr(String(e));
    }
  }, [id, baseline, baselineDeck]);

  // Load (and reload on baseline change) once the sweep is done — or cancelled,
  // which carries whatever partial buckets completed before the stop.
  useEffect(() => {
    if (status?.status === "done" || status?.status === "cancelled") loadResults();
  }, [status?.status, loadResults]);

  const decksInResults = useMemo(() => {
    if (!results) return [];
    const decks = Object.keys(results.buckets)
      .map((k) => parseKey(k)?.deck)
      .filter((d): d is string => d !== undefined && d !== "");
    return [...new Set(decks)].sort();
  }, [results]);
  const multiDeck = decksInResults.length > 1;

  // Buckets grouped by cell (enc @ Aasc). Within a cell, series get short labels
  // and stable colors via seriesLabels (keyed by the bucket key).
  const cells = useMemo(() => {
    if (!results) return [] as [string, CellSeries[]][];
    const byCell = new Map<string, { key: string; bucket: Bucket; parsed: ParsedKey }[]>();
    for (const [key, bucket] of Object.entries(results.buckets)) {
      const parsed = parseKey(key);
      if (!parsed) continue;
      const arr = byCell.get(parsed.cell) ?? [];
      arr.push({ key, bucket, parsed });
      byCell.set(parsed.cell, arr);
    }
    const out: [string, CellSeries[]][] = [...byCell.entries()].map(([cell, items]) => {
      const infos = seriesLabels(items.map((it) => it.key));
      const series: CellSeries[] = items.map((it, i) => ({
        key: it.key,
        label: infos[i]?.label ?? it.parsed.strategy,
        color: infos[i]?.color ?? "#1f77b4",
        deck: it.parsed.deck,
        strategy: it.parsed.strategy,
        bucket: it.bucket,
      }));
      return [cell, series];
    });
    return out.sort(([a], [b]) => a.localeCompare(b));
  }, [results]);

  // Precompute each cell's Plotly inputs once per loaded result set. Building
  // these inline in the render below produced fresh array refs every render, so
  // the App-level 2s sweep-list poll (refresh -> setSweeps -> re-render) re-ran
  // each chart's effect and called Plotly.react — which discards a user's
  // legend toggle (the hidden strategy reappeared ~2s after being clicked off).
  // Keying on `cells` (memoized on `results`) keeps the refs stable so a legend
  // hide/show survives the poll. See DistChart's effect deps.
  const chartData = useMemo(
    () =>
      cells.map(([cell, entries]) => {
        const traces: Trace[] = entries.map(({ label, color, bucket }) => ({
          label,
          color,
          kde: bucket.kde,
          values: bucket.dmg_values,
        }));
        // Mean (dashed) + mode (dotted) vlines, only when few enough buckets to
        // stay legible (>4 would overwhelm the plot).
        const showMarkers = entries.length <= 4;
        const shapes: Partial<Plotly.Shape>[] = showMarkers
          ? entries.flatMap(({ color, bucket }) => [
              {
                type: "line" as const,
                x0: bucket.mean_dmg,
                x1: bucket.mean_dmg,
                yref: "paper" as const,
                y0: 0,
                y1: 1,
                line: { color, width: 1.5, dash: "dash" as const },
              },
              {
                type: "line" as const,
                x0: bucket.mode_dmg,
                x1: bucket.mode_dmg,
                yref: "paper" as const,
                y0: 0,
                y1: 1,
                line: { color, width: 1.5, dash: "dot" as const },
              },
            ])
          : [];
        const annotations: Partial<Plotly.Annotations>[] = showMarkers
          ? entries.flatMap(({ color, bucket }) => [
              {
                x: bucket.mean_dmg,
                yref: "paper" as const,
                y: 1,
                text: "mean",
                showarrow: false,
                font: { size: 9, color },
                yanchor: "bottom" as const,
              },
              {
                x: bucket.mode_dmg,
                yref: "paper" as const,
                y: 0.94,
                text: "mode",
                showarrow: false,
                font: { size: 9, color },
                yanchor: "bottom" as const,
              },
            ])
          : [];
        const cdfSeries: CdfSeries[] = entries.map(({ label, color, bucket }) => ({
          label,
          color,
          values: bucket.dmg_values,
        }));
        const boxSeries: BoxSeries[] = entries.map(({ deck, strategy, color, bucket }) => ({
          strategy,
          // When the sweep has a single deck, deck part is "" — fall back to a
          // generic x-category so the boxes still render.
          deck: deck || "deck",
          color,
          q1: bucket.box.q1,
          median: bucket.box.median,
          q3: bucket.box.q3,
          lo: bucket.box.lo,
          hi: bucket.box.hi,
          outliers: bucket.box.outliers,
        }));
        return { cell, entries, traces, shapes, annotations, cdfSeries, boxSeries };
      }),
    [cells],
  );

  const strategiesInResults = useMemo(() => {
    if (!results) return [];
    const strategies = Object.keys(results.buckets)
      .map((k) => parseKey(k)?.strategy)
      .filter((s): s is string => s !== undefined);
    return [...new Set(strategies)].sort();
  }, [results]);

  // Once results load, pick a baseline that actually exists in this sweep so we
  // never request one the backend will 422 on. `strategiesInResults` is derived
  // from the loaded buckets, so any choice here is guaranteed valid. Prefer
  // min_overblock (the conventional reference) when present; with a single
  // strategy a baseline-vs-itself is meaningless, so leave it as none. Skipped
  // while a deck baseline is in effect (it leaves `baseline` dormant).
  useEffect(() => {
    if (baselineDeck || strategiesInResults.length === 0) return;
    if (baseline !== null && strategiesInResults.includes(baseline)) return;
    const next =
      strategiesInResults.length < 2
        ? null
        : strategiesInResults.includes("min_overblock")
          ? "min_overblock"
          : strategiesInResults[0];
    setBaseline(next);
  }, [strategiesInResults, baseline, baselineDeck]);

  // Default the chart tab to box plot when the sweep spans >1 deck, else dist.
  // Keyed on the `decksInResults` array identity (a fresh array per loaded
  // result set) rather than the derived `multiDeck` boolean, so the default is
  // re-applied whenever a different sweep loads — even one that happens to share
  // the same multi/single-deck-ness as the previous selection.
  useEffect(() => {
    setTab(decksInResults.length > 1 ? "box" : "dist");
  }, [decksInResults]);

  const title = name ?? id;
  if (err) return <div style={{ ...card, color: "var(--bad, #d66)" }}>Error: {err}</div>;
  if (!status) return <div style={card}>Loading…</div>;
  if (status.status === "error")
    return (
      <div style={{ ...card, color: "var(--bad, #d66)" }}>
        <strong>{title}</strong> — sweep failed: {status.error}
      </div>
    );
  // "cancelled" carries partial results, so it falls through to the results
  // view below (with a note); only genuinely-in-flight states show the live
  // progress header + Stop button.
  if (status.status !== "done" && status.status !== "cancelled")
    return (
      <div
        style={{
          ...card,
          display: "flex",
          alignItems: "center",
          gap: 12,
          flexWrap: "nowrap",
        }}
      >
        <strong style={{ whiteSpace: "nowrap" }}>{title}</strong>
        <ProgressBar done={status.done} total={status.total} width={200} />
        <span style={{ color: "var(--muted)", fontSize: 13, whiteSpace: "nowrap" }}>
          {status.total ? `${status.done ?? 0}/${status.total}` : "running…"}
        </span>
        {status.combats_per_s !== undefined && (
          <span style={{ color: "var(--accent)", fontSize: 13, whiteSpace: "nowrap" }}>
            {status.combats_per_s.toFixed(1)} combat/s
            {status.jobs ? ` · ${status.jobs} workers` : ""}
          </span>
        )}
        {status.status === "running" && (
          <button
            type="button"
            onClick={onStop}
            disabled={stopping}
            style={{
              marginLeft: "auto",
              padding: "3px 12px",
              fontSize: 13,
              color: "#fff",
              background: stopping ? "#bdbdbd" : "var(--bad, #d66)",
              border: "none",
              borderRadius: 4,
              cursor: stopping ? "default" : "pointer",
              whiteSpace: "nowrap",
            }}
          >
            {stopping ? "stopping…" : "Stop"}
          </button>
        )}
      </div>
    );

  return (
    <div>
      {status.status === "cancelled" && (
        <div
          style={{
            ...card,
            color: "var(--muted)",
            fontSize: 13,
            display: "flex",
            alignItems: "center",
            gap: 6,
          }}
        >
          <span>⊘</span>
          cancelled — partial results
          {status.total ? ` (${status.done ?? 0}/${status.total} combats)` : ""}
        </div>
      )}
      <div style={{ ...card, display: "flex", alignItems: "center", gap: 16, flexWrap: "wrap" }}>
        <label>
          Baseline strategy:{" "}
          <select
            value={baselineDeck ? "" : baseline ?? ""}
            onChange={(e) => {
              // Selecting a strategy baseline clears any deck baseline.
              setBaselineDeck(null);
              setBaseline(e.target.value || null);
            }}
          >
            <option value="">(none)</option>
            {strategiesInResults.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
        {multiDeck && (
          <label>
            Baseline deck:{" "}
            <select
              value={baselineDeck ?? ""}
              onChange={(e) => {
                // Selecting a deck baseline disables the strategy baseline.
                const v = e.target.value || null;
                setBaselineDeck(v);
                if (v) setBaseline(null);
              }}
            >
              <option value="">(none)</option>
              {decksInResults.map((d) => (
                <option key={d} value={d}>
                  {d}
                </option>
              ))}
            </select>
          </label>
        )}
        {results && results.errors_total > 0 && (
          <span style={{ color: "var(--bad, #d66)" }}>
            {results.errors_total} run error(s)
          </span>
        )}
        {results && <ThroughputSummary results={results} status={status} />}
      </div>

      {/* One global chart-tab bar: `tab` is shared across all cells, so the
          control must be a single row above the loop (not per-card). */}
      {cells.length > 0 && (
        <div style={{ display: "flex", borderBottom: "1px solid var(--border)", marginBottom: 12 }}>
          <button type="button" style={tabButton(tab === "dist")} onClick={() => setTab("dist")}>
            Distribution
          </button>
          <button type="button" style={tabButton(tab === "cdf")} onClick={() => setTab("cdf")}>
            CDF
          </button>
          <button type="button" style={tabButton(tab === "box")} onClick={() => setTab("box")}>
            Box plot
          </button>
        </div>
      )}

      {chartData.map(({ cell, entries, traces, shapes, annotations, cdfSeries, boxSeries }) => {
        return (
          <div key={cell} style={card}>
            {tab === "dist" && (
              <DistChart
                traces={traces}
                title={cell}
                shapes={shapes}
                annotations={annotations}
              />
            )}
            {tab === "cdf" && <CdfChart series={cdfSeries} title={cell} />}
            {tab === "box" && <BoxChart series={boxSeries} title={cell} />}
            <table style={{ borderCollapse: "collapse", width: "100%", marginTop: 12 }}>
              <thead>
                <tr>
                  <th style={{ ...th, textAlign: "left" }}>strategy</th>
                  <th style={th}>n</th>
                  <th style={th}>mean</th>
                  <th style={th}>median</th>
                  <th style={th}>mode</th>
                  <th style={th}>p95</th>
                  <th style={th}>max</th>
                  <th style={th}>worst</th>
                  <th style={th}>win%</th>
                  <th style={th}>turns</th>
                  <th style={th}>errors</th>
                  <th style={th}>Δ vs base</th>
                </tr>
              </thead>
              <tbody>
                {entries.map(({ key, label, strategy, deck, bucket }) => {
                  const isBase = baselineDeck
                    ? deck === baselineDeck
                    : strategy === baseline;
                  return (
                    <tr key={key}>
                      <td style={{ ...td, textAlign: "left" }}>
                        {label}
                        {isBase ? " (base)" : ""}
                      </td>
                      <td style={td}>{bucket.n}</td>
                      <td style={td}>{fmt(bucket.mean_dmg)}</td>
                      <td style={td}>{fmt(bucket.median_dmg)}</td>
                      <td style={td}>{bucket.mode_dmg}</td>
                      <td style={td}>{fmt(bucket.p95_dmg)}</td>
                      <td style={td}>{bucket.max_dmg}</td>
                      <td style={td}>
                        {bucket.worst} · {oneIn(bucket.worst_p)}
                      </td>
                      <td style={td}>{fmt(bucket.win_rate * 100)}</td>
                      <td style={td}>{fmt(bucket.mean_turns)}</td>
                      <td style={td}>{bucket.errors}</td>
                      <td style={td}>
                        {bucket.delta_vs_baseline
                          ? fmt(bucket.delta_vs_baseline.mean)
                          : "—"}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        );
      })}
    </div>
  );
}
