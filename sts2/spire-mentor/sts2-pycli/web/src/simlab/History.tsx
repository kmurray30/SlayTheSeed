import { useState } from "react";
import type { SweepListEntry } from "./api";
import { card, shortTime } from "./styles";
import { ProgressBar, StatusInline } from "./widgets";

// ---------------------------------------------------------------------------
// SweepList
// ---------------------------------------------------------------------------
/** Strip a known suffix off the encounter name for a compact label. */
function shortEnc(enc: string): string {
  return enc.replace(/_(NORMAL|ELITE|BOSS|WEAK)$/, "");
}

function SweepRow({
  s,
  selected,
  onSelect,
}: {
  s: SweepListEntry;
  selected: boolean;
  onSelect: (id: string) => void;
}) {
  const positions = s.spec.positions?.length ?? s.spec.start_states?.length ?? 0;
  const encLabel = positions
    ? `${positions} position${positions === 1 ? "" : "s"}`
    : s.spec.encounters.length === 1
      ? `${shortEnc(s.spec.encounters[0])}${
          s.spec.ascensions.length === 1 ? `+${s.spec.ascensions[0]}` : ""
        } enc`
      : `${s.spec.encounters.length} enc`;
  // New specs carry `decks`; legacy ones only `deck_preset` (count as 1).
  const deckCount = s.spec.decks?.length ?? 1;
  const deckLabel = positions ? "" : ` · ${deckCount} deck${deckCount === 1 ? "" : "s"}`;
  const summary = `${s.spec.name} · ${encLabel}${deckLabel} · ${s.spec.strategies.length} strats · ${s.spec.n_seeds} ${positions ? "trials" : "seeds"}`;
  return (
    <div
      onClick={() => onSelect(s.sweep_id)}
      title={`${s.spec.encounters.join(", ")} · A${s.spec.ascensions.join(",")} · ${s.spec.strategies.join("/")}`}
      style={{
        display: "flex",
        alignItems: "center",
        gap: 8,
        padding: "3px 8px",
        cursor: "pointer",
        background: selected ? "var(--accent-weak)" : undefined,
        borderRadius: 4,
        fontSize: 13,
        whiteSpace: "nowrap",
      }}
    >
      <span style={{ color: "var(--muted)" }}>{selected ? "▾" : "▸"}</span>
      <span
        style={{
          flex: 1,
          overflow: "hidden",
          textOverflow: "ellipsis",
        }}
      >
        {summary}
      </span>
      {s.status === "running" && <ProgressBar done={s.done} total={s.total} width={60} />}
      <StatusInline s={s} />
      <span style={{ color: "var(--muted)", fontSize: 12, minWidth: 44, textAlign: "right" }}>
        {shortTime(s.mtime)}
      </span>
    </div>
  );
}

export default function SweepHistory({
  sweeps,
  selected,
  onSelect,
}: {
  sweeps: SweepListEntry[];
  selected: string | null;
  onSelect: (id: string) => void;
}) {
  const [showAll, setShowAll] = useState(false);
  const RECENT = 8;
  // Server already sorts newest-first by mtime; trust that order.
  const shown = showAll ? sweeps : sweeps.slice(0, RECENT);
  return (
    <div style={{ ...card, padding: 8 }}>
      <div
        style={{
          fontSize: 12,
          color: "var(--muted)",
          fontWeight: 600,
          padding: "2px 8px 6px",
        }}
      >
        History
      </div>
      {sweeps.length === 0 && (
        <p style={{ color: "var(--muted)", padding: "0 8px" }}>No sweeps yet.</p>
      )}
      {shown.map((s) => (
        <SweepRow
          key={s.sweep_id}
          s={s}
          selected={s.sweep_id === selected}
          onSelect={onSelect}
        />
      ))}
      {sweeps.length > RECENT && (
        <button
          onClick={() => setShowAll((v) => !v)}
          style={{
            margin: "6px 8px 2px",
            fontSize: 12,
            background: "none",
            border: "none",
            color: "var(--accent)",
            cursor: "pointer",
            padding: 0,
          }}
        >
          {showAll ? "show less" : `show all (${sweeps.length})`}
        </button>
      )}
    </div>
  );
}
