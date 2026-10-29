"""Optionality path-plan overlay attach in the Rust backend's ``get_map``.

``RustGame.get_map`` attaches the run-AI optionality path plan (from the
``sts2_rs`` ``PyRunManager::path_plan`` binding) under ``full_map["path_plan"]``
so the workbench map view can overlay it. The attach is best-effort: bindings
predating the method (no ``path_plan`` attr) and a raising ``path_plan`` both
degrade to a plan-less map rather than breaking the screen.
"""

from __future__ import annotations

from typing import Any

from workbench.backends.rust_game import RustGame

_FULL_MAP: dict[str, Any] = {
    "rows": [[{"col": 3, "row": 0, "type": "Monster", "children": []}]],
    "boss": None,
    "current_coord": None,
}

_PLAN: dict[str, Any] = {
    "best_col": 2,
    "baseline_col": 6,
    "diverged": True,
    "route": [{"col": 2, "row": 1}, {"col": 2, "row": 2}],
    "columns": [
        {
            "col": 2,
            "score": -3.8,
            "baseline_score": -3.85,
            "n_paths": 18,
            "min_elites": 0,
            "max_elites": 2,
            "can_skip_elite": True,
            "is_pick": True,
        }
    ],
}


class _Raise:
    """Sentinel: make ``path_plan()`` raise."""


class _Rm:
    """Minimal stand-in exposing the bits ``get_map`` reads."""

    def __init__(self, plan: Any = None) -> None:
        self._plan = plan

    def full_map(self) -> dict[str, Any]:
        return dict(_FULL_MAP)

    def path_plan(self) -> Any:
        if isinstance(self._plan, _Raise):
            raise RuntimeError("boom")
        return self._plan


def _game(rm: Any) -> RustGame:
    g = RustGame()
    g.rm = rm  # type: ignore[assignment]
    return g


def test_plan_attached_when_binding_present() -> None:
    fm = _game(_Rm(_PLAN)).get_map()
    assert fm["path_plan"] == _PLAN
    assert fm["path_plan"]["columns"][0]["can_skip_elite"] is True


def test_no_plan_key_when_binding_predates_method() -> None:
    class _OldRm:
        def full_map(self) -> dict[str, Any]:
            return dict(_FULL_MAP)

    fm = _game(_OldRm()).get_map()
    assert "path_plan" not in fm


def test_no_plan_key_when_plan_is_none() -> None:
    fm = _game(_Rm(None)).get_map()
    assert "path_plan" not in fm


def test_raising_path_plan_degrades_gracefully() -> None:
    fm = _game(_Rm(_Raise())).get_map()
    assert "path_plan" not in fm
    # The map itself still renders.
    assert fm["rows"]


class _SeedRm:
    """A binding that DOES accept the seeded first column."""

    def __init__(self) -> None:
        self.seen: list[int | None] = []

    def full_map(self) -> dict[str, Any]:
        return dict(_FULL_MAP)

    def path_plan(self, first_col: int | None = None) -> dict[str, Any]:
        self.seen.append(first_col)
        plan = dict(_PLAN)
        plan["first_col"] = first_col
        if first_col is not None:
            plan["route"] = [{"col": first_col, "row": 1}, {"col": first_col, "row": 2}]
        return plan


def test_first_col_seeds_the_route() -> None:
    rm = _SeedRm()
    fm = _game(rm).get_map(5)
    assert rm.seen == [5]
    assert fm["path_plan"]["route"][0]["col"] == 5
    assert fm["path_plan"]["first_col"] == 5


def test_unseeded_call_leaves_the_planner_alone() -> None:
    # An older binding's ``path_plan()`` takes no parameter at all, so the
    # unseeded path calls it with no argument — which is the whole reason
    # ``get_path_plan`` branches instead of always passing None.
    rm = _SeedRm()
    fm = _game(rm).get_map()
    assert rm.seen == [None]  # via the default, not an explicit argument
    assert fm["path_plan"]["first_col"] is None
    assert fm["path_plan"]["route"] == _PLAN["route"]


def test_old_binding_ignores_a_seed_instead_of_losing_the_plan() -> None:
    # ``_Rm.path_plan`` takes no argument: a seeded call raises TypeError and
    # must fall back to the unseeded plan, not drop the overlay.
    fm = _game(_Rm(_PLAN)).get_map(5)
    assert fm["path_plan"] == _PLAN
