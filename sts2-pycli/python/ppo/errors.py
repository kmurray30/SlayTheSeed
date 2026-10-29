"""PPO exception types, importable without numpy.

Split out of :mod:`ppo.schema` for the same reason ``schema`` was split out
of :mod:`ppo.model`: the type is needed on paths that must not drag the
heavy dependency in. ``schema`` imports numpy at module scope (it builds the
padded fixed-shape tensors), so ``from ppo.schema import
BudgetExceededError`` -- which :mod:`realgame_ppo` did at module level --
made numpy a hard requirement of the advisor even when nothing on the live
path featurizes in Python.

``ppo.schema`` re-exports everything here, so every existing import site
stays valid.
"""

from __future__ import annotations


class BudgetExceededError(ValueError):
    """Decision exceeds the fixed-shape export budget (T_MAX/A_MAX).

    Only the fixed-shape paths can hit this — the ONNX graph is exported at
    a fixed ``(t_max, a_max)`` and both backends read those off the loaded
    model (:class:`ppo.onnx_policy.OnnxPolicy`,
    :class:`ppo.rust_policy.RustPolicy`); torch's ``collate`` sizes
    dynamically and cannot. Callers on the advice path must degrade, not
    crash — a 103-option shop removal killed a full run this way once
    (GH #315).
    """


__all__ = ["BudgetExceededError"]
