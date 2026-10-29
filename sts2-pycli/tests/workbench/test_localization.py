import json
from pathlib import Path

from workbench import localization
from workbench.statediff import diff_states

_LOC = {
    "STRIKE_IRONCLAD.title": "Strike",
    "LOST_WISP.title": "Lost Wisp",
    "FIRE_POTION.title": "Fire Potion",
    "NEOWS_TALISMAN.title": "Neow's Talisman",
    "rest_site.OPTION_SMITH.name": "Smith",
}


def test_localization_pins_to_engine_version_not_latest(tmp_path: Path):
    """Display loc must resolve against the pinned engine build
    (``_ENGINE_WIKI_VERSION``), not whatever newer ``data/<ver>/`` the wiki also
    carries for a future port. The decoy must sort strictly above the pin."""
    wiki_data = tmp_path / "data"
    for ver, title in ((localization._ENGINE_WIKI_VERSION, "Pinned"), ("v0.999.0", "Newer")):
        (wiki_data / ver).mkdir(parents=True)
        (wiki_data / ver / "cards.json").write_text(
            json.dumps([{"loc_key": "BASH", "title": title}])
        )
        (wiki_data / ver / "relics").mkdir()
        (wiki_data / ver / "relics" / "x.json").write_text(
            json.dumps({"class_name": "BurningBlood", "description": f"{title} desc"})
        )

    assert localization._engine_manifest(wiki_data).parent.name == localization._ENGINE_WIKI_VERSION
    assert (
        localization._engine_versioned_subdir(wiki_data, "relics").parent.name
        == localization._ENGINE_WIKI_VERSION
    )
    card_loc = localization.load_card_localization(tmp_path / "missing.json", wiki_data)
    assert card_loc["BASH.title"] == "Pinned"


def test_localization_falls_back_to_latest_when_pin_absent(tmp_path: Path):
    """If the pinned build's data isn't on disk (e.g. a trimmed deploy), still
    resolve against the highest version present rather than returning nothing."""
    wiki_data = tmp_path / "data"
    (wiki_data / "v0.107.1").mkdir(parents=True)
    (wiki_data / "v0.107.1" / "cards.json").write_text(json.dumps([{"loc_key": "BASH"}]))
    assert localization._engine_manifest(wiki_data).parent.name == "v0.107.1"


def test_load_card_meta_includes_cost(tmp_path: Path):
    wiki_data = tmp_path / "data"
    (wiki_data / "v0.0.1").mkdir(parents=True)
    (wiki_data / "v0.0.1" / "cards.json").write_text(
        json.dumps(
            {
                "cards": [
                    {"loc_key": "BASH", "type": "Attack", "rarity": "Basic", "energy_cost": 2},
                    {
                        "loc_key": "WHIRLWIND",
                        "type": "Attack",
                        "rarity": "Uncommon",
                        "energy_cost": -1,
                    },
                ]
            }
        )
    )
    meta = localization.load_card_meta(wiki_data)
    assert meta["BASH"]["cost"] == "2"
    assert meta["WHIRLWIND"]["cost"] == "X"  # -1 → variable cost


def test_resolve_state_names_covers_cards_relics_potions_options():
    state = {
        "hand": [{"index": 0, "name": "STRIKE_IRONCLAD.title"}],
        "player": {
            "deck": [{"name": "STRIKE_IRONCLAD.title"}],
            "relics": [{"name": "LOST_WISP.title"}],
            "potions": [{"name": "FIRE_POTION.title"}],
        },
        "options": [{"index": 0, "title": "NEOWS_TALISMAN.title"}],
    }
    out = localization.resolve_state_names(state, _LOC)
    assert out["hand"][0]["name"] == "Strike"
    assert out["player"]["deck"][0]["name"] == "Strike"
    assert out["player"]["relics"][0]["name"] == "Lost Wisp"
    assert out["player"]["potions"][0]["name"] == "Fire Potion"
    assert out["options"][0]["title"] == "Neow's Talisman"
    # Original is not mutated (deep copy).
    assert state["player"]["relics"][0]["name"] == "LOST_WISP.title"


def test_resolve_state_names_resolves_a_rest_site_option_name():
    """A rest-site option labels itself in ``name`` (``rest_site.OPTION_<ID>.name``),
    not ``title``, and ``describe_action`` prefers ``name`` -- so resolving only
    ``title`` leaked the key into the run log: "Choose: rest_site.OPTION_SMITH.name".
    """
    out = localization.resolve_state_names(
        {
            "options": [
                {"index": 0, "option_id": "SMITH", "name": "rest_site.OPTION_SMITH.name"},
                {"index": 1, "option_id": "DIG", "name": "rest_site.OPTION_DIG.name"},
            ]
        },
        _LOC,
    )
    assert out["options"][0]["name"] == "Smith"
    # A key the bundle lacks is prettified from the option id, never printed raw.
    assert out["options"][1]["name"] == "Dig"


def test_relic_gain_diff_reads_resolved_name():
    # The Rust backend stores a relic as its loc key; resolving before the diff
    # makes the run-log line read "+Relic Lost Wisp", not "+Relic LOST_WISP.title".
    prev = localization.resolve_state_names({"player": {"relics": []}}, _LOC)
    nxt = localization.resolve_state_names(
        {"player": {"relics": [{"name": "LOST_WISP.title"}]}}, _LOC
    )
    assert diff_states(prev, nxt) == ["+Relic Lost Wisp"]


def test_load_card_localization_loads_plain_descriptions(tmp_path: Path):
    """Wiki ``description_plain`` / ``upgraded_description_plain`` are captured."""
    repo = tmp_path / "cards.json"
    repo.write_text(json.dumps({}))
    wiki = tmp_path / "wiki"
    (wiki / "v1.0.0").mkdir(parents=True)
    (wiki / "v1.0.0" / "cards.json").write_text(
        json.dumps(
            {
                "cards": [
                    {
                        "loc_key": "THUNDERCLAP",
                        "title": "Thunderclap",
                        "description_template": "Deal {Damage} damage and apply "
                        "{VulnerablePower} Vulnerable to ALL enemies.",
                        "description_plain": "Deal 4 damage and apply 1 Vulnerable to ALL enemies.",
                        "upgraded_description_plain": "Deal 7 damage and apply 1 Vulnerable "
                        "to ALL enemies.",
                    }
                ]
            }
        )
    )
    loc = localization.load_card_localization(repo, wiki)
    assert loc["THUNDERCLAP.description_plain"].startswith("Deal 4 damage")
    assert loc["THUNDERCLAP.upgraded_description_plain"].startswith("Deal 7 damage")


def test_plain_description_picks_base_or_upgraded():
    loc = {
        "THUNDERCLAP.description_plain": "Deal 4 damage.",
        "THUNDERCLAP.upgraded_description_plain": "Deal 7 damage.",
    }
    assert localization.plain_description("THUNDERCLAP", False, loc) == "Deal 4 damage."
    assert localization.plain_description("THUNDERCLAP", True, loc) == "Deal 7 damage."


def test_plain_description_falls_back_to_base_when_no_upgraded():
    loc = {"HAVOC.description_plain": "Play the top card of your Draw Pile and Exhaust it."}
    assert localization.plain_description("HAVOC", True, loc).startswith("Play the top card")


def test_plain_description_missing_returns_none():
    assert localization.plain_description("UNKNOWN", False, {}) is None
    assert localization.plain_description(None, False, {}) is None


def test_card_var_value_resolves_power_suffix():
    loc = {"BASH.var.vulnerable": "2", "BASH.uvar.vulnerable": "3", "CLAW.var.increase": "2"}
    # Template references {VulnerablePower}; manifest stores it as "vulnerable".
    assert localization.card_var_value("BASH", "VulnerablePower", False, loc) == "2"
    assert localization.card_var_value("BASH", "VulnerablePower", True, loc) == "3"
    # Plain (non-power) var matches directly.
    assert localization.card_var_value("CLAW", "Increase", False, loc) == "2"
    # Unknown var / card -> None.
    assert localization.card_var_value("CLAW", "Nope", False, loc) is None
    assert localization.card_var_value(None, "Increase", False, loc) is None


def test_engine_manifest_wins_over_stale_repo_localization(tmp_path: Path):
    """Where both the vendored ``localization_eng/`` dump and the engine-pinned
    wiki manifest carry a key, the manifest must win — so template and plain
    text always come from the same file. This gate was written when the dump was
    a v0.103.2 asset overriding a v0.107.1 manifest, which made 29 cards render
    the wrong build: Pact's End read "Can only be played if..." (v0.103.2, an
    ``IsPlayable`` gate) while the engine let you play it for no effect
    (v0.107.1, an ``OnPlay`` gate). Bug report ``wb-66e36e372eef4bea``.

    Keys the manifest has NO value for still fall through to the dump, which is
    the only source for prompts like ``<KEY>.selectionScreenPrompt``.
    """
    repo = tmp_path / "cards.json"
    repo.write_text(
        json.dumps(
            {
                "PACTS_END.title": "Pact's End",
                "PACTS_END.description": "Can only be played if you have 3 cards.",
                "PACTS_END.selectionScreenPrompt": "Choose a card",
                "GONE_IN_V107.description": "A card the manifest dropped.",
            }
        )
    )
    wiki_data = tmp_path / "data"
    (wiki_data / localization._ENGINE_WIKI_VERSION).mkdir(parents=True)
    (wiki_data / localization._ENGINE_WIKI_VERSION / "cards.json").write_text(
        json.dumps(
            [
                {
                    "loc_key": "PACTS_END",
                    "title": "Pact's End",
                    "description_template": "If you have 3 cards, deal 17 damage.",
                }
            ]
        )
    )

    loc = localization.load_card_localization(repo, wiki_data)

    assert loc["PACTS_END.description"] == "If you have 3 cards, deal 17 damage."
    assert loc["PACTS_END.selectionScreenPrompt"] == "Choose a card"
    assert loc["GONE_IN_V107.description"] == "A card the manifest dropped."


def test_shipped_localization_resolves_pacts_end_to_v107_text():
    """End-to-end over the REAL shipped files: the workbench must render the
    v0.107.1 wording. Guards the whole 29-card drift class, not just this card.
    """
    repo_root = Path(localization.__file__).resolve().parents[2]
    wiki_data = repo_root.parent / "sts2-wiki" / "data"
    if not (wiki_data / localization._ENGINE_WIKI_VERSION / "cards.json").is_file():
        return  # sibling wiki checkout absent

    loc = localization.load_card_localization(
        repo_root / "localization_eng" / "cards.json", wiki_data
    )
    desc = loc["PACTS_END.description"]
    assert desc.startswith("If you have"), desc
    assert "Can only be played" not in desc
    # A second card from the same drift class, changed for a different reason.
    assert "twice" in loc["ASTRAL_PULSE.description"]


def test_keyword_card_lines_follow_the_games_own_order(tmp_path: Path):
    """Keywords print on the card face, and WHERE they print is not the order
    ``CardKeywordOrder.beforeDescription`` declares: the game ``Insert(0, …)``s
    each hit, so the before-lines come out reversed. Ascender's Bane reads
    "Unplayable." above "Ethereal.", with "Eternal." below the description."""
    wiki_data = tmp_path / "data"
    (wiki_data / localization._ENGINE_WIKI_VERSION).mkdir(parents=True)
    (wiki_data / localization._ENGINE_WIKI_VERSION / "cards.json").write_text(
        json.dumps(
            [
                {
                    "loc_key": "ASCENDERS_BANE",
                    "keywords": ["Eternal", "Unplayable", "Ethereal"],
                },
                # Gains a keyword only on upgrade (C# ``OnUpgrade`` AddKeyword).
                {
                    "loc_key": "AFTERIMAGE",
                    "upgrades": [{"action": "add_keyword", "keyword": "Innate"}],
                },
                # Loses one on upgrade.
                {
                    "loc_key": "APPARITION",
                    "keywords": ["Ethereal", "Exhaust"],
                    "upgrades": [{"action": "remove_keyword", "keyword": "Ethereal"}],
                },
                {"loc_key": "STRIKE_IRONCLAD"},
            ]
        )
    )
    loc = localization.load_card_localization(tmp_path / "missing.json", wiki_data)
    loc.update(localization.load_card_keyword_localization(tmp_path / "no-such-dir"))

    assert localization.keyword_card_lines("ASCENDERS_BANE", False, loc) == (
        ["[gold]Unplayable[/gold].", "[gold]Ethereal[/gold]."],
        ["[gold]Eternal[/gold]."],
    )
    # A card with no keywords gets no lines at all — not an empty string to join.
    assert localization.keyword_card_lines("STRIKE_IRONCLAD", False, loc) == ([], [])
    # Upgrade deltas apply only to the upgraded face.
    assert localization.keyword_card_lines("AFTERIMAGE", False, loc) == ([], [])
    assert localization.keyword_card_lines("AFTERIMAGE", True, loc) == (
        ["[gold]Innate[/gold]."],
        [],
    )
    assert localization.card_keywords("APPARITION", False, loc) == {"Ethereal", "Exhaust"}
    assert localization.card_keywords("APPARITION", True, loc) == {"Exhaust"}


def test_shipped_manifest_keywords_are_the_cards_own_not_its_hover_tips():
    """The wiki extractor used to scrape every ``CardKeyword.X`` in a card's C#
    source, so a card that Exhausts OTHER cards (Burning Pact, Corruption, Feel
    No Pain — all carry an Exhaust HOVER TIP) came out flagged Exhaust, and
    Afterimage came out Innate at base rather than on upgrade. 65 of 576 cards
    were wrong; every one of them would have printed a keyword line the real
    card does not have."""
    repo_root = Path(localization.__file__).resolve().parents[2]
    wiki_data = repo_root.parent / "sts2-wiki" / "data"
    if not (wiki_data / localization._ENGINE_WIKI_VERSION / "cards.json").is_file():
        return  # sibling wiki checkout absent

    loc = localization.load_card_localization(
        repo_root / "localization_eng" / "cards.json", wiki_data
    )
    loc.update(localization.load_card_keyword_localization(repo_root / "localization_eng"))

    assert localization.card_keywords("BURNING_PACT", False, loc) == set()
    assert localization.card_keywords("CORRUPTION", False, loc) == set()
    assert localization.card_keywords("FEEL_NO_PAIN", False, loc) == set()
    assert localization.card_keywords("AFTERIMAGE", False, loc) == set()
    assert localization.card_keywords("AFTERIMAGE", True, loc) == {"Innate"}
    # …and the card that started this: Tremble really does Exhaust.
    assert localization.keyword_card_lines("TREMBLE", False, loc) == ([], ["[gold]Exhaust[/gold]."])


def test_vendored_localization_describes_the_pinned_engine_build():
    """``localization_eng/`` must be the build the engine simulates.

    It was a v0.103.2 dump until 2026-08-24 while the decomp, the vendored
    headless ``lib/sts2.dll`` and the wiki manifest were all v0.107.1 — so every
    one of the ~64 places that reads it for display text was quoting a build the
    engine does not play. Re-vendor with ``scripts/vendor_localization.py`` and
    move ``_ENGINE_WIKI_VERSION`` in the same commit.
    """
    loc_dir = Path(localization.__file__).resolve().parents[2] / "localization_eng"
    build = (loc_dir / "BUILD.txt").read_text(encoding="utf-8").splitlines()[0].strip()
    assert build == localization._ENGINE_WIKI_VERSION

    cards = json.loads((loc_dir / "cards.json").read_text(encoding="utf-8"))
    # v0.107.1 replaced Pact's End's ``IsPlayable`` gate with an ``OnPlay`` check.
    assert "Can only be played" not in cards["PACTS_END.description"]
    # Content the game removed after v0.103.2 but the engine still implements
    # must survive the re-vendor, or it renders nameless.
    assert cards["GRAPPLE.title"] == "Grapple"
    assert cards["FOLLOW_THROUGH.title"] == "Follow Through"


def test_rest_site_option_names_come_from_the_games_own_rows():
    """Every rest-site option must resolve to a word, not a numbered button.

    The Rust backend emits ``rest_site.OPTION_<ID>.name`` for each option the
    engine offers; the map must cover all nine ``RestSiteOption`` subclasses,
    including the ones only a relic adds (Dig / Lift / Kindle / Cook) — those
    are exactly the indices an index-keyed label table could not name.
    """
    loc_dir = Path(localization.__file__).resolve().parents[2] / "localization_eng"
    loc = localization.load_rest_site_localization(loc_dir)
    assert loc["rest_site.OPTION_HEAL.name"] == "Rest"
    assert loc["rest_site.OPTION_SMITH.name"] == "Smith"
    assert loc["rest_site.OPTION_KINDLE.name"] == "Kindle"
    # The game calls Girya's option "Train", not "Lift" — a hand-written label
    # would have guessed wrong.
    assert loc["rest_site.OPTION_LIFT.name"] == "Train"
    for opt in localization._REST_SITE_OPTION_IDS:
        assert localization.resolve(f"rest_site.OPTION_{opt}.name", loc) != ""
        assert not localization.looks_like_unresolved_key(
            localization.resolve(f"rest_site.OPTION_{opt}.name", loc)
        )
