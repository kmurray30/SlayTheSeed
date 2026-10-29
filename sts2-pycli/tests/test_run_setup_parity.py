"""item#5: run-setup (map) parity path-enumeration helpers.

The cross-engine comparison hinges on reducing two differently-shaped map dumps
(C# PascalCase types + separate boss + no ancient node; Rust snake_case +
inline boss + leading ancient node) to the same coordinate-independent
invariant: the SET of root->boss room-type paths. These tests pin the type
canonicalization, the C#/Rust path enumeration, and the ancient-strip so the
two sides are actually comparable.
"""

from __future__ import annotations

import run_setup_parity as rsp


def test_canon_type_pascal_and_snake_agree() -> None:
    assert rsp._canon_type("RestSite") == rsp._canon_type("rest_site") == "rest_site"
    assert rsp._canon_type("Monster") == "monster"
    assert rsp._canon_type("Boss") == "boss"


def test_csharp_paths_simple_diamond() -> None:
    # row1: (0,1) monster -> (0,2) elite, (1,2) shop ; both -> boss (0,3)
    m = {
        "rows": [
            [
                {
                    "col": 0,
                    "row": 1,
                    "type": "Monster",
                    "children": [{"col": 0, "row": 2}, {"col": 1, "row": 2}],
                }
            ],
            [
                {"col": 0, "row": 2, "type": "Elite", "children": [{"col": 0, "row": 3}]},
                {"col": 1, "row": 2, "type": "Shop", "children": [{"col": 0, "row": 3}]},
            ],
        ],
        "boss": {"col": 0, "row": 3, "type": "Boss"},
    }
    assert rsp._csharp_paths(m) == {
        ("monster", "elite", "boss"),
        ("monster", "shop", "boss"),
    }


def test_rust_paths_strip_ancient_matches_csharp() -> None:
    # Rust includes a leading ancient node (row 0) + inline boss (top row).
    am = [
        {"col": 0, "row": 0, "point_type": "ancient", "children": [(0, 1)]},
        {"col": 0, "row": 1, "point_type": "monster", "children": [(0, 2), (1, 2)]},
        {"col": 0, "row": 2, "point_type": "elite", "children": [(0, 3)]},
        {"col": 1, "row": 2, "point_type": "shop", "children": [(0, 3)]},
        {"col": 0, "row": 3, "point_type": "boss", "children": []},
    ]
    # ancient prefix stripped → identical to the C# diamond above
    assert rsp._rust_paths(am) == {
        ("monster", "elite", "boss"),
        ("monster", "shop", "boss"),
    }


def test_seed_for_is_deterministic_and_distinct() -> None:
    seeds = [rsp._seed_for(i) for i in range(50)]
    assert len(set(seeds)) == 50  # distinct
    assert rsp._seed_for(0) == rsp._seed_for(0)  # deterministic
    assert all(s.startswith("PAR") for s in seeds)
