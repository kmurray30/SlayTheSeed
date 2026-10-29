"""Attribute enemy/player HP desyncs to the effect that caused them. GH #368.1.

A mirror desync record says an enemy has N more HP in the world than in the
mirror. It does not say which of the mirror's effects spent the difference,
and one decision later the mirror has been re-imposed from reality, so the
evidence is gone. Round 7d's ``+6`` class on the Defect arm died on exactly
that: 23 independent fights, one reproducible magnitude, no reachable cause.

With ``STS2_MIRROR_DAMAGE_TRACE=1`` the mirror now attaches the engine's
per-step damage rows (``combat_mirror``'s ``damage_trace``), each labelled
with the dispatcher that produced it. This script joins the two: for every HP
desync it lists the mirror's damage for that step, then reports which
``source`` labels are enriched in a chosen delta bucket versus everything
else.

Usage::

    python python/diag_damage_source.py logs/diag/mirror_defect_trace.jsonl
    python python/diag_damage_source.py <log> --delta 6      # one bucket
    python python/diag_damage_source.py <log> --delta 6 --show 5

Reads ``world_wire``/``mirror_wire`` for context; records without a
``damage_trace`` key are counted and reported separately, because "the sweep
was not armed" and "the step dealt no damage" must not look alike.
"""

from __future__ import annotations

import argparse
import collections
import json
import sys
from pathlib import Path
from typing import Any

#: Desync paths this tool is about. Both HP surfaces, since an over-dealt
#: player hit and an over-dealt enemy hit are the same question asked of
#: different pipelines.
_HP_ENEMY_PREFIX = "$.encounter["
_HP_SUFFIX = "].hp"
_HP_PLAYER = "$.player.hp"


def _hp_desyncs(rec: dict[str, Any]) -> list[tuple[str, int]]:
    """``(path, world - mirror)`` for every HP desync with two real numbers.

    A ``None`` on either side means the creature is absent from one board
    (a spawn or a corpse the dialects disagree about), which is a roster
    finding, not a damage one — those are skipped here on purpose.
    """
    out: list[tuple[str, int]] = []
    for d in rec.get("desyncs") or []:
        path = d.get("path") or ""
        is_hp = (path.startswith(_HP_ENEMY_PREFIX) and path.endswith(_HP_SUFFIX)) or (
            path == _HP_PLAYER
        )
        if not is_hp:
            continue
        mirror, world = d.get("mirror"), d.get("world")
        if not isinstance(mirror, int) or not isinstance(world, int):
            continue
        out.append((path, world - mirror))
    return out


def _target_of(path: str) -> str:
    """The creature key a desync path names (``Rocket#0``, ``player``)."""
    if path == _HP_PLAYER:
        return "player"
    return path[len(_HP_ENEMY_PREFIX) : -len(_HP_SUFFIX)]


def _rows_for_target(rec: dict[str, Any], target: str) -> list[dict[str, Any]]:
    """Trace rows that hit `target`. Falls back to every row when the trace
    names no matching creature — a target the trace cannot resolve is still
    better read against the whole step than against nothing.
    """
    trace = rec.get("damage_trace") or []
    species = target.split("#")[0]
    hit = [r for r in trace if r.get("target_id") == species or r.get("target_id") == target]
    return hit or list(trace)


def _load(path: Path) -> list[dict[str, Any]]:
    recs: list[dict[str, Any]] = []
    with path.open(encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                recs.append(json.loads(line))
    return recs


def _summarize_sources(
    bucket: list[tuple[dict[str, Any], str, int]],
    rest: list[tuple[dict[str, Any], str, int]],
) -> list[tuple[float, int, int, str]]:
    """Per-source enrichment of `bucket` over `rest`, most enriched first.

    Returns ``(lift, in_bucket, in_rest, source)``. Enrichment, not raw count,
    because the sources that fire on every step (the played card, the enemy's
    intent) dominate any absolute tally and say nothing.
    """

    def tally(rows: list[tuple[dict[str, Any], str, int]]) -> collections.Counter[str]:
        c: collections.Counter[str] = collections.Counter()
        for rec, target, _ in rows:
            seen = {r.get("source") or "(unlabelled)" for r in _rows_for_target(rec, target)}
            c.update(seen)
        return c

    tb, tr = tally(bucket), tally(rest)
    nb, nr = max(1, len(bucket)), max(1, len(rest))
    out = [(tb[s] / nb - tr.get(s, 0) / nr, tb[s], tr.get(s, 0), s) for s in tb]
    out.sort(reverse=True)
    return out


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("log", type=Path, help="mirror divergence JSONL")
    ap.add_argument(
        "--delta",
        type=int,
        default=None,
        help="focus on HP desyncs of exactly this size (world - mirror); "
        "omit for the whole distribution",
    )
    ap.add_argument("--show", type=int, default=3, help="worked examples to print")
    args = ap.parse_args(argv)

    recs = _load(args.log)
    armed = sum(1 for r in recs if "damage_trace" in r)
    print(f"{len(recs)} records; {armed} carry a damage_trace")
    if not armed:
        print(
            "\nNo record carries a trace. Re-run the sweep with "
            "STS2_MIRROR_DAMAGE_TRACE=1 — an unarmed sweep and a damage-free "
            "step are indistinguishable from the outside.",
            file=sys.stderr,
        )
        return 2
    truncated = sum(1 for r in recs if r.get("damage_trace_dropped"))
    if truncated:
        print(f"WARNING: {truncated} record(s) have a TRUNCATED trace (buffer overflow)")

    events: list[tuple[dict[str, Any], str, int]] = []
    for rec in recs:
        for path, delta in _hp_desyncs(rec):
            events.append((rec, _target_of(path), delta))

    dist = collections.Counter(d for _, _, d in events)
    print(f"\nHP desyncs: {len(events)} across {len(dist)} distinct deltas")
    print("  (world - mirror; positive = the mirror over-dealt damage)")
    for delta, n in dist.most_common(12):
        print(f"    {delta:+5d}  {n}")

    if args.delta is None:
        return 0

    bucket = [e for e in events if e[2] == args.delta]
    rest = [e for e in events if e[2] != args.delta]
    print(f"\n=== delta {args.delta:+d}: {len(bucket)} desyncs ===")
    if not bucket:
        return 0

    print("\nsource enrichment (lift, in-bucket, elsewhere):")
    for lift, nb, nr, source in _summarize_sources(bucket, rest)[:15]:
        print(f"  {lift:+.3f}  {nb:4d} / {nr:4d}  {source}")

    # An exact-magnitude match is the strongest single signal available: a
    # source whose own hp_loss equals the divergence is a candidate cause, not
    # merely a co-occurrence.
    exact: collections.Counter[str] = collections.Counter()
    for rec, target, delta in bucket:
        for r in _rows_for_target(rec, target):
            if r.get("hp_loss") == abs(delta):
                exact[r.get("source") or "(unlabelled)"] += 1
    print(f"\nsources dealing exactly {abs(args.delta)} on the diverging step:")
    for source, n in exact.most_common(10):
        print(f"  {n:4d}  {source}")
    if not exact:
        print("  (none — the divergence is not one whole hit; look for a")
        print("   per-hit scaling error or a missing/extra target instead)")

    for rec, target, delta in bucket[: args.show]:
        rows = _rows_for_target(rec, target)
        print(f"\n--- {rec.get('label')} {target} delta {delta:+d} ---")
        print(f"    last_action: {rec.get('last_action')}")
        for r in rows:
            print(
                f"      [{r.get('seq')}] {r.get('source') or '(unlabelled)':28s}"
                f" {r.get('dealer')} -> {r.get('target_id')}"
                f"  dmg={r.get('final_damage')} blocked={r.get('blocked')}"
                f" hp {r.get('hp_before')}->{r.get('hp_after')}"
            )
        if any(not r.get("source") for r in rows):
            # Only cards, monster intents and orbs push a frame. An unlabelled
            # row is therefore a relic or a power hook, and the candidate set
            # is right here in the record — printing it turns "unattributed"
            # into a one-grep lookup (this is how `relic:Metronome`'s 30-to-all
            # was identified).
            print("    unlabelled row(s) => a relic or power hook. Candidates:")
            print(f"      relics: {_relic_ids(rec)}")
            print(f"      player powers: {_power_names(rec)}")
    return 0


def _relic_ids(rec: dict[str, Any]) -> list[str]:
    relics = ((rec.get("mirror_wire") or {}).get("player") or {}).get("relics") or []
    return [r if isinstance(r, str) else (r.get("id") or r.get("name") or "?") for r in relics]


def _power_names(rec: dict[str, Any]) -> list[str]:
    powers = ((rec.get("mirror_wire") or {}).get("player") or {}).get("powers") or []
    return [p if isinstance(p, str) else (p.get("name") or p.get("kind") or "?") for p in powers]


if __name__ == "__main__":
    raise SystemExit(main())
