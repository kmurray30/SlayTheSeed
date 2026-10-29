"""The deployed image must install what the deployed code lazily imports.

The image's Python dependencies are a hand-written `pip install` list in the
Dockerfile, deliberately narrower than `pyproject.toml` (no torch, no sklearn,
no umap). That list is maintained by hand, so it can silently fall behind the
code.

A *lazy* import — one inside a function, in a `try`/`except` that falls back —
turns that drift into a silent behaviour change rather than a crash. The case
this module exists for: `bugreport_store` imports `google.cloud.storage` inside
a `try/except Exception` that falls back to writing a container-local file. The
package was never added to the Dockerfile, so on Cloud Run every 🐞 Report was
written to a filesystem that is destroyed at scale-to-zero. No error, no failed
deploy — the reports simply never existed. Found 2026-08-08, months after the
feature shipped.

A top-level import would have crashed the server on the first request and been
fixed the same hour. So lazy imports in deployed code need this check instead.
"""

from __future__ import annotations

import re
from pathlib import Path

import pytest

_PYCLI_ROOT = Path(__file__).resolve().parents[1]
_DOCKERFILE = _PYCLI_ROOT.parent / "Dockerfile"

# Third-party packages the deployed code imports lazily, mapped to the
# distribution name the Dockerfile must install. Add a row whenever you add a
# lazy third-party import to a module that runs in the image.
_LAZY_RUNTIME_IMPORTS = {
    "google.cloud.storage": "google-cloud-storage",
}


def _dockerfile() -> str:
    return _DOCKERFILE.read_text(encoding="utf-8")


@pytest.mark.parametrize(
    ("module_path", "distribution"),
    sorted(_LAZY_RUNTIME_IMPORTS.items()),
)
def test_dockerfile_installs_lazily_imported_package(module_path: str, distribution: str) -> None:
    assert re.search(rf'"{re.escape(distribution)}[>=<]', _dockerfile()), (
        f"the deployed code lazily imports {module_path} but the Dockerfile does "
        f"not pip-install {distribution!r}. The lazy import is inside a "
        f"try/except with a fallback, so this does not fail the build or the "
        f"request — the feature just silently does nothing in production."
    )


def test_bugreport_store_still_imports_storage_lazily() -> None:
    """Establishes the premise; if this becomes a top-level import, the guard
    above is no longer the thing protecting us (the server would just crash)."""
    src = (_PYCLI_ROOT / "python" / "workbench" / "bugreport_store.py").read_text(encoding="utf-8")
    assert "    from google.cloud import storage" in src, (
        "google.cloud.storage is no longer imported lazily inside a function — "
        "re-check whether _LAZY_RUNTIME_IMPORTS still describes reality"
    )


def test_unconfigured_bucket_is_not_an_error() -> None:
    """Local dev legitimately has no bucket; that must not log an error."""
    from workbench import bugreport_store

    assert bugreport_store.check_config(None) is None
    assert bugreport_store.check_config("") is None


def test_missing_library_is_reported_at_startup(monkeypatch: pytest.MonkeyPatch) -> None:
    """The exact production failure: bucket configured, library absent.

    Simulated rather than probed, because this test environment *has* the
    library — the point is what happens in an image that doesn't.
    """
    import builtins

    from workbench import bugreport_store

    real_import = builtins.__import__

    def _no_google(name: str, *args: object, **kwargs: object) -> object:
        if name.startswith("google"):
            raise ImportError("No module named 'google'")
        return real_import(name, *args, **kwargs)  # type: ignore[arg-type]

    monkeypatch.setattr(builtins, "__import__", _no_google)

    problem = bugreport_store.check_config("gs://example-bugreports")
    assert problem, "a configured bucket with no client library must be reported"
    assert "google-cloud-storage" in problem, "the message must name the missing package"
    assert "LOST" in problem, "the message must say reports are lost, not merely 'failed'"
