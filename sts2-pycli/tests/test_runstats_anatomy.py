"""Unit tests for runstats.anatomy on synthetic Run data (no corpus needed)."""

import gzip
import json
import sys
from pathlib import Path

import pytest
from runstats.anatomy import anatomy, fatal_fight, main
from runstats.load import Run


def _mk_run(
    win: bool,
    killed_by: str,
    combat_decisions: list[dict],
    *,
    outcome: str | None = None,
) -> Run:
    decisions = [
        {
            "step": 0,
            "phase": "Event",
            "act": 1,
            "floor": 0,
            "hp": 80,
            "chosen_idx": 0,
            "n_options": 3,
            "offered": {"kind": "event", "event_id": "EVENT.Neow", "choice_idx": 0},
        },
        *combat_decisions,
    ]
    data = {
        "win": win,
        "seed": "42",
        "ascension": 0,
        "killed_by_encounter": killed_by,
        "players": [
            {
                "deck": [{"id": "CARD.STRIKE_IRONCLAD", "upgrades": 0}] * 12,
                "relics": [],
                "potions": [],
            }
        ],
        "map_point_history": [
            [
                {
                    "map_point_type": "Boss",
                    "floor": 16,
                    "monster_ids": ["MONSTER.CRUSHER", "MONSTER.ROCKET"],
                    "event_id": None,
                },
            ]
        ],
        "sts2sim": {
            "meta": {
                "outcome": outcome or ("lost" if not win else "won"),
                "steps": 3,
            },
            "decisions": decisions,
        },
    }
    return Run(data=data, path=Path("42.run.gz"))


def _combat(step: int, floor: int, rnd: int, hp: int, enemies: list[str]) -> dict:
    return {
        "step": step,
        "phase": "Combat",
        "act": 2,
        "floor": floor,
        "hp": hp,
        "chosen_idx": 0,
        "n_options": 2,
        "offered": {
            "kind": "combat",
            "action": "end_turn",
            "round": rnd,
            "board": {
                "enemies": [{"id": e, "is_alive": True} for e in enemies],
                "hand": [],
            },
        },
    }


def test_fatal_fight_extracts_last_combat_entry() -> None:
    run = _mk_run(
        win=False,
        killed_by="MONSTER.CRUSHER",
        combat_decisions=[
            _combat(1, 10, 1, 60, ["Nibbit"]),  # earlier, survived fight
            {  # real streams always have a non-combat decision between fights
                "step": 2,
                "phase": "MapChoice",
                "act": 2,
                "floor": 15,
                "hp": 60,
                "chosen_idx": 0,
                "n_options": 1,
                "offered": {"kind": "map", "choice_idx": 0},
            },
            _combat(3, 16, 1, 64, ["Crusher", "Rocket"]),  # fatal fight, turn 1
            _combat(4, 16, 4, 12, ["Crusher", "Rocket"]),  # fatal fight, turn 4
        ],
    )
    ff = fatal_fight(run)
    assert ff is not None
    assert ff["floor"] == 16
    assert ff["entry_hp"] == 64
    assert ff["turns"] == 4
    assert ff["encounter"] == "MONSTER.CRUSHER"


def test_fatal_fight_none_for_win() -> None:
    run = _mk_run(
        win=True,
        killed_by="NONE.NONE",
        combat_decisions=[_combat(1, 10, 1, 60, ["Nibbit"])],
    )
    assert fatal_fight(run) is None


def test_anatomy_aggregates_killers() -> None:
    loss = _mk_run(
        win=False,
        killed_by="MONSTER.CRUSHER",
        combat_decisions=[_combat(1, 16, 1, 64, ["Crusher", "Rocket"])],
    )
    win = _mk_run(
        win=True,
        killed_by="NONE.NONE",
        combat_decisions=[_combat(1, 10, 1, 60, ["Nibbit"])],
    )
    rep = anatomy([loss, win])
    assert rep["n_runs"] == 2 and rep["n_losses"] == 1
    assert rep["killers"][0]["encounter"] == "MONSTER.CRUSHER"
    assert rep["killers"][0]["deaths"] == 1


def test_fatal_fight_none_for_non_completion() -> None:
    """win=False + outcome != 'lost' (livelock, GH #270) is not a death."""
    run = _mk_run(
        win=False,
        killed_by="MONSTER.CRUSHER",  # stale last-combat lead
        combat_decisions=[_combat(1, 16, 1, 64, ["Crusher"])],
        outcome="empty_actions",
    )
    assert fatal_fight(run) is None


def test_fatal_fight_none_without_combat_decisions() -> None:
    run = _mk_run(win=False, killed_by="NONE.NONE", combat_decisions=[])
    assert fatal_fight(run) is None


def test_anatomy_buckets_non_combat_losses() -> None:
    livelock = _mk_run(
        win=False,
        killed_by="MONSTER.CRUSHER",
        combat_decisions=[_combat(1, 16, 1, 64, ["Crusher"])],
        outcome="empty_actions",
    )
    no_combat = _mk_run(win=False, killed_by="NONE.NONE", combat_decisions=[])
    rep = anatomy([livelock, no_combat])
    buckets = {k["encounter"]: k for k in rep["killers"]}
    assert buckets["<empty_actions>"]["deaths"] == 1
    # A death with no combat block lands in its own pseudo-encounter bucket.
    assert buckets["<no-combat-death>"]["deaths"] == 1
    # Pseudo-encounters were never "faced", so the rate/median stay None.
    assert buckets["<empty_actions>"]["faced"] == 0
    assert buckets["<empty_actions>"]["loss_rate"] is None
    assert buckets["<empty_actions>"]["entry_hp_median"] is None
    assert rep["fatal_by_seed"] == {}
    assert rep["deaths_by_act"] == {}
    assert rep["win_rate"] == pytest.approx(0.0)


def test_anatomy_no_combat_death_label_is_single_bracketed() -> None:
    """Regression: anatomy() once wrapped the already-bracketed
    '<no-combat-death>' sentinel again, yielding '<<no-combat-death>>' —
    which select_fork_seeds' exact-match exclusion filter never matched."""
    no_combat = _mk_run(win=False, killed_by="NONE.NONE", combat_decisions=[])
    rep = anatomy([no_combat])
    assert {k["encounter"] for k in rep["killers"]} == {"<no-combat-death>"}


def test_anatomy_faced_denominator_and_deck_sizes() -> None:
    loss = _mk_run(
        win=False,
        killed_by="MONSTER.CRUSHER",
        combat_decisions=[_combat(1, 16, 1, 64, ["Crusher", "Rocket"])],
    )
    win = _mk_run(win=True, killed_by="NONE.NONE", combat_decisions=[])
    rep = anatomy([loss, win])
    crusher = next(k for k in rep["killers"] if k["encounter"] == "MONSTER.CRUSHER")
    # Both runs' map_point_history list one Crusher room -> faced twice.
    assert crusher["faced"] == 2
    assert crusher["loss_rate"] == pytest.approx(0.5)
    assert crusher["entry_hp_median"] == 64
    assert rep["deck_size_mean_win"] == 12
    assert rep["deck_size_mean_loss"] == 12
    assert rep["deaths_by_act"] == {2: 1}
    assert rep["fatal_by_seed"]["42"]["floor"] == 16


def test_anatomy_empty_corpus_has_no_win_rate() -> None:
    rep = anatomy([])
    assert rep["n_runs"] == 0 and rep["win_rate"] is None
    assert rep["deck_size_mean_win"] is None and rep["deck_size_mean_loss"] is None


def test_main_writes_json_and_prints_killers(tmp_path, monkeypatch, capsys) -> None:
    runs = [
        _mk_run(
            win=False,
            killed_by="MONSTER.CRUSHER",
            combat_decisions=[_combat(1, 16, 1, 64, ["Crusher", "Rocket"])],
        ),
        _mk_run(win=True, killed_by="NONE.NONE", combat_decisions=[]),
    ]
    for i, run in enumerate(runs):
        (tmp_path / f"{i}.run.gz").write_bytes(gzip.compress(json.dumps(run.data).encode()))
    out_json = tmp_path / "anatomy.json"
    monkeypatch.setattr(sys, "argv", ["anatomy", str(tmp_path), "--json", str(out_json)])

    main()

    out = capsys.readouterr().out
    assert "runs=2 losses=1 wr=0.5" in out
    assert "deaths by act: {2: 1}" in out
    assert "deck size win/loss: 12 / 12" in out
    assert "MONSTER.CRUSHER" in out and "loss_rate=0.5" in out

    rep = json.loads(out_json.read_text())
    assert rep["n_runs"] == 2
    assert rep["killers"][0]["encounter"] == "MONSTER.CRUSHER"
