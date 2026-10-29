/**
 * Particle emission scheduling — when each particle in a burst/stream is
 * born, as a fraction of the system's lifetime.
 *
 * Ported from Godot's particle compute shader
 * (`servers/rendering/renderer_rd/shaders/particles.glsl`, `4.5.1-stable`;
 * NOT `particle_process_material.cpp`, which doesn't derive this):
 *
 * ```glsl
 * float restart_phase = float(index) / float(params.total_particles);
 *
 * if (FRAME.randomness > 0.0) {
 *     uint seed = FRAME.cycle;
 *     if (restart_phase >= FRAME.system_phase) {
 *         seed -= uint(1);
 *     }
 *     seed *= uint(params.total_particles);
 *     seed += uint(index);
 *     float random = float(hash(seed) % uint(65536)) / 65536.0;
 *     restart_phase += FRAME.randomness * random * 1.0 / float(params.total_particles);
 * }
 *
 * restart_phase *= (1.0 - FRAME.explosiveness);
 * ```
 *
 * We don't reproduce the shader's per-cycle `hash(seed)` (that decorrelates
 * repeat cycles of a looping emitter) — the workbench renders one shot, so
 * Task 11's seeded `rng` stands in for `random` directly, keeping the whole
 * simulation a pure function of the fight's seed. Two things carry over
 * exactly, because they're the load-bearing part of the formula:
 *
 * - `explosiveness` multiplies `(1 - explosiveness)` **after** the
 *   randomness term is added, not before — order matters, since it scales
 *   the randomness contribution down too.
 * - The randomness term is bounded by `1 / amount` (`randomness * random <
 *   randomness <= 1`), which is exactly why perturbing it can never reorder
 *   two particles: `restart_phase[i] < (i + 1) / amount <=
 *   restart_phase[i + 1]`'s minimum, for every draw of `random`.
 *
 * `restart_phase` is a fraction of the system's cycle in [0, 1); the cycle
 * lasts `lifetime` seconds, so the emission *time* is `restart_phase *
 * lifetime`.
 */

export interface EmissionParams {
  /** Number of particles in the system. */
  amount: number;
  /** Seconds the particle system's cycle lasts. */
  lifetime: number;
  /** 0 = spread evenly across the lifetime (Godot's default, a stream); 1 =
   *  every particle born at t=0 (a burst). Values between interpolate. */
  explosiveness: number;
  /** 0 = perfectly even spacing; >0 jitters each particle's birth time,
   *  bounded so it can never reorder emissions. */
  randomness: number;
}

/** Returns each particle's emission time (seconds from the system's start),
 *  in particle index order — always sorted ascending, since the randomness
 *  perturbation can't cross an index boundary (see module doc). */
export function emissionTimes(
  { amount, lifetime, explosiveness, randomness }: EmissionParams,
  rng: () => number,
): number[] {
  const times: number[] = [];
  for (let index = 0; index < amount; index++) {
    let restartPhase = index / amount;
    if (randomness > 0) {
      restartPhase += (randomness * rng()) / amount;
    }
    restartPhase *= 1 - explosiveness;
    times.push(restartPhase * lifetime);
  }
  return times;
}
