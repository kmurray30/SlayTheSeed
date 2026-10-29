"""`.run` parser for the per-floor verification harness — bead
`sts2-rs-p0jzo` (W7).

Pins:
- Schema: ``sts2_run_scraper`` v0.103.2 ``.run`` JSON shape (top-level
  ``acts`` / ``ascension`` / ``map_point_history`` / ``win`` / …).
- Single-player only at this pin — multiplayer ``player_stats``
  positions are rejected at parse time.

The corpus lives at ``../sts2_run_scraper/data/files/<character>/<YYYY-MM>/<uuid>.run``.
Floors are 1-indexed per ``Run.floors`` ordinal (ancient = floor 1,
boss = final). Each ``Floor`` carries the *after* state recorded by
the scraper for that floor; the harness asserts the engine's state
matches ``Floor.hp_after`` / ``Floor.gold_after`` / etc. once it has
dispatched the actions implied by the scraped choices.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from run_files import load_run

_TEXTKEY_RELIC_PREFIX = "RELIC."


@dataclass(frozen=True)
class Floor:
    """One row in ``Run.map_point_history`` flattened to the harness's
    view of "after this floor resolved, here's what the player looks
    like."

    ``hp_after`` / ``gold_after`` are exact snapshots; ``cards_gained``
    / ``cards_removed`` / ``upgraded_cards`` are the deltas the scraper
    recorded for this floor. Engine-side checks accumulate them across
    floors (see :func:`replay.checkpoints.check_floor`).
    """

    ordinal: int
    """1-indexed floor number within the act (ancient room = 1)."""

    map_point_type: str
    """Lowercased room type: ``ancient`` / ``monster`` / ``elite`` /
    ``boss`` / ``shop`` / ``rest_site`` / ``treasure`` / ``event``."""

    hp_after: int
    """Player current_hp at floor exit. Exact for non-combat phases;
    combats get a ±2 tolerance (Vulnerable rounding, intent variance)."""

    gold_after: int
    """Player gold at floor exit. Always exact."""

    relics_picked: list[str]
    """``"RELIC.<UPPER_SNAKE>"`` ids picked on this floor — union of
    ``ancient_choice`` / ``relic_choices`` entries with
    ``was_chosen=true`` / ``was_picked=true``."""

    cards_gained: list[str]
    """``"CARD.<UPPER_SNAKE>"`` ids added to ``master_deck`` on this
    floor (matches ``player_stats[0].cards_gained``). Includes shop
    buys, card-reward picks, event grants."""

    cards_removed: list[str]
    """Card ids removed from ``master_deck`` on this floor — Smith
    pre-state forms, shop-purge picks, event removals."""

    upgraded_cards: list[str]
    """Card ids upgraded on this floor (Smith pick, Whetstone/Mummified
    Hand auto-upgrade, …). One entry per upgraded instance."""

    event_choices: list[str]
    """TextKey path tail (lowercase, e.g. ``"bottle"``) of each picked
    event option on this floor. Empty for non-Event floors."""

    raw_player_stats: dict[str, Any] = field(repr=False, default_factory=dict)
    """Full ``player_stats[0]`` blob, retained verbatim for the
    diagnostic dump. Not used in equality / checkpoints."""


@dataclass(frozen=True)
class Run:
    """One scraped ``.run`` file in typed form. ``floors`` flattens
    ``map_point_history`` across all acts in encounter order (Act-1
    floors first, then Act-2 if present, then Act-3); the harness
    starts re-driving from ``floors[0]``.
    """

    seed: str
    """Run RNG seed as captured by the scraper. The v0.103.2 corpus
    stores the raw human-typed string (alphanumeric, e.g.
    ``"3X2G20D0UW"``); the engine's
    ``new_ironclad_run_manager_from_string_seed`` accepts this form
    and runs the same string-hash mix the C# game uses."""

    ascension: int
    """0..20. Engine constructor takes this verbatim."""

    build_id: str
    """``"v0.103.2"`` at the current pin; the harness filters to this."""

    won: bool
    """Whether the scraped human won the run (boss kill on the final
    act)."""

    acts: list[str]
    """Per-act biome ids (``"ACT.UNDERDOCKS"`` / ``"ACT.HIVE"`` /
    ``"ACT.GLORY"`` / …). Same length as ``map_point_history``."""

    floors: list[Floor]
    """All floors across all acts, flattened in encounter order."""

    source_path: Path
    """Absolute path of the ``.run`` JSON this Run was parsed from."""


def parse_run(path: Path | str) -> Run:
    """Parse a single ``.run`` JSON into a :class:`Run`.

    Raises ``ValueError`` if:

    - the file is multi-player (``len(players) > 1``) — the harness
      only supports single-player at this pin;
    - ``map_point_history`` is empty;
    - any floor lacks ``player_stats[0]`` (scraper drop signal).

    The parser is lenient about *unknown* fields (forward-compat with
    schema additions) but strict about the fields it actually pulls
    into :class:`Floor`.
    """
    path = Path(path)
    raw = load_run(path)

    players = raw.get("players")
    if isinstance(players, list) and len(players) > 1:
        raise ValueError(
            f"{path}: multi-player .run files are not supported "
            f"(found {len(players)} players); harness is single-player only"
        )

    history = raw.get("map_point_history")
    if not history:
        raise ValueError(f"{path}: empty map_point_history")

    floors: list[Floor] = []
    ordinal = 0
    for act_history in history:
        for row in act_history:
            ordinal += 1
            stats_list = row.get("player_stats") or []
            if not stats_list:
                raise ValueError(f"{path}: floor {ordinal} has no player_stats entry")
            stats = stats_list[0]
            floors.append(_floor_from_stats(ordinal, row, stats))

    return Run(
        seed=str(raw.get("seed", "")),
        ascension=int(raw.get("ascension", 0)),
        build_id=str(raw.get("build_id", "")),
        won=bool(raw.get("win", False)),
        acts=[str(a) for a in raw.get("acts", [])],
        floors=floors,
        source_path=path.resolve(),
    )


def _floor_from_stats(ordinal: int, row: dict[str, Any], stats: dict[str, Any]) -> Floor:
    """Build a :class:`Floor` from the scraper's per-floor blob.

    Scraper field shapes vary by room type — ``card_choices`` is a list
    of ``{card: {id}, was_picked}`` dicts; ``relic_choices`` is the
    same with ``relic`` / ``was_chosen``; ``ancient_choice`` (Act-1
    first room only) uses ``{TextKey, was_chosen}`` directly. We union
    relic + ancient picks into ``relics_picked`` so downstream
    consumers don't need to special-case the first floor.
    """
    relics_picked: list[str] = []
    for entry in stats.get("ancient_choice", []) or []:
        if entry.get("was_chosen"):
            text_key = entry.get("TextKey")
            if isinstance(text_key, str):
                relics_picked.append(f"RELIC.{text_key}")
    for entry in stats.get("relic_choices", []) or []:
        if entry.get("was_picked") or entry.get("was_chosen"):
            relic = entry.get("relic") or {}
            relic_id = relic.get("id") or entry.get("TextKey")
            if isinstance(relic_id, str):
                if not relic_id.startswith(_TEXTKEY_RELIC_PREFIX):
                    relic_id = f"RELIC.{relic_id}"
                relics_picked.append(relic_id)

    # `cards_gained` / `cards_removed` entries are `{"id": "CARD.X"}`
    # dicts; `upgraded_cards` entries in the v0.103.2 corpus are *bare*
    # card-id strings (no dict wrapper). Normalize to a uniform string
    # list — accepts both shapes so the parser keeps working if the
    # scraper schema converges on either form.
    def _ids(entries: Any) -> list[str]:
        out: list[str] = []
        for e in entries or []:
            if isinstance(e, str):
                out.append(e)
            elif isinstance(e, dict):
                cid = e.get("id")
                if isinstance(cid, str):
                    out.append(cid)
        return out

    cards_gained = _ids(stats.get("cards_gained"))
    cards_removed = _ids(stats.get("cards_removed"))
    upgraded_cards = _ids(stats.get("upgraded_cards"))

    event_choices: list[str] = []
    for entry in stats.get("event_choices", []) or []:
        if not entry.get("was_chosen"):
            continue
        title = entry.get("title") or {}
        key = title.get("key") or entry.get("TextKey") or ""
        # `EVENT_ID.pages.INITIAL.options.<OPT>.title` → `<opt>`.
        tail = key.split(".")[-2] if key.endswith(".title") else key.split(".")[-1]
        if tail:
            event_choices.append(tail.lower())

    return Floor(
        ordinal=ordinal,
        map_point_type=str(row.get("map_point_type", "unknown")).lower(),
        hp_after=int(stats.get("current_hp", 0)),
        gold_after=int(stats.get("current_gold", 0)),
        relics_picked=relics_picked,
        cards_gained=cards_gained,
        cards_removed=cards_removed,
        upgraded_cards=upgraded_cards,
        event_choices=event_choices,
        raw_player_stats=stats,
    )
