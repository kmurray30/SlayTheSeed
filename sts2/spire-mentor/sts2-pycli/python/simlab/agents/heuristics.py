"""The four rule-based agents from the Caleb Gannon video, plus random.

All share a skeleton: resolve pending choices first, probe playable
cards once per decision, then apply the strategy's ordering rule.
Agents return exactly one action; the runner calls them repeatedly.
"""

from __future__ import annotations

from itertools import combinations

from simlab.agents.base import Action, DecisionContext
from simlab.intel import ActionEffect, incoming_damage, probe_action


def _choices(ctx: DecisionContext) -> list[Action]:
    return [a for a in ctx.legal_actions if a["action"] == "choose"]


def _plays(ctx: DecisionContext) -> list[Action]:
    return [a for a in ctx.legal_actions if a["action"] == "play"]


def _end_turn(ctx: DecisionContext) -> Action:
    action = next((a for a in ctx.legal_actions if a["action"] == "end_turn"), None)
    if action is None:
        raise AssertionError(f"no end_turn in legal actions: {ctx.legal_actions}")
    return action


def _probed(ctx: DecisionContext) -> list[tuple[Action, ActionEffect]]:
    """Probe each play action once, sharing one before-state snapshot."""
    return [(a, probe_action(ctx.combat, a, before_state=ctx.state)) for a in _plays(ctx)]


def random_agent(ctx: DecisionContext) -> Action:
    """Pick any legal action uniformly at random."""
    return ctx.rng.choice(ctx.legal_actions)


def _best_attack(probed: list[tuple[Action, ActionEffect]]) -> Action | None:
    attacks = [(a, e) for a, e in probed if e.enemy_hp_loss > 0]
    if not attacks:
        return None
    # kills first, then raw damage, then cheaper
    attacks.sort(key=lambda ae: (-ae[1].kills, -ae[1].enemy_hp_loss, ae[1].energy_spent))
    return attacks[0][0]


def _blocks(probed: list[tuple[Action, ActionEffect]]) -> list[tuple[Action, ActionEffect]]:
    return [(a, e) for a, e in probed if e.block_gain > 0 and e.enemy_hp_loss == 0]


def block_first(ctx: DecisionContext) -> Action:
    """Defense until incoming is fully covered (overshoot allowed), then attack.

    Greedy biggest-block-first coverage: on mixed block values it overshoots
    where min_overblock picks the exact subset (8+5 vs a clean 5+5 against a
    10 hit); on uniform-block decks the two coincide. A "dump every block
    card, then attack" reading of block-first was tried and rejected — vs
    strength-scaling enemies it drags fights out and lands *above*
    attack_first, inverting the video's ordering.
    """
    if cs := _choices(ctx):
        return cs[0]
    probed = _probed(ctx)
    deficit = incoming_damage(ctx.state) - ctx.state["player"]["block"]
    if deficit > 0:
        blocks = _blocks(probed)
        if blocks:
            # biggest block first — cover ASAP, overshoot is acceptable
            blocks.sort(key=lambda ae: (-ae[1].block_gain, ae[1].energy_spent))
            return blocks[0][0]
    if atk := _best_attack(probed):
        return atk
    return _end_turn(ctx)


def attack_first(ctx: DecisionContext) -> Action:
    """All attacks, then leftover energy on block."""
    if cs := _choices(ctx):
        return cs[0]
    probed = _probed(ctx)
    if atk := _best_attack(probed):
        return atk
    blocks = _blocks(probed)
    if blocks:
        blocks.sort(key=lambda ae: (-ae[1].block_gain, ae[1].energy_spent))
        return blocks[0][0]
    return _end_turn(ctx)


def pick_block_subset(
    options: list[tuple[int, int, int]], incoming: int, energy: int
) -> list[tuple[int, int, int]]:
    """Choose the largest block-card subset that does NOT overblock.

    Never block past the incoming hit: among subsets with total block
    <= incoming, maximize block (smallest leak), tie-broken by energy.
    The video's example holds — vs an 11 attack with 5-block Defends,
    block 10 and take the 1 rather than block 15.

    Fallback when nothing fits under the hit (every playable block card
    alone already overshoots): overblock only if the waste is smaller
    than the leak it prevents — an 8-block Shrug vs a 7 hit is played
    (waste 1 < leak 7), a lone 5-block Defend vs a 1 hit is not (waste
    4 > leak 1, "take the one and move on with your life").

    Args:
        options: List of (key, block_gain, energy_cost) tuples.
        incoming: Damage that needs to be blocked this turn.
        energy: Energy budget the chosen subset must fit within.

    Returns:
        The subset (as a list of the input tuples) with the best score.
        Empty list when playing nothing is best. Exhaustive subset
        enumeration — hands hold <=10 cards so <=2^10 subsets worst case,
        in practice block cards <=6.
    """
    under_best: list[tuple[int, int, int]] = []
    under_score = (0, 0)  # (block, -cost): maximize block, then cheaper
    over_best: list[tuple[int, int, int]] = []
    over_score: tuple[int, int] | None = None  # (waste, cost): minimize both
    for r in range(1, len(options) + 1):
        for combo in combinations(options, r):
            cost = sum(e for _, _, e in combo)
            if cost > energy:
                continue
            block = sum(b for _, b, _ in combo)
            if block <= incoming:
                s = (block, -cost)
                if s > under_score:
                    under_score = s
                    under_best = list(combo)
            else:
                o = (block - incoming, cost)
                if over_score is None or o < over_score:
                    over_score = o
                    over_best = list(combo)
    if under_best:
        return under_best
    # Nothing fits under the hit; overblock only when it beats eating it.
    if over_best and over_score is not None and over_score[0] < incoming:
        return over_best
    return []


def min_overblock(ctx: DecisionContext) -> Action:
    """Block up to (never past) the incoming damage; spend the rest attacking.

    Recomputes the subset plan every decision (stateless): as blocks
    land, `deficit` shrinks and the remaining plan re-derives.
    """
    if cs := _choices(ctx):
        return cs[0]
    probed = _probed(ctx)
    deficit = incoming_damage(ctx.state) - ctx.state["player"]["block"]
    if deficit > 0:
        blocks = _blocks(probed)
        # Clamp energy_spent to >=0: probe deltas can be negative (e.g. a
        # card that refunds energy), and a negative cost would corrupt the
        # knapsack budget arithmetic in pick_block_subset.
        options = [(i, e.block_gain, max(0, e.energy_spent)) for i, (_, e) in enumerate(blocks)]
        chosen = pick_block_subset(options, incoming=deficit, energy=ctx.state["player"]["energy"])
        if chosen:
            return blocks[chosen[0][0]][0]
    if atk := _best_attack(probed):
        return atk
    return _end_turn(ctx)
