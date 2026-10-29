import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import type { ReactElement } from "react";
import CardReward from "./CardReward";
import { HintsFixture } from "../hints/HintsContext";
import type { Step, RewardPreview } from "../api";
import { __clearArchetypeCache } from "../archetype";

function step(): Step {
  return {
    idx: 3,
    decision: "card_reward",
    state: {
      decision: "card_reward",
      cards: [
        { index: 0, id: "ADRENALINE", name: "Adrenaline", cost: 0, type: "Skill", description: "", image: null },
        { index: 1, id: "BLUDGEON", name: "Bludgeon", cost: 3, type: "Attack", description: "", image: null },
      ],
      player: { name: "Ironclad", hp: 50, max_hp: 72, block: 0, deck: [{ index: 0, id: "STRIKE_IRONCLAD", name: "Strike", cost: 1, type: "Attack", description: "", image: null }] },
    } as unknown as Step["state"],
    scores: [],
    pv: null,
    chosen_action: null,
    source: "log",
    meta: { turn: null, step_no: 3 },
  };
}

const PREVIEW: RewardPreview = {
  base: { damage: 50, block: 33, velocity: 0, volume: 0 },
  candidates: [
    { index: 0, scores: { damage: 48, block: 31, velocity: 47, volume: 41 }, delta: { damage: -2, block: -2, velocity: 47, volume: 41 } },
    { index: 1, scores: { damage: 60, block: 31, velocity: 0, volume: 0 }, delta: { damage: 10, block: -2, velocity: 0, volume: 0 } },
  ],
};

// Route each fetched URL to its canned payload.
function stubFetch(byUrl: (url: string) => unknown) {
  const spy = vi.fn(async (url: string) => ({ ok: true, json: async () => byUrl(url) }) as Response);
  vi.stubGlobal("fetch", spy);
  return spy;
}

beforeEach(() => __clearArchetypeCache()); // the fetch cache is module-global
afterEach(() => vi.unstubAllGlobals());

/** The reward screen's chips are decision support, gated on the global hints
 *  switch (default OFF) whenever the player is the one picking. */
function renderReward(ui: ReactElement, on = true) {
  return render(<HintsFixture on={on}>{ui}</HintsFixture>);
}

describe("CardReward delta preview", () => {
  it("fetches and shows per-card volume deltas", async () => {
    stubFetch(() => PREVIEW);
    renderReward(<CardReward step={step()} onChoose={() => {}} />);
    // Adrenaline lifts volume; Bludgeon leaves it flat — both chips render.
    expect(await screen.findByText("volume +41")).toBeInTheDocument();
    expect(screen.getByText("volume 0")).toBeInTheDocument();
    expect(screen.getByText("V +47")).toBeInTheDocument();
  });
});

describe("CardReward archetype chip", () => {
  const ARCHETYPES = {
    character: "ironclad",
    archetypes: [
      {
        id: "combo",
        label: "Adrenaline Combo",
        tag: "confirmed",
        cards: [
          { token: "STRIKE_IRONCLAD", weight: 0.6 },
          { token: "ADRENALINE", weight: 1.0 },
        ],
        curve: {
          targetK: 2,
          points: [
            { k: 0, v: -20, additive: -20 },
            { k: 1, v: -17, additive: -17 },
            { k: 2, v: -10, additive: -14 }, // super-additive at piece 2
          ],
        },
      },
    ],
  };

  it("shows an archetype chip for a card advancing an archetype the deck holds", async () => {
    // Deck holds STRIKE_IRONCLAD (1 piece of the combo); offered ADRENALINE is a
    // weighty member → chip: '2nd Adrenaline Combo' with the super-additive mark.
    stubFetch((url) => (url.includes("/archetypes_ironclad.json") ? ARCHETYPES : PREVIEW));
    renderReward(<CardReward step={step()} onChoose={() => {}} />);
    expect(await screen.findByText(/Adrenaline Combo/)).toBeInTheDocument();
  });
});

describe("CardReward advice gate", () => {
  it("shows no chips — and fetches nothing — while hints are off in live play", async () => {
    const spy = stubFetch(() => PREVIEW);
    renderReward(<CardReward step={step()} onChoose={() => {}} />, false);
    await new Promise((r) => setTimeout(r, 0));
    expect(screen.queryByText(/^volume/)).toBeNull();
    expect(screen.queryByText(/synergy/)).toBeNull();
    expect(screen.queryByRole("button", { name: /analyze picks/i })).toBeNull();
    expect(spy.mock.calls.some(([u]) => String(u).includes("reward-preview"))).toBe(false);
    // The cards themselves are still there — you play the game, just unadvised.
    expect(screen.getByText("Adrenaline")).toBeInTheDocument();
  });

  it("keeps the chips in replay, where the pick has already been made", async () => {
    stubFetch(() => PREVIEW);
    renderReward(<CardReward step={step()} />, false);
    expect(await screen.findByText("volume +41")).toBeInTheDocument();
  });
});
