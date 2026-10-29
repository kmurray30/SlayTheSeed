import json
from pathlib import Path

import pytest


def _line(step: int, kind: str, data: dict) -> str:
    return json.dumps({"step": step, "ts": "2026-05-24T00:00:00", "type": kind, "data": data})


@pytest.fixture
def combat_state() -> dict:
    """Minimal but realistic combat_play decision dict."""
    return {
        "decision": "combat_play",
        "round": 3,
        "energy": 2,
        "max_energy": 3,
        "draw_pile_count": 5,
        "discard_pile_count": 3,
        "player": {"name": "Ironclad", "hp": 58, "max_hp": 72, "block": 8},
        "player_powers": [{"name": "Strength", "amount": 2, "description": ""}],
        "enemies": [
            {
                "index": 0,
                "name": "Scroll of Biting",
                "hp": 28,
                "max_hp": 40,
                "block": 0,
                "intents": [{"type": "Attack", "damage": 12, "hits": 1}],
                "intends_attack": True,
                "powers": None,
            }
        ],
        "hand": [
            {
                "index": 0,
                "id": "CARD.STRIKE_IRONCLAD",
                "name": "Strike",
                "cost": 1,
                "type": "Attack",
                "stats": {"damage": 6},
                "description": "Deal {Damage:diff()} damage.",
                "can_play": True,
                "target_type": "AnyEnemy",
            }
        ],
    }


@pytest.fixture
def fixture_log(tmp_path: Path, combat_state: dict) -> Path:
    """A 2-step log: combat state + the action taken, then a map state."""
    p = tmp_path / "run.jsonl"
    map_state = {"decision": "map_select", "round": 0}
    p.write_text(
        "\n".join(
            [
                _line(1, "state", combat_state),
                _line(1, "action", {"command": "play_card", "card_index": 0, "target_index": 0}),
                _line(2, "state", map_state),
            ]
        )
        + "\n"
    )
    return p
