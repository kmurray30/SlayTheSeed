#!/usr/bin/env python3
"""Re-featurize the PPO encoder parity fixtures after an append-only vocab growth.

``sts2-runai/tests/fixtures/ppo_encoder_parity*.jsonl`` record, per decision,
the raw observation, the Python featurization (gate A: Rust must reproduce it
exactly) and the torch pi/V (gate B). Growing a vocab moves that kind's OOV and
PAD index to the new tail, so the recorded ``feat`` arrays go stale while the
recorded observation, actions and pi/V stay correct: a re-exported champion
answers exactly as before (``vocab_reexport_equivalence.py``).

So this does NOT re-walk or re-record anything. For every row it featurizes the
RECORDED observation again, at the schema the row was recorded at, and asserts
the new arrays differ from the recorded ones only by the OOV/PAD remap of the
grown columns. Only then does it rewrite ``feat``; every other field is copied
through byte for byte (the line is re-serialized with the same ``json.dumps``
the dumper uses, and the script checks the untouched fields round-trip).

USAGE (from sts2-pycli, worktree wheel venv)::

    python scripts/refeaturize_encoder_fixtures.py --old-rev <rev before the growth>
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "python"))

FIXTURES = ROOT.parent / "sts2-rs/crates/sts2-runai/tests/fixtures"

#: (vocab accessor, pin file, categorical column).
KINDS = [("cards", "card", 0), ("relics", "relic_id", 2), ("potions", "potion", 3)]


def old_count(pin_kind: str, rev: str) -> int:
    path = f"sts2-rs/crates/sts2-vocab/src/vocab_pins/{pin_kind}.txt"
    text = subprocess.run(
        ["git", "show", f"{rev}:{path}"],
        cwd=ROOT.parent,
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    return sum(1 for ln in text.splitlines() if ln.strip() and not ln.startswith("#"))


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--old-rev", required=True)
    a = ap.parse_args()

    from ppo import vocab
    from ppo.encoder import featurize_row

    remap: dict[int, dict[int, int]] = {}
    for acc, pin_kind, col in KINDS:
        reg = getattr(vocab, acc)()
        old_n, new_n = old_count(pin_kind, a.old_rev), len(reg.names)
        assert new_n >= old_n, pin_kind
        remap[col] = {old_n: new_n, old_n + 1: new_n + 1}
        print(
            f"{pin_kind}: {old_n} -> {new_n} ids (OOV {old_n}->{new_n}, PAD {old_n + 1}->{new_n + 1})"
        )

    for path in sorted(FIXTURES.glob("ppo_encoder_parity*.jsonl")):
        lines = path.read_text().splitlines()
        out = []
        changed = 0
        for ln in lines:
            if not ln.strip():
                out.append(ln)
                continue
            row = json.loads(ln)
            assert json.dumps(row, allow_nan=False) == ln, f"{path.name}: line does not round-trip"
            feat = featurize_row(
                row["obs"], row["actions"], row["metrics"], features=frozenset(row["features"])
            )
            new = {
                "obs_cat": feat.obs_cat.tolist(),
                "obs_cont": feat.obs_cont.tolist(),
                "obs_sec": feat.obs_sec.tolist(),
                "act_cat": feat.act_cat.tolist(),
                "act_cont": feat.act_cont.tolist(),
                "act_sec": feat.act_sec.tolist(),
                "n_actions": feat.n_actions,
            }
            old = row["feat"]
            for k in ("obs_cont", "obs_sec", "act_cont", "act_sec", "n_actions"):
                assert new[k] == old[k], f"{path.name} seed {row['seed']} d{row['decision']}: {k}"
            for k in ("obs_cat", "act_cat"):
                o = np.asarray(old[k], dtype=np.int64)
                expect = o.copy()
                for col, m in remap.items():
                    for src, dst in m.items():
                        expect[..., col][o[..., col] == src] = dst
                assert np.array_equal(expect, np.asarray(new[k], dtype=np.int64)), (
                    f"{path.name} seed {row['seed']} d{row['decision']}: {k} differs beyond "
                    "the OOV/PAD remap"
                )
            changed += new != old
            row["feat"] = new
            out.append(json.dumps(row, allow_nan=False))
        path.write_text("\n".join(out) + "\n")
        print(f"{path.name}: {len(lines)} rows, {changed} re-featurized (remap only)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
