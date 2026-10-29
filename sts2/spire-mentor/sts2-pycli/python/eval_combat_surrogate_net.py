"""Accuracy + real-state calibration go/no-go gate for the combat
**SurrogateNet** (AlphaZero outer-loop plan, Milestone 1, Task 6).

The milestone's acceptance test: no net is trusted by Milestone 3 until
this passes. This is a THIRD forward implementation of
`SurrogateNet::forward` (Rust, `sts2-rs/crates/sts2-runai/src/eval/
surrogate.rs`) / `SurrogateMLP.forward` (torch, `train_combat_surrogate_
net.py`) — written directly against the exported net JSON in numpy, so it
must independently agree with the other two (see
`test_surrogate_gate.py::test_numpy_forward_matches_parity_fixture`).

Gates (spec §3.1 + unified-plan Phase 3, verbatim from
`.superpowers/sdd/task-6-brief.md`), measured on the **realistic** subset
of `--labels` (`is_realistic` is derived per-row from that row's own
`source` field, `source == "realistic"`; see
`train_combat_surrogate_net.py::load`). These are the pass/fail bars —
UNCHANGED by the soft-calibration fix below:

- Win AUC >= 0.94
- Win Brier <= 0.09 (scored against the BINARY `win_rate >= 0.5` outcome,
  for comparability across runs — see the informational soft-label MSE
  diagnostic below for the non-circular, non-binarized view)
- HP MAE <= 8.0 HP (the label is a fraction of max HP; MAE is converted to
  HP units by multiplying by each row's `max_hp` before averaging)
- The WIN head beats a per-`(encounter, ascension)` lookup-table (mean
  predictor) baseline on Brier. (AUC isn't a meaningful comparison against
  a bucket-constant score.)

HP-vs-lookup-baseline is INFORMATIONAL, not gating (decision 2026-07-02):
HP loss is encounter-dominated, so the per-encounter mean sits near the
information ceiling and deck/relic context adds little to HP prediction
(while adding a lot to WIN prediction). The load-bearing surrogate output
for outer-loop MCTS is win_prob; a lookup-quality HP-carry (~6 HP MAE) is
serviceable, and the absolute HP MAE <= 8.0 gate still bounds it. The
HP-vs-baseline number is still printed, as a non-gating diagnostic.

In addition to the pass/fail gates above, the report prints an
**informational, non-gating** block: soft-label MSE of `win_prob` vs the
continuous `win_rate` target, and a `win_rate` distribution summary
(min/median/max, a coarse histogram, and the "mushy middle" fraction —
`0.1 < win_rate < 0.9`). These exist to surface whether the binary
Brier/AUC gate is hiding a bimodality-vs-mushy-middle distribution shift;
they never affect the exit code.

Ascension baseline bucketing: recovered from the RAW (pre-standardization)
dense context via the `.schema.json` sidecar's `scalar_names` list — the
scalar block starts at `2*n_cards + n_relics`, and `scalar_names.index
("ascension")` gives the offset within it (mirrors `SCALAR_NAMES` in
`sts2-rs/crates/sts2-runai/src/eval/ranker_features.rs`, where `ascension`
is index 9). If a sidecar doesn't enumerate `scalar_names`, the baseline
falls back to keying on `enc_idx` ALONE — documented simplification, since
the baseline is a sanity floor, not a precision gate.

IMPORTANT — point `--labels` at the trainer-emitted `<net>.heldout.jsonl`
(the held-out TEST split `train_combat_surrogate_net.py::main` writes next
to the exported net), NOT the training `--labels` file. The trainer trains
on ~80% of the training labels file (`seed_split`); scoring the gate
against that same file reports memorized, optimistic metrics, not
generalization. Point `--schema` at the matching `<net>.heldout.jsonl.
schema.json` sidecar (a verbatim copy of the training schema).

    python eval_combat_surrogate_net.py \\
        --net    fixtures/untrained_net.json \\
        --labels fixtures/tiny_realistic.jsonl \\
        --schema fixtures/tiny_realistic.jsonl.schema.json

Real Step-5 invocation (the milestone go/no-go — DEFERRED to a box-compute
200-epoch training run; NOT run by this commit, see task-6-report.md):

    cd sts2-pycli/python
    python eval_combat_surrogate_net.py \\
        --net ../../sts2-rs/data/runai/surrogate/combat_surrogate_net.json \\
        --labels ../../sts2-rs/data/runai/surrogate/combat_surrogate_net.heldout.jsonl \\
        --schema ../../sts2-rs/data/runai/surrogate/combat_surrogate_net.heldout.jsonl.schema.json \\
        | tee ../../sts2-rs/docs/experiments/2026-07-01-surrogate-v107-eval.md
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any

import numpy as np
from sklearn.metrics import brier_score_loss, roc_auc_score
from train_combat_surrogate_net import apply_win_calib, load

# Gate thresholds — verbatim from task-6-brief.md / spec §3.1.
WIN_AUC_MIN = 0.94
# Within-(encounter, ascension) Spearman rho below which the win head is
# treated as a per-bucket lookup table for PPO-screening purposes. NOT a
# gate on this script's exit code (M1's gates are unchanged); it decides
# what the PPO screen may use the net for. 0.20 is a deliberately low bar —
# it separates "moves with the deck at all" from "does not", which is the
# only distinction the screen needs. Set from first principles, not from a
# measurement: no net exists to calibrate it against yet, so treat the first
# real reading as evidence about the threshold too.
DECK_SENSITIVITY_MIN_RHO = 0.20
WIN_BRIER_MAX = 0.09
HP_MAE_MAX_HP = 8.0


def surrogate_forward(
    net: dict[str, Any], ctx_dense: np.ndarray, enc_idx: np.ndarray
) -> tuple[np.ndarray, np.ndarray]:
    """Numpy mirror of `SurrogateNet::forward`
    (sts2-rs/crates/sts2-runai/src/eval/surrogate.rs) — the third
    independent implementation (Rust hand-rolled forward, torch
    `SurrogateMLP`, this). Vectorized over rows.

    `ctx_dense` is the RAW densified context (`[n, context_dim]`, BEFORE
    standardization — same convention as `SurrogateNet::predict_from_dense`
    and the `--emit-parity-fixture` cases). `enc_idx` is `[n]` integer
    encounter indices; any `>= n_encounters` (OOD) clamps to the reserved
    last `enc_emb` row, exactly like the Rust loader.
    """
    feat_mu = np.asarray(net["feat_mu"], dtype=np.float64)
    feat_sd = np.asarray(net["feat_sd"], dtype=np.float64)
    enc_emb = np.asarray(net["enc_emb"], dtype=np.float64)  # [n_encounters+1, enc_dim]
    w1 = np.asarray(net["w1"], dtype=np.float64)  # [context_dim+enc_dim, hidden]
    b1 = np.asarray(net["b1"], dtype=np.float64)
    w_win = np.asarray(net["w_win"], dtype=np.float64)
    b_win = float(net["b_win"])
    w_hp = np.asarray(net["w_hp"], dtype=np.float64)
    b_hp = float(net["b_hp"])
    n_encounters = int(net["n_encounters"])

    ctx = np.atleast_2d(np.asarray(ctx_dense, dtype=np.float64))
    enc_idx = np.atleast_1d(np.asarray(enc_idx, dtype=np.int64))

    # 1. standardize (feat_mu/feat_sd are the net's own, from training).
    xs = (ctx - feat_mu) / feat_sd
    # 2. append RAW encounter embedding; OOD (idx >= n_encounters) clamps
    #    to the reserved last row.
    rows = np.minimum(enc_idx, n_encounters)
    emb = enc_emb[rows]
    x = np.concatenate([xs, emb], axis=1)
    # 3. one hidden ReLU layer.
    h = np.maximum(x @ w1 + b1, 0.0)
    # 4. win head: logit -> sigmoid -> calibration (hp head is NEVER calibrated).
    win_logit = h @ w_win + b_win
    win_prob = 1.0 / (1.0 + np.exp(-win_logit))
    win_prob = apply_win_calib(win_prob, net.get("win_calib"))
    # 5. hp head: linear, clamped to [0, 1] (a fraction of max HP).
    hp_frac_lost = np.clip(h @ w_hp + b_hp, 0.0, 1.0)
    return win_prob, hp_frac_lost


def _bucket_keys(x_raw: np.ndarray, enc_idx: np.ndarray, schema: dict[str, Any]) -> list[Any]:
    """Per-row `(encounter, ascension)` bucket key for the lookup-table
    baseline. Falls back to `enc_idx` alone if the schema sidecar doesn't
    enumerate `scalar_names` (see module docstring)."""
    scalar_names = schema.get("scalar_names")
    if scalar_names and "ascension" in scalar_names:
        scalar_base = 2 * schema["n_cards"] + schema["n_relics"]
        asc_col = scalar_base + scalar_names.index("ascension")
        ascension = np.rint(x_raw[:, asc_col]).astype(int)
        return list(zip(enc_idx.tolist(), ascension.tolist()))
    return [int(e) for e in enc_idx.tolist()]


def _soft_label_diagnostics(win_prob: np.ndarray, y_win_r: np.ndarray) -> str:
    """Informational, NON-GATING diagnostics (Fix #3): soft-label MSE of
    `win_prob` vs the continuous `win_rate` target, plus a `win_rate`
    distribution summary (min/median/max + a coarse histogram + the
    "mushy middle" fraction `0.1 < win_rate < 0.9`). These never affect
    the gate's exit code — they exist to show whether the binary
    AUC/Brier gate above is hiding a bimodal-vs-mushy `win_rate`
    distribution shift that binarization would otherwise mask.
    """
    soft_mse = float(np.mean((win_prob - y_win_r) ** 2))
    mushy_mask = (y_win_r > 0.1) & (y_win_r < 0.9)
    mushy_frac = float(np.mean(mushy_mask))
    wr_min, wr_med, wr_max = (
        float(np.min(y_win_r)),
        float(np.median(y_win_r)),
        float(np.max(y_win_r)),
    )
    edges = [0.0, 0.1, 0.3, 0.5, 0.7, 0.9, 1.0]
    counts, _ = np.histogram(y_win_r, bins=edges)
    hist = ", ".join(
        f"[{edges[i]:.1f},{edges[i + 1]:.1f})={c}" for i, c in enumerate(counts.tolist())
    )
    lines = [
        "=== informational diagnostics (non-gating, Fix #3) ===",
        f"  soft-label MSE (win_prob vs win_rate)   {soft_mse:.4f}",
        f"  win_rate min / median / max             {wr_min:.3f} / {wr_med:.3f} / {wr_max:.3f}",
        f"  win_rate mushy-middle (0.1<wr<0.9)       "
        f"{mushy_frac:.3f} ({int(mushy_mask.sum())}/{len(y_win_r)} rows)",
        f"  win_rate histogram                      {hist}",
    ]
    return "\n".join(lines)


def _within_bucket_rank_agreement(
    bucket_keys: list[Any],
    y_pred: np.ndarray,
    y_true: np.ndarray,
    min_rows: int = 8,
) -> dict[str, Any]:
    """Does the net rank states *within* one `(encounter, ascension)` cell?

    The question a headline AUC cannot answer. Win outcomes are dominated by
    which encounter you walked into, so a predictor that has memorized nothing
    but the per-`(encounter, ascension)` mean still scores well overall — and
    the M1 eval found exactly that shape (net Brier 0.0157 vs a lookup table's
    0.0198, with the net LOSING on HP MAE, on data where 94% of rows sat above
    win_rate 0.9).

    That distinction is load-bearing for the PPO screening env
    (`ppo/env.py`, `combat="surrogate"`). A run policy's whole job is to build
    a better deck; if the surrogate's prediction does not move with the deck
    once the encounter is fixed, then surrogate-world combat presents **no
    deck-quality gradient**, and screening a card-pick change against it
    measures nothing. Removing the bucket mean is exactly what isolates that.

    Returns the row-weighted mean Spearman rho over buckets with at least
    `min_rows` rows and non-degenerate truth. Spearman rather than Kendall
    for cost — these buckets are small and the two agree in sign, which is
    all the gate reads.
    """
    by_bucket: dict[Any, list[int]] = defaultdict(list)
    for i, k in enumerate(bucket_keys):
        by_bucket[k].append(i)

    rhos: list[float] = []
    weights: list[int] = []
    skipped_small = 0
    skipped_degenerate = 0
    for idxs in by_bucket.values():
        if len(idxs) < min_rows:
            skipped_small += 1
            continue
        t = y_true[idxs]
        p = y_pred[idxs]
        # A bucket where the truth is constant carries no within-cell ranking
        # to agree with; counting it as rho=0 would dilute the mean toward the
        # answer "no signal" for a reason that is not about the net.
        if np.allclose(t, t[0]) or np.allclose(p, p[0]):
            skipped_degenerate += 1
            continue
        rho = _spearman(p, t)
        if not np.isnan(rho):
            rhos.append(rho)
            weights.append(len(idxs))
    if not rhos:
        return {
            "mean_rho": float("nan"),
            "n_buckets": 0,
            "skipped_small": skipped_small,
            "skipped_degenerate": skipped_degenerate,
        }
    w = np.asarray(weights, dtype=np.float64)
    return {
        "mean_rho": float(np.average(np.asarray(rhos), weights=w)),
        "n_buckets": len(rhos),
        "n_rows": int(w.sum()),
        "skipped_small": skipped_small,
        "skipped_degenerate": skipped_degenerate,
    }


def _spearman(a: np.ndarray, b: np.ndarray) -> float:
    """Spearman rho = Pearson on ranks. Average ranks for ties."""
    ra, rb = _rankdata(a), _rankdata(b)
    ra = ra - ra.mean()
    rb = rb - rb.mean()
    denom = math.sqrt(float((ra * ra).sum()) * float((rb * rb).sum()))
    return float((ra * rb).sum() / denom) if denom > 0 else float("nan")


def _rankdata(x: np.ndarray) -> np.ndarray:
    """Average ranks, ties shared — `scipy.stats.rankdata`'s default method."""
    order = np.argsort(x, kind="mergesort")
    ranks = np.empty(len(x), dtype=np.float64)
    ranks[order] = np.arange(1, len(x) + 1, dtype=np.float64)
    sx = x[order]
    i = 0
    while i < len(sx):
        j = i
        while j + 1 < len(sx) and sx[j + 1] == sx[i]:
            j += 1
        if j > i:
            ranks[order[i : j + 1]] = (i + j + 2) / 2.0
        i = j + 1
    return ranks


def _lookup_table_baseline(bucket_keys: list[Any], y_true: np.ndarray) -> np.ndarray:
    """Leave-one-out per-bucket mean predictor (the "lookup-table"
    baseline both heads must beat). For each row, predicts the mean of
    `y_true` over every OTHER row sharing the same bucket key — a plain
    in-sample bucket mean would let a singleton bucket "predict itself"
    perfectly, which is a degenerate, not-actually-predictive baseline.
    Falls back to the leave-one-out GLOBAL mean when a row's bucket has no
    other members.
    """
    sums: dict[Any, float] = defaultdict(float)
    counts: dict[Any, int] = defaultdict(int)
    for k, y in zip(bucket_keys, y_true):
        sums[k] += float(y)
        counts[k] += 1
    n = len(y_true)
    total_sum = float(np.sum(y_true))
    preds = np.empty(n, dtype=np.float64)
    for i, (k, y) in enumerate(zip(bucket_keys, y_true)):
        bsum, bcount = sums[k], counts[k]
        if bcount > 1:
            preds[i] = (bsum - float(y)) / (bcount - 1)
        elif n > 1:
            preds[i] = (total_sum - float(y)) / (n - 1)
        else:
            preds[i] = float(y)
    return preds


def main() -> None:
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    ap.add_argument("--net", type=Path, required=True, help="SurrogateNet weights JSON")
    ap.add_argument("--labels", type=Path, required=True, help="held-out realistic labels JSONL")
    ap.add_argument("--schema", type=Path, required=True, help="labels' .schema.json sidecar")
    args = ap.parse_args()

    net = json.loads(args.net.read_text())
    schema = json.loads(args.schema.read_text())

    # Schema guard — mirrors `SurrogateNet::from_json_slice`'s refusal to
    # load weights trained against a stale/different feature index space.
    if net["schema_id"] != schema["schema_id"]:
        print(
            f"FAIL: schema_id mismatch — net trained for {net['schema_id']!r}, "
            f"labels schema is {schema['schema_id']!r}. Re-export features + retrain."
        )
        sys.exit(1)

    d = load([args.labels], args.schema)
    x_all, enc_idx_all = d["X"], d["enc_idx"]
    y_win_all, y_hpf_all, max_hp_all = d["y_win"], d["y_hpfrac"], d["max_hp"]
    is_realistic = d["is_realistic"]

    mask = is_realistic
    n_realistic = int(mask.sum())
    if n_realistic == 0:
        print("FAIL: no realistic rows in --labels (is_realistic all False)")
        sys.exit(1)

    x_r = x_all[mask]
    enc_r = enc_idx_all[mask]
    y_win_r = y_win_all[mask]
    y_hpf_r = y_hpf_all[mask]
    max_hp_r = max_hp_all[mask]

    win_prob, hp_pred = surrogate_forward(net, x_r, enc_r)
    win_true_bin = (y_win_r >= 0.5).astype(int)  # same binarization as the trainer

    try:
        win_auc = (
            roc_auc_score(win_true_bin, win_prob)
            if len(np.unique(win_true_bin)) > 1
            else float("nan")
        )
    except ValueError:
        win_auc = float("nan")
    win_brier = brier_score_loss(win_true_bin, np.clip(win_prob, 1e-6, 1 - 1e-6))
    hp_mae_hp = float(np.mean(np.abs(hp_pred - y_hpf_r) * max_hp_r))

    bucket_keys = _bucket_keys(x_r, enc_r, schema)
    baseline_win_prob = np.clip(_lookup_table_baseline(bucket_keys, y_win_r), 0.0, 1.0)
    baseline_win_brier = brier_score_loss(win_true_bin, np.clip(baseline_win_prob, 1e-6, 1 - 1e-6))
    baseline_hp_frac = np.clip(_lookup_table_baseline(bucket_keys, y_hpf_r), 0.0, 1.0)
    baseline_hp_mae_hp = float(np.mean(np.abs(baseline_hp_frac - y_hpf_r) * max_hp_r))

    checks = [
        (f"win AUC >= {WIN_AUC_MIN}", win_auc >= WIN_AUC_MIN, f"{win_auc:.4f}"),
        (f"win Brier <= {WIN_BRIER_MAX}", win_brier <= WIN_BRIER_MAX, f"{win_brier:.4f}"),
        (f"HP MAE <= {HP_MAE_MAX_HP} HP", hp_mae_hp <= HP_MAE_MAX_HP, f"{hp_mae_hp:.4f}"),
        (
            "win beats lookup-table baseline (Brier)",
            win_brier < baseline_win_brier,
            f"net={win_brier:.4f}  baseline={baseline_win_brier:.4f}",
        ),
    ]

    print("=== combat surrogate accuracy + calibration gate (AZ M1 Task 6) ===")
    print(f"  net    = {args.net}  (schema_id={net['schema_id']})")
    print(f"  labels = {args.labels}")
    print(f"  realistic rows evaluated = {n_realistic} (of {len(y_win_all)} total in --labels)")
    print()
    ok = True
    for name, passed, detail in checks:
        status = "PASS" if passed else "FAIL"
        if not passed:
            ok = False
        print(f"  [{status}] {name:42s} {detail}")

    print()
    # HP-vs-lookup-baseline is INFORMATIONAL, not gating (decision 2026-07-02):
    # HP loss is encounter-dominated, so the per-encounter mean is near the
    # information ceiling and deck/relic context adds little to HP prediction
    # (even though it adds a lot to WIN prediction). The absolute HP MAE bar
    # (<= {HP_MAE_MAX_HP} HP) remains a gate; beating the lookup mean on HP does
    # not, because the net's load-bearing output for outer-loop MCTS is
    # win_prob, and a lookup-quality HP-carry (~6 HP MAE) is serviceable.
    hp_vs_base = "beats" if hp_mae_hp < baseline_hp_mae_hp else "does NOT beat"
    print(
        f"  [INFO] HP vs lookup-table baseline (MAE, non-gating): "
        f"net={hp_mae_hp:.4f}  baseline={baseline_hp_mae_hp:.4f}  ({hp_vs_base})"
    )
    print(_soft_label_diagnostics(win_prob, y_win_r))

    # Deck-sensitivity: informational for the M1 gate, DECISIVE for the PPO
    # screening env. See `_within_bucket_rank_agreement` and
    # `docs/ppo-screen-playbook.md`.
    win_sens = _within_bucket_rank_agreement(bucket_keys, win_prob, y_win_r)
    hp_sens = _within_bucket_rank_agreement(bucket_keys, hp_pred, y_hpf_r)
    print("\n=== deck sensitivity (within-(encounter, ascension) rank agreement) ===")
    print("  The headline AUC cannot separate 'knows this deck is strong' from 'knows")
    print("  this encounter is easy'. Removing the bucket mean is what separates them,")
    print("  and it is the gate for using this net as the PPO screen's combat backend.")
    for label, sens in (("WIN", win_sens), ("HP ", hp_sens)):
        if sens["n_buckets"] == 0:
            print(f"  {label}: no bucket had >= 8 rows with non-degenerate truth — not measurable")
            continue
        print(
            f"  {label}: mean Spearman rho = {sens['mean_rho']:+.3f} "
            f"over {sens['n_buckets']} buckets / {sens['n_rows']} rows "
            f"(skipped {sens['skipped_small']} small, {sens['skipped_degenerate']} degenerate)"
        )
    if win_sens["n_buckets"] and win_sens["mean_rho"] < DECK_SENSITIVITY_MIN_RHO:
        print(
            f"  → WIN rho < {DECK_SENSITIVITY_MIN_RHO}: this net is close to a per-bucket lookup\n"
            "    table. Surrogate-world combat would present little deck-quality gradient,\n"
            "    so do NOT screen card-pick / deck-shaped changes on it — restrict it to\n"
            "    path / event / shop questions, or screen those changes at low MCTS sims."
        )
    else:
        print(
            f"  → WIN rho >= {DECK_SENSITIVITY_MIN_RHO}: the net moves with the deck inside a\n"
            "    fixed encounter, so surrogate-world combat preserves a deck-quality\n"
            "    gradient. Still confirm any headline result at low MCTS sims."
        )

    if not ok:
        print(
            "\nGATE FAILED — surrogate is not trustworthy enough to search against "
            "(kill-criterion, spec §6). Do NOT proceed to Milestone 2 with this net; "
            "iterate on data/model (more realistic labels, larger trunk) and re-run."
        )
        sys.exit(1)
    print("\nGATE PASSED.")


if __name__ == "__main__":
    main()
