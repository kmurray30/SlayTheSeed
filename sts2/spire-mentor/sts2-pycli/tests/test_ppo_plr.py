"""Unit gates for Prioritized Level Replay (Jiang et al. 2021) over (seed, ascension).

Sampler math: P = (1-rho)*P_S + rho*P_C with rank prioritization
P_S ∝ (1/rank)^(1/beta) (Eq. 3) and staleness P_C ∝ (it - last_it) (Eq. 4).
Trainer wiring: collect_rows replays buffer levels with prob plr_replay_p and
stays byte-identical to the legacy seed stream when PLR is off.
"""

import os
import sys

import numpy as np
import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "python"))

from ppo.plr import LevelSampler
from ppo.rewards import ShapingConfig
from ppo.trainer import PPOConfig, collect_rows, gae_from_row, plr_score

# ── sampler math ─────────────────────────────────────────────────────────────


def test_rank_prioritization_picks_the_top_score_first():
    """(1/rank)^(1/0.1): rank 1 outweighs rank 2 by 2^10, so a single draw is
    the top-scoring level essentially always."""
    s = LevelSampler(cap=8, beta=0.1, rho=0.0)
    s.update((1, 0), score=0.1, it=0)
    s.update((2, 3), score=0.9, it=0)
    s.update((3, 7), score=0.5, it=0)
    rng = np.random.default_rng(0)
    firsts = [s.sample(1, it=1, rng=rng)[0] for _ in range(200)]
    assert firsts.count((2, 3)) > 190


def test_one_batch_draw_does_not_collapse_onto_a_single_level():
    """A whole iteration's replay slots must be DISTINCT levels.

    PLR is online: it re-scores level l when its episode ends, before the next
    draw, so rank 1 churns away. We draw a whole iteration's replays at once
    from a frozen distribution — and with the paper's beta=0.1 the exponent is
    1/beta = 10, so P_S(rank 1) = 0.999 for ANY buffer size. Drawing i.i.d.
    would put ~90% of the batch on one (seed, ascension); the 2026-09-01 smoke
    did exactly that (49 of 51 replays on one level). Draw without replacement
    instead: same prioritization, no collapse.
    """
    s = LevelSampler(cap=512, beta=0.1, rho=0.1)
    for i in range(300):
        s.update((i, i % 11), score=1.0 - i / 1000.0, it=0)
    draws = s.sample(48, it=1, rng=np.random.default_rng(0))
    assert len(draws) == 48
    assert len(set(draws)) == 48


def test_draw_without_replacement_still_prefers_high_scores():
    s = LevelSampler(cap=512, beta=0.1, rho=0.0)
    for i in range(100):
        s.update((i, 0), score=float(i), it=0)  # seed 99 = best
    draws = s.sample(5, it=1, rng=np.random.default_rng(0))
    assert set(draws) == {(99, 0), (98, 0), (97, 0), (96, 0), (95, 0)}


def test_draw_larger_than_the_buffer_cycles_in_rounds():
    """n > len(buffer) can't be distinct; each ROUND is, so nothing collapses."""
    s = LevelSampler(cap=8, beta=0.1, rho=0.1)
    for i in range(3):
        s.update((i, 0), score=float(i), it=0)
    draws = s.sample(7, it=1, rng=np.random.default_rng(0))
    assert len(draws) == 7
    counts = {k: draws.count(k) for k in set(draws)}
    assert set(counts) == {(0, 0), (1, 0), (2, 0)}
    assert max(counts.values()) - min(counts.values()) <= 1


def test_pure_staleness_prefers_the_oldest_level():
    s = LevelSampler(cap=8, beta=0.1, rho=1.0)
    s.update((1, 0), score=0.9, it=0)  # high score, but fresh below
    s.update((2, 0), score=0.1, it=9)
    rng = np.random.default_rng(0)
    # one draw at a time: `sample` re-touches last_it, so re-seed each time
    n_old = 0
    for _ in range(200):
        s.update((1, 0), 0.9, 0)
        s.update((2, 0), 0.1, 9)
        n_old += s.sample(1, it=10, rng=rng)[0] == (1, 0)
    assert n_old > 150  # staleness 10 vs 1


def test_sampling_touches_last_it():
    s = LevelSampler(cap=4, beta=0.1, rho=0.5)
    s.update((1, 0), score=0.5, it=0)
    s.sample(1, it=7, rng=np.random.default_rng(0))
    (lv,) = s.state_dict()["levels"]
    assert lv[3] == 7  # (seed, asc, score, last_it)


def test_cap_evicts_the_lowest_score():
    s = LevelSampler(cap=2, beta=0.1, rho=0.0)
    s.update((1, 0), score=0.5, it=0)
    s.update((2, 0), score=0.1, it=0)
    s.update((3, 0), score=0.9, it=0)
    keys = {(lv[0], lv[1]) for lv in s.state_dict()["levels"]}
    assert keys == {(1, 0), (3, 0)}


def test_update_rescores_in_place():
    s = LevelSampler(cap=4, beta=0.1, rho=0.0)
    s.update((1, 2), score=0.5, it=0)
    s.update((1, 2), score=0.7, it=3)
    (lv,) = s.state_dict()["levels"]
    assert list(lv) == [1, 2, 0.7, 3]
    assert len(s) == 1


def test_state_dict_roundtrip_reproduces_sampling():
    a = LevelSampler(cap=8, beta=0.5, rho=0.3)
    for i in range(5):
        a.update((i, i % 3), score=float(i), it=i)
    b = LevelSampler(cap=8, beta=0.5, rho=0.3)
    b.load_state_dict(a.state_dict())
    da = a.sample(50, it=9, rng=np.random.default_rng(4))
    db = b.sample(50, it=9, rng=np.random.default_rng(4))
    assert da == db


def test_empty_sampler_yields_nothing():
    s = LevelSampler(cap=4, beta=0.1, rho=0.1)
    assert s.sample(3, it=1, rng=np.random.default_rng(0)) == []
    assert len(s) == 0


# ── trainer wiring ───────────────────────────────────────────────────────────


def _capture_play_batch(monkeypatch, captured):
    import ppo.trainer as T

    def fake(seeds, **kw):
        captured["seeds"] = list(seeds)
        captured.update(kw)
        return [{"seed": s, "outcome": "died", "won": False} for s in seeds]

    monkeypatch.setattr(T, "play_batch", fake)


def _cfg(**kw):
    return PPOConfig(**{"games_per_iter": 8, "seed_base": 1000, **kw})


def test_collect_rows_without_plr_keeps_the_legacy_seed_stream(monkeypatch):
    captured = {}
    _capture_play_batch(monkeypatch, captured)
    cfg = _cfg()
    collect_rows(cfg, 3, "ckpt.pt")
    assert captured["seeds"] == [1000 + 3 * 8 + g for g in range(8)]
    assert captured.get("sample_seeds") in (None, [None] * 8)


def test_collect_rows_with_empty_buffer_is_all_fresh(monkeypatch):
    captured = {}
    _capture_play_batch(monkeypatch, captured)
    cfg = _cfg(plr_replay_p=1.0)
    collect_rows(cfg, 3, "ckpt.pt", sampler=LevelSampler(cap=4, beta=0.1, rho=0.1))
    assert captured["seeds"] == [1000 + 3 * 8 + g for g in range(8)]


def test_collect_rows_replays_buffer_levels_with_fresh_sample_seeds(monkeypatch):
    captured = {}
    _capture_play_batch(monkeypatch, captured)
    cfg = _cfg(plr_replay_p=1.0, ascension_weights="0:1,5:1")
    s = LevelSampler(cap=4, beta=0.1, rho=0.1)
    s.update((77, 9), score=0.9, it=0)
    s.update((88, 2), score=0.8, it=0)
    collect_rows(cfg, 3, "ckpt.pt", sampler=s)
    pairs = set(zip(captured["seeds"], captured["ascensions"]))
    assert pairs <= {(77, 9), (88, 2)}  # p=1.0: every slot replayed
    # a replayed run must NOT reuse its game seed as the action-sampling seed
    for seed, ss in zip(captured["seeds"], captured["sample_seeds"]):
        assert ss is not None and ss != seed


def test_collect_rows_marks_replayed_rows(monkeypatch):
    captured = {}
    _capture_play_batch(monkeypatch, captured)
    cfg = _cfg(plr_replay_p=1.0)
    s = LevelSampler(cap=4, beta=0.1, rho=0.1)
    s.update((77, 9), score=0.9, it=0)
    rows = collect_rows(cfg, 3, "ckpt.pt", sampler=s)
    assert all(r.get("plr_replayed") for r in rows)
    captured.clear()
    rows = collect_rows(_cfg(), 3, "ckpt.pt")  # PLR off: no annotation
    assert not any(r.get("plr_replayed") for r in rows)


def test_collect_rows_replay_is_deterministic_per_iteration(monkeypatch):
    c1, c2 = {}, {}
    cfg = _cfg(plr_replay_p=0.5)

    def run(cap):
        s = LevelSampler(cap=8, beta=0.1, rho=0.1)
        for i in range(4):
            s.update((100 + i, i), score=float(i), it=0)
        _capture_play_batch(monkeypatch, cap)
        collect_rows(cfg, 5, "ckpt.pt", sampler=s)

    run(c1)
    run(c2)
    assert c1["seeds"] == c2["seeds"]
    assert c1["ascensions"] == c2["ascensions"]
    assert c1["sample_seeds"] == c2["sample_seeds"]


def test_plr_score_is_mean_abs_gae():
    cfg = PPOConfig()
    shaping = ShapingConfig()
    trans = [
        {
            "feat": None,
            "action_idx": 0,
            "old_logprob": 0.0,
            "value": v,
            "metrics": {
                "total_floor": f,
                "hp": 50,
                "max_hp": 80,
                "num_upgraded": 0,
                "num_relics": 1,
                "gold": 0,
                "act_index": 1,
            },
        }
        for f, v in [(1, 0.3), (4, 0.5), (9, 0.4)]
    ]
    row = {
        "transitions": trans,
        "won": False,
        "ascension": 0,
        "final": {
            "total_floor": 12,
            "hp": 0,
            "max_hp": 80,
            "num_upgraded": 0,
            "num_relics": 1,
            "gold": 0,
            "act_index": 1,
        },
    }
    expect = float(np.mean([abs(t.advantage) for t in gae_from_row(row, cfg, shaping)]))
    assert plr_score(row, cfg, shaping) == pytest.approx(expect)


def test_plr_score_none_for_empty_transitions():
    assert plr_score({"transitions": []}, PPOConfig(), ShapingConfig()) is None


def _row(seed, asc, floor=9, replayed=False, outcome="died"):
    r = {
        "seed": seed,
        "ascension": asc,
        "won": False,
        "outcome": outcome,
        "transitions": [
            {
                "feat": None,
                "action_idx": 0,
                "old_logprob": 0.0,
                "value": 0.4,
                "metrics": {
                    "total_floor": floor,
                    "hp": 40,
                    "max_hp": 80,
                    "num_upgraded": 0,
                    "num_relics": 1,
                    "gold": 0,
                    "act_index": 1,
                },
            }
        ],
        "final": {
            "total_floor": floor + 2,
            "hp": 0,
            "max_hp": 80,
            "num_upgraded": 0,
            "num_relics": 1,
            "gold": 0,
            "act_index": 1,
        },
    }
    if replayed:
        r["plr_replayed"] = True
    return r


def test_train_refuses_plr_with_async_collect():
    """The background collector reads the sampler while update_and_log scores
    into it — a data race. PLR is sync-only until that's designed for."""
    from ppo.trainer import train

    with pytest.raises(ValueError, match="plr"):
        train(_cfg(plr_replay_p=0.5, async_collect=True))


def test_plr_update_from_rows_scores_runs_and_reports_stats():
    from ppo.trainer import plr_update_from_rows

    s = LevelSampler(cap=8, beta=0.1, rho=0.1)
    rows = [
        _row(1, 0),
        _row(2, 5, replayed=True),
        _row(3, 9, outcome="error"),  # must NEVER enter the buffer
        {"seed": 4, "outcome": "timeout", "won": False},  # nor this
    ]
    stats = plr_update_from_rows(s, rows, PPOConfig(), ShapingConfig(), it=7)
    assert len(s) == 2
    keys = {(lv[0], lv[1]) for lv in s.state_dict()["levels"]}
    assert keys == {(1, 0), (2, 5)}
    assert stats["plr_buffer_n"] == 2
    assert stats["plr_replay_frac"] == pytest.approx(1 / 4)
    assert stats["plr_replayed/A5"] == 1


def test_stats_report_replay_concentration_not_just_ascension():
    """The per-ascension counts are too coarse to catch a batch collapsing onto
    one level: 480 replays over a 4096-level buffer can be 100% one level and
    still look like a normal ascension spread. Report the distinct-level count
    and the top level's share directly."""
    from ppo.trainer import plr_update_from_rows

    s = LevelSampler(cap=8, beta=0.1, rho=0.1)
    rows = [_row(1, 3, replayed=True) for _ in range(9)] + [_row(2, 3, replayed=True)]
    stats = plr_update_from_rows(s, rows, PPOConfig(), ShapingConfig(), it=7)
    assert stats["plr_replay_distinct"] == 2
    assert stats["plr_replay_top_share"] == pytest.approx(0.9)


# ── workers plumbing ─────────────────────────────────────────────────────────


@pytest.mark.skipif(sys.platform == "win32", reason="fork context unavailable on Windows")
def test_play_batch_routes_per_seed_sample_seeds_to_play_one(monkeypatch):
    """`sample_seeds` must reach each worker's `play_one` as `sample_seed`.

    fork context so the monkeypatched `play_one` is inherited by the workers
    (same trick as the stall-watchdog test).
    """
    from ppo import workers

    def fake_play_one(seed, policy="random", sample_seed=None, **kw):
        return {
            "seed": seed,
            "policy": policy,
            "outcome": "ok",
            "won": False,
            "sample_seed": sample_seed,
        }

    monkeypatch.setattr(workers, "play_one", fake_play_one)
    rows = workers.play_batch(
        [10, 11, 12],
        policy="net",
        procs=2,
        mp_context="fork",
        sample_seeds=[None, 777, 888],
    )
    assert [r["sample_seed"] for r in rows] == [None, 777, 888]


def test_play_batch_rejects_mislengthed_sample_seeds():
    from ppo import workers

    with pytest.raises(ValueError):
        workers.play_batch([1, 2, 3], sample_seeds=[None])
