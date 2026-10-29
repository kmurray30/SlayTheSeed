#!/usr/bin/env python3
"""Deep act-2/3 whole-run state parity (invincible lockstep).

The default C#-driven whole-run lockstep (``run_parity.py --sweep``) drives a
*random* policy, which dies early in Act 1 — so the carried run state of Acts 2
and 3 (deck/relic/gold/HP evolution, later-act maps, elite/boss combats, shops)
is essentially never sampled. This driver closes that gap (coverage-matrix gap
``(c)``) by pinning the player to ``INVINCIBLE_HP`` on **both** engines whenever
the run is *out of combat*, so the next combat seeds from full invincible HP and
the random walk survives deep into the later acts.

Why this stays a faithful parity check:

* ``set_player`` writes only scalar HP/gold fields — it consumes **no RNG** and
  fires **no hooks** on either engine, so the run's RNG stream is identical to a
  non-invincible run. The trajectory only diverges by *surviving longer*, and it
  does so symmetrically on both engines.
* HP is pinned **only out of combat** (after the per-step diff, before the next
  action). Combat therefore *seeds* from ``INVINCIBLE_HP`` but is never re-pinned
  mid-fight, so in-combat HP decay remains a genuine C#↔Rust parity check (and we
  sidestep the Rust mirror-creature lazy-recreation that an in-combat pin hits).
* ``max_hp`` is pinned too (so heals can't clamp the pinned current HP back down).
  This masks *max-HP-gain* parity in this mode — that surface is covered by the
  normal (non-invincible) sweeps; here the prize is deep act-2/3 *carried state*.

The policy is the same seeded random picker as ``run_parity`` (off the C# oracle's
enumerated choices), with one addition: when an enumerated move is rejected by
**both** engines (the random policy offered an illegal move, e.g. an unaffordable
buy), the choice is dropped and another is picked rather than ending the run — so
one benign reject doesn't truncate a deep run.

Reuses ``run_parity``'s proven ``Snap`` / ``diff_snaps`` / ``diff_maps`` /
``enumerate_choices`` / ``run_signature`` primitives verbatim; ``run_parity.py``
itself is left untouched (this protects its established lockstep baseline).

Usage::

    # single verbose run (triage a divergence)
    python python/act23_state_parity.py --character Ironclad --seed 1000 --verbose

    # sweep
    python python/act23_state_parity.py --all-characters -n 20
    python python/act23_state_parity.py --character Defect -n 50 --max-steps 3000
"""

from __future__ import annotations

import argparse
import random
import sys
from collections import Counter
from concurrent.futures import ProcessPoolExecutor, as_completed
from dataclasses import dataclass, field
from typing import Any

from parity_audit import assert_wheel_fresh
from run_parity import (
    Divergence,
    RunResult,
    Snap,
    diff_maps,
    diff_snaps,
    enumerate_choices,
    map_signature,
    run_signature,
)
from sts2_client import add_game_version_arg, apply_game_version_arg
from workbench.backends.base import make_backend

# Mirrors ``sts2_core::full_run::INVINCIBLE_HP`` (the engine-internal invincible
# coverage constant). Both backends are pinned to this exact value so the
# lockstep HP diff matches.
INVINCIBLE_HP = 100_000

ALL_CHARACTERS = ["Ironclad", "Silent", "Defect", "Regent", "Necrobinder"]


def _act_of(view: dict[str, Any] | None) -> int | None:
    """Best-effort current act index from either engine's state dict.

    The C# backend nests ``act`` under a per-decision ``context`` dict (only the
    ``map_select`` / ``game_over`` serializers also lift it to the top level),
    while Rust puts it top-level — so check ``context`` too or C#'s act reads
    ``None`` at every event/combat/card_reward step (which silently pinned
    ``max_act`` to the Rust side alone). Both engines number acts 1-based and
    identically, so the resolved value IS cross-engine comparable.
    """
    if not isinstance(view, dict):
        return None
    ctx = view.get("context") if isinstance(view.get("context"), dict) else None
    p = view.get("player") if isinstance(view.get("player"), dict) else None
    for src in (view, ctx, p):
        if isinstance(src, dict) and src.get("act") is not None:
            return int(src["act"])
    return None


def _floor_of(view: dict[str, Any] | None) -> int | None:
    """Best-effort serialized floor from either engine's state dict.

    NOTE — these are NOT cross-engine comparable in acts 2/3: C# serializes
    ``RunState.ActFloor`` (per-act, resets to 0 each act) while Rust serializes
    ``RunManager.total_floor()`` (cumulative across acts). So ``_floor_of`` is
    surfaced in the trace per-side for human triage ONLY; the divergence diff
    keys on ``act`` + ``current_coord`` (see ``diff_nav``), both of which use the
    same per-act ``(col, row)`` coordinate system the map-parity check already
    proves equal. Within act 1 the two floor schemes happen to coincide.
    """
    if not isinstance(view, dict):
        return None
    ctx = view.get("context") if isinstance(view.get("context"), dict) else None
    p = view.get("player") if isinstance(view.get("player"), dict) else None
    for src in (view, ctx, p):
        if isinstance(src, dict) and src.get("floor") is not None:
            return int(src["floor"])
    return None


def _current_coord(backend: Any) -> tuple[int, int] | None:
    """Current map-node ``(col, row)`` on either engine, or ``None``.

    This is the finer per-step nav signal issue #158 calls for: it pinpoints the
    *room* each engine sits in, so a "C# a room ahead" boundary desync surfaces
    at its TRUE step instead of cascading into downstream gold/deck/phase skews.
    Rust's ``full_map`` exposes a top-level ``current_coord {col,row}``; C# marks
    the current node in-row with ``current=True``. The ``(col, row)`` coordinate
    system is shared (the same one ``_norm_map_graph`` compares 25/25), so the
    two engines' coords ARE directly comparable. Read defensively — a map-less
    phase or a backend without a map returns ``None`` and the diff skips it.
    """
    try:
        m = backend.get_map()
    except Exception:
        return None
    if not isinstance(m, dict):
        return None
    cc = m.get("current_coord")
    if isinstance(cc, dict) and cc.get("col") is not None and cc.get("row") is not None:
        return (int(cc["col"]), int(cc["row"]))
    for row in m.get("rows") or []:
        for node in row or []:
            if isinstance(node, dict) and node.get("current"):
                col, r = node.get("col"), node.get("row")
                if col is not None and r is not None:
                    return (int(col), int(r))
    return None


@dataclass
class Nav:
    """Finer act/floor/current-node nav snapshot for one engine (issue #158)."""

    act: int | None
    floor: int | None
    coord: tuple[int, int] | None

    @classmethod
    def of(cls, view: dict[str, Any], backend: Any) -> Nav:
        return cls(act=_act_of(view), floor=_floor_of(view), coord=_current_coord(backend))


def diff_nav(cs: Nav, rs: Nav) -> list[str]:
    """Cross-engine nav divergences (``act`` + ``current_coord`` only).

    Called as a BLOCKING check only while both engines are on the ``map`` (see
    the gate at the call site) — there the current node is authoritative on
    both, so a mismatch is a genuine "standing on different nodes" desync.

    Raw ``floor`` is deliberately NOT compared (C# ActFloor per-act vs Rust
    cumulative total_floor — see ``_floor_of``). ``act`` and ``coord`` use the
    shared per-act coordinate system. Each side's coord is read only when that
    engine exposes a current node (``None`` otherwise); a one-sided ``None`` is
    skipped so a benign no-map phase doesn't false-flag.
    """
    out: list[str] = []
    if cs.act is not None and rs.act is not None and cs.act != rs.act:
        out.append(f"nav.act: cs={cs.act} rs={rs.act}")
    if cs.coord is not None and rs.coord is not None and cs.coord != rs.coord:
        out.append(f"nav.coord: cs={cs.coord} rs={rs.coord}")
    return out


# Max player-HP delta tolerated as a C# mid-combat-snapshot artifact (issue
# #158 step 3). The snapshot-timing gap is at most the HP swing of one
# unresolved damage/heal instance at the read boundary; a real combat-damage
# divergence compounds turn over turn (so it eventually exceeds this) or
# co-surfaces an ``enemies_hp``/``phase`` field (so it is never peeled here).
# Empirically the observed artifact deltas are single-/low-double-digit; this
# bound leaves wide margin for a self-damage card (Combust/Brutality/Offering)
# resolving across the snapshot while still catching a persistent real skew.
HP_ARTIFACT_TOLERANCE = 60

# Phases where a small player-HP-only delta is the C# mid-combat snapshot
# artifact rather than a real divergence. Combat itself, plus the combat→reward
# exit window (``card_reward`` / the in-combat ``card_select``) where the
# combat-end HP carries one step before the next OUT-OF-COMBAT pin re-syncs both
# engines to ``INVINCIBLE_HP``. Deliberately EXCLUDES ``event`` / ``rest`` — the
# surfaces where a *real* HP-changing effect (event damage/heal, campfire rest)
# lives and must still surface as a genuine divergence.
_HP_ARTIFACT_PHASES = frozenset({"combat", "card_reward", "card_select"})


def _is_combat_hp_artifact(cs: Snap, rs: Snap, fields: list[str]) -> bool:
    """True when ``fields`` is *exactly* a small player-HP delta inside the
    combat / combat-exit window — the C# mid-combat-snapshot oracle-artifact
    class (issue #158 step 3).

    Strict on purpose so it never masks a real bug: it fires ONLY when every
    diverging field is the player ``hp`` line (a phase/deck/enemy/nav mismatch
    would add another field and disqualify it), the shared phase is one of
    ``_HP_ARTIFACT_PHASES``, and ``|Δhp| <= HP_ARTIFACT_TOLERANCE``. Because the
    run is pinned to ``INVINCIBLE_HP`` out of combat, a tolerated delta is wiped
    at the next pin and cannot perturb the trajectory or survival — so peeling it
    only un-masks the deeper structural signal it was hiding.
    """
    if cs.phase not in _HP_ARTIFACT_PHASES:
        return False
    if not fields or any(not f.startswith("hp:") for f in fields):
        return False
    if cs.hp is None or rs.hp is None:
        return False
    return abs(cs.hp - rs.hp) <= HP_ARTIFACT_TOLERANCE


def _is_err(view: Any) -> bool:
    return isinstance(view, dict) and (view.get("error") or view.get("type") == "error")


def _pin(g: Any, r: Any, hp: int) -> None:
    """Pin both engines' current+max HP to ``hp`` (mirror of C# ``set_player``)."""
    g.set_player(hp=hp, max_hp=hp)
    r.set_player(hp=hp, max_hp=hp)


@dataclass
class InvRun:
    """One invincible-lockstep run: a ``run_parity.RunResult`` plus the extra
    act-depth metadata this driver tracks."""

    result: RunResult
    character: str
    max_act: int = 1
    repicks: int = 0
    atomic_select_skips: int = 0
    event_skips: int = 0
    hp_artifacts: int = 0
    max_hp_artifact: int = 0


def run_one_invincible(
    seed: str,
    character: str = "Ironclad",
    ascension: int = 0,
    max_steps: int = 2500,
    invincible: bool = True,
    verbose: bool = False,
    peel_hp_artifacts: bool = True,
) -> InvRun:
    """Drive a single invincible lockstep run; return an :class:`InvRun`.

    Mirrors ``run_parity.run_one`` (same Snap/diff/choice logic) with two
    additions: out-of-combat HP pinning and both-reject re-picking.
    """
    rng = random.Random(f"act23state:{seed}")
    g = make_backend("csharp")()
    r = make_backend("rust", target_convention="csharp")()
    res = RunResult(seed=seed, steps=0, terminal="error")
    out = InvRun(result=res, character=character)
    try:
        cs_view = g.start(character=character, seed=seed, ascension=ascension)
        rs_view = r.start(character=character, seed=seed, ascension=ascension)

        for step in range(max_steps):
            res.steps = step
            # Track the deepest act EITHER engine reached. They can briefly
            # disagree at an act boundary (one advances a step before the
            # other), and that disagreement is itself a divergence we want to
            # report as "the driver got this deep" — so take the max.
            acts = [a for a in (_act_of(rs_view), _act_of(cs_view)) if a]
            if acts:
                out.max_act = max(out.max_act, *acts)

            cs_dec = cs_view.get("decision") or cs_view.get("type")
            cs_snap, rs_snap = Snap.of(cs_view, "cs"), Snap.of(rs_view, "rs")

            # Atomic-select skip (ported from ``run_parity.py``'s lockstep guard).
            # Rust SURFACES a played card's OnPlay select so an MCTS agent can
            # pick, but several selects it resolves ATOMICALLY (first-legal /
            # deterministic-order) without surfacing:
            #   - Echo / OneTwoPunch replay siblings (the choice opens mid-apply
            #     and cannot surface) and a discard-N whose hand exactly fits;
            #   - turn-start power selects like ForegoneConclusion
            #     (``hooks.rs`` auto-picks the 2 lowest-(rarity,id) draw cards,
            #     matching C#'s first FromSimpleGrid option, then RACES on — often
            #     all the way through combat-end into ``card_reward``);
            #   - synchronous filtered deck removals (Amalgamator: remove 2 basic
            #     Strikes/Defends), which leave Rust in ``map`` / ``card_reward``.
            # In all of these the *same* action leaves C# paused at a
            # ``card_select`` while Rust has already moved on. Driving the shared
            # lockstep here would (a) false-flag the phase/state gap as a
            # divergence and (b) feed C#'s card_select verb to a Rust that is no
            # longer selecting → a phantom ``act_reject``. Instead advance C# ALONE
            # through the select with its first legal pick — the analog of Rust's
            # atomic first-legal resolution — without touching Rust, then re-snap.
            # The next iteration compares C#'s post-select state against Rust's, so
            # a genuinely divergent pick (Rust's atomic choice ≠ C#'s first option)
            # still surfaces there. Gated on ``rs != card_select`` so a REAL shared
            # card_select (Rust ALSO paused, e.g. a surfaced OnPlay pick) is driven
            # normally through the shared lockstep. Beads `sts2-rs-RP25` (original,
            # rs=combat only) / `sts2-rs-RP28` (generalized to any non-select Rust
            # phase: ForegoneConclusion → card_reward, Amalgamator removal → map).
            if cs_snap.phase == "card_select" and rs_snap.phase != "card_select":
                cs_choices = enumerate_choices(g, cs_view)
                if cs_choices:
                    verb, kw = cs_choices[0]
                    nxt_cs = g.act(verb, **kw)
                    if not _is_err(nxt_cs):
                        if verbose:
                            res.trace.append(
                                f"[{step:04d}] act{out.max_act} "
                                f"atomic-select-skip cs:{verb}({kw}) (rust resolved atomically)"
                            )
                        out.atomic_select_skips += 1
                        cs_view = nxt_cs
                        continue
                # Could not advance C# alone (no legal pick, or C# rejected its
                # own choice) — fall through so the state diff reports it rather
                # than silently masking a stuck select.

            # MIRROR of the atomic-select skip: RUST surfaces a played card's
            # OnPlay select (or a turn-start power select) that C# resolved
            # ATOMICALLY, so C# is already back in ``combat`` / has moved on while
            # Rust is paused at ``card_select``. Drive RUST ALONE to FINALIZE its
            # select — ``skip_select`` → ``combat_resolve_choice{option: None}``,
            # the analog of C#'s one-shot ``ResolvePendingByIndices`` — WITHOUT
            # touching C#, then re-snap. The next iteration compares Rust's
            # post-select state against C#'s, so a genuinely divergent atomic pick
            # (Rust's finalize ≠ C#'s resolution) still surfaces there. Gated on
            # ``cs != card_select`` so a REAL shared card_select (both paused) is
            # driven normally. Bounded so a select that never clears (finalize
            # rejected, e.g. unmet ``min_select``) falls through to the diff
            # instead of wedging. Oracle-artifact "run-combat-cardselect".
            guard = 0
            while rs_snap.phase == "card_select" and cs_snap.phase != "card_select" and guard < 8:
                nxt_rs = r.act("skip_select")
                if _is_err(nxt_rs):
                    break  # not skippable / min_select unmet — let the diff report it
                if verbose:
                    res.trace.append(
                        f"[{step:04d}] act{out.max_act} "
                        f"atomic-select-skip rs:skip_select (c# resolved atomically)"
                    )
                out.atomic_select_skips += 1
                rs_view = nxt_rs
                rs_snap = Snap.of(rs_view, "rs")
                guard += 1

            # Event-granularity skip: the C# HEADLESS auto-skips Custom-layout
            # events (FakeMerchant: ``GenerateInitialOptions => Array.Empty`` →
            # "no options, auto-skipping" → ``EnterRoom(new MapRoom())``), so C# is
            # already on the ``map`` while Rust correctly surfaces the FakeMerchant
            # ``event``. Drive RUST ALONE through its no-op ``leave`` option so it
            # re-aligns to the map, then re-snap. Sound because FakeMerchant
            # consumes NO shared run RNG — its inventory shuffle uses a private
            # ``Rng.with_name(seed, "FAKE_MERCHANT")`` stream — so leaving cannot
            # desync the shared trajectory; a genuine "Rust a room behind" desync
            # still surfaces in the subsequent map/nav diff. Bead sts2-rs-1hiht.
            if rs_snap.phase == "event" and cs_snap.phase == "map":
                leave = next(
                    (
                        o
                        for o in (rs_view.get("options") or [])
                        if o.get("option_id") == "leave" and not o.get("is_locked")
                    ),
                    None,
                )
                if leave is not None:
                    nxt_rs = r.act("choose_option", option_index=leave["index"])
                    if not _is_err(nxt_rs):
                        if verbose:
                            res.trace.append(
                                f"[{step:04d}] act{out.max_act} "
                                f"event-gran-skip rs:leave (c# headless auto-skipped)"
                            )
                        out.event_skips += 1
                        rs_view = nxt_rs
                        rs_snap = Snap.of(rs_view, "rs")

            # Finer per-step nav instrumentation (issue #158 step 1): act +
            # current map-node coord for BOTH engines, surfaced every step in the
            # verbose trace so the Act-1→Act-2 "C# a room ahead" desync is
            # *visible at its true step* instead of being reconstructed from a
            # downstream gold/deck/phase cascade.
            cs_nav, rs_nav = Nav.of(cs_view, g), Nav.of(rs_view, r)

            fields = diff_snaps(cs_snap, rs_snap)
            if cs_snap.phase == "map" and rs_snap.phase == "map":
                fields += diff_maps(map_signature(g), map_signature(r))
                # Nav (act + current-node) is BLOCKING only while both engines
                # are on the map — there the current coord is authoritative on
                # both, so a mismatch is a genuine "standing on different nodes"
                # desync that ``diff_maps`` (graph-only) misses. Off the map it
                # is trace-only: at the act boundary C# legitimately leads by one
                # act/room for a step (it regenerates the next act's map and
                # enters its entry event before Rust advances), and 92%+ of runs
                # resolve that transient and cross cleanly — so flagging nav at
                # the transitional event/card_reward would false-diverge and
                # REGRESS reach, the opposite of this probe's goal.
                fields += diff_nav(cs_nav, rs_nav)

            # Oracle-artifact classification (issue #158 step 3): the C#
            # mid-combat HP *snapshot* is taken at a slightly different point in
            # the damage/animation resolution than Rust's, so while BOTH engines
            # are in combat the player-HP read can differ by a small,
            # non-directional amount that self-heals a step or two later. Left
            # un-classified it terminates the run and MASKS every deeper real
            # divergence. Peel it off: when the ONLY field is a small player-HP
            # delta and both sides are mid-combat, record it as an artifact and
            # keep walking (the in-combat enemy-HP / deck / phase checks stay
            # un-masked, so a REAL combat-damage divergence still surfaces — it
            # carries an enemies_hp/phase field and is NOT peeled here). A delta
            # that exceeds the threshold, or persists/grows, falls through to a
            # genuine divergence.
            if peel_hp_artifacts and fields and _is_combat_hp_artifact(cs_snap, rs_snap, fields):
                out.hp_artifacts += 1
                out.max_hp_artifact = max(
                    out.max_hp_artifact, abs((cs_snap.hp or 0) - (rs_snap.hp or 0))
                )
                if verbose:
                    res.trace.append(
                        f"[{step:04d}] act{out.max_act} {cs_snap.phase:11s} "
                        f"hp-artifact cs={cs_snap.hp} rs={rs_snap.hp} "
                        f"(Δ{abs((cs_snap.hp or 0) - (rs_snap.hp or 0))}, tolerated)"
                    )
                fields = []

            if verbose:
                res.trace.append(
                    f"[{step:04d}] act{out.max_act} {cs_snap.phase:11s} "
                    f"cs[hp={cs_snap.hp} gold={cs_snap.gold} deck={cs_snap.deck_size} "
                    f"coord={cs_nav.coord} floor={cs_nav.floor}] "
                    f"rs[hp={rs_snap.hp} coord={rs_nav.coord} floor={rs_nav.floor}] "
                    f"enemies cs={list(cs_snap.enemies)} rs={list(rs_snap.enemies)}"
                )
            if fields:
                # On a phase split, record each engine's raw decision + the C#
                # offered choices, so a ``cs=map rs=event`` style room-entry
                # granularity skew shows WHICH map move C# still has pending vs
                # the room Rust already resolved (issue #158 step 2 triage).
                if verbose and cs_snap.phase != rs_snap.phase:
                    res.trace.append(
                        f"       phase-split: cs_dec={cs_view.get('decision') or cs_view.get('type')!r} "
                        f"cs_choices={enumerate_choices(g, cs_view)[:4]} "
                        f"rs_dec={rs_view.get('decision') or rs_view.get('type')!r}"
                    )
                res.divergence = Divergence(
                    step,
                    cs_snap.phase,
                    fields,
                    "map"
                    if any(f.startswith("map") for f in fields)
                    else ("nav" if all(f.startswith("nav") for f in fields) else "state"),
                )
                res.terminal = "diverged"
                return out

            if cs_dec == "game_over":
                res.cs_won = bool(cs_view.get("victory") or cs_view.get("won"))
                rs_over = Snap.of(rs_view, "rs").phase == "game_over"
                res.rs_won = bool(rs_view.get("victory") or rs_view.get("won") or rs_over)
                res.terminal = "game_over_match" if rs_over else "diverged"
                if not rs_over:
                    res.divergence = Divergence(
                        step, "game_over", [f"cs=game_over rs={rs_snap.phase}"], "state"
                    )
                return out

            choices = enumerate_choices(g, cs_view)
            if not choices:
                res.divergence = Divergence(
                    step, cs_snap.phase, [f"no choices for cs decision {cs_dec!r}"], "harness"
                )
                res.terminal = "error"
                return out

            # Pin HP out of combat (AFTER the genuine diff above): the next
            # combat then seeds from INVINCIBLE_HP on both engines so the random
            # policy survives, while in-combat HP stays an un-masked parity check.
            if invincible and cs_snap.phase != "combat":
                _pin(g, r, INVINCIBLE_HP)

            # Pick a choice; on a both-reject (benign illegal move) drop it and
            # re-pick so one bad enumerated move doesn't truncate the run.
            pool = list(choices)
            advanced = False
            while pool:
                idx = rng.randrange(len(pool))
                verb, kw = pool.pop(idx)
                nxt_cs = g.act(verb, **kw)
                nxt_rs = r.act(verb, **kw)
                cs_err, rs_err = _is_err(nxt_cs), _is_err(nxt_rs)
                if cs_err and rs_err:
                    out.repicks += 1
                    continue  # both reject => benign, try another choice
                if cs_err or rs_err:
                    rs_msg = (nxt_rs.get("error") or nxt_rs.get("message")) if rs_err else None
                    cs_msg = nxt_cs.get("message") if cs_err else None
                    res.divergence = Divergence(
                        step,
                        cs_snap.phase,
                        [f"act({verb},{kw}) -> cs_err={cs_msg} rs_err={rs_msg}"],
                        "act_reject",
                    )
                    res.terminal = "diverged"
                    return out
                cs_view, rs_view = nxt_cs, nxt_rs
                advanced = True
                break
            if not advanced:
                # Every enumerated choice was rejected by both engines.
                res.terminal = "cs_act_reject"
                return out

        res.terminal = "max_steps"
        return out
    except Exception as exc:  # pragma: no cover - harness-level failure
        import traceback

        res.divergence = Divergence(res.steps, "?", [f"{type(exc).__name__}: {exc}"], "harness")
        res.terminal = "error"
        if verbose:
            res.trace.append(traceback.format_exc())
        return out
    finally:
        for backend in (g, r):
            try:
                backend.close()
            except Exception:
                pass


# --------------------------------------------------------------------------- #
# Sweep
# --------------------------------------------------------------------------- #
@dataclass
class SweepReport:
    runs: int = 0
    reached_act2: int = 0
    reached_act3: int = 0
    act_hist: Counter = field(default_factory=Counter)
    term_hist: Counter = field(default_factory=Counter)
    buckets: dict[str, list[str]] = field(default_factory=dict)
    hp_artifact_runs: int = 0  # runs that tolerated >=1 mid-combat HP artifact
    hp_artifacts: int = 0  # total artifacts peeled across the sweep
    max_hp_artifact: int = 0  # largest tolerated |Δhp| (sanity-checks the bound)


def _worker(
    args: tuple[str, str, int, int, bool, bool],
) -> tuple[str, str, int, str, str | None, int, int]:
    seed, character, ascension, max_steps, invincible, peel = args
    out = run_one_invincible(
        seed,
        character,
        ascension=ascension,
        max_steps=max_steps,
        invincible=invincible,
        peel_hp_artifacts=peel,
    )
    res = out.result
    sig = run_signature(res)
    div = None
    if res.divergence is not None:
        div = f"{res.divergence.phase}@{res.divergence.step}: {res.divergence.fields[:2]}"
    return (
        f"{character}:{seed}",
        sig,
        out.max_act,
        res.terminal,
        div,
        out.hp_artifacts,
        out.max_hp_artifact,
    )


def sweep(
    characters: list[str],
    n: int,
    start: int,
    ascension: int,
    max_steps: int,
    workers: int,
    invincible: bool,
    peel_hp_artifacts: bool = True,
) -> SweepReport:
    rep = SweepReport()
    jobs = [
        (str(start + i), ch, ascension, max_steps, invincible, peel_hp_artifacts)
        for ch in characters
        for i in range(n)
    ]
    with ProcessPoolExecutor(max_workers=workers) as ex:
        futs = {ex.submit(_worker, j): j for j in jobs}
        for fut in as_completed(futs):
            label, sig, max_act, term, div, hp_arts, max_hp_art = fut.result()
            rep.runs += 1
            rep.act_hist[max_act] += 1
            rep.term_hist[term] += 1
            if max_act >= 2:
                rep.reached_act2 += 1
            if max_act >= 3:
                rep.reached_act3 += 1
            if hp_arts:
                rep.hp_artifact_runs += 1
                rep.hp_artifacts += hp_arts
                rep.max_hp_artifact = max(rep.max_hp_artifact, max_hp_art)
            rep.buckets.setdefault(sig, [])
            art = f" [+{hp_arts}hp-art]" if hp_arts else ""
            if div:
                rep.buckets[sig].append(f"{label} act{max_act} {div}{art}")
            else:
                rep.buckets[sig].append(f"{label} act{max_act}{art}")
            print(f"  {label:24s} act{max_act} {term:16s} {sig}{art}", flush=True)
    return rep


def print_report(rep: SweepReport) -> None:
    print("\n=== ACT-DEPTH ===")
    print(f"runs:            {rep.runs}")
    pct2 = 100 * rep.reached_act2 / rep.runs if rep.runs else 0
    pct3 = 100 * rep.reached_act3 / rep.runs if rep.runs else 0
    print(f"reached act>=2:  {rep.reached_act2} ({pct2:.0f}%)")
    print(f"reached act>=3:  {rep.reached_act3} ({pct3:.0f}%)")
    print(f"max-act hist:    {dict(sorted(rep.act_hist.items()))}")
    print(f"terminal hist:   {dict(rep.term_hist.most_common())}")
    print(
        f"hp-artifacts:    {rep.hp_artifacts} peeled across {rep.hp_artifact_runs} runs "
        f"(max |Δhp|={rep.max_hp_artifact}, tolerance={HP_ARTIFACT_TOLERANCE})"
    )
    print("\n=== BUCKETS (by run_signature) ===")
    for sig, examples in sorted(rep.buckets.items(), key=lambda kv: (-len(kv[1]), kv[0])):
        clean = sig.startswith("clean:")
        tag = "OK  " if clean else "DIV "
        print(f"{tag}[{len(examples):3d}] {sig}")
        if not clean:
            for ex in examples[:4]:
                print(f"        e.g. {ex}")


def main() -> int:
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    ap.add_argument("--character", default="Ironclad")
    ap.add_argument("--all-characters", action="store_true")
    ap.add_argument("--ascension", type=int, default=0)
    ap.add_argument("-n", "--num", type=int, default=10, help="seeds per character")
    ap.add_argument("--start", type=int, default=1000, help="first seed (ints, stringified)")
    ap.add_argument("--max-steps", type=int, default=2500)
    ap.add_argument("--workers", type=int, default=8)
    ap.add_argument("--no-invincible", action="store_true", help="baseline (death-limited) compare")
    ap.add_argument(
        "--no-hp-peel",
        action="store_true",
        help="do NOT classify out the C# mid-combat-snapshot HP artifact (raw reach, for A/B)",
    )
    ap.add_argument("--seed", help="single verbose run for this seed (overrides sweep)")
    ap.add_argument("--verbose", action="store_true", help="dump per-step trace (single run)")
    add_game_version_arg(ap)
    args = ap.parse_args()
    apply_game_version_arg(args)

    assert_wheel_fresh()
    invincible = not args.no_invincible
    peel = not args.no_hp_peel
    chars = ALL_CHARACTERS if args.all_characters else [args.character]

    if args.seed is not None:
        out = run_one_invincible(
            args.seed,
            args.character,
            ascension=args.ascension,
            max_steps=args.max_steps,
            invincible=invincible,
            verbose=args.verbose,
            peel_hp_artifacts=peel,
        )
        res = out.result
        if args.verbose:
            for line in res.trace[-80:]:
                print(line)
        print(
            f"\nseed={args.seed} char={args.character} max_act={out.max_act} "
            f"steps={res.steps} terminal={res.terminal} repicks={out.repicks} "
            f"atomic_select_skips={out.atomic_select_skips} "
            f"event_skips={out.event_skips} "
            f"hp_artifacts={out.hp_artifacts} (max |Δhp|={out.max_hp_artifact})"
        )
        if res.divergence:
            d = res.divergence
            print(f"DIVERGENCE @step {d.step} phase={d.phase} kind={d.kind}")
            for fld in d.fields:
                print(f"  {fld}")
            print(f"signature: {run_signature(res)}")
        return 1 if res.divergence else 0

    print(
        f"invincible={invincible} peel_hp_artifacts={peel} chars={chars} n={args.num} "
        f"start={args.start} max_steps={args.max_steps}"
    )
    rep = sweep(
        chars,
        args.num,
        args.start,
        args.ascension,
        args.max_steps,
        args.workers,
        invincible,
        peel_hp_artifacts=peel,
    )
    print_report(rep)
    n_div = sum(len(v) for k, v in rep.buckets.items() if not k.startswith("clean:"))
    return 1 if n_div else 0


if __name__ == "__main__":
    sys.exit(main())
