import type { Results, SweepStatus } from "./api";

// ---------------------------------------------------------------------------
// Shared bits: panel styles, status glyphs, progress bar, throughput readout.
// ---------------------------------------------------------------------------
/** Single-row, non-wrapping status glyph + text (no rounded chip). */
export function StatusInline({ s }: { s: SweepStatus }): React.ReactElement {
  const color =
    s.status === "done"
      ? "var(--good, #6c6)"
      : s.status === "error"
        ? "var(--bad, #d66)"
        : s.status === "cancelled"
          ? "var(--muted)"
          : "var(--accent)";
  let glyph: string;
  let label: string;
  if (s.status === "done") {
    glyph = "✓";
    label = "";
  } else if (s.status === "error") {
    glyph = "✗";
    label = s.error ?? "error";
  } else if (s.status === "cancelled") {
    glyph = "⊘";
    label = s.total ? `cancelled · ${s.done ?? 0}/${s.total}` : "cancelled";
  } else if (s.status === "running") {
    glyph = "⏳";
    const base = s.total ? `${s.done ?? 0}/${s.total}` : "running";
    label = s.combats_per_s ? `${base} · ${s.combats_per_s.toFixed(1)}/s` : base;
  } else {
    glyph = "·";
    label = s.status;
  }
  return (
    <span
      style={{
        color,
        fontSize: 12,
        whiteSpace: "nowrap",
        display: "inline-flex",
        alignItems: "center",
        gap: 4,
      }}
    >
      <span>{glyph}</span>
      {label && (
        <span
          style={{
            overflow: "hidden",
            textOverflow: "ellipsis",
            maxWidth: 220,
          }}
        >
          {label}
        </span>
      )}
    </span>
  );
}

/** Thin inline progress bar; renders nothing unless a total is known. */
export function ProgressBar({
  done,
  total,
  width = 120,
}: {
  done?: number;
  total?: number;
  width?: number;
}): React.ReactElement | null {
  if (!total) return null;
  const frac = Math.max(0, Math.min(1, (done ?? 0) / total));
  return (
    <span
      style={{
        display: "inline-block",
        width,
        height: 6,
        background: "var(--panel-raised)",
        borderRadius: 3,
        overflow: "hidden",
        verticalAlign: "middle",
      }}
    >
      <span
        style={{
          display: "block",
          width: `${frac * 100}%`,
          height: "100%",
          background: "var(--accent)",
        }}
      />
    </span>
  );
}

/**
 * Compact speed readout for a finished sweep.
 *
 * Per-core rates are summed from the per-bucket throughput in `results` (always
 * present, for both CLI and web sweeps). The wall-clock combats/s + worker
 * count come from `status` (the final progress.json), which only web sweeps
 * write — so that half is omitted for CLI-produced sweeps.
 */
export function ThroughputSummary({
  results,
  status,
}: {
  results: Results;
  status: SweepStatus;
}): React.ReactElement | null {
  let actions = 0;
  let coreSeconds = 0;
  for (const b of Object.values(results.buckets)) {
    if (b.throughput) {
      actions += b.throughput.actions;
      coreSeconds += b.throughput.core_seconds;
    }
  }
  const perCore = coreSeconds > 0 ? actions / coreSeconds : 0;
  if (perCore <= 0 && status.combats_per_s === undefined) return null;
  return (
    <span style={{ color: "var(--muted)", fontSize: 13, whiteSpace: "nowrap", marginLeft: "auto" }}>
      ⚡ {perCore.toFixed(0)} act/s/core
      {status.combats_per_s !== undefined &&
        ` · ${status.combats_per_s.toFixed(1)} combat/s${
          status.jobs ? ` (${status.jobs} workers)` : ""
        }`}
    </span>
  );
}
