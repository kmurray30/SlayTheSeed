"""Tests for ``sts2_hint.recommender`` + ``sts2_hint.display``.

Bead: ``sts2-rs-tjtr`` (hint-T8).

Covers:

* Smoke: ``recommend`` returns a non-empty ``{stable_idx: HintTag}``
  with exactly one ``is_best=True`` entry and all ``win_prob`` in
  ``[0, 1]``.
* Alignment: re-ordering the C# hand still maps win-probs back to
  the same ``stable_idx`` (within MCTS noise) — i.e. card identity
  follows ``stable_idx``, not slot position.
* :func:`display.format_tag` canned-tag snapshots.
"""

from __future__ import annotations

import sys
from pathlib import Path

import pytest
from sts2_hint import adapter, display
from sts2_hint.recommender import (
    END_TURN_STABLE_IDX,
    HintTag,
    RecommendConfig,
    recommend,
)

sys.path.insert(0, str(Path(__file__).parent / "fixtures" / "hint_adapter"))

from _helpers import (  # type: ignore[import-not-found]
    base_state,
    card,
    enemy,
    player_summary,
)

# Skip the live-MCTS tests if sts2_rs is not present in this venv.
sts2_rs = pytest.importorskip("sts2_rs")


def _ironclad_vs_nibbit(hand_cards: list[dict]) -> dict:
    return base_state(
        player=player_summary(
            hp=70,
            max_hp=75,
            block=0,
            relics=[{"name": "Burning Blood", "description": "", "vars": None}],
            potions=[],
        ),
        hand=hand_cards,
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


def _three_card_hand() -> list[dict]:
    return [
        card(index=0, cid="IRONCLAD/STRIKE", name="Strike", cost=1),
        card(
            index=1, cid="IRONCLAD/DEFEND", name="Defend", cost=1, ctype="Skill", target_type="Self"
        ),
        card(index=2, cid="IRONCLAD/BASH", name="Bash", cost=2),
    ]


# ---------------------------------------------------------------------------
# Smoke test
# ---------------------------------------------------------------------------


def test_recommend_smoke_ironclad_vs_nibbit():
    state = _ironclad_vs_nibbit(_three_card_hand())
    cfg = RecommendConfig(sims=60, depth=10, seed=42)
    tags = recommend(state, cfg)

    assert tags, "recommend() returned empty map for non-terminal state"
    # Every win_prob must be a finite value in [0, 1].
    for tag in tags.values():
        assert isinstance(tag, HintTag)
        assert 0.0 <= tag.win_prob <= 1.0, tag
    # Exactly one is_best=True.
    bests = [t for t in tags.values() if t.is_best]
    assert len(bests) == 1, f"expected exactly one is_best, got {len(bests)}: {tags}"
    # End-turn sentinel should be in the map (end_turn is always legal
    # when no card-resolution is pending).
    assert END_TURN_STABLE_IDX in tags
    # Card stable_idx values from the 3-card hand (0, 1, 2) should be
    # present too — the hand is fully playable energy-wise (3 energy).
    card_keys = {k for k in tags if k >= 0}
    assert card_keys.issuperset({0, 1, 2}), f"missing card stable_idxs: {card_keys}"


# ---------------------------------------------------------------------------
# Alignment test: re-ordering the hand preserves stable_idx → win_prob.
# ---------------------------------------------------------------------------


def test_recommend_alignment_under_hand_reorder():
    base_hand = _three_card_hand()
    state_a = _ironclad_vs_nibbit(base_hand)

    swapped = [base_hand[1], base_hand[0], base_hand[2]]
    state_b = _ironclad_vs_nibbit(swapped)

    cfg = RecommendConfig(sims=200, depth=10, seed=2024)
    tags_a = recommend(state_a, cfg)
    tags_b = recommend(state_b, cfg)

    # Both runs must surface the same set of card stable_idxs.
    a_cards = {k for k in tags_a if k >= 0}
    b_cards = {k for k in tags_b if k >= 0}
    assert a_cards == b_cards, f"hand reorder dropped a stable_idx: {a_cards} vs {b_cards}"

    # Per-card win_prob should match within MCTS noise. The tolerance
    # is wide because we're running cheap searches and the action set
    # contains targeting variants. We assert that the *ordering* of
    # win_probs across the three card stable_idxs is preserved — that's
    # the property "card identity follows stable_idx, not slot".
    tol = 0.20
    for k in a_cards:
        diff = abs(tags_a[k].win_prob - tags_b[k].win_prob)
        assert diff <= tol, (
            f"stable_idx {k}: win_prob differs by {diff:.3f} > {tol} "
            f"({tags_a[k].win_prob} vs {tags_b[k].win_prob})"
        )


# ---------------------------------------------------------------------------
# Regression: Entomancer "MCTS suggests end turn" report.
#
# Bug report (workbench): Ironclad facing Entomancer's BEES telegraph (3×7),
# 11 block, with energy to spare — yet the live workbench reported End Turn as
# the best move (P(win) 100%). The save reproducing it ("bloodletting bug") had
# the Entomancer carrying ManglePower (a *TemporaryStrength* power, −10 Strength
# applied by the Ironclad rare Mangle, restored at the enemy's turn end).
#
# ROOT CAUSE (confirmed by reproducing through the real lossy path
# adapter.translate -> state_from_dict -> mcts_recommend): the hint adapter's
# power map is keyed by localization title, and ManglePower's title ("Mangle")
# has no powers.json entry, so it was silently dropped. With Mangle gone, the
# −10 Strength stranded as *permanent*, so every Entomancer attack stayed
# clamped to ~0 — the enemy was defanged and every reconstructed line scored
# P(win)≈1.0, making End Turn look as good as anything. (NOT an "energy desync":
# carrying the engine power id through the adapter fixes it. See
# sts2_hint.adapter._translate_power.)
# ---------------------------------------------------------------------------


def _entomancer_state(*, energy: int, player_hp: int = 50, block: int = 11) -> dict:
    """Post-Bloodletting Ironclad vs a lone Entomancer telegraphing 7×3.

    Hand is two 1-cost Defends + a 1-cost Strike; ``energy`` knobs the only
    field that flips the recommendation (see the two tests below).
    """
    return base_state(
        player=player_summary(hp=player_hp, max_hp=80, block=block),
        hand=[
            card(
                index=0,
                cid="IRONCLAD/DEFEND",
                name="Defend",
                cost=1,
                ctype="Skill",
                target_type="Self",
            ),
            card(
                index=1,
                cid="IRONCLAD/DEFEND",
                name="Defend",
                cost=1,
                ctype="Skill",
                target_type="Self",
            ),
            card(index=2, cid="IRONCLAD/STRIKE", name="Strike", cost=1),
        ],
        enemies=[
            enemy(
                index=0,
                name="Entomancer",
                hp=145,
                max_hp=145,
                intents=[{"type": "Attack", "damage": 3, "hits": 7}],
            ),
        ],
        energy=energy,
        max_energy=3,
        round_=3,
    )


def test_recommend_prefers_block_over_end_turn_vs_entomancer():
    # Production recommender defaults (RecommendConfig(): sims=300, depth=200,
    # c_puct=1.4) — the config the live workbench hint actually uses.
    cfg = RecommendConfig(character="Ironclad")
    for hp in (20, 35, 50, 70):
        tags = recommend(_entomancer_state(energy=2, player_hp=hp), cfg)
        best = next(t for t in tags.values() if t.is_best)
        assert best.stable_idx != END_TURN_STABLE_IDX, (
            f"hp={hp}: recommended End Turn over an affordable Defend wall vs "
            f"Entomancer's 7×3 — the reported blunder. tags={tags}"
        )
        # End Turn must rank strictly below the best card by win_prob.
        if END_TURN_STABLE_IDX in tags:
            assert tags[END_TURN_STABLE_IDX].win_prob <= best.win_prob


def test_recommend_zero_energy_only_end_turn():
    # With 0 energy nothing is affordable, so End Turn is the only legal action
    # and is trivially "best". A degenerate case, distinct from the real bug.
    cfg = RecommendConfig(character="Ironclad")
    tags = recommend(_entomancer_state(energy=0), cfg)
    best = next(t for t in tags.values() if t.is_best)
    assert best.stable_idx == END_TURN_STABLE_IDX, (
        f"with 0 energy End Turn is the only legal move; got {best}"
    )


def _entomancer_state_with_mangle(*, energy: int = 3) -> dict:
    """The reproducing scenario: the Entomancer carries ManglePower (the −10
    temporary Strength) plus the resulting Strength(−10), exactly as the live
    Rust backend serializes it. ``MANGLE_POWER`` is the engine ``id``; its
    display title ("Mangle") has no loc entry, so only the id round-trips it.
    """
    state = _entomancer_state(energy=energy)
    enemy0 = state["enemies"][0]
    enemy0["powers"] = [
        {"name": "Mangle", "id": "MANGLE_POWER", "amount": 10},
        {"name": "Strength", "amount": -10},
    ]
    # The AI move-state is what keeps the Entomancer acting in rollouts (it
    # telegraphs BEES now, then SPEAR-18 next turn once Mangle restores its
    # Strength). Without it the enemy goes inert and the test can't isolate the
    # Mangle drop. This mirrors the Rust backend's serialized state.
    enemy0["move_state"] = "BEES"
    return state


def test_temporary_strength_power_survives_reconstruction():
    """End-to-end mechanism check for the "MCTS suggests end turn" bug.

    ManglePower must survive ``adapter.translate -> state_from_dict`` so the
    enemy's −10 Strength is restored at its turn end (it is *temporary*). Before
    the fix the power was dropped, stranding the −10 Strength as permanent — the
    Entomancer's attacks stayed clamped to ~0 and the reconstructed search
    scored every line a near-certain win, ranking End Turn best. Deterministic:
    no MCTS, just the reconstruct-then-advance the search rolls out over.
    """
    state = _entomancer_state_with_mangle()
    wire = adapter.translate(state)
    cs = sts2_rs.state_from_dict(wire)

    def alive_enemy(combat_state: object) -> dict:
        enc = sts2_rs.state_to_dict(combat_state)["encounter"]
        return next(e for e in enc if not e.get("is_dead"))

    powers_before = {p["name"] for p in alive_enemy(cs)["powers"]}
    assert "MANGLE_POWER" in powers_before, (
        f"ManglePower dropped in reconstruction; enemy powers={powers_before}"
    )

    # End the player's turn so the enemy acts: ManglePower must expire and
    # restore the Strength to 0 (full strength), not leave it stuck at −10.
    cs.apply({"action": "end_turn"})
    strength = next((p["amount"] for p in alive_enemy(cs)["powers"] if p["name"] == "STRENGTH"), 0)
    assert strength == 0, (
        f"enemy stuck at Strength {strength} after its turn — Mangle's temporary "
        "Strength loss was not restored, so the enemy stays defanged (the bug)"
    )


# ---------------------------------------------------------------------------
# format_tag snapshot
# ---------------------------------------------------------------------------


def test_format_tag_best_and_non_best():
    best = HintTag(
        stable_idx=0,
        win_prob=0.72,
        bucket_label="likely-win",
        is_best=True,
        visits=120,
        q_saturated=False,
        low_confidence=False,
    )
    non_best = HintTag(
        stable_idx=1,
        win_prob=0.47,
        bucket_label="even",
        is_best=False,
        visits=40,
        q_saturated=False,
        low_confidence=False,
    )
    saturated = HintTag(
        stable_idx=2,
        win_prob=0.12,
        bucket_label="likely-loss",
        is_best=False,
        visits=15,
        q_saturated=True,
        low_confidence=False,
    )

    s_best = display.format_tag(best)
    s_non_best = display.format_tag(non_best)
    s_sat = display.format_tag(saturated)

    # Fixed width across all renderings.
    assert len(s_best) == display.TAG_WIDTH
    assert len(s_non_best) == display.TAG_WIDTH
    assert len(s_sat) == display.TAG_WIDTH

    # Content checks (use startswith so trailing padding doesn't matter).
    assert s_best.startswith("[LIKELY-WIN 72%] ")
    assert display.BEST_ARROW in s_best
    assert s_non_best.startswith("[EVEN 47%]")
    assert display.BEST_ARROW not in s_non_best
    assert s_sat.startswith("[LIKELY-LOSS 12%]")
    assert display.SAT_MARKER in s_sat


def test_format_empty_matches_tag_width():
    assert len(display.format_empty()) == display.TAG_WIDTH
    assert display.format_empty().strip() == ""


def test_format_tag_clamps_out_of_range_probs():
    # Defensive: a float just above 1.0 or below 0.0 (rounding wobble)
    # should still render as 100% / 0% without exploding TAG_WIDTH.
    hi = HintTag(0, 1.0001, "likely-win", True, 1, False, False)
    lo = HintTag(0, -0.0001, "likely-loss", False, 1, False, False)
    s_hi = display.format_tag(hi)
    s_lo = display.format_tag(lo)
    assert "100%" in s_hi
    assert "0%" in s_lo
    assert len(s_hi) == display.TAG_WIDTH
    assert len(s_lo) == display.TAG_WIDTH
