#!/usr/bin/env bash
# Build and run the mod's game-free unit tests (tests/unit). Exit 0 = all pass.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOTNET="${DOTNET:-dotnet}"
BUILD="$HERE/.build"
mkdir -p "$BUILD"
"$DOTNET" build "$HERE/ModUnitTests.csproj" -c Release -nologo -v q \
  -p:BaseIntermediateOutputPath="$BUILD/obj/" \
  -p:MSBuildProjectExtensionsPath="$BUILD/obj/" \
  -o "$BUILD/bin" >"$BUILD/build.log" 2>&1 \
  || { cat "$BUILD/build.log" >&2; echo "run_unit_tests.sh: build failed" >&2; exit 3; }
"$DOTNET" "$BUILD/bin/ModUnitTests.dll"
