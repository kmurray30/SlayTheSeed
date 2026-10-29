"""A10 DoubleBoss: ``full_map()`` identifies the SECOND boss, not just its node.

The final act of an A10 run carries a second Boss node directly above the act
boss (``ActMap::attach_second_boss``), and the game fights a *different*
encounter there — C# ``NBossMapPoint._Ready`` draws
``Point == Map.SecondBossMapPoint ? Act.SecondBossEncounter :
Act.BossEncounter``, and ``RoomSet.NextBossEncounter`` resolves the second
visit to the second-boss id.

The node arrives as an ordinary ``rows`` entry (only ``ActMap::boss`` is
grid-exempt in ``full_map_view``, and the PPO map featurizer depends on reading
it there). What ``rows`` cannot express is WHICH boss stands on it, so the
wire's ``second_boss`` meta key carries the identity. Without it the map drew
the act boss's face and name on both of the run's last two nodes.
"""

from __future__ import annotations

from typing import Any

import pytest
import sts2_rs


def _a10_final_act_map(seed: int) -> tuple[Any, dict[str, Any]]:
    """Drive an A10 run until the final act's map is loaded.

    Returns ``(run_manager, full_map)``. The walk takes the first legal action
    at every decision with the player pinned alive — the point is to reach act
    3's map, not to play well.
    """
    rm = sts2_rs.RunManager(seed, "Ironclad", ascension_level=10)
    for _ in range(300_000):
        if rm.is_over():
            break
        rm.set_player(hp=9999, max_hp=9999)
        actions = rm.available_actions()
        if not actions:
            break
        rm.take_action(actions[0])
        if rm.act_index >= 3:
            full_map = rm.full_map()
            if full_map and full_map.get("second_boss"):
                return rm, full_map
    pytest.fail(f"seed {seed}: never reached the A10 final-act map")


@pytest.mark.parametrize("seed", [1, 7])
def test_second_boss_is_a_different_encounter_one_row_above_the_boss(seed: int) -> None:
    rm, full_map = _a10_final_act_map(seed)
    boss = full_map["boss"]
    second = full_map["second_boss"]

    # The id the engine pre-rolled for this act is the one the map reports.
    generated = rm.generated_rooms()[rm.act_index - 1]
    assert second["id"] == generated["second_boss"]
    assert second["id"] != boss["id"], "the second boss is a different encounter"

    # Directly above the act boss, same column (`attach_second_boss`).
    assert (second["col"], second["row"]) == (boss["col"], boss["row"] + 1)
    assert second["type"] == "Boss"

    # …and it is STILL an ordinary row node, which the PPO map featurizer
    # (`mapfeat::nodes_from_full_map`) reads. Surfacing the identity must not
    # have moved the node off `rows`.
    row_nodes = [n for row in full_map["rows"] for n in row if n["type"] == "Boss"]
    assert [(n["col"], n["row"]) for n in row_nodes] == [(second["col"], second["row"])]


def test_no_second_boss_below_a10() -> None:
    # `has_double_boss` is an A10 rule; below it the key is present and empty.
    rm = sts2_rs.RunManager(1, "Ironclad", ascension_level=9)
    for _ in range(300_000):
        if rm.is_over():
            break
        rm.set_player(hp=9999, max_hp=9999)
        actions = rm.available_actions()
        if not actions:
            break
        rm.take_action(actions[0])
        full_map = rm.full_map()
        if full_map:
            assert full_map["second_boss"] is None
        if rm.act_index >= 3 and rm.act_floor() > 1:
            return
    pytest.fail("A9 run never reached act 3")
