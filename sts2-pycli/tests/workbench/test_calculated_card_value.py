"""A card whose value is computed at play time must show the live number.

Ashen Strike is "Deal 6 damage." at rest and "Deal 18 damage." with four cards
in the Exhaust Pile — the game re-renders the face as the board changes. The
engine computes that from the same function its play handler uses and ships it
as ``calculated_value``; these tests pin how the workbench consumes it.

The failure mode this guards is specific and was live for a day: when the
manifest gained ``CalculationBase`` (PR #513), the in-combat gap-filler started
answering ``{CalculatedHits}`` with that base — which for the 25 cards whose
value only exists on a live board is **0**. Barrage's face read "(Hits 0
times)" mid-combat, stated as fact. Absent must stay absent.
"""

import pytest
from workbench import localization, render


@pytest.fixture(scope="module")
def loc():
    from pathlib import Path

    wiki = Path(__file__).resolve().parents[3] / "sts2-wiki" / "data"
    if not wiki.is_dir():
        pytest.skip("sibling sts2-wiki checkout not present")
    return localization.load_card_localization(Path("unused.json"), wiki)


def enrich(card_id, loc, *, in_combat, calculated=None, wire_stats=None, osty_alive=False):
    """The branch structure of ``server.py``'s ``enrich_card`` description path.

    Kept as a local reimplementation rather than a call into the FastAPI app so
    the test states the contract in one screen; the assertions below are about
    which branch is taken, which is exactly what this mirrors.
    """
    template = localization.resolve(f"{card_id}.description", loc)
    stats = dict(wire_stats or {}) if in_combat else None
    flags = {"istargeting": False, "isostyalive": osty_alive}
    needed = render.required_stat_vars(
        template, in_combat=in_combat, upgraded=False, extra_flags=flags
    )
    have = {k.lower() for k in (stats or {})}
    missing = needed - have
    if missing and in_combat:
        filled = dict(stats or {})
        for var in missing:
            if var.startswith("calculated"):
                if calculated is not None:
                    filled[var] = calculated
                continue
            base = localization.card_var_value(card_id, var, False, loc)
            if base is not None:
                filled[var] = int(base) if base.lstrip("-").isdigit() else base
        stats = filled
        missing = needed - {k.lower() for k in stats}
    plain = localization.plain_description(card_id, False, loc) if missing else None
    if plain:
        return render.resolve_card_text(plain, None)
    return render.resolve_card_text(
        template, stats, in_combat=in_combat, upgraded=False, extra_flags=flags
    )


def test_live_value_reaches_the_card_face(loc):
    """With the engine's number on the wire, the face shows it — not the base."""
    text = enrich("ASHEN_STRIKE", loc, in_combat=True, calculated=18, wire_stats={"damage": 6})
    assert text.startswith("Deal 18 damage."), text


def test_at_rest_face_shows_the_base(loc):
    """Outside combat the multiplier is zero, so the game shows the plain base
    and so must the workbench."""
    text = enrich("ASHEN_STRIKE", loc, in_combat=False)
    assert text.startswith("Deal 6 damage."), text


@pytest.mark.parametrize(
    ("card_id", "wire_stats"),
    [
        ("BARRAGE", {"damage": 5}),
        ("COMPILE_DRIVER", {"damage": 7}),
        ("STACK", {"block": 0}),
        ("MIRAGE", {"block": 0}),
        ("VOLTAIC", {}),
    ],
)
def test_absent_value_never_renders_as_zero(loc, card_id, wire_stats):
    """No number on the wire must mean no parenthetical — never "(Hits 0 times)".

    These cards state their live value inside an ``{InCombat:…}`` readout the
    game only draws on the board. A fabricated zero there is a false statement
    about the board, which is worse than the at-rest text.
    """
    text = enrich(card_id, loc, in_combat=True, calculated=None, wire_stats=wire_stats)
    assert " 0 " not in text and "(" not in text, text
    assert text == enrich(card_id, loc, in_combat=False), text


def test_present_zero_is_allowed(loc):
    """A real zero from the engine IS the truth — Barrage with no Orbs
    Channeled genuinely hits zero times, and the game says so."""
    text = enrich("BARRAGE", loc, in_combat=True, calculated=0, wire_stats={"damage": 5})
    assert "(Hits 0 times)" in text, text


def test_osty_flag_unlocks_the_live_readout(loc):
    """Sacrifice's readout is gated on ``{IsOstyAlive:…}``, which is not a stat.

    Without the flag the renderer treats the name as a missing var and the card
    falls back to its at-rest text, so the live Block number never appears.
    """
    alive = enrich("SACRIFICE", loc, in_combat=True, calculated=40, osty_alive=True)
    assert "(Gain 40 Block)" in alive, alive
    dead = enrich("SACRIFICE", loc, in_combat=True, calculated=0, osty_alive=False)
    assert "(Gain 0 Block)" in dead, dead


def test_wire_field_survives_the_card_projection():
    """``_combat_card_dict`` is an allow-list, so a new wire field reaches the
    frontend only if it is named there.

    This is where the chain actually broke during development: the engine
    emitted ``calculated_value``, the server consumed it, and nothing arrived —
    the projection in between silently dropped it.
    """
    from workbench.backends.rust_game import _combat_card_dict

    wire = {
        "id": "ASHEN_STRIKE",
        "base_damage": 6,
        "cost_override": 1,
        "kind": "card",
        "calculated_value": 18,
    }
    card = _combat_card_dict(0, wire, True, "AnyEnemy")
    assert card["calculated_value"] == 18

    # Absent stays absent — the key must not appear as a zero or a None.
    plain = _combat_card_dict(
        0, {k: v for k, v in wire.items() if k != "calculated_value"}, True, "AnyEnemy"
    )
    assert "calculated_value" not in plain
