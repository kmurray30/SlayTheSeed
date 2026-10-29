#!/bin/bash
# tests/test_setup_stale_wheel.sh — bead sts2-rs-j7gi
#
# Verifies that setup.sh's stale-wheel uninstall step actually clears
# a previously-installed `sts2_rs` package before `maturin develop`
# runs. The failure mode being guarded against: a globally- or
# previously-installed wheel named `sts2_rs` shadows the editable
# build that `maturin develop` produces, silently giving the user
# a stale extension at runtime.
#
# Strategy:
#   1. Build a tiny "fake" pure-Python package named `sts2_rs` whose
#      `version()` returns a sentinel string ("FAKE-STALE-WHEEL").
#   2. Install it into the active venv with `pip install`.
#   3. Run `setup.sh` with STS2_SKIP_DOTNET=1 (this test is about
#      the Rust step only).
#   4. Assert that `python -c "import sts2_rs; print(sts2_rs.version())"`
#      now returns the *real* version (NOT the sentinel) and that the
#      module is loaded from the venv's site-packages with a `.so`
#      extension (proof it's the maturin build, not the pure-Python
#      stub).
#
# Prereqs (same as setup.sh's Rust block):
#   * Active sts2-pycli venv
#   * maturin on PATH
#   * sts2-rs checkout at ../sts2-rs
#
# Manual fallback (if any prereq is missing):
#   $ source .venv/bin/activate
#   $ pip install --force-reinstall --no-deps \
#         /tmp/fake-sts2_rs/    # see fake-pkg generator below
#   $ python -c "import sts2_rs; assert sts2_rs.version() == 'FAKE-STALE-WHEEL'"
#   $ STS2_SKIP_DOTNET=1 ./setup.sh
#   $ python -c "import sts2_rs; assert sts2_rs.version() != 'FAKE-STALE-WHEEL'"
#   $ python -c "import sts2_rs, pathlib; \
#         assert pathlib.Path(sts2_rs.__file__).suffix in {'.so', '.pyd', '.dylib'}"

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SENTINEL="FAKE-STALE-WHEEL"

# ── Prereq gates ──
if [ -z "${VIRTUAL_ENV:-}" ] || [[ "$VIRTUAL_ENV" != *sts2-pycli* ]]; then
    echo "SKIP: this test must run inside sts2-pycli's venv (got '$VIRTUAL_ENV')."
    echo "      Activate it with: source $REPO_DIR/.venv/bin/activate"
    exit 0
fi
if ! command -v maturin &>/dev/null; then
    echo "SKIP: maturin not on PATH; cannot exercise the real build step."
    exit 0
fi
if [ ! -f "$REPO_DIR/../sts2-rs/crates/sts2-py/Cargo.toml" ]; then
    echo "SKIP: sts2-rs checkout not found at ../sts2-rs."
    exit 0
fi

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

echo "▶ test_setup_stale_wheel: building fake sts2_rs stub at $WORKDIR/fake-sts2_rs"

# ── Build the fake package ──
mkdir -p "$WORKDIR/fake-sts2_rs/sts2_rs"
cat > "$WORKDIR/fake-sts2_rs/pyproject.toml" <<'PYPROJECT'
[build-system]
requires = ["setuptools>=61"]
build-backend = "setuptools.build_meta"

[project]
name = "sts2_rs"
version = "0.0.0.dev0"
description = "Stale-wheel sentinel for setup.sh test (bead sts2-rs-j7gi)"
PYPROJECT
cat > "$WORKDIR/fake-sts2_rs/sts2_rs/__init__.py" <<PY
def version() -> str:
    return "$SENTINEL"
PY

# ── Install the fake wheel ──
# Use `uv pip` because sts2-pycli's venv doesn't include pip natively.
if command -v uv &>/dev/null; then
    uv pip install --quiet --force-reinstall --no-deps "$WORKDIR/fake-sts2_rs"
else
    pip install --quiet --force-reinstall --no-deps "$WORKDIR/fake-sts2_rs"
fi
FAKE_VER="$(python -c "import sts2_rs; print(sts2_rs.version())")"
if [ "$FAKE_VER" != "$SENTINEL" ]; then
    echo "FAIL: fake install didn't take — sts2_rs.version() returned '$FAKE_VER'"
    exit 1
fi
echo "  ✓ Fake wheel installed: sts2_rs.version() == '$SENTINEL'"

# ── Run setup.sh's Rust block ──
echo "  ▶ Running setup.sh (STS2_SKIP_DOTNET=1)…"
STS2_SKIP_DOTNET=1 bash "$REPO_DIR/setup.sh" >/dev/null

# ── Assert the fake is gone and the real extension is loaded ──
REAL_VER="$(python -c "import sts2_rs; print(sts2_rs.version())")"
if [ "$REAL_VER" = "$SENTINEL" ]; then
    echo "FAIL: setup.sh did NOT uninstall the stale wheel — version still '$SENTINEL'"
    exit 1
fi
echo "  ✓ Stale wheel cleared: sts2_rs.version() == '$REAL_VER'"

# Maturin installs sts2_rs as a package directory whose `__init__.py`
# re-exports from a sibling `sts2_rs.abi3.so` (linux) / `.pyd` / `.dylib`.
# Confirm we see exactly that layout, AND that one of the Rust-only
# symbols (`state_from_dict`) is bound — the fake stub only had `version()`.
python - <<'PY'
import sts2_rs, pathlib, sys
root = pathlib.Path(sts2_rs.__file__).parent
shared_libs = [p for p in root.iterdir()
               if p.suffix in {".so", ".pyd", ".dylib"}]
if not shared_libs:
    sys.exit(f"FAIL: no .so/.pyd/.dylib next to {sts2_rs.__file__} — "
             "the fake may have been re-installed on top of the maturin build.")
if not hasattr(sts2_rs, "state_from_dict"):
    sys.exit("FAIL: sts2_rs.state_from_dict not bound — "
             "stale stub still in front of the real extension.")
print(f"  ✓ Loaded native extension: {shared_libs[0].name}")
print(f"  ✓ Real Rust symbols bound: state_from_dict={type(sts2_rs.state_from_dict).__name__}")
PY

echo "✅ test_setup_stale_wheel PASSED"
