// Reads game-skin.css off disk with node:fs, so it lives in the
// tsconfig.node-tests project alongside arenaForegroundZIndex.test.ts -- see
// that file's header for why the stylesheet has to be read as a FILE (jsdom
// does not apply it, and vitest stubs `?raw` CSS imports to an empty string).
import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

// Comments stripped: these rules are documented with the very declarations
// they no longer carry, and a naive match would find them in the prose.
const SKIN_CSS = readFileSync(resolve(__dirname, "../../game-skin.css"), "utf8")
  .replace(/\/\*[\s\S]*?\*\//g, "");

const rule = (selector: string): string => {
  const m = new RegExp(`${selector.replace(/[.*+?^${}()|[\\]\\\\]/g, "\\\\$&")}\\s*\\{([^}]*)\\}`)
    .exec(SKIN_CSS);
  expect(m, `${selector} rule not found in game-skin.css`).not.toBeNull();
  return m![1];
};

/**
 * THE GAME DOES NOT GRADE ITS COMBAT ROOMS, SO NEITHER MAY WE (GH #610).
 *
 * A room is a stack of bare `TextureRect`s under a `%BgContainer` with no
 * `CanvasModulate`, no material and no shader; the one `WorldEnvironment` in
 * `scenes/game.tscn` is inert over an LDR 2D canvas. Compositing the extracted
 * PNGs offline at the scene's own 1.44x reproduces a real game screenshot to a
 * MEDIAN 1/255 over the whole frame — there is nothing left for a grade to
 * correct, so any grade is error by construction.
 *
 * This is a guard, not a style preference. Three separate passes had
 * accumulated here, each fitted by eye or against a mis-registered comparison,
 * and together they put the room's ground band 48% below the game's and its
 * side walls ~20% below: a `brightness()/contrast()` on the layer stack (which
 * DARKENS everything under v≈0.19, and an act room lives at 0.05..0.20), a
 * green depth-haze wash, and a vignette plus bottom scrim. Removing all three
 * took every measured region to within half a level of the game's.
 *
 * The room's own air is not a grade and is not covered here: the additive fog
 * sheets come out of the PCK as layers, and `.gs-arena-dust` carries the
 * particle weather of the three rooms that have any.
 *
 * See docs/experiments/2026-08-30-combat-room-grade/.
 */
describe("the combat room's own values", () => {
  it("puts no colour grade on either half of the layer stack", () => {
    for (const selector of [".gs-arena-bg", ".gs-arena-fg"]) {
      expect(rule(selector), `${selector} must not grade the room art`)
        .not.toMatch(/filter\s*:/);
    }
  });

  it("keeps no vignette, edge shadow or scrim over the scene", () => {
    const block = rule("\\.gs-stage-scale::after");
    // The gold ring is the framed viewport's own edge and stays; a gradient or
    // an inset SHADOW (`inset 0 0 <blur>`, as opposed to the ring's
    // `inset 0 0 0 2px`) would be grading the picture again.
    expect(block).not.toMatch(/gradient/);
    expect(block).not.toMatch(/inset 0 0 [1-9]/);
  });

  it("has no global haze layer left to wash every room with one hue", () => {
    expect(SKIN_CSS).not.toMatch(/^\.gs-arena-haze\s*\{/m);
  });
});
