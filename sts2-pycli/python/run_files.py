"""Read scraped ``.run`` files, transparently handling gzip (``.run.gz``).

The spire-codex corpus (``files-spire-codex``) is stored gzipped as
``<hash>.run.gz`` to save disk; the vendored base-game parity corpus
(``sts2-rs/data/run-corpus/files``) is plain ``.run``. Both flow through
here so callers never have to care which encoding a corpus uses.
"""

from __future__ import annotations

import gzip
import json
import os
import sys
from pathlib import Path
from typing import Any


def read_run_bytes(path: str | Path) -> bytes:
    """Return the raw JSON bytes of a run file, decompressing gzip if present."""
    raw = Path(path).read_bytes()
    return gzip.decompress(raw) if raw[:2] == b"\x1f\x8b" else raw


def load_run(path: str | Path) -> Any:
    """Parse a ``.run``/``.run.gz`` file into its JSON object."""
    return json.loads(read_run_bytes(path))


def glob_runs(root: Path, *, recursive: bool = True) -> list[Path]:
    """Sorted run files under ``root`` — both plain ``.run`` and ``.run.gz``."""
    g = root.rglob if recursive else root.glob
    return sorted([*g("*.run"), *g("*.run.gz")])


# ─── External scraper corpus discovery ──────────────────────────────────────

# Opt-in spire-codex corpus that is not vendored in this repo; it stays in the
# external sts2_run_scraper checkout, located via STS2_SCRAPER_ROOT (default:
# a sibling of the monorepo, honored only while it exists on disk).
LEGACY_SCRAPER_ROOT = Path(__file__).resolve().parents[2] / "sts2_run_scraper"


def codex_root() -> Path:
    """Spire-codex corpus dir inside the external sts2_run_scraper checkout."""
    env = os.environ.get("STS2_SCRAPER_ROOT")
    root = Path(env) if env else LEGACY_SCRAPER_ROOT
    codex = root / "data" / "files-spire-codex"
    if codex.is_dir():
        return codex
    sys.exit(
        f"--use-codex: spire-codex corpus not found at {codex}; "
        "set STS2_SCRAPER_ROOT to an sts2_run_scraper checkout"
    )
