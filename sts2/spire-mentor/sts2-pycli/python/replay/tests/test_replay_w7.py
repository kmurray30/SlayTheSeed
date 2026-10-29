"""W7 scaffolding tests — bead `sts2-rs-p0jzo`.

Exercises the parser + checkpoint contract end-to-end against a
synthetic floor + a real ``.run`` file from
``../sts2_run_scraper/data/files/the-ironclad/``. Driver scaffold is
sanity-checked (phase-after surface only — full dispatch is W8).
"""

from __future__ import annotations

import json
from collections.abc import Iterable
from dataclasses import dataclass, field
from pathlib import Path

import pytest
from run_files import glob_runs, load_run

from replay.checkpoints import Divergence, DivergenceKind, check_floor
from replay.driver import DispatchResult, apply_floor
from replay.scraped import Floor, parse_run

_CORPUS_ROOT = Path(__file__).resolve().parents[3].parent / "sts2_run_scraper" / "data" / "files"


@dataclass
class _FakeEngineState:
    current_hp: int = 75
    gold: int = 99
    relic_ids: Iterable[str] = field(default_factory=list)
    master_deck_card_ids: Iterable[str] = field(default_factory=list)
    upgraded_card_ids: Iterable[str] = field(default_factory=list)
    deck_delta: dict | None = None


def _floor(**overrides) -> Floor:
    defaults = dict(
        ordinal=1,
        map_point_type="monster",
        hp_after=70,
        gold_after=99,
        relics_picked=[],
        cards_gained=[],
        cards_removed=[],
        upgraded_cards=[],
        event_choices=[],
        raw_player_stats={},
    )
    defaults.update(overrides)
    return Floor(**defaults)


# ── checkpoints ───────────────────────────────────────────────────────


def test_combat_hp_within_tolerance_is_clean():
    floor = _floor(hp_after=70, map_point_type="monster")
    eng = _FakeEngineState(current_hp=72)
    assert check_floor(floor, eng) == []


def test_combat_hp_outside_tolerance_flags_combat_div():
    floor = _floor(hp_after=70, map_point_type="elite")
    eng = _FakeEngineState(current_hp=65)
    divs = check_floor(floor, eng)
    kinds = [d.kind for d in divs]
    assert DivergenceKind.COMBAT in kinds
    assert all(isinstance(d, Divergence) for d in divs)


def test_noncombat_hp_must_be_exact():
    floor = _floor(hp_after=70, map_point_type="event")
    eng = _FakeEngineState(current_hp=71)  # 1-point drift outside event tolerance
    divs = check_floor(floor, eng)
    assert any(d.kind is DivergenceKind.HP for d in divs)


def test_gold_is_always_exact():
    floor = _floor(gold_after=99, map_point_type="monster")
    eng = _FakeEngineState(current_hp=70, gold=100)
    divs = check_floor(floor, eng)
    assert any(d.kind is DivergenceKind.GOLD for d in divs)


def test_relic_superset_check_accumulates():
    floor = _floor(relics_picked=["RELIC.ANCHOR"], map_point_type="treasure", hp_after=75)
    eng = _FakeEngineState(
        current_hp=75,
        # ANCHOR not granted by the engine yet → divergence.
        relic_ids=["RELIC.BURNING_BLOOD"],
    )
    divs = check_floor(floor, eng, accumulated_relics=["RELIC.BURNING_BLOOD"])
    assert any(d.kind is DivergenceKind.RELIC_MISSING for d in divs)


def test_deck_delta_flags_extra_engine_gain():
    floor = _floor(
        map_point_type="monster",
        hp_after=70,
        cards_gained=["CARD.SHRUG_IT_OFF"],
    )
    eng = _FakeEngineState(
        current_hp=70,
        deck_delta={
            "gained": ["CARD.SHRUG_IT_OFF", "CARD.HEAVY_BLADE"],  # extra
            "removed": [],
            "upgraded": [],
        },
    )
    divs = check_floor(floor, eng)
    assert any(d.kind is DivergenceKind.CARDS_GAINED for d in divs)


def test_deck_delta_absent_is_treated_as_noop_floor():
    floor = _floor(
        map_point_type="monster",
        hp_after=70,
        cards_gained=["CARD.SHRUG_IT_OFF"],
    )
    eng = _FakeEngineState(current_hp=70)  # no deck_delta attr → harness skips
    divs = check_floor(floor, eng)
    # No HP/gold/relic divergence, and the absent deck_delta isn't a
    # divergence either — driver hasn't tracked deltas this floor.
    assert all(
        d.kind
        not in {DivergenceKind.CARDS_GAINED, DivergenceKind.CARDS_REMOVED, DivergenceKind.UPGRADED}
        for d in divs
    )
    assert divs == []


# ── scraped parser ─────────────────────────────────────────────────────


def _pick_sample_run() -> Path | None:
    """Return one single-player IC v0.103.2 .run file from the corpus,
    or None when the corpus isn't checked out (CI / fresh-clone).

    Skips multi-player .run files — the W7 parser rejects those by
    design and the smoke test exists to exercise the happy path.
    """
    ic = _CORPUS_ROOT / "the-ironclad"
    if not ic.exists():
        return None
    for month_dir in sorted(ic.iterdir()):
        if not month_dir.is_dir():
            continue
        for f in glob_runs(month_dir, recursive=False):
            try:
                blob = load_run(f)
            except (OSError, json.JSONDecodeError):
                continue
            players = blob.get("players")
            if isinstance(players, list) and len(players) > 1:
                continue
            return f
    return None


@pytest.mark.skipif(_pick_sample_run() is None, reason="scraped corpus not present")
def test_parse_run_smoke_against_real_corpus():
    sample = _pick_sample_run()
    assert sample is not None
    run = parse_run(sample)
    assert run.floors, "expected at least one floor"
    assert run.acts, "expected at least one act"
    assert run.build_id, "expected build_id"
    # First Ironclad floor is ancient (Neow); ordinal is 1-indexed.
    assert run.floors[0].ordinal == 1
    # Floor invariants the harness relies on.
    for fl in run.floors:
        assert isinstance(fl.hp_after, int)
        assert isinstance(fl.gold_after, int)
        assert all(isinstance(r, str) for r in fl.relics_picked)
        assert all(c.startswith("CARD.") for c in fl.cards_gained)


def test_parse_run_rejects_multiplayer(tmp_path: Path):
    bad = tmp_path / "multi.run"
    bad.write_text(
        json.dumps(
            {
                "players": [{}, {}],
                "map_point_history": [[{"player_stats": [{}], "map_point_type": "monster"}]],
            }
        )
    )
    with pytest.raises(ValueError, match="multi-player"):
        parse_run(bad)


def test_parse_run_rejects_empty_history(tmp_path: Path):
    bad = tmp_path / "empty.run"
    bad.write_text(json.dumps({"players": [{}], "map_point_history": []}))
    with pytest.raises(ValueError, match="empty map_point_history"):
        parse_run(bad)


def test_parse_run_reads_gzipped_run(tmp_path: Path):
    """spire-codex runs are stored as ``.run.gz``; parse_run must decompress.

    Reaching the domain ValueError (not a decode/UnicodeError) proves the
    gzip bytes were inflated and JSON-parsed before validation.
    """
    import gzip

    p = tmp_path / "g.run.gz"
    with gzip.open(p, "wt") as fh:
        json.dump({"players": [{}], "map_point_history": []}, fh)
    with pytest.raises(ValueError, match="empty map_point_history"):
        parse_run(p)


# ── driver scaffold ───────────────────────────────────────────────────


class _StubRunManager:
    """Bare minimum surface the W7 driver scaffold pokes."""

    def __init__(self, phase: str) -> None:
        self._phase = phase

    def phase(self) -> str:
        return self._phase


def test_driver_apply_floor_returns_phase_after():
    floor = _floor(map_point_type="event")
    rm = _StubRunManager(phase="map_choice")
    result = apply_floor(rm, floor)
    assert isinstance(result, DispatchResult)
    assert result.phase_after == "map_choice"
    # W7 is a scaffold — no actions dispatched yet.
    assert result.actions == []
    assert result.results == []
