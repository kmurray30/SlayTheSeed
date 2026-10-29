"""Torch-free ``NetPolicy.distribution`` twin backed by onnxruntime (GH #380).

The advisor's only torch use was loading the run-policy ``.pt`` checkpoint;
every champion also ships a committed ONNX twin (served by tract in
``sts2-runai``). Running that graph through onnxruntime drops torch (504 MB,
~45 s cold import) from the advisor's runtime entirely.

Mirrors the Rust consumer (``sts2-rs/crates/sts2-runai/src/ppo/net.rs``):

- ``T_MAX`` is read off the ``cont`` input fact and ``A_MAX`` off
  ``act_mask`` rather than duplicated as consts, so a re-export at different
  budgets cannot silently mismatch (GH #315).
- Legacy 18-cont exports (pre-ascension-input) and 9-categorical exports
  (pre-boss-input) are handled by slicing the featurized columns down to the
  graph's width. Both new inputs live in the TRAILING slot of their array for
  exactly this reason, so the slice drops the input the old graph never had
  and every already-gated champion keeps serving unchanged.
- Softmax runs in f64 with max subtraction over the unpadded logit slice.

Unlike torch's dynamic ``collate``, the fixed-shape path can exceed the
export budget (``schema.BudgetExceededError``) — callers on the advice path
must catch and degrade, never crash.
"""

from __future__ import annotations

import champions
import numpy as np

from . import schema
from .encoder import featurize_row
from .schema import pad_to_fixed


class OnnxPolicy:
    """Same ``distribution`` contract as :class:`ppo.net_policy.NetPolicy`.

    One instance holds one ``InferenceSession`` — construction is the
    expensive part, so cache per character (``realgame_ppo.PpoPolicyCache``);
    map-route projection alone re-calls ``distribution`` up to 8 times per
    decision. Single-threaded: the advisor shares the box with the live game
    and the combat MCTS (torch twin sets ``torch.set_num_threads(1)``).
    """

    def __init__(self, model_path: str):
        import onnxruntime as ort  # deferred: keep module importable without ort

        so = ort.SessionOptions()
        so.intra_op_num_threads = 1
        so.inter_op_num_threads = 1
        self.session = ort.InferenceSession(
            model_path, sess_options=so, providers=["CPUExecutionProvider"]
        )
        shapes = {i.name: i.shape for i in self.session.get_inputs()}
        self.t_max = int(shapes["cont"][1])  # 320 on current exports
        self.n_cont = int(shapes["cont"][2])  # 19; 18 on legacy exports
        self.n_cat = int(shapes["cat"][2])  # 10; 9 on pre-boss-input exports
        self.a_max = int(shapes["act_mask"][1])  # 160 (96 pre-GH#315)

        # GH #545: an ONNX graph carries no cfg (unlike the torch checkpoint --
        # see `NetPolicy`), and `<net>.provenance.md` is prose, not data. So the
        # feature set this net was TRAINED with comes from `champions.toml`'s
        # entry for it, keyed by basename; a model path that isn't a declared
        # champion (an ad-hoc export, a retired net) gets the default schema.
        champ = champions.by_onnx_filename(model_path)
        self.features: frozenset[str] = (
            schema.parse_features(champ.features) if champ else frozenset()
        )

    def distribution(
        self, obs: dict, actions: list[dict], metrics: dict
    ) -> tuple[list[float], float]:
        """Full masked softmax over the candidates + V(s).

        Raises :class:`ppo.schema.BudgetExceededError` when the decision
        exceeds the graph's fixed T_MAX/A_MAX budget.
        """
        feat = featurize_row(obs, actions, metrics, features=self.features)
        inp = pad_to_fixed(feat, self.t_max, self.a_max)
        if inp["cont"].shape[2] > self.n_cont:  # legacy 18-cont graph (net.rs:225-232)
            inp["cont"] = np.ascontiguousarray(inp["cont"][:, :, : self.n_cont])
        if inp["cat"].shape[2] > self.n_cat:  # pre-boss-input 9-categorical graph
            inp["cat"] = np.ascontiguousarray(inp["cat"][:, :, : self.n_cat])
        logits, value = self.session.run(["logits", "value"], inp)
        z = logits[0, : feat.n_actions].astype(np.float64)
        z -= z.max()  # padding slots are -inf in-graph; sliced off before this
        p = np.exp(z)
        p /= p.sum()
        return [float(x) for x in p], float(value[0])
