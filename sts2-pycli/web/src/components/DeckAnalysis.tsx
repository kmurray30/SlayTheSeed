import { useEffect, useState } from "react";
import type { DeckAnalysis as Report, TrendPoint } from "../api";
import { loadArchetypes, deckMembership, type ArchetypeMembership } from "../archetype";

/**
 * Modal grading the master deck on the three axes — Damage, Block, Velocity —
 * after Jorbs' "jobs" framework and the volume-cube analogy (deck strength ≈
 * Damage × Block × Velocity, so a starved axis can't be hidden). Shows the
 * per-axis scores, a triangular radar, the raw numbers behind each axis
 * (expected damage/block per turn, scaling, AOE, velocity engine pieces), an
 * energy curve, a composition breakdown, and heuristic crisis warnings.
 *
 * Closes on backdrop click or Escape. Purely presentational — all numbers come
 * from the server's deck_analysis report.
 */

const AXES = [
  {
    key: "damage",
    label: "Damage",
    color: "var(--hp, oklch(62% 0.2 25))",
    help: "Average damage your deck deals per turn over a simulated multi-turn fight. Scaling (Strength, per-turn powers), multi-hit and AOE all earn their score by raising this output — nothing is a flat bonus.",
  },
  {
    key: "block",
    label: "Block",
    color: "oklch(70% 0.13 240)",
    help: "Average block your deck gains per turn over the simulation. Block engines (Metallicize, Dexterity, Barricade) show up by compounding over turns.",
  },
  {
    key: "velocity",
    label: "Velocity",
    color: "oklch(78% 0.16 95)",
    help: "Extra card plays your draw/energy engines buy per turn — measured as plays-per-turn above the same deck simulated on raw energy. Doing more with less.",
  },
] as const;

const SCORE_HELP =
  "Each axis is a 0-100 index (higher is better) with diminishing returns — NOT raw damage/block. " +
  "It saturates, so going from 20→40 is a bigger real jump than 80→90. The scores come from simulating " +
  "the deck over several turns, so scaling, AOE and velocity emerge from actual play. Hover an axis for details.";

const VEL_LABELS: Record<string, string> = {
  draw: "card draw",
  energy: "energy gain",
  extra_play: "extra plays",
  tutor: "deck manip",
  cost_reduction: "cost reduction",
  exhaust_synergy: "exhaust engine",
};

function Bar({ label, value, color, help }: { label: string; value: number; color: string; help?: string }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 12 }} title={help}>
      <span style={{ width: 64, color: "var(--muted)", cursor: help ? "help" : "default",
        textDecoration: help ? "underline dotted" : "none", textUnderlineOffset: 2 }}>{label}</span>
      <div style={{ flex: 1, height: 12, background: "var(--panel)", borderRadius: 6, overflow: "hidden" }}>
        <div style={{ width: `${value}%`, height: "100%", background: color }} />
      </div>
      <b style={{ width: 36, textAlign: "right" }}>{value}</b>
    </div>
  );
}

/** Triangular radar (one vertex per axis) — the "cube" silhouette at a glance. */
function Radar({ d, b, v }: { d: number; b: number; v: number }) {
  const cx = 70;
  const cy = 70;
  const R = 56;
  // top = damage, bottom-right = block, bottom-left = velocity.
  const angs = [-90, 30, 150].map((a) => (a * Math.PI) / 180);
  const pt = (frac: number, i: number) => [
    cx + R * frac * Math.cos(angs[i]),
    cy + R * frac * Math.sin(angs[i]),
  ];
  const outer = angs.map((_, i) => pt(1, i));
  const vals = [d, b, v].map((x) => Math.max(0.02, x / 100));
  const poly = vals.map((f, i) => pt(f, i));
  const path = (pts: number[][]) => pts.map((p) => p.join(",")).join(" ");
  const labels = ["DMG", "BLK", "VEL"];
  return (
    <svg width={140} height={150} aria-hidden style={{ flexShrink: 0 }}>
      <polygon points={path(outer)} fill="none" stroke="var(--border)" />
      <polygon points={path(outer.map((_, i) => pt(0.5, i)))} fill="none" stroke="var(--border)" opacity={0.5} />
      <polygon points={path(poly)} fill="oklch(70% 0.13 240 / .35)" stroke="oklch(70% 0.13 240)" />
      {outer.map((p, i) => (
        <text key={i} x={p[0]} y={p[1] + (i === 0 ? -4 : 14)} textAnchor="middle"
          fontSize={10} fill="var(--muted)">{labels[i]}</text>
      ))}
    </svg>
  );
}

function Chips({ items, empty }: { items: string[]; empty: string }) {
  if (items.length === 0) return <span style={{ color: "var(--muted)", fontSize: 11 }}>{empty}</span>;
  return (
    <div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
      {items.map((t, i) => (
        <span key={i} style={{ fontSize: 11, padding: "1px 6px", border: "1px solid var(--border)",
          borderRadius: 4, background: "var(--panel)" }}>{t}</span>
      ))}
    </div>
  );
}

/** Multi-series sparkline of the four axis scores over the run (one x per deck
 *  change). Volume is drawn bold; the three axes are faint underneath. Clicking
 *  jumps the replay to that point. */
function Trend({ points, onSeek }: { points: TrendPoint[]; onSeek?: (idx: number) => void }) {
  const W = 612;
  const H = 96;
  const padX = 6;
  const padY = 8;
  const n = points.length;
  const x = (i: number) => padX + (i * (W - 2 * padX)) / Math.max(1, n - 1);
  const y = (v: number) => padY + (1 - v / 100) * (H - 2 * padY);
  const series = [
    { key: "damage", color: "var(--hp, oklch(62% 0.2 25))", w: 1 },
    { key: "block", color: "oklch(70% 0.13 240)", w: 1 },
    { key: "velocity", color: "oklch(78% 0.16 95)", w: 1 },
    { key: "volume", color: "var(--fg)", w: 2 },
  ] as const;
  const line = (k: (typeof series)[number]["key"]) =>
    points.map((p, i) => `${x(i)},${y(p.scores[k])}`).join(" ");
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
      <b style={{ fontSize: 12 }}>Run trend</b>
      <svg width={W} height={H} style={{ background: "var(--panel)", borderRadius: 6 }}>
        {[25, 50, 75].map((g) => (
          <line key={g} x1={padX} x2={W - padX} y1={y(g)} y2={y(g)} stroke="var(--border)" opacity={0.4} />
        ))}
        {series.map((s) => (
          <polyline key={s.key} points={line(s.key)} fill="none" stroke={s.color}
            strokeWidth={s.w} opacity={s.key === "volume" ? 1 : 0.55} />
        ))}
        {points.map((p, i) => (
          <circle key={i} cx={x(i)} cy={y(p.scores.volume)} r={3} fill="var(--fg)"
            style={{ cursor: onSeek ? "pointer" : "default" }} onClick={() => onSeek?.(p.idx)}>
            <title>{`floor ${p.floor ?? "?"} · ${p.deck_size} cards · volume ${p.scores.volume}`}</title>
          </circle>
        ))}
      </svg>
      <div style={{ display: "flex", gap: 12, fontSize: 10, color: "var(--muted)" }}>
        <span style={{ color: "var(--fg)" }}>● volume</span>
        <span style={{ color: "var(--hp, oklch(62% 0.2 25))" }}>● damage</span>
        <span style={{ color: "oklch(70% 0.13 240)" }}>● block</span>
        <span style={{ color: "oklch(78% 0.16 95)" }}>● velocity</span>
        <span style={{ marginLeft: "auto" }}>{n} deck changes · click a point to jump</span>
      </div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
      <b style={{ fontSize: 12 }}>{title}</b>
      {children}
    </div>
  );
}

export default function DeckAnalysis({
  report,
  trend,
  onSeek,
  onClose,
  deckTokens,
  character,
}: {
  report: Report | null;
  trend?: TrendPoint[];
  onSeek?: (idx: number) => void;
  onClose: () => void;
  /** Deck card tokens — enables the archetype-membership panel (spec §10). */
  deckTokens?: string[];
  /** Character sub-name (e.g. "ironclad") for the archetype file. */
  character?: string;
}) {
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") { e.preventDefault(); onClose(); }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  // Archetype membership: where the current deck sits on each archetype's
  // measured dose-response curve. Only populated when the caller supplies deck
  // tokens + character (backward-compatible: no props → no panel).
  const [membership, setMembership] = useState<ArchetypeMembership[]>([]);
  useEffect(() => {
    setMembership([]);
    if (!character || !deckTokens || deckTokens.length === 0) return;
    let live = true;
    loadArchetypes(character)
      .then((doc) => { if (live) setMembership(deckMembership(doc, deckTokens)); })
      .catch(() => {});
    return () => { live = false; };
  }, [character, deckTokens]);

  const curveKeys = report?.composition
    ? Object.keys(report.composition.energy_curve).sort((a, b) =>
        (a === "X" ? 99 : Number(a)) - (b === "X" ? 99 : Number(b)))
    : [];
  const curveMax = report?.composition
    ? Math.max(1, ...Object.values(report.composition.energy_curve))
    : 1;

  return (
    <div role="dialog" aria-label="Deck Analysis" onClick={onClose}
      style={{ position: "fixed", inset: 0, zIndex: 50, background: "oklch(20% 0.02 255 / .55)",
        display: "flex", alignItems: "center", justifyContent: "center", padding: 24 }}>
      <div className="mono" onClick={(e) => e.stopPropagation()}
        style={{ background: "var(--bg)", border: "1px solid var(--border)", borderRadius: 10,
          width: "min(680px, 94vw)", maxHeight: "88vh", overflow: "auto",
          boxShadow: "0 12px 40px oklch(30% 0.04 255 / .4)" }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12,
          padding: "12px 16px", borderBottom: "1px solid var(--border)", position: "sticky", top: 0,
          background: "var(--bg)" }}>
          <b>Deck Analysis{report && !report.empty ? ` (${report.deck_size} cards)` : ""}</b>
          <button onClick={onClose} aria-label="Close"
            style={{ padding: "4px 10px", border: "1px solid var(--border)", borderRadius: 4, cursor: "pointer" }}>
            close (Esc)
          </button>
        </div>

        {!report ? (
          <div style={{ padding: 24, color: "var(--muted)" }}>analyzing…</div>
        ) : report.empty ? (
          <div style={{ padding: 24, color: "var(--muted)" }}>no deck available on this screen</div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 18, padding: 16 }}>
            {/* Volume + axes */}
            <div style={{ display: "flex", gap: 20, alignItems: "center" }}>
              <Radar d={report.scores.damage} b={report.scores.block} v={report.scores.velocity} />
              <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 8 }}>
                <div style={{ display: "flex", alignItems: "baseline", gap: 8 }}>
                  <span style={{ fontSize: 28, fontWeight: 700 }} title="Overall deck strength: the three axes multiplied, shown as their geometric mean. A deck weak on one axis can't hide behind the others.">{report.scores.volume}</span>
                  <span style={{ color: "var(--muted)", fontSize: 11 }}>
                    volume · Damage × Block × Velocity (geometric mean, 0-100)
                  </span>
                </div>
                {AXES.map((a) => (
                  <Bar key={a.key} label={a.label} value={report.scores[a.key]} color={a.color} help={a.help} />
                ))}
                <span style={{ fontSize: 10, color: "var(--muted)", lineHeight: 1.4 }}>{SCORE_HELP}</span>
              </div>
            </div>

            {/* Warnings */}
            <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
              {report.warnings.map((w, i) => (
                <div key={i} style={{ fontSize: 12, padding: "4px 8px", borderRadius: 4,
                  border: `1px solid ${w.severity === "warn" ? "var(--hp)" : "var(--border)"}`,
                  color: w.severity === "warn" ? "var(--hp)" : "var(--fg)" }}>
                  {w.severity === "warn" ? "⚠ " : "· "}{w.text}
                </div>
              ))}
            </div>

            {/* Archetype membership (spec §10): dose position on the measured curve */}
            {membership.length > 0 && (
              <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                <div style={{ fontSize: 12, fontWeight: 600 }}
                  title="Archetypes your deck is building, and where you sit on each one's measured combat dose-response curve. ⚡ = the next piece turns super-additive.">
                  Archetypes
                </div>
                {membership.map((m) => {
                  const total = m.archetype.cards.length;
                  const color = m.archetype.tag === "confirmed"
                    ? "oklch(68% 0.13 145)"
                    : m.archetype.tag === "undiscovered"
                      ? "oklch(62% 0.16 300)"
                      : "var(--muted)";
                  return (
                    <div key={m.archetype.id} style={{ fontSize: 12, display: "flex", gap: 8, alignItems: "baseline" }}>
                      <span style={{ color, fontWeight: 600 }}>{m.archetype.label}</span>
                      <span style={{ color: "var(--muted)" }}>
                        {m.k} of {total} pieces
                        {m.marginalNext !== 0 && (
                          <>
                            {" · "}next piece {m.marginalNext > 0 ? "+" : ""}{m.marginalNext.toFixed(1)}
                            {m.superAdditiveNext ? " ⚡ super-additive" : ""}
                          </>
                        )}
                      </span>
                    </div>
                  );
                })}
              </div>
            )}

            {trend && trend.length > 1 && (
              <Trend points={trend} onSeek={onSeek ? (i) => { onSeek(i); onClose(); } : undefined} />
            )}

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 18 }}>
              <Section title="Damage">
                <span style={{ fontSize: 12 }}
                  title="Simulated average damage dealt per turn over a multi-turn fight — single-target (boss) and vs 3 enemies (hallway). Scaling and AOE are already baked in.">
                  <b>{report.damage.single_target}</b>/turn single-target ·
                  {" "}<b>{report.damage.vs_group}</b>/turn vs 3
                </span>
                <span style={{ fontSize: 11, color: "var(--muted)" }}>scaling</span>
                <Chips items={report.damage.scaling_cards} empty="no damage scaling" />
                {report.damage.aoe_attacks.length > 0 && (
                  <>
                    <span style={{ fontSize: 11, color: "var(--muted)" }}>AOE</span>
                    <Chips items={report.damage.aoe_attacks} empty="" />
                  </>
                )}
              </Section>

              <Section title="Block">
                <span style={{ fontSize: 12 }}
                  title="Simulated average block gained per turn (block engines and Dexterity baked in).">
                  <b>{report.block.per_turn}</b> block/turn
                </span>
                <span style={{ fontSize: 11, color: "var(--muted)" }}>scaling</span>
                <Chips items={report.block.scaling_cards} empty="no block engine" />
              </Section>

              <Section title="Velocity">
                <span style={{ fontSize: 12 }}
                  title="Simulated cards played per turn, and how many of those your draw/energy engines buy over the same deck on raw energy.">
                  ≈ <b>{report.velocity.cards_per_turn}</b> cards played/turn
                  <span style={{ color: "var(--muted)" }}>
                    {" "}(+{report.velocity.extra_per_turn} from engines)
                  </span>
                </span>
                <span style={{ fontSize: 11, color: "var(--muted)" }}>{report.velocity.engine_cards} engine cards</span>
                <div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
                  {Object.entries(report.velocity.tags).length === 0 ? (
                    <span style={{ color: "var(--muted)", fontSize: 11 }}>no draw / energy / replay</span>
                  ) : (
                    Object.entries(report.velocity.tags).map(([k, n]) => (
                      <span key={k} style={{ fontSize: 11, padding: "1px 6px", border: "1px solid var(--border)",
                        borderRadius: 4, background: "var(--panel)" }}>{VEL_LABELS[k] ?? k} ×{n}</span>
                    ))
                  )}
                </div>
              </Section>

              <Section title="Composition">
                <div style={{ fontSize: 11, display: "flex", flexWrap: "wrap", gap: 8 }}>
                  {Object.entries(report.composition.by_type).map(([t, n]) => (
                    <span key={t}>{t} {n}</span>
                  ))}
                </div>
                <span style={{ fontSize: 11, color: "var(--muted)" }}>
                  dead {report.composition.dead_cards} · unupgraded basics {report.composition.unupgraded_basics}
                </span>
                {/* energy curve */}
                <div style={{ display: "flex", alignItems: "flex-end", gap: 6, height: 48, marginTop: 4 }}>
                  {curveKeys.map((k) => (
                    <div key={k} style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 2 }}>
                      <div style={{ width: 18,
                        height: `${(report.composition.energy_curve[k] / curveMax) * 36}px`,
                        minHeight: 2, background: "oklch(70% 0.13 240)", borderRadius: "2px 2px 0 0" }} />
                      <span style={{ fontSize: 10, color: "var(--muted)" }}>{k}</span>
                    </div>
                  ))}
                </div>
              </Section>
            </div>

            <span style={{ fontSize: 10, color: "var(--muted)" }}>
              From simulating the deck over multiple turns ({report.assumptions.samples} rollouts,
              {" "}{report.assumptions.energy} energy, {report.assumptions.hand}-card hands). Models attacks,
              block, Strength/Dex, per-turn powers, draw/energy and exhaust; not yet orbs/poison/summons.
              Heuristics for learning, not a verdict.
            </span>
          </div>
        )}
      </div>
    </div>
  );
}
