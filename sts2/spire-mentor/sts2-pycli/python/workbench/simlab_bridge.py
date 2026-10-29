"""Hand a workbench position to SimLab.

Two questions, two shapes:

* **"How does this deck do against that fight, from the top?"** —
  :func:`scenario_prefill` derives an ordinary SimLab ``ScenarioSpec`` (deck,
  HP, relics, potions, ascension, and a ``monsters:`` lineup built from the
  enemies actually on screen). SimLab builds fresh fights from turn 1.

* **"How does THIS position play out?"** — :func:`sweep_from_states` builds a
  ``SweepSpec`` carrying the board itself, so every trial starts from the exact
  turn, hand and pile order, with only the future dice varying. That is the one
  that answers a counterfactual: branch in the workbench, sweep from each
  branch, and the distributions differ by the decision alone.

SimLab's API is mounted into the workbench app (see ``server.create_app``), so
this posts to it in-process rather than over the network.
"""

from __future__ import annotations

import json
from typing import Any

#: SimLab strategies to sweep when the caller names none. `mcts` is deliberately
#: absent: it is minutes-per-cell, and a default should be the cheap read.
DEFAULT_STRATEGIES = ("random", "block_first", "attack_first", "min_overblock")

#: Trials per (position, strategy) cell when the caller names none. Enough for
#: the shape of a damage distribution; the UI can ask for more.
DEFAULT_N_SEEDS = 200


def _monster_lineup(wire: dict[str, Any]) -> list[str]:
    """Living enemies as SimLab's ``monsters:`` names, in slot order.

    Corpses are dropped: a fresh fight against a lineup that includes something
    already dead is not the fight the player is in.
    """
    out: list[str] = []
    for enemy in wire.get("encounter") or []:
        if not isinstance(enemy, dict):
            continue
        if (enemy.get("hp") or 0) <= 0:
            continue
        kind = enemy.get("kind") or enemy.get("id")
        if isinstance(kind, str) and kind:
            out.append(kind)
    return out


def scenario_prefill(imported: Any) -> dict[str, Any]:
    """SimLab sweep-form fields for the run behind *imported*, fights from turn 1.

    Args:
        imported: A :class:`workbench.state_import.ImportedState`.

    Returns:
        A dict the SimLab SPA can drop straight into its form. ``encounter`` is
        a ``monsters:`` lineup when the paste carried a fight, and ``None``
        when it did not — a run-level paste says which deck to sweep, not which
        fight, so the user picks that.
    """
    run = imported.run
    encounter = None
    if imported.combat_wire is not None:
        lineup = _monster_lineup(imported.combat_wire)
        if lineup:
            encounter = "monsters:" + ",".join(lineup)
    return {
        "character": run.character,
        "ascension": run.ascension,
        "deck_name": f"{run.character.lower()}-floor{run.floor}",
        # `id+1` is how SimLab's deck presets spell an upgraded card.
        "deck_cards": [f"{cid}+1" if up else cid for cid, up in run.deck],
        "hp": run.hp,
        "max_hp": run.max_hp,
        "relics": list(run.relics),
        "potions": list(run.potions),
        "encounter": encounter,
    }


def sweep_from_states(
    name: str,
    states: list[tuple[str, dict[str, Any]]],
    *,
    strategies: tuple[str, ...] | list[str] = DEFAULT_STRATEGIES,
    n_seeds: int = DEFAULT_N_SEEDS,
) -> dict[str, Any]:
    """A SimLab sweep spec that starts every trial from one of *states*.

    Args:
        name: Sweep name, shown in SimLab's list.
        states: ``(label, wire)`` pairs. Labels become the compared axis, so
            they should say what differs ("played Bash", "played Strike").
        strategies: SimLab agents to run each position under.
        n_seeds: Trials per (position, strategy) cell.

    Returns:
        The ``SweepSpec`` JSON to POST to ``/api/sweeps``.

    Raises:
        ValueError: When *states* is empty — a sweep with no position to start
            from has nothing to measure.
    """
    if not states:
        raise ValueError("a start-state sweep needs at least one position")
    return {
        "name": name,
        # Pinned by the states themselves; SweepSpec ignores these axes when
        # `start_states` is set, but they are required fields.
        "encounters": [],
        "ascensions": [],
        "strategies": list(strategies),
        "n_seeds": int(n_seeds),
        "start_states": [[label, json.dumps(wire)] for label, wire in states],
    }
