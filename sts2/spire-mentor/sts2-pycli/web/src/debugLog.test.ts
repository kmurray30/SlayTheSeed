import { describe, it, expect } from "vitest";
import { debugSettingsLine, debugStepLine, type DebugSettings } from "./debugLog";
import type { FaithfulDebug } from "./api";

const SETTINGS: DebugSettings = {
  character: "Necrobinder",
  ascension: 5,
  seed: "12345",
  backend: "rust",
  brain: "ppo",
  combat: "quick",
  threads: 1,
  monsterSims: 200,
  bossSims: 800,
};

describe("debugSettingsLine", () => {
  it("includes every setting: ascension, seed, threads, brain, combat, engine, sims", () => {
    const line = debugSettingsLine(SETTINGS);
    expect(line).toContain("Necrobinder");
    expect(line).toContain("A5");
    expect(line).toContain("seed 12345");
    expect(line).toContain("engine rust");
    expect(line).toContain("brain ppo");
    expect(line).toContain("combat quick");
    expect(line).toContain("threads 1");
    expect(line).toContain("sims mon 200 / boss 800");
  });

  it("renders an empty/whitespace seed as (random)", () => {
    expect(debugSettingsLine({ ...SETTINGS, seed: "" })).toContain("seed (random)");
    expect(debugSettingsLine({ ...SETTINGS, seed: "   " })).toContain("seed (random)");
  });
});

describe("debugStepLine", () => {
  it("appends the combat MCTS sim count and seed", () => {
    const dbg: FaithfulDebug = { phase: "combat", summary: "Play Strike", sims: 200, seed: 42 };
    expect(debugStepLine(dbg)).toBe("Play Strike · sims=200 seed=42");
  });

  it("flags boss combat", () => {
    const dbg: FaithfulDebug = { phase: "combat", summary: "Play Bash", sims: 800, is_boss: true };
    expect(debugStepLine(dbg)).toBe("Play Bash · sims=800 boss");
  });

  it("surfaces the card-reward boss-sim depth", () => {
    const dbg: FaithfulDebug = {
      phase: "card_reward", summary: "Choose card", reward: { sims: 3000 },
    };
    expect(debugStepLine(dbg)).toBe("Choose card · reward-sims=3000");
  });

  it("returns the bare summary when no sim detail is present", () => {
    const dbg: FaithfulDebug = { phase: "map", summary: "Choose path" };
    expect(debugStepLine(dbg)).toBe("Choose path");
  });

  it("treats sims=0 as present (cheap heuristic pick), not omitted", () => {
    const dbg: FaithfulDebug = { phase: "card_reward", summary: "Choose card", reward: { sims: 0 } };
    expect(debugStepLine(dbg)).toBe("Choose card · reward-sims=0");
  });
});
