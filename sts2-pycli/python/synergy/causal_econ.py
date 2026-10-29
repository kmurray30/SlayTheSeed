"""State sampler + value-net scorer for the card-synergy catalog.

Wraps the ``synergy_score`` Rust binary (Task 2.1) to evaluate
V(state) for batches of mid-run deck contexts.  The binary is invoked
**once per call** — all states are piped through stdin in a single
subprocess — so callers may batch thousands of states cheaply.

**Approximation note (MVP sampler):** :func:`sample_contexts` constructs
mid-run states from *terminal* decks by randomly dropping a tail fraction
of cards.  This is a rough proxy for mid-run progress: a player heading
toward act 3 with 20 cards might have had 14 at act 2.  The
:mod:`human_features` per-floor reconstruction in sts2-rs is the
principled alternative; for the synergy catalog this approximation is
sufficient to generate a diverse distribution of real-deck contexts.

Usage::

    from synergy.causal_econ import sample_contexts, score_states

    states = sample_contexts(200)          # 200 mid-run contexts from corpus
    scores = score_states(states)          # V(state) for each
"""

from __future__ import annotations

import json
import os
import random
import statistics
import subprocess
import sys
from collections.abc import Iterable
from dataclasses import dataclass, field
from pathlib import Path

from synergy.corpus import Deck
from synergy.observational import Pair

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------

_HERE = Path(__file__).resolve().parent
_RS_ROOT = (_HERE / ".." / ".." / ".." / "sts2-rs").resolve()
_BINARY = _RS_ROOT / "target" / "release" / "synergy_score"


# ---------------------------------------------------------------------------
# Build-freshness guard (mirrors run_arena.py)
# ---------------------------------------------------------------------------


def _newest_source_mtime(rs_root: Path) -> float:
    """Newest mtime over the Rust workspace inputs the binary is built from."""
    newest = 0.0
    for pattern in ("crates/**/*.rs", "crates/**/Cargo.toml"):
        for p in rs_root.glob(pattern):
            try:
                newest = max(newest, p.stat().st_mtime)
            except OSError:
                continue
    for name in ("Cargo.toml", "Cargo.lock"):
        p = rs_root / name
        if p.exists():
            newest = max(newest, p.stat().st_mtime)
    return newest


def ensure_binary_fresh(no_build: bool = False) -> Path:
    """Return the synergy_score binary path, rebuilding if stale/missing.

    Mtime-based freshness check: the binary is stale if any
    ``crates/**/*.rs`` (or workspace ``Cargo.{toml,lock}``) is newer
    than the binary.

    Args:
        no_build: If ``True``, raise :class:`RuntimeError` instead of
            rebuilding when the binary is stale or missing.  Use in
            contexts where building is known to have already happened
            and a stale binary would indicate a programming error.

    Returns:
        Path to the fresh ``synergy_score`` binary.

    Raises:
        RuntimeError: When *no_build* is ``True`` and the binary is
            stale or missing.
    """
    stale = not _BINARY.exists() or _BINARY.stat().st_mtime < _newest_source_mtime(_RS_ROOT)
    if not stale:
        return _BINARY
    if no_build:
        raise RuntimeError(
            f"synergy_score binary is stale or missing ({_BINARY}).\n"
            "Rebuild with: cargo build --release -p sts2-cli --bin synergy_score "
            f"(in {_RS_ROOT}) or drop no_build=True."
        )
    print(f"[synergy_score] rebuilding stale binary: {_BINARY}", file=sys.stderr)
    subprocess.run(
        ["cargo", "build", "--release", "-p", "sts2-cli", "--bin", "synergy_score"],
        cwd=_RS_ROOT,
        check=True,
    )
    return _BINARY


# ---------------------------------------------------------------------------
# State dataclass
# ---------------------------------------------------------------------------


@dataclass
class State:
    """A mid-run game state for value-net scoring.

    Attributes:
        deck: Engine-token card ids (e.g. ``["STRIKE_IRONCLAD", "BASH+1"]``).
            Upgraded cards use the ``+1`` suffix.
        relics: Engine-token relic ids.  Defaults to ``["BURNING_BLOOD"]``
            (the Ironclad starter relic).
        hp: Current player HP.
        max_hp: Maximum player HP.
        gold: Current gold.
        ascension: Ascension level (0–20).
        act_index: Current act index (1-based; act 1 = 1, act 2 = 2, act 3 = 3).
        act_floor: Floor within the current act.
    """

    deck: list[str]
    relics: list[str] = field(default_factory=lambda: ["BURNING_BLOOD"])
    hp: int = 70
    max_hp: int = 80
    gold: int = 99
    ascension: int = 0
    act_index: int = 2
    act_floor: int = 3


# ---------------------------------------------------------------------------
# Sampler
# ---------------------------------------------------------------------------


def sample_contexts(
    n: int,
    decks: Iterable[Deck] | None = None,
    seed: int = 0,
) -> list[State]:
    """Sample *n* mid-run-like :class:`State` instances from real terminal decks.

    **Approximation:** terminal decks are used directly, but a random
    *tail fraction* of cards is dropped to mimic a mid-run state where
    the player has not yet acquired all their eventual cards.  The
    fraction is drawn uniformly in ``[0.0, 0.5)`` so at least half the
    deck is always kept.

    This is cheaper than per-floor reconstruction and sufficient for the
    card-synergy catalog; use ``human_features.rs`` if you need precise
    floor-by-floor states.

    Args:
        n: Number of states to sample.
        decks: Source decks.  If ``None``, decks are loaded from the corpus
            via :func:`~synergy.corpus.iter_runs` (reads ``SYNERGY_CODEX_ROOT``).
        seed: RNG seed for reproducibility.

    Returns:
        A list of :class:`State` objects, one per sampled deck context.
        If the corpus has fewer than *n* decks (including zero), returns
        fewer than *n* states (sampling with replacement is NOT applied).
    """
    rng = random.Random(seed)

    if decks is None:
        # Re-read SYNERGY_CODEX_ROOT at call time so tests can override it
        # via os.environ before calling sample_contexts.
        from pathlib import Path as _Path

        from synergy.corpus import CODEX_ROOT, iter_runs, parse_deck

        codex_root = _Path(os.environ.get("SYNERGY_CODEX_ROOT", str(CODEX_ROOT)))
        all_decks = [
            Deck(cards=parse_deck(run), won=bool(run.get("win")), player_id=None, skill=None)
            for run in iter_runs(codex_root)
        ]
    else:
        all_decks = list(decks)

    if not all_decks:
        return []

    # Always sample with replacement so the caller reliably gets exactly n states
    # regardless of corpus size.  This means small corpora (e.g. test fixtures)
    # produce repeated deck draws; that is intentional — callers that want
    # without-replacement sampling should pre-filter decks themselves.
    chosen: list[Deck] = rng.choices(all_decks, k=n)

    states: list[State] = []
    for deck_obj in chosen:
        cards = list(deck_obj.cards)
        # Drop a random tail fraction to approximate mid-run progress.
        if cards:
            fraction = rng.uniform(0.0, 0.5)
            keep = max(1, int(len(cards) * (1.0 - fraction)))
            # Shuffle then keep first `keep` so dropped cards are random, not
            # always the last-acquired ones (which would bias toward early game).
            rng.shuffle(cards)
            cards = cards[:keep]
        states.append(State(deck=cards))

    return states


# ---------------------------------------------------------------------------
# Scorer
# ---------------------------------------------------------------------------


def score_states(
    states: list[State],
    no_build: bool = False,
    *,
    net: str = "gbm",
    character: str = "ironclad",
) -> list[float]:
    """Score a batch of :class:`State` instances via the ``synergy_score`` binary.

    Serialises each state to a JSON line, pipes all lines to a single
    ``synergy_score`` subprocess, and parses the float output.  The net
    path is controlled by the ``STS2_RUNAI_VALUE`` environment variable
    (passed through to the subprocess).

    Args:
        states: States to score.  Returned list preserves input order.
        no_build: Forwarded to :func:`ensure_binary_fresh`; set ``True``
            to abort (rather than rebuild) when the binary is stale.
        net: Value-net backend — ``"gbm"`` (default; the legacy Ironclad-only
            GBM net) or ``"ppo"`` (the deployed per-character PPO value head).
            Passed to the binary as ``--net``.  The default ``"gbm"`` emits a
            bare invocation for backward compatibility.
        character: Character whose base stats / PPO champion to use, passed as
            ``--character``.  Omitted from the argv when left at the default
            ``"ironclad"`` (the binary's own default).

    Returns:
        A list of ``float`` values, one per input state, in the same order.

    Raises:
        RuntimeError: If the binary is stale/missing and *no_build* is
            ``True``, or if the binary returns a number of scores that does
            not match the number of input states (silent misalignment).
        subprocess.CalledProcessError: If the binary exits with a non-zero
            status (e.g. unknown card token).
    """
    if not states:
        return []

    binary = ensure_binary_fresh(no_build=no_build)

    lines = [json.dumps(s.__dict__) for s in states]
    stdin_text = "\n".join(lines)

    # Build argv. Default gbm/ironclad stays a bare invocation (drop-in with the
    # historical behaviour); non-default net/character append the matching flags.
    cmd = [str(binary)]
    if net != "gbm":
        cmd += ["--net", net]
    if character != "ironclad":
        cmd += ["--character", character]

    result = subprocess.run(
        cmd,
        input=stdin_text,
        capture_output=True,
        text=True,
        env=os.environ.copy(),
    )

    if result.returncode != 0:
        raise subprocess.CalledProcessError(
            result.returncode,
            str(binary),
            output=result.stdout,
            stderr=result.stderr,
        )

    scores: list[float] = []
    for line in result.stdout.splitlines():
        line = line.strip()
        if line:
            scores.append(float(line))

    # The spec is exactly one float per input line, in order. If the bin emits
    # a blank/extra/missing line, the `if line:` guard above would silently
    # produce a misaligned list — surface that as an error instead.
    if len(scores) != len(states):
        raise RuntimeError(f"synergy_score returned {len(scores)} scores for {len(states)} states")

    return scores


# ---------------------------------------------------------------------------
# Second-difference synergy scorer (Task 2.3)
# ---------------------------------------------------------------------------


@dataclass
class EconRow:
    """Aggregated second-difference synergy score for a card pair over contexts.

    For each context deck D and pair (A, B), the per-context score is::

        S = V(D+A+B) − V(D+A) − V(D+B) + V(D)

    ``median``, ``p25``, and ``p75`` summarise the distribution of S across all
    contexts; ``n`` is the number of contexts evaluated.
    """

    median: float
    p25: float
    p75: float
    n: int


def econ_synergy(
    pair: Pair,
    contexts: list[State],
    no_build: bool = False,
    *,
    net: str = "gbm",
    character: str = "ironclad",
) -> EconRow:
    """Compute the value-net second difference for *pair* over *contexts*.

    For each context state *D*, four states are constructed:

    * ``D``       — the context as-is
    * ``D+A``     — context deck with card A appended
    * ``D+B``     — context deck with card B appended
    * ``D+A+B``   — context deck with both A and B appended

    All four share identical relics/hp/gold/ascension/act fields; only the
    deck differs (RULE 4: only the tested cards differ).

    The four states for each context are packed consecutively, and all
    ``4 * len(contexts)`` states are submitted in a **single**
    :func:`score_states` call for efficiency.

    Per-context second difference::

        S = V(D+A+B) − V(D+A) − V(D+B) + V(D)

    Args:
        pair: A ``(card_a_token, card_b_token)`` pair.
        contexts: Base deck contexts to evaluate over.  Input states are
            never mutated.
        no_build: Forwarded to :func:`score_states`; set ``True`` to abort
            rather than rebuild when the binary is stale.

    Returns:
        An :class:`EconRow` with ``median``, ``p25``, ``p75``, and ``n``.
        For empty *contexts*, returns ``EconRow(0.0, 0.0, 0.0, 0)``.
    """
    if not contexts:
        return EconRow(median=0.0, p25=0.0, p75=0.0, n=0)

    a, b = pair

    # Build all 4*K states in one flat list: [D0, DA0, DB0, DAB0, D1, DA1, …]
    batch: list[State] = []
    for ctx in contexts:
        base_deck = ctx.deck  # don't copy yet — we copy per variant below
        d = State(
            deck=list(base_deck),
            relics=ctx.relics,
            hp=ctx.hp,
            max_hp=ctx.max_hp,
            gold=ctx.gold,
            ascension=ctx.ascension,
            act_index=ctx.act_index,
            act_floor=ctx.act_floor,
        )
        da = State(
            deck=[*base_deck, a],
            relics=ctx.relics,
            hp=ctx.hp,
            max_hp=ctx.max_hp,
            gold=ctx.gold,
            ascension=ctx.ascension,
            act_index=ctx.act_index,
            act_floor=ctx.act_floor,
        )
        db = State(
            deck=[*base_deck, b],
            relics=ctx.relics,
            hp=ctx.hp,
            max_hp=ctx.max_hp,
            gold=ctx.gold,
            ascension=ctx.ascension,
            act_index=ctx.act_index,
            act_floor=ctx.act_floor,
        )
        dab = State(
            deck=[*base_deck, a, b],
            relics=ctx.relics,
            hp=ctx.hp,
            max_hp=ctx.max_hp,
            gold=ctx.gold,
            ascension=ctx.ascension,
            act_index=ctx.act_index,
            act_floor=ctx.act_floor,
        )
        batch.extend([d, da, db, dab])

    all_scores = score_states(batch, no_build=no_build, net=net, character=character)

    # Slice back into per-context quartets and compute second difference
    s_values: list[float] = []
    for i in range(len(contexts)):
        v_d, v_da, v_db, v_dab = all_scores[4 * i : 4 * i + 4]
        s_values.append(v_dab - v_da - v_db + v_d)

    med = statistics.median(s_values)

    if len(s_values) >= 2:
        qs = statistics.quantiles(s_values, n=4)
        p25, p75 = qs[0], qs[2]
    else:
        # Single value: all three collapse to the same point
        p25, p75 = med, med

    return EconRow(median=med, p25=p25, p75=p75, n=len(contexts))
