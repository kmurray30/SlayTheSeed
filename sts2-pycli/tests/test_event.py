"""Tests for events."""


class TestNeowEvent:
    def test_neow_is_first_event(self, game):
        state = game.start(seed="ne1")
        assert state["decision"] == "event_choice"
        assert "Neow" in str(state.get("event_name", ""))

    def test_neow_options(self, game):
        state = game.start(seed="ne2")
        for opt in state["options"]:
            assert "title" in opt
            assert isinstance(opt["title"], str)
            assert "is_locked" in opt

    def test_neow_option_vars(self, game):
        state = game.start(seed="ne3")
        for opt in state["options"]:
            if opt.get("vars"):
                for k, v in opt["vars"].items():
                    assert isinstance(v, (int, float))

    def test_choose_neow(self, game):
        state = game.start(seed="ne4")
        opts = [o for o in state["options"] if not o.get("is_locked")]
        state = game.act("choose_option", option_index=opts[0]["index"])
        assert state.get("decision") is not None


class TestEventDescriptions:
    def test_no_ismultiplayer_tag(self, game):
        state = game.start(seed="ed1")
        for opt in state.get("options", []):
            d = opt.get("description") or ""
            assert "IsMultiplayer" not in d


class TestEventCombatBridge:
    """Bead sts2-rs-ck9z6 (GH #166): event options that fire
    ``EnterCombatWithoutExitingEvent`` now run a REAL nested combat instead of
    short-circuiting to an instant-victory reward."""

    def test_battleworn_dummy_enters_and_resolves_real_combat(self, game):
        game.start(character="Ironclad", seed="bwd1")
        # Big HP pool so the fight can't kill us before it resolves.
        game.set_player(current_hp=200, max_hp=200)
        state = game.enter_room("event", event="BATTLEWORN_DUMMY")
        assert state.get("decision") == "event_choice"
        # Setting 1 (BattleFriendV1) — choosing it must ENTER combat, not
        # instantly resolve to a reward / map.
        state = game.choose_option(0)
        assert state.get("decision") == "combat_play", (
            f"event option must enter a real combat, got {state.get('decision')}"
        )
        # The existing combat loop drives it to completion and returns to the run.
        state = game.auto_play_combat(state)
        assert state.get("decision") != "combat_play", "combat must resolve"

    def test_the_lantern_key_fight_enters_real_combat(self, game):
        game.start(character="Ironclad", seed="tlk1")
        game.set_player(current_hp=200, max_hp=200)
        state = game.enter_room("event", event="THE_LANTERN_KEY")
        assert state.get("decision") == "event_choice"
        # Page 0 "Keep the Key" opens the page-1 Fight option (multi-page).
        state = game.choose_option(1)
        assert state.get("decision") == "event_choice"
        # Page 1 "Fight" enters the MysteriousKnight combat.
        state = game.choose_option(0)
        assert state.get("decision") == "combat_play", (
            f"the Fight option must enter combat, got {state.get('decision')}"
        )
        state = game.auto_play_combat(state)
        assert state.get("decision") != "combat_play"
