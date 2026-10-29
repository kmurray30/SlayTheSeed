"""Tests for `scripts/godot_noise.py`.

Pinned values are captured from `scripts/noise_oracle/oracle.cpp`, built
against the vendored `scripts/noise_oracle/FastNoiseLite.h` (upstream
Auburn/FastNoiseLite v1.1.1, MIT — the exact source Godot 4.5.1 vendors),
configured exactly as Godot's `FastNoiseLite` wrapper defaults it, plus
`vfx_monster_death.tscn`'s two `frequency` overrides. See `godot_noise.py`'s
module docstring for the full recipe. Reproduce with:

    cd scripts/noise_oracle
    g++ -O2 -std=c++17 -o oracle oracle.cpp
    ./oracle 8 8 0.0234 0
    ./oracle 64 64 0.0084 0
    ./oracle 512 512 0.0234 0
    ./oracle 512 512 0.0084 0

Each `oracle` output line is "x y raw normalized" (row-major: y outer, x
inner). The pins below use the 4th column — the pre-quantization normalized
float — at each (x, y), read off as `noise[y][x]`.

The 512x512 pins matter beyond "bigger is more thorough": at that
resolution, the fractal's later octaves push primed coordinates past 2**31,
genuinely overflowing a 32-bit signed `int` the way `FastNoiseLite.h`'s
`int`-typed coordinate arithmetic does. (Mutation-testing found that this
specific port doesn't actually need `_wrap_i32` to get the right answer —
see its docstring and `task-9-report.md` — but 512x512 is still the
resolution `vfx_monster_death.tscn` actually bakes at, so pinning it directly
tests the shipped scale rather than only a scaled-down proxy.)
"""

import tempfile
import unittest
from pathlib import Path

from PIL import Image

from scripts.godot_noise import bake_noise_png, fast_noise_lite

# oracle: `0 0 6.87847806e-29 0.205369562`
EXPECTED_Y0_X0 = 0.205369562
# oracle: `1 0 -0.000919251237 0.203218997`
EXPECTED_Y0_X1 = 0.203218997
# oracle: `5 3 0.167271703 0.596695065` -- the brief's `noise[3][5]`.
EXPECTED_Y3_X5 = 0.596695065
# oracle: `4 4 0.234576419 0.754151762`
EXPECTED_Y4_X4 = 0.754151762
# oracle: `7 7 0.258541435 0.810216963`
EXPECTED_Y7_X7 = 0.810216963

# 512x512 pins (Godot's default `NoiseTexture2D` size, and what
# `vfx_monster_death.tscn` actually bakes at) -- see the module docstring for
# why these specifically exercise the int32 overflow the smaller grids can't
# reach.
# `./oracle 512 512 0.0234 0`, pixel (511, 511):
EXPECTED_512_FREQ_0234_Y511_X511 = 0.697892129
# `./oracle 512 512 0.0084 0`, pixel (511, 511):
EXPECTED_512_FREQ_0084_Y511_X511 = 0.198026508


class TestFastNoiseLite(unittest.TestCase):
    def test_matches_godots_simplex_smooth_at_known_points(self):
        # Values captured from the C++ oracle (see module docstring for the
        # capture recipe). Pinning VALUES is the whole point: a noise that
        # merely "looks like noise" is not the game's noise, and the
        # dissolve edge is exactly where the difference shows.
        noise = fast_noise_lite(8, 8, frequency=0.0234, seed=0)
        self.assertAlmostEqual(noise[0][0], EXPECTED_Y0_X0, places=4)
        self.assertAlmostEqual(noise[3][5], EXPECTED_Y3_X5, places=4)

    def test_matches_godot_at_additional_interior_points(self):
        # Beyond the brief's two pins: chosen to be sensitive to narrower
        # mutations (wrong octave count, wrong lacunarity, a transposed x/y
        # axis, a wrong gradient-table stride) that two points alone can
        # miss by coincidence. See the mutation report in task-9-report.md.
        noise = fast_noise_lite(8, 8, frequency=0.0234, seed=0)
        self.assertAlmostEqual(noise[0][1], EXPECTED_Y0_X1, places=4)
        self.assertAlmostEqual(noise[4][4], EXPECTED_Y4_X4, places=4)
        self.assertAlmostEqual(noise[7][7], EXPECTED_Y7_X7, places=4)

    def test_output_is_normalised_to_unit_range(self):
        noise = fast_noise_lite(64, 64, frequency=0.0084, seed=0)
        flat = [v for row in noise for v in row]
        self.assertGreaterEqual(min(flat), 0.0)
        self.assertLessEqual(max(flat), 1.0)
        # A per-image rescale satisfies [0, 1] by construction -- this test
        # is nearly vacuous on its own (see the brief's own caveat). Assert
        # the extremes are actually attained so a broken/constant field
        # (e.g. every sample equal, or a degenerate min==max short-circuit
        # firing when it shouldn't) still fails something.
        self.assertAlmostEqual(min(flat), 0.0, places=6)
        self.assertAlmostEqual(max(flat), 1.0, places=6)

    def test_different_seeds_produce_different_fields(self):
        a = fast_noise_lite(8, 8, frequency=0.0234, seed=0)
        b = fast_noise_lite(8, 8, frequency=0.0234, seed=1)
        self.assertNotEqual(a, b)

    def test_different_frequencies_produce_different_fields(self):
        a = fast_noise_lite(8, 8, frequency=0.0234, seed=0)
        b = fast_noise_lite(8, 8, frequency=0.0084, seed=0)
        self.assertNotEqual(a, b)

    def test_matches_godot_at_production_scale(self):
        # 512x512 is Godot's default `NoiseTexture2D` size and what
        # `vfx_monster_death.tscn` actually bakes -- pin it directly rather
        # than relying on the smaller grids above as a proxy for it. See the
        # module docstring for why this scale is also where the fractal's
        # coordinate arithmetic would overflow a 32-bit int, if that still
        # mattered for this port's output (it doesn't, see `_wrap_i32`).
        noise_a = fast_noise_lite(512, 512, frequency=0.0234, seed=0)
        self.assertAlmostEqual(noise_a[511][511], EXPECTED_512_FREQ_0234_Y511_X511, places=4)
        noise_b = fast_noise_lite(512, 512, frequency=0.0084, seed=0)
        self.assertAlmostEqual(noise_b[511][511], EXPECTED_512_FREQ_0084_Y511_X511, places=4)

    def test_degenerate_constant_field_does_not_divide_by_zero(self):
        # frequency=0 collapses every sample to the same input coordinate
        # transform's fixed point... in practice FastNoiseLite still varies
        # per-pixel input before scaling, so this exercises the same
        # min==max guard `Noise::_get_image` has (noise.cpp:120-121)
        # without asserting a specific field, only that it doesn't raise.
        noise = fast_noise_lite(1, 1, frequency=0.0234, seed=0)
        self.assertEqual(noise, [[0.0]])


class TestBakeNoisePng(unittest.TestCase):
    def test_bakes_a_grayscale_png_of_the_requested_size(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = str(Path(tmp) / "noise.png")
            bake_noise_png(path, 8, 8, frequency=0.0234, seed=0)
            with Image.open(path) as img:
                self.assertEqual(img.mode, "L")
                self.assertEqual(img.size, (8, 8))

    def test_bakes_pixels_matching_the_truncating_quantization(self):
        # Godot's cast is `static_cast<uint8_t>(CLAMP(v * 255.f, 0, 255))`
        # -- TRUNCATING, not rounding. Pixel (6, 0) is chosen because its
        # normalized value * 255 lands just past a `.5` boundary
        # (3.500176...): truncation gives 3, the "obvious" rounding mistake
        # (`+ 0.5` before the cast) gives 4. A pin at a value whose fraction
        # is far from `.5` (e.g. (0, 0) at ~52.37) can't tell truncation and
        # rounding apart -- this one is a mutation-tested regression test
        # for exactly that bug (see task-9-report.md).
        with tempfile.TemporaryDirectory() as tmp:
            path = str(Path(tmp) / "noise.png")
            bake_noise_png(path, 8, 8, frequency=0.0234, seed=0)
            with Image.open(path) as img:
                pixel_00 = img.getpixel((0, 0))
                pixel_60 = img.getpixel((6, 0))
        self.assertEqual(pixel_00, int(EXPECTED_Y0_X0 * 255))
        self.assertEqual(pixel_60, 3)


if __name__ == "__main__":
    unittest.main()
