"""SimLab CLI: `python -m simlab run|summarize`."""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

from simlab.specs import DECK_PRESETS, SweepSpec, validate_deck, validate_encounter
from simlab.telemetry import read_rows, summarize

DEFAULT_OUT = Path("results/simlab")


def _print_summary(summary: dict) -> None:
    cols = ["n", "mean_dmg", "median_dmg", "p95_dmg", "max_dmg", "win_rate", "mean_turns", "errors"]
    print(f"{'bucket':<48}" + "".join(f"{c:>12}" for c in cols))
    for key in sorted(summary["buckets"]):
        b = summary["buckets"][key]
        print(f"{key:<48}" + "".join(f"{b[c]:>12}" for c in cols))


def _print_throughput(tp: dict) -> None:
    """Print the per-core and wall-clock throughput block from a summary."""
    if not tp:
        return
    wall = tp.get("wall", {})
    print(
        f"\nthroughput  {tp.get('combats', 0)} combats, "
        f"{tp.get('actions', 0)} actions, {tp.get('turns', 0)} turns",
        file=sys.stderr,
    )
    print(
        f"  per core   {tp.get('combats_per_core_s', 0):.2f} combat/s  "
        f"{tp.get('actions_per_core_s', 0):.1f} act/s  "
        f"{tp.get('turns_per_core_s', 0):.1f} turn/s",
        file=sys.stderr,
    )
    if wall:
        print(
            f"  wall ({wall.get('jobs', '?')} jobs)  "
            f"{wall.get('combats_per_s', 0):.2f} combat/s  "
            f"{wall.get('actions_per_s', 0):.1f} act/s  "
            f"{wall.get('turns_per_s', 0):.1f} turn/s  "
            f"(efficiency {wall.get('parallel_efficiency', 0):.0%})",
            file=sys.stderr,
        )


def _cmd_run(args: argparse.Namespace) -> int:
    from simlab.agents import AGENTS  # late import alongside runner
    from simlab.runner import run_sweep  # late import: heavy (sts2_rs)

    unknown = set(args.strategies.split(",")) - AGENTS.keys()
    if unknown:
        print(
            f"error: unknown strategies {sorted(unknown)}; valid: {sorted(AGENTS)}",
            file=sys.stderr,
        )
        return 2

    # Preset decks (CLI order), then any custom --deck-json variants appended after.
    decks: list[tuple[str, list[str]]] = []
    for name in args.deck:
        if name not in DECK_PRESETS:
            print(
                f"error: unknown deck {name!r}; valid: {sorted(DECK_PRESETS)}",
                file=sys.stderr,
            )
            return 2
        decks.append((name, list(DECK_PRESETS[name])))

    if args.deck_json is not None:
        json_path = Path(args.deck_json)
        try:
            custom = json.loads(json_path.read_text(encoding="utf-8"))
            if not isinstance(custom, dict):
                raise TypeError("expected a JSON object")
        except (OSError, json.JSONDecodeError, TypeError) as exc:
            print(f"error: --deck-json {args.deck_json}: {exc}", file=sys.stderr)
            return 2
        for name, cards in custom.items():
            if not isinstance(cards, list) or not all(isinstance(c, str) for c in cards):
                print(
                    f"error: --deck-json: value for {name!r} must be a list of card ids",
                    file=sys.stderr,
                )
                return 2
            decks.append((name, list(cards)))

    try:
        for name, cards in decks:
            validate_deck(name, cards)
        for e in args.encounter:
            validate_encounter(e)
        sweep = SweepSpec(
            name=args.name,
            encounters=args.encounter,
            ascensions=[int(a) for a in args.ascension],
            strategies=args.strategies.split(","),
            n_seeds=args.seeds,
            decks=decks,
            mcts_sims=args.mcts_sims,
            mcts_depth=args.mcts_depth,
        )
    except ValueError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    out_dir = Path(args.out) / sweep.name
    t0 = time.perf_counter()

    def progress(done: int, total: int) -> None:
        elapsed = time.perf_counter() - t0
        rate = done / elapsed if elapsed > 0 else 0.0
        print(f"\r[{done}/{total}] combats  {rate:6.1f}/s", end="", file=sys.stderr, flush=True)

    run_sweep(sweep, out_dir=out_dir, jobs=args.jobs, progress=progress)
    print(file=sys.stderr)
    summary = json.loads((out_dir / "summary.json").read_text(encoding="utf-8"))
    _print_summary(summary)
    _print_throughput(summary.get("throughput", {}))
    print(f"\n{time.perf_counter() - t0:.1f}s -> {out_dir}", file=sys.stderr)
    return 0


def _cmd_bench(args: argparse.Namespace) -> int:
    from simlab.bench import bench  # late import: heavy (sts2_rs)

    bench(args.seeds)
    return 0


def _cmd_summarize(args: argparse.Namespace) -> int:
    rows_path = Path(args.sweep_dir) / "rows.jsonl"
    if not rows_path.exists():
        print(f"error: {rows_path} not found", file=sys.stderr)
        return 1
    rows = read_rows(rows_path)
    _print_summary(summarize(rows))
    return 0


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(prog="simlab")
    sub = p.add_subparsers(dest="cmd", required=True)

    run = sub.add_parser("run", help="run a sweep")
    run.add_argument("--name", required=True)
    run.add_argument(
        "--encounter",
        action="append",
        required=True,
        help='encounter id (e.g. MAWLER_NORMAL) or "monsters:Name,Name" custom lineup (repeatable)',
    )
    run.add_argument("--ascension", action="append", default=None)
    run.add_argument("--strategies", default="random,block_first,attack_first,min_overblock")
    run.add_argument("--seeds", type=int, default=1000)
    run.add_argument(
        "--deck",
        action="append",
        default=None,
        help=(
            "deck preset name (repeatable; default ironclad_starter). "
            f"Valid: {sorted(DECK_PRESETS)}"
        ),
    )
    run.add_argument(
        "--deck-json",
        default=None,
        help="JSON file of {name: [card_id, ...]} custom decks, appended after presets",
    )
    run.add_argument("--jobs", type=int, default=0)
    run.add_argument("--mcts-sims", type=int, default=400)
    run.add_argument("--mcts-depth", type=int, default=200)
    run.add_argument("--out", default=str(DEFAULT_OUT))
    run.set_defaults(fn=_cmd_run)

    bench_p = sub.add_parser("bench", help="per-combat wall-clock bench")
    bench_p.add_argument("--seeds", type=int, default=50)
    bench_p.set_defaults(fn=_cmd_bench)

    summ = sub.add_parser("summarize", help="re-print summary for a sweep dir")
    summ.add_argument("sweep_dir")
    summ.set_defaults(fn=_cmd_summarize)

    args = p.parse_args(argv)
    if args.fn is _cmd_run:
        if not args.ascension:
            args.ascension = ["0"]
        if not args.deck:
            args.deck = ["ironclad_starter"]
    return args.fn(args)


if __name__ == "__main__":
    raise SystemExit(main())
