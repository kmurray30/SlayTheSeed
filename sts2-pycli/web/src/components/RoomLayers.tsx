/**
 * One half of a room's painted layer stack, as elements.
 *
 * The stack used to be a single element carrying a CSS multi-background list.
 * That can place a layer and size it and nothing else — one alpha for the whole
 * element, no orientation — and the game's scenes need both: these rooms mirror
 * a prop by rotating its `Control` a half turn (there being no `flip` on a
 * Control), and seven layers carry an inherited `modulate` tint or alpha (the
 * Insatiable's floor at 0.47, the Queen's teal war-historian mural). See
 * `roomLayerStyle`.
 *
 * INSIDE this component the units are canvas px, 1:1: the outer box is placed
 * and sized by Board, and the inner box is exactly 1920x900 scaled to fit it.
 * That is what lets the layers use the manifest's own numbers.
 */
import type { CSSProperties } from "react";
import { CANVAS_W, CANVAS_H, layerTints, roomLayerPaintStyle, roomLayerStyle, tintFilterId,
  type RoomLayer } from "./roomScenes";

/** `modulate` multiplies the art per channel, and no CSS `filter` shorthand
 *  does that — so each distinct tint gets one `<feColorMatrix>`. Rendered once
 *  per stack, next to the layers that reference it. */
function RoomTintFilters({ tints }: { tints: number[][] }) {
  if (!tints.length) return null;
  return (
    <svg width="0" height="0" aria-hidden="true" style={{ position: "absolute" }}>
      <defs>
        {tints.map((t) => (
          <filter key={tintFilterId(t)} id={tintFilterId(t)} colorInterpolationFilters="sRGB">
            <feColorMatrix
              type="matrix"
              values={`${t[0]} 0 0 0 0  0 ${t[1]} 0 0 0  0 0 ${t[2]} 0 0  0 0 0 1 0`}
            />
          </filter>
        ))}
      </defs>
    </svg>
  );
}

export default function RoomLayers({
  className,
  layers,
  style,
  url,
  testId,
  scene,
}: {
  className: string;
  layers: RoomLayer[];
  /** Position and size of the canvas on the stage — Board's own numbers. */
  style: CSSProperties;
  url: (rel: string) => string;
  testId: string;
  scene?: string;
}) {
  const width = Number(style.width) || 0;
  return (
    <div className={className} aria-hidden="true" data-testid={testId} data-scene={scene}
      style={style}>
      <div
        className="gs-room-canvas"
        style={{
          width: CANVAS_W,
          height: CANVAS_H,
          // The canvas is drawn at whatever box Board gave the parent; scaling
          // by width alone is right because the parent always carries the
          // canvas's own aspect.
          transform: `scale(${width > 0 ? width / CANVAS_W : 1})`,
        }}
      >
        <RoomTintFilters tints={layerTints(layers)} />
        {layers.map((layer, i) => {
          // The paint lives on a CHILD for a tinted additive layer — its tint
          // filter would otherwise cancel its blend mode. See roomLayerStyle.
          const paint = roomLayerPaintStyle(layer);
          const image = { backgroundImage: `url(${url(`rooms/${layer.src}`)})` };
          return (
            <div
              // Two props in a room can be the same file at the same spot only
              // if the extractor's dedup let them through, so src+index is
              // unique.
              key={`${layer.src}|${i}`}
              style={paint ? roomLayerStyle(layer) : { ...roomLayerStyle(layer), ...image }}
            >
              {paint && <div style={{ ...paint, ...image }} />}
            </div>
          );
        })}
      </div>
    </div>
  );
}
