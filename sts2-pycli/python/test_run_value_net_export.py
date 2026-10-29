"""Schema-conformance test for `train_run_value_net.py` (AZ M4 Task 3).

Mirrors `test_surrogate_net_export.py` (AZ M1 Task 3): runs the torch
trainer end-to-end on a tiny synthetic fixture (`features.jsonl` +
`decisions.jsonl` + `schema.json`, in the exact shape `human_features`
emits) and asserts the exported net JSON has every field + shape the Rust
`RunValueNet` loader
(`sts2-rs/crates/sts2-runai/src/eval/run_value_net.rs::RunValueJson`)
requires. This is the seam contract: if this test passes but the Rust
loader still rejects the file, the schemas have drifted and one of the two
sides needs fixing. Also covers the `--emit-parity-fixture` sidecar (RAW,
pre-standardization `ctx_dense`) and the seed-join dropped-row diagnostic.
"""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Any

import numpy as np

HERE = Path(__file__).parent

# Small fixed dims so the fixture stays tiny and the densify layout is
# trivial to hand-write: 2 cards, 1 relic, 3 scalars -> context_dim =
# 2*2 + 1 + 3 = 8. Same layout convention as test_surrogate_net_export.py.
N_CARDS = 2
N_RELICS = 1
SCALAR_NAMES = ["hp_frac", "gold", "floor"]
CONTEXT_DIM = 2 * N_CARDS + N_RELICS + len(SCALAR_NAMES)
N_ACT_BUCKETS = 4


def _schema_dict() -> dict:
    return {
        "schema_id": "test-run-schema-v1",
        "engine": "test-engine-abc123",
        "n_card": N_CARDS,
        "n_relic": N_RELICS,
        "n_scalar": len(SCALAR_NAMES),
        "context_dim": CONTEXT_DIM,
    }


def _feature_row(seed: int, step: int, floor: int, gold: float) -> dict:
    """One `FeatureRow`-shaped line (`human_features`'s `features.jsonl`)."""
    return {
        "seed": seed,
        "step": step,
        "floor": floor,
        "surface": "card_reward",
        "schema_id": "test-run-schema-v1",
        "engine": "test-engine-abc123",
        "context": {
            "deck": [[0, 5.0, 1.0], [1, 3.0, 0.0]],
            "relics": [[0, 1.0]],
            "scalars": [0.5, gold, float(floor)],
        },
    }


def _decision_row(seed: int, act: int, win: int) -> dict:
    """One `decisions.jsonl` line: `{seed, act, win}` per run."""
    return {"seed": seed, "act": act, "win": win}


def _densify(context: dict, schema: dict) -> np.ndarray:
    """Reference re-implementation of `train_value.densify`, used ONLY to
    independently recompute the expected RAW context vector for the
    ctx_dense-is-raw assertion below (kept separate from the module under
    test so the assertion doesn't just check the code against itself)."""
    n_card, n_relic = schema["n_card"], schema["n_relic"]
    x = np.zeros(schema["context_dim"], dtype=np.float32)
    for idx, count, upgraded in context["deck"]:
        i = int(idx)
        if i < n_card:
            x[2 * i] = count
            x[2 * i + 1] = upgraded
    base = 2 * n_card
    for idx, count in context["relics"]:
        i = int(idx)
        if i < n_relic:
            x[base + i] = count
    sbase = base + n_relic
    scalars = np.asarray(context["scalars"], dtype=np.float32)
    x[sbase : sbase + schema["n_scalar"]] = scalars[: schema["n_scalar"]]
    return x


def _write_fixture(
    tmp_path: Path, n_seeds: int = 3, extra_orphan_seeds: int = 0
) -> tuple[Path, Path, Path, list[dict]]:
    """Write `features.jsonl` / `decisions.jsonl` / `schema.json`.

    `n_seeds` seeds each get one floor-row + a matching decision (win
    alternates, act varies so both value and aux targets have spread).
    `extra_orphan_seeds` adds feature rows with NO matching decision row,
    to exercise the dropped-row join diagnostic (Finding 3).

    Returns the three paths plus the list of feature-row dicts actually
    joined (i.e. excluding orphans) in file order, for the RAW-ctx_dense
    cross-check.
    """
    features_path = tmp_path / "features.jsonl"
    decisions_path = tmp_path / "decisions.jsonl"
    schema_path = tmp_path / "schema.json"
    schema = _schema_dict()

    joined_rows: list[dict] = []
    feature_lines: list[str] = []
    decision_lines: list[str] = []
    for i in range(n_seeds):
        seed = 100 + i
        win = i % 2
        act = 1 + (i % 3)
        row = _feature_row(seed=seed, step=0, floor=1 + i, gold=80.0 + 10.0 * i)
        feature_lines.append(json.dumps(row))
        decision_lines.append(json.dumps(_decision_row(seed=seed, act=act, win=win)))
        joined_rows.append(row)

    for j in range(extra_orphan_seeds):
        orphan_seed = 900 + j
        feature_lines.append(
            json.dumps(_feature_row(seed=orphan_seed, step=0, floor=1, gold=999.0))
        )
        # deliberately no matching decisions_path line

    features_path.write_text("\n".join(feature_lines) + "\n")
    decisions_path.write_text("\n".join(decision_lines) + "\n")
    schema_path.write_text(json.dumps(schema))
    return features_path, decisions_path, schema_path, joined_rows


def _run_trainer(args: list[str]) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, "train_run_value_net.py", *args],
        cwd=HERE,
        capture_output=True,
        text=True,
    )


def test_export_conforms_to_rust_schema(tmp_path: Path) -> None:
    features, decisions, schema, joined_rows = _write_fixture(tmp_path, n_seeds=3)
    out = tmp_path / "net.json"
    parity = tmp_path / "parity.json"
    result = _run_trainer(
        [
            "--features",
            str(features),
            "--decisions",
            str(decisions),
            "--schema",
            str(schema),
            "--out",
            str(out),
            "--epochs",
            "2",
            "--hidden",
            "4",
            "--holdout-frac",
            "0.34",
            "--emit-parity-fixture",
            str(parity),
            "--parity-cases",
            "2",
        ]
    )
    assert result.returncode == 0, result.stderr
    net = json.loads(out.read_text())

    for k in (
        "schema_id",
        "engine",
        "context_dim",
        "hidden",
        "feat_mu",
        "feat_sd",
        "w1",
        "b1",
        "w_val",
        "b_val",
        "w_act",
        "b_act",
        "win_calib",
    ):
        assert k in net, f"missing {k}"

    assert net["schema_id"] == "test-run-schema-v1"
    assert net["context_dim"] == CONTEXT_DIM
    assert net["hidden"] == 4

    assert len(net["feat_mu"]) == CONTEXT_DIM
    assert len(net["feat_sd"]) == CONTEXT_DIM
    assert all(sd > 0 for sd in net["feat_sd"]), "feat_sd must be strictly positive"

    assert len(net["w1"]) == net["context_dim"]
    assert all(len(r) == net["hidden"] for r in net["w1"])
    assert len(net["b1"]) == net["hidden"]

    assert len(net["w_val"]) == net["hidden"]
    assert isinstance(net["b_val"], float)

    assert len(net["w_act"]) == net["hidden"]
    assert all(len(r) == N_ACT_BUCKETS for r in net["w_act"])
    assert len(net["b_act"]) == N_ACT_BUCKETS

    # win_calib must be one of the three valid wire states: null, or a
    # dict with a recognized `kind`. This tiny fixture (3 seeds) is far
    # below MIN_CALIB_ROWS=20 so the trainer must fall back to null.
    assert net["win_calib"] is None or net["win_calib"]["kind"] in ("isotonic", "platt")
    assert net["win_calib"] is None

    # ── parity fixture: net + cases, RAW (pre-standardization) ctx_dense ──
    fixture: dict[str, Any] = json.loads(parity.read_text())
    assert "net" in fixture
    assert "cases" in fixture
    assert fixture["net"] == net

    raw_rows = [_densify(r["context"], _schema_dict()) for r in joined_rows]
    cases = fixture["cases"]
    assert len(cases) >= 1
    for case in cases:
        ctx_dense = case["ctx_dense"]
        assert len(ctx_dense) == CONTEXT_DIM
        # RAW, not standardized: must exactly match one of the hand-densified
        # rows above. A standardized vector would NOT match (our scalars
        # have nonzero mean/std by construction), so this positively proves
        # the emitted ctx_dense is pre-standardization, catching the
        # documented double-standardization footgun.
        assert any(np.allclose(ctx_dense, raw, atol=1e-4) for raw in raw_rows), (
            f"ctx_dense {ctx_dense} is not a RAW densified row (looks standardized?)"
        )
        assert 0.0 <= case["win_prob"] <= 1.0
        assert len(case["act_probs"]) == N_ACT_BUCKETS
        assert abs(sum(case["act_probs"]) - 1.0) < 1e-4


def test_export_reports_dropped_rows(tmp_path: Path) -> None:
    """Finding 3: feature rows whose seed has no decisions match must be
    counted and reported on stderr (helps diagnose Task 6's real-corpus
    join), while still succeeding on the remaining matched rows."""
    features, decisions, schema, _joined = _write_fixture(tmp_path, n_seeds=3, extra_orphan_seeds=2)
    out = tmp_path / "net.json"
    result = _run_trainer(
        [
            "--features",
            str(features),
            "--decisions",
            str(decisions),
            "--schema",
            str(schema),
            "--out",
            str(out),
            "--epochs",
            "1",
            "--hidden",
            "4",
            "--holdout-frac",
            "0.34",
        ]
    )
    assert result.returncode == 0, result.stderr
    assert "dropped 2" in result.stderr, result.stderr
    assert out.exists()


def test_load_raises_when_all_rows_dropped(tmp_path: Path) -> None:
    """Keep the existing hard-failure behavior when EVERY feature row is
    orphaned (no seed overlap at all) — must still error out, not silently
    export an empty/garbage net."""
    features, decisions, schema, _joined = _write_fixture(tmp_path, n_seeds=0, extra_orphan_seeds=3)
    out = tmp_path / "net.json"
    result = _run_trainer(
        [
            "--features",
            str(features),
            "--decisions",
            str(decisions),
            "--schema",
            str(schema),
            "--out",
            str(out),
            "--epochs",
            "1",
            "--hidden",
            "4",
        ]
    )
    assert result.returncode != 0
    assert not out.exists()
