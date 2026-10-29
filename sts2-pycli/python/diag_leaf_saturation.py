"""Is the deployed wide-Texel leaf saturated on real combat states? GH #293.

`evaluate_in_progress_wide` is `tanh(features · weights)` with
`WIDE_IN_PROGRESS_DIVISOR = 1.0` — no normalisation — over 216 features, several
of which are **unnormalised raw counts** (`p_hand_size`, `p_draw_size`,
`p_discard_size`, the hand/deck card-type tallies). If the dot product is
routinely far from 0, `tanh` saturates, its local derivative `1 - tanh^2` goes
to ~0, and *every* marginal difference the search is trying to compare — block
played vs not, HP preserved vs spent — is squashed into float noise.

That would explain both halves of #293: the search cannot see the value of
blocking, and re-pricing HP via `$STS2_RUNAI_HP_WEIGHT` does nothing because the
extra `(w-1) * W_hp * hp_ratio` term is added *inside* the same `tanh` and only
pushes it further into saturation.

This probe measures the dot product on real states pulled from the mirror
corpora (`world_wire` legs, reconstructed with `state_from_dict`), and prices
the marginal decision the issue is about: what does one Defend's worth of block
actually move the leaf by?

Usage:
  python diag_leaf_saturation.py [--corpus logs/diag/mirror_a1.jsonl] [--limit 4000]
"""

from __future__ import annotations

import argparse
import json
import math
import re
import statistics
import sys
from pathlib import Path

import sts2_rs

#: `EVAL_FEATURE_WEIGHTS_WIDE` has no Python binding (only the narrow
#: `feature_weights()` is exposed), so the table is parsed from source. The
#: length assertion below is the guard against drift.
WEIGHTS_RS = (
    Path(__file__).resolve().parents[2]
    / "sts2-rs"
    / "crates"
    / "sts2-core"
    / "src"
    / "eval_wide.rs"
)


def load_weights() -> list[float]:
    """Parse `EVAL_FEATURE_WEIGHTS_WIDE: [f32; N] = [...]` out of eval_wide.rs."""
    src = WEIGHTS_RS.read_text(encoding="utf-8")
    m = re.search(
        r"EVAL_FEATURE_WEIGHTS_WIDE:\s*\[f32;\s*(\d+)\]\s*=\s*\[(.*?)\];",
        src,
        re.S,
    )
    if not m:
        raise SystemExit(f"could not find EVAL_FEATURE_WEIGHTS_WIDE in {WEIGHTS_RS}")
    n = int(m.group(1))
    body = re.sub(r"//[^\n]*", "", m.group(2))
    vals = [float(x) for x in re.findall(r"-?\d+\.?\d*(?:e-?\d+)?", body)]
    if len(vals) != n:
        raise SystemExit(f"parsed {len(vals)} weights, expected {n}")
    return vals


def states_from_corpus(path: Path, limit: int):
    """Yield reconstructed `CombatState`s from a mirror corpus's world legs."""
    seen = 0
    with path.open(encoding="utf-8") as fh:
        for line in fh:
            if seen >= limit:
                return
            try:
                rec = json.loads(line)
            except json.JSONDecodeError:
                continue
            wire = rec.get("world_wire")
            if not isinstance(wire, dict) or not wire:
                continue
            try:
                st = sts2_rs.state_from_dict(wire)
            except Exception:  # a corpus row we cannot rebuild is skipped
                continue
            seen += 1
            yield rec.get("label", "?"), st


#: Some corpus rows carry a placeholder enemy with `max_hp = 999999999`, which
#: makes `e*_hp_norm` ~1e7 and swamps every other column. Those are excluded
#: from the statistics and counted separately — see `--report-degenerate`.
DEGENERATE_ABS = 1e3


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--corpus", default="logs/diag/mirror_a1.jsonl")
    ap.add_argument("--limit", type=int, default=4000)
    args = ap.parse_args(argv)

    weights = load_weights()
    names = sts2_rs.CombatState.feature_names_wide()
    if len(names) != len(weights):
        raise SystemExit(f"{len(names)} names vs {len(weights)} weights — schema drift")

    i_block = names.index("p_block_norm")
    i_hp = names.index("p_hp_ratio")
    w_block, w_hp = weights[i_block], weights[i_hp]

    dots: list[float] = []
    hp_ratios: list[float] = []
    # Marginal value of ~one Defend (12 block on an 80-max-HP player) at the
    # state's own operating point, vs at a hypothetical unsaturated one.
    deltas: list[float] = []
    contribs: dict[str, float] = {}

    path = Path(args.corpus)
    if not path.is_absolute():
        path = Path(__file__).resolve().parents[1] / path

    degenerate = 0
    for _label, st in states_from_corpus(path, args.limit):
        f = st.feature_vector_wide()
        if any(abs(x) > DEGENERATE_ABS for x in f):
            degenerate += 1
            continue
        dot = sum(a * b for a, b in zip(f, weights))
        dots.append(dot)
        hp_ratios.append(f[i_hp])
        # One Defend's worth of block, in the same normalised units the feature
        # uses (block / max_hp). 12/80 is the Ironclad Defend on a full bar.
        d_dot = w_block * (12.0 / 80.0)
        deltas.append(math.tanh(dot + d_dot) - math.tanh(dot))
        for nm, fv, wv in zip(names, f, weights):
            contribs[nm] = contribs.get(nm, 0.0) + abs(fv * wv)

    if not dots:
        print(f"no reconstructable states in {path}", file=sys.stderr)
        return 1

    n = len(dots)
    absd = sorted(abs(d) for d in dots)
    sat = [1.0 - math.tanh(d) ** 2 for d in dots]

    def pct(xs: list[float], p: float) -> float:
        return xs[min(len(xs) - 1, int(p * len(xs)))]

    print(f"corpus            : {path}")
    print(f"states            : {n}  (+{degenerate} degenerate, excluded)")
    print(f"dot  mean / median: {statistics.mean(dots):+.3f} / {statistics.median(dots):+.3f}")
    print(
        f"|dot| p50/p90/p99 : {pct(absd, 0.5):.2f} / {pct(absd, 0.9):.2f} / {pct(absd, 0.99):.2f}"
    )
    print(f"|dot| > 3 (tanh'<0.010) : {100 * sum(1 for d in dots if abs(d) > 3) / n:.1f}%")
    print(f"|dot| > 4 (tanh'<0.001) : {100 * sum(1 for d in dots if abs(d) > 4) / n:.1f}%")
    print(f"tanh' median      : {statistics.median(sat):.5f}   (1.0 = fully responsive)")
    print()
    print(f"w[p_block_norm]={w_block:+.3f}   w[p_hp_ratio]={w_hp:+.3f}")
    print("one Defend (12 block / 80 max HP) moves the leaf by:")
    print(f"  at the real operating point : {statistics.median(deltas):.6f} (median)")
    print(f"  at an unsaturated dot=0     : {math.tanh(w_block * 12.0 / 80.0):.6f}")
    ratio = math.tanh(w_block * 12.0 / 80.0) / max(statistics.median(deltas), 1e-12)
    print(f"  suppression factor          : {ratio:,.0f}x")
    print()
    print("top-12 |contribution| to the dot product (mean per state):")
    for nm, tot in sorted(contribs.items(), key=lambda kv: -kv[1])[:12]:
        print(f"  {nm:<28} {tot / n:+.3f}")

    # ── the $STS2_RUNAI_HP_WEIGHT null, explained ────────────────────────
    # `hp_term` is added INSIDE the same tanh, so raising the knob does not
    # re-price HP — it drives the leaf further into saturation and flattens
    # every comparison the search is trying to make. #293 measured
    # w=1 -> 12 wins, w=4 -> 12, w=8 -> 11; the knob could only ever hurt.
    print()
    print("effect of $STS2_RUNAI_HP_WEIGHT on the leaf's discrimination:")
    print(
        f"  {'w':>3} | {'median tanh-prime':>18} | {'one Defend moves':>17} | {'suppression':>13}"
    )
    print(f"  {'-' * 3}-+-{'-' * 18}-+-{'-' * 17}-+-{'-' * 13}")
    for hw in (1, 2, 4, 8):
        ds, sats = [], []
        for dot, hp_ratio in zip(dots, hp_ratios):
            d = dot + hp_ratio * w_hp * (hw - 1)
            sats.append(1.0 - math.tanh(d) ** 2)
            ds.append(math.tanh(d + w_block * 12.0 / 80.0) - math.tanh(d))
        md = statistics.median(ds)
        supp = math.tanh(w_block * 12.0 / 80.0) / max(md, 1e-15)
        print(f"  {hw:>3} | {statistics.median(sats):>18.6f} | {md:>17.6f} | {supp:>12,.0f}x")

    # ── a saturated in-progress leaf is indistinguishable from a WIN ─────
    # `MAX_LEAF_VALUE_ABS`'s contract (sts2-mcts/src/lib.rs:731-745): wins
    # score `0.5 + evaluate_victory` in [+1.0, +1.5]; an in-progress leaf is
    # `tanh(dot)` in (-1, 1). At |dot| > 4, tanh > 0.9993 — within 0.001 of an
    # outright victory.
    near_win = 100 * sum(1 for d in dots if math.tanh(d) > 0.999) / n
    print()
    print(f"in-progress states scoring within 0.001 of a WIN (tanh > 0.999): {near_win:.1f}%")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
