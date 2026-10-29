"""A game build's art overlay (GH #801): ``images/<version>/…`` is the art that
build added or redrew. Its resolver tries the overlay first; the default
build's resolver and manifest never see it."""

from __future__ import annotations

import sys
from pathlib import Path

from workbench.assets import AssetResolver

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))
from build_asset_manifest import build_manifest, overlay_versions


def _tree(root: Path) -> Path:
    for rel in (
        "card_atlas/defect/beta/voltaic.png",
        "v0.111.0/card_atlas/defect/voltaic.png",
        "v0.111.0/relic_atlas/dowsing_rod.png",
    ):
        (root / rel).parent.mkdir(parents=True, exist_ok=True)
        (root / rel).write_bytes(b"png")
    return root


def test_the_overlay_wins_only_for_its_build(tmp_path: Path) -> None:
    root = _tree(tmp_path)
    base = AssetResolver(root)
    beta = AssetResolver(root, overlay="v0.111.0")
    assert base.card("Defect", "CARD.VOLTAIC", "Voltaic") == "card_atlas/defect/beta/voltaic.png"
    assert (
        beta.card("Defect", "CARD.VOLTAIC", "Voltaic") == "v0.111.0/card_atlas/defect/voltaic.png"
    )
    assert base.relic("Dowsing Rod") is None
    assert beta.relic("Dowsing Rod") == "v0.111.0/relic_atlas/dowsing_rod.png"


def test_manifest_mode_matches_filesystem_mode(tmp_path: Path) -> None:
    root = _tree(tmp_path)
    manifest = set(build_manifest(root, overlays=["v0.111.0"]))
    beta = AssetResolver(root, manifest=manifest, overlay="v0.111.0")
    assert (
        beta.card("Defect", "CARD.VOLTAIC", "Voltaic") == "v0.111.0/card_atlas/defect/voltaic.png"
    )


def test_the_default_manifest_lists_no_overlay(tmp_path: Path) -> None:
    root = _tree(tmp_path)
    assert overlay_versions(root) == ["v0.111.0"]
    assert build_manifest(root) == ["card_atlas/defect/beta/voltaic.png"]
    assert "v0.111.0/relic_atlas/dowsing_rod.png" in build_manifest(root, overlays=["v0.111.0"])
