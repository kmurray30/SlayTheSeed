"""Live, human-readable debug log for a workbench branching session.

Unlike :class:`game_log.GameLogger` — a JSONL state/action recording that the
:class:`~workbench.branching.BranchingLiveSession` only flushes to disk on
``quit`` — this writes a **plain-text trace as the session runs**, one line per
event, ``flush()``-ed immediately so it survives a crash and can be ``tail -f``'d.

It records not just game actions but workbench-level navigation — seeking back to
an earlier node, forking a new branch — so a confusing history ("why did it spawn
after it died?") can be reconstructed: *did the user rewind and take a different
line?* Each event also carries a curated pre/post state snapshot (player hp/block
/powers and ``enemies=[index:name hp/block]``) so an enemy that appears to die and
respawn can be checked against the raw indices and HP, not just the prose diff.

Each line is ``<iso-ts> <KIND>  <message>``; details/snapshots are indented under
their event.
"""

from __future__ import annotations

import os
from datetime import datetime
from typing import Any

from game_log import LOG_DIR


def snapshot(state: Any) -> str:
    """One-line curated summary of a (name-resolved) state for the debug log.

    Surfaces exactly the fields needed to diagnose enemy life-cycle confusion:
    the decision, the player's hp/block/powers, and each enemy's
    ``index``/``name``/``hp``/``block``. Defensive: missing keys degrade to
    ``?`` rather than raising.
    """
    if not isinstance(state, dict):
        return "?"
    p = state.get("player") or {}
    powers = ",".join(f"{pw.get('name')}{pw.get('amount')}" for pw in (p.get("powers") or []))
    hp, mhp = p.get("hp"), p.get("max_hp")
    you = f"you={hp}/{mhp}" if mhp is not None else f"you={hp}"
    you += f" blk={p.get('block', 0)}"
    if powers:
        you += f" [{powers}]"
    enemies = ", ".join(
        f"{e.get('index')}:{e.get('name')} hp={e.get('hp')} blk={e.get('block', 0)}"
        for e in (state.get("enemies") or [])
    )
    return f"decision={state.get('decision', '?')} {you} enemies=[{enemies}]"


class SessionLog:
    """Append-only text debug log for one branching session.

    Best-effort: any I/O error disables the log rather than breaking a live run.
    A disabled instance is a silent no-op.
    """

    def __init__(
        self,
        logs_dir: str | None,
        character: str,
        seed: str,
        ascension: int,
        backend: str,
        enabled: bool = True,
    ) -> None:
        self.enabled = enabled
        self._file = None
        self._path: str | None = None
        if not enabled:
            return
        out_dir = logs_dir or LOG_DIR
        try:
            os.makedirs(out_dir, exist_ok=True)
            ts = datetime.now().strftime("%Y%m%d_%H%M%S")
            safe_seed = str(seed).replace("/", "_")
            self._path = os.path.join(out_dir, f"{ts}_{character}_{safe_seed}_session.log")
            # Explicit utf-8: log lines contain "→" etc.; Windows' default
            # cp1252 raises UnicodeEncodeError mid-run (2026-07-12).
            self._file = open(self._path, "w", encoding="utf-8")
        except OSError:
            self.enabled = False
            self._file = None
            return
        self.line("START", f"{character} seed={seed} A{ascension} backend={backend}")

    def line(self, kind: str, msg: str, details: list[str] | None = None) -> None:
        """Write one timestamped ``<KIND> <msg>`` record, plus indented details."""
        if not self.enabled or self._file is None:
            return
        ts = datetime.now().isoformat(timespec="milliseconds")
        try:
            self._file.write(f"{ts} {kind:<7} {msg}\n")
            for d in details or []:
                self._file.write(f"{ts} {'':<7}     {d}\n")
            self._file.flush()
        except OSError:
            self.enabled = False

    def close(self) -> None:
        if self._file is not None:
            try:
                self._file.close()
            except OSError:
                pass
            self._file = None

    @property
    def path(self) -> str | None:
        return self._path
