"""Non-deckbuildable card filter for the synergy pipeline.

Curses, statuses, and combat-generated / event-quest token cards are not
choices a player makes when building a deck, so they carry no meaningful
"synergy" and only pollute the observational pair universe (the value net,
for instance, assigns spurious second-difference scores to curse pairs such
as ``ASCENDERS_BANE|GREED``).  This module lists those tokens and exposes a
membership predicate used to prune them from the pair/vocabulary construction
in :mod:`synergy.observational` and :mod:`synergy.archetype_discover`.

Scope: a token is excluded iff, in the v0.107.1 content specs
(``sts2-rs/crates/sts2-content/src/cards/status_specs_generated.rs``), its
``card_type`` is ``Curse`` or ``Status``, or its ``rarity`` is ``Token``
(combat-generated: Shiv, Soul, Fuel, Minion*, …) or ``Quest`` (event artifact
cards: Lantern Key, Spoils Map, Byrdonis Egg).  Real deckbuildable cards —
including Event-rarity colorless cards like Apotheosis or Outmaneuver — are
kept.  The filter deliberately does *not* touch combat/econ baseline decks:
baseline composition cancels in the four-cell synergy second difference, so
those decks stay faithful to the real corpus.
"""

from __future__ import annotations

from collections.abc import Iterable

#: Card tokens (engine ``CARD.`` id with the prefix stripped) that are never
#: deckbuilding choices.  Sourced from the v0.107.1 status card-spec table;
#: see the module docstring for the exact card_type / rarity predicate.
NON_DECKBUILDABLE: frozenset[str] = frozenset(
    {
        # --- Curses (card_type Curse) ---
        "ASCENDERS_BANE",
        "BAD_LUCK",
        "CLUMSY",
        "CURSE_OF_THE_BELL",
        "DEBT",
        "DECAY",
        "DOUBT",
        "ENTHRALLED",
        "FOLLY",
        "GREED",
        "GUILTY",
        "INJURY",
        "NORMALITY",
        "PAIN",
        "PARASITE",
        "POOR_SLEEP",
        "REGRET",
        "SHAME",
        "SPORE_MIND",
        "WRITHE",
        # --- Statuses (card_type Status) ---
        "BECKON",
        "BURN",
        "DAZED",
        "DEBRIS",
        "DISINTEGRATION",
        "FRANTIC_ESCAPE",
        "INFECTION",
        "MIND_ROT",
        "SLIMED",
        "SLOTH_STATUS",
        "SOOT",
        "TOXIC",
        "VOID",
        "WASTE_AWAY",
        "WITHER",
        "WOUND",
        # --- Combat-generated tokens (rarity Token) ---
        "FUEL",
        "GIANT_ROCK",
        "LUMINESCE",
        "MINION_DIVE_BOMB",
        "MINION_SACRIFICE",
        "MINION_STRIKE",
        "SHIV",
        "SOUL",
        "SOVEREIGN_BLADE",
        "SWEEPING_GAZE",
        # --- Event / quest artifact cards (rarity Quest) ---
        "BYRDONIS_EGG",
        "LANTERN_KEY",
        "SPOILS_MAP",
    }
)


def is_deckbuildable(token: str) -> bool:
    """Return ``True`` unless *token* is a curse / status / token / quest card."""
    return token not in NON_DECKBUILDABLE


def filter_deckbuildable(cards: Iterable[str]) -> list[str]:
    """Drop non-deckbuildable tokens from *cards*, preserving order."""
    return [c for c in cards if c not in NON_DECKBUILDABLE]
