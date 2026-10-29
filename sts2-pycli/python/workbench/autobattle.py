"""Autobattle mode — the run-level drill loop's pure core.

Autobattle mode answers a training problem: drilling *run-level* decisions as
isolated flashcards doesn't work, because a cold position costs far more time
to understand than to decide. In real play your context accrues — the deck
changes one card at a time — so the decision is cheap. A stranger's 22-card
deck is not.

So an autobattle replays one whole run with the combats (and only the combats)
auto-played by the native MCTS agent, stopping at every run-level decision for
the human. Context accrues exactly as it does in real play, and the cost of
understanding it is paid once per run instead of once per decision.

The load-bearing UI element is the **change diff** at each stop — "since your
last stop: +Inflame, −2 Strikes, 52/75 HP, +Bag of Prep" — not a deck list.
You rebuild context by reading only what moved.

The fights themselves are watchable: the same leg can be skipped in one call
(:meth:`~workbench.branching.BranchingLiveSession.autobattle_advance`) or walked
one paintable decision at a time
(:meth:`~workbench.branching.BranchingLiveSession.autobattle_step`), so the
combats play out on screen instead of being inferred from the diff's HP line.
Both share this module's stop rule and stop→stop diff window.

This module is the **pure** core (no HTTP, no game engine, no net): it decides
which states are stop points and diffs two run-level states. Orchestration —
the autoplay loop — lives in :mod:`workbench.branching`; the HTTP surface is
``/api/live/autobattle/advance`` and ``/api/live/autobattle/step`` in
``server.py``.

Name resolution is the caller's job: pass states whose loc-keys are already
resolved (``localization.resolve_state_names``), exactly as
:meth:`~workbench.branching.BranchingLiveSession.apply` does before rendering
its labels. This module reads whatever ``name`` it is given.
"""

from __future__ import annotations

from collections import Counter
from dataclasses import asdict, dataclass, field, replace
from typing import Any

_MINUS = "−"  # real minus glyph, matching workbench.statediff

#: Decision kinds that hand control to the human in an autobattle. Everything else
#: — combat, the game-over screen, and unrecognised surfaces — is auto-played.
#: ``card_select`` is included because it is how card removal / upgrade /
#: transform choices surface, which are genuine deckbuilding decisions.
STOP_DECISIONS = frozenset(
    {
        "bundle_select",
        "card_reward",
        "card_select",
        "event_choice",
        "map_select",
        "rest_site",
        "shop",
        "treasure",
    }
)

#: Decisions that never stop, regardless of configuration: combat is the whole
#: point of skipping, and ``game_over`` ends the autobattle rather than prompting.
NEVER_STOP = frozenset({"combat_play", "game_over"})

#: How close two options' policy probabilities must be for a disagreement to be
#: reported as a near-tie rather than a miss. Marking a coin flip "wrong" trains
#: the player to match noise, which is the opposite of the point.
#:
#: Deliberately NOT borrowed from the combat coach's ``NEAR_TIE_DROP_PCT`` (3):
#: that is measured in win-probability points and this is a policy probability,
#: which is a different quantity on a different scale. Retune from feel.
NEAR_TIE_PROB_MARGIN = 0.05

#: Verdict bands, worst-to-best ordering irrelevant — these are labels.
BAND_MATCHED = "matched"
BAND_NEAR_TIE = "near_tie"
BAND_DIFFERED = "differed"


def verdict_band(agreed: bool, margin: float | None, near_tie: float = NEAR_TIE_PROB_MARGIN) -> str:
    """Which of the three verdict bands a committed pick falls in.

    ``margin`` is the champion's policy probability minus *yours* — how much
    less the policy liked what you actually did. It is deliberately not the
    top-two spread: your pick may be third, and what matters is the gap to the
    option you passed up.

    ``None`` (no probabilities available — the heuristic brain, or a net that
    couldn't load) collapses to the two-band behaviour rather than guessing.
    """
    if agreed:
        return BAND_MATCHED
    if margin is not None and margin <= near_tie:
        return BAND_NEAR_TIE
    return BAND_DIFFERED


def is_stop_point(state: dict[str, Any], stop_decisions: frozenset[str] = STOP_DECISIONS) -> bool:
    """True when *state* is a run-level decision the human should make.

    Defensive: a state without a recognisable ``decision`` is auto-played
    rather than stopping the autobattle on a screen the UI may not render.
    """
    decision = state.get("decision")
    if not isinstance(decision, str) or decision in NEVER_STOP:
        return False
    return decision in stop_decisions


def _card_key(card: dict[str, Any]) -> tuple[str, bool]:
    """Identity of a deck card for diffing: display name plus upgrade state.

    Upgrades are part of the identity so that upgrading Strike reads as one
    card leaving and another arriving, which :func:`_deck_delta` then folds
    back into an ``upgraded`` line.
    """
    name = card.get("name") or card.get("id") or card.get("card_id") or "?"
    return str(name), bool(card.get("upgraded"))


@dataclass(frozen=True)
class DeckDelta:
    """What changed in the master deck between two stops."""

    added: list[str] = field(default_factory=list)
    removed: list[str] = field(default_factory=list)
    upgraded: list[str] = field(default_factory=list)

    @property
    def is_empty(self) -> bool:
        return not (self.added or self.removed or self.upgraded)


def _deck_delta(prev: list[dict[str, Any]], cur: list[dict[str, Any]]) -> DeckDelta:
    """Multiset diff of two master decks, folding upgrades into their own bucket.

    A card whose unupgraded copy left while an upgraded copy of the same name
    arrived is reported as an upgrade, not as a removal plus an addition —
    that is what actually happened at a rest site or Enchant.
    """
    before = Counter(_card_key(c) for c in prev)
    after = Counter(_card_key(c) for c in cur)
    gained = after - before
    lost = before - after

    upgraded: list[str] = []
    for (name, is_upgraded), count in list(gained.items()):
        if not is_upgraded:
            continue
        # Pair each newly-arrived upgraded copy with an unupgraded copy that
        # left in the same window; only surplus copies count as truly new.
        paired = min(count, lost.get((name, False), 0))
        if paired:
            upgraded.extend([name] * paired)
            gained[name, True] -= paired
            lost[name, False] -= paired

    def _names(counter: Counter[tuple[str, bool]]) -> list[str]:
        out: list[str] = []
        for (name, is_upgraded), count in counter.items():
            if count <= 0:
                continue
            out.extend([f"{name}+" if is_upgraded else name] * count)
        return sorted(out)

    return DeckDelta(added=_names(gained), removed=_names(lost), upgraded=sorted(upgraded))


def _named_items(items: list[dict[str, Any]] | None) -> Counter[str]:
    return Counter(str(i.get("name") or i.get("id") or "?") for i in (items or []))


def _gained_lost(
    prev: list[dict[str, Any]] | None, cur: list[dict[str, Any]] | None
) -> tuple[list[str], list[str]]:
    """``(gained, lost)`` display names for a relic / potion tray."""
    before, after = _named_items(prev), _named_items(cur)
    return sorted((after - before).elements()), sorted((before - after).elements())


@dataclass(frozen=True)
class ContextDiff:
    """Everything that changed between the previous stop and this one.

    Rendered by the UI as the "since your last stop" card. Every field is
    optional-safe: a backend that omits gold or floor yields ``None`` there
    rather than breaking the autobattle.
    """

    from_floor: int | None
    to_floor: int | None
    autoplayed: int
    hp: int | None
    max_hp: int | None
    hp_delta: int | None
    gold: int | None
    gold_delta: int | None
    deck_size: int
    deck: DeckDelta
    relics_gained: list[str]
    relics_lost: list[str]
    potions_gained: list[str]
    potions_lost: list[str]
    lines: list[str]

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def _int_or_none(value: Any) -> int | None:
    return value if isinstance(value, int) else None


def context_diff(
    prev: dict[str, Any] | None, cur: dict[str, Any], *, autoplayed: int = 0
) -> ContextDiff:
    """Diff the run-level state between two autobattle stops.

    ``prev`` is ``None`` at the first stop of a run — every "delta" field is
    then ``None`` or empty and only the absolute state is reported, which is
    what the opening card should show.
    """
    prev_player = (prev or {}).get("player") or {}
    cur_player = cur.get("player") or {}

    hp = _int_or_none(cur_player.get("hp"))
    max_hp = _int_or_none(cur_player.get("max_hp"))
    prev_hp = _int_or_none(prev_player.get("hp")) if prev else None
    hp_delta = hp - prev_hp if hp is not None and prev_hp is not None else None

    gold = _int_or_none(cur_player.get("gold"))
    prev_gold = _int_or_none(prev_player.get("gold")) if prev else None
    gold_delta = gold - prev_gold if gold is not None and prev_gold is not None else None

    # At the first stop there is nothing to diff against. Report absolutes only:
    # diffing against an empty deck would render the whole starter deck as
    # "Added", which is the opposite of the "read only what moved" premise.
    if prev is None:
        deck = DeckDelta()
        relics_gained = relics_lost = potions_gained = potions_lost = []
    else:
        deck = _deck_delta(prev_player.get("deck") or [], cur_player.get("deck") or [])
        relics_gained, relics_lost = _gained_lost(
            prev_player.get("relics"), cur_player.get("relics")
        )
        potions_gained, potions_lost = _gained_lost(
            prev_player.get("potions"), cur_player.get("potions")
        )

    diff = ContextDiff(
        from_floor=_int_or_none((prev or {}).get("floor")),
        to_floor=_int_or_none(cur.get("floor")),
        autoplayed=autoplayed,
        hp=hp,
        max_hp=max_hp,
        hp_delta=hp_delta,
        gold=gold,
        gold_delta=gold_delta,
        deck_size=len(cur_player.get("deck") or []),
        deck=deck,
        relics_gained=relics_gained,
        relics_lost=relics_lost,
        potions_gained=potions_gained,
        potions_lost=potions_lost,
        lines=[],
    )
    return replace(diff, lines=render_lines(diff))


def _signed(delta: int) -> str:
    return f"{_MINUS if delta < 0 else '+'}{abs(delta)}"


def render_lines(diff: ContextDiff) -> list[str]:
    """Human-readable "since your last stop" lines, most context-carrying first.

    Deck changes lead because they are what you must know to decide the next
    pick; resources follow. Unchanged quantities are omitted — the whole point
    is that you read only what moved.
    """
    out: list[str] = []
    if diff.deck.upgraded:
        out.append("Upgraded " + ", ".join(diff.deck.upgraded))
    if diff.deck.added:
        out.append("Added " + ", ".join(diff.deck.added))
    if diff.deck.removed:
        out.append("Removed " + ", ".join(diff.deck.removed))
    if diff.relics_gained:
        out.append("Relic " + ", ".join(diff.relics_gained))
    if diff.relics_lost:
        out.append("Lost relic " + ", ".join(diff.relics_lost))
    if diff.potions_gained:
        out.append("Potion " + ", ".join(diff.potions_gained))
    if diff.potions_lost:
        out.append("Used potion " + ", ".join(diff.potions_lost))
    if diff.hp is not None:
        hp_line = f"HP {diff.hp}"
        if diff.max_hp is not None:
            hp_line += f"/{diff.max_hp}"
        if diff.hp_delta:
            hp_line += f" ({_signed(diff.hp_delta)})"
        out.append(hp_line)
    if diff.gold is not None:
        gold_line = f"Gold {diff.gold}"
        if diff.gold_delta:
            gold_line += f" ({_signed(diff.gold_delta)})"
        out.append(gold_line)
    if diff.autoplayed:
        out.append(f"{diff.autoplayed} decision{'s' if diff.autoplayed != 1 else ''} auto-played")
    return out
