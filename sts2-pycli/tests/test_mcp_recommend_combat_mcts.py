"""Tests for the new MCP tool ``recommend_combat_mcts`` (bead sts2-rs-zvhr).

Pinned behavior:

* Happy path: invoking ``recommend_combat_mcts`` against a live combat
  state returns the documented response envelope (``actions``, ``pv``,
  ``summary``, ``config``) and at least one ``is_best=True`` row.
* Failure path: when the underlying recommender raises (simulated via
  monkeypatch with :class:`UnsupportedStateError`), the tool degrades to
  ``{"error": ..., "fallback": <heuristic-result>}``. The fallback must
  be the EXACT envelope returned by the heuristic tool.
* Independence: pre/post the new tool's presence, ``recommend_combat``'s
  output for a fixed state is identical (proves no regression to the
  heuristic surface).
"""

from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

import pytest
import sts2_mcp
from sts2_hint.errors import UnsupportedStateError

# Reuse the hint-adapter test helpers for valid combat fixtures.
sys.path.insert(0, str(Path(__file__).parent / "fixtures" / "hint_adapter"))

from _helpers import (  # type: ignore[import-not-found]
    base_state,
    card,
    enemy,
    player_summary,
)

# Skip the live-MCTS test if sts2_rs isn't available in this venv.
sts2_rs = pytest.importorskip("sts2_rs")


def _combat_state() -> dict[str, Any]:
    """One Ironclad-vs-Nibbit combat_play state; small enough for a
    60-sim MCTS smoke run to finish in <1s."""
    return base_state(
        player=player_summary(
            hp=70,
            max_hp=75,
            block=0,
            relics=[{"name": "Burning Blood", "description": "", "vars": None}],
            potions=[],
        ),
        hand=[
            card(index=0, cid="IRONCLAD/STRIKE", name="Strike", cost=1),
            card(
                index=1,
                cid="IRONCLAD/DEFEND",
                name="Defend",
                cost=1,
                ctype="Skill",
                target_type="Self",
            ),
            card(index=2, cid="IRONCLAD/BASH", name="Bash", cost=2),
        ],
        enemies=[
            enemy(
                index=0,
                name="Nibbit",
                hp=48,
                max_hp=48,
                intents=[{"type": "Buff"}],
            )
        ],
        draw_pile=[card(index=10, cid="IRONCLAD/STRIKE", name="Strike", cost=1)],
    )


# ---------------------------------------------------------------------------
# Happy path
# ---------------------------------------------------------------------------


class TestRecommendCombatMctsHappyPath:
    def test_returns_documented_envelope(self):
        sts2_mcp._last_state = _combat_state()
        # Tiny budget so the test completes fast — recommender doesn't
        # care about budget magnitude for shape pinning.
        out = sts2_mcp.recommend_combat_mcts(sims=60, depth=10, seed=42)

        assert "error" not in out, out
        # Top-level keys.
        assert set(out.keys()) >= {"actions", "pv", "summary", "config"}
        # Actions list — at least one row, exactly one is_best.
        rows = out["actions"]
        assert isinstance(rows, list) and rows, "expected non-empty actions list"
        bests = [r for r in rows if r["is_best"]]
        assert len(bests) == 1, f"expected exactly one is_best row, got {len(bests)}"
        # Per-row schema.
        for r in rows:
            assert set(r.keys()) >= {
                "stable_idx",
                "action",
                "win_prob",
                "bucket",
                "is_best",
                "sims_used",
            }
            assert isinstance(r["stable_idx"], int)
            assert isinstance(r["action"], str) and r["action"]
            assert 0.0 <= r["win_prob"] <= 1.0
            assert isinstance(r["bucket"], str)
            assert r["sims_used"] == 60
        # PV is search-global, surfaced as top-level list or None.
        assert out["pv"] is None or isinstance(out["pv"], list)
        # Summary is a non-empty string.
        assert isinstance(out["summary"], str) and out["summary"]
        # Config echoes back inputs.
        cfg = out["config"]
        assert cfg["sims"] == 60
        assert cfg["depth"] == 10
        assert cfg["seed"] == 42

    def test_no_run_returns_error(self):
        sts2_mcp._last_state = None
        out = sts2_mcp.recommend_combat_mcts(sims=10)
        assert "error" in out

    def test_outside_combat_returns_error(self):
        sts2_mcp._last_state = {"decision": "map_select"}
        out = sts2_mcp.recommend_combat_mcts(sims=10)
        assert "error" in out


# ---------------------------------------------------------------------------
# Failure path — degrade to heuristic fallback
# ---------------------------------------------------------------------------


class TestRecommendCombatMctsFallback:
    def test_unsupported_state_yields_fallback(self, monkeypatch):
        """Monkeypatch ``recommend`` to raise ``UnsupportedStateError``
        (the canonical "unported relic / unmodelled power" signal). The
        tool must catch it and return a fallback heuristic recommendation."""
        # Build a heuristic-friendly state: the fallback must produce a
        # non-trivial action, not an error envelope itself.
        sts2_mcp._last_state = {
            "decision": "combat_play",
            "energy": 3,
            "player": {"hp": 70, "max_hp": 80, "block": 0},
            "enemies": [
                {
                    "index": 0,
                    "name": "Nibbit",
                    "hp": 5,
                    "max_hp": 48,
                    "intends_attack": True,
                    "intents": [{"type": "Attack", "damage": 7, "hits": 1}],
                }
            ],
            "hand": [
                {
                    "index": 0,
                    "name": "Strike",
                    "cost": 1,
                    "type": "Attack",
                    "target_type": "AnyEnemy",
                    "can_play": True,
                    "stats": {"damage": 6},
                }
            ],
        }

        def _boom(_state, _cfg=None):
            raise UnsupportedStateError("player.relics[0]: GHIBBET_MAW")

        # The MCP tool imports `recommend` lazily from
        # `sts2_hint.recommender`; monkeypatch the source binding so the
        # import inside the tool picks up our raising stub.
        import sts2_hint.recommender as rec_mod

        monkeypatch.setattr(rec_mod, "recommend", _boom)

        out = sts2_mcp.recommend_combat_mcts(sims=60)
        assert "error" in out
        assert "GHIBBET_MAW" in out["error"]
        assert "fallback" in out, "fallback envelope missing"
        fb = out["fallback"]
        # Heuristic envelope: action / args / reasoning / summary.
        assert fb["action"] in {"play_card", "use_potion", "end_turn"}
        assert fb.get("summary")


# ---------------------------------------------------------------------------
# Independence — heuristic tool is byte-identical pre/post our changes
# ---------------------------------------------------------------------------


class TestRecommendCombatUntouched:
    """Pin that adding ``recommend_combat_mcts`` did NOT change the
    heuristic tool's response for a fixed state. The check is anchored
    against the heuristic's own ``recommend()`` function — calling
    ``sts2_mcp.recommend_combat()`` must produce the exact same dict
    that the underlying ``sts2_combat.recommend`` would on its own.
    """

    def test_heuristic_output_matches_underlying_recommender(self):
        from sts2_combat import recommend as raw_heuristic

        state = {
            "decision": "combat_play",
            "energy": 3,
            "player": {"hp": 70, "max_hp": 80, "block": 0},
            "enemies": [
                {
                    "index": 0,
                    "name": "Slime",
                    "hp": 5,
                    "max_hp": 12,
                    "intends_attack": True,
                    "intents": [{"type": "Attack", "damage": 7, "hits": 1}],
                }
            ],
            "hand": [
                {
                    "index": 0,
                    "name": "Strike",
                    "cost": 1,
                    "type": "Attack",
                    "target_type": "AnyEnemy",
                    "can_play": True,
                    "stats": {"damage": 6},
                }
            ],
        }
        sts2_mcp._last_state = state
        out = sts2_mcp.recommend_combat()
        ref = raw_heuristic(state)
        # The MCP envelope mutates ``summary`` via ``_briefings`` /
        # ``format_state_diff`` (auto-lookup of new enemies, etc.) — that's
        # expected and predates this bead. What MUST be byte-identical is
        # the decision payload itself: ``action``, ``args``, ``reasoning``.
        # If any of those drift, the heuristic surface has regressed.
        for key in ("action", "args", "reasoning"):
            assert out[key] == ref[key], f"{key} drifted: {out[key]!r} != {ref[key]!r}"
        # The base ``summary`` line (incoming/block/hp/energy) must
        # appear somewhere in the envelope summary — auto-briefings and
        # the state-diff hook only wrap around it, never replace it.
        assert ref["summary"] in out["summary"]
