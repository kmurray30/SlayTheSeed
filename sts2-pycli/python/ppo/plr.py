"""Prioritized Level Replay over (seed, ascension) levels.

Jiang, Grefenstette, Rocktäschel 2021 (arXiv:2010.03934), adapted for an
unbounded seed space (App. B.3: finite top-K buffer, evict the lowest score):

- score S(level) = mean |GAE| over the level's last collected run — the L1
  value-loss score, the only one the paper found consistent (§5 finding ii).
- rank prioritization (Eq. 3): P_S(l) ∝ (1/rank(S_l))^(1/beta), rank
  descending by score.
- staleness (Eq. 4): P_C(l) ∝ (it - last_it), mixed in with coefficient rho
  so no score drifts too far off-policy.
- replay probability itself lives in the trainer (`PPOConfig.plr_replay_p`);
  this module only owns the buffer.
- ONE deliberate deviation: a batch is drawn WITHOUT replacement. See
  `LevelSampler.sample` — the paper's with-replacement draw is safe only
  because it re-scores between draws, which a batched collector cannot do.

Everything is plain Python/JSON-able so `state_dict()` can ride in the
trainer checkpoint (`weights_only=True` load) and keep `--resume-from-iter`
faithful: a resumed run replays from the same buffer the uninterrupted run
would have had.
"""

from __future__ import annotations

import numpy as np

Level = tuple[int, int]  # (seed, ascension) — the same seed at two ascensions is a different level


class LevelSampler:
    def __init__(self, cap: int, beta: float = 0.1, rho: float = 0.1) -> None:
        if cap <= 0:
            raise ValueError(f"cap must be positive, got {cap}")
        self.cap = cap
        self.beta = beta
        self.rho = rho
        # insertion-ordered: {(seed, asc): [score, last_it]}
        self._levels: dict[Level, list] = {}

    def __len__(self) -> int:
        return len(self._levels)

    def update(self, key: Level, score: float, it: int) -> None:
        """Insert or re-score a level; evict the lowest score when over cap."""
        self._levels[key] = [float(score), int(it)]
        if len(self._levels) > self.cap:
            worst = min(self._levels, key=lambda k: self._levels[k][0])
            del self._levels[worst]

    def sample(self, n: int, it: int, rng: np.random.Generator) -> list[Level]:
        """Draw `n` levels ~ (1-rho)·P_S + rho·P_C; touches drawn levels' last_it.

        WITHOUT replacement, in rounds. The paper samples WITH replacement, but
        it is ONLINE: level l is re-scored the moment its episode ends, before
        the next draw, so the rank-1 level churns away between draws. We pick a
        whole iteration's replay slots at once from a FROZEN distribution, and
        at the paper's beta=0.1 the exponent is 1/beta = 10, which puts
        P_S(rank 1) = 0.999 for ANY buffer size — i.i.d. draws would land ~90%
        of the batch on ONE (seed, ascension). The 2026-09-01 smoke measured
        exactly that (49 of 51 replays on one level; see
        docs/experiments/2026-08-31-ironclad-plr-continuation.md). Sampling
        without replacement keeps the prioritization and drops the collapse.

        `n > len(buffer)` cannot be distinct, so it repeats in rounds — each
        round is itself collapse-free.

        The staleness clock of a drawn level is reset to `it` so P_C keeps
        cycling the buffer rather than re-serving one stale level forever.
        """
        if not self._levels or n <= 0:
            return []
        keys = list(self._levels)
        scores = np.array([self._levels[k][0] for k in keys])
        # rank 1 = highest score; ties broken by buffer order (stable argsort)
        order = np.argsort(-scores, kind="stable")
        ranks = np.empty(len(keys), dtype=np.int64)
        ranks[order] = np.arange(1, len(keys) + 1)
        p_s = (1.0 / ranks) ** (1.0 / self.beta)
        p_s /= p_s.sum()
        stale = np.array([it - self._levels[k][1] for k in keys], dtype=np.float64)
        stale = np.maximum(stale, 0.0)
        p_c = stale / stale.sum() if stale.sum() > 0 else p_s
        p = (1.0 - self.rho) * p_s + self.rho * p_c
        p /= p.sum()
        drawn: list[Level] = []
        while len(drawn) < n:
            k = min(n - len(drawn), len(keys))
            drawn.extend(keys[i] for i in rng.choice(len(keys), size=k, replace=False, p=p))
        for lv in set(drawn):
            self._levels[lv][1] = int(it)
        return drawn

    def state_dict(self) -> dict:
        return {
            "cap": self.cap,
            "beta": self.beta,
            "rho": self.rho,
            "levels": [[k[0], k[1], v[0], v[1]] for k, v in self._levels.items()],
        }

    def load_state_dict(self, d: dict) -> None:
        self.cap = int(d["cap"])
        self.beta = float(d["beta"])
        self.rho = float(d["rho"])
        self._levels = {(int(s), int(a)): [float(sc), int(li)] for s, a, sc, li in d["levels"]}
