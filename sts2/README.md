# Slay the Spire 2 tools

A seed finder and seed explorer for Slay the Spire 2 that run in the browser
with no server. They're built on [Spire Mentor](https://github.com/tfriedel/spire-mentor)
(MIT): a Rust port of the game's rules that compiles to WebAssembly, with a
browser workbench that already plays seeded runs with rewind and a branch tree.

## Layout

| Path | Description |
|------|-------------|
| `spire-mentor/` | `tfriedel/spire-mentor`, vendored with `git subtree` (squashed) at upstream commit `c60ce39e`. Our changes are ordinary commits on top. |
| `  └── sts2-rs/` | Rust engine (`sts2-core`, `sts2-content`), CLI (`sts2-cli`, including `seedfind`), browser engine shell (`sts2-workbench`, `sts2-workbench-wasm`) |
| `  └── sts2-pycli/web/` | The workbench web app (React); `VITE_WORKBENCH_MODE=static` builds the no-server version |
| `  └── sts2-pycli/src/Sts2Headless/` | Runs the real game's `sts2.dll` headless; the reference for accuracy checks |

Never commit game files (`sts2.dll`, the `.pck`), decompiled code, or
extracted art and audio. Spire Mentor's `just import-game` writes them into
git-ignored paths from your own install
(`spire-mentor/docs/bring-your-own-game.md`). Everything below builds and runs
without the game; the UI shows placeholder art.

## Prerequisites (macOS)

- Rust via [rustup](https://rustup.rs). The toolchain pinned in
  `spire-mentor/sts2-rs/rust-toolchain.toml` (1.99.1 plus the
  `wasm32-unknown-unknown` target) installs itself the first time you run
  `cargo` in that folder.
- `cargo install wasm-bindgen-cli --version 0.2.120 --locked`. The version
  must match `wasm-bindgen` in `spire-mentor/sts2-rs/Cargo.lock`.
- Node 20+ and Python 3. The browser build script uses only the standard library.

## Native CLI

```bash
cd sts2/spire-mentor/sts2-rs
cargo build --release -p sts2-cli --bin sts2-cli

# Checks fixed by the seed alone: Neow offers, bosses, encounters, event pool, Ancient offers
target/release/sts2-cli seedfind --spec crates/sts2-cli/examples/seedfind/prismatic_gem_ancient.toml \
    --character ironclad --seeds 200000 --limit 10

# Path-dependent checks (card rewards, relics, events), walking all three acts with fights skipped
target/release/sts2-cli seedfind --spec crates/sts2-cli/examples/seedfind/issue297_full_combo.toml \
    --character silent --seeds 50000 --paths-per-seed 4 --limit 3 --skip-fights
```

## Browser workbench (no server)

```bash
cd sts2/spire-mentor/sts2-pycli
python3 scripts/workbench_static.py build     # engine -> WebAssembly, web app -> web/dist
python3 scripts/workbench_static.py preview   # serves web/dist on http://127.0.0.1:4173
```

## Measured speed

Linux container, 4-core 2.1 GHz Xeon, one core unless noted. A Mac will be faster.

| What | Cost |
|------|------|
| Full game, simple heuristic playing every fight (no lookahead) | 3–9 ms per run; 100 runs in 0.36 s |
| Full game, Spire Mentor's strongest AI (250 lookahead simulations per move, 1,000 at bosses) | ~4–5 CPU-seconds per run (their benchmark) |
| Seed finder, seed-alone checks | ~1,000 seeds/s; ~3,700 seeds/s on 4 cores |
| Seed finder, path walk with fights played by random moves | over 15 s per seed |
| Seed finder, path walk with `--skip-fights` | ~250 seeds/s (~4 ms per seed, through Act 3) |
| Same seed-alone check in headless Chromium (WebAssembly, one thread) | 1.3 ms per seed (~760/s) |
| Same path walk with fights skipped, in headless Chromium | 5.1 ms per seed (~195/s), all three acts |
| Engine download | 4.5 MB brotli / 6.8 MB gzip with the AI runtime; ~1 MB brotli without it (their measurement) |

## Accuracy status

Spire Mentor checks its engine against the real game run headless, comparing
state after every action:

- **Fights:** Ironclad 6,104/6,104 clean; the other characters 99.4–99.8%.
- **Whole runs:** 1,232/1,250 clean. Those runs use random play, which mostly
  dies in Act 1, so Acts 2–3 are not verified yet.

Verifying full runs against the real game on macOS is on our list.

## RNG model (how it differs from STS1)

- STS2 keeps one set of random streams for the whole run, with no per-floor
  reseed like STS1. Fights advance the shuffle, enemy-AI and "niche" streams,
  so how you play one fight changes later fights' draws, intents and monster HP.
- Card rewards, potions, shops and card transforms use per-player streams that
  fights never touch. Seed finder and explorer results therefore don't depend
  on how you fight. The exception is the few pickups that roll the shared niche
  stream, such as Whetstone, War Paint and Pandora's Box.
- Events mostly have their own per-event randomness. Several moved off the
  shared niche stream in v0.107.1.

## Changes on top of upstream

- `sts2-cli seedfind --skip-fights` resolves each fight on a walked path as an
  instant full-HP win, so path-dependent checks run in milliseconds.

## Updating from upstream

```bash
git subtree pull --prefix=sts2/spire-mentor https://github.com/tfriedel/spire-mentor main --squash
```

## Next

1. **Browser seed finder.** Expose `seedfind` (with fights skipped) through the
   WebAssembly workbench. Web Workers scan seed ranges in parallel, with a
   filter builder modelled on the STS1 `searchConfig.json`.
2. **Seed explorer views.** A seed overview (all three acts' maps, bosses,
   Ancients, event pool), skip or auto-play fights, and a branch tree with
   backtracking.
3. **Accuracy pass.** Full runs on macOS compared against the real game,
   with fixes sent upstream.
