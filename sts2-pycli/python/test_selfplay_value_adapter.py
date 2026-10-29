"""Test for `selfplay_to_value_inputs.py` (AlphaZero M5a, Task 7).

Confirms the adapter reshapes `selfplay_records.jsonl` (Task 4's
`SelfplayJsonlRow` — `{seed, act, context, z, ...}`) into M4's
`train_run_value_net.py` input pair (`features.jsonl` rows `{seed,
context}`, `decisions.jsonl` rows `{seed, win, act}`), and — the
load-bearing assertion — that the output actually loads cleanly into
`train_run_value_net.load()` with a matching `schema.json`: zero rows
dropped by the seed-join, one X row per input record.
"""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

from train_run_value_net import load

HERE = Path(__file__).parent

# Same tiny-fixture convention as test_run_value_net_export.py: 2 cards, 1
# relic, 3 scalars -> context_dim = 2*2 + 1 + 3 = 8.
N_CARDS = 2
N_RELICS = 1
SCALAR_NAMES = ["hp_frac", "gold", "floor"]
CONTEXT_DIM = 2 * N_CARDS + N_RELICS + len(SCALAR_NAMES)
SCHEMA_ID = "test-selfplay-schema-v1"


def _schema_dict() -> dict:
    return {
        "schema_id": SCHEMA_ID,
        "engine": "test-engine-abc123",
        "n_card": N_CARDS,
        "n_relic": N_RELICS,
        "n_scalar": len(SCALAR_NAMES),
        "context_dim": CONTEXT_DIM,
    }


def _context(gold: float, floor: int) -> dict:
    return {
        "deck": [[0, 5.0, 1.0], [1, 3.0, 0.0]],
        "relics": [[0, 1.0]],
        "scalars": [0.5, gold, float(floor)],
    }


def _selfplay_row(seed: int, act: int, step: int, z: float, gold: float) -> dict:
    """One `SelfplayJsonlRow`-shaped line (Task 4's `selfplay_records.jsonl`),
    including fields the adapter does NOT need (candidates/card_idx/pi/
    chosen/schema_id) to prove it tolerates the real (wider) row shape."""
    return {
        "seed": seed,
        "act": act,
        "step": step,
        "schema_id": SCHEMA_ID,
        "context": _context(gold=gold, floor=step),
        "candidates": [[0.1, 0.2, 0.3, 0.4, 0.5]],
        "card_idx": [3],
        "pi": [1.0],
        "chosen": 0,
        "z": z,
    }


def _write_records(tmp_path: Path) -> Path:
    """2 seeds, distinct z, a couple rows each (uneven counts to prove the
    row-count assertion isn't a coincidence of symmetry)."""
    rows = [
        _selfplay_row(seed=100, act=1, step=0, z=1.0, gold=80.0),
        _selfplay_row(seed=100, act=2, step=1, z=1.0, gold=85.0),
        _selfplay_row(seed=100, act=3, step=2, z=1.0, gold=90.0),
        _selfplay_row(seed=200, act=1, step=0, z=0.0, gold=60.0),
        _selfplay_row(seed=200, act=1, step=1, z=0.0, gold=65.0),
    ]
    records_path = tmp_path / "selfplay_records.jsonl"
    records_path.write_text("\n".join(json.dumps(r) for r in rows) + "\n")
    return records_path


def _run_adapter(
    records: Path, features_out: Path, decisions_out: Path
) -> subprocess.CompletedProcess:
    return subprocess.run(
        [
            sys.executable,
            "selfplay_to_value_inputs.py",
            "--records",
            str(records),
            "--features-out",
            str(features_out),
            "--decisions-out",
            str(decisions_out),
        ],
        cwd=HERE,
        capture_output=True,
        text=True,
    )


def test_adapter_reshapes_and_loads_cleanly(tmp_path: Path) -> None:
    records = _write_records(tmp_path)
    features_out = tmp_path / "value_features.jsonl"
    decisions_out = tmp_path / "value_decisions.jsonl"

    result = _run_adapter(records, features_out, decisions_out)
    assert result.returncode == 0, result.stderr

    feature_lines = [
        json.loads(line) for line in features_out.read_text().splitlines() if line.strip()
    ]
    decision_lines = [
        json.loads(line) for line in decisions_out.read_text().splitlines() if line.strip()
    ]

    # (b) value_features row count == input row count.
    assert len(feature_lines) == 5

    # (c) each feature row carries `context` + `seed` (and nothing forces it
    # to carry the wider self-play fields — the adapter reshapes down).
    for row in feature_lines:
        assert "seed" in row
        assert "context" in row
        assert set(row["context"].keys()) == {"deck", "relics", "scalars"}

    # (a) one value_decisions row per seed with `win == z`.
    assert len(decision_lines) == 2
    win_by_seed = {r["seed"]: r["win"] for r in decision_lines}
    assert win_by_seed == {100: 1, 200: 0}
    act_by_seed = {r["seed"]: r["act"] for r in decision_lines}
    assert act_by_seed == {100: 3, 200: 1}

    # (d) the output loads into train_run_value_net.load() with ZERO
    # schema/seed-join drops. This is the load-bearing assertion: it
    # proves the adapter's output is genuinely consumable by the M4
    # trainer, not merely shaped like it.
    schema = _schema_dict()
    d = load(features_out, decisions_out, schema)
    assert d["X"].shape == (5, CONTEXT_DIM)
    assert d["seeds"].tolist() == [100, 100, 100, 200, 200]
    assert d["y_win"].tolist() == [1.0, 1.0, 1.0, 0.0, 0.0]
