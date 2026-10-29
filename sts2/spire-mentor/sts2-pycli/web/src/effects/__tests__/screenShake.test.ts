import { describe, it, expect } from "vitest";
import {
  SHAKE_DURATION,
  SHAKE_STRENGTH,
  screenShakeKeyframes,
  screenShakeOffset,
  shakeStrengthForDamage,
} from "../screenShake";

const SHORT = SHAKE_DURATION.short;

describe("shakeStrengthForDamage", () => {
  it("splits on the C#'s own two boundaries", () => {
    // `CreatureCmd.cs:351-365`: `< 6` Weak, `< 11` Medium, else Strong.
    expect(shakeStrengthForDamage(0)).toBe("weak");
    expect(shakeStrengthForDamage(5)).toBe("weak");
    expect(shakeStrengthForDamage(6)).toBe("medium");
    expect(shakeStrengthForDamage(10)).toBe("medium");
    expect(shakeStrengthForDamage(11)).toBe("strong");
    expect(shakeStrengthForDamage(999)).toBe("strong");
  });
});

describe("screenShakeOffset", () => {
  it("starts at FULL amplitude — this is a punch, not a build-up", () => {
    // The envelope is keyed on the time REMAINING (`_duration / _startDuration`
    // in `ScreenPunchInstance.Update`), so it is 1 at t=0. A shake that ramped
    // in would be the wrong shape, and reads plausible enough to ship.
    const { x, y } = screenShakeOffset(0, SHORT, 40, 0);
    expect(Math.hypot(x, y)).toBeCloseTo(40 * Math.abs(Math.cos(SHORT * 60)));
  });

  it("decays to exactly zero at the end", () => {
    expect(screenShakeOffset(SHORT, SHORT, 40, 1)).toEqual({ x: 0, y: 0 });
    expect(screenShakeOffset(SHORT * 2, SHORT, 40, 1)).toEqual({ x: 0, y: 0 });
  });

  it("oscillates along ONE fixed axis, not around a circle", () => {
    // `new Vector2(num, 0f).Rotated(_angle)` — only the x component varies, so
    // every sample lies on the same line through the origin. A circular orbit
    // (the intuitive "shake") would put samples off it.
    const angle = 0.7;
    for (const t of [0.02, 0.05, 0.11, 0.19, 0.27]) {
      const { x, y } = screenShakeOffset(t, SHORT, 40, angle);
      expect(y * Math.cos(angle) - x * Math.sin(angle)).toBeCloseTo(0);
    }
  });

  it("crosses zero repeatedly — ~2.9 cycles at 60 rad/s over the Short", () => {
    // Sign changes are what make it a wiggle rather than a lurch; counting them
    // pins `WiggleSpeed` itself, which nothing else here would notice.
    let crossings = 0;
    let prev = screenShakeOffset(0, SHORT, 40, 0).x;
    for (let i = 1; i <= 600; i++) {
      const cur = screenShakeOffset((i / 600) * SHORT, SHORT, 40, 0).x;
      if (prev !== 0 && cur !== 0 && Math.sign(cur) !== Math.sign(prev)) crossings++;
      prev = cur;
    }
    // The phase sweeps 60 rad/s * 0.3 s = 18 rad, i.e. 18/2π ≈ 2.86 cycles.
    // `cos` is zero at π/2 + kπ, and six of those (1.57 … 17.28) fall inside
    // (0, 18).
    expect(crossings).toBe(6);
  });

  it("scales linearly with strength, so the tier is the only difference", () => {
    const weak = screenShakeOffset(0.05, SHORT, SHAKE_STRENGTH.weak, 1.2);
    const strong = screenShakeOffset(0.05, SHORT, SHAKE_STRENGTH.strong, 1.2);
    expect(strong.x).toBeCloseTo(weak.x * (SHAKE_STRENGTH.strong / SHAKE_STRENGTH.weak));
    expect(strong.y).toBeCloseTo(weak.y * (SHAKE_STRENGTH.strong / SHAKE_STRENGTH.weak));
  });

  it("carries the game's own amplitudes", () => {
    // `NScreenShake.cs:115-121`. These are the numbers, not a re-tuning.
    expect(SHAKE_STRENGTH.weak).toBe(5);
    expect(SHAKE_STRENGTH.medium).toBe(20);
    expect(SHAKE_STRENGTH.strong).toBe(40);
    expect(SHAKE_DURATION.short).toBe(0.3);
  });

  it("returns to rest for a zero-length shake instead of dividing by it", () => {
    expect(screenShakeOffset(0, 0, 40, 0)).toEqual({ x: 0, y: 0 });
  });
});

describe("screenShakeKeyframes", () => {
  it("spans offset 0 to 1 and ends at no transform", () => {
    const frames = screenShakeKeyframes(SHORT, 40, 0.3, 12);
    expect(frames).toHaveLength(13);
    expect(frames[0]!.offset).toBe(0);
    expect(frames[frames.length - 1]!.offset).toBe(1);
    expect(frames[frames.length - 1]!.transform).toBe("translate(0.000px, 0.000px)");
  });

  it("samples the curve it says it samples", () => {
    const frames = screenShakeKeyframes(SHORT, 40, 0, 4);
    const { x } = screenShakeOffset(SHORT * 0.5, SHORT, 40, 0);
    expect(frames[2]!.transform).toBe(`translate(${x.toFixed(3)}px, 0.000px)`);
  });
});
