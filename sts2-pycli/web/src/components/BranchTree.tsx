import { useEffect, useMemo } from "react";
import type { LiveTimeline } from "../api";

/**
 * A modal node-link view of the live run's branch tree. Rows are decision
 * depth (root at top); forks fan out into columns. Every node is clickable to
 * rewind (seek) the run to that point; the current position is marked. Edges
 * are drawn in an SVG layer beneath positioned node buttons (so labels get
 * cheap CSS truncation and native click handling). Closes on backdrop or Esc.
 *
 * Layout is a simple tidy-tree pass: leaves take successive columns, parents
 * center over their children. Good for the mostly-linear-with-occasional-forks
 * trees these runs produce.
 */
const COL_GAP = 210;
const ROW_GAP = 34;
const PAD = 28;
const DOT = 5; // dot radius

type Pos = { x: number; y: number };

function layout(timeline: LiveTimeline): {
  byId: Map<number, LiveTimeline["nodes"][number]>;
  pos: Map<number, Pos>;
  width: number;
  height: number;
} | null {
  const byId = new Map(timeline.nodes.map((n) => [n.id, n]));
  const root = timeline.nodes.find((n) => n.parent_id == null);
  if (!root) return null;

  const col = new Map<number, number>();
  let nextLeaf = 0;
  let maxDepth = 0;
  // Iterative post-order so deep runs can't blow the call stack.
  const stack: { id: number; depth: number; phase: 0 | 1 }[] = [{ id: root.id, depth: 0, phase: 0 }];
  while (stack.length) {
    const frame = stack[stack.length - 1];
    const node = byId.get(frame.id)!;
    const kids = node.child_ids.filter((c) => byId.has(c));
    maxDepth = Math.max(maxDepth, frame.depth);
    if (frame.phase === 0) {
      frame.phase = 1;
      // Push children to visit first (reverse so left-most resolves first).
      for (let i = kids.length - 1; i >= 0; i--) stack.push({ id: kids[i], depth: frame.depth + 1, phase: 0 });
      continue;
    }
    stack.pop();
    if (kids.length === 0) {
      col.set(frame.id, nextLeaf++);
    } else {
      const xs = kids.map((k) => col.get(k)!);
      col.set(frame.id, (Math.min(...xs) + Math.max(...xs)) / 2);
    }
  }

  const pos = new Map<number, Pos>();
  const depthOf = new Map<number, number>([[root.id, 0]]);
  // BFS to record each node's depth for y placement.
  const q = [root.id];
  while (q.length) {
    const id = q.shift()!;
    const d = depthOf.get(id)!;
    for (const c of byId.get(id)!.child_ids) {
      if (byId.has(c)) {
        depthOf.set(c, d + 1);
        q.push(c);
      }
    }
  }
  for (const n of timeline.nodes) {
    const c = col.get(n.id);
    const d = depthOf.get(n.id);
    if (c == null || d == null) continue;
    pos.set(n.id, { x: PAD + c * COL_GAP, y: PAD + d * ROW_GAP });
  }

  const maxCol = Math.max(0, ...[...col.values()]);
  return {
    byId,
    pos,
    width: PAD + maxCol * COL_GAP + COL_GAP, // trailing column for labels
    height: PAD * 2 + maxDepth * ROW_GAP,
  };
}

export default function BranchTree({
  timeline,
  onSeek,
  onClose,
  busy,
}: {
  timeline: LiveTimeline | null;
  onSeek: (nodeId: number) => void;
  onClose: () => void;
  busy?: boolean;
}) {
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") { e.preventDefault(); onClose(); }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const built = useMemo(() => (timeline ? layout(timeline) : null), [timeline]);
  if (!timeline || !built) return null;
  const activePath = new Set(timeline.active_path);
  const leaf = timeline.active_leaf;
  const accent = "var(--accent, #6aa0ff)";

  return (
    <div role="dialog" aria-label="Branch tree" onClick={onClose}
      style={{ position: "fixed", inset: 0, zIndex: 60, background: "oklch(20% 0.02 255 / .55)",
        display: "flex", alignItems: "center", justifyContent: "center", padding: 24 }}>
      <div onClick={(e) => e.stopPropagation()}
        style={{ background: "var(--bg)", border: "1px solid var(--border)", borderRadius: 10,
          maxWidth: "min(1100px, 94vw)", maxHeight: "88vh", display: "flex", flexDirection: "column",
          boxShadow: "0 12px 40px oklch(30% 0.04 255 / .4)" }}>
        <div className="mono" style={{ display: "flex", alignItems: "center", justifyContent: "space-between",
          gap: 12, padding: "12px 16px", borderBottom: "1px solid var(--border)" }}>
          <b>Branch tree</b>
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <span style={{ color: "var(--muted)", fontSize: 11 }}>click a node to rewind</span>
            <button onClick={onClose} aria-label="Close"
              style={{ padding: "4px 10px", border: "1px solid var(--border)", borderRadius: 4, cursor: "pointer" }}>
              close (Esc)
            </button>
          </div>
        </div>

        <div style={{ overflow: "auto", padding: 8 }}>
          <div style={{ position: "relative", width: built.width, height: built.height }}>
            <svg width={built.width} height={built.height}
              style={{ position: "absolute", inset: 0, pointerEvents: "none" }}>
              {timeline.nodes.map((n) => {
                if (n.parent_id == null) return null;
                const p = built.pos.get(n.parent_id);
                const c = built.pos.get(n.id);
                if (!p || !c) return null;
                const onActive = activePath.has(n.id) && activePath.has(n.parent_id);
                const x1 = p.x + DOT, y1 = p.y, x2 = c.x + DOT, y2 = c.y;
                const my = (y1 + y2) / 2;
                return (
                  <path key={n.id} d={`M ${x1} ${y1} C ${x1} ${my}, ${x2} ${my}, ${x2} ${y2}`}
                    fill="none" stroke={onActive ? accent : "var(--border)"}
                    strokeWidth={onActive ? 2 : 1.5} />
                );
              })}
            </svg>

            {timeline.nodes.map((n) => {
              const p = built.pos.get(n.id);
              if (!p) return null;
              const isLeaf = n.id === leaf;
              const onActive = activePath.has(n.id);
              // Live-GAME trees mark nodes without their own save point as
              // non-seekable (the game only saves at room boundaries);
              // clicking one still works — the seek handler resolves the
              // nearest earlier save point. Workbench-local sessions never
              // set the flag and seek exactly.
              const canSeek = n.seekable !== false;
              return (
                <button key={n.id} type="button" disabled={busy}
                  onClick={() => onSeek(n.id)}
                  aria-current={isLeaf ? "step" : undefined}
                  title={!canSeek
                    ? `${n.label} — rewinds to the nearest earlier save point (the game only saves at room entry / post-combat)`
                    : isLeaf ? `current position — ${n.label}` : `rewind to: ${n.label}`}
                  style={{
                    position: "absolute", left: p.x, top: p.y, transform: "translateY(-50%)",
                    display: "flex", alignItems: "center", gap: 7, maxWidth: COL_GAP - 12,
                    padding: "1px 6px 1px 0", border: "none", background: "transparent",
                    cursor: busy ? "wait" : canSeek ? "pointer" : "default",
                    opacity: canSeek ? 1 : 0.55, font: "inherit", fontSize: 12,
                    color: n.error ? "var(--hp)" : onActive ? "var(--text)" : "var(--muted)",
                    fontWeight: isLeaf ? 700 : onActive ? 550 : 400,
                  }}>
                  <span aria-hidden style={{ flexShrink: 0, width: DOT * 2, height: DOT * 2, borderRadius: "50%",
                    background: isLeaf ? accent : onActive ? "var(--text)" : "var(--bg)",
                    border: `1.5px solid ${onActive ? accent : "var(--border)"}`, boxSizing: "border-box" }} />
                  <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                    {n.error ? `⚠ ${n.label}` : n.label}
                  </span>
                  {isLeaf && <span style={{ flexShrink: 0, fontSize: 9.5, color: "var(--muted)" }}>now</span>}
                </button>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
