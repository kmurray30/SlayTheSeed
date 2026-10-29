import { describe, it, expect } from "vitest";
import { attackingEnemies } from "../enemyTurn";
import type { CombatState } from "../../api";

function state(over: Partial<CombatState> = {}): CombatState {
  return {
    decision: "combat_play", round: 1, energy: 3, max_energy: 3,
    draw_pile_count: 0, discard_pile_count: 0, exhaust_pile_count: 0,
    player: { name: "Ironclad", hp: 80, max_hp: 80, block: 0 },
    player_powers: [], enemies: [], hand: [],
    ...over,
  } as CombatState;
}

function enemy(over: Partial<CombatState["enemies"][number]>) {
  return { index: 0, name: "Cultist", hp: 50, max_hp: 50, block: 0, image: null, ...over };
}

describe("attackingEnemies", () => {
  it("returns [] when the round did not advance (player still mid-turn)", () => {
    const prev = state({ round: 1, enemies: [enemy({ intents: [{ type: "Attack", damage: 6 }] })] });
    const next = state({ round: 1, enemies: [enemy({ intents: [{ type: "Attack", damage: 6 }] })] });
    expect(attackingEnemies(prev, next)).toEqual([]);
  });

  it("returns an enemy that telegraphed Attack and survived the enemy turn", () => {
    const prev = state({ round: 1, enemies: [enemy({ index: 0, intents: [{ type: "Attack", damage: 6 }] })] });
    const next = state({ round: 2, enemies: [enemy({ index: 0, intents: [{ type: "Defend" }] })] });
    expect(attackingEnemies(prev, next)).toEqual([0]);
  });

  it("ignores enemies whose telegraphed intent was not an attack", () => {
    const prev = state({ round: 1, enemies: [enemy({ index: 0, intents: [{ type: "Buff" }] })] });
    const next = state({ round: 2, enemies: [enemy({ index: 0, intents: [{ type: "Attack", damage: 6 }] })] });
    expect(attackingEnemies(prev, next)).toEqual([]);
  });

  it("ignores an attacker that died during the turn (it is handled by the death clip)", () => {
    const prev = state({ round: 1, enemies: [enemy({ index: 0, intents: [{ type: "Attack", damage: 6 }] })] });
    const next = state({ round: 2, enemies: [] });
    expect(attackingEnemies(prev, next)).toEqual([]);
  });

  it("returns only the attacking subset when intents are mixed", () => {
    const prev = state({ round: 1, enemies: [
      enemy({ index: 0, intents: [{ type: "Attack", damage: 6 }] }),
      enemy({ index: 1, name: "B", intents: [{ type: "Defend" }] }),
      enemy({ index: 2, name: "C", intents: [{ type: "Attack", damage: 9 }] }),
    ] });
    const next = state({ round: 2, enemies: [
      enemy({ index: 0 }), enemy({ index: 1, name: "B" }), enemy({ index: 2, name: "C" }),
    ] });
    expect(attackingEnemies(prev, next)).toEqual([0, 2]);
  });
});
