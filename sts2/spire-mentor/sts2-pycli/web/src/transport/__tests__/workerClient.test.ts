import { describe, expect, it, vi } from "vitest";
import { WorkerClient, getWorkerClient, type WorkerLike } from "../workerClient";
import type { WorkerInbound, WorkerOutbound } from "../protocol";

/** A Worker double: records what the page posts, lets the test answer. */
class FakeWorker implements WorkerLike {
  posted: WorkerInbound[] = [];
  onmessage: ((ev: MessageEvent<WorkerOutbound>) => void) | null = null;
  onerror: ((ev: ErrorEvent) => void) | null = null;
  terminated = false;
  postMessage(msg: WorkerInbound) {
    this.posted.push(msg);
  }
  terminate() {
    this.terminated = true;
  }
  reply(msg: WorkerOutbound) {
    this.onmessage?.({ data: msg } as MessageEvent<WorkerOutbound>);
  }
  crash(message: string) {
    this.onerror?.({ message } as ErrorEvent);
  }
  /** Requests only (the init message filtered out). */
  get requests() {
    return this.posted.filter((m) => "id" in m);
  }
}

function setup() {
  const workers: FakeWorker[] = [];
  const client = new WorkerClient({
    spawn: () => {
      const w = new FakeWorker();
      workers.push(w);
      return w;
    },
    assetsBase: "https://cdn.example/art",
  });
  return { client, workers };
}

const tick = () => new Promise((r) => setTimeout(r, 0));

describe("WorkerClient", () => {
  it("spawns lazily, sends init first, then the {id, method, route, body} envelope", async () => {
    const { client, workers } = setup();
    expect(workers).toHaveLength(0);
    const p = client.request("POST", "/api/live/action", { action: "end_turn" });
    expect(workers).toHaveLength(1);
    const w = workers[0];
    expect(w.posted[0]).toEqual({ init: { assets_base: "https://cdn.example/art" } });
    expect(w.posted[1]).toEqual({ id: 1, method: "POST", route: "/api/live/action", body: { action: "end_turn" } });
    w.reply({ id: 1, status: 200, body: { step: 3 } });
    await expect(p).resolves.toEqual({ status: 200, body: { step: 3 } });
  });

  it("serialises requests: one in flight at a time, in call order, ids increasing", async () => {
    const { client, workers } = setup();
    const order: string[] = [];
    const p1 = client.request("GET", "/api/health").then((r) => order.push(`a:${r.status}`));
    const p2 = client.request("GET", "/api/live/state").then((r) => order.push(`b:${r.status}`));
    const p3 = client.request("GET", "/api/live/map").then((r) => order.push(`c:${r.status}`));
    const w = workers[0];
    expect(w.requests).toHaveLength(1);
    w.reply({ id: 1, status: 200, body: null });
    await tick();
    expect(w.requests).toHaveLength(2);
    expect(w.requests[1]).toMatchObject({ id: 2, route: "/api/live/state", body: null });
    w.reply({ id: 2, status: 400, body: { detail: "no live run" } });
    await tick();
    expect(w.requests).toHaveLength(3);
    w.reply({ id: 3, status: 200, body: null });
    await Promise.all([p1, p2, p3]);
    expect(order).toEqual(["a:200", "b:400", "c:200"]);
    expect(workers).toHaveLength(1); // one worker for the lot
  });

  it("forwards {id, progress} frames to listeners without settling the request", async () => {
    const { client, workers } = setup();
    const seen: [number, number, number][] = [];
    const off = client.onProgress((p, id) => seen.push([id, p.done, p.total]));
    const p = client.request("GET", "/api/live/state");
    const w = workers[0];
    w.reply({ id: 1, progress: { done: 1, total: 4 } });
    w.reply({ id: 1, progress: { done: 4, total: 4 } });
    expect(seen).toEqual([[1, 1, 4], [1, 4, 4]]);
    off();
    w.reply({ id: 1, progress: { done: 4, total: 4 } });
    expect(seen).toHaveLength(2);
    w.reply({ id: 1, status: 200, body: { idx: 4 } });
    await expect(p).resolves.toEqual({ status: 200, body: { idx: 4 } });
  });

  it("delivers {id, frame} messages to the request's onFrame, in order, before the reply", async () => {
    const { client, workers } = setup();
    const frames: unknown[] = [];
    const p = client.request("POST", "/api/live/card-advisor/stream", { config: {} }, (f) => frames.push(f));
    const w = workers[0];
    w.reply({ id: 1, frame: { type: "start", total: 2 } });
    w.reply({ id: 1, frame: { type: "tick", done: 1, total: 2 } });
    w.reply({ id: 7, frame: { type: "tick", done: 99, total: 2 } }); // not ours
    w.reply({ id: 1, status: 200, body: null });
    await expect(p).resolves.toEqual({ status: 200, body: null });
    expect(frames).toEqual([{ type: "start", total: 2 }, { type: "tick", done: 1, total: 2 }]);
    // A plain request without onFrame ignores frames rather than throwing.
    const q = client.request("GET", "/api/health");
    w.reply({ id: 2, frame: { type: "tick" } });
    w.reply({ id: 2, status: 200, body: "ok" });
    await expect(q).resolves.toEqual({ status: 200, body: "ok" });
  });

  it("ignores frames whose id is not the in-flight request", async () => {
    const { client, workers } = setup();
    const p = client.request("GET", "/api/health");
    const w = workers[0];
    w.reply({ id: 99, status: 500, body: null });
    w.reply({ id: 1, status: 200, body: "ok" });
    await expect(p).resolves.toEqual({ status: 200, body: "ok" });
  });

  it("a crashed worker rejects everything outstanding and is respawned on the next request", async () => {
    const { client, workers } = setup();
    const p1 = client.request("GET", "/api/health");
    const p2 = client.request("GET", "/api/live/state");
    workers[0].crash("boom");
    await expect(p1).rejects.toThrow(/engine worker crashed: boom/);
    await expect(p2).rejects.toThrow(/engine worker crashed: boom/);
    expect(workers[0].terminated).toBe(true);
    const p3 = client.request("GET", "/api/health");
    expect(workers).toHaveLength(2);
    expect(workers[1].posted[0]).toEqual({ init: { assets_base: "https://cdn.example/art" } });
    workers[1].reply({ id: 3, status: 200, body: null });
    await expect(p3).resolves.toEqual({ status: 200, body: null });
  });

  it("getWorkerClient is a singleton over the global Worker", () => {
    const spawned: FakeWorker[] = [];
    vi.stubGlobal("Worker", class extends FakeWorker {
      constructor() {
        super();
        spawned.push(this);
      }
    });
    try {
      const c = getWorkerClient("/assets");
      expect(getWorkerClient("/other")).toBe(c);
      void c.request("GET", "/api/health");
      expect(spawned).toHaveLength(1);
      expect(spawned[0].posted[0]).toEqual({ init: { assets_base: "/assets" } });
    } finally {
      vi.unstubAllGlobals();
    }
  });
});
