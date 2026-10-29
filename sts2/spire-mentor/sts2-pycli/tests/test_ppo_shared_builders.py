"""Shared PPO observation builders: module functions == env methods.

The workbench PPO option (workbench/ppo_policy.py) feeds the net through the
module-level builders; these tests pin them to the env methods the net was
trained through. Runs on a REAL RunManager but never advances into combat
(the run opens on the multi-option Neow event), so no MCTS and no slow marker.
"""

import glob
import os
import re

import pytest

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
PY = os.path.join(ROOT, "sts2-pycli", "python")


@pytest.fixture(autouse=True)
def _env(monkeypatch):
    monkeypatch.delenv("STS2_RUNAI_VALUE", raising=False)
    monkeypatch.syspath_prepend(PY)


def _fresh_env(seed=7):
    from ppo.env import PpoRunEnv

    return PpoRunEnv(seed=seed)


def test_module_builders_match_env_methods():
    from ppo import env as ppo_env

    env = _fresh_env()
    rm = env.rm
    phase = rm.phase()
    actions = rm.available_actions()
    assert phase != "combat" and len(actions) > 1  # Neow multi-option event

    assert ppo_env.metrics_snapshot(rm) == env.metrics_snapshot()
    masked_fn = ppo_env.mask_rejectable(rm, phase, actions)
    masked_method = env._mask_rejectable(phase, actions)
    assert masked_fn == masked_method

    surface = ppo_env.classify_surface(phase, masked_fn)
    obs_fn = ppo_env.observe(rm, phase, surface, seed=env.seed, ascension=env.ascension)
    obs_method = env.observe(phase, surface)
    assert obs_fn == obs_method


def test_distribution_matches_greedy_sample():
    import numpy as np
    from ppo.env import Decision
    from ppo.net_policy import NetPolicy

    env = _fresh_env(seed=11)
    d = env.reset()
    assert isinstance(d, Decision)  # run opens on the Neow event, pre-combat

    pol = NetPolicy(None)  # random init — distribution/sample consistency only
    probs, value = pol.distribution(d.obs, d.actions, d.metrics)
    assert len(probs) == len(d.actions)
    assert abs(sum(probs) - 1.0) < 1e-5
    greedy_idx, _, greedy_v, _ = pol.sample(
        d.obs, d.actions, d.metrics, np.random.default_rng(0), greedy=True
    )
    assert greedy_idx == max(range(len(probs)), key=probs.__getitem__)
    assert abs(value - greedy_v) < 1e-6


def _snapshot_path():
    paths = glob.glob(os.path.join(ROOT, "sts2-pycli", "data", "ppo", "ppo_fullrun_it*.pt"))
    assert paths, "Task 1 snapshot missing from data/ppo/"
    # Select by numeric iteration, not lexicographic sort (4-digit vs 3-digit safety)
    iter_re = re.compile(r"ppo_fullrun_it(\d+)\.pt$")
    best: tuple[int, str] | None = None
    for path in paths:
        m = iter_re.search(path)
        if m and (best is None or int(m.group(1)) > best[0]):
            best = (int(m.group(1)), path)
    return best[1] if best else None


def test_ppo_policy_evaluate_and_recommend(monkeypatch):
    import sts2_rs
    from workbench import ppo_policy

    monkeypatch.setenv("STS2_WB_PPO_CKPT", _snapshot_path())
    ppo_policy._reset_for_tests()
    assert ppo_policy.available()

    # Same construction as RustGame.start_faithful / PpoRunEnv: run opens on
    # the multi-option Neow event — a PPO-owned surface, no combat involved.
    rm = sts2_rs.RunManager(seed=3, character="Ironclad", neow_start=True)
    ev = ppo_policy.evaluate(rm)
    assert ev is not None and ev.surface == "event"
    assert ev.probs is not None and len(ev.probs) == len(ev.actions)
    assert ev.chosen == max(range(len(ev.probs)), key=ev.probs.__getitem__)
    assert ev.actions[ev.chosen] in rm.available_actions()

    rec = ppo_policy.recommend(rm)
    assert rec is not None
    verb, args = rec
    assert isinstance(verb, str) and isinstance(args, dict)


def test_ppo_policy_unavailable_degrades(monkeypatch):
    import sts2_rs
    from workbench import ppo_policy

    monkeypatch.setenv("STS2_WB_PPO_CKPT", "/nonexistent/ckpt.pt")
    ppo_policy._reset_for_tests()
    assert not ppo_policy.available()
    rm = sts2_rs.RunManager(seed=3, character="Ironclad", neow_start=True)
    ev = ppo_policy.evaluate(rm)
    assert ev is not None and ev.probs is None and ev.error
    assert ppo_policy.recommend(rm) is None
    ppo_policy._reset_for_tests()


def _faithful_session(tmp_path, seed=3):
    from workbench.branching import BranchingLiveSession

    sess = BranchingLiveSession(logs_dir=str(tmp_path), backend="rust")
    sess.start_faithful("Ironclad", seed)
    return sess


def test_session_run_policy_selector(tmp_path, monkeypatch):
    from workbench import ppo_policy

    monkeypatch.setenv("STS2_WB_PPO_CKPT", _snapshot_path())
    ppo_policy._reset_for_tests()
    sess = _faithful_session(tmp_path)

    sess.set_run_mode(off_combat_brain="heuristic")
    assert sess.off_combat_brain == "heuristic"
    baseline = sess.recommended_action()
    assert baseline is not None and "policy" not in baseline  # heuristic untouched

    with pytest.raises(ValueError):
        sess.set_run_mode(off_combat_brain="bogus")
    sess.set_run_mode(off_combat_brain="ppo")
    rec = sess.recommended_action()
    assert rec is not None and rec.get("policy") == "ppo" and "fallback" not in rec

    panel = sess.ppo_policy_panel()
    assert panel is not None and panel["ppo_available"]
    assert panel["surface"] == "event"  # Neow
    probs = [o["prob"] for o in panel["options"]]
    assert abs(sum(probs) - 1.0) < 1e-5
    best = [o for o in panel["options"] if o["is_best"]]
    assert len(best) == 1
    # The greedy recommendation IS the panel's argmax option.
    assert rec["action"] == best[0]["action"] and rec["args"] == best[0]["args"]
    ppo_policy._reset_for_tests()


def test_ppo_policy_evaluate_degrades_on_inference_error(monkeypatch):
    import sts2_rs
    from workbench import ppo_policy

    monkeypatch.setenv("STS2_WB_PPO_CKPT", _snapshot_path())
    ppo_policy._reset_for_tests()
    assert ppo_policy.available()

    def _raise(*_args, **_kwargs):
        raise RuntimeError("boom")

    monkeypatch.setattr(ppo_policy._get_policy(), "distribution", _raise)

    rm = sts2_rs.RunManager(seed=3, character="Ironclad", neow_start=True)
    ev = ppo_policy.evaluate(rm)
    assert ev is not None
    assert ev.probs is None and ev.value is None and ev.chosen is None
    assert ev.error is not None and ev.error.startswith("inference failed")

    assert ppo_policy.recommend(rm) is None
    ppo_policy._reset_for_tests()


def test_session_ppo_falls_back_when_net_missing(tmp_path, monkeypatch):
    from workbench import ppo_policy

    monkeypatch.setenv("STS2_WB_PPO_CKPT", "/nonexistent/ckpt.pt")
    ppo_policy._reset_for_tests()
    sess = _faithful_session(tmp_path)
    sess.set_run_mode(off_combat_brain="ppo")
    sess.set_run_mode(ppo_impl="python")
    rec = sess.recommended_action()
    assert rec is not None and rec.get("fallback") is True  # heuristic answered
    panel = sess.ppo_policy_panel()
    assert panel is not None and panel["ppo_available"] is False and panel["error"]
    ppo_policy._reset_for_tests()


def test_rust_backend_degrades_on_corrupt_onnx(tmp_path, monkeypatch):
    """A present-but-unloadable ONNX makes the Rust PpoRunAgent constructor
    panic (pyo3 PanicException = BaseException); the backend must degrade to
    unavailable, never propagate out of available()/evaluate()."""
    from workbench import ppo_policy

    bad = tmp_path / "not_really.onnx"
    bad.write_text("this is not an onnx model")
    monkeypatch.setenv("STS2_WB_PPO_ONNX", str(bad))
    ppo_policy._reset_for_tests()
    # Must NOT raise (would be a PanicException escaping except Exception):
    assert ppo_policy.available("rust") is False
    import sts2_rs

    rm = sts2_rs.RunManager(seed=3, character="Ironclad", neow_start=True)
    ev = ppo_policy.evaluate(rm, impl="rust")
    assert ev is not None and ev.probs is None and ev.error is not None
    ppo_policy._reset_for_tests()


def test_shop_remove_label_names_the_deck_card():
    from workbench.ppo_policy import shop_remove_label

    deck = [("Strike", False), ("AscendersBane", False), ("PerfectedStrike", True)]
    assert shop_remove_label(deck, 1) == "Remove Ascenders Bane (deck #1)"
    assert shop_remove_label(deck, 2) == "Remove Perfected Strike+ (deck #2)"
    assert shop_remove_label(deck, 99) is None  # out of range -> caller falls back
