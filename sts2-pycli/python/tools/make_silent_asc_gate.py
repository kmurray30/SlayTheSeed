"""Emit silent-asc-gate-2200.json = Silent, A0-A10 x 200 paired seeds.

The gate panel for the uniform-curriculum leg of GH #333. Playbook section 2.1:
a fresh panel per gate, disjoint from every panel already spent on the same
comparison. `silent-bleed-60` (seeds 1000-1059) was spent on the it60/it130 kill
checks and `silent-rel-500` on the champion baseline, so this uses a seed block
that touches neither, and asserts it rather than assuming it.

200 seeds x 11 ascensions = 2200 cells. Ascensions stop at 10: above that the
observation encoding is corrupted.
"""

import json
import pathlib
import sys

RS = pathlib.Path(__file__).resolve().parents[3] / "sts2-rs"
PANELS = RS / "data/seed-panels"

SEED_LO, SEED_HI = 8000, 8200  # 200 seeds
ASCENSIONS = range(11)
SPENT = ("silent-bleed-60.json", "silent-rel-500.json")


def spent_seeds() -> set[int]:
    out: set[int] = set()
    for name in SPENT:
        path = PANELS / name
        if path.exists():
            out |= {r["seed"] for r in json.loads(path.read_text())["rows"]}
    return out


def main() -> None:
    seeds = list(range(SEED_LO, SEED_HI))
    clash = set(seeds) & spent_seeds()
    if clash:
        sys.exit(f"seed block overlaps a spent panel: {sorted(clash)[:10]}")

    rows = [{"seed": s, "ascension": a, "character": "silent"} for s in seeds for a in ASCENSIONS]
    panel = {"name": "silent-asc-gate-2200", "gen_seed": 6004515678751641164, "rows": rows}
    out = PANELS / "silent-asc-gate-2200.json"
    out.write_text(json.dumps(panel, indent=2) + "\n")
    print(
        f"wrote {out} ({len(rows)} rows, {len(seeds)} seeds x {len(list(ASCENSIONS))} ascensions)"
    )


if __name__ == "__main__":
    main()
