"""Engine-MCTS agent: the strongest player in the sts2_rs wheel.

Per decision: build the engine's full `sts2_rs.Mcts` over the live combat,
run `sims` simulations, and play the recommended action. The engine's MCTS
uses the Texel hand-crafted-evaluation (HCE) leaf eval and the `RichBoth`
terminal-eval mode (`MctsConfig::default()` selects both), whose win branch
scales with remaining HP/potions (see sts2-core `evaluate_victory`) so the
search prefers wins that preserve resources, not just any win.

Pending-choice nodes (Armaments / Warcry / Dual Wield / Burning Pact / any
Discover) are searched like every other decision. They used to be answered
with `choices[0]` — "not searched; first option taken, mirroring the
heuristics" — which was true when written and stopped being true once
`legal_actions` learned to fan an open `PendingChoice` out into one
`ResolveChoice` per option (sts2-core, bead `sts2-rs-419n`; multi-pick
decomposes sequentially per bead `sts2-rs-sopgf`). The short-circuit then
left the lab's *strongest* agent taking option 0 on exactly the cards a
card-comparison sweep is most likely to be measuring, so every such sweep
scored the choice card as if nobody chose. `combat.legal_actions()` and
`Mcts.search()` share one action encoder in `sts2-py`, so the recommendation
compares equal to a legal action here as it does anywhere else.
"""

from __future__ import annotations

import sts2_rs

from simlab.agents.base import Action, DecisionContext


def mcts_agent(ctx: DecisionContext, sims: int = 400, depth: int = 200) -> Action:
    """Pick an action via the engine's full MCTS (Texel + RichBoth eval).

    Args:
        ctx: The decision context (live combat + legal actions).
        sims: MCTS simulation budget per decision (engine `iters`).
        depth: Max rollout depth (engine `max_rollout_depth`).

    Returns:
        The chosen action object taken from `ctx.legal_actions`.

    Raises:
        AssertionError: If the MCTS recommendation does not equal any legal
            action (no silent fallback — the mismatch is surfaced).
    """
    if len(ctx.legal_actions) == 1:
        return ctx.legal_actions[0]

    # Derive the search seed from the state alone so the recommendation is
    # replayable with no hidden coupling to the per-combat rng stream.
    seed = ctx.combat.state_hash() & 0xFFFFFFFF

    # `Mcts(state, ...)` clones `state` internally (it builds the engine over
    # a clone of the inner state), so ctx.combat is not mutated by search().
    mcts = sts2_rs.Mcts(ctx.combat, iters=sims, seed=seed, max_rollout_depth=depth)
    recommended = mcts.search()["action"]

    for action in ctx.legal_actions:
        if action == recommended:
            return action
    raise AssertionError(
        "mcts_agent recommendation matched no legal action: "
        f"recommended={recommended!r}, legal_actions={ctx.legal_actions!r}"
    )
