#!/usr/bin/env python3
"""Wall-clock benchmark for ``sts2_hint.recommender.recommend``.

Bead: ``sts2-rs-886i`` (hint-T13, sts2-pycli side).

Measures end-to-end overhead per turn at the plan's reference config
(sims=300, fair=False, threads=1) over ~100 turn-decisions collected
by walking multiple seeded combats end-to-end.

Output: ``tests/bench_baselines/hint_overhead.json`` (only when run
with ``--update-baseline``; default just prints the comparison vs the
existing baseline, if any).

Usage::

    uv run python tests/bench_hint_overhead.py
    uv run python tests/bench_hint_overhead.py --update-baseline
    uv run python tests/bench_hint_overhead.py --samples 50

The bench piggybacks on :mod:`tests.smoke_hint` for the
combat-driving harness so the two stay in lock-step.
"""

from __future__ import annotations

import argparse
import json
import os
import platform
import random
import subprocess
import sys
import time
from datetime import UTC, datetime
from pathlib import Path

_REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(_REPO_ROOT / "python"))

from sts2_client import Game  # noqa: E402
from sts2_hint.recommender import RecommendConfig, recommend  # noqa: E402

BASELINE_PATH = _REPO_ROOT / "tests" / "bench_baselines" / "hint_overhead.json"
SCHEMA_VERSION = 1

DEFAULT_ENCOUNTER = os.environ.get("STS2_SMOKE_ENCOUNTER", "SHRINKER_BEETLE_WEAK")
DEFAULT_CHARACTER = os.environ.get("STS2_SMOKE_CHARACTER", "Ironclad")
MAX_TURNS_PER_COMBAT = 100


def _pick_random_action(state: dict, rng: random.Random) -> tuple[str, dict]:
    """Mirror of smoke_hint._pick_random_action — kept in sync."""
    hand = state.get("hand", [])
    energy = int(state.get("energy", 0))
    playable = [c for c in hand if c.get("can_play") and int(c.get("cost", 99)) <= energy]
    if playable and rng.random() < 0.75:
        card = rng.choice(playable)
        target_idx: int | None = None
        if card.get("target_type") == "AnyEnemy":
            alive = [e for e in state.get("enemies", []) if int(e.get("hp", 0)) > 0]
            if alive:
                target_idx = int(rng.choice(alive)["index"])
        return "play_card", {
            "card_index": int(card["index"]),
            "target_index": target_idx,
        }
    return "end_turn", {}


def _git_sha() -> str:
    try:
        out = subprocess.check_output(
            ["git", "-C", str(_REPO_ROOT), "rev-parse", "HEAD"],
            stderr=subprocess.DEVNULL,
            text=True,
        )
        return out.strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return "unknown"


def collect_samples(
    *,
    n_target: int,
    sims: int,
    encounter: str,
    character: str,
    starting_seed: int = 1,
) -> list[float]:
    """Drive seeded combats until ``n_target`` recommend() samples are
    collected. Returns sample latencies in milliseconds.

    UnsupportedStateError / other failures from the recommender are
    skipped (they don't represent meaningful latency); the bench is
    purely about end-to-end cost on the happy path.
    """
    cfg = RecommendConfig(sims=sims, depth=10, fair=False, threads=1, character=character)
    samples_ms: list[float] = []
    failures = 0
    # Cap total seeds tried so a 100% adapter-failure scenario can't hang.
    max_seeds = max(200, n_target * 5)

    seed_idx = starting_seed
    while len(samples_ms) < n_target and (seed_idx - starting_seed) < max_seeds:
        rng = random.Random(seed_idx)
        game = Game()
        try:
            game.start(character=character, seed=str(seed_idx), ascension=0)
            state = game.enter_room("combat", encounter=encounter)
            if state.get("decision") != "combat_play":
                seed_idx += 1
                continue
            turn = 0
            while (
                state.get("decision") == "combat_play"
                and turn < MAX_TURNS_PER_COMBAT
                and len(samples_ms) < n_target
            ):
                turn += 1
                t0 = time.perf_counter()
                try:
                    recommend(state, cfg)
                except Exception:
                    dt = None
                    failures += 1
                else:
                    dt = (time.perf_counter() - t0) * 1000.0
                if dt is not None:
                    samples_ms.append(dt)

                verb, kwargs = _pick_random_action(state, rng)
                try:
                    state = game.act(verb, **kwargs)
                except Exception:
                    break
        finally:
            game.close()
        seed_idx += 1

    if not samples_ms:
        print(
            f"\nWARNING: collected 0 successful samples after {seed_idx - starting_seed} "
            f"seeds ({failures} recommend() failures). The adapter is failing "
            "100% of live combat states — see tests/smoke_hint.py output for details.",
            file=sys.stderr,
        )
    return samples_ms


def _percentile(sorted_samples: list[float], pct: float) -> float:
    if not sorted_samples:
        return float("nan")
    # Nearest-rank percentile — adequate at n=100.
    k = max(0, min(len(sorted_samples) - 1, round((pct / 100.0) * (len(sorted_samples) - 1))))
    return sorted_samples[k]


def summarize(samples_ms: list[float]) -> dict:
    s = sorted(samples_ms)
    return {
        "samples_n": len(s),
        "p50_ms": round(_percentile(s, 50.0), 3),
        "p95_ms": round(_percentile(s, 95.0), 3),
        "max_ms": round(s[-1], 3) if s else float("nan"),
        "min_ms": round(s[0], 3) if s else float("nan"),
    }


def _load_baseline() -> dict | None:
    if not BASELINE_PATH.exists():
        return None
    try:
        with BASELINE_PATH.open("r", encoding="utf-8") as f:
            return json.load(f)
    except (OSError, json.JSONDecodeError):
        return None


def _write_baseline(record: dict) -> None:
    BASELINE_PATH.parent.mkdir(parents=True, exist_ok=True)
    with BASELINE_PATH.open("w", encoding="utf-8") as f:
        json.dump(record, f, indent=2, sort_keys=True)
        f.write("\n")


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--samples", type=int, default=100, help="target sample count (default 100)")
    p.add_argument("--sims", type=int, default=300, help="MCTS sims per call (default 300)")
    p.add_argument("--encounter", default=DEFAULT_ENCOUNTER)
    p.add_argument("--character", default=DEFAULT_CHARACTER)
    p.add_argument("--starting-seed", type=int, default=1)
    p.add_argument(
        "--update-baseline",
        action="store_true",
        help=f"write results to {BASELINE_PATH.relative_to(_REPO_ROOT)}",
    )
    args = p.parse_args(argv)

    print(
        f"sts2_hint bench: target={args.samples} samples, "
        f"sims={args.sims}, encounter={args.encounter}, character={args.character}"
    )

    t0 = time.perf_counter()
    samples = collect_samples(
        n_target=args.samples,
        sims=args.sims,
        encounter=args.encounter,
        character=args.character,
        starting_seed=args.starting_seed,
    )
    wall = time.perf_counter() - t0

    summary = summarize(samples)
    print(f"\nCollected {summary['samples_n']} samples in {wall:.1f}s wall:")
    if summary["samples_n"] == 0:
        print("  (no samples — recommend() failed on every state; cannot bench)")
        return 2
    print(f"  p50  = {summary['p50_ms']:.2f} ms")
    print(f"  p95  = {summary['p95_ms']:.2f} ms")
    print(f"  max  = {summary['max_ms']:.2f} ms")
    print(f"  min  = {summary['min_ms']:.2f} ms")

    record = {
        "schema_version": SCHEMA_VERSION,
        "timestamp_utc": datetime.now(UTC).isoformat(),
        "host": platform.node(),
        "platform": platform.platform(),
        "git_sha": _git_sha(),
        "config": {
            "sims": args.sims,
            "fair": False,
            "threads": 1,
            "depth": 10,
            "encounter": args.encounter,
            "character": args.character,
        },
        "samples_n": summary["samples_n"],
        "p50_ms": summary["p50_ms"],
        "p95_ms": summary["p95_ms"],
        "max_ms": summary["max_ms"],
    }

    baseline = _load_baseline()
    if baseline is not None:
        print(
            f"\nBaseline ({baseline.get('timestamp_utc', '?')}, "
            f"git={(baseline.get('git_sha') or '')[:8]}):"
        )
        for k in ("p50_ms", "p95_ms", "max_ms"):
            b = baseline.get(k)
            cur = record[k]
            if isinstance(b, (int, float)) and b > 0:
                delta_pct = (cur - b) / b * 100.0
                print(f"  {k}: {cur:.2f} ms vs baseline {b:.2f} ms ({delta_pct:+.1f}%)")
            else:
                print(f"  {k}: {cur:.2f} ms (no baseline value)")
    else:
        print("\nNo baseline found.")

    if args.update_baseline:
        _write_baseline(record)
        rel = BASELINE_PATH.relative_to(_REPO_ROOT)
        print(f"\nWrote baseline to {rel}")
    else:
        print(f"\n(use --update-baseline to overwrite {BASELINE_PATH.relative_to(_REPO_ROOT)})")

    return 0


if __name__ == "__main__":
    sys.exit(main())
