import { describe, it, expect } from "vitest";
import { combatDiff } from "../combatDiff";
import type { CombatState } from "../../api";

// Minimal CombatState factory — only the fields combatDiff reads.
function state(over: Partial<CombatState> = {}): CombatState {
  return {
    decision: "combat_play", round: 1, energy: 3, max_energy: 3,
    draw_pile_count: 0, discard_pile_count: 0, exhaust_pile_count: 0,
    player: { name: "Ironclad", hp: 80, max_hp: 80, block: 0 },
    player_powers: [],
    enemies: [],
    hand: [],
    ...over,
  } as CombatState;
}

function enemy(over: Partial<CombatState["enemies"][number]>) {
  return { index: 0, name: "Cultist", hp: 50, max_hp: 50, block: 0, image: null, ...over };
}

describe("combatDiff — hurt", () => {
  it("returns [] when prev is null", () => {
    expect(combatDiff(null, state())).toEqual([]);
  });

  it("emits a hurt event when an enemy loses hp", () => {
    const prev = state({ enemies: [enemy({ hp: 50 })] });
    const next = state({ enemies: [enemy({ hp: 41 })] });
    expect(combatDiff(prev, next)).toEqual([
      { kind: "hurt", target: { side: "enemy", index: 0 }, amount: 9 },
    ]);
  });

  it("emits a hurt event when the player loses hp", () => {
    const prev = state({ player: { name: "Ironclad", hp: 80, max_hp: 80, block: 0 } });
    const next = state({ player: { name: "Ironclad", hp: 74, max_hp: 80, block: 0 } });
    expect(combatDiff(prev, next)).toEqual([
      { kind: "hurt", target: { side: "player" }, amount: 6 },
    ]);
  });

  it("does not emit hurt on heal (hp up) — emits heal instead", () => {
    const prev = state({ enemies: [enemy({ hp: 40 })] });
    const next = state({ enemies: [enemy({ hp: 50 })] });
    const events = combatDiff(prev, next);
    expect(events.some((e) => e.kind === "hurt")).toBe(false);
    expect(events).toContainEqual(
      { kind: "heal", target: { side: "enemy", index: 0 }, amount: 10 },
    );
  });
});

describe("combatDiff — heal", () => {
  it("emits a heal event when the player's hp rises", () => {
    const prev = state({ player: { name: "Ironclad", hp: 40, max_hp: 80, block: 0 } });
    const next = state({ player: { name: "Ironclad", hp: 55, max_hp: 80, block: 0 } });
    expect(combatDiff(prev, next)).toContainEqual(
      { kind: "heal", target: { side: "player" }, amount: 15 },
    );
  });

  it("does not emit heal on a max-hp increase alone (hp itself unchanged)", () => {
    const prev = state({ player: { name: "Ironclad", hp: 40, max_hp: 80, block: 0 } });
    const next = state({ player: { name: "Ironclad", hp: 40, max_hp: 88, block: 0 } });
    const events = combatDiff(prev, next);
    expect(events.some((e) => e.kind === "heal")).toBe(false);
  });

  it("does not synthesise a heal for a newly-spawned enemy (absent in prev)", () => {
    const prev = state({ enemies: [] });
    const next = state({ enemies: [enemy({ index: 0, hp: 50 })] });
    expect(combatDiff(prev, next)).toEqual([]);
  });
});

describe("combatDiff — block", () => {
  it("emits blockGain when block increases", () => {
    const prev = state({ enemies: [enemy({ block: 0 })] });
    const next = state({ enemies: [enemy({ block: 12 })] });
    expect(combatDiff(prev, next)).toContainEqual(
      { kind: "blockGain", target: { side: "enemy", index: 0 }, amount: 12 },
    );
  });

  it("emits blockBreak when block falls to zero", () => {
    const prev = state({ enemies: [enemy({ block: 8, hp: 50 })] });
    const next = state({ enemies: [enemy({ block: 0, hp: 50 })] });
    expect(combatDiff(prev, next)).toContainEqual(
      { kind: "blockBreak", target: { side: "enemy", index: 0 } },
    );
  });

  it("does not emit blockBreak when block merely decreases but remains", () => {
    const prev = state({ enemies: [enemy({ block: 8 })] });
    const next = state({ enemies: [enemy({ block: 3 })] });
    const events = combatDiff(prev, next);
    expect(events.some((e) => e.kind === "blockBreak")).toBe(false);
  });
});

describe("combatDiff — powers & death", () => {
  it("emits powerAdd for a newly applied power, carrying its stack total", () => {
    const prev = state({ enemies: [enemy({ powers: [] })] });
    const next = state({ enemies: [enemy({ powers: [{ name: "Vulnerable", amount: 2 }] })] });
    expect(combatDiff(prev, next)).toContainEqual(
      { kind: "powerAdd", target: { side: "enemy", index: 0 }, power: "Vulnerable", amount: 2 },
    );
  });

  it("emits powerRemove when a power disappears, with its last known total", () => {
    // From `prev`: by `next` the power is gone, so `next` has no amount to give.
    const prev = state({ enemies: [enemy({ powers: [{ name: "Vulnerable", amount: 2 }] })] });
    const next = state({ enemies: [enemy({ powers: [] })] });
    expect(combatDiff(prev, next)).toContainEqual(
      { kind: "powerRemove", target: { side: "enemy", index: 0 }, power: "Vulnerable", amount: 2 },
    );
  });

  it("carries a NEGATIVE total through, which is what flips a power's rendered valence", () => {
    // `PowerModel.GetTypeForAmount` makes -3 Strength a debuff; the sign has to
    // survive the diff for `powerValence.ts` to see it at all.
    const prev = state({ enemies: [enemy({ powers: [] })] });
    const next = state({ enemies: [enemy({ powers: [{ name: "Strength", amount: -3 }] })] });
    expect(combatDiff(prev, next)).toContainEqual(
      { kind: "powerAdd", target: { side: "enemy", index: 0 }, power: "Strength", amount: -3 },
    );
  });

  it("emits death when an enemy disappears from the array", () => {
    const prev = state({ enemies: [enemy({ index: 0 }), enemy({ index: 1, name: "B" })] });
    const next = state({ enemies: [enemy({ index: 1, name: "B" })] });
    expect(combatDiff(prev, next)).toContainEqual(
      { kind: "death", target: { side: "enemy", index: 0 } },
    );
  });

  it("emits death (and hurt) when an enemy is present but at 0 hp", () => {
    const prev = state({ enemies: [enemy({ index: 0, hp: 5 })] });
    const next = state({ enemies: [enemy({ index: 0, hp: 0 })] });
    const events = combatDiff(prev, next);
    expect(events).toContainEqual({ kind: "hurt", target: { side: "enemy", index: 0 }, amount: 5 });
    expect(events).toContainEqual({ kind: "death", target: { side: "enemy", index: 0 } });
  });

  it("emits no events for a newly-spawned enemy (absent in prev)", () => {
    const prev = state({ enemies: [] });
    const next = state({ enemies: [enemy({ index: 0 })] });
    expect(combatDiff(prev, next)).toEqual([]);
  });
});

describe("combatDiff — osty", () => {
  it("emits a hurt event when Osty loses hp", () => {
    const prev = state({ osty: { name: "Osty", hp: 30, max_hp: 30, block: 0, alive: true } });
    const next = state({ osty: { name: "Osty", hp: 22, max_hp: 30, block: 0, alive: true } });
    expect(combatDiff(prev, next)).toContainEqual(
      { kind: "hurt", target: { side: "osty" }, amount: 8 },
    );
  });

  it("emits death when Osty goes from alive to not alive", () => {
    const prev = state({ osty: { name: "Osty", hp: 5, max_hp: 30, block: 0, alive: true } });
    const next = state({ osty: { name: "Osty", hp: 0, max_hp: 30, block: 0, alive: false } });
    expect(combatDiff(prev, next)).toContainEqual(
      { kind: "death", target: { side: "osty" } },
    );
  });
});

describe("combatDiff — player block", () => {
  it("emits blockGain when player block increases", () => {
    const prev = state({ player: { name: "Ironclad", hp: 80, max_hp: 80, block: 0 } });
    const next = state({ player: { name: "Ironclad", hp: 80, max_hp: 80, block: 14 } });
    expect(combatDiff(prev, next)).toContainEqual(
      { kind: "blockGain", target: { side: "player" }, amount: 14 },
    );
  });

  it("emits blockBreak when player block falls to zero", () => {
    const prev = state({ player: { name: "Ironclad", hp: 80, max_hp: 80, block: 7 } });
    const next = state({ player: { name: "Ironclad", hp: 80, max_hp: 80, block: 0 } });
    expect(combatDiff(prev, next)).toContainEqual(
      { kind: "blockBreak", target: { side: "player" } },
    );
  });
});
