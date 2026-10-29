"""``build_combat_view`` must carry each enemy's raw ``kind`` onto the wire.

The server's ``_enrich`` disambiguates rigs by ``MonsterKind`` (``enemy["kind"]``)
for monsters whose segments share one display name — the three Decimillipede
segments all localize to "Decimillipede", so only ``kind`` tells them apart and
picks their per-segment rig / group. When the enemy dict omits ``kind``, that
whole kind-addressed path (Decimillipede segments, SpinyToad / Tunneler /
TestSubject state idles) silently falls back to sprites.
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


def test_enemies_carry_their_wire_kind() -> None:
    """Each ``build_combat_view`` enemy carries the encounter's raw ``kind``.

    Without it, ``_enrich`` cannot kind-address rigs (Decimillipede segments,
    state-dependent idles) and every such monster degrades to a static sprite.
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
            living = [c for c in wire["encounter"] if not c.get("is_dead")]
            assert living, "combat should have at least one living enemy"

            view = build_combat_view(cs, "Ironclad", legal=rm.available_actions())
            kinds = [e.get("kind") for e in view["enemies"]]
            assert kinds == [c["kind"] for c in living], (
                f"enemies must carry the wire kind; got {kinds}"
            )

        finally:
            sess.quit()
