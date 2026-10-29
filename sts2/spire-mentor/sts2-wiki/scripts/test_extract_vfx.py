import contextlib
import io
import json
import os
import re
import shutil
import sys
import tempfile
import unittest
import unittest.mock
from collections import Counter
from dataclasses import fields as dc_fields
from pathlib import Path
from typing import Any

from scripts.anim_track import BezierKey, TrackSpec
from scripts.extract_rig import read_pck_index
from scripts.extract_vfx import (
    DISSOLVE_EFFECTS,
    FLIPBOOK_EFFECTS,
    PARTICLE_EFFECTS,
    SPINE_EFFECTS,
    SPRITE_SHADER_EFFECTS,
    TRANSFORM_ANIM_EFFECTS,
    VFX_LOC_STRINGS,
    _camel,
    _extract_loc_strings,
    _serialize_transform_anim_sprite,
    blend_mode,
    content_rev,
    extract_vfx,
)
from scripts.extract_vfx import main as extract_vfx_main
from scripts.particle_scene import (
    _CPU_PARAMS_NOT_MODELLED,
    CompositeSpec,
    EmitterSpec,
    MaterialSpec,
)


def _resolve_pck() -> Path:
    """Find the vendored pck, honouring `STS2_PCK` first.

    This worktree does not contain `sts2_binaries/` (it's untracked and only
    exists in the main checkout), so the naive `parents[2]` from this file
    lands inside the worktree and finds nothing. Walk up from `__file__`
    instead — that reaches the monorepo root (`sts2-mono/`) from any worktree
    depth and finds the vendored copy there.
    """
    env = os.environ.get("STS2_PCK")
    if env:
        return Path(env)
    here = Path(__file__).resolve()
    for parent in here.parents:
        candidate = parent / "sts2_binaries/v0.107.1/SlayTheSpire2.pck"
        if candidate.is_file():
            return candidate
    # Nothing found while walking up — return a path that `is_file()` reports
    # False for, so the class-level `skipUnless` below skips cleanly.
    return here.parents[2] / "sts2_binaries/v0.107.1/SlayTheSpire2.pck"


PCK = _resolve_pck()

# The artifact tree `just extract-vfx` writes into and the repo commits.
COMMITTED_IMAGES = Path(__file__).resolve().parents[1] / "site" / "public" / "images"


def _read_pck_text(pck: Path, res_path: str) -> str:
    """One pck resource as text. The extractor keeps its own reader private;
    this is the two-line equivalent a test needs to re-derive a scene fact
    instead of restating it from a comment."""
    from scripts.extract_rig import read_pck_index

    offset, size = read_pck_index(str(pck))[res_path]
    with open(pck, "rb") as f:
        f.seek(offset)
        return f.read(size).decode("utf-8", "replace")


@unittest.skipUnless(PCK.is_file(), f"vendored pck not present at {PCK}")
class ExtractVfxTest(unittest.TestCase):
    """Against the real pck: these numbers are the game's, not ours."""

    _tmp: tempfile.TemporaryDirectory[str]
    out: Path
    manifest: dict[str, Any]

    @classmethod
    def setUpClass(cls) -> None:
        cls._tmp = tempfile.TemporaryDirectory()
        cls.out = Path(cls._tmp.name)
        extract_vfx(str(PCK), cls.out, flipbooks=FLIPBOOK_EFFECTS, spines=[])
        cls.manifest = json.loads((cls.out / "vfx" / "manifest.json").read_text())

    @classmethod
    def tearDownClass(cls) -> None:
        cls._tmp.cleanup()

    def test_block_has_its_six_frames_on_disk(self) -> None:
        entry = self.manifest["effects"]["vfx_block"]
        self.assertEqual(entry["kind"], "flipbook")
        self.assertEqual(len(entry["frames"]), 6)
        for rel in entry["frames"]:
            self.assertTrue((self.out / rel).is_file(), rel)

    def test_block_frames_are_the_games_dimensions(self) -> None:
        from PIL import Image

        rel = self.manifest["effects"]["vfx_block"]["frames"][0]
        with Image.open(self.out / rel) as im:
            self.assertEqual(im.size, (209, 239))

    def test_slash_carries_its_authored_scale_and_rotation(self) -> None:
        entry = self.manifest["effects"]["vfx_attack_slash"]
        self.assertEqual(len(entry["frames"]), 4)
        self.assertEqual(entry["scale"], 0.75)
        self.assertTrue(entry["randomizeRotation"])
        self.assertEqual(entry["rotationRange"], [0.0, 25.0])
        self.assertEqual(entry["fps"], 15.0)

    def test_frame_paths_are_relative_to_the_images_root(self) -> None:
        # `assetUrl(rel)` resolves against `/assets`, which IS images_root.
        for rel in self.manifest["effects"]["vfx_block"]["frames"]:
            self.assertTrue(rel.startswith("vfx/"), rel)

    def test_manifest_carries_a_revision(self) -> None:
        # The art bucket serves immutable at non-content-addressed URLs; the
        # client appends ?v=<rev> or a redeploy serves stale frames.
        self.assertTrue(self.manifest["rev"])
        self.assertTrue(self.manifest["rev"].startswith("v0.107.1-"))

    def test_the_revision_tracks_the_ART_not_the_calendar(self) -> None:
        """A changed frame must move the rev; an unchanged re-extract must not.

        This is the whole reason the rev is derived rather than hardcoded: the
        bucket serves the frames ``immutable``, so a rev that does not move is
        a cache bust that does nothing, and the first deploy after a frame
        change keeps serving the old art (the audio pipeline was bitten by
        exactly this).
        """
        with tempfile.TemporaryDirectory() as tmp:
            again = extract_vfx(str(PCK), Path(tmp), flipbooks=FLIPBOOK_EFFECTS, spines=[])
            self.assertEqual(again["rev"], self.manifest["rev"])
            # Now perturb one extracted frame and re-derive from what is on
            # disk: the rev must move.
            frame = Path(tmp) / again["effects"]["vfx_block"]["frames"][0]
            written = sorted(p for p in Path(tmp).rglob("*.png"))
            before = content_rev(Path(tmp), written)
            frame.write_bytes(frame.read_bytes() + b"\0")
            self.assertNotEqual(content_rev(Path(tmp), written), before)

    def test_a_spine_scenes_slot_emitters_reach_the_manifest(self) -> None:
        # GH #669. `kaiser_crab_boss_explosion.tscn` is a Spine rig AND a
        # `GPUParticles2D` parented to a `SpineSlotNode` on that rig's own
        # `explosion_slot`. Neither `SPINE_EFFECTS` (pure rigs) nor
        # `PARTICLE_EFFECTS` (pure emitters) had a member for the composition;
        # `emitters` on a spine entry is it.
        with tempfile.TemporaryDirectory() as tmp:
            out = extract_vfx(
                str(PCK),
                Path(tmp),
                flipbooks=[],
                spines=[
                    (
                        "vfx_kaiser_crab_boss_explosion",
                        "scenes/vfx/monsters/kaiser_crab_boss_explosion.tscn",
                        "infested_boss_explosion",
                    )
                ],
            )
        entry = out["effects"]["vfx_kaiser_crab_boss_explosion"]
        self.assertEqual(entry["kind"], "spine")
        [emitter] = entry["emitters"]
        # The slot is what makes this emitter follow the rig's animation
        # rather than sit at a fixed offset from the effect root.
        self.assertEqual(emitter["slot"], "explosion_slot")
        # ...and the trigger and one-shot come from the effect's C# script
        # (`NKaiserCrabBossExplosionVfx`), not from the scene: `_Ready` sets
        # `OneShot = true`, which the `.tscn` never authors, and
        # `OnAnimationEvent` restarts on `left_embers_start`. Taking the
        # scene's own `one_shot` default would loop a one-off burst.
        self.assertEqual(emitter["event"], "left_embers_start")
        self.assertTrue(emitter["oneShot"])
        # `blend_mode = 1` on a `CanvasItemMaterial` SUB-resource. `material`
        # models `ShaderMaterial` `.tres` ext-resources only, so this flag is
        # the only place the additive blend survives — without it the embers
        # draw as a straight-alpha mix.
        self.assertTrue(emitter["blendAdd"])
        self.assertIsNone(emitter["material"])
        self.assertEqual(emitter["amount"], 20)
        # The trail is not decoration here, it is the effect: a 24x24 dot at
        # `scale_min/max` 0.1-0.4 under a 0.25 `SpineSprite` is under two
        # screen pixels, and what the player sees is the streak the 0.05 s
        # ribbon sweeps out at 2500-5000 units/s. Dropping these two fields
        # renders the burst invisible without failing anything else.
        self.assertTrue(emitter["trailEnabled"])
        self.assertAlmostEqual(emitter["trailLifetime"], 0.05)

    def test_an_emitter_that_authors_no_trail_takes_godots_defaults(self) -> None:
        # The other side of the trail fields: `vfx_attack_blunt` authors
        # neither, so it must arrive `false` / 0.3 (Godot's own default,
        # `gpu_particles_2d.cpp:1012-1013`) rather than inheriting anything
        # from the one scene in scope that does author them. All 14
        # `PARTICLE_EFFECTS` composites are in this case.
        with tempfile.TemporaryDirectory() as tmp:
            out = extract_vfx(
                str(PCK),
                Path(tmp),
                flipbooks=[],
                spines=[],
                particles=[("vfx_attack_blunt", "scenes/vfx/vfx_attack_blunt.tscn")],
            )
        for emitter in out["effects"]["vfx_attack_blunt"]["emitters"]:
            self.assertFalse(emitter["trailEnabled"])
            self.assertAlmostEqual(emitter["trailLifetime"], 0.3)

    def test_a_spine_scene_with_no_slot_emitters_omits_the_key(self) -> None:
        # ABSENT, not `[]`: every other spine entry stays byte-identical to
        # what it was before GH #669, which is what keeps the committed-
        # artifact gate honest about what actually changed.
        with tempfile.TemporaryDirectory() as tmp:
            out = extract_vfx(
                str(PCK),
                Path(tmp),
                flipbooks=[],
                spines=[("vfx_bite", "scenes/vfx/vfx_bite.tscn", "attackbite")],
            )
        self.assertNotIn("emitters", out["effects"]["vfx_bite"])

    def test_a_non_spine_scene_in_spines_raises_rather_than_writing_a_fallback(
        self,
    ) -> None:
        # A `SPINE_EFFECTS` entry that does not parse as a `SpineVfxSpec` must
        # fail the extraction loudly, not silently write `scale: 1.0,
        # clip: null` into the manifest and report success — that silent
        # fallback is exactly how fix round 1's bug happened (task-2-findings-
        # r2.md finding 1).
        # `source="chain"` is a real Spine rig stem (valid `.spskel` etc.), so
        # a pre-fix run reaches the fallback and completes "successfully"
        # instead of erroring on an unrelated missing-rig-file lookup — this
        # isolates the one behaviour under test: does a spec/scene-shape
        # mismatch raise?
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):
                extract_vfx(
                    str(PCK),
                    Path(tmp),
                    flipbooks=[],
                    spines=[("not_spine", "scenes/vfx/vfx_block.tscn", "chain")],
                )


@unittest.skipUnless(PCK.is_file(), f"vendored pck not present at {PCK}")
class ExtractVfxLocStringsTest(unittest.TestCase):
    """Task 12 fix round 1: `Combatant.tsx`'s "Blocked" / "Wears Off" text
    must come from the pck's loc rows via the manifest, not a bare literal.
    """

    def test_loc_strings_match_the_pcks_own_table(self) -> None:
        # Re-derive the expected values independently of `extract_vfx` (via
        # the same `read_pck_index` the extractor itself uses, not by
        # hardcoding "Blocked"/"Wears Off" a second time) — a test that just
        # repeated the extractor's own two literals would pass even if BOTH
        # copies drifted from a future pck the same way.
        offset, size = read_pck_index(str(PCK))["localization/eng/vfx.json"]
        with open(PCK, "rb") as f:
            f.seek(offset)
            table = json.loads(f.read(size).decode("utf-8"))
        with tempfile.TemporaryDirectory() as tmp:
            manifest = extract_vfx(
                str(PCK), Path(tmp), flipbooks=[], spines=[], loc_strings=VFX_LOC_STRINGS
            )
        self.assertEqual(manifest["locStrings"]["blocked"], table["BLOCKED"])
        self.assertEqual(manifest["locStrings"]["powerWearsOff"], table["POWER_WEARS_OFF"])

    def test_omitted_when_not_requested(self) -> None:
        # `Combatant.tsx` falls back to its own English literal when the
        # manifest carries none — this is the "manifest incomplete" half of
        # that contract: no `loc_strings` kwarg means no `locStrings` key at
        # all, not an empty dict masquerading as "checked, found nothing".
        with tempfile.TemporaryDirectory() as tmp:
            manifest = extract_vfx(str(PCK), Path(tmp), flipbooks=[], spines=[])
        self.assertNotIn("locStrings", manifest)

    def test_unknown_loc_row_raises_rather_than_omitting_the_key(self) -> None:
        index = read_pck_index(str(PCK))
        with self.assertRaises(KeyError):
            _extract_loc_strings(index, PCK, [("blocked", "vfx", "NOT_A_REAL_ROW")])

    def test_a_loc_only_change_still_moves_the_rev(self) -> None:
        # `content_rev`'s `extra` param exists so a game update that only
        # changes English text (no art) still busts the `immutable` cache —
        # see its docstring. Proven directly on `content_rev` rather than by
        # mutating the vendored pck (not writable/not this test's job).
        written: list[Path] = []
        images_root = Path(PCK).parent  # any real dir; `written` is empty
        before = content_rev(images_root, written, extra=b'{"blocked": "Blocked"}')
        after = content_rev(images_root, written, extra=b'{"blocked": "Blockedx"}')
        self.assertNotEqual(before, after)


@unittest.skipUnless(PCK.is_file(), f"vendored pck not present at {PCK}")
class ExtractParticlesTest(unittest.TestCase):
    """Against the real pck: these numbers are the game's, not ours."""

    _tmp: tempfile.TemporaryDirectory[str]
    out: Path
    manifest: dict[str, Any]

    @classmethod
    def setUpClass(cls) -> None:
        cls._tmp = tempfile.TemporaryDirectory()
        cls.out = Path(cls._tmp.name)
        extract_vfx(str(PCK), cls.out, flipbooks=[], spines=[], particles=PARTICLE_EFFECTS)
        cls.manifest = json.loads((cls.out / "vfx" / "manifest.json").read_text())

    @classmethod
    def tearDownClass(cls) -> None:
        cls._tmp.cleanup()

    def test_all_14_particle_effects_present(self) -> None:
        self.assertEqual(len(PARTICLE_EFFECTS), 14)
        for name, _ in PARTICLE_EFFECTS:
            self.assertIn(name, self.manifest["effects"], name)
            self.assertEqual(self.manifest["effects"][name]["kind"], "particles")

    def test_vfx_scratch_impact_stagger_survives_extraction(self) -> None:
        # Fix round 1 (task-4-report.md): NScratchVfx.cs restarts
        # `_anticipationParticles` at t=0 and `_impactParticles` 0.1s later,
        # and has no `_lifetime` export of its own (script-derived 2.1s).
        entry = self.manifest["effects"]["vfx_scratch_impact"]
        self.assertAlmostEqual(entry["lifetime"], 2.1)
        self.assertFalse(entry["tinted"])
        delays = {e["name"]: e["startDelay"] for e in entry["emitters"]}
        self.assertAlmostEqual(delays["vfx_scratch_flipbook"], 0.0)
        for impact_name in ("vfx_common_hit_flare", "vfx_common_specks"):
            self.assertAlmostEqual(delays[impact_name], 0.1, msg=impact_name)
        # The two `vfx_big_slash_impact_side_smoke_{left,right}` instances
        # collide on `EmitterSpec.name` (both are the shared child scene's
        # own internal root name) — both must still carry the delay.
        smoke_delays = [
            e["startDelay"]
            for e in entry["emitters"]
            if e["name"].startswith("vfx_big_slash_impact_side_smoke")
        ]
        self.assertEqual(len(smoke_delays), 2)
        for d in smoke_delays:
            self.assertAlmostEqual(d, 0.1)

    def test_particle_manifest_entry_shape(self) -> None:
        entry = self.manifest["effects"]["vfx_attack_blunt"]
        self.assertEqual(entry["kind"], "particles")
        self.assertAlmostEqual(entry["lifetime"], 1.25)
        self.assertGreater(len(entry["emitters"]), 0)
        e = entry["emitters"][0]
        for field in ("texture", "amount", "lifetime", "explosiveness", "fixedFps", "process"):
            self.assertIn(field, e)

    def _blunt_specks_emitter(self) -> dict[str, Any]:
        # Named lookup rather than `emitters[0]`: the composite's real node
        # order is [common_blunt_impact_core, common_specks, common_glow] —
        # only `vfx_common_specks` (spec §2.1's own worked example) carries
        # `damping_curve`/`color_initial_ramp`, so pinning by name is both
        # correct against the real pck and robust to node-order changes.
        e: dict[str, Any]
        for e in self.manifest["effects"]["vfx_attack_blunt"]["emitters"]:
            if e["name"] == "vfx_common_specks":
                return e
        raise AssertionError("vfx_common_specks emitter not found in vfx_attack_blunt")

    def test_curves_are_baked_to_lut_files_the_manifest_names(self) -> None:
        e = self._blunt_specks_emitter()
        lut = e["process"]["dampingCurve"]
        self.assertTrue(lut.startswith("vfx_particles/"))
        self.assertTrue((self.out / lut).is_file())

    def test_hdr_ramps_are_float_arrays_not_pngs(self) -> None:
        # color_initial_ramp reaches 2.45; an 8-bit PNG clips it to white.
        ramp = self._blunt_specks_emitter()["process"]["colorInitialRamp"]
        self.assertIsInstance(ramp, list)
        self.assertGreater(max(c[0] for c in ramp), 1.0)

    def test_a_shared_leaf_scene_is_extracted_once(self) -> None:
        # vfx_common_specks appears in six of the 13 composites; its baked LUTs
        # (and its decoded texture) must not be written six times under six
        # different content-addressed/derived names — the SAME curve data must
        # resolve to the SAME lut path everywhere it's referenced.
        lut_refs: list[str] = []
        texture_refs: list[str] = []
        for effect in self.manifest["effects"].values():
            if effect.get("kind") != "particles":
                continue
            for em in effect["emitters"]:
                texture_refs.append(em["texture"])
                for v in em["process"].values():
                    if isinstance(v, str) and v.startswith("vfx_particles/"):
                        lut_refs.append(v)
        self.assertGreater(len(lut_refs), 0)
        # Content-addressing means repeats collapse to fewer distinct files
        # than references — proving reuse, not just absence of collision.
        self.assertLess(len(set(lut_refs)), len(lut_refs))
        self.assertLess(len(set(texture_refs)), len(texture_refs))
        for lut in set(lut_refs):
            self.assertTrue((self.out / lut).is_file(), lut)

    def test_textures_written_under_the_shared_vfx_tree(self) -> None:
        e = self.manifest["effects"]["vfx_attack_blunt"]["emitters"][0]
        self.assertTrue(e["texture"].startswith("vfx/"), e["texture"])
        self.assertTrue((self.out / e["texture"]).is_file())

    def test_particle_effects_have_no_flipbook_or_spine_fields(self) -> None:
        entry = self.manifest["effects"]["vfx_attack_blunt"]
        self.assertNotIn("frames", entry)
        self.assertNotIn("rig", entry)

    def test_every_dataclass_field_reaches_the_manifest(self) -> None:
        # `ProcessSpec` serializes generically (a dataclasses.fields() loop in
        # _serialize_process), so it's complete by construction. `EmitterSpec`
        # and `CompositeSpec` serialize via HAND-BUILT dicts instead
        # (_serialize_emitter; the particle-loop's composite dict literal) —
        # complete today, but a future field added to either dataclass with no
        # matching manifest key would silently never reach the browser, with
        # nothing failing. This happened once already in this task:
        # `start_delay` only reached the manifest because the second edit
        # site was remembered by hand. Walk the real dataclasses and assert
        # every field's camelCase name is a manifest key.
        e = self.manifest["effects"]["vfx_attack_blunt"]["emitters"][0]
        for f in dc_fields(EmitterSpec):
            self.assertIn(_camel(f.name), e, f.name)

        entry = self.manifest["effects"]["vfx_attack_blunt"]
        for f in dc_fields(CompositeSpec):
            self.assertIn(_camel(f.name), entry, f.name)

        # `MaterialSpec` serializes by hand too (_serialize_material), and it
        # is the one dataclass whose fields decide whether a particle is the
        # right COLOUR — a field silently missing here is a wrong-looking
        # effect, not a crash. Pick an emitter that actually has a material.
        with_material = next(
            em
            for eff in self.manifest["effects"].values()
            if eff.get("kind") == "particles"
            for em in eff["emitters"]
            if em["material"] is not None
        )
        for f in dc_fields(MaterialSpec):
            self.assertIn(_camel(f.name), with_material["material"], f.name)

    def test_material_lut_carries_the_real_authored_colour(self) -> None:
        # The whole point of extracting the material: `lut` is what colourises
        # a grayscale particle texture. Asserting only that it is non-null
        # would pass with every gradient baked to white, which is exactly the
        # bug this extraction exists to fix — so assert a COLOUR.
        #
        # `vfx_blunt_impact_core_flipbook.tres` authors its lut as a Gradient
        # with `colors = PackedColorArray(1, 0.20615101, 0, 1, 1, 1, 1, 1)`
        # and NO `offsets` line, i.e. Godot's two default stops recoloured:
        # orange at 0, white at 1.
        emitter = next(
            em
            for eff in self.manifest["effects"].values()
            if eff.get("kind") == "particles"
            for em in eff["emitters"]
            if em["material"]
            and em["material"]["path"].endswith("vfx_blunt_impact_core_flipbook.tres")
        )
        material = emitter["material"]
        self.assertEqual(material["shader"], "vfx_row_flipbook_shader")
        self.assertEqual(material["frameCount"], 20.0)
        self.assertEqual(material["flipbookSize"], [5.0, 4.0])
        self.assertIsNotNone(material["flipbookCurve"])

        lut = json.loads((self.out / material["lut"]).read_text())
        r, g, b, _a = lut[0]
        self.assertAlmostEqual(r, 1.0, places=3)
        self.assertAlmostEqual(g, 0.20615101, places=3)
        self.assertAlmostEqual(b, 0.0, places=3)
        # ...and it really is a RAMP to white, not a constant.
        self.assertAlmostEqual(lut[-1][1], 1.0, places=3)

    #: The one shader in use that declares no `lut` uniform at all:
    #: `vfx_ring_polar_shader` takes its colour straight from `vertex_color`
    #: and gates alpha on a polar erosion smoothstep. A null lut is CORRECT
    #: for it, so it is named here rather than the whole assertion being
    #: loosened — if a second shader ever lands here, that is news.
    _SHADERS_WITHOUT_A_LUT = {"vfx_ring_polar_shader"}

    def test_every_material_bearing_emitter_resolves_a_lut(self) -> None:
        missing = [
            (name, em["name"], em["material"]["shader"])
            for name, eff in self.manifest["effects"].items()
            if eff.get("kind") == "particles"
            for em in eff["emitters"]
            if em["material"] is not None
            and em["material"]["lut"] is None
            and em["material"]["shader"] not in self._SHADERS_WITHOUT_A_LUT
        ]
        self.assertEqual(missing, [])

        # ...and the exemption is not dead weight: the shader it names must
        # actually be in use, or it is silently excusing nothing.
        shaders_in_use = {
            em["material"]["shader"]
            for eff in self.manifest["effects"].values()
            if eff.get("kind") == "particles"
            for em in eff["emitters"]
            if em["material"] is not None
        }
        self.assertTrue(self._SHADERS_WITHOUT_A_LUT <= shaders_in_use)

    def test_the_bespoke_transforms_mark_exactly_their_own_descendants(self) -> None:
        # These flags exist because the renderer previously inferred them by
        # SUBSTRING-matching a munged node name, which over-matched:
        # `vfx_spore_impact`'s pivot node `poof_pivot` has exactly ONE child
        # (`vfx_spore_impact_poof_flipbook`), but the composite also holds a
        # root-level `vfx_poof` that "poof" matches too. Assert the counts.
        pivoted = [
            (name, em["name"])
            for name, eff in self.manifest["effects"].items()
            if eff.get("kind") == "particles"
            for em in eff["emitters"]
            if em["underPivotScale"]
        ]
        mirrored = [
            (name, em["name"])
            for name, eff in self.manifest["effects"].items()
            if eff.get("kind") == "particles"
            for em in eff["emitters"]
            if em["underMirrorX"]
        ]
        self.assertEqual(pivoted, [("vfx_spore_impact", "vfx_spore_impact_poof_flipbook")])
        self.assertEqual(mirrored, [("vfx_poison_impact", "vfx_horizontal_smoke")])

    def test_anim_offset_is_extracted_where_the_scene_authors_it(self) -> None:
        # `params.animation_offset` is what gives each particle its own
        # starting frame of a flipbook; the pck authors it on 190 emitters and
        # never at the default, so an all-zero result means it was dropped.
        authored = [
            em["name"]
            for eff in self.manifest["effects"].values()
            if eff.get("kind") == "particles"
            for em in eff["emitters"]
            if em["process"]["animOffsetMax"] != 0.0
        ]
        self.assertNotEqual(authored, [])

    def test_sandy_impacts_ash_rock_texture_lives_outside_images(self) -> None:
        # `vfx_sandy_impact`'s "rock" emitter sources
        # res://animations/vfx/vfx_sandy_impact/vfx_ash_rock.png — the
        # flipbook-animation tree, not images/. `_out_rel_texture` only
        # special-cases stripping a leading `images/`; a source path that
        # doesn't start with it passes through unchanged, landing this
        # texture at `animations/...` under images_root rather than under
        # `vfx/`. Not a bug (verified against the real pck), but nothing
        # pinned the behavior — regression guard so it can't drift silently.
        entry = self.manifest["effects"]["vfx_sandy_impact"]
        rock = next(e for e in entry["emitters"] if e["name"] == "rock")
        self.assertEqual(rock["texture"], "animations/vfx/vfx_sandy_impact/vfx_ash_rock.png")
        self.assertTrue((self.out / rock["texture"]).is_file())

    def test_block_spark_is_extracted_as_a_particle_composite(self):
        eff = self.manifest["effects"]["vfx_block_spark_vfx"]
        self.assertEqual(eff["kind"], "particles")
        # The scene's own emitters — a spark burst, not an empty shell.
        self.assertGreater(len(eff["emitters"]), 0)
        for em in eff["emitters"]:
            self.assertGreater(em["amount"], 0)

    def test_tumble_and_anim_speed_carry_the_scenes_authored_values(self) -> None:
        """The nine fields that were dropped until the pck came back.

        Real authored numbers, not round-trip identities: a re-extraction that
        silently stopped reading these would still produce a well-formed
        manifest full of Godot's zero defaults, which is exactly what shipped
        before and is indistinguishable from "this emitter authors none".
        """
        procs = [
            e["process"]
            for eff in self.manifest["effects"].values()
            if eff.get("kind") == "particles"
            for e in eff["emitters"]
        ]
        self.assertTrue(procs)
        # Every emitter carries all nine keys, default or not.
        for key in (
            "angleMin",
            "angleMax",
            "angleCurve",
            "angularVelocityMin",
            "angularVelocityMax",
            "angularVelocityCurve",
            "animSpeedMin",
            "animSpeedMax",
            "animSpeedCurve",
        ):
            for pr in procs:
                self.assertIn(key, pr)
        # ...and the values are the game's. `vfx_smoke_puff` authors a full
        # -180..180 spawn rotation on both of its emitters, which is the
        # clearest single witness that a real number survived the pipeline.
        smoke = [e["process"] for e in self.manifest["effects"]["vfx_smoke_puff"]["emitters"]]
        self.assertEqual([p["angleMin"] for p in smoke], [-180.0, -180.0])
        self.assertEqual([p["angleMax"] for p in smoke], [180.0, 180.0])
        # At least one emitter somewhere authors a real spin and a real anim
        # speed — otherwise this whole feature would be inert on the shipped set.
        self.assertTrue(any(p["angularVelocityMax"] != 0.0 for p in procs))
        self.assertTrue(any(p["animSpeedMax"] != 0.0 for p in procs))

    def test_emission_shapes_carry_the_scenes_authored_volumes(self) -> None:
        """The emission VOLUME — the other half of what needed the pck.

        Same failure mode as the tumble fields: with these ten keys missing,
        every emitter reads as `EMISSION_SHAPE_POINT` and the manifest stays
        well-formed while 29 emitters quietly lose their spawn volume.
        """
        procs = [
            e["process"]
            for eff in self.manifest["effects"].values()
            if eff.get("kind") == "particles"
            for e in eff["emitters"]
        ]
        self.assertTrue(procs)
        for key in (
            "emissionShape",
            "emissionSphereRadius",
            "emissionBoxExtents",
            "emissionRingAxis",
            "emissionRingHeight",
            "emissionRingRadius",
            "emissionRingInnerRadius",
            "emissionRingConeAngle",
            "emissionShapeOffset",
            "emissionShapeScale",
        ):
            for pr in procs:
                self.assertIn(key, pr)
        # Four distinct non-point shapes are in the shipped set. Reading the
        # enum but mis-mapping it (the CPU and GPU enums agree only on 0..2 —
        # see `particle_scene.py`'s divergence table) would still leave a
        # plausible-looking histogram, so pin the shapes to named emitters.
        by_name = {
            (name, em["name"]): em["process"]
            for name, eff in self.manifest["effects"].items()
            if eff.get("kind") == "particles"
            for em in eff["emitters"]
        }
        # Sphere, with a real 50px radius.
        fire = by_name[("vfx_fire_burst", "vfx_fire_burst_center_flipbook")]
        self.assertEqual(fire["emissionShape"], 1)
        self.assertEqual(fire["emissionSphereRadius"], 50.0)
        # Sphere Surface, squashed to a 2:1 ellipse by the trailing scale.
        drops = by_name[("vfx_poison_impact", "vfx_liquid_droplets")]
        self.assertEqual(drops["emissionShape"], 2)
        self.assertEqual(drops["emissionSphereRadius"], 120.0)
        self.assertEqual(drops["emissionShapeScale"], [1.0, 0.5, 1.0])
        # Box, and the extents are per-axis rather than a single number.
        smoke = by_name[("vfx_scratch_impact", "vfx_big_slash_impact_side_smoke")]
        self.assertEqual(smoke["emissionShape"], 3)
        self.assertEqual(smoke["emissionBoxExtents"], [10.0, 40.0, 1.0])
        # Ring, as an annulus: a nonzero inner radius is the field most easily
        # dropped, and dropping it fills the hole in.
        poof = by_name[("vfx_gaseous_impact", "vfx_poof")]
        self.assertEqual(poof["emissionShape"], 6)
        self.assertEqual(poof["emissionRingRadius"], 200.0)
        self.assertEqual(poof["emissionRingInnerRadius"], 150.0)
        self.assertEqual(poof["emissionShapeOffset"], [0.0, 25.0, 0.0])
        # `emission_shape_scale` is not decoration: this ring is authored at
        # Godot's default radius 1 and gets its actual 128px size from the
        # scale alone, so a dropped scale collapses it to a point.
        ember = by_name[("vfx_smoke_puff", "Ember")]
        self.assertEqual(ember["emissionRingRadius"], 1.0)
        self.assertEqual(ember["emissionShapeScale"], [128.0, 128.0, 1.0])

    def test_the_authored_curves_bake_to_luts(self) -> None:
        """Three scenes author a curve on one of these params; each must reach
        the manifest as a baked `vfx_particles/<hash>.json` path rather than
        being silently dropped to `null`."""
        procs = [
            e["process"]
            for eff in self.manifest["effects"].values()
            if eff.get("kind") == "particles"
            for e in eff["emitters"]
        ]
        curves = [
            p[k]
            for p in procs
            for k in ("angleCurve", "angularVelocityCurve", "animSpeedCurve")
            if p[k] is not None
        ]
        self.assertTrue(curves, "no emitter baked a tumble/anim-speed curve")
        for path in curves:
            self.assertTrue(path.startswith("vfx_particles/"), path)
            self.assertTrue((self.out / path).is_file(), path)


@unittest.skipUnless(PCK.is_file(), f"vendored pck not present at {PCK}")
class ExtractTransformAnimTest(unittest.TestCase):
    """Against the real pck: these numbers are the game's, not ours."""

    _tmp: tempfile.TemporaryDirectory[str]
    out: Path
    manifest: dict[str, Any]

    @classmethod
    def setUpClass(cls) -> None:
        cls._tmp = tempfile.TemporaryDirectory()
        cls.out = Path(cls._tmp.name)
        extract_vfx(
            str(PCK), cls.out, flipbooks=[], spines=[], transform_anims=TRANSFORM_ANIM_EFFECTS
        )
        cls.manifest = json.loads((cls.out / "vfx" / "manifest.json").read_text())

    @classmethod
    def tearDownClass(cls) -> None:
        cls._tmp.cleanup()

    def test_block_broken_carries_both_halves_and_their_tracks(self) -> None:
        eff = self.manifest["effects"]["vfx_block_broken"]
        self.assertEqual(eff["kind"], "transform_anim")
        names = {s["name"] for s in eff["sprites"]}
        self.assertEqual(names, {"BlockBreakLeft", "BlockBreakRight"})
        for s in eff["sprites"]:
            self.assertTrue(s["texture"].endswith(".png"))
            # Both halves are thrown AND spun -- a half with no rotation track
            # would sail off flat, which is not what the scene authors.
            self.assertIsNotNone(s["rotation"])

    def test_baked_luts_are_on_disk_and_256_samples(self) -> None:
        eff = self.manifest["effects"]["vfx_block_broken"]
        for s in eff["sprites"]:
            for key in ("positionX", "positionY", "rotation"):
                lut = s[key]
                self.assertIsNotNone(lut, key)
                path = self.out / lut
                self.assertTrue(path.is_file(), lut)
                self.assertEqual(len(json.loads(path.read_text())), 256)

    def test_each_sprite_carries_the_scene_nodes_own_authored_transform(self) -> None:
        # A channel with NO track holds the node's authored value for the
        # whole effect, and the renderer cannot recover that from a null LUT
        # path — so it travels in the manifest. Both halves author a
        # `position` in `vfx_block_broken.tscn` and neither authors a
        # `rotation`, which is Godot's own default of 0 (an absent property
        # is not "unknown", it is the default).
        rest = {
            s["name"]: (s["restPositionX"], s["restPositionY"], s["restRotation"])
            for s in self.manifest["effects"]["vfx_block_broken"]["sprites"]
        }
        self.assertEqual(rest["BlockBreakRight"], (1.0, -11.0, 0.0))
        self.assertEqual(rest["BlockBreakLeft"], (-1.0, -10.0, 0.0))

    def test_lifetime_matches_the_exposed_tracks_not_the_unrelated_fade(self) -> None:
        # The scene's `broken` animation also carries modulate:{r,g,b,a}
        # tracks that fade to invisible at 0.6s -- outside this schema. The
        # position/rotation tracks this manifest DOES expose all end at
        # 0.4s; `lifetime` must track those, not the longer unrelated fade.
        eff = self.manifest["effects"]["vfx_block_broken"]
        self.assertAlmostEqual(eff["lifetime"], 0.4)


class TransformAnimSpriteSpanTest(unittest.TestCase):
    """The serializer bakes every channel over the EFFECT's span.

    No pck: the effect that ships happens to have six tracks of identical
    length, so only a scene it does not have can distinguish a per-effect
    bake from a per-track one. Hand-built tracks supply that scene. See
    `anim_track.bake_track`'s `span` for what goes wrong without it —
    the renderer samples every LUT at `elapsed / lifetime`, so a short track
    baked over its own range plays in slow motion.
    """

    @staticmethod
    def _track(path: str, end_time: float, end_value: float) -> TrackSpec:
        # Flat handles, so the value climbs monotonically from key to key and
        # "has it arrived yet" is a meaningful question at any sample.
        return TrackSpec(
            path=path,
            kind="bezier",
            keys=[
                BezierKey(time=0.0, value=0.0, in_x=-0.05, in_y=0.0, out_x=0.05, out_y=0.0),
                BezierKey(
                    time=end_time, value=end_value, in_x=-0.05, in_y=0.0, out_x=0.05, out_y=0.0
                ),
            ],
        )

    def test_a_short_channel_finishes_early_instead_of_stretching(self) -> None:
        out = Path(tempfile.mkdtemp())
        try:
            written: list[Path] = []
            sprite = _serialize_transform_anim_sprite(
                "Half",
                "res://images/half.png",
                "position = Vector2(3, -4)\n",
                {
                    # Done at 0.2 — halfway through the effect.
                    "position:x": self._track("Half:position:x", 0.2, 10.0),
                    # Runs the whole 0.4.
                    "position:y": self._track("Half:position:y", 0.4, 10.0),
                },
                (0.0, 0.4),
                {},
                Path("unused.pck"),
                out,
                # Pre-seeded, so nothing here touches the pck to decode a texture.
                {"res://images/half.png": "vfx/half.png"},
                {},
                written,
            )
            short = json.loads((out / sprite["positionX"]).read_text())
            full = json.loads((out / sprite["positionY"]).read_text())
            # Index 127/128 of 256 is the effect's own midpoint, t = 0.2.
            self.assertAlmostEqual(short[128], 10.0, places=4)
            self.assertLess(full[128], 10.0 - 1e-4)
            # Both still end where their last key says.
            self.assertAlmostEqual(short[-1], 10.0, places=5)
            self.assertAlmostEqual(full[-1], 10.0, places=5)
        finally:
            shutil.rmtree(out)

    def test_an_untouched_channel_carries_the_nodes_authored_value(self) -> None:
        out = Path(tempfile.mkdtemp())
        try:
            sprite = _serialize_transform_anim_sprite(
                "Half",
                "res://images/half.png",
                "position = Vector2(3, -4)\nrotation = 1.25\n",
                {},  # the animation touches nothing on this node
                (0.0, 0.4),
                {},
                Path("unused.pck"),
                out,
                {"res://images/half.png": "vfx/half.png"},
                {},
                [],
            )
            self.assertIsNone(sprite["positionX"])
            self.assertIsNone(sprite["positionY"])
            self.assertIsNone(sprite["rotation"])
            self.assertEqual(
                (sprite["restPositionX"], sprite["restPositionY"], sprite["restRotation"]),
                (3.0, -4.0, 1.25),
            )
        finally:
            shutil.rmtree(out)

    def test_a_node_authoring_no_transform_rests_at_godots_own_defaults(self) -> None:
        out = Path(tempfile.mkdtemp())
        try:
            sprite = _serialize_transform_anim_sprite(
                "Half",
                "res://images/half.png",
                'texture = ExtResource("1")\n',
                {},
                (0.0, 0.4),
                {},
                Path("unused.pck"),
                out,
                {"res://images/half.png": "vfx/half.png"},
                {},
                [],
            )
            self.assertEqual(
                (sprite["restPositionX"], sprite["restPositionY"], sprite["restRotation"]),
                (0.0, 0.0, 0.0),
            )
        finally:
            shutil.rmtree(out)


@unittest.skipUnless(PCK.is_file(), f"vendored pck not present at {PCK}")
class DroppedParameterCountsTest(unittest.TestCase):
    """`_CPU_PARAMS_NOT_MODELLED`'s counts are the evidence for NOT widening
    `ProcessSpec` — "18 emitters author `angle_min` and we drop it" is the
    whole argument. A hand-kept number rots silently and the argument rots
    with it, so re-derive them from the pck and compare.

    Lives here rather than in `test_particle_scene.py` because it needs the
    real pck; that module is pck-free by design.
    """

    #: The resources the counts cover: the 14 in-scope particle composites,
    #: `vfx_monster_death`, and every scene/`.tres` they instance, walked
    #: transitively. Stated here because a count means nothing without it.
    counts: "Counter[str]"
    resource_count: int

    @classmethod
    def setUpClass(cls) -> None:
        from scripts.extract_rig import read_pck_index

        index = read_pck_index(str(PCK))
        ext = re.compile(r'^\[ext_resource\s+[^\]]*path="res://(?P<p>[^"]+)"', re.MULTILINE)
        section_body = re.compile(r"^\[(?:node|sub_resource)\b[^\]]*\]", re.MULTILINE)
        any_header = re.compile(r"^\[", re.MULTILINE)

        def read(res: str) -> str:
            offset, size = index[res]
            with open(PCK, "rb") as f:
                f.seek(offset)
                return f.read(size).decode("utf-8", "replace")

        counts: Counter[str] = Counter()
        seen: set[str] = set()
        queue = [path for _, path in PARTICLE_EFFECTS + DISSOLVE_EFFECTS]
        while queue:
            res = queue.pop()
            if res in seen or res not in index:
                continue
            seen.add(res)
            text = read(res)
            for m in section_body.finditer(text):
                nxt = any_header.search(text, m.end())
                body = text[m.end() : (nxt.start() if nxt else len(text))]
                counts.update(
                    line.split("=", 1)[0].strip()
                    for line in body.splitlines()
                    if "=" in line and not line[:1].isspace()
                )
            for m in ext.finditer(text):
                if m.group("p").endswith((".tscn", ".tres")):
                    queue.append(m.group("p"))
        cls.counts = counts
        cls.resource_count = len(seen)

    def test_the_surveyed_resource_set_is_the_one_the_record_claims(self) -> None:
        # The record's header says "61 resources in total". If that walk ever
        # changes shape, every count below is measuring a different thing.
        self.assertEqual(self.resource_count, 61)

    def test_every_recorded_count_matches_a_fresh_survey(self) -> None:
        stale = {
            name: (recorded, self.counts.get(name, 0))
            for name, recorded in _CPU_PARAMS_NOT_MODELLED.items()
            if recorded != self.counts.get(name, 0)
        }
        self.assertEqual(stale, {}, "recorded (expected, actual) counts diverged")

    def test_the_record_is_not_quietly_all_zeroes(self) -> None:
        # A survey bug that returned nothing would make the test above pass
        # only if the record were also all zeroes -- but a future edit could
        # zero the record to "fix" a failure. Pin that the gap is real.
        self.assertGreater(sum(_CPU_PARAMS_NOT_MODELLED.values()), 50)


class BlendModeTest(unittest.TestCase):
    """`blend_mode` is pure, so it is tested without the pck."""

    def test_the_declared_blend_mode_wins(self) -> None:
        src = "shader_type canvas_item;\nrender_mode blend_add;\n\nvoid fragment() {}\n"
        self.assertEqual(blend_mode(src), "blend_add")

    def test_premul_alpha_is_not_truncated_to_a_prefix(self) -> None:
        # `blend_premul_alpha` and `blend_add` must not both collapse to
        # "blend" -- the renderer composites them differently.
        src = "shader_type canvas_item;\nrender_mode blend_premul_alpha;\n"
        self.assertEqual(blend_mode(src), "blend_premul_alpha")

    def test_a_shader_declaring_no_render_mode_gets_godots_default(self) -> None:
        self.assertEqual(blend_mode("shader_type canvas_item;\nvoid fragment() {}\n"), "blend_mix")


class EffectKeyNamingTest(unittest.TestCase):
    """Every extracted effect key starts with `vfx_`, matching the effects-map
    convention `test_effect_keys_use_one_naming_convention` enforces on the
    binding table.

    The two sub-project-3 additions are the reason this is pinned here too:
    the Godot scenes are `power_flash_vfx.tscn` and `block_spark_vfx.tscn` --
    `_vfx`-SUFFIXED, unprefixed -- and the convention is to keep the verbatim
    game name and PREPEND `vfx_`, never to invent a tidier rename. Three
    shipped keys already read that way (`vfx_spike_splash_vfx`,
    `vfx_stab_vfx`, `vfx_kin_priest_grenade_vfx`).
    """

    def test_every_declared_effect_key_uses_the_vfx_prefix(self) -> None:
        names = (
            [n for n, _ in FLIPBOOK_EFFECTS]
            + [n for n, _, _ in SPINE_EFFECTS]
            + [n for n, _ in PARTICLE_EFFECTS]
            + [n for n, _ in TRANSFORM_ANIM_EFFECTS]
            + [n for n, _ in DISSOLVE_EFFECTS]
            + [n for n, _ in SPRITE_SHADER_EFFECTS]
        )
        self.assertGreater(len(names), 0)
        for name in names:
            with self.subTest(effect=name):
                self.assertTrue(name.startswith("vfx_"), name)

    def test_the_power_flash_key_keeps_the_games_own_scene_name(self) -> None:
        # Not `vfx_power_flash`: the pck names the scene
        # `scenes/vfx/power_flash_vfx.tscn`.
        self.assertEqual(
            SPRITE_SHADER_EFFECTS,
            [("vfx_power_flash_vfx", "scenes/vfx/power_flash_vfx.tscn")],
        )


@unittest.skipUnless(PCK.is_file(), f"vendored pck not present at {PCK}")
class ExtractDissolveAndSpriteShaderTest(unittest.TestCase):
    """Against the real pck: these numbers are the game's, not ours."""

    _tmp: tempfile.TemporaryDirectory[str]
    out: Path
    manifest: dict[str, Any]

    @classmethod
    def setUpClass(cls) -> None:
        cls._tmp = tempfile.TemporaryDirectory()
        cls.out = Path(cls._tmp.name)
        extract_vfx(
            str(PCK),
            cls.out,
            flipbooks=[],
            spines=[],
            dissolves=DISSOLVE_EFFECTS,
            sprite_shaders=SPRITE_SHADER_EFFECTS,
        )
        cls.manifest = json.loads((cls.out / "vfx" / "manifest.json").read_text())

    @classmethod
    def tearDownClass(cls) -> None:
        cls._tmp.cleanup()

    def _png_size(self, rel: str) -> tuple[int, int]:
        from PIL import Image

        with Image.open(self.out / rel) as im:
            return im.size

    def test_monster_death_bakes_both_noise_gradients_at_a_power_of_two(self) -> None:
        eff = self.manifest["effects"]["vfx_monster_death"]
        self.assertEqual(eff["kind"], "dissolve")
        for key in ("gradient1", "gradient2"):
            with self.subTest(gradient=key):
                # NoiseTexture2D's own default size, AND a power of two -- WebGL1
                # cannot REPEAT-wrap an NPOT texture and the shader declares
                # `repeat_enable`, so an NPOT bake would silently clamp.
                self.assertEqual(self._png_size(eff[key]), (512, 512))

    def test_the_two_gradients_are_the_scenes_two_different_frequencies(self) -> None:
        # `vfx_monster_death.tscn` authors 0.0234 and 0.0084; the shader mixes
        # the two fields. Baking one frequency twice would still produce two
        # 512x512 files and pass the size test above, so compare the BYTES.
        eff = self.manifest["effects"]["vfx_monster_death"]
        first = (self.out / eff["gradient1"]).read_bytes()
        second = (self.out / eff["gradient2"]).read_bytes()
        self.assertNotEqual(first, second)

    def test_monster_death_carries_the_scenes_own_tween_and_alpha(self) -> None:
        eff = self.manifest["effects"]["vfx_monster_death"]
        # A CEILING, not a constant: `NMonsterDeathVfx.cs:235` computes
        # `Math.Min(node.Size.X / (0.1 * viewportWidth) * 2.5, 2.5)`, so a
        # creature smaller than ~5% of screen width dissolves faster.
        self.assertAlmostEqual(eff["dissolveSeconds"], 2.5)
        self.assertAlmostEqual(eff["modulateAlpha"], 0.466667, places=5)

    def test_monster_death_records_the_dissolve_tweens_direction_and_easing(self) -> None:
        # `NMonsterDeathVfx.cs:239` tweens `shader_parameter/threshold` to 0
        # with `.SetEase((EaseType)1).SetTrans((TransitionType)1)` --
        # EASE_OUT + TRANS_SINE. The start is the scene's authored 1.0. A
        # linear ramp, or a run in the wrong direction, is a visibly different
        # dissolve.
        eff = self.manifest["effects"]["vfx_monster_death"]
        self.assertAlmostEqual(eff["thresholdFrom"], 1.0)
        self.assertAlmostEqual(eff["thresholdTo"], 0.0)
        self.assertEqual(eff["dissolveEase"], 1)
        self.assertEqual(eff["dissolveTrans"], 1)

    def test_the_dissolve_lifetime_is_the_TWEENS_not_the_debris(self) -> None:
        # `PlayVfxInternal` awaits the threshold tween and only then frees the
        # node (`NMonsterDeathVfx.cs:240-241`), so the effect's on-screen life
        # IS the dissolve's. The debris emitter's 1.8s is a per-particle
        # lifespan that expires first and does not end the fade -- the two are
        # easy to confuse, which is why this pins which one `lifetime` means.
        eff = self.manifest["effects"]["vfx_monster_death"]
        self.assertAlmostEqual(eff["lifetime"], eff["dissolveSeconds"])
        self.assertNotAlmostEqual(eff["lifetime"], eff["emitters"][0]["lifetime"])

    def test_the_debris_emitter_is_flagged_as_a_CPUParticles2D(self) -> None:
        # `EmitterSpec.cpu` is not provenance trivia -- the renderer branches
        # on it, because `CPUParticles2D` computes a DIFFERENT emission volume
        # from the same property names (two draws of a flat, radius-uniform
        # disc against the shader's three draws of a 3D ball). An unflagged
        # debris burst would silently run the GPU formula.
        eff = self.manifest["effects"]["vfx_monster_death"]
        self.assertTrue(eff["emitters"][0]["cpu"])
        # ...and it is the only one. Every `NVfxParticleSystem` composite is
        # `GPUParticles2D`, so a `True` anywhere else means the CPU parser ran
        # on a node it should not have.
        gpu = [
            (name, em["name"])
            for name, entry in self.manifest["effects"].items()
            if entry.get("kind") == "particles"
            for em in entry["emitters"]
            if em["cpu"]
        ]
        self.assertEqual(gpu, [])

    def test_the_scenes_placeholder_emission_radius_is_never_baked(self) -> None:
        # `NMonsterDeathVfx.cs:231` overwrites `EmissionSphereRadius` with
        # `node.Size.Y / 4f` before the burst emits, and the SubViewport's
        # size comes from the creature's measured bounds (:204) -- so the
        # scene's 99.23 (like its `size = Vector2i(2, 2)`) is an authoring
        # placeholder. Baking it would pin every creature to one wrong radius.
        eff = self.manifest["effects"]["vfx_monster_death"]
        blob = json.dumps(eff)
        self.assertNotIn("99.23", blob)
        # Emission shapes ARE now read generically for every emitter, so the
        # placeholder no longer stays out by accident -- `extract_vfx` blanks
        # it on purpose, and a radius-0 sphere collapses to the origin, which
        # is a visible failure rather than a plausible wrong number.
        debris = eff["emitters"][0]["process"]
        self.assertEqual(debris["emissionShape"], 1)
        self.assertEqual(debris["emissionSphereRadius"], 0.0)
        # What ships instead is the runtime RULE: radius = (2n)/4 = n/2, half
        # the creature's larger on-screen extent.
        self.assertAlmostEqual(eff["emissionRadiusPerCreatureExtent"], 0.5)

    def test_the_debris_burst_reaches_the_manifest_as_an_ordinary_emitter(self) -> None:
        # The CPUParticles2D that `particle_scene.py` used to refuse outright.
        eff = self.manifest["effects"]["vfx_monster_death"]
        self.assertEqual(len(eff["emitters"]), 1)
        debris = eff["emitters"][0]
        self.assertEqual(debris["amount"], 500)
        self.assertAlmostEqual(debris["lifetime"], 1.8)
        self.assertTrue(debris["oneShot"])
        self.assertAlmostEqual(debris["explosiveness"], 0.08)
        self.assertTrue((self.out / debris["texture"]).is_file(), debris["texture"])

    def test_the_modulate_alpha_covers_the_debris_too_not_just_the_sprite(self) -> None:
        # `modulateAlpha` is the `Visual` sprite's authored
        # `modulate = Color(1, 1, 1, 0.466667)`. Godot's `CanvasItem.modulate`
        # propagates to children (`self_modulate` is the one that does not),
        # and the debris `CPUParticles2D` is declared `parent="Visual"` — so
        # the game draws the burst at that alpha too, and a renderer applying
        # it to the sprite alone shows the debris ~2x too bright.
        #
        # This pins the two halves of that contract: the parent/child edge the
        # claim rests on (re-derived from the scene, not assumed), and the fact
        # that the product is NOT baked into the emitter's `selfModulate`.
        from scripts.particle_scene import _iter_nodes

        scene = _read_pck_text(PCK, "scenes/vfx/vfx_monster_death.tscn")
        nodes = {n.name: n for n in _iter_nodes(scene)}
        self.assertEqual(nodes["Particles"].type, "CPUParticles2D")
        self.assertEqual(nodes["Particles"].parent, "Visual")
        self.assertIn("modulate = Color(1, 1, 1, 0.466667)", nodes["Visual"].section)

        eff = self.manifest["effects"]["vfx_monster_death"]
        self.assertAlmostEqual(eff["modulateAlpha"], 0.466667, places=5)
        # Extracted verbatim from the node. Baking `modulateAlpha` in here
        # would make `selfModulate` a lie about what the scene says.
        self.assertEqual(eff["emitters"][0]["selfModulate"], [1.0, 1.0, 1.0, 1.0])

    def test_the_debris_process_params_come_off_the_node_not_a_material(self) -> None:
        # A CPUParticles2D carries the ParticleProcessMaterial parameter set as
        # node properties, under partly different names
        # (`scale_amount_min`/`_max`/`_curve`) and as Vector2 rather than
        # Vector3. Reading them with the GPU spellings yields Godot defaults
        # everywhere -- which looks like a working extraction.
        process = self.manifest["effects"]["vfx_monster_death"]["emitters"][0]["process"]
        self.assertEqual(process["direction"], [1.0, -0.5, 0.0])
        self.assertEqual(process["gravity"], [0.0, -100.0, 0.0])
        self.assertAlmostEqual(process["spread"], 30.0)
        self.assertAlmostEqual(process["initialVelocityMin"], 100.0)
        self.assertAlmostEqual(process["initialVelocityMax"], 150.0)
        self.assertAlmostEqual(process["scaleMin"], 0.05)
        self.assertAlmostEqual(process["scaleMax"], 0.07)
        self.assertAlmostEqual(process["lifetimeRandomness"], 0.75)
        self.assertAlmostEqual(process["animOffsetMax"], 1.0)

    def test_the_debris_scale_curve_is_baked_from_a_direct_curve_subresource(self) -> None:
        # `scale_amount_curve = SubResource("Curve_6prs8")` names the Curve
        # ITSELF -- no CurveTexture wrapper, unlike every GPU emitter. Going
        # through `_curve_field` yields null, which reads as "no curve
        # authored" rather than as a parse failure.
        process = self.manifest["effects"]["vfx_monster_death"]["emitters"][0]["process"]
        lut = process["scaleCurve"]
        self.assertIsNotNone(lut)
        self.assertTrue(lut.startswith("vfx_particles/"), lut)
        samples = json.loads((self.out / lut).read_text())
        self.assertEqual(len(samples), 256)
        # The scene's curve rises from 0 and falls back toward 0.2 -- not flat.
        self.assertGreater(max(samples), min(samples))

    def test_power_flash_takes_the_scenes_strength_not_the_shaders_default(self) -> None:
        eff = self.manifest["effects"]["vfx_power_flash_vfx"]
        self.assertEqual(eff["kind"], "sprite_shader")
        # The shader declares `strength = 3.0`; the SCENE overrides it to 1.0,
        # and the scene is what ships.
        self.assertAlmostEqual(eff["strength"], 1.0)
        # The scene's `scale = Vector2(0.4, 0.4)` IS live -- it is the sprite's
        # starting scale, which the tween then drives.
        self.assertAlmostEqual(eff["scale"], 0.4)

    def test_power_flash_alpha_is_the_scripts_white_not_the_scenes_dead_value(self) -> None:
        # `power_flash_vfx.tscn` authors `modulate = Color(1, 1, 1, 0.65098)`,
        # but `NPowerFlashVfx.StartVfx` sets `_sprite.Modulate = Colors.White`
        # at :95 -- BEFORE the method's first `await`, so no rendered frame
        # ever shows 0.65098. Shipping the scene value would make the
        # workbench's flash dimmer than the game's.
        eff = self.manifest["effects"]["vfx_power_flash_vfx"]
        self.assertAlmostEqual(eff["modulateAlpha"], 1.0)

    def test_power_flash_carries_the_scripts_two_phase_tween(self) -> None:
        # What actually animates the flash. Outer list = sequential steps,
        # inner = parallel within a step (`SetParallel(true)` at :97 covers
        # only the first pair; :100 turns it back off).
        eff = self.manifest["effects"]["vfx_power_flash_vfx"]
        tween = eff["tween"]
        self.assertEqual([len(step) for step in tween], [2, 1, 1])

        scale_up, fade_in = tween[0]
        self.assertEqual(
            scale_up, {"property": "scale", "to": 0.4, "duration": 0.4, "ease": None, "trans": None}
        )
        self.assertEqual(
            fade_in,
            {"property": "modulateAlpha", "to": 1.0, "duration": 0.25, "ease": 1, "trans": 1},
        )
        self.assertEqual(
            tween[1][0],
            {"property": "scale", "to": 0.45, "duration": 0.4, "ease": None, "trans": None},
        )
        self.assertEqual(
            tween[2][0],
            {"property": "modulateAlpha", "to": 0.0, "duration": 0.25, "ease": 0, "trans": 1},
        )
        # Steps run in sequence, so the effect lasts 0.4 + 0.4 + 0.25.
        self.assertAlmostEqual(eff["lifetime"], 1.05)

    def test_both_effects_carry_their_shaders_own_blend_mode(self) -> None:
        # `power_flash_vfx.gdshader` declares `render_mode blend_add` and
        # `dissolve.gdshader` `blend_premul_alpha`. Composited with the usual
        # alpha mix, an additive flash draws dark instead of glowing.
        effects = self.manifest["effects"]
        self.assertEqual(effects["vfx_power_flash_vfx"]["blendMode"], "blend_add")
        self.assertEqual(effects["vfx_power_flash_vfx"]["shader"], "power_flash_vfx")
        self.assertEqual(effects["vfx_monster_death"]["blendMode"], "blend_premul_alpha")
        self.assertEqual(effects["vfx_monster_death"]["shader"], "dissolve")


@unittest.skipUnless(PCK.is_file(), f"vendored pck not present at {PCK}")
@unittest.skipUnless(
    COMMITTED_IMAGES.is_dir(), f"committed image tree not present at {COMMITTED_IMAGES}"
)
class CommittedArtifactGateTest(unittest.TestCase):
    """The committed artifact must equal a fresh extraction.

    `extract_vfx_bindings` and `power_flash_sites` have carried this gate
    since they shipped (`test_extract_vfx_bindings.ReExtractionGateTest`
    and `test_power_flash_sites.CommittedTableTest`, both of which re-run
    their generator and compare). `extract_vfx` did not, and it went stale under
    it: the task that taught this extractor to emit `locStrings` — and
    taught the client to read them — never re-ran `just extract-vfx`, so the
    shipped manifest had no `locStrings` key at all and every render took the
    hardcoded `?? "Blocked"` fallback instead. The `rev` was wrong with it:
    it is a content hash that includes the loc table, so the `?v=` bust that
    the art bucket's `immutable` URLs depend on was computed for a manifest
    the repo no longer had.

    Nothing else caught that. Every OTHER case in this file extracts into a
    temp tree and asserts against the temp tree — none of them ever opens
    the committed file, so a generator change that nobody re-ran was
    invisible to the whole suite.

    Deliberately two assertions, because the manifest is not the only thing
    the extractor writes: the frames, rigs, particle scenes and baked LUTs
    are committed artifacts too and can go stale exactly the same way.
    """

    _tmp: tempfile.TemporaryDirectory[str]
    out: Path
    fresh: dict[str, Any]

    @classmethod
    def setUpClass(cls) -> None:
        cls._tmp = tempfile.TemporaryDirectory()
        cls.out = Path(cls._tmp.name)
        # Drive the CLI's own `main()` rather than re-listing `extract_vfx`'s
        # keyword arguments here. `just extract-vfx` runs exactly this, and a
        # future effect family added to `main()` but not to a hand-copied
        # argument list would leave this gate silently checking less than the
        # recipe produces.
        argv = ["extract_vfx", str(PCK), str(cls.out)]
        with contextlib.redirect_stdout(io.StringIO()):
            with unittest.mock.patch.object(sys, "argv", argv):
                extract_vfx_main()
        cls.fresh = json.loads((cls.out / "vfx" / "manifest.json").read_text())

    @classmethod
    def tearDownClass(cls) -> None:
        cls._tmp.cleanup()

    def test_the_committed_manifest_matches_a_fresh_extraction(self) -> None:
        path = COMMITTED_IMAGES / "vfx" / "manifest.json"
        self.assertTrue(path.is_file(), f"missing committed manifest: {path}")
        committed = json.loads(path.read_text())
        # Report the shape before the contents: a missing top-level key is
        # the failure this gate exists for, and diffing two 29k-line effect
        # maps to discover it is not a readable error.
        self.assertEqual(
            sorted(committed),
            sorted(self.fresh),
            "committed manifest's top-level keys differ from a fresh extraction — "
            "re-run `just extract-vfx` and commit the result",
        )
        for key in sorted(self.fresh):
            if key == "effects":
                continue
            self.assertEqual(committed[key], self.fresh[key], f"manifest[{key!r}] is stale")
        stale = sorted(
            name
            for name in set(self.fresh["effects"]) | set(committed["effects"])
            if self.fresh["effects"].get(name) != committed["effects"].get(name)
        )
        self.assertEqual(stale, [], "these effect entries are stale")

    def test_every_file_the_extractor_writes_is_committed_and_identical(self) -> None:
        missing: list[str] = []
        differing: list[str] = []
        for src in sorted(p for p in self.out.rglob("*") if p.is_file()):
            rel = src.relative_to(self.out)
            if rel.as_posix() == "vfx/manifest.json":
                continue  # covered above, with a readable diff
            dst = COMMITTED_IMAGES / rel
            if not dst.is_file():
                missing.append(rel.as_posix())
            elif dst.read_bytes() != src.read_bytes():
                differing.append(rel.as_posix())
        self.assertEqual(missing, [], "the extractor writes these, but they are not committed")
        self.assertEqual(differing, [], "these committed files differ from a fresh extraction")


if __name__ == "__main__":
    unittest.main()
