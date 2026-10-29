"""Eval harness for the real-game "SpireMentor" mod (real-game bridge phase 3).

Runs N sessions per requested ``--policy`` against the live bridge, one
bridge TCP connection per run (``play_one_session`` exits on
``run_complete`` and the caller reconnects fresh for the next run -- the
mod's single-client server accepts a new connection once the previous one
disconnects, same as every other ``realgame_*`` driver), aggregates each
run's outcome (result/floor/act/decision count/fallback count/duration)
into a win-rate/depth comparison table across policies, and writes both a
machine-readable and a human-readable report.

Deliberately does **not** reimplement the pick-functions: ``--policy ppo``
reuses :func:`realgame_ppo.pick_noncombat_action_ppo` (+ combat via
:func:`realgame_mcts.pick_combat_action_mcts` or :func:`realgame_random.
pick_combat_action`, see ``--combat-policy``), ``--policy mcts`` reuses
:func:`realgame_mcts.pick_combat_action_mcts` for combat and
:func:`realgame_random.choose_action` elsewhere (mirrors
:mod:`realgame_mcts`'s own driver exactly), ``--policy random`` reuses
:func:`realgame_random.choose_action`/:func:`realgame_random.
pick_combat_action` for everything (mirrors :mod:`realgame_random`'s own
driver). Only the per-run session loop + outcome aggregation is new.

Usage (against a live bridge)::

    uv run python python/realgame_eval.py --policy random mcts ppo \\
        --runs 10 --character IRONCLAD --seed-base 1000

Self-check, no live bridge required (per the phase-3 task brief -- "just
make ``--runs 0 --dry-run`` style self-checks work"; the coordinator runs
the real thing)::

    uv run python python/realgame_eval.py --policy random mcts ppo \\
        --runs 0 --dry-run

``--dry-run`` skips every network call entirely and exercises only the
aggregation + report-writing pipeline (each requested policy appears in
the comparison table with ``runs: 0``, not silently omitted).

Report layout: ``logs/realgame_eval/<UTC timestamp>/report.json`` (full
machine-readable report: meta + per-run rows + per-policy aggregate) and
``report.md`` (the same, rendered as markdown tables) -- override the
parent directory with ``--out-dir``.
"""

from __future__ import annotations

import argparse
import dataclasses
import json
import logging
import random
import sys
import time
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from combat_mirror import CombatMirror, policy_from_env
from realgame_bridge import DEFAULT_HOST, DEFAULT_PORT, RealGameBridgeClient
from realgame_mcts import (
    DEFAULT_SIMS_BOSS,
    DEFAULT_SIMS_REGULAR,
    chosen_map_node_type,
    combat_sims_for,
    pick_card_select_mirrored,
    pick_combat_action_mcts,
)
from realgame_ppo import PpoPolicyCache, pick_noncombat_action_ppo
from realgame_random import _TERMINAL_TYPES, JsonlLogger, choose_action, pick_combat_action
from sts2_hint.recommender import RecommendConfig

logger = logging.getLogger(__name__)

POLICIES = ("ppo", "mcts", "random")


@dataclasses.dataclass
class RunOutcome:
    """Aggregable per-run result -- one bridge session."""

    run_index: int
    policy: str
    seed: int | None
    character: str | None
    ascension: int | None
    result: str
    won: bool
    floor: int | None
    act: int | None
    decisions: int
    fallback_count: int
    duration_s: float
    error: str | None = None
    # Soft-drop tally from the sts2_hint adapter over this run's combat
    # translations ({kind: count, "total": N}). Non-empty => the MCTS searched
    # a DEGRADED state (an entity it couldn't map was elided). Silent at INFO,
    # so surfacing it here is how a search-fidelity regression stays visible.
    search_drops: dict[str, int] | None = None
    # Seed-locked shadow-engine telemetry (headless_eval --shadow only).
    # ``{alive_floors, combat_searches, divergence: {...} | None}``: how far
    # the lockstep shadow stayed in sync with the DLL wire, how many combat
    # decisions were searched off the shadow's lossless native state, and the
    # first divergence record that retired the shadow to the wire fallback.
    shadow_stats: dict[str, Any] | None = None
    # Persistent-combat-mirror telemetry (:class:`combat_mirror.MirrorStats`
    # as a dict, plus ``tainted``). ``desync_rate_per_1000`` is the fidelity
    # metric that REPLACED "fallback share": with a mirror there is no
    # fallback to take a share of, so the question stops being "how often did
    # we guess?" and becomes "how often did the engine mispredict reality?".
    # ``tainted`` marks a run that hit a divergence under OBSERVE — such a run
    # must never be quoted as a clean measurement.
    mirror_stats: dict[str, Any] | None = None
    # Exceptions the C# world printed to its stderr during this run
    # (``{"count": int, "sample": [...]}``, from ``sts2_client.Game``). None for
    # harnesses with no DLL. Non-zero means the WORLD threw: the throw is
    # swallowed by `TaskHelper.LogTaskExceptions`, so the run keeps going and
    # its `result` looks like an ordinary loss. Until this field existed, that
    # was invisible — see docs/experiments/2026-07-27-rung35-winrate-v20.md.
    world_errors: dict[str, Any] | None = None

    def to_dict(self) -> dict[str, Any]:
        return dataclasses.asdict(self)


def play_one_session(
    host: str,
    port: int,
    policy: str,
    rng: random.Random,
    *,
    run_index: int = 0,
    character: str | None = None,
    ascension: int | None = None,
    seed: int | None = None,
    max_steps: int | None = None,
    mcts_cfg: RecommendConfig | None = None,
    ppo_policies: PpoPolicyCache | None = None,
    temperature: float = 0.0,
    combat_policy: str = "mcts",
    map_explore: bool = False,
    sims_regular: int = DEFAULT_SIMS_REGULAR,
    sims_boss: int = DEFAULT_SIMS_BOSS,
    sims_flat: int | None = None,
    reconnect_attempts: int = 30,
    reconnect_delay: float = 2.0,
    jsonl: JsonlLogger | None = None,
) -> RunOutcome:
    """Play exactly one bridge session (connect -> ... -> ``run_complete``).

    Args:
        policy: ``"ppo"``, ``"mcts"``, or ``"random"`` -- see module
            docstring for which pick-functions each reuses.
        mcts_cfg: Required when ``policy == "mcts"``, or when
            ``policy == "ppo"`` and ``combat_policy == "mcts"``.
        ppo_policies: Required when ``policy == "ppo"``.
        reconnect_attempts/reconnect_delay: Forwarded to
            :class:`~realgame_bridge.RealGameBridgeClient` -- lowered by
            tests to fail fast against a deliberately-closed port instead
            of the ~60s default retry budget.

    Returns:
        The run's :class:`RunOutcome`. A connection error is captured as
        ``result="error: ..."``/``error=<message>`` rather than raised --
        one bad run must not abort the rest of the eval sweep.
    """
    start = time.monotonic()
    step = 0
    fallback_count = 0
    result = "unknown"
    floor: int | None = None
    act: int | None = None
    error: str | None = None
    current_room_type: str | None = None  # type of the map node last walked into
    session: dict[str, Any] = {}  # per-run scratch for pick_noncombat_action_ppo
    # Mirror advance DEFERRED until the world confirms it took the action.
    # PROTOCOL.md section 3a: a rejected action gets an ``error`` message and a
    # re-send of the decision — the world never moved. Advancing the mirror at
    # send time (the pre-2026-08-02 behavior) mirrored an action that never
    # happened, guaranteeing a desync cascade after every ``invalid_index``/
    # ``unplayable_card`` rejection (13 of them across the 20260802T184818Z
    # sweep's 12 runs). ``("engine", action)`` or ``("picks", [..])``; applied
    # at the top of the loop on the first non-error message, discarded on error.
    pending_advance: tuple[str, Any] | None = None
    # Commands the game already REJECTED for an identical combat state, keyed
    # by a content hash that excludes the per-message ids (``request_id``/
    # ``state_id`` change on every re-send). Same contract as headless_eval's
    # ``banned_for_state``: without it a deterministic search re-picks the
    # rejected play forever once the mirror no longer phantom-advances past it.
    banned_for_state: dict[int, set[str]] = {}
    last_sent: tuple[int, str] | None = None  # (combat state key, canonical cmd)

    def _combat_state_key(s: dict[str, Any]) -> int:
        volatile = ("request_id", "state_id")
        return hash(
            json.dumps(
                {k: v for k, v in s.items() if k not in volatile}, sort_keys=True, default=str
            )
        )

    # One persistent engine state per fight. Combat searches run on a clone of
    # it, and a mid-combat card_select is answered off the choice the played
    # card already opened on it. `STS2_MIRROR_OBSERVE=1` downgrades a
    # divergence from a crash to a tagged, logged drop.
    mirror = CombatMirror(
        mcts_cfg or RecommendConfig(),
        policy=policy_from_env(),
        label=f"live-{character}-{seed}",
    )

    try:
        with RealGameBridgeClient(
            host=host,
            port=port,
            reconnect_attempts=reconnect_attempts,
            reconnect_delay=reconnect_delay,
        ) as client:
            while True:
                state = client.receive_state()
                msg_type = state.get("type")

                if msg_type == "pong":
                    continue
                if msg_type == "error":
                    # The world REJECTED the previous action (PROTOCOL.md 3a)
                    # and will re-send the decision: the action never happened,
                    # so the deferred mirror advance is discarded, and the
                    # command is banned for that exact state so the re-search
                    # tries its next-best action instead of looping.
                    pending_advance = None
                    if last_sent is not None:
                        key, cmd = last_sent
                        banned_for_state.setdefault(key, set()).add(cmd)
                        logger.warning(
                            "run %d step %d: bridge rejected %s (%s: %s) - banning for this state",
                            run_index,
                            step,
                            cmd,
                            state.get("error"),
                            state.get("detail"),
                        )
                        last_sent = None
                    if jsonl is not None:
                        jsonl.log_error(state)
                    continue

                # Any non-error message means the world accepted and executed
                # the previous action — advance the mirror exactly once, now.
                if mirror.alive and pending_advance is not None:
                    p_kind, p_payload = pending_advance
                    if p_kind == "engine":
                        mirror.advance(p_payload)
                    else:
                        mirror.advance_through_choice(p_payload)
                pending_advance = None
                last_sent = None

                # Track the MAX seen, not the latest: terminal messages
                # (game_over/run_complete) are sent after run teardown, when
                # the envelope degrades to floor=0/act=1 — taking the last
                # value reported every run as "floor 0" (2026-07-11 eval).
                state_floor = state.get("floor")
                if isinstance(state_floor, int):
                    floor = state_floor if floor is None else max(floor, state_floor)
                state_act = state.get("act")
                if isinstance(state_act, int):
                    act = state_act if act is None else max(act, state_act)

                if msg_type in _TERMINAL_TYPES:
                    step += 1
                    if jsonl is not None:
                        jsonl.log_terminal(step, state)
                    result = state.get("result") or state.get("message") or msg_type
                    if msg_type == "run_complete":
                        break
                    continue

                log_extra: dict[str, Any] | None = None
                engine_action: dict[str, Any] | None = None
                picks: list[int] | None = None
                # The mirror only survives while the game is still showing a
                # fight. Anything else ends it — including an out-of-combat
                # card_select (a rest-site upgrade carries no board), which
                # the PPO surface answers as before.
                in_fight = msg_type == "combat_action" or (
                    msg_type == "card_select" and state.get("combat") is not None
                )
                mirrored = (
                    in_fight
                    and mcts_cfg is not None
                    and (policy == "mcts" or (policy == "ppo" and combat_policy == "mcts"))
                )
                if mirror.alive and not in_fight:
                    mirror.drop("left combat")

                state_key: int | None = None
                if msg_type == "combat_action":
                    if policy == "random":
                        action = pick_combat_action(state, rng)
                    else:
                        assert mcts_cfg is not None, "mcts_cfg required for combat"
                        if mirrored:
                            # Boss rooms get sims_boss (4000), regular fights
                            # sims_regular (1000); matches the realgame_ppo/mcts
                            # drivers (sims_flat overrides both when set).
                            sims = combat_sims_for(
                                current_room_type, sims_regular, sims_boss, sims_flat
                            )
                            state_key = _combat_state_key(state)
                            action, log_extra, engine_action = pick_combat_action_mcts(
                                state,
                                mcts_cfg,
                                rng,
                                sims=sims,
                                mirror=mirror,
                                banned=banned_for_state.get(state_key),
                            )
                        else:
                            action = pick_combat_action(state, rng)
                elif mirrored:
                    # Mid-combat selection, answered off the mirror the played
                    # card already parked on a choice: no replay, no prompt-key
                    # heuristic, no random fallback.
                    assert mcts_cfg is not None
                    action, picks = pick_card_select_mirrored(
                        state,
                        mcts_cfg,
                        mirror,
                        sims=combat_sims_for(current_room_type, sims_regular, sims_boss, sims_flat),
                    )
                elif policy == "ppo":
                    assert ppo_policies is not None, "ppo_policies required for policy='ppo'"
                    action, log_extra = pick_noncombat_action_ppo(
                        state,
                        rng,
                        ppo_policies,
                        temperature=temperature,
                        run_config_character=character,
                        run_config_ascension=ascension,
                        run_config_seed=seed,
                        map_explore=map_explore,
                        session=session,
                    )
                    # Remember the room we're entering so the next combat knows
                    # whether it's the boss (4000 sims) or a regular fight (1000).
                    node_type = chosen_map_node_type(state, action)
                    if node_type is not None:
                        current_room_type = node_type
                else:
                    action = choose_action(
                        state,
                        rng,
                        run_config_character=character,
                        run_config_ascension=ascension,
                        run_config_seed=seed,
                        map_explore=map_explore,
                    )
                    node_type = chosen_map_node_type(state, action)
                    if node_type is not None:
                        current_room_type = node_type

                if log_extra is not None:
                    fallback_count += 1

                # Context for the NEXT decision: a card_select message carries
                # no source info, so pick_noncombat_action_ppo reads which
                # screen preceded it (rest_site -> smith target, shop ->
                # removal target, event -> card grant) from here.
                session["prev_msg_type"] = msg_type

                step += 1
                if jsonl is not None:
                    jsonl.log_step(step, state, action, extra=log_extra)
                client.send_action(action)
                # Stage the mirror advance; it is applied only when the next
                # message proves the world took the action (a rejected action —
                # ``error`` + re-send — never happened in the world, so
                # mirroring it would desync the mirror on purpose).
                if mirror.alive:
                    if engine_action is not None:
                        pending_advance = ("engine", engine_action)
                    elif picks is not None:
                        pending_advance = ("picks", picks)
                if state_key is not None:
                    last_sent = (
                        state_key,
                        json.dumps(
                            {k: v for k, v in action.items() if k != "annotations"},
                            sort_keys=True,
                        ),
                    )

                if max_steps is not None and step >= max_steps:
                    result = "max_steps_reached"
                    break
    except (ConnectionError, TimeoutError) as e:
        error = str(e)
        result = f"error: {e}"
        logger.error("Run %d (%s) failed after %d steps: %s", run_index, policy, step, e)
    except Exception as e:
        # Same contract as headless_eval's session loop: one bad run — a
        # STRICT MirrorDivergence, a pick-function crash — is recorded as an
        # error outcome instead of aborting the whole sweep with no report
        # written. (Until 2026-08-02 only connection errors were caught here,
        # so a single STRICT divergence killed every remaining run's data.)
        error = str(e)
        result = f"error: {e}"
        logger.exception("Run %d (%s) crashed after %d steps", run_index, policy, step)

    return RunOutcome(
        run_index=run_index,
        policy=policy,
        seed=seed,
        character=character,
        ascension=ascension,
        result=result,
        won=(result == "victory"),
        floor=floor,
        act=act,
        decisions=step,
        fallback_count=fallback_count,
        duration_s=time.monotonic() - start,
        error=error,
        mirror_stats={**mirror.stats.as_dict(), "tainted": mirror.tainted},
    )


# ---------------------------------------------------------------------------
# Aggregation + report.
# ---------------------------------------------------------------------------


@dataclasses.dataclass
class PolicyAggregate:
    """Per-policy rollup over its :class:`RunOutcome` rows."""

    policy: str
    runs: int
    wins: int
    win_rate: float
    mean_floor: float
    max_floor: int
    mean_decisions: float
    mean_fallback_count: float
    mean_duration_s: float
    errors: int
    # Persistent-combat-mirror fidelity, pooled over the policy's runs.
    # `desync_rate_per_1000` is DESYNCing reconciles per 1000 reconciles — the
    # metric that REPLACED "fallback share". Under a mirror there is no
    # fallback to take a share of, so the question is no longer "how often did
    # we guess?" but "how often did the engine mispredict reality?". Pooled
    # over raw counts, not averaged over per-run rates, so a 40-decision run
    # cannot outvote a 400-decision one.
    desync_rate_per_1000: float = 0.0
    mirror_reconciles: int = 0
    mirror_injections: int = 0
    # Injections the engine had no parked choice for — see
    # `MirrorStats.unparked_injections`. Each is a guaranteed one-advance
    # misprediction of whatever the screen granted, so this is the size of
    # the "screens the engine does not model" gap.
    mirror_unparked_injections: int = 0
    # Per-stream RNG read-count differences — see `MirrorStats.cursor_drifts`.
    # Recorded rather than raised: a drift says the two engines spent
    # different amounts of randomness on the same step, which is a lead
    # toward a divergence rather than proof of one.
    mirror_cursor_drifts: int = 0
    # Runs that hit a divergence under OBSERVE. Any non-zero value means this
    # row is NOT a clean measurement.
    tainted_runs: int = 0
    # Runs whose C# world threw at least once, and the total error-line count
    # (one throw prints 2-3 lines — a volume signal, not a throw tally).
    # A swallowed exception does not end the run, so these runs are scored as
    # ordinary losses; a non-zero `world_error_runs` means some share of this
    # row's non-wins are the harness failing, not the policy losing.
    #
    # ``None`` means UNINSTRUMENTED — no row in this policy could count, so the
    # renderer must show `n/a`, not `0/0`. See :func:`aggregate`.
    world_error_runs: int | None = 0
    world_errors: int | None = 0

    def to_dict(self) -> dict[str, Any]:
        return dataclasses.asdict(self)


def aggregate(
    outcomes: list[RunOutcome], policies: list[str] | None = None
) -> dict[str, PolicyAggregate]:
    """Roll ``outcomes`` up per policy.

    Args:
        policies: Policies to always include in the result even with zero
            matching outcomes (e.g. a ``--dry-run``/``--runs 0`` self-check
            still wants every requested policy in the comparison table).
    """
    by_policy: dict[str, list[RunOutcome]] = {p: [] for p in (policies or [])}
    for o in outcomes:
        by_policy.setdefault(o.policy, []).append(o)

    result: dict[str, PolicyAggregate] = {}
    for policy, rows in by_policy.items():
        n = len(rows)
        wins = sum(1 for r in rows if r.won)
        floors = [r.floor for r in rows if r.floor is not None]
        result[policy] = PolicyAggregate(
            policy=policy,
            runs=n,
            wins=wins,
            win_rate=(wins / n) if n else 0.0,
            mean_floor=(sum(floors) / len(floors)) if floors else 0.0,
            max_floor=max(floors) if floors else 0,
            mean_decisions=(sum(r.decisions for r in rows) / n) if n else 0.0,
            mean_fallback_count=(sum(r.fallback_count for r in rows) / n) if n else 0.0,
            mean_duration_s=(sum(r.duration_s for r in rows) / n) if n else 0.0,
            errors=sum(1 for r in rows if r.error is not None),
            # `None` on EVERY row means the arm is UNINSTRUMENTED, not clean —
            # `play_one_session` talks to the live game over TCP, never through
            # `sts2_client.Game`, so it has no stderr to classify. Distinguish the
            # two: a rendered `0/0` for an arm that cannot count is the same
            # silently-clean instrument this whole feature exists to stop trusting.
            world_error_runs=(
                sum(1 for r in rows if (r.world_errors or {}).get("count"))
                if any(r.world_errors is not None for r in rows)
                else None
            ),
            world_errors=(
                sum(int((r.world_errors or {}).get("count") or 0) for r in rows)
                if any(r.world_errors is not None for r in rows)
                else None
            ),
            **_mirror_rollup(rows),
        )
    return result


def _mirror_rollup(rows: list[RunOutcome]) -> dict[str, Any]:
    """Pool the per-run :class:`combat_mirror.MirrorStats` into the aggregate."""
    stats = [r.mirror_stats for r in rows if r.mirror_stats]
    reconciles = sum(int(s.get("reconciles") or 0) for s in stats)
    desyncs = sum(int(s.get("desyncs") or 0) for s in stats)
    return {
        "desync_rate_per_1000": round(1000.0 * desyncs / reconciles, 2) if reconciles else 0.0,
        "mirror_reconciles": reconciles,
        "mirror_injections": sum(int(s.get("injections") or 0) for s in stats),
        "mirror_unparked_injections": sum(int(s.get("unparked_injections") or 0) for s in stats),
        "mirror_cursor_drifts": sum(int(s.get("cursor_drifts") or 0) for s in stats),
        "tainted_runs": sum(1 for s in stats if s.get("tainted")),
    }


def build_report(
    outcomes: list[RunOutcome], *, meta: dict[str, Any], policies: list[str] | None = None
) -> dict[str, Any]:
    """Assemble the full JSON-serializable report."""
    aggs = aggregate(outcomes, policies=policies)
    return {
        "meta": meta,
        "runs": [o.to_dict() for o in outcomes],
        "aggregate": {k: v.to_dict() for k, v in aggs.items()},
    }


def _world_err_cell(agg: dict[str, Any]) -> str:
    """The ``world err (runs/throws)`` cell for one aggregate row.

    ``n/a`` when the arm carries no instrumentation at all — the SpireMentor arm
    talks to the live game over TCP and never sees the C# process's stderr, so
    rendering it as ``0/0`` would claim a clean world on the strength of a
    counter that was never wired up.
    """
    runs, throws = agg.get("world_error_runs"), agg.get("world_errors")
    if runs is None and throws is None:
        return "n/a"
    return f"{runs or 0}/{throws or 0}"


def render_markdown(report: dict[str, Any]) -> str:
    """Human-readable markdown rendering of :func:`build_report`'s output."""
    meta = report["meta"]
    lines = [
        "# realgame eval report",
        "",
        f"generated: {meta.get('generated_at')}",
        f"host: {meta.get('host')}:{meta.get('port')}  "
        f"character: {meta.get('character')}  ascension: {meta.get('ascension')}  "
        f"seed_base: {meta.get('seed_base')}  dry_run: {meta.get('dry_run')}",
        "",
        "## comparison",
        "",
        "| policy | runs | wins | win rate | mean floor | max floor | "
        "mean decisions | mean fallbacks | DESYNC/1k | unparked | tainted | "
        "world err (runs/throws) | mean duration (s) | errors |",
        "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    for policy, agg in report["aggregate"].items():
        lines.append(
            f"| {policy} | {agg['runs']} | {agg['wins']} | {agg['win_rate']:.1%} | "
            f"{agg['mean_floor']:.1f} | {agg['max_floor']} | {agg['mean_decisions']:.1f} | "
            f"{agg['mean_fallback_count']:.2f} | "
            f"{agg.get('desync_rate_per_1000', 0.0):.1f} | "
            f"{agg.get('mirror_unparked_injections', 0)} | "
            f"{agg.get('tainted_runs', 0)} | "
            f"{_world_err_cell(agg)} | "
            f"{agg['mean_duration_s']:.1f} | {agg['errors']} |"
        )
    lines += ["", "## per-run detail", ""]
    if report["runs"]:
        lines.append(
            "| run | policy | seed | result | won | floor | act | "
            "decisions | fallbacks | world err | duration (s) |"
        )
        lines.append("|---|---|---|---|---|---|---|---|---|---|---|")
        for r in report["runs"]:
            lines.append(
                f"| {r['run_index']} | {r['policy']} | {r['seed']} | {r['result']} | "
                f"{r['won']} | {r['floor']} | {r['act']} | {r['decisions']} | "
                f"{r['fallback_count']} | "
                f"{'n/a' if r.get('world_errors') is None else r['world_errors'].get('count', 0)} | "
                f"{r['duration_s']:.1f} |"
            )
    else:
        lines.append("(no runs -- dry-run self-check or `--runs 0`)")
    return "\n".join(lines) + "\n"


def write_report(report: dict[str, Any], out_dir: Path) -> tuple[Path, Path]:
    """Write ``report.json``/``report.md`` into a fresh ``out_dir``.

    Returns:
        ``(json_path, md_path)``.
    """
    out_dir.mkdir(parents=True, exist_ok=True)
    json_path = out_dir / "report.json"
    md_path = out_dir / "report.md"
    json_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    md_path.write_text(render_markdown(report), encoding="utf-8")
    return json_path, md_path


# ---------------------------------------------------------------------------
# CLI.
# ---------------------------------------------------------------------------


def main(argv: list[str] | None = None) -> int:
    """CLI entry point."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default=DEFAULT_HOST, help="Bridge mod TCP host.")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help="Bridge mod TCP port.")
    parser.add_argument(
        "--policy",
        nargs="+",
        choices=list(POLICIES),
        default=["random"],
        help="One or more policies to evaluate; each gets its own --runs sessions and a "
        "row in the comparison table.",
    )
    parser.add_argument("--runs", type=int, default=1, help="Sessions per policy.")
    parser.add_argument(
        "--character",
        default=None,
        help="Character id to request via start_run when a run_config message is offered.",
    )
    parser.add_argument("--ascension", type=int, default=None)
    parser.add_argument(
        "--seed-base",
        type=int,
        default=None,
        help="Run i of a policy seeds its RNG/start_run with seed_base + i (None: unseeded).",
    )
    parser.add_argument(
        "--max-steps", type=int, default=None, help="Per-run decision cap (default: unlimited)."
    )
    parser.add_argument(
        "--out-dir",
        type=Path,
        default=Path("logs/realgame_eval"),
        help="Parent directory; a UTC-timestamped subdirectory is created per invocation.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Skip the live bridge entirely -- exercise only the aggregation/report-writing "
        "pipeline (use with --runs 0). The coordinator runs the real sweep.",
    )
    parser.add_argument(
        "--model",
        default=None,
        help="PPO model override, forwarded when --policy ppo -- a torch checkpoint "
        "(.pt/.pth/.ckpt/.bin/.tar) or an .onnx export; the extension picks the backend. "
        "Failing to load it ABORTS the sweep rather than silently scoring the random policy.",
    )
    parser.add_argument(
        "--temperature", type=float, default=0.0, help="Forwarded when --policy ppo."
    )
    parser.add_argument(
        "--combat-policy",
        choices=["mcts", "random"],
        default="mcts",
        help="Combat resolution for --policy ppo (PPO has no combat net -- see realgame_ppo).",
    )
    parser.add_argument(
        "--sims",
        type=int,
        default=None,
        help="Flat combat-sim override for EVERY fight (regular and boss). "
        "Default None -> adaptive: --sims-regular for regular rooms, "
        "--sims-boss for boss rooms.",
    )
    parser.add_argument(
        "--sims-regular",
        type=int,
        default=DEFAULT_SIMS_REGULAR,
        help="MCTS sims for regular (non-boss) combats (default 1000).",
    )
    parser.add_argument(
        "--sims-boss",
        type=int,
        default=DEFAULT_SIMS_BOSS,
        help="MCTS sims for boss combats (default 4000).",
    )
    parser.add_argument("--depth", type=int, default=RecommendConfig.depth)
    parser.add_argument(
        "--c-puct",
        type=float,
        default=None,
        help="Explicit exploration constant; default follows the search profile "
        "(2.5 honest / 1.4 with --clairvoyant).",
    )
    parser.add_argument(
        "--clairvoyant",
        action="store_true",
        help="reproduce the OLD leaky combat search (hidden_info=clairvoyant, "
        "c_puct=1.4, no draw widening). Default matches the DEPLOYED honest "
        "MctsCombatPolicy (beliefdraw, c_puct=2.5, draw_widening_c=2.0) — the "
        "same trio headless_eval defaults to. Until 2026-08-02 this driver "
        "silently ran the clairvoyant profile, so no live sweep was comparable "
        "to the rung-3.5 or engine arms.",
    )
    parser.add_argument("--mcts-seed", type=int, default=RecommendConfig.seed)
    parser.add_argument(
        "--rollout-policy",
        choices=["heuristic", "uniform", "net"],
        default=RecommendConfig.rollout_policy,
    )
    args = parser.parse_args(argv)

    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")

    # Match the deployed honest combat search by default, exactly like
    # headless_eval — the live arm is only comparable to the rung-3.5 and
    # engine arms when all three search the same profile.
    mcts_cfg = RecommendConfig(
        # Base sims is overridden per-combat by combat_sims_for() (adaptive
        # 1000/4000, or --sims flat); this default only matters if that path
        # is ever bypassed.
        sims=args.sims if args.sims is not None else DEFAULT_SIMS_REGULAR,
        depth=args.depth,
        hidden_info="clairvoyant" if args.clairvoyant else "beliefdraw",
        c_puct=args.c_puct if args.c_puct is not None else (1.4 if args.clairvoyant else 2.5),
        draw_widening_c=None if args.clairvoyant else 2.0,
        seed=args.mcts_seed,
        rollout_policy=args.rollout_policy,
    )

    outcomes: list[RunOutcome] = []
    for policy in args.policy:
        if args.dry_run:
            print(f"[dry-run] skipping live bridge for policy={policy!r} (0 of {args.runs} run)")
            continue
        ppo_policies = PpoPolicyCache(args.model) if policy == "ppo" else None
        for i in range(args.runs):
            seed = None if args.seed_base is None else args.seed_base + i
            rng = random.Random(seed)
            # Per-run decision log (state+action JSONL, same shape as the
            # realgame_ppo/mcts/random drivers') — the 2026-07-11 sweeps had
            # only aggregate reports, leaving no way to audit what the PPO
            # actually picked at card rewards / shops / rests.
            jsonl = JsonlLogger(Path("logs/realgame"))
            # Self-naming header (same as headless_eval): parallel/short runs
            # in one log dir are otherwise only matchable to a seed by
            # filename-ordering guesswork.
            jsonl.log_meta(
                seed=seed,
                character=args.character,
                ascension=args.ascension,
                policy=policy,
                run_index=i,
            )
            outcome = play_one_session(
                args.host,
                args.port,
                policy,
                rng,
                run_index=i,
                character=args.character,
                ascension=args.ascension,
                seed=seed,
                max_steps=args.max_steps,
                mcts_cfg=mcts_cfg,
                ppo_policies=ppo_policies,
                temperature=args.temperature,
                combat_policy=args.combat_policy,
                sims_regular=args.sims_regular,
                sims_boss=args.sims_boss,
                sims_flat=args.sims,
                jsonl=jsonl,
            )
            jsonl.close()
            outcomes.append(outcome)
            print(
                f"[{policy}] run {i + 1}/{args.runs}: result={outcome.result!r} "
                f"floor={outcome.floor} act={outcome.act} decisions={outcome.decisions} "
                f"fallbacks={outcome.fallback_count} duration={outcome.duration_s:.1f}s"
            )

    meta = {
        "generated_at": datetime.now(UTC).isoformat(),
        "host": args.host,
        "port": args.port,
        "policies": args.policy,
        "runs_per_policy": args.runs,
        "character": args.character,
        "ascension": args.ascension,
        "seed_base": args.seed_base,
        "dry_run": args.dry_run,
        "combat_policy": args.combat_policy,
        "sims_flat": args.sims,
        "sims_regular": args.sims_regular,
        "sims_boss": args.sims_boss,
        # Record the search profile + mirror policy, exactly like headless_eval:
        # the 2026-08-02 clairvoyant confound went unnoticed for weeks partly
        # because no live report said which profile it had searched.
        "combat_search": {
            "hidden_info": mcts_cfg.hidden_info,
            "c_puct": mcts_cfg.c_puct,
            "draw_widening_c": mcts_cfg.draw_widening_c,
        },
        "mirror_policy": policy_from_env().name,
    }
    report = build_report(outcomes, meta=meta, policies=args.policy)

    ts = datetime.now(UTC).strftime("%Y%m%dT%H%M%SZ")
    json_path, md_path = write_report(report, args.out_dir / ts)

    print()
    print(render_markdown(report))
    print(f"Report written to {json_path} / {md_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
