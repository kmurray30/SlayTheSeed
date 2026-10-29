"""Seed-paired batch runner over the sts2_rs engine.

Seed pairing: seed i builds the byte-identical combat (same shuffle,
same enemy rolls) for every strategy in the sweep, so per-seed strategy
deltas are meaningful. Worker processes import this module fresh; the
work unit is one (scenario, strategy, seed-range) chunk.
"""

from __future__ import annotations

import json
import multiprocessing
import os
import threading
import time
from collections.abc import Callable
from concurrent.futures import ProcessPoolExecutor, as_completed
from concurrent.futures.process import BrokenProcessPool
from pathlib import Path
from typing import Any

from simlab.agents import AGENTS, DecisionContext
from simlab.specs import ScenarioSpec, SweepSpec, build_combat
from simlab.telemetry import summarize, write_rows

MAX_ACTIONS = 2000

# Worker-pool start method. The web server (simlab.web.app) imports sts2_rs and
# runs under uvicorn with dozens of threads. Linux's default "fork" copies only
# the forking thread but inherits every lock other threads happen to hold at
# that instant (e.g. the glibc malloc arena lock); a worker that then touches
# such a lock — including during its own interpreter teardown — deadlocks on it
# forever. The futures all complete, but ProcessPoolExecutor.shutdown(wait=True)
# never returns, so run_sweep hangs after its last combat and summary.json is
# never written (observed: FuzzyWurmCrawler sweeps stuck at status="running").
# "spawn" starts a fresh interpreter per worker (fork+exec, no inherited locks)
# and is already the macOS default. Workers are reused for the whole sweep, so
# the extra startup cost is paid only `jobs` times. Requires the simlab entry
# point to guard `main()` behind `if __name__ == "__main__"` (see __main__.py),
# since spawned workers re-import it.
_MP_START_METHOD = "spawn"


class CancelToken:
    """Thread-safe stop signal for a running sweep.

    The web stop endpoint (HTTP thread) calls `request()`; the runner thread
    polls `is_set()` between chunks. `request()` also hard-kills the bound
    executor's workers so the cores free immediately rather than at the next
    chunk boundary — the in-flight futures then surface as `BrokenProcessPool`,
    which the runner treats as a clean stop (see `run_sweep`).
    """

    def __init__(self) -> None:
        self._event = threading.Event()
        self._lock = threading.Lock()
        self._pool: ProcessPoolExecutor | None = None

    def bind(self, pool: ProcessPoolExecutor | None) -> bool:
        """Attach the live executor; return False if already cancelled.

        A False return means `request()` fired before the pool existed (the
        stop-before-spawn race), so the caller must not start dispatching.
        """
        with self._lock:
            if self._event.is_set():
                return False
            self._pool = pool
            return True

    def is_set(self) -> bool:
        return self._event.is_set()

    def request(self) -> None:
        """Signal cancellation and terminate the bound workers immediately."""
        with self._lock:
            self._event.set()
            pool = self._pool
        if pool is None:
            return
        # Drop queued futures, then SIGTERM the live workers so cores free now.
        # `_processes` is a private dict {pid: Process}; ProcessPoolExecutor
        # exposes no public "kill running tasks" API, and this is a localhost
        # dev tool. It may be None/empty if workers haven't spawned yet.
        pool.shutdown(wait=False, cancel_futures=True)
        for proc in (pool._processes or {}).values():
            proc.terminate()


def play_combat(spec: ScenarioSpec, strategy: str, seed: int, **agent_kw: Any) -> dict[str, Any]:
    """Drive one combat to terminal under `strategy`; return a telemetry row.

    Args:
        spec: The scenario configuration (encounter, ascension, deck, etc.).
        strategy: Key into the AGENTS registry.
        seed: Seed for both combat construction and agent RNG.
        **agent_kw: Extra keyword args forwarded to the agent (e.g. mc kwargs).

    Returns:
        A dict with keys: encounter, ascension, deck, strategy, seed, hp_start,
        hp_end, dmg_taken, turns, actions, victory, timed_out, wall_ms.
    """
    import random

    import sts2_rs

    combat = build_combat(spec, seed)
    agent = AGENTS[strategy]
    rng = random.Random(seed)
    hp_start = sts2_rs.state_to_dict(combat)["player"]["hp"]
    t0 = time.perf_counter()
    actions = 0
    # turn number the player was on when combat ended (a fight decided on
    # turn 1 reports 1).
    turns = 1
    stuck = False
    while actions < MAX_ACTIONS:
        terminal, _ = combat.terminal_value()
        if terminal:
            break
        legal = combat.legal_actions()
        if not legal:
            stuck = True
            break
        ctx = DecisionContext.from_combat(combat, legal, rng)
        action = agent(ctx, **agent_kw) if agent_kw else agent(ctx)
        combat.apply(action)
        actions += 1
        if action["action"] == "end_turn":
            turns += 1
    final = sts2_rs.state_to_dict(combat)["player"]["hp"]
    terminal, _ = combat.terminal_value()
    victory = terminal and final > 0
    return {
        "encounter": spec.encounter,
        "ascension": spec.ascension,
        "deck": spec.deck_name,
        "strategy": strategy,
        "seed": seed,
        "hp_start": hp_start,
        "hp_end": max(0, final),
        "dmg_taken": hp_start - max(0, final),
        "turns": turns,
        "actions": actions,
        "victory": victory,
        # timed_out means we hit MAX_ACTIONS; stuck means no legal actions
        # while non-terminal. Both False on a normal terminal exit.
        "timed_out": not terminal and not stuck,
        "stuck": stuck,
        "wall_ms": round((time.perf_counter() - t0) * 1000, 2),
    }


def _error_row(spec: ScenarioSpec, strategy: str, seed: int, error: str) -> dict[str, Any]:
    """Build a telemetry row for a failed trial with the uniform schema."""
    return {
        "encounter": spec.encounter,
        "ascension": spec.ascension,
        "deck": spec.deck_name,
        "strategy": strategy,
        "seed": seed,
        "hp_start": None,
        "hp_end": None,
        "dmg_taken": 0,
        "turns": 0,
        "actions": 0,
        "victory": False,
        "timed_out": False,
        "stuck": False,
        "wall_ms": None,
        "error": error,
    }


def _run_chunk(args: tuple[ScenarioSpec, str, list[int], dict[str, Any]]) -> list[dict[str, Any]]:
    spec, strategy, seeds, agent_kw = args
    out = []
    for s in seeds:
        try:
            out.append(play_combat(spec, strategy, s, **agent_kw))
        except Exception as exc:
            out.append(_error_row(spec, strategy, s, f"{type(exc).__name__}: {exc}"))
    return out


CHUNK = 50


def _chunk_size(strategy: str, n_seeds: int, jobs: int) -> int:
    """Seeds per work unit, sized so the worker pool stays busy to the end.

    Fast heuristic strategies (~ms/combat) use the coarse `CHUNK` to keep
    pickling/dispatch overhead negligible. The `mcts` strategy is ~0.5-1s per
    combat, so a 50-seed chunk is ~30-60s of serial work; when chunks outnumber
    workers only slightly, the last few leave most cores idle for a full chunk.
    For mcts we therefore aim for several chunks per worker (so the tail is one
    short combat, not one long chunk), clamped to [1, CHUNK].

    Args:
        strategy: Strategy key for the cell being chunked.
        n_seeds: Total seeds in the cell.
        jobs: Resolved worker count.

    Returns:
        Chunk size in seeds (>= 1).
    """
    if strategy != "mcts":
        return CHUNK
    target_chunks = max(1, jobs * 4)
    return max(1, min(CHUNK, n_seeds // target_chunks or 1))


def write_spec(out_dir: Path, sweep: SweepSpec) -> Path:
    """Write ``out_dir/spec.json``, leaving an already-identical file untouched.

    ``spec.json`` is what makes a sweep visible to the web UI's listing, and
    that listing sorts by this file's mtime. Two consequences the plain write
    got wrong:

    * The web :class:`~simlab.web.jobs.JobManager` writes the spec at SUBMIT
      time so a just-POSTed sweep is listable straight away; :func:`run_sweep`
      then re-writes the same bytes when its worker thread finally runs. A
      blind write would restamp the mtime with that arbitrarily-later moment
      and reorder the list, so an unchanged spec is left alone.
    * A CLI sweep (``just sim``) never goes through the JobManager, so this is
      still the only writer on that path.

    Returns:
        The path written (or left in place).
    """
    spec_path = out_dir / "spec.json"
    payload = json.dumps(sweep.to_json(), indent=2)
    try:
        if spec_path.read_text(encoding="utf-8") == payload:
            return spec_path
    except OSError:
        pass  # missing or unreadable — write it
    spec_path.write_text(payload, encoding="utf-8")
    return spec_path


def run_sweep(
    sweep: SweepSpec,
    out_dir: Path,
    jobs: int = 0,
    progress: Callable[[int, int], None] | None = None,
    cancel: CancelToken | None = None,
) -> Path:
    """Run the full sweep; write spec.json, rows.jsonl, summary.json into out_dir.

    Args:
        sweep: Full sweep specification (encounters x ascensions x strategies x seeds).
        out_dir: Directory to write output files into (created if needed).
        jobs: Worker count; 0 means os.cpu_count(). Use 1 for single-process.
        progress: Optional callback(done, total) called after each chunk completes.
        cancel: Optional stop signal. When tripped the sweep stops dispatching,
            kills in-flight workers, and summarizes only the rows collected so
            far (a partial summary.json). The caller inspects `cancel.is_set()`
            after this returns to decide the final status. Status-agnostic here.

    Returns:
        The resolved out_dir path.
    """
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    write_spec(out_dir, sweep)
    # rows.jsonl is appended to; clear any prior run so re-running into the
    # same out_dir starts clean (matching spec.json/summary.json overwrite).
    rows_path = out_dir / "rows.jsonl"
    rows_path.unlink(missing_ok=True)

    t_start = time.perf_counter()
    jobs = jobs if jobs > 0 else (os.cpu_count() or 1)

    # Strategies that take extra kwargs must be registered here so the runner
    # forwards the right SweepSpec fields to the agent.
    agent_kw_by_strategy: dict[str, dict[str, Any]] = {
        "mcts": {"sims": sweep.mcts_sims, "depth": sweep.mcts_depth},
    }
    chunks: list[tuple[ScenarioSpec, str, list[int], dict[str, Any]]] = []
    for cell in sweep.cells():
        kw = agent_kw_by_strategy.get(cell.strategy, {})
        seeds = list(range(sweep.n_seeds))
        step = _chunk_size(cell.strategy, len(seeds), jobs)
        for i in range(0, len(seeds), step):
            chunks.append((cell.scenario, cell.strategy, seeds[i : i + step], kw))

    total = sum(len(c[2]) for c in chunks)
    done = 0
    all_rows: list[dict[str, Any]] = []
    if jobs == 1:
        for c in chunks:
            if cancel is not None and cancel.is_set():
                break
            rows = _run_chunk(c)
            write_rows(rows_path, rows)
            all_rows.extend(rows)
            done += len(rows)
            if progress:
                progress(done, total)
    else:
        ctx = multiprocessing.get_context(_MP_START_METHOD)
        with ProcessPoolExecutor(max_workers=jobs, mp_context=ctx) as pool:
            # bind() returns False if a stop landed before the pool existed; then
            # we skip dispatch entirely and fall through to the partial summary.
            if cancel is None or cancel.bind(pool):
                try:
                    fut_to_chunk = {pool.submit(_run_chunk, c): c for c in chunks}
                    for fut in as_completed(fut_to_chunk):
                        if cancel is not None and cancel.is_set():
                            break
                        spec, strategy, seeds, _ = fut_to_chunk[fut]
                        try:
                            rows = fut.result()
                        except BrokenProcessPool:
                            # request() terminated the workers: a clean stop,
                            # not a crash — drop this chunk and bail out.
                            if cancel is not None and cancel.is_set():
                                break
                            raise
                        except Exception as exc:
                            # Worker crashed mid-chunk: reconstruct per-seed
                            # error rows so every requested seed still produces
                            # a row.
                            err = f"{type(exc).__name__}: {exc}"
                            rows = [_error_row(spec, strategy, s, err) for s in seeds]
                        write_rows(rows_path, rows)
                        all_rows.extend(rows)
                        done += len(rows)
                        if progress:
                            progress(done, total)
                except (BrokenProcessPool, RuntimeError):
                    # A stop landing mid-dispatch breaks the pool, so the
                    # `pool.submit` above raises RuntimeError("cannot schedule
                    # new futures after shutdown") or BrokenProcessPool. That's
                    # the requested cancellation, not a failure — keep the
                    # partial. Re-raise anything not caused by a stop.
                    if cancel is None or not cancel.is_set():
                        raise

    elapsed = time.perf_counter() - t_start
    summary = summarize(all_rows)
    # Core-time throughput (per-core speed) comes from the rows; layer on the
    # wall-clock view, which only run_sweep can compute since it alone knows the
    # elapsed time and worker count. combats/actions/turns-per-second here are
    # sweep-wide (they reflect the parallelism); parallel_efficiency is the
    # achieved speedup divided by `jobs` (1.0 = every worker busy the whole run,
    # lower means cores idled — e.g. on the chunk tail).
    tp = summary.get("throughput", {})
    core_s = tp.get("core_seconds", 0.0)
    wrate = (lambda n: round(n / elapsed, 2)) if elapsed > 0 else (lambda _n: 0.0)
    tp["wall"] = {
        "elapsed_s": round(elapsed, 3),
        "jobs": jobs,
        "combats_per_s": wrate(tp.get("combats", 0)),
        "actions_per_s": wrate(tp.get("actions", 0)),
        "turns_per_s": wrate(tp.get("turns", 0)),
        "parallel_efficiency": round(core_s / elapsed / jobs, 3) if elapsed > 0 else 0.0,
    }
    summary["throughput"] = tp
    (out_dir / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    return out_dir
