"""Offline tests for :mod:`realgame_parity` (real-game bridge phase 2 GATE).

Pure-dict tests only, per the phase-2 task split: the differ
(``choose_action``/``flatten_decision``/``diff_trails``) is exercised with
small synthetic decision dicts, and ``record_trail``'s orchestration loop is
exercised against a minimal in-process fake backend (``_FakeGame``) -- never
the live game or the headless C# binary, so this module runs with no
external dependencies.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "python"))

from realgame_parity import (
    DEFAULT_IGNORE_PATTERNS,
    choose_action,
    diff_trails,
    flatten_decision,
    record_trail,
)

# ──────────────────────────────────────────────────────────────────────────────
# choose_action
# ──────────────────────────────────────────────────────────────────────────────


class TestChooseAction:
    def test_map_select_picks_first_choice(self) -> None:
        decision = {
            "type": "decision",
            "decision": "map_select",
            "choices": [{"col": 2, "row": 1, "type": "Monster"}, {"col": 3, "row": 1}],
        }
        assert choose_action(decision) == ("select_map_node", {"col": 2, "row": 1})

    def test_map_select_no_choices_returns_none(self) -> None:
        decision = {"type": "decision", "decision": "map_select", "choices": []}
        assert choose_action(decision) is None

    def test_combat_play_always_ends_turn(self) -> None:
        decision = {
            "type": "decision",
            "decision": "combat_play",
            "hand": [{"index": 0, "cost": 1, "can_play": True}],
            "energy": 3,
        }
        assert choose_action(decision) == ("end_turn", {})

    def test_event_choice_picks_first_unlocked(self) -> None:
        decision = {
            "type": "decision",
            "decision": "event_choice",
            "options": [
                {"index": 0, "is_locked": True},
                {"index": 1, "is_locked": False},
                {"index": 2, "is_locked": False},
            ],
        }
        assert choose_action(decision) == ("choose_option", {"option_index": 1})

    def test_event_choice_all_locked_falls_back_to_first(self) -> None:
        decision = {
            "type": "decision",
            "decision": "event_choice",
            "options": [{"index": 0, "is_locked": True}, {"index": 1, "is_locked": True}],
        }
        assert choose_action(decision) == ("choose_option", {"option_index": 0})

    def test_event_choice_no_options_leaves_room(self) -> None:
        decision = {"type": "decision", "decision": "event_choice", "options": []}
        assert choose_action(decision) == ("leave_room", {})

    def test_rest_site_picks_first_enabled(self) -> None:
        decision = {
            "type": "decision",
            "decision": "rest_site",
            "options": [
                {"index": 0, "option_id": "SMITH", "is_enabled": False},
                {"index": 1, "option_id": "HEAL", "is_enabled": True},
            ],
        }
        assert choose_action(decision) == ("choose_option", {"option_index": 1})

    def test_shop_always_leaves(self) -> None:
        decision = {"type": "decision", "decision": "shop", "cards": [], "relics": []}
        assert choose_action(decision) == ("leave_room", {})

    def test_card_reward_takes_first_card_when_offered(self) -> None:
        decision = {
            "type": "decision",
            "decision": "card_reward",
            "cards": [{"index": 0, "id": "STRIKE"}],
            "can_skip": True,
        }
        assert choose_action(decision) == ("select_card_reward", {"card_index": 0})

    def test_card_reward_skips_when_no_cards(self) -> None:
        decision = {"type": "decision", "decision": "card_reward", "cards": [], "can_skip": True}
        assert choose_action(decision) == ("skip_card_reward", {})

    def test_card_select_picks_min_required(self) -> None:
        decision = {
            "type": "decision",
            "decision": "card_select",
            "cards": [{"index": 0}, {"index": 1}, {"index": 2}],
            "min_select": 2,
            "max_select": 3,
        }
        assert choose_action(decision) == ("select_cards", {"indices": "0,1"})

    def test_card_select_zero_min_skips(self) -> None:
        decision = {
            "type": "decision",
            "decision": "card_select",
            "cards": [{"index": 0}],
            "min_select": 0,
            "max_select": 1,
        }
        assert choose_action(decision) == ("skip_select", {})

    def test_bundle_select_picks_first(self) -> None:
        decision = {
            "type": "decision",
            "decision": "bundle_select",
            "bundles": [{"index": 0, "cards": []}, {"index": 1, "cards": []}],
        }
        assert choose_action(decision) == ("select_bundle", {"bundle_index": 0})

    def test_game_over_returns_none(self) -> None:
        decision = {"type": "decision", "decision": "game_over", "victory": False}
        assert choose_action(decision) is None

    def test_non_decision_type_returns_none(self) -> None:
        assert choose_action({"type": "error", "message": "boom"}) is None

    def test_unknown_decision_kind_returns_none(self) -> None:
        assert choose_action({"type": "decision", "decision": "crystal_sphere"}) is None

    def test_is_pure_function_same_input_same_output(self) -> None:
        decision = {
            "type": "decision",
            "decision": "rest_site",
            "options": [{"index": 0, "option_id": "HEAL", "is_enabled": True}],
        }
        results = [choose_action(decision) for _ in range(5)]
        assert all(r == results[0] for r in results)


# ──────────────────────────────────────────────────────────────────────────────
# flatten_decision
# ──────────────────────────────────────────────────────────────────────────────


class TestFlattenDecision:
    def test_scalar_leaves(self) -> None:
        leaves = flatten_decision({"a": 1, "b": "x"})
        paths = {p: v for p, _, v in leaves}
        assert paths == {"a": 1, "b": "x"}

    def test_nested_dict_path(self) -> None:
        leaves = flatten_decision({"context": {"act": 1, "floor": 3}})
        paths = {p: v for p, _, v in leaves}
        assert paths == {"context.act": 1, "context.floor": 3}

    def test_list_indices_and_normalization(self) -> None:
        leaves = flatten_decision({"hand": [{"cost": 1}, {"cost": 2}]})
        by_path = {p: (norm, v) for p, norm, v in leaves}
        assert by_path["hand[0].cost"] == ("hand[].cost", 1)
        assert by_path["hand[1].cost"] == ("hand[].cost", 2)

    def test_empty_dict_and_list_are_leaves(self) -> None:
        leaves = flatten_decision({"options": [], "context": {}})
        by_path = {p: v for p, _, v in leaves}
        assert by_path["options"] == "<empty-list>"
        assert by_path["context"] == "<empty-dict>"

    def test_none_value_is_a_leaf(self) -> None:
        leaves = flatten_decision({"act_name": None})
        by_path = {p: v for p, _, v in leaves}
        assert by_path["act_name"] is None


# ──────────────────────────────────────────────────────────────────────────────
# diff_trails
# ──────────────────────────────────────────────────────────────────────────────


def _decision(kind: str, **fields: Any) -> dict[str, Any]:
    return {"type": "decision", "decision": kind, **fields}


class TestDiffTrails:
    def test_identical_trails_have_empty_backlog(self) -> None:
        trail = [
            _decision("map_select", choices=[{"col": 0, "row": 0}]),
            _decision("combat_play", energy=3, hand=[{"cost": 1}]),
        ]
        report = diff_trails(trail, [dict(d) for d in trail])
        assert report.first_structural_divergence is None
        assert report.unique_mismatching_field_paths == []
        assert report.per_field_mismatch_counts == {}
        assert report.common_prefix_length == len(trail)

    def test_seeded_field_difference_is_reported(self) -> None:
        a = [_decision("combat_play", player={"hp": 50, "block": 0})]
        b = [_decision("combat_play", player={"hp": 42, "block": 0})]
        report = diff_trails(a, b)
        assert report.first_structural_divergence is None
        assert "player.hp" in report.unique_mismatching_field_paths
        assert report.per_field_mismatch_counts["player.hp"] == 1
        assert report.example_mismatches["player.hp"] == {
            "step": 0,
            "path": "player.hp",
            "a": 50,
            "b": 42,
        }
        # Untouched field must not show up.
        assert "player.block" not in report.unique_mismatching_field_paths

    def test_structural_divergence_reports_prefix_and_stops_field_diff(self) -> None:
        a = [
            _decision("map_select", choices=[{"col": 0, "row": 0}]),
            _decision("combat_play", energy=3),
        ]
        b = [
            _decision("map_select", choices=[{"col": 0, "row": 0}]),
            _decision("shop", cards=[]),
        ]
        report = diff_trails(a, b)
        assert report.first_structural_divergence == {
            "step": 1,
            "a_kind": "combat_play",
            "b_kind": "shop",
        }
        assert report.common_prefix_length == 1
        # Only step 0 (identical) is compared -- no mismatches at all.
        assert report.unique_mismatching_field_paths == []

    def test_trail_length_mismatch_is_a_structural_divergence(self) -> None:
        a = [_decision("map_select", choices=[]), _decision("combat_play")]
        b = [_decision("map_select", choices=[])]
        report = diff_trails(a, b)
        assert report.first_structural_divergence is not None
        assert report.first_structural_divergence["step"] == 1
        assert report.first_structural_divergence["a_kind"] == "combat_play"
        assert report.first_structural_divergence["b_kind"] is None
        assert report.common_prefix_length == 1

    def test_default_ignore_patterns_suppress_documented_residuals(self) -> None:
        a = [
            _decision(
                "map_select",
                context={"act": 1, "act_name": "The Sewers", "floor": 3},
                act_name="The Sewers",
            )
        ]
        b = [
            _decision(
                "map_select",
                context={"act": 1, "act_name": None, "floor": 3},
                act_name=None,
            )
        ]
        report = diff_trails(a, b, ignore_patterns=DEFAULT_IGNORE_PATTERNS)
        assert report.unique_mismatching_field_paths == []

    def test_custom_ignore_pattern_suppresses_extra_field(self) -> None:
        a = [_decision("combat_play", round=5)]
        b = [_decision("combat_play", round=9)]
        report = diff_trails(a, b, ignore_patterns=(*DEFAULT_IGNORE_PATTERNS, "round"))
        assert report.unique_mismatching_field_paths == []

    def test_field_present_only_on_one_side_is_reported(self) -> None:
        a = [_decision("card_reward", cards=[{"index": 0, "id": "STRIKE"}])]
        b = [_decision("card_reward", cards=[{"index": 0}])]
        report = diff_trails(a, b)
        assert "cards[].id" in report.unique_mismatching_field_paths
        ex = report.example_mismatches["cards[].id"]
        assert ex["a"] == "STRIKE"
        assert ex["b"] == "<absent>"

    def test_mismatch_counts_aggregate_across_steps_and_list_positions(self) -> None:
        a = [
            _decision("combat_play", hand=[{"cost": 1}, {"cost": 2}]),
            _decision("combat_play", hand=[{"cost": 3}]),
        ]
        b = [
            _decision("combat_play", hand=[{"cost": 9}, {"cost": 9}]),
            _decision("combat_play", hand=[{"cost": 9}]),
        ]
        report = diff_trails(a, b)
        assert report.per_field_mismatch_counts["hand[].cost"] == 3


# ──────────────────────────────────────────────────────────────────────────────
# record_trail -- orchestration loop against a minimal fake backend.
# ──────────────────────────────────────────────────────────────────────────────


class _FakeGame:
    """Minimal ``Game``/``RealGame``-shaped stub: a scripted decision sequence."""

    def __init__(self, decisions: list[dict[str, Any]]) -> None:
        self._decisions = decisions
        self._i = 0
        self.actions_received: list[tuple[str, dict[str, Any]]] = []

    def start(
        self,
        character: str,
        seed: str,
        ascension: int,
        lang: str = "en",
        act1: str = "overgrowth",
    ) -> dict[str, Any]:
        assert isinstance(seed, str)  # record_trail must stringify the int seed
        self._i = 0
        return self._decisions[self._i]

    def act(self, action: str, **kwargs: Any) -> dict[str, Any]:
        self.actions_received.append((action, kwargs))
        self._i += 1
        return self._decisions[self._i]


class TestRecordTrail:
    def test_stops_at_game_over(self) -> None:
        decisions = [
            _decision("map_select", choices=[{"col": 0, "row": 0}]),
            _decision("game_over", victory=True),
        ]
        game = _FakeGame(decisions)
        trail = record_trail(game, character="Ironclad", seed=12345, ascension=0, max_decisions=50)
        assert trail == decisions
        assert game.actions_received == [("select_map_node", {"col": 0, "row": 0})]

    def test_stops_at_max_decisions(self) -> None:
        decisions = [_decision("map_select", choices=[{"col": 0, "row": 0}])] * 10
        game = _FakeGame(decisions)
        trail = record_trail(game, character="Ironclad", seed=1, ascension=0, max_decisions=3)
        assert len(trail) == 3

    def test_stops_on_error_response(self) -> None:
        decisions = [
            _decision("map_select", choices=[{"col": 0, "row": 0}]),
            {"type": "error", "message": "boom"},
        ]
        game = _FakeGame(decisions)
        trail = record_trail(game, character="Ironclad", seed=1, ascension=0, max_decisions=50)
        assert trail == decisions

    def test_seed_is_stringified_before_start(self) -> None:
        decisions = [_decision("game_over", victory=False)]
        game = _FakeGame(decisions)
        record_trail(game, character="Ironclad", seed=999, ascension=0, max_decisions=10)
        # _FakeGame.start() itself asserts seed is a str; reaching here is enough.

    def test_writes_canonical_sorted_key_jsonl_trail(self, tmp_path: Path) -> None:
        decisions = [
            _decision("map_select", choices=[{"col": 0, "row": 0}]),
            _decision("game_over", victory=True),
        ]
        game = _FakeGame(decisions)
        trail_path = tmp_path / "trail.jsonl"
        record_trail(
            game,
            character="Ironclad",
            seed=1,
            ascension=0,
            max_decisions=50,
            trail_path=trail_path,
        )
        lines = trail_path.read_text(encoding="utf-8").splitlines()
        assert len(lines) == 2
        first = json.loads(lines[0])
        assert first["step"] == 0
        assert first["decision"]["decision"] == "map_select"
        assert first["action"] == {"action": "select_map_node", "col": 0, "row": 0}
        # Canonicalized: keys sorted -- the raw line's key order must match
        # json.dumps(..., sort_keys=True) applied to the same object.
        assert lines[0] == json.dumps(first, sort_keys=True, ensure_ascii=False)
        second = json.loads(lines[1])
        assert second["action"] is None
