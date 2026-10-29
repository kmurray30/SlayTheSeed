# Build the self-contained SimAdvisor.exe (GH #380).
#
# Uses a DEDICATED venv (.venv-advisor) synced with --only-group advisor-exe:
# the project deps (torch, wandb, fastapi, ...) are structurally absent, so
# they cannot leak into the bundle no matter what the spec's excludes miss.
#
# Gates (any failure aborts):
#   1. no torch in the bundle
#   2. bundle size sanity cap
#   3. SimAdvisor.exe --smoke exits 0 (exercises the .pyd, the bundled
#      localization datas, and ALL FIVE bundled ONNX champions through the
#      real resolvers AND one real forward pass each -- loading a session
#      alone never touches the encoder, the registry or session.run -- plus
#      a socket bind)
#
# Output: sts2-pycli/dist/SimAdvisor/
$ErrorActionPreference = "Stop"
$pycli = Split-Path -Parent $PSScriptRoot
Set-Location $pycli

$env:UV_PROJECT_ENVIRONMENT = ".venv-advisor"
uv sync --only-group advisor-exe
if ($LASTEXITCODE -ne 0) { throw "uv sync --only-group advisor-exe failed" }

uv run --no-sync pyinstaller packaging/SimAdvisor.spec `
    --distpath dist --workpath build --noconfirm
if ($LASTEXITCODE -ne 0) { throw "pyinstaller failed" }

# numpy and onnxruntime joined torch here once the run policy moved to
# `ppo.rust_policy` (tract, through the sts2_rs the advisor already loads for
# combat MCTS). A leak is not a crash: `ppo.onnx_policy` would simply be
# importable again and the bundle would quietly re-inflate by ~54 MB.
foreach ($dep in @("torch", "numpy", "onnxruntime")) {
    if (Test-Path "dist/SimAdvisor/_internal/$dep") {
        throw "$dep leaked into the bundle -- check the spec excludes and the build venv"
    }
}
$sizeMB = [math]::Round((Get-ChildItem dist/SimAdvisor -Recurse |
    Measure-Object Length -Sum).Sum / 1MB)
if ($sizeMB -gt 180) {
    throw "bundle unexpectedly large: $sizeMB MB (expected ~95-130) -- a fat dep leaked in"
}

& dist/SimAdvisor/SimAdvisor.exe --smoke
if ($LASTEXITCODE -ne 0) { throw "SimAdvisor.exe --smoke failed" }

Write-Host "OK: dist/SimAdvisor ($sizeMB MB)"
