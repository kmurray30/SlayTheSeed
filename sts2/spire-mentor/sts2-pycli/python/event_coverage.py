"""Exhaustive event/option coverage harness (GH #313).

Every world-throw fixed so far was found *by luck* — a policy-driven sweep
happened to take the branch. ``Trial.Accept`` threw on its **first line**
(``NEventRoom.Instance.Layout``), which means the whole Trial event had been
dead in headless for as long as it existed; it surfaced only when one seed of a
60-seed corpus walked into it.

That bias is structural, not a one-off miss. Any driver with a **fixed** option
index deterministically never reaches the other branches; a **random** one
reaches a depth-``d`` page with probability ``~2**-d`` (SlipperyBridge escalates
7+ pages deep); and a **trained** policy is worse still, because it is biased
away from exactly the options a human would call risky — which is where the
unusual code paths live.

So this harness uses no play policy. It injects each event directly
(``enter_room type=event``) and walks **every** option, breadth-first over the
follow-up pages that ``SetEventState`` re-presents.

Four failure classes, all asserted. Each is invisible to at least one of the
others, which is the point:

* **throwing** — the world printed an exception line
  (``sts2_client._WORLD_ERROR_RE``).
* **non-throwing** — the option resolved without faulting but the event did not
  advance, so the harness sees a ``force-finishing`` line. The state effect was
  dropped silently. INVISIBLE to the world-error counter, which keys on
  exception type names; observed on ``SlipperyBridge`` (seeds 1011/1013) with
  zero exceptions logged. Since GH #316 the driver decides "didn't advance" by
  counting ``EventModel.StateChanged``, so this class means the option really
  did nothing — a repeat-option loop (AbyssalBaths' Linger, SlipperyBridge's
  ``HOLD_ON_LOOP``) no longer trips it.
* **wire error** — the action came back as ``{"type": "error"}``.
  ``ExecuteActionImpl`` wraps its dispatch in a catch that RETURNS
  ``ErrorWithTrace(...)`` without ever calling ``Log()``, so a fault outside the
  option body reaches stderr nowhere at all: no throw line, no force-finish, no
  state change. Both channels above read it as a clean pass.
* **entry** — the event could not be entered, or presented nothing to walk.
  ``EnterRoom`` catches and returns, so an entry-time fault is visible only in
  the returned dict. An event that throws on entry but still shows options used
  to be reported as covered-and-clean, which quietly narrowed every "N options,
  0 failures" headline to "N option BODIES".

A fifth signal, ``no_state_change``, is **reported but not failed**: plenty of
options legitimately change nothing observable ("Leave", pure dialogue pages),
so it is a lead to read, not a gate. So is ``truncated``, which says the BFS hit
a cap and the event is therefore only PARTIALLY covered — a coverage tool that
cannot say what it did not cover reproduces the blind spot it exists to close.

Because each option mutates the run, a path is replayed from a **fresh process**
every time: start run -> apply pre-state -> inject event -> replay the prefix ->
take the next option.

Usage::

    uv run python python/event_coverage.py                 # all events, base pre-state
    uv run python python/event_coverage.py --event TRIAL -v
    uv run python python/event_coverage.py --prestate full_potions --json out.json
"""

from __future__ import annotations

import argparse
import json
import logging
import sys
from collections import deque
from dataclasses import dataclass, field
from typing import Any

from sts2_client import Game

logger = logging.getLogger(__name__)

#: Marks an option whose task neither advanced the event nor threw. The C# side
#: prints this from ``RunSimulator`` when an option leaves the event in the same
#: state it started in.
#:
#: Matched on ``force-finish`` alone, NOT on the full sentence. There are two
#: sites with two different wordings — ``Event {X} didn't advance,
#: force-finishing`` (``RunSimulator.cs`` ~2700) and ``Event {X}: same options
#: after choice, force-finishing`` (~4081, the ``EventChoiceState`` re-entry
#: guard, reachable when a leg parks at a selection and resumes). They are the
#: same defect with the same silent state drop, and an earlier pattern keyed on
#: ``didn't advance, `` saw only the first. The wording is not a stable
#: contract, which is itself the argument for the looser pattern.
FORCE_FINISH_RE = r"force-finish"

#: Guards against a pathological event graph (a page that re-presents itself)
#: turning the BFS into an unbounded walk. SlipperyBridge's "Hold On" chain is
#: the real motivator: it escalates indefinitely by design.
MAX_DEPTH = 6
MAX_PATHS_PER_EVENT = 60

#: Run states that make otherwise-unreachable branches reachable. The
#: ``RewardsSet.Offer()`` assertion bug (2026-07-29) could NOT be found by
#: option coverage alone — it needs a full potion belt at the moment a potion
#: reward is offered, i.e. it is a *pre-state* bug wearing an option's clothes.
#:
#: ``base`` and ``full_potions`` are the exercised pair — the ones the gate runs
#: and the ones the roster sweep used. The other three are offered but UNPROVEN:
#: no test drives them, and ``low_hp`` in particular sets ``hp: 1``, which will
#: kill the player on any damaging option and turn most of the roster into
#: run-over rows rather than coverage. Treat them as a starting point for a
#: targeted investigation, not as sweep axes.
PRESTATES: dict[str, dict[str, Any]] = {
    "base": {},
    "full_potions": {"potions": ["FIRE_POTION", "FIRE_POTION", "FIRE_POTION"]},
    "low_hp": {"hp": 1},
    "rich": {"gold": 999},
    "poor": {"gold": 0},
}


#: World failures that are known, understood and deliberately not fixed, keyed
#: ``(event_id, text_key)``.
#:
#: ``text_key`` and NOT ``title``: the title is a localization lookup with an
#: ``option_{i}`` fallback, i.e. a display string. Keying on it meant a loc-table
#: edit could silently disarm an entry — or, worse, re-aim it at a different
#: option that happened to inherit the string.
#:
#: Lives here rather than in the test module so the CLI and the gate share one
#: list; otherwise `main()` exits nonzero forever on a residual the suite has
#: already accepted.
KNOWN_RESIDUALS: dict[tuple[str, str], str] = {
    ("TRIAL", "TRIAL.pages.REJECT.options.DOUBLE_DOWN"): (
        "Trial.DoubleDown's whole body opens the abandon-run CONFIRM popup "
        "(NModalContainer.Instance.Add(NAbandonRunConfirmPopup.Create(null))), which is "
        "unreachable headless, so it is no-op'd and the event force-finishes. Live, "
        "cancelling the confirm leaves the player on the REJECT page still able to take "
        "Accept; here the run skips the event for free. The POLICY no longer picks it — "
        "the option is ThatWillKillPlayerIf(_ => true) and both choosers now withhold "
        "will_kill_player options — but this harness takes every option by design, so it "
        "still sees the drop. Fixing it means modelling the confirm, not adding a guard."
    ),
}


#: Events whose entry presents ZERO options for a reason the GAME gives, not
#: because anything faulted. Deliberately NOT in ``KNOWN_RESIDUALS``: those are
#: admitted bugs, these are correct behaviour, and collapsing the two would make
#: the residual list unreadable. The entry assertion ("entered fine, but there
#: is nothing to take") only carries signal for events that are supposed to
#: offer something, so it is suppressed here and the row is still recorded.
EXPECTED_EMPTY_ENTRY: dict[str, str] = {
    "FAKE_MERCHANT": (
        "GenerateInitialOptions() returns Array.Empty<EventOption>() — it is an "
        "EventLayoutType.Custom shop node, and its ONLY interaction is throwing a "
        "FoulPotion at the merchant (FakeMerchant.FoulPotionThrown, which enters "
        "combat). There is no option list to sweep, in any pre-state."
    ),
    "THE_FUTURE_OF_POTIONS": (
        "One option per carried potion, min(3, Owner.Potions.Count) — and its own "
        "IsAllowed demands >= 2 potions, which this harness deliberately ignores "
        "so it can force-enter. The `base` pre-state carries none, so the empty "
        "entry IS the pre-state; `full_potions` presents and exercises all three."
    ),
}


@dataclass
class OptionResult:
    """Outcome of taking one option at the end of one path."""

    event_id: str
    prestate: str
    path: list[int]
    title: str
    #: The option's STABLE id (``EventOption.TextKey``). ``title`` is a
    #: localization lookup with an ``option_{i}`` fallback — a display string,
    #: not an identity — so anything that has to name an option across runs
    #: (an allowlist, a JSON diff) keys on this instead.
    text_key: str = ""
    world_errors: int = 0
    world_error_sample: list[str] = field(default_factory=list)
    force_finished: bool = False
    force_finish_lines: list[str] = field(default_factory=list)
    #: The measured action came back as ``{"type": "error"}`` on the WIRE.
    #: `ExecuteActionImpl` wraps its whole dispatch in a catch that returns
    #: `ErrorWithTrace(...)` and never calls `Log()`, so a fault outside the
    #: option body — in `DetectDecisionPoint`, `EventChoiceState`, `ForceToMap`
    #: or state serialization — reaches stderr NOWHERE. Without this field such
    #: an option scored a clean pass: no throws, no force-finish, empty delta.
    wire_error: str | None = None
    #: The event could not be entered, or presented nothing to walk. A row, not
    #: a log line: an event that cannot be entered is not covered, and reporting
    #: it as absent-from-the-results is the same blind spot one level up.
    entry_failed: str | None = None
    #: The entry presented nothing AND the event is in ``EXPECTED_EMPTY_ENTRY``.
    #: Carries the same text ``entry_failed`` would have, so the row still says
    #: what was observed — it just isn't scored as a fault.
    entry_empty_expected: str | None = None
    #: Set on the ENTRY row when the BFS hit a cap. Informational, never a
    #: failure — but it must be reported, or a capped sweep reads as full
    #: coverage.
    truncated: bool = False
    truncated_reason: str = ""
    state_delta: dict[str, Any] = field(default_factory=dict)
    error: str | None = None

    @property
    def failed(self) -> bool:
        """A world failure — throwing, silent, wire-level, or unenterable.

        ``error`` stays separate: that is HARNESS trouble (a bad handshake, an
        option that vanished on replay), not a verdict about the world.
        """
        return bool(
            self.world_errors > 0 or self.force_finished or self.wire_error or self.entry_failed
        )

    def describe(self) -> str:
        why = []
        if self.world_errors:
            why.append(f"threw x{self.world_errors}: {(self.world_error_sample or [''])[0][:90]}")
        if self.force_finished:
            why.append("force-finished (no throw, state effect dropped)")
        if self.wire_error:
            why.append(f"wire error: {self.wire_error[:90]}")
        if self.entry_failed:
            why.append(f"entry: {self.entry_failed}")
        path = "/".join(map(str, self.path)) or "-"
        return f"{self.event_id}[{self.prestate}] path={path} {self.title!r}: {'; '.join(why)}"


def _snapshot(state: dict[str, Any]) -> dict[str, Any]:
    """The player-visible quantities an event option could plausibly move.

    Reads a DECISION payload (what ``enter_room`` / ``act`` return), not
    ``get_state()``. ``get_state()`` is the COMBAT state: its ``player`` carries
    only ``block``/``energy``/``hp``/``max_hp``/``powers``, so ``gold``, ``deck``
    and ``potions`` are absent entirely and ``relics`` sits at top level. Reading
    it hardwired four of these six fields to ``None``/``0``, which meant every
    gold-only, card-gain, relic-gain and potion-gain option — most of the roster
    — reported an empty delta and landed in the "no state change" bucket. The
    decision payload carries the full ``PlayerSummary``.
    """
    player = state.get("player") or {}
    return {
        "hp": player.get("hp"),
        "max_hp": player.get("max_hp"),
        "gold": player.get("gold"),
        "deck": len(player.get("deck") or []),
        "relics": len(player.get("relics") or []),
        "potions": len([p for p in (player.get("potions") or []) if not p.get("empty")]),
    }


def _delta(before: dict[str, Any], after: dict[str, Any]) -> dict[str, Any]:
    out: dict[str, Any] = {}
    for k, b in before.items():
        a = after.get(k)
        if isinstance(a, int) and isinstance(b, int) and a != b:
            out[k] = a - b
    return out


def _options(state: dict[str, Any]) -> list[dict[str, Any]]:
    if state.get("decision") != "event_choice":
        return []
    return [o for o in (state.get("options") or []) if not o.get("is_locked")]


def _drive(
    event_id: str, prestate: str, path: list[int], *, seed: str, character: str
) -> tuple[OptionResult, list[dict[str, Any]]]:
    """Replay ``path`` in a fresh process and take its final option.

    Returns the result plus the option list the event presented AFTER the final
    option, so the caller can extend the BFS frontier.
    """
    res = OptionResult(event_id=event_id, prestate=prestate, path=list(path), title="?")
    # Constructed INSIDE the try: `Game.__init__` spawns the child and then
    # raises RuntimeError on a bad handshake, so building it above would orphan
    # the process AND let the exception escape past the `finally` — one flaky
    # start out of ~300 spawns would abandon a 90-minute sweep.
    game: Game | None = None
    try:
        game = Game(watch_stderr=FORCE_FINISH_RE)
        game.start(seed=seed, character=character)
        overrides = PRESTATES[prestate]
        if overrides:
            game.set_player(**overrides)

        if not path:
            # The ROOT drive measures event ENTRY, and its row is kept. Entry is
            # a world action like any other: `EnterRoom` catches and returns, so
            # an entry-time fault is visible ONLY in the returned dict, and an
            # event that throws on entry but still presents options used to be
            # reported as covered-and-clean.
            res.title = "<entry>"
            game.stderr_watch(clear=True)
            entry_baseline = game.world_errors()["count"]
            entered = game.enter_room("event", event=event_id)
            if entered.get("type") == "error":
                res.entry_failed = f"enter_room: {entered.get('message')}"
                return res, []
            opts = _options(entered)
            _measure(game, res, entry_baseline)
            if not opts:
                why = f"presented no options on entry (decision={entered.get('decision')!r})"
                if event_id in EXPECTED_EMPTY_ENTRY:
                    res.entry_empty_expected = why
                else:
                    res.entry_failed = why
            return res, opts

        entered = game.enter_room("event", event=event_id)
        if entered.get("type") == "error":
            # On a non-root path this is replay trouble, not a verdict: the root
            # row above already owns the entry assertion for this event.
            res.error = f"enter_room: {entered.get('message')}"
            return res, []

        state = entered
        # Replay the prefix. Any world noise here belongs to an EARLIER option,
        # which has its own result row, so drop it before the measured step.
        for idx in path[:-1]:
            opts = _options(state)
            if not any(o.get("index") == idx for o in opts):
                res.error = f"prefix option {idx} vanished (event re-rolled?)"
                return res, []
            state = game.act("choose_option", option_index=idx)

        target = next((o for o in _options(state) if o.get("index") == path[-1]), None)
        if target is None:
            res.error = f"option {path[-1]} not present"
            return res, []
        res.title = str(target.get("title") or "?")
        res.text_key = str(target.get("text_key") or "")

        before = _snapshot(state)
        baseline_errors = game.world_errors()["count"]
        game.stderr_watch(clear=True)

        state = game.act("choose_option", option_index=path[-1])

        if state.get("type") == "error":
            res.wire_error = str(state.get("message"))
            _measure(game, res, baseline_errors)
            return res, []

        res.state_delta = _delta(before, _snapshot(state))
        _measure(game, res, baseline_errors)
        return res, _options(state)
    except Exception as exc:  # harness trouble, not a world failure
        res.error = f"{type(exc).__name__}: {exc}"
        return res, []
    finally:
        if game is not None:
            try:
                game.close()
            except Exception:
                pass


def _measure(game: Game, res: OptionResult, baseline_errors: int) -> None:
    """Read both failure channels for the step that just ran.

    The ``get_state()`` round-trip is a BARRIER, not a query. ``stderr_watch``
    reads a buffer the drain thread fills asynchronously and — unlike
    ``world_errors`` — never joins it, while ``act()`` returns as soon as the
    child writes stdout. Without a second round-trip a force-finish line still in
    flight is simply lost, and the option scores a silent PASS on the one signal
    this harness exists to produce.
    """
    try:
        game.get_state()
    except Exception:  # a dead child cannot emit anything further either
        pass
    hits = game.stderr_watch(clear=True)
    res.force_finish_lines = hits
    res.force_finished = bool(hits)
    errs = game.world_errors()
    res.world_errors = errs["count"] - baseline_errors
    # The sample buffer is session-wide, first-20 and never cleared, so its tail
    # can belong to an EARLIER step. Only quote it when this step is the only
    # thing that could have filled it.
    res.world_error_sample = (
        errs["sample"][-3:] if res.world_errors and baseline_errors == 0 else []
    )


def sweep_event(
    event_id: str, *, prestate: str = "base", seed: str = "cov1", character: str = "Ironclad"
) -> list[OptionResult]:
    """Breadth-first over every option and follow-up page of one event.

    The first row is always the ENTRY drive; every later row is one option.
    """
    root, roots = _drive(event_id, prestate, [], seed=seed, character=character)
    results: list[OptionResult] = [root]
    if root.failed:
        logger.warning("FAIL %s", root.describe())
    if not roots:
        return results

    frontier: deque[list[int]] = deque([o["index"]] for o in roots)
    seen: set[tuple[int, ...]] = set()
    dropped_depth = 0
    while frontier and len(results) < MAX_PATHS_PER_EVENT:
        path = frontier.popleft()
        key = tuple(path)
        if key in seen:
            continue
        seen.add(key)

        res, next_opts = _drive(event_id, prestate, path, seed=seed, character=character)
        results.append(res)
        if res.failed:
            # Emit failures AS THEY ARE FOUND, not only in the closing report: a
            # full-roster sweep is ~30 min per pre-state, and a run that only
            # speaks at the end is indistinguishable from a stuck one.
            logger.warning("FAIL %s", res.describe())
        elif res.error:
            logger.debug("%s: %s", event_id, res.error)
        if len(path) < MAX_DEPTH:
            for o in next_opts:
                frontier.append([*path, int(o["index"])])
        else:
            dropped_depth += len(next_opts)

    # Truncation is RECORDED, never silent. Both caps fire routinely on exactly
    # the events this harness was built for: any page with >=2 options reaches
    # 2+4+...+64 = 126 candidate paths by depth 6, twice the path cap. A
    # coverage tool that cannot say what it did NOT cover reproduces, one level
    # up, the blind spot it exists to close.
    if frontier or dropped_depth:
        root.truncated = True
        root.truncated_reason = (
            f"{len(frontier)} path(s) unexplored at the {MAX_PATHS_PER_EVENT}-path cap, "
            f"{dropped_depth} dropped at depth {MAX_DEPTH}"
        )
        logger.info("%s[%s]: TRUNCATED — %s", event_id, prestate, root.truncated_reason)
    return results


def sweep_all(
    event_ids: list[str],
    *,
    prestates: list[str],
    seed: str,
    character: str,
    json_path: str | None = None,
) -> list[OptionResult]:
    """Sweep every (pre-state, event) pair.

    Args:
        json_path: Rewritten after each event, not just at the end. A full
            roster takes ~30 min per pre-state, and per repo policy a long
            experiment must leave partial results behind so it can be inspected
            — or abandoned — while still running.
    """
    out: list[OptionResult] = []
    for prestate in prestates:
        for i, event_id in enumerate(event_ids, 1):
            logger.info("[%s] %d/%d %s", prestate, i, len(event_ids), event_id)
            out.extend(sweep_event(event_id, prestate=prestate, seed=seed, character=character))
            if json_path:
                _write_json(json_path, out)
        failed = sum(1 for r in out if r.failed and r.prestate == prestate)
        logger.info("[%s] complete: %d failure(s) so far", prestate, failed)
    return out


def _write_json(path: str, results: list[OptionResult]) -> None:
    try:
        with open(path, "w", encoding="utf-8") as fh:
            json.dump([r.__dict__ for r in results], fh, indent=2)
    except OSError as exc:
        logger.warning("could not write %s: %s", path, exc)


def roster(*, seed: str = "cov1", character: str = "Ironclad") -> list[dict[str, Any]]:
    """Every event in ``ModelDb.AllEvents``, or as many as serialized.

    Warns when the C# side had to skip one: a shortened roster is silently
    reduced coverage, and this harness would sweep fewer events and still report
    success.
    """
    game = Game()
    try:
        game.start(seed=seed, character=character)
        dump = game.dump_events()
        if dump.get("skipped"):
            logger.warning(
                "event roster is SHORT: %d of %d events failed to serialize "
                "(see the [SIM] DumpEvents lines) — coverage below is partial",
                dump["skipped"],
                dump.get("total", "?"),
            )
        return list(dump.get("events") or [])
    finally:
        game.close()


def _report(results: list[OptionResult], *, allow: set[tuple[str, str]] | None = None) -> int:
    allow = allow or set()
    failures = [r for r in results if r.failed and (r.event_id, r.text_key) not in allow]
    waived = [r for r in results if r.failed and (r.event_id, r.text_key) in allow]
    harness = [r for r in results if r.error and not r.failed]
    no_change = [r for r in results if not r.failed and not r.error and not r.state_delta]
    truncated = [r for r in results if r.truncated]
    empty_entry = [r for r in results if r.entry_empty_expected]

    print(f"\nrows recorded     : {len(results)}  (1 entry row per event + 1 per option)")
    print(f"WORLD FAILURES    : {len(failures)}")
    print(f"  throwing        : {sum(1 for r in failures if r.world_errors)}")
    print(f"  force-finished  : {sum(1 for r in failures if r.force_finished)}")
    print(f"  wire error      : {sum(1 for r in failures if r.wire_error)}")
    print(f"  entry failed    : {sum(1 for r in failures if r.entry_failed)}")
    print(f"known residuals   : {len(waived)}  (allowlisted, see KNOWN_RESIDUALS)")
    print(f"empty by design   : {len(empty_entry)}  (see EXPECTED_EMPTY_ENTRY)")
    print(f"unreached/harness : {len(harness)}")
    print(f"no state change   : {len(no_change)}  (informational, not a failure)")
    print(f"TRUNCATED events  : {len(truncated)}  (coverage is PARTIAL for these)")

    if failures:
        print("\n--- failures ---")
        for r in failures:
            print(f"  {r.describe()}")
    if empty_entry:
        print("\n--- entered clean, nothing to take (expected) ---")
        for r in empty_entry:
            print(f"  {r.event_id}[{r.prestate}]: {r.entry_empty_expected}")
    if truncated:
        print("\n--- truncated (not fully covered) ---")
        for r in truncated:
            print(f"  {r.event_id}[{r.prestate}]: {r.truncated_reason}")
    if harness:
        print("\n--- not exercised ---")
        for r in harness[:20]:
            path = "/".join(map(str, r.path)) or "-"
            print(f"  {r.event_id}[{r.prestate}] path={path}: {r.error}")
        if len(harness) > 20:
            print(f"  ... and {len(harness) - 20} more")
    return 1 if failures else 0


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--event", action="append", help="event id (repeatable); default: all")
    ap.add_argument(
        "--prestate",
        action="append",
        choices=sorted(PRESTATES),
        help="repeatable; default: base",
    )
    ap.add_argument("--character", default="Ironclad")
    ap.add_argument("--seed", default="cov1")
    ap.add_argument("--json", help="write full results here")
    ap.add_argument(
        "--no-allowlist",
        action="store_true",
        help="fail on KNOWN_RESIDUALS too (use when checking whether one is stale)",
    )
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args(argv)

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        # Timestamped, because progress is per EVENT and one event can run for
        # minutes: without a clock a slow event and a wedged one look identical,
        # and "a stuck experiment must be visible" is the whole point of logging
        # progress at all.
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
        datefmt="%H:%M:%S",
    )

    event_ids = args.event or [e["id"] for e in roster(seed=args.seed, character=args.character)]
    logger.info("sweeping %d event(s)", len(event_ids))
    results = sweep_all(
        event_ids,
        prestates=args.prestate or ["base"],
        seed=args.seed,
        character=args.character,
        json_path=args.json,
    )

    if args.json:
        _write_json(args.json, results)
        logger.info("wrote %s", args.json)
    return _report(results, allow=None if args.no_allowlist else set(KNOWN_RESIDUALS))


if __name__ == "__main__":
    sys.exit(main())
