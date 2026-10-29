"""A decision the net did not make must not claim the net's name (GH #399).

Both consumers short-circuit `len(actions) == 1` to `probs=[1.0]`. A forced
pick used to be archived and shown as a maximally confident run-policy decision
— `run-policy net · p=100%` in the HUD, `PPO p=1.00` in the JSONL corpus — so
the one pick the net never made read as its most confident. These pin the
honest spelling on both lanes.

The original vehicle for this was `auto_front`, the flags-off engine replica
that answered TO_REMOVE / TO_TRANSFORM / TO_ENCHANT. It is gone (GH #477): all
five champions now train with those surfaces visible, so those prompts are real
net rankings and are asserted as such below. Forced decisions still exist —
any surface can arrive with a single legal option — so the honesty rule they
motivated still needs holding shut, and `source_note` is still the mechanism
for a decision that wants to name a non-net source.
"""

from __future__ import annotations

from typing import Any

import realgame_advisor as ra
import realgame_ppo as rp


def _card(cid: str, index: int, ctype: str, rarity: str, **kw: Any) -> dict[str, Any]:
    """A card shaped like the wire's (see test_realgame_advisor_deck_mutation)."""
    return {
        "id": cid,
        "name": cid.title(),
        "index": index,
        "type": ctype,
        "rarity": rarity,
        "is_curse": ctype == "Curse",
        "upgrade_level": 0,
        "cost": 1,
        **kw,
    }


def _card_select_state(prompt: str) -> dict[str, Any]:
    return {
        "type": "card_select",
        "prompt_key": prompt,
        "cards": [
            _card("STRIKE_IRONCLAD", 0, "Attack", "Basic"),
            _card("DEFEND_IRONCLAD", 1, "Skill", "Basic"),
            _card("CLUMSY", 2, "Curse", "Curse"),
        ],
        "min_select": 1,
        "max_select": 1,
        "run_state": {
            "character": "Ironclad",
            "master_deck": [
                {"id": "STRIKE_IRONCLAD"},
                {"id": "DEFEND_IRONCLAD"},
                {"id": "CLUMSY"},
            ],
        },
    }


def _rest_site_state(labels: list[str]) -> dict[str, Any]:
    return {
        "type": "rest_site",
        "options": [{"label": label, "index": i} for i, label in enumerate(labels)],
        "run_state": {"character": "Ironclad"},
    }


class _StubPolicy:
    """Returns a fixed distribution; asserts it is never asked for a forced pick."""

    def __init__(self, probs: list[float]) -> None:
        self.probs = probs
        self.calls = 0

    def distribution(self, obs: Any, actions: Any, metrics: Any) -> tuple[list[float], float]:
        self.calls += 1
        return list(self.probs), 0.25


class _StubPolicies:
    def __init__(self, policy: Any) -> None:
        self.policy = policy

    def get(self, character: str | None) -> tuple[Any, str | None]:
        return self.policy, None


# --------------------------------------------------------------------------
# The shared helper
# --------------------------------------------------------------------------


def test_a_deck_mutation_prompt_is_now_a_genuine_net_ranking() -> None:
    """The retirement, asserted from the consumer's side (GH #477).

    This is the inverse of the test that used to live here: TO_REMOVE produced
    exactly one action carrying an engine byline, because `auto_front` picked
    the front of the deck. It now ranks every offered card and reads as what it
    is — the net's own decision.
    """
    decision = rp._build_card_select(_card_select_state("TO_REMOVE"), None)

    assert decision is not None
    assert len(decision.actions) == 3, "the net ranks every offer, it does not pick one"
    assert decision.source_note == ""
    note = rp.decision_note(decision, [0.1, 0.2, 0.7])
    assert note == "run-policy net · p=70%"
    assert "forced" not in note
    assert "engine auto-removal replica" not in note


def _shop_state() -> dict[str, Any]:
    """A shop whose removal service is affordable, deck [Strike, Defend, Clumsy]."""
    return {
        "type": "shop",
        "purge_cost": 75,
        "options": [
            {"index": 0, "id": "leave_shop", "action": "leave_shop", "enabled": True},
            {
                "index": 1,
                "id": "remove_card",
                "action": "remove_card",
                "price": 75,
                "enabled": True,
            },
        ],
        "run_state": {
            "character": "Ironclad",
            "gold": 200,
            "master_deck": [
                {"id": "STRIKE_IRONCLAD"},
                {"id": "DEFEND_IRONCLAD"},
                {"id": "CLUMSY"},
            ],
            "potions": [{"slot": 0, "empty": True}],
        },
    }


def test_the_advisor_executes_the_shop_removal_it_already_chose() -> None:
    """The shop picks WHICH card; the follow-up screen only executes it.

    The advisor had no carry at all -- `compute_screen_advice` calls
    `_build_card_select` directly and never routes through
    `pick_noncombat_action_ppo`, where the carry lived. So every live shop
    removal was re-decided on the differently trained `remove_deck_card`
    surface, which disagrees with the shop pick about half the time
    (2026-07-16 meta differ). Zero carries appear across
    `logs/f7-mirror-check18` and `check19`.

    The stub ranks the shop's Clumsy candidate top, then ranks the SCREEN's
    Clumsy last -- so a re-decision cannot accidentally produce the right
    answer and let this pass for the wrong reason.
    """
    session: dict[str, Any] = {}

    # Shop: candidates are [leave, remove:STRIKE, remove:DEFEND, remove:CLUMSY].
    shop = rp._build_shop(_shop_state())
    assert shop is not None
    labels = [lbl for a, lbl in zip(shop.actions, shop.labels) if a["action"] == "shop_remove_card"]
    assert labels == ["remove:STRIKE_IRONCLAD", "remove:DEFEND_IRONCLAD", "remove:CLUMSY"]

    shop_probs = [0.1] * len(shop.actions)
    shop_probs[shop.labels.index("remove:CLUMSY")] = 0.9
    ra.compute_screen_advice(
        _shop_state(), "shop", _StubPolicies(_StubPolicy(shop_probs)), session=session
    )
    assert session["shop_remove_target"] == ("CLUMSY", False)

    # Screen: the net would rank Clumsy LAST. The carry must win anyway.
    advice = ra.compute_screen_advice(
        _card_select_state("TO_REMOVE"),
        "card_select",
        _StubPolicies(_StubPolicy([0.7, 0.2, 0.1])),
        session=session,
    )
    assert advice["best"]["label"] == "Clumsy"
    assert "carried from the shop pick" in advice["note"]
    assert "shop_remove_target" not in session, "the stash must be consumed"


def test_a_shop_target_does_not_survive_an_unrelated_decision() -> None:
    """The stash bridges shop -> card_select and nothing else.

    Without this, a removal that never opened (rejected purchase, re-presented
    shop) leaves a target that fires on whatever deck screen comes next.
    """
    session: dict[str, Any] = {"shop_remove_target": ("CLUMSY", False)}
    ra.compute_screen_advice(
        _rest_site_state(["Rest", "Smith"]),
        "rest_site",
        _StubPolicies(_StubPolicy([0.6, 0.4])),
        session=session,
    )
    assert "shop_remove_target" not in session


def test_a_source_note_still_overrides_the_net_byline() -> None:
    """`source_note` outlived `auto_front` and is still wired.

    Nothing produces one today, which is exactly why it needs a direct test:
    the next surface that must not claim the net's name has to be able to say
    so, and an untested mechanism would rot before then.
    """
    decision = rp.PpoDecision(
        {},
        [{"action": "x", "idx": 0}],
        {},
        [{"action": "choose", "index": 0}],
        ["X"],
        source_note="engine replica (not a net ranking)",
    )
    note = rp.decision_note(decision, [1.0])
    assert "forced (single option)" in note
    assert "engine replica (not a net ranking)" in note
    assert "p=100%" not in note
    assert "p=1.00" not in note


def test_multi_action_decision_keeps_net_attribution() -> None:
    builder = rp._PPO_NONCOMBAT_BUILDERS["rest_site"]
    decision = builder(_rest_site_state(["heal", "smith"]))

    assert decision is not None
    assert len(decision.actions) > 1
    note = rp.decision_note(decision, [0.7, 0.3])
    assert "forced" not in note
    assert note == "run-policy net · p=70%"


def test_a_forced_decision_without_a_source_note_still_says_forced() -> None:
    """A forced pick with no `source_note` says forced and nothing more."""
    builder = rp._PPO_NONCOMBAT_BUILDERS["rest_site"]
    decision = builder(_rest_site_state(["heal"]))

    assert decision is not None and len(decision.actions) == 1
    note = rp.decision_note(decision, [1.0])
    assert "forced (single option)" in note
    assert "engine replica" not in note
    assert "p=100%" not in note


# --------------------------------------------------------------------------
# Consumer 1: the PPO driver's JSONL annotations
# --------------------------------------------------------------------------


def test_driver_annotations_do_not_archive_a_forced_pick_as_a_net_pick() -> None:
    """A single-option screen is forced whatever surface it arrives on.

    Driven by a one-card TO_TRANSFORM rather than the old `auto_front`: with
    three cards this is now a real ranking (see the first test), so the forced
    case has to be produced honestly — by there genuinely being one option.
    """
    state = _card_select_state("TO_TRANSFORM")
    state["cards"] = [_card("CLUMSY", 0, "Curse", "Curse")]
    decision = rp._build_card_select(state, None)
    assert decision is not None and len(decision.actions) == 1

    ann = rp.build_ppo_annotations(decision, [1.0], 0, None)
    assert "forced (single option)" in ann["note"]
    assert "p=1.00" not in ann["note"]
    assert "p=100%" not in ann["note"]
    # The chosen card is still named -- honesty, not less information.
    assert decision.labels[0] in ann["note"]


def test_driver_annotations_keep_the_net_spelling_for_a_real_ranking() -> None:
    builder = rp._PPO_NONCOMBAT_BUILDERS["rest_site"]
    decision = builder(_rest_site_state(["heal", "smith"]))
    assert decision is not None

    ann = rp.build_ppo_annotations(decision, [0.3, 0.7], 1, 0.5)
    assert ann["note"] == "PPO p=0.70 V=0.50 -> smith"
    assert "forced" not in ann["note"]


# --------------------------------------------------------------------------
# Consumer 2: the live advisor's HUD note
# --------------------------------------------------------------------------


def test_advisor_note_for_a_single_option_screen_is_forced_not_confident() -> None:
    policy = _StubPolicy([1.0])
    advice = ra.compute_screen_advice(
        _rest_site_state(["heal"]), "rest_site", _StubPolicies(policy)
    )

    assert "forced (single option)" in advice["note"]
    assert "p=100%" not in advice["note"]
    assert policy.calls == 0, "a forced pick must not be attributed to the net at all"
    assert advice["best"]["label"] == "heal"


def test_advisor_note_for_a_ranked_screen_is_unchanged() -> None:
    advice = ra.compute_screen_advice(
        _rest_site_state(["heal", "smith"]),
        "rest_site",
        _StubPolicies(_StubPolicy([0.4, 0.6])),
    )

    assert advice["note"].startswith("run-policy net · p=60%")
    assert "V=+0.25" in advice["note"]
    assert "forced" not in advice["note"]
