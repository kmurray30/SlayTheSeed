import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  ART_CACHE,
  PPO_CACHE,
  downloadForOffline,
  isOfflineReady,
  offlineStatus,
  offlineTargets,
} from "../offline";

/** A minimal CacheStorage: enough of the API `offline.ts` touches. */
class FakeCache {
  store = new Map<string, Response>();
  async match(url: string | Request) {
    return this.store.get(typeof url === "string" ? url : url.url);
  }
  async put(url: string, res: Response) {
    this.store.set(url, res);
  }
  async keys() {
    return [...this.store.keys()].map((u) => new Request(new URL(u, "http://localhost/").href));
  }
}
const store = new Map<string, FakeCache>();
const fakeCaches = {
  open: async (name: string) => {
    let c = store.get(name);
    if (!c) store.set(name, (c = new FakeCache()));
    return c;
  },
};

const ART = { files: [{ path: "card_atlas/a.png", bytes: 10 }, { path: "audio/x.webm", bytes: 5 }], bytes: 15 };
const PPO = { files: ["ironclad.onnx"] };

function fakeFetch(fail: Set<string> = new Set()): typeof fetch {
  return (async (input: RequestInfo | URL) => {
    const url = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
    if (url.startsWith("/wb-art-list.json")) return new Response(JSON.stringify(ART));
    if (url.startsWith("/ppo/index.json")) return new Response(JSON.stringify(PPO));
    if (fail.has(url)) return new Response("nope", { status: 404 });
    return new Response("bytes", { headers: { "content-length": "5" } });
  }) as typeof fetch;
}

beforeEach(() => {
  store.clear();
  vi.stubGlobal("caches", fakeCaches);
});

describe("offlineTargets", () => {
  it("maps the two build lists onto the URLs the service worker caches", async () => {
    const t = await offlineTargets(fakeFetch());
    expect(t.art).toEqual(["/assets/card_atlas/a.png", "/assets/audio/x.webm"]);
    expect(t.ppo).toEqual(["/ppo/ironclad.onnx"]);
    expect(t.artBytes).toBe(15);
  });
  it("is empty, not an error, on a host without the lists", async () => {
    const f = (async () => new Response("", { status: 404 })) as typeof fetch;
    const t = await offlineTargets(f);
    expect(t).toEqual({ art: [], ppo: [], artBytes: 0 });
  });
});

describe("downloadForOffline", () => {
  it("fills both caches under the worker's keys and reports progress", async () => {
    const seen: number[] = [];
    const t = await offlineTargets(fakeFetch());
    const p = await downloadForOffline(t, { fetchImpl: fakeFetch(), concurrency: 2, onProgress: (x) => seen.push(x.done) });
    expect(p).toEqual({ done: 3, total: 3, failed: 0, bytes: 15 });
    expect(seen[seen.length - 1]).toBe(3);
    expect([...store.get(ART_CACHE)!.store.keys()].sort()).toEqual(["/assets/audio/x.webm", "/assets/card_atlas/a.png"]);
    expect([...store.get(PPO_CACHE)!.store.keys()]).toEqual(["/ppo/ironclad.onnx"]);
    const s = await offlineStatus(fakeFetch());
    expect(isOfflineReady(s)).toBe(true);
  });
  it("skips what is cached, counts failures, and never throws for one bad file", async () => {
    const t = await offlineTargets(fakeFetch());
    const art = await fakeCaches.open(ART_CACHE);
    await art.put("/assets/card_atlas/a.png", new Response("old"));
    const p = await downloadForOffline(t, { fetchImpl: fakeFetch(new Set(["/ppo/ironclad.onnx"])) });
    expect(p.done).toBe(3);
    expect(p.failed).toBe(1);
    expect(p.bytes).toBe(5); // only the audio file was fetched
    expect(await art.match("/assets/card_atlas/a.png")).toBeDefined();
    expect(isOfflineReady(await offlineStatus(fakeFetch()))).toBe(false);
  });
  it("stops at the abort signal", async () => {
    const t = await offlineTargets(fakeFetch());
    const ctrl = new AbortController();
    ctrl.abort();
    const p = await downloadForOffline(t, { fetchImpl: fakeFetch(), signal: ctrl.signal });
    expect(p.done).toBe(0);
  });
});
