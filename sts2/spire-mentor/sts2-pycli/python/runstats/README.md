# Run-trajectory recording & stats

Record reproducible per-run `.run` trajectory files from the deployed run-AI
and run spire-codex / sts2replays-style statistics over them.

## 1. Record a corpus (Rust)

`run_arena sweep` gains `--record-runs <dir>` (+ `--record-detail`). It writes
one gzipped `<seed>.run.gz` per run:

- **Top level** = the game's native `RunHistory` v9 schema (deck with
  `floor_added_to_deck` + upgrades, relics, potions, `map_point_history` with
  monster ids, seed, ascension, `build_id`, `win`, `killed_by_*`). Parseable by
  `run_files.load_run` and any spire-codex-style tooling.
- **`sts2sim` block** = our extension: `meta` (repro: policy, model sha256,
  engine git sha, sim budgets, env, seed), the full `decisions[]` stream
  (offered-vs-taken at every card-reward / event / shop / rest / map / boss-relic
  node), and per-ply `combat` boards (hand, enemies+intents, energy/block).

```sh
# Ironclad PPO + MCTS (1000 sims / 4000 boss), 1000 seeds at A0:
cd sts2-rs
STS2_RUNAI_PPO=../sts2-pycli/data/ppo/ppo_fullrun_ironclad_asc_it55.onnx \
  ./target/release/run_arena sweep \
    --seeds 1000 --seed-start 0 --policy ppo --combat eval-cheap --ascension 0 \
    --record-runs data/run-trajectories/ppo-ironclad-a0 --record-detail full \
    --out data/run-trajectories/ppo-ironclad-a0/report.jsonl
```

`--record-detail`: `summary` (native only) | `decisions` (+ off-combat choices) |
`full` (+ per-ply combat, the default). Determinism: same seed + same engine/
model reproduce a byte-identical file (repro inputs are in `sts2sim.meta`).

## 2. Analyse (Python)

```sh
cd sts2-pycli
PYTHONPATH=python python -m runstats.analyze \
  ../sts2-rs/data/run-trajectories/ppo-ironclad-a0 \
  --min-n 20 --out report.md --json report.json
```

Reports: card win-rate (present in final deck, Wilson-lower-bound ranked),
pick quality (winrate if picked − if skipped, from the offered stream), card
combos (pair co-occurrence), event-choice win-rate, synergy usage (do decks that
*hold* a combo actually *play* it?), and deck aggression (great/fast vs safe/
grindy). Programmatic use:

```python
from runstats.load import load_corpus
from runstats import analyze
runs = load_corpus("path/to/corpus")
print(analyze.card_winrate(runs, min_n=20)[:10])
```

Tune the synergy catalog in `analyze.DEFAULT_SYNERGIES` against the real card
tokens surfaced by `card_winrate`.
