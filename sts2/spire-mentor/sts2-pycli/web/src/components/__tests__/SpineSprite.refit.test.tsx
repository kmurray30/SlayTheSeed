import { render } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

/**
 * A rig that is too big for the arena is fitted to `maxBodyH`, and `maxBodyH` is
 * a LIVE measurement of the arena (Board re-measures it on every arena resize —
 * the hand strip's height feeds it). Refitting must therefore be a pure resize:
 * the same loaded skeleton drawn into a differently sized canvas.
 *
 * It used to be a dependency of the WebGL load effect, so a refit disposed the
 * canvas and re-fetched / re-parsed / re-scanned the whole rig. On the big
 * bosses (Queen, Kaiser Crab — the only rigs `maxBodyH` actually clamps) that is
 * a second of the boss blinking out and coming back at a new size, with its
 * layout box (`boxH`, and `boxW` = 0.7 * `boxH`) moving the whole column under
 * it. That is the "boss shifts location for a second" report.
 */

// One shared record of how many times the WebGL canvas was constructed.
const constructions: string[] = [];

vi.mock("@esotericsoftware/spine-webgl", () => {
  class Vector2 {
    constructor(public x = 0, public y = 0) {}
    set(x: number, y: number) { this.x = x; this.y = y; return this; }
  }
  class RegionAttachment { constructor(public name = "region") {} }
  class MeshAttachment { constructor(public name = "mesh") {} }
  class Skin {
    constructor(public name: string) {}
    addSkin() {}
    getAttachment() { return null; }
  }
  // Bounds: 1000 x 4000 world units, origin at (-500, 0) — a Queen-shaped rig
  // (very tall, so `maxBodyH` binds).
  const BOUNDS = { x: -500, y: 0, w: 1000, h: 4000 };
  class Skeleton {
    slots: unknown[] = [];
    constructor(public data: unknown) {}
    setSkin() {}
    setSlotsToSetupPose() {}
    setToSetupPose() {}
    update() {}
    updateWorldTransform() {}
    getBounds(off: Vector2, size: Vector2) {
      off.set(BOUNDS.x, BOUNDS.y);
      size.set(BOUNDS.w, BOUNDS.h);
    }
  }
  class AnimationStateData { constructor(public skeletonData: unknown) {} }
  class AnimationState {
    constructor(public data: AnimationStateData) {}
    setAnimation() { return {}; }
    addAnimation() { return {}; }
    clearTracks() {}
    update() {}
    apply() {}
  }
  const skeletonData = {
    animations: [{ name: "idle_loop", duration: 1 }],
    slots: [],
    skins: [],
    defaultSkin: null,
    findAnimation(n: string) { return n === "idle_loop" ? { name: n, duration: 1 } : null; },
    findSkin() { return null; },
  };
  class AtlasAttachmentLoader { constructor(public atlas: unknown) {} }
  class SkeletonBinary {
    constructor(public loader: unknown) {}
    readSkeletonData() { return skeletonData; }
  }
  class SpineCanvas {
    constructor(public canvas: HTMLCanvasElement, public config: { app: Record<string, (c: unknown) => void> }) {
      constructions.push("new");
      const c = {
        assetManager: { loadBinary() {}, loadTextureAtlas() {}, hasErrors: () => false, require: () => ({}) },
        // render() bails before touching these when it has no frame to draw.
        gl: null,
        renderer: null,
      };
      config.app.loadAssets?.(c);
      config.app.initialize?.(c);
    }
    dispose() {}
  }
  return {
    Vector2, RegionAttachment, MeshAttachment, Skin, Skeleton,
    AnimationState, AnimationStateData, AtlasAttachmentLoader, SkeletonBinary,
    SpineCanvas,
    Physics: { update: 1 },
    ResizeMode: { Expand: 1 },
  };
});

const { default: SpineSprite } = await import("../SpineSprite");

beforeEach(() => { constructions.length = 0; });

/** The rendered canvas's CSS height in px. */
const canvasH = (c: HTMLElement) =>
  parseFloat((c.querySelector("canvas") as HTMLCanvasElement).style.height);

describe("SpineSprite refit", () => {
  it("re-fits to a new maxBodyH without rebuilding the rig", () => {
    const { container, rerender } = render(
      <SpineSprite rigBase="/rig/queen/queen" worldScale={0.3} boundsH={1670} maxBodyH={488} />,
    );
    expect(constructions).toHaveLength(1);
    // The authored scale would draw this rig 4000 * 0.3 * 0.5 = 600px tall, so
    // `maxBodyH` binds: 488px of body, plus this rig's overscan margins.
    const before = canvasH(container);
    expect(before).toBeGreaterThan(488);

    rerender(
      <SpineSprite rigBase="/rig/queen/queen" worldScale={0.3} boundsH={1670} maxBodyH={448} />,
    );
    // The rig is the same one, redrawn smaller — not fetched and parsed again.
    expect(constructions).toHaveLength(1);
    expect(canvasH(container)).toBeCloseTo(before * (448 / 488), 1);
  });

  it("still rebuilds when the rig itself changes", () => {
    const { rerender } = render(<SpineSprite rigBase="/rig/a/a" maxBodyH={400} />);
    expect(constructions).toHaveLength(1);
    rerender(<SpineSprite rigBase="/rig/b/b" maxBodyH={400} />);
    expect(constructions).toHaveLength(2);
  });
});
