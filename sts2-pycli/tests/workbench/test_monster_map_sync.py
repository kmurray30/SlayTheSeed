"""Pin ``MONSTER_TITLE_TO_ID`` to the engine's monster registry.

The adapter translates a scraped/live monster *display name* into the
CamelCase registry id ``sts2_rs`` expects. That map (``canonical_maps.
MONSTER_TITLE_TO_ID``) is hand-written but is supposed to cover *every*
monster the engine models. It silently drifted once — it covered 30 of 121
modeled monsters, so a real monster like "Scroll of Biting" raised
``UnsupportedStateError`` even though the engine fully models it.

This test fails loud the moment the engine grows (or renames) a monster that
the map doesn't represent, so the gap can't reopen unnoticed. It is also the
canonical regeneration recipe: see ``_derive_expected`` below.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

import pytest

# canonical_maps imports without the extension (it degrades gracefully), so it
# can sit with the other top-level imports; only the registry calls below need
# the live engine, gated by the importorskip.
from sts2_hint.canonical_maps import MONSTER_TITLE_ALIASES, MONSTER_TITLE_TO_ID

sts2_rs = pytest.importorskip("sts2_rs", reason="needs the sts2_rs extension")

_LOC = Path(__file__).resolve().parents[2] / "localization_eng" / "monsters.json"

# Engine monster ids intentionally absent from a title→id map.
#
# Synthetic test monsters (no localization entry — not real game content):
_TEST_MONSTERS = {
    "OneHpMonster",
    "TenHpMonster",
    "SingleAttackMoveMonster",
    "MultiAttackMoveMonster",
}
# Ids whose display name collides with another id, which a title→id map cannot
# disambiguate. The three directional Decimillipede segments all localize to
# "Decimillipede" (the base ``DecimillipedeSegment`` carries that title);
# ``DenseVegetationWriggler`` localizes to "Wriggler" (bound to ``Wriggler``).
_DISPLAY_COLLISIONS = {
    "DecimillipedeSegmentBack",
    "DecimillipedeSegmentFront",
    "DecimillipedeSegmentMiddle",
    "DenseVegetationWriggler",
}
EXCLUDED = _TEST_MONSTERS | _DISPLAY_COLLISIONS


def _screaming(camel: str) -> str:
    """``ScrollOfBiting`` -> ``SCROLL_OF_BITING`` (localization key stem)."""
    return re.sub(r"(?<!^)(?=[A-Z])", "_", camel).upper()


def _derive_expected() -> dict[str, str]:
    """Regenerate the canonical map from the engine registry × localization.

    This is the recipe to re-run when porting a new monster: paste the result
    into ``canonical_maps.MONSTER_TITLE_TO_ID`` (sorted by title), then
    re-merge ``MONSTER_TITLE_ALIASES`` on top (see its docstring — titles a
    monster's C# ``Title`` getter can emit that the registry-id ->
    localization-key derivation below can't produce, e.g. ``ToughEgg`` ->
    "Hatchling" post-hatch).
    """
    loc = json.loads(_LOC.read_text(encoding="utf-8"))
    engine = set(sts2_rs.list_registry_names("monster"))
    expected: dict[str, str] = {}
    for mid in sorted(engine):
        if mid in EXCLUDED:
            continue
        title = loc.get(f"{_screaming(mid)}.name")
        assert title is not None, (
            f"engine monster {mid!r} has no localization '.name' and is not in "
            f"the EXCLUDED allow-list — add it to canonical_maps or EXCLUDED"
        )
        expected[title] = mid
    expected.update(MONSTER_TITLE_ALIASES)
    return expected


def test_every_modeled_monster_is_mapped() -> None:
    """Every engine monster (minus documented exclusions) is in the map."""
    engine = set(sts2_rs.list_registry_names("monster"))
    mapped_ids = set(MONSTER_TITLE_TO_ID.values())
    unmapped = sorted(engine - mapped_ids - EXCLUDED)
    assert not unmapped, (
        f"{len(unmapped)} engine monster(s) missing from MONSTER_TITLE_TO_ID "
        f"(add to the map, or to EXCLUDED if unmappable): {unmapped}"
    )


def test_no_stale_or_unknown_ids() -> None:
    """Every mapped id is a real engine monster (no STS1 leftovers / typos)."""
    engine = set(sts2_rs.list_registry_names("monster"))
    unknown = sorted(v for v in MONSTER_TITLE_TO_ID.values() if v not in engine)
    assert not unknown, f"map ids not in the engine registry: {unknown}"


def test_map_matches_localization_derivation() -> None:
    """The map equals the registry×localization derivation exactly."""
    assert _derive_expected() == MONSTER_TITLE_TO_ID


def test_ids_are_unique() -> None:
    """No engine id is reachable from two different display titles --
    except the documented ``MONSTER_TITLE_ALIASES`` entries, whose whole
    point is a second title for an id already covered by the derived map
    (see its docstring)."""
    alias_ids = set(MONSTER_TITLE_ALIASES.values())
    ids = [v for k, v in MONSTER_TITLE_TO_ID.items() if k not in MONSTER_TITLE_ALIASES]
    assert len(ids) == len(set(ids)), "duplicate id(s) in MONSTER_TITLE_TO_ID"
    # Every alias id must be duplicating a *real* derived-map id, not
    # introducing a brand-new one under the alias's cover.
    assert alias_ids <= set(ids), (
        "MONSTER_TITLE_ALIASES id(s) not otherwise present in the derived "
        f"map: {alias_ids - set(ids)}"
    )
