"""Tests for rest site / campfire."""

import pytest


class TestRestSiteStructure:
    def test_rest_site_fields(self, game):
        state = game.start(seed="rs1")
        game.skip_neow(state)
        state = game.enter_room("rest_site")
        assert state["decision"] == "rest_site"
        for opt in state["options"]:
            assert "index" in opt
            assert "option_id" in opt
            assert "is_enabled" in opt

    def test_has_heal_and_smith(self, game):
        state = game.start(seed="rs2")
        game.skip_neow(state)
        state = game.enter_room("rest_site")
        ids = {o["option_id"] for o in state["options"]}
        assert "HEAL" in ids
        assert "SMITH" in ids


class TestRestSiteActions:
    def test_heal_restores_hp(self, game):
        state = game.start(seed="rsa1")
        game.skip_neow(state)
        game.set_player(hp=30, max_hp=80)
        state = game.enter_room("rest_site")
        heal = next(
            (o for o in state["options"] if o["option_id"] == "HEAL" and o["is_enabled"]), None
        )
        assert heal, "HEAL not available"
        hp_before = state["player"]["hp"]
        state = game.act("choose_option", option_index=heal["index"])
        new_hp = state.get("player", {}).get("hp", hp_before)
        assert new_hp > hp_before

    def test_heal_caps_at_max(self, game):
        state = game.start(seed="rsa2")
        game.skip_neow(state)
        game.set_player(hp=79, max_hp=80)
        state = game.enter_room("rest_site")
        heal = next(
            (o for o in state["options"] if o["option_id"] == "HEAL" and o["is_enabled"]), None
        )
        if not heal:
            pytest.skip("HEAL not available at near-full HP")
        state = game.act("choose_option", option_index=heal["index"])
        # Some starter relics (e.g. Stone Humidifier) raise max HP at rest
        # sites, so the post-heal cap is the resulting max_hp, not 80.
        player = state.get("player", {})
        assert player.get("hp", 0) <= player.get("max_hp", 0)

    def test_smith_triggers_card_select(self, game):
        state = game.start(seed="rsa3")
        game.skip_neow(state)
        state = game.enter_room("rest_site")
        smith = next(
            (o for o in state["options"] if o["option_id"] == "SMITH" and o["is_enabled"]), None
        )
        assert smith, "SMITH not available"
        state = game.act("choose_option", option_index=smith["index"])
        assert state["decision"] == "card_select"
        assert len(state.get("cards", [])) > 0

    def test_smith_actually_upgrades(self, game):
        """After Smith resolves, the chosen card must be upgraded in the deck.

        Guards against the rest-site race where the ChooseLocalOption task
        finishes the upgrade asynchronously after select_cards returns.
        """
        state = game.start(seed="rsa4")
        game.skip_neow(state)
        state = game.enter_room("rest_site")
        smith = next(
            (o for o in state["options"] if o["option_id"] == "SMITH" and o["is_enabled"]), None
        )
        assert smith, "SMITH not available"
        state = game.act("choose_option", option_index=smith["index"])
        assert state["decision"] == "card_select"
        cards = state.get("cards", [])
        assert cards, "no upgrade candidates"
        # Pick the first non-upgraded candidate
        target = next((c for c in cards if not c.get("upgraded")), cards[0])
        target_id = target.get("id") or target.get("name")
        state = game.act("select_cards", indices=str(target["index"]))
        # After the upgrade, the deck must contain at least one upgraded card.
        deck = state.get("player", {}).get("deck") or []
        upgraded = [c for c in deck if c.get("upgraded")]
        assert upgraded, (
            f"No upgraded card in deck after Smith — race? target={target_id} deck_size={len(deck)}"
        )
