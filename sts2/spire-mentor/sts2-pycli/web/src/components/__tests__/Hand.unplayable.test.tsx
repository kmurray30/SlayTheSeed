import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import Hand from "../Hand";
import type { Card } from "../../api";

const card = (over: Partial<Card>): Card => ({
  index: 0, id: "CARD.STRIKE", name: "Strike", cost: 1,
  type: "Attack", description: "Deal 6 damage.", image: null, ...over,
});

describe("a card you cannot pay for", () => {
  it("is not faded", () => {
    // `NCard.UpdateEnergyCostVisuals` never touches the card's opacity for
    // insufficient resources — it reddens the cost numeral and the hand's cyan
    // Highlight simply does not light. We used to drop the card to 50%.
    const { container } = render(
      <Hand cards={[card({ can_play: false })]} onCardClick={() => {}} />,
    );
    const el = container.querySelector(".gs-card")!;
    expect(el.className).toContain("gs-unaffordable");
    expect(el.className).not.toContain("gs-dim");
  });

  it("loses the playable glow", () => {
    const { container } = render(
      <Hand cards={[card({ can_play: false })]} onCardClick={() => {}} />,
    );
    // `.gs-playable` on the slot is what carries the cyan drop-shadow stack.
    expect(container.querySelector(".gs-handslot")!.className).not.toContain("gs-playable");
  });

  it("leaves an affordable card alone", () => {
    const { container } = render(
      <Hand cards={[card({ can_play: true })]} onCardClick={() => {}} />,
    );
    expect(container.querySelector(".gs-card")!.className).not.toContain("gs-unaffordable");
    expect(container.querySelector(".gs-handslot")!.className).toContain("gs-playable");
  });

  it("marks nothing in replay, where there is no energy agency", () => {
    // No `onCardClick` — a recorded frame. The game's red cost is a live
    // affordance; a replay frame has no "right now" to be short of.
    const { container } = render(<Hand cards={[card({ can_play: false })]} />);
    expect(container.querySelector(".gs-card")!.className).not.toContain("gs-unaffordable");
  });
});
