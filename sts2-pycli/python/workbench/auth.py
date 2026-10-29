"""A minimal shared-password gate for the workbench.

Activated only when ``STS2_AUTH_PASSWORD`` is set (so local dev and the pytest
suite are unaffected). When active, every request needs a valid signed session
cookie; without one, browsers are redirected to a self-contained ``/login``
page and API/XHR calls get ``401``. Submitting the correct password sets a
long-lived ``HttpOnly`` cookie, so you log in once and stay logged in.

This is intentionally a *shared* password — no per-user accounts. The session
cookie + middleware are the part that a future Google-OIDC login would reuse:
swap the ``/login`` password check for an OAuth callback that sets the same
cookie, and nothing else changes.

Implemented as a pure-ASGI middleware (not Starlette ``BaseHTTPMiddleware``) so
it never buffers responses — the SSE advisor stream keeps working.
"""

from __future__ import annotations

import hashlib
import hmac
import time
from urllib.parse import urlencode

from starlette.requests import Request
from starlette.responses import HTMLResponse, JSONResponse, RedirectResponse, Response
from starlette.types import ASGIApp, Receive, Scope, Send


def _strip_password(request: Request) -> str:
    """The request's path + query with the ``password`` param removed."""
    kept = [(k, v) for k, v in request.query_params.multi_items() if k != "password"]
    query = urlencode(kept)
    path = request.url.path or "/"
    return f"{path}?{query}" if query else path


# Paths reachable without a session: the login form/handler, logout, and the
# health check (so uptime probes don't need the cookie).
_PUBLIC_PATHS = frozenset({"/login", "/logout", "/api/health"})

_COOKIE_NAME = "sts2_auth"
_DEFAULT_MAX_AGE = 30 * 24 * 3600  # 30 days

_LOGIN_HTML = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>STS2 Workbench — sign in</title>
<style>
  body {{ margin:0; min-height:100vh; display:grid; place-items:center;
    background:#15171c; color:#e8e8ea; font:16px/1.4 system-ui,sans-serif; }}
  form {{ background:#1f232b; padding:2rem 2.25rem; border-radius:12px;
    box-shadow:0 8px 30px rgba(0,0,0,.4); width:min(92vw,320px); }}
  h1 {{ font-size:1.1rem; margin:0 0 1rem; font-weight:600; }}
  input {{ width:100%; box-sizing:border-box; padding:.6rem .7rem; margin:.35rem 0 1rem;
    border:1px solid #3a3f4b; border-radius:8px; background:#12141a; color:#e8e8ea; }}
  button {{ width:100%; padding:.6rem; border:0; border-radius:8px; cursor:pointer;
    background:#e0533d; color:#fff; font-weight:600; }}
  .err {{ color:#ff8a7a; font-size:.85rem; margin:-.5rem 0 .75rem; min-height:1em; }}
</style></head>
<body><form method="post" action="/login">
  <h1>🗡️ STS2 Workbench</h1>
  <div class="err">{error}</div>
  <input type="password" name="password" placeholder="Password" autofocus
    autocomplete="current-password">
  <button type="submit">Sign in</button>
</form></body></html>"""


class PasswordGateMiddleware:
    """Gate all requests behind a shared password + signed session cookie."""

    def __init__(
        self,
        app: ASGIApp,
        *,
        password: str,
        secret: str | None = None,
        max_age: int = _DEFAULT_MAX_AGE,
    ) -> None:
        self.app = app
        self._password = password
        # Sign cookies with an explicit secret, else derive a stable key from the
        # password (so rotating the password also invalidates old sessions).
        key = secret if secret else f"sts2-auth:{password}"
        self._key = hashlib.sha256(key.encode()).digest()
        self._max_age = max_age

    # -- token helpers ----------------------------------------------------
    def _mint(self) -> str:
        exp = str(int(time.time()) + self._max_age)
        sig = hmac.new(self._key, exp.encode(), hashlib.sha256).hexdigest()
        return f"{exp}.{sig}"

    def _valid(self, token: str | None) -> bool:
        if not token or "." not in token:
            return False
        exp_s, _, sig = token.partition(".")
        if not exp_s.isdigit() or int(exp_s) < time.time():
            return False
        expected = hmac.new(self._key, exp_s.encode(), hashlib.sha256).hexdigest()
        return hmac.compare_digest(sig, expected)

    def _set_cookie(self, resp: Response) -> None:
        resp.set_cookie(
            _COOKIE_NAME,
            self._mint(),
            max_age=self._max_age,
            httponly=True,
            secure=True,
            samesite="lax",
            path="/",
        )

    # -- ASGI entrypoint --------------------------------------------------
    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        request = Request(scope, receive)
        path = request.url.path

        if self._valid(request.cookies.get(_COOKIE_NAME)):
            await self.app(scope, receive, send)
            return

        # Sign in via ?password=<pw> (handy for a bookmarkable URL). On a match,
        # set the cookie and redirect to the same URL minus the password, so it
        # doesn't linger in history / the address bar / referer headers.
        qp_password = request.query_params.get("password")
        if qp_password is not None and hmac.compare_digest(qp_password, self._password):
            resp: Response = RedirectResponse(_strip_password(request), status_code=303)
            self._set_cookie(resp)
            await resp(scope, receive, send)
            return

        if path == "/login" and request.method == "POST":
            form = await request.form()
            supplied = str(form.get("password", ""))
            if hmac.compare_digest(supplied, self._password):
                resp: Response = RedirectResponse("/", status_code=303)
                self._set_cookie(resp)
            else:
                resp = HTMLResponse(_LOGIN_HTML.format(error="Incorrect password"), status_code=401)
            await resp(scope, receive, send)
            return

        if path in _PUBLIC_PATHS or path == "/login":
            if path == "/logout":
                resp = RedirectResponse("/login", status_code=303)
                resp.delete_cookie(_COOKIE_NAME, path="/")
            elif path == "/api/health":
                await self.app(scope, receive, send)
                return
            else:
                resp = HTMLResponse(_LOGIN_HTML.format(error=""))
            await resp(scope, receive, send)
            return

        # Unauthenticated: 401 for programmatic callers, redirect for navigation.
        if path.startswith("/api/"):
            resp = JSONResponse({"error": "authentication required"}, status_code=401)
        else:
            resp = RedirectResponse("/login", status_code=303)
        await resp(scope, receive, send)


def install_password_gate(app: ASGIApp, password: str, secret: str | None) -> None:
    """Wrap *app* with the password gate (call once, after routes are set up)."""
    app.add_middleware(PasswordGateMiddleware, password=password, secret=secret)
