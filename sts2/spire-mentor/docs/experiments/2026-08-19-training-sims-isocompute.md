# Training-sims iso-compute experiment — pre-registration

2026-08-19. Committed BEFORE any training data exists (champion playbook §2.3).

## Question

During PPO run-policy **training-data collection**, is the campaign default of
1000/4000 combat sims the right point on the quality-vs-quantity frontier? At a
fixed wall-clock collection budget, fewer sims buys proportionally more games
(screen playbook §0: cost falls near-linearly with sims). GH#153 established
1000/4000 as the knee for **eval-time** strength; nothing has measured the
**training-time** trade.

This is NOT a promotion experiment. Nothing trained here may be promoted
(screen playbook §6 applies). The output is a recipe decision for future
campaigns.

## Design: two iso-wall-clock arms

| | Arm A (control) | Arm B |
|---|---|---|
| `--sims` / `--boss-sims` | 1000 / 4000 (default, flags omitted) | 250 / 1000 |
| `--games-per-iter` | 256 | `round(256 × R / 64) × 64` where R = Phase-0 cost ratio |
| everything else | identical | identical |

Shared config, both arms:

```
--character Ironclad --iterations 75
--init-ckpt data/ppo/ppo_fullrun_ironclad_deckmut_it150.pt   (deployed champion)
--ascension-weights "0:1,1:1,2:1,3:1,4:1,5:1,6:1,7:1,8:1,9:1,10:1"
--entropy-coef 0.02 --entropy-decay-start 40 --entropy-decay-steps 30
--entropy-coef-final 0.0125
--lr-anneal-final 0.3
--seed-base 50000000
--save-every 15 --device cuda
```

Rationale for holding **iterations** fixed and scaling **games/iter**: same
number of optimizer updates, same entropy/LR schedule shape — the only moving
variable is the data (its combat quality and its volume). The parent is
annealed (final `ent_c` 0.0127), so `--entropy-coef 0.02` is the deliberate
intermediate restart the champion playbook prescribes; both arms share it.

**R (the cost ratio)** is measured in Phase 0 immediately before launch:
`ppo.probe --policy net` with the deployed champion at 1000/4000 vs 250/1000,
64 seeds each, current engine (post-#467/#469 — the playbook's old 4× figure
is presumed stale). R = (s/run at 1000/4000) ÷ (s/run at 250/1000). The
games-per-iter formula above rounds to a multiple of 64 to keep worker
scheduling sane. Phase-0 numbers are appended to this doc in a follow-up
commit before either arm starts training.

Arms run **sequentially** (each gets the full 46 collection cores), Arm A
first. Save paths `~/ppo-simscale/{a,b}/main`.

## Evaluation — always at the deployed budget

Both arms are evaluated with `ppo.probe --policy net_greedy` at the eval-cheap
default (1000/4000), i.e. the world the policy will actually be deployed into.

1. **Checkpoint curve** (secondary endpoint): checkpoints it15/30/45/60/75,
   A0, paired seeds 41,100,000–41,100,511 (512 seeds), both arms on the same
   panel.
2. **Final** (primary endpoint): it75, full A0–A10 curve, 200 paired seeds per
   level, panel 41,000,000–41,000,199 per level (2,200 paired cells/arm).
   McNemar exact two-sided on discordant pairs, pooled across levels.

Panels are disjoint from the training seed range (50,000,000+), from the
champion's training range (10,000,000+), and from every gate panel recorded in
`docs/experiments/`.

## Pre-registered decision rule

Let Δ = pooled win rate (Arm B − Arm A) at it75 on the 2,200-cell panel.

- **Δ ≥ +3pp and McNemar p < 0.05** → cheap-sims data wins at iso-compute.
  Adopt 250/1000 as the collection default for the next campaign; a 100/300
  arm C becomes worth running.
- **Δ ≤ −3pp and p < 0.05** → quality wins. Close the "fewer sims, more runs"
  idea; 1000/4000 stands.
- **otherwise** → unreadable at k=1 (extrapolated seed-base noise at this
  scale ≈ 1.3pp RMS; 3pp ≈ 2.3× noise). Status quo (1000/4000) stands; a
  seed-base replicate MAY be run but this experiment files no directional
  claim.

Secondary read: consistent-sign dominance across the 5 checkpoint evals
(strength-vs-wall-clock frontier). Reported alongside, never overriding, the
primary rule. Training win rate is a liveness signal only.

Liveness gates before believing anything (champion playbook §0.1): both arms'
first iterations must show `dec_per_run/event` ≈ 6.x and non-zero fine-grained
deck-mutation surfaces; verify `lr_frac` against the closed form on the latest
stats row.

## Known distortions, accepted

- Arm B trains in a weaker combat world; its policy may adapt to weak-pilot
  deck values (screen playbook distortion #1). That is part of what is being
  measured — the eval-at-deployed-budget endpoint prices it in.
- 75 iterations is half a campaign. A crossing beyond it75 is invisible; the
  checkpoint curve is the guard (if B is still rising faster at it75 the
  verdict says so).
- Warm-started refinement regime only. A from-scratch run may weight data
  volume differently; see the liftoff probe below.

## Side probe (observational, no decision rule): from-scratch liftoff

After both arms finish: `ppo.trainer` from random init (no `--init-ckpt`),
250/1000 sims, 512 games/iter, 30 iterations, same curriculum and seed base.
Reports training-WR liftoff (any sustained WR > 0) and `ent` trajectory only.
Context: the 2026-06 from-scratch attempts hit the sparse-win wall at a
~14% incumbent; today's stack self-plays at ~44–49% A0, so the wall may be
gone. This probe prices the "how long would from-scratch take" question; it
decides nothing by itself.

## Phase 0 results (2026-08-19, appended before any training started)

`ppo.probe --policy net`, deployed champion, A0, 64 seeds (42,000,000+),
procs 46, current wheel (post-#467/#469; the stale canonical-venv wheel was
rebuilt via `uv sync --dev` first — it predated the `boss` registry kind):

| sims/boss | mean s/run | speedup | win rate (64 seeds, context only) |
|---|---|---|---|
| 1000/4000 | 83.5 | 1.00× | 71.9% |
| 250/1000 | 22.5 | 3.71× | 60.9% |
| 100/300 | 8.5 | 9.82× | 53.1% |
| 32/100 | 3.8 | 22.0× | 20.3% |

R = 83.5 / 22.5 = **3.71** ⇒ Arm B `--games-per-iter` =
`round(256 × 3.71 / 64) × 64` = **960**. The old playbook curve (18.5 s/run,
4×/12×/33×) is indeed stale in absolute terms; the near-linear shape holds.
The eval-time cliff below 1000 sims (GH#153) also reproduces on 64 seeds.

## RESULT (2026-08-20)

Training: arm A 10.9 h, arm B 10.2 h (B under-consumed its iso-budget by ~7%
— conservative against B), both exit 0, liveness green in both arms
(`dec_per_run/event` 6.92 / 6.58, anneal verified against the closed form,
entropy turned over post-decay in both).

Final it75, A0–A10 × 200 paired seeds, net_greedy at deployed 1000/4000:

| | pooled WR (n=2200) | vs A | vs champ |
|---|---|---|---|
| A (1000/4000 × 256) | 61.82% | — | +0.14pp, p=0.93 |
| B (250/1000 × 960) | 64.32% | **+2.50pp, 329/274 discordant, p=0.028** | +2.64pp, p=0.024 |

Per level: B ≥ A at 8 of 11 (best A6 +9.0pp p=0.027, A4 +7.0pp p=0.076;
worst A9 −3.0pp p=0.50). Checkpoint frontier (A0, 512 paired seeds): B ahead
at **all five** checkpoints — +3.12 / +1.95 / +3.32 / +4.10 / +3.91 pp at
it15/30/45/60/75.

**PRE-REGISTERED VERDICT: UNREADABLE at k=1.** The pooled delta (+2.50pp)
did not clear the pre-registered ±3.0pp magnitude bar, so per the committed
rule the status quo (1000/4000) stands and this experiment files NO
directional claim. That bar was set at ~2.3× the extrapolated seed-base
noise RMS (~1.3pp) precisely so a single seed-base could not manufacture a
recipe change.

Honest annotation of the suggestive (not verdict-bearing) evidence, all
pointing the same way: the pooled sign is positive at p=0.028; the secondary
checkpoint frontier is B-dominant at 5/5 checkpoints; arm A's 75 extra
refinement iterations over the champion bought nothing (+0.14pp, p=0.93)
while arm B's did (+2.64pp, p=0.024); and B was compute-handicapped ~7%.
A single seed-base replicate (~27 h: two arms + eval on a fresh panel,
different `--seed-base`) would settle it: same-sign +≥2pp on the replicate
would make the pooled two-base evidence readable.

## Liftoff probe result (2026-08-20)

**The 2026-06 sparse-win cold-start wall does NOT reproduce on the current
stack.** Random init, 250/1000 sims, 512 games/iter, uniform A0–A10, 30
iterations (56 min wall): training WR 0.2% at it0 rising to ~2% by it28
(monotone-ish, compounding), mean floor 15.4 → 27.5, `ent` 1.313 → 0.901
under the pinned 0.05 coefficient, `ev` healthy (~0.6). The June attempt won
0 games across 205 iterations; today the combat MCTS carries fights well
enough that even a random run policy wins sometimes, so PPO has a gradient
from iteration 0.

What this does NOT establish: time-to-champion-parity. At it28 the acting WR
is ~2% vs the warm-started arms' ~45–55%; parity plausibly needs hundreds of
iterations (the champion lineage embodies ~500+), and collection cost per
iteration grows as runs deepen (54 s → 118 s across the probe). Ballpark at
cheap sims: ≥50–100 h — feasible now, not cheap. A from-scratch campaign is
only worth pricing seriously if the cheap-sims recipe is confirmed by the
seed-base replicate.

## Cost budget

Arm A ≈ 15–16 h (half of Regent's 31 h/150-iter campaign shape), Arm B the
same by construction, evals ≈ 1.5 h, liftoff probe ≈ 2–3 h. Total ≈ 35 h.
