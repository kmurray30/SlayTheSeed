"""PPO-meta headless ladder driver (rungs 2-PPO / 3-PPO of the live-gap ladder).

The live-gap ablation ladder (see memory/realgame-live-winrate-gap.md) ran its
pycli rungs with the HEURISTIC meta policy only — but the deployed live stack
uses the PPO meta, whose native reference is 18/20 = 90% (`run_arena --policy
ppo --combat eval-cheap`, seeds 1000-1019). This driver fills the missing PPO
rungs headless:

* rung 2-PPO (default): ``sts2_rs.PpoRunAgent`` plays every decision — PPO net
  for non-combat, native-state MCTS (eval-cheap budget 1000/4000) for combat.
  Expected to reproduce the ~90% native reference; validates the harness.
* rung 3-PPO (``STS2_DICT_ROUNDTRIP=1``): non-combat decisions stay with the
  PPO net, but combat decisions run through :class:`run_stepper.RunStepper`'s
  dict round-trip path (state_to_dict -> state_from_dict -> mcts_recommend),
  the same reconstruction the real-game bridge suffers — including the fake
  RNG reseed, alive-slot target remap, native pending-choice search, and the
  anti-stall guard. The rung-2-PPO minus rung-3-PPO delta is the
  reconstruction cost under the REAL meta policy.

Usage:
    uv run python python/play_full_run_ppo.py 20 --seed-base 1000
    STS2_DICT_ROUNDTRIP=1 uv run python python/play_full_run_ppo.py 20 --seed-base 1000
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import time
from collections import Counter, deque
from pathlib import Path
from typing import IO, Any

import sts2_rs
from run_stepper import RunStepper
from sts2_hint.display_maps import loc_id

MAX_STEPS = 100_000

# Consecutive steps without a state this run has never been in before, after
# which the run is declared wedged (GH #271). The point is to fail in seconds
# instead of grinding to MAX_STEPS: the livelock that motivated this burned
# 100_000 steps over ~54 minutes while its 59 sibling seeds finished in 8-115s.
#
# 300 is not a guess. Measured over seeds 1050-1057 (rung 3-PPO, 310-744 steps
# each, 8/8 terminal): the WORST legitimate stationary streak in the whole
# corpus was 2 steps — a rest-site option that opens a screen without moving
# any resource. That is a 150x margin. Streaks are short because
# `run_fingerprint` moves on nearly every step of real play; the out-of-combat
# screens it cannot see into (rest-site smith, shop browse) are the only source
# of stationary steps at all, and they resolve in one or two.
#
# `peak_stationary_steps` is reported on every run so this margin stays a
# measurement rather than a historical claim.
NO_PROGRESS_STEPS = 300

# How many trailing steps the wedge dump keeps. The cycle is what makes the
# wedge diagnosable, and cycles are short; the whole 300-step window would be
# the same handful of states repeated.
WEDGE_TAIL_STEPS = 200


def _end_reason(*, stalled: bool, is_over: bool, steps: int, no_progress: bool = False) -> str:
    """WHY the run loop ended, not just that it did.

    A ``None`` from the agent/stepper breaks out mid-run and leaves
    ``player_won`` False — indistinguishable from a real defeat unless it is
    recorded. Engine-world win rates are quoted against rung-3.5 ones, so an
    unmarked non-terminal exit inflates the apparent gap exactly the way the
    DLL's swallowed exceptions did
    (``docs/experiments/2026-07-27-rung35-winrate-v20.md``).

    ``is_over`` is checked BEFORE the step cap: a run that reaches its terminal
    state on exactly step ``MAX_STEPS`` did complete, and calling that
    ``max_steps`` would print WIN while excluding it from the win-rate
    denominator and listing it as non-terminal. ``MAX_STEPS = 100_000`` makes
    the tie unreachable in practice; the ordering costs nothing.

    Both early-break guards (``stalled``, ``no_progress``) outrank ``is_over``,
    which is the opposite of the step cap's tie-break and deliberate: the loop
    consults them only while ``rm.is_over()`` is False, so a True here means
    the break really happened and nothing downstream is trustworthy. The
    ``is_over`` tie-break exists for the step cap, where the run genuinely did
    finish on its last permitted step.
    """
    if stalled:
        return "agent_stalled"
    if no_progress:
        return "no_progress"
    if is_over:
        return "terminal"
    if steps >= MAX_STEPS:
        return "max_steps"
    return "unknown"


def _result_tag(r: dict) -> str:
    """The per-run line's leading tag.

    ``STOP`` exists so a non-terminal run is not printed as ``LOSS``: it is a
    MISSING MEASUREMENT, and folding it into the losses is what makes a win rate
    silently become a floor.
    """
    if r.get("victory"):
        return "WIN "
    if r.get("error"):
        return "ERR "
    if not r.get("completed", True):
        return "STOP"
    return "LOSS"


def run_fingerprint(rm: sts2_rs.RunManager) -> tuple:
    """Everything about the run that a *productive* step can move.

    Two steps sharing a fingerprint left the run in the same place, so a long
    streak of already-seen fingerprints is a livelock. The combat hash is what
    makes this usable mid-fight: floor/HP/gold sit still for most of a combat,
    so a run-level snapshot without it would call every long fight a wedge.

    ``CombatState.state_hash`` covers the piles, both sides' scalars and
    powers, and the ``pending_choice`` payload (kind / min / max / can_skip /
    ordered options — see ``sts2-core/src/hashing.rs``), so a card-select loop
    moves it. It does **not** cover potions, which live on the run rather than
    the combat; those are carried here explicitly, the same omission that made
    ``run_stepper``'s guard misread a Duplicator drink as a no-op.

    The deck is hashed by content, not size: an in-place smith swaps X for X+
    and leaves the count alone, so a rest-site loop would otherwise look
    stationary.
    """
    cs = rm.combat_state() if rm.phase() == "combat" else None
    return (
        rm.phase(),
        rm.total_floor(),
        rm.act_index,
        rm.current_hp,
        rm.max_hp,
        rm.gold,
        tuple(sorted(rm.potions())),
        tuple(sorted(rm.relics())),
        hash(tuple(sorted(rm.master_deck_detailed()))),
        cs.state_hash() if cs is not None else None,
    )


def _fp_digest(fp: tuple) -> str:
    """Short stable label for a fingerprint, for the dump's cycle trace.

    ``hash()`` would do the job in-process but is salted per interpreter, so
    two dumps of the same wedge would not be comparable.
    """
    return hashlib.blake2b(repr(fp).encode(), digest_size=6).hexdigest()


class NoProgressWatchdog:
    """Windowed livelock detector for the run loop (GH #271).

    Trips after ``window`` consecutive steps in which the run entered no state
    it had not already been in. Novelty against **every** state seen so far is
    the point: comparing against only the previous step — which is what
    ``run_stepper``'s ``_last_combat_fp`` guard does — cannot see an A→B→A→B
    cycle, because consecutive fingerprints differ every time. Here both A and
    B are already in ``_seen`` after two steps, so the counter climbs.

    A healthy run resets the counter constantly; only a run going nowhere
    accumulates. Recording every distinct fingerprint costs one small tuple per
    novel state (a few thousand in a full run) and is what buys the cycle
    detection.
    """

    def __init__(self, window: int = NO_PROGRESS_STEPS, tail: int = WEDGE_TAIL_STEPS) -> None:
        self.window = window
        self._seen: set[tuple] = set()
        self._since_novel = 0
        self._peak = 0
        self._tail: deque[dict] = deque(maxlen=tail)

    @property
    def enabled(self) -> bool:
        return self.window > 0

    @property
    def stationary_steps(self) -> int:
        """Consecutive steps since the run last reached a new state."""
        return self._since_novel

    @property
    def peak_stationary_steps(self) -> int:
        """Longest stationary streak this run survived.

        Reported on every run, not just wedged ones: it is the only way to see
        whether ``window`` still has margin. A corpus whose peak creeps toward
        the window is one engine change away from false wedges, and that should
        be visible before it starts eating runs rather than after.
        """
        return self._peak

    def observe(self, fp: tuple, *, step: int, action: Any) -> bool:
        """Record one applied step; True once the run is declared wedged.

        Call AFTER the action has been applied and only while the run has not
        ended, so a trip can never be a run that actually finished.
        """
        if not self.enabled:
            return False
        novel = fp not in self._seen
        if novel:
            self._seen.add(fp)
            self._since_novel = 0
        else:
            self._since_novel += 1
            self._peak = max(self._peak, self._since_novel)
        self._tail.append({"step": step, "state": _fp_digest(fp), "novel": novel, "action": action})
        return self._since_novel >= self.window

    def report(self, rm: sts2_rs.RunManager, *, seed: int, steps: int) -> dict:
        """The wedge artifact: where it stuck, what it kept doing, and the cycle.

        Self-contained on purpose — the point of the dump is that the wedge is
        diagnosable from the file alone, without re-running the ~54-minute
        sweep that produced it.
        """
        cycle = [e["state"] for e in self._tail]
        actions = Counter(json.dumps(e["action"], sort_keys=True, default=str) for e in self._tail)
        return {
            "seed": seed,
            "end_reason": "no_progress",
            "steps": steps,
            "window": self.window,
            "stationary_steps": self._since_novel,
            "peak_stationary_steps": self._peak,
            "distinct_states_total": len(self._seen),
            "distinct_states_in_tail": len(set(cycle)),
            "where": {
                "phase": rm.phase(),
                "floor": rm.total_floor(),
                "act": rm.act_index,
                "room_type": (rm.current_room() or {}).get("room_type"),
                "hp": rm.current_hp,
                "max_hp": rm.max_hp,
                "gold": rm.gold,
            },
            # The repeating action IS the bug in every livelock seen so far:
            # something the engine reports as `Advanced` while ignoring it.
            "repeated_actions": [
                {"count": n, "action": json.loads(a)} for a, n in actions.most_common(10)
            ],
            "tail": list(self._tail),
        }


class RunTracer:
    """Per-floor world trace for the distribution diff vs rung-3.5 logs.

    Emits one JSONL record per room entered (floor, act, room_type, hp,
    gold, deck size/upgrades), one per combat (roster kinds, elite flag,
    hp before/after), and a final run summary. The schema mirrors what
    the rung-3.5 extractor pulls out of the real-game decision logs so
    the two worlds diff directly (fights per act, elite density,
    HP-bleed per fight, deck quality at matched floors).
    """

    def __init__(self, path: Path) -> None:
        self._f: IO[str] = path.open("w", encoding="utf-8")
        self._last_room_key: tuple[int, str] | None = None
        self._in_combat = False
        self._combat_rec: dict | None = None
        self._prev_snap: dict | None = None
        self._prev_in_combat = False
        self._prev_where: dict | None = None
        self._prev_decision: str | None = None

    def _emit(self, rec: dict) -> None:
        self._f.write(json.dumps(rec, separators=(",", ":")) + "\n")
        self._f.flush()

    @staticmethod
    def _snapshot(rm: sts2_rs.RunManager) -> dict:
        """Comparable resource snapshot; deck cards carry a ``+`` suffix when
        upgraded so an in-place smith shows up as remove(X)+add(X+).

        Every id goes through :func:`sts2_hint.display_maps.loc_id`, which is
        what makes this trace diffable against ``headless_eval``'s real-DLL
        twin. The two worlds name the same object three different ways —
        engine ``RELIC.SILVER_CRUCIBLE`` / ``Unrelenting`` / ``AttackPotion``
        vs DLL ``SILVER_CRUCIBLE`` / ``CARD.UNRELENTING`` / ``ATTACK_POTION``
        — so an un-normalised diff reports 100% spurious divergence on every
        relic, card and potion. ``loc_id`` collapses all of them to the game's
        own SCREAMING_SNAKE stem, including the Rust-only ``_CARD``/``_STATUS``
        disambiguators that have no counterpart in the C# id space.
        """
        return {
            "gold": rm.gold,
            "hp": rm.current_hp,
            "max_hp": rm.max_hp,
            "relics": Counter(loc_id(r) for r in rm.relics()),
            "potions": Counter(loc_id(p) for p in rm.potions()),
            "cards": Counter(
                loc_id(cid) + ("+" if upgraded else "")
                for cid, upgraded in rm.master_deck_detailed()
            ),
        }

    def _emit_delta(self, snap: dict, in_combat: bool) -> None:
        """One ``ev: delta`` per observe whose resources changed vs the last
        observe: relic/potion/card/gold/max-hp changes, plus out-of-combat HP
        changes. The HP rule also surfaces losses the combat detector never
        sees (e.g. event-spawned fights — GH #300's invisible deaths).

        The record is stamped with the location and decision of the PREVIOUS
        observe, because that is where the change was caused. Stamping the
        current location instead put a rest-site heal at the map node the
        player advanced to, and split one event's outcomes across two
        ``room_type``s depending on observe cadence — which also made the two
        arms disagree about where the same pickup happened.
        """
        prev = self._prev_snap
        if prev is None:
            return
        changes: dict = {}
        for kind in ("relics", "potions", "cards"):
            added = list((snap[kind] - prev[kind]).elements())
            removed = list((prev[kind] - snap[kind]).elements())
            if added:
                changes[f"{kind}_added"] = added
            if removed:
                changes[f"{kind}_removed"] = removed
        for field in ("gold", "max_hp"):
            if prev[field] != snap[field]:
                changes[f"{field}_delta"] = snap[field] - prev[field]
        if not in_combat and not self._prev_in_combat and prev["hp"] != snap["hp"]:
            changes["hp_delta"] = snap["hp"] - prev["hp"]
        if changes:
            self._emit(
                {
                    "ev": "delta",
                    **(self._prev_where or {}),
                    "after_decision": self._prev_decision,
                    **changes,
                }
            )

    def observe(self, rm: sts2_rs.RunManager) -> None:
        """Call once per driver-loop iteration, before the decision."""
        floor = rm.total_floor()
        room = rm.current_room()
        room_type = room["room_type"] if room else None
        phase = rm.phase()
        # One snapshot per observe, shared with the room record below: it is
        # the only `master_deck_detailed()` call on this path.
        snap = self._snapshot(rm)
        if room_type is not None and (floor, room_type) != self._last_room_key:
            self._last_room_key = (floor, room_type)
            cards = snap["cards"]
            self._emit(
                {
                    "ev": "room",
                    "floor": floor,
                    "act": rm.act_index,
                    "act_floor": rm.act_floor(),
                    "room_type": room_type,
                    "hp": rm.current_hp,
                    "max_hp": rm.max_hp,
                    "gold": rm.gold,
                    "deck_size": cards.total(),
                    "upgrades": sum(n for c, n in cards.items() if c.endswith("+")),
                    "relics": sorted(snap["relics"].elements()),
                    "potions": sorted(snap["potions"].elements()),
                }
            )
        in_combat = phase == "combat"
        self._emit_delta(snap, in_combat)
        self._prev_snap = snap
        self._prev_in_combat = in_combat
        self._prev_where = {"floor": floor, "act": rm.act_index, "room_type": room_type}
        self._prev_decision = phase
        if in_combat and not self._in_combat:
            self._in_combat = True
            roster: list[str] = []
            is_elite = False
            cs = rm.combat_state()
            if cs is not None:
                d = sts2_rs.state_to_dict(cs)
                roster = [
                    m.get("kind", "?")
                    for m in d.get("encounter", [])
                    if not m.get("is_dead") and not m.get("is_escaped")
                ]
                is_elite = bool(d.get("is_elite"))
            self._combat_rec = {
                "ev": "combat",
                "floor": floor,
                "act": rm.act_index,
                "room_type": room_type,
                "roster": roster,
                "is_elite": is_elite,
                "hp_before": rm.current_hp,
                "max_hp": rm.max_hp,
            }
        elif not in_combat and self._in_combat:
            self._in_combat = False
            self._close_combat(rm)

    def _close_combat(self, rm: sts2_rs.RunManager) -> None:
        rec = self._combat_rec
        if rec is None:
            return
        rec["hp_after"] = rm.current_hp
        rec["hp_lost"] = rec["hp_before"] - rm.current_hp
        self._emit(rec)
        self._combat_rec = None

    def finish(self, rm: sts2_rs.RunManager, result: dict) -> None:
        if self._in_combat:  # died (or timed out) mid-fight
            self._in_combat = False
            self._close_combat(rm)
        deck = rm.master_deck_detailed()
        self._emit(
            {
                "ev": "run_end",
                **result,
                "hp": rm.current_hp,
                "max_hp": rm.max_hp,
                "gold": rm.gold,
                "deck_size": len(deck),
                "upgrades": sum(1 for _, upgraded in deck if upgraded),
                "deck": [cid for cid, _ in deck],
                "relics": rm.relics(),
            }
        )
        self._f.close()


def default_wedge_dir() -> Path:
    """Where wedge dumps land when the caller names no directory.

    Under the gitignored ``logs/``, same as every other run artifact.
    """
    return Path(__file__).resolve().parent.parent / "logs" / "wedges"


def _dump_wedge(report: dict, seed: int, wedge_dir: Path | None) -> Path | None:
    """Persist the wedge artifact; never let a dump failure kill the sweep.

    A sweep that dies while writing its diagnostic would lose the other 59
    seeds' results to a disk error — the artifact matters, but not that much.
    """
    target = wedge_dir if wedge_dir is not None else default_wedge_dir()
    try:
        target.mkdir(parents=True, exist_ok=True)
        path = target / f"wedge-seed{seed}.json"
        path.write_text(json.dumps(report, indent=2, default=str), encoding="utf-8")
        return path
    except OSError as e:
        print(f"  [watchdog] could not write the wedge dump: {e}", flush=True)
        return None


def play_run(
    seed: int,
    character: str,
    *,
    sims: int,
    boss_sims: int,
    roundtrip: bool,
    verbose: bool = False,
    trace_dir: Path | None = None,
    string_seeds: bool = False,
    no_progress_steps: int = NO_PROGRESS_STEPS,
    wedge_dir: Path | None = None,
) -> dict:
    # string_seeds: seed the run the way the C# game does with a STRING seed
    # (canonicalize + deterministic_hash_code). The rung-3.5 harness starts
    # the real game with seed=str(seed), so this is what makes an engine-world
    # run seed-MATCHED to a real-world run (same map/boss/encounter draws,
    # given run-setup parity). The plain numeric path is a different stream.
    rm = sts2_rs.RunManager(
        seed=seed,
        string_seed=str(seed) if string_seeds else None,
        character=character,
        neow_start=True,
    )
    agent = sts2_rs.PpoRunAgent(seed=seed, sims=sims, boss_sims=boss_sims)
    stepper = (
        RunStepper(rm, seed=seed, mcts_iters=sims, boss_mcts_iters=boss_sims, verbose=verbose)
        if roundtrip
        else None
    )
    tracer = RunTracer(trace_dir / f"seed{seed}.jsonl") if trace_dir else None

    t0 = time.perf_counter()
    steps = 0
    stalled = False
    wedged = False
    watchdog = NoProgressWatchdog(no_progress_steps)
    while steps < MAX_STEPS and not rm.is_over():
        if tracer is not None:
            tracer.observe(rm)
        if stepper is not None and rm.phase() == "combat":
            decision = stepper.decide()
            if decision is None:
                stalled = True
                break
            action = decision.action
            rm.take_action(action)
            stepper.step_taken()
        else:
            rec = agent.step(rm)
            if rec is None:
                stalled = True
                break
            action = rec.get("action")
            if stepper is not None:
                # Keep the round-trip combat seed schedule advancing across
                # non-combat steps too, mirroring run_stepper's seed+steps.
                stepper.step_taken()
        steps += 1
        # Only while the run is still going: a trip must never be able to
        # reclassify a run that actually reached its terminal state.
        if (
            watchdog.enabled
            and not rm.is_over()
            and watchdog.observe(run_fingerprint(rm), step=steps, action=action)
        ):
            wedged = True
            break

    end_reason = _end_reason(stalled=stalled, is_over=rm.is_over(), steps=steps, no_progress=wedged)
    wedge_path: Path | None = None
    if wedged:
        wedge_path = _dump_wedge(watchdog.report(rm, seed=seed, steps=steps), seed, wedge_dir)

    result = {
        "seed": seed,
        "victory": bool(rm.player_won),
        "floor": rm.total_floor(),
        "steps": steps,
        "timeout": steps >= MAX_STEPS,
        "end_reason": end_reason,
        # A run that never reached a terminal state is NOT a loss; it is a
        # missing measurement. Only `terminal` runs belong in a win rate.
        "completed": end_reason == "terminal",
        "seconds": round(time.perf_counter() - t0, 1),
        # How close this run came to the watchdog, so the window's margin is a
        # measurement rather than an assumption.
        "peak_stationary_steps": watchdog.peak_stationary_steps,
    }
    if wedged:
        result["wedge_dump"] = str(wedge_path) if wedge_path else None
    if tracer is not None:
        tracer.finish(rm, result)
    return result


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("num_runs", type=int)
    ap.add_argument("--character", default="Ironclad")
    ap.add_argument("--seed-base", type=int, default=1000)
    ap.add_argument("--sims", type=int, default=1000)
    ap.add_argument("--boss-sims", type=int, default=4000)
    ap.add_argument("--verbose", action="store_true")
    ap.add_argument(
        "--trace-dir",
        type=Path,
        default=None,
        help="write per-seed world-trace JSONL (rooms, combats, run summary) here",
    )
    ap.add_argument(
        "--string-seeds",
        action="store_true",
        help="seed runs via string_seed=str(seed) (C#-game hash) so runs are "
        "seed-matched to real-game/headless runs started with seed=str(seed)",
    )
    ap.add_argument(
        "--no-progress-steps",
        type=int,
        default=NO_PROGRESS_STEPS,
        help="declare a run wedged after this many consecutive steps that "
        f"reach no state the run has not already been in (default {NO_PROGRESS_STEPS}; "
        "0 disables the watchdog and leaves only the 100k step cap)",
    )
    ap.add_argument(
        "--wedge-dir",
        type=Path,
        default=None,
        help=f"where to write wedge dumps (default {default_wedge_dir()})",
    )
    args = ap.parse_args()
    if args.trace_dir is not None:
        args.trace_dir.mkdir(parents=True, exist_ok=True)

    roundtrip = os.environ.get("STS2_DICT_ROUNDTRIP") == "1"
    rung = "3-PPO (dict round-trip combat)" if roundtrip else "2-PPO (native combat)"
    print(
        f"PPO ladder rung {rung}: {args.num_runs} runs as {args.character}, "
        f"seeds {args.seed_base}..{args.seed_base + args.num_runs - 1}, "
        f"sims {args.sims}/{args.boss_sims}"
    )
    print("=" * 64)

    results = []
    t_start = time.perf_counter()
    for i in range(args.num_runs):
        seed = args.seed_base + i
        try:
            r = play_run(
                seed,
                args.character,
                sims=args.sims,
                boss_sims=args.boss_sims,
                roundtrip=roundtrip,
                verbose=args.verbose,
                trace_dir=args.trace_dir,
                string_seeds=args.string_seeds,
                no_progress_steps=args.no_progress_steps,
                wedge_dir=args.wedge_dir,
            )
        except Exception as e:  # keep the sweep alive; report the seed
            r = {
                "seed": seed,
                "victory": False,
                "floor": 0,
                "steps": 0,
                "timeout": False,
                "end_reason": "exception",
                "completed": False,
                "seconds": 0.0,
                "error": str(e),
            }
        results.append(r)
        tag = _result_tag(r)
        extra = f"  error={r['error']}" if r.get("error") else ""
        if not r.get("completed", True) and not r.get("error"):
            extra = f"  end_reason={r.get('end_reason')}"
            # Name the artifact on the run's own line — a wedge dump nobody
            # knows exists is the same as no dump.
            if r.get("wedge_dump"):
                extra += f"  dump={r['wedge_dump']}"
        print(
            f"  Run {i + 1:>2}/{args.num_runs}: {tag} seed={seed} "
            f"floor={r['floor']:>2} steps={r['steps']} ({r['seconds']}s){extra}",
            flush=True,
        )

    wins = sum(1 for r in results if r["victory"])
    floors = [r["floor"] for r in results]
    incomplete = [r for r in results if not r.get("completed", True)]
    print("=" * 64)
    print(
        f"Wins: {wins}/{len(results)}  mean floor: {sum(floors) / len(floors):.1f}  "
        f"({time.perf_counter() - t_start:.0f}s total)"
    )
    # The watchdog's margin over the whole corpus. Printed always, for the same
    # reason the completion count is: a guard whose headroom nobody looks at is
    # a guard that starts eating real runs unnoticed.
    peaks = [r["peak_stationary_steps"] for r in results if "peak_stationary_steps" in r]
    if peaks and args.no_progress_steps > 0:
        print(
            f"Watchdog: worst stationary streak {max(peaks)} step(s) "
            f"against a window of {args.no_progress_steps}"
        )
    # Print the denominator's health next to the win count, always — a silent
    # "0 incomplete" is the whole point, and an unprinted non-zero is how a
    # win rate quietly becomes a floor.
    by_reason = Counter(r.get("end_reason", "unknown") for r in incomplete)
    print(
        f"Completed (reached a terminal state): {len(results) - len(incomplete)}/{len(results)}"
        + (
            f"  NON-TERMINAL: {dict(by_reason)} seeds={[r['seed'] for r in incomplete]}"
            if incomplete
            else ""
        )
    )
    if incomplete:
        clean = len(results) - len(incomplete)
        if clean == 0:
            # Every run was a missing measurement, so there is no rate to
            # quote — and quoting 0% would be exactly the "non-terminal folded
            # into losses" error the rest of this block exists to prevent.
            print(
                f"  -> NO win rate: 0/{len(results)} runs reached a terminal state. "
                f"There is nothing to measure here, not a 0% win rate."
            )
        else:
            print(
                f"  -> win rate over COMPLETED runs only: {wins}/{clean} = "
                f"{wins / clean:.1%} (vs {wins / len(results):.1%} over all runs). "
                f"Non-terminal runs are missing measurements, not losses."
            )


if __name__ == "__main__":
    main()
