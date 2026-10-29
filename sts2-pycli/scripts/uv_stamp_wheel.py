#!/usr/bin/env python3
"""Mark a freshly built ``sts2_rs`` as fresh for uv, so ``uv run`` keeps it until
the Rust source changes.

uv decides whether the installed editable ``sts2-rs`` is still fresh by
comparing the ``uv_cache.json`` in its dist-info with a key it computes from
the source: the newest ctime (not mtime; the mtime off unix) among the files
that ``[tool.uv] cache-keys`` in ``sts2-rs/crates/sts2-py/pyproject.toml``
names. That is the stale-wheel fix: any Rust edit moves the key, so uv
rebuilds. But a wheel uv did not build itself never matches. ``maturin
develop`` (``wheel-venv.sh``, ``pgo_wheel.sh``) installs a wheel FILE, and uv
records that file's ctime. So the next ``uv run`` / ``uv sync`` (``just test``,
``lint``, ``typecheck``, ``check``, ``advisor-warm``, ...) reinstalls uv's own
cached plain build over it, even when no source changed. For a plain install
that only swaps one plain build for another; for the PGO wheel it threw away a
~15 min build.

So ``pgo_wheel.sh`` takes uv's key BEFORE it builds anything (``key``) and
writes exactly that key when it installs the wheel (``stamp``). The stamp then
claims only the source the build started from: a change to any cache-key file
after that point, during the build or after it, moves uv's key past the stamp
and uv rebuilds, just as without the stamp. ``stamp`` refuses outright when
uv's key has already moved, or when the installed extension's ``src_hash`` is
not the source's. A key read at install time instead would bless an edit made
during the build, and ``src_hash`` alone does not see every file uv keys on.

Nothing else stamps: a plain ``maturin develop`` is still swapped for uv's
cached plain build (same source, harmless), and ``wheel-venv.sh`` does not
re-key a PGO wheel it keeps.

Only ``{file = "<path or glob>"}`` cache keys (and bare strings) are mirrored;
any other kind refuses rather than compute a key uv would not.

``key`` needs any Python >= 3.11; ``stamp`` must run on the venv's own
interpreter, which it inspects (``import sts2_rs``)::

    python3 -I scripts/uv_stamp_wheel.py key <sts2-rs checkout>   # prints the key
    <venv>/bin/python -I scripts/uv_stamp_wheel.py stamp <sts2-rs checkout> '<key>' [--check]

``--check`` then asks uv itself (``uv sync --frozen --inexact --dry-run``
against this venv) and fails if uv would still reinstall ``sts2-rs``, e.g.
after a uv upgrade changed how it computes the key, and also when uv's plan is
not one it recognises (a reworded plan must not read as agreement). ``stamp``
exits 0 when it wrote the key (and, with ``--check``, uv agrees or is not
installed).
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import tomllib
from pathlib import Path

CACHE_RECORD = "uv_cache.json"
# uv's `Timestamp::from_metadata` (uv-cache-info): the ctime on unix, the
# mtime everywhere else.
KEY_ON_CTIME = os.name == "posix"


def _key_time_ns(st: os.stat_result) -> int:
    return st.st_ctime_ns if KEY_ON_CTIME else st.st_mtime_ns


def cache_info(pkg_dir: Path) -> tuple[dict, Path]:
    """uv's ``CacheInfo`` for the editable source at ``pkg_dir`` (the dict uv
    serializes into ``uv_cache.json``), and the newest file behind it."""
    with open(pkg_dir / "pyproject.toml", "rb") as f:
        keys = tomllib.load(f)["tool"]["uv"]["cache-keys"]
    newest: tuple[int, Path] | None = None
    for key in keys:
        pattern = key if isinstance(key, str) else key.get("file")
        if not isinstance(pattern, str) or (isinstance(key, dict) and set(key) != {"file"}):
            raise SystemExit(f"uv-stamp: cache key {key!r} is not a {{file = ...}} key")
        if "{" in pattern:
            raise SystemExit(f"uv-stamp: brace globs are not mirrored: {pattern!r}")
        if any(c in pattern for c in "*?["):
            rels = glob.glob(pattern, root_dir=pkg_dir, recursive=True, include_hidden=True)
        else:
            rels = [pattern]
        for rel in rels:
            try:
                st = (pkg_dir / rel).stat()  # follows symlinks, as uv does
            except OSError:
                continue
            if stat.S_ISREG(st.st_mode) and (newest is None or _key_time_ns(st) > newest[0]):
                newest = (_key_time_ns(st), pkg_dir / rel)
    if newest is None:
        raise SystemExit(f"uv-stamp: no cache-key file exists under {pkg_dir}")
    ns, path = newest
    info = {
        "timestamp": {"secs_since_epoch": ns // 10**9, "nanos_since_epoch": ns % 10**9},
        "commit": None,
        "tags": None,
        "env": {},
        "directories": {},
    }
    return info, path


def uv_would_reinstall(prefix: str) -> str | None:
    """uv's own verdict on the venv at ``prefix``: None when ``uv sync`` keeps
    sts2-rs (or uv is not installed), else why not -- the plan line that
    touches sts2-rs, a failed dry run, or a plan :func:`planned_sts2_rs_change`
    does not recognise."""
    uv = shutil.which("uv")
    if uv is None:
        print("uv-stamp: uv is not on PATH; skipped the cross-check", file=sys.stderr)
        return None
    # Plain text: a FORCE_COLOR / CLICOLOR_FORCE in the caller's env would
    # otherwise wrap the plan's markers in ANSI codes.
    env = {k: v for k, v in os.environ.items() if k not in ("FORCE_COLOR", "CLICOLOR_FORCE")}
    out = subprocess.run(
        [uv, "sync", "--frozen", "--inexact", "--dry-run"],
        cwd=Path(__file__).resolve().parent.parent,
        env={**env, "UV_PROJECT_ENVIRONMENT": prefix, "NO_COLOR": "1"},
        capture_output=True,
        text=True,
        timeout=300,
        check=False,
    )
    text = out.stdout + out.stderr
    if out.returncode != 0:
        return f"`uv sync --dry-run` failed (rc={out.returncode}): {text.strip()}"
    return planned_sts2_rs_change(text)


_ANSI = re.compile(r"\x1b\[[0-9;]*m")


def planned_sts2_rs_change(plan: str) -> str | None:
    """None only when a ``uv sync --dry-run`` plan positively keeps sts2-rs, else
    why not.

    uv prints ``~ sts2-rs==0.0.1 (from ...)`` when it would reinstall a cached
    build, and a ``- sts2-rs==0.0.1`` / ``+ sts2-rs @ file://...`` pair when it
    would build anew; that line is returned. A plan keeps sts2-rs when it says
    ``Would make no changes``, or when it is a ``Would install / uninstall /
    ...`` plan that never names sts2-rs. Anything else -- a plan that names
    sts2-rs in a shape not matched above, or no recognised summary at all -- is
    reported as unrecognised, so a uv that rewords its plan fails the check
    instead of passing it.
    """
    plan = _ANSI.sub("", plan)
    hit = re.search(r"^ [-+~] sts2-rs(==| @ ).*$", plan, re.M)
    if hit:
        return hit.group(0)
    # The environment line names a path, which may spell sts2-rs; not a plan entry.
    entries = re.sub(r"^(Would use project environment|Using) .*$", "", plan, flags=re.M)
    if not re.search(r"sts2[-_.]rs(?![-_.\w])", entries, re.I):
        if re.search(r"^Would make no changes\b", plan, re.M):
            return None
        if re.search(r"^Would (install|uninstall|download|remove|update)\b", plan, re.M):
            return None
    return f"unrecognised `uv sync --dry-run` output: {plan.strip()[:300]!r}"


def _pkg_dir(rs_root: str) -> Path:
    return Path(rs_root).resolve() / "crates" / "sts2-py"


def key(rs_root: str) -> int:
    info, newest = cache_info(_pkg_dir(rs_root))
    print(json.dumps(info, separators=(",", ":")))
    print(f"uv-stamp: key = newest cache-key file {newest}", file=sys.stderr)
    return 0


def stamp(rs_root: str, taken: dict, check: bool) -> int:
    root = Path(rs_root).resolve()
    import sts2_rs  # type: ignore[import-not-found]

    stamped = sts2_rs.src_hash()  # ty: ignore[unresolved-attribute]
    current = sts2_rs.recompute_src_hash(str(root))  # ty: ignore[unresolved-attribute]
    if stamped != current:
        raise SystemExit(
            f"uv-stamp: not stamping: the installed sts2_rs (src_hash {stamped}) was not "
            f"compiled from the source at {root} ({current})"
        )
    now, newest = cache_info(_pkg_dir(rs_root))
    if now != taken:
        print(
            f"uv-stamp: not stamping: {newest} changed after the build started (uv's key "
            f"{now['timestamp']} != {taken.get('timestamp')}), so the next `uv run` / "
            "`uv sync` rebuilds sts2-rs from the current source",
            file=sys.stderr,
        )
        return 1
    site = Path(sts2_rs.__file__).parent.parent
    dist_infos = sorted(site.glob("sts2_rs-*.dist-info"))
    if len(dist_infos) != 1:
        raise SystemExit(f"uv-stamp: expected one sts2_rs dist-info in {site}: {dist_infos}")
    target = dist_infos[0] / CACHE_RECORD
    tmp = target.with_name(CACHE_RECORD + ".tmp")
    tmp.write_text(json.dumps(taken, separators=(",", ":")))
    tmp.replace(target)
    print(f"uv-stamp: {target} = the key from before the build", file=sys.stderr)

    if check:
        reinstall = uv_would_reinstall(sys.prefix)
        if reinstall is not None:
            print(f"uv-stamp: uv still plans to replace it: {reinstall}", file=sys.stderr)
            return 1
        print("uv-stamp: uv agrees -- `uv run` / `uv sync` keep this sts2_rs", file=sys.stderr)
    return 0


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=(__doc__ or "").split("\n\n")[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    k = sub.add_parser("key", help="print uv's key for the source now (before a build)")
    k.add_argument("rs_root", help="the sts2-rs checkout")
    s = sub.add_parser("stamp", help="write a key taken before the build into the dist-info")
    s.add_argument("rs_root", help="the sts2-rs checkout the venv's sts2_rs was built from")
    s.add_argument("key", help="the JSON `key` printed before the build started")
    s.add_argument("--check", action="store_true", help="confirm with `uv sync --dry-run`")
    args = ap.parse_args(argv)
    if args.cmd == "key":
        return key(args.rs_root)
    return stamp(args.rs_root, json.loads(args.key), args.check)


if __name__ == "__main__":
    sys.exit(main())
