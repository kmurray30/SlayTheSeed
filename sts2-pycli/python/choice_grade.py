"""Policy-agreement grade for a run-level choice (map, card reward, shop, ...).

Twin of ``sts2-rs/crates/sts2-workbench/src/session_choice.rs::grade_choice``
(the browser workbench's coach). Keep the two in step: same constants, same
tie-breaks, and the unit cases in ``tests/test_choice_grade.py`` are ported
one-to-one from that file's ``grade_choice_*`` tests.

Only a policy distribution exists for these decisions, so the grade is
AGREEMENT -- never a win-probability cost (PPO probabilities are not win
rates). A pick is flagged only when the policy's top pick carries at least
:data:`FLAG_TOP_MIN` and at least :data:`FLAG_RATIO` times the pick's own
probability; anything else that is not the top pick is a toss-up.
"""

from __future__ import annotations

from dataclasses import dataclass

#: The top pick must carry at least this much probability to flag a choice.
FLAG_TOP_MIN = 0.40
#: ...and at least this multiple of the pick's own probability.
FLAG_RATIO = 2.0
#: A top pick at or above this is a "strong preference".
STRONG_PREFERENCE = 0.70


@dataclass(frozen=True)
class ChoiceVerdict:
    """:func:`grade_choice`'s result."""

    #: ``"agree"`` | ``"tossup"`` | ``"flag"``.
    verdict: str
    #: The pick's 1-based rank by probability.
    rank: int
    #: Position (in ``options``) of the policy's top pick.
    top: int
    #: ``"strong preference"`` | ``"preference"`` -- how sure the policy is of its top.
    strength: str


def grade_choice(options: list[tuple[str, float]], played: int | None) -> ChoiceVerdict | None:
    """Grade pick ``played`` among ``options`` (``(label, prob)`` pairs).

    ``played`` is a POSITION in ``options``. A skip is ``None`` here: the
    caller resolves it to the advice's own skip option when the screen offered
    one, and a skip with no such option has no grade (``None``), as does an
    out-of-range position or an empty option list.
    """
    if played is None or not options or not 0 <= played < len(options):
        return None
    probs = [float(p) for _, p in options]
    # First maximum wins ties, exactly like the Rust fold (`*p > probs[b]`).
    top = 0
    for i, p in enumerate(probs):
        if p > probs[top]:
            top = i
    p_top, p_pick = probs[top], probs[played]
    rank = 1 + sum(1 for p in probs if p > p_pick)
    strength = "strong preference" if p_top >= STRONG_PREFERENCE else "preference"
    if played == top or p_pick >= p_top:
        verdict = "agree"
    elif p_top >= FLAG_TOP_MIN and p_top >= FLAG_RATIO * p_pick:
        verdict = "flag"
    else:
        verdict = "tossup"
    return ChoiceVerdict(verdict=verdict, rank=rank, top=top, strength=strength)
