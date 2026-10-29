#!/usr/bin/env python3
"""Tests for `vfx_bindings.py`.

Crusher is the fixture because its state ids and its method names
disagree — the case that makes name-matching unsound (see the module
docstring in `vfx_bindings.py`).

NOTE on owner casing: the task brief's own `Binding` docstring example
(`owner: "CRUSHER:THRASH_MOVE"`) is wrong and was overridden during
pre-flight — the monster half of a move owner is PascalCase
(`"Crusher:THRASH_MOVE"`), matching `MonsterKind::python_name()`
(`sts2-core/src/monsters.rs`) and `combatlog.py::_source_ids`'s "left
uncased" note. A SCREAMING_SNAKE monster half would not join to anything.
"""

import unittest
from pathlib import Path

from scripts.vfx_bindings import (
    Binding,
    _card_id_python_names,
    card_owner_id,
    mentions_death_override,
    parse_card_file,
    parse_death_fade,
    parse_monster_file,
)

CRUSHER = """
	protected override MonsterMoveStateMachine GenerateMoveStateMachine()
	{
		MoveState moveState = new MoveState("THRASH_MOVE", ThrashMove, new SingleAttackIntent(ThrashDamage));
		MoveState moveState3 = new MoveState("BUG_STING_MOVE", BugStingMove, new MultiAttackIntent(BugStingDamage, BugStingTimes), new DebuffIntent());
		return new MonsterMoveStateMachine(list, moveState);
	}

	private async Task ThrashMove(IReadOnlyList<Creature> targets)
	{
		await DamageCmd.Attack(ThrashDamage).FromMonster(this).WithHitFx("vfx/vfx_attack_blunt", "event:/sfx/x")
	}

	private async Task BugStingMove(IReadOnlyList<Creature> targets)
	{
		await DamageCmd.Attack(BugStingDamage)
			.WithHitFx("vfx/vfx_attack_slash")
	}
"""  # noqa: E501 — the C# fixture text is verbatim from the task brief; keep it byte-for-byte

FLYCONID = """
	private async Task SporeMove(IReadOnlyList<Creature> targets)
	{
		await DamageCmd.Attack(SporeDamage)
			.WithHitVfxNode((Creature t) => (Node2D?)(object)NSporeImpactVfx.Create(t, new Color("8aad7d")))
	}
"""  # noqa: E501 — verbatim C# from the task brief; keep it byte-for-byte


class BindingTest(unittest.TestCase):
    def test_move_binding_uses_the_state_id_not_the_method_name(self) -> None:
        rows = {b.owner: b for b in parse_monster_file("Crusher", CRUSHER)}
        self.assertIn("Crusher:THRASH_MOVE", rows)
        self.assertEqual(rows["Crusher:THRASH_MOVE"].effect, "vfx_attack_blunt")

    def test_a_state_id_that_does_not_resemble_its_method_still_binds(self) -> None:
        # BUG_STING_MOVE -> BugStingMove is only knowable from the MoveState
        # constructor. Nothing in the two strings alone connects them.
        rows = {b.owner: b for b in parse_monster_file("Crusher", CRUSHER)}
        self.assertEqual(rows["Crusher:BUG_STING_MOVE"].effect, "vfx_attack_slash")

    def test_hit_vfx_node_captures_the_effect_and_the_hardcoded_tint(self) -> None:
        b = parse_monster_file("Flyconid", FLYCONID)[0]
        self.assertEqual(b.effect, "vfx_spore_impact")
        # The C# literal is bare hex (no '#'); `_tint` normalises to the
        # documented `#RRGGBB` form (task-6 review round 1) so every
        # consumer sees one format regardless of how the source wrote it.
        self.assertEqual(b.tint, "#8aad7d")
        self.assertEqual(b.shape, "hit_vfx_node")

    def test_a_non_literal_effect_argument_produces_no_row(self) -> None:
        self.assertEqual(parse_monster_file("X", '.WithHitFx(null, "s.mp3")'), [])

    def test_a_move_state_whose_method_cannot_be_found_raises(self) -> None:
        # Review round 3: this used to be a silent `continue` that dropped
        # the whole state's bindings with no error. A full-decomp sweep
        # found zero real cases (MonsterMoveBodyResolutionTest below), so
        # a genuine hit is either a new authoring pattern or a caller bug
        # -- either way, loud beats silent.
        text = 'new MoveState("GHOST_MOVE", MethodThatIsNeverDeclared, new SingleAttackIntent(1));'
        with self.assertRaises(ValueError):
            parse_monster_file("X", text)

    def test_card_binding_is_keyed_on_the_card_id(self) -> None:
        b = parse_card_file("Bash", '.WithHitFx("vfx/vfx_attack_blunt", null, "blunt_attack.mp3")')[
            0
        ]
        self.assertEqual(b.owner, "BASH")
        self.assertEqual(b.kind, "card")

    def test_multi_word_card_names_get_a_real_screaming_snake_join_key(self) -> None:
        # `CardId::KnockoutBlow.to_python_name()` == "KNOCKOUT_BLOW"
        # (sts2-core/src/card_ids_generated.rs) -- str.upper() alone would
        # give "KNOCKOUTBLOW", which joins to nothing.
        b = parse_card_file(
            "KnockoutBlow", '.WithHitFx("vfx/vfx_attack_blunt", null, "blunt_attack.mp3")'
        )[0]
        self.assertEqual(b.owner, "KNOCKOUT_BLOW")

    def test_direct_create_plus_add_child_safely_is_a_spawn_binding(self) -> None:
        # Shape 3 (design spec §1.1): a free-standing spawn, not attached to
        # a hit. Mirrors DeadlyPoison.cs.
        text = (
            "NPoisonImpactVfx child = NPoisonImpactVfx.Create(cardPlay.Target);\n"
            "((Node)(object)NCombatRoom.Instance?.CombatVfxContainer).AddChildSafely"
            "((Node?)(object)child);"
        )
        b = parse_card_file("DeadlyPoison", text)[0]
        self.assertEqual(
            b,
            Binding(
                owner="DEADLY_POISON",
                kind="card",
                effect="vfx_poison_impact",
                tint=None,
                shape="spawn",
            ),
        )

    def test_a_factory_for_an_out_of_scope_effect_produces_no_row(self) -> None:
        # NHyperbeamVfx is reached only via the free-standing "spawn" shape
        # in the real decomp (Hyperbeam.cs), never through WithHitVfxNode --
        # round 2 widened the table to cover every WithHitVfxNode-reached
        # class (reconciled against all 30 real call sites, see
        # task-5-report.md), NOT every N*Vfx.Create class in the codebase.
        text = ".WithHitVfxNode((Creature t) => (Node2D?)(object)NHyperbeamVfx.Create(t))"
        self.assertEqual(parse_monster_file("X", text), [])

    def test_vfx_cmd_is_the_only_route_to_coin_explosion_regular(self) -> None:
        # 4th shape, added in review: vfx_coin_explosion_regular has no
        # WithHitFx/WithHitVfxNode/Create site anywhere in the decomp --
        # GremlinMerc.cs's is the real call, reduced to its essentials.
        b = parse_monster_file(
            "GremlinMerc",
            'VfxCmd.PlayOnCreatureCenters(targets, "vfx/vfx_coin_explosion_regular");',
        )[0]
        self.assertEqual(b.effect, "vfx_coin_explosion_regular")
        self.assertEqual(b.shape, "vfx_cmd")

    def test_vfx_cmd_path_argument_need_not_be_first(self) -> None:
        # HandOfGreed.cs: VfxCmd.PlayVfx(position, "vfx/...", container) --
        # the literal is the 2nd argument, not the 1st like WithHitFx.
        b = parse_card_file(
            "HandOfGreed",
            'VfxCmd.PlayVfx(monsterPos.Value, "vfx/vfx_coin_explosion_regular", '
            "NCombatRoom.Instance?.CombatVfxContainer);",
        )[0]
        self.assertEqual(b.effect, "vfx_coin_explosion_regular")
        self.assertEqual(b.shape, "vfx_cmd")

    def test_hit_vfx_node_covers_classes_outside_the_13_particle_composites(self) -> None:
        # Round 2 reconciliation: NStabVfx (Finisher.cs/Skewer.cs) plays
        # "vfx_stab_vfx" -- a real effect id (the game's own scenePath is
        # "res://scenes/vfx/stab_vfx.tscn"; task-6 review round 1 asked for
        # a uniform `vfx_` prefix on every map key, so this is that verbatim
        # name with the prefix added, not a renamed id) -- resolvable from
        # the class's own scenePath, even though it isn't one of the 13
        # in-scope composites. Design spec Sec1.5: the table carries the
        # row, the renderer falls back.
        b = parse_card_file(
            "Finisher",
            ".WithHitVfxNode((Creature t) => "
            "(Node2D?)(object)NStabVfx.Create(t, facingEnemies: true))",
        )[0]
        self.assertEqual(b.effect, "vfx_stab_vfx")
        self.assertEqual(b.shape, "hit_vfx_node")


# `card_owner_id()` derives the Card/Status-suffix resolution itself now
# (round 2 -- see the module docstring in vfx_bindings.py); no
# hand-maintained exception list to keep in sync here any more. The only
# thing genuinely unresolvable is a decomp class with no CardId variant at
# all under any of the three tried suffixes -- an internal base with no
# live card, never a real join target.
_CARD_STEMS_WITH_NO_CARD_ID = {"DeprecatedCard"}

_STS2_WIKI = Path(__file__).resolve().parents[1]
_CARDS_DIR = _STS2_WIKI / "decompiled" / "v0.107.1" / "MegaCrit.Sts2.Core.Models.Cards"
_CARD_IDS_RS = (
    _STS2_WIKI.parent / "sts2-rs" / "crates" / "sts2-core" / "src" / "card_ids_generated.rs"
)


@unittest.skipUnless(
    _CARDS_DIR.is_dir() and _CARD_IDS_RS.is_file(),
    f"decomp Cards dir or sibling sts2-rs not present ({_CARDS_DIR}, {_CARD_IDS_RS})",
)
class CardOwnerIdResolutionTest(unittest.TestCase):
    """Guards `card_owner_id()` against the real, authoritative `CardId`
    list, over every decomp card class -- not just the fixtures above.

    For every decomp `Cards/*.cs` stem, asserts `card_owner_id(stem)` is
    one of the real `to_python_name()` strings in `card_ids_generated.rs`
    -- covering both the plain-stem case and the ~68 Card/Status-suffix
    collisions, since `card_owner_id` now resolves both the same way. A
    stem in `_CARD_STEMS_WITH_NO_CARD_ID` is checked the other way: it
    must NOT resolve under any of the three tried suffixes, so that list
    can't quietly go stale either. Anything else that fails to resolve --
    a genuinely new naming pattern `card_owner_id` doesn't handle -- fails
    this test loudly instead of silently joining to nothing.
    """

    def test_card_owner_ids_resolve_against_card_ids_generated_rs(self) -> None:
        python_names = _card_id_python_names()
        checked_ok = 0
        for cs_file in sorted(_CARDS_DIR.glob("*.cs")):
            stem = cs_file.stem
            if stem in _CARD_STEMS_WITH_NO_CARD_ID:
                self.assertTrue(
                    all(stem + suffix not in python_names for suffix in ("", "Card", "Status")),
                    f"{stem!r} is listed as variantless but a CardId variant "
                    "exists for it now -- drop it from _CARD_STEMS_WITH_NO_CARD_ID",
                )
                continue
            self.assertIn(card_owner_id(stem), python_names.values(), stem)
            checked_ok += 1
        # Sanity: this should be checking the overwhelming majority of
        # cards, not silently degrading to a near-empty loop.
        self.assertGreater(checked_ok, 500)


_MONSTERS_DIR = _STS2_WIKI / "decompiled" / "v0.107.1" / "MegaCrit.Sts2.Core.Models.Monsters"


@unittest.skipUnless(_MONSTERS_DIR.is_dir(), f"decomp Monsters dir not present ({_MONSTERS_DIR})")
class MonsterMoveBodyResolutionTest(unittest.TestCase):
    """Confirms the loud failure `parse_monster_file` now raises (review
    round 3, see its module docstring) never actually fires on real
    content -- every `MoveState` in every real monster file resolves to a
    method body. `subTest` reports which file failed rather than stopping
    at the first one.
    """

    def test_every_real_monster_file_parses_without_a_dropped_move_state(self) -> None:
        checked = 0
        for cs_file in sorted(_MONSTERS_DIR.glob("*.cs")):
            with self.subTest(monster=cs_file.stem):
                parse_monster_file(cs_file.stem, cs_file.read_text())
            checked += 1
        self.assertGreater(checked, 50)


if __name__ == "__main__":
    unittest.main()


class ParseDeathFadeTest(unittest.TestCase):
    """`parse_death_fade` — the two `MonsterModel` death overrides that decide
    whether a dying monster gets a dissolve at all (`NCreature.cs:1106`) and
    how long the game waits for its death animation (`:1093`).
    """

    def test_a_monster_overriding_nothing_gets_no_row(self) -> None:
        # The overwhelming majority. The table carries only the exceptions, so
        # "absent" has to mean "takes MonsterModel's defaults" unambiguously.
        self.assertIsNone(parse_death_fade("Cultist", "public sealed class Cultist { }"))

    def test_a_literal_false_reads_as_never(self) -> None:
        row = parse_death_fade(
            "Fabricator", "\tpublic override bool ShouldFadeAfterDeath => false;\n"
        )
        self.assertEqual(row, {"fade": "never"})

    def test_a_non_constant_expression_is_recorded_as_conditional_with_its_text(self) -> None:
        # `WaterfallGiant.cs:157`. Flattening this to a bool either way would be
        # wrong: `false` is a lie and `true` hides it. The expression rides
        # along so whoever implements it does not have to re-find it.
        row = parse_death_fade(
            "WaterfallGiant",
            "\tpublic override bool ShouldFadeAfterDeath => PressureBuildupIdx == 0;\n",
        )
        self.assertEqual(row, {"fade": "conditional", "condition": "PressureBuildupIdx == 0"})

    def test_the_death_wait_override_is_carried_alongside(self) -> None:
        row = parse_death_fade(
            "Crusher",
            "\tpublic override bool ShouldFadeAfterDeath => false;\n"
            "\tpublic override float DeathAnimLengthOverride => 2.5f;\n",
        )
        self.assertEqual(row, {"fade": "never", "deathAnimSeconds": 2.5})

    def test_a_zero_death_wait_is_the_default_and_not_recorded(self) -> None:
        # `HasDeathAnimLengthOverride` is `DeathAnimLengthOverride > 0f`, so a
        # literal zero means "no override" to the game and must mean the same
        # here — recording it would make the renderer wait 0 s instead of for
        # the rig.
        self.assertIsNone(
            parse_death_fade("Whatever", "\tpublic override float DeathAnimLengthOverride => 0f;\n")
        )

    def test_the_base_class_virtual_declaration_is_not_read_as_an_override(self) -> None:
        # `MonsterModel.cs:311` is `public virtual bool ShouldFadeAfterDeath =>
        # true;`. Matching it would put the base class in the table as though it
        # were a monster.
        self.assertIsNone(
            parse_death_fade(
                "MonsterModel", "\tpublic virtual bool ShouldFadeAfterDeath => true;\n"
            )
        )

    def test_an_explicit_true_override_is_still_recorded(self) -> None:
        # Redundant with the base class, but recording it keeps the
        # completeness gate honest: an override that parsed to nothing is what
        # `mentions_death_override` exists to catch.
        self.assertEqual(
            parse_death_fade("Loud", "\tpublic override bool ShouldFadeAfterDeath => true;\n"),
            {"fade": "always"},
        )

    def test_mentions_death_override_is_looser_than_the_parsers(self) -> None:
        # The completeness gate's tripwire: a shape the regexes miss must still
        # register as "this file talks about it", so the extractor raises
        # instead of silently reporting the defaults.
        self.assertTrue(
            mentions_death_override("bool ShouldFadeAfterDeath { get { return false; } }")
        )
        self.assertFalse(mentions_death_override("public sealed class Cultist { }"))
