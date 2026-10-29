# Benchmark host: hardware and software

Copy this file to `$BENCH_DIR/facts/hardware.md` and fill in the Value column
(`bash hw_snapshot.sh` prints most of it). `../results/analyze.py` reads every
`| Item | Value | Source |` row below by its Item name, so keep the names as
they are; a missing row stops the analysis with a KeyError.

## Machine

| Item | Value | Source |
|---|---|---|
| System | | `/sys/class/dmi/id/{sys_vendor,product_name,board_name,bios_version}` |
| L1d / L1i / L2 | | `lscpu` |
| L3 | | `lscpu`, `cache/index3/size` |
| RAM | | `udevadm info /sys/devices/virtual/dmi/id` (MEMORY_DEVICE_*) |
| OS / kernel | | `/etc/os-release`, `uname -a` |
| Sharing | | whether other users or jobs share the host |

## Software

| Item | Version | Source |
|---|---|---|
| Rust | | `rustc -vV` in `sts2-rs/` |
| .NET | | `dotnet --list-sdks`, `dotnet --list-runtimes` |
| Python (worktree venv) | | `sts2-pycli/.venv/bin/python -V` |
| PyTorch | | `torch.__version__` |
| Node | | `node -v` |
| Playwright / Chromium | | `sts2-pycli/web/node_modules/playwright{,-core}` |
| wasm-bindgen / wasm-opt | | `wasm-bindgen --version`, `wasm-opt --version` |
| perf | | `perf --version` |
