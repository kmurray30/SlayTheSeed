/**
 * The game's SCREEN shake — `NGame.ScreenShake` → `NScreenShake.Shake` →
 * `ScreenPunchInstance`, ported.
 *
 * `CreatureCmd.cs` fires it from two sites, and both are this same global
 * shake; they differ in trigger, not in scope:
 *
 *  - `:349-368` scales the strength by unblocked damage (Weak under 6, Medium
 *    under 11, Strong above), inside the damage loop.
 *  - `:400-407` fires a fixed `ScreenShake(Weak, Short)` on `WasFullyBlocked`,
 *    in a second loop. A fully-blocked hit cannot reach the first site — `:294`
 *    binds `damage = UnblockedDamage + OverkillDamage`, which is 0 when block
 *    ate the hit, and `:338`'s `if (damage <= 0) continue` skips the rest of
 *    that loop body — so the two sites partition the cases rather than
 *    overlapping.
 *
 * This module is the arithmetic only: pure, no DOM, no React. `Board.tsx` owns
 * the triggers and the element it plays on.
 */

/** `ShakeStrength.cs`, with the amplitudes `NScreenShake._Ready` binds to each
 *  (`NScreenShake.cs:115-121`). SCENE px, i.e. against the game's 1920-wide
 *  viewport — `Board.tsx` multiplies by `pxPerScene` to reach stage px, the
 *  same conversion every rig and every VFX composite goes through. */
export const SHAKE_STRENGTH = {
  none: 0,
  veryWeak: 2,
  weak: 5,
  medium: 20,
  strong: 40,
  tooMuch: 80,
} as const;
export type ShakeStrength = keyof typeof SHAKE_STRENGTH;

/** `ShakeDuration.cs` / `NScreenShake.cs:123-127`, in seconds. `Forever`
 *  (999999999.0) is omitted: it belongs to `Rumble`, which no `CreatureCmd`
 *  site uses, and a never-ending animation is not a thing to hand a browser. */
export const SHAKE_DURATION = { short: 0.3, normal: 0.8, long: 1.2 } as const;
export type ShakeDuration = keyof typeof SHAKE_DURATION;

/** `ShakeInstance.WiggleSpeed` (`ShakeInstance.cs:17`), radians per second.
 *  60 rad/s over the 0.3 s `Short` is ~2.9 full oscillations. */
const WIGGLE_SPEED = 60;

/** `Ease.CubicOut` (`Ease.cs:150-154`): `(p - 1)^3 + 1`. */
function cubicOut(p: number): number {
  const q = p - 1;
  return q * q * q + 1;
}

/**
 * `ScreenPunchInstance.Update`, as a function of elapsed time rather than of
 * an accumulating countdown:
 *
 * ```text
 * _duration -= delta;
 * float num = (float)Math.Cos(_duration * WiggleSpeed);
 * _ease = Ease.CubicOut((float)(_duration / _startDuration));
 * return new Vector2(num, 0f).Rotated(_angle) * _strength * _ease;
 * ```
 *
 * Two things fall out of reading it rather than assuming a shake shape. It
 * decays from FULL amplitude at t=0 — the envelope is keyed on the time
 * REMAINING, so this is a punch, not a build-up. And the wiggle is along one
 * fixed axis, `Rotated(_angle)`, not a circular orbit: `Vector2(num, 0)` has
 * only an x component before the rotation.
 *
 * `angleRad` is Godot's rotation of a screen-space vector and this returns
 * screen-space CSS px, both with y pointing DOWN, so no chirality flip applies
 * here — unlike the Y-up WebGL overlay (see `VfxLayer`'s
 * `drawAngleFromGodotDegrees`).
 */
export function screenShakeOffset(
  elapsed: number,
  duration: number,
  strengthPx: number,
  angleRad: number,
): { x: number; y: number } {
  if (duration <= 0 || elapsed >= duration) return { x: 0, y: 0 };
  const remaining = duration - elapsed;
  const amp = Math.cos(remaining * WIGGLE_SPEED) * strengthPx * cubicOut(remaining / duration);
  return { x: amp * Math.cos(angleRad), y: amp * Math.sin(angleRad) };
}

/**
 * The shake sampled as Web-Animations keyframes.
 *
 * Sampled rather than expressed as a CSS `@keyframes` block because the curve
 * is a ~2.9-cycle cosine: any hand-written keyframe set coarse enough to read
 * would alias it into a different waveform. `steps` picks the resolution —
 * 60 (the default) is one sample per screen frame over the 0.3 s `Short`, so
 * the browser's linear interpolation has nothing left to distort.
 */
export function screenShakeKeyframes(
  duration: number,
  strengthPx: number,
  angleRad: number,
  steps = 60,
): Keyframe[] {
  const frames: Keyframe[] = [];
  for (let i = 0; i <= steps; i++) {
    const offset = i / steps;
    const { x, y } = screenShakeOffset(offset * duration, duration, strengthPx, angleRad);
    frames.push({ offset, transform: `translate(${x.toFixed(3)}px, ${y.toFixed(3)}px)` });
  }
  return frames;
}

/**
 * `CreatureCmd.cs:351-365`'s damage tiers. Reads UNBLOCKED damage, which is
 * what the `hurt` event carries.
 */
export function shakeStrengthForDamage(damage: number): ShakeStrength {
  if (damage < 6) return "weak";
  if (damage < 11) return "medium";
  return "strong";
}
