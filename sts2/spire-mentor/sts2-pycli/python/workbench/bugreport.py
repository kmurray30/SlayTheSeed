"""Serialize the current workbench moment into a self-describing bug-report dump.

The dump (``schema_version`` 1) is optimized for Claude to read from the CLI: it
carries enough to reproduce a live run (the full branch timeline) or reload a
replay (run-file + line), the captured decision-point state + map, the UI
context (screen/overlay), and an optional note. Both modes capture the full
rendered state at the viewed moment — including a run reviewed *after* it ended
(which the workbench reopens in the replay viewer). The route layer stamps
``meta.id`` and ``meta.captured_at`` at store time; this module never touches
env secrets.
"""

from __future__ import annotations

import subprocess
from pathlib import Path
from typing import Any

ENGINE_BUILD = "v0.107.1"  # the default build; a dump reports its session's own


def _git_commit() -> str:
    try:
        out = subprocess.run(
            ["git", "rev-parse", "--short", "HEAD"],
            cwd=Path(__file__).resolve().parent,
            capture_output=True,
            text=True,
            timeout=2,
            check=True,
        )
        return out.stdout.strip() or "unknown"
    except Exception:
        return "unknown"


WORKBENCH_COMMIT = _git_commit()


def build_dump(
    session: Any,
    *,
    mode: str,
    step: int | None,
    screen: str | None,
    overlay: str | None,
    note: str | None,
    run_path: str | None,
) -> dict[str, Any]:
    """Build the schema-v1 dump for ``mode`` ("live" | "replay").

    Raises ``ValueError("nothing to capture")`` when the requested mode's
    underlying state is absent.
    """
    ui = {"screen": screen, "overlay": overlay}

    if mode == "live":
        live = session.live
        if live is None:
            raise ValueError("nothing to capture")
        meta = {
            "mode": "live",
            "character": live.character,
            "seed": live.seed,
            "ascension": live.ascension,
            "backend": live.backend,
        }
        engine_build = getattr(live, "game_version", None)
        if not isinstance(engine_build, str) or not engine_build:
            engine_build = ENGINE_BUILD
        repro = {"timeline": live.timeline()}
        state = {"game_state": live.state(), "map": live.map()}
    elif mode == "replay":
        store = session.store
        if store is None:
            raise ValueError("nothing to capture")
        meta = {
            "mode": "replay",
            "character": store.character(),
            "seed": None,
            "ascension": None,
            "backend": None,
        }
        ident = store.run_identity() if hasattr(store, "run_identity") else None
        engine_build = ident.get("game_version") if isinstance(ident, dict) else None
        engine_build = engine_build or ENGINE_BUILD
        # A run that ENDED reopens in this replay viewer (the live engine is
        # released on quit), so this is the path a *post-session* bug report
        # takes — it must still carry the rendered state at the viewed step, not
        # just the repro recipe. ``store.get(step)`` is the same Step the replay
        # viewer renders; its ``state`` is the full decision-point state.
        game_state = None
        if step is not None and 0 <= step < len(store):
            game_state = store.get(step).get("state")
        repro = {
            "run_file": run_path if run_path is not None else store.fixture_path(),
            "line": step,
            "step_count": len(store),
        }
        state = {"game_state": game_state, "map": None}
    else:
        raise ValueError(f"unknown mode: {mode}")

    # The build the captured run simulates (GH #801) — the session's, not the
    # process default; a replay of a log that predates the field is v0.107.1.
    meta["engine_build"] = engine_build
    meta["workbench_commit"] = WORKBENCH_COMMIT
    return {
        "schema_version": 1,
        "meta": meta,
        "repro": repro,
        "state": state,
        "ui": ui,
        "note": note,
    }
