"""Corpus loader for the card-synergy catalog pipeline.

Reads human run ``.run`` JSON files from the spire-codex scraper
output and yields typed :class:`Deck` instances after applying the
global filter (version, character, game-mode, abandonment, mods).

Usage::

    from synergy.corpus import load_decks

    for deck in load_decks():          # real corpus
        print(deck.cards, deck.won)

    for deck in load_decks(root=my_fixture_root, skill_bucket="high"):
        ...  # top-quartile players only
"""

from __future__ import annotations

import json
import os
from collections import defaultdict
from collections.abc import Iterator
from dataclasses import dataclass
from pathlib import Path
from statistics import quantiles

from run_files import glob_runs, load_run

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

# Default: the `sts2_run_scraper` checkout next to the monorepo (not vendored).
CODEX_ROOT = Path(
    os.environ.get(
        "SYNERGY_CODEX_ROOT",
        str(
            Path(__file__).resolve().parents[3] / "sts2_run_scraper" / "data" / "files-spire-codex"
        ),
    )
)

_DEFAULT_BUILD_IDS = ("v0.107.1",)
_DEFAULT_CHARACTER = "ironclad"


# ---------------------------------------------------------------------------
# Public dataclass
# ---------------------------------------------------------------------------


@dataclass
class Deck:
    """One run's terminal deck plus metadata.

    Attributes:
        cards: Engine-token card ids (``CARD.`` prefix stripped).
        won: Whether the run was a win.
        player_id: Steam64 player id, or ``None`` if absent in the source.
        skill: Caller-attached win-rate estimate for this player, or ``None``.
        ascension: Ascension level of the run, or ``None`` if absent in the
            source.  Used as a control in the ``interaction_lr`` / FM estimators.
    """

    cards: list[str]
    won: bool
    player_id: int | None
    skill: float | None
    ascension: int | None = None


# ---------------------------------------------------------------------------
# Core helpers
# ---------------------------------------------------------------------------


def iter_runs(
    root: Path,
    build_ids: tuple[str, ...] = _DEFAULT_BUILD_IDS,
    character: str = _DEFAULT_CHARACTER,
) -> Iterator[dict]:
    """Yield parsed run dicts that pass the global-constraints filter.

    Traverses ``<root>/<character>/<YYYY-MM>/*.run`` and yields one
    :class:`dict` per JSON file that satisfies all of:

    - ``build_id`` is in *build_ids*
    - ``game_mode == "standard"``
    - ``not was_abandoned``
    - ``not modifiers``
    - ``len(players) == 1``
    - ``players[0]["character"] == "CHARACTER.<CHARACTER>"``

    Args:
        root: Corpus root directory (parent of the character sub-dirs).
        build_ids: Accepted ``build_id`` values.
        character: Character sub-directory name (lowercase, e.g. ``"ironclad"``).

    Yields:
        Parsed run dicts passing the filter.
    """
    char_dir = root / character
    if not char_dir.is_dir():
        return

    expected_char_key = f"CHARACTER.{character.upper()}"

    for month_dir in sorted(char_dir.iterdir()):
        if not month_dir.is_dir():
            continue
        for run_path in glob_runs(month_dir, recursive=False):
            try:
                raw = load_run(run_path)
            except (json.JSONDecodeError, OSError):
                continue

            if raw.get("build_id") not in build_ids:
                continue
            if raw.get("game_mode") != "standard":
                continue
            if raw.get("was_abandoned"):
                continue
            if raw.get("modifiers"):
                continue

            players = raw.get("players")
            if not isinstance(players, list) or len(players) != 1:
                continue
            if players[0].get("character") != expected_char_key:
                continue

            yield raw


def parse_deck(run: dict) -> list[str]:
    """Extract engine-token card ids from a run dict.

    Strips the ``CARD.`` prefix from each card entry's ``id`` field so
    the returned ids match the engine's card token format.

    Args:
        run: A parsed run dict (as yielded by :func:`iter_runs`).

    Returns:
        List of engine card tokens, e.g. ``["STRIKE_IRONCLAD", "BASH"]``.
    """
    deck = run["players"][0].get("deck") or []
    return [card["id"].partition(".")[2] for card in deck]


def compute_skill(
    root: Path,
    build_ids: tuple[str, ...] = _DEFAULT_BUILD_IDS,
    character: str = _DEFAULT_CHARACTER,
) -> dict[int, float]:
    """Compute per-player win rates for players with ≥5 runs.

    Players with fewer than 5 qualifying runs are excluded; they lack
    enough data for a reliable estimate.

    Args:
        root: Corpus root directory.
        build_ids: Accepted ``build_id`` values (forwarded to :func:`iter_runs`).
        character: Character sub-directory (forwarded to :func:`iter_runs`).

    Returns:
        Mapping of ``player_id → win_rate`` for players with ≥5 runs.
    """
    wins_by_player: dict[int, int] = defaultdict(int)
    runs_by_player: dict[int, int] = defaultdict(int)

    for run in iter_runs(root, build_ids=build_ids, character=character):
        pid_raw = run["players"][0].get("id")
        if pid_raw is None:
            continue
        pid = int(pid_raw)
        runs_by_player[pid] += 1
        if run.get("win"):
            wins_by_player[pid] += 1

    return {pid: wins_by_player[pid] / count for pid, count in runs_by_player.items() if count >= 5}


def load_decks(
    root: Path = CODEX_ROOT,
    skill_bucket: str | None = None,
    build_ids: tuple[str, ...] = _DEFAULT_BUILD_IDS,
    character: str = _DEFAULT_CHARACTER,
) -> Iterator[Deck]:
    """Load filtered decks from the corpus, optionally restricted by player skill.

    Two-pass algorithm when *skill_bucket* is set:

    1. ``compute_skill`` builds the per-player win-rate map (first pass).
    2. A second ``iter_runs`` pass yields :class:`Deck` objects whose
       player is in the top quartile (Q3 threshold, i.e. ≥ the 75th
       percentile win rate).

    When *skill_bucket* is ``None`` (default) only a single pass is made
    and every qualifying run is yielded regardless of player history.

    Args:
        root: Corpus root directory.  Defaults to :data:`CODEX_ROOT` (overridable
            via ``SYNERGY_CODEX_ROOT`` env var or call-time *root* argument).
        skill_bucket: ``"high"`` to keep only top-quartile players; ``None``
            to disable skill filtering.
        build_ids: Accepted ``build_id`` values.
        character: Character sub-directory name.

    Yields:
        :class:`Deck` instances for each qualifying run.
    """
    skill_map: dict[int, float] = {}
    skill_threshold: float | None = None

    if skill_bucket == "high":
        skill_map = compute_skill(root, build_ids=build_ids, character=character)
        if skill_map:
            win_rates = sorted(skill_map.values())
            # quantiles() needs at least 2 data points; for tiny sets treat
            # the max as the Q3.
            if len(win_rates) >= 4:
                q3 = quantiles(win_rates, n=4)[2]  # index 2 = 75th percentile
            else:
                q3 = win_rates[-1]
            skill_threshold = q3

    for run in iter_runs(root, build_ids=build_ids, character=character):
        pid_raw = run["players"][0].get("id")
        pid = int(pid_raw) if pid_raw is not None else None

        if skill_threshold is not None:
            # Exclude players not in the skill map or below the threshold
            if pid is None or pid not in skill_map:
                continue
            if skill_map[pid] < skill_threshold:
                continue

        player_skill = skill_map.get(pid) if pid is not None else None

        yield Deck(
            cards=parse_deck(run),
            won=bool(run.get("win")),
            player_id=pid,
            skill=player_skill,
            ascension=run.get("ascension"),
        )


def deck_size(deck: Deck) -> int:
    """Number of card slots in the deck (multiplicity counted)."""
    return len(deck.cards)
