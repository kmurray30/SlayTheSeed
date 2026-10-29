"""Tests for synergy.corpus — deck loader and filter.

All tests run against a tiny hand-written fixture directory; the real
corpus (~15.3k decks) is NOT touched by the automated suite.
"""

from __future__ import annotations

from pathlib import Path

import pytest

from synergy.corpus import Deck, compute_skill, deck_size, load_decks, parse_deck

_FIXTURE_ROOT = Path(__file__).resolve().parent / "fixtures" / "codex"

# ---------------------------------------------------------------------------
# Fixture player ids
# ---------------------------------------------------------------------------
_PLAYER_WON = 111111111111111
_PLAYER_LOST = 222222222222222


# ---------------------------------------------------------------------------
# parse_deck
# ---------------------------------------------------------------------------


def test_parse_deck_strips_card_prefix() -> None:
    """parse_deck must strip the ``CARD.`` prefix, yielding engine tokens."""
    run = {
        "players": [
            {
                "deck": [
                    {"id": "CARD.STRIKE_IRONCLAD", "floor_added_to_deck": 1},
                    {"id": "CARD.BASH", "floor_added_to_deck": 1},
                ]
            }
        ]
    }
    cards = parse_deck(run)
    assert cards == ["STRIKE_IRONCLAD", "BASH"]


def test_parse_deck_empty() -> None:
    run = {"players": [{"deck": []}]}
    assert parse_deck(run) == []


# ---------------------------------------------------------------------------
# load_decks — filter correctness
# ---------------------------------------------------------------------------


def test_load_decks_returns_valid_runs_only() -> None:
    """Only the two valid solo v0.107.1 standard non-abandoned non-modded runs."""
    decks = list(load_decks(root=_FIXTURE_ROOT))
    assert len(decks) == 2


def test_load_decks_won_flag() -> None:
    decks = list(load_decks(root=_FIXTURE_ROOT))
    won_flags = {d.won for d in decks}
    assert True in won_flags
    assert False in won_flags


def test_load_decks_cards_are_engine_tokens() -> None:
    """No deck card should start with ``CARD.``."""
    decks = list(load_decks(root=_FIXTURE_ROOT))
    for deck in decks:
        for card in deck.cards:
            assert not card.startswith("CARD."), f"raw id leaked: {card}"


def test_load_decks_correct_cards() -> None:
    """The won run has STRIKE_IRONCLAD, DEFEND_IRONCLAD, BASH."""
    decks = {d.player_id: d for d in load_decks(root=_FIXTURE_ROOT)}
    won_deck = decks[_PLAYER_WON]
    assert set(won_deck.cards) == {"STRIKE_IRONCLAD", "DEFEND_IRONCLAD", "BASH"}
    lost_deck = decks[_PLAYER_LOST]
    assert set(lost_deck.cards) == {"STRIKE_IRONCLAD", "CLEAVE"}


def test_load_decks_excludes_old_build() -> None:
    decks = list(load_decks(root=_FIXTURE_ROOT))
    player_ids = [d.player_id for d in decks]
    assert 333333333333333 not in player_ids  # v0.102.0


def test_load_decks_excludes_abandoned() -> None:
    decks = list(load_decks(root=_FIXTURE_ROOT))
    player_ids = [d.player_id for d in decks]
    assert 444444444444444 not in player_ids


def test_load_decks_excludes_modded() -> None:
    decks = list(load_decks(root=_FIXTURE_ROOT))
    player_ids = [d.player_id for d in decks]
    assert 555555555555555 not in player_ids


def test_load_decks_excludes_coop() -> None:
    decks = list(load_decks(root=_FIXTURE_ROOT))
    player_ids = [d.player_id for d in decks]
    assert 666666666666666 not in player_ids


# ---------------------------------------------------------------------------
# compute_skill — per-player win rate (≥5 runs threshold)
# ---------------------------------------------------------------------------


def _make_runs(player_id: int, wins: int, total: int) -> list[dict]:
    """Build a list of minimal run dicts for a single player."""
    runs = []
    for i in range(total):
        runs.append(
            {
                "build_id": "v0.107.1",
                "game_mode": "standard",
                "win": i < wins,
                "was_abandoned": False,
                "modifiers": [],
                "players": [
                    {
                        "id": player_id,
                        "character": "CHARACTER.IRONCLAD",
                        "deck": [{"id": "CARD.STRIKE_IRONCLAD", "floor_added_to_deck": 1}],
                    }
                ],
            }
        )
    return runs


def test_compute_skill_requires_five_runs(tmp_path: Path) -> None:
    """Players with fewer than 5 runs must be omitted from the skill map."""
    subdir = tmp_path / "ironclad" / "2026-01"
    subdir.mkdir(parents=True)
    import json

    # Player A: 5 runs, 2 wins → included (wr=0.4)
    for i, run in enumerate(_make_runs(player_id=901, wins=2, total=5)):
        (subdir / f"a{i}.run").write_text(json.dumps(run))
    # Player B: 4 runs → excluded (< 5)
    for i, run in enumerate(_make_runs(player_id=902, wins=3, total=4)):
        (subdir / f"b{i}.run").write_text(json.dumps(run))

    skill = compute_skill(tmp_path)
    assert 901 in skill
    assert 902 not in skill
    assert abs(skill[901] - 0.4) < 1e-9


def test_compute_skill_win_rate_100pct(tmp_path: Path) -> None:
    subdir = tmp_path / "ironclad" / "2026-01"
    subdir.mkdir(parents=True)
    import json

    for i, run in enumerate(_make_runs(player_id=903, wins=5, total=5)):
        (subdir / f"c{i}.run").write_text(json.dumps(run))

    skill = compute_skill(tmp_path)
    assert abs(skill[903] - 1.0) < 1e-9


# ---------------------------------------------------------------------------
# load_decks — skill_bucket filtering
# ---------------------------------------------------------------------------


def test_load_decks_skill_bucket_high(tmp_path: Path) -> None:
    """skill_bucket='high' keeps only top-quartile (≥Q3) players."""
    import json

    subdir = tmp_path / "ironclad" / "2026-01"
    subdir.mkdir(parents=True)

    # Build 4 distinct players × 5 runs each so we have 4 skill values.
    # Win rates: 0.0, 0.2, 0.6, 1.0 → Q3 = 0.75 → only player 904 (1.0) qualifies
    configs = [
        (904, 5, 5),  # wr=1.0
        (905, 3, 5),  # wr=0.6
        (906, 1, 5),  # wr=0.2
        (907, 0, 5),  # wr=0.0
    ]
    for pid, wins, total in configs:
        for i, run in enumerate(_make_runs(player_id=pid, wins=wins, total=total)):
            (subdir / f"p{pid}_{i}.run").write_text(json.dumps(run))

    high_decks = list(load_decks(root=tmp_path, skill_bucket="high"))
    high_pids = {d.player_id for d in high_decks}
    # Only the top-quartile player (wr=1.0) should appear
    assert 904 in high_pids
    assert 905 not in high_pids
    assert 906 not in high_pids
    assert 907 not in high_pids


def test_load_decks_skill_bucket_none_returns_all(tmp_path: Path) -> None:
    """skill_bucket=None (default) must not filter by player skill."""
    import json

    subdir = tmp_path / "ironclad" / "2026-01"
    subdir.mkdir(parents=True)
    # Two players, one with 5 runs (skill computed) one with 1 run
    for i, run in enumerate(_make_runs(player_id=908, wins=2, total=5)):
        (subdir / f"d{i}.run").write_text(json.dumps(run))
    for i, run in enumerate(_make_runs(player_id=909, wins=1, total=1)):
        (subdir / f"e{i}.run").write_text(json.dumps(run))

    all_decks = list(load_decks(root=tmp_path, skill_bucket=None))
    pids = {d.player_id for d in all_decks}
    assert 908 in pids
    assert 909 in pids


# ---------------------------------------------------------------------------
# Deck dataclass
# ---------------------------------------------------------------------------


def test_deck_dataclass_fields() -> None:
    d = Deck(cards=["STRIKE_IRONCLAD"], won=True, player_id=1, skill=0.5)
    assert d.cards == ["STRIKE_IRONCLAD"]
    assert d.won is True
    assert d.player_id == 1
    assert d.skill == pytest.approx(0.5)


def test_deck_skill_can_be_none() -> None:
    d = Deck(cards=[], won=False, player_id=None, skill=None)
    assert d.skill is None
    assert d.player_id is None


def test_deck_ascension_defaults_none() -> None:
    d = Deck(cards=[], won=False, player_id=None, skill=None)
    assert d.ascension is None


def test_deck_size_counts_multiplicity() -> None:
    d = Deck(
        cards=["STRIKE_IRONCLAD", "STRIKE_IRONCLAD", "BASH"], won=True, player_id=None, skill=None
    )
    assert deck_size(d) == 3


# ---------------------------------------------------------------------------
# v0.107.1 refresh — build-id default and ascension propagation
# ---------------------------------------------------------------------------


def test_default_build_ids_are_107() -> None:
    from synergy.corpus import _DEFAULT_BUILD_IDS

    assert _DEFAULT_BUILD_IDS == ("v0.107.1",)


def test_load_decks_carries_ascension() -> None:
    """The won fixture run declares ascension 7; the Deck must carry it."""
    decks = {d.player_id: d for d in load_decks(root=_FIXTURE_ROOT)}
    assert decks[_PLAYER_WON].ascension == 7
    # The lost fixture has no ascension field → None.
    assert decks[_PLAYER_LOST].ascension is None
