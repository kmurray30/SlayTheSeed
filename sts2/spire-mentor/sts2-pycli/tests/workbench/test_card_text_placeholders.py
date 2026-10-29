"""The shipped card text must not leak the template's unresolved placeholders.

``extract_cards.py`` renders a card's description by substituting the game's
SmartFormat template, and marks anything it could not resolve with a literal
"X". That fallback used to fire for values the decompile *does* state — a
``CalculationBaseVar`` the var parser never matched, a ``{TargetType:choose…}``
branch it had no handler for — so Ashen Strike shipped as "Deal X damage."
instead of "Deal 6 damage.", and Sovereign Blade as "Deal 10 damageXX.X".

The X is legitimate for exactly one thing: a card whose displayed value IS the
X-cost the player chooses (Whirlwind, Cascade, Tempest). Anything else with an X
in its text is an extraction gap, so this pins the closed set.
"""

import json
import re
from pathlib import Path

_CARD_DB = Path(__file__).resolve().parents[2] / "python" / "workbench" / "data" / "card_db.json"

# Cards the game itself prints an "X" on, because the value is the X-cost the
# player pays (or, for Dirge/Heavenly Drill, is derived from it).
_X_COST_CARDS = frozenset(
    {
        "CASCADE",
        "DIRGE",
        "ERADICATE",
        "HEAVENLY_DRILL",
        "MALAISE",
        "MULTI_CAST",
        "SKEWER",
        "STARDUST",
        "TEMPEST",
        "VOLLEY",
        "WHIRLWIND",
    }
)


def _cards() -> dict[str, dict]:
    return json.loads(_CARD_DB.read_text(encoding="utf-8"))


def test_no_unresolved_placeholder_x_outside_the_x_cost_cards():
    offenders = {
        key: card["desc"]
        for key, card in _cards().items()
        if "X" in (card.get("desc") or "") and key not in _X_COST_CARDS
    }
    assert not offenders, f"unresolved template placeholders leaked as 'X': {offenders}"


def test_x_cost_cards_still_say_x():
    """Guard the other direction: a var lookup that resolves too eagerly would
    replace the player-chosen X with a base value and silently mis-state the
    card."""
    cards = _cards()
    missing = {k for k in _X_COST_CARDS if "X" not in (cards[k].get("desc") or "")}
    assert not missing, f"X-cost cards lost their X: {missing}"


def test_no_stray_template_syntax_survives():
    """Braces, dangling ``|}`` and bare var names are the other shapes an
    unrendered directive takes once the X substitution has run over it."""
    bad = {
        key: card["desc"]
        for key, card in _cards().items()
        if re.search(r"[{}]|\|\}", card.get("desc") or "")
    }
    assert not bad, f"template syntax survived into shipped card text: {bad}"


def test_scaling_attacks_state_their_base_damage():
    """The regression that started this: a card whose damage is
    ``CalculationBase + ExtraDamage x <runtime count>`` must show the base, which
    is what the real game shows outside combat."""
    cards = _cards()
    for key, expected in (
        ("ASHEN_STRIKE", "Deal 6 damage."),
        ("PERFECTED_STRIKE", "Deal 6 damage."),
        ("REND", "Deal 15 damage."),
        ("SHIV", "Deal 4 damage."),
        ("SOVEREIGN_BLADE", "Deal 10 damage."),
    ):
        assert cards[key]["desc"].startswith(expected), (key, cards[key]["desc"])
