"""``CombatReplayBackend`` — a combat-only :class:`~workbench.backends.GameBackend`
over a single ``sts2_rs`` ``PyCombatState``.

Powers the workbench "play from here" feature: take over a *recorded* fight
interactively from an exact decision. The state is materialized by reloading
the source fixture (``combat_state_from_scenario_toml`` is deterministic) and
replaying the recorded prefix actions, so it is bit-identical to what the
recorder saw — including the RNG cursor and intent history.

Everything except that materialization lives in
:class:`~workbench.backends.single_combat.SingleCombatBackend`, which
:class:`~workbench.backends.imported_combat.ImportedCombatBackend` shares.
"""

from __future__ import annotations

from typing import Any

from workbench.backends.single_combat import SingleCombatBackend


class CombatReplayBackend(SingleCombatBackend):
    """Drive one combat from a recorded decision (reload fixture + replay prefix).

    Bound at construction to ``(fixture_path, prefix_actions, character)``;
    :meth:`materialize` rebuilds the exact state. The binding lets
    ``BranchingLiveSession.seek`` reconstruct the session via a zero-arg
    ``functools.partial`` factory.
    """

    def __init__(
        self, fixture_path: str, prefix_actions: list[dict[str, Any]], character: str
    ) -> None:
        super().__init__(character)
        self._fixture_path = fixture_path
        self._prefix = [dict(a) for a in prefix_actions]

    def materialize(self) -> Any:
        import sts2_rs

        cs = sts2_rs.combat_state_from_scenario_toml(self._fixture_path)
        for action in self._prefix:
            cs.apply(action)
        return cs
