"""Rank the in-combat card selections no trained head covers.

Why this exists
---------------
``card_select`` is answered by the run-policy net via
:func:`realgame_ppo._build_card_select`, whose surface table maps only the
*deck-mutation* prompts (``TO_REMOVE`` / ``TO_TRANSFORM`` / ``TO_ENCHANT`` /
``TO_UPGRADE`` / ``TO_OBTAIN``). An in-combat selection -- Burning Pact asking
which card to Exhaust, Gambler's Brew which to replace -- matches none of them,
so the builder returns ``None`` and the advisor fell through to
``_unranked``, whose documented contract is *"defaulting to the first option,
NOT a recommendation"*.

That default is not neutral: it exhausts whatever sits leftmost in hand.
Measured over the 2026-08-06 ascension climb, **8 of 8** in-hand picks were
index 0, including a Burning Pact that exhausted a ``Strike+`` while an
unupgraded ``Strike`` sat next to it. See
``docs/experiments/2026-08-06-ascension-climb-issues.md`` issue 5.

This module is deliberately NOT a net. It is a small, explicit heuristic that
answers the one question the leftmost default gets wrong most often: *does the
chosen card get hurt or helped?* -- and then picks the worst or the best card
accordingly.

Direction is data, not taste
----------------------------
Every per-card prompt ships its English text in
``localization_eng`` as ``"<ID>.selectionScreenPrompt"``. The tables below are
transcribed from those strings, so "Choose a card to Copy" (Dual Wield) is
classified BOOST because the game says so, not because someone guessed. A
prompt that is not in the tables stays UNKNOWN and keeps the old leftmost
behaviour -- with the note saying so, which is the point: a guess in the wrong
direction is worse than an admitted arbitrary pick.

This lives beside :mod:`realgame_ppo` rather than inside it on purpose.
``_build_card_select`` is shared with the PPO driver that the engine-world and
rung-3.5 arms are measured with; changing it would move a comparator mid-ladder.
"""

from __future__ import annotations

from typing import Any

#: The chosen card SUFFERS -- exhausted, discarded, removed, transformed away.
#: Pick the worst card.
DISPOSE = "dispose"

#: The chosen card BENEFITS -- copied, retained, drawn, made free. Pick the
#: best card.
BOOST = "boost"

#: No idea which way this prompt points. Keep the leftmost default and say so.
UNKNOWN = "unknown"


#: Keyed on ``CardSelectorPrefs``' shared prompts (``CardSelectorPrefs.cs``).
#: The deck-mutation four are here for completeness even though
#: ``_build_card_select`` claims them first -- if it ever declines one (it bails
#: on any ``max_select > 1``), the direction is still known.
_PROMPT_DIRECTION: dict[str, str] = {
    "TO_EXHAUST": DISPOSE,
    "TO_DISCARD": DISPOSE,
    "TO_REMOVE": DISPOSE,
    "TO_TRANSFORM": DISPOSE,
    "TO_UPGRADE": BOOST,
    "TO_ENCHANT": BOOST,
}


#: Keyed on the source model id -- the ``AbstractModel source`` argument that
#: ``CardSelectContextPatch`` captures for the three ``FromHand*`` overloads,
#: and equally the stem of a ``"<ID>.selectionScreenPrompt"`` prompt key.
#: Transcribed from the English prompt text; the quoted string is that text.
_SOURCE_DIRECTION: dict[str, str] = {
    # -- the chosen card is destroyed / thrown away -------------------------
    "ASHWATER": DISPOSE,  # "Choose any number of cards to Exhaust."
    "PURITY": DISPOSE,  # "Choose up to {N} cards to Exhaust."
    "GAMBLERS_BREW": DISPOSE,  # "Choose any number of cards to replace."
    "GAMBLING_CHIP": DISPOSE,  # "Choose any number of cards to replace."
    "COMBAT_REWARD_CARD_REMOVAL": DISPOSE,  # "...remove from your deck."
    "GUARDS": DISPOSE,  # "Choose cards to Transform."
    "CLAWS": DISPOSE,  # "Select cards to Transform into Maul."
    # -- the chosen card is duplicated / improved / retrieved ---------------
    "DUAL_WIELD": BOOST,  # "Choose a card to Copy."
    "DOLLYS_MIRROR": BOOST,  # "Select 1 card to Copy"
    "NIGHTMARE": BOOST,  # "Choose a Card." (copies it next turn)
    "HAND_TRICK": BOOST,  # "...add Sly to."
    "SNAP": BOOST,  # "...add Retain to."
    "TRANSFIGURE": BOOST,  # "...add Replay to."
    "TOUCH_OF_INSANITY": BOOST,  # "Choose a card to make free."
    "HEIRLOOM_HAMMER": BOOST,  # "Choose a Colorless Card."
    "SEA_GLASS": BOOST,  # "...add to your Deck."
    # Put on top of the draw pile / back into hand: you want the card you most
    # want to see next, so the best one.
    "COSMIC_INDIFFERENCE": BOOST,
    "GLIMMER": BOOST,
    "HEADBUTT": BOOST,
    "PHOTON_CUT": BOOST,
    "THINKING_AHEAD": BOOST,
    "GRAVEBLAST": BOOST,
    "HOLOGRAM": BOOST,
    "SEEKER_STRIKE": BOOST,
    "WISH": BOOST,
    "SECRET_TECHNIQUE": BOOST,
    "SECRET_WEAPON": BOOST,
    "DROPLET_OF_PRECOGNITION": BOOST,
    "LIQUID_MEMORIES": BOOST,
    "CHOICES_PARADOX": BOOST,
    "DREDGE": BOOST,
    "FOREGONE_CONCLUSION_POWER": BOOST,
    "STRATAGEM_POWER": BOOST,
    "WELL_LAID_PLANS_POWER": BOOST,
    # -- deliberately absent -----------------------------------------------
    # SCULPTING_STRIKE ("add Ethereal") hands the card a DRAWBACK in exchange
    # for something else; DECISIONS_DECISIONS ("Decide a Skill") auto-plays the
    # pick, which is a play decision, not a value ranking. Neither is a clean
    # dispose-or-boost, so both stay UNKNOWN rather than being guessed.
}


def _as_int(value: Any, default: int = 0) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def direction_of(state: dict[str, Any]) -> tuple[str, str]:
    """Classify a ``card_select`` state as DISPOSE / BOOST / UNKNOWN.

    Returns ``(direction, key)`` where ``key`` is whatever named it, for the
    note. ``prompt_key`` is authoritative: it survives
    ``FromHandForDiscard`` delegating to ``FromHand`` (``CardSelectCmd.cs:763``,
    which overwrites the captured ``source`` with the inner method name but
    leaves the prefs untouched), so it is checked before ``source_model``.
    """
    prompt = str(state.get("prompt_key") or "")
    shared = _PROMPT_DIRECTION.get(prompt.upper())
    if shared is not None:
        return shared, prompt.upper()

    # "<ID>.selectionScreenPrompt" -> "<ID>" (CardModel.SelectionScreenPrompt).
    stem = prompt.split(".", 1)[0].upper()
    for key in (stem, str(state.get("source_model") or "").upper()):
        if key and key in _SOURCE_DIRECTION:
            return _SOURCE_DIRECTION[key], key

    return UNKNOWN, prompt or str(state.get("source_model") or "?")


def card_value(card: dict[str, Any]) -> tuple[int, int, int]:
    """Rank a card from worthless to precious, worst first.

    A tuple so the ordering is explicit and stable rather than a single fudged
    number:

    1. **class** -- curses and statuses are dead weight (they are their own
       ``CardRarity``/``CardType`` values, so no id list is needed); basics
       (Strike/Defend) next; everything else above them by rarity.
    2. **upgrade level** -- the whole point of the Strike+ bug. Between two
       otherwise identical cards the unupgraded one is the one to throw away.
    3. **cost** -- last resort, cheapest first, so a 0-cost filler goes before
       a card that was worth paying for.
    """
    rarity = str(card.get("rarity") or "").lower()
    ctype = str(card.get("type") or "").lower()

    if card.get("is_curse") or ctype == "curse" or rarity == "curse":
        klass = 0
    elif card.get("is_status") or ctype == "status" or rarity == "status":
        klass = 1
    else:
        klass = {
            "basic": 2,
            "token": 2,
            "common": 3,
            "event": 3,
            "uncommon": 4,
            "rare": 5,
            "ancient": 6,
        }.get(rarity, 3)

    return klass, _as_int(card.get("upgrade_level")), _as_int(card.get("cost"))


def rank(state: dict[str, Any]) -> dict[str, Any] | None:
    """Rank a ``card_select`` the nets do not cover, or ``None`` if unknown.

    ``None`` means "I still do not know which way this points" -- the caller
    keeps its existing arbitrary-but-honest fallback. Returning a confident
    wrong direction (exhausting the card you meant to copy) is the one outcome
    worse than the leftmost default.
    """
    cards = state.get("cards") or []
    if not cards or not all(isinstance(c, dict) for c in cards):
        return None

    direction, key = direction_of(state)
    if direction == UNKNOWN:
        return None

    scored = [(card_value(c), i) for i, c in enumerate(cards)]
    # Ties resolve to the LEFTMOST card either way, so the pick is reproducible
    # across a resumed run -- hence the sign flip rather than one shared key.
    if direction == DISPOSE:
        best_i = min(scored, key=lambda s: (s[0], s[1]))[1]
    else:
        best_i = max(scored, key=lambda s: (s[0], -s[1]))[1]

    verb = "worst" if direction == DISPOSE else "best"
    labels = [str(c.get("name") or c.get("id") or f"option {i}") for i, c in enumerate(cards)]
    return {
        "note": (
            f"no trained head for this selection ({key}); "
            f"heuristic pick: the {verb} card by rarity, then upgrade, then cost"
        ),
        "options": [
            {
                "label": label,
                "is_best": i == best_i,
                "index": _as_int(cards[i].get("index"), i),
                "action": "choose",
            }
            for i, label in enumerate(labels)
        ],
        "best": {
            "action": "choose",
            "index": _as_int(cards[best_i].get("index"), best_i),
            "label": labels[best_i],
        },
    }
