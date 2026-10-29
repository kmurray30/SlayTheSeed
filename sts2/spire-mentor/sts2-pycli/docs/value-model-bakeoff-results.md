# Value-model bake-off — results

> **STATUS:** the value net this bake-off ranked is no longer served — the
> v0.107.1 retrain measured V-pathing net-negative (+9.2pp with it OFF,
> p=0.0002) and shipped value-OFF; the deployed stack is PPO champions.

Offline only (no game sim). Data split by seed (70/15/15). The setup doc
(`value-model-bakeoff.md`) was later removed — recover it with
`git show 5efc7ba2b:sts2-pycli/docs/value-model-bakeoff.md`.
Win = `act ≥ 4`. Graded target = `act`.

## A0 / release-eval data (`/tmp/human-v`)

103,688 floor-states · 4,271 runs · state-level win-rate 18.7%.

### Graded-act regression (predicted V used as the win-ranking score)

| model | MAE↓ | Spearman↑ | win-AUC↑ |
|---|---|---|---|
| baseline (floor only) | — | 0.050 | 0.522 |
| Ridge (linear) | 0.669 | 0.493 | 0.734 |
| **MLP-256 (incumbent V net)** | 0.643 | 0.519 | 0.725 |
| **LightGBM** | **0.624** | **0.549** | **0.738** |
| XGBoost | 0.627 | 0.548 | 0.734 |
| CatBoost | 0.629 | 0.538 | 0.726 |

The **MLP-256 row is the pre-experiment incumbent** (`train_value.py`'s
architecture), trained/evaluated on the *same* split here — so this is a true
apples-to-apples comparison. It lands **between Ridge and the GBDTs** on
MAE/Spearman and is the **weakest of the regression models on win-AUC** (0.725).
Every GBDT beats it on all three metrics.

Fit times were dominated by CPU contention during the GBDT/classifier sweep
(load ~170 from a concurrent job). On an *idle* box the same fits are fast —
e.g. XGBoost-reg 9 s, MLP 165 s, CatBoost 8 s — so treat earlier timings as
noise, not algorithmic cost.

### Direct win classifiers (binary act≥4)

| model | win-AUC↑ | log-loss↓ | Brier↓ |
|---|---|---|---|
| logistic regression | 0.676 | 0.512 | 0.144 |
| LightGBM | 0.727 | 0.396 | 0.121 |
| CatBoost | 0.720 | 0.398 | 0.121 |
| XGBoost | 0.725 | 0.393 | 0.120 |

(The classifiers' log-loss/Brier are properly calibrated; the regression models'
are not — they predict act-units, min-max'd only for comparability. Use **AUC**
across the two tables.)

### Where the signal lives (LightGBM, floor-bucketed)

| subset | win-AUC | Spearman | n |
|---|---|---|---|
| floor 0–3 | 0.707 | 0.503 | 3,317 |
| floor 4–9 | 0.727 | 0.529 | 6,086 |
| floor 10–19 | 0.763 | 0.569 | 6,455 |
| **1-state/run (earliest floor)** | **0.594** | **0.196** | 640 runs |

## Takeaways

1. **GBDTs give a real but modest quality bump over linear/MLP**: Spearman
   0.49 (Ridge) → 0.55 (LightGBM/XGBoost); MAE 0.669 → 0.624. CatBoost ~ties
   XGBoost with near-zero tuning and the fastest fit.
2. **Win-AUC is essentially flat (~0.73) across every model family** — Ridge,
   GBDTs, and direct classifiers all land 0.73–0.74. *Model family is not the
   bottleneck for win-discrimination.*
3. **Regression-on-graded-act ≥ direct win-classification** for ranking: same
   AUC, and you also get a calibrated act/depth estimate for free. No reason to
   switch the head to a pure binary classifier.
4. **The decorrelated number is the honest one: AUC 0.59 / Spearman 0.20** when
   scoring one earliest-floor state per run. The global 0.73 is inflated by the
   trivial depth↔outcome correlation of pooled per-floor states. This validates
   the label-noise concern: early-game states barely predict the final outcome,
   and *no model family fixes that* — it's a data/label ceiling (→ TD
   bootstrapping / state-subsampling / more skill-diverse data are the levers,
   not the model architecture).

## All-ascensions data (`/tmp/human-v-allasc`)

483,200 floor-states · 19,845 runs · state-level win-rate 28.6%.

### Graded-act regression

| model | MAE↓ | Spearman↑ | win-AUC↑ |
|---|---|---|---|
| baseline (floor only) | — | 0.077 | 0.532 |
| Ridge (linear) | 0.746 | 0.546 | 0.746 |
| **LightGBM** | **0.701** | **0.586** | **0.771** |
| XGBoost | 0.701 | 0.584 | 0.770 |
| CatBoost | 0.704 | 0.583 | 0.768 |

### Direct win classifiers (binary act≥4)

| model | win-AUC↑ | log-loss↓ | Brier↓ |
|---|---|---|---|
| logistic regression | 0.746 | 0.519 | 0.169 |
| **LightGBM** | **0.790** | **0.475** | **0.155** |
| XGBoost | 0.788 | 0.477 | 0.156 |
| CatBoost | 0.786 | 0.481 | 0.158 |

### Where the signal lives (XGBoost, floor-bucketed)

| subset | win-AUC | Spearman | n |
|---|---|---|---|
| floor 0–3 | 0.750 | 0.566 | 15,222 |
| floor 4–9 | 0.762 | 0.582 | 28,037 |
| floor 10–19 | 0.787 | 0.598 | 28,967 |
| **1-state/run (earliest floor)** | **0.695** | **0.315** | 2,976 runs |

The decorrelated number is much healthier than A0's (0.695 vs 0.594) — 8.5× the
runs and far more wins gives the model genuine early-game signal, not just the
depth artifact. Still well below the pooled 0.77, confirming most of the
headline AUC is depth correlation even here.

### Deltas vs A0

- The **GBDT > linear edge holds and widens** with more data: Spearman
  0.546→0.586 (Ridge→LightGBM), win-AUC 0.746→0.771. Same ordering as A0,
  larger margin — consistent with the prior session's finding that the bigger,
  skill-diverse all-ascensions corpus lifts calibration.
- **Direct win-classification now BEATS regression-on-graded-act for win-AUC**
  (LightGBM-win 0.790 vs LightGBM-reg 0.771, +0.019) — the *opposite* of A0,
  where they tied. With enough labels, modeling the binary outcome directly
  pays off; with little data (A0) the graded act target regularizes better.
- **CatBoost** again ≈ matches the field with default hyper-parameters and is
  ~15–20× faster to fit (54–64 s vs 800–1225 s for LightGBM/XGBoost under the
  same load).

## Per-ascension stratification (all-asc model)

`ascension` is scalar index 9 — already a model input. Trained one LightGBM on
all-asc, then held-out win-AUC **stratified by ascension** (`value_ascension_strata.py`):

| ascension | n (test) | human win% | win-AUC | Spearman |
|---|---|---|---|---|
| A0 | 14,852 | 0.162 | 0.729 | 0.575 |
| A1–9 | 46,237 | 0.351 | **0.765** | 0.572 |
| A10–19 | 11,137 | 0.172 | 0.743 | 0.593 |

- The all-asc model is **modestly better at mid-ascensions (A1–9, AUC 0.765)
  than at A0 (0.729)** — it has more, more-balanced data there. At A0 it is **no
  better than the A0-specialised net** (0.729 ≈ the deployed A0 net's 0.726),
  which is exactly why it deploys *worse* at A0 (11.8% vs 14.4%) despite a higher
  *pooled* AUC: the pool is dominated by mid-asc states it predicts well.
- **Caveat:** human win% per ascension is confounded by player-population skill,
  not pure difficulty — A1–8 are experienced grinders (win 34–42%), A0 is mostly
  beginners (19%), A9–10 dip again. So "train per ascension" inherits that
  population's skill in its labels.
- **Verdict on the ascension-matched hypothesis:** plausible and offline-supported
  for higher ascensions, but per the don't-trust-offline lesson it must be
  confirmed by a *per-ascension deployment A/B* (all-asc net vs A0 net deployed
  at A10/A20) — sim required, not done here. Since ascension is already a
  feature, the cleanest test is one all-asc model evaluated by deployment at each
  ascension, vs ascension-specialised nets.

## Per-ascension DEPLOYMENT A/B (the real test of the ascension-matched hypothesis)

Production config (`--policy ranker` + V-pathing, blessed `ranker_full500`),
swept paired-by-seed at fixed ascensions on main-500, A0-net (`value_human_a0`)
vs all-asc-net (`value_human_allasc`). Win rate = full-run wins / 500. Paired
McNemar on seed-matched outcomes. (`scripts/per_ascension_ab.sh` +
`python/per_ascension_ab_analyze.py`.)

| ascension | A0-net | all-asc-net | Δpp (all−A0) | McNemar p |
|---|---|---|---|---|
| A0 | **13.8%** | 11.2% | −2.6 | 0.092 |
| A5 | **9.0%** | 8.0% | −1.0 | 0.51 |
| A10 | 0.0% | 0.0% | 0.0 | — |

**Hypothesis ("all-asc models work better at their respective ascension
levels") — REJECTED by deployment.** The all-asc net never beats the A0 net at
any tested ascension: significantly worse at A0 (replicating the parallel
release-2000 result; p=0.092 here is just main-500's lower power), still behind
at A5, and at A10 the question is moot — **the agent wins 0/500 with either net.**

Two reasons it floors out / doesn't help:
- **A10 is a combat-bottleneck floor**: 0% win regardless of value net. When the
  agent dies in combat ~act 2, the meta/value head is irrelevant — consistent
  with [[project_combat_budget_not_a_fullrun_lever]]. You can't get an
  ascension-matched value-net signal until the agent can actually win at high
  ascension (combat is the prerequisite lever).
- The all-asc net's broader training mass (A1–A10 human play) **does not transfer
  to our agent's state distribution** at any ascension — its offline calibration
  edge never converts (the don't-trust-offline lesson again).

Win rate also falls monotonically with ascension (13.8% → 9.0% → 0%), confirming
the engine's ascension modifiers (swarming/elites A1+, shop inflation A6+, enemy
HP A8+, damage A9+) genuinely bite.

## Don't-trust-offline caveat (from the parallel main-branch run)

A concurrent experiment trained the value net on all-ascensions and found it
**better on every offline metric** (Spearman 0.581 vs 0.520, win-AUC 0.773 vs
0.726) **yet it deployed WORSE at A0** (11.8% vs 14.4%, p<0.001) — classic
distribution shift (its test pool's base-rate 0.285 vs A0's 0.164). The GBDT >
MLP offline edge measured here (+0.01–0.03 AUC) is the *same magnitude* as that
non-converting gap. **So this bake-off identifies GBDT/LightGBM as a cheap
candidate worth a deployment A/B at A0 — it does NOT prove it wins.** Only the
A0 deployment A/B counts.

## GBDT DEPLOYMENT A/B — the real test (2026-06-18): NULL

The LightGBM head was then wired into the Rust runtime and deployed for real —
the only way to know whether the offline edge converts. A `kind:"lightgbm_v1"`
value net (a hand-rolled forest tree-walk, no new deps) now loads through the
SAME `ValueNet` loader as the MLP (`run_value.rs`); a parity test pins the Rust
walk to LightGBM's `booster.predict` (<1e-4). Exported by
`export_lightgbm_value.py`, A0 human-V data, 88 trees (early-stopped).

A/B: ranker + V-pathing (blessed `ranker_full500`), main-500, A0, paired by
seed, `eval-cheap`. **MLP-V (`value_human_a0`) vs GBDT-V (LightGBM).**
(`scripts`→ `/tmp/abgbdt/run.sh` + `python/gbdt_value_ab_analyze.py`.)

| value head | wins / 500 | win% | discordant | McNemar p |
|---|---|---|---|---|
| MLP (incumbent) | 69 | **13.8%** | c=32 (mlp-only) | — |
| LightGBM (GBDT) | 71 | **14.2%** | b=34 (gbdt-only) | **0.90** |

Δ = **+0.4pp, p=0.90 — a clean NULL.** Swapping the MLP for the offline-better
GBDT does not move the deployment win rate; the 34/32 discordant split is noise.

- The MLP arm reproduced the prior per-ascension A/B's A0 number (13.8%)
  **exactly**, confirming the GBDT plumbing left the MLP path bit-for-bit intact
  (same net/seeds/engine, `--no-wall-ms`).
- **This converts the bake-off's offline finding into a deployment fact:** the
  +0.04–0.05 Spearman / +0.02–0.03 AUC GBDT edge is real offline and *worthless*
  in runs — the same non-conversion the all-asc net showed, now demonstrated for
  the model *family* too. Architecture/model-family is confirmed NOT the lever,
  by the only test that counts.

## Overall recommendation

1. **A GBDT is a cheap, real offline upgrade that does NOT convert in
   deployment.** Offline it beats the numpy MLP (+0.04–0.05 Spearman,
   +0.02–0.03 win-AUC); deployed at A0 it ties it (14.2% vs 13.8%, p=0.90 — see
   the GBDT A/B above). So **do not ship the GBDT head for win rate.** It is now
   wired and works (`kind:"lightgbm_v1"`, hand-rolled tree-walk, no native deps
   — LightGBM was the right *family* choice for Rust: pure-Rust inference vs
   CatBoost's `libcatboostmodel` FFI), so it remains a ready substrate if a
   future *label* change makes a better-calibrated head actually matter — but on
   today's labels it buys nothing.
2. **But the win-AUC ceiling (~0.77–0.79) is set by the data, not the model.**
   Every family lands within ~0.02 AUC of every other. Don't expect a model
   swap alone to move full-run win-rate much — consistent with this project's
   repeated "net quality is the lever, architecture isn't" findings.
3. **If you want a bigger lever, attack the label, not the model**: TD/bootstrap
   targets instead of Monte-Carlo final-act returns, one-state-per-run
   subsampling to kill the inflated depth correlation, and more diverse data.
   The decorrelated A0 number (AUC 0.59) shows how much of the headline metric
   is trivial depth signal.
4. **GPU buys nothing here**: data is small for GBDTs (seconds–minutes on CPU
   when uncontended), and these RTX 3090s can't run the pip CUDA builds anyway
   (driver/runtime mismatch). It would only change fit speed, never the metrics.
