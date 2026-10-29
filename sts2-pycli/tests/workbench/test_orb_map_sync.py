"""Pin ``ORB_TITLE_TO_ID`` to the engine's orb registry.

The map is five hand-written lines, which is exactly why it rotted: the
engine grew ``GLASS`` and the map did not, so a Glass orb on the wire raised
``UnsupportedStateError`` even though the engine models the orb completely.
That killed rung-3.5 seed 1019 at floor 33 on 2026-08-01 — 28 combat searches
failed before the run died — and the same failure mode has now cost a run
twice (see ``test_monster_map_sync`` for the monster edition).

The engine registry × ``localization_eng/orbs.json`` is the recipe; these
tests are that recipe asserted.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

import pytest
from sts2_hint.canonical_maps import ORB_TITLE_TO_ID

sts2_rs = pytest.importorskip("sts2_rs", reason="needs the sts2_rs extension")

_LOC = Path(__file__).resolve().parents[2] / "localization_eng" / "orbs.json"

#: Registry id -> localization key stem, for the ids whose key is not simply
#: ``<ID>_ORB``. ``EMPTY`` is the empty belt slot, which localizes under its
#: own ``EMPTY_SLOT`` entry ("Orb Slot") rather than as an orb.
_KEY_STEM = {"EMPTY": "EMPTY_SLOT"}


def _loc_stem(orb_id: str) -> str:
    return _KEY_STEM.get(orb_id, f"{orb_id}_ORB")


def _derive_expected() -> dict[str, str]:
    """Regenerate the canonical map from the engine registry × localization.

    Re-run this when the engine gains an orb: paste the result into
    ``canonical_maps.ORB_TITLE_TO_ID``.
    """
    loc = json.loads(_LOC.read_text(encoding="utf-8"))
    expected: dict[str, str] = {}
    for orb_id in sorted(sts2_rs.list_registry_names("orb")):
        title = loc.get(f"{_loc_stem(orb_id)}.title")
        assert title is not None, (
            f"engine orb {orb_id!r} has no localization '.title' under "
            f"{_loc_stem(orb_id)!r} — add its key stem to _KEY_STEM"
        )
        expected[title] = orb_id
    return expected


def test_every_modeled_orb_is_mapped() -> None:
    engine = set(sts2_rs.list_registry_names("orb"))
    unmapped = sorted(engine - set(ORB_TITLE_TO_ID.values()))
    assert not unmapped, (
        f"{len(unmapped)} engine orb(s) missing from ORB_TITLE_TO_ID — the "
        f"adapter will refuse any state holding one: {unmapped}"
    )


def test_no_stale_or_unknown_ids() -> None:
    engine = set(sts2_rs.list_registry_names("orb"))
    unknown = sorted(v for v in ORB_TITLE_TO_ID.values() if v not in engine)
    assert not unknown, f"map ids not in the engine registry: {unknown}"


def test_map_matches_localization_derivation() -> None:
    assert _derive_expected() == ORB_TITLE_TO_ID


def test_titles_are_the_wire_spelling() -> None:
    """The keys are what the producers send: the localized display title."""
    loc = json.loads(_LOC.read_text(encoding="utf-8"))
    titles = {v for k, v in loc.items() if re.fullmatch(r"[A-Z_]+\.title", k)}
    assert set(ORB_TITLE_TO_ID) <= titles
