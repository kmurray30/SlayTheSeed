import json
import pathlib
import subprocess
import sys

RS = pathlib.Path(__file__).resolve().parents[3] / "sts2-rs"


def test_rel500_is_first_500_of_release():
    out = RS / "data/seed-panels/rel-500.json"
    subprocess.run(
        [sys.executable, str(pathlib.Path(__file__).with_name("make_rel500.py"))], check=True
    )
    rel500 = json.loads(out.read_text())
    full = json.loads((RS / "data/seed-panels/release-2000.json").read_text())
    assert rel500["name"] == "rel-500"
    assert len(rel500["rows"]) == 500
    assert rel500["rows"] == full["rows"][:500]
    assert all(r["ascension"] == 0 for r in rel500["rows"])
