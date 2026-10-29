import { describe, expect, it } from "vitest";
import { monsterSkin, slugifyMonster } from "./monsterSkins";

describe("slugifyMonster", () => {
  it("mirrors the backend slugify rules", () => {
    expect(slugifyMonster("Bowlbug (Silk)")).toBe("bowlbug_silk");
    expect(slugifyMonster("Pael's Legion")).toBe("paels_legion");
    expect(slugifyMonster("The Forgotten")).toBe("forgotten");
    expect(slugifyMonster("Battle Friend v2.0")).toBe("battle_friend_v2_0");
  });
});

describe("monsterSkin", () => {
  it("maps every bowlbug variant to its decomp skin", () => {
    // BowlbugEgg/Nectar/Rock/Silk.cs SetupSkins
    expect(monsterSkin("Bowlbug (Egg)")).toBe("cocoon");
    expect(monsterSkin("Bowlbug (Nectar)")).toBe("goop");
    expect(monsterSkin("Bowlbug (Rock)")).toBe("rock");
    expect(monsterSkin("Bowlbug (Silk)")).toBe("web");
  });

  it("leaves the base bowlbug (and unknown monsters) unskinned", () => {
    expect(monsterSkin("Bowlbug")).toBeUndefined();
    expect(monsterSkin("Nibbit")).toBeUndefined();
    // Random-cosmetic rigs stay with composeSetupSkin.
    expect(monsterSkin("Toadpole")).toBeUndefined();
    expect(monsterSkin("Cubex Construct")).toBeUndefined();
  });

  it("maps the cultist variants onto the shared cultists rig skins", () => {
    expect(monsterSkin("Calcified Cultist")).toBe("coral");
    expect(monsterSkin("Damp Cultist")).toBe("slug");
    expect(monsterSkin("Cultist")).toBeUndefined();
  });

  it("maps the battleworn dummy Battle Friend versions", () => {
    expect(monsterSkin("Battle Friend v1.0")).toBe("v1");
    expect(monsterSkin("Battle Friend v2.0")).toBe("v2");
    expect(monsterSkin("Battle Friend v3.0")).toBe("v3");
  });

  it("picks the pets' SkinOptions[0] defaults", () => {
    expect(monsterSkin("Byrdpip")).toBe("version1");
    expect(monsterSkin("Pael's Legion")).toBe("eyes");
  });

  it("alternates the Phantasmal Gardener tall/short by enemy index", () => {
    // PhantasmalGardener.cs: "tall" for slots first/third, "short" otherwise.
    expect(monsterSkin("Phantasmal Gardener", 0)).toBe("tall");
    expect(monsterSkin("Phantasmal Gardener", 1)).toBe("short");
    expect(monsterSkin("Phantasmal Gardener", 2)).toBe("tall");
    expect(monsterSkin("Phantasmal Gardener", 3)).toBe("short");
    // No index known → the game's first-slot look.
    expect(monsterSkin("Phantasmal Gardener")).toBe("tall");
  });
});
