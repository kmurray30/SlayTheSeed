"""Tests for the live card-pick advisor (``workbench.card_advisor``).

The advisor forks a real ``sts2_rs.RunManager`` and plays the rest of the act
out, so these tests need the built engine wheel; they skip cleanly when it's
absent (e.g. CI without a Rust build). Rollouts use ``sims=0`` (uniform-random
combat) so a full act plays out in milliseconds — we assert the *shape* and
*contracts* of the result, not specific win rates (which are policy/seed bound).
"""

from __future__ import annotations

import random

import pytest

sts2_rs = pytest.importorskip("sts2_rs")

from run_policy import heuristic_run_action  # noqa: E402
from workbench import card_advisor  # noqa: E402


def _run_to_first_reward(seed: str = "ADVISORTEST"):
    """Drive a fresh Ironclad run to its first plain card-reward decision."""
    rm = sts2_rs.RunManager(
        string_seed=seed, character="Ironclad", ascension_level=0, neow_start=True
    )
    rng = random.Random(7)
    for _ in range(5000):
        if rm.is_over():
            break
        if card_advisor.is_advisable(rm):
            return rm
        acts = rm.available_actions()
        if not acts:
            break
        action = rng.choice(acts) if rm.phase() == "combat" else heuristic_run_action(rm, acts, rng)
        rm.take_action(action)
    raise AssertionError("never reached a card-reward decision")


def test_clone_is_independent():
    """``copy.deepcopy`` forks the run — mutating the clone leaves the live run
    untouched (the property the advisor relies on to never disturb the run)."""
    import copy

    rm = _run_to_first_reward()
    before_hp, before_floor = rm.current_hp, rm.total_floor()
    clone = copy.deepcopy(rm)
    clone.reseed_run(999)
    # Play the clone forward; the original must not move.
    clone.take_action({"action": "pick_card_reward", "idx": None})
    assert rm.current_hp == before_hp
    assert rm.total_floor() == before_floor
    assert card_advisor.is_advisable(rm)  # original still parked on the reward


def test_is_advisable_only_on_card_reward():
    rm = sts2_rs.RunManager(string_seed="X", character="Ironclad", neow_start=True)
    # Fresh run opens on the Neow event, not a card reward.
    assert rm.phase() != "card_reward"
    assert card_advisor.is_advisable(rm) is False
    assert card_advisor.is_advisable(None) is False


def test_advise_returns_one_best_with_deltas():
    rm = _run_to_first_reward()
    result = card_advisor.advise(rm, {"rollouts": 3, "sims": 0})

    assert "error" not in result
    cands = result["candidates"]
    assert len(cands) >= 2  # at least one card + skip
    # Exactly one recommended pick.
    assert sum(c["is_best"] for c in cands) == 1
    # Sorted best-first.
    assert cands[0]["is_best"] is True

    for c in cands:
        assert 0.0 <= c["win_rate"] <= 1.0
        assert c["n"] == 3
        assert 0 <= c["wins"] <= c["n"]
        assert {"idx", "label", "win_rate", "delta", "value", "avg_hp_at_deepest"} <= c.keys()
    # The best option's delta vs the best alternative is non-negative; every
    # other option's delta is <= 0 (it trails the leader).
    best = next(c for c in cands if c["is_best"])
    assert best["delta"] >= 0
    assert all(c["delta"] <= 0 for c in cands if not c["is_best"])

    # Skip is always one of the evaluated options when the reward is skippable.
    assert any(c["idx"] is None for c in cands)


def test_advise_paired_seed_is_deterministic():
    """Same config + same parked run → identical result (seeded throughout)."""
    rm = _run_to_first_reward()
    cfg = {"rollouts": 3, "sims": 0, "seed": 4242}
    a = card_advisor.advise(rm, cfg)
    b = card_advisor.advise(rm, cfg)
    assert [c["value"] for c in a["candidates"]] == [c["value"] for c in b["candidates"]]


def test_advise_progress_callback_emits_start_and_one_tick_per_playout():
    """The optional ``progress`` callback drives the streaming progress bar: one
    ``start`` event (candidate list + total), then exactly one ``tick`` per
    completed playout with a monotonically increasing ``done`` that ends at
    ``total``."""
    rm = _run_to_first_reward()
    events: list[dict] = []
    result = card_advisor.advise(rm, {"rollouts": 2, "sims": 0}, progress=events.append)
    assert "error" not in result

    starts = [e for e in events if e["phase"] == "start"]
    ticks = [e for e in events if e["phase"] == "tick"]
    assert len(starts) == 1
    start = starts[0]
    assert start["rollouts"] == 2
    assert start["total"] == len(start["candidates"]) * 2
    # One tick per playout; done counts 1..total with the right total throughout.
    assert len(ticks) == start["total"]
    assert [t["done"] for t in ticks] == list(range(1, start["total"] + 1))
    assert all(t["total"] == start["total"] for t in ticks)
    # Every tick is attributed to one of the advertised candidate options.
    cand_idxs = {c["idx"] for c in start["candidates"]}
    assert all(t["candidate_idx"] in cand_idxs for t in ticks)


def test_advise_off_reward_returns_error():
    rm = sts2_rs.RunManager(string_seed="X", character="Ironclad", neow_start=True)
    result = card_advisor.advise(rm, {"rollouts": 1, "sims": 0})
    assert "error" in result
    assert result["candidates"] == []
