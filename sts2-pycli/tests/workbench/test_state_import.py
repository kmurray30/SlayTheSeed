"""Tests for parsing a state copied out of the live game.

The fixtures under ``tests/fixtures/realgame_adapter/`` are real SimBridge
captures, so these exercise the shapes the mod actually emits rather than a
shape invented here. They predate the 2026-08-02 dialect audit, which is
useful in itself: they carry no ``map`` and no ``rng``, so they pin the
degraded path — the one where the import must SAY what it cannot reproduce.
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest

pytest.importorskip("sts2_rs", reason="the state importer needs the sts2_rs extension")

from workbench.state_import import StateImportError, parse_pasted_state

FIXTURES = Path(__file__).resolve().parents[1] / "fixtures" / "realgame_adapter"


def _wire(name: str = "single_enemy_ironclad") -> dict:
    """One real SimBridge ``combat_action`` message."""
    return json.loads((FIXTURES / f"{name}.json").read_text(encoding="utf-8"))["state"]


def test_parses_a_real_combat_message():
    imported = parse_pasted_state(json.dumps(_wire()))

    assert imported.surface == "combat_action"
    assert imported.has_combat
    assert imported.run.character == "Ironclad"
    assert imported.run.seed == "42"
    assert imported.run.deck, "the master deck rides on run_state"
    # The wire is what `state_from_dict` eats — proving it parses is not the
    # same as proving the engine accepts it.
    import sts2_rs

    cs = sts2_rs.state_from_dict(imported.combat_wire)
    board = sts2_rs.state_to_dict(cs)
    assert len(board["deck"]["hand"]) == len(_wire()["hand"])
    assert board["player"]["hp"] == _wire()["player"]["hp"]


def test_says_what_it_cannot_reproduce():
    """A capture with no RNG lineage and no map must not read as complete."""
    imported = parse_pasted_state(json.dumps(_wire()))

    assert imported.run.rng is None
    warnings = " ".join(imported.warnings)
    assert "RNG cursors" in warnings
    assert "map graph" in warnings
    fidelity = imported.fidelity()
    assert fidelity["rng_restored"] is False
    assert "map + position" not in fidelity["exact"]


def test_accepts_an_advice_request_wrapper():
    """The mod's Copy-state payload wraps the board as an ``advice_request``."""
    msg = {**_wire(), "type": "advice_request", "advice_for": "combat_action"}
    imported = parse_pasted_state(json.dumps(msg))
    assert imported.surface == "combat_action"
    assert imported.source == "advice_request"
    assert imported.has_combat


def test_unwraps_a_log_record():
    record = {"step": 12, "ts": 1787796140.7, "type": "state", "data": _wire()}
    imported = parse_pasted_state(json.dumps(record))
    assert imported.surface == "combat_action"


def test_a_pasted_log_takes_the_newest_position():
    """A whole log is a legitimate paste; the last board is the one on screen."""
    early = _wire()
    late = _wire()
    # `run_state.hp` is the run's HP; `player.hp` is the in-combat mirror. The
    # snapshot reads the former, so that is what has to differ here.
    late["run_state"] = {**late["run_state"], "hp": 41}
    blob = "\n".join(
        json.dumps({"step": i, "ts": 0.0, "type": "state", "data": w})
        for i, w in enumerate([early, late])
    )
    imported = parse_pasted_state(blob)
    assert imported.run.hp == 41


def test_a_clipped_line_does_not_sink_the_paste():
    """Half a line at one end is ordinary; the rest must still import."""
    blob = '{"step": 0, "ts": 0.0, "typ\n' + json.dumps(_wire())
    assert parse_pasted_state(blob).surface == "combat_action"


def test_run_level_paste_carries_no_fight():
    """A screen message rebuilds the run, not a board."""
    msg = {
        "type": "advice_request",
        "advice_for": "shop",
        "seed": "42",
        "act": 1,
        "floor": 5,
        "run_state": _wire()["run_state"],
        "options": [],
    }
    imported = parse_pasted_state(json.dumps(msg))
    assert imported.surface == "shop"
    assert not imported.has_combat
    assert imported.combat_wire is None
    assert imported.run.seed == "42"


def test_rejects_an_engine_wire():
    """It looks importable and is not — it carries no run to rebuild."""
    import sts2_rs

    cs = sts2_rs.build_scenario_state(
        seed=1,
        character="Ironclad",
        deck=["STRIKE_IRONCLAD"] * 5 + ["DEFEND_IRONCLAD"] * 4 + ["BASH"],
        hp=80,
        max_hp=80,
        relics=[],
        encounter="MAWLER_NORMAL",
        ascension=0,
        potions=[],
    )
    with pytest.raises(StateImportError, match="engine wire dict"):
        parse_pasted_state(json.dumps(sts2_rs.state_to_dict(cs)))


def test_rejects_a_headless_decision():
    from realgame_adapter import to_headless_combat_play

    headless = to_headless_combat_play(_wire())
    with pytest.raises(StateImportError, match="headless"):
        parse_pasted_state(json.dumps(headless))


def test_rejects_junk_with_something_actionable():
    with pytest.raises(StateImportError, match="does not look like JSON"):
        parse_pasted_state("hello there")
    with pytest.raises(StateImportError, match="empty"):
        parse_pasted_state("   ")
    with pytest.raises(StateImportError, match="No game state"):
        parse_pasted_state(json.dumps({"type": "pong"}))


def test_a_message_without_a_run_is_not_a_position():
    msg = {**_wire(), "run_state": {**_wire()["run_state"], "character": None}}
    with pytest.raises(StateImportError, match="names no character"):
        parse_pasted_state(json.dumps(msg))


def test_relics_and_potions_come_back_as_registry_ids():
    wire = _wire()
    wire["run_state"] = {
        **wire["run_state"],
        "relics": [{"id": "BURNING_BLOOD", "name": "Burning Blood"}, {"id": "NOT_A_RELIC"}],
        "potions": [{"slot": 0, "empty": True}, {"slot": 1, "id": "FIRE_POTION"}],
    }
    imported = parse_pasted_state(json.dumps(wire))
    assert imported.run.relics == ("BURNING_BLOOD",)
    assert imported.run.potions == ("FIRE_POTION",)
    # The unknown relic is dropped, but never silently.
    assert any("NOT_A_RELIC" in w for w in imported.warnings)
