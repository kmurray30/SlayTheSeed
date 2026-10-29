#!/usr/bin/env python3
"""item#7: mutation testing for the C#-vs-Rust parity suite.

A differential suite is only as good as its *sensitivity*: if you perturb the
Rust engine and the sweep stays green, that perturbation is a blind spot. This
driver injects a catalog of small, compile-safe source mutations into the Rust
engine one at a time, rebuilds, runs a fast parity slice, and records whether
the sweep CAUGHT the mutation (went non-clean) or it SURVIVED (sweep still all
clean → a real gap in coverage).

It is the empirical complement to the other parity-gap work: instead of
reasoning about what the suite might miss, it measures what it actually misses.

Usage (run through the wheel venv so the rebuild lands in the right .so):

    scripts/wheel-venv.sh run -- python python/mutation_test.py
    scripts/wheel-venv.sh run -- python python/mutation_test.py --tier quick --axes fuzz

Safety: the engine source tree must be git-clean before running (so a crash
mid-mutation can always be restored), each mutation is reverted in a `finally`,
and a clean rebuild is forced at the end so a later sweep never runs against a
left-over mutated .so. Exits non-zero if any mutation SURVIVED.
"""

from __future__ import annotations

import argparse
import dataclasses
import os
import re
import subprocess
import sys

_PYCLI_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_RS_ROOT = os.path.normpath(os.path.join(_PYCLI_ROOT, "..", "sts2-rs"))
_WHEEL_VENV = os.path.join(_PYCLI_ROOT, "scripts", "wheel-venv.sh")


@dataclasses.dataclass(frozen=True)
class Mutation:
    """One surgical, compile-safe engine source edit.

    ``old`` must occur EXACTLY ONCE in ``file`` (the driver refuses to apply an
    ambiguous mutation — a catalog that drifted against the source is a bug, not
    a silent skip). ``expect`` documents the intended catch difficulty:
    ``"easy"`` mutations exercise a hot path and should always be caught (they
    are the suite's smoke test); ``"hard"`` mutations fire only on a narrow path
    and may legitimately survive a small slice (raise --tier to chase them).
    """

    label: str
    file: str  # relative to sts2-rs/
    old: str
    new: str
    expect: str  # "easy" | "hard"


# Seed catalog. Keep each edit a single, reversible, compile-safe token swap.
MUTATIONS: list[Mutation] = [
    Mutation(
        label="energy-drain-becomes-gain",
        file="crates/sts2-core/src/combat_loop.rs",
        old="state.player.energy = state.player.energy.saturating_sub(cost_i8);",
        new="state.player.energy = state.player.energy.saturating_add(cost_i8);",
        expect="easy",  # every card play drains energy → player.energy diverges
    ),
    Mutation(
        label="damage-relic-additive-flip",
        file="crates/sts2-core/src/damage.rs",
        old="let raw_damage_with_relic_additive = raw_damage.saturating_add(relic_additive);",
        new="let raw_damage_with_relic_additive = raw_damage.saturating_sub(relic_additive);",
        expect="hard",  # only matters when an additive-damage relic is present
    ),
]


# Dedicated trace cache for the mutation run: C# is never mutated, so its
# recorded traces are valid across every mutation (and across reruns) — but it
# must be a CLEAN namespace dir, not the shared ~/.cache one that may hold
# traces from an older oracle build.
_MUT_CACHE_DIR = os.path.join("/tmp", "sts2_parity_mutation_cache")


def _run(cmd: list[str], **kw) -> subprocess.CompletedProcess:
    env = {**os.environ, "STS2_PARITY_CACHE_DIR": _MUT_CACHE_DIR}
    return subprocess.run(cmd, cwd=_PYCLI_ROOT, text=True, capture_output=True, env=env, **kw)


def _engine_is_clean() -> bool:
    r = _run(["git", "-C", _RS_ROOT, "status", "--porcelain", "crates"])
    return r.returncode == 0 and not r.stdout.strip()


def _rebuild() -> bool:
    print("    rebuilding wheel…", file=sys.stderr)
    r = _run([_WHEEL_VENV, "build"])
    if r.returncode != 0:
        print(r.stdout[-2000:], r.stderr[-2000:], file=sys.stderr)
    return r.returncode == 0


_BUCKET_RE = re.compile(r"^\s*\d+\s+(\S.*)$")


def _sweep_is_clean(tier: str, axes: str) -> tuple[bool, str]:
    """Run a parity slice; return (all_clean, summary). all_clean is True iff the
    ONLY bucket the sweep reported was ``clean``."""
    r = _run(
        [
            _WHEEL_VENV,
            "run",
            "--",
            "python",
            "python/parity_audit.py",
            "--sweep",
            "v2",
            "--tier",
            tier,
            "--axes",
            axes,
            "--sweep-character",
            "Ironclad",
        ]
    )
    # The summary block prints one "  <count>  <bucket>" line per bucket.
    buckets: dict[str, int] = {}
    for line in (r.stdout + r.stderr).splitlines():
        m = _BUCKET_RE.match(line)
        if m and (m.group(1) == "clean" or m.group(1).isupper() or ":" in m.group(1)):
            label = m.group(1).strip()
            try:
                buckets[label] = int(line.split()[0])
            except ValueError:
                continue
    non_clean = {k: v for k, v in buckets.items() if k != "clean"}
    summary = ", ".join(f"{k}={v}" for k, v in non_clean.items()) or "all clean"
    return (not non_clean), summary


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--tier", default="smoke", choices=["smoke", "quick", "mid", "full"])
    p.add_argument("--axes", default="fuzz", help="v2 axes for the slice (default fuzz)")
    p.add_argument(
        "--only", default=None, help="run only the mutation with this label (substring match)"
    )
    args = p.parse_args(argv)

    if not _engine_is_clean():
        sys.exit(
            "engine source tree is dirty — commit/stash crates/ first so a "
            "mutation can always be cleanly reverted."
        )

    catalog = [m for m in MUTATIONS if not args.only or args.only in m.label]
    if not catalog:
        sys.exit(f"no mutation matches --only {args.only!r}")

    results: list[tuple[Mutation, str, str]] = []  # (mutation, verdict, summary)
    for mut in catalog:
        path = os.path.join(_RS_ROOT, mut.file)
        print(f"\n=== mutation: {mut.label} ({mut.expect}) ===", file=sys.stderr)
        with open(path, encoding="utf-8") as f:
            original = f.read()
        n = original.count(mut.old)
        if n != 1:
            print(f"    SKIP — anchor occurs {n}× (expected 1); catalog drift", file=sys.stderr)
            results.append((mut, "SKIP", f"anchor {n}×"))
            continue
        try:
            with open(path, "w", encoding="utf-8") as f:
                f.write(original.replace(mut.old, mut.new))
            if not _rebuild():
                results.append((mut, "BUILD-FAIL", "did not compile"))
                continue
            clean, summary = _sweep_is_clean(args.tier, args.axes)
            verdict = "SURVIVED" if clean else "caught"
            results.append((mut, verdict, summary))
            print(f"    {verdict}: {summary}", file=sys.stderr)
        finally:
            with open(path, "w", encoding="utf-8") as f:
                f.write(original)

    # Always leave the installed .so matching clean source.
    print("\n=== restoring clean engine ===", file=sys.stderr)
    _rebuild()

    print("\n=== mutation-test summary ===", file=sys.stderr)
    survived = []
    for mut, verdict, summary in results:
        print(f"  {verdict:11s} {mut.label:32s} {summary}", file=sys.stderr)
        if verdict == "SURVIVED":
            survived.append(mut)
    if survived:
        print(
            f"\n{len(survived)} mutation(s) SURVIVED — the suite did not catch them at "
            f"--tier {args.tier} --axes {args.axes}. Either a coverage gap, or raise the "
            "tier/axes to reach the affected path.",
            file=sys.stderr,
        )
        return 1
    print("\nall mutations caught — suite is sensitive on the tested paths.", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
