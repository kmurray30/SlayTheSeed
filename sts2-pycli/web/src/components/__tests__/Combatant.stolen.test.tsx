import { render } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import Combatant from "../Combatant";
import type { Card } from "../../api";

/**
 * The card a Thieving Hopper is holding.
 *
 * `ThievingHopper.ThieveryMove` parents a real card node to the thief's
 * `%StolenCardPos` marker and `SwipePower.ExtraHoverTips` shows the card, so in
 * the game you can always see WHICH card left your deck — and therefore whether
 * killing the thief before it escapes (`SwipePower.BeforeDeath` hands the card
 * back) is worth spending the turns on. The workbench used to show a bare
 * "Swipe 1" pip and the card silently vanished from the deck view.
 */
/** A stolen card as the backend sends it — the pin only reads id/name/upgraded,
 *  but the payload is a whole `Card`, so build one. */
const card = (id: string, name: string, upgraded = false): Card => ({
  index: 0, id, name, cost: 1, type: "Attack", description: "", image: null, upgraded,
});

const hopper = (stolen?: Card[]) => ({
  name: "Thieving Hopper", hp: 79, max_hp: 79, block: 0,
  powers: [{ name: "Swipe", id: "SWIPE", amount: 1 }],
  stolen_cards: stolen,
});

const cards = (c: HTMLElement) => Array.from(c.querySelectorAll(".gs-stolen-card"));

describe("the stolen card pinned on the thief", () => {
  it("is absent when nothing has been stolen", () => {
    expect(cards(render(<Combatant c={hopper()} isEnemy />).container)).toHaveLength(0);
    expect(cards(render(<Combatant c={hopper([])} isEnemy />).container)).toHaveLength(0);
  });

  it("draws one card frame per stolen card, naming the card it took", () => {
    const { container } = render(
      <Combatant c={hopper([card("BASH", "Bash", true)])} isEnemy />,
    );
    const [held] = cards(container);
    expect(held).toBeTruthy();
    // The upgrade is part of the identity — a stolen Bash+ is not a stolen Bash.
    expect(held.getAttribute("title")).toContain("Bash+");
    expect(held.getAttribute("aria-label")).toBe("Thieving Hopper stole Bash+");
    // …and it says what to do about it.
    expect(held.getAttribute("title")).toContain("Thieving Hopper");
  });

  it("stacks every held card — one THIEVERY move per hopper, but several hoppers", () => {
    const { container } = render(
      <Combatant c={hopper([
        card("BASH", "Bash"),
        card("STRIKE_IRONCLAD", "Strike"),
      ])} isEnemy />,
    );
    expect(cards(container)).toHaveLength(2);
  });
});
