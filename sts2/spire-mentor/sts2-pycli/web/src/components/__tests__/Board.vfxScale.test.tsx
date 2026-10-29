import { render } from "@testing-library/react";
import { useEffect } from "react";
import { afterAll, describe, it, expect, vi } from "vitest";
import Board from "../Board";
import { stageZoom } from "../../stageZoom";
import type { Step } from "../../api";

// What `Board` handed the overlay, per render. The real `VfxLayer` uses this
// inside a WebGL draw loop jsdom cannot run, so the prop is the only place the
// value is observable at all.
const { sceneScales } = vi.hoisted(() => ({ sceneScales: [] as number[] }));
vi.mock("../VfxLayer", () => {
  function VfxLayerMock({ sceneScale }: { sceneScale?: number }) {
    useEffect(() => { sceneScales.push(sceneScale ?? NaN); }, [sceneScale]);
    return null;
  }
  return { default: VfxLayerMock };
});

/** The value from the most recent render. (`Array.prototype.at` is past this
 *  project's TS lib target.) */
function last(): number {
  return sceneScales[sceneScales.length - 1];
}

const LAYOUT_PROPS = ["clientWidth", "clientHeight", "offsetWidth", "offsetHeight"] as const;

/**
 * Give jsdom (which measures every box as 0) the two DIFFERENT measurements
 * the scene actually takes, and return the `--gs-k` they imply.
 *
 * The distinction is the entire point of this file:
 *   - `.gs-arena-stage` is read with `clientWidth/Height` — the SCREEN box.
 *   - `.gs-stage-scale` is read with `offsetWidth/Height` — its own layout box,
 *     which is `screen / --gs-k`, because that layer is laid out large and
 *     transformed back down.
 * Stubbing both to the same number (the obvious thing) hides the factor under
 * test.
 */
function stubStage(w: number, h: number): number {
  const zoom = stageZoom(w, h);
  const dims: Record<string, number> = {
    clientWidth: w, clientHeight: h,
    offsetWidth: Math.round(w / zoom), offsetHeight: Math.round(h / zoom),
  };
  for (const k of LAYOUT_PROPS) {
    Object.defineProperty(HTMLElement.prototype, k, { configurable: true, get: () => dims[k] });
  }
  return zoom;
}

afterAll(() => {
  for (const k of LAYOUT_PROPS) {
    Object.defineProperty(HTMLElement.prototype, k, { configurable: true, get: () => 0 });
  }
});

function step(): Step {
  return {
    idx: 0, decision: "combat_play", scores: [], pv: null, chosen_action: null,
    source: "live", meta: { turn: 1, step_no: 0 },
    state: {
      decision: "combat_play", round: 1, energy: 3, max_energy: 3,
      draw_pile_count: 5, discard_pile_count: 0, exhaust_pile_count: 0,
      player: { name: "Ironclad", hp: 80, max_hp: 80, block: 0 },
      enemies: [{ index: 0, name: "Twig Slime", hp: 50, max_hp: 50, block: 0,
        image: null, intents: [] }],
      hand: [],
    },
  } as unknown as Step;
}

describe("the overlay's scene scale", () => {
  // `sceneScale` is SCREEN px per scene px: the canvas measures `.gs-actors`
  // with `getBoundingClientRect`, which is taken after `--gs-k`. So it must
  // shrink with the window exactly as the fighters do. Passing `actorScale`
  // alone — LAYOUT px per scene px, derived from the unscaled box — leaves it
  // pinned near 1.0 at every window size, which is the bug: the slash stayed
  // the same number of screen pixels while the fighters got smaller.
  it.each([
    [1920, 1052],
    [1366, 740],
    [1000, 900],
  ])("tracks --gs-k at %ix%i", (w, h) => {
    const zoom = stubStage(w, h);
    sceneScales.length = 0;
    render(<Board step={step()} />);
    // Within a couple of percent of `--gs-k`: the residue is `fitScene`'s own
    // factor against the quantised zoom, not a unit error. An `actorScale`-only
    // value is ~1.0 for every row here and misses by up to 0.48.
    expect(last()).toBeCloseTo(zoom, 1);
  });

  it("actually varies with the window, rather than being a constant", () => {
    // The guard against "close to zoom" passing vacuously if both were 1.
    sceneScales.length = 0;
    stubStage(1920, 1052);
    render(<Board step={step()} />);
    const big = last();
    stubStage(1000, 900);
    render(<Board step={step()} />);
    const small = last();
    // 0.7, not the 0.52 a pure fit gives: at 1000x900 the compact framing
    // (stageZoom.ts) draws the scene ~1.27x its fit, so the zoom is 0.66.
    expect(small).toBeLessThan(big * 0.7);
  });
});
