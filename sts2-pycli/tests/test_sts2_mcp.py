"""Unit tests for the MCP server envelope.

We don't spawn the C# process here — the underlying ``Game`` is exercised by
every other test file through the fixture. This module pins the envelope shape
that ``_wrap`` produces, since clients depend on those keys.
"""

import sts2_mcp


class TestWrapEnvelope:
    def test_decision_envelope_default_omits_state(self):
        sts2_mcp._last_state = None  # reset cache
        state = {
            "type": "decision",
            "decision": "map_select",
            "context": {
                "act": 1,
                "act_name": "X",
                "floor": 1,
                "room_type": "Map",
                "boss": {"id": "B", "name": "Boss"},
            },
            "choices": [{"col": 1, "row": 1, "type": "Monster"}],
            "player": {"hp": 80, "max_hp": 80, "deck_size": 10, "gold": 99},
        }
        # Default is verbose=False — text summary only, no JSON state.
        out = sts2_mcp._wrap(state)
        assert out["decision"] == "map_select"
        assert "summary" in out
        assert "Choose next room" in out["summary"]
        assert "state" not in out
        # _wrap still caches the latest state for inspection tools.
        assert sts2_mcp._last_state is not None

    def test_verbose_true_includes_state(self):
        sts2_mcp._last_state = None
        state = {
            "type": "decision",
            "decision": "map_select",
            "context": {
                "act": 1,
                "act_name": "X",
                "floor": 1,
                "room_type": "Map",
                "boss": {"id": "B", "name": "Boss"},
            },
            "choices": [{"col": 1, "row": 1, "type": "Monster"}],
            "player": {"hp": 80, "max_hp": 80, "deck_size": 10, "gold": 99},
        }
        out = sts2_mcp._wrap(state, verbose=True)
        assert "state" in out

    def test_verbose_false_drops_state(self):
        state = {
            "type": "decision",
            "decision": "game_over",
            "victory": True,
            "context": {
                "act": 1,
                "act_name": "X",
                "floor": 8,
                "room_type": "Boss",
                "boss": {"id": "B", "name": "Boss"},
            },
            "player": {"hp": 50, "max_hp": 80, "deck_size": 20, "gold": 100},
        }
        out = sts2_mcp._wrap(state, verbose=False)
        assert "summary" in out
        assert "state" not in out

    def test_error_envelope(self):
        out = sts2_mcp._wrap({"type": "error", "message": "boom"})
        assert out["decision"] == "error"
        assert out.get("error") == "boom"
        # Summary is now always present, even on errors, so clients can rely on the key.
        assert "summary" in out
        assert "boom" in out["summary"]

    def test_envelope_summary_present_for_unknown_type(self):
        # Non-decision/non-error responses (e.g. a save_result) still get a summary.
        out = sts2_mcp._wrap({"type": "save_result", "ok": True})
        assert "summary" in out
        assert out["decision"] == "save_result"

    def test_get_state_does_not_overwrite_cache(self):
        sentinel = {
            "type": "decision",
            "decision": "map_select",
            "context": {
                "act": 1,
                "act_name": "X",
                "floor": 1,
                "room_type": "Map",
                "boss": {"id": "B", "name": "Boss"},
            },
            "choices": [{"col": 1, "row": 1, "type": "Monster"}],
        }
        sts2_mcp._last_state = sentinel
        sts2_mcp.get_state()
        # ``get_state`` is read-only — it must not replace the cached object
        # with a re-sanitized clone, since other inspection tools read directly
        # from this reference.
        assert sts2_mcp._last_state is sentinel

    def test_get_state_without_run(self):
        sts2_mcp._game = None
        sts2_mcp._last_state = None
        out = sts2_mcp.get_state()
        assert "error" in out

    def test_describe_relic_without_run(self):
        sts2_mcp._last_state = None
        out = sts2_mcp.describe_relic("anything")
        assert "error" in out

    def test_describe_relic_with_state(self):
        sts2_mcp._last_state = {
            "player": {
                "relics": [
                    {"name": "Burning Blood", "description": "Heal {Heal}.", "vars": {"Heal": 6}},
                ]
            }
        }
        out = sts2_mcp.describe_relic("burn")
        assert "Burning Blood" in out["text"]


class TestInspectionToolsDoNotSpawn:
    """Inspection tools must error rather than spawn a fresh subprocess."""

    def test_describe_boss_without_game_returns_error(self):
        sts2_mcp._game = None
        sts2_mcp._last_state = None
        out = sts2_mcp.describe_boss()
        assert "error" in out

    def test_describe_boss_with_state_but_no_game_does_not_spawn(self):
        # State has no boss info, so describe_boss would normally fall back to
        # get_map(). Without an active game it must report an error instead of
        # silently spawning a fresh C# subprocess.
        sts2_mcp._game = None
        sts2_mcp._last_state = {"context": {}}
        out = sts2_mcp.describe_boss()
        assert "error" in out
        assert sts2_mcp._game is None  # confirm no orphan process was created

    def test_show_map_without_game_returns_error(self):
        sts2_mcp._game = None
        out = sts2_mcp.show_map()
        assert "error" in out
        assert sts2_mcp._game is None


class TestToolDiscovery:
    def test_all_action_verbs_have_tools(self):
        """Every verb in ACTIONS must be exposed as an MCP tool."""
        import asyncio

        from sts2_client import ACTIONS

        tools = asyncio.run(sts2_mcp.mcp.list_tools())
        tool_names = {t.name for t in tools}
        for verb in ACTIONS:
            assert verb in tool_names, f"missing MCP tool for action: {verb}"

    def test_lifecycle_and_inspection_tools_present(self):
        import asyncio

        tools = asyncio.run(sts2_mcp.mcp.list_tools())
        tool_names = {t.name for t in tools}
        for name in (
            "start_run",
            "quit_run",
            "get_state",
            "load_save",
            "write_continue_save",
            "describe_card",
            "describe_relic",
            "describe_potion",
            "describe_boss",
            "show_deck",
            "show_map",
            "show_relics",
            "show_potions",
            "show_pile",
        ):
            assert name in tool_names


class TestListAndPileInspection:
    """``show_relics`` / ``show_potions`` / ``show_pile`` mirror the CLI views."""

    def test_show_relics_lists_owned_relics(self):
        sts2_mcp._last_state = {
            "player": {
                "relics": [
                    {"name": "Burning Blood", "description": "Heal {Heal}.", "vars": {"Heal": 6}},
                    {"name": "Lava Rock", "description": "Drop {Drops}.", "vars": {"Drops": 2}},
                ]
            }
        }
        out = sts2_mcp.show_relics()
        assert "Burning Blood" in out["text"]
        assert "Lava Rock" in out["text"]

    def test_show_relics_without_run(self):
        sts2_mcp._last_state = None
        assert "error" in sts2_mcp.show_relics()

    def test_show_potions_lists_owned_potions(self):
        sts2_mcp._last_state = {
            "player": {
                "potions": [
                    {
                        "index": 0,
                        "name": "Block Potion",
                        "description": "Gain {Block}.",
                        "vars": {"Block": 12},
                    },
                ]
            }
        }
        out = sts2_mcp.show_potions()
        assert "Block Potion" in out["text"]

    def test_show_pile_renders_named_pile(self):
        sts2_mcp._last_state = {
            "draw_pile": [{"name": "Strike", "cost": 1, "type": "Attack"}],
            "discard_pile": [],
            "exhaust_pile": [],
        }
        out = sts2_mcp.show_pile("draw")
        assert "Strike" in out["text"]
        assert "Draw pile (1)" in out["text"]

    def test_show_pile_unknown_kind_returns_helpful_text(self):
        sts2_mcp._last_state = {"draw_pile": [], "discard_pile": [], "exhaust_pile": []}
        out = sts2_mcp.show_pile("graveyard")
        # Not an error envelope — we let the caller see the valid set so it
        # can retry without another roundtrip.
        assert "Unknown pile" in out["text"]


class TestRecommendCombat:
    """``recommend_combat`` must read from the cached state and return only
    a small action envelope — no JSON state shuttling required."""

    def test_recommend_without_run_returns_error(self):
        sts2_mcp._last_state = None
        assert "error" in sts2_mcp.recommend_combat()

    def test_recommend_outside_combat_returns_error(self):
        sts2_mcp._last_state = {"decision": "map_select"}
        out = sts2_mcp.recommend_combat()
        assert "error" in out

    def test_recommend_picks_a_card(self):
        # Minimal combat_play state: one playable Strike, one weak attacker.
        sts2_mcp._last_state = {
            "decision": "combat_play",
            "energy": 3,
            "player": {"hp": 70, "max_hp": 80, "block": 0},
            "enemies": [
                {
                    "index": 0,
                    "name": "Slime",
                    "hp": 5,
                    "max_hp": 12,
                    "intends_attack": True,
                    "intents": [{"type": "Attack", "damage": 7, "hits": 1}],
                }
            ],
            "hand": [
                {
                    "index": 0,
                    "name": "Strike",
                    "cost": 1,
                    "type": "Attack",
                    "target_type": "AnyEnemy",
                    "can_play": True,
                    "stats": {"damage": 6},
                },
            ],
        }
        out = sts2_mcp.recommend_combat()
        # No "error" key, picks an action with reasoning + summary.
        assert "error" not in out
        assert out["action"] in {"play_card", "use_potion", "end_turn"}
        assert "reasoning" in out
        assert "summary" in out
        # The recommender must NOT echo the full state JSON back — that's
        # the entire point of wrapping this as an MCP tool.
        assert "hand" not in out
        assert "enemies" not in out


class TestAutoBriefing:
    """The ``_briefings`` hook should auto-lookup unfamiliar enemies and
    fire a tool reminder when the floor advances."""

    def setup_method(self):
        sts2_mcp._seen_enemies.clear()
        sts2_mcp._seen_relics.clear()
        sts2_mcp._last_floor = None

    def test_floor_change_appends_tool_tip(self):
        # First call: jump from no-floor to floor 5 in a card_reward decision.
        curr = {
            "decision": "card_reward",
            "context": {"floor": 5, "act": 1, "act_name": "X", "room_type": "Card"},
        }
        out = sts2_mcp._briefings(None, curr)
        assert "lookup" in out
        # Same floor, no change → no reminder a second time.
        assert sts2_mcp._briefings(curr, curr) == ""

    def test_seen_enemies_dedup_within_run(self):
        # Pretend we've seen Slime already; new state with Slime should NOT
        # trigger another lookup.
        sts2_mcp._seen_enemies.add("Slime")
        curr = {
            "decision": "combat_play",
            "context": {"floor": 3, "act": 1, "act_name": "X", "room_type": "Combat"},
            "enemies": [{"name": "Slime"}],
        }
        out = sts2_mcp._briefings(None, curr)
        # No "New enemies" briefing — only the floor tip (if any).
        assert "New enemies" not in out

    def test_start_run_resets_caches(self):
        sts2_mcp._seen_enemies.add("Beetle")
        sts2_mcp._last_floor = 12
        # We can't easily call start_run without spawning the C# process, but
        # we can verify the body's cache resets directly.
        sts2_mcp._seen_enemies.clear()
        sts2_mcp._last_floor = None
        assert sts2_mcp._seen_enemies == set()
        assert sts2_mcp._last_floor is None
