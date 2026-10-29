import { describe, it, expect, vi } from "vitest";
import type { Step } from "./api";
import { computeRunEnd, shouldShowViewToggle } from "./runControl";

function mkStep(): Step {
  return {
    idx: 0,
    decision: "map_select",
    state: { decision: "map_select", player: { name: "Silent", hp: 20, max_hp: 50 } } as Step["state"],
    scores: [],
    pv: null,
    chosen_action: null,
    source: "live",
    meta: { turn: null, step_no: 0 },
  };
}

describe("computeRunEnd", () => {
  it("carries the log path through on a successful quit", async () => {
    const snap = mkStep();
    const r = await computeRunEnd(snap, async () => ({ log_path: "/logs/run.jsonl" }));
    expect(r.step).toBe(snap);
    expect(r.logPath).toBe("/logs/run.jsonl");
  });

  it("still ends the run (never rejects) when quit() throws — the regression guard", async () => {
    const snap = mkStep();
    const quit = vi.fn(async () => {
      throw new Error("HTTP 400: no live run");
    });
    // Must resolve, not reject: the caller's teardown must always run so the
    // live run can never stay stranded on screen.
    const r = await computeRunEnd(snap, quit);
    expect(quit).toHaveBeenCalledOnce();
    expect(r.step).toBe(snap);
    expect(r.logPath).toBeNull();
  });

  it("treats a missing/null log_path as no replay", async () => {
    const r = await computeRunEnd(mkStep(), async () => ({ log_path: null }));
    expect(r.logPath).toBeNull();
  });
});

describe("shouldShowViewToggle", () => {
  it("hidden when there is no live run, no just-ended run, and no loaded replay", () => {
    expect(shouldShowViewToggle({ hasLive: false, hasEndedRun: false, replayCount: 0 })).toBe(false);
  });
  it("shown while a live run is active", () => {
    expect(shouldShowViewToggle({ hasLive: true, hasEndedRun: false, replayCount: 0 })).toBe(true);
  });
  it("shown on the run-ended summary", () => {
    expect(shouldShowViewToggle({ hasLive: false, hasEndedRun: true, replayCount: 0 })).toBe(true);
  });
  it("shown while reviewing a loaded replay", () => {
    expect(shouldShowViewToggle({ hasLive: false, hasEndedRun: false, replayCount: 42 })).toBe(true);
  });
});
