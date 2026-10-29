#!/usr/bin/env python3
"""Play full STS2 runs against the sts2-rs (Rust) engine via PyO3.

Mirrors `play_full_run.py`'s CLI surface (N character) but drives
`sts2_rs.RunManager` directly — no C# subprocess, no .NET dependency.
Useful for fast offline benchmarks, training rollouts, and any harness
that doesn't need the real game's rendering / animation state.

Usage::

    python python/play_full_run_rust.py [num_runs] [character] [policy] [options]

Defaults: ``num_runs=5``, ``character=Ironclad``, ``policy=random``.

Policies:

- ``random`` — uniform-random everywhere (Rust-side loop, fastest).
- ``random-pyloop`` — same but the loop lives in Python (per-step hooks).
- ``heuristic`` — heuristic out-of-combat decisions (path / rewards /
  shop / rest), uniform-random combat. Isolates the run-policy's value.
- ``mcts`` — MCTS per combat decision (potions + pending choices
  included; ``--boss-iters`` on boss rooms), heuristic out-of-combat
  decisions, card rewards picked by simulating the pinned act-boss
  fight per candidate (``--reward-sims``; 0 disables). Each run is
  recorded as a workbench-readable JSONL log under ``--record-dir``
  (open via ``just workbench`` → MCTS Runs, or ``python -m
  workbench.report <log>``).

The run-level policy itself lives in ``run_policy.py`` so the workbench
recorder can share it.
"""

from __future__ import annotations

import argparse
import random
import sys
import time
from pathlib import Path
from typing import Any

try:
    import sts2_rs
except ImportError:
    sys.exit(
        "❌ sts2_rs not installed in this venv. From ../sts2-rs, run:\n"
        "    VIRTUAL_ENV=$PWD/../sts2-pycli/.venv maturin develop --release "
        "-m crates/sts2-py/Cargo.toml"
    )

from run_policy import heuristic_run_action, to_workbench_verb
from run_stepper import RunStepper, hash_seed

_DEFAULT_LOG_DIR = str(Path(__file__).resolve().parent.parent / "logs")

# Re-exported for back-compat; the canonical implementation lives in
# ``run_stepper`` so the workbench's faithful start shares the exact mapping.
_hash_seed = hash_seed


def _run_result(rm: Any, seed: int, steps: int, elapsed: float, **extra: Any) -> dict[str, Any]:
    return {
        "victory": rm.player_won,
        "seed": seed,
        "steps": steps,
        "act": rm.act_index,
        "floor": rm.total_floor(),
        "hp": rm.current_hp,
        "gold": rm.gold,
        "phase": rm.phase(),
        "timeout": not rm.is_over(),
        "elapsed_s": elapsed,
        **extra,
    }


def play_run_random(
    seed: int,
    character: str,
    *,
    max_steps: int = 200_000,
    verbose: bool = True,
) -> dict[str, Any]:
    """Drive one full run with uniform-random over available_actions.

    Returns a dict shaped to match `play_full_run.py`'s schema where
    overlapping fields exist (victory / seed / steps / act / floor /
    timeout) so downstream aggregators can consume both transparently.
    """
    rm = sts2_rs.RunManager(seed=seed, character=character, neow_start=True)
    t0 = time.perf_counter()
    res = rm.random_playout(max_steps=max_steps, policy_seed=seed)
    elapsed = time.perf_counter() - t0
    victory = bool(res["player_won"])
    timeout = not res["terminated"]
    if verbose:
        status = "WIN" if victory else ("TIMEOUT" if timeout else "LOSS")
        print(
            f"  {status} seed={seed} steps={res['steps']} "
            f"act={res['act_index']} hp={res['final_hp']} "
            f"gold={res['final_gold']} ({elapsed * 1000:.1f}ms)"
        )
    return {
        "victory": victory,
        "seed": seed,
        "steps": res["steps"],
        "act": res["act_index"],
        "floor": rm.total_floor(),
        "hp": res["final_hp"],
        "gold": res["final_gold"],
        "phase": res["final_phase"],
        "timeout": timeout,
        "elapsed_s": elapsed,
    }


def play_run_mcts(
    seed: int,
    character: str,
    *,
    max_steps: int = 200_000,
    mcts_iters: int = 1000,
    boss_mcts_iters: int = 10_000,
    reward_sims: int | None = 3000,
    record_dir: str | None = _DEFAULT_LOG_DIR,
    heuristic_ooc: bool = True,
    threads: int = 8,
    verbose: bool = True,
) -> dict[str, Any]:
    """Drive a full run with MCTS in combat + heuristics elsewhere.

    Combat decisions run ``sts2_rs.mcts_recommend`` on the snapshotted
    ``CombatState`` (``mcts_iters`` sims; ``boss_mcts_iters`` on boss
    rooms — the act1_replay sweeps show MCTS reaches the Act-1 boss
    healthy and loses there). Card rewards are picked by simulating the
    pinned act-boss fight per candidate at ``reward_sims`` sims/decision
    (``None``/0 → cheap heuristic). Other out-of-combat decisions use
    ``heuristic_run_action`` (``heuristic_ooc=False`` → uniform-random).

    When ``record_dir`` is set, the run is written as a workbench JSONL
    log (same schema as ``workbench.record``): per combat decision a
    ``state`` + ``mcts`` + ``action`` record, per run a ``meta`` header
    and a ``summary`` trailer. Open it on the workbench MCTS Runs page
    or with ``python -m workbench.report <log>``.
    """
    from workbench.record import (
        _combat_state_dict,
        _load_card_loc,
        _mcts_record,
    )

    rm = sts2_rs.RunManager(seed=seed, character=character, neow_start=True)
    # The decision policy lives in RunStepper. The workbench's faithful "play
    # to end" does NOT reuse it (it drives the native HeuristicRunAgent and
    # shares only `hash_seed`), so these runs are not reproduced there
    # move-for-move. `neow_start=True`
    # opens on the Neow event like the real game / run-AI arena; skipping it
    # forfeits the opening blessing and shifts the act-1 event cursor.
    stepper = RunStepper(
        rm,
        seed=seed,
        mcts_iters=mcts_iters,
        boss_mcts_iters=boss_mcts_iters,
        reward_sims=reward_sims,
        threads=threads,
        heuristic_ooc=heuristic_ooc,
        verbose=verbose,
    )

    logger = None
    view = None  # RustGame wrapper used ONLY to serialize states for the log
    loc: dict[str, str] = {}
    if record_dir:
        from game_log import GameLogger
        from workbench.backends.rust_game import RustGame

        view = RustGame()
        view.rm = rm
        view.character = character
        loc = _load_card_loc()
        logger = GameLogger(character=character, seed=str(seed), log_dir=record_dir, ascension=0)
        logger.log_meta_extra(
            {
                "fixture_stem": f"{character}-{seed}",
                "build_id": None,
                "encounter": None,
                "sims": mcts_iters,
                "boss_sims": boss_mcts_iters,
                "reward_sims": reward_sims or 0,
                "policy": "mcts+heuristic" if heuristic_ooc else "mcts+random",
            }
        )

    t0 = time.perf_counter()
    steps = 0
    decision_idx = 0
    last_round = 0
    try:
        while steps < max_steps and not rm.is_over():
            decision = stepper.decide()
            if decision is None:
                break
            action = decision.action
            if decision.is_combat:
                if logger is not None:
                    state = _combat_state_dict(decision.combat_cs, character, loc)
                    last_round = state.get("round", last_round)
                    logger.log_state(state)
                    if decision.combat_result is not None:
                        mcts_rec, _ = _mcts_record(
                            decision.combat_result, state, decision.combat_cfg, decision_idx
                        )
                        logger.log_mcts(mcts_rec)
                decision_idx += 1
            elif logger is not None and view is not None:
                # Canonical RustGame serialization — the same state shape
                # the SPA's live mode renders (map_select choices,
                # card_reward cards, shop inventory, …), so the replay
                # screens and /api/map work. "round" feeds
                # workbench.report's T<n> header (= run floor here).
                state = view.get_state()
                state["round"] = rm.total_floor()
                if decision.reward_sim:
                    state["reward_sim"] = decision.reward_sim
                if state.get("decision") == "map_select":
                    # Full per-act graph (nodes carry visited/current
                    # flags) — /api/map groups these into `graphs` so the
                    # SPA renders the real SVG map with the taken path
                    # instead of the reconstructed floor-row fallback,
                    # and act transitions swap to the new act's graph.
                    state["full_map"] = rm.full_map()
                logger.log_state(state)
            if logger is not None:
                verb, vargs = to_workbench_verb(rm, action)
                logger.log_action({"action": verb, "args": vargs})
            result_str = rm.take_action(action)
            if result_str == "not_implemented":
                raise RuntimeError(f"hit not_implemented at phase={rm.phase()} action={action}")
            stepper.step_taken()
            steps += 1
    finally:
        if logger is not None:
            if view is not None and rm.is_over():
                # Trailing game_over record so the SPA's GameOver screen and
                # the timeline's terminal step render.
                try:
                    logger.log_state(view.get_state() | {"round": rm.total_floor()})
                except Exception:
                    pass
            logger.log_summary(
                {
                    "final_won": rm.player_won,
                    "final_turn": last_round,
                    "final_player_hp": rm.current_hp,
                    "final_player_max_hp": rm.max_hp,
                }
            )
            logger.close()

    elapsed = time.perf_counter() - t0
    if verbose:
        victory = rm.player_won
        timeout = not rm.is_over()
        status = "WIN" if victory else ("TIMEOUT" if timeout else "LOSS")
        print(
            f"  {status} seed={seed} steps={steps} act={rm.act_index} "
            f"hp={rm.current_hp} gold={rm.gold} ({elapsed:.1f}s, "
            f"mcts={mcts_iters}/{boss_mcts_iters}-boss, reward-sims={reward_sims or 0})"
        )
        if logger is not None:
            print(f"  log: {logger.path}")
    return _run_result(rm, seed, steps, elapsed, log=logger.path if logger else None)


def play_run_heuristic(
    seed: int,
    character: str,
    *,
    max_steps: int = 200_000,
    verbose: bool = True,
) -> dict[str, Any]:
    """Heuristic out-of-combat policy + uniform-random combat.

    Cheap A/B baseline that isolates the run-level policy's value from
    MCTS combat strength (compare vs ``random`` / ``mcts``)."""
    rm = sts2_rs.RunManager(seed=seed, character=character, neow_start=True)
    rng = random.Random(seed ^ 0xC0FFEE)
    t0 = time.perf_counter()
    steps = 0
    while steps < max_steps and not rm.is_over():
        acts = rm.available_actions()
        if not acts:
            raise RuntimeError(
                f"stalled at phase={rm.phase()} step={steps}: available_actions() returned empty"
            )
        if rm.phase() == "combat":
            action = rng.choice(acts)
        else:
            action = heuristic_run_action(rm, acts, rng)
        result = rm.take_action(action)
        if result == "not_implemented":
            raise RuntimeError(f"hit not_implemented at phase={rm.phase()} action={action}")
        steps += 1
    elapsed = time.perf_counter() - t0
    if verbose:
        victory = rm.player_won
        timeout = not rm.is_over()
        status = "WIN" if victory else ("TIMEOUT" if timeout else "LOSS")
        print(
            f"  {status} seed={seed} steps={steps} act={rm.act_index} "
            f"hp={rm.current_hp} gold={rm.gold} ({elapsed * 1000:.1f}ms)"
        )
    return _run_result(rm, seed, steps, elapsed)


def play_run_pyloop(
    seed: int,
    character: str,
    *,
    max_steps: int = 200_000,
    verbose: bool = True,
) -> dict[str, Any]:
    """Same as play_run_random but the loop lives in Python so callers
    can hook per-step instrumentation (telemetry, alt policies). Use
    this when you need per-step state; `play_run_random` is faster
    because the loop stays in Rust."""
    rm = sts2_rs.RunManager(seed=seed, character=character, neow_start=True)
    rng = random.Random(seed)
    t0 = time.perf_counter()
    steps = 0
    while steps < max_steps and not rm.is_over():
        acts = rm.available_actions()
        if not acts:
            raise RuntimeError(
                f"stalled at phase={rm.phase()} step={steps}: available_actions() returned empty"
            )
        action = rng.choice(acts)
        result = rm.take_action(action)
        if result == "not_implemented":
            raise RuntimeError(f"hit not_implemented at phase={rm.phase()} action={action}")
        steps += 1
    elapsed = time.perf_counter() - t0
    if verbose:
        victory = rm.player_won
        timeout = not rm.is_over()
        status = "WIN" if victory else ("TIMEOUT" if timeout else "LOSS")
        print(
            f"  {status} seed={seed} steps={steps} act={rm.act_index} "
            f"hp={rm.current_hp} gold={rm.gold} ({elapsed * 1000:.1f}ms)"
        )
    return _run_result(rm, seed, steps, elapsed)


def main() -> int:
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    ap.add_argument("num_runs", nargs="?", type=int, default=5)
    ap.add_argument("character", nargs="?", default="Ironclad")
    ap.add_argument(
        "policy",
        nargs="?",
        default="random",
        choices=["random", "random-pyloop", "heuristic", "mcts"],
    )
    ap.add_argument("--iters", type=int, default=1000, help="MCTS sims per combat decision")
    ap.add_argument(
        "--boss-iters", type=int, default=10_000, help="MCTS sims per decision in boss rooms"
    )
    ap.add_argument(
        "--reward-sims",
        type=int,
        default=3000,
        help="sims/decision for the boss-fight card-reward simulation (0 disables)",
    )
    ap.add_argument(
        "--record-dir",
        default=_DEFAULT_LOG_DIR,
        help="workbench JSONL log dir for mcts runs ('' disables recording)",
    )
    ap.add_argument("--max-steps", type=int, default=200_000)
    args = ap.parse_args()

    drivers = {
        "random": play_run_random,
        "random-pyloop": play_run_pyloop,
        "heuristic": play_run_heuristic,
        "mcts": play_run_mcts,
    }
    driver = drivers[args.policy]
    extra: dict[str, Any] = {"max_steps": args.max_steps}
    if args.policy == "mcts":
        extra.update(
            mcts_iters=args.iters,
            boss_mcts_iters=args.boss_iters,
            reward_sims=args.reward_sims or None,
            record_dir=args.record_dir or None,
        )

    print(
        f"Playing {args.num_runs} runs as {args.character} (engine=sts2_rs, policy={args.policy})"
    )
    print("=" * 64)

    results: list[dict[str, Any]] = []
    t_start = time.perf_counter()
    for i in range(args.num_runs):
        seed = _hash_seed(f"run_{i + 1}")
        print(f"\n--- Run {i + 1}/{args.num_runs} (seed: {seed}) ---")
        try:
            results.append(driver(seed, args.character, verbose=True, **extra))
        except Exception as e:
            print(f"  EXCEPTION: {e}")
            results.append({"victory": False, "seed": seed, "error": str(e), "timeout": False})
    t_total = time.perf_counter() - t_start

    print("\n" + "=" * 64)
    print("SUMMARY")
    print("=" * 64)
    wins = sum(1 for r in results if r.get("victory"))
    completed = sum(1 for r in results if not r.get("timeout") and "error" not in r)
    for i, r in enumerate(results):
        if "error" in r:
            status = "ERROR"
        elif r.get("victory"):
            status = "WIN"
        elif r.get("timeout"):
            status = "TIMEOUT"
        else:
            status = "LOSS"
        print(
            f"  Run {i + 1}: {status} | seed={r.get('seed')} "
            f"steps={r.get('steps', '-')} act={r.get('act', '-')} "
            f"floor={r.get('floor', '-')}"
        )
    logs = [r["log"] for r in results if r.get("log")]
    if logs:
        print(f"\nRecorded {len(logs)} workbench logs in {Path(logs[0]).parent}/")
        print("  open: just workbench (MCTS Runs page), or python -m workbench.report <log>")
    print(
        f"\nWins: {wins}/{args.num_runs}, Completed: {completed}/{args.num_runs}  "
        f"({t_total:.2f}s total, {args.num_runs / t_total:.1f} runs/s)"
    )
    return 0 if completed == args.num_runs else 1


if __name__ == "__main__":
    sys.exit(main())
