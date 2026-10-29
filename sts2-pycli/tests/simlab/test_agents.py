import random

import pytest
from simlab.agents import AGENTS, DecisionContext
from simlab.agents.heuristics import attack_first, block_first, pick_block_subset
from simlab.intel import probe_action
from simlab.specs import ScenarioSpec, build_combat


def test_registry_has_all_v1_heuristics() -> None:
    assert {"random", "block_first", "attack_first", "min_overblock"} <= set(AGENTS)


def test_pick_block_subset_takes_small_leak_over_bigger_overblock() -> None:
    # The video's example: vs 11 incoming, block 10 and take the 1
    # instead of blocking 13 or 15.
    options = [(0, 5, 1), (1, 5, 1), (2, 5, 1), (3, 8, 2)]
    chosen = pick_block_subset(options, incoming=11, energy=3)
    assert sum(b for _, b, _ in chosen) == 10


def test_pick_block_subset_never_overblocks_when_under_option_exists() -> None:
    # vs 13 incoming with 5-block cards: block 10 and take 3 — never 15,
    # even though 15 is "closer" to 13 than 10 is.
    options = [(0, 5, 1), (1, 5, 1), (2, 5, 1)]
    chosen = pick_block_subset(options, incoming=13, energy=3)
    assert sum(b for _, b, _ in chosen) == 10


def test_pick_block_subset_refuses_huge_overblock() -> None:
    # only a 30-block option vs an 11 hit: wasting 19 block is worse than
    # taking the 11 — play nothing.
    chosen = pick_block_subset([(0, 30, 2)], incoming=11, energy=3)
    assert chosen == []


def test_pick_block_subset_overshoots_when_leak_is_worse() -> None:
    # incoming 11, only a 12-block option: blocking 12 (waste 1) beats
    # blocking 0 (leak 11).
    options = [(0, 12, 2)]
    chosen = pick_block_subset(options, incoming=11, energy=3)
    assert [i for i, _, _ in chosen] == [0]


def test_pick_block_subset_respects_energy() -> None:
    options = [(0, 8, 2), (1, 8, 2), (2, 5, 1)]
    chosen = pick_block_subset(options, incoming=20, energy=3)
    total_energy = sum(e for _, _, e in chosen)
    assert total_energy <= 3


def test_pick_block_subset_prefers_no_block_when_overblock_exceeds_leak() -> None:
    # incoming=1, block=5: take the 1 (deviation 1) rather than overblock
    # by 4 (deviation 4) — "move on with your life".
    chosen = pick_block_subset([(0, 5, 1)], incoming=1, energy=3)
    assert chosen == []


def test_block_first_prefers_block_when_deficit_exists() -> None:
    # seed 3 telegraphs an 8-damage attack on turn 1, so a deficit exists.
    combat = build_combat(ScenarioSpec(encounter="MAWLER_NORMAL"), seed=3)
    acts = combat.legal_actions()
    ctx = DecisionContext.from_combat(combat, acts, random.Random(0))
    eff = probe_action(combat, block_first(ctx), before_state=ctx.state)
    assert eff.block_gain > 0 and eff.enemy_hp_loss == 0


def test_attack_first_prefers_attack_when_available() -> None:
    combat = build_combat(ScenarioSpec(encounter="MAWLER_NORMAL"), seed=3)
    acts = combat.legal_actions()
    ctx = DecisionContext.from_combat(combat, acts, random.Random(0))
    eff = probe_action(combat, attack_first(ctx), before_state=ctx.state)
    assert eff.enemy_hp_loss > 0


@pytest.mark.parametrize("name", ["random", "block_first", "attack_first", "min_overblock"])
def test_agent_completes_combat(name: str) -> None:
    combat = build_combat(ScenarioSpec(encounter="MAWLER_NORMAL"), seed=11)
    agent = AGENTS[name]
    rng = random.Random(0)
    for _ in range(500):
        if combat.terminal_value()[0]:
            break
        acts = combat.legal_actions()
        if not acts:
            break
        ctx = DecisionContext.from_combat(combat, acts, rng)
        action = agent(ctx)
        assert action in acts, f"{name} returned an illegal action: {action}"
        combat.apply(action)
    assert combat.terminal_value()[0], f"{name} did not finish the fight in 500 actions"
