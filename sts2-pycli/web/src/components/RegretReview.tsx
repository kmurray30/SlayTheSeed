import { useEffect, useLayoutEffect, useRef } from "react";
import { type RegretReview as Review, type DecisionRegret } from "../api";

/**
 * Post-combat regret review — the learning engine, shown as a dock pinned to
 * the bottom of the viewport (not a modal, so the board/timeline above stays
 * visible and interactive).
 *
 * Replays the loaded run's combat decisions, diffing each against the MCTS line,
 * and surfaces the 1-2 plays that cost the most win-probability:
 *
 *   Turn 3: Strike instead of Defend dropped P(win) 71%→58% (−13 pts)
 *
 * Purely presentational — all numbers come from the server's
 * /api/regret-review. The expensive diff is run by the parent and cached, so
 * the dock stays open while you click through decisions; clicking a row seeks
 * the replay there *without closing* (when onSeek is provided). Closes on the ✕
 * or Escape; ↻ forces a fresh analysis (onRerun).
 *
 * Because the dock is a fixed overlay it would otherwise cover the bottom of
 * the board (the card row) and the history log with no way to scroll them into
 * view. It reports its rendered height via `onHeight` so the parent can reserve
 * matching bottom padding on those scroll regions.
 */

function pct(p: number): string {
  return `${Math.round(p * 100)}%`;
}

function BlunderRow({ d, onSeek, tone = "var(--hp)" }: {
  d: DecisionRegret; onSeek?: (d: DecisionRegret) => void; tone?: string;
}) {
  return (
    <button
      type="button"
      onClick={onSeek ? () => onSeek(d) : undefined}
      title={onSeek ? "jump to this decision" : undefined}
      style={{
        display: "block", width: "100%", textAlign: "left", cursor: onSeek ? "pointer" : "default",
        background: "var(--field-bg)", border: "1px solid var(--border)", borderRadius: 8,
        padding: "10px 12px", marginBottom: 8, color: "var(--text)",
      }}
    >
      <div style={{ display: "flex", gap: 8, alignItems: "baseline" }}>
        <span style={{ color: tone, fontWeight: 700 }}>
          −{d.drop_pct} pts{d.hp_drop != null && Math.round(d.hp_drop) >= 1 ? ` · ≈${Math.round(d.hp_drop)} HP` : ""}
        </span>
        <span style={{ color: "var(--muted)", fontSize: 12 }}>
          turn {d.turn ?? "?"}{d.floor != null ? ` · floor ${d.floor}` : ""}
        </span>
      </div>
      <div style={{ marginTop: 4, fontSize: 13 }}>
        played <b>{d.played_label}</b> instead of{" "}
        <b style={{ color: "var(--accent-strong)" }}>{d.best_label}</b>{" "}
        {pct(d.played_win_prob) === pct(d.best_win_prob)
          ? `(${pct(d.best_win_prob)} either way)`
          : `(${pct(d.played_win_prob)} vs ${pct(d.best_win_prob)})`}
      </div>
      <div style={{ marginTop: 3, color: "var(--muted)", fontSize: 11 }}>
        your move ranked #{d.player_rank + 1} of {d.n_actions} · {d.visits} MCTS visits on the top line
      </div>
    </button>
  );
}

export default function RegretReview({
  review,
  live = false,
  onSeek,
  onRerun,
  onClose,
  onHeight,
}: {
  review: Review | null;
  live?: boolean;
  onSeek?: (d: DecisionRegret) => void;
  onRerun?: () => void;
  onClose: () => void;
  /** Reports the dock's rendered height (px) so the parent can pad scroll
   *  regions out from under this fixed overlay; called with 0 on unmount. */
  onHeight?: (px: number) => void;
}) {
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") { e.preventDefault(); onClose(); }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const rootRef = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => {
    const el = rootRef.current;
    if (!el || !onHeight) return;
    const report = () => onHeight(el.offsetHeight);
    report();
    const ro = new ResizeObserver(report);
    ro.observe(el);
    return () => { ro.disconnect(); onHeight(0); };
  }, [onHeight]);

  const s = review?.summary;
  const lost = s?.cumulative_drop_pct ?? 0;
  const blunders = [...(review?.blunders ?? [])].sort((a, b) => b.severity - a.severity);
  const uncertain = (review?.decisions ?? []).filter((d) => d.verdict === "uncertain").length;
  // Slips the live coach also flags (server verdict), worst-first. Ties are
  // not surfaced as mistakes.
  const slips = (review?.decisions ?? [])
    .filter((d) => d.verdict === "slip")
    .sort((a, b) => b.severity - a.severity);
  // Truly clean only when nothing was lost (win-points OR HP), nothing slipped
  // and nothing went ungraded.
  const trulyClean = review != null && blunders.length === 0 && slips.length === 0
    && lost === 0 && (s?.cumulative_hp_drop ?? 0) === 0 && uncertain === 0;

  return (
    <div ref={rootRef} className="mono" role="region" aria-label="Regret Review"
      style={{ position: "fixed", left: 0, right: 0, bottom: 0, zIndex: 50,
        display: "flex", flexDirection: "column", maxHeight: "46vh",
        background: "var(--bg)", borderTop: "1px solid var(--border)",
        boxShadow: "0 -8px 28px oklch(30% 0.04 255 / .35)" }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12,
          padding: "10px 16px", borderBottom: "1px solid var(--border)", background: "var(--bg)" }}>
          <div style={{ display: "flex", alignItems: "baseline", gap: 10 }}>
            <b>Post-combat regret review{live ? " · live run" : ""}</b>
            {onSeek && (
              <span style={{ color: "var(--muted)", fontSize: 11 }}>
                click a decision to jump there
              </span>
            )}
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            {onRerun && (
              <button onClick={onRerun} aria-label="Re-run analysis"
                title="re-run the analysis (e.g. after more moves)">↻ re-run</button>
            )}
            <button onClick={onClose} aria-label="Close">✕</button>
          </div>
        </div>
        <div style={{ padding: 16, overflow: "auto" }}>
          {review == null && (
            <div style={{ color: "var(--muted)", fontSize: 12 }}>analyzing…</div>
          )}
          {review != null && (
            <>
              <div style={{ color: "var(--muted)", fontSize: 12, marginBottom: 12 }}>
                {s?.total_decisions ?? 0} decision(s) reviewed ·{" "}
                {s?.blunder_count ?? 0} blunder(s) ·{" "}
                {s?.cumulative_drop_pct ?? 0} total win-prob pts lost ·{" "}
                {Math.round(s?.cumulative_hp_drop ?? 0)} HP lost
              </div>
              {trulyClean ? (
                <div style={{ color: "var(--accent-strong)", fontSize: 13 }}>
                  clean run — every decision matched or beat the MCTS line. ✔
                </div>
              ) : (
                <>
                  {blunders.length > 0 && (
                    <>
                      <div style={{ color: "var(--muted)", fontSize: 11, marginBottom: 8 }}>
                        blunders — costliest plays:
                      </div>
                      {blunders.map((d) => (
                        <BlunderRow key={d.idx} d={d} onSeek={onSeek} />
                      ))}
                    </>
                  )}
                  {slips.length > 0 && (
                    <>
                      <div style={{ color: "var(--muted)", fontSize: 11, margin: "12px 0 8px" }}>
                        smaller slips — sub-threshold, often within MCTS search noise:
                      </div>
                      {slips.map((d) => (
                        <BlunderRow key={d.idx} d={d} onSeek={onSeek} tone="var(--warn, #c79a3a)" />
                      ))}
                    </>
                  )}
                  {blunders.length === 0 && slips.length === 0 && (
                    <div style={{ color: "var(--accent-strong)", fontSize: 13 }}>
                      on the MCTS line — {lost} win-prob pt(s) lost across {s?.total_decisions ?? 0}{" "}
                      decisions
                      {uncertain > 0
                        ? `; ${uncertain} move(s) ungraded (too few search samples).`
                        : ", all toss-ups within search noise. ✔"}
                    </div>
                  )}
                </>
              )}
            </>
          )}
        </div>
    </div>
  );
}
