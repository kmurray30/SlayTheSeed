import { describe, it, expect } from "vitest";
import { bestAction, toggleHintsLatch } from "./hint";
import type { Score } from "./api";

function score(over: Partial<Score>): Score {
  return {
    stable_idx: 0, action: "Strike", win_prob: 0.5, q: 0, visits: 0,
    is_best: false, q_saturated: false, low_confidence: false, target_index: null, ...over,
  };
}

describe("bestAction", () => {
  it("returns null for empty scores", () => {
    expect(bestAction([])).toBeNull();
  });

  it("maps the end-turn sentinel (-1) to end", () => {
    expect(bestAction([score({ stable_idx: -1, is_best: true })])).toEqual({ kind: "end" });
  });

  it("maps a targeted play", () => {
    expect(bestAction([score({ stable_idx: 2, target_index: 1, is_best: true })])).toEqual({
      kind: "play", cardIndex: 2, targetIndex: 1,
    });
  });

  it("maps an untargeted play (null target -> undefined)", () => {
    expect(bestAction([score({ stable_idx: 3, target_index: null, is_best: true })])).toEqual({
      kind: "play", cardIndex: 3, targetIndex: undefined,
    });
  });

  it("prefers the is_best score over a higher win_prob", () => {
    const scores = [score({ stable_idx: 0, win_prob: 0.9 }), score({ stable_idx: 1, win_prob: 0.6, is_best: true })];
    expect(bestAction(scores)).toEqual({ kind: "play", cardIndex: 1, targetIndex: undefined });
  });

  it("falls back to highest win_prob when none flagged best", () => {
    const scores = [score({ stable_idx: 0, win_prob: 0.3 }), score({ stable_idx: 1, win_prob: 0.7 })];
    expect(bestAction(scores)).toEqual({ kind: "play", cardIndex: 1, targetIndex: undefined });
  });

  it("maps the potion sentinel (-100 - slot) to a potion use, decoding the slot", () => {
    expect(bestAction([score({ stable_idx: -100, is_best: true })])).toEqual({
      kind: "potion", slot: 0, targetIndex: undefined,
    });
    expect(bestAction([score({ stable_idx: -102, is_best: true })])).toEqual({
      kind: "potion", slot: 2, targetIndex: undefined,
    });
  });

  it("carries target_index for a targeted potion", () => {
    expect(bestAction([score({ stable_idx: -101, target_index: 1, is_best: true })])).toEqual({
      kind: "potion", slot: 1, targetIndex: 1,
    });
  });

  it("marks non-dispatchable sentinels (channel/evoke/choose) as unsupported, not a card play", () => {
    expect(bestAction([score({ stable_idx: -200, is_best: true })])).toEqual({ kind: "unsupported" });
    expect(bestAction([score({ stable_idx: -201, is_best: true })])).toEqual({ kind: "unsupported" });
    expect(bestAction([score({ stable_idx: -301, is_best: true })])).toEqual({ kind: "unsupported" });
  });
});

describe("toggleHintsLatch", () => {
  it("turning hints OFF latches against re-fetch and clears visible scores", () => {
    expect(toggleHintsLatch(true)).toEqual({ latch: true, clearScores: true });
  });

  it("turning hints ON releases the latch so the auto-fetch effect can fire again", () => {
    // Regression: re-enabling hints after disabling them must release the
    // latch — otherwise the auto-fetch effect stays bailed and the toggle
    // looks stuck off ("enable hints again, stays disabled").
    expect(toggleHintsLatch(false)).toEqual({ latch: false, clearScores: false });
  });

  it("off→on→off→on releases the latch on every re-enable", () => {
    let autoHints = false;
    const latches: boolean[] = [];
    for (let i = 0; i < 4; i++) {
      latches.push(toggleHintsLatch(autoHints).latch);
      autoHints = !autoHints;
    }
    // enable, disable, enable, disable → latch released on both enables.
    expect(latches).toEqual([false, true, false, true]);
  });
});
