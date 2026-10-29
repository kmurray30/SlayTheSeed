#!/usr/bin/env python3
"""W8 — Act-1 corpus replay harness + win-rate report.

Iterates IC v0.103.2 `.run` scraped corpus files, filters to runs that
reached the Act-1 boss, drives the Rust `RunManager` engine through
the scraped out-of-combat decisions floor-by-floor, hands combat to
MCTS, and reports engine win-rate vs scraped human win-rate bucketed
by ascension.

This is the success-bar harness for the R-Act1-Replay umbrella
(`sts2-rs-i6h9i`): engine win-rate within 90-110% of scraped human
win-rate at matched ascension = MCTS is competitive on real human
decks (not starter-only fixtures).

Bead `sts2-rs-cr5xs` (W8, claimed in advance of its W7 blocker for
scaffolding work). The end-to-end driver is gated on:
  - W3 `sts2-rs-if6ry` — Phase::Shop port (drives `shop_actions`)
  - W4 `sts2-rs-h9qn3` — `event_options()` for TextKey → idx (LANDED)
  - W5a `sts2-rs-ze744` — `card_reward_option_ids()` (LANDED)
  - W5b `sts2-rs-5abnx` — Phase::RestSite smith sub-state (LANDED)
  - W6 `sts2-rs-yb3cm` — MCTS handoff (`mcts_pick_action`)
  - W7 `sts2-rs-p0jzo` — per-floor checkpoint helper

Until those land, this script runs as a **corpus-survey scaffold**:
- Iterates the .run corpus, filters to IC v0.103.2 Asc-buck reached-
  boss runs, prints histograms (per-ascension count, win-rate of the
  scraped runs themselves, Act-1 length distribution).
- Emits TODO markers wherever the driver will plug into the engine.
- Exits 0 even if no engine handoff is wired — the survey output is
  the deliverable at this stage.

Usage:
  python3 act1_replay.py --character the-ironclad [--codex] [--limit N]
  python3 act1_replay.py --character the-ironclad --survey-only
"""

from __future__ import annotations

import argparse
import json
import random
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path

# `sts2_rs` is the PyO3 module produced by `crates/sts2-py`. The
# engine driver paths (RunManager construction, take_action, MCTS
# handoff) live here, but are NOT exercised in survey-only mode so
# the scaffold runs even if the wheel is stale.
try:
    import sts2_rs  # type: ignore[import-not-found]

    _RUST_AVAILABLE = True
except ImportError:
    sts2_rs = None  # type: ignore[assignment]
    _RUST_AVAILABLE = False

from run_files import codex_root, glob_runs, load_run

# ─── Corpus discovery ───────────────────────────────────────────────────────


# Vendored, clone-portable corpus (sts2-rs/data/run-corpus/, git-lfs).
PRIMARY_ROOT = Path(__file__).resolve().parents[2] / "sts2-rs/data/run-corpus/files"
CHAR_ALIASES = {
    "the-ironclad": ["the-ironclad", "ironclad"],
    "the-silent": ["the-silent", "silent"],
    "the-defect": ["the-defect", "defect"],
    "the-necrobinder": ["the-necrobinder", "necrobinder"],
    "the-regent": ["the-regent", "regent"],
}
CHARACTER_RUST_NAME = {"the-ironclad": "Ironclad"}


@dataclass
class Act1Run:
    """A v0.103.2 single-player run that reached the Act-1 boss row
    (won or lost in the boss fight, OR survived to Act-2 entry)."""

    seed: str
    ascension: int
    scraped_won: bool  # True iff the overall scraped run was a victory
    reached_boss: bool  # True iff the run entered the Act-1 boss room
    killed_in_act1: bool  # True iff the run terminated during Act-1
    act1_floors: list  # `map_point_history[0]` — list of floor dicts
    source_path: Path


def iter_v103_act1_runs(character_dir: str, use_codex: bool):
    """Yield [`Act1Run`] for each IC v0.103.2 single-player run that
    progressed at least to the Act-1 boss row (`map_point_type=boss`
    in `map_point_history[0]`).

    Pattern mirrors `r3_neow_parity.iter_v103_neow_runs`: scan `.run`
    files under `data/files/<char>` (+ optional `files-spire-codex`),
    filter by `build_id`/`players`/`modifiers`, parse Act-1.
    """
    roots = [PRIMARY_ROOT] + ([codex_root()] if use_codex else [])
    aliases = CHAR_ALIASES.get(character_dir, [character_dir])
    files: list[Path] = []
    for root in roots:
        for alias in aliases:
            files.extend(glob_runs(root / alias))
    for f in files:
        try:
            d = load_run(f)
        except (OSError, json.JSONDecodeError):
            continue
        if d.get("build_id") != "v0.103.2":
            continue
        if d.get("modifiers"):
            continue
        if len(d.get("players", [])) != 1:
            continue
        mph = d.get("map_point_history", [])
        if not mph or not mph[0]:
            continue
        act1 = mph[0]
        types = [fl.get("map_point_type") for fl in act1]
        # The boss row entry is the last floor in Act-1's slice. We
        # require the boss type to appear (the player at least entered
        # the boss room — won, lost, or recorded the boss intent).
        if "boss" not in types:
            continue
        killed_act = d.get("killed_by_act")
        if killed_act is None:
            # Some dumps record the kill act via `killed_by_encounter`
            # location; fall back to "act 2/3 visited?" heuristic.
            killed_act = 1 if len(mph) < 2 or not mph[1] else 999
        yield Act1Run(
            seed=d["seed"],
            ascension=d.get("ascension", 0),
            scraped_won=bool(d.get("win", False)),
            reached_boss=True,
            killed_in_act1=(killed_act == 1),
            act1_floors=act1,
            source_path=f,
        )


# ─── Per-floor decision extraction (W5/W7 scaffolding) ──────────────────────


@dataclass
class FloorDecision:
    """One floor's out-of-combat decisions, extracted from a scraped
    `map_point_history[0][i]` entry. The driver dispatches these
    against the engine after MCTS resolves the combat (if any)."""

    row: int  # 0..=16 in Act-1 (entrance=0, boss=16)
    map_point_type: str
    rooms: list  # scraped `rooms[*]` (model_id + monster_ids + ...)
    card_choices: list  # scraped `player_stats[0].card_choices`
    cards_gained: list  # scraped `player_stats[0].cards_gained`
    cards_removed: list  # may be None
    upgraded_cards: list  # rest-site smith picks
    relic_choices: list  # treasure / event / boss relic picks
    event_choices: list  # ancient event option picks
    rest_site_choices: list  # ["SMITH" | "HEAL"]
    shop_actions: list  # ordered transaction list
    gold_after: int | None  # post-floor gold balance (None when unrecorded)
    hp_after: int | None  # post-floor HP (None when unrecorded)


def extract_floor_decision(floor_dict: dict, row: int) -> FloorDecision:
    """Coerce a scraped floor dict into the typed driver input shape."""
    ps = (floor_dict.get("player_stats") or [{}])[0]
    return FloorDecision(
        row=row,
        map_point_type=floor_dict.get("map_point_type", "unknown"),
        rooms=floor_dict.get("rooms", []),
        card_choices=ps.get("card_choices") or [],
        cards_gained=ps.get("cards_gained") or [],
        cards_removed=ps.get("cards_removed") or [],
        upgraded_cards=ps.get("upgraded_cards") or [],
        relic_choices=ps.get("relic_choices") or [],
        event_choices=ps.get("event_choices") or ps.get("ancient_choice") or [],
        rest_site_choices=ps.get("rest_site_choices") or [],
        shop_actions=ps.get("shop_actions") or [],
        gold_after=ps.get("gold_after"),
        hp_after=ps.get("hp_after"),
    )


# ─── Engine driver (gated on W3/W6 — currently a TODO) ──────────────────────


@dataclass
class ReplayOutcome:
    """One run's replay result for win-rate reporting."""

    seed: str
    ascension: int
    scraped_won: bool
    # `engine_act1_won` is `None` when the driver stub couldn't reach
    # the boss (e.g. W3 shop path NotImplemented, MCTS PyO3 absent).
    engine_act1_won: bool | None = None
    structural_divergences: list = field(default_factory=list)


def _translate_mcts_action(mcts_action: dict) -> dict | None:
    """Convert an MCTS-returned `CombatAction` dict into the
    `RunAction` dict shape that `RunManager.take_action()` accepts.

    Returns `None` for combat-internal actions the RunManager doesn't
    surface (potions, pending-choice resolutions, Defect channel/evoke)
    — caller should fall back to `available_actions()[0]`.
    """
    kind = mcts_action.get("action")
    if kind == "play":
        return {
            "action": "play_card",
            "hand_idx": mcts_action["card_index"],
            "target_index": mcts_action.get("target_index"),
        }
    if kind == "end_turn":
        return {"action": "end_turn"}
    return None


def _infer_act1_path(act_map: list, scraped_types: list[str]) -> list[int] | None:
    """Given the engine's Act-1 `act_map` (list of node dicts) and the
    scraped visited type sequence (length 16 — rows 1..16 of Act-1,
    Neow at index 0 already stripped by the caller), return the list of
    column indices the engine should `move` to in order.

    Picks the lexicographically-first path through the map whose
    per-row types match `scraped_types`. Returns `None` if no path
    matches — caller records a divergence and falls back to random
    column picks.
    """
    if not act_map:
        return None
    by_coord = {(p["col"], p["row"]): p for p in act_map}
    starts = sorted((p for p in act_map if p["row"] == 1), key=lambda p: p["col"])

    def walk(node: dict, depth: int) -> list[int] | None:
        # `depth` indexes into `scraped_types`; node's type should match
        # scraped_types[depth].
        if node["point_type"] != scraped_types[depth]:
            return None
        if depth == len(scraped_types) - 1:
            return [node["col"]]
        children = sorted(
            (by_coord[c, r] for (c, r) in node.get("children", []) if (c, r) in by_coord),
            key=lambda p: p["col"],
        )
        for child in children:
            tail = walk(child, depth + 1)
            if tail is not None:
                return [node["col"], *tail]
        return None

    for start in starts:
        path = walk(start, 0)
        if path is not None:
            return path
    return None


def _scraped_card_ids(decision: FloorDecision) -> list[str]:
    """Card IDs the human picked at this card-reward floor (excluding skips)."""
    picked = []
    for entry in decision.card_choices:
        if entry.get("was_picked") and entry.get("card", {}).get("id"):
            picked.append(entry["card"]["id"])
    return picked


def _norm_card(cid: str) -> str:
    """Canonical key for matching scraped ``CARD.X`` ids against the engine
    Debug enum names returned by ``master_deck_detailed`` — strip the
    ``CARD.`` prefix / ``_CARD`` suffix, drop non-alphanumerics, lowercase.
    ``"CARD.STRIKE_IRONCLAD"`` and ``"StrikeIronclad"`` both → ``"strikeironclad"``."""
    s = cid
    if s.startswith("CARD."):
        s = s[5:]
    s = "".join(ch for ch in s if ch.isalnum()).lower()
    # Engine Debug names carry a `Card` suffix the scraped ids omit
    # (`ColossusCard` vs `CARD.COLOSSUS`); the scraped `_CARD` suffix
    # collapses to the same `…card` tail. Strip it so both sides agree.
    if s.endswith("card") and len(s) > 4:
        s = s[:-4]
    return s


def _reconcile_deck_to_human(rm, starter_ids: list, decisions: list, divergences: list) -> int:
    """Force the engine ``master_deck`` to equal the human's cumulative
    pre-boss deck and apply the same upgrades, by **diffing** the target
    multiset (starter + Σ ``cards_gained`` − Σ ``cards_removed``, with
    Σ ``upgraded_cards`` upgrades) against the engine's current deck and
    issuing ``force_add_card`` / ``force_remove_card`` / ``force_upgrade_card``.

    Diff-based ⇒ **idempotent**: it corrects whatever the per-phase
    handlers did or missed — unreplayed card removals (no removal API
    existed before), dropped shop buys / event-granted cards, and
    wrong-option deck pollution (event idx-0 adding a card the human never
    took). ``decisions`` must be the floors strictly BEFORE the boss (the
    boss-floor reward is post-fight). Returns the number of mutations made.

    MUST run before the engine enters the boss room — the boss combat
    snapshots its draw pile from ``master_deck`` at room entry, so a later
    reconcile would not reach the ongoing fight.
    """
    from collections import Counter

    # ── Target: starter + scraped deltas (normalized keys). ──
    target = Counter(_norm_card(c) for c in starter_ids)
    upg_target: Counter = Counter()
    rep_id: dict = {}  # norm key -> a concrete id usable by force_add_card
    # Seed with starter cards (engine Debug names, e.g. "StrikeIronclad")
    # so the reconcile can re-add a base card the run lost — card_id_from_str
    # accepts the Debug form via its case/underscore-insensitive fallback.
    for s in starter_ids:
        rep_id.setdefault(_norm_card(s), s)
    for dec in decisions:
        for g in dec.cards_gained or []:
            cid = g.get("id")
            if not cid:
                continue
            k = _norm_card(cid)
            target[k] += 1
            rep_id[k] = cid
        for rmv in dec.cards_removed or []:
            cid = rmv.get("id")
            if cid:
                target[_norm_card(cid)] -= 1
        for u in dec.upgraded_cards or []:
            if u:
                upg_target[_norm_card(u)] += 1

    # ── Current engine deck. ──
    detailed = rm.master_deck_detailed()  # [(DebugName, upgraded_bool), ...]
    current = Counter(_norm_card(name) for name, _up in detailed)
    dbg_id: dict = {}  # norm key -> engine Debug name (usable by force_remove)
    for name, _up in detailed:
        dbg_id.setdefault(_norm_card(name), name)

    changed = 0
    # 1) Count diff — add missing, remove extra.
    for k in set(target) | set(current):
        delta = target.get(k, 0) - current.get(k, 0)
        if delta > 0:
            add_id = rep_id.get(k) or dbg_id.get(k)
            if add_id is None:
                divergences.append(f"reconcile_add_no_id:{k}")
                continue
            for _ in range(delta):
                try:
                    rm.force_add_card(add_id)
                    changed += 1
                except Exception as e:
                    divergences.append(f"reconcile_add_failed:{add_id}:{e}")
                    break
        elif delta < 0:
            rid = dbg_id.get(k) or rep_id.get(k)
            for _ in range(-delta):
                try:
                    if rm.force_remove_card(rid):
                        changed += 1
                    else:
                        divergences.append(f"reconcile_remove_miss:{rid}")
                        break
                except Exception as e:
                    divergences.append(f"reconcile_remove_failed:{rid}:{e}")
                    break

    # 2) Upgrade diff — re-read deck so freshly-added cards are upgradable.
    detailed = rm.master_deck_detailed()
    upg_current = Counter(_norm_card(name) for name, up in detailed if up)
    id_for: dict = {}
    for name, _up in detailed:
        id_for.setdefault(_norm_card(name), name)
    for k, want in upg_target.items():
        for _ in range(max(0, want - upg_current.get(k, 0))):
            uid = id_for.get(k) or rep_id.get(k)
            if uid is None:
                divergences.append(f"reconcile_upg_no_id:{k}")
                break
            try:
                if rm.force_upgrade_card(uid):
                    changed += 1
                else:
                    divergences.append(f"reconcile_upgrade_miss:{uid}")
                    break
            except Exception as e:
                divergences.append(f"reconcile_upgrade_failed:{uid}:{e}")
                break

    if changed:
        divergences.append(f"deck_reconciled:{changed}")
    return changed


def _engine_card_reward_pick(
    rm, decision: FloorDecision, divergences: list, rng: random.Random
) -> dict:
    """Pick a card-reward action matching the scraped pick. Falls back
    to skip when the engine's offered cards don't intersect the human's
    pick (silent divergence — recorded for visibility)."""
    # NOTE: `card_reward_options()` returns the *action* list (a thin
    # wrapper over PickCardReward enumeration); the card-id surface
    # we want is `card_reward_option_ids()` (W5a, sts2-rs-ze744).
    try:
        offered = rm.card_reward_option_ids() or []
    except Exception:
        offered = []
    scraped_picks = _scraped_card_ids(decision)
    if not scraped_picks:
        # Human skipped → engine skips.
        return {"action": "pick_card_reward", "idx": None}
    target = scraped_picks[0]
    for i, off in enumerate(offered):
        # `off` is the C#-style id string (`CARD.STRIKE`) per the
        # `card_reward_options()` PyO3 surface.
        if off == target:
            return {"action": "pick_card_reward", "idx": i}
    # RNG mismatch (card-reward sampler diverges from C# pin; see
    # bead `sts2-rs-i6h9i.1.1`): skip the engine's reward AND inject
    # the human's actual pick via `force_add_card` so the deck still
    # matches at boss entry. Mutates `master_deck` without touching
    # phase state, so the subsequent skip drains the reward queue
    # cleanly.
    divergences.append(f"card_reward_rng_drift:{target}")
    try:
        rm.force_add_card(target)
    except Exception as e:
        divergences.append(f"force_add_card_failed:{target}:{e}")
    return {"action": "pick_card_reward", "idx": None}


def _engine_event_choice(
    rm, decision: FloorDecision, divergences: list, rng: random.Random
) -> dict:
    """Pick the first event option. The engine surfaces options by
    index; the scraped JSON gives a TextKey but we don't yet have a
    TextKey→idx mapping wired through PyO3 — record a divergence and
    take idx=0 (matches what most events offer first)."""
    actions = rm.available_actions()
    if decision.event_choices:
        divergences.append("event_choice_unmapped_textkey")
    return actions[0]


def _engine_rest_action(rm, decision: FloorDecision, divergences: list, rng: random.Random) -> dict:
    """Take the scraped rest-site choice (SMITH vs HEAL). The engine
    enumerates `rest_option` actions in the order returned by
    `rest_site_options()` — which at the current pin is
    `[Rest, Smith{enabled?}]`, so idx=0=Rest(Heal) and idx=1=Smith."""
    actions = rm.available_actions()
    # In the smith sub-state, `available_actions()` is a list of
    # `smith_card {idx}` entries — but we don't have a "what card sits
    # at smith idx N" accessor, AND the engine's smith candidate set
    # may not include the human's target (single-card auto-resolve
    # paths, RNG drift, etc.). Take idx=0 to progress the engine, and
    # force-upgrade the human's actual smith target via
    # `force_upgrade_card` so the deck still matches. Recorded as a
    # divergence for visibility.
    if actions and actions[0].get("action") == "smith_card":
        wanted = (decision.upgraded_cards or [None])[0]
        if wanted:
            try:
                ok = rm.force_upgrade_card(wanted)
                if not ok:
                    divergences.append(f"force_upgrade_card_no_match:{wanted}")
                else:
                    divergences.append(f"smith_card_force_upgraded:{wanted}")
            except Exception as e:
                divergences.append(f"force_upgrade_card_failed:{wanted}:{e}")
        return actions[0]
    choice = (decision.rest_site_choices or ["HEAL"])[0]
    target_idx = 1 if choice == "SMITH" else 0
    # If the human SMITHed but the engine's rest options don't expose
    # a smith path (single-card auto-resolve, or rest_site_options
    # collapses to [Rest] under some modifier path), pick the first
    # valid rest_option and force-upgrade so the deck still matches.
    if choice == "SMITH":
        wanted = (decision.upgraded_cards or [None])[0]
        if wanted:
            try:
                rm.force_upgrade_card(wanted)
            except Exception:
                pass
    for a in actions:
        if a.get("action") == "rest_option" and a.get("idx") == target_idx:
            return a
    # Fallback: first available.
    return actions[0]


def _engine_shop_action(rm, decision: FloorDecision, divergences: list, rng: random.Random) -> dict:
    """Replay scraped shop transactions. The engine surfaces
    `shop_buy_card/relic/potion` + `shop_remove_card` + `leave_shop`;
    we attempt to match `cards_gained` / `relic_choices[was_picked]` /
    `potion_choices[was_picked]` from the scraped floor against
    available shop options by id.

    Conservative: when none of the scraped purchases are still
    affordable in the engine's shop, leave. The engine's shop pricing
    will differ in HP-pricing edge cases (Membership/Courier relics
    not modeled), so unmatched-but-still-affordable items get a
    divergence note rather than silent skip."""
    actions = rm.available_actions()
    # Build the set of (kind, id) the human bought.
    wanted: set[tuple[str, str]] = set()
    for g in decision.cards_gained or []:
        if g.get("id"):
            wanted.add(("card", g["id"]))
    for r in decision.relic_choices or []:
        if r.get("was_picked") and r.get("choice"):
            wanted.add(("relic", r["choice"]))
    for p in decision.shop_actions or []:
        # potion_choices is only on the spec/extra side; not exposed
        # under FloorDecision yet — skip for now.
        _ = p

    # Without an "id at this shop_buy_* idx" accessor on the engine
    # side, we can't deterministically pick which shop_buy idx
    # corresponds to which scraped purchase. Best we can do today:
    # if scraped bought ANY card and engine has any shop_buy_card,
    # take the first; same for relic/potion.
    bought_any_card = any(k == "card" for k, _ in wanted)
    bought_any_relic = any(k == "relic" for k, _ in wanted)
    for a in actions:
        if bought_any_card and a.get("action") == "shop_buy_card":
            if wanted:
                divergences.append(f"shop_buy_card_idx_unresolved:{wanted}")
            return a
        if bought_any_relic and a.get("action") == "shop_buy_relic":
            divergences.append(f"shop_buy_relic_idx_unresolved:{wanted}")
            return a
    # Nothing matched → leave.
    return {"action": "leave_shop"}


def _scraped_relics_for_floor(decision: FloorDecision) -> list[str]:
    """Relic ids the human acquired at this floor (ancient/event/elite/
    treasure/shop). Combines `relic_choices[was_picked]` (the common
    case) with `ancient_choice[was_chosen]` (Neow events) so the
    dispatcher catches both shapes."""
    out: list[str] = []
    for r in decision.relic_choices or []:
        if r.get("was_picked") and r.get("choice"):
            out.append(r["choice"])
    # `ancient_choice` is a separate scraped key — extract_floor_decision
    # folds it into `event_choices` already, but its native shape uses
    # `was_chosen` and `TextKey` rather than `was_picked`/`choice`.
    # Inspect the raw rooms payload to recover Neow relic acquisitions.
    for entry in decision.event_choices or []:
        # `ancient_choice` shape: {TextKey, title, was_chosen}
        if entry.get("was_chosen") and entry.get("TextKey"):
            out.append(f"RELIC.{entry['TextKey']}")
    return out


def _inject_scraped_relics(
    rm, decision: FloorDecision, divergences: list, already_owned: set
) -> None:
    """Push scraped relic acquisitions for this floor into the engine
    via `force_add_relic`. Skips relics the engine already owns
    (idempotent). Records `relic_force_add_failed:` for parse failures
    (typically a relic id the engine doesn't have a `RelicId` enum
    entry for — e.g. NEOWS_BONES at the current pin)."""
    for rid in _scraped_relics_for_floor(decision):
        if rid in already_owned:
            continue
        try:
            added = rm.force_add_relic(rid)
            if added:
                already_owned.add(rid)
                divergences.append(f"relic_force_added:{rid}")
            else:
                # Already in engine deck (e.g. Burning Blood starter).
                already_owned.add(rid)
        except Exception as e:
            divergences.append(f"relic_force_add_failed:{rid}:{e}")


def _engine_map_move(
    rm, path_cols: list[int] | None, depth: int, divergences: list, rng: random.Random
) -> dict:
    """Pick the move-action whose `col` matches the inferred path at
    this depth. Falls back to a random valid move when path inference
    failed or this depth is out of range."""
    actions = rm.available_actions()
    if path_cols is not None and 0 <= depth < len(path_cols):
        want_col = path_cols[depth]
        for a in actions:
            if a.get("action") == "move" and a.get("col") == want_col:
                return a
        divergences.append(f"map_move_col_unmatched:{want_col}@depth{depth}")
    return rng.choice(actions)


def replay_run(
    run: Act1Run,
    *,
    mcts_sims_normal=1000,
    mcts_sims_boss=3000,
    max_steps=10000,
    policy_seed: int | None = None,
    trace=None,
    leaf_eval: str | None = None,
    threads: int = 1,
) -> ReplayOutcome:
    """Drive the Rust engine through `run`'s Act-1 floor sequence.

    W8.3 wiring (sts2-rs-i6h9i.1): MCTS in `Phase::Combat`, scraped-
    decision dispatch in every other phase, falling back to random or
    skip on unmapped sub-cases (each recorded under
    `structural_divergences`). Stops when `is_over()` or the engine
    transitions past Act-1 (`act_index > 1`).
    """
    out = ReplayOutcome(seed=run.seed, ascension=run.ascension, scraped_won=run.scraped_won)
    if not _RUST_AVAILABLE:
        out.structural_divergences.append("sts2_rs wheel not installed")
        return out

    try:
        rm = sts2_rs.RunManager(  # type: ignore[union-attr]
            string_seed=run.seed,
            character="Ironclad",
            ascension_level=run.ascension,
        )
    except Exception as e:  # NotImplementedError on non-IC, etc.
        out.structural_divergences.append(f"run_manager_init_failed: {e}")
        return out

    # Precompute the inferred col-by-col path from the scraped type
    # sequence (floors 1..16 — Neow at scraped index 0 is pre-applied
    # by the engine constructor and not surfaced as a map_choice).
    scraped_types = [fl.get("map_point_type", "?") for fl in run.act1_floors[1:]]
    try:
        path_cols = _infer_act1_path(rm.act_map() or [], scraped_types)
    except Exception as e:
        path_cols = None
        out.structural_divergences.append(f"path_inference_failed: {e}")
    if path_cols is None:
        out.structural_divergences.append("path_inference_no_match")

    # Per-floor decisions, indexed by visited-floor (1..16, skipping Neow).
    floor_decisions = [
        extract_floor_decision(fl, row=i) for i, fl in enumerate(run.act1_floors[1:], start=1)
    ]
    # Walk the engine; `move_depth` tracks how many map moves we've
    # made so far (= which scraped floor we're entering). The driver's
    # "current floor" is therefore `floor_decisions[move_depth - 1]`
    # AFTER a move, or the next-to-enter `floor_decisions[move_depth]`
    # at the moment of a map_choice.
    rng = random.Random(policy_seed if policy_seed is not None else hash(run.seed) & 0xFFFFFFFF)
    move_depth = 0
    steps = 0
    act1_resolved = False
    relics_owned: set = set(rm.relics() or [])
    # Clean starter deck (10 cards Asc-0, +AscendersBane Asc≥10) captured
    # BEFORE any Neow/reward injection — the baseline the pre-boss deck
    # reconcile diffs the human's cumulative deltas against.
    starter_deck: list = list(rm.master_deck() or [])
    neow_dec = None
    # Inject Neow-floor (scraped index 0) relics + cards immediately —
    # the engine pre-applies its own Neow during RunManager construction
    # but doesn't surface a card_reward / event phase for it, so the
    # human's actual Neow picks have to be force-injected up front.
    if run.act1_floors:
        neow_dec = extract_floor_decision(run.act1_floors[0], row=0)
        _inject_scraped_relics(rm, neow_dec, out.structural_divergences, relics_owned)
        for c in neow_dec.cards_gained or []:
            cid = c.get("id")
            if cid:
                try:
                    rm.force_add_card(cid)
                    out.structural_divergences.append(f"neow_card_force_added:{cid}")
                except Exception as e:
                    out.structural_divergences.append(f"neow_card_force_add_failed:{cid}:{e}")
    while steps < max_steps:
        if rm.is_over():
            break
        if rm.act_index > 1:
            act1_resolved = True
            break
        actions = rm.available_actions()
        if not actions:
            out.structural_divergences.append(f"no_actions_phase={rm.phase()}")
            break
        phase = rm.phase()
        if trace is not None:
            trace(rm, phase, move_depth)
        try:
            if phase == "combat":
                cs = rm.combat_state()
                # Boss row = the last visited floor. We index off
                # `move_depth` (== floors entered so far).
                is_boss = move_depth == len(floor_decisions)
                sims = mcts_sims_boss if is_boss else mcts_sims_normal
                mcts = sts2_rs.Mcts(  # type: ignore[union-attr]
                    cs,
                    iters=sims,
                    seed=rng.randint(0, (1 << 31) - 1),
                    leaf_eval=leaf_eval,
                    threads=threads,
                )
                res = mcts.search()
                translated = _translate_mcts_action(res["action"])
                if translated is None:
                    # MCTS picked a potion/choose/channel/evoke — engine
                    # doesn't surface those via RunAction. Fall back to
                    # the engine's enumerated options (PlayCard/EndTurn
                    # only).
                    out.structural_divergences.append(
                        f"mcts_action_unmapped:{res['action'].get('action')}"
                    )
                    translated = actions[0]
                rm.take_action(translated)
            elif phase == "map_choice":
                act = _engine_map_move(rm, path_cols, move_depth, out.structural_divergences, rng)
                # Reconcile the engine deck to the human's cumulative deck
                # BEFORE every move — the next combat snapshots its draw
                # pile from master_deck at room entry, so a later reconcile
                # wouldn't reach the fight. This closes the shop/event/
                # removal/upgrade gaps AND neutralises the per-floor
                # starter-card bleed (an unscripted engine-side divergence,
                # see sts2-rs-i6h9i deck-bleed note) so the MCTS-vs-human
                # comparison is a FAIR deck match at every fight, not just
                # the boss. `move_depth` floors have been entered and fully
                # rewarded by now, so the target is neow + floors[:move_depth].
                cumulative = ([neow_dec] if neow_dec is not None else []) + floor_decisions[
                    :move_depth
                ]
                _reconcile_deck_to_human(rm, starter_deck, cumulative, out.structural_divergences)
                rm.take_action(act)
                move_depth += 1
                # We just entered floor `move_depth` (1-indexed into
                # scraped path; floor_decisions is 0-indexed for those
                # floors). Inject any scraped relic acquisitions for
                # this floor BEFORE phase dispatch, so the after_obtained
                # listeners (Whetstone upgrades / ScrollBoxes gold-zero
                # / etc.) fire with the right state context.
                dec_idx = move_depth - 1
                if 0 <= dec_idx < len(floor_decisions):
                    _inject_scraped_relics(
                        rm,
                        floor_decisions[dec_idx],
                        out.structural_divergences,
                        relics_owned,
                    )
            elif phase == "card_reward":
                # Card-reward floor's decisions live at the floor we
                # just entered = floor_decisions[move_depth - 1].
                idx = max(0, move_depth - 1)
                dec = floor_decisions[idx] if idx < len(floor_decisions) else None
                if dec is None:
                    rm.take_action(rng.choice(actions))
                else:
                    # First action in card_reward may be card OR relic;
                    # check the shape.
                    if actions and actions[0].get("action") in (
                        "pick_relic_reward",
                        "skip_relic",
                    ):
                        # Relic reward at this floor — scraped picks?
                        picked_relic = any(r.get("was_picked") for r in (dec.relic_choices or []))
                        rm.take_action(
                            {"action": "pick_relic_reward" if picked_relic else "skip_relic"}
                        )
                    else:
                        rm.take_action(
                            _engine_card_reward_pick(rm, dec, out.structural_divergences, rng)
                        )
            elif phase == "event":
                idx = max(0, move_depth - 1)
                dec = floor_decisions[idx] if idx < len(floor_decisions) else None
                rm.take_action(
                    _engine_event_choice(rm, dec, out.structural_divergences, rng)
                    if dec
                    else rng.choice(actions)
                )
            elif phase == "rest_site":
                idx = max(0, move_depth - 1)
                dec = floor_decisions[idx] if idx < len(floor_decisions) else None
                rm.take_action(
                    _engine_rest_action(rm, dec, out.structural_divergences, rng)
                    if dec
                    else rng.choice(actions)
                )
            elif phase == "shop":
                idx = max(0, move_depth - 1)
                dec = floor_decisions[idx] if idx < len(floor_decisions) else None
                rm.take_action(
                    _engine_shop_action(rm, dec, out.structural_divergences, rng)
                    if dec
                    else {"action": "leave_shop"}
                )
            elif phase == "treasure":
                rm.take_action({"action": "collect_treasure"})
            elif phase == "boss_relic":
                # Take the first option (boss relics aren't in scraped
                # data in a directly-mappable shape and humans usually
                # take w/e seems good — record divergence).
                out.structural_divergences.append("boss_relic_uncurated_pick")
                rm.take_action(actions[0])
            else:
                # Unknown phase — fall back to first action and note it.
                out.structural_divergences.append(f"unhandled_phase:{phase}")
                rm.take_action(actions[0])
        except Exception as e:
            out.structural_divergences.append(f"take_action_failed_phase={phase}: {e}")
            break
        steps += 1
    else:
        out.structural_divergences.append(f"max_steps_exceeded={max_steps}")

    if act1_resolved:
        out.engine_act1_won = True
    elif rm.is_over():
        # Died in Act-1 (or won the entire run, but you can't win the
        # whole run while still in act_index=1 — so this is a death).
        out.engine_act1_won = bool(rm.player_won)
    # else: broke out via divergence → engine_act1_won stays None.
    return out


# ─── Reporting ──────────────────────────────────────────────────────────────


def report_winrate(outcomes: list[ReplayOutcome]) -> None:
    """Print the per-ascension `K_engine_won / K_scraped_won / N` table
    that the W8 bead's success bar is measured against."""
    by_asc: dict[int, list[ReplayOutcome]] = defaultdict(list)
    for o in outcomes:
        by_asc[o.ascension].append(o)
    print()
    print(f"{'Asc':>5} {'engine_won':>11} {'scraped_won':>12} {'N':>6}  ratio")
    print("-" * 56)
    for asc in sorted(by_asc):
        runs = by_asc[asc]
        n = len(runs)
        scraped = sum(1 for r in runs if r.scraped_won)
        engine = sum(1 for r in runs if r.engine_act1_won is True)
        engine_resolved = sum(1 for r in runs if r.engine_act1_won is not None)
        ratio_str = f"{engine / scraped:.0%}" if scraped > 0 and engine_resolved > 0 else "—"
        print(f"{asc:>5} {engine:>11} {scraped:>12} {n:>6}  {ratio_str}")
    unresolved = sum(1 for o in outcomes if o.engine_act1_won is None)
    if unresolved:
        print(
            f"\nNOTE: {unresolved}/{len(outcomes)} runs have engine_won=None — "
            "see ReplayOutcome.structural_divergences for per-run cause."
        )
        # Aggregate divergence histogram so the failure modes are visible.
        causes = Counter()
        for o in outcomes:
            for d in o.structural_divergences:
                # Strip the trailing exception text for bucketing.
                key = d.split(":", 1)[0]
                causes[key] += 1
        if causes:
            print("Divergence histogram:", dict(causes.most_common()))


def report_survey(runs: list[Act1Run]) -> None:
    """Survey-mode output — what we already know about the corpus
    without driving the engine. Useful as a pre-W6 sanity check."""
    by_asc = Counter(r.ascension for r in runs)
    by_asc_won = Counter(r.ascension for r in runs if r.scraped_won)
    print(f"\n{'Asc':>5} {'reached_boss':>13} {'scraped_won':>12}  human_winrate")
    print("-" * 56)
    for asc in sorted(by_asc):
        n = by_asc[asc]
        w = by_asc_won[asc]
        wr = f"{w / n:.0%}" if n else "—"
        print(f"{asc:>5} {n:>13} {w:>12}  {wr}")
    # Distribution of Act-1 length — should hover at 17 (entrance + 15
    # rows + boss) but odd values surface broken / pre-boss-killed runs.
    lengths = Counter(len(r.act1_floors) for r in runs)
    print(f"\nAct-1 floor-count distribution: {dict(sorted(lengths.items()))}")
    # Top room-type histogram across Act-1.
    types = Counter()
    for r in runs:
        types.update(fl.get("map_point_type") for fl in r.act1_floors)
    print(f"Act-1 room-type histogram: {dict(sorted(types.items()))}")


# ─── Entry point ────────────────────────────────────────────────────────────


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=(__doc__ or "").splitlines()[0] if __doc__ else None)
    p.add_argument("--character", default="the-ironclad", choices=sorted(CHAR_ALIASES.keys()))
    p.add_argument("--codex", action="store_true", help="Include codex corpus root.")
    p.add_argument("--limit", type=int, default=0, help="Cap runs (0 = all).")
    p.add_argument(
        "--won-only",
        action="store_true",
        help="Restrict corpus to runs the scraped human won (cleanest "
        "MCTS-vs-human combat-skill comparison: engine_won/N close to 100%% "
        "iff MCTS plays winnable decks at least as well as the human).",
    )
    p.add_argument(
        "--ascension",
        type=int,
        default=None,
        help="Restrict to a single ascension level (e.g. 0). Default: all.",
    )
    p.add_argument(
        "--survey-only",
        action="store_true",
        help="Skip engine driver; report corpus-survey output only (pre-W6 mode).",
    )
    p.add_argument(
        "--mcts-sims-normal",
        type=int,
        default=1000,
        help="MCTS budget for Act-1 normal/elite floors (per memory project_mcts_budget_knee).",
    )
    p.add_argument(
        "--mcts-sims-boss", type=int, default=3000, help="MCTS budget for the Act-1 boss."
    )
    p.add_argument(
        "--leaf-eval",
        choices=["texel", "texel-wide"],
        default=None,
        help=(
            "MCTS leaf evaluator: 'texel' (old narrow 29-feature) or "
            "'texel-wide' (new wide L2 champion). Default None = engine "
            "default (texel-wide). For the leaf A/B win-rate comparison."
        ),
    )
    p.add_argument(
        "--threads",
        type=int,
        default=1,
        help=(
            "MCTS root-parallel workers per move. >1 splits the sim budget "
            "across workers (same total sims, faster wall-clock) via "
            "par_search_with_policy. Default 1 = single-threaded."
        ),
    )
    args = p.parse_args(argv)

    if args.character != "the-ironclad":
        print(
            f"act1_replay: only Ironclad has wired ContentCallbacks; "
            f"{args.character!r} support is a follow-up (per crates/sts2-py/src/run_manager.rs).",
            file=sys.stderr,
        )
        return 2

    runs: list[Act1Run] = []
    for r in iter_v103_act1_runs(args.character, args.codex):
        if args.won_only and not r.scraped_won:
            continue
        if args.ascension is not None and r.ascension != args.ascension:
            continue
        runs.append(r)
        if args.limit and len(runs) >= args.limit:
            break

    if not runs:
        print("act1_replay: no v0.103.2 IC runs reached the Act-1 boss row.", file=sys.stderr)
        return 1

    print(f"act1_replay: corpus = {len(runs)} reached-boss v0.103.2 IC runs.")

    if args.survey_only or not _RUST_AVAILABLE:
        if not _RUST_AVAILABLE:
            print("act1_replay: sts2_rs wheel not importable — survey mode forced.")
        report_survey(runs)
        return 0

    outcomes = [
        replay_run(
            r,
            mcts_sims_normal=args.mcts_sims_normal,
            mcts_sims_boss=args.mcts_sims_boss,
            leaf_eval=args.leaf_eval,
            threads=args.threads,
        )
        for r in runs
    ]
    report_survey(runs)
    report_winrate(outcomes)
    return 0


if __name__ == "__main__":
    sys.exit(main())
