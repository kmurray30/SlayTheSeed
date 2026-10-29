import importlib.util
import pathlib

spec = importlib.util.spec_from_file_location(
    "asc_ab", pathlib.Path(__file__).with_name("asc_ab.py")
)
aa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(aa)


def test_per_level_delta_pairs_by_seed_and_asc():
    base = [
        {"seed": 1, "character": "ironclad", "ascension": 5, "won": False},
        {"seed": 2, "character": "ironclad", "ascension": 5, "won": False},
    ]
    new = [
        {"seed": 1, "character": "ironclad", "ascension": 5, "won": True},
        {"seed": 2, "character": "ironclad", "ascension": 5, "won": False},
    ]
    d = aa.per_level_delta(base, new)
    base_pct, new_pct, delta_pp, _p = d[5]
    # win-rate percentages are exact here (0/2, 1/2): equality is intentional
    assert base_pct == 0.0 and new_pct == 50.0 and delta_pp == 50.0  # noqa: RUF069
