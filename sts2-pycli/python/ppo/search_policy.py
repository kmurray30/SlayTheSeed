"""Gumbel depth-1 search wrapper over the real simulator (GH #452, D1).

Spec: docs/superpowers/specs/2026-07-05-gumbel-search-wrapper-design.md;
research direction D1 in docs/plans/2026-08-13-ppo-alternatives-research.md
(expert iteration with a *gated* teacher).

At each yielded Decision: Gumbel-top-m shortlist by (g_i + logit_i), fork the
run per (candidate, outcome-sample) — ``rm.clone()`` + ``rm.reseed_run()`` so
a fork can never see the run's REAL upcoming RNG (anti-clairvoyance) —
advance to the next strategic decision, score q = ΔΦ + γ·V(s'), and select by
Gumbel MuZero's completed-Q rule argmax(g + logit + σ(q̂)) (Danihelka et al.,
ICLR 2022).

Beyond action selection, ``SearchPolicy.choose`` also emits the two
distillation targets issue #452 asks for:

- ``pi``: the improved policy softmax(logits + σ(q̂_completed)) over ALL
  candidates, with unevaluated candidates completed at the critic's root
  value (Grill et al. 2020: the regularized-policy solution, not visit
  counts) — the CE target for ``ppo.bc``.
- ``search_value``: the improved-policy-weighted mean of the completed
  Q-values — the value-regression target, on the same shaped-return scale
  the PPO critic is trained on.

Eval/teacher-time only: nothing here is imported by trainer.py or the PPO
collection path.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np


@dataclass(frozen=True)
class SearchConfig:
    m: int = 6  # Gumbel shortlist size
    n: int = 2  # outcome samples (reseeded forks) per candidate
    combat_sims: int = 100  # fork combat budget (parent run keeps its own)
    combat_boss_sims: int = 400
    c_visit: float = 50.0  # Gumbel MuZero σ-transform defaults
    c_scale: float = 1.0
    gamma: float = 1.0  # matches trainer.py PPOConfig.gamma
    fork_max_steps: int = 600  # engine-step cap per fork advance
    # 1.0 = paper-faithful Gumbel sampling; 0.0 = deterministic teacher
    # (argmax over logit + σ(q̂) only — for gates where sampling noise
    # would handicap the search arm against a greedy baseline).
    gumbel_scale: float = 1.0
    # Fixed return-scale temperature for the σ transform, in shaped-return
    # units: a q advantage of `q_temp` is worth exactly 1 nat of logit.
    # `None` keeps the legacy min-max normalization, which rescales each
    # decision's candidate spread to fill [0,1] and therefore hands a
    # near-tied decision the same σ span as a decisive one — measured
    # harmful with n=2 fork q's (FINDINGS.md: −35pp at A0). MuZero's own
    # normalization is over the whole tree's value range, not over the root
    # candidates, so a fixed scale is the faithful analogue here.
    q_temp: float | None = None
    # Leaf evaluator for a fork that did not terminate:
    #   "value"   — the critic, q = ΔΦ + γ·V(s')  (one-step lookahead; its
    #               improvement guarantee needs V to be accurate for the
    #               policy, which is exactly what is in doubt);
    #   "rollout" — keep playing the fork greedily to the end of the run and
    #               score the realized base(terminal). Monte-Carlo, unbiased
    #               by construction, ~10x the cost per fork. This is the M5
    #               design's "incumbent-rollout leaf" (A3a), never built.
    # A rollout that hits `rollout_max_steps` falls back to Φ + γ·V there,
    # making it an n-step return rather than a full one.
    leaf: str = "value"
    rollout_max_steps: int = 3000


def synthetic_seed(run_seed: int, decision_idx: int, j: int) -> int:
    """Deterministic positive-i32 seed for a fork's ``reseed_run``.

    Depends on (run, decision point, outcome sample j) but NOT on the
    candidate — sibling candidates share seeds, so their q's are a paired
    common-random-numbers comparison.
    """
    x = (run_seed & 0xFFFFFFFF) * 1_000_003 + decision_idx * 8_191 + j * 127 + 17
    return int(x % 2_147_483_647) + 1


def gumbel_shortlist(logp: np.ndarray, gumbels: np.ndarray, m: int) -> np.ndarray:
    """Top-min(m, n) candidate indices by g_i + logit_i, best first
    (Gumbel-top-k sampling without replacement)."""
    order = np.argsort(-(gumbels + logp), kind="stable")
    return order[: min(m, len(logp))]


def _sigma(
    q: np.ndarray,
    n_visits: int,
    c_visit: float,
    c_scale: float,
    q_temp: float | None = None,
) -> np.ndarray:
    """Q-transform mapping q onto logit (nat) units.

    With ``q_temp`` set, σ(q) = (q − max q) / q_temp — a fixed scale, so a
    decision whose candidates are genuinely near-tied produces a near-flat
    σ and the prior decides. (The constant shift is irrelevant: both call
    sites feed σ into an argmax or a softmax.)

    With ``q_temp`` None, the legacy Gumbel MuZero form
    (c_visit + visits) · c_scale · q̂ with q̂ min-max normalized over the
    given vector. Flat q → zeros either way.
    """
    lo, hi = float(q.min()), float(q.max())
    if hi <= lo:
        return np.zeros_like(q)
    if q_temp is not None:
        if q_temp <= 0:
            raise ValueError(f"q_temp must be > 0, got {q_temp}")
        return (q - hi) / q_temp
    return (c_visit + n_visits) * c_scale * (q - lo) / (hi - lo)


def completed_q_select(
    logp: np.ndarray,
    gumbels: np.ndarray,
    shortlist: np.ndarray,
    q: list,
    n_visits: int,
    c_visit: float,
    c_scale: float,
    q_temp: float | None = None,
) -> int:
    """argmax over the shortlist of (g + logit + σ(q̂)).

    A ``None`` q (all forks for that candidate failed) excludes the
    candidate; if every q is None, fall back to the raw net argmax.
    """
    valid = [(int(i), float(qi)) for i, qi in zip(shortlist, q) if qi is not None]
    if not valid:
        return int(np.argmax(logp))
    sig = _sigma(np.array([qi for _, qi in valid]), n_visits, c_visit, c_scale, q_temp)
    scores = [gumbels[i] + logp[i] + sig[k] for k, (i, _) in enumerate(valid)]
    return valid[int(np.argmax(scores))][0]


def improved_policy(
    logp: np.ndarray,
    shortlist: np.ndarray,
    q: list,
    root_value: float,
    n_visits: int,
    c_visit: float,
    c_scale: float,
    q_temp: float | None = None,
) -> tuple[np.ndarray, float]:
    """→ (π′ over ALL candidates, search value) — the distillation targets.

    Completed-Q (Danihelka et al. 2022 §4): evaluated candidates keep their
    measured q; every other candidate is completed at ``root_value`` (the
    critic's V(s), which is on the same shaped-return-to-go scale as q).
    π′ = softmax(logits + σ(q̂_completed)); the search value is the
    π′-weighted mean of q̂ (the one-step policy-improvement backup).

    With no evaluated q at all, π′ degrades to softmax(logits) and the
    search value to ``root_value`` — the raw net, never garbage.
    """
    n = len(logp)
    q_completed = np.full(n, float(root_value))
    any_valid = False
    for i, qi in zip(shortlist, q):
        if qi is not None:
            q_completed[int(i)] = float(qi)
            any_valid = True
    if not any_valid:
        pi = np.exp(logp - logp.max())
        pi /= pi.sum()
        return pi, float(root_value)
    scores = logp + _sigma(q_completed, n_visits, c_visit, c_scale, q_temp)
    pi = np.exp(scores - scores.max())
    pi /= pi.sum()
    return pi, float(np.dot(pi, q_completed))


class SearchPolicy:
    """Wraps a NetPolicy with depth-1 Gumbel search over real-sim forks.

    One instance per worker process; holds no run state — the env is passed
    per call.
    """

    def __init__(self, net_policy, cfg: SearchConfig = SearchConfig(), shaping=None):
        from ppo.rewards import ShapingConfig

        self.pol = net_policy
        self.cfg = cfg
        self.shaping = shaping or ShapingConfig()

    def _fork_q(self, env, d, action_idx: int, seed: int) -> tuple[float, bool]:
        """Score one reseeded fork of candidate ``action_idx``.

        → (q, leaf_was_terminal). q = ΔΦ + γ·V(s') for a Decision leaf;
        base(final, won) − Φ(parent) for a Terminal leaf (terminal shaping
        un-credited, matching rewards.shaped_rewards). Φ telescopes over the
        auto-taken steps between the two decisions, so intermediate states
        need no special handling. Raises RuntimeError on engine rejection
        (caller drops the sample).
        """
        from ppo.env import Decision, PpoRunEnv, Terminal
        from ppo.rewards import base_value, potential

        rm = env.rm.clone()
        rm.reseed_run(seed)  # anti-clairvoyance: fork never sees real future rolls
        fenv = PpoRunEnv.from_fork(
            rm,
            like=env,
            sims=self.cfg.combat_sims,
            boss_sims=self.cfg.combat_boss_sims,
            max_steps=min(env.max_steps, env.steps + self.cfg.fork_max_steps),
            agent_seed=seed,  # paired across sibling candidates (same j → same seed)
        )
        fenv._pending_actions = list(d.actions)
        out = fenv.step(action_idx)
        parent_pot = potential(d.metrics, self.shaping)
        if self.cfg.leaf == "rollout":
            # Play the fork out greedily; the realized terminal base is a
            # Monte-Carlo sample of the same quantity V is trained to
            # predict, so it drops into the identical q algebra.
            while isinstance(out, Decision) and fenv.steps < self.cfg.rollout_max_steps:
                logp, _, _ = self.pol.logits_value(out.obs, out.actions, out.metrics)
                out = fenv.step(int(np.argmax(logp)))
        if isinstance(out, Terminal):
            return base_value(out.metrics, out.won, self.shaping) - parent_pot, True
        _, v, _ = self.pol.logits_value(out.obs, out.actions, out.metrics)
        return potential(out.metrics, self.shaping) - parent_pot + self.cfg.gamma * v, False

    def choose(self, env, d, rng) -> tuple[int, dict]:
        """Pick a candidate index for Decision ``d`` on live env ``env``.

        The diag dict carries the selection trace (``override``,
        ``root_argmax``, ``shortlist``, ``q``, ``terminal_leaves``) and the
        distillation targets (``pi``, ``search_value``).
        """
        logp, root_v, _ = self.pol.logits_value(d.obs, d.actions, d.metrics)
        root_argmax = int(np.argmax(logp))
        n_act = len(d.actions)
        if n_act <= 1:
            return 0, {
                "override": False,
                "root_argmax": 0,
                "shortlist": [0],
                "q": [None],
                "terminal_leaves": 0,
                "pi": [1.0],
                "search_value": float(root_v),
            }
        gumbels = self.cfg.gumbel_scale * rng.gumbel(size=n_act)
        shortlist = gumbel_shortlist(logp, gumbels, self.cfg.m)
        qs: list[float | None] = []
        terminal_leaves = 0
        for i in shortlist:
            samples = []
            for j in range(self.cfg.n):
                seed = synthetic_seed(env.seed, env.decisions, j)
                try:
                    q, was_terminal = self._fork_q(env, d, int(i), seed)
                except RuntimeError:
                    continue  # rejected/errored fork → drop this sample
                samples.append(q)
                terminal_leaves += int(was_terminal)
            qs.append(float(np.mean(samples)) if samples else None)
        chosen = completed_q_select(
            logp,
            gumbels,
            shortlist,
            qs,
            self.cfg.n,
            self.cfg.c_visit,
            self.cfg.c_scale,
            self.cfg.q_temp,
        )
        pi, search_value = improved_policy(
            logp,
            shortlist,
            qs,
            root_v,
            self.cfg.n,
            self.cfg.c_visit,
            self.cfg.c_scale,
            self.cfg.q_temp,
        )
        return chosen, {
            "override": chosen != root_argmax,
            "root_argmax": root_argmax,
            "shortlist": [int(x) for x in shortlist],
            "q": qs,
            "terminal_leaves": terminal_leaves,
            "pi": [float(p) for p in pi],
            "search_value": search_value,
        }
