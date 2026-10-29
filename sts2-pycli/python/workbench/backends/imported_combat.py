"""``ImportedCombatBackend`` — a combat-only backend over a board copied out of
the live game.

The other half of the live-state import (see
:mod:`workbench.state_import`): where the run lane rebuilds the position on the
map, this rebuilds the *fight*. The wire dict carries the hand, the
draw/discard/exhaust piles in order, every power, every intent, the energy and
the block, plus the game's own RNG lineage — so
``sts2_rs.state_from_dict`` returns a board that is bit-identical to the one on
screen, not a look-alike.

That makes it a true counterfactual surface: undo a play, take the other line,
and the only thing that changed is the decision.
"""

from __future__ import annotations

import copy
from typing import Any

from workbench.backends.single_combat import SingleCombatBackend


class ImportedCombatBackend(SingleCombatBackend):
    """Drive one combat rebuilt from an engine wire dict.

    Bound at construction to ``(wire, character)``. ``state_from_dict`` is a
    pure function of the wire, so :meth:`materialize` is deterministic and
    ``BranchingLiveSession.seek`` can reconstruct the backend through a
    zero-arg ``functools.partial`` factory and replay onto it.

    Args:
        wire: An ``sts2_rs.state_to_dict``-shaped dict — what
            :func:`workbench.state_import.parse_pasted_state` returns as
            ``combat_wire``.
        character: Display character name for the rendered board.
    """

    def __init__(self, wire: dict[str, Any], character: str) -> None:
        super().__init__(character)
        # Deep-copied once at construction: the session holds this backend for
        # the life of the branch tree and re-materializes from it on every
        # seek, so a caller mutating the dict it handed us afterwards would
        # silently change what "the imported state" means.
        self._wire = copy.deepcopy(wire)

    def materialize(self) -> Any:
        import sts2_rs

        return sts2_rs.state_from_dict(self._wire)
