"""Join layer for the card-synergy catalog.

Merges the score sources — observational (PMI, plus the candidate
``interaction_lr`` and factorization-machine ``fm_S`` signals), economic
(value-net second difference), and combat (four-cell fight interaction) — into a
single ``catalog.json`` document and tags each pair with a quadrant.

**Quadrant tagging (spec §4: PMI is the human-intent axis)**

- ``obs_high``:   ``pmi > median(pmi over all edges)`` (PMI only; the retired
  ``wins_minus_losses`` gate is gone — ``interaction_lr`` / ``fm_S`` are reported
  but do not gate this axis)
- ``causal_high``: ``combat.median_interaction > combat_median_min`` (effect-size
  gate; default 0).  The 95% bootstrap CI is preserved in ``confidence`` rather
  than used as the gate — a strict CI-lower-bound>0 gate is far too stringent at
  the per-pair sample sizes feasible for a whole-catalog sweep.
  Fallback order when combat is absent for a pair:
  1. If econ is present: ``econ.median > econ_median_min``.
  2. Otherwise: causal_high = False.

Quadrant map::

    obs_high ∧  causal_high → "confirmed"
    obs_high ∧ ¬causal_high → "myth"
   ¬obs_high ∧  causal_high → "undiscovered"
   ¬obs_high ∧ ¬causal_high → "noise"

**Confidence formula**

When a combat :class:`~synergy.causal_combat.CombatRow` is present the
``confidence`` field is derived from the CI width relative to the CI mid-point:

    confidence = 1 − (ci_width / (abs(mid) + ci_width + 1e-9))

This is bounded in ``[0, 1]``: a wide CI relative to its centre → low
confidence; a tight CI → high confidence.  When combat is absent, ``confidence``
is ``None``.

**Label derivation**

Card tokens follow the ``UPPER_SNAKE`` naming convention used by the engine.
A display label is derived by:

1. Stripping any ``_IRONCLAD`` (or ``_SILENT``, ``_DEFECT``, etc.) character
   suffix — i.e. removing the trailing ``_<UPPERCASE_WORD>`` when that word is a
   known character name.  Because we cannot enumerate every character name in
   advance, we strip the last ``_WORD`` suffix only if the token still contains
   at least one more ``_``; otherwise we keep it as-is.  (Tokens like ``BASH``
   remain ``Bash``; ``STRIKE_IRONCLAD`` → ``STRIKE`` → ``Strike``.)
   A simpler heuristic: strip any trailing ``_<SINGLE_WORD>`` that matches the
   pattern ``_[A-Z][A-Z]+$`` — i.e. an all-uppercase word of ≥ 2 chars at the
   tail. This reliably removes ``_IRONCLAD`` / ``_SILENT`` / ``_DEFECT`` /
   ``_NECROBINDER`` etc. while preserving meaningful multi-word tokens like
   ``BODY_SLAM``.
2. Stripping any upgrade suffix (``+1``, ``+2``, …) after the above.
3. Replacing remaining underscores with spaces.
4. Title-casing the result.

**econ/combat input file format (for the CLI)**

Econ scores file: JSON object keyed by ``"A|B"`` pair strings (alphabetically
sorted, separated by ``|``) where each value is::

    {"median": 0.55, "p25": 0.3, "p75": 0.8, "n": 200}

Combat scores file: JSON object keyed by ``"A|B"`` pair strings where each
value is::

    {"median_interaction": 1.2, "ci": [0.4, 2.1], "n": 90}

Usage::

    from synergy.catalog import build_catalog, card_label, Thresholds

    catalog_dict = build_catalog(
        obs=pmi_table_result,
        interaction=interaction_lr_result,
        fm=fm_interactions_result,
        econ=None,
        combat=None,
        card_frequency={"STRIKE_IRONCLAD": 0.91, ...},
        run_count=10_000,
    )
"""

from __future__ import annotations

import datetime
import re
import statistics
from dataclasses import dataclass
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from synergy.causal_combat import CombatRow
    from synergy.causal_econ import EconRow
    from synergy.multiplicity import MultiplicityRow
    from synergy.observational import InteractionRow, ObsRow, Pair

# ---------------------------------------------------------------------------
# Label derivation
# ---------------------------------------------------------------------------

# Character suffixes are single ALL-CAPS words of ≥ 2 characters at the tail
# preceded by an underscore.  This matches ``_IRONCLAD``, ``_SILENT``,
# ``_DEFECT``, ``_NECROBINDER``, ``_REGENT`` etc.  It deliberately does NOT
# match ``_SLAM`` in ``BODY_SLAM`` because ``BODY_SLAM`` only has one
# underscore — after stripping ``_SLAM`` the result would be a single word
# ``BODY``, which is wrong.  We only strip if the prefix still contains an
# underscore (i.e. there is meaningful multi-word content left).
_CHAR_SUFFIX_RE = re.compile(r"_([A-Z]{2,})$")
# Upgrade suffix: +1, +2, …
_UPGRADE_SUFFIX_RE = re.compile(r"\+\d+$")

# Explicit character name set for safety — strip suffix only when it's known
# to be a character name.  Extend as new characters are added.
_CHARACTER_NAMES: frozenset[str] = frozenset(
    {
        "IRONCLAD",
        "SILENT",
        "DEFECT",
        "NECROBINDER",
        "REGENT",
    }
)


def card_label(token: str) -> str:
    """Derive a human-readable display label from an engine card token.

    Rules (applied in order):
    1. Strip upgrade suffix (``+N`` at end of string).
    2. If the token ends with ``_<WORD>`` and ``<WORD>`` is a known character
       name, strip that suffix.
    3. Replace underscores with spaces.
    4. Title-case.

    Examples::

        card_label("STRIKE_IRONCLAD") == "Strike"
        card_label("BODY_SLAM")       == "Body Slam"
        card_label("BASH")            == "Bash"
        card_label("IRON_WAVE")       == "Iron Wave"
        card_label("BODY_SLAM+1")     == "Body Slam"

    Args:
        token: Engine card token string (``UPPER_SNAKE_CASE``).

    Returns:
        Title-cased display label.
    """
    # 1. Strip upgrade suffix
    token = _UPGRADE_SUFFIX_RE.sub("", token)

    # 2. Strip known character suffix
    m = _CHAR_SUFFIX_RE.search(token)
    if m and m.group(1) in _CHARACTER_NAMES:
        token = token[: m.start()]

    # 3. Replace underscores with spaces, title-case
    return token.replace("_", " ").title()


# ---------------------------------------------------------------------------
# Thresholds
# ---------------------------------------------------------------------------


@dataclass
class Thresholds:
    """Configurable classification thresholds for quadrant tagging.

    Attributes:
        pmi_median_override: If not ``None``, override the auto-computed PMI
            median with this fixed value.  Useful in unit tests to pin the
            obs_high threshold without needing exactly the right number of pairs.
            Default: ``None`` (compute from the edge set).
        combat_median_min: Combat ``median_interaction`` must exceed this to be
            causal_high via combat.  Default: ``0.0`` (strictly positive median
            interaction — an effect-size gate).  The CI is preserved in the
            ``confidence`` field so callers can still see statistical reliability;
            a strict 95% CI lower-bound>0 gate is far too stringent at the
            per-pair sample sizes feasible for a whole-catalog sweep (n≈36 gives
            HP-granularity CIs ±5-6 wide), so the gate is effect-size based.
        econ_median_min: Econ median must exceed this for the econ fallback to
            count as causal_high.  Default: ``0.0`` (strictly positive median).
        q_max: Benjamini-Hochberg FDR target for the ``undiscovered`` gate
            (spec §9b).  A pair tagged ``undiscovered`` whose multiplicity
            ``qvalue`` exceeds this is demoted to ``noise``.  Default ``0.1``.
            Ignored when no multiplicity data is supplied.
    """

    pmi_median_override: float | None = None
    combat_median_min: float = 0.0
    econ_median_min: float = 0.0
    q_max: float = 0.1


# ---------------------------------------------------------------------------
# Confidence formula
# ---------------------------------------------------------------------------


def _confidence(ci: tuple[float, float]) -> float:
    """Derive a [0,1] confidence score from a bootstrap CI.

    Formula::

        width = ci[1] - ci[0]
        mid   = (ci[0] + ci[1]) / 2
        confidence = 1 - width / (abs(mid) + width + 1e-9)

    Interpretation: a tight CI relative to its centre → high confidence (→ 1);
    a wide CI → low confidence (→ 0).  The ``1e-9`` term prevents division by
    zero when both the CI width and its mid-point are exactly 0.

    Args:
        ci: ``(lower, upper)`` bootstrap percentile CI.

    Returns:
        Confidence in ``[0.0, 1.0]``.
    """
    lo, hi = ci
    width = hi - lo
    mid = (lo + hi) / 2.0
    raw = 1.0 - width / (abs(mid) + width + 1e-9)
    return max(0.0, min(1.0, raw))


# ---------------------------------------------------------------------------
# Core builder
# ---------------------------------------------------------------------------


def build_catalog(
    *,
    obs: dict[Pair, ObsRow],
    interaction: dict[Pair, InteractionRow],
    econ: dict[Pair, EconRow] | None,
    combat: dict[Pair, CombatRow] | None,
    card_frequency: dict[str, float],
    run_count: int,
    fm: dict[Pair, float] | None = None,
    multiplicity: dict[Pair, MultiplicityRow] | None = None,
    iv: dict[Pair, float] | None = None,
    lopo: dict[Pair, float] | None = None,
    character: str = "Ironclad",
    thresholds: Thresholds | None = None,
) -> dict:
    """Join score sources and produce the final catalog document.

    Merges observational PMI (+ the candidate interaction_lr / fm_S signals),
    optional economic (value-net second difference), and optional combat
    (four-cell fight interaction) scores into a single
    ``catalog.json``-compatible dict.

    **Quadrant tagging** — see module docstring for the full spec.

    Args:
        obs: PMI table from :func:`~synergy.observational.pmi_table`.
        interaction: Controlled logistic-regression interaction mapping from
            :func:`~synergy.observational.interaction_lr` (replaces the retired
            ``wins_minus_losses``).  A candidate causal-adjacent signal; reported
            on each edge but does NOT gate ``obs_high`` (PMI is the human axis).
        fm: Optional factorization-machine pairwise interaction weights from
            :func:`~synergy.observational.fm_interactions` (``V[a]·V[b]``), or
            ``None`` when the FM pass was not run.
        multiplicity: Optional per-pair :class:`~synergy.multiplicity.
            MultiplicityRow` (EB-shrunk estimate + permutation p + BH-FDR q), or
            ``None`` when the §9b layer was not run.  When present, each edge
            carries ``shrunk_S``/``pvalue``/``qvalue`` and the ``undiscovered``
            quadrant is FDR-gated at ``thresholds.q_max``.
        iv: Optional per-pair offer-IV effect (``synergy.causal_iv``, §4b);
            reported as ``iv_S`` per edge.  Does not gate any quadrant.
        lopo: Optional per-pair leave-one-out combat marginal
            (``synergy.causal_lopo``, §7b); reported as ``combat_lopo_S``.
        econ: Per-pair :class:`~synergy.causal_econ.EconRow` mapping, or
            ``None`` when the econ layer was not run.
        combat: Per-pair :class:`~synergy.causal_combat.CombatRow` mapping,
            or ``None`` when the combat layer was not run.
        card_frequency: Mapping from card token to appearance frequency
            (fraction of decks).  Nodes are built from the union of tokens
            that appear in any edge or in this mapping.
        run_count: Number of runs in the corpus (echoed into the document).
        character: Character name for the document header.  Default
            ``"Ironclad"``.
        thresholds: Classification thresholds.  ``None`` → :class:`Thresholds`
            defaults.

    Returns:
        A ``dict`` with keys ``generated_at``, ``character``, ``run_count``,
        ``nodes``, ``edges`` matching the Phase-0 schema (0.4).
    """
    if thresholds is None:
        thresholds = Thresholds()

    pairs = list(obs.keys())

    # --- PMI median for obs_high threshold -----------------------------------
    if thresholds.pmi_median_override is not None:
        pmi_median = thresholds.pmi_median_override
    elif pairs:
        pmi_median = statistics.median(obs[p].pmi for p in pairs)
    else:
        pmi_median = 0.0

    # --- Build edges ---------------------------------------------------------
    edges: list[dict] = []
    for pair in pairs:
        obs_row = obs[pair]
        inter_row = interaction.get(pair)
        fm_s: float | None = fm.get(pair) if fm is not None else None

        # obs_high is the human-intent axis: PMI only (spec §4).  The
        # interaction_lr / fm_S fields are reported as candidate causal signals
        # but do not gate this axis; the causal axis stays combat/econ→ppo.
        obs_high = obs_row.pmi > pmi_median

        # Causal signal
        combat_row = (combat or {}).get(pair)
        econ_row = (econ or {}).get(pair)

        if combat_row is not None:
            # Effect-size gate on the median interaction; the CI feeds the
            # `confidence` field so reliability stays visible (see Thresholds).
            causal_high = combat_row.median_interaction > thresholds.combat_median_min
            combat_s: float | None = combat_row.median_interaction
            confidence: float | None = _confidence(combat_row.ci)
            sample_n: int | None = combat_row.n
        elif econ_row is not None:
            # Fallback: use econ median when combat absent
            causal_high = econ_row.median > thresholds.econ_median_min
            combat_s = None
            confidence = None
            sample_n = econ_row.n
        else:
            causal_high = False
            combat_s = None
            confidence = None
            sample_n = None

        econ_s: float | None = econ_row.median if econ_row is not None else None

        # Quadrant
        if obs_high and causal_high:
            quadrant = "confirmed"
        elif obs_high and not causal_high:
            quadrant = "myth"
        elif not obs_high and causal_high:
            quadrant = "undiscovered"
        else:
            quadrant = "noise"

        # Multiplicity (§9b): report shrunk/p/q, and FDR-gate `undiscovered`.
        mult_row = (multiplicity or {}).get(pair)
        shrunk_s: float | None = mult_row.shrunk_S if mult_row is not None else None
        pvalue: float | None = mult_row.pvalue if mult_row is not None else None
        qvalue: float | None = mult_row.qvalue if mult_row is not None else None

        # The `undiscovered` quadrant is promoted only when its interaction
        # survives BH-FDR at the target q; otherwise it is demoted to `noise`
        # (spec §9b).  The obs_high axis (confirmed/myth) is the human-intent
        # axis and is never gated here.  Raw estimates stay reported regardless.
        if quadrant == "undiscovered" and qvalue is not None and qvalue > thresholds.q_max:
            quadrant = "noise"

        edges.append(
            {
                "pair": list(pair),
                "pmi": obs_row.pmi,
                "interaction_lr": inter_row.beta_ab if inter_row is not None else None,
                "interaction_ci": list(inter_row.ci) if inter_row is not None else None,
                "fm_S": fm_s,
                "shrunk_S": shrunk_s,
                "pvalue": pvalue,
                "qvalue": qvalue,
                "iv_S": (iv or {}).get(pair),
                "combat_lopo_S": (lopo or {}).get(pair),
                "econ_S": econ_s,
                "combat_S": combat_s,
                "confidence": confidence,
                "sample_n": sample_n,
                "quadrant": quadrant,
            }
        )

    # --- Build nodes ---------------------------------------------------------
    # Collect all card tokens referenced in any edge or in card_frequency.
    all_tokens: set[str] = set(card_frequency.keys())
    for pair in pairs:
        all_tokens.update(pair)

    nodes: list[dict] = []
    for token in sorted(all_tokens):
        nodes.append(
            {
                "id": token,
                "label": card_label(token),
                "frequency": card_frequency.get(token, 0.0),
                # upgraded_frequency: not available from the obs pre-catalog;
                # callers may inject it by post-processing the returned dict.
            }
        )

    return {
        "generated_at": datetime.datetime.now(datetime.UTC).isoformat(),
        "character": character,
        "run_count": run_count,
        "nodes": nodes,
        "edges": edges,
    }
