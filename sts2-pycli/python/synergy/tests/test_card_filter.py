"""Tests for the non-deckbuildable card filter and its wiring."""

from __future__ import annotations

from synergy.archetype_discover import deck_card_matrix
from synergy.card_filter import (
    NON_DECKBUILDABLE,
    filter_deckbuildable,
    is_deckbuildable,
)
from synergy.corpus import Deck
from synergy.observational import pmi_table


def test_curses_statuses_tokens_quest_excluded():
    # curse, status, combat token, and quest artifact all excluded
    for tok in (
        "ASCENDERS_BANE",
        "GREED",
        "WOUND",
        "SLIMED",
        "SHIV",
        "SOUL",
        "LANTERN_KEY",
        "SPOILS_MAP",
        "BYRDONIS_EGG",
    ):
        assert not is_deckbuildable(tok), tok
        assert tok in NON_DECKBUILDABLE


def test_real_cards_kept():
    # basic, common, and event-rarity colorless cards are real choices
    for tok in ("STRIKE_IRONCLAD", "BASH", "INFLAME", "APOTHEOSIS", "OUTMANEUVER", "DEMON_FORM"):
        assert is_deckbuildable(tok), tok
        assert tok not in NON_DECKBUILDABLE


def test_filter_preserves_order_of_real_cards():
    deck = ["BASH", "ASCENDERS_BANE", "STRIKE_IRONCLAD", "WOUND", "INFLAME"]
    assert filter_deckbuildable(deck) == ["BASH", "STRIKE_IRONCLAD", "INFLAME"]


def test_pmi_table_drops_excluded_pairs():
    # Curse/status/token present in *every* deck, so their pairs clear the
    # freq_floor and would surface if unfiltered — they must not become nodes.
    decks = [
        Deck(
            cards=["BASH", "INFLAME", "ASCENDERS_BANE", "GREED", "WOUND", "SHIV"],
            won=(i % 2 == 0),
            player_id=None,
            skill=None,
            ascension=0,
        )
        for i in range(60)
    ]
    table = pmi_table(decks, freq_floor=50)
    nodes = {c for pair in table for c in pair}
    for excluded in ("ASCENDERS_BANE", "GREED", "WOUND", "SHIV"):
        assert excluded not in nodes, excluded
    # the genuine pair still survives
    assert tuple(sorted(("BASH", "INFLAME"))) in table


def test_deck_card_matrix_excludes_and_does_not_crash():
    # A deck carrying an excluded token must not raise and must not add a column.
    decks = [
        Deck(
            cards=["BASH", "INFLAME", "ASCENDERS_BANE", "WOUND"],
            won=True,
            player_id=None,
            skill=None,
            ascension=0,
        ),
        Deck(cards=["BASH", "STRIKE_IRONCLAD"], won=False, player_id=None, skill=None, ascension=0),
    ]
    mat, card_names = deck_card_matrix(decks)
    assert "ASCENDERS_BANE" not in card_names
    assert "WOUND" not in card_names
    assert set(card_names) == {"BASH", "INFLAME", "STRIKE_IRONCLAD"}
    assert mat.shape == (2, 3)
