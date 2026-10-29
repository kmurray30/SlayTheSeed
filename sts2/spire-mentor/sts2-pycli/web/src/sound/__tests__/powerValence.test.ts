import { describe, it, expect } from "vitest";
import { powerTypeForAmount, powerValenceLookup } from "../powerValence";
import type { SfxMap } from "../sfxMap";

// The two tables `powerValenceLookup` reads, in the shape sfx_map.json ships
// them. `Strength` is the case the whole rule exists for; `Vulnerable` is an
// ordinary debuff that must be unaffected by it.
const MAP = {
  powers: { Strength: "Buff", Shriek: "Debuff", Vulnerable: "Debuff" },
  powersAllowNegative: ["Dexterity", "Focus", "Shriek", "Shrink", "Strength"],
} as Pick<SfxMap, "powers" | "powersAllowNegative">;

describe("powerTypeForAmount", () => {
  it("leaves a positive amount on the power's own Type", () => {
    expect(powerTypeForAmount("Buff", true, 3)).toBe("Buff");
    expect(powerTypeForAmount("Debuff", false, 3)).toBe("Debuff");
  });

  it("turns an AllowNegative buff into a debuff below zero", () => {
    // `PowerModel.cs:464` — this is the arm negative Strength takes, and the
    // reason a name-keyed table cannot classify it.
    expect(powerTypeForAmount("Buff", true, -3)).toBe("Debuff");
  });

  it("leaves an AllowNegative debuff a debuff below zero", () => {
    // Shriek: the same arm, but its Type already agrees, so nothing flips.
    expect(powerTypeForAmount("Debuff", true, -2)).toBe("Debuff");
  });

  it("turns a non-AllowNegative debuff into a buff below zero", () => {
    // `PowerModel.cs:468`, the second arm. Unreachable through live wire state
    // — `ShouldRemoveDueToAmount` drops a non-AllowNegative power at zero — but
    // it is in the C# and costs nothing to transcribe.
    expect(powerTypeForAmount("Debuff", false, -1)).toBe("Buff");
  });

  it("holds a non-AllowNegative buff at Buff below zero", () => {
    // Neither arm matches, so `return Type`.
    expect(powerTypeForAmount("Buff", false, -1)).toBe("Buff");
  });

  it("treats zero as non-negative, which is what the C# comparison does", () => {
    expect(powerTypeForAmount("Buff", true, 0)).toBe("Buff");
  });

  it("classifies by name alone when the amount is unknown", () => {
    expect(powerTypeForAmount("Buff", true, undefined)).toBe("Buff");
  });

  it("stays undefined for a power the table never classified", () => {
    expect(powerTypeForAmount(undefined, false, -5)).toBeUndefined();
  });
});

describe("powerValenceLookup", () => {
  it("flips Strength on a negative stack total and leaves it alone otherwise", () => {
    const valenceOf = powerValenceLookup(MAP);
    expect(valenceOf("Strength", 2)).toBe("Buff");
    expect(valenceOf("Strength", -2)).toBe("Debuff");
  });

  it("does not flip a power that is merely a debuff", () => {
    expect(powerValenceLookup(MAP)("Vulnerable", 2)).toBe("Debuff");
  });

  it("degrades to name-only classification when the map predates the list", () => {
    // A deployed bucket can still be serving a map generated before
    // `powersAllowNegative` existed — the old behaviour, not a crash.
    const stale = { powers: MAP.powers } as Pick<SfxMap, "powers" | "powersAllowNegative">;
    expect(powerValenceLookup(stale)("Strength", -2)).toBe("Buff");
  });

  it("returns undefined for every power when there is no map at all", () => {
    expect(powerValenceLookup(null)("Strength", -2)).toBeUndefined();
  });
});
