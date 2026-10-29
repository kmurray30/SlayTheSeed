#!/usr/bin/env python3
"""Parse a Godot ``.tscn`` particle scene into plain dataclasses.

Scope: the `NVfxParticleSystem` / `N*ImpactVfx` composites — a `Node2D` root
that restarts every `GPUParticles2D` descendant on a timer (see
`docs/superpowers/specs/2026-08-28-workbench-vfx-particles-design.md` §2). A
flipbook (`Sprite2D` + `NSpriteAnimator`) or Spine (`SkeletonDataResource`)
scene is `vfx_scene.py`'s job, not this module's — those return `None` here,
same as any other shape this parser doesn't understand.

Godot's instance-plus-override composition (§2.1) is implemented recursively:
an instanced child `.tscn` is parsed on its own (position (0, 0), scale
(1, 1), rotation 0) via the caller-supplied `load`, then the instancing
node's own transform is composed on top and its authored properties replace
(never merge with) the child's. `process_material` in particular is a full
replacement — an override that declares its own `ParticleProcessMaterial`
sub-resource does NOT inherit any field from the base scene's one; every
field the override omits falls back straight to Godot's own default.

A `CPUParticles2D` leaf is parsed as a `GPUParticles2D` whose process
material has been inlined. Godot's CPU implementation exposes the same
parameter set as `ParticleProcessMaterial`, but as properties on the node
itself, and its curve/gradient fields point STRAIGHT at a `Curve`/`Gradient`
sub-resource rather than at the `CurveTexture`/`GradientTexture1D` wrapper
the GPU path resolves through. This used to be an outright `ValueError`,
because none of the first 13 composites used one (spec, "Two questions the
survey answered outright"); `vfx_monster_death`'s debris burst is the first
consumer that does, so the leaf case now builds a real `EmitterSpec` (see
`_build_cpu_emitter`, which also lists what is deliberately NOT carried
across). A `CPUParticles2D` as a scene's ROOT still raises: that is not a
composite at all, and `parse_particle_scene` is then simply the wrong
parser. `LightOccluder2D` is skipped explicitly — the workbench overlay
doesn't render 2-D shadows.
"""

from __future__ import annotations

import math
import re
from collections.abc import Callable
from dataclasses import dataclass, replace
from typing import Any, TypeVar

from scripts.godot_curve import Curve, Gradient, parse_curve, parse_gradient

# ---------------------------------------------------------------------------
# .tscn section parsing — regex idioms ported verbatim from `vfx_scene.py`
# (`_EXT_RESOURCE_LINE` + the order-independent attribute regexes, and the
# `(?<![A-Za-z_])` guard below), both review findings from sub-project 1.
# ---------------------------------------------------------------------------

_EXT_RESOURCE_LINE = re.compile(r"^\[ext_resource\s+(?P<attrs>[^\]]*)\]", re.MULTILINE)
_SUB_RESOURCE_LINE = re.compile(r"^\[sub_resource\s+(?P<attrs>[^\]]*)\]", re.MULTILINE)
_NODE_HEADER = re.compile(r"^\[node\b(?P<attrs>[^\]]*)\]", re.MULTILINE)
_SECTION_HEADER = re.compile(r"^\[", re.MULTILINE)

# `(?<![A-Za-z_])` keeps these from matching inside another attribute's
# name — plain `id="..."` also matches the tail of `uid="..."` with no
# boundary between them (both are word characters), which silently pulls
# the uid string instead of the id. Applied to every quoted attribute here
# for the same reason, not just `id`.
_ATTR_TYPE = re.compile(r'(?<![A-Za-z_])type="(?P<v>[^"]+)"')
_ATTR_PATH = re.compile(r'(?<![A-Za-z_])path="res://(?P<v>[^"]+)"')
_ATTR_ID = re.compile(r'(?<![A-Za-z_])id="(?P<v>[^"]+)"')
_ATTR_NAME = re.compile(r'(?<![A-Za-z_])name="(?P<v>[^"]+)"')
_ATTR_PARENT = re.compile(r'(?<![A-Za-z_])parent="(?P<v>[^"]*)"')
# `instance=` has no other attribute whose name it could collide with, so it
# doesn't need the lookbehind guard.
_ATTR_INSTANCE = re.compile(r'instance=ExtResource\("(?P<v>[^"]+)"\)')

_T = TypeVar("_T")


def _or(value: _T | None, default: _T) -> _T:
    return default if value is None else value


def _scalar(section: str, name: str) -> float | None:
    m = re.search(rf"^{re.escape(name)}\s*=\s*(?P<v>-?[\d.eE+-]+)", section, re.MULTILINE)
    return float(m.group("v")) if m else None


def _int(section: str, name: str) -> int | None:
    m = re.search(rf"^{re.escape(name)}\s*=\s*(?P<v>-?\d+)", section, re.MULTILINE)
    return int(m.group("v")) if m else None


def _bool(section: str, name: str) -> bool | None:
    m = re.search(rf"^{re.escape(name)}\s*=\s*(?P<v>true|false)", section, re.MULTILINE)
    return (m.group("v") == "true") if m else None


def _quoted_str(section: str, name: str) -> str | None:
    """A `name = "value"` property, or `None` when the section has none."""
    m = re.search(rf'^{re.escape(name)}\s*=\s*"(?P<v>[^"]*)"', section, re.MULTILINE)
    return m.group("v") if m else None


def _vector2(section: str, name: str) -> tuple[float, float] | None:
    m = re.search(
        rf"^{re.escape(name)}\s*=\s*Vector2\(\s*(?P<x>-?[\d.eE+-]+)\s*,\s*(?P<y>-?[\d.eE+-]+)\s*\)",
        section,
        re.MULTILINE,
    )
    return (float(m.group("x")), float(m.group("y"))) if m else None


def _node_path_array(section: str, name: str) -> list[str]:
    """`name = [NodePath("a"), NodePath("b"), ...]` -> `["a", "b"]`.

    Used to read a `node_paths`-exported `Array<...>` field (e.g.
    `NScratchVfx.cs`'s `_anticipationParticles`/`_impactParticles`) — the
    field itself is declared once, up front, in the `node_paths=
    PackedStringArray(...)` node header; its VALUE (this) is a plain
    top-level property assignment naming NodePaths, same shape as any other
    array field in `.tscn` source.
    """
    m = re.search(rf"^{re.escape(name)}\s*=\s*\[(?P<body>.*?)\]", section, re.MULTILINE | re.DOTALL)
    if m is None:
        return []
    return re.findall(r'NodePath\("([^"]+)"\)', m.group("body"))


def _node_path_value(section: str, name: str) -> str | None:
    """`name = NodePath("a")` -> `"a"`.

    The single-value form of `_node_path_array`. Used to resolve an exported
    node reference (`_poofPivot = NodePath("poof_pivot")`) to the node's real
    declaration name — the EXPORT name and the NODE name differ, which is
    precisely what a name-munging renderer got wrong.
    """
    m = re.search(rf'^{re.escape(name)}\s*=\s*NodePath\("(?P<v>[^"]*)"\)', section, re.MULTILINE)
    return m.group("v") if m else None


def _vector3(section: str, name: str) -> tuple[float, float, float] | None:
    m = re.search(
        rf"^{re.escape(name)}\s*=\s*Vector3\(\s*(?P<x>-?[\d.eE+-]+)\s*,\s*"
        rf"(?P<y>-?[\d.eE+-]+)\s*,\s*(?P<z>-?[\d.eE+-]+)\s*\)",
        section,
        re.MULTILINE,
    )
    if m is None:
        return None
    return (float(m.group("x")), float(m.group("y")), float(m.group("z")))


def _color(section: str, name: str) -> tuple[float, float, float, float] | None:
    m = re.search(rf"^{re.escape(name)}\s*=\s*Color\((?P<body>[^)]*)\)", section, re.MULTILINE)
    if m is None:
        return None
    vals = [float(x) for x in m.group("body").split(",")]
    if len(vals) == 3:
        vals.append(1.0)
    return (vals[0], vals[1], vals[2], vals[3])


def _resource_ref_field(section: str, name: str) -> tuple[str, str] | None:
    """`name = SubResource("id")` / `name = ExtResource("id")` -> `(kind, id)`."""
    m = re.search(
        rf'^{re.escape(name)}\s*=\s*(?P<kind>Sub|Ext)Resource\("(?P<id>[^"]+)"\)',
        section,
        re.MULTILINE,
    )
    if m is None:
        return None
    return m.group("kind"), m.group("id")


def _ext_ref_path(section: str, name: str, ext_path: dict[str, str]) -> str | None:
    ref = _resource_ref_field(section, name)
    if ref is None or ref[0] != "Ext":
        return None
    return ext_path.get(ref[1])


def _sub_ref_id(section: str, name: str) -> str | None:
    ref = _resource_ref_field(section, name)
    if ref is None or ref[0] != "Sub":
        return None
    return ref[1]


# ---------------------------------------------------------------------------
# Godot 4.5.1 defaults. `ParticleProcessMaterial`'s live in its constructor,
# `scene/resources/particle_process_material.cpp:2364-2440` (tag
# `4.5.1-stable`); each constant below cites its own line so a future re-read
# of the source can spot-check any single value without re-deriving the
# whole table. `GPUParticles2D` node-level defaults are its own constructor,
# `scene/2d/gpu_particles_2d.cpp:982-1010`. `Node2D`/`CanvasItem` defaults are
# in-class member initializers, not constructor-body assignments.
# ---------------------------------------------------------------------------

_PPM_DEFAULT_DIRECTION = (1.0, 0.0, 0.0)  # particle_process_material.cpp:2368
_PPM_DEFAULT_SPREAD = 45.0  # particle_process_material.cpp:2369
_PPM_DEFAULT_FLATNESS = 0.0  # particle_process_material.cpp:2370
_PPM_DEFAULT_INITIAL_VELOCITY_MIN = 0.0  # particle_process_material.cpp:2371
_PPM_DEFAULT_INITIAL_VELOCITY_MAX = 0.0  # particle_process_material.cpp:2383
# PARAM_RADIAL_VELOCITY and PARAM_SCALE_OVER_VELOCITY are never explicitly
# set in the constructor; both min/max fall out of the zero-initialised
# `params_min`/`params_max` arrays (particle_process_material.h:305-306).
_PPM_DEFAULT_RADIAL_VELOCITY_MIN = 0.0
_PPM_DEFAULT_RADIAL_VELOCITY_MAX = 0.0
_PPM_DEFAULT_SCALE_OVER_VELOCITY_MAX = 0.0
_PPM_DEFAULT_GRAVITY = (0.0, -9.8, 0.0)  # particle_process_material.cpp:2418
_PPM_DEFAULT_DAMPING_MIN = 0.0  # particle_process_material.cpp:2377
_PPM_DEFAULT_DAMPING_MAX = 0.0  # particle_process_material.cpp:2389
_PPM_DEFAULT_SCALE_MIN = 1.0  # particle_process_material.cpp:2379
_PPM_DEFAULT_SCALE_MAX = 1.0  # particle_process_material.cpp:2391
_PPM_DEFAULT_LIFETIME_RANDOMNESS = 0.0  # particle_process_material.cpp:2419
# `params.animation_offset`, which the flipbook shaders read as
# `INSTANCE_CUSTOM.z` to give each particle its own starting frame of the
# sheet. Authored on 190 emitters across the pck's vfx scenes and never left
# at the default, so a port that ignores it animates every particle of an
# emitter in lockstep.
_PPM_DEFAULT_ANIM_OFFSET_MIN = 0.0  # particle_process_material.cpp:2382
_PPM_DEFAULT_ANIM_OFFSET_MAX = 0.0  # particle_process_material.cpp:2394
# `params.animation_speed`. Together with `anim_offset` above it forms the
# flipbook phase the shaders read as `INSTANCE_CUSTOM.z`:
# `CUSTOM.z = params.animation_offset + lifetime_percent * params.animation_speed`
# (particle_process_material.cpp:1147). A port that carries only the offset
# freezes every particle on its starting frame instead of playing the sheet.
_PPM_DEFAULT_ANIM_SPEED_MIN = 0.0  # particle_process_material.cpp:2381
_PPM_DEFAULT_ANIM_SPEED_MAX = 0.0  # particle_process_material.cpp:2393
# `PARAM_ANGLE` is the particle's INITIAL rotation and `PARAM_ANGULAR_VELOCITY`
# its spin. Both are DEGREES (the inspector hint on
# particle_process_material.cpp:2222 ends `,degrees`; :2234's is the same range
# in degrees per second), and both reach the transform through
# `CUSTOM.x = base_angle * degree_to_rad` (:1081).
_PPM_DEFAULT_ANGLE_MIN = 0.0  # particle_process_material.cpp:2378
_PPM_DEFAULT_ANGLE_MAX = 0.0  # particle_process_material.cpp:2390
_PPM_DEFAULT_ANGULAR_VELOCITY_MIN = 0.0  # particle_process_material.cpp:2372
_PPM_DEFAULT_ANGULAR_VELOCITY_MAX = 0.0  # particle_process_material.cpp:2384
# Emission volume. Everything below is POINT-shaped by default, which is what
# the port assumed before these fields existed.
#
# THE TWO NODE TYPES DO NOT SHARE AN ENUM, and the overlap is partial:
#
#     value  ParticleProcessMaterial (GPU)   CPUParticles2D
#     0      Point                           Point
#     1      Sphere                          Sphere
#     2      Sphere Surface                  Sphere Surface
#     3      Box            (Vector3)        Rectangle   (Vector2)
#     4      Points                          Points
#     5      Directed Points                 Directed Points
#     6      Ring                            -- does not exist --
#
# (particle_process_material.cpp:2209 vs cpu_particles_2d.cpp:1547.) 0-2 agree,
# so the one CPU emitter in scope — the monster-death debris, `emission_shape
# = 1` — is unambiguous. A CPU node authoring 3 would mean RECTANGLE with a
# Vector2 `emission_rect_extents`, NOT this Vector3 box, and nothing here may
# quietly treat the two as one field.
_PPM_DEFAULT_EMISSION_SHAPE = 0  # particle_process_material.cpp:2396 (POINT)
_PPM_DEFAULT_EMISSION_SPHERE_RADIUS = 1.0  # particle_process_material.cpp:2397
_PPM_DEFAULT_EMISSION_BOX_EXTENTS = (1.0, 1.0, 1.0)  # particle_process_material.cpp:2398
_PPM_DEFAULT_EMISSION_RING_AXIS = (0.0, 0.0, 1.0)  # particle_process_material.cpp:2399
_PPM_DEFAULT_EMISSION_RING_HEIGHT = 1.0  # particle_process_material.cpp:2400
_PPM_DEFAULT_EMISSION_RING_RADIUS = 1.0  # particle_process_material.cpp:2401
_PPM_DEFAULT_EMISSION_RING_INNER_RADIUS = 0.0  # particle_process_material.cpp:2402
_PPM_DEFAULT_EMISSION_RING_CONE_ANGLE = 90.0  # particle_process_material.cpp:2403
_PPM_DEFAULT_EMISSION_SHAPE_OFFSET = (0.0, 0.0, 0.0)  # particle_process_material.cpp:2404
_PPM_DEFAULT_EMISSION_SHAPE_SCALE = (1.0, 1.0, 1.0)  # particle_process_material.cpp:2405
# The `for` loop zeroing `particle_flags[PARTICLE_FLAG_MAX]` — that array has
# no in-class default initializer (particle_process_material.h:320), unlike
# `params_min`/`params_max` above, so this IS the default, not incidental.
_PPM_DEFAULT_PARTICLE_FLAG = False  # particle_process_material.cpp:2434-2436
_PPM_DEFAULT_TURBULENCE_ENABLED = False  # particle_process_material.cpp:2408
_PPM_DEFAULT_TURBULENCE_NOISE_STRENGTH = 1.0  # particle_process_material.cpp:2410
_PPM_DEFAULT_TURBULENCE_NOISE_SCALE = 9.0  # particle_process_material.cpp:2411
_PPM_DEFAULT_TURBULENCE_NOISE_SPEED = (0.0, 0.0, 0.0)  # particle_process_material.cpp:2409
_PPM_DEFAULT_TURBULENCE_NOISE_SPEED_RANDOM = 0.2  # particle_process_material.cpp:2412
_PPM_DEFAULT_TURBULENCE_INFLUENCE_MIN = 0.1  # particle_process_material.cpp:2413
_PPM_DEFAULT_TURBULENCE_INFLUENCE_MAX = 0.1  # particle_process_material.cpp:2414
_PPM_DEFAULT_TURBULENCE_INIT_DISPLACEMENT_MIN = 0.0  # particle_process_material.cpp:2415
_PPM_DEFAULT_TURBULENCE_INIT_DISPLACEMENT_MAX = 0.0  # particle_process_material.cpp:2416

_GP_DEFAULT_AMOUNT = 8  # gpu_particles_2d.cpp:995
_GP_DEFAULT_LIFETIME = 1.0  # gpu_particles_2d.cpp:997
_GP_DEFAULT_ONE_SHOT = False  # gpu_particles_2d.cpp:990,992
_GP_DEFAULT_EXPLOSIVENESS = 0.0  # gpu_particles_2d.cpp:1002
_GP_DEFAULT_RANDOMNESS = 0.0  # gpu_particles_2d.cpp:1003
# `set_fixed_fps` is called twice in the constructor (0, then 30); the
# second call is the one that sticks.
_GP_DEFAULT_FIXED_FPS = 30  # gpu_particles_2d.cpp:1008
_GP_DEFAULT_LOCAL_COORDS = False  # gpu_particles_2d.cpp:1005
_GP_DEFAULT_TRAIL_ENABLED = False  # gpu_particles_2d.cpp:1012
_GP_DEFAULT_TRAIL_LIFETIME = 0.3  # gpu_particles_2d.cpp:1013

# `CPUParticles2D`'s own defaults, read from Godot 4.5's
# `scene/2d/cpu_particles_2d.h:139-143` (member initialisers) and its
# constructor at `cpu_particles_2d.cpp:1644-1651`. Two of the three coincide
# with the GPU node's; `fixed_fps` does NOT, which is why this block exists
# instead of reusing `_GP_DEFAULT_*` for a node type that never promised to
# match.
#
# `0` here is not "unset" the way the GPU node's pre-constructor `0` is —
# `CPUParticles2D` genuinely ships at 0, meaning "simulate at the display
# frame rate" rather than on a fixed step. The web simulator maps 0 back to
# 30 (`simulate.ts:786`), so this changes no pixel today; it makes the
# MANIFEST honest, and keeps that 30 identifiable as the renderer's own
# approximation rather than something the game authored.
_CPU_DEFAULT_RANDOMNESS = 0.0  # cpu_particles_2d.h:139 (randomness_ratio)
_CPU_DEFAULT_FIXED_FPS = 0  # cpu_particles_2d.h:143
_CPU_DEFAULT_LOCAL_COORDS = False  # cpu_particles_2d.h:142, .cpp:1651
# The remaining four node-level fallbacks, which `_build_cpu_emitter` used to
# borrow from `_GP_DEFAULT_*`. All four happen to COINCIDE with the GPU node's
# — but "happens to coincide" is what made `fixed_fps` wrong, so each is now
# read from, and cited to, the CPU node's own source.
_CPU_DEFAULT_AMOUNT = 8  # cpu_particles_2d.cpp:1650 (set_amount(8))
_CPU_DEFAULT_LIFETIME = 1.0  # cpu_particles_2d.h:135
_CPU_DEFAULT_ONE_SHOT = False  # cpu_particles_2d.h:133
_CPU_DEFAULT_EXPLOSIVENESS = 0.0  # cpu_particles_2d.h:138 (explosiveness_ratio)

# `CPUParticles2D`'s gravity, widened to this port's 3-tuple. This is the one
# process-level fallback that does NOT coincide with the
# `ParticleProcessMaterial` value `_parse_cpu_process` used to borrow, and it
# differs in BOTH magnitude and sign: `_PPM_DEFAULT_GRAVITY` is
# `(0, -9.8, 0)`, a 3-D vector in metres, while a `CPUParticles2D` is a 2-D
# node whose gravity is a `Vector2` in screen pixels, where +Y points DOWN.
# Borrowing the 3-D value gave a fallback ~100x too small and pointing the
# opposite way on screen.
#
# Every OTHER `_PPM_DEFAULT_*` fallback still used by `_parse_cpu_process` was
# audited against the same two files and does coincide, so those are left
# borrowed rather than duplicated: `direction` `Vector2(1, 0)` (h:160),
# `spread` 45.0 (h:161), `lifetime_randomness` 0.0 (h:140), and — from the
# constructor's `set_param_min`/`set_param_max` pairs — initial velocity 0/0
# (cpp:1656, 1669), damping 0/0 (cpp:1662, 1675), scale 1/1 (cpp:1664, 1677),
# anim offset 0/0 (cpp:1667, 1680), and `particle_flags[i] = false`
# (cpp:1683).
_CPU_DEFAULT_GRAVITY = (0.0, 980.0, 0.0)  # cpu_particles_2d.h:185

_NODE2D_DEFAULT_ROTATION = 0.0  # scene/2d/node_2d.h:40
_CANVAS_ITEM_DEFAULT_SELF_MODULATE = (1.0, 1.0, 1.0, 1.0)  # scene/main/canvas_item.h:84
_CANVAS_ITEM_DEFAULT_Z_INDEX = 0  # scene/main/canvas_item.h:92

# `NVfxParticleSystem.cs`'s own `_lifetime` field default — only used when a
# composite root omits `_lifetime` entirely, which none of the 13 in-scope
# composites do (every one authors it).
_NVFX_DEFAULT_LIFETIME = 1.0  # NVfxParticleSystem.cs:53

_GENERIC_COMPOSITE_SCRIPT = "NVfxParticleSystem.cs"

#: Per-effect extras the bespoke scripts add on top of `NVfxParticleSystem`.
#: Every entry is verified against the decompiled script named in its comment;
#: nothing here is inferred from the scene. `pivot_scale`'s min/max are NOT
#: here — `_scaleRange` has no C# default (a bare `[Export] Vector2`), so it
#: is authored per-composite in the .tscn and read from there at parse time.
_SCRIPT_EXTRAS: dict[str, dict[str, Any]] = {
    # NPoisonImpactVfx.cs:101 — Scale = new Vector2(Randf() > 0.5 ? 1 : -1, 1)
    "NPoisonImpactVfx.cs": {"mirror_x": "horizontal_smoke_container"},
    # NSporeImpactVfx.cs:129 — _poofPivot.Scale = One * RandRange(_scaleRange)
    "NSporeImpactVfx.cs": {"pivot_scale_node": "_poofPivot"},
    # NScratchVfx.cs:120-133 — PlaySequence() restarts `_anticipationParticles`
    # at t=0, `await Cmd.Wait(0.1f)` (:127), THEN restarts `_impactParticles`,
    # screen-shakes, `await Cmd.Wait(2f)` (:133), and frees — a genuinely
    # staggered two-phase restart, not the generic "everything at once" model
    # every other composite in scope uses. `start_delay_groups` maps a root
    # `node_paths` group NAME (a `_anticipationParticles`/`_impactParticles`
    # -style `PackedStringArray`/`NodePath` array field on the root, per
    # `[EXPORT] Array<GpuParticles2D>` in the script) to the seconds its
    # members' restart is delayed by; a group with no entry here (or a script
    # with no `start_delay_groups` entry at all) leaves its emitters at the
    # `EmitterSpec` default of 0.0. `lifetime` overrides `_NVFX_DEFAULT_
    # LIFETIME`: this script has no `_lifetime` export (unlike every other
    # composite in scope) — its total on-screen time is read directly off the
    # two `Cmd.Wait` calls, 0.1 + 2.0 = 2.1s.
    "NScratchVfx.cs": {
        "start_delay_groups": {"_impactParticles": 0.1},
        "lifetime": 2.1,
    },
}

#: Whether a composite script's `Create(...)` accepts a `Color` tint on ANY
#: overload — not "every bespoke script is tinted" (that heuristic was wrong:
#: `NPoisonImpactVfx` and `NWormyImpactVfx` have no `Color` parameter on
#: either overload; `NSmokePuffVfx` takes a `SmokePuffColor` ENUM, not a
#: `Color`, so it doesn't count either). Explicit per-script, cited against
#: `decompiled/v0.107.1/MegaCrit.Sts2.Core.Nodes.Vfx/`, one entry per script
#: actually used by the 13 in-scope composites — `vfx_slime_impact`,
#: `vfx_sandy_impact`, `vfx_bloody_impact` and `vfx_coin_explosion_regular`
#: have no bespoke class and use the generic `NVfxParticleSystem` (confirmed:
#: no `NSlimeImpactVfx`/`NSandyImpactVfx`/`NBloodyImpactVfx` class exists, and
#: `GremlinMerc.cs`/`HandOfGreed.cs` spawn `vfx_coin_explosion_regular` by
#: scene path via `VfxCmd`, not a bespoke `Create`). `vfx_scratch_impact` DOES
#: have a bespoke class — `NScratchVfx.cs` — a prior pass of this table
#: checked for a nonexistent `NScratchImpactVfx` and concluded (wrongly) that
#: no bespoke class existed; see `_SCRIPT_EXTRAS` for what `NScratchVfx.cs`
#: actually does. A script not in this table raises rather than silently
#: guessing — same reasoning as the `CPUParticles2D` raise.
_TINTED_SCRIPTS: dict[str, bool] = {
    _GENERIC_COMPOSITE_SCRIPT: False,
    # NPoisonImpactVfx.cs:76,91 — Create(Creature) / Create(Vector2): no Color.
    "NPoisonImpactVfx.cs": False,
    # NWormyImpactVfx.cs:94,110 — Create(Creature) / Create(Vector2, Vector2): no Color.
    "NWormyImpactVfx.cs": False,
    # NSmokePuffVfx.cs:95 — Create(Creature, SmokePuffColor): an enum, not a Color.
    "NSmokePuffVfx.cs": False,
    # NSporeImpactVfx.cs:94,110 — both Create overloads take a Color.
    "NSporeImpactVfx.cs": True,
    # NGaseousImpactVfx.cs:69,80,96 — every Create overload takes a Color.
    "NGaseousImpactVfx.cs": True,
    # NGoopyImpactVfx.cs:89 — Create(Vector2, Color); :73 Create(Creature) doesn't.
    "NGoopyImpactVfx.cs": True,
    # NFireBurstVfx.cs:108 — Create(Vector2, float, Color); :85,101 don't.
    "NFireBurstVfx.cs": True,
    # NScratchVfx.cs:77,92 — Create(Creature, bool) / Create(Vector2, bool):
    # neither overload takes a Color (the `bool` is `goingRight`, a caller-
    # supplied mirror flag the binding table has to carry — unlike
    # `NPoisonImpactVfx`'s `mirror_x`, it's a runtime call argument, not
    # scene data, so it isn't and can't be modeled as a `_SCRIPT_EXTRAS`
    # entry here).
    "NScratchVfx.cs": False,
    # NBlockSparkVfx.cs:71 — the only Create overload is Create(Creature): no Color.
    "NBlockSparkVfx.cs": False,
}


# ---------------------------------------------------------------------------
# Output dataclasses (spec §2.2). Fields are snake_case — R2 (binding):
# camelCase JSON serialization belongs to a later task, not here.
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class ProcessSpec:
    """A resolved `ParticleProcessMaterial`, defaults filled in per Godot 4.5.1."""

    direction: tuple[float, float, float] = _PPM_DEFAULT_DIRECTION
    spread: float = _PPM_DEFAULT_SPREAD
    flatness: float = _PPM_DEFAULT_FLATNESS
    initial_velocity_min: float = _PPM_DEFAULT_INITIAL_VELOCITY_MIN
    initial_velocity_max: float = _PPM_DEFAULT_INITIAL_VELOCITY_MAX
    radial_velocity_min: float = _PPM_DEFAULT_RADIAL_VELOCITY_MIN
    radial_velocity_max: float = _PPM_DEFAULT_RADIAL_VELOCITY_MAX
    radial_velocity_curve: Curve | None = None
    gravity: tuple[float, float, float] = _PPM_DEFAULT_GRAVITY
    damping_min: float = _PPM_DEFAULT_DAMPING_MIN
    damping_max: float = _PPM_DEFAULT_DAMPING_MAX
    damping_curve: Curve | None = None
    scale_min: float = _PPM_DEFAULT_SCALE_MIN
    scale_max: float = _PPM_DEFAULT_SCALE_MAX
    scale_curve: Curve | None = None
    scale_over_velocity_max: float = _PPM_DEFAULT_SCALE_OVER_VELOCITY_MAX
    scale_over_velocity_curve: Curve | None = None
    color_ramp: Gradient | None = None
    color_initial_ramp: Gradient | None = None
    alpha_curve: Curve | None = None
    lifetime_randomness: float = _PPM_DEFAULT_LIFETIME_RANDOMNESS
    # Godot's `PARAM_ANIM_OFFSET`. No scene in scope authors an
    # `anim_offset_curve` (checked across every vfx `.tscn` in the pck: zero
    # hits), so the per-lifetime curve modulation the shader supports is not
    # modelled and there is deliberately no field for it here.
    anim_offset_min: float = _PPM_DEFAULT_ANIM_OFFSET_MIN
    anim_offset_max: float = _PPM_DEFAULT_ANIM_OFFSET_MAX
    anim_speed_min: float = _PPM_DEFAULT_ANIM_SPEED_MIN
    anim_speed_max: float = _PPM_DEFAULT_ANIM_SPEED_MAX
    anim_speed_curve: Curve | None = None
    angle_min: float = _PPM_DEFAULT_ANGLE_MIN
    angle_max: float = _PPM_DEFAULT_ANGLE_MAX
    angle_curve: Curve | None = None
    angular_velocity_min: float = _PPM_DEFAULT_ANGULAR_VELOCITY_MIN
    angular_velocity_max: float = _PPM_DEFAULT_ANGULAR_VELOCITY_MAX
    angular_velocity_curve: Curve | None = None
    emission_shape: int = _PPM_DEFAULT_EMISSION_SHAPE
    emission_sphere_radius: float = _PPM_DEFAULT_EMISSION_SPHERE_RADIUS
    emission_box_extents: tuple[float, float, float] = _PPM_DEFAULT_EMISSION_BOX_EXTENTS
    emission_ring_axis: tuple[float, float, float] = _PPM_DEFAULT_EMISSION_RING_AXIS
    emission_ring_height: float = _PPM_DEFAULT_EMISSION_RING_HEIGHT
    emission_ring_radius: float = _PPM_DEFAULT_EMISSION_RING_RADIUS
    emission_ring_inner_radius: float = _PPM_DEFAULT_EMISSION_RING_INNER_RADIUS
    emission_ring_cone_angle: float = _PPM_DEFAULT_EMISSION_RING_CONE_ANGLE
    emission_shape_offset: tuple[float, float, float] = _PPM_DEFAULT_EMISSION_SHAPE_OFFSET
    emission_shape_scale: tuple[float, float, float] = _PPM_DEFAULT_EMISSION_SHAPE_SCALE
    particle_flag_align_y: bool = _PPM_DEFAULT_PARTICLE_FLAG
    particle_flag_rotate_y: bool = _PPM_DEFAULT_PARTICLE_FLAG
    particle_flag_disable_z: bool = _PPM_DEFAULT_PARTICLE_FLAG
    particle_flag_damping_as_friction: bool = _PPM_DEFAULT_PARTICLE_FLAG
    turbulence_enabled: bool = _PPM_DEFAULT_TURBULENCE_ENABLED
    turbulence_noise_strength: float = _PPM_DEFAULT_TURBULENCE_NOISE_STRENGTH
    turbulence_noise_scale: float = _PPM_DEFAULT_TURBULENCE_NOISE_SCALE
    turbulence_noise_speed: tuple[float, float, float] = _PPM_DEFAULT_TURBULENCE_NOISE_SPEED
    turbulence_noise_speed_random: float = _PPM_DEFAULT_TURBULENCE_NOISE_SPEED_RANDOM
    turbulence_influence_min: float = _PPM_DEFAULT_TURBULENCE_INFLUENCE_MIN
    turbulence_influence_max: float = _PPM_DEFAULT_TURBULENCE_INFLUENCE_MAX
    turbulence_initial_displacement_min: float = _PPM_DEFAULT_TURBULENCE_INIT_DISPLACEMENT_MIN
    turbulence_initial_displacement_max: float = _PPM_DEFAULT_TURBULENCE_INIT_DISPLACEMENT_MAX
    turbulence_influence_over_life_curve: Curve | None = None


@dataclass(frozen=True)
class EmitterSpec:
    """One `GPUParticles2D` leaf, with its process material resolved."""

    name: str
    texture: str  # res-relative path
    material: str | None  # res-relative .tres path
    amount: int
    lifetime: float
    one_shot: bool
    explosiveness: float
    randomness: float
    fixed_fps: int
    local_coords: bool
    rotation: float
    self_modulate: tuple[float, float, float, float]
    z_index: int
    offset: tuple[float, float]  # composed parent transform
    process: ProcessSpec
    # Seconds after the composite starts before this emitter restarts — 0.0
    # for every composite except `NScratchVfx.cs`'s staggered anticipation/
    # impact sequence (see `_SCRIPT_EXTRAS["NScratchVfx.cs"]`). Not authored
    # in the .tscn itself; set post-`_walk` from the root's `node_paths`
    # group arrays via a `_SCRIPT_EXTRAS["start_delay_groups"]` entry.
    start_delay: float = 0.0
    # The Spine SLOT this emitter is parented to (`SpineSlotNode.slot_name`),
    # or `None` for an emitter that hangs off an ordinary Node2D. A slot-
    # parented emitter's position FOLLOWS the rig's own animation rather than
    # sitting at a fixed offset from the effect root, so the renderer has to
    # read the slot each frame — see `kaiser_crab_boss_explosion.tscn`, whose
    # `ExplosionParticles` hangs off `ExplosionSlot` on `explosion_slot`
    # (GH #669). `None` for every composite in `PARTICLE_EFFECTS`.
    slot: str | None = None
    # `CanvasItemMaterial.blend_mode == 1` (Add) on the emitter's own material
    # SUB-resource. `MaterialSpec` models `ShaderMaterial` `.tres` ext-resources
    # only, so a blend-mode-only `CanvasItemMaterial` leaves `material` as
    # `None` and would otherwise lose the blend entirely — which for
    # `kaiser_crab_boss_explosion`'s ember burst is the difference between
    # glowing embers and grey dots (GH #669). Godot's enum:
    # 0 Mix / 1 Add / 2 Sub / 3 Mul / 4 PremultAlpha; only Add is carried,
    # because only Add occurs in scope and a silent default for the others
    # would be the same loss in a new coat.
    blend_add: bool = False
    # `GPUParticles2D.trail_enabled` / `.trail_lifetime`. Godot draws a
    # trail-enabled particle as a RIBBON through the positions it held over the
    # last `trail_lifetime` seconds, not as the single quad every other emitter
    # in scope gets. Carried because for `kaiser_crab_boss_explosion` it is the
    # whole effect: its embers are a 24x24 dot at `scale_min/max` 0.1-0.4 under
    # a 0.25 `SpineSprite`, so the quad alone is UNDER TWO SCREEN PIXELS, while
    # the ribbon its 2500-5000 initial velocity sweeps out over 0.05 s is a
    # 30-60 px streak (GH #669). No `PARTICLE_EFFECTS` composite enables
    # trails, so this is `False` for all 14 of them.
    #
    # `trail_sections` / `trail_section_subdivisions` are deliberately NOT
    # carried: they subdivide the ribbon, and this emitter's is 0.05 s long
    # under a 30 fps process rate, i.e. about one and a half process frames.
    # A subdivided ribbon and a straight segment differ by the trajectory's
    # curvature over 50 ms — at this emitter's 5000 peak `linear_accel` that is
    # 0.5*a*t^2 = 6 units, ~1.5 px on screen against a 30-60 px segment. Adding
    # them would mean carrying a per-particle position history the simulator
    # does not keep, to move a corner by a pixel.
    trail_enabled: bool = False
    trail_lifetime: float = _GP_DEFAULT_TRAIL_LIFETIME
    # Whether this emitter is a DESCENDANT of the node `CompositeSpec
    # .pivot_scale` / `.mirror_x` names — i.e. whether that bespoke transform
    # actually applies to it. Resolved here, from the real scene graph, because
    # the manifest carries no parent/child edge and a renderer that tries to
    # infer it from names gets it wrong: `vfx_spore_impact`'s pivot node
    # `poof_pivot` has exactly ONE child (`vfx_spore_impact_poof_flipbook`),
    # but the composite also contains a ROOT-level `vfx_poof` that any
    # substring rule matches too.
    under_pivot_scale: bool = False
    under_mirror_x: bool = False
    # Whether this leaf is a `CPUParticles2D` rather than a `GPUParticles2D`.
    # The two nodes take the SAME parameter set and this module resolves both
    # into one `ProcessSpec` -- but they do not compute the same particles
    # from it, and the difference is not a rounding one. The clearest case is
    # the emission sphere, which the renderer has to branch on:
    #
    #   GPU (`particle_process_material.cpp:645-648`): three draws, a 3D ball.
    #     s = rand*2-1; t = rand*TAU; p = rand
    #     pos = mix(vec3(0), vec3(R*sqrt(1-s*s)*cos t, ...*sin t, R*s), p)
    #   CPU (`cpu_particles_2d.cpp:893-897`): two draws, a flat disc, and
    #   uniform in RADIUS rather than in area.
    #     t = TAU*randf(); radius = R*randf()
    #     pos = Vector2(cos t, sin t) * radius
    #
    # So this is not bookkeeping: it names which of two different formulas a
    # consumer must use. It is also the reason the CPU parser reads only
    # `emission_shape` and `emission_sphere_radius` and not the rest of the
    # shape fields -- see `_parse_cpu_process`'s enum-divergence note.
    #
    # `vfx_monster_death`'s debris burst is the pck's one in-scope
    # `CPUParticles2D`, so today this is True exactly once.
    cpu: bool = False


@dataclass(frozen=True)
class CompositeSpec:
    """An `NVfxParticleSystem` / `N*ImpactVfx` scene."""

    lifetime: float
    tinted: bool  # the script takes a tint argument
    emitters: list[EmitterSpec]
    # The two per-effect randomisers the bespoke scripts add on top of the
    # generic "restart everything" behaviour. Data, not code — see the
    # spec's "Why this is tractable" item 1.
    mirror_x: str | None  # node name whose Scale.x flips on a coin flip
    pivot_scale: tuple[str, float, float] | None  # (node, min, max)


# ---------------------------------------------------------------------------
# Curve / gradient resolution — a process-material field points at a
# `CurveTexture`/`GradientTexture1D` sub-resource, which itself points at the
# `Curve`/`Gradient` (Task 2's `godot_curve.parse_curve`/`parse_gradient`).
# ---------------------------------------------------------------------------


def _resolve_curve_texture(ref_id: str, sub_section: dict[str, str]) -> Curve | None:
    """Resolve a `CurveTexture`/`CurveXYZTexture` sub-resource id to its `Curve`.

    Only the first channel of an XYZ texture is resolved (`curve_x`) — no
    field in this sub-project's scope (spec §2.2) authors per-axis process
    curves, so falling back to the single-channel `curve` property both
    texture types expose is enough.
    """
    tex_section = sub_section.get(ref_id)
    if tex_section is None:
        return None
    curve_id = _sub_ref_id(tex_section, "curve") or _sub_ref_id(tex_section, "curve_x")
    if curve_id is None:
        return None
    curve_section = sub_section.get(curve_id)
    if curve_section is None:
        return None
    return parse_curve(f'[sub_resource type="Curve"]\n{curve_section}')


def _curve_field(section: str, name: str, sub_section: dict[str, str]) -> Curve | None:
    ref = _sub_ref_id(section, name)
    if ref is None:
        return None
    return _resolve_curve_texture(ref, sub_section)


def _direct_curve_field(section: str, name: str, sub_section: dict[str, str]) -> Curve | None:
    """A field naming a `Curve` sub-resource with NO `CurveTexture` in between.

    `CPUParticles2D` stores its ramps as the resources themselves
    (`scale_amount_curve = SubResource("Curve_6prs8")`), where the GPU path's
    `ParticleProcessMaterial` stores a `CurveTexture` that wraps one. Same
    baked result, one fewer hop — so this is `_curve_field` minus the
    indirection, not a different parse.
    """
    ref = _sub_ref_id(section, name)
    if ref is None:
        return None
    body = sub_section.get(ref)
    if body is None:
        return None
    return parse_curve(f'[sub_resource type="Curve"]\n{body}')


def _direct_gradient_field(section: str, name: str, sub_section: dict[str, str]) -> Gradient | None:
    """`_direct_curve_field`'s gradient twin — a `Gradient` sub-resource named
    without the `GradientTexture1D` wrapper the GPU path goes through."""
    ref = _sub_ref_id(section, name)
    if ref is None:
        return None
    body = sub_section.get(ref)
    if body is None:
        return None
    return parse_gradient(f'[sub_resource type="Gradient"]\n{body}')


def _gradient_field(section: str, name: str, sub_section: dict[str, str]) -> Gradient | None:
    ref = _sub_ref_id(section, name)
    if ref is None:
        return None
    tex_section = sub_section.get(ref)
    if tex_section is None:
        return None
    gradient_id = _sub_ref_id(tex_section, "gradient")
    if gradient_id is None:
        return None
    gradient_section = sub_section.get(gradient_id)
    if gradient_section is None:
        return None
    return parse_gradient(f'[sub_resource type="Gradient"]\n{gradient_section}')


# ---------------------------------------------------------------------------
# ShaderMaterial `.tres` parsing (spec §4.1).
#
# A `GPUParticles2D`'s `material` names a `.tres` ShaderMaterial, which is
# where the COLOURISATION lives — the per-emitter `ParticleProcessMaterial`
# above carries none of it. Two uniforms matter:
#
#   `shader_parameter/lut` — a `GradientTexture1D` the fragment shader indexes
#   by the texture's own grayscale value: `texture(lut, tex.rr) * vertex_color`
#   in every one of the family's `.gdshader`s. 37 of the 48 material-bearing
#   emitters carry a NON-identity lut, so treating it as white renders poison
#   grey rather than green and the blunt impact core white rather than orange.
#
#   `frame_count` / `flipbook_size` / `flipbook_curve` — a sprite sheet indexed
#   over the particle's lifetime. 24 of 48 emitters are flipbooks of 4 to 20
#   frames; without these they draw as a single static frame.
#
# Both reach their real resource through one level of indirection
# (`GradientTexture1D.gradient`, `CurveTexture.curve`), which `_gradient_field`
# and `_curve_field` already resolve.
# ---------------------------------------------------------------------------

_RESOURCE_SECTION = re.compile(r"^\[resource\]", re.MULTILINE)


@dataclass(frozen=True)
class MaterialSpec:
    """One `ShaderMaterial` `.tres`, reduced to the uniforms this port reads."""

    path: str  # res-relative .tres path
    shader: str  # `.gdshader` basename, e.g. "vfx_flipbook_shader"
    lut: Gradient | None
    frame_count: float | None
    flipbook_size: tuple[float, float] | None
    flipbook_curve: Curve | None
    # `mix()`-gated extras. Carried rather than dropped so a reader can see the
    # gap is bounded: measured across every material in use, erosion is enabled
    # on 0 emitters and hue-shift on exactly 3 (`vfx_fire_burst_flipbook`).
    use_erosion: bool
    use_hue_shift: bool


def _shader_param(name: str) -> str:
    return f"shader_parameter/{name}"


def parse_material(text: str, path: str) -> MaterialSpec:
    """Parse a `ShaderMaterial` `.tres` into a `MaterialSpec`.

    `path` is the res-relative path the caller loaded, kept on the spec so the
    manifest can still name the material it came from.
    """
    ext_path, sub_section = _parse_resource_tables(text)
    m = _RESOURCE_SECTION.search(text)
    if m is None:
        raise ValueError(f"{path}: no [resource] section")
    section = text[m.end() :]

    shader_path = _ext_ref_path(section, "shader", ext_path)
    if shader_path is None:
        raise ValueError(f"{path}: [resource] has no `shader = ExtResource(...)`")
    shader = shader_path.rsplit("/", 1)[-1].removesuffix(".gdshader")

    return MaterialSpec(
        path=path,
        shader=shader,
        lut=_gradient_field(section, _shader_param("lut"), sub_section),
        frame_count=_scalar(section, _shader_param("frame_count")),
        flipbook_size=_vector2(section, _shader_param("flipbook_size")),
        flipbook_curve=_curve_field(section, _shader_param("flipbook_curve"), sub_section),
        # Authored as floats (`0.0`/`1.0`) because they are `mix()` weights in
        # the shader, not bools.
        use_erosion=bool(_or(_scalar(section, _shader_param("use_erosion_texture")), 0.0)),
        use_hue_shift=bool(_or(_scalar(section, _shader_param("use_hue_shift")), 0.0)),
    )


def _parse_process_material(section: str, sub_section: dict[str, str]) -> ProcessSpec:
    return ProcessSpec(
        direction=_or(_vector3(section, "direction"), _PPM_DEFAULT_DIRECTION),
        spread=_or(_scalar(section, "spread"), _PPM_DEFAULT_SPREAD),
        flatness=_or(_scalar(section, "flatness"), _PPM_DEFAULT_FLATNESS),
        initial_velocity_min=_or(
            _scalar(section, "initial_velocity_min"), _PPM_DEFAULT_INITIAL_VELOCITY_MIN
        ),
        initial_velocity_max=_or(
            _scalar(section, "initial_velocity_max"), _PPM_DEFAULT_INITIAL_VELOCITY_MAX
        ),
        radial_velocity_min=_or(
            _scalar(section, "radial_velocity_min"), _PPM_DEFAULT_RADIAL_VELOCITY_MIN
        ),
        radial_velocity_max=_or(
            _scalar(section, "radial_velocity_max"), _PPM_DEFAULT_RADIAL_VELOCITY_MAX
        ),
        radial_velocity_curve=_curve_field(section, "radial_velocity_curve", sub_section),
        gravity=_or(_vector3(section, "gravity"), _PPM_DEFAULT_GRAVITY),
        damping_min=_or(_scalar(section, "damping_min"), _PPM_DEFAULT_DAMPING_MIN),
        damping_max=_or(_scalar(section, "damping_max"), _PPM_DEFAULT_DAMPING_MAX),
        damping_curve=_curve_field(section, "damping_curve", sub_section),
        scale_min=_or(_scalar(section, "scale_min"), _PPM_DEFAULT_SCALE_MIN),
        scale_max=_or(_scalar(section, "scale_max"), _PPM_DEFAULT_SCALE_MAX),
        scale_curve=_curve_field(section, "scale_curve", sub_section),
        scale_over_velocity_max=_or(
            _scalar(section, "scale_over_velocity_max"), _PPM_DEFAULT_SCALE_OVER_VELOCITY_MAX
        ),
        scale_over_velocity_curve=_curve_field(section, "scale_over_velocity_curve", sub_section),
        color_ramp=_gradient_field(section, "color_ramp", sub_section),
        color_initial_ramp=_gradient_field(section, "color_initial_ramp", sub_section),
        alpha_curve=_curve_field(section, "alpha_curve", sub_section),
        anim_offset_min=_or(_scalar(section, "anim_offset_min"), _PPM_DEFAULT_ANIM_OFFSET_MIN),
        anim_offset_max=_or(_scalar(section, "anim_offset_max"), _PPM_DEFAULT_ANIM_OFFSET_MAX),
        anim_speed_min=_or(_scalar(section, "anim_speed_min"), _PPM_DEFAULT_ANIM_SPEED_MIN),
        anim_speed_max=_or(_scalar(section, "anim_speed_max"), _PPM_DEFAULT_ANIM_SPEED_MAX),
        anim_speed_curve=_curve_field(section, "anim_speed_curve", sub_section),
        angle_min=_or(_scalar(section, "angle_min"), _PPM_DEFAULT_ANGLE_MIN),
        angle_max=_or(_scalar(section, "angle_max"), _PPM_DEFAULT_ANGLE_MAX),
        angle_curve=_curve_field(section, "angle_curve", sub_section),
        angular_velocity_min=_or(
            _scalar(section, "angular_velocity_min"), _PPM_DEFAULT_ANGULAR_VELOCITY_MIN
        ),
        angular_velocity_max=_or(
            _scalar(section, "angular_velocity_max"), _PPM_DEFAULT_ANGULAR_VELOCITY_MAX
        ),
        angular_velocity_curve=_curve_field(section, "angular_velocity_curve", sub_section),
        emission_shape=_or(_int(section, "emission_shape"), _PPM_DEFAULT_EMISSION_SHAPE),
        emission_sphere_radius=_or(
            _scalar(section, "emission_sphere_radius"), _PPM_DEFAULT_EMISSION_SPHERE_RADIUS
        ),
        emission_box_extents=_or(
            _vector3(section, "emission_box_extents"), _PPM_DEFAULT_EMISSION_BOX_EXTENTS
        ),
        emission_ring_axis=_or(
            _vector3(section, "emission_ring_axis"), _PPM_DEFAULT_EMISSION_RING_AXIS
        ),
        emission_ring_height=_or(
            _scalar(section, "emission_ring_height"), _PPM_DEFAULT_EMISSION_RING_HEIGHT
        ),
        emission_ring_radius=_or(
            _scalar(section, "emission_ring_radius"), _PPM_DEFAULT_EMISSION_RING_RADIUS
        ),
        emission_ring_inner_radius=_or(
            _scalar(section, "emission_ring_inner_radius"),
            _PPM_DEFAULT_EMISSION_RING_INNER_RADIUS,
        ),
        emission_ring_cone_angle=_or(
            _scalar(section, "emission_ring_cone_angle"), _PPM_DEFAULT_EMISSION_RING_CONE_ANGLE
        ),
        emission_shape_offset=_or(
            _vector3(section, "emission_shape_offset"), _PPM_DEFAULT_EMISSION_SHAPE_OFFSET
        ),
        emission_shape_scale=_or(
            _vector3(section, "emission_shape_scale"), _PPM_DEFAULT_EMISSION_SHAPE_SCALE
        ),
        lifetime_randomness=_or(
            _scalar(section, "lifetime_randomness"), _PPM_DEFAULT_LIFETIME_RANDOMNESS
        ),
        particle_flag_align_y=_or(
            _bool(section, "particle_flag_align_y"), _PPM_DEFAULT_PARTICLE_FLAG
        ),
        particle_flag_rotate_y=_or(
            _bool(section, "particle_flag_rotate_y"), _PPM_DEFAULT_PARTICLE_FLAG
        ),
        particle_flag_disable_z=_or(
            _bool(section, "particle_flag_disable_z"), _PPM_DEFAULT_PARTICLE_FLAG
        ),
        particle_flag_damping_as_friction=_or(
            _bool(section, "particle_flag_damping_as_friction"), _PPM_DEFAULT_PARTICLE_FLAG
        ),
        turbulence_enabled=_or(
            _bool(section, "turbulence_enabled"), _PPM_DEFAULT_TURBULENCE_ENABLED
        ),
        turbulence_noise_strength=_or(
            _scalar(section, "turbulence_noise_strength"), _PPM_DEFAULT_TURBULENCE_NOISE_STRENGTH
        ),
        turbulence_noise_scale=_or(
            _scalar(section, "turbulence_noise_scale"), _PPM_DEFAULT_TURBULENCE_NOISE_SCALE
        ),
        turbulence_noise_speed=_or(
            _vector3(section, "turbulence_noise_speed"), _PPM_DEFAULT_TURBULENCE_NOISE_SPEED
        ),
        turbulence_noise_speed_random=_or(
            _scalar(section, "turbulence_noise_speed_random"),
            _PPM_DEFAULT_TURBULENCE_NOISE_SPEED_RANDOM,
        ),
        turbulence_influence_min=_or(
            _scalar(section, "turbulence_influence_min"), _PPM_DEFAULT_TURBULENCE_INFLUENCE_MIN
        ),
        turbulence_influence_max=_or(
            _scalar(section, "turbulence_influence_max"), _PPM_DEFAULT_TURBULENCE_INFLUENCE_MAX
        ),
        turbulence_initial_displacement_min=_or(
            _scalar(section, "turbulence_initial_displacement_min"),
            _PPM_DEFAULT_TURBULENCE_INIT_DISPLACEMENT_MIN,
        ),
        turbulence_initial_displacement_max=_or(
            _scalar(section, "turbulence_initial_displacement_max"),
            _PPM_DEFAULT_TURBULENCE_INIT_DISPLACEMENT_MAX,
        ),
        turbulence_influence_over_life_curve=_curve_field(
            section, "turbulence_influence_over_life_curve", sub_section
        ),
    )


# ---------------------------------------------------------------------------
# Scene-document parsing: nodes + resource tables.
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class _NodeInfo:
    name: str
    type: str | None
    parent: str | None
    instance_id: str | None
    section: str


def _iter_nodes(text: str) -> list[_NodeInfo]:
    nodes = []
    for m in _NODE_HEADER.finditer(text):
        attrs = m.group("attrs")
        name_m = _ATTR_NAME.search(attrs)
        if name_m is None:
            continue
        type_m = _ATTR_TYPE.search(attrs)
        parent_m = _ATTR_PARENT.search(attrs)
        instance_m = _ATTR_INSTANCE.search(attrs)
        next_header = _SECTION_HEADER.search(text, m.end())
        end = next_header.start() if next_header is not None else len(text)
        nodes.append(
            _NodeInfo(
                name=name_m.group("v"),
                type=type_m.group("v") if type_m else None,
                parent=parent_m.group("v") if parent_m else None,
                instance_id=instance_m.group("v") if instance_m else None,
                section=text[m.end() : end],
            )
        )
    return nodes


def _node_path(node: _NodeInfo) -> str:
    """This node's own scene-relative path, in the same shape Godot writes
    into a child's `parent=` attribute (`.` for the root, plain names or
    slash-joined paths below it)."""
    if node.parent is None:
        return "."
    if node.parent == ".":
        return node.name
    return f"{node.parent}/{node.name}"


def _parse_resource_tables(text: str) -> tuple[dict[str, str], dict[str, str]]:
    """`ext_resource` id -> res-relative path, and `sub_resource` id -> body text.

    `ext_resource` declarations legitimately live in the scene header, so
    this is built from the whole document, same as `vfx_scene.py`.
    """
    ext_path: dict[str, str] = {}
    for m in _EXT_RESOURCE_LINE.finditer(text):
        attrs = m.group("attrs")
        path_m = _ATTR_PATH.search(attrs)
        id_m = _ATTR_ID.search(attrs)
        if path_m is None or id_m is None:
            continue
        ext_path[id_m.group("v")] = path_m.group("v")

    sub_section: dict[str, str] = {}
    for m in _SUB_RESOURCE_LINE.finditer(text):
        attrs = m.group("attrs")
        id_m = _ATTR_ID.search(attrs)
        if id_m is None:
            continue
        next_header = _SECTION_HEADER.search(text, m.end())
        end = next_header.start() if next_header is not None else len(text)
        sub_section[id_m.group("v")] = text[m.end() : end]

    return ext_path, sub_section


# ---------------------------------------------------------------------------
# Transform composition (spec §2.1): `Node2D` position/scale/rotation on
# intermediate containers compose multiplicatively into `EmitterSpec.offset`.
# Everything else on an override replaces rather than merges.
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class _Xform:
    """A `Transform2D`-equivalent: a 2x2 basis (`a, c` / `b, d` as columns
    x_axis=(a, b), y_axis=(c, d)) plus a translation. Godot composes global
    transforms by real matrix multiplication
    (`CanvasItem::get_global_transform`, `scene/main/canvas_item.cpp:179-194`
    — `new_global = parent.get_global_transform() * get_transform()`), NOT by
    summing rotation and multiplying scale component-wise: those two are only
    equivalent when every intermediate scale is uniform.  `S(s)·R(θ) !=
    R(θ)·S(s)` for non-uniform `s`, so decomposed accumulators silently give
    the wrong answer at 3+ levels of nesting with a non-uniform intermediate
    scale (fix round 2 — see task-3-report.md; not triggered by any of the
    13 in-scope composites today, but `NPoisonImpactVfx.cs:101` sets a
    non-uniform `(+-1, 1)` scale on `horizontal_smoke_container` at runtime,
    so the shape is real).
    """

    a: float = 1.0
    b: float = 0.0
    c: float = 0.0
    d: float = 1.0
    offset: tuple[float, float] = (0.0, 0.0)

    def then_local(self, local: "_Xform") -> "_Xform":
        """`self * local` — `local` (authored in this transform's local
        space) composed on top of `self`, matching
        `Transform2D::operator*=` (`core/math/transform_2d.cpp:198-212`) via
        `tdotx`/`tdoty` (`core/math/transform_2d.h:61-62`): `new_basis =
        self.basis @ local.basis`, `new_offset = self.basis @ local.offset +
        self.offset`.
        """
        ox = self.a * local.offset[0] + self.c * local.offset[1] + self.offset[0]
        oy = self.b * local.offset[0] + self.d * local.offset[1] + self.offset[1]
        return _Xform(
            a=self.a * local.a + self.c * local.b,
            b=self.b * local.a + self.d * local.b,
            c=self.a * local.c + self.c * local.d,
            d=self.b * local.c + self.d * local.d,
            offset=(ox, oy),
        )


def _local_xform(section: str) -> _Xform:
    """A `Node2D`'s local `Transform2D`, built from `position`/`scale`/
    `rotation` the way `Transform2D(real_t p_rot, const Size2 &p_scale,
    real_t p_skew, const Vector2 &p_pos)` does with `skew=0`
    (`core/math/transform_2d.cpp:107-113`) — nothing in this sub-project's
    scope authors Node2D skew.
    """
    pos = _or(_vector2(section, "position"), (0.0, 0.0))
    scale = _or(_vector2(section, "scale"), (1.0, 1.0))
    rotation = _or(_scalar(section, "rotation"), 0.0)
    cos_r, sin_r = math.cos(rotation), math.sin(rotation)
    return _Xform(
        a=cos_r * scale[0],
        b=sin_r * scale[0],
        c=-sin_r * scale[1],
        d=cos_r * scale[1],
        offset=pos,
    )


# ---------------------------------------------------------------------------
# Leaf emitter construction + override application.
# ---------------------------------------------------------------------------


def _build_emitter(
    node: _NodeInfo,
    xform: _Xform,
    ext_path: dict[str, str],
    sub_section: dict[str, str],
    start_delay: float = 0.0,
    under_pivot_scale: bool = False,
    under_mirror_x: bool = False,
    slot: str | None = None,
) -> EmitterSpec:
    section = node.section
    material_sub = _sub_ref_id(section, "material")
    blend_add = False
    if material_sub is not None:
        mat_section = sub_section.get(material_sub)
        if mat_section is not None:
            blend_add = _int(mat_section, "blend_mode") == 1
    process_id = _sub_ref_id(section, "process_material")
    process_section = sub_section.get(process_id) if process_id is not None else None
    process = (
        _parse_process_material(process_section, sub_section)
        if process_section is not None
        else ProcessSpec()
    )
    return EmitterSpec(
        name=node.name,
        texture=_or(_ext_ref_path(section, "texture", ext_path), ""),
        material=_ext_ref_path(section, "material", ext_path),
        amount=_or(_int(section, "amount"), _GP_DEFAULT_AMOUNT),
        lifetime=_or(_scalar(section, "lifetime"), _GP_DEFAULT_LIFETIME),
        one_shot=_or(_bool(section, "one_shot"), _GP_DEFAULT_ONE_SHOT),
        explosiveness=_or(_scalar(section, "explosiveness"), _GP_DEFAULT_EXPLOSIVENESS),
        randomness=_or(_scalar(section, "randomness"), _GP_DEFAULT_RANDOMNESS),
        fixed_fps=_or(_int(section, "fixed_fps"), _GP_DEFAULT_FIXED_FPS),
        local_coords=_or(_bool(section, "local_coords"), _GP_DEFAULT_LOCAL_COORDS),
        trail_enabled=_or(_bool(section, "trail_enabled"), _GP_DEFAULT_TRAIL_ENABLED),
        trail_lifetime=_or(_scalar(section, "trail_lifetime"), _GP_DEFAULT_TRAIL_LIFETIME),
        rotation=_or(_scalar(section, "rotation"), _NODE2D_DEFAULT_ROTATION),
        self_modulate=_or(_color(section, "self_modulate"), _CANVAS_ITEM_DEFAULT_SELF_MODULATE),
        z_index=_or(_int(section, "z_index"), _CANVAS_ITEM_DEFAULT_Z_INDEX),
        offset=xform.offset,
        process=process,
        start_delay=start_delay,
        under_pivot_scale=under_pivot_scale,
        under_mirror_x=under_mirror_x,
        slot=slot,
        blend_add=blend_add,
    )


#: Particle parameters this port DROPS, mapped to how often the pck authors
#: each one. The count is the number of authored occurrences across the 14
#: in-scope particle composites plus `vfx_monster_death`, and every scene and
#: `.tres` they instance — 61 resources in total. A `0` means the schema
#: cannot express the parameter but nothing in scope uses it; those rows are
#: kept so the record describes the SCHEMA gap, not just today's usage.
#:
#: Membership rule, so this stays checkable rather than hand-curated: a name
#: belongs here when it is a `CPUParticles2D` property that maps to Godot's
#: particle `Parameter` enum (`cpu_particles_2d.h`'s `PARAM_*`, each exposed
#: as a `_min`/`_max`/`_curve` triple), or to the emission-shape family, or
#: to the `CanvasItemMaterial` particles-animation family, or is one of the
#: node-level draw/simulation knobs listed last — AND `ProcessSpec` /
#: `EmitterSpec` has no field for it. Of the 12 `PARAM_*` entries this port
#: models four (initial linear velocity, damping, scale, anim offset); the
#: other eight are here.
#:
#: This is NOT a CPU-only gap. Every name is also a `ParticleProcessMaterial`
#: param or a `GPUParticles2D`/`CanvasItemMaterial` property, and the GPU path
#: drops it too — which is why the counts above are dominated by GPU
#: composites. Widening `ProcessSpec` would change the extracted output of all
#: 14 shipped composites; that is a sub-project 2 decision, not something the
#: monster-death port gets to make on the way past. Recorded here so the gap
#: is counted by name rather than silently absent.
#:
#: `test_the_dropped_parameter_record_is_complete` partitions every property
#: the real `vfx_monster_death` node authors across this table,
#: `_CPU_PROPS_NOT_PARAMETERS` and what the port actually reads, so a name
#: going missing here is a test failure rather than a stale comment. An
#: earlier revision of this record listed `CanvasItemMaterial
#: particles_anim_*` in its prose and none of the four names in its tuple.
_CPU_PARAMS_NOT_MODELLED: dict[str, int] = {
    # PARAM_ORBIT_VELOCITY
    "orbit_velocity_min": 2,
    "orbit_velocity_max": 2,
    "orbit_velocity_curve": 0,
    # PARAM_HUE_VARIATION
    "hue_variation_min": 10,
    "hue_variation_max": 11,
    "hue_variation_curve": 0,
    # PARAM_LINEAR_ACCEL / PARAM_RADIAL_ACCEL / PARAM_TANGENTIAL_ACCEL — the
    # three acceleration params. Zero in scope, but nothing in `ProcessSpec`
    # could carry them if a future effect used one.
    "linear_accel_min": 0,
    "linear_accel_max": 0,
    "linear_accel_curve": 0,
    "radial_accel_min": 0,
    "radial_accel_max": 0,
    "radial_accel_curve": 0,
    "tangential_accel_min": 0,
    "tangential_accel_max": 0,
    "tangential_accel_curve": 0,
    # Emission shape. `emission_shape` and `emission_sphere_radius` ARE modelled
    # now; these three are not. `emission_rect_extents` is the CPU node's
    # Vector2 RECTANGLE extents -- a different property AND a different enum
    # value from the GPU's Vector3 box (see `_PPM_DEFAULT_EMISSION_SHAPE`), so
    # it stays counted here rather than folded into the box field. The two
    # texture-driven shapes need an emission texture the port has no reader for.
    "emission_rect_extents": 0,
    "emission_points": 0,
    "emission_normals": 0,
    "emission_colors": 0,
    # `CanvasItemMaterial` sprite-sheet layout. `MaterialSpec` models the
    # `ShaderMaterial` flipbook fields but not these, so a debris flake draws
    # its whole 8-frame strip instead of one cell.
    "particles_animation": 10,
    "particles_anim_h_frames": 10,
    "particles_anim_v_frames": 10,
    "particles_anim_loop": 10,
    # Node-level draw / simulation knobs with no `EmitterSpec` field.
    # `speed_scale` multiplies the whole simulation rate (1.5 on the debris);
    # `color` is the flat per-particle tint `ProcessSpec` has only ramps for;
    # the `split_scale` trio is the per-axis scale a single `scale_curve`
    # cannot express.
    "speed_scale": 11,
    "color": 12,
    "split_scale": 0,
    "scale_curve_x": 0,
    "scale_curve_y": 0,
}

#: Properties a `CPUParticles2D` node can author that are NOT renderable
#: parameters, so their absence from `_CPU_PARAMS_NOT_MODELLED` is correct
#: rather than an omission. Named explicitly so the completeness test can
#: partition every authored property into exactly one bucket.
_CPU_PROPS_NOT_PARAMETERS = frozenset(
    {
        # Scene-tree / editor plumbing, not particle behaviour.
        "unique_name_in_owner",
        "script",
        "metadata/_edit_lock_",
        "metadata/_edit_group_",
        # `emitting` is the scene's authored START state, and for the one CPU
        # emitter in scope it is a lie the script immediately overwrites:
        # `vfx_monster_death.tscn` says `emitting = false` and
        # `NMonsterDeathVfx.cs:232` sets `Emitting = true`. Carrying the scene
        # value would stop the burst from ever playing.
        "emitting",
        # Deterministic-replay knobs. This port re-seeds per instance.
        "seed",
        "use_fixed_seed",
        "fract_delta",
        # Node-level knobs that are not `PARAM_*` entries — which is the only
        # thing this bucket asserts about them. Two of them DO change what you
        # see, so do not read this group as "cosmetically irrelevant":
        # `draw_order` picks the sort (index vs lifetime) and `preprocess`
        # pre-advances the simulation by N seconds before the first frame, so
        # a scene that authored either would render differently here. Nothing
        # in scope authors any of the four (the completeness test walks all 61
        # resources), which is why they sit outside the dropped-parameter
        # record rather than inside it. Authoring one moves it to
        # `_CPU_PARAMS_NOT_MODELLED`; it does not make it free.
        "draw_order",
        "texture_repeat",
        "show_behind_parent",
        "preprocess",
    }
)


def _parse_cpu_process(section: str, sub_section: dict[str, str]) -> ProcessSpec:
    """A `CPUParticles2D` node's own section -> `ProcessSpec`.

    The GPU twin of this is `_parse_process_material`, which reads a separate
    `ParticleProcessMaterial` sub-resource. FOUR things differ, and together
    they are the reason this is a second function rather than the same one:

    * `direction`/`gravity` are `Vector2` here and `Vector3` there (Godot's
      CPU 2-D particles are genuinely 2-D), so they are widened with `z = 0`.
    * `scale_min`/`scale_max`/`scale_curve` are spelled `scale_amount_min` /
      `scale_amount_max` / `scale_amount_curve`.
    * curve/gradient fields name their resource directly — `_direct_*_field`
      rather than `_curve_field`/`_gradient_field`.
    * the DEFAULTS are not all the `ParticleProcessMaterial` ones. `gravity`
      in particular is `_CPU_DEFAULT_GRAVITY` — see that constant for the
      audit of which `_PPM_DEFAULT_*` fallbacks were checked and do coincide.
      This bullet is the one an earlier revision of this docstring left out,
      while the `gravity` fallback was silently the 3-D value.

    Fields `CPUParticles2D` has no equivalent for at all keep their
    `ProcessSpec` defaults: `flatness`, `radial_velocity_*`,
    `scale_over_velocity_*`, the `turbulence_*` block, the
    `rotate_y`/`disable_z`/`damping_as_friction` particle flags, and
    `alpha_curve`. The last one was read here until it was noticed that
    `CPUParticles2D` has no such property — grepping Godot 4.5's
    `cpu_particles_2d.{h,cpp}` for `alpha_curve` and `PARAM_ALPHA` returns
    zero hits, and its `Parameter` enum has no alpha member; per-particle
    alpha lives in `color_ramp`'s A channel instead.

    See `_CPU_PARAMS_NOT_MODELLED` for the parameters that go the other way:
    authored on the node and dropped here.
    """
    direction2 = _vector2(section, "direction")
    gravity2 = _vector2(section, "gravity")
    return ProcessSpec(
        direction=((direction2[0], direction2[1], 0.0) if direction2 else _PPM_DEFAULT_DIRECTION),
        spread=_or(_scalar(section, "spread"), _PPM_DEFAULT_SPREAD),
        initial_velocity_min=_or(
            _scalar(section, "initial_velocity_min"), _PPM_DEFAULT_INITIAL_VELOCITY_MIN
        ),
        initial_velocity_max=_or(
            _scalar(section, "initial_velocity_max"), _PPM_DEFAULT_INITIAL_VELOCITY_MAX
        ),
        gravity=((gravity2[0], gravity2[1], 0.0) if gravity2 else _CPU_DEFAULT_GRAVITY),
        damping_min=_or(_scalar(section, "damping_min"), _PPM_DEFAULT_DAMPING_MIN),
        damping_max=_or(_scalar(section, "damping_max"), _PPM_DEFAULT_DAMPING_MAX),
        damping_curve=_direct_curve_field(section, "damping_curve", sub_section),
        scale_min=_or(_scalar(section, "scale_amount_min"), _PPM_DEFAULT_SCALE_MIN),
        scale_max=_or(_scalar(section, "scale_amount_max"), _PPM_DEFAULT_SCALE_MAX),
        scale_curve=_direct_curve_field(section, "scale_amount_curve", sub_section),
        color_ramp=_direct_gradient_field(section, "color_ramp", sub_section),
        color_initial_ramp=_direct_gradient_field(section, "color_initial_ramp", sub_section),
        anim_offset_min=_or(_scalar(section, "anim_offset_min"), _PPM_DEFAULT_ANIM_OFFSET_MIN),
        anim_offset_max=_or(_scalar(section, "anim_offset_max"), _PPM_DEFAULT_ANIM_OFFSET_MAX),
        anim_speed_min=_or(_scalar(section, "anim_speed_min"), _PPM_DEFAULT_ANIM_SPEED_MIN),
        anim_speed_max=_or(_scalar(section, "anim_speed_max"), _PPM_DEFAULT_ANIM_SPEED_MAX),
        anim_speed_curve=_direct_curve_field(section, "anim_speed_curve", sub_section),
        angle_min=_or(_scalar(section, "angle_min"), _PPM_DEFAULT_ANGLE_MIN),
        angle_max=_or(_scalar(section, "angle_max"), _PPM_DEFAULT_ANGLE_MAX),
        angle_curve=_direct_curve_field(section, "angle_curve", sub_section),
        angular_velocity_min=_or(
            _scalar(section, "angular_velocity_min"), _PPM_DEFAULT_ANGULAR_VELOCITY_MIN
        ),
        angular_velocity_max=_or(
            _scalar(section, "angular_velocity_max"), _PPM_DEFAULT_ANGULAR_VELOCITY_MAX
        ),
        angular_velocity_curve=_direct_curve_field(section, "angular_velocity_curve", sub_section),
        # Only the POINT/SPHERE/SPHERE_SURFACE values (0-2) mean the same thing
        # on both node types -- see `_PPM_DEFAULT_EMISSION_SHAPE`'s table. The
        # one CPU emitter in scope authors 1 (Sphere). A 3 here would be
        # RECTANGLE with a Vector2 `emission_rect_extents`, which this does not
        # read and must not be confused with the GPU's Vector3 box, so that
        # property stays in `_CPU_PARAMS_NOT_MODELLED`.
        emission_shape=_or(_int(section, "emission_shape"), _PPM_DEFAULT_EMISSION_SHAPE),
        emission_sphere_radius=_or(
            _scalar(section, "emission_sphere_radius"), _PPM_DEFAULT_EMISSION_SPHERE_RADIUS
        ),
        lifetime_randomness=_or(
            _scalar(section, "lifetime_randomness"), _PPM_DEFAULT_LIFETIME_RANDOMNESS
        ),
        particle_flag_align_y=_or(
            _bool(section, "particle_flag_align_y"), _PPM_DEFAULT_PARTICLE_FLAG
        ),
    )


def _build_cpu_emitter(
    node: _NodeInfo,
    xform: _Xform,
    ext_path: dict[str, str],
    sub_section: dict[str, str],
    start_delay: float = 0.0,
    under_pivot_scale: bool = False,
    under_mirror_x: bool = False,
    slot: str | None = None,
) -> EmitterSpec:
    """One `CPUParticles2D` leaf, as the same `EmitterSpec` the GPU path emits.

    Deliberately the SAME output shape rather than a parallel one: a
    CPUParticles2D is Godot's CPU implementation of the same particle system,
    so a renderer that already draws `EmitterSpec` draws this too.

    Two node-level caveats a reader should not have to rediscover:

    * `material` resolves to `None` here, and correctly so — the monster-death
      debris names a `CanvasItemMaterial` SUB-resource, not a `ShaderMaterial`
      `.tres` ext-resource, and `MaterialSpec` models the latter. Its
      `particles_anim_h_frames`/`_v_frames` sheet layout is therefore not
      carried (`_CPU_PARAMS_NOT_MODELLED`).
    * `randomness`, `fixed_fps` and `local_coords` fall back to the
      `_CPU_DEFAULT_*` values when unauthored. These were once an assumption
      borrowed from `_GP_DEFAULT_*` on the strength of the shared property
      names; they have since been read from Godot 4.5's own
      `cpu_particles_2d.h`/`.cpp` (see that block's comment). Worth knowing
      that the assumption was HALF wrong: `randomness` and `local_coords`
      do coincide with the GPU node's, but `fixed_fps` is 0 on a
      `CPUParticles2D` against the GPU node's 30. `vfx_monster_death` authors
      none of the three, so that difference was live.
    * `amount`, `lifetime`, `one_shot` and `explosiveness` also take
      `_CPU_DEFAULT_*` values. All four DO coincide with the GPU node's, but
      they are cited to the CPU node's own source anyway — "happens to
      coincide" is exactly the reasoning that made `fixed_fps` wrong.
    """
    section = node.section
    return EmitterSpec(
        name=node.name,
        texture=_or(_ext_ref_path(section, "texture", ext_path), ""),
        material=_ext_ref_path(section, "material", ext_path),
        amount=_or(_int(section, "amount"), _CPU_DEFAULT_AMOUNT),
        lifetime=_or(_scalar(section, "lifetime"), _CPU_DEFAULT_LIFETIME),
        one_shot=_or(_bool(section, "one_shot"), _CPU_DEFAULT_ONE_SHOT),
        explosiveness=_or(_scalar(section, "explosiveness"), _CPU_DEFAULT_EXPLOSIVENESS),
        randomness=_or(_scalar(section, "randomness"), _CPU_DEFAULT_RANDOMNESS),
        fixed_fps=_or(_int(section, "fixed_fps"), _CPU_DEFAULT_FIXED_FPS),
        local_coords=_or(_bool(section, "local_coords"), _CPU_DEFAULT_LOCAL_COORDS),
        rotation=_or(_scalar(section, "rotation"), _NODE2D_DEFAULT_ROTATION),
        self_modulate=_or(_color(section, "self_modulate"), _CANVAS_ITEM_DEFAULT_SELF_MODULATE),
        z_index=_or(_int(section, "z_index"), _CANVAS_ITEM_DEFAULT_Z_INDEX),
        offset=xform.offset,
        process=_parse_cpu_process(section, sub_section),
        cpu=True,
        start_delay=start_delay,
        under_pivot_scale=under_pivot_scale,
        under_mirror_x=under_mirror_x,
        slot=slot,
    )


def _apply_overrides(
    emitter: EmitterSpec, section: str, ext_path: dict[str, str], sub_section: dict[str, str]
) -> EmitterSpec:
    """Field-wise REPLACEMENT of an instanced child's root emitter by the
    instancing `[node ... instance=...]`'s own authored properties — not a
    merge. `process_material` in particular swaps in a whole fresh
    `ProcessSpec`, defaults and all, rather than overlaying onto the base
    scene's process material (spec §2.1)."""
    updates: dict[str, Any] = {}

    texture = _ext_ref_path(section, "texture", ext_path)
    if texture is not None:
        updates["texture"] = texture
    material = _ext_ref_path(section, "material", ext_path)
    if material is not None:
        updates["material"] = material
    amount = _int(section, "amount")
    if amount is not None:
        updates["amount"] = amount
    lifetime = _scalar(section, "lifetime")
    if lifetime is not None:
        updates["lifetime"] = lifetime
    one_shot = _bool(section, "one_shot")
    if one_shot is not None:
        updates["one_shot"] = one_shot
    explosiveness = _scalar(section, "explosiveness")
    if explosiveness is not None:
        updates["explosiveness"] = explosiveness
    randomness = _scalar(section, "randomness")
    if randomness is not None:
        updates["randomness"] = randomness
    fixed_fps = _int(section, "fixed_fps")
    if fixed_fps is not None:
        updates["fixed_fps"] = fixed_fps
    local_coords = _bool(section, "local_coords")
    if local_coords is not None:
        updates["local_coords"] = local_coords
    rotation = _scalar(section, "rotation")
    if rotation is not None:
        updates["rotation"] = rotation
    self_modulate = _color(section, "self_modulate")
    if self_modulate is not None:
        updates["self_modulate"] = self_modulate
    z_index = _int(section, "z_index")
    if z_index is not None:
        updates["z_index"] = z_index

    process_id = _sub_ref_id(section, "process_material")
    if process_id is not None:
        process_section = sub_section.get(process_id)
        updates["process"] = (
            _parse_process_material(process_section, sub_section)
            if process_section is not None
            else ProcessSpec()
        )

    if not updates:
        return emitter
    return replace(emitter, **updates)


# ---------------------------------------------------------------------------
# Tree walk.
# ---------------------------------------------------------------------------


def _walk(
    node: _NodeInfo,
    xform: _Xform,
    by_path: dict[str, _NodeInfo],
    ext_path: dict[str, str],
    sub_section: dict[str, str],
    load: Callable[[str], str | None],
    delay_by_name: dict[str, float] | None = None,
    delay: float = 0.0,
    pivot_target: str | None = None,
    mirror_target: str | None = None,
    under_pivot: bool = False,
    under_mirror: bool = False,
    slot: str | None = None,
) -> list[EmitterSpec]:
    # `delay_by_name` (constant for the whole walk — see `_SCRIPT_EXTRAS
    # ["start_delay_groups"]`) is re-checked against EVERY node's own name at
    # every level, not just at the top: a `node_paths` group names a node by
    # its declaration name in the COMPOSITE's own document (the instancing
    # node, e.g. `vfx_big_slash_impact_side_smoke_left`), which is a
    # different name from the instanced CHILD scene's internal root
    # (`vfx_big_slash_impact_side_smoke` — shared by both the `_left` and
    # `_right` instances). Re-checking at each level, falling back to the
    # inherited `delay` on a miss, resolves the match at the instancing
    # level and then correctly carries it through into the child scene's own
    # namespace, where the name won't match again (or, when a leaf scene's
    # internal name happens to equal its own instancing name — the common
    # case — matches again with the identical delay: a no-op either way).
    effective_delay = delay_by_name.get(node.name, delay) if delay_by_name else delay
    # "Is this node, or any ancestor of it, the bespoke transform's node?" —
    # monotone, which is exactly what "descendant of" means. Matched on the
    # node's declaration name at each level, the same way `delay_by_name` is.
    under_pivot = under_pivot or (pivot_target is not None and node.name == pivot_target)
    under_mirror = under_mirror or (mirror_target is not None and node.name == mirror_target)
    # A `SpineSlotNode` re-parents everything under it to a slot on the rig,
    # so its descendants follow that slot's animated transform instead of a
    # fixed offset (GH #669). Nearest-ancestor wins, the same monotone
    # inheritance `under_pivot` uses. `slot_name` is the Spine slot, NOT the
    # node's own declaration name.
    if node.type == "SpineSlotNode":
        slot = _quoted_str(node.section, "slot_name") or slot

    if node.type == "LightOccluder2D":
        return []
    # An authored `visible = false` on a node prunes it AND its descendants —
    # same convention `extract_bg_layers.py` already uses for room art.
    # `block_spark_vfx.tscn`'s `StandardSparks`/`SmallSparks` are exactly
    # this: `visible = false`, `emitting = false`, and absent from
    # `NBlockSparkVfx._particles` (the array `_Ready` restarts) — leftover
    # nodes the game never emits, not a second live effect. Excluding them
    # here is also what sidesteps `StandardSparks`' plain (non-shader)
    # `CanvasItemMaterial` — a blend-mode-only material this parser has no
    # reason to support since nothing live ever reaches it.
    if _bool(node.section, "visible") is False:
        return []
    node_xform = xform.then_local(_local_xform(node.section))

    if node.type == "CPUParticles2D":
        # Was an unconditional `raise` while no in-scope composite used one.
        # `vfx_monster_death`'s debris burst is the first that does, and a
        # CPUParticles2D is the same particle system with its process
        # material inlined — so it maps onto `EmitterSpec` rather than
        # earning a second shape. See `_build_cpu_emitter`.
        return [
            _build_cpu_emitter(
                node,
                node_xform,
                ext_path,
                sub_section,
                start_delay=effective_delay,
                under_pivot_scale=under_pivot,
                under_mirror_x=under_mirror,
                slot=slot,
            )
        ]

    if node.instance_id is not None:
        child_rel = ext_path.get(node.instance_id)
        if child_rel is None:
            return []
        child_text = load(child_rel)
        if child_text is None:
            return []
        c_ext_path, c_sub_section = _parse_resource_tables(child_text)
        c_nodes = _iter_nodes(child_text)
        c_by_path = {_node_path(n): n for n in c_nodes}
        c_root = next((n for n in c_nodes if n.parent is None), None)
        if c_root is None:
            return []
        emitters = _walk(
            c_root,
            node_xform,
            c_by_path,
            c_ext_path,
            c_sub_section,
            load,
            delay_by_name=delay_by_name,
            delay=effective_delay,
            pivot_target=pivot_target,
            mirror_target=mirror_target,
            under_pivot=under_pivot,
            under_mirror=under_mirror,
            slot=slot,
        )
        # Godot keys an instance override by the SAME relative NodePath as
        # the override line itself, which for every scene in this
        # sub-project's scope is the instanced scene's own root — the child
        # scenes here are single-emitter (a GPUParticles2D root) or, when
        # they're multi-emitter containers, carry no leaf-level overrides.
        if c_root.type == "GPUParticles2D" and emitters:
            emitters = list(emitters)
            emitters[0] = _apply_overrides(emitters[0], node.section, ext_path, sub_section)
        return emitters

    if node.type == "GPUParticles2D":
        return [
            _build_emitter(
                node,
                node_xform,
                ext_path,
                sub_section,
                start_delay=effective_delay,
                under_pivot_scale=under_pivot,
                under_mirror_x=under_mirror,
                slot=slot,
            )
        ]

    # Node2D (or an unrecognised container type): recurse into declared
    # children rather than treating an unknown type as a leaf.
    path = _node_path(node)
    emitters = []
    for child in by_path.values():
        if child.parent == path:
            emitters.extend(
                _walk(
                    child,
                    node_xform,
                    by_path,
                    ext_path,
                    sub_section,
                    load,
                    delay_by_name=delay_by_name,
                    delay=effective_delay,
                    pivot_target=pivot_target,
                    mirror_target=mirror_target,
                    under_pivot=under_pivot,
                    under_mirror=under_mirror,
                    slot=slot,
                )
            )
    return emitters


def parse_emitters(text: str, load: Callable[[str], str | None]) -> list[EmitterSpec]:
    """Every particle emitter under a scene's root, transforms composed.

    `parse_particle_scene` minus the `NVfxParticleSystem` layer — no
    `_lifetime`, no tint lookup, no bespoke mirror/pivot randomisers. That
    layer is what a COMPOSITE is; this exists for a scene whose particles are
    one component of an effect whose behaviour lives in its own C# script
    instead. `vfx_monster_death` is the first: its debris burst sits under a
    dissolving `SubViewport` sprite, and `NMonsterDeathVfx` — not
    `NVfxParticleSystem` — decides when either runs.

    Returns `[]` for a scene with no root or no emitters; unlike
    `parse_particle_scene` it makes no claim about the scene's overall shape,
    so there is nothing here to report as `None`.
    """
    ext_path, sub_section = _parse_resource_tables(text)
    nodes = _iter_nodes(text)
    root = next((n for n in nodes if n.parent is None), None)
    if root is None:
        return []
    by_path = {_node_path(n): n for n in nodes}
    return _walk(root, _Xform(), by_path, ext_path, sub_section, load)


def parse_particle_scene(text: str, load: Callable[[str], str | None]) -> CompositeSpec | None:
    """Parse `.tscn` source into a `CompositeSpec`.

    `None` when the scene isn't a particle composite (a `Node2D` root) — a
    flipbook or Spine scene, for instance; those are `vfx_scene.py`'s shapes.
    Raises `ValueError` when the ROOT is a `CPUParticles2D`: a bare emitter is
    not a composite, so it carries none of what this function returns (no
    `_lifetime`, no script, no tint), and asking for one is a caller mistake
    worth saying out loud. A `CPUParticles2D` deeper in the tree is a
    supported leaf — see the module docstring and `_build_cpu_emitter`.

    `load` resolves a res-path to `.tscn` source for an instanced child scene,
    so this function stays pure and a test can pass a dict-backed fake.
    """
    ext_path, sub_section = _parse_resource_tables(text)
    nodes = _iter_nodes(text)
    root = next((n for n in nodes if n.parent is None), None)
    if root is None:
        return None
    if root.type == "CPUParticles2D":
        raise ValueError(
            f"CPUParticles2D node {root.name!r} is not a particle composite — it is a bare "
            "emitter with no composite script, lifetime or tint. Use `parse_emitters` for a "
            "scene whose particles are one part of a larger, non-`NVfxParticleSystem` effect."
        )
    if root.type != "Node2D":
        return None

    by_path = {_node_path(n): n for n in nodes}

    script_path = _ext_ref_path(root.section, "script", ext_path)
    script_basename = script_path.rsplit("/", 1)[-1] if script_path else None
    extras = _SCRIPT_EXTRAS.get(script_basename or "", {})

    mirror_x = extras.get("mirror_x")
    pivot_scale = None
    pivot_node = extras.get("pivot_scale_node")
    if pivot_node is not None:
        scale_range = _or(_vector2(root.section, "_scaleRange"), (0.0, 0.0))
        pivot_scale = (pivot_node, scale_range[0], scale_range[1])

    lifetime = _or(
        _scalar(root.section, "_lifetime"), extras.get("lifetime", _NVFX_DEFAULT_LIFETIME)
    )
    if script_basename not in _TINTED_SCRIPTS:
        raise ValueError(
            f"composite script {script_basename!r} is not in _TINTED_SCRIPTS — "
            "add an entry cited against its decompiled Create(...) overloads "
            "before parsing a scene that uses it"
        )
    tinted = _TINTED_SCRIPTS[script_basename]

    # Keyed by NODE NAME AS DECLARED IN THIS DOCUMENT — the composite's own
    # direct-child instancing name (e.g. `vfx_big_slash_impact_side_smoke_
    # left`), not the instanced child scene's internal root name. `_walk`
    # re-resolves this at every level it descends into (see its docstring
    # comment), which is what makes two same-leaf-scene instances under
    # different instancing names (that `_left`/`_right` pair) each still
    # pick up their OWN group's delay correctly, despite ending up with the
    # identical `EmitterSpec.name` once inside the shared child scene.
    start_delay_groups: dict[str, float] = extras.get("start_delay_groups", {})
    delay_by_name: dict[str, float] = {}
    for group_name, group_delay in start_delay_groups.items():
        for node_name in _node_path_array(root.section, group_name):
            delay_by_name[node_name] = group_delay

    # Resolve each bespoke transform's node to its real declaration name. Both
    # `_SCRIPT_EXTRAS` values may be either an EXPORT name (`_poofPivot`, which
    # the scene maps to the node `poof_pivot`) or already a node name
    # (`horizontal_smoke_container`); try the NodePath indirection first and
    # fall back to treating the value as the node name itself.
    pivot_target = None
    if pivot_node is not None:
        pivot_target = _node_path_value(root.section, pivot_node) or pivot_node
    mirror_target = None
    if mirror_x is not None:
        mirror_target = _node_path_value(root.section, mirror_x) or mirror_x

    emitters = _walk(
        root,
        _Xform(),
        by_path,
        ext_path,
        sub_section,
        load,
        delay_by_name,
        pivot_target=pivot_target,
        mirror_target=mirror_target,
    )

    return CompositeSpec(
        lifetime=lifetime,
        tinted=tinted,
        emitters=emitters,
        mirror_x=mirror_x,
        pivot_scale=pivot_scale,
    )
