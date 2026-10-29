import { render } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import type { CrabArm } from "../KaiserCrabGroup";
import type { Enemy } from "../../api";
import type { DissolveEffect } from "../../effects/manifest";

/**
 * Task 14 review finding 1: `DecimillipedeGroup.tsx`'s chrome-only
 * `<Combatant>` overlay (the "Targetable overlays" block) receives `dying`
 * but not `dissolve`, even though the segment's OWN `<SpineSprite>` (drawn
 * separately, just above it) does. So `dyingChromeFadeSeconds` fell back to
 * `DEFAULT_DYING_FADE_SECONDS` (0.9s) for the overlay's HP/name/intent chrome
 * while the segment's body was still visibly mid-dissolve for ~1.5s longer,
 * and `Board.tsx`'s corpse-removal timer (which uses the manifest's real
 * `dissolveSeconds`) kept the corpse around the whole time.
 */
vi.mock("@esotericsoftware/spine-webgl", () => {
  class Vector2 {
    constructor(public x = 0, public y = 0) {}
    set(x: number, y: number) { this.x = x; this.y = y; return this; }
  }
  class RegionAttachment { constructor(public name = "region") {} }
  class MeshAttachment { constructor(public name = "mesh") {} }
  class Skin { constructor(public name: string) {} addSkin() {} getAttachment() { return null; } }
  const BOUNDS = { x: -500, y: 0, w: 1000, h: 800 };
  class Skeleton {
    slots: unknown[] = [];
    constructor(public data: unknown) {}
    setSkin() {} setSlotsToSetupPose() {} setToSetupPose() {}
    update() {} updateWorldTransform() {}
    getBounds(off: Vector2, size: Vector2) {
      off.set(BOUNDS.x, BOUNDS.y); size.set(BOUNDS.w, BOUNDS.h);
    }
  }
  class AnimationStateData { constructor(public skeletonData: unknown) {} }
  class AnimationState {
    constructor(public data: AnimationStateData) {}
    setAnimation() { return {}; } addAnimation() { return {}; } clearTracks() {}
    update() {} apply() {}
  }
  const skeletonData = {
    animations: [{ name: "idle_loop", duration: 1 }],
    slots: [], skins: [], defaultSkin: null,
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
      const c = {
        assetManager: { loadBinary() {}, loadTextureAtlas() {}, hasErrors: () => false, require: () => ({}) },
        gl: null, renderer: null,
      };
      config.app.loadAssets?.(c);
      config.app.initialize?.(c);
    }
    dispose() {}
  }
  return {
    Vector2, RegionAttachment, MeshAttachment, Skin, Skeleton,
    AnimationState, AnimationStateData, AtlasAttachmentLoader, SkeletonBinary,
    SpineCanvas, Physics: { update: 1 }, ResizeMode: { Expand: 1 },
  };
});

const { default: DecimillipedeGroup } = await import("../DecimillipedeGroup");

const dissolveEffect = (dissolveSeconds: number) => ({ dissolveSeconds }) as DissolveEffect;

const dyingSegment = (part: string, index: number): CrabArm => ({
  c: {
    index, name: "Decimillipede", hp: 0, max_hp: 60, block: 0,
    rig: "monster_rigs/decimillipede_segment_back/decimillipede_segment_back",
  } as unknown as Enemy,
  part, dataIndex: index, actionAnim: "die", holdLast: true, dying: true,
  dissolve: { effect: dissolveEffect(1.4), rev: "r1", startedAt: 0 },
});

describe("DecimillipedeGroup — a dying segment's chrome fade duration", () => {
  it("carries the manifest's own dissolve duration, not the 0.9s fallback", () => {
    const { container } = render(
      <DecimillipedeGroup segments={[dyingSegment("back", 0)]} />,
    );
    const wrap = container.querySelector(".gs-combatant") as HTMLElement | null;
    expect(wrap, ".gs-combatant overlay not found").not.toBeNull();
    expect(wrap!.style.getPropertyValue("--gs-dying-ms")).toBe("1.4s");
  });
});
