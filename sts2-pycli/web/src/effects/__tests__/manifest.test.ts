import { describe, it, expect, vi } from "vitest";
import { loadManifest } from "../manifest";

const MANIFEST = {
  rev: "v0.107.1-1",
  effects: {
    vfx_block: {
      kind: "flipbook", frames: ["vfx/vfx_block/vfx_block_00.png"], fps: 15,
      loop: false, scale: 1, randomizeRotation: false, rotationRange: [0, 0],
    },
  },
};

describe("loadManifest", () => {
  it("fetches the manifest with a cache-busting parameter", async () => {
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true, json: async () => MANIFEST,
    } as Response);
    await loadManifest(fetchImpl as unknown as typeof fetch);
    const url = String(fetchImpl.mock.calls[0]![0]);
    expect(url).toContain("vfx/manifest.json");
    // The art bucket serves immutable at non-content-addressed URLs.
    expect(url).toMatch(/[?&]v=/);
  });

  it("returns the parsed manifest", async () => {
    const fetchImpl = vi.fn().mockResolvedValue({
      ok: true, json: async () => MANIFEST,
    } as Response);
    const m = await loadManifest(fetchImpl as unknown as typeof fetch);
    expect(m.effects.vfx_block!.kind).toBe("flipbook");
  });

  it("rejects on a failed fetch rather than returning an empty manifest", async () => {
    const fetchImpl = vi.fn().mockResolvedValue({ ok: false, status: 404 } as Response);
    await expect(loadManifest(fetchImpl as unknown as typeof fetch)).rejects.toThrow();
  });
});

// `effectsForEvent` moved to `bindings.ts` (Task 9) — see
// `__tests__/bindings.test.ts` for its coverage. It now selects from the
// extracted binding table instead of this module's old hardcoded guess.
