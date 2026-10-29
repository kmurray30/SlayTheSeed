"""Unit tests for the pure utilities in ``python/sts2_client.py``.

The Game class itself is integration-tested by every test that uses the
``game`` fixture; this module covers the pure helpers only.
"""

from sts2_client import ACTIONS, compact_json, is_world_error_line, sanitize_json


class TestWorldErrorDetector:
    """Guard the stderr classifier behind ``Game.world_errors()``.

    Every sweep before 2026-07-28 drained the DLL's stderr and discarded it, so
    a C# throw mid-combat was structurally invisible and the wedged run scored
    as an ordinary loss. These cases are verbatim lines from the seed-1038 /
    1045 Kaiser Crab captures (``logs/stall/*.log``) plus the benign chatter
    that shares the pipe — a detector that cries wolf would get ignored, which
    is the exact failure mode it exists to fix.
    """

    def test_flags_real_throws(self):
        for line in (
            "[ERROR] [ERROR] System.NullReferenceException: Object reference not set "
            "to an instance of an object.",
            "[SIM] Event choose: NullReferenceException: Object reference not set to "
            "an instance of an object.",
            "[WARN] Unobserved task exception: A Task's exception(s) were not observed "
            "either by Waiting on the Task or accessing its Exception property.",
            "Unhandled exception. System.AggregateException: One or more errors",
            "  ---> System.ArgumentNullException: Value cannot be null.",
        ):
            assert is_world_error_line(line), line

    def test_ignores_benign_chatter(self):
        # Stack frames are NOT counted: one throw would otherwise score as
        # dozens, turning a presence signal into noise.
        for line in (
            "   at MegaCrit.Sts2.Core.Models.Monsters.Rocket.BeforeDeath(Creature creature)",
            "   at MegaCrit.Sts2.Core.Helpers.TaskHelper.LogTaskExceptions(Task task)",
            "LocManager: loaded 1234 keys <ValidationErrors>",
            "[INFO] ExceptionHandler registered",
            "[SIM] the encounter was exceptional",
        ):
            assert not is_world_error_line(line), line

    def test_one_throw_scores_one_line_with_frames_attached(self):
        """``RunSimulator.LogException`` prints frames under the header.

        The header must be counted and every frame ignored, or converting the
        swallowing catches to log stack traces would multiply every world-error
        count by ~12. Frames are emitted in the runtime's own ``   at Foo.Bar()``
        shape, prefixed with ``[SIM] `` like all other harness output.
        """
        emitted = [
            "[SIM] Event choose: NullReferenceException: Object reference not set "
            "to an instance of an object.",
            "[SIM]    at MegaCrit.Sts2.Core.Models.Events.Amalgamator.CombineStrikes()",
            "[SIM]    at System.Runtime.ExceptionServices.ExceptionDispatchInfo.Throw()",
            "[SIM]    at System.Runtime.CompilerServices.TaskAwaiter"
            ".HandleNonSuccessAndDebuggerNotification(Task task)",
            "[SIM]    at MegaCrit.Sts2.Core.Helpers.TaskHelper.LogTaskExceptions(Task task)",
            "[SIM]    ... 4 more frame(s)",
        ]
        assert sum(is_world_error_line(line) for line in emitted) == 1

    def test_a_frame_naming_an_exception_parameter_is_still_a_frame(self):
        """The reason frames are excluded STRUCTURALLY, not by content.

        Both of these are real v0.107.1 members, and both mention ``Exception``
        in their parameter list — so a content-only classifier counts them as
        fresh world errors and inflates one throw into three. The exactness the
        ``count`` docstring claims only holds if ``at``-lines are dropped before
        the type pattern ever runs.
        """
        for frame in (
            "[SIM]    at MegaCrit.Sts2.Core.Nodes.NGame"
            ".ReturnToMainMenuWithInternalError(Exception e)",
            "[SIM]    at MegaCrit.Sts2.Core.Debug.SentryService.CaptureException(Exception ex)",
            "   at MegaCrit.Sts2.Core.Debug.SentryService.CaptureException(Exception ex)",
        ):
            assert not is_world_error_line(frame), frame


class TestSanitizeJson:
    def test_strips_control_chars_in_string(self):
        assert sanitize_json("hi\x00\x07world") == "hiworld"

    def test_preserves_unicode(self):
        # Non-ASCII text is preserved — only ASCII control chars are stripped.
        assert sanitize_json("café\x01") == "café"

    def test_recurses_into_dict_and_list(self):
        out = sanitize_json({"a": "x\x00y", "b": ["c\x07d", {"e": "f\x1fg"}]})
        assert out == {"a": "xy", "b": ["cd", {"e": "fg"}]}

    def test_passes_through_non_strings(self):
        assert sanitize_json(42) == 42
        assert sanitize_json(None) is None
        assert sanitize_json(True) is True


class TestCompactJson:
    def test_strips_verbose_keys(self):
        state = {
            "decision": "card_reward",
            "id": "CARD.X",
            "description": "Deal 6.",
            "after_upgrade": {"stats": {"damage": 9}},
            "name": "Strike",
        }
        out = compact_json(state)
        assert "id" not in out
        assert "description" not in out
        assert "after_upgrade" not in out
        assert out["name"] == "Strike"

    def test_combat_play_drops_context_and_player_except_potions(self):
        state = {
            "decision": "combat_play",
            "context": {"act": 1, "floor": 3},
            "player": {
                "hp": 50,
                "max_hp": 80,
                "potions": [{"index": 0, "name": "X", "description": "..."}],
            },
            "round": 1,
        }
        out = compact_json(state)
        assert "context" not in out
        # player itself is dropped, but potions are surfaced at the top level
        assert "player" not in out
        assert out["potions"] == [{"index": 0, "name": "X"}]
        assert out["round"] == 1

    def test_non_combat_decisions_keep_player(self):
        state = {
            "decision": "map_select",
            "player": {"hp": 50, "max_hp": 80, "name": "Ironclad"},
            "context": {"act": 1, "floor": 3},
        }
        out = compact_json(state)
        # player retained for non-combat decisions
        assert out["player"]["hp"] == 50
        assert out["context"]["floor"] == 3

    def test_relic_list_compacted_when_nested(self):
        state = {
            "player": {
                "relics": [
                    {"name": "X", "description": "...", "vars": {"H": 6}},
                ]
            }
        }
        out = compact_json(state)
        # Description stripped from the nested relic.
        assert out["player"]["relics"][0] == {"name": "X", "vars": {"H": 6}}

    def test_passes_through_non_containers(self):
        assert compact_json(42) == 42
        assert compact_json("hello") == "hello"


class TestActions:
    def test_actions_tuple_matches_runsimulator(self):
        # Sanity check that the canonical list hasn't drifted.
        for verb in (
            "play_card",
            "end_turn",
            "select_map_node",
            "use_potion",
            "choose_option",
            "select_card_reward",
            "skip_card_reward",
            "select_bundle",
            "select_cards",
            "skip_select",
            "remove_card",
            "buy_card",
            "buy_relic",
            "buy_potion",
            "discard_potion",
            "leave_room",
            "proceed",
        ):
            assert verb in ACTIONS
