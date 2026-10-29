"""Enemy-side wire completeness: power ``vars`` + allowlisted private AI state.

2026-08-02 wire-completeness round. Three enemy-object disclosures that the
headless (``RunSimulator.CombatPlayState``) wire withheld while the state they
carry is exactly what a reconstruction cannot re-derive:

* Enemy power ``vars`` -- the per-instance DynamicVar accumulators
  (Thievery's stolen ``Gold``, CrimsonMantle-class ``SelfDamage``) that
  neither ``amount`` nor ``display_amount`` describes. The player-power block
  has emitted them since the v10 fidelity fixes; the enemy block never did.
* ``fabricator_last_spawned`` -- reflection export of the private
  ``Fabricator._lastSpawned`` (Fabricator.cs:37), the bot kind excluded from
  the next SpawnBot pool.
* ``curse_of_knowledge_counter`` -- reflection export of the private
  ``KnowledgeDemon._curseOfKnowledgeCounter`` (KnowledgeDemon.cs:66), which
  indexes the Disintegration 6/7/8 damage table.

The SpireMentor mod emits the same fields from
``SimStateExtractor.SerializeCreature`` / ``SerializePower``; these tests
pin the headless twin so the two producers cannot drift apart silently.
"""

FABRICATOR_BOTS = {"ZAPBOT", "STABBOT", "GUARDBOT", "NOISEBOT"}
# FabricateMove spawns defense-then-aggro, FabricatingStrikeMove spawns aggro
# only -- either way the LAST spawn of the first enemy turn is an aggro bot.
FABRICATOR_AGGRO_BOTS = {"ZAPBOT", "STABBOT"}


def _enemy(state, name):
    matches = [e for e in state["enemies"] if e["name"] == name]
    assert matches, f"no enemy named {name!r} in {[e['name'] for e in state['enemies']]}"
    return matches[0]


class TestEnemyPowerVars:
    def test_thievery_gold_var_on_enemy_power(self, game):
        """GremlinMerc opens with Thievery, whose stolen-Gold tally lives in
        ``DynamicVars.Gold`` (ThieveryPower.cs:19,27) -- the wire's only
        carrier for it must be the enemy power's ``vars`` dict."""
        state = game.start(seed="epv1")
        game.skip_neow(state)
        state = game.enter_room("combat", encounter="GREMLIN_MERC_NORMAL")
        assert state["decision"] == "combat_play"
        merc = _enemy(state, "Gremlin Merc")
        thievery = [p for p in (merc.get("powers") or []) if p.get("id") == "THIEVERY_POWER"]
        assert thievery, f"expected THIEVERY_POWER on the merc, got {merc.get('powers')}"
        assert thievery[0].get("vars") == {"Gold": 0}, (
            "enemy power vars must disclose the Thievery Gold accumulator "
            f"(got {thievery[0].get('vars')!r})"
        )


class TestMonsterHiddenState:
    def test_knowledge_demon_curse_counter_on_wire(self, game):
        state = game.start(seed="epv2")
        game.skip_neow(state)
        state = game.enter_room("combat", encounter="KNOWLEDGE_DEMON_BOSS")
        assert state["decision"] == "combat_play"
        demon = _enemy(state, "Knowledge Demon")
        assert demon.get("curse_of_knowledge_counter") == 0, (
            "KnowledgeDemon must disclose its private curse counter "
            f"(got {demon.get('curse_of_knowledge_counter')!r})"
        )

    def test_fabricator_last_spawned_absent_then_disclosed(self, game):
        state = game.start(seed="epv3")
        game.skip_neow(state)
        state = game.enter_room("combat", encounter="FABRICATOR_NORMAL")
        assert state["decision"] == "combat_play"
        fab = _enemy(state, "Fabricator")
        # Nothing spawned yet: absent field == "nothing to disclose", the
        # relic-latch convention. Emitting a null here would be a lie -- the
        # engine's inference must stay in charge for pre-spawn states.
        assert "fabricator_last_spawned" not in fab

        # Both turn-1 telegraphs (FABRICATE / FABRICATING_STRIKE) spawn at
        # least one bot, and the last spawn of either move is an aggro bot.
        state = game.act("end_turn")
        if state["decision"] != "combat_play":
            return  # world ended the fight some other way; nothing to pin
        fab = _enemy(state, "Fabricator")
        last = fab.get("fabricator_last_spawned")
        assert last in FABRICATOR_AGGRO_BOTS, (
            f"after the first Fabricator turn the wire must carry the "
            f"last-spawned bot kind (got {last!r})"
        )
        assert last in FABRICATOR_BOTS
