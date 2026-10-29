#!/usr/bin/env python3
"""Extract the game's renderable VFX from the PCK for the workbench.

Six output shapes, one manifest:

* **Flipbooks** — `images/vfx/<effect>/<frame>.png`, decoded from the pck's
  `.godot/imported/*.ctex` via the same decoder the wiki's image pipeline uses.
* **Spine VFX** — `images/vfx_rigs/<effect>/<effect>.{skel,atlas,png}`, via
  `extract_rig`.
* **Particles** — `NVfxParticleSystem`/`N*ImpactVfx` composites, parsed by
  `particle_scene.parse_particle_scene`. Their textures land under
  `images/<source-relative path with a leading images/ stripped>` (so a
  texture shared by several composites is decoded and written once); their
  `Curve`/`Gradient` process-material fields bake through
  `godot_curve.bake_curve`/`bake_gradient` to content-addressed
  `images/vfx_particles/<sha256-16>.json` LUT files — a deliberate deviation
  from the design spec's 256x1 PNG default (see `_write_lut`'s docstring for
  why).
* **Transform-animated sprites** — a plain `AnimationPlayer` scene whose
  `bezier` tracks throw/spin one or more child sprites (`vfx_block_broken`),
  parsed by `anim_track.parse_animation` and baked the same way particle
  curves are, to 256-sample LUTs. Distinct from a particle composite: there
  is no emitter, just sprites moved by baked tracks.
* **Dissolves** — a `SubViewport` snapshot of the dying creature drawn
  through `shaders/dissolve.gdshader`, whose `threshold` uniform is tweened
  1 -> 0, plus a `CPUParticles2D` debris burst. The two noise fields the
  shader reads are procedural (`FastNoiseLite`), not stored art, so they are
  BAKED here by `godot_noise.bake_noise_png` rather than decoded from a
  `.ctex`. `vfx_monster_death` is the only member.
* **Sprite shaders** — a single `Sprite2D` under a shader, animated entirely
  by a `Tween` the C# script builds, with nothing in the scene describing the
  motion (`vfx_power_flash_vfx`). Distinct from a flipbook: no frames, one
  runtime-assigned texture (the power's `BigIcon`).

`manifest.json` sits beside them and carries the AUTHORED parameters — frame
order, fps, scale, rotation jitter, baked LUT paths — so the renderer
reproduces the game's motion instead of approximating it, and so adding an
effect later is a data change rather than a code change.

The manifest's ``rev`` is a CONTENT hash of what was written (see
:func:`content_rev`), so a re-extract that changes a frame moves it and one
that changes nothing does not.

Usage:
    uv run python -m scripts.extract_vfx <pck> site/public/images [--rev REV]
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections.abc import Callable
from dataclasses import fields as dc_fields
from pathlib import Path
from typing import Any

from scripts.anim_track import TrackSpec, bake_track, parse_animation
from scripts.extract_images import decode_ctex
from scripts.extract_rig import extract_rig, read_pck_index
from scripts.godot_curve import Curve, Gradient, bake_curve, bake_gradient
from scripts.godot_noise import bake_noise_png

# The leading-underscore names are `particle_scene`'s `.tscn` section/attribute
# readers, reused rather than re-implemented — the same precedent as the
# `vfx_scene` import block below, which already pulls that module's regex
# idioms across. One parser for a given `.tscn` shape, not three that drift.
from scripts.particle_scene import (
    CompositeSpec,
    EmitterSpec,
    ProcessSpec,
    _color,
    _ext_ref_path,
    _int,
    _iter_nodes,
    _NodeInfo,
    _or,
    _parse_resource_tables,
    _resource_ref_field,
    _scalar,
    _sub_ref_id,
    _vector2,
    parse_emitters,
    parse_material,
    parse_particle_scene,
)
from scripts.vfx_scene import (
    _ATTR_ID,
    _ATTR_PATH,
    _ATTR_TYPE,
    _EXT_RESOURCE_LINE,
    _NODE_HEADER,
    _SECTION_HEADER,
    FlipbookSpec,
    SpineVfxSpec,
    parse_vfx_scene,
)

#: Effects whose scenes are `NSpriteAnimator` flipbooks, as
#: `(effect name, scene res-path)`. Sub-project 1 scope.
FLIPBOOK_EFFECTS: list[tuple[str, str]] = [
    ("vfx_attack_slash", "scenes/vfx/vfx_attack_slash.tscn"),
    ("vfx_block", "scenes/vfx/vfx_block.tscn"),
]

#: Effects whose scenes are Spine rigs, as
#: `(effect name, scene res-path, source rig name)`. The source name is the
#: stem the pck's `.godot/imported/` entries use, which is NOT always the
#: effect name (`vfx_bite`'s rig files are `attackbite.*`).
SPINE_EFFECTS: list[tuple[str, str, str]] = [
    ("vfx_bite", "scenes/vfx/vfx_bite.tscn", "attackbite"),
    ("vfx_chain", "scenes/vfx/vfx_chain.tscn", "chain"),
    ("vfx_flying_slash", "scenes/vfx/vfx_flying_slash.tscn", "flying_slash"),
    ("vfx_gaze", "scenes/vfx/vfx_gaze.tscn", "gaze"),
    ("vfx_scratch", "scenes/vfx/vfx_scratch.tscn", "scratch"),
    # Extracted but NOT wired in sub-project 1: `vfx_adrenaline` is played by
    # `VfxCmd.PlayFullScreenInCombat`, not on a creature, so it belongs with
    # the screen-level effects in sub-project 5.
    ("vfx_adrenaline", "scenes/vfx/vfx_adrenaline.tscn", "adrenaline"),
    # Sub-project 3 (GH #642): the Crusher's death explosion, spawned by
    # `NKaiserCrabBossVfx.OnClawLExplode` (line 387) at the crab rig's
    # `claw_explode_l` spine event — t=1.53s of `left/die`.
    #
    # This scene is a `SpineSprite` child of the root at scale 0.25, exactly
    # the shape `vfx_scene.parse_vfx_scene` already reads, so the rig half of
    # the effect needs no new machinery.
    #
    # It is NOT the whole effect. The scene also parents a `GPUParticles2D`
    # ("ExplosionParticles", 20 additive trailed glow dots) to a
    # `SpineSlotNode` on the rig's own `explosion_slot`, restarted by the
    # effect rig's `left_embers_start` event at t=0.100s of `animation`.
    # A particle emitter anchored to a SPINE SLOT is a shape this manifest has
    # no member for — `SPINE_EFFECTS` are pure rigs and `PARTICLE_EFFECTS` are
    # pure emitters, and nothing composes the two — so the ember burst is
    # deliberately left for GH #669 rather than half-modelled here. What ships
    # is the explosion art itself, which is the bulk of the effect.
    #
    # The third element is the pck SKELETON ASSET name, not the clip: the clip
    # is parsed out of the scene (`SpineVfxSpec.clip`). This rig's asset name
    # does not match its effect key — `infested_boss_explosion`, the crab's
    # internal name (`KaiserCrabBoss`'s creature visual is `InfestedBoss`).
    (
        "vfx_kaiser_crab_boss_explosion",
        "scenes/vfx/monsters/kaiser_crab_boss_explosion.tscn",
        "infested_boss_explosion",
    ),
]

#: Per-effect facts about a Spine effect's slot-parented emitters that live in
#: the effect's own C# script rather than in its `.tscn` (GH #669), keyed by
#: `(effect name, emitter node name)`.
#:
#: A `SpineSlotNode`-parented `GPUParticles2D` is not restarted by an
#: `NVfxParticleSystem` — it has a bespoke script listening on the effect rig's
#: OWN animation events. `kaiser_crab_boss_explosion.tscn`'s
#: `NKaiserCrabBossExplosionVfx._Ready` grabs
#: `ExplosionSlot/ExplosionParticles`, sets `Emitting = false` and
#: `OneShot = true`, and `OnAnimationEvent` calls `Restart()` when the rig
#: raises `left_embers_start` (t=0.100s of the 0.67s `animation` clip).
#:
#: `one_shot` is here and not read off the scene because the scene does not
#: author it — `_Ready` does, at runtime. Taking the `.tscn` default (`false`)
#: would loop a burst the game fires exactly once.
SPINE_SLOT_EMITTER_SCRIPT_FACTS: dict[tuple[str, str], dict[str, Any]] = {
    ("vfx_kaiser_crab_boss_explosion", "ExplosionParticles"): {
        "event": "left_embers_start",
        "one_shot": True,
    },
}

#: Effects that are `NVfxParticleSystem` / `N*ImpactVfx` particle composites,
#: as `(effect name, scene res-path)`. Sub-project 2 scope; `vfx_starry_impact`
#: and `vfx_heavy_blunt` are deliberately ABSENT — both need `BackBufferCopy`
#: screen distortion and belong to sub-project 5.
PARTICLE_EFFECTS: list[tuple[str, str]] = [
    ("vfx_attack_blunt", "scenes/vfx/vfx_attack_blunt.tscn"),
    ("vfx_poison_impact", "scenes/vfx/vfx_poison_impact.tscn"),
    ("vfx_spore_impact", "scenes/vfx/vfx_spore_impact.tscn"),
    ("vfx_gaseous_impact", "scenes/vfx/vfx_gaseous_impact.tscn"),
    ("vfx_goopy_impact", "scenes/vfx/vfx_goopy_impact.tscn"),
    ("vfx_wormy_impact", "scenes/vfx/vfx_wormy_impact.tscn"),
    ("vfx_slime_impact", "scenes/vfx/vfx_slime_impact.tscn"),
    ("vfx_sandy_impact", "scenes/vfx/vfx_sandy_impact.tscn"),
    ("vfx_bloody_impact", "scenes/vfx/vfx_bloody_impact.tscn"),
    ("vfx_scratch_impact", "scenes/vfx/vfx_scratch_impact.tscn"),
    ("vfx_smoke_puff", "scenes/vfx/vfx_smoke_puff.tscn"),
    ("vfx_coin_explosion_regular", "scenes/vfx/vfx_coin_explosion_regular.tscn"),
    ("vfx_fire_burst", "scenes/vfx/vfx_fire_burst.tscn"),
    # Sub-project 3: the spark that fires WITH the "Blocked" text when a hit
    # is fully absorbed (`CreatureCmd.cs:404`). An ordinary GPUParticles2D
    # composite — no new machinery.
    #
    # The doubled `vfx`: the Godot scene is `block_spark_vfx.tscn`, and the
    # effect-key convention (`test_effect_keys_use_one_naming_convention`) is
    # to keep the verbatim game name and PREFIX `vfx_` when it is missing,
    # never to fabricate a tidier rename. Three effects already read this way
    # (`vfx_spike_splash_vfx`, `vfx_stab_vfx`, `vfx_kin_priest_grenade_vfx`).
    ("vfx_block_spark_vfx", "scenes/vfx/block_spark_vfx.tscn"),
]

#: Effects driven by baked `AnimationPlayer` bezier transform tracks rather
#: than a particle system, as `(effect name, scene res-path)`. Sub-project 3
#: scope: `vfx_block_broken`'s two sprite halves are thrown apart on
#: position and spun on rotation, not emitted/particled.
TRANSFORM_ANIM_EFFECTS: list[tuple[str, str]] = [
    ("vfx_block_broken", "scenes/vfx/vfx_block_broken.tscn"),
]

#: Effects whose scene is a `SubViewport` snapshot of the creature dissolved
#: by `shaders/dissolve.gdshader`, plus a debris burst, as
#: `(effect name, scene res-path)`. Sub-project 3 scope; the one member is the
#: monster death fade.
DISSOLVE_EFFECTS: list[tuple[str, str]] = [
    ("vfx_monster_death", "scenes/vfx/vfx_monster_death.tscn"),
]

#: Effects that are a single `Sprite2D` under a shader, animated entirely by a
#: `Tween` the C# script builds, as `(effect name, scene res-path)`.
#: Sub-project 3 scope.
#:
#: The doubled `vfx`, as with `vfx_block_spark_vfx` above: the Godot scene is
#: `power_flash_vfx.tscn`, and the effect-key convention
#: (`test_effect_keys_use_one_naming_convention`) keeps the verbatim game name
#: and PREFIXES `vfx_` when it is missing rather than fabricating a tidier
#: rename. Three shipped keys already read this way (`vfx_spike_splash_vfx`,
#: `vfx_stab_vfx`, `vfx_kin_priest_grenade_vfx`).
SPRITE_SHADER_EFFECTS: list[tuple[str, str]] = [
    ("vfx_power_flash_vfx", "scenes/vfx/power_flash_vfx.tscn"),
]

#: English UI text three DOM-rendered (not canvas) combat VFX show, as
#: `(manifest key, loc table, loc row)`. These panels — the "Blocked" pop
#: (`vfx_blocked_text.tscn`) and the "Wears Off" label
#: (`power_removed_vfx.tscn`) — have no art of their own (`Combatant.tsx`
#: renders them as plain HTML), so they are not one of the six shapes above;
#: they still belong in THIS manifest and not a new pipeline, because they
#: come from the same pck and the workbench already fetches this file.
#:
#: Both scenes' `.tscn` sources author a placeholder string on the `Label`
#: node and overwrite it at `_Ready` from these loc rows
#: (`NDamageBlockedVfx.cs`'s `_blockedLoc`, `NPowerRemovedVfx.cs`'s
#: `_wearsOffLoc`) — the scene text itself is NOT the source of truth, which
#: is why this reads `localization/eng/vfx.json` rather than the `.tscn`.
#: `POWER_WEARS_OFF`'s authored placeholder is literally `"Where's Off?"`, so
#: extracting the scene text verbatim would ship a typo the game never shows.
VFX_LOC_STRINGS: list[tuple[str, str, str]] = [
    ("blocked", "vfx", "BLOCKED"),
    ("powerWearsOff", "vfx", "POWER_WEARS_OFF"),
]

#: Prefixed onto the content hash, so a rev still says which game build the
#: art came from at a glance.
GAME_VERSION = "v0.107.1"

# `path="res://..."` for a single-variant import, or `path.bptc="res://..."` /
# `path.s3tc="res://..."` etc. when the importer emitted a per-compression-
# format stub — verified against the real pck: every `.import` in scope
# carries exactly one `path`-ish line, never both a plain and a variant one.
_IMPORT_PATH = re.compile(r'^path(?:\.\w+)?="res://(?P<path>.+)"\s*$', re.MULTILINE)

# Which library animation an `AnimationPlayer` plays on spawn — the ONE
# animation (of possibly several, e.g. an implicit "RESET") whose tracks
# belong in the manifest.
_AUTOPLAY = re.compile(r'^autoplay\s*=\s*"(?P<v>[^"]+)"', re.MULTILINE)
# `name="..."` on a `[node ...]` header. `(?<![A-Za-z_])` guards the same way
# `vfx_scene.py`'s own attribute regexes do: a bare `name="..."` also matches
# the tail of an attribute like `unique_name="..."` with no word boundary
# between them.
_ATTR_NAME = re.compile(r'(?<![A-Za-z_])name="(?P<v>[^"]+)"')
_TEXTURE_REF = re.compile(r'^texture\s*=\s*ExtResource\("(?P<id>[^"]+)"\)', re.MULTILINE)

#: The only bezier-track channels `TransformAnimEffect` exposes. A scene may
#: author others (e.g. `vfx_block_broken`'s `modulate:{r,g,b,a}` fade, which
#: this schema has no field for) — those must not leak into the effect's
#: `lifetime`, which is the clock EVERY exposed LUT is baked and replayed
#: over (`bake_track(track, span=(0.0, lifetime))`). Widening `lifetime` to
#: an unrenderable track would slow every rendered one to match it.
_TRANSFORM_ANIM_CHANNELS = ("position:x", "position:y", "rotation")

# ---------------------------------------------------------------------------
# Script-derived constants. Godot `Tween` enums, verbatim: `EaseType` 0 =
# EASE_IN, 1 = EASE_OUT; `TransitionType` 1 = TRANS_SINE. The decompiled
# sources cast to the raw ints, so the manifest carries the raw ints too
# rather than re-encoding them as strings that would need a second table on
# the renderer side.
# ---------------------------------------------------------------------------

#: `NMonsterDeathVfx.PlayVfxInternal` tweens the dissolve shader's
#: `threshold` uniform from the scene's authored 1.0 down to 0
#: (`NMonsterDeathVfx.cs:239`; the 0 also appears as the `_tweenStartValue`
#: constant at :56, which despite its name is the tween's TARGET).
_DISSOLVE_THRESHOLD_TO = 0.0

#: `.SetEase((EaseType)1).SetTrans((TransitionType)1)` on that same tween
#: (`NMonsterDeathVfx.cs:239`) — EASE_OUT, TRANS_SINE. The brief omitted the
#: easing; a linear dissolve reads visibly differently.
_DISSOLVE_EASE = 1
_DISSOLVE_TRANS = 1

#: The dissolve duration CEILING, not a constant duration
#: (`NMonsterDeathVfx.cs:235`):
#:
#:     num  = 0.1f * GetViewportRect().Size.X          # :233-234
#:     num2 = Math.Min(node.Size.X / num * 2.5f, 2.5f) # :235
#:
#: `node` is the `SubViewport`, whose size the same method set to
#: `Vector2I(n, n) * 2` (:204) with `n = RoundToInt(max(bounds.X, bounds.Y))`
#: over the creature's MEASURED bounds. So the `Math.Min` binds — i.e. the
#: full 2.5s is reached — once `2n >= 0.1 * screenWidth`, that is once the
#: creature's larger bound is at least ~5% of the screen width. A smaller
#: creature dissolves proportionally faster.
_DISSOLVE_MAX_SECONDS = 2.5

#: `NoiseTexture2D`'s own default `width`/`height`. Both `NoiseTexture2D`
#: sub-resources in `vfx_monster_death.tscn` set neither (they set only
#: `noise`), and their `FastNoiseLite`s set only `frequency` — so the bake
#: size is the default rather than anything authored. It also has to stay a
#: power of two: `dissolve.gdshader` declares both uniforms `repeat_enable`,
#: and WebGL1 cannot REPEAT-wrap an NPOT texture, so an NPOT bake would
#: silently clamp instead of tiling.
_NOISE_TEXTURE_DEFAULT_SIZE = 512

#: `FastNoiseLite.seed`'s default. Neither sub-resource authors one.
_FAST_NOISE_DEFAULT_SEED = 0

#: `NPowerFlashVfx.StartVfx` (`NPowerFlashVfx.cs:96-102`), transcribed: a
#: two-phase `Tween` on the one `Sprite2D`. Outer list = STEPS, run in
#: sequence; inner list = tweeners within a step, run in parallel. Step 0 is
#: the `SetParallel(true)` pair (:98-99); `SetParallel(false)` (:100) makes
#: each of the remaining two its own step (:101, :102).
#:
#: `ease`/`trans` are `None` where the script calls neither `SetEase` nor
#: `SetTrans` — that means "the game overrides nothing here, so the `Tween`'s
#: own default applies", NOT "linear". Only the two `modulate:a` tweeners
#: carry an override.
#:
#: This is SCRIPT data, not scene data: nothing in `power_flash_vfx.tscn`
#: describes it, so it cannot be extracted and is pinned here with citations
#: instead — the same treatment `particle_scene._SCRIPT_EXTRAS` gives the
#: bespoke composite behaviours.
_POWER_FLASH_TWEEN: list[list[dict[str, Any]]] = [
    [
        # :98 — no SetEase/SetTrans.
        {"property": "scale", "to": 0.4, "duration": 0.4, "ease": None, "trans": None},
        # :99 — SetEase(1) = EASE_OUT, SetTrans(1) = TRANS_SINE.
        {"property": "modulateAlpha", "to": 1.0, "duration": 0.25, "ease": 1, "trans": 1},
    ],
    # :101 — no SetEase/SetTrans.
    [{"property": "scale", "to": 0.45, "duration": 0.4, "ease": None, "trans": None}],
    # :102 — SetEase(0) = EASE_IN, SetTrans(1) = TRANS_SINE.
    [{"property": "modulateAlpha", "to": 0.0, "duration": 0.25, "ease": 0, "trans": 1}],
]

#: The alpha the sprite actually renders at, from `_sprite.Modulate =
#: Colors.White` (`NPowerFlashVfx.cs:95`) — set BEFORE the method's first
#: `await`, so it lands on the same frame the node is added and no rendered
#: frame ever shows the scene's authored `modulate = Color(1, 1, 1, 0.65098)`.
#: That scene value is dead data; shipping it would make the workbench's flash
#: dimmer than the game's.
_POWER_FLASH_START_ALPHA = 1.0

#: Godot's default `canvas_item` blend mode, used when a `.gdshader` declares
#: no `render_mode` of its own. Both shaders in scope DO declare one
#: (`blend_add` for the power flash, `blend_premul_alpha` for the dissolve),
#: so this is the fallback for a future effect, not something in use today.
_DEFAULT_BLEND_MODE = "blend_mix"

_BLEND_MODE_TOKEN = re.compile(r"\bblend_\w+\b")


def content_rev(images_root: Path, written: list[Path], *, extra: bytes = b"") -> str:
    """A revision that changes exactly when the extracted art does.

    The art bucket serves these files ``immutable`` at non-content-addressed
    URLs, so the manifest's ``?v=<rev>`` is the ONLY thing that can make a
    browser re-fetch a changed frame — and the audio pipeline has already been
    bitten by that once (``project_workbench_player_attack_sfx_missing``). A
    hardcoded rev makes the bust a no-op: re-extracting after editing a frame
    would rewrite the same rev and the bucket would keep serving the old art.

    Hashing the bytes actually written (keyed by their path, so a rename
    counts) removes the human step entirely.

    *extra* folds in manifest content that never lands as a file under
    *images_root* — `VFX_LOC_STRINGS`, at present. Without it, a loc-only
    game update (the string changes, no art does) would leave `written`
    untouched and the rev would not move, silently pinning the OLD English
    text behind an `immutable` cache the same way an un-hashed art change
    once did.
    """
    h = hashlib.sha256()
    for path in sorted(written):
        h.update(path.relative_to(images_root).as_posix().encode())
        h.update(b"\0")
        h.update(hashlib.sha256(path.read_bytes()).digest())
    if extra:
        h.update(b"\0extra\0")
        h.update(extra)
    return f"{GAME_VERSION}-{h.hexdigest()[:12]}"


def _read(index: dict[str, tuple[int, int]], pck: Path, res_path: str) -> bytes:
    if res_path not in index:
        raise KeyError(f"{res_path} not in pck")
    offset, size = index[res_path]
    with open(pck, "rb") as f:
        f.seek(offset)
        return f.read(size)


def _imported_ctex_path(index: dict[str, tuple[int, int]], pck: Path, source_png: str) -> str:
    """Resolve `images/.../foo.png` to its `.godot/imported/foo.png-<hash>.ctex`.

    The pck stores only a `.import` stub at the source path; the stub names the
    real, hashed location.
    """
    stub = _read(index, pck, f"{source_png}.import").decode("utf-8", "replace")
    m = _IMPORT_PATH.search(stub)
    if m is None:
        raise ValueError(f"no imported path in {source_png}.import")
    return m.group("path")


def _extract_loc_strings(
    index: dict[str, tuple[int, int]],
    pck: Path,
    spec: list[tuple[str, str, str]],
) -> dict[str, str]:
    """`VFX_LOC_STRINGS`-shaped spec -> `{manifest key: English string}`.

    Reads `localization/eng/<table>.json` fresh once per distinct table
    (cheap — these are a handful of KB, not the art tree), keyed by manifest
    field name rather than by loc key, so `Combatant.tsx` names a field
    instead of a loc row. A row named in the spec but absent from the pck's
    table raises rather than silently omitting the key: the CLIENT'S
    fallback (a hardcoded English literal) exists for "the manifest doesn't
    have this yet", not for "the extractor pretended it does".
    """
    tables: dict[str, dict[str, str]] = {}
    out: dict[str, str] = {}
    for manifest_key, table, row in spec:
        if table not in tables:
            tables[table] = json.loads(
                _read(index, pck, f"localization/eng/{table}.json").decode("utf-8")
            )
        if row not in tables[table]:
            raise KeyError(f"localization/eng/{table}.json has no {row!r} row")
        out[manifest_key] = tables[table][row]
    return out


def _make_scene_loader(index: dict[str, tuple[int, int]], pck: Path) -> Callable[[str], str | None]:
    """`load` callback for `parse_particle_scene`: res-path -> `.tscn` source.

    `_ATTR_PATH` in `particle_scene.py` strips the `res://` prefix off every
    path it reads, and the pck's own index keys have no `res://` prefix
    either — so a res-relative path resolves against the index unmodified.
    """

    def load(res_path: str) -> str | None:
        if res_path not in index:
            return None
        return _read(index, pck, res_path).decode("utf-8", "replace")

    return load


def _camel(snake: str) -> str:
    """`color_initial_ramp` -> `colorInitialRamp` (R2: Task 4 owns this boundary)."""
    head, *rest = snake.split("_")
    return head + "".join(part.capitalize() for part in rest)


def _write_lut(cache: dict[str, str], images_root: Path, written: list[Path], payload: Any) -> str:
    """Write *payload* (JSON-serialisable) as a content-addressed LUT file.

    Baking is exact (not lossy 8-bit), so a shared leaf scene's identical
    curve/gradient data hashes to the identical file — that's what makes
    `vfx_common_specks`, reused across six composites, write its LUTs once.

    REVIEWED DEVIATION from design spec §2.3, which specifies baking every
    `Curve`/`Gradient` to a 256x1 PNG (an exact match for how Godot itself
    samples them as textures at runtime), with `color_initial_ramp` as the
    one named HDR exception that must stay a float array. This function
    instead writes every baked curve/gradient — not just the HDR one — as a
    JSON float array, for two reasons: (1) unlike a color channel, fields
    like `damping_curve`/`scale_curve` are not `[0, 1]`-bounded, and §2.3
    specifies no normalization scheme for packing an arbitrary-range float
    into an 8-bit channel — inventing one here would be a silent, unreviewed
    precision decision; (2) `color_ramp` (the non-initial gradient) isn't
    named as HDR-safe the way `color_initial_ramp` is, but nothing rules out
    it also carrying values >1.0, and a JSON array sidesteps having to prove
    it can't. Measured cost: 636K across 73 LUTs from the real pck, none of
    it git-tracked (extraction output, not committed source). If a future
    renderer wants to sample these as real GPU textures, a PNG (or
    texture-upload-from-array) path can be added later without touching the
    manifest schema — this field stays a path string either way.
    """
    data = json.dumps(payload).encode()
    digest = hashlib.sha256(data).hexdigest()[:16]
    if digest in cache:
        return cache[digest]
    rel = f"vfx_particles/{digest}.json"
    out_path = images_root / rel
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_bytes(data)
    written.append(out_path)
    cache[digest] = rel
    return rel


def _serialize_process(
    process: ProcessSpec, lut_cache: dict[str, str], images_root: Path, written: list[Path]
) -> dict[str, Any]:
    """`ProcessSpec` -> camelCase JSON, curves/gradients baked to LUTs.

    `color_initial_ramp` is the one exception (spec §2.3): it is HDR (values
    above 1.0 are real, e.g. 2.454227), so it is serialized as an inline
    float array rather than a file — an 8-bit-quantized asset would clip it.
    Every other `Curve`/`Gradient` field becomes a content-addressed
    `vfx_particles/<hash>.json` reference via `_write_lut`.
    """
    out: dict[str, Any] = {}
    for f in dc_fields(process):
        value = getattr(process, f.name)
        key = _camel(f.name)
        if f.name == "color_initial_ramp":
            out[key] = [list(c) for c in bake_gradient(value)] if value is not None else None
        elif isinstance(value, Curve):
            out[key] = _write_lut(lut_cache, images_root, written, bake_curve(value))
        elif isinstance(value, Gradient):
            out[key] = _write_lut(
                lut_cache, images_root, written, [list(c) for c in bake_gradient(value)]
            )
        elif isinstance(value, tuple):
            out[key] = list(value)
        else:
            out[key] = value
    return out


def _serialize_material(
    res_path: str,
    index: dict[str, tuple[int, int]],
    pck: Path,
    material_cache: dict[str, dict[str, Any]],
    lut_cache: dict[str, str],
    images_root: Path,
    written: list[Path],
) -> dict[str, Any]:
    """A `ShaderMaterial` `.tres` -> camelCase JSON, its curves/gradients baked.

    The `lut` gradient is what COLOURISES a grayscale particle texture
    (`texture(lut, tex.rr) * vertex_color` in every shader of the family), and
    `frameCount`/`flipbookSize`/`flipbookCurve` are what animate a sprite
    sheet. Both live in the material, not in the per-emitter
    `ParticleProcessMaterial`, so neither is reachable from the scene alone.

    Cached by res-path: 20 distinct materials serve 48 emitters, and several
    are shared by six composites.
    """
    cached = material_cache.get(res_path)
    if cached is not None:
        return cached

    spec = parse_material(_read(index, pck, res_path).decode("utf-8", "replace"), res_path)
    out: dict[str, Any] = {
        "path": spec.path,
        "shader": spec.shader,
        "lut": (
            _write_lut(lut_cache, images_root, written, [list(c) for c in bake_gradient(spec.lut)])
            if spec.lut is not None
            else None
        ),
        "frameCount": spec.frame_count,
        "flipbookSize": list(spec.flipbook_size) if spec.flipbook_size is not None else None,
        "flipbookCurve": (
            _write_lut(lut_cache, images_root, written, bake_curve(spec.flipbook_curve))
            if spec.flipbook_curve is not None
            else None
        ),
        "useErosion": spec.use_erosion,
        "useHueShift": spec.use_hue_shift,
    }
    material_cache[res_path] = out
    return out


def _out_rel_texture(source_png: str) -> str:
    """`images/vfx/common/common_speck.png` -> `vfx/common/common_speck.png`.

    `images_root` (the extractor's output root) already IS the game's
    `images/` tree, so the leading `images/` component of a source res-path
    is stripped, not kept — this is what makes a texture shared by several
    composites (the same source res-path every time) land at the same
    output path instead of being copied under a private per-effect name.
    """
    prefix = "images/"
    return source_png[len(prefix) :] if source_png.startswith(prefix) else source_png


def _serialize_emitter(
    emitter: EmitterSpec,
    index: dict[str, tuple[int, int]],
    pck: Path,
    images_root: Path,
    texture_cache: dict[str, str],
    lut_cache: dict[str, str],
    material_cache: dict[str, dict[str, Any]],
    written: list[Path],
) -> dict[str, Any]:
    if not emitter.texture:
        raise ValueError(f"emitter {emitter.name!r} has no texture")
    rel = texture_cache.get(emitter.texture)
    if rel is None:
        ctex = _imported_ctex_path(index, pck, emitter.texture)
        img = decode_ctex(_read(index, pck, ctex))
        if img is None:
            # Same reasoning as the flipbook branch: a silent skip would leave
            # the manifest naming a texture that isn't on disk.
            raise ValueError(f"could not decode {ctex} (for particle texture {emitter.texture})")
        rel = _out_rel_texture(emitter.texture)
        out_path = images_root / rel
        out_path.parent.mkdir(parents=True, exist_ok=True)
        img.save(out_path)
        written.append(out_path)
        texture_cache[emitter.texture] = rel

    return {
        "name": emitter.name,
        "texture": rel,
        # GH #669. `null` / `false` for every `PARTICLE_EFFECTS` composite —
        # only a Spine scene's slot-parented emitters carry either.
        "slot": emitter.slot,
        "blendAdd": emitter.blend_add,
        "material": (
            _serialize_material(
                emitter.material, index, pck, material_cache, lut_cache, images_root, written
            )
            if emitter.material
            else None
        ),
        "amount": emitter.amount,
        "lifetime": emitter.lifetime,
        "oneShot": emitter.one_shot,
        "explosiveness": emitter.explosiveness,
        "randomness": emitter.randomness,
        "fixedFps": emitter.fixed_fps,
        "localCoords": emitter.local_coords,
        # GH #669. `false`/`0.3` (Godot's default) for every `PARTICLE_EFFECTS`
        # composite — the crab explosion's ember burst is the only emitter in
        # scope that enables trails, and for it the ribbon IS the effect.
        "trailEnabled": emitter.trail_enabled,
        "trailLifetime": emitter.trail_lifetime,
        "rotation": emitter.rotation,
        "selfModulate": list(emitter.self_modulate),
        "zIndex": emitter.z_index,
        "offset": list(emitter.offset),
        "startDelay": emitter.start_delay,
        "underPivotScale": emitter.under_pivot_scale,
        "underMirrorX": emitter.under_mirror_x,
        # Which of Godot's two particle nodes authored this emitter -- see
        # `EmitterSpec.cpu`. The renderer branches on it, so it is data, not
        # provenance trivia.
        "cpu": emitter.cpu,
        "process": _serialize_process(emitter.process, lut_cache, images_root, written),
    }


def _ext_resource_paths(text: str) -> dict[str, str]:
    """`ext_resource` id -> `res://`-stripped path, for the whole document.

    Mirrors the table `vfx_scene.parse_vfx_scene` builds internally for
    resolving flipbook frames — declarations legitimately live in the scene
    header, so this is built once over the whole file, not a node section.
    """
    out: dict[str, str] = {}
    for m in _EXT_RESOURCE_LINE.finditer(text):
        attrs = m.group("attrs")
        path_m = _ATTR_PATH.search(attrs)
        id_m = _ATTR_ID.search(attrs)
        if path_m is not None and id_m is not None:
            out[id_m.group("v")] = path_m.group("v")
    return out


def _sprite_nodes(text: str, ext: dict[str, str]) -> dict[str, tuple[str, str]]:
    """Node name -> `(source texture path, node body)`, per non-root `Sprite2D`.

    A `transform_anim` scene's bezier tracks address their targets by node
    name (`NodePath("BlockBreakRight:position:x")`), and the manifest needs
    each such sprite's own texture — this resolves that name -> texture
    edge from the scene graph rather than guessing it from the effect name.

    The body comes back with it because the tracks are DELTAS from nothing:
    a channel the animation never touches leaves the node's own authored
    `position`/`rotation` standing, and that value lives only here (see
    `_transform_anim_rest_pose`).
    """
    out: dict[str, tuple[str, str]] = {}
    for header in _NODE_HEADER.finditer(text):
        attrs = header.group("attrs")
        if "parent=" not in attrs:
            continue
        type_m = _ATTR_TYPE.search(attrs)
        if type_m is None or type_m.group("v") != "Sprite2D":
            continue
        name_m = _ATTR_NAME.search(attrs)
        if name_m is None:
            continue
        next_header = _SECTION_HEADER.search(text, header.end())
        end = next_header.start() if next_header is not None else len(text)
        body = text[header.end() : end]
        tex_m = _TEXTURE_REF.search(body)
        if tex_m is None:
            continue
        path = ext.get(tex_m.group("id"))
        if path is not None:
            out[name_m.group("v")] = (path, body)
    return out


def _transform_anim_rest_pose(section: str) -> tuple[float, float, float]:
    """A sprite node's authored `(position.x, position.y, rotation)`.

    Godot's own node defaults when the scene omits them: `Vector2.ZERO` and
    `0.0`. This is the value a channel with NO track keeps for the whole
    effect — the renderer cannot invent it (the manifest carries a LUT path
    or `None`, and `None` is NOT "zero"), so it travels in the manifest
    beside the LUT paths.
    """
    pos = _or(_vector2(section, "position"), (0.0, 0.0))
    return (pos[0], pos[1], _or(_scalar(section, "rotation"), 0.0))


def _serialize_transform_anim_sprite(
    node_name: str,
    texture_source: str,
    node_section: str,
    tracks_by_channel: dict[str, TrackSpec],
    span: tuple[float, float],
    index: dict[str, tuple[int, int]],
    pck: Path,
    images_root: Path,
    texture_cache: dict[str, str],
    lut_cache: dict[str, str],
    written: list[Path],
) -> dict[str, Any]:
    rel = texture_cache.get(texture_source)
    if rel is None:
        ctex = _imported_ctex_path(index, pck, texture_source)
        img = decode_ctex(_read(index, pck, ctex))
        if img is None:
            raise ValueError(
                f"could not decode {ctex} (for transform_anim sprite texture {texture_source})"
            )
        rel = _out_rel_texture(texture_source)
        out_path = images_root / rel
        out_path.parent.mkdir(parents=True, exist_ok=True)
        img.save(out_path)
        written.append(out_path)
        texture_cache[texture_source] = rel

    def lut(channel: str) -> str | None:
        track = tracks_by_channel.get(channel)
        if track is None:
            return None
        # `span`, not the track's own key range: every sprite in one effect is
        # replayed off ONE clock (`elapsed / lifetime`), so a track shorter
        # than the animation must bake its trailing hold, not stretch itself
        # across the whole playback — see `bake_track`'s doc.
        return _write_lut(lut_cache, images_root, written, bake_track(track, span=span))

    position_x, position_y, rotation = _TRANSFORM_ANIM_CHANNELS
    rest_x, rest_y, rest_rotation = _transform_anim_rest_pose(node_section)
    return {
        "name": node_name,
        "texture": rel,
        "positionX": lut(position_x),
        "positionY": lut(position_y),
        "rotation": lut(rotation),
        "restPositionX": rest_x,
        "restPositionY": rest_y,
        "restRotation": rest_rotation,
    }


def blend_mode(shader_src: str) -> str:
    """A `.gdshader`'s `blend_*` render mode, or Godot's `canvas_item` default.

    The renderer needs it: `power_flash_vfx.gdshader` is `blend_add`, so a
    flash composited with the usual alpha mix draws dark instead of glowing,
    and `dissolve.gdshader` is `blend_premul_alpha`, whose colour is already
    multiplied by its own alpha.

    Scans the whole source for the token rather than only a `render_mode`
    line, which keeps this a one-branch function. That is safe for the pck as
    it stands, not by language rule: GLSL would happily let a `blend_` token
    appear elsewhere (`uniform float blend_strength;` is legal and would be
    matched), so the guarantee is empirical. Measured over all 90 `.gdshader`
    files in `SlayTheSpire2.pck`: zero `blend_*` tokens occur outside a
    `render_mode` line, and every shader's first such token IS its render
    mode. Re-check that if this is ever pointed at another pck.
    """
    m = _BLEND_MODE_TOKEN.search(shader_src)
    return m.group(0) if m is not None else _DEFAULT_BLEND_MODE


def _shader_stem(res_path: str) -> str:
    """`shaders/dissolve.gdshader` -> `dissolve`.

    The same "name a shader by its basename" convention `MaterialSpec.shader`
    already uses for the particle materials.
    """
    return Path(res_path).stem


def _noise_texture_frequency_and_seed(
    material_section: str, uniform: str, sub_section: dict[str, str]
) -> tuple[float, int]:
    """`shader_parameter/<uniform>` -> its `FastNoiseLite`'s frequency and seed.

    Two hops: the uniform names a `NoiseTexture2D` sub-resource, which names a
    `FastNoiseLite`. Every step raises rather than defaulting — a missing hop
    means the scene is not the shape this was written against, and a silently
    defaulted frequency would bake a plausible-looking WRONG noise field.

    Also refuses a `NoiseTexture2D` that authors its own `width`/`height`: the
    bake size would then not be `_NOISE_TEXTURE_DEFAULT_SIZE` and the caller's
    512x512 would be wrong without anything failing.
    """
    tex_id = _sub_ref_id(material_section, f"shader_parameter/{uniform}")
    if tex_id is None:
        raise ValueError(f"no shader_parameter/{uniform} NoiseTexture2D on the dissolve material")
    tex = sub_section.get(tex_id)
    if tex is None:
        raise ValueError(f"shader_parameter/{uniform} names unknown sub-resource {tex_id!r}")
    for authored in ("width", "height"):
        if _int(tex, authored) is not None:
            raise ValueError(
                f"NoiseTexture2D {tex_id!r} authors its own {authored}; the bake size is no "
                f"longer NoiseTexture2D's {_NOISE_TEXTURE_DEFAULT_SIZE}px default"
            )
    noise_id = _sub_ref_id(tex, "noise")
    if noise_id is None:
        raise ValueError(f"NoiseTexture2D {tex_id!r} names no noise resource")
    noise = sub_section.get(noise_id)
    if noise is None:
        raise ValueError(f"NoiseTexture2D {tex_id!r} names unknown noise sub-resource {noise_id!r}")
    frequency = _scalar(noise, "frequency")
    if frequency is None:
        raise ValueError(f"FastNoiseLite {noise_id!r} authors no frequency")
    return frequency, _or(_int(noise, "seed"), _FAST_NOISE_DEFAULT_SEED)


def _sprite_with_shader_material(
    nodes: list[_NodeInfo], sub_section: dict[str, str]
) -> tuple[_NodeInfo, str]:
    """The one `Sprite2D` carrying a `ShaderMaterial`, and that material's body.

    Both scenes in `DISSOLVE_EFFECTS`/`SPRITE_SHADER_EFFECTS` have exactly one.
    Raises when there is none or more than one rather than picking the first:
    "the first sprite happened to be the right one" is precisely the kind of
    silent mis-pick this extractor has already been bitten by (the Spine
    type-string mismatch in sub-project 1).

    A `ShaderMaterial` is told apart from the `CanvasItemMaterial` the same
    scenes also use by its `shader = ExtResource(...)` line — a blend-mode-only
    `CanvasItemMaterial` names no shader at all.
    """
    found: list[tuple[_NodeInfo, str]] = []
    for node in nodes:
        if node.type != "Sprite2D":
            continue
        material_id = _sub_ref_id(node.section, "material")
        if material_id is None:
            continue
        body = sub_section.get(material_id)
        if body is None:
            continue
        ref = _resource_ref_field(body, "shader")
        if ref is None or ref[0] != "Ext":
            continue
        found.append((node, body))
    if len(found) != 1:
        raise ValueError(f"expected exactly one Sprite2D with a ShaderMaterial, found {len(found)}")
    return found[0]


def _extract_dissolve(
    name: str,
    scene_path: str,
    index: dict[str, tuple[int, int]],
    pck: Path,
    images_root: Path,
    written: list[Path],
) -> tuple[dict[str, Any], str]:
    """`vfx_monster_death` -> its manifest entry and its debris scene source.

    Returns the entry WITHOUT `emitters`; the caller fills those in, because
    emitter serialization owns the shared texture/LUT/material caches.
    """
    text = _read(index, pck, scene_path).decode("utf-8", "replace")
    ext_path, sub_section = _parse_resource_tables(text)
    sprite, material = _sprite_with_shader_material(_iter_nodes(text), sub_section)

    shader_res = _ext_ref_path(material, "shader", ext_path)
    if shader_res is None:
        raise ValueError(f"{scene_path}: dissolve material names no shader")
    threshold_from = _scalar(material, "shader_parameter/threshold")
    if threshold_from is None:
        raise ValueError(f"{scene_path}: dissolve material authors no threshold")
    modulate = _color(sprite.section, "modulate")
    if modulate is None:
        raise ValueError(f"{scene_path}: dissolve sprite authors no modulate")

    gradients: dict[str, str] = {}
    for key, uniform in (("gradient1", "dissolveGradient1"), ("gradient2", "dissolveGradient2")):
        frequency, seed = _noise_texture_frequency_and_seed(material, uniform, sub_section)
        rel = f"vfx/{name}/{key}.png"
        out_path = images_root / rel
        out_path.parent.mkdir(parents=True, exist_ok=True)
        bake_noise_png(
            str(out_path),
            _NOISE_TEXTURE_DEFAULT_SIZE,
            _NOISE_TEXTURE_DEFAULT_SIZE,
            frequency,
            seed=seed,
        )
        written.append(out_path)
        gradients[key] = rel

    entry: dict[str, Any] = {
        "kind": "dissolve",
        "shader": _shader_stem(shader_res),
        "blendMode": blend_mode(_read(index, pck, shader_res).decode("utf-8", "replace")),
        "gradient1": gradients["gradient1"],
        "gradient2": gradients["gradient2"],
        "modulateAlpha": modulate[3],
        "thresholdFrom": threshold_from,
        "thresholdTo": _DISSOLVE_THRESHOLD_TO,
        # The CEILING, reached by any creature at least ~5% of screen width —
        # see `_DISSOLVE_MAX_SECONDS` for the derivation. A smaller creature
        # dissolves faster, and the factor depends on the live screen width
        # and the creature's measured bounds, neither of which is in the pck.
        "dissolveSeconds": _DISSOLVE_MAX_SECONDS,
        "dissolveEase": _DISSOLVE_EASE,
        "dissolveTrans": _DISSOLVE_TRANS,
        # The effect's own on-screen life, which is the DISSOLVE tween's —
        # `PlayVfxInternal` awaits that tween and then frees the node
        # (`NMonsterDeathVfx.cs:240-241`), so nothing else decides when this
        # ends. Deliberately NOT the debris emitter's 1.8s `lifetime`, which
        # is a per-particle lifespan and expires first; the burst finishing
        # early does not end the fade. It inherits `dissolveSeconds`' caveat
        # exactly: a ceiling, shorter for a creature under ~5% of screen width.
        "lifetime": _DISSOLVE_MAX_SECONDS,
        # The debris emitter's own `emissionSphereRadius` is deliberately
        # blanked to 0 (see the loop that does it in `extract_vfx`); THIS is
        # the field that replaces it. The scene authors 99.23, but
        # `NMonsterDeathVfx.cs:231` overwrites it with
        # `node.Size.Y / 4f` before the burst ever emits, where the
        # SubViewport's size is `Vector2I(n, n) * 2` over the creature's
        # measured bounds (:204) — so the real radius is `n / 2`, half the
        # creature's larger on-screen extent, and the scene's 99.23 (like the
        # SubViewport's `size = Vector2i(2, 2)`) is an authoring placeholder.
        # Baking it would pin every creature's debris to one wrong radius.
        "emissionRadiusPerCreatureExtent": 0.5,
    }
    return entry, text


def _extract_sprite_shader(
    scene_path: str, index: dict[str, tuple[int, int]], pck: Path
) -> dict[str, Any]:
    """`vfx_power_flash_vfx` -> its manifest entry.

    The scene contributes the sprite's `scale` and the material's `strength`
    override; everything that MOVES comes from `_POWER_FLASH_TWEEN`, which the
    scene has no representation of at all.
    """
    text = _read(index, pck, scene_path).decode("utf-8", "replace")
    ext_path, sub_section = _parse_resource_tables(text)
    sprite, material = _sprite_with_shader_material(_iter_nodes(text), sub_section)

    shader_res = _ext_ref_path(material, "shader", ext_path)
    if shader_res is None:
        raise ValueError(f"{scene_path}: sprite material names no shader")
    strength = _scalar(material, "shader_parameter/strength")
    if strength is None:
        # The shader's own `uniform float strength = 3.0` would apply, which
        # is three times the authored flash. Raising beats silently shipping
        # a value the scene meant to override.
        raise ValueError(f"{scene_path}: material authors no strength override")
    scale = _uniform_scale(sprite.section)

    return {
        "kind": "sprite_shader",
        "shader": _shader_stem(shader_res),
        "blendMode": blend_mode(_read(index, pck, shader_res).decode("utf-8", "replace")),
        "strength": strength,
        "scale": scale,
        "modulateAlpha": _POWER_FLASH_START_ALPHA,
        "tween": _POWER_FLASH_TWEEN,
        "lifetime": sum(max(t["duration"] for t in step) for step in _POWER_FLASH_TWEEN),
    }


def _uniform_scale(section: str) -> float:
    """A node's `scale = Vector2(s, s)` as the single scalar `s`.

    The manifest carries `scale` as one number (as the flipbook and Spine
    entries already do), and `NPowerFlashVfx`'s tween drives it with
    `Vector2.One * 0.4f` / `* 0.45f` — uniform throughout. A non-uniform
    authored scale would make that one number a lie, so it raises instead of
    silently keeping the x component.
    """
    scale = _vector2(section, "scale")
    if scale is None:
        raise ValueError("sprite authors no scale")
    if scale[0] != scale[1]:
        raise ValueError(f"non-uniform sprite scale {scale} cannot be one manifest scalar")
    return scale[0]


def extract_vfx(
    pck_path: str,
    images_root: Path,
    *,
    flipbooks: list[tuple[str, str]],
    spines: list[tuple[str, str, str]],
    particles: list[tuple[str, str]] | None = None,
    transform_anims: list[tuple[str, str]] | None = None,
    dissolves: list[tuple[str, str]] | None = None,
    sprite_shaders: list[tuple[str, str]] | None = None,
    loc_strings: list[tuple[str, str, str]] | None = None,
    rev: str | None = None,
) -> dict[str, Any]:
    """Extract every requested effect kind under *images_root*; write the
    manifest.

    Six independent lists, one per output shape: *flipbooks*, *spines*,
    *particles*, *transform_anims*, *dissolves* and *sprite_shaders*. The
    module docstring describes what each shape is. The first two are required
    (pass `[]` to skip them); the other four default to `None`, which is why a
    test can extract one kind in isolation without naming the rest.

    *loc_strings* is a SEVENTH, unrelated list (`VFX_LOC_STRINGS`-shaped):
    English UI text a DOM-rendered VFX panel shows, sourced from the pck's
    `localization/eng/` rather than from any `.tscn`/scene shape above. It
    lands in the manifest's own `locStrings` key, not under `effects`.

    *rev* defaults to a content hash of everything written — see
    :func:`content_rev` for why that must not be a hand-kept constant.
    """
    pck = Path(pck_path)
    index = read_pck_index(pck_path)
    effects: dict[str, Any] = {}
    written: list[Path] = []
    # ONE set of caches for the whole extraction, not one per effect-kind
    # block. They were previously re-created at the top of each block, which
    # made `written` double-count any texture or LUT two blocks shared: both
    # `_serialize_emitter` and `_write_lut` append to `written` on a cache
    # MISS, and a reset cache misses on something already on disk. That fed
    # `content_rev` the same (path, digest) pair twice. Sharing them also lets
    # the dissolve's debris reuse a texture a particle composite already
    # decoded, which is the same content-addressing win that makes
    # `vfx_common_specks` write its LUTs once across six composites.
    texture_cache: dict[str, str] = {}
    lut_cache: dict[str, str] = {}
    material_cache: dict[str, dict[str, Any]] = {}

    for name, scene_path in flipbooks:
        spec = parse_vfx_scene(_read(index, pck, scene_path).decode("utf-8", "replace"))
        if not isinstance(spec, FlipbookSpec):
            raise ValueError(
                f"{scene_path} did not parse as a flipbook (got {type(spec).__name__})"
            )
        out_dir = images_root / "vfx" / name
        out_dir.mkdir(parents=True, exist_ok=True)
        rels: list[str] = []
        for source_png in spec.frames:
            ctex = _imported_ctex_path(index, pck, source_png)
            img = decode_ctex(_read(index, pck, ctex))
            if img is None:
                # A silent skip would leave the manifest promising a frame that
                # is not on disk — a 404 at runtime, far from the cause.
                raise ValueError(f"could not decode {ctex} (for {source_png})")
            fname = Path(source_png).name
            img.save(out_dir / fname)
            written.append(out_dir / fname)
            rels.append(f"vfx/{name}/{fname}")
        effects[name] = {
            "kind": "flipbook",
            "frames": rels,
            "fps": spec.fps,
            "loop": spec.loop,
            "scale": spec.scale,
            "randomizeRotation": spec.randomize_rotation,
            "rotationRange": list(spec.rotation_range),
        }

    for name, scene_path, source in spines:
        spec = parse_vfx_scene(_read(index, pck, scene_path).decode("utf-8", "replace"))
        if not isinstance(spec, SpineVfxSpec):
            # A silent `scale=1.0, clip=None` fallback here would write wrong
            # values into the manifest while reporting success — exactly how
            # this task's own fix round happened (a type-string mismatch made
            # every real Spine scene parse as `None`, and nothing failed
            # loudly enough to notice).
            raise ValueError(f"{scene_path} did not parse as a spine (got {type(spec).__name__})")

        extract_rig(pck_path, source, str(images_root / "vfx_rigs"), slug=name)
        written.extend(p for p in (images_root / "vfx_rigs" / name).iterdir() if p.is_file())
        entry: dict[str, Any] = {
            "kind": "spine",
            "rig": f"vfx_rigs/{name}/{name}",
            "scale": spec.scale,
            "clip": spec.clip,
        }
        # GH #669: a Spine scene may also parent `GPUParticles2D` emitters to
        # slots on its own rig, restarted by the rig's own animation events —
        # `kaiser_crab_boss_explosion`'s ember burst on `explosion_slot`. That
        # is a composition `SPINE_EFFECTS` (pure rigs) and `PARTICLE_EFFECTS`
        # (pure emitters) had no member for; `emitters` is it. Absent, not
        # empty, for a scene with none, so every other spine entry is
        # byte-identical to what it was.
        slot_load = _make_scene_loader(index, pck)
        scene_text = _read(index, pck, scene_path).decode("utf-8", "replace")
        slot_emitters = [e for e in parse_emitters(scene_text, slot_load) if e.slot]
        if slot_emitters:
            serialized = []
            for emitter in slot_emitters:
                facts = SPINE_SLOT_EMITTER_SCRIPT_FACTS.get((name, emitter.name))
                if facts is None:
                    # The trigger lives in the effect's C# script, not its
                    # scene, so an emitter with no entry has nothing to start
                    # it. Loud rather than emitted-and-inert: a burst that
                    # never fires looks exactly like a renderer bug.
                    raise ValueError(
                        f"{name}: slot emitter {emitter.name!r} has no "
                        f"SPINE_SLOT_EMITTER_SCRIPT_FACTS entry (its restart "
                        f"event is authored in C#, not in {scene_path})"
                    )
                row = _serialize_emitter(
                    emitter,
                    index,
                    pck,
                    images_root,
                    texture_cache,
                    lut_cache,
                    material_cache,
                    written,
                )
                row["event"] = facts["event"]
                # `_Ready` overrides the scene here — see the table's doc.
                row["oneShot"] = facts["one_shot"]
                serialized.append(row)
            entry["emitters"] = serialized
        effects[name] = entry

    if particles:
        load = _make_scene_loader(index, pck)
        for name, scene_path in particles:
            text = _read(index, pck, scene_path).decode("utf-8", "replace")
            particle_spec = parse_particle_scene(text, load)
            if not isinstance(particle_spec, CompositeSpec):
                # Not a fallback-worthy case: a scene in PARTICLE_EFFECTS that
                # doesn't parse as a composite means scope drifted from what
                # was surveyed, same reasoning as the flipbook/spine branches.
                raise ValueError(
                    f"{scene_path} did not parse as a particle composite "
                    f"(got {type(particle_spec).__name__})"
                )
            effects[name] = {
                "kind": "particles",
                "lifetime": particle_spec.lifetime,
                "tinted": particle_spec.tinted,
                "mirrorX": particle_spec.mirror_x,
                "pivotScale": list(particle_spec.pivot_scale)
                if particle_spec.pivot_scale is not None
                else None,
                "emitters": [
                    _serialize_emitter(
                        e,
                        index,
                        pck,
                        images_root,
                        texture_cache,
                        lut_cache,
                        material_cache,
                        written,
                    )
                    for e in particle_spec.emitters
                ],
            }

    if transform_anims:
        for name, scene_path in transform_anims:
            text = _read(index, pck, scene_path).decode("utf-8", "replace")
            animations = parse_animation(text)
            autoplay_m = _AUTOPLAY.search(text)
            if autoplay_m is None or autoplay_m.group("v") not in animations:
                # Same reasoning as the flipbook/spine/particle branches: a
                # scene in TRANSFORM_ANIM_EFFECTS whose autoplay animation
                # can't be resolved means scope drifted from what was
                # surveyed, not something to silently fall back on.
                raise ValueError(f"{scene_path}: no resolvable autoplay animation")
            tracks = animations[autoplay_m.group("v")]
            if not tracks:
                raise ValueError(f"{scene_path}: autoplay animation has no bezier tracks")

            ext = _ext_resource_paths(text)
            sprite_nodes = _sprite_nodes(text, ext)
            if not sprite_nodes:
                raise ValueError(f"{scene_path}: no Sprite2D children with a resolvable texture")

            by_node: dict[str, dict[str, TrackSpec]] = {}
            for t in tracks:
                node, _, channel = t.path.partition(":")
                by_node.setdefault(node, {})[channel] = t

            exposed_tracks = [
                t
                for channels in by_node.values()
                for channel, t in channels.items()
                if channel in _TRANSFORM_ANIM_CHANNELS
            ]
            duration = max((t.keys[-1].time for t in exposed_tracks if t.keys), default=0.0)

            effects[name] = {
                "kind": "transform_anim",
                "lifetime": duration,
                "sprites": [
                    _serialize_transform_anim_sprite(
                        node_name,
                        texture_source,
                        node_section,
                        by_node.get(node_name, {}),
                        (0.0, duration),
                        index,
                        pck,
                        images_root,
                        texture_cache,
                        lut_cache,
                        written,
                    )
                    for node_name, (texture_source, node_section) in sprite_nodes.items()
                ],
            }

    if dissolves:
        load = _make_scene_loader(index, pck)
        for name, scene_path in dissolves:
            entry, scene_text = _extract_dissolve(
                name, scene_path, index, pck, images_root, written
            )
            # `parse_emitters`, not `parse_particle_scene`: the debris burst is
            # one PART of this effect, and what starts it, when, and for how
            # long lives in `NMonsterDeathVfx` rather than in the
            # `NVfxParticleSystem` composite layer.
            emitters = parse_emitters(scene_text, load)
            if not emitters:
                raise ValueError(f"{scene_path}: no debris emitters found")
            entry["emitters"] = [
                _serialize_emitter(
                    e, index, pck, images_root, texture_cache, lut_cache, material_cache, written
                )
                for e in emitters
            ]
            for emitter in entry["emitters"]:
                # Blank the authoring placeholder. Emission shapes are read
                # generically for every emitter in the pck, which is right
                # everywhere EXCEPT here: `NMonsterDeathVfx.cs:231` overwrites
                # `EmissionSphereRadius` at runtime, per creature, before the
                # burst emits — see `emissionRadiusPerCreatureExtent` in
                # `_extract_dissolve`, which carries the rule that replaces it.
                # Shipping the scene's 99.23 would pin every creature's debris
                # to one wrong radius, and would do it silently, because the
                # number looks perfectly plausible. Zero instead: a radius-0
                # sphere degenerates to the emitter origin, so a renderer that
                # forgets to apply the rule fails visibly rather than subtly.
                emitter["process"]["emissionSphereRadius"] = 0.0
            effects[name] = entry

    if sprite_shaders:
        for name, scene_path in sprite_shaders:
            effects[name] = _extract_sprite_shader(scene_path, index, pck)

    loc: dict[str, str] = _extract_loc_strings(index, pck, loc_strings) if loc_strings else {}

    manifest: dict[str, Any] = {
        "rev": rev
        if rev is not None
        else content_rev(images_root, written, extra=json.dumps(loc, sort_keys=True).encode()),
        "effects": effects,
    }
    if loc:
        manifest["locStrings"] = loc
    manifest_path = images_root / "vfx" / "manifest.json"
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
    return manifest


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("pck")
    ap.add_argument("images_root")
    # Omitted by `just extract-vfx`: the default (a content hash of the
    # extracted files) is what keeps the `?v=` cache bust honest.
    ap.add_argument("--rev", default=None)
    args = ap.parse_args()
    manifest = extract_vfx(
        args.pck,
        Path(args.images_root),
        flipbooks=FLIPBOOK_EFFECTS,
        spines=SPINE_EFFECTS,
        particles=PARTICLE_EFFECTS,
        transform_anims=TRANSFORM_ANIM_EFFECTS,
        dissolves=DISSOLVE_EFFECTS,
        sprite_shaders=SPRITE_SHADER_EFFECTS,
        loc_strings=VFX_LOC_STRINGS,
        rev=args.rev,
    )
    print(f"wrote {len(manifest['effects'])} effects to {args.images_root}/vfx/manifest.json")


if __name__ == "__main__":
    main()
