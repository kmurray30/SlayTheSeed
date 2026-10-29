"""Lethal-fight autopsy — the gate that decides issue #301.

#301 (repricing remaining HP in the deployed Texel/victory combat eval) is
PARKED behind one question: are the excess real-DLL deaths **attrition-shaped**
(the agent arrived with an HP debt, so the fight was already near-lost) or
**catastrophic-blunder-shaped** (it arrived healthy and threw a winnable
fight)? Only the first implicates the price of HP in the eval.

Method: for every LOST run in a real-DLL sweep, take the killer fight's ENTRY
state, reconstruct it in the current engine (``adapter.translate ->
state_from_dict`` — the same path ``diag_combat_parity.py`` uses), and replay
it to completion N times under the DEPLOYED honest search. ``p_survive`` over
those replays is the verdict:

  p_survive <= 0.35  attrition  — engine also loses from here; HP debt is real
  p_survive >= 0.75  blunder    — engine wins comfortably; the LINE lost it
  otherwise          ambiguous  — reported as its own bucket, never folded in

Runs matching #300's signature (ends on an event floor at healthy HP with no
lethal combat logged) are EXCLUDED and counted, not classified.

Usage::

    uv run python python/diag_lethal_autopsy.py logs/headless \\
        --out-dir experiments/lethal-autopsy --replays 20
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

# --- fight classes -----------------------------------------------------------
FIGHT_BOSS = "boss"
FIGHT_ELITE = "elite"
FIGHT_MONSTER = "monster"

# --- exclusion reasons -------------------------------------------------------
EXCLUDE_SURVIVED = "run_not_lost"
EXCLUDE_NO_TERMINAL = "run_has_no_terminal_record"
EXCLUDE_EVENT_FLOOR = "event_floor_no_lethal_combat"
EXCLUDE_NO_COMBAT = "no_combat_before_death"
EXCLUDE_RECON_FAILED = "entry_state_reconstruction_failed"

_COMBAT = "combat_play"
_CARD_SELECT = "card_select"
_GAME_OVER = "game_over"

# Room types that mean "the player is inside a fight". A `card_select` in one
# of these is an INTRA-COMBAT prompt (Dual Wield, an exhaust picker, ...); a
# `card_select` in a RestSite / Event / Shop room is not.
_COMBAT_ROOMS = frozenset({"monster", "elite", "boss"})


def _room_key(state: dict[str, Any]) -> str:
    return str((state.get("context") or {}).get("room_type") or "").strip().lower()


def _belongs_to_fight(state: dict[str, Any], *, floor: Any, room: str) -> bool:
    """True when ``state`` is a decision taken INSIDE the fight ``(floor, room)``.

    A fight is not a contiguous run of ``combat_play``: the logs interleave
    ``card_select`` rows mid-fight (896 of them in the 2026-07-26 sweep, 559
    inside combat rooms). Walking back over ``combat_play`` alone therefore
    stops at the first such prompt and mistakes a mid-fight state for the
    entry state.

    ``card_select`` alone is not a combat marker either — it fires at rest
    sites, events and shops too — so membership is decided by the COMBAT ROOM
    CONTEXT (same ``total_floor``, same combat ``room_type``), not by the
    decision type. Compare ``shadow_replay._iter_decision_rows``' caller,
    which walks back over intra-shop ``card_select`` rows the same way.
    """
    if str(state.get("decision") or "") not in (_COMBAT, _CARD_SELECT):
        return False
    room_now = _room_key(state)
    if room_now not in _COMBAT_ROOMS:
        return False
    return room_now == room and (state.get("context") or {}).get("total_floor") == floor


def fight_class(state: dict[str, Any]) -> str:
    """Classify a combat decision's room as boss / elite / monster.

    ``room_type`` arrives in mixed case on the wire (``"Boss"`` and ``"boss"``
    both occur — see ``headless_eval.py``'s boss-sims branch), so compare
    case-insensitively. ``context.is_elite`` wins over a bare room_type.
    """
    ctx = state.get("context") or {}
    room = str(ctx.get("room_type") or "").strip().lower()
    if room == "boss":
        return FIGHT_BOSS
    if ctx.get("is_elite") or room == "elite":
        return FIGHT_ELITE
    return FIGHT_MONSTER


def seed_of_log(records: list[dict[str, Any]]) -> int | None:
    """Read the seed from the ``log_meta`` header record, if present."""
    for rec in records:
        meta = rec.get("meta")
        if isinstance(meta, dict) and "seed" in meta:
            return int(meta["seed"])
    return None


@dataclass
class KillerFight:
    """The fight a run died in, plus the state it was entered at."""

    seed: int | None
    character: str
    floor: int | None
    act: int
    fight_class: str
    entry_hp: int
    entry_max_hp: int
    roster: str
    entry_state: dict[str, Any] = field(repr=False)


def _roster_key(state: dict[str, Any]) -> str:
    names = [str(e.get("name") or "?") for e in (state.get("enemies") or [])]
    return "+".join(sorted(names))


def find_killer_fight(
    records: list[dict[str, Any]],
) -> tuple[KillerFight | None, str | None]:
    """Locate the fight a lost run died in and the state it entered it at.

    Returns ``(fight, None)`` on success or ``(None, reason)`` when the run
    is not autopsiable. The ENTRY state is the FIRST ``combat_play`` of the
    final fight — every decision of it, ``card_select`` prompts included (see
    ``_belongs_to_fight``) — because the whole point is to replay the fight
    from where the agent arrived, not from where it was already dying.
    """
    decisions = [r for r in records if (r.get("state") or {}).get("decision")]
    if not decisions or decisions[-1]["state"]["decision"] != _GAME_OVER:
        return None, EXCLUDE_NO_TERMINAL

    # The outcome lives in `game_over.victory`. Do NOT read the record's
    # player stub: it reports `hp: 0` for WON runs too (verified on all 58
    # game_over records of the 2026-07-26 sweep, including its 11 victories),
    # so an hp-based guard can never fire and a victory whose last decision
    # is a combat_play would be autopsied as a killer fight and would vote in
    # the gate.
    if decisions[-1]["state"].get("victory"):
        return None, EXCLUDE_SURVIVED

    # The decision immediately before death must be a fight, or the run did
    # not die IN one -> #300's event-floor channel (or some other non-combat
    # death). Not autopsiable either way.
    idx = len(decisions) - 2
    if idx < 0:
        return None, EXCLUDE_NO_COMBAT
    last_kind = str(decisions[idx]["state"]["decision"])
    if last_kind != _COMBAT:
        return None, (EXCLUDE_EVENT_FLOOR if "event" in last_kind.lower() else EXCLUDE_NO_COMBAT)

    # Walk back over every decision belonging to this fight (combat_play AND
    # its intra-combat card_selects) to the block's FIRST decision — where the
    # agent arrived, not where it was already dying.
    last = decisions[idx]["state"]
    floor, room = (last.get("context") or {}).get("total_floor"), _room_key(last)
    while idx >= 0 and _belongs_to_fight(decisions[idx]["state"], floor=floor, room=room):
        idx -= 1
    start = idx + 1

    # The entry state is fed to `reconstruct`, which needs the `combat_play`
    # wire shape — a `card_select` row carries no enemies/run_state — so take
    # the block's first combat_play, which is also the round-1 state.
    while decisions[start]["state"]["decision"] != _COMBAT:
        start += 1

    entry = decisions[start]["state"]
    player = entry.get("player") or {}
    ctx = entry.get("context") or {}
    return (
        KillerFight(
            seed=seed_of_log(records),
            character=str((entry.get("run_state") or {}).get("character") or "Ironclad"),
            floor=ctx.get("total_floor"),
            act=int(ctx.get("act") or 0),
            fight_class=fight_class(entry),
            entry_hp=int(player.get("hp") or 0),
            entry_max_hp=int(player.get("max_hp") or 0),
            roster=_roster_key(entry),
            entry_state=entry,
        ),
        None,
    )


def load_records(path: Path) -> list[dict[str, Any]]:
    """Read one run's JSONL log, skipping unparseable lines."""
    out: list[dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        try:
            out.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    return out


# --- pre-registered classification -------------------------------------------
# Fixed BEFORE any replay was run (spec 2026-07-26). Do not tune post-hoc:
# the whole value of this autopsy is that its verdict was not chosen after
# seeing the data.
ATTRITION_MAX_P = 0.35
BLUNDER_MIN_P = 0.75
REPLAYS_PER_FIGHT = 20

BUCKET_ATTRITION = "attrition"
BUCKET_BLUNDER = "blunder"
BUCKET_AMBIGUOUS = "ambiguous"
BUCKET_UNSCORED = "unscored"


def p_survive(replays: list[dict[str, Any]]) -> float | None:
    """Fraction of replays the engine WON, from the identical entry state.

    Timed-out replays are excluded from the denominator rather than counted
    as losses: a search that blew its action budget produced no verdict, and
    scoring it as a death would fake attrition evidence.
    """
    scored = [r for r in replays if not r.get("timed_out")]
    if not scored:
        return None
    return sum(1 for r in scored if r.get("player_won")) / len(scored)


def classify(p: float | None) -> str:
    """Bucket a fight by its replay survival rate (see module docstring)."""
    if p is None:
        return BUCKET_UNSCORED
    if p <= ATTRITION_MAX_P:
        return BUCKET_ATTRITION
    if p >= BLUNDER_MIN_P:
        return BUCKET_BLUNDER
    return BUCKET_AMBIGUOUS


# --- deployed honest search (see spec; MUST match mcts_config_for) -----------
DEPLOYED_HIDDEN_INFO = "beliefdraw"
DEPLOYED_C_PUCT = 2.5
DEPLOYED_DRAW_WIDENING_C = 2.0
DEPLOYED_ROLLOUT_DEPTH = 200
SIMS_BOSS = 4000
SIMS_REGULAR = 1000


def sims_for(kind: str) -> int:
    """Sim budget by fight class, matching headless_eval's boss escalation."""
    return SIMS_BOSS if kind == FIGHT_BOSS else SIMS_REGULAR


def reconstruct(fight: KillerFight):
    """Rebuild the killer fight's entry state in the CURRENT engine.

    Same path as ``diag_combat_parity.py``: ``adapter.translate ->
    state_from_dict``. Imported lazily so the pure logic above stays
    importable (and unit-testable) without the compiled wheel.
    """
    import sts2_rs
    from realgame_adapter import character_display_name
    from sts2_hint import adapter

    char = character_display_name(fight.character) or "Ironclad"
    wire = adapter.translate(fight.entry_state, character=char, combat_id=0, seed=0, ascension=0)
    return sts2_rs.state_from_dict(wire)


def replay_fight(
    fight: KillerFight,
    replays: int = REPLAYS_PER_FIGHT,
    *,
    sims: int | None = None,
) -> list[dict[str, Any]]:
    """Play the entry state to completion ``replays`` times, deployed config.

    Only the seed varies across replays. ``hidden_info``/``draw_widening_c``/
    defaults: a clairvoyant replay would overstate how easily the engine
    wins and fake a 'blunder' verdict, and a stronger (prior-on) replay
    search than the sweep that produced the death would do the same —
    """
    import sts2_rs

    budget = sims if sims is not None else sims_for(fight.fight_class)
    out: list[dict[str, Any]] = []
    for i in range(replays):
        state = reconstruct(fight)  # fresh state per replay — mcts mutates it
        res = sts2_rs.mcts_play_to_terminal(
            state,
            sims=budget,
            max_rollout_depth=DEPLOYED_ROLLOUT_DEPTH,
            seed=i,
            c_puct=DEPLOYED_C_PUCT,
            hidden_info=DEPLOYED_HIDDEN_INFO,
            draw_widening_c=DEPLOYED_DRAW_WIDENING_C,
        )
        out.append(dict(res))
    return out


def autopsy_run(path: Path, replays: int = REPLAYS_PER_FIGHT) -> dict[str, Any]:
    """Autopsy one run log. Always returns a record — excluded runs included."""
    records = load_records(path)
    fight, reason = find_killer_fight(records)
    if fight is None:
        return {"log": path.name, "seed": seed_of_log(records), "excluded": reason}

    base = {
        "log": path.name,
        "seed": fight.seed,
        "character": fight.character,
        "floor": fight.floor,
        "act": fight.act,
        "class": fight.fight_class,
        "entry_hp": fight.entry_hp,
        "entry_max_hp": fight.entry_max_hp,
        "entry_hp_frac": (fight.entry_hp / fight.entry_max_hp) if fight.entry_max_hp else None,
        "roster": fight.roster,
    }
    try:
        results = replay_fight(fight, replays)
    except Exception as e:  # reconstruction or engine failure — name it, don't hide it
        return {**base, "excluded": EXCLUDE_RECON_FAILED, "error": f"{type(e).__name__}: {e}"}

    p = p_survive(results)
    won = [r for r in results if r.get("player_won")]
    parity_err = parity_error_for_fight(records, fight)
    bucket = flag_divergence(classify(p), parity_err)
    return {
        **base,
        "replays": results,
        "n_replays": len(results),
        "n_timed_out": sum(1 for r in results if r.get("timed_out")),
        "p_survive": p,
        "bucket": bucket,
        "parity_signed_hp_per_turn": parity_err,
        "median_final_hp_when_won": (
            sorted(r["final_hp"] for r in won)[len(won) // 2] if won else None
        ),
    }


# --- divergence guard --------------------------------------------------------
# Signed one-step HP error = predicted_hp - actual_hp, per enemy turn, over the
# killer fight only. >0 means the engine is OPTIMISTIC (thinks the player kept
# more HP than the DLL gave it). The 2026-07-19 probe measured act-level means
# of +0.10 / +0.01 / +0.59; a per-fight mean above 1.0 HP/turn is well outside
# that and means this fight specifically is modeled soft -- so the engine
# "comfortably winning" it may be an artifact, not a blunder by the DLL run.
OPTIMISM_HP_PER_TURN_MAX = 1.0
BUCKET_BLUNDER_SUSPECT = "blunder-suspect-divergence"


def parity_error_for_fight(records: list[dict[str, Any]], fight: KillerFight) -> float | None:
    """Mean signed one-step HP error over the killer fight's end_turns.

    Rebuilds each ``end_turn`` state of THIS fight in the engine, applies the
    same ``end_turn``, and compares predicted post-enemy-turn player HP with
    what the DLL actually produced. Mirrors ``diag_combat_parity.py``,
    restricted to one fight. ``None`` when no transition could be scored.
    """
    import sts2_rs
    from realgame_adapter import character_display_name
    from sts2_hint import adapter

    # The floor IS the fight filter below. Without it every end_turn of the
    # whole run would be pooled into the mean, so an unrelated fight's parity
    # error could downgrade this fight's blunder verdict (or hide one).
    if fight.floor is None:
        return None

    char = character_display_name(fight.character) or "Ironclad"
    decisions = [r for r in records if (r.get("state") or {}).get("decision")]
    errors: list[float] = []
    for i in range(len(decisions) - 1):
        s0 = decisions[i]["state"]
        a0 = decisions[i].get("action") or {}
        s1 = decisions[i + 1]["state"]
        if s0.get("decision") != _COMBAT or a0.get("action") != "end_turn":
            continue
        if (s0.get("context") or {}).get("total_floor") != fight.floor:
            continue
        if s1.get("decision") != _COMBAT:
            continue
        try:
            wire = adapter.translate(s0, character=char, combat_id=0, seed=0, ascension=0)
            cs = sts2_rs.state_from_dict(wire)
            cs.apply({"action": "end_turn"})
            pred = (sts2_rs.state_to_dict(cs).get("player") or {}).get("hp")
        except Exception:
            continue
        actual = (s1.get("player") or {}).get("hp")
        if pred is None or actual is None:
            continue
        errors.append(float(pred - actual))
    if not errors:
        return None
    return sum(errors) / len(errors)


def flag_divergence(bucket: str, signed_hp_per_turn: float | None) -> str:
    """Downgrade a 'blunder' verdict that rests on an optimistic engine.

    Only blunders are downgraded: an optimistic engine makes fights look
    WINNABLE, so it can manufacture a false blunder but cannot manufacture a
    false attrition (attrition means the engine lost too, despite optimism --
    if anything that is stronger evidence).
    """
    if bucket != BUCKET_BLUNDER or signed_hp_per_turn is None:
        return bucket
    if signed_hp_per_turn > OPTIMISM_HP_PER_TURN_MAX:
        return BUCKET_BLUNDER_SUSPECT
    return bucket


# --- verdict + report --------------------------------------------------------
NON_BOSS = (FIGHT_ELITE, FIGHT_MONSTER)


def gate_verdict(records: list[dict[str, Any]]) -> tuple[bool, str]:
    """Decide #301's gate: unpark iff NON-BOSS deaths are majority attrition.

    Boss deaths are excluded from the count on purpose — #301 already has
    numbers for those, and the park was conditioned specifically on the
    non-boss lethal fights the 2026-07-24 ledger re-audit surfaced. Excluded
    runs (no killer fight) never vote.
    """
    scored = [r for r in records if not r.get("excluded") and r.get("class") in NON_BOSS]
    if not scored:
        return False, "no non-boss killer fights were scored — the gate cannot be decided"
    n_attr = sum(1 for r in scored if r.get("bucket") == BUCKET_ATTRITION)
    others = len(scored) - n_attr
    unpark = n_attr > others
    verdict = "UNPARK #301" if unpark else "KEEP #301 PARKED"
    return unpark, (
        f"{verdict}: {n_attr}/{len(scored)} non-boss killer fights are "
        f"{BUCKET_ATTRITION}-shaped vs {others} not"
    )


_BUCKET_COLUMNS = (
    BUCKET_ATTRITION,
    BUCKET_AMBIGUOUS,
    BUCKET_BLUNDER,
    BUCKET_BLUNDER_SUSPECT,
    BUCKET_UNSCORED,
)
# `unscored` is reached only via `p_survive() -> None`, i.e. every replay of
# that fight timed out. Label the column by what it counts (and by what the
# published writeup calls it) rather than by the bucket's internal name.
_BUCKET_HEADERS = {BUCKET_UNSCORED: "timed out"}


def _bucket_counts(records: list[dict[str, Any]], classes: tuple[str, ...]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for r in records:
        if r.get("excluded") or r.get("class") not in classes:
            continue
        counts[r.get("bucket", BUCKET_UNSCORED)] = (
            counts.get(r.get("bucket", BUCKET_UNSCORED), 0) + 1
        )
    return counts


def render_report(records: list[dict[str, Any]]) -> str:
    """Render the autopsy as markdown: verdict first, then evidence."""
    unpark, why = gate_verdict(records)
    lines: list[str] = []
    lines.append("# Non-boss lethal-fight autopsy — results\n")
    lines.append(f"**Gate verdict:** {why}.\n")
    lines.append(
        "The gate: #301 (HP repricing in the Texel/victory combat eval) unparks "
        "iff non-boss killer fights are majority attrition-shaped. Thresholds "
        f"were pre-registered: p_survive <= {ATTRITION_MAX_P} attrition, "
        f">= {BLUNDER_MIN_P} blunder, N = {REPLAYS_PER_FIGHT} replays/fight, "
        "replayed under the deployed honest search "
        f"(hidden_info={DEPLOYED_HIDDEN_INFO}, c_puct={DEPLOYED_C_PUCT}, "
        f"draw_widening_c={DEPLOYED_DRAW_WIDENING_C}, depth={DEPLOYED_ROLLOUT_DEPTH})."
        "\n"
    )

    lines.append("## Buckets\n")
    lines.append(
        "| scope | " + " | ".join(_BUCKET_HEADERS.get(b, b) for b in _BUCKET_COLUMNS) + " |"
    )
    lines.append("|---|" + "---|" * len(_BUCKET_COLUMNS))
    for label, classes in (("non-boss (the gate)", NON_BOSS), ("boss (separate)", (FIGHT_BOSS,))):
        c = _bucket_counts(records, classes)
        lines.append(f"| {label} | " + " | ".join(str(c.get(b, 0)) for b in _BUCKET_COLUMNS) + " |")

    lines.append("\n## Per-fight\n")
    lines.append("| seed | act | floor | class | entry HP | p_survive | parity HP/turn | bucket |")
    lines.append("|---|---|---|---|---|---|---|---|")
    for r in records:
        if r.get("excluded"):
            continue
        hp = f"{r.get('entry_hp')}/{r.get('entry_max_hp')}"
        p = r.get("p_survive")
        pe = r.get("parity_signed_hp_per_turn")
        lines.append(
            f"| {r.get('seed')} | {r.get('act')} | {r.get('floor')} | {r.get('class')} | "
            f"{hp} | {p if p is None else f'{p:.2f}'} | "
            f"{pe if pe is None else f'{pe:+.2f}'} | {r.get('bucket')} |"
        )

    excluded = [r for r in records if r.get("excluded")]
    lines.append(f"\n## Excluded runs ({len(excluded)})\n")
    if not excluded:
        lines.append("None — every lost run had an autopsiable killer fight.\n")
    else:
        lines.append("| seed | reason |")
        lines.append("|---|---|")
        for r in excluded:
            lines.append(f"| {r.get('seed')} | {r.get('excluded')} |")
        lines.append(
            f"\n`{EXCLUDE_EVENT_FLOOR}` is issue #300's unattributed loss channel: "
            "the run ends on an event floor at healthy HP with no lethal combat "
            "logged. Those runs are not killer fights and are not classified.\n"
        )
    return "\n".join(lines) + "\n"


def main(argv: list[str] | None = None) -> int:
    import argparse

    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("log_dir", type=Path, help="dir of headless per-decision JSONL run logs")
    ap.add_argument("--out-dir", type=Path, default=Path("experiments/lethal-autopsy"))
    ap.add_argument("--replays", type=int, default=REPLAYS_PER_FIGHT)
    ap.add_argument("--since", default=None, help="only logs whose name sorts >= this prefix")
    args = ap.parse_args(argv)

    args.out_dir.mkdir(parents=True, exist_ok=True)
    out_path = args.out_dir / "fights.jsonl"

    logs = sorted(p for p in args.log_dir.glob("*.jsonl"))
    if args.since:
        logs = [p for p in logs if p.name >= args.since]

    # Truncate, then write one record per log and flush after each: a long
    # autopsy must be inspectable mid-flight so a stuck fight is visible
    # without waiting for the end (repo experiment rule). Each run rewrites
    # the file from scratch — it does not append to a previous run's output.
    n = 0
    with out_path.open("w", encoding="utf-8") as fh:
        for path in logs:
            rec = autopsy_run(path, args.replays)
            fh.write(json.dumps(rec, separators=(",", ":")) + "\n")
            fh.flush()
            n += 1
            print(
                f"[{n}/{len(logs)}] seed={rec.get('seed')} "
                f"{rec.get('class') or rec.get('excluded')} "
                f"p_survive={rec.get('p_survive')} -> {rec.get('bucket', '-')}",
                flush=True,
            )

    print(f"\nwrote {n} records to {out_path}")

    all_recs = [
        json.loads(x) for x in out_path.read_text(encoding="utf-8").splitlines() if x.strip()
    ]
    report_path = args.out_dir / "report.md"
    report_path.write_text(render_report(all_recs), encoding="utf-8")
    unpark, why = gate_verdict(all_recs)
    print(f"\n{why}")
    print(f"report: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
