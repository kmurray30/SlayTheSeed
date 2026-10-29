import shutil
from pathlib import Path

from workbench.aggregate import aggregate_runs

FIX = Path(__file__).parent / "fixtures" / "sample_mcts_run.jsonl"


def test_aggregate_summarizes_losses(tmp_path):
    for i in range(2):
        shutil.copy(FIX, tmp_path / f"run{i}.jsonl")
    agg = aggregate_runs(str(tmp_path))
    assert agg["n_runs"] == 2
    assert agg["n_losses"] == 2
    assert agg["final_turn_histogram"].get("5") == 2
    assert agg["turn1_q"]["max"] <= -0.5
    assert agg["pattern_tags"].get("hopeless_position", 0) == 2
    assert len(agg["representative_losses"]) >= 1
