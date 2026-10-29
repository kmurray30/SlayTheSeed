"""A minimal ``sts2_rs.RunManager`` stand-in for driving ``RustGame`` through
its interface (``get_state`` / ``act``) without a real run.

Carries just enough run-wide state for the backend to render any decision
(player block, floor, act, ascension) and to accept an action (``take_action``
records the native dict and reports ``"advanced"``). A test subclasses it and
overrides the surface it cares about — ``phase``, ``available_actions`` and
the per-screen accessors — and asserts on the state the backend renders or
the native action it emitted.
"""

from __future__ import annotations

import copy
from typing import Any

#: The smallest ``build_combat_view`` result ``RustGame._fill_combat`` accepts.
MINIMAL_COMBAT_VIEW: dict[str, Any] = {
    "player": {"hp": 50, "max_hp": 80, "block": 0},
    "enemies": [],
    "player_powers": [],
    "round": 1,
    "energy": 3,
    "max_energy": 3,
    "hand": [],
    "draw_pile_count": 0,
    "discard_pile_count": 0,
    "exhaust_pile_count": 0,
    "draw_pile": [],
    "discard_pile": [],
    "exhaust_pile": [],
}


def stub_combat_view(cs: Any, character: str, legal: Any = None, **_: Any) -> dict[str, Any]:
    """Drop-in for ``rust_game.build_combat_view`` when no native combat state
    exists (monkeypatch it in tests that only exercise the surrounding view)."""
    return copy.deepcopy(MINIMAL_COMBAT_VIEW)


class FakeCombatState:
    """``rm.combat_state()`` stand-in: an optional open pending choice."""

    def __init__(self, pending_choice: dict[str, Any] | None = None) -> None:
        self._pending = pending_choice

    def pending_choice(self) -> dict[str, Any] | None:
        return self._pending


class FakeRm:
    def __init__(
        self, *, phase: str = "map_choice", actions: list[dict[str, Any]] | None = None
    ) -> None:
        self._phase = phase
        self._actions = list(actions or [])
        #: Every native action ``take_action`` received, oldest first.
        self.taken: list[dict[str, Any]] = []
        self.current_hp = 50
        self.max_hp = 80
        self.gold = 99
        self.act_index = 0
        self.ascension_level = 0
        self.act1 = "overgrowth"
        self.seed = 1
        self.player_won = False

    # -- what every decision view reads ------------------------------------
    def phase(self) -> str:
        return self._phase

    def available_actions(self) -> list[dict[str, Any]]:
        return list(self._actions)

    def take_action(self, native: dict[str, Any]) -> str:
        self.taken.append(dict(native))
        return "advanced"

    def total_floor(self) -> int:
        return 5

    def relics(self) -> list[str]:
        return []

    def potions(self) -> list[str]:
        return []

    def master_deck_detailed(self) -> list[tuple[str, bool]]:
        return []

    def max_potion_slots(self) -> int:
        return 3

    # -- per-screen accessors, overridden as needed ---------------------------
    def combat_state(self) -> Any:
        return None

    def rest_site_options(self) -> list[tuple[str, bool]]:
        return []

    def smith_options(self) -> list[str]:
        return []

    def card_reward_bundles(self) -> list[list[str]]:
        return []

    def card_removal_options(self) -> list[int]:
        return []

    def full_map(self) -> dict[str, Any]:
        return {}

    def map_coord(self) -> tuple[int, int] | None:
        return None
