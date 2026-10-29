import { type MouseEvent as ReactMouseEvent, type ReactNode, useEffect, useMemo, useRef, useState } from "react";
import type { Enemy, MctsConfig, SearchTree as SearchTreeData, SearchTreeNode } from "../api";
import NumberInput from "./NumberInput";
import { enemyLabeller } from "../enemyLabels";

/**
 * Agent search-tree inspector: a modal node-link view of the MCTS search for
 * one combat decision. Nodes are sized by visit count and colored by Q (the
 * player-perspective expected value); edge width tracks child visits; the
 * most-visited path (the engine's principal variation) is highlighted gold.
 *
 * The tree has hundreds of nodes, so it renders lazily: the root is expanded,
 * each node shows its top-K children by visits, and clicking a node expands it.
 * A min-visits filter and the sims selector (re-runs the search) tame density.
 * Selecting a node shows its full stats — N, Q, win/tie/loss, the UCT
 * exploration term U, and MCTS-solver proven-win/loss flags. The engine uses a
 * uniform prior, so there is no learned policy P to show.
 *
 * The tree alternates DECISION nodes (the player picks an action — circles ●) and CHANCE
 * nodes (nature resolves a stochastic transition like a card draw or intent
 * roll — diamonds ◆). A chance node's children ARE its sampled outcomes (each
 * outcome's probability p = child.visits / chance.visits), its value is the
 * expectimax E[V] = Σ p·V (labelled "E", not "Q"), and DPW caps how many
 * outcomes are sampled (k/cap, possibly truncated). The "collapse dice" toggle
 * folds the chance layer so the tree reads like the legacy PIMC move line: a
 * decision connects straight to the modal (most-visited) outcome through its
 * action, honestly labelled with the chance node's E[V]. Older / PIMC trees
 * have no chance nodes (node_kind defaults to "decision").
 */
const COL_GAP = 152;
const ROW_GAP = 58;
const PAD = 34;
const MAX_R = 16;
const SIM_CHOICES = [300, 1000, 3000, 10000, 50000, 150000];
const ROOT_KEY = "r";
// Hard cap on rendered rows: "expand all" on a 150k-sim DAG unfolds every path.
const MAX_ROWS = 5000;

function qColor(q: number): string {
  // q ∈ [-1,1] → red (loss) ⟶ amber ⟶ green (win).
  const t = Math.max(0, Math.min(1, (q + 1) / 2));
  return `hsl(${Math.round(t * 122)} 62% 44%)`;
}

export default function SearchTree({
  tree,
  busy,
  config,
  onRun,
  onClose,
  potionNames,
  enemies,
}: {
  tree: SearchTreeData | null;
  busy?: boolean;
  config: MctsConfig;
  onRun: (config: MctsConfig) => void;
  onClose: () => void;
  // Potion display names by slot, so "Potion s0" edges show the actual potion.
  potionNames?: (string | null | undefined)[];
  // Combatants of the searched position, so a target edge reads "→ Cultist"
  // instead of "→ enemy 0". Same reason as potionNames.
  enemies?: Enemy[];
}) {
  const enemyName = enemyLabeller(enemies);
  // Draft knobs: edited locally, applied (search re-runs) only on "run". Keeps
  // an expensive 150k-sim search from firing on every keystroke.
  const [draft, setDraft] = useState<MctsConfig>(config);
  useEffect(() => setDraft(config), [config]);
  const dirty =
    draft.sims !== config.sims || draft.c_puct !== config.c_puct ||
    draft.depth !== config.depth || draft.rollout_policy !== config.rollout_policy ||
    draft.seed !== config.seed ||
    (draft.dominance_pruning ?? false) !== (config.dominance_pruning ?? false);
  const setK = <K extends keyof MctsConfig>(k: K, v: MctsConfig[K]) =>
    setDraft((d) => ({ ...d, [k]: v }));
  const run = () => onRun(draft);
  // Rows are identified by their PATH key, not their node id: the search graph
  // is a DAG, so one node can appear under several parents (reached by a
  // different action from each) and every appearance is its own row.
  // "expand all" flips to open-unless-closed, so it never enumerates paths.
  const [expanded, setExpanded] = useState<Set<string>>(new Set([ROOT_KEY]));
  const [allOpen, setAllOpen] = useState(false);
  const [closed, setClosed] = useState<Set<string>>(new Set());
  const [selectedKey, setSelectedKey] = useState<string>(ROOT_KEY);
  const [topK, setTopK] = useState(5);
  const [minVisits, setMinVisits] = useState(1);
  const [view, setView] = useState<"outline" | "graph">("outline");
  // Fold the chance layer so a decision connects straight to the modal outcome
  // through its action (PIMC-shaped). Off = full depth (the user's default).
  const [collapseChance, setCollapseChance] = useState(false);
  const [hover, setHover] = useState<{ key: string; x: number; y: number } | null>(null);
  // Zoom: "fit" auto-scales to the viewport (but never below a readable floor,
  // scrolling instead); a number is an explicit user zoom. Default fit.
  const [zoom, setZoom] = useState<number | "fit">("fit");
  // Measure the viewport so "fit" can size the graph.
  const wrapRef = useRef<HTMLDivElement>(null);
  const [box, setBox] = useState({ w: 0, h: 0 });
  useEffect(() => {
    const el = wrapRef.current;
    if (!el) return;
    const measure = () => setBox({ w: el.clientWidth, h: el.clientHeight });
    measure();
    // Prefer ResizeObserver; fall back to window resize (e.g. jsdom in tests).
    if (typeof ResizeObserver !== "undefined") {
      const ro = new ResizeObserver(measure);
      ro.observe(el);
      return () => ro.disconnect();
    }
    window.addEventListener("resize", measure);
    return () => window.removeEventListener("resize", measure);
  }, []);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") { e.preventDefault(); onClose(); }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const byId = useMemo(
    () => new Map((tree?.nodes ?? []).map((n) => [n.id, n])),
    [tree],
  );
  const rootId = tree?.root ?? 0;

  // A node's own type. PIMC / older trees omit node_kind → treat as "decision".
  const nodeKind = (n: SearchTreeNode): "decision" | "chance" => n.node_kind ?? "decision";
  const isChance = (n: SearchTreeNode | undefined): boolean =>
    !!n && nodeKind(n) === "chance";
  // The most-visited child of a node (a chance node's modal outcome).
  function modalChild(n: SearchTreeNode): SearchTreeNode | undefined {
    let best: SearchTreeNode | undefined;
    for (const cid of n.children) {
      const c = byId.get(cid);
      if (c && (!best || c.visits > best.visits)) best = c;
    }
    return best;
  }

  // ── Display tree ────────────────────────────────────────────────────────
  // One central edge resolver routes ALL traversal (outline, graph layout, PV,
  // expand-all / best-line). A display edge keeps two ids apart: `displayId`
  // (the node whose stats we render for this row) and `childId` (whose
  // children we recurse into). They differ only for a FOLDED chance edge.
  // The row is identified by `key` (its path), and its action label and
  // visit count come from `info` — the EDGE — because a transposed node is
  // reached by a different action from each parent, and its own `visits`
  // sums the traffic of all of them.
  type EdgeInfo = {
    kind: string | null;
    label: string | null;
    target_index: number | null;
    outcome_ordinal: number | null;
    visits: number;           // traffic through THIS edge from this parent
  };
  type DispEdge = {
    key: string;              // path key: parent's key + "/" + edge index
    parentId: number;         // the display parent (U term, outcome p)
    childId: number;          // recurse into this node's displayChildren
    displayId: number;        // render this node's stats for the row
    info: EdgeInfo;
    viaChance?: number;       // chance node id folded into this edge (collapse on)
    outcome?: boolean;        // this edge is a chance OUTCOME (probability edge)
  };
  // Edge i of `node`: its serialized `edges[i]`, or — for dumps that predate
  // per-edge data — the child's own incoming-edge fields.
  function edgeInfo(node: SearchTreeNode, i: number): EdgeInfo {
    const e = node.edges?.[i];
    const c = byId.get(node.children[i]);
    if (e) {
      return { kind: e.kind, label: e.label, target_index: e.target_index,
        outcome_ordinal: e.outcome_ordinal ?? null, visits: e.visits };
    }
    return { kind: c?.kind ?? null, label: c?.label ?? null, target_index: c?.target_index ?? null,
      outcome_ordinal: c?.outcome_ordinal ?? null, visits: c?.visits ?? 0 };
  }
  function displayChildren(id: number, parentKey: string): DispEdge[] {
    const node = byId.get(id);
    if (!node) return [];
    const edges: DispEdge[] = [];
    node.children.forEach((cid, i) => {
      const c = byId.get(cid);
      if (!c) return;
      const key = `${parentKey}/${i}`;
      const info = edgeInfo(node, i);
      if (collapseChance && isChance(c)) {
        // Fold the dice: show the chance node (its action + E[V]) but recurse
        // into its modal outcome's children → one child per action, PIMC-shaped.
        const modal = modalChild(c);
        edges.push({ key, parentId: id, childId: modal?.id ?? c.id, displayId: c.id, info, viaChance: c.id });
      } else {
        edges.push({ key, parentId: id, childId: cid, displayId: cid, info, outcome: info.kind === "outcome" });
      }
    });
    return edges;
  }
  const isOpen = (key: string) => (allOpen ? !closed.has(key) : expanded.has(key));

  // Principal variation = most-visited display-edge chain from the root, by
  // the parent's own traffic through each edge.
  const pvSet = useMemo(() => {
    const s = new Set<string>([ROOT_KEY]);
    let recurseId: number = rootId;
    let key = ROOT_KEY;
    const seen = new Set<number>([rootId]);
    for (;;) {
      const edges = displayChildren(recurseId, key);
      if (edges.length === 0) break;
      let best = edges[0];
      for (const e of edges) if (e.info.visits > best.info.visits) best = e;
      if (seen.has(best.childId)) break;
      seen.add(best.childId);
      s.add(best.key);
      recurseId = best.childId;
      key = best.key;
    }
    return s;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [byId, rootId, collapseChance]);

  // Display edges we actually render for an expanded node: edge visits ≥
  // filter, sorted by edge visits desc, capped at topK. + hidden count.
  const shownDisplay = (recurseId: number, parentKey: string): { edges: DispEdge[]; hidden: number } => {
    const kids = displayChildren(recurseId, parentKey)
      .filter((e) => e.info.visits >= minVisits)
      .sort((a, b) => b.info.visits - a.info.visits);
    return { edges: kids.slice(0, topK), hidden: Math.max(0, kids.length - topK) };
  };

  // Every visible row, in outline (DFS) order: the root, then each open row's
  // shown children. Both views and every key lookup (selection, hover) read it.
  type VisRow = { key: string; displayId: number; recurseId: number; edge?: DispEdge; depth: number };
  type OutRow =
    | ({ kind: "node" } & VisRow)
    | { kind: "more"; key: string; parentKey: string; depth: number; count: number };
  const { rows, rowByKey, truncated } = useMemo(() => {
    const out: OutRow[] = [];
    const byKey = new Map<string, VisRow>();
    let cut = false;
    const walk = (r: VisRow) => {
      if (byKey.size >= MAX_ROWS) { cut = true; return; }
      out.push({ kind: "node", ...r });
      byKey.set(r.key, r);
      if (!isOpen(r.key)) return;
      const { edges, hidden } = shownDisplay(r.recurseId, r.key);
      for (const e of edges) {
        walk({ key: e.key, displayId: e.displayId, recurseId: e.childId, edge: e, depth: r.depth + 1 });
      }
      if (hidden > 0) out.push({ kind: "more", key: `more-${r.key}`, parentKey: r.key, depth: r.depth + 1, count: hidden });
    };
    if (byId.has(rootId)) walk({ key: ROOT_KEY, displayId: rootId, recurseId: rootId, depth: 0 });
    return { rows: out, rowByKey: byKey, truncated: cut };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [byId, rootId, expanded, allOpen, closed, topK, minVisits, collapseChance]);

  // Probability of an outcome row = its edge visits / its chance parent's visits.
  function outcomeProb(r: VisRow): number | null {
    const parent = r.edge ? byId.get(r.edge.parentId) : undefined;
    if (!r.edge || !parent || parent.visits <= 0) return null;
    return r.edge.info.visits / parent.visits;
  }
  // DPW sampling badge for a chance node: k sampled of cap, truncated/closed.
  function dpwInfo(n: SearchTreeNode) {
    const k = n.children.length;
    const cap = n.outcome_cap;
    const truncated = cap != null && k >= cap;
    return { k, cap, truncated, closed: !!n.chance_closed };
  }
  // Expectimax outcome spread = [min, max] q over a chance node's outcomes.
  function outcomeSpread(n: SearchTreeNode): { min: number; max: number } | null {
    const qs = n.children
      .map((c) => byId.get(c)?.q)
      .filter((q): q is number => q != null);
    if (qs.length === 0) return null;
    return { min: Math.min(...qs), max: Math.max(...qs) };
  }
  // value label "E" for chance (expectimax), "Q" for a decision node.
  const valueKey = (n: SearchTreeNode) => (isChance(n) ? "E" : "Q");

  // Lay out the visible rows (tidy tree: leaves take columns, parents center).
  // Keyed by row key, so a transposed node gets one position per appearance.
  const built = useMemo(() => {
    if (!tree || !byId.has(rootId)) return null;
    const childMap = new Map<string, string[]>();    // row key → child row keys
    const hiddenMap = new Map<string, number>();
    const depth = new Map<string, number>();
    for (const r of rows) {
      if (r.kind === "more") { hiddenMap.set(r.parentKey, r.count); continue; }
      depth.set(r.key, r.depth);
      childMap.set(r.key, []);
      if (r.edge) childMap.get(r.key.slice(0, r.key.lastIndexOf("/")))?.push(r.key);
    }
    const col = new Map<string, number>();
    let nextLeaf = 0;
    const place = (key: string) => {
      const kids = childMap.get(key) ?? [];
      kids.forEach(place);
      if (kids.length === 0) col.set(key, nextLeaf++);
      else {
        const xs = kids.map((k) => col.get(k)!);
        col.set(key, (Math.min(...xs) + Math.max(...xs)) / 2);
      }
    };
    if (childMap.has(ROOT_KEY)) place(ROOT_KEY);
    const pos = new Map<string, { x: number; y: number }>();
    for (const [key, c] of col) {
      pos.set(key, { x: PAD + c * COL_GAP, y: PAD + (depth.get(key) ?? 0) * ROW_GAP });
    }
    const maxCol = Math.max(0, ...[...col.values()]);
    const maxDepth = Math.max(0, ...[...depth.values()]);
    return {
      pos, childMap, hiddenMap,
      width: PAD + maxCol * COL_GAP + COL_GAP,
      height: PAD * 2 + maxDepth * ROW_GAP,
    };
  }, [tree, byId, rootId, rows]);

  const rootVisits = byId.get(rootId)?.visits ?? 1;
  // A row's visits: the parent's traffic through its edge (the root: its own).
  const rowVisits = (r: VisRow) => r.edge?.info.visits ?? byId.get(r.displayId)?.visits ?? 0;
  const radius = (r: VisRow) =>
    Math.max(4, Math.min(MAX_R, 4 + 13 * Math.sqrt(rowVisits(r) / Math.max(1, rootVisits))));

  // Effective render scale. "fit" auto-scales to the viewport but never below a
  // readable floor (we scroll/pan past that); a number is the user's choice.
  const READABLE_MIN = 0.55;
  const fitRaw = built && box.w && box.h
    ? Math.min(1, (box.w - 12) / built.width, (box.h - 12) / built.height)
    : 1;
  const scale = zoom === "fit" ? Math.max(READABLE_MIN, fitRaw) : zoom;
  const zoomBy = (f: number) =>
    setZoom((z) => Math.round(Math.max(0.3, Math.min(2, (z === "fit" ? scale : z) * f)) * 100) / 100);

  const hasKids = (r: VisRow) => displayChildren(r.recurseId, r.key).length > 0;
  function toggle(r: VisRow) {
    setSelectedKey(r.key);
    if (!hasKids(r)) return;
    const flip = (cur: Set<string>) => {
      const next = new Set(cur);
      if (next.has(r.key)) next.delete(r.key);
      else next.add(r.key);
      return next;
    };
    if (allOpen) setClosed(flip);
    else setExpanded(flip);
  }
  // Expand everything: open-unless-closed, so no path set is enumerated.
  const expandAll = () => { setAllOpen(true); setClosed(new Set()); };
  const collapseAll = () => { setAllOpen(false); setClosed(new Set()); setExpanded(new Set([ROOT_KEY])); };

  // Unfold ONLY the best line (most-traversed edge at each step) from row
  // `from` down `levels` deeper, without expanding any sibling branches.
  function expandBestLine(from: VisRow, levels = 10) {
    const keys: string[] = [];
    let key = from.key;
    let recurseId = from.recurseId;
    for (let i = 0; i < levels; i++) {
      const edges = displayChildren(recurseId, key);
      if (edges.length === 0) break;
      keys.push(key); // reveal this row's children (incl. its best child)
      let best = edges[0];
      for (const e of edges) if (e.info.visits > best.info.visits) best = e;
      key = best.key;
      recurseId = best.childId;
    }
    if (allOpen) setClosed((cur) => new Set([...cur].filter((k) => !keys.includes(k))));
    else setExpanded((cur) => new Set([...cur, ...keys]));
  }

  // Display label for a row: the action on its EDGE (a transposed node is
  // reached by a different action from each parent), with potion slots
  // resolved to the potion's name; a chance node shows the stochastic action
  // it resolves.
  function labelOf(r: VisRow): string {
    const nd = byId.get(r.displayId);
    if (!nd || r.key === ROOT_KEY) return "root";
    if (isChance(nd)) return nd.chance_action ?? r.edge?.info.label ?? "roll";
    const kind = r.edge?.info.kind ?? nd.kind;
    const base = r.edge?.info.label ?? nd.label ?? "?";
    if (kind === "potion" || kind === "drink_potion") {
      const m = (base.match(/s(\d+)/) ?? [])[1];
      const name = m != null ? potionNames?.[Number(m)] : undefined;
      if (name) return name;
    }
    return base;
  }
  const targetOf = (r: VisRow) => r.edge?.info.target_index ?? null;

  const selRow = rowByKey.get(selectedKey) ?? rowByKey.get(ROOT_KEY);
  const sel = selRow ? byId.get(selRow.displayId) : undefined;
  const cPuct = tree?.c_puct ?? Math.SQRT2;
  // UCT exploration term for the selected edge vs. its parent.
  const selU = (() => {
    if (!selRow?.edge) return null;
    const parent = byId.get(selRow.edge.parentId);
    const n = selRow.edge.info.visits;
    if (!parent || n === 0) return null;
    return cPuct * Math.sqrt(Math.log(Math.max(2, parent.visits)) / n);
  })();

  // Human-readable explanation of every stat on a node, for the hover card.
  function tooltipContent(r: VisRow) {
    const n = byId.get(r.displayId);
    if (!n) return null;
    const visits = rowVisits(r);
    const sharePct = Math.round((visits / Math.max(1, rootVisits)) * 100);
    const tgt = targetOf(r);
    const sm = n.summary;
    const Row = ({ k, v, hint }: { k: string; v: ReactNode; hint?: string }) => (
      <div style={{ display: "flex", gap: 8, justifyContent: "space-between" }}>
        <span style={{ color: "var(--muted)" }}>{k}</span>
        <span style={{ textAlign: "right" }}>{v}{hint && <span style={{ color: "var(--muted)" }}> {hint}</span>}</span>
      </div>
    );
    const chance = isChance(n);
    const spread = chance ? outcomeSpread(n) : null;
    const dpw = chance ? dpwInfo(n) : null;
    const prob = r.edge?.outcome ? outcomeProb(r) : null; // p for an outcome child
    const fmt = (q: number) => `${q >= 0 ? "+" : ""}${q.toFixed(3)}`;
    return (
      <div style={{ display: "flex", flexDirection: "column", gap: 3 }}>
        <div style={{ fontWeight: 700, marginBottom: 2 }}>
          {chance && <span aria-hidden>🎲 </span>}
          {r.key === ROOT_KEY ? "Current position (root)" : labelOf(r)}
          {tgt != null && <span style={{ color: "var(--muted)" }}> → {enemyName(tgt)}</span>}
        </div>
        {chance && <div style={{ color: "var(--muted)" }}>chance node — nature resolves a roll</div>}
        <Row k="Visits" v={visits} hint={`(${sharePct}% of search)`} />
        {visits !== n.visits && (
          <Row k="Visits, all paths" v={n.visits}
            hint="this position is also reached by another move order; Q and W/T/L pool every path" />
        )}
        {chance ? (
          <Row k="Expectimax E[V]" v={fmt(n.q)} hint="Σ p·V over outcomes" />
        ) : (
          <Row k="Win value (Q)" v={fmt(n.q)} hint="−1 loss … +1 win" />
        )}
        {prob != null && <Row k="Outcome prob p" v={`.${Math.round(prob * 100).toString().padStart(2, "0")}`} hint="visits / chance visits" />}
        {spread && <Row k="Outcome spread" v={`${fmt(spread.min)} … ${fmt(spread.max)}`} hint="min … max V" />}
        {dpw && <Row k="Outcomes" v={`${dpw.k} / ${dpw.cap ?? "?"}`} hint={dpw.closed ? "closed" : dpw.truncated ? "truncated" : "(DPW cap)"} />}
        {dpw && (
          <div style={{ color: "var(--muted)" }}>
            Sampled {dpw.k} of ≥{dpw.k} outcomes{dpw.cap != null && ` (DPW cap ${dpw.cap})`}
            {dpw.closed ? "; distribution closed" : dpw.truncated ? "; distribution truncated" : ""}
            {n.chance_draws != null && ` · ${n.chance_draws} draws`}
          </div>
        )}
        <Row k="Playouts W/T/L" v={`${n.wins} / ${n.ties} / ${n.losses}`} />
        {n.proven_win && <div style={{ color: "#3ad17a" }}>✓ Forced win (solved)</div>}
        {n.proven_loss && <div style={{ color: "#e0566f" }}>✗ Forced loss</div>}
        {n.is_terminal && <div style={{ color: "var(--muted)" }}>Combat ends here</div>}
        {sm && (
          <>
            <div style={{ borderTop: "1px solid var(--border)", margin: "4px 0 2px", paddingTop: 4, color: "var(--muted)" }}>
              {r.key === ROOT_KEY ? "Current state:" : "After this action:"}
            </div>
            <Row k="Player HP" v={`${sm.player_hp} / ${sm.player_max_hp}`} />
            {sm.player_block > 0 && <Row k="Block" v={sm.player_block} />}
            <Row k="Energy" v={sm.energy} />
            <Row k="Round" v={sm.round} />
            {sm.enemies.map((e, i) => (
              <Row key={e.uid} k={`Enemy ${i} HP`} v={`${e.hp} / ${e.max_hp}`} />
            ))}
          </>
        )}
      </div>
    );
  }
  const hoverHandlers = (key: string) => ({
    onMouseEnter: (e: ReactMouseEvent) => setHover({ key, x: e.clientX, y: e.clientY }),
    onMouseMove: (e: ReactMouseEvent) => setHover({ key, x: e.clientX, y: e.clientY }),
    onMouseLeave: () => setHover((h) => (h?.key === key ? null : h)),
  });
  const hoverRow = hover ? rowByKey.get(hover.key) : undefined;

  return (
    <div role="dialog" aria-label="Search tree" onClick={onClose}
      style={{ position: "fixed", inset: 0, zIndex: 60, background: "oklch(20% 0.02 255 / .55)",
        display: "flex", alignItems: "center", justifyContent: "center", padding: 24 }}>
      <div onClick={(e) => e.stopPropagation()}
        style={{ background: "var(--bg)", border: "1px solid var(--border)", borderRadius: 10,
          width: "min(1500px, 97vw)", height: "94vh", display: "flex", flexDirection: "column",
          boxShadow: "0 12px 40px oklch(30% 0.04 255 / .4)" }}>
        {/* header / controls */}
        <div className="mono" style={{ display: "flex", alignItems: "center", gap: 14, flexWrap: "wrap",
          padding: "10px 16px", borderBottom: "1px solid var(--border)", fontSize: 12 }}>
          <b>MCTS search tree</b>
          <span style={{ display: "inline-flex", border: "1px solid var(--border)", borderRadius: 4, overflow: "hidden" }}>
            {(["outline", "graph"] as const).map((v) => (
              <button key={v} onClick={() => setView(v)}
                style={{ padding: "2px 9px", border: "none", cursor: "pointer", font: "inherit",
                  background: view === v ? "var(--accent, #6aa0ff)" : "transparent",
                  color: view === v ? "#fff" : "var(--text)" }}>{v}</button>
            ))}
          </span>
          <label title="number of MCTS playouts — more = stronger but slower">sims{" "}
            <select value={draft.sims} disabled={busy} onChange={(e) => setK("sims", Number(e.target.value))}>
              {SIM_CHOICES.map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
          </label>
          <label title="UCT exploration constant — higher explores more, lower exploits the best line">explore{" "}
            <NumberInput min={0} step={0.1} value={draft.c_puct} disabled={busy} width={68}
              onKeyDown={(e) => e.key === "Enter" && dirty && run()}
              onChange={(v) => setK("c_puct", v)} />
          </label>
          <label title="rollout simulation depth in plies — how far each random playout looks ahead">depth{" "}
            <NumberInput min={1} max={500} value={draft.depth} disabled={busy} width={72}
              onKeyDown={(e) => e.key === "Enter" && dirty && run()}
              onChange={(v) => setK("depth", v)} />
          </label>
          <label title="rollout policy — heuristic (expert priorities) or uniform (unbiased random)">rollout{" "}
            <select value={draft.rollout_policy} disabled={busy}
              onChange={(e) => setK("rollout_policy", e.target.value as MctsConfig["rollout_policy"])}>
              <option value="heuristic">heuristic</option>
              <option value="uniform">uniform</option>
            </select>
          </label>
          <label title="RNG seed — fixes the determinization draw; change to re-roll">seed{" "}
            <NumberInput min={0} value={draft.seed} disabled={busy} width={72}
              onKeyDown={(e) => e.key === "Enter" && dirty && run()}
              onChange={(v) => setK("seed", v)} />
          </label>
          <label title="prune move orders the engine proves dominated — e.g. never search Strike-then-Beam-Cell when Beam-Cell-then-Strike is the same turn with more damage">
            <input type="checkbox" checked={draft.dominance_pruning ?? false} disabled={busy}
              onChange={(e) => setK("dominance_pruning", e.target.checked)} />{" "}prune
          </label>
          <button onClick={() => setK("seed", draft.seed + 1)} disabled={busy} title="re-roll: bump the seed"
            style={{ padding: "2px 8px", border: "1px solid var(--border)", borderRadius: 4, cursor: "pointer" }}>🎲</button>
          <button onClick={run} disabled={busy} title="run the search with these parameters"
            style={{ padding: "2px 12px", borderRadius: 4, cursor: busy ? "wait" : "pointer", fontWeight: 600,
              border: `1px solid ${dirty ? "var(--accent, #6aa0ff)" : "var(--border)"}`,
              background: dirty ? "var(--accent, #6aa0ff)" : "transparent",
              color: dirty ? "#fff" : "var(--text)" }}>
            {busy ? "running…" : dirty ? "run ▶" : "rerun"}
          </button>
          <label>top-K{" "}
            <NumberInput min={1} max={12} value={topK} width={60}
              onChange={(v) => setTopK(v)} />
          </label>
          <label>min visits{" "}
            <NumberInput min={1} value={minVisits} width={68}
              onChange={(v) => setMinVisits(v)} />
          </label>
          <span style={{ display: "inline-flex", gap: 4 }}>
            <button onClick={() => selRow && expandBestLine(selRow, 10)}
              title="unfold only the best (most-visited) line from the selected node, 10 levels deep"
              style={{ padding: "2px 8px", border: "1px solid var(--border)", borderRadius: 4, cursor: "pointer" }}>best +10</button>
            <button onClick={expandAll} title="expand every node"
              style={{ padding: "2px 8px", border: "1px solid var(--border)", borderRadius: 4, cursor: "pointer" }}>expand all</button>
            <button onClick={collapseAll} title="collapse to the root"
              style={{ padding: "2px 8px", border: "1px solid var(--border)", borderRadius: 4, cursor: "pointer" }}>collapse</button>
            <button onClick={() => setCollapseChance((v) => !v)}
              title="fold the chance layer so a decision connects straight to its modal outcome through its action — the tree then reads like the legacy PIMC move line (value stays expectimax E[V])"
              style={{ padding: "2px 8px", borderRadius: 4, cursor: "pointer",
                border: `1px solid ${collapseChance ? "var(--accent, #6aa0ff)" : "var(--border)"}`,
                background: collapseChance ? "var(--accent, #6aa0ff)" : "transparent",
                color: collapseChance ? "#fff" : "var(--text)" }}>🎲 collapse dice</button>
          </span>
          {view === "graph" && (
            <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
              <button onClick={() => zoomBy(1 / 1.25)} aria-label="zoom out" title="zoom out"
                style={{ padding: "2px 8px", border: "1px solid var(--border)", borderRadius: 4, cursor: "pointer" }}>−</button>
              <span style={{ width: 38, textAlign: "center", color: "var(--muted)" }}>{Math.round(scale * 100)}%</span>
              <button onClick={() => zoomBy(1.25)} aria-label="zoom in" title="zoom in"
                style={{ padding: "2px 8px", border: "1px solid var(--border)", borderRadius: 4, cursor: "pointer" }}>＋</button>
              <button onClick={() => setZoom("fit")} title="fit the whole tree in view"
                style={{ padding: "2px 8px", border: `1px solid ${zoom === "fit" ? "var(--accent, #6aa0ff)" : "var(--border)"}`,
                  borderRadius: 4, cursor: "pointer" }}>fit</button>
            </span>
          )}
          <span style={{ color: "var(--muted)", marginLeft: "auto" }}>
            ●decision ◆chance · color ∝ value · gold = most-visited line · click to expand
          </span>
          <button onClick={onClose} aria-label="Close"
            style={{ padding: "4px 10px", border: "1px solid var(--border)", borderRadius: 4, cursor: "pointer" }}>
            close (Esc)
          </button>
        </div>

        {/* graph */}
        <div ref={wrapRef} style={{ overflow: "auto", flex: 1, position: "relative", opacity: busy ? 0.5 : 1 }}>
          {tree?.error ? (
            <div className="mono" style={{ padding: 24, color: "var(--hp)" }}>⚠ {tree.error}</div>
          ) : !built || !tree || tree.nodes.length === 0 ? (
            <div className="mono" style={{ padding: 24, color: "var(--muted)" }}>
              {busy ? "searching…" : "no tree"}
            </div>
          ) : view === "outline" ? (
            <div className="mono" style={{ fontSize: 12 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8, padding: "3px 12px 3px 4px",
                position: "sticky", top: 0, zIndex: 1, background: "var(--bg)",
                borderBottom: "1px solid var(--border)", color: "var(--muted)", fontSize: 10,
                textTransform: "uppercase", letterSpacing: 0.4 }}>
                <span style={{ width: 12, flexShrink: 0 }} />
                <span style={{ flex: 1 }}>action</span>
                <span style={{ width: 70, textAlign: "right" }} title="how many times the search visited this node (more = more confidence)">visits</span>
                <span style={{ width: 64, textAlign: "right" }} title="expected value Q: +1 = sure win, 0 = even, −1 = sure loss">Q</span>
                <span style={{ width: 90 }} title="this node's share of all search visits">search&nbsp;%</span>
                <span style={{ width: 188 }} title="outcome after this action — ♥ player HP (+block) · ⚡ energy · r = round number · then each enemy's HP">outcome</span>
              </div>
              {truncated && (
                <div style={{ padding: "3px 12px", color: "var(--muted)", fontSize: 11 }}>
                  showing the first {MAX_ROWS} rows — collapse or raise min visits to see the rest
                </div>
              )}
              {rows.map((r) =>
                r.kind === "more" ? (
                  <div key={r.key} style={{ paddingLeft: r.depth * 16 + 26, color: "var(--muted)", fontSize: 11, padding: "1px 0" }}>
                    ＋{r.count} more (raise top-K)
                  </div>
                ) : (() => {
                  const n = byId.get(r.displayId)!;
                  const isSel = r.key === selRow?.key;
                  const onPv = pvSet.has(r.key);
                  const kids = hasKids(r);
                  const open = isOpen(r.key);
                  const visits = rowVisits(r);
                  const share = Math.round((visits / Math.max(1, rootVisits)) * 100);
                  const chance = isChance(n);
                  const outcome = !!r.edge?.outcome;
                  const dpw = chance ? dpwInfo(n) : null;
                  const prob = outcome ? outcomeProb(r) : null;
                  const tgt = targetOf(r);
                  return (
                    <div key={r.key} onClick={() => toggle(r)} {...hoverHandlers(r.key)}
                      style={{ display: "flex", alignItems: "center", gap: 8, cursor: "pointer",
                        paddingLeft: r.depth * 16 + 4, paddingRight: 12, height: 22,
                        borderLeft: `2px solid ${onPv ? "var(--accent, #d9a441)" : "transparent"}`,
                        fontStyle: chance || outcome ? "italic" : "normal",
                        background: isSel ? "var(--panel)" : "transparent" }}>
                      <span aria-hidden style={{ width: 12, color: "var(--muted)", flexShrink: 0 }}>
                        {kids ? (open ? "▾" : "▸") : ""}
                      </span>
                      <span style={{ flex: 1, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap",
                        color: chance || outcome ? "var(--muted)" : undefined,
                        fontWeight: isSel ? 700 : onPv ? 600 : 400 }}>
                        {outcome ? (
                          <>
                            <span aria-hidden>⤳ </span>
                            p={prob != null ? `.${Math.round(prob * 100).toString().padStart(2, "0")}` : "?"}
                            {r.edge?.info.outcome_ordinal != null && <span> ·#{r.edge.info.outcome_ordinal}</span>}
                          </>
                        ) : (
                          <>
                            {chance && <span aria-hidden>🎲 </span>}
                            {labelOf(r)}
                            {r.edge?.viaChance != null && <span aria-hidden title="a chance roll was folded into this action"> 🎲</span>}
                            {dpw && (
                              <span style={{ color: "var(--muted)", fontStyle: "normal" }}> {dpw.k}/{dpw.cap ?? "?"}
                                {dpw.closed ? " closed" : dpw.truncated ? " trunc" : ""}</span>
                            )}
                            {tgt != null && <span style={{ color: "var(--muted)" }}> → {enemyName(tgt)}</span>}
                            {n.proven_win && <span style={{ color: "#3ad17a" }}> ✓</span>}
                            {n.proven_loss && <span style={{ color: "#e0566f" }}> ✗</span>}
                          </>
                        )}
                      </span>
                      <span style={{ width: 70, textAlign: "right", color: "var(--muted)", flexShrink: 0 }}
                        title={visits !== n.visits ? `${visits} via this move · ${n.visits} over every move order reaching this position` : undefined}>
                        N {visits}{visits !== n.visits ? "*" : ""}</span>
                      <span style={{ width: 64, textAlign: "right", color: qColor(n.q), flexShrink: 0 }}>
                        {chance && <span style={{ color: "var(--muted)" }}>E</span>}{chance ? " " : ""}{n.q >= 0 ? "+" : ""}{n.q.toFixed(2)}
                      </span>
                      <span title={`${share}% of root visits`} style={{ width: 90, height: 7, flexShrink: 0,
                        background: "var(--border)", borderRadius: 3, overflow: "hidden" }}>
                        <span style={{ display: "block", height: "100%", width: `${share}%`, background: qColor(n.q) }} />
                      </span>
                      {n.summary && (
                        <span style={{ width: 188, flexShrink: 0, color: "var(--muted)", fontSize: 11,
                          overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                          ♥{n.summary.player_hp}{n.summary.player_block > 0 ? `+${n.summary.player_block}` : ""}
                          {" ⚡"}{n.summary.energy} r{n.summary.round}
                          {n.summary.enemies.length > 0 && ` · ${n.summary.enemies.map((e) => e.hp).join("·")}`}
                        </span>
                      )}
                    </div>
                  );
                })(),
              )}
            </div>
          ) : (
            // The footprint wrapper takes the post-scale size so the scroll
            // area is correct; the inner layer is scaled via transform.
            <div style={{ width: built.width * scale, height: built.height * scale }}>
            <div style={{ position: "relative", width: built.width, height: built.height,
              transform: `scale(${scale})`, transformOrigin: "top left" }}>
              <svg width={built.width} height={built.height}
                style={{ position: "absolute", inset: 0 }}>
                {/* edges */}
                {[...built.childMap.entries()].flatMap(([pk, kids]) => {
                  const p = built.pos.get(pk);
                  const pr = rowByKey.get(pk);
                  if (!p || !pr) return [];
                  const parentVisits = Math.max(1, rowVisits(pr));
                  return kids.map((ck) => {
                    const c = built.pos.get(ck);
                    const cr = rowByKey.get(ck);
                    if (!c || !cr) return null;
                    const onPv = pvSet.has(pk) && pvSet.has(ck);
                    const w = 1 + 5 * Math.min(1, rowVisits(cr) / parentVisits);
                    const my = (p.y + c.y) / 2;
                    return (
                      <path key={`${pk}>${ck}`} d={`M ${p.x} ${p.y} C ${p.x} ${my}, ${c.x} ${my}, ${c.x} ${c.y}`}
                        fill="none" stroke={onPv ? "var(--accent, #d9a441)" : "var(--border)"}
                        strokeWidth={onPv ? Math.max(2, w) : w} strokeOpacity={onPv ? 1 : 0.7} />
                    );
                  });
                })}
                {/* nodes: decisions are circles ●, chance nodes diamonds ◆ */}
                {[...built.pos.entries()].map(([key, p]) => {
                  const r = rowByKey.get(key);
                  const n = r ? byId.get(r.displayId) : undefined;
                  if (!r || !n) return null;
                  const onPv = pvSet.has(key);
                  const isSel = key === selRow?.key;
                  const ring = n.proven_win ? "#3ad17a" : n.proven_loss ? "#e0566f" : onPv ? "var(--accent, #d9a441)" : "var(--border)";
                  const sw = isSel ? 3 : onPv || n.proven_win || n.proven_loss ? 2 : 1;
                  const label = labelOf(r);
                  const common = {
                    fill: qColor(n.q), stroke: isSel ? "var(--text)" : ring, strokeWidth: sw,
                    style: { cursor: busy ? "wait" : ("pointer" as const) },
                    onClick: () => toggle(r), ...hoverHandlers(key),
                  };
                  if (isChance(n)) {
                    // A diamond = the same visit-sized radius rotated 45°.
                    const rad = radius(r);
                    const h = rad / Math.SQRT2; // half-side so corners reach rad
                    return (
                      <rect key={key} x={p.x - h} y={p.y - h} width={2 * h} height={2 * h}
                        transform={`rotate(45 ${p.x} ${p.y})`} aria-label={label} {...common} />
                    );
                  }
                  return (
                    <circle key={key} cx={p.x} cy={p.y} r={radius(r)} aria-label={label} {...common} />
                  );
                })}
              </svg>
              {/* labels (non-interactive; clicks go to the circles) */}
              {[...built.pos.entries()].map(([key, p]) => {
                const r = rowByKey.get(key);
                const n = r ? byId.get(r.displayId) : undefined;
                if (!r || !n) return null;
                const hidden = built.hiddenMap.get(key) ?? 0;
                const collapsedKids = !isOpen(key) && hasKids(r);
                const chance = isChance(n);
                const edge = r.edge;
                const outcome = !!edge?.outcome;
                const prob = outcome ? outcomeProb(r) : null;
                const labelText = outcome
                  ? `⤳ p=${prob != null ? `.${Math.round(prob * 100).toString().padStart(2, "0")}` : "?"}`
                  : chance ? `🎲 ${labelOf(r)}` : labelOf(r);
                return (
                  <div key={key} className="mono" style={{ position: "absolute",
                    left: p.x + MAX_R + 5, top: p.y, transform: "translateY(-50%)",
                    pointerEvents: "none", lineHeight: 1.15,
                    fontStyle: chance || outcome ? "italic" : "normal",
                    color: chance || outcome ? "var(--muted)" : undefined,
                    // Cap to the column so long names can't bleed into the next one.
                    maxWidth: COL_GAP - MAX_R - 12,
                    fontWeight: key === selRow?.key ? 700 : pvSet.has(key) ? 600 : 400 }}>
                    <div style={{ fontSize: 11, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                      {labelText}
                      {edge?.viaChance != null && <span aria-hidden> 🎲</span>}
                      {collapsedKids && <span style={{ color: "var(--muted)" }}> ▸</span>}
                    </div>
                    <div style={{ fontSize: 9.5, color: "var(--muted)", whiteSpace: "nowrap" }}>
                      N={rowVisits(r)} {chance ? "E" : "Q"}={n.q.toFixed(2)}
                      {hidden > 0 && <span> ＋{hidden}</span>}
                    </div>
                  </div>
                );
              })}
            </div>
            </div>
          )}
        </div>

        {/* detail strip */}
        {sel && !tree?.error && (
          <div className="mono" style={{ display: "flex", gap: 16, flexWrap: "wrap",
            padding: "8px 16px", borderTop: "1px solid var(--border)", fontSize: 11.5, background: "var(--panel)" }}>
            <b>{isChance(sel) && <span aria-hidden>🎲 </span>}{selRow && labelOf(selRow)}</b>
            {selRow && targetOf(selRow) != null && <span style={{ color: "var(--muted)" }}>→ {enemyName(targetOf(selRow)!)}</span>}
            <span>N <b>{selRow ? rowVisits(selRow) : sel.visits}</b>
              {selRow && rowVisits(selRow) !== sel.visits && (
                <span style={{ color: "var(--muted)" }} title="this position is also reached by another move order; Q and W/T/L pool every path"> ({sel.visits} all paths)</span>
              )}</span>
            <span>{valueKey(sel)} <b style={{ color: qColor(sel.q) }}>{sel.q.toFixed(3)}</b></span>
            {isChance(sel) && (() => {
              const sp = outcomeSpread(sel);
              const dpw = dpwInfo(sel);
              return (
                <>
                  {sp && <span style={{ color: "var(--muted)" }} title="min … max V over sampled outcomes">spread {sp.min >= 0 ? "+" : ""}{sp.min.toFixed(2)} … {sp.max >= 0 ? "+" : ""}{sp.max.toFixed(2)}</span>}
                  <span style={{ color: "var(--muted)" }} title={`sampled ${dpw.k} of ≥${dpw.k} outcomes (DPW cap ${dpw.cap ?? "?"})${dpw.closed ? "; closed" : dpw.truncated ? "; truncated" : ""}`}>
                    {dpw.k}/{dpw.cap ?? "?"} outcomes{dpw.closed ? " · closed" : dpw.truncated ? " · truncated" : ""}
                  </span>
                </>
              );
            })()}
            <span style={{ color: "var(--muted)" }}>W/T/L {sel.wins}/{sel.ties}/{sel.losses}</span>
            {selU != null && <span style={{ color: "var(--muted)" }}>U {selU.toFixed(3)}</span>}
            <span style={{ color: "var(--muted)" }}>children {sel.children.length}</span>
            {sel.proven_win && <span style={{ color: "#3ad17a" }}>proven win</span>}
            {sel.proven_loss && <span style={{ color: "#e0566f" }}>proven loss</span>}
            {sel.is_terminal && <span style={{ color: "var(--muted)" }}>terminal</span>}
            {sel.summary && (
              <span style={{ marginLeft: "auto", display: "inline-flex", gap: 12, alignItems: "center" }}>
                <span title="player HP (+ block)">
                  hp <b style={{ color: "var(--hp)" }}>{sel.summary.player_hp}/{sel.summary.player_max_hp}</b>
                  {sel.summary.player_block > 0 && <span style={{ color: "var(--muted)" }}> +{sel.summary.player_block} blk</span>}
                </span>
                <span style={{ color: "var(--muted)" }}>energy {sel.summary.energy}</span>
                <span style={{ color: "var(--muted)" }}>round {sel.summary.round}</span>
                {sel.summary.enemies.length > 0 && (
                  <span style={{ color: "var(--muted)" }}>
                    enemies {sel.summary.enemies.map((e) => `${e.hp}/${e.max_hp}`).join(", ")}
                  </span>
                )}
              </span>
            )}
          </div>
        )}
      </div>

      {/* Rich hover card: everything about the hovered node in plain English. */}
      {hover && hoverRow && (
        <div className="mono" style={{ position: "fixed", zIndex: 70, pointerEvents: "none",
          left: Math.min(hover.x + 14, window.innerWidth - 250),
          top: Math.min(hover.y + 14, window.innerHeight - 230),
          width: 232, padding: "8px 10px", fontSize: 11, lineHeight: 1.45,
          background: "var(--bg)", border: "1px solid var(--border)", borderRadius: 6,
          boxShadow: "0 8px 24px oklch(20% 0.03 255 / .5)" }}>
          {tooltipContent(hoverRow)}
        </div>
      )}
    </div>
  );
}
