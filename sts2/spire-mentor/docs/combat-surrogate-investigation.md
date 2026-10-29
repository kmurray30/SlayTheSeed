# The combat surrogate: why it cannot carry a PPO training loop

**Status: CLOSED, 2026-08-14.** Do not reopen without reading "What was
exhausted" — eight distinct hypotheses were tested and eliminated. The
harvester improvements and the fidelity gate are kept and are useful on their
own; the surrogate *as a combat replacement inside PPO training* is not.

## Why we wanted it

The goal was faster PPO run-policy training. `ppo.screen` answers "will this
change help?" in about an hour instead of a 31-hour campaign, but its
calibration failed: it only resolves effects of roughly 8pp or larger.

The cause is structural and measured. The screen trains on **10,240 runs**
against a campaign's **38,400**, and that reduction is simultaneously the
speedup and the noise. More *eval* seeds cannot fix it — the variance lives in
which seeds get **trained** on. So the only fix is more training runs, more
training runs are only affordable if combat gets cheap, and reducing search
sims is already spent (`ppo/screen.py` runs at `sims: 100` against a campaign's
1000).

That left one candidate for cheap combat: replace the MCTS fight with a net
that predicts its outcome. This document records why that does not work.

## The gate

`python/fidelity_combat_surrogate.py` replays each held-out run's fight
sequence twice — once driven by the K-probe MCTS labels, once by the surrogate
— and compares the HP trajectories.

Three design points make the comparison trustworthy:

* **The non-combat HP delta cancels.** Campfire/event/potion changes are
  *defined* as the residual that makes the reference arm reproduce the run's
  observed `entry_hp` trace, then replayed identically in both arms.
* **Closed loop.** The surrogate is re-queried at its *own* simulated HP, so
  under-prediction feeds back. An open-loop error sum cannot see runaway.
* **Paired sampling.** In `--distributional` mode both arms draw the *same*
  standard normal deviate per fight, so they differ only in (μ, σ).

Run it with `--distributional` for the spread head and `--encoder <champion.pt>`
to add potion identity and transferred embeddings.

## What was exhausted

Every row below is a measurement, not an argument.

| # | Hypothesis | Verdict |
|---|---|---|
| 1 | Labels are too weak (64 vs 1000 sims) | **No.** Strong labels re-rank decks only 0.033 beyond the same-strength noise floor (0.865 vs 0.898). 10× cost for nothing. |
| 2 | Labels are too noisy (K=12) | **No.** Binomial reliability caps any correlation at **0.977**; measured rho was 0.52. |
| 3 | The corpus lacks Acts 2–3 | **Real, and fixed** — see `--driver ppo` below. It changed the *verdict*, not the outcome. |
| 4 | Training data lacks dynamic range | **No.** The synthetic corpus already spans acts 36/35/30 with *wider* outcomes (hp std 0.2568) than the realistic one. |
| 5 | Not enough on-policy data | **No.** Doubling 6k → 12k rows moved MAE 0.0738 → 0.0707 and the risk model **not at all**. |
| 6 | The mean objective throws away the tail | **Partly, but insufficient.** A spread head + sampling closed only **16%** of the survival gap. |
| 7 | Potion identity is invisible | **No detectable effect.** |
| 8 | Card embeddings are under-trained | **No detectable effect.** Champion transfer, zero OOV, dim 192. |

## The finding

On a PPO-driven Acts 1–3 corpus, a surrogate-driven agent ends runs **+27 to
+40 HP too healthy (34–50% of max HP)** and survives at **0.64–0.68 against a
reference 0.29** — roughly 2.3× the survival rate it should have. A run policy
trained in that world would treat the game as far safer than it is, and every
card, path and campfire decision would be calibrated accordingly.

Per-fight accuracy stays *good* throughout (hp MAE 0.071 against a lookup
table's 0.135). That is the trap: **per-fight MAE cannot see this failure.**
Only the compounded, closed-loop trajectory can.

### The mechanism

Death is a tail event, and the tail is missing. The decisive number is the
**across-row spread of the conditional mean**: predicted **0.169–0.171**
against an actual **0.209**. The net cannot separate dangerous fights from safe
ones sharply enough.

Within-row sampling cannot repair that, and the arithmetic says why — it adds
in quadrature:

```
sqrt(0.1631² + 0.0785²) = 0.181   vs the observed sampled std of 0.1777
```

The spread head itself is accurate (predicted 0.0785 against a label 0.0862).
It is layered onto a mean that is already too flat.

## Rules worth keeping

* **Never clear a risk model on a corpus whose fights cannot kill.** The gate
  *passed* on an Act-1 corpus (+3.06 HP end-of-run error) and failed by an
  order of magnitude on Acts 1–3. Act-1 fights are too easy and too saturated
  to expose tail compression — the hypothesis Act-1 rows actively *refuted*
  turned out to be the mechanism.
* **Sampling cannot widen a distribution whose conditional mean is already
  under-dispersed.** Diagnose across-row versus within-row spread *before*
  building a distributional head.
* **Never drive an absorbing boundary from a mean.** `hp_frac_lost` is clamped
  at entry HP, so every "fatal" row has margin exactly 0.0 and "fatal" really
  means *all K probes died*. Sample death from the win head instead.
* **The noise floor is ±0.04 survival product.** An identical config scored
  0.5966 and 0.6406 on two harvests of the same configuration. Any future
  surrogate work must beat that before it means anything.

## What is kept

Independently useful, regardless of the verdict:

* **`--driver ppo` / `--driver-combat`** on `combat_surrogate_data`. The old
  `--realistic` harvest was driven by the deprecated `HeuristicPolicy` with no
  combat search and put **98.3% of its rows in Act 1** (302 in act 2, six in
  act 3) at 6.2 fights per run. Every "realistic" surrogate number published
  before 2026-08-13 describes Act 1 under a policy we do not deploy. The PPO
  driver reaches 44/33/23 across acts at 20.1 fights per run.
* **`hp_frac_lost_std`** — the outcome spread across the K probes, previously
  computed and discarded.
* **Potion identity** on every row, with a bijective index space. The harvester
  randomizes `PotionKind::ALL` into each state while the features emitted only
  `potions.len()`, making potion identity an invisible cause of outcome
  variance.
* **`card_ids` / `relic_ids` / `potion_ids`** in the schema sidecar, so PPO
  champion embeddings can be transferred by name. The two index spaces sort by
  different keys, so a positional copy would silently misalign every card.
* **The fidelity gate itself**, which catches "this combat model is not lethal
  enough" — a failure per-fight metrics are blind to.

## Gotchas

* **`--jobs` does not parallelize `--realistic`.** Runs are driven sequentially
  and the driver's MCTS is single-threaded, so one process pins exactly one
  core (~5 h for 6,000 rows). Shard on disjoint `--seed-start` ranges instead:
  16 shards produce 12,000 rows in about 80 minutes.
* A realistic harvest saturates (mean win_rate 0.96+) through **survivorship**,
  not difficulty — runs that get hurt die and stop emitting rows. Ascension
  barely moves it.
* `ppo.trainer` is deterministic given `(config, seed_base)`, but this
  surrogate trainer is **not** — torch init is unseeded, and two identical runs
  gave 41 vs 53 closed-loop deaths. Replicate before reading a small delta.

## Where fast PPO screening goes next

Not here. The screen stays underpowered at ~8pp, sims reduction is spent, and
combat replacement is closed. A different idea is needed — this document exists
so the next attempt does not re-derive these eight results.
