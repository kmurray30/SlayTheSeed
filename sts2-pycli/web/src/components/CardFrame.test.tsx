import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import CardFrame from "./CardFrame";
import type { Card } from "../api";

const card: Card = {
  index: 0, id: "STRIKE", name: "Strike", cost: 1, type: "Attack",
  description: "Deal 6 damage.", can_play: true, image: null, target_type: "AnyEnemy",
};

describe("CardFrame win-prob badge", () => {
  it("shows a win-prob badge when winProb is set", () => {
    render(<CardFrame card={card} winProb={0.62} />);
    expect(screen.getByText("62%")).toBeInTheDocument();
  });

  it("shows no win-prob badge when winProb is unset", () => {
    render(<CardFrame card={card} />);
    expect(screen.queryByText(/%$/)).toBeNull();
  });
});

describe("CardFrame damage preview", () => {
  const strike: Card = {
    ...card, stats: { damage: 6 }, damage_preview: { "0": 9 },
  };

  it("rewrites the damage number to the modified value when aimed at a target", () => {
    render(<CardFrame card={strike} previewDamage={9} />);
    expect(screen.getByText("9")).toBeInTheDocument();
    // The base "6 damage" is gone; only "damage" remains around the swapped number.
    expect(screen.queryByText("Deal 6 damage.")).toBeNull();
  });

  it("keeps the base damage when no target is aimed at", () => {
    render(<CardFrame card={strike} />);
    expect(screen.getByText("Deal 6 damage.")).toBeInTheDocument();
  });

  it("keeps the base damage when the modified value equals it", () => {
    render(<CardFrame card={strike} previewDamage={6} />);
    expect(screen.getByText("Deal 6 damage.")).toBeInTheDocument();
  });
});

describe("CardFrame upgrade marker", () => {
  // The game marks an upgrade on the title itself — a trailing '+' in green —
  // rather than with a separate badge, so that is what is asserted here.
  it("appends '+' to the name and greens the title when upgraded", () => {
    const { container } = render(<CardFrame card={{ ...card, upgraded: true }} />);
    expect(screen.getByText("Strike+")).toBeInTheDocument();
    expect(container.querySelector(".gs-card-title.gs-upgraded")).not.toBeNull();
  });

  it("shows the plain name and no upgrade styling when not upgraded", () => {
    const { container } = render(<CardFrame card={card} />);
    expect(screen.getByText("Strike")).toBeInTheDocument();
    expect(container.querySelector(".gs-card-title.gs-upgraded")).toBeNull();
  });
});

describe("CardFrame keyword markup", () => {
  // The game ships its descriptions with the keywords already marked up, and
  // draws the tagged span in StsColors.gold (#EFC851).
  it("colours [gold] spans and never shows the tags themselves", () => {
    const { container } = render(
      <CardFrame card={{ ...card, description_rich: "Gain 7 [gold]Block[/gold]." }} />
    );
    const kw = [...container.querySelectorAll("span")].find((s) => s.textContent === "Block");
    expect(kw).toBeDefined();
    expect(kw!.style.color).toBe("rgb(239, 200, 81)");
    expect(container.textContent).not.toContain("[gold]");
  });

  it("still swaps the previewed damage number inside marked-up text", () => {
    const { container } = render(
      <CardFrame
        card={{
          ...card, stats: { damage: 6 },
          description_rich: "Deal 6 damage. Apply 2 [gold]Vulnerable[/gold].",
        }}
        previewDamage={9}
      />
    );
    expect(container.textContent).toContain("Deal 9 damage.");
    expect(container.textContent).not.toContain("[gold]");
  });

  it("falls back to the plain description when no marked-up one was sent", () => {
    render(<CardFrame card={card} />);
    expect(screen.getByText("Deal 6 damage.")).toBeInTheDocument();
  });

  it("renders a card that carries no description at all", () => {
    // Token cards and several fixtures omit it despite the type; the frame must
    // still draw rather than throw while splitting undefined markup.
    const { container } = render(
      <CardFrame card={{ ...card, description: undefined as unknown as string }} />
    );
    expect(container.querySelector(".gs-card-desc")).not.toBeNull();
  });
});

describe("CardFrame cost glyphs", () => {
  it("draws an inline orb per point of energy, using the card's own orb art", () => {
    const { container } = render(
      <CardFrame card={{ ...card, color: "blue", description_rich: "Gain [energy]3[/energy]." }} />
    );
    const orbs = [...container.querySelectorAll("img")].filter((i) =>
      i.className === "gs-card-inline-icon"
    );
    expect(orbs).toHaveLength(3);
    expect(orbs[0].getAttribute("src")).toBe("/assets/card_template/energy/defect.png");
    expect(container.textContent).not.toContain("[energy]");
  });

  it("draws star glyphs from the shared star sprite", () => {
    const { container } = render(
      <CardFrame card={{ ...card, description_rich: "Spend [star]2[/star]." }} />
    );
    const stars = [...container.querySelectorAll("img")].filter(
      (i) => i.getAttribute("src") === "/assets/card_template/energy_star.png"
    );
    expect(stars).toHaveLength(2);
  });

  it("shows the Regent's star-cost gem only when the card has one", () => {
    const { container: withStars } = render(
      <CardFrame card={{ ...card, star_cost: 5 }} />
    );
    const gem = withStars.querySelector(".gs-card-star");
    expect(gem).not.toBeNull();
    expect(gem!.textContent).toBe("5");

    const { container: without } = render(<CardFrame card={card} />);
    expect(without.querySelector(".gs-card-star")).toBeNull();
  });
});

describe("CardFrame art selection", () => {
  const src = (container: HTMLElement, part: string) =>
    [...container.querySelectorAll("img")].map((i) => i.getAttribute("src") ?? "")
      .find((s) => s.includes(part));

  it("tints the frame by pool and the banner by rarity — the two are independent", () => {
    const { container } = render(
      <CardFrame card={{ ...card, color: "green", rarity: "Rare" }} />
    );
    expect(src(container, "frames/")).toBe("/assets/card_template/frames/attack_green.png");
    expect(src(container, "banners/")).toBe("/assets/card_template/banners/rare.png");
  });

  it("falls back to the skill frame for types that have none of their own", () => {
    const { container } = render(
      <CardFrame card={{ ...card, type: "Status", color: "colorless" }} />
    );
    expect(src(container, "frames/")).toBe("/assets/card_template/frames/skill_colorless.png");
  });

  it("derives the pool from the art path when the backend sent no color", () => {
    const { container } = render(
      <CardFrame card={{ ...card, image: "/assets/card_atlas/silent/backstab.png" }} />
    );
    expect(src(container, "frames/")).toBe("/assets/card_template/frames/attack_green.png");
  });

  it("uses the common banner for rarities the game has no banner for", () => {
    const { container } = render(<CardFrame card={{ ...card, rarity: "Basic" }} />);
    expect(src(container, "banners/")).toBe("/assets/card_template/banners/common.png");
  });
});

describe("CardFrame modified energy cost", () => {
  // `CardCostHelper.GetEnergyCostColor` compares the hook/modifier cost against
  // the printed one; `GetCostTextColorInHand` then paints Decreased green and
  // Increased energy-blue. Unrelenting's FreeAttack is the common Decreased
  // case — every Attack in hand reads 0 over a printed 1 or 2.
  it("marks a cost the board pushed DOWN", () => {
    const { container } = render(<CardFrame card={{ ...card, cost: 0, base_cost: 2 }} />);
    expect(container.querySelector(".gs-card-energy.gs-cost-down")).not.toBeNull();
    expect(container.querySelector(".gs-card-energy.gs-cost-up")).toBeNull();
  });

  it("marks a cost the board pushed UP", () => {
    const { container } = render(<CardFrame card={{ ...card, cost: 3, base_cost: 1 }} />);
    expect(container.querySelector(".gs-card-energy.gs-cost-up")).not.toBeNull();
  });

  it("leaves an unmodified cost alone", () => {
    const { container } = render(<CardFrame card={{ ...card, cost: 1, base_cost: 1 }} />);
    expect(container.querySelector(".gs-cost-down, .gs-cost-up")).toBeNull();
  });

  it("leaves the cost alone when the backend sends no base", () => {
    const { container } = render(<CardFrame card={{ ...card, cost: 0 }} />);
    expect(container.querySelector(".gs-cost-down, .gs-cost-up")).toBeNull();
  });

  // C# checks InsufficientResources FIRST, so a card you cannot pay for stays
  // red even when a modifier moved its cost.
  it("lets unaffordable win over a modified cost", () => {
    const { container } = render(
      <CardFrame card={{ ...card, cost: 1, base_cost: 2 }} unaffordable />,
    );
    expect(container.querySelector(".gs-cost-down")).toBeNull();
    expect(container.querySelector(".gs-card.gs-unaffordable")).not.toBeNull();
  });
});
