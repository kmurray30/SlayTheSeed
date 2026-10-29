import { useState } from "react";
import type { Relic } from "../api";

/**
 * A horizontal tray of owned relics. Each relic shows its icon (or a name
 * chip when art is missing); hovering or focusing one reveals a popover with
 * the relic's name and resolved description. Relics persist for the whole run,
 * so this is rendered above every screen — not just combat.
 *
 * `skin="hud"` renders the game-band variant used by GameHud: bare relic art
 * at game size (no chip borders, no mono font), same tooltip behavior.
 */
export default function RelicTray({ relics, skin }: { relics?: Relic[]; skin?: "hud" }) {
  const [active, setActive] = useState<number | null>(null);
  const hud = skin === "hud";
  if (!relics || relics.length === 0) return null;
  return (
    <div className={hud ? "gs-hud-relictray" : "mono"} data-testid="relic-tray"
      style={hud ? undefined : { display: "flex", gap: 6, alignItems: "center", flexWrap: "wrap" }}>
      {relics.map((r, i) => (
        <div key={i} style={{ position: "relative", display: "inline-flex" }}>
          <button
            type="button"
            className={hud ? "gs-hud-relic" : undefined}
            aria-label={r.name}
            title={r.name}
            onMouseEnter={() => setActive(i)}
            onMouseLeave={() => setActive((cur) => (cur === i ? null : cur))}
            onFocus={() => setActive(i)}
            onBlur={() => setActive((cur) => (cur === i ? null : cur))}
            onClick={() => setActive((cur) => (cur === i ? null : i))}
            style={hud ? undefined : { padding: r.image ? 1 : "3px 7px", border: "1px solid var(--border)",
              borderRadius: 4, background: "var(--bg)", cursor: "pointer", lineHeight: 0,
              display: "inline-flex", alignItems: "center" }}>
            {r.image
              ? <img src={r.image} alt={r.name} style={hud ? undefined : { height: 22, display: "block" }} />
              : <span className={hud ? "gs-hud-relicname" : undefined}
                  style={hud ? undefined : { fontSize: 11, lineHeight: 1.2 }}>{r.name}</span>}
          </button>
          {active === i && (
            <div role="tooltip"
              // The tray sits at the right of the screen, so a left-anchored
              // popover overflows the viewport (horizontal scrollbar). Anchor
              // the right edge instead so it grows leftward and stays on-screen.
              style={{ position: "absolute", top: "calc(100% + 6px)", right: 0, zIndex: 30,
                width: 220, padding: "8px 10px", background: "var(--panel)",
                border: "1px solid var(--border)", borderRadius: 6,
                boxShadow: "0 6px 18px oklch(40% 0.03 255 / .25)" }}>
              <div style={{ fontWeight: 650, fontSize: 12, marginBottom: r.description ? 4 : 0 }}>{r.name}</div>
              {r.description && (
                <div style={{ fontSize: 11, lineHeight: 1.4, color: "var(--muted)", whiteSpace: "pre-line" }}>
                  {r.description}
                </div>
              )}
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
