"""``SingleCombatBackend`` — a combat-only :class:`~workbench.backends.GameBackend`
over one ``sts2_rs`` ``PyCombatState``.

Everything such a backend does *except decide where the state came from*. Two
subclasses supply that:

* :class:`~workbench.backends.combat_replay.CombatReplayBackend` reloads a
  recorded fixture and replays a prefix ("play from here" on a recording).
* :class:`~workbench.backends.imported_combat.ImportedCombatBackend` rebuilds a
  board copied out of the live game.

Both are combat-only: there is no run or map behind the state, so play ends at
the fight's resolution (``game_over``) rather than continuing the run.

Because this satisfies the same ``GameBackend`` surface as
:class:`~workbench.backends.rust_game.RustGame`, the existing
:class:`~workbench.branching.BranchingLiveSession` drives it unchanged — branch
tree, seek/fork, and the native MCTS hint (via :meth:`combat_state`) all work.
Subclasses must therefore keep :meth:`materialize` **deterministic**: ``seek``
reconstructs the backend through a zero-arg factory and replays, so a state
that differs between two materializations silently forks the branch tree.
"""

from __future__ import annotations

from typing import Any

from workbench.backends.base import GameBackend
from workbench.backends.rust_game import build_combat_view, fill_combat_choice


class SingleCombatBackend(GameBackend):
    """Drive one combat state through the :class:`GameBackend` surface.

    Subclasses implement :meth:`materialize` and are bound at construction to
    whatever that needs, so ``start``'s run params can be ignored. Every
    optional capability the base declares — a native run, run-mode start,
    an out-of-combat policy — stays at its "not available" default: there is
    no run behind a single combat to offer them from.
    """

    def __init__(self, character: str) -> None:
        self.character = character or "Ironclad"
        self._cs: Any = None

    # -- subclass hook -----------------------------------------------------
    def materialize(self) -> Any:
        """Build the ``sts2_rs`` combat state this backend drives.

        Must be deterministic — see the module docstring.
        """
        raise NotImplementedError

    # -- GameBackend surface ----------------------------------------------
    # Every run parameter below is accepted and ignored: the bound source
    # fixes the state, and there is no run or act behind a single combat.
    def start(
        self,
        *,
        character: str = "",
        seed: str = "",
        ascension: int = 0,
        act1: str = "overgrowth",
    ) -> dict[str, Any]:
        """Materialize the combat state and return the opening board.

        The run params are ignored — the bound source fully determines the
        state — so ``BranchingLiveSession`` can call ``start`` after a zero-arg
        reconstruction during ``seek``.
        """
        self._cs = self.materialize()
        return self._serialize()

    def act(self, action: str, **args: Any) -> dict[str, Any]:
        """Apply one combat verb; return the new state or an error dict.

        Illegal/unsupported moves return ``{"type": "error", ...}`` (never
        raise), per the ``GameBackend`` protocol.
        """
        if self._cs is None:
            return {"type": "error", "message": "no combat state"}
        native = self._resolve_legal_action(action, args)
        if native is None:
            return {"type": "error", "message": f"illegal or unsupported move: {action} {args}"}
        try:
            self._cs.apply(native)
        except Exception as exc:  # defensive: engine rejected a "legal" move
            return {"type": "error", "message": str(exc)}
        return self._serialize()

    def get_state(self) -> dict[str, Any]:
        return self._serialize()

    def get_map(self, first_col: int | None = None) -> dict[str, Any]:
        return {}  # combat-only: no run/map context, so nothing to plan a route on

    def combat_state(self) -> Any:
        """The native combat state while the fight is live (drives MCTS hints);
        ``None`` once it is over so the session stops offering hints."""
        if self._cs is None:
            return None
        is_terminal, _ = self._cs.terminal_value()
        return None if is_terminal else self._cs

    def close(self) -> None:
        self._cs = None

    # -- translation + rendering ------------------------------------------
    def _resolve_legal_action(self, action: str, args: dict[str, Any]) -> dict[str, Any] | None:
        """Map a C# live verb to a matching entry from ``cs.legal_actions()``.

        Returns the canonical native action (so the engine's own target slot is
        used) or ``None`` if the move is illegal/unsupported. Validating against
        the legal set is required because the engine silently no-ops some
        illegal applies (e.g. an out-of-range ``card_index``) rather than
        raising, so a bare ``apply`` + try/except would not catch them.
        """
        legal = self._cs.legal_actions()

        def _match(pred: Any, target: int | None) -> dict[str, Any] | None:
            cands = [a for a in legal if pred(a)]
            if not cands:
                return None
            # Prefer an exact target match; else take the first and stamp the
            # requested target (single-enemy fights have one candidate anyway).
            for a in cands:
                if a.get("target_index") == target:
                    return a
            chosen = dict(cands[0])
            if target is not None:
                chosen["target_index"] = target
            return chosen

        if action == "play_card":
            ci = args.get("card_index")
            return _match(
                lambda a: a.get("action") == "play" and a.get("card_index") == ci,
                args.get("target_index"),
            )
        if action == "end_turn":
            return (
                {"action": "end_turn"}
                if any(a.get("action") == "end_turn" for a in legal)
                else None
            )
        if action == "use_potion":
            slot = args.get("potion_index")
            return _match(
                lambda a: a.get("action") in ("drink_potion", "potion") and a.get("slot") == slot,
                args.get("target_index"),
            )
        if action == "select_cards":
            # An open in-combat pending choice (Headbutt, Warcry, …); ``indices``
            # is the chosen option index, which maps 1:1 to ``choose``.
            raw = str(args.get("indices", "")).split(",")[0].strip()
            if not raw:
                return None
            want = int(raw)
            return next(
                (a for a in legal if a.get("action") == "choose" and a.get("index") == want), None
            )
        return None

    def _serialize(self) -> dict[str, Any]:
        cs = self._cs
        is_terminal, value = cs.terminal_value()
        if is_terminal or not cs.legal_actions():
            return {"decision": "game_over", "victory": bool(value > 0)}
        state = build_combat_view(cs, self.character, legal=cs.legal_actions(), loc=None)
        pc = cs.pending_choice()
        if pc:
            # The same renderer the live backend uses, so a card like Headbutt
            # is not a dead end here and looks identical either side.
            fill_combat_choice(state, pc)
        return state
