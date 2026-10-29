#!/usr/bin/env python3
"""Build the workbench art manifest for deployments that do not carry the art.

The workbench's :class:`workbench.assets.AssetResolver` normally decides which
art file to reference by probing the local filesystem (``Path.is_file``). Where
the 142 MB of art does not sit beside the code, the resolver runs in *manifest
mode* instead: it checks each candidate path against a set of known-present
files.

This script walks the extracted art tree and emits that set as a JSON list
(``asset_manifest.json``), plus copies the small ``creature_scales.json`` the
resolver also reads.

Two live consumers: the static build's ``just _art-into-dist``, which refuses to
finish unless every file the manifest names reached ``web/dist/assets``, and the
root ``Dockerfile``, which COPYs both files into the image. (The third consumer
was the retired Cloud Run deploy, whose bucket sync went with it — see
``deploy/DEPLOY.md``.)

Usage:
    python scripts/build_asset_manifest.py [IMAGES_ROOT] [-o OUT_DIR]

``IMAGES_ROOT`` defaults to ``../sts2-wiki/site/public/images`` (the sibling
wiki's extracted art). ``OUT_DIR`` defaults to ``deploy/`` next to this repo.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
from collections.abc import Iterable
from pathlib import Path

# A top-level ``v<major>.<minor>.<patch>/`` folder of the art tree is a game
# build's OVERLAY (GH #801): art that build has and the default build lacks,
# or draws differently, under the same relative layout. The default build's
# resolver never looks inside one; a build's own resolver tries
# ``<version>/<rel>`` before ``<rel>``.
_OVERLAY_DIR = re.compile(r"^v\d+\.\d+\.\d+$")


def overlay_versions(images_root: Path) -> list[str]:
    """The game builds that have an art overlay under *images_root*, sorted."""
    if not images_root.is_dir():
        return []
    return sorted(
        p.name for p in images_root.iterdir() if p.is_dir() and _OVERLAY_DIR.match(p.name)
    )


_REPO_ROOT = Path(__file__).resolve().parents[1]
_DEFAULT_IMAGES = _REPO_ROOT.parent / "sts2-wiki" / "site" / "public" / "images"
_DEFAULT_AUDIO = _REPO_ROOT.parent / "sts2-wiki" / "site" / "public" / "audio"
_DEFAULT_OUT = _REPO_ROOT / "deploy"


def build_manifest(
    images_root: Path, audio_root: Path | None = None, *, overlays: Iterable[str] = ()
) -> list[str]:
    """Return the sorted list of art (+ audio) file paths.

    Version overlay folders (``v0.111.0/…``) are left out unless named in
    *overlays*, so the default build's manifest does not move when a later
    build's art is added. A named overlay's files are listed under their
    ``<version>/`` prefix, the path the resolver requests them by.

    Image paths are relative to *images_root*, exactly as the frontend/
    resolver request them (``card_atlas/...``). ``creature_scales.json`` is
    excluded — it is shipped separately and read from the resolver root, not
    looked up as art.

    Audio paths are relative to *audio_root* but prefixed with ``audio/`` —
    ``eventAssetUrl`` (``web/src/sound/sfxMap.ts``) builds its request as
    ``audio/<event-path>.webm`` against the same asset base the images use,
    so the manifest entry has to carry that prefix to match. *audio_root* is
    optional (and skipped when the directory doesn't exist) so a checkout
    without the audio tree still produces an images-only manifest.
    """
    wanted = set(overlays)
    rels: list[str] = []
    for path in images_root.rglob("*"):
        if not path.is_file():
            continue
        rel = path.relative_to(images_root).as_posix()
        if rel == "creature_scales.json":
            continue
        top = rel.split("/", 1)[0]
        if _OVERLAY_DIR.match(top) and top not in wanted:
            continue
        rels.append(rel)
    if audio_root is not None and audio_root.is_dir():
        for path in audio_root.rglob("*"):
            if not path.is_file():
                continue
            rel = path.relative_to(audio_root).as_posix()
            rels.append(f"audio/{rel}")
    rels.sort()
    return rels


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "images_root",
        nargs="?",
        default=str(_DEFAULT_IMAGES),
        help="Extracted art root (default: ../sts2-wiki/site/public/images).",
    )
    parser.add_argument(
        "audio_root",
        nargs="?",
        default=str(_DEFAULT_AUDIO),
        help="Extracted audio root (default: ../sts2-wiki/site/public/audio).",
    )
    parser.add_argument(
        "-o",
        "--out-dir",
        default=str(_DEFAULT_OUT),
        help="Output directory for asset_manifest.json (default: deploy/).",
    )
    args = parser.parse_args(argv)

    images_root = Path(args.images_root)
    if not images_root.is_dir():
        print(f"error: images root not found: {images_root}", file=sys.stderr)
        return 1

    audio_root = Path(args.audio_root)
    if not audio_root.is_dir():
        print(
            f"warning: audio root not found ({audio_root}) — manifest will carry no audio/ entries.",
            file=sys.stderr,
        )

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    # The deploy manifest serves every build's resolver, so it lists every
    # overlay; each resolver only asks for its own build's prefix.
    rels = build_manifest(images_root, audio_root, overlays=overlay_versions(images_root))
    audio_entries = [r for r in rels if r.startswith("audio/")]
    on_disk = (
        sorted(p.relative_to(audio_root).as_posix() for p in audio_root.rglob("*") if p.is_file())
        if audio_root.is_dir()
        else []
    )
    if len(audio_entries) != len(on_disk):
        print(
            f"error: manifest has {len(audio_entries)} audio/ entries but {len(on_disk)} files exist "
            f"under {audio_root} — refusing to write a manifest that would 404 audio at runtime.",
            file=sys.stderr,
        )
        return 1

    manifest_path = out_dir / "asset_manifest.json"
    manifest_path.write_text(json.dumps(rels))
    print(
        f"wrote {manifest_path} ({len(rels)} files: {len(rels) - len(audio_entries)} images, "
        f"{len(audio_entries)} audio)"
    )

    scales_src = images_root / "creature_scales.json"
    if scales_src.is_file():
        shutil.copyfile(scales_src, out_dir / "creature_scales.json")
        print(f"copied creature_scales.json -> {out_dir / 'creature_scales.json'}")
    else:
        print("warning: creature_scales.json not found (combat scaling falls back)")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
