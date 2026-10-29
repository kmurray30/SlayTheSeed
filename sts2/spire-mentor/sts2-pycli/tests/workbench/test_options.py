from workbench.options import normalize_options


def test_combat_has_no_options():
    assert normalize_options({"decision": "combat_play"}) == []


def test_card_reward_lists_cards_and_skip():
    state = {
        "decision": "card_reward",
        "cards": [{"index": 0, "name": "Strike"}, {"index": 1, "name": "Bash"}],
        "can_skip": True,
    }
    opts = normalize_options(state)
    assert opts[0] == {
        "label": "Strike",
        "action": "select_card_reward",
        "args": {"card_index": 0},
        "enabled": True,
    }
    assert opts[-1] == {"label": "Skip", "action": "skip_card_reward", "args": {}, "enabled": True}


def test_card_reward_no_skip_when_not_allowed():
    state = {
        "decision": "card_reward",
        "cards": [{"index": 0, "name": "Strike"}],
        "can_skip": False,
    }
    assert not any(o["action"] == "skip_card_reward" for o in normalize_options(state))


def test_map_select_uses_col_row():
    state = {"decision": "map_select", "choices": [{"col": 2, "row": 1, "type": "Monster"}]}
    o = normalize_options(state)[0]
    assert o["action"] == "select_map_node" and o["args"] == {"col": 2, "row": 1}
    assert "Monster" in o["label"]


def test_rest_site_skips_disabled():
    state = {
        "decision": "rest_site",
        "options": [
            {"index": 0, "option_id": "HEAL", "is_enabled": True},
            {"index": 1, "option_id": "DIG", "is_enabled": False},
        ],
    }
    opts = normalize_options(state)
    assert [o["enabled"] for o in opts] == [True, False]
    assert opts[0] == {
        "label": "HEAL",
        "action": "choose_option",
        "args": {"option_index": 0},
        "enabled": True,
    }


def test_event_marks_locked_disabled():
    state = {
        "decision": "event_choice",
        "options": [
            {"index": 0, "title": "Take the gold", "is_locked": False},
            {"index": 1, "title": "Locked path", "is_locked": True},
        ],
    }
    opts = normalize_options(state)
    assert opts[0]["label"] == "Take the gold" and opts[0]["enabled"] is True
    assert opts[1]["enabled"] is False


def test_shop_lists_stocked_items_then_remove_and_leave():
    state = {
        "decision": "shop",
        "cards": [
            {"index": 0, "name": "Strike", "cost": 50, "is_stocked": True},
            {"index": 1, "name": "Gone", "is_stocked": False},
        ],
        "relics": [{"index": 0, "name": "Akabeko", "cost": 150, "is_stocked": True}],
        "potions": [{"index": 0, "name": "Fire", "cost": 60, "is_stocked": True}],
        "card_removal_cost": 75,
    }
    opts = normalize_options(state)
    actions = [o["action"] for o in opts]
    assert "buy_card" in actions and "buy_relic" in actions and "buy_potion" in actions
    assert actions[-2:] == ["remove_card", "leave_room"]
    assert all("Gone" not in o["label"] for o in opts)


def test_bundle_select():
    state = {"decision": "bundle_select", "bundles": [{"index": 0}, {"index": 1}]}
    opts = normalize_options(state)
    assert opts[1] == {
        "label": "Pack 1",
        "action": "select_bundle",
        "args": {"bundle_index": 1},
        "enabled": True,
    }


def test_card_select_skip_when_min_zero_or_can_skip():
    """Both routes to a legal skip, and they are independent flags.

    ``min_select == 0`` means "nothing is a valid selection"; ``can_skip`` is
    the engine's own ``canSkip`` on a choice that otherwise wants a card —
    Discovery and the Colorless potion are min 1 / max 1 / can_skip true.
    Reading only ``min_select`` dropped their skip from the option list while
    the combat search still scored it (bug wb-31c2d79f995fe1a6).
    """
    base = {
        "decision": "card_select",
        "cards": [{"index": 0, "name": "Wound"}],
        "min_select": 0,
        "max_select": 1,
    }
    assert any(o["action"] == "skip_select" for o in normalize_options(base))
    base["min_select"] = 1
    assert not any(o["action"] == "skip_select" for o in normalize_options(base))
    base["can_skip"] = True
    assert any(o["action"] == "skip_select" for o in normalize_options(base))
    base["can_skip"] = False
    assert not any(o["action"] == "skip_select" for o in normalize_options(base))


def test_unknown_decision_falls_back_to_proceed():
    opts = normalize_options({"decision": "weird_new_screen"})
    assert opts == [{"label": "Continue", "action": "proceed", "args": {}, "enabled": True}]
