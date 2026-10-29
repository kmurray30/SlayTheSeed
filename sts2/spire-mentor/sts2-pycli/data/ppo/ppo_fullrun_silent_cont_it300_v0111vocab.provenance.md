# `ppo_fullrun_silent_cont_it300_v0111vocab` — provenance

**A vocab re-export of `ppo_fullrun_silent_cont_it300`, not a new net.** Same weights, no training,
no gate panel. GH #799 (v0.111.0 port, S10).

## What changed

The PPO card / relic / potion vocabularies now span both supported game builds
(v0.107.1 and v0.111.0), so the 22 v0.111.0-only ids have rows of their own:

| table | ids before | ids after | added |
| --- | --- | --- | --- |
| `emb_card` | 580 | 599 | the 19 v0.111.0 cards |
| `emb_relic` | 297 | 299 | DowsingRod, NeowsSacrifice |
| `emb_potion` | 63 | 64 | Ambergris |

The pins are append-only, so every v0.107.1 id keeps its row. The new rows are
the old OOV row (`_widen_padded_embedding`); OOV and PAD moved to the new tail.
`ppo_fullrun_silent_cont_it300_v0111vocab.pt` is a byte copy of `ppo_fullrun_silent_cont_it300.pt` (git stores one blob).

Exported from the checkpoint with `ppo.export_onnx.export`. The graph inputs and outputs are unchanged and every initializer other than the three grown tables is bit-identical to the shipped graph's.

Built by `scripts/reexport_champions_vocab.py --suffix _v0111vocab`.

## Equivalence on v0.107.1

`scripts/vocab_reexport_equivalence.py` drove `PpoRunEnv` (default build
v0.107.1, `--act1 roll`, A0/3/5/8/10, combat MCTS 250/1000) greedily with this
net, featurized every decision under both the old and the new registries, and
scored the old encoding on `ppo_fullrun_silent_cont_it300.onnx` and the new one on this file:

| runs | decisions | bit-identical logits and value | max abs diff |
| --- | --- | --- | --- |
| 42 | 2527 | 2527 | 0.0 / 0.0 |

It also asserted the two encodings differ only by the OOV/PAD index remap, i.e.
no v0.107.1 observation reached a new row. Every gate result recorded for
`ppo_fullrun_silent_cont_it300` (`ppo_fullrun_silent_cont_it300.provenance.md`) therefore describes this file too.

## Under v0.111.0

The new ids answer with the old OOV row: the net reads "an id I have never
seen", as it did before the widening. It is not trained for v0.111.0; that is
GH #802. sha256 `804dda517f8f4b203db8a42172f54520b1f3354f1f828df2b0150f4a2d833b43`.
