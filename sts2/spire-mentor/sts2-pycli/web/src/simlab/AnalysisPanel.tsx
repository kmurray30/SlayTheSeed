import { useCallback, useEffect, useRef, useState } from "react";
import NumberInput from "../components/NumberInput";
import { api, type BuiltPosition, type Catalog, type MctsRow, type SweepSpec } from "./api";
import type { Position } from "./position";

/** How the engine last answered a build request. */
export type BuildStatus =
  | { state: "idle" }
  | { state: "building" }
  | { state: "built"; built: BuiltPosition }
  | { state: "error"; message: string };

/**
 * The right column. Three cards:
 *  - Position: the engine's verdict on the current position (rebuilt on every
 *    edit after a short pause — that is what "Built." certifies).
 *  - Play out: a start-state sweep — trials per strategy from this exact
 *    board, optionally one cell per legal first move.
 *  - Search: an MCTS verdict per first move, the same search the workbench's
 *    hint runs.
 */
export default function AnalysisPanel({
  position,
  catalog,
  onSubmitted,
  onNormalized,
  sweepsEnabled = true,
}: {
  position: Position;
  catalog: Catalog;
  onSubmitted: (sweepId: string) => void;
  /** The engine rolled something the position left open (an enemy intent); adopt it. */
  onNormalized?: (next: Position) => void;
  /** Whether the distribution "play it out" sweep is available (desktop only). */
  sweepsEnabled?: boolean;
}) {
  const [status, setStatus] = useState<BuildStatus>({ state: "idle" });
  const seq = useRef(0);

  // Debounced build: every edit re-asks the engine. Out-of-order replies are
  // dropped by sequence so a slow older build cannot overwrite a newer one.
  useEffect(() => {
    const mine = ++seq.current;
    setStatus({ state: "building" });
    const t = setTimeout(() => {
      api
        .buildPosition(position)
        .then((built) => {
          if (mine !== seq.current) return;
          setStatus({ state: "built", built });
          const rolled = position.monsters.some((m, i) => m.intent === null && built.position.monsters[i]?.intent);
          if (rolled && onNormalized) {
            onNormalized({
              ...position,
              monsters: position.monsters.map((m, i) => (m.intent === null ? { ...m, intent: built.position.monsters[i]?.intent ?? null } : m)),
            });
          }
        })
        .catch((e: Error) => {
          if (mine !== seq.current) return;
          setStatus({ state: "error", message: e.message || String(e) });
        });
    }, 350);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [position]);

  const built = status.state === "built" ? status.built : null;

  return (
    <div className="sl-analysis">
      <BuildCard status={status} />
      {sweepsEnabled && <PlayOutCard position={position} built={built} catalog={catalog} onSubmitted={onSubmitted} />}
      <SearchCard position={position} built={built} />
    </div>
  );
}

function BuildCard({ status }: { status: BuildStatus }) {
  return (
    <section className="sl-panel">
      <div className="sl-panel-head">
        <span className="sl-label">Position</span>
      </div>
      {status.state === "building" && <div className="sl-status sl-status-wait">Building…</div>}
      {status.state === "idle" && <div className="sl-status sl-status-wait">Waiting for a position.</div>}
      {status.state === "error" && (
        <div className="sl-status sl-status-bad" role="alert">
          <b>Not built.</b> {status.message}
        </div>
      )}
      {status.state === "built" && (
        <div className="sl-status sl-status-good">
          <b>Built.</b>{" "}
          {status.built.notes.length === 0 ? "The engine accepted this position with nothing to report." : "The engine accepted this position, with notes:"}
          {status.built.notes.length > 0 && (
            <ul>
              {status.built.notes.map((n, i) => (
                <li key={i}>{n}</li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  );
}

function PlayOutCard({
  position,
  built,
  catalog,
  onSubmitted,
}: {
  position: Position;
  built: BuiltPosition | null;
  catalog: Catalog;
  onSubmitted: (id: string) => void;
}) {
  const strategies = catalog.strategies.length ? catalog.strategies : ["min_overblock", "mcts"];
  const [picked, setPicked] = useState<string[]>(["min_overblock"]);
  const [trials, setTrials] = useState(200);
  const [compare, setCompare] = useState(true);
  const [mctsSims, setMctsSims] = useState(200);
  const [mctsDepth, setMctsDepth] = useState(200);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [moveCount, setMoveCount] = useState<number | null>(null);

  // A rough cell count so the user sees what they are about to spend before
  // pressing Run (the sweep runs under a wall-clock cap).
  useEffect(() => {
    if (!compare) {
      setMoveCount(1);
      return;
    }
    const hand = position.piles.hand.length;
    setMoveCount(hand + position.player.potions.length + 1);
  }, [position, compare]);

  const toggle = (s: string) => setPicked((cur) => (cur.includes(s) ? cur.filter((x) => x !== s) : [...cur, s]));

  const run = useCallback(async () => {
    if (!built) return;
    setErr(null);
    if (picked.length === 0) {
      setErr("pick at least one strategy");
      return;
    }
    setBusy(true);
    try {
      const cells: [string, string][] = [];
      const positions: [string, string][] = [];
      if (compare) {
        const { moves } = await api.firstMoves(position);
        const taken = new Map<string, number>();
        for (const m of moves) {
          if (m.note || m.terminal || !m.wire) continue;
          const n = (taken.get(m.label) ?? 0) + 1;
          taken.set(m.label, n);
          const label = (n > 1 ? `${m.label} #${n}` : m.label).replace(/\//g, "∕");
          cells.push([label, JSON.stringify(m.wire)]);
          if (m.position) positions.push([label, JSON.stringify(m.position)]);
        }
        if (cells.length === 0) setErr("no first move can be swept from (every move opens a choice or ends the fight)");
      }
      if (cells.length === 0) {
        cells.push(["as is", JSON.stringify(built.wire)]);
        positions.push(["as is", JSON.stringify(built.position)]);
      }
      const spec: SweepSpec = {
        name: `${position.encounter ?? (position.monsters.map((m) => m.monster).join("+") || "position")} · T${position.turn}`,
        encounters: [],
        ascensions: [],
        strategies: picked,
        n_seeds: trials,
        decks: [],
        mcts_sims: mctsSims,
        mcts_depth: mctsDepth,
        start_states: cells,
        positions,
      };
      const { sweep_id } = await api.submit(spec);
      onSubmitted(sweep_id);
    } catch (e) {
      setErr(String((e as Error).message ?? e));
    } finally {
      setBusy(false);
    }
  }, [built, picked, compare, position, trials, mctsSims, mctsDepth, onSubmitted]);

  const cells = (moveCount ?? 1) * picked.length;
  return (
    <section className="sl-panel">
      <div className="sl-panel-head">
        <span className="sl-label">Play out</span>
      </div>
      <p className="sl-blurb">
        Play this exact position to the end of the fight many times under each strategy — same hand, same pile order, same
        telegraphs; only the dice that have not been rolled yet vary. The result is a distribution of HP lost, not one playout.
      </p>
      <div className="sl-row sl-wrap">
        <fieldset className="sl-fieldset">
          <legend>strategies</legend>
          {strategies.map((s) => (
            <label key={s} className="sl-check">
              <input type="checkbox" checked={picked.includes(s)} onChange={() => toggle(s)} /> {s}
            </label>
          ))}
        </fieldset>
        <label className="sl-field">
          <span>trials</span>
          <NumberInput min={1} value={trials} onChange={setTrials} width={72} ariaLabel="trials" />
        </label>
        <label className="sl-check" title="one cell per legal first move (the Jorbs table): play X, play Y, drink Z, end turn">
          <input type="checkbox" checked={compare} onChange={(e) => setCompare(e.target.checked)} /> compare first moves
        </label>
      </div>
      {picked.includes("mcts") && (
        <div className="sl-row">
          <label className="sl-field">
            <span>mcts sims / decision</span>
            <NumberInput min={1} value={mctsSims} onChange={setMctsSims} width={72} ariaLabel="mcts sims" />
          </label>
          <label className="sl-field">
            <span>rollout depth</span>
            <NumberInput min={1} value={mctsDepth} onChange={setMctsDepth} width={72} ariaLabel="mcts depth" />
          </label>
        </div>
      )}
      <div className="sl-row">
        <button type="button" className="sl-primary" onClick={run} disabled={busy || !built}>
          {busy ? "starting…" : "play it out"}
        </button>
        <span className="sl-muted">
          ≈ {cells} cell{cells === 1 ? "" : "s"} × {trials} trials{picked.includes("mcts") ? " · mcts is minutes per cell" : ""}
        </span>
      </div>
      {err && <div className="sl-error">{err}</div>}
    </section>
  );
}

function SearchCard({ position, built }: { position: Position; built: BuiltPosition | null }) {
  const [sims, setSims] = useState(2000);
  const [rows, setRows] = useState<MctsRow[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const stamp = useRef<string>("");

  // Results belong to the position they were computed for.
  useEffect(() => {
    const now = JSON.stringify(position);
    if (stamp.current && stamp.current !== now) setRows(null);
  }, [position]);

  async function search() {
    setBusy(true);
    setErr(null);
    try {
      const res = await api.mcts(position, { sims });
      stamp.current = JSON.stringify(position);
      setRows(res.rows);
    } catch (e) {
      setErr(String((e as Error).message ?? e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="sl-panel">
      <div className="sl-panel-head">
        <span className="sl-label">Search</span>
      </div>
      <p className="sl-blurb">
        The workbench's MCTS on this board: win probability per first move. Fast, and honest about what it did not see (hidden
        draw order is sampled, not peeked).
      </p>
      <div className="sl-row">
        <label className="sl-field">
          <span>simulations</span>
          <NumberInput min={100} max={20000} step={100} value={sims} onChange={setSims} width={80} ariaLabel="mcts simulations" />
        </label>
        <button type="button" className="sl-primary" onClick={search} disabled={busy || !built}>
          {busy ? "searching…" : "search this position"}
        </button>
      </div>
      {err && <div className="sl-error">{err}</div>}
      {rows && (
        <table className="sl-table">
          <thead>
            <tr>
              <th style={{ textAlign: "left" }}>move</th>
              <th>win %</th>
              <th>q</th>
              <th>visits</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r, i) => (
              <tr key={i} className={r.is_best ? "best" : undefined}>
                <td style={{ textAlign: "left" }}>
                  {r.label}
                  {r.is_best && <span className="sl-best">best</span>}
                  {r.low_confidence && <span className="sl-muted"> · low confidence</span>}
                </td>
                <td>{(r.win_prob * 100).toFixed(1)}</td>
                <td>{r.q.toFixed(3)}</td>
                <td>{r.visits}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
