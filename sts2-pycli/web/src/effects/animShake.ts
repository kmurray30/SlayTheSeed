/**
 * The game's per-CREATURE shake — `NCreature.AnimShake` (`NCreature.cs:1237`),
 * ported.
 *
 * Not to be confused with `screenShake.ts`, which is the whole-viewport punch
 * (`NGame.ScreenShake`). These are two different effects on two different
 * triggers, and the workbench used to conflate them: `.gs-shaking` wobbled the
 * struck creature *on damage* and its own CSS comment explained that it did so
 * "because the game shakes the whole SCREEN". Once the screen shake landed for
 * real, that wobble became a duplicate — and reading the decomp showed it was
 * worse than a duplicate, because damage is the one case the game EXCLUDES:
 *
 * ```text
 * if (IsInsideTree() && (_shakeTween == null || !_shakeTween.IsRunning())
 *     && !Visuals.IsPlayingHurtAnimation())
 * ```
 *
 * The real caller is `NCreature.cs:895-902` — a power landing whose valence is
 * NOT Buff:
 *
 * ```text
 * if (power.ShouldPlayVfx) SfxCmd.Play(flag ? ".../buff" : ".../debuff");
 * if (!flag) AnimShake();
 * ```
 *
 * where `flag` is `GetTypeForAmount(power.Amount) == PowerType.Buff` — the same
 * valence `sound/powerValence.ts` already computes for the panel colour and the
 * sound cue, so all three read one rule.
 *
 * The tween itself:
 *
 * ```text
 * _shakeTween.TweenMethod(t => Visuals.Position = Vector2.Right * 10f
 *                                 * Mathf.Sin(t * 4f) * Mathf.Sin(t * 0.5f),
 *                         0f, MathF.PI * 2f, 1.0)
 *     .SetEase(EaseType.Out).SetTrans(TransitionType.Cubic);
 * ```
 *
 * Four things fall out of reading it rather than assuming a wobble shape:
 *
 *  - the amplitude is a FIXED 10 scene px. It does not scale with damage; the
 *    2/5/9 px tiers the old stand-in used were borrowed from the screen
 *    shake's `ShakeStrength` and do not belong to this effect at all.
 *  - it runs for a full second, not the screen shake's 0.3 s `Short`.
 *  - the easing is applied to the tweened PHASE, not to the position, so the
 *    four oscillations bunch up at the start and stretch out at the end.
 *  - `sin(t * 0.5)` over `t` in [0, 2pi] is a half-sine envelope, which is what
 *    makes the shake start and end at rest with no settle step.
 *
 * This module is the arithmetic only: pure, no DOM, no React. `Combatant.tsx`
 * owns the trigger and the element it plays on.
 */

/** `Vector2.Right * 10f` (`NCreature.cs:1247`), in SCENE px — i.e. against the
 *  game's 1920-wide viewport, so callers multiply by `pxPerScene` to reach
 *  stage px, the same conversion every rig and every VFX composite goes
 *  through. The peak excursion is slightly under this: the two sines never
 *  crest together (see the module doc). */
export const ANIM_SHAKE_AMPLITUDE = 10;

/** The tween's own duration (`NCreature.cs:1253`), in seconds. */
export const ANIM_SHAKE_DURATION = 1;

/** Godot's `TRANS_CUBIC` + `EASE_OUT` (`easing_equations.h`'s cubic `out`):
 *  `c * ((p/d - 1)^3 + 1)`, i.e. `(p - 1)^3 + 1` normalised.
 *
 *  Deliberately a local copy rather than shared with `screenShake.ts`'s
 *  identically-shaped `cubicOut`: that one transcribes the GAME's own
 *  `Ease.CubicOut` (`Ease.cs:150-154`), this one transcribes GODOT's tween
 *  easing table. They agree today because both are the standard curve, but
 *  they are two different upstreams and an import would assert otherwise. */
function cubicOut(p: number): number {
  const q = p - 1;
  return q * q * q + 1;
}

/**
 * The creature's horizontal offset at `elapsed` seconds into the shake, in the
 * same units as `amplitudePx`.
 *
 * There is no y component — `Vector2.Right * ...` is a pure x displacement.
 * That is the other half of the confusion with the screen shake, which picks a
 * fresh random axis on every fire.
 */
export function animShakeOffsetX(elapsed: number, duration: number, amplitudePx: number): number {
  if (duration <= 0 || elapsed >= duration) return 0;
  // The tween interpolates its VALUE from 0 to 2pi under cubic-out easing;
  // `t` below is that value, not the clock.
  const t = 2 * Math.PI * cubicOut(elapsed / duration);
  return amplitudePx * Math.sin(t * 4) * Math.sin(t * 0.5);
}

/**
 * The shake sampled as Web-Animations keyframes.
 *
 * Sampled rather than written as a CSS `@keyframes` block for the same reason
 * `screenShakeKeyframes` is: four eased oscillations under a half-sine
 * envelope is not a curve a readable keyframe set can carry, and the four-stop
 * block that used to stand in for it aliased into a different waveform. Here
 * the easing makes it worse than the screen shake's case — the early cycles
 * are compressed into the first fifth of the run, so uniform hand-written
 * stops would smear exactly the part that reads as a shake.
 *
 * `steps` picks the resolution; 60 (the default) is one sample per screen
 * frame over the 1 s tween.
 */
export function animShakeKeyframes(
  duration: number,
  amplitudePx: number,
  steps = 60,
): Keyframe[] {
  const frames: Keyframe[] = [];
  for (let i = 0; i <= steps; i++) {
    const offset = i / steps;
    const x = animShakeOffsetX(offset * duration, duration, amplitudePx);
    frames.push({ offset, transform: `translateX(${x.toFixed(3)}px)` });
  }
  return frames;
}
