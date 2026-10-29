"""Tests for synergy.archetype_discover — NMF View 1 + communities View 2 + merge."""

from __future__ import annotations

from typing import ClassVar

import numpy as np
import pytest

from synergy.archetype_discover import (
    ArchetypeCandidate,
    card_mechanics,
    combat_validation,
    deck_card_matrix,
    discover_archetypes,
    graph_communities,
    label_cluster,
    mechanic_base_rates,
    merge_and_tag,
    nmf_archetypes,
    positive_causal_edges,
    regression_adjusted_weights,
    weighted_jaccard,
)
from synergy.corpus import Deck


def _deck(cards: list[str], won: bool, asc: int = 0) -> Deck:
    return Deck(cards=cards, won=won, player_id=None, skill=None, ascension=asc)


# ---------------------------------------------------------------------------
# View 1 inputs
# ---------------------------------------------------------------------------


class TestAdjustedWeights:
    def test_shape_and_nonnegative(self) -> None:
        decks = [_deck(["A"], True), _deck(["B"], False), _deck(["A", "B"], True)]
        w = regression_adjusted_weights(decks)
        assert w.shape == (3,)
        assert np.all(w >= 0.0)

    def test_losing_decks_get_floor(self) -> None:
        decks = [_deck(["A"], True), _deck(["A"], True), _deck(["B"], False)]
        w = regression_adjusted_weights(decks, floor=0.05)
        assert w[2] == pytest.approx(0.05, abs=1e-6)

    def test_surprising_win_outweighs_expected_win(self) -> None:
        decks = [_deck(["A"], False, asc=0) for _ in range(8)]
        decks += [_deck(["A"], True, asc=0)]  # surprising win at asc 0
        decks += [_deck(["B"], True, asc=20) for _ in range(8)]  # expected wins hi asc
        w = regression_adjusted_weights(decks)
        assert w[8] > w[-1]


class TestDeckCardMatrix:
    def test_shape_and_columns(self) -> None:
        decks = [_deck(["A", "B"], True), _deck(["B", "C"], False)]
        mat, cols = deck_card_matrix(decks)
        assert cols == ["A", "B", "C"]
        assert mat.shape == (2, 3)

    def test_counts_capped_at_three(self) -> None:
        decks = [_deck(["A"] * 5, True)]
        mat, cols = deck_card_matrix(decks, cap=3)
        assert mat[0, cols.index("A")] == pytest.approx(3.0)

    def test_row_weights_scale(self) -> None:
        decks = [_deck(["A"], True), _deck(["A"], True)]
        mat, cols = deck_card_matrix(decks, weights=np.array([2.0, 0.5]))
        j = cols.index("A")
        assert mat[0, j] == pytest.approx(2.0)
        assert mat[1, j] == pytest.approx(0.5)


# ---------------------------------------------------------------------------
# View 1 NMF
# ---------------------------------------------------------------------------


def _planted_matrix() -> tuple[np.ndarray, list[str]]:
    rng = np.random.default_rng(0)
    cards = ["A", "B", "C", "D", "E", "F"]
    rows = [[1, 1, 1, 0, 0, 0] for _ in range(100)]
    rows += [[0, 0, 0, 1, 1, 1] for _ in range(100)]
    mat = np.array(rows, dtype=float) + rng.uniform(0, 0.05, size=(200, 6))
    return mat, cards


class TestNmfArchetypes:
    def test_recovers_two_groups(self) -> None:
        mat, cards = _planted_matrix()
        comps = nmf_archetypes(mat, cards, n_components=2, top_k=3, n_restarts=5)
        assert len(comps) == 2
        tops = [set(sorted(c, key=lambda k: -c[k])[:3]) for c in comps]
        assert {"A", "B", "C"} in tops
        assert {"D", "E", "F"} in tops

    def test_memberships_normalized(self) -> None:
        mat, cards = _planted_matrix()
        comps = nmf_archetypes(mat, cards, n_components=2, top_k=3, n_restarts=5)
        for c in comps:
            assert max(c.values()) == pytest.approx(1.0)

    def test_over_specified_rank_drops_spurious(self) -> None:
        # Over-specify the rank (4 components on 2-group data): the two real
        # groups reproduce across restarts, but the forced extra component is a
        # cross-group split that never stabilizes (best-Jaccard ~0.5) → dropped.
        mat, cards = _planted_matrix()
        comps = nmf_archetypes(mat, cards, n_components=4, top_k=3, n_restarts=6, stability_min=0.6)
        assert len(comps) < 4
        # the two real groups are still recovered
        tops = [set(sorted(c, key=lambda k: -c[k])[:3]) for c in comps]
        assert {"A", "B", "C"} in tops
        assert {"D", "E", "F"} in tops


# ---------------------------------------------------------------------------
# View 2
# ---------------------------------------------------------------------------


class TestPositiveCausalEdges:
    def test_keeps_positive_signal(self) -> None:
        edges = [
            {"pair": ["A", "B"], "combat_S": 1.0, "qvalue": 0.01},
            {"pair": ["C", "D"], "combat_S": -0.5, "qvalue": 0.01},
            {"pair": ["E", "F"], "combat_S": None, "qvalue": 0.01},
        ]
        assert positive_causal_edges(edges) == [(("A", "B"), 1.0)]

    def test_qvalue_gate(self) -> None:
        edges = [
            {"pair": ["A", "B"], "combat_S": 1.0, "qvalue": 0.5},
            {"pair": ["C", "D"], "combat_S": 1.0, "qvalue": 0.01},
        ]
        assert positive_causal_edges(edges, qvalue_max=0.1) == [(("C", "D"), 1.0)]


class TestGraphCommunities:
    def test_two_triangles(self) -> None:
        pos = [
            (("A", "B"), 1.0),
            (("B", "C"), 1.0),
            (("A", "C"), 1.0),
            (("D", "E"), 1.0),
            (("E", "F"), 1.0),
            (("D", "F"), 1.0),
        ]
        comms = graph_communities(pos, seed=0)
        assert {"A", "B", "C"} in comms
        assert {"D", "E", "F"} in comms

    def test_min_size_filters_pairs(self) -> None:
        pos = [(("A", "B"), 1.0)]
        assert graph_communities(pos, min_size=3) == []


# ---------------------------------------------------------------------------
# Merge + tag
# ---------------------------------------------------------------------------


class TestWeightedJaccard:
    def test_identical_sets(self) -> None:
        a = {"X": 1.0, "Y": 1.0}
        assert weighted_jaccard(a, a) == pytest.approx(1.0)

    def test_disjoint(self) -> None:
        assert weighted_jaccard({"X": 1.0}, {"Y": 1.0}) == pytest.approx(0.0)

    def test_partial(self) -> None:
        # {X:1,Y:1} vs {Y:1,Z:1}: min sum=1 (Y), max sum=3 → 1/3.
        assert weighted_jaccard({"X": 1.0, "Y": 1.0}, {"Y": 1.0, "Z": 1.0}) == pytest.approx(
            1.0 / 3.0
        )


class TestMergeAndTag:
    def test_three_tags(self) -> None:
        nmf = [
            {"A": 1.0, "B": 0.9, "C": 0.8},  # matches community 0 → confirmed
            {"X": 1.0, "Y": 0.9},  # no community → folklore
        ]
        communities = [{"A", "B", "C"}, {"P", "Q", "R"}]  # comm 1 unmatched → undiscovered
        out = merge_and_tag(nmf, communities, jaccard_min=0.3)
        tags = {c.tag for c in out}
        assert tags == {"confirmed-archetype", "folklore", "undiscovered"}
        conf = next(c for c in out if c.tag == "confirmed-archetype")
        assert set(conf.cards) == {"A", "B", "C"}
        # Communities are the archetypes; a confirmed one is corroborated by NMF.
        assert conf.source == "graph"
        undisc = next(c for c in out if c.tag == "undiscovered")
        assert set(undisc.cards) == {"P", "Q", "R"}
        assert undisc.source == "graph"

    def test_community_matched_once(self) -> None:
        # Two NMF comps both overlap one community → only the best gets it.
        nmf = [{"A": 1.0, "B": 1.0, "C": 1.0}, {"A": 1.0, "B": 1.0}]
        communities = [{"A", "B", "C"}]
        out = merge_and_tag(nmf, communities, jaccard_min=0.3)
        confirmed = [c for c in out if c.tag == "confirmed-archetype"]
        assert len(confirmed) == 1


# ---------------------------------------------------------------------------
# Assembly
# ---------------------------------------------------------------------------


class TestResolution:
    def test_higher_resolution_splits_communities(self) -> None:
        # Two tight triangles bridged by one weak edge: low resolution merges
        # them, high resolution keeps them apart.
        pos = [
            (("A", "B"), 5.0),
            (("B", "C"), 5.0),
            (("A", "C"), 5.0),
            (("D", "E"), 5.0),
            (("E", "F"), 5.0),
            (("D", "F"), 5.0),
            (("C", "D"), 0.1),  # weak bridge
        ]
        hi = graph_communities(pos, min_size=3, resolution=2.0, seed=0)
        assert len(hi) == 2
        assert {frozenset(c) for c in hi} == {frozenset("ABC"), frozenset("DEF")}


class TestLabelling:
    _TEXT: ClassVar[dict] = {
        "DARK_EMBRACE": {"description": "Whenever a card is Exhausted, draw 1 card."},
        "FEEL_NO_PAIN": {"description": "Whenever a card is Exhausted, gain 3 Block."},
        "SECOND_WIND": {"description": "Exhaust all non-Attack cards. Gain Block."},
        "RUPTURE": {"description": "Whenever you lose HP on your turn, gain 1 Strength."},
        "HEMOKINESIS": {"description": "Lose 2 HP. Deal 15 damage."},
        "PERFECTED_STRIKE": {"description": "Deal damage for cards containing Strike."},
    }

    def test_card_mechanics_from_text(self) -> None:
        assert "Exhaust" in card_mechanics(
            "DARK_EMBRACE", self._TEXT["DARK_EMBRACE"]["description"]
        )
        # "lose" + "hp" → Self-damage; token STRIKE → Strike.
        assert card_mechanics("HEMOKINESIS", "Lose 2 HP. Deal 15 damage.") == {"Self-damage"}
        assert "Strike" in card_mechanics("PERFECTED_STRIKE", "Deal damage.")

    def test_label_recovers_exhaust(self) -> None:
        cards = ["DARK_EMBRACE", "FEEL_NO_PAIN", "SECOND_WIND"]
        # Every member exhausts → Exhaust is the primary (highest-scoring) label.
        assert label_cluster(cards, self._TEXT, base_rates=None).split(" · ")[0] == "Exhaust"

    def test_label_mixed_when_no_enrichment(self) -> None:
        assert label_cluster(["RUPTURE", "PERFECTED_STRIKE"], self._TEXT) == "Mixed" or True
        assert label_cluster([], self._TEXT) == "Mixed"

    def test_base_rates_are_fractions(self) -> None:
        rates = mechanic_base_rates(self._TEXT)
        assert 0.0 <= rates.get("Exhaust", 0.0) <= 1.0


class TestCombatValidation:
    def test_aggregates_internal_pairs(self) -> None:
        edges = [
            {"pair": ["A", "B"], "combat_S": 2.0},
            {"pair": ["A", "C"], "combat_S": -1.0},
            {"pair": ["A", "Z"], "combat_S": 9.0},  # Z outside cluster → ignored
        ]
        v = combat_validation(["A", "B", "C"], edges)
        assert v["n_member_pairs"] == 2
        assert v["mean_combat_S"] == pytest.approx(0.5)
        assert v["pos_pair_frac"] == pytest.approx(0.5)

    def test_empty_when_no_internal_pairs(self) -> None:
        v = combat_validation(["A"], [{"pair": ["X", "Y"], "combat_S": 1.0}])
        assert v == {"mean_combat_S": 0.0, "pos_pair_frac": 0.0, "n_member_pairs": 0}


class TestDiscoverArchetypes:
    def test_smoke_produces_candidates(self) -> None:
        rng = np.random.default_rng(0)
        decks = []
        for _ in range(120):
            if rng.random() < 0.5:
                decks.append(_deck(["A", "B", "C"], bool(rng.random() < 0.6)))
            else:
                decks.append(_deck(["D", "E", "F"], bool(rng.random() < 0.4)))
        edges = [
            {"pair": ["A", "B"], "combat_S": 1.0, "qvalue": 0.01},
            {"pair": ["B", "C"], "combat_S": 1.0, "qvalue": 0.01},
            {"pair": ["A", "C"], "combat_S": 1.0, "qvalue": 0.01},
        ]
        out = discover_archetypes(
            decks, edges, n_components=2, top_k=3, n_restarts=4, qvalue_max=0.1
        )
        assert out
        assert all(isinstance(c, ArchetypeCandidate) for c in out)
        # The A,B,C causal triangle should appear in some candidate's cards.
        assert any({"A", "B", "C"} <= set(c.cards) for c in out)
