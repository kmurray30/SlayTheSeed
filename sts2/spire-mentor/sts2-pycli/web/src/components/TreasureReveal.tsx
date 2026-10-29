import type { RoomReveal } from "../api";
import { assetUrl } from "../api";
import SpineSprite from "./SpineSprite";
import RichText from "./RichText";
import "../skins/event.css";

/** Chest height in px — a constant, not a measurement, for the same reason
 *  EventScreen's is: `fallbackHeight` sits in SpineSprite's load-effect deps,
 *  so a live value rebuilds its WebGL context on every resize. */
const CHEST_H = 300;

/**
 * "What's Inside?" — the panel that makes a treasure room visible.
 *
 * A chest has no decision in it. The engine opens it on room entry, pays the
 * gold and hands you the relic in one step (`resolve_treasure_room`, C# parity
 * with AutoSlay's always-grabbing `TreasureRoomHandler`), then jumps to the
 * map. Nothing reached the UI, so walking into a chest room showed nothing at
 * all — the whole room passed as three lines in the event log.
 *
 * So this reports, it does not gate: the relic is already yours when the panel
 * opens, and dismissing it is the only thing you can do. The chest rig plays
 * its one clip (which IS the opening) once on mount and holds the last frame,
 * so you arrive on an open chest rather than being asked to open one you
 * already opened.
 */
export default function TreasureReveal({ reveal, onDismiss }: {
  reveal: RoomReveal;
  onDismiss: () => void;
}) {
  const act = Math.min(3, Math.max(1, reveal.act || 1));
  const relic = reveal.relic;
  return (
    <div
      className="gs-scene gs-treasure gs-treasure-reveal"
      role="dialog"
      aria-label="What's inside the chest"
      onClick={onDismiss}
    >
      <div className="gs-treasure-banner">
        <img src={assetUrl("ui/reward_screen/reward_banner.png")} alt="" aria-hidden="true" />
        <h2 className="gs-treasure-title">What's Inside?</h2>
      </div>
      <div className="gs-treasure-rig" data-act={act}>
        <SpineSprite
          rigBase={assetUrl(`room_rigs/treasure_act_${act}/treasure_act_${act}`)}
          holdSetup
          actionAnim="animation"
          actionNonce={1}
          holdLast
          fallbackHeight={CHEST_H}
          fallback={assetUrl("run_history/treasure.png")}
          alt=""
        />
      </div>
      <div className="gs-treasure-haul">
        {relic ? (
          <div className="gs-treasure-relic">
            {relic.image && <img src={relic.image} alt="" aria-hidden="true" />}
            <div className="gs-treasure-plaque">
              <div className="gs-treasure-relicname">{relic.name}</div>
              {relic.description && (
                <div className="gs-treasure-relictext">
                  <RichText text={relic.description} />
                </div>
              )}
            </div>
          </div>
        ) : (
          // Silver Crucible vetoes the first chest outright. An empty chest is
          // a real outcome, and saying so beats a panel with a hole in it.
          <div className="gs-treasure-plaque gs-treasure-empty">The chest was empty.</div>
        )}
        {reveal.gold > 0 && <div className="gs-treasure-gold">+{reveal.gold} gold</div>}
      </div>
      {/* The whole panel is the dismiss target; this is the affordance that
          says so, not a second control. */}
      <button className="gs-treasure-dismiss" onClick={onDismiss}>Continue</button>
    </div>
  );
}
