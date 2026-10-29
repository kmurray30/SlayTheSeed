import type { Option } from "../api";

export default function ChoicePanel({
  options,
  onChoose,
  title,
}: {
  options: Option[];
  onChoose: (action: string, args: Record<string, unknown>) => void;
  title?: string;
}) {
  // Number the first 9 enabled options 1..9 (matches the keyboard shortcuts).
  let n = 0;
  const hotkeys = options.map((o) => (o.enabled && n < 9 ? ++n : null));
  return (
    <div className="mono" style={{ padding: 20, display: "flex", flexDirection: "column", gap: 8 }}>
      {title && <div style={{ fontWeight: 600, marginBottom: 6 }}>{title}</div>}
      {options.length === 0 && <div style={{ color: "var(--muted)" }}>no choices</div>}
      {options.map((o, i) => (
        <button
          key={i}
          disabled={!o.enabled}
          onClick={() => onChoose(o.action, o.args)}
          style={{
            textAlign: "left",
            padding: "8px 12px",
            border: "1px solid var(--border)",
            background: o.enabled ? "var(--panel)" : "var(--field-bg)",
            color: o.enabled ? "inherit" : "var(--muted)",
            cursor: o.enabled ? "pointer" : "default",
            borderRadius: 4,
            display: "flex",
            gap: 8,
            alignItems: "baseline",
          }}
        >
          {hotkeys[i] != null && (
            <span aria-hidden="true" style={{ minWidth: 16, color: "var(--accent)", fontWeight: 700 }}>{hotkeys[i]}</span>
          )}
          <span>{o.label}</span>
        </button>
      ))}
    </div>
  );
}
