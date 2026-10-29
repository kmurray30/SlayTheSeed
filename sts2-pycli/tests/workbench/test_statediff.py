from workbench.statediff import _MINUS, diff_states


def _combat(player_hp, player_block, enemies):
    return {
        "decision": "combat_play",
        "player": {"name": "Ironclad", "hp": player_hp, "max_hp": 80, "block": player_block},
        "enemies": enemies,
    }


def _enemy(index, name, hp, block=0, powers=None):
    e = {"index": index, "name": name, "hp": hp, "max_hp": 50, "block": block}
    if powers is not None:
        e["powers"] = powers
    return e


def test_no_change_is_empty():
    s = _combat(70, 0, [_enemy(0, "Nibbit", 50)])
    assert diff_states(s, s) == []


def test_player_damage_and_heal():
    a = _combat(70, 0, [])
    b = _combat(64, 0, [])
    assert diff_states(a, b) == ["You HP 70→64 (−6)"]
    assert diff_states(b, _combat(80, 0, [])) == ["You HP 64→80 (+16)"]


def test_player_block_change():
    a = _combat(70, 0, [])
    b = _combat(70, 5, [])
    assert diff_states(a, b) == ["You Block 0→5"]


def test_enemy_damage():
    a = _combat(70, 0, [_enemy(0, "Nibbit", 50)])
    b = _combat(70, 0, [_enemy(0, "Nibbit", 44)])
    assert diff_states(a, b) == ["Nibbit HP 50→44 (−6)"]


def test_enemy_death_when_hp_zero():
    a = _combat(70, 0, [_enemy(0, "Looter", 30)])
    b = _combat(70, 0, [_enemy(0, "Looter", 0)])
    assert diff_states(a, b) == ["Looter HP 30→0 (−30)  ☠ defeated"]


def test_enemy_death_when_removed():
    a = _combat(70, 0, [_enemy(0, "Looter", 30)])
    b = _combat(70, 0, [])
    assert diff_states(a, b) == ["Looter HP 30→0  ☠ defeated"]


def test_removed_corpse_already_dead_is_silent():
    a = _combat(70, 0, [_enemy(0, "Looter", 0)])
    b = _combat(70, 0, [])
    assert diff_states(a, b) == []


def test_enemy_spawn():
    a = _combat(70, 0, [_enemy(0, "Nibbit", 50)])
    b = _combat(70, 0, [_enemy(0, "Nibbit", 50), _enemy(1, "Spire Growth", 60)])
    assert diff_states(a, b) == ["Spawned: Spire Growth"]


def _pow(name, amount):
    return {"name": name, "amount": amount}


def test_player_power_gained_with_amount():
    a = {"player": {"hp": 70, "block": 0}, "player_powers": []}
    b = {"player": {"hp": 70, "block": 0}, "player_powers": [_pow("Strength", 2)]}
    assert diff_states(a, b) == ["You +2 Strength"]


def test_power_gained_without_amount():
    a = {"player": {"hp": 70, "block": 0}, "player_powers": []}
    b = {"player": {"hp": 70, "block": 0}, "player_powers": [_pow("Intangible", 0)]}
    assert diff_states(a, b) == ["You +Intangible"]


def test_power_gained_with_negative_amount_renders_signed():
    # A debuff gained at a negative amount must read "You −1 Shrink", not the
    # old "You +-1 Shrink".
    a = {"player": {"hp": 70, "block": 0}, "player_powers": []}
    b = {"player": {"hp": 70, "block": 0}, "player_powers": [_pow("Shrink", -1)]}
    assert diff_states(a, b) == [f"You {_MINUS}1 Shrink"]


def test_enemy_power_amount_changed():
    a = _combat(70, 0, [_enemy(0, "Nibbit", 50, powers=[_pow("Vulnerable", 3)])])
    b = _combat(70, 0, [_enemy(0, "Nibbit", 50, powers=[_pow("Vulnerable", 1)])])
    assert diff_states(a, b) == ["Nibbit Vulnerable 3→1"]


def test_power_lost():
    a = {"player": {"hp": 70, "block": 0}, "player_powers": [_pow("Strength", 2)]}
    b = {"player": {"hp": 70, "block": 0}, "player_powers": []}
    assert diff_states(a, b) == [f"You {_MINUS}Strength"]


def test_gold_change():
    a = {"player": {"hp": 70, "block": 0, "gold": 120}}
    b = {"player": {"hp": 70, "block": 0, "gold": 70}}
    assert diff_states(a, b) == ["Gold 120→70"]


def test_relic_gained():
    a = {"player": {"hp": 70, "block": 0, "relics": []}}
    b = {"player": {"hp": 70, "block": 0, "relics": [{"name": "Burning Blood"}]}}
    assert diff_states(a, b) == ["+Relic Burning Blood"]


def test_potion_gained_and_used():
    none = {"player": {"hp": 70, "block": 0, "potions": []}}
    one = {"player": {"hp": 70, "block": 0, "potions": [{"index": 0, "name": "Fire Potion"}]}}
    assert diff_states(none, one) == ["+Potion Fire Potion"]
    assert diff_states(one, none) == ["Used Fire Potion"]


def _defect_turn(round_no, enemies, orbs):
    return {
        "decision": "combat_play",
        "round": round_no,
        "player": {"name": "Defect", "hp": 75, "max_hp": 75, "block": 0},
        "enemies": enemies,
        "orbs": orbs,
    }


def _orb(index, kind, passive):
    return {"index": index, "type": kind, "name": kind, "passive": passive, "evoke": passive}


def test_lightning_orb_passive_attributed_on_turn_end():
    pre = _defect_turn(1, [_enemy(0, "Shrinker Beetle", 38)], [_orb(0, "Lightning", 3)])
    post = _defect_turn(2, [_enemy(0, "Shrinker Beetle", 35)], [_orb(0, "Lightning", 3)])
    lines = diff_states(pre, post)
    assert "Shrinker Beetle HP 38→35 (−3)" in lines
    assert "Lightning orb passive: 3 damage to a random enemy" in lines


def test_orb_passive_only_fires_on_round_increment():
    # Same round (e.g. a card play) → no orb-passive annotation.
    a = _defect_turn(1, [_enemy(0, "Beetle", 38)], [_orb(0, "Lightning", 3)])
    b = _defect_turn(1, [_enemy(0, "Beetle", 35)], [_orb(0, "Lightning", 3)])
    assert all("orb passive" not in line for line in diff_states(a, b))


def test_orb_passive_per_type_descriptions_and_collapse():
    orbs = [
        _orb(0, "Lightning", 3),
        _orb(1, "Lightning", 3),
        _orb(2, "Frost", 2),
        _orb(3, "Plasma", 1),
        _orb(4, "Dark", 6),
        _orb(5, "Glass", 4),
    ]
    pre = _defect_turn(1, [_enemy(0, "Beetle", 38)], orbs)
    post = _defect_turn(2, [_enemy(0, "Beetle", 38)], orbs)
    lines = diff_states(pre, post)
    assert "Lightning orb passive: 3 damage to a random enemy (×2)" in lines
    assert "Frost orb passive: +2 block" in lines
    assert "Plasma orb passive: +1 energy" in lines
    assert "Dark orb passive: +6 stored damage" in lines
    assert "Glass orb passive: 4 damage to all enemies" in lines


def test_no_orb_passive_for_non_defect_turn():
    pre = {"decision": "combat_play", "round": 1, "player": {"hp": 70}, "enemies": []}
    post = {"decision": "combat_play", "round": 2, "player": {"hp": 70}, "enemies": []}
    assert all("orb passive" not in line for line in diff_states(pre, post))
