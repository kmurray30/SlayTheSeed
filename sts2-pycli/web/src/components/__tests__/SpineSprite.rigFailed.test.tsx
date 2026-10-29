import { render } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

/**
 * Task 14 review round 3: the dying-creature CSS fade is scoped away from the
 * sprite because the WebGL dissolve owns it — but a rig whose assets fail to
 * load has no WebGL sprite at all, only the `fallback` `<img>`, which the
 * dissolve post-pass never touches. `Combatant` cannot see that state (it
 * passed a rig and a dissolve; the failure happens inside), so `SpineSprite`
 * marks its own root `gs-rig-failed` and `game-skin.css` fades that subtree
 * again. Without it a failed-rig corpse holds full opacity for the whole
 * dissolve and then pops out at removal.
 */
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
  class Skeleton { constructor(public data: unknown) {} }
  class AnimationStateData { constructor(public skeletonData: unknown) {} }
  class AnimationState { constructor(public data: AnimationStateData) {} }
  class AtlasAttachmentLoader { constructor(public atlas: unknown) {} }
  class SkeletonBinary {
    constructor(public loader: unknown) {}
    readSkeletonData() { return null; }
  }
  class SpineCanvas {
    constructor(public canvas: HTMLCanvasElement, public config: { app: Record<string, (c: unknown) => void> }) {
      const c = {
        // The rig's atlas/skeleton 404s: `hasErrors()` is how `SpineSprite`
        // learns that, and it is the ONLY path to `failed` that does not
        // depend on the shape of a half-loaded skeleton.
        assetManager: { loadBinary() {}, loadTextureAtlas() {}, hasErrors: () => true, require: () => ({}) },
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

describe("a rig that failed to load", () => {
  it("marks its root gs-rig-failed so the dying fade is not left to a dissolve that cannot run", () => {
    const { container } = render(
      <SpineSprite rigBase="/rig/gone/gone" fallback="/img/gone.png" alt="Gone" maxBodyH={400} />,
    );
    expect(container.querySelector(".gs-rig-failed")).not.toBeNull();
    // The stand-in the fade has to cover, and the reason the sprite is not
    // the dissolve's to own here.
    expect(container.querySelector("img")).not.toBeNull();
  });
});
