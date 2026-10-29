import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import Shop from "../Shop";
import { HintsFixture } from "../../hints/HintsContext";
import type { Step } from "../../api";

/** A shop with 30 gold: one card you can afford, one you cannot, one relic
 *  already bought, and one relic that is simply out of reach. */
function shopStep(): Step {
  return {
    idx: 0, decision: "shop",
    state: {
      decision: "shop",
      player: { name: "Ironclad", gold: 30 },
      cards: [
        { index: 0, id: "CARD.BREAKTHROUGH", name: "Breakthrough", type: "Attack",
          cost: 25, card_cost: 1, description: "Lose 1 HP.", is_stocked: true, is_available: true },
        { index: 1, id: "CARD.TRUE_GRIT", name: "True Grit", type: "Skill",
          cost: 48, card_cost: 1, description: "Gain 7 Block.", is_stocked: false, is_available: true },
      ],
      relics: [
        { index: 0, name: "Bought Already", cost: 167, is_stocked: false, is_available: false },
        { index: 1, name: "Too Dear", cost: 192, is_stocked: false, is_available: true },
      ],
      potions: [],
    } as unknown as Step["state"],
    scores: [], pv: null, chosen_action: null, source: "live",
    meta: { turn: null, step_no: 0, floor: 12, act: 1, act_name: "Act 1" },
  };
}

function shop() {
  return render(
    <HintsFixture on={false}><Shop step={shopStep()} onChoose={() => {}} /></HintsFixture>,
  ).container;
}

describe("shop: sold out is not the same as too expensive", () => {
  it("does not stamp Sold across a ware you merely cannot afford", () => {
    // refs/game-shop-2.png is the whole rule: 30 gold against six red price
    // tags, every ware still on the sheet at full brightness. `is_stocked` on
    // the wire means "buyable right now" (in stock AND affordable) because it
    // gates the buy action — reading it as availability marked most of the
    // shop Sold.
    const c = shop();
    const dear = [...c.querySelectorAll(".gs-shop-item")]
      .find((el) => el.textContent?.includes("Too Dear"))!;
    expect(dear.className).not.toContain("is-soldout");
    expect(dear.className).toContain("is-unaffordable");
  });

  it("still marks a slot someone has actually bought", () => {
    const c = shop();
    const gone = [...c.querySelectorAll(".gs-shop-item")]
      .find((el) => el.textContent?.includes("Bought Already"))!;
    expect(gone.className).toContain("is-soldout");
  });

  it("applies the same split to the card rack", () => {
    const c = shop();
    const stands = [...c.querySelectorAll(".gs-shop-cardstand")];
    expect(stands).toHaveLength(2);
    // 25g against 30 gold — affordable, so neither class.
    expect(stands[0].className).not.toContain("is-unaffordable");
    expect(stands[0].className).not.toContain("is-soldout");
    // 48g — out of reach, but still on the rack.
    expect(stands[1].className).toContain("is-unaffordable");
    expect(stands[1].className).not.toContain("is-soldout");
  });

  it("treats a missing availability flag as still on the shelf", () => {
    // Backends that drop bought slots from the list entirely never send the
    // field; absent must not read as sold.
    const step = shopStep();
    for (const r of step.state.relics!) delete (r as { is_available?: boolean }).is_available;
    const c = render(
      <HintsFixture on={false}><Shop step={step} onChoose={() => {}} /></HintsFixture>,
    ).container;
    for (const el of c.querySelectorAll(".gs-shop-item")) {
      expect(el.className).not.toContain("is-soldout");
    }
  });
});

describe("shop: the wares say what they do", () => {
  // A relic on the rug is a 60px sprite and a price. Without the effect text
  // the only way to know what you were buying was to already know it — the
  // cards on the rack have carried their full text all along.
  function wares(): Step {
    const step = shopStep();
    step.state.relics = [
      {
        index: 0,
        name: "Akabeko",
        description: "At the start of each combat, gain 8 Vigor.",
        cost: 25,
        is_stocked: true,
        is_available: true,
      },
      // an older backend that ships no description at all
      { index: 1, name: "Silent Type", cost: 25, is_stocked: true, is_available: true },
    ];
    step.state.potions = [
      {
        index: 0,
        name: "Energy Potion",
        description: "Gain 2 Energy.",
        cost: 25,
        is_stocked: true,
        is_available: true,
      },
    ];
    return step;
  }

  function ware(c: Element, name: string): Element {
    return [...c.querySelectorAll(".gs-shop-item")].find((el) =>
      el.textContent?.includes(name),
    )!;
  }

  const rug = () =>
    render(
      <HintsFixture on={false}><Shop step={wares()} onChoose={() => {}} /></HintsFixture>,
    ).container;

  it("prints the relic's effect on its hover card", () => {
    const el = ware(rug(), "Akabeko");
    expect(el.querySelector(".gs-shop-itemdesc")?.textContent).toBe(
      "At the start of each combat, gain 8 Vigor.",
    );
  });

  it("prints the potion's effect on its hover card", () => {
    const el = ware(rug(), "Energy Potion");
    expect(el.querySelector(".gs-shop-itemdesc")?.textContent).toBe("Gain 2 Energy.");
  });

  it("folds the effect into the accessible name, so it is not hover-only", () => {
    // The reveal is `opacity`, which removes nothing from the a11y tree; the
    // buy button's name is the whole pitch.
    const el = ware(rug(), "Energy Potion");
    expect(el.textContent).toContain("Gain 2 Energy.");
    expect(el.getAttribute("title")).toBe("Energy Potion — Gain 2 Energy.");
  });

  it("renders no description block when the backend sends none", () => {
    const el = ware(rug(), "Silent Type");
    expect(el.querySelector(".gs-shop-itemname")?.textContent).toBe("Silent Type");
    expect(el.querySelector(".gs-shop-itemdesc")).toBeNull();
  });
});

describe("shop: the merchant is a rig, not a collage", () => {
  // He was assembled from four `background-position` crops of his Spine ATLAS
  // PAGE (`scene_bgs/merchant_room/top` + `.../hand`) rotated into a pose. An
  // atlas page is a sheet of loose hoods, sleeves and hands, not a picture of
  // anyone, so what rendered was a floating hood over a disembodied arm. The
  // fix is to let his own skeleton assemble him, and the failure mode of a
  // relapse is silent — it still "renders", just wrongly — so pin it.
  it("draws him from his own skeleton", () => {
    const c = shop();
    const m = c.querySelector(".gs-shop-merchant")!;
    expect(m).toBeTruthy();
    // A SpineSprite is a <canvas> it drives itself; jsdom has no WebGL so the
    // rig never paints, but the canvas is still the tell that a skeleton — and
    // not a stack of cropped <i> elements — is what draws him.
    expect(m.querySelector("canvas")).toBeTruthy();
  });

  it("keeps no atlas-crop limbs behind", () => {
    const c = shop();
    const m = c.querySelector(".gs-shop-merchant")!;
    for (const cls of ["gs-shop-m-body", "gs-shop-m-head", "gs-shop-m-sleeve", "gs-shop-hand"]) {
      expect(m.querySelector(`.${cls}`)).toBeNull();
    }
  });
});

describe("shop: the discounted card", () => {
  /** The same shop, with the affordable card marked as the merchant's sale. */
  function saleShop() {
    const step = shopStep();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (step.state as any).cards[0].on_sale = true;
    return render(
      <HintsFixture on={false}><Shop step={step} onChoose={() => {}} /></HintsFixture>,
    ).container;
  }

  it("pins the % tag to the discounted card and nothing else", () => {
    // merchant_card.tscn shows its SaleVisual only when `IsOnSale`; exactly one
    // card per shop can carry it.
    const c = saleShop();
    expect(c.querySelectorAll(".gs-shop-cardsale")).toHaveLength(1);
    const stand = c.querySelector(".gs-shop-cardsale")!.closest(".gs-shop-cardstand")!;
    expect(stand.textContent).toContain("Breakthrough");
  });

  it("greens the price of the discounted card", () => {
    // `NMerchantCard.UpdateVisual`: StsColors.green when IsOnSale and the purse
    // covers it.
    const c = saleShop();
    const stand = c.querySelector(".gs-shop-cardsale")!.closest(".gs-shop-cardstand")!;
    expect(stand.querySelector(".gs-shop-pricetag.is-onsale")).not.toBeNull();
  });

  it("keeps red over green when you cannot pay", () => {
    // Red is C#'s first branch: `if (!EnoughGold) red; else IsOnSale ? green`.
    const step = shopStep();
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (step.state as any).cards[1].on_sale = true;
    const c = render(
      <HintsFixture on={false}><Shop step={step} onChoose={() => {}} /></HintsFixture>,
    ).container;
    const stand = c.querySelector(".gs-shop-cardsale")!.closest(".gs-shop-cardstand")!;
    expect(stand.querySelector(".gs-shop-pricetag.is-unaffordable")).not.toBeNull();
    expect(stand.querySelector(".gs-shop-pricetag.is-onsale")).toBeNull();
  });

  it("shows no tag in a shop with no sale", () => {
    expect(shop().querySelectorAll(".gs-shop-cardsale")).toHaveLength(0);
  });
});
