"""When does the MCTS rollout policy actually prefer to block? GH #293 suspect 2.

Companion to `diag_leaf_saturation.py`. That probe showed the wide Texel *leaf*
cannot express the value of blocking (median `tanh'` ~0.07). This one asks what
the *rollout* does, since with a flat leaf the node values are dominated by it.

`Heuristic::pick` (`sts2-mcts/src/expert.rs:1993`) is:

  1. `obvious_kill_action` — an offense-first short circuit, taken before
     anything else.
  2. `pick_action_heuristic` — softmax over `-priority / SOFTMAX_TAU` (tau=32),
     so a LOWER priority is played sooner.

In `play_card_priority` (`:1617`), Defend-class cards get a bonus in exactly two
situations, and they are cliffs rather than gradients:

  * `is_lethal_turn`       — `incoming > 0 && current_hp + block <= incoming`
  * `is_sustained_pressure`— `incoming > 0 && effective_hp <= 2 * incoming`,
                             and the player cannot kill the weakest enemy

When either fires, Defend jumps to `LETHAL_DEFEND_PRIORITY = -5` and basic
Strikes drop to `LETHAL_STRIKE_PRIORITY = 90`. When neither fires, Defend takes
its base `play_ordering` priority — which for every character's basic Defend is
**55, exactly equal to that character's basic Strike** (`expert.rs:791-801`).
Meanwhile the only other priority bonuses in that function (focus-fire,
debuff-setup) apply to ATTACK-class cards.

So the rollout has two modes: indifferent, or panic. It never blocks for HP
efficiency — only to avoid dying. This probe measures how often each regime
holds on real states.

Usage:
  python diag_rollout_defense.py [--corpus logs/diag/mirror_a1.jsonl] [--limit 4000]
"""

from __future__ import annotations

import argparse
import math
from pathlib import Path

import sts2_rs
from diag_leaf_saturation import DEGENERATE_ABS, states_from_corpus

#: `expert.rs:60` / `:67` / `:73` / `:791-801`.
SOFTMAX_TAU = 32.0
LETHAL_DEFEND_PRIORITY = -5
LETHAL_STRIKE_PRIORITY = 90
BASIC_CARD_PRIORITY = 55
#: `expert.rs:110`.
SUSTAINED_PRESSURE_HORIZON = 2


def softmax_ratio(p_defend: int, p_strike: int) -> float:
    """Relative sampling weight of Defend vs Strike under `exp(-p/tau)`."""
    return math.exp(-(p_defend - p_strike) / SOFTMAX_TAU)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--corpus", default="logs/diag/mirror_a1.jsonl")
    ap.add_argument("--limit", type=int, default=4000)
    args = ap.parse_args(argv)

    names = sts2_rs.CombatState.feature_names_wide()
    i_hp = names.index("p_hp_ratio")
    i_blk = names.index("p_block_norm")
    i_max = names.index("p_max_hp_norm")
    slots = [(names.index(f"e{s}_alive"), names.index(f"e{s}_intent_dmg_norm")) for s in range(4)]

    path = Path(args.corpus)
    if not path.is_absolute():
        path = Path(__file__).resolve().parents[1] / path

    n = attacked = lethal = sustained = indifferent = 0
    for _label, st in states_from_corpus(path, args.limit):
        f = st.feature_vector_wide()
        if any(abs(x) > DEGENERATE_ABS for x in f):
            continue
        n += 1
        max_hp = f[i_max] * 100.0
        eff_hp = f[i_hp] * max_hp + f[i_blk] * max_hp
        # `dmg_norm` is (damage * hits) / 20, summed across each slot's intents.
        incoming = sum(f[d] * 20.0 for a, d in slots if f[a] > 0.5)
        if incoming <= 0:
            continue
        attacked += 1
        if eff_hp <= incoming:
            lethal += 1
        elif eff_hp <= incoming * SUSTAINED_PRESSURE_HORIZON:
            sustained += 1
        else:
            indifferent += 1

    if not attacked:
        print(f"no attacked states in {path}")
        return 1

    print(f"corpus : {path}")
    print(f"states : {n}   facing a telegraphed attack: {attacked} ({100 * attacked / n:.1f}%)")
    print()
    print("of the states facing a telegraphed attack:")
    print(f"  lethal-turn arm fires        {lethal:>5}  {100 * lethal / attacked:5.1f}%")
    print(f"  sustained-pressure arm may   {sustained:>5}  {100 * sustained / attacked:5.1f}%")
    print(f"  NEITHER - Defend == Strike   {indifferent:>5}  {100 * indifferent / attacked:5.1f}%")
    print()
    print("rollout sampling weight, Defend vs basic Strike:")
    print(
        f"  emergency arm : priority {LETHAL_DEFEND_PRIORITY} vs {LETHAL_STRIKE_PRIORITY}"
        f"  ->  {softmax_ratio(LETHAL_DEFEND_PRIORITY, LETHAL_STRIKE_PRIORITY):.1f}x toward Defend"
    )
    print(
        f"  otherwise     : priority {BASIC_CARD_PRIORITY} vs {BASIC_CARD_PRIORITY}"
        f"  ->  {softmax_ratio(BASIC_CARD_PRIORITY, BASIC_CARD_PRIORITY):.1f}x  (exactly indifferent)"
    )
    print()
    print(
        "NOTE: the two arms are UPPER bounds. `is_lethal_turn` is suppressed when the\n"
        "player can kill every alive enemy this turn, and `is_sustained_pressure` also\n"
        "requires that the player cannot kill the weakest enemy — neither is computable\n"
        "from the feature vector. So the true indifferent share is >= the number above."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
