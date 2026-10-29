import json
from pathlib import Path

from workbench.report import render_run_markdown

FIX = Path(__file__).parent / "fixtures" / "sample_mcts_run.jsonl"


def test_render_run_markdown_has_outcome_and_mcts_table():
    md = render_run_markdown(str(FIX))
    assert "vantom_boss" in md
    assert "LOST" in md and "turn 5" in md
    assert "W/L/T" in md
    assert "0/690/0" in md  # End turn outcomes
    assert "End turn" in md
    assert "Vantom" in md and "173/173" in md and "Attack 7" in md


def test_render_run_markdown_canonical_intent_shape(tmp_path):
    """Render a log in the canonical shape the recorder now emits: enemies carry
    an ``intents`` list of ``{type, damage, hits}`` (not the legacy singular
    ``intent.label``), and names are already-resolved display strings.
    """
    log = tmp_path / "run.jsonl"
    records = [
        {"step": 0, "type": "meta", "data": {"fixture_stem": "demo_vantom_boss"}},
        {
            "step": 0,
            "type": "state",
            "data": {
                "decision": "combat_play",
                "round": 1,
                "player": {"name": "Ironclad", "hp": 52, "max_hp": 80, "block": 0, "energy": 3},
                "enemies": [
                    {
                        "name": "Vantom",
                        "hp": 173,
                        "max_hp": 173,
                        "block": 0,
                        "intents": [{"type": "Attack", "damage": 5, "hits": 3}],
                    }
                ],
                "hand": [{"index": 0, "name": "Strike"}],
            },
        },
        {
            "step": 0,
            "type": "mcts",
            "data": {
                "total_visits": 10,
                "root_outcomes": [
                    {
                        "action_label": "Strike",
                        "visits": 10,
                        "q": -1.0,
                        "wins": 0,
                        "losses": 10,
                        "ties": 0,
                        "is_best": True,
                    }
                ],
            },
        },
        {"step": 0, "type": "meta", "data": {"summary": True, "final_won": True, "final_turn": 9}},
    ]
    log.write_text("\n".join(json.dumps(r) for r in records))
    md = render_run_markdown(str(log))
    assert "Ironclad" in md
    assert "Strike" in md
    assert "Attack 5x3" in md  # rendered from {type, damage, hits}
    assert "Vantom" in md and "173/173" in md
    assert "WON" in md
