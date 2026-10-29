#!/usr/bin/env python3
"""Tests for `extract_vfx_bindings.py` — the CLI wrapper and the committed
table it produces.

`vfx_bindings.py`'s per-file parsing is covered by `test_vfx_bindings.py`;
this file covers the whole-decomp walk, the `effects` map, and the two gates
design spec §1.4 calls for: a re-extraction diff (`build_table` on a fresh
decomp must equal the committed JSON byte-for-byte) and full coverage (every
row's effect resolves in the `effects` map).
"""

import json
import re
import unittest
from pathlib import Path

from scripts.extract_vfx_bindings import _SITE_PUBLISH_PATH, build_table

_STS2_WIKI = Path(__file__).resolve().parents[1]
_DECOMPILED = _STS2_WIKI / "decompiled" / "v0.107.1"
_TABLE_PATH = _STS2_WIKI / "data" / "v0.107.1" / "vfx_bindings.json"
_IMAGES = _SITE_PUBLISH_PATH.parents[1]  # site/public/images: `just import-game`


@unittest.skipUnless(_DECOMPILED.is_dir(), f"decomp dir not present ({_DECOMPILED})")
class ReExtractionGateTest(unittest.TestCase):
    """Design spec §1.4, gate 1: the table is committed so it can be
    reviewed; re-running the extractor against the same decomp must
    reproduce it exactly. Fails on a decomp change AND on a hand-edit of
    the committed JSON — those are the only two ways this can diverge.
    """

    def test_the_committed_table_matches_a_fresh_extraction(self) -> None:
        self.assertTrue(_TABLE_PATH.is_file(), f"missing committed table: {_TABLE_PATH}")
        fresh = build_table(_DECOMPILED)
        committed = json.loads(_TABLE_PATH.read_text())

        self.assertEqual(fresh["version"], committed["version"], "version field diverged")
        self.assertEqual(
            fresh["effects"], committed["effects"], "effects map diverged from a fresh extraction"
        )
        self.assertEqual(
            fresh["aliases"], committed["aliases"], "aliases diverged from a fresh extraction"
        )
        self.assertEqual(
            fresh["deathFade"],
            committed["deathFade"],
            "deathFade diverged from a fresh extraction",
        )

        # Bindings compared as a list (order matters and is deterministic,
        # see build_table's docstring) but the mismatch report below finds
        # the first differing row instead of dumping both 431-row lists,
        # per the task-6 brief's instruction that the gate's failure
        # message must say which rows differ.
        fresh_rows = fresh["bindings"]
        committed_rows = committed["bindings"]
        if fresh_rows != committed_rows:
            fresh_len, committed_len = len(fresh_rows), len(committed_rows)
            for i in range(min(fresh_len, committed_len)):
                if fresh_rows[i] != committed_rows[i]:
                    self.fail(
                        f"bindings diverged at row {i}: "
                        f"fresh={fresh_rows[i]!r} committed={committed_rows[i]!r} "
                        f"(fresh has {fresh_len} rows, committed has {committed_len})"
                    )
            self.fail(
                f"bindings diverged only in length: "
                f"fresh has {fresh_len} rows, committed has {committed_len}"
            )


@unittest.skipUnless(_TABLE_PATH.is_file(), f"committed table not present ({_TABLE_PATH})")
class PublishedCopyGateTest(unittest.TestCase):
    """The published copy under `site/public/` is the file the workbench
    frontend actually FETCHES (`sts2-pycli/web/src/effects/bindings.ts`); the
    `data/` copy is the one `ReExtractionGateTest` above ties to the decomp.
    Nothing joined the two, so the frontend could ship a stale table with every
    gate green.

    `main()` writes both from one `table` object in one run, which stops them
    diverging as long as the recipe is what produces them — but that is not the
    way this drifts. It drifts by a hand-edit to one file, a `git add` of one
    path, or a merge that resolves one side. Byte-identity is what catches all
    three, and chaining through the re-extraction gate above is what makes it a
    statement about the decomp rather than about two files agreeing.

    This is the fourth instance of one shape in this pipeline: a generated
    artifact committed alongside a gate that tests the generator and not its
    output. The manifest instance (`58db76a48`) shipped a dormant feature for
    several commits before a review caught it.
    """

    @unittest.skipUnless(_IMAGES.is_dir(), f"art tree not present ({_IMAGES})")
    def test_the_published_copy_matches_the_data_copy(self) -> None:
        self.assertTrue(
            _SITE_PUBLISH_PATH.is_file(),
            f"missing published copy: {_SITE_PUBLISH_PATH} — re-run "
            f"`just extract-vfx-bindings` and commit BOTH paths",
        )
        published = _SITE_PUBLISH_PATH.read_bytes()
        data = _TABLE_PATH.read_bytes()
        if published == data:
            return

        # Byte-identity is the assertion, but a byte diff is unreadable, so
        # name the structural difference when there is one — a re-serialisation
        # (indent, trailing newline) with identical content reads very
        # differently from a genuinely stale table.
        detail = ""
        try:
            pub_json = json.loads(published)
            data_json = json.loads(data)
        except json.JSONDecodeError as exc:
            detail = f" (published copy is not valid JSON: {exc})"
        else:
            if pub_json == data_json:
                detail = " — same content, different serialisation"
            else:
                differing = sorted(
                    k for k in set(pub_json) | set(data_json) if pub_json.get(k) != data_json.get(k)
                )
                detail = f" — differing top-level keys: {differing}"
        self.fail(
            f"{_SITE_PUBLISH_PATH} has drifted from {_TABLE_PATH}{detail}. "
            f"Re-run `just extract-vfx-bindings` and commit BOTH paths."
        )


@unittest.skipUnless(_TABLE_PATH.is_file(), f"committed table not present ({_TABLE_PATH})")
class CommittedTableTest(unittest.TestCase):
    def setUp(self) -> None:
        self.table = json.loads(_TABLE_PATH.read_text())

    def test_the_table_covers_the_effects_this_sub_project_ships(self) -> None:
        effects = {r["effect"] for r in self.table["bindings"]}
        self.assertIn("vfx_attack_blunt", effects)
        self.assertIn("vfx_spore_impact", effects)

    def test_every_row_names_a_known_effect(self) -> None:
        for row in self.table["bindings"]:
            self.assertIn(row["effect"], self.table["effects"], row)

    def test_a_zero_binding_effect_still_appears_in_the_effects_map(self) -> None:
        # vfx_sandy_impact: sandyImpactPath (VfxCmd.cs:59) has no call site
        # anywhere in the decomp, but it's one of the 13 shipped composites
        # and must not silently vanish from the table.
        self.assertIn("vfx_sandy_impact", self.table["effects"])
        self.assertEqual(self.table["effects"]["vfx_sandy_impact"], {"renderable": True})
        self.assertNotIn("vfx_sandy_impact", {r["effect"] for r in self.table["bindings"]})

    def test_deferred_effects_are_not_renderable_and_declare_their_fallback(self) -> None:
        for effect in ("vfx_heavy_blunt", "vfx_starry_impact"):
            with self.subTest(effect=effect):
                entry = self.table["effects"][effect]
                self.assertFalse(entry["renderable"])
                self.assertEqual(entry["fallback"], "vfx_attack_blunt")

    def test_every_non_renderable_effect_declares_a_renderable_fallback(self) -> None:
        # Task-6 review round 1: a `renderable: false` entry with no
        # `fallback` forces the renderer to invent a substitution at
        # runtime -- exactly what design spec §5's fallback chain exists to
        # avoid. This is the regression guard: every non-renderable effect
        # must name a fallback, and that fallback must itself be playable.
        for effect, entry in self.table["effects"].items():
            if entry["renderable"]:
                continue
            with self.subTest(effect=effect):
                self.assertIn("fallback", entry, f"{effect} has no declared fallback")
                fallback = entry["fallback"]
                self.assertIn(
                    fallback, self.table["effects"], f"{effect}'s fallback {fallback!r} unknown"
                )
                self.assertTrue(
                    self.table["effects"][fallback]["renderable"],
                    f"{effect}'s fallback {fallback!r} is itself not renderable",
                )

    def test_tints_are_normalised_to_hash_prefixed_hex(self) -> None:
        # Task-6 review round 1: Binding's docstring documents `#RRGGBB`;
        # the C# source mixes bare and `#`-prefixed literals, so every
        # tinted row must come out the same shape regardless of source form.
        tinted = [r for r in self.table["bindings"] if r["tint"] is not None]
        self.assertGreaterEqual(len(tinted), 10)
        for row in tinted:
            with self.subTest(row=row["owner"]):
                self.assertTrue(row["tint"].startswith("#"), row["tint"])

    def test_effect_keys_use_one_naming_convention(self) -> None:
        # Task-6 review round 1: every effects map key starts with `vfx_`
        # -- including the three whose real Godot resource name suffixes
        # `_vfx` instead (`vfx_spike_splash_vfx`, `vfx_stab_vfx`,
        # `vfx_kin_priest_grenade_vfx`), which keep the verbatim game name
        # with a `vfx_` prefix added rather than a fabricated rename.
        for effect in self.table["effects"]:
            with self.subTest(effect=effect):
                self.assertTrue(effect.startswith("vfx_"), effect)

    def test_shape_counts_match_the_reconciled_totals(self) -> None:
        # Pinned per task-6 brief context: task 5 (HEAD 44e8ad1af) produced
        # 431 bindings across four shapes, independently reconciled against
        # the decomp twice. Task 7's engine coverage gate
        # (`vfx_binding_coverage.rs`) then found one more real `hit_fx` site
        # this table was silently dropping: `VineShambler.cs:63`'s
        # `.WithHitFx("vfx/monsters/vine_shambler_vines/vine_shambler_vines_vfx")`
        # nests a directory under `vfx/`, which `_WITH_HIT_FX`'s original
        # `[a-zA-Z0-9_]+` character class could not match at all (it has no
        # way to reach the closing quote past the extra `/`s) — silent, not
        # an error, exactly the failure mode this whole gate exists to catch.
        # 432 (365+1 hit_fx) is the new reconciled pin. A deviation here means
        # this table build is dropping or duplicating sites, not that the pin
        # is stale.
        from collections import Counter

        shapes = Counter(r["shape"] for r in self.table["bindings"])
        self.assertEqual(len(self.table["bindings"]), 432)
        self.assertEqual(shapes["hit_fx"], 366)
        self.assertEqual(shapes["vfx_cmd"], 25)
        self.assertEqual(shapes["spawn"], 11)
        self.assertEqual(shapes["hit_vfx_node"], 30)

    def test_every_alias_resolves_to_a_row_the_table_really_has(self) -> None:
        # The aliases are published so BOTH consumers -- the Rust coverage
        # gate and the workbench renderer -- apply the same reconciliation.
        # An alias pointing at a row that does not exist would send both to
        # the same wrong place, so check the targets here rather than only
        # in the Rust gate's failure message.
        owners = {r["owner"] for r in self.table["bindings"]}
        aliases = self.table["aliases"]
        for engine, alias in aliases["monsters"].items():
            with self.subTest(monster=engine):
                self.assertTrue(
                    any(o.startswith(f"{alias['monster']}:") for o in owners),
                    f"{engine} aliases to {alias['monster']}, which owns no rows",
                )
                self.assertFalse(
                    any(o.startswith(f"{engine}:") for o in owners),
                    f"{engine} owns rows of its own -- it needs no monster alias",
                )
                self.assertTrue(alias["reason"])
        for key, alias in aliases["states"].items():
            with self.subTest(state=key):
                monster = key.split(":", 1)[0]
                self.assertIn(f"{monster}:{alias['state']}", owners)
                self.assertTrue(alias["reason"])

    def test_no_state_alias_restates_the_move_suffix_rule(self) -> None:
        # Both consumers already try `<id>_MOVE` and then the bare `<id>`.
        # An alias that only adds or drops that suffix is dead weight that
        # reads like a real rename.
        for key, alias in self.table["aliases"]["states"].items():
            engine_id = key.split(":", 1)[1]
            with self.subTest(state=key):
                self.assertNotEqual(alias["state"], f"{engine_id}_MOVE")
                self.assertNotEqual(alias["state"], engine_id)

    def test_effect_counts_match_the_spec_sanity_check(self) -> None:
        # design spec §1.1 / task-6 brief Step 5: vfx_attack_slash ~180,
        # vfx_attack_blunt ~115 -- explicitly approximate ("~"), so this
        # checks the same "no large deviation" band the brief describes
        # (sites being dropped), not an exact pin. The exact, reconciled
        # pin is the per-shape total in the test above (431 / 365-25-11-30).
        from collections import Counter

        counts = Counter(r["effect"] for r in self.table["bindings"])
        self.assertAlmostEqual(counts["vfx_attack_slash"], 180, delta=15)
        self.assertAlmostEqual(counts["vfx_attack_blunt"], 115, delta=15)


if __name__ == "__main__":
    unittest.main()


@unittest.skipUnless(_DECOMPILED.is_dir(), f"decomp dir not present ({_DECOMPILED})")
class DeathFadeCoverageTest(unittest.TestCase):
    """`deathFade` gates the dissolve on `NCreature.cs:1106`'s own condition, so
    a row missing from it is a monster that dissolves when the game leaves it
    alone — and the renderer has no way to notice.

    These tests deliberately re-derive the truth by scanning the decomp
    INDEPENDENTLY of `parse_death_fade`, rather than asserting the extractor
    agrees with itself. A gate that tests the generator and not its output is
    the exact shape that has now shipped stale artifacts four times in this
    pipeline (the manifest in #621, `vfx_bindings.json`'s published copy in
    #622); this is that lesson applied to a new table rather than re-learned on
    it.
    """

    @staticmethod
    def _classes_declaring(member: str) -> set[str]:
        """Every class in the WHOLE decomp that `override`s `member`.

        The whole tree, not just `Models.Monsters/` — `build_table` only walks
        that one directory, so a future build putting a monster subclass
        anywhere else would be invisible to it. This is what notices.
        """
        pattern = re.compile(rf"public\s+override\s+\w+\s+{member}\s*=>")
        found = set()
        for cs in _DECOMPILED.rglob("*.cs"):
            if pattern.search(cs.read_text(errors="replace")):
                found.add(cs.stem)
        return found

    def test_every_ShouldFadeAfterDeath_override_in_the_decomp_has_a_row(self) -> None:
        table = json.loads(_TABLE_PATH.read_text())["deathFade"]
        declared = self._classes_declaring("ShouldFadeAfterDeath")
        self.assertTrue(declared, "found no overrides at all — the scan itself is broken")
        with_fade = {k for k, v in table.items() if "fade" in v}
        self.assertEqual(
            declared,
            with_fade,
            "deathFade's `fade` rows do not match the decomp's ShouldFadeAfterDeath overrides",
        )

    def test_every_DeathAnimLengthOverride_in_the_decomp_has_a_row(self) -> None:
        table = json.loads(_TABLE_PATH.read_text())["deathFade"]
        declared = self._classes_declaring("DeathAnimLengthOverride")
        with_seconds = {k for k, v in table.items() if "deathAnimSeconds" in v}
        # A literal `0f` override would be declared but correctly absent from
        # the table (the game's own `> 0f` test), so this is a subset check with
        # the difference spelled out rather than an equality.
        self.assertTrue(
            with_seconds <= declared,
            f"deathFade claims a death wait for classes that declare none: "
            f"{with_seconds - declared}",
        )
        for name in declared - with_seconds:
            text = (next(_DECOMPILED.rglob(f"{name}.cs"))).read_text(errors="replace")
            self.assertRegex(
                text,
                r"DeathAnimLengthOverride\s*=>\s*0f?\s*;",
                f"{name} declares a death wait the table dropped",
            )

    def test_the_base_class_defaults_are_what_the_table_assumes(self) -> None:
        # Every absent monster is read as "fades, no death wait". That is only
        # right while `MonsterModel`'s virtuals say so — if a future build flips
        # either default, the whole table inverts meaning and nothing else here
        # would notice.
        text = (_DECOMPILED / "MegaCrit.Sts2.Core.Models" / "MonsterModel.cs").read_text()
        self.assertRegex(text, r"public\s+virtual\s+bool\s+ShouldFadeAfterDeath\s*=>\s*true;")
        self.assertRegex(text, r"public\s+virtual\s+float\s+DeathAnimLengthOverride\s*=>\s*0f;")

    def test_the_crab_arms_never_fade_and_wait_two_and_a_half_seconds(self) -> None:
        # The pair GH #633 was opened about. The issue asked for a group-level
        # dissolve for the Kaiser Crab's shared body; the decomp says neither
        # arm reaches `NMonsterDeathVfx` at all, so there was never a dissolve
        # to give it. Pinned by name because this is the claim the issue's
        # premise was rewritten around.
        table = json.loads(_TABLE_PATH.read_text())["deathFade"]
        for arm in ("Crusher", "Rocket"):
            self.assertEqual(table[arm], {"fade": "never", "deathAnimSeconds": 2.5}, arm)

    def test_the_one_conditional_row_carries_its_expression(self) -> None:
        table = json.loads(_TABLE_PATH.read_text())["deathFade"]
        conditional = {k: v for k, v in table.items() if v.get("fade") == "conditional"}
        for name, row in conditional.items():
            self.assertIn("condition", row, f"{name} is conditional but names no condition")
            self.assertTrue(row["condition"].strip(), name)
