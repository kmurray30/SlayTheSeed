"""Tests for synergy.embed_geometry — PPO card-embedding View 3 + control matcher.

Geometry primitives are tested on synthetic vectors (no ONNX).  Champion-table
tests are skipped when the committed champion ONNX is not on disk.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
import pytest

import synergy.embed_geometry as eg
from synergy.embed_geometry import (
    build_geometry,
    champion_onnx_path,
    control_match,
    cosine_knn_graph,
    frequency_gate,
    l2_normalize,
    load_card_embeddings,
    louvain_clusters,
)

# Resolved, never hardcoded: a literal filename here would pin a retired net
# the moment Ironclad is promoted, and this suite would go on skipping (or
# testing the wrong net) without failing. See
# `tests/test_champion_resolvers_agree.py::test_no_fifth_hardcoded_champion_table`.
_IRONCLAD = champion_onnx_path("ironclad")
_have_champ = _IRONCLAD.exists()


# ---------------------------------------------------------------------------
# Champion table IO
# ---------------------------------------------------------------------------


class TestChampionPath:
    def test_unknown_character_raises(self) -> None:
        with pytest.raises(ValueError):
            champion_onnx_path("not_a_character")

    def test_env_override_wins(self, monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
        f = tmp_path / "x.onnx"
        f.write_bytes(b"")
        monkeypatch.setenv("STS2_RUNAI_PPO", str(f))
        assert champion_onnx_path("ironclad") == f


@pytest.mark.skipif(not _have_champ, reason="champion ONNX not on disk")
class TestLoadEmbeddings:
    def test_maps_names_to_192d_rows(self) -> None:
        emb = load_card_embeddings(_IRONCLAD)
        assert len(emb) >= 500  # ~580 card names
        v = next(iter(emb.values()))
        assert v.shape == (192,)

    def test_known_card_present(self) -> None:
        emb = load_card_embeddings(_IRONCLAD)
        assert "BASH" in emb  # Ironclad starter attack, always in vocab


# ---------------------------------------------------------------------------
# Geometry primitives
# ---------------------------------------------------------------------------


def _two_clusters() -> dict[str, np.ndarray]:
    # Two tight groups in 2-D: {A1,A2,A3} near +x, {B1,B2,B3} near +y.
    return {
        "A1": np.array([1.0, 0.0]),
        "A2": np.array([0.99, 0.1]),
        "A3": np.array([0.98, 0.15]),
        "B1": np.array([0.0, 1.0]),
        "B2": np.array([0.1, 0.99]),
        "B3": np.array([0.15, 0.98]),
    }


class TestPrimitives:
    def test_l2_normalize_unit_norm(self) -> None:
        out = l2_normalize({"A": np.array([3.0, 4.0])})
        assert np.linalg.norm(out["A"]) == pytest.approx(1.0)

    def test_l2_normalize_zero_stays_zero(self) -> None:
        out = l2_normalize({"Z": np.array([0.0, 0.0])})
        assert np.allclose(out["Z"], 0.0)

    def test_frequency_gate_drops_rare_and_absent(self) -> None:
        emb = {"A": np.array([1.0]), "B": np.array([1.0]), "C": np.array([1.0])}
        freq = {"A": 0.5, "B": 0.001}  # C absent entirely
        out = frequency_gate(emb, freq, min_freq=0.01)
        assert set(out) == {"A"}

    def test_cosine_knn_finds_same_cluster_neighbors(self) -> None:
        emb = l2_normalize(_two_clusters())
        knn = cosine_knn_graph(emb, k=2)
        neigh = {n for n, _ in knn["A1"]}
        assert neigh <= {"A2", "A3"}

    def test_louvain_recovers_two_clusters(self) -> None:
        emb = l2_normalize(_two_clusters())
        knn = cosine_knn_graph(emb, k=2)
        clusters = louvain_clusters(knn, seed=0)
        assert clusters["A1"] == clusters["A2"] == clusters["A3"]
        assert clusters["B1"] == clusters["B2"] == clusters["B3"]
        assert clusters["A1"] != clusters["B1"]


# ---------------------------------------------------------------------------
# Control matcher (§7)
# ---------------------------------------------------------------------------


class TestControlMatch:
    def _emb(self) -> dict[str, np.ndarray]:
        return l2_normalize(
            {
                "A1": np.array([1.0, 0.0]),
                "A2": np.array([0.98, 0.1]),
                "NEAR": np.array([0.9, 0.2]),  # close to A cluster but not a member
                "FAR": np.array([0.0, 1.0]),  # opposite side
            }
        )

    def test_picks_near_outside_cluster(self) -> None:
        emb = self._emb()
        clusters = {"A1": 0, "A2": 0, "NEAR": 1, "FAR": 2}
        out = control_match(["A1", "A2"], emb, clusters, n=1)
        assert out == ["NEAR"]

    def test_excludes_archetype_members(self) -> None:
        emb = self._emb()
        clusters = {"A1": 0, "A2": 0, "NEAR": 1, "FAR": 2}
        out = control_match(["A1", "A2"], emb, clusters, n=5)
        assert "A1" not in out
        assert "A2" not in out

    def test_type_filter(self) -> None:
        emb = self._emb()
        clusters = {"A1": 0, "A2": 0, "NEAR": 1, "FAR": 2}
        types = {"A1": "attack", "A2": "attack", "NEAR": "skill", "FAR": "attack"}
        out = control_match(["A1", "A2"], emb, clusters, n=1, card_type=types)
        assert out == ["FAR"]


# ---------------------------------------------------------------------------
# build_geometry (monkeypatched loader — no ONNX needed)
# ---------------------------------------------------------------------------


class TestBuildGeometry:
    def _fake_emb(self) -> dict[str, np.ndarray]:
        # Two clusters + a rare card that the frequency gate should drop.
        return {
            "A1": np.array([1.0, 0.0]),
            "A2": np.array([0.98, 0.1]),
            "A3": np.array([0.97, 0.12]),
            "B1": np.array([0.0, 1.0]),
            "B2": np.array([0.1, 0.98]),
            "B3": np.array([0.12, 0.97]),
            "RARE": np.array([0.5, 0.5]),
        }

    def _freq(self) -> dict[str, float]:
        return {c: 0.4 for c in ["A1", "A2", "A3", "B1", "B2", "B3"]} | {"RARE": 0.0001}

    def test_document_shape_and_freq_gate(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setattr(eg, "load_card_embeddings", lambda _p: self._fake_emb())
        monkeypatch.setattr(eg, "champion_onnx_path", lambda _c: Path("/x.onnx"))
        doc = build_geometry("ironclad", self._freq(), k=2, min_freq=0.01)
        assert set(doc) == {"character", "n_cards", "k", "clusters", "knn", "fm_agreement"}
        assert doc["character"] == "ironclad"
        # RARE dropped by the frequency gate.
        assert "RARE" not in doc["clusters"]
        assert doc["n_cards"] == 6
        # kNN is JSON-shaped: list of [neighbor, sim].
        assert all(isinstance(nbr, list) and len(nbr) == 2 for nbr in doc["knn"]["A1"])

    def test_fm_agreement_populated(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setattr(eg, "load_card_embeddings", lambda _p: self._fake_emb())
        monkeypatch.setattr(eg, "champion_onnx_path", lambda _c: Path("/x.onnx"))
        fm = self._fake_emb()  # identical FM → perfect neighbour agreement
        doc = build_geometry("ironclad", self._freq(), k=2, min_freq=0.01, fm_embeddings=fm)
        assert doc["fm_agreement"]["A1"] == pytest.approx(1.0)

    def test_fm_agreement_none_without_fm(self, monkeypatch: pytest.MonkeyPatch) -> None:
        monkeypatch.setattr(eg, "load_card_embeddings", lambda _p: self._fake_emb())
        monkeypatch.setattr(eg, "champion_onnx_path", lambda _c: Path("/x.onnx"))
        doc = build_geometry("ironclad", self._freq(), k=2, min_freq=0.01)
        assert all(v is None for v in doc["fm_agreement"].values())


@pytest.mark.skipif(not _have_champ, reason="champion ONNX not on disk")
class TestBuildGeometryReal:
    def test_real_ironclad_champion(self) -> None:
        # Frequencies: give every card a passing frequency so the gate is a no-op.
        emb = load_card_embeddings(_IRONCLAD)
        freq = dict.fromkeys(emb, 0.5)
        doc = build_geometry("ironclad", freq, k=10, min_freq=0.01)
        assert doc["n_cards"] > 100
        assert len(set(doc["clusters"].values())) >= 2  # multiple communities
