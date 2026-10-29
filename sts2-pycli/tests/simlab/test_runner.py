import json
import multiprocessing

from simlab import runner
from simlab.runner import play_combat, run_sweep
from simlab.specs import ScenarioSpec, SweepSpec, build_combat


def test_play_combat_row_shape() -> None:
    spec = ScenarioSpec(encounter="MAWLER_NORMAL")
    row = play_combat(spec, strategy="min_overblock", seed=5)
    assert row["victory"] in (True, False)
    assert row["dmg_taken"] >= 0
    assert row["turns"] >= 1
    assert row["strategy"] == "min_overblock"
    assert row["seed"] == 5


def test_play_combat_deterministic() -> None:
    spec = ScenarioSpec(encounter="MAWLER_NORMAL")
    a = play_combat(spec, strategy="random", seed=9)
    b = play_combat(spec, strategy="random", seed=9)
    a.pop("wall_ms"), b.pop("wall_ms")
    assert a == b


def test_seed_pairing_same_opening_hand() -> None:
    import sts2_rs

    spec = ScenarioSpec(encounter="MAWLER_NORMAL")
    h1 = [c["id"] for c in sts2_rs.state_to_dict(build_combat(spec, 33))["deck"]["hand"]]
    h2 = [c["id"] for c in sts2_rs.state_to_dict(build_combat(spec, 33))["deck"]["hand"]]
    assert h1 == h2


def test_run_sweep_writes_telemetry(tmp_path) -> None:
    sw = SweepSpec(
        name="micro",
        encounters=["MAWLER_NORMAL"],
        ascensions=[0],
        strategies=["random", "min_overblock"],
        n_seeds=4,
    )
    out = run_sweep(sw, out_dir=tmp_path, jobs=1)
    rows = [json.loads(line) for line in (out / "rows.jsonl").read_text().splitlines()]
    assert len(rows) == 2 * 4  # strategies x seeds
    summary = json.loads((out / "summary.json").read_text())
    key = "MAWLER_NORMAL/0/ironclad_starter/min_overblock"
    assert key in summary["buckets"]
    assert summary["buckets"][key]["n"] == 4
    assert (out / "spec.json").exists()


def test_run_sweep_multiprocess(tmp_path) -> None:
    sw = SweepSpec(
        name="mp",
        encounters=["MAWLER_NORMAL"],
        ascensions=[0],
        strategies=["random"],
        n_seeds=8,
    )
    out = run_sweep(sw, out_dir=tmp_path, jobs=2)
    rows = [json.loads(line) for line in (out / "rows.jsonl").read_text().splitlines()]
    assert len(rows) == 8
    assert {r["seed"] for r in rows} == set(range(8))
    assert not any("error" in r for r in rows)


def test_chunk_size_mcts_smaller_than_heuristic() -> None:
    from simlab.runner import CHUNK, _chunk_size

    # Heuristics keep the coarse chunk; per-combat cost is ~ms.
    assert _chunk_size("random", 1000, 24) == CHUNK
    # MCTS aims for several chunks per worker so the tail can't idle cores.
    assert _chunk_size("mcts", 1000, 24) < CHUNK
    assert _chunk_size("mcts", 1000, 24) >= 1
    # Never zero, even when seeds < target chunk count.
    assert _chunk_size("mcts", 3, 24) == 1


def test_run_sweep_summary_has_throughput(tmp_path) -> None:
    sw = SweepSpec(
        name="tp",
        encounters=["MAWLER_NORMAL"],
        ascensions=[0],
        strategies=["random"],
        n_seeds=4,
    )
    out = run_sweep(sw, out_dir=tmp_path, jobs=2)
    summary = json.loads((out / "summary.json").read_text())
    tp = summary["throughput"]
    assert tp["combats"] == 4
    assert tp["actions"] > 0
    assert tp["wall"]["jobs"] == 2
    assert tp["wall"]["elapsed_s"] > 0
    assert tp["wall"]["parallel_efficiency"] >= 0


def test_cancel_token_request_sets_and_blocks_bind() -> None:
    """request() flips is_set(); a bind() after request() is refused.

    The refusal handles the stop-before-pool-spawn race: if the HTTP stop lands
    before run_sweep has created its executor, bind() returns False so the
    runner skips dispatch entirely instead of starting work it must immediately
    kill.
    """
    from simlab.runner import CancelToken

    tok = CancelToken()
    assert not tok.is_set()
    # A fresh token binds happily (None stands in for a real executor here).
    assert tok.bind(None) is True
    tok.request()
    assert tok.is_set()
    # Once cancelled, a later bind is refused.
    assert tok.bind(None) is False


def test_run_sweep_honors_precancelled_token(tmp_path) -> None:
    """A token cancelled before run_sweep starts yields a clean, empty partial.

    No chunk runs (single-process loop breaks on the first is_set() check), but
    spec.json/summary.json are still written so the sweep is browsable, and the
    token stays set so the caller can mark it "cancelled".
    """
    from simlab.runner import CancelToken

    sw = SweepSpec(
        name="precancel",
        encounters=["MAWLER_NORMAL"],
        ascensions=[0],
        strategies=["random"],
        n_seeds=8,
    )
    tok = CancelToken()
    tok.request()
    out = run_sweep(sw, out_dir=tmp_path, jobs=1, cancel=tok)
    assert tok.is_set()
    assert (out / "spec.json").exists()
    summary = json.loads((out / "summary.json").read_text())
    assert summary["buckets"] == {}
    # No rows were collected.
    rows_path = out / "rows.jsonl"
    assert not rows_path.exists() or rows_path.read_text() == ""


def test_worker_pool_avoids_fork_start_method() -> None:
    """The multiprocess pool must not use "fork".

    Forking the multithreaded uvicorn server inherits locks held by other
    threads; a worker deadlocks on one during teardown and
    ProcessPoolExecutor.shutdown(wait=True) hangs forever (summary.json never
    written, sweep stuck at status="running"). The start method must be a
    fork-free one the platform actually supports.
    """
    assert runner._MP_START_METHOD != "fork"
    assert runner._MP_START_METHOD in multiprocessing.get_all_start_methods()
    # get_context must accept it (guards against a typo'd method name).
    multiprocessing.get_context(runner._MP_START_METHOD)
