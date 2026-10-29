"""Tests for simlab.intel: incoming_damage and probe_action."""

import sts2_rs
from simlab.intel import incoming_damage, probe_action
from simlab.specs import ScenarioSpec, build_combat


def _enemy(kind: str = "Mawler", hp: int = 30, intents: list | None = None) -> dict:
    return {
        "kind": kind,
        "hp": hp,
        "max_hp": hp,
        "block": 0,
        "intents": intents or [],
        "is_dead": hp <= 0,
        "removed_after_death": False,
    }


def test_incoming_damage_single_attack() -> None:
    state = {"encounter": [_enemy(intents=[{"kind": "Attack", "amount": 11}])]}
    assert incoming_damage(state) == 11


def test_incoming_damage_multi_attack_and_dead_enemy() -> None:
    state = {
        "encounter": [
            _enemy(intents=[{"kind": "Attack", "amount": 4, "times": 3}]),
            _enemy(hp=0, intents=[{"kind": "Attack", "amount": 99}]),
            _enemy(intents=[{"kind": "Buff", "amount": 2}]),
        ]
    }
    assert incoming_damage(state) == 12


def test_probe_block_card() -> None:
    # seed=3: hand=['STRIKE_IRONCLAD', 'DEFEND_IRONCLAD', 'DEFEND_IRONCLAD', 'STRIKE_IRONCLAD', 'STRIKE_IRONCLAD']
    combat = build_combat(ScenarioSpec(encounter="MAWLER_NORMAL"), seed=3)
    d = sts2_rs.state_to_dict(combat)
    hand = d["deck"]["hand"]
    defends = [i for i, c in enumerate(hand) if c["id"] == "DEFEND_IRONCLAD"]
    strikes = [i for i, c in enumerate(hand) if c["id"] == "STRIKE_IRONCLAD"]
    assert defends and strikes
    eff = probe_action(combat, {"action": "play", "card_index": defends[0], "target_index": None})
    assert eff.block_gain == 5  # DEFEND_IRONCLAD: 5 base block (no upgrades/relics)
    assert eff.enemy_hp_loss == 0
    assert eff.energy_spent == 1
    atk = next(
        a for a in combat.legal_actions() if a["action"] == "play" and a["card_index"] == strikes[0]
    )
    eff = probe_action(combat, atk)
    assert eff.enemy_hp_loss >= 6  # Strike: 6 base, more if modifiers
    assert eff.block_gain == 0
    # probing must not mutate the original
    assert sts2_rs.state_to_dict(combat)["player"]["energy"] == 3


def test_probe_end_turn_signed_deltas() -> None:
    combat = build_combat(ScenarioSpec(encounter="MAWLER_NORMAL"), seed=3)
    eff = probe_action(combat, {"action": "end_turn"})
    assert eff.kills == 0
    assert eff.energy_spent <= 0  # energy refills by next player turn
