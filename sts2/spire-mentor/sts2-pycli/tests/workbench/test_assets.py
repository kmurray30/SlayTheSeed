import json
from pathlib import Path

from workbench.assets import AssetResolver, slugify


def test_slugify():
    assert slugify("Twin Strike") == "twin_strike"
    assert slugify("Scroll of Biting!") == "scroll_of_biting"
    assert slugify("The Necrobinder") == "necrobinder"  # leading 'the' dropped


def test_resolver_finds_existing_card(tmp_path: Path):
    root = tmp_path
    (root / "card_atlas" / "ironclad").mkdir(parents=True)
    (root / "card_atlas" / "ironclad" / "bash.png").write_bytes(b"x")
    r = AssetResolver(root)
    assert r.card("Ironclad", "CARD.BASH", "Bash") == "card_atlas/ironclad/bash.png"


def test_resolver_returns_none_when_missing(tmp_path: Path):
    r = AssetResolver(tmp_path)
    assert r.card("Ironclad", "CARD.NOPE", "Nope") is None
    assert r.monster("Ghost") is None


def test_resolver_finds_cross_character_card(tmp_path: Path):
    # A run can hold a card from another character (e.g. an Ironclad run picks
    # up the Defect card Claw via a relic). Its art lives under the DEFECT atlas
    # folder, not ironclad's, so the resolver must search the card's OWN
    # character folder, supplied via ``card_character``.
    root = tmp_path
    (root / "card_atlas" / "defect").mkdir(parents=True)
    (root / "card_atlas" / "defect" / "claw.png").write_bytes(b"x")
    r = AssetResolver(root)
    assert (
        r.card("Ironclad", "CARD.CLAW", "Claw", card_character="Defect")
        == "card_atlas/defect/claw.png"
    )


def test_cross_character_collision_prefers_card_character(tmp_path: Path):
    # "scavenge.png" exists under both defect/ and necrobinder/. When the card's
    # own character is known, it must win over a blind all-folders scan so the
    # correct art is chosen.
    root = tmp_path
    for folder in ("defect", "necrobinder"):
        (root / "card_atlas" / folder).mkdir(parents=True)
        (root / "card_atlas" / folder / "scavenge.png").write_bytes(b"x")
    r = AssetResolver(root)
    assert (
        r.card("Necrobinder", "CARD.SCAVENGE", "Scavenge", card_character="Defect")
        == "card_atlas/defect/scavenge.png"
    )


def test_cross_character_fallback_scans_all_folders(tmp_path: Path):
    # When the card's character is unknown (e.g. a card missing from the pinned
    # localization manifest), fall back to scanning every character folder so the
    # image still resolves instead of rendering blank.
    root = tmp_path
    (root / "card_atlas" / "defect").mkdir(parents=True)
    (root / "card_atlas" / "defect" / "beam_cell.png").write_bytes(b"x")
    r = AssetResolver(root)
    assert r.card("Ironclad", "CARD.BEAM_CELL", "Beam Cell") == "card_atlas/defect/beam_cell.png"


def test_cross_character_fallback_works_in_manifest_mode(tmp_path: Path):
    # In manifest mode (lean Cloud Run path) existence is a set-membership check
    # with no filesystem to glob, so the all-folders fallback must derive the
    # candidate folders from the manifest itself.
    manifest = {"card_atlas/defect/beam_cell.png"}
    r = AssetResolver(tmp_path, manifest=manifest)
    assert r.card("Ironclad", "CARD.BEAM_CELL", "Beam Cell") == "card_atlas/defect/beam_cell.png"


def test_monster_alias_resolves_flyconid(tmp_path: Path):
    # Flyconid's sprite ships as flying_mushrooms.png; the alias maps the
    # display-name slug to the real file so its combat image isn't blank.
    (tmp_path / "monsters").mkdir()
    (tmp_path / "monsters" / "flying_mushrooms.png").write_bytes(b"x")
    r = AssetResolver(tmp_path)
    assert r.monster("Flyconid") == "monsters/flying_mushrooms.png"


def test_monster_alias_resolves_ruby_raiders(tmp_path: Path):
    # The Ruby Raiders display as "<Role> Raider" but their sprites keep the
    # internal "<role>_ruby_raider" key, so the alias bridges the two.
    (tmp_path / "monsters").mkdir()
    for slug in ("axe", "tracker", "brute", "crossbow", "assassin"):
        (tmp_path / "monsters" / f"{slug}_ruby_raider.png").write_bytes(b"x")
    r = AssetResolver(tmp_path)
    assert r.monster("Axe Raider") == "monsters/axe_ruby_raider.png"
    assert r.monster("Tracker Raider") == "monsters/tracker_ruby_raider.png"
    assert r.monster("Brute Raider") == "monsters/brute_ruby_raider.png"
    assert r.monster("Crossbow Raider") == "monsters/crossbow_ruby_raider.png"
    assert r.monster("Assassin Raider") == "monsters/assassin_ruby_raider.png"


def test_mysterious_knight_uses_flail_knight_art(tmp_path: Path):
    # MysteriousKnight is a code subclass of FlailKnight (no animator override),
    # so it renders with the flail_knight rig/sprite; its own display slug
    # ("mysterious_knight") has no art. The alias bridges both.
    (tmp_path / "monsters").mkdir()
    (tmp_path / "monsters" / "flail_knight.png").write_bytes(b"x")
    rig = tmp_path / "monster_rigs" / "flail_knight"
    rig.mkdir(parents=True)
    (rig / "flail_knight.skel").write_bytes(b"x")
    r = AssetResolver(tmp_path)
    assert r.monster("Mysterious Knight") == "monsters/flail_knight.png"
    assert (
        r.monster_rig("Mysterious Knight", "MysteriousKnight")
        == "monster_rigs/flail_knight/flail_knight"
    )


def test_fake_merchant_uses_fake_merchant_monster_art(tmp_path: Path):
    # FakeMerchantMonster ("The Merchant???") ships its combat rig under the
    # slug fake_merchant_monster (matching its creature_scales key); its display
    # slug "merchant" has no art, so the alias bridges name/kind → rig.
    rig = tmp_path / "monster_rigs" / "fake_merchant_monster"
    rig.mkdir(parents=True)
    (rig / "fake_merchant_monster.skel").write_bytes(b"x")
    r = AssetResolver(tmp_path)
    assert (
        r.monster_rig("The Merchant???", "FakeMerchantMonster")
        == "monster_rigs/fake_merchant_monster/fake_merchant_monster"
    )


def test_monster_rig_scale_describes_the_rig_that_is_sent(tmp_path: Path):
    # "Crusher" and "Rocket" are the Kaiser Crab's two arms: separate creature
    # scenes with their own authored scales, whose visible body is the ONE
    # shared kaiser_crab rig they reach through the alias table. ``scale`` is px
    # per RIG unit, so pairing the crab rig with the arm's 1.5 drew the boss at
    # three times its size, claws off both edges of the screen. The scale has to
    # come from whatever rig ``monster_rig`` actually resolved to.
    (tmp_path / "monster_rigs" / "kaiser_crab").mkdir(parents=True)
    (tmp_path / "monster_rigs" / "kaiser_crab" / "kaiser_crab.skel").write_bytes(b"x")
    (tmp_path / "creature_scales.json").write_text(
        json.dumps(
            {
                "crusher": {"scale": 1.5, "bounds_h": 366.0},
                "rocket": {"scale": -1.5, "bounds_h": 331.0},
                "kaiser_crab": {"scale": 0.5, "bounds_h": None},
                "nibbit": {"scale": 0.28, "bounds_h": 146.0},
            }
        ),
        encoding="utf-8",
    )
    r = AssetResolver(tmp_path)
    assert r.monster_rig("Crusher") == "monster_rigs/kaiser_crab/kaiser_crab"
    # The RIG's scale, the ARM's own Bounds box (which is what the row spaces by).
    assert r.monster_rig_scale("Crusher") == {"scale": 0.5, "bounds_h": 366.0}
    assert r.monster_rig_scale("Rocket") == {"scale": 0.5, "bounds_h": 331.0}
    # A monster with no staged rig still reports its own authored scale.
    assert r.monster_rig_scale("Nibbit") == {"scale": 0.28, "bounds_h": 146.0}


def test_monster_rig_scale_keeps_a_variant_own_scale(tmp_path: Path):
    # The narrow case above must NOT generalise. A creature that draws a shared
    # rig at its own authored scale is doing that deliberately: the Mysterious
    # Knight draws the Flail Knight's rig at 0.17 against its 0.14 and is that
    # much bigger (bounds 437 against 372). Only a shared-SKELETON group — where
    # one rig is several creatures — takes the rig's scale.
    (tmp_path / "monster_rigs" / "flail_knight").mkdir(parents=True)
    (tmp_path / "monster_rigs" / "flail_knight" / "flail_knight.skel").write_bytes(b"x")
    (tmp_path / "creature_scales.json").write_text(
        json.dumps(
            {
                "mysterious_knight": {"scale": 0.17, "bounds_h": 437.0},
                "flail_knight": {"scale": 0.14, "bounds_h": 372.0},
            }
        ),
        encoding="utf-8",
    )
    r = AssetResolver(tmp_path)
    assert r.monster_rig("Mysterious Knight") == "monster_rigs/flail_knight/flail_knight"
    assert r.monster_rig_scale("Mysterious Knight") == {"scale": 0.17, "bounds_h": 437.0}


def test_monster_rig_group_kaiser_crab_arms(tmp_path: Path):
    # Crusher (left arm) and Rocket (right arm) share the one big Kaiser Crab rig;
    # the resolver reports the shared group + the animation-track family each
    # drives, so the frontend renders the rig once and pins each arm's overlay
    # over its claw. Ordinary monsters report no group.
    r = AssetResolver(tmp_path)
    assert r.monster_rig_group("Crusher") == {"group": "kaiser_crab", "part": "left"}
    assert r.monster_rig_group("Rocket") == {"group": "kaiser_crab", "part": "right"}
    assert r.monster_rig_group("Nibbit") is None
    assert r.monster_rig_group("") is None
    # Decimillipede segments group by kind (all share the name "Decimillipede").
    assert r.monster_rig_group("Decimillipede", "DecimillipedeSegmentFront") == {
        "group": "decimillipede",
        "part": "front",
    }
    assert r.monster_rig_group("Decimillipede", "DecimillipedeSegmentBack") == {
        "group": "decimillipede",
        "part": "back",
    }
    assert r.monster_rig_group("Decimillipede") is None  # name alone can't tell


def test_monster_rig_by_kind_decimillipede_segments(tmp_path: Path):
    # All three Decimillipede segments localize to the one name "Decimillipede",
    # so the rig must be picked by the enemy's `kind` (MonsterKind). Each segment
    # has its own rig; a name-only lookup can't disambiguate and finds nothing.
    for part in ("front", "middle", "back"):
        d = tmp_path / "monster_rigs" / f"decimillipede_segment_{part}"
        d.mkdir(parents=True)
        (d / f"decimillipede_segment_{part}.skel").write_bytes(b"x")
    r = AssetResolver(tmp_path)
    assert (
        r.monster_rig("Decimillipede", "DecimillipedeSegmentFront")
        == "monster_rigs/decimillipede_segment_front/decimillipede_segment_front"
    )
    assert (
        r.monster_rig("Decimillipede", "DecimillipedeSegmentBack")
        == "monster_rigs/decimillipede_segment_back/decimillipede_segment_back"
    )
    # Name alone (no kind) can't pick a segment.
    assert r.monster_rig("Decimillipede") is None
    # An unknown kind falls back to name resolution (still None here).
    assert r.monster_rig("Decimillipede", "SomethingElse") is None


def test_relic_and_potion(tmp_path: Path):
    (tmp_path / "relic_atlas").mkdir()
    (tmp_path / "relic_atlas" / "akabeko.png").write_bytes(b"x")
    (tmp_path / "potion_atlas").mkdir()
    (tmp_path / "potion_atlas" / "fire_potion.png").write_bytes(b"x")
    r = AssetResolver(tmp_path)
    assert r.relic("Akabeko") == "relic_atlas/akabeko.png"
    assert r.potion("Fire Potion") == "potion_atlas/fire_potion.png"
    assert r.relic("Nonexistent") is None


def test_relic_power_potion_keep_a_leading_the(tmp_path: Path):
    """Art trees that keep the article still resolve.

    ``slugify`` drops a leading "the", but ``relic_atlas`` / ``power_atlas``
    keep it, so a single-form lookup left The Boot, The Abacus, The Courier and
    four powers with no icon at all.
    """
    (tmp_path / "relic_atlas").mkdir()
    (tmp_path / "relic_atlas" / "the_boot.png").write_bytes(b"x")
    (tmp_path / "relic_atlas" / "fake_merchants_rug.png").write_bytes(b"x")
    (tmp_path / "power_atlas").mkdir()
    (tmp_path / "power_atlas" / "the_bomb_power.png").write_bytes(b"x")
    (tmp_path / "power_atlas" / "strength_power.png").write_bytes(b"x")
    (tmp_path / "potion_atlas").mkdir()
    (tmp_path / "potion_atlas" / "clarity.png").write_bytes(b"x")
    r = AssetResolver(tmp_path)
    assert r.relic("The Boot") == "relic_atlas/the_boot.png"
    assert r.power("The Bomb") == "power_atlas/the_bomb_power.png"
    # The stripped form still wins where the file has no article.
    assert r.power("Strength") == "power_atlas/strength_power.png"
    # Alias tables cover the two names that match neither form.
    assert r.relic("The Merchant's Rug???") == "relic_atlas/fake_merchants_rug.png"
    assert r.potion("Clarity Extract") == "potion_atlas/clarity.png"


def test_orb(tmp_path: Path):
    # Defect orb icons live under orb_atlas/<type>.png, keyed by the lowercased
    # orb type (the C# class name minus "Orb").
    (tmp_path / "orb_atlas").mkdir()
    for slug in ("lightning", "frost", "dark", "plasma", "glass"):
        (tmp_path / "orb_atlas" / f"{slug}.png").write_bytes(b"x")
    r = AssetResolver(tmp_path)
    assert r.orb("Lightning") == "orb_atlas/lightning.png"
    assert r.orb("Frost") == "orb_atlas/frost.png"
    assert r.orb("Plasma") == "orb_atlas/plasma.png"
    assert r.orb("Nonexistent") is None


def test_event_illustration(tmp_path: Path):
    events = tmp_path / "events"
    events.mkdir()
    (events / "this_or_that.png").write_bytes(b"x")
    (events / "the_future_of_potions.png").write_bytes(b"x")  # file keeps leading 'the_'
    (events / "welcome_to_wongos.png").write_bytes(b"x")  # aliased
    r = AssetResolver(tmp_path)
    # direct slugify match
    assert r.event_illustration("This or That?") == "events/this_or_that.png"
    # 'the_' kept in filename but slugify strips it -> raw (un-stripped) form matches
    assert r.event_illustration("The Future of Potions?") == "events/the_future_of_potions.png"
    # alias table
    assert r.event_illustration("Welcome to Wongo's") == "events/welcome_to_wongos.png"
    # no art -> None (fallback panel)
    assert r.event_illustration("The Architect") is None
    assert r.event_illustration("") is None


def test_room_icon_common_types(tmp_path: Path):
    rh = tmp_path / "run_history"
    rh.mkdir()
    for slug in ("monster", "elite", "shop", "rest_site", "treasure", "event", "ancient"):
        (rh / f"{slug}.png").write_bytes(b"x")
    r = AssetResolver(tmp_path)
    assert r.room_icon("Monster") == "run_history/monster.png"
    assert r.room_icon("Elite") == "run_history/elite.png"
    assert r.room_icon("Shop") == "run_history/shop.png"
    assert r.room_icon("RestSite") == "run_history/rest_site.png"
    assert r.room_icon("Treasure") == "run_history/treasure.png"
    assert r.room_icon("Event") == "run_history/event.png"
    assert r.room_icon("Ancient") == "run_history/ancient.png"
    # UI aliases reuse the same badge.
    assert r.room_icon("MegaElite") == "run_history/elite.png"
    assert r.room_icon("Rest") == "run_history/rest_site.png"
    # Unknown / None / unmapped -> None (frontend keeps its glyph).
    assert r.room_icon("Unknown") is None
    assert r.room_icon(None) is None
    assert r.room_icon("Boss") is None  # Boss needs an id


def test_room_icon_boss_by_id(tmp_path: Path):
    rh = tmp_path / "run_history"
    rh.mkdir()
    # Filenames always carry the ``_boss`` suffix; encounter ids may or may not.
    (rh / "vantom_boss.png").write_bytes(b"x")
    (rh / "the_insatiable_boss.png").write_bytes(b"x")
    r = AssetResolver(tmp_path)
    # bare id -> the ``_boss``-suffixed candidate matches
    assert r.room_icon("Boss", "VANTOM") == "run_history/vantom_boss.png"
    # id already carrying ``_boss`` -> exact match wins
    assert r.room_icon("Boss", "THE_INSATIABLE_BOSS") == "run_history/the_insatiable_boss.png"
    # unknown boss id / no id -> None
    assert r.room_icon("Boss", "MYSTERY_BOSS") is None
    assert r.room_icon("Boss", None) is None


def test_monster_kind_accepts_both_producers_spellings(tmp_path: Path):
    # THE TWO WIRES SPELL THE KIND DIFFERENTLY. The engine emits the PascalCase
    # MonsterKind (`state_dict.rs`: species.python_name()); SpireMentor emits the
    # game's own `Creature.Monster.Id.Entry`, which is SCREAMING_SNAKE (verified
    # against the raw v2 captures in logs/realgame/). Keying only on PascalCase
    # silently no-op'd the whole kind-addressed path for every replayed live
    # run: the Decimillipede drew as three sprite fallbacks there while
    # rendering correctly in a workbench-played run.
    for part in ("front", "middle", "back"):
        d = tmp_path / "monster_rigs" / f"decimillipede_segment_{part}"
        d.mkdir(parents=True)
        (d / f"decimillipede_segment_{part}.skel").write_bytes(b"x")
    r = AssetResolver(tmp_path)
    for kind in ("DecimillipedeSegmentFront", "DECIMILLIPEDE_SEGMENT_FRONT"):
        assert (
            r.monster_rig("Decimillipede", kind)
            == "monster_rigs/decimillipede_segment_front/decimillipede_segment_front"
        )
        assert r.monster_rig_group("Decimillipede", kind) == {
            "group": "decimillipede",
            "part": "front",
        }


def test_normalize_kind_leaves_a_pascal_kind_alone():
    from workbench.assets import normalize_kind

    # Anything carrying a lowercase letter is already a MonsterKind, so the
    # normalizer can never rewrite a name it does not recognise.
    assert normalize_kind("DecimillipedeSegmentFront") == "DecimillipedeSegmentFront"
    assert normalize_kind("TestSubject") == "TestSubject"
    assert normalize_kind("DECIMILLIPEDE_SEGMENT_BACK") == "DecimillipedeSegmentBack"
    assert normalize_kind("TWIG_SLIME_S") == "TwigSlimeS"
    assert normalize_kind("") is None
    assert normalize_kind(None) is None


def test_power_big_resolves_the_vfx_icon_and_keeps_the_article(tmp_path: Path):
    """``power_big`` is a SEPARATE tree from ``power_atlas``.

    The game keeps two icons per power: the 64x64 tray sprite
    (``PackedIconPath``) and the 256x256 ``BigIcon`` the power VFX draw. Both
    resolve through the same ``slug_forms`` article handling.
    """
    (tmp_path / "power_atlas").mkdir()
    (tmp_path / "power_atlas" / "strength_power.png").write_bytes(b"x")
    (tmp_path / "power_big").mkdir()
    (tmp_path / "power_big" / "strength_power.png").write_bytes(b"x")
    (tmp_path / "power_big" / "the_bomb_power.png").write_bytes(b"x")
    r = AssetResolver(tmp_path)
    assert r.power_big("Strength") == "power_big/strength_power.png"
    assert r.power_big("The Bomb") == "power_big/the_bomb_power.png"
    # The two trees stay independent — the tray icon is NOT what this returns.
    assert r.power("Strength") == "power_atlas/strength_power.png"


def test_power_big_falls_back_to_missing_power_like_the_game_does(tmp_path: Path):
    """``PowerModel.ResolvedBigIconPath`` ends at ``missing_power.png``.

    Not a defensive nicety: 14 powers ship a tray icon and no big icon in the
    v0.107.1 pck, and the game shows the placeholder for exactly those.
    """
    (tmp_path / "power_big").mkdir()
    (tmp_path / "power_big" / "missing_power.png").write_bytes(b"x")
    r = AssetResolver(tmp_path)
    assert r.power_big("Grapple") == "power_big/missing_power.png"


def test_power_big_is_none_when_the_art_tree_predates_the_pass(tmp_path: Path):
    """An art tree with no ``power_big`` at all must yield None, so the web
    side falls back to the tray icon rather than requesting a 404."""
    (tmp_path / "power_atlas").mkdir()
    (tmp_path / "power_atlas" / "strength_power.png").write_bytes(b"x")
    assert AssetResolver(tmp_path).power_big("Strength") is None
