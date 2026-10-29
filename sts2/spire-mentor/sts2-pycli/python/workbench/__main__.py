"""Run the workbench backend: ``python -m workbench``.

Art root defaults to the sibling sts2-wiki extracted images; override with
STS2_WIKI_IMAGES. Logs dir defaults to the repo's logs/.
"""

from __future__ import annotations

import os
from pathlib import Path

import uvicorn

_ROOT = Path(__file__).resolve().parents[2]
_DEFAULT_IMAGES = _ROOT.parent / "sts2-wiki" / "site" / "public" / "images"


def main() -> None:
    """Start the workbench FastAPI server via uvicorn.

    Reads configuration from environment variables:
        STS2_WIKI_IMAGES: path to the extracted game art images root.
            Defaults to ``../sts2-wiki/site/public/images`` relative to the
            repo's parent directory.
        STS2_LOGS_DIR: path to the directory containing recorded run JSONL
            logs. Defaults to ``logs/`` in the repo root.
        STS2_HOST / STS2_PORT: bind address. Defaults to ``127.0.0.1:8077``
            (this machine only — the workbench serves the game's art, which
            is for the user's own use; set ``STS2_HOST=0.0.0.0`` to reach it
            from other machines on the LAN; 8077 avoids the commonly-occupied
            8000). The Vite dev proxy in ``web/vite.config.ts`` targets the
            same port.
        STS2_RELOAD: when truthy (default), auto-reload on Python changes
            under ``python/workbench`` so backend edits don't require a
            manual restart. Set ``STS2_RELOAD=0`` to disable.
    """
    host = os.environ.get("STS2_HOST", "127.0.0.1")
    port = int(os.environ.get("STS2_PORT", "8077"))
    reload = os.environ.get("STS2_RELOAD", "1").lower() not in ("0", "false", "")

    if reload:
        # Reload re-imports the app in a worker subprocess, so it must be
        # referenced by import string + factory (not a built object). The
        # factory reads the same STS2_WIKI_IMAGES / STS2_LOGS_DIR env vars.
        uvicorn.run(
            "workbench.server:create_app_from_env",
            factory=True,
            host=host,
            port=port,
            reload=True,
            reload_dirs=[str(_ROOT / "python" / "workbench")],
        )
    else:
        # Build from env (honors STS2_SERVE_SPA / STS2_ASSETS_MANIFEST /
        # STS2_ASSETS_BASE_URL — the Cloud Run / lean-deploy knobs).
        from workbench.server import create_app_from_env

        uvicorn.run(create_app_from_env(), host=host, port=port)


if __name__ == "__main__":
    main()
