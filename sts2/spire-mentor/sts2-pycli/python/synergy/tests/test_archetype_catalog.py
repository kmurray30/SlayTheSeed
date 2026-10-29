"""Tests for synergy.archetype_catalog — UI archetype-file assembly (§9/§10)."""

from __future__ import annotations

from synergy.archetype_catalog import assemble_archetypes


def _archetypes_doc() -> dict:
    return {
        "character": "ironclad",
        "candidates": [
            {"id": "strength", "cards": ["INFLAME", "LIMIT_BREAK"], "tag": "confirmed-archetype"},
            {"id": "folk", "cards": ["A", "B"], "tag": "folklore"},
        ],
    }


def _dose_doc() -> dict:
    return {
        "character": "ironclad",
        "doses": [
            {
                "cards": ["INFLAME", "LIMIT_BREAK"],
                "tag": "confirmed",
                "v_curve": [-20.0, -17.0, -10.0],
                "additive_curve": [-20.0, -17.0, -14.0],
                "target_k": 2,
            }
        ],
    }


class TestAssembleArchetypes:
    def test_maps_tag_and_zips_curve(self) -> None:
        doc = assemble_archetypes(_archetypes_doc(), _dose_doc())
        assert doc["character"] == "ironclad"
        strength = next(a for a in doc["archetypes"] if a["id"] == "strength")
        assert strength["tag"] == "confirmed"  # -archetype suffix dropped
        assert strength["cards"] == [
            {"token": "INFLAME", "weight": 1.0},
            {"token": "LIMIT_BREAK", "weight": 1.0},
        ]
        assert strength["curve"]["targetK"] == 2
        assert strength["curve"]["points"][2] == {"k": 2, "v": -10.0, "additive": -14.0}

    def test_candidate_without_dose_gets_empty_curve(self) -> None:
        doc = assemble_archetypes(_archetypes_doc(), _dose_doc())
        folk = next(a for a in doc["archetypes"] if a["id"] == "folk")
        assert folk["tag"] == "folklore"
        assert folk["curve"]["points"] == []

    def test_works_without_dose_doc(self) -> None:
        doc = assemble_archetypes(_archetypes_doc(), None)
        assert len(doc["archetypes"]) == 2
        assert all(a["curve"]["points"] == [] for a in doc["archetypes"])

    def test_derives_id_and_label_when_absent(self) -> None:
        doc = assemble_archetypes(
            {"character": "silent", "candidates": [{"cards": ["X"], "tag": "undiscovered"}]},
            None,
        )
        a = doc["archetypes"][0]
        assert a["id"] == "arch-0"
        assert a["label"] == "Arch 0"
        assert a["tag"] == "undiscovered"
