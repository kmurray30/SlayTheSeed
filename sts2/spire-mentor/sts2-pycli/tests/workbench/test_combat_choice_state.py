"""Regression: an in-combat card_select (Headbutt, Touch of Insanity, …) must
stay enemy-/power-bearing.

The Rust backend renders an in-combat pending choice as a ``card_select``. It
previously dropped ``enemies`` and ``player_powers`` from that state, so the
event-log diff across the choice boundary read every enemy as dying (then
respawning once the choice resolved) and wobbled player powers (phantom
Dexterity −/+). This pins the state shape and the resulting clean diff.
"""

import workbench.backends.rust_game as rust_game
from fake_rm import FakeCombatState, FakeRm
from workbench.statediff import diff_states

_ENEMIES = [
    {"index": 0, "name": "Twig Slime (M)", "hp": 18, "max_hp": 18, "block": 0, "powers": []},
    {"index": 1, "name": "Flyconid", "hp": 47, "max_hp": 50, "block": 0, "powers": []},
]
_POWERS = [{"name": "Dexterity", "amount": 1}]


# Touch of Insanity: pick one card in hand to make free this turn.
_PENDING = {
    "cards": [{"id": "STRIKE_IRONCLAD", "upgraded": False}],
    "min_select": 1,
    "max_select": 1,
    "can_skip": False,
    "kind": "make_free_in_hand",  # snake_case, per choice_kind_python_name
}


class _FakeRm(FakeRm):
    def __init__(self):
        super().__init__(phase="combat")

    def combat_state(self):
        return FakeCombatState(_PENDING)


def _fill_choice_state(monkeypatch):
    monkeypatch.setattr(
        rust_game,
        "build_combat_view",
        lambda cs, character, legal: {
            "player": {"hp": 62, "max_hp": 80, "block": 5},
            "enemies": _ENEMIES,
            "player_powers": _POWERS,
        },
    )
    g = rust_game.RustGame()
    g.rm = _FakeRm()
    g.character = "Ironclad"
    return g.get_state()


def test_in_combat_card_select_keeps_enemies_and_powers(monkeypatch):
    state = _fill_choice_state(monkeypatch)
    assert state["decision"] == "card_select"
    assert state["cards"]  # the pickable card(s)
    # The live combat must survive the choice rendering.
    assert state["enemies"] == _ENEMIES
    assert state["player_powers"] == _POWERS


def test_diff_across_choice_boundary_has_no_phantom_death_or_spawn(monkeypatch):
    combat = {
        "decision": "combat_play",
        "player": {"hp": 62, "max_hp": 80, "block": 5},
        "player_powers": _POWERS,
        "enemies": _ENEMIES,
    }
    choice = _fill_choice_state(monkeypatch)
    # combat -> choice (e.g. use_potion) and choice -> combat (resolve) must
    # both be free of phantom defeated / Spawned / Dexterity lines.
    for prev, nxt in ((combat, choice), (choice, combat)):
        lines = diff_states(prev, nxt)
        joined = " | ".join(lines)
        assert "defeated" not in joined, joined
        assert "Spawned" not in joined, joined
        assert "Dexterity" not in joined, joined
