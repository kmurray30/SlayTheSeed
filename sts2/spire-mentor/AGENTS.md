# AGENTS.md

Instructions for coding agents (Claude Code, Codex, Cursor, …) working in this
repository. Humans: [CONTRIBUTING.md](CONTRIBUTING.md) says the same at more
length.

## Orientation

- A monorepo of four subprojects with independent build systems: `sts2-rs/`
  (Rust engine, MCTS, PPO inference, the wasm workbench), `sts2-pycli/` (Python
  CLI, C# oracle harness, PPO training, web app), `sts2-wiki/` (extraction
  tooling) and `spire-mentor-mod/` (the C# game mod). They reference each other
  by relative path (`../sts2-rs/…`).
- Read [CONTEXT-MAP.md](CONTEXT-MAP.md), then the `CONTEXT.md` of the
  subproject you touch: they define the domain terms the code uses.
  [ARCHITECTURE.md](ARCHITECTURE.md) is the big picture.
- The game's art, audio, assemblies and decompiled code are not in the
  repository. `just import-game` regenerates them from the user's own game
  install ([docs/bring-your-own-game.md](docs/bring-your-own-game.md)). Without
  it, everything builds and the game-free tests run; tests that need the game
  skip.

## Rules

- **Game mechanics are answered by the decompilation**
  (`sts2-wiki/decompiled/<version>/` after `just import-game`), never by wikis,
  memory or other reimplementations. Cite the decompiled file path; never paste
  decompiled code or game assets into the repository.
- **Rust tests: `cargo nextest run --workspace`, never bare `cargo test`.**
  Plain `cargo test` shares one process and produces order-dependent false
  failures. Then `cargo test --workspace --doc`, the one `cargo test`
  invocation, because nextest cannot run doctests. Fence non-Rust snippets in
  doc comments as ` ```text ` or they compile as doctests.
- **Python: use `uv`** (`uv sync`, `uv run …`, `uv add`), never `pip`.
- **`sts2-pycli/data/ppo/champions.toml` is the single source of truth** for
  the deployed PPO champions. Never hand-edit inside `GENERATED … DO NOT EDIT`
  markers; `just check-champions` (in `sts2-pycli/`) verifies them.
- **Before calling a change done**, run the gates in
  [CONTRIBUTING.md](CONTRIBUTING.md#local-gates) for every subproject you
  touched. CI (`.github/workflows/ci.yml`) runs the game-free subset on every
  push and pull request.
- Windows: see [BUILDING-WINDOWS.md](BUILDING-WINDOWS.md). The root justfile's
  recipes run under PowerShell; the subproject justfiles need Git Bash.
- Conventional commits: `feat(core): …`, `fix(workbench): …`.
