import { afterEach, describe, expect, it, vi } from "vitest";

const { request, onProgress } = vi.hoisted(() => ({ request: vi.fn(), onProgress: vi.fn() }));
vi.mock("../workerClient", () => ({
  getWorkerClient: () => ({ request, onProgress }),
}));

import { apiFetch, configuredAssetsBase, isStaticMode, onRestoreProgress } from "../index";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  request.mockReset();
  onProgress.mockReset();
});

describe("apiFetch — non-static mode (the Python backend)", () => {
  it("passes every call straight to the global fetch, resolved at call time", async () => {
    const resp = new Response("{}", { status: 200 });
    const fetch = vi.fn(async () => resp);
    vi.stubGlobal("fetch", fetch); // AFTER import: the transport must not capture fetch
    const init = { method: "POST", body: "{}" };
    expect(isStaticMode()).toBe(false);
    await expect(apiFetch("/api/live/action", init)).resolves.toBe(resp);
    expect(fetch).toHaveBeenCalledWith("/api/live/action", init);
    expect(request).not.toHaveBeenCalled();
  });

  it("onRestoreProgress is a no-op subscription", () => {
    const off = onRestoreProgress(() => {});
    expect(onProgress).not.toHaveBeenCalled();
    expect(() => off()).not.toThrow();
  });
});

describe("apiFetch — static mode (the WASM worker)", () => {
  it("routes /api/* to the worker and wraps its reply in a JSON Response", async () => {
    vi.stubEnv("VITE_WORKBENCH_MODE", "static");
    const fetch = vi.fn();
    vi.stubGlobal("fetch", fetch);
    request.mockResolvedValue({ status: 409, body: { detail: "replay diverged" } });

    const r = await apiFetch("/api/live/seek", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ node_id: "n7" }),
    });

    expect(request).toHaveBeenCalledWith("POST", "/api/live/seek", { node_id: "n7" });
    expect(fetch).not.toHaveBeenCalled();
    expect(r.status).toBe(409);
    expect(r.ok).toBe(false);
    expect(r.headers.get("content-type")).toBe("application/json");
    await expect(r.json()).resolves.toEqual({ detail: "replay diverged" });
  });

  it("a bare GET sends a null body and keeps the query string on the route", async () => {
    vi.stubEnv("VITE_WORKBENCH_MODE", "static");
    request.mockResolvedValue({ status: 200, body: { ok: true } });
    const r = await apiFetch("/api/runs/stat?path=a%20b");
    expect(request).toHaveBeenCalledWith("GET", "/api/runs/stat?path=a%20b", null);
    expect(r.ok).toBe(true);
    await expect(r.json()).resolves.toEqual({ ok: true });
  });

  it("a …/stream route is an NDJSON Response fed by the worker's frames", async () => {
    vi.stubEnv("VITE_WORKBENCH_MODE", "static");
    request.mockImplementation(
      async (_m: string, _r: string, _b: unknown, onFrame: (f: unknown) => void) => {
        onFrame({ type: "start", total: 1 });
        onFrame({ type: "tick", done: 1, total: 1 });
        onFrame({ type: "result", candidates: [] });
        return { status: 200, body: null };
      },
    );
    const r = await apiFetch("/api/live/card-advisor/stream", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ config: { rollouts: 1 } }),
    });
    expect(request).toHaveBeenCalledWith(
      "POST",
      "/api/live/card-advisor/stream",
      { config: { rollouts: 1 } },
      expect.any(Function),
    );
    expect(r.ok).toBe(true);
    expect(r.headers.get("content-type")).toBe("application/x-ndjson");
    const text = await r.text();
    expect(text.split("\n").filter(Boolean).map((l) => JSON.parse(l))).toEqual([
      { type: "start", total: 1 },
      { type: "tick", done: 1, total: 1 },
      { type: "result", candidates: [] },
    ]);
  });

  it("a non-200 reply on a stream route becomes a trailing error line (the status is already 200)", async () => {
    vi.stubEnv("VITE_WORKBENCH_MODE", "static");
    request.mockResolvedValue({ status: 400, body: { detail: "no live run" } });
    const r = await apiFetch("/api/live/decision-advisor/stream", { method: "POST", body: "{}" });
    expect(r.status).toBe(200);
    expect(await r.text()).toBe(`${JSON.stringify({ type: "error", error: "no live run" })}\n`);
  });

  it("a crashed worker errors the stream so the reader throws", async () => {
    vi.stubEnv("VITE_WORKBENCH_MODE", "static");
    request.mockRejectedValue(new Error("engine worker crashed: boom"));
    const r = await apiFetch("/api/live/decision-advisor/stream", { method: "POST", body: "{}" });
    await expect(r.text()).rejects.toThrow(/crashed/);
  });

  it("non-API URLs (art, other origins) still go to the network", async () => {
    vi.stubEnv("VITE_WORKBENCH_MODE", "static");
    const fetch = vi.fn(async () => new Response("png"));
    vi.stubGlobal("fetch", fetch);
    await apiFetch("/assets/cards/strike.png");
    await apiFetch("https://cdn.example/api/health");
    expect(fetch).toHaveBeenCalledTimes(2);
    expect(request).not.toHaveBeenCalled();
  });

  it("onRestoreProgress subscribes to the worker's progress frames", () => {
    vi.stubEnv("VITE_WORKBENCH_MODE", "static");
    const off = vi.fn();
    onProgress.mockReturnValue(off);
    const cb = vi.fn();
    const unsubscribe = onRestoreProgress(cb);
    expect(onProgress).toHaveBeenCalledOnce();
    // The worker client hands (progress, id); the page only wants progress.
    onProgress.mock.calls[0][0]({ done: 3, total: 9 }, 1);
    expect(cb).toHaveBeenCalledWith({ done: 3, total: 9 });
    unsubscribe();
    expect(off).toHaveBeenCalledOnce();
  });
});

describe("configuredAssetsBase", () => {
  it("defaults to /assets and strips a trailing slash from VITE_ASSETS_BASE", () => {
    expect(configuredAssetsBase()).toBe("/assets");
    vi.stubEnv("VITE_ASSETS_BASE", "https://cdn.example/art/");
    expect(configuredAssetsBase()).toBe("https://cdn.example/art");
    vi.stubEnv("VITE_ASSETS_BASE", "");
    expect(configuredAssetsBase()).toBe("/assets");
  });
});
