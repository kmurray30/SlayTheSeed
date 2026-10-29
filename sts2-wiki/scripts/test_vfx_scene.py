import unittest

from scripts.vfx_scene import FlipbookSpec, SpineVfxSpec, parse_vfx_scene

# Fixture lines are verbatim `.tscn` text, so the ones over the 100-char
# limit are wrapped as concatenated literals (each still holding the exact
# original characters) with a real per-line lint suppression rather than a
# blanket file-level ignore in pyproject.toml — see task-1-findings-r1.md
# finding 1. A suppression comment written *inside* a triple-quoted string
# is inert (ruff's tokenizer sees no comment there), so plain triple-quoted
# blocks cannot host it; splitting into adjacent string literals keeps the
# comment a real token outside the string.
SLASH = (
    '[gd_scene load_steps=6 format=3 uid="uid://dlo4ynkvta3jd"]\n'
    "\n"
    '[ext_resource type="Texture2D" uid="uid://a" path="res://images/vfx/vfx_attack_slash/vfx_attack_slash_01.png" id="1_h40d6"]\n'  # noqa: E501
    '[ext_resource type="Script" uid="uid://b" path="res://src/Core/Nodes/Vfx/Utilities/NSpriteAnimator.cs" id="1_h71hg"]\n'  # noqa: E501
    '[ext_resource type="Texture2D" uid="uid://c" path="res://images/vfx/vfx_attack_slash/vfx_attack_slash_00.png" id="2_87cgl"]\n'  # noqa: E501
    '[ext_resource type="Texture2D" uid="uid://d" path="res://images/vfx/vfx_attack_slash/vfx_attack_slash_02.png" id="3_ms47n"]\n'  # noqa: E501
    '[ext_resource type="Texture2D" uid="uid://e" path="res://images/vfx/vfx_attack_slash/vfx_attack_slash_03.png" id="4_wapxv"]\n'  # noqa: E501
    "\n"
    '[node name="VfxAttackSlash" type="Sprite2D"]\n'
    "scale = Vector2(0.75, 0.75)\n"
    'script = ExtResource("1_h71hg")\n'
    '_frames = Array[Texture2D]([ExtResource("2_87cgl"), ExtResource("1_h40d6"), ExtResource("3_ms47n"), ExtResource("4_wapxv")])\n'  # noqa: E501
    "_randomizeRotation = true\n"
    "_rotationRange = Vector2(0, 25)\n"
)

BLOCK = (
    '[gd_scene load_steps=8 format=3 uid="uid://dteh8dpwhj0md"]\n'
    "\n"
    '[ext_resource type="Script" uid="uid://z" path="res://src/Core/Nodes/Vfx/Utilities/NSpriteAnimator.cs" id="1_wx551"]\n'  # noqa: E501
    '[ext_resource type="Texture2D" uid="uid://f" path="res://images/vfx/vfx_block/vfx_block_00.png" id="2_487cg"]\n'  # noqa: E501
    '[ext_resource type="Texture2D" uid="uid://g" path="res://images/vfx/vfx_block/vfx_block_01.png" id="3_r3jd8"]\n'  # noqa: E501
    "\n"
    '[node name="VfxBlock" type="Sprite2D"]\n'
    'script = ExtResource("1_wx551")\n'
    '_frames = Array[Texture2D]([ExtResource("2_487cg"), ExtResource("3_r3jd8")])\n'
)

PARTICLES = (
    '[gd_scene load_steps=9 format=3 uid="uid://q"]\n'
    "\n"
    '[ext_resource type="Script" uid="uid://r" path="res://src/Core/Nodes/Vfx/Utilities/NVfxParticleSystem.cs" id="1_yv241"]\n'  # noqa: E501
    "\n"
    '[sub_resource type="ParticleProcessMaterial" id="ParticleProcessMaterial_qdfs7"]\n'
    "turbulence_noise_scale = 10.1\n"
    "\n"
    '[node name="VfxAttackBlunt" type="Node2D"]\n'
    'script = ExtResource("1_yv241")\n'
)

BITE = (
    '[gd_scene load_steps=3 format=3 uid="uid://s"]\n'
    "\n"
    '[ext_resource type="SkeletonDataResource" uid="uid://t" path="res://animations/vfx/vfx_bite/vfx_bite_skeleton_data_resource.tres" id="1_bite"]\n'  # noqa: E501
    "\n"
    '[node name="VfxBite" type="SpineSprite"]\n'
    "scale = Vector2(1.2, 1.2)\n"
    'skeleton_data_res = ExtResource("1_bite")\n'
)

# The real shape every actual Spine VFX scene uses (task-2-findings-r1.md):
# a root `Node2D` carrying the script and `_animation`, plus a CHILD
# `SpineSprite` node (`parent="."`) carrying the scale and skeleton
# reference. Also exercises the `SpineSkeletonDataResource` type string the
# real pck uses (vs. `BITE`'s plain `SkeletonDataResource`, which some other
# real Spine ext_resource may or may not use — both must work).
SPINE_REAL_SHAPE = (
    '[gd_scene load_steps=4 format=3 uid="uid://chain"]\n'
    "\n"
    '[ext_resource type="Script" uid="uid://chainscript" path="res://src/Core/Nodes/Vfx/VfxChain.cs" id="1_script"]\n'  # noqa: E501
    '[ext_resource type="SpineSkeletonDataResource" uid="uid://chaindata" path="res://animations/vfx/vfx_chain/vfx_chain_skeleton_data_resource.tres" id="2_x"]\n'  # noqa: E501
    "\n"
    '[node name="VfxChain" type="Node2D"]\n'
    'script = ExtResource("1_script")\n'
    '_animation = "chain"\n'
    "\n"
    '[node name="SpineSprite" type="SpineSprite" parent="."]\n'
    "scale = Vector2(0.75, 0.75)\n"
    'skeleton_data_res = ExtResource("2_x")\n'
)

# `vfx_scratch`'s real shape: scale is authored on BOTH the root Node2D
# (0.7) and the SpineSprite child (0.75). Godot composes parent/child
# transforms, so the true rendered scale is the PRODUCT, 0.7 * 0.75 = 0.525
# — taking either node alone is a 33% or 43% size error (task-2-findings-r1.md
# finding 2).
SPINE_COMPOUND_SCALE = (
    '[gd_scene load_steps=4 format=3 uid="uid://scratch"]\n'
    "\n"
    '[ext_resource type="Script" uid="uid://scratchscript" path="res://src/Core/Nodes/Vfx/VfxScratch.cs" id="1_script"]\n'  # noqa: E501
    '[ext_resource type="SpineSkeletonDataResource" uid="uid://scratchdata" path="res://animations/vfx/vfx_scratch/vfx_scratch_skel_data.tres" id="2_x"]\n'  # noqa: E501
    "\n"
    '[node name="VfxScratch" type="Node2D"]\n'
    'script = ExtResource("1_script")\n'
    "scale = Vector2(0.7, 0.7)\n"
    '_animation = "scratch"\n'
    "\n"
    '[node name="SpineSprite" type="SpineSprite" parent="."]\n'
    "scale = Vector2(0.75, 0.75)\n"
    'skeleton_data_res = ExtResource("2_x")\n'
)

# Neither root nor child authors a scale (`vfx_flying_slash`/`vfx_adrenaline`'s
# real shape) — both default to 1.0, product 1.0.
SPINE_NO_AUTHORED_SCALE = (
    '[gd_scene load_steps=4 format=3 uid="uid://flyingslash"]\n'
    "\n"
    '[ext_resource type="Script" uid="uid://fsscript" path="res://src/Core/Nodes/Vfx/VfxFlyingSlash.cs" id="1_script"]\n'  # noqa: E501
    '[ext_resource type="SpineSkeletonDataResource" uid="uid://fsdata" path="res://animations/vfx/vfx_flying_slash/vfx_flying_slash_skeleton_data_resource.tres" id="2_x"]\n'  # noqa: E501
    "\n"
    '[node name="VfxFlyingSlash" type="Node2D"]\n'
    'script = ExtResource("1_script")\n'
    '_animation = "flying_slash"\n'
    "\n"
    '[node name="SpineSprite" type="SpineSprite" parent="."]\n'
    'skeleton_data_res = ExtResource("2_x")\n'
)

# `MegaSkeletonDataResource` (`MegaCrit.Sts2.Core.Bindings.MegaSpine`) is a
# real, distinct decomp type used by monster rigs, not VFX scenes. It also
# ends in "SkeletonDataResource" — a suffix match would wrongly accept it as
# a Spine VFX. Regression for task-2-findings-r2.md finding 2.
MEGA_SKELETON_TYPE = (
    '[gd_scene load_steps=3 format=3 uid="uid://mega"]\n'
    "\n"
    '[ext_resource type="MegaSkeletonDataResource" uid="uid://megadata" path="res://monsters/some_monster.tres" id="1_x"]\n'  # noqa: E501
    "\n"
    '[node name="SomeMonster" type="Node2D"]\n'
    'skeleton_data_res = ExtResource("1_x")\n'
)

# `ext_resource` attributes in orders the real pck does not restrict to a
# single order: `id` before `uid`, `id` before `type`, `path` before `type`
# before `id`. Regression for finding 2 (task-1-findings-r1.md).
SHUFFLED_ATTR_ORDER = (
    '[gd_scene load_steps=4 format=3 uid="uid://shuf"]\n'
    "\n"
    '[ext_resource type="Script" path="res://src/Core/Nodes/Vfx/Utilities/NSpriteAnimator.cs" id="1_script" uid="uid://shufscript"]\n'  # noqa: E501
    '[ext_resource id="2_frame0" type="Texture2D" path="res://images/vfx/vfx_shuffled/vfx_shuffled_00.png"]\n'  # noqa: E501
    '[ext_resource path="res://images/vfx/vfx_shuffled/vfx_shuffled_01.png" type="Texture2D" id="3_frame1"]\n'  # noqa: E501
    "\n"
    '[node name="VfxShuffled" type="Sprite2D"]\n'
    'script = ExtResource("1_script")\n'
    '_frames = Array[Texture2D]([ExtResource("2_frame0"), ExtResource("3_frame1")])\n'
)

# `_frames` names an id no `ext_resource` line declares. Regression for
# finding 2's second half: this must yield `None`, never `KeyError`.
UNKNOWN_FRAME_ID = (
    '[gd_scene load_steps=3 format=3 uid="uid://unk"]\n'
    "\n"
    '[ext_resource type="Texture2D" path="res://images/vfx/vfx_unknown/vfx_unknown_00.png" id="1_frame0"]\n'  # noqa: E501
    "\n"
    '[node name="VfxUnknown" type="Sprite2D"]\n'
    '_frames = Array[Texture2D]([ExtResource("1_frame0"), ExtResource("9_missing")])\n'
)

# The root node omits `scale`/`_fps`; a second, child `[node ... parent="."]`
# carries both. Regression for finding 3: field regexes must not pick up a
# non-root node's values. Expected: `scale` stays the 1.0 default and `_fps`
# stays the 15.0 default, both scoped away from the child's Vector2(3.0, 3.0)
# / 30.0.
MULTI_NODE_SCENE = (
    '[gd_scene load_steps=3 format=3 uid="uid://multi"]\n'
    "\n"
    '[ext_resource type="Texture2D" path="res://images/vfx/vfx_multi/vfx_multi_00.png" id="1_frame0"]\n'  # noqa: E501
    "\n"
    '[node name="VfxMulti" type="Sprite2D"]\n'
    '_frames = Array[Texture2D]([ExtResource("1_frame0")])\n'
    "\n"
    '[node name="Child" type="Node2D" parent="."]\n'
    "scale = Vector2(3.0, 3.0)\n"
    "_fps = 30.0\n"
)


class ParseFlipbookTest(unittest.TestCase):
    def test_frames_follow_the_frames_array_not_declaration_order(self) -> None:
        spec = parse_vfx_scene(SLASH)
        assert isinstance(spec, FlipbookSpec)
        self.assertEqual(
            spec.frames,
            [
                "images/vfx/vfx_attack_slash/vfx_attack_slash_00.png",
                "images/vfx/vfx_attack_slash/vfx_attack_slash_01.png",
                "images/vfx/vfx_attack_slash/vfx_attack_slash_02.png",
                "images/vfx/vfx_attack_slash/vfx_attack_slash_03.png",
            ],
        )

    def test_authored_scale_and_rotation_are_carried(self) -> None:
        spec = parse_vfx_scene(SLASH)
        assert isinstance(spec, FlipbookSpec)
        self.assertEqual(spec.scale, 0.75)
        self.assertTrue(spec.randomize_rotation)
        self.assertEqual(spec.rotation_range, (0.0, 25.0))

    def test_omitted_fields_take_the_nspriteanimator_defaults(self) -> None:
        spec = parse_vfx_scene(BLOCK)
        assert isinstance(spec, FlipbookSpec)
        self.assertEqual(spec.fps, 15.0)
        self.assertFalse(spec.loop)
        self.assertFalse(spec.randomize_rotation)
        self.assertEqual(spec.scale, 1.0)
        self.assertEqual(len(spec.frames), 2)

    def test_a_particle_composite_is_not_parsed(self) -> None:
        self.assertIsNone(parse_vfx_scene(PARTICLES))

    def test_a_spine_scene_reports_its_scale(self) -> None:
        spec = parse_vfx_scene(BITE)
        assert isinstance(spec, SpineVfxSpec)
        self.assertEqual(spec.scale, 1.2)

    def test_ext_resource_attributes_parse_in_any_order(self) -> None:
        spec = parse_vfx_scene(SHUFFLED_ATTR_ORDER)
        assert isinstance(spec, FlipbookSpec)
        self.assertEqual(
            spec.frames,
            [
                "images/vfx/vfx_shuffled/vfx_shuffled_00.png",
                "images/vfx/vfx_shuffled/vfx_shuffled_01.png",
            ],
        )

    def test_an_unknown_frame_id_yields_none_not_keyerror(self) -> None:
        self.assertIsNone(parse_vfx_scene(UNKNOWN_FRAME_ID))

    def test_field_regexes_are_scoped_to_the_root_node(self) -> None:
        spec = parse_vfx_scene(MULTI_NODE_SCENE)
        assert isinstance(spec, FlipbookSpec)
        self.assertEqual(spec.scale, 1.0)
        self.assertEqual(spec.fps, 15.0)

    # -- Regression tests for task-2-findings-r1.md --

    def test_spine_skeleton_data_resource_type_string_is_recognized(self) -> None:
        # The real pck declares `type="SpineSkeletonDataResource"`, not the
        # plain `"SkeletonDataResource"` `BITE` uses — both must parse.
        spec = parse_vfx_scene(SPINE_REAL_SHAPE)
        self.assertIsInstance(spec, SpineVfxSpec)

    def test_spine_scale_is_read_from_the_spinesprite_child_not_the_root(self) -> None:
        # `SPINE_REAL_SHAPE`'s root carries no `scale` at all; the authored
        # 0.75 lives only on the child `SpineSprite` node.
        spec = parse_vfx_scene(SPINE_REAL_SHAPE)
        assert isinstance(spec, SpineVfxSpec)
        self.assertEqual(spec.scale, 0.75)

    def test_spine_scale_on_root_and_child_composes_as_a_product(self) -> None:
        # `vfx_scratch`'s real shape: root scale 0.7 * child scale 0.75.
        spec = parse_vfx_scene(SPINE_COMPOUND_SCALE)
        assert isinstance(spec, SpineVfxSpec)
        self.assertAlmostEqual(spec.scale, 0.525)

    def test_spine_scale_defaults_to_one_when_neither_node_authors_it(self) -> None:
        spec = parse_vfx_scene(SPINE_NO_AUTHORED_SCALE)
        assert isinstance(spec, SpineVfxSpec)
        self.assertEqual(spec.scale, 1.0)

    def test_spine_scene_captures_its_animation_clip(self) -> None:
        spec = parse_vfx_scene(SPINE_COMPOUND_SCALE)
        assert isinstance(spec, SpineVfxSpec)
        self.assertEqual(spec.clip, "scratch")

    def test_spine_scene_without_an_animation_field_has_no_clip(self) -> None:
        spec = parse_vfx_scene(BITE)
        assert isinstance(spec, SpineVfxSpec)
        self.assertIsNone(spec.clip)

    def test_a_mega_skeleton_data_resource_type_is_not_a_spine_vfx(self) -> None:
        # A suffix match on "SkeletonDataResource" would wrongly accept the
        # monster-rig type `MegaSkeletonDataResource`; the match must be an
        # explicit set of the two known Spine-VFX type strings.
        self.assertIsNone(parse_vfx_scene(MEGA_SKELETON_TYPE))


if __name__ == "__main__":
    unittest.main()
