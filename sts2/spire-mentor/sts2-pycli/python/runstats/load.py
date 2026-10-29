"""Load a recorded ``.run`` trajectory corpus and expose typed accessors.

Each file is the native ``RunHistory`` v9 JSON with our ``sts2sim`` block
(see ``sts2-rs/crates/sts2-runai/src/trajectory/schema.rs``). We read them
through :func:`run_files.load_run`, which transparently handles gzip.
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from run_files import glob_runs, load_run


def base_card(token: str) -> str:
    """Upgrade-agnostic card token: ``CARD.X+`` / ``CARD.X`` → ``CARD.X``."""
    return token[:-1] if token.endswith("+") else token


def card_label(token: str) -> str:
    """Human display label for a ``CARD.X`` token.

    Strips the ``CARD.`` prefix, any ``+`` upgrade suffix, a trailing
    character suffix (``_IRONCLAD`` etc.), then title-cases. ``CARD.SHRUG_IT_OFF``
    → ``Shrug It Off``; ``CARD.STRIKE_IRONCLAD`` → ``Strike``.
    """
    t = base_card(token)
    if t.startswith("CARD."):
        t = t[len("CARD.") :]
    parts = t.split("_")
    CHARS = {"IRONCLAD", "SILENT", "DEFECT", "NECROBINDER", "REGENT"}
    if len(parts) > 1 and parts[-1] in CHARS:
        parts = parts[:-1]
    return " ".join(p.capitalize() for p in parts)


@dataclass
class Run:
    """One parsed run with cached views over its native + ``sts2sim`` data."""

    data: dict[str, Any]
    path: Path

    # ── outcome / identity ───────────────────────────────────────────
    @property
    def won(self) -> bool:
        return bool(self.data.get("win"))

    @property
    def seed(self) -> str:
        return str(self.data.get("seed"))

    @property
    def ascension(self) -> int:
        return int(self.data.get("ascension", 0))

    @property
    def killed_by(self) -> str:
        return str(self.data.get("killed_by_encounter", "NONE.NONE"))

    @property
    def meta(self) -> dict[str, Any]:
        return self.data.get("sts2sim", {}).get("meta", {})

    @property
    def outcome(self) -> str:
        return str(self.meta.get("outcome", ""))

    @property
    def steps(self) -> int:
        return int(self.meta.get("steps", 0))

    # ── deck / relics ────────────────────────────────────────────────
    @property
    def _player(self) -> dict[str, Any]:
        players = self.data.get("players") or [{}]
        return players[0]

    def deck(self, *, upgraded_aware: bool = False) -> list[str]:
        """Final-deck card tokens (one per copy). ``upgraded_aware`` appends
        ``+`` for upgraded copies; otherwise base ``CARD.X`` tokens."""
        out: list[str] = []
        for c in self._player.get("deck", []):
            tok = str(c["id"])
            if upgraded_aware and int(c.get("upgrades", 0)) > 0:
                tok += "+"
            out.append(tok)
        return out

    def deck_set(self) -> set[str]:
        """Distinct base card tokens in the final deck."""
        return {base_card(t) for t in self.deck()}

    @property
    def relics(self) -> list[str]:
        return [str(r["id"]) for r in self._player.get("relics", [])]

    # ── decision stream ──────────────────────────────────────────────
    @property
    def decisions(self) -> list[dict[str, Any]]:
        return self.data.get("sts2sim", {}).get("decisions", [])

    def card_rewards(self) -> Iterator[dict[str, Any]]:
        """Yield each card-reward decision's offered payload
        (``options``/``relic_offer``/``picked``)."""
        for d in self.decisions:
            o = d.get("offered", {})
            if o.get("kind") == "card_reward":
                yield o

    def event_choices(self) -> Iterator[tuple[str, int]]:
        """Yield ``(event_id, choice_idx)`` for each event decision."""
        for d in self.decisions:
            o = d.get("offered", {})
            if o.get("kind") == "event":
                yield str(o.get("event_id")), int(o.get("choice_idx", -1))

    def combat_plays(self) -> Iterator[str]:
        """Yield the base card token of every card actually played in combat."""
        for d in self.decisions:
            o = d.get("offered", {})
            if o.get("kind") == "combat" and o.get("card"):
                yield base_card(str(o["card"]))

    def combats(self) -> list[dict[str, Any]]:
        """Group consecutive combat decisions into fights.

        Returns one dict per fight: ``{turns, plays, dmg_taken, enemies}``.
        A new fight starts when the combat ``round`` resets to <= 1 after a
        gap or the enemy set changes.
        """
        fights: list[dict[str, Any]] = []
        cur: dict[str, Any] | None = None
        prev_round = 10**9
        for d in self.decisions:
            o = d.get("offered", {})
            if o.get("kind") != "combat":
                if cur is not None:
                    fights.append(cur)
                    cur = None
                prev_round = 10**9
                continue
            rnd = int(o.get("round", 0))
            board = o.get("board") or {}
            if cur is None or rnd < prev_round:
                if cur is not None:
                    fights.append(cur)
                enemies = [e.get("id") for e in (board.get("enemies") or [])]
                cur = {"turns": rnd, "plays": 0, "enemies": enemies}
            cur["turns"] = max(cur["turns"], rnd)
            if o.get("card"):
                cur["plays"] += 1
            prev_round = rnd
        if cur is not None:
            fights.append(cur)
        return fights


def load_corpus(root: str | Path) -> list[Run]:
    """Load every ``.run``/``.run.gz`` under ``root`` into :class:`Run`s."""
    root = Path(root)
    runs: list[Run] = []
    for p in glob_runs(root):
        try:
            runs.append(Run(load_run(p), p))
        except Exception as e:
            print(f"runstats: skipping unreadable {p.name}: {e}")
    return runs
