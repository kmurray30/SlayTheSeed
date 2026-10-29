"""Tests for language support."""


class TestLanguage:
    def test_lang_en_returns_english(self, game):
        state = game.start(seed="lang_en1", lang="en")
        player = state.get("player", {})
        deck = player.get("deck", [])
        assert any("Strike" in str(c.get("name", "")) for c in deck), (
            f"Expected English card names, got: {[c['name'] for c in deck[:3]]}"
        )

    def test_default_lang_is_english(self, game):
        """Without lang param, should default to English."""
        state = game.send({"cmd": "start_run", "character": "Ironclad", "seed": "lang_def1"})
        player = state.get("player", {})
        deck = player.get("deck", [])
        names = [c.get("name", "") for c in deck]
        assert any("Strike" in str(name) for name in names), (
            f"Expected English by default, got: {names[:3]}"
        )
