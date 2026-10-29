"""`NCombatRoom.RandomizeEnemyScalesAndHues`, as the workbench reproduces it.

The game sizes every member of a group of two or more monsters of the same type
by where that instance's rolled max HP landed inside its `[MinInitialHp,
MaxInitialHp]` band, so three Exoskeletons at 26/24/25 HP are three visibly
different sizes. Without this the board drew them as identical clones.
"""

from __future__ import annotations

from typing import Any

import pytest
from workbench.server import _apply_instance_jitter

# Exoskeleton's real band (wiki manifest v0.107.1): 24..28 base, 25..29 from A8.
RANGES: dict[str, dict[str, int]] = {
    "Exoskeleton": {"min": 24, "max": 28, "min_asc": 25, "max_asc": 29, "hp_ascension": 8},
    "Toadpole": {"min": 21, "max": 25, "min_asc": 22, "max_asc": 26, "hp_ascension": 8},
}


def _enemy(kind: str, max_hp: int, box: float = 336.0) -> dict[str, Any]:
    return {"kind": kind, "max_hp": max_hp, "rig_bounds_w": box, "rig_bounds_h": box}


class TestGrouping:
    def test_a_lone_monster_is_drawn_at_its_authored_size(self) -> None:
        enemies = [_enemy("Exoskeleton", 28)]
        _apply_instance_jitter(enemies, RANGES)
        # No key at all, so the frontend reads the absence as 1.0.
        assert "size_jitter" not in enemies[0]

    def test_two_of_a_kind_are_both_sized(self) -> None:
        enemies = [_enemy("Exoskeleton", 28), _enemy("Exoskeleton", 24)]
        _apply_instance_jitter(enemies, RANGES)
        assert all("size_jitter" in e for e in enemies)

    def test_a_mixed_board_only_sizes_the_repeated_kind(self) -> None:
        enemies = [_enemy("Exoskeleton", 28), _enemy("Exoskeleton", 24), _enemy("Toadpole", 25)]
        _apply_instance_jitter(enemies, RANGES)
        assert "size_jitter" in enemies[0]
        assert "size_jitter" in enemies[1]
        assert "size_jitter" not in enemies[2]

    def test_an_unknown_kind_is_left_alone(self) -> None:
        enemies = [_enemy("NotAMonster", 28), _enemy("NotAMonster", 24)]
        _apply_instance_jitter(enemies, RANGES)
        assert all("size_jitter" not in e for e in enemies)


class TestScale:
    def test_only_the_above_median_half_of_the_band_grows(self) -> None:
        # `num` clamps at 0, so everything at or below the band's midpoint is
        # drawn at exactly its authored size — the variation is one-sided.
        enemies = [_enemy("Exoskeleton", hp) for hp in (24, 25, 26, 27, 28)]
        _apply_instance_jitter(enemies, RANGES)
        got = [e["size_jitter"] for e in enemies]
        assert got[0] == pytest.approx(1.0)
        assert got[1] == pytest.approx(1.0)
        assert got[2] == pytest.approx(1.0)
        assert 1.0 < got[3] < got[4]

    def test_the_top_of_the_band_takes_the_whole_envelope(self) -> None:
        # box >= 250 -> amount 0 -> envelope 0.10; num == 1 at the band's top.
        enemies = [_enemy("Exoskeleton", 28, box=336.0), _enemy("Exoskeleton", 24, box=336.0)]
        _apply_instance_jitter(enemies, RANGES)
        assert enemies[0]["size_jitter"] == pytest.approx(1.1)

    def test_a_small_creature_varies_more_than_a_big_one(self) -> None:
        # inverseLerp(250, 100, box): a rig at or under 100 gets the full 0.15.
        small = [_enemy("Exoskeleton", 28, box=100.0), _enemy("Exoskeleton", 24, box=100.0)]
        big = [_enemy("Exoskeleton", 28, box=250.0), _enemy("Exoskeleton", 24, box=250.0)]
        _apply_instance_jitter(small, RANGES)
        _apply_instance_jitter(big, RANGES)
        assert small[0]["size_jitter"] == pytest.approx(1.15)
        assert big[0]["size_jitter"] == pytest.approx(1.1)

    def test_a_fixed_hp_monster_takes_no_jitter(self) -> None:
        ranges = {
            "Boss": {"min": 100, "max": 100, "min_asc": 100, "max_asc": 100, "hp_ascension": 8}
        }
        enemies = [_enemy("Boss", 100), _enemy("Boss", 100)]
        _apply_instance_jitter(enemies, ranges)
        assert all(e["size_jitter"] == pytest.approx(1.0) for e in enemies)

    def test_an_ascension_roll_outside_the_base_band_uses_the_ascension_band(self) -> None:
        # 29 is above the base band (24..28) and is the TOP of the A8 band, so
        # it must read as the top of its band, not as an out-of-range clamp.
        enemies = [_enemy("Exoskeleton", 29), _enemy("Exoskeleton", 25)]
        _apply_instance_jitter(enemies, RANGES)
        assert enemies[0]["size_jitter"] == pytest.approx(1.1)
        assert enemies[1]["size_jitter"] == pytest.approx(1.0)


class TestWireSpelling:
    def test_the_bridge_s_screaming_snake_kind_groups_too(self) -> None:
        # SpireMentor spells the kind SCREAMING_SNAKE; grouping must see those two
        # as the same monster or a live run gets no jitter at all.
        enemies = [_enemy("EXOSKELETON", 28), _enemy("Exoskeleton", 24)]
        _apply_instance_jitter(enemies, RANGES)
        assert all("size_jitter" in e for e in enemies)


class TestHue:
    def test_hue_is_stable_across_calls(self) -> None:
        # The game's own hue comes from `Rng.Chaotic` (wall-clock seeded) and is
        # not reproducible; what MUST hold is that a replayed step does not
        # shimmer, i.e. the same board renders the same tint every time.
        first = [_enemy("Exoskeleton", 28), _enemy("Exoskeleton", 24)]
        second = [_enemy("Exoskeleton", 28), _enemy("Exoskeleton", 24)]
        _apply_instance_jitter(first, RANGES)
        _apply_instance_jitter(second, RANGES)
        assert [e["hue_jitter"] for e in first] == [e["hue_jitter"] for e in second]
        assert all(0.0 <= e["hue_jitter"] <= 0.05 for e in first)
