"""GH #399 Task 5b: deck-mutation option tokens must name the card the engine picks.

Every deck-mutation arm's ``idx`` is a position in an index space the ENGINE
defines, and the encoder has to featurize option ``idx`` with the card that
same ``idx`` resolves to:

- ``remove_deck_card``    -> ``removal_offer_order()``  (``card_removal_options``)
- ``transform_deck_card`` -> ``transform_select_deck_indices()``
- ``enchant_deck_card``   -> ``enchant_select_deck_indices()``
- ``upgrade_deck_card``   -> ``upgrade_select_deck_indices()`` — the engine's
  ``Card::is_upgradable()`` subset (``run_manager.rs:954-968`` offers it,
  ``do_upgrade_deck_card_at`` resolves it), which drops Curse/Status and the
  three Quest cards, **not** merely the ``!upgraded`` cards.

The upgrade arm was the one that derived its own index space from the raw deck
(``_non_upgraded(obs["deck"])``). With Ascender's Bane in the deck — every A5+
run from floor 1 — the two lists differ in length and every option after the
curse names the wrong card. Both encoders did it identically, so Gate A1
(Rust-vs-Python featurization) and G1 (env-vs-arena) are structurally blind to
it; only an identity assertion against the engine can see it. The policy-side
twin of this defect was fixed in ``a807f36a9``.

No combat is played here (the run is force-bound to an event at floor 1), so
these are fast tests, not ``slow`` ones.
"""

from __future__ import annotations

import copy
import os

import pytest

PY = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "python"))

# Appended AFTER Ascender's Bane so the curse sits in the MIDDLE of the deck:
# with it last, `!upgraded` and `is_upgradable()` agree on every offered
# position and the fixture would prove nothing.
_TAIL_CARDS = ("Anger", "Armaments", "Thrash")


@pytest.fixture(autouse=True)
def _syspath(monkeypatch):
    monkeypatch.syspath_prepend(PY)


def _run_at_pending_upgrade():
    """A real run parked on an interactive upgrade select, curse mid-deck.

    ``AromaOfChaos``' "Maintain Control" queues a pick-1 upgrade card_select
    (C# ``FromDeckForUpgrade``) when ``interactive_card_upgrade`` is on, which
    GH #399 turned on for every training/arena cell. Ascension 5 appends
    Ascender's Bane; the three extra cards push it off the end of the deck.
    """
    import sts2_rs

    rm = sts2_rs.RunManager(
        seed=1010,
        character="Ironclad",
        ascension_level=5,
        neow_start=True,
        interactive_card_upgrade=True,
    )
    assert rm.force_event("AromaOfChaos") == "AromaOfChaos"
    rm.take_action({"action": "event_choice", "idx": 1})
    for cid in _TAIL_CARDS:
        rm.force_add_card(cid)
    assert rm.pending_deck_upgrades == 1
    return rm


def _flipped_card(rm, action) -> tuple[int, str]:
    """(master-deck index, card id) the engine upgrades for `action`.

    Resolved by driving a CLONE of the engine, never by re-deriving the index
    space from the helper under test.
    """
    before = rm.master_deck_detailed()
    probe = copy.deepcopy(rm)
    probe.take_action(action)
    after = probe.master_deck_detailed()
    flipped = [i for i, (_, up) in enumerate(after) if up and not before[i][1]]
    assert len(flipped) == 1, f"expected exactly one upgrade, got {flipped}"
    return flipped[0], after[flipped[0]][0]


def test_fixture_index_spaces_actually_differ():
    """Guard the guard: without a non-upgradable card mid-deck this proves nothing."""
    rm = _run_at_pending_upgrade()
    deck = rm.master_deck_detailed()
    non_upgraded = [i for i, (_, up) in enumerate(deck) if not up]
    upgradable = rm.upgrade_select_deck_indices()
    excluded = sorted(set(non_upgraded) - set(upgradable))
    assert excluded, (deck, upgradable)
    # The excluded card must sit BEFORE at least one offered card, or the two
    # index spaces still agree on every offer and this proves nothing.
    assert max(upgradable) > min(excluded), (deck, upgradable, excluded)
    assert len(rm.available_actions()) == len(upgradable)


def test_upgrade_option_tokens_name_the_card_the_engine_upgrades():
    from ppo import encoder, vocab
    from ppo.env import metrics_snapshot, observe

    rm = _run_at_pending_upgrade()
    actions = rm.available_actions()
    assert {a["action"] for a in actions} == {"upgrade_deck_card"}

    obs = observe(rm, rm.phase(), "card-pick", seed=int(rm.seed), ascension=5)
    feat = encoder.featurize_row(obs, actions, metrics_snapshot(rm))
    cards = vocab.cards()

    mismatches = []
    for i, action in enumerate(actions):
        deck_idx, card_id = _flipped_card(rm, action)
        want = cards.index(card_id)
        got = int(feat.act_cat[i][0])
        if got != want:
            mismatches.append((i, deck_idx, card_id, got, want))
    assert not mismatches, (
        "upgrade option tokens featurize a different card than the engine "
        f"upgrades (idx, deck_idx, engine_card, token_cat0, want_cat0): {mismatches}"
    )


def test_upgrade_obs_carries_the_engines_index_space():
    """`observe` must ship the engine's offer map, not leave the encoder to guess."""
    from ppo.env import observe

    rm = _run_at_pending_upgrade()
    obs = observe(rm, rm.phase(), "card-pick", seed=int(rm.seed), ascension=5)
    assert obs["upgrade_select_deck_indices"] == rm.upgrade_select_deck_indices()
