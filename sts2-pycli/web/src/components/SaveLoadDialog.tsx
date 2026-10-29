import { useEffect, useMemo, useState } from "react";
import type { SaveMeta } from "../api";

type SortKey = "name" | "saved_at" | "character" | "seed" | "step_count";

/** Format a UTC ISO timestamp as a compact local date-time for the list. */
function fmtDate(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString(undefined, {
    year: "numeric", month: "short", day: "numeric", hour: "2-digit", minute: "2-digit",
  });
}

function fmtProgress(p: SaveMeta["progress"]): string {
  const bits: string[] = [];
  if (p.act != null) bits.push(`Act ${p.act}`);
  if (p.floor != null) bits.push(`Floor ${p.floor}`);
  if (p.hp != null) bits.push(`${p.hp}${p.max_hp != null ? `/${p.max_hp}` : ""} HP`);
  return bits.join(", ") || "—";
}

/**
 * Modal listing named saves in a sortable table (Name, Date, Character,
 * Progress, Seed, Steps). Clicking a row loads that save. Default sort is by
 * date, newest first. Closes on backdrop click or Escape.
 *
 * `loading` is the name of the save currently being restored, or null. A
 * restore replays the entire saved line through the engine, so a long run
 * takes tens of seconds; the dialog stays up and says so, and no row is
 * clickable meanwhile — a second restore cannot cancel the first, it only
 * competes with it for CPU.
 */
export default function SaveLoadDialog({
  saves,
  loading = null,
  onLoad,
  onClose,
}: {
  saves: SaveMeta[];
  loading?: string | null;
  onLoad: (name: string) => void;
  onClose: () => void;
}) {
  const busy = loading !== null;
  const [sortKey, setSortKey] = useState<SortKey>("saved_at");
  const [asc, setAsc] = useState(false); // newest/Z-first by default for dates

  // Escape (and the backdrop, and the close button) are inert mid-restore:
  // dismissing would take away the only sign that anything is happening, which
  // is exactly the "I clicked load and nothing happened" this panel exists to
  // answer. The request cannot be cancelled from here anyway.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape" && !busy) { e.preventDefault(); onClose(); }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose, busy]);
  const close = busy ? () => {} : onClose;

  const sorted = useMemo(() => {
    const rows = [...saves];
    rows.sort((a, b) => {
      let av: string | number;
      let bv: string | number;
      if (sortKey === "step_count") {
        av = a.step_count; bv = b.step_count;
      } else {
        av = String(a[sortKey] ?? "").toLowerCase();
        bv = String(b[sortKey] ?? "").toLowerCase();
      }
      const cmp = av < bv ? -1 : av > bv ? 1 : 0;
      return asc ? cmp : -cmp;
    });
    return rows;
  }, [saves, sortKey, asc]);

  function sortBy(key: SortKey) {
    if (key === sortKey) setAsc((v) => !v);
    else { setSortKey(key); setAsc(key === "name" || key === "character" || key === "seed"); }
  }

  const cols: { key: SortKey; label: string }[] = [
    { key: "name", label: "Name" },
    { key: "saved_at", label: "Date" },
    { key: "character", label: "Character" },
    { key: "step_count", label: "Steps" },
    { key: "seed", label: "Seed" },
  ];

  return (
    <div role="dialog" aria-label="Load save" onClick={close}
      style={{ position: "fixed", inset: 0, zIndex: 50, background: "oklch(20% 0.02 255 / .55)",
        display: "flex", alignItems: "center", justifyContent: "center", padding: 24 }}>
      <div onClick={(e) => e.stopPropagation()}
        style={{ background: "var(--bg)", border: "1px solid var(--border)", borderRadius: 10,
          maxWidth: "min(820px, 94vw)", maxHeight: "86vh", overflow: "auto",
          boxShadow: "0 12px 40px oklch(30% 0.04 255 / .4)" }}>
        <div className="mono" style={{ display: "flex", alignItems: "center", justifyContent: "space-between",
          gap: 12, padding: "12px 16px", borderBottom: "1px solid var(--border)", position: "sticky", top: 0,
          background: "var(--bg)" }}>
          {busy ? (
            // Name the mechanism, not just "loading": the wait is proportional
            // to the run's length because restoring replays it move by move,
            // and knowing that is what stops the reflex to click again.
            <b aria-live="polite">restoring “{loading}” — replaying the run…</b>
          ) : (
            <b>Load save ({saves.length})</b>
          )}
          <button onClick={close} aria-label="Close" disabled={busy}
            style={{ padding: "4px 10px", border: "1px solid var(--border)", borderRadius: 4,
              cursor: busy ? "default" : "pointer", opacity: busy ? 0.5 : 1 }}>
            close (Esc)
          </button>
        </div>
        {saves.length === 0 ? (
          <div className="mono" style={{ padding: 24, color: "var(--muted)" }}>no saves yet</div>
        ) : (
          <table className="mono" style={{ borderCollapse: "collapse", width: "100%", fontSize: 12 }}>
            <thead>
              <tr style={{ textAlign: "left", color: "var(--muted)" }}>
                {cols.map((c) => (
                  <th key={c.key} onClick={() => sortBy(c.key)}
                    style={{ padding: "8px 12px", cursor: "pointer", userSelect: "none",
                      borderBottom: "1px solid var(--border)", whiteSpace: "nowrap" }}>
                    {c.label}{sortKey === c.key ? (asc ? " ▲" : " ▼") : ""}
                  </th>
                ))}
                <th style={{ padding: "8px 12px", borderBottom: "1px solid var(--border)" }}>Progress</th>
              </tr>
            </thead>
            <tbody>
              {sorted.map((s) => (
                <tr key={s.name} onClick={busy ? undefined : () => onLoad(s.name)}
                  title={busy ? "a restore is already running" : "click to load"}
                  style={{ cursor: busy ? "default" : "pointer",
                    borderBottom: "1px solid var(--border)",
                    // Dim everything except the row being restored, so the
                    // panel says WHICH save is loading and not merely that
                    // something is.
                    opacity: busy && s.name !== loading ? 0.4 : 1,
                    background: s.name === loading ? "var(--panel)" : "" }}
                  onMouseEnter={(e) => { if (!busy) e.currentTarget.style.background = "var(--panel)"; }}
                  onMouseLeave={(e) => { if (!busy) e.currentTarget.style.background = ""; }}>
                  <td style={{ padding: "8px 12px", fontWeight: 600 }}>
                    {s.name}{s.name === loading ? " ⏳" : ""}
                  </td>
                  <td style={{ padding: "8px 12px", whiteSpace: "nowrap", color: "var(--muted)" }}>{fmtDate(s.saved_at)}</td>
                  <td style={{ padding: "8px 12px" }}>{s.character}{s.ascension ? ` A${s.ascension}` : ""}</td>
                  <td style={{ padding: "8px 12px" }}>{s.step_count}</td>
                  <td style={{ padding: "8px 12px", color: "var(--muted)" }}>{s.seed}</td>
                  <td style={{ padding: "8px 12px" }}>{fmtProgress(s.progress)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
