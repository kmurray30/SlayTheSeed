// Reads shop.css off disk with node:fs, so it lives in the tsconfig.node-tests
// project alongside sfxMapCoverage.test.ts rather than in tsconfig.app.json --
// see that project's comment for why @types/node cannot be scoped with a
// triple-slash directive instead.
//
// It has to read the FILE. Vitest stubs CSS imports (`test.css` defaults to
// false), and the stub swallows `?raw` too: `import css from "…/shop.css?raw"`
// hands back an empty string, so a `?raw` version of this test passes on any
// stylesheet at all, including one with the bug in it.
import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const SHOP_CSS = readFileSync(resolve(__dirname, "../shop.css"), "utf8");

/**
 * The merchant sits on a rug that is PAINTED INTO THE BACKDROP ART -- not on
 * `.gs-shop-rug`, the wares mat down on the floor row. He shipped anchored at
 * `top: 10%; right: 2.5%`, a third of a frame above that rug and off its right
 * edge: a hooded figure levitating in the corner of the tent. Compare
 * `docs/experiments/2026-08-22-workbench-visual-gauntlet/refs/game-shop-1.png`,
 * which seats him on the weave with the wares laid out on the near half.
 *
 * A percentage anchor is exact for this, not an approximation. `.gs-shop.gs-
 * scene::before` draws the backdrop with percentage sizing off the bottom-left
 * corner (`background-size: 113% 212.4%; background-position: 0% 100%`, then
 * `scale(1.04)` about the same corner), so every painted prop lands on a FIXED
 * percentage box whatever the viewport's size or aspect. The band below is the
 * rug's, measured two independent ways that agreed: masking the green cloth in
 * a screenshot of the unfiltered backdrop (x 62.2..79.0%, y 44.6..72.0% -- the
 * erosion that drops the rug's sparse chevron edge also clips its faded right
 * end), and mapping the rug's source pixels through the transform above
 * (x 62.2..82.6%, y 44.7..72.1%).
 *
 * A relapse is silent -- he still renders, just in mid-air -- which is why the
 * anchor is pinned here and not left to the eye.
 */
const RUG = { left: 62.2, right: 82.6, top: 44.7, bottom: 72.1 };

const RULE = (() => {
  const m = SHOP_CSS.match(/^\.gs-shop-merchant\s*\{([^}]*)\}/m);
  if (!m) throw new Error(".gs-shop-merchant rule not found in shop.css");
  return m[1];
})();

/** The rule's own value for `prop`, in percent, or null if it doesn't set it. */
function pct(prop: string): number | null {
  const m = RULE.match(new RegExp(String.raw`(?:^|[;{\s])${prop}\s*:\s*(-?[\d.]+)%`));
  return m ? Number(m[1]) : null;
}

describe("shop: the merchant sits on the painted rug", () => {
  it("puts his centre over the rug's far half", () => {
    const left = pct("left");
    expect(left).not.toBeNull();
    // `translateX(-50%)` is what makes `left` his CENTRE rather than his left
    // edge. It matters: his width is a px constant (MERCHANT_H, which
    // SpineSprite bakes into the rig's world scale) while the rug's is a
    // percentage, so a left-edge anchor drifts him off the weave as the frame
    // widens.
    expect(RULE).toMatch(/translateX\(\s*-50%\s*\)/);
    expect(left!).toBeGreaterThan(RUG.left);
    expect(left!).toBeLessThan(RUG.right);
    // the reference seats him on the far side, wares nearest the viewer
    expect(left!).toBeGreaterThan((RUG.left + RUG.right) / 2);
  });

  it("anchors his seat, not his head", () => {
    const bottom = pct("bottom");
    expect(bottom).not.toBeNull();
    const seat = 100 - bottom!;
    expect(seat).toBeGreaterThan(RUG.top);
    expect(seat).toBeLessThan(RUG.bottom);
    // His height being a px constant is also why a top anchor cannot hold his
    // contact point: it slides off the rug the moment the frame changes
    // height. That is the exact shape of the original bug, so refuse both the
    // top and the right anchor it used.
    expect(pct("top")).toBeNull();
    expect(pct("right")).toBeNull();
  });
});
