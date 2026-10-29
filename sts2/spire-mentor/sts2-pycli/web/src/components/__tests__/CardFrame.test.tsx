import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { Card } from "../../api";
import CardFrame from "../CardFrame";

/** A minimal combat hand card. */
function card(over: Partial<Card> = {}): Card {
  return {
    index: 0, id: "DEFEND_NECROBINDER", name: "Defend", type: "Skill",
    cost: 1, upgraded: false, can_play: true, target_type: "None",
    description: "Gain 5 Block.", description_rich: "Gain 5 [gold]Block[/gold].",
    stats: { block: 5 },
    ...over,
  } as Card;
}

describe("CardFrame damage preview", () => {
  it("swaps the printed damage for the live modified value", () => {
    render(<CardFrame card={card({
      id: "STRIKE", name: "Strike", type: "Attack", stats: { damage: 6 },
      description: "Deal 6 damage.", description_rich: "Deal 6 damage.",
    })} previewDamage={9} />);
    expect(screen.getByText("9")).toBeTruthy();
    expect(screen.queryByText(/Deal 6 damage/)).toBeNull();
  });

  it("leaves the text alone when the preview equals the base", () => {
    render(<CardFrame card={card({
      id: "STRIKE", name: "Strike", type: "Attack", stats: { damage: 6 },
      description: "Deal 6 damage.", description_rich: "Deal 6 damage.",
    })} previewDamage={6} />);
    expect(screen.getByText(/Deal 6 damage/)).toBeTruthy();
  });
});

describe("CardFrame block preview (GH #520)", () => {
  it("swaps the printed block for the block the card will really grant", () => {
    // Dexterity 5 turns Defend's printed 5 into 10. Showing the base made
    // Necrobinder's "summon 7" / "summon 9" look like better mitigation than a
    // Defend that actually out-blocks both.
    render(<CardFrame card={card({ block_preview: 10 })} />);
    expect(screen.getByText("10")).toBeTruthy();
    expect(screen.queryByText(/Gain 5/)).toBeNull();
  });

  it("colours a reduced block as a nerf and a raised one as a buff", () => {
    const { container: up } = render(<CardFrame card={card({ block_preview: 10 })} />);
    const { container: down } = render(<CardFrame card={card({ block_preview: 3 })} />);
    const colorOf = (c: HTMLElement, text: string) =>
      [...c.querySelectorAll("span")].find((s) => s.textContent === text)?.style.color;
    expect(colorOf(up, "10")).toBe("var(--gs-buff)");
    expect(colorOf(down, "3")).toBe("var(--gs-nerf)");
  });

  it("leaves the text alone with no block_preview", () => {
    render(<CardFrame card={card()} />);
    expect(screen.getByText(/Gain 5/)).toBeTruthy();
  });

  it("does not touch a coincidental damage number", () => {
    // "Deal 5 damage. Gain 5 Block." — only the Block 5 may be rewritten.
    render(<CardFrame card={card({
      id: "IRON_WAVE", name: "Iron Wave", type: "Attack",
      description: "Deal 5 damage. Gain 5 Block.",
      description_rich: "Deal 5 damage. Gain 5 [gold]Block[/gold].",
      stats: { damage: 5, block: 5 }, block_preview: 10,
    })} />);
    expect(screen.getByText("10")).toBeTruthy();
    expect(screen.getByText(/Deal 5 damage/)).toBeTruthy();
  });
});
