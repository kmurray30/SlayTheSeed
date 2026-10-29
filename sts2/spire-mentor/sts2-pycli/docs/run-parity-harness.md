# Whole-run parity harness (C# vs Rust)

`python/run_parity.py`. Companion to the combat parity suite
(`docs/parity-suite.md`). Where the combat suite compares **one combat arena**
for up to N turns, this harness drives an **entire run** — Neow → map traversal
→ combats → events → shops → rest sites → card/relic rewards → act transitions →
boss — carrying HP/deck/relics/gold/potions between nodes.

## Design

Both engines are driven through the shared `workbench/backends` `GameBackend`
interface: the C# `Game` subprocess and the Rust `RustGame` (PyO3
`PyRunManager`). Both accept the **same action verbs** (`select_map_node`,
`choose_option`, `play_card`, `end_turn`, `select_card_reward`, `buy_card`, …).

Two modes:

- **`--rng-parity`** (representation-robust): for each seed, start a run on both
  engines and compare the act-1 **boss** (rolled from the run-setup RNG).
  Isolates run-level RNG alignment from decision-modeling skew. This is the
  highest-signal run-level metric.
- **default (lockstep)**: every decision is enumerated from the **C# decision
  view** (the oracle), one choice is picked with a seeded RNG, and the *same*
  verb is applied to BOTH engines. After each step both engines' canonical
  run-snapshots (phase/hp/gold/deck-multiset/relics/potions, plus map structure
  in the map phase) are diffed; the first differing field is reported.

Run:

```bash
scripts/wheel-venv.sh run -- python python/run_parity.py --rng-parity --seed RP --seeds 50
scripts/wheel-venv.sh run -- python python/run_parity.py --seed PARITY1 --steps 400 --verbose
```

## Findings (2026-06-10) — whole-run parity is NOT established

Unlike combat (which Rust was built C#-faithful for), the run layer diverges
structurally. Two independent gap classes:

### 1. Run-level RNG is not aligned with C# (foundational)

`--rng-parity` over fresh seeds shows the act-1 boss matching **~5/15 ≈ chance
level** (3 possible bosses). The boss is rolled from the run-setup RNG, so the
map, encounters, and rewards all inherit the same misalignment.

This is **not** a map-generation bug and **not** a string-hash bug:

- `deterministic_hash_code` matches Python/C# (`map_generator_parity.rs`).
- `generate_act_map(numeric_seed)` is byte-equivalent to the C# snapshot for
  seeds 0/42/2026 (`standard_act_map_matches_csharp_snapshot`, green).

The divergence is in **how the run-setup RNG stream seed feeding map/boss
generation is derived** in a full run. Root cause is architectural: Rust's
`RunRngSet` (`crates/sts2-core/src/run_rng_set.rs`) explicitly mirrors the
**Python RL-agent** model (`sts2-rl-agent@01c094d`, `RUN_MANAGER_RNG_SEED_OFFSET
= 9999`, "exactly as Python does"), **not** the C# game. Where the Python
reference diverged from C#, the run diverges. Combat RNG was made C#-faithful
through a long bead campaign; the run-level RNG never was.

### 2. Decision-point granularity / labeling skew (pervasive)

The Rust run-layer (a port of the Python RL environment) models decision points
at a different granularity than C#, even when content + RNG agree:

- **Neow options match** in order and content (Lead Paperweight / Pomander /
  Hefty Tablet), but each leads to a different *phase*: C# always presents an
  interactive `card_select`; Rust either auto-resolves (Pomander "upgrade a
  card" → straight to `map_select`) or relabels (`card_reward`).
- `RustGame.start` already aligns *some* of this (`interactive_card_removal`,
  `direct_event_relic_grant`); the upgrade path has no `interactive_card_upgrade`
  analog, so it auto-picks.

Strict micro-decision lockstep therefore hits this skew immediately (step 1 on
most seeds) — benign labeling/granularity noise rather than engine bugs.

## What "resolving" requires

This is a campaign, not a one-shot fix, and it gates on an architectural
decision: **should the Rust run-layer be C#-faithful or stay Python-RL-faithful?**
They were deliberately built Python-faithful. Re-anchoring run-level RNG to C#
(class 1) is the run-scope analog of the entire combat-parity campaign and
carries regression risk to existing Python-anchored run fixtures. Class 1 must
land first — until the map/boss/encounters match seed-for-seed, decision-level
parity (class 2) is moot because the runs already face different rooms.

Tracked as beads `sts2-rs-<run-rng>` (class 1) and `sts2-rs-<run-decisions>`
(class 2).
