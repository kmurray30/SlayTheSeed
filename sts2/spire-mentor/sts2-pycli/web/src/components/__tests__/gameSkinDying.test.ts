// Reads game-skin.css off disk with node:fs — jsdom does not apply the
// stylesheet, and vitest stubs CSS imports (`?raw` included) to an empty
// string, so this has to read the FILE, same as arenaForegroundZIndex.test.ts
// (see that file's own comment). Lives in tsconfig.node-tests.json's project.
import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const SKIN_CSS = readFileSync(resolve(__dirname, "../../game-skin.css"), "utf8");

/** The one rule that carries `animation-name: gs-chrome-fade`, as
 *  `[selector list, declarations]`. */
function chromeFadeRule(): [string, string] {
  const rule = Array.from(SKIN_CSS.matchAll(/([^{}]+)\{([^{}]*)\}/g)).find(
    ([, , body]) => /animation-name:\s*gs-chrome-fade/.test(body ?? ""),
  );
  expect(rule, "no rule applying gs-chrome-fade found in game-skin.css").toBeDefined();
  return [rule![1]!, rule![2]!];
}

/**
 * Task 14 fix round 1: a dying enemy's SPRITE now dissolves for real via the
 * WebGL shader port (`SpineSprite`'s `dissolve` prop, `effects/dissolve.ts`).
 * `.gs-dying`'s own CSS animation used to fade the WHOLE `.gs-combatant`
 * wrapper — sprite included — and got deleted wholesale when the shader
 * landed, which silently regressed the chrome (HP bar / name / powers): it
 * stayed fully opaque for the whole 2.5s dissolve and then popped out at
 * removal, since nothing else ever dimmed it.
 *
 * The fix re-adds a fade, scoped away from a sprite the shader is really
 * eating — stacking a CSS fade on THAT would double-darken it and desync
 * from the shader's own (possibly per-effect) duration. "Really eating" is
 * narrower than `.gs-dying`, hence the `.gs-dissolving` / `.gs-rig-failed`
 * classes: see the rule's own comment for the three dying states that have
 * no shader fade and must therefore keep the CSS one.
 */
describe("the dying-enemy chrome fade", () => {
  it("fades everything inside .gs-combatant except the sprite wrap", () => {
    const [selector, body] = chromeFadeRule();
    expect(selector).toMatch(/\.gs-dying\s*>\s*:not\(\.gs-sprite-wrap\)/);
    expect(body).toMatch(/animation-name:\s*gs-chrome-fade/);
  });

  it("reads its duration from --gs-dying-ms (set inline, per corpse, by Combatant.tsx)", () => {
    expect(chromeFadeRule()[1]).toMatch(/animation-duration:\s*var\(--gs-dying-ms/);
  });

  it("the gs-chrome-fade keyframes actually dim opacity, not a no-op animation", () => {
    const kf = /@keyframes gs-chrome-fade \{([\s\S]*?)\}\s*\}/.exec(SKIN_CSS);
    expect(kf, "@keyframes gs-chrome-fade not found in game-skin.css").not.toBeNull();
    expect(kf![1]).toMatch(/0%\s*\{\s*opacity:\s*1;\s*filter:\s*none;\s*\}/);
    expect(kf![1]).toMatch(/100%\s*\{\s*opacity:\s*\.05;\s*filter:\s*grayscale\(1\)/);
  });

  it("does fade the sprite wrap when NO dissolve is running — three dying states have no shader fade", () => {
    // An enemy with no rig, a rig that failed to load, and any death before
    // the effect manifest resolves all render a plain `<img>` (or an
    // untouched canvas). Dropping them out of the fade — which scoping to
    // `:not(.gs-sprite-wrap)` alone does — makes those corpses pop out at
    // full opacity instead.
    expect(chromeFadeRule()[0]).toMatch(
      /\.gs-dying:not\(\.gs-dissolving\)\s*>\s*\.gs-sprite-wrap/,
    );
  });

  it("fades SpineSprite's fallback <img> even inside a dissolving combatant", () => {
    // `Combatant` sets `.gs-dissolving` from the props it can see (dying +
    // a manifest entry + a rig); only `SpineSprite` knows the rig then
    // FAILED to load and an `<img>` is standing in for it.
    expect(chromeFadeRule()[0]).toMatch(/\.gs-dying\.gs-dissolving\s+\.gs-rig-failed/);
  });

  it("never applies a fade/dying animation to a sprite wrap the WebGL dissolve owns", () => {
    // A regression here would double-fade the dissolving sprite: the
    // shader's own fade PLUS a CSS one stacked on top, darkening it and
    // desyncing from the shader's actual duration. Every "selector { body }"
    // pair in the sheet: any rule that animates `.gs-sprite-wrap` must carry
    // the `:not(.gs-dissolving)` guard that keeps it off a live dissolve.
    const rulePairs = Array.from(SKIN_CSS.matchAll(/([^{}]+)\{([^{}]*)\}/g));
    let checked = 0;
    for (const [, rawSelector, body] of rulePairs) {
      if (!/animation(-name)?\s*:/.test(body ?? "")) continue;
      // One rule, many selectors: check each independently, or a guarded
      // selector in the list would vouch for an unguarded sibling.
      for (const one of (rawSelector ?? "").split(",")) {
        // Strip `:not(...)` before asking whether the selector TARGETS the
        // wrap, so the chrome rule's own `:not(.gs-sprite-wrap)` doesn't
        // read as targeting it.
        if (!/\.gs-sprite-wrap\b/.test(one.replace(/:not\([^)]*\)/g, ""))) continue;
        checked++;
        expect(one, `${one.trim()} animates a sprite wrap with no :not(.gs-dissolving) guard`)
          .toMatch(/:not\(\.gs-dissolving\)/);
      }
    }
    expect(checked).toBeGreaterThan(0); // sanity: the selector still exists at all
  });
});
