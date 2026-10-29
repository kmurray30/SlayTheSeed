# Gate: Necrobinder `deckmut_it180` (annealed tail) vs `deckmut_it150`

**Pre-registration. Committed before the sweep is launched and before any gate
data exists.**

This is the follow-up **pre-identified in §6 of the it150 gate**
(`docs/experiments/2026-08-15-necrobinder-deckmut-gate.md`, committed
`486658148` before that result existed), so it is not a post-hoc rescue of a
number someone did not like.

---

## 1. Why this leg exists

`--entropy-decay-start 100 --entropy-decay-steps 50` over 150 iterations puts
the entropy coefficient at its floor **exactly at the final iteration**. The
it150 run therefore spent **zero** iterations at the fully-annealed
coefficient, where its own parent `asc_it300` spent 100 (decay at it150, floor
at it200, ran to it300). The playbook's record on this character is that
conversion lags decay onset by ~40 iterations, so it150 plausibly stopped
mid-conversion.

`--iterations 180 --resume-from-iter 150` appends 30 iterations. The LR
schedule anchors to the **absolute** iteration, so declaring 180 steps
`lr_frac` up to 0.4134 at it150 and re-anneals it to 0.300 by it179, while
`ent_c` sits at its 0.0025 floor throughout.

## 2. This leg is *attributable*, unlike its parent

The it150 gate moved three things at once and could license no per-cause
claim. This one is a **true resume** — same optimizer state, same advantage
normaliser, same curriculum, same engine — with the LR/entropy schedule as the
only moving part. That is what made Ironclad's equivalent leg (PR #340,
+6.0pp) a measurement rather than a story.

**The engine identity is enforced, not assumed.** `origin/main` moved during
this campaign (`sts2-mcts` +494 lines, `planning.rs`, `hashing.rs`), so the
tail ran from a worktree pinned to the pre-rebase commit whose venv is
content-addressed to `src_hash 5e8066537e2a5cf9` — the exact engine iterations
0–149 trained on — and `tail.sh` refuses to start if that hash does not match.
Without that, "one variable moving" would have been false and this section
would be worthless.

## 3. Arms

| | |
|---|---|
| Baseline | `~/ppo-necrobinder-deckmut/main.iter_150.pt` — the just-promoted champion |
| Candidate | `~/ppo-necrobinder-deckmut/main.iter_180.pt` |
| Panel | A0–A10 × 200 paired seeds, **42,000,000–42,000,199** (fresh, disjoint) |
| Policy | `ppo.probe --policy net_greedy`, eval-cheap (1000 / 4000 boss), honest search |
| Binary | one build both arms |

The panel is disjoint from 41,000,000+ (the it150 gate) and from every earlier
Necrobinder panel (9000–9199). **No number here may be compared to the it150
gate's 33.14%** — that was a different panel, and cross-panel win rates are
never comparable.

**Gated on `origin/main`'s engine, not the one the nets trained on.** Both arms
share it, so the paired delta is valid; main is the deployment target, and
shipping a net measured only on a superseded engine is the worse failure. This
is disclosed rather than hidden, exactly as the Silent leg's engine shift was.

## 4. Decision rule

> **PROMOTE iff (a)** the pooled delta is > 0 with McNemar exact two-sided
> p < 0.01, **and (b)** no single ascension level regresses by more than
> 5.0pp.

Identical to the it150 rule, and conjunctive. Cells where either arm fails to
complete are dropped from both. Promotion is additionally blocked unless the
ONNX headroom pass clears, with the row set asserted to carry exactly one
character token before any number is read.

**If it fails, `it150` stays.** It is already promoted and already passed its
own gate; there is nothing to roll back.

## 5. Prediction

Magnitude only, per the standing rule.

The training-time signal is **+1.81pp** (it120–149 pooled 0.2823 → it150–179
0.3004 over 2,560 games per block), with `ent` sharpening 0.395 → 0.351 as
intended. **Training win rate is not a gate** — it is measured under
exploration, on the training distribution, without the deployed search — but it
is the only prior available, and it is modest.

So: **positive but small, low single digits, and materially below Ironclad's
+6.0pp.** Ironclad's tail recovered an anneal that had been *entirely absent*
(`lr_frac` pinned at 1.0 for the whole parent run); this one only extends an
anneal that already ran, so there is far less to recover.

**A null is a real possibility and would not be a surprise.** If it lands null,
that is the finding — it says the it150 run had already converted, and the §6
concern was unfounded. Recording that in advance so a null cannot later be
written up as "inconclusive".

No shape predicted.

## 6. Run health

it150 → it180 completed in 7.9 h, **1 error in 7,679 runs** (the whole campaign:
1 in 46,079). Final `lr_frac` **0.30000** against a closed form of 0.30000;
final `ent_c` **0.002500** at the floor. The single error is at it165 and is
not diagnosable from the trainer's aggregate — it records the count, not the
seed.

## 7. Result — **NULL. DO NOT PROMOTE. `it150` stays.**

Swept 2026-08-16 11:00 → 15:09. 2,200 paired cells, 0 unpaired dropped.

| A | `it150` | `it180` | delta | b | c | p |
|---|---|---|---|---|---|---|
| 0 | 49.50% | 44.00% | −5.50 | 36 | 25 | 2.00e-01 |
| 1 | 46.00% | 53.50% | +7.50 | 25 | 40 | 8.17e-02 |
| 2 | 47.00% | 45.50% | −1.50 | 31 | 28 | 7.95e-01 |
| 3 | 42.00% | 40.50% | −1.50 | 32 | 29 | 7.98e-01 |
| 4 | 42.50% | 39.50% | −3.00 | 34 | 28 | 5.26e-01 |
| 5 | 32.50% | 39.00% | +6.50 | 21 | 34 | 1.05e-01 |
| 6 | 38.00% | 34.00% | −4.00 | 31 | 23 | 3.41e-01 |
| 7 | 26.50% | 25.00% | −1.50 | 23 | 20 | 7.61e-01 |
| 8 | 21.50% | 19.00% | −2.50 | 20 | 15 | 5.00e-01 |
| 9 | 6.50% | 6.50% | 0.00 | 7 | 7 | 1.00e+00 |
| 10 | 2.50% | 3.50% | +1.00 | 3 | 5 | 7.27e-01 |
| **pooled** | **32.23%** | **31.82%** | **−0.41** | 263 | 254 | **7.25e-01** |

* **(a)** pooled delta > 0 at p < 0.01 — **FAIL** (−0.41pp, p=0.725)
* **(b)** no level regresses by more than 5.0pp — **FAIL** (A0 at −5.50pp)

Not a marginal miss. `b`=263 against `c`=254 is a coin flip: of 517 seeds where
the two nets disagreed, the candidate won 49.1% of them. Not one of the eleven
levels is individually significant. The two largest cells point in opposite
directions (A1 +7.50, A0 −5.50) and are exactly what n=200 noise looks like.

### This is the outcome §5 registered in advance

Predicted: "positive but small… **a null is a real possibility and would not be
a surprise**", recorded specifically so a null could not later be softened into
"inconclusive". It is not inconclusive. It is a clean null on 2,200 paired
cells.

### The finding

**The it150 run had already converted. §6's concern was wrong**, and it is worth
saying that plainly because §6 was mine and it is what justified spending 7.9 h
of training plus a 4 h sweep. The "zero iterations at the fully-annealed
coefficient" gap was real as an *observation* about the schedule and irrelevant
as a *prediction* about the policy.

**The annealed-tail move does not generalise, and the playbook should say so.**
It is currently recorded on the strength of one instance — Ironclad's PR #340,
+6.0pp, p=4.3e-07 — which reads like a rule. The mechanism is now visibly
narrower than the write-up implies:

| | Ironclad #340 | Necrobinder here |
|---|---|---|
| parent's anneal | **entirely absent** (`lr_frac` pinned 1.0 all run) | ran to completion, `lr_frac` 0.300 |
| what the tail did | supplied a missing anneal | extended one that already finished |
| gated | **+6.0pp** | **−0.41pp (null)** |

So the lesson is *"an annealed tail recovers an anneal that never ran"*, not
*"an annealed tail is worth ~6pp"*. Extending a completed anneal buys nothing.
One instance was never enough to tell those two readings apart; this leg is the
second instance that separates them.

### What it cost, and why it was still worth running

7.9 h training + 4.2 h sweep, and it changes nothing about what ships. But the
alternative was leaving a one-for-one result in the playbook as a general
recipe, where the next four campaigns would each have paid the same 8 h on the
same reasoning. The training-time signal was **+1.81pp** and did not survive a
paired greedy sweep — a reminder, cheap at this price, of why training win rate
is not a gate.

`it180` is **not promoted** and its artifacts are deliberately not committed.
`ppo_fullrun_necrobinder_deckmut_it150` remains the champion on its own
+6.82pp / p=6.09e-10 gate.
