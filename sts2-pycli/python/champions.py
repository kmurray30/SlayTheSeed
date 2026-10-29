"""The deployed PPO champion nets, read from ``data/ppo/champions.toml``.

That file is the single source of truth; this module is the only thing that
parses it. Everything that serves or analyzes a champion goes through here --
:mod:`workbench.ppo_policy` (both inference backends), the frozen advisor exe's
PyInstaller spec, :mod:`synergy.embed_geometry`, and the generator that keeps
the Rust match arms and the Dockerfile ``COPY`` lines in sync.

**Why a manifest and not a glob.** The previous resolver globbed
``<stem>*.onnx`` and took the highest iteration ``<N>`` *present in whatever
tree it ran in*. The repo checkout, the Cloud Run image and the frozen advisor
bundle are three different trees holding three different subsets of the
committed nets, so one function returned three different champions and said
nothing about it. PR #339 shipped a retired Ironclad net that way, and a
leftover retired file with a higher ``<N>`` could outrank the real champion.
Naming the exact file makes champion identity tree-independent: a stale net on
disk is inert, and a *missing* champion raises instead of quietly demoting to
whatever older net happens to be lying around.

Paths resolve through :mod:`runtime_paths`, so this works unchanged inside the
packaged advisor exe.
"""

from __future__ import annotations

import hashlib
import tomllib
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

import runtime_paths

MANIFEST_NAME = "champions.toml"


def ppo_dir() -> Path:
    """Directory holding the committed nets and the manifest."""
    return runtime_paths.data_root() / "data" / "ppo"


def manifest_path() -> Path:
    return ppo_dir() / MANIFEST_NAME


@dataclass(frozen=True)
class Champion:
    """One character's deployed net, exactly as the manifest names it."""

    character: str
    """Normalized (lowercased) character key."""
    onnx: str
    """Filename of the served ONNX export."""
    ckpt: str | None
    """Filename of the torch checkpoint, when one is committed."""
    sha256: str
    """Hash of the ONNX *as shipped* -- the playbook forbids re-exporting."""
    gate: str
    """One line on what promoted it; full story in ``<net>.provenance.md``."""
    features: str
    """Encoder features this net was TRAINED with (``ppo.schema``'s
    ``STS2_PPO_FEATURES`` names, comma-separated). Empty = the default schema
    -- every currently committed champion. Read by `OnnxPolicy` at serve time
    so a champion is never handed a column it never trained on (GH #545)."""

    @property
    def onnx_path(self) -> Path:
        return ppo_dir() / self.onnx

    @property
    def ckpt_path(self) -> Path | None:
        return ppo_dir() / self.ckpt if self.ckpt else None


def normalize(character: str | None) -> str:
    return (character or "ironclad").strip().lower()


@lru_cache(maxsize=1)
def _load(manifest: str) -> dict[str, Champion]:
    raw = tomllib.loads(Path(manifest).read_text(encoding="utf-8"))
    out: dict[str, Champion] = {}
    for character, entry in raw.items():
        if not isinstance(entry, dict):
            raise ValueError(f"{MANIFEST_NAME}: [{character}] must be a table")
        missing = {"onnx", "sha256"} - set(entry)
        if missing:
            raise ValueError(f"{MANIFEST_NAME}: [{character}] is missing {sorted(missing)}")
        out[normalize(character)] = Champion(
            character=normalize(character),
            onnx=entry["onnx"],
            ckpt=entry.get("ckpt"),
            sha256=entry["sha256"],
            gate=entry.get("gate", ""),
            features=entry.get("features", ""),
        )
    return out


def all_champions() -> dict[str, Champion]:
    """Every declared champion, keyed by normalized character."""
    path = manifest_path()
    if not path.exists():
        raise FileNotFoundError(
            f"champion manifest not found: {path}. It is the source of truth for "
            f"every PPO champion; a build that lost it cannot serve one."
        )
    return _load(str(path))


def characters() -> list[str]:
    """Normalized character keys with a declared champion, sorted."""
    return sorted(all_champions())


def get(character: str) -> Champion | None:
    """The declared champion for *character*, or ``None`` if it has none.

    ``None`` means "no net was ever trained for this character" (Watcher has no
    card pool yet). It never means "the file is missing" -- that is a hard error
    from :func:`onnx_path`, because a silently absent champion is the failure
    this manifest exists to prevent.
    """
    return all_champions().get(normalize(character))


def reset_cache() -> None:
    """Drop the parsed manifest (tests that point the data dir elsewhere)."""
    _load.cache_clear()


def _resolve(character: str, filename: str, kind: str) -> str:
    path = ppo_dir() / filename
    if not path.exists():
        raise FileNotFoundError(
            f"{MANIFEST_NAME} declares {kind} {filename!r} for {character!r}, but "
            f"{path} does not exist. Commit the net, or correct the manifest -- do "
            f"NOT let the lane fall back to an older one."
        )
    return str(path)


def by_onnx_filename(filename: str) -> Champion | None:
    """The declared champion whose ``onnx`` file IS *filename*, or ``None``.

    Serving backends (`ppo.onnx_policy.OnnxPolicy`) are constructed from a
    model *path*, not a character key -- a workbench cache keeps one instance
    per character but the advisor exe and ad-hoc scripts can point at any
    ``.onnx`` on disk. Matching lets a champion-manifest entry (e.g. its
    declared `features`) be recovered from that path (GH #545).

    Requires the resolved path to actually live in `ppo_dir()`, not merely
    share a champion's basename. A basename-only match would let any file
    that happens to be named the same as a champion (a scratch export
    elsewhere, a copy under /tmp) silently inherit that champion's declared
    `features` -- exactly the "served at a schema it wasn't trained at"
    failure this manifest field exists to prevent, just moved from the
    env-var layer down to the path layer. A path outside the champions
    directory returns ``None`` (default/empty feature set), which is the
    safe direction for an unrecognized model.
    """
    path = Path(filename).resolve()
    if path.parent != ppo_dir().resolve():
        return None
    for champ in all_champions().values():
        if champ.onnx == path.name:
            return champ
    return None


def onnx_path(character: str) -> str | None:
    """Absolute path to *character*'s champion ONNX, or ``None`` if untrained.

    Raises:
        FileNotFoundError: the manifest names a file that is not on disk.
    """
    champ = get(character)
    if champ is None:
        return None
    return _resolve(champ.character, champ.onnx, "onnx")


def ckpt_path(character: str) -> str | None:
    """Absolute path to *character*'s torch checkpoint, or ``None``.

    ``None`` covers both "no champion" and "champion ships ONNX only".

    Raises:
        FileNotFoundError: the manifest names a checkpoint that is not on disk.
    """
    champ = get(character)
    if champ is None or champ.ckpt is None:
        return None
    return _resolve(champ.character, champ.ckpt, "ckpt")


def sha256_of(path: str | Path) -> str:
    """Streaming SHA-256 of a file (the nets are ~8 MB each)."""
    digest = hashlib.sha256()
    with Path(path).open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify(character: str) -> str | None:
    """Check the shipped ONNX against its declared hash.

    Returns ``None`` when it matches (or the character has no champion), else a
    human-readable mismatch description. Called from the test suite and the exe
    build -- not from the serving path, where hashing 5 x 8 MB on every start
    would buy nothing a build-time check does not.
    """
    champ = get(character)
    if champ is None:
        return None
    path = onnx_path(character)
    if path is None:
        return None
    actual = sha256_of(path)
    if actual == champ.sha256:
        return None
    return (
        f"{champ.onnx} hashes {actual}, but {MANIFEST_NAME} declares "
        f"{champ.sha256}. Either the file was re-exported (ship the exact .onnx "
        f"the gate swept) or the manifest is stale."
    )
