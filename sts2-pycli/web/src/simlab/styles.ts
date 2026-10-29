import type React from "react";

// Panel styles + tiny formatters shared by the SimLab views (no components here,
// so React fast-refresh stays happy).
export const font = "var(--font, system-ui, -apple-system, sans-serif)";
export const card: React.CSSProperties = {
  border: "1px solid var(--border)",
  borderRadius: 8,
  padding: 16,
  marginBottom: 16,
  background: "var(--panel)",
};
export const th: React.CSSProperties = {
  textAlign: "right",
  borderBottom: "2px solid var(--border)",
  padding: "4px 8px",
  color: "var(--muted)",
  fontSize: 12,
  fontWeight: 600,
  letterSpacing: "0.04em",
  textTransform: "uppercase",
};
export const td: React.CSSProperties = {
  textAlign: "right",
  borderBottom: "1px solid var(--border)",
  padding: "4px 8px",
};

/** Short timestamp: "14:32" for today, otherwise "Jun 3". */
export function shortTime(mtime: number): string {
  const d = new Date(mtime * 1000);
  const now = new Date();
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate();
  return sameDay
    ? d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit", hour12: false })
    : d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

