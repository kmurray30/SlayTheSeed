"""Resource-delta tracing: the two arms must produce diffable records.

The whole point of ``ev: delta`` is that an engine-world trace and a real-DLL
trace of the same string-seed can be diffed directly. That only works if both
arms name the same relic/card/potion the same way, so the cross-arm vocabulary
is the property under test here — not an implementation detail.

Neither tracer needs a game process: ``WireRunTracer._snapshot`` is a pure
function of the wire ``player`` dict, and ``RunTracer._snapshot`` only reads a
handful of ``RunManager`` accessors, which a stub supplies.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import pytest
from headless_eval import WireRunTracer


class _StubRunManager:
    """The `RunManager` surface `RunTracer._snapshot` / `observe` touch."""

    def __init__(
        self,
        *,
        gold: int = 0,
        hp: int = 50,
        max_hp: int = 50,
        relics: list[str] | None = None,
        potions: list[str] | None = None,
        deck: list[tuple[str, bool]] | None = None,
        phase: str = "map",
        floor: int = 1,
    ) -> None:
        self.gold = gold
        self.current_hp = hp
        self.max_hp = max_hp
        self.act_index = 1
        self._relics = relics or []
        self._potions = potions or []
        self._deck = deck or []
        self._phase = phase
        self._floor = floor

    def relics(self) -> list[str]:
        return list(self._relics)

    def potions(self) -> list[str]:
        return list(self._potions)

    def master_deck_detailed(self) -> list[tuple[str, bool]]:
        return list(self._deck)

    def phase(self) -> str:
        return self._phase

    def total_floor(self) -> int:
        return self._floor

    def act_floor(self) -> int:
        return self._floor

    def current_room(self) -> dict[str, Any] | None:
        return {"room_type": "monster"}


def _engine_tracer(tmp_path: Path):
    """Import lazily: `play_full_run_ppo` pulls in the `sts2_rs` extension."""
    pytest.importorskip("sts2_rs")
    from play_full_run_ppo import RunTracer

    return RunTracer(tmp_path / "engine.jsonl")


def _wire_player(
    *,
    gold: int = 0,
    hp: int = 50,
    max_hp: int = 50,
    relics: list[dict[str, Any]] | None = None,
    potions: list[dict[str, Any]] | None = None,
    deck: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    return {
        "gold": gold,
        "hp": hp,
        "max_hp": max_hp,
        "relics": relics or [],
        "potions": potions or [],
        "deck": deck or [],
    }


def _records(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


# ---------------------------------------------------------------------------
# The cross-arm vocabulary — the reason this feature exists.
# ---------------------------------------------------------------------------


class TestCrossArmVocabulary:
    """Same object, two worlds, one id.

    Each case pairs the exact strings the two producers emit for one object:
    the engine's ``rm.relics()`` / ``master_deck_detailed()`` / ``potions()``
    against the DLL's ``PlayerSummary`` entry. Both must land on the same
    canonical stem, or a diff reports a phantom gain and a phantom loss.
    """

    @pytest.mark.parametrize(
        ("engine_relic", "wire_relic"),
        [
            ("RELIC.SILVER_CRUCIBLE", "SILVER_CRUCIBLE"),
            ("RELIC.CHOSEN_CHEESE", "CHOSEN_CHEESE"),
        ],
    )
    def test_relics_agree(self, tmp_path: Path, engine_relic: str, wire_relic: str) -> None:
        engine = _engine_tracer(tmp_path)._snapshot(_StubRunManager(relics=[engine_relic]))
        wire = WireRunTracer._snapshot(_wire_player(relics=[{"id": wire_relic}]))
        assert engine["relics"] == wire["relics"]

    @pytest.mark.parametrize(
        ("engine_card", "wire_card"),
        [
            ("Unrelenting", "CARD.UNRELENTING"),
            ("SwordBoomerang", "CARD.SWORD_BOOMERANG"),
            ("ThrummingHatchet", "CARD.THRUMMING_HATCHET"),
            # The Rust id space appends `_CARD` / `_STATUS` to disambiguate
            # against a same-named power; the C# id has no such suffix.
            ("StampedeCard", "CARD.STAMPEDE"),
            ("SlothStatus", "CARD.SLOTH"),
        ],
    )
    def test_cards_agree(self, tmp_path: Path, engine_card: str, wire_card: str) -> None:
        engine = _engine_tracer(tmp_path)._snapshot(_StubRunManager(deck=[(engine_card, False)]))
        wire = WireRunTracer._snapshot(_wire_player(deck=[{"id": wire_card, "upgraded": False}]))
        assert engine["cards"] == wire["cards"]

    @pytest.mark.parametrize(
        ("engine_potion", "wire_entry"),
        [
            ("AttackPotion", "ATTACK_POTION"),
            ("BeetleJuice", "BEETLE_JUICE"),
        ],
    )
    def test_potions_agree(self, tmp_path: Path, engine_potion: str, wire_entry: str) -> None:
        engine = _engine_tracer(tmp_path)._snapshot(_StubRunManager(potions=[engine_potion]))
        wire = WireRunTracer._snapshot(
            _wire_player(potions=[{"entry": wire_entry, "name": "Attack Potion"}])
        )
        assert engine["potions"] == wire["potions"]

    def test_upgrade_suffix_survives_canonicalization(self, tmp_path: Path) -> None:
        """The `+` must be appended AFTER the id is normalised, on both sides."""
        engine = _engine_tracer(tmp_path)._snapshot(
            _StubRunManager(deck=[("SwordBoomerang", True)])
        )
        wire = WireRunTracer._snapshot(
            _wire_player(deck=[{"id": "CARD.SWORD_BOOMERANG", "upgraded": True}])
        )
        assert engine["cards"] == wire["cards"] == {"SWORD_BOOMERANG+": 1}

    def test_localized_potion_title_is_never_the_key(self) -> None:
        """A potion whose title differs from its entry words must still key on
        the entry — the title is not recoverable back to an id."""
        snap = WireRunTracer._snapshot(
            _wire_player(potions=[{"entry": "ASHWATER", "name": "Draught of Ashwater"}])
        )
        assert snap["potions"] == {"ASHWATER": 1}


# ---------------------------------------------------------------------------
# Delta computation.
# ---------------------------------------------------------------------------


class TestWireDelta:
    def test_first_observe_emits_no_delta(self, tmp_path: Path) -> None:
        """There is nothing to diff against yet."""
        path = tmp_path / "t.jsonl"
        tracer = WireRunTracer(path, seed=1)
        tracer.observe(
            {"context": {"total_floor": 1, "room_type": "Map"}, "player": _wire_player()}
        )
        tracer.finish({"player": _wire_player()}, won=False)
        assert [r for r in _records(path) if r["ev"] == "delta"] == []

    def test_in_place_upgrade_reads_as_remove_plus_add(self, tmp_path: Path) -> None:
        path = tmp_path / "t.jsonl"
        tracer = WireRunTracer(path, seed=1)
        base = {"context": {"total_floor": 5, "room_type": "RestSite"}, "decision": "rest_site"}
        tracer.observe(
            {**base, "player": _wire_player(deck=[{"id": "CARD.BASH", "upgraded": False}])}
        )
        tracer.observe(
            {
                "context": {"total_floor": 6, "room_type": "Map"},
                "decision": "map_select",
                "player": _wire_player(deck=[{"id": "CARD.BASH", "upgraded": True}]),
            }
        )
        tracer.finish({"player": _wire_player()}, won=False)
        (delta,) = [r for r in _records(path) if r["ev"] == "delta"]
        assert delta["cards_added"] == ["BASH+"]
        assert delta["cards_removed"] == ["BASH"]

    def test_delta_is_stamped_where_the_change_was_CAUSED(self, tmp_path: Path) -> None:
        """Location and decision must both come from the previous observe.

        The smith happens at the rest site on floor 5; the driver only notices
        one decision later, standing on the map at floor 6. Stamping the
        current location filed every event outcome under the room the player
        walked into next.
        """
        path = tmp_path / "t.jsonl"
        tracer = WireRunTracer(path, seed=1)
        tracer.observe(
            {
                "context": {"total_floor": 5, "act": 1, "room_type": "RestSite"},
                "decision": "rest_site",
                "player": _wire_player(deck=[{"id": "CARD.BASH", "upgraded": False}]),
            }
        )
        tracer.observe(
            {
                "context": {"total_floor": 6, "act": 1, "room_type": "Map"},
                "decision": "map_select",
                "player": _wire_player(deck=[{"id": "CARD.BASH", "upgraded": True}]),
            }
        )
        tracer.finish({"player": _wire_player()}, won=False)
        (delta,) = [r for r in _records(path) if r["ev"] == "delta"]
        assert delta["floor"] == 5
        assert delta["room_type"] == "RestSite"
        assert delta["after_decision"] == "rest_site"

    def test_hp_delta_is_suppressed_across_combat(self, tmp_path: Path) -> None:
        """In-combat HP flow belongs to the combat record, not to a delta."""
        path = tmp_path / "t.jsonl"
        tracer = WireRunTracer(path, seed=1)
        for hp in (50, 40, 30):
            tracer.observe(
                {
                    "context": {"total_floor": 3, "room_type": "Monster"},
                    "decision": "combat_play",
                    "player": _wire_player(hp=hp),
                }
            )
        tracer.finish({"player": _wire_player(hp=30)}, won=False)
        assert all("hp_delta" not in r for r in _records(path) if r["ev"] == "delta")

    def test_out_of_combat_hp_change_is_reported(self, tmp_path: Path) -> None:
        path = tmp_path / "t.jsonl"
        tracer = WireRunTracer(path, seed=1)
        for hp, dec in ((50, "event_choice"), (41, "map_select")):
            tracer.observe(
                {
                    "context": {"total_floor": 3, "room_type": "Event"},
                    "decision": dec,
                    "player": _wire_player(hp=hp),
                }
            )
        tracer.finish({"player": _wire_player(hp=41)}, won=False)
        (delta,) = [r for r in _records(path) if r["ev"] == "delta"]
        assert delta["hp_delta"] == -9
        assert delta["after_decision"] == "event_choice"

    def test_entry_without_an_id_is_dropped_not_bucketed_as_None(self) -> None:
        """A malformed relic must not become a phantom relic named "None"
        that every other id-less relic then merges into."""
        snap = WireRunTracer._snapshot(
            _wire_player(relics=[{"name": "Mystery"}, {"id": "AKABEKO"}])
        )
        assert snap["relics"] == {"AKABEKO": 1}

    def test_no_changes_means_no_record(self, tmp_path: Path) -> None:
        path = tmp_path / "t.jsonl"
        tracer = WireRunTracer(path, seed=1)
        player = _wire_player(gold=99, relics=[{"id": "AKABEKO"}])
        for _ in range(3):
            tracer.observe(
                {
                    "context": {"total_floor": 3, "room_type": "Map"},
                    "decision": "map_select",
                    "player": player,
                }
            )
        tracer.finish({"player": player}, won=False)
        assert [r for r in _records(path) if r["ev"] == "delta"] == []
