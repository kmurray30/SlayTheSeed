#!/usr/bin/env python3
"""Derive the combat-background layer manifest from the STS2 PCK's scene files.

The workbench renders a combat room as a stack of CSS background layers. Which
files go in that stack, in what order, and where each one sits on the shared
canvas all live in the game's Godot scenes:

    scenes/backgrounds/<title>/<title>_background.tscn   the room: an ordered
        list of `Layer_NN` / `Foreground` slots with static props interleaved
        between them by document order.
    scenes/backgrounds/<title>/layers/<title>_bg_NN_v.tscn   one variant of one
        parallax slot; `_fg_v` is a foreground variant.

`BackgroundAssets` (see `MegaCrit.Sts2.Core.Rooms`) buckets the layer scenes by
their `_bg_<n>_` index, picks ONE variant per bucket at random and ONE `_fg_`,
then `NCombatBackground` drops each pick into the matching slot node. This
script mirrors that grouping and emits the whole table as JSON so the frontend
can do the same roll without hand-transcribing 200 file names.

Geometry
--------
Every layer scene positions its art against ONE shared canvas, centred on the
game's 1920x1080 combat scene (`%BgContainer` in `combat_room.tscn` is anchored
0.5/0.5 with zero size, and `NCombatBackground.AddLayer` parents each layer
scene to a `Layer_NN` slot sitting on that point). A full-canvas layer is a
`TextureRect` anchored at 0.5 with symmetric offsets — ``-1382.4..1382.4`` x
``-648..648`` in the Overgrowth family, ``-1440.75..1440.75`` x
``-675.35..675.35`` in the Hive/Glory one. Both have the canvas's own 1920:900
aspect, so every scene normalises onto one 1920x900 px canvas and `Canvas.unit`
(1.44 or 1.50078) is the scale the room is DRAWN at — emitted per scene as
`RoomScene.scale`.

A node's placement is the composed Godot transform, not a rect: `control_local`
resolves a Control's rect the Godot way (``anchor * parent_size + offset``) and
then turns it about `pivot_offset` by its own `rotation`/`scale`, and
`node2d_local` does the same for a `Node2D`/`Sprite2D` from `position`. That
matters because a Control has no `flip`: these scenes mirror a prop with
``rotation = 3.14159``, which lands its rect on the OTHER side of its origin.
`place()` splits the result into an origin, two edge LENGTHS and the leftover
rotation/mirror, which is exactly what a CSS box under ``transform-origin: 0 0``
needs.

Also honoured: `show_behind_parent` (draw order), inherited `modulate` (tint and
alpha), and the particle emitters that hang ROOM art rather than VFX sparks —
Glory's cloud bank and the additive fog 14 of the 18 rooms drift between two
parallax slots, both sampled into static sheets by `_particle_layers`.

Verified against the three layers whose geometry was hand-measured into
`ROOM_LAYERS` before this script existed: underdocks ``03_b`` -> top 0 / h 518,
``04_a`` -> top 312.5 / h 572, and every full-canvas layer -> top 0 / h 900.

Usage (from `sts2-wiki/`)::

    uv run python -m scripts.extract_bg_layers \\
        ../sts2_binaries/v0.107.1/SlayTheSpire2.pck \\
        ../sts2-pycli/web/src/components/roomScenes.data.ts
"""

from __future__ import annotations

import argparse
import json
import math
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from scripts.extract_images import decode_ctex
from scripts.extract_rooms import make_reader, read_pck_index

# The canvas every scene's layers are normalised onto, in workbench px.
CANVAS_W = 1920.0
CANVAS_H = 900.0

# How many scene px one canvas px is, per authored family. A room's layers are
# authored against a rect CENTRED on the 1920x1080 combat scene, and the game
# ships exactly two sizes of that rect:
#
#   2764.8 x 1296.0  -> 1.44    Overgrowth, Underdocks and the bosses that
#                               borrow their layers (the Kin, Vantom, ...)
#   2881.5 x 1350.7  -> 1.50078 Hive, Glory and the bosses on those (Queen,
#                               Test Subject, Kaiser Crab, the Insatiable, ...)
#
# Both have the canvas's own 1920:900 aspect, so a scene's layers still land on
# one 1920x900 canvas — it is only the size that canvas is DRAWN at that differs,
# and that is what `RoomScene.scale` carries out to the frontend. It used to be
# one global 1.44 fitted against a single Overgrowth reference and applied to
# every room, which drew the Hive and Glory families ~4.2% small (GH #607).
DEFAULT_UNIT_SCALE = 1.44

# Art the workbench can actually serve: everything under `images/rooms/` is
# extracted by `extract_rooms.py` to `rooms/<same relpath>`. VFX sheets, shader
# noise and Spine rigs are not layers and are skipped.
ROOM_PREFIX = "images/rooms/"

# Sheets a room hangs IN THE AIR: additive fog puffs drifting on a
# `CPUParticles2D` between two parallax slots. They live under `images/vfx/`
# with the sparks and motes, but they are room art, not a hit effect — 14 of the
# 18 rooms hang `fog_sheet_1` and it is what lifts their far bands over the bare
# layer stack (GH #610: the Overgrowth's four teal puffs account for the whole
# +20% in G and B its canopy showed against a raw composite, in that colour's
# own 1:5.3:4.7 ratio). `extract_rooms.ROOM_AIR_SHEETS` serves them; the value
# here is the served relpath, like every other `src` in the manifest.
#
# Only the SHEETS. The point sparks on the same emitters (`light.png`, 658 of
# them across the rooms) are deliberately left out even though placing them is
# now correct: at ~70 additive elements in one room Chromium starts dropping
# `mix-blend-mode` on individual layers, and a DROPPED blend on this sheet is a
# black square across the room, because the art is a glow on opaque black.
# Three fog puffs per room stay well inside that; seventy sparks do not, and
# they were worth ~1 level. `.gs-motes` answers those instead.
AIR_SHEETS = {"images/vfx/fog_sheet_1.png": "air/fog_sheet_1.png"}
# The prefab every additive emitter in these scenes shares, for the ones that
# don't inline a `CanvasItemMaterial`.
SHARED_ADDITIVE_MAT = "themes/canvas_item_material_additive_shared.tres"

_EXT_RE = re.compile(
    r'\[ext_resource type="(?P<type>[^"]+)"[^\]]*?path="res://(?P<path>[^"]+)" id="(?P<id>[^"]+)"\]'
)
_NODE_RE = re.compile(
    r'\[node name="(?P<name>[^"]*)" type="(?P<type>[^"]*)"(?: parent="(?P<parent>[^"]*)")?[^\]]*\]'
)
_INSTANCE_RE = re.compile(
    r'\[node name="(?P<name>[^"]*)"(?: parent="(?P<parent>[^"]*)")? instance='
)
_VECTOR2_RE = re.compile(r"Vector2\(\s*([-\d.e+]+)\s*,\s*([-\d.e+]+)\s*\)")


@dataclass
class Node:
    """One `[node]` block: its header fields plus its scalar properties."""

    name: str
    type: str
    parent: str | None
    props: dict[str, str]
    path: str = ""
    children: list[Node] = field(default_factory=list)


def parse_scene(text: str) -> tuple[dict[str, str], list[Node]]:
    """Return ``(ext_resources, nodes_in_document_order)`` for a `.tscn`."""
    ext = {m.group("id"): m.group("path") for m in _EXT_RE.finditer(text)}

    nodes: list[Node] = []
    # Split on section headers so each node keeps only its OWN properties.
    blocks = re.split(r"(?m)^\[", text)
    for block in blocks:
        if not block.startswith("node "):
            continue
        header, _, body = block.partition("]")
        m = _NODE_RE.match("[" + header + "]")
        if m is None:
            # An instanced sub-scene (`instance=ExtResource(...)`) has no
            # `type=`. The background scenes use these for Spine rigs only,
            # which are not layers — record the node so the tree stays whole.
            mi = _INSTANCE_RE.match("[" + header + "]")
            if mi is None:
                continue
            node = Node(name=mi.group("name"), type="Instance", parent=mi.group("parent"), props={})
        else:
            node = Node(
                name=m.group("name"), type=m.group("type"), parent=m.group("parent"), props={}
            )
        for line in body.splitlines():
            key, sep, val = line.partition(" = ")
            if sep and key and " " not in key.strip():
                node.props[key.strip()] = val.strip()
        nodes.append(node)
    return ext, nodes


def parse_subresources(text: str) -> dict[str, dict[str, str]]:
    """Return ``{sub_resource id: its scalar properties}`` for a `.tscn`.

    Only the materials are read out of these today: a particle emitter says
    whether it draws additively, and how its sheet is cut into animation
    frames, on its `CanvasItemMaterial` rather than on the node.
    """
    out: dict[str, dict[str, str]] = {}
    for block in re.split(r"(?m)^\[", text):
        if not block.startswith("sub_resource "):
            continue
        header, _, body = block.partition("]")
        m = re.search(r'id="([^"]+)"', header)
        if m is None:
            continue
        props: dict[str, str] = {}
        for line in body.splitlines():
            key, sep, val = line.partition(" = ")
            if sep and key and " " not in key.strip():
                props[key.strip()] = val.strip()
        out[m.group(1)] = props
    return out


def build_tree(nodes: list[Node]) -> list[Node]:
    """Link nodes by their `parent` paths; return the document-ordered list."""
    by_path: dict[str, Node] = {}
    for node in nodes:
        if node.parent is None:
            node.path = "."
        elif node.parent == ".":
            node.path = node.name
        else:
            node.path = f"{node.parent}/{node.name}"
        by_path[node.path] = node
        if node.parent is not None:
            parent = by_path.get(node.parent)
            if parent is not None:
                parent.children.append(node)
    return nodes


def _f(node: Node, key: str, default: float = 0.0) -> float:
    raw = node.props.get(key)
    if raw is None:
        return default
    try:
        return float(raw)
    except ValueError:
        return default


def _vec2(node: Node, key: str, default: tuple[float, float]) -> tuple[float, float]:
    raw = node.props.get(key)
    if raw is None:
        return default
    m = _VECTOR2_RE.search(raw)
    return (float(m.group(1)), float(m.group(2))) if m else default


@dataclass(frozen=True)
class Canvas:
    """One scene's mapping from authored scene px to the shared 1920x900 canvas."""

    unit: float = DEFAULT_UNIT_SCALE

    @property
    def half_w(self) -> float:
        return CANVAS_W * self.unit / 2

    @property
    def half_h(self) -> float:
        return CANVAS_H * self.unit / 2

    def to_px(self, x: float, y: float) -> tuple[float, float]:
        return ((x + self.half_w) / self.unit, (y + self.half_h) / self.unit)


Rect = tuple[float, float, float, float]  # x, y, w, h in canvas units


class Xf:
    """A 2D affine transform: ``(x, y) -> (a*x + c*y + e, b*x + d*y + f)``.

    Godot builds a `CanvasItem`'s placement by composing its parent's transform
    with its own, so the walk needs the whole matrix and not the
    origin-and-uniform-scale pair this used to carry. A `Control` has no `flip`,
    so these scenes mirror a prop with ``rotation = 3.14159`` (and sometimes
    ``scale = Vector2(1, -1)`` on top, which nets out to a horizontal mirror) —
    and a rect rotated a half turn about its own origin lands on the OTHER side
    of it. Dropping that put the whole Overgrowth canopy in mid-air and 20 of
    the 309 placed layers off the 1920x900 canvas entirely, the Kin's and
    Ceremonial Beast's foreground variants among them (GH #607).
    """

    __slots__ = ("a", "b", "c", "d", "e", "f")

    def __init__(
        self,
        a: float = 1.0,
        b: float = 0.0,
        c: float = 0.0,
        d: float = 1.0,
        e: float = 0.0,
        f: float = 0.0,
    ) -> None:
        self.a, self.b, self.c, self.d, self.e, self.f = a, b, c, d, e, f

    def __matmul__(self, o: Xf) -> Xf:
        """``self @ o`` — apply `o` first, then `self` (Godot's own order)."""
        return Xf(
            self.a * o.a + self.c * o.b,
            self.b * o.a + self.d * o.b,
            self.a * o.c + self.c * o.d,
            self.b * o.c + self.d * o.d,
            self.a * o.e + self.c * o.f + self.e,
            self.b * o.e + self.d * o.f + self.f,
        )

    def apply(self, x: float, y: float) -> tuple[float, float]:
        return (self.a * x + self.c * y + self.e, self.b * x + self.d * y + self.f)

    @staticmethod
    def translate(x: float, y: float) -> Xf:
        return Xf(1.0, 0.0, 0.0, 1.0, x, y)

    @staticmethod
    def rotate(theta: float) -> Xf:
        return Xf(math.cos(theta), math.sin(theta), -math.sin(theta), math.cos(theta))

    @staticmethod
    def scale(sx: float, sy: float) -> Xf:
        return Xf(sx, 0.0, 0.0, sy)

    @staticmethod
    def skew(k: float) -> Xf:
        """Godot 4 shears the local y axis toward x by ``tan(skew)``."""
        return Xf(1.0, 0.0, math.tan(k), 1.0)


IDENTITY = Xf()

# What a background scene's root resolves against: a zero-size point at the
# canvas CENTRE. Godot places a `Layer_NN` slot (anchors_preset 0, no offsets)
# at its parent's origin, and the layer scene inside anchors at 0.5 with
# offsets -1382.4..1382.4 — which only spans the canvas if that origin is the
# centre. Confirmed in `combat_room.tscn`: `%BgContainer`, the node
# `NCombatRoom.SetUpBackground` parents the whole background scene to, is
# anchored 0.5/0.5 with zero size. The room scene's own props are positioned
# from the same point, so a bead strand at offset_top -300 hangs ABOVE the
# midline, not off the top edge.
ORIGIN_SIZE: tuple[float, float] = (0.0, 0.0)


def control_local(node: Node, parent_size: tuple[float, float]) -> tuple[Xf, tuple[float, float]]:
    """A Control's own transform and rect size, in its PARENT's local space.

    Godot: ``edge = anchor * parent_size + offset``, then the node's
    `rotation`/`scale` turn its rect about `pivot_offset` (which defaults to the
    rect's own top-left corner, and is what makes a half-turn move the art).
    A layer scene's root anchors at 0.5 with symmetric offsets, which is how a
    zero-sized `Layer_NN` slot still yields the full 2764.8x1296 canvas.
    """
    pw, ph = parent_size
    left = _f(node, "anchor_left") * pw + _f(node, "offset_left")
    right = _f(node, "anchor_right") * pw + _f(node, "offset_right")
    top = _f(node, "anchor_top") * ph + _f(node, "offset_top")
    bottom = _f(node, "anchor_bottom") * ph + _f(node, "offset_bottom")
    size = (right - left, bottom - top)
    px, py = _vec2(node, "pivot_offset", (0.0, 0.0))
    sx, sy = _vec2(node, "scale", (1.0, 1.0))
    local = (
        Xf.translate(left + px, top + py)
        @ Xf.rotate(_f(node, "rotation"))
        @ Xf.scale(sx, sy)
        @ Xf.translate(-px, -py)
    )
    return local, size


def node2d_local(node: Node) -> Xf:
    """A Node2D/Sprite2D's own transform, in its parent's local space."""
    x, y = _vec2(node, "position", (0.0, 0.0))
    sx, sy = _vec2(node, "scale", (1.0, 1.0))
    return (
        Xf.translate(x, y)
        @ Xf.rotate(_f(node, "rotation"))
        @ Xf.skew(_f(node, "skew"))
        @ Xf.scale(sx, sy)
    )


def sprite_quad(node: Node, tex_size: tuple[float, float]) -> Rect:
    """A Sprite2D's drawing quad in its OWN local space (before its transform).

    Sprites are centred on their origin unless ``centered = false``; `offset`
    shifts the quad inside that space, so both ride the node's own scale — which
    is exactly what composing the matrix does for us.
    """
    ox, oy = _vec2(node, "offset", (0.0, 0.0))
    w, h = tex_size
    if node.props.get("centered") == "false":
        return (ox, oy, w, h)
    return (ox - w / 2, oy - h / 2, w, h)


# A layer authored at the canvas's own size covers the room by construction, so
# its offset is scroll headroom for the shader that pans it (the Underdocks' sky
# is authored 194px right and 370px down of the canvas, and the game's narrower
# viewport never sees the gap). The workbench has no panning shader, so honouring
# that offset would just leave the room's top-left corner empty. Snap
# full-canvas-sized layers to the origin; place everything SHORTER exactly where
# the scene puts it, which is what the short bands (the Underdocks' dock, the
# Insatiable's sand floor) need.
SNAP_EPS = 2.0

# Below this the layer is a degenerate rect — a zero-sized `TextureRect` used as
# a naming host, not art. Nothing servable comes out of one.
MIN_EXTENT = 0.5


def place(
    canvas: Canvas, xf: Xf, quad: Rect, flip_h: bool = False, flip_v: bool = False
) -> dict[str, Any] | None:
    """Where a drawing quad lands on the 1920x900 px canvas.

    Returns the quad's own origin and its two edge LENGTHS in px, plus `t` — the
    normalised 2x2 linear part, i.e. the rotation/mirror left over once those
    lengths are factored out. That split is what the CSS side needs: a box of
    `w` x `h` at (`left`, `top`) under ``transform-origin: 0 0; transform:
    matrix(...t, 0, 0)`` reproduces the quad exactly, shear included, and `t` is
    simply absent for the (overwhelmingly common) axis-aligned layer — so every
    full-canvas act-room layer still emits the same four numbers it always did.
    """
    qx, qy, qw, qh = quad
    if qw <= 0 or qh <= 0:
        return None
    # `flip_h`/`flip_v` mirror the texture INSIDE the same rect, so they move
    # the quad's origin to the opposite corner and reverse that axis.
    if flip_h:
        qx, qw = qx + qw, -qw
    if flip_v:
        qy, qh = qy + qh, -qh
    ox, oy = xf.apply(qx, qy)
    # Images of the quad's own edge vectors, in canvas units.
    ux, uy = xf.a * qw, xf.b * qw
    vx, vy = xf.c * qh, xf.d * qh
    w = math.hypot(ux, uy)
    h = math.hypot(vx, vy)
    if w / canvas.unit < MIN_EXTENT or h / canvas.unit < MIN_EXTENT:
        return None
    left, top = canvas.to_px(ox, oy)
    out: dict[str, Any] = {
        "src": "",  # filled by the caller; keeps `src` first in the JSON
        "left": round(left, 2),
        "top": round(top, 2),
        "w": round(w / canvas.unit, 2),
        "h": round(h / canvas.unit, 2),
    }
    t = (ux / w, uy / w, vx / h, vy / h)
    if any(abs(t[i] - (1.0, 0.0, 0.0, 1.0)[i]) > 1e-4 for i in range(4)):
        out["t"] = [round(v, 6) for v in t]
    else:
        # Only an axis-aligned layer can be snapped: the snap is a statement
        # about the canvas's own edges, and a rotated rect's origin is a corner
        # that need not be its top-left.
        if abs(out["w"] - CANVAS_W) < SNAP_EPS:
            out["w"], out["left"] = CANVAS_W, 0.0
        if abs(out["h"] - CANVAS_H) < SNAP_EPS:
            out["h"], out["top"] = CANVAS_H, 0.0
        if abs(out["left"]) < SNAP_EPS:
            out["left"] = 0.0
        if abs(out["top"]) < SNAP_EPS:
            out["top"] = 0.0
    return out


class TextureSizes:
    """Original (pre-downscale) pixel dimensions of a PCK texture, memoised.

    `Sprite2D` sizes its art from the texture itself, so the manifest needs the
    SOURCE size — the extracted PNG may have been downscaled to 1920px.
    `TextureRect` sets ``expand_mode = 1`` (ignore size) throughout these
    scenes, so its own rect wins and no lookup is needed.
    """

    def __init__(self, index: dict[str, tuple[int, int]], read: Any) -> None:
        self._index = index
        self._read = read
        self._cache: dict[str, tuple[float, float]] = {}

    def get(self, image_path: str) -> tuple[float, float] | None:
        if image_path in self._cache:
            return self._cache[image_path]
        import_text = (self._read(image_path + ".import") or b"").decode("utf-8", "replace")
        paths: list[str] = re.findall(r'path[^=]*="res://([^"]+\.ctex)"', import_text)
        paths.sort(key=lambda p: 0 if "bptc" in p else 1 if "s3tc" in p else 2)
        for ctex in paths:
            data = self._read(ctex)
            img = decode_ctex(data) if data else None
            if img is not None:
                self._cache[image_path] = (float(img.width), float(img.height))
                return self._cache[image_path]
        return None


def _modulate(node: Node) -> tuple[float, float, float, float]:
    """This node's own `modulate`, which multiplies its whole subtree.

    `self_modulate` is deliberately NOT folded in here: Godot applies it to the
    node's own drawing only, and none of these scenes use it.
    """
    m = _COLOR_RE.match(node.props.get("modulate", ""))
    return (1.0, 1.0, 1.0, 1.0) if m is None else tuple(float(v) for v in m.groups())  # type: ignore[return-value]


def _tint(node: dict[str, Any], mod: tuple[float, float, float, float]) -> None:
    """Record the accumulated `modulate` on a placed layer, if it changes it."""
    r, g, b, a = mod
    if a < 0.999:
        node["alpha"] = round(a, 4)
    if min(r, g, b) < 0.999 or max(r, g, b) > 1.001:
        node["tint"] = [round(r, 4), round(g, 4), round(b, 4)]


def canvas_shift(canvas: Canvas, root: Node) -> Xf:
    """The whole-scene translation that pins a canvas-sized root to the canvas.

    A layer scene whose ROOT is authored at the canvas's own size covers the
    room by construction, so any offset on it is scroll headroom for the shader
    that pans it — the Underdocks' and the Waterfall Giant's skies are both
    authored 194px right and 370px down of the canvas, and the game's narrower
    viewport never sees the gap. The workbench has no panning shader, so
    honouring that offset would leave the room's top-left corner empty.

    It has to move the whole scene, not the one rect. The plate's siblings are
    positioned in ITS frame — the Waterfall Giant's rock shelf is a `Sprite2D`
    at y 1015 of a canvas 900 tall — so snapping the plate alone tore the room
    apart and dropped the shelf off the bottom (GH #607).
    """
    if root.type not in ("Control", "TextureRect", "ColorRect", "Panel"):
        return IDENTITY
    local, size = control_local(root, ORIGIN_SIZE)
    if abs(size[0] - 2 * canvas.half_w) > SNAP_EPS or abs(size[1] - 2 * canvas.half_h) > SNAP_EPS:
        return IDENTITY
    x, y = local.apply(0.0, 0.0)
    return Xf.translate(-canvas.half_w - x, -canvas.half_h - y)


# A particle field is sampled, not simulated: `amount` particles with ages
# spread evenly over `lifetime` IS the steady state of a uniform emitter, so
# stepping each sampled age through the authored motion gives the field a frame
# of the game would show. Capped, because a cheap CSS stand-in for 40 overlapping
# 438px cloud sheets is not worth 40 elements.
MAX_SHEETS = 12


def _particle_layers(
    canvas: Canvas,
    node: Node,
    xf: Xf,
    mod: tuple[float, float, float, float],
    src: str,
    tex_size: tuple[float, float],
    blend: str | None = None,
    frames: int = 1,
) -> list[dict[str, Any]]:
    """Static stand-ins for a particle emitter that hangs ROOM art.

    Most emitters in these scenes throw `images/vfx/` sparks and motes, which
    the workbench answers with its own CSS motes. Two families do not, and both
    ARE the room:

    * the CLOUD BANKS — Glory (and the two boss rooms built on it) hang
      `glory/cloud_sheet.png`, and the Architect's victory plate two more.
      Skipping them is why the Glory sky read as an empty gradient next to the
      game's (GH #607).
    * the FOG — 14 rooms hang `fog_sheet_1` additively between two parallax
      slots (`AIR_SHEETS`). It sits behind the near layers and in front of the
      far ones, so it lifts exactly the far bands and nothing else: a depth fade
      made of sprites, and the cause GH #610 went looking for a post-process to
      explain.

    Motion is the authored one: a particle spawns inside `emission_sphere_radius`
    and moves ``p0 + v0*t + g*t*t/2``. Only the ones whose sprite actually
    touches the canvas are kept — the Glory bank spawns 2800 units off the right
    edge and takes ~166 s to drift in, so most of its 40 are always off-screen,
    and the Overgrowth's fog accelerates off the right edge after ~45 s so two
    of its four puffs are always gone.

    `blend` is the emitter's `CanvasItemMaterial.blend_mode` ("add" or None) and
    `frames` its `particles_anim_v_frames`: an animated sheet is a COLUMN of
    frames and each particle draws one, so the sprite is `tex_h / frames` tall.
    """
    lifetime = _f(node, "lifetime", 1.0)
    amount = int(_f(node, "amount", 8.0))
    if lifetime <= 0 or amount <= 0:
        return []
    gx, gy = _vec2(node, "gravity", (0.0, 0.0))
    dx, dy = _vec2(node, "direction", (1.0, 0.0))
    speed = (_f(node, "initial_velocity_min") + _f(node, "initial_velocity_max")) / 2
    radius = _f(node, "emission_sphere_radius")
    # EMISSION_SHAPE_RECTANGLE = 3, spawning uniformly inside `+-extents`. The
    # Overgrowth's fireflies use a 2000-wide one: read as a point emitter they
    # all came out stacked in a single column up the middle of the room.
    rect = (
        _vec2(node, "emission_rect_extents", (0.0, 0.0))
        if node.props.get("emission_shape") == "3"
        else None
    )
    grow_min = _f(node, "scale_amount_min", 1.0)
    grow = (grow_min + _f(node, "scale_amount_max", grow_min)) / 2
    if grow <= 0:
        return []
    m = _COLOR_RE.match(node.props.get("color", ""))
    tint = tuple(float(v) for v in m.groups()) if m else (1.0, 1.0, 1.0, 1.0)
    mod = tuple(a * b for a, b in zip(mod, tint))  # type: ignore[assignment]

    sprite = (tex_size[0], tex_size[1] / max(1, frames))
    out: list[dict[str, Any]] = []
    for i in range(amount):
        t = lifetime * (i + 0.5) / amount
        # A deterministic walk stands in for the emission shape: same spread,
        # same result every run, no RNG to seed. A ring for the sphere, and the
        # two golden-ratio sequences for a rectangle — low-discrepancy, so a
        # dozen picks cover it evenly instead of clumping.
        angle = i * 2.399963  # the golden angle, so successive picks spread
        if rect is not None:
            ox = rect[0] * (2 * ((i + 0.5) * 0.6180339887 % 1.0) - 1)
            oy = rect[1] * (2 * ((i + 0.5) * 0.7548776662 % 1.0) - 1)
        else:
            ox, oy = radius * math.cos(angle), radius * math.sin(angle)
        px = ox + dx * speed * t + gx * t * t / 2
        py = oy + dy * speed * t + gy * t * t / 2
        quad = (
            px - sprite[0] * grow / 2,
            py - sprite[1] * grow / 2,
            sprite[0] * grow,
            sprite[1] * grow,
        )
        placed = place(canvas, xf, quad)
        if placed is None:
            continue
        if (
            placed["left"] >= CANVAS_W
            or placed["top"] >= CANVAS_H
            or placed["left"] + placed["w"] <= 0
            or placed["top"] + placed["h"] <= 0
        ):
            continue
        placed["src"] = src
        placed["vfx"] = True
        if blend:
            placed["blend"] = blend
        if frames > 1:
            # Which frame this particle shows: `anim_offset` spreads them over
            # the column, so walking it is the deterministic stand-in.
            placed["frame"] = [i % frames, frames]
        _tint(placed, mod)
        out.append(placed)
        if len(out) >= MAX_SHEETS:
            break
    return out


def _emitter_material(
    node: Node, ext: dict[str, str], mats: dict[str, dict[str, str]]
) -> tuple[str | None, int]:
    """``(blend, anim frames)`` for a particle emitter's `CanvasItemMaterial`.

    `blend_mode = 1` is ADD. Most emitters inline the material as a
    `sub_resource`; the rest share one `.tres` prefab, which is additive by the
    definition of its own name.
    """
    raw = node.props.get("material")
    if raw is None:
        return None, 1
    m = re.search(r'ExtResource\("([^"]+)"\)', raw)
    if m is not None:
        return ("add" if ext.get(m.group(1), "").endswith(SHARED_ADDITIVE_MAT) else None), 1
    m = re.search(r'SubResource\("([^"]+)"\)', raw)
    props = mats.get(m.group(1), {}) if m else {}
    blend = "add" if props.get("blend_mode") == "1" else None
    frames = 1
    if props.get("particles_animation") == "true":
        try:
            frames = max(1, int(props.get("particles_anim_v_frames", "1")))
        except ValueError:
            frames = 1
    return blend, frames


def collect_layers(
    canvas: Canvas,
    node: Node,
    ext: dict[str, str],
    sizes: TextureSizes,
    xf: Xf,
    size: tuple[float, float],
    mod: tuple[float, float, float, float] = (1.0, 1.0, 1.0, 1.0),
    mats: dict[str, dict[str, str]] | None = None,
) -> list[dict[str, Any]]:
    """Walk a subtree and emit one entry per servable room texture, in draw order.

    Draw order IS document order in Godot — with one exception this walk honours:
    a child with ``show_behind_parent`` draws BEFORE its parent's own art, which
    is how the Overgrowth canopy's back foliage sits behind the pillar plate
    rather than on top of it. Particles, shaders and Spine rigs carry no static
    texture the CSS stack can stand in for and are skipped; the few particle
    emitters that hang ROOM art (Glory's cloud sheets) come out of
    `scene_sheets()` instead.
    """
    own_xf, own_size = xf, size
    if node.type in ("Control", "TextureRect", "ColorRect", "Panel"):
        local, own_size = control_local(node, size)
        own_xf = xf @ local
    elif node.type in ("Node2D", "Sprite2D", "CPUParticles2D", "GPUParticles2D"):
        # An emitter is a Node2D too, and its `position` is where the room hangs
        # it: the Overgrowth's fog spawns at (-250.6, 409) off its layer's
        # corner, and ignoring that put the whole fog bank on the canvas's top
        # edge instead of across its middle distance.
        own_xf = xf @ node2d_local(node)
        own_size = (0.0, 0.0)

    if node.props.get("visible") == "false":
        return []
    mod = tuple(m * o for m, o in zip(mod, _modulate(node)))  # type: ignore[assignment]

    own: list[dict[str, Any]] = []
    tex = _ext_texture(node, ext)
    is_emitter = node.type in ("CPUParticles2D", "GPUParticles2D")
    # Room art, or one of the airborne sheets a room hangs on an emitter. An
    # air sheet counts as room art ONLY on an emitter: the same file is a hit
    # effect everywhere else in the game.
    if tex is None:
        src = None
    elif tex.startswith(ROOM_PREFIX):
        src = tex[len(ROOM_PREFIX) :]
    else:
        src = AIR_SHEETS.get(tex) if is_emitter else None
    if tex is not None and src is not None:
        placed: dict[str, Any] | None = None
        if node.type == "TextureRect":
            placed = place(
                canvas,
                own_xf,
                (0.0, 0.0, own_size[0], own_size[1]),
                node.props.get("flip_h") == "true",
                node.props.get("flip_v") == "true",
            )
        elif node.type == "Sprite2D" and _is_painted_sprite(own_xf):
            tex_size = sizes.get(tex)
            if tex_size is not None:
                placed = place(
                    canvas,
                    own_xf,
                    sprite_quad(node, tex_size),
                    node.props.get("flip_h") == "true",
                    node.props.get("flip_v") == "true",
                )
        elif is_emitter:
            tex_size = sizes.get(tex)
            if tex_size is not None:
                blend, frames = _emitter_material(node, ext, mats or {})
                own.extend(
                    _particle_layers(
                        canvas, node, own_xf, mod, src, tex_size, blend=blend, frames=frames
                    )
                )
        if placed is not None:
            placed["src"] = src
            _tint(placed, mod)
            own.append(placed)

    behind: list[dict[str, Any]] = []
    front: list[dict[str, Any]] = []
    for child in node.children:
        bin_ = behind if child.props.get("show_behind_parent") == "true" else front
        bin_.extend(collect_layers(canvas, child, ext, sizes, own_xf, own_size, mod, mats))

    out = behind + own + front
    # A layer scene often repeats its own art on an inner `clip_children` host
    # that masks shader sprites (the Underdocks' water reflections). That is one
    # painted layer, not two.
    deduped: list[dict[str, Any]] = []
    for entry in out:
        if not deduped or deduped[-1] != entry:
            deduped.append(entry)
    return deduped


def _is_painted_sprite(xf: Xf) -> bool:
    """Is this `Sprite2D` room art, or a room texture reused as a VFX surface?

    Painted props are drawn at their authored proportions — the Insatiable's
    sand floor at 1x, its four stumps at 0.5x. The shader waterfalls in the same
    scenes reuse `sand_pillar_base.png` as a stretched column (4x12, 3x9.6,
    3.36x12): a room texture, but not room art, and nothing the CSS layer stack
    can stand in for. Anisotropy is the tell, and it is a clean split — every
    prop is uniform, every column is 3-4x wider-than-tall in the wrong axis.
    """
    sx = math.hypot(xf.a, xf.b)
    sy = math.hypot(xf.c, xf.d)
    if sx == 0 or sy == 0:
        return False
    return 0.95 <= sx / sy <= 1.05


_COLOR_RE = re.compile(r"Color\(([\d.e+-]+), ([\d.e+-]+), ([\d.e+-]+), ([\d.e+-]+)\)")

# Maps a room's airborne-particle count to how much its air tints what is behind
# it. Calibrated on the one room we have a game screenshot of: the Insatiable
# (16386 particles) needs alpha 0.68 to land on the reference — measured, see
# `atmosphere()`. `AIR_KNEE` sets how fast a thinner room falls off it.
AIR_KNEE = 320.0
# The Insatiable is the thickest room in the game and the one we have a
# screenshot of, so its solved alpha is both the curve's anchor and its ceiling:
# no room's air is denser than the densest room we have measured.
AIR_MAX = 0.66
AIR_GAIN = AIR_MAX / math.log1p(16386 / AIR_KNEE)


def atmosphere(read: Any, title: str, index: dict[str, tuple[int, int]]) -> dict[str, Any] | None:
    """How this room's air colours what is behind it, or None if it is clear.

    A combat room's haze is entirely particle work in the game — the Insatiable
    hangs 16k sand motes between the camera and the Hive backdrop, which is why
    a game screenshot of that fight shows warm brown dunes under warm brown
    canopy while a straight layer stack shows dark green canopy. Measured on
    that screenshot, the scene's thirds run L=80/111/111 against our unhazed
    L=28/80/120 — the room is not a bit dark at the top, it is a different
    place. Compositing the room's own airborne tint over it closes that.

    Two things are read out of the scene, both authored:

    * the TINT — the area-and-alpha-weighted mean `modulate` of the sprites the
      room hangs in the air (the sand-fall columns, the mist sheets). Only three
      rooms tint airborne art at all, and they are exactly the three thick
      rooms; every other room returns None here and so keeps a clear sky. That
      is the point of sourcing it this way rather than from a threshold: a room
      the artists did not fill with weather cannot accidentally acquire any.
    * the STRENGTH — total live particle `amount`, through a log curve, so the
      Insatiable's 16k and the Kin boss's 12 do not land in the same place. The
      curve is pinned by measuring a render against the reference and solving
      for the alpha that lands on it; `AIR_KNEE` sets how fast a thinner room
      falls off that anchor.

    A pure white/greyscale `modulate` is skipped: it is an opacity fade, and
    carries no hue to tint anything with.
    """
    acc = [0.0, 0.0, 0.0]
    weight = 0.0
    particles = 0
    for path in sorted(p for p in index if p.startswith(f"scenes/backgrounds/{title}/")):
        if not path.endswith(".tscn"):
            continue
        text = (read(path) or b"").decode("utf-8", "replace")
        _, nodes = parse_scene(text)
        for node in nodes:
            if node.props.get("visible") == "false":
                continue
            if node.type in ("GPUParticles2D", "CPUParticles2D"):
                particles += int(_f(node, "amount", 8.0))
                continue
            if node.type not in ("Sprite2D", "TextureRect", "ColorRect"):
                continue
            m = _COLOR_RE.match(node.props.get("modulate", ""))
            if m is None:
                continue
            r, g, b, a = (float(v) for v in m.groups())
            if max(r, g, b) - min(r, g, b) < 0.02:
                continue  # greyscale: an opacity fade, no hue to carry
            sx, sy = _vec2(node, "scale", (1.0, 1.0))
            w = abs(sx * sy) * a
            acc = [acc[0] + r * w, acc[1] + g * w, acc[2] + b * w]
            weight += w
    if weight == 0.0 or particles == 0:
        return None
    strength = min(AIR_MAX, AIR_GAIN * math.log1p(particles / AIR_KNEE))
    return {
        "rgb": [round(c / weight * 255) for c in acc],
        "strength": round(strength, 4),
        "particles": particles,
    }


def _ext_texture(node: Node, ext: dict[str, str]) -> str | None:
    raw = node.props.get("texture")
    if raw is None:
        return None
    m = re.search(r'ExtResource\("([^"]+)"\)', raw)
    return ext.get(m.group(1)) if m else None


def scene_layers(canvas: Canvas, read: Any, path: str, sizes: TextureSizes) -> list[dict[str, Any]]:
    """Every servable room texture in one layer scene, back to front."""
    data = read(path)
    if data is None:
        return []
    text = data.decode("utf-8", "replace")
    ext, nodes = parse_scene(text)
    tree = build_tree(nodes)
    if not tree:
        return []
    return collect_layers(
        canvas,
        tree[0],
        ext,
        sizes,
        canvas_shift(canvas, tree[0]),
        ORIGIN_SIZE,
        mats=parse_subresources(text),
    )


def room_slots(canvas: Canvas, read: Any, path: str, sizes: TextureSizes) -> list[dict[str, Any]]:
    """The room scene's ordered slot list: bucket / fg placeholders and props.

    Document order is draw order, so a prop between `Layer_02` and `Layer_03`
    must render between those two picks — that is how the Insatiable's sand
    floor lands on top of the Hive backdrop but under the stumps.
    """
    data = read(path)
    if data is None:
        return []
    text = data.decode("utf-8", "replace")
    ext, nodes = parse_scene(text)
    mats = parse_subresources(text)
    tree = build_tree(nodes)
    if not tree:
        return []
    root = tree[0]
    if root.type in ("Control", "TextureRect", "ColorRect", "Panel"):
        root_local, root_size = control_local(root, ORIGIN_SIZE)
    else:
        root_local, root_size = node2d_local(root), ORIGIN_SIZE
    root_xf = canvas_shift(canvas, root) @ root_local
    root_mod = _modulate(root)

    slots: list[dict[str, Any]] = []
    seen: list[dict[str, Any]] = []
    for child in root.children:
        if child.name.startswith("Layer_") and child.type == "Control":
            slots.append({"kind": "bucket", "index": child.name[len("Layer_") :]})
        elif child.name == "Foreground":
            slots.append({"kind": "fg"})
        else:
            layers = [
                layer
                for layer in collect_layers(
                    canvas, child, ext, sizes, root_xf, root_size, root_mod, mats
                )
                if not _is_duplicate(layer, seen)
            ]
            if layers:
                slots.append({"kind": "prop", "name": child.name, "layers": layers})
                seen.extend(layers)
    return slots


def _is_duplicate(layer: dict[str, Any], seen: list[dict[str, Any]]) -> bool:
    """Has this art already been placed at (near enough) the same spot?

    The Insatiable's room draws its sand floor twice, one sprite a pixel off the
    other, to thicken the edge — one painted layer as far as CSS is concerned.
    Repeats of the SAME texture elsewhere on the canvas (the Ceremonial Beast's
    22 bead strands) are separate props and must survive.
    """
    return any(
        prev["src"] == layer["src"]
        and all(abs(prev[k] - layer[k]) < 4.0 for k in ("left", "top", "w", "h"))
        for prev in seen
    )


def scene_canvas(read: Any, title: str, index: dict[str, tuple[int, int]]) -> Canvas:
    """The scale THIS room authors its canvas at (`RoomScene.scale`).

    Read off the layer roots: a full-canvas layer is a `Control` anchored at the
    scene centre whose rect IS the canvas, so the modal root extent across a
    room's layer scenes is the room's canvas. The modal one, not the largest —
    a few rooms hang one extra-wide parallax plate (4239.4 units in the Hive),
    and a few hang short bands (the Underdocks' dock), and neither is the canvas.
    """
    sizes: dict[float, int] = {}
    for path in sorted(p for p in index if p.startswith(f"scenes/backgrounds/{title}/")):
        if not path.endswith(".tscn"):
            continue
        _, nodes = parse_scene((read(path) or b"").decode("utf-8", "replace"))
        tree = build_tree(nodes)
        if not tree or tree[0].type not in ("Control", "TextureRect", "ColorRect", "Panel"):
            continue
        _, size = control_local(tree[0], ORIGIN_SIZE)
        # The canvas keeps the 1920:900 aspect; anything else is a lone plate.
        if size[0] > 0 and abs(size[0] / max(size[1], 1e-6) - CANVAS_W / CANVAS_H) < 0.01:
            sizes[round(size[0], 1)] = sizes.get(round(size[0], 1), 0) + 1
    if not sizes:
        return Canvas()
    width = max(sizes, key=lambda w: (sizes[w], w))
    return Canvas(unit=round(width / CANVAS_W, 6))


def build_manifest(pck_path: str) -> dict[str, Any]:
    index = read_pck_index(pck_path)
    read = make_reader(pck_path, index)
    sizes = TextureSizes(index, read)

    titles = sorted(
        {
            p.split("/")[2]
            for p in index
            if p.startswith("scenes/backgrounds/") and "/layers/" in p and p.endswith(".tscn")
        }
    )

    scenes: dict[str, Any] = {}
    for title in titles:
        canvas = scene_canvas(read, title, index)
        buckets: dict[str, list[dict[str, Any]]] = {}
        fg: list[dict[str, Any]] = []
        prefix = f"scenes/backgrounds/{title}/layers/"
        for path in sorted(p for p in index if p.startswith(prefix) and p.endswith(".tscn")):
            name = Path(path).stem
            # A variant that paints NOTHING is still a variant: `underdocks_fg_b`
            # anchors its plate "Full Rect" against the zero-sized `Foreground`
            # slot, so the game rolls it and shows no foreground at all. Dropping
            # it here would shorten the bucket and re-deal every other roll, so
            # the empty list is kept and `paintsAnything()` looks past it.
            layers = scene_layers(canvas, read, path, sizes)
            if "_fg_" in name:
                fg.append({"id": name.split("_fg_")[1], "layers": layers})
            elif "_bg_" in name:
                suffix = name.split("_bg_")[1]
                buckets.setdefault(suffix.split("_")[0], []).append(
                    {"id": suffix, "layers": layers}
                )
        scenes[title] = {
            "scale": canvas.unit,
            "atmosphere": atmosphere(read, title, index),
            "slots": room_slots(
                canvas, read, f"scenes/backgrounds/{title}/{title}_background.tscn", sizes
            ),
            "buckets": {k: buckets[k] for k in sorted(buckets)},
            "fg": fg,
        }
    return {"canvas": {"w": CANVAS_W, "h": CANVAS_H}, "scenes": scenes}


def render_ts(manifest: dict[str, Any]) -> str:
    """Emit the manifest as a typed TS module the web app imports directly."""
    body = json.dumps(manifest, indent=2, sort_keys=False)
    return f"""// GENERATED — do not edit by hand.
//
// Regenerate from the game PCK (from `sts2-wiki/`):
//
//   uv run python -m scripts.extract_bg_layers \\
//       ../sts2_binaries/v0.107.1/SlayTheSpire2.pck \\
//       ../sts2-pycli/web/src/components/roomScenes.data.ts
//
// Source of truth: `scenes/backgrounds/<title>/` in the v0.107.1 PCK. See
// `sts2-wiki/scripts/extract_bg_layers.py` for the geometry derivation, and
// `roomScenes.ts` for how a scene is picked and its variants rolled.
import type {{ RoomManifest }} from "./roomScenes";

export const ROOM_MANIFEST: RoomManifest = {body} as const;
"""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pck_path")
    parser.add_argument("out_path", help="`.ts` for the web module, `.json` for raw JSON")
    args = parser.parse_args()

    manifest = build_manifest(args.pck_path)
    out = Path(args.out_path)
    if out.suffix == ".json":
        out.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    else:
        out.write_text(render_ts(manifest), encoding="utf-8")

    scenes = manifest["scenes"]
    props = sum(1 for s in scenes.values() for slot in s["slots"] if slot["kind"] == "prop")
    variants = sum(len(v) for s in scenes.values() for v in s["buckets"].values()) + sum(
        len(s["fg"]) for s in scenes.values()
    )
    print(f"{len(scenes)} scenes, {variants} layer variants, {props} static props -> {out}")
    for where, layer in _off_canvas(manifest):
        print(f"  off-canvas: {where} {layer['src']}")


def _off_canvas(manifest: dict[str, Any]) -> list[tuple[str, dict[str, Any]]]:
    """Layers the game itself never shows, reported so they stay KNOWN.

    A layer that misses the canvas entirely is almost always this script getting
    a transform wrong, and that is how GH #607 was found. The two that survive
    the fix are the game's own: `the_kin_boss_fg_b` and its Ceremonial Beast copy
    put their root at ``(-278, 1042)`` where the Overgrowth scene they were
    copied from puts it at the canvas centre, so a third of the fights in those
    two rooms get no foreground — in the game as much as here. Keep the list
    empty-or-explained; a NEW entry means a regression.
    """
    out: list[tuple[str, dict[str, Any]]] = []
    for name, scene in manifest["scenes"].items():
        groups: list[tuple[str, list[dict[str, Any]]]] = [
            (f"{name}/{slot['name']}", slot["layers"])
            for slot in scene["slots"]
            if slot["kind"] == "prop"
        ]
        groups += [
            (f"{name}/bg_{key}_{v['id']}", v["layers"])
            for key, bucket in scene["buckets"].items()
            for v in bucket
        ]
        groups += [(f"{name}/fg_{v['id']}", v["layers"]) for v in scene["fg"]]
        for where, layers in groups:
            for layer in layers:
                a, b, c, d = layer.get("t", (1.0, 0.0, 0.0, 1.0))
                corners = [
                    (layer["left"] + a * u + c * v, layer["top"] + b * u + d * v)
                    for u, v in ((0, 0), (layer["w"], 0), (layer["w"], layer["h"]), (0, layer["h"]))
                ]
                xs = [p[0] for p in corners]
                ys = [p[1] for p in corners]
                if min(xs) >= CANVAS_W or min(ys) >= CANVAS_H or max(xs) <= 0 or max(ys) <= 0:
                    out.append((where, layer))
    return out


if __name__ == "__main__":
    main()
