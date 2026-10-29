"""Persist + retrieve workbench bug-report dumps.

A dump is a single JSON object (see :mod:`workbench.bugreport`). It lives in a
dedicated public-read GCS bucket (``STS2_BUGREPORT_BUCKET``) so a report URL is
directly fetchable from any environment with no credentials. When no bucket is
configured or GCS credentials don't resolve, dumps fall back to a local file
under ``<logs_dir>/bugreports/`` so capture never hard-fails in local dev.
"""

from __future__ import annotations

import json
import logging
import re
import secrets
from pathlib import Path
from typing import Any

_log = logging.getLogger(__name__)

_PREFIX = "bugreports"
_ID_RE = re.compile(r"^wb-[0-9a-f]{16}$")  # exactly what new_id() produces


def _check_id(report_id: str) -> None:
    """Reject ids that don't match the generated format, so a caller-supplied
    id can't escape the bugreports prefix (path traversal) in the local or GCS
    path."""
    if not _ID_RE.match(report_id):
        raise ValueError("invalid report id")


def new_id() -> str:
    """A short, unguessable report id: ``wb-`` + 16 hex chars (64 bits).

    The id is the only barrier to enumerating public-read report objects, so it
    must carry enough entropy to be unguessable.
    """
    return f"wb-{secrets.token_hex(8)}"


def check_config(bucket: str | None) -> str | None:
    """Return an error string if ``bucket`` is set but unusable, else ``None``.

    Called at server startup so a misconfigured deployment says so immediately
    instead of at the first lost report. The GCS path in :func:`put` swallows
    every exception and falls back to a container-local file, which on Cloud Run
    is destroyed when the instance scales to zero — so "configured but broken"
    and "working" look identical from the outside. They are only
    distinguishable here.

    The historical failure this exists for: ``google-cloud-storage`` was absent
    from the deployed image's dependency list, so the lazy import raised, every
    capture fell back to local, and no report ever survived.

    Deliberately does NOT probe the bucket itself. ``Bucket.exists()`` needs
    ``storage.buckets.get``, which the least-privilege ``roles/storage.objectUser``
    grant does not include — so a reachability probe would report a problem for a
    bucket the service can write to perfectly well. It checks only the two things
    it can check correctly and cheaply; a genuine per-report failure is logged at
    ERROR by :func:`put`.
    """
    if not bucket:
        return None  # deliberately unconfigured — local fallback is expected.
    try:
        from google.cloud import storage
    except ImportError as exc:
        return (
            f"STS2_BUGREPORT_BUCKET={bucket} is set but google-cloud-storage is "
            f"not installed ({exc}) — every bug report will fall back to a "
            f"container-local file and be LOST on scale-to-zero. Add "
            f"google-cloud-storage to the image's dependency list."
        )
    try:
        storage.Client()
    except Exception as exc:  # any failure to build a client means "unusable"
        return (
            f"STS2_BUGREPORT_BUCKET={bucket} is set but GCS credentials do not "
            f"resolve ({exc}) — bug reports will fall back to a container-local "
            f"file and be LOST on scale-to-zero."
        )
    return None


def _local_path(report_id: str, logs_dir: Path) -> Path:
    return Path(logs_dir) / _PREFIX / f"{report_id}.json"


def _write_local(report_id: str, body: str, logs_dir: Path) -> str:
    path = _local_path(report_id, logs_dir)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(body, encoding="utf-8")
    return path.resolve().as_uri()


def put(report_id: str, data: dict[str, Any], *, logs_dir: Path, bucket: str | None) -> str:
    """Store ``data`` under ``report_id``; return a fetchable URL.

    GCS (public-read) when ``bucket`` is set and creds resolve; otherwise a
    local file whose ``file://`` URI is returned. Never raises on the
    no-creds/GCS-unavailable path — it falls back to local.
    """
    _check_id(report_id)
    body = json.dumps(data, indent=2, sort_keys=True)
    if bucket:
        try:
            from google.cloud import storage  # lazy: optional in local dev

            bucket_name = bucket.removeprefix("gs://")
            client = storage.Client()
            blob = client.bucket(bucket_name).blob(f"{_PREFIX}/{report_id}.json")
            blob.upload_from_string(body, content_type="application/json")
            return blob.public_url
        except Exception:
            # ERROR, not warning: a bucket was explicitly configured, so the
            # local file is not the intended destination — on Cloud Run it dies
            # with the instance. The capture still succeeds (never lose the
            # user's report to a logging decision), but this must be findable.
            _log.error(
                "bug report %s: GCS upload to %s FAILED — falling back to a local "
                "file, which is NOT persisted on a scale-to-zero deployment",
                report_id,
                bucket,
                exc_info=True,
            )
    return _write_local(report_id, body, logs_dir)


def get(report_id: str, *, logs_dir: Path, bucket: str | None) -> dict[str, Any] | None:
    """Fetch a dump by id; GCS first (when configured), then local file."""
    _check_id(report_id)
    if bucket:
        try:
            from google.cloud import storage

            bucket_name = bucket.removeprefix("gs://")
            client = storage.Client()
            blob = client.bucket(bucket_name).blob(f"{_PREFIX}/{report_id}.json")
            raw = blob.download_as_text()
            return json.loads(raw)
        except Exception:
            _log.info("GCS fetch miss for %s; trying local", report_id, exc_info=True)
    path = _local_path(report_id, logs_dir)
    if path.is_file():
        return json.loads(path.read_text(encoding="utf-8"))
    return None
