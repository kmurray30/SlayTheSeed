"""The Harmony patches that keep the C# world out of the Godot scene tree must bind.

Every ``Patch*`` method in ``RunSimulator`` resolves its target by REFLECTION
against the decompiled game DLL (``GetMethod``/``GetProperty`` by name). When a
game update renames or re-signs one of those members, the lookup returns null,
the patch is silently skipped, and the only symptom is a run that throws again —
the exact failure this whole family exists to prevent, reintroduced invisibly.

Reflection failures are already reported on stderr; this test just makes them
fail the suite instead of scrolling past. See
``docs/experiments/2026-07-29-remaining-world-throws.md``.
"""

import pytest
from sts2_client import Game

# Banners whose absence means a headless-hostile deref is live again. Kept to
# the patches with a MEASURED corpus impact rather than every Patch* call, so a
# deliberate removal does not have to be re-litigated in two places.
# The member COUNTS are part of the banner on purpose. `PatchEventScreenShake`
# and `PatchTrialEventRoom` resolve N members by name and then hand out a
# non-null stub; a substring match that ignores N would pass while one renamed
# member left its REAL body running against a `GetUninitializedObject` receiver
# — strictly worse than the null-Instance short-circuit it replaced, and
# invisible. Both patches now refuse wholesale on a missing member, so an
# absent-or-changed banner is the signal; that only works if the count is
# asserted.
_REQUIRED_BANNERS = (
    "Patched NGame.Instance + 11 presentation member(s) (event screen-shake)",
    "Patched NEventRoom.Instance/.Layout + 6 presentation member(s) (Trial)",
    "Patched CrystalSphereMinigame.PlayMinigame",
    "Patched SoulNexus.AfterDeath",
    "Installed RewardsSet.testSelector",
)


@pytest.fixture
def patch_banners(capfd):
    """stderr from a game process that has initialised its patches.

    Takes ``capfd`` itself rather than leaving it to the test function: the
    banners are emitted during FIXTURE SETUP, and a ``capsys``/``capfd``
    requested only by the test starts capturing too late to see them.

    Function-scoped, so the C# process starts once per test — twice for the two
    assertions below. That is deliberate rather than an oversight: ``capfd`` is
    itself function-scoped, so a module- or session-scoped fixture could not
    request it, and a ~3s startup is cheaper than reimplementing the capture.
    """
    g = Game(forward_stderr=True)
    try:
        g.start(seed="patchbanner")
    finally:
        g.close()
        # Forces the drain thread to finish, so no line is still in flight.
        g.world_errors()
    return capfd.readouterr().err


class TestGodotStubPatches:
    def test_no_patch_target_went_missing(self, patch_banners):
        """No Harmony target failed to resolve.

        Anchored on two phrases the PATCH code owns. A bare ``"not found"``
        scan was tempting and wrong: the harness emits `[WARN] Localization dir
        not found:` and `[WARN] _smartFormatter field not found in LocManager`
        on perfectly healthy startups, and `AssetCache` logs `Asset not
        cached:` freely — so any new benign warning containing those words
        would fail this suite for a reason unrelated to Harmony. Every
        patch-side message uses `Could not find` instead, so the two families
        stay distinguishable by token rather than by luck.

        This test covers the SIBLING patches that install per-member. The five
        banners asserted below are the stronger guard for the stubs, because
        those refuse wholesale and their counts are pinned.
        """
        err = patch_banners
        failures = [
            ln for ln in err.splitlines() if "Failed to patch" in ln or "Could not find" in ln
        ]
        assert not failures, "Harmony patch target(s) no longer resolve:\n" + "\n".join(failures)

    def test_required_patches_installed(self, patch_banners):
        err = patch_banners
        missing = [b for b in _REQUIRED_BANNERS if b not in err]
        assert not missing, f"patch banner(s) absent from startup stderr: {missing}"
