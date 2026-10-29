"""Tests for the Gumbel depth-1 search teacher (ppo.search_policy, GH #452).

Spec: docs/superpowers/specs/2026-07-05-gumbel-search-wrapper-design.md;
D1 in docs/plans/2026-08-13-ppo-alternatives-research.md.

Pure-math tests need no engine; engine-backed tests (fork mechanics,
``choose``, the ``net_search`` worker mode) build real ``PpoRunEnv``s at a
low combat budget.
"""

from __future__ import annotations

import numpy as np
import pytest
import torch

# ── pure selection math ──────────────────────────────────────────────────


def test_synthetic_seed_deterministic_positive_i32():
    from ppo.search_policy import synthetic_seed

    a = synthetic_seed(12345, 7, 0)
    assert a == synthetic_seed(12345, 7, 0)
    assert 1 <= a <= 2_147_483_647
    # varies across j and decision_idx, shared across candidates by design
    assert a != synthetic_seed(12345, 7, 1)
    assert a != synthetic_seed(12345, 8, 0)


def test_gumbel_shortlist_top_m_by_g_plus_logit():
    from ppo.search_policy import gumbel_shortlist

    logp = np.array([-1.0, -2.0, -3.0, -4.0])
    g = np.array([0.0, 0.0, 2.5, 0.0])  # boosts idx 2 past idx 1
    sl = gumbel_shortlist(logp, g, m=2)
    assert list(sl) == [2, 0]  # scores: -1.0, -2.0, -0.5, -4.0 → top2 = [2, 0]
    assert len(gumbel_shortlist(logp, g, m=10)) == 4  # m clamps to n_actions


def test_completed_q_select_reduces_to_argmax_when_q_flat():
    from ppo.search_policy import completed_q_select, gumbel_shortlist

    logp = np.array([-0.5, -1.5, -2.5])
    g = np.zeros(3)
    sl = gumbel_shortlist(logp, g, m=3)
    # equal q → σ(q̂)=0 vector → falls back to prior: argmax(g+logp)=0
    idx = completed_q_select(logp, g, sl, [1.0, 1.0, 1.0], 2, 50.0, 1.0)
    assert idx == 0


def test_completed_q_select_high_q_overrides_prior():
    from ppo.search_policy import completed_q_select, gumbel_shortlist

    logp = np.array([-0.1, -3.0])
    g = np.zeros(2)
    sl = gumbel_shortlist(logp, g, m=2)
    # σ = (50+2)·1.0·q̂; q̂ = [0,1] → +52 on idx 1 dominates the 2.9-nat prior gap
    idx = completed_q_select(logp, g, sl, [0.0, 0.2], 2, 50.0, 1.0)
    assert idx == 1


def test_completed_q_select_none_q_excluded_and_all_none_falls_back():
    from ppo.search_policy import completed_q_select, gumbel_shortlist

    logp = np.array([-0.1, -3.0, -0.2])
    g = np.zeros(3)
    sl = gumbel_shortlist(logp, g, m=3)
    # idx 1 has huge q but ALL its forks failed (None) → excluded → idx 0 wins
    assert completed_q_select(logp, g, sl, [0.1, None, 0.0], 2, 50.0, 1.0) == 0
    # every fork failed → raw net argmax
    assert completed_q_select(logp, g, sl, [None, None, None], 2, 50.0, 1.0) == 0


# ── improved-policy distillation target ──────────────────────────────────


def test_improved_policy_no_q_degrades_to_softmax():
    from ppo.search_policy import improved_policy

    logp = np.log(np.array([0.6, 0.3, 0.1]))
    pi, sv = improved_policy(logp, np.array([0, 1]), [None, None], 0.42, 2, 50.0, 1.0)
    assert np.allclose(pi, [0.6, 0.3, 0.1], atol=1e-9)
    assert sv == pytest.approx(0.42)


def test_improved_policy_boosts_high_q_and_completes_rest_at_root_value():
    from ppo.search_policy import improved_policy

    logp = np.log(np.array([0.7, 0.2, 0.1]))
    # idx 1 measured far above the root value; idx 2 unevaluated → completed
    # at root value 0.1, same as idx 0's measured q.
    pi, sv = improved_policy(logp, np.array([0, 1]), [0.1, 0.9], 0.1, 2, 50.0, 1.0)
    assert pi.shape == (3,)
    assert pi.sum() == pytest.approx(1.0)
    assert pi[1] > 0.99  # σ ≈ +52 nats on idx 1 dominates the prior
    assert 0.1 <= sv <= 0.9  # π-weighted mean of completed q stays in range


def test_improved_policy_flat_q_keeps_prior():
    from ppo.search_policy import improved_policy

    logp = np.log(np.array([0.5, 0.4, 0.1]))
    pi, sv = improved_policy(logp, np.array([0, 1, 2]), [0.3, 0.3, 0.3], 0.3, 2, 50.0, 1.0)
    assert np.allclose(pi, [0.5, 0.4, 0.1], atol=1e-9)
    assert sv == pytest.approx(0.3)


def test_q_temp_is_a_fixed_scale_unlike_minmax():
    """The legacy min-max σ stretches ANY candidate spread to fill [0,1], so
    a near-tied decision gets the same σ span as a decisive one. A fixed
    q_temp must keep near-ties near-flat (FINDINGS.md: the −35pp cause)."""
    from ppo.search_policy import _sigma

    tied = np.array([0.500, 0.501])
    decisive = np.array([0.1, 0.9])
    # legacy: identical σ span for both — the pathology
    assert np.ptp(_sigma(tied, 2, 50.0, 1.0)) == pytest.approx(
        np.ptp(_sigma(decisive, 2, 50.0, 1.0))
    )
    # fixed scale: span is proportional to the real gap
    assert np.ptp(_sigma(tied, 2, 50.0, 1.0, q_temp=0.02)) == pytest.approx(0.05)
    assert np.ptp(_sigma(decisive, 2, 50.0, 1.0, q_temp=0.02)) == pytest.approx(40.0)


def test_q_temp_rejects_nonpositive():
    from ppo.search_policy import _sigma

    with pytest.raises(ValueError, match="q_temp must be > 0"):
        _sigma(np.array([0.0, 1.0]), 2, 50.0, 1.0, q_temp=0.0)


def test_q_temp_threads_through_select_and_improved_policy():
    from ppo.search_policy import completed_q_select, improved_policy

    logp = np.log(np.array([0.7, 0.3]))
    g = np.zeros(2)
    sl = np.array([0, 1])
    q = [0.50, 0.51]  # idx 1 better by 0.01 — one q_temp unit
    # coarse temp: 0.01 gap buys 0.01 nat, nowhere near the 0.85-nat prior gap
    assert completed_q_select(logp, g, sl, q, 2, 50.0, 1.0, q_temp=1.0) == 0
    # fine temp: 0.01 gap buys 10 nats, overrides the prior
    assert completed_q_select(logp, g, sl, q, 2, 50.0, 1.0, q_temp=0.001) == 1
    pi_cold, _ = improved_policy(logp, sl, q, 0.5, 2, 50.0, 1.0, q_temp=1.0)
    pi_hot, _ = improved_policy(logp, sl, q, 0.5, 2, 50.0, 1.0, q_temp=0.001)
    assert pi_cold[0] > pi_cold[1]  # prior still rules
    assert pi_hot[1] > pi_hot[0]  # q rules


# ── bc soft-CE plumbing (no engine) ──────────────────────────────────────


def _mk_sample(chosen, n_actions, pi=None, search_value=None, ret=0.0):
    from ppo.bc import Sample

    return Sample(
        feat=None,  # _soft_targets never touches feat
        chosen=chosen,
        ret=ret,
        surface="card-pick",
        n_actions=n_actions,
        pi=pi,
        search_value=search_value,
    )


def test_soft_targets_one_hot_without_pi():
    from ppo.bc import _soft_targets

    t = _soft_targets([_mk_sample(2, 4), _mk_sample(0, 2)], 5, torch.device("cpu"))
    assert t.shape == (2, 5)
    assert torch.allclose(t[0], torch.tensor([0.0, 0.0, 1.0, 0.0, 0.0]))
    assert torch.allclose(t[1], torch.tensor([1.0, 0.0, 0.0, 0.0, 0.0]))


def test_soft_targets_renormalize_pi_and_pad():
    from ppo.bc import _soft_targets

    t = _soft_targets([_mk_sample(0, 3, pi=[0.2, 0.2, 0.6])], 5, torch.device("cpu"))
    assert t.shape == (1, 5)
    assert torch.allclose(t[0, :3], torch.tensor([0.2, 0.2, 0.6]))
    assert int(torch.count_nonzero(t[0, 3:])) == 0


def test_soft_ce_reduces_to_hard_ce_on_one_hot():
    from ppo.bc import _soft_targets
    from torch.nn import functional as F

    logits = torch.tensor([[1.0, -2.0, 0.5, float("-inf")]])
    mb = [_mk_sample(2, 3)]
    target = _soft_targets(mb, 4, torch.device("cpu"))
    logp = F.log_softmax(logits, dim=-1)
    soft = -(target * torch.where(target > 0, logp, logp.new_zeros(()))).sum(-1).mean()
    hard = F.cross_entropy(logits, torch.tensor([2]))
    assert torch.isfinite(soft)  # -inf padded slot must not poison the loss
    assert float(soft) == pytest.approx(float(hard), abs=1e-6)


# ── ab_compare (no engine) ───────────────────────────────────────────────


def test_mcnemar_exact_known_value():
    from ppo.ab_compare import mcnemar_exact

    # b=2, c=8 discordant: p = 2·P(X≤2 | n=10, ½) = 112/1024
    assert mcnemar_exact(2, 8) == pytest.approx(112 / 1024)
    assert mcnemar_exact(0, 0) == pytest.approx(1.0)
    assert mcnemar_exact(5, 5) == pytest.approx(1.0)


def test_ab_compare_cli(tmp_path, capsys):
    import json

    from ppo.ab_compare import main

    base, treat = tmp_path / "a.jsonl", tmp_path / "b.jsonl"
    rows_a = [
        {"seed": s, "won": s < 10, "outcome": "won" if s < 10 else "lost"} for s in range(100)
    ]
    rows_b = [
        {"seed": s, "won": s < 14, "outcome": "won" if s < 14 else "lost"} for s in range(100)
    ]
    rows_a.append({"seed": 100, "won": False, "outcome": "error"})  # excluded
    rows_b.append({"seed": 100, "won": True, "outcome": "won"})
    base.write_text("\n".join(json.dumps(r) for r in rows_a))
    treat.write_text("\n".join(json.dumps(r) for r in rows_b))
    assert main([str(base), str(treat)]) == 0
    out = capsys.readouterr().out
    assert "paired seeds: 100" in out
    assert "baseline WR: 10/100" in out
    assert "treatment WR: 14/100" in out
    assert "discordant: b=0 c=4" in out


# ── engine-backed tests ──────────────────────────────────────────────────

# Low combat budget keeps these tests fast; the first yielded decision is
# the Neow event, reached without combat.
_SIMS = 50


def _first_decision(seed: int = 0):
    from ppo.env import Decision, PpoRunEnv

    env = PpoRunEnv(seed=seed, sims=_SIMS, boss_sims=_SIMS)
    out = env.reset()
    assert isinstance(out, Decision)
    return env, out


def test_logits_value_consistent_with_greedy_sample():
    from ppo.net_policy import NetPolicy

    _, d = _first_decision(seed=0)
    torch.manual_seed(0)
    pol = NetPolicy(None)  # random-init net — no checkpoint needed
    logp, value, _feat = pol.logits_value(d.obs, d.actions, d.metrics)
    assert logp.shape == (len(d.actions),)
    assert np.isclose(np.exp(logp).sum(), 1.0, atol=1e-5)
    idx, s_logp, s_value, _ = pol.sample(
        d.obs, d.actions, d.metrics, np.random.default_rng(0), greedy=True
    )
    assert idx == int(np.argmax(logp))
    assert np.isclose(s_logp, logp[idx], atol=1e-6)
    assert np.isclose(s_value, value, atol=1e-6)


def test_potential_is_base_plus_shape():
    from ppo.rewards import ShapingConfig, base_value, potential, shape_value

    m = {"total_floor": 5, "hp": 40, "max_hp": 80, "num_upgraded": 2, "num_relics": 3}
    cfg = ShapingConfig()
    assert potential(m, cfg) == pytest.approx(base_value(m, False, cfg) + shape_value(m, cfg))


def _walk(env, first, n_decisions):
    """Step `first` then always candidate 0, recording per-decision facts."""
    from ppo.env import Decision

    rec, out = [], first
    while isinstance(out, Decision) and len(rec) < n_decisions:
        rec.append((out.surface, len(out.actions), out.metrics["hp"], out.metrics["gold"]))
        out = env.step(0)
    return rec


# Seed pinned by procedure: try seeds 0..9 and use the first that passes all
# three fork tests. Seed 0 passes; do NOT weaken the assertions to re-pin.
FORK_SEED = 0


def test_fork_without_reseed_matches_real_timeline():
    """An un-reseeded clone stepping the same action reproduces the parent's
    next decision EXACTLY — proving clone fidelity AND that reseeding is
    load-bearing (spec: clairvoyance-leak test, part 1)."""
    from ppo.env import Decision, PpoRunEnv

    env, d = _first_decision(seed=FORK_SEED)
    fork_rm = env.rm.clone()
    fenv = PpoRunEnv.from_fork(fork_rm, like=env, sims=env.sims, boss_sims=env.boss_sims)
    fenv._pending_actions = list(d.actions)
    fout = fenv.step(0)
    rout = env.step(0)
    assert isinstance(fout, Decision) and isinstance(rout, Decision)
    assert fout.surface == rout.surface
    assert fout.actions == rout.actions
    assert fout.metrics == rout.metrics
    assert fout.obs == rout.obs


def test_fork_with_reseed_diverges_from_real_timeline():
    """A reseeded clone must sample a DIFFERENT future (anti-clairvoyance,
    part 2). Walk both forks identically; assert divergence within 6
    decisions. Deterministic for the pinned seed."""
    from ppo.env import PpoRunEnv

    env, d = _first_decision(seed=FORK_SEED)

    def fork(reseed):
        rm = env.rm.clone()
        if reseed:
            rm.reseed_run(999_983)
        f = PpoRunEnv.from_fork(rm, like=env, sims=env.sims, boss_sims=env.boss_sims)
        f._pending_actions = list(d.actions)
        return _walk(f, f.step(0), 6)

    assert fork(reseed=False) != fork(reseed=True), (
        "reseeded fork tracked the real timeline for 6 decisions — either "
        "extend the walk or re-pin FORK_SEED; do not delete this test"
    )


def test_fork_does_not_perturb_parent():
    """Fork isolation: interleaving fork+step at each decision leaves the
    parent trajectory bit-identical to a never-forked control run."""
    from ppo.env import Decision, PpoRunEnv

    def run(with_forks):
        env = PpoRunEnv(seed=FORK_SEED, sims=_SIMS, boss_sims=_SIMS)
        out, rec = env.reset(), []
        while isinstance(out, Decision) and len(rec) < 5:
            if with_forks:
                rm = env.rm.clone()
                rm.reseed_run(7)
                f = PpoRunEnv.from_fork(rm, like=env, sims=_SIMS, boss_sims=_SIMS)
                f._pending_actions = list(out.actions)
                f.step(len(out.actions) - 1)  # deliberately different action
            rec.append((out.surface, len(out.actions), out.metrics["hp"], out.metrics["gold"]))
            out = env.step(0)
        return rec

    assert run(with_forks=False) == run(with_forks=True)


def _search_choose(seed, cfg_kwargs):
    from ppo.net_policy import NetPolicy
    from ppo.search_policy import SearchConfig, SearchPolicy

    env, d = _first_decision(seed=seed)
    torch.manual_seed(0)  # random-init net must be identical across calls
    pol = SearchPolicy(NetPolicy(None), SearchConfig(**cfg_kwargs))
    return pol.choose(env, d, np.random.default_rng(0)), d


def test_choose_is_deterministic_and_in_range():
    cfg = dict(m=3, n=1, combat_sims=_SIMS, combat_boss_sims=_SIMS)
    (idx1, diag1), d = _search_choose(FORK_SEED, cfg)
    (idx2, diag2), _ = _search_choose(FORK_SEED, cfg)
    assert idx1 == idx2 and diag1["q"] == diag2["q"]
    assert 0 <= idx1 < len(d.actions)
    assert diag1["override"] == (idx1 != diag1["root_argmax"])
    assert len(diag1["shortlist"]) == min(3, len(d.actions))
    # distillation targets: π′ over ALL candidates, normalized
    assert len(diag1["pi"]) == len(d.actions)
    assert sum(diag1["pi"]) == pytest.approx(1.0, abs=1e-6)
    assert diag1["search_value"] == diag2["search_value"]


def test_choose_terminal_leaf_branch():
    # fork_max_steps=1 forces every fork to Terminal('max_steps') → exercises
    # the terminal q branch (base_value(final, won) − Φ(parent)).
    cfg = dict(m=2, n=1, combat_sims=_SIMS, combat_boss_sims=_SIMS, fork_max_steps=1)
    (idx, diag), d = _search_choose(FORK_SEED, cfg)
    assert 0 <= idx < len(d.actions)
    assert all(q is not None for q in diag["q"])
    assert diag["terminal_leaves"] == len([q for q in diag["q"] if q is not None])


@pytest.mark.slow
def test_play_one_net_search_smoke():
    from ppo.workers import play_one

    row = play_one(
        FORK_SEED,
        policy="net_search",
        ckpt_path=None,  # random-init net
        sims=_SIMS,
        boss_sims=_SIMS,
        max_steps=120,  # bounded smoke run
        record_decisions=True,
        search={"m": 3, "n": 1, "combat_sims": _SIMS, "combat_boss_sims": _SIMS},
    )
    assert row["outcome"] in ("won", "lost", "max_steps", "no_actions", "agent_halt")
    st = row["search_stats"]
    assert st["searched"] >= 1
    assert 0 <= st["overrides"] <= st["searched"]
    assert sum(v[1] for v in st["by_surface"].values()) == st["searched"]
    # every recorded decision carries the distillation targets
    for d in row["decisions"]:
        assert len(d["pi"]) == d["n_actions"]
        assert sum(d["pi"]) == pytest.approx(1.0, abs=1e-6)
        assert isinstance(d["search_value"], float)
