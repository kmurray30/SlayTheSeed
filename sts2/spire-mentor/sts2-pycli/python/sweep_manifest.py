"""Sweep-v2 manifest: per-subject verdict record + cross-run comparator.

Each sweep run is summarized as one JSON manifest so nightly runs can be
diffed: a subject flipping clean → divergent is a REGRESSION the driver
exits non-zero on; divergent → clean is a fix to celebrate (and close
beads for). Manifests live in sts2-rs/docs/audits/sweeps/.
"""

from __future__ import annotations

import datetime as _dt
import hashlib
import json
import os
import warnings
from collections.abc import Sequence


def _subject_key(result: dict) -> str:
    # Empty-string coverage_subject keys as "" (a visible bug in the manifest)
    # rather than falling through to seed-keying, which would silently
    # misattribute the trial under a seed:N key.
    subj = result.get("coverage_subject")
    return subj if subj is not None else f"seed:{result['seed']}"


def build_manifest(
    tier: str,
    axes: Sequence[str],
    results: list[dict],
    meta: dict,
    blocked: dict[str, str] | None = None,
) -> dict:
    subjects: dict[str, dict] = {}
    for r in results:
        key = _subject_key(r)
        if key in subjects:
            warnings.warn(
                f"duplicate subject key {key!r}; overwriting earlier entry",
                stacklevel=2,
            )
        cat = r.get("category", "clean")
        if cat == "clean":
            subjects[key] = {"verdict": "clean"}
        else:
            detail = f"{cat}|{r.get('first_path')}"
            subjects[key] = {
                "verdict": "divergent",
                "bucket": cat,
                "detail_hash": hashlib.sha1(detail.encode(), usedforsecurity=False).hexdigest()[
                    :12
                ],
            }
    for subject, reason in (blocked or {}).items():
        subjects[subject] = {"verdict": "BLOCKED", "reason": reason}
    # Explicit tier/axes always reflect the run arguments and win over any
    # same-named keys a caller passed in `meta`.
    return {
        "meta": {**meta, "tier": tier, "axes": list(axes)},
        "subjects": subjects,
    }


def compare_manifests(prev: dict, cur: dict) -> dict:
    """Diff two manifests. BLOCKED counts as neither clean nor divergent."""
    p, c = prev["subjects"], cur["subjects"]

    def _is(d: dict | None, verdict: str) -> bool:
        return d is not None and d.get("verdict") == verdict

    regressions = sorted(s for s in c if _is(p.get(s), "clean") and _is(c[s], "divergent"))
    fixes = sorted(s for s in c if _is(p.get(s), "divergent") and _is(c[s], "clean"))
    new = sorted(s for s in c if s not in p)
    gone = sorted(s for s in p if s not in c)
    return {"regressions": regressions, "fixes": fixes, "new": new, "gone": gone}


def write_manifest(manifest: dict, out_dir: str, tier: str) -> str:
    """Serialize a manifest to ``<out_dir>/<date>-<tier>-manifest.json``.

    Tier names must not contain hyphens (the suffix match in
    ``latest_manifest`` would be ambiguous).
    """
    os.makedirs(out_dir, exist_ok=True)
    date = _dt.datetime.now().strftime("%Y-%m-%d-%H%M%S")
    path = os.path.join(out_dir, f"{date}-{tier}-manifest.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, sort_keys=True)
    return path


def latest_manifest(out_dir: str, tier: str, before: str | None = None) -> str | None:
    """Most recent same-tier manifest in out_dir (optionally excluding `before`).

    Pass `before` as the exact path returned by write_manifest with the same
    out_dir (no realpath canonicalization). Tier names must not contain hyphens
    (the suffix match would be ambiguous).
    """
    if not os.path.isdir(out_dir):
        return None
    cands = sorted(
        f
        for f in os.listdir(out_dir)
        if f.endswith(f"-{tier}-manifest.json")
        and (before is None or os.path.join(out_dir, f) != before)
    )
    return os.path.join(out_dir, cands[-1]) if cands else None
