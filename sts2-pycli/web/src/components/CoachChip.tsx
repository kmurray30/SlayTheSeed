import type { DecisionRegret } from "../api";

const pct = (p: number) => `${Math.round(p * 100)}%`;

/** Live coach chip for the last played combat move, rendered by server verdict. */
export default function CoachChip({ regret, onDismiss }: {
  regret: DecisionRegret; onDismiss: () => void;
}) {
  const { verdict } = regret;
  if (verdict === "uncertain") return null;
  const bad = verdict === "slip" || verdict === "blunder";
  const tone = !bad ? "var(--accent-strong)"
    : verdict === "blunder" ? "var(--hp)" : "var(--warn, #c79a3a)";
  const hp = regret.hp_drop;
  return (
    <div className="mono" style={{ marginBottom: 8, padding: "6px 10px", borderRadius: 4,
      color: tone, border: `1px solid ${tone}`, background: "var(--field-bg)",
      fontSize: 11, display: "flex", gap: 8, alignItems: "baseline" }}>
      <div style={{ flex: 1 }}>
        {verdict === "best" && <>✓ best move — <b>{regret.played_label}</b></>}
        {verdict === "tie" && <>✓ toss-up — <b>{regret.played_label}</b> ≈ <b>{regret.best_label}</b></>}
        {bad && (
          <>
            <div>{verdict === "blunder" ? "⚠ blunder" : "▽ suboptimal"} — you: <b>{regret.played_label}</b></div>
            <div>AI: <b>{regret.best_line.join(" → ") || regret.best_label}</b></div>
            <div>
              {hp != null && hp >= 1 ? `≈ ${Math.round(hp)} HP more lost · ` : ""}
              {pct(regret.best_win_prob) === pct(regret.played_win_prob)
                ? `win ${pct(regret.best_win_prob)} either way`
                : `win ${pct(regret.best_win_prob)} → ${pct(regret.played_win_prob)}`}
            </div>
          </>
        )}
      </div>
      <button type="button" onClick={onDismiss} aria-label="Dismiss"
        style={{ font: "inherit", background: "none", border: "none", color: "var(--muted)",
          cursor: "pointer" }}>✕</button>
    </div>
  );
}
