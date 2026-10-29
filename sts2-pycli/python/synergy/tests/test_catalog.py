"""Tests for synergy.catalog — join + quadrant tagging.

Fixtures are hand-crafted minimal inputs that exercise the four quadrant
assignments (confirmed, myth, undiscovered, noise) without touching the corpus.
"""

from __future__ import annotations

import pytest

from synergy.catalog import Thresholds, build_catalog, card_label
from synergy.causal_combat import CombatRow
from synergy.causal_econ import EconRow
from synergy.observational import InteractionRow, ObsRow

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _obs(pmi: float) -> ObsRow:
    """Return an ObsRow for a synthetic pair with the given PMI."""
    return ObsRow(pmi=pmi, cooccur=10, p_a=0.5, p_b=0.5, p_ab=0.25)


def _inter(beta: float = 0.5, lo: float = 0.1, hi: float = 0.9) -> InteractionRow:
    """Return an InteractionRow (the interaction_lr candidate signal)."""
    return InteractionRow(beta_ab=beta, ci=(lo, hi), n_both=10, n=1000)


def _combat(ci_lo: float, ci_hi: float, n: int = 20) -> CombatRow:
    return CombatRow(median_interaction=ci_lo + (ci_hi - ci_lo) / 2, ci=(ci_lo, ci_hi), n=n)


def _econ(median: float, n: int = 10) -> EconRow:
    return EconRow(median=median, p25=median - 0.1, p75=median + 0.1, n=n)


# ---------------------------------------------------------------------------
# Minimal corpus builder
# ---------------------------------------------------------------------------
# Three synthetic pairs with known pmi values so we control the median threshold.
# pmi values: 1.0, 2.0, 3.0 → median = 2.0
# obs_high = pmi > 2.0 (PMI-only human-intent axis; spec §4)

PAIR_LOW = ("ALPHA", "BETA")  # pmi=1.0 → obs_low
PAIR_MED = ("ALPHA", "GAMMA")  # pmi=2.0 → obs_low (not strictly > median)
PAIR_HIGH = ("BETA", "GAMMA")  # pmi=3.0 → obs_high


def _build_three_pair_catalog(
    combat_map: dict | None = None,
    econ_map: dict | None = None,
) -> dict:
    """Build a catalog from three pairs with pmi 1, 2, 3."""
    obs = {
        PAIR_LOW: _obs(1.0),
        PAIR_MED: _obs(2.0),
        PAIR_HIGH: _obs(3.0),
    }
    interaction = {
        PAIR_LOW: _inter(),
        PAIR_MED: _inter(),
        PAIR_HIGH: _inter(),
    }
    fm = {PAIR_LOW: 0.1, PAIR_MED: 0.2, PAIR_HIGH: 0.3}
    card_frequency = {
        "ALPHA": 0.8,
        "BETA": 0.7,
        "GAMMA": 0.6,
    }
    return build_catalog(
        obs=obs,
        interaction=interaction,
        fm=fm,
        econ=econ_map,
        combat=combat_map,
        card_frequency=card_frequency,
        run_count=1000,
        character="Ironclad",
    )


# ---------------------------------------------------------------------------
# label derivation
# ---------------------------------------------------------------------------


class TestCardLabel:
    def test_strike_ironclad(self) -> None:
        assert card_label("STRIKE_IRONCLAD") == "Strike"

    def test_body_slam(self) -> None:
        assert card_label("BODY_SLAM") == "Body Slam"

    def test_bash(self) -> None:
        assert card_label("BASH") == "Bash"

    def test_iron_wave(self) -> None:
        assert card_label("IRON_WAVE") == "Iron Wave"

    def test_defend_ironclad(self) -> None:
        assert card_label("DEFEND_IRONCLAD") == "Defend"

    def test_upgraded_suffix(self) -> None:
        # Upgraded tokens like STRIKE_IRONCLAD+1 — strip suffix then label.
        # Note: the catalog only stores base token ids in edges/nodes; upgrades
        # are separate tokens.  But if one appears, handle gracefully.
        # We accept either form; just confirm no crash and reasonable output.
        result = card_label("BODY_SLAM+1")
        assert isinstance(result, str)
        assert len(result) > 0


# ---------------------------------------------------------------------------
# Schema / top-level structure
# ---------------------------------------------------------------------------


class TestCatalogSchema:
    def test_top_level_keys(self) -> None:
        catalog = _build_three_pair_catalog()
        assert set(catalog.keys()) == {"generated_at", "character", "run_count", "nodes", "edges"}

    def test_character(self) -> None:
        catalog = _build_three_pair_catalog()
        assert catalog["character"] == "Ironclad"

    def test_run_count(self) -> None:
        catalog = _build_three_pair_catalog()
        assert catalog["run_count"] == 1000

    def test_generated_at_is_string(self) -> None:
        catalog = _build_three_pair_catalog()
        assert isinstance(catalog["generated_at"], str)
        # ISO 8601 UTC ends with +00:00 or Z
        ts = catalog["generated_at"]
        assert "T" in ts

    def test_nodes_present(self) -> None:
        catalog = _build_three_pair_catalog()
        assert isinstance(catalog["nodes"], list)
        assert len(catalog["nodes"]) == 3  # ALPHA, BETA, GAMMA

    def test_node_keys(self) -> None:
        catalog = _build_three_pair_catalog()
        for node in catalog["nodes"]:
            assert "id" in node
            assert "label" in node
            assert "frequency" in node

    def test_edges_present(self) -> None:
        catalog = _build_three_pair_catalog()
        assert isinstance(catalog["edges"], list)
        assert len(catalog["edges"]) == 3

    def test_edge_keys(self) -> None:
        catalog = _build_three_pair_catalog()
        for edge in catalog["edges"]:
            assert "pair" in edge
            assert "pmi" in edge
            assert "interaction_lr" in edge
            assert "interaction_ci" in edge
            assert "fm_S" in edge
            assert "wins_minus_losses" not in edge
            assert "econ_S" in edge
            assert "combat_S" in edge
            assert "confidence" in edge
            assert "sample_n" in edge
            assert "quadrant" in edge

    def test_pair_is_two_element_list(self) -> None:
        catalog = _build_three_pair_catalog()
        for edge in catalog["edges"]:
            assert isinstance(edge["pair"], list)
            assert len(edge["pair"]) == 2

    def test_econ_s_null_when_absent(self) -> None:
        catalog = _build_three_pair_catalog(econ_map=None)
        for edge in catalog["edges"]:
            assert edge["econ_S"] is None

    def test_combat_s_null_when_absent(self) -> None:
        catalog = _build_three_pair_catalog(combat_map=None)
        for edge in catalog["edges"]:
            assert edge["combat_S"] is None

    def test_confidence_null_when_combat_absent(self) -> None:
        catalog = _build_three_pair_catalog(combat_map=None)
        for edge in catalog["edges"]:
            assert edge["confidence"] is None

    def test_econ_s_populated_when_present(self) -> None:
        econ_map = {p: _econ(0.5) for p in [PAIR_LOW, PAIR_MED, PAIR_HIGH]}
        catalog = _build_three_pair_catalog(econ_map=econ_map)
        for edge in catalog["edges"]:
            assert edge["econ_S"] is not None
            assert isinstance(edge["econ_S"], float)

    def test_combat_s_populated_when_present(self) -> None:
        combat_map = {p: _combat(0.1, 1.0) for p in [PAIR_LOW, PAIR_MED, PAIR_HIGH]}
        catalog = _build_three_pair_catalog(combat_map=combat_map)
        for edge in catalog["edges"]:
            assert edge["combat_S"] is not None
            assert isinstance(edge["combat_S"], float)


# ---------------------------------------------------------------------------
# Quadrant tagging — four-fixture suite
# ---------------------------------------------------------------------------


def _edge_for_pair(catalog: dict, pair: tuple[str, str]) -> dict:
    pair_list = list(pair)
    for edge in catalog["edges"]:
        if edge["pair"] == pair_list:
            return edge
    raise KeyError(f"pair {pair} not found in catalog")


class TestQuadrants:
    """
    Three pairs, pmi = 1.0, 2.0, 3.0.  median(pmi) = 2.0.
    obs_high = pmi > 2.0  →  only PAIR_HIGH (pmi=3.0) is obs_high.
    """

    # ----- confirmed: obs_high ∧ causal_high --------------------------------

    def test_confirmed(self) -> None:
        """PAIR_HIGH (pmi=3.0, obs_high) + combat ci[0]>0 → confirmed."""
        combat_map = {PAIR_HIGH: _combat(0.5, 2.0)}
        catalog = _build_three_pair_catalog(combat_map=combat_map)
        edge = _edge_for_pair(catalog, PAIR_HIGH)
        assert edge["quadrant"] == "confirmed", f"expected confirmed, got {edge['quadrant']}"

    # ----- myth: obs_high ∧ ¬causal_high ------------------------------------

    def test_myth(self) -> None:
        """PAIR_HIGH (obs_high) + combat ci[0]≤0 → myth."""
        combat_map = {PAIR_HIGH: _combat(-1.0, 0.5)}
        catalog = _build_three_pair_catalog(combat_map=combat_map)
        edge = _edge_for_pair(catalog, PAIR_HIGH)
        assert edge["quadrant"] == "myth", f"expected myth, got {edge['quadrant']}"

    # ----- undiscovered: ¬obs_high ∧ causal_high ----------------------------

    def test_undiscovered(self) -> None:
        """PAIR_LOW (pmi=1.0, obs_low) + combat ci[0]>0 → undiscovered."""
        combat_map = {PAIR_LOW: _combat(0.5, 2.0)}
        catalog = _build_three_pair_catalog(combat_map=combat_map)
        edge = _edge_for_pair(catalog, PAIR_LOW)
        assert edge["quadrant"] == "undiscovered", f"expected undiscovered, got {edge['quadrant']}"

    # ----- noise: ¬obs_high ∧ ¬causal_high ----------------------------------

    def test_noise(self) -> None:
        """PAIR_LOW (obs_low) + combat ci[0]≤0 → noise."""
        combat_map = {PAIR_LOW: _combat(-1.0, 0.0)}
        catalog = _build_three_pair_catalog(combat_map=combat_map)
        edge = _edge_for_pair(catalog, PAIR_LOW)
        assert edge["quadrant"] == "noise", f"expected noise, got {edge['quadrant']}"

    # ----- obs_high is PMI-only: interaction sign does not gate it ----------

    def test_obs_high_is_pmi_only_regardless_of_interaction(self) -> None:
        """pmi>median with a NEGATIVE interaction is still obs_high (spec §4).

        The retired W-L gate would have flipped this to obs_low; interaction_lr
        is a reported candidate signal, not the human-intent axis.
        """
        obs = {
            PAIR_LOW: _obs(1.0),
            PAIR_MED: _obs(2.0),
            PAIR_HIGH: _obs(3.0),
        }
        interaction = {
            PAIR_LOW: _inter(),
            PAIR_MED: _inter(),
            PAIR_HIGH: _inter(beta=-0.8, lo=-1.5, hi=-0.2),  # negative interaction
        }
        combat_map = {PAIR_HIGH: _combat(0.5, 2.0)}  # causal_high
        catalog = build_catalog(
            obs=obs,
            interaction=interaction,
            econ=None,
            combat=combat_map,
            card_frequency={"ALPHA": 0.8, "BETA": 0.7, "GAMMA": 0.6},
            run_count=1000,
        )
        edge = _edge_for_pair(catalog, PAIR_HIGH)
        # obs_high (PMI) ∧ causal_high (combat) → confirmed, despite negative interaction.
        assert edge["quadrant"] == "confirmed"
        assert edge["interaction_lr"] == pytest.approx(-0.8)

    # ----- econ fallback: no combat → use econ.median > 0 ------------------

    def test_causal_high_via_econ_fallback(self) -> None:
        """No combat for pair → fall back to econ.median > 0 for causal_high."""
        # PAIR_LOW: obs_low, econ fallback causal_high → undiscovered
        econ_map = {PAIR_LOW: _econ(0.3)}  # median > 0 → causal_high via fallback
        catalog = _build_three_pair_catalog(econ_map=econ_map)
        edge = _edge_for_pair(catalog, PAIR_LOW)
        assert edge["quadrant"] == "undiscovered"

    def test_causal_high_false_via_econ_fallback_negative(self) -> None:
        """No combat for pair → econ.median ≤ 0 → causal_low → noise."""
        econ_map = {PAIR_LOW: _econ(-0.1)}  # median ≤ 0 → causal_low
        catalog = _build_three_pair_catalog(econ_map=econ_map)
        edge = _edge_for_pair(catalog, PAIR_LOW)
        assert edge["quadrant"] == "noise"

    def test_causal_high_false_when_no_combat_no_econ(self) -> None:
        """No combat and no econ → causal_low → noise (obs_low pair)."""
        catalog = _build_three_pair_catalog(combat_map=None, econ_map=None)
        edge = _edge_for_pair(catalog, PAIR_LOW)
        assert edge["quadrant"] == "noise"

    # ----- median boundary: pmi == median is NOT obs_high -------------------

    def test_pmi_at_median_is_not_obs_high(self) -> None:
        """PAIR_MED has pmi=2.0 == median(pmi) → NOT obs_high (strict >)."""
        combat_map = {PAIR_MED: _combat(0.5, 2.0)}  # causal_high
        catalog = _build_three_pair_catalog(combat_map=combat_map)
        edge = _edge_for_pair(catalog, PAIR_MED)
        # Not obs_high, but causal_high → undiscovered
        assert edge["quadrant"] == "undiscovered"

    # ----- confidence field present when combat present ----------------------

    def test_confidence_is_float_when_combat_present(self) -> None:
        """confidence is a float in [0,1] when combat ci is available."""
        combat_map = {PAIR_HIGH: _combat(0.5, 2.0)}
        catalog = _build_three_pair_catalog(combat_map=combat_map)
        edge = _edge_for_pair(catalog, PAIR_HIGH)
        assert isinstance(edge["confidence"], float)
        assert 0.0 <= edge["confidence"] <= 1.0

    # ----- sample_n follows combat n, then econ n ----------------------------

    def test_sample_n_from_combat(self) -> None:
        combat_map = {PAIR_HIGH: _combat(0.5, 2.0, n=42)}
        catalog = _build_three_pair_catalog(combat_map=combat_map)
        edge = _edge_for_pair(catalog, PAIR_HIGH)
        assert edge["sample_n"] == 42

    def test_sample_n_from_econ_when_no_combat(self) -> None:
        econ_map = {PAIR_HIGH: _econ(0.5, n=17)}
        catalog = _build_three_pair_catalog(econ_map=econ_map)
        edge = _edge_for_pair(catalog, PAIR_HIGH)
        assert edge["sample_n"] == 17

    def test_sample_n_null_when_neither(self) -> None:
        catalog = _build_three_pair_catalog()
        edge = _edge_for_pair(catalog, PAIR_HIGH)
        assert edge["sample_n"] is None


# ---------------------------------------------------------------------------
# Thresholds dataclass
# ---------------------------------------------------------------------------


class TestThresholds:
    def test_thresholds_has_sane_defaults(self) -> None:
        t = Thresholds()
        # No assertion on specific values; just confirm it's instantiable and
        # the documented attributes exist.
        assert hasattr(t, "pmi_median_override")
        assert not hasattr(t, "wml_min")  # retired with wins_minus_losses
        assert hasattr(t, "combat_median_min")
        assert hasattr(t, "econ_median_min")
        assert t.q_max == pytest.approx(0.1)


# ---------------------------------------------------------------------------
# Multiplicity (§9b): shrunk/p/q fields + undiscovered FDR gate
# ---------------------------------------------------------------------------


class TestMultiplicityGating:
    def _obs(self) -> dict:
        # (A,B) high-PMI; (C,D) low-PMI — median PMI is 0 so obs_high splits them.
        return {
            ("A", "B"): ObsRow(pmi=2.0, cooccur=100, p_a=0.5, p_b=0.5, p_ab=0.4),
            ("C", "D"): ObsRow(pmi=-2.0, cooccur=100, p_a=0.5, p_b=0.5, p_ab=0.1),
        }

    def _combat_causal(self) -> dict:
        # Both causal_high (median_interaction > 0).
        return {
            ("A", "B"): CombatRow(median_interaction=1.0, ci=(0.5, 1.5), n=30),
            ("C", "D"): CombatRow(median_interaction=1.0, ci=(0.5, 1.5), n=30),
        }

    def test_backward_compatible_none(self) -> None:
        cat = build_catalog(
            obs=self._obs(),
            interaction={},
            econ=None,
            combat=self._combat_causal(),
            card_frequency={},
            run_count=1,
            multiplicity=None,
        )
        for e in cat["edges"]:
            assert e["shrunk_S"] is None
            assert e["pvalue"] is None
            assert e["qvalue"] is None
            assert e["iv_S"] is None
            assert e["combat_lopo_S"] is None

    def test_iv_and_lopo_fields_populated(self) -> None:
        cat = build_catalog(
            obs=self._obs(),
            interaction={},
            econ=None,
            combat=self._combat_causal(),
            card_frequency={},
            run_count=1,
            iv={("A", "B"): 0.7},
            lopo={("C", "D"): -0.3},
        )
        ab = next(e for e in cat["edges"] if e["pair"] == ["A", "B"])
        cd = next(e for e in cat["edges"] if e["pair"] == ["C", "D"])
        assert ab["iv_S"] == pytest.approx(0.7)
        assert ab["combat_lopo_S"] is None
        assert cd["combat_lopo_S"] == pytest.approx(-0.3)

    def test_undiscovered_survives_low_q(self) -> None:
        from synergy.multiplicity import MultiplicityRow

        mult = {
            ("C", "D"): MultiplicityRow(
                raw_S=1.0, shrunk_S=0.8, pvalue=0.001, qvalue=0.02, n_both=50
            )
        }
        cat = build_catalog(
            obs=self._obs(),
            interaction={},
            econ=None,
            combat=self._combat_causal(),
            card_frequency={},
            run_count=1,
            multiplicity=mult,
            thresholds=Thresholds(q_max=0.1),
        )
        cd = next(e for e in cat["edges"] if e["pair"] == ["C", "D"])
        assert cd["quadrant"] == "undiscovered"  # low-PMI, causal, q<=0.1
        assert cd["qvalue"] == pytest.approx(0.02)
        assert cd["shrunk_S"] == pytest.approx(0.8)

    def test_undiscovered_demoted_on_high_q(self) -> None:
        from synergy.multiplicity import MultiplicityRow

        mult = {
            ("C", "D"): MultiplicityRow(raw_S=1.0, shrunk_S=0.8, pvalue=0.5, qvalue=0.9, n_both=50)
        }
        cat = build_catalog(
            obs=self._obs(),
            interaction={},
            econ=None,
            combat=self._combat_causal(),
            card_frequency={},
            run_count=1,
            multiplicity=mult,
            thresholds=Thresholds(q_max=0.1),
        )
        cd = next(e for e in cat["edges"] if e["pair"] == ["C", "D"])
        assert cd["quadrant"] == "noise"  # demoted: q > q_max
        # Raw shrunk/q still reported so the erased candidate stays visible.
        assert cd["qvalue"] == pytest.approx(0.9)
        assert cd["shrunk_S"] == pytest.approx(0.8)

    def test_confirmed_not_gated(self) -> None:
        # obs_high pairs are on the human axis; the q-gate touches undiscovered only.
        from synergy.multiplicity import MultiplicityRow

        mult = {
            ("A", "B"): MultiplicityRow(raw_S=1.0, shrunk_S=0.8, pvalue=0.9, qvalue=0.99, n_both=50)
        }
        cat = build_catalog(
            obs=self._obs(),
            interaction={},
            econ=None,
            combat=self._combat_causal(),
            card_frequency={},
            run_count=1,
            multiplicity=mult,
            thresholds=Thresholds(q_max=0.1),
        )
        ab = next(e for e in cat["edges"] if e["pair"] == ["A", "B"])
        assert ab["quadrant"] == "confirmed"  # high-PMI + causal, q-gate ignored
