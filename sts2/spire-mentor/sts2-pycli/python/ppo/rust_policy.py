"""Torch- and onnxruntime-free ``distribution`` twin backed by ``sts2_rs``.

:class:`ppo.onnx_policy.OnnxPolicy` dropped torch (504 MB, ~45 s cold
import) from the advisor by running the champion graph on onnxruntime. It
left the *other* half: featurization in :mod:`ppo.encoder` and the padded
tensors in :mod:`ppo.schema` are numpy, and onnxruntime itself is 34.5 MB.
Together they are ~54 MB of the shipped bundle and the bulk of what remains
of its cold start.

Both halves already exist in Rust and are pinned against these ones:
``sts2-runai::ppo::encoder`` against ``ppo/encoder.py`` by the
``ppo_encoder_parity`` fixture gate, and ``sts2-runai::ppo::net`` against
torch by ``ppo_net_parity``. This class is the door — same
``distribution(obs, actions, metrics) -> (probs, value)`` contract as
:class:`~ppo.onnx_policy.OnnxPolicy` and :class:`~ppo.net_policy.NetPolicy`,
so :func:`realgame_ppo.load_ppo_policy` swaps the backend and nothing
downstream changes.

Scoring the *observation* rather than the bridge message is what makes it a
drop-in: it covers every surface the Python builders emit, including
``card_select``, which the wire-side port (``sts2_rs.ppo_wire_obs``) does
not.

`sts2_rs` is a hard requirement here, not an optional accelerator: the
advisor already imports it for the combat MCTS.
"""

from __future__ import annotations

from typing import Any

import champions
import sts2_rs

from .errors import BudgetExceededError


class RustPolicy:
    """Same ``distribution`` contract as :class:`ppo.onnx_policy.OnnxPolicy`.

    One instance holds one loaded graph. Construction is the expensive part
    (``tract`` parses and optimizes the ~8 MB model), so cache per character
    — :class:`realgame_ppo.PpoPolicyCache` does, and map-route projection
    alone re-calls :meth:`distribution` up to 8 times per decision.

    Args:
        model_path: An exported champion ``.onnx``. Resolved by the caller
            (``workbench.ppo_policy.onnx_path``), never here — ``champions.
            toml`` is the single source of truth and a second resolver is
            how a retired net ships (#339).

    Raises:
        ValueError: The model is missing or cannot be loaded.
    """

    def __init__(self, model_path: str):
        # Same resolution as `ppo.onnx_policy.OnnxPolicy`: the encoder schema
        # this net was TRAINED with comes from `champions.toml`'s entry for it,
        # and a path that is not a declared champion gets the default schema.
        # Passed EXPLICITLY across the FFI rather than left to the Rust side's
        # process-wide `STS2_PPO_FEATURES`, because one process holds one
        # instance per character (`realgame_ppo.PpoPolicyCache`) and a
        # process-wide switch cannot say "on for Ironclad, off for the rest"
        # (GH #545).
        champ = champions.by_onnx_filename(model_path)
        self.features: str = champ.features if champ else ""
        self._net = sts2_rs.PpoNetPolicy(model_path, self.features)
        self.t_max = self._net.t_max
        self.n_cont = self._net.n_cont
        self.a_max = self._net.a_max

    def distribution(
        self, obs: dict, actions: list[dict], metrics: dict
    ) -> tuple[list[float], float]:
        """Full masked softmax over the candidates + V(s).

        Raises:
            BudgetExceededError: The decision exceeds the graph's fixed
                T_MAX/A_MAX budget. Re-raised as the *same* type
                :class:`~ppo.onnx_policy.OnnxPolicy` raises, so
                ``realgame_ppo``'s existing handler — which degrades to a
                random legal pick and tags the decision rather than
                crashing a live run — keeps working untouched.
        """
        try:
            return self._net.distribution(obs, actions, metrics)
        except sts2_rs.PpoBudgetExceeded as e:
            raise BudgetExceededError(str(e)) from e


def available() -> bool:
    """Whether this backend can be constructed at all.

    The class is added by the same module init that adds the wire helpers,
    so an old wheel in the venv (a stale ``maturin develop``, a mismatched
    sibling checkout) is the realistic way it goes missing. Callers use
    this to fall back to :class:`~ppo.onnx_policy.OnnxPolicy` with a
    diagnosable message instead of an ``AttributeError`` mid-decision.
    """
    return hasattr(sts2_rs, "PpoNetPolicy")


def describe_unavailable() -> str:
    """Why :func:`available` is False, for a log line."""
    return (
        f"the installed sts2_rs wheel ({getattr(sts2_rs, '__file__', '?')}) has no "
        "PpoNetPolicy -- rebuild it with `scripts/wheel-venv.sh build`"
    )


__all__ = ["RustPolicy", "available", "describe_unavailable"]
