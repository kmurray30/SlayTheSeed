# Cross-engine harnesses for lain-wood/sts2core

Two benchmark harnesses that run spire-mentor's cross-engine workloads on
[lain-wood/sts2core](https://github.com/lain-wood/sts2core). Neither file is part
of sts2core; drop them into a checkout and build them with sts2core's own release
profile. The results and the method are in
[`sts2-rs/docs/perf/2026-10-14-benchmarks.md`](../../../../sts2-rs/docs/perf/2026-10-14-benchmarks.md).

| File | Workload |
|---|---|
| `xsim_w1_site.rs` | **W1 "site"**: spire-mentor's M1 (`sts2-rs/crates/sts2-bench/examples/site_random_bench.rs`). Ironclad A0, starter deck at 80/80 HP plus Burning Blood, the 22 Act-1 Overgrowth encounters round-robin (seed `s` gets encounter `s % 22`, in spire-mentor's `ACT1_ENCOUNTERS` order). Each seed's start state is built once, untimed. Each fight copies it (timed) and plays uniformly random legal actions to the end, 50 fights per seed and 2,200 seeds by default. |
| `xsim_w2_toadpoles.rs` | **W2 "toadpoles"**: sansb/sts2-sim's own bench, `ironclad_toadpoles_uniform_v1`. The entry is the fixture's turn-1 state: hand S S D S S, draw pile (top first) S D D Bash D, Toadpoles at 26 HP (opens SPIKEN) and 25 HP (opens WHIRL), player at 68/80, **ascension 10** (`--ascension`, default 10). The fixture has no `player.ascension` field and sansb reads that as A10 (`v0.111.0/engine/src/boundary.rs:636`), so the Toadpoles hit at their A9 values: Spike Spit 4×3 and Whirl 8, against 3×3 and 7 at A0 (C# `Toadpole.cs:49,53`). The 26 HP is only a legal roll from A8 (22–26, `Toadpole.cs:32,34`). The workload is 240,000 uniform playouts with a cap of 64 actions, using sansb's SplitMix64 policy stream and seed. |
| `per_encounter_compare.py` | Per-encounter win rate and actions per fight, spire-mentor M1 against sts2core W1. It reports counts only, no timing. |

## Target commit

`3b2d969d025b9e64bc96b9a15928bd2a0d824bf2` (2026-09-25 16:28:25 +0800,
"docs: README 刷到 2026-09-25"). A fetch on 2026-10-14 found this commit at the
head of both `origin/main` and `origin/l2-audit-fixes`.

## Build

`scripts/site-bench/bench/xsim/build_ext.sh sts2core` does the steps below
(with rustc 1.96.0, the toolchain the measured binaries were built with) into
the layout the benchmark drivers expect; see `scripts/site-bench/README.md`,
"Reproducing the whole benchmark".

```bash
git clone https://github.com/lain-wood/sts2core && cd sts2core
git checkout 3b2d969d025b9e64bc96b9a15928bd2a0d824bf2
cp <this dir>/xsim_w1_site.rs <this dir>/xsim_w2_toadpoles.rs src/bin/
CARGO_TARGET_DIR=<dir> cargo build --release --bin xsim_w1_site --bin xsim_w2_toadpoles --bin bench
```

The release profile is sts2core's own, from its `Cargo.toml`: `opt-level = 3`,
`lto = true` (fat), `codegen-units = 1` and `panic = "abort"`. The repository sets no
`target-cpu` and has no `.cargo/config`. It uses the system allocator (no
`#[global_allocator]`) and has zero dependencies. The build takes about 10 s here
with rustc 1.96.0. W1 reads `data/encounters.json`, `data/encounters_overrides.json`
and `data/enemy_ids.json`. It finds them through the checkout path compiled into the
binary, and `--data-dir DIR` overrides that path.

## Run

Both harnesses are single-threaded. Pin one core per process. Scale across the whole
machine by launching N pinned processes.

```bash
B=<dir>/release
# W1: identity check without timing (rosters, opening moves, HP, gaps, content counts)
$B/xsim_w1_site --describe
# W1 smoke (44 seeds x 5 fights)
$B/xsim_w1_site --quick
# W1 measured: --policy instance is the comparable one (see below); also run engine
taskset -c 20 $B/xsim_w1_site --seeds 2200 --reps 50 --policy instance
taskset -c 20 $B/xsim_w1_site --seeds 2200 --reps 50 --policy engine
# W1 on a subset (intersection runs): seed s -> list[s % len(list)]
$B/xsim_w1_site --encounters SHRINKER_BEETLE_WEAK,NIBBITS_WEAK --seeds 200 --reps 50

# W2 smoke (20,000 playouts)
$B/xsim_w2_toadpoles --quick
# W2 measured (sansb's defaults, the fixture's A10)
taskset -c 20 $B/xsim_w2_toadpoles --policy engine
taskset -c 20 $B/xsim_w2_toadpoles --policy instance
# W2 as this harness ran before 2026-10-14 (A0)
$B/xsim_w2_toadpoles --policy instance --ascension 0

# sts2core's own bench, as its authors run it (no flags). Its "N threads" leg uses
# std::thread::available_parallelism(), which honours the affinity mask:
taskset -c 20 $B/bench          # 1-thread leg, and the "N threads" leg = 1 thread
taskset -c 0-47 $B/bench        # "N threads" leg = 48 threads (the 1-thread leg is unpinned)
```

The last stdout line of each harness is one JSON object. Every harness also prints
work-identity fields that depend only on the engine and the flags: W1 prints
`fights`, `wins`, `actions` and `digest` (FNV over each fight's length and outcome),
and W2 prints `transitions`, `wins` and `checksum`. Two runs with the same flags
must print identical values. Values from 2026-10-14 (defaults, so W1 runs 2,200
seeds × 50 fights and W2 runs 240,000 playouts with the fixture entry at A10):

| Run | Fights or playouts | Wins | Actions | Actions per fight | Digest or checksum |
|---|---|---|---|---|---|
| W1 `--policy engine` | 110,000 | 23,105 (21.0%) | 2,134,709 | 19.41 | `3e3d440a028383b2` |
| W1 `--policy instance` | 110,000 | 38,496 (35.0%) | 2,471,988 | 22.47 | `2575e7b84eb83af7` |
| W2 `--policy engine` (A10) | 240,000 | 146,562 (61.1%) | 4,117,950 | 17.16 | `3f16caacf0147aa9` |
| W2 `--policy instance` (A10) | 240,000 | 214,314 (89.3%) | 4,434,416 | 18.48 | `08d6095eb398a250` |
| W2 `--policy engine --ascension 0` | 240,000 | 201,179 (83.8%) | 4,525,154 | 18.85 | `cb8c186372394067` |
| W2 `--policy instance --ascension 0` | 240,000 | 235,400 (98.1%) | 4,544,774 | 18.94 | `ac0a5eaa54a11df2` |

The A0 rows are the values this harness printed before it took `--ascension`.

For comparison, spire-mentor's M1 with its run-path monster setup (GH #762) gives
110,000 fights, 35,646 wins (32.4%) and 21.98 actions per fight (digest
`c78f3428460d718b`). Before that fix it gave 35,607 wins and 21.99 (digest
`b00b9c215952899c`, `site_random_bench --legacy-spawn`). The shipped game, run
headless, gives 32% and 21.97. On W2 at A10, uniform over card instances,
spire-mentor's `xsim_toadpoles` wins 88.65% (18.67 actions per playout) and sansb's
`xsim_w2_toadpoles --policy instance` on its fixture as shipped wins 88.25% (19.27).

## Policy: `engine` against `instance`

sts2core's `legal_actions` (`src/step.rs` around line 289) keeps only the first of
several identical hand cards. A hand of four Strikes and a Defend facing two enemies
is therefore 2 + 1 + EndTurn = 4 actions, where spire-mentor and the shipped game
list 4×2 + 1 + 1 = 10. Uniform play over sts2core's set ends the turn about 2.5×
as often. On W2 this alone moves the random player from 98.1% to 83.8%; that is
the 84%-against-99% gap observed on 2026-10-12.

`--policy instance` weights each PlayCard action by the number of identical copies in
hand. That reproduces "uniform over card instances" without touching the engine.
Its aggregates come close to spire-mentor's M1 and the shipped game: 22.5 against
22.0 actions per fight, and 35.0% against 32.4% wins. The per-encounter differences
below show where the remaining win-rate gap sits. The weighting adds a little harness
work per pick. Report both policies.

sansb/sts2-sim also collapses identical cards, and it further collapses identical
targets (`v0.111.0/engine/src/engine/mod.rs` around line 1733).

## Deviations that cannot be removed

- **RNG.** sts2core has its own splitmix64 streams (shuffle, enemy and gen) and
  deliberately does not reproduce the game's RNG (its CLAUDE.md, invariant 4). W1
  rolls enemy HP from sts2core's `[源码]` HP ranges with its own stream. It draws the
  four random rosters (FlyconidNormal, SlimesWeak, RubyRaidersNormal and
  SlitheringStranglerNormal) from sts2core's hand-enumerated weights, with a pick
  derived from the seed. Per-seed rosters, HP and draw order therefore differ from
  spire-mentor; the distributions are meant to match the game's. W2 cannot load
  sansb's RNG words (`--state-seed`, default 7).
- **Opening moves.** sts2core does not roll a random opening move. It takes a fixed
  representative (`step::initial_move`), so the Flyconid always opens with Frail
  Spores and LeafSlimeS always opens with its first move. Both Kin Followers open
  with Quick Slash, where in the game the first one opens with Power Dance
  (`POWER_DANCE_MOVE`, read from the shipped game's start state).
- **Rosters are multisets.** sts2core does not model slot order for the random
  encounters. It records only the multiset.
- **Content differences.** All 22 encounters resolve with no constructor gaps, and
  Burning Blood is modelled. They still play differently: with `--policy instance`,
  random play wins clearly more often on sts2core in CUBEX_CONSTRUCT_NORMAL
  (+21.6 pp), FLYCONID_NORMAL (+15.8), MAWLER_NORMAL (+13.0) and
  SNAPPING_JAXFRUIT_NORMAL (+10.4). The other encounters fall within a few points.
  Only Cubex is explained. The C# `CubexConstruct.AfterAddedToRoom` gives it 13
  block plus 1 Artifact, and its machine loops REPEATER, REPEATER, EXPEL, REPEATER
  and so on. sts2core's table (`src/content.rs` around line 6083, tagged
  `[实测+wiki]`) gives only the Artifact and cycles through five moves:
  Charge, Repeater, Repeater, Expel, then a fifth move, Burrow (15 block), that the
  C# never uses. Its Construct therefore spends one turn in five buffing and one
  blocking instead of attacking.

The files here were copied from the scratch clone where they were built and
smoke-tested. That clone also kept the original 2026-10-12 harness
(`src/bin/xsim_toadpoles.rs`, untracked and not part of this directory);
`xsim_w2_toadpoles --entry shuffled` reproduces it.
