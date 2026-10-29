import random
from typing import Any

import sts2_rs
from simlab.agents import AGENTS, DecisionContext
from simlab.runner import play_combat
from simlab.specs import ScenarioSpec, build_combat

#: A deck that reliably opens a `PendingChoice`: Armaments asks which card in
#: hand to upgrade, so playing it parks the combat on a 4-option choice.
CHOICE_SPEC = ScenarioSpec(
    encounter="MAWLER_NORMAL",
    deck_cards=("Armaments",) * 5 + ("StrikeIronclad",) * 5,
)


def _parked_on_choice(seed: int) -> Any:
    """A combat sitting on Armaments' open choice, or None if it never drew one."""
    combat = build_combat(CHOICE_SPEC, seed)
    hand = sts2_rs.state_to_dict(combat)["deck"]["hand"]
    idx = next((i for i, c in enumerate(hand) if c.get("id") == "ARMAMENTS"), None)
    if idx is None:
        return None
    combat.apply({"action": "play", "card_index": idx, "target_index": None})
    return combat


def test_mcts_agent_registered() -> None:
    assert "mcts" in AGENTS


def test_mcts_agent_returns_legal_action() -> None:
    combat = build_combat(ScenarioSpec(encounter="MAWLER_NORMAL"), seed=2)
    acts = combat.legal_actions()
    ctx = DecisionContext.from_combat(combat, acts, random.Random(0))
    action = AGENTS["mcts"](ctx, sims=50, depth=10)
    assert action in acts


def test_mcts_agent_completes_combat() -> None:
    row = play_combat(
        ScenarioSpec(encounter="MAWLER_NORMAL"),
        strategy="mcts",
        seed=4,
        sims=50,
        depth=10,
    )
    assert row["victory"] in (True, False)
    assert not row["timed_out"]
    assert "error" not in row


def test_mcts_agent_searches_an_open_choice(monkeypatch) -> None:
    """The agent must SEARCH a pending choice, not shortcut to option 0.

    It used to return `choices[0]` outright, so the strongest agent in the lab
    never searched exactly the cards a card-comparison sweep is measuring.
    Spying on the `Mcts` constructor pins the intent (a search happened) rather
    than the pick, which is free to move with the eval.
    """
    combat = _parked_on_choice(0)
    assert combat is not None, "seed 0 must draw Armaments"
    acts = combat.legal_actions()
    assert acts and all(a["action"] == "choose" for a in acts), acts

    built = []
    real_mcts = sts2_rs.Mcts
    monkeypatch.setattr(sts2_rs, "Mcts", lambda *a, **kw: (built.append(1), real_mcts(*a, **kw))[1])
    action = AGENTS["mcts"](DecisionContext.from_combat(combat, acts, random.Random(0)))

    assert built, "no search ran at a pending-choice root"
    assert action in acts


def test_mcts_agent_choice_is_not_always_option_zero() -> None:
    """The payoff of searching: the pick varies with the board.

    The old short-circuit returned index 0 for every seed. Six seeds is a loose
    guard — the eval may reorder the options, but "option 0, always" is what
    the bug looked like.
    """
    picks = []
    for seed in range(6):
        combat = _parked_on_choice(seed)
        if combat is None:
            continue
        acts = combat.legal_actions()
        ctx = DecisionContext.from_combat(combat, acts, random.Random(0))
        picks.append(AGENTS["mcts"](ctx, sims=200, depth=30)["index"])

    assert len(picks) >= 4, f"too few seeds parked on a choice: {picks}"
    assert set(picks) != {0}, "every seed picked option 0 — the choice is not searched"


def test_mcts_agent_deterministic() -> None:
    # Same combat + same params -> same recommended action (state-derived seed).
    def choose() -> dict:
        combat = build_combat(ScenarioSpec(encounter="MAWLER_NORMAL"), seed=7)
        acts = combat.legal_actions()
        ctx = DecisionContext.from_combat(combat, acts, random.Random(0))
        return AGENTS["mcts"](ctx, sims=50, depth=10)

    assert choose() == choose()
