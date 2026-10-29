/**
 * `apiFetch`: the one door every `/api/*` call in `api.ts` goes through.
 *
 * Non-static mode (dev proxy / Cloud Run): a plain `fetch`, resolved at call
 * time so tests can stub the global after import.
 *
 * Static mode (`VITE_WORKBENCH_MODE=static`, the browser-only workbench): an
 * `/api/*` URL is routed to the WASM engine in a Web Worker and the reply is
 * wrapped in a real `Response`, so `api.ts` and everything above it cannot
 * tell the two apart. A streaming route (`…/stream`, the advisors' NDJSON
 * progress) becomes a `Response` whose body is a `ReadableStream` fed one
 * line per worker frame, so `api.ts`'s line readers work unchanged.
 * Non-API URLs (art, etc.) still hit the network.
 */
import { getWorkerClient, type Progress } from "./workerClient";

export function isStaticMode(): boolean {
  return import.meta.env.VITE_WORKBENCH_MODE === "static";
}

/** `VITE_ASSETS_BASE` (trailing slash stripped) or the local `/assets` mount. */
export function configuredAssetsBase(): string {
  const base = import.meta.env.VITE_ASSETS_BASE;
  return (typeof base === "string" && base ? base : "/assets").replace(/\/$/, "");
}

function apiRoute(input: string): string | null {
  let url: URL;
  try {
    url = new URL(input, globalThis.location?.href ?? "http://localhost/");
  } catch {
    return null;
  }
  if (globalThis.location && url.origin !== globalThis.location.origin) return null;
  if (url.pathname !== "/api" && !url.pathname.startsWith("/api/")) return null;
  return url.pathname + url.search;
}

function parseBody(body: BodyInit | null | undefined): unknown {
  if (body == null) return null;
  if (typeof body === "string") return body ? JSON.parse(body) : null;
  throw new Error("apiFetch(static): only JSON string bodies are supported");
}

/** Routes whose body is newline-delimited JSON streamed as it is produced. */
export function isStreamRoute(route: string): boolean {
  return route.split("?")[0].endsWith("/stream");
}

/** A streaming route as a `Response`: status 200 (an HTTP status cannot
 *  change once the body has started — the worker answers "not applicable"
 *  and "no live run" as an `{"type": "error"}` line, like the Python
 *  server), `application/x-ndjson`, one line per frame, closed when the
 *  worker's reply lands. A rejected request (a crashed worker) errors the
 *  stream so the reader's loop throws. */
function streamResponse(method: string, route: string, body: unknown): Response {
  const client = getWorkerClient(configuredAssetsBase());
  const enc = new TextEncoder();
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      client
        .request(method, route, body, (frame) => {
          controller.enqueue(enc.encode(`${JSON.stringify(frame)}\n`));
        })
        .then(
          (reply) => {
            if (reply.status !== 200) {
              const detail = (reply.body as { detail?: unknown } | null)?.detail;
              const error = typeof detail === "string" ? detail : `HTTP ${reply.status}`;
              controller.enqueue(enc.encode(`${JSON.stringify({ type: "error", error })}\n`));
            }
            controller.close();
          },
          (err: unknown) => controller.error(err),
        );
    },
  });
  return new Response(stream, {
    status: 200,
    headers: { "content-type": "application/x-ndjson" },
  });
}

export async function apiFetch(input: string, init?: RequestInit): Promise<Response> {
  const route = isStaticMode() ? apiRoute(input) : null;
  if (route == null) return globalThis.fetch(input, init);
  const method = (init?.method ?? "GET").toUpperCase();
  if (isStreamRoute(route)) return streamResponse(method, route, parseBody(init?.body));
  const { status, body } = await getWorkerClient(configuredAssetsBase()).request(
    method,
    route,
    parseBody(init?.body),
  );
  return new Response(JSON.stringify(body ?? null), {
    status,
    headers: { "content-type": "application/json" },
  });
}

declare global {
  var __sts2ApiFetch: typeof apiFetch | undefined;
}

// Static mode only: the shim on `window`, so a script driving the page from
// outside the bundle (the phone-speed bench in docs/browser-workbench.md, a
// Playwright probe) can call `/api/bench` through the worker. Never set on
// the server-backed build, where a plain `fetch` already reaches the API.
if (isStaticMode() && typeof globalThis !== "undefined") {
  globalThis.__sts2ApiFetch = apiFetch;
}

/** Progress frames from the worker while it restores a persisted run
 *  (`GET /api/live/state` after a reload). No-op outside static mode. */
export function onRestoreProgress(cb: (p: Progress) => void): () => void {
  if (!isStaticMode()) return () => {};
  return getWorkerClient(configuredAssetsBase()).onProgress((p) => cb(p));
}

export type { Progress };
