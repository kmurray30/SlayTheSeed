import { render } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import Board from "../Board";
import fixture from "./fixture-step.json";
import type { Step } from "../../api";

/**
 * The enemy row is laid out ONCE per fight.
 *
 * `NCombatRoom.PositionEnemies` runs from `CreateEnemyNodes` at combat setup and
 * is never called again: a creature that dies has its node removed and everyone
 * else keeps the `Position` they were handed. The workbench recomputed the row
 * from the live enemy list on every render, so killing one of two enemies sent
 * the survivor sliding across the floor to a slot the game would never move it
 * to.
 */
const enemy = (index: number, name: string) => ({
  index, name, hp: 30, max_hp: 30, block: 0,
  image: null, rig: "/assets/monster_rigs/nibbit/nibbit", rig_scale: 0.28, rig_bounds_h: 146,
  intents: [{ type: "Attack", damage: 6, hits: 1 }],
});

/** One enemy of a slotted encounter, addressed by its art slug and carrying the
 *  engine's `uid` — which is the only identity a mid-fight spawn cannot move. */
const slotted = (index: number, uid: number, slug: string, name = slug) => ({
  index, uid, name, hp: 30, max_hp: 30, block: 0,
  image: `monsters/${slug}.png`, rig: `monster_rigs/${slug}/${slug}`,
  rig_scale: 0.28, rig_bounds_h: 146,
  intents: [{ type: "Attack", damage: 6, hits: 1 }],
});

function stepWith(enemies: unknown[], floor: number): Step {
  const base = fixture as unknown as Step;
  return {
    ...base,
    meta: { ...(base.meta ?? {}), floor } as Step["meta"],
    state: { ...base.state, enemies } as Step["state"],
  };
}

/** Where each live enemy stands, keyed by its board index. */
function slotXs(c: Element): Record<string, number> {
  const out: Record<string, number> = {};
  for (const el of c.querySelectorAll<HTMLElement>(".gs-side")) {
    const idx = el.querySelector("[data-enemy-index]")?.getAttribute("data-enemy-index");
    if (idx != null) out[idx] = parseFloat(el.style.left);
  }
  return out;
}

/** Where each live enemy stands, keyed by the enemy's NAME — the handle that
 *  survives a spawn renumbering the board. */
function slotXsByName(c: Element, names: Record<string, string>): Record<string, number> {
  const out: Record<string, number> = {};
  for (const [idx, x] of Object.entries(slotXs(c))) {
    const name = names[idx];
    if (name != null) out[name] = x;
  }
  return out;
}

describe("enemy row layout", () => {
  it("leaves the survivor where it stood when its neighbour dies", () => {
    const both = [enemy(0, "Nibbit"), enemy(1, "Nibbit")];
    const { container, rerender } = render(<Board step={stepWith(both, 7)} />);
    const before = slotXs(container);
    expect(Object.keys(before)).toHaveLength(2);

    // Enemy 0 is gone — the fight is down to the one that was on the right.
    rerender(<Board step={stepWith([enemy(1, "Nibbit")], 7)} />);
    const after = slotXs(container);
    expect(Object.keys(after)).toEqual(["1"]);
    expect(after["1"]).toBeCloseTo(before["1"], 6);
  });

  it("lays the next fight out for its own roster", () => {
    // The memo is per fight, not for the session: a new floor starts over, so a
    // single enemy on the next floor takes a single enemy's slot rather than
    // inheriting the second slot of the fight before it.
    const { container, rerender } = render(
      <Board step={stepWith([enemy(0, "Nibbit"), enemy(1, "Nibbit")], 7)} />);
    rerender(<Board step={stepWith([enemy(0, "Nibbit")], 8)} />);
    const next = slotXs(container);
    const solo = slotXs(render(<Board step={stepWith([enemy(0, "Nibbit")], 8)} />).container);
    expect(next["0"]).toBeCloseTo(solo["0"], 6);
  });
});

/**
 * A mid-fight spawn must not shuffle the creatures already standing.
 *
 * The wire's `index` is a position in the encounter ARRAY, which the engine
 * keeps in slot order (`CombatState.SortEnemiesBySlotName`), so an Ovicopter
 * that lays three eggs into `egg5`/`egg4`/`egg3` is renumbered from index 0 to
 * index 3 in a single step. The row memo used to be keyed by that index, so the
 * Ovicopter's own slot was handed to whichever hatchling inherited index 0 and
 * the Ovicopter itself was stood in an egg slot — the reported "enemies swap
 * around when new ones spawn".
 */
describe("a mid-fight spawn", () => {
  const OVI = "Ovicopter";
  const EGG = "Hatchling";

  it("leaves the summoner exactly where it stood", () => {
    const alone = [slotted(0, 100, "ovicopter", OVI)];
    const { container, rerender } = render(<Board step={stepWith(alone, 30)} />);
    const before = slotXsByName(container, { 0: OVI });
    expect(before[OVI]).toBeTypeOf("number");

    // Three eggs land in egg5/egg4/egg3 — all AHEAD of the Ovicopter's own
    // slot, so the wire renumbers it 0 -> 3.
    const laid = [
      slotted(0, 101, "tough_egg", EGG),
      slotted(1, 102, "tough_egg", EGG),
      slotted(2, 103, "tough_egg", EGG),
      slotted(3, 100, "ovicopter", OVI),
    ];
    rerender(<Board step={stepWith(laid, 30)} />);
    const after = slotXsByName(container, { 0: "egg_a", 1: "egg_b", 2: "egg_c", 3: OVI });
    expect(after[OVI]).toBeCloseTo(before[OVI], 6);
    // And it is still the rightmost thing on the board: `OvicopterNormal`
    // declares `[egg1..egg5, ovicopter]`, so every egg stands left of it.
    for (const egg of ["egg_a", "egg_b", "egg_c"]) {
      expect(after[egg], egg).toBeLessThan(after[OVI]);
    }
  });

  it("tucks the eggs in beside the Ovicopter, not at the far end of the row", () => {
    // `Ovicopter.LayEggsMove` takes the LAST free slot
    // (`Slots.LastOrDefault(free)`), not `EncounterModel.GetNextSlot` — so the
    // three eggs fill egg5/egg4/egg3, nearest the Ovicopter first, and
    // egg1/egg2 stay empty. Scanning from the front instead puts them in
    // egg1/egg2/egg3, a row's width away on the wrong side.
    //
    // `OvicopterNormal`'s markers are egg1=1, egg2=180, egg3=363, egg4=538,
    // egg5=691, ovicopter=1011 (centre-relative scene x). The board maps those
    // through one affine transform, so RATIOS of the drawn gaps are the
    // assertion: they hold whatever the stage's size and camera turn out to be.
    const laid = [
      slotted(0, 101, "tough_egg", EGG),
      slotted(1, 102, "tough_egg", EGG),
      slotted(2, 103, "tough_egg", EGG),
      slotted(3, 100, "ovicopter", OVI),
    ];
    const { container, rerender } = render(
      <Board step={stepWith([slotted(0, 100, "ovicopter", OVI)], 31)} />);
    rerender(<Board step={stepWith(laid, 31)} />);
    const at = slotXsByName(container, { 0: "e0", 1: "e1", 2: "e2", 3: OVI });

    // egg3 -> egg4 -> egg5: (691-363)/(538-363). Front-scanning would give
    // egg1 -> egg2 -> egg3, (363-1)/(180-1) = 2.02.
    expect((at.e2 - at.e0) / (at.e1 - at.e0)).toBeCloseTo(328 / 175, 2);
    // And the Ovicopter is a slot's width from the nearest egg, not a row's:
    // (1011-691)/(691-363). Front-scanning gives 1.79.
    expect((at[OVI] - at.e2) / (at.e2 - at.e0)).toBeCloseTo(320 / 328, 2);
  });
});
