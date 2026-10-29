"""Regression: GameLogger must serialize engine state dicts that carry raw
``bytes`` values.

The Rust backend ships a combat ``ResolveChoice``'s ``selection`` field as a
PyO3 ``Vec<u8>`` — i.e. Python ``bytes`` — inside the state dict at a
``combat_resolve_choice`` decision point.  ``BranchingLiveSession.quit()`` walks
the active path and ``log_state``s every node, so a run whose path includes such
a decision used to blow up with ``TypeError: Object of type bytes is not JSON
serializable`` → HTTP 500 on ``/api/live/quit`` and ``/api/live/start-faithful``
(which quits the prior run first)."""

import json
from pathlib import Path

from game_log import GameLogger


def test_log_state_serializes_bytes(tmp_path):
    logger = GameLogger("Ironclad", "run_1", enabled=True, log_dir=str(tmp_path))
    # A state shaped like a Rust combat resolve-choice decision: `selection` is
    # raw bytes (empty == skip, b"\x00" == picked option 0).
    logger.log_state({"decision": "combat_resolve_choice", "selection": b"\x00\x02"})
    logger.log_state({"decision": "combat_resolve_choice", "selection": b""})
    logger.close()

    lines = [
        json.loads(ln)
        for ln in Path(logger.path).read_text(encoding="utf-8").splitlines()
        if ln.strip()
    ]
    states = [e for e in lines if e.get("type") == "state"]
    assert len(states) == 2
    # bytes round-trip as a JSON list of ints (lossless, matches how
    # reasoning._choice_key reads `selection`).
    assert states[0]["data"]["selection"] == [0, 2]
    assert states[1]["data"]["selection"] == []


def test_log_action_serializes_bytes(tmp_path):
    logger = GameLogger("Ironclad", "run_1", enabled=True, log_dir=str(tmp_path))
    logger.log_action({"cmd": "action", "action": "choose", "args": {"selection": b"\x01"}})
    logger.close()

    lines = [
        json.loads(ln)
        for ln in Path(logger.path).read_text(encoding="utf-8").splitlines()
        if ln.strip()
    ]
    actions = [e for e in lines if e.get("type") == "action"]
    assert len(actions) == 1
    assert actions[0]["data"]["args"]["selection"] == [1]
