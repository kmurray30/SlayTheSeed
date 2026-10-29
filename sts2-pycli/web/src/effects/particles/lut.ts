/**
 * Sampling for the baked curve/gradient LUTs the extracted VFX manifest
 * ships (`manifest.ts`'s `*Curve`/`colorRamp` fields, each a path to a
 * `vfx_particles/<hash>.json` array).
 *
 * Those arrays are produced by Task 2's `bake_curve(c, n=256)`: `n` samples
 * of the Godot `Curve` taken evenly across [0, 1], **endpoint-inclusive** —
 * `lut[0]` is the value at t=0 and `lut[n-1]` is the value at t=1. So index
 * `i` maps back to `t = i / (n - 1)`, NOT `i / n`; the latter would put
 * `lut[n-1]` at `t = (n-1)/n < 1` and silently shift every sample.
 */

/** The two neighboring LUT indices to interpolate between for a given `t`,
 *  plus the interpolation fraction between them — endpoint-inclusive index
 *  math (see module doc), shared by `sampleLut` and any future per-channel
 *  sampler (e.g. the inline RGBA `colorInitialRamp` table, out of scope
 *  here). `t` is clamped to [0, 1] rather than wrapped: VFX curves are
 *  authored over a bounded domain, not periodic.
 *
 *  `length <= 1` collapses to index 0 with `f = 0` — the caller decides what
 *  index 0 means for a zero-length table (there isn't one to look up). */
export function lutSamplePoint(
  length: number,
  t: number,
): { i0: number; i1: number; f: number } {
  if (length <= 1) return { i0: 0, i1: 0, f: 0 };
  const clamped = t < 0 ? 0 : t > 1 ? 1 : t;
  const pos = clamped * (length - 1);
  const i0 = Math.floor(pos);
  const i1 = Math.min(i0 + 1, length - 1);
  return { i0, i1, f: pos - i0 };
}

/** Linearly interpolates `lut` at `t` (clamped to [0, 1], not wrapped).
 *
 *  - A 1-entry LUT returns that entry for every `t`.
 *  - A 0-entry LUT (no curve baked) returns 0 — documented, not thrown: a
 *    `null` manifest field (no curve authored) should never reach here, but
 *    an empty array degrading to "no effect" rather than a crash keeps a
 *    malformed/missing bake non-fatal for the whole particle system it feeds. */
export function sampleLut(lut: readonly number[], t: number): number {
  if (lut.length === 0) return 0;
  const { i0, i1, f } = lutSamplePoint(lut.length, t);
  return lut[i0] + (lut[i1] - lut[i0]) * f;
}
