"""Unit tests for runstats.trace_mine feature extraction + contrast."""

import json
import sys

from runstats.trace_mine import (
    _fmt_turn,
    contrast,
    load_reps,
    main,
    print_examples,
    rep_features,
    turn_features,
)


def _step(rnd, hp, block, energy, action, enemies):
    return {
        "round": rnd,
        "hp": hp,
        "block": block,
        "energy": energy,
        "draw_n": 5,
        "discard_n": 0,
        "hand": ["CARD.Strike"],
        "enemies": enemies,
        "action": action,
    }


ENEMY_ATK = ["MONSTER.Crusher:100+0b:ATTACK:12"]
ENEMY_MULTI = ["MONSTER.Crusher:80+5b:MULTI_ATTACK:4x3"]
ENEMY_BUFF = ["MONSTER.Crusher:60+0b:BUFF"]


def _trace():
    return [
        # turn 1: play a card, drink a potion, end with 10 block vs 12 incoming
        _step(1, 70, 0, 3, "Play:CARD.Strike@MONSTER.Crusher", ENEMY_ATK),
        _step(1, 70, 0, 2, "Potion:POTION.BlockPotion", ENEMY_ATK),
        _step(1, 70, 10, 2, "EndTurn", ENEMY_ATK),
        # turn 2: 15 block vs 12 (=4*3) incoming -> overblock 3; hp lost 2
        _step(2, 68, 0, 3, "Play:CARD.Defend", ENEMY_MULTI),
        _step(2, 68, 15, 1, "EndTurn", ENEMY_MULTI),
        # turn 3: no attack intent; fight ends mid-turn (kill), no EndTurn
        _step(3, 68, 0, 3, "Play:CARD.Strike@MONSTER.Crusher", ENEMY_BUFF),
    ]


def test_turn_features_parses_intents_and_boundaries():
    turns = turn_features(_trace())
    assert [t["round"] for t in turns] == [1, 2, 3]
    t1, t2, t3 = turns
    assert t1["incoming"] == 12 and t1["block_end"] == 10
    assert t1["potions"] == ["Potion:POTION.BlockPotion"]
    assert t2["incoming"] == 12 and t2["block_end"] == 15  # 4x3 multi-attack
    assert t3["block_end"] is None  # resolved mid-turn
    assert t1["enemy_hp"] == 100 and t2["enemy_hp"] == 80


def test_rep_features_aggregates():
    f = rep_features(_trace())
    assert f["rounds"] == 3.0
    assert f["potions_used"] == 1.0 and f["first_potion_round"] == 1.0
    assert f["overblock_per_turn"] == 1.5  # (0 + 3) / 2 ended turns
    assert f["underblock_per_turn"] == 1.0  # (2 + 0) / 2
    assert f["dmg_taken_per_turn"] == 1.0  # (2 + 0) / 2 transitions
    assert f["dmg_dealt_per_turn"] == 20.0  # (100-80 + 80-60) / 2 -> 20


def _rep_row(seed, arm, survived, trace):
    return {
        "kind": "rep",
        "seed": seed,
        "arm": arm,
        "rep": 0,
        "encounter": "MONSTER.Crusher",
        "survived": survived,
        "end_hp": 10 if survived else 0,
        "trace": trace,
    }


def test_contrast_requires_both_sides_and_signs():
    rows = [
        _rep_row(1, "deployed", False, _trace()),
        _rep_row(1, "clair", True, _trace()[:3]),  # clair wins in 1 round
        _rep_row(2, "deployed", False, _trace()),  # seed 2: no clair win -> dropped
        _rep_row(2, "clair", False, _trace()),
    ]
    rep = contrast(rows)
    assert rep["n_seeds"] == 1
    rounds = rep["overall"]["rounds"]
    assert rounds["mean_diff"] == -2.0  # clair won in 1 round vs 3
    assert rounds["neg"] == 1 and rounds["pos"] == 0
    assert rep["per_seed"][1]["clair_p"] == 1.0
    assert rep["per_seed"][1]["deployed_p"] == 0.0


# ── intent parsing edge cases ─────────────────────────────────────────
def test_incoming_ignores_intentless_and_payloadless_enemies():
    """Enemies with no intent field, or an ATTACK with no payload, add 0."""
    trace = [
        _step(
            1,
            70,
            4,
            0,
            "EndTurn",
            [
                "MONSTER.Silent:30+0b",  # no intent at all
                "MONSTER.Vague:20+0b:ATTACK",  # ATTACK without a value
                "MONSTER.Sloppy:10+0b:MULTI_ATTACK",  # MULTI without dxh
                "MONSTER.Real:40+0b:ATTACK:7",
            ],
        )
    ]
    (t,) = turn_features(trace)
    assert t["incoming"] == 7
    assert t["n_enemies"] == 4
    assert t["enemy_hp"] == 100


def test_rep_features_nan_when_no_potion_and_no_kill():
    f = rep_features(_plain_trace(2))
    assert f["potions_used"] == 0.0
    assert f["first_potion_round"] != f["first_potion_round"]  # NaN
    assert f["first_kill_round"] != f["first_kill_round"]  # NaN


def test_rep_features_first_kill_round():
    trace = [
        _step(1, 70, 0, 0, "EndTurn", ["MONSTER.A:10+0b:ATTACK:5", "MONSTER.B:10+0b:ATTACK:5"]),
        _step(2, 65, 0, 0, "EndTurn", ["MONSTER.B:10+0b:ATTACK:5"]),
    ]
    assert rep_features(trace)["first_kill_round"] == 2.0


# ── load_reps ─────────────────────────────────────────────────────────
def test_load_reps_keeps_only_traced_rep_rows(tmp_path):
    path = tmp_path / "trace.jsonl"
    lines = [
        {"kind": "config", "arm": "deployed"},  # not a rep
        {"kind": "rep", "seed": 1, "trace": []},  # rep with an empty trace
        _rep_row(1, "deployed", False, _trace()),
    ]
    path.write_text("".join(json.dumps(o) + "\n" for o in lines))
    other = tmp_path / "trace2.jsonl"
    other.write_text(json.dumps(_rep_row(2, "clair", True, _trace())) + "\n")

    rows = load_reps([str(path), str(other)])
    assert [r["seed"] for r in rows] == [1, 2]
    assert [r["arm"] for r in rows] == ["deployed", "clair"]


# ── example rendering ─────────────────────────────────────────────────
def test_fmt_turn_renders_block_potions_and_midturn_end():
    t1, _t2, t3 = turn_features(_trace())
    assert _fmt_turn(t1) == "    r 1 hp= 70 foes=1(100) cards=1 10b/12in 1pot"
    assert _fmt_turn(t3) == "    r 3 hp= 68 foes=1( 60) cards=1 -"


def test_print_examples_shows_both_arms(capsys):
    rows = [
        _rep_row(1, "deployed", False, _trace()),
        _rep_row(1, "clair", True, _trace()[:3]),
    ]
    rep = contrast(rows)
    print_examples(rows, rep, 1)
    out = capsys.readouterr().out
    assert "== seed 1 MONSTER.Crusher deployed_p=0.00 clair_p=1.00" in out
    assert "deployed LOSS (rep 0, end_hp 0):" in out
    assert "clair WIN (rep 0, end_hp 10):" in out
    assert out.count("    r 1 hp= 70") == 2  # one turn-1 line per arm


# ── CLI ───────────────────────────────────────────────────────────────
def _plain_trace(n_rounds: int, hp0: int = 70) -> list[dict]:
    """Potion-free, kill-free trace of ``n_rounds`` complete rounds."""
    steps = []
    for i in range(n_rounds):
        hp = hp0 - 2 * i
        steps.append(_step(i + 1, hp, 0, 3, "Play:CARD.Strike@MONSTER.Crusher", ENEMY_ATK))
        steps.append(_step(i + 1, hp, 5, 2, "EndTurn", ENEMY_ATK))
    return steps


def _enc_rows(seed: int, encounter: str) -> list[dict]:
    rows = [
        _rep_row(seed, "deployed", False, _plain_trace(4)),
        _rep_row(seed, "clair", True, _plain_trace(2)),
    ]
    for r in rows:
        r["encounter"] = encounter
    return rows


def test_main_reports_overall_and_big_encounters(tmp_path, monkeypatch, capsys):
    rows = [r for seed in range(1, 6) for r in _enc_rows(seed, "MONSTER.Crusher")]
    rows += _enc_rows(99, "MONSTER.Nibbit")  # only 1 seed -> below the n>=5 cut
    jsonl = tmp_path / "trace.jsonl"
    jsonl.write_text("".join(json.dumps(r) + "\n" for r in rows))
    out_json = tmp_path / "mine.json"
    monkeypatch.setattr(
        sys, "argv", ["trace_mine", str(jsonl), "--json", str(out_json), "--examples", "1"]
    )

    main()

    out = capsys.readouterr().out
    assert "seeds contrasted: 6 (traced reps: 12)" in out
    assert "[MONSTER.Crusher] n=5" in out
    assert "[MONSTER.Nibbit]" not in out
    assert "rounds" in out
    # All-NaN features are dropped from the printed table entirely.
    assert "first_potion_round" not in out
    assert "== seed" in out  # --examples rendered

    rep = json.loads(out_json.read_text())
    assert rep["n_seeds"] == 6
    assert rep["overall"]["rounds"]["mean_diff"] == -2.0
    assert rep["overall"]["first_potion_round"] is None
    assert set(rep["per_encounter"]) == {"MONSTER.Crusher", "MONSTER.Nibbit"}
