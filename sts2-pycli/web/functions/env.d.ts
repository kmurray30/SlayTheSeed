/**
 * The slice of Cloudflare's Workers runtime the bug-report Function uses,
 * hand-typed so `functions/` type-checks without @cloudflare/workers-types
 * (which would also re-declare `fetch`/`Request` against the DOM lib the
 * SPA compiles with). Keep it to what the Functions call.
 */
export interface KvListKey {
  name: string;
  expiration?: number;
  metadata?: unknown;
}

export interface KvNamespace {
  get(key: string, type: "text"): Promise<string | null>;
  put(
    key: string,
    value: string,
    options?: { expirationTtl?: number; metadata?: unknown },
  ): Promise<void>;
  list(options?: { prefix?: string; limit?: number; cursor?: string }): Promise<{
    keys: KvListKey[];
    list_complete: boolean;
    cursor?: string;
  }>;
}

/** Bindings from wrangler.toml. */
export interface Env {
  BUGREPORTS: KvNamespace;
}

/** What Pages hands a Function (the fields used here). */
export interface EventContext<E, P extends string = string> {
  request: Request;
  env: E;
  params: Record<P, string | string[]>;
}

export type PagesFunction<E, P extends string = string> = (
  context: EventContext<E, P>,
) => Response | Promise<Response>;
