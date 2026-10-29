"""Tests for CLI play helpers."""

from __future__ import annotations

import importlib.util
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
PLAY_PATH = ROOT / "python" / "play.py"

sys.path.insert(0, str(ROOT / "python"))
spec = importlib.util.spec_from_file_location("play_module_for_tests", PLAY_PATH)
play = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(play)


def test_quit_save_defaults_to_save_dir(monkeypatch):
    monkeypatch.setattr("builtins.input", lambda prompt="": "y")

    path = play._quit_with_save(None, "Ironclad", "seed123")

    assert path is not None
    assert path.startswith(play.SAVE_DIR)
    assert path.endswith(".save")


def test_boss_monster_key_strips_boss_suffix():
    assert play._boss_monster_key("VANTOM_BOSS") == "VANTOM"


def test_boss_monster_key_remaps_the_kin():
    assert play._boss_monster_key("THE_KIN_BOSS") == "KIN_PRIEST"
    assert play._boss_monster_key("THE_KIN") == "KIN_PRIEST"


def test_boss_monster_key_passes_plain_ids_through():
    assert play._boss_monster_key("VANTOM") == "VANTOM"
    assert play._boss_monster_key("") == ""


def test_boss_known_moves_includes_localized_titles():
    moves = play._boss_known_moves("VANTOM")
    assert "Dismember" in moves
    assert "Inky Lance" in moves
    # Order is insertion order from cache; just check no duplicates.
    assert len(moves) == len(set(moves))


def test_boss_known_moves_unknown_monster_is_empty():
    assert play._boss_known_moves("NOT_A_REAL_BOSS") == []


def _sample_player():
    return {
        "relics": [
            {
                "name": "Akabeko",
                "description": "At the start of each combat, gain {Vigor} Vigor.",
                "vars": {"Vigor": 8},
            },
            {
                "name": "Anchor",
                "description": "Start each combat with {Block} Block.",
                "vars": {"Block": 10},
            },
        ],
        "potions": [
            {
                "index": 0,
                "name": "Block Potion",
                "description": "Gain {Block} Block.",
                "vars": {"Block": 12},
            },
        ],
    }


def test_resolve_relic_query_by_index():
    relics = _sample_player()["relics"]
    assert play._resolve_relic_query(relics, "1")["name"] == "Anchor"


def test_resolve_relic_query_by_name_substring_case_insensitive():
    relics = _sample_player()["relics"]
    assert play._resolve_relic_query(relics, "akab")["name"] == "Akabeko"
    assert play._resolve_relic_query(relics, "ANCHOR")["name"] == "Anchor"


def test_resolve_relic_query_returns_none_when_no_match():
    relics = _sample_player()["relics"]
    assert play._resolve_relic_query(relics, "nope") is None
    assert play._resolve_relic_query(relics, "99") is None
    assert play._resolve_relic_query([], "0") is None


def test_show_one_relic_renders_resolved_description(capsys):
    play._show_one_relic(_sample_player(), "akabeko")
    out = capsys.readouterr().out
    assert "Akabeko" in out
    assert "gain 8 Vigor" in out  # var is substituted


def test_show_one_potion_by_index(capsys):
    play._show_one_potion(_sample_player(), "0")
    out = capsys.readouterr().out
    assert "Block Potion" in out
    assert "Gain 12 Block" in out


def test_show_boss_renders_name_and_moves(capsys):
    state = {"context": {"boss": {"id": "VANTOM_BOSS", "name": "Vantom"}, "act_name": "Overgrowth"}}
    play._show_boss(state)
    out = capsys.readouterr().out
    assert "Vantom" in out
    assert "Overgrowth" in out
    assert "Dismember" in out


def test_show_boss_falls_back_to_get_map(capsys):
    calls = []

    def fake_send(req):
        calls.append(req)
        return {"boss": {"id": "VANTOM_BOSS", "name": "Vantom"}}

    play._show_boss({"context": {}}, send_fn=fake_send)
    out = capsys.readouterr().out
    assert calls == [{"cmd": "get_map"}]
    assert "Vantom" in out
