# Inspecting MCTS runs with the workbench

How to record what the engine's MCTS does over a combat, then look at *why it
won or lost* — as agent-readable Markdown, a batch loss summary, or the
interactive workbench SPA.

This replaces the old Rust `sts2-fight-replay` HTML flow (and
`sts2-rs/scripts/analyze_vantom_losses.py`), both now deprecated.

## The idea: one log, many views

```
                  ┌─ workbench.report     Markdown, one run   (read/grep)
record ─► JSONL ──┤─ workbench.aggregate  batch loss patterns (read/grep)
                  └─ workbench SPA         timeline + Reasoning + MCTS Runs page
```

A recorder drives MCTS over a fixture (or a full self-play run) and writes a
canonical JSONL log to `logs/`. Everything else just reads that log.

## Prerequisite: the wheel must be current

The recorder uses `sts2_rs.combat_state_from_scenario_toml`, so the `sts2_rs`
wheel must be built from current source. **Bare `uv run` uninstalls the maturin
editable `sts2_rs`**, so route recorder commands through the wheel-preserving
wrapper:

```bash
cd sts2-pycli
scripts/wheel-venv.sh build            # once, after pulling Rust changes (~30s compile)
# then run anything that imports sts2_rs via:
WHEEL_VENV_NO_BUILD=1 scripts/wheel-venv.sh run -- <cmd>
```

`report` and `aggregate` are pure-Python (no `sts2_rs`), but running them through
the same wrapper avoids re-triggering the uninstall churn.

## 1. Record

```bash
# single combat from a .run-source fixture
WHEEL_VENV_NO_BUILD=1 scripts/wheel-venv.sh run -- \
  python -m workbench.record --fixture <fixture.toml> --sims 800 --out logs

# full self-play run (best-effort; run-sim layer is partial)
WHEEL_VENV_NO_BUILD=1 scripts/wheel-venv.sh run -- \
  python -m workbench.record --run --character ironclad --seed SOMESEED --out logs
```

`--sims` is MCTS budget per decision (default 3000; lower is faster, weaker play
→ more losses). Logs land in `logs/<ts>_<char>_<stem>.jsonl`.

**Filter the corpus to the engine's build first.** The `.run` corpus spans many
game versions (v0.98–v0.107); the engine is pinned to **v0.103.2** (v0.103.3
tolerated). Recording older-version decks contaminates results (removed cards,
different mechanics). Select version-clean, human-won Vantom fixtures:

```bash
sel=$(grep -lE 'build_id += +"v0.103.(2|3)"' /tmp/run-fixtures-full/the-ironclad/*vantom_boss.toml \
      | xargs grep -l 'player_outcome = "won"' | head -8)
for fx in $sel; do
  WHEEL_VENV_NO_BUILD=1 scripts/wheel-venv.sh run -- \
    python -m workbench.record --fixture "$fx" --sims 800 --out logs
done
```

Fixtures whose decks/relics use unported content (e.g. `BeatingRemnant`,
`HeftyTablet`, the deferred Neow relics) fail fast with a `ValueError` and are
skipped — an engine content gap, not a recorder bug.

## 2. Read which runs we lost (batch)

```bash
WHEEL_VENV_NO_BUILD=1 scripts/wheel-venv.sh run -- python -m workbench.aggregate logs
```

Reports `n_runs`/`n_losses`, a final-turn histogram, the turn-1 root-Q
distribution, pattern tags (`hopeless_position`, `no_damage`, `fast_death`), and
the representative losing runs.

```
runs=5 losses=4
final-turn histogram: {'6': 2, '11': 1, '5': 1}
turn-1 Q: {'min': -1.45, 'max': 0.16, 'mean': -1.05}
pattern tags: {'hopeless_position': 3, 'no_damage': 4}
```

## 3. Read one run in detail (Markdown)

```bash
WHEEL_VENV_NO_BUILD=1 scripts/wheel-venv.sh run -- python -m workbench.report logs/<run>.jsonl
```

Renders, per decision: state (turn/HP/block/energy, hand, monsters + intents)
and the MCTS table — rank / action / visits / share / **Q** / **W/L/T** —
plus the recommended action and principal variation.

```
## T1 · combat_play · HP 52/80 blk 0 e 3
- hand: BASH, MIMIC, ASCENDERS_BANE, STRIKE_IRONCLAD, POMMEL_STRIKE
- Vantom 173/173 blk 0 → Attack 7

| rank | action        | visits | share | Q     | W/L/T   |
| 0    | POMMEL_STRIKE | 257    | 32%   | -1.45 | 0/257/0 |
| 3    | end turn ⭐    | 88     | 11%   | -1.45 | 0/88/0  |
```

`W/L/T` is rollout wins/losses/ties. `0/n/0` across every action at turn-1
full-HP is the **eval-bound** signature: the leaf evaluation is blind to a
mechanic (e.g. Vantom's Slippery wall), so every line looks lost and MCTS has no
gradient — it's not a search-budget problem. See
`../../sts2-rs/docs/audits/2026-06-07-vantom-boss-investigation.md`.

## 4. Interactive (the SPA)

```bash
just workbench       # backend (FastAPI :8077)
just workbench-web   # frontend (Vite :5173)
```

Open http://localhost:5173 → the **MCTS Runs** page lists recorded runs with
outcome / final turn / turn-1 Q; click a losing run to step its timeline. The
Reasoning panel shows the persisted decision-time search with W/L/T per action
(no live recompute). Logs come from `logs/` (override `STS2_LOGS_DIR`).

## Just shortcuts

```bash
just record-fixture <fixture.toml> [sims]   # record one fixture
just report <log.jsonl>                     # Markdown for one run
just aggregate <logs_dir>                   # batch loss patterns
```

(These call bare `uv run`; if the wheel is stale, rebuild with
`scripts/wheel-venv.sh build` first.)
