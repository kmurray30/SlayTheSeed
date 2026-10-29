import { useEffect, useState } from "react";
import { api, type BugReportDump } from "../api";
import { loadReportIntoWorkbench, reportLoadKind, type LoadedReport } from "../bugreport";
import { isStaticMode } from "../transport";

/** Read-only inspection view for a captured bug report (no live engine).
 *
 *  In the browser build a report can also be TAKEN: a live report carries the
 *  reporter's persisted tree, a replay report the run log, and "Load into this
 *  workbench" puts either into this browser's engine (src/bugreport.ts) —
 *  the live run resumes on the reload, a log shows up in the Runs list. */
export default function BugReportView({ id }: { id: string }) {
  const [dump, setDump] = useState<BugReportDump | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState<LoadedReport | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    api
      .fetchBugReport(id)
      .then((d) => live && setDump(d))
      .catch(() => live && setNotFound(true));
    return () => {
      live = false;
    };
  }, [id]);

  if (notFound) return <div className="bugreport-view">Report not found: {id}</div>;
  if (!dump) return <div className="bugreport-view">Loading {id}…</div>;

  const meta = dump.meta as Record<string, unknown>;
  const loadKind = isStaticMode() ? reportLoadKind(dump) : null;

  async function load() {
    setLoading(true);
    setLoadError(null);
    try {
      setLoaded(await loadReportIntoWorkbench(dump!));
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : "load failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="bugreport-view">
      <h1>Bug report {String(meta.id ?? id)}</h1>
      <dl>
        <dt>captured</dt><dd>{String(meta.captured_at ?? "")}</dd>
        <dt>mode</dt><dd>{String(meta.mode ?? "")}</dd>
        <dt>character</dt><dd>{String(meta.character ?? "")}</dd>
        <dt>engine</dt><dd>{String(meta.engine_build ?? "")} @ {String(meta.workbench_commit ?? "")}</dd>
        {meta.host != null && <><dt>host</dt><dd>{String(meta.host)}</dd></>}
        {meta.user_agent != null && <><dt>browser</dt><dd>{String(meta.user_agent)}</dd></>}
        <dt>screen / overlay</dt><dd>{String(dump.ui.screen)} / {String(dump.ui.overlay)}</dd>
      </dl>
      {dump.note && <p className="bugreport-note">Note: {dump.note}</p>}
      {loadKind && !loaded && (
        <p className="bugreport-load">
          <button type="button" onClick={load} disabled={loading} data-testid="bugreport-load"
            title={loadKind === "live"
              ? "replace this browser's live run with the reporter's (it resumes where they stood)"
              : "add the reporter's run log to this browser's Runs list"}>
            {loading ? "Loading…" : loadKind === "live" ? "▶ Load into this workbench" : "▶ Add run to this workbench"}
          </button>
          {loadError && <span className="bugreport-error"> {loadError}</span>}
        </p>
      )}
      {loaded && (
        <p className="bugreport-load" data-testid="bugreport-loaded">
          {loaded.kind === "live"
            ? "Loaded — this is now the live run. "
            : `Stored as ${loaded.path} — open it from the Runs list. `}
          <a href="/">Open the workbench</a>
        </p>
      )}
      <h2>repro</h2>
      <pre>{JSON.stringify(reproForDisplay(dump.repro), null, 2)}</pre>
      <h2>state</h2>
      <pre>{JSON.stringify(dump.state, null, 2)}</pre>
    </div>
  );
}

/** The repro block without its bulk: a run log is thousands of lines and a
 *  persisted tree hundreds of nodes; show their size, not their bodies. */
function reproForDisplay(repro: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = { ...repro };
  if (typeof out.run_log === "string") out.run_log = `<${out.run_log.length} chars, ${out.run_log.split("\n").filter(Boolean).length} records>`;
  const p = out.persist as { nodes?: unknown[] } | undefined;
  if (p && typeof p === "object") out.persist = `<persisted tree, ${Array.isArray(p.nodes) ? p.nodes.length : "?"} nodes>`;
  return out;
}
