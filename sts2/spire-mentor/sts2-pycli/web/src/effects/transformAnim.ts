/**
 * Sampling for `transform_anim` effects (`vfx_block_broken`'s two halves) —
 * a sprite driven entirely by baked `AnimationPlayer` transform tracks, one
 * per channel (`positionX`/`positionY`/`rotation`), rather than a shader or
 * a particle system.
 */

import { sampleLut } from "./particles/lut";

/**
 * One transform channel at time `t` (fraction of the effect's `lifetime`,
 * [0, 1]) — the baked 256-sample LUT (`sampleLut`'s endpoint-inclusive index
 * math), or `fallback` when the channel carries no track at all.
 *
 * A `null` track is NOT zero: `manifest.ts`'s own doc on `TransformAnimEffect`
 * says so explicitly — "a null track is a channel the animation never
 * touches — hold the sprite's authored value, do not default it to zero."
 * `vfx_block_broken`'s two halves happen to author every channel today (both
 * are thrown AND spun), but a future `transform_anim` scene need not, and a
 * silent zero would snap that sprite to the origin/no-rotation instead of
 * leaving it where it was authored. The caller supplies whatever that
 * authored value is: the manifest carries it per sprite as
 * `restPositionX`/`restPositionY`/`restRotation`, extracted from the scene
 * node's own transform, precisely because this function cannot know it from
 * a track path's absence.
 */
export function sampleTrack(lut: readonly number[] | null, t: number, fallback: number): number {
  if (lut === null) return fallback;
  return sampleLut(lut, t);
}
