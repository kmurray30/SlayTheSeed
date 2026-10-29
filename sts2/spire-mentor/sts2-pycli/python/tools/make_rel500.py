"""Emit rel-500.json = first 500 rows of release-2000.json (paired A0-A10 eval base)."""

import json
import pathlib

RS = pathlib.Path(__file__).resolve().parents[3] / "sts2-rs"


def main() -> None:
    full = json.loads((RS / "data/seed-panels/release-2000.json").read_text())
    panel = {"name": "rel-500", "gen_seed": full["gen_seed"], "rows": full["rows"][:500]}
    out = RS / "data/seed-panels/rel-500.json"
    out.write_text(json.dumps(panel, indent=2) + "\n")
    print(f"wrote {out} ({len(panel['rows'])} rows)")


if __name__ == "__main__":
    main()
