"""Smoke tests for synergy.cli — emit-observational subcommand.

Runs against the small hand-written fixture corpus; the real corpus is NOT
touched by the automated suite.
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from synergy.cli import main
from synergy.embed_geometry import champion_onnx_path

_FIXTURE_ROOT = Path(__file__).resolve().parent / "fixtures" / "codex"

# The fixture has exactly 2 valid decks (valid_won + valid_lost).
_FIXTURE_RUN_COUNT = 2

_EXPECTED_TOP_KEYS = {
    "generated_at",
    "character",
    "run_count",
    "freq_floor",
    "skill_bucket",
    "build_ids",
    "card_frequency",
    "pairs",
}

_EXPECTED_PAIR_KEYS = {
    "pair",
    "pmi",
    "interaction_lr",
    "interaction_ci",
    "fm_S",
    "cooccur",
    "p_a",
    "p_b",
    "p_ab",
}


def test_emit_observational_smoke(tmp_path: Path) -> None:
    """end-to-end smoke: valid JSON with expected structure from the fixture corpus."""
    out_file = tmp_path / "obs_catalog.json"

    main(
        [
            "emit-observational",
            "--root",
            str(_FIXTURE_ROOT),
            "--freq-floor",
            "1",
            "--out",
            str(out_file),
        ]
    )

    assert out_file.exists(), "output file was not created"

    data = json.loads(out_file.read_text())

    # Top-level keys
    assert _EXPECTED_TOP_KEYS.issubset(data.keys()), (
        f"missing keys: {_EXPECTED_TOP_KEYS - data.keys()}"
    )

    # run_count matches fixture
    assert data["run_count"] == _FIXTURE_RUN_COUNT

    # character default
    assert data["character"] == "ironclad"

    # card_frequency non-empty
    assert isinstance(data["card_frequency"], dict)
    assert len(data["card_frequency"]) > 0, "card_frequency should not be empty"

    # pairs is a list (may be empty for freq_floor=1 with tiny fixture since
    # most pairs need ≥1 co-occurrence; STRIKE_IRONCLAD appears in both decks)
    assert isinstance(data["pairs"], list)
    assert len(data["pairs"]) > 0, (
        "expected at least one pair (STRIKE_IRONCLAD co-occurs in both decks)"
    )

    # Each pair has the right keys
    for pair_entry in data["pairs"]:
        assert _EXPECTED_PAIR_KEYS.issubset(pair_entry.keys()), (
            f"pair missing keys: {_EXPECTED_PAIR_KEYS - pair_entry.keys()}"
        )
        assert isinstance(pair_entry["pair"], list)
        assert len(pair_entry["pair"]) == 2

    # build_ids is a list
    assert isinstance(data["build_ids"], list)

    # freq_floor echoed back
    assert data["freq_floor"] == 1

    # skill_bucket is null (default)
    assert data["skill_bucket"] is None


def test_emit_catalog_multiplicity_smoke(tmp_path: Path) -> None:
    """emit-catalog --multiplicity emits shrunk_S/pvalue/qvalue on every edge."""
    obs_file = tmp_path / "obs_catalog.json"
    cat_file = tmp_path / "catalog.json"

    main(
        [
            "emit-observational",
            "--root",
            str(_FIXTURE_ROOT),
            "--freq-floor",
            "1",
            "--n-boot",
            "5",
            "--out",
            str(obs_file),
        ]
    )

    main(
        [
            "emit-catalog",
            "--obs",
            str(obs_file),
            "--out",
            str(cat_file),
            "--multiplicity",
            "--root",
            str(_FIXTURE_ROOT),
            "--freq-floor",
            "1",
            "--n-perm",
            "10",
        ]
    )

    cat = json.loads(cat_file.read_text())
    assert cat["edges"], "expected at least one edge"
    for edge in cat["edges"]:
        assert "shrunk_S" in edge
        assert "pvalue" in edge
        assert "qvalue" in edge
        # With --multiplicity the three fields are populated (not None) for pairs
        # the point estimator scored (the fixture pairs all clear freq-floor 1).
        assert edge["qvalue"] is not None
        assert 0.0 < edge["pvalue"] <= 1.0
        assert 0.0 <= edge["qvalue"] <= 1.0


# Resolved, not hardcoded — a baked-in filename would silently pin a retired
# net at the next Ironclad promotion and this smoke test would skip forever.
_PPO_IRONCLAD = champion_onnx_path("ironclad")


@pytest.mark.skipif(not _PPO_IRONCLAD.exists(), reason="champion ONNX not on disk")
def test_emit_geometry_smoke(tmp_path: Path) -> None:
    """emit-geometry writes a geometry doc with clusters and kNN from the champion."""
    obs_file = tmp_path / "obs.json"
    # Minimal obs_catalog carrying a card_frequency map (all cards pass the gate).
    import ppo.vocab as vocab

    freq = {name: 0.5 for name in vocab.cards().names}
    obs_file.write_text(json.dumps({"card_frequency": freq}))

    out_file = tmp_path / "geom.json"
    main(
        [
            "emit-geometry",
            "--character",
            "ironclad",
            "--obs",
            str(obs_file),
            "--out",
            str(out_file),
            "--k",
            "5",
        ]
    )

    doc = json.loads(out_file.read_text())
    assert doc["character"] == "ironclad"
    assert doc["n_cards"] > 100
    assert doc["k"] == 5
    assert isinstance(doc["clusters"], dict)
    assert isinstance(doc["knn"], dict)
    assert len(set(doc["clusters"].values())) >= 2


def test_emit_archetypes_smoke(tmp_path: Path) -> None:
    """emit-archetypes discovers/tags candidates from the fixture corpus + a catalog."""
    # Minimal catalog with a positive causal triangle → a View-2 community.
    catalog = tmp_path / "catalog.json"
    catalog.write_text(
        json.dumps(
            {
                "edges": [
                    {"pair": ["BASH", "STRIKE_IRONCLAD"], "combat_S": 1.0, "qvalue": 0.01},
                    {"pair": ["BASH", "DEFEND_IRONCLAD"], "combat_S": 1.0, "qvalue": 0.01},
                    {
                        "pair": ["STRIKE_IRONCLAD", "DEFEND_IRONCLAD"],
                        "combat_S": 1.0,
                        "qvalue": 0.01,
                    },
                ]
            }
        )
    )

    out_file = tmp_path / "archetypes.json"
    main(
        [
            "emit-archetypes",
            "--catalog",
            str(catalog),
            "--root",
            str(_FIXTURE_ROOT),
            "--n-components",
            "1",
            "--top-k",
            "3",
            "--n-restarts",
            "2",
            "--out",
            str(out_file),
        ]
    )

    doc = json.loads(out_file.read_text())
    assert doc["character"] == "ironclad"
    assert isinstance(doc["candidates"], list)
    # The causal triangle yields at least one community-backed candidate.
    tags = {c["tag"] for c in doc["candidates"]}
    assert tags <= {"confirmed-archetype", "folklore", "undiscovered"}
    assert doc["candidates"]  # non-empty


_RS_FIGHT = Path(__file__).resolve().parents[4] / "sts2-rs" / "target" / "release" / "synergy_fight"


@pytest.mark.skipif(
    not (_RS_FIGHT.exists() and _PPO_IRONCLAD.exists()),
    reason="synergy_fight binary or champion ONNX not on disk",
)
def test_emit_dose_smoke(tmp_path: Path) -> None:
    """emit-dose runs one tiny archetype dose panel end to end (binary-gated)."""
    archetypes = tmp_path / "arch.json"
    archetypes.write_text(
        json.dumps(
            {
                "character": "ironclad",
                "candidates": [
                    {
                        "cards": ["BASH", "IRON_WAVE"],
                        "tag": "folklore",
                        "jaccard": 0.0,
                        "source": "nmf",
                    }
                ],
            }
        )
    )
    # Geometry only needs a clusters map for control_match.
    geometry = tmp_path / "geom.json"
    geometry.write_text(json.dumps({"clusters": {"BASH": 0, "IRON_WAVE": 0}}))

    out_file = tmp_path / "dose.json"
    main(
        [
            "emit-dose",
            "--archetypes",
            str(archetypes),
            "--geometry",
            str(geometry),
            "--character",
            "ironclad",
            "--k",
            "2",
            "--target-k",
            "2",
            "--n-draws",
            "1",
            "--n-null",
            "2",
            "--sims",
            "80",
            "--seeds",
            "1",
            "--encounters",
            "BYGONE_EFFIGY_ELITE",
            "--out",
            str(out_file),
        ]
    )

    doc = json.loads(out_file.read_text())
    assert doc["character"] == "ironclad"
    assert len(doc["doses"]) == 1
    d = doc["doses"][0]
    assert "convexity_gap" in d
    assert "null_percentile" in d
    assert len(d["v_curve"]) == 3  # k = 0..2


@pytest.mark.skipif(not _PPO_IRONCLAD.exists(), reason="champion ONNX not on disk")
def test_emit_injection_smoke(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    """emit-injection wires archetypes + geometry + a mocked run_arm into verdicts."""
    from synergy import causal_run

    # Mock the arena arm: strong package (BASH+…) wins more often than its control.
    def fake_arm(pkg, seeds, **kw):
        strong = "BASH" in pkg
        return [(s % 2 == 0) if strong else (s % 4 == 0) for s in seeds]

    monkeypatch.setattr(causal_run, "run_arena_arm", fake_arm)

    archetypes = tmp_path / "arch.json"
    archetypes.write_text(
        json.dumps(
            {
                "character": "ironclad",
                "candidates": [
                    {
                        "id": "a0",
                        "cards": ["BASH", "IRON_WAVE", "INFLAME"],
                        "tag": "confirmed",
                        "jaccard": 0.5,
                        "source": "nmf",
                    }
                ],
            }
        )
    )
    geometry = tmp_path / "geom.json"
    geometry.write_text(json.dumps({"clusters": {"BASH": 0, "IRON_WAVE": 0, "INFLAME": 0}}))

    out_file = tmp_path / "injection.json"
    main(
        [
            "emit-injection",
            "--archetypes",
            str(archetypes),
            "--geometry",
            str(geometry),
            "--character",
            "ironclad",
            "--top",
            "1",
            "--k",
            "3",
            "--seeds",
            "40",
            "--out",
            str(out_file),
        ]
    )

    doc = json.loads(out_file.read_text())
    assert len(doc["injections"]) == 1
    inj = doc["injections"][0]
    assert inj["wr_pkg"] > inj["wr_ctrl"]
    assert inj["wr_delta"] > 0
    assert inj["seeds"] == 40


def test_emit_archetype_catalog_smoke(tmp_path: Path) -> None:
    """emit-archetype-catalog merges archetypes + dose into the UI schema."""
    archetypes = tmp_path / "arch.json"
    archetypes.write_text(
        json.dumps(
            {
                "character": "ironclad",
                "candidates": [
                    {"id": "s", "cards": ["INFLAME", "LIMIT_BREAK"], "tag": "confirmed-archetype"}
                ],
            }
        )
    )
    dose = tmp_path / "dose.json"
    dose.write_text(
        json.dumps(
            {
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
        )
    )

    out_file = tmp_path / "archetypes_ui.json"
    main(
        [
            "emit-archetype-catalog",
            "--archetypes",
            str(archetypes),
            "--dose",
            str(dose),
            "--out",
            str(out_file),
        ]
    )

    doc = json.loads(out_file.read_text())
    assert doc["character"] == "ironclad"
    a = doc["archetypes"][0]
    assert a["tag"] == "confirmed"  # -archetype suffix mapped
    assert a["curve"]["targetK"] == 2
    assert a["curve"]["points"][2]["v"] == pytest.approx(-10.0)
