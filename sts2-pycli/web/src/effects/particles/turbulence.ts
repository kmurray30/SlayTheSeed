/**
 * Godot 4.5.1's particle turbulence — 4D gradient noise driving a curl field
 * — transcribed from `scene/resources/particle_process_material.cpp` at tag
 * `4.5.1-stable` (the shader-source string literals building `grad`, `noise`,
 * `noise_3x`, `curl_3d`, `get_noise_direction`, lines 403-485 of that file).
 *
 * This is a **transcription**, not a reimplementation: every magic constant
 * (the `grad` dot-product coefficients, `2365.952041`,
 * `1.7320508 * 2048.333333`, the `10/-15/6` fade polynomial, the `70.0`
 * contrast multiplier) is copied verbatim from the GLSL. Two GLSL semantics
 * that do NOT match their obvious TS equivalents and matter here:
 *
 * - `fract(x)` is `x - floor(x)`, not `x % 1` — for negative `x` those differ
 *   (`fract(-0.3) === 0.7`, `-0.3 % 1 === -0.3`), and `grad` depends on this
 *   for negative coordinates.
 * - `mix(a, b, t)` is the UNCLAMPED lerp `a + (b - a) * t` — GLSL has no
 *   built-in clamping `mix`. `get_noise_direction` exploits this:
 *   `mix(0.9 * dir, dir, turbulence_noise_strength - 9.0)` with the game's
 *   authored strength of 5.0 gives `t = -4.0`, an EXTRAPOLATION, collapsing
 *   the result to `0.5 * dir` (magnitude 0.5, not a unit vector). A clamped
 *   lerp would silently produce a different field here.
 *
 * `curl_3d` ends in `normalize(...)`, which GLSL/Godot does not guard against
 * a zero-length input (that's an NaN direction, hardware-dependent in real
 * GLSL, `[NaN, NaN, NaN]` in this port via plain IEEE division). Transcribed
 * faithfully, unguarded, same as the source. Empirically swept (see
 * `__tests__/turbulence.test.ts`'s "never produces NaN" case): 51,005 samples
 * across a wide position/time grid at the game's authored parameters
 * (strength 5.0, scale 10.1, speed (0.1,0.1,0.1), speedRandom 1.0) — zero
 * NaNs. Not a proof (continuous noise could in principle hit an exact
 * cancellation somewhere unsampled), but the practical risk is confirmed
 * negligible rather than merely assumed.
 */

import { sampleLut } from "./lut";

type Vec3 = readonly [number, number, number];
type Vec4 = readonly [number, number, number, number];

/** GLSL `fract(x)`: `x - floor(x)`. NOT `x % 1` — differs for negative `x`. */
function fract(x: number): number {
  return x - Math.floor(x);
}

/** GLSL's unclamped `mix(a, b, t)`: `a + (b - a) * t`. Extrapolates for
 *  `t` outside [0, 1] — see module doc. */
function mix(a: number, b: number, t: number): number {
  return a + (b - a) * t;
}

function dot4(a: Vec4, b: Vec4): number {
  return a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
}

function add4(a: Vec4, b: Vec4): Vec4 {
  return [a[0] + b[0], a[1] + b[1], a[2] + b[2], a[3] + b[3]];
}

function sub4(a: Vec4, b: Vec4): Vec4 {
  return [a[0] - b[0], a[1] - b[1], a[2] - b[2], a[3] - b[3]];
}

function floor4(a: Vec4): Vec4 {
  return [Math.floor(a[0]), Math.floor(a[1]), Math.floor(a[2]), Math.floor(a[3])];
}

/**
 * ```glsl
 * vec4 grad(vec4 p) {
 * 	p = fract(vec4(
 * 			dot(p, vec4(0.143081, 0.001724, 0.280166, 0.262771)),
 * 			dot(p, vec4(0.645401, -0.047791, -0.146698, 0.595016)),
 * 			dot(p, vec4(-0.499665, -0.095734, 0.425674, -0.207367)),
 * 			dot(p, vec4(-0.013596, -0.848588, 0.423736, 0.17044))));
 * 	return fract((p.xyzw * p.yzwx) * 2365.952041) * 2.0 - 1.0;
 * }
 * ```
 * `p.xyzw * p.yzwx` is a component-wise multiply of `p` against itself
 * rotated left by one lane: `[p.x*p.y, p.y*p.z, p.z*p.w, p.w*p.x]`.
 */
function grad(p: Vec4): Vec4 {
  const px = fract(dot4(p, [0.143081, 0.001724, 0.280166, 0.262771]));
  const py = fract(dot4(p, [0.645401, -0.047791, -0.146698, 0.595016]));
  const pz = fract(dot4(p, [-0.499665, -0.095734, 0.425674, -0.207367]));
  const pw = fract(dot4(p, [-0.013596, -0.848588, 0.423736, 0.17044]));
  return [
    fract(px * py * 2365.952041) * 2.0 - 1.0,
    fract(py * pz * 2365.952041) * 2.0 - 1.0,
    fract(pz * pw * 2365.952041) * 2.0 - 1.0,
    fract(pw * px * 2365.952041) * 2.0 - 1.0,
  ];
}

/**
 * 4D value noise with quintic fade interpolation across all 16 hypercube
 * corners.
 *
 * ```glsl
 * float noise(vec4 coord) {
 * 	// Domain rotation to improve the look of XYZ slices + animation patterns.
 * 	coord = vec4(
 * 			coord.xyz + dot(coord, vec4(vec3(-0.1666667), -0.5)),
 * 			dot(coord, vec4(0.5)));
 *
 * 	vec4 base = floor(coord), delta = coord - base;
 * 	... 16 grad() calls at the hypercube corners ...
 * 	vec4 fade = delta * delta * delta * (10.0 + delta * (-15.0 + delta * 6.0));
 * 	vec4 result_W0 = mix(result_0123, result_89AB, fade.w), result_W1 = mix(result_4567, result_CDEF, fade.w);
 * 	vec4 result_WZ = mix(result_W0, result_W1, fade.z);
 * 	vec2 result_WZY = mix(result_WZ.xy, result_WZ.zw, fade.y);
 * 	return mix(result_WZY.x, result_WZY.y, fade.x);
 * }
 * ```
 */
function noise(coordIn: Vec4): number {
  const rot = dot4(coordIn, [-0.1666667, -0.1666667, -0.1666667, -0.5]);
  const coord: Vec4 = [
    coordIn[0] + rot,
    coordIn[1] + rot,
    coordIn[2] + rot,
    dot4(coordIn, [0.5, 0.5, 0.5, 0.5]),
  ];

  const base = floor4(coord);
  const delta = sub4(coord, base);

  const corner = (dx: number, dy: number, dz: number, dw: number): Vec4 =>
    grad(add4(base, [dx, dy, dz, dw]));

  const grad0000 = corner(0, 0, 0, 0);
  const grad1000 = corner(1, 0, 0, 0);
  const grad0100 = corner(0, 1, 0, 0);
  const grad1100 = corner(1, 1, 0, 0);
  const grad0010 = corner(0, 0, 1, 0);
  const grad1010 = corner(1, 0, 1, 0);
  const grad0110 = corner(0, 1, 1, 0);
  const grad1110 = corner(1, 1, 1, 0);
  const grad0001 = corner(0, 0, 0, 1);
  const grad1001 = corner(1, 0, 0, 1);
  const grad0101 = corner(0, 1, 0, 1);
  const grad1101 = corner(1, 1, 0, 1);
  const grad0011 = corner(0, 0, 1, 1);
  const grad1011 = corner(1, 0, 1, 1);
  const grad0111 = corner(0, 1, 1, 1);
  const grad1111 = corner(1, 1, 1, 1);

  const dotCorner = (dx: number, dy: number, dz: number, dw: number, g: Vec4): number =>
    dot4(sub4(delta, [dx, dy, dz, dw]), g);

  const result0123: Vec4 = [
    dotCorner(0, 0, 0, 0, grad0000),
    dotCorner(1, 0, 0, 0, grad1000),
    dotCorner(0, 1, 0, 0, grad0100),
    dotCorner(1, 1, 0, 0, grad1100),
  ];
  const result4567: Vec4 = [
    dotCorner(0, 0, 1, 0, grad0010),
    dotCorner(1, 0, 1, 0, grad1010),
    dotCorner(0, 1, 1, 0, grad0110),
    dotCorner(1, 1, 1, 0, grad1110),
  ];
  const result89AB: Vec4 = [
    dotCorner(0, 0, 0, 1, grad0001),
    dotCorner(1, 0, 0, 1, grad1001),
    dotCorner(0, 1, 0, 1, grad0101),
    dotCorner(1, 1, 0, 1, grad1101),
  ];
  const resultCDEF: Vec4 = [
    dotCorner(0, 0, 1, 1, grad0011),
    dotCorner(1, 0, 1, 1, grad1011),
    dotCorner(0, 1, 1, 1, grad0111),
    dotCorner(1, 1, 1, 1, grad1111),
  ];

  const fadeComponent = (d: number): number => d * d * d * (10.0 + d * (-15.0 + d * 6.0));
  const fade: Vec4 = [
    fadeComponent(delta[0]),
    fadeComponent(delta[1]),
    fadeComponent(delta[2]),
    fadeComponent(delta[3]),
  ];

  const resultW0: Vec4 = [
    mix(result0123[0], result89AB[0], fade[3]),
    mix(result0123[1], result89AB[1], fade[3]),
    mix(result0123[2], result89AB[2], fade[3]),
    mix(result0123[3], result89AB[3], fade[3]),
  ];
  const resultW1: Vec4 = [
    mix(result4567[0], resultCDEF[0], fade[3]),
    mix(result4567[1], resultCDEF[1], fade[3]),
    mix(result4567[2], resultCDEF[2], fade[3]),
    mix(result4567[3], resultCDEF[3], fade[3]),
  ];
  const resultWZ: Vec4 = [
    mix(resultW0[0], resultW1[0], fade[2]),
    mix(resultW0[1], resultW1[1], fade[2]),
    mix(resultW0[2], resultW1[2], fade[2]),
    mix(resultW0[3], resultW1[3], fade[2]),
  ];
  const resultWZYx = mix(resultWZ[0], resultWZ[2], fade[1]);
  const resultWZYy = mix(resultWZ[1], resultWZ[3], fade[1]);
  return mix(resultWZYx, resultWZYy, fade[0]);
}

/**
 * ```glsl
 * // Curl 3D and three-noise function with friendly permission by Isaac Cohen.
 * // Modified to accept 4D noise.
 * vec3 noise_3x(vec4 p) {
 * 	float s = noise(p);
 * 	float s1 = noise(p + vec4(vec3(0.0), 1.7320508 * 2048.333333));
 * 	float s2 = noise(p - vec4(vec3(0.0), 1.7320508 * 2048.333333));
 * 	vec3 c = vec3(s, s1, s2);
 * 	return c;
 * }
 * ```
 */
function noise3x(p: Vec4): Vec3 {
  const offset = 1.7320508 * 2048.333333;
  const s = noise(p);
  const s1 = noise(add4(p, [0, 0, 0, offset]));
  const s2 = noise(sub4(p, [0, 0, 0, offset]));
  return [s, s1, s2];
}

/**
 * ```glsl
 * vec3 curl_3d(vec4 p, float c) {
 * 	float epsilon = 0.001 + c;
 * 	vec4 dx = vec4(epsilon, 0.0, 0.0, 0.0);
 * 	vec4 dy = vec4(0.0, epsilon, 0.0, 0.0);
 * 	vec4 dz = vec4(0.0, 0.0, epsilon, 0.0);
 * 	vec3 x0 = noise_3x(p - dx).xyz;
 * 	vec3 x1 = noise_3x(p + dx).xyz;
 * 	vec3 y0 = noise_3x(p - dy).xyz;
 * 	vec3 y1 = noise_3x(p + dy).xyz;
 * 	vec3 z0 = noise_3x(p - dz).xyz;
 * 	vec3 z1 = noise_3x(p + dz).xyz;
 * 	float x = (y1.z - y0.z) - (z1.y - z0.y);
 * 	float y = (z1.x - z0.x) - (x1.z - x0.z);
 * 	float z = (x1.y - x0.y) - (y1.x - y0.x);
 * 	return normalize(vec3(x, y, z));
 * }
 * ```
 * `normalize` on a zero vector is unguarded here, matching the source
 * exactly — see module doc for reachability discussion.
 */
function curl3d(p: Vec4, c: number): Vec3 {
  const epsilon = 0.001 + c;
  const dx: Vec4 = [epsilon, 0, 0, 0];
  const dy: Vec4 = [0, epsilon, 0, 0];
  const dz: Vec4 = [0, 0, epsilon, 0];

  const x0 = noise3x(sub4(p, dx));
  const x1 = noise3x(add4(p, dx));
  const y0 = noise3x(sub4(p, dy));
  const y1 = noise3x(add4(p, dy));
  const z0 = noise3x(sub4(p, dz));
  const z1 = noise3x(add4(p, dz));

  const x = y1[2] - y0[2] - (z1[1] - z0[1]);
  const y = z1[0] - z0[0] - (x1[2] - x0[2]);
  const z = x1[1] - x0[1] - (y1[0] - y0[0]);

  const len = Math.hypot(x, y, z);
  // No zero-length guard, matching Godot: 0/0 -> NaN, same as unguarded GLSL.
  return [x / len, y / len, z / len];
}

/** The subset of `vfx_attack_blunt`-style manifest turbulence fields this
 *  module needs — `turbulenceNoise*` for `noiseDirection`, plus
 *  `influence*` for `turbulenceDisplacement`. Field names track
 *  `manifest.ts`'s `turbulence*` keys (minus the `turbulence` prefix). */
export interface TurbulenceParams {
  /** `turbulenceNoiseStrength`. */
  strength: number;
  /** `turbulenceNoiseScale`. */
  scale: number;
  /** `turbulenceNoiseSpeed`. */
  speed: Vec3;
  /** `turbulenceNoiseSpeedRandom` — the noise field's 4th (time) axis speed. */
  speedRandom: number;
  /** `turbulenceInfluenceMin` — low end of the per-particle random blend
   *  factor Godot draws once per particle (`dynamic_params.turb_influence`).
   *  `turbulenceDisplacement` defaults its `turbInfluence` argument to this
   *  range's midpoint when the caller doesn't supply a drawn value — see
   *  that function's doc. */
  influenceMin: number;
  /** `turbulenceInfluenceMax`. */
  influenceMax: number;
  /** Baked `turbulence_influence_over_life` curve (Task 2's `bake_curve`
   *  output — `manifest.ts`'s `turbulenceInfluenceOverLifeCurve`, loaded).
   *  `null`/empty matches Godot's no-curve-assigned fallback: a constant
   *  `turbulence_influence = 1.0`.
   *
   *  Measured across the committed manifest (`sts2-wiki/site/public/images/
   *  vfx/manifest.json`): of the 13 particle-kind composites, **10 emitters
   *  have `turbulenceEnabled: true`, and every one of those 10 has this field
   *  `null`** — including `vfx_attack_blunt`'s `vfx_common_specks`. So today
   *  every real turbulence-enabled emitter takes Godot's constant-1.0 branch;
   *  the curve-sampling branch below is exercised only by synthetic test
   *  fixtures, not by any in-scope effect. Kept (not deleted) because Godot
   *  supports it and a future effect may author one — but a reader should not
   *  assume this path is validated against real bake data. */
  influenceOverLifeLut: readonly number[] | null;
}

/**
 * Port of `get_noise_direction`:
 * ```glsl
 * vec3 get_noise_direction(vec3 pos) {
 * 	float adj_contrast = max((turbulence_noise_strength - 1.0), 0.0) * 70.0;
 * 	vec4 noise_time = TIME * vec4(turbulence_noise_speed, turbulence_noise_speed_random);
 * 	vec4 noise_pos = vec4(pos * turbulence_noise_scale, 0.0);
 * 	vec3 noise_direction = curl_3d(noise_pos + noise_time, adj_contrast);
 * 	noise_direction = mix(0.9 * noise_direction, noise_direction, turbulence_noise_strength - 9.0);
 * 	return noise_direction;
 * }
 * ```
 * `pos` is Godot's `TRANSFORM[3].xyz` (the particle's current world
 * position); `TIME` is the shader's global animation clock, threaded through
 * here as `time`.
 *
 * NOT unit length in general — see module doc's `mix` extrapolation note.
 * With the game's authored `strength = 5.0`, this returns a vector of
 * magnitude 0.5, not 1.0.
 */
export function noiseDirection(pos: Vec3, time: number, params: TurbulenceParams): Vec3 {
  const adjContrast = Math.max(params.strength - 1.0, 0.0) * 70.0;
  const noiseTime: Vec4 = [
    time * params.speed[0],
    time * params.speed[1],
    time * params.speed[2],
    time * params.speedRandom,
  ];
  const noisePos: Vec4 = [pos[0] * params.scale, pos[1] * params.scale, pos[2] * params.scale, 0];
  const [dx, dy, dz] = curl3d(add4(noisePos, noiseTime), adjContrast);
  const t = params.strength - 9.0;
  return [mix(0.9 * dx, dx, t), mix(0.9 * dy, dy, t), mix(0.9 * dz, dz, t)];
}

function clamp01(x: number): number {
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

/**
 * The scalar blend factor turbulence applies to a particle at a given point
 * in its life — Godot's `vel_infl`, shared by both `get_noise_direction`
 * application sites (`particle_process_material.cpp:1020-1055`):
 * ```glsl
 * float turbulence_influence = textureLod(turbulence_influence_over_life, vec2(lifetime_percent, 0.0), 0.0).r; // or 1.0 if unset
 * ...
 * float vel_infl = clamp(dynamic_params.turb_influence * turbulence_influence, 0.0, 1.0) * (DELTA <= 0.0 ? 0.0 : 1.0);
 * ```
 * `dynamic_params.turb_influence` is drawn ONCE PER PARTICLE, uniformly from
 * `[influenceMin, influenceMax]` — with the manifest's
 * `turbulenceInfluenceOverLifeCurve` null for every in-scope emitter (see
 * `TurbulenceParams.influenceOverLifeLut`'s doc), this per-particle draw is
 * the ONLY source of real variation turbulence has today; `turbulence_influence`
 * itself is a constant 1.0. That makes `turbInfluence` load-bearing, not a
 * detail to default away silently.
 *
 * `turbInfluence` is therefore an explicit parameter — the caller must
 * supply the specific particle's drawn value (e.g. via Task 11's `makeRng`,
 * uniform over `[params.influenceMin, params.influenceMax]`). It defaults to
 * the range's midpoint ONLY so the brief's existing 2-arg call sites keep
 * compiling; that default is representative-only (no real particle ever
 * takes exactly the midpoint) and must not be relied on by the real
 * per-particle simulator — a future task wiring this in for real rendering
 * must pass a seeded per-particle draw here, not lean on the default.
 *
 * The `DELTA <= 0.0` gate guards Godot's first uninitialized simulation
 * frame — not applicable to a pure function of `lifetimePercent`, so it's
 * dropped here.
 */
export function turbulenceDisplacement(
  lifetimePercent: number,
  params: TurbulenceParams,
  turbInfluence: number = (params.influenceMin + params.influenceMax) / 2,
): number {
  const lut = params.influenceOverLifeLut;
  const curveValue = lut && lut.length > 0 ? sampleLut(lut, lifetimePercent) : 1.0;
  return clamp01(turbInfluence * curveValue);
}
