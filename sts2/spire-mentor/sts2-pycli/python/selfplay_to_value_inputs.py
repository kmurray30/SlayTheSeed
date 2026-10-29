"""Reshape selfplay_records.jsonl (AZ M5a Task 4) into the M4 value
trainer's features.jsonl + decisions.jsonl (join-by-seed, win = z). No new
net — train_run_value_net.py trains verbatim on the output. AUC/Brier are
NON-gating regression checks here; win-rate is the real gate (Task 9). The
value net trains FROM SCRATCH on self-play z (never warm-started from the
failed M4 human-corpus net).

Reuse invocation (M4 trainer + eval run verbatim on the adapter's output)::

    python selfplay_to_value_inputs.py --records selfplay_records.jsonl
    python train_run_value_net.py \\
        --features value_features.jsonl --decisions value_decisions.jsonl \\
        --schema schema.json --out value_selfplay.json \\
        --emit-parity-fixture value_selfplay.parity.json
    python eval_run_value_net.py --net value_selfplay.json ...  # non-gating go/no-go

AUC/Brier from eval_run_value_net.py are non-gating regression checks; the
milestone gate is the Task 9 win-rate A/B.
"""

import argparse
import json
from pathlib import Path


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--records", type=Path, required=True)
    ap.add_argument("--features-out", type=Path, default=Path("value_features.jsonl"))
    ap.add_argument("--decisions-out", type=Path, default=Path("value_decisions.jsonl"))
    args = ap.parse_args()

    z_by_seed: dict[int, int] = {}
    act_by_seed: dict[int, int] = {}
    with args.features_out.open("w") as fout:
        for line in args.records.read_text().splitlines():
            if not line.strip():
                continue
            r = json.loads(line)
            seed = int(r["seed"])
            fout.write(json.dumps({"seed": seed, "context": r["context"]}) + "\n")
            z_by_seed[seed] = round(float(r["z"]))
            act_by_seed[seed] = max(act_by_seed.get(seed, 0), int(r.get("act", 1)))
    with args.decisions_out.open("w") as dout:
        for seed, win in sorted(z_by_seed.items()):
            dout.write(json.dumps({"seed": seed, "win": win, "act": act_by_seed[seed]}) + "\n")


if __name__ == "__main__":
    main()
