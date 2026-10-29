import inspect
import math
import re
import unittest

from scripts import particle_scene
from scripts.particle_scene import (
    _CPU_PARAMS_NOT_MODELLED,
    _CPU_PROPS_NOT_PARAMETERS,
    parse_emitters,
    parse_particle_scene,
)


def _authored_property_names(section: str) -> set[str]:
    """Top-level `name = ...` assignments in a `.tscn` section body."""
    return {
        line.split("=", 1)[0].strip()
        for line in section.splitlines()
        if "=" in line and not line[:1].isspace()
    }


# `vfx_attack_blunt.tscn`'s real structure, trimmed to the fields under test
# (task-3-brief.md Step 1): a composite with one instanced child scene that
# carries overrides. Split as concatenated literals so an over-length line
# can carry a real per-line `noqa` — a comment inside a triple-quoted string
# is inert to ruff's tokenizer (see `test_vfx_scene.py`/`test_godot_curve.py`
# for the same pattern).
BLUNT = (
    '[gd_scene load_steps=28 format=3 uid="uid://bawvrm1vpk8m8"]\n'
    "\n"
    '[ext_resource type="Script" path="res://src/Core/Nodes/Vfx/Utilities/NVfxParticleSystem.cs" id="1_yv241"]\n'  # noqa: E501
    '[ext_resource type="PackedScene" path="res://scenes/vfx/common/vfx_common_specks.tscn" id="3_n4tiw"]\n'  # noqa: E501
    '[ext_resource type="Texture2D" path="res://images/vfx/common/common_speck.png" id="4_pv0na"]\n'  # noqa: E501
    "\n"
    '[sub_resource type="ParticleProcessMaterial" id="ParticleProcessMaterial_qdfs7"]\n'
    "initial_velocity_min = 500.0\n"
    "initial_velocity_max = 2000.0\n"
    "spread = 180.0\n"
    "\n"
    '[node name="VfxAttackBlunt" type="Node2D"]\n'
    'script = ExtResource("1_yv241")\n'
    "_lifetime = 1.25\n"
    "\n"
    '[node name="vfx_common_specks" parent="." instance=ExtResource("3_n4tiw")]\n'
    "self_modulate = Color(1, 0.19999999, 0.19999999, 1)\n"
    "z_index = 1\n"
    "amount = 6\n"
    'texture = ExtResource("4_pv0na")\n'
    "lifetime = 1.0\n"
    'process_material = SubResource("ParticleProcessMaterial_qdfs7")\n'
)

SPECKS = (
    '[gd_scene load_steps=16 format=3 uid="uid://chqoe33c4gm6a"]\n'
    "\n"
    '[ext_resource type="Material" path="res://materials/vfx/common/vfx_speck_glow_white.tres" id="1_53e6a"]\n'  # noqa: E501
    '[ext_resource type="Texture2D" path="res://images/vfx/common/common_glow_speck.png" id="2_38o4g"]\n'  # noqa: E501
    "\n"
    '[sub_resource type="ParticleProcessMaterial" id="ParticleProcessMaterial_536ct"]\n'
    "initial_velocity_min = 100.0\n"
    "initial_velocity_max = 2500.0\n"
    "spread = 180.0\n"
    "damping_min = 0.26\n"
    "damping_max = 0.26\n"
    "\n"
    '[node name="vfx_common_specks" type="GPUParticles2D"]\n'
    'material = ExtResource("1_53e6a")\n'
    "rotation = 1.57079\n"
    "emitting = false\n"
    "amount = 10\n"
    'texture = ExtResource("2_38o4g")\n'
    "lifetime = 1.5\n"
    "one_shot = true\n"
    "explosiveness = 1.0\n"
    "randomness = 0.5\n"
    "fixed_fps = 60\n"
    'process_material = SubResource("ParticleProcessMaterial_536ct")\n'
)

LOADER = {"scenes/vfx/common/vfx_common_specks.tscn": SPECKS}

# An authored `visible = false` prunes a node AND its descendants. Synthetic
# rather than the real `block_spark_vfx.tscn`, so the rule is pinned on its own
# terms: `dead_child` is explicitly visible and must STILL be dropped for
# sitting under an invisible parent -- Godot hides a CanvasItem's whole subtree
# regardless of a descendant's own `visible`.
VISIBILITY = (
    "[gd_scene load_steps=2 format=3]\n"
    "\n"
    '[ext_resource type="Script" path="res://src/Core/Nodes/Vfx/NPoisonImpactVfx.cs" id="1_a"]\n'
    "\n"
    '[node name="Root" type="Node2D"]\n'
    'script = ExtResource("1_a")\n'
    "_lifetime = 1.0\n"
    "\n"
    '[node name="live" type="GPUParticles2D" parent="."]\n'
    "amount = 3\n"
    "\n"
    '[node name="dead" type="GPUParticles2D" parent="."]\n'
    "visible = false\n"
    "amount = 5\n"
    "\n"
    '[node name="dead_group" type="Node2D" parent="."]\n'
    "visible = false\n"
    "\n"
    '[node name="dead_child" type="GPUParticles2D" parent="dead_group"]\n'
    "visible = true\n"
    "amount = 7\n"
)


# `NScratchVfx.cs` (`vfx_scratch_impact`'s real script): staggered restart,
# not "everything at once". Trimmed to the shape that matters —
# `node_paths`-exported `_anticipationParticles`/`_impactParticles` arrays on
# the root, no `_lifetime` (the script frees itself at a script-derived time,
# not a scene-authored one), and `shared_leaf` instanced TWICE under
# different names in the SAME delayed group — the real scene's
# `vfx_big_slash_impact_side_smoke_{left,right}` shape, where both instances
# share one child scene's internal root name.
_ANTICIPATION_LEAF = '[gd_scene load_steps=1 format=3]\n\n[node name="flipbook_leaf" type="GPUParticles2D"]\namount = 1\n'  # noqa: E501
_SHARED_LEAF = '[gd_scene load_steps=1 format=3]\n\n[node name="shared_leaf" type="GPUParticles2D"]\namount = 2\n'  # noqa: E501
SCRATCH = (
    "[gd_scene load_steps=4 format=3]\n"
    "\n"
    '[ext_resource type="Script" path="res://src/Core/Nodes/Vfx/NScratchVfx.cs" id="1_a"]\n'
    '[ext_resource type="PackedScene" path="res://scenes/vfx/scratch/flipbook_leaf.tscn" id="2_a"]\n'  # noqa: E501
    '[ext_resource type="PackedScene" path="res://scenes/vfx/common/shared_leaf.tscn" id="3_a"]\n'
    "\n"
    '[node name="VfxScratchImpact" type="Node2D" node_paths=PackedStringArray("_anticipationParticles", "_impactParticles")]\n'  # noqa: E501
    'script = ExtResource("1_a")\n'
    '_anticipationParticles = [NodePath("flipbook_leaf")]\n'
    '_impactParticles = [NodePath("shared_leaf_left"), NodePath("shared_leaf_right")]\n'
    "\n"
    '[node name="flipbook_leaf" parent="." instance=ExtResource("2_a")]\n'
    "\n"
    '[node name="shared_leaf_left" parent="." instance=ExtResource("3_a")]\n'
    "\n"
    '[node name="shared_leaf_right" parent="." instance=ExtResource("3_a")]\n'
)
SCRATCH_LOADER = {
    "scenes/vfx/scratch/flipbook_leaf.tscn": _ANTICIPATION_LEAF,
    "scenes/vfx/common/shared_leaf.tscn": _SHARED_LEAF,
}

# A bespoke script (`NPoisonImpactVfx.cs`) that does NOT take a tint — both of
# its `Create` overloads (NPoisonImpactVfx.cs:76,91) omit `Color`. Regression
# fixture for the fix-round-1 defect: `tinted` must not default to "any
# non-generic script is tinted".
POISON = (
    "[gd_scene load_steps=2 format=3]\n"
    "\n"
    '[ext_resource type="Script" path="res://src/Core/Nodes/Vfx/NPoisonImpactVfx.cs" id="1_a"]\n'
    "\n"
    '[node name="VfxPoisonImpact" type="Node2D"]\n'
    'script = ExtResource("1_a")\n'
    "_lifetime = 2.0\n"
    "\n"
    '[node name="particles" type="GPUParticles2D" parent="."]\n'
    "amount = 5\n"
)

# A composite with an intermediate `Node2D` container carrying a non-identity
# position/scale/rotation, between the root and the `GPUParticles2D` leaf.
# Values are chosen so a wrong composition order (e.g. summing offsets
# without rotating/scaling them, or applying the parent's scale to the
# wrong operand) gives a visibly different answer rather than one that
# happens to agree by commuting: expected offset is (100, 70), not
# (110, 50) [rotation skipped] or (100, 60) [parent scale not applied to
# the child's local position].
_ROTATION = math.pi / 2
CONTAINER = (
    "[gd_scene load_steps=2 format=3]\n"
    "\n"
    '[ext_resource type="Script" path="res://src/Core/Nodes/Vfx/Utilities/NVfxParticleSystem.cs" id="1_a"]\n'  # noqa: E501
    "\n"
    '[node name="VfxContainerTest" type="Node2D"]\n'
    'script = ExtResource("1_a")\n'
    "_lifetime = 1.0\n"
    "\n"
    '[node name="Container" type="Node2D" parent="."]\n'
    "position = Vector2(100, 50)\n"
    "scale = Vector2(2, 2)\n"
    f"rotation = {_ROTATION!r}\n"
    "\n"
    '[node name="particles" type="GPUParticles2D" parent="Container"]\n'
    "position = Vector2(10, 0)\n"
    "amount = 3\n"
)

# A 3-level chain — root -> A (non-uniform scale, no rotation) -> B
# (rotation, uniform scale) -> leaf — the shape fix-round-2 exists for.
# Godot composes global transforms by real 2x2-basis matrix multiplication
# (`Transform2D::operator*=`), which is NOT the same as summing rotation and
# multiplying scale component-wise once a NON-uniform scale sits above a
# rotation: `S(s)*R(th) != R(th)*S(s)` for non-uniform `s`.
#
# Hand-derived via Transform2D composition (`then_local`'s own algorithm,
# transcribed from `core/math/transform_2d.cpp:107-113,198-212`):
#   A local basis (rotation=0, scale=(2, 1)):        a=2, b=0, c=0,  d=1
#   A global == A local (root is identity)
#   B local basis (rotation=90 deg, scale=(1, 1)):    a=0, b=1, c=-1, d=0
#   B local offset = (10, 0)
#   B global = A_global.then_local(B_local):
#     offset = (A.a*10 + A.c*0 + A.ox, A.b*10 + A.d*0 + A.oy) = (20, 0)
#     basis: a=A.a*B.a+A.c*B.b=0, b=A.b*B.a+A.d*B.b=1, c=A.a*B.c+A.c*B.d=-2, d=A.b*B.c+A.d*B.d=0
#   leaf local offset = (0, 5), identity basis
#   leaf global offset = (B.a*0 + B.c*5 + B.ox, B.b*0 + B.d*5 + B.oy)
#                       = (-2*5 + 20, 0 + 0) = (10, 0)
#
# The OLD decomposed-accumulator code (rotation summed, scale multiplied
# component-wise, applied in a fixed scale-then-rotate order every step)
# gives (15, 0) for these same values — see task-3-report.md fix round 2.
# (10, 0) is the only value a real Transform2D composition can produce.
_NON_UNIFORM_ROTATION = math.pi / 2
NON_UNIFORM_CHAIN = (
    "[gd_scene load_steps=2 format=3]\n"
    "\n"
    '[ext_resource type="Script" path="res://src/Core/Nodes/Vfx/Utilities/NVfxParticleSystem.cs" id="1_a"]\n'  # noqa: E501
    "\n"
    '[node name="VfxNonUniformChainTest" type="Node2D"]\n'
    'script = ExtResource("1_a")\n'
    "_lifetime = 1.0\n"
    "\n"
    '[node name="A" type="Node2D" parent="."]\n'
    "scale = Vector2(2, 1)\n"
    "\n"
    '[node name="B" type="Node2D" parent="A"]\n'
    "position = Vector2(10, 0)\n"
    f"rotation = {_NON_UNIFORM_ROTATION!r}\n"
    "\n"
    '[node name="particles" type="GPUParticles2D" parent="A/B"]\n'
    "position = Vector2(0, 5)\n"
    "amount = 3\n"
)

# A `ParticleProcessMaterial` whose `damping_curve`/`color_ramp` are wrapped
# in a `CurveTexture`/`GradientTexture1D`, the way Godot actually authors
# them — the process-material property is never a bare `Curve`/`Gradient`.
CURVE_GRADIENT = (
    "[gd_scene load_steps=7 format=3]\n"
    "\n"
    '[ext_resource type="Script" path="res://src/Core/Nodes/Vfx/Utilities/NVfxParticleSystem.cs" id="1_a"]\n'  # noqa: E501
    "\n"
    '[sub_resource type="Curve" id="Curve_a"]\n'
    "_data = [Vector2(0, 0), 0.0, 0.0, 0, 0, Vector2(1, 1), 0.0, 0.0, 0, 0]\n"
    "point_count = 2\n"
    "\n"
    '[sub_resource type="CurveTexture" id="CurveTexture_a"]\n'
    'curve = SubResource("Curve_a")\n'
    "\n"
    '[sub_resource type="Gradient" id="Gradient_a"]\n'
    "offsets = PackedFloat32Array(0, 1)\n"
    "colors = PackedColorArray(0, 0, 0, 1, 1, 1, 1, 1)\n"
    "\n"
    '[sub_resource type="GradientTexture1D" id="GradientTexture1D_a"]\n'
    'gradient = SubResource("Gradient_a")\n'
    "\n"
    '[sub_resource type="ParticleProcessMaterial" id="PPM_a"]\n'
    'damping_curve = SubResource("CurveTexture_a")\n'
    'color_ramp = SubResource("GradientTexture1D_a")\n'
    "\n"
    '[node name="VfxCurveTest" type="Node2D"]\n'
    'script = ExtResource("1_a")\n'
    "_lifetime = 1.0\n"
    "\n"
    '[node name="particles" type="GPUParticles2D" parent="."]\n'
    "amount = 3\n"
    'process_material = SubResource("PPM_a")\n'
)


class ParticleSceneTest(unittest.TestCase):
    def parse(self):
        return parse_particle_scene(BLUNT, LOADER.get)

    def test_composite_lifetime_comes_from_the_script_field(self):
        self.assertAlmostEqual(self.parse().lifetime, 1.25)

    def test_child_overrides_replace_the_base_scene_value(self):
        e = self.parse().emitters[0]
        self.assertEqual(e.amount, 6)  # base scene says 10
        self.assertAlmostEqual(e.lifetime, 1.0)  # base scene says 1.5
        self.assertEqual(e.texture, "images/vfx/common/common_speck.png")
        self.assertAlmostEqual(e.self_modulate[1], 0.19999999, places=6)
        self.assertEqual(e.z_index, 1)

    def test_unoverridden_values_are_inherited_from_the_base_scene(self):
        e = self.parse().emitters[0]
        self.assertAlmostEqual(e.explosiveness, 1.0)
        self.assertAlmostEqual(e.randomness, 0.5)
        self.assertEqual(e.fixed_fps, 60)
        self.assertAlmostEqual(e.rotation, 1.57079, places=5)
        self.assertEqual(e.material, "materials/vfx/common/vfx_speck_glow_white.tres")

    def test_an_overridden_process_material_replaces_the_base_one_entirely(self):
        # NOT a field-wise merge: the override declares its own
        # ParticleProcessMaterial sub-resource, and damping_min (0.26 in the
        # base) is absent from it, so the result must be Godot's default, not
        # the base scene's 0.26.
        p = self.parse().emitters[0].process
        self.assertAlmostEqual(p.initial_velocity_min, 500.0)
        self.assertAlmostEqual(p.damping_min, 0.0)

    def test_unset_properties_take_godot_defaults_not_zero(self):
        p = self.parse().emitters[0].process
        self.assertAlmostEqual(p.scale_min, 1.0)  # Godot default is 1.0
        self.assertAlmostEqual(p.spread, 180.0)  # authored here
        self.assertFalse(p.turbulence_enabled)  # Godot default is false

    def test_local_coords_defaults_to_false(self):
        self.assertFalse(self.parse().emitters[0].local_coords)

    def test_a_flipbook_scene_is_not_a_particle_scene(self):
        self.assertIsNone(parse_particle_scene('[node name="x" type="Sprite2D"]', LOADER.get))

    def test_a_bare_cpu_particles_ROOT_still_raises(self):
        # A CPUParticles2D LEAF is supported now (see `CpuParticlesLeafTest`),
        # but a scene whose root IS one carries none of what a `CompositeSpec`
        # holds -- no script, no `_lifetime`, no tint -- so asking this parser
        # for one stays a loud caller mistake rather than becoming an empty
        # composite.
        with self.assertRaises(ValueError):
            parse_particle_scene('[node name="x" type="CPUParticles2D"]\n', LOADER.get)

    def test_an_invisible_node_and_its_whole_subtree_are_pruned(self):
        # Added after the task-7 review: the pruning rule shipped with no test
        # of its own. The block_spark manifest assertion (`len(emitters) > 0`)
        # would pass just as happily if pruning dropped a LIVE emitter instead
        # of the two dead ones, and this is shared code every particle scene
        # walks.
        c = parse_particle_scene(VISIBILITY, LOADER.get)
        self.assertEqual([e.name for e in c.emitters], ["live"])
        self.assertEqual([e.amount for e in c.emitters], [3])

    def test_the_generic_composite_carries_no_randomisers(self):
        c = self.parse()
        self.assertIsNone(c.mirror_x)
        self.assertIsNone(c.pivot_scale)
        self.assertFalse(c.tinted)

    def test_a_bespoke_script_without_a_color_overload_is_not_tinted(self):
        # Fix-round-1 regression: "any non-generic script is tinted" was
        # wrong. NPoisonImpactVfx's Create overloads never take a Color.
        c = parse_particle_scene(POISON, LOADER.get)
        self.assertFalse(c.tinted)

    def test_an_unlisted_composite_script_raises_rather_than_guessing(self):
        unknown = (
            "[gd_scene load_steps=2 format=3]\n"
            "\n"
            '[ext_resource type="Script" path="res://src/Core/Nodes/Vfx/NUnknownImpactVfx.cs" id="1_a"]\n'  # noqa: E501
            "\n"
            '[node name="x" type="Node2D"]\n'
            'script = ExtResource("1_a")\n'
        )
        with self.assertRaises(ValueError):
            parse_particle_scene(unknown, LOADER.get)

    def test_intermediate_container_transform_composes_into_offset(self):
        c = parse_particle_scene(CONTAINER, LOADER.get)
        offset = c.emitters[0].offset
        self.assertAlmostEqual(offset[0], 100.0, places=6)
        self.assertAlmostEqual(offset[1], 70.0, places=6)

    def test_nonuniform_intermediate_scale_composes_as_a_real_matrix(self):
        # Fix-round-2 regression: a decomposed (rotation-summed,
        # scale-multiplied) accumulator gives (15, 0) here; only a real
        # Transform2D composition gives the correct (10, 0). See the
        # NON_UNIFORM_CHAIN comment above for the hand derivation.
        c = parse_particle_scene(NON_UNIFORM_CHAIN, LOADER.get)
        offset = c.emitters[0].offset
        self.assertAlmostEqual(offset[0], 10.0, places=6)
        self.assertAlmostEqual(offset[1], 0.0, places=6)

    def test_process_material_curve_and_gradient_resolve_through_the_texture_wrapper(self):
        c = parse_particle_scene(CURVE_GRADIENT, LOADER.get)
        p = c.emitters[0].process
        self.assertIsNotNone(p.damping_curve)
        # Zero-tangent Hermite is smoothstep, not a straight line — 0.15625
        # at t=0.25, not 0.25 (see test_godot_curve.py). Confirms the actual
        # curve data was resolved, not just that a Curve object exists.
        self.assertAlmostEqual(p.damping_curve.sample(0.25), 0.15625, places=6)
        self.assertIsNotNone(p.color_ramp)
        self.assertEqual(p.color_ramp.sample(0.0), (0.0, 0.0, 0.0, 1.0))
        self.assertEqual(p.color_ramp.sample(1.0), (1.0, 1.0, 1.0, 1.0))


class ScratchVfxStaggeredRestartTest(unittest.TestCase):
    """`NScratchVfx.cs` (`vfx_scratch_impact`): tinted False, no `_lifetime`
    export (script-derived 2.1s instead), and a two-phase restart —
    `_anticipationParticles` at t=0, `_impactParticles` delayed 0.1s. Task 4
    fix round 1: reported as a blocker, then implemented per the lead's
    ruling (task-4-report.md)."""

    def parse(self):
        return parse_particle_scene(SCRATCH, SCRATCH_LOADER.get)

    def test_not_tinted(self):
        # Create(Creature, bool)/Create(Vector2, bool) — the bool is
        # `goingRight`, a mirror flag, not a Color.
        self.assertFalse(self.parse().tinted)

    def test_lifetime_comes_from_the_script_table_not_a_missing_scene_field(self):
        # No `_lifetime` in SCRATCH's root section — must not silently fall
        # back to _NVFX_DEFAULT_LIFETIME (1.0); NScratchVfx.cs frees itself
        # at 0.1s (anticipation->impact gap) + 2.0s (final wait) = 2.1s.
        self.assertAlmostEqual(self.parse().lifetime, 2.1)

    def test_anticipation_group_member_has_no_delay(self):
        c = self.parse()
        flipbook = [e for e in c.emitters if e.name == "flipbook_leaf"]
        self.assertEqual(len(flipbook), 1)
        self.assertAlmostEqual(flipbook[0].start_delay, 0.0)

    def test_impact_group_members_are_delayed_point_one_seconds(self):
        c = self.parse()
        shared = [e for e in c.emitters if e.name == "shared_leaf"]
        # Both `shared_leaf_left` and `shared_leaf_right` instance the SAME
        # child scene, so both end up named "shared_leaf" (the child's own
        # internal root name) — this is the exact shape that broke a
        # name-based-only delay lookup (matching the FINAL emitter name
        # instead of the instancing name) in an earlier draft of this fix.
        self.assertEqual(len(shared), 2)
        for e in shared:
            self.assertAlmostEqual(e.start_delay, 0.1)


#: `vfx_monster_death.tscn`'s shape, trimmed to what the CPU leaf case
#: exercises: a Node2D root, a Sprite2D container with its own transform, and
#: a `CPUParticles2D` whose process parameters live on the node — with a
#: `Curve` named DIRECTLY (no `CurveTexture` wrapper) and Vector2 rather than
#: Vector3 `direction`/`gravity`.
CPU_LEAF = (
    "[gd_scene load_steps=4 format=3]\n"
    "\n"
    '[sub_resource type="Curve" id="Curve_1"]\n'
    "_data = [Vector2(0, 0), 0.0, 0.0, 0, 0, Vector2(1, 1), 0.0, 0.0, 0, 0]\n"
    "point_count = 2\n"
    "\n"
    '[sub_resource type="Gradient" id="Gradient_1"]\n'
    "colors = PackedColorArray(1, 0, 0, 1, 0, 0, 1, 1)\n"
    "\n"
    '[node name="MonsterDeathVfx" type="Node2D"]\n'
    "\n"
    '[node name="Visual" type="Sprite2D" parent="."]\n'
    "position = Vector2(10, 20)\n"
    "\n"
    '[node name="Particles" type="CPUParticles2D" parent="Visual"]\n'
    "emitting = false\n"
    "amount = 500\n"
    "lifetime = 1.8\n"
    "one_shot = true\n"
    "explosiveness = 0.08\n"
    "lifetime_randomness = 0.75\n"
    "direction = Vector2(1, -0.5)\n"
    "spread = 30.0\n"
    "gravity = Vector2(0, -100)\n"
    "initial_velocity_min = 100.0\n"
    "initial_velocity_max = 150.0\n"
    "scale_amount_min = 0.05\n"
    "scale_amount_max = 0.07\n"
    'scale_amount_curve = SubResource("Curve_1")\n'
    'color_ramp = SubResource("Gradient_1")\n'
    "anim_offset_max = 1.0\n"
)


class CpuParticlesLeafTest(unittest.TestCase):
    """`CPUParticles2D` used to raise unconditionally; `vfx_monster_death`'s
    debris burst is the first consumer, so the leaf case now builds the same
    `EmitterSpec` the GPU path does."""

    def parse(self):
        emitters = parse_emitters(CPU_LEAF, LOADER.get)
        self.assertEqual(len(emitters), 1)
        return emitters[0]

    def test_the_node_level_properties_are_read(self):
        e = self.parse()
        self.assertEqual(e.name, "Particles")
        self.assertEqual(e.amount, 500)
        self.assertAlmostEqual(e.lifetime, 1.8)
        self.assertTrue(e.one_shot)
        self.assertAlmostEqual(e.explosiveness, 0.08)

    def test_the_containers_transform_still_composes_into_the_offset(self):
        # A CPU leaf goes through the same `_Xform` walk as a GPU one; reading
        # the node before composing the parent transform would silently place
        # the burst at the effect's origin.
        self.assertEqual(self.parse().offset, (10.0, 20.0))

    def test_vector2_direction_and_gravity_are_widened_not_dropped(self):
        p = self.parse().process
        self.assertEqual(p.direction, (1.0, -0.5, 0.0))
        self.assertEqual(p.gravity, (0.0, -100.0, 0.0))

    def test_the_scale_amount_spellings_map_onto_the_scale_fields(self):
        # Reading the GPU spellings (`scale_min`/`scale_max`) against a CPU
        # node yields Godot's 1.0 defaults -- 20x too large here, and silent.
        p = self.parse().process
        self.assertAlmostEqual(p.scale_min, 0.05)
        self.assertAlmostEqual(p.scale_max, 0.07)

    def test_curves_and_gradients_are_resolved_without_the_texture_wrapper(self):
        p = self.parse().process
        self.assertIsNotNone(p.scale_curve)
        self.assertIsNotNone(p.color_ramp)

    def test_unauthored_node_fields_use_the_CPU_defaults_not_the_GPU_ones(self):
        # `CPUParticles2D` and `GPUParticles2D` share these property NAMES but
        # not all their defaults: `fixed_fps` is 0 on the CPU node
        # (`cpu_particles_2d.h:143`) against the GPU node's 30
        # (`gpu_particles_2d.cpp:1008`). `CPU_LEAF` authors none of the three,
        # so borrowing the GPU table here was silently wrong for one of them.
        # 0 means "simulate at the display rate", which the web simulator maps
        # back to 30 itself -- so this is about what the manifest CLAIMS the
        # game authored, not about a visible change.
        e = self.parse()
        self.assertEqual(e.fixed_fps, 0)
        self.assertAlmostEqual(e.randomness, 0.0)
        self.assertFalse(e.local_coords)

    def test_the_rest_of_the_process_fields_keep_their_godot_defaults(self):
        # `CPUParticles2D` has no turbulence / radial-velocity / flatness
        # equivalent at all, so those must read as defaults rather than zeros
        # invented by a partial parse.
        p = self.parse().process
        self.assertFalse(p.turbulence_enabled)
        self.assertAlmostEqual(p.turbulence_noise_scale, 9.0)
        self.assertAlmostEqual(p.flatness, 0.0)

    def test_parse_emitters_does_not_require_a_composite_script(self):
        # `parse_particle_scene` raises for a root script that is not in
        # `_TINTED_SCRIPTS`; `vfx_monster_death`'s root runs
        # `NMonsterDeathVfx`, which is not a composite script at all. That is
        # the whole reason `parse_emitters` exists as its own entry point.
        scripted = CPU_LEAF.replace(
            '[node name="MonsterDeathVfx" type="Node2D"]\n',
            '[node name="MonsterDeathVfx" type="Node2D"]\nscript = ExtResource("nope")\n',
        )
        self.assertEqual(len(parse_emitters(scripted, LOADER.get)), 1)


#: The real `vfx_monster_death.tscn` CPUParticles2D node, verbatim from the
#: pck, plus the `CanvasItemMaterial` it names. Used by the record-completeness
#: test below, which needs the ACTUAL authored property set rather than the
#: trimmed `CPU_LEAF` fixture.
REAL_MONSTER_DEATH_CPU_SECTION = """unique_name_in_owner = true
material = SubResource("CanvasItemMaterial_64doj")
emitting = false
amount = 500
texture = ExtResource("3_bvleg")
lifetime = 1.8
one_shot = true
speed_scale = 1.5
explosiveness = 0.08
lifetime_randomness = 0.75
emission_shape = 1
emission_sphere_radius = 99.23
direction = Vector2(1, -0.5)
spread = 30.0
gravity = Vector2(0, -100)
initial_velocity_min = 100.0
initial_velocity_max = 150.0
angular_velocity_min = -108.83
angular_velocity_max = 100.46
angle_min = -180.0
angle_max = 180.0
scale_amount_min = 0.05
scale_amount_max = 0.07
scale_amount_curve = SubResource("Curve_6prs8")
anim_speed_min = 0.5
anim_speed_max = 1.5
anim_offset_max = 1.0
"""

REAL_MONSTER_DEATH_CANVAS_ITEM_MATERIAL = """particles_animation = true
particles_anim_h_frames = 8
particles_anim_v_frames = 1
particles_anim_loop = true
"""


class CpuDefaultsTest(unittest.TestCase):
    """Every `CPUParticles2D` fallback this port uses, pinned to Godot's own
    value. Unauthored fallbacks are invisible in the extracted manifest of a
    scene that authors the property, so nothing else pins them — which is how
    `fixed_fps` shipped as the GPU node's 30, and how `gravity` shipped as the
    3-D `ParticleProcessMaterial` vector."""

    def parse_bare(self):
        # A CPUParticles2D authoring NOTHING: every field falls back.
        scene = (
            "[gd_scene load_steps=1 format=3]\n"
            "\n"
            '[node name="Root" type="Node2D"]\n'
            "\n"
            '[node name="Bare" type="CPUParticles2D" parent="."]\n'
        )
        emitters = parse_emitters(scene, LOADER.get)
        self.assertEqual(len(emitters), 1)
        return emitters[0]

    def test_gravity_falls_back_to_the_CPU_nodes_value_not_the_3d_materials(self):
        # `cpu_particles_2d.h:185` — `Vector2 gravity = Vector2(0, 980)`.
        # `_PPM_DEFAULT_GRAVITY` is `(0, -9.8, 0)`: a 3-D vector in metres,
        # ~100x smaller AND opposite in sign, since a CPUParticles2D's gravity
        # is screen pixels with +Y pointing down. Borrowing it was silent —
        # `vfx_monster_death` authors gravity, so no extracted value moved.
        self.assertEqual(self.parse_bare().process.gravity, (0.0, 980.0, 0.0))

    def test_the_node_level_fallbacks_are_the_CPU_nodes_own(self):
        e = self.parse_bare()
        self.assertEqual(e.amount, 8)  # cpu_particles_2d.cpp:1650
        self.assertAlmostEqual(e.lifetime, 1.0)  # h:135
        self.assertFalse(e.one_shot)  # h:133
        self.assertAlmostEqual(e.explosiveness, 0.0)  # h:138
        self.assertAlmostEqual(e.randomness, 0.0)  # h:139
        self.assertEqual(e.fixed_fps, 0)  # h:143 — the GPU node's is 30
        self.assertFalse(e.local_coords)  # h:142

    def test_the_process_fallbacks_that_DO_coincide_still_hold(self):
        # The audit recorded in `_CPU_DEFAULT_GRAVITY`'s comment claims these
        # coincide with the `_PPM_DEFAULT_*` values. Assert the claim, so
        # "audited, coincides" is a checked statement and not a promise.
        p = self.parse_bare().process
        self.assertEqual(p.direction, (1.0, 0.0, 0.0))  # h:160
        self.assertAlmostEqual(p.spread, 45.0)  # h:161
        self.assertAlmostEqual(p.lifetime_randomness, 0.0)  # h:140
        self.assertAlmostEqual(p.initial_velocity_min, 0.0)  # cpp:1656
        self.assertAlmostEqual(p.initial_velocity_max, 0.0)  # cpp:1669
        self.assertAlmostEqual(p.damping_min, 0.0)  # cpp:1662
        self.assertAlmostEqual(p.scale_min, 1.0)  # cpp:1664
        self.assertAlmostEqual(p.scale_max, 1.0)  # cpp:1677
        self.assertAlmostEqual(p.anim_offset_max, 0.0)  # cpp:1680
        self.assertFalse(p.particle_flag_align_y)  # cpp:1683


class CpuDroppedParameterRecordTest(unittest.TestCase):
    """`_CPU_PARAMS_NOT_MODELLED` is the whole justification for NOT widening
    `ProcessSpec`, so it has to be complete and self-consistent. It was
    neither: its prose counted `CanvasItemMaterial particles_anim_*` while its
    tuple listed none of the four names, and it omitted every acceleration and
    hue-variation param too. A hand-kept record drifts; this test makes it fail
    instead."""

    def _port_reads(self) -> set[str]:
        """Property names `_parse_cpu_process`/`_build_cpu_emitter` actually
        read, derived from their SOURCE rather than hand-listed — a hand-kept
        mirror of a hand-kept record would drift the same way.

        Both functions reach every property through a helper whose second
        argument is the literal property name (`_scalar(section, "spread")`,
        `_direct_curve_field(section, "scale_amount_curve", ...)`), so the
        literals are exactly the read set.
        """
        src = inspect.getsource(particle_scene)
        reads: set[str] = set()
        for fn in ("_parse_cpu_process", "_build_cpu_emitter"):
            body = src.split(f"def {fn}(", 1)[1].split("\ndef ", 1)[0]
            reads |= set(
                re.findall(
                    r"_(?:scalar|int|bool|color|vector2|direct_curve_field"
                    r"|direct_gradient_field|ext_ref_path|sub_ref_id)\("
                    r"\s*section\s*,\s*\"([^\"]+)\"",
                    body,
                )
            )
        # Composed by `_local_xform` on the way in, not read in either body.
        return reads | {"position", "scale", "rotation"}

    def test_the_port_read_set_is_actually_derivable(self):
        # If the regex above ever stops matching, every other assertion in
        # this class goes vacuous — the whole authored set would land in the
        # "unaccounted" bucket and the partition test would fail loudly, but
        # this makes the failure legible.
        reads = self._port_reads()
        for expected in ("spread", "gravity", "scale_amount_curve", "amount", "texture"):
            self.assertIn(expected, reads)

    #: Properties the CPU path reads that `vfx_monster_death` does NOT author,
    #: so they only ever appear as fallbacks. Each is a real `CPUParticles2D`
    #: property — cited, because the defect this guards against is reading one
    #: that is not: `alpha_curve` was read here until a grep of Godot 4.5's
    #: `cpu_particles_2d.{h,cpp}` for `alpha_curve`/`PARAM_ALPHA` returned zero
    #: hits and its `Parameter` enum turned out to have no alpha member. A
    #: read of a non-existent property is silently always-`None`, so nothing
    #: else notices.
    _UNAUTHORED_BUT_REAL = frozenset(
        {
            "randomness",  # cpu_particles_2d.h:139 (randomness_ratio)
            "fixed_fps",  # h:143
            "local_coords",  # h:142
            "rotation",  # Node2D
            "position",  # Node2D
            "scale",  # Node2D
            "self_modulate",  # CanvasItem
            "z_index",  # CanvasItem
            "damping_min",  # PARAM_DAMPING, cpp:1662
            "damping_max",  # PARAM_DAMPING, cpp:1675
            "damping_curve",  # curve_parameters[PARAM_DAMPING]
            "color_ramp",  # h:168
            "color_initial_ramp",  # h:169
            "anim_offset_min",  # PARAM_ANIM_OFFSET, cpp:1667
            "particle_flag_align_y",  # h:171 (particle_flags)
            # The three curve parameters the port now reads. Real properties
            # on `CPUParticles2D` — each is an `ADD_PROPERTYI(... "Curve") ->
            # set_param_curve` binding — that THIS node simply leaves unset,
            # exactly like `damping_curve` above. It authors the min/max of all
            # three (`angle_min/max`, `angular_velocity_min/max`,
            # `anim_speed_min/max`) and no curve for any of them.
            "angle_curve",  # cpu_particles_2d.cpp:1590, PARAM_ANGLE
            "angular_velocity_curve",  # cpu_particles_2d.cpp:1566
            "anim_speed_curve",  # cpu_particles_2d.cpp:1611, PARAM_ANIM_SPEED
        }
    )

    def test_the_port_reads_no_property_CPUParticles2D_does_not_have(self):
        # Every name the port reads is either authored by the real scene (so
        # it demonstrably exists) or explicitly listed above as a real-but-
        # unauthored property. A read of something Godot has no such property
        # for lands in neither bucket.
        authored = _authored_property_names(REAL_MONSTER_DEATH_CPU_SECTION)
        stray = self._port_reads() - authored - self._UNAUTHORED_BUT_REAL
        self.assertEqual(stray, set())

    def test_the_dropped_parameter_record_is_complete(self):
        # Every property the REAL monster-death node authors must land in
        # exactly one bucket: read by the port, recorded as a dropped
        # parameter, or named as not-a-parameter.
        authored = _authored_property_names(REAL_MONSTER_DEATH_CPU_SECTION)
        authored |= _authored_property_names(REAL_MONSTER_DEATH_CANVAS_ITEM_MATERIAL)
        unaccounted = (
            authored
            - self._port_reads()
            - set(_CPU_PARAMS_NOT_MODELLED)
            - set(_CPU_PROPS_NOT_PARAMETERS)
        )
        self.assertEqual(unaccounted, set())

    def test_the_four_particles_anim_names_are_in_the_record(self):
        # The specific omission that motivated this test: the prose counted
        # them, the tuple did not contain them.
        for name in (
            "particles_animation",
            "particles_anim_h_frames",
            "particles_anim_v_frames",
            "particles_anim_loop",
        ):
            with self.subTest(name=name):
                self.assertIn(name, _CPU_PARAMS_NOT_MODELLED)

    def test_no_recorded_parameter_is_also_read_by_the_port(self):
        # The two buckets must be disjoint, or the record claims a gap that
        # does not exist — which would be just as misleading as omitting one.
        self.assertEqual(set(_CPU_PARAMS_NOT_MODELLED) & self._port_reads(), set())
        self.assertEqual(set(_CPU_PROPS_NOT_PARAMETERS) & self._port_reads(), set())

    def test_the_counts_are_counts_not_booleans(self):
        # The record's whole value is that it says HOW MUCH is dropped. A
        # table of 1s and 0s would read as a checklist and lose that.
        self.assertGreater(max(_CPU_PARAMS_NOT_MODELLED.values()), 10)
        self.assertGreater(sum(1 for v in _CPU_PARAMS_NOT_MODELLED.values() if v == 0), 0)


if __name__ == "__main__":
    unittest.main()
