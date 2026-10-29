"""Fast per-option ranking for an in-combat pending choice — one combat MCTS
search instead of the :mod:`workbench.decision_advisor`'s full-act rollouts.

When a card opens a single-pick in-combat choice (Headbutt's "put a discard
card on top of your draw pile", an upgrade-in-hand pick, …) the player can ask
the slow decision advisor for an EV per option, but that forks the run and plays
the **rest of the act** out many times *per option* — accurate, but seconds of
work. This module is the cheap counterpart: a single ``sts2_rs.mcts_recommend``
search over the live combat state. Because an open ``pending_choice`` masks every
other action, the engine's legal moves at the root *are* the choice options, so
one search already scores each option (visit / Q / win-probability) the same way
it scores card plays.

Multi-pick choices (Dredge's "choose up to 3") rank too, one pick at a time:
the engine decomposes them sequentially (bead ``sts2-rs-sopgf``), so the root
children are "accumulate option *i*" plus a FINALIZE (keyed ``choice:skip``),
and the search explores each accumulation subtree beneath them. A row is
therefore "best NEXT pick", not "best set of K" — re-rank after each pick.

Out-of-combat decisions (shops, events) stay with
:mod:`workbench.decision_advisor`. Requires the Rust backend's native combat
state; the C# backend (a subprocess) can't be searched in-process, so callers
surface an error instead.
"""

from __future__ import annotations

from typing import Any

from workbench import reasoning
from workbench.decision_advisor import _candidates, _pending_choice


def is_rankable(rm: Any) -> bool:
    """True when *rm* sits on an in-combat pending choice with ≥2 legal options.

    The fast ranker only applies in combat: it scores the choice off one combat
    MCTS search, which is meaningful only while the engine is in a fight.
    Everything out of combat belongs to :mod:`workbench.decision_advisor`.

    Multi-pick is included. ``legal_actions`` decomposes an ``max_select > 1``
    choice into one incremental pick per still-unpicked option plus a FINALIZE,
    so the root children are ordinary rankable actions and the search reads the
    accumulation subtree under each. Each ranking answers "best NEXT pick"; the
    caller re-ranks on the re-opened choice until FINALIZE wins.
    """
    if rm is None or rm.phase() != "combat":
        return False
    if not _pending_choice(rm):
        return False
    return len(_candidates(rm)) >= 2


def rank(rm: Any, config: dict[str, Any] | None = None) -> dict[str, Any]:
    """Per-option combat-MCTS ranking for the live run's in-combat choice.

    Runs one ``sts2_rs.mcts_recommend`` over the native combat state and joins
    each search result to its choice option, so every option carries the search's
    ``win_prob`` / ``q`` / ``visits`` and a single ``is_best`` flag. Read-only —
    the search clones the state internally, so the live run is never mutated.

    Args:
        rm: An ``sts2_rs.RunManager`` parked on an in-combat choice.
        config: Optional MCTS overrides (``sims``, ``depth``, ``c_puct``,
            ``threads``, ``seed``, ``rollout_policy``), same
            envelope as the combat hint. Echoed back under ``config``.

    Returns:
        On success::

            {"candidates": [{"key", "label", "win_prob", "q", "visits",
                             "is_best"}],
             "decision": "card_select", "act": int, "config": {...}}

        ``key`` is ``"choice:{opt}"`` / ``"choice:skip"`` — the same join key the
        slow advisor and the frontend use. On a multi-pick choice a row is the
        best NEXT pick and ``choice:skip`` is FINALIZE. On a non-rankable state
        or a search failure: ``{"error": str, "candidates": [], ...}``.
    """
    config = dict(config or {})
    if not is_rankable(rm):
        return {
            "error": "the choice ranker only runs on an in-combat card-select "
            "choice with at least two legal options",
            "candidates": [],
            "config": config,
        }

    cs = rm.combat_state()
    if cs is None:
        return {
            "error": "no native combat state (the choice ranker requires the Rust engine backend)",
            "candidates": [],
            "config": config,
        }

    ranking = reasoning.rank_choice_native(cs, config)
    if "error" in ranking:
        return {"error": ranking["error"], "candidates": [], "config": config}

    by_key = ranking["by_key"]
    rows: list[dict[str, Any]] = []
    for key, label, _action in _candidates(rm):
        row = by_key.get(key)
        rows.append(
            {
                "key": key,
                "label": label,
                "win_prob": row["win_prob"] if row else None,
                "q": row["q"] if row else None,
                "visits": row["visits"] if row else 0,
                "is_best": bool(row["is_best"]) if row else False,
            }
        )
    # If the search marked no row best (e.g. a single visited option), fall back
    # to the highest win_prob among scored rows so the UI always has one pick.
    if rows and not any(r["is_best"] for r in rows):
        scored = [r for r in rows if r["win_prob"] is not None]
        if scored:
            max(scored, key=lambda r: (r["win_prob"], r["visits"]))["is_best"] = True

    return {
        "candidates": rows,
        "decision": "card_select",
        "act": rm.act_index,
        "config": ranking.get("config", config),
    }


def best_action(rm: Any, config: dict[str, Any] | None = None) -> dict[str, Any] | None:
    """The raw engine action behind the option :func:`rank` marks best.

    The pick the UI stars on its ``search %`` chip, handed back as the
    ``combat_resolve_choice`` action dict ``run_policy.to_workbench_verb``
    turns into a live-endpoint verb (``select_cards`` / ``skip_select``). Pass
    the same *config* the chips were fetched with — the search is deterministic
    for a given config, so the caller then plays exactly what the panel shows.

    On a multi-pick choice this is the best NEXT pick (or FINALIZE); call it
    again on the re-opened choice until the engine closes it.

    Returns ``None`` when the state isn't rankable (out of combat, fewer than
    two legal options), when the search failed, or when the best row has no
    matching legal action; callers fall back to their own default.
    """
    out = rank(rm, config)
    if out.get("error"):
        return None
    best_key = next((c["key"] for c in out["candidates"] if c["is_best"]), None)
    if best_key is None:
        return None
    return next((action for key, _label, action in _candidates(rm) if key == best_key), None)
