"""Surrogate that reuses the PPO champion's learned representation.

The diagnosed residual after the distributional head fell short was ACROSS-row
under-dispersion of the conditional mean (predicted std 0.163 vs actual 0.209):
the net cannot separate dangerous fights from safe ones sharply enough, and no
amount of correct sampling widens a mean that is already too flat. That is a
REPRESENTATION problem, so this attacks the representation on two fronts:

1. **Potion identity.** Until 2026-08-14 the features carried `potions.len()`
   and nothing else, so a Fire Potion and a Block Potion were the same input
   while the harvester randomized `PotionKind::ALL` into the state. That is an
   unmodellable cause of outcome variance — a hard ceiling on the conditional
   mean.

2. **Transferred embeddings.** The surrogate was learning 580 card embeddings
   from a few thousand rows. The champion PPO net already has them, trained on
   millions of decisions, at exactly dim 192 — the surrogate's own width — so
   the transfer is a straight copy with no projection.

Index spaces: `ranker_features` sorts by Rust Debug name, `ppo/vocab.py` by the
registry's canonical name, so the two disagree. Tokens are therefore remapped
to PPO-VOCAB indices at batch time, which makes the embedding tables literally
the champion's and the transfer a row-for-row copy. Verified 2026-08-14:
580/580 cards, 297/297 relics, 63/63 potions map with ZERO OOV.
"""

from __future__ import annotations

import os
import sys

import numpy as np
import torch
from torch import nn

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ppo import vocab

ASCENSION_SCALAR_IDX = 9
# Token type ids. CLS and ENC keep slots 0/1 so the layout matches the
# mean-only model's; potions are new.
T_CLS, T_ENC, T_CARD, T_RELIC, T_POTION = 0, 1, 2, 3, 4


def build_id_maps(schema: dict) -> dict[str, np.ndarray]:
    """surrogate index -> PPO vocab index, per space.

    Built from the schema's name tables rather than assumed: the two index
    spaces are sorted by different keys, so a positional copy would silently
    hand every card the wrong embedding.
    """
    out = {}
    for key, reg in (
        ("card_ids", vocab.cards()),
        ("relic_ids", vocab.relics()),
        ("potion_ids", vocab.potions()),
    ):
        names = schema[key]
        out[key] = np.array([reg.index(n) for n in names], dtype=np.int64)
    return out


class EncoderBatch:
    """Pre-tokenized rows with potion tokens and PPO-vocab ids.

    Layout: [CLS, ENC, cards..., relics..., potions...].
    """

    def __init__(self, rows: list[dict], id_maps: dict, max_len: int):
        n = len(rows)
        self.n_scalars = len(rows[0]["scalars"])
        self.card_map = id_maps["card_ids"]
        self.relic_map = id_maps["relic_ids"]
        self.potion_map = id_maps["potion_ids"]

        self.tok_id = np.zeros((n, max_len), dtype=np.int64)
        self.tok_type = np.zeros((n, max_len), dtype=np.int64)
        self.tok_num = np.zeros((n, max_len, 2), dtype=np.float32)
        self.mask = np.ones((n, max_len), dtype=bool)  # True == PAD
        self.scalars = np.zeros((n, self.n_scalars), dtype=np.float32)
        self.enc = np.zeros(n, dtype=np.int64)
        self.win = np.zeros(n, dtype=np.float32)
        self.hp = np.zeros(n, dtype=np.float32)
        self.sd = np.zeros(n, dtype=np.float32)
        self.bucket: list[tuple] = []

        self.truncated = 0
        self.n_potion_tokens = 0
        for i, r in enumerate(rows):
            toks: list[tuple[int, int, float, float]] = [(0, T_CLS, 0.0, 0.0), (0, T_ENC, 0.0, 0.0)]
            for card_idx, count, upg in r["deck"]:
                toks.append((int(self.card_map[int(card_idx)]), T_CARD, float(count), float(upg)))
            for relic_idx, count in r["relics"]:
                toks.append((int(self.relic_map[int(relic_idx)]), T_RELIC, float(count), 0.0))
            for pidx in r.get("potions", []):
                toks.append((int(self.potion_map[int(pidx)]), T_POTION, 1.0, 0.0))
                self.n_potion_tokens += 1
            if len(toks) > max_len:
                self.truncated += 1
                # Truncating from the END would drop potions first, since they
                # are appended last — exactly the signal this model exists to
                # add. Keep CLS/ENC, then take the tail, which preserves them.
                toks = toks[:2] + toks[2:][-(max_len - 2) :]
            for j, (tid, ttype, a, b) in enumerate(toks):
                self.tok_id[i, j] = tid
                self.tok_type[i, j] = ttype
                self.tok_num[i, j, 0] = a
                self.tok_num[i, j, 1] = b
                self.mask[i, j] = False
            self.scalars[i] = np.asarray(r["scalars"], dtype=np.float32)
            self.enc[i] = int(r["enc_idx"])
            self.win[i] = float(r["win_rate"])
            self.hp[i] = float(r["hp_frac_lost"])
            self.sd[i] = float(r.get("hp_frac_lost_std", 0.0))
            self.bucket.append((int(r["enc_idx"]), float(r["scalars"][ASCENSION_SCALAR_IDX])))

    def __len__(self) -> int:
        return len(self.enc)


class EncoderSurrogate(nn.Module):
    """Deck/relic/POTION tokens over the champion's embedding tables."""

    def __init__(
        self,
        champion_sd: dict,
        n_enc: int,
        n_scalars: int,
        dim: int = 192,
        layers: int = 3,
        heads: int = 4,
        freeze: bool = False,
    ):
        super().__init__()
        self.emb_card = self._from_champion(champion_sd, "emb_card.weight", dim, freeze)
        self.emb_relic = self._from_champion(champion_sd, "emb_relic.weight", dim, freeze)
        self.emb_potion = self._from_champion(champion_sd, "emb_potion.weight", dim, freeze)
        self.type_emb = nn.Embedding(5, dim)
        self.num_proj = nn.Linear(2, dim)
        self.enc_emb = nn.Embedding(n_enc + 1, dim)
        self.scalar_proj = nn.Sequential(nn.Linear(n_scalars, dim), nn.ReLU(), nn.Linear(dim, dim))
        enc_layer = nn.TransformerEncoderLayer(
            d_model=dim,
            nhead=heads,
            dim_feedforward=dim * 4,
            dropout=0.1,
            batch_first=True,
            norm_first=True,
        )
        self.tr = nn.TransformerEncoder(enc_layer, num_layers=layers)
        self.norm = nn.LayerNorm(dim)
        self.win_head = nn.Linear(dim, 1)
        self.hp_head = nn.Linear(dim, 1)
        self.sd_head = nn.Linear(dim, 1)

    @staticmethod
    def _from_champion(sd: dict, key: str, dim: int, freeze: bool) -> nn.Embedding:
        w = sd[key]
        if w.shape[1] != dim:
            raise ValueError(
                f"{key} is dim {w.shape[1]} but the surrogate is dim {dim}; "
                "transfer would need a projection, which this does not do"
            )
        emb = nn.Embedding(w.shape[0], dim)
        with torch.no_grad():
            emb.weight.copy_(w)
        emb.weight.requires_grad = not freeze
        return emb

    def forward(self, tok_id, tok_type, tok_num, mask, scalars, enc):
        # One lookup per space, selected by token type. Three tables rather
        # than one shared table because card 7, relic 7 and potion 7 are
        # unrelated things and the champion trained them separately.
        h = self.type_emb(tok_type) + self.num_proj(tok_num)
        for ttype, emb in (
            (T_CARD, self.emb_card),
            (T_RELIC, self.emb_relic),
            (T_POTION, self.emb_potion),
        ):
            sel = tok_type == ttype
            if sel.any():
                h = h + torch.where(sel.unsqueeze(-1), emb(tok_id * sel), torch.zeros_like(h))
        h[:, 1, :] = self.enc_emb(enc) + self.type_emb(torch.full_like(enc, T_ENC))
        h[:, 0, :] = h[:, 0, :] + self.scalar_proj(scalars)
        h = self.tr(h, src_key_padding_mask=mask)
        cls = self.norm(h[:, 0, :])
        return (
            self.win_head(cls).squeeze(-1),
            torch.sigmoid(self.hp_head(cls)).squeeze(-1),
            nn.functional.softplus(self.sd_head(cls)).squeeze(-1),
        )


def load_champion(path: str) -> dict:
    """Load only the champion's weights.

    `weights_only=True` first: the checkpoint also carries `opt` and `cfg`,
    and nothing here needs them unpickled. Falls back only if the safe loader
    rejects the file, since a stale champion that cannot be read at all is a
    louder failure than one read permissively.
    """
    try:
        ck = torch.load(path, map_location="cpu", weights_only=True)
    except Exception as e:
        print(f"  note: safe load failed ({type(e).__name__}), retrying permissively", flush=True)
        ck = torch.load(path, map_location="cpu", weights_only=False)
    return ck.get("state_dict", ck)
