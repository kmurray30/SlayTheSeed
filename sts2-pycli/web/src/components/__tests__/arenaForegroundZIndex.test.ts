// Reads game-skin.css off disk with node:fs, so it lives in the
// tsconfig.node-tests project alongside sfxMapCoverage.test.ts and
// shopMerchantAnchor.test.ts -- see that project's comment for why @types/node
// cannot be scoped with a triple-slash directive instead.
//
// It has to read the FILE. jsdom does not apply the stylesheet, so a rendered
// `.gs-arena-fg` reports no z-index whether or not the rule sets one; and
// vitest stubs CSS imports (`?raw` included, handing back an empty string), so
// a `?raw` version of this test would pass on any stylesheet at all.
import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const SKIN_CSS = readFileSync(resolve(__dirname, "../../game-skin.css"), "utf8");

/**
 * NOTHING IN THE ROOM MAY COVER A COMBATANT'S READOUTS.
 *
 * `.gs-actors` — the layer every fighter is placed on — is `z-index: 1`, so any
 * z-index on the room's foreground layer puts painted scenery over the HP bar,
 * the nameplate and the buff chips that hang under a fighter. It shipped at
 * `z-index: 3` and did exactly that: the
 * Underdocks' netting ran across both fighters' chip rows, and the deeper the
 * stack the more of it went under the art.
 *
 * The game is unambiguous here. `combat_room.tscn` orders `BgContainer` --
 * which is where the background scene's own `Foreground` node lives -- ahead
 * of `AllyContainer`/`EnemyContainer`, with no `z_index` on any of them, so
 * room art is never drawn over a creature. `NCreature` then lifts its own
 * `_stateDisplay` to `ZIndex = 1` so the readout clears even the creature's
 * own sprite.
 *
 * With no z-index the layer is an `auto` sibling of `.gs-arena-bg` and lands
 * where it belongs: over the far stack (DOM order) and under every combatant.
 */
describe(".gs-arena-fg", () => {
  it("declares no z-index, so it can never paint over a combatant", () => {
    const block = /\.gs-arena-fg\s*\{([^}]*)\}/.exec(SKIN_CSS);
    expect(block, ".gs-arena-fg rule not found in game-skin.css").not.toBeNull();
    expect(block![1]).not.toMatch(/z-index/);
  });
});
