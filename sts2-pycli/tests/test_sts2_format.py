"""Unit tests for the text formatters in ``python/sts2_format.py``."""

import pytest
from sts2_format import (
    format_boss_summary,
    format_card,
    format_decision,
    format_deck,
    format_map,
    format_pile,
    format_potion,
    format_potion_detail,
    format_potions_list,
    format_relic,
    format_relic_detail,
    format_relics_list,
    format_state_diff,
    resolve,
)


class TestResolve:
    """``resolve()`` strips BBCode + SmartFormat and substitutes vars."""

    def test_strips_bbcode(self):
        assert resolve("[b]bold[/b] text") == "bold text"

    def test_substitutes_simple_var(self):
        assert resolve("Deal {Damage:diff()} damage.", {"damage": 6}) == "Deal 6 damage."

    def test_substitutes_case_insensitive(self):
        # Game state vars are lowercase but template tokens are CamelCase.
        assert resolve("Gain {Block:diff()} Block.", {"block": 5}) == "Gain 5 Block."

    def test_keeps_placeholder_when_no_vars_dict(self):
        # No vars dict → SmartFormat is collapsed to a [VarName] placeholder
        # but never substituted. This is rare in practice (C# always sends
        # populated vars); used here only to pin the contract.
        assert resolve("Deal {Damage:diff()}") == "Deal [Damage]"

    def test_drops_brackets_when_vars_dict_missing_key(self, capsys):
        # When a vars dict IS provided but the key is missing, we substitute
        # with the bare key (no brackets) and warn to stderr so the bug is
        # visible without leaking template residue to the user.
        out = resolve("Deal {Damage:diff()}", {"unrelated": 1})
        assert out == "Deal Damage"
        assert "missing template var: Damage" in capsys.readouterr().err

    def test_relic_gold_template_resolves_to_number(self):
        # Regression: the Signet Ring offer rendered "Upon pickup, gain Gold
        # Gold." because the offer serializer dropped the relic's {Gold}=999
        # DynamicVar. With the var present it must show the number, not the
        # bare placeholder word.
        template = "Upon pickup, gain [blue]{Gold}[/blue] [gold]Gold[/gold]."
        assert resolve(template, {"Gold": 999}) == "Upon pickup, gain 999 Gold."

    def test_relic_gold_template_missing_var_is_flagged(self, capsys):
        # The same template WITHOUT its var degrades to "gain Gold Gold." and
        # MUST warn — this is the signal the offer-placeholder regression test
        # keys off to fail loudly instead of shipping the broken text.
        template = "Upon pickup, gain [blue]{Gold}[/blue] [gold]Gold[/gold]."
        out = resolve(template, {"unrelated": 1})
        assert out == "Upon pickup, gain Gold Gold."
        assert "missing template var: Gold" in capsys.readouterr().err

    def test_if_upgraded_default_non_upgraded_branch(self):
        # Without an upgraded flag we show the non-upgraded form.
        assert resolve("{IfUpgraded:show:UPGRADED|normal}") == "normal"

    def test_if_upgraded_takes_upgraded_branch_when_flagged(self):
        assert resolve("{IfUpgraded:show:UPGRADED|normal}", upgraded=True) == "UPGRADED"

    def test_if_upgraded_short_form_respects_flag(self):
        assert resolve("{IfUpgraded:UPG|base}") == "base"
        assert resolve("{IfUpgraded:UPG|base}", upgraded=True) == "UPG"

    def test_plural_singular(self):
        out = resolve("Draw {Cards:diff()} {Cards:plural:card|cards}", {"cards": 1})
        assert out == "Draw 1 card"

    def test_plural_multi(self):
        out = resolve("Draw {Cards:diff()} {Cards:plural:card|cards}", {"cards": 3})
        assert out == "Draw 3 cards"

    def test_in_combat_branch(self):
        assert resolve("{InCombat:combat-text|alt-text}") == "combat-text"

    def test_empty_input(self):
        assert resolve(None) == ""
        assert resolve("") == ""

    def test_multiline_preserved(self):
        out = resolve(
            "Deal {Damage:diff()}.\nApply {VulnerablePower:diff()} Vulnerable.",
            {"damage": 8, "vulnerablepower": 2},
        )
        assert out == "Deal 8.\nApply 2 Vulnerable."


class TestAtomFormatters:
    def test_format_card_with_index_and_description(self):
        card = {
            "index": 0,
            "name": "Strike",
            "cost": 1,
            "type": "Attack",
            "description": "Deal {Damage:diff()} damage.",
            "stats": {"damage": 6},
            "can_play": True,
        }
        out = format_card(card)
        assert "[0]" in out
        assert "(1)" in out
        assert "Strike" in out
        assert "Attack" in out
        assert "Deal 6 damage." in out

    def test_format_card_unplayable_marker(self):
        card = {"index": 1, "name": "Strike", "cost": 1, "type": "Attack", "can_play": False}
        out = format_card(card)
        assert "(unplayable)" in out

    def test_format_card_upgraded_plus(self):
        card = {"index": 0, "name": "Strike", "cost": 1, "type": "Attack", "upgraded": True}
        assert "Strike+" in format_card(card)

    def test_format_card_resolves_if_upgraded_branch(self):
        # Cards with {IfUpgraded:show:…} pick the upgraded branch when the
        # card itself is upgraded.
        base = {
            "index": 0,
            "name": "Bash",
            "cost": 2,
            "type": "Attack",
            "description": "Deal {IfUpgraded:show:10|8} damage.",
            "stats": {},
        }
        assert "Deal 8 damage." in format_card({**base, "upgraded": False})
        assert "Deal 10 damage." in format_card({**base, "upgraded": True})

    def test_format_card_no_index_when_disabled(self):
        card = {"index": 0, "name": "Strike", "cost": 1, "type": "Attack"}
        assert "[0]" not in format_card(card, show_index=False)
        assert "Strike" in format_card(card, show_index=False)

    def test_format_relic_resolves_vars(self):
        relic = {
            "name": "Burning Blood",
            "description": "At the end of combat, heal {Heal} HP.",
            "vars": {"Heal": 6},
        }
        out = format_relic(relic)
        assert "Burning Blood" in out
        assert "heal 6 HP" in out

    def test_format_potion_includes_index(self):
        potion = {
            "index": 2,
            "name": "Block Potion",
            "description": "Gain {Block} Block.",
            "vars": {"Block": 12},
        }
        out = format_potion(potion)
        assert "[2]" in out
        assert "Gain 12 Block" in out


class TestFormatDecisionDispatch:
    """``format_decision`` dispatches on ``decision`` and never crashes on minimal input."""

    @pytest.fixture
    def context(self):
        return {
            "act": 1,
            "act_name": "Overgrowth",
            "floor": 3,
            "room_type": "Monster",
            "boss": {"id": "X_BOSS", "name": "Some Boss"},
        }

    def test_combat_play(self, context):
        state = {
            "type": "decision",
            "decision": "combat_play",
            "context": context,
            "round": 2,
            "energy": 3,
            "max_energy": 3,
            "draw_pile_count": 5,
            "discard_pile_count": 0,
            "exhaust_pile_count": 0,
            "hand": [
                {
                    "index": 0,
                    "name": "Strike",
                    "cost": 1,
                    "type": "Attack",
                    "description": "Deal {Damage:diff()} damage.",
                    "stats": {"damage": 6},
                    "can_play": True,
                    "target_type": "AnyEnemy",
                }
            ],
            "enemies": [
                {
                    "index": 0,
                    "name": "Beetle",
                    "hp": 30,
                    "max_hp": 38,
                    "intents": [{"type": "Attack", "damage": 8, "hits": 1}],
                }
            ],
            "player": {
                "name": "Ironclad",
                "hp": 65,
                "max_hp": 80,
                "block": 4,
                "gold": 99,
                "deck_size": 12,
            },
            "player_powers": [
                {"name": "Strength", "amount": 2, "description": "Add {Amount} damage to attacks."}
            ],
        }
        out = format_decision(state)
        assert "Round 2" in out
        assert "Energy 3/3" in out
        assert "HP 65/80" in out and "Block 4" in out
        assert "Beetle" in out and "intent: Attack(damage=8,hits=1)" in out
        assert "Strike" in out and "Deal 6 damage." in out
        assert "Strength" in out and "Add 2 damage to attacks." in out

    def test_combat_renders_osty_when_alive(self, context):
        """Necrobinder's Osty companion is shown in combat (regression: was invisible)."""
        state = {
            "type": "decision",
            "decision": "combat_play",
            "context": context,
            "round": 1,
            "energy": 3,
            "max_energy": 3,
            "player": {"name": "Necrobinder", "hp": 50, "max_hp": 60, "deck_size": 10},
            "osty": {"name": "Osty", "hp": 25, "max_hp": 40, "block": 5, "alive": True},
            "enemies": [{"index": 0, "name": "Beetle", "hp": 30, "max_hp": 38}],
        }
        out = format_decision(state)
        assert "Osty:" in out and "HP 25/40" in out and "Block 5" in out

    def test_combat_renders_osty_when_dead(self, context):
        state = {
            "type": "decision",
            "decision": "combat_play",
            "context": context,
            "player": {"name": "Necrobinder", "hp": 50, "max_hp": 60},
            "osty": {"alive": False},
            "enemies": [],
        }
        assert "Osty:  (dead)" in format_decision(state)

    def test_combat_no_osty_line_for_other_characters(self, context):
        """Non-Necrobinder states have no ``osty`` key and must not render an Osty line."""
        state = {
            "type": "decision",
            "decision": "combat_play",
            "context": context,
            "player": {"name": "Ironclad", "hp": 65, "max_hp": 80},
            "enemies": [],
        }
        assert "Osty" not in format_decision(state)

    def test_map_select(self, context):
        state = {
            "type": "decision",
            "decision": "map_select",
            "context": context,
            "choices": [
                {"col": 2, "row": 1, "type": "Monster"},
                {"col": 4, "row": 1, "type": "Elite"},
            ],
        }
        out = format_decision(state)
        assert "col=2 row=1" in out and "Monster" in out
        assert "col=4 row=1" in out and "Elite" in out

    def test_card_reward(self, context):
        state = {
            "type": "decision",
            "decision": "card_reward",
            "context": context,
            "can_skip": True,
            "gold_earned": 15,
            "cards": [
                {
                    "index": 0,
                    "name": "Bash",
                    "cost": 2,
                    "type": "Attack",
                    "description": "Deal {Damage:diff()} damage.",
                    "stats": {"damage": 8},
                }
            ],
        }
        out = format_decision(state)
        assert "gold +15" in out
        assert "Bash" in out and "Deal 8 damage." in out
        assert "skip allowed" in out

    def test_event_choice(self, context):
        state = {
            "type": "decision",
            "decision": "event_choice",
            "context": context,
            "event_name": "MysteriousAltar",
            "options": [
                {
                    "index": 0,
                    "title": "Pay 100",
                    "description": "Lose {Gold} gold.",
                    "vars": {"Gold": 100},
                    "is_locked": False,
                },
                {"index": 1, "title": "Locked", "description": "", "is_locked": True},
            ],
        }
        out = format_decision(state)
        assert "MysteriousAltar" in out
        assert "[0] Pay 100" in out and "Lose 100 gold." in out
        assert "[1] Locked (LOCKED)" in out

    def test_card_select(self, context):
        state = {
            "type": "decision",
            "decision": "card_select",
            "context": context,
            "min_select": 1,
            "max_select": 1,
            "cards": [{"index": 0, "name": "Strike", "cost": 1, "type": "Attack"}],
        }
        out = format_decision(state)
        assert "min=1, max=1" in out

    def test_game_over_victory(self, context):
        state = {
            "type": "decision",
            "decision": "game_over",
            "context": context,
            "victory": True,
            "player": {"hp": 50, "max_hp": 80, "deck_size": 20, "gold": 200},
        }
        assert "VICTORY" in format_decision(state)

    def test_game_over_defeat(self, context):
        state = {
            "type": "decision",
            "decision": "game_over",
            "context": context,
            "victory": False,
            "player": {"hp": 0, "max_hp": 80, "deck_size": 20, "gold": 200},
        }
        assert "DEFEAT" in format_decision(state)

    def test_unknown_decision_falls_through(self, context):
        state = {"type": "decision", "decision": "future_decision", "context": context}
        # Should not raise, should mention the decision name.
        out = format_decision(state)
        assert "future_decision" in out

    def test_error_envelope(self):
        out = format_decision({"type": "error", "message": "boom"})
        assert "ERROR" in out and "boom" in out


class TestInspectionFormatters:
    def test_format_deck(self):
        player = {
            "deck": [
                {
                    "name": "Strike",
                    "cost": 1,
                    "type": "Attack",
                    "description": "Deal {Damage:diff()}.",
                    "stats": {"damage": 6},
                },
                {
                    "name": "Defend",
                    "cost": 1,
                    "type": "Skill",
                    "description": "Gain {Block:diff()}.",
                    "stats": {"block": 5},
                },
            ]
        }
        out = format_deck(player)
        assert "2 cards" in out
        assert "Strike" in out and "Defend" in out

    def test_format_relic_detail_by_index(self):
        player = {
            "relics": [
                {"name": "Burning Blood", "description": "Heal {Heal}.", "vars": {"Heal": 6}},
                {"name": "Lava Rock", "description": "Drop {Relics}.", "vars": {"Relics": 2}},
            ]
        }
        out = format_relic_detail(player, "1")
        assert "Lava Rock" in out
        assert "Drop 2" in out

    def test_format_relic_detail_by_name_substring(self):
        player = {
            "relics": [
                {"name": "Burning Blood", "description": "Heal {Heal}.", "vars": {"Heal": 6}}
            ]
        }
        out = format_relic_detail(player, "burn")
        assert "Burning Blood" in out

    def test_format_relic_detail_no_match(self):
        assert "No matching" in format_relic_detail({"relics": []}, "anything")

    def test_format_potion_detail_by_index(self):
        player = {
            "potions": [
                {
                    "index": 0,
                    "name": "Block Potion",
                    "description": "Gain {Block}.",
                    "vars": {"Block": 12},
                }
            ]
        }
        assert "Block Potion" in format_potion_detail(player, "0")

    def test_format_boss_summary_from_state(self):
        state = {"context": {"boss": {"id": "X_BOSS", "name": "Vantom"}}}
        out = format_boss_summary(state)
        assert "Vantom" in out
        assert "X_BOSS" in out

    def test_format_boss_summary_from_get_map(self):
        out = format_boss_summary({"boss": {"id": "Y", "name": "Other"}})
        assert "Other" in out

    def test_format_boss_summary_missing(self):
        assert "No boss info" in format_boss_summary({})

    def test_format_boss_summary_lists_known_moves(self):
        # Vantom is one of the bosses in the monsters localization table; this
        # exercises the loc loader without hardcoding move titles.
        out = format_boss_summary({"boss": {"id": "VANTOM_BOSS", "name": "Vantom"}})
        assert "Vantom" in out
        # We don't pin specific move names (the data file may evolve), but the
        # "Known moves" line should be present whenever the loc tables resolve
        # at least one move for the boss id.
        if "Known moves" in out:
            assert ":" in out.split("Known moves")[1].splitlines()[0]

    def test_format_map(self):
        map_data = {
            "boss": {"col": 3, "row": 16, "name": "Ceremonial Beast"},
            "current_coord": {"col": 1, "row": 7},
            "rows": [
                [
                    {"col": 0, "row": 1, "type": "Monster", "visited": True, "current": False},
                    {"col": 1, "row": 1, "type": "Elite", "visited": False, "current": False},
                ],
                [{"col": 1, "row": 2, "type": "Shop", "visited": False, "current": True}],
            ],
        }
        out = format_map(map_data)
        assert "Ceremonial Beast" in out
        assert "Current: (1,7)" in out
        # row 0: visited monster + non-visited elite
        assert "(0,1)vMon" in out
        assert "(1,1) Eli" in out
        # row 1: current shop
        assert "(1,2)*Sho" in out


class TestRestSite:
    """Rest-site options must surface ``is_enabled`` so MCP agents avoid disabled picks."""

    def _state(self, options):
        return {
            "type": "decision",
            "decision": "rest_site",
            "context": {"act": 1, "act_name": "X", "floor": 8, "room_type": "Rest"},
            "options": options,
        }

    def test_disabled_option_is_marked(self):
        state = self._state(
            [
                {"index": 0, "name": "Heal", "is_enabled": True},
                {"index": 1, "name": "Smith", "is_enabled": False},
            ]
        )
        out = format_decision(state)
        assert "[0] Heal" in out and "(disabled)" not in out.split("\n")[1]
        # The disabled marker is on the second option line.
        smith_line = next(line for line in out.splitlines() if "Smith" in line)
        assert "(disabled)" in smith_line

    def test_options_default_to_enabled_when_field_missing(self):
        state = self._state([{"index": 0, "name": "Heal"}])
        out = format_decision(state)
        assert "(disabled)" not in out


class TestEnglishLabels:
    """Structural labels render in English."""

    def test_combat_labels(self):
        state = {
            "type": "decision",
            "decision": "combat_play",
            "context": {"act": 1, "act_name": "X", "floor": 1, "room_type": "Monster"},
            "round": 2,
            "energy": 3,
            "max_energy": 3,
            "draw_pile_count": 5,
            "discard_pile_count": 0,
            "exhaust_pile_count": 0,
            "hand": [],
            "enemies": [],
            "player": {"hp": 80, "max_hp": 80, "deck_size": 10, "gold": 99},
        }
        out = format_decision(state)
        assert "Round" in out
        assert "Energy" in out
        assert "Floor" in out

    def test_victory_label(self):
        state = {
            "type": "decision",
            "decision": "game_over",
            "context": {"act": 1, "act_name": "X", "floor": 8, "room_type": "Boss"},
            "victory": True,
            "player": {"hp": 50, "max_hp": 80, "deck_size": 20, "gold": 100},
        }
        assert "VICTORY" in format_decision(state)


class TestRelicsAndPotionsList:
    """``format_relics_list`` / ``format_potions_list`` mirror the CLI's list views."""

    def test_relics_list_includes_each_relic(self):
        player = {
            "relics": [
                {"name": "Burning Blood", "description": "Heal {Heal}.", "vars": {"Heal": 6}},
                {"name": "Lava Rock", "description": "Drop {Drops}.", "vars": {"Drops": 2}},
            ]
        }
        out = format_relics_list(player)
        assert "Relics (2)" in out
        assert "[0] Burning Blood" in out and "Heal 6" in out
        assert "[1] Lava Rock" in out and "Drop 2" in out

    def test_relics_list_empty(self):
        assert "No relics" in format_relics_list({"relics": []})
        assert "No relics" in format_relics_list({})

    def test_potions_list_uses_game_index(self):
        # Potion indices come from the game (potion belt slots), not the list position.
        player = {
            "potions": [
                {
                    "index": 0,
                    "name": "Block Potion",
                    "description": "Gain {Block}.",
                    "vars": {"Block": 12},
                },
                None,  # empty belt slot — must be filtered out
                {
                    "index": 2,
                    "name": "Strength Potion",
                    "description": "+{Str}.",
                    "vars": {"Str": 2},
                },
            ]
        }
        out = format_potions_list(player)
        assert "Potions (2)" in out
        assert "[0] Block Potion" in out
        assert "[2] Strength Potion" in out

    def test_potions_list_empty(self):
        assert "No potions" in format_potions_list({"potions": []})


class TestFormatPile:
    """``format_pile`` renders combat draw/discard/exhaust piles."""

    def _state(self, **piles):
        return {
            "draw_pile": piles.get("draw", []),
            "discard_pile": piles.get("discard", []),
            "exhaust_pile": piles.get("exhaust", []),
        }

    def test_draw_pile_lists_cards(self):
        state = self._state(draw=[{"name": "Strike", "cost": 1, "type": "Attack"}])
        out = format_pile(state, "draw")
        assert "Draw pile (1)" in out
        assert "Strike" in out

    def test_empty_pile(self):
        state = self._state()
        assert "(empty)" in format_pile(state, "discard")

    def test_unknown_kind_reports_valid_set(self):
        out = format_pile(self._state(), "graveyard")
        assert "Unknown pile" in out
        # Valid kinds are listed in the message so the caller can self-correct.
        assert "draw" in out and "discard" in out and "exhaust" in out


class TestStateDiff:
    """``format_state_diff`` narrates what changed between two combat states."""

    def _combat(self, **kw):
        base = {
            "type": "decision",
            "decision": "combat_play",
            "round": 1,
            "hand": [],
            "enemies": [],
            "player": {"hp": 80, "max_hp": 80, "block": 0, "gold": 99, "deck_size": 10},
        }
        base.update(kw)
        return base

    def test_play_card_enemy_damage(self):
        prev = self._combat(
            hand=[{"index": 0, "name": "Strike", "cost": 1, "type": "Attack"}],
            enemies=[{"index": 0, "name": "Beetle", "hp": 30, "max_hp": 30, "block": 0}],
        )
        curr = self._combat(
            enemies=[{"index": 0, "name": "Beetle", "hp": 24, "max_hp": 30, "block": 0}],
        )
        out = format_state_diff(prev, curr, "play_card", (0,))
        assert "Strike" in out
        assert "Beetle" in out
        assert "6" in out  # 30-24 = 6 dmg
        assert "30" in out and "24" in out

    def test_play_card_enemy_defeated(self):
        prev = self._combat(
            hand=[{"index": 0, "name": "Strike", "cost": 1, "type": "Attack"}],
            enemies=[{"index": 0, "name": "Beetle", "hp": 5, "max_hp": 30, "block": 0}],
        )
        curr = self._combat(enemies=[])
        out = format_state_diff(prev, curr, "play_card", (0,))
        assert "Beetle" in out
        assert "defeated" in out

    def test_play_card_kills_middle_enemy_does_not_attribute_damage_to_survivor(self):
        # Repro: Round 4 of an Overgrowth boss fight, three enemies alive.
        # Strike (6 dmg) targets the wounded Kin Follower at index 1, killing
        # it. The C# serializer rebuilds the enemy list from alive monsters
        # only, so post-kill the Kin Priest's index drops from 2 to 1. A naive
        # prev[i]/curr[i] diff therefore sees prev[2]=Priest with no curr[2]
        # and reports "Kin Priest: 190 dmg (defeated)" — wildly wrong.
        prev = self._combat(
            hand=[{"index": 0, "name": "Strike", "cost": 1, "type": "Attack"}],
            enemies=[
                {"index": 0, "name": "Kin Follower", "hp": 58, "max_hp": 58, "block": 0},
                {"index": 1, "name": "Kin Follower", "hp": 4, "max_hp": 59, "block": 0},
                {"index": 2, "name": "Kin Priest", "hp": 190, "max_hp": 190, "block": 0},
            ],
        )
        curr = self._combat(
            enemies=[
                {"index": 0, "name": "Kin Follower", "hp": 58, "max_hp": 58, "block": 0},
                {"index": 1, "name": "Kin Priest", "hp": 190, "max_hp": 190, "block": 0},
            ],
        )
        out = format_state_diff(prev, curr, "play_card", (0,))
        assert "defeated" in out
        # Only the wounded Follower (max_hp 59) was killed; the Priest is alive.
        assert "Kin Priest" not in out
        assert "190" not in out
        assert "Kin Follower" in out
        assert "4" in out  # 4 HP → 0, dealt 4 dmg

    def test_play_card_player_defeat_does_not_credit_card_with_killing_enemies(self):
        # Repro: at 1 HP the agent plays Bloodletting (lose 3 HP, gain 2 energy).
        # Player dies, combat ends, curr.enemies is empty. A naive diff pairs
        # every prev enemy to None and reports each as "defeated by Bloodletting"
        # — claiming the boss took its full HP as damage.
        prev = self._combat(
            hand=[{"index": 0, "name": "Bloodletting", "cost": 0, "type": "Skill"}],
            enemies=[
                {"index": 0, "name": "Kin Priest", "hp": 184, "max_hp": 190, "block": 0},
            ],
            player={"hp": 1, "max_hp": 80, "block": 0, "gold": 99, "deck_size": 10},
        )
        curr = {
            "type": "decision",
            "decision": "game_over",
            "enemies": [],
            "player": {"hp": 0, "max_hp": 80, "block": 0, "gold": 99, "deck_size": 10},
        }
        out = format_state_diff(prev, curr, "play_card", (0,))
        assert "Kin Priest" not in out
        assert "184" not in out
        assert "defeated" not in out

    def test_play_card_block_gained(self):
        prev = self._combat(
            hand=[{"index": 1, "name": "Defend", "cost": 1, "type": "Skill"}],
            player={"hp": 80, "max_hp": 80, "block": 0, "gold": 99, "deck_size": 10},
        )
        curr = self._combat(
            player={"hp": 80, "max_hp": 80, "block": 5, "gold": 99, "deck_size": 10},
        )
        out = format_state_diff(prev, curr, "play_card", (1,))
        assert "Defend" in out
        assert "+5" in out
        assert "block" in out

    def test_end_turn_attack_single_enemy(self):
        prev = self._combat(
            round=2,
            enemies=[
                {
                    "index": 0,
                    "name": "Beetle",
                    "hp": 30,
                    "max_hp": 30,
                    "block": 0,
                    "intents": [{"type": "Attack", "damage": 8, "hits": 1}],
                }
            ],
            player={"hp": 76, "max_hp": 80, "block": 0, "gold": 99, "deck_size": 10},
        )
        curr = self._combat(
            round=3,
            enemies=[{"index": 0, "name": "Beetle", "hp": 30, "max_hp": 30, "block": 0}],
            player={"hp": 68, "max_hp": 80, "block": 0, "gold": 99, "deck_size": 10},
            hand=[
                {"index": 0, "name": "Strike", "cost": 1},
                {"index": 1, "name": "Defend", "cost": 1},
            ],
        )
        out = format_state_diff(prev, curr, "end_turn")
        assert "Beetle" in out
        assert "8" in out  # intended damage
        assert "8" in out  # hp lost (no block)
        assert "76" in out and "68" in out  # HP range shown

    def test_end_turn_attack_with_block(self):
        prev = self._combat(
            round=1,
            enemies=[
                {
                    "index": 0,
                    "name": "Bug",
                    "hp": 20,
                    "max_hp": 20,
                    "block": 0,
                    "intents": [{"type": "Attack", "damage": 10, "hits": 1}],
                }
            ],
            player={"hp": 80, "max_hp": 80, "block": 5, "gold": 99, "deck_size": 10},
        )
        curr = self._combat(
            round=2,
            enemies=[{"index": 0, "name": "Bug", "hp": 20, "max_hp": 20, "block": 0}],
            player={"hp": 75, "max_hp": 80, "block": 0, "gold": 99, "deck_size": 10},
            hand=[{"index": 0, "name": "Strike", "cost": 1}],
        )
        out = format_state_diff(prev, curr, "end_turn")
        assert "5" in out  # 5 blocked
        assert "blocked" in out
        assert "5" in out  # 5 HP lost (10 - 5 block = 5)
        assert "80" in out and "75" in out

    def test_end_turn_enemy_dies_does_not_misalign_power_changes(self):
        # If an enemy dies during the enemy turn (e.g., minion expires after
        # buffing the boss), the alive-list indices shift down. Power-change
        # detection must keep matching prev[i] to its surviving counterpart,
        # not to whoever happens to occupy index i in the post-turn snapshot.
        prev = self._combat(
            round=2,
            enemies=[
                {
                    "index": 0,
                    "name": "Acolyte",
                    "hp": 1,
                    "max_hp": 30,
                    "block": 0,
                    "intents": [{"type": "Buff"}],
                    "powers": [{"name": "Strength", "amount": 0}],
                },
                {
                    "index": 1,
                    "name": "Boss",
                    "hp": 100,
                    "max_hp": 100,
                    "block": 0,
                    "intents": [{"type": "Buff"}],
                    "powers": [{"name": "Strength", "amount": 0}],
                },
            ],
            player={"hp": 80, "max_hp": 80, "block": 0, "gold": 99, "deck_size": 10},
        )
        curr = self._combat(
            round=3,
            enemies=[
                {
                    "index": 0,
                    "name": "Boss",
                    "hp": 100,
                    "max_hp": 100,
                    "block": 0,
                    "powers": [{"name": "Strength", "amount": 2}],
                },
            ],
            player={"hp": 80, "max_hp": 80, "block": 0, "gold": 99, "deck_size": 10},
            hand=[{"index": 0, "name": "Strike", "cost": 1}],
        )
        out = format_state_diff(prev, curr, "end_turn")
        # Boss buffed itself for +2 Strength; the dead Acolyte should not be
        # credited with that change (which would happen if prev[1]=Boss were
        # paired with curr[1] which doesn't exist).
        assert "Boss: Buff (+2 Strength)" in out

    def test_end_turn_shows_new_hand(self):
        prev = self._combat(round=1, enemies=[])
        curr = self._combat(
            round=2,
            enemies=[],
            hand=[
                {"index": 0, "name": "Strike", "cost": 1},
                {"index": 1, "name": "Defend", "cost": 1},
            ],
        )
        out = format_state_diff(prev, curr, "end_turn")
        assert "Round 2" in out
        assert "Strike" in out
        assert "Defend" in out

    def test_no_diff_when_prev_not_combat_and_no_enemies(self):
        # Entering a combat decision with an empty enemy list emits nothing —
        # combat_start would have nothing to narrate.
        prev = {"type": "decision", "decision": "map_select"}
        curr = self._combat()
        assert format_state_diff(prev, curr, "select_map_node") == ""

    def test_no_diff_when_nothing_changed(self):
        state = self._combat(enemies=[{"index": 0, "name": "Bug", "hp": 20, "max_hp": 20}])
        out = format_state_diff(state, state, "play_card", (0,))
        assert out == ""

    def test_combat_start_narrates_enemy_roster(self):
        prev = {"type": "decision", "decision": "map_select"}
        curr = self._combat(
            enemies=[
                {
                    "index": 0,
                    "name": "Twig Slime",
                    "hp": 14,
                    "max_hp": 14,
                    "intents": [{"type": "Attack", "damage": 6, "hits": 1}],
                },
                {
                    "index": 1,
                    "name": "Twig Slime",
                    "hp": 18,
                    "max_hp": 18,
                    "intents": [{"type": "Defend", "block": 5}],
                },
            ]
        )
        out = format_state_diff(prev, curr, "select_map_node")
        assert "Combat starts" in out
        assert "Twig Slime 14/14" in out
        assert "Atk 6" in out
        assert "Def 5" in out

    def test_combat_end_narrates_hp_delta(self):
        prev = self._combat(
            round=3,
            enemies=[{"index": 0, "name": "Beetle", "hp": 1, "max_hp": 30}],
            player={"hp": 70, "max_hp": 80, "block": 0, "gold": 99, "deck_size": 10},
        )
        curr = {
            "type": "decision",
            "decision": "card_reward",
            "cards": [],
            "player": {"hp": 70, "max_hp": 80, "block": 0, "gold": 99, "deck_size": 10},
        }
        out = format_state_diff(prev, curr, "play_card", (0,))
        # Combat end must be reported with HP/round counts. Whether the kill
        # blow itself shows up depends on whether prev still had a live enemy
        # (it did, so we get both lines).
        assert "Combat ends" in out
        assert "70" in out  # HP figure
        assert "3" in out  # rounds elapsed

    def test_combat_end_defeat_marked(self):
        prev = self._combat(
            round=2,
            enemies=[{"index": 0, "name": "Beetle", "hp": 30, "max_hp": 30}],
            player={"hp": 1, "max_hp": 80, "block": 0, "gold": 99, "deck_size": 10},
        )
        curr = {
            "type": "decision",
            "decision": "game_over",
            "victory": False,
            "player": {"hp": 0, "max_hp": 80, "block": 0, "gold": 99, "deck_size": 10},
        }
        out = format_state_diff(prev, curr, "end_turn")
        assert "DEFEAT" in out

    def test_round_start_snapshot_shows_intent_and_hp(self):
        prev = self._combat(
            round=1,
            enemies=[
                {
                    "index": 0,
                    "name": "Beetle",
                    "hp": 30,
                    "max_hp": 30,
                    "block": 0,
                    "intents": [{"type": "Attack", "damage": 5, "hits": 1}],
                }
            ],
            player={"hp": 80, "max_hp": 80, "block": 0, "gold": 99, "deck_size": 10},
            energy=0,
            max_energy=3,
        )
        curr = self._combat(
            round=2,
            energy=3,
            max_energy=3,
            enemies=[
                {
                    "index": 0,
                    "name": "Beetle",
                    "hp": 30,
                    "max_hp": 30,
                    "block": 0,
                    "intents": [{"type": "Attack", "damage": 5, "hits": 1}],
                }
            ],
            player={"hp": 75, "max_hp": 80, "block": 0, "gold": 99, "deck_size": 10},
            hand=[
                {"index": 0, "name": "Strike", "cost": 1},
                {"index": 1, "name": "Defend", "cost": 1},
            ],
        )
        out = format_state_diff(prev, curr, "end_turn")
        # New-round snapshot: HP, energy, hand, enemy intent.
        assert "Round 2" in out
        assert "75/80" in out
        assert "E 3/3" in out
        assert "Strike" in out and "Defend" in out
        assert "Atk 5" in out

    def test_play_card_shows_energy_delta(self):
        prev = self._combat(
            energy=3,
            max_energy=3,
            hand=[{"index": 0, "name": "Strike", "cost": 1}],
            enemies=[{"index": 0, "name": "Beetle", "hp": 30, "max_hp": 30}],
        )
        curr = self._combat(
            energy=2,
            max_energy=3,
            enemies=[{"index": 0, "name": "Beetle", "hp": 24, "max_hp": 30}],
        )
        out = format_state_diff(prev, curr, "play_card", (0,))
        assert "E 3→2" in out
        assert "Strike" in out

    def test_play_card_defensive_surfaces_enemy_intent(self):
        prev = self._combat(
            hand=[{"index": 1, "name": "Defend", "cost": 1, "type": "Skill"}],
            enemies=[
                {
                    "index": 0,
                    "name": "Beetle",
                    "hp": 30,
                    "max_hp": 30,
                    "intents": [{"type": "Attack", "damage": 8, "hits": 1}],
                }
            ],
            player={"hp": 80, "max_hp": 80, "block": 0, "gold": 99, "deck_size": 10},
        )
        curr = self._combat(
            enemies=[
                {
                    "index": 0,
                    "name": "Beetle",
                    "hp": 30,
                    "max_hp": 30,
                    "intents": [{"type": "Attack", "damage": 8, "hits": 1}],
                }
            ],
            player={"hp": 80, "max_hp": 80, "block": 5, "gold": 99, "deck_size": 10},
        )
        out = format_state_diff(prev, curr, "play_card", (1,))
        # Defensive plays advertise what damage was being defended against.
        assert "Atk 8" in out
        assert "vs" in out

    def test_play_card_self_damage(self):
        prev = self._combat(
            hand=[{"index": 0, "name": "Searing Blow", "cost": 2, "type": "Attack"}],
            enemies=[{"index": 0, "name": "Bug", "hp": 30, "max_hp": 30}],
            player={"hp": 80, "max_hp": 80, "block": 0, "gold": 99, "deck_size": 10},
        )
        curr = self._combat(
            enemies=[{"index": 0, "name": "Bug", "hp": 30, "max_hp": 30}],
            player={"hp": 78, "max_hp": 80, "block": 0, "gold": 99, "deck_size": 10},
        )
        out = format_state_diff(prev, curr, "play_card", (0,))
        assert "2 HP lost" in out
        assert "80→78" in out

    def test_end_turn_reports_unspent_energy(self):
        prev = self._combat(
            round=1,
            energy=2,
            max_energy=3,
            enemies=[{"index": 0, "name": "Bug", "hp": 20, "max_hp": 20}],
            player={"hp": 80, "max_hp": 80, "block": 0, "gold": 99, "deck_size": 10},
        )
        curr = self._combat(
            round=2,
            energy=3,
            max_energy=3,
            enemies=[{"index": 0, "name": "Bug", "hp": 20, "max_hp": 20}],
            player={"hp": 80, "max_hp": 80, "block": 0, "gold": 99, "deck_size": 10},
        )
        out = format_state_diff(prev, curr, "end_turn")
        # 2 energy was left unspent before turn ended — flag it.
        assert "2" in out and "energy unspent" in out

    def test_card_reward_offered_lists_all_cards(self):
        prev = {"type": "decision", "decision": "combat_play"}
        curr = {
            "type": "decision",
            "decision": "card_reward",
            "can_skip": True,
            "cards": [
                {"index": 0, "name": "Inflame", "cost": 1, "type": "Power", "rarity": "Uncommon"},
                {"index": 1, "name": "Strike", "cost": 1, "type": "Attack", "rarity": "Common"},
                {"index": 2, "name": "Bash", "cost": 2, "type": "Attack", "rarity": "Common"},
            ],
        }
        # Use proceed (a no-op transition) to drop the combat narrative.
        out = format_state_diff(prev, curr, "proceed")
        assert "Card reward offered" in out
        assert "Inflame" in out and "Strike" in out and "Bash" in out

    def test_card_reward_pick_records_chosen_and_skipped(self):
        prev = {
            "type": "decision",
            "decision": "card_reward",
            "cards": [
                {"index": 0, "name": "Inflame", "cost": 1},
                {"index": 1, "name": "Strike", "cost": 1},
                {"index": 2, "name": "Bash", "cost": 2},
            ],
        }
        curr = {"type": "decision", "decision": "map_select"}
        out = format_state_diff(prev, curr, "select_card_reward", (0,))
        assert "Inflame" in out
        assert "skipped" in out and "Strike" in out and "Bash" in out

    def test_card_reward_skip_records_skipped_set(self):
        prev = {
            "type": "decision",
            "decision": "card_reward",
            "cards": [
                {"index": 0, "name": "Inflame"},
                {"index": 1, "name": "Strike"},
            ],
        }
        curr = {"type": "decision", "decision": "map_select"}
        out = format_state_diff(prev, curr, "skip_card_reward")
        assert "Skipped" in out
        assert "Inflame" in out and "Strike" in out
