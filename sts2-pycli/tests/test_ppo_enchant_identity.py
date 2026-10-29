"""GH #545: an enchant option must say WHICH enchantment it stamps.

`pending_deck_enchants` returned only a COUNT, so Perfect Fit, Sharp, Adroit
and Goopy all featurized as the same decision — and Goopy is a downside that
inverts which card you want. Root-caused as `wb-27d55ac0`.
"""

import sts2_rs


def test_registry_lists_the_enchantments():
    names = sts2_rs.list_registry_names("enchantment")
    assert names[0] == "ROYALLY_APPROVED"
    assert "GOOPY" in names and "PERFECT_FIT" in names
    assert len(names) == 23


def test_pending_enchantment_is_none_on_a_fresh_run():
    rm = sts2_rs.RunManager(seed=7, character="Ironclad", neow_start=True)
    assert rm.pending_enchantment is None


def test_pending_enchantment_is_the_front_of_the_queue_not_the_last():
    """Queue TWO different enchantments and assert the FRONT one wins.

    A prior review flagged that the empty-queue test above would pass
    unchanged even if `pending_enchantment` were hardcoded to `None`, or if
    it read the wrong end of the queue, or if it used `wire_name()`
    ("Imbued") instead of the canonical `python_name()` ("IMBUED"). This
    kills all three at once: Electric Shrymp queues `Imbued` first, Royal
    Stamp queues `RoyallyApproved` second (both via the real
    `try_queue_interactive_enchant` path in `shop_event.rs`, triggered by a
    genuine `force_add_relic` + `after_obtained` dispatch -- not a
    hand-built `PendingDeckEnchant`). If the front/last choice or the
    name-method choice were wrong, this reads "ROYALLY_APPROVED" or
    "Imbued" instead of "IMBUED" and fails.
    """
    rm = sts2_rs.RunManager(
        seed=7, character="Ironclad", neow_start=True, interactive_card_transform=True
    )
    assert rm.pending_deck_enchants == 0

    assert rm.force_add_relic("ELECTRIC_SHRYMP")
    assert rm.pending_deck_enchants == 1
    assert rm.pending_enchantment == "IMBUED"

    assert rm.force_add_relic("ROYAL_STAMP")
    assert rm.pending_deck_enchants == 2
    # The queue grew, but the FRONT entry -- the one actually being
    # decided -- must not have moved.
    assert rm.pending_enchantment == "IMBUED"


def test_the_pending_enchantment_reaches_the_state_token():
    from ppo import schema, vocab
    from ppo.encoder import featurize_row

    obs = {
        "deck": [["STRIKE", False]],
        "enchant_select_deck_indices": [0],
        "pending_enchantment": "GOOPY",
        "max_hp": 80,
    }
    feat = featurize_row(obs, [{"action": "enchant_deck_card", "idx": 0}], {})
    want = vocab.enchantments().index("GOOPY")
    assert int(feat.obs_cat[0][schema.CAT_ENCHANT]) == want
    assert int(feat.act_cat[0][schema.CAT_ENCHANT]) == want


def test_goopy_and_perfect_fit_no_longer_featurize_identically():
    from ppo.encoder import featurize_row

    def enchant(name):
        obs = {
            "deck": [["STRIKE", False]],
            "enchant_select_deck_indices": [0],
            "pending_enchantment": name,
            "max_hp": 80,
        }
        return featurize_row(obs, [{"action": "enchant_deck_card", "idx": 0}], {})

    assert list(enchant("GOOPY").act_cat[0]) != list(enchant("PERFECT_FIT").act_cat[0])
