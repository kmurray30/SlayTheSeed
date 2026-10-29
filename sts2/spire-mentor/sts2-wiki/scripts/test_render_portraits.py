"""`render_portraits.write_aliases`: the portrait copies under the workbench's names."""

import tempfile
import unittest
from pathlib import Path

from scripts.render_portraits import PART_PORTRAITS, write_aliases


class TestWriteAliases(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.monsters = Path(self._tmp.name)

    def png(self, name: str, data: bytes) -> None:
        (self.monsters / f"{name}.png").write_bytes(data)

    def read(self, name: str) -> bytes:
        return (self.monsters / f"{name}.png").read_bytes()

    def test_writes_a_missing_alias(self):
        self.png("torch_head_amalgam", b"new")
        self.assertEqual(write_aliases(self.monsters, {"torch_head_amalgam": "amalgam"}, set()), 1)
        self.assertEqual(self.read("amalgam"), b"new")

    def test_keeps_an_existing_alias_without_force(self):
        self.png("torch_head_amalgam", b"new")
        self.png("amalgam", b"old")
        write_aliases(self.monsters, {"torch_head_amalgam": "amalgam"}, set())
        self.assertEqual(self.read("amalgam"), b"old")

    def test_force_rewrites_the_copy_of_a_rerendered_portrait(self):
        self.png("torch_head_amalgam", b"new")
        self.png("amalgam", b"old")
        write_aliases(self.monsters, {"torch_head_amalgam": "amalgam"}, set(), force=True)
        self.assertEqual(self.read("amalgam"), b"new")

    def test_force_never_overwrites_an_alias_that_is_a_rig_of_its_own(self):
        # skulking_colony -> living_shield, and living_shield has its own rig.
        self.png("skulking_colony", b"colony")
        self.png("living_shield", b"shield")
        write_aliases(
            self.monsters, {"skulking_colony": "living_shield"}, {"living_shield"}, force=True
        )
        self.assertEqual(self.read("living_shield"), b"shield")

    def test_a_segmented_creature_gets_its_whole_creature_portrait(self):
        self.png("decimillipede_segment_front", b"head")
        write_aliases(self.monsters, PART_PORTRAITS, set())
        self.assertEqual(self.read("decimillipede"), b"head")


if __name__ == "__main__":
    unittest.main()
