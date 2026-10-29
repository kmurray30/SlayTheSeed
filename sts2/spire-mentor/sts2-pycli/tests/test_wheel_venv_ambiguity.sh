#!/bin/bash
# tests/test_wheel_venv_ambiguity.sh — issue #15, Trap 1 (fail-closed `run`).
#
# `wheel-venv.sh run` must REFUSE to silently auto-run the shared main
# `.venv` when all of:
#   (a) no SRC was given (neither arg nor $STS2_RS_SRC),
#   (b) cwd is NOT inside the main sts2-rs checkout (so the resolution is a
#       fallback to canonical main, not "I'm standing in main"), and
#   (c) isolated worktree venvs (.venv-h/*) exist.
# That trio is the silent footgun: you `build` a worktree's engine but `run`
# falls back to canonical main and tests the WRONG engine while reporting
# success. The guard must still proceed when the caller is explicit (passes
# SRC) or confirms main with WHEEL_VENV_ALLOW_MAIN=1.
#
# Hermetic-ish: runs against the real pycli checkout but fabricates a
# throwaway .venv-h/* marker so it does not depend on live swarm state, and
# uses WHEEL_VENV_NO_BUILD=1 so nothing is compiled.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
WV="$REPO_DIR/scripts/wheel-venv.sh"

[ -x "$WV" ] || { echo "SKIP: wheel-venv.sh not found at $WV"; exit 0; }
[ -x "$REPO_DIR/.venv/bin/python" ] || {
  echo "SKIP: main .venv not present (needed for the allow-main exec case)."; exit 0; }

# Guarantee condition (c) deterministically with a throwaway marker; clean up
# WITHOUT touching any real .venv-h/<hash> (rmdir only removes empties).
MARKER="$REPO_DIR/.venv-h/__ambiguity_test__"
mkdir -p "$MARKER"
cleanup() { rmdir "$MARKER" 2>/dev/null || true; rmdir "$REPO_DIR/.venv-h" 2>/dev/null || true; }
trap cleanup EXIT

cd "$REPO_DIR"   # cwd = canonical pycli → exactly the footgun position
fail=0

# 1) No SRC + worktree venvs present → must FAIL CLOSED with an 'ambiguous' msg.
out="$(WHEEL_VENV_NO_BUILD=1 "$WV" run -- true 2>&1)"; rc=$?
if [ "$rc" -eq 0 ]; then
  echo "FAIL: no-SRC run from pycli with .venv-h present should refuse, exited 0"; fail=1
elif ! printf '%s' "$out" | grep -qi "ambiguous"; then
  echo "FAIL: refused (rc=$rc) but message lacked 'ambiguous':"; printf '   %s\n' "$out"; fail=1
else
  echo "ok: no-SRC run fails closed (rc=$rc, mentions 'ambiguous')"
fi

# 2) WHEEL_VENV_ALLOW_MAIN=1 confirms main → must proceed (exec `true` → rc 0).
out="$(WHEEL_VENV_NO_BUILD=1 WHEEL_VENV_ALLOW_MAIN=1 "$WV" run -- true 2>&1)"; rc=$?
if [ "$rc" -ne 0 ]; then
  echo "FAIL: ALLOW_MAIN=1 should bypass the ambiguity guard, rc=$rc:"; printf '   %s\n' "$out"; fail=1
else
  echo "ok: ALLOW_MAIN=1 bypasses the guard"
fi

if [ "$fail" -eq 0 ]; then echo "▶ test_wheel_venv_ambiguity: PASS"; else
  echo "▶ test_wheel_venv_ambiguity: FAIL"; exit 1; fi
