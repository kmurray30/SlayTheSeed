import { useCallback, useEffect, useState } from "react";
import { fetchMctsRuns, fetchMctsAggregate, type MctsRunSummary, type MctsAggregate } from "../api";

/** Aggregate dashboard + table of recorded MCTS runs. Clicking a row opens that
 *  run in the existing timeline replay via `onOpen(path)`. */
export function MctsRuns({ onOpen }: { onOpen: (path: string) => void }) {
  const [runs, setRuns] = useState<MctsRunSummary[]>([]);
  const [agg, setAgg] = useState<MctsAggregate | null>(null);
  const [loading, setLoading] = useState(false);
  // Re-fetch the run list + aggregate. The list is only otherwise loaded on
  // mount, so runs recorded after this page opened won't appear until the user
  // hits Refresh (or remounts the component).
  const refresh = useCallback(() => {
    setLoading(true);
    Promise.all([
      fetchMctsRuns().then((d) => setRuns(d.runs)),
      fetchMctsAggregate().then(setAgg),
    ]).finally(() => setLoading(false));
  }, []);
  useEffect(() => {
    refresh();
  }, [refresh]);
  return (
    <div className="mono" style={{ padding: 12 }}>
      <h2 style={{ display: "flex", alignItems: "center", gap: 12 }}>
        MCTS Runs
        <button onClick={refresh} disabled={loading} title="re-scan the logs dir for new runs">
          {loading ? "…" : "↻ Refresh"}
        </button>
      </h2>
      {agg && (
        <div style={{ marginBottom: 12 }}>
          <div>runs {agg.n_runs} · losses {agg.n_losses}</div>
          <div>turn-1 Q: min {agg.turn1_q.min} · max {agg.turn1_q.max} · mean {agg.turn1_q.mean}</div>
          <div>tags: {Object.entries(agg.pattern_tags).map(([k, v]) => `${k}×${v}`).join("  ")}</div>
        </div>
      )}
      <table>
        <thead><tr><th>run</th><th>outcome</th><th>floor</th><th>turn</th><th>turn-1 Q</th><th>enemy min%</th></tr></thead>
        <tbody>
          {runs.map((r) => (
            <tr key={r.path} onClick={() => onOpen(r.path)} style={{ cursor: "pointer" }}>
              <td>{r.stem}</td>
              <td style={{ color: r.won ? "var(--win, #6c6)" : "var(--loss, #c66)" }}>{r.won ? "WON" : "LOST"}</td>
              <td>{r.final_floor ?? ""}</td>
              <td>{r.final_turn ?? ""}</td>
              <td>{r.turn1_q?.toFixed(2)}</td>
              <td>{r.enemy_min_pct != null ? `${r.enemy_min_pct.toFixed(0)}%` : ""}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {!loading && runs.length === 0 && (
        // Say WHY it is empty. "runs 0" alone reads as "your runs were not
        // recorded" when the truth is usually "nothing has been recorded here
        // yet" — and on the Cloud Run deploy the logs dir starts empty on
        // every cold start.
        <div style={{ color: "var(--muted)", marginTop: 8 }}>
          No recorded runs in the logs directory yet. Runs land here when you end one
          (the run-end screen’s “Review replay”) or after <code>python -m workbench.record</code>.
        </div>
      )}
    </div>
  );
}
