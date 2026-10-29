"""Differential gate: the Rust wire→obs port vs. the Python builders.

``sts2-runai::ppo::wire`` is a port of :mod:`realgame_ppo`'s ``_base_obs`` /
``_base_metrics`` / ``_build_*`` functions. Unit tests on either side pin what
each *believes*; only replaying the same real decisions through both catches
the case where the port is self-consistently wrong.

That matters more here than the line count suggests. The Python builders carry
a series of corrections each learned from a live failure -- the row-0
``map_coord`` guard (which flipped 27 of 42 reproduced act-start routes), the
``total_floor`` offset (wrong on 4735/4735 meta decisions before the fix), the
``_STATUS`` vocab suffix (407/6074 deck tokens OOV), the BBCode strip and the
``{HpLoss}`` resolution (without which every live event payload parsed to
(0, 0)). None of them announce themselves when lost: the observation still
builds, the net still answers, and only the win rate knows.

So the gate is field-for-field equality of the observation dict, plus the
candidate action list, labels and bridge responses, over every recorded
decision of every covered surface.

Usage::

    uv run python python/diag_ppo_wire_parity.py [--logs logs] [--limit N]
        [--surface map_select] [--verbose]

Exits non-zero if any row mismatches. Prints a per-surface tally either way.
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import sys
from collections import Counter
from typing import Any

import realgame_ppo
import sts2_rs

#: Surfaces the Rust port covers, i.e. `_PPO_NONCOMBAT_BUILDERS`' keys.
SURFACES = tuple(realgame_ppo._PPO_NONCOMBAT_BUILDERS)

#: Obs keys the two sides are allowed to differ on, with the reason. Keep this
#: EMPTY unless a difference is provably unobservable to the encoder -- an
#: exemption here is exactly how a real divergence would hide.
OBS_KEY_EXEMPTIONS: dict[str, str] = {}


#: The one *known* representational difference, verified feature-neutral at
#: both encoders.
#:
#: Python names an unrecognized shop option ``shop_other_<kind>``
#: (``realgame_ppo._build_shop``'s else-branch); Rust has a closed
#: ``ActionDesc`` enum whose catch-all serializes as ``unknown``. Both land on
#: the SAME token: ``ppo/encoder.py:391`` falls through to
#: ``_fixed_tok("none")`` for any kind it does not recognize, and
#: ``encoder.rs:614`` maps ``ActionDesc::Unknown`` to ``fixed_tok("none", 0)``.
#: The string survives only as a diagnostic label, so widening the shared
#: enum to carry it would buy nothing.
def _actions_equivalent(py_action: Any, rs_action: Any) -> bool:
    py_kind = (py_action or {}).get("action")
    rs_kind = (rs_action or {}).get("action")
    if py_kind == rs_kind:
        return py_action == rs_action
    return isinstance(py_kind, str) and py_kind.startswith("shop_other_") and rs_kind == "unknown"


def merged_python_obs(decision: Any) -> dict[str, Any]:
    """Python's `obs` + `metrics`, merged the way the encoder reads them.

    `realgame_ppo` splits the observation across two dicts because
    `net_policy.distribution(obs, actions, metrics)` takes them separately;
    the Rust `PpoObs` is one struct holding both, so the comparison has to
    merge before it can mean anything.

    `obs` wins the overlap. That matters for exactly one key: both dicts carry
    `max_hp`, with different defaults (`_base_obs` uses `or 1`,
    `_base_metrics` uses `, 0`) -- and it is `_base_obs`'s that the encoder
    actually reads, as `_Ctx.max_hp` (`ppo/encoder.py:200`).
    """
    return {**decision.metrics, **decision.obs}


#: The shop-entry keys either encoder actually reads: `id` and `price` feed the
#: card/relic/potion token, `available` sets `C_ENABLED`
#: (`ppo/encoder.py:320-339`, `encoder.rs:511-543`). Everything else on the
#: entry is structural.
_SHOP_ENTRY_KEYS = ("id", "price", "available")


def normalize_shop(obs: dict[str, Any]) -> dict[str, Any]:
    """Reduce `obs["shop"]` to the fields the encoder reads, on both sides.

    `PpoObs` is a typed struct mirroring the *training* dict, so Rust always
    emits `idx` / `on_sale` / `is_colorless` / `removal_used`; the bridge
    builder in Python, writing dict literals, omits them because the wire has
    nothing to put there. Rust also skips a `None` `id` where Python writes an
    explicit `null`.

    None of that reaches a feature -- the reads are enumerated in
    `_SHOP_ENTRY_KEYS` above, and `removal_used` is read by neither encoder --
    so the comparison drops the structural fields rather than pretending the
    shop matches or exempting the whole subtree.
    """
    shop = obs.get("shop")
    if not isinstance(shop, dict):
        return obs
    trimmed = {
        kind: [{k: entry.get(k) for k in _SHOP_ENTRY_KEYS} for entry in (shop.get(kind) or [])]
        for kind in ("cards", "relics", "potions")
    }
    trimmed["removal_cost"] = shop.get("removal_cost")
    return {**obs, "shop": trimmed}


def _norm(value: Any) -> Any:
    """Normalize for comparison across the JSON round trip.

    Rust emits `(String, bool)` deck entries as 2-element arrays where Python
    holds tuples, and integers that survived a float slot come back as ints.
    Neither is a semantic difference, and normalizing here keeps a real one
    from being drowned out by shape noise.
    """
    if isinstance(value, tuple):
        return [_norm(v) for v in value]
    if isinstance(value, list):
        return [_norm(v) for v in value]
    if isinstance(value, dict):
        return {k: _norm(v) for k, v in sorted(value.items())}
    if isinstance(value, bool):
        return value
    if isinstance(value, float) and value.is_integer():
        return int(value)
    return value


def _is_default(value: Any) -> bool:
    """Whether `value` is what a `.get(key)` on the other side would yield.

    Rust's observation is a typed struct, so it always materializes every
    field; Python's bridge builders write dict literals and simply omit a key
    the wire has nothing for (`visited`, `current`, `option_id`, a `None`
    shop-entry `id`, …). Both encoders read those through `.get(key) or
    <falsy>` / `Option::unwrap_or_default`, so an absent key and a default
    value are the same input.

    This is the ONLY absence rule, and it is deliberately narrow: a key Rust
    fills with a *non*-default while Python omits it is still reported. That
    is the direction a real divergence would take — Rust inventing a value the
    reference never had.
    """
    return value in (None, False, 0, "", [], {})


def _diff(py_obj: Any, rs_obj: Any, path: str = "$") -> list[str]:
    """Every leaf where the two disagree, as readable paths."""
    py_obj, rs_obj = _norm(py_obj), _norm(rs_obj)
    if isinstance(py_obj, dict) and isinstance(rs_obj, dict):
        out: list[str] = []
        for key in sorted(set(py_obj) | set(rs_obj)):
            if key in OBS_KEY_EXEMPTIONS and path == "$.obs":
                continue
            if key not in py_obj:
                if not _is_default(rs_obj[key]):
                    out.append(f"{path}.{key}: absent in python, rust={rs_obj[key]!r}")
            elif key not in rs_obj:
                if not _is_default(py_obj[key]):
                    out.append(f"{path}.{key}: python={py_obj[key]!r}, absent in rust")
            else:
                out += _diff(py_obj[key], rs_obj[key], f"{path}.{key}")
        return out
    if isinstance(py_obj, list) and isinstance(rs_obj, list):
        if len(py_obj) != len(rs_obj):
            return [f"{path}: length {len(py_obj)} != {len(rs_obj)}"]
        out = []
        for i, (a, b) in enumerate(zip(py_obj, rs_obj)):
            out += _diff(a, b, f"{path}[{i}]")
        return out
    if py_obj != rs_obj:
        return [f"{path}: python={py_obj!r} rust={rs_obj!r}"]
    return []


def compare_row(state: dict[str, Any], surface: str) -> list[str]:
    """Diff one recorded decision through both builders."""
    builder = realgame_ppo._PPO_NONCOMBAT_BUILDERS.get(surface)
    if builder is None:
        return []
    try:
        py_decision = builder(state)
    except Exception as exc:  # the Python side is the reference; a throw is a finding
        return [f"python builder raised: {type(exc).__name__}: {exc}"]
    rs_decision = sts2_rs.ppo_wire_obs(state, surface)

    if py_decision is None or rs_decision is None:
        if (py_decision is None) != (rs_decision is None):
            return [
                "decline mismatch: "
                f"python={'None' if py_decision is None else 'decision'} "
                f"rust={'None' if rs_decision is None else 'decision'}"
            ]
        return []

    diffs = _diff(
        {
            "obs": normalize_shop(merged_python_obs(py_decision)),
            "responses": py_decision.responses,
            "labels": py_decision.labels,
        },
        {
            "obs": normalize_shop(rs_decision["obs"]),
            "responses": rs_decision["responses"],
            "labels": rs_decision["labels"],
        },
    )

    # Actions compare through the shop_other/unknown equivalence above.
    py_actions, rs_actions = py_decision.actions, rs_decision["actions"]
    if len(py_actions) != len(rs_actions):
        diffs.append(f"$.actions: length {len(py_actions)} != {len(rs_actions)}")
    else:
        for i, (a, b) in enumerate(zip(py_actions, rs_actions)):
            if not _actions_equivalent(_norm(a), _norm(b)):
                diffs.append(f"$.actions[{i}]: python={a!r} rust={b!r}")
    return diffs


def iter_states(logs_dir: str, surfaces: tuple[str, ...], limit: int | None):
    """Yield `(surface, state)` for every recorded decision in `logs_dir`."""
    seen = 0
    for path in sorted(glob.glob(os.path.join(logs_dir, "**", "*.jsonl"), recursive=True)):
        try:
            handle = open(path, encoding="utf-8", errors="replace")
        except OSError:
            continue
        with handle:
            for line in handle:
                try:
                    row = json.loads(line)
                except Exception:
                    continue
                state = row.get("state")
                if not isinstance(state, dict):
                    continue
                surface = state.get("type")
                if surface not in surfaces:
                    continue
                yield surface, state
                seen += 1
                if limit is not None and seen >= limit:
                    return


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--logs", default="logs", help="directory of recorded JSONL runs")
    ap.add_argument("--limit", type=int, default=None, help="stop after N decisions")
    ap.add_argument(
        "--surface",
        action="append",
        choices=SURFACES,
        help="restrict to one surface (repeatable); default is all",
    )
    ap.add_argument("--verbose", action="store_true", help="print every mismatching field")
    ap.add_argument(
        "--max-report", type=int, default=10, help="stop printing after N mismatched rows"
    )
    args = ap.parse_args(argv)

    surfaces = tuple(args.surface) if args.surface else SURFACES
    checked: Counter[str] = Counter()
    mismatched: Counter[str] = Counter()
    reported = 0

    for surface, state in iter_states(args.logs, surfaces, args.limit):
        checked[surface] += 1
        diffs = compare_row(state, surface)
        if not diffs:
            continue
        mismatched[surface] += 1
        if reported < args.max_report:
            reported += 1
            print(f"\n--- MISMATCH #{reported} [{surface}] ---")
            for d in diffs[: (None if args.verbose else 8)]:
                print(f"  {d}")
            if not args.verbose and len(diffs) > 8:
                print(f"  ... and {len(diffs) - 8} more (use --verbose)")

    total = sum(checked.values())
    bad = sum(mismatched.values())
    print("\n=== wire-obs parity ===")
    for surface in SURFACES:
        if checked[surface]:
            print(
                f"  {surface:<12} {checked[surface] - mismatched[surface]:>6}"
                f" / {checked[surface]:<6} agree"
            )
    if total == 0:
        print("  no recorded decisions found — nothing was compared")
        return 1
    print(f"  {'TOTAL':<12} {total - bad:>6} / {total:<6} agree")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
