"""Evaluate the (synthetic-trained) combat surrogate on the **realistic**
self-play distribution, for the two questions that actually matter:

1. **Generalization** — win/HP accuracy on real self-play pre-combat states
   (`encounter_delta` full-fight truth), vs the synthetic held-out numbers.

2. **Decision agreement** — at each realistic card-shaped decision, does the
   surrogate *rank the candidate options* like the ground truth? Reports mean
   **Kendall τ** (the E3 calibration metric), **top-1 agreement**, and
   **regret** (ground-truth-score gap between the surrogate's pick and the true
   best). Measured against TWO ground truths:
     * **teacher** = `fight_delta().score()` — the exact score the run-AI's
       boss-sim card chooser used (truncated horizon, includes a damage-tempo
       term the surrogate has no head for). This is the "can it replace that
       score" test.
     * **ideal**   = the same win/HP blend the surrogate predicts, but on TRUE
       full-fight `encounter_delta` outcomes. This isolates model quality from
       target mismatch.

    python eval_combat_surrogate.py \\
        --eval  ../sts2-rs/data/runai/surrogate/eval_realistic.jsonl \\
        --model ../sts2-rs/data/runai/surrogate/combat_surrogate.joblib
"""

from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path

import numpy as np
from scipy.stats import kendalltau
from sklearn.metrics import roc_auc_score

HP_W = 0.004  # mirrors rollout::DeltaStats::score hp coefficient


def densify_one(feats, schema):
    n_cards, n_relics = schema["n_cards"], schema["n_relics"]
    ctx_dim = schema["context_dim"]
    relic_base, scalar_base = 2 * n_cards, 2 * n_cards + n_relics
    x = np.zeros(ctx_dim + len(schema["encounters"]), dtype=np.float32)
    for idx, c, u in feats["deck"]:
        x[2 * idx], x[2 * idx + 1] = c, u
    for idx, c in feats["relics"]:
        x[relic_base + idx] = c
    sc = feats["scalars"]
    x[scalar_base : scalar_base + len(sc)] = sc
    return x, ctx_dim


def blend(win, hp_frac, max_hp):
    """fight_delta-like scalar from a win prob + HP-loss fraction (full-fight:
    loss_rate ≈ 1-win → win_rate-loss_rate ≈ 2*win-1)."""
    return (2.0 * win - 1.0) - HP_W * (hp_frac * max_hp)


def rank_report(name, pairs):
    """pairs: list of (surrogate_scores, gt_scores) per decision."""
    taus, top1, regrets = [], [], []
    for sg, gt in pairs:
        sg, gt = np.asarray(sg), np.asarray(gt)
        if len(sg) < 2 or np.allclose(gt, gt[0]) or np.allclose(sg, sg[0]):
            continue
        t, _ = kendalltau(sg, gt)
        if t is not None and not np.isnan(t):
            taus.append(t)
        gb, sp = int(np.argmax(gt)), int(np.argmax(sg))
        top1.append(1.0 if gb == sp else 0.0)
        regrets.append(float(gt[gb] - gt[sp]))
    reg = np.array(regrets) if regrets else np.array([0.0])
    print(f"  vs {name}:")
    print(f"    rankable decisions   : {len(taus)}")
    print(f"    mean Kendall τ       : {np.mean(taus):+.3f}")
    print(f"    top-1 agreement      : {np.mean(top1):.1%}")
    print(f"    mean / median regret : {np.mean(reg):.4f} / {np.median(reg):.4f}")
    print(f"    pick within 0.02 best: {(reg <= 0.02).mean():.1%}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--eval", type=Path, required=True)
    ap.add_argument("--model", type=Path, required=True)
    args = ap.parse_args()

    import joblib  # trusted local artifact (our train_combat_surrogate.py output)

    model = joblib.load(args.model)
    schema, keep = model["schema"], model["keep_columns"]
    ctx_dim = schema["context_dim"]

    rows = [json.loads(l) for l in args.eval.read_text().splitlines() if l.strip()]
    print(f"loaded {len(rows)} realistic decisions")

    # Build one densified input per (decision, candidate, encounter).
    Xrows, meta = [], []
    for di, r in enumerate(rows):
        enc_idxs = [e["enc_idx"] for e in r["plan_enc"]]
        if not enc_idxs:
            continue
        for ci, c in enumerate(r["candidates"]):
            base, _ = densify_one(c["feats"], schema)
            for k, ei in enumerate(enc_idxs):
                x = base.copy()
                x[ctx_dim + ei] = 1.0
                Xrows.append(x)
                meta.append((di, ci, k))
    X = np.array(Xrows, dtype=np.float32)[:, keep]
    win = np.clip(model["win"].predict(X), 0.0, 1.0)
    hpf = np.clip(model["hp"].predict(X), 0.0, None)

    pred = defaultdict(list)  # (di,ci) -> [(win,hp_frac)] in plan_enc order
    for (di, ci, k), w, h in zip(meta, win, hpf):
        pred[di, ci].append((float(w), float(h)))

    # ───────────────── 2. DECISION AGREEMENT ─────────────────
    teacher_pairs, ideal_pairs = [], []
    for di, r in enumerate(rows):
        cands = r["candidates"]
        if len(cands) < 2 or not r["plan_enc"]:
            continue
        sg, gt_teacher, gt_ideal = [], [], []
        for ci, c in enumerate(cands):
            mh = c["feats"]["scalars"][1]
            sg.append(sum(blend(w, h, mh) for (w, h) in pred[di, ci]))
            gt_teacher.append(c["gt_score_teacher"])
            gt_ideal.append(
                sum(blend(t["true_win"], t["true_hp_frac"], t["max_hp"]) for t in c["truth"])
            )
        teacher_pairs.append((sg, gt_teacher))
        ideal_pairs.append((sg, gt_ideal))

    print("\n=== DECISION AGREEMENT (realistic card-shaped decisions) ===")
    print("  [regret in ground-truth score units; candidates differ by one card,")
    print("   so options are often near-equivalent → small regret = cheap mistake]")
    rank_report("TEACHER fight_delta.score (replace the boss-sim score)", teacher_pairs)
    rank_report("IDEAL full-fight win/HP blend (model quality)", ideal_pairs)

    # ───────────────── 1. GENERALIZATION ─────────────────
    ty, pw, th, ph, mhs = [], [], [], [], []
    for di, r in enumerate(rows):
        for ci, c in enumerate(r["candidates"]):
            for k, t in enumerate(c["truth"]):
                w, h = pred[di, ci][k]
                ty.append(t["true_win"])
                pw.append(w)
                th.append(t["true_hp_frac"])
                ph.append(h)
                mhs.append(t["max_hp"])
    ty, pw = np.array(ty), np.array(pw)
    th, ph, mhs = np.array(th), np.array(ph), np.array(mhs)
    win_bin = (ty >= 0.5).astype(int)

    print("\n=== GENERALIZATION (realistic states, vs full-fight encounter_delta truth) ===")
    print(f"  (candidate,encounter) pairs : {len(ty)}   win base-rate {win_bin.mean():.3f}")
    try:
        auc = roc_auc_score(win_bin, np.clip(pw, 1e-6, 1 - 1e-6))
    except ValueError:
        auc = float("nan")
    acc = ((pw >= 0.5).astype(int) == win_bin).mean()
    brier = np.mean((np.clip(pw, 0, 1) - win_bin) ** 2)
    print(
        f"  WIN : AUC={auc:.3f}  acc={acc:.3f}  Brier={brier:.4f}  "
        f"MAE(win_rate)={np.mean(np.abs(pw - ty)):.4f}"
    )
    ss_res = np.sum((th - ph) ** 2)
    ss_tot = np.sum((th - th.mean()) ** 2)
    r2 = 1 - ss_res / ss_tot if ss_tot > 0 else float("nan")
    print(
        f"  HP  : MAE={np.mean(np.abs(ph - th)):.4f} frac "
        f"({np.mean(np.abs(ph - th) * mhs):.2f} hp)  R2={r2:+.3f}"
    )
    print("\n  synthetic held-out was: WIN AUC 0.973 / HP MAE 5.45 hp R2 0.884")
    print("  → AUC drop = ranking generalization cost; Brier/HP-MAE gap = calibration cost")


if __name__ == "__main__":
    main()
