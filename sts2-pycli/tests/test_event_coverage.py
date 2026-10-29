"""Gate for the event/option coverage harness (GH #313).

The harness itself (``python/event_coverage.py``) walks all 57 events and is far
too slow for the normal suite; this gates its *machinery* plus a small,
high-signal subset, so a regression in either the harness or the events it
covers fails ``just test`` rather than waiting for a 60-seed corpus sweep to
notice.

This is a LOCAL gate. The full-roster sweep stays a manual tool — run it from
the CLI when touching event plumbing. Each option drive spawns a fresh game
process (~20 for the subset below), so keep the subset small.

What matters here is that the harness detects **non-throwing** failures. The
world-error counter keys on exception type names, so an option that resolves
without faulting but silently drops its state effect — the C# side prints
``Event <X> didn't advance, force-finishing`` — is invisible to it. That class
is why this harness exists.
"""

from __future__ import annotations

import event_coverage as ec
import pytest
from sts2_client import Game, oracle_available

# Every test here drives the C# oracle (directly or through event_coverage).
pytestmark = pytest.mark.skipif(
    not oracle_available(),
    reason="C# oracle unavailable: lib/sts2.dll missing (run `just import-game`)",
)

# Small and deliberately chosen:
#   TRIAL           — threw on the FIRST line of Accept until 2026-07-29, so the
#                     whole event was dead in headless. Regression canary.
#   DENSE_VEGETATION— an NGame event leg fixed on 2026-07-28.
#   THE_LEGENDS_WERE_TRUE — the RewardsSet pre-state bug (needs full potions).
_SUBSET = ["TRIAL", "DENSE_VEGETATION", "THE_LEGENDS_WERE_TRUE"]

#: Options that STILL fail, on purpose, with the reason. This is an admission,
#: not a suppression: the harness is right to flag them, and a fix would remove
#: the entry. Anything not listed here is a regression and fails the build.
#:
#: Lives in ``event_coverage`` rather than here so the manual CLI and this gate
#: share ONE list — otherwise `main()` exits nonzero forever on a residual the
#: suite has already accepted, and the two drift.
_KNOWN_RESIDUALS = ec.KNOWN_RESIDUALS


def _unexpected(results) -> list[str]:
    """Failures that are not a recorded residual.

    Keyed on ``text_key``, the option's stable id, NOT on ``title``: the title is
    a localization lookup with an ``option_{i}`` fallback, so a loc-table edit
    could silently disarm an entry — or re-aim it at whichever option inherited
    the string.
    """
    return [
        r.describe()
        for r in results
        if r.failed and (r.event_id, r.text_key) not in _KNOWN_RESIDUALS
    ]


@pytest.fixture(scope="module")
def event_roster() -> list[dict]:
    return ec.roster()


class TestHarnessMachinery:
    def test_roster_is_populated_and_ids_are_usable(self, event_roster) -> None:
        assert len(event_roster) > 20, "ModelDb.AllEvents returned a suspiciously short roster"
        ids = {e["id"] for e in event_roster}
        for known in _SUBSET:
            assert known in ids, f"{known} missing from the event roster"

    def test_watch_stderr_retains_non_exception_lines(self) -> None:
        """``watch_stderr`` must see lines ``_WORLD_ERROR_RE`` cannot.

        Guards the mechanism the whole non-throwing-failure check rests on: a
        pattern that names no exception type still has to be retained.
        """
        g = Game(watch_stderr=r"Run launched")
        try:
            g.start(seed="watchprobe")
            hits = g.stderr_watch()
            assert hits, "watch_stderr retained nothing for a line that is definitely printed"
            assert all("Exception" not in h for h in hits)
            assert g.world_errors()["count"] == 0, "a benign line must not score as a world error"
            assert g.stderr_watch(clear=True)
            assert g.stderr_watch() == []
        finally:
            g.close()

    def test_option_result_failed_covers_both_classes(self) -> None:
        base = {"event_id": "X", "prestate": "base", "path": [0], "title": "t"}
        assert not ec.OptionResult(**base).failed
        assert ec.OptionResult(**base, world_errors=1).failed
        # The point of the harness: a silent drop is a failure with zero throws.
        silent = ec.OptionResult(**base, force_finished=True)
        assert silent.failed
        assert silent.world_errors == 0
        # Harness trouble is NOT a world failure -- it must not be reported as one.
        assert not ec.OptionResult(**base, error="enter_room: nope").failed
        # Nor is an entry that is empty because the GAME offers nothing there.
        assert ec.OptionResult(**base, entry_failed="presented no options").failed
        assert not ec.OptionResult(**base, entry_empty_expected="presented no options").failed


class TestSubsetIsClean:
    @pytest.mark.parametrize("event_id", _SUBSET)
    def test_no_world_failure_on_any_option(self, event_id: str) -> None:
        results = ec.sweep_event(event_id, prestate="base")
        assert results, f"{event_id} presented no options at all"
        unexpected = _unexpected(results)
        assert not unexpected, "world failure(s):\n" + "\n".join(unexpected)

    def test_full_potion_belt_does_not_break_a_potion_reward(self) -> None:
        """``RewardsSet.Offer()`` asserts every offered reward was taken.

        ``PotionReward.OnSelect`` returns false by design on a full belt, so
        before the 2026-07-29 fix this threw AND force-finished. Option coverage
        alone cannot find it -- it is a PRE-STATE bug, which is why the harness
        carries a pre-state axis at all.
        """
        results = ec.sweep_event("THE_LEGENDS_WERE_TRUE", prestate="full_potions")
        assert results, "THE_LEGENDS_WERE_TRUE presented no options at all"
        unexpected = _unexpected(results)
        assert not unexpected, "world failure(s):\n" + "\n".join(unexpected)

    def test_zero_option_event_entry_is_recorded_but_not_a_failure(self) -> None:
        """``FAKE_MERCHANT`` presents nothing on entry, by design.

        ``GenerateInitialOptions()`` returns an empty array — the event is a
        custom shop node whose only interaction is throwing a FoulPotion. The
        entry row must still EXIST (an event nobody records is an event nobody
        notices going missing), just not score as a world failure.
        """
        results = ec.sweep_event("FAKE_MERCHANT", prestate="base")
        assert results, "the entry row must be recorded even with zero options"
        assert all(r.entry_empty_expected for r in results)
        assert not _unexpected(results)

    def test_repeat_option_loop_is_not_force_finished(self) -> None:
        """GH #316: an event may re-present the SAME options and still advance.

        ``AbyssalBaths.Linger`` re-offers ``…options.LINGER`` verbatim on every
        page — only the DESCRIPTION key moves (LINGER1, LINGER2, …) — while its
        effects compound (+2 max HP, unblockable damage that grows by 1 each
        time). The old TextKey-identity guard read the repeat as a no-op and cut
        the event after ONE Linger, deleting the whole risk/reward curve; it did
        the same to SlipperyBridge once ``GetHoldOnSuffix`` saturates at "LOOP".

        Driven directly rather than through ``sweep_event``: this walks ONE deep
        path in ONE process instead of a breadth-first fan-out, and depth is
        exactly what regressed.
        """
        g = Game(watch_stderr=ec.FORCE_FINISH_RE)
        try:
            state = g.start(seed="cov1", character="Ironclad")
            state = g.enter_room("event", event="ABYSSAL_BATHS")
            immerse = next(o for o in state["options"] if o["text_key"].endswith("options.IMMERSE"))
            state = g.act("choose_option", option_index=immerse["index"])
            seen = []
            for i in range(4):
                linger = next(
                    (o for o in (state.get("options") or []) if o["text_key"].endswith(".LINGER")),
                    None,
                )
                assert linger is not None, f"Linger {i + 1} was not offered: {state}"
                state = g.act("choose_option", option_index=linger["index"])
                assert state.get("decision") == "event_choice", (
                    f"event ended after Linger {i + 1}: {state}"
                )
                seen.append((state["player"]["hp"], state["player"]["max_hp"]))
            g.get_state()  # barrier: let the stderr drain thread catch up
            assert not g.stderr_watch(clear=True), "the Linger loop was force-finished"
            assert g.world_errors()["count"] == 0
            # Each Linger is a real, compounding effect, not a repeated no-op.
            max_hps = [m for _, m in seen]
            assert max_hps == sorted(set(max_hps)), f"max HP did not keep rising: {seen}"
            damage = [b - a for (a, _), (b, _) in zip(seen[1:], seen[:-1])]
            assert damage == sorted(set(damage)), f"damage did not escalate: {seen}"
        finally:
            g.close()

    def test_known_residuals_are_still_real(self) -> None:
        """A recorded residual that stopped failing must be REMOVED, not left.

        Otherwise the allowlist silently grows into a place where real
        regressions can hide behind a stale entry.

        Scoped to whatever events the allowlist actually names, not hardcoded to
        TRIAL: an entry added for any other event used to get no staleness check
        at all, which is the same rot one level down.
        """
        events = sorted({k[0] for k in _KNOWN_RESIDUALS})
        assert events, "no residuals recorded — delete this test with the last one"
        still_failing: set[tuple[str, str]] = set()
        for event_id in events:
            still_failing |= {
                (r.event_id, r.text_key)
                for r in ec.sweep_event(event_id, prestate="base")
                if r.failed
            }
        stale = set(_KNOWN_RESIDUALS) - still_failing
        assert not stale, f"residual(s) now pass — delete them from KNOWN_RESIDUALS: {stale}"
