# python/ — the Python AI stack

This is a flat script tree, not an installed package: `pyproject.toml` puts it
on `sys.path` (`[tool.pytest.ini_options] pythonpath = ["python"]`), so modules
import each other by bare name and scripts run either directly
(`python3 python/play.py`) or as modules from the repo root with
`PYTHONPATH=python`. The pytest suite lives in `../tests/`; the handful of
`test_*.py` files *here* are gate scripts invoked by explicit path.

The ~100 top-level files group into the concerns below. "Entry point" is the
file you actually run; the rest of each row is supporting modules.

## Playing the game

- **Interactive CLI** — entry: `play.py`. Terminal player over the C# headless
  runner. Built on `sts2_client.py` (JSON-over-stdio `Game` client),
  `sts2_combat.py` / `sts2_format.py` (state rendering), `game_log.py` (JSONL
  run logs to `logs/`).
- **MCP server** — entry: `sts2_mcp.py` (stdio MCP server; typed tools for
  Claude or any MCP client). `mcp_viewer.py` tails its log and re-renders the
  live state.
- **MCTS hint sidecar** — package: `sts2_hint/` (no standalone entry; wired
  into `play.py`). Translates headless state for the `sts2_rs` extension and
  surfaces ranked suggestions next to each choice.
- **Batch smoke runs** — entry: `play_full_run.py` (random-policy full runs;
  backs `just regression-test`).

## C#-vs-Rust parity

See `../docs/parity-suite.md` for the full map of this area before touching it.

- **Combat parity harness** — entry: `parity_audit.py` (identity-aware audit
  driver with divergence taxonomy and sweep tiers). Built on `parity_fuzz.py` /
  `parity_fuzz_scenario.py` / `parity_fuzz_state.py`, `first_divergence.py`,
  `divergence_classifier.py`, `sweep_manifest.py`; `mutation_test.py` checks
  the harness itself catches injected bugs.
- **Run-level parity** — entry: `run_parity.py` (whole-run lockstep) and
  `act23_state_parity.py` (invincible lockstep deep into Acts 2/3). Around
  them: `act23_map_parity.py`, `r2_initial_state_diff.py`, `r3_map_parity.py`,
  `r3_neow_parity.py`, `event_parity.py`, `event_coverage.py`,
  `run_setup_parity.py`, `spawn_roster_parity.py`, `parity_walk.py`.
- **Parity diagnostics** — one-off drill-downs, run by explicit path:
  `diag_*.py`, `trace_*.py`, `draw_sequence_trace.py`, `debug_lockstep.py`,
  `dump_seed.py`, `triage_cascades.py`, `repro_bucket.py`, `necro_repro.py`.

## Training and evaluation

- **PPO run-policy training** — entry: `python -m ppo.trainer` (see
  `../../docs/ppo-champion-training-playbook.md` before training or promoting).
  Package `ppo/` holds the env, encoder, model, ONNX export, and parity
  fixtures; `champions.py` is the sole reader of the deployed-champion
  manifest `../data/ppo/champions.toml`.
- **Self-play drivers** — entries: `play_full_run_rust.py` (Rust engine
  full-run self-play), `play_full_run_ppo.py` (PPO-meta headless ladder
  rungs), `run_arena.py` / `label_campaign.py` (wrappers over the Rust
  `run_arena` binary). `run_policy.py` is the shared run-level policy layer.
- **Value / surrogate nets** — entries: `train_run_value_net.py` (whole-run
  win-prob net) and `train_combat_surrogate_net.py` (combat surrogate;
  `train_combat_surrogate.py` is the retired sklearn prototype). Evaluated by
  `eval_run_value_net.py`, `eval_combat_surrogate*.py`; gated by
  `test_run_value_gate.py` / `test_surrogate_gate.py` (+ export tests) against
  the vendored `fixtures/`. Feature prep: `selfplay_to_value_inputs.py`,
  `train_value.py`, `retune_v.py`.

## Analysis and UI

- **Workbench** — entry: `python -m workbench` (FastAPI backend; `just
  workbench` + `just workbench-web` from the repo root of this subproject).
  Package `workbench/` also carries the recorder (`workbench.record`),
  aggregation/reports, regret review, and the PPO inference backends.
- **SimLab** — entry: `python -m simlab run|summarize` (`just sim`). Batch
  combat simulator on the Rust engine comparing play-style strategies over
  seed-paired sweeps.
- **Run-corpus statistics** — entry: `python -m runstats.analyze`. Card
  win-rates, pick quality, combos, event choices over a `.run` corpus; see
  `runstats/README.md`.
- **Synergy discovery** — entry: `python -m synergy.cli` (observational +
  causal card-synergy catalog pipeline over the scraped corpus).
- **Corpus replay** — entry: `act1_replay.py` (drive the Rust engine through
  scraped human runs). Package `replay/` is the phase-dispatch driver;
  `run_files.py` reads `.run`/`.run.gz`; `run_stepper.py`, `shadow_replay.py`,
  `shadow_sweep_summary.py` replay recorded logs against a shadow engine.

## Real game (SpireMentor)

- **Bridge clients** — transport: `realgame_bridge.py` (stdlib-only TCP client
  for `../../spire-mentor-mod/PROTOCOL.md`); `sts2_realgame.py` adapts the live
  game to the headless decision-point schema. Drivers: `realgame_random.py`,
  `realgame_mcts.py`, `realgame_ppo.py`, `realgame_hand_select.py`,
  `realgame_parity.py`, `realgame_adapter.py`; eval: `realgame_eval.py`.
- **Advisor** — entry: `realgame_advisor.py` (answers the mod's
  `advice_request`s; frozen into the SimAdvisor exe via
  `../packaging/SimAdvisor.spec`). `runtime_paths.py` resolves paths inside
  the frozen bundle.
- **Headless eval rung** — entry: `headless_eval.py` (real game DLL as world,
  Rust engine as search model — the rung between pure-sim and live).
  `combat_mirror.py` keeps a persistent per-fight engine mirror for these
  drivers.

## Experiments and one-offs

- **Boss-sim studies** — entries: `boss_only.py` / `boss_sweep_parallel.py`
  (fixed boss-fight sweeps), `exp_boss_sim_winrate.py`,
  `exp_boss_sim_summarize.py`, `proxy_mi_study.py`,
  `bench_csharp_throughput.py`.
- **`tools/`** — dataset/gate makers for ascension studies (`asc_ab.py`,
  `make_rel500.py`, …) with their own `test_*.py` beside them.
