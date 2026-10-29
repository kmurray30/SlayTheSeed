import type { Power } from "../api";
import { DEFAULT_POWER_BOX, POWER_GAP, powerColumns } from "../powerRow";

/** Row(s) of buff/debuff icons with stack counts; falls back to a labelled chip
 *  when the power has no art.
 *
 *  `boxWidth` is the creature's `Visuals.Bounds` width (plus the game's 25px
 *  margin) in the same px this row is drawn in — `NPowerContainer
 *  .SetCreatureBounds`. It decides only how WIDE a row may run; the two-row cap
 *  above is what keeps the block off the hand strip below. */
export default function Powers({
  powers, size = 26, boxWidth = DEFAULT_POWER_BOX,
}: { powers?: Power[]; size?: number; boxWidth?: number }) {
  if (!powers || powers.length === 0) return null;
  const cols = Math.min(powers.length, powerColumns(powers.length, boxWidth, size));
  return (
    // A grid, not a wrapping flex row: the column count is the game's answer
    // above, not whatever happens to fit the box CSS gives this element (which
    // is how a narrow creature ended up with a three-row stack — see
    // `.gs-powers`, which now sizes to its content).
    <div style={{ display: "grid", gridTemplateColumns: `repeat(${cols}, ${size}px)`,
      gap: POWER_GAP, justifyContent: "center", justifyItems: "center" }}>
      {powers.map((p) => (
        <div key={p.name} title={`${p.name}${p.description ? ` — ${p.description}` : ""}`}
          style={{ position: "relative", display: "flex", alignItems: "center" }}>
          {p.image ? (
            <img src={p.image} alt={p.name} style={{ width: size, height: size, display: "block" }} />
          ) : (
            <span style={{ fontFamily: "var(--game-font)", fontSize: 10, padding: "1px 6px", borderRadius: 10,
              border: "1px solid var(--border)", background: "var(--panel)" }}>
              {p.name}
            </span>
          )}
          {p.image && (
            <span style={{ fontFamily: "var(--game-font)", fontVariantNumeric: "tabular-nums",
              position: "absolute", right: -3, bottom: -3, fontSize: 10, fontWeight: 700,
              lineHeight: 1, padding: "1px 3px", borderRadius: 6, color: "var(--text)",
              background: "var(--bg)", border: "1px solid var(--text)",
              textShadow: "0 1px 2px oklch(0% 0 0 / 0.8)" }}>
              {p.display_amount ?? p.amount}
            </span>
          )}
        </div>
      ))}
    </div>
  );
}
