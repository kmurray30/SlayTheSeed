"""Tests for the MCTS hint CLI flags (T10).

Bead: ``sts2-rs-vjd1`` (hint-T10, sts2-pycli side).

Covers:

* Arg-parser smoke — each new flag parses with the expected default.
* ``--no-hints`` → hint mode is ``"off"`` and ``_compute_hint_tags``
  short-circuits without calling ``recommend()``.
* ``--hint-on-demand`` → ``show_combat`` skips inline tag computation;
  ``/hint`` REPL verb is registered (i.e. ``get_input`` recognizes it).
* ``--hint-timeout-ms 50`` with a 500 ms sleeping ``recommend()`` →
  returns ``None`` and logs a warning via the ``sts2_hint`` logger.
* ``--mcts-fair`` propagates to ``RecommendConfig.fair=True``.
* ``--no-hints`` and ``--hint-on-demand`` together → argparse error.
"""

from __future__ import annotations

import importlib.util
import io
import pathlib
import sys
import time

import pytest

ROOT = pathlib.Path(__file__).resolve().parents[1]
PLAY_PATH = ROOT / "python" / "play.py"

sys.path.insert(0, str(ROOT / "python"))
spec = importlib.util.spec_from_file_location("play_module_for_t10_tests", PLAY_PATH)
play = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(play)

from sts2_hint.recommender import HintTag, RecommendConfig  # noqa: E402

# ─── Helpers ──────────────────────────────────────────────────────────


def _build_parser():
    """Re-create the argparse.ArgumentParser identically to play.py main.

    We can't trivially import the parser from play.py (it's built inline
    in ``if __name__ == "__main__":``) so duplicate the flag definitions
    that we care about. Keep this in sync with play.py.
    """
    import argparse

    p = argparse.ArgumentParser()
    p.add_argument("--character", default="Ironclad")
    p.add_argument("--ascension", type=int, default=0)
    p.add_argument("--seed", type=str, default=None)
    p.add_argument("--mcts-sims", type=int, default=300)
    p.add_argument("--mcts-depth", type=int, default=10)
    p.add_argument("--mcts-c-puct", type=float, default=1.4)
    p.add_argument("--mcts-seed", type=int, default=None)
    p.add_argument("--mcts-threads", type=int, default=1)
    p.add_argument("--mcts-fair", action="store_true")
    p.add_argument("--mcts-cheat-draw-order", action="store_true")
    p.add_argument("--hint-timeout-ms", type=int, default=5000)
    g = p.add_mutually_exclusive_group()
    g.add_argument("--no-hints", action="store_true")
    g.add_argument("--hint-on-demand", action="store_true")
    return p


def _sample_combat_state():
    return {
        "decision": "combat_play",
        "round": 1,
        "energy": 3,
        "max_energy": 3,
        "draw_pile_count": 5,
        "discard_pile_count": 0,
        "player": {"name": "Ironclad", "hp": 80, "max_hp": 80, "block": 0},
        "enemies": [{"index": 0, "name": "Nibbit", "hp": 50, "max_hp": 50, "intents": []}],
        "hand": [
            {
                "name": "Strike",
                "type": "Attack",
                "cost": 1,
                "can_play": True,
                "target_type": "AnyEnemy",
                "stats": {"damage": 6},
                "stable_idx": 0,
                "index": 0,
                "keywords": [],
            }
        ],
    }


# ─── Arg-parser smoke ─────────────────────────────────────────────────


def test_flag_defaults_parse_cleanly():
    parser = _build_parser()
    args = parser.parse_args([])
    assert args.mcts_sims == 300
    assert args.mcts_depth == 10
    assert args.mcts_c_puct == pytest.approx(1.4)
    assert args.mcts_seed is None
    assert args.mcts_threads == 1
    assert args.mcts_fair is False
    assert args.mcts_cheat_draw_order is False
    assert args.hint_timeout_ms == 5000
    assert args.no_hints is False
    assert args.hint_on_demand is False


def test_flags_override_defaults():
    parser = _build_parser()
    args = parser.parse_args(
        [
            "--mcts-sims",
            "1000",
            "--mcts-depth",
            "5",
            "--mcts-c-puct",
            "2.0",
            "--mcts-seed",
            "42",
            "--mcts-threads",
            "4",
            "--mcts-fair",
            "--mcts-cheat-draw-order",
            "--hint-timeout-ms",
            "1234",
        ]
    )
    assert args.mcts_sims == 1000
    assert args.mcts_depth == 5
    assert args.mcts_c_puct == pytest.approx(2.0)
    assert args.mcts_seed == 42
    assert args.mcts_threads == 4
    assert args.mcts_fair is True
    assert args.mcts_cheat_draw_order is True
    assert args.hint_timeout_ms == 1234


def test_no_hints_and_on_demand_are_mutually_exclusive():
    parser = _build_parser()
    with pytest.raises(SystemExit):
        # argparse exits the process on mutex violation; capture stderr
        # to keep test output clean.
        saved = sys.stderr
        try:
            sys.stderr = io.StringIO()
            parser.parse_args(["--no-hints", "--hint-on-demand"])
        finally:
            sys.stderr = saved


# ─── --mcts-fair → RecommendConfig.fair=True ─────────────────────────


def test_mcts_fair_propagates_to_recommend_config():
    """The CLI builds a RecommendConfig from args; --mcts-fair must
    flip ``fair=True``."""
    parser = _build_parser()
    args = parser.parse_args(["--mcts-fair", "--mcts-sims", "100"])

    cfg = RecommendConfig(
        sims=args.mcts_sims,
        depth=args.mcts_depth,
        c_puct=args.mcts_c_puct,
        seed=args.mcts_seed if args.mcts_seed is not None else 0,
        threads=args.mcts_threads,
        fair=args.mcts_fair,
        cheat_draw_order=args.mcts_cheat_draw_order,
        timeout_ms=args.hint_timeout_ms,
    )
    assert cfg.fair is True
    assert cfg.sims == 100


# ─── --no-hints → recommend() not called ─────────────────────────────


def test_no_hints_short_circuits_compute(monkeypatch):
    state = _sample_combat_state()

    def tripwire(_s, _cfg):
        raise AssertionError("recommend() called with hint mode='off'")

    monkeypatch.setattr(play, "_hint_recommend_all", tripwire)
    monkeypatch.setattr(play, "_HINT_AVAILABLE", True)
    monkeypatch.setattr(play, "_HINT_MODE", "off")
    monkeypatch.setattr(play, "_HINT_CFG", RecommendConfig())

    result = play._compute_hint_tags(state)
    assert result is None


# ─── --hint-on-demand: show_combat doesn't call recommend ────────────


def test_hint_on_demand_skips_inline_compute(monkeypatch, capsys):
    state = _sample_combat_state()

    def tripwire(_s, _cfg):
        raise AssertionError("recommend() called from show_combat in on_demand mode")

    monkeypatch.setattr(play, "_hint_recommend_all", tripwire)
    monkeypatch.setattr(play, "_HINT_AVAILABLE", True)
    monkeypatch.setattr(play, "_HINT_MODE", "on_demand")
    monkeypatch.setattr(play, "_HINT_CFG", RecommendConfig())

    # Must not call recommend, must not raise.
    play.show_combat(state)
    out = capsys.readouterr().out
    # No bucket labels should leak.
    assert "LIKELY-WIN" not in out
    assert "EVEN" not in out


def test_hint_on_demand_force_computes(monkeypatch):
    """``force=True`` (used by the /hint REPL verb) bypasses the mode
    gate and triggers an actual recommend() call."""
    state = _sample_combat_state()
    calls = {"n": 0}

    def fake(_s, _cfg):
        calls["n"] += 1
        return [HintTag(stable_idx=0, win_prob=0.72, bucket_label="likely-win", is_best=True)]

    monkeypatch.setattr(play, "_hint_recommend_all", fake)
    monkeypatch.setattr(play, "_HINT_AVAILABLE", True)
    monkeypatch.setattr(play, "_HINT_MODE", "on_demand")
    monkeypatch.setattr(play, "_HINT_CFG", RecommendConfig())
    monkeypatch.setattr(play, "_HINT_TIMEOUT_MS", 0)  # no timeout for speed

    tags = play._compute_hint_tags(state, force=True)
    assert calls["n"] == 1
    assert tags is not None
    assert 0 in tags.by_idx


# ─── /hint REPL verb is registered ───────────────────────────────────


def test_hint_repl_verb_registered(monkeypatch, capsys):
    """``get_input`` should treat ``/hint`` as a meta-command: it calls
    ``_show_hints_on_demand(state)`` and re-prompts rather than treating
    it as a card-play token."""
    calls = {"shown": 0}

    def fake_show(_state):
        calls["shown"] += 1

    monkeypatch.setattr(play, "_show_hints_on_demand", fake_show)

    # Drive get_input with two stdin lines: first "/hint" (handled),
    # then "ok" (returned as a real action).
    inputs = iter(["/hint", "ok"])
    monkeypatch.setattr("builtins.input", lambda _prompt: next(inputs))

    result = play.get_input("test prompt", state=_sample_combat_state())
    assert result == "ok"
    assert calls["shown"] == 1


def test_hint_repl_verb_bare_word(monkeypatch):
    """Both ``/hint`` and bare ``hint`` should be accepted."""
    calls = {"shown": 0}

    def fake_show(_state):
        calls["shown"] += 1

    monkeypatch.setattr(play, "_show_hints_on_demand", fake_show)

    inputs = iter(["hint", "done"])
    monkeypatch.setattr("builtins.input", lambda _prompt: next(inputs))
    result = play.get_input("p", state=_sample_combat_state())
    assert result == "done"
    assert calls["shown"] == 1


# ─── --hint-timeout-ms enforces wall-clock cap ───────────────────────


def test_timeout_returns_none_and_logs_warning(monkeypatch):
    """A recommend() that exceeds the timeout returns None; the
    sts2_hint logger.warning is called with the timeout value."""
    state = _sample_combat_state()

    def slow_recommend(_s, _cfg):
        time.sleep(0.5)  # 500ms — way over our 50ms cap
        return []

    monkeypatch.setattr(play, "_hint_recommend_all", slow_recommend)
    monkeypatch.setattr(play, "_HINT_AVAILABLE", True)
    monkeypatch.setattr(play, "_HINT_MODE", "inline")
    monkeypatch.setattr(play, "_HINT_CFG", RecommendConfig())
    monkeypatch.setattr(play, "_HINT_TIMEOUT_MS", 50)

    warnings: list[str] = []

    class _StubLogger:
        def warning(self, fmt, *args, **kwargs):
            try:
                warnings.append(fmt % args if args else fmt)
            except Exception:
                warnings.append(str(fmt))

        def error(self, *args, **kwargs):
            pass

    monkeypatch.setattr(play, "_hint_get_logger", lambda: _StubLogger())

    t0 = time.monotonic()
    result = play._compute_hint_tags(state)
    elapsed = time.monotonic() - t0

    assert result is None
    # We bailed near the 50ms mark, well under the worker's 500ms sleep.
    assert elapsed < 0.4, f"timeout not enforced; took {elapsed:.3f}s"
    assert warnings, "expected a logger.warning call on timeout"
    assert "timeout" in warnings[0].lower()


def test_timeout_zero_disables_cap(monkeypatch):
    """timeout_ms=0 runs synchronously without a worker thread."""
    state = _sample_combat_state()

    captured_cfg = {}

    def fake_recommend(_s, cfg):
        captured_cfg["cfg"] = cfg
        return [HintTag(stable_idx=0, win_prob=0.5, bucket_label="even", is_best=True)]

    monkeypatch.setattr(play, "_hint_recommend_all", fake_recommend)
    monkeypatch.setattr(play, "_HINT_AVAILABLE", True)
    monkeypatch.setattr(play, "_HINT_MODE", "inline")
    monkeypatch.setattr(play, "_HINT_CFG", RecommendConfig())
    monkeypatch.setattr(play, "_HINT_TIMEOUT_MS", 0)

    tags = play._compute_hint_tags(state)
    assert tags is not None
    assert 0 in tags.by_idx
