"""A crashed seed is MISSING DATA, not a loss (GH #545).

``play_batch`` writes ``won: false`` on a run that raised, exactly as it does
on a run that lost. The probe used to divide wins by *every* row, so a cell
whose workers died read as a win-rate collapse rather than as a broken
measurement.

That is not hypothetical. On 2026-08-26 a ``just sim-engine`` rebuild swapped
the extension module out from under a running probe: 91 of 100 seeds raised
``ModuleNotFoundError: No module named 'sts2_rs'`` and the cell reported 8.0%
sitting between neighbouring ascensions at 75%. Pooled into its arm, that one
cell flipped the sign of the headline result (+4.60pp → −8.31pp).

These tests assert on the reported RATE and the exit code, because those are
what a downstream reader and a shell script actually consume.
"""

from __future__ import annotations

import pytest

probe = pytest.importorskip("ppo.probe")
workers = pytest.importorskip("ppo.workers")

_ARGV = ["--policy", "random", "--seeds", "4", "--procs", "1"]


def _row(seed: int, *, won: bool = False, error: str | None = None) -> dict:
    r: dict = {
        "seed": seed,
        "won": won,
        "outcome": "error" if error else ("win" if won else "death"),
        "resolution_counts": {"map_choice/path/net": 1},
    }
    if error:
        r["error"] = error
    return r


def _run(monkeypatch, rows: list[dict], capsys) -> tuple[int, str]:
    # `main` imports play_batch from ppo.workers at call time, so the
    # patch has to land on the source module, not on ppo.probe.
    monkeypatch.setattr(workers, "play_batch", lambda *a, **k: rows)
    rc = probe.main(_ARGV)
    return rc, capsys.readouterr().out


def test_errored_seeds_are_excluded_from_the_win_rate_denominator(monkeypatch, capsys) -> None:
    """The 8%-vs-88.9% bug, in miniature.

    Two of four seeds crashed and both survivors won. The honest rate over
    what was actually measured is 2/2; scoring the crashes as losses gives
    2/4 — a 50pp error produced by the harness, not the policy.
    """
    rows = [
        _row(0, won=True),
        _row(1, won=True),
        _row(2, error="ModuleNotFoundError: No module named 'sts2_rs'"),
        _row(3, error="ModuleNotFoundError: No module named 'sts2_rs'"),
    ]
    rc, out = _run(monkeypatch, rows, capsys)

    assert "win rate: 2/2 = 1.000" in out, out
    assert "2/4 = 0.500" not in out
    assert rc != 0, "a cell missing half its seeds must not exit success"


def test_a_crashed_cell_says_so_loudly_and_names_the_downstream_hazard(monkeypatch, capsys) -> None:
    """The warning has to reach whoever pairs the JSONL, not just the console.

    The failure was never that nobody could tell — the old output did list the
    error rows further down. It was that the headline number looked like a
    finding, so nobody read further.
    """
    rows = [_row(0, won=True), _row(1, error="boom")]
    rc, out = _run(monkeypatch, rows, capsys)

    assert "SEEDS CRASHED" in out
    assert "1/2" in out
    assert "`error`" in out, "must tell the reader which key to filter on"
    assert rc != 0


def test_a_clean_cell_is_unchanged_and_exits_zero(monkeypatch, capsys) -> None:
    """The guard must not fire on the normal path.

    Without this, every assertion above could pass with the probe simply
    failing all the time.
    """
    rows = [_row(0, won=True), _row(1, won=False)]
    rc, out = _run(monkeypatch, rows, capsys)

    assert "win rate: 1/2 = 0.500" in out
    assert "SEEDS CRASHED" not in out
    assert "PROBE INCOMPLETE" not in out
    assert rc == 0
