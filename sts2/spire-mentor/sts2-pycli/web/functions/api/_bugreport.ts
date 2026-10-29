/**
 * Bug reports for the browser-only workbench — the one server-side piece of
 * the static site, a Cloudflare Pages Function over a Workers KV namespace
 * (binding `BUGREPORTS`, wrangler.toml).
 *
 * The Python workbench captures a report server-side (workbench/bugreport.py
 * builds the dump, bugreport_store.py puts it in GCS). Here the PAGE builds
 * the dump (src/bugreport.ts: it has the engine in its worker) and posts the
 * finished JSON; this side only validates, stamps `meta.id` /
 * `meta.captured_at` the way the Python route layer does, stores, and serves
 * it back. Same schema, same id format (`wb-` + 16 hex), so `?report=<id>`
 * opens either kind and `scripts/bug_reports.py` reads both.
 *
 * Routes (functions/api/bugreport.ts, functions/api/bugreport/[id].ts):
 *   GET  /api/bugreport        {ok, store}  — the probe the SPA uses to show
 *                              the button (a host without Functions answers
 *                              HTML or 404, never this JSON).
 *   POST /api/bugreport        the dump → {id, url}
 *   GET  /api/bugreport/<id>   the stored dump
 *
 * Abuse budget: there is no rate-limit binding on Pages' free plan. The
 * cap is the body size (a flood of 20 MiB bodies still costs nothing — KV
 * writes are free up to 1,000 a day and then fail, which is the worst
 * case: reports lost for a day) plus a same-origin check on POST, which
 * stops drive-by posts without stopping anything a browser does.
 */
import type { Env, KvNamespace } from "../env";

export const ID_RE = /^wb-[0-9a-f]{16}$/;
export const KEY_PREFIX = "bugreports/";
/** KV values cap at 25 MiB; leave room for the stamped fields. */
export const MAX_BODY_BYTES = 20 * 1024 * 1024;
/** Reports self-clean after this; a bug older than that has been fixed or
 *  re-reported (the free tier's 1 GB is the reason to clean at all). */
export const TTL_SECONDS = 180 * 24 * 3600;
const NOTE_META_CHARS = 200;

export type Json = Record<string, unknown>;

/** `wb-` + 16 hex chars (64 random bits) — the id is the only barrier to
 *  reading a report, so it must be unguessable. */
export function newId(random: (n: number) => Uint8Array = randomBytes): string {
  return `wb-${[...random(8)].map((b) => b.toString(16).padStart(2, "0")).join("")}`;
}

function randomBytes(n: number): Uint8Array {
  return crypto.getRandomValues(new Uint8Array(n));
}

export function isoNow(now: () => Date = () => new Date()): string {
  return now().toISOString().replace(/\.\d{3}Z$/, "Z");
}

export function json(body: unknown, status = 200, extra: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json", "cache-control": "no-store", ...extra },
  });
}

export function detail(status: number, msg: string): Response {
  return json({ detail: msg }, status);
}

const isObject = (v: unknown): v is Json => typeof v === "object" && v !== null && !Array.isArray(v);

/** The dump shape src/bugreport.ts builds (schema 1, the Python one). */
export function validateDump(body: unknown): string | null {
  if (!isObject(body)) return "body must be a JSON object";
  if (body.schema_version !== 1) return "schema_version must be 1";
  for (const k of ["meta", "repro", "state", "ui"]) {
    if (!isObject(body[k])) return `${k} must be an object`;
  }
  if (body.note != null && typeof body.note !== "string") return "note must be a string or null";
  return null;
}

/** What a listing shows without fetching the value (KV metadata ≤ 1024 B). */
export function metadataOf(dump: Json, capturedAt: string): Json {
  const meta = dump.meta as Json;
  const ui = dump.ui as Json;
  const note = typeof dump.note === "string" ? dump.note.slice(0, NOTE_META_CHARS) : null;
  return {
    captured_at: capturedAt,
    mode: meta.mode ?? null,
    character: meta.character ?? null,
    screen: ui.screen ?? null,
    build: meta.workbench_commit ?? null,
    note,
  };
}

export interface StoreDeps {
  now?: () => Date;
  random?: (n: number) => Uint8Array;
}

/** Stamp + store a validated dump; returns the id. */
export async function storeDump(
  kv: KvNamespace,
  dump: Json,
  request: Request,
  deps: StoreDeps = {},
): Promise<string> {
  const id = newId(deps.random);
  const capturedAt = isoNow(deps.now);
  const meta = dump.meta as Json;
  meta.id = id;
  meta.captured_at = capturedAt;
  meta.received_via = "pages";
  const ua = request.headers.get("user-agent");
  if (ua && meta.user_agent == null) meta.user_agent = ua.slice(0, 300);
  await kv.put(KEY_PREFIX + id, JSON.stringify(dump), {
    expirationTtl: TTL_SECONDS,
    metadata: metadataOf(dump, capturedAt),
  });
  return id;
}

/** `POST /api/bugreport`. */
export async function handlePost(request: Request, env: Env, deps: StoreDeps = {}): Promise<Response> {
  const url = new URL(request.url);
  const origin = request.headers.get("origin");
  if (origin !== url.origin) return detail(403, "bug reports are accepted from the workbench page only");
  const declared = Number(request.headers.get("content-length") ?? "0");
  if (declared > MAX_BODY_BYTES) return detail(413, `report too large (${declared} bytes; limit ${MAX_BODY_BYTES})`);
  const text = await request.text();
  if (text.length > MAX_BODY_BYTES) return detail(413, `report too large (${text.length} chars; limit ${MAX_BODY_BYTES})`);
  let body: unknown;
  try {
    body = JSON.parse(text);
  } catch {
    return detail(400, "body is not JSON");
  }
  const problem = validateDump(body);
  if (problem) return detail(422, problem);
  const id = await storeDump(env.BUGREPORTS, body as Json, request, deps);
  return json({ id, url: `${url.origin}/?report=${id}` }, 201);
}

/** `GET /api/bugreport` — the probe. */
export function handleProbe(): Response {
  return json({ ok: true, store: "kv" });
}

/** `GET /api/bugreport/<id>`. */
export async function handleGet(id: string, env: Env): Promise<Response> {
  if (!ID_RE.test(id)) return detail(404, `unknown report: ${id}`);
  const text = await env.BUGREPORTS.get(KEY_PREFIX + id, "text");
  if (text == null) return detail(404, `unknown report: ${id}`);
  return new Response(text, {
    status: 200,
    // Immutable once written; a reader may cache it.
    headers: { "content-type": "application/json", "cache-control": "public, max-age=3600" },
  });
}
