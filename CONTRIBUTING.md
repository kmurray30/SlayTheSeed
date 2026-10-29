# Contributing

## How this repository is maintained

This is a **snapshot export** of a private development monorepo. Development
happens there; every so often its tree is exported — with all game-derived
content left out and verbatim decompiled code stripped from comments — and
mirrored here as one `sync: spire-mentor-private@<sha>` commit. So:

- The history here is a series of snapshots, not the development history.
- Pull requests are welcome. A merged PR is re-applied to the private repo
  and comes back in the next sync commit (which may also rewrite unrelated
  files); the sync tooling refuses to run while a merged PR is still
  unported, so it cannot be silently undone. Keep PRs focused so they apply
  cleanly.
- Never commit game art, audio, assemblies or decompiled code. The paths
  `just import-game` produces are git-ignored (see the block at the end of
  `.gitignore`); if you find yourself force-adding one, stop. (The game's
  text and extracted data JSON are committed on purpose.)

## Hard invariants

- **Rust tests: nextest, never bare `cargo test`.**
  `cd sts2-rs && cargo nextest run --workspace` (per-process isolation), plus
  `cargo test --workspace --doc` — the one `cargo test` invocation, because
  nextest cannot run doctests.
- **Doc comments that quote decompiled game code** use a code fence tagged
  `csharp`; they exist only in the private repo and are replaced by a
  placeholder line here. Do not add game code or game assets to this
  repository in any form — cite the decompiled file path instead.
- **`sts2-pycli/data/ppo/champions.toml` is the single source of truth** for
  the deployed PPO champions; two files carry regions generated from it
  (`sts2-rs/crates/sts2-runai/src/ppo/net.rs`) — never hand-edit inside the
  `GENERATED … DO NOT EDIT` markers; `just check-champions` (sts2-pycli)
  byte-checks the sync.
- **Game mechanics questions** are answered by the decompilation of your own
  game copy (`sts2-wiki/decompiled/<version>/` after `just import-game`),
  never by secondary sources.

## Local gates

```sh
cd sts2-rs
cargo check --workspace --all-targets
cargo clippy --workspace --all-targets -- -D warnings
cargo fmt --check
cargo nextest run --workspace
cargo test --workspace --doc
cargo check --target wasm32-unknown-unknown -p sts2-workbench-wasm   # the browser build

cd ../sts2-pycli
uv run ruff check . && uv run ruff format --check .
uv run pytest tests               # oracle tests skip without the game
just check-champions
cd web && npm test && npx tsc -b && npx eslint . && npx vite build

cd ../../sts2-wiki                # the extraction / import tooling
just check-format check-types test   # tests needing the game skip without it
```

CI runs the game-free subset of exactly these on every push and pull
request (`.github/workflows/ci.yml`).

## Conventional commits

`feat(core): …`, `fix(workbench): …`, `docs(content): …` — the changelog is
generated from them.
