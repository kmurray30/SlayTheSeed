"""Tests for combat scenarios."""

import pytest


class TestCombatStructure:
    def test_combat_play_fields(self, game):
        state = game.start(seed="cs1")
        game.skip_neow(state)
        state = game.enter_room("combat", encounter="SHRINKER_BEETLE_WEAK")
        assert state["decision"] == "combat_play"
        for key in (
            "round",
            "energy",
            "max_energy",
            "hand",
            "enemies",
            "player",
            "draw_pile_count",
            "discard_pile_count",
            "player_powers",
        ):
            assert key in state, f"Missing: {key}"

    def test_card_fields(self, game):
        state = game.start(seed="cs2")
        game.skip_neow(state)
        state = game.enter_room("combat", encounter="SHRINKER_BEETLE_WEAK")
        for card in state["hand"]:
            assert isinstance(card["name"], str)
            assert "cost" in card
            assert "can_play" in card
            assert card["type"] in ("Attack", "Skill", "Power", "Status", "Curse")

    def test_enemy_fields(self, game):
        state = game.start(seed="cs3")
        game.skip_neow(state)
        state = game.enter_room("combat", encounter="SHRINKER_BEETLE_WEAK")
        for e in state["enemies"]:
            assert isinstance(e["name"], str)
            assert e["hp"] > 0
            assert e["max_hp"] > 0
            assert "block" in e


class TestPlayCards:
    def test_play_card_costs_energy(self, game):
        state = game.start(seed="cp1")
        game.skip_neow(state)
        state = game.enter_room("combat", encounter="SHRINKER_BEETLE_WEAK")
        energy_before = state["energy"]
        playable = [c for c in state["hand"] if c.get("can_play") and c["cost"] <= energy_before]
        assert playable
        card = playable[0]
        args = {"card_index": card["index"]}
        if card.get("target_type") == "AnyEnemy":
            args["target_index"] = state["enemies"][0]["index"]
        state = game.act("play_card", **args)
        if state["decision"] == "combat_play":
            assert state["energy"] == energy_before - card["cost"]

    def test_play_attack_reduces_enemy_hp(self, game):
        state = game.start(seed="cp2")
        game.skip_neow(state)
        state = game.enter_room("combat", encounter="SHRINKER_BEETLE_WEAK")
        target = state["enemies"][0]
        hp_before = target["hp"]
        attacks = [
            c
            for c in state["hand"]
            if c.get("can_play") and c["type"] == "Attack" and c["cost"] <= state["energy"]
        ]
        if not attacks:
            pytest.skip("No attacks in hand")
        card = attacks[0]
        args = {"card_index": card["index"]}
        if card.get("target_type") == "AnyEnemy":
            args["target_index"] = target["index"]
        state = game.act("play_card", **args)
        if state["decision"] == "combat_play":
            new_target = next((e for e in state["enemies"] if e["index"] == target["index"]), None)
            if new_target and target.get("block", 0) == 0:
                assert new_target["hp"] < hp_before

    def test_play_defend_adds_block(self, game):
        state = game.start(seed="cp3")
        game.skip_neow(state)
        state = game.enter_room("combat", encounter="SHRINKER_BEETLE_WEAK")
        block_before = state["player"].get("block", 0)
        defends = [
            c
            for c in state["hand"]
            if c.get("can_play") and c["type"] == "Skill" and c["cost"] <= state["energy"]
        ]
        if not defends:
            pytest.skip("No skill cards")
        state = game.act("play_card", card_index=defends[0]["index"])
        if state["decision"] == "combat_play":
            assert state["player"].get("block", 0) >= block_before


class TestTurnFlow:
    def test_end_turn_advances_round(self, game):
        state = game.start(seed="tf1")
        game.skip_neow(state)
        state = game.enter_room("combat", encounter="SHRINKER_BEETLE_WEAK")
        rnd = state["round"]
        state = game.act("end_turn")
        if state["decision"] == "combat_play":
            assert state["round"] == rnd + 1

    def test_end_turn_resets_energy(self, game):
        state = game.start(seed="tf2")
        game.skip_neow(state)
        state = game.enter_room("combat", encounter="SHRINKER_BEETLE_WEAK")
        max_e = state["max_energy"]
        state = game.act("end_turn")
        if state["decision"] == "combat_play":
            assert state["energy"] == max_e

    def test_end_turn_draws_new_hand(self, game):
        state = game.start(seed="tf3")
        game.skip_neow(state)
        state = game.enter_room("combat", encounter="SHRINKER_BEETLE_WEAK")
        state = game.act("end_turn")
        if state["decision"] == "combat_play":
            assert len(state["hand"]) > 0


class TestCombatEnd:
    def test_win_combat_leads_to_reward(self, game):
        state = game.start(seed="cw1")
        game.skip_neow(state)
        state = game.enter_room("combat", encounter="SHRINKER_BEETLE_WEAK")
        state = game.auto_play_combat(state)
        assert state["decision"] in ("card_reward", "map_select", "card_select", "bundle_select")

    def test_player_powers_after_enemy_debuff(self, game):
        """Shrinker Beetle applies Shrink debuff to player after its turn."""
        state = game.start(seed="ep1")
        game.skip_neow(state)
        state = game.enter_room("combat", encounter="SHRINKER_BEETLE_WEAK")
        # End turn so beetle acts (applies Shrink to player)
        state = game.act("end_turn")
        if state["decision"] == "combat_play":
            pp = state.get("player_powers") or []
            assert len(pp) > 0, "Expected player debuff after Shrinker Beetle turn"
            for pw in pp:
                assert "name" in pw
                assert "amount" in pw
                assert "description" in pw


class TestCombatEdgeCases:
    def test_exhaust_all_and_end_turn(self, game):
        state = game.start(seed="ce1")
        game.skip_neow(state)
        state = game.enter_room("combat", encounter="SHRINKER_BEETLE_WEAK")
        for _ in range(20):
            if state.get("decision") != "combat_play":
                break
            playable = [
                c for c in state["hand"] if c.get("can_play") and c["cost"] <= state["energy"]
            ]
            if not playable:
                break
            card = playable[0]
            args = {"card_index": card["index"]}
            if card.get("target_type") == "AnyEnemy" and state["enemies"]:
                args["target_index"] = state["enemies"][0]["index"]
            state = game.act("play_card", **args)
        if state.get("decision") == "combat_play":
            state = game.act("end_turn")
            assert state.get("type") != "error"

    def test_many_cards_per_turn(self, game):
        """Play all playable cards in a single turn without errors."""
        state = game.start(seed="inf1")
        game.skip_neow(state)
        state = game.enter_room("combat", encounter="SHRINKER_BEETLE_WEAK")
        plays = 0
        for _ in range(20):
            if state.get("decision") != "combat_play":
                break
            playable = [
                c
                for c in state["hand"]
                if c.get("can_play")
                and c["cost"] <= state["energy"]
                and c["type"] not in ("Status", "Curse")
            ]
            if not playable:
                break
            card = playable[0]
            args = {"card_index": card["index"]}
            if card.get("target_type") == "AnyEnemy" and state["enemies"]:
                args["target_index"] = state["enemies"][0]["index"]
            state = game.act("play_card", **args)
            plays += 1
            assert state.get("type") != "error", (
                f"Error after {plays} plays: {state.get('message')}"
            )
        assert plays >= 2

    def test_infinite_card_loop(self, game):
        """Pommel Strike + Bloodletting infinite loop doesn't crash.

        Pommel Strike (1e): damage + draw 1
        Bloodletting (0e): lose HP + gain 2 energy
        Each cycle: net +1 energy, draws next card. Truly infinite.
        """
        state = game.start(seed="inf2")
        game.skip_neow(state)
        game.set_player(hp=80, max_hp=80, deck=["POMMEL_STRIKE"] * 5 + ["BLOODLETTING"] * 5)
        state = game.enter_room("combat", encounter="SHRINKER_BEETLE_WEAK")

        plays = 0
        for _ in range(60):
            if state.get("decision") != "combat_play":
                break
            hand = state.get("hand", [])
            energy = state.get("energy", 0)
            playable = [
                c
                for c in hand
                if c.get("can_play")
                and c["cost"] <= energy
                and c["type"] not in ("Status", "Curse")
            ]
            if not playable:
                break
            card = playable[0]
            args = {"card_index": card["index"]}
            if card.get("target_type") == "AnyEnemy" and state["enemies"]:
                args["target_index"] = state["enemies"][0]["index"]
            state = game.act("play_card", **args)
            plays += 1
            assert state.get("type") != "error", (
                f"Error after {plays} plays: {state.get('message')}"
            )

        # With Pommel Strike + Bloodletting, should play many cards before enemy dies
        assert plays >= 5, f"Expected infinite loop plays >= 5, got {plays}"

    def test_low_hp_death(self, game):
        """Player with 1 HP should die to any attack."""
        state = game.start(seed="ce2")
        game.skip_neow(state)
        game.set_player(hp=1)
        state = game.enter_room("combat", encounter="SHRINKER_BEETLE_WEAK")
        # Just end turn, beetle will kill us
        state = game.act("end_turn")
        # Might need another turn
        for _ in range(10):
            if state.get("decision") == "game_over":
                break
            if state.get("decision") == "combat_play":
                state = game.act("end_turn")
            else:
                break
        assert state["decision"] == "game_over"
        assert state["victory"] is False


class TestCardSelectSurfacing:
    """Regression: a card play that opens a card-select (Armaments'
    CardSelectCmd.FromHandForUpgrade) must surface the choice as a
    `card_select` decision, not silently auto-resolve. Locks in the
    finding under sts2-rs-fdd9g that the headless harness surfaces
    FromHandForUpgrade correctly for >=2 upgradable hand cards (the
    decomp's `list.Count <= 1 ? auto : prompt` threshold)."""

    def test_armaments_two_plus_upgradable_surfaces_card_select(self, game):
        state = game.start(seed="armsel")
        game.skip_neow(state)
        # Armaments + 4 un-upgraded (upgradable) Pommel Strikes → on play,
        # FromHandForUpgrade has 4 candidates → must prompt.
        game.set_player(
            hp=80,
            max_hp=80,
            deck=["ARMAMENTS"] + ["POMMEL_STRIKE"] * 4,
        )
        game.enter_room("combat", encounter="SHRINKER_BEETLE_WEAK")
        st = game.get_state()
        hand_ids = st["hand_ids"]
        arm_idx = next(i for i, n in enumerate(hand_ids) if "ARMAMENT" in n.upper())
        upgradable = sum(1 for n in hand_ids if "POMMEL_STRIKE" in n.upper())
        assert upgradable >= 2, f"need >=2 upgradable in hand, got {hand_ids}"

        resp = game.play_card(card_index=arm_idx)
        assert resp.get("type") == "decision"
        assert resp.get("decision") == "card_select", (
            f"Armaments with {upgradable} upgradable cards must open a "
            f"card_select, got decision={resp.get('decision')}"
        )
        assert len(resp.get("cards", [])) == upgradable

    def test_armaments_single_upgradable_auto_resolves(self, game):
        state = game.start(seed="armsel1")
        game.skip_neow(state)
        # Armaments + exactly 1 upgradable card → FromHandForUpgrade
        # `list.Count <= 1` auto-resolves; no card_select surfaces.
        game.set_player(hp=80, max_hp=80, deck=["ARMAMENTS", "POMMEL_STRIKE"])
        game.enter_room("combat", encounter="SHRINKER_BEETLE_WEAK")
        st = game.get_state()
        hand_ids = st["hand_ids"]
        upgradable = sum(1 for n in hand_ids if "POMMEL_STRIKE" in n.upper())
        if upgradable != 1:
            pytest.skip(f"need exactly 1 upgradable in hand, got {hand_ids}")
        arm_idx = next(i for i, n in enumerate(hand_ids) if "ARMAMENT" in n.upper())
        resp = game.play_card(card_index=arm_idx)
        assert resp.get("decision") != "card_select", (
            "single upgradable card must auto-resolve (<=1 threshold)"
        )
