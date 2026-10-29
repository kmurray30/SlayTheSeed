from runstats.classify_forks import BUCKETS, classify_seed


def _p(deployed: float, h10x: float, clair: float, clair10x: float) -> dict[str, float]:
    return {
        "deployed": deployed,
        "honest10x": h10x,
        "clair": clair,
        "clair10x": clair10x,
    }


def test_buckets() -> None:
    # spec thresholds, in precedence order
    assert classify_seed(_p(0.5, 0.5, 0.5, 0.5)) == "VARIANCE"
    assert classify_seed(_p(0.0, 0.0, 0.125, 0.125)) == "UNWINNABLE"
    assert classify_seed(_p(0.0, 0.125, 0.5, 0.875)) == "INFO_LIMITED"
    assert classify_seed(_p(0.0, 0.5, 0.25, 0.875)) == "BUDGET_LIMITED"
    assert classify_seed(_p(0.125, 0.25, 0.25, 0.5)) == "EVAL_LIMITED"
    assert set(BUCKETS) == {
        "VARIANCE",
        "UNWINNABLE",
        "INFO_LIMITED",
        "BUDGET_LIMITED",
        "EVAL_LIMITED",
    }
