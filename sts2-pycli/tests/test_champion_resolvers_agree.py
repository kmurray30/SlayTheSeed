"""``data/ppo/champions.toml`` is the source of truth. These pin the rest to it.

Promotions used to mean hand-editing four independent champion lists, each with
a different selection rule, and the Python one *globbed*: it took the highest
iteration ``<N>`` present in whatever tree it ran in. The repo, the Cloud Run
image and the frozen advisor bundle are three different trees, so one resolver
returned three different champions and said nothing. PR #339 promoted Ironclad
``asc_it150``, left the Dockerfile ``COPY`` on ``asc_it55``, and the container
served a pre-honest-search net (measured clairvoyance rent -22.80pp) until #340
caught it. Nothing failed loudly: every list was self-consistent and the image
was valid.

Now nothing globs. The manifest names exact files; Python, the PyInstaller spec
and the taxonomy view read it; the Rust match arms and the Dockerfile ``COPY``
block are generated from it by ``scripts/sync_champions.py``. So the tests here
are no longer "did four humans agree" but:

* the generated regions byte-match what the generator produces (``--check``),
* every declared file exists and hashes as declared,
* a retired net on disk is inert -- it cannot outrank anything anymore,
* and the taxonomy view's deliberate lag is declared in the manifest rather
  than silently drifting.
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

import champions
import pytest
from workbench import ppo_policy

_PYCLI = Path(__file__).resolve().parents[1]
_SYNC = _PYCLI / "scripts" / "sync_champions.py"

_CHARACTERS = ["Ironclad", "Silent", "Defect", "Regent", "Necrobinder"]


def test_generated_regions_match_the_manifest() -> None:
    """`net.rs` and the Dockerfile must be what the generator would write.

    This is the whole promotion gate. It does not ask whether someone
    remembered to update the other lists -- it asks whether the committed bytes
    equal the generator's output, which forgetting cannot satisfy.
    """
    proc = subprocess.run(
        [sys.executable, str(_SYNC), "--check"],
        capture_output=True,
        text=True,
        cwd=_PYCLI,
        check=False,
    )
    assert proc.returncode == 0, (
        f"champion regions are stale:\n{proc.stdout}{proc.stderr}\n"
        f"Run `just sync-champions` and commit the result."
    )


def test_the_generated_rust_is_a_rustfmt_fixed_point() -> None:
    """`cargo fmt --check` and the sync gate must want the SAME bytes.

    They once did not, and the repo could satisfy neither. The generator
    emits one match arm per line, which happened to equal rustfmt's output
    only while every arm fit inside the line width. GH #545 gave Ironclad's
    arm a non-empty feature spec, it crossed the width, and from then on
    `cargo fmt` rewrapped it across four lines while the sync gate demanded
    the one-line form back -- committing either failed the other, on `main`,
    with no CI to say so.

    `_rustfmt` in the generator is the fix; this is the test that it stays
    fixed. Formatting the committed file must be a no-op, which is exactly
    the property the two gates were disagreeing about. Skipped rather than
    failed where rustfmt is unavailable, since then `cargo fmt --check` is
    not a gate either and there is nothing to disagree with.
    """
    net_rs = _PYCLI.parent / "sts2-rs" / "crates" / "sts2-runai" / "src" / "ppo" / "net.rs"
    committed = net_rs.read_text(encoding="utf-8")
    try:
        proc = subprocess.run(
            ["rustfmt", "--edition", "2021", "--emit", "stdout", "--quiet"],
            input=committed,
            capture_output=True,
            text=True,
            check=False,
        )
    except OSError:
        pytest.skip("rustfmt not installed")
    if proc.returncode != 0:
        pytest.skip(f"rustfmt failed: {proc.stderr.strip()}")
    assert proc.stdout == committed, (
        "net.rs is not rustfmt-clean, so `cargo fmt --check` and the champion "
        "sync gate disagree about it. Run `just sync-champions` (which now "
        "normalises through rustfmt) and commit the result."
    )


@pytest.mark.parametrize("character", _CHARACTERS)
def test_every_declared_champion_exists(character: str) -> None:
    """A manifest naming an absent file must raise, not quietly degrade.

    The old resolver's answer to a missing champion was "serve the next-newest
    file I can see". That is the #339 shape. Here it is a hard error.
    """
    resolved = ppo_policy.onnx_path(character)
    assert resolved is not None, f"{character} has no declared champion"
    assert Path(resolved).exists()


@pytest.mark.parametrize("character", _CHARACTERS)
def test_shipped_net_matches_its_declared_hash(character: str) -> None:
    """The playbook requires shipping the exact .onnx the gate swept.

    Re-exporting produces a byte-different artifact that no measurement covers.
    Nothing checked that before the manifest carried a hash.
    """
    mismatch = champions.verify(character)
    assert mismatch is None, mismatch


@pytest.mark.parametrize("character", _CHARACTERS)
def test_checkpoint_tracks_the_same_iteration(character: str) -> None:
    """`.pt` and `.onnx` are named per-entry, so they cannot drift apart.

    They used to be resolved by two independent `_newest()` globs, which could
    legitimately land on different iterations of the same stem.
    """
    onnx = ppo_policy.onnx_path(character)
    ckpt = ppo_policy.ckpt_path(character)
    assert onnx is not None and ckpt is not None
    assert Path(ckpt).stem == Path(onnx).stem, (
        f"{character}: checkpoint {Path(ckpt).name} is not the same iteration as "
        f"champion {Path(onnx).name}"
    )


def test_a_retired_net_on_disk_cannot_win() -> None:
    """The #339 hazard, asserted gone.

    Retired nets stay committed as paired-gate baselines, and several carry a
    HIGHER iteration number than the champion that replaced them (Necrobinder
    `it750` vs `asc_it300`, Regent `it800` vs `asc_it200`). Under the old
    highest-`<N>`-present rule those files outranked the real champion; under
    the manifest they are inert.
    """
    declared = {c.onnx for c in champions.all_champions().values()}
    on_disk = {p.name for p in champions.ppo_dir().glob("*.onnx")}
    retired = on_disk - declared
    assert retired, (
        "expected retired nets to still be committed as gate baselines; if they "
        "were deliberately deleted, this test no longer proves anything"
    )
    for character in _CHARACTERS:
        resolved = ppo_policy.onnx_path(character)
        assert resolved is not None
        assert Path(resolved).name not in retired


@pytest.mark.parametrize("character", _CHARACTERS)
def test_synergy_embed_geometry_agrees(character: str) -> None:
    """The card-embedding view must read the SAME net the agent plays with.

    `synergy.embed_geometry` used to keep its own hardcoded champion table --
    pinned by nothing -- and it rotted unnoticed across several promotions: as
    of 2026-08-08 it still named Ironclad `asc_it55` and Defect `asc_it80`, and
    had no Regent entry at all, so the geometry and control-matcher views
    described retired nets while claiming to describe "the deployed champion".
    Nothing failed; the numbers were simply about a different net. It now reads
    the manifest, and this pins it.
    """
    from synergy.embed_geometry import champion_onnx_path

    resolved = ppo_policy.onnx_path(character)
    assert resolved is not None
    assert Path(champion_onnx_path(character)).name == Path(resolved).name


def test_no_second_hardcoded_champion_table() -> None:
    """No module may pin a champion by full filename -- resolve it instead.

    This is the tripwire for the *class* of bug above rather than one instance
    of it. A fully-qualified filename with the iteration baked in
    (`ppo_fullrun_defect_asc_it230.onnx`) is a private copy of the manifest's
    answer: it goes stale at the next promotion with no test and no error to
    say so. The manifest itself is TOML, not Python, so it is not scanned.
    """
    import re

    rx = re.compile(r"ppo_fullrun_[A-Za-z0-9_]*it\d+\.(?:onnx|pt)")
    offenders: dict[str, list[str]] = {}
    python_root = _PYCLI / "python"
    for path in sorted(python_root.rglob("*.py")):
        hits = rx.findall(path.read_text(encoding="utf-8"))
        if hits:
            offenders[str(path.relative_to(_PYCLI))] = sorted(set(hits))
    assert not offenders, (
        "hardcoded champion filenames found — these silently go stale at the "
        f"next promotion:\n{offenders}\n"
        "Resolve via workbench.ppo_policy.onnx_path(character) instead."
    )
