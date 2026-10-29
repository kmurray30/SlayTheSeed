import importlib.util
import pathlib

spec = importlib.util.spec_from_file_location(
    "asc_blend", pathlib.Path(__file__).with_name("asc_blend.py")
)
ab = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ab)


def test_weights_peak_at_a9_halve_at_a10():
    assert ab.BLEND_WEIGHTS[9] == 10
    assert ab.BLEND_WEIGHTS[10] == 5
    assert ab.BLEND_WEIGHTS[0] == 1


def test_per_level_winrate_counts():
    rows = [
        {"seed": 1, "character": "ironclad", "ascension": 0, "won": True},
        {"seed": 2, "character": "ironclad", "ascension": 0, "won": False},
        {"seed": 1, "character": "ironclad", "ascension": 10, "won": True},
    ]
    pl = ab.per_level_winrate(rows)
    assert pl[0] == (1, 2, 50.0)
    assert pl[10] == (1, 1, 100.0)


def test_blended_uses_weights():
    # rate 100 at A9 (wt 10), rate 0 at A10 (wt 5) -> 1000/15 = 66.6667
    b = ab.blended({9: 100.0, 10: 0.0})
    assert abs(b - (1000.0 / 15.0)) < 1e-6
