"""Group-mass decode of a PPO run-policy distribution (A8 audit §8).

The port of ``sts2-runai/src/ppo/policy.rs``'s ``grouped_argmax`` for the
two Python lanes that pick an action from the net's probabilities: the
live-game advisor (:mod:`realgame_ppo`) and the workbench panel's torch
backend (:mod:`workbench.ppo_policy`). Rust's ``PpoPolicy`` — run_arena,
the workbench's default ONNX backend, and (since this module landed) the
bridge's wire lane — applies the same rule, so all four decode one
distribution the same way.

Why it exists: a screen fans a single INTENT out into one candidate per
target — a shop emits one ``shop_remove_card`` per removable deck card —
and the softmax splits that intent's mass across them. Measured on the
30-run A8 audit, a 0.667 total "remove a starter" preference split 5 ways
(0.133 each) lost the plain argmax to a single 0.142 ``shop_buy_card``;
11 of 137 scored shop decisions picked an action type with strictly less
total mass than a competitor, 8 of them losing to removal.

Deliberately NOT applied to the probabilities themselves: the bars the
workbench draws stay the net's raw per-candidate output. Only the pick
changes.

Zero imports beyond the stdlib on purpose — :mod:`realgame_ppo` is frozen
into the advisor exe, which must stay numpy-free
(``tests/test_advisor_closure_is_numpy_free.py``).
"""

from __future__ import annotations

import os
from typing import Any

#: Action kinds whose candidates are one intent fanned out over targets: the
#: kind fixes WHAT happens and the payload only picks WHICH card. Every other
#: kind is its own group — ``shop_buy_card{idx: 1}`` and ``{idx: 2}`` are
#: genuinely different decisions, not one preference split over targets.
#:
#: Mirrors ``policy.rs::GroupKey`` exactly, including what it leaves out
#: (``remove_deck_card`` / ``upgrade_deck_card`` / ``grant_deck_card``, the
#: event-driven deck-mutation surfaces). Widening the set changes what the
#: deployed champions do and needs its own win-rate measurement — not a
#: drive-by edit here.
POOLED_KINDS = frozenset(
    {
        "shop_remove_card",
        "transform_deck_card",
        "enchant_deck_card",
        "smith_card",
    }
)

#: Rollback seam, shared with the Rust side: ``"0"`` restores the plain
#: per-candidate argmax. Any other value (including ``"1"``) keeps the group
#: decode — a rollback switch, not a mode enum.
GROUP_DECODE_ENV = "STS2_RUNAI_PPO_GROUP_DECODE"


def group_decode_enabled(env_value: str | None = None) -> bool:
    """Whether the group decode is on. Default ON; ``"0"`` turns it off."""
    if env_value is None:
        env_value = os.environ.get(GROUP_DECODE_ENV)
    return env_value != "0"


def argmax_first(xs: list[float]) -> int:
    """The index of the largest value, ties broken toward the SMALLEST index.

    ``max(range(n), key=xs.__getitem__)`` and numpy's ``argmax`` both do this;
    spelled out here so the tie-break the Rust port asserts against
    (``policy.rs::argmax_first``) is stated once and shared by both callers.
    """
    if not xs:
        raise ValueError("argmax_first: empty list")
    return max(range(len(xs)), key=xs.__getitem__)


def grouped_argmax(actions: list[dict[str, Any]], probs: list[float]) -> int:
    """Pool each intent's mass, argmax over the pooled masses, then return the
    highest-probability member WITHIN the winning group.

    Both argmaxes are first-max, so the decode is deterministic and ties break
    exactly as the ungrouped path would. On a screen with no pooled kind every
    candidate is its own group and this is exactly :func:`argmax_first`.

    Args:
        actions: The masked candidates, in the order ``probs`` scores them.
            Each is the ``run_action_to_pydict`` dict shape (an ``"action"``
            key naming the kind).
        probs: Per-candidate probabilities, same length and order.

    Returns:
        The index into ``actions`` / ``probs`` to take.
    """
    if len(actions) != len(probs):
        raise ValueError(f"grouped_argmax: {len(actions)} actions vs {len(probs)} probs")
    # First-occurrence order keeps the group argmax's tie-break aligned with
    # candidate order (and thus with argmax_first).
    keys = [
        kind if (kind := str(a.get("action"))) in POOLED_KINDS else f"#{i}"
        for i, a in enumerate(actions)
    ]
    order: list[str] = []
    mass: dict[str, float] = {}
    for key, p in zip(keys, probs, strict=True):
        if key not in mass:
            order.append(key)
            mass[key] = 0.0
        mass[key] += p
    winner = order[argmax_first([mass[k] for k in order])]
    members = [i for i, key in enumerate(keys) if key == winner]
    return members[argmax_first([probs[i] for i in members])]


def select_index(
    actions: list[dict[str, Any]], probs: list[float], *, enabled: bool | None = None
) -> int:
    """:func:`grouped_argmax` when the decode is on, else :func:`argmax_first`.

    ``enabled`` overrides the env seam (tests pin both branches).
    """
    on = group_decode_enabled() if enabled is None else enabled
    return grouped_argmax(actions, probs) if on else argmax_first(probs)
