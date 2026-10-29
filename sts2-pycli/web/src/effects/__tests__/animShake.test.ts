import { describe, it, expect } from "vitest";
import {
  ANIM_SHAKE_AMPLITUDE,
  ANIM_SHAKE_DURATION,
  animShakeKeyframes,
  animShakeOffsetX,
} from "../animShake";

const D = ANIM_SHAKE_DURATION;
const A = ANIM_SHAKE_AMPLITUDE;

describe("animShakeOffsetX", () => {
  it("carries the game's own amplitude and duration", () => {
    // `NCreature.cs:1244-1254`: `Vector2.Right * 10f * ...`, tweened over
    // `1.0` seconds. These are the numbers, not a re-tuning — the 2/5/9 px
    // over 0.3 s that `.gs-shaking` used were invented for a stand-in.
    expect(A).toBe(10);
    expect(D).toBe(1);
  });

  it("rests at both ends", () => {
    // Both factors of `sin(t*4) * sin(t*0.5)` vanish at t=0, and `sin(t*0.5)`
    // vanishes again at t=2pi — the envelope closes the shake by construction,
    // which is why the tween can free the node without a settle step.
    expect(animShakeOffsetX(0, D, A)).toBeCloseTo(0);
    expect(animShakeOffsetX(D, D, A)).toBeCloseTo(0);
  });

  it("holds at rest after the tween has finished", () => {
    expect(animShakeOffsetX(D * 1.5, D, A)).toBe(0);
    expect(animShakeOffsetX(D * 99, D, A)).toBe(0);
  });

  it("returns to rest for a zero-length shake instead of dividing by it", () => {
    expect(animShakeOffsetX(0, 0, A)).toBe(0);
  });

  it("moves along ONE axis — there is no y component to return", () => {
    // `Vector2.Right * ...` in the C#. Named here because the SCREEN shake
    // next door rotates its axis per fire, and the two are easy to conflate.
    expect(typeof animShakeOffsetX(0.3, D, A)).toBe("number");
  });

  it("peaks just under the full 10, because the two sines never crest together", () => {
    // `sin(t*0.5)` is 1 at t=pi, where `sin(t*4)` is exactly 0. The product's
    // maximum sits at t = 7pi/8 (and its mirror), giving sin(7pi/16) ~= 0.9808.
    // Asserted rather than rounded to 10 so that a future change to either
    // frequency shows up here instead of hiding inside a tolerance.
    let peak = 0;
    for (let i = 0; i <= 10000; i++) peak = Math.max(peak, Math.abs(animShakeOffsetX((i / 10000) * D, D, A)));
    expect(peak).toBeCloseTo(A * Math.sin((7 * Math.PI) / 16), 2);
    expect(peak).toBeLessThan(A);
  });

  it("scales linearly with amplitude, so pxPerScene is the only difference", () => {
    expect(animShakeOffsetX(0.4, D, 20)).toBeCloseTo(animShakeOffsetX(0.4, D, 10) * 2);
  });

  it("crosses zero eight times inside the tween — four oscillations, not one lurch", () => {
    // The phase sweeps 0 -> 2pi, so `sin(t*4)` completes four cycles and is
    // zero at t = k*pi/4 for k = 1..7; `sin(t*0.5)` adds the endpoint at 2pi.
    // Counting them pins BOTH frequencies, which no amplitude assertion would.
    let crossings = 0;
    let prev = animShakeOffsetX(0.0001, D, A);
    for (let i = 1; i < 20000; i++) {
      const cur = animShakeOffsetX((i / 20000) * D, D, A);
      if (prev !== 0 && cur !== 0 && Math.sign(cur) !== Math.sign(prev)) crossings++;
      prev = cur;
    }
    expect(crossings).toBe(7);
  });

  it("front-loads the wiggle: cubic-out easing spends half the phase in the first sixth", () => {
    // `SetEase(EASE_OUT).SetTrans(TRANS_CUBIC)` eases the TWEENED VALUE (the
    // phase), not the position — so the oscillations bunch at the start and
    // stretch out at the end. Dropping the easing would leave a shake that
    // still starts and ends at rest and still crosses zero seven times, so
    // this is the only assertion that would notice.
    //
    // cubicOut(p) = (p-1)^3 + 1 reaches 0.5 at p = 1 - cbrt(0.5) ~= 0.2063.
    const halfPhaseAt = 1 - Math.cbrt(0.5);
    expect(halfPhaseAt).toBeLessThan(0.25);
    // Three of the four oscillations are done by then.
    let crossings = 0;
    let prev = animShakeOffsetX(0.0001, D, A);
    for (let i = 1; i <= 20000 * halfPhaseAt; i++) {
      const cur = animShakeOffsetX((i / 20000) * D, D, A);
      if (prev !== 0 && cur !== 0 && Math.sign(cur) !== Math.sign(prev)) crossings++;
      prev = cur;
    }
    expect(crossings).toBe(3);
  });
});

describe("animShakeKeyframes", () => {
  it("spans offset 0 to 1 and ends at no transform", () => {
    const frames = animShakeKeyframes(D, A, 12);
    expect(frames).toHaveLength(13);
    expect(frames[0]!.offset).toBe(0);
    expect(frames[frames.length - 1]!.offset).toBe(1);
    expect(frames[frames.length - 1]!.transform).toBe("translateX(0.000px)");
  });

  it("samples the curve it says it samples", () => {
    const frames = animShakeKeyframes(D, A, 4);
    expect(frames[2]!.transform).toBe(`translateX(${animShakeOffsetX(D * 0.5, D, A).toFixed(3)}px)`);
  });
});
