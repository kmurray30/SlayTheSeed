"""Tests for hint-tag wire-up in ``play.show_combat`` (T9).

Bead: ``sts2-rs-qxyf`` (hint-T9, sts2-pycli side).

Covers:

* Alignment — when ``recommend()`` returns tags for a *mixed* hand
  (some tagged cards, some untagged status cards), each rendered card
  row starts its content at the same column offset.
* Crash isolation — a forced ``RuntimeError`` from ``recommend()``
  doesn't propagate; the menu still renders, no tag column appears,
  and the failure is logged with a traceback.
* No-hint regression — passing ``cfg=None`` (and the module-level
  ``_HINT_CFG`` patched to ``None``) yields bit-identical pre-T9 output
  with no tag column.
"""

from __future__ import annotations

import importlib.util
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
PLAY_PATH = ROOT / "python" / "play.py"

sys.path.insert(0, str(ROOT / "python"))
spec = importlib.util.spec_from_file_location("play_module_for_render_tests", PLAY_PATH)
play = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(play)

from sts2_hint import display as _hint_display  # noqa: E402
from sts2_hint.recommender import HintTag  # noqa: E402

ANSI_RE = re.compile(r"\x1b\[[0-9;]*m")


def _strip_ansi(s: str) -> str:
    return ANSI_RE.sub("", s)


def _sample_combat_state():
    """A minimal combat_play state with mixed Attack + Status cards."""
    return {
        "decision": "combat_play",
        "round": 1,
        "energy": 3,
        "max_energy": 3,
        "draw_pile_count": 5,
        "discard_pile_count": 0,
        "player": {
            "name": "Ironclad",
            "hp": 80,
            "max_hp": 80,
            "block": 0,
            "gold": 99,
            "deck_size": 10,
        },
        "enemies": [
            {"index": 0, "name": "Nibbit", "hp": 50, "max_hp": 50, "block": 0, "intents": []}
        ],
        "hand": [
            {
                "name": "Strike",
                "type": "Attack",
                "cost": 1,
                "can_play": True,
                "target_type": "AnyEnemy",
                "stats": {"damage": 6},
                "stable_idx": 0,
                "index": 0,
                "keywords": [],
            },
            {
                "name": "Defend",
                "type": "Skill",
                "cost": 1,
                "can_play": True,
                "target_type": "Self",
                "stats": {"block": 5},
                "stable_idx": 1,
                "index": 1,
                "keywords": [],
            },
            {
                "name": "Wound",
                "type": "Status",
                "cost": 0,
                "can_play": False,
                "target_type": "None",
                "stats": {},
                "stable_idx": 2,
                "index": 2,
                "keywords": ["Unplayable"],
            },
        ],
    }


def _make_tag(stable_idx: int, win_prob: float, is_best: bool = False) -> HintTag:
    return HintTag(
        stable_idx=stable_idx,
        win_prob=win_prob,
        bucket_label="likely-win" if win_prob >= 0.7 else "even",
        is_best=is_best,
        visits=100,
    )


def _find_card_rows(output: str) -> list[str]:
    """Return only the lines that look like a hand-card menu row.

    Filter on card-name keyword presence; the card-name set in
    ``_sample_combat_state`` is intentionally disjoint from the enemy
    name (``Nibbit``) so an enemy row doesn't slip through. Hand rows
    carry a playable-state bullet (``●``/``○``); the ranked-actions
    footer (``play [0] Strike``) does not, so the bullet filter keeps it
    out.
    """
    rows = []
    for line in output.splitlines():
        plain = _strip_ansi(line)
        if ("●" in plain or "○" in plain) and any(
            kw in plain for kw in ("Strike", "Defend", "Wound")
        ):
            rows.append(plain)
    return rows


def test_hint_tag_column_aligned_across_mixed_rows(monkeypatch, capsys):
    """Mixed tagged + untagged cards still share a stable name column."""
    state = _sample_combat_state()

    def fake_recommend(s, cfg):
        # recommend_all returns a flat list of tags (one per action/target);
        # _compute_hint_tags collapses it into by_idx. Only Strike is tagged.
        return [
            _make_tag(0, 0.72, is_best=True),  # Strike tagged
            # 1 (Defend) untagged on purpose
            # 2 (Wound) untagged
        ]

    monkeypatch.setattr(play, "_hint_recommend_all", fake_recommend)
    monkeypatch.setattr(play, "_HINT_AVAILABLE", True)
    monkeypatch.setattr(play, "_HINT_CFG", object())  # truthy stub config

    play.show_combat(state)
    out = capsys.readouterr().out
    rows = _find_card_rows(out)
    assert len(rows) == 3, f"expected 3 card rows, got {len(rows)}: {rows!r}"

    # The "[N] CardName" token must start at the same column in every
    # row. We locate the "[<digit>]" marker after the playable dot.
    positions = []
    for row in rows:
        m = re.search(r"\[\d+\]", row)
        assert m is not None, f"no [N] marker in row: {row!r}"
        positions.append(m.start())
    assert len(set(positions)) == 1, (
        f"hand-row '[N]' positions misaligned: {positions} rows={rows!r}"
    )

    # And at least one row carries a real tag rendering (LIKELY-WIN).
    assert any("LIKELY-WIN" in r for r in rows), f"no tag rendered: {rows!r}"


def test_crash_isolation_recommend_raises(monkeypatch, capsys):
    """A boom inside recommend() must not break combat rendering.

    Verifies (a) no exception escapes ``show_combat``, (b) no tag column
    text appears in the output, and (c) the traceback is forwarded to
    the ``sts2_hint`` logger. We stub the logger directly because it
    has ``propagate=False`` (see :mod:`sts2_hint.errors`), which blocks
    pytest's ``caplog`` fixture.
    """
    state = _sample_combat_state()

    def boom(_s, _cfg):
        raise RuntimeError("boom-hint-error")

    # Patch the list-returning recommender used by _compute_hint_tags.

    captured: list[str] = []

    class _StubLogger:
        def error(self, fmt, *args, **kwargs):
            try:
                captured.append(fmt % args if args else fmt)
            except Exception:
                captured.append(str(fmt))

    monkeypatch.setattr(play, "_hint_recommend_all", boom)
    monkeypatch.setattr(play, "_HINT_AVAILABLE", True)
    monkeypatch.setattr(play, "_HINT_CFG", object())
    monkeypatch.setattr(play, "_hint_get_logger", lambda: _StubLogger())

    # Must not raise.
    play.show_combat(state)
    out = capsys.readouterr().out
    rows = _find_card_rows(out)
    assert rows, "combat render returned no card rows after recommend() error"

    # No real tags appear — bucket labels shouldn't leak through.
    plain_out = _strip_ansi(out)
    assert "LIKELY-WIN" not in plain_out
    assert "[EVEN" not in plain_out

    # Traceback was logged with the original exception message.
    blob = "\n".join(captured)
    assert "boom-hint-error" in blob, f"expected traceback in logger; got: {blob!r}"
    assert "RuntimeError" in blob, f"expected exception class in traceback; got: {blob!r}"


def test_no_hints_when_cfg_is_none(monkeypatch, capsys):
    """With _HINT_CFG=None the render is bit-identical to pre-T9."""
    state = _sample_combat_state()

    # Force hint path off via the module-level config.
    monkeypatch.setattr(play, "_HINT_CFG", None)

    # Make recommend a tripwire — must not be called.
    def tripwire(_s, _cfg):
        raise AssertionError("recommend() called when hints are disabled")

    monkeypatch.setattr(play, "_hint_recommend", tripwire)

    play.show_combat(state)
    out = capsys.readouterr().out
    plain_out = _strip_ansi(out)
    # No tag column markers anywhere.
    assert "LIKELY-WIN" not in plain_out
    assert _hint_display.format_empty() not in plain_out, (
        "format_empty cells leaked into the no-hint render path"
    )


def test_render_hint_cell_disabled_returns_empty_string():
    """Sanity check on the helper used by the production render path."""
    # With tags=None we return literally "" — not a padded cell — so
    # disabled-hint output is bit-identical to pre-T9.
    assert play._render_hint_cell(None, 0) == ""


def _hint_result(*tags: HintTag):
    """Wrap tags in the ``_HintResult`` shape ``_render_hint_cell`` expects."""
    return play._HintResult(by_idx={t.stable_idx: t for t in tags}, all_tags=list(tags))


def test_render_hint_cell_active_no_match_pads():
    """When tags are present but a row has no entry, pad for alignment."""
    tags = _hint_result(_make_tag(99, 0.5))
    cell = play._render_hint_cell(tags, 7)
    # format_empty width + 2-space separator.
    assert len(cell) == _hint_display.TAG_WIDTH + 2
    assert cell.strip() == ""


def test_render_hint_cell_match_renders_tag():
    cell = play._render_hint_cell(_hint_result(_make_tag(7, 0.85, is_best=True)), 7)
    assert "LIKELY-WIN" in cell
