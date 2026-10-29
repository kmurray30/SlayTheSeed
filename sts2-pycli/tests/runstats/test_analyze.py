"""Unit tests for :mod:`runstats.analyze` over a hand-built synthetic corpus
with known answers."""

from __future__ import annotations

import gzip
import json
from pathlib import Path

import pytest
from runstats.analyze import (
    _fmt_table,
    _wilson_lo,
    build_report,
    card_pick_quality,
    card_winrate,
    combos,
    deck_aggression,
    event_choice_winrate,
    main,
    render_markdown,
    synergy_usage,
)
from runstats.load import Run, card_label


def _run(*, win: bool, deck: list[str], decisions: list[dict] | None = None) -> Run:
    """Minimal Run: `deck` is base tokens without the CARD. prefix for brevity."""
    data = {
        "win": win,
        "seed": "0",
        "ascension": 0,
        "killed_by_encounter": "NONE.NONE",
        "players": [
            {
                "deck": [
                    {"id": f"CARD.{c}", "floor_added_to_deck": 1, "upgrades": 0} for c in deck
                ],
                "relics": [],
            }
        ],
        "sts2sim": {
            "meta": {
                "policy": "ppo",
                "combat_budget": "eval-cheap",
                "outcome": "won" if win else "lost",
                "steps": 1,
            },
            "decisions": decisions or [],
        },
    }
    return Run(data, Path("mem"))


def test_card_label_strips_char_suffix_and_upgrade():
    assert card_label("CARD.STRIKE_IRONCLAD") == "Strike"
    assert card_label("CARD.SHRUG_IT_OFF+") == "Shrug It Off"
    assert card_label("CARD.BASH") == "Bash"


def test_card_winrate_lift_and_threshold():
    # WIN_CARD present in all 4 wins; LOSE_CARD present in the 1 loss only.
    runs = [
        _run(win=True, deck=["WIN_CARD", "STRIKE_IRONCLAD"]),
        _run(win=True, deck=["WIN_CARD", "STRIKE_IRONCLAD"]),
        _run(win=True, deck=["WIN_CARD"]),
        _run(win=True, deck=["WIN_CARD"]),
        _run(win=False, deck=["LOSE_CARD"]),
    ]
    rows = card_winrate(runs, min_n=4)  # base win rate = 0.8
    by_tok = {r["token"]: r for r in rows}
    assert "CARD.WIN_CARD" in by_tok
    assert by_tok["CARD.WIN_CARD"]["winrate"] == 1.0
    assert by_tok["CARD.WIN_CARD"]["lift_vs_base"] > 0
    # LOSE_CARD (n=1) is below min_n and excluded.
    assert "CARD.LOSE_CARD" not in by_tok


def test_pick_quality_picked_vs_skipped():
    # GOOD picked in a win; skipped in a loss → positive delta.
    decisions_win = [
        {
            "offered": {
                "kind": "card_reward",
                "options": ["CARD.GOOD", "CARD.BAD"],
                "picked": "CARD.GOOD",
            }
        }
    ]
    decisions_loss = [
        {
            "offered": {
                "kind": "card_reward",
                "options": ["CARD.GOOD", "CARD.BAD"],
                "picked": "CARD.BAD",
            }
        }
    ]
    runs = [
        _run(win=True, deck=["GOOD"], decisions=decisions_win),
        _run(win=True, deck=["GOOD"], decisions=decisions_win),
        _run(win=False, deck=["BAD"], decisions=decisions_loss),
        _run(win=False, deck=["BAD"], decisions=decisions_loss),
    ]
    rows = {r["token"]: r for r in card_pick_quality(runs, min_n=1)}
    assert rows["CARD.GOOD"]["winrate_if_picked"] == 1.0
    assert rows["CARD.GOOD"]["winrate_if_skipped"] == 0.0
    assert rows["CARD.GOOD"]["pick_delta"] == 1.0


def test_event_choice_winrate():
    decisions_a = [{"offered": {"kind": "event", "event_id": "EVENT.X", "choice_idx": 0}}]
    decisions_b = [{"offered": {"kind": "event", "event_id": "EVENT.X", "choice_idx": 1}}]
    runs = [
        _run(win=True, deck=["A"], decisions=decisions_a),
        _run(win=True, deck=["A"], decisions=decisions_a),
        _run(win=False, deck=["A"], decisions=decisions_b),
        _run(win=False, deck=["A"], decisions=decisions_b),
    ]
    rows = {(r["event"], r["choice_idx"]): r for r in event_choice_winrate(runs, min_n=1)}
    assert rows["EVENT.X", 0]["winrate"] == 1.0
    assert rows["EVENT.X", 1]["winrate"] == 0.0


def test_combos_pair_winrate():
    runs = [
        _run(win=True, deck=["A", "B"]),
        _run(win=True, deck=["A", "B"]),
        _run(win=False, deck=["A", "C"]),
    ]
    rows = {r["pair"]: r for r in combos(runs, min_co=2)}
    ab = f"{card_label('CARD.A')} + {card_label('CARD.B')}"
    assert rows[ab]["co_count"] == 2
    assert rows[ab]["winrate"] == 1.0


def test_synergy_usage_counts_plays():
    plays = [{"offered": {"kind": "combat", "card": "CARD.BODY_SLAM", "round": 1, "board": {}}}]
    runs = [
        _run(win=True, deck=["INFLAME", "BODY_SLAM"], decisions=plays),
        _run(win=False, deck=["INFLAME", "BODY_SLAM"], decisions=[]),  # holds but never plays
    ]
    rows = {r["synergy"]: r for r in synergy_usage(runs)}
    syn = rows["Inflame strength → Body Slam"]
    assert syn["n_holders"] == 2
    assert syn["used_rate"] == 0.5  # one of two holders actually played it
    assert syn["avg_payoff_plays"] == 0.5


def test_deck_aggression_split():
    fast = [{"offered": {"kind": "combat", "card": "CARD.X", "round": 1, "board": {"enemies": []}}}]
    slow = [
        {"offered": {"kind": "combat", "card": "CARD.X", "round": r, "board": {"enemies": []}}}
        for r in (1, 2, 3)
    ]
    runs = [
        _run(win=True, deck=["X"], decisions=fast),
        _run(win=False, deck=["X"] * 30, decisions=slow),
    ]
    agg = deck_aggression(runs)
    assert agg["wins"]["n"] == 1
    assert agg["losses"]["avg_deck_size"] == 30
    assert agg["losses"]["avg_fight_turns"] >= agg["wins"]["avg_fight_turns"]


# ── thresholds & degenerate inputs ────────────────────────────────────
def test_wilson_lo_zero_sample():
    assert _wilson_lo(0, 0) == 0.0
    assert _wilson_lo(0, 4) == 0.0
    assert 0.0 < _wilson_lo(4, 4) < 1.0


def test_pick_quality_drops_rarely_offered_cards():
    decisions = [
        {
            "offered": {
                "kind": "card_reward",
                "options": ["CARD.GOOD", "CARD.BAD"],
                "picked": "CARD.GOOD",
            }
        }
    ]
    runs = [_run(win=True, deck=["GOOD"], decisions=decisions) for _ in range(3)]
    assert card_pick_quality(runs, min_n=10) == []
    assert {r["token"] for r in card_pick_quality(runs, min_n=3)} == {"CARD.GOOD", "CARD.BAD"}


def test_pick_quality_never_skipped_card_has_no_delta():
    decisions = [
        {"offered": {"kind": "card_reward", "options": ["CARD.ONLY"], "picked": "CARD.ONLY"}}
    ]
    runs = [_run(win=True, deck=["ONLY"], decisions=decisions) for _ in range(2)]
    (row,) = card_pick_quality(runs, min_n=1)
    assert row["pick_rate"] == 1.0
    assert row["winrate_if_skipped"] is None
    assert row["pick_delta"] is None


def test_event_choice_winrate_drops_rare_choices():
    decisions = [{"offered": {"kind": "event", "event_id": "EVENT.X", "choice_idx": 0}}]
    runs = [_run(win=True, deck=["A"], decisions=decisions) for _ in range(3)]
    assert event_choice_winrate(runs, min_n=10) == []
    assert len(event_choice_winrate(runs, min_n=3)) == 1


def test_deck_aggression_empty_side_is_empty_dict():
    runs = [_run(win=True, deck=["X"], decisions=[])]
    agg = deck_aggression(runs)
    assert agg["losses"] == {}
    assert agg["wins"]["n"] == 1
    assert agg["wins"]["avg_fight_turns"] is None  # no combats recorded


def test_synergy_usage_reports_zero_holders():
    runs = [_run(win=True, deck=["UNRELATED"], decisions=[])]
    rows = {r["synergy"]: r for r in synergy_usage(runs)}
    assert rows["Havoc value"] == {"synergy": "Havoc value", "n_holders": 0}


# ── report assembly / rendering ───────────────────────────────────────
def _corpus_runs(n: int = 10) -> list[Run]:
    """n runs (half won) sharing a 2-card deck, one card reward, one event
    and a 2-round fight each — enough to clear every build_report threshold."""
    runs = []
    for i in range(n):
        win = i % 2 == 0
        picked = "CARD.GOOD" if win else "CARD.BAD"
        decisions = [
            {
                "offered": {
                    "kind": "card_reward",
                    "options": ["CARD.GOOD", "CARD.BAD"],
                    "picked": picked,
                }
            },
            {"offered": {"kind": "event", "event_id": "EVENT.X", "choice_idx": 0 if win else 1}},
            {"offered": {"kind": "combat", "card": "CARD.HAVOC", "round": 1, "board": {}}},
            {"offered": {"kind": "combat", "card": "CARD.HAVOC", "round": 2, "board": {}}},
        ]
        runs.append(_run(win=win, deck=["ALPHA", "HAVOC"], decisions=decisions))
    return runs


def test_build_report_fills_every_section():
    report = build_report(_corpus_runs(), min_n=2)
    assert report["n_runs"] == 10
    assert report["win_rate"] == 0.5
    assert report["policy"] == "ppo"
    assert report["combat_budget"] == "eval-cheap"
    assert {r["token"] for r in report["card_winrate"]} == {"CARD.ALPHA", "CARD.HAVOC"}
    assert {r["token"] for r in report["card_pick_quality"]} == {"CARD.GOOD", "CARD.BAD"}
    assert report["combos"][0]["co_count"] == 10
    assert {(r["event"], r["choice_idx"]) for r in report["event_choice_winrate"]} == {
        ("EVENT.X", 0),
        ("EVENT.X", 1),
    }
    havoc = next(r for r in report["synergy_usage"] if r["synergy"] == "Havoc value")
    assert havoc["n_holders"] == 10 and havoc["used_rate"] == 1.0
    assert report["deck_aggression"]["wins"]["n"] == 5


def test_fmt_table_empty_and_limit():
    assert _fmt_table([], ["a"]) == "_(no rows above threshold)_\n"
    rows = [{"a": i, "b": "x"} for i in range(5)]
    out = _fmt_table(rows, ["a", "b"], limit=2)
    assert out.splitlines() == ["| a | b |", "| --- | --- |", "| 0 | x |", "| 1 | x |"]


def test_fmt_table_blanks_missing_columns():
    out = _fmt_table([{"a": 1}], ["a", "missing"])
    assert out.splitlines()[-1] == "| 1 |  |"


def test_render_markdown_has_every_section():
    md = render_markdown(build_report(_corpus_runs(), min_n=2))
    assert md.startswith("# Run-corpus stats — 10 runs, win rate 50.0% (policy=ppo, eval-cheap)")
    for heading in (
        "## Card win-rate",
        "## Best & worst by lift",
        "## Pick quality",
        "## Top card combos",
        "## Event-choice win-rate",
        "## Synergy usage",
        "## Deck aggression",
    ):
        assert heading in md
    assert md.rstrip().endswith("|")  # the aggression table is last
    assert "| wins | 5 |" in md
    assert "| losses | 5 |" in md


# ── CLI ───────────────────────────────────────────────────────────────
def _write_corpus(tmp_path: Path, runs: list[Run]) -> Path:
    for i, run in enumerate(runs):
        (tmp_path / f"{i}.run.gz").write_bytes(gzip.compress(json.dumps(run.data).encode()))
    return tmp_path


def test_main_writes_markdown_and_json(tmp_path, capsys):
    corpus = _write_corpus(tmp_path, _corpus_runs())
    md_path = tmp_path / "report.md"
    json_path = tmp_path / "report.json"
    main([str(corpus), "--min-n", "2", "--out", str(md_path), "--json", str(json_path)])

    out = capsys.readouterr().out
    assert "# Run-corpus stats — 10 runs" in out
    assert f"markdown → {md_path}" in out
    assert f"json → {json_path}" in out
    assert md_path.read_text().startswith("# Run-corpus stats")
    assert json.loads(json_path.read_text())["n_runs"] == 10


def test_main_rejects_an_empty_corpus(tmp_path):
    with pytest.raises(SystemExit, match=r"no \.run files"):
        main([str(tmp_path)])
