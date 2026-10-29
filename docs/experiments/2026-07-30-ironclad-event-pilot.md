# Ironclad event-learning pilot — GH #302 items 3 & 4

**Status:** baseline measured; 150-iteration pilot running (launched 2026-07-30 03:12).

## Why

PR #317 (main `06adc1c15`) fixed the root cause of #302: `PpoRunEnv._advance`
had been auto-resolving every ordinary event room as `single-option-auto`,
because the event was bound inside `observe()` — downstream of the
`len(actions) == 1` check. The consequence was that **no PPO champion has ever
made an ordinary event choice during training.**

That is not an inference from the code, it is measured on the checkpoints. All
four committed champions' `emb_event` tables agree to four decimal places:

| champion | rows 0–68 norm (mean ± sd) | OOV row 69 | OOV z vs pack |
|---|---|---|---|
| regent_it800 | 13.8057 ± 0.7518 | 13.4614 | −0.46 |
| ironclad_asc_it55 | 13.8047 ± 0.7509 | 13.4246 | −0.51 |
| necrobinder_it750 | 13.8035 ± 0.7523 | 13.5800 | −0.30 |
| silent_it390 | 13.8047 ± 0.7509 | 13.5378 | −0.36 |

Pairwise diffs confirm it: rows 0–68 differ between independently trained
characters by a **uniform** ~4e-4 (a global effect — the trainer's
`--weight-decay 1e-4` acts on rows that receive no gradient), while row 27
(Neow) differs by 0.05–0.09 and row 57 (TinkerTime) by 0.03–0.06. Those are
the only two events that ever surfaced. Uniform-across-all-rows means no
learning; per-row means learning.

**This also killed the "checkpoint surgery" shortcut.** The proposed cheap fix
was to copy the trained OOV row into the 69 random event rows. There is no
trained OOV row — it sits inside the pack (z = −0.3 to −0.5), and collapsing 69
distinct rows into one identical row would be strictly *worse* as a warm-start.

## Event surfacing, before and after (apples-to-apples)

Same character, same trainer, same warm-start checkpoint:

| `dec_per_run/event` | value |
|---|---|
| pre-fix — the champion's own `asc_it55` run, 60 iters (`~/ppo-ascension/main2.stats.jsonl`) | **1.22** (min 1.10, max 1.34) |
| post-fix — same config, 2026-07-30 | **6.66** |

5.5×. At 256 games/iter that is ~1700 event decisions per iteration, ~25 per
event id, so 150 iterations gives ~3700 samples per event row.

## Baseline: event HP bleed, deployed champion

Instrument: `STS2_RUNAI_EVENT_BLEED_LOG` (see
`sts2-runai::policy::MctsCombatPolicy::log_event_choice`), aggregated by
`diag_event_bleed.py`. Deltas come from `preview_option_effect` (#237) — exact,
resolved on a throwaway clone, so SlipperyBridge's escalating per-page cost is
priced correctly instead of parsed from description text. The log records the
meta policy's **raw** pick next to the post-veto one, so "what the net wants"
is separable from "what the run paid".

```
run_arena sweep --seeds 60 --seed-start 1000 --policy ppo --combat eval-cheap
```

Seeds 1000–1059, Ironclad, A0, `ppo_fullrun_ironclad_asc_it55.onnx`.
(Seed range chosen to match the sweep #302 quotes — the issue cites "seed 1027
still bleeds 64→1 HP in SlipperyBridge".)

| metric | value |
|---|---|
| event decisions | 464 (7.73/run) |
| distinct events / unbound | 47 / **0** |
| **chosen HP bleed** | **1203 per 60 runs** |
| realized (post-veto) bleed | 1173 per 60 runs |
| HP gained at events | 75 |
| **lethal-veto fires** | **6** |
| win rate | 35/60 = 58.3% |

Per-run: 49 of 60 runs bleed at all; median 12 HP, mean 20.1, max 95.

The issue quotes ~1.6–1.8k HP/60 runs. 1203 is the same order but not the same
number; the original sweep's exact spec isn't recorded, so treat **1203 as the
baseline of record** and the older figure as superseded rather than reproduced.

### Half the bleed is one event

| event | picks | bleed | vetos |
|---|---|---|---|
| **SlipperyBridge** | **88** | **565** | **6** |
| RoomFullOfCheese | 12 | 140 | 0 |
| ColossalFlower | 21 | 113 | 0 |
| BrainLeech | 14 | 70 | 0 |
| WhisperingHollow | 6 | 54 | 0 |
| TheLegendsWereTrue | 6 | 48 | 0 |
| HungryForMushrooms | 3 | 45 | 0 |

SlipperyBridge appears in only **10 of 60 runs** but accounts for **47% of all
bleed** — 58.8 HP/run in the runs that meet it, against 12.3 HP/run across the
other 50. **84 of its 88 picks (95.5%) were "Hold On"**, matching the ~0.99
prior #302 describes. All 6 veto fires are here.

Seed 1048 is the mechanism in one trace — twelve consecutive Hold On clicks on
floor 40, the escalation plainly visible, the veto catching only the last:

```
floor 40 hp  90 -> opt 1 delta  -3 veto False
floor 40 hp  87 -> opt 1 delta  -4 veto False
floor 40 hp  83 -> opt 1 delta  -5 veto False
floor 40 hp  78 -> opt 1 delta  -6 veto False
floor 40 hp  72 -> opt 1 delta  -7 veto False
floor 40 hp  65 -> opt 1 delta  -8 veto False
floor 40 hp  57 -> opt 1 delta  -9 veto False
floor 40 hp  48 -> opt 1 delta -10 veto False
floor 40 hp  38 -> opt 1 delta -11 veto False
floor 40 hp  27 -> opt 1 delta -12 veto False
floor 40 hp  15 -> opt 1 delta -13 veto False
floor 40 hp   2 -> opt 1 delta  -2 veto True   <-- #300 guard
```

`unbound: 0` and 47 distinct events also confirm the vocab/binding path is
healthy at deploy — #302's item 1 really was fixed before the issue was filed.

## Pilot design

`~/ppo-ironclad-events/run_pilot.sh`, artifacts in `~/ppo-ironclad-events/`.

Warm-start from `ppo_fullrun_ironclad_asc_it55.pt`, 150 iters, 256 games/iter,
**uniform A0–A10 curriculum** — deliberately the same distribution that
produced the champion. An A0-only finetune of an ascension-conditioned net
would improve A0 by specialization alone and confound "did event learning pay?"
with "did A0 specialization pay?". Holding the curriculum fixed leaves the
event fix as the only changed variable, and keeps the result promotable without
re-checking the ascension curve.

Entropy flat at 0.05 for 100 iters (the net must explore options it has never
once selected), then geometric decay to 0.0125 over the last 50.

Engine pinned at main `95fc19f47` in worktree `sts2-sim-ppo-ironclad-events`
(branch `exp/ppo-ironclad-event-pilot`) so the run is isolated from further
main development.

## Success criteria

Sharp and checkable, in priority order:

1. **`emb_event` drift becomes non-zero.** The one thing that was provably
   impossible before. Diff the pilot checkpoint's table against `asc_it55`.
2. **SlipperyBridge Hold-On rate collapses** from 95.5%, and its 565 HP share
   with it. This is where half the prize is.
3. **Veto fires → 0** (from 6). #302's stated acceptance criterion: the guard
   becomes dead code because the policy no longer needs saving from itself.
4. **Chosen bleed materially below 1203**/60 runs.
5. **Win rate** — the actual question. Note 60 seeds is ±~6pp, far too noisy to
   gate on; the promotion decision needs a paired eval at the 200–2000-seed
   scale the earlier campaigns used. 58.3% here is a sanity anchor, not a gate.

Expect a modest WR delta at best: events are ~7% of decisions and the
catastrophic tail is already guarded. A NULL would be in good company with the
Silent ascension finetune and the deck-completion prior.

## Mid-flight result at iteration 60 — events FIXED, win rate NOT

Same 60 seeds (1000–1059), same engine, greedy decode, `it60` exported to ONNX
and swept against the champion. This is the paired read, so engine drift
cancels.

`emb_event` is learning at full-table rates — median row drift vs the champion
0.00338 (it10) → 0.00671 (it30) → 0.00900 (it60), against Regent's `emb_relic`
0.0084 over an entire 580-iteration leg. A stable 48 of 71 rows move; the other
23 are events Ironclad's act pools never serve.

### The event criteria: unambiguous

| SlipperyBridge (met in the same 10 of 60 runs in both arms) | champion | it60 |
|---|---|---|
| picks | 88 | **12** |
| Hold-On rate | **95.5%** | **16.7%** |
| HP bled | 565 | **7** |
| longest chain in one run | 12 | 3 |

| overall | champion | it60 |
|---|---|---|
| chosen HP bleed / 60 runs | 1203 | **493** (−59%) |
| lethal-veto fires | 6 | **0** |
| HP gained at events | 75 | **303** |

#302's acceptance criteria are met: the retrained net picks Overcome without
needing the veto, the guard is dead code in practice (0 fires), and the bleed is
materially below baseline.

### The win rate: did not follow

| | champion | it60 |
|---|---|---|
| WR (60 seeds) | 58.3% | 46.7% |
| paired flips | — | +7 / −14 |
| McNemar exact | — | **p = 0.189 (not significant)** |
| mean final HP | 23.2 | 23.6 |
| mean final act | 2.70 | 2.75 |

**710 HP/60 runs of saved bleed bought no measurable survival.** Final HP moved
+0.4 and final act +0.05. The WR point estimate is −11.6pp but is not
statistically established at n=60.

This corroborates the #301 lethal-fight autopsy, which concluded boss deaths are
a **deck ceiling, not HP debt**. If that holds, removing ~12 HP/run of event
attrition *should not* move win rate — the bleed was real but not load-bearing.
That would make #302's stated premise ("a shared winrate tax") false while
leaving its diagnosis and fix correct.

Behavior did change beyond HP: the pilot spends far more gold at events
(net −1546 vs −11 per 60 runs) and ends with **more** gold (145.2 vs 124.4),
**more relics** (14.3 vs 13.3) and a **smaller deck** (30.2 vs 32.9). So it is
trading gold for relics at events rather than merely hoarding HP.

### Why this is not yet the verdict

The comparison is biased against the pilot in two ways that the remaining 90
iterations are designed to remove:

1. **`it60` is unsharpened.** Entropy is deliberately pinned at 0.05 until
   iteration 100 to force exploration of options the net had never selected;
   decay to 0.0125 runs over the last 50. The champion is a converged,
   entropy-annealed net.
2. **`it60` is unselected.** The champion was picked by a greedy screen over
   checkpoints (its provenance describes exactly this, and warns of winner's
   curse). `it60` is just "wherever the run happened to be".

Pre-registered stopping rule was: kill the run if SlipperyBridge's Hold-On rate
is essentially unmoved at it60. It went 95.5% → 16.7%, so **the run continues**
to let entropy decay convert learned structure into greedy performance.

The eventual gate needs 200–500 paired seeds across several ascensions — n=60
cannot resolve anything smaller than ~15pp.

## ~~Unplanned finding: the deployed champion is STALE~~ — RETRACTED 2026-07-31

> **This section's conclusion is WRONG. It is kept for the record, struck
> through, because the error is instructive.**
>
> The whole gap is the **clairvoyance rent**, not engine drift. Honest combat
> search became the default on **2026-07-12** (`f7f3cbd7c`); the champion was
> blessed **2026-07-10** (`73503d2f0`). Every number in its provenance — and
> every `main2` training number below — was measured with a combat MCTS that
> could see the true draw order. The pilot's numbers are honest.
>
> The rent was already measured, both arms on one binary
> (`sts2-rs/docs/experiments/2026-07-12-clairvoyance-rent-reanchor.md`, G4):
> clairvoyant 38.35% vs honest 15.55% = **−22.80pp**, McNemar p=1.1e-86. That
> doc states the trap outright: *"every pre-2026-07-12 win-rate anchor in
> docs/memory was measured under clairvoyance."*
>
> Both gaps below match that rent and are therefore explained by it:
> - training, it0: 0.569 → 0.297 = **27.2pp**
> - greedy A9: 36.2% → 12.5% = **23.7pp**
>
> The 45 combat commits listed below are real but are not the mechanism. The
> champion is not stale; it was never measured honestly. **Lesson: before
> attributing a cross-date win-rate gap to drift, check whether the honest-search
> flip (2026-07-12) sits between the two measurements.**
>
> What survives unchanged: everything measured *inside* this pilot, since both
> arms ran post-fix on the same honest binary — the bleed 1203→493, the
> SlipperyBridge 95.5%→16.7%, the veto 6→0, and the `emb_event` drift table.
> The "Consequence for this pilot" subsection below still holds, but for a
> different reason (see its own note).

Surfaced while sanity-checking the pilot at iteration 35. The pilot's training
win rate (~0.29) is about **half** what the champion's own finetune run logged
on the identical uniform curriculum (~0.57), and the gap is uniform across all
eleven ascension levels:

| | A0 | A3 | A6 | A9 | A10 | ALL |
|---|---|---|---|---|---|---|
| `main2`, 2026-07-09 engine | 0.724 | 0.716 | 0.648 | 0.223 | 0.081 | **0.571** |
| pilot, today | 0.382 | 0.384 | 0.330 | 0.051 | 0.011 | **0.288** |

This is not the pilot misbehaving. Iteration 0 collects with the warm-started
champion weights *before any gradient step*, so pilot iter 0 ≈ the champion on
today's engine — and `main2` iter 0 scored 0.569 while warm-started from
`it350`, a **weaker** checkpoint than the one the pilot starts from. Same
trainer, same curriculum, same sampled decode.

Confirmed greedy-vs-greedy against the champion's own provenance numbers:

| `asc_it55` at A9, greedy, eval-cheap | WR |
|---|---|
| at blessing (2026-07-10 engine): 80-seed screen / 200-seed paired | **36.2% / 36.0%** |
| today (main `95fc19f47`), 80 seeds 2000–2079 | **12.5%** (10/80) |

z ≈ 3.5, p ≈ 0.0004 (unpaired — the seed panels differ). Average final act
1.81, average final HP 5.5: it dies in act 2. One run returned
`empty_actions`, which is its own small anomaly worth a look.

**Not a broken artifact.** `tests/test_ppo_rust_parity.py` and
`tests/test_ppo_onnx_export.py` both pass, so the #315 A_MAX 96→160 re-export
at HEAD really is numerically inert and the ONNX matches torch.

**Likely mechanism: the combat partner moved.** 45 commits have touched
`sts2-content`/`sts2-core` since blessing. The run policy was trained against a
specific combat cell (MCTS + wide Texel leaf), and that leaf has been **retuned
at least four times** since: `b3fb84591`/`ac6a029cd` (Regent columns +
192-dim retune), `d3031e3f1` (boss-aware retune, 166k corpus),
`3bfd544c0`/`45994c430` (Necrobinder columns + 24-col fit). Add HP-repricing
changes (`d13ab4355`, `8443f0078`) and monster fixes (`1fd06e876` TestSubject
escalation). Events also got more expensive independently — `664e517a0` made
ColossalFlower a multi-page dig chain (the #2 bleed event in the baseline
above) and SlipperyBridge became the faithful multi-page escalation.

### Consequence for this pilot

**Revised 2026-07-31 after the retraction above.**

The **gate stays valid**, and for a stronger reason than originally written: it
is a paired A/B of pilot vs champion on the same honest binary and the same
seeds, so both the clairvoyance flip and any engine movement cancel. The
originally-stated confound — "any WR gain is partly re-adaptation to a drifted
engine" — was premised on the retracted staleness claim and is **weaker than
stated**, though not zero (45 combat commits did land since blessing; they just
aren't what produced the 23–27pp gaps).

The residual confound that *does* survive: the champion never had a chance to
adapt to honest combat, while the pilot trains under it for 150 iterations. A
pilot WR gain could therefore be honest-search adaptation rather than event
learning. That is separable in principle — finetune the champion under honest
search *without* the event fix as a third arm — but was not run here. So:

- Treat the **event-specific criteria as the pilot's verdict** — SlipperyBridge
  Hold-On rate, its 565 HP share, veto fires, total bleed. Those are
  attributable to event behavior regardless of what WR does.
- Treat the **WR delta as suggestive only**, and say so in any writeup.

### Consequence beyond this pilot (bigger than #302)

A deployed champion at ~1/3 of its blessed A9 win rate is a production issue in
its own right, independent of events. Worth a separate issue: re-measure every
committed champion against current main and re-bless. Regent (`it800`,
2026-07-26) and Necrobinder (`it750`, 2026-07-28) postdate most of the leaf
retunes and should be far less affected; Ironclad `asc_it55` (07-10), Silent
`it390` (07-10) and Defect `asc_it80` (07-12) all predate them.

## Not covered

The **real-DLL arm**. #302 item 4 asks for both arms; the real-DLL arm needs
the live game via SimBridge and cannot run headlessly here. The issue records
the bleed as symmetric across arms and not part of the arm gap, so the engine
arm sizes the prize — but the paired-arm confirmation needs the machine that
runs the game.

---

# GATE — 2026-07-31, pilot complete at 150/150 (0 errors)

Harness: `sts2-pycli/experiments/ironclad-event-pilot/gate.sh`. All arms run on
one honest binary from the `feat/event-bleed-probe` checkout (base
`95fc19f47`, sole engine delta = the env-gated probe). Raw artifacts archived
alongside it.

## 1. Event criteria — PASS, decisively

Same 60 seeds (1000–1059) as the baseline, 410 logged event decisions.

| per 60 runs | champion | pilot | |
|---|---|---|---|
| chosen HP bleed | 1203 | **640** | −47% |
| realized HP bleed | 1173 | 640 | |
| HP **gained** at events | 75 | **270** | 3.6× |
| lethal-veto fires | 6 | **0** | |
| distinct events / unbound | 47 / 0 | 47 / 0 | |

`SlipperyBridge`, the #300 pathology and 47% of all champion bleed: **565 HP
from 88 picks → 21 HP from 18 picks** (6.4 → 1.2 HP per pick). Now the #9 bleed
event at 3% of the total.

The veto number is the cleanest signal. The champion's guard had to *intervene*
(chosen 1203 vs realized 1173 = 30 HP saved); the pilot's chosen and realized
are identical, so it never approached a lethal event choice. #302's stated
acceptance criterion is met.

**Not blanket HP-aversion — reallocation.** `RoomFullOfCheese` bleed rose
140 → 196 (now the top bleed event) and `RoundTeaParty` 44 → 77, while HP gained
tripled. A policy that had merely learned "HP loss bad" would have pushed every
row down. This is per-event value, matching the embedding evidence in §3.

## 2. Win rate — +15.7pp, p=1e-4, but NOT attributable to events

300 paired seeds (5000–5299), A0, greedy, eval-cheap.

| | champion | pilot |
|---|---|---|
| win rate | 149/300 = 49.7% | 196/300 = **65.3%** |
| flips | — | +92 / −45 |
| McNemar exact two-sided | — | **p = 0.0001** |
| mean final HP | 20.0 | 33.3 |
| mean deck size | 34.3 | **30.1** |
| mean relics | 13.6 | 15.2 |
| reached act 3 | 222 | 254 |
| died in act 1 | 22 | **7** |

**The prediction recorded at it60 ("events fixed, WR will not follow") was
wrong.** it60 was measured mid-exploration with entropy pinned at 0.05; the
conversion happened entirely during the it100–150 decay. Mid-flight readings of
a deliberately-exploratory phase are not evidence about the final policy.

**The gain cannot be credited to event learning.** The champion trained under
*clairvoyant* combat (pre-2026-07-12) and never adapted to honest search; the
pilot got 150 iterations of it. The measured rent is 22.8pp — same order as this
gain. Two mechanism checks point away from events:

- deck size fell 34.3 → 30.1, and **−3.0 among losses alone**, so it is not
  winner survivorship. That is deck-building — the #301 "deck ceiling" lever,
  unrelated to events.
- mean final HP rose +13.3 while the event fix saved only 9.4 HP/run.

Separating the two needs a third arm — finetune the champion under honest search
with the event fix reverted — which was **not run**. Until then: *the pilot is
significantly better than the deployed champion; the cause is not isolated.*

## 3. Embedding forensics — the table is alive

Median row drift vs the `asc_it55` warm-start:

```
emb_card    0.05147  491/582      emb_event   0.06755   48/71
emb_relic   0.06908  250/299      emb_potion  0.07296   50/65
emb_upg     0.00000    2/18  <- negative control
```

`emb_event` now sits alongside `emb_relic`/`emb_potion` — the tables that always
trained — against **exactly 0.000000** across Regent's entire pre-fix 580-iter
leg. 23/71 rows remain at ~0 (events this curriculum never hit) plus the OOV row
at exactly 0, so this is per-row gradient, not uniform weight decay.

## Verdict

- #302 items 3+4 (Ironclad): **DONE**. Events are learned; the bleed is halved;
  the #300 guard is dead code in practice.
- Promotion: **not on this evidence alone.** A +15.7pp paired A0 result is
  promotion-grade in size, but the champion is an unfair comparator (never
  adapted to honest search). Re-anchor against an honestly-finetuned baseline,
  and run the full release-2000 gate before touching the champion resolvers.
- Known bias, self-inflicted: the pilot ran **without** `--lr-anneal-final`
  (default 1.0 = off), unlike the recipe that produced the champions. It is
  therefore under-converged relative to its own recipe — which makes the +15.7pp
  a floor, not a ceiling.

---

# PROMOTION DECISION RULE — pre-registered 2026-07-31, before the curve landed

Written and committed while `asc_gate.sh` was still running, so the call cannot
be rationalised after seeing the data. Harness:
`sts2-pycli/experiments/ironclad-event-pilot/asc_gate.sh` — A0–A10, 200 paired
seeds per level (panel 7000–7199), both arms on one honest binary.

**Promote if no level regresses beyond noise.**

- A0–A8: expect gains. A regression here is a veto.
- **A9/A10 are underpowered at n=200.** At the champion's ~2–7% win rate the
  resolvable difference is ~5pp, so these are *"no significant regression"*
  tests, not *"must improve"* tests. A level down 1–2pp is noise, not a veto.
- One level down within noise does not block; two or more levels down, or any
  level down beyond noise, does.

**Attribution is explicitly NOT a promotion criterion.** The +15.7pp may be
honest-search re-adaptation rather than event learning (see the gate section).
That distinction matters for what #302 may claim; it does not matter for whether
to serve the better net. The champion is the incumbent, both arms were measured
on today's honest binary, and the better one ships.

**Rationale for measuring at all,** given the pilot trained on uniform A0–A10:
training on a distribution does not guarantee per-level improvement. The pilot's
own logs (first 25 iters vs last 25, ~580 runs/level/block, sampled decode) show
gains at nine of eleven levels but **A9 +0.02 and A10 −0.01** — the two levels an
A0-only result cannot speak to, and the two people care most about.

| | A0 | A1 | A2 | A3 | A4 | A5 | A6 | A7 | A8 | A9 | A10 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| first 25 | .40 | .45 | .43 | .40 | .39 | .31 | .33 | .25 | .16 | .05 | .02 |
| last 25 | .46 | .50 | .44 | .43 | .46 | .37 | .37 | .31 | .21 | .07 | .01 |
| delta | +.06 | +.05 | +.01 | +.04 | +.07 | +.07 | +.04 | +.06 | +.05 | +.02 | −.01 |

Note the greedy gate gain at A0 (+15.7pp) is ~2.6× the sampled training gain
(+6pp). Sharpening pays off fully only under argmax decode — which is also why
the it60 mid-flight read was misleading.

---

# ASCENSION CURVE + PROMOTION — 2026-07-31

Decided under the rule pre-registered in the section above, before any curve
data existed. 200 paired seeds per level, panel 7000–7199, greedy, eval-cheap,
both arms on one honest binary.

| A | `asc_it55` | pilot | delta | flips | McNemar p |
|---|---|---|---|---|---|
| 0 | 49.5% | 61.5% | **+12.0pp** | +51/−27 | 0.0088 |
| 1 | 50.0% | 67.0% | **+17.0pp** | +60/−26 | 0.0003 |
| 2 | 49.0% | 64.0% | **+15.0pp** | +59/−29 | 0.0018 |
| 3 | 41.0% | 58.5% | **+17.5pp** | +54/−19 | 0.0001 |
| 4 | 36.5% | 56.5% | **+20.0pp** | +70/−30 | 0.0001 |
| 5 | 34.5% | 55.0% | **+20.5pp** | +61/−20 | <0.0001 |
| 6 | 38.5% | 54.0% | **+15.5pp** | +59/−28 | 0.0012 |
| 7 | 27.5% | 49.5% | **+22.0pp** | +64/−20 | <0.0001 |
| 8 | 16.5% | 38.0% | **+21.5pp** | +59/−16 | <0.0001 |
| 9 | 4.5% | 12.5% | **+8.0pp** | +23/−7 | 0.0052 |
| 10 | 0.5% | 2.5% | +2.0pp | +5/−1 | 0.22 (n.s.) |

**Pooled 696/2200 = 31.6% → 1038/2200 = 47.2%, +15.5pp.**

**Zero regressions at any level.** Ten of eleven individually significant,
including A9 (p=0.005) which the pre-registration had written off as
unresolvable. A10 is the only non-significant level and is still directionally
positive — exactly the case the rule anticipated.

The gain is *largest* in the A4–A8 band (+20 to +22pp), which is the opposite
of the signature an A0-specialisation artifact would leave. A0 also replicated
independently on the disjoint 5000–5299 panel (+15.7pp there vs +12.0pp here;
the champion scored 49.7% and 49.5% on the two panels, so they are comparable
and the original was not a lucky draw). Honest point estimate: low-to-mid teens.

## Promoted

`ppo_fullrun_ironclad_asc_it150` is the deployed Ironclad champion. Both
resolvers updated, parity fixtures regenerated (the fixture now covers the
`event` surface, which it structurally could not before #317), full swap
procedure from `sts2-rs/docs/ppo-rust-inference.md` run end-to-end. All gates
green: `check` / `clippy -D warnings` / `fmt` / **nextest 6383 passed** / the
two pytest parity gates.

Attribution is unchanged and unclaimed — see the gate section. The provenance
file carries the clairvoyance warning so the next reader does not compare
against `asc_it55`'s pre-2026-07-12 figures.

---

# Follow-up leg — LR anneal (running, launched 2026-07-31 23:16)

## Why

The pilot omitted `--lr-anneal-final`. The trainer default is `1.0`, which is
*off* (`trainer.py:350`), so all 150 iterations ran at full LR — unlike the
recipe behind `asc_it55` and every other champion, which annealed to `0.3`.
This was an oversight, not a design choice. It is why the promotion writeup
calls +15.5pp a **floor** rather than a point estimate.

This leg asks the narrow question: **did the missing annealed tail cost
anything?**

## Design — LR is the only variable

A **true resume**, not a warm start. `main.iter_150.pt` carries optimizer state
*and* the advantage-normaliser moments (`adv_mean`/`adv_sq`), so
`--resume-from-iter 150` continues the run. A `--init-ckpt` start would have
reset Adam and confounded "annealed LR helped" with "resetting the optimizer
helped".

`apply_lr_schedule` anchors to the **absolute** iteration
(`t = it/(iterations-1)`), so declaring `--iterations 180 --lr-anneal-final 0.3`
makes the LR step to frac `0.4134` at it150 and decay to `0.300` by it179 —
exactly the tail the pilot never got.

Entropy is already pinned at its floor: with the pilot's own decay flags any
`it >= 150` gives `t=1.0` => coef `0.0125` flat. Character, curriculum (uniform
A0–A10) and games/iter are held identical for the same reason. The promoted
checkpoint was copied into a fresh directory so the campaign artifacts stay
untouched.

First annealed iteration confirms the schedule engaged rather than silently
no-op'ing — the failure mode worth checking, since `lr_anneal_final >= 1.0`
returns early and leaves no trace in the log:

```
iteration 150  win_rate 0.375  entropy_coef 0.0125
lr_frac 0.4134  policy_lr 1.24e-05  dec_per_run/event 6.19  errors 0
```

`lr_frac` is 0.4134 and not 1.0, and event engagement (6.19 decisions/run)
survived the resume.

## Pre-registered decision rule — fixed before any data exists

Baseline is **`asc_it150`, not `asc_it55`**. Re-comparing to `asc_it55` would
re-bank a gain already claimed and blessed, and it is a clairvoyance-era net
besides (see the retraction above).

> **PROMOTE** `polish` over `asc_it150` iff **both**:
> (a) pooled A0–A10 delta > 0 with McNemar p < 0.01, **and**
> (b) no single level regresses by more than 5.0pp.
>
> Otherwise **KEEP** `asc_it150` and record a null.

Full curve in one shot on a **fresh panel 8000–8199** (disjoint from the
promotion curve's 7000–7199 and the A0 replication's 5000–5299). No cheap A0
screen first: a screen that gates the curve is a selection filter, and with an
effect this small that bias is exactly where it would bite.

Training-time win rate is **not** a criterion. The pilot taught that lesson in
the other direction — I called the run dead off its deliberately-exploratory
phase and it went on to move +15.7pp. Arena or nothing.

A null is the expected and perfectly good outcome: it converts "+15.5pp is a
floor" into "+15.5pp is the number, the omission was harmless", and closes the
campaign's one self-inflicted gap either way.

Harness: `polish_gate.sh` + `aggregate.py`. The aggregator was validated by
re-deriving the promotion curve above cell-for-cell (pooled p=4.87e-35) before
being pointed at new data.

## Outcome — the anneal was worth ~6pp, and my expectation was wrong

The leg completed 180/180 with 0 errors. `lr_frac` landed exactly on 0.300,
`policy_lr` 3e-05 → 9e-06, entropy pinned at 0.0125 throughout, event
engagement steady at ~6.2 decisions/run across the resume.

| A | `asc_it150` | `asc_it180` | delta | p |
|---|---|---|---|---|
| 0 | 59.0% | 63.5% | +4.5pp | 0.37 |
| 1 | 60.5% | 73.5% | **+13.0pp** | 0.0029 |
| 2 | 67.0% | 69.5% | +2.5pp | 0.61 |
| 3 | 54.0% | 66.0% | **+12.0pp** | 0.0043 |
| 4 | 58.5% | 60.0% | +1.5pp | 0.80 |
| 5 | 50.0% | 55.0% | +5.0pp | 0.31 |
| 6 | 42.0% | 56.5% | **+14.5pp** | 0.0006 |
| 7 | 41.5% | 47.0% | +5.5pp | 0.22 |
| 8 | 29.0% | 32.5% | +3.5pp | 0.44 |
| 9 | 11.5% | 13.0% | +1.5pp | 0.75 |
| 10 | 1.5% | 3.5% | +2.0pp | 0.22 |

**Pooled 43.1% → 49.1%, +6.0pp, p=4.26e-07.** Both pre-registered criteria
met: pooled delta positive well inside p<0.01, and **no level regresses at
all**, let alone by 5pp. Promoted.

**I expected a null and said so twice.** The honest reading is that the
omitted flag cost about 6pp, and that the `asc_it150` writeup's "Known bias"
section was right to call +15.5pp a floor rather than hedging. The full gap
from `asc_it55` to a properly-annealed event-trained net is larger than what
was originally banked.

Read the pooled number, not the per-level pattern. Three levels (A1/A3/A6)
carry most of the significance, but nothing in the setup would produce a gain
at those levels specifically — it looks like noise around a broadly uniform
gain, and treating it as an A1/A3/A6 story would be reading structure into
eleven n=200 cells.

### Unlike the pilot, this gain IS cleanly attributable

The pilot's +15.5pp is still unattributed, because `asc_it55` never adapted to
honest search and the clairvoyance rent is the same order as the gain. This
leg has no such confound: a true resume of the very net it is measured
against, one binary, one panel, paired, LR the only moving part. The +6.0pp is
caused by the annealed tail.

### Cross-panel note

`asc_it150` scores 43.1% on panel 8000–8199 vs 47.2% on panel 7000–7199. The
weights were confirmed bit-identical (0 differing tensors; the md5 gap is ONNX
export metadata), so that is seed-panel variance, ~2.9σ across independent
panels. Neither comparison is affected — each curve is paired inside one panel
on one binary — but it is a standing reminder not to read win rates across
panels, only deltas within them.
