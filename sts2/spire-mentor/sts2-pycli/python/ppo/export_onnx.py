"""Export RunPolicyNet to ONNX with fixed max-token padding (Phase 2 §1).

Fixed shapes are the tract-friendly contract: batch 1, T_MAX total tokens,
A_MAX action slots. ``pad_to_fixed`` (now in ``ppo.schema``, torch-free;
re-exported here) is the single definition of the padding semantics
(== model.collate, extended to a fixed T) — the Rust collate in
sts2-runai/src/ppo/net.rs mirrors it and the parity fixtures enforce it.
Re-running this exporter is the entire cost of a checkpoint swap.

This file is the single source of truth for the budget: `PpoNet::load`
reads T_MAX off the `cont` input fact and A_MAX off the `act_mask` input
fact rather than duplicating them as Rust consts, so a re-export at
different values cannot silently mismatch the runtime (GH #315).

A_MAX went 96 -> 160 on 2026-07-29 (GH #315). The observation is
`1 state + deck + relics + potions + map nodes` (~deck + 68) and the
per-deck-card surfaces (shop card removal, campfire upgrade) offer ~deck
options, so A_MAX=96 bound at a ~96-card deck and killed a full run
mid-flight on a 103-option shop removal. 160 moves the binder to
T_MAX=320 (2*deck + 68 <= 320 -> deck ~126). A_MAX is nearly free —
action slots are gathered and fed through a per-token linear head, while
attention runs over T_MAX regardless — whereas raising T_MAX would cost
O(T^2) attention on every non-combat decision, so T_MAX is unchanged.
Widening A_MAX is numerically inert for real decisions: the extra slots
are masked to -inf before the softmax and never enter the value pool.
"""

from __future__ import annotations

import argparse

import numpy as np
import torch
from torch import nn

from .model import AUX_IGNORE, N_CONT, Batch, RunPolicyNet, empty_cat, load_compat
from .schema import pad_to_fixed


class ExportWrapper(nn.Module):
    """Inference-only view: (logits, value); aux head dropped."""

    def __init__(self, net: RunPolicyNet) -> None:
        super().__init__()
        self.net = net

    def forward(self, cat, cont, sec, pad_mask, obs_len, act_mask):
        b = Batch(
            cat=cat,
            cont=cont,
            sec=sec,
            pad_mask=pad_mask,
            obs_len=obs_len,
            act_mask=act_mask,
            aux_room=torch.full_like(act_mask, AUX_IGNORE, dtype=torch.long),
            device=cat.device,
        )
        logits, value, _ = self.net(b)
        return logits, value


def export(ckpt: str, out: str, t_max: int = 320, a_max: int = 160) -> None:
    torch.set_num_threads(1)
    net = RunPolicyNet()
    ck = torch.load(ckpt, map_location="cpu", weights_only=True)
    # Dim-tolerant loader (same as NetPolicy.__init__): legacy 18-cont
    # checkpoints (pre-ascension-input) export unchanged, trailing new
    # cont columns zeroed.
    load_compat(net, ck.get("state_dict", ck))
    wrapper = ExportWrapper(net).eval()
    args = (
        torch.from_numpy(np.tile(np.array(empty_cat(), dtype=np.int64), (1, t_max, 1))),
        torch.zeros(1, t_max, N_CONT),
        torch.zeros(1, t_max, dtype=torch.long),
        torch.ones(1, t_max, dtype=torch.bool),
        torch.tensor([1], dtype=torch.long),
        torch.zeros(1, a_max, dtype=torch.bool),
    )
    torch.onnx.export(
        wrapper,
        args,
        out,
        input_names=["cat", "cont", "sec", "pad_mask", "obs_len", "act_mask"],
        output_names=["logits", "value"],
        opset_version=18,
        dynamo=True,
        # Keep weights inlined in the single .onnx file (default dynamo
        # behavior splits >1MB tensors into a companion `<out>.data` file,
        # which is easy to lose track of / forget to commit alongside the
        # model — tract loads a single-file graph just as well).
        external_data=False,
    )


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--ckpt", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--t-max", type=int, default=320)
    ap.add_argument("--a-max", type=int, default=160)
    a = ap.parse_args()
    export(a.ckpt, a.out, a.t_max, a.a_max)
