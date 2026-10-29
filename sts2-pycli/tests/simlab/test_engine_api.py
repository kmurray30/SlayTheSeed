"""Pin the sts2_rs API surface SimLab depends on.

If these fail after an sts2-rs update, SimLab's assumptions changed —
fix simlab, not the tests, unless the engine change was unintentional.
"""

import sts2_rs

STARTER_DECK = ["STRIKE_IRONCLAD"] * 5 + ["DEFEND_IRONCLAD"] * 4 + ["BASH"]


def _build(seed: int = 1) -> "sts2_rs.CombatState":
    return sts2_rs.build_scenario_state(
        seed=seed,
        character="Ironclad",
        deck=STARTER_DECK,
        hp=80,
        max_hp=80,
        relics=[],
        encounter="MAWLER_NORMAL",
        ascension=0,
    )


def test_build_and_state_shape() -> None:
    c = _build()
    d = sts2_rs.state_to_dict(c)
    assert d["player"]["hp"] == 80
    assert {"hand", "draw_pile", "discard_pile", "exhaust_pile"} <= set(d["deck"])
    assert len(d["encounter"]) >= 1
    enemy = d["encounter"][0]
    assert {"hp", "max_hp", "block", "intents", "is_dead"} <= set(enemy)


def test_legal_actions_shape() -> None:
    c = _build()
    acts = c.legal_actions()
    kinds = {a["action"] for a in acts}
    assert "end_turn" in kinds
    assert "play" in kinds  # starter hand always has a playable card
    play = next(a for a in acts if a["action"] == "play")
    assert "card_index" in play and "target_index" in play


def test_apply_and_terminal() -> None:
    c = _build()
    term, _val = c.terminal_value()
    assert term is False
    h_before = c.state_hash()
    c.apply({"action": "end_turn"})
    # apply mutated the state in place
    assert c.state_hash() != h_before
    # one enemy turn later the fight is still on
    assert c.terminal_value()[0] is False


def test_clone_is_independent() -> None:
    c = _build()
    energy_before = sts2_rs.state_to_dict(c)["player"]["energy"]
    assert energy_before == 3
    snap = c.clone()
    c.apply({"action": "end_turn"})
    # mutating the original must not touch the snapshot
    assert sts2_rs.state_to_dict(snap)["player"]["energy"] == energy_before
    assert c.state_hash() != snap.state_hash()


def test_same_seed_same_state_different_seed_differs() -> None:
    assert _build(7).state_hash() == _build(7).state_hash()
    hashes = {_build(s).state_hash() for s in range(5)}
    assert len(hashes) == 5  # each seed yields a distinct combat
