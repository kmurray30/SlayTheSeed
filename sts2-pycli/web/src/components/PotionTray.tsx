import type { Potion, Score } from "../api";

/**
 * The combat potion belt, styled as the game's tray: a recessed strip of
 * circular sockets (one per slot), the potion sprite sitting in each filled
 * socket. Name, resolved description, the win-prob hint and the Use/Discard
 * actions live in a popover that opens on hover or keyboard focus — the
 * controls are ALWAYS in the DOM (visibility is pure CSS), so replay tests
 * and assistive tech see the same tree as live play. In replay (no handlers)
 * the popover is display-only.
 */
export default function PotionTray({
  potions,
  slots,
  scores,
  onUse,
  onDiscard,
}: {
  potions: Potion[];
  slots?: number;
  scores?: Score[];
  onUse?: (slot: number) => void;
  onDiscard?: (slot: number) => void;
}) {
  // Render at least the owned potions; pad to `slots` empty sockets if known.
  const total = Math.max(potions.length, slots ?? potions.length);
  const tiles = Array.from({ length: total }, (_, i) => potions[i]);

  return (
    <div className="gs-potiontray" data-testid="potion-tray">
      {tiles.map((pt, slot) => {
        if (!pt) {
          return (
            <div key={slot} className="gs-potion-socket gs-potion-empty"
              aria-label="empty potion slot" />
          );
        }
        // The recommender scores a potion as stable_idx -100 - slot.
        const sc = scores?.find((s) => s.stable_idx === -100 - slot);
        return (
          <div key={slot} className={`gs-potion${sc?.is_best ? " gs-potion-best" : ""}`}>
            <div className="gs-potion-socket gs-potion-filled" tabIndex={0}
              role="img" aria-label={pt.name}>
              {pt.image
                ? <img className="gs-potion-art" src={pt.image} alt="" draggable={false} />
                : <span className="gs-potion-glyph" aria-hidden="true">◉</span>}
              {sc && <span className="gs-potion-win">{sc.win_prob.toFixed(2)}</span>}
            </div>
            <div className="gs-potion-pop" role="tooltip">
              <div className="gs-potion-popin">
                <div className="gs-potion-popname">{pt.name}</div>
                {pt.description && <div className="gs-potion-popdesc">{pt.description}</div>}
                {(onUse || onDiscard) && (
                  <div className="gs-potion-actions">
                    {onUse && (
                      <button type="button" className="gs-potion-btn" onClick={() => onUse(slot)}>
                        use
                      </button>
                    )}
                    {onDiscard && (
                      <button type="button" className="gs-potion-btn gs-potion-btn-dim"
                        aria-label={`discard ${pt.name}`} onClick={() => onDiscard(slot)}>
                        discard
                      </button>
                    )}
                  </div>
                )}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
