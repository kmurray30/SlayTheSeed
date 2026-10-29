#!/usr/bin/env python3
"""Evaluate Godot `Curve` and `Gradient` sub-resources exactly as Godot does.

Godot samples both as 1-D textures at runtime, so baking them to lookup
tables (`bake_curve`/`bake_gradient`) is exact rather than approximate — but
only if the evaluator matches the engine bit-for-bit in shape. Ported from
`scene/resources/curve.cpp` and `scene/resources/gradient.h` at Godot tag
`4.5.1-stable` (the version the shipped `.pck` embeds).
"""

from __future__ import annotations

import re
from dataclasses import dataclass

# A bracketed section header, e.g. `[sub_resource type="Curve" id="Curve_a"]`.
_SECTION_HEADER = re.compile(r"^\[", re.MULTILINE)
_CURVE_HEADER = re.compile(r'^\[sub_resource type="Curve"', re.MULTILINE)
_GRADIENT_HEADER = re.compile(r'^\[sub_resource type="Gradient"', re.MULTILINE)

_DATA = re.compile(r"^_data\s*=\s*\[(?P<body>.*?)\]", re.MULTILINE | re.DOTALL)
_LIMITS = re.compile(r"^_limits\s*=\s*\[(?P<body>.*?)\]", re.MULTILINE | re.DOTALL)
_VECTOR2 = re.compile(r"Vector2\(\s*(?P<x>-?[\d.e+-]+)\s*,\s*(?P<y>-?[\d.e+-]+)\s*\)")
# Requires a leading digit so this can't match the bare "e" inside a word
# like "Vector2" when run over text that has not had vectors stripped first.
_NUMBER = re.compile(r"-?\d+\.?\d*(?:e[+-]?\d+)?")

_OFFSETS = re.compile(
    r"^offsets\s*=\s*PackedFloat32Array\((?P<body>.*?)\)", re.MULTILINE | re.DOTALL
)
_COLORS = re.compile(r"^colors\s*=\s*PackedColorArray\((?P<body>.*?)\)", re.MULTILINE | re.DOTALL)
_INTERPOLATION_MODE = re.compile(r"^interpolation_mode\s*=\s*(?P<v>\d+)", re.MULTILINE)

# `Gradient::InterpolationMode` (gradient.h). Only LINEAR and CONSTANT are
# ported — the game's authored gradients don't exercise CUBIC, and OKLAB/
# LINEAR_SRGB color-space conversion pulls in `thirdparty/misc/ok_color.h`,
# which nothing in this task's scope needs.
_INTERPOLATE_LINEAR = 0
_INTERPOLATE_CONSTANT = 1

# Godot's Math::is_zero_approx compares against CMP_EPSILON (math_defs.h:50).
# Transcribed rather than chosen: the zero-width-segment guard in
# Curve::sample_local_nocheck (curve.cpp:422) is `Math::is_zero_approx(d)`.
CMP_EPSILON = 0.00001


def _section(text: str, header: re.Pattern[str]) -> str:
    m = header.search(text)
    if m is None:
        raise ValueError(f"no {header.pattern!r} section found")
    next_header = _SECTION_HEADER.search(text, m.end())
    end = next_header.start() if next_header is not None else len(text)
    return text[m.end() : end]


@dataclass(frozen=True)
class _Point:
    x: float
    y: float
    left_tangent: float
    right_tangent: float


@dataclass(frozen=True)
class Curve:
    """A Godot `Curve` resource: points with Bezier-Hermite tangents.

    `limits` is the `_limits` editor metadata, ordered (min_y, max_y, min_x,
    max_x) — `Curve::get_limits`/`set_limits` (curve.cpp:308-335) write
    `[_min_value, _max_value, _min_domain, _max_domain]`, value range first.
    It is display-only range hinting for the curve editor UI — Godot does
    NOT rescale sampled values by it, so `sample()` must not either.
    """

    points: list[_Point]
    limits: tuple[float, float, float, float] | None = None

    def sample(self, offset: float) -> float:
        """Port of `Curve::sample`/`Curve::sample_local_nocheck` (curve.cpp:379-433)."""
        if not self.points:
            return 0.0
        if len(self.points) == 1:
            return self.points[0].y

        i = _get_index(self.points, offset)
        if i == len(self.points) - 1:
            return self.points[i].y

        local = offset - self.points[i].x
        if i == 0 and local <= 0:
            return self.points[0].y

        return _sample_segment(self.points[i], self.points[i + 1], local)


def _get_index(points: list[_Point], offset: float) -> int:
    """Port of `Curve::get_index` (curve.cpp:123-149): lower-bound binary search."""
    imin = 0
    imax = len(points) - 1

    while imax - imin > 1:
        m = (imin + imax) // 2
        a = points[m].x
        b = points[m + 1].x
        if a < offset and b < offset:
            imin = m
        elif a > offset:
            imax = m
        else:
            return m

    if offset > points[imax].x:
        return imax
    return imin


def _sample_segment(a: _Point, b: _Point, local_offset: float) -> float:
    """Port of `Curve::sample_local_nocheck` (curve.cpp:402-433).

    Control points sit at a THIRD of the segment width — `d / 3.0` — which is
    exactly what makes this Bezier equal a Hermite whose tangents are scaled
    by the segment width. Using the raw tangents, or spacing the controls at
    `d`, gets every non-zero-tangent curve wrong by a factor of three.
    """
    d = b.x - a.x
    if abs(d) < CMP_EPSILON:
        # Godot's guard: a zero-width segment samples the RIGHT endpoint.
        return b.y
    t = local_offset / d
    third = d / 3.0
    yac = a.y + third * a.right_tangent
    ybc = b.y - third * b.left_tangent
    return _bezier_interpolate(a.y, yac, ybc, b.y, t)


def _bezier_interpolate(p0: float, p1: float, p2: float, p3: float, t: float) -> float:
    """Standard cubic Bezier (`Math::bezier_interpolate`, math_funcs.h:442-451)."""
    omt = 1.0 - t
    omt2 = omt * omt
    omt3 = omt2 * omt
    t2 = t * t
    t3 = t2 * t
    return p0 * omt3 + p1 * omt2 * t * 3.0 + p2 * omt * t2 * 3.0 + p3 * t3


@dataclass(frozen=True)
class Gradient:
    """A Godot `Gradient` resource: offset/color stops.

    `interpolation_mode` is Godot's raw int (`Gradient::InterpolationMode`),
    not a bool — `0` is LINEAR, `1` is CONSTANT. Reading it as a boolean
    "is linear" flag happens to work for these two values but is the wrong
    concept; keep it as the enum int so a future CUBIC (`2`) fails loudly
    instead of silently behaving like CONSTANT.
    """

    offsets: list[float]
    colors: list[tuple[float, float, float, float]]
    interpolation_mode: int = _INTERPOLATE_LINEAR

    def sample(self, offset: float) -> tuple[float, float, float, float]:
        """Port of `Gradient::get_color_at_offset` (gradient.h:151-235).

        Only the LINEAR and CONSTANT interpolation modes are ported — see
        the module docstring for why CUBIC/non-sRGB color spaces are out of
        scope here.
        """
        if not self.offsets:
            return (0.0, 0.0, 0.0, 1.0)

        # Binary search requires `self.offsets` ascending. `parse_gradient`
        # sorts at construction time so this holds here — Godot instead
        # re-sorts lazily inside `get_color_at_offset` itself
        # (`_update_sorting()`, gradient.h:156) but the effect is the same:
        # by the time any sample runs, the stops are offset-ordered.
        low, high, middle = 0, len(self.offsets) - 1, 0
        while low <= high:
            middle = (low + high) // 2
            point_offset = self.offsets[middle]
            if point_offset > offset:
                high = middle - 1
            elif point_offset < offset:
                low = middle + 1
            else:
                return self.colors[middle]

        if self.offsets[middle] > offset:
            middle -= 1
        first = middle
        second = middle + 1
        if second >= len(self.offsets):
            return self.colors[-1]
        if first < 0:
            return self.colors[0]

        if self.interpolation_mode == _INTERPOLATE_CONSTANT:
            return self.colors[first]

        weight = (offset - self.offsets[first]) / (self.offsets[second] - self.offsets[first])
        c1 = self.colors[first]
        c2 = self.colors[second]
        return tuple(a + (b - a) * weight for a, b in zip(c1, c2))  # type: ignore[return-value]


def parse_curve(text: str) -> Curve:
    """Parse a `[sub_resource type="Curve" ...]` block from `.tscn`/`.tres` source.

    Args:
        text: Source containing (at least) one `Curve` sub-resource section.

    Returns:
        The parsed `Curve`.
    """
    section = _section(text, _CURVE_HEADER)

    data_m = _DATA.search(section)
    body = data_m.group("body") if data_m else ""
    vectors = [(float(m.group("x")), float(m.group("y"))) for m in _VECTOR2.finditer(body)]
    # `_data` layout: [Vector2(x, y), left_tangent, right_tangent, left_mode,
    # right_mode, ...] repeated per point. The `Vector2(...)` chunks are
    # stripped before the flat number scan — otherwise "Vector2" itself
    # contributes a spurious digit ("2") and a spurious "e" to the token
    # stream, corrupting every subsequent point's offset.
    scalars = [float(n) for n in _NUMBER.findall(_VECTOR2.sub("", body))]

    points = []
    for i, (x, y) in enumerate(vectors):
        base = i * 4
        left_tangent = scalars[base]
        right_tangent = scalars[base + 1]
        points.append(_Point(x=x, y=y, left_tangent=left_tangent, right_tangent=right_tangent))

    limits_m = _LIMITS.search(section)
    limits = None
    if limits_m is not None:
        vals = [float(n) for n in _NUMBER.findall(limits_m.group("body"))]
        if len(vals) == 4:
            limits = (vals[0], vals[1], vals[2], vals[3])

    return Curve(points=points, limits=limits)


def parse_gradient(text: str) -> Gradient:
    """Parse a `[sub_resource type="Gradient" ...]` block from `.tscn`/`.tres` source.

    Args:
        text: Source containing (at least) one `Gradient` sub-resource section.

    Returns:
        The parsed `Gradient`.
    """
    section = _section(text, _GRADIENT_HEADER)

    offsets_m = _OFFSETS.search(section)
    offsets = [float(n) for n in _NUMBER.findall(offsets_m.group("body"))] if offsets_m else []

    colors_m = _COLORS.search(section)
    # `PackedColorArray(r, g, b, a, r, g, b, a, ...)` — flat, 4 numbers per
    # stop. HDR gradients carry real values above 1.0 (e.g. 2.454227), so
    # this must not clamp.
    flat = [float(n) for n in _NUMBER.findall(colors_m.group("body"))] if colors_m else []
    colors = [(flat[i], flat[i + 1], flat[i + 2], flat[i + 3]) for i in range(0, len(flat), 4)]

    # A `.tres`/`.tscn` OMITS a property left at its resource default, so a
    # gradient that only re-colours Godot's two default stops writes `colors`
    # and no `offsets` at all. Taking that as "no stops" makes `sample()`
    # return opaque black for every offset — i.e. a black particle. 93 of the
    # 569 Gradient sub-resources across the pck's vfx scenes are written this
    # way, as are 7 of the 20 materials in use, so this is the common case,
    # not a corner.
    #
    # Godot's own defaults: the constructor makes exactly two points at
    # offsets 0 and 1 (gradient.cpp:35-41), and `set_colors` only resizes the
    # point list and assigns colours — it never touches an offset
    # (gradient.cpp:155-164), so surviving points keep 0 and 1 and any point
    # beyond the second gets `Point::offset`'s in-class default of 0.0
    # (gradient.h:55).
    if not offsets_m and colors:
        if len(colors) > 2:
            raise ValueError(
                f"Gradient with {len(colors)} colors and no `offsets` line: Godot would "
                "leave every stop past the second at offset 0.0, which no gradient in "
                "scope does. Refusing to guess — check the source and model it explicitly."
            )
        offsets = [0.0, 1.0][: len(colors)]

    mode_m = _INTERPOLATION_MODE.search(section)
    mode = int(mode_m.group("v")) if mode_m else _INTERPOLATE_LINEAR

    # `Gradient::set_offsets` marks the point list unsorted unconditionally,
    # and `_update_sorting()` re-sorts it before every `get_color_at_offset`
    # call (gradient.h:68-73, 156) — so Godot always samples in offset order
    # regardless of file order. `sorted()` is stable, so stops that share an
    # offset keep their authored relative order; Godot's own re-sort uses an
    # unstable introsort, so its tie-break is unspecified — preserving
    # authored order for ties is the only defensible reading here.
    if offsets != sorted(offsets):
        paired = sorted(zip(offsets, colors), key=lambda p: p[0])
        offsets = [o for o, _ in paired]
        colors = [c for _, c in paired]

    return Gradient(offsets=offsets, colors=colors, interpolation_mode=mode)


def bake_curve(c: Curve, n: int = 256) -> list[float]:
    """Sample `c` at `n` evenly spaced points across [0, 1], endpoint-inclusive.

    Args:
        c: The curve to bake.
        n: Number of samples in the returned lookup table.

    Returns:
        `n` sampled values, `lut[0]` at offset 0.0 and `lut[-1]` at offset 1.0.
    """
    if n == 1:
        return [c.sample(0.0)]
    return [c.sample(i / (n - 1)) for i in range(n)]


def bake_gradient(g: Gradient, n: int = 256) -> list[tuple[float, float, float, float]]:
    """Sample `g` at `n` evenly spaced points across [0, 1], endpoint-inclusive.

    Args:
        g: The gradient to bake.
        n: Number of samples in the returned lookup table.

    Returns:
        `n` sampled colors, `lut[0]` at offset 0.0 and `lut[-1]` at offset 1.0.
    """
    if n == 1:
        return [g.sample(0.0)]
    return [g.sample(i / (n - 1)) for i in range(n)]
