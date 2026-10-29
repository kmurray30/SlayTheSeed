import { describe, expect, it } from "vitest";
import { DESIGN_STAGE_H, DESIGN_STAGE_W, MAX_FRAMING, MAX_FRAMING_PORTRAIT, MIN_STAGE_ZOOM, stageFraming, stageZoom } from "./stageZoom";

/** A stage of the given height with width to spare, so the HEIGHT binds. */
const wide = (h: number) => Math.ceil((h * 1920) / 1002) + 8;

describe("stageZoom", () => {
  it("leaves a desktop window at 1:1", () => {
    // A maximised 1920x1080 window gives the stage ~1052px of height and all
    // 1920 of width, both past the design size. Neither may be scaled — the
    // zoom only ever shrinks, so the art is never blown up past its own
    // resolution.
    expect(stageZoom(1920, 1052)).toBe(1);
    expect(stageZoom(DESIGN_STAGE_W, DESIGN_STAGE_H)).toBe(1);
    expect(stageZoom(2560, 1400)).toBe(1);
  });

  it("shrinks the scene in proportion to a short stage", () => {
    // The whole point: the 470px Ironclad rig has to end up inside the box.
    // (framing 1: the pure fit. With the compact framing on, this stage is
    // drawn at 0.64 and cropped instead — see the framing tests below.)
    const k = stageZoom(wide(501), 501, 1);
    expect(k).toBe(0.5);
    expect(470 * k).toBeLessThan(501);
  });

  it("takes the NARROWER axis, so the hand cannot outgrow the room", () => {
    // 1366x768 leaves the stage ~740px tall: fitting by height alone gave 0.74
    // while the width only supported 0.71, so every design-px thing standing on
    // the scene — the card fan above all — came out ~4% too big for the room it
    // was standing in. On a properly narrow stage the gap is much wider.
    expect(stageZoom(1366, 740, 1)).toBe(0.7);
    expect(stageZoom(1000, 900, 1)).toBe(0.52);
    // Whichever axis binds, the composition fits inside the box — right down
    // to the readability floor, below which the scene is deliberately allowed
    // to oversail a stage that is too small to be worth composing for.
    for (const [w, h] of [[1366, 740], [1000, 900], [1920, 600], [1200, 1200]]) {
      const k = stageZoom(w, h, 1);
      expect(k).toBeGreaterThan(MIN_STAGE_ZOOM);
      expect(DESIGN_STAGE_W * k).toBeLessThanOrEqual(w);
      expect(DESIGN_STAGE_H * k).toBeLessThanOrEqual(h);
    }
  });

  it("frames a tablet stage larger than its fit, and a desktop one not at all", () => {
    // A maximised 1080p window and a 1366x768 laptop fit at 1 and ~0.7: no
    // framing, so nothing a monitor shows today moves.
    expect(stageFraming(1920, 1052)).toBe(1);
    expect(stageFraming(1366, 740)).toBeLessThanOrEqual(1.06);
    // An 8.8" tablet in landscape (1003x627 CSS px) leaves ~1000x500 of stage
    // once the chrome is compacted — a fit of 0.5, framed up to the cap.
    const f = stageFraming(1002, 500);
    expect(f).toBeGreaterThan(1.25);
    expect(f).toBeLessThanOrEqual(MAX_FRAMING);
    expect(stageFraming(1002, 423)).toBe(MAX_FRAMING);
    // The framed zoom is the fit times the framing: the cards and the plate
    // come out that much bigger, and the frame they are laid out for is that
    // much smaller than the design one.
    const k = stageZoom(1002, 500);
    expect(k).toBeGreaterThan(stageZoom(1002, 500, 1));
    expect((DESIGN_STAGE_W / f) * k).toBeLessThanOrEqual(1002 + 1);
    expect((DESIGN_STAGE_H / f) * k).toBeLessThanOrEqual(500 + 1);
  });

  it("frames a portrait stage larger still, blending in past square", () => {
    // A tablet in portrait leaves ~626x800 of stage: the width binds at 0.33,
    // and the height has room for a scene drawn well past 1.35x that.
    expect(stageFraming(626, 800)).toBeGreaterThan(MAX_FRAMING);
    expect(stageFraming(626, 800)).toBeLessThanOrEqual(MAX_FRAMING_PORTRAIT);
    expect(stageFraming(500, 1000)).toBe(MAX_FRAMING_PORTRAIT);
    // Landscape and square stages keep the landscape cap.
    expect(stageFraming(1002, 423)).toBe(MAX_FRAMING);
    expect(stageFraming(600, 600)).toBe(MAX_FRAMING);
    // No jump through square: dragging a 600px-wide window taller moves the
    // framing by small steps only.
    let prev = stageFraming(600, 560);
    for (let h = 560; h <= 1000; h += 4) {
      const f = stageFraming(600, h);
      expect(f).toBeGreaterThanOrEqual(prev);
      expect(f - prev).toBeLessThan(0.03);
      prev = f;
    }
  });

  it("ramps the framing continuously so a resize never jumps", () => {
    let prev = stageFraming(wide(1002), 1002);
    for (let h = 1002; h >= 400; h -= 4) {
      const f = stageFraming(wide(h), h);
      expect(f).toBeGreaterThanOrEqual(prev);
      expect(f - prev).toBeLessThan(0.03);
      prev = f;
    }
    expect(stageFraming(20, 10)).toBe(MAX_FRAMING);
    expect(stageFraming(0, 0)).toBe(1);
  });

  it("scales a tablet-in-landscape stage into the frame", () => {
    // ~460px is what a 1024x640 tablet leaves once the compact chrome rules
    // have handed the board back its pixels.
    expect(470 * stageZoom(1024, 460)).toBeLessThan(460);
  });

  it("never goes below the readability floor", () => {
    expect(stageZoom(20, 10)).toBe(MIN_STAGE_ZOOM);
    expect(stageZoom(0, 0)).toBe(1); // unmeasured box: don't scale on a guess
  });

  it("quantises so a resize drag does not relayout every pixel", () => {
    // Dragging a window edge across 200px of height must not push 200 distinct
    // zooms — each one re-lays-out every card and rig in the scene.
    const seen = new Set<number>();
    for (let h = 400; h <= 600; h++) seen.add(stageZoom(wide(h), h));
    expect(seen.size).toBeLessThanOrEqual(14);
    // ...and each one is an exact 2-decimal number, not 0.6000000000000001.
    for (const k of seen) expect(k).toBe(Number(k.toFixed(2)));
  });
});
