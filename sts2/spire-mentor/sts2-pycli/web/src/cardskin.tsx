/**
 * Card-skin reference sheet.
 *
 * `CardFrame` composites the game's own card-template art, whose two colour
 * axes are independent: the frame is tinted by the card's POOL and the
 * banner/plaque/portrait-border by its RARITY. Bugs there (a swapped axis, a
 * missing variant, a mis-scaled 9-slice) are invisible on any one card and
 * obvious on a grid, so this page renders the grid.
 *
 * Static fixtures only — no backend. `npm run dev` then open /cardskin.html.
 */
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import CardFrame from "./components/CardFrame";
import type { Card } from "./api";
import "./theme.css";
import "./game-skin.css";

let nextIndex = 0;

function card(over: Partial<Card> & { name: string; image: string }): Card {
  return {
    index: nextIndex++,
    id: `CARD.${over.name.toUpperCase().replace(/[^A-Z]+/g, "_")}`,
    cost: 1,
    type: "Attack",
    description: "Deal 6 damage.",
    ...over,
  } as Card;
}

const art = (folder: string, slug: string) => `/assets/card_atlas/${folder}/${slug}.png`;

// One card per pool colour — the frame axis.
const POOLS: Card[] = [
  card({ name: "Bash", image: art("ironclad", "bash"), color: "red", rarity: "Common",
         cost: 2, description: "Deal 8 damage.\nApply 2 Vulnerable." }),
  card({ name: "Accelerant", image: art("silent", "accelerant"), color: "green", rarity: "Common",
         type: "Skill", description: "Gain 1 Energy.\nDraw 1 card." }),
  card({ name: "Ball Lightning", image: art("defect", "ball_lightning"), color: "blue",
         rarity: "Common", description: "Deal 7 damage.\nChannel 1 Lightning." }),
  card({ name: "Alchemize", image: art("colorless", "alchemize"), color: "colorless",
         rarity: "Uncommon", type: "Skill", description: "Obtain a random potion." }),
  card({ name: "Clumsy", image: art("curse", "clumsy"), color: "curse", rarity: "Curse",
         type: "Curse", cost: 0, description: "Unplayable.\nExhaust." }),
  card({ name: "Burn", image: art("status", "burn"), color: "colorless", rarity: "Status",
         type: "Status", cost: 0, description: "Unplayable.\nTake 2 damage at end of turn." }),
];

// One card per rarity — the banner / plaque / portrait-border axis. Same pool
// and same type throughout, so any difference here is rarity and nothing else.
const RARITIES: Card[] = ["Basic", "Common", "Uncommon", "Rare", "Event", "Quest"].map((rarity) =>
  card({ name: rarity, image: art("ironclad", "anger"), color: "red", rarity,
         description: `Rarity: ${rarity}.` })
);

// One card per type — the frame *texture* axis (Status/Curse fall back to skill).
const TYPES: Card[] = [
  card({ name: "Attack", image: art("ironclad", "whirlwind"), color: "red", rarity: "Common",
         type: "Attack", description: "Deal 8 damage to ALL enemies." }),
  card({ name: "Skill", image: art("ironclad", "armaments"), color: "red", rarity: "Common",
         type: "Skill", description: "Gain 5 [gold]Block[/gold].\nUpgrade a card in your hand for the rest of combat." }),
  card({ name: "Power", image: art("ironclad", "barricade"), color: "red", rarity: "Rare",
         type: "Power", cost: 3, description: "[gold]Block[/gold] is no longer reduced at the start of your turn." }),
  card({ name: "Upgraded", image: art("ironclad", "bash"), color: "red", rarity: "Common",
         cost: 2, upgraded: true, description: "Deal 10 damage.\nApply 3 [gold]Vulnerable[/gold]." }),
  card({ name: "A Very Long Card Name", image: art("ironclad", "aggression"), color: "red",
         rarity: "Uncommon", description: "A deliberately long description that has to shrink to fit inside the description box without spilling over the frame." }),
];

// The exact cards from the live deck screenshot this skin was matched against,
// so the two can be diffed side by side.
const AGAINST_GAME: Card[] = [
  card({ name: "Dismantle", image: art("ironclad", "dismantle"), color: "red", rarity: "Uncommon",
         description: "Deal 8 damage.\nIf the enemy is [gold]Vulnerable[/gold], hits twice." }),
  card({ name: "Uppercut", image: art("ironclad", "uppercut"), color: "red", rarity: "Uncommon",
         cost: 2, upgraded: true,
         description: "Deal 13 damage.\nApply 2 [gold]Weak[/gold].\nApply 2 [gold]Vulnerable[/gold]." }),
  card({ name: "Taunt", image: art("ironclad", "taunt"), color: "red", rarity: "Uncommon",
         type: "Skill", description: "Gain 7 [gold]Block[/gold].\nApply 1 [gold]Vulnerable[/gold]." }),
  card({ name: "Rupture", image: art("ironclad", "rupture"), color: "red", rarity: "Uncommon",
         type: "Power", description: "Whenever you lose HP on your turn, gain 1 [gold]Strength[/gold]." }),
  card({ name: "Crimson Mantle", image: art("ironclad", "crimson_mantle"), color: "red",
         rarity: "Rare", type: "Power",
         description: "At the start of your turn, lose 1 HP and gain 8 [gold]Block[/gold]." }),
  card({ name: "Twin Strike", image: art("ironclad", "twin_strike"), color: "red", rarity: "Basic",
         description: "Deal 5 damage twice." }),
];

// Cost glyphs: the star cost the Regent pays alongside energy, and the energy /
// star runs the game draws inline in card text. Both are easy to lose — the
// star cost never travels on the wire, and the inline tokens render as bare
// numbers (or vanish) if the markup is stripped.
const COSTS: Card[] = [
  card({ name: "Comet", image: art("regent", "comet"), color: "orange", rarity: "Rare",
         cost: 0, star_cost: 5, description: "Deal 30 damage to ALL enemies." }),
  card({ name: "Seven Stars", image: art("regent", "seven_stars"), color: "orange",
         rarity: "Rare", type: "Power", cost: 0, star_cost: 7,
         description: "At the start of your turn, gain [star]1[/star]." }),
  card({ name: "Crescent Spear", image: art("regent", "crescent_spear"), color: "orange",
         rarity: "Common", cost: 1, star_cost: 1,
         description: "Deal 6 damage.\nSpend [star]1[/star] to deal 2 more." }),
  card({ name: "Alignment", image: art("regent", "alignment"), color: "orange",
         rarity: "Uncommon", type: "Skill", cost: 0, star_cost: 3,
         description: "Gain [energy]2[/energy].\nGain [star]1[/star]." }),
  card({ name: "Adrenaline", image: art("colorless", "alchemize"), color: "colorless",
         rarity: "Uncommon", type: "Skill", cost: 0,
         description: "Gain [energy]3[/energy].\nDraw 2 cards.\n[gold]Exhaust[/gold]." }),
];

// The two ways a card in hand refuses a click, which the game paints as two
// different things: a resource shortfall reddens the cost numeral, and anything
// else (the Queen's Chains of Binding on a Bound card you have already spent
// this turn, a smogged Skill) gets the red `unplayable` slash over the orb with
// the numeral left alone — `NCard.UpdateEnergyCostVisuals`,
// `Visible = !reason.HasResourceCostReason()`. An afflicted card also prints
// its affliction under the description in purple
// (`CardModel.GetDescriptionForPile`).
const HAND_STATES: { card: Card; unaffordable?: boolean; blocked?: boolean }[] = [
  { card: card({ name: "Playable", image: art("silent", "backflip"), color: "green",
                 rarity: "Common", type: "Skill",
                 description: "Gain 5 [gold]Block[/gold].\nDraw 2 cards." }) },
  { card: card({ name: "Too Expensive", image: art("silent", "backflip"), color: "green",
                 rarity: "Common", type: "Skill", cost: 3,
                 description: "Gain 5 [gold]Block[/gold].\nDraw 2 cards." }),
    unaffordable: true },
  { card: card({ name: "Bound", image: art("silent", "backflip"), color: "green",
                 rarity: "Common", type: "Skill", affliction: "Bound",
                 description: "Gain 5 [gold]Block[/gold].\nDraw 2 cards.\n[purple]Bound[/purple]" }) },
  { card: card({ name: "Bound, Spent", image: art("silent", "backflip"), color: "green",
                 rarity: "Common", type: "Skill", affliction: "Bound",
                 description: "Gain 5 [gold]Block[/gold].\nDraw 2 cards.\n[purple]Bound[/purple]" }),
    blocked: true },
];

function StateRow({ title, width }: { title: string; width: number }) {
  return (
    <section style={{ marginBottom: 28 }}>
      <h2 style={{ font: "600 13px/1.4 system-ui", letterSpacing: ".08em",
                   textTransform: "uppercase", color: "#8b949e", margin: "0 0 10px" }}>
        {title}
      </h2>
      <div style={{ display: "flex", gap: 14, flexWrap: "wrap", alignItems: "flex-start" }}>
        {HAND_STATES.map((s) => (
          <CardFrame key={s.card.index} card={s.card} width={width}
            unaffordable={s.unaffordable} blocked={s.blocked} />
        ))}
      </div>
    </section>
  );
}

function Row({ title, cards, width }: { title: string; cards: Card[]; width: number }) {
  return (
    <section style={{ marginBottom: 28 }}>
      <h2 style={{ font: "600 13px/1.4 system-ui", letterSpacing: ".08em",
                   textTransform: "uppercase", color: "#8b949e", margin: "0 0 10px" }}>
        {title}
      </h2>
      <div style={{ display: "flex", gap: 14, flexWrap: "wrap", alignItems: "flex-start" }}>
        {cards.map((c) => <CardFrame key={c.index} card={c} width={width} />)}
      </div>
    </section>
  );
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <div style={{ padding: 28, background: "#0d1117", minHeight: "100vh" }}>
      <Row title="Matched against the live deck screen" cards={AGAINST_GAME} width={228} />
      <Row title="Cost glyphs — Regent star cost, and inline energy/star runs" cards={COSTS} width={228} />
      <Row title="Pool → frame tint" cards={POOLS} width={200} />
      <Row title="Rarity → banner / plaque / portrait border" cards={RARITIES} width={200} />
      <Row title="Type → frame texture, and edge cases" cards={TYPES} width={200} />
      <StateRow title="Hand states — playable / unaffordable / Bound / Bound and spent" width={228} />
      <Row title="Hand size (112px)" cards={POOLS} width={112} />
    </div>
  </StrictMode>
);
