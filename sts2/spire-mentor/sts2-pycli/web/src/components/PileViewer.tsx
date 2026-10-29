import { useEffect, useLayoutEffect, useState } from "react";
import type { Card } from "../api";
import CardFrame from "./CardFrame";
import "../skins/pileviewer.css";

/** The measured GAME-SCENE rectangle the takeover dims (viewport pixels). */
type Frame = { top: number; left: number; width: number; height: number };

/**
 * The game's screen-explainer sentence, per pile — the large white line the
 * real client paints at the bottom of its pile screen ("Cards are drawn from
 * here at the start of each turn." for the draw pile). Keyed by the caller's
 * title, lowercased.
 */
const PILE_SENTENCE: Record<string, string> = {
  "draw pile": "Cards are drawn from here at the start of each turn.",
  "discard pile": "Cards are discarded here once they have been played.",
  "exhaust pile": "Exhausted cards are gone for the rest of this combat.",
  deck: "Every card you carry, wherever this combat has put it.",
};

/**
 * Callers pass a terse debug-flavoured `note`; the caption speaks in the
 * game's voice, so the known ones are re-voiced into the game's own gold
 * parenthetical. Anything unknown passes through verbatim.
 */
const NOTE_VOICE: Record<string, string> = {
  "order hidden — sorted, not draw order": "sorted by rarity — order hidden",
};

/**
 * The game's pile screen (draw / discard / exhaust / deck) as a takeover of
 * the GAME SCENE, the way the real client draws it: the scene under the HUD
 * dims to a near-black, the cards render full-size and legible in one row
 * seated below the frame's middle, a red/gold return banner sits at the lower
 * left, and the two caption lines sit bottom-center — a large white sentence
 * over a gold parenthetical.
 *
 * The scrim is scoped to the scene rectangle (measured off the drawn HUD band
 * and the screen host below it), never the browser window: in the real game
 * the status bar stays at FULL brightness above the dim, and here the tool
 * column / toolbar / scrubber are workbench chrome that must not be dimmed
 * either. Falls back to a full-viewport scrim when no game band is mounted
 * (gallery, tests).
 *
 * The caption's second line is the epistemics keeper: the draw pile is
 * order-scrambled server-side, so what arrives here never reveals the real
 * draw order. Closes on backdrop click, the return banner, or Escape.
 */
export default function PileViewer({
  title,
  cards,
  note,
  onClose,
}: {
  title: string;
  cards: Card[];
  note?: string;
  onClose: () => void;
}) {
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") { e.preventDefault(); onClose(); }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  // ---- scope the takeover to the game scene ------------------------------
  // App.tsx renders <GameHud/> (`.gs-hud`) and then the screen's scroll host
  // as its next sibling — that host IS the game viewport (left of the tool
  // column, above the scrubber). Measured, not hard-coded, so a resized
  // window / hidden panel keeps the scrim aligned.
  const [frame, setFrame] = useState<Frame | null>(null);
  useLayoutEffect(() => {
    function measure() {
      const hud = document.querySelector<HTMLElement>(".gs-hud");
      const host = hud?.nextElementSibling as HTMLElement | null;
      if (!hud || !host) { setFrame(null); return; }
      const band = hud.getBoundingClientRect();
      const r = host.getBoundingClientRect();
      // The dim starts at the band's bottom edge (the game dims everything
      // below its status bar and nothing above it).
      const top = Math.max(r.top, band.bottom);
      const height = r.bottom - top;
      if (r.width < 360 || height < 260) { setFrame(null); return; }
      setFrame({ top, left: r.left, width: r.width, height });
    }
    measure();
    window.addEventListener("resize", measure);
    return () => window.removeEventListener("resize", measure);
  }, []);

  const sentence = PILE_SENTENCE[title.trim().toLowerCase()] ?? `${title}.`;
  const count = `${cards.length} ${cards.length === 1 ? "card" : "cards"}`;
  const voiced = note ? (NOTE_VOICE[note] ?? note) : null;

  return (
    <div
      className="gs-pileview"
      role="dialog"
      aria-label={title}
      onClick={onClose}
      style={
        frame
          ? {
              top: `${frame.top}px`,
              left: `${frame.left}px`,
              width: `${frame.width}px`,
              height: `${frame.height}px`,
              // the caption / banner / row anchors are all fractions of the
              // scene height, so they scale with the viewport like the game's
              ["--gs-pv-h" as string]: `${frame.height}px`,
            }
          : undefined
      }
    >
      <div className="gs-pileview-scroll">
        {cards.length === 0 ? (
          <div className="gs-pileview-empty">This pile is empty.</div>
        ) : (
          <div className="gs-pileview-grid">
            {cards.map((c, i) => (
              // cards are inert here (as before) — a click on one must not
              // fall through to the backdrop-close
              <div key={i} className="gs-pileview-cardslot" onClick={(e) => e.stopPropagation()}>
                <CardFrame card={c} width={220} />
              </div>
            ))}
          </div>
        )}
      </div>

      {/* the game's return banner: dark-red pennant, hard gold rim, painted
          cream back-arrow silhouette (sampled off the reference frame:
          rim #f2b601, field #7b1b15, arrow #ffeecd on a #3f0f0a edge) */}
      <button
        type="button"
        className="gs-pileview-return"
        aria-label="Return — close pile view (Esc)"
        title="Return — close pile view (Esc)"
        onClick={(e) => { e.stopPropagation(); onClose(); }}
      >
        <svg viewBox="0 0 104 84" aria-hidden="true" focusable="false">
          {/* dark painted edge under both pieces, then the cream silhouette:
              a chunky triangular head pointing LEFT and a thick tail sweeping
              right and down to a flat cut — the game's back arrow, not a
              keyboard return glyph */}
          <g fill="#3f0f0a" stroke="#3f0f0a" strokeWidth="8" strokeLinejoin="round">
            <path d="M 46 32 C 76 32, 86 46, 86 68" fill="none" strokeWidth="32" />
            <path d="M 8 44 L 48 12 L 48 76 Z" />
          </g>
          <path
            d="M 46 32 C 76 32, 86 46, 86 68"
            fill="none" stroke="#ffeecd" strokeWidth="26" strokeLinecap="butt"
          />
          <path d="M 10 44 L 46 15 L 46 73 Z" fill="#ffeecd" />
        </svg>
      </button>

      <div className="gs-pileview-caption">
        <div className="gs-pileview-line1">{sentence}</div>
        <div className="gs-pileview-line2">
          ({count}{voiced ? `, ${voiced}` : ""})
        </div>
      </div>
    </div>
  );
}
