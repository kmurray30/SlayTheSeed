"""Scenario/sweep specifications and combat construction.

Sweeps compare DECK VARIANTS along a dedicated axis: a sweep carries one or
more named full decks (``SweepSpec.decks``), each expanded into its own cell so
runs of different decks are seed-paired and directly comparable. Encounters may
be either a registry encounter id (e.g. ``"MAWLER_NORMAL"``) or a custom
``monsters:`` lineup (e.g. ``"monsters:Nibbit,Nibbit"``) for foes that have no
encounter id of their own.
"""

from __future__ import annotations

import difflib
import functools
from dataclasses import asdict, dataclass
from typing import Any

import sts2_rs

DECK_PRESETS: dict[str, list[str]] = {
    "ironclad_starter": ["STRIKE_IRONCLAD"] * 5 + ["DEFEND_IRONCLAD"] * 4 + ["BASH"],
    # Starter deck, everything upgraded (card ids carry the "+1" suffix).
    "starter_upgraded": (["STRIKE_IRONCLAD+1"] * 5 + ["DEFEND_IRONCLAD+1"] * 4 + ["BASH+1"]),
    # Attack-dense rush: cheap direct damage, minimal defense.
    # (CLOTHESLINE / HEAVY_BLADE are not in the parity-audited pool; substituted
    # with UPPERCUT, POMMEL_STRIKE, THUNDERCLAP, HEMOKINESIS.)
    "aggro_rush": (
        ["STRIKE_IRONCLAD"] * 4
        + ["ANGER"] * 2
        + ["TWIN_STRIKE", "UPPERCUT", "POMMEL_STRIKE", "THUNDERCLAP", "HEMOKINESIS"]
        + ["DEFEND_IRONCLAD"] * 2
    ),
    # Block-heavy turtle: keep a few attacks so fights still end.
    "turtle": (
        ["DEFEND_IRONCLAD"] * 4
        + ["SHRUG_IT_OFF"] * 2
        + ["IRON_WAVE"] * 2
        + ["IMPERVIOUS"]
        + ["STRIKE_IRONCLAD"] * 2
        + ["BASH"]
    ),
    # Scaling ramp: powers + strength synergy where multi-turn planning pays.
    # (DEMON_FORM / RUPTURE / etc. carry the "_CARD" suffix in the pool.)
    "scaling": (
        ["DEMON_FORM_CARD"]
        + ["INFLAME"] * 2
        + ["WHIRLWIND", "UPPERCUT"]
        + ["STRIKE_IRONCLAD"] * 3
        + ["DEFEND_IRONCLAD"] * 3
        + ["BASH"]
    ),
}

# Encounter ids surfaced in the web UI dropdown (kept in sync with the SPA).
UI_ENCOUNTERS: list[str] = [
    "SHRINKER_BEETLE_WEAK",
    "FUZZY_WURM_CRAWLER_WEAK",
    "MAWLER_NORMAL",
    "FLYCONID_NORMAL",
    "FOGMOG_NORMAL",
    "SLITHERING_STRANGLER_NORMAL",
    "SNAPPING_JAXFRUIT_NORMAL",
    "VINE_SHAMBLER_NORMAL",
    "BYGONE_EFFIGY_ELITE",
    "BYRDONIS_ELITE",
    "PHROG_PARASITE_ELITE",
    "VANTOM_BOSS",
]

# Dummy base encounter id required by build_scenario_state; the engine ignores
# it entirely when an explicit `monsters=` lineup is supplied (probed).
_MONSTER_BASE = "MAWLER_NORMAL"
_MAX_DECK = 100


@functools.cache
def _registry(kind: str) -> frozenset[str]:
    """Return the (cached) set of registry ids for `kind`.

    The registry is static for a given engine wheel, so caching is safe.

    Args:
        kind: One of "card", "monster", "relic", "power", "potion", "orb".

    Returns:
        Frozen set of all registered ids of that kind.
    """
    return frozenset(sts2_rs.list_registry_names(kind))


def validate_deck(name: str, cards: list[str] | tuple[str, ...]) -> None:
    """Validate a named deck against the card registry.

    Args:
        name: Human-readable deck name (used in error messages).
        cards: The card ids making up the deck. Upgrade suffixes ("+1", "+2")
            are stripped before the registry membership check.

    Raises:
        ValueError: If the deck name is empty or contains "/", or the deck is
            empty, exceeds the size cap, or names a card id not in the registry
            (with a close-match suggestion when one exists).
    """
    # Deck names become part of the enc/asc/deck/strategy bucket key (Task 2),
    # so a "/" would corrupt it.
    if not name or "/" in name:
        raise ValueError(f"invalid deck name {name!r} (must be non-empty and contain no '/')")
    if not cards:
        raise ValueError(f"deck {name!r} is empty")
    if len(cards) > _MAX_DECK:
        raise ValueError(f"deck {name!r} has {len(cards)} cards (max {_MAX_DECK})")
    known = _registry("card")
    for c in cards:
        base = c.split("+", 1)[0]  # strip upgrade suffix
        if base not in known:
            hint = difflib.get_close_matches(base, known, n=1)
            extra = f" (did you mean {hint[0]!r}?)" if hint else ""
            raise ValueError(f"deck {name!r}: unknown card id {c!r}{extra}")


def parse_encounter(encounter: str) -> tuple[str, list[str]]:
    """Split an encounter axis entry into ``(encounter_id, monsters)``.

    A ``"monsters:Nibbit,Nibbit"`` entry yields the dummy base id plus the
    explicit monster lineup; a plain id yields ``(id, [])``.

    Args:
        encounter: Either a registry encounter id or a ``monsters:`` lineup.

    Returns:
        A tuple of (encounter_id, monsters) where monsters is empty for plain
        encounter ids.
    """
    if encounter.startswith("monsters:"):
        names = [m.strip() for m in encounter[len("monsters:") :].split(",") if m.strip()]
        return _MONSTER_BASE, names
    return encounter, []


def validate_encounter(encounter: str) -> None:
    """Validate a ``monsters:`` entry against the monster registry.

    Plain encounter ids pass through unvalidated (the engine validates them at
    build time).

    Args:
        encounter: Either a registry encounter id or a ``monsters:`` lineup.

    Raises:
        ValueError: If a ``monsters:`` entry names no monsters, or names a
            monster containing "/" or not in the registry (with a close-match
            suggestion when one exists).
    """
    _, monsters = parse_encounter(encounter)
    if not monsters and encounter.startswith("monsters:"):
        raise ValueError("monsters: entry names no monsters")
    known = _registry("monster")
    for m in monsters:
        # "/" would corrupt the enc/asc/deck/strategy bucket key (Task 2).
        if "/" in m:
            raise ValueError(f"invalid monster name {m!r}")
        if m not in known:
            hint = difflib.get_close_matches(m, known, n=1)
            extra = f" (did you mean {hint[0]!r}?)" if hint else ""
            raise ValueError(f"unknown monster {m!r}{extra}")


#: `ScenarioSpec.encounter` for a scenario whose fight comes from a pasted
#: state rather than the encounter registry. The lineup, deck, HP and ascension
#: all ride in the state; this is a bucket label, not a lookup key.
IMPORTED_ENCOUNTER = "imported"


@dataclass(frozen=True)
class ScenarioSpec:
    """One combat configuration (everything but the per-trial seed).

    The deck is carried inline as a named list of card ids (`deck_name` +
    `deck_cards`) so a sweep can compare arbitrary deck variants, not just
    presets. The `encounter` may be a registry encounter id or a `monsters:`
    custom lineup (see :func:`parse_encounter`).

    A scenario may instead carry `start_state`: an engine wire dict (as JSON)
    captured from a real position — see :mod:`workbench.state_import`. Then
    every trial begins from THAT board rather than from turn 1 of a freshly
    built fight, and `encounter` / `deck_cards` / `hp` are ignored because the
    state already carries them. That is the difference between "how does this
    deck do against that encounter" and "how does this position play out".
    """

    encounter: str
    ascension: int = 0
    character: str = "Ironclad"
    deck_name: str = "ironclad_starter"
    deck_cards: tuple[str, ...] = tuple(DECK_PRESETS["ironclad_starter"])
    hp: int = 80
    max_hp: int = 80
    relics: tuple[str, ...] = ()
    potions: tuple[str, ...] = ()
    start_state: str = ""
    """An `sts2_rs.state_to_dict` wire dict as JSON, or `""` to build fresh.

    Carried as a string, not a dict, so the dataclass stays frozen and
    hashable — the runner ships scenarios across process boundaries.
    """
    start_label: str = ""
    """Human name for this position, e.g. `"drank the potion"`. Bucket key."""

    def __post_init__(self) -> None:
        # Coerce so callers may pass plain lists; keep instances frozen.
        object.__setattr__(self, "deck_cards", tuple(self.deck_cards))
        object.__setattr__(self, "relics", tuple(self.relics))
        object.__setattr__(self, "potions", tuple(self.potions))

    @property
    def deck(self) -> list[str]:
        return list(self.deck_cards)

    @property
    def from_state(self) -> bool:
        return bool(self.start_state)


@dataclass(frozen=True)
class SweepCell:
    """One (scenario, strategy) bucket of a sweep; seeds are added by the runner."""

    scenario: ScenarioSpec
    strategy: str


@dataclass(frozen=True)
class SweepSpec:
    """A full sweep request: encounters x ascensions x decks x strategies x n_seeds.

    Decks are a dedicated axis: each entry of `decks` is a ``(name, cards)``
    pair expanded into its own cells, so deck variants are seed-paired and
    directly comparable. Encounters may be registry ids or `monsters:` custom
    lineups. Ironclad-only for now; character/relics/potions are not swept.
    """

    name: str
    encounters: tuple[str, ...]
    ascensions: tuple[int, ...]
    strategies: tuple[str, ...]
    n_seeds: int
    decks: tuple[tuple[str, tuple[str, ...]], ...] = (
        ("ironclad_starter", tuple(DECK_PRESETS["ironclad_starter"])),
    )
    hp: int = 80
    max_hp: int = 80
    # consumed by the mcts agent via the runner
    mcts_sims: int = 400
    mcts_depth: int = 200
    start_states: tuple[tuple[str, str], ...] = ()
    """`(label, wire_json)` positions to sweep FROM, instead of building fights.

    When set this REPLACES the encounter / deck / ascension axes — a pasted
    board already fixes all three — leaving `start_states x strategies`. That
    is the counterfactual comparison: the same fight from the same turn, one
    cell per variant of the board, seed-paired across every strategy.
    """
    positions: tuple[tuple[str, str], ...] = ()
    """`(label, position_json)` — the sandbox positions behind `start_states`.

    Display-only: the runner reads the wire in `start_states`; this is what
    lets SimLab's history show and reopen the position a sweep started from
    (`simlab-position/1`, see :mod:`simlab.position`). Absent on specs older
    than the sandbox and on CLI sweeps.
    """

    def __post_init__(self) -> None:
        # Coerce so callers (CLI/web) may pass plain lists; keep instances frozen.
        object.__setattr__(self, "encounters", tuple(self.encounters))
        object.__setattr__(self, "ascensions", tuple(int(a) for a in self.ascensions))
        object.__setattr__(self, "strategies", tuple(self.strategies))
        object.__setattr__(self, "decks", tuple((str(n), tuple(cs)) for n, cs in self.decks))
        object.__setattr__(
            self, "start_states", tuple((str(n), str(b)) for n, b in self.start_states)
        )
        object.__setattr__(self, "positions", tuple((str(n), str(b)) for n, b in self.positions))
        names = [n for n, _ in self.decks]
        # Deck names become part of the enc/asc/deck/strategy bucket key (Task 2),
        # so they must be non-empty, "/"-free, and unique across the sweep.
        for n in names:
            if not n or "/" in n:
                raise ValueError(f"invalid deck name {n!r} (must be non-empty and contain no '/')")
        if len(names) != len(set(names)):
            dupes = sorted({n for i, n in enumerate(names) if n in names[:i]})
            raise ValueError(f"duplicate deck names in sweep: {dupes}")
        # Start-state labels land in the SAME bucket key (as the deck column),
        # so they carry the same constraints — a duplicate label would silently
        # merge two different positions into one distribution.
        labels = [n for n, _ in self.start_states]
        for n in labels:
            if not n or "/" in n:
                raise ValueError(
                    f"invalid start-state label {n!r} (must be non-empty and contain no '/')"
                )
        if len(labels) != len(set(labels)):
            dupes = sorted({n for i, n in enumerate(labels) if n in labels[:i]})
            raise ValueError(f"duplicate start-state labels in sweep: {dupes}")

    def cells(self) -> list[SweepCell]:
        if self.start_states:
            return [
                SweepCell(
                    scenario=ScenarioSpec(
                        encounter=IMPORTED_ENCOUNTER,
                        # Read off the board, not off the sweep: the position
                        # was played at one ascension and the column must say
                        # which.
                        ascension=start_state_ascension(blob),
                        deck_name=label,
                        deck_cards=(),
                        start_state=blob,
                        start_label=label,
                    ),
                    strategy=s,
                )
                for label, blob in self.start_states
                for s in self.strategies
            ]
        return [
            SweepCell(
                scenario=ScenarioSpec(
                    encounter=e,
                    ascension=a,
                    deck_name=dname,
                    deck_cards=dcards,
                    hp=self.hp,
                    max_hp=self.max_hp,
                ),
                strategy=s,
            )
            for e in self.encounters
            for a in self.ascensions
            for dname, dcards in self.decks
            for s in self.strategies
        ]

    def to_json(self) -> dict[str, Any]:
        d = asdict(self)
        # JSON-friendly: emit lists (round-trips back to tuples via __post_init__).
        d["encounters"] = list(self.encounters)
        d["ascensions"] = list(self.ascensions)
        d["strategies"] = list(self.strategies)
        d["decks"] = [[name, list(cards)] for name, cards in self.decks]
        d["start_states"] = [[label, blob] for label, blob in self.start_states]
        d["positions"] = [[label, blob] for label, blob in self.positions]
        return d

    @classmethod
    def from_json(cls, d: dict[str, Any]) -> SweepSpec:
        d = dict(d)
        # Back-compat: a legacy `deck_preset` maps to a single named deck axis.
        if "decks" not in d and "deck_preset" in d:
            preset = d["deck_preset"]
            if preset not in DECK_PRESETS:
                raise ValueError(
                    f"unknown deck_preset {preset!r}; valid presets: {sorted(DECK_PRESETS)}"
                )
            d["decks"] = [[preset, list(DECK_PRESETS[preset])]]
        d.pop("deck_preset", None)
        return cls(**d)


@functools.lru_cache(maxsize=64)
def _parsed_start_state(blob: str) -> dict[str, Any]:
    """Parse a `start_state` blob once per process.

    A sweep re-materializes the same position for every (strategy, seed) cell,
    so the JSON parse is on the hot path. Cached by the blob itself, which is
    also the cell's identity — two cells with the same board share the parse.
    """
    import json

    value = json.loads(blob)
    if not isinstance(value, dict):
        raise ValueError("start_state must be a JSON object (an engine wire dict)")
    return value


def start_state_ascension(blob: str) -> int:
    """The ascension the pasted board was played at, or 0 if it does not say.

    Read from the state rather than taken from the sweep, so the telemetry
    column cannot disagree with the fight it describes.
    """
    rng = _parsed_start_state(blob).get("rng")
    value = rng.get("ascension") if isinstance(rng, dict) else None
    return int(value) if isinstance(value, int) and not isinstance(value, bool) else 0


def build_combat_from_state(blob: str, seed: int) -> Any:
    """Rebuild a pasted board, with this trial's own future dice.

    The visible position is reproduced exactly — hand, draw/discard/exhaust
    order, powers, intents, energy, block. What is replaced is the RNG
    LINEAGE: every stream is re-seeded from `seed` and rewound to zero, so
    trials diverge on the rolls that have not happened yet (reshuffles,
    monster AI, card generation) while starting from the identical board.

    Without that reseed every trial replays the world's frozen stream and the
    distribution collapses to a single point — the reseed is what makes this a
    sample rather than a replay.

    Same (blob, seed) -> byte-identical combat, so cross-strategy comparisons
    stay seed-paired exactly as they are for a freshly built scenario.
    """
    import copy

    wire = copy.deepcopy(_parsed_start_state(blob))
    rng = dict(wire.get("rng") or {})
    rng["seed"] = int(seed)
    # `run_seed` is a u32 on the wire; the trial seed IS the new lineage.
    rng["run_seed"] = int(seed) & 0xFFFF_FFFF
    # Dropped, not zeroed one-by-one: an absent `counters` block leaves every
    # stream of the new seed at position 0, which is the whole intent.
    rng.pop("counters", None)
    wire["rng"] = rng
    return sts2_rs.state_from_dict(wire)


def build_combat(spec: ScenarioSpec, seed: int) -> Any:
    """Construct a seeded sts2_rs CombatState for `spec`.

    Same (spec, seed) -> byte-identical combat (shuffle, enemy rolls),
    which is what makes cross-strategy comparisons seed-paired. A `monsters:`
    encounter builds the explicit lineup (the base encounter id is ignored by
    the engine in that case). A scenario carrying a `start_state` is rebuilt
    from that board instead (see :func:`build_combat_from_state`).
    """
    if spec.from_state:
        return build_combat_from_state(spec.start_state, seed)
    enc_id, monsters = parse_encounter(spec.encounter)
    if monsters:
        return sts2_rs.build_scenario_state(
            seed=seed,
            character=spec.character,
            deck=spec.deck,
            hp=spec.hp,
            max_hp=spec.max_hp,
            relics=list(spec.relics),
            encounter=enc_id,
            ascension=spec.ascension,
            potions=list(spec.potions),
            monsters=monsters,
        )
    return sts2_rs.build_scenario_state(
        seed=seed,
        character=spec.character,
        deck=spec.deck,
        hp=spec.hp,
        max_hp=spec.max_hp,
        relics=list(spec.relics),
        encounter=enc_id,
        ascension=spec.ascension,
        potions=list(spec.potions),
    )
