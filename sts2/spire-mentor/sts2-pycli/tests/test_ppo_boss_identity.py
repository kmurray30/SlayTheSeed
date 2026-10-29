"""The upcoming boss's identity must reach the policy (GH #431 follow-on).

``full_map()`` has always carried the act's boss encounter id, and the map
featurizer has always thrown it away — the boss node was rewritten as a bare
``{"type": "boss"}``, so the one fixed, known, run-defining fight the deck is
being built FOR was the only thing the net could not see. These tests pin the
wiring that fixes that, and the two properties that make it safe to land on a
fleet of already-gated champions:

* the boss slot is an APPENDED categorical (``schema.CAT_BOSS``), so a
  9-categorical export drops it cleanly (``ppo.onnx_policy`` / ``ppo::net``'s
  ``cat_dim``) whether or not later columns (GH #545's deck-op/enchantment)
  were appended after it; and
* ``emb_boss`` is zero-initialized, so warm-starting a net that predates it is
  EXACTLY a no-op rather than a random perturbation of every state token.
"""

from __future__ import annotations

import numpy as np
import pytest
import sts2_rs
import torch
from ppo import mapfeat, vocab
from ppo.encoder import featurize_row
from ppo.env import PpoRunEnv, Terminal
from ppo.model import RunPolicyNet, collate, load_compat
from ppo.schema import CAT_BOSS, SEC_MAPNODE, SEC_STATE, empty_cat

BOSS_SLOT = CAT_BOSS


@pytest.fixture(scope="module")
def first_decision():
    """The first yielded decision of a fixed Necrobinder A5 run."""
    env = PpoRunEnv(seed=40_000_000, character="necrobinder", ascension=5)
    out = env.reset()
    assert not isinstance(out, Terminal)
    return out


class TestBossVocab:
    def test_it_covers_every_boss_the_map_can_roll(self) -> None:
        b = vocab.bosses()
        rollable = sts2_rs.list_registry_names("boss")
        # The count is pinned as a CANARY, not as a fact worth knowing: the
        # registry is derived from `run_room_gen::all_boss_encounter_ids()`, so
        # it grows the moment content lands, and growth moves the OOV/PAD
        # indices every deployed champion's `emb_boss` table was exported at.
        # 9 -> 12 when Underdocks landed as the second Act 1 (GH #544), which
        # is what forced the champion re-bake. If this fires, the vocab grew:
        # append the new ids to `ppo/vocab_pins/boss.txt` (append-only — a
        # reorder silently repoints a trained row), re-bake the champions with
        # `scripts/rebake_champion_vocab.py`, regenerate BOTH parity fixtures,
        # and then update this number.
        assert len(rollable) == 12, rollable
        for boss_id in rollable:
            assert b.index(boss_id) < b.oov, f"{boss_id} is OOV"

    def test_every_producer_shape_folds_onto_one_row(self) -> None:
        """The id reaches the encoder from three producers with three shapes:
        the generated UPPER_SNAKE id, the encounter pool's lower-snake name,
        and the pinned-boss fallback's spaced display name."""
        b = vocab.bosses()
        rows = {
            b.index(raw)
            for raw in ("KAISER_CRAB_BOSS", "kaiser_crab_boss", "KaiserCrab", "Kaiser Crab")
        }
        assert len(rows) == 1
        assert rows.pop() < b.oov

    def test_an_unknown_boss_is_oov_not_a_silent_neighbour(self) -> None:
        b = vocab.bosses()
        assert b.index("NOT_A_BOSS_XYZ_BOSS") == b.oov


class TestEncoderCarriesTheBoss:
    def test_the_state_token_names_the_acts_boss(self, first_decision) -> None:
        boss_id = mapfeat.boss_id_of(first_decision.obs["map"])
        assert boss_id, "the engine resolves the act's boss from floor 1"

        feat = featurize_row(first_decision.obs, first_decision.actions, first_decision.metrics)
        state = feat.obs_cat[feat.obs_sec == SEC_STATE]
        assert len(state) == 1
        # The real boss, not the pad row and not OOV.
        assert state[0][BOSS_SLOT] == vocab.bosses().index(boss_id)
        assert state[0][BOSS_SLOT] < vocab.bosses().oov

    def test_only_the_state_and_boss_node_tokens_carry_it(self, first_decision) -> None:
        pad = empty_cat()[BOSS_SLOT]
        feat = featurize_row(first_decision.obs, first_decision.actions, first_decision.metrics)
        named = feat.obs_cat[:, BOSS_SLOT] != pad
        secs = set(feat.obs_sec[named].tolist())
        assert secs == {SEC_STATE, SEC_MAPNODE}
        # Exactly one map node — the terminal boss — is named.
        assert int((named & (feat.obs_sec == SEC_MAPNODE)).sum()) == 1
        # Action tokens never carry it; the boss is state, not a candidate.
        assert np.all(feat.act_cat[:, BOSS_SLOT] == pad)

    def test_a_decision_with_no_map_degrades_to_the_pad_row(self, first_decision) -> None:
        """The advisor's live path can hand over an observation with no map at
        all. That must cost the feature, never the decision."""
        obs = dict(first_decision.obs)
        obs["map"] = None
        feat = featurize_row(obs, first_decision.actions, first_decision.metrics)
        assert np.all(feat.obs_cat[:, BOSS_SLOT] == empty_cat()[BOSS_SLOT])

    def test_different_seeds_can_name_different_bosses(self) -> None:
        """A constant here would featurize identically to no feature at all."""
        seen = set()
        for seed in range(40_000_000, 40_000_012):
            rm = sts2_rs.RunManager(
                seed=seed, character="necrobinder", ascension_level=0, neow_start=True
            )
            seen.add(mapfeat.boss_id_of(rm.full_map()))
        assert len(seen) > 1, f"act-1 boss never varied across 12 seeds: {seen}"


class TestWarmStartIsANoOp:
    def test_the_boss_table_starts_at_exactly_zero(self) -> None:
        net = RunPolicyNet()
        assert torch.count_nonzero(net.emb_boss.weight) == 0

    def test_a_checkpoint_without_the_table_leaves_it_at_zero(self) -> None:
        """`load_compat` keeps the net's own init for tensors the checkpoint
        lacks. That is only safe because the init is zeros — a randomly
        initialized table would add a large random vector to the state token of
        every decision and throw away the parent policy on iteration 0."""
        torch.manual_seed(1)
        parent = RunPolicyNet()
        legacy = {k: v for k, v in parent.state_dict().items() if k != "emb_boss.weight"}

        net = RunPolicyNet()  # the production path: fresh net, then load
        load_compat(net, legacy)

        assert torch.count_nonzero(net.emb_boss.weight) == 0
        # ...and every other table really did come from the checkpoint, so
        # this is testing the missing-key branch and not a no-op load.
        assert torch.equal(net.emb_card.weight, parent.emb_card.weight)

    def test_the_boss_input_cannot_move_a_warm_started_nets_output(self, first_decision) -> None:
        """End-to-end: on a net that has never trained the table, naming the
        boss and padding it must give identical logits and value."""
        torch.manual_seed(0)
        net = RunPolicyNet().eval()
        feat = featurize_row(first_decision.obs, first_decision.actions, first_decision.metrics)
        blinded = deepcopy_featurized(feat)
        blinded.obs_cat[:, BOSS_SLOT] = empty_cat()[BOSS_SLOT]

        with torch.no_grad():
            named_logits, named_v, _ = net(collate([feat]))
            blind_logits, blind_v, _ = net(collate([blinded]))
        assert torch.equal(named_logits, blind_logits)
        assert torch.equal(named_v, blind_v)


def deepcopy_featurized(feat):
    """A copy whose arrays can be mutated without touching the original."""
    import copy

    out = copy.copy(feat)
    out.obs_cat = feat.obs_cat.copy()
    out.act_cat = feat.act_cat.copy()
    return out
