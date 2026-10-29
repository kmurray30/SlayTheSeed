"""Phase-dispatched action driver scaffold — bead `sts2-rs-p0jzo` (W7).

Given a :class:`replay.scraped.Floor` and a Rust ``RunManager``
handle, :func:`apply_floor` emits the actions the scraper implies for
that floor (move to the floor's column, dispatch event/shop/reward
choices, hand off combat to MCTS) and returns the engine ``take_action``
result list verbatim.

This module is a **scaffold** at W7: it documents the phase-dispatch
contract and exposes a thin entry point so W8 (``act1_replay.py``,
bead ``sts2-rs-cr5xs``) can iterate, but the full per-phase wiring is
W8's domain. The four scraper choice arrays the driver consumes are:

- ``ancient_choice`` (Act-1 first room — relic pick by TextKey)
- ``card_choices`` (CardReward — picked card id via
  ``RunManager.card_reward_option_ids``)
- ``event_choices`` (Event — TextKey tail → option idx via
  ``RunManager.event_options``)
- ``relic_choices`` / ``potion_choices`` (Treasure + post-elite +
  shop — relic / potion picks)

Combat floors hand off via ``RunManager.mcts_pick_action`` (W6); the
driver loops MCTS picks until the engine reports phase != ``combat``.

The pins for each are in CLAUDE.md and the corresponding W-bead
descriptions; this file's job is to *route* the floor's choices to
the right engine surface — not to re-implement the parity logic.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from .scraped import Floor


@dataclass(frozen=True)
class DispatchResult:
    """Outcome of :func:`apply_floor`.

    ``actions`` is the list of action dicts the driver fed to
    ``RunManager.take_action`` (in dispatch order — useful when the
    harness wants to replay just the engine side of one floor).
    ``results`` is the parallel list of ``ActionResult`` strings the
    engine returned (``"advanced"``, ``"shop_purchase_failed"``, …).
    ``phase_after`` is the engine's phase string post-dispatch — the
    harness uses it to confirm the floor closed (e.g. a Monster floor
    should exit at ``"card_reward"`` or ``"map_choice"``).
    """

    actions: list[dict[str, Any]]
    results: list[str]
    phase_after: str


def apply_floor(run_manager: Any, floor: Floor) -> DispatchResult:
    """Dispatch the actions implied by ``floor`` against
    ``run_manager``.

    This W7 entry point is intentionally minimal: it documents the
    phase-dispatch contract (see the module docstring) and lets
    callers stand up the rest of the W8 driver around a stable
    function signature. The body currently emits a single
    ``no-op`` placeholder action set and returns the engine's
    current phase — once the per-phase routers land (W3/W4/W5/W6
    bead follow-ups already merged), they slot into the
    ``# TODO(W8)`` branches below without changing the surface.

    Bead `sts2-rs-p0jzo` (W7). Full driver wiring tracked under
    `sts2-rs-cr5xs` (W8).
    """
    actions: list[dict[str, Any]] = []
    results: list[str] = []

    room = floor.map_point_type
    if room in {"monster", "elite", "boss"}:
        # TODO(W8): loop `mcts_pick_action(sims=1000, depth=10)` until
        # `run_manager.phase() != "combat"`. Bead `sts2-rs-yb3cm`.
        pass
    elif room == "event":
        # TODO(W8): map scraped event_choices[*] TextKey tails to idx
        # via `run_manager.event_options()` and dispatch
        # `{"action": "event_choice", "idx": <i>}`. Bead `sts2-rs-h9qn3`.
        pass
    elif room == "shop":
        # TODO(W8): replay buy_card / buy_relic / buy_potion / remove
        # entries from raw_player_stats (`shop_purchases`/`gold_spent`
        # break-down). New typed shop_purchase_failed results
        # (sts2-rs-8he0t) feed the divergence dump on rejected buys.
        pass
    elif room == "rest_site":
        # TODO(W8): RestOption picker; smith sub-state via SmithCard{idx}
        # if scraped upgraded_cards is non-empty. Bead `sts2-rs-5abnx`.
        pass
    elif room == "treasure":
        # TODO(W8): one `collect_treasure`, then a `pick_relic_reward`
        # / `skip_relic` per scraped relic_choices entry.
        pass
    elif room == "ancient":
        # TODO(W8): Neow-room — pick the ancient_choice option index
        # whose TextKey matches the scraped `was_chosen=true` entry.
        pass
    # CardReward floors (after monster/elite combats) are routed
    # implicitly by the engine's phase transition; the driver only
    # needs to dispatch `pick_card_reward{idx}` from card_choices,
    # which W8 will plumb in.

    phase_after = run_manager.phase() if hasattr(run_manager, "phase") else "unknown"
    return DispatchResult(actions=actions, results=results, phase_after=phase_after)
