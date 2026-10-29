"""Tests for synergy.causal_run — parallel causal-layer orchestration.

The pure selection/sampling helpers are tested directly.  The parallel
table builders are exercised with monkeypatched per-pair estimators so no
Rust binary or process pool subprocess work is required (ProcessPoolExecutor
is monkeypatched to a serial map to keep the worker globals in-process).
"""

from __future__ import annotations

from pathlib import Path

import pytest

from synergy.causal_econ import EconRow, State
from synergy.causal_run import (
    InjectionResult,
    compute_combat_table,
    compute_econ_table,
    injection_ab,
    paired_wr_delta,
    sample_baselines,
    select_pairs,
)

# ---------------------------------------------------------------------------
# select_pairs
# ---------------------------------------------------------------------------


def _obs(pmi_pairs: list[tuple[float, str, str]]) -> list[dict]:
    return [{"pair": [a, b], "pmi": pmi} for pmi, a, b in pmi_pairs]


class TestSelectPairs:
    def test_takes_top_k_by_pmi(self) -> None:
        obs = _obs([(1.0, "A", "B"), (3.0, "C", "D"), (2.0, "E", "F")])
        pairs = select_pairs(obs, top_k=1, sample_k=0)
        # highest PMI = 3.0 → (C, D)
        assert pairs == [("C", "D")]

    def test_canonical_sorted_tuples(self) -> None:
        obs = _obs([(5.0, "Z", "A")])
        pairs = select_pairs(obs, top_k=1, sample_k=0)
        assert pairs == [("A", "Z")]  # sorted within the pair

    def test_dedup_top_and_sample(self) -> None:
        # Small set: top and below-median can overlap; result must dedup.
        obs = _obs([(2.0, "A", "B"), (1.0, "C", "D")])
        pairs = select_pairs(obs, top_k=2, sample_k=2, seed=1)
        assert len(pairs) == len(set(pairs))

    def test_sample_draws_from_below_median(self) -> None:
        # 5 pairs; median index = 2. Below-median half = indices [2,3,4]
        # (PMI 3,2,1). top_k=1 takes PMI=5. sample should come from the low half.
        obs = _obs(
            [
                (5.0, "A", "A2"),
                (4.0, "B", "B2"),
                (3.0, "C", "C2"),
                (2.0, "D", "D2"),
                (1.0, "E", "E2"),
            ]
        )
        pairs = select_pairs(obs, top_k=1, sample_k=2, seed=0)
        assert pairs[0] == ("A", "A2")  # the top
        low_tokens = {("C", "C2"), ("D", "D2"), ("E", "E2")}
        for p in pairs[1:]:
            assert p in low_tokens

    def test_empty_obs(self) -> None:
        assert select_pairs([], top_k=10, sample_k=10) == []

    def test_requesting_more_than_available(self) -> None:
        obs = _obs([(1.0, "A", "B")])
        pairs = select_pairs(obs, top_k=100, sample_k=100)
        assert pairs == [("A", "B")]


# ---------------------------------------------------------------------------
# sample_baselines
# ---------------------------------------------------------------------------


class TestSampleBaselines:
    def test_filters_by_size_and_win(self, monkeypatch: pytest.MonkeyPatch) -> None:
        runs = [
            {"win": True, "players": [{"deck": [{"id": f"CARD.C{i}"} for i in range(16)]}]},
            {"win": False, "players": [{"deck": [{"id": f"CARD.C{i}"} for i in range(16)]}]},
            {
                "win": True,
                "players": [{"deck": [{"id": f"CARD.C{i}"} for i in range(5)]}],
            },  # too small
            {
                "win": True,
                "players": [{"deck": [{"id": f"CARD.C{i}"} for i in range(30)]}],
            },  # too big
        ]
        import synergy.causal_run as mod

        monkeypatch.setattr(mod, "iter_runs", lambda root, character: iter(runs))
        out = sample_baselines("ironclad", n=5, root=Path("/x"), min_cards=14, max_cards=18)
        # Only the first run qualifies (winning, 16 cards)
        assert len(out) == 1
        assert len(out[0]) == 16
        assert all(not c.startswith("CARD.") for c in out[0])  # prefix stripped

    def test_caps_at_n(self, monkeypatch: pytest.MonkeyPatch) -> None:
        runs = [
            {"win": True, "players": [{"deck": [{"id": f"CARD.C{i}"} for i in range(15)]}]}
            for _ in range(20)
        ]
        import synergy.causal_run as mod

        monkeypatch.setattr(mod, "iter_runs", lambda root, character: iter(runs))
        out = sample_baselines("ironclad", n=3, root=Path("/x"))
        assert len(out) == 3

    def test_empty_when_none_qualify(self, monkeypatch: pytest.MonkeyPatch) -> None:
        import synergy.causal_run as mod

        monkeypatch.setattr(mod, "iter_runs", lambda root, character: iter([]))
        out = sample_baselines("ironclad", n=3, root=Path("/x"))
        assert out == []


# ---------------------------------------------------------------------------
# compute_econ_table / compute_combat_table (serial-executor monkeypatch)
# ---------------------------------------------------------------------------


class _SerialExecutor:
    """Drop-in for ProcessPoolExecutor that runs the initializer + maps in-process.

    Lets us test the orchestration (key formatting, skip-on-None) without
    spawning real subprocesses; the worker globals are set by calling the
    real initializer synchronously.
    """

    def __init__(self, max_workers=None, initializer=None, initargs=()):
        if initializer is not None:
            initializer(*initargs)

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False

    def map(self, fn, iterable, chunksize=1):
        return [fn(x) for x in iterable]


class TestComputeEconTable:
    def test_keys_and_shape(self, monkeypatch: pytest.MonkeyPatch) -> None:
        import synergy.causal_run as mod

        monkeypatch.setattr(mod, "ProcessPoolExecutor", _SerialExecutor)
        monkeypatch.setattr(
            mod,
            "econ_synergy",
            lambda pair, ctx, no_build=False, **_kwargs: EconRow(0.5, 0.3, 0.7, len(ctx)),
        )

        ctxs = [State(deck=["BASH"]), State(deck=["STRIKE_IRONCLAD"])]
        table = compute_econ_table([("A", "B"), ("C", "D")], ctxs, workers=2)
        assert set(table.keys()) == {"A|B", "C|D"}
        assert table["A|B"] == {"median": 0.5, "p25": 0.3, "p75": 0.7, "n": 2}

    def test_empty_pairs(self) -> None:
        assert compute_econ_table([], [], workers=2) == {}


class TestComputeCombatTable:
    def test_skips_failed_pairs(self, monkeypatch: pytest.MonkeyPatch) -> None:
        import synergy.causal_run as mod
        from synergy.causal_combat import CombatRow

        monkeypatch.setattr(mod, "ProcessPoolExecutor", _SerialExecutor)

        def _fake_combat(pair, **kw):
            if pair == ("BAD", "CARD"):
                raise RuntimeError("unknown card factory")
            return CombatRow(median_interaction=2.0, ci=(0.5, 3.0), n=10)

        monkeypatch.setattr(mod, "combat_synergy", _fake_combat)

        table = compute_combat_table(
            [("A", "B"), ("BAD", "CARD")],
            character="silent",
            baselines=[["STRIKE_SILENT"]],
            encounters=["SLIMES_NORMAL"],
            seeds=[1, 2],
            sims=300,
            workers=2,
        )
        assert "A|B" in table
        assert "BAD|CARD" not in table  # failed pair skipped, not aborted
        assert table["A|B"] == {"median_interaction": 2.0, "ci": [0.5, 3.0], "n": 10}

    def test_checkpoint_written(self, monkeypatch, tmp_path) -> None:
        import json as _json

        import synergy.causal_run as mod
        from synergy.causal_combat import CombatRow

        monkeypatch.setattr(mod, "ProcessPoolExecutor", _SerialExecutor)
        monkeypatch.setattr(
            mod,
            "combat_synergy",
            lambda pair, **kw: CombatRow(median_interaction=1.0, ci=(0.0, 2.0), n=4),
        )

        ckpt = tmp_path / "combat_partial.json"
        table = compute_combat_table(
            [("A", "B"), ("C", "D"), ("E", "F")],
            character="ironclad",
            baselines=[["STRIKE_IRONCLAD"]],
            encounters=["SLIMES_NORMAL"],
            seeds=[1],
            sims=300,
            workers=2,
            log_every=1,
            checkpoint_path=ckpt,
        )
        assert ckpt.exists()
        # the checkpoint holds the same completed table as the return value
        assert _json.loads(ckpt.read_text()) == table
        assert set(table) == {"A|B", "C|D", "E|F"}

    def test_empty_pairs(self) -> None:
        assert (
            compute_combat_table(
                [],
                character="ironclad",
                baselines=[["X"]],
                encounters=["E"],
                seeds=[1],
                sims=1,
                workers=2,
            )
            == {}
        )


# ---------------------------------------------------------------------------
# Injection A/B (spec §8)
# ---------------------------------------------------------------------------


class TestPairedWrDelta:
    def test_positive_delta(self) -> None:
        d, ci = paired_wr_delta([True] * 8 + [False] * 2, [True] * 4 + [False] * 6)
        assert d == pytest.approx(0.8 - 0.4)
        assert ci[0] <= d <= ci[1]

    def test_zero_delta(self) -> None:
        d, _ = paired_wr_delta([True, False], [True, False])
        assert d == pytest.approx(0.0)

    def test_empty(self) -> None:
        assert paired_wr_delta([], []) == (0.0, (0.0, 0.0))


class TestParseReportWins:
    def test_orders_by_requested_seeds_and_defaults_missing_to_loss(self, tmp_path) -> None:
        from synergy.causal_run import _parse_report_wins

        report = tmp_path / "report.jsonl"
        report.write_text(
            '{"seed": 2, "won": true}\n'
            "\n"  # blank line ignored
            '{"seed": 0, "won": false}\n'
            '{"seed": 1, "won": true}\n'
        )
        # seed 3 is absent → counts as a loss; order follows the requested seeds.
        assert _parse_report_wins(report, [0, 1, 2, 3]) == [False, True, True, False]


class TestInjectionAb:
    def test_package_beats_control(self) -> None:
        # Mock arm: the strong package wins on even seeds; control on multiples of 3.
        def run_arm(pkg: list[str], seeds: list[int]) -> list[bool]:
            strong = pkg == ["A", "B", "C"]
            return [(s % 2 == 0) if strong else (s % 3 == 0) for s in seeds]

        res = injection_ab(["A", "B", "C"], ["X", "Y", "Z"], list(range(30)), run_arm=run_arm)
        assert isinstance(res, InjectionResult)
        assert res.wr_pkg > res.wr_ctrl
        assert res.wr_delta > 0
        assert res.seeds == 30
