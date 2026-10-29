import { useEffect, useRef, useState } from "react";
import { api, type DecisionAdvice, type DecisionAdvisorCandidate, type DecisionAdvisorProgress } from "../api";
import { useHints } from "../hints/HintsContext";

/**
 * Shared "Analyze options (EV)" advisor for the live non-combat-play decision
 * screens — in-combat card selects (Knowledge Daemon's pick, Headbutt…), shops,
 * and events. The deep, slow sibling of the card-reward advisor: it forks the
 * live run and Monte-Carlos the rest of the act out for every option (MCTS in
 * each fight), so it runs only on demand. Each screen renders the button +
 * progress block where it likes and joins {@link DecisionWinChip} onto its own
 * options by the candidate `key` (`"choice:0"` / `"card:1"` / `"option:2"` …).
 *
 * Play mode only — the analysis needs the live in-process run to fork. Replay
 * passes ``enabled = false`` and these render nothing.
 */

/** Live streaming progress: total/done playouts plus per-option completed counts. */
type DecProgress = {
  done: number;
  total: number;
  rollouts: number;
  perCand: Map<string, number>;
  cands: { key: string; label: string }[];
};

export interface DecisionAdvisorState {
  enabled: boolean;
  advising: boolean;
  advice: DecisionAdvice | null;
  prog: DecProgress | null;
  elapsed: number;
  byKey: Map<string, DecisionAdvisorCandidate>;
  /** True when at least one option cleared the act in ≥1 rollout. Act-win is a
   *  coarse, often-all-zero signal (the act boss is a wall + a deliberately
   *  cheap rollout policy), so the chips lead with it only when it actually
   *  discriminates; otherwise they fall back to the depth/HP signal the ranking
   *  already uses. */
  anyWin: boolean;
  run: () => void;
}

/** Run the streaming decision advisor for the current screen. ``resetKey``
 *  (e.g. the step index) invalidates prior advice when the screen changes. */
export function useDecisionAdvisor(resetKey: unknown, enabled: boolean): DecisionAdvisorState {
  // The advisor ranks the options in front of you, so it is advice like any
  // other: available in live play only when the global hints switch is on.
  const hints = useHints();
  const active = enabled && hints.on;
  const [advice, setAdvice] = useState<DecisionAdvice | null>(null);
  const [advising, setAdvising] = useState(false);
  const [prog, setProg] = useState<DecProgress | null>(null);
  const [elapsed, setElapsed] = useState(0);
  const startRef = useRef(0);

  // A fresh screen invalidates any prior advice.
  // Turning hints off mid-screen must also drop advice already on screen.
  useEffect(() => { setAdvice(null); setAdvising(false); setProg(null); setElapsed(0); }, [resetKey, active]);
  // Tick the elapsed-seconds readout while running.
  useEffect(() => {
    if (!advising) return;
    const id = setInterval(() => setElapsed((Date.now() - startRef.current) / 1000), 100);
    return () => clearInterval(id);
  }, [advising]);

  const byKey = new Map<string, DecisionAdvisorCandidate>(
    (advice?.candidates ?? []).map((c) => [c.key, c]),
  );
  const anyWin = (advice?.candidates ?? []).some((c) => c.win_rate > 0);

  async function run() {
    if (!active) return; // belt and braces: no advice without the switch
    startRef.current = Date.now();
    setElapsed(0);
    setProg(null);
    setAdvice(null);
    setAdvising(true);
    try {
      const result = await api.live.decisionAdvisorStream({}, (ev: DecisionAdvisorProgress) => {
        if (ev.type === "start") {
          setProg({
            done: 0,
            total: ev.total,
            rollouts: ev.rollouts,
            perCand: new Map(ev.candidates.map((c) => [c.key, 0])),
            cands: ev.candidates.map((c) => ({ key: c.key, label: c.label })),
          });
        } else {
          setProg((p) => {
            if (!p) return p;
            const perCand = new Map(p.perCand);
            perCand.set(ev.candidate_key, (perCand.get(ev.candidate_key) ?? 0) + 1);
            return { ...p, done: ev.done, perCand };
          });
        }
      });
      setAdvice(result);
    } catch (e) {
      setAdvice({ candidates: [], error: e instanceof Error ? e.message : String(e) });
    } finally {
      setAdvising(false);
      setProg(null);
    }
  }

  return { enabled: active, advising, advice, prog, elapsed, byKey, anyWin, run };
}

/** The "🎲 analyze options (EV)" trigger. Renders nothing in replay. */
export function DecisionAnalyzeButton({ adv, style }: { adv: DecisionAdvisorState; style?: React.CSSProperties }) {
  if (!adv.enabled) return null;
  return (
    <button
      className="mono"
      onClick={adv.run}
      disabled={adv.advising}
      title="Deep EV analysis: fork the run and play the rest of the act out many times for each option (MCTS in every fight). Slow — a few seconds."
      style={{ fontSize: 11, ...style }}
    >
      {adv.advising ? "analyzing…" : "🎲 analyze options (EV)"}
    </button>
  );
}

/** Progress bar + per-option counts while running, then the error / summary line. */
export function DecisionAnalyzeStatus({ adv }: { adv: DecisionAdvisorState }) {
  if (!adv.enabled) return null;
  const { advising, prog, elapsed, advice } = adv;
  return (
    <>
      {advising && (
        <div className="mono" style={{ marginTop: 10, fontSize: 11, color: "var(--muted)" }}>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
            <span>analyzing options…{prog ? ` ${prog.done}/${prog.total} sims` : ""}</span>
            <span>{elapsed.toFixed(1)}s</span>
          </div>
          <div style={{ height: 6, background: "var(--field-bg)", borderRadius: 3, overflow: "hidden" }}>
            <div
              style={{
                height: "100%",
                width: prog && prog.total ? `${(prog.done / prog.total) * 100}%` : "0%",
                background: "var(--accent-strong)",
                transition: "width 0.2s ease",
              }}
            />
          </div>
          {prog && (
            <div style={{ display: "flex", flexWrap: "wrap", gap: 14, marginTop: 6 }}>
              {prog.cands.map((c) => {
                const n = prog.perCand.get(c.key) ?? 0;
                const full = n >= prog.rollouts;
                return (
                  <span key={c.key} style={{ color: full ? "var(--accent-strong)" : "var(--muted)" }}>
                    {c.label} {n}/{prog.rollouts}
                  </span>
                );
              })}
            </div>
          )}
        </div>
      )}
      {advice?.error && (
        <div className="mono" style={{ color: "var(--hp)", marginTop: 10, fontSize: 11 }}>
          advisor unavailable: {advice.error}
        </div>
      )}
      {advice && !advice.error && (
        <div className="mono" style={{ color: "var(--muted)", marginTop: 10, fontSize: 11 }}>
          {adv.anyWin
            ? `act win-rate over ${advice.candidates[0]?.n ?? "?"} rollouts of the rest of act ${advice.act}`
            : `depth reached + HP at the boss over ${advice.candidates[0]?.n ?? "?"} rollouts of the rest of act ${advice.act} (no option cleared the act)`}
          {" "}· ★ = recommended
        </div>
      )}
    </>
  );
}

/** Per-option advice chip, keyed onto a rendered option by its candidate key.
 *  Green + ★ on the recommended option. Mirrors CardReward's AdviceChip.
 *
 *  Leads with the act win-rate only when at least one option actually cleared
 *  the act ({@link DecisionAdvisorState.anyWin}). Otherwise — the common case
 *  early in a run, where the cheap rollout policy rarely beats the act boss and
 *  every option reads 0% — it falls back to the depth/HP signal the ranking is
 *  really built on (`avg_floors_advanced` + `avg_hp_at_deepest`), so a screen of
 *  all-zero win-rates still shows a meaningful, discriminating number. */
export function DecisionWinChip({ c, adv }: { c: DecisionAdvisorCandidate; adv: DecisionAdvisorState }) {
  const star = c.is_best ? "★ " : "";
  const col = c.is_best ? "oklch(68% 0.13 145)" : "var(--muted)";
  const floors = c.avg_floors_advanced.toFixed(1);
  const hp = c.avg_hp_at_deepest.toFixed(0);
  const headline = adv.anyWin
    ? `${star}${(c.win_rate * 100).toFixed(0)}% act win`
    : `${star}~${floors} floors deeper`;
  const sub = adv.anyWin
    ? `Δ ${c.delta >= 0 ? "+" : ""}${(c.delta * 100).toFixed(0)} · ~${floors}f · ${hp} hp`
    : `${hp} hp at the wall`;
  return (
    <div
      className="mono"
      title={
        (adv.anyWin
          ? `act win-rate over ${c.n} rollouts of the rest of the act (MCTS in combat). `
          : `no option cleared the act in ${c.n} rollouts (the act boss is a wall for the ` +
            `cheap rollout policy), so this shows how far the option got instead. `) +
        `~${c.avg_floors_advanced.toFixed(1)} floors advanced, ${hp} HP at the deepest room. ` +
        `Δ is this option's composite value (act-win + depth reached + HP carried) ` +
        `minus the best other option; ★ = recommended.`
      }
      style={{ fontSize: 11, marginTop: 5, lineHeight: 1.45, color: col, fontWeight: c.is_best ? 600 : 400 }}
    >
      <div>{headline}</div>
      <div style={{ color: "var(--muted)", fontWeight: 400 }}>{sub}</div>
    </div>
  );
}
