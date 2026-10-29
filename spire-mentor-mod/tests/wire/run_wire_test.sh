#!/usr/bin/env bash
# Differential black-box test of SpireMentor's BridgeServer.
#
# Usage: run_wire_test.sh <SpireMentor.dll> <out-dir>
#
# Hosts the given SpireMentor.dll's BridgeServer in-process (GodotSharp resolved
# to the headless STUB build, real sts2.dll, real Harmony), drives it with a
# scripted TCP client, and writes <out-dir>/transcript.jsonl (+ server.log,
# meta.json, harness.stderr). If a baseline transcript exists it is then
# diffed against it (diff_transcripts.py) and the exit status reflects that:
#   0 = ran, identical to baseline (or no baseline)   1 = differs   2+ = harness failure
#
# Env overrides:
#   REPO          monorepo root (default: three levels up from this script) -- only READ:
#                 GodotStubs sources + sts2-pycli/lib/<GAME_VERSION> (Harmony, MonoMod, ...)
#   GAME_VERSION  game build whose DLLs to use (default: $STS2_GAME_VERSION or v0.107.1)
#   STS2_DLL      the game's sts2.dll (default: sts2-pycli/lib/<GAME_VERSION>/sts2.dll, which `just import-game` provides)
#   BASELIB_DLL   BaseLib.dll (optional; nothing the server does touches it)
#   BASELINE      transcript to diff against (default: <this dir>/golden/transcript.jsonl); BASELINE=none to skip
#   WIRE_BUILD    build directory (default: <this dir>/.build, gitignored)
#   DOTNET        dotnet executable (default: dotnet)
#   PYTHON        Python for the diff step (default: first working of python3, python, py)
#   NO_PLATFORM_SHIM=1  do not map SO_RCVTIMEO/SO_SNDTIMEO -1 -> 0 (see README: on Linux the
#                 current server then rejects every client, which is a platform artefact)
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
[[ $# -eq 2 ]] || { echo "usage: $0 <SpireMentor.dll> <out-dir>" >&2; exit 64; }
DLL="$(realpath "$1")"
OUT="$(mkdir -p "$2" && cd "$2" && pwd)"
REPO="${REPO:-$(cd "$HERE/../../.." && pwd)}"
GAME_VERSION="${GAME_VERSION:-${STS2_GAME_VERSION:-v0.107.1}}"
LIB="$REPO/sts2-pycli/lib/$GAME_VERSION"
# Pre-#789 checkouts keep the v0.107.1 DLLs flat in lib/.
[[ -f "$LIB/sts2.dll" || "$GAME_VERSION" != v0.107.1 || ! -f "$REPO/sts2-pycli/lib/sts2.dll" ]] || LIB="$REPO/sts2-pycli/lib"
STS2_DLL="${STS2_DLL:-$LIB/sts2.dll}"
BASELIB_DLL="${BASELIB_DLL:-}"
BASELINE="${BASELINE:-$HERE/golden/transcript.jsonl}"
DOTNET="${DOTNET:-dotnet}"
STUB_SRC="$REPO/sts2-pycli/src/GodotStubs"
BUILD="${WIRE_BUILD:-$HERE/.build}"

die() { echo "run_wire_test.sh: ERROR: $*" >&2; exit 3; }
[[ -f "$DLL" ]] || die "SpireMentor.dll not found: $DLL"
[[ -f "$STS2_DLL" ]] || die "sts2.dll not found: $STS2_DLL (run \`just import-game <game> --tier oracle\`, or set STS2_DLL)"
[[ -f "$LIB/0Harmony.dll" ]] || die "0Harmony.dll not found in $LIB"
[[ -d "$STUB_SRC" ]] || die "GodotStubs sources not found: $STUB_SRC"

mkdir -p "$BUILD"
# 1) GodotSharp STUB: copy sources out of the repo, build there (repo untouched).
mkdir -p "$BUILD/godotstubs-src"
cp "$STUB_SRC"/*.cs "$STUB_SRC"/*.csproj "$BUILD/godotstubs-src/"   # overwrite each run
# Stub augmentation (on the COPY only): SpireMentor's SimCombatUndo/SimRestore
# call Callable.From(Func<Task>) (real GodotSharp has From<TResult>(Func<TResult>));
# the stub lacks it, and Harmony must resolve every token of a method it patches.
grep -q 'From<TResult>(Func<TResult>' "$BUILD/godotstubs-src/Types.cs" || \
  sed -i 's|^\(\s*\)public static Callable From(Action action) => new(action);|&\n\1public static Callable From<TResult>(Func<TResult> func) => new(func);|' "$BUILD/godotstubs-src/Types.cs"
grep -q 'From<TResult>(Func<TResult>' "$BUILD/godotstubs-src/Types.cs" || die "could not augment GodotStubs Callable"
"$DOTNET" build "$BUILD/godotstubs-src/GodotStubs.csproj" -c Release -nologo -v q \
  -o "$BUILD/godotstub" >"$BUILD/godotstub.build.log" 2>&1 \
  || { cat "$BUILD/godotstub.build.log" >&2; die "GodotStubs build failed"; }
STUB_DLL="$BUILD/godotstub/GodotSharp.dll"

# 2) Harness host.
"$DOTNET" build "$HERE/host/BridgeHarness.csproj" -c Release -nologo -v q \
  -p:PycliLib="$LIB" -p:BaseIntermediateOutputPath="$BUILD/host-obj/" \
  -p:MSBuildProjectExtensionsPath="$BUILD/host-obj/" -o "$BUILD/host" >"$BUILD/host.build.log" 2>&1 \
  || { cat "$BUILD/host.build.log" >&2; die "harness build failed"; }

# 3) Run.
args=(--spirementor "$DLL" --out "$OUT" --godot-stub "$STUB_DLL" --sts2 "$STS2_DLL" --lib "$LIB")
[[ -n "$BASELIB_DLL" && -f "$BASELIB_DLL" ]] && args+=(--baselib "$(realpath "$BASELIB_DLL")")
[[ "${NO_PLATFORM_SHIM:-0}" == 1 ]] && args+=(--no-platform-shim)
set +e
timeout 600 "$DOTNET" "$BUILD/host/BridgeHarness.dll" "${args[@]}" 2>"$OUT/harness.stderr"
rc=$?
set -e
[[ $rc -eq 0 ]] || { tail -40 "$OUT/harness.stderr" >&2; echo "run_wire_test.sh: harness exit $rc" >&2; exit $((rc == 1 ? 2 : rc)); }
echo "run_wire_test.sh: transcript: $OUT/transcript.jsonl ($(wc -l <"$OUT/transcript.jsonl") events)"

# 4) Diff against baseline.
if [[ "$BASELINE" != none && -f "$BASELINE" && "$(realpath "$BASELINE")" != "$OUT/transcript.jsonl" ]]; then
  # First interpreter that actually runs: on Windows `python3` is often the
  # Microsoft Store alias stub (on PATH, exits non-zero), while `py` works.
  PY="${PYTHON:-}"
  if [[ -z "$PY" ]]; then
    for c in python3 python py; do
      if "$c" -c 'import sys' >/dev/null 2>&1; then PY="$c"; break; fi
    done
  fi
  [[ -n "$PY" ]] || die "no working Python found (tried python3, python, py; set PYTHON)"
  "$PY" -I "$HERE/diff_transcripts.py" "$BASELINE" "$OUT/transcript.jsonl" | tee "$OUT/diff.txt"
  exit "${PIPESTATUS[0]}"
fi
exit 0
