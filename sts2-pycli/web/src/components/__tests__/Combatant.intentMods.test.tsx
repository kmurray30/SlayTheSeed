import { render } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import Combatant from "../Combatant";
import { describeIntent, formatMul, intentDirection } from "../../intentBreakdown";

/**
 * Why a telegraph reads what it reads.
 *
 * Bug report `wb-4eae3bad6ebd57f8`: a Torch Head Amalgam telegraphing 12x3 was
 * hit with an Uppercut that applied Weak, and kept telegraphing 12x3. The
 * engine was right — the enemy already had Weak from an earlier Shockwave, and
 * `WeakPower.StackType = Counter` makes stacks DURATION, so the flat x0.75 was
 * already in the 12 and a third stack cannot move it. Nothing on the board said
 * so, which is what turned a correct number into a bug report.
 *
 * The board now shows the listeners that produced the gap between the move's
 * own damage and the telegraph. Its numbers are the engine's own
 * (`hooks::damage_modifier_breakdown`); this file pins that they REACH the
 * screen and read correctly.
 */
const AMALGAM_BEAM = {
  type: "Attack",
  damage: 12,
  base_damage: 8,
  hits: 3,
  modifiers: [
    { name: "VULNERABLE", kind: "power", side: "player", title: "Vulnerable",
      image: "/assets/power_atlas/vulnerable_power.png", mul: [3, 2] as [number, number] },
    { name: "STRENGTH", kind: "power", side: "enemy", title: "Strength",
      image: "/assets/power_atlas/strength_power.png", add: 3 },
    { name: "WEAK", kind: "power", side: "enemy", title: "Weak",
      image: "/assets/power_atlas/weak_power.png", mul: [3, 4] as [number, number] },
  ],
};

const amalgam = (intent: unknown) => ({
  name: "Torch Head Amalgam", hp: 75, max_hp: 211, block: 0,
  intents: [intent],
} as never);

describe("describeIntent", () => {
  it("spells the telegraph out in PIPELINE order — additive, then multiplicative", () => {
    expect(describeIntent(AMALGAM_BEAM)).toBe(
      [
        "12 damage per hit",
        "8 base",
        "+3 Strength (attacker)",
        "×3/2 Vulnerable (you)",
        "×3/4 Weak (attacker)",
      ].join("\n"),
    );
  });

  it("says nothing when there is nothing to explain", () => {
    expect(describeIntent({ damage: 8, hits: 3 })).toBeUndefined();
    // A backend that predates `modifiers` still ships base_damage; a tooltip
    // that could only say "12, somehow" is worse than no tooltip.
    expect(describeIntent({ damage: 12, base_damage: 8 })).toBeUndefined();
  });

  it("drops the '/1' on a whole-number multiplier", () => {
    expect(formatMul([2, 1])).toBe("×2");
    expect(formatMul([3, 4])).toBe("×3/4");
  });
});

describe("intentDirection", () => {
  it("is null unless the board actually moved the number", () => {
    expect(intentDirection({ damage: 8 })).toBeNull();
    expect(intentDirection({ damage: 8, base_damage: 8 })).toBeNull();
  });

  it("reads down when the net effect is a reduction, up when it is a boost", () => {
    expect(intentDirection({ damage: 6, base_damage: 8 })).toBe("down");
    expect(intentDirection({ damage: 12, base_damage: 8 })).toBe("up");
  });
});

describe("the intent chip", () => {
  it("hangs each contributing listener's own tray icon under the intent", () => {
    const { container } = render(<Combatant c={amalgam(AMALGAM_BEAM)} isEnemy />);
    const icons = [...container.querySelectorAll(".gs-intent-mod-icon")];
    expect(icons.map((i) => i.getAttribute("title")))
      .toEqual(["Vulnerable", "Strength", "Weak"]);
    // The one the bug report went looking for and could not find.
    expect(icons.some((i) => (i as HTMLImageElement).src.includes("weak_power")))
      .toBe(true);
  });

  it("marks the numeral as boosted and carries the arithmetic on the chip", () => {
    const { container } = render(<Combatant c={amalgam(AMALGAM_BEAM)} isEnemy />);
    expect(container.querySelector(".gs-intent-dmg")!.getAttribute("data-mod"))
      .toBe("up");
    expect(container.querySelector(".gs-intent")!.getAttribute("title"))
      .toContain("×3/4 Weak (attacker)");
  });

  it("leaves an unmodified telegraph exactly as it was — no tint, no icons", () => {
    const { container } = render(
      <Combatant c={amalgam({ type: "Attack", damage: 8, hits: 3 })} isEnemy />,
    );
    expect(container.querySelector(".gs-intent-mods")).toBeNull();
    expect(container.querySelector(".gs-intent-dmg")!.getAttribute("data-mod"))
      .toBeNull();
    expect(container.querySelector(".gs-intent")!.getAttribute("title")).toBeNull();
  });

  it("draws no icon for the residual `other` row (it names no listener)", () => {
    const { container } = render(<Combatant c={amalgam({
      ...AMALGAM_BEAM,
      modifiers: [...AMALGAM_BEAM.modifiers,
        { name: "OTHER", kind: "other", title: "Other", mul: [2, 1] as [number, number] }],
    })} isEnemy />);
    expect(container.querySelectorAll(".gs-intent-mod-icon")).toHaveLength(3);
    expect(container.querySelectorAll(".gs-intent-mod-txt")).toHaveLength(0);
    // …but it still appears in the arithmetic, which is why it exists.
    expect(container.querySelector(".gs-intent")!.getAttribute("title"))
      .toContain("×2 Other");
  });
});
