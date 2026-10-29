#!/usr/bin/env bash
# worktree-venv-gc.sh — Claude Code WorktreeRemove hook.
#
# When a git worktree is torn down (a swarm `isolation: "worktree"` subagent
# finishing, or `claude --worktree` cleanup), reap the content-addressed wheel
# venv(s) that scripts/wheel-venv.sh created for it. Without this the
# .venv-h/<hash> dirs accumulate forever as worktrees come and go.
#
# Delegates to `wheel-venv.sh gc`, which prunes every `.venv-h/<hash>` that no
# CURRENTLY-live checkout still hashes to. Reachability-based on purpose: a venv
# shared by two live worktrees (identical engine source) must survive the
# teardown of just one of them — the venv name is the source hash, not the
# worktree path, so removal cannot be keyed off the departing `worktree_path`.
#
# WorktreeRemove cannot block and its exit code is ignored, so this is
# best-effort cleanup only.
set -uo pipefail

PYCLI_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"

# Best-effort; never let a GC hiccup surface as a hook failure.
"$PYCLI_DIR/scripts/wheel-venv.sh" gc >/dev/null 2>&1 || true
exit 0
