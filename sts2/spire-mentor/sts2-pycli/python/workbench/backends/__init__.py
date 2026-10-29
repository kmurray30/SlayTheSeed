"""Pluggable game backends for the workbench live session.

See :mod:`workbench.backends.base` for the :class:`GameBackend` protocol and
the :func:`make_backend` factory.
"""

from __future__ import annotations

from workbench.backends.base import (
    DuckTypedBackend,
    GameBackend,
    UnsupportedOperation,
    adapt_backend,
    make_backend,
)

__all__ = [
    "DuckTypedBackend",
    "GameBackend",
    "UnsupportedOperation",
    "adapt_backend",
    "make_backend",
]
