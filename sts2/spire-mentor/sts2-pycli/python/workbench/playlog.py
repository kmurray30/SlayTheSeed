"""Format the engine card play-log into workbench event-log lines.

The Rust engine records the ordered sequence of cards that resolved during one
action — the manually played card FIRST, then any engine auto-plays (Cascade /
Havoc / Stampede / Mayhem) it spawned. This renders that parent-first sequence
into event-log lines. Pure: takes the list of entry dicts (sliced to one
action's tail by the caller) plus an optional id->display-name resolver.
"""

from __future__ import annotations

from collections.abc import Callable
from typing import Any

_AUTO = "  ↳ "  # two-space indent + downwards-arrow-with-tip-rightwards


def format_play_log(
    entries: list[dict[str, Any]],
    name_of: Callable[[str], str] = lambda i: i,
) -> list[str]:
    """Return ordered event-log lines for one action's play-log tail.

    Args:
        entries: play-log entry dicts from ``RunManager.card_play_log()``,
            already sliced to this action's plays.
        name_of: maps a card id (SCREAMING_SNAKE) to a display title; defaults
            to identity for tests.
    """
    lines: list[str] = []
    for e in entries:
        name = name_of(str(e.get("id", "?")))
        if e.get("upgraded"):
            name += "+"
        if e.get("auto_played"):
            lines.append(f"{_AUTO}{name} (auto-played)")
            continue
        # Only X-cost triggers carry a stamped energy_spent at record time; a
        # normal card logs 0, so a positive value IS the X to surface.
        spent = e.get("energy_spent") or 0
        suffix = f" (X={spent})" if spent else ""
        lines.append(f"Play {name}{suffix}")
    return lines
