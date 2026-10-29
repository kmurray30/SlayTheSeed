"""Pure-dict passthrough tests for the enemy private-AI-state disclosures.

2026-08-02 wire-completeness round: both C# producers now emit
``fabricator_last_spawned`` (canonical ``Id.Entry`` of the last bot
``Fabricator._lastSpawned`` points at) and ``curse_of_knowledge_counter``
(``KnowledgeDemon._curseOfKnowledgeCounter``) on the enemy/creature object.
These tests pin the two python consumers:

* ``realgame_adapter._translate_enemy`` (bridge dialect -> headless dialect)
  forwards both fields verbatim, and stays silent when a pre-field mod build
  omits them.
* ``sts2_hint.adapter._translate_enemy`` (headless dialect -> engine dict)
  forwards both fields into the ``state_from_dict`` enemy dict --
  forward-compat plumbing for the engine's reviver logic (which today infers
  the Fabricator counter from the max-uid living bot and rebuilds the curse
  counter at 0).

No game process and no ``sts2_rs`` needed.
"""

from __future__ import annotations

from typing import Any

from realgame_adapter import _translate_enemy as bridge_translate_enemy
from sts2_hint.adapter import _translate_enemy as hint_translate_enemy


def _bridge_enemy(**extra: Any) -> dict[str, Any]:
    return {
        "id": "FABRICATOR",
        "name": "Fabricator",
        "hp": 120,
        "max_hp": 155,
        "block": 0,
        "is_alive": True,
        "powers": [],
        "intents": [],
        "next_move_id": "FABRICATE_MOVE",
        **extra,
    }


def _headless_enemy(name: str, **extra: Any) -> dict[str, Any]:
    return {
        "index": 0,
        "name": name,
        "hp": 120,
        "max_hp": 155,
        "block": 0,
        "intents": None,
        "powers": None,
        "move_state": "FABRICATE_MOVE",
        **extra,
    }


class TestRealgameAdapterPassthrough:
    def test_forwards_fabricator_last_spawned_and_curse_counter(self) -> None:
        out = bridge_translate_enemy(
            _bridge_enemy(fabricator_last_spawned="ZAPBOT", curse_of_knowledge_counter=2),
            0,
        )
        assert out["fabricator_last_spawned"] == "ZAPBOT"
        assert out["curse_of_knowledge_counter"] == 2

    def test_absent_fields_stay_absent(self) -> None:
        """A pre-field mod build must behave exactly as before -- no key, not
        a null/zero claim the engine would mistake for a disclosure."""
        out = bridge_translate_enemy(_bridge_enemy(), 0)
        assert "fabricator_last_spawned" not in out
        assert "curse_of_knowledge_counter" not in out

    def test_curse_counter_zero_is_forwarded(self) -> None:
        """0 is a real disclosure (pre-first-curse), distinct from absent."""
        out = bridge_translate_enemy(_bridge_enemy(curse_of_knowledge_counter=0), 0)
        assert out["curse_of_knowledge_counter"] == 0

    def test_malformed_values_dropped(self) -> None:
        out = bridge_translate_enemy(
            _bridge_enemy(fabricator_last_spawned="", curse_of_knowledge_counter=True),
            0,
        )
        assert "fabricator_last_spawned" not in out
        assert "curse_of_knowledge_counter" not in out


class TestHintAdapterPassthrough:
    def test_forwards_into_engine_enemy_dict(self) -> None:
        raw = _headless_enemy(
            "Fabricator", fabricator_last_spawned="STABBOT", curse_of_knowledge_counter=1
        )
        out = hint_translate_enemy(raw, "$.enemies[0]", 0)
        assert out["kind"] == "Fabricator"
        assert out["fabricator_last_spawned"] == "STABBOT"
        assert out["curse_of_knowledge_counter"] == 1

    def test_absent_fields_stay_absent(self) -> None:
        out = hint_translate_enemy(_headless_enemy("Fabricator"), "$.enemies[0]", 0)
        assert "fabricator_last_spawned" not in out
        assert "curse_of_knowledge_counter" not in out

    def test_curse_counter_zero_survives_translation(self) -> None:
        raw = _headless_enemy("Knowledge Demon", curse_of_knowledge_counter=0)
        out = hint_translate_enemy(raw, "$.enemies[0]", 0)
        assert out["kind"] == "KnowledgeDemon"
        assert out["curse_of_knowledge_counter"] == 0
