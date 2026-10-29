"""Tests for the power-flash site extractor.

These run against the REAL decomp, not a fixture: the extractor's whole job is
to be exhaustive about a specific corpus, and a synthetic sample cannot fail
the way a missed regex does.
"""

import json
import re
import unittest
from pathlib import Path

from scripts.power_flash_sites import collect, merge, power_id

REPO = Path(__file__).resolve().parents[1]
DECOMP = REPO / "decompiled" / "v0.107.1"
TABLE = REPO / "data" / "v0.107.1" / "power_flash_sites.json"
needs_decomp = unittest.skipUnless(DECOMP.is_dir(), f"decomp dir not present ({DECOMP})")


class PowerIdTest(unittest.TestCase):
    def test_splits_camel_case_into_the_python_name_form(self):
        self.assertEqual(power_id("ThornsPower"), "THORNS")
        self.assertEqual(power_id("SlitheringStranglerPower"), "SLITHERING_STRANGLER")
        self.assertEqual(power_id("PaleBlueDotPower"), "PALE_BLUE_DOT")

    @needs_decomp
    def test_every_power_id_is_a_real_power_kind(self):
        # The Rust gate joins on `PowerKind::python_name()`. If the CamelCase
        # conversion ever stops reproducing it, the join silently drops sites
        # -- so it is checked against the generated Rust enum itself, not
        # against a hand-copied list.
        rust = REPO / ".." / "sts2-rs" / "crates" / "sts2-core" / "src"
        src = (rust / "power_ids_generated.rs").read_text()
        real = set(re.findall(r'Self::[A-Za-z0-9]+ => "([A-Z0-9_]+)"', src))
        self.assertGreater(len(real), 200, "failed to parse the PowerKind table")
        missing = sorted({s["power"] for s in collect(DECOMP)} - real)
        self.assertEqual(missing, [], f"power ids with no PowerKind: {missing}")


class CollectTest(unittest.TestCase):
    @needs_decomp
    def test_finds_every_flash_site_in_the_real_decomp(self):
        sites = collect(DECOMP)
        # Measured 2026-08-29: 90 calls across 89 classes (one class flashes
        # twice). A change here is either a decomp update or a regex that
        # started over- or under-matching; both are worth stopping for.
        self.assertEqual(len(sites), 90)
        self.assertEqual(len({s["cs_class"] for s in sites}), 89)

    @needs_decomp
    def test_thorns_is_found_at_its_real_line(self):
        thorns = [s for s in collect(DECOMP) if s["cs_class"] == "ThornsPower"]
        self.assertEqual([s["cs_line"] for s in thorns], [21])
        self.assertEqual(thorns[0]["power"], "THORNS")

    @needs_decomp
    def test_relic_and_ui_flashes_are_not_collected(self):
        # `RelicModel.Flash` flashes a RELIC, `NHandCardHolder.Flash` a card in
        # hand; neither reaches `NPowerFlashVfx`. Scoping to the powers
        # directory is what excludes them -- assert the exclusion holds rather
        # than trusting the directory scope silently.
        classes = {s["cs_class"] for s in collect(DECOMP)}
        self.assertTrue(all(c.endswith("Power") for c in classes))
        self.assertNotIn("RelicModel", classes)
        self.assertNotIn("NHandCardHolder", classes)

    def test_a_flash_inside_a_larger_expression_does_not_match(self):
        # The regex anchors on a bare `Flash();` statement. `GlowFlash();`
        # (NEpochCard.cs:301) is a different method whose name merely ends in
        # the same letters.
        self.assertFalse(re.match(r"^\s*Flash\(\);", "\t\tGlowFlash();"))


class MergeTest(unittest.TestCase):
    def test_re_extraction_keeps_what_has_already_been_ported(self):
        fresh = [
            {
                "power": "THORNS",
                "cs_class": "ThornsPower",
                "cs_line": 21,
                "ported": False,
                "rust": None,
                "reason": None,
            },
        ]
        existing = [
            {
                "power": "THORNS",
                "cs_class": "ThornsPower",
                "cs_line": 21,
                "ported": True,
                "rust": "hooks.rs:before_damage_received",
                "reason": None,
            },
        ]
        merged = merge(fresh, existing)
        self.assertTrue(merged[0]["ported"])
        self.assertEqual(merged[0]["rust"], "hooks.rs:before_damage_received")

    def test_a_site_that_vanished_from_the_cs_is_dropped_not_carried(self):
        # A `ported: true` row for a site the C# no longer has would keep the
        # gate's count steady while the thing it counts is gone.
        fresh: list[dict] = []
        existing = [
            {
                "power": "THORNS",
                "cs_class": "ThornsPower",
                "cs_line": 21,
                "ported": True,
                "rust": "hooks.rs",
                "reason": None,
            },
        ]
        self.assertEqual(merge(fresh, existing), [])

    def test_a_moved_line_is_treated_as_a_new_unported_site(self):
        # Keyed on (class, line): if the C# shifts, the row must not silently
        # inherit a `ported` mark that pointed at different code.
        fresh = [
            {
                "power": "THORNS",
                "cs_class": "ThornsPower",
                "cs_line": 99,
                "ported": False,
                "rust": None,
                "reason": None,
            },
        ]
        existing = [
            {
                "power": "THORNS",
                "cs_class": "ThornsPower",
                "cs_line": 21,
                "ported": True,
                "rust": "hooks.rs",
                "reason": None,
            },
        ]
        self.assertFalse(merge(fresh, existing)[0]["ported"])


@needs_decomp
class CommittedTableTest(unittest.TestCase):
    def test_the_committed_table_matches_a_fresh_extraction(self):
        # Same contract `test_extract_vfx_bindings` enforces: the committed
        # artifact and the decomp cannot drift apart unnoticed.
        self.assertTrue(
            TABLE.exists(), f"{TABLE} is missing -- run `just extract-power-flash-sites`"
        )
        committed = json.loads(TABLE.read_text())
        fresh = merge(collect(DECOMP), committed["sites"])
        self.assertEqual(committed["sites"], fresh)


if __name__ == "__main__":
    unittest.main()
