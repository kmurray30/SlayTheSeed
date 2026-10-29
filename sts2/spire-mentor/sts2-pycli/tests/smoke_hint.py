#!/usr/bin/env python3
"""50-combat seeded smoke gate for the sts2_hint MCTS recommender.

Bead: ``sts2-rs-886i`` (hint-T13, sts2-pycli side).

Drives full combats via the live :class:`sts2_client.Game` (the same
Sts2Headless C# binary the rest of sts2-pycli uses), and calls
``sts2_hint.recommender.recommend(state, RecommendConfig(sims=300))``
at every turn. Each combat uses a deterministic seed.

The gate asserts ZERO :class:`sts2_hint.errors.UnsupportedStateError`
across all configured seeds. Other exceptions are reported with a
distinct flavor so an environment break (C# binary missing, etc.)
is distinguishable from a real fail-loud adapter hit.

Usage::

    uv run python tests/smoke_hint.py --seeds 1-50
    uv run python tests/smoke_hint.py --seeds 1 2 3
    uv run python tests/smoke_hint.py --random-seeds 10   # may legitimately fail

Free-running mode (``--random-seeds N``) is documented as "may
legitimately hit fail-loud paths on unported game content" and is
used for CI-flakiness / coverage investigation, not as a gate.
"""

from __future__ import annotations

import argparse
import os
import random
import secrets
import sys
import time
import traceback
from dataclasses import dataclass
from pathlib import Path

# Make ``python/`` importable when this script is invoked directly.
_REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(_REPO_ROOT / "python"))

from sts2_client import Game  # noqa: E402
from sts2_hint.errors import UnsupportedStateError  # noqa: E402
from sts2_hint.recommender import RecommendConfig, recommend  # noqa: E402

# Default encounter — the canonical "small Ironclad combat" used by
# tests/test_combat.py and tests/test_hint_recommender.py: the
# SHRINKER_BEETLE_WEAK encounter, which is small and Ironclad-friendly.
DEFAULT_ENCOUNTER = os.environ.get("STS2_SMOKE_ENCOUNTER", "SHRINKER_BEETLE_WEAK")
DEFAULT_CHARACTER = os.environ.get("STS2_SMOKE_CHARACTER", "Ironclad")
MAX_TURNS_PER_COMBAT = 100  # safety net


@dataclass
class Failure:
    seed: int
    turn: int
    kind: str  # "UNSUPPORTED_STATE" | "OTHER"
    message: str
    json_path: str | None = None
    traceback: str | None = None


def _pick_random_action(state: dict, rng: random.Random) -> tuple[str, dict]:
    """Choose a legal action for the current ``combat_play`` state.

    Random play — the smoke is about adapter coverage, not optimal
    play. Returns ``(verb, kwargs)`` tuple to pass to ``game.act``.
    """
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


def _parse_seed_arg(spec: list[str]) -> list[int]:
    """Parse ``--seeds 1-50`` or ``--seeds 1 2 3`` style args."""
    seeds: list[int] = []
    for chunk in spec:
        if "-" in chunk:
            lo_s, hi_s = chunk.split("-", 1)
            lo, hi = int(lo_s), int(hi_s)
            if lo > hi:
                lo, hi = hi, lo
            seeds.extend(range(lo, hi + 1))
        else:
            seeds.append(int(chunk))
    return seeds


def _run_one_combat(
    game: Game,
    seed: int,
    *,
    encounter: str,
    character: str,
    cfg: RecommendConfig,
) -> list[Failure]:
    """Drive a single combat from start to end at ``seed``.

    Returns a list of Failures encountered (empty == clean). Failures
    don't abort the combat — we keep going on the C# side so a single
    seed surfaces every distinct adapter gap.
    """
    fails: list[Failure] = []
    rng = random.Random(seed)

    # Fresh run for this seed.
    game.start(character=character, seed=str(seed), ascension=0)
    state = game.enter_room("combat", encounter=encounter)
    if state.get("decision") != "combat_play":
        fails.append(
            Failure(
                seed=seed,
                turn=0,
                kind="OTHER",
                message=(
                    f"enter_room did not produce combat_play; got "
                    f"decision={state.get('decision')!r}"
                ),
            )
        )
        return fails

    turn = 0
    while state.get("decision") == "combat_play" and turn < MAX_TURNS_PER_COMBAT:
        turn += 1
        # 1) Call the recommender — this is what we're gating.
        try:
            recommend(state, cfg)
        except UnsupportedStateError as e:
            msg = str(e)
            json_path: str | None = None
            # The adapter convention is "<json-path>: <detail>" — grab
            # the first colon-separated token for diagnostic clarity.
            if ":" in msg:
                json_path = msg.split(":", 1)[0].strip()
            fails.append(
                Failure(
                    seed=seed,
                    turn=turn,
                    kind="UNSUPPORTED_STATE",
                    message=msg,
                    json_path=json_path,
                )
            )
        except Exception as e:
            fails.append(
                Failure(
                    seed=seed,
                    turn=turn,
                    kind="OTHER",
                    message=f"{type(e).__name__}: {e}",
                    traceback=traceback.format_exc(),
                )
            )

        # 2) Drive the combat with a random legal action regardless of
        # whether the recommender succeeded — we want full turn coverage.
        verb, kwargs = _pick_random_action(state, rng)
        try:
            state = game.act(verb, **kwargs)
        except Exception as e:
            fails.append(
                Failure(
                    seed=seed,
                    turn=turn,
                    kind="OTHER",
                    message=f"game.act({verb}) raised: {type(e).__name__}: {e}",
                    traceback=traceback.format_exc(),
                )
            )
            return fails

    return fails


def run_smoke(
    seeds: list[int],
    *,
    encounter: str = DEFAULT_ENCOUNTER,
    character: str = DEFAULT_CHARACTER,
    sims: int = 300,
    verbose: bool = False,
) -> tuple[int, list[Failure]]:
    """Top-level smoke driver. Returns ``(n_passed, failures)``."""
    cfg = RecommendConfig(sims=sims, depth=10, fair=False, threads=1, character=character)

    failures: list[Failure] = []
    passed = 0
    t0 = time.perf_counter()

    # Fresh Game process per seed — the C# binary's start_run does not
    # fully reset post-combat state in the same process (we observed
    # the second start_run landing in 'card_reward' instead of an
    # idle pre-combat decision), so we keep isolation strict.
    for i, seed in enumerate(seeds, start=1):
        seed_t0 = time.perf_counter()
        game = Game()
        try:
            seed_failures = _run_one_combat(
                game,
                seed,
                encounter=encounter,
                character=character,
                cfg=cfg,
            )
        except Exception as e:
            seed_failures = [
                Failure(
                    seed=seed,
                    turn=-1,
                    kind="OTHER",
                    message=f"driver crash: {type(e).__name__}: {e}",
                    traceback=traceback.format_exc(),
                )
            ]
        finally:
            game.close()
        dt = time.perf_counter() - seed_t0
        if seed_failures:
            failures.extend(seed_failures)
            status = "FAIL"
        else:
            passed += 1
            status = "ok"
        if verbose or seed_failures:
            print(
                f"  [{i:>3}/{len(seeds)}] seed={seed:<10} "
                f"{status:<4} {dt:6.2f}s "
                f"({len(seed_failures)} failures)"
            )

    total_dt = time.perf_counter() - t0
    print(f"\nTotal wall time: {total_dt:.1f}s for {len(seeds)} combats")
    return passed, failures


def _print_failure_table(failures: list[Failure]) -> None:
    if not failures:
        return
    print("\nFailure detail:")
    unsupported = [f for f in failures if f.kind == "UNSUPPORTED_STATE"]
    other = [f for f in failures if f.kind == "OTHER"]
    if unsupported:
        print(f"\n  UnsupportedStateError ({len(unsupported)}):")
        print(f"    {'seed':<10} {'turn':<5} {'json_path':<40} message")
        for f in unsupported:
            print(f"    {f.seed:<10} {f.turn:<5} {(f.json_path or '?'):<40} {f.message}")
    if other:
        print(f"\n  Other exceptions ({len(other)}):")
        print(f"    {'seed':<10} {'turn':<5} message")
        for f in other:
            print(f"    {f.seed:<10} {f.turn:<5} {f.message}")
            if f.traceback:
                # Indent traceback for readability.
                for line in f.traceback.rstrip().splitlines()[-6:]:
                    print(f"      | {line}")


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument(
        "--seeds",
        nargs="+",
        default=["1-50"],
        help="seed list/range (e.g. '1-50' or '1 2 3'). Default: 1-50.",
    )
    p.add_argument(
        "--random-seeds",
        type=int,
        default=0,
        metavar="N",
        help="instead of fixed seeds, run N combats with random seeds "
        "(may legitimately hit fail-loud paths on unported content).",
    )
    p.add_argument(
        "--encounter",
        default=DEFAULT_ENCOUNTER,
        help=f"C# encounter id (default: {DEFAULT_ENCOUNTER})",
    )
    p.add_argument(
        "--character",
        default=DEFAULT_CHARACTER,
        help=f"character name (default: {DEFAULT_CHARACTER})",
    )
    p.add_argument(
        "--sims",
        type=int,
        default=300,
        help="MCTS simulations per recommend() call (default: 300)",
    )
    p.add_argument(
        "--verbose",
        "-v",
        action="store_true",
        help="print per-combat status lines (default: only failures)",
    )
    args = p.parse_args(argv)

    if args.random_seeds > 0:
        seeds = [int.from_bytes(secrets.token_bytes(4), "big") for _ in range(args.random_seeds)]
        mode = f"random ({args.random_seeds} combats)"
    else:
        seeds = _parse_seed_arg(args.seeds)
        mode = f"fixed seeds {args.seeds}"

    print(
        f"sts2_hint smoke: {len(seeds)} combats | mode={mode} | "
        f"encounter={args.encounter} | character={args.character} | "
        f"sims={args.sims}"
    )

    passed, failures = run_smoke(
        seeds,
        encounter=args.encounter,
        character=args.character,
        sims=args.sims,
        verbose=args.verbose,
    )

    unsupported_count = sum(1 for f in failures if f.kind == "UNSUPPORTED_STATE")
    other_count = sum(1 for f in failures if f.kind == "OTHER")

    print(
        f"\nResult: {passed}/{len(seeds)} combats clean | "
        f"{unsupported_count} UnsupportedStateError | "
        f"{other_count} other-failures"
    )
    _print_failure_table(failures)

    if args.random_seeds > 0:
        # Free-running mode is non-gating; only env-broken paths fail it.
        if other_count > 0:
            print(
                "\nFAIL (random-seeds mode): non-UnsupportedStateError "
                "failures indicate an environment break."
            )
            return 1
        if unsupported_count > 0:
            print(
                "\nNOTE (random-seeds mode): UnsupportedStateError hits "
                "are expected on unported content and do not fail the gate."
            )
        print(
            f"\nOK random-seeds {passed}/{len(seeds)} combats clean "
            f"({unsupported_count} unsupported tolerated)"
        )
        return 0

    if unsupported_count == 0 and other_count == 0:
        print(f"\nOK {passed}/{len(seeds)} combats, 0 UnsupportedStateError")
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
