"""Verify ``_enrich`` attaches art URLs for non-combat decision states."""

from pathlib import Path

from workbench.assets import AssetResolver
from workbench.server import _enrich


def _resolver(tmp_path: Path) -> AssetResolver:
    """A resolver over a root pre-populated with the few files these tests touch."""
    (tmp_path / "relic_atlas").mkdir(exist_ok=True)
    (tmp_path / "relic_atlas" / "akabeko.png").write_bytes(b"x")
    (tmp_path / "relic_atlas" / "winged_boots.png").write_bytes(b"x")
    (tmp_path / "potion_atlas").mkdir(exist_ok=True)
    (tmp_path / "potion_atlas" / "fire_potion.png").write_bytes(b"x")
    (tmp_path / "events").mkdir(exist_ok=True)
    (tmp_path / "events" / "this_or_that.png").write_bytes(b"x")
    return AssetResolver(tmp_path)


def _step(state: dict) -> dict:
    # _enrich reads the decision from the top level of the step (mirrors Step shape).
    return {"decision": state.get("decision"), "state": state, "chosen_action": None}


def test_shop_enriches_relic_and_potion(tmp_path: Path):
    step = _step(
        {
            "decision": "shop",
            "player": {"name": "Ironclad"},
            "cards": [],
            "relics": [{"name": "Akabeko", "index": 0}],
            "potions": [{"name": "Fire Potion", "index": 0}],
        }
    )
    out = _enrich(step, _resolver(tmp_path), {})
    assert out["state"]["relics"][0]["image"] == "/assets/relic_atlas/akabeko.png"
    assert out["state"]["potions"][0]["image"] == "/assets/potion_atlas/fire_potion.png"


def test_shop_resolves_loc_key_names(tmp_path: Path):
    # The Rust backend ships relic/potion names as loc keys; _enrich must
    # resolve them to display names (used for the art-slug lookup too).
    loc = {"JUZU_BRACELET.title": "Juzu Bracelet", "FIRE_POTION.title": "Fire Potion"}
    (tmp_path / "relic_atlas" / "juzu_bracelet.png").parent.mkdir(exist_ok=True)
    (tmp_path / "relic_atlas" / "juzu_bracelet.png").write_bytes(b"x")
    step = _step(
        {
            "decision": "shop",
            "player": {"name": "Ironclad"},
            "cards": [],
            "relics": [{"name": "JUZU_BRACELET.title", "index": 0}],
            "potions": [{"name": "FIRE_POTION.title", "index": 0}],
        }
    )
    out = _enrich(step, _resolver(tmp_path), loc)
    assert out["state"]["relics"][0]["name"] == "Juzu Bracelet"
    assert out["state"]["relics"][0]["image"] == "/assets/relic_atlas/juzu_bracelet.png"
    assert out["state"]["potions"][0]["name"] == "Fire Potion"
    assert out["state"]["potions"][0]["image"] == "/assets/potion_atlas/fire_potion.png"


def test_shop_describes_relics_and_potions(tmp_path: Path):
    # The shop is where you decide whether an item is worth your gold, so it
    # has to say what the item DOES. It used to resolve name + art only and
    # leave ``description`` off the wire entirely, so the frontend's ware
    # tooltip showed a bare name.
    #
    # The Rust backend ships no ``vars`` for a relic/potion, so the text comes
    # from the wiki's pre-resolved ``description_plain`` — keyed off the item's
    # ``id``, exactly as the player's own relic tray already resolves it.
    loc = {
        "JUZU_BRACELET.title": "Juzu Bracelet",
        "JUZU_BRACELET.description_plain": "Enemy attacks that would kill you leave you at 1 HP.",
        "FIRE_POTION.title": "Fire Potion",
        "FIRE_POTION.description_plain": "Deal 20 damage to an enemy.",
    }
    step = _step(
        {
            "decision": "shop",
            "player": {"name": "Ironclad"},
            "cards": [],
            "relics": [{"id": "JUZU_BRACELET", "name": "JUZU_BRACELET.title", "index": 0}],
            "potions": [{"id": "FIRE_POTION", "name": "FIRE_POTION.title", "index": 0}],
        }
    )
    out = _enrich(step, _resolver(tmp_path), loc)
    assert (
        out["state"]["relics"][0]["description"]
        == "Enemy attacks that would kill you leave you at 1 HP."
    )
    assert out["state"]["potions"][0]["description"] == "Deal 20 damage to an enemy."


def test_shop_keeps_a_backend_supplied_description(tmp_path: Path):
    # The C# backend ships the item's own already-formatted text plus a
    # ``vars`` dict; that must survive the shop's enrichment rather than being
    # replaced by (or blanked in favour of) the wiki fallback.
    step = _step(
        {
            "decision": "shop",
            "player": {"name": "Ironclad"},
            "cards": [],
            "relics": [
                {
                    "id": "AKABEKO",
                    "name": "Akabeko",
                    "description": "At the start of each combat, gain {Vigor} Vigor.",
                    "vars": {"Vigor": 8},
                    "index": 0,
                }
            ],
            "potions": [],
        }
    )
    out = _enrich(step, _resolver(tmp_path), {})
    assert "8" in out["state"]["relics"][0]["description"]


def test_shop_card_energy_badge_backfilled(tmp_path: Path):
    # In the shop, ``cost`` is the gold price; the card frame reads its energy
    # badge from ``card_cost``. The Rust backend ships neither an energy cost
    # nor ``card_cost`` for shop cards, so _enrich must backfill the energy cost
    # from the manifest — otherwise every shop card renders as 0-cost.
    loc = {"TWIN_STRIKE.cost": "1", "WHIRLWIND.cost": "X"}
    step = _step(
        {
            "decision": "shop",
            "player": {"name": "Ironclad"},
            "cards": [
                {"id": "TWIN_STRIKE", "name": "Twin Strike", "cost": 50, "index": 0},
                {"id": "WHIRLWIND", "name": "Whirlwind", "cost": 75, "index": 1},
            ],
            "relics": [],
            "potions": [],
        }
    )
    out = _enrich(step, _resolver(tmp_path), loc)
    cards = out["state"]["cards"]
    # ``cost`` stays the gold price; ``card_cost`` carries the energy cost.
    assert cards[0]["cost"] == 50
    assert cards[0]["card_cost"] == 1
    assert cards[1]["cost"] == 75
    assert cards[1]["card_cost"] == "X"


def test_treasure_resolves_loc_key_name(tmp_path: Path):
    loc = {"AKABEKO.title": "Akabeko"}
    step = _step({"decision": "treasure", "relics": [{"name": "AKABEKO.title", "index": 0}]})
    out = _enrich(step, _resolver(tmp_path), loc)
    assert out["state"]["relics"][0]["name"] == "Akabeko"
    assert out["state"]["relics"][0]["image"] == "/assets/relic_atlas/akabeko.png"


def test_event_attaches_illustration(tmp_path: Path):
    step = _step(
        {
            "decision": "event_choice",
            "event_name": "This or That?",
            "description": "Arms jut out.",
            "options": [{"title": "This", "description": "Lose 6 HP.", "index": 0}],
        }
    )
    out = _enrich(step, _resolver(tmp_path), {})
    assert out["state"]["art"]["illustration"] == "/assets/events/this_or_that.png"
    # rich per-option payload survives under event_options (state.options gets
    # overwritten by the normalized action list in _live_step).
    assert out["state"]["event_options"][0]["title"] == "This"
    assert out["state"]["event_options"][0]["description"] == "Lose 6 HP."
    assert out["state"]["event_options"][0]["index"] == 0


def test_event_resolves_template_vars(tmp_path: Path):
    # The game ships option text as templates with {VarName} placeholders plus a
    # per-option ``vars`` dict; _enrich must substitute the actual numbers.
    step = _step(
        {
            "decision": "event_choice",
            "event_name": "Jungle Maze Adventure",
            "description": "You reach a maze.",
            "options": [
                {
                    "title": "Solo Quest",
                    "description": "Gain {SoloGold} Gold. Lose {SoloHp} HP.",
                    "vars": {"SoloGold": 149, "SoloHp": 18, "JoinForcesGold": 35},
                    "index": 0,
                },
                {
                    "title": "Join Forces",
                    "description": "Gain {JoinForcesGold} Gold.",
                    "vars": {"SoloGold": 149, "SoloHp": 18, "JoinForcesGold": 35},
                    "index": 1,
                },
            ],
        }
    )
    out = _enrich(step, _resolver(tmp_path), {})
    opts = out["state"]["event_options"]
    assert opts[0]["description"] == "Gain 149 Gold. Lose 18 HP."
    assert opts[1]["description"] == "Gain 35 Gold."
    # No unresolved braces should remain.
    assert "{" not in opts[0]["description"]


def test_event_relic_option_uses_plain_description(tmp_path: Path):
    # An event offers a relic as an option carrying NO localized description —
    # only a ``text_key`` whose tail is the relic id (the Neow / Ancient-event
    # shape). _enrich must fill the option text from the wiki's pre-resolved
    # plain description, NOT the raw ``{Cards}`` / ``{Damage}`` template, which
    # would leak bare variable names ("remove Cards cards … lose Damage HP")
    # since the option's ``vars`` dict can't resolve a relic's own variables.
    loc = {
        "PRECARIOUS_SHEARS.description": "Upon pickup, remove [blue]{Cards}[/blue] "
        "{Cards:plural:card|cards} from your [gold]Deck[/gold] and lose [blue]{Damage}[/blue] HP.",
        "PRECARIOUS_SHEARS.description_plain": "Upon pickup, remove [blue]2[/blue] cards "
        "from your [gold]Deck[/gold] and lose [blue]16[/blue] HP.",
    }
    step = _step(
        {
            "decision": "event_choice",
            "event_name": "Ancient Writing",
            "options": [
                {"text_key": "ANCIENT.pages.INITIAL.options.PRECARIOUS_SHEARS", "index": 0}
            ],
        }
    )
    out = _enrich(step, _resolver(tmp_path), loc)
    desc = out["state"]["event_options"][0]["description"]
    assert desc == "Upon pickup, remove 2 cards from your Deck and lose 16 HP."
    assert "Cards" not in desc and "Damage" not in desc and "{" not in desc


def test_event_missing_illustration_is_none(tmp_path: Path):
    step = _step({"decision": "event_choice", "event_name": "The Architect", "options": []})
    out = _enrich(step, _resolver(tmp_path), {})
    assert out["state"]["art"]["illustration"] is None


def test_treasure_enriches_relic(tmp_path: Path):
    step = _step({"decision": "treasure", "relics": [{"name": "Akabeko", "index": 0}]})
    out = _enrich(step, _resolver(tmp_path), {})
    assert out["state"]["relics"][0]["image"] == "/assets/relic_atlas/akabeko.png"


def test_owned_relic_uses_plain_description_without_vars(tmp_path: Path):
    # The Rust backend ships a relic as a loc key with no ``vars`` dict, so the
    # ``{Relics}`` template can't be substituted; _enrich must fall back to the
    # wiki's pre-resolved plain description instead of leaking "Relics".
    loc = {
        "LARGE_CAPSULE.title": "Large Capsule",
        "LARGE_CAPSULE.description": "Upon pickup, obtain {Relics} random "
        "{Relics:plural:Relic|Relics}.",
        "LARGE_CAPSULE.description_plain": "Upon pickup, obtain [blue]2[/blue] random "
        "[gold]Relics[/gold].",
    }
    step = _step(
        {
            "decision": "shop",
            "player": {
                "name": "Ironclad",
                "relics": [
                    {
                        "id": "LARGE_CAPSULE",
                        "name": "LARGE_CAPSULE.title",
                        "description": "LARGE_CAPSULE.description",
                    }
                ],
            },
            "cards": [],
        }
    )
    out = _enrich(step, _resolver(tmp_path), loc)
    desc = out["state"]["player"]["relics"][0]["description"]
    assert desc == "Upon pickup, obtain 2 random Relics."
    assert "{" not in desc and "Relics random" not in desc


def test_owned_relic_keeps_vars_substitution(tmp_path: Path):
    # The C# backend ships a ``vars`` dict; that path must still win (live values)
    # and must not be replaced by the plain description.
    loc = {
        "AKABEKO.title": "Akabeko",
        "AKABEKO.description": "Gain {VigorPower} Vigor.",
        "AKABEKO.description_plain": "Gain 8 Vigor.",
    }
    step = _step(
        {
            "decision": "shop",
            "player": {
                "name": "Ironclad",
                "relics": [
                    {
                        "id": "AKABEKO",
                        "name": "AKABEKO.title",
                        "description": "AKABEKO.description",
                        "vars": {"VigorPower": 12},
                    }
                ],
            },
            "cards": [],
        }
    )
    out = _enrich(step, _resolver(tmp_path), loc)
    assert out["state"]["player"]["relics"][0]["description"] == "Gain 12 Vigor."


def test_combat_card_fills_missing_var_from_base_keeping_live_damage(tmp_path: Path):
    # In combat the Rust backend attaches only damage/block stats, so an extra
    # var like {VulnerablePower} would leak its bare name. _enrich fills it from
    # the manifest base value (var.vulnerable) while keeping the wire's live
    # damage — here a scaled 12, not the printed base 8.
    loc = {
        "CLAW.title": "Claw",
        "CLAW.description": "Deal {Damage:diff()} damage.\nApply {VulnerablePower:diff()} Vulnerable.",
        "CLAW.description_plain": "Deal 3 damage.\nApply 2 Vulnerable.",
        "CLAW.var.vulnerable": "2",
    }
    step = _step(
        {
            "decision": "combat_play",
            "player": {"name": "Ironclad"},
            "enemies": [],
            "hand": [
                {
                    "id": "CLAW",
                    "name": "CLAW.title",
                    "description": "CLAW.description",
                    "stats": {"damage": 12},
                }
            ],
        }
    )
    out = _enrich(step, _resolver(tmp_path), loc)
    desc = out["state"]["hand"][0]["description"]
    assert "VulnerablePower" not in desc
    # Live (scaled) damage kept; missing var filled from base.
    assert desc == "Deal 12 damage.\nApply 2 Vulnerable."


def test_combat_card_uses_plain_when_no_base_var(tmp_path: Path):
    # When a missing var has no manifest base value either, fall back to the
    # pre-resolved plain description rather than leak the bare name.
    loc = {
        "BASH.title": "Bash",
        "BASH.description": "Deal {Damage:diff()} damage.\nApply {VulnerablePower:diff()} Vulnerable.",
        "BASH.description_plain": "Deal 8 damage.\nApply 2 Vulnerable.",
    }
    step = _step(
        {
            "decision": "combat_play",
            "player": {"name": "Ironclad"},
            "enemies": [],
            "hand": [
                {
                    "id": "BASH",
                    "name": "BASH.title",
                    "description": "BASH.description",
                    "stats": {"damage": 8},
                }
            ],
        }
    )
    out = _enrich(step, _resolver(tmp_path), loc)
    desc = out["state"]["hand"][0]["description"]
    assert "VulnerablePower" not in desc
    assert desc == "Deal 8 damage.\nApply 2 Vulnerable."


def test_combat_card_keeps_template_when_stats_complete(tmp_path: Path):
    # The C# backend ships every DynamicVar in stats; the template path keeps the
    # live (possibly buffed) values rather than the plain base description.
    loc = {
        "BASH.title": "Bash",
        "BASH.description": "Deal {Damage:diff()} damage.\nApply {VulnerablePower:diff()} Vulnerable.",
        "BASH.description_plain": "Deal 8 damage.\nApply 2 Vulnerable.",
    }
    step = _step(
        {
            "decision": "combat_play",
            "player": {"name": "Ironclad"},
            "enemies": [],
            "hand": [
                {
                    "id": "BASH",
                    "name": "BASH.title",
                    "description": "BASH.description",
                    "stats": {"damage": 11, "vulnerablepower": 3},
                }
            ],
        }
    )
    out = _enrich(step, _resolver(tmp_path), loc)
    assert out["state"]["hand"][0]["description"] == "Deal 11 damage.\nApply 3 Vulnerable."


def test_non_combat_without_art_does_not_crash(tmp_path: Path):
    for decision in ("map_select", "rest_site", "bundle_select", "card_select"):
        step = _step({"decision": decision, "player": {"name": "Silent"}})
        _enrich(step, _resolver(tmp_path), {})  # must not raise


def test_neow_relic_option_gets_description_and_icon(tmp_path: Path):
    # NeowRelic options arrive with description=null and a text_key whose tail
    # is the relic id; _enrich must fall back to relic loc + relic art.
    loc = {"WINGED_BOOTS.description": "Ignore paths [blue]{Rooms}[/blue] times."}
    step = _step(
        {
            "decision": "event_choice",
            "event_name": "Neow",
            "description": "",
            "options": [
                {
                    "index": 0,
                    "title": "WINGED BOOTS",
                    "description": None,
                    "text_key": "NEOW.pages.INITIAL.options.WINGED_BOOTS",
                    "vars": {"Rooms": 3},
                }
            ],
        }
    )
    out = _enrich(step, _resolver(tmp_path), loc)
    opt = out["state"]["event_options"][0]
    assert opt["description"] == "Ignore paths 3 times."
    assert opt["image"] == "/assets/relic_atlas/winged_boots.png"


def test_event_option_without_text_key_has_null_image(tmp_path: Path):
    step = _step(
        {
            "decision": "event_choice",
            "event_name": "This or That?",
            "description": "A choice.",
            "options": [{"title": "This", "description": "Lose 6 HP.", "index": 0}],
        }
    )
    out = _enrich(step, _resolver(tmp_path), {})
    assert out["state"]["event_options"][0]["image"] is None
    assert out["state"]["event_options"][0]["description"] == "Lose 6 HP."


def test_combat_enriches_osty_sprite(tmp_path: Path):
    """Necrobinder's Osty resolves to the bone-hand monster sprite."""
    (tmp_path / "monsters").mkdir(exist_ok=True)
    (tmp_path / "monsters" / "osty.png").write_bytes(b"x")
    step = _step(
        {
            "decision": "combat_play",
            "player": {"name": "Necrobinder"},
            "enemies": [],
            "hand": [],
            "osty": {"name": "Osty", "hp": 25, "max_hp": 40, "block": 5, "alive": True},
        }
    )
    out = _enrich(step, _resolver(tmp_path), {})
    assert out["state"]["osty"]["image"] == "/assets/monsters/osty.png"


def test_combat_dead_osty_gets_no_image(tmp_path: Path):
    """A slain Osty carries only ``alive: false`` and must not gain an image key."""
    step = _step(
        {
            "decision": "combat_play",
            "player": {"name": "Necrobinder"},
            "enemies": [],
            "hand": [],
            "osty": {"alive": False},
        }
    )
    out = _enrich(step, _resolver(tmp_path), {})
    assert "image" not in out["state"]["osty"]


def test_combat_enriches_defect_orbs(tmp_path: Path):
    """Defect orbs resolve to the orb-atlas icon keyed by orb type."""
    (tmp_path / "orb_atlas").mkdir(exist_ok=True)
    (tmp_path / "orb_atlas" / "lightning.png").write_bytes(b"x")
    (tmp_path / "orb_atlas" / "frost.png").write_bytes(b"x")
    step = _step(
        {
            "decision": "combat_play",
            "player": {"name": "Defect"},
            "enemies": [],
            "hand": [],
            "orbs": [
                {"index": 0, "name": "Lightning", "type": "Lightning", "passive": 3, "evoke": 8},
                {"index": 1, "name": "Frost", "type": "Frost", "passive": 2, "evoke": 5},
            ],
            "orb_slots": 3,
        }
    )
    out = _enrich(step, _resolver(tmp_path), {})
    orbs = out["state"]["orbs"]
    assert orbs[0]["image"] == "/assets/orb_atlas/lightning.png"
    assert orbs[1]["image"] == "/assets/orb_atlas/frost.png"
    # Capacity passes through untouched for the frontend to render empty slots.
    assert out["state"]["orb_slots"] == 3


def test_combat_orb_resolves_loc_key_name(tmp_path: Path):
    """The Rust backend ships the orb name as a loc key; _enrich resolves it."""
    (tmp_path / "orb_atlas").mkdir(exist_ok=True)
    (tmp_path / "orb_atlas" / "dark.png").write_bytes(b"x")
    loc = {"DARK.title": "Dark"}
    step = _step(
        {
            "decision": "combat_play",
            "player": {"name": "Defect"},
            "enemies": [],
            "hand": [],
            "orbs": [{"index": 0, "name": "DARK.title", "type": "Dark", "passive": 6, "evoke": 6}],
        }
    )
    out = _enrich(step, _resolver(tmp_path), loc)
    assert out["state"]["orbs"][0]["name"] == "Dark"
    assert out["state"]["orbs"][0]["image"] == "/assets/orb_atlas/dark.png"


# The real MAD_SCIENCE.description template (the morphing Tinker Time card).
_MAD_SCIENCE_DESC = (
    "{CardType:choose(Attack|Skill|Power):Deal {Damage:diff()} damage"
    "{Violence: {ViolenceHits:diff()} times|}.|Gain {Block:diff()} [gold]Block[/gold].|}"
    "{HasRider:{Sapping:\nApply {SappingWeak:diff()} [gold]Weak[/gold].\n"
    "Apply {SappingVulnerable:diff()} [gold]Vulnerable[/gold].|}"
    "{Energized:\nGain {EnergizedEnergy:energyIcons()}.|}"
    "{Improvement:At the end of combat, [gold]Upgrade[/gold] a random card.|}"
    "|{CardType:choose(Attack|Skill|Power):\n???|\n???|???}}"
)
_MAD_SCIENCE_LOC = {
    "MAD_SCIENCE.title": "Mad Science",
    "MAD_SCIENCE.description": _MAD_SCIENCE_DESC,
}


def _mad_science_resolver(tmp_path: Path) -> AssetResolver:
    r = _resolver(tmp_path)
    (tmp_path / "card_atlas" / "event").mkdir(parents=True, exist_ok=True)
    for variant in ("attack", "skill", "power"):
        (tmp_path / "card_atlas" / "event" / f"mad_science_{variant}.png").write_bytes(b"x")
    return r


def test_mad_science_skill_sapping_morphs_art_and_text(tmp_path: Path):
    # A Skill/Sapping Mad Science: per-type art + the Skill branch + the Sapping
    # rider block. Mirrors what the C# backend now ships (type + rider).
    step = _step(
        {
            "decision": "combat_play",
            "player": {"name": "Ironclad"},
            "enemies": [],
            "hand": [
                {
                    "id": "MAD_SCIENCE",
                    "name": "MAD_SCIENCE.title",
                    "description": "MAD_SCIENCE.description",
                    "type": "Skill",
                    "tinker_time_rider": "Sapping",
                    "stats": {"block": 8, "sappingweak": 2, "sappingvulnerable": 2, "damage": 12},
                }
            ],
        }
    )
    out = _enrich(step, _mad_science_resolver(tmp_path), _MAD_SCIENCE_LOC)
    card = out["state"]["hand"][0]
    assert card["image"] == "/assets/card_atlas/event/mad_science_skill.png"
    assert card["description"] == "Gain 8 Block.\nApply 2 Weak.\nApply 2 Vulnerable."


def test_mad_science_attack_no_rider_uses_attack_art(tmp_path: Path):
    # Default/no-rider Mad Science (e.g. Rust backend, which can't morph yet):
    # Attack art + the mystery "???" rider placeholder.
    step = _step(
        {
            "decision": "combat_play",
            "player": {"name": "Ironclad"},
            "enemies": [],
            "hand": [
                {
                    "id": "MAD_SCIENCE",
                    "name": "MAD_SCIENCE.title",
                    "description": "MAD_SCIENCE.description",
                    "type": "Attack",
                    "tinker_time_rider": "None",
                    "stats": {"damage": 12},
                }
            ],
        }
    )
    out = _enrich(step, _mad_science_resolver(tmp_path), _MAD_SCIENCE_LOC)
    card = out["state"]["hand"][0]
    assert card["image"] == "/assets/card_atlas/event/mad_science_attack.png"
    assert card["description"] == "Deal 12 damage.\n???"


def test_cross_character_card_art_uses_loc_character(tmp_path: Path):
    # An Ironclad run can hold a card from another character (e.g. via a relic).
    # _enrich must read the card's OWN character from the loc manifest
    # (``<id>.character``) and pass it to the resolver. "scavenge.png" exists
    # under BOTH defect/ and necrobinder/, so a blind fallback scan would pick
    # the wrong one; only the loc-character wiring selects necrobinder/.
    for folder in ("defect", "necrobinder"):
        (tmp_path / "card_atlas" / folder).mkdir(parents=True, exist_ok=True)
        (tmp_path / "card_atlas" / folder / "scavenge.png").write_bytes(b"x")
    loc = {"SCAVENGE.title": "Scavenge", "SCAVENGE.character": "Necrobinder"}
    step = _step(
        {
            "decision": "combat_play",
            "player": {"name": "Ironclad"},
            "enemies": [],
            "hand": [{"id": "SCAVENGE", "name": "SCAVENGE.title", "index": 0}],
        }
    )
    out = _enrich(step, _resolver(tmp_path), loc)
    assert out["state"]["hand"][0]["image"] == "/assets/card_atlas/necrobinder/scavenge.png"


def test_combat_enriches_decimillipede_segment_rigs_by_kind(tmp_path: Path):
    # The three Decimillipede segments share the display name "Decimillipede" —
    # only their MonsterKind picks the per-segment rig + group. Regression: the
    # wire enemy must carry ``kind`` (build_combat_view) and _enrich must thread
    # it to monster_rig / monster_rig_group; without it all three fell back to
    # the shared sprite (three static millipedes instead of one connected rig).
    for part in ("front", "middle", "back"):
        d = tmp_path / "monster_rigs" / f"decimillipede_segment_{part}"
        d.mkdir(parents=True)
        (d / f"decimillipede_segment_{part}.skel").write_bytes(b"x")
    (tmp_path / "monsters").mkdir(exist_ok=True)
    (tmp_path / "monsters" / "decimillipede.png").write_bytes(b"x")
    step = _step(
        {
            "decision": "combat_play",
            "player": {"name": "Ironclad"},
            "hand": [],
            "enemies": [
                {"index": 0, "kind": "DecimillipedeSegmentFront", "name": "Decimillipede"},
                {"index": 1, "kind": "DecimillipedeSegmentMiddle", "name": "Decimillipede"},
                {"index": 2, "kind": "DecimillipedeSegmentBack", "name": "Decimillipede"},
            ],
        }
    )
    out = _enrich(step, _resolver(tmp_path), {})
    es = out["state"]["enemies"]
    assert es[0]["rig"] == (
        "/assets/monster_rigs/decimillipede_segment_front/decimillipede_segment_front"
    )
    assert es[0]["rig_group"] == "decimillipede"
    assert es[0]["rig_part"] == "front"
    assert es[2]["rig"].endswith("decimillipede_segment_back")
    assert es[2]["rig_part"] == "back"


def test_combat_enriches_mysterious_knight_as_flail_knight(tmp_path: Path):
    # MysteriousKnight (a FlailKnight subclass, no own art) must resolve to the
    # flail_knight sprite + rig via the alias — otherwise it renders blank.
    (tmp_path / "monsters").mkdir(exist_ok=True)
    (tmp_path / "monsters" / "flail_knight.png").write_bytes(b"x")
    rig = tmp_path / "monster_rigs" / "flail_knight"
    rig.mkdir(parents=True)
    (rig / "flail_knight.skel").write_bytes(b"x")
    step = _step(
        {
            "decision": "combat_play",
            "player": {"name": "Ironclad"},
            "hand": [],
            "enemies": [{"index": 0, "kind": "MysteriousKnight", "name": "Mysterious Knight"}],
        }
    )
    out = _enrich(step, _resolver(tmp_path), {})
    e = out["state"]["enemies"][0]
    assert e["image"] == "/assets/monsters/flail_knight.png"
    assert e["rig"] == "/assets/monster_rigs/flail_knight/flail_knight"


def test_combat_enriches_fake_merchant_rig_and_combat_idle(tmp_path: Path):
    # The Fake Merchant resolves its combat rig (fake_merchant_monster) via the
    # "merchant" alias and rests on combat_idle_loop, not the shop-disguise idle.
    rig = tmp_path / "monster_rigs" / "fake_merchant_monster"
    rig.mkdir(parents=True)
    (rig / "fake_merchant_monster.skel").write_bytes(b"x")
    step = _step(
        {
            "decision": "combat_play",
            "player": {"name": "Ironclad"},
            "hand": [],
            "enemies": [{"index": 0, "kind": "FakeMerchantMonster", "name": "The Merchant???"}],
        }
    )
    out = _enrich(step, _resolver(tmp_path), {})
    e = out["state"]["enemies"][0]
    assert e["rig"] == "/assets/monster_rigs/fake_merchant_monster/fake_merchant_monster"
    assert e["rig_anim"] == "combat_idle_loop"


def test_combat_without_orbs_does_not_crash(tmp_path: Path):
    """Non-Defect combat states carry no orbs key and must enrich cleanly."""
    step = _step(
        {"decision": "combat_play", "player": {"name": "Ironclad"}, "enemies": [], "hand": []}
    )
    _enrich(step, _resolver(tmp_path), {})  # must not raise


def _map_resolver(tmp_path: Path) -> AssetResolver:
    rh = tmp_path / "run_history"
    rh.mkdir(exist_ok=True)
    for slug in ("monster", "elite", "shop", "event", "the_insatiable_boss", "queen_boss"):
        (rh / f"{slug}.png").write_bytes(b"x")
    return AssetResolver(tmp_path)


def test_map_select_enriches_choice_and_graph_icons(tmp_path: Path):
    step = _step(
        {
            "decision": "map_select",
            "player": {"name": "Ironclad"},
            "choices": [
                {"col": 0, "row": 1, "type": "Monster"},
                {"col": 2, "row": 1, "type": "Event"},
            ],
            "full_map": {
                "rows": [
                    [{"col": 0, "row": 1, "type": "Monster"}],
                    [{"col": 2, "row": 2, "type": "Elite"}],
                ],
                "boss": {"col": 3, "row": 3, "type": "Boss", "id": "THE_INSATIABLE_BOSS"},
            },
        }
    )
    out = _enrich(step, _map_resolver(tmp_path), {})
    state = out["state"]
    # Immediately-reachable choices carry the real per-type badge.
    assert state["choices"][0]["icon"] == "/assets/run_history/monster.png"
    assert state["choices"][1]["icon"] == "/assets/run_history/event.png"
    # Full graph nodes + the boss meta-node get icons too.
    assert state["full_map"]["rows"][0][0]["icon"] == "/assets/run_history/monster.png"
    assert state["full_map"]["rows"][1][0]["icon"] == "/assets/run_history/elite.png"
    assert state["full_map"]["boss"]["icon"] == "/assets/run_history/the_insatiable_boss.png"


def test_a10_second_boss_node_gets_its_own_icon(tmp_path: Path):
    # A10 DoubleBoss: the second boss node arrives as an ordinary ``rows``
    # entry typed Boss, one row above the act boss, and it fights a DIFFERENT
    # encounter. Resolving every Boss-typed node against the act boss's id put
    # the act boss's face on both of the run's last two nodes.
    step = _step(
        {
            "decision": "map_select",
            "player": {"name": "Ironclad"},
            "choices": [],
            "full_map": {
                "rows": [
                    [{"col": 0, "row": 1, "type": "Monster"}],
                    [{"col": 3, "row": 4, "type": "Boss"}],
                ],
                "boss": {"col": 3, "row": 3, "type": "Boss", "id": "THE_INSATIABLE_BOSS"},
                "second_boss": {"col": 3, "row": 4, "type": "Boss", "id": "QUEEN_BOSS"},
            },
        }
    )
    out = _enrich(step, _map_resolver(tmp_path), {})
    fm = out["state"]["full_map"]
    assert fm["boss"]["icon"] == "/assets/run_history/the_insatiable_boss.png"
    assert fm["second_boss"]["icon"] == "/assets/run_history/queen_boss.png"
    # …and the row node standing at the second boss's coord, which is what the
    # map actually draws, resolves to the second boss too.
    assert fm["rows"][1][0]["icon"] == "/assets/run_history/queen_boss.png"


def test_map_select_missing_art_leaves_icon_none(tmp_path: Path):
    # Empty art root: every icon resolves to None so the frontend keeps its glyph.
    step = _step(
        {
            "decision": "map_select",
            "player": {"name": "Ironclad"},
            "choices": [{"col": 0, "row": 1, "type": "Treasure"}],
            "full_map": {"rows": [[{"col": 0, "row": 1, "type": "Treasure"}]], "boss": None},
        }
    )
    out = _enrich(step, AssetResolver(tmp_path), {})
    assert out["state"]["choices"][0]["icon"] is None
    assert out["state"]["full_map"]["rows"][0][0]["icon"] is None


def test_card_frame_color_follows_the_pool_not_the_run(tmp_path: Path):
    """The frame tint is the card's OWN pool colour.

    A Defect card taken into an Ironclad run stays blue, and a Colorless card
    stays grey — the game reads a card's frame off its pool, never off whoever
    is holding it. Taking it from the run character instead would repaint the
    whole deck red.
    """
    step = _step(
        {
            "decision": "card_reward",
            "player": {"name": "Ironclad"},
            "cards": [
                {"id": "CARD.ANGER", "name": "Anger", "type": "Attack"},
                {"id": "CARD.CLAW", "name": "Claw", "type": "Attack"},
                {"id": "CARD.ALCHEMIZE", "name": "Alchemize", "type": "Skill"},
                {"id": "CARD.CLUMSY", "name": "Clumsy", "type": "Curse"},
            ],
        }
    )
    loc = {
        "ANGER.character": "Ironclad",
        "CLAW.character": "Defect",
        # Colorless and curse cards carry no character in the manifest.
        "CLUMSY.rarity": "Curse",
    }
    out = _enrich(step, AssetResolver(tmp_path), loc)
    assert [c["color"] for c in out["state"]["cards"]] == ["red", "blue", "colorless", "curse"]


def test_regent_star_cost_is_attached(tmp_path: Path):
    """Star cost is a second cost resource, and it only exists in the manifest.

    No backend puts it on the wire — the engine keeps it as a per-CardId
    constant — so without this the Regent's star cards render as though they
    cost nothing but energy.
    """
    step = _step(
        {
            "decision": "card_reward",
            "player": {"name": "Regent"},
            "cards": [
                {"id": "CARD.COMET", "name": "Comet", "type": "Attack"},
                {"id": "CARD.STRIKE_REGENT", "name": "Strike", "type": "Attack"},
            ],
        }
    )
    out = _enrich(step, AssetResolver(tmp_path), {"COMET.star_cost": "5"})
    cards = out["state"]["cards"]
    assert cards[0]["star_cost"] == 5
    # A card with no star cost must not grow the key at all — the frame keys the
    # gem's presence off it.
    assert "star_cost" not in cards[1]


def test_enrich_reports_the_assets_base(tmp_path: Path):
    # The card skin composes its own art paths in the frontend, so every step
    # carries the base those paths hang off (it is not "/assets" in bucket mode).
    out = _enrich(_step({"decision": "map_select", "player": {}}), AssetResolver(tmp_path), {})
    assert out["assets_base"] == "/assets"


def _test_subject(powers: list[dict]) -> dict:
    return {
        "decision": "combat_play",
        "player": {"name": "Ironclad"},
        "hand": [],
        "enemies": [{"index": 0, "kind": "TestSubject", "name": "Test Subject", "powers": powers}],
    }


def test_test_subject_form_reads_the_power_ID_not_its_display_title(tmp_path: Path):
    """Each revive gives the Test Subject another head, and the frontend picks
    ``idle_loop{rig_form}`` from this field.

    The wire power carries a localized TITLE in ``name`` ("Painful Stabs") and
    the engine id in ``id``. Deriving the form by comparing ``name`` to the id
    matched nothing, so every Test Subject rendered as its one-headed first form
    for the whole fight — a perfectly healthy-looking render, which is why it
    went unnoticed. Pin both fields.
    """
    form2 = _enrich(
        _step(_test_subject([{"name": "Painful Stabs", "id": "PAINFUL_STABS", "amount": 1}])),
        _resolver(tmp_path),
        {},
    )
    assert form2["state"]["enemies"][0]["rig_form"] == 2

    form3 = _enrich(
        _step(
            _test_subject(
                [
                    {"name": "Nemesis", "id": "NEMESIS", "amount": 1},
                    {"name": "Strength", "id": "STRENGTH", "amount": 3},
                ]
            )
        ),
        _resolver(tmp_path),
        {},
    )
    assert form3["state"]["enemies"][0]["rig_form"] == 3


def test_test_subject_form_also_reads_a_title_only_backend(tmp_path: Path):
    """A backend that ships only the display title still resolves the form:
    matching normalizes spaces to underscores, so "Painful Stabs" hits
    ``PAINFUL_STABS``."""
    out = _enrich(
        _step(_test_subject([{"name": "Painful Stabs", "amount": 1}])), _resolver(tmp_path), {}
    )
    assert out["state"]["enemies"][0]["rig_form"] == 2


def test_test_subject_first_form_carries_no_rig_form(tmp_path: Path):
    """Form 1 is the rig's own default — absent, not 1, so the frontend's
    ``e.rig_form ? … : …`` falls through to the normal idle resolution."""
    out = _enrich(
        _step(_test_subject([{"name": "Adaptable", "id": "ADAPTABLE", "amount": 1}])),
        _resolver(tmp_path),
        {},
    )
    assert "rig_form" not in out["state"]["enemies"][0]


def test_room_reveal_relic_gets_art_and_a_display_name(tmp_path: Path):
    # A treasure chest is auto-opened by the engine, so its reveal panel rides
    # on whatever decision comes NEXT (usually the map). Enrichment therefore
    # can't hang off a `decision` — it has to run for every state.
    loc = {
        "AKABEKO.title": "Akabeko",
        "AKABEKO.description": "Your first Attack each combat deals 8 additional damage.",
    }
    step = _step(
        {
            "decision": "map_select",
            "player": {"name": "Ironclad"},
            "room_reveal": {
                "kind": "treasure",
                "act": 1,
                "gold": 43,
                "relic": {"name": "AKABEKO.title", "description": "AKABEKO.description"},
            },
        }
    )
    out = _enrich(step, _resolver(tmp_path), loc)
    relic = out["state"]["room_reveal"]["relic"]
    assert relic["name"] == "Akabeko"
    assert relic["image"] == "/assets/relic_atlas/akabeko.png"
    assert relic["description"].startswith("Your first Attack")


def test_an_empty_room_reveal_enriches_without_raising(tmp_path: Path):
    # Silver Crucible's veto leaves a chest with no relic at all.
    step = _step(
        {
            "decision": "map_select",
            "player": {"name": "Ironclad"},
            "room_reveal": {"kind": "treasure", "act": 1, "gold": 0, "relic": None},
        }
    )
    out = _enrich(step, _resolver(tmp_path), {})
    assert out["state"]["room_reveal"]["relic"] is None


def test_card_face_prints_its_keyword_lines(tmp_path: Path):
    """The game prints a card's keywords ON the card, as their own lines above
    or below the rules text (C# ``CardModel.GetDescriptionForPile``). The
    workbench dropped them entirely: Tremble read "Apply 3 Vulnerable." where
    the real card reads "Apply 3 Vulnerable." / "Exhaust."."""
    loc = {
        "TREMBLE.title": "Tremble",
        "TREMBLE.description": "Apply {VulnerablePower} [gold]Vulnerable[/gold].",
        "TREMBLE.description_plain": "Apply 3 [gold]Vulnerable[/gold].",
        "TREMBLE.keywords": "Exhaust",
        "DAZED.title": "Dazed",
        "DAZED.keywords": "Ethereal,Unplayable",
        "card_keyword.Exhaust": "Exhaust",
        "card_keyword.Ethereal": "Ethereal",
        "card_keyword.Unplayable": "Unplayable",
    }
    step = _step(
        {
            "decision": "card_reward",
            "player": {"name": "Ironclad", "deck": [{"id": "DAZED", "index": 1}]},
            "cards": [{"id": "TREMBLE", "index": 0, "description": "TREMBLE.description"}],
        }
    )
    out = _enrich(step, _resolver(tmp_path), loc)

    card = out["state"]["cards"][0]
    assert card["description"] == "Apply 3 Vulnerable.\nExhaust."
    # The rich twin keeps the game's own markup, on the keyword line too.
    assert card["description_rich"] == "Apply 3 [gold]Vulnerable[/gold].\n[gold]Exhaust[/gold]."
    # Before-description keywords print above, in REVERSE declaration order —
    # the game ``Insert(0, …)``s each one.
    assert out["state"]["player"]["deck"][0]["description"] == "Unplayable.\nEthereal."


def test_combat_powers_carry_both_the_tray_and_the_big_icon(tmp_path: Path):
    """``enrich_powers`` attaches BOTH icons, because the game uses both.

    ``image`` is the 64x64 tray sprite ``Powers.tsx`` renders at 26px;
    ``image_big`` is the 256x256 ``BigIcon`` the three power VFX surfaces draw
    (``NPowerAppliedVfx``, ``NPowerRemovedVfx``, ``NPowerFlashVfx``). Shipping
    only the tray icon made the flash render at a quarter size, since its
    authored ``scale: 0.4`` is sized against the 256px sprite.
    """
    (tmp_path / "power_atlas").mkdir(exist_ok=True)
    (tmp_path / "power_atlas" / "strength_power.png").write_bytes(b"x")
    (tmp_path / "power_big").mkdir(exist_ok=True)
    (tmp_path / "power_big" / "strength_power.png").write_bytes(b"x")
    step = _step(
        {
            "decision": "combat_play",
            "player": {"name": "Ironclad"},
            "player_powers": [{"name": "Strength", "amount": 2}],
            "enemies": [],
            "hand": [],
        }
    )
    out = _enrich(step, AssetResolver(tmp_path), {})
    power = out["state"]["player_powers"][0]
    assert power["image"] == "/assets/power_atlas/strength_power.png"
    assert power["image_big"] == "/assets/power_big/strength_power.png"
