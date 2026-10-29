"""The deploy identity must be pinned by env override, not by gcloud config.

`gcloud auth login` REWRITES the `account` field of whichever configuration is
active. So `CLOUDSDK_ACTIVE_CONFIG_NAME=private` pins the config *name* but not
who actually deploys — the config's account silently follows the last login.

That is not hypothetical. The `private` config was created 2026-06-21 under the
personal account that owns the `sts2-sim` project; a `gcloud auth login
--account=<work account>` on 2026-07-30 repointed it at the unrelated work
account (config mtime is one minute after that login). Nothing failed loudly:
the next deploy would have run as the wrong identity against a project that
identity may not even own.

The Cloud Run deploy itself was retired on 2026-09-08 (service deleted), and
deploy/*.sh went with it. Terraform still manages the art bucket and the
Artifact Registry repo under this identity, so the pin still matters and these
tests still hold it in place — over deploy/justfile alone now.

`CLOUDSDK_CORE_ACCOUNT` is the fix because it is a READ-ONLY override — it wins
over the active configuration for one invocation and gcloud never writes it
back, so pinning it here cannot leak into the global default account used for
other projects.

These tests pin that arrangement in place.
"""

from __future__ import annotations

import re
from pathlib import Path

import pytest

_PYCLI_ROOT = Path(__file__).resolve().parents[1]
# The GCP recipes live in deploy/justfile (imported by the main justfile via
# `import?`); deploy/ never ships in the public export, so these tests skip there.
_JUSTFILE = _PYCLI_ROOT / "deploy" / "justfile"
pytestmark = pytest.mark.skipif(
    not _JUSTFILE.is_file(), reason="deploy/justfile is private (absent from the public export)"
)
# Every recipe that authenticates to GCP. Each must run the preflight first, so
# a missing/expired credential fails up front rather than mid-command (or,
# worse, succeeds under some other authenticated account). Only the Terraform
# pair is left; the deploy recipes were removed with the Cloud Run service.
_GCP_RECIPES = ["tf", "tf-import"]


def _justfile() -> str:
    return _JUSTFILE.read_text(encoding="utf-8")


def test_justfile_pins_account_via_readonly_override() -> None:
    """The account is pinned with CLOUDSDK_CORE_ACCOUNT and stays overridable."""
    m = re.search(
        r'^export CLOUDSDK_CORE_ACCOUNT := env_var_or_default\("GCP_ACCOUNT", "([^"]+)"\)',
        _justfile(),
        re.M,
    )
    assert m, "justfile must export CLOUDSDK_CORE_ACCOUNT with a GCP_ACCOUNT escape hatch"
    assert "@" in m.group(1), f"pinned deploy account looks malformed: {m.group(1)!r}"


def test_justfile_pins_project() -> None:
    assert re.search(r"^export CLOUDSDK_CORE_PROJECT := gcp_project", _justfile(), re.M), (
        "justfile must pin CLOUDSDK_CORE_PROJECT so `gcloud config get-value project` "
        "cannot resolve to whatever project the active config happens to name"
    )


def test_config_name_pin_is_not_the_only_pin() -> None:
    """Guards the exact regression: config-name pinning alone is insufficient."""
    text = _justfile()
    assert "CLOUDSDK_ACTIVE_CONFIG_NAME" in text, "the config pin was removed entirely"
    assert "CLOUDSDK_CORE_ACCOUNT" in text, (
        "CLOUDSDK_ACTIVE_CONFIG_NAME pins the config NAME, whose account field "
        "`gcloud auth login` rewrites — CLOUDSDK_CORE_ACCOUNT must pin the identity"
    )


@pytest.mark.parametrize("recipe", _GCP_RECIPES)
def test_gcp_recipes_depend_on_preflight(recipe: str) -> None:
    m = re.search(rf"^{re.escape(recipe)}(?: [^:\n]*)?:(.*)$", _justfile(), re.M)
    assert m, f"recipe {recipe!r} not found in the justfile"
    assert "_gcp-preflight" in m.group(1), (
        f"recipe {recipe!r} authenticates to GCP but does not depend on _gcp-preflight"
    )


def test_preflight_recipe_exists_and_checks_the_pinned_account() -> None:
    text = _justfile()
    assert re.search(r"^_gcp-preflight:", text, re.M), "_gcp-preflight recipe is missing"
    body = text.split("_gcp-preflight:", 1)[1]
    assert "print-access-token" in body, (
        "preflight must prove the credential is live, not merely that the account is listed"
    )


def test_no_stale_reference_to_the_retired_deploy_scripts() -> None:
    """deploy/*.sh were removed with the Cloud Run service; nothing may call them."""
    text = _justfile()
    for name in ("build-local.sh", "deploy.sh", "sync-assets.sh"):
        assert name not in text, f"justfile still references the removed deploy/{name}"
