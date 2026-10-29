import { useState } from "react";
import type { CombatLogRow } from "../api";
import { readableRows } from "../combatlog";
import { LogLine } from "./logLine";

/**
 * Shared turn-ledger pieces for the two history logs (LiveLog / EventLog).
 *
 * The ledger groups combat steps under "TURN N" headers, summarizes each turn
 * as chips (damage dealt / block gained / HP lost / kills), and renders each
 * step's combat-log slice as coloured sentences (`logLine.tsx`, the game's
 * own colouring). A `net / full` toggle (persisted) decides how much of the
 * tape is inlined: `net` keeps only what the enemies did — the turn chips already
 * summarize the player's side, and the step label says what was played —
 * while `full` shows every row.
 */

const DETAIL_KEY = "wb.history.detail";
export type DetailMode = "net" | "full";

/** Persisted detail mode. localStorage is absent/throwing in some contexts. */
export function useDetailMode(): [DetailMode, (m: DetailMode) => void] {
  const [mode, setMode] = useState<DetailMode>(() => {
    try {
      return globalThis.localStorage?.getItem(DETAIL_KEY) === "full" ? "full" : "net";
    } catch {
      return "net";
    }
  });
  const set = (m: DetailMode) => {
    setMode(m);
    try {
      globalThis.localStorage?.setItem(DETAIL_KEY, m);
    } catch {
      // Preference not persisted; the toggle still works this session.
    }
  };
  return [mode, set];
}

export function DetailToggle({ mode, onChange }: {
  mode: DetailMode;
  onChange: (m: DetailMode) => void;
}) {
  const btn = (m: DetailMode, label: string) => (
    <button type="button" onClick={() => onChange(m)}
      aria-pressed={mode === m}
      style={{
        font: "inherit", fontSize: 9.5, padding: "1px 7px", border: "none",
        cursor: "pointer", borderRadius: 4,
        background: mode === m ? "var(--accent-weak)" : "transparent",
        color: mode === m ? "var(--accent-strong)" : "var(--muted)",
        fontWeight: mode === m ? 600 : 400,
      }}>
      {label}
    </button>
  );
  return (
    <span aria-label="History detail" style={{ display: "inline-flex", border: "1px solid var(--border)",
      borderRadius: 5, overflow: "hidden", flexShrink: 0 }}>
      {btn("net", "net")}
      {btn("full", "full")}
    </span>
  );
}

/**
 * Chips summarizing one turn: total damage the player dealt, block gained,
 * HP the player lost, and kills. Kills only exist in the diff-derived detail
 * lines (the engine log has no death event), so those ride in via `details`.
 */
export function turnChips(rows: CombatLogRow[], details: string[]): string[] {
  let dealt = 0, taken = 0, block = 0;
  for (const r of rows) {
    if (r.kind === "damage" && typeof r.amount === "number") {
      if (r.actor_side === "player") dealt += r.amount;
      if (r.target_side === "player") taken += r.amount;
    }
    if (r.kind === "block" && r.actor_side === "player" && typeof r.amount === "number") {
      block += r.amount;
    }
  }
  const kills = details.filter((d) => d.includes("☠")).length;
  const out: string[] = [];
  if (dealt) out.push(`⚔ ${dealt}`);
  if (block) out.push(`🛡 ${block}`);
  if (taken) out.push(`−${taken} HP`);
  if (kills) out.push(`☠ ${kills}`);
  return out;
}

export function TurnHeader({ turn, chips }: { turn: number; chips: string[] }) {
  return (
    <div aria-label={`Turn ${turn}`}
      style={{
        display: "flex", justifyContent: "space-between", alignItems: "baseline",
        gap: 8, margin: "7px 0 2px", padding: "1px 6px",
        background: "oklch(24% 0.02 265)", borderRadius: 4,
      }}>
      <span style={{ fontSize: 10, letterSpacing: ".06em", color: "var(--accent)" }}>
        TURN {turn}
      </span>
      <span style={{ fontSize: 9.5, color: "var(--muted)", whiteSpace: "nowrap",
        overflow: "hidden", textOverflow: "ellipsis" }}>
        {chips.join(" · ")}
      </span>
    </div>
  );
}

/** One coloured diff line ("Nibbit HP 50→44 (−6)") for steps without a tape. */
export function ColoredDetail({ line }: { line: string }) {
  // Tokenize on the few shapes diff_states emits; everything else stays muted.
  const parts = line.split(/(\(\s*[−+]\d+\s*\)|☠ defeated|\d+→\d+)/g);
  return (
    <>
      {parts.map((p, i) => {
        if (/^\(\s*−\d+\s*\)$/.test(p)) return <span key={i} style={{ color: "var(--hp)" }}>{p}</span>;
        if (/^\(\s*\+\d+\s*\)$/.test(p)) return <span key={i} style={{ color: "var(--good, var(--accent))" }}>{p}</span>;
        if (p === "☠ defeated") return <span key={i} style={{ color: "var(--hp)", fontWeight: 600 }}>{p}</span>;
        if (/^\d+→\d+$/.test(p)) return <span key={i} style={{ color: "var(--gold)" }}>{p}</span>;
        return <span key={i}>{p}</span>;
      })}
    </>
  );
}

/**
 * The detail block under one history step.
 *
 * With a combat-log slice: the coloured sentences (net mode keeps only the
 * enemy turn's rows — the red-guttered "what happened to you" — plus any
 * defeat lines from the diff details). Without one: the diff detail lines,
 * coloured. `dim` de-emphasizes steps ahead of the current position.
 */
export function StepEvents({ rows: allRows, details, mode, dim }: {
  rows: CombatLogRow[];
  details: string[];
  mode: DetailMode;
  dim?: boolean;
}) {
  // A node's slice carries the view-only rows the board's effect stream needs
  // (`power_flash`), so drop them before anything here reads them — including
  // the emptiness test below, which decides whether this step falls back to
  // its diff detail lines. A step whose only rows were `power_flash` must
  // still count as having no tape. See `readableRows`.
  const rows = readableRows(allRows);
  const wrap = (children: React.ReactNode, key: number, enemy: boolean) => (
    <div key={key} data-detail
      style={{
        fontSize: 10.5, lineHeight: 1.5, padding: "0 4px 0 24px",
        opacity: dim ? 0.7 : 0.92,
        ...(enemy ? { borderLeft: "2px solid var(--hp-track)", marginLeft: 14, paddingLeft: 10 } : {}),
      }}>
      {children}
    </div>
  );
  if (rows.length === 0) {
    return (
      <>
        {details.map((d, i) => (
          <div key={i} data-detail style={{ fontSize: 10.5, lineHeight: 1.5,
            color: "var(--muted)", padding: "0 4px 0 24px", opacity: dim ? 0.7 : 1 }}>
            <ColoredDetail line={d} />
          </div>
        ))}
      </>
    );
  }
  const kept = mode === "full" ? rows : rows.filter((r) => r.side === "enemy");
  const deaths = details.filter((d) => d.includes("☠"));
  return (
    <>
      {kept.map((r, i) => wrap(<LogLine row={r} />, i, r.side === "enemy"))}
      {deaths.map((d, i) => (
        <div key={`d${i}`} data-detail style={{ fontSize: 10.5, lineHeight: 1.5,
          padding: "0 4px 0 24px", opacity: dim ? 0.7 : 1 }}>
          <ColoredDetail line={d} />
        </div>
      ))}
    </>
  );
}
