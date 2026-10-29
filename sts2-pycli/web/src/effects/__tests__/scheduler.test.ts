import { describe, it, expect } from "vitest";
import { EffectScheduler, type Clock } from "../scheduler";
import { HIT_SPACING_MS } from "../timing";
import type { EffectEvent } from "../types";

const E = { side: "enemy", index: 0 } as const;
const hit = (amount: number): EffectEvent => ({ kind: "hurt", target: E, amount });

/** A hand-driven clock: nothing happens until the test advances it. */
function fakeClock() {
  let t = 0;
  let cbs: (() => void)[] = [];
  const clock: Clock = {
    now: () => t,
    frame: (cb) => {
      cbs.push(cb);
      return () => { cbs = cbs.filter((c) => c !== cb); };
    },
  };
  return {
    clock,
    advance(ms: number) {
      t += ms;
      const due = cbs;
      cbs = [];
      due.forEach((c) => c());
    },
  };
}

describe("EffectScheduler", () => {
  it("emits the first event immediately on play", () => {
    const { clock } = fakeClock();
    const seen: EffectEvent[] = [];
    new EffectScheduler(clock, (e) => seen.push(e)).play([hit(6), hit(6)]);
    expect(seen).toEqual([hit(6)]);
  });

  it("holds later hits until their time arrives", () => {
    const { clock, advance } = fakeClock();
    const seen: EffectEvent[] = [];
    const s = new EffectScheduler(clock, (e) => seen.push(e));
    s.play([hit(1), hit(2), hit(3)]);
    expect(seen).toHaveLength(1);
    advance(HIT_SPACING_MS);
    expect(seen).toHaveLength(2);
    advance(HIT_SPACING_MS);
    expect(seen).toHaveLength(3);
    expect(s.pending).toBe(0);
  });

  it("fires several already-due events together in one tick", () => {
    // pump() must drain everything due in a single frame (a `while` loop),
    // not one event per frame (an `if`) — advancing past two hits' worth of
    // spacing in one jump should surface both, not just the first.
    const { clock, advance } = fakeClock();
    const seen: EffectEvent[] = [];
    const s = new EffectScheduler(clock, (e) => seen.push(e));
    s.play([hit(1), hit(2), hit(3)]);
    advance(HIT_SPACING_MS * 2);
    expect(seen).toHaveLength(3);
  });

  it("drain emits everything outstanding at once", () => {
    const { clock } = fakeClock();
    const seen: EffectEvent[] = [];
    const s = new EffectScheduler(clock, (e) => seen.push(e));
    s.play([hit(1), hit(2), hit(3)]);
    s.drain();
    expect(seen).toEqual([hit(1), hit(2), hit(3)]);
    expect(s.pending).toBe(0);
  });

  it("supersedes: a new play settles the old queue first, in order", () => {
    const { clock } = fakeClock();
    const seen: EffectEvent[] = [];
    const s = new EffectScheduler(clock, (e) => seen.push(e));
    s.play([hit(1), hit(2)]);
    s.play([hit(9)]);
    // hit(2) was still queued; it lands before the new action's first event.
    expect(seen).toEqual([hit(1), hit(2), hit(9)]);
  });

  it("does not emit after dispose", () => {
    const { clock, advance } = fakeClock();
    const seen: EffectEvent[] = [];
    const s = new EffectScheduler(clock, (e) => seen.push(e));
    s.play([hit(1), hit(2)]);
    s.dispose();
    advance(HIT_SPACING_MS * 4);
    expect(seen).toEqual([hit(1)]);
  });

  it("playing nothing is a no-op", () => {
    const { clock } = fakeClock();
    const seen: EffectEvent[] = [];
    const s = new EffectScheduler(clock, (e) => seen.push(e));
    s.play([]);
    expect(seen).toEqual([]);
    expect(s.pending).toBe(0);
  });
});
