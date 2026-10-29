# `ppo_fullrun_regent_deckmut_it150_v0111vocab` — provenance

**A vocab re-export of `ppo_fullrun_regent_deckmut_it150`, not a new net.** Same weights, no training,
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
`ppo_fullrun_regent_deckmut_it150_v0111vocab.pt` is a byte copy of `ppo_fullrun_regent_deckmut_it150.pt` (git stores one blob).

The shipped graph takes 10 categorical / 19 continuous columns, the input contract it was gated at. Re-exporting from the checkpoint today emits the current 12 / 25, which would feed it columns it never had (zero weights, but a different graph from the one the gate swept). So the script kept the shipped graph and replaced only the three grown tables, taking them from the checkpoint through `load_compat` and asserting each equals the shipped table re-laid. The checkpoint and the shipped graph are therefore proven to be the same weights.

Built by `scripts/reexport_champions_vocab.py --suffix _v0111vocab`.

## Equivalence on v0.107.1

`scripts/vocab_reexport_equivalence.py` drove `PpoRunEnv` (default build
v0.107.1, `--act1 roll`, A0/3/5/8/10, combat MCTS 250/1000) greedily with this
net, featurized every decision under both the old and the new registries, and
scored the old encoding on `ppo_fullrun_regent_deckmut_it150.onnx` and the new one on this file:

| runs | decisions | bit-identical logits and value | max abs diff |
| --- | --- | --- | --- |
| 50 | 2503 | 2503 | 0.0 / 0.0 |

It also asserted the two encodings differ only by the OOV/PAD index remap, i.e.
no v0.107.1 observation reached a new row. Every gate result recorded for
`ppo_fullrun_regent_deckmut_it150` (`ppo_fullrun_regent_deckmut_it150.provenance.md`) therefore describes this file too.

## Under v0.111.0

The new ids answer with the old OOV row: the net reads "an id I have never
seen", as it did before the widening. It is not trained for v0.111.0; that is
GH #802. sha256 `57859226f5cfb638adb1c4dedd22367f68ed2afea3617721c89e5f51f5a69233`.
