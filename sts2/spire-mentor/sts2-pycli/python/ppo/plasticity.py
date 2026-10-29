"""Plasticity diagnostics for the run-policy trunk.

A PPO campaign trains against a target that moves every iteration: the policy
changes, so the returns the critic predicts change, and so does the state
distribution generating them. Networks degrade under that non-stationarity in
a way that costs **trainability** rather than knowledge — they still predict
what they already learned, they just stop being able to absorb anything new.

Two symptoms are cheap to measure and are the ones the literature tracks:

- **Dormant units** (Sokar et al. 2023, "The Dormant Neuron Phenomenon in Deep
  RL"): units whose activation is near zero for essentially every input. They
  pass no gradient, so they are dead capacity — the net keeps the parameter
  count and loses the width.
- **Effective rank** (Kumar et al., "Implicit Under-Parameterization"): how
  many directions of the representation carry real signal. A collapsing rank
  means the trunk is folding into a subspace narrower than its width allows.

This module measures the symptom, nothing more. It does not fix anything: the
fix in the literature is a periodic soft reset (BBF perturbs 50% of parameters
toward a random init), and whether that is worth implementing here depends on
whether these numbers actually move. `ppo.screen` cannot answer that — it
resolves ~8pp campaign effects (commit a760f3dd6) — so a diagnostic logged
through a campaign is the cheap route in.

Why now: `--epochs` is going 2 → 4, and more gradient steps per unit of fresh
data is exactly the condition that erodes plasticity fastest (it is why BBF
pairs resets with a raised replay ratio rather than shipping either alone).
Champions are also warm-started from champions — `asc_it180` →
`deckmut_it150` → `simscale_it200` — so the deployed lineage carries far more
training against a moving target than one campaign's iteration count suggests.

**Reading the numbers.** They are computed on the CURRENT iteration's states,
which themselves shift as the policy improves, so a single value means little.
The signal is the TREND across a campaign: dormancy climbing or effective rank
falling while the data distribution is otherwise healthy. A one-iteration
reading is not evidence of anything.

Observed baseline on a fresh `dim=192, n_layers=3` net, three smoke
iterations: `dormant_frac` **0.0** across all blocks, `effective_rank`
**174 → 168 of 192**. A freshly initialised net having zero dormant units is
the correct reading, not a broken metric — the unit tests cover detection by
silencing units directly. Note the score is relative (a unit at ≤2.5% of its
layer's mean), which is what makes it meaningful for SwiGLU: unlike ReLU it
never emits exact zeros, so a zero-counting definition would read 0.0 forever.
"""

from __future__ import annotations

from typing import cast

import torch
from torch import nn

from .model import Batch, RunPolicyNet

# Sokar et al. 2023 count a unit dormant at <=2.5% of its layer's mean score.
DORMANT_TAU = 0.025
# srank_delta keeps the directions holding 99% of the spectrum's mass.
RANK_DELTA = 0.01


def dormant_fraction(acts: torch.Tensor, tau: float = DORMANT_TAU) -> float:
    """Share of units in `acts` [N, H] that are dormant.

    Score is the unit's mean absolute activation divided by the layer's mean
    score, so it is invariant to overall scale — without that normalisation a
    shrinking weight norm would masquerade as rising dormancy, which is the
    confound the metric exists to avoid.
    """
    if acts.numel() == 0:
        return float("nan")
    score = acts.detach().abs().mean(dim=0).float()
    score = score / score.mean().clamp_min(1e-12)
    return float((score <= tau).float().mean())


def effective_rank(acts: torch.Tensor, delta: float = RANK_DELTA) -> int:
    """srank_delta of `acts` [N, H]: directions holding (1-delta) of the mass.

    Kumar et al.'s definition — the smallest k whose leading k singular values
    carry at least 1-delta of the total. Full rank means the representation
    still uses its whole width.
    """
    if acts.numel() == 0:
        return 0
    sv = torch.linalg.svdvals(acts.detach().float())
    total = sv.sum().clamp_min(1e-12)
    frac = sv.cumsum(0) / total
    return int((frac < 1.0 - delta).sum().item()) + 1


@torch.no_grad()
def plasticity_stats(net: RunPolicyNet, batch: Batch, tau: float = DORMANT_TAU) -> dict[str, float]:
    """Dormant fraction per block, plus the trunk output's effective rank.

    Dormancy is measured on each block's SwiGLU **hidden** activation — the
    `dim*4`-wide layer where dead units actually accumulate — captured with a
    temporary pre-hook on `w2`, whose input that hidden is. Nothing in
    `model.py` has to change, and the hooks are removed on every path out.

    Effective rank is measured on the post-trunk representation both heads
    read (`net.norm`'s output), flattened over non-pad tokens.
    """
    captured: dict[object, torch.Tensor] = {}
    handles = []

    def _grab_input(key: object):
        def hook(_module, inputs):
            captured[key] = inputs[0].detach()
            return None

        return hook

    def _grab_output(key: object):
        def hook(_module, _inputs, output):
            captured[key] = output.detach()
            return None

        return hook

    was_training = net.training
    net.eval()
    try:
        for i, block in enumerate(net.blocks):
            # `nn.Module.__getattr__` is typed `Tensor | Module`; w2 is the
            # SwiGLU output projection, so the hook target is the Module.
            handles.append(cast(nn.Module, block.w2).register_forward_pre_hook(_grab_input(i)))
        handles.append(net.norm.register_forward_hook(_grab_output("trunk")))
        net(batch)  # ONE forward serves both metrics
    finally:
        for h in handles:
            h.remove()
        if was_training:
            net.train()

    out: dict[str, float] = {}
    fracs = []
    keep = ~batch.pad_mask.reshape(-1)
    for i in range(len(net.blocks)):
        hidden = captured[i].reshape(-1, captured[i].shape[-1])[keep]
        f = dormant_fraction(hidden, tau)
        out[f"dormant_frac/block{i}"] = round(f, 5)
        fracs.append(f)
    out["dormant_frac"] = round(sum(fracs) / max(1, len(fracs)), 5)

    trunk = captured["trunk"]
    rank = effective_rank(trunk.reshape(-1, trunk.shape[-1])[keep])
    out["effective_rank"] = float(rank)
    out["effective_rank_frac"] = round(rank / max(1, net.norm.normalized_shape[0]), 5)
    return out
