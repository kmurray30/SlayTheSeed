"""MCTS run recorder — drives MCTS over a fixture or a full run, writing the
canonical workbench JSONL run-log (``game_log.GameLogger``).

Two entry points:

* :func:`record_fixture` (PRIMARY) — loads a ``.run-source`` scenario TOML into
  a native ``sts2_rs`` combat state and plays it to terminal with MCTS picking
  each move. No ``RunManager`` / ``RustGame``; the state drives itself.
* :func:`record_run` (best-effort) — drives a full self-play run via
  :class:`workbench.backends.rust_game.RustGame`, routing combat decisions
  through MCTS and taking the first legal action elsewhere. The run-sim layer
  is partial, so this is allowed to bail out early.

The log is a sequence of JSONL records consumed by :mod:`workbench.report`
(Markdown) and ``workbench.aggregate``:

* a head ``meta`` (character/seed from the constructor) + a provenance ``meta``
  (``fixture_stem`` / ``build_id`` / ``encounter`` / ``sims``);
* per decision: a ``state`` record, an ``mcts`` record (shared step), an
  ``action`` record;
* a trailing summary ``meta`` (``final_won`` / ``final_turn`` /
  ``final_player_hp`` / ``final_player_max_hp``).
"""

from __future__ import annotations

import argparse
import json
import sys
import tomllib
from pathlib import Path
from typing import Any

from game_log import GameLogger

# Sentinel stable_idx values, mirrored from sts2_hint.recommender /
# workbench.reasoning so report/aggregate/timeline agree.
_END_TURN = -1
_POTION_BASE = -100  # potion slot s -> -100 - s

_REPO_ROOT = Path(__file__).resolve().parents[2]


# ── action-dict → (stable_idx, label, target) mapping ─────────────────────
def _stable_idx_and_label(
    action: dict[str, Any], hand: list[dict[str, Any]]
) -> tuple[int, str, int | None]:
    """Map a native action dict to ``(stable_idx, action_label, target_index)``.

    Mirrors :func:`workbench.reasoning.rows_from_mcts`: a ``play`` card's
    ``card_index`` is its hand position (== its stable index); ``end_turn`` is
    ``-1``; a potion slot ``s`` is ``-100 - s``.
    """
    kind = action.get("action")
    target = action.get("target_index")
    target = int(target) if target is not None else None
    if kind == "play":
        idx = int(action["card_index"])
        name = "?"
        if 0 <= idx < len(hand):
            name = hand[idx].get("name") or hand[idx].get("id") or "?"
        return idx, name, target
    if kind == "end_turn":
        return _END_TURN, "end turn", None
    if kind in ("potion", "drink_potion"):
        slot = int(action["slot"])
        return _POTION_BASE - slot, f"drink potion slot {slot}", target
    # channel / evoke / choose — not surfaced at the root boundary yet, but
    # keep a deterministic label so a stray entry never crashes the recorder.
    return -999, str(kind), target


# ── combat-state serialization (canonical workbench shape) ────────────────
def _load_card_loc() -> dict[str, str]:
    """Load the card / relic / potion loc map (titles + descriptions).

    Mirrors the workbench server's loader so recorded logs carry the same
    resolved display names the SPA shows (``Strike`` not ``STRIKE_IRONCLAD``).
    """
    from workbench import localization

    loc_dir = _REPO_ROOT / "localization_eng"
    loc = localization.load_card_localization(loc_dir / "cards.json", None)
    for fname in ("relics.json", "potions.json"):
        path = loc_dir / fname
        if path.is_file():
            try:
                loc.update(json.loads(path.read_text(encoding="utf-8")))
            except (OSError, json.JSONDecodeError):
                pass
    return loc


def _combat_state_dict(cs: Any, character: str, loc: dict[str, str]) -> dict[str, Any]:
    """Canonical ``combat_play`` state dict for a native ``cs``.

    Delegates to :func:`workbench.backends.rust_game.build_combat_view` — the
    single source of truth shared with the interactive/replay backend — so a
    recorded run renders identically to a live one (display names, intents in
    the ``{type, damage, hits}`` shape the recompute/search endpoints need) and
    the persisted log is directly readable by passing ``loc``.
    """
    from workbench.backends.rust_game import build_combat_view

    return build_combat_view(cs, character, legal=cs.legal_actions(), loc=loc)


# ── MCTS record builder (shared contract with report/aggregate) ───────────
def _mcts_record(
    result: dict[str, Any], state: dict[str, Any], cfg: Any, decision_idx: int
) -> tuple[dict[str, Any], dict[str, Any]]:
    """Return ``(mcts_record, recommended_action_dict)``.

    The ``mcts`` record uses the exact keys ``workbench.report`` /
    ``workbench.aggregate`` read. The recommended action is the ``is_best``
    outcome (fallback: max visits), returned so the caller can apply it.
    """
    hand = state.get("hand", [])
    outcomes: list[dict[str, Any]] = []
    best_entry: dict[str, Any] | None = None
    most_visited: dict[str, Any] | None = None
    max_visits = -1
    for entry in result.get("actions", []):
        action = entry.get("action", {})
        stable_idx, label, target = _stable_idx_and_label(action, hand)
        visits = int(entry.get("visits", 0))
        is_best = bool(entry.get("is_best", False))
        outcomes.append(
            {
                "stable_idx": stable_idx,
                "action_label": label,
                "target_index": target,
                "visits": visits,
                "q": float(entry.get("q", entry.get("value", 0.0))),
                "wins": int(entry.get("wins", 0)),
                "losses": int(entry.get("losses", 0)),
                "ties": int(entry.get("ties", 0)),
                "is_best": is_best,
            }
        )
        if is_best:
            best_entry = entry
        if visits > max_visits:
            max_visits = visits
            most_visited = entry
    if best_entry is None:
        best_entry = most_visited  # fallback: most-visited root child

    total_visits = sum(o["visits"] for o in outcomes)
    rec_idx = _END_TURN
    rec_label = "end turn"
    rec_action: dict[str, Any] = {"action": "end_turn"}
    if best_entry is not None:
        rec_action = best_entry.get("action", {})
        rec_idx, rec_label, _ = _stable_idx_and_label(rec_action, hand)

    pv = []
    for step in result.get("pv", []) or []:
        _, s_label, _ = _stable_idx_and_label(step, hand)
        pv.append(s_label)

    record = {
        "decision_idx": decision_idx,
        "config": {
            "sims": cfg.sims,
            "c_puct": cfg.resolved_c_puct(),
            "depth": cfg.depth,
            "seed": cfg.seed,
        },
        "total_visits": total_visits,
        "recommended_stable_idx": rec_idx,
        "recommended_label": rec_label,
        "pv": pv,
        "root_outcomes": outcomes,
    }
    return record, rec_action


def _mcts_recommend(cs: Any, cfg: Any) -> dict[str, Any]:
    """Run MCTS on ``cs`` (copy from reasoning.score_native)."""
    import sts2_rs

    return sts2_rs.mcts_recommend(
        cs,
        sims=cfg.sims,
        c_puct=cfg.resolved_c_puct(),
        depth=cfg.depth,
        seed=cfg.seed,
        threads=cfg.threads,
        fair=cfg.fair,
        rollout_policy=cfg.rollout_policy,
        # Forwarded, not defaulted. Dropping these two let the binding's own
        # `hidden_info="clairvoyant"` default through, so every recorded run
        # was searched by a root that could see the draw order — and the
        # regret review then graded honest play against it.
        hidden_info=cfg.hidden_info,
        draw_widening_c=cfg.resolved_draw_widening_c(),
        endturn_widening_c=cfg.endturn_widening_c,
        endturn_widening_alpha=cfg.endturn_widening_alpha,
        dominance_pruning=cfg.dominance_pruning,
    )


# ── fixture provenance ────────────────────────────────────────────────────
def _fixture_provenance(fixture: str) -> dict[str, Any]:
    """Best-effort head-meta from a ``.run-source`` fixture's ``[provenance]``.

    Also surfaces the player ``class`` (e.g. ``Ironclad``) so the recorded log
    carries the cased display character instead of the raw engine ``ironclad``.
    """
    stem = Path(fixture).stem
    prov: dict[str, Any] = {}
    character = "Ironclad"
    try:
        with open(fixture, "rb") as f:
            data = tomllib.load(f)
        prov = data.get("provenance", {}) or {}
        character = (data.get("player", {}) or {}).get("class") or character
    except Exception:
        pass
    return {
        "fixture_stem": stem,
        "build_id": prov.get("build_id"),
        "encounter": prov.get("encounter"),
        "character": character,
    }


# ── PRIMARY: fixture recorder ─────────────────────────────────────────────
def record_fixture(
    fixture: str,
    out_dir: str | None = None,
    sims: int = 3000,
    seed: int = 1,
    max_steps: int = 2000,
) -> str:
    """Drive MCTS over a ``.run-source`` fixture, writing a JSONL run-log.

    Returns the log path. Plays the loaded combat to terminal (or until
    ``max_steps``), logging per-decision ``state`` / ``mcts`` / ``action``
    records and a trailing summary ``meta``.
    """
    import sts2_rs
    from sts2_hint.recommender import RecommendConfig

    cfg = RecommendConfig(sims=sims, depth=200, seed=seed)
    prov = _fixture_provenance(fixture)
    character = prov["character"]
    loc = _load_card_loc()

    logger = GameLogger(
        character=character,
        seed=Path(fixture).stem,
        log_dir=out_dir,
        ascension=0,
    )
    # Absolute fixture path + the FULL MCTS config let the workbench re-search
    # the *true* native state later (reload fixture + replay the logged actions)
    # with the exact same search params. Without the config the re-search falls
    # back to whatever RecommendConfig defaults to at re-search time, which
    # silently picks a different "best" move on an otherwise identical state.
    # The logged `c_puct` is the RESOLVED one (`resolved_c_puct`), never the
    # unset `None`, so the replay reproduces this search rather than
    # re-deriving a constant from a budget the caller may have changed.
    logger.log_meta_extra(
        {
            **prov,
            "sims": sims,
            "fixture_path": str(Path(fixture).resolve()),
            "mcts_seed": seed,
            "mcts_config": {
                "sims": cfg.sims,
                "c_puct": cfg.resolved_c_puct(),
                "depth": cfg.depth,
                "seed": cfg.seed,
                "rollout_policy": cfg.rollout_policy,
            },
        }
    )

    cs = sts2_rs.combat_state_from_scenario_toml(fixture)

    last_state: dict[str, Any] = {}
    decision_idx = 0
    final_value = 0.0

    for _ in range(max_steps):
        is_terminal, value = cs.terminal_value()
        legal = cs.legal_actions()
        if is_terminal or not legal:
            final_value = value
            break

        state = _combat_state_dict(cs, character, loc)
        last_state = state
        logger.log_state(state)

        result = _mcts_recommend(cs, cfg)
        mcts_record, rec_action = _mcts_record(result, state, cfg, decision_idx)
        logger.log_mcts(mcts_record)
        logger.log_action({"action": mcts_record["recommended_label"], "args": rec_action})

        cs.apply(rec_action)
        decision_idx += 1  # noqa: SIM113 — advances only on a logged decision, not per loop iter
    else:
        # Hit max_steps without terminal — record whatever value we have.
        final_value = cs.terminal_value()[1]

    final_state = _combat_state_dict(cs, character, loc)
    pl = final_state.get("player", {})
    logger.log_summary(
        {
            "final_won": bool(final_value > 0),
            "final_turn": final_state.get("round", last_state.get("round", 0)),
            "final_player_hp": pl.get("hp"),
            "final_player_max_hp": pl.get("max_hp"),
        }
    )
    logger.close()
    return logger.path


# ── BEST-EFFORT: full-run recorder ────────────────────────────────────────
def record_run(
    character: str,
    seed: str,
    ascension: int = 0,
    out_dir: str | None = None,
    sims: int = 3000,
    mcts_seed: int = 1,
    max_steps: int = 5000,
) -> str:
    """Drive a full self-play run via ``RustGame``, writing a JSONL run-log.

    Best-effort: the run-sim layer is partial, so this takes MCTS-recommended
    actions inside combat and the first available action in every other phase.
    It stops at game over, when no action advances, or at ``max_steps``.
    """
    from sts2_hint.recommender import RecommendConfig

    from workbench.backends.rust_game import RustGame

    cfg = RecommendConfig(sims=sims, depth=200, seed=mcts_seed)

    logger = GameLogger(
        character=character,
        seed=seed,
        log_dir=out_dir,
        ascension=ascension,
    )
    logger.log_meta_extra(
        {"fixture_stem": f"{character}-{seed}", "build_id": None, "encounter": None, "sims": sims}
    )

    loc = _load_card_loc()
    char_display = character if character[:1].isupper() else character.title()

    game = RustGame()
    state = game.start(character=character, seed=seed, ascension=ascension)

    decision_idx = 0
    won = False
    last_state = state

    for _ in range(max_steps):
        decision = state.get("decision")
        if decision == "game_over":
            won = bool(state.get("victory"))
            break

        cs = game.combat_state()
        if decision == "combat_play" and cs is not None:
            combat_state = _combat_state_dict(cs, char_display, loc)
            logger.log_state(combat_state)
            result = _mcts_recommend(cs, cfg)
            mcts_record, rec_action = _mcts_record(result, combat_state, cfg, decision_idx)
            logger.log_mcts(mcts_record)
            decision_idx += 1
            verb, args = _native_to_run_verb(rec_action)
            logger.log_action({"action": verb, "args": args})
            state = game.act(verb, **args)
        else:
            # Non-combat phase: log the run-state and take a default action.
            logger.log_state(state)
            verb, args = _default_run_action(state, game)
            if verb is None:
                break
            logger.log_action({"action": verb, "args": args})
            state = game.act(verb, **args)

        if isinstance(state, dict) and state.get("type") == "error":
            # Run-sim couldn't advance — stop (best-effort path).
            break
        last_state = state if isinstance(state, dict) else last_state

    pl = (last_state or {}).get("player", {}) if isinstance(last_state, dict) else {}
    logger.log_summary(
        {
            "final_won": won,
            "final_turn": (last_state or {}).get("round", 0) if isinstance(last_state, dict) else 0,
            "final_player_hp": pl.get("hp"),
            "final_player_max_hp": pl.get("max_hp"),
        }
    )
    logger.close()
    return logger.path


def _native_to_run_verb(action: dict[str, Any]) -> tuple[str, dict[str, Any]]:
    """Translate a native combat action dict into a ``RustGame`` verb + args."""
    kind = action.get("action")
    if kind == "play":
        return "play_card", {
            "card_index": action["card_index"],
            "target_index": action.get("target_index"),
        }
    if kind in ("potion", "drink_potion"):
        return "use_potion", {
            "potion_index": action["slot"],
            "target_index": action.get("target_index"),
        }
    return "end_turn", {}


def _default_run_action(state: dict[str, Any], game: Any) -> tuple[str | None, dict[str, Any]]:
    """Pick a default advancing action for a non-combat run phase.

    Best-effort: take the first offered option / card / move. Returns
    ``(None, {})`` when no sensible default exists (caller stops).
    """
    decision = state.get("decision")
    if decision == "map_select":
        choices = state.get("choices") or []
        if choices:
            return "select_map_node", {"col": choices[0]["col"]}
    if decision == "card_reward":
        # Skip card rewards to keep the run moving (deck stays small).
        return "skip_card_reward", {}
    if decision == "bundle_select":
        return "select_bundle", {"bundle_index": 0}
    if decision == "card_select":
        cards = state.get("cards") or []
        if state.get("can_skip"):
            return "skip_select", {}
        if cards:
            return "select_cards", {"indices": str(cards[0]["index"])}
    if decision in ("event_choice", "shop"):
        options = state.get("options") or []
        if decision == "shop":
            return "leave_room", {}
        if options:
            return "choose_option", {"option_index": options[0]["index"]}
    if decision == "rest_site":
        options = state.get("options") or []
        if options:
            return "choose_option", {"option_index": options[0]["index"]}
    # Generic continue.
    return "proceed", {}


# ── CLI ───────────────────────────────────────────────────────────────────
def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Record an MCTS run as JSONL.")
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--fixture", help="path to a .run-source scenario TOML")
    src.add_argument("--run", action="store_true", help="drive a full self-play run")
    ap.add_argument("--character", default="ironclad")
    ap.add_argument("--seed", default="WORKBENCH")
    ap.add_argument("--ascension", type=int, default=0)
    ap.add_argument("--sims", type=int, default=3000)
    ap.add_argument("--mcts-seed", type=int, default=1)
    ap.add_argument("--out", default=str(_REPO_ROOT / "logs"))
    a = ap.parse_args(argv)

    if a.fixture:
        path = record_fixture(a.fixture, out_dir=a.out, sims=a.sims, seed=a.mcts_seed)
    else:
        path = record_run(
            character=a.character,
            seed=a.seed,
            ascension=a.ascension,
            out_dir=a.out,
            sims=a.sims,
            mcts_seed=a.mcts_seed,
        )
    sys.stdout.write(path + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
