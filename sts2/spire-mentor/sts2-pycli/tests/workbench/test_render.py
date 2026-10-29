"""Unit tests for workbench.render (card-text and action labels)."""

from __future__ import annotations

from workbench.render import describe_action, required_stat_vars, resolve_card_text


def test_resolve_simple_damage() -> None:
    assert resolve_card_text("Deal {Damage:diff()} damage.", {"damage": 6}) == "Deal 6 damage."


def test_resolve_strips_bbcode() -> None:
    assert (
        resolve_card_text("Gain {Block:diff()} [gold]Block[/gold].", {"block": 5})
        == "Gain 5 Block."
    )


def test_resolve_keeps_bbcode_when_asked() -> None:
    # The card skin colours the game's own keyword markup, so it needs the tags
    # left in — while every other caller keeps the stripped form above.
    assert (
        resolve_card_text("Gain {Block:diff()} [gold]Block[/gold].", {"block": 5}, keep_markup=True)
        == "Gain 5 [gold]Block[/gold]."
    )


def test_resolve_cost_glyphs_in_markup_mode() -> None:
    """All four icon-token shapes the game's card text uses.

    The count is either read from ``stats`` (empty parens) or stated literally
    in the formatter; ``singleStarIcon`` carries no formatter at all. Getting
    the literal forms wrong is invisible in plain text — they render empty —
    which is why they are pinned here.
    """
    assert (
        resolve_card_text("Gain {Energy:energyIcons()}.", {"energy": 3}, keep_markup=True)
        == "Gain [energy]3[/energy]."
    )
    assert (
        resolve_card_text("Add a 0{energyPrefix:energyIcons(1)} copy.", {}, keep_markup=True)
        == "Add a 0[energy]1[/energy] copy."
    )
    assert (
        resolve_card_text("Gain {Stars:starIcons()}.", {"stars": 2}, keep_markup=True)
        == "Gain [star]2[/star]."
    )
    assert (
        resolve_card_text("Spend {singleStarIcon} to win.", {}, keep_markup=True)
        == "Spend [star]1[/star] to win."
    )


def test_plain_mode_spells_the_cost_resource_out() -> None:
    """Plain text has no glyphs, so it names the resource.

    These strings are matched against the wiki's pre-resolved
    ``description_plain`` (``extract_cards.py``), which is the OTHER text the
    same card can display — the backend falls back to it whenever a card's
    ``stats`` are incomplete. If the two disagree, a card silently reads
    differently depending on which path served it.
    """
    assert resolve_card_text("Gain {Energy:energyIcons()}.", {"energy": 1}) == "Gain 1 Energy."
    assert resolve_card_text("Gain {Stars:starIcons()}.", {"stars": 2}) == "Gain 2 Stars."
    # energyPrefix is a bare unit — the number is already in the sentence.
    assert (
        resolve_card_text("Add a 0{energyPrefix:energyIcons(1)} copy.", {})
        == "Add a 0 Energy copy."
    )
    # Previously rendered as nothing, leaving "Whenever you spend , ...".
    assert (
        resolve_card_text("Whenever you spend {singleStarIcon}, gain 3 Block.", {})
        == "Whenever you spend ★, gain 3 Block."
    )
    # An unresolved count still names the resource rather than leaking the var.
    assert resolve_card_text("Gain {Energy:energyIcons()}.", {}) == "Gain Energy."


def test_resolve_plural_singular_and_plural() -> None:
    tmpl = "Draw {Cards} {Cards:plural:card|cards}."
    assert resolve_card_text(tmpl, {"cards": 3}) == "Draw 3 cards."
    assert resolve_card_text(tmpl, {"cards": 1}) == "Draw 1 card."


def test_resolve_unknown_var_falls_back_to_name() -> None:
    assert resolve_card_text("Deal {Damage} damage.", {}) == "Deal Damage damage."


def test_resolve_empty() -> None:
    assert resolve_card_text("", None) == ""
    assert resolve_card_text(None, None) == ""


def test_resolve_incombat_conditional() -> None:
    # Body Slam: the combat-only parenthetical lives in {InCombat:then|else};
    # out of combat (shop/deck) takes the empty else-branch.
    tmpl = "Deal damage equal to your [gold]Block[/gold].{InCombat:\n(Deals {CalculatedDamage:diff()} damage)|}"
    assert resolve_card_text(tmpl, {}, in_combat=False) == "Deal damage equal to your Block."
    assert (
        resolve_card_text(tmpl, {"calculateddamage": 18}, in_combat=True)
        == "Deal damage equal to your Block.\n(Deals 18 damage)"
    )


def test_resolve_ifupgraded_show_branch() -> None:
    tmpl = "[gold]Upgrade[/gold] {IfUpgraded:show:ALL cards|a card} in your Hand."
    assert resolve_card_text(tmpl, {}, upgraded=False) == "Upgrade a card in your Hand."
    assert resolve_card_text(tmpl, {}, upgraded=True) == "Upgrade ALL cards in your Hand."


def test_resolve_nested_plural_and_conditional() -> None:
    # CHARGE: a {Cards:plural:..|..} whose branches each nest an {IfUpgraded:..}
    # — exercises balanced-brace recursion (a naive split would mangle it).
    tmpl = (
        "Transform into {Cards:plural:{IfUpgraded:show:Bomb+|Bomb}|{IfUpgraded:show:Bombs+|Bombs}}."
    )
    assert resolve_card_text(tmpl, {"cards": 1}, upgraded=False) == "Transform into Bomb."
    assert resolve_card_text(tmpl, {"cards": 2}, upgraded=True) == "Transform into Bombs+."


# ── Mad Science morph DSL ({CardType:choose(...)} + rider booleans) ──────────

# The real MAD_SCIENCE.description template, used across the morph tests.
_MAD_SCIENCE = (
    "{CardType:choose(Attack|Skill|Power):Deal {Damage:diff()} damage"
    "{Violence: {ViolenceHits:diff()} times|}.|Gain {Block:diff()} [gold]Block[/gold].|}"
    "{HasRider:{Sapping:\nApply {SappingWeak:diff()} [gold]Weak[/gold].\n"
    "Apply {SappingVulnerable:diff()} [gold]Vulnerable[/gold].|}"
    "{Choking:\nWhenever you play a card this turn, the enemy loses {ChokingDamage:diff()} HP.|}"
    "{Energized:\nGain {EnergizedEnergy:energyIcons()}.|}"
    "{Wisdom:\nDraw {WisdomCards:diff()} cards.|}"
    "{Chaos:\nAdd a random card into your [gold]Hand[/gold]. "
    "It costs 0 {energyPrefix:energyIcons(1)} this turn.|}"
    "{Expertise:Gain {ExpertiseStrength:diff()} [gold]Strength[/gold].\n"
    "Gain {ExpertiseDexterity:diff()} [gold]Dexterity[/gold].|}"
    "{Curious:Powers cost {CuriousReduction:diff()} {energyPrefix:energyIcons(1)} less.|}"
    "{Improvement:At the end of combat, [gold]Upgrade[/gold] a random card.|}"
    "|{CardType:choose(Attack|Skill|Power):\n???|\n???|???}}"
)


def _morph(card_type: str, rider: str) -> dict:
    """Build the extra_flags dict the way the server does from the C# fields."""
    riders = (
        "violence",
        "sapping",
        "choking",
        "energized",
        "wisdom",
        "chaos",
        "expertise",
        "curious",
        "improvement",
    )
    flags: dict = {r: (rider.lower() == r) for r in riders}
    flags["hasrider"] = rider.lower() != "none"
    flags["cardtype"] = card_type
    return flags


def test_choose_selects_branch_by_value() -> None:
    tmpl = "{CardType:choose(Attack|Skill|Power):attack text|skill text|power text}"
    assert resolve_card_text(tmpl, {"cardtype": "Attack"}) == "attack text"
    assert resolve_card_text(tmpl, {"cardtype": "Skill"}) == "skill text"
    assert resolve_card_text(tmpl, {"cardtype": "Power"}) == "power text"


def test_choose_defaults_to_first_branch_when_value_missing() -> None:
    tmpl = "{CardType:choose(Attack|Skill|Power):a|b|c}"
    assert resolve_card_text(tmpl, {}) == "a"


def test_generic_boolean_conditional_via_extra_flags() -> None:
    tmpl = "Deal 6 damage{Violence: {ViolenceHits:diff()} times|}."
    assert (
        resolve_card_text(tmpl, {"violencehits": 3}, extra_flags={"violence": True})
        == "Deal 6 damage 3 times."
    )
    assert (
        resolve_card_text(tmpl, {"violencehits": 3}, extra_flags={"violence": False})
        == "Deal 6 damage."
    )


def test_mad_science_attack_sapping() -> None:
    stats = {"cardtype": "Attack", "damage": 12, "sappingweak": 2, "sappingvulnerable": 2}
    out = resolve_card_text(_MAD_SCIENCE, stats, extra_flags=_morph("Attack", "Sapping"))
    assert out == "Deal 12 damage.\nApply 2 Weak.\nApply 2 Vulnerable."


def test_mad_science_attack_violence_rider() -> None:
    stats = {"cardtype": "Attack", "damage": 12, "violencehits": 3}
    out = resolve_card_text(_MAD_SCIENCE, stats, extra_flags=_morph("Attack", "Violence"))
    assert out == "Deal 12 damage 3 times."


def test_mad_science_skill_no_rider_shows_mystery() -> None:
    # No rider → HasRider false → the else-branch shows the "???" placeholder
    # (the game's pre-reveal preview), chosen by card type.
    stats = {"cardtype": "Skill", "block": 8}
    out = resolve_card_text(_MAD_SCIENCE, stats, extra_flags=_morph("Skill", "None"))
    assert out == "Gain 8 Block.\n???"


def test_mad_science_power_expertise() -> None:
    stats = {"cardtype": "Power", "expertisestrength": 2, "expertisedexterity": 2}
    out = resolve_card_text(_MAD_SCIENCE, stats, extra_flags=_morph("Power", "Expertise"))
    assert out == "Gain 2 Strength.\nGain 2 Dexterity."


def test_required_stat_vars_follows_choose_and_rider() -> None:
    # Skill+no-rider needs block; the rider/attack vars are off-branch.
    need = required_stat_vars(_MAD_SCIENCE, extra_flags=_morph("Skill", "None"))
    assert need == {"block"}
    # Attack+Sapping needs damage + the two sapping vars, not block.
    need2 = required_stat_vars(_MAD_SCIENCE, extra_flags=_morph("Attack", "Sapping"))
    assert need2 == {"damage", "sappingweak", "sappingvulnerable"}


def test_describe_play_card_uses_hand_name_and_target() -> None:
    state = {"hand": [{"index": 0, "name": "Strike"}]}
    action = {"cmd": "action", "action": "play_card", "args": {"card_index": 0, "target_index": 1}}
    assert describe_action(state, action) == "Play Strike → enemy 1"


def test_describe_end_turn_and_start_run() -> None:
    assert describe_action({}, {"cmd": "action", "action": "end_turn"}) == "End turn"
    assert (
        describe_action({}, {"cmd": "start_run", "character": "Ironclad"}) == "Start run · Ironclad"
    )


def test_describe_select_card_reward() -> None:
    state = {"cards": [{"index": 0, "name": "Anger"}]}
    action = {"cmd": "action", "action": "select_card_reward", "args": {"card_index": 0}}
    assert describe_action(state, action) == "Take Anger"


def test_describe_play_upgraded_card_appends_plus() -> None:
    # Upgraded cards are flagged with ``upgraded: True``; the log marks them
    # with a trailing "+" (e.g. an upgraded Bash reads "Bash+").
    state = {"hand": [{"index": 0, "name": "Bash", "upgraded": True}]}
    action = {"cmd": "action", "action": "play_card", "args": {"card_index": 0}}
    assert describe_action(state, action) == "Play Bash+"


def test_describe_play_unupgraded_card_no_plus() -> None:
    state = {"hand": [{"index": 0, "name": "Bash", "upgraded": False}]}
    action = {"cmd": "action", "action": "play_card", "args": {"card_index": 0}}
    assert describe_action(state, action) == "Play Bash"


def test_describe_take_upgraded_card_appends_plus() -> None:
    state = {"cards": [{"index": 0, "name": "Anger", "upgraded": True}]}
    action = {"cmd": "action", "action": "select_card_reward", "args": {"card_index": 0}}
    assert describe_action(state, action) == "Take Anger+"


def test_describe_choose_option_uses_title() -> None:
    # The Rust backend emits event options with a ``title`` field (C# uses
    # ``text``/``name``); describe_action should read it so event picks read
    # "Choose: <option>" rather than "Choose option 0".
    state = {"options": [{"index": 0, "title": "Lost Wisp"}]}
    action = {"cmd": "action", "action": "choose_option", "args": {"option_index": 0}}
    assert describe_action(state, action) == "Choose: Lost Wisp"


def test_describe_buy_names_the_item() -> None:
    # Shop purchases should log the item name, not the bare verb.
    shop = {
        "cards": [{"index": 0, "name": "Strike"}],
        "relics": [{"index": 0, "name": "Akabeko"}],
        "potions": [{"index": 0, "name": "Fire Potion"}],
    }
    assert (
        describe_action(shop, {"cmd": "a", "action": "buy_card", "args": {"card_index": 0}})
        == "Buy Strike"
    )
    assert (
        describe_action(shop, {"cmd": "a", "action": "buy_relic", "args": {"relic_index": 0}})
        == "Buy Akabeko"
    )
    assert (
        describe_action(shop, {"cmd": "a", "action": "buy_potion", "args": {"potion_index": 0}})
        == "Buy Fire Potion"
    )


def test_describe_no_action() -> None:
    assert describe_action({}, None) == ""


def test_required_stat_vars_simple() -> None:
    assert required_stat_vars("Deal {Damage:diff()} damage.") == {"damage"}


def test_required_stat_vars_multiple() -> None:
    # Bash-style: damage plus an applied-power var.
    tpl = "Deal {Damage:diff()} damage.\nApply {VulnerablePower:diff()} Vulnerable."
    assert required_stat_vars(tpl) == {"damage", "vulnerablepower"}


def test_required_stat_vars_includes_plural_count() -> None:
    tpl = "Add {Cards:diff()} {Cards:plural:card|cards}."
    assert required_stat_vars(tpl) == {"cards"}


def test_required_stat_vars_excludes_icon_pseudo_tokens() -> None:
    # ``energyPrefix`` / ``energyIcons`` aren't stats — only Damage is required.
    tpl = "Deal {Damage:diff()} damage.\nAdd a 0{energyPrefix:energyIcons(1)} copy."
    assert required_stat_vars(tpl) == {"damage"}


def test_required_stat_vars_follows_ifupgraded_branch() -> None:
    tpl = "{IfUpgraded:show:Gain {Block:diff()} Block|Deal {Damage:diff()} damage}"
    assert required_stat_vars(tpl, upgraded=True) == {"block"}
    assert required_stat_vars(tpl, upgraded=False) == {"damage"}


def test_required_stat_vars_none() -> None:
    assert required_stat_vars(None) == set()
    assert required_stat_vars("Exhaust.") == set()


# Shiv's real v0.107.1 template. Its target type morphs — Fan of Knives makes
# every Shiv hit all enemies — and only the live board knows which it is.
_SHIV = "Deal {Damage:diff()} damage{TargetType:choose(AllEnemies): to ALL enemies|}."


def test_unsuppliable_chooser_is_a_required_var() -> None:
    """A chooser nobody supplies must be REPORTED as required, so the caller
    takes the pre-resolved-plain fallback.

    ``_choose_branch`` resolves an unknown value to branch 0. For Shiv that
    branch is the Fan-of-Knives morph, so a silently-defaulted render says every
    Shiv hits ALL enemies. Only Mad Science's ``cardtype`` travels in
    ``extra_flags``; ``targettype`` does not, and a chooser the renderer cannot
    answer is exactly the "confidently wrong" case the fallback exists for.
    """
    assert required_stat_vars(_SHIV) == {"damage", "targettype"}
    # A supplied chooser stays a morph field, never a required stat.
    assert "cardtype" not in required_stat_vars(_MAD_SCIENCE, extra_flags=_morph("Skill", "None"))


def test_describe_shop_removal_names_the_deck_card() -> None:
    """A shop card-removal must name the DECK card, not the shop stock.

    ``shop_remove_card{deck_idx}`` maps to ``select_cards{indices}``
    (``run_policy.to_workbench_verb``), and a shop state's ``cards`` is the
    STOCK — so the generic path labelled a removal with whatever the shop
    happened to sell at that index. Report wb-c2adad9c8eeb21f7: "play best"
    removed deck card #0 (gold 143→68, the 75g removal price) and the
    timeline logged it as "Select Thunderclap", the first card on sale.
    """
    shop = {
        "decision": "shop",
        "cards": [{"index": 0, "name": "Thunderclap"}],
        "player": {"deck": [{"index": 0, "name": "Strike"}, {"index": 1, "name": "Bash"}]},
    }
    action = {"cmd": "action", "action": "select_cards", "args": {"indices": "0"}}
    assert describe_action(shop, action) == "Remove Strike (deck #0)"


def test_describe_shop_removal_marks_upgraded_and_multiple_indices() -> None:
    shop = {
        "decision": "shop",
        "cards": [{"index": 0, "name": "Thunderclap"}],
        "player": {
            "deck": [
                {"index": 0, "name": "Strike"},
                {"index": 1, "name": "Bash", "upgraded": True},
            ]
        },
    }
    action = {"cmd": "action", "action": "select_cards", "args": {"indices": "0,1"}}
    assert describe_action(shop, action) == "Remove Strike (deck #0), Bash+ (deck #1)"


def test_describe_shop_removal_without_a_deck_keeps_the_generic_label() -> None:
    # A backend that ships no ``player.deck`` (C#) must not regress to
    # "Remove card 0" — it keeps the pre-existing stock-indexed label.
    shop = {"decision": "shop", "cards": [{"index": 0, "name": "Thunderclap"}]}
    action = {"cmd": "action", "action": "select_cards", "args": {"indices": "0"}}
    assert describe_action(shop, action) == "Select Thunderclap"


def test_describe_prefers_the_recorded_label() -> None:
    # The workbench's quit path records the label the live log displayed;
    # it wins over anything re-derived from the action shape.
    action = {"cmd": "action", "action": "__agent_native__", "args": {}, "label": "Play Bash+"}
    assert describe_action({}, action) == "Play Bash+"


def test_describe_unwraps_a_native_agent_action() -> None:
    """Autoplayed steps recorded before the label existed still read as English.

    Every native-agent node logs under the ``__agent_native__`` sentinel with
    its real kind inside ``args``; left wrapped, a whole run's combat history
    rendered as "__agent_native__".
    """
    state = {"hand": [{"index": 0, "name": "Strike"}], "enemies": [{"index": 1, "name": "Nibbit"}]}
    native = {
        "cmd": "action",
        "action": "__agent_native__",
        "args": {"action": "play", "card_index": 0, "target_index": 1},
    }
    assert describe_action(state, native) == "Play Strike → Nibbit"
    assert (
        describe_action({}, {"action": "__agent_native__", "args": {"action": "end_turn"}})
        == "End turn"
    )
    # A raw RunAction (``play_card``/``hand_idx``) reads too, not just the
    # normalized ``play``/``card_index`` pair the log writer emits.
    raw = {"action": "__agent_native__", "args": {"action": "play_card", "hand_idx": 0}}
    assert describe_action(state, raw) == "Play Strike"
    # A kind with no dedicated verb degrades to the kind, not to the sentinel.
    assert (
        describe_action({}, {"action": "__agent_native__", "args": {"action": "move", "col": 4}})
        == "move"
    )


def test_describe_potion() -> None:
    # Both index spellings: the C# verb's ``potion_index`` and the native
    # RunAction's ``slot``.
    # The belt lives under ``player.potions``; top-level ``potions`` is the
    # SHOP's stock, so naming a drink from it labelled it with whatever was
    # on sale.
    state = {
        "player": {"potions": [{"index": 0, "name": "Fire Potion"}]},
        "potions": [{"index": 0, "name": "Attack Potion"}],
        "enemies": [{"index": 0, "name": "Nibbit"}],
    }
    verb = {"cmd": "action", "action": "use_potion", "args": {"potion_index": 0}}
    assert describe_action(state, verb) == "Drink Fire Potion"
    native = {
        "action": "__agent_native__",
        "args": {"action": "drink_potion", "slot": 0, "target_index": 0},
    }
    assert describe_action(state, native) == "Drink Fire Potion → Nibbit"
