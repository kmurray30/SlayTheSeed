import { describe, it, expect } from "vitest";
import { firstAvailable, pickAttackClip } from "../clips";

describe("firstAvailable", () => {
  it("returns the first candidate the rig has", () => {
    const has = (n: string) => ["idle_loop", "attack", "die"].includes(n);
    expect(firstAvailable(["attack_heavy", "attack"], has)).toBe("attack");
  });
  it("returns undefined when none are available", () => {
    expect(firstAvailable(["nope"], () => false)).toBeUndefined();
  });
});

describe("pickAttackClip", () => {
  const has = (n: string) => ["idle_loop", "attack", "attack_heavy"].includes(n);
  it("prefers a damage-keyed variant when present", () => {
    expect(pickAttackClip(has, { hits: 1, totalDamage: 30 })).toBe("attack_heavy");
  });
  it("falls back to plain attack", () => {
    const plain = (n: string) => n === "attack" || n === "idle_loop";
    expect(pickAttackClip(plain, { hits: 1, totalDamage: 5 })).toBe("attack");
  });
  it("prefers a multi-hit variant for multi-hit moves when present", () => {
    const multi = (n: string) => ["attack", "attack_multi"].includes(n);
    expect(pickAttackClip(multi, { hits: 3, totalDamage: 9 })).toBe("attack_multi");
  });
});
