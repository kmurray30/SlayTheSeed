"""State readers and the clone-based action probe.

The probe is what keeps agents deck-agnostic: instead of a card-effect
database, we apply a candidate action to a clone and diff block / enemy
HP / energy. Exact for the cards it measures (including Strength,
Vulnerable, tags), cheap enough at heuristic-agent scale.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import sts2_rs


def alive_enemies(state: dict[str, Any]) -> list[dict[str, Any]]:
    """Return enemies that are not dead."""
    return [e for e in state["encounter"] if not e["is_dead"]]


def incoming_damage(state: dict[str, Any]) -> int:
    """Sum of telegraphed attack damage across alive enemies this turn.

    Reads `amount * times` from Attack intents. NOTE: whether `amount`
    is pre- or post- strength/vulnerable is an engine detail — agents
    treat this as an estimate.

    Args:
        state: State dict as returned by sts2_rs.state_to_dict().

    Returns:
        Total incoming attack damage from alive enemies.
    """
    total = 0
    for e in alive_enemies(state):
        for intent in e["intents"]:
            if intent.get("kind") == "Attack":
                total += int(intent.get("amount", 0)) * int(intent.get("times", 1))
    return total


@dataclass(frozen=True)
class ActionEffect:
    """Observed single-action effect, measured on a clone.

    Fields are signed deltas vs the pre-action state. Probing `end_turn`
    can yield negative `block_gain` (block cleared) and non-positive
    `energy_spent` (energy refills next turn). Agents must not assume
    non-negative values.
    """

    block_gain: int
    enemy_hp_loss: int
    energy_spent: int
    kills: int


def _enemy_hp_total(state: dict[str, Any]) -> int:
    # Include dead enemies (clamped to 0) so the before/after diff is
    # purely damage dealt.
    return sum(max(0, e["hp"]) for e in state["encounter"])


def probe_action(
    combat: Any,
    action: dict[str, Any],
    *,
    before_state: dict[str, Any] | None = None,
) -> ActionEffect:
    """Apply `action` to a clone of `combat` and diff the result.

    Args:
        combat: A sts2_rs CombatState instance.
        action: Action dict matching sts2_rs legal_actions() format.
        before_state: Optional pre-computed state dict for `combat`, so
            callers can hoist the snapshot out of per-action loops. If
            None, it is read from `combat`.

    Returns:
        ActionEffect with block_gain, enemy_hp_loss, energy_spent, kills
        measured against the pre-action state. Does not mutate `combat`.
    """
    before = before_state if before_state is not None else sts2_rs.state_to_dict(combat)
    clone = combat.clone()
    clone.apply(action)
    after = sts2_rs.state_to_dict(clone)
    return ActionEffect(
        block_gain=after["player"]["block"] - before["player"]["block"],
        enemy_hp_loss=_enemy_hp_total(before) - _enemy_hp_total(after),
        energy_spent=before["player"]["energy"] - after["player"]["energy"],
        kills=len(alive_enemies(before)) - len(alive_enemies(after)),
    )
