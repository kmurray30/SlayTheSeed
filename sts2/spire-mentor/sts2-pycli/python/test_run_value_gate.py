"""Tests for `eval_run_value_net.py` (AlphaZero outer-loop, Milestone 4,
Task 5 — win-prob go/no-go gate).

Mirrors `test_surrogate_gate.py` (AZ M1 Task 6):

- `test_gate_fails_on_bad_net` — the eval script exits non-zero when pointed
  at a deliberately-untrained net (`fixtures/run_value_untrained_net.json`,
  all-zero weights -> constant chance-level `win_prob=0.5` for every row).
- `test_numpy_forward_matches_parity_fixture` — the cross-implementation
  anchor. `eval_run_value_net.py`'s numpy forward is a THIRD implementation
  of `RunValueNet::forward` (Rust) / `RunValueMLP.forward` (torch,
  `train_run_value_net.py`). This loads the committed Rust<->torch parity
  fixture (`fixtures/run_value_net_parity.json`, vendored from
  `sts2-rs/crates/sts2-runai/tests/fixtures/` — produced by
  `train_run_value_net.py --emit-parity-fixture`, Rust-verified in
  `sts2-rs/crates/sts2-runai/tests/run_value_net_parity.rs`, and kept in
  sync by `tests/test_fixture_sync.py` when the sibling repo is present)
  and asserts the numpy forward reproduces each case's `win_prob` and
  `act_probs` within `1e-4` — exercising standardization, the trunk, and a
  non-null isotonic `win_calib` against Rust-verified ground truth.
"""

from __future__ import annotations

import json
import pathlib
import subprocess
import sys

import numpy as np

HERE = pathlib.Path(__file__).parent
PARITY_FIXTURE = HERE / "fixtures/run_value_net_parity.json"


def test_gate_fails_on_bad_net() -> None:
    # Point the evaluator at a deliberately-untrained net fixture; expect exit!=0.
    r = subprocess.run(
        [
            sys.executable,
            "eval_run_value_net.py",
            "--net",
            "fixtures/run_value_untrained_net.json",
            "--features",
            "fixtures/run_value_tiny.heldout.jsonl",
            "--decisions",
            "fixtures/run_value_tiny.decisions.jsonl",
            "--schema",
            "fixtures/run_value_tiny.schema.json",
        ],
        cwd=HERE,
        capture_output=True,
        text=True,
    )
    assert r.returncode != 0, f"expected non-zero exit, got 0. stdout:\n{r.stdout}"
    # Sanity: it should fail because a metric missed a gate, not because it
    # crashed before printing a report.
    assert "FAIL" in r.stdout, f"expected a FAIL line in the report:\n{r.stdout}"


def test_numpy_forward_matches_parity_fixture() -> None:
    from eval_run_value_net import run_value_forward

    fixture = json.loads(PARITY_FIXTURE.read_text())
    net = fixture["net"]
    cases = fixture["cases"]
    assert len(cases) > 0
    # This fixture is required to exercise a non-null (isotonic) win_calib —
    # otherwise the anchor wouldn't cover the calibration path at all.
    assert net["win_calib"] is not None
    assert net["win_calib"]["kind"] == "isotonic"

    for case in cases:
        ctx = np.asarray(case["ctx_dense"], dtype=np.float64).reshape(1, -1)
        win_prob, act_probs = run_value_forward(net, ctx)
        assert abs(float(win_prob[0]) - case["win_prob"]) < 1e-4, (
            f"win_prob mismatch: got {win_prob[0]}, expected {case['win_prob']}"
        )
        for i, expected in enumerate(case["act_probs"]):
            assert abs(float(act_probs[0][i]) - expected) < 1e-4, (
                f"act_probs[{i}] mismatch: got {act_probs[0][i]}, expected {expected}"
            )
