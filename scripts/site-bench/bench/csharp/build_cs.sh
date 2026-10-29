#!/usr/bin/env bash
# Build what measure_cs.sh runs: a Release build of the headless harness
# (sts2-pycli/src/Sts2Headless) from a clean export, plus the trees it expects.
#   BENCH_DIR=<scratch> bash build_cs.sh
# Outputs under $BENCH_DIR/csharp:
#   src/      git archive of CS_REF's sts2-pycli/src + python/sts2_client.py (the checkout is not touched)
#   bin/cs/   `dotnet build -c Release -o` of Sts2Headless, with the game's assemblies in bin/cs/lib/
#             (Program.cs looks for <exe dir>/lib first; the csproj references them Private=false,
#             so -o does not copy them), and bin/cs.MD5
#   pycli/    python/sts2_client.py + lib/ (the game's assemblies), the --pycli tree of the harnesses
# Env: GAME_DIR (the game's assemblies, sts2.dll and its dependencies; default
#   $REPO/sts2-pycli/lib/v0.107.1, filled by `just import-game`), CS_REF (default 6d1918237;
#   the published binary was built from 37693f17f, whose sts2-pycli/src and
#   sts2_client.py are identical), DOTNET (default: dotnet on PATH; a .NET 9 SDK).
# The published run used sts2.dll md5 282bfb4ccec5b17c51dea6b37ccb4791 (game build v0.107.1).
set -euo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
. "$HERE/../env.sh"   # REPO, BENCH_DIR, SB
B=$BENCH_DIR/csharp
# The pinned bench ran v0.107.1; DLLs live in lib/<version>/ since GH #789 (flat lib/ before).
GAME_DIR=${GAME_DIR:-$REPO/sts2-pycli/lib/v0.107.1}
[ -f "$GAME_DIR/sts2.dll" ] || [ ! -f "$REPO/sts2-pycli/lib/sts2.dll" ] || GAME_DIR=$REPO/sts2-pycli/lib
CS_REF=${CS_REF:-6d1918237}
DOTNET=${DOTNET:-dotnet}

[ -f "$GAME_DIR/sts2.dll" ] || { echo "no $GAME_DIR/sts2.dll: set GAME_DIR to the game's assemblies (just import-game)"; exit 1; }
if head -c 24 "$GAME_DIR/sts2.dll" | grep -q 'git-lfs'; then echo "$GAME_DIR/sts2.dll is an LFS pointer"; exit 1; fi
echo "game assemblies: $GAME_DIR (sts2.dll md5 $(md5sum "$GAME_DIR/sts2.dll" | cut -d' ' -f1))"

sha=$(git -C "$REPO" rev-parse "$CS_REF^{commit}")
if [ -e "$B/src" ]; then mv "$B/src" "$B/old-src-$(date +%s)"; fi
mkdir -p "$B/src"
git -C "$REPO" archive --format=tar "$sha" sts2-pycli/src sts2-pycli/python/sts2_client.py | tar -x -C "$B/src"
echo "$sha" > "$B/src/EXPORTED_SHA"
mkdir -p "$B/src/sts2-pycli/lib" && cp "$GAME_DIR"/*.dll "$B/src/sts2-pycli/lib/"

if [ -e "$B/bin/cs" ]; then mv "$B/bin/cs" "$B/bin/old-cs-$(date +%s)"; fi
mkdir -p "$B/bin"
"$DOTNET" build "$B/src/sts2-pycli/src/Sts2Headless/Sts2Headless.csproj" -c Release -o "$B/bin/cs" \
  -p:TreatWarningsAsErrors=false
mkdir -p "$B/bin/cs/lib" && cp "$GAME_DIR"/*.dll "$B/bin/cs/lib/"
(cd "$B/bin/cs" && md5sum Sts2Headless Sts2Headless.dll GodotSharp.dll lib/*.dll) > "$B/bin/cs.MD5"

mkdir -p "$B/pycli/python" "$B/pycli/lib"
cp "$B/src/sts2-pycli/python/sts2_client.py" "$B/pycli/python/"
cp "$GAME_DIR"/*.dll "$B/pycli/lib/"
cat "$B/bin/cs.MD5"
echo "built $B/bin/cs from $sha; next: bash $HERE/measure_cs.sh prep"
