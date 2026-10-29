"""Workbench↔PPO integration (slow): construction parity + greedy legality.

Mirrors tests/test_ppo_env_parity.py conventions (env fixture, slow marker).
"""

import glob
import os

import pytest

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
PY = os.path.join(ROOT, "sts2-pycli", "python")


@pytest.fixture(autouse=True)
def _env(monkeypatch):
    monkeypatch.delenv("STS2_RUNAI_VALUE", raising=False)
    monkeypatch.syspath_prepend(PY)


def _snapshot_path():
    paths = glob.glob(os.path.join(ROOT, "sts2-pycli", "data", "ppo", "ppo_fullrun_it*.pt"))
    assert paths, "Task 1 snapshot missing from data/ppo/"

    def _iteration(path: str) -> int:
        # ppo_fullrun_it390.pt -> 390. Select by the numeric iteration N, not
        # lexicographic sort — "it9.pt" would otherwise beat "it390.pt".
        name = os.path.basename(path)
        digits = name[len("ppo_fullrun_it") : -len(".pt")]
        return int(digits)

    return max(paths, key=_iteration)


def test_faithful_construction_obs_parity():
    """RustGame.start_faithful builds a RunManager whose shared-builder obs is
    IDENTICAL to PpoRunEnv's at the same seed — the workbench PPO option is
    in-distribution by construction on faithful runs."""
    from ppo import env as ppo_env
    from ppo.env import PpoRunEnv
    from workbench.backends.rust_game import RustGame

    for seed in (0, 3, 11):
        env = PpoRunEnv(seed=seed)
        game = RustGame()
        game.start_faithful(character="Ironclad", seed=seed)
        for rm in (env.rm, game.rm):
            assert rm.phase() != "combat"
        phase = env.rm.phase()
        actions = env.rm.available_actions()
        assert game.rm.available_actions() == actions
        surface = ppo_env.classify_surface(phase, actions)
        obs_env = ppo_env.observe(env.rm, phase, surface, seed=seed, ascension=0)
        obs_wb = ppo_env.observe(game.rm, phase, surface, seed=seed, ascension=0)
        assert obs_env == obs_wb


@pytest.mark.slow
@pytest.mark.timeout(0)  # one full run with eval-cheap combat MCTS
def test_snapshot_greedy_full_run_is_legal():
    """Drive one complete run with the committed snapshot, greedy, through the
    shared masking. PpoRunEnv.step raises on any engine-rejected action, so a
    Terminal outcome proves every PPO pick was legal end-to-end."""
    import numpy as np
    from ppo.env import Decision, PpoRunEnv, Terminal
    from ppo.net_policy import NetPolicy

    pol = NetPolicy(_snapshot_path())
    env = PpoRunEnv(seed=0)
    rng = np.random.default_rng(0)
    out = env.reset()
    while isinstance(out, Decision):
        idx, _, _, _ = pol.sample(out.obs, out.actions, out.metrics, rng, greedy=True)
        out = env.step(idx)
    assert isinstance(out, Terminal)
    assert out.outcome in ("won", "lost"), f"greedy run degenerated: {out.outcome}"


# ── shop card-removal service (rust backend) ─────────────────────────────────


class _StubRm:
    """Minimal rm for _translate: a shop phase with no pending selects."""

    def phase(self):
        return "shop"

    pending_deck_removals = 0
    pending_deck_upgrades = 0
    pending_deck_transforms = 0
    pending_deck_enchants = 0
    pending_card_grants = 0

    def smith_options(self):
        return []


def test_translate_select_cards_at_shop_maps_to_shop_remove_card():
    from workbench.backends.rust_game import RustGame

    g = RustGame()
    g.rm = _StubRm()
    native = g._translate("select_cards", {"indices": "7"})
    assert native == {"action": "shop_remove_card", "deck_idx": 7}


def _rust_game_at_shop():
    """Walk expert runs until a shop with the removal service on offer, then
    hand that live RunManager to a RustGame adapter (as the workbench would).
    """
    from ppo.env import Decision, PpoRunEnv, Terminal
    from workbench.backends.rust_game import RustGame

    for seed in range(20):
        env = PpoRunEnv(seed=seed, expert=True)
        for out in env.expert_run():
            if isinstance(out, Terminal):
                break
            assert isinstance(out, Decision)
            if env.rm.phase() == "shop" and any(
                a.get("action") == "shop_remove_card" for a in env.rm.available_actions()
            ):
                g = RustGame()
                g.rm = env.rm
                g.character = "Ironclad"
                return g
    pytest.skip("no expert run reached a shop with the removal service")


@pytest.mark.slow
@pytest.mark.timeout(0)  # walks real runs (combat MCTS) to reach a shop
def test_shop_removal_service_flow():
    g = _rust_game_at_shop()
    rm = g.rm

    # 1. The serialized shop advertises the removal service.
    state = g._serialize()
    assert state["decision"] == "shop"
    assert isinstance(state.get("card_removal_cost"), int) and state["card_removal_cost"] > 0

    # 2. remove_card opens a card_select over the engine's own offers
    #    (index == the action's raw deck_idx).
    sel = g.act("remove_card")
    assert sel.get("error") is None
    assert sel["decision"] == "card_select" and sel["context"] == "remove"
    offer_idxs = {
        int(a["deck_idx"]) for a in rm.available_actions() if a.get("action") == "shop_remove_card"
    }
    sel_idxs = {c["index"] for c in sel["cards"]}
    assert sel_idxs == offer_idxs and sel_idxs

    # 3. Skip cancels back to the shop, deck untouched.
    deck_before = len(rm.master_deck_detailed())
    back = g.act("skip_select")
    assert back["decision"] == "shop"
    assert len(rm.master_deck_detailed()) == deck_before

    # 4. Re-open and remove one card: deck shrinks, gold drops, back at shop.
    gold_before = int(rm.gold)
    sel = g.act("remove_card")
    pick = sorted(c["index"] for c in sel["cards"])[0]
    after = g.act("select_cards", indices=str(pick))
    assert after.get("error") is None
    assert after["decision"] == "shop"
    assert len(rm.master_deck_detailed()) == deck_before - 1
    assert int(rm.gold) < gold_before
