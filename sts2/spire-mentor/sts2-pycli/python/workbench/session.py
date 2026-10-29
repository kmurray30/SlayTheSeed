"""Per-client session isolation for the workbench server.

Without this, all run state (the loaded replay :class:`~workbench.timeline.
TimelineStore` and the interactive :class:`~workbench.branching.
BranchingLiveSession`) lived on two process-global singletons
(``app.state.store`` / ``app.state.live``), so every browser pointed at the
same deployment **shared one run** — a second person starting a live game
clobbered the first person's.

:class:`SessionRegistry` keeps one :class:`Session` per client (keyed by an
opaque ``sts2_sid`` cookie minted on first contact), so each person gets their
own loaded replay and their own live game. Sessions expire after an idle TTL;
eviction calls ``live.quit()`` so the engine subprocess a live game owns is
released (otherwise idle browsers would leak one process each).
"""

from __future__ import annotations

import threading
import time
import uuid
from dataclasses import dataclass, field
from typing import Any

from starlette.datastructures import MutableHeaders
from starlette.requests import Request
from starlette.responses import Response
from starlette.types import ASGIApp, Message, Receive, Scope, Send

# Cookie that routes a client to its own session, and the ASGI-scope key under
# which :class:`SessionCookieMiddleware` hands the resolved id to the request
# handlers. The id is *not* the auth credential (that gate is separate) — it
# only selects which run state a client sees.
SID_COOKIE = "sts2_sid"
SID_SCOPE_KEY = "sts2_session_id"
_SID_MAX_AGE = 30 * 24 * 3600  # 30 days

# Defaults: an hour of inactivity before a session is reaped, and a hard cap so
# a burst of one-shot visitors can't grow the process unbounded (each live game
# can own an engine subprocess). When the cap is hit the least-recently-used
# session is evicted to make room.
_DEFAULT_TTL_SECONDS = 3600
_DEFAULT_MAX_SESSIONS = 64


@dataclass
class Session:
    """One client's isolated workbench state.

    Attributes mirror the two globals this replaces:

    * ``store`` — the loaded replay :class:`~workbench.timeline.TimelineStore`
      (or ``None`` before ``/api/load``).
    * ``live`` — the active :class:`~workbench.branching.BranchingLiveSession`
      (or ``None`` when no live game is running).
    """

    sid: str
    store: Any = None
    live: Any = None
    imported: Any = None
    """The :class:`~workbench.state_import.ImportedState` ``live`` was built
    from, or ``None``. Kept so the SimLab handoff can describe the run behind
    the board without asking the user to paste it twice."""
    last_access: float = field(default_factory=time.monotonic)


class SessionRegistry:
    """Thread-safe map of session id → :class:`Session` with idle eviction.

    The workbench runs FastAPI sync endpoints in a threadpool, so requests
    touch this concurrently — every mutation is guarded by one lock. Eviction is
    lazy (driven from :meth:`get` / :meth:`create`), so no background sweeper
    thread is needed.
    """

    def __init__(
        self,
        ttl_seconds: float = _DEFAULT_TTL_SECONDS,
        max_sessions: int = _DEFAULT_MAX_SESSIONS,
    ) -> None:
        self._sessions: dict[str, Session] = {}
        self._lock = threading.Lock()
        self._ttl = ttl_seconds
        self._max = max_sessions

    @staticmethod
    def _close(sess: Session) -> None:
        """Release a session's engine resources (its live game's subprocess)."""
        live = sess.live
        sess.live = None
        if live is not None:
            try:
                live.quit()
            except Exception:
                # Best-effort cleanup — a failed quit must never block eviction.
                pass

    def _evict_expired_locked(self, now: float) -> None:
        """Drop every session idle longer than the TTL. Caller holds the lock."""
        dead = [sid for sid, s in self._sessions.items() if now - s.last_access > self._ttl]
        for sid in dead:
            self._close(self._sessions.pop(sid))

    def get(self, sid: str | None) -> Session | None:
        """Return the live session for *sid* (touching it), or ``None``.

        A session past its TTL is treated as absent (and reaped), so a returned
        session is always fresh.
        """
        if not sid:
            return None
        now = time.monotonic()
        with self._lock:
            self._evict_expired_locked(now)
            sess = self._sessions.get(sid)
            if sess is not None:
                sess.last_access = now
            return sess

    def get_or_create(self, sid: str) -> Session:
        """Return the session for *sid*, creating it if new (enforcing the cap).

        The id is supplied by the caller — minted into the client's cookie by
        :class:`SessionCookieMiddleware` — so a fresh browser's concurrent first
        requests, which all carry the same cookie, converge on one session
        instead of racing to mint their own (and orphaning empties).
        """
        now = time.monotonic()
        with self._lock:
            self._evict_expired_locked(now)
            sess = self._sessions.get(sid)
            if sess is not None:
                sess.last_access = now
                return sess
            # Over the cap (all sessions still active): evict the least-recently
            # used to make room rather than refuse the new client.
            while len(self._sessions) >= self._max:
                lru = min(self._sessions.values(), key=lambda s: s.last_access)
                self._close(self._sessions.pop(lru.sid))
            sess = Session(sid=sid)
            self._sessions[sid] = sess
            return sess

    def close_all(self) -> None:
        """Release every session (server shutdown)."""
        with self._lock:
            for sess in list(self._sessions.values()):
                self._close(sess)
            self._sessions.clear()


class SessionCookieMiddleware:
    """Issue every client an ``sts2_sid`` cookie on its first request.

    A pure-ASGI middleware (not Starlette ``BaseHTTPMiddleware``) so it never
    buffers responses — the SSE advisor streams keep working. It mints an id for
    any request arriving without the cookie and attaches a ``Set-Cookie`` to that
    request's response, so the very first document load establishes the id
    *before* the SPA fires its API calls. Without this, a fresh browser's
    concurrent first requests would each mint their own id (last ``Set-Cookie``
    wins), orphaning empty sessions. The resolved id is published on the ASGI
    scope under :data:`SID_SCOPE_KEY` for the request handlers to read.
    """

    def __init__(self, app: ASGIApp, *, max_age: int = _SID_MAX_AGE) -> None:
        self.app = app
        self._max_age = max_age

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        request = Request(scope)
        sid = request.cookies.get(SID_COOKIE)
        # Already cookied → reuse it (an expired session is re-created under the
        # same id, so the cookie need not be reset). Otherwise mint a fresh id.
        new_sid = None if sid else uuid.uuid4().hex
        scope[SID_SCOPE_KEY] = sid or new_sid

        if new_sid is None:
            await self.app(scope, receive, send)
            return

        # Format a correct Set-Cookie via a throwaway Response, then append it to
        # the response's start message — headers only, no body buffering.
        carrier = Response()
        carrier.set_cookie(
            SID_COOKIE,
            new_sid,
            max_age=self._max_age,
            httponly=True,
            secure=request.url.scheme == "https",
            samesite="lax",
            path="/",
        )
        cookie_value = carrier.headers["set-cookie"]

        async def send_with_cookie(message: Message) -> None:
            if message["type"] == "http.response.start":
                MutableHeaders(raw=message.setdefault("headers", [])).append(
                    "set-cookie", cookie_value
                )
            await send(message)

        await self.app(scope, receive, send_with_cookie)


def install_session_cookie(app: ASGIApp, *, max_age: int = _SID_MAX_AGE) -> None:
    """Wrap *app* so every client is issued an ``sts2_sid`` cookie (call once)."""
    app.add_middleware(SessionCookieMiddleware, max_age=max_age)
