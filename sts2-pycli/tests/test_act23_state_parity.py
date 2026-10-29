"""Pure-logic tests for the deep act-2/3 state-parity probe (issue #158).

These exercise the nav-instrumentation and the C# mid-combat-snapshot HP
oracle-artifact classifier WITHOUT driving the engines (no C# subprocess), so
they are fast and deterministic. The engine-driven sweep is validated by running
the CLI on real seeds.
"""

from __future__ import annotations

import act23_state_parity as a23
from run_parity import Snap


def _snap(phase: str, hp: int | None = None, enemies: tuple[int, ...] = ()) -> Snap:
    """Minimal Snap for classifier tests (only the fields the helpers read)."""
    return Snap(
        phase=phase,
        hp=hp,
        max_hp=hp,
        block=0,
        gold=0,
        deck={},
        deck_size=0,
        relics=[],
        potions=[],
        enemies=enemies,
    )


# --------------------------------------------------------------------------- #
# act / floor extraction reads the C# ``context`` nesting
# --------------------------------------------------------------------------- #
def test_act_of_reads_csharp_context() -> None:
    # C# nests act under ``context`` at event/combat/card_reward decisions.
    assert a23._act_of({"decision": "event_choice", "context": {"act": 2}}) == 2
    # Rust puts it top-level.
    assert a23._act_of({"decision": "event_choice", "act": 3}) == 3
    # Absent everywhere -> None (the old behaviour that pinned max_act to Rust).
    assert a23._act_of({"decision": "event_choice"}) is None
    assert a23._act_of(None) is None


def test_floor_of_reads_context() -> None:
    assert a23._floor_of({"context": {"floor": 7}}) == 7
    assert a23._floor_of({"floor": 22}) == 22
    assert a23._floor_of({"decision": "combat_play"}) is None


# --------------------------------------------------------------------------- #
# current-node coord: Rust top-level dict vs C# per-node ``current`` flag
# --------------------------------------------------------------------------- #
class _MapBackend:
    def __init__(self, m: dict) -> None:
        self._m = m

    def get_map(self) -> dict:
        return self._m


def test_current_coord_rust_style() -> None:
    b = _MapBackend({"current_coord": {"col": 3, "row": 16}, "rows": []})
    assert a23._current_coord(b) == (3, 16)


def test_current_coord_csharp_style() -> None:
    b = _MapBackend(
        {
            "rows": [
                [{"col": 1, "row": 5, "current": False}],
                [{"col": 2, "row": 6, "current": True}],
            ]
        }
    )
    assert a23._current_coord(b) == (2, 6)


def test_current_coord_absent() -> None:
    # No current node and no current_coord -> None (the C# off-map case).
    b = _MapBackend({"rows": [[{"col": 1, "row": 5, "current": False}]]})
    assert a23._current_coord(b) is None

    class _Boom:
        def get_map(self):
            raise RuntimeError("no map in this phase")

    assert a23._current_coord(_Boom()) is None


# --------------------------------------------------------------------------- #
# diff_nav: act + coord, with one-sided None skipped
# --------------------------------------------------------------------------- #
def test_diff_nav_flags_act_and_coord() -> None:
    cs = a23.Nav(act=2, floor=7, coord=(3, 0))
    rs = a23.Nav(act=1, floor=16, coord=(3, 16))
    fields = a23.diff_nav(cs, rs)
    assert any(f.startswith("nav.act:") for f in fields)
    assert any(f.startswith("nav.coord:") for f in fields)


def test_diff_nav_skips_one_sided_none() -> None:
    # C# exposes no current node (coord None) -> coord comparison skipped.
    cs = a23.Nav(act=2, floor=None, coord=None)
    rs = a23.Nav(act=2, floor=16, coord=(3, 16))
    assert a23.diff_nav(cs, rs) == []


def test_diff_nav_clean_when_aligned() -> None:
    cs = a23.Nav(act=2, floor=7, coord=(2, 6))
    rs = a23.Nav(act=2, floor=22, coord=(2, 6))  # floor differs but is NOT compared
    assert a23.diff_nav(cs, rs) == []


# --------------------------------------------------------------------------- #
# HP oracle-artifact classifier (issue #158 step 3)
# --------------------------------------------------------------------------- #
def test_hp_artifact_peeled_in_combat() -> None:
    cs, rs = _snap("combat", hp=99988), _snap("combat", hp=99986)
    assert a23._is_combat_hp_artifact(cs, rs, ["hp: cs=99988 rs=99986"])


def test_hp_artifact_peeled_in_combat_exit_window() -> None:
    for phase in ("card_reward", "card_select"):
        cs, rs = _snap(phase, hp=99791), _snap(phase, hp=99790)
        assert a23._is_combat_hp_artifact(cs, rs, ["hp: cs=99791 rs=99790"]), phase


def test_hp_artifact_not_peeled_with_enemy_diff() -> None:
    # A co-occurring enemies_hp field => real combat divergence, never peeled.
    cs, rs = _snap("combat", hp=99969, enemies=(15,)), _snap("combat", hp=99956, enemies=(12,))
    fields = ["hp: cs=99969 rs=99956", "enemies_hp: cs=[15] rs=[12]"]
    assert not a23._is_combat_hp_artifact(cs, rs, fields)


def test_hp_artifact_not_peeled_at_event_or_rest() -> None:
    # Events/rest are where REAL HP-changing effects live.
    for phase in ("event", "rest"):
        cs, rs = _snap(phase, hp=99988), _snap(phase, hp=99986)
        assert not a23._is_combat_hp_artifact(cs, rs, ["hp: cs=99988 rs=99986"]), phase


def test_hp_artifact_not_peeled_above_tolerance() -> None:
    delta = a23.HP_ARTIFACT_TOLERANCE + 1
    cs, rs = _snap("combat", hp=100_000), _snap("combat", hp=100_000 - delta)
    assert not a23._is_combat_hp_artifact(cs, rs, [f"hp: cs=100000 rs={100_000 - delta}"])
