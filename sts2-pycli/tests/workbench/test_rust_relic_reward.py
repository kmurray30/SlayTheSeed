"""Relic-reward rendering for the Rust backend.

An event-granted relic (e.g. the Lost Wisp event) opens a relic reward; the
adapter must name the relic (title loc key + text_key for art), not show a bare
"Take relic".
"""

from __future__ import annotations

import pytest

pytest.importorskip("sts2_rs", reason="rust backend requires the sts2_rs extension")

from fake_rm import FakeRm
from workbench.backends.rust_game import RustGame


class _FakeRm(FakeRm):
    """A reward screen whose only offers are a relic (no card picks)."""

    def __init__(self, actions: list[dict], relic_ids: list[str]):
        super().__init__(phase="card_reward", actions=actions)
        self._relic_ids = relic_ids

    def relic_reward_ids(self) -> list[str]:
        return self._relic_ids


def test_relic_reward_names_the_relic() -> None:
    g = RustGame()
    g.rm = _FakeRm(
        actions=[{"action": "pick_relic_reward"}, {"action": "skip_relic"}],
        relic_ids=["LostWisp"],
    )
    state = g.get_state()
    assert state["decision"] == "event_choice"  # a relic-only reward renders as options
    opts = state["options"]
    # The take option names the relic via its loc key (server resolves it to
    # "Lost Wisp") and carries text_key for the relic art lookup.
    assert opts[0]["title"] == "LOST_WISP.title"
    assert opts[0]["text_key"] == "LOST_WISP"
    assert opts[1]["title"] == "Skip"
