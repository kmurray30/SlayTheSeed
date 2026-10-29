"""Offline shadow-engine divergence census (Phase 0 of the seed-locked
shadow-engine plan, ``docs/plans/2026-07-18-seed-locked-shadow-engine.md``).

For a recorded headless-eval run (``logs/headless/*.jsonl``, rows
``{step, ts, state, action}``), reconstruct the run in a shadow Rust
``RunManager`` started from the SAME string seed, replay the logged actions
into it in lockstep, and diff the shadow's observable state against the
logged DLL wire state before every step. Reports steps-to-first-divergence
and a machine-readable divergence record per run.

The shadow rides the workbench csharp-emulation backend
(``workbench.backends.rust_game.RustGame(target_convention="csharp")``),
which already speaks the DLL action dialect (``play_card {card_index,
target_index}`` over alive-enemy indices, ``choose_option``, ...) and
mimics DLL-side auto-collection of combat relic/gold rewards — do not
hand-roll a second mapping.

Usage::

    # single run
    python python/shadow_replay.py --jsonl logs/headless/20260718T041559Z.jsonl --seed 1059

    # whole sweep (seed->jsonl matched via the sweep driver's START/DONE log)
    python python/shadow_replay.py --sweep-log path/to/full60_resweep_results.log

Divergence records land in ``logs/shadow_divergences/`` as jsonl unless
``--out`` overrides.
"""

from __future__ import annotations

import argparse
import json
import logging
import re
import sys
from collections import Counter
from collections.abc import Iterator
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from sts2_hint import display_maps
from workbench.backends.rust_game import RustGame

logger = logging.getLogger("shadow_replay")


# ---------------------------------------------------------------------------
# Canonicalization (dialect noise must not read as divergence — plan "Risks").
# ---------------------------------------------------------------------------


# Same item, different registry name across the two worlds (engine name ->
# DLL name, in stripped-canonical form). Grown from census findings.
_ID_ALIASES = {
    "CLARITY": "CLARITYEXTRACT",
}


def _canon_id(raw: Any) -> str:
    """Any card/relic/potion id dialect -> SCREAMING_SNAKE stem.

    Handles the DLL's ``CARD.STRIKE_IRONCLAD`` registry prefix, the
    emulation's loc-key leaks (``STRIKE_IRONCLAD.title``) and PascalCase
    engine ids — all collapse to ``STRIKE_IRONCLAD``.
    """
    s = str(raw or "")
    if s.endswith(".title"):
        s = s[: -len(".title")]
    # Display-name leaks ("Blood Potion"), loc stems ("BLOOD_POTION") and
    # apostrophe splits (DLL "SOLDIER_S_STEW" vs engine "SoldiersStew") must
    # all collapse to one form: strip every separator outright.
    canon = re.sub(r"[^A-Z0-9+]+", "", display_maps.loc_id(s).upper())
    return _ID_ALIASES.get(canon, canon)


def _canon_power_name(p: dict[str, Any]) -> str:
    raw = str(p.get("id") or p.get("name") or "")
    s = display_maps.loc_id(raw)
    return s[:-6] if s.endswith("_POWER") else s


def _canon_powers(powers: Any) -> dict[str, Any]:
    out: dict[str, Any] = {}
    for p in powers or []:
        if isinstance(p, dict):
            out[_canon_power_name(p)] = p.get("amount")
    return out


def _canon_move_state(ms: Any) -> str | None:
    if ms is None:
        return None
    # DLL emits the C# enum name (``BUTT_MOVE``), the engine its own id
    # (``Butt``) — collapse punctuation and a trailing ``MOVE`` marker.
    s = re.sub(r"[^A-Z0-9]", "", str(ms).upper())
    return s[:-4] if s.endswith("MOVE") and len(s) > 4 else s


def _canon_intents(intents: Any) -> list[tuple[Any, Any]]:
    """Attack intents as (type, hits) — no damage magnitude.

    Two dialect asymmetries make finer comparison noise, not signal:
    the DLL lists every intent icon of a move while the engine serializes
    only the primary intent, folding secondary components into the move's
    ``effect_fn`` (Nibbit SLICE, bead sts2-rs-5i8yj); and the DLL's
    ``damage`` is ``AttackIntent.GetSingleDamage`` — the full
    ``Hook.ModifyDamage`` pipeline (Strength/Weak/Vulnerable/… baked in) —
    while the engine emits the unmodified base amount. A damage-PORT error
    still fires one row later as a ``player.hp`` diff after the enemy turn.
    """
    return [
        (i.get("type"), i.get("hits") or 1)
        for i in intents or []
        if isinstance(i, dict)
        and i.get("damage") is not None
        and "Attack" in str(i.get("type", ""))
    ]


def _canon_enemy(e: dict[str, Any]) -> dict[str, Any]:
    return {
        "hp": e.get("hp"),
        "max_hp": e.get("max_hp"),
        "block": e.get("block", 0) or 0,
        "powers": _canon_powers(e.get("powers")),
        "intents": _canon_intents(e.get("intents")),
        "move_state": _canon_move_state(e.get("move_state")),
    }


# X-cost cards (C# ``HasEnergyCostX``, decomp sweep 2026-07-18): the DLL
# serializes their cost as ``EnergyCost.GetResolved()`` (a live X value)
# while the engine emits 0 — display dialect, so their cost is excluded
# from the hand compare (the id still is compared).
_X_COST_CARDS = frozenset(
    {
        "CASCADE",
        "DIRGE",
        "ERADICATE",
        "HEAVENLYDRILL",
        "MALAISE",
        "MULTICAST",
        "SKEWER",
        "TEMPEST",
        "VOLLEY",
        "WHIRLWIND",
    }
)


def _canon_hand(hand: Any) -> list[tuple[str, Any]]:
    out = []
    for c in hand or []:
        if not isinstance(c, dict):
            continue
        cid = _canon_id(c.get("id"))
        out.append((cid, None if cid in _X_COST_CARDS else c.get("cost")))
    return out


def _owned_ids(entries: Any) -> list[str]:
    out = []
    for r in entries or []:
        if isinstance(r, dict):
            out.append(_canon_id(r.get("id") or r.get("name")))
        elif r:
            out.append(_canon_id(r))
    return sorted(out)


# ---------------------------------------------------------------------------
# State diff
# ---------------------------------------------------------------------------


def diff_states(
    wire: dict[str, Any], shadow: dict[str, Any], *, shop_fresh: bool = True
) -> list[dict[str, Any]]:
    """Field-level differences between a logged DLL wire decision state and
    the shadow's serialized state, on the overlapping canonical surface.

    Returns a list of ``{"field", "wire", "shadow"}`` records (empty = in
    sync). A ``decision``-type mismatch short-circuits: every downstream
    field would cascade-diff, drowning the actual signal.
    """
    diffs: list[dict[str, Any]] = []

    def add(field: str, w: Any, s: Any) -> None:
        diffs.append({"field": field, "wire": w, "shadow": s})

    wd, sd = wire.get("decision"), shadow.get("decision")
    if wd != sd:
        add("decision", wd, sd)
        return diffs

    ctx = wire.get("context") or {}
    w_floor = ctx.get("total_floor")
    s_floor = shadow.get("floor")
    w_act = ctx.get("act")
    s_act = shadow.get("act")
    # Act-start boundary row (the Ancient / act-start event): the DLL applies
    # the act transition (act++, between-acts heal, floor rebase) BEFORE
    # presenting the event, the engine applies it on RESOLVING it — same
    # screen, same options, different bookkeeping timing (seed-1005 probe:
    # shadow hp 21/act 1 becomes hp 82/act 2 the moment the option resolves,
    # matching the wire exactly). Suppress the bookkeeping fields for this
    # one row.
    boundary = (
        wd == "event_choice" and w_act is not None and s_act is not None and w_act == s_act + 1
    )
    if not boundary:
        # DLL ``total_floor`` counts each act's start room (Neow, Ancients)
        # as a floor, the engine's ``total_floor()`` doesn't — so the offset
        # is the act number (verified: +1 across all of act 1, +2 across
        # act 2 on a 251-row blind replay).
        if w_floor is not None and s_floor is not None and w_floor != s_floor + (w_act or 1):
            add("floor", w_floor, s_floor)
        if w_act is not None and s_act is not None and w_act != s_act:
            add("act", w_act, s_act)

    wp = wire.get("player") or {}
    sp = shadow.get("player") or {}
    for k in ("hp", "max_hp", "gold"):
        if boundary and k in ("hp", "max_hp"):
            continue
        if k in wp and k in sp and wp[k] != sp[k]:
            add(f"player.{k}", wp[k], sp[k])
    w_deck = wp.get("deck")
    s_deck = sp.get("deck")
    if w_deck and s_deck:
        w_ids = sorted(_canon_id(c.get("id")) for c in w_deck if isinstance(c, dict))
        s_ids = sorted(_canon_id(c.get("id")) for c in s_deck if isinstance(c, dict))
        if w_ids != s_ids:
            add("player.deck", w_ids, s_ids)
    if wp.get("relics") is not None and sp.get("relics") is not None:
        w_r, s_r = _owned_ids(wp.get("relics")), _owned_ids(sp.get("relics"))
        if w_r != s_r:
            add("player.relics", w_r, s_r)
    if wp.get("potions") is not None and sp.get("potions") is not None:
        # ORDERED: ``use_potion {potion_index}`` addresses the belt by slot,
        # so slot order is a cross-world contract, not display (seed-1013
        # v1 census: same slot index drank Dexterity in one world, Energy in
        # the other — sorted compare hid the order drift until it bit).
        def _belt(entries: Any) -> list[str]:
            return [
                _canon_id(p.get("id") or p.get("name")) if isinstance(p, dict) else _canon_id(p)
                for p in entries or []
                if p
            ]

        w_p, s_p = _belt(wp.get("potions")), _belt(sp.get("potions"))
        if w_p != s_p:
            add("player.potions", w_p, s_p)

    if wd == "combat_play":
        for k in ("round", "energy"):
            if wire.get(k) is not None and shadow.get(k) is not None and wire[k] != shadow[k]:
                add(k, wire[k], shadow[k])
        if wp.get("block", 0) != sp.get("block", 0):
            add("player.block", wp.get("block", 0), sp.get("block", 0))
        w_pp = _canon_powers(wire.get("player_powers"))
        s_pp = _canon_powers(shadow.get("player_powers"))
        if w_pp != s_pp:
            add("player_powers", w_pp, s_pp)
        w_hand = _canon_hand(wire.get("hand"))
        s_hand = _canon_hand(shadow.get("hand"))
        if w_hand != s_hand:
            add("hand", w_hand, s_hand)
        for pile in ("draw_pile_count", "discard_pile_count", "exhaust_pile_count"):
            if wire.get(pile) != shadow.get(pile):
                add(pile, wire.get(pile), shadow.get(pile))
        w_en = [_canon_enemy(e) for e in wire.get("enemies") or []]
        s_en = [_canon_enemy(e) for e in shadow.get("enemies") or []]
        if len(w_en) != len(s_en):
            add("enemies.count", len(w_en), len(s_en))
        else:
            for i, (we, se) in enumerate(zip(w_en, s_en, strict=True)):
                for k, wv in we.items():
                    if wv != se[k]:
                        # move_state ids are engine-internal names, not a
                        # shared vocabulary (the port renamed e.g. the slime
                        # TACKLE_MOVE to BUTT with identical behavior) — a
                        # mismatch is a rename candidate, not proof of AI
                        # drift; real drift still fires via intents/hp/block.
                        rec = {"field": f"enemies[{i}].{k}", "wire": wv, "shadow": se[k]}
                        if k == "move_state":
                            rec["soft"] = True
                        diffs.append(rec)
    elif wd == "map_select":
        # Cols only: the engine moves by col (unique within a row) and its
        # emulation's row/type are best-effort display sugar that can be
        # None (``_fill_map`` child-lookup fallback). Real map-gen drift
        # still fires here — a different node set has different cols.
        w_ch = sorted(c.get("col") for c in wire.get("choices") or [])
        s_ch = sorted(c.get("col") for c in shadow.get("choices") or [])
        if w_ch != s_ch:
            add("choices", w_ch, s_ch)
    elif wd == "card_reward":
        w_cards = [_canon_id(c.get("id")) for c in wire.get("cards") or []]
        s_cards = [_canon_id(c.get("id")) for c in shadow.get("cards") or []]
        if w_cards != s_cards:
            add("cards", w_cards, s_cards)
    elif wd in ("event_choice", "rest_site", "treasure"):
        w_n = len(wire.get("options") or [])
        s_n = len(shadow.get("options") or [])
        if w_n != s_n:
            add("options.count", w_n, s_n)
    elif wd == "card_select":
        w_n = len(wire.get("cards") or [])
        s_n = len(shadow.get("cards") or [])
        if w_n != s_n:
            add("cards.count", w_n, s_n)
    elif wd == "shop" and shop_fresh:
        # First row of a shop visit only: fresh inventory is the actual
        # loot-stream signal. Revisit rows are bookkeeping dialect — the DLL
        # blanks a bought slot's id, while the emulation's ``is_stocked``
        # means in-stock AND AFFORDABLE (``_fill_shop``), so neither side's
        # flags line up after a purchase. ``is_stocked`` is ignored here.
        def _stock(items: Any) -> list[str]:
            return sorted(
                _canon_id(i.get("id"))
                for i in items or []
                if isinstance(i, dict) and _canon_id(i.get("id"))
            )

        for key in ("cards", "relics", "potions"):
            w_s, s_s = _stock(wire.get(key)), _stock(shadow.get(key))
            if w_s != s_s:
                add(f"shop.{key}", w_s, s_s)

    return diffs


# ---------------------------------------------------------------------------
# Replay
# ---------------------------------------------------------------------------


def _state_key(state: dict[str, Any]) -> str:
    return json.dumps(state, sort_keys=True, default=str)


def _has_state(row: dict[str, Any]) -> bool:
    """Check if a row is a decision row (carries a 'state' key).

    Meta/header records (e.g., seed/character/ascension) lack 'state'.
    """
    return "state" in row


def _iter_decision_rows(
    rows: list[dict[str, Any]],
) -> Iterator[tuple[int, dict[str, Any]]]:
    """Yield (original_index, row) for decision rows only.

    Meta/header records (those without 'state' key) are skipped. This lets
    the test import and exercise the same skip logic the reader uses, so
    removing/breaking the guard fails the test.
    """
    for i, row in enumerate(rows):
        if _has_state(row):
            yield i, row


def replay_run(
    jsonl_path: Path,
    seed: int,
    *,
    character: str = "Ironclad",
    ascension: int = 0,
    verbose: bool = False,
) -> dict[str, Any]:
    """Lockstep-replay one recorded run into a shadow engine.

    Returns a summary dict: ``status`` is one of ``completed`` (terminal row
    reached in sync), ``diverged`` (state diff fired), ``apply_rejected``
    (the shadow engine refused a logged action), ``log_exhausted`` (file
    ended without a terminal row).
    """
    rows = [json.loads(line) for line in jsonl_path.read_text(encoding="utf-8").splitlines()]
    backend = RustGame(target_convention="csharp")
    state = backend.start(character=character, seed=str(seed), ascension=ascension)

    result: dict[str, Any] = {
        "seed": seed,
        "jsonl": str(jsonl_path),
        "total_rows": len(rows),
        "steps_replayed": 0,
        "echo_skips": [],
        "status": "log_exhausted",
        "first_divergence": None,
        "soft_diffs": [],
    }

    for i, row in _iter_decision_rows(rows):
        wire = row["state"]
        if row.get("action") is None:
            # Terminal (game_over / run_complete): reached in lockstep.
            result["status"] = "completed"
            result["terminal_decision"] = wire.get("decision") or wire.get("type")
            break

        # A shop row is "fresh" (inventory comparable) only on the FIRST row
        # of the visit. The visit survives intra-shop card_selects (the
        # removal service opens one), so walk back over card_select rows —
        # otherwise the post-removal shop row is re-compared as fresh and
        # the bought-slot dialect (DLL blanks, emulation keeps) false-fires
        # (v7 seeds 1042/1045/1047/1055).
        j = i - 1
        while j >= 0 and _has_state(rows[j]) and rows[j]["state"].get("decision") == "card_select":
            j -= 1
        prev_dec = rows[j]["state"].get("decision") if j >= 0 and _has_state(rows[j]) else None
        shop_fresh = not (wire.get("decision") == "shop" and prev_dec == "shop")
        diffs = diff_states(wire, state, shop_fresh=shop_fresh)
        soft = [d for d in diffs if d.get("soft")]
        diffs = [d for d in diffs if not d.get("soft")]
        if soft and len(result["soft_diffs"]) < 50:
            result["soft_diffs"].append({"step": row.get("step"), "diffs": soft})
        if diffs:
            result["status"] = "diverged"
            result["first_divergence"] = {
                "step": row.get("step"),
                "floor": (wire.get("context") or {}).get("total_floor"),
                "act": (wire.get("context") or {}).get("act"),
                "decision": wire.get("decision"),
                "action": row.get("action", {}).get("action"),
                "diffs": diffs[:20],
            }
            break

        # DLL-rejected echo: the harness logs the command BEFORE sending; a
        # rejected send leaves the DLL state unchanged, so the next row
        # repeats this state verbatim. The DLL world never executed the
        # action — the shadow must skip it too.
        if (
            i + 1 < len(rows)
            and _has_state(rows[i + 1])
            and rows[i + 1].get("action") is not None
            and _state_key(rows[i + 1]["state"]) == _state_key(wire)
        ):
            result["echo_skips"].append(row.get("step"))
            result["steps_replayed"] = i + 1
            continue

        action = row["action"]
        verb = action.get("action")
        args = dict(action.get("args") or {})
        # "Choose up to N" selects (min_select 0) answered with NOTHING: the
        # DLL accepts ``select_cards {indices: ""}`` as a confirm-empty; the
        # emulation models the same move as ``skip_select``.
        if verb == "select_cards" and not str(args.get("indices", "")).strip():
            verb, args = "skip_select", {}
        state = backend.act(verb, **args)
        result["steps_replayed"] = i + 1
        if state.get("type") == "error":
            result["status"] = "apply_rejected"
            result["first_divergence"] = {
                "step": row.get("step"),
                "floor": (wire.get("context") or {}).get("total_floor"),
                "act": (wire.get("context") or {}).get("act"),
                "decision": wire.get("decision"),
                "action": verb,
                "diffs": [{"field": "apply", "wire": action, "shadow": state.get("message")}],
            }
            break
        if verbose:
            logger.info(
                "step %s ok: %s %s -> shadow %s",
                row.get("step"),
                verb,
                json.dumps(args, sort_keys=True),
                state.get("decision"),
            )

    return result


# ---------------------------------------------------------------------------
# Sweep-log seed->jsonl matching
# ---------------------------------------------------------------------------

_SWEEP_LINE = re.compile(
    r"\[(?P<ts>[\dTZ:\-]+)\] seed (?P<seed>\d+) (?P<kind>START|DONE)",
)


def map_seeds_to_jsonl(sweep_log: Path, logs_dir: Path) -> dict[int, Path]:
    """Match each seed to its run's jsonl via the sweep driver's timestamps.

    Jsonl filenames are stamped at run START (``JsonlLogger.__init__``), so
    a seed's file is the one stamped inside its ``[START ts, DONE ts]``
    window. Skips (with a warning) seeds whose window matches zero or
    multiple files.
    """
    windows: dict[int, list[datetime]] = {}
    for line in sweep_log.read_text(encoding="utf-8").splitlines():
        m = _SWEEP_LINE.search(line)
        if not m:
            continue
        ts = datetime.strptime(m["ts"], "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=UTC)
        windows.setdefault(int(m["seed"]), [None, None])[0 if m["kind"] == "START" else 1] = ts

    files: list[tuple[datetime, Path]] = []
    for p in sorted(logs_dir.glob("*.jsonl")):
        # JsonlLogger stems are `{ts}_{pid}_{n}` (ts has no underscore), so the
        # window timestamp is the prefix before the first `_`. Bare `{ts}`
        # stems (older logs) split to themselves.
        try:
            stamp = datetime.strptime(p.stem.split("_", 1)[0], "%Y%m%dT%H%M%SZ").replace(tzinfo=UTC)
        except ValueError:
            continue
        files.append((stamp, p))

    out: dict[int, Path] = {}
    for seed, (start, done) in sorted(windows.items()):
        if start is None or done is None:
            logger.warning("seed %d: incomplete START/DONE window in sweep log", seed)
            continue
        hits = [p for stamp, p in files if start <= stamp <= done]
        if len(hits) != 1:
            logger.warning("seed %d: %d jsonl files in window %s..%s", seed, len(hits), start, done)
            if not hits:
                continue
        out[seed] = hits[0]
    return out


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jsonl", type=Path, help="single recorded run to replay")
    parser.add_argument("--seed", type=int, help="seed of --jsonl's run")
    parser.add_argument(
        "--sweep-log", type=Path, help="sweep driver log with seed START/DONE lines"
    )
    parser.add_argument("--logs-dir", type=Path, default=Path("logs/headless"))
    parser.add_argument("--character", default="Ironclad")
    parser.add_argument("--ascension", type=int, default=0)
    parser.add_argument("--out", type=Path, help="census jsonl output path")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    if args.jsonl is not None:
        if args.seed is None:
            parser.error("--jsonl requires --seed")
        pairs = [(args.seed, args.jsonl)]
    elif args.sweep_log is not None:
        pairs = sorted(map_seeds_to_jsonl(args.sweep_log, args.logs_dir).items())
        logger.info("matched %d seeds to jsonl files", len(pairs))
    else:
        parser.error("need --jsonl + --seed or --sweep-log")

    out_path = args.out
    if out_path is None:
        stamp = datetime.now(UTC).strftime("%Y%m%dT%H%M%SZ")
        out_path = Path("logs/shadow_divergences") / f"census_{stamp}.jsonl"
    out_path.parent.mkdir(parents=True, exist_ok=True)

    results = []
    with out_path.open("w", encoding="utf-8") as fh:
        for seed, jsonl_path in pairs:
            try:
                res = replay_run(
                    jsonl_path,
                    seed,
                    character=args.character,
                    ascension=args.ascension,
                    verbose=args.verbose,
                )
            except Exception as exc:
                logger.exception("seed %d: replay crashed", seed)
                res = {
                    "seed": seed,
                    "jsonl": str(jsonl_path),
                    "status": "crashed",
                    "error": repr(exc),
                    "steps_replayed": 0,
                    "total_rows": 0,
                    "first_divergence": None,
                }
            results.append(res)
            fh.write(json.dumps(res, default=str) + "\n")
            fh.flush()
            fd = res.get("first_divergence") or {}
            logger.info(
                "seed %d: %s at row %d/%d%s",
                seed,
                res["status"],
                res.get("steps_replayed", 0),
                res.get("total_rows", 0),
                (
                    f" (floor {fd.get('floor')} {fd.get('decision')}: "
                    + ", ".join(sorted({d["field"] for d in fd.get("diffs", [])}))
                    + ")"
                    if fd
                    else ""
                ),
            )

    # Aggregate summary.
    by_status = Counter(r["status"] for r in results)
    print("\n=== shadow replay census ===")
    print(f"runs: {len(results)}  {dict(by_status)}")
    div = [r for r in results if r.get("first_divergence")]
    if div:
        first_fields = Counter(
            d["field"].split("[")[0] for r in div for d in r["first_divergence"].get("diffs", [])
        )
        floors = Counter(r["first_divergence"].get("floor") for r in div)
        steps = sorted(r.get("steps_replayed", 0) for r in div)
        print(f"first-divergence fields: {dict(first_fields.most_common(15))}")
        print(
            f"first-divergence floors: {dict(sorted(floors.items(), key=lambda kv: (kv[0] is None, kv[0])))}"
        )
        print(
            f"rows-survived quartiles: {steps[0]}/{steps[len(steps) // 4]}/{steps[len(steps) // 2]}/{steps[(3 * len(steps)) // 4]}/{steps[-1]}"
        )
    print(f"records: {out_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
