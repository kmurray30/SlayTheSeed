"""No-clairvoyance pin for the seed-locked shadow combat search.

Phase 1 of ``docs/plans/2026-07-18-seed-locked-shadow-engine.md`` feeds the
combat search from the shadow engine's LOSSLESS native state — but it must do
so through the ``state_to_dict -> state_from_dict`` round-trip (the rung-3
channel), which DISCARDS the shadow's true RNG cursors / draw order. Passing
the native combat state straight to ``mcts_recommend`` instead would let the
search see the real future (clairvoyance) — the exact regression the plan's
"No clairvoyance regression" non-goal forbids.

These tests pin that contract structurally: the state handed to
``mcts_recommend`` is the round-tripped one (never the native handle), and the
honest ``hidden_info`` from the caller's config is forwarded verbatim (never
silently upgraded to ``clairvoyant``). They run without a game process by
stubbing ``sts2_rs``.
"""

from __future__ import annotations

from typing import Any

import headless_eval
import pytest
from sts2_hint.recommender import RecommendConfig


class _FakeCombatState:
    """Stand-in for a native ``sts2_rs`` combat state handle."""

    def __init__(self, tag: str) -> None:
        self.tag = tag

    def pending_choice(self) -> None:
        return None


class _FakeShadow:
    def __init__(self, cs: Any) -> None:
        self._cs = cs

    def combat_state(self) -> Any:
        return self._cs


class _RecordingRs:
    """Recording stub for the ``sts2_rs`` surface the shadow search touches."""

    def __init__(self) -> None:
        self.calls: dict[str, Any] = {}
        self.native = _FakeCombatState("native")
        self.roundtripped = _FakeCombatState("roundtripped")

    def state_to_dict(self, cs: Any) -> dict[str, Any]:
        self.calls["state_to_dict_arg"] = cs
        return {"wire": True}

    def state_from_dict(self, wire: dict[str, Any]) -> Any:
        self.calls["state_from_dict_arg"] = wire
        return self.roundtripped

    def mcts_recommend(self, cs: Any, **kwargs: Any) -> dict[str, Any]:
        self.calls["mcts_cs"] = cs
        self.calls["mcts_kwargs"] = kwargs
        # Best action = end_turn: simplest legal answer, no hand needed.
        return {"actions": [{"action": {"action": "end_turn"}, "p_win": 1.0, "is_best": True}]}


def _run(monkeypatch: Any, cfg: RecommendConfig) -> _RecordingRs:
    rs = _RecordingRs()
    monkeypatch.setattr(headless_eval, "sts2_rs", rs)
    shadow = _FakeShadow(rs.native)
    import random

    cmd, log_extra = headless_eval.pick_combat_action_from_shadow(
        shadow, {"hand": []}, cfg, random.Random(0), sims=32
    )
    assert cmd == {"action": "end_turn", "args": {}}
    assert log_extra is None
    return rs


def test_shadow_search_uses_roundtripped_state_not_native(monkeypatch: Any) -> None:
    rs = _run(monkeypatch, RecommendConfig(hidden_info="beliefdraw"))
    # The round-trip must have happened: state_to_dict(native) -> state_from_dict.
    assert rs.calls["state_to_dict_arg"] is rs.native
    assert rs.calls["state_from_dict_arg"] == {"wire": True}
    # And the search MUST run on the round-tripped handle, never the native one
    # (the native handle carries the true RNG cursors -> clairvoyance).
    assert rs.calls["mcts_cs"] is rs.roundtripped
    assert rs.calls["mcts_cs"] is not rs.native


def test_shadow_search_forwards_honest_hidden_info(monkeypatch: Any) -> None:
    rs = _run(monkeypatch, RecommendConfig(hidden_info="beliefdraw", draw_widening_c=2.0))
    kw = rs.calls["mcts_kwargs"]
    # The deployed honest search config must pass through unchanged — the
    # shadow path never silently forces clairvoyant.
    assert kw["hidden_info"] == "beliefdraw"
    assert kw["draw_widening_c"] == pytest.approx(2.0)


def test_target_remap_slot_to_alive_compact() -> None:
    # Encounter with a dead corpse in slot 0 (kept in-slot by the round-trip):
    # native mcts targets the two live enemies by SLOT (1, 2); the DLL wants
    # alive-compact indices (0, 1). Verified against seed 1021 floor-2 probe.
    enc = [
        {"kind": "TwigSlimeS", "is_dead": True},
        {"kind": "TwigSlimeM"},
        {"kind": "LeafSlimeS"},
    ]
    assert headless_eval._shadow_target_to_dll(enc, 1) == 0
    assert headless_eval._shadow_target_to_dll(enc, 2) == 1
    # No dead/escaped: identity.
    live = [{"kind": "A"}, {"kind": "B"}, {"kind": "C"}]
    assert [headless_eval._shadow_target_to_dll(live, t) for t in (0, 1, 2)] == [0, 1, 2]
    # Escaped in slot 1 is dropped from the round-trip frame before dead-count:
    # slot indices count over non-escaped, alive-count skips the dead.
    esc = [
        {"kind": "A"},
        {"kind": "B", "is_escaped": True},
        {"kind": "C", "is_dead": True},
        {"kind": "D"},
    ]
    # kept = [A, C(dead), D]; native slot 2 (D) -> alive ahead = {A} = 1.
    assert headless_eval._shadow_target_to_dll(esc, 2) == 1


def test_shadow_search_falls_back_on_pending_choice(monkeypatch: Any) -> None:
    rs = _RecordingRs()
    monkeypatch.setattr(headless_eval, "sts2_rs", rs)

    class _PendingCs(_FakeCombatState):
        def pending_choice(self) -> dict[str, Any]:
            return {"kind": "discard"}

    import random

    out = headless_eval.pick_combat_action_from_shadow(
        _FakeShadow(_PendingCs("native")), {"hand": []}, RecommendConfig(), random.Random(0)
    )
    # A pending in-combat select can't survive the round-trip (state_to_dict
    # drops it) -> the shadow search declines and the wire path takes over.
    assert out is None
    assert "mcts_cs" not in rs.calls
