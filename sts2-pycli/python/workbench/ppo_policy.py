"""Deployed PPO run-policy for the workbench.

Two interchangeable inference backends answer the same non-combat
decisions, selected per call by ``impl``, and each resolves the champion
net for the run's ``character`` from ``data/ppo/champions.toml`` (via
:mod:`champions` — the single source of truth the Rust resolver and the
Dockerfile are generated from). Both are cached per character:

- ``"python"`` — the committed torch checkpoint via
  :class:`ppo.net_policy.NetPolicy`. Needs ``torch``.
- ``"rust"`` — the committed ONNX snapshot via ``sts2_rs.PpoRunAgent``. No
  torch; works on the torch-less Cloud Run image.

Both go through the SAME training-time observation + masking (the Rust
encoder is parity-gated against ``ppo.env``), so a checkpoint scored by
either backend sees identical inputs. Each backend loads lazily and
degrades independently: a missing torch / checkpoint / ONNX / old wheel
flips that backend's :func:`available` to ``False`` (with an error
string) and the workbench falls back to the heuristic policy — it never
crashes the server.

Combat is never PPO's decision (training resolved it with MCTS), so
everything here returns ``None`` in combat.
"""

from __future__ import annotations

import os
import re
import threading
from dataclasses import dataclass
from typing import Any

import champions
import ppo_decode

_lock = threading.Lock()
# Both backends are cached per NORMALIZED character (lowercased); a live
# run of a given character resolves + loads its own champion once.
# python backend
_policies: dict[str, Any] = {}  # char -> ppo.net_policy.NetPolicy | None
_policy_errors: dict[str, str | None] = {}
# rust backend
_rust_agents: dict[str, Any] = {}  # char -> sts2_rs.PpoRunAgent | None
_rust_errors: dict[str, str | None] = {}


def _norm_char(character: str | None) -> str:
    return champions.normalize(character)


def ckpt_path(character: str = "Ironclad") -> str | None:
    """``STS2_WB_PPO_CKPT`` when set (an explicit override that wins for
    every character; None if the file is missing), else the torch checkpoint
    ``data/ppo/champions.toml`` declares for ``character``. None when the
    character has no committed champion.

    Raises:
        FileNotFoundError: the manifest names a checkpoint that is absent.
            Deliberately loud — a silent fall-back to an older net is the
            failure the manifest exists to prevent.
    """
    env = os.environ.get("STS2_WB_PPO_CKPT")
    if env:
        return env if os.path.exists(env) else None
    return champions.ckpt_path(character)


def onnx_path(character: str = "Ironclad") -> str | None:
    """``STS2_WB_PPO_ONNX`` when set (an explicit override that wins for
    every character; None if the file is missing), else the ONNX snapshot
    ``data/ppo/champions.toml`` declares for ``character``. Passed to
    ``PpoRunAgent(model_path=...)``. None when the character has no committed
    champion.

    Raises:
        FileNotFoundError: the manifest names a net that is absent.
    """
    env = os.environ.get("STS2_WB_PPO_ONNX")
    if env:
        return env if os.path.exists(env) else None
    return champions.onnx_path(character)


def _get_policy(character: str = "Ironclad") -> Any:
    """Lazily construct + cache the PYTHON (torch) backend for
    ``character``, or None (with the reason in ``_policy_errors``)."""
    key = _norm_char(character)
    with _lock:
        if key in _policies:
            return _policies[key]
        # A manifest that names an absent file raises — surface it as the
        # backend's error string rather than 500ing the workbench. Nothing
        # older is served either way; the manifest names one net or none.
        try:
            path = ckpt_path(character)
        except FileNotFoundError as exc:
            _policy_errors[key] = str(exc)
            _policies[key] = None
            return None
        if path is None:
            env = os.environ.get("STS2_WB_PPO_CKPT")
            if env:
                err = f"STS2_WB_PPO_CKPT={env!r} does not exist"
            elif champions.get(character) is None:
                err = f"no PPO checkpoint for character {character!r} (no committed champion)"
            else:
                err = (
                    f"no PPO checkpoint for {character!r} — set STS2_WB_PPO_CKPT, or give "
                    f"its data/ppo/champions.toml entry a `ckpt`"
                )
            _policy_errors[key] = err
            _policies[key] = None
            return None
        try:
            from ppo.net_policy import NetPolicy

            _policies[key] = NetPolicy(path)
            _policy_errors[key] = None
        except Exception as exc:  # torch/shape/IO — degrade, never crash the server
            _policy_errors[key] = f"failed to load PPO checkpoint {path}: {exc}"
            _policies[key] = None
        return _policies[key]


def _get_rust_agent(character: str = "Ironclad") -> Any:
    """Lazily construct + cache the RUST (ONNX) backend for ``character``,
    or None (with the reason in ``_rust_errors``)."""
    key = _norm_char(character)
    with _lock:
        if key in _rust_agents:
            return _rust_agents[key]
        _rust_agents[key] = None  # pessimistic default until construction succeeds
        try:
            path = onnx_path(character)
        except FileNotFoundError as exc:  # see _get_policy
            _rust_errors[key] = str(exc)
            return None
        if path is None:
            env = os.environ.get("STS2_WB_PPO_ONNX")
            if env:
                _rust_errors[key] = f"STS2_WB_PPO_ONNX={env!r} does not exist"
            else:
                _rust_errors[key] = (
                    f"no PPO ONNX for character {character!r} (no committed champion "
                    f"in data/ppo/champions.toml)"
                )
            return None
        try:
            import sts2_rs

            agent_cls = getattr(sts2_rs, "PpoRunAgent", None)
            if agent_cls is None:
                _rust_errors[key] = "sts2_rs has no PpoRunAgent (rebuild the wheel)"
                return None
            # PyO3 methods are descriptors on the class — check once, no
            # construction. Guards an old wheel predating Task 2.
            if not hasattr(agent_cls, "evaluate_with_actions"):
                _rust_errors[key] = (
                    "sts2_rs.PpoRunAgent lacks evaluate_with_actions (rebuild the wheel)"
                )
                return None
            # The encoder schema the champion was TRAINED at (GH #545): an
            # explicit `model_path` makes the binding fall back to the
            # process-wide `STS2_PPO_FEATURES`, which is empty on the server,
            # so a `reward_upgrade` champion was served at the default schema.
            # The manifest names the schema; an env-overridden net keeps the
            # env-declared one.
            champ = None if os.environ.get("STS2_WB_PPO_ONNX") else champions.get(character)
            features = None if champ is None else champ.features
            _rust_agents[key] = agent_cls(seed=0, model_path=path, features=features)
            _rust_errors[key] = None
        except BaseException as exc:  # incl. pyo3 PanicException (BaseException, not Exception) — Rust panic must degrade, never 500
            _rust_errors[key] = f"failed to load PPO ONNX agent: {exc}"
            _rust_agents[key] = None
        return _rust_agents[key]


def _reset_for_tests() -> None:
    with _lock:
        _policies.clear()
        _policy_errors.clear()
        _rust_agents.clear()
        _rust_errors.clear()


def available(impl: str = "python", character: str = "Ironclad") -> bool:
    if impl == "rust":
        return _get_rust_agent(character) is not None
    return _get_policy(character) is not None


@dataclass
class PpoEval:
    """PPO's view of one non-combat decision."""

    phase: str
    surface: str
    actions: list[dict[str, Any]]  # masked candidates — the net's view
    full_actions: list[dict[str, Any]]  # unmasked available_actions()
    probs: list[float] | None  # None when the net can't load
    value: float | None
    chosen: int | None  # argmax over probs
    error: str | None = None


def evaluate(
    rm: Any, *, character: str = "Ironclad", ascension: int = 0, impl: str = "python"
) -> PpoEval | None:
    """PPO's distribution over the current decision, or ``None`` when the
    decision isn't PPO's (combat / finished / no actions / unknown surface).
    ``impl`` selects the inference backend (``"python"`` torch / ``"rust"``
    ONNX); ``character`` selects that character's committed champion net."""
    from ppo.env import classify_surface, mask_rejectable

    if rm is None or rm.is_over():
        return None
    phase = rm.phase()
    if phase in ("combat", "run_over"):
        return None
    full = rm.available_actions()
    if not full:
        return None
    masked = mask_rejectable(rm, phase, full)
    surface = classify_surface(phase, masked)
    if surface is None:
        return None
    if len(masked) == 1:
        # Single-option decisions are auto-taken in training; no net needed.
        return PpoEval(phase, surface, masked, full, [1.0], None, 0)
    if impl == "rust":
        return _evaluate_rust(rm, character, phase, surface, masked, full)
    return _evaluate_python(rm, character, phase, surface, masked, full, ascension)


def _evaluate_python(
    rm: Any, character: str, phase: str, surface: str, masked: list, full: list, ascension: int
) -> PpoEval:
    from ppo.env import metrics_snapshot, observe

    policy = _get_policy(character)
    if policy is None:
        return PpoEval(
            phase,
            surface,
            masked,
            full,
            None,
            None,
            None,
            error=_policy_errors.get(_norm_char(character)),
        )
    try:
        obs = observe(rm, phase, surface, seed=int(rm.seed), ascension=ascension)
        probs, value = policy.distribution(obs, masked, metrics_snapshot(rm))
        # Group-mass decode, matching the rust backend below (whose argmax is
        # `PpoPolicy::grouped_argmax`): a plain per-candidate argmax here made
        # the panel's star jump when you flipped the backend toggle on any
        # split-mass screen — a shop fans removal out into one candidate per
        # deck card, so the intent's pooled mass and the biggest single bar
        # are routinely different options.
        chosen = ppo_decode.select_index(masked, list(probs))
    except BaseException as exc:  # incl. pyo3 PanicException (BaseException, not Exception) — Rust panic must degrade, never 500
        return PpoEval(
            phase, surface, masked, full, None, None, None, error=f"inference failed: {exc}"
        )
    return PpoEval(phase, surface, masked, full, probs, value, chosen)


def _evaluate_rust(
    rm: Any, character: str, phase: str, surface: str, masked: list, full: list
) -> PpoEval:
    agent = _get_rust_agent(character)
    if agent is None:
        return PpoEval(
            phase,
            surface,
            masked,
            full,
            None,
            None,
            None,
            error=_rust_errors.get(_norm_char(character)),
        )
    try:
        res = agent.evaluate_with_actions(rm)
    except BaseException as exc:  # incl. pyo3 PanicException (BaseException, not Exception) — Rust panic must degrade, never 500
        return PpoEval(
            phase, surface, masked, full, None, None, None, error=f"inference failed: {exc}"
        )
    if res is None:
        # Parity: rust also declines <2 masked; single-option handled above, so
        # this only happens if the two masks disagree — surface as unavailable.
        return PpoEval(
            phase,
            surface,
            masked,
            full,
            None,
            None,
            None,
            error="rust backend returned no distribution for a multi-option decision",
        )
    rust_actions, probs, value, chosen = res
    rust_surface = classify_surface_or(phase, rust_actions, surface)
    return PpoEval(phase, rust_surface, list(rust_actions), full, list(probs), value, int(chosen))


def classify_surface_or(phase: str, actions: list, default: str) -> str:
    """``ppo.env.classify_surface`` on the rust-returned masked actions, or the
    already-computed ``default`` if it can't classify (never None to the panel)."""
    from ppo.env import classify_surface

    return classify_surface(phase, actions) or default


def recommend(
    rm: Any, *, character: str = "Ironclad", ascension: int = 0, impl: str = "python"
) -> tuple[str, dict[str, Any]] | None:
    """Greedy PPO ``(verb, args)`` for the current non-combat decision, or
    ``None`` when PPO can't decide here — the caller falls back to the
    heuristic policy."""
    from run_policy import resolve_workbench_verb

    ev = evaluate(rm, character=character, ascension=ascension, impl=impl)
    if ev is None or ev.chosen is None:
        return None
    return resolve_workbench_verb(rm, ev.actions[ev.chosen], ev.full_actions)


_CAMEL_SPLIT = re.compile(r"(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")


def shop_remove_label(deck: list[tuple[str, bool]], deck_idx: int) -> str | None:
    """Human label for a shop ``shop_remove_card`` action, or ``None`` when
    ``deck_idx`` is out of range (caller falls back to the generic label).

    The generic ``describe_action`` path resolves ``select_cards`` indices
    against the SHOP state's ``cards`` (the stock), not the master deck, so
    removal options degrade to "Select card 16" — name the deck card instead.
    """
    if not 0 <= deck_idx < len(deck):
        return None
    card_id, upgraded = deck[deck_idx]
    name = _CAMEL_SPLIT.sub(" ", str(card_id))
    return f"Remove {name}{'+' if upgraded else ''} (deck #{deck_idx})"
