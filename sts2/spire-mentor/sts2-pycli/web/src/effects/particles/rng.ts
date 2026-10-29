/**
 * Deterministic seeded RNG for the particle simulator.
 *
 * A replayed fight must render byte-identical VFX, so `Math.random()` is
 * forbidden here — every stream must be a pure function of its seed.
 * mulberry32 (public domain, Tommy Ettinger): 32-bit state, one `Math.imul`
 * multiply-mix, full 2^32 period, good enough distribution for particle
 * scatter/jitter (this is not cryptographic).
 */

/** Returns a `() => number` generator producing values in [0, 1), fully
 *  determined by `seed`: the same seed always yields the same sequence. */
export function makeRng(seed: number): () => number {
  let state = seed | 0;
  return function rng(): number {
    state = (state + 0x6d2b79f5) | 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
