"""Refuse to train on anything but a fresh PGO ``sts2_rs`` wheel, and keep a
run on the wheel it was checked on.

PPO collection burns its CPU inside the ``sts2_rs`` extension, and the PGO
build of that extension (``just sim-engine-pgo``, i.e.
``sts2-rs/scripts/pgo_wheel.sh``) makes the same decisions as the plain one --
the script re-checks that on 50 full runs every build -- while collecting
about 1.28x faster (trainer collection path, 250/1000 sims). Nothing used to
check which wheel a campaign ran on, and the plain one is what ``just
sim-engine`` / ``maturin develop`` / ``uv sync`` install. So the training entry
points (``ppo.trainer``'s CLI, ``ppo.screen``) run :func:`check_training_wheel`
(through :func:`prepare_training_wheel`) before they start, and it refuses
unless the imported wheel is

* **fresh**: compiled from the current source of every crate linked into it,
  the same content-addressed ``src_hash`` / ``recompute_src_hash`` comparison
  as ``parity_audit.assert_wheel_fresh`` (but not bypassable by
  ``STS2_SKIP_WHEEL_CHECK``: a campaign on a stale engine is wasted),
* **verified**: not imported from one of ``pgo_wheel.sh``'s own build dirs
  (``target-pgo-wheel-*``), whose PGO module reports ``pgo`` before (or, under
  ``--skip-identity``, without) passing the identity check, and not the
  instrumented build, and
* **PGO**: ``sts2_rs.build_info()["pgo"]``, which only ``pgo_wheel.sh``'s
  profile-use build stamps.

A host that cannot build PGO (no ``llvm-tools``) can opt out with
``--allow-plain-wheel`` or ``STS2_ALLOW_PLAIN_WHEEL=1``; the run then logs a
loud warning and records ``wheel_pgo: false``. Freshness has no opt-out. The
returned record goes into the run's checkpoints and wandb config, and each
``stats.jsonl`` row says what that iteration's workers ran (``wheel``,
``wheel_pgo``), so every campaign states which wheel it trained on.

**Pinning.** A check at startup does not hold for a whole campaign:
``play_batch`` opens a new spawn pool every iteration, whose workers import
``sts2_rs`` from disk again, and the venv's ``.so`` does get replaced under a
running campaign (another session's ``uv run`` / ``just test``, ``just
sim-engine`` after a pull). So the entry points call
:func:`prepare_training_wheel`, which copies the wheel into a private,
content-addressed pin dir (``<run dir>/sts2_rs-pin/<sha256[:16]>/``,
:func:`pin_wheel`) and re-executes the process with that dir first on
``PYTHONPATH``. The check then runs on the pinned copy, and everything the run
starts imports that copy: the trainer's spawn workers (which inherit the
parent's ``sys.path``), and the screen's arm trainers and evals (which inherit
``PYTHONPATH``). Each worker returns :func:`wheel_identity` with every run, and
``train()`` aborts an iteration whose workers ran anything but the build the
run started on (:func:`worker_wheel_mismatch`). That is the backstop for a run
the pin does not cover, such as ``train()`` called directly, or any run on
Windows, which does not pin (:data:`PIN_SUPPORTED`).

The pin freezes only the extension. The workers import the first-party Python
(``python/ppo``, ``champions.py``) from the checkout on disk every iteration,
so a pull, checkout or edit mid-campaign changes the code collection runs.
Each worker therefore also reports :func:`python_source_digest`, and
``train()`` aborts an iteration whose workers ran other source than the run
started with (:func:`worker_source_mismatch`). That is a source change, not an
``sts2_rs`` build change, and the abort says so.

The guard and the pin live in the CLI entry points, not in ``train()``: the
tests call ``train()`` directly on a plain worktree wheel, where the
per-iteration check compares the workers against the test process's import.

``python -m ppo.wheel_guard [--allow-plain-wheel]`` runs the check alone and
prints the record (exit 1 on refusal) -- a pre-flight before a launch.
"""

from __future__ import annotations

import functools
import hashlib
import importlib.machinery
import importlib.util
import itertools
import json
import os
import shlex
import shutil
import sys
import tempfile
import time
from pathlib import Path
from typing import Any, NoReturn

ALLOW_PLAIN_ENV = "STS2_ALLOW_PLAIN_WHEEL"
FIX = "just sim-engine-pgo"
# Trainer collection path (play_batch, policy=net, 250/1000 sims, Ironclad, one
# pinned core): 31.2 s plain -> 24.3 s PGO.
PGO_COLLECTION_SPEEDUP = 1.28
# Where a run keeps its pinned copies, next to its checkpoints.
PIN_DIRNAME = "sts2_rs-pin"
# Set by `prepare_training_wheel` on the re-executed process. Every process of
# that run (the screen's arm trainers too) must then import from exactly this
# pin dir.
PIN_ENV = "STS2_WHEEL_PIN"
_PIN_RECORD = "pin.json"
# The pin restarts the process on the copy, which needs a real exec. Windows
# has none: `os.execve` there starts the command as a new process and exits
# this one, so the shell (or the screen waiting on an arm's trainer) would see
# the run end at once. A Windows run is checked but not pinned; the
# per-iteration check is what keeps it on one build.
PIN_SUPPORTED = os.name != "nt"
# pgo_wheel.sh's build dirs under sts2-rs (`target-pgo-wheel-stage/`,
# `-unverified/`, ...). A PGO module there has not passed the identity check.
_BUILD_DIR_PREFIX = "target-pgo-wheel"
# What a process loaded, as far as the run's behaviour and speed are concerned.
# Not `build_git`: two builds of one engine source at different commits are the
# same engine.
IDENTITY_KEYS = ("pgo", "pgo_instrumented", "profdata_sha256", "target_cpu", "src_hash", "so_path")
# The first-party Python a collection worker imports from disk: `python/` (this
# package's parent) holds `ppo/` and the modules it imports outside it.
_PY_ROOT = Path(__file__).resolve().parents[1]
_FIRST_PARTY_EXTRA = ("champions.py",)
# Files a pin never needs: bytecode caches, and the temp names pgo_wheel.sh
# copies a .so under before renaming it (a SIGKILL can leave one behind).
_PIN_IGNORE = ("__pycache__", "*.pgo-tmp", "*.tmp")


def default_rs_root() -> Path:
    """The sts2-rs checkout this ``sts2-pycli`` belongs to (``$STS2_RS_SRC`` wins,
    as in ``scripts/wheel-venv.sh``)."""
    env = os.environ.get("STS2_RS_SRC")
    if env:
        return Path(env)
    return Path(__file__).resolve().parents[3] / "sts2-rs"


def wheel_build_info() -> dict[str, Any]:
    """``sts2_rs.build_info()``, or a plain record for a wheel that predates it."""
    import sts2_rs  # type: ignore[import-not-found]

    fn = getattr(sts2_rs, "build_info", None)
    info: dict[str, Any] = dict(fn()) if fn is not None else {"pgo": False}
    # maturin installs a package whose `__init__.py` re-exports the extension
    # submodule; a bare `.so` on PYTHONPATH is the module itself.
    ext = getattr(sts2_rs, "sts2_rs", sts2_rs)
    info["so_path"] = getattr(ext, "__file__", None)
    return info


def wheel_staleness(rs_root: Path) -> str | None:
    """None when the imported wheel was compiled from the source at ``rs_root``,
    else the reason it was not (or cannot be shown to be)."""
    import sts2_rs  # type: ignore[import-not-found]

    if not (rs_root / "crates").is_dir():
        return f"no sts2-rs source at {rs_root} to check the wheel against"
    baked = getattr(sts2_rs, "src_hash", None)
    recompute = getattr(sts2_rs, "recompute_src_hash", None)
    if baked is None or recompute is None:
        return "the installed sts2_rs predates the src_hash stamp"
    stamped, current = baked(), recompute(str(rs_root))
    if stamped != current:
        return f"loaded .so src_hash = {stamped}, on-disk source hash = {current}"
    return None


# How a refusal names the venv's interpreter on this platform.
_VENV_PYTHON = r".venv\Scripts\python" if os.name == "nt" else ".venv/bin/python"


def pgo_unbuildable(rs_root: Path) -> str | None:
    """None when this host and checkout can run ``just sim-engine-pgo``, else
    why not (the opt-out is then the only way to train)."""
    if os.name == "nt":
        return "the PGO build (scripts/pgo_wheel.sh) is bash-only and does not run on Windows"
    script = rs_root / "scripts" / "pgo_wheel.sh"
    if not script.is_file():
        return f"this checkout has no PGO build script ({script})"
    return None


def allow_plain_requested(flag: bool) -> bool:
    """The CLI flag, or ``STS2_ALLOW_PLAIN_WHEEL`` (which also reaches the
    trainer processes ``ppo.screen`` launches)."""
    return flag or os.environ.get(ALLOW_PLAIN_ENV, "").lower() in ("1", "true", "yes")


def check_training_wheel(
    *, allow_plain: bool = False, rs_root: Path | None = None, warn: bool = True
) -> dict:
    """Return the wheel record for the run's metadata, or raise ``SystemExit``
    naming the fix.

    Args:
        allow_plain: the ``--allow-plain-wheel`` flag (``STS2_ALLOW_PLAIN_WHEEL``
            is honoured either way).
        rs_root: the sts2-rs checkout to check freshness against; defaults to
            :func:`default_rs_root`.
        warn: print the plain-wheel warning when the opt-out admits one. Off
            only for the pre-pin check, whose process re-executes and checks
            (and warns) again.
    """
    rs_root = rs_root if rs_root is not None else default_rs_root()
    allow_plain = allow_plain_requested(allow_plain)
    info = wheel_build_info()
    stale = wheel_staleness(rs_root)
    if stale is not None:
        raise SystemExit(
            "REFUSING TO TRAIN: the imported sts2_rs was not compiled from the current "
            f"source.\n  {stale}\n  wheel:  {info.get('so_path')}\n  source: {rs_root}\n"
            f"Rebuild it (from sts2-pycli):  {FIX}\n"
            "(A host that cannot build PGO: `just sim-engine`, then train with "
            f"--allow-plain-wheel.) Run the trainer as `{_VENV_PYTHON} -m ppo.trainer`, "
            "not `uv run`: after a Rust source change uv's env sync installs a plain build."
        )
    so_path = info.get("so_path")
    if info.get("pgo_instrumented") or (
        so_path and any(p.startswith(_BUILD_DIR_PREFIX) for p in Path(so_path).parts)
    ):
        raise SystemExit(
            "REFUSING TO TRAIN: the imported sts2_rs is one of pgo_wheel.sh's own builds "
            "(an instrumented one, or a PGO one that has not passed its identity check).\n"
            f"  wheel: {so_path}\n"
            "Drop it from PYTHONPATH and train on the venv's install, which pgo_wheel.sh "
            f"only replaces after the check passes. Build that with:  {FIX}"
        )
    if not info.get("pgo"):
        if not allow_plain:
            head = (
                "REFUSING TO TRAIN: the imported sts2_rs is a plain build "
                '(build_info()["pgo"] is false).\n'
                f"  wheel: {info.get('so_path')}\n"
                "The PGO wheel makes the same decisions (pgo_wheel.sh re-checks 50 full "
                f"runs) and collects ~{PGO_COLLECTION_SPEEDUP}x faster.\n"
            )
            cannot = pgo_unbuildable(rs_root)
            if cannot is not None:
                raise SystemExit(
                    head + f"This host cannot build it: {cannot}.\n"
                    f"Train on the plain wheel explicitly: --allow-plain-wheel or "
                    f"{ALLOW_PLAIN_ENV}=1 (same decisions, slower collection)."
                )
            raise SystemExit(
                head + f"Build it (from sts2-pycli):  {FIX}\n"
                "A host that cannot build PGO (no `rustup component add llvm-tools`) can "
                f"opt out explicitly: --allow-plain-wheel or {ALLOW_PLAIN_ENV}=1"
            )
        if warn:
            bar = "!" * 72
            print(
                f"\n{bar}\n"
                "WARNING: TRAINING ON A PLAIN (non-PGO) sts2_rs WHEEL — "
                f"--allow-plain-wheel / {ALLOW_PLAIN_ENV}=1.\n"
                f"  wheel: {info.get('so_path')}\n"
                f"Collection runs ~{PGO_COLLECTION_SPEEDUP}x slower than on the PGO wheel. "
                "This run records wheel_pgo=false.\n"
                f"Build the PGO wheel with `{FIX}` (from sts2-pycli) when the host can.\n{bar}",
                file=sys.stderr,
                flush=True,
            )
    return {**info, "wheel_pgo": bool(info.get("pgo")), "allow_plain_wheel": allow_plain}


def wheel_identity(info: dict | None = None) -> dict:
    """The :data:`IDENTITY_KEYS` of a wheel record, by default of this process's
    own ``sts2_rs`` import. The stamps are compiled into the loaded code, so they
    describe what is running even if the file on disk has been replaced since."""
    info = wheel_build_info() if info is None else info
    return {k: info.get(k) for k in IDENTITY_KEYS}


def engine_change(before: dict | None, after: dict | None) -> str | None:
    """How the build in wheel record ``after`` differs from ``before`` (say, a
    resumed run's checkpoint), or None when it is the same build or either
    record is missing. Where the file lives does not count: a resumed run pins
    anew."""
    if before is None or after is None:
        return None
    moved = [
        f"{k} {before.get(k)} -> {after.get(k)}"
        for k in IDENTITY_KEYS
        if k != "so_path" and before.get(k) != after.get(k)
    ]
    return ", ".join(moved) or None


def worker_wheel_mismatch(rows: list[dict], expected: dict) -> str | None:
    """None when every collected run reports the ``expected`` identity, else
    what the workers ran instead.

    ``workers.play_one`` adds ``wheel`` to every row. A row that has
    transitions but no ``wheel`` counts as a mismatch, so a training row cannot
    slip past unchecked. Error and timeout rows that never reached the engine
    carry neither and are skipped.
    """
    seen: dict[str, int] = {}
    for row in rows:
        if "wheel" not in row and not row.get("transitions"):
            continue
        got = row.get("wheel")
        if got != expected:
            key = json.dumps(got, sort_keys=True)
            seen[key] = seen.get(key, 0) + 1
    if not seen:
        return None
    return "\n".join(f"  {n} run(s) ran {key}" for key, n in seen.items())


def first_party_sources(root: Path = _PY_ROOT) -> list[Path]:
    """The first-party Python files a collection worker imports from disk:
    every ``ppo/**/*.py`` under ``root`` plus ``champions.py``, sorted."""
    ppo = [p for p in (root / "ppo").rglob("*.py") if "__pycache__" not in p.parts]
    return sorted([*ppo, *(root / name for name in _FIRST_PARTY_EXTRA)])


def source_digest(root: Path = _PY_ROOT) -> str:
    """sha256[:16] over the relative paths and bytes of
    :func:`first_party_sources` (a missing file hashes as absent)."""
    h = hashlib.sha256()
    for path in first_party_sources(root):
        h.update(path.relative_to(root).as_posix().encode() + b"\0")
        try:
            data = path.read_bytes()
        except OSError:
            h.update(b"\xff")
            continue
        h.update(len(data).to_bytes(8, "little") + data)
    return h.hexdigest()[:16]


@functools.cache
def python_source_digest() -> str:
    """This process's :func:`source_digest`, taken once: at the first call,
    which ``ppo.workers`` makes at import, so it describes the source the
    process loaded, not what the files hold later."""
    return source_digest()


def worker_source_mismatch(rows: list[dict], expected: str) -> str | None:
    """None when every collected run reports the ``expected``
    :func:`python_source_digest` (``py_src``), else what the workers ran
    instead. Rows are skipped and counted as :func:`worker_wheel_mismatch`
    does."""
    seen: dict[str | None, int] = {}
    for row in rows:
        if "py_src" not in row and not row.get("transitions"):
            continue
        got = row.get("py_src")
        if got != expected:
            seen[got] = seen.get(got, 0) + 1
    if not seen:
        return None
    return "\n".join(f"  {n} run(s) ran py_src {key}" for key, n in seen.items())


def _sha256(path: Path) -> str:
    with open(path, "rb") as f:
        return hashlib.file_digest(f, "sha256").hexdigest()


def _import_unit() -> tuple[Path, Path]:
    """``(unit, so)``: what ``import sts2_rs`` loads (the loaded module if this
    process has one, else where the import system would find it) as a path to
    copy -- maturin's package dir, or a bare ``.so`` on ``PYTHONPATH`` -- and the
    extension file inside it."""
    mod = sys.modules.get("sts2_rs")
    if mod is not None:
        origin = getattr(mod, "__file__", None)
    else:
        spec = importlib.util.find_spec("sts2_rs")
        origin = spec.origin if spec is not None else None
    if not origin:
        raise SystemExit("cannot pin sts2_rs: it is not importable from this interpreter")
    origin_path = Path(origin)
    if origin_path.name != "__init__.py":
        return origin_path, origin_path
    # `sts2_rs.cpython-313-x86_64-linux-gnu.so`, `sts2_rs.abi3.so`, ...
    sos = [
        so
        for suffix in importlib.machinery.EXTENSION_SUFFIXES
        if (so := origin_path.parent / f"sts2_rs{suffix}").is_file()
    ]
    if len(sos) != 1:
        raise SystemExit(
            f"cannot pin sts2_rs: expected one extension in {origin_path.parent}: {sos}"
        )
    return origin_path.parent, sos[0]


def pin_of(so_path: str | os.PathLike | None) -> Path | None:
    """The pin dir the extension at ``so_path`` lives in, if it is one: its
    ``pin.json`` names the sha256 the file still has."""
    if not so_path:
        return None
    so = Path(so_path)
    for cand in (so.parent, so.parent.parent):
        record = cand / _PIN_RECORD
        if record.is_file():
            try:
                pinned = json.loads(record.read_text())["sha256"]
                return cand if pinned == _sha256(so) else None
            except (OSError, ValueError, KeyError, TypeError):
                return None
    return None


def pin_wheel(root: str | os.PathLike) -> Path:
    """Copy the ``sts2_rs`` that ``import sts2_rs`` loads into
    ``root/<sha256[:16]>/`` and return that dir, for the front of ``sys.path``.

    Content-addressed on the copied bytes, so the hash describes exactly the
    file that will run even if the source is being replaced while it is read,
    and a run (or a resumed one) that pins the same wheel again reuses the dir.
    Atomic: the copy is assembled in a temp dir under ``root`` and renamed into
    place.
    """
    unit, so = _import_unit()
    root = Path(os.path.abspath(root))
    root.mkdir(parents=True, exist_ok=True)
    tmp = Path(tempfile.mkdtemp(prefix=".pinning-", dir=root))
    try:
        tmp.chmod(0o755)  # mkdtemp's 0700 would hide the pin from other users
        if unit.is_dir():
            shutil.copytree(unit, tmp / unit.name, ignore=shutil.ignore_patterns(*_PIN_IGNORE))
            copied = tmp / unit.name / so.name
        else:
            copied = tmp / unit.name
            shutil.copy2(unit, copied)
        digest = _sha256(copied)
        record = {"sha256": digest, "source": str(so), "pinned_at": time.strftime("%FT%T%z")}
        (tmp / _PIN_RECORD).write_text(json.dumps(record, indent=2) + "\n")
        final = root / digest[:16]
        try:
            tmp.rename(final)
        except OSError:
            if not final.is_dir():
                raise
            # Pinned before (a resumed run, the screen's other arm): keep that
            # copy once it checks out as these same bytes, and as ours. The
            # hash covers only the .so; another user who could write into
            # `root` could have put a pin there whose `__init__.py` runs first.
            existing = final / copied.relative_to(tmp)
            shutil.rmtree(tmp)
            if pin_of(existing) != final or _sha256(existing) != digest:
                raise SystemExit(
                    f"{final} exists but is not a pin of {so}; move it aside and retry"
                )
            if hasattr(os, "getuid") and final.stat().st_uid != os.getuid():
                raise SystemExit(
                    f"{final} is a pin another user created; refusing to import from it. "
                    "Put the run's save dir (or the screen's --out) on a path you own."
                )
        return final
    except BaseException:
        shutil.rmtree(tmp, ignore_errors=True)
        raise


def _reexec_on_pin(pin: Path, main_module: str) -> NoReturn:
    """Replace this process with its own command line, re-run with ``pin`` first
    on ``PYTHONPATH`` (and :data:`PIN_ENV` naming it). Only for a process that
    was started as ``python ... -m <main_module>``: anything else (a test
    calling the CLI function) would re-run the wrong program."""
    argv = list(sys.orig_argv)
    if not any(a == "-m" and b == main_module for a, b in itertools.pairwise(argv)):
        raise SystemExit(
            f"cannot pin the sts2_rs wheel: this process was not started as "
            f"`python -m {main_module}` ({shlex.join(argv)})"
        )
    env = dict(os.environ)
    env[PIN_ENV] = str(pin)
    env["PYTHONPATH"] = os.pathsep.join(p for p in (str(pin), env.get("PYTHONPATH")) if p)
    print(f"wheel: pinned to {pin}; restarting on the pinned copy", file=sys.stderr, flush=True)
    sys.stdout.flush()
    os.execve(sys.executable, [sys.executable, *argv[1:]], env)


def prepare_training_wheel(
    *, pin_root: str | os.PathLike, allow_plain: bool, main_module: str
) -> dict:
    """The entry points' wheel step: pin, then check (see the module docstring).

    A process whose ``sts2_rs`` does not come from a pin is checked quietly
    (so a refused wheel is never copied), pinned under ``pin_root`` and
    re-executed (:func:`_reexec_on_pin`, which does not return). The
    re-executed process -- or one started under an existing pin, like the
    screen's arm trainers -- gets the full check on the pinned import and the
    record with ``pinned`` set. If :data:`PIN_ENV` is set, the import must come
    from that pin; anything else (``-E`` / ``-I`` dropping ``PYTHONPATH``, an
    ``sts2_rs`` in the cwd) is refused.

    Without :data:`PIN_SUPPORTED` (Windows) it only checks, and records
    ``pinned: None``.
    """
    if not PIN_SUPPORTED:
        print(
            "wheel: not pinned (no exec on this platform); the trainer still aborts an "
            "iteration whose workers load another sts2_rs build",
            file=sys.stderr,
            flush=True,
        )
        return {**check_training_wheel(allow_plain=allow_plain), "pinned": None}
    so_path = wheel_build_info()["so_path"]
    pin = pin_of(so_path)
    required = os.environ.get(PIN_ENV)
    if required and pin != Path(required):
        raise SystemExit(
            f"REFUSING TO TRAIN: this run is pinned to {required} ({PIN_ENV}), but "
            f"`import sts2_rs` loaded {so_path}. Was python started with -E or -I, "
            "or is there an sts2_rs in the working directory?"
        )
    if pin is None:
        check_training_wheel(allow_plain=allow_plain, warn=False)
        _reexec_on_pin(pin_wheel(pin_root), main_module)
    return {**check_training_wheel(allow_plain=allow_plain), "pinned": str(pin)}


def wheel_banner(wheel: dict) -> str:
    """One log line naming the wheel a run trains on."""
    pinned = f", pinned at {wheel['pinned']}" if wheel.get("pinned") else ""
    if wheel.get("wheel_pgo"):
        sha = str(wheel.get("profdata_sha256") or "?")[:12]
        cpu = wheel.get("target_cpu") or "portable"
        return (
            f"wheel: PGO sts2_rs (profdata sha256 {sha}, {cpu}, "
            f"src_hash {wheel.get('src_hash')}{pinned})"
        )
    return (
        f"WHEEL: PLAIN sts2_rs (src_hash {wheel.get('src_hash')}{pinned}) — trained under "
        "--allow-plain-wheel; wheel_pgo=false"
    )


def _cli(argv: list[str] | None = None) -> int:
    import argparse

    ap = argparse.ArgumentParser(description="Check the sts2_rs wheel a training run would use.")
    ap.add_argument("--allow-plain-wheel", action="store_true")
    args = ap.parse_args(argv)
    wheel = check_training_wheel(allow_plain=args.allow_plain_wheel)
    print(wheel_banner(wheel))
    print(json.dumps(wheel, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(_cli())
