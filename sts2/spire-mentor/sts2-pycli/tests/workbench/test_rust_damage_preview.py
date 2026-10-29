"""Per-enemy modified-damage preview on combat hand cards (rust backend).

The workbench shows the live, fully-modified attack number on a card — "9" on a
Strike aimed at a Vulnerable target instead of the base "6" — by folding the
engine's own ``compute_modified_damage`` stage (Strength / Vulnerable / Weak /
relic-additive) into a per-enemy ``damage_preview`` map attached to each hand
card. These guard that the binding is wired and faithful. Requires the native
``sts2_rs`` extension.
"""

from __future__ import annotations

import tempfile

import pytest

pytest.importorskip("sts2_rs", reason="rust backend requires the sts2_rs extension")

import sts2_rs
from workbench.backends.rust_game import build_combat_view
from workbench.branching import BranchingLiveSession
from workbench.options import normalize_options


def _drive_to_combat(sess: BranchingLiveSession, state: dict, max_steps: int = 40) -> dict:
    for _ in range(max_steps):
        if state["decision"] == "combat_play":
            return state
        opts = normalize_options(state)
        assert opts, f"no navigable options at decision {state['decision']!r}"
        o = opts[0]
        state = sess.apply(o["action"], o.get("args") or {})["state"]
    raise AssertionError("did not reach combat within step budget")


def test_attack_cards_carry_a_per_enemy_damage_preview() -> None:
    with tempfile.TemporaryDirectory() as d:
        sess = BranchingLiveSession(logs_dir=d, backend="rust", loc={})
        # Close the session before the tempdir exits -- the open
        # session log otherwise blocks rmtree on Windows (WinError 32).
        try:
            state = sess.start(character="Ironclad", seed="PREVIEWTEST", ascension=0)
            state = _drive_to_combat(sess, state)

            enemy_indices = {e["index"] for e in state.get("enemies") or []}
            assert enemy_indices, "combat should have at least one enemy"

            attack_seen = False
            for card in state.get("hand") or []:
                base = (card.get("stats") or {}).get("damage")
                preview = card.get("damage_preview")
                if not base:
                    # Non-attack cards (Defend, …) get no preview.
                    assert preview is None
                    continue
                attack_seen = True
                assert preview, f"attack card {card['id']} should carry a damage_preview"
                # One entry per living enemy, each a positive int. With no debuffs in
                # play on turn 1 the modified value equals the base.
                assert {int(k) for k in preview} == enemy_indices
                for v in preview.values():
                    assert isinstance(v, int) and v >= base
            assert attack_seen, "Ironclad's opening hand should include an attack"

        finally:
            sess.quit()


def test_vulnerable_amplifies_the_preview() -> None:
    """Strike's preview against a Vulnerable target is ``floor(base * 1.5)``.

    Mirrors the engine's canonical 6 → 9 Vulnerable assertion, exercised through
    the workbench wire path (``build_combat_view``) rather than the raw binding.
    """
    with tempfile.TemporaryDirectory() as d:
        sess = BranchingLiveSession(logs_dir=d, backend="rust", loc={})
        # Close the session before the tempdir exits -- the open
        # session log otherwise blocks rmtree on Windows (WinError 32).
        try:
            state = sess.start(character="Ironclad", seed="PREVIEWTEST", ascension=0)
            _drive_to_combat(sess, state)

            rm = sess.game.rm  # type: ignore[attr-defined]
            cs = rm.combat_state()
            wire = sts2_rs.state_to_dict(cs)

            # Inject Vulnerable(2) onto the first living enemy and rebuild the state.
            enc = wire["encounter"]
            target_pos = next(i for i, e in enumerate(enc) if not e.get("is_dead"))
            enc[target_pos].setdefault("powers", []).append({"name": "VULNERABLE", "amount": 2})
            cs2 = sts2_rs.state_from_dict(wire)

            view = build_combat_view(cs2, "Ironclad", legal=rm.available_actions())
            strike = next((c for c in view["hand"] if (c.get("stats") or {}).get("damage")), None)
            assert strike is not None, "expected an attack card in hand"
            base = strike["stats"]["damage"]
            preview = strike["damage_preview"]
            assert preview[str(target_pos)] == (base * 3) // 2  # floor(base * 1.5)

        finally:
            sess.quit()
