"""Unit tests for the live-viewer one-liner formatter (``mcp_viewer._action_summary``).

The viewer renders a Recent Actions panel from the JSONL log written by
``sts2_mcp.py``. Each entry has ``action``, ``args_str``, ``prev_state``, and
``state``; ``_action_summary`` distills them into a single rich line.
"""

from __future__ import annotations

import re

import mcp_viewer

ANSI_RE = re.compile(r"\033\[[0-9;]*m")


def strip(s: str) -> str:
    """Drop ANSI color codes for stable substring assertions."""
    return ANSI_RE.sub("", s)


def summary(action: str, args_str: str = "", *, prev=None, curr=None) -> str:
    entry = {
        "action": action,
        "args_str": args_str,
        "prev_state": prev or {},
        "state": curr or {},
    }
    return strip(mcp_viewer._action_summary(entry))


class TestPlayCard:
    def test_attack_shows_damage_and_state_suffix(self):
        prev = {
            "decision": "combat_play",
            "energy": 3,
            "max_energy": 3,
            "hand": [{"index": 0, "name": "Strike", "cost": 1}],
            "enemies": [{"index": 0, "name": "Beetle", "hp": 30, "max_hp": 30}],
            "player": {"hp": 80, "max_hp": 80, "block": 0},
        }
        curr = {
            "decision": "combat_play",
            "energy": 2,
            "max_energy": 3,
            "hand": [],
            "enemies": [{"index": 0, "name": "Beetle", "hp": 24, "max_hp": 30}],
            "player": {"hp": 80, "max_hp": 80, "block": 0},
        }
        out = summary("play_card", "card_index=0, target_index=0", prev=prev, curr=curr)
        assert "Strike" in out
        assert "Beetle" in out
        assert "-6" in out and "30→24" in out
        assert "HP 80/80" in out
        assert "E 2/3" in out

    def test_block_play_surfaces_enemy_intent(self):
        prev = {
            "decision": "combat_play",
            "hand": [{"index": 1, "name": "Defend", "cost": 1}],
            "enemies": [
                {
                    "index": 0,
                    "name": "Beetle",
                    "hp": 30,
                    "max_hp": 30,
                    "intents": [{"type": "Attack", "damage": 8, "hits": 1}],
                }
            ],
            "player": {"hp": 80, "max_hp": 80, "block": 0},
        }
        curr = {
            "decision": "combat_play",
            "enemies": prev["enemies"],
            "player": {"hp": 80, "max_hp": 80, "block": 5},
        }
        out = summary("play_card", "card_index=1", prev=prev, curr=curr)
        assert "+5 block" in out
        assert "Atk 8" in out
        assert "vs" in out

    def test_kill_marks_enemy(self):
        prev = {
            "decision": "combat_play",
            "hand": [{"index": 0, "name": "Strike", "cost": 1}],
            "enemies": [{"index": 0, "name": "Bug", "hp": 5, "max_hp": 30}],
            "player": {"hp": 80, "max_hp": 80, "block": 0},
        }
        curr = {
            "decision": "combat_play",
            "enemies": [],
            "player": {"hp": 80, "max_hp": 80, "block": 0},
        }
        out = summary("play_card", "card_index=0, target_index=0", prev=prev, curr=curr)
        assert "Bug" in out
        assert "killed" in out


class TestEndTurn:
    def test_reports_dmg_taken_and_block(self):
        prev = {
            "decision": "combat_play",
            "round": 1,
            "energy": 0,
            "max_energy": 3,
            "player": {"hp": 80, "max_hp": 80, "block": 5},
            "enemies": [
                {
                    "index": 0,
                    "name": "Bug",
                    "hp": 20,
                    "max_hp": 20,
                    "intents": [{"type": "Attack", "damage": 10, "hits": 1}],
                }
            ],
        }
        curr = {
            "decision": "combat_play",
            "round": 2,
            "energy": 3,
            "max_energy": 3,
            "player": {"hp": 75, "max_hp": 80, "block": 0},
            "enemies": [{"index": 0, "name": "Bug", "hp": 20, "max_hp": 20}],
        }
        out = summary("end_turn", "", prev=prev, curr=curr)
        assert "round 1→2" in out
        assert "took 5 dmg" in out
        assert "blocked 5" in out
        assert "HP 80→75" in out

    def test_reports_unspent_energy(self):
        prev = {
            "decision": "combat_play",
            "round": 1,
            "energy": 2,
            "max_energy": 3,
            "player": {"hp": 80, "max_hp": 80, "block": 0},
            "enemies": [],
        }
        curr = {
            "decision": "combat_play",
            "round": 2,
            "energy": 3,
            "max_energy": 3,
            "player": {"hp": 80, "max_hp": 80, "block": 0},
            "enemies": [],
        }
        out = summary("end_turn", "", prev=prev, curr=curr)
        assert "2 E unspent" in out

    def test_combat_end_transition_records_hp_delta(self):
        prev = {
            "decision": "combat_play",
            "round": 2,
            "player": {"hp": 70, "max_hp": 80, "block": 0},
            "enemies": [{"index": 0, "name": "Bug", "hp": 5, "max_hp": 20}],
        }
        curr = {
            "decision": "card_reward",
            "player": {"hp": 70, "max_hp": 80, "block": 0},
            "cards": [],
        }
        out = summary("end_turn", "", prev=prev, curr=curr)
        assert "70→70" in out
        assert "rounds" in out


class TestSelectMapNode:
    def test_combat_start_appends_enemy_roster(self):
        prev = {"decision": "map_select"}
        curr = {
            "decision": "combat_play",
            "context": {"room_type": "Monster"},
            "enemies": [
                {
                    "index": 0,
                    "name": "Twig Slime",
                    "hp": 14,
                    "max_hp": 14,
                    "intents": [{"type": "Attack", "damage": 6, "hits": 1}],
                }
            ],
            "player": {"hp": 80, "max_hp": 80},
        }
        out = summary("select_map_node", "col=1, row=1", prev=prev, curr=curr)
        assert "Monster" in out
        assert "Twig Slime 14/14" in out
        assert "Atk 6" in out


class TestCardReward:
    def test_select_lists_all_with_chosen_starred(self):
        prev = {
            "decision": "card_reward",
            "cards": [
                {"index": 0, "name": "Inflame", "cost": 1, "rarity": "Uncommon"},
                {"index": 1, "name": "Strike", "cost": 1, "rarity": "Common"},
            ],
        }
        curr = {"decision": "map_select"}
        out = summary("select_card_reward", "card_index=0", prev=prev, curr=curr)
        assert "Inflame" in out
        assert "Strike" in out
        assert "★" in out

    def test_skip_lists_all_offerings(self):
        prev = {
            "decision": "card_reward",
            "cards": [
                {"index": 0, "name": "Inflame"},
                {"index": 1, "name": "Strike"},
            ],
        }
        curr = {"decision": "map_select"}
        out = summary("skip_card_reward", "", prev=prev, curr=curr)
        assert "Inflame" in out and "Strike" in out
        assert "skipped" in out


class TestDescribeBoss:
    def test_includes_boss_name(self):
        curr = {"context": {"boss": {"name": "The Champ", "id": "CHAMP_BOSS"}}}
        out = summary("describe_boss", "", prev={}, curr=curr)
        assert "describe_boss" in out
        assert "The Champ" in out
