"""Pure-python tests for the sweep-v2 manifest writer + comparator.

(write_manifest/latest_manifest are exercised by the sweep-v2 driver tests.)
"""

from sweep_manifest import build_manifest, compare_manifests


def _r(subject, category, first_path=None, seed=0):
    return {
        "seed": seed,
        "coverage_subject": subject,
        "category": category,
        "first_path": first_path,
    }


def test_build_manifest_subjects_and_verdicts() -> None:
    results = [
        _r("card+:BASH", "clean"),
        _r("card:WHIRLWIND@act2", "PLAYER_STATE", "player.block"),
        _r(None, "clean", seed=42),  # fuzz trial → seed-keyed
    ]
    m = build_manifest(
        tier="quick",
        axes=["upgraded"],
        results=results,
        meta={"sts2_rs_sha": "abc", "pycli_sha": "def"},
    )
    assert m["subjects"]["card+:BASH"] == {"verdict": "clean"}
    div = m["subjects"]["card:WHIRLWIND@act2"]
    assert div["verdict"] == "divergent"
    assert div["bucket"] == "PLAYER_STATE"
    assert "detail_hash" in div
    assert m["subjects"]["seed:42"] == {"verdict": "clean"}
    assert m["meta"]["tier"] == "quick"


def test_blocked_subject_passthrough() -> None:
    m = build_manifest(
        tier="full",
        axes=["arenas"],
        results=[],
        meta={},
        blocked={"card:BASH@act2": "arena act2 not clean"},
    )
    assert m["subjects"]["card:BASH@act2"] == {
        "verdict": "BLOCKED",
        "reason": "arena act2 not clean",
    }


def test_compare_finds_regressions_fixes_and_new() -> None:
    prev = build_manifest(
        "quick",
        [],
        [
            _r("card+:BASH", "clean"),
            _r("card+:CLEAVE", "PLAYER_STATE", "player.block"),
            _r("card+:GONE", "clean"),
        ],
        meta={},
    )
    cur = build_manifest(
        "quick",
        [],
        [
            _r("card+:BASH", "MONSTER_STATE", "monsters[0].hp"),  # regression
            _r("card+:CLEAVE", "clean"),  # fix
            _r("card+:NEW", "clean"),  # new
        ],
        meta={},
    )
    d = compare_manifests(prev, cur)
    assert d["regressions"] == ["card+:BASH"]
    assert d["fixes"] == ["card+:CLEAVE"]
    assert d["new"] == ["card+:NEW"]
    assert d["gone"] == ["card+:GONE"]


def test_detail_hash_stable_for_same_divergence() -> None:
    a = build_manifest("q", [], [_r("x", "PLAYER_STATE", "player.block")], meta={})
    b = build_manifest("q", [], [_r("x", "PLAYER_STATE", "player.block")], meta={})
    assert a["subjects"]["x"]["detail_hash"] == b["subjects"]["x"]["detail_hash"]
