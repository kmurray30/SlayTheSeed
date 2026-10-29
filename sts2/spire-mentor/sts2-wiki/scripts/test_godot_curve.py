import unittest

from scripts.godot_curve import bake_curve, parse_curve, parse_gradient

LINEAR_ISH = """
[sub_resource type="Curve" id="Curve_a"]
_data = [Vector2(0, 0), 0.0, 0.0, 0, 0, Vector2(1, 1), 0.0, 0.0, 0, 0]
point_count = 2
"""

# Split as concatenated literals so the over-length `_data` line can carry a
# real per-line `noqa` — a comment inside a triple-quoted string is inert to
# ruff's tokenizer, so a single `"""`-block cannot host one (see vfx_scene.py
# test fixtures for the same pattern).
DAMPING = (
    '[sub_resource type="Curve" id="Curve_b"]\n'
    "_data = [Vector2(0, 0), 0.0, 4.30388, 0, 0, Vector2(0.501292, 1), 0.0, 0.0, 0, 0, "
    "Vector2(1, 0), -5.93081, 0.0, 0, 0]\n"  # noqa: E501
    "point_count = 3\n"
)

LIMITED = """
[sub_resource type="Curve" id="Curve_c"]
_limits = [0.0, 5.0, 0.0, 1.0]
_data = [Vector2(0, 0.997601), 0.0, 0.0, 0, 0, Vector2(1, 3.25336), 6.03527, 0.0, 0, 0]
point_count = 2
"""

# Middle and right points are 5e-6 apart in x — inside Godot's CMP_EPSILON
# (1e-5) zero-width-segment guard but four orders of magnitude bigger than
# an under-tight tolerance would catch. Zero tangents so a wrongly-unguarded
# sample would fall on the zero-tangent smoothstep curve instead of the
# guarded flat value, making the two outcomes clearly distinguishable.
NEAR_ZERO_WIDTH_SEGMENT = (
    '[sub_resource type="Curve" id="Curve_e"]\n'
    "_data = [Vector2(0, 0), 0.0, 0.0, 0, 0, Vector2(0.5, 1.0), 0.0, 0.0, 0, 0, "
    "Vector2(0.500005, 5.0), 0.0, 0.0, 0, 0]\n"  # noqa: E501
    "point_count = 3\n"
)

# Scientific notation as Godot actually writes tiny tangents.
SCIENTIFIC_NOTATION = """
[sub_resource type="Curve" id="Curve_f"]
_data = [Vector2(0, 0), 0.0, 1.49012e-06, 0, 0, Vector2(1, 1), -2.5e-07, 0.0, 0, 0]
point_count = 2
"""


class CurveTest(unittest.TestCase):
    def test_endpoints_are_exact(self):
        c = parse_curve(LINEAR_ISH)
        self.assertAlmostEqual(c.sample(0.0), 0.0, places=6)
        self.assertAlmostEqual(c.sample(1.0), 1.0, places=6)

    def test_outside_the_domain_clamps_to_the_endpoints(self):
        c = parse_curve(LINEAR_ISH)
        self.assertAlmostEqual(c.sample(-0.5), 0.0, places=6)
        self.assertAlmostEqual(c.sample(1.5), 1.0, places=6)

    def test_zero_tangent_hermite_is_smoothstep_not_a_straight_line(self):
        # Cubic Hermite with both tangents 0 gives 3t^2 - 2t^3. At t=0.25 that
        # is 0.15625, NOT 0.25. If this reads 0.25 the evaluator is linear and
        # every damping/scale curve in the game is subtly wrong.
        c = parse_curve(LINEAR_ISH)
        self.assertAlmostEqual(c.sample(0.25), 0.15625, places=6)

    def test_the_peak_of_a_three_point_curve_is_at_its_middle_point(self):
        c = parse_curve(DAMPING)
        self.assertAlmostEqual(c.sample(0.501292), 1.0, places=6)
        self.assertLess(c.sample(0.0), c.sample(0.501292))
        self.assertLess(c.sample(1.0), c.sample(0.501292))

    def test_limits_do_not_rescale_the_sampled_value(self):
        # `_limits` is EDITOR range metadata. The stored point values are
        # already in real units (this curve genuinely reaches 3.25), so
        # sampling must not normalise them into 0..1.
        c = parse_curve(LIMITED)
        self.assertAlmostEqual(c.sample(1.0), 3.25336, places=5)

    def test_bake_is_endpoint_inclusive(self):
        c = parse_curve(LINEAR_ISH)
        lut = bake_curve(c, n=256)
        self.assertEqual(len(lut), 256)
        self.assertAlmostEqual(lut[0], 0.0, places=6)
        self.assertAlmostEqual(lut[-1], 1.0, places=6)

    def test_a_near_zero_width_segment_samples_the_right_endpoint(self):
        # Godot's guard compares against CMP_EPSILON (1e-5), not an
        # arbitrarily tight tolerance. A segment 5e-6 wide must still trip
        # it and return the right endpoint's y flat, not interpolate.
        c = parse_curve(NEAR_ZERO_WIDTH_SEGMENT)
        self.assertAlmostEqual(c.sample(0.500002), 5.0, places=6)

    def test_scientific_notation_tangents_parse(self):
        c = parse_curve(SCIENTIFIC_NOTATION)
        self.assertAlmostEqual(c.points[0].right_tangent, 1.49012e-06, delta=1e-12)
        self.assertAlmostEqual(c.points[1].left_tangent, -2.5e-07, delta=1e-12)


GRADIENT_HDR = """
[sub_resource type="Gradient" id="Gradient_a"]
interpolation_mode = 1
offsets = PackedFloat32Array(0, 0.6347826)
colors = PackedColorArray(1, 1, 1, 1, 2.454227, 2.454227, 2.454227, 1)
"""


class GradientTest(unittest.TestCase):
    def test_hdr_values_survive(self):
        g = parse_gradient(GRADIENT_HDR)
        r, _, _, a = g.sample(1.0)
        self.assertAlmostEqual(r, 2.454227, places=5)
        self.assertAlmostEqual(a, 1.0, places=6)

    def test_interpolation_mode_1_is_constant_not_linear(self):
        # Godot's Gradient.INTERPOLATE_CONSTANT == 1: the value holds at the
        # previous stop rather than blending. Reading this as linear would
        # smear every ramp that uses it.
        g = parse_gradient(GRADIENT_HDR)
        r, _, _, _ = g.sample(0.5)
        self.assertAlmostEqual(r, 1.0, places=6)

    def test_a_single_offset_gradient_is_constant_everywhere(self):
        g = parse_gradient(
            '[sub_resource type="Gradient" id="g"]\n'
            "offsets = PackedFloat32Array(1)\n"
            "colors = PackedColorArray(1, 1, 1, 1)\n"
        )
        self.assertEqual(g.sample(0.0), g.sample(1.0))

    def test_offsets_authored_out_of_order_still_sample_correctly(self):
        # Godot's Gradient::set_offsets always marks the point list unsorted
        # and re-sorts before every sample (gradient.h:68-73, 156) — file
        # order is not guaranteed to be offset order.
        g = parse_gradient(
            '[sub_resource type="Gradient" id="g"]\n'
            "offsets = PackedFloat32Array(0.8, 0.2)\n"
            "colors = PackedColorArray(1, 0, 0, 1, 0, 1, 0, 1)\n"
        )
        r, gr, b, a = g.sample(0.5)
        self.assertAlmostEqual(r, 0.5, places=6)
        self.assertAlmostEqual(gr, 0.5, places=6)
        self.assertAlmostEqual(b, 0.0, places=6)
        self.assertAlmostEqual(a, 1.0, places=6)


if __name__ == "__main__":
    unittest.main()
