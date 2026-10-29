"""Unit tests for the shared-password gate (``workbench.auth``).

Everything runs offline against a throwaway two-route ASGI app — no workbench
server, no network, no real identity. ``base_url`` is https so httpx's cookie
jar will send back the ``Secure`` session cookie the gate mints.
"""

from __future__ import annotations

import asyncio
import time

import pytest
from starlette.applications import Starlette
from starlette.responses import PlainTextResponse
from starlette.routing import Route
from starlette.testclient import TestClient
from workbench.auth import _COOKIE_NAME, PasswordGateMiddleware, install_password_gate

PASSWORD = "hunter2"


def _inner_app() -> Starlette:
    """A tiny app whose routes echo their path, so pass-through is visible."""

    async def ok(request):  # noqa: RUF029 — ASGI handlers must be async
        return PlainTextResponse(f"inner:{request.url.path}")

    return Starlette(
        routes=[
            Route("/", ok),
            Route("/deep/page", ok),
            Route("/api/health", ok),
            Route("/api/state", ok),
        ]
    )


def _client(**kwargs) -> TestClient:
    app = _inner_app()
    app.add_middleware(PasswordGateMiddleware, password=PASSWORD, **kwargs)
    return TestClient(app, base_url="https://testserver")


# -- token helpers --------------------------------------------------------


def _gate(**kwargs) -> PasswordGateMiddleware:
    return PasswordGateMiddleware(_inner_app(), password=PASSWORD, **kwargs)


def test_mint_roundtrips_through_valid():
    gate = _gate()
    assert gate._valid(gate._mint())


@pytest.mark.parametrize(
    "token",
    [
        None,
        "",
        "no-dot-here",  # missing separator
        "notanumber.deadbeef",  # exp is not digits
        "9999999999.deadbeef",  # signature mismatch
    ],
)
def test_valid_rejects_malformed_tokens(token):
    assert _gate()._valid(token) is False


def test_valid_rejects_expired_token():
    gate = _gate(max_age=-10)  # mints a token that expired 10s ago
    assert gate._valid(gate._mint()) is False


def test_mint_encodes_expiry_from_max_age():
    gate = _gate(max_age=1234)
    exp_s, _, _sig = gate._mint().partition(".")
    assert abs(int(exp_s) - (int(time.time()) + 1234)) <= 2


def test_key_derives_from_password_when_no_secret():
    """Rotating the password must invalidate sessions minted under the old one."""
    old = PasswordGateMiddleware(_inner_app(), password="old-pw")
    new = PasswordGateMiddleware(_inner_app(), password="new-pw")
    assert new._valid(old._mint()) is False


def test_explicit_secret_makes_tokens_portable():
    """With a shared explicit secret the token survives a password change."""
    a = PasswordGateMiddleware(_inner_app(), password="pw-a", secret="s3cret")
    b = PasswordGateMiddleware(_inner_app(), password="pw-b", secret="s3cret")
    assert b._valid(a._mint())


# -- unauthenticated routing ---------------------------------------------


def test_browser_navigation_redirects_to_login():
    resp = _client().get("/deep/page", follow_redirects=False)
    assert resp.status_code == 303
    assert resp.headers["location"] == "/login"


def test_api_call_gets_401_json():
    resp = _client().get("/api/state")
    assert resp.status_code == 401
    assert resp.json() == {"error": "authentication required"}


def test_health_is_public():
    resp = _client().get("/api/health")
    assert resp.status_code == 200
    assert resp.text == "inner:/api/health"


def test_get_login_serves_the_form_without_error_text():
    resp = _client().get("/login")
    assert resp.status_code == 200
    assert "STS2 Workbench" in resp.text
    assert 'name="password"' in resp.text
    assert "Incorrect password" not in resp.text


def test_logout_redirects_and_clears_the_cookie():
    resp = _client().get("/logout", follow_redirects=False)
    assert resp.status_code == 303
    assert resp.headers["location"] == "/login"
    set_cookie = resp.headers["set-cookie"]
    assert _COOKIE_NAME in set_cookie
    assert "Max-Age=0" in set_cookie or "expires=Thu, 01 Jan 1970" in set_cookie.lower()


# -- login flows ----------------------------------------------------------


def test_post_login_with_correct_password_sets_cookie_and_redirects():
    client = _client()
    resp = client.post("/login", data={"password": PASSWORD}, follow_redirects=False)
    assert resp.status_code == 303
    assert resp.headers["location"] == "/"
    cookie = resp.headers["set-cookie"]
    assert cookie.startswith(f"{_COOKIE_NAME}=")
    assert "HttpOnly" in cookie
    assert "Secure" in cookie
    assert "SameSite=lax" in cookie
    assert "Path=/" in cookie
    # The minted cookie is now good for a normally-gated page.
    assert client.get("/deep/page").text == "inner:/deep/page"


def test_post_login_with_wrong_password_returns_401_form():
    resp = _client().post("/login", data={"password": "nope"})
    assert resp.status_code == 401
    assert "Incorrect password" in resp.text
    assert "set-cookie" not in resp.headers


def test_post_login_with_missing_password_field_returns_401():
    resp = _client().post("/login", data={})
    assert resp.status_code == 401
    assert "Incorrect password" in resp.text


def test_query_password_sets_cookie_and_strips_it_from_the_redirect():
    client = _client()
    resp = client.get(
        "/deep/page", params={"password": PASSWORD, "step": "7"}, follow_redirects=False
    )
    assert resp.status_code == 303
    # password gone, the rest of the query preserved.
    assert resp.headers["location"] == "/deep/page?step=7"
    assert resp.headers["set-cookie"].startswith(f"{_COOKIE_NAME}=")
    assert client.get("/deep/page").text == "inner:/deep/page"


def test_query_password_only_redirects_to_bare_path():
    resp = _client().get("/", params={"password": PASSWORD}, follow_redirects=False)
    assert resp.status_code == 303
    assert resp.headers["location"] == "/"


def test_query_password_preserves_repeated_params():
    resp = _client().get(
        "/deep/page",
        params=[("password", PASSWORD), ("t", "1"), ("t", "2")],
        follow_redirects=False,
    )
    assert resp.headers["location"] == "/deep/page?t=1&t=2"


def test_wrong_query_password_still_gets_turned_away():
    resp = _client().get("/api/state", params={"password": "nope"})
    assert resp.status_code == 401
    assert "set-cookie" not in resp.headers


# -- cookie handling ------------------------------------------------------


def test_forged_cookie_is_rejected():
    client = _client()
    client.cookies.set(_COOKIE_NAME, f"{int(time.time()) + 999}.{'0' * 64}")
    resp = client.get("/deep/page", follow_redirects=False)
    assert resp.status_code == 303
    assert resp.headers["location"] == "/login"


def test_cookie_from_a_different_password_is_rejected():
    stale = PasswordGateMiddleware(_inner_app(), password="other-pw")._mint()
    client = _client()
    client.cookies.set(_COOKIE_NAME, stale)
    assert client.get("/api/state").status_code == 401


def test_authenticated_request_reaches_the_inner_app_untouched():
    client = _client()
    client.cookies.set(_COOKIE_NAME, _gate()._mint())
    resp = client.get("/api/state")
    assert resp.status_code == 200
    assert resp.text == "inner:/api/state"
    # Already authenticated: the gate doesn't re-mint on every request.
    assert "set-cookie" not in resp.headers


# -- ASGI plumbing --------------------------------------------------------


def test_non_http_scopes_pass_straight_through():
    """Lifespan/websocket traffic must not be gated (and never parsed as HTTP)."""
    seen: list[str] = []

    async def inner(scope, receive, send):  # noqa: RUF029 — ASGI app signature
        seen.append(scope["type"])

    gate = PasswordGateMiddleware(inner, password=PASSWORD)

    async def _run():
        await gate({"type": "lifespan"}, None, None)
        await gate({"type": "websocket", "path": "/ws"}, None, None)

    asyncio.run(_run())
    assert seen == ["lifespan", "websocket"]


def test_install_password_gate_wraps_the_app():
    app = _inner_app()
    install_password_gate(app, PASSWORD, None)
    client = TestClient(app, base_url="https://testserver")
    assert client.get("/api/state").status_code == 401
    client.cookies.set(_COOKIE_NAME, _gate()._mint())
    assert client.get("/api/state").status_code == 200


def test_install_password_gate_honours_explicit_secret():
    app = _inner_app()
    install_password_gate(app, PASSWORD, "shared-secret")
    client = TestClient(app, base_url="https://testserver")
    # A token minted with the same secret but a different password is accepted.
    token = PasswordGateMiddleware(
        _inner_app(), password="whatever", secret="shared-secret"
    )._mint()
    client.cookies.set(_COOKIE_NAME, token)
    assert client.get("/api/state").status_code == 200
