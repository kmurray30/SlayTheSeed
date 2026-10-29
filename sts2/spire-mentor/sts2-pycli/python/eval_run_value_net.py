"""Win-probability go/no-go gate for the run-level **RunValueNet**
(AlphaZero outer-loop plan, Milestone 4, Task 5).

Mirrors `eval_combat_surrogate_net.py` (AZ M1 Task 6): a THIRD independent
forward implementation of `RunValueNet::forward` (Rust,
`sts2-rs/crates/sts2-runai/src/eval/run_value_net.rs`) /
`RunValueMLP.forward` (torch, `train_run_value_net.py`) — written directly
against the exported net JSON in numpy, so it must independently agree with
the other two (see `test_run_value_gate.py::test_numpy_forward_matches_
parity_fixture`). Same standardize -> relu -> heads shape as the surrogate,
minus the encounter embedding (`RunValueNet` has none), plus the act-reached
aux head (`RunValueNet` has one, `SurrogateNet` doesn't).

Gates (task-5-brief.md, provisional — Task 6 may adjust against the first
real fit with rationale), measured on the held-out split:

- Win AUC >= 0.70
- Win Brier <= 0.20
- The WIN head beats a base-rate baseline on Brier (predict the eval split's
  own constant win-rate for every row — a plain scalar, unlike the
  surrogate's per-`(encounter, ascension)` lookup table: a whole RUN has no
  natural per-row bucketing key the way a combat probe has an encounter id).

In addition to the pass/fail gates above, the report prints an
**informational, non-gating** block: act-bucket accuracy (the aux head is
diagnostics-only, mirrors the Rust `act_probs` being `#[cfg(test)]`-only), a
coarse reliability/calibration table (predicted-probability bin -> actual
win rate in that bin), the split's win-rate, and `win_prob` spread
(min/median/max/std). These never affect the exit code.

IMPORTANT — point `--features` at the trainer-emitted `<net>.heldout.jsonl`
(the held-out TEST split `train_run_value_net.py::main` writes next to the
exported net via `emit_heldout_sidecar`), NOT the training `--features`
file. That sidecar carries the ORIGINAL per-floor feature rows verbatim
(same shape as `human_features`'s `features.jsonl`) — it does NOT inline the
win/act labels the way the surrogate's `--labels` rows do, because a
`RunValueNet` label is a per-RUN outcome, not a per-row one. So `--decisions`
must point at a `decisions.jsonl` that still has an entry for every seed
appearing in the heldout split — the ORIGINAL (full) `decisions.jsonl` is
always a safe choice, since the trainer's split only holds out FEATURE rows,
never removes decision rows. Point `--schema` at the matching
`<net>.heldout.jsonl.schema.json` sidecar (a verbatim copy of the training
schema). The `train_run_value_net.load` join (imported below, not
reimplemented) does exactly this labeling: every heldout floor-row inherits
its run's terminal `win`/`act` by `seed`.

    python eval_run_value_net.py \\
        --net       fixtures/run_value_untrained_net.json \\
        --features  fixtures/run_value_tiny.heldout.jsonl \\
        --decisions fixtures/run_value_tiny.decisions.jsonl \\
        --schema    fixtures/run_value_tiny.schema.json

Real invocation (once Task 6 has a trained net):

    cd sts2-pycli/python
    python eval_run_value_net.py \\
        --net       ../../sts2-rs/data/runai/nets/run_value_net.json \\
        --features  ../../sts2-rs/data/runai/nets/run_value_net.heldout.jsonl \\
        --decisions /path/to/decisions.jsonl \\
        --schema    ../../sts2-rs/data/runai/nets/run_value_net.heldout.jsonl.schema.json
"""

from __future__ import annotations

import argparse
import json
import sys
from itertools import pairwise
from pathlib import Path
from typing import Any

import numpy as np
from sklearn.metrics import brier_score_loss, roc_auc_score
from train_combat_surrogate_net import apply_win_calib
from train_run_value_net import N_ACT_BUCKETS, load

# Gate thresholds — provisional, verbatim from task-5-brief.md. Module-level
# constants so Task 6 can adjust with rationale against the first real fit.
WIN_AUC_MIN = 0.70
WIN_BRIER_MAX = 0.20


def run_value_forward(net: dict[str, Any], ctx_dense: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Numpy mirror of `RunValueNet::forward`
    (sts2-rs/crates/sts2-runai/src/eval/run_value_net.rs) — the third
    independent implementation (Rust hand-rolled forward, torch
    `RunValueMLP`, this). Vectorized over rows.

    `ctx_dense` is the RAW densified context (`[n, context_dim]`, BEFORE
    standardization — same convention as `RunValueNet::win_prob_from_dense`
    and the `--emit-parity-fixture` cases). `w1` is stored `[context_dim,
    hidden]` and `w_act` `[hidden, N_ACT_BUCKETS]` (the Rust row-major
    orientation, already transposed by the trainer at export time — see
    `train_run_value_net.py`'s `w1_rows`/`w_act_rows`), so both matmuls
    below need no transpose.
    """
    feat_mu = np.asarray(net["feat_mu"], dtype=np.float64)
    feat_sd = np.asarray(net["feat_sd"], dtype=np.float64)
    w1 = np.asarray(net["w1"], dtype=np.float64)  # [context_dim, hidden]
    b1 = np.asarray(net["b1"], dtype=np.float64)  # [hidden]
    w_val = np.asarray(net["w_val"], dtype=np.float64)  # [hidden]
    b_val = float(net["b_val"])
    w_act = np.asarray(net["w_act"], dtype=np.float64)  # [hidden, N_ACT_BUCKETS]
    b_act = np.asarray(net["b_act"], dtype=np.float64)  # [N_ACT_BUCKETS]

    ctx = np.atleast_2d(np.asarray(ctx_dense, dtype=np.float64))

    # 1. standardize (feat_mu/feat_sd are the net's own, from training).
    xs = (ctx - feat_mu) / feat_sd
    # 2. one hidden ReLU layer.
    h = np.maximum(xs @ w1 + b1, 0.0)
    # 3. win head: logit -> sigmoid -> calibration.
    win_logit = h @ w_val + b_val
    win_prob = 1.0 / (1.0 + np.exp(-win_logit))
    win_prob = apply_win_calib(win_prob, net.get("win_calib"))
    # 4. act head: logits -> softmax (aux, diagnostics-only — never calibrated).
    act_logits = h @ w_act + b_act
    act_logits = act_logits - act_logits.max(axis=1, keepdims=True)
    exp = np.exp(act_logits)
    act_probs = exp / exp.sum(axis=1, keepdims=True)
    return win_prob, act_probs


def _reliability_table(win_prob: np.ndarray, win_true_bin: np.ndarray) -> str:
    """Informational, NON-GATING coarse reliability/calibration table:
    per predicted-probability bin, the mean prediction vs the actual win
    rate observed in that bin. A well-calibrated net has the two columns
    track each other; this never affects the gate's exit code."""
    edges = [0.0, 0.2, 0.4, 0.6, 0.8, 1.0]
    lines = ["  bin              n    mean_pred   actual_win_rate"]
    for lo, hi in pairwise(edges):
        mask = (win_prob >= lo) & (win_prob < hi if hi < 1.0 else win_prob <= hi)
        n = int(mask.sum())
        if n == 0:
            lines.append(f"  [{lo:.1f},{hi:.1f}]      {n:5d}          -                 -")
            continue
        mean_pred = float(win_prob[mask].mean())
        actual = float(win_true_bin[mask].mean())
        lines.append(
            f"  [{lo:.1f},{hi:.1f}]      {n:5d}     {mean_pred:.3f}            {actual:.3f}"
        )
    return "\n".join(lines)


def main() -> None:
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    ap.add_argument("--net", type=Path, required=True, help="RunValueNet weights JSON")
    ap.add_argument(
        "--features", type=Path, required=True, help="held-out feature rows (<net>.heldout.jsonl)"
    )
    ap.add_argument(
        "--decisions",
        type=Path,
        required=True,
        help="decisions.jsonl carrying {seed, act, win} for every heldout seed",
    )
    ap.add_argument("--schema", type=Path, required=True, help="features' .schema.json sidecar")
    args = ap.parse_args()

    net = json.loads(args.net.read_text())
    schema = json.loads(args.schema.read_text())

    # Schema guard — mirrors `RunValueNet::from_json_str`'s refusal to load
    # weights trained against a stale/different feature index space.
    if net["schema_id"] != schema["schema_id"]:
        print(
            f"FAIL: schema_id mismatch — net trained for {net['schema_id']!r}, "
            f"features schema is {schema['schema_id']!r}. Re-export features + retrain."
        )
        sys.exit(1)

    d = load(args.features, args.decisions, schema)
    x_all, y_win_all, y_act_all = d["X"], d["y_win"], d["y_act"]
    n_rows = x_all.shape[0]

    win_prob, act_probs = run_value_forward(net, x_all)
    win_true_bin = (y_win_all >= 0.5).astype(int)  # already binary; same convention as the trainer

    try:
        win_auc = (
            roc_auc_score(win_true_bin, win_prob)
            if len(np.unique(win_true_bin)) > 1
            else float("nan")
        )
    except ValueError:
        win_auc = float("nan")
    win_brier = brier_score_loss(win_true_bin, np.clip(win_prob, 1e-6, 1 - 1e-6))

    # Base-rate baseline: predict the eval split's own constant win-rate for
    # every row (a plain scalar — unlike the surrogate's per-encounter
    # lookup table, a whole RUN has no natural per-row bucketing key).
    corpus_win_rate = float(win_true_bin.mean())
    baseline_pred = np.full(n_rows, corpus_win_rate, dtype=np.float64)
    baseline_brier = brier_score_loss(win_true_bin, np.clip(baseline_pred, 1e-6, 1 - 1e-6))

    checks = [
        (f"win AUC >= {WIN_AUC_MIN}", win_auc >= WIN_AUC_MIN, f"{win_auc:.4f}"),
        (f"win Brier <= {WIN_BRIER_MAX}", win_brier <= WIN_BRIER_MAX, f"{win_brier:.4f}"),
        (
            "win beats base-rate baseline (Brier)",
            win_brier < baseline_brier,
            f"net={win_brier:.4f}  baseline={baseline_brier:.4f}",
        ),
    ]

    print("=== run value net win-prob gate (AZ M4 Task 5) ===")
    print(f"  net      = {args.net}  (schema_id={net['schema_id']})")
    print(f"  features = {args.features}")
    print(f"  decisions= {args.decisions}")
    print(f"  rows evaluated = {n_rows}")
    print()
    ok = True
    for name, passed, detail in checks:
        status = "PASS" if passed else "FAIL"
        if not passed:
            ok = False
        print(f"  [{status}] {name:42s} {detail}")

    # ── non-gating diagnostics ──
    act_pred = act_probs.argmax(axis=1)
    act_acc = float((act_pred == y_act_all).mean())
    wp_min, wp_med, wp_max, wp_std = (
        float(np.min(win_prob)),
        float(np.median(win_prob)),
        float(np.max(win_prob)),
        float(np.std(win_prob)),
    )
    print()
    print("=== informational diagnostics (non-gating) ===")
    print(f"  act-bucket accuracy (aux head, {N_ACT_BUCKETS} buckets)   {act_acc:.4f} (n={n_rows})")
    print(f"  split win-rate                            {corpus_win_rate:.4f}")
    print(
        f"  win_prob min / median / max / std          {wp_min:.3f} / {wp_med:.3f} / {wp_max:.3f} / {wp_std:.3f}"
    )
    print("  reliability table (predicted vs actual, non-gating):")
    print(_reliability_table(win_prob, win_true_bin))

    if not ok:
        print(
            "\nGATE FAILED — run value net is not trustworthy enough to steer the "
            "outer-loop MCTS leaf evaluator. Do NOT deploy this net; iterate on "
            "data/model and re-run."
        )
        sys.exit(1)
    print("\nGATE PASSED.")


if __name__ == "__main__":
    main()
