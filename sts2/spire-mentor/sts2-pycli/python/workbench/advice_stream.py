"""Stream a slow advisor's progress to the UI as newline-delimited JSON.

The advisors (card advisor, decision advisor) fork the run and play the rest
of the act out many times per option — seconds to minutes of CPU. Their
streaming endpoints run that on a worker thread and hand the caller a line
per event as it happens: one ``{"type": "start", ...}`` (candidate list +
total playout count), a ``{"type": "tick", ...}`` after every completed
playout, and finally a ``{"type": "result", ...}`` carrying the full advice
payload — or ``{"type": "error", ...}`` if the decision isn't advisable or
the advisor failed. An HTTP status can't change once the body has begun, so
"not advisable" is an ``error`` event on a 200, not a 409.

Both streaming routes used to carry this thread + queue + NDJSON plumbing by
hand, byte-identical; :func:`stream_advice` is the one copy, and the only
thing the routes still know is which advisor to run.
"""

from __future__ import annotations

import json
import queue
import threading
from collections.abc import Callable, Iterator
from typing import Any

#: What an advisor reports through its ``progress`` callback: a dict carrying
#: a ``phase`` (``"start"`` / ``"tick"``) plus the event's fields.
Progress = Callable[[dict[str, Any]], None]

#: One advisor run: given a progress callback, return the advice payload, or
#: ``None`` when the active decision is not one this advisor covers.
AdvisorRun = Callable[[Progress], dict[str, Any] | None]


def advice_events(run: AdvisorRun, *, unavailable: str) -> Iterator[dict[str, Any]]:
    """The event dicts an advisor run produces, in order, as they happen.

    *run* executes on a daemon worker thread; the iterator blocks on each
    event so the caller can forward it before the next one exists. Ends after
    exactly one terminal event: ``result``, or ``error`` (with *unavailable*
    as the message when *run* returns ``None``, the exception text when it
    raises).
    """
    events: queue.Queue[dict[str, Any] | None] = queue.Queue()

    def worker() -> None:
        try:
            result = run(lambda ev: events.put({"type": ev.pop("phase"), **ev}))
            if result is None:
                events.put({"type": "error", "error": unavailable})
            else:
                events.put({"type": "result", **result})
        except Exception as exc:  # surface engine/advisor failures to the UI
            events.put({"type": "error", "error": str(exc)})
        finally:
            events.put(None)  # sentinel: stream complete

    threading.Thread(target=worker, daemon=True).start()
    while True:
        ev = events.get()
        if ev is None:
            return
        yield ev


def stream_advice(run: AdvisorRun, *, unavailable: str) -> Iterator[str]:
    """:func:`advice_events` rendered as ``application/x-ndjson`` lines."""
    for ev in advice_events(run, unavailable=unavailable):
        yield json.dumps(ev) + "\n"
