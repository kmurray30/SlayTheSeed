"""Schema-conformance test for `train_combat_surrogate_net.py` (AZ M1 Task 3).

Runs the torch trainer end-to-end on a tiny synthetic fixture (a 3-row
labels JSONL + matching `.schema.json` sidecar, in the exact shape
`combat_surrogate_data --realistic` emits) and asserts the exported net
JSON has every field + shape the Rust `SurrogateNet` loader
(`sts2-rs/crates/sts2-runai/src/eval/surrogate.rs`) requires. This is the
seam contract: if this test passes but the Rust loader still rejects the
file, the schemas have drifted and one of the two sides needs fixing.
"""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).parent

# Small fixed context dimension so the fixture stays tiny and the encoder
# layout is trivial to hand-write: 2 cards, 1 relic, 3 scalars ->
# context_dim = 2*2 + 1 + 3 = 8.
N_CARDS = 2
N_RELICS = 1
SCALAR_NAMES = ["hp_frac", "gold", "floor"]
CONTEXT_DIM = 2 * N_CARDS + N_RELICS + len(SCALAR_NAMES)
N_ENCOUNTERS = 2


def _schema_dict() -> dict:
    return {
        "schema_id": "test-schema-v1",
        "engine": "test-engine-abc123",
        "context_dim": CONTEXT_DIM,
        "n_cards": N_CARDS,
        "n_relics": N_RELICS,
        "scalar_names": SCALAR_NAMES,
        "n_scalars": len(SCALAR_NAMES),
        "n_encounters": N_ENCOUNTERS,
        "encounters": [
            {"idx": 0, "id": "ENC_A", "act": 1, "tier": "normal"},
            {"idx": 1, "id": "ENC_B", "act": 1, "tier": "elite"},
        ],
        "layout": "x = [deck(count,upg) per card_idx] ++ [relic count] ++ [scalars]",
    }


def _row(seed: int, enc_idx: int, win_rate: float, hp_frac_lost: float) -> dict:
    return {
        "seed": seed,
        "variant": 0,
        "deck": [[0, 5.0, 1.0], [1, 3.0, 0.0]],
        "relics": [[0, 1.0]],
        "scalars": [0.5, 100.0, 3.0],
        "enc_idx": enc_idx,
        "enc_id": "ENC_A" if enc_idx == 0 else "ENC_B",
        "enc_act": 1,
        "enc_tier": "normal" if enc_idx == 0 else "elite",
        "win_rate": win_rate,
        "hp_lost_mean": hp_frac_lost * 50.0,
        "hp_frac_lost": hp_frac_lost,
        "losses": 0,
        "probes": 12,
        "terminals": 12,
        "max_hp": 50,
        "entry_hp": 40,
        "deck_size": 8,
        "source": "synthetic",
    }


def _write_fixture(tmp_path: Path) -> tuple[Path, Path]:
    labels = tmp_path / "labels.jsonl"
    schema = tmp_path / "labels.jsonl.schema.json"
    rows = [
        _row(seed=1, enc_idx=0, win_rate=0.9, hp_frac_lost=0.1),
        _row(seed=2, enc_idx=1, win_rate=0.4, hp_frac_lost=0.5),
        _row(seed=3, enc_idx=0, win_rate=0.6, hp_frac_lost=0.3),
    ]
    labels.write_text("\n".join(json.dumps(r) for r in rows) + "\n")
    schema.write_text(json.dumps(_schema_dict()))
    return labels, schema


def test_export_conforms_to_rust_schema(tmp_path: Path) -> None:
    labels, schema = _write_fixture(tmp_path)
    out = tmp_path / "net.json"
    subprocess.run(
        [
            sys.executable,
            "train_combat_surrogate_net.py",
            "--labels",
            str(labels),
            "--schema",
            str(schema),
            "--out",
            str(out),
            "--epochs",
            "1",
            "--hidden",
            "4",
            "--enc-dim",
            "2",
            "--holdout-frac",
            "0.34",
        ],
        check=True,
        cwd=HERE,
    )
    net = json.loads(out.read_text())

    for k in (
        "schema_id",
        "engine",
        "context_dim",
        "n_encounters",
        "enc_dim",
        "hidden",
        "feat_mu",
        "feat_sd",
        "enc_emb",
        "w1",
        "b1",
        "w_win",
        "b_win",
        "w_hp",
        "b_hp",
        "win_calib",
    ):
        assert k in net, f"missing {k}"

    assert net["schema_id"] == "test-schema-v1"
    assert net["engine"] == "test-engine-abc123"
    assert net["context_dim"] == CONTEXT_DIM
    assert net["n_encounters"] == N_ENCOUNTERS
    assert net["enc_dim"] == 2
    assert net["hidden"] == 4
    assert net["win_calib"] is None

    assert len(net["feat_mu"]) == CONTEXT_DIM
    assert len(net["feat_sd"]) == CONTEXT_DIM
    assert all(sd > 0 for sd in net["feat_sd"]), "feat_sd must be strictly positive"

    assert len(net["enc_emb"]) == net["n_encounters"] + 1
    assert all(len(row) == net["enc_dim"] for row in net["enc_emb"])

    assert len(net["w1"]) == net["context_dim"] + net["enc_dim"]
    assert all(len(r) == net["hidden"] for r in net["w1"])
    assert len(net["b1"]) == net["hidden"]

    assert len(net["w_win"]) == net["hidden"]
    assert isinstance(net["b_win"], float)
    assert len(net["w_hp"]) == net["hidden"]
    assert isinstance(net["b_hp"], float)


def test_export_merges_labels_extra(tmp_path: Path) -> None:
    """`--labels-extra` rows must be merged in (more seeds -> split still works)."""
    labels, schema = _write_fixture(tmp_path)
    extra = tmp_path / "labels_extra.jsonl"
    extra_rows = [
        _row(seed=4, enc_idx=1, win_rate=0.2, hp_frac_lost=0.7),
        _row(seed=5, enc_idx=0, win_rate=0.8, hp_frac_lost=0.2),
    ]
    extra.write_text("\n".join(json.dumps(r) for r in extra_rows) + "\n")
    out = tmp_path / "net.json"
    subprocess.run(
        [
            sys.executable,
            "train_combat_surrogate_net.py",
            "--labels",
            str(labels),
            "--labels-extra",
            str(extra),
            "--schema",
            str(schema),
            "--out",
            str(out),
            "--epochs",
            "1",
            "--hidden",
            "4",
            "--enc-dim",
            "2",
            "--holdout-frac",
            "0.2",
        ],
        check=True,
        cwd=HERE,
    )
    net = json.loads(out.read_text())
    assert net["context_dim"] == CONTEXT_DIM
    assert len(net["w1"]) == net["context_dim"] + net["enc_dim"]


def test_fit_win_calibrator_isotonic_is_soft_fit_and_monotone() -> None:
    """Fix #3: isotonic must fit the CONTINUOUS `win_rate` target, not a
    binarized win/loss call.

    NOTE: a fitted breakpoint landing strictly inside (0, 1) does NOT by
    itself prove the fit target was continuous — PAVA (isotonic
    regression) pools any label-noise violators straddling a binarizing
    threshold like 0.5, so even a fit against `(win_rate >= 0.5)` can
    produce an interior breakpoint (e.g. exactly 0.5) from pooled 0/1
    labels. An "any interior breakpoint" check is therefore NOT a
    discriminator between a soft fit and a binarized fit.

    What actually distinguishes them is reconstruction error: fitting
    against the soft `y_soft` target and applying the resulting
    calibrator back to `raw_p` must reproduce `y_soft` far more
    accurately than fitting against a binarized `(y_soft >= 0.5)` target
    ever could, because the binarized fit throws away all the mid-range
    structure. We build `y_soft` as a genuinely continuous,
    monotone-in-`raw_p` target with plenty of points in the 0.2-0.8
    mid-range (not just noise clustered at the 0/1 extremes), fit BOTH
    ways on the same `raw_p`, apply both calibrators via the production
    `apply_win_calib`, and assert the soft fit's MSE against `y_soft` is
    both small in absolute terms and dramatically lower than the
    binarized fit's MSE. This fails cleanly if the production code is
    ever reverted to fitting the binarized target. Also asserts the
    standard isotonic monotonicity contract on the exported `y`
    breakpoints.
    """
    from train_combat_surrogate_net import apply_win_calib, fit_win_calibrator

    n = 40
    raw_p = np.linspace(0.05, 0.95, n)
    # Continuous, monotone-in-raw_p soft target with plenty of genuine
    # mid-range (0.2-0.8) structure — a checkerboard offset keeps it
    # non-trivial (not literally raw_p) while staying overall increasing.
    offsets = np.array([0.05 if i % 2 == 0 else -0.05 for i in range(n)])
    y_soft = np.clip(raw_p + offsets, 0.0, 1.0)
    y_bin = (y_soft >= 0.5).astype(np.float64)

    calib_soft = fit_win_calibrator(raw_p, y_soft, "isotonic")
    calib_bin = fit_win_calibrator(raw_p, y_bin, "isotonic")

    assert calib_soft is not None
    assert calib_bin is not None
    assert calib_soft["kind"] == "isotonic"
    y = calib_soft["y"]
    assert len(y) >= 2
    assert all(y[i] <= y[i + 1] for i in range(len(y) - 1)), f"not monotone: {y}"

    pred_soft = apply_win_calib(raw_p, calib_soft)
    pred_bin = apply_win_calib(raw_p, calib_bin)
    soft_mse = float(np.mean((pred_soft - y_soft) ** 2))
    bin_mse = float(np.mean((pred_bin - y_soft) ** 2))

    # Empirically: soft_mse ~= 0.0015, bin_mse ~= 0.069 (~46x higher) for
    # this fixture. Thresholds below leave a comfortable margin on both
    # sides while still failing hard if the fit target reverts to binary.
    assert soft_mse < 0.02, f"soft fit should closely reproduce y_soft, got MSE={soft_mse}"
    assert soft_mse < bin_mse / 5, (
        f"soft fit (MSE={soft_mse}) should reconstruct the continuous target far better "
        f"than a binarized fit (MSE={bin_mse}) — otherwise the test can't tell the two apart"
    )
