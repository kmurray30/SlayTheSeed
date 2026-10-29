import { describe, it, expect } from "vitest";
import { targetForUid } from "../uidTargets";
import type { CombatState } from "../../api";

function state(enemies: { index: number; uid: number }[]): CombatState {
  return {
    decision: "combat_play", round: 1, energy: 3, max_energy: 3,
    draw_pile_count: 0, discard_pile_count: 0, exhaust_pile_count: 0,
    player: { name: "Ironclad", hp: 80, max_hp: 80, block: 0 },
    player_powers: [],
    enemies: enemies.map((e) => ({
      index: e.index, uid: e.uid, name: "Twig Slime", hp: 10, max_hp: 10,
      block: 0, image: null,
    })),
    hand: [],
  } as unknown as CombatState;
}

describe("targetForUid", () => {
  it("resolves a player-side row without needing a uid", () => {
    expect(targetForUid(null, "player", [state([])])).toEqual({ side: "player" });
  });

  it("maps a uid to its encounter slot index", () => {
    const s = state([{ index: 0, uid: 11 }, { index: 2, uid: 12 }]);
    expect(targetForUid(12, "enemy", [s])).toEqual({ side: "enemy", index: 2 });
  });

  it("distinguishes two same-named enemies by uid", () => {
    const s = state([{ index: 0, uid: 11 }, { index: 1, uid: 12 }]);
    expect(targetForUid(11, "enemy", [s])).toEqual({ side: "enemy", index: 0 });
    expect(targetForUid(12, "enemy", [s])).toEqual({ side: "enemy", index: 1 });
  });

  it("falls back to an earlier state for an enemy that just died", () => {
    const next = state([{ index: 0, uid: 11 }]);
    const prev = state([{ index: 0, uid: 11 }, { index: 1, uid: 12 }]);
    expect(targetForUid(12, "enemy", [next, prev])).toEqual({ side: "enemy", index: 1 });
  });

  it("returns null for a uid on no board (Osty, torn-down creatures)", () => {
    expect(targetForUid(99, "enemy", [state([{ index: 0, uid: 11 }])])).toBeNull();
  });

  it("tolerates null states in the search list", () => {
    expect(targetForUid(11, "enemy", [null, state([{ index: 0, uid: 11 }])]))
      .toEqual({ side: "enemy", index: 0 });
  });
});
