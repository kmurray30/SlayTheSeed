"""set_player upgrade-marker ("BASH+1") integration tests (sweep v2).

Uses the real C# headless via the ``game`` fixture (conftest.py). The only
externally observable proof that UpgradeInternal actually ran is the card's
combat behavior, so we play an upgraded STRIKE and assert the upgraded damage
(9, base 6) lands on the monster.

The ShrinkerBeetle (SHRINKER_BEETLE_WEAK) starts its first turn with a
DebuffIntent (ShrinkMove), so it has no block on turn 1 — a clean target for
damage assertions.
"""

from __future__ import annotations

SEED = "PARITYSWEEPV2"
ENCOUNTER = "SHRINKER_BEETLE_WEAK"  # single-monster, no block on turn 1


def _enter_combat(game, deck: list[str]) -> dict:
    state = game.start(character="Ironclad", seed=SEED)
    game.skip_neow(state)
    r = game.set_player(hp=70, max_hp=80, deck=deck, relics=[], potions=[])
    assert r.get("type") != "error", f"set_player failed: {r}"
    combat = game.enter_room("combat", encounter=ENCOUNTER)
    assert combat.get("decision") == "combat_play", f"expected combat_play, got: {combat}"
    # enter_room returns the combat_play state with "enemies"; use get_state()
    # which returns the normalised snapshot with "monsters" (matching the parity
    # schema) so _play_strike can use a single consistent field name.
    return game.get_state()


def _play_strike(game, state: dict) -> int:
    """Play the first Strike in hand, return damage dealt.

    ``state`` must come from ``game.get_state()`` (uses "monsters" key).
    The combat_play state from ``enter_room`` uses "enemies" instead.
    """
    monsters = state["monsters"]
    hp0 = monsters[0]["hp"]
    # Find a playable strike-family card (all cards in the deck are Strikes).
    # get_state() doesn't include hand metadata like can_play, so we use
    # legal_actions() to find a playable card index and target.
    actions = game.legal_actions()
    play_actions = [a for a in actions.get("actions", []) if a.get("action") == "play_card"]
    assert play_actions, f"No legal play_card actions: {actions}"
    first = play_actions[0]
    r = game.play_card(first["card_index"], first.get("target_index"))
    assert r.get("type") != "error", f"play_card failed: {r}"
    hp1 = game.get_state()["monsters"][0]["hp"]
    return hp0 - hp1


def test_upgraded_strike_deals_upgraded_damage(game):
    """Upgraded STRIKE_IRONCLAD must deal 9 damage (base 6, upgraded 9)."""
    state = _enter_combat(game, ["STRIKE_IRONCLAD+1"] * 5)
    dealt = _play_strike(game, state)
    assert dealt == 9, f"upgraded STRIKE must deal 9, dealt {dealt}"


def test_base_strike_unchanged(game):
    """Baseline: un-upgraded STRIKE_IRONCLAD still deals exactly 6 damage."""
    state = _enter_combat(game, ["STRIKE_IRONCLAD"] * 5)
    dealt = _play_strike(game, state)
    assert dealt == 6, f"base STRIKE must deal 6, dealt {dealt}"


def test_bad_suffix_plus_zero_is_error(game):
    """'+0' is a malformed marker — must be rejected with an error response."""
    game.skip_neow(game.start(character="Ironclad", seed=SEED))
    r = game.set_player(hp=70, max_hp=80, deck=["STRIKE_IRONCLAD+0"], relics=[], potions=[])
    assert r.get("type") == "error", (
        "STRIKE_IRONCLAD+0: malformed marker must be rejected, not silently dropped"
    )


def test_bad_suffix_plus_two_is_error(game):
    """'+2' is a malformed marker — must be rejected with an error response."""
    game.skip_neow(game.start(character="Ironclad", seed=SEED))
    r = game.set_player(hp=70, max_hp=80, deck=["STRIKE_IRONCLAD+2"], relics=[], potions=[])
    assert r.get("type") == "error", (
        "STRIKE_IRONCLAD+2: malformed marker must be rejected, not silently dropped"
    )


def test_bad_suffix_non_numeric_is_error(game):
    """'+x' is a malformed marker — must be rejected with an error response."""
    game.skip_neow(game.start(character="Ironclad", seed=SEED))
    r = game.set_player(hp=70, max_hp=80, deck=["STRIKE_IRONCLAD+x"], relics=[], potions=[])
    assert r.get("type") == "error", (
        "STRIKE_IRONCLAD+x: malformed marker must be rejected, not silently dropped"
    )
