#!/usr/bin/env python3
"""Parse and bake Godot `AnimationPlayer` bezier tracks.

An `AnimationPlayer`'s `bezier` track keys are a fundamentally different
shape from a `Curve` sub-resource's points (`godot_curve.py`): a `Curve`
tangent is a SLOPE (dy/dx), which is what lets `Curve.sample` assume the
Bezier control handle sits at exactly a third of the segment width and
sample it in closed form. A bezier animation-track key instead stores its
handles as RAW `(dx, dy)` offsets from `(time, value)` — free-form, not
pinned to a fixed fraction of the segment — so the segment's Bezier
parameter for a given time isn't known in closed form. Godot itself finds
it by binary-searching the bezier's own time (x) axis
(`Animation::bezier_track_interpolate`, `animation.cpp`), then reads back
the value (y) at that parameter.

That binary search is ported here (`_evaluate`), reusing
`godot_curve._bezier_interpolate` — the same scalar cubic-Bezier polynomial
Godot's `Vector2::bezier_interpolate` runs per-component — rather than
re-deriving the curve form. `Curve.sample`'s Hermite-segment shortcut is
NOT reused: applying it to a track whose handle isn't at `d/3` gets every
non-symmetric handle wrong.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

from scripts.godot_curve import _bezier_interpolate

_SECTION_HEADER = re.compile(r"^\[", re.MULTILINE)
# Requires the type value to be EXACTLY "Animation" (the closing quote is
# part of the pattern) so this can't also match an `AnimationLibrary`
# section, which shares the "Animation" prefix.
_ANIMATION_HEADER = re.compile(r'^\[sub_resource type="Animation"', re.MULTILINE)
_RESOURCE_NAME = re.compile(r'^resource_name\s*=\s*"(?P<name>[^"]*)"', re.MULTILINE)
_TRACK_TYPE = re.compile(r'^tracks/(?P<i>\d+)/type\s*=\s*"(?P<type>\w+)"', re.MULTILINE)
_TRACK_PATH = re.compile(
    r'^tracks/(?P<i>\d+)/path\s*=\s*NodePath\("(?P<path>[^"]*)"\)', re.MULTILINE
)
# The `keys = { ... }` dict is never nested (no field inside it holds a
# `{`/`}` of its own), so a non-greedy scan up to the first line that is
# just `}` is exact, not an approximation.
_TRACK_KEYS = re.compile(
    r"^tracks/(?P<i>\d+)/keys\s*=\s*\{(?P<body>.*?)\n\}", re.MULTILINE | re.DOTALL
)
_POINTS = re.compile(r'"points"\s*:\s*PackedFloat32Array\((?P<body>.*?)\)', re.DOTALL)
_TIMES = re.compile(r'"times"\s*:\s*PackedFloat32Array\((?P<body>.*?)\)', re.DOTALL)
_NUMBER = re.compile(r"-?\d+\.?\d*(?:e[+-]?\d+)?")

_BEZIER = "bezier"

#: `AnimationLibrary._data` names the RESET track without a `resource_name`
#: property of its own — Godot's editor writes that convention, not this
#: parser's choice. Any `[sub_resource type="Animation" ...]` section that
#: omits `resource_name` keys here, mirroring that.
DEFAULT_NAME = "default"


@dataclass(frozen=True)
class BezierKey:
    """One key of a `bezier` animation track.

    `in_x`/`in_y`/`out_x`/`out_y` are RAW `(dx, dy)` handle offsets from
    `(time, value)` — not slopes. `Animation::bezier_track_interpolate` adds
    them straight onto the key's point before running the 2-D cubic Bezier;
    see the module docstring for why that's a different shape from
    `godot_curve.Curve`'s tangents.
    """

    time: float
    value: float
    in_x: float
    in_y: float
    out_x: float
    out_y: float


@dataclass(frozen=True)
class TrackSpec:
    """One `tracks/N/...` entry of an `[sub_resource type="Animation"]` section."""

    path: str
    kind: str
    keys: list[BezierKey]


def _section(text: str, start: int) -> str:
    next_header = _SECTION_HEADER.search(text, start)
    return text[start : next_header.start() if next_header is not None else len(text)]


def parse_animation(text: str) -> dict[str, list[TrackSpec]]:
    """Parse every `[sub_resource type="Animation" ...]` section in *text*.

    Returns a dict keyed by the section's `resource_name` (or
    :data:`DEFAULT_NAME` when the property is omitted). Only `bezier` tracks
    are parsed — this task's scope (`vfx_block_broken`) has no other kind. A
    non-bezier track is silently skipped rather than raising: a scene that
    also carries e.g. a `value` track for an unrelated property must not
    block extraction of the tracks that matter.
    """
    result: dict[str, list[TrackSpec]] = {}
    for header in _ANIMATION_HEADER.finditer(text):
        body = _section(text, header.end())
        name_m = _RESOURCE_NAME.search(body)
        name = name_m.group("name") if name_m is not None else DEFAULT_NAME

        types = {int(m.group("i")): m.group("type") for m in _TRACK_TYPE.finditer(body)}
        paths = {int(m.group("i")): m.group("path") for m in _TRACK_PATH.finditer(body)}
        keys_bodies = {int(m.group("i")): m.group("body") for m in _TRACK_KEYS.finditer(body)}

        tracks: list[TrackSpec] = []
        for i in sorted(types):
            kind = types[i]
            if kind != _BEZIER:
                continue
            keys_body = keys_bodies.get(i)
            if keys_body is None:
                raise ValueError(f"bezier track {i} ({paths.get(i)!r}) has no keys block")
            points_m = _POINTS.search(keys_body)
            times_m = _TIMES.search(keys_body)
            if points_m is None or times_m is None:
                raise ValueError(f"bezier track {i} ({paths[i]!r}) has no points/times")
            points = [float(n) for n in _NUMBER.findall(points_m.group("body"))]
            times = [float(n) for n in _NUMBER.findall(times_m.group("body"))]
            keys = [
                BezierKey(
                    time=times[k],
                    value=points[k * 5],
                    in_x=points[k * 5 + 1],
                    in_y=points[k * 5 + 2],
                    out_x=points[k * 5 + 3],
                    out_y=points[k * 5 + 4],
                )
                for k in range(len(times))
            ]
            tracks.append(TrackSpec(path=paths[i], kind=kind, keys=keys))
        result[name] = tracks
    return result


def _evaluate(track: TrackSpec, t: float) -> float:
    """Port of `Animation::bezier_track_interpolate` (`animation.cpp`).

    Binary-searches the segment's own time axis for the Bezier parameter
    landing on *t*, then reads back the value at that parameter — 10
    iterations, matching the engine's own hardcoded `iterations = 10`.
    """
    keys = track.keys
    if not keys:
        return 0.0
    if len(keys) == 1 or t <= keys[0].time:
        return keys[0].value
    if t >= keys[-1].time:
        return keys[-1].value

    idx = len(keys) - 2
    for i in range(len(keys) - 1):
        if keys[i].time <= t < keys[i + 1].time:
            idx = i
            break
    a, b = keys[idx], keys[idx + 1]

    duration = b.time - a.time
    if duration <= 0:
        return b.value
    local_t = t - a.time

    start_x, start_y = 0.0, a.value
    start_out_x, start_out_y = start_x + a.out_x, start_y + a.out_y
    end_x, end_y = duration, b.value
    end_in_x, end_in_y = end_x + b.in_x, end_y + b.in_y

    low, high = 0.0, 1.0
    middle = 0.0
    for _ in range(10):
        middle = (low + high) / 2
        x = _bezier_interpolate(start_x, start_out_x, end_in_x, end_x, middle)
        if x < local_t:
            low = middle
        else:
            high = middle

    low_x = _bezier_interpolate(start_x, start_out_x, end_in_x, end_x, low)
    low_y = _bezier_interpolate(start_y, start_out_y, end_in_y, end_y, low)
    high_x = _bezier_interpolate(start_x, start_out_x, end_in_x, end_x, high)
    high_y = _bezier_interpolate(start_y, start_out_y, end_in_y, end_y, high)
    if high_x == low_x:
        return low_y
    c = (local_t - low_x) / (high_x - low_x)
    return low_y + (high_y - low_y) * c


def bake_track(
    track: TrackSpec,
    samples: int = 256,
    span: tuple[float, float] | None = None,
) -> list[float]:
    """Sample *track* at *samples* evenly spaced points across *span*.

    *span* is `(start, end)` in animation seconds. Pass the span the RENDERER
    will replay the LUT over — for an effect whose sprites share one clock,
    that is the whole animation, NOT each track's own key range. Baking every
    track over its own range and then sampling them all at
    `elapsed / effect_lifetime` time-warps any track that does not happen to
    span the full animation: a track ending at 0.25 s inside a 0.4 s effect
    plays 1.6x too slowly, and one whose first key is at t > 0 gets dragged
    back to 0. `vfx_block_broken`'s six tracks all span 0 -> 0.4, which is why
    the per-track default was invisible; a scene that mixes track lengths is
    the case this parameter exists for.

    Omitting *span* keeps the per-track default (first key's time -> last
    key's time), which mirrors `godot_curve.bake_curve`'s convention: `lut[0]`
    is the value at the first key, `lut[-1]` at the last. Only use it for a
    track replayed on its OWN clock.

    Outside the key range the track holds its endpoint value (`_evaluate`'s
    own clamp, matching Godot), so a span WIDER than the keys bakes the hold
    faithfully. A track with no keys bakes flat at 0.0 — Godot's own
    `bezier_track_interpolate` returns 0 for an empty track.
    """
    if not track.keys:
        return [0.0] * samples
    if span is None:
        start, end = track.keys[0].time, track.keys[-1].time
    else:
        start, end = span
    duration = end - start
    if samples == 1 or duration <= 0:
        return [_evaluate(track, start)] * samples
    return [_evaluate(track, start + duration * i / (samples - 1)) for i in range(samples)]
