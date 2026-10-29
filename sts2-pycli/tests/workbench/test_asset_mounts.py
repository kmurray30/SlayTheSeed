"""The `/assets` static mount, and its audio sibling.

Final review finding 2 (BLOCKING, 2026-08-23 final review of
feat/workbench-game-sounds): `sfx_map.json`'s events resolve to
`audio/<event>.webm` (see `sts2-pycli/web/src/sound/sfxMap.ts`'s
`eventAssetUrl`), and the art bucket of the day happened to make that work
because the bucket root IS `images_root` — the sync put the audio tree at
`$BUCKET/audio` (that deploy and its sync script are retired; the static build
lays the same shape out under `web/dist/assets`). Locally, `images_root` is
`sts2-wiki/site/public/images` and the audio tree
(`sts2-wiki/site/public/audio`) is a SIBLING of it, not a child — so before
the fix every `/assets/audio/...` request 404s in `just workbench`, the only
way to try the feature before paying for a deploy, and the failure is
indistinguishable from "sound is off" (one `console.warn` per cue, otherwise
silence). No network: `TestClient` calls the ASGI app in-process.
"""

from __future__ import annotations

from fastapi.testclient import TestClient
from workbench.server import create_app


def _app_with_art(tmp_path):
    """A `create_app` instance whose `images_root` and its sibling `audio`
    directory both hold one real file, mirroring the real
    `sts2-wiki/site/public/{images,audio}` layout.
    """
    site_public = tmp_path / "site_public"
    images = site_public / "images"
    images.mkdir(parents=True)
    (images / "ancients").mkdir()
    (images / "ancients" / "darv.png").write_bytes(b"\x89PNG\r\n\x1a\n fake png bytes")

    audio = site_public / "audio"
    (audio / "sfx").mkdir(parents=True)
    (audio / "sfx" / "block_gain.webm").write_bytes(b"fake webm bytes")

    logs = tmp_path / "logs"
    logs.mkdir()
    return create_app(images_root=images, logs_dir=logs)


def test_audio_asset_resolves_alongside_images(tmp_path):
    app = _app_with_art(tmp_path)
    client = TestClient(app)

    image_resp = client.get("/assets/ancients/darv.png")
    audio_resp = client.get("/assets/audio/sfx/block_gain.webm")

    assert image_resp.status_code == 200
    assert audio_resp.status_code == 200
    assert audio_resp.content == b"fake webm bytes"


def test_missing_audio_tree_degrades_to_404_not_a_crash(tmp_path):
    # A checkout whose art hasn't been extracted yet (no sibling `audio/`
    # directory at all) must not fail to boot — same graceful degradation as
    # a missing `images_root` already gets.
    images = tmp_path / "site_public" / "images"
    images.mkdir(parents=True)
    logs = tmp_path / "logs"
    logs.mkdir()
    app = create_app(images_root=images, logs_dir=logs)
    client = TestClient(app)

    resp = client.get("/assets/audio/sfx/block_gain.webm")

    assert resp.status_code == 404
