import unittest

from scripts.anim_track import bake_track, parse_animation

# A single unnamed `Animation` section (no `resource_name`), so it keys as
# "default" — mirrors the real `vfx_block_broken.tscn`'s implicit-RESET
# section, minus the parts (modulate tracks, a second named Animation, the
# surrounding scene) this test doesn't need.
SCENE_TEXT = (
    '[sub_resource type="Animation" id="Animation_test"]\n'
    "length = 0.4\n"
    'tracks/0/type = "bezier"\n'
    "tracks/0/imported = false\n"
    "tracks/0/enabled = true\n"
    'tracks/0/path = NodePath("BlockBreakRight:position:x")\n'
    "tracks/0/interp = 1\n"
    "tracks/0/loop_wrap = true\n"
    "tracks/0/keys = {\n"
    '"handle_modes": PackedInt32Array(0, 0),\n'
    '"points": PackedFloat32Array(1, -0.25, 0, 0.25, 0, 14, -0.25, 0, 0.25, 0),\n'
    '"times": PackedFloat32Array(0, 0.4)\n'
    "}\n"
    'tracks/1/type = "bezier"\n'
    "tracks/1/imported = false\n"
    "tracks/1/enabled = true\n"
    'tracks/1/path = NodePath("BlockBreakRight:rotation")\n'
    "tracks/1/interp = 1\n"
    "tracks/1/loop_wrap = true\n"
    "tracks/1/keys = {\n"
    '"handle_modes": PackedInt32Array(0, 0),\n'
    '"points": PackedFloat32Array(0, -0.25, 0, 0.25, 0, 0.42237, -0.25, 0, 0.25, 0),\n'  # noqa: E501
    '"times": PackedFloat32Array(0, 0.4)\n'
    "}\n"
)


class ParseAnimationTest(unittest.TestCase):
    def test_reads_a_bezier_track_path_and_keys(self):
        tracks = parse_animation(SCENE_TEXT)["default"]
        by_path = {t.path: t for t in tracks}
        self.assertIn("BlockBreakRight:position:x", by_path)
        self.assertEqual(by_path["BlockBreakRight:position:x"].kind, "bezier")

    def test_track_carries_both_keys_in_time_order(self):
        track = parse_animation(SCENE_TEXT)["default"][0]
        self.assertEqual([k.time for k in track.keys], [0.0, 0.4])
        self.assertEqual([k.value for k in track.keys], [1.0, 14.0])
        self.assertEqual(track.keys[0].out_x, 0.25)
        self.assertEqual(track.keys[1].in_x, -0.25)

    def test_bakes_the_track_through_godots_own_bezier_form(self):
        # A bezier track's value at t=0 is its first key's value -- baked, not
        # asserted from the sampler's own output.
        track = parse_animation(SCENE_TEXT)["default"][0]
        baked = bake_track(track, samples=256)
        self.assertEqual(len(baked), 256)
        self.assertAlmostEqual(baked[0], track.keys[0].value, places=5)

    def test_bake_ends_at_the_last_keys_value(self):
        track = parse_animation(SCENE_TEXT)["default"][0]
        baked = bake_track(track, samples=256)
        self.assertAlmostEqual(baked[-1], track.keys[-1].value, places=5)

    def test_bake_reproduces_an_independently_derived_interior_value(self):
        # The three tests above cannot fail if the binary search in
        # `_evaluate` is broken: they only pin baked[0] and baked[-1], which
        # `_evaluate`'s early-return clauses hand back regardless, plus a
        # bounds check a wrong-but-in-range value still satisfies. Demonstrated
        # by mutation: swapping the search's low/high branches leaves all of
        # them green while the value at t=0.2 moves 7.5 -> 13.99.
        #
        # So pin an INTERIOR sample against a number derived by hand, not by
        # running the code. This segment is symmetric: the first key is
        # (t=0, v=1) with a horizontal out-handle at dt=+0.25, the second is
        # (t=0.4, v=14) with a horizontal in-handle at dt=-0.25 -- equal and
        # opposite. Reflecting t -> 0.4 - t and v -> 15 - v maps the segment
        # onto itself, so its own midpoint t=0.2 must sit at the midpoint of
        # the values, (1 + 14) / 2 = 7.5, whatever path the curve takes to get
        # there.
        #
        # Note the handles CROSS in time (out at 0.25 > in at 0.15), which is
        # why `Curve.sample`'s d/3 assumption cannot express this track and
        # Godot's own solve-for-t is required.
        track = parse_animation(SCENE_TEXT)["default"][0]
        # samples=3 lands exactly on 0.0, 0.2, 0.4 (i * duration / (n - 1)).
        self.assertAlmostEqual(bake_track(track, samples=3)[1], 7.5, places=5)

    def test_bake_is_monotonic_between_two_ascending_keys_with_flat_handles(self):
        # Both handles are horizontal (dy=0), the classic "ease" shape --
        # value should never overshoot past 14 or dip back below 1.
        track = parse_animation(SCENE_TEXT)["default"][0]
        baked = bake_track(track, samples=256)
        self.assertTrue(all(1.0 - 1e-6 <= v <= 14.0 + 1e-6 for v in baked))


class BakeSpanTest(unittest.TestCase):
    """`span` exists because an effect's sprites share ONE clock.

    The renderer samples every channel at `elapsed / effect_lifetime`, so a
    track baked over its own shorter key range is replayed stretched across
    the whole effect. `vfx_block_broken`'s tracks all span 0 -> 0.4, which is
    why the default hid this; these use a track that does NOT.
    """

    #: The `position:x` track above, 1 -> 14 over 0 -> 0.4, re-timed to end
    #: at 0.2 — i.e. a track half as long as a 0.4s effect.
    SHORT = SCENE_TEXT.replace(
        '"times": PackedFloat32Array(0, 0.4)', '"times": PackedFloat32Array(0, 0.2)', 1
    )

    def _short_track(self):
        return parse_animation(self.SHORT)["default"][0]

    def test_a_short_track_baked_over_the_effect_span_holds_its_end(self):
        # Second half of the effect: the track is over, so Godot holds the
        # last key. Baked over the track's OWN span those samples would still
        # be climbing 1 -> 14.
        baked = bake_track(self._short_track(), samples=5, span=(0.0, 0.4))
        self.assertAlmostEqual(baked[-1], 14.0, places=5)
        self.assertAlmostEqual(baked[2], 14.0, places=5)  # t = 0.2, the last key

    def test_the_same_track_baked_over_its_own_span_is_still_climbing_when_it_should_be_done(self):
        # The bug this parameter fixes, pinned as a contrast rather than a
        # magic number. The LUT is replayed over the effect's whole lifetime
        # either way, so index 2 of 5 is the same wall-clock instant (t=0.2)
        # in both bakes — the moment this track's last key says it is
        # finished. Baked over its own span it has not even got there: the
        # 0.2s of motion has been stretched across the full 0.4s.
        own = bake_track(self._short_track(), samples=5)
        self.assertLess(own[2], 14.0 - 1e-6)
        self.assertAlmostEqual(own[-1], 14.0, places=5)

    def test_a_span_starting_before_the_first_key_holds_the_first_value(self):
        # A track whose keys start at t > 0 must sit still until then, not be
        # dragged back to start at 0.
        late = SCENE_TEXT.replace(
            '"times": PackedFloat32Array(0, 0.4)', '"times": PackedFloat32Array(0.2, 0.4)', 1
        )
        baked = bake_track(parse_animation(late)["default"][0], samples=5, span=(0.0, 0.4))
        self.assertAlmostEqual(baked[0], 1.0, places=5)
        self.assertAlmostEqual(baked[2], 1.0, places=5)  # t = 0.2, still the first key
        self.assertAlmostEqual(baked[-1], 14.0, places=5)
