import { describe, expect, it } from "vitest";
import {
  addCard,
  decodeHash,
  emptyPosition,
  encodeHash,
  groupCards,
  moveCard,
  normalizePosition,
  removeMonster,
  setGroupCount,
  sweepFromSearch,
  totalCards,
} from "./position";
import { samplePosition } from "./testMocks";

describe("position hash codec", () => {
  it("round-trips a position through #p=", () => {
    const pos = samplePosition();
    const hash = "#" + encodeHash(pos);
    expect(hash.startsWith("#p=")).toBe(true);
    expect(decodeHash(hash)).toEqual(pos);
  });
  it("survives non-ASCII and is URL-safe", () => {
    const pos = samplePosition();
    pos.encounter = "Fuzzy → Wurm ✓";
    const hash = encodeHash(pos);
    expect(hash).toMatch(/^p=[A-Za-z0-9_-]+$/);
    expect(decodeHash("#" + hash)?.encounter).toBe("Fuzzy → Wurm ✓");
  });
  it("returns null for a missing or corrupt hash", () => {
    expect(decodeHash("")).toBeNull();
    expect(decodeHash("#x=1")).toBeNull();
    expect(decodeHash("#p=@@@")).toBeNull();
    expect(decodeHash("#p=" + btoa("not json"))).toBeNull();
  });
  it("reads ?sweep=", () => {
    expect(sweepFromSearch("?sweep=abc")).toBe("abc");
    expect(sweepFromSearch("")).toBeNull();
  });
});

describe("normalizePosition", () => {
  it("fills every field a sparse document omits", () => {
    const pos = normalizePosition({ character: "Silent", piles: { hand: [{ card: "STRIKE_SILENT" }] }, monsters: [{ monster: "Nibbit", hp: 20 }] });
    expect(pos.schema).toBe("simlab-position/1");
    expect(pos.character).toBe("Silent");
    expect(pos.player.hp).toBe(80);
    expect(pos.piles.hand).toEqual([{ card: "STRIKE_SILENT", upgrade: 0, cost: null }]);
    expect(pos.piles.draw).toEqual([]);
    expect(pos.monsters[0]).toEqual({ monster: "Nibbit", hp: 20, maxHp: 20, block: 0, powers: [], intent: null });
  });
  it("accepts relic ids as bare strings", () => {
    const pos = normalizePosition({ player: { relics: ["BURNING_BLOOD"] } });
    expect(pos.player.relics).toEqual([{ id: "BURNING_BLOOD", counter: 0 }]);
  });
});

describe("card rows", () => {
  it("groups identical consecutive cards and resizes a group in place", () => {
    const pos = samplePosition();
    const groups = groupCards(pos.piles.hand);
    expect(groups.map((g) => [g.card.card, g.indices.length])).toEqual([
      ["STRIKE_IRONCLAD", 2],
      ["DEFEND_IRONCLAD", 1],
      ["BASH", 1],
    ]);
    const resized = setGroupCount(pos.piles.hand, groups[0], 4);
    expect(resized.map((c) => c.card)).toEqual(["STRIKE_IRONCLAD", "STRIKE_IRONCLAD", "STRIKE_IRONCLAD", "STRIKE_IRONCLAD", "DEFEND_IRONCLAD", "BASH"]);
    expect(setGroupCount(pos.piles.hand, groups[0], 0).map((c) => c.card)).toEqual(["DEFEND_IRONCLAD", "BASH"]);
  });
  it("moves a card to the end of another pile and counts across piles", () => {
    const pos = samplePosition();
    const moved = moveCard(pos, "hand", 3, "draw");
    expect(moved.piles.hand.map((c) => c.card)).toEqual(["STRIKE_IRONCLAD", "STRIKE_IRONCLAD", "DEFEND_IRONCLAD"]);
    expect(moved.piles.draw[moved.piles.draw.length - 1].card).toBe("BASH");
    expect(totalCards(moved)).toBe(totalCards(pos));
    expect(totalCards(addCard(pos, "exhaust", "BASH"))).toBe(totalCards(pos) + 1);
  });
  it("does not mutate the input", () => {
    const pos = samplePosition();
    const before = JSON.stringify(pos);
    removeMonster(pos, 0);
    addCard(pos, "hand", "BASH");
    expect(JSON.stringify(pos)).toBe(before);
    expect(emptyPosition("Defect", 75).player.maxHp).toBe(75);
  });
});
