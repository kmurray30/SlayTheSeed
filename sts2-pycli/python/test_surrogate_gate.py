"""Tests for `eval_combat_surrogate_net.py` (AlphaZero outer-loop, Milestone
1, Task 6 — accuracy + real-state calibration go/no-go gate).

Two tests:

- `test_gate_fails_on_bad_net` (from `task-6-brief.md` Step 1) — the eval
  script exits non-zero when pointed at a deliberately-untrained net
  (`fixtures/untrained_net.json`, all-zero weights -> chance-level metrics).
- `test_numpy_forward_matches_parity_fixture` — the cross-implementation
  anchor. `eval_combat_surrogate_net.py`'s numpy forward is a THIRD
  implementation of `SurrogateNet::forward` (Rust) /
  `SurrogateMLP.forward` (torch, `train_combat_surrogate_net.py`). This
  loads the Rust<->torch parity fixture (`fixtures/surrogate_parity.json`,
  vendored from `sts2-rs/crates/sts2-runai/tests/fixtures/` — produced by
  `train_combat_surrogate_net.py --emit-parity-fixture`, Rust-verified in
  `sts2-rs/crates/sts2-runai/tests/surrogate_parity.rs`, and kept in sync
  by `tests/test_fixture_sync.py` when the sibling repo is present)
  and asserts the numpy forward reproduces each case's `win_prob` and
  `hp_frac_lost` within `1e-4` — exercising standardization, the trunk,
  the OOD encounter clamp, AND a non-null isotonic `win_calib` against
  Rust-verified ground truth.
"""

from __future__ import annotations

import json
import pathlib
import subprocess
import sys

import numpy as np

HERE = pathlib.Path(__file__).parent
PARITY_FIXTURE = HERE / "fixtures/surrogate_parity.json"


def test_gate_fails_on_bad_net() -> None:
    # Point the evaluator at a deliberately-untrained net fixture; expect exit!=0.
    r = subprocess.run(
        [
            sys.executable,
            "eval_combat_surrogate_net.py",
            "--net",
            "fixtures/untrained_net.json",
            "--labels",
            "fixtures/tiny_realistic.jsonl",
            "--schema",
            "fixtures/tiny_realistic.jsonl.schema.json",
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
    from eval_combat_surrogate_net import surrogate_forward

    fixture = json.loads(PARITY_FIXTURE.read_text())
    net = fixture["net"]
    cases = fixture["cases"]
    assert len(cases) > 0
    # This fixture is required to exercise a non-null (isotonic) win_calib —
    # otherwise the anchor wouldn't cover the calibration path at all.
    assert net["win_calib"] is not None
    assert net["win_calib"]["kind"] == "isotonic"

    for case in cases:
        ctx = np.asarray(case["ctx_dense"], dtype=np.float64)
        enc_idx = np.asarray([case["encounter_idx"]], dtype=np.int64)
        win_prob, hp_frac_lost = surrogate_forward(net, ctx.reshape(1, -1), enc_idx)
        assert abs(float(win_prob[0]) - case["win_prob"]) < 1e-4, (
            f"win_prob mismatch: got {win_prob[0]}, expected {case['win_prob']}"
        )
        assert abs(float(hp_frac_lost[0]) - case["hp_frac_lost"]) < 1e-4, (
            f"hp_frac_lost mismatch: got {hp_frac_lost[0]}, expected {case['hp_frac_lost']}"
        )
