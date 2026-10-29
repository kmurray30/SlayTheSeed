"""Emit necro-asc-gate-2200.json = Necrobinder, A0-A10 x 200 paired seeds.

The gate panel for the uniform-curriculum event retrain of Necrobinder.
Playbook section 2.1: a fresh panel per gate, disjoint from every panel already
spent on the same comparison.

Two seed blocks are already spent on Necrobinder:

* seeds **0-799** at A0 -- the `it750` champion's own greedy eval
  (`~/ppo-necro/eval_it750*.jsonl`, the 44.8% pooled number in CHAMPION.md).
  These are not stored as a panel file, so they are hardcoded here.
* the committed panels below, which other characters' gates have used.

200 seeds x 11 ascensions = 2200 cells. Ascensions stop at 10: above that the
observation encoding is corrupted (see the honest-PPO retrain notes).
"""

import json
import pathlib
import sys

RS = pathlib.Path(__file__).resolve().parents[3] / "sts2-rs"
PANELS = RS / "data/seed-panels"

SEED_LO, SEED_HI = 9000, 9200  # 200 seeds
ASCENSIONS = range(11)
# Every committed panel -- cheaper to check them all than to curate a list and
# be wrong about which one some other gate spent.
SPENT_PANELS = sorted(p.name for p in PANELS.glob("*.json"))
# The it750 champion's own A0 greedy eval, which has no panel file.
SPENT_LOOSE = set(range(0, 800))


def spent_seeds() -> set[int]:
    out = set(SPENT_LOOSE)
    for name in SPENT_PANELS:
        path = PANELS / name
        rows = json.loads(path.read_text()).get("rows", [])
        out |= {r["seed"] for r in rows}
    return out


def main() -> None:
    seeds = list(range(SEED_LO, SEED_HI))
    clash = sorted(set(seeds) & spent_seeds())
    if clash:
        sys.exit(f"seed block overlaps a spent panel/eval: {clash[:10]}")

    rows = [
        {"seed": s, "ascension": a, "character": "necrobinder"} for s in seeds for a in ASCENSIONS
    ]
    panel = {"name": "necro-asc-gate-2200", "gen_seed": 4695122318874193007, "rows": rows}
    out = PANELS / "necro-asc-gate-2200.json"
    out.write_text(json.dumps(panel, indent=2) + "\n")
    print(f"wrote {out} ({len(rows)} rows, {len(seeds)} seeds x {len(list(ASCENSIONS))} levels)")


if __name__ == "__main__":
    main()
