"""Pure unit gates for the WPA coaching review (ppo.wpa) — no engine, no torch.

Covers the three layers of issue #201: curve scoring + delta attribution
(step 1), the common-random-numbers pairing seams of the counterfactual
machinery (step 2), and the HCA overdetermined/suppression rule (step 3).
The net and the rollout function are injected, so nothing here touches
sts2_rs or a checkpoint.
"""

import os
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "python"))

from ppo.wpa import (
    REPLAYABLE_POLICIES,
    action_label,
    build_report,
    cf_sample_seed,
    render_text,
    run_counterfactuals,
    score_decisions,
    summarize_counterfactual,
    turning_points,
)


class FakeNet:
    """Scripted (probs, value) per call, in decision order."""

    def __init__(self, outputs):
        self.outputs = list(outputs)
        self.calls = []

    def distribution(self, obs, actions, metrics):
        self.calls.append((obs, actions, metrics))
        probs, value = self.outputs[len(self.calls) - 1]
        assert len(probs) == len(actions)
        return probs, value


def _row(i, n_actions=3, chosen=0, floor=None):
    return {
        "step": 10 * (i + 1),
        "surface": "card-pick",
        "phase": "card_reward",
        "n_actions": n_actions,
        "actions": [{"action": "card_reward_choose", "index": j} for j in range(n_actions)],
        "obs": {"i": i},
        "metrics": {"total_floor": floor if floor is not None else i + 1, "act_index": 1, "hp": 50},
        "chosen": chosen,
    }


def _run_row(values, chosens=None, policy="net_greedy", won=False, seed=7):
    n = len(values)
    chosens = chosens or [0] * n
    return {
        "seed": seed,
        "policy": policy,
        "character": "Ironclad",
        "ascension": 0,
        "outcome": "won" if won else "lost",
        "won": won,
        "steps": 100,
        "final": {"total_floor": n},
        "decisions": [_row(i, chosen=chosens[i]) for i in range(n)],
    }


def _net_for(values, argmax=1):
    """Uniform-ish probs with a fixed argmax, one scripted value per decision."""
    outputs = []
    for v in values:
        probs = [0.2, 0.2, 0.2]
        probs[argmax] = 0.6
        outputs.append((probs, v))
    return FakeNet(outputs)


# ── step 1: curve scoring ────────────────────────────────────────────────


def test_score_decisions_curve_values_and_deltas():
    values = [0.50, 0.52, 0.10, 0.40]
    rows = [_row(i) for i in range(4)]
    points = score_decisions(rows, _net_for(values))
    assert [p.value for p in points] == pytest.approx(values)
    assert points[0].delta_prev == pytest.approx(0.0)
    assert [p.delta_prev for p in points[1:]] == pytest.approx([0.02, -0.42, 0.30])
    assert [p.index for p in points] == [0, 1, 2, 3]
    assert points[0].step == 10 and points[0].floor == 1
    assert points[0].chosen == 0
    assert points[0].chosen_prob == pytest.approx(0.2)
    assert points[0].argmax == 1
    assert points[0].n_actions == 3


def test_turning_point_attribution_and_ranking():
    # Swings: d0 +0.02, d1 -0.42, d2 +0.30 → ranked d1, d2, d0. The LAST
    # decision (d3) has no successor V and is never curve-flagged.
    values = [0.50, 0.52, 0.10, 0.40]
    rows = [_row(i) for i in range(4)]
    points = score_decisions(rows, _net_for(values))
    tps = turning_points(points, rows, top_k=5)
    assert [t.index for t in tps] == [1, 2, 0]
    top = tps[0]
    assert top.swing == pytest.approx(-0.42)
    assert top.value_before == pytest.approx(0.52)
    assert top.value_after == pytest.approx(0.10)
    assert "dropped" in top.message and "Φ" in top.message
    assert all(t.counterfactual is None for t in tps)


def test_turning_points_top_k_and_min_swing():
    values = [0.50, 0.52, 0.10, 0.40]
    rows = [_row(i) for i in range(4)]
    points = score_decisions(rows, _net_for(values))
    assert len(turning_points(points, rows, top_k=1)) == 1
    tps = turning_points(points, rows, top_k=5, min_swing=0.1)
    assert [t.index for t in tps] == [1, 2]  # the +0.02 swing is filtered


def test_alt_is_argmax_when_played_differs_else_runner_up():
    values = [0.5, 0.4, 0.3]
    rows = [_row(i) for i in range(3)]
    # Played 0 while argmax is 1 → alternative is the policy's pick (1).
    points = score_decisions(rows, _net_for(values, argmax=1))
    tps = turning_points(points, rows, top_k=5)
    assert all(t.alt_index == 1 for t in tps)
    # Played == argmax → alternative is the runner-up by probability.
    rows2 = [_row(i, chosen=1) for i in range(3)]
    points2 = score_decisions(rows2, _net_for(values, argmax=1))
    tps2 = turning_points(points2, rows2, top_k=5)
    assert all(t.alt_index in (0, 2) for t in tps2)
    assert all(t.alt_label for t in tps2)


def test_build_report_shape_and_render():
    row = _run_row([0.5, 0.52, 0.1, 0.4])
    report = build_report(row, _net_for([0.5, 0.52, 0.1, 0.4]), top_k=2)
    assert report["run"]["seed"] == 7 and report["run"]["won"] is False
    assert len(report["curve"]) == 4
    assert len(report["turning_points"]) == 2
    assert len(report["caveats"]) == 3
    text = render_text(report)
    assert "NOT a win probability" in text
    assert "turning points" in text
    # The internal handle is present for CF mutation but excluded from curve/tps dicts.
    assert "_tps" in report


def test_build_report_requires_recorded_decisions():
    with pytest.raises(ValueError, match="no recorded decisions"):
        build_report({"seed": 1, "decisions": []}, _net_for([]))


def test_action_label():
    assert action_label({"action": "choose_map", "col": 3}) == "choose_map(col=3)"
    assert action_label({"action": "rest"}) == "rest"
    assert action_label({}) == "?"


# ── step 2: counterfactual pairing seams ─────────────────────────────────


def test_cf_sample_seed_pairing_and_determinism():
    # Same (seed, decision, k) → same value (both arms share it: CRN pairing);
    # varying any component changes it; always a valid non-negative 31-bit seed.
    assert cf_sample_seed(7, 3, 0) == cf_sample_seed(7, 3, 0)
    ks = {cf_sample_seed(7, 3, k) for k in range(16)}
    assert len(ks) == 16
    assert cf_sample_seed(7, 3, 0) != cf_sample_seed(7, 4, 0)
    assert cf_sample_seed(7, 3, 0) != cf_sample_seed(8, 3, 0)
    assert all(0 <= cf_sample_seed(-5, d, k) < 2**31 for d in range(3) for k in range(3))


def test_run_counterfactuals_pairs_arms_and_fills_points():
    values = [0.9, 0.2, 0.8]
    rows_net = _net_for(values, argmax=1)
    row = _run_row(values, chosens=[0, 0, 0])
    report = build_report(row, rows_net, top_k=2)
    tps = report["_tps"]
    assert [t.index for t in tps] == [0, 1]

    seen = []

    def fake_rollout(job):
        seen.append(job)
        # Alternative arm always wins, factual always loses → Δwin = +1.
        return {"won": job["branch_action_idx"] == 1, "outcome": "won"}

    run_counterfactuals(row, tps, rollouts=3, max_points=1, rollout_fn=fake_rollout)
    # Only the top point was paid for: 3 paired rollouts × 2 arms.
    assert len(seen) == 6
    assert tps[1].counterfactual is None
    # CRN: each pair shares its sampling seed across arms and prefixes match.
    fact = [j for j in seen if j["branch_action_idx"] == 0]
    alt = [j for j in seen if j["branch_action_idx"] == 1]
    assert [j["sample_seed"] for j in fact] == [j["sample_seed"] for j in alt]
    assert all(j["prefix"] == [] and j["prefix_n_actions"] == [] for j in seen)  # branch at d0
    assert all(j["branch_n_actions"] == 3 for j in seen)
    cf = tps[0].counterfactual
    assert cf is not None
    assert cf["delta_win"] == pytest.approx(1.0)
    assert cf["pairs_differ"] == 3
    assert not cf["overdetermined"] and not cf["suppressed"]


def test_run_counterfactuals_prefix_is_recorded_choices():
    values = [0.9, 0.2, 0.8]
    row = _run_row(values, chosens=[2, 1, 0])
    report = build_report(row, _net_for(values, argmax=1), top_k=3)
    tp = next(t for t in report["_tps"] if t.index == 1)

    jobs = []
    run_counterfactuals(
        row,
        [tp],
        rollouts=1,
        rollout_fn=lambda j: jobs.append(j) or {"won": False, "outcome": "lost"},
    )
    assert all(j["prefix"] == [2] and j["prefix_n_actions"] == [3] for j in jobs)
    assert {j["branch_action_idx"] for j in jobs} == {1, 0}  # factual=1 (recorded), alt=0


def test_run_counterfactuals_rejects_expert_runs():
    row = _run_row([0.5, 0.4], policy="expert")
    report = build_report(row, _net_for([0.5, 0.4]), top_k=1)
    with pytest.raises(ValueError, match="index-replayable"):
        run_counterfactuals(
            row, report["_tps"], rollout_fn=lambda j: {"won": True, "outcome": "won"}
        )
    assert "expert" not in REPLAYABLE_POLICIES


# ── step 3: HCA overdetermined / suppression filter ──────────────────────


def _results(wons, outcome="lost"):
    return [{"won": w, "outcome": "won" if w else outcome} for w in wons]


def test_summarize_counterfactual_delta_and_message():
    cf = summarize_counterfactual(
        _results([False, False, True, False]),
        _results([True, True, True, False]),
        threshold=0.10,
        played_label="take Anger",
        alt_label="Skip",
    )
    assert cf["n"] == 4
    assert cf["delta_win"] == pytest.approx(0.5)
    assert cf["pairs_differ"] == 2
    assert not cf["overdetermined"] and not cf["suppressed"]
    assert "Δwin +50 pts" in cf["message"] and "Skip" in cf["message"]


def test_summarize_counterfactual_overdetermined_when_pairs_never_differ():
    # Same outcome in every pair → the game didn't hinge on this choice.
    cf = summarize_counterfactual(
        _results([False, True, False]), _results([False, True, False]), threshold=0.10
    )
    assert cf["overdetermined"] and cf["suppressed"]
    assert cf["delta_win"] == pytest.approx(0.0)
    assert "overdetermined" in cf["message"]


def test_summarize_counterfactual_suppresses_below_threshold():
    # Pairs disagree symmetrically → tiny net delta: real but not coachable.
    cf = summarize_counterfactual(
        _results([True, False, True, False]), _results([False, True, True, False]), threshold=0.10
    )
    assert not cf["overdetermined"]
    assert cf["suppressed"]
    assert abs(cf["delta_win"]) < 0.10


def test_summarize_counterfactual_counts_anomalies():
    fact = _results([False, False])
    fact[0]["outcome"] = "replay_divergence"
    cf = summarize_counterfactual(fact, _results([True, True]), threshold=0.10)
    assert cf["anomalies"] == 1
    assert cf["delta_win"] == pytest.approx(1.0)
