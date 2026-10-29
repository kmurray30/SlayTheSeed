# GH #545 — Ironclad Underdocks + encoder-gap campaign

**Pre-registered before any data exists.** Written on `main` @ `4c651b7c1`
(PR #579, the build this campaign trains).

## What changed under the policy

1. Training can reach the **Underdocks** Act-1 variant (`PpoRunEnv(act1=...)`;
   the campaign trains at `--act1 roll`, ~50/50 seed-deterministic).
2. `cat[10]` — **deck-op kind**. Every deck-mutation surface used to be
   indistinguishable at the card token: a reward, a smith, a remove, an
   upgrade, a transform, an enchant and a grant all reached the net as the
   same shape.
3. `cat[11]` — **enchantment identity**. `pending_deck_enchants()` returned a
   bare count, so Goopy (a downside) and Imbued were the same input. Every
   champion therefore holds an unconditional anti-upgraded-card prior that
   runs backwards on enchant.
4. `reward_upgrade` is ON for this campaign (`STS2_PPO_FEATURES` +
   `--features`), so a card reward's upgraded flag reaches `cat[1]`.

Both new slots are **appends**: `ppo::net`'s `cat_dim` and
`ppo.onnx_policy`'s `n_cat` read the width off the loaded graph and slice, so
all five deployed champions stay bit-identical. That is pinned by
`tests/test_ppo_appended_slots_are_free.py` with literal positional indices
and a frozen-pad canary.

## Baseline and panel

- **Incumbent:** `ppo_fullrun_ironclad_simscale_it200` (promoted #486,
  2026-08-21; post-clairvoyance-flip, so §0.3-comparable).
- **Gate panel:** A0–A10 × 200 **fresh** paired seeds, unspent — NOT the
  `51000000` panel G-b burned, and NOT any panel that has promoted anything.
- **Agent:** `ppo.probe --policy net_greedy`, the same instrument as G-b, so
  the act-difficulty re-anchor is directly comparable.

## Decision rule

> PROMOTE iff, on the `--act1 overgrowth` arm against
> `ppo_fullrun_ironclad_simscale_it200.onnx`, A0–A10 × 200 **fresh** paired
> seeds: **(a)** pooled delta > 0 with McNemar exact two-sided p < 0.01, and
> **(b)** no single level regresses by more than 5.0pp.
>
> The `--act1 underdocks` arm is REPORTED, NOT GATED ON: it has no incumbent
> that ever trained on the act, so a delta there measures OOV boss rows as
> much as it measures the policy.

Neither arm may be compared against an `--act1 roll` run: under `roll` the act
is a function of the seed, so a per-seed diff mixes the act change with
everything downstream of it (`arena.rs:196-206`).

Training win rate is **not** a gate (playbook §2) — it is measured under
exploration, on the training distribution, without the deployed search.

## Recipe

```
--iterations 150 --sims 250 --boss-sims 1000 --games-per-iter 960
--ascension-weights 0:1..10:1   --init-ckpt <incumbent>.pt
--entropy-decay-start 100 --entropy-decay-steps 50 --entropy-coef-final 0.0125
--lr-anneal-final 0.3 --epochs 4 --features reward_upgrade --act1 roll
--save-every 10 --device cuda
STS2_PPO_FEATURES=reward_upgrade  STS2_RUNAI_POTION_SWAP=1  OMP_NUM_THREADS=1
```

`--lr-anneal-final` defaults silently OFF and cost ~6pp once; `--epochs 4`
against a trainer default of 2; `--device cuda` against a `PPOConfig.device`
default of `"cpu"` (~33 s vs ~497 s per update).

## Abort signals

Pre-flight (iteration 1, from `<save>.stats.jsonl`): any zero in
`dec_per_run/card-enchant`, `dec_per_run/potion-swap`, `dec_per_run/event`, or
an `act1_frac/underdocks` nowhere near 0.50. Note the deck-mutation keys are
legitimately ~0.1–0.5/run (playbook §0.1) — the check there is non-zero over
the whole iteration, not "~6.x".

Mid-campaign: KL, clipfrac and explained variance off the rails, or any
`dec_per_run/*` collapsing to zero.

## Amendment (2026-08-26, it17): entropy_coef deviates from the parent

**Recorded after launch, before any gate data exists. The decision rule above
is unchanged.**

The launched command does not set `--entropy-coef`, so it took the trainer
default **0.05**. The parent `ppo_fullrun_ironclad_simscale_it200` trained at
`entropy_coef=0.02`, `entropy_decay_start=40`, `entropy_decay_steps=30`, and
finished **annealed at 0.0125**.

The playbook's "Check the parent before choosing the flag" prescribes exactly
this check — parent annealed ⇒ set the starting bonus explicitly rather than
taking the 0.05 default. It was not performed: the playbook's own template
recipe omits `--entropy-coef`, and the design carried the template verbatim.

Observed under the pinned `ent_c=0.05`:

| it | ent | train win |
|---|---|---|
| 0 | 0.543 | 0.527 |
| 8 | 0.742 | 0.421 |
| 17 | 0.816 | 0.350 |

**Decision: continue as-is** (user, 2026-08-26). The playbook's measured
precedent for this exact mistake (Silent event pilot leg 2: annealed parent
restarted at 0.05, entropy 0.529 → 0.693 peak at it70–79) reads it as "a waste
of iterations, not a run-killer" that self-corrects, and its greedy paired
sweep found only −6.67pp at p=0.48 — the training log overstates the damage
~3×. Budget ~1/3 of the run to re-annealing.

**Consequences for reading this run:**

1. The declining training win rate is **not** evidence of harm. A uniformly
   diffuse policy loses on every ascension at once, which is the same
   fingerprint as real degradation and is why this is easy to misread. Only
   the paired greedy gate decides.
2. The signal to watch is `ent` **turning over**, not win rate. At it17 it is
   still climbing monotonically with no turnover, and already above the Silent
   precedent's peak.
3. If `ent` has not turned over well before it100, the 50-iteration anneal leg
   may not re-sharpen the policy in time, and the gate should be read with
   that in mind rather than as a clean test of the encoder changes.

## Results — FAILED THE GATE (2026-08-27)

**Verdict: not promoted.** The candidate loses to the incumbent by
**−13.27pp** on the gate's own arm, so clause (a) (pooled delta > 0 at
p < 0.01) fails in the wrong direction and clause (b) fails at nine of eleven
levels. No fresh gate panel was spent confirming it: the pre-registered panel
is a scarce resource and a −13pp result at p=4.6e-17 does not need a
tiebreaker.

All numbers below are `ppo.probe --policy net_greedy`, Ironclad, paired by
seed against `ppo_fullrun_ironclad_simscale_it200`, on the **diagnostic**
panels 52000000/53000000/54000000 — all now SPENT and unusable for any gate.

| arm | ckpt | levels | n (paired) | inc | cand | Δ | McNemar exact |
|---|---|---|---|---|---|---|---|
| overgrowth | it10 | A0–A2 | 180 | 86.1% | 81.1% | −5.00pp | p=0.20 (ns) |
| overgrowth | it120 | A0–A10 | 660 | 69.5% | 53.2% | **−16.36pp** | p=1.15e-14 |
| overgrowth | it150 | A0–A10 | 1100 | 70.6% | 57.4% | **−13.27pp** | p=4.6e-17 |
| overgrowth | it150 | A0–A4 | 500 | 88.0% | 72.4% | −15.60pp | p=4.58e-11 |
| underdocks | it150 | A0–A4 | 409 | 79.0% | 70.7% | −8.31pp | p=0.00266 |

### What the diagnostics ruled out

- **Bad initialisation / a broken warm start.** Damage is *progressive*:
  −5.00pp at it10 (ns) → −16.36pp at it120. A structural fault would have been
  full-size at it10.
- **A silent encoder-schema mismatch** — the failure mode this campaign's own
  guard work exists to prevent. Checked directly rather than assumed:
  `NetPolicy` resolved `features` per checkpoint, reading `[]` for the
  incumbent and `['reward_upgrade']` for every candidate. Not the cause.
- **"Entropy is still too high; it just needs the anneal."** The anneal ran to
  completion — `ent_c` 0.05 → 0.0129, `ent` 0.839 (peak, it41) → 0.610 — with
  **zero** greedy recovery (it120 −16.36pp → it150 −13.27pp, overlapping).
  Training win rate ended at 0.410 against 0.503 at it1: it never regained its
  own starting point, let alone the incumbent's.
- **A real capability trade** (worse on Overgrowth, better on Underdocks). The
  Underdocks arm is *less bad* (−8.31pp vs −15.60pp at the same levels) but
  still **negative**: the candidate never beats the incumbent even on the act
  it alone trained on. That is a globally damaged policy, not a trade.

### Root cause: four simultaneous deviations from the parent, not one

The amendment above caught `entropy_coef` and read it against a precedent that
assumed it was the only deviation. It was not:

| knob | parent | this run | ratio |
|---|---|---|---|
| `entropy_coef` | 0.02 | 0.05 | 2.5× |
| `entropy_decay_start` | 40 / 200 (20% in) | 100 / 150 (67% in) | — |
| `entropy_decay_steps` | 30 | 50 | 1.7× |
| `epochs` | 2 | 4 | 2× |

The run held 2.5× the entropy bonus for 2.5× as long, under 2× the gradient
passes per sample. The Silent precedent invoked to justify continuing covered
*one* of these; it was not evidence about this configuration.

`--epochs 4` arrived as a bundled default from
`project_epochs_knob_adopted_bundled`, whose finding was explicitly
"mechanism verified, win rate NOT". A knob adopted on mechanism alone became a
silent default in a campaign recipe, and nothing flagged it as untested.

### Harness finding: a crashed seed is scored as a LOSS

`incumbent_A3` in the Underdocks arm read **8.0%** between neighbours at 75%.
91 of its 100 seeds were `ModuleNotFoundError: No module named 'sts2_rs'` — a
`just sim-engine` rebuild swapped the extension module out from under the
running probe's workers. The harness writes `won: false` on an errored row, so
a corrupted cell reads as a catastrophic win-rate collapse rather than as
missing data.

Scored as written, the Underdocks arm pools to **+4.60pp** and appears to show
the capability trade the campaign hoped for. Dropping errored seeds from the
pairing gives **−8.31pp**. One clobbered cell inverted the sign of the
headline result.

**Rules this earns:** never rebuild the wheel while a probe is running; and
`won: false` in a probe row means "lost" only if `error` is absent — always
filter, never trust the pooled number until you have checked the per-level
rows for one that cannot be a real ascension effect.
