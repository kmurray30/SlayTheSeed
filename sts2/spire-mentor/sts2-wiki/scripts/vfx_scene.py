#!/usr/bin/env python3
"""Parse a Godot ``.tscn`` VFX scene into a renderable spec.

Only two shapes are understood, because only two are renderable without a
particle engine:

* **Flipbook** — a ``Sprite2D`` carrying ``NSpriteAnimator.cs``, which swaps
  its texture through ``_frames`` every ``1000/_fps`` ms and frees itself.
* **Spine** — a scene whose skeleton comes from a ``SkeletonDataResource``.

Anything else (the ``NVfxParticleSystem`` composites) returns ``None``. Those
are sub-project 2's; half-parsing them here would produce a spec that renders
as a single static sprite and looks like a bug rather than a gap.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

# `NSpriteAnimator.cs` field defaults. A scene omits a field precisely when it
# is using the C# default, so these are load-bearing, not fallbacks.
_DEFAULT_FPS = 15.0
_DEFAULT_LOOP = False
_DEFAULT_RANDOMIZE_ROTATION = False
_DEFAULT_ROTATION_RANGE = (0.0, 0.0)
_DEFAULT_SCALE = 1.0

# The two known Spine-VFX `ext_resource` type spellings. An explicit set, not
# a `.endswith("SkeletonDataResource")` suffix match: `MegaSkeletonDataResource`
# (`MegaCrit.Sts2.Core.Bindings.MegaSpine`) is a real, distinct decomp type
# used by monster rigs, and a suffix match would wrongly accept it as a Spine
# VFX scene instead of rejecting it to `None`.
_SPINE_SKELETON_TYPES = {"SkeletonDataResource", "SpineSkeletonDataResource"}

# A bracketed `[ext_resource ...]` line, attributes captured as one blob so
# `type`/`path`/`id` can be pulled out order-independently below — real
# scenes do not keep them in a fixed order (e.g. `uid=` can land between
# `id=` and the closing `]`).
_EXT_RESOURCE_LINE = re.compile(r"^\[ext_resource\s+(?P<attrs>[^\]]*)\]", re.MULTILINE)
# `(?<![A-Za-z_])` keeps these from matching inside another attribute's
# name — plain `id="..."` also matches the tail of `uid="..."` with no
# boundary between them (both are word characters), which silently pulls
# the uid string instead of the id.
_ATTR_TYPE = re.compile(r'(?<![A-Za-z_])type="(?P<v>[^"]+)"')
_ATTR_PATH = re.compile(r'(?<![A-Za-z_])path="res://(?P<v>[^"]+)"')
_ATTR_ID = re.compile(r'(?<![A-Za-z_])id="(?P<v>[^"]+)"')

_FRAMES = re.compile(
    r"^_frames\s*=\s*Array\[Texture2D\]\(\[(?P<body>.*?)\]\)",
    re.MULTILINE | re.DOTALL,
)
_EXT_REF = re.compile(r'ExtResource\("(?P<id>[^"]+)"\)')

# A `[node ...]` header; the root node is the one whose header carries no
# `parent=` attribute (Godot's convention for the scene root).
_NODE_HEADER = re.compile(r"^\[node\b(?P<attrs>[^\]]*)\]", re.MULTILINE)
_SECTION_HEADER = re.compile(r"^\[", re.MULTILINE)

_SCALE = re.compile(r"^scale\s*=\s*Vector2\(\s*(?P<x>-?[\d.e+-]+)\s*,", re.MULTILINE)
_FPS = re.compile(r"^_fps\s*=\s*(?P<v>-?[\d.e+-]+)", re.MULTILINE)
_LOOP = re.compile(r"^_loop\s*=\s*(?P<v>true|false)", re.MULTILINE)
_RANDOMIZE = re.compile(r"^_randomizeRotation\s*=\s*(?P<v>true|false)", re.MULTILINE)
_ROT_RANGE = re.compile(
    r"^_rotationRange\s*=\s*Vector2\(\s*(?P<a>-?[\d.e+-]+)\s*,\s*(?P<b>-?[\d.e+-]+)\s*\)",
    re.MULTILINE,
)
_ANIMATION = re.compile(r'^_animation\s*=\s*"(?P<v>[^"]+)"', re.MULTILINE)


@dataclass(frozen=True)
class FlipbookSpec:
    """An `NSpriteAnimator` scene: ordered frames played once at `fps`."""

    frames: list[str]
    fps: float
    loop: bool
    scale: float
    randomize_rotation: bool
    rotation_range: tuple[float, float]


@dataclass(frozen=True)
class SpineVfxSpec:
    """A Spine-rig VFX scene. The rig files are named by the caller.

    `scale` is the PRODUCT of the root node's `scale` and the `SpineSprite`
    child node's `scale` — Godot composes parent/child transforms, and real
    scenes author either or both (`vfx_scratch` authors both: 0.7 root *
    0.75 child = 0.525 rendered). Taking either node alone is wrong.
    """

    scale: float
    clip: str | None = None


def _scale(section: str) -> float:
    m = _SCALE.search(section)
    return float(m.group("x")) if m else _DEFAULT_SCALE


def _animation_clip(section: str) -> str | None:
    m = _ANIMATION.search(section)
    return m.group("v") if m else None


def _root_node_section(text: str) -> str:
    """Text spanning the root ``[node ...]`` header (the one with no
    ``parent=`` attribute) up to the next section header or end of file.

    The NODE-LOCAL field regexes (`scale`, `_fps`, `_loop`, `animation`) are
    run against this section: a scene commonly carries more than one
    `[node ...]` block, and a second node's fields must never shadow the
    root's. `_frames` is deliberately NOT one of them — it is searched over the
    whole document, because the frame array is authored on whichever node owns
    the `NSpriteAnimator` and a scene carries at most one.
    """
    for header in _NODE_HEADER.finditer(text):
        if "parent=" in header.group("attrs"):
            continue
        next_header = _SECTION_HEADER.search(text, header.end())
        end = next_header.start() if next_header is not None else len(text)
        return text[header.end() : end]
    return ""


def _spine_sprite_child_section(text: str) -> str | None:
    """Text spanning a non-root ``[node ... type="SpineSprite" ...]`` header.

    Every real Spine VFX scene authors its rig on a `SpineSprite` CHILD of
    the root (``parent="."``), not on the root itself — the root carries the
    script and `_animation`. `None` when no such child exists (a scene whose
    root node itself is the `SpineSprite`, as older/synthetic fixtures do —
    that scale is read via `_scale(root)` instead, so this returning `None`
    there is correct, not a miss).
    """
    for header in _NODE_HEADER.finditer(text):
        attrs = header.group("attrs")
        if "parent=" not in attrs:
            continue
        type_m = _ATTR_TYPE.search(attrs)
        if type_m is None or type_m.group("v") != "SpineSprite":
            continue
        next_header = _SECTION_HEADER.search(text, header.end())
        end = next_header.start() if next_header is not None else len(text)
        return text[header.end() : end]
    return None


def parse_vfx_scene(text: str) -> FlipbookSpec | SpineVfxSpec | None:
    """Parse ``.tscn`` source. ``None`` when the scene is not a shape we render."""
    # `ext_resource` declarations legitimately live in the scene header, so
    # this table is built from the whole document, not the root node section.
    ext: dict[str, str] = {}
    types: dict[str, str] = {}
    for m in _EXT_RESOURCE_LINE.finditer(text):
        attrs = m.group("attrs")
        path_m = _ATTR_PATH.search(attrs)
        id_m = _ATTR_ID.search(attrs)
        if path_m is None or id_m is None:
            continue
        ext[id_m.group("v")] = path_m.group("v")
        type_m = _ATTR_TYPE.search(attrs)
        if type_m is not None:
            types[id_m.group("v")] = type_m.group("v")

    root = _root_node_section(text)

    frames_m = _FRAMES.search(text)
    if frames_m is not None:
        # `_frames` order IS the animation order and does NOT track the
        # `ext_resource` declaration order — resolve through the id table
        # rather than sorting filenames. An id the table doesn't have (a
        # malformed or unresolvable `ext_resource` line) means this scene
        # cannot be rendered faithfully, so bail out to `None` rather than
        # raising or silently dropping the frame.
        frames: list[str] = []
        for ref in _EXT_REF.finditer(frames_m.group("body")):
            path = ext.get(ref.group("id"))
            if path is None:
                return None
            frames.append(path)
        if not frames:
            return None
        fps_m = _FPS.search(root)
        loop_m = _LOOP.search(root)
        rnd_m = _RANDOMIZE.search(root)
        rot_m = _ROT_RANGE.search(root)
        rnd_rotation = (rnd_m.group("v") == "true") if rnd_m else _DEFAULT_RANDOMIZE_ROTATION
        if rot_m:
            rot_range = (float(rot_m.group("a")), float(rot_m.group("b")))
        else:
            rot_range = _DEFAULT_ROTATION_RANGE
        return FlipbookSpec(
            frames=frames,
            fps=float(fps_m.group("v")) if fps_m else _DEFAULT_FPS,
            loop=(loop_m.group("v") == "true") if loop_m else _DEFAULT_LOOP,
            scale=_scale(root),
            randomize_rotation=rnd_rotation,
            rotation_range=rot_range,
        )

    # The real pck declares `type="SpineSkeletonDataResource"`; the plain
    # `SkeletonDataResource` spelling is also accepted (an older/synthetic
    # fixture shape). See `_SPINE_SKELETON_TYPES` for why this is an explicit
    # set rather than a suffix match.
    if any(t in _SPINE_SKELETON_TYPES for t in types.values()):
        child = _spine_sprite_child_section(text)
        child_scale = _scale(child) if child is not None else _DEFAULT_SCALE
        return SpineVfxSpec(
            # `round`: composing two authored decimals (e.g. 0.7 * 0.75)
            # lands on a binary-float artifact like 0.5249999999999999 —
            # correct in value, noisy in the committed manifest.
            scale=round(_scale(root) * child_scale, 6),
            clip=_animation_clip(root),
        )

    return None
