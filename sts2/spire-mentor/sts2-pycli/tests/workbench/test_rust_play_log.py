"""Engine card play-log surfaces through the live session (rust backend).

Regression guard: the play-log recording flag must be enabled on the
RunManager *after* it exists, so a manual play produces a "Play <card>" line in
the live event-log ``details``. A prior bug enabled recording before
``RustGame.start`` created the RunManager, so the flag silently no-op'd and no
play-log lines appeared. Requires the native ``sts2_rs`` extension.
"""

from __future__ import annotations

import tempfile

import pytest

pytest.importorskip("sts2_rs", reason="rust backend requires the sts2_rs extension")

from workbench.branching import BranchingLiveSession
from workbench.options import normalize_options


def _drive_to_combat(sess: BranchingLiveSession, state: dict, max_steps: int = 40) -> dict:
    for _ in range(max_steps):
        if state["decision"] == "combat_play":
            return state
        opts = normalize_options(state)
        assert opts, f"no navigable options at decision {state['decision']!r}"
        o = opts[0]
        state = sess.apply(o["action"], o.get("args") or {})["state"]
    raise AssertionError("did not reach combat within step budget")


def test_manual_play_emits_play_log_line() -> None:
    with tempfile.TemporaryDirectory() as d:
        sess = BranchingLiveSession(logs_dir=d, backend="rust", loc={})
        # Close the session before the tempdir exits -- the open
        # session log otherwise blocks rmtree on Windows (WinError 32).
        try:
            state = sess.start(character="Ironclad", seed="PLAYLOGTEST", ascension=0)
            state = _drive_to_combat(sess, state)

            hand = state.get("hand") or []
            assert hand, "combat should deal a hand"
            enemies = state.get("enemies") or []
            target = enemies[0]["index"] if enemies else None

            result = sess.apply(
                "play_card", {"card_index": hand[0]["index"], "target_index": target}
            )
            # The first detail line is the play-log entry for the card just played
            # ("Play <Card>" — the trigger, parent-first). With an empty loc map the
            # name prettifies to a title-cased id (e.g. "Defend Ironclad").
            assert any(ln.startswith("Play ") for ln in result["details"]), (
                f"expected a 'Play <card>' play-log line, got {result['details']!r}"
            )

        finally:
            sess.quit()
