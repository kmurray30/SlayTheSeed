"""Tests for the generic decision advisor (``workbench.decision_advisor``).

Pure key/label/gating contracts run without the engine; the Monte-Carlo paths
fork a real ``sts2_rs.RunManager`` and play the rest of the act out, so they
need the built wheel and skip cleanly when it's absent. Rollouts use ``sims=0``
(uniform-random combat) so a full act plays out in milliseconds — we assert the
*shape* and *contracts*, not specific win rates.
"""

from __future__ import annotations

import random

import pytest
from workbench import decision_advisor as da


# ── pure contracts (no engine) ────────────────────────────────────────────────
def test_candidate_key_label_maps_every_advisable_verb():
    cases = {
        "choice:skip": {"action": "combat_resolve_choice", "option": None},
        "choice:0": {"action": "combat_resolve_choice", "option": 0},
        "card:2": {"action": "shop_buy_card", "idx": 2},
        "relic:1": {"action": "shop_buy_relic", "idx": 1},
        "potion:0": {"action": "shop_buy_potion", "idx": 0},
        "leave": {"action": "leave_shop"},
        "option:3": {"action": "event_choice", "idx": 3},
        "boss_relic:1": {"action": "pick_boss_relic", "idx": 1},
    }
    for expected_key, action in cases.items():
        kl = da._candidate_key_label(action)
        assert kl is not None and kl[0] == expected_key


def test_candidate_key_label_ignores_non_advisable_verbs():
    # Multi-pick prefixes and combat verbs aren't single-option candidates.
    assert (
        da._candidate_key_label({"action": "combat_resolve_choice_take_first", "count": 2}) is None
    )
    assert da._candidate_key_label({"action": "play_card", "hand_idx": 0}) is None
    assert da._candidate_key_label({"action": "end_turn"}) is None
    assert da._candidate_key_label({"action": "pick_card_reward", "idx": 0}) is None


class _FakeRm:
    """Minimal stand-in exercising the engine-free branches of is_advisable."""

    def __init__(self, phase, acts, pending=None, choose_screen=False, option_ids=()):
        self._phase = phase
        self._acts = acts
        self._pending = pending
        # The engine's run-level choose-screen marker (Lead Paperweight's 2
        # Colorless / Hefty Tablet's 3 Rares) and the option ids the host
        # renders, in `pick_card_reward` idx order.
        self.card_offer_choose_screen = choose_screen
        self._option_ids = list(option_ids)

    def card_reward_option_ids(self):
        return self._option_ids

    def phase(self):
        return self._phase

    def available_actions(self):
        return self._acts

    def combat_state(self):
        if self._phase != "combat":
            return None

        class _CS:
            def __init__(self, pc):
                self._pc = pc

            def pending_choice(self):
                return self._pc

        return _CS(self._pending)


def test_is_advisable_gating():
    assert da.is_advisable(None) is False
    # Map / plain combat / card_reward are not advisable here.
    assert da.is_advisable(_FakeRm("map_choice", [{"action": "move", "col": 0}])) is False
    assert da.is_advisable(_FakeRm("combat", [{"action": "end_turn"}], pending=None)) is False
    # An event with two options is advisable.
    assert (
        da.is_advisable(
            _FakeRm(
                "event",
                [{"action": "event_choice", "idx": 0}, {"action": "event_choice", "idx": 1}],
            )
        )
        is True
    )
    # A single-option decision is not a real choice.
    assert da.is_advisable(_FakeRm("event", [{"action": "event_choice", "idx": 0}])) is False
    # Single-pick in-combat choice with two options → advisable.
    choice_acts = [
        {"action": "combat_resolve_choice", "option": 0},
        {"action": "combat_resolve_choice", "option": 1},
    ]
    assert da.is_advisable(_FakeRm("combat", choice_acts, pending={"max_select": 1})) is True
    # Multi-pick choice is excluded.
    assert da.is_advisable(_FakeRm("combat", choice_acts, pending={"max_select": 3})) is False


def _paperweight_rm(skip=True):
    """A stand-in for Lead Paperweight's 2-Colorless choose screen.

    The engine offers it through the ``card_reward`` phase's
    ``pick_card_reward`` fan-out (skip included, C# ``canSkip: true``) while the
    host renders it as a ``card_select`` — the shape that used to fall between
    both advisors.
    """
    acts = [
        {"action": "pick_card_reward", "idx": 0},
        {"action": "pick_card_reward", "idx": 1},
    ]
    if skip:
        acts.append({"action": "pick_card_reward", "idx": None})
    return _FakeRm("card_reward", acts, choose_screen=True, option_ids=["Prolong", "DarkShackles"])


def test_is_advisable_choose_screen():
    # Lead Paperweight / Hefty Tablet: a card_reward-phase fan-out the host
    # shows as a card_select, so the decision advisor — not the card advisor —
    # is the one the UI asks for it.
    assert da.is_advisable(_paperweight_rm()) is True
    # A PLAIN card reward keeps belonging to workbench.card_advisor: same
    # actions, no choose-screen marker.
    plain = _FakeRm(
        "card_reward",
        [
            {"action": "pick_card_reward", "idx": 0},
            {"action": "pick_card_reward", "idx": 1},
        ],
    )
    assert da.is_advisable(plain) is False


def test_choose_screen_candidates_use_the_card_select_key_space():
    # The frontend joins its chips on `choice:<card index>` / `choice:skip`
    # (CardSelect.tsx), so the keys have to be those and not `card:<idx>`.
    cands = da._candidates(_paperweight_rm())
    assert [key for key, _, _ in cands] == ["choice:0", "choice:1", "choice:skip"]
    # Actions stay the engine's own picks, in the same index space.
    assert [a for _, _, a in cands] == [
        {"action": "pick_card_reward", "idx": 0},
        {"action": "pick_card_reward", "idx": 1},
        {"action": "pick_card_reward", "idx": None},
    ]
    # Labels come from the offered card ids.
    assert [label for _, label, _ in cands] == ["PROLONG", "DARK_SHACKLES", "skip"]


def test_choose_screen_reports_the_card_select_decision():
    # The advice has to name the screen the UI is showing, not the phase.
    assert da._decision_of(_paperweight_rm()) == "card_select"


def test_choose_screen_without_skip_still_advisable():
    rm = _paperweight_rm(skip=False)
    assert da.is_advisable(rm) is True
    assert [key for key, _, _ in da._candidates(rm)] == ["choice:0", "choice:1"]


# ── engine-backed Monte-Carlo paths ───────────────────────────────────────────
sts2_rs = pytest.importorskip("sts2_rs")

from run_policy import heuristic_run_action  # noqa: E402


def _fresh_run(seed="DECISIONADV"):
    return sts2_rs.RunManager(
        string_seed=seed, character="Ironclad", ascension_level=0, neow_start=True
    )


def _drive_to(pred, seed, max_steps=9000):
    """Drive a fresh Ironclad run until *pred(rm)* holds, or return ``None``."""
    rm = _fresh_run(seed)
    rng = random.Random(7)
    for _ in range(max_steps):
        if rm.is_over():
            return None
        if pred(rm):
            return rm
        acts = rm.available_actions()
        if not acts:
            return None
        action = rng.choice(acts) if rm.phase() == "combat" else heuristic_run_action(rm, acts, rng)
        rm.take_action(action)
    return None


def test_advise_event_returns_one_best_with_deltas():
    # The fresh run opens on the Neow event — instant, no driving needed.
    rm = _fresh_run()
    assert rm.phase() == "event"
    result = da.advise(rm, {"rollouts": 3, "sims": 0})

    assert "error" not in result
    assert result["decision"] == "event_choice"
    cands = result["candidates"]
    assert len(cands) >= 2
    assert sum(c["is_best"] for c in cands) == 1
    assert cands[0]["is_best"] is True
    for c in cands:
        assert 0.0 <= c["win_rate"] <= 1.0
        assert c["n"] == 3
        assert {"key", "label", "win_rate", "delta", "value"} <= c.keys()
    best = next(c for c in cands if c["is_best"])
    assert best["delta"] >= 0
    assert all(c["delta"] <= 0 for c in cands if not c["is_best"])
    # Keys are the engine option indices the frontend joins on.
    assert {c["key"] for c in cands} == {"option:0", "option:1", "option:2"}


def test_advise_paired_seed_is_deterministic():
    rm = _fresh_run()
    cfg = {"rollouts": 3, "sims": 0, "seed": 4242}
    a = da.advise(rm, cfg)
    b = da.advise(rm, cfg)
    assert [c["value"] for c in a["candidates"]] == [c["value"] for c in b["candidates"]]


def test_advise_progress_callback_emits_start_and_one_tick_per_playout():
    rm = _fresh_run()
    events: list[dict] = []
    result = da.advise(rm, {"rollouts": 2, "sims": 0}, progress=events.append)
    assert "error" not in result

    starts = [e for e in events if e["phase"] == "start"]
    ticks = [e for e in events if e["phase"] == "tick"]
    assert len(starts) == 1
    start = starts[0]
    assert start["decision"] == "event_choice"
    assert start["total"] == len(start["candidates"]) * 2
    assert len(ticks) == start["total"]
    assert [t["done"] for t in ticks] == list(range(1, start["total"] + 1))
    cand_keys = {c["key"] for c in start["candidates"]}
    assert all(t["candidate_key"] in cand_keys for t in ticks)


def test_advise_off_decision_returns_error():
    rm = _drive_to(lambda r: r.phase() == "map_choice", "DECISIONADV")
    assert rm is not None, "never reached a map decision"
    result = da.advise(rm, {"rollouts": 1, "sims": 0})
    assert "error" in result
    assert result["candidates"] == []


def test_advise_in_combat_card_select():
    rm = _drive_to(lambda r: r.phase() == "combat" and da.is_advisable(r), "CHOICEHUNT")
    if rm is None:
        pytest.skip("no in-combat card-select reached for this seed/build")
    result = da.advise(rm, {"rollouts": 2, "sims": 0})
    assert "error" not in result
    assert result["decision"] == "card_select"
    cands = result["candidates"]
    assert len(cands) >= 2
    assert sum(c["is_best"] for c in cands) == 1
    assert all(c["key"].startswith("choice:") for c in cands)


def test_advise_shop():
    rm = _drive_to(lambda r: r.phase() == "shop" and da.is_advisable(r), "S2")
    if rm is None:
        pytest.skip("no advisable shop reached for this seed/build")
    result = da.advise(rm, {"rollouts": 2, "sims": 0})
    assert "error" not in result
    assert result["decision"] == "shop"
    cands = result["candidates"]
    assert len(cands) >= 2
    assert sum(c["is_best"] for c in cands) == 1
    assert any(c["key"] == "leave" for c in cands)
    assert all(
        c["key"] == "leave" or c["key"].split(":")[0] in {"card", "relic", "potion"} for c in cands
    )


def _lead_paperweight_choose_screen(max_seeds=40):
    """A run parked on Lead Paperweight's 2-Colorless choose screen, or ``None``.

    The relic is a Neow offer, so this scans string seeds for a run that is
    offered it, takes that option and accepts the relic reward it queues —
    *obtaining* the relic is what fires C# ``AfterObtained`` and opens the
    screen. The option index comes from ``event_options()`` rather than being
    assumed, so a reordered Neow screen makes the helper skip, not lie.
    """
    for i in range(max_seeds):
        rm = _fresh_run(f"LEADPW{i}")
        options = rm.event_options() or []
        idx = next((n for n, (_, relic) in enumerate(options) if relic == "LEAD_PAPERWEIGHT"), None)
        if idx is None:
            continue
        if rm.take_action({"action": "event_choice", "idx": idx}) != "advanced":
            continue
        if {"action": "pick_relic_reward"} not in rm.available_actions():
            continue
        rm.take_action({"action": "pick_relic_reward"})
        if rm.phase() == "card_reward" and rm.card_offer_choose_screen:
            return rm
    return None


def test_advise_lead_paperweight_choose_screen():
    """The screen this advisor used to refuse: "choose 1 of 2 Colorless cards".

    Its phase is ``card_reward``, but the host renders it as a ``card_select``
    (GH #34) and so asks the DECISION advisor for it — which answered "only runs
    on a shop, event, or ... card-select decision" and analyzed nothing, while
    the card advisor's own endpoint 409s on a non-``card_reward`` decision.
    """
    from sts2_hint import display_maps

    rm = _lead_paperweight_choose_screen()
    if rm is None:
        pytest.skip("no Lead Paperweight Neow offer in the scanned seeds")
    assert da.is_advisable(rm) is True
    offered = [display_maps.loc_id(cid) for cid in rm.card_reward_option_ids()]
    result = da.advise(rm, {"rollouts": 2, "sims": 0})

    assert "error" not in result
    assert result["decision"] == "card_select"
    cands = result["candidates"]
    # The 2 rolled Colorless cards plus the screen's own skip (C# canSkip).
    by_key = {c["key"]: c for c in cands}
    assert set(by_key) == {"choice:0", "choice:1", "choice:skip"}
    assert sum(c["is_best"] for c in cands) == 1
    assert all(c["n"] == 2 for c in cands)
    # Each row is labelled with the card it is a verdict on, in the engine's
    # own option order — the chips hang off these indices.
    assert [by_key[f"choice:{i}"]["label"] for i in (0, 1)] == offered
