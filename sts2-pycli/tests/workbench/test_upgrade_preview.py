"""``_enrich`` ships the upgraded twin of every option on an upgrade select.

The rest-site Smith / Armaments screens show the card as it is now next to the
card it becomes (the game's ``NUpgradePreview``), so the backend renders both
sides — the frontend never re-derives upgraded text.
"""

from pathlib import Path

from workbench.assets import AssetResolver
from workbench.server import _enrich

_LOC = {
    "BASH.title": "Bash",
    "BASH.description": "Deal {Damage:diff()} damage.",
    "BASH.description_plain": "Deal 8 damage.",
    "BASH.upgraded_description_plain": "Deal 10 damage.",
    "BASH.card_type": "Attack",
    "BASH.rarity": "Basic",
    "BASH.cost": "2",
    # Keyword-only upgrade: no upgraded description exists for these at all.
    "AFTERIMAGE.title": "Afterimage",
    "AFTERIMAGE.description": "Whenever you play a card, gain {Block:diff()} Block.",
    "AFTERIMAGE.description_plain": "Whenever you play a card, gain 1 Block.",
    "AFTERIMAGE.upgrade_keywords": "+Innate",
    # Cost-only upgrade: 57 cards upgrade by dropping an energy and nothing
    # else, so their upgraded text reads identically to the base.
    "STAMPEDE.title": "Stampede",
    "STAMPEDE.description": "At the end of your turn, 1 random Attack is played.",
    "STAMPEDE.description_plain": "At the end of your turn, 1 random Attack is played.",
    "STAMPEDE.upgraded_description_plain": "At the end of your turn, 1 random Attack is played.",
    "STAMPEDE.card_type": "Power",
    "STAMPEDE.rarity": "Uncommon",
    "STAMPEDE.cost": "2",
    "STAMPEDE.upgraded_cost": "1",
}


def _step(context: str, cards: list[dict]) -> dict:
    return {
        "decision": "card_select",
        "state": {
            "decision": "card_select",
            "context": context,
            "player": {"name": "Ironclad"},
            "cards": cards,
            "min_select": 1,
            "max_select": 1,
        },
        "chosen_action": None,
    }


def _card(index: int, cid: str) -> dict:
    return {
        "index": index,
        "id": cid,
        "name": f"{cid}.title",
        "description": f"{cid}.description",
        "upgraded": False,
        "type": "card",
    }


def test_upgrade_select_carries_the_upgraded_twin(tmp_path: Path):
    step = _step("upgrade", [_card(0, "BASH")])
    card = _enrich(step, AssetResolver(tmp_path), _LOC)["state"]["cards"][0]
    assert card["description"] == "Deal 8 damage."
    twin = card["upgrade_preview"]
    assert twin["upgraded"] is True
    assert twin["description"] == "Deal 10 damage."
    # The twin is a fully rendered card: same identity/type/rarity/cost as its base.
    assert (twin["name"], twin["type"], twin["rarity"], twin["cost"]) == (
        "Bash",
        "Attack",
        "Basic",
        2,
    )


def test_in_combat_upgrade_pick_previews_too(tmp_path: Path):
    """Armaments (``upgrade_in_hand``) upgrades the pick just like the Smith."""
    step = _step("upgrade_in_hand", [_card(0, "BASH")])
    card = _enrich(step, AssetResolver(tmp_path), _LOC)["state"]["cards"][0]
    assert card["upgrade_preview"]["description"] == "Deal 10 damage."


def test_keyword_only_upgrade_names_its_delta(tmp_path: Path):
    """~48 cards upgrade by keyword alone and have NO upgraded description.
    The keyword still SHOWS: the game prints it as its own line on the card
    face, so Afterimage+ gains an "Innate." line above its rules text. The
    ``keyword_delta`` label rides alongside as the compact form."""
    step = _step("upgrade", [_card(0, "AFTERIMAGE")])
    out = _enrich(step, AssetResolver(tmp_path), _LOC)["state"]["cards"][0]
    twin = out["upgrade_preview"]
    assert twin["keyword_delta"] == "+Innate"
    assert out["description"] == "Whenever you play a card, gain 1 Block."
    assert twin["description"] == "Innate.\nWhenever you play a card, gain 1 Block."


def test_no_preview_for_non_upgrade_selects_or_upgraded_cards(tmp_path: Path):
    removal = _enrich(_step("remove", [_card(0, "BASH")]), AssetResolver(tmp_path), _LOC)
    assert "upgrade_preview" not in removal["state"]["cards"][0]
    already = _card(0, "BASH") | {"upgraded": True}
    out = _enrich(_step("upgrade", [already]), AssetResolver(tmp_path), _LOC)
    assert "upgrade_preview" not in out["state"]["cards"][0]


def test_live_stats_do_not_leak_into_the_twin(tmp_path: Path):
    """A hand card's ``stats`` are its UN-upgraded numbers; keeping them would
    render the twin with the old damage (i.e. as no upgrade at all)."""
    step = _step("upgrade_in_hand", [_card(0, "BASH") | {"stats": {"damage": 8}}])
    card = _enrich(step, AssetResolver(tmp_path), _LOC)["state"]["cards"][0]
    assert card["description"] == "Deal 8 damage."
    assert card["upgrade_preview"]["description"] == "Deal 10 damage."


def test_cost_reducing_upgrade_previews_the_new_cost(tmp_path: Path):
    """Stampede+ costs 1, not 2. The twin is a deep copy of the un-upgraded
    option, so without clearing the copied cost the preview drew the old one --
    and outside combat there is no cost on the wire at all, so the backfill has
    to read the manifest's ``upgraded_cost``."""
    step = _step("upgrade", [_card(0, "STAMPEDE")])
    card = _enrich(step, AssetResolver(tmp_path), _LOC)["state"]["cards"][0]
    assert card["cost"] == 2
    assert card["upgrade_preview"]["cost"] == 1


def test_upgrade_preview_keeps_a_live_discount(tmp_path: Path):
    """In combat (Armaments) the option carries a live cost, which a hook may
    have discounted. Upgrading does not clear that discount, so the twin shifts
    both the effective and the printed cost by the upgrade delta instead of
    overwriting them."""
    discounted = _card(0, "STAMPEDE") | {"cost": 1, "base_cost": 2}
    step = _step("upgrade_in_hand", [discounted])
    twin = _enrich(step, AssetResolver(tmp_path), _LOC)["state"]["cards"][0]["upgrade_preview"]
    assert (twin["cost"], twin["base_cost"]) == (0, 1)


def test_upgrade_preview_never_previews_a_negative_cost(tmp_path: Path):
    """A hook that ZEROED the cost outright (Unrelenting) leaves nothing for
    the upgrade's -1 to come off, so the preview floors at 0."""
    free = _card(0, "STAMPEDE") | {"cost": 0, "base_cost": 2}
    step = _step("upgrade_in_hand", [free])
    twin = _enrich(step, AssetResolver(tmp_path), _LOC)["state"]["cards"][0]["upgrade_preview"]
    assert (twin["cost"], twin["base_cost"]) == (0, 1)


def test_upgraded_deck_card_shows_its_upgraded_cost(tmp_path: Path):
    """The deck view ships ``upgraded`` but no cost, so an already-upgraded
    Stampede+ printed its un-upgraded 2 until the backfill learned to read
    ``upgraded_cost``."""
    step = _step("remove", [_card(0, "STAMPEDE") | {"upgraded": True}])
    card = _enrich(step, AssetResolver(tmp_path), _LOC)["state"]["cards"][0]
    assert card["cost"] == 1
