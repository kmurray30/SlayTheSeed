# sansb/sts2-sim cross-engine harnesses

These harnesses run spire-mentor's cross-engine workloads on
[sansb/sts2-sim](https://github.com/sansb/sts2-sim), the combat engine behind
Relay the Spire. They are ours, not part of that repository. They are Cargo
examples that use only the `sts-sim` crate's public API: the same calls its own
`sts-sim bench` and `examples/composition_sweep` make. The results and the
method are in
[`sts2-rs/docs/perf/2026-10-14-benchmarks.md`](../../../../sts2-rs/docs/perf/2026-10-14-benchmarks.md).

## Target

- **Repository and commit:** `sansb/sts2-sim` at `c61336cf05d289ae01ab9254beaa62c1b4ee407c`
  (2026-10-12, "Sync from monorepo 0b3c27dfb").
- **Game build:** v0.111.0. spire-mentor is on v0.107.1.
- **Crate:** `v0.111.0/engine` (`sts-sim` 0.1.0).
- **Toolchain:** rustc 1.97.1, pinned by `rust-toolchain.toml`.
- **Release profile** (the crate's own): `lto = "thin"`, `codegen-units = 1`,
  `panic = "abort"` and opt-level 3. There is no `target-cpu` (no
  `.cargo/config`, no RUSTFLAGS) and no custom global allocator, so it uses
  system malloc.

## Files

| File | Drop into | What |
|---|---|---|
| `xsim_w1_site.rs` | `v0.111.0/engine/examples/` | W1 "site" workload |
| `xsim_w2_toadpoles.rs` | `v0.111.0/engine/examples/` | W2 "toadpoles" workload, which is sansb's own bench fixture |
| `xsim_hp_bands.rs` | `v0.111.0/engine/examples/` | Prints the monster HP bands at an ascension, for the content diff |
| `hp_diff.py` | (run anywhere) | Compares the A0 HP bands of the W1 monsters, C# v0.107.1 against sansb |
| `setup.sh` | (run anywhere) | Clones or checks out the commit, copies the examples in and builds them |

## Build

`scripts/site-bench/bench/xsim/build_ext.sh` runs `setup.sh` (and the sts2core
build) into the layout the benchmark drivers expect; see
`scripts/site-bench/README.md`, "Reproducing the whole benchmark".

```bash
scripts/site-bench/xsim/sansb/setup.sh /path/to/sts2-sim /path/to/target
# equivalent by hand, inside the clone:
cd v0.111.0/engine
CARGO_TARGET_DIR=/path/to/target cargo build --locked --release \
  --bin sts-sim --example xsim_w1_site --example xsim_w2_toadpoles \
  --example xsim_hp_bands --example eval_throughput
```

The published 2026-10-14 run measured `xsim_w1_site.rs` as it was before a
comment-only edit (its header now names `scripts/site-bench/bench/w1_encounters.txt`
instead of a scratch path), so a copy from this tree differs from the measured
one in that doc comment only; the code is the same.

## Run

Every harness is single-threaded. To scale across the machine, launch N pinned
processes (`taskset -c CPU ...`). Each one prints ONE JSON object on its last
stdout line.

```bash
T=/path/to/target/release
$T/examples/xsim_w1_site                      # W1: 2200 seeds x 50 fights, engine-native uniform policy
$T/examples/xsim_w1_site --policy instance    # W1, random player matched to spire-mentor/the game
$T/examples/xsim_w1_site --relics none        # W1 without Burning Blood
$T/examples/xsim_w1_site --quick              # smoke: 44 seeds x 5 fights
$T/examples/xsim_w1_site --encounters MAWLER_NORMAL,VANTOM_BOSS   # any subset (an intersection)
$T/examples/xsim_w2_toadpoles                 # W2: 240,000 playouts (sansb's bench workload)
$T/examples/xsim_w2_toadpoles --policy instance
$T/sts-sim bench                              # sansb's own benchmark (PERF.md: best of 3-5 runs)
$T/examples/eval_throughput <clone>/v0.111.0/eval/fights   # sansb's own eval-fight throughput (~3 min)
```

W1 flags: `--seeds N` (2200), `--seed-start K` (0), `--reps R` (50),
`--encounters ID,...`, `--relics burning-blood|none`,
`--policy uniform|instance`, `--max-steps M` (5000),
`--dump-root SEED:PATH` (write that seed's start state as
`sts-sim-canonical-v2`) and `--quick`.

W2 flags: `--trajectories N` (240000), `--actions M` (64), `--seed S`,
`--warmup T` (2000), `--policy uniform|instance` and `--quick` (10,000).

## W1: the workload and how it differs here

W1 is spire-mentor's site workload M1, run on this engine. The player is
Ironclad at A0 with the 10-card starter deck, 80/80 HP and Burning Blood. Seed
`s` fights encounter `s mod 22` of the 22 Act-1 Overgrowth encounters, in
spire-mentor's registry order (`scripts/site-bench/bench/w1_encounters.txt`). Each seed's start
state is built once and not timed. Each fight clones the start state (timed)
and plays random legal actions until the fight ends.

- **Start state.** The engine's own combat opening (`entry::build_root`)
  builds it from a synthetic schema-20 save. That save is the checked-in
  `fixtures/entry_builder_v1.json`, re-pointed the way `composition_sweep` does
  it: A0, our deck and relic, 3 empty belt slots, every unlock epoch. Every run
  stream comes from the run-seed string `XSIMW1<seed>` at counter 0. The
  opening does monster creation (HP rolls and roster draws on the per-fight
  Encounter stream), the opening shuffle, the room-entry and before-combat
  hooks and the first player turn. The start states therefore come from this
  engine's RNG, not from ours. The encounter mix and the starting kit are the
  same, but individual fights are not.
- **Encounter ids.** All 22 ids are the game's own ids, and the engine accepts
  them unchanged as `ENCOUNTER.<id>`. Three elites resolve to sansb's
  registered keys by substring:
  - `BYGONE_EFFIGY_ELITE` resolves to `BYGONE_EFFIGY`.
  - `BYRDONIS_ELITE` resolves to `BYRDONIS`.
  - `PHROG_PARASITE_ELITE` resolves to `PHROG_PARASITE`.

  All 22 encounters build. At A0, the HP bands of all 29 monster kinds involved
  are identical to the v0.107.1 C# decomp (`hp_diff.py`).
- **Burning Blood is census-inert in this engine.** It is held in the
  inventory but has no combat body (`content_tables.rs` `RelicLedgerRow`:
  `CensusInert`, `implemented: false`). Its only effect is the +6 heal after a
  victory, which falls outside this engine's combat-only scope. Here it costs
  nothing per action. In spire-mentor's engine it is a live relic.
  `--relics none` gives the same play (the digest and checksum are identical)
  and changes only `roots_digest`.
- **Transitions.** The only transition API is
  `apply_action_into(&state) -> Result<HotState>`, which returns a successor
  for every action. That is clone-and-apply on every step. spire-mentor's
  engine mutates in place.
- **The legal-action list is deduplicated.** `legal_actions_into` collapses
  equal hand cards into one play, and identical monsters into one target (see
  the doc comment above `legal_actions_into` in `src/engine/mod.rs`). Under
  `--policy uniform`, the engine's own list, the random player therefore ends
  its turn more often than the per-card-instance player that spire-mentor's
  engine and the shipped game use. The result is different fights: about 19.0
  actions per fight and a 20% win rate. `--policy instance` weights each
  offered action by the number of (card instance, alive enemy) pairs it stands
  for. That restores the per-instance distribution: about 21.95 actions per
  fight and a 32.5% win rate. spire-mentor measured 21.99 and 32.4%, and the
  C# headless game 21.97 and 32%. The weights cost a few tens of nanoseconds
  per step inside the timed loop.
- **Policy RNG.** SplitMix64, seeded with `i*1_000_003 + r` and reduced with
  `next % n`, as in sansb's bench. spire-mentor uses Xoshiro256++ with
  `gen_range`, which is irrelevant across engines.

W1 output fields: `fights`, `wins`, `win_rate`, `actions` (every applied
action: card plays, end turns and selections), `actions_per_fight`, `digest`
(an FNV fold of each fight's `(length << 1 | won)`, spire-mentor's definition),
`action_checksum` (an FNV fold of every applied action, in sansb's bench
encoding), `roots_digest` (an FNV fold of every start state's differential
digest), `stops`/`anomalies`, `elapsed_s`, `fights_per_s`, `actions_per_s`,
`setup_s`, and `per_encounter` (fights, wins and actions per encounter, plus
the first root's monsters and HP).

## W2: the workload

W2 is sansb's `ironclad_toadpoles_uniform_v1`, re-implemented line for line,
with win and playout reporting added. It uses the same embedded entry, with
sha256 `6a5f557d8ae8b3966aeea58c623d2bdb6fdda9069ec3f1a4a05af32049f16ee2`.
The entry state is:

- **Player.** Ironclad at 68/80 HP, no relic, no potions.
- **Hand, already dealt.** STRIKE, STRIKE, DEFEND, STRIKE, STRIKE.
- **Draw pile, top first** (index 0 is drawn next). STRIKE, DEFEND, DEFEND,
  BASH, DEFEND.
- **Monsters.** Two Toadpoles:
  - slot 0: 26 HP, rotation position 2 = SPIKEN (the front toad);
  - slot 1: 25 HP, rotation position 1 = WHIRL.

  The Toadpole rotation is SPIKE_SPIT, then WHIRL, then SPIKEN +2 Thorns.
- **Ascension 10.** The entry has no `player.ascension` field, and admission
  reads a missing one as A10 (`v0.111.0/engine/src/boundary.rs:636`; the traces
  print `catalog ascension = 10`). So the Toadpoles hit at their A9
  `DeadlyEnemies` values, SPIKE_SPIT 4x3 and WHIRL 8 (3x3 and 7 below A9; C#
  v0.107.1 `Toadpole.cs:49,53`), and 26 HP is an A8 `ToughEnemies` roll (22–26,
  `Toadpole.cs:32,34`). Writing `"ascension":10` explicitly makes admission
  panic; only the elided field gives A10. Our `xsim_toadpoles` and the sts2core
  harness therefore run W2 at `--ascension 10` (their default).

The run uses the same SplitMix64 policy and per-trajectory seed, 2,000 warm-up
playouts and a 64-action cap. Under `--policy uniform`, `work.checksum` and
`work.transitions` equal `sts-sim bench`'s for the same arguments. This was
verified at 10k and 20k trajectories: `f2207dabd5455e02` with 170,189
transitions, and `e782c25c922f9820` with 340,579. If they ever differ, the two
did different work.

## Smoke results

These were taken on 2026-10-14 on an EPYC 7443P that other jobs were loading
heavily (load average 10–16). They are not measurements.

| Run | fights | wins | actions | digest / checksum |
|---|---:|---:|---:|---|
| W1 default (2200x50, uniform) | 110,000 | 22,327 (20.3%) | 2,090,654 (19.01/fight) | digest `0e9e0d42139c4a46`, actions `3783b55e1ed32583`, roots `8507541542907e89` |
| W1 `--policy instance` | 110,000 | 35,778 (32.5%) | 2,414,924 (21.95/fight) | digest `dda07e263c74afe9`, actions `5a36f4d9b145d035`, roots `8507541542907e89` |
| W2 `--quick` (10k, uniform) | 10,000 | 5,753 (57.5%) | 170,189 (17.02/playout) | `f2207dabd5455e02` (= built-in bench) |
| W2 20k `--policy instance` | 20,000 | 17,740 (88.7%) | 385,546 (19.28/playout) | `8c41cf7e24190b82` |
| W2 240k `--policy instance` (lighter load, later) | 240,000 | 211,793 (88.25%) | 4,623,932 (19.27/playout) | `476f9e9905c3e287` |
| W2 240k `--policy uniform` (lighter load, later) | 240,000 | 137,468 (57.28%) | 4,078,800 (17.00/playout) | `5d4f992689b53874` |

The rough speed under that load was about 17–20k fights/s and about
380–390k actions/s for W1, and about 400k transitions/s for W2 and the
built-in bench.
