"""The advisor streaming protocol, without a server or an engine."""

from __future__ import annotations

import json

from workbench.advice_stream import advice_events, stream_advice


def test_progress_then_result_in_order():
    def run(progress):
        progress({"phase": "start", "total": 2, "candidates": ["a", "b"]})
        progress({"phase": "tick", "done": 1})
        progress({"phase": "tick", "done": 2})
        return {"candidates": [{"key": "a", "is_best": True}], "config": {}}

    events = list(advice_events(run, unavailable="n/a"))
    assert [e["type"] for e in events] == ["start", "tick", "tick", "result"]
    assert events[0]["total"] == 2 and "phase" not in events[0]
    assert events[-1]["candidates"][0]["key"] == "a"


def test_none_result_is_the_unavailable_error():
    events = list(advice_events(lambda progress: None, unavailable="not at a card reward"))
    assert events == [{"type": "error", "error": "not at a card reward"}]


def test_exception_becomes_an_error_event_after_any_progress():
    def run(progress):
        progress({"phase": "start", "total": 1})
        raise RuntimeError("engine build too old")

    events = list(advice_events(run, unavailable="n/a"))
    assert events[0]["type"] == "start"
    assert events[-1] == {"type": "error", "error": "engine build too old"}
    assert len(events) == 2  # exactly one terminal event


def test_stream_advice_is_one_json_object_per_line():
    def run(progress):
        progress({"phase": "tick", "done": 1})
        return {"ok": True}

    lines = list(stream_advice(run, unavailable="n/a"))
    assert all(line.endswith("\n") for line in lines)
    assert [json.loads(line)["type"] for line in lines] == ["tick", "result"]
