/** "Download for offline" for the browser-only build.
 *
 *  The service worker (public/sw.js) caches art and PPO nets cache-first AS
 *  THEY ARE FETCHED — a fight you have seen renders offline, one you have
 *  not shows holes. This module fills those two caches up front from the
 *  lists the static build emits (`/wb-art-list.json` from
 *  scripts/art_to_dist.py, `/ppo/index.json` from the Vite champion plugin),
 *  writing into the SAME caches under the SAME keys the worker uses, so the
 *  worker's fetch handler serves them afterwards. Written from the page
 *  rather than the worker so progress is a plain callback and the run
 *  survives the worker being restarted.
 *
 *  Sizes: the art is ~4150 files (~70 MB as WebP), the five champions
 *  ~42 MB; everything stays under the free host's 360 MB/day transfer quota
 *  even for a few fresh devices.
 */

export const ART_CACHE = "wb-art-v1";
export const PPO_CACHE = "wb-ppo-v1";

/** Requests in flight at once. The list is ~4,200 SMALL files, so the download
 *  is bound by per-request latency, not bandwidth: at 6 in flight a fast pipe
 *  managed ~1 MB/s. HTTP/2 multiplexes these over one connection, so a high
 *  count costs nothing on the host side. */
export const DEFAULT_CONCURRENCY = 32;

export type OfflineTargets = {
  art: string[];
  ppo: string[];
  /** Shipped bytes of the art list (what a fresh device downloads). */
  artBytes: number;
};

export type OfflineProgress = {
  done: number;
  total: number;
  failed: number;
  /** Bytes fetched so far (from Content-Length when the host sends it). */
  bytes: number;
};

export type OfflineStatus = {
  cachedArt: number;
  cachedPpo: number;
  targets: OfflineTargets;
};

function json<T>(r: Response): Promise<T> {
  if (!r.ok) throw new Error(`${r.status} ${r.url}`);
  return r.json() as Promise<T>;
}

/** The two lists the static build emits; empty lists when a host lacks them. */
export async function offlineTargets(fetchImpl: typeof fetch = fetch): Promise<OfflineTargets> {
  const [art, ppo] = await Promise.all([
    fetchImpl("/wb-art-list.json", { cache: "no-cache" })
      .then(json<{ files: { path: string; bytes: number }[]; bytes: number }>)
      .catch(() => ({ files: [], bytes: 0 })),
    fetchImpl("/ppo/index.json", { cache: "no-cache" })
      .then(json<{ files: string[] }>)
      .catch(() => ({ files: [] })),
  ]);
  return {
    art: art.files.map((f) => `/assets/${f.path}`),
    ppo: ppo.files.map((f) => `/ppo/${f}`),
    artBytes: art.bytes,
  };
}

async function countCached(name: string, urls: string[]): Promise<number> {
  if (urls.length === 0) return 0;
  const cache = await caches.open(name);
  const keys = await cache.keys();
  const have = new Set(keys.map((r) => new URL(r.url).pathname));
  return urls.filter((u) => have.has(u)).length;
}

/** What the caches already hold of the two lists. */
export async function offlineStatus(fetchImpl: typeof fetch = fetch): Promise<OfflineStatus> {
  const targets = await offlineTargets(fetchImpl);
  const [cachedArt, cachedPpo] = await Promise.all([
    countCached(ART_CACHE, targets.art),
    countCached(PPO_CACHE, targets.ppo),
  ]);
  return { cachedArt, cachedPpo, targets };
}

export function isOfflineReady(s: OfflineStatus): boolean {
  const total = s.targets.art.length + s.targets.ppo.length;
  return total > 0 && s.cachedArt + s.cachedPpo >= total;
}

/** `true` when the page can write the caches at all (secure context + API). */
export function canDownloadForOffline(): boolean {
  return typeof caches !== "undefined" && typeof fetch === "function";
}

type Options = {
  concurrency?: number;
  signal?: AbortSignal;
  fetchImpl?: typeof fetch;
  onProgress?: (p: OfflineProgress) => void;
};

/** Fill the art and PPO caches; resolves with the final tally. */
export async function downloadForOffline(
  targets: OfflineTargets,
  { concurrency = DEFAULT_CONCURRENCY, signal, fetchImpl = fetch, onProgress }: Options = {},
): Promise<OfflineProgress> {
  // With a controlling service worker every fetch below already lands in the
  // right cache (its handler stores art and champions cache-first), so the
  // page writes nothing itself — a second `put` per file doubled the disk
  // work, which on a tablet is what the download waits on, not the network.
  const workerCaches =
    typeof navigator !== "undefined" && !!navigator.serviceWorker?.controller;
  const plan: [string, string][] = [
    ...targets.art.map((u): [string, string] => [ART_CACHE, u]),
    ...targets.ppo.map((u): [string, string] => [PPO_CACHE, u]),
  ];
  const progress: OfflineProgress = { done: 0, total: plan.length, failed: 0, bytes: 0 };
  const caches_ = new Map<string, Cache>();
  for (const name of [ART_CACHE, PPO_CACHE]) caches_.set(name, await caches.open(name));
  let next = 0;
  const report = () => onProgress?.({ ...progress });
  const worker = async () => {
    while (next < plan.length) {
      if (signal?.aborted) return;
      const [name, url] = plan[next++];
      const cache = caches_.get(name)!;
      try {
        if (!(await cache.match(url))) {
          const res = await fetchImpl(url, { signal });
          if (!res.ok) throw new Error(String(res.status));
          const len = Number(res.headers.get("content-length") ?? 0);
          if (workerCaches) await res.arrayBuffer();
          else await cache.put(url, res);
          progress.bytes += Number.isFinite(len) ? len : 0;
        }
      } catch {
        if (signal?.aborted) return;
        progress.failed += 1;
      }
      progress.done += 1;
      report();
    }
  };
  await Promise.all(Array.from({ length: Math.max(1, concurrency) }, worker));
  report();
  return progress;
}

export function formatMb(bytes: number): string {
  return `${Math.max(1, Math.round(bytes / 1e6))} MB`;
}
