"""Unit tests for telemetry bucketing and summary statistics."""

import pytest
from simlab.telemetry import bucket_key, summarize, throughput


def _row(dmg, deck="starter", strategy="random", seed=0):
    return {
        "encounter": "E",
        "ascension": 0,
        "deck": deck,
        "strategy": strategy,
        "seed": seed,
        "dmg_taken": dmg,
        "victory": True,
        "turns": 3,
    }


def test_bucket_key_includes_deck_and_tolerates_legacy():
    assert bucket_key(_row(1)) == "E/0/starter/random"
    legacy = _row(1)
    del legacy["deck"]
    assert bucket_key(legacy) == "E/0/default/random"


def test_summarize_mode_worst_box():
    rows = [_row(d, seed=i) for i, d in enumerate([0, 2, 2, 2, 5, 25])]
    b = summarize(rows)["buckets"]["E/0/starter/random"]
    assert b["mode_dmg"] == 2
    assert b["worst"] == 25
    assert b["worst_p"] == round(1 / 6, 6)
    box = b["box"]
    assert box["median"] == 2
    assert set(box) == {"q1", "median", "q3", "lo", "hi", "outliers"}
    assert box["q1"] <= box["median"] <= box["q3"]


def test_box_outliers_beyond_p1_p99():
    rows = [_row(2, seed=i) for i in range(200)] + [_row(40, seed=200)]
    box = summarize(rows)["buckets"]["E/0/starter/random"]["box"]
    assert box["outliers"] == [40]


def test_summarize_separates_decks():
    rows = [_row(1, deck="a", seed=0), _row(9, deck="b", seed=0)]
    b = summarize(rows)["buckets"]
    assert b["E/0/a/random"]["mean_dmg"] == pytest.approx(1.0)
    assert b["E/0/b/random"]["mean_dmg"] == pytest.approx(9.0)


def _timed_row(actions, turns, wall_ms, **kw):
    return {**_row(0, **kw), "actions": actions, "turns": turns, "wall_ms": wall_ms}


def test_throughput_per_core_rates():
    # 2 combats, 100 actions, 10 turns, 2000 ms = 2.0 core-seconds.
    rows = [_timed_row(60, 6, 1500, seed=0), _timed_row(40, 4, 500, seed=1)]
    tp = throughput(rows)
    assert tp["combats"] == 2
    assert tp["actions"] == 100
    assert tp["turns"] == 10
    assert tp["core_seconds"] == pytest.approx(2.0)
    assert tp["actions_per_core_s"] == pytest.approx(50.0)
    assert tp["turns_per_core_s"] == pytest.approx(5.0)
    assert tp["combats_per_core_s"] == pytest.approx(1.0)


def test_throughput_tolerates_missing_timing_fields():
    # Hand-built rows (and error rows) lack actions/wall_ms: rates are 0, no raise.
    tp = throughput([_row(1, seed=0), {"error": "boom", "encounter": "E"}])
    assert tp["combats"] == 1  # error row excluded
    assert tp["core_seconds"] == pytest.approx(0.0)
    assert tp["actions_per_core_s"] == pytest.approx(0.0)


def test_summarize_carries_throughput_top_and_per_bucket():
    rows = [_timed_row(10, 1, 1000, seed=0)]
    summary = summarize(rows)
    assert "throughput" in summary
    assert summary["throughput"]["combats"] == 1
    assert summary["buckets"]["E/0/starter/random"]["throughput"]["combats"] == 1
