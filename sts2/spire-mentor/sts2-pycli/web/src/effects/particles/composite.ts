/**
 * The composite lifecycle: which emitters a `ParticlesEffect` starts, when
 * each one begins relative to the composite's own start, how long the whole
 * thing lives, and the two things a caller can layer on top of the manifest
 * (a binding-table tint, and the one bespoke per-effect randomiser this
 * sub-project's scope reaches).
 *
 * Deliberately pure — no RNG-derived particle state lives here (that's
 * `simulate.ts`'s job, invoked separately per emitter by the renderer) — so
 * the composite's start-up decisions are unit-testable without a WebGL
 * context, the same seam `VfxLayer.tsx`'s `buildLiveEffect` already
 * established for flipbook/Spine selection.
 */

import type { ParticleEmitter, ParticlesEffect } from "../manifest";

/** One emitter of a live composite: which manifest emitter it came from, when
 *  it starts (seconds after the composite's own start — `ParticleEmitter
 *  .startDelay`, NOT necessarily 0; see module doc / `NScratchVfx
 *  .PlaySequence`'s two-phase restart), and the seed `simulate()` should run
 *  with.
 *
 *  `seed` is this PROJECT's own instancing draw, not a transcribed Godot RNG
 *  call: `simulate()` must be safely re-invocable every render frame at a
 *  growing `duration` and still reproduce the same particle trajectory (its
 *  whole determinism contract, see `simulate.ts`'s module doc), which needs
 *  one STABLE seed per emitter rather than a stream that keeps advancing
 *  frame to frame. Drawing it once here, from the composite's own `rng`, at
 *  start time is what makes that possible. */
export interface LiveParticleEmitter {
  emitter: ParticleEmitter;
  startedAt: number;
  seed: number;
}

/** One started `ParticlesEffect` instance. */
export interface LiveComposite {
  effect: ParticlesEffect;
  emitters: LiveParticleEmitter[];
  /** `effect.lifetime`, copied so `isDone` doesn't need the effect too. */
  lifetime: number;
  /** The binding table's tint (design spec §1.4/§5), as `[r, g, b, a]` in
   *  [0, 1] — identity (opaque white) when the caller passes none, so a
   *  draw path can always multiply by it unconditionally. */
  tint: readonly [number, number, number, number];
  /** `NPoisonImpactVfx.cs:101`: `Scale = new Vector2(Randf() > 0.5 ? 1 : -1,
   *  1)` on the node `effect.mirrorX` names — a per-spawn coin flip a bespoke
   *  script layers on top of the extracted scene, not something any
   *  `ParticleEmitter` field encodes. Driven by `effect.mirrorX` being
   *  non-null (today that is exactly `vfx_poison_impact`, and only it) rather
   *  than a name check, so a future composite the extractor tags the same way
   *  picks this up automatically. Always `false` when `mirrorX` is null — no
   *  RNG draw happens for those composites, matching the real game, which
   *  never calls `Randf()` for them either. */
  mirrored: boolean;
}

/** `#RRGGBB` or bare `RRGGBB` (task 6 normalizes the committed binding table
 *  to the `#`-prefixed form; accepting both keeps this tolerant of either). */
function hexToRgba01(hex: string): [number, number, number, number] {
  const h = hex.startsWith("#") ? hex.slice(1) : hex;
  const r = Number.parseInt(h.slice(0, 2), 16);
  const g = Number.parseInt(h.slice(2, 4), 16);
  const b = Number.parseInt(h.slice(4, 6), 16);
  return [r / 255, g / 255, b / 255, 1];
}

/**
 * Starts one composite: schedules every emitter at its authored
 * `startDelay` (do NOT assume they all start at once — see module doc),
 * resolves the binding tint, and draws the one bespoke-script randomiser
 * this sub-project models. `rng` is consumed in this fixed order: the mirror
 * coin flip first (only when `effect.mirrorX` is non-null), then one seed
 * draw per emitter in manifest order — both are this project's own
 * conventions, not transcribed Godot call sites (see `LiveParticleEmitter
 * .seed`'s doc), so there is no "real" order to match; a fixed, documented
 * one is what makes this function itself deterministic and testable.
 */
export function startComposite(
  effect: ParticlesEffect,
  rng: () => number,
  tintHex?: string | null,
): LiveComposite {
  const mirrored = effect.mirrorX !== null && rng() <= 0.5;
  const emitters: LiveParticleEmitter[] = effect.emitters.map((emitter) => ({
    emitter,
    startedAt: emitter.startDelay,
    seed: Math.floor(rng() * 0xffffffff),
  }));
  return {
    effect,
    emitters,
    lifetime: effect.lifetime,
    tint: tintHex ? hexToRgba01(tintHex) : [1, 1, 1, 1],
    mirrored,
  };
}

/** Whether a live composite has outlived its authored `lifetime`, given
 *  seconds elapsed since `startComposite` was called for it. */
export function isDone(live: LiveComposite, elapsedSeconds: number): boolean {
  return elapsedSeconds >= live.lifetime;
}
