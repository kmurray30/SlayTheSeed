"""Agent contract: a stateless callable from DecisionContext to one legal action."""

from __future__ import annotations

import random
from collections.abc import Callable
from dataclasses import dataclass, field
from typing import Any

import sts2_rs

Action = dict[str, Any]


@dataclass
class DecisionContext:
    """Everything an agent may look at for one decision.

    `state` is snapshotted lazily: the first agent that reads `ctx.state`
    triggers the `sts2_rs.state_to_dict(combat)` serialization, and the result
    is cached. Agents that never touch `state` — notably `mcts_agent`, which
    searches over `ctx.combat` directly — pay nothing for it. At ~tens of
    decisions per second across a sweep, eagerly serializing a dict the MCTS
    agent throws away was pure waste.

    Attributes:
        legal_actions: The actions the agent must choose from.
        combat: Live sts2_rs.CombatState, for clone-based lookahead.
        rng: Per-run random source (agents must not seed their own).
        state: Snapshot of the combat state (sts2_rs.state_to_dict), computed
            on first access. See the `state` property.
    """

    legal_actions: list[Action]
    combat: Any  # live sts2_rs.CombatState — for clone-based lookahead
    rng: random.Random
    _state: dict[str, Any] | None = field(default=None, repr=False)

    @property
    def state(self) -> dict[str, Any]:
        """The combat snapshot, serialized on first access and cached."""
        s = self._state
        if s is None:
            s = sts2_rs.state_to_dict(self.combat)
            self._state = s
        return s

    @classmethod
    def from_combat(
        cls, combat: Any, legal_actions: list[Action], rng: random.Random
    ) -> DecisionContext:
        """Build a context for one decision (state snapshotted lazily)."""
        return cls(legal_actions=legal_actions, combat=combat, rng=rng)


Agent = Callable[[DecisionContext], Action]
