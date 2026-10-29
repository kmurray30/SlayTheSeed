"""Drift check for parity fixtures vendored from the sts2-rs sibling.

`python/test_run_value_gate.py` and `python/test_surrogate_gate.py` read the
Rust<->torch parity fixtures from `python/fixtures/`, vendored copies of the
originals in `../sts2-rs/crates/sts2-runai/tests/fixtures/` so the tests run
in a standalone sts2-pycli checkout. The Rust side (`run_value_net_parity.rs`,
`surrogate_parity.rs`) keeps verifying the ORIGINALS, so a re-emit there
(`--emit-parity-fixture`) that isn't re-vendored here would silently anchor
the numpy forwards to a stale net.

This test compares content hashes of each vendored copy against its original
when the sibling repo is present (the monorepo layout) and skips cleanly when
it is absent (a standalone checkout). On mismatch, re-vendor:

    cp ../sts2-rs/crates/sts2-runai/tests/fixtures/<name> python/fixtures/
"""

from __future__ import annotations

import hashlib
from pathlib import Path

import pytest

_PYCLI_ROOT = Path(__file__).resolve().parents[1]
_VENDORED_DIR = _PYCLI_ROOT / "python" / "fixtures"
_ORIGINAL_DIR = _PYCLI_ROOT.parent / "sts2-rs" / "crates" / "sts2-runai" / "tests" / "fixtures"

# Every fixture vendored from sts2-runai; extend when vendoring more.
_VENDORED_FIXTURES = [
    "run_value_net_parity.json",
    "surrogate_parity.json",
]


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


@pytest.mark.parametrize("name", _VENDORED_FIXTURES)
def test_vendored_fixture_matches_sibling(name: str) -> None:
    if not _ORIGINAL_DIR.is_dir():
        pytest.skip(f"sts2-rs sibling not present ({_ORIGINAL_DIR})")
    vendored = _VENDORED_DIR / name
    original = _ORIGINAL_DIR / name
    assert vendored.is_file(), f"vendored fixture missing: {vendored}"
    assert original.is_file(), f"original fixture missing: {original}"
    assert _sha256(vendored) == _sha256(original), (
        f"{name} has drifted from the sts2-rs original. Re-vendor it:\n  cp {original} {vendored}"
    )
