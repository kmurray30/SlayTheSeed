"""Unit tests for the event-EFFECT parity comparison core (`event_parity`).

Pure-Python — no game engine, no wheel. These cover the effect-diff logic that
`effect_parity` applies to each resolved option (`_effect_fields` /
`_follow_options`): does it surface a missing potion / a no-op deck mutation / a
finish-vs-continue phase split / a multi-page follow-up divergence, and stay
silent on a faithful resolution. The live cross-engine resolution is exercised
by the harness itself (`event_parity.py --effect`), not here.
"""

from __future__ import annotations

from event_parity import _effect_fields, _follow_options
from run_parity import Snap


def _snap(
    *,
    phase: str = "map",
    hp: int = 80,
    max_hp: int = 80,
    gold: int = 99,
    deck: dict[str, int] | None = None,
    relics: list[str] | None = None,
    potions: list[str] | None = None,
) -> Snap:
    """A Snap with sensible defaults; deck_size is derived from the multiset."""
    deck = deck or {"STRIKE|0": 5}
    return Snap(
        phase=phase,
        hp=hp,
        max_hp=max_hp,
        block=0,
        gold=gold,
        deck=deck,
        deck_size=sum(deck.values()),
        relics=relics or ["BURNINGBLOOD"],
        potions=potions or [],
        enemies=(),
    )


def _view(options: list | None = None) -> dict:
    return {"options": options or []}


def test_faithful_resolution_has_no_fields() -> None:
    """Identical post-choice state on both engines → no divergence."""
    cs = _snap()
    rs = _snap()
    assert _effect_fields(cs, _view(), rs, _view()) == []


def test_missing_potion_is_flagged() -> None:
    """PotionCourier-shape: C# adds potions, Rust adds none."""
    cs = _snap(potions=["FOULPOTION", "FOULPOTION", "FOULPOTION"])
    rs = _snap(potions=[])
    fields = _effect_fields(cs, _view(), rs, _view())
    assert any("potions" in f for f in fields)


def test_noop_deck_mutation_is_flagged() -> None:
    """SpiritGrafter-shape: C# deck grows, Rust deck unchanged."""
    cs = _snap(deck={"STRIKE|0": 5, "DEFEND|0": 5, "BASH|0": 1})  # 11
    rs = _snap(deck={"STRIKE|0": 5, "DEFEND|0": 5})  # 10
    fields = _effect_fields(cs, _view(), rs, _view())
    assert any("deck" in f for f in fields)


def test_missing_relic_is_flagged() -> None:
    """WarHistorianRepy-shape: C# grants a relic Rust does not."""
    cs = _snap(relics=["BURNINGBLOOD", "VAMBRACE"])
    rs = _snap(relics=["BURNINGBLOOD"])
    fields = _effect_fields(cs, _view(), rs, _view())
    assert any("relics" in f for f in fields)


def test_finish_vs_continue_phase_split_is_flagged() -> None:
    """TinkerTime-shape: C# stays in the event (multi-page), Rust finishes."""
    cs = _snap(phase="event")
    rs = _snap(phase="map")
    fields = _effect_fields(cs, _view(options=[{"index": 0}]), rs, _view())
    assert any("phase" in f for f in fields)


def test_multipage_followup_option_divergence_is_flagged() -> None:
    """Both still in the event, but a chain branches to a different page."""
    cs = _snap(phase="event")
    rs = _snap(phase="event")
    cs_view = _view(options=[{"index": 0}, {"index": 1}])
    rs_view = _view(options=[{"index": 0}])
    fields = _effect_fields(cs, cs_view, rs, rs_view)
    assert any("followup_options" in f for f in fields)


def test_followup_count_only_compared_inside_event_phase() -> None:
    """Outside the event phase, a stray options list must NOT be compared."""
    cs = _snap(phase="map")
    rs = _snap(phase="map")
    cs_view = _view(options=[{"index": 0}, {"index": 1}])
    rs_view = _view(options=[])
    assert _effect_fields(cs, cs_view, rs, rs_view) == []


def test_follow_options_counts_and_tolerates_missing() -> None:
    assert _follow_options({"options": [{"index": 0}, {"index": 1}]}) == 2
    assert _follow_options({}) == 0
    assert _follow_options({"options": None}) == 0
