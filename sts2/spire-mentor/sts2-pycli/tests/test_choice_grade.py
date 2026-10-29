"""``choice_grade.grade_choice`` -- ported one-to-one from the Rust twin.

Each case mirrors a ``grade_choice_*`` test in
``sts2-rs/crates/sts2-workbench/src/session_choice.rs`` with identical inputs
and expectations, so a drift between the two coaches fails here.
"""

from __future__ import annotations

from choice_grade import grade_choice


def _opts(*probs: float) -> list[tuple[str, float]]:
    return [(f"o{i}", p) for i, p in enumerate(probs)]


def test_grade_choice_agrees_when_pick_is_top() -> None:
    g = grade_choice(_opts(0.6, 0.4), 0)
    assert g is not None
    assert (g.verdict, g.rank, g.top) == ("agree", 1, 0)


def test_grade_choice_flags_clear_disagreement() -> None:
    g = grade_choice(_opts(0.5, 0.3, 0.2), 2)
    assert g is not None
    assert (g.verdict, g.rank, g.top) == ("flag", 3, 0)
    assert g.strength == "preference"


def test_grade_choice_tossup_below_ratio() -> None:
    g = grade_choice(_opts(0.45, 0.35, 0.2), 1)
    assert g is not None and g.verdict == "tossup"


def test_grade_choice_tossup_below_top_floor() -> None:
    g = grade_choice(_opts(0.38, 0.12, 0.25, 0.25), 1)
    assert g is not None and g.verdict == "tossup"


def test_grade_choice_strong_preference() -> None:
    g = grade_choice(_opts(0.8, 0.1, 0.1), 1)
    assert g is not None
    assert (g.verdict, g.strength) == ("flag", "strong preference")


def test_grade_choice_flags_at_the_exact_thresholds() -> None:
    g = grade_choice(_opts(0.4, 0.2, 0.4), 1)
    assert g is not None and g.verdict == "flag"


def test_grade_choice_tie_with_the_top_is_agreement() -> None:
    g = grade_choice(_opts(0.5, 0.5), 1)
    assert g is not None and g.verdict == "agree"


def test_a_skip_or_out_of_range_pick_has_no_grade() -> None:
    # Python-only: the caller resolves a skip to the advice's skip option; an
    # unresolved one (None) or a bad position is never guessed at.
    assert grade_choice(_opts(0.6, 0.4), None) is None
    assert grade_choice(_opts(0.6, 0.4), 2) is None
    assert grade_choice([], 0) is None
