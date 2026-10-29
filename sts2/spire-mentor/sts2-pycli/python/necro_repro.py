"""Single-seed live repro for the v2 combat-parity sweep (issue #178).

Usage:
    WHEEL_VENV_ALLOW_MAIN=1 STS2_PARITY_NOCACHE=1 \
      scripts/wheel-venv.sh run -- python python/necro_repro.py \
        --character Necrobinder --tier quick --seed 225

Reconstructs the EXACT trial (scenario + action seed) the sweep used for
`seed`, runs BOTH engines live (C# headless oracle vs Rust), and prints the
scenario, the action that triggered the first divergence, and the divergence
fields. Runs fully live (no trace cache) so it is always authoritative.
"""

import argparse
import dataclasses
import json
import os
import sys

os.environ.setdefault("STS2_PARITY_NOCACHE", "1")
os.environ.setdefault("WHEEL_VENV_ALLOW_MAIN", "1")

import parity_audit as pa


def main(argv=None):
    p = argparse.ArgumentParser()
    p.add_argument("--character", default="Necrobinder")
    p.add_argument("--tier", default="quick")
    p.add_argument("--seed", type=int, required=True)
    p.add_argument("--axes", default="upgraded,arenas,fuzz,pairwise")
    p.add_argument("--max-turns", type=int, default=6)
    args = p.parse_args(argv)

    axes = [a for a in args.axes.split(",") if a]
    trials = pa.assemble_v2_trials(
        args.tier, axes, character=args.character, max_turns=args.max_turns
    )
    match = [t for t in trials if t[0] == args.seed]
    if not match:
        print(f"no trial with seed={args.seed} in tier={args.tier} (have {len(trials)})")
        return 2
    seed, sc, max_turns = match[0]
    print("=== SCENARIO ===")
    print(json.dumps(dataclasses.asdict(sc), indent=2))
    print(f"\n=== running LIVE (seed={seed}, max_turns={max_turns}) ===")
    # Force fully-live: clear the module cache so run_trial drives C# live.
    pa._CACHE = None
    out = pa.run_trial(seed, max_turns, sc)
    print("category:", out.get("category"))
    print("turn:", out.get("turn"))
    print("first_path:", out.get("first_path"))
    print("action:", json.dumps(out.get("action")))
    print("hand_before:", out.get("hand_before"))
    print("divergences:")
    print(json.dumps(out.get("divergences"), indent=2)[:4000])
    return 0


if __name__ == "__main__":
    sys.exit(main())
