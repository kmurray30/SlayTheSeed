"""Full-run self-play decisions for the Python drivers (sts2_rs).

``play_full_run_rust.play_run_mcts`` (the offline benchmark driver — the
``exp_boss_sim_winrate`` 2000-seed win-rate sweep runs on it) and
``play_full_run_ppo`` step a run with this policy: MCTS in combat
(``sts2_rs.mcts_recommend``), ``boss_sim_card_reward`` for card rewards, the
heuristic run-policy everywhere else.

The workbench's faithful "play to end" no longer shares it: it drives the
native ``sts2_rs.HeuristicRunAgent`` (``workbench/branching.py``) and imports
only :func:`hash_seed` from here, so the two do not reproduce each other
move-for-move. Both now search combat with the same deployed dialect
(``MctsConfig::apply_mode_dialect``, GH #758), but the agents, budgets and
seed schedules differ.

A :class:`RunStepper` owns the run's RNG (``random.Random(seed ^ 0xC0FFEE)``,
threaded through every out-of-combat pick) and an action counter (``steps``,
which seeds each combat search at ``seed + steps``). :meth:`decide` is **pure**
— it reads the live ``RunManager`` and returns the next raw engine action
without mutating anything; the caller applies it (so it can log / record /
translate first) and then calls :meth:`step_taken` to advance the counter.

For reproducibility ``threads`` defaults to 1: rayon thread-ordering makes a
multi-threaded MCTS search non-deterministic, so a faithful replay (and the
parity test) must pin it to 1, exactly as ``exp_boss_sim_winrate`` does.
"""

from __future__ import annotations

import json
import os
import random
from dataclasses import dataclass
from typing import Any

import sts2_rs
from run_policy import (
    best_recommend_action,
    boss_sim_card_reward,
    combat_action_to_run_action,
    heuristic_run_action,
    validated_choice,
)

# Offset that turns the run seed into the out-of-combat RNG seed. Shared by the
# benchmark driver and the workbench so both consume the same random stream.
RUN_RNG_OFFSET = 0xC0FFEE


def hash_seed(seed_str: str) -> int:
    """Stable int seed from a string label.

    ``run_1``/``run_2`` → ``1``/``2`` (human-readable determinism matching the
    C# ``play_full_run.py`` runs); any other string folds to a 31-bit int. This
    is the canonical mapping the benchmark (``play_full_run_rust._hash_seed``,
    re-exported from here) and the workbench's faithful start share, so seed
    ``"run_7"`` names the same run in both.
    """
    if seed_str.startswith("run_"):
        tail = seed_str[len("run_") :]
        if tail.isdigit():
            return int(tail)
    h = 0
    for ch in seed_str:
        h = (h * 131 + ord(ch)) & 0x7FFF_FFFF
    return h


@dataclass
class Decision:
    """The next action the policy would take, plus reasoning for logging.

    ``action`` is a raw ``RunManager.take_action`` dict. For a combat decision
    ``combat_result``/``combat_cfg``/``combat_cs`` carry the MCTS table, its
    config, and the native state (so a recorder can log the search); for a
    boss-simulated card reward ``reward_sim`` carries the per-candidate scores.
    """

    action: dict[str, Any]
    phase: str
    is_combat: bool
    reward_sim: dict[str, Any] | None = None
    combat_result: dict[str, Any] | None = None
    combat_cfg: Any | None = None
    combat_cs: Any | None = None


class RunStepper:
    """Drives one ``sts2_rs.RunManager`` with the deployed self-play policy.

    The decision policy mirrors ``play_full_run_rust.play_run_mcts`` exactly;
    both now route through :meth:`decide` so they cannot drift apart.
    """

    def __init__(
        self,
        rm: Any,
        *,
        seed: int,
        mcts_iters: int = 2000,
        boss_mcts_iters: int = 10_000,
        reward_sims: int | None = 3000,
        threads: int = 1,
        c_puct: float = 1.0,
        depth: int = 200,
        heuristic_ooc: bool = True,
        verbose: bool = False,
    ) -> None:
        self.rm = rm
        self.seed = seed
        self.mcts_iters = mcts_iters
        self.boss_mcts_iters = boss_mcts_iters
        self.reward_sims = reward_sims or None
        self.threads = threads
        self.c_puct = c_puct
        self.depth = depth
        self.heuristic_ooc = heuristic_ooc
        self.verbose = verbose
        # The single out-of-combat RNG, threaded through the whole run so the
        # nth heuristic pick depends on every prior pick — identical to the
        # benchmark driver's ``random.Random(seed ^ 0xC0FFEE)``.
        self.rng = random.Random(seed ^ RUN_RNG_OFFSET)
        # Actions applied so far; seeds each combat search at ``seed + steps``.
        self.steps = 0
        # Anti-stall backstop: structural hash of the combat state at the
        # previous combat decision. ``RunManager.take_action`` reports
        # "advanced" even for plays it silently ignores (dead-slot target,
        # Ringing's one-card-per-turn gate, ...), so a deterministic policy
        # that keeps recommending an ignored play spins forever. If the
        # state hash hasn't moved since the last combat decision, the last
        # action was a no-op -- end the turn instead of re-playing it.
        self._last_combat_fp: int | None = None

    # -- per-step policy ---------------------------------------------------
    def decide(self) -> Decision | None:
        """The next raw engine action for the live run, or ``None`` if over.

        Pure: reads the ``RunManager`` and (for combat) runs one MCTS search,
        but never mutates the run. The caller applies ``Decision.action`` (via
        ``take_action`` or the workbench verb path) and then calls
        :meth:`step_taken`.
        """
        rm = self.rm
        if rm.is_over():
            return None
        phase = rm.phase()
        if phase == "combat":
            return self._decide_combat()
        return self._decide_ooc(phase)

    def _decide_combat(self) -> Decision:
        from sts2_hint.recommender import RecommendConfig

        rm = self.rm
        cs = rm.combat_state()
        if cs is None:
            raise RuntimeError(f"phase=='combat' but combat_state() is None at step {self.steps}")
        # Fingerprint via the full wire dict, NOT cs.state_hash():
        # `hash_player_tail` (sts2-core hashing.rs) skips `player.potions`
        # and the mirror-Creature power list, so drinking a potion is
        # invisible to state_hash and the guard would misread the drink as
        # a no-op (observed: Duplicator at seed 1 step 28) and wrongly end
        # the turn. state_to_dict emits potions and the merged power union.
        wire = sts2_rs.state_to_dict(cs)
        fp = hash(json.dumps(wire, sort_keys=True, default=str))
        if fp == self._last_combat_fp and cs.pending_choice() is None:
            # The action applied since the last decision changed nothing:
            # the engine silently ignored it. Ending the turn always
            # advances the state, so the run can't loop to the step cap.
            if self.verbose:
                print(f"  [stall-guard] no-op combat action at step {self.steps}; ending turn")
            self._last_combat_fp = fp
            return Decision(
                action=combat_action_to_run_action({"action": "end_turn"}),
                phase="combat",
                is_combat=True,
                # No search ran; carry the state so recorders can still
                # serialize the decision point.
                combat_cs=cs,
            )
        self._last_combat_fp = fp
        # Ablation (live-gap rung 3, 2026-07-12): round-trip the native combat
        # state through the same dict serialization the real-game bridge path
        # uses (state_to_dict -> state_from_dict). This deliberately discards
        # the true RNG cursors (re-seeded from seed+turn, like a bridge state)
        # and any field the dict format can't carry, so the win-rate delta vs.
        # the native-state run isolates the reconstruction loss the live game
        # suffers. Opt-in via STS2_DICT_ROUNDTRIP=1; default path is unchanged.
        # Skip the round-trip while a choice is pending: state_to_dict DROPS
        # pending_choice silently (and state_from_dict would reject it), so a
        # round-tripped state searches plays the real engine ignores until the
        # choice resolves -- the second infinite-stall class of the first
        # rung-3 sweep. Searching choices natively also mirrors the live path,
        # which MCTS-ranks card_selects on a natively replayed state.
        native_cs = None
        if os.environ.get("STS2_DICT_ROUNDTRIP") == "1" and cs.pending_choice() is None:
            native_cs = cs
            cs = sts2_rs.state_from_dict(wire)
        room = rm.current_room()
        is_boss = bool(room) and room.get("room_type") == "boss"
        # Honest BeliefDraw by default — the deployed `MctsCombatPolicy`'s
        # config, so a round-trip ablation isolates RECONSTRUCTION loss instead
        # of also paying a search-MODE gap against the native baseline it is
        # being compared with. It used to run clairvoyant here and honest only
        # under `STS2_ROUNDTRIP_HONEST=1`, which meant the default measurement
        # conflated the two. `STS2_ROUNDTRIP_HONEST=0` restores the old leaky
        # arm for an explicit ablation. The paired constants are resolved from
        # the mode (`resolved_c_puct` / `resolved_draw_widening_c`), so a
        # trimmed budget cannot leave them behind.
        honest = os.environ.get("STS2_ROUNDTRIP_HONEST") != "0"
        cfg = RecommendConfig(
            sims=self.boss_mcts_iters if is_boss else self.mcts_iters,
            c_puct=None if honest else self.c_puct,
            depth=self.depth,
            seed=self.seed + self.steps,
            threads=self.threads,
            hidden_info="beliefdraw" if honest else "clairvoyant",
        )
        result = sts2_rs.mcts_recommend(
            cs,
            sims=cfg.sims,
            c_puct=cfg.resolved_c_puct(),
            depth=cfg.depth,
            seed=cfg.seed,
            threads=cfg.threads,
            fair=cfg.fair,
            rollout_policy=cfg.rollout_policy,
            hidden_info=cfg.hidden_info,
            draw_widening_c=cfg.resolved_draw_widening_c(),
        )
        rec_action = best_recommend_action(result) or {"action": "end_turn"}
        action = combat_action_to_run_action(rec_action)
        if native_cs is not None and action.get("target_index") is not None:
            # The round-trip drops ESCAPED enemies and renumbers the
            # remaining slots, so a searched target is an index into the
            # present (non-escaped) list -- but the real RunManager keeps
            # every creature in its encounter slot, and a play targeting
            # the wrong slot silently no-ops ("advanced", no state
            # change), which looped every first rung-3 sweep run to the
            # 200k-step cap. Map the present-list index back to the real
            # encounter slot. (Dead REVIVERS are no longer dropped by
            # state_from_dict -- corpses stay in their slots so their
            # revive machinery runs in rollouts -- hence present =
            # non-escaped, NOT alive-only.)
            enc = wire.get("encounter") or []
            present = [i for i, e in enumerate(enc) if not e.get("is_escaped")]
            t = int(action["target_index"])
            if 0 <= t < len(present):
                action["target_index"] = present[t]
        if action["action"] == "combat_resolve_choice":
            action = validated_choice(rm, action)
        return Decision(
            action=action,
            phase="combat",
            is_combat=True,
            combat_result=result,
            combat_cfg=cfg,
            combat_cs=cs,
        )

    def _decide_ooc(self, phase: str) -> Decision:
        self._last_combat_fp = None
        rm = self.rm
        acts = rm.available_actions()
        if not acts:
            raise RuntimeError(
                f"stalled at phase={phase} step={self.steps}: available_actions() returned empty"
            )
        action: dict[str, Any] | None = None
        sim_info: dict[str, Any] | None = None
        if self.reward_sims and phase == "card_reward":
            action, sim_info = boss_sim_card_reward(
                rm, acts, sims=self.reward_sims, threads=self.threads, verbose=self.verbose
            )
        if action is None:
            action = (
                heuristic_run_action(rm, acts, self.rng)
                if self.heuristic_ooc
                else self.rng.choice(acts)
            )
        return Decision(action=action, phase=phase, is_combat=False, reward_sim=sim_info)

    def step_taken(self) -> None:
        """Record that one action has been applied (advances the combat seed)."""
        self.steps += 1
