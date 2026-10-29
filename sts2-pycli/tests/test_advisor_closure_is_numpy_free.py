"""The advisor's import closure must not need numpy or onnxruntime.

Dropping them from the bundle (``packaging/SimAdvisor.spec`` excludes, and
from the ``advisor-exe`` dependency group) is only half a change: the other
half is that nothing the advisor imports still reaches for them. PyInstaller
will happily build a bundle whose first live decision dies on ``import
numpy``, and the failure would land on a user mid-run rather than here --
the advisor's own never-crash contract means it would degrade to a random
legal pick and keep going, so the bundle would look fine while serving no
net at all.

So this blocks both modules at the import system and imports the advisor's
entry point plus the live decision path. It is a *closure* test, not a
behavior test: it asserts what gets imported, not what gets decided (that is
``diag_ppo_policy_parity.py``'s job).

The block is a meta-path finder rather than a ``sys.modules`` poison, so it
catches a lazy ``import numpy`` inside a function too -- which is the form
these imports usually take here.
"""

from __future__ import annotations

import builtins
import importlib
import sys

import pytest

#: What the advisor must not need. Kept in step with the spec's excludes and
#: the `advisor-exe` group; adding one here without adding it there (or vice
#: versa) is the drift this test exists to make loud.
BANNED = ("numpy", "onnxruntime")

#: The advisor's entry point and the modules its live decision path pulls in.
#: `realgame_advisor` is what PyInstaller analyzes; the rest are imported
#: lazily at runtime and so are invisible to a plain import of it.
ADVISOR_MODULES = (
    "realgame_advisor",
    "realgame_ppo",
    "realgame_mcts",
    "ppo.rust_policy",
    "ppo.errors",
    "workbench.ppo_policy",
)


class _Blocked(ImportError):
    """Raised in place of importing a banned module."""


@pytest.fixture
def block_banned(monkeypatch: pytest.MonkeyPatch):
    """Make importing any banned module raise, including lazily.

    Purging them from ``sys.modules`` first matters: pytest itself may have
    imported numpy long before this test runs, and a module already in
    ``sys.modules`` is returned without consulting any finder.
    """
    real_import = builtins.__import__

    def guarded(name, globals=None, locals=None, fromlist=(), level=0):
        root = name.partition(".")[0]
        if root in BANNED:
            raise _Blocked(f"{name!r} is not available to the advisor bundle")
        return real_import(name, globals, locals, fromlist, level)

    for mod in list(sys.modules):
        if mod.partition(".")[0] in BANNED or mod.partition(".")[0] in {
            m.partition(".")[0] for m in ADVISOR_MODULES
        }:
            monkeypatch.delitem(sys.modules, mod, raising=False)
    monkeypatch.setattr(builtins, "__import__", guarded)
    return guarded


@pytest.mark.parametrize("module", ADVISOR_MODULES)
def test_advisor_module_imports_without_numpy_or_onnxruntime(module, block_banned):
    """Each advisor module imports with numpy/onnxruntime unavailable."""
    try:
        importlib.import_module(module)
    except _Blocked as e:  # pragma: no cover - the failure we're guarding
        pytest.fail(
            f"{module} needs a banned dependency ({e}). The advisor bundle ships "
            f"neither {' nor '.join(BANNED)} -- route the code through "
            f"ppo.rust_policy, or make the import lazy and unreachable on the "
            f"live path."
        )


def test_rust_policy_is_the_default_backend():
    """`_load_onnx` must prefer Rust, or the exclusion breaks the advisor.

    The bundle has no onnxruntime to fall back to, so a default that
    silently flipped would leave the packaged advisor with no run policy at
    all -- and, per its never-crash contract, quietly playing at random.
    """
    realgame_ppo = importlib.import_module("realgame_ppo")
    assert realgame_ppo._use_rust_backend() is True


def test_ppo_schema_still_reexports_the_error():
    """`ppo.errors` is the new home; `ppo.schema` keeps the old name working.

    Every pre-existing `from ppo.schema import BudgetExceededError` must
    still resolve to the SAME class, or a caller's `except` clause silently
    stops catching (the advisor's shop-removal degrade path, GH #315).
    """
    from ppo.errors import BudgetExceededError as from_errors
    from ppo.schema import BudgetExceededError as from_schema

    assert from_schema is from_errors
