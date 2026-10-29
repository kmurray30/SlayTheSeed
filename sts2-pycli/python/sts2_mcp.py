"""MCP server: lets Claude (or any MCP client) play Slay the Spire 2.

Exposes the C# headless runner as a set of typed MCP tools. One ``Game``
process per server instance — single game at a time. The shared
``sts2_client.Game`` class does process management; ``sts2_format``
renders compact text summaries.

Run as a stdio MCP server:

    uv run --frozen --dev python python/sts2_mcp.py

Or wire it into ``.mcp.json``:

    {
      "mcpServers": {
        "sts2": {
          "command": "uv",
          "args": ["run", "--frozen", "--dev", "--project",
                   "<repo>", "python", "python/sts2_mcp.py"]
        }
      }
    }
"""

from __future__ import annotations

import datetime
import os
from collections.abc import Callable
from functools import wraps
from pathlib import Path
from typing import Any, TextIO

from mcp.server.fastmcp import FastMCP
from sts2_client import Game, sanitize_json
from sts2_combat import recommend as _recommend_combat
from sts2_format import (
    format_boss_summary,
    format_card,
    format_decision,
    format_deck,
    format_map,
    format_pile,
    format_potion_detail,
    format_potions_list,
    format_relic_detail,
    format_relics_list,
    format_state_diff,
)

mcp = FastMCP("sts2")

_game: Game | Any = None
_last_state: dict[str, Any] | None = None

# Per-run caches for auto-briefing. Reset on start_run so a new run gets a
# clean slate. ``_seen_enemies`` / ``_seen_relics`` are the names we've
# already auto-looked-up this run; ``_last_floor`` lets us detect floor
# changes for the contextual tool reminder.
_seen_enemies: set[str] = set()
_seen_relics: set[str] = set()
_last_floor: int | None = None

# Per-floor save checkpoints for easy reproduce/undo of mid-run bugs. We snapshot
# whenever the agent lands on a ``map_select`` decision (the safe between-rooms
# moment that ``write_continue_save`` requires anyway). Disable with
# ``STS2_MCP_AUTOSAVE=0``. Loading any checkpoint via ``load_save`` resumes
# deterministically from that floor's map.
_last_saved_floor: int | None = None
_AUTOSAVE_DIR: Path | None = None

# Tail-friendly logs: one block per MCP tool call. Two parallel files —
# `mcp_*.log` is human-readable (great for `tail -f`); `mcp_*.jsonl` carries
# the full game state for programmatic viewers (e.g. python/mcp_viewer.py).
# Disable with STS2_MCP_LOG=0.
_LOG_FH: TextIO | None = None
_LOG_PATH: Path | None = None
_JSONL_FH: TextIO | None = None
_JSONL_PATH: Path | None = None


def _log_path() -> Path | None:
    global _LOG_FH, _LOG_PATH, _JSONL_FH, _JSONL_PATH
    if os.environ.get("STS2_MCP_LOG") == "0":
        return None
    if _LOG_FH is not None:
        return _LOG_PATH
    logs_dir = Path(__file__).resolve().parent.parent / "logs"
    logs_dir.mkdir(exist_ok=True)
    ts = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    _LOG_PATH = logs_dir / f"mcp_{ts}_{os.getpid()}.log"
    _JSONL_PATH = logs_dir / f"mcp_{ts}_{os.getpid()}.jsonl"
    _LOG_FH = _LOG_PATH.open("w", buffering=1, encoding="utf-8")
    _JSONL_FH = _JSONL_PATH.open("w", buffering=1, encoding="utf-8")
    _LOG_FH.write(
        f"# STS2 MCP server log — started {datetime.datetime.now().isoformat(timespec='seconds')}\n"
    )
    return _LOG_PATH


def _format_args(args: tuple, kwargs: dict) -> str:
    parts = [repr(a) for a in args]
    parts += [f"{k}={v!r}" for k, v in kwargs.items()]
    return ", ".join(parts)


def _log_call(action: str, args: tuple, kwargs: dict, result: Any, prev_state: Any = None) -> None:
    import json as _json

    _log_path()
    if _LOG_FH is None:
        return
    ts = datetime.datetime.now().isoformat(timespec="seconds")
    args_str = _format_args(args, kwargs)
    body: str
    if isinstance(result, dict):
        if "summary" in result:
            body = str(result["summary"])
        elif "text" in result:
            body = str(result["text"])
        elif "error" in result:
            body = f"error: {result['error']}"
        elif "status" in result:
            body = f"status: {result['status']}"
        else:
            body = str(result)
    else:
        body = str(result)
    _LOG_FH.write(f"\n{'=' * 70}\n[{ts}] {action}({args_str})\n{'=' * 70}\n{body}\n")
    if _JSONL_FH is not None:
        try:
            entry = {
                "ts": ts,
                "action": action,
                "args_str": args_str,
                "prev_state": prev_state,
                "state": _last_state,
            }
            _JSONL_FH.write(_json.dumps(entry, ensure_ascii=False, default=str) + "\n")
        except Exception:
            pass


def _logged(fn: Callable[..., Any]) -> Callable[..., Any]:
    """Decorator: log each MCP tool call to the tailable text log."""

    @wraps(fn)
    def wrapper(*args: Any, **kwargs: Any) -> Any:
        prev = _last_state  # snapshot before action mutates it
        result = fn(*args, **kwargs)
        # Prepend combat diff to the summary so agents/users see what happened.
        if isinstance(result, dict) and "summary" in result and prev is not None:
            try:
                diff = format_state_diff(prev, _last_state, fn.__name__, args, kwargs)
                if diff:
                    result["summary"] = diff + "\n" + result["summary"]
            except Exception:
                pass
        # Append auto-briefings (new-enemy lookup, floor-change tool tips) so
        # the agent doesn't need a separate tool call to see the move pattern
        # of an unfamiliar enemy or remember which tools fit this room.
        if isinstance(result, dict) and "summary" in result:
            try:
                addons = _briefings(prev, _last_state)
                if addons:
                    result["summary"] = result["summary"] + "\n" + addons
            except Exception:
                pass
        try:
            _log_call(fn.__name__, args, kwargs, result, prev_state=prev)
        except Exception:  # never let logging break gameplay
            pass
        return result

    return wrapper


def _ensure_game() -> Game | Any:
    """Lazily construct the game backend.

    Defaults to the headless C# process (byte-identical to prior behavior).
    Set ``STS2_BACKEND=real`` to instead attach to a live Slay the Spire 2
    process via the SpireMentor mod (real-game bridge phase 2, see
    ``python/sts2_realgame.py``) -- every MCP tool below works unchanged
    because :class:`~sts2_realgame.RealGame` implements the same decision
    schema and action verbs as :class:`~sts2_client.Game`. Configure the
    connection with ``STS2_REALGAME_HOST``/``STS2_REALGAME_PORT``
    (default ``127.0.0.1``/``9002``) and the default run with
    ``STS2_REALGAME_CHARACTER``/``STS2_REALGAME_ASCENSION``/
    ``STS2_REALGAME_SEED`` (all optional; ``start_run``'s own args still
    override them per call).
    """
    global _game
    if _game is None:
        if os.environ.get("STS2_BACKEND", "").strip().lower() == "real":
            from sts2_realgame import RealGame

            _game = RealGame(
                host=os.environ.get("STS2_REALGAME_HOST", "127.0.0.1"),
                port=int(os.environ.get("STS2_REALGAME_PORT", "9002")),
                character=os.environ.get("STS2_REALGAME_CHARACTER", "Ironclad"),
                ascension=int(os.environ.get("STS2_REALGAME_ASCENSION", "0")),
                seed=os.environ.get("STS2_REALGAME_SEED"),
            )
        else:
            _game = Game(forward_stderr=False)
    return _game


def _maybe_autosave(state: dict[str, Any]) -> None:
    """Write a per-floor save checkpoint when we land on the floor's map.

    ``map_select`` is the canonical between-rooms decision and the only point
    where ``write_continue_save`` serialises the *current* floor (mid-room calls
    roll back to the prior map state). Saving here gives us one snapshot per
    floor, named ``floor_NN.json``, that ``load_save`` can resume from to
    reproduce mid-combat bugs against the same RNG.
    """
    global _last_saved_floor, _AUTOSAVE_DIR
    if os.environ.get("STS2_MCP_AUTOSAVE") == "0":
        return
    if state.get("decision") != "map_select":
        return
    floor = (state.get("context") or {}).get("floor")
    if not isinstance(floor, int) or floor == _last_saved_floor:
        return
    if _game is None:
        return
    if _AUTOSAVE_DIR is None:
        log_path = _log_path()
        if log_path is None:
            return
        _AUTOSAVE_DIR = log_path.parent / f"saves_{log_path.stem}"
        _AUTOSAVE_DIR.mkdir(exist_ok=True)
    save_path = _AUTOSAVE_DIR / f"floor_{floor:02d}.json"
    try:
        _game.write_continue_save(str(save_path))
        _last_saved_floor = floor
    except Exception:  # save failures must never break gameplay
        pass


def _wrap(state: dict[str, Any], *, verbose: bool = False, cache: bool = True) -> dict[str, Any]:
    """Common envelope: ``{decision, summary, state}``.

    ``summary`` is always present — a terse text rendering of the decision (or a
    short status string for error/ok responses). ``state`` is the raw JSON
    (post-sanitize) for clients that need exact field access; it is omitted by
    default and only included when ``verbose=True``. Verbose explicitly means
    "give me everything structured" — we do *not* run ``compact_json`` here,
    because that would strip ``player.hp``/``player.block`` on ``combat_play``
    decisions and silently break the ``recommend_combat`` heuristic.

    Pass ``cache=False`` for read-only callers (``get_state``) that re-wrap the
    cached state and must not overwrite ``_last_state`` with a re-sanitized copy.
    """
    global _last_state
    state = sanitize_json(state)
    if cache:
        _last_state = state
        _maybe_autosave(state)
    decision = state.get("decision") or state.get("type")
    out: dict[str, Any] = {"decision": decision}
    state_type = state.get("type")
    if state_type in ("decision", "map"):
        out["summary"] = format_decision(state)
    elif state_type == "error":
        out["summary"] = f"error: {state.get('message', 'unknown error')}"
        out["error"] = state.get("message", "unknown error")
    else:
        out["summary"] = str(decision or state_type or "")
    if verbose:
        out["state"] = state
    return out


# ──────────────────────────────────────────────────────────────────────────────
# Lifecycle
# ──────────────────────────────────────────────────────────────────────────────


@mcp.tool()
@_logged
def start_run(
    character: str = "Ironclad",
    seed: str = "test",
    ascension: int = 0,
    lang: str = "en",
    verbose: bool = False,
) -> dict[str, Any]:
    """Start a new STS2 run.

    If a run is already in progress, that game subprocess is closed first so
    we don't end up sending ``start_run`` to an in-flight C# process. Returns
    the first decision (typically the Neow event_choice).

    Args:
        character: One of "Ironclad", "Silent", "Defect", "Regent", "Necrobinder".
        seed: Run seed string. Same seed reproduces the same run.
        ascension: Ascension level (0-20).
        lang: "en" or "zh". Also sets the language for formatter labels.
        verbose: If True, include the structured ``state`` JSON. Default: text summary only.
    """
    global _game, _last_state, _last_floor, _last_saved_floor, _AUTOSAVE_DIR
    if _game is not None:
        try:
            _game.close()
        finally:
            _game = None
            _last_state = None
    _seen_enemies.clear()
    _seen_relics.clear()
    _last_floor = None
    _last_saved_floor = None
    _AUTOSAVE_DIR = None
    return _wrap(_ensure_game().start(character, seed, ascension, lang), verbose=verbose)


@mcp.tool()
@_logged
def quit_run() -> dict[str, str]:
    """Terminate the current run and the C# game process. Idempotent."""
    global _game, _last_state, _last_floor, _last_saved_floor, _AUTOSAVE_DIR
    if _game is not None:
        _game.close()
        _game = None
        _last_state = None
    _seen_enemies.clear()
    _seen_relics.clear()
    _last_floor = None
    _last_saved_floor = None
    _AUTOSAVE_DIR = None
    return {"status": "closed"}


@mcp.tool()
@_logged
def get_state(verbose: bool = False) -> dict[str, Any]:
    """Return the current decision state without taking an action.

    Useful for re-reading a long state (e.g. after losing context to compaction).
    """
    if _last_state is None:
        return {"error": "no run in progress; call start_run first"}
    # cache=False: don't recompact-and-overwrite the cache from a read-only call.
    return _wrap(_last_state, verbose=verbose, cache=False)


@mcp.tool()
@_logged
def load_save(
    path: str | None = None,
    save_json: str | None = None,
    verbose: bool = False,
) -> dict[str, Any]:
    """Load a native save file by path or by inline JSON string."""
    return _wrap(_ensure_game().load_save(path=path, json_str=save_json), verbose=verbose)


@mcp.tool()
@_logged
def write_continue_save(path: str) -> dict[str, Any]:
    """Write a native save checkpoint to ``path`` for later ``load_save``.

    The game can only serialize between rooms: if called mid-room the save is
    rolled back to the pre-room map state (combat progress in the current room
    is discarded). Pair with ``load_save`` for between-room undo.
    """
    return _ensure_game().write_continue_save(path)


# ──────────────────────────────────────────────────────────────────────────────
# Game actions — one tool per verb in sts2_client.ACTIONS.
# ──────────────────────────────────────────────────────────────────────────────


@mcp.tool()
@_logged
def play_card(
    card_index: int,
    target_index: int | None = None,
    verbose: bool = False,
) -> dict[str, Any]:
    """Play a card from your hand.

    Args:
        card_index: Index of the card in your hand (see ``hand[].index``).
        target_index: Required for cards with ``target_type == "AnyEnemy"``.
        verbose: If True, include the structured ``state`` JSON. Default: text summary only.
    """
    return _wrap(_ensure_game().play_card(card_index, target_index), verbose=verbose)


@mcp.tool()
@_logged
def end_turn(verbose: bool = False) -> dict[str, Any]:
    """End the current combat turn."""
    return _wrap(_ensure_game().end_turn(), verbose=verbose)


@mcp.tool()
@_logged
def use_potion(
    potion_index: int,
    target_index: int | None = None,
    verbose: bool = False,
) -> dict[str, Any]:
    """Use a potion from your potion belt.

    Args:
        potion_index: Index of the potion (see ``player.potions[].index``).
        target_index: Required for potions targeting a specific enemy.
        verbose: If True, include the structured ``state`` JSON. Default: text summary only.
    """
    return _wrap(_ensure_game().use_potion(potion_index, target_index), verbose=verbose)


@mcp.tool()
@_logged
def discard_potion(potion_index: int, verbose: bool = False) -> dict[str, Any]:
    """Discard a potion to free up a belt slot."""
    return _wrap(_ensure_game().discard_potion(potion_index), verbose=verbose)


@mcp.tool()
@_logged
def select_map_node(col: int, row: int, verbose: bool = False) -> dict[str, Any]:
    """Move to a map node by its (col, row) coordinates.

    The valid choices for the current floor are listed in
    ``state["choices"]`` of a ``map_select`` decision.
    """
    return _wrap(_ensure_game().select_map_node(col, row), verbose=verbose)


@mcp.tool()
@_logged
def select_card_reward(card_index: int, verbose: bool = False) -> dict[str, Any]:
    """Pick a card from a card-reward screen by its index."""
    return _wrap(_ensure_game().select_card_reward(card_index), verbose=verbose)


@mcp.tool()
@_logged
def skip_card_reward(verbose: bool = False) -> dict[str, Any]:
    """Skip the current card reward."""
    return _wrap(_ensure_game().skip_card_reward(), verbose=verbose)


@mcp.tool()
@_logged
def buy_card(card_index: int, verbose: bool = False) -> dict[str, Any]:
    """Buy a card from the shop."""
    return _wrap(_ensure_game().buy_card(card_index), verbose=verbose)


@mcp.tool()
@_logged
def buy_relic(relic_index: int, verbose: bool = False) -> dict[str, Any]:
    """Buy a relic from the shop."""
    return _wrap(_ensure_game().buy_relic(relic_index), verbose=verbose)


@mcp.tool()
@_logged
def buy_potion(potion_index: int, verbose: bool = False) -> dict[str, Any]:
    """Buy a potion from the shop."""
    return _wrap(_ensure_game().buy_potion(potion_index), verbose=verbose)


@mcp.tool()
@_logged
def remove_card(verbose: bool = False) -> dict[str, Any]:
    """Use the rest-site card removal option (selects which card next)."""
    return _wrap(_ensure_game().remove_card(), verbose=verbose)


@mcp.tool()
@_logged
def select_bundle(bundle_index: int, verbose: bool = False) -> dict[str, Any]:
    """Pick a card bundle (Neow bundle screens, etc.)."""
    return _wrap(_ensure_game().select_bundle(bundle_index), verbose=verbose)


@mcp.tool()
@_logged
def select_cards(indices: str, verbose: bool = False) -> dict[str, Any]:
    """Select one or more cards at a card_select prompt.

    Args:
        indices: Comma-separated card indices, e.g. "0" or "0,2,3".
    """
    return _wrap(_ensure_game().select_cards(indices), verbose=verbose)


@mcp.tool()
@_logged
def skip_select(verbose: bool = False) -> dict[str, Any]:
    """Skip a card_select prompt (only allowed when ``min_select == 0``)."""
    return _wrap(_ensure_game().skip_select(), verbose=verbose)


@mcp.tool()
@_logged
def choose_option(option_index: int, verbose: bool = False) -> dict[str, Any]:
    """Choose an option at an event_choice or rest_site prompt."""
    return _wrap(_ensure_game().choose_option(option_index), verbose=verbose)


@mcp.tool()
@_logged
def leave_room(verbose: bool = False) -> dict[str, Any]:
    """Leave the current room (e.g. exit a shop)."""
    return _wrap(_ensure_game().leave_room(), verbose=verbose)


@mcp.tool()
@_logged
def proceed(verbose: bool = False) -> dict[str, Any]:
    """Generic 'continue' — used after rewards, treasure pickups, etc."""
    return _wrap(_ensure_game().proceed(), verbose=verbose)


# ──────────────────────────────────────────────────────────────────────────────
# Wiki lookup — wraps sts2-wiki/tools/lookup/lookup.py for static data queries
# (enemies, cards, encounters) that don't need an active run.
# ──────────────────────────────────────────────────────────────────────────────


_lookup_mod: Any | None = None


def _resolve_wiki_dir() -> Path:
    """Return the sts2-wiki repo path. Honours ``$STS2_WIKI_DIR``; default = sibling."""
    override = os.environ.get("STS2_WIKI_DIR")
    if override:
        return Path(override).expanduser().resolve()
    return Path(__file__).resolve().parent.parent.parent / "sts2-wiki"


def _get_lookup() -> Any:
    """Lazy-load the wiki ``lookup`` module from disk. Raises if not found."""
    global _lookup_mod
    if _lookup_mod is not None:
        return _lookup_mod
    src = _resolve_wiki_dir() / "tools" / "lookup" / "lookup.py"
    if not src.is_file():
        raise FileNotFoundError(
            f"sts2-wiki lookup module not found at {src}. Set STS2_WIKI_DIR to the wiki repo root."
        )
    import importlib.util

    spec = importlib.util.spec_from_file_location("sts2_wiki_lookup", src)
    if spec is None or spec.loader is None:
        raise ImportError(f"could not build module spec for {src}")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    _lookup_mod = mod
    return mod


def _enemy_briefing(enemies: list[dict[str, Any]]) -> str:
    """Look up move patterns for any enemies not yet seen this run.

    Returns a multi-line block with full wiki info for each new name, or
    ``""`` if every enemy has already been auto-looked-up. Saves the agent
    from having to remember to call ``lookup`` on combat start.
    """
    new_names: list[str] = []
    for e in enemies:
        name = (e or {}).get("name") or ""
        if name and name not in _seen_enemies:
            new_names.append(name)
            _seen_enemies.add(name)
    if not new_names:
        return ""
    try:
        lk = _get_lookup()
    except (FileNotFoundError, ImportError):
        return ""
    parts: list[str] = []
    for name in new_names:
        try:
            matches = lk.find_files(name, "monsters", False)
            if not matches:
                continue
            data = lk.load_json("monsters", matches[0])
            if data:
                parts.append(lk.format_enemy(data).strip())
        except Exception:
            continue
    if not parts:
        return ""
    return "📖 New enemies (auto-looked-up):\n" + "\n\n".join(parts)


def _format_relic_entry(data: dict[str, Any]) -> str:
    """Render a wiki ``relics/<class>.json`` blob as a compact text block."""
    if not data:
        return ""
    title = data.get("title") or data.get("class_name") or "?"
    rarity = data.get("rarity") or ""
    desc = (data.get("description") or "").strip()
    notes = (data.get("notes") or "").strip()
    head = f"\n{title}"
    if rarity:
        head += f"  {rarity}"
    lines = [head]
    if desc:
        lines.append(f"  {desc}")
    if notes and notes != desc:
        lines.append(f"\n  Notes: {notes}")
    return "\n".join(lines)


def _format_potion_entry(data: dict[str, Any]) -> str:
    """Render a wiki ``potions/<class>.json`` blob as a compact text block."""
    if not data:
        return ""
    title = data.get("title") or data.get("class_name") or "?"
    rarity = data.get("rarity") or ""
    target = data.get("target") or ""
    usage = data.get("usage") or ""
    desc = (data.get("description") or "").strip()
    notes = (data.get("notes") or "").strip()
    head = f"\n{title}"
    if rarity:
        head += f"  {rarity}"
    if usage:
        head += f"  [{usage}]"
    if target:
        head += f"  →{target}"
    lines = [head]
    if desc:
        lines.append(f"  {desc}")
    if notes and notes != desc:
        lines.append(f"\n  Notes: {notes}")
    return "\n".join(lines)


# Order matters: shown to the agent in this order when a query hits multiple
# categories. ``monsters`` first because that's the most common cross-domain
# collision (Byrdpip relic vs monster — agent usually wants the monster pattern
# top-of-mind). ``encounters`` last because it's structurally redundant with
# the monster entry it shares a name with.
_LOOKUP_CATEGORIES: tuple[tuple[str, str, str], ...] = (
    ("monster", "monsters", "Enemy"),
    ("card", "cards", "Card"),
    ("relic", "relics", "Relic"),
    ("potion", "potions", "Potion"),
    ("encounter", "encounters", "Encounter"),
)


def _lookup_format(category: str, data: dict[str, Any]) -> str:
    """Dispatch to the right wiki formatter for ``category``."""
    lk = _get_lookup()
    if category == "monsters":
        return lk.format_enemy(data).strip()
    if category == "cards":
        return lk.format_card(data).strip()
    if category == "encounters":
        return lk.format_encounter(data).strip()
    if category == "relics":
        return _format_relic_entry(data).strip()
    if category == "potions":
        return _format_potion_entry(data).strip()
    return ""


def _relic_briefing(state: dict[str, Any]) -> str:
    """Auto-look-up relic descriptions for offered relics in events/shops/Neow.

    Walks every option-like slot in the current decision (``options``,
    ``relics``, ``bundles``), pulls the title strings, and pulls full wiki
    text for any relic name we haven't briefed yet this run. Most Neow
    options ship with a ``null`` description, so this is the only way the
    agent gets to read what the relic actually does without an extra call.
    """
    decision = state.get("decision") or state.get("type")
    if decision not in ("event_choice", "bundle_select", "shop"):
        return ""
    candidates: list[str] = []
    for slot in ("options", "relics", "bundles"):
        for item in state.get(slot) or []:
            name = (item or {}).get("title") or (item or {}).get("name") or ""
            if isinstance(name, str) and name:
                candidates.append(name)
    if not candidates:
        return ""
    try:
        lk = _get_lookup()
    except (FileNotFoundError, ImportError):
        return ""
    parts: list[str] = []
    for raw in candidates:
        # Title-case key for dedup so "WINGED BOOTS" and "Winged Boots" hit
        # the same set entry.
        key = raw.strip().lower()
        if not key or key in _seen_relics:
            continue
        try:
            matches = lk.find_files(raw, "relics", False)
            if not matches:
                continue
            data = lk.load_json("relics", matches[0])
            text = _format_relic_entry(data).strip()
            if text:
                parts.append(text)
                _seen_relics.add(key)
        except Exception:
            continue
    if not parts:
        return ""
    return "📖 Offered relics (auto-looked-up):\n" + "\n\n".join(parts)


# Per-decision tool reminders. Kept short — one line apiece — so they nag the
# agent toward the right tool without bloating every response.
_TOOL_TIPS: dict[str, str] = {
    "combat_play": "💡 Combat tools: recommend_combat (heuristic), show_pile draw|discard|exhaust, describe_card",
    "shop": "💡 Shop tools: lookup on offered cards / relics / potions before buying",
    "card_reward": "💡 Reward tools: lookup on unfamiliar cards (upgraded text matters)",
    "map_select": "💡 Routing tools: describe_boss, lookup on elite/monster pools",
    "rest_site": "💡 Rest tools: show_deck before upgrading; remove_card carefully",
    "event_choice": "💡 Event tools: lookup on offered cards / relics before choosing",
}


def _floor_of(state: dict[str, Any] | None) -> int | None:
    if not state:
        return None
    ctx = state.get("context") or {}
    f = ctx.get("floor")
    return f if isinstance(f, int) else None


def _briefings(prev: dict[str, Any] | None, curr: dict[str, Any] | None) -> str:
    """Compose auto-briefing add-ons: new-enemy lookups + floor-change tool tip.

    Mutates the module-level ``_seen_enemies`` and ``_last_floor`` caches so
    each enemy is looked up at most once per run and the tool tip fires only
    when the floor actually changes.
    """
    global _last_floor
    if not isinstance(curr, dict):
        return ""

    parts: list[str] = []

    enemies = curr.get("enemies") or []
    if enemies:
        briefing = _enemy_briefing(enemies)
        if briefing:
            parts.append(briefing)

    relic_brief = _relic_briefing(curr)
    if relic_brief:
        parts.append(relic_brief)

    curr_floor = _floor_of(curr)
    if curr_floor is not None and curr_floor != _last_floor:
        _last_floor = curr_floor
        decision = curr.get("decision") or curr.get("type")
        tip = _TOOL_TIPS.get(decision or "")
        if tip is not None:
            parts.append(tip)

    return "\n".join(parts)


def _boss_to_wiki_class(boss_id: str) -> str:
    """Map a game boss id (e.g. ``THE_KIN_BOSS``) to a wiki monster class (``KinPriest``)."""
    key = boss_id[:-5] if boss_id.endswith("_BOSS") else boss_id
    if key == "THE_KIN":
        key = "KIN_PRIEST"
    return "".join(w.capitalize() for w in key.split("_") if w)


@mcp.tool()
@_logged
def lookup(
    query: str,
    fuzzy: bool = False,
    category: str | None = None,
) -> dict[str, str]:
    """Look up any wiki entry — cards, relics, potions, enemies, encounters.

    Searches every category by class name, human title, or substring and
    returns all matches grouped by section header (``[Card]``, ``[Relic]``,
    ``[Enemy]``, ``[Potion]``, ``[Encounter]``). Works without an active
    run.

    Cross-category collisions are rare (only ``Byrdpip`` and ``PaelsLegion``
    share names between relics and monsters, plus the structural
    monster↔encounter pairing). Pass ``category`` to disambiguate when
    needed.

    Args:
        query: Class name (``"Vantom"``, ``"WingedBoots"``), human title
            (``"Leaf Slime"``, ``"Winged Boots"``), or substring.
            Case-insensitive.
        fuzzy: Fall back to Levenshtein matching if no exact/substring hit.
        category: Optional filter — one of ``"card"``, ``"relic"``,
            ``"potion"``, ``"monster"``, ``"encounter"``. Default searches
            all categories.
    """
    try:
        lk = _get_lookup()
    except (FileNotFoundError, ImportError) as e:
        return {"error": str(e)}

    cats = _LOOKUP_CATEGORIES
    if category is not None:
        cats = tuple(c for c in _LOOKUP_CATEGORIES if c[0] == category)
        if not cats:
            valid = ", ".join(c[0] for c in _LOOKUP_CATEGORIES)
            return {"error": f"unknown category {category!r}; expected one of: {valid}"}

    blocks: list[str] = []
    for _, subdir, label in cats:
        try:
            names = lk.find_files(query, subdir, fuzzy)
        except Exception:
            continue
        for name in names:
            data = lk.load_json(subdir, name)
            text = _lookup_format(subdir, data)
            if text:
                blocks.append(f"[{label}] {text}")
    if not blocks:
        scope = category or "any category"
        return {"error": f"no entry matching {query!r} in {scope}"}
    return {"text": "\n\n".join(blocks)}


@mcp.tool()
@_logged
def lookup_list(category: str, query: str | None = None) -> dict[str, str]:
    """List every entry in a wiki category, optionally filtered by class-name substring.

    Args:
        category: One of ``"card"``, ``"relic"``, ``"potion"``, ``"monster"``,
            ``"encounter"``.
        query: Optional case-insensitive substring filter on the class name.
    """
    subdir_map = {c[0]: c[1] for c in _LOOKUP_CATEGORIES}
    if category not in subdir_map:
        return {"error": f"category must be one of {sorted(subdir_map)}"}
    try:
        lk = _get_lookup()
    except (FileNotFoundError, ImportError) as e:
        return {"error": str(e)}
    names = lk.list_json_files(subdir_map[category])
    if query:
        q = query.lower()
        names = [n for n in names if q in n.lower()]
    return {"text": f"{len(names)} items\n" + "\n".join(names)}


# ──────────────────────────────────────────────────────────────────────────────
# Inspection — counterparts to play.py's `relic`, `potion`, `boss`, `deck`,
# `map` meta-commands. Read-only against the cached last state or via get_map.
# ──────────────────────────────────────────────────────────────────────────────


@mcp.tool()
@_logged
def describe_card(query: str) -> dict[str, str]:
    """Show full details for a card in your hand by index or name substring."""
    if _last_state is None:
        return {"error": "no run in progress"}
    hand = _last_state.get("hand") or _last_state.get("cards") or []
    if not hand:
        return {"error": "no card list in current state"}
    if query.isdigit():
        idx = int(query)
        match = next((c for c in hand if c.get("index") == idx), None)
    else:
        q = query.lower()
        match = next((c for c in hand if q in (c.get("name") or "").lower()), None)
    if not match:
        return {"error": f"no card matching {query!r}"}
    return {"text": format_card(match).strip()}


@mcp.tool()
@_logged
def describe_relic(query: str) -> dict[str, str]:
    """Show full details for one of your relics, identified by index or name substring."""
    if _last_state is None:
        return {"error": "no run in progress"}
    player = _last_state.get("player") or {}
    return {"text": format_relic_detail(player, query)}


@mcp.tool()
@_logged
def describe_potion(query: str) -> dict[str, str]:
    """Show full details for one of your potions."""
    if _last_state is None:
        return {"error": "no run in progress"}
    player = _last_state.get("player") or {}
    return {"text": format_potion_detail(player, query)}


@mcp.tool()
@_logged
def describe_boss() -> dict[str, str]:
    """Show the current act boss with its full move pattern from the wiki.

    Pulls the boss id from ``state.context.boss`` (falling back to ``get_map``),
    renders the bilingual known-moves summary, and appends the wiki's full
    move table + pattern via the wiki.
    """
    if _last_state is None:
        return {"error": "no run in progress"}
    state = _last_state
    text = format_boss_summary(state)
    if "No boss" in text:
        if _game is None:
            return {"error": "no run in progress"}
        try:
            state = _game.get_map()
            text = format_boss_summary(state)
        except Exception as e:
            return {"error": f"could not fetch map: {e}"}

    boss = (state.get("context") or {}).get("boss") or state.get("boss") or {}
    bid = boss.get("id") or ""
    if bid:
        try:
            lk = _get_lookup()
            class_name = _boss_to_wiki_class(bid)
            matches = lk.find_files(class_name, "monsters", False)
            if matches:
                data = lk.load_json("monsters", matches[0])
                if data:
                    text = f"{text}\n\n{lk.format_enemy(data).strip()}"
        except (FileNotFoundError, ImportError):
            pass
    return {"text": text}


@mcp.tool()
@_logged
def show_deck() -> dict[str, str]:
    """Show the player's full deck."""
    if _last_state is None:
        return {"error": "no run in progress"}
    return {"text": format_deck(_last_state)}


@mcp.tool()
@_logged
def show_map() -> dict[str, str]:
    """Show the full map (rows of nodes, current position, boss)."""
    if _game is None:
        return {"error": "no run in progress"}
    try:
        map_data = _game.get_map()
    except Exception as e:
        return {"error": f"could not fetch map: {e}"}
    return {"text": format_map(map_data)}


@mcp.tool()
@_logged
def show_relics() -> dict[str, str]:
    """List every owned relic with its resolved description.

    Counterpart to play.py's ``relics`` meta-command. Use ``describe_relic``
    for a single relic.
    """
    if _last_state is None:
        return {"error": "no run in progress"}
    player = _last_state.get("player") or {}
    return {"text": format_relics_list(player)}


@mcp.tool()
@_logged
def show_potions() -> dict[str, str]:
    """List every owned potion with its resolved description.

    Counterpart to play.py's ``potions`` meta-command. Use ``describe_potion``
    for a single potion.
    """
    if _last_state is None:
        return {"error": "no run in progress"}
    player = _last_state.get("player") or {}
    return {"text": format_potions_list(player)}


@mcp.tool()
@_logged
def show_pile(kind: str) -> dict[str, str]:
    """Show a combat pile.

    Args:
        kind: One of ``"draw"``, ``"discard"``, ``"exhaust"``. Only meaningful
            inside a ``combat_play`` decision; outside combat the pile will
            simply be empty.
    """
    if _last_state is None:
        return {"error": "no run in progress"}
    return {"text": format_pile(_last_state, kind)}


@mcp.tool()
@_logged
def recommend_combat() -> dict[str, Any]:
    """Recommend the next combat action from the cached state.

    Reads from the server's last-seen state — no need to pass JSON. Returns
    ``{action, args, reasoning, summary}`` where ``action`` is one of
    ``play_card`` / ``use_potion`` / ``end_turn`` and ``args`` are the
    kwargs you'd pass to that tool.

    Heuristic only — sanity-check for routine turns, not a substitute for
    careful play against bosses, elites, or unknown move patterns.
    """
    if _last_state is None:
        return {"error": "no run in progress"}
    decision = _last_state.get("decision") or _last_state.get("type")
    if decision != "combat_play":
        return {"error": f"not in combat (decision={decision!r})"}
    return _recommend_combat(_last_state)


def _describe_action_for_stable_idx(stable_idx: int, state: dict[str, Any]) -> str:
    """Render a short human-readable description of an action keyed by
    its hint ``stable_idx``.

    Mirrors the sentinel table in :mod:`sts2_hint.recommender`:

    * ``-1``           → ``"End turn"``
    * ``-100 - slot``  → ``"Drink potion <name>"`` (slot ∈ [0, 4])
    * ``-200``         → ``"Channel"``
    * ``-201``         → ``"Evoke"``
    * ``-300 - i``     → ``"Choose option <i>"``
    * ``>= 0``         → matched against ``hand[*].index`` to recover
      the card name; falls back to ``"Card #<idx>"`` when no match.

    Kept resilient — anything unexpected returns ``f"action({stable_idx})"``
    rather than raising, so a single odd state can't break the whole
    response.
    """
    # End turn
    if stable_idx == -1:
        return "End turn"
    # Potion: -100 - slot
    if stable_idx <= -100 and stable_idx > -200:
        slot = -100 - stable_idx
        potions = (state.get("player") or {}).get("potions") or state.get("potions") or []
        if 0 <= slot < len(potions):
            p = potions[slot]
            pname = p.get("name") if isinstance(p, dict) else str(p)
            if isinstance(pname, dict):
                pname = pname.get("en") or pname.get("zh") or "?"
            return f"Drink potion {pname}"
        return f"Drink potion slot={slot}"
    if stable_idx == -200:
        return "Channel"
    if stable_idx == -201:
        return "Evoke"
    if stable_idx <= -300:
        return f"Choose option {-300 - stable_idx}"
    # Card play. The hint adapter uses C# ``card.index`` as stable_idx.
    hand = state.get("hand") or []
    for c in hand:
        if c.get("index") == stable_idx:
            name = c.get("name") or ""
            if isinstance(name, dict):
                name = name.get("en") or name.get("zh") or ""
            target_type = c.get("target_type") or ""
            return f"Play {name}" + (f" (target={target_type})" if target_type else "")
    return f"Card #{stable_idx}"


@mcp.tool()
@_logged
def recommend_combat_mcts(
    sims: int = 300,
    fair: bool = False,
    depth: int = 10,
    c_puct: float = 1.4,
    seed: int | None = None,
    threads: int = 1,
    cheat_draw_order: bool = False,
    timeout_ms: int = 5000,
) -> dict[str, Any]:
    """Recommend the next combat action using a sidecar MCTS search.

    Companion to :func:`recommend_combat` — the heuristic tool stays the
    sanity-check default; this tool is the deeper search for tight
    spots. Reads the same cached state (``_last_state``).

    Returns a JSON-serializable dict:

    .. code-block:: text

        {
          "actions": [
            {"stable_idx": int, "action": str, "win_prob": float,
             "bucket": str, "is_best": bool, "sims_used": int,
             "visits": int, "q_saturated": bool, "low_confidence": bool},
            ...
          ],
          "pv": [str, ...] | None,   # principal variation, top-level
          "summary": "...",
          "config": {sims, depth, c_puct, fair, threads, seed},
        }

    On any failure (unported relic, adapter ``UnsupportedStateError``,
    Rust panic, etc.) the response degrades to
    ``{"error": "<message>", "fallback": <heuristic-result>}`` so the
    caller always has *something* actionable.

    Design note on ``pv``: it is search-global (the principal variation
    of the single tree) and therefore lifted to the top level rather
    than duplicated on every per-action row. ``None`` when MCTS could
    not produce a PV (e.g. ``fair=True``, terminal state, or threaded
    search with no shared tree).

    Args:
        sims: Simulation budget. Default 300 (budget-knee per
            ``project_mcts_budget_knee`` memory note).
        fair: When True, run an independent per-action search for
            unbiased win-probs; trades cost for accuracy.
        depth: Max rollout depth. Default 10 (Q-saturation knee).
        c_puct: UCB exploration constant. Default 1.4 (~sqrt(2)).
        seed: Optional deterministic seed. ``None`` → 0.
        threads: Parallel worker count. 1 = single-threaded; >1 forfeits
            the PV (par_search has no shared tree).
        cheat_draw_order: Reserved; forwarded to recommender as-is.
        timeout_ms: Soft budget hint. Not yet enforced by the Rust
            side — accepted for forward-compatibility.
    """
    if _last_state is None:
        return {"error": "no run in progress"}
    decision = _last_state.get("decision") or _last_state.get("type")
    if decision != "combat_play":
        return {"error": f"not in combat (decision={decision!r})"}

    # Local imports keep the MCP module's import-time graph small for
    # users who never invoke the hint tool. Matches `recommender.py`'s
    # own deferred-import strategy.
    try:
        from sts2_hint.recommender import RecommendConfig, recommend
    except ImportError as e:
        return {
            "error": f"sts2_hint unavailable: {e}",
            "fallback": _recommend_combat(_last_state),
        }

    cfg = RecommendConfig(
        sims=sims,
        depth=depth,
        c_puct=c_puct,
        seed=seed if seed is not None else 0,
        threads=threads,
        fair=fair,
        cheat_draw_order=cheat_draw_order,
        timeout_ms=timeout_ms,
    )

    try:
        tags = recommend(_last_state, cfg)
    except Exception as e:
        return {
            "error": f"{type(e).__name__}: {e}",
            "fallback": _recommend_combat(_last_state),
        }

    # Convert {stable_idx: HintTag} into a JSON-serializable list,
    # preserving the recommender's own sort: is_best first, then by
    # descending win_prob. The HintTag is a NamedTuple so we can't ship
    # it directly through MCP — convert each row to a plain dict.
    rows: list[dict[str, Any]] = []
    for stable_idx, tag in tags.items():
        rows.append(
            {
                "stable_idx": int(stable_idx),
                "action": _describe_action_for_stable_idx(stable_idx, _last_state),
                "win_prob": float(tag.win_prob),
                "bucket": tag.bucket_label,
                "is_best": bool(tag.is_best),
                "sims_used": int(cfg.sims),
                "visits": int(tag.visits),
                "q_saturated": bool(tag.q_saturated),
                "low_confidence": bool(tag.low_confidence),
            }
        )
    rows.sort(key=lambda r: (not r["is_best"], -r["win_prob"]))

    # PV is search-global. We don't have direct access to mcts_recommend's
    # raw output here (recommender.recommend hides it); for now we surface
    # the best action's description as a 1-element PV when present. A
    # follow-up bead can plumb the full PV through `recommender.recommend`
    # if needed.
    pv: list[str] | None = None
    best = next((r for r in rows if r["is_best"]), None)
    if best is not None:
        pv = [best["action"]]

    summary = (
        f"mcts sims={sims} depth={depth} fair={fair} → "
        f"{best['action'] if best else '(no legal actions)'}"
        + (f" (p_win={best['win_prob']:.2f}, {best['bucket']})" if best else "")
    )

    return {
        "actions": rows,
        "pv": pv,
        "summary": summary,
        "config": {
            "sims": sims,
            "depth": depth,
            "c_puct": c_puct,
            "fair": fair,
            "threads": threads,
            "seed": seed,
        },
    }


def main() -> None:
    mcp.run()


if __name__ == "__main__":
    main()
