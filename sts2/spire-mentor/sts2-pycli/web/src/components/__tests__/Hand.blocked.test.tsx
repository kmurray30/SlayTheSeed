import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import Hand from "../Hand";
import type { Card } from "../../api";

const card = (over: Partial<Card>): Card => ({
  index: 0, id: "CARD.DEFEND_SILENT", name: "Defend", cost: 1,
  type: "Skill", description: "Gain 5 Block.\nBound", image: null, ...over,
});

// A Bound card under the Queen's Chains of Binding, once another Bound card
// has been played this turn: unplayable, but NOT because you are short on
// energy. `NCard.UpdateEnergyCostVisuals` shows the red `unplayable` slash for
// exactly this case and leaves the cost numeral alone
// (`Visible = !reason.HasResourceCostReason()`).
describe("a card blocked by an effect rather than by its cost", () => {
  const blocked = card({ can_play: false, unplayable_reason: "blocked", affliction: "Bound" });

  it("wears the game's unplayable slash", () => {
    const { container } = render(<Hand cards={[blocked]} onCardClick={() => {}} />);
    expect(container.querySelector(".gs-card-unplayable")).not.toBeNull();
    expect(container.querySelector(".gs-card")!.className).toContain("gs-blocked");
  });

  it("does not redden the cost, which would blame the wrong thing", () => {
    const { container } = render(<Hand cards={[blocked]} onCardClick={() => {}} />);
    expect(container.querySelector(".gs-card")!.className).not.toContain("gs-unaffordable");
  });

  it("still cannot be clicked", () => {
    const { container } = render(<Hand cards={[blocked]} onCardClick={() => {}} />);
    expect(container.querySelector(".gs-handslot")!.className).not.toContain("gs-playable");
  });

  it("leaves a card you merely cannot afford on the red-cost path", () => {
    const { container } = render(
      <Hand cards={[card({ can_play: false, unplayable_reason: "cost" })]} onCardClick={() => {}} />,
    );
    expect(container.querySelector(".gs-card")!.className).toContain("gs-unaffordable");
    expect(container.querySelector(".gs-card-unplayable")).toBeNull();
  });

  it("keeps the old behaviour for backends that ship no reason", () => {
    // Recorded logs and the C# lane carry `can_play` alone; those must keep
    // reading as unaffordable rather than silently losing their marking.
    const { container } = render(
      <Hand cards={[card({ can_play: false })]} onCardClick={() => {}} />,
    );
    expect(container.querySelector(".gs-card")!.className).toContain("gs-unaffordable");
    expect(container.querySelector(".gs-card-unplayable")).toBeNull();
  });
});
