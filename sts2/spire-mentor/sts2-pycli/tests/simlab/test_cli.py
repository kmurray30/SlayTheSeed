import json

from simlab.cli import main


def test_cli_run_micro_sweep(tmp_path, capsys) -> None:
    rc = main(
        [
            "run",
            "--name",
            "clitest",
            "--encounter",
            "MAWLER_NORMAL",
            "--ascension",
            "0",
            "--strategies",
            "random,min_overblock",
            "--seeds",
            "3",
            "--jobs",
            "1",
            "--out",
            str(tmp_path),
        ]
    )
    assert rc == 0
    sweep_dir = tmp_path / "clitest"
    assert (sweep_dir / "summary.json").exists()
    assert (sweep_dir / "rows.jsonl").exists()
    assert (sweep_dir / "spec.json").exists()
    out = capsys.readouterr().out
    assert "min_overblock" in out  # summary table printed


def test_cli_run_unknown_strategy(tmp_path, capsys) -> None:
    rc = main(
        [
            "run",
            "--name",
            "bad",
            "--encounter",
            "MAWLER_NORMAL",
            "--strategies",
            "random,bogus",
            "--seeds",
            "1",
            "--out",
            str(tmp_path),
        ]
    )
    assert rc == 2
    assert "unknown strategies" in capsys.readouterr().err


def test_cli_run_unknown_deck(tmp_path, capsys) -> None:
    rc = main(
        [
            "run",
            "--name",
            "baddeck",
            "--encounter",
            "MAWLER_NORMAL",
            "--strategies",
            "random",
            "--deck",
            "no_such_deck",
            "--seeds",
            "1",
            "--out",
            str(tmp_path),
        ]
    )
    assert rc == 2
    err = capsys.readouterr().err
    assert "unknown deck" in err
    assert "ironclad_starter" in err  # lists valid presets


def test_cli_multiple_deck_presets(tmp_path) -> None:
    rc = main(
        [
            "run",
            "--name",
            "twodecks",
            "--encounter",
            "MAWLER_NORMAL",
            "--strategies",
            "random",
            "--deck",
            "ironclad_starter",
            "--deck",
            "turtle",
            "--seeds",
            "1",
            "--jobs",
            "1",
            "--out",
            str(tmp_path),
        ]
    )
    assert rc == 0
    spec = json.loads((tmp_path / "twodecks" / "spec.json").read_text())
    names = [name for name, _cards in spec["decks"]]
    assert names == ["ironclad_starter", "turtle"]


def test_cli_deck_json(tmp_path) -> None:
    lean = ["STRIKE_IRONCLAD"] * 6 + ["DEFEND_IRONCLAD"] * 4
    deck_json = tmp_path / "decks.json"
    deck_json.write_text(json.dumps({"lean": lean}))
    rc = main(
        [
            "run",
            "--name",
            "jsondeck",
            "--encounter",
            "MAWLER_NORMAL",
            "--strategies",
            "random",
            "--deck-json",
            str(deck_json),
            "--seeds",
            "1",
            "--jobs",
            "1",
            "--out",
            str(tmp_path),
        ]
    )
    assert rc == 0
    spec = json.loads((tmp_path / "jsondeck" / "spec.json").read_text())
    names = [name for name, _cards in spec["decks"]]
    assert "lean" in names


def test_cli_deck_json_bad_structure_rc2(tmp_path, capsys) -> None:
    deck_json = tmp_path / "bad_structure.json"
    deck_json.write_text(json.dumps({"my_deck": 5}))
    rc = main(
        [
            "run",
            "--name",
            "badstruct",
            "--encounter",
            "MAWLER_NORMAL",
            "--strategies",
            "random",
            "--deck-json",
            str(deck_json),
            "--seeds",
            "1",
            "--out",
            str(tmp_path),
        ]
    )
    assert rc == 2
    assert "list" in capsys.readouterr().err


def test_cli_bad_deck_json_card_rc2(tmp_path, capsys) -> None:
    deck_json = tmp_path / "bad.json"
    deck_json.write_text(json.dumps({"typo": ["STRIKE_IRONCLD"] * 10}))
    rc = main(
        [
            "run",
            "--name",
            "badcard",
            "--encounter",
            "MAWLER_NORMAL",
            "--strategies",
            "random",
            "--deck-json",
            str(deck_json),
            "--seeds",
            "1",
            "--out",
            str(tmp_path),
        ]
    )
    assert rc == 2
    assert "STRIKE_IRONCLAD" in capsys.readouterr().err


def test_cli_monsters_encounter(tmp_path, capsys) -> None:
    # Typo'd monster name -> rc 2 with a suggestion.
    rc = main(
        [
            "run",
            "--name",
            "badmon",
            "--encounter",
            "monsters:Nibit",
            "--strategies",
            "random",
            "--seeds",
            "1",
            "--out",
            str(tmp_path),
        ]
    )
    assert rc == 2
    assert "Nibbit" in capsys.readouterr().err

    # Valid custom monster lineup -> runs to completion, bucket key carries it.
    rc = main(
        [
            "run",
            "--name",
            "goodmon",
            "--encounter",
            "monsters:Nibbit",
            "--strategies",
            "random",
            "--seeds",
            "1",
            "--jobs",
            "1",
            "--out",
            str(tmp_path),
        ]
    )
    assert rc == 0
    summary = json.loads((tmp_path / "goodmon" / "summary.json").read_text())
    assert any("monsters:Nibbit" in key for key in summary["buckets"])


def test_cli_summarize_missing_rows(tmp_path, capsys) -> None:
    rc = main(["summarize", str(tmp_path)])
    assert rc == 1
    assert "not found" in capsys.readouterr().err


def test_cli_summarize(tmp_path) -> None:
    (tmp_path / "rows.jsonl").write_text(
        json.dumps(
            {
                "encounter": "X",
                "ascension": 0,
                "strategy": "s",
                "seed": 0,
                "dmg_taken": 5,
                "turns": 3,
                "victory": True,
            }
        )
        + "\n"
    )
    assert main(["summarize", str(tmp_path)]) == 0
