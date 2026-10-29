"""The Queen's Chains of Binding must be visible AND enforced in the workbench.

Reported off a live Queen fight: "Chains of Binding is not working in the
workbench — I don't see any restrictions on the cards I can play." The engine
was right all along (``legal_actions`` drops every further ``Bound`` card once
one has been played, bead ``sts2-rs-ybtmu``), but nothing between it and the
board said so: ``_combat_card_dict`` dropped the wire's per-card ``affliction``,
so a chained card rendered exactly like a free one and the whole power looked
inert. These pin both halves — the marker and the gate — on the workbench's own
``build_combat_view`` path, not on the raw binding.

C# pins: ``ChainsOfBindingPower.cs`` (``AfterCardDrawn`` afflicts up to
``Amount`` drawn cards with ``Bound``; ``ShouldPlay`` refuses a second Bound
card in a turn; ``BeforeSideTurnEnd`` clears every ``Bound``) and
``CardModel.GetDescriptionForPile`` (the purple extra-card-text line).
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import pytest

pytest.importorskip("sts2_rs", reason="the rust backend needs the sts2_rs extension")

import sts2_rs
from workbench import localization, render
from workbench.backends.imported_combat import ImportedCombatBackend
from workbench.backends.rust_game import build_combat_view

DECK = ["STRIKE_SILENT"] * 5 + ["DEFEND_SILENT"] * 5


def _queen_combat() -> Any:
    """A Silent-vs-Queen board one turn in, so PUPPET_STRINGS has landed.

    The Queen's opening move is PUPPET_STRINGS_MOVE, which applies
    ``ChainsOfBindingPower(3)``; the player's next draw is what gets Bound.
    """
    cs = sts2_rs.build_scenario_state(
        seed=7,
        character="Silent",
        deck=DECK,
        hp=70,
        max_hp=70,
        relics=[],
        encounter="QUEEN_BOSS",
        ascension=0,
        potions=[],
    )
    cs.apply({"action": "end_turn"})
    return cs


def _view(cs: Any) -> dict[str, Any]:
    return build_combat_view(cs, "Silent", legal=cs.legal_actions())


def test_the_queen_chains_the_hand_and_the_board_shows_it() -> None:
    """Drawn cards come back Bound, and the view says which ones."""
    view = _view(_queen_combat())
    assert any(p["id"] == "CHAINS_OF_BINDING" for p in view["player_powers"]), (
        "PUPPET_STRINGS_MOVE must leave ChainsOfBinding on the player"
    )
    bound = [c for c in view["hand"] if c.get("affliction") == "Bound"]
    # ChainsOfBinding(3) afflicts up to 3 cards per turn (C# AfterCardDrawn's
    # `num < base.Amount` budget), so a 5-card draw comes back 3 Bound + 2 free.
    assert len(bound) == 3, (
        "the hand must carry the Bound marker the game paints on it; "
        f"got {[(c['id'], c.get('affliction')) for c in view['hand']]}"
    )
    # Every Bound card is still playable — the lockout starts at the FIRST play.
    assert all(c["can_play"] for c in bound)


def test_playing_a_bound_card_locks_out_the_other_bound_cards() -> None:
    """C# ``ChainsOfBindingPower.ShouldPlay``, as the workbench renders it."""
    cs = _queen_combat()
    before = _view(cs)
    first_bound = next(i for i, c in enumerate(before["hand"]) if c.get("affliction") == "Bound")
    cs.apply({"action": "play", "card_index": first_bound, "target_index": None})
    after = _view(cs)

    still_bound = [c for c in after["hand"] if c.get("affliction") == "Bound"]
    free = [c for c in after["hand"] if not c.get("affliction")]
    assert still_bound and free, "need both kinds left in hand for this to mean anything"
    for card in still_bound:
        assert card["can_play"] is False, (
            f"{card['id']} is Bound and a Bound card was already played this turn"
        )
        # And the board must not blame the player's energy for it: the game
        # shows the `unplayable` slash, not a red cost numeral.
        assert card["unplayable_reason"] == "blocked"
    for card in free:
        assert card["can_play"] is True, "an unafflicted card is unaffected by Chains"


def test_the_lockout_lifts_next_turn() -> None:
    """``BeforeSideTurnEnd`` clears every ``Bound``, so the next hand is free."""
    cs = _queen_combat()
    view = _view(cs)
    first_bound = next(i for i, c in enumerate(view["hand"]) if c.get("affliction") == "Bound")
    cs.apply({"action": "play", "card_index": first_bound, "target_index": None})
    assert any(c["can_play"] is False for c in _view(cs)["hand"]), "lockout should be on"

    cs.apply({"action": "end_turn"})
    view = _view(cs)
    assert [c for c in view["hand"] if c.get("affliction") == "Bound"], (
        "Chains is still on the player, so the new draw is Bound again"
    )
    assert all(c["can_play"] for c in view["hand"]), (
        "the per-turn lockout must reset — no card was played this turn yet"
    )


def test_the_gate_holds_through_the_workbench_backend() -> None:
    """The same restriction, driven through a real ``GameBackend``.

    ``build_combat_view`` above is handed the engine's legal set directly; this
    goes through the backend the workbench actually runs, so the plumbing in
    between (``act`` -> re-serialize) is covered too.
    """
    wire = sts2_rs.state_to_dict(_queen_combat())
    be = ImportedCombatBackend(wire, "Silent")
    state = be.start()
    bound_idx = [c["index"] for c in state["hand"] if c.get("affliction") == "Bound"]
    assert len(bound_idx) >= 2, "need two Bound cards to prove the second is refused"

    state = be.act("play_card", card_index=bound_idx[0])
    assert state.get("type") != "error", state
    blocked = [c for c in state["hand"] if c.get("affliction") == "Bound"]
    assert blocked, "the rest of the Bound cards should still be in hand"
    assert all(c["can_play"] is False for c in blocked)
    # And the backend refuses the play outright, not just in the display.
    assert be.act("play_card", card_index=blocked[0]["index"])["type"] == "error"


def test_a_bound_card_prints_bound_on_its_face() -> None:
    """The marker a player actually reads (``CardModel.GetDescriptionForPile``)."""
    loc = localization.load_affliction_localization(
        Path(__file__).resolve().parents[2] / "localization_eng"
    )
    line = localization.affliction_card_line("Bound", loc)
    assert line == "[purple]Bound[/purple]", "the game prints the affliction in purple"
    assert render.resolve_card_text(line, None) == "Bound"
    # An affliction the game shows no card text for must stay silent — the loc
    # dump carries an ENTANGLED entry that `AfflictionModel.HasExtraCardText`
    # never reads.
    assert localization.affliction_card_line("Entangled", loc) is None
    assert localization.affliction_card_line(None, loc) is None
    # Amount-bearing text resolves against the card's own affliction amount.
    galvanized = localization.affliction_card_line("Galvanized", loc)
    assert render.resolve_card_text(galvanized, {"amount": 3}) == "Take 3 damage."


def test_the_served_card_face_carries_the_bound_line(tmp_path) -> None:
    """End to end: an afflicted hand card reaches the client saying "Bound".

    Goes through the app's own enrichment rather than
    :func:`localization.affliction_card_line` alone, because the line has to
    survive the description branch (template vs plain) and the keyword stitch
    that runs around it.
    """
    import json

    from fastapi.testclient import TestClient
    from workbench.server import create_app

    images = tmp_path / "images"
    images.mkdir(parents=True)
    log = tmp_path / "run.jsonl"
    state = {
        "decision": "combat_play",
        "round": 2,
        "player": {"name": "Silent", "hp": 70, "max_hp": 70},
        "enemies": [],
        "hand": [
            {
                "index": 0,
                "id": "DEFEND_SILENT",
                "name": "DEFEND_SILENT.title",
                "description": "DEFEND_SILENT.description",
                "type": "Skill",
                "cost": 1,
                "stats": {"block": 5},
                "can_play": False,
                "unplayable_reason": "blocked",
                "affliction": "Bound",
                "affliction_amount": 1,
            },
            {
                "index": 1,
                "id": "DEFEND_SILENT",
                "name": "DEFEND_SILENT.title",
                "description": "DEFEND_SILENT.description",
                "type": "Skill",
                "cost": 1,
                "stats": {"block": 5},
                "can_play": True,
            },
        ],
    }
    log.write_text(
        json.dumps({"step": 1, "ts": "2026-08-29T00:00:00", "type": "state", "data": state}) + "\n"
    )
    client = TestClient(create_app(images_root=images, logs_dir=tmp_path))
    client.post("/api/load", json={"path": str(log)})
    hand = client.get("/api/step/0").json()["state"]["hand"]

    assert hand[0]["description"].endswith("Bound"), hand[0]["description"]
    assert "[purple]Bound[/purple]" in hand[0]["description_rich"], hand[0]["description_rich"]
    # The card's own text is still there — the affliction line is appended, not
    # a replacement.
    assert hand[0]["description"].split("\n")[0] == hand[1]["description"].split("\n")[0]
    # An unafflicted card gains nothing.
    assert "Bound" not in hand[1]["description"]
