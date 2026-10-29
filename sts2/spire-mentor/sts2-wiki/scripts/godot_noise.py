#!/usr/bin/env python3
"""Evaluate Godot's `FastNoiseLite` exactly as Godot does, to bake dissolve
gradients the browser can sample from a PNG.

`vfx_monster_death.tscn` (the monster-death dissolve VFX) drives two
`FastNoiseLite` sub-resources through their `NoiseTexture2D` wrapper. Both
sub-resources set only `frequency` (0.0234 and 0.0084) — every other property
is Godot's default (`modules/noise/fastnoise_lite.h:98-107`):
`TYPE_SIMPLEX_SMOOTH` (upstream `NoiseType_OpenSimplex2S`), `FRACTAL_FBM`,
5 octaves, lacunarity 2.0, gain 0.5, weighted_strength 0.0, seed 0. The
`NoiseTexture2D` sub-resources set nothing, so baking is 512x512, non-seamless,
uninverted, with no color ramp — this module only needs to port that one path,
not seamless generation, domain warp, cellular/perlin/value noise types, or
color ramps.

Ported from Auburn/FastNoiseLite v1.1.1 (MIT), the exact header Godot vendors
at `thirdparty/noise/FastNoiseLite.h` — see `TransformNoiseCoordinate`,
`GenFractalFBm`, `SingleOpenSimplex2S`, `GradCoord`, `Hash` and
`CalculateFractalBounding`. Only the 2D path is ported (that's all any
consuming `.tscn` uses); 3D and every other `NoiseType`/`FractalType`
combination are out of scope. `fast_noise_lite()` additionally ports Godot's
own normalization, `Noise::_get_image`'s `p_normalize=true` branch
(`modules/noise/noise.cpp:87-136`): it is NOT a fixed [-1, 1] -> [0, 1] map —
it scans the WHOLE image for `min`/`max` first, then rescales each sample by
`(v - min) / (max - min)`. A fixed-range map produces plausible-looking noise
with the wrong contrast, and the dissolve edge is exactly where that would
show.

`fast_noise_lite()` returns that pre-quantization float in [0, 1], which is
what the oracle pins compare against — a uint8 pin would hide an error of up
to 1/255 inside a single quantization bucket. The agreement is close but NOT
per-bit: FastNoiseLite computes in C `float`, this module in Python `float`
(a double), so accumulated rounding leaves the two ~1e-7 apart (measured
across the pinned points). That is why `test_godot_noise.py` asserts to 4
decimal places rather than exactly; tightening those pins to full precision
would fail on the float/double gap, not on a porting bug.
`bake_noise_png()` then applies Godot's uint8 cast — `static_cast<uint8_t>
(CLAMP(v * 255.f, 0, 255))`, which TRUNCATES rather than rounds — to produce
the actual baked PNG.

One consequence of the per-image normalization is worth stating so it isn't
mistaken for a coverage gap: a constant factor on the raw noise CANCELS in
`(v - min) / (max - min)`, so `_OPEN_SIMPLEX2S_SCALE` is unobservable through
this module's public surface. Mutating it fails no test and changes no baked
pixel — not because it is untested, but because it cannot matter here. It
stays at the header's value for fidelity, and would start mattering the
moment anything consumed an un-normalized sample.

There is no Godot binary on this machine, so the reference values are NOT a
screenshot of a `NoiseTexture2D`. They are captured from a small harness
(`scripts/noise_oracle/oracle.cpp`) built directly against the same vendored
`FastNoiseLite.h` (`scripts/noise_oracle/FastNoiseLite.h`, byte-identical to
the upstream v1.1.1 tag), configured with the exact settings above, replicating
`Noise::_get_image`'s normalize pass. That is a STRONGER oracle than a
screenshot: it is the same source code Godot compiles in, not a rendering of
it. Reproduce:

    cd scripts/noise_oracle
    g++ -O2 -std=c++17 -o oracle oracle.cpp
    ./oracle 8 8 0.0234 0     # -> EXPECTED_00 / EXPECTED_35 in test_godot_noise.py
    ./oracle 64 64 0.0084 0   # -> range-check fixture

Each output line is "x y raw normalized" (row-major, y outer / x inner); the
4th column is the exact float `fast_noise_lite()` must reproduce at that
pixel, pre-quantization.
"""

from __future__ import annotations

from PIL import Image

# Auburn/FastNoiseLite.h: PrimeX/PrimeY (`static const int PrimeX = ...`).
_PRIME_X = 501125321
_PRIME_Y = 1136930381

# FastNoiseLite.h: `hash *= 0x27d4eb2d;` (Hash, both overloads).
_HASH_MULTIPLIER = 0x27D4EB2D

# FastNoiseLite.h SingleOpenSimplex2S: G2 = (3 - sqrt(3)) / 6, and the same
# constant used (as F2) in TransformNoiseCoordinate's skew.
_SQRT3 = 1.7320508075688772935274463415059
_G2 = (3 - _SQRT3) / 6
_F2 = 0.5 * (_SQRT3 - 1)

# The magic OpenSimplex2S normalization constant `SingleOpenSimplex2S` scales
# its accumulated `value` by at the end (FastNoiseLite.h:1283).
_OPEN_SIMPLEX2S_SCALE = 18.24196194486065

# Godot's FastNoiseLite wrapper defaults (fastnoise_lite.h:98-109) as used by
# every FastNoiseLite sub-resource in `vfx_monster_death.tscn` (only
# `frequency` is overridden per-instance).
_DEFAULT_OCTAVES = 5
_DEFAULT_LACUNARITY = 2.0
_DEFAULT_GAIN = 0.5
_DEFAULT_WEIGHTED_STRENGTH = 0.0

# FastNoiseLite.h `Lookup<T>::Gradients2D` — 128 unit-ish 2D gradient vectors
# (256 floats), transcribed programmatically from the vendored
# `noise_oracle/FastNoiseLite.h` so the literals here are byte-identical to
# upstream rather than hand-copied. Indexed by `GradCoord` via a hash mask
# (`hash & (127 << 1)`), x-component at an even index, y at `index | 1`.
_GRADIENTS_2D = (
    0.130526192220052,
    0.99144486137381,
    0.38268343236509,
    0.923879532511287,
    0.608761429008721,
    0.793353340291235,
    0.793353340291235,
    0.608761429008721,
    0.923879532511287,
    0.38268343236509,
    0.99144486137381,
    0.130526192220051,
    0.99144486137381,
    -0.130526192220051,
    0.923879532511287,
    -0.38268343236509,
    0.793353340291235,
    -0.60876142900872,
    0.608761429008721,
    -0.793353340291235,
    0.38268343236509,
    -0.923879532511287,
    0.130526192220052,
    -0.99144486137381,
    -0.130526192220052,
    -0.99144486137381,
    -0.38268343236509,
    -0.923879532511287,
    -0.608761429008721,
    -0.793353340291235,
    -0.793353340291235,
    -0.608761429008721,
    -0.923879532511287,
    -0.38268343236509,
    -0.99144486137381,
    -0.130526192220052,
    -0.99144486137381,
    0.130526192220051,
    -0.923879532511287,
    0.38268343236509,
    -0.793353340291235,
    0.608761429008721,
    -0.608761429008721,
    0.793353340291235,
    -0.38268343236509,
    0.923879532511287,
    -0.130526192220052,
    0.99144486137381,
    0.130526192220052,
    0.99144486137381,
    0.38268343236509,
    0.923879532511287,
    0.608761429008721,
    0.793353340291235,
    0.793353340291235,
    0.608761429008721,
    0.923879532511287,
    0.38268343236509,
    0.99144486137381,
    0.130526192220051,
    0.99144486137381,
    -0.130526192220051,
    0.923879532511287,
    -0.38268343236509,
    0.793353340291235,
    -0.60876142900872,
    0.608761429008721,
    -0.793353340291235,
    0.38268343236509,
    -0.923879532511287,
    0.130526192220052,
    -0.99144486137381,
    -0.130526192220052,
    -0.99144486137381,
    -0.38268343236509,
    -0.923879532511287,
    -0.608761429008721,
    -0.793353340291235,
    -0.793353340291235,
    -0.608761429008721,
    -0.923879532511287,
    -0.38268343236509,
    -0.99144486137381,
    -0.130526192220052,
    -0.99144486137381,
    0.130526192220051,
    -0.923879532511287,
    0.38268343236509,
    -0.793353340291235,
    0.608761429008721,
    -0.608761429008721,
    0.793353340291235,
    -0.38268343236509,
    0.923879532511287,
    -0.130526192220052,
    0.99144486137381,
    0.130526192220052,
    0.99144486137381,
    0.38268343236509,
    0.923879532511287,
    0.608761429008721,
    0.793353340291235,
    0.793353340291235,
    0.608761429008721,
    0.923879532511287,
    0.38268343236509,
    0.99144486137381,
    0.130526192220051,
    0.99144486137381,
    -0.130526192220051,
    0.923879532511287,
    -0.38268343236509,
    0.793353340291235,
    -0.60876142900872,
    0.608761429008721,
    -0.793353340291235,
    0.38268343236509,
    -0.923879532511287,
    0.130526192220052,
    -0.99144486137381,
    -0.130526192220052,
    -0.99144486137381,
    -0.38268343236509,
    -0.923879532511287,
    -0.608761429008721,
    -0.793353340291235,
    -0.793353340291235,
    -0.608761429008721,
    -0.923879532511287,
    -0.38268343236509,
    -0.99144486137381,
    -0.130526192220052,
    -0.99144486137381,
    0.130526192220051,
    -0.923879532511287,
    0.38268343236509,
    -0.793353340291235,
    0.608761429008721,
    -0.608761429008721,
    0.793353340291235,
    -0.38268343236509,
    0.923879532511287,
    -0.130526192220052,
    0.99144486137381,
    0.130526192220052,
    0.99144486137381,
    0.38268343236509,
    0.923879532511287,
    0.608761429008721,
    0.793353340291235,
    0.793353340291235,
    0.608761429008721,
    0.923879532511287,
    0.38268343236509,
    0.99144486137381,
    0.130526192220051,
    0.99144486137381,
    -0.130526192220051,
    0.923879532511287,
    -0.38268343236509,
    0.793353340291235,
    -0.60876142900872,
    0.608761429008721,
    -0.793353340291235,
    0.38268343236509,
    -0.923879532511287,
    0.130526192220052,
    -0.99144486137381,
    -0.130526192220052,
    -0.99144486137381,
    -0.38268343236509,
    -0.923879532511287,
    -0.608761429008721,
    -0.793353340291235,
    -0.793353340291235,
    -0.608761429008721,
    -0.923879532511287,
    -0.38268343236509,
    -0.99144486137381,
    -0.130526192220052,
    -0.99144486137381,
    0.130526192220051,
    -0.923879532511287,
    0.38268343236509,
    -0.793353340291235,
    0.608761429008721,
    -0.608761429008721,
    0.793353340291235,
    -0.38268343236509,
    0.923879532511287,
    -0.130526192220052,
    0.99144486137381,
    0.130526192220052,
    0.99144486137381,
    0.38268343236509,
    0.923879532511287,
    0.608761429008721,
    0.793353340291235,
    0.793353340291235,
    0.608761429008721,
    0.923879532511287,
    0.38268343236509,
    0.99144486137381,
    0.130526192220051,
    0.99144486137381,
    -0.130526192220051,
    0.923879532511287,
    -0.38268343236509,
    0.793353340291235,
    -0.60876142900872,
    0.608761429008721,
    -0.793353340291235,
    0.38268343236509,
    -0.923879532511287,
    0.130526192220052,
    -0.99144486137381,
    -0.130526192220052,
    -0.99144486137381,
    -0.38268343236509,
    -0.923879532511287,
    -0.608761429008721,
    -0.793353340291235,
    -0.793353340291235,
    -0.608761429008721,
    -0.923879532511287,
    -0.38268343236509,
    -0.99144486137381,
    -0.130526192220052,
    -0.99144486137381,
    0.130526192220051,
    -0.923879532511287,
    0.38268343236509,
    -0.793353340291235,
    0.608761429008721,
    -0.608761429008721,
    0.793353340291235,
    -0.38268343236509,
    0.923879532511287,
    -0.130526192220052,
    0.99144486137381,
    0.38268343236509,
    0.923879532511287,
    0.923879532511287,
    0.38268343236509,
    0.923879532511287,
    -0.38268343236509,
    0.38268343236509,
    -0.923879532511287,
    -0.38268343236509,
    -0.923879532511287,
    -0.923879532511287,
    -0.38268343236509,
    -0.923879532511287,
    0.38268343236509,
    -0.38268343236509,
    0.923879532511287,
)


def _wrap_i32(value: int) -> int:
    """Emulate C++ 32-bit signed `int` overflow.

    Kept for a byte-for-byte match against `FastNoiseLite.h`'s `int`-typed
    coordinate arithmetic (`i *= PrimeX`, `i + PrimeX`, etc.), which
    genuinely does overflow at the game's 512x512 bake resolution (verified:
    without this wrap, `i * PrimeX` alone exceeds 2**31 there). Mutation-
    tested this DOESN'T currently change any output, though: every wrapped
    value here flows only into `_hash`/`_grad_coord`, which reduce it through
    XOR, multiplication, and a final small bitmask — all of which commute
    with "mod 2**32" — so Python's unbounded integers land on the same low
    bits regardless of whether this wrap runs. It stays in as the faithful,
    line-for-line transliteration the brief asks for (and as a guard against
    a future caller that DOES compare `i`/`j` by magnitude, where the
    difference would matter), not because removing it currently breaks a
    test. See `task-9-report.md` for the mutation that found this.
    """
    value &= 0xFFFFFFFF
    if value >= 0x80000000:
        value -= 0x100000000
    return value


def _fast_floor(f: float) -> int:
    """Port of `FastNoiseLite::FastFloor` (FastNoiseLite.h:449-450)."""
    return int(f) if f >= 0 else int(f) - 1


def _hash(seed: int, x_primed: int, y_primed: int) -> int:
    """Port of `FastNoiseLite::Hash` (2D overload, FastNoiseLite.h:491-497)."""
    h = _wrap_i32(seed ^ x_primed ^ y_primed)
    return _wrap_i32(h * _HASH_MULTIPLIER)


def _grad_coord(seed: int, x_primed: int, y_primed: int, xd: float, yd: float) -> float:
    """Port of `FastNoiseLite::GradCoord` (2D overload, FastNoiseLite.h:529-539)."""
    h = _hash(seed, x_primed, y_primed)
    h = _wrap_i32(h ^ (h >> 15))
    h &= 127 << 1
    xg = _GRADIENTS_2D[h]
    yg = _GRADIENTS_2D[h | 1]
    return xd * xg + yd * yg


def _single_open_simplex2s(seed: int, x: float, y: float) -> float:
    """Port of `FastNoiseLite::SingleOpenSimplex2S` (2D overload,
    FastNoiseLite.h:1156-1284). `x`/`y` here are already frequency-scaled
    and skewed by `_transform_2d`.
    """
    i = _fast_floor(x)
    j = _fast_floor(y)
    xi = x - i
    yi = y - j

    i = _wrap_i32(i * _PRIME_X)
    j = _wrap_i32(j * _PRIME_Y)
    i1 = _wrap_i32(i + _PRIME_X)
    j1 = _wrap_i32(j + _PRIME_Y)

    t = (xi + yi) * _G2
    x0 = xi - t
    y0 = yi - t

    a0 = (2.0 / 3.0) - x0 * x0 - y0 * y0
    value = (a0 * a0) * (a0 * a0) * _grad_coord(seed, i, j, x0, y0)

    a1 = (2 * (1 - 2 * _G2) * (1 / _G2 - 2)) * t + (-2 * (1 - 2 * _G2) * (1 - 2 * _G2) + a0)
    x1 = x0 - (1 - 2 * _G2)
    y1 = y0 - (1 - 2 * _G2)
    value += (a1 * a1) * (a1 * a1) * _grad_coord(seed, i1, j1, x1, y1)

    xmyi = xi - yi
    if t > _G2:
        if xi + xmyi > 1:
            x2 = x0 + (3 * _G2 - 2)
            y2 = y0 + (3 * _G2 - 1)
            a2 = (2.0 / 3.0) - x2 * x2 - y2 * y2
            if a2 > 0:
                value += (
                    (a2 * a2)
                    * (a2 * a2)
                    * _grad_coord(
                        seed, _wrap_i32(i + (_PRIME_X << 1)), _wrap_i32(j + _PRIME_Y), x2, y2
                    )
                )
        else:
            x2 = x0 + _G2
            y2 = y0 + (_G2 - 1)
            a2 = (2.0 / 3.0) - x2 * x2 - y2 * y2
            if a2 > 0:
                value += (
                    (a2 * a2) * (a2 * a2) * _grad_coord(seed, i, _wrap_i32(j + _PRIME_Y), x2, y2)
                )

        if yi - xmyi > 1:
            x3 = x0 + (3 * _G2 - 1)
            y3 = y0 + (3 * _G2 - 2)
            a3 = (2.0 / 3.0) - x3 * x3 - y3 * y3
            if a3 > 0:
                value += (
                    (a3 * a3)
                    * (a3 * a3)
                    * _grad_coord(
                        seed, _wrap_i32(i + _PRIME_X), _wrap_i32(j + (_PRIME_Y << 1)), x3, y3
                    )
                )
        else:
            x3 = x0 + (_G2 - 1)
            y3 = y0 + _G2
            a3 = (2.0 / 3.0) - x3 * x3 - y3 * y3
            if a3 > 0:
                value += (
                    (a3 * a3) * (a3 * a3) * _grad_coord(seed, _wrap_i32(i + _PRIME_X), j, x3, y3)
                )
    else:
        if xi + xmyi < 0:
            x2 = x0 + (1 - _G2)
            y2 = y0 - _G2
            a2 = (2.0 / 3.0) - x2 * x2 - y2 * y2
            if a2 > 0:
                value += (
                    (a2 * a2) * (a2 * a2) * _grad_coord(seed, _wrap_i32(i - _PRIME_X), j, x2, y2)
                )
        else:
            x2 = x0 + (_G2 - 1)
            y2 = y0 + _G2
            a2 = (2.0 / 3.0) - x2 * x2 - y2 * y2
            if a2 > 0:
                value += (
                    (a2 * a2) * (a2 * a2) * _grad_coord(seed, _wrap_i32(i + _PRIME_X), j, x2, y2)
                )

        if yi < xmyi:
            x2 = x0 - _G2
            y2 = y0 - (_G2 - 1)
            a2 = (2.0 / 3.0) - x2 * x2 - y2 * y2
            if a2 > 0:
                value += (
                    (a2 * a2) * (a2 * a2) * _grad_coord(seed, i, _wrap_i32(j - _PRIME_Y), x2, y2)
                )
        else:
            x2 = x0 + _G2
            y2 = y0 + (_G2 - 1)
            a2 = (2.0 / 3.0) - x2 * x2 - y2 * y2
            if a2 > 0:
                value += (
                    (a2 * a2) * (a2 * a2) * _grad_coord(seed, i, _wrap_i32(j + _PRIME_Y), x2, y2)
                )

    return value * _OPEN_SIMPLEX2S_SCALE


def _transform_2d(x: float, y: float, frequency: float) -> tuple[float, float]:
    """Port of `FastNoiseLite::TransformNoiseCoordinate` (2D overload,
    FastNoiseLite.h:663-684), OpenSimplex2/2S branch only (the skew this
    module needs; the `default: break` branch for other noise types is out
    of scope).
    """
    x *= frequency
    y *= frequency
    t = (x + y) * _F2
    return x + t, y + t


def _lerp(a: float, b: float, t: float) -> float:
    """Port of `FastNoiseLite::Lerp` (FastNoiseLite.h:455)."""
    return a + t * (b - a)


def _fractal_bounding(octaves: int, gain: float) -> float:
    """Port of `FastNoiseLite::CalculateFractalBounding` (FastNoiseLite.h:473-484)."""
    g = abs(gain)
    amp = g
    amp_fractal = 1.0
    for _ in range(1, octaves):
        amp_fractal += amp
        amp *= g
    return 1.0 / amp_fractal


def _fbm_2d(
    seed: int,
    x: float,
    y: float,
    *,
    octaves: int,
    lacunarity: float,
    gain: float,
    weighted_strength: float,
) -> float:
    """Port of `FastNoiseLite::GenFractalFBm` (2D overload, FastNoiseLite.h:844-863).
    `x`/`y` are already frequency-scaled and skewed (`_transform_2d` runs once,
    in `GetNoise`, before the fractal loop — NOT once per octave).
    """
    amp = _fractal_bounding(octaves, gain)
    total = 0.0
    for _ in range(octaves):
        noise = _single_open_simplex2s(seed, x, y)
        seed += 1
        total += noise * amp
        amp *= _lerp(1.0, min(noise + 1.0, 2.0) * 0.5, weighted_strength)
        x *= lacunarity
        y *= lacunarity
        amp *= gain
    return total


def _get_noise_2d(x: float, y: float, *, frequency: float, seed: int) -> float:
    """Port of `FastNoiseLite::GetNoise` (2D overload, FastNoiseLite.h:295-313),
    fixed to the OpenSimplex2S + FBm path (`FastNoiseLite.h:302-312`'s other
    branches — Ridged, PingPong, the fractal-less default — are out of scope;
    see the module docstring).
    """
    tx, ty = _transform_2d(x, y, frequency)
    return _fbm_2d(
        seed,
        tx,
        ty,
        octaves=_DEFAULT_OCTAVES,
        lacunarity=_DEFAULT_LACUNARITY,
        gain=_DEFAULT_GAIN,
        weighted_strength=_DEFAULT_WEIGHTED_STRENGTH,
    )


def fast_noise_lite(width: int, height: int, frequency: float, seed: int = 0) -> list[list[float]]:
    """Sample Godot's default `FastNoiseLite` (OpenSimplex2S + FBm, 5 octaves,
    lacunarity 2.0, gain 0.5) over a `width` x `height` pixel grid and
    normalize it exactly as `Noise::_get_image(..., p_normalize=true)` does.

    Args:
        width: Grid width in pixels (pixel x-coordinates 0..width-1 are the
            raw noise-function input, then scaled by `frequency`).
        height: Grid height in pixels, same convention as `width`.
        frequency: `FastNoiseLite.frequency`. `vfx_monster_death.tscn` uses
            0.0234 and 0.0084 for its two sub-resources.
        seed: `FastNoiseLite.seed`. Godot's (and this game's) default is 0.

    Returns:
        `height` rows of `width` floats in [0, 1], `result[y][x]` — row-major,
        matching `Noise::_get_image`'s loop nesting. These are the
        PRE-QUANTIZATION normalized values; `bake_noise_png` quantizes them
        to the uint8 PNG Godot would actually produce.
    """
    raw = [
        [_get_noise_2d(float(x), float(y), frequency=frequency, seed=seed) for x in range(width)]
        for y in range(height)
    ]
    flat = [v for row in raw for v in row]
    min_val = min(flat)
    max_val = max(flat)
    if max_val == min_val:
        # Noise::_get_image's degenerate branch (noise.cpp:120-121): every
        # sample maps to ivalue 0 rather than dividing by zero.
        return [[0.0] * width for _ in range(height)]
    return [[(v - min_val) / (max_val - min_val) for v in row] for row in raw]


def bake_noise_png(path: str, width: int, height: int, frequency: float, seed: int = 0) -> None:
    """Bake `fast_noise_lite` to an 8-bit grayscale PNG, quantizing exactly as
    `Noise::_get_image` does: `static_cast<uint8_t>(CLAMP(v * 255.f, 0, 255))`
    — a TRUNCATING cast, not a rounding one. `int()` on a non-negative Python
    float truncates the same way, so this must not add the usual `+ 0.5`
    rounding bias other bakers in this codebase use for RGBA output.

    Args:
        path: Output PNG path.
        width: Grid width in pixels.
        height: Grid height in pixels.
        frequency: `FastNoiseLite.frequency` — see `fast_noise_lite`.
        seed: `FastNoiseLite.seed` — see `fast_noise_lite`.
    """
    noise = fast_noise_lite(width, height, frequency, seed)
    pixels = bytes(min(255, max(0, int(v * 255.0))) for row in noise for v in row)
    Image.frombytes("L", (width, height), pixels).save(path)
