"""Per-floor parity assertions — bead `sts2-rs-p0jzo` (W7).

After the W8 driver dispatches the actions implied by a scraped
:class:`replay.scraped.Floor`, this module compares the engine's
post-floor state against the scraped snapshot and emits a typed
:class:`Divergence` list. The harness logs each divergence and
continues (no fail-fast) so a single run produces a full diagnostic
record across all floors.

Tolerance pin:

- HP: exact for non-combat floors; ±2 for ``monster`` / ``elite`` /
  ``boss`` rooms (MCTS variance + Vulnerable rounding edge cases).
- Gold: always exact.
- Relics: engine relic-list must be a superset of every scraped
  ``relics_picked`` accumulated up to and including this floor.
- Master deck: cards-gained / cards-removed / upgraded-cards diffs
  must match the scraped per-floor deltas exactly.
"""

from __future__ import annotations

from collections import Counter
from collections.abc import Iterable
from dataclasses import dataclass
from enum import StrEnum
from typing import Any

from .scraped import Floor

_COMBAT_ROOMS = frozenset({"monster", "elite", "boss"})
_DEFAULT_COMBAT_HP_TOLERANCE = 2


class DivergenceKind(StrEnum):
    """Why a per-floor checkpoint flagged a state mismatch."""

    HP = "hp_div"
    """Engine ``current_hp`` differs from ``Floor.hp_after`` beyond
    tolerance."""

    GOLD = "gold_div"
    """Engine ``gold`` differs from ``Floor.gold_after`` (exact)."""

    RELIC_MISSING = "relic_div"
    """Scraped run picked a relic the engine never granted."""

    CARDS_GAINED = "reward_div"
    """Engine ``master_deck`` is missing one of the scraped
    ``cards_gained`` entries, or contains a gain the scraper didn't
    record."""

    CARDS_REMOVED = "shop_div"
    """Engine ``master_deck`` still contains a card the scraper
    recorded as removed on this floor."""

    UPGRADED = "upgrade_div"
    """Engine's set of upgraded card-ids disagrees with the scraped
    ``upgraded_cards`` delta for this floor."""

    EVENT = "event_div"
    """Event floor didn't dispatch the scraped option (no engine-side
    effect detectable on hp/gold/deck — recorded as a structural
    divergence so the driver knows to flag the run)."""

    COMBAT = "combat_div"
    """Combat floor's hp_after fell outside the documented tolerance —
    structurally split from :attr:`HP` so harness summaries can bucket
    by-phase."""


@dataclass(frozen=True)
class Divergence:
    """One mismatch between the engine and the scraped floor.

    ``floor_ordinal`` is the 1-indexed floor number (``Floor.ordinal``);
    ``kind`` selects the bucket; ``detail`` is a short human-readable
    summary the harness can ``print`` verbatim. Callers wanting
    structured analysis match on ``kind`` and read ``engine`` /
    ``scraped`` for the raw values.
    """

    floor_ordinal: int
    kind: DivergenceKind
    detail: str
    engine: Any = None
    scraped: Any = None


def check_floor(
    floor: Floor,
    engine_state: EngineStateLike,
    *,
    accumulated_relics: Iterable[str] = (),
    combat_hp_tolerance: int = _DEFAULT_COMBAT_HP_TOLERANCE,
) -> list[Divergence]:
    """Assert engine state matches the scraped ``Floor`` snapshot.

    ``engine_state`` is duck-typed: the checker reads
    ``engine_state.current_hp`` (int), ``engine_state.gold`` (int),
    ``engine_state.relic_ids`` (set/list of ``"RELIC.<UPPER_SNAKE>"``
    strings), ``engine_state.master_deck_card_ids`` (list of
    ``"CARD.<UPPER_SNAKE>"`` strings, including duplicates), and
    ``engine_state.upgraded_card_ids`` (Counter / list of upgraded
    instances, one entry per upgraded card).

    ``accumulated_relics`` is the union of every ``relics_picked``
    from the prior floors; the harness threads it forward so this
    floor's check sees the full set the engine should have granted.

    Returns the divergences for this floor. Empty list == clean.
    """
    divs: list[Divergence] = []

    expected_hp = floor.hp_after
    actual_hp = engine_state.current_hp
    if floor.map_point_type in _COMBAT_ROOMS:
        if abs(actual_hp - expected_hp) > combat_hp_tolerance:
            divs.append(
                Divergence(
                    floor.ordinal,
                    DivergenceKind.COMBAT,
                    f"hp {actual_hp} != {expected_hp} (tolerance ±{combat_hp_tolerance})",
                    engine=actual_hp,
                    scraped=expected_hp,
                )
            )
    elif actual_hp != expected_hp:
        divs.append(
            Divergence(
                floor.ordinal,
                DivergenceKind.HP,
                f"hp {actual_hp} != {expected_hp} (exact)",
                engine=actual_hp,
                scraped=expected_hp,
            )
        )

    if engine_state.gold != floor.gold_after:
        divs.append(
            Divergence(
                floor.ordinal,
                DivergenceKind.GOLD,
                f"gold {engine_state.gold} != {floor.gold_after}",
                engine=engine_state.gold,
                scraped=floor.gold_after,
            )
        )

    expected_relics = set(accumulated_relics) | set(floor.relics_picked)
    engine_relics = set(engine_state.relic_ids)
    missing = expected_relics - engine_relics
    if missing:
        divs.append(
            Divergence(
                floor.ordinal,
                DivergenceKind.RELIC_MISSING,
                f"missing relics: {sorted(missing)}",
                engine=sorted(engine_relics),
                scraped=sorted(expected_relics),
            )
        )

    # Deck deltas — compare per-floor scraper gains/removes against the
    # engine's master_deck before/after this floor. The engine-side
    # bookkeeping (Counter of CardId strings) is provided by the W8
    # driver via `engine_state.deck_delta`; absence implies the driver
    # didn't track deltas this floor (e.g. CardReward skipped), which
    # we recognize as a "no-op floor" rather than a divergence.
    deck_delta = getattr(engine_state, "deck_delta", None)
    if deck_delta is not None:
        gained_eng = Counter(deck_delta.get("gained", []))
        gained_scr = Counter(floor.cards_gained)
        if gained_eng != gained_scr:
            divs.append(
                Divergence(
                    floor.ordinal,
                    DivergenceKind.CARDS_GAINED,
                    f"cards_gained engine={sorted(gained_eng.elements())} "
                    f"scraped={sorted(gained_scr.elements())}",
                    engine=sorted(gained_eng.elements()),
                    scraped=sorted(gained_scr.elements()),
                )
            )

        removed_eng = Counter(deck_delta.get("removed", []))
        removed_scr = Counter(floor.cards_removed)
        if removed_eng != removed_scr:
            divs.append(
                Divergence(
                    floor.ordinal,
                    DivergenceKind.CARDS_REMOVED,
                    f"cards_removed engine={sorted(removed_eng.elements())} "
                    f"scraped={sorted(removed_scr.elements())}",
                    engine=sorted(removed_eng.elements()),
                    scraped=sorted(removed_scr.elements()),
                )
            )

        upgraded_eng = Counter(deck_delta.get("upgraded", []))
        upgraded_scr = Counter(floor.upgraded_cards)
        if upgraded_eng != upgraded_scr:
            divs.append(
                Divergence(
                    floor.ordinal,
                    DivergenceKind.UPGRADED,
                    f"upgraded engine={sorted(upgraded_eng.elements())} "
                    f"scraped={sorted(upgraded_scr.elements())}",
                    engine=sorted(upgraded_eng.elements()),
                    scraped=sorted(upgraded_scr.elements()),
                )
            )

    return divs


class EngineStateLike:
    """Structural protocol for :func:`check_floor`'s engine-state arg.

    The harness uses ``typing.Protocol`` semantics implicitly: any
    object exposing these attributes works. Defined as a plain class
    so the module imports cleanly under Python 3.13 without a
    ``typing`` dependency.
    """

    current_hp: int
    gold: int
    relic_ids: Iterable[str]
    master_deck_card_ids: Iterable[str]
    upgraded_card_ids: Iterable[str]
    deck_delta: dict[str, list[str]] | None = None
