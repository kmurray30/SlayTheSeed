#!/bin/bash
# tests/test_venv_hashkey.sh — issue #15 / bead sts2-rs-cg5ci, Phase 2.
#
# Content-addressed worktree venvs (`.venv-h/<engine-source-hash>`). Verifies:
#   1. the sts2-srchash CLI is deterministic and emits a 16-hex hash — the key
#      wheel-venv.sh names each worktree venv with;
#   2. `wheel-venv.sh gc-prune` removes ONLY unreachable `.venv-h/<H>` dirs and
#      NEVER one still reachable from a live checkout. Deleting a live venv is
#      the worst-case regression here (the old `.venv-wt/<basename>` GC could
#      delete a venv two worktrees shared), so this is the property that matters;
#   3. the MAIN checkout still maps to the heavy shared `.venv` (unchanged — it
#      carries simlab/workbench deps and is what setup.sh / interactive use).
#
# The git-worktree-list → hash resolution half of GC (reachable_prune) is
# layout-sensitive (monorepo worktree roots vs the sts2-rs subdir) and is
# verified live; this test pins the deletion-safety core (prune_unreachable via
# the `gc-prune` subcommand) which takes the reachable set explicitly.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
WV="$REPO_DIR/scripts/wheel-venv.sh"
RS="$(cd "$REPO_DIR/../sts2-rs" 2>/dev/null && pwd -P || true)"

if [ -z "$RS" ] || [ ! -f "$RS/crates/sts2-py/Cargo.toml" ]; then
    echo "SKIP: sts2-rs checkout not found at ../sts2-rs."
    exit 0
fi
if ! command -v cargo >/dev/null 2>&1; then
    echo "SKIP: cargo not on PATH; cannot exercise the source hasher."
    exit 0
fi

fail() { echo "FAIL: $*"; exit 1; }

# ── 1. CLI determinism + format ──
echo "▶ source hasher is deterministic 16-hex"
H1="$(cd "$RS" && cargo run --quiet --release -p sts2-srchash -- "$RS")"
H2="$(cd "$RS" && cargo run --quiet --release -p sts2-srchash -- "$RS")"
[ "$H1" = "$H2" ] || fail "CLI not deterministic: '$H1' vs '$H2'"
[[ "$H1" =~ ^[0-9a-f]{16}$ ]] || fail "CLI output not 16-hex: '$H1'"
echo "  ✓ engine hash = $H1"

# ── 2. gc-prune keeps reachable, removes orphans ──
echo "▶ gc-prune preserves reachable venvs, removes orphans"
HROOT="$(mktemp -d)"
trap 'rm -rf "$HROOT"' EXIT
mkdir -p "$HROOT/keepA/bin" "$HROOT/orphanB/bin" "$HROOT/sharedC/bin"
# reachable set = {keepA, sharedC}; orphanB is NOT reachable → must be removed.
# sharedC stands in for a venv two live worktrees share — it must survive.
bash "$WV" gc-prune "$HROOT" keepA sharedC
[ -d "$HROOT/keepA" ] || fail "gc-prune deleted a reachable venv (keepA)"
[ -d "$HROOT/sharedC" ] || fail "gc-prune deleted a reachable venv (sharedC)"
[ ! -d "$HROOT/orphanB" ] || fail "gc-prune kept an orphan venv (orphanB)"
echo "  ✓ kept keepA + sharedC, removed orphanB"

# An empty reachable set means every venv is an orphan → all pruned.
bash "$WV" gc-prune "$HROOT"
{ [ ! -d "$HROOT/keepA" ] && [ ! -d "$HROOT/sharedC" ]; } || fail "gc-prune with empty set left venvs behind"
echo "  ✓ empty reachable set prunes all"

# A non-existent root is a no-op, not an error (GC is best-effort).
bash "$WV" gc-prune "$HROOT/does-not-exist" || fail "gc-prune on a missing root should be a no-op"
echo "  ✓ missing root is a harmless no-op"

# ── 3. main checkout still maps to the heavy shared .venv ──
echo "▶ main checkout maps to .venv (unchanged)"
MAIN_VENV="$(bash "$WV" path "$RS")"
[ "$MAIN_VENV" = "$REPO_DIR/.venv" ] || fail "main path is '$MAIN_VENV', expected '$REPO_DIR/.venv'"
echo "  ✓ main -> $MAIN_VENV"

echo "✅ test_venv_hashkey PASSED"
