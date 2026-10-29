import { render } from "@testing-library/react";
import { afterAll, describe, expect, it } from "vitest";
import Board from "../Board";
import { INTENT_HEADROOM_PX } from "../../scene";
import { stageZoom } from "../../stageZoom";
import fixture from "./fixture-step.json";
import type { Step } from "../../api";

/**
 * A tablet's compact framing (stageZoom.ts) crops the scene mostly off the
 * top. For the Ironclad and a Cultist that is sky; for the Bygone Effigy — a
 * 588 scene-px statue whose crown, through its own 0.88 camera, sits at scene
 * row ~249 — it was the scythe and the intent chip over it, so the number
 * telling you what it will hit for was the one thing off the screen.
 *
 * Board now pulls the SCENE's framing back per fight (`sceneFramingFor`) as
 * far as the tallest enemy's chip needs, and no further; the cards keep the
 * stage's zoom. What is pinned: the enemy column's top — the crown the chip
 * hangs off — lands at least the chip's headroom below the stage's top edge,
 * an ordinary fight is untouched, and a corpse leaving does not zoom the room.
 */

const LAYOUT_PROPS = ["clientWidth", "clientHeight", "offsetWidth", "offsetHeight"] as const;

/** jsdom's two measurements of the tablet stage: the screen box (`client*`,
 *  read off `.gs-arena-stage`) and the zoom layer's own box (`offset*`, read
 *  off `.gs-stage-scale`, which is `screen / --gs-k`). */
function stubStage(w: number, h: number): void {
  const zoom = stageZoom(w, h);
  const dims: Record<string, number> = {
    clientWidth: w, clientHeight: h,
    offsetWidth: Math.round(w / zoom), offsetHeight: Math.round(h / zoom),
  };
  for (const k of LAYOUT_PROPS) {
    Object.defineProperty(HTMLElement.prototype, k, { configurable: true, get: () => dims[k] });
  }
}

afterAll(() => {
  for (const k of LAYOUT_PROPS) {
    Object.defineProperty(HTMLElement.prototype, k, { configurable: true, get: () => 0 });
  }
});

const effigy = (index = 0) => ({
  index, name: "Bygone Effigy", hp: 127, max_hp: 127, block: 0, image: null,
  rig: "/assets/monster_rigs/bygone_effigy/bygone_effigy", rig_scale: 0.32,
  rig_bounds_h: 600, rig_bounds_w: 360,
  intents: [{ type: "Attack", damage: 20, hits: 1 }],
});
// The Architect: a 488 scene-px rig (crown at row ~252) under NO encounter
// camera, so a fight it shares can lose it without the camera changing.
const architect = (index = 0) => ({
  index, name: "The Architect", hp: 200, max_hp: 200, block: 0, image: null,
  rig: "/assets/monster_rigs/architect/architect", rig_scale: 0.35,
  rig_bounds_h: 629, rig_bounds_w: 467,
  intents: [{ type: "Attack", damage: 12, hits: 1 }],
});
const cultist = (index = 0) => ({
  index, name: "Cultist", hp: 50, max_hp: 50, block: 0, image: null,
  rig: "/assets/monster_rigs/cultists/cultists", rig_scale: 0.28,
  rig_bounds_h: 305, rig_bounds_w: 250,
  intents: [{ type: "Attack", damage: 6, hits: 1 }],
});

const nibbit = (index = 0) => ({
  index, name: "Nibbit", hp: 30, max_hp: 30, block: 0, image: null,
  rig: "/assets/monster_rigs/nibbit/nibbit", rig_scale: 0.28,
  rig_bounds_h: 146, rig_bounds_w: 270,
  intents: [{ type: "Attack", damage: 6, hits: 1 }],
});

function stepWith(enemies: unknown[], floor = 7): Step {
  const base = fixture as unknown as Step;
  return {
    ...base,
    meta: { ...base.meta, act: 1, floor },
    state: { ...base.state, enemies } as Step["state"],
  };
}

/** The enemy columns' tops in layer px — where each rig's crown is drawn,
 *  since the column is hung above the ground line by the rig's height. */
function crownTops(c: Element): Record<string, number> {
  const out: Record<string, number> = {};
  for (const el of c.querySelectorAll<HTMLElement>(".gs-side")) {
    const idx = el.querySelector("[data-enemy-index]")?.getAttribute("data-enemy-index");
    if (idx != null) out[idx] = parseFloat(el.style.top);
  }
  return out;
}
/** The player's column top, the same way. */
function playerTop(c: Element): number {
  const el = c.querySelector<HTMLElement>('[data-combatant="player"]')?.closest<HTMLElement>(".gs-side");
  return parseFloat(el?.style.top ?? "NaN");
}

// An 8.8" tablet in landscape once the chrome is compacted: a stage that
// frames at the full MAX_FRAMING.
const TABLET = [1002, 465] as const;

describe("a tall enemy on a tablet stage", () => {
  it("keeps the Effigy's intent chip on the stage", () => {
    stubStage(...TABLET);
    const top = crownTops(render(<Board step={stepWith([effigy()])} />).container)["0"];
    expect(top).toBeGreaterThanOrEqual(INTENT_HEADROOM_PX);
    // ...and not by pulling all the way back to the pure fit: the crown is
    // not far below the line, so the fight is still framed larger than a
    // monitor's composition of it.
    expect(top).toBeLessThan(INTENT_HEADROOM_PX + 40);
  });

  it("leaves an ordinary fight exactly where the compact framing put it", () => {
    stubStage(...TABLET);
    const cultistTop = crownTops(render(<Board step={stepWith([cultist()])} />).container)["0"];
    // The Cultist's crown already clears the chip's headroom at the full
    // framing, so the cap does not fire: the Ironclad stands exactly where he
    // stands beside any other ordinary enemy.
    expect(cultistTop).toBeGreaterThanOrEqual(INTENT_HEADROOM_PX);
    const p = playerTop(render(<Board step={stepWith([cultist()])} />).container);
    const pNibbit = playerTop(render(<Board step={stepWith([nibbit()])} />).container);
    expect(p).toBe(pNibbit);
    // ...whereas beside the Architect the same Ironclad stands lower and
    // smaller: the whole scene was pulled back, not one column moved. (The
    // Architect rather than the Effigy, whose own camera also moves him.)
    const pTall = playerTop(render(<Board step={stepWith([architect()])} />).container);
    expect(pTall).toBeGreaterThan(p + 40);
  });

  it("holds the framing when the tall one dies", () => {
    stubStage(...TABLET);
    // Same fight (same act/floor), rendered with the Architect and then
    // without: the roster memo keeps its crown, so the Cultist's column does
    // not jump when the corpse leaves.
    const both = stepWith([architect(0), cultist(1)], 11);
    const r = render(<Board step={both} />);
    const before = crownTops(r.container)["1"];
    r.rerender(<Board step={stepWith([cultist(1)], 11)} />);
    const after = crownTops(r.container)["1"];
    expect(after).toBe(before);
    // A fresh fight on another floor with only the Cultist is framed fully.
    const alone = crownTops(render(<Board step={stepWith([cultist(1)], 12)} />).container)["1"];
    expect(alone).toBeLessThan(before);
  });

  it("does not fire on a monitor", () => {
    stubStage(1920, 1002);
    const top = crownTops(render(<Board step={stepWith([effigy()])} />).container)["0"];
    // The Effigy's crown at scene row ~249 less the 78px HUD band, at k = 1.
    expect(top).toBeGreaterThan(150);
    expect(top).toBeLessThan(190);
  });
});
