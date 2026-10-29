"""Guard: every declared PPO champion ONNX must be shipped in the deploy image.

The Cloud Run image is torch-less, so the PPO off-combat brain resolves each
character's champion from a committed ONNX under ``data/ppo/``, by name, out of
``champions.toml``. If the Dockerfile does not ``COPY`` that exact file the
image simply lacks it and the character degrades to heuristic. This bit twice
(Ironclad asc_it55, then Silent it390) back when the ``COPY`` lines were
hand-kept.

They are generated now, so this is a belt-and-braces check on the *result*
rather than on the discipline: ``test_champion_resolvers_agree`` verifies the
generated region byte-matches the generator, and this one verifies the shipped
image would actually contain what the resolver will ask for -- including the
manifest itself, without which the container cannot name a champion at all.
"""

from __future__ import annotations

import os

import champions
from workbench import ppo_policy

_MONO_ROOT = champions.ppo_dir().parents[2]  # sts2-pycli/data/ppo -> monorepo root
_DOCKERFILE = _MONO_ROOT / "Dockerfile"


def test_dockerfile_ships_the_champion_manifest() -> None:
    assert _DOCKERFILE.is_file(), f"Dockerfile not found at {_DOCKERFILE}"
    assert champions.MANIFEST_NAME in _DOCKERFILE.read_text(encoding="utf-8"), (
        f"the image does not COPY data/ppo/{champions.MANIFEST_NAME}; without it "
        f"the deployed resolver cannot name any champion."
    )


def test_dockerfile_ships_every_declared_ppo_champion() -> None:
    assert _DOCKERFILE.is_file(), f"Dockerfile not found at {_DOCKERFILE}"
    dockerfile = _DOCKERFILE.read_text(encoding="utf-8")

    missing: list[str] = []
    checked: list[str] = []
    for character in champions.characters():
        path = ppo_policy.onnx_path(character)
        if path is None:
            # No declared champion for this character yet — nothing to ship.
            continue
        basename = os.path.basename(path)
        checked.append(basename)
        if basename not in dockerfile:
            missing.append(basename)

    assert checked, "no declared PPO champion ONNX found — resolver/manifest wiring broke"
    assert not missing, (
        "Dockerfile does not COPY these declared PPO champion ONNX files "
        f"(deployed image would degrade for that character): {missing}. "
        "Run `just sync-champions`."
    )


def test_dockerfile_ships_no_retired_net() -> None:
    """A leftover COPY is now only wasted image space, but still wrong.

    It used to be worse: the old resolver picked the highest iteration
    *present*, so a stale file shipped alongside the champion could outrank it.
    """
    dockerfile = _DOCKERFILE.read_text(encoding="utf-8")
    declared = {c.onnx for c in champions.all_champions().values()}
    stale = [
        p.name
        for p in champions.ppo_dir().glob("*.onnx")
        if p.name not in declared and p.name in dockerfile
    ]
    assert not stale, f"Dockerfile ships retired nets: {stale}. Run `just sync-champions`."
