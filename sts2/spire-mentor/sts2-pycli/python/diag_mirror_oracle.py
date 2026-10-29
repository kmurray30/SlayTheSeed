"""Native-engine oracle for :mod:`combat_mirror`.

Drives a :class:`~combat_mirror.CombatMirror` alongside a *native*
``sts2_rs.RunManager`` run: at every combat decision the true engine state is
serialized with ``state_to_dict`` and handed to the mirror as if it had come
off the wire from the C# DLL, both sides commit the same action, and the
mirror's prediction is checked against reality on the next decision.

Why this is the highest-value test in the project
=================================================

On the DLL and live paths a divergence has two possible causes — the mirror's
own machinery, or a content-parity gap between the Rust engine and the real
game — and no way to tell them apart. Here the world *is* the Rust engine, so
content parity is exact by construction and **every divergence is a
wire/mirror bug**. It needs no game process, so it runs in CI.

It has already paid for itself. Every bug below silently degraded every
reconstructed search on the deployed rung-3.5 path, and each was found by
running this (or the 60-run DLL sweep it made legible):

* ``player.play`` (cards mid-resolution) had **no wire representation at all**,
  so every card-select reconstruction dropped the card that opened the screen.
* ``state_to_dict`` emits base intent damage while the C# producers emit the
  UI-effective number, and the parser assumed the latter — so a
  ``state_to_dict`` round trip subtracted Strength/Vulnerable that were never
  added (12 damage read back as 10). Now explicit on the wire as
  ``intent_amounts``.
* Reconstructed cards got ``CardTags::NONE``, so tag-gated handlers read an
  empty deck: PerfectedStrike counted **zero** Strikes and dealt 4 where the
  native engine dealt 12.
* The Defect orb belt was rebuilt compacted instead of ``Orb::Empty``-padded to
  capacity, and ``OrbSlots::channel`` fills the first empty slot or else
  *evicts* — so a reconstructed belt could never grow.
* ``Power::counter`` — CrimsonMantle's self-damage total, Slow's tally,
  Surrounded's facing — was not on the wire, so a reconstructed CrimsonMantle
  dealt no self-damage and the mirror sat exactly 1 HP high after every turn.
* Only the EFFECTIVE card cost was on the wire, so ``build_card`` baked a
  this-turn discount into ``Card.cost`` and it never expired: the search
  believed Stomp-reduced and free-this-turn cards were cheap forever. Now
  ``base_cost`` + ``combat_vars`` travel alongside it.
* The two worlds stop agreeing on enemy SLOTS the moment one reaps a corpse
  the other keeps, so a slot-wise diff reported six fields wrong on a
  perfectly synchronized board. Enemies are now keyed by identity
  (``combat_mirror.enemy_slot_key``).

Running it
==========

    python python/diag_mirror_oracle.py --characters Ironclad --seeds 1-20

Prints a per-character summary and a signature inventory: which observable
field-sets diverged, how often, and on which seeds. That inventory is what
:data:`KNOWN_RECONSTRUCTION_GAPS` documents and the pytest sweep enforces.
"""

from __future__ import annotations

import argparse
import json
import random
import re
from collections import Counter
from dataclasses import dataclass, field
from typing import Any

from combat_mirror import CombatMirror, MirrorPolicy
from sts2_hint.recommender import RecommendConfig

# `run_policy` and `sts2_rs` are imported inside `drive_run`: this module must
# import cleanly in a venv without the PyO3 wheel so a test file can still
# `pytest.importorskip("sts2_rs")` and skip rather than fail collection.

#: The five playable characters, in registry order.
CHARACTERS = ("Ironclad", "Silent", "Defect", "Regent", "Necrobinder")

#: Search profile the oracle hands its mirrors. The oracle never searches
#: (the world picks the actions), so ``sims`` only has to be legal.
ORACLE_CFG = RecommendConfig(sims=8, hidden_info="beliefdraw", c_puct=2.5, draw_widening_c=2.0)

#: Any bracketed subscript — a numeric pile index, or an enemy identity
#: key like `[TwigSlimeS#1]` (see `combat_mirror.enemy_slot_key`).
_INDEXED = re.compile(r"\[[^\]]*\]")


def normalize(paths: list[str]) -> list[str]:
    """A divergence's field list as a stable, sorted set of class labels.

    Subscripts are erased (``$.encounter[TwigSlimeS#1].hp`` →
    ``$.encounter[].hp``) so "the same gap on a different enemy" is one class,
    and the result is sorted so field order can't split a class in two.
    """
    return sorted({_INDEXED.sub("[]", p) for p in paths})


def signature(paths: list[str]) -> str:
    """:func:`normalize`, rendered as one human-readable label."""
    return " + ".join(normalize(paths))


# ---------------------------------------------------------------------------
# The reconstruction frontier
# ---------------------------------------------------------------------------
#
# Everything below is a KNOWN gap in `state_dict.rs`'s reconstruction — a field
# the wire does not carry, exposed (not caused) by the mirror. The pytest sweep
# asserts that no divergence falls OUTSIDE this table, which is what makes it a
# real gate: any new mirror-machinery bug produces an unlisted signature and
# fails, while the documented frontier does not block the phase.
#
# Each entry retires when the underlying wire gap is closed; delete the row and
# the sweep tightens automatically.


@dataclass(frozen=True)
class Gap:
    """One documented reconstruction gap.

    Args:
        why: What the wire loses, in enough detail to fix it.
        characters: Restrict to these characters; ``None`` means all of them.
        requires: Only known when the same divergence ALSO names this path —
            for fields that are legitimate downstream effects of another gap
            but would be alarming on their own.
    """

    why: str
    characters: tuple[str, ...] | None = None
    requires: str | None = None


#: ``normalized path -> Gap``.
#:
#: Classification is PER PATH, not per divergence: a divergence is known only
#: when every field it names is listed here for that character. Any other
#: field — ``$.player.energy``, ``$.player.block``, ``$.player.potions``,
#: ``$.phase``, a transplant refusal, a choice mismatch — is unlisted and
#: fails the sweep, which is what makes this a gate rather than a rubber stamp.
KNOWN_RECONSTRUCTION_GAPS: dict[str, Gap] = {
    "$.encounter[].intents": Gap(
        "Enemy AI re-roll. The wire carries the current telegraph and a "
        "debug-only `move_state` string, but not the AI's internal counters or "
        "its `IntentHistory` (a private shape, so `state_from_dict` builds a "
        "default one). A reconstruction therefore rolls its next intent off a "
        "different history than the native engine did."
    ),
    "$.encounter[].block": Gap(
        "Downstream of `$.encounter[].intents`: a mispredicted Defend "
        "telegraph blocks for a different amount when it performs."
    ),
    "$.encounter[].hp": Gap(
        "Downstream of the intent gap and of the per-instance card state below "
        "— the enemy took a different amount of damage than predicted. Also "
        "covers random-target effects (the Defect's Lightning orb passive) "
        "landing on a different enemy."
    ),
    "$.encounter[].is_dead": Gap(
        "Downstream of `$.encounter[].hp`: the damage difference crossed zero.",
        requires="$.encounter[].hp",
    ),
    "$.encounter[].powers": Gap(
        "Downstream of `$.encounter[].intents`: a mispredicted Buff/Debuff "
        "telegraph applies different stacks.",
        requires="$.encounter[].intents",
    ),
    "$.deck.hand": Gap(
        "Draw order. The transient cost gap that used to dominate this bucket "
        "is closed (`base_cost` + `combat_vars` now travel on the wire), and "
        "what remains is the pile ORDER after a shuffle the two sides rolled "
        "independently — the same root as `$.deck.draw_pile`, seen through "
        "what got drawn."
    ),
    "$.deck.discard_pile": Gap("Same draw-order root as `$.deck.hand`."),
    "$.deck.exhaust_pile": Gap("Same draw-order root as `$.deck.hand`."),
    "$.deck.draw_pile": Gap("Same draw-order root as `$.deck.hand`."),
    "$.player.hp": Gap(
        "Necrobinder's Osty — the CrimsonMantle half of this bucket is closed "
        "now that `Power::counter` is on the wire. `$.player.osty` is parsed "
        "and validated but never applied by `state_from_dict_impl`, so a "
        "reconstruction has no pet at all — DieForYou redirects, Osty block "
        "and the resurrect loop vanish, "
        "and the player absorbs damage the native engine sent to the pet.",
        characters=("Necrobinder",),
    ),
    "$.player.hp#after-a-kill": Gap(
        "On any character, an enemy the mirror killed and the world did not "
        "(or vice versa) attacks one fewer time, so the player's HP follows. "
        "Recognized ONLY alongside `$.encounter[].is_dead`: player HP is the "
        "most load-bearing observable there is, and a bare drift in it is a "
        "bug, not a known gap.",
        requires="$.encounter[].is_dead",
    ),
    "$.player.energy": Gap(
        "Downstream of the per-instance card-state flattening above: a card "
        "whose `cost_override` came back as a bare number costs a different "
        "amount to play, and the energy left over follows it.\n\n"
        "Newly VISIBLE rather than newly caused. `reconcile` used to skip the "
        "entire observable diff after an advance that crossed a shuffle, "
        "which suppressed ~13.5% of reconciles wholesale; the skip is now "
        "scoped to `$.deck.*` (the only paths a reshuffle actually makes "
        "unverifiable), so energy is measured on those decisions too."
    ),
    "$.transplant": Gap(
        "Necrobinder turn-start draw count. The mirror finishes an `end_turn` "
        "with six cards in draw where the world has five, so "
        "`transplant_hidden` refuses to translate pins by position and the "
        "reconcile reports the refusal instead of a field list.\n\n"
        "NOT a lost or duplicated card: the pile TOTALS match exactly (12 vs "
        "12 on Necrobinder seed 2) and only the draw/hand split differs — the "
        "mirror drew one card fewer at turn start. The specific effect "
        "responsible has not been isolated; it is listed so the sweep gates "
        "on anything NEW rather than on this, and it sits squarely in the "
        "territory the mirror-fidelity work that follows this commit is "
        "chasing.",
        characters=("Necrobinder",),
    ),
}


def is_known(path: str, character: str, divergence: set[str]) -> bool:
    """Is this normalized field path documented, in this divergence's context?

    ``divergence`` is the full normalized path set, so a :attr:`Gap.requires`
    entry can insist that a downstream field only counts as known when the
    field it is downstream *of* diverged too.
    """
    for key in (path, f"{path}#after-a-kill"):
        gap = KNOWN_RECONSTRUCTION_GAPS.get(key)
        if gap is None:
            continue
        if gap.characters is not None and character not in gap.characters:
            continue
        if gap.requires is not None and gap.requires not in divergence:
            continue
        return True
    return False


@dataclass
class OracleReport:
    """What one sweep saw."""

    runs: int = 0
    clean_runs: int = 0
    decisions: int = 0
    reconciles: int = 0
    injections: int = 0
    reshuffle_skips: int = 0
    divergences: int = 0
    by_signature: Counter[str] = field(default_factory=Counter)
    seeds_by_signature: dict[str, list[str]] = field(default_factory=dict)
    #: Signatures with no :data:`KNOWN_RECONSTRUCTION_GAPS` entry. Must be
    #: empty — each one is a live mirror-machinery bug.
    unknown: dict[str, list[str]] = field(default_factory=dict)

    def note(self, character: str, label: str, paths: list[str]) -> None:
        normalized = normalize(paths)
        sig = " + ".join(normalized) or "<no fields>"
        self.divergences += 1
        self.by_signature[sig] += 1
        self.seeds_by_signature.setdefault(sig, []).append(label)
        context = set(normalized)
        novel = [p for p in normalized if not is_known(p, character, context)]
        if novel or not normalized:
            self.unknown.setdefault(sig, []).append(label)

    def render(self) -> str:
        lines = [
            f"runs={self.runs} clean={self.clean_runs} decisions={self.decisions} "
            f"reconciles={self.reconciles} injections={self.injections} "
            f"reshuffle_skips={self.reshuffle_skips} divergences={self.divergences}",
        ]
        for sig, n in self.by_signature.most_common():
            mark = "UNKNOWN" if sig in self.unknown else "known"
            seeds = ", ".join(self.seeds_by_signature[sig][:8])
            lines.append(f"  [{mark}] x{n:<4} {sig}")
            lines.append(f"           seeds: {seeds}")
        return "\n".join(lines)


def combat_action_to_run_action(a: dict[str, Any]) -> dict[str, Any]:
    """Combat-shape action → ``RunManager.take_action`` shape.

    Deliberately not ``run_policy.combat_action_to_run_action``: that one emits
    ``combat_resolve_choice`` with a single ``option``, which cannot express
    "skip" separately from "pick 0". The oracle needs the world to make the
    *exact* pick it hands the mirror, so it uses the explicit-index
    ``combat_resolve_choice_selection`` verb.
    """
    kind = a["action"]
    if kind == "play":
        return {
            "action": "play_card",
            "hand_idx": a["card_index"],
            "target_index": a.get("target_index"),
        }
    if kind == "end_turn":
        return {"action": "end_turn"}
    if kind in ("potion", "drink_potion"):
        return {
            "action": "drink_potion",
            "potion_slot": a["slot"],
            "target_index": a.get("target_index"),
        }
    if kind == "choose":
        # Read `selection` when present. `[a["index"]]` was the whole story
        # before, which broke the docstring's own promise twice over:
        # `legal_actions` emits `index = selection.first().unwrap_or(0)`
        # (sts2-py/src/lib.rs:1631-1637), so a SKIP (`selection: []`) arrived
        # as `index=0` — handed to the world as "pick option 0", exactly the
        # conflation this verb was chosen to avoid — and every multi-pick was
        # truncated to its first index, so the oracle never drove a real
        # multi-pick end to end.
        selection = a.get("selection")
        if selection is None:
            selection = [a["index"]]
        return {"action": "combat_resolve_choice_selection", "selection": list(selection)}
    raise AssertionError(f"oracle: unexpected combat action {a!r}")


def drive_run(
    seed: int,
    character: str,
    mirror: CombatMirror,
    *,
    max_steps: int = 1500,
) -> int:
    """Play one native run with random combat play, mirroring every fight.

    Random play is deliberate: it reaches a far wider spread of boards per
    second than the deployed policy — reshuffles, multi-enemy fights,
    mid-combat card selects, deaths — and the oracle is testing state
    machinery, not play quality.

    Returns:
        The number of fights entered.

    Raises:
        MirrorDivergence: Under ``STRICT``, at the first divergence.
    """
    import sts2_rs
    from run_policy import heuristic_run_action

    rm = sts2_rs.RunManager(seed=seed, character=character)
    rng = random.Random(seed ^ 0xC0FFEE)
    fights = 0
    last_fingerprint: int | None = None

    for _ in range(max_steps):
        if rm.is_over():
            break
        if rm.phase() != "combat":
            if mirror.alive:
                mirror.drop("combat ended")
            actions = rm.available_actions()
            if not actions:
                raise AssertionError(f"run stalled out of combat at phase={rm.phase()}")
            rm.take_action(heuristic_run_action(rm, actions, rng))
            last_fingerprint = None
            continue

        cs = rm.combat_state()
        wire = sts2_rs.state_to_dict(cs)
        if not mirror.alive:
            fights += 1
        mirror.reconcile(wire)

        parked = cs.pending_choice()
        if parked is not None and mirror.alive:
            mirror.inject_choice(
                parked["kind"],
                parked["cards"],
                parked["min_select"],
                parked["max_select"],
                can_skip=parked["can_skip"],
            )

        legal = cs.legal_actions()
        if not legal:
            break
        # Anti-stall backstop, same shape as `RunStepper`'s: the engine reports
        # "advanced" for plays it silently ignores (dead-slot target, a
        # once-per-turn gate), so an unchanged fingerprint means the last action
        # was a no-op. Ending the turn always advances.
        fingerprint = hash(json.dumps(wire, sort_keys=True, default=str))
        if fingerprint == last_fingerprint and parked is None:
            action = {"action": "end_turn"}
        else:
            action = rng.choice(legal)
        last_fingerprint = fingerprint

        rm.take_action(combat_action_to_run_action(action))
        # Under OBSERVE a divergence drops the mirror and the next reconcile
        # re-seeds it from reality. STRICT never reaches this branch.
        if not mirror.alive:
            continue
        if action["action"] == "choose":
            # Same `selection`-first read as `combat_action_to_run_action`:
            # the mirror must be advanced through the SAME pick the world
            # just made, so a skip stays a skip and a multi-pick stays whole.
            picks = action.get("selection")
            mirror.advance_through_choice(list(picks) if picks is not None else [action["index"]])
        else:
            mirror.advance(action)
    return fights


def sweep(
    characters: tuple[str, ...] = CHARACTERS,
    seeds: range | list[int] = range(1, 21),
    *,
    max_steps: int = 1500,
    log_path: str | None = None,
) -> OracleReport:
    """Run the matrix under OBSERVE and classify every divergence.

    OBSERVE rather than STRICT because a sweep wants the *inventory*: a STRICT
    mirror stops at the first divergence and the rest of the run goes unmeasured.
    """
    report = OracleReport()
    for character in characters:
        for seed in seeds:
            label = f"{character}-{seed}"
            mirror = CombatMirror(
                ORACLE_CFG, policy=MirrorPolicy.OBSERVE, label=label, log_path=log_path
            )
            # Only the FIRST divergence of a run is signal. OBSERVE drops the
            # mirror and re-seeds it from reality, so everything after is
            # downstream of a board the mirror never predicted — and a STRICT
            # run (what the gate models) would have stopped at the first one
            # anyway. Counting the cascade would drown the inventory in
            # composite signatures that name no distinct bug.
            seen: list[bool] = []

            def once(
                paths: list[str],
                _c: str = character,
                _l: str = label,
                _seen: list[bool] = seen,
            ) -> None:
                if not _seen:
                    _seen.append(True)
                    report.note(_c, _l, paths)

            mirror.on_divergence = once
            drive_run(seed, character, mirror, max_steps=max_steps)
            report.runs += 1
            report.clean_runs += int(not mirror.tainted)
            report.decisions += mirror.stats.decisions
            report.reconciles += mirror.stats.reconciles
            report.injections += mirror.stats.injections
            report.reshuffle_skips += mirror.stats.reshuffle_skips
    return report


def _parse_seeds(text: str) -> list[int]:
    out: list[int] = []
    for part in text.split(","):
        part = part.strip()
        if "-" in part:
            lo, hi = part.split("-", 1)
            out.extend(range(int(lo), int(hi) + 1))
        else:
            out.append(int(part))
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--characters", default=",".join(CHARACTERS))
    ap.add_argument("--seeds", default="1-20")
    ap.add_argument("--max-steps", type=int, default=1500)
    ap.add_argument("--log", default=None, help="forensic JSONL path for every divergence")
    args = ap.parse_args()

    report = sweep(
        characters=tuple(c.strip() for c in args.characters.split(",")),
        seeds=_parse_seeds(args.seeds),
        max_steps=args.max_steps,
        log_path=args.log,
    )
    print(report.render())
    if report.unknown:
        print("\nUNCLASSIFIED divergences — these are mirror-machinery bugs:")
        for sig, seeds in report.unknown.items():
            print(f"  {sig}: {', '.join(seeds[:12])}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
