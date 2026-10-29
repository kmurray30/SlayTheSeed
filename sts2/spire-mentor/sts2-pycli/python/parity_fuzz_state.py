"""Parity-fuzz state surface — normalizers + recursive structural diff.

Task 5 of the parity-fuzz harness plan
(docs/superpowers/plans/2026-05-24-csharp-rust-parity-fuzz.md).

Three exports:

  - :func:`normalize_csharp` reshapes a C# ``get_state`` response into a
    canonical dict.
  - :func:`normalize_rust` reshapes a ``sts2_rs.PyCombatState`` (via its
    ``state_to_dict()`` method) into the same canonical dict.
  - :func:`diff` recursively structurally compares two canonical dicts
    and returns a list of ``{path, csharp, rust}`` divergence records.

The canonical schema (the shape both normalizers produce)::

    {
        "player": {"hp": int, "max_hp": int, "block": int, "energy": int,
                   "powers": [{"kind": str, "amount": int}, ...]},  # sorted by kind
        "monsters": [
            {"uid": int, "kind": str | None, "hp": int, "max_hp": int,
             "block": int, "alive": bool,
             "powers": [{"kind": str, "amount": int}, ...]},  # sorted by kind
            ...
        ],  # sorted by uid
        "hand_count": int, "draw_count": int, "discard_count": int,
        "exhaust_count": int,
        "turn": int,
        "relics": [str, ...],  # sorted alphabetically
        "combat_terminal": bool,
        "player_won": bool | None,
    }

------------------------------------------------------------------------------
Probe results — 2026-05-24 (sts2-pycli @ main)
------------------------------------------------------------------------------

Combat-shaped responses (`enter_room("combat", ...)` / `play_card` /
`end_turn`) all go through `DetectDecisionPoint() -> CombatPlayState()` in
the C# headless. The Task 3 `get_state` command emits a parallel
canonical-id surface — that's the shape :func:`normalize_csharp` consumes
here. See ``src/Sts2Headless/RunSimulator.cs:660`` for the producing
``GetStateImpl()``.
"""

from __future__ import annotations

import re
from typing import Any, TypedDict


class Divergence(TypedDict):
    path: str
    csharp: Any
    rust: Any


# ---------------------------------------------------------------------------
# diff
# ---------------------------------------------------------------------------


def diff(csharp: Any, rust: Any, _path: str = "") -> list[Divergence]:
    """Recursive structural diff between two normalized states.

    When ``csharp == rust`` returns ``[]``. Otherwise returns one
    :class:`Divergence` per leaf disagreement. Dict keys are visited in
    sorted order so output is deterministic. For lists of unequal length
    we emit a single ``<path>.len`` entry first, then descend on the
    zipped prefix. Type mismatches at the same path emit one entry and
    do not descend.
    """
    if csharp == rust:
        return []

    # Type mismatch at this path → single entry, no descent.
    if type(csharp) is not type(rust):
        return [{"path": _path or ".", "csharp": csharp, "rust": rust}]

    if isinstance(csharp, dict):
        out: list[Divergence] = []
        keys = sorted(set(csharp.keys()) | set(rust.keys()))
        for k in keys:
            sub_path = f"{_path}.{k}" if _path else k
            if k not in csharp or k not in rust:
                out.append(
                    {
                        "path": sub_path,
                        "csharp": csharp.get(k, _Missing),
                        "rust": rust.get(k, _Missing),
                    }
                )
                continue
            out.extend(diff(csharp[k], rust[k], sub_path))
        return out

    if isinstance(csharp, list):
        out = []
        if len(csharp) != len(rust):
            len_path = f"{_path}.len" if _path else "len"
            out.append({"path": len_path, "csharp": len(csharp), "rust": len(rust)})
        for i, (a, b) in enumerate(zip(csharp, rust)):
            sub_path = f"{_path}[{i}]"
            out.extend(diff(a, b, sub_path))
        return out

    # Same type, unequal scalars.
    return [{"path": _path or ".", "csharp": csharp, "rust": rust}]


def diff_post_action(csharp: Any, rust: Any) -> list[Divergence]:
    """:func:`diff`, but combat-terminal-aware for post-action snapshots.

    When BOTH engines report ``combat_terminal`` (the action ended combat
    on both sides) the piles, powers, leftover monster corpses, turn
    counter, etc. are pure teardown convention — C# zeros its piles and
    clears the player's powers on Victory/Defeat, while the Rust resolved-
    combat snapshot retains them. Comparing those fields manufactures a
    spurious STATE_DRIFT on every killing blow (the ``combat_terminal`` /
    ``discard_count`` teardown buckets of sts2-rs-8ukqd). Once both sides
    agree combat is over, the only parity-meaningful comparison is the
    terminal OUTCOME, so we keep just a ``player_won`` mismatch (one side
    Victory, the other Defeat) and drop the rest.

    This is intentionally NOT folded into :func:`diff` itself: ``diff`` is a
    generic structural differ reused for INIT and intra-combat comparisons
    where every field IS meaningful. Only the post-action snapshot earns the
    terminal carve-out.
    """
    divs = diff(csharp, rust)
    if not divs:
        return divs
    both_terminal = bool(csharp.get("combat_terminal")) and bool(rust.get("combat_terminal"))
    if both_terminal:
        divs = [d for d in divs if d["path"] in ("player_won", "combat_terminal")]
    return divs


class _Missing:
    """Sentinel for keys present on one side but not the other."""


# ---------------------------------------------------------------------------
# normalize_csharp
# ---------------------------------------------------------------------------


def _sort_powers(powers: list[dict] | None) -> list[dict]:
    """Normalize C# power kinds for comparison with the Rust side.

    The C# headless surface emits class names like `SLIPPERY_POWER`
    and `SLOW_POWER`; the Rust side emits `SLIPPERY` / `SLOW` via
    `MonsterKind::python_name`. Strip the trailing `_POWER` suffix
    so both sides converge on the canonical short form. (sts2-rs-5dp60
    follow-up via 3010t validation.)
    """
    powers = powers or []
    return sorted(
        (
            {
                "kind": _strip_power_suffix(p["kind"]),
                "amount": int(p["amount"]),
            }
            for p in powers
        ),
        key=lambda p: p["kind"],
    )


def _strip_power_suffix(kind: str) -> str:
    """Canonicalize a power kind by stripping ALL trailing ``_POWER``.

    C# wraps every power class as ``<Name>Power``, so a power whose
    canonical name itself ends in "Power" gets a DOUBLE suffix: the
    ``FreePower`` power (next Power card is free) is class
    ``FreePowerPower`` → ``FREE_POWER_POWER``. Rust's
    ``PowerKind::FreePower`` python_name is the single ``FREE_POWER``.
    A single strip left the two sides at ``FREE_POWER`` (C#) vs ``FREE``
    (Rust) — a spurious ``PLAYER_STATE`` divergence (Synthesis applies
    ``FreePower``; surfaced on the v2 sweep, seed 2649). ``FreeAttack`` /
    ``FreeSkill`` dodge this only because their Rust python_name carries
    no ``_POWER`` to begin with (``FREE_ATTACK`` vs C#
    ``FREE_ATTACK_POWER`` → both ``FREE_ATTACK`` after one strip). A
    greedy strip converges every case (and is a no-op for the normal
    single-suffix powers ``SLOW_POWER`` → ``SLOW``,
    ``GRAPPLE_POWER`` → ``GRAPPLE``).
    """
    while kind.endswith("_POWER"):
        kind = kind[:-6]
    return kind


def _normalize_orbs(orbs: list[dict] | None) -> list[dict]:
    """Canonicalize a Defect orb belt for cross-engine comparison.

    Both engines emit an ORDERED list of channelled orbs as
    ``{"type": <PascalCase>, "passive": int, "evoke": int}`` (C# from
    ``OrbModel`` via ``RunSimulator.GetStateImpl``; Rust from
    ``state_dict.rs``). We compare ``kind`` (upper-cased type) + ``passive``
    in channel order — ``passive`` folds in Focus (and Glass's per-instance
    decay), so a Focus / channel-count / order / type divergence surfaces
    directly instead of only via a downstream block/HP cascade.

    ``evoke`` IS compared, as of 2026-08-03. It used to be excluded as a
    "representation mismatch" — Rust serialized a Dark orb's ``evoke`` as its
    raw accumulated ``stored`` while C# reports ``DarkOrb._evokeVal``
    (``6 + stored``). That was not a representation difference, it was a bug:
    the parse leg subtracted nothing either, so the two errors cancelled in
    every round-trip and every reconstructed Dark orb evoked for 6 too much
    (257 orb elements on the 2026-08-03 live Defect corpus, and a measured
    48-vs-36 damage error through ``state_from_dict``). Both legs now carry the
    C# value, so excluding ``evoke`` here would only re-hide the next one.
    Orb order is load-bearing (FIFO eviction / evoke-front), so the list is
    NOT sorted.
    """
    return [
        {
            "kind": str(o["type"]).upper(),
            "passive": int(o["passive"]),
            "evoke": int(o["evoke"]),
        }
        for o in (orbs or [])
    ]


def _canon_osty(raw: dict | None) -> dict | None:
    """Canonicalize the Necrobinder Osty pet for cross-engine comparison.

    Both engines export the companion (C# ``RunSimulator.GetStateImpl`` at
    top-level ``result["osty"]``; Rust ``state_dict.rs`` at
    ``player.osty``) but the parity surface never compared it, so Osty
    divergences (a wrong Summon amount / OstyAttack self-damage / failed
    revive) were invisible until they cascaded into player/enemy HP.

    A dead-or-absent Osty collapses to ``None`` on BOTH sides: C# reports
    ``{"alive": false}`` for a null OR dead pet, Rust omits the key entirely
    when never summoned and reports ``alive=false`` once it dies. When the
    Osty is alive we compare its ``hp`` / ``max_hp`` / ``block`` (the
    companion's size IS the mechanic — Summon grows MaxHp, OstyAttacks scale
    on it). ``name`` (C#-only) and Osty ``powers`` (Rust-only; C# does not
    export them) are deliberately excluded — neither side exports a shape the
    other can match.
    """
    if not raw or not raw.get("alive"):
        return None
    return {
        "hp": int(raw["hp"]),
        "max_hp": int(raw["max_hp"]),
        "block": int(raw["block"]),
    }


def normalize_csharp(get_state_response: dict) -> dict:
    """Reshape a C# ``get_state`` response into the canonical dict.

    Input shape: see ``RunSimulator.GetStateImpl()`` (commit d8c9143).
    """
    raw_player = get_state_response["player"]
    player = {
        "hp": int(raw_player["hp"]),
        "max_hp": int(raw_player["max_hp"]),
        "block": int(raw_player["block"]),
        "energy": int(raw_player["energy"]),
        "powers": _sort_powers(raw_player.get("powers")),
    }

    raw_monsters = get_state_response.get("monsters") or []
    monsters = [
        {
            "uid": int(m["uid"]),
            "kind": m.get("kind"),
            "hp": int(m["hp"]),
            "max_hp": int(m["max_hp"]),
            "block": int(m["block"]),
            "alive": bool(m["alive"]),
            "powers": _sort_powers(m.get("powers")),
        }
        for m in raw_monsters
    ]
    monsters.sort(key=lambda m: m["uid"])
    # PARITY-FUZZ: C# uids are raw slot indices (0-based); Rust uids are
    # opaque allocator handles. Project both onto ordinal position
    # post-sort so uid differences alone don't fire a divergence.
    for i, m in enumerate(monsters):
        m["uid"] = i

    # PARITY-FUZZ: C# harness `skip_neow` side-effects may leave
    # NEOWS_TORMENT on the relic list, but the Rust scenario builder
    # never grants it. Filter so both sides agree.
    relics = sorted(r for r in (get_state_response.get("relics") or []) if r != "NEOWS_TORMENT")

    player_won = get_state_response.get("player_won")
    return {
        "player": player,
        "monsters": monsters,
        "orbs": _normalize_orbs(get_state_response.get("orbs")),
        "osty": _canon_osty(get_state_response.get("osty")),
        "hand_count": int(get_state_response.get("hand_count", 0)),
        "draw_count": int(get_state_response.get("draw_count", 0)),
        "discard_count": int(get_state_response.get("discard_count", 0)),
        "exhaust_count": int(get_state_response.get("exhaust_count", 0)),
        "turn": int(get_state_response.get("turn", 0)),
        "relics": relics,
        "combat_terminal": bool(get_state_response.get("combat_terminal")),
        "player_won": None if player_won is None else bool(player_won),
    }


# ---------------------------------------------------------------------------
# normalize_rust
# ---------------------------------------------------------------------------


_PASCAL_RE = re.compile(
    r"""
    [A-Z]+(?=[A-Z][a-z])   # run of caps followed by Capital+lower (e.g. APIResponse → API + Response)
    | [A-Z]?[a-z0-9]+      # Capital + lowercase tail (e.g. Beetle, foo123)
    | [A-Z]+               # trailing run of caps (e.g. trailing S in LeafSlimeS)
    """,
    re.VERBOSE,
)


def _pascal_to_upper_snake(s: str) -> str:
    """Convert ``PascalCase`` to ``UPPER_SNAKE_CASE``.

    Already-UPPER_SNAKE strings pass through unchanged (the underscore
    short-circuit handles them, and the regex also gives the right
    answer on a plain run of caps).

    Examples::

        _pascal_to_upper_snake("ShrinkerBeetle") == "SHRINKER_BEETLE"
        _pascal_to_upper_snake("BowlbugRock")    == "BOWLBUG_ROCK"
        _pascal_to_upper_snake("LeafSlimeS")     == "LEAF_SLIME_S"
        _pascal_to_upper_snake("APIResponse")    == "API_RESPONSE"
        _pascal_to_upper_snake("ALREADY_UPPER")  == "ALREADY_UPPER"
    """
    if not s:
        return s
    # Already upper-snake (or all-upper) — short circuit so we don't
    # split runs of caps inside an existing screaming-snake token.
    if "_" in s or s.isupper():
        return s
    parts = _PASCAL_RE.findall(s)
    return "_".join(p.upper() for p in parts)


def _maybe_convert_kind(kind: str | None) -> str | None:
    if kind is None:
        return None
    return _pascal_to_upper_snake(kind)


def normalize_rust(py_combat_state: Any) -> dict:
    """Reshape a ``sts2_rs.PyCombatState`` into the canonical dict.

    Calls ``py_combat_state.state_to_dict()`` to get the underlying
    wire-format dict produced by
    ``sts2-py/src/state_dict.rs::state_to_dict``. The Rust dict uses
    different key names than the C# response — this function maps them
    onto the shared canonical schema.

    Missing required keys raise :class:`KeyError` rather than masking
    schema drift.
    """
    d = py_combat_state.state_to_dict()

    raw_player = d["player"]
    # Rust emits per-power dicts as {"name": ..., "amount": ...}; the
    # canonical schema uses "kind" to match the C# field. Power IDs
    # already come through `python_name()` as UPPER_SNAKE_CASE
    # (crates/sts2-core/src/power_ids_generated.rs), so no kind
    # conversion is needed for powers.

    def _rust_powers(raw: list[dict] | None) -> list[dict]:
        # Strip the trailing `_POWER` symmetrically with the C# side
        # (`_sort_powers`). Three Rust power ids keep the suffix —
        # FREE_POWER / GRAPPLE_POWER / MANGLE_POWER — and would otherwise
        # spuriously diverge on `powers[].kind` against the C# short form
        # (sts2-rs-q6bix triage; cf. the `_CARD` canon in parity_audit).
        raw = raw or []
        return sorted(
            ({"kind": _strip_power_suffix(p["name"]), "amount": int(p["amount"])} for p in raw),
            key=lambda p: p["kind"],
        )

    player = {
        "hp": int(raw_player["hp"]),
        "max_hp": int(raw_player["max_hp"]),
        "block": int(raw_player["block"]),
        "energy": int(raw_player["energy"]),
        "powers": _rust_powers(raw_player.get("powers")),
    }

    # Monsters live under "encounter" in the Rust dict. Each entry has
    # uid / kind (PascalCase via MonsterKind::python_name) / hp / max_hp
    # / block / is_dead / powers.
    raw_encounter = d.get("encounter") or []
    monsters = []
    for m in raw_encounter:
        # C# RemoveCreature parity (sts2-rs-c8why): the C# `monsters` list
        # is derived from `pcs.Enemies`, which drops dead-and-removable
        # corpses. The Rust engine keeps them in the stable `enemy_uids`
        # substrate (so NN feature slots don't reshuffle on death) and
        # flags each via `removed_after_death`. Skip those here so the
        # compacted Rust view matches C#'s Enemies shape. Dead-but-retained
        # corpses (IllusionPower owners, e.g. a slain EyeWithTeeth) have
        # the flag false and stay, matching C#.
        if bool(m.get("removed_after_death", False)):
            continue
        kind = _maybe_convert_kind(m.get("kind"))
        monsters.append(
            {
                "uid": int(m["uid"]),
                "kind": kind,
                "hp": int(m["hp"]),
                "max_hp": int(m["max_hp"]),
                "block": int(m["block"]),
                # Rust uses is_dead; canonical schema uses alive (matches C#).
                "alive": not bool(m.get("is_dead", False)),
                "powers": _rust_powers(m.get("powers")),
            }
        )
    # sts2-rs-o1mwv (C.1): do NOT sort by Rust uid (an opaque
    # allocator handle = spawn order). `state_to_dict` already emits
    # monsters in `enemy_uids` order, which the engine keeps faithful
    # to the C# slot order (`CombatState::spawn_enemy_at_front` inserts
    # summons like Fogmog's EyeWithTeeth into their leading slot).
    # normalize_csharp sorts C# monsters by uid (= 0-based slot index),
    # i.e. into slot order — so comparing Rust in emission order against
    # C# in slot order aligns. Sorting Rust by handle here instead pins
    # spawned creatures to the back regardless of slot, which is exactly
    # the FOGMOG monsters[0]/[1] index-swap drift.
    for i, m in enumerate(monsters):
        m["uid"] = i

    # Pile counts: Rust emits the full pile lists under d["deck"].
    deck = d.get("deck") or {}
    hand_count = len(deck.get("hand") or [])
    draw_count = len(deck.get("draw_pile") or [])
    discard_count = len(deck.get("discard_pile") or [])
    exhaust_count = len(deck.get("exhaust_pile") or [])

    # Relics: list of {"id": str, "counter"?: int}. Already UPPER_SNAKE
    # (crates/sts2-core/src/relics.rs::RelicKind::python_name).
    raw_relics = raw_player.get("relics") or []
    relics = sorted(r["id"] for r in raw_relics if r.get("id"))

    # Combat termination: Rust state_to_dict emits a top-level "phase"
    # string; "Victory"/"Defeat" are the resolved-combat discriminants
    # (CombatPhase::Resolved { player_won }, see
    # crates/sts2-py/src/state_dict.rs::combat_phase_to_str). The earlier
    # comment here claimed Rust carried no terminal info and hardcoded
    # combat_terminal=False — that is stale and made the Rust side
    # structurally unable to ever report termination, so EVERY player
    # death/win read as a spurious TERMINAL divergence against C#
    # (parity-fuzz bucket A1, 2026-05-29 PM audit). Read the phase instead.
    phase = d.get("phase")
    combat_terminal = phase in ("Victory", "Defeat")
    player_won = True if phase == "Victory" else False if phase == "Defeat" else None
    return {
        "player": player,
        "monsters": monsters,
        "orbs": _normalize_orbs(raw_player.get("orbs")),
        "osty": _canon_osty(raw_player.get("osty")),
        "hand_count": hand_count,
        "draw_count": draw_count,
        "discard_count": discard_count,
        "exhaust_count": exhaust_count,
        "turn": int(d.get("turn", d.get("round", 0))),
        "relics": relics,
        "combat_terminal": combat_terminal,
        "player_won": player_won,
    }
