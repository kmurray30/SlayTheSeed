"""Every live driver must search the DEPLOYED honest profile by default (GH #383).

The three honest knobs travel together -- ``hidden_info="beliefdraw"``,
``c_puct=2.5``, ``draw_widening_c=2.0`` -- because on reconstructed
real-DLL states the RNG cursors are faked, so a clairvoyant root plans
against a fictional future (see ``RecommendConfig.hidden_info``).

``RecommendConfig``'s dataclass defaults are the *clairvoyant* ones, so a
driver that simply omits the fields silently runs the leaky search. That
happened to ``realgame_eval`` and ``headless_eval`` until 2026-08-02, and
the sweep that fixed them missed ``realgame_ppo`` and ``realgame_mcts``
entirely -- which is GH #383, found by comparing the advisor lane against
the autopilot lane and noticing they were not running the same search.

These tests pin the profile at the CLI boundary for every driver at once,
so a fourth driver cannot quietly drift back.
"""

from __future__ import annotations

from typing import Any

import pytest

#: The deployed honest search. Kept literal rather than imported so a change
#: to the shared constant has to be made here too, deliberately.
HONEST = {"hidden_info": "beliefdraw", "c_puct": 2.5, "draw_widening_c": 2.0}
CLAIRVOYANT = {"hidden_info": "clairvoyant", "c_puct": 1.4, "draw_widening_c": None}


def _capture_cfg(monkeypatch, module, run_name: str = "run") -> dict[str, Any]:
    """Replace ``module.run`` with a spy that records the config it was handed."""
    captured: dict[str, Any] = {}

    def _spy(*args: Any, **kwargs: Any) -> int:
        # realgame_mcts takes mcts_cfg positionally (6th), realgame_ppo by kw.
        cfg = kwargs.get("mcts_cfg")
        if cfg is None:
            cfg = next((a for a in args if hasattr(a, "hidden_info")), None)
        captured["cfg"] = cfg
        return 0

    monkeypatch.setattr(module, run_name, _spy)
    return captured


def _profile(cfg) -> dict[str, Any]:
    return {
        "hidden_info": cfg.hidden_info,
        "c_puct": cfg.c_puct,
        "draw_widening_c": cfg.draw_widening_c,
    }


@pytest.fixture
def base_argv(tmp_path) -> list[str]:
    return ["--log-dir", str(tmp_path)]


class TestHonestByDefault:
    def test_ppo_driver_defaults_to_honest(self, monkeypatch, base_argv) -> None:
        import realgame_ppo as rp

        captured = _capture_cfg(monkeypatch, rp)
        assert rp.main(base_argv) == 0
        assert _profile(captured["cfg"]) == HONEST

    def test_mcts_driver_defaults_to_honest(self, monkeypatch, base_argv) -> None:
        import realgame_mcts as rm

        captured = _capture_cfg(monkeypatch, rm)
        assert rm.main(base_argv) == 0
        assert _profile(captured["cfg"]) == HONEST


class TestClairvoyantIsOptIn:
    """The old profile stays reachable -- it is how an old number is reproduced."""

    def test_ppo_driver_clairvoyant_flag(self, monkeypatch, base_argv) -> None:
        import realgame_ppo as rp

        captured = _capture_cfg(monkeypatch, rp)
        assert rp.main([*base_argv, "--clairvoyant"]) == 0
        assert _profile(captured["cfg"]) == CLAIRVOYANT

    def test_mcts_driver_clairvoyant_flag(self, monkeypatch, base_argv) -> None:
        import realgame_mcts as rm

        captured = _capture_cfg(monkeypatch, rm)
        assert rm.main([*base_argv, "--clairvoyant"]) == 0
        assert _profile(captured["cfg"]) == CLAIRVOYANT


class TestExplicitCPuctWins:
    """``--c-puct`` overrides the profile default in both directions."""

    @pytest.mark.parametrize("extra", [[], ["--clairvoyant"]])
    def test_ppo_driver(self, monkeypatch, base_argv, extra) -> None:
        import realgame_ppo as rp

        captured = _capture_cfg(monkeypatch, rp)
        assert rp.main([*base_argv, *extra, "--c-puct", "3.75"]) == 0
        assert captured["cfg"].c_puct == pytest.approx(3.75)

    @pytest.mark.parametrize("extra", [[], ["--clairvoyant"]])
    def test_mcts_driver(self, monkeypatch, base_argv, extra) -> None:
        import realgame_mcts as rm

        captured = _capture_cfg(monkeypatch, rm)
        assert rm.main([*base_argv, *extra, "--c-puct", "3.75"]) == 0
        assert captured["cfg"].c_puct == pytest.approx(3.75)
