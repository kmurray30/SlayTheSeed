"""Differential gate: :class:`ppo.rust_policy.RustPolicy` vs onnxruntime.

``realgame_ppo`` now scores non-combat decisions through ``sts2_rs``
(``tract`` + the Rust encoder) instead of :class:`ppo.onnx_policy.
OnnxPolicy` (``onnxruntime`` + the numpy encoder), which is what lets the
advisor bundle drop both libraries. Two component gates already pin the
halves -- ``ppo_encoder_parity`` fixes Rust featurization against
``ppo/encoder.py`` on committed fixtures, ``ppo_net_parity`` fixes tract
against torch -- but neither runs on *live bridge observations*, which are
built by ``realgame_ppo._build_*`` rather than by the training env and reach
shapes (103-option shop removals, empty map graphs, event payloads) the
fixtures do not carry.

So this replays recorded decisions through both backends and compares the
answer that actually matters: **which action gets picked**. A probability
difference of 1e-6 changes nothing; an argmax flip changes the run.

The gate is argmax agreement. Probability deltas are reported as
distribution statistics, not asserted, because the two paths legitimately
differ in the last bits: Rust softmaxes in f64 and returns f32, onnxruntime
softmaxes in f64 and returns f64.

Usage::

    uv run python python/diag_ppo_policy_parity.py [--logs logs] [--limit N]
        [--surface map_select] [--verbose]

Exits non-zero if any decision's argmax disagrees. Prints a per-surface
tally and the probability-delta distribution either way.
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import sys
from collections import Counter, defaultdict
from typing import Any

import realgame_ppo
from ppo import rust_policy
from ppo.onnx_policy import OnnxPolicy
from ppo.schema import BudgetExceededError
from workbench.ppo_policy import onnx_path

#: Surfaces reachable through the type->builder table. ``card_select`` is
#: deliberately absent: it needs the PREVIOUS message type to know which
#: screen opened it, which a single recorded row does not carry.
SURFACES = tuple(realgame_ppo._PPO_NONCOMBAT_BUILDERS)

#: An argmax flip is a finding at any margin, but the ones where the top two
#: candidates are this close were going to be coin flips anyway -- reported
#: separately so a genuine divergence is not buried under near-ties.
NEAR_TIE = 1e-4


class Backends:
    """One loaded pair per character, built lazily."""

    def __init__(self) -> None:
        self._cache: dict[str, tuple[Any, Any] | None] = {}

    def get(self, character: str) -> tuple[Any, Any] | None:
        key = (character or "ironclad").strip().lower()
        if key not in self._cache:
            path = onnx_path(character)
            if path is None:
                print(f"  ! no ONNX champion for character={character!r} -- skipping its rows")
                self._cache[key] = None
            else:
                self._cache[key] = (rust_policy.RustPolicy(path), OnnxPolicy(path))
        return self._cache[key]


def _argmax(probs: list[float]) -> int:
    """First-max index -- the tie-break numpy uses and the one both
    ``realgame_ppo._select_index`` (temperature 0) and the training env's
    ``logp.argmax()`` matched."""
    return max(range(len(probs)), key=probs.__getitem__)


def _margin(probs: list[float]) -> float:
    """Gap between the top two candidates, or 1.0 for a forced pick."""
    if len(probs) < 2:
        return 1.0
    top = sorted(probs, reverse=True)
    return top[0] - top[1]


def compare_row(
    state: dict[str, Any], surface: str, backends: Backends
) -> tuple[str, dict[str, Any]] | None:
    """Score one recorded decision on both backends.

    Returns ``(outcome, detail)`` where outcome is ``agree`` / ``flip`` /
    ``budget`` / ``skip``, or ``None`` when the row has nothing to score.
    """
    builder = realgame_ppo._PPO_NONCOMBAT_BUILDERS.get(surface)
    if builder is None:
        return None
    try:
        decision = builder(state)
    except Exception as exc:
        return "skip", {"reason": f"builder raised: {type(exc).__name__}: {exc}"}
    # Fewer than two candidates is a forced pick: `realgame_ppo` short-circuits
    # before ever calling the net, so scoring it here would compare a code
    # path the live lane does not take.
    if decision is None or len(decision.actions) < 2:
        return None

    character = (state.get("run_state") or {}).get("character") or "Ironclad"
    pair = backends.get(character)
    if pair is None:
        return "skip", {"reason": f"no champion for {character!r}"}
    rust, onnx = pair

    results = {}
    for name, policy in (("rust", rust), ("onnx", onnx)):
        try:
            results[name] = policy.distribution(decision.obs, decision.actions, decision.metrics)
        except BudgetExceededError as e:
            results[name] = e
        except Exception as e:
            # A backend that cannot score an observation the other one can is
            # the single most important thing this harness can find: on the
            # live path it becomes a random pick, silently, because the
            # advisor never crashes. Reported per (backend, message) rather
            # than raised, so one bad shape does not hide the rest.
            return "error", {"detail": f"{name} raised {type(e).__name__}: {e}"}

    over = {n for n, r in results.items() if isinstance(r, BudgetExceededError)}
    if over:
        # Both must agree that the decision does not fit: the budget is read
        # off the same graph on both sides, so a one-sided overflow means one
        # of them counts tokens differently -- a real encoder divergence, and
        # one that silently converts a net decision into a random pick.
        if len(over) == 2:
            return "budget", {"detail": "both over budget"}
        return "flip", {
            "detail": f"budget disagreement: over={sorted(over)}",
            "n_actions": len(decision.actions),
        }

    (rp, rv), (op, ov) = results["rust"], results["onnx"]
    if len(rp) != len(op):
        return "flip", {"detail": f"length {len(rp)} != {len(op)}"}

    ra, oa = _argmax(rp), _argmax(op)
    max_dp = max((abs(a - b) for a, b in zip(rp, op)), default=0.0)
    detail = {
        "max_prob_delta": max_dp,
        "value_delta": abs(rv - ov),
        "margin": _margin(op),
        "n_actions": len(rp),
    }
    if ra != oa:
        detail["detail"] = (
            f"argmax {ra} != {oa} "
            f"(rust p={rp[ra]:.6f}/{rp[oa]:.6f}, onnx p={op[ra]:.6f}/{op[oa]:.6f})"
        )
        detail["labels"] = [
            decision.labels[i] if i < len(decision.labels) else "?" for i in (ra, oa)
        ]
        return "flip", detail
    return "agree", detail


def iter_states(logs_dir: str, surfaces: tuple[str, ...], limit: int | None):
    """Yield ``(surface, state)`` for every recorded decision in ``logs_dir``."""
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
    ap.add_argument("--verbose", action="store_true", help="print every flip")
    ap.add_argument("--max-report", type=int, default=20, help="stop printing after N flips")
    args = ap.parse_args(argv)

    if not rust_policy.available():
        print(f"FATAL: {rust_policy.describe_unavailable()}")
        return 2

    surfaces = tuple(args.surface) if args.surface else SURFACES
    backends = Backends()
    scored: Counter[str] = Counter()
    flips: Counter[str] = Counter()
    near_ties: Counter[str] = Counter()
    budget: Counter[str] = Counter()
    skipped: Counter[str] = Counter()
    errors: Counter[str] = Counter()
    error_kinds: Counter[str] = Counter()
    deltas: dict[str, list[float]] = defaultdict(list)
    reported = 0

    for surface, state in iter_states(args.logs, surfaces, args.limit):
        out = compare_row(state, surface, backends)
        if out is None:
            continue
        outcome, detail = out
        if outcome == "skip":
            skipped[surface] += 1
            continue
        if outcome == "error":
            errors[surface] += 1
            error_kinds[detail["detail"]] += 1
            continue
        if outcome == "budget":
            budget[surface] += 1
            continue
        scored[surface] += 1
        if "max_prob_delta" in detail:
            deltas[surface].append(detail["max_prob_delta"])
        if outcome == "flip":
            flips[surface] += 1
            if detail.get("margin", 1.0) < NEAR_TIE:
                near_ties[surface] += 1
            if reported < args.max_report:
                reported += 1
                print(f"\n--- ARGMAX FLIP #{reported} [{surface}] ---")
                print(f"  {detail.get('detail')}")
                if "labels" in detail:
                    print(f"  rust -> {detail['labels'][0]!r}  onnx -> {detail['labels'][1]!r}")
                print(
                    f"  margin={detail.get('margin', float('nan')):.2e} "
                    f"n_actions={detail.get('n_actions')}"
                )

    total = sum(scored.values())
    bad = sum(flips.values())
    print("\n=== rust-vs-onnxruntime policy parity ===")
    for surface in SURFACES:
        if not scored[surface]:
            continue
        d = deltas[surface]
        worst = max(d) if d else 0.0
        extra = f" budget={budget[surface]}" if budget[surface] else ""
        extra += f" skipped={skipped[surface]}" if skipped[surface] else ""
        print(
            f"  {surface:<12} {scored[surface] - flips[surface]:>6} / {scored[surface]:<6}"
            f" same pick   max|dp|={worst:.2e}{extra}"
        )
    if total == 0:
        print("  no scorable decisions found -- nothing was compared")
        return 1
    all_d = [x for v in deltas.values() for x in v]
    print(f"  {'TOTAL':<12} {total - bad:>6} / {total:<6} same pick   max|dp|={max(all_d):.2e}")
    if bad:
        ties = sum(near_ties.values())
        print(f"  {bad} argmax flip(s), of which {ties} at a margin below {NEAR_TIE:g}")
    n_err = sum(errors.values())
    if n_err:
        print(f"\n  {n_err} decision(s) a backend could not score:")
        for kind, n in error_kinds.most_common(10):
            print(f"    {n:>6}  {kind}")
    return 1 if (bad or n_err) else 0


if __name__ == "__main__":
    sys.exit(main())
