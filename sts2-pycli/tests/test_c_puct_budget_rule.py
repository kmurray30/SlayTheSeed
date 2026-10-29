"""`belief_draw_c_puct` — the budget-keyed exploration constant.

Deliberately in its own module: `test_hint_recommender.py` is
`importorskip`-gated on the compiled `sts2_rs` wheel, and this rule is pure
Python. Gating it behind the wheel would mean the rule that guards deployed
search quality is silently unexercised in exactly the environments that do not
build the extension.
"""

from __future__ import annotations

# --- budget-keyed exploration constant ---------------------------------------
# `belief_draw_c_puct` mirrors `sts2_runai::policy::belief_draw_c_puct`. These
# pin the two step boundaries and, above all, that every DEPLOYED budget still
# resolves to the F2 constant: this rule is a guard on the low-sims corner, not
# a retune of production.


def test_belief_draw_c_puct_leaves_every_deployed_budget_alone():
    from sts2_hint.recommender import belief_draw_c_puct

    # eval_cheap 1000/4000, eval_strong 5000/15000, advisor default 8000.
    for sims in (1000, 4000, 5000, 8000, 15000):
        assert belief_draw_c_puct(sims) == 2.5, f"sims={sims} must keep 2.5"


def test_belief_draw_c_puct_steps_at_the_measured_boundaries():
    from sts2_hint.recommender import belief_draw_c_puct

    assert belief_draw_c_puct(200) == 0.8
    assert belief_draw_c_puct(499) == 0.8
    assert belief_draw_c_puct(500) == 1.41
    assert belief_draw_c_puct(999) == 1.41
    # 1000 is the main deployed combat lane. The sweep's best arm there is 1.41
    # but only by 0.8pp at p=0.33, so the rule must NOT take it.
    assert belief_draw_c_puct(1000) == 2.5


def test_belief_draw_c_puct_is_monotone_in_the_budget():
    from sts2_hint.recommender import belief_draw_c_puct

    prev = 0.0
    for sims in (1, 200, 300, 499, 500, 999, 1000, 2000, 4000, 8000, 50_000):
        c = belief_draw_c_puct(sims)
        assert c >= prev, f"c_puct fell as the budget rose at sims={sims}"
        prev = c


def test_belief_draw_c_puct_keys_on_the_per_tree_budget():
    from sts2_hint.recommender import belief_draw_c_puct

    # Root-parallel SPLITS the budget across workers and c_puct governs one
    # tree, so 1000 sims over 8 threads is a 125-sim tree, not a 1000-sim one.
    assert belief_draw_c_puct(1000, threads=8) == 0.8
    # The advisor's own defaults still land on the deployed constant.
    assert belief_draw_c_puct(8000, threads=8) == 2.5
