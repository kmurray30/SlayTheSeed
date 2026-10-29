"""scripts/bug_reports.py — the pure parts (no wrangler, no network)."""

from __future__ import annotations

import importlib.util
import json
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
_spec = importlib.util.spec_from_file_location("bug_reports", ROOT / "scripts" / "bug_reports.py")
assert _spec and _spec.loader
bug_reports = importlib.util.module_from_spec(_spec)
sys.modules["bug_reports"] = bug_reports
_spec.loader.exec_module(bug_reports)


def test_namespace_id_reads_the_production_binding_from_the_shipped_wrangler_toml():
    text = (ROOT / "web" / "wrangler.toml").read_text()
    ns = bug_reports.namespace_id(text)
    assert len(ns) == 32 and all(c in "0123456789abcdef" for c in ns)
    with pytest.raises(SystemExit):
        bug_reports.namespace_id(text, binding="NOPE")


def test_key_list_parses_past_the_banner_and_sorts_newest_first():
    rows = [
        {
            "name": "bugreports/wb-aaaaaaaaaaaaaaaa",
            "metadata": {
                "captured_at": "2026-09-01T00:00:00Z",
                "mode": "live",
                "character": "Silent",
                "screen": "combat_play",
                "build": "abc",
                "note": "x\ny",
            },
        },
        {
            "name": "bugreports/wb-bbbbbbbbbbbbbbbb",
            "metadata": {
                "captured_at": "2026-09-03T00:00:00Z",
                "mode": "replay",
                "character": "Ironclad",
            },
        },
        {"name": "other/thing", "metadata": None},
        {"name": "bugreports/wb-cccccccccccccccc"},
    ]
    stdout = " ⛅️ wrangler 4.127.0\n────\n" + json.dumps(rows)
    headers = bug_reports.listing(bug_reports.parse_key_list(stdout))
    assert [h["id"] for h in headers] == [
        "wb-bbbbbbbbbbbbbbbb",
        "wb-aaaaaaaaaaaaaaaa",
        "wb-cccccccccccccccc",
    ]
    assert headers[1]["note"] == "x\ny"
    assert bug_reports.listing(rows, limit=1)[0]["id"] == "wb-bbbbbbbbbbbbbbbb"
    text = bug_reports.format_listing(headers)
    assert "wb-bbbbbbbbbbbbbbbb" in text and "x y" in text
    assert bug_reports.format_listing([]) == "no bug reports stored"
    with pytest.raises(SystemExit):
        bug_reports.parse_key_list("no json here")


def test_unpack_writes_the_dump_and_the_run_it_carries(tmp_path: Path):
    dump = {
        "schema_version": 1,
        "meta": {"id": "wb-0123456789abcdef", "mode": "replay"},
        "repro": {"run_file": "r.jsonl", "line": 3, "run_log": '{"type":"meta"}\n{"type":"state"}'},
        "state": {},
        "ui": {"screen": None, "overlay": None},
        "note": None,
    }
    written = bug_reports.unpack(dump, tmp_path / "reports", "wb-0123456789abcdef")
    assert [p.name for p in written] == ["wb-0123456789abcdef.json", "wb-0123456789abcdef.jsonl"]
    assert json.loads(written[0].read_text())["meta"]["id"] == "wb-0123456789abcdef"
    assert written[1].read_text() == '{"type":"meta"}\n{"type":"state"}\n'
    live = {**dump, "repro": {"persist": {"character": "Silent", "nodes": []}}}
    written = bug_reports.unpack(live, tmp_path / "reports", "wb-fedcba9876543210")
    assert [p.name for p in written] == [
        "wb-fedcba9876543210.json",
        "wb-fedcba9876543210.persist.json",
    ]
    assert json.loads(written[1].read_text()) == {"character": "Silent", "nodes": []}
