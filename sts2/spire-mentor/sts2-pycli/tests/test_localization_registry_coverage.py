"""Every engine-registry entry must have a resolvable display title (GH #358).

``localization_eng/`` is a hand-taken snapshot of the game's loc tables
(``_done.txt`` says "45 tables at 03/20/2026"), and there is no generator to
re-run -- so it drifts silently as content lands. ``canonical_maps`` inverts
it into the ``{display title -> REGISTRY_ID}`` maps that the wire adapters
fall back on, so a missing row means a live object cannot be resolved by
title at all.

That is how ``FISHING_ROD`` (1590 occurrences in recent live logs),
``SILKEN_TRESS`` and ``KALEIDOSCOPE`` came to be absent from the snapshot
entirely, three v0.107.1 Ancient/Event relics with no title, description or
flavor. ``47e4cb96b`` made the *resolution* path drift-proof by preferring
the bridge's canonical ``id`` when it is a registry name, so these no longer
soft-drop on the live arm -- but anything that can only ever resolve by title
still needs the row to exist.

These tests compare the snapshot against ``sts2_rs.list_registry_names`` --
the engine registry itself, so the gate cannot drift with content the way a
hand-listed expectation would.
"""

from __future__ import annotations

import json
from functools import cache
from pathlib import Path

import pytest

try:
    import sts2_rs  # type: ignore[import-not-found]

    _RS_SKIP_REASON: str | None = None
except ImportError as _e:  # pragma: no cover - build-less env
    sts2_rs = None  # type: ignore[assignment]
    _RS_SKIP_REASON = f"sts2_rs PyO3 extension not importable in this venv: {_e}"

pytestmark = pytest.mark.skipif(_RS_SKIP_REASON is not None, reason=_RS_SKIP_REASON or "no sts2_rs")

_LOC_DIR = Path(__file__).resolve().parent.parent / "localization_eng"

#: Card registry names carry a ``_CARD`` suffix the loc keys do not -- same
#: strip ``ppo.vocab`` applies.
_CARD_SUFFIX = "_CARD"

#: Card registry entries with genuinely no loc row, pinned exactly so the set
#: can only shrink deliberately. Mostly engine placeholders and statuses
#: (``GENERIC``, ``SLOTH_STATUS``) rather than playable cards.
#: Not investigated here -- GH #358 is scoped to relics; this pins the rest so
#: NEW drift is still caught.
#:
#: Shrank by four on 2026-08-24 when ``localization_eng/`` was re-vendored from
#: the pinned v0.107.1 build: NOT_YET, PREPARED, SCARE and WITHER are all
#: content the game added AFTER v0.103.2, so the old dump simply had no row for
#: them. That was the whole "untitled" story for those four.
_KNOWN_UNTITLED_CARDS = frozenset(
    {
        "GENERIC",
        "PAIN",
        "PARASITE",
        "SLOTH_STATUS",
    }
)


#: Registry ids that exist only in game build v0.111.0 (GH #799 put them in the
#: card and potion registries so the PPO vocab can give them rows of their
#: own). ``localization_eng/`` is the v0.107.1 build's tables (``BUILD.txt``),
#: which cannot title them; per-version localization is the workbench data
#: bundle's job (GH #801). Pinned exactly, like the set above, so any OTHER new
#: untitled id still fails.
_V0111_ONLY = {
    "card": frozenset(
        {
            "ABUNDANCE",
            "BLADE_SYMPHONY",
            "BLAZE",
            "CACOPHONY",
            "CONCOCT",
            "CONSTELLATION",
            "DOWSING",
            "FADE",
            "HIBERNATE",
            "IMITATION_LEARNING",
            "MIDNIGHT",
            "ONE_FOR_ALL",
            "OUTRAGE",
            "PLOT",
            "SIDESTEP",
            "SOULBOUND",
            "THE_BALL",
            "TUTOR",
            "UNDERWORLD",
        }
    ),
    "potion": frozenset({"AMBERGRIS"}),
    "relic": frozenset(),
}


def _v0107_registry(kind: str) -> list[str]:
    """The engine registry minus the v0.111.0-only ids."""
    skip = _V0111_ONLY[kind]
    names = list(sts2_rs.list_registry_names(kind))
    assert skip <= {n.removesuffix(_CARD_SUFFIX) for n in names}, (
        f"_V0111_ONLY[{kind!r}] names ids the registry no longer has"
    )
    return [n for n in names if n.removesuffix(_CARD_SUFFIX) not in skip]


@cache
def _loc(filename: str) -> dict[str, str]:
    return json.loads((_LOC_DIR / filename).read_text(encoding="utf-8"))


def _titles(filename: str) -> set[str]:
    return {k[: -len(".title")] for k in _loc(filename) if k.endswith(".title")}


def _title_of(filename: str, loc_key: str) -> str | None:
    """The display title, or ``None`` when the snapshot has no row at all.

    Absence is the coverage test's business -- returning ``None`` here keeps
    the round-trip test from cascading into a bare ``KeyError`` and hiding its
    own verdict behind an unrelated traceback.
    """
    return _loc(filename).get(f"{loc_key}.title")


class TestEveryRegistryEntryHasATitle:
    @pytest.mark.parametrize(
        ("kind", "filename"), [("relic", "relics.json"), ("potion", "potions.json")]
    )
    def test_no_untitled_entries(self, kind: str, filename: str) -> None:
        missing = sorted(set(_v0107_registry(kind)) - _titles(filename))
        assert missing == [], (
            f"{len(missing)} {kind}(s) in the engine registry have no "
            f"'<KEY>.title' row in localization_eng/{filename}: {missing}. "
            "Refresh the snapshot from the current game build."
        )

    def test_cards_have_no_new_untitled_entries(self) -> None:
        titles = _titles("cards.json")
        missing = {
            name.removesuffix(_CARD_SUFFIX)
            for name in _v0107_registry("card")
            if name.removesuffix(_CARD_SUFFIX) not in titles
        }
        assert missing <= _KNOWN_UNTITLED_CARDS, (
            "new untitled card(s) since this gate was written: "
            f"{sorted(missing - _KNOWN_UNTITLED_CARDS)}"
        )
        # Keep the pin honest: an entry that gained a title must leave the set.
        assert missing >= _KNOWN_UNTITLED_CARDS, (
            "these cards now have titles and should be dropped from "
            f"_KNOWN_UNTITLED_CARDS: {sorted(_KNOWN_UNTITLED_CARDS - missing)}"
        )


class TestTitlesRoundTripToRegistryIds:
    """A row that exists but inverts to a different id is no better than absent."""

    @pytest.mark.parametrize(
        ("kind", "filename", "map_name"),
        [
            ("relic", "relics.json", "RELIC_TITLE_TO_ID"),
            ("potion", "potions.json", "POTION_TITLE_TO_ID"),
        ],
    )
    def test_round_trip(self, kind: str, filename: str, map_name: str) -> None:
        from sts2_hint import canonical_maps

        title_to_id = getattr(canonical_maps, map_name)
        broken = {
            name: title_to_id.get(_title_of(filename, name))
            for name in _v0107_registry(kind)
            if title_to_id.get(_title_of(filename, name)) != name
        }
        assert broken == {}, f"{kind} titles that do not invert back to their registry id: {broken}"


class TestTheThreeRelicsThatStartedThis:
    """GH #358's named relics, pinned by name so the regression is legible."""

    @pytest.mark.parametrize(
        ("loc_key", "title"),
        [
            ("FISHING_ROD", "Fishing Rod"),
            ("SILKEN_TRESS", "Silken Tress"),
            # Survived before only by luck: its single-word title uppercases
            # straight onto its registry id, so the id path covered it.
            ("KALEIDOSCOPE", "Kaleidoscope"),
        ],
    )
    def test_relic_resolves(self, loc_key: str, title: str) -> None:
        from sts2_hint.canonical_maps import RELIC_TITLE_TO_ID

        assert _title_of("relics.json", loc_key) == title
        assert RELIC_TITLE_TO_ID[title] == loc_key
