#!/usr/bin/env bash
# maturin-guard.sh — Claude Code PreToolUse(Bash) guard.
#
# DENIES a raw `maturin develop`. Building the sts2_rs wheel MUST go through
# scripts/wheel-venv.sh so swarm worktree agents don't clobber the shared
# sts2-pycli/.venv (the recurring stale-wheel bug). This is the *enforcement*
# layer behind the CLAUDE.md guidance — prose is advisory, this is not.
#
# Why this is safe to deny broadly:
#   * setup.sh and wheel-venv.sh both invoke `maturin develop` INSIDE a script,
#     so the only thing this hook (which sees the top-level Bash command) ever
#     matches is an agent typing `maturin develop` directly — exactly the
#     clobber path.
#   * `maturin build` (produces a wheel without installing) is NOT matched.
#   * For the main checkout `wheel-venv.sh build` routes to .venv anyway, so
#     nothing is lost by requiring the wrapper.
#
# Contract (Claude Code hooks): reads the PreToolUse JSON on stdin, emits a
# `permissionDecision: deny` object on stdout, exits 0. No match -> no output,
# exit 0 (the tool call proceeds normally).
set -euo pipefail

input="$(cat)"
cmd="$(printf '%s' "$input" | jq -r '.tool_input.command // ""' 2>/dev/null || true)"

# Match `maturin develop` only in COMMAND POSITION: at the start of a line or
# right after a shell separator (`;`, `&`, `|`, `(`), optionally preceded by
# env-var assignments (`VIRTUAL_ENV=… maturin develop`). grep scans line by
# line, so `^` also covers newline- and `&&`/`||`-separated invocations. This
# deliberately does NOT match a mere MENTION inside a quoted string — e.g. a
# `git commit -m "… maturin develop …"` message or an `echo` — which is not an
# actual invocation and must not be blocked. `maturin build` is never matched.
if printf '%s' "$cmd" | grep -qE '(^|[;&|(])[[:space:]]*([A-Za-z_][A-Za-z0-9_]*=[^[:space:]]*[[:space:]]+)*maturin[[:space:]]+develop'; then
  reason='Blocked: raw `maturin develop` clobbers the shared sts2-pycli/.venv when run from a worktree (the recurring stale-wheel bug — every worktree is at a different commit but they share one venv). Build through the isolation wrapper instead:

  scripts/wheel-venv.sh build [SRC]          # build this checkout'\''s wheel into its OWN venv
  scripts/wheel-venv.sh run -- python python/parity_audit.py …   # build-if-stale + run the harness in the right venv

The wrapper routes the main checkout to .venv (unchanged) and each worktree to its own .venv-wt/<id>. See sts2-pycli CLAUDE.md "Building the sts2_rs wheel". (setup.sh / wheel-venv.sh call maturin internally and are not affected.)'
  jq -nc --arg r "$reason" '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'
fi
exit 0
