"""Tests for synergy.embed — UMAP embedding on synthetic block-structured PMI."""

import json
import tempfile
from pathlib import Path

import numpy as np
import pytest

from synergy.embed import build_pmi_matrix, embed_catalog


def _make_block_pairs() -> list[dict]:
    """Two tight blocks {A,B,C} and {D,E,F} with cross-block PMI ≈ 0."""
    high = 3.0
    low = 0.01
    pairs = []
    block1 = ["A", "B", "C"]
    block2 = ["D", "E", "F"]
    for i, a in enumerate(block1):
        for b in block1[i + 1 :]:
            pairs.append({"pair": [a, b], "pmi": high})
    for i, a in enumerate(block2):
        for b in block2[i + 1 :]:
            pairs.append({"pair": [a, b], "pmi": high})
    for a in block1:
        for b in block2:
            pairs.append({"pair": [a, b], "pmi": low})
    return pairs


def test_build_pmi_matrix_symmetric():
    pairs = [{"pair": ["X", "Y"], "pmi": 2.5}]
    tok2idx, mat = build_pmi_matrix(pairs)
    xi, yi = tok2idx["X"], tok2idx["Y"]
    assert mat[xi, yi] == pytest.approx(2.5)
    assert mat[yi, xi] == pytest.approx(2.5)
    assert mat[xi, xi] == pytest.approx(0.0)


def test_build_pmi_matrix_diagonal_zero():
    pairs = [{"pair": ["A", "B"], "pmi": 1.0}, {"pair": ["B", "C"], "pmi": 0.5}]
    tok2idx, mat = build_pmi_matrix(pairs)
    n = len(tok2idx)
    assert mat.diagonal().sum() == pytest.approx(0.0)
    assert mat.shape == (n, n)


def test_within_block_closer_than_cross_block():
    """Cards in the same tight PMI block must land closer than cross-block pairs."""
    pairs = _make_block_pairs()
    block1 = ["A", "B", "C"]
    block2 = ["D", "E", "F"]
    all_cards = block1 + block2

    # Build minimal obs_catalog and catalog json
    obs_catalog = {"pairs": pairs}
    catalog = {
        "generated_at": "2026-01-01T00:00:00Z",
        "character": "Test",
        "run_count": 100,
        "nodes": [{"id": c, "label": c, "frequency": 0.5} for c in all_cards],
        "edges": [],
    }

    with tempfile.TemporaryDirectory() as tmp:
        obs_path = Path(tmp) / "obs.json"
        cat_path = Path(tmp) / "catalog.json"
        out_path = Path(tmp) / "out.json"
        obs_path.write_text(json.dumps(obs_catalog))
        cat_path.write_text(json.dumps(catalog))

        embed_catalog(
            obs_path=obs_path,
            catalog_path=cat_path,
            out_path=out_path,
            n_neighbors=2,  # tiny input
            min_dist=0.1,
            random_state=42,
        )

        result = json.loads(out_path.read_text())

    # Extract coords
    coords = {n["id"]: (n["x"], n["y"]) for n in result["nodes"] if "x" in n}
    assert len(coords) == 6, f"Expected 6 embedded nodes, got {len(coords)}: {list(coords.keys())}"

    def dist(a, b):
        xa, ya = coords[a]
        xb, yb = coords[b]
        return ((xa - xb) ** 2 + (ya - yb) ** 2) ** 0.5

    within_dists = [dist(a, b) for a in block1 for b in block1 if a < b]
    cross_dists = [dist(a, b) for a in block1 for b in block2]

    avg_within = sum(within_dists) / len(within_dists)
    avg_cross = sum(cross_dists) / len(cross_dists)
    assert avg_within < avg_cross, (
        f"Expected within-block avg dist {avg_within:.4f} < cross-block avg dist {avg_cross:.4f}"
    )


def test_coords_normalized_to_unit():
    """All x,y coords must be in [0,1]."""
    pairs = _make_block_pairs()
    obs_catalog = {"pairs": pairs}
    all_cards = ["A", "B", "C", "D", "E", "F"]
    catalog = {
        "generated_at": "2026-01-01T00:00:00Z",
        "character": "Test",
        "run_count": 100,
        "nodes": [{"id": c, "label": c, "frequency": 0.5} for c in all_cards],
        "edges": [],
    }

    with tempfile.TemporaryDirectory() as tmp:
        obs_path = Path(tmp) / "obs.json"
        cat_path = Path(tmp) / "catalog.json"
        out_path = Path(tmp) / "out.json"
        obs_path.write_text(json.dumps(obs_catalog))
        cat_path.write_text(json.dumps(catalog))

        embed_catalog(
            obs_path=obs_path,
            catalog_path=cat_path,
            out_path=out_path,
            n_neighbors=2,
            random_state=42,
        )
        result = json.loads(out_path.read_text())

    for node in result["nodes"]:
        if "x" in node:
            assert 0.0 <= node["x"] <= 1.0, f"x={node['x']} out of [0,1]"
            assert 0.0 <= node["y"] <= 1.0, f"y={node['y']} out of [0,1]"
            assert 0.0 <= node["hue"] < 360.0, f"hue={node['hue']} out of [0,360)"
