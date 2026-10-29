"""Common-random-numbers A/B, judged on per-decision STABILITY.

Plan: ``2026-07-24 persistent combat mirror`` (Phase 5).

What CRN is
===========

Every stochastic edge off a decision node (a draw, a shuffle) normally gets
its own reseed stream, so two sibling actions are compared under *different*
luck: "attack" may be scored on a lucky draw and "block" on an unlucky one,
and the difference between them is partly noise. Common random numbers give
the siblings ONE shared ``randomness_base``, so the comparison is
like-for-like and the variance that survives is the variance that is actually
about the choice.

Why this metric and not win rate
================================

The plan is explicit: judge CRN on flip rate and modal-share instability, not
on a 60-seed win rate. A variance-reduction technique moves the *noise floor*
of a decision, and a 60-run win rate has a noise floor of its own that is far
wider than the effect — such a comparison could not distinguish "CRN helped"
from "CRN did nothing" either way, so running it would produce a number with
no information in it.

So: hold the position fixed, vary only the search seed, and ask how often the
search changes its mind.

* **modal share** — the fraction of searches that picked the most popular
  action. 1.0 means the search is certain; 0.5 on a two-way choice means a
  coin flip.
* **flip rate** — ``1 - modal share``, i.e. how often the search disagreed
  with its own favourite.

CRN should RAISE modal share. It is not obliged to change which action wins.

Caveat carried from the plan: CRN's matched-luck argument is weaker here than
in the lightspeed original. After two different card plays the deck
composition itself differs, so a shared reseed stream is an approximation
rather than a true coupling — which is exactly why this is a flag with an A/B
behind it rather than a default.

Usage::

    python python/diag_decision_stability.py --seeds 1043,1007,1047
"""

from __future__ import annotations

import argparse
import json
import random
from collections import Counter
from dataclasses import dataclass, field
from statistics import mean
from typing import Any

from run_policy import heuristic_run_action

#: The plan's seeds — picked for their pivotal block-vs-push turns.
DEFAULT_SEEDS = (1043, 1007, 1047)


def _action_key(action: dict[str, Any]) -> str:
    """A search result's chosen action, as a comparable label."""
    kind = action.get("action")
    if kind == "play":
        return f"play:{action.get('card_index')}@{action.get('target_index')}"
    if kind in ("potion", "drink_potion"):
        return f"potion:{action.get('slot')}@{action.get('target_index')}"
    if kind == "choose":
        return f"choose:{action.get('index')}"
    return str(kind)


def _best_action(result: dict[str, Any]) -> dict[str, Any] | None:
    best = None
    most_visited, max_visits = None, -1
    for entry in result.get("actions", []):
        if entry.get("is_best"):
            best = entry
        visits = int(entry.get("visits", 0))
        if visits > max_visits:
            max_visits, most_visited = visits, entry
    chosen = best or most_visited
    return chosen.get("action") if chosen else None


@dataclass
class Stability:
    """One arm of the A/B."""

    label: str
    modal_shares: list[float] = field(default_factory=list)
    #: Per-decision modal action, so the two arms can be compared on WHICH
    #: action they settle on, not only on how firmly.
    modal_actions: list[str] = field(default_factory=list)

    @property
    def mean_modal_share(self) -> float:
        return mean(self.modal_shares) if self.modal_shares else 0.0

    @property
    def mean_flip_rate(self) -> float:
        return 1.0 - self.mean_modal_share

    #: Decisions the search never settled on at all (modal share <= 0.5) —
    #: the ones where seed choice, not position, decides the move.
    @property
    def coin_flips(self) -> int:
        return sum(1 for s in self.modal_shares if s <= 0.5)


def measure_state(
    state: Any,
    *,
    trials: int,
    sims: int,
    crn: bool,
) -> tuple[float, str] | None:
    """Search one position ``trials`` times under different seeds.

    Returns ``(modal share, modal action)``, or None when the position has
    fewer than two legal actions — a forced move has nothing to be unstable
    about and would only dilute the average with 1.0s.
    """
    import sts2_rs

    if len(state.legal_actions()) < 2:
        return None
    picks: Counter[str] = Counter()
    for t in range(trials):
        result = sts2_rs.mcts_recommend(
            state.clone(),
            sims=sims,
            c_puct=2.5,
            depth=200,
            seed=t,
            threads=1,
            hidden_info="beliefdraw",
            draw_widening_c=2.0,
            common_random_numbers=crn,
        )
        action = _best_action(result)
        if action is not None:
            picks[_action_key(action)] += 1
    if not picks:
        return None
    action, n = picks.most_common(1)[0]
    return n / sum(picks.values()), action


def collect_positions(
    seed: int,
    character: str,
    *,
    limit: int,
    max_steps: int = 1500,
) -> list[Any]:
    """Play one run and snapshot its combat positions.

    Random play rather than the deployed policy: it reaches a wider spread of
    boards per second, and stability is a property of the POSITION, not of how
    the position was arrived at.
    """
    import sts2_rs

    rm = sts2_rs.RunManager(seed=seed, character=character)
    rng = random.Random(seed ^ 0xC0FFEE)
    out: list[Any] = []
    last_fingerprint: int | None = None

    for _ in range(max_steps):
        if rm.is_over() or len(out) >= limit:
            break
        if rm.phase() != "combat":
            actions = rm.available_actions()
            if not actions:
                break
            rm.take_action(heuristic_run_action(rm, actions, rng))
            last_fingerprint = None
            continue

        cs = rm.combat_state()
        legal = cs.legal_actions()
        if not legal:
            break
        if cs.pending_choice() is None and len(legal) >= 2:
            out.append(cs.clone())
        # Same anti-stall backstop the oracle uses: an unchanged fingerprint
        # means the engine silently ignored the last action.
        fingerprint = hash(json.dumps(sts2_rs.state_to_dict(cs), sort_keys=True, default=str))
        action = (
            {"action": "end_turn"}
            if fingerprint == last_fingerprint and cs.pending_choice() is None
            else rng.choice(legal)
        )
        last_fingerprint = fingerprint
        rm.take_action(_to_run_action(action))
    return out


def _to_run_action(a: dict[str, Any]) -> dict[str, Any]:
    kind = a["action"]
    if kind == "play":
        return {
            "action": "play_card",
            "hand_idx": a["card_index"],
            "target_index": a.get("target_index"),
        }
    if kind in ("potion", "drink_potion"):
        return {
            "action": "drink_potion",
            "potion_slot": a["slot"],
            "target_index": a.get("target_index"),
        }
    if kind == "choose":
        return {"action": "combat_resolve_choice_selection", "selection": [a["index"]]}
    return {"action": "end_turn"}


def run(
    seeds: tuple[int, ...],
    character: str,
    *,
    trials: int,
    sims: int,
    per_seed: int,
) -> tuple[Stability, Stability]:
    off = Stability("crn=off")
    on = Stability("crn=on")
    for seed in seeds:
        positions = collect_positions(seed, character, limit=per_seed)
        print(f"seed {seed}: {len(positions)} multi-action combat positions")
        for pos in positions:
            a = measure_state(pos, trials=trials, sims=sims, crn=False)
            b = measure_state(pos, trials=trials, sims=sims, crn=True)
            if a is None or b is None:
                continue
            off.modal_shares.append(a[0])
            off.modal_actions.append(a[1])
            on.modal_shares.append(b[0])
            on.modal_actions.append(b[1])
    return off, on


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--seeds", default=",".join(str(s) for s in DEFAULT_SEEDS))
    ap.add_argument("--character", default="Ironclad")
    ap.add_argument("--trials", type=int, default=8, help="searches per position per arm")
    ap.add_argument("--sims", type=int, default=200)
    ap.add_argument("--per-seed", type=int, default=25, help="positions sampled per seed")
    args = ap.parse_args(argv)

    seeds = tuple(int(s) for s in args.seeds.split(","))
    off, on = run(
        seeds,
        args.character,
        trials=args.trials,
        sims=args.sims,
        per_seed=args.per_seed,
    )

    n = len(off.modal_shares)
    print(f"\npositions measured: {n}  (x{args.trials} searches x 2 arms, {args.sims} sims)")
    if not n:
        print("no positions — nothing to compare")
        return 1
    print(f"{'arm':<10} {'modal share':>12} {'flip rate':>11} {'coin flips':>11}")
    for arm in (off, on):
        print(
            f"{arm.label:<10} {arm.mean_modal_share:>12.3f} "
            f"{arm.mean_flip_rate:>11.3f} {arm.coin_flips:>11}"
        )
    delta = on.mean_modal_share - off.mean_modal_share
    changed = sum(1 for a, b in zip(off.modal_actions, on.modal_actions, strict=True) if a != b)
    print(f"\nmodal-share delta (on - off): {delta:+.3f}")
    print(f"positions whose modal ACTION changed: {changed}/{n}")
    print(
        "\nCRN is meant to RAISE modal share (less seed-driven churn). A delta "
        "within +-0.01 on this sample size is noise, not an effect."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
