import { useEffect, useLayoutEffect, useRef, useState } from "react";
import type { WpaReview as Review, WpaTurningPoint, WpaVerify } from "../api";

/**
 * WPA run review — the run-level twin of the post-combat RegretReview dock
 * (issue #201). Where the regret review grades combat moves against the MCTS
 * line, this reviews the RUN-level decisions (map paths, card picks, shops,
 * rests, events): the AI's outlook score V(s), recorded live at every
 * non-combat decision, drawn as a curve with the biggest swings flagged as
 * turning points.
 *
 * Honesty rules baked into the rendering:
 * - V is the PPO critic's shaped-return estimate Φ — a RELATIVE outlook score,
 *   never shown as a percentage.
 * - A raw swing mixes the choice with the luck revealed after it, so each
 *   turning point offers a "verify" spot check (live runs only): the agent
 *   finishes the run twice — the played line vs the AI's preferred
 *   alternative — and the real outcomes are compared.
 *
 * Purely presentational apart from the verify calls (delegated to `onVerify`).
 * Same dock contract as RegretReview: fixed to the bottom, reports its height
 * via `onHeight`, closes on ✕ or Escape, `onSeek` jumps the viewer to a
 * decision without closing.
 */

const CURVE_W = 640;
const CURVE_H = 72;
const PAD = 6;

function curveGeometry(values: number[]) {
  const lo = Math.min(...values);
  const hi = Math.max(...values);
  const span = hi - lo || 1;
  const x = (i: number) =>
    values.length === 1 ? CURVE_W / 2 : PAD + (i * (CURVE_W - 2 * PAD)) / (values.length - 1);
  const y = (v: number) => CURVE_H - PAD - ((v - lo) * (CURVE_H - 2 * PAD)) / span;
  return { x, y };
}

function Curve({ review, onSeek }: { review: Review; onSeek?: (p: WpaTurningPoint) => void }) {
  const values = review.points.map((p) => p.value);
  if (values.length < 2) return null;
  const { x, y } = curveGeometry(values);
  const line = values.map((v, i) => `${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(" ");
  // Turning points mark the decision the swing followed — locate each by idx.
  const markers = review.turning_points
    .map((tp) => {
      const i = review.points.findIndex((p) => p.idx === tp.idx);
      return i < 0 ? null : { tp, cx: x(i), cy: y(review.points[i].value) };
    })
    .filter((m): m is NonNullable<typeof m> => m != null);
  return (
    <svg
      viewBox={`0 0 ${CURVE_W} ${CURVE_H}`}
      style={{ width: "100%", height: CURVE_H, display: "block" }}
      role="img"
      aria-label="run outlook curve"
    >
      <polyline
        points={line}
        fill="none"
        stroke="var(--accent-strong)"
        strokeWidth={1.5}
        vectorEffect="non-scaling-stroke"
      />
      {markers.map(({ tp, cx, cy }) => (
        <circle
          key={tp.idx}
          cx={cx}
          cy={cy}
          r={5}
          fill={tp.swing < 0 ? "var(--hp)" : "var(--accent-strong)"}
          stroke="var(--bg)"
          strokeWidth={1.5}
          style={{ cursor: onSeek ? "pointer" : undefined }}
          onClick={onSeek ? () => onSeek(tp) : undefined}
        >
          <title>{tp.message}</title>
        </circle>
      ))}
    </svg>
  );
}

function VerifyResult({ v }: { v: WpaVerify }) {
  const outcome = (a: { label: string; won: boolean }) => `“${a.label}” → ${a.won ? "WIN" : "LOSS"}`;
  const verdictText =
    v.verdict === "same"
      ? "same outcome either way — this choice didn't decide the run"
      : v.verdict === "alternative_better"
        ? "the alternative won where your line lost"
        : "your pick held up — the alternative lost";
  return (
    <div style={{ marginTop: 6, fontSize: 12 }}>
      <div>
        <b
          style={{
            color:
              v.verdict === "same"
                ? "var(--muted)"
                : v.verdict === "alternative_better"
                  ? "var(--hp)"
                  : "var(--accent-strong)",
          }}
        >
          {verdictText}
        </b>
      </div>
      <div style={{ color: "var(--muted)", marginTop: 2 }}>
        {outcome(v.played)} · {outcome(v.alternative)}
      </div>
      <div style={{ color: "var(--muted)", fontSize: 11, marginTop: 2 }}>{v.note}</div>
    </div>
  );
}

function TurningPointRow({
  tp,
  onSeek,
  onVerify,
}: {
  tp: WpaTurningPoint;
  onSeek?: (tp: WpaTurningPoint) => void;
  onVerify?: (tp: WpaTurningPoint) => Promise<WpaVerify>;
}) {
  const [verifying, setVerifying] = useState(false);
  const [verify, setVerify] = useState<WpaVerify | null>(null);
  const [verifyError, setVerifyError] = useState<string | null>(null);
  const canVerify = onVerify != null && tp.node_id != null;

  async function runVerify() {
    if (!onVerify || verifying) return;
    setVerifying(true);
    setVerifyError(null);
    try {
      setVerify(await onVerify(tp));
    } catch (e) {
      setVerifyError(e instanceof Error ? e.message : "verify failed");
    } finally {
      setVerifying(false);
    }
  }

  return (
    <div
      style={{
        background: "var(--field-bg)",
        border: "1px solid var(--border)",
        borderRadius: 8,
        padding: "10px 12px",
        marginBottom: 8,
      }}
    >
      <div style={{ display: "flex", gap: 8, alignItems: "baseline", flexWrap: "wrap" }}>
        <button
          type="button"
          onClick={onSeek ? () => onSeek(tp) : undefined}
          title={onSeek ? "jump to this decision" : undefined}
          style={{
            all: "unset",
            cursor: onSeek ? "pointer" : "default",
            color: tp.swing < 0 ? "var(--hp)" : "var(--accent-strong)",
            fontWeight: 700,
          }}
        >
          {tp.swing > 0 ? "+" : ""}
          {tp.swing.toFixed(3)}
        </button>
        <span style={{ color: "var(--muted)", fontSize: 12 }}>
          {tp.floor != null ? `floor ${tp.floor}` : `step ${tp.idx}`} · {tp.decision}
        </span>
        {canVerify && !verify && (
          <button
            onClick={() => void runVerify()}
            disabled={verifying}
            title="spot-check this turning point: the agent finishes the run twice — your line vs the AI's preferred alternative (slow)"
            style={{ marginLeft: "auto", fontSize: 11 }}
          >
            {verifying ? "verifying… (plays the run out twice)" : "🔬 verify"}
          </button>
        )}
      </div>
      <div style={{ marginTop: 4, fontSize: 13 }}>{tp.message}</div>
      {verifyError && (
        <div style={{ marginTop: 4, color: "var(--hp)", fontSize: 11 }}>{verifyError}</div>
      )}
      {verify && <VerifyResult v={verify} />}
    </div>
  );
}

export default function WpaReview({
  review,
  live = false,
  onSeek,
  onVerify,
  onRerun,
  onClose,
  onHeight,
}: {
  review: Review | null;
  live?: boolean;
  onSeek?: (tp: WpaTurningPoint) => void;
  /** Live runs only: counterfactual spot check of a turning point. */
  onVerify?: (tp: WpaTurningPoint) => Promise<WpaVerify>;
  onRerun?: () => void;
  onClose: () => void;
  /** Reports the dock's rendered height (px) so the parent can pad scroll
   *  regions out from under this fixed overlay; called with 0 on unmount. */
  onHeight?: (px: number) => void;
}) {
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") {
        e.preventDefault();
        onClose();
      }
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
    return () => {
      ro.disconnect();
      onHeight(0);
    };
  }, [onHeight]);

  const s = review?.summary;
  return (
    <div
      ref={rootRef}
      className="mono"
      role="region"
      aria-label="WPA Run Review"
      style={{
        position: "fixed",
        left: 0,
        right: 0,
        bottom: 0,
        zIndex: 50,
        display: "flex",
        flexDirection: "column",
        maxHeight: "46vh",
        background: "var(--bg)",
        borderTop: "1px solid var(--border)",
        boxShadow: "0 -8px 28px oklch(30% 0.04 255 / .35)",
      }}
    >
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 12,
          padding: "10px 16px",
          borderBottom: "1px solid var(--border)",
          background: "var(--bg)",
        }}
      >
        <div style={{ display: "flex", alignItems: "baseline", gap: 10 }}>
          <b>Run review — turning points{live ? " · live run" : ""}</b>
          <span style={{ color: "var(--muted)", fontSize: 11 }}>
            outlook score, not a win % {onSeek ? "· click a point to jump there" : ""}
          </span>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          {onRerun && (
            <button
              onClick={onRerun}
              aria-label="Re-run analysis"
              title="re-run the analysis (e.g. after more moves)"
            >
              ↻ re-run
            </button>
          )}
          <button onClick={onClose} aria-label="Close">
            ✕
          </button>
        </div>
      </div>
      <div style={{ padding: 16, overflow: "auto" }}>
        {review == null && <div style={{ color: "var(--muted)", fontSize: 12 }}>analyzing…</div>}
        {review != null && !review.available && (
          <div style={{ color: "var(--muted)", fontSize: 13 }}>
            {review.reason ?? "no run-level value data for this run"}
          </div>
        )}
        {review != null && review.available && (
          <>
            <div style={{ color: "var(--muted)", fontSize: 12, marginBottom: 8 }}>
              {s?.n_points ?? 0} decisions scored · outlook {s?.first_value?.toFixed(3)} →{" "}
              {s?.final_value?.toFixed(3)}
              {s?.won != null ? (s.won ? " · run WON" : " · run LOST") : ""}
            </div>
            <Curve review={review} onSeek={onSeek} />
            <div style={{ color: "var(--muted)", fontSize: 11, margin: "12px 0 8px" }}>
              turning points — the decisions your run swung on:
            </div>
            {review.turning_points.length === 0 && (
              <div style={{ color: "var(--accent-strong)", fontSize: 13 }}>
                steady run — no outlook swing above the threshold. ✔
              </div>
            )}
            {review.turning_points.map((tp) => (
              <TurningPointRow key={tp.idx} tp={tp} onSeek={onSeek} onVerify={onVerify} />
            ))}
            <div style={{ color: "var(--muted)", fontSize: 11, marginTop: 10 }}>
              {review.caveats.map((c, i) => (
                <div key={i}>· {c}</div>
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
