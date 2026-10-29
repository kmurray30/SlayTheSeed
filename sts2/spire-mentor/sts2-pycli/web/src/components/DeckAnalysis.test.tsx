import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, afterEach, beforeEach } from "vitest";
import DeckAnalysis from "./DeckAnalysis";
import type { DeckAnalysis as Report } from "../api";
import { __clearArchetypeCache } from "../archetype";

const REPORT: Report = {
  deck_size: 14,
  assumptions: { energy: 3, hand: 5, samples: 800 },
  scores: { damage: 70, block: 34, velocity: 81, volume: 58 },
  damage: {
    per_turn: 38.6,
    single_target: 29.5,
    vs_group: 48.2,
    scaling_cards: ["Inflame", "Demon Form"],
    aoe_attacks: ["Whirlwind"],
    attack_count: 6,
  },
  block: { per_turn: 6.6, scaling_cards: ["Feel No Pain"] },
  velocity: { tags: { draw: 3, energy: 1 }, engine_cards: 4, cards_per_turn: 4.1, extra_per_turn: 0.6 },
  composition: {
    by_type: { Attack: 6, Skill: 4, Power: 4 },
    by_rarity: { Basic: 5, Rare: 2 },
    energy_curve: { "0": 2, "1": 9, "2": 1, "3": 2 },
    dead_cards: 0,
    unupgraded_basics: 2,
  },
  warnings: [{ severity: "warn", text: "Thin on block." }],
};

describe("DeckAnalysis", () => {
  it("renders the volume, axis scores, scaling chips and warnings", () => {
    render(<DeckAnalysis report={REPORT} onClose={() => {}} />);
    expect(screen.getByText("58")).toBeInTheDocument(); // volume
    expect(screen.getByText("Demon Form")).toBeInTheDocument(); // damage scaling chip
    expect(screen.getByText("Whirlwind")).toBeInTheDocument(); // AOE chip
    expect(screen.getByText(/Thin on block/)).toBeInTheDocument(); // warning
    expect(screen.getByText(/Deck Analysis \(14 cards\)/)).toBeInTheDocument();
    expect(screen.getByText("4.1")).toBeInTheDocument(); // simulated velocity: cards/turn
    expect(screen.getByText(/29.5/)).toBeInTheDocument(); // single-target damage/turn
  });

  it("shows a loading state when the report is null", () => {
    render(<DeckAnalysis report={null} onClose={() => {}} />);
    expect(screen.getByText("analyzing…")).toBeInTheDocument();
  });

  it("handles an empty deck", () => {
    render(<DeckAnalysis report={{ empty: true } as Report} onClose={() => {}} />);
    expect(screen.getByText(/no deck available/)).toBeInTheDocument();
  });

  it("renders the run-trend sparkline when trend points are supplied", () => {
    const trend = [
      { idx: 0, floor: 1, act: 1, deck_size: 10, scores: { damage: 40, block: 30, velocity: 0, volume: 0 } },
      { idx: 5, floor: 6, act: 1, deck_size: 12, scores: { damage: 55, block: 35, velocity: 40, volume: 42 } },
    ];
    render(<DeckAnalysis report={REPORT} trend={trend} onClose={() => {}} />);
    expect(screen.getByText("Run trend")).toBeInTheDocument();
    expect(screen.getByText(/2 deck changes/)).toBeInTheDocument();
  });
});

describe("DeckAnalysis archetype membership", () => {
  const ARCHETYPES = {
    character: "ironclad",
    archetypes: [
      {
        id: "strength",
        label: "Strength Engine",
        tag: "confirmed",
        cards: [
          { token: "INFLAME", weight: 1.0 },
          { token: "LIMIT_BREAK", weight: 0.9 },
          { token: "SPOT_WEAKNESS", weight: 0.6 },
        ],
        curve: {
          targetK: 3,
          points: [
            { k: 0, v: -20, additive: -20 },
            { k: 1, v: -17, additive: -17 },
            { k: 2, v: -13, additive: -14 },
            { k: 3, v: -6, additive: -11 }, // super-additive at piece 3
          ],
        },
      },
    ],
  };

  beforeEach(() => __clearArchetypeCache());
  afterEach(() => vi.unstubAllGlobals());

  it("shows membership + next-piece super-additivity when deck tokens are supplied", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({ ok: true, json: async () => ARCHETYPES }) as Response),
    );
    render(
      <DeckAnalysis
        report={REPORT}
        onClose={() => {}}
        deckTokens={["INFLAME", "LIMIT_BREAK", "STRIKE_IRONCLAD"]}
        character="ironclad"
      />,
    );
    // Deck holds 2 of 3 pieces; the 3rd turns super-additive on the curve.
    expect(await screen.findByText("Strength Engine")).toBeInTheDocument();
    expect(screen.getByText(/2 of 3 pieces/)).toBeInTheDocument();
    expect(screen.getByText(/super-additive/)).toBeInTheDocument();
  });

  it("renders no archetype panel without deck tokens (backward compatible)", () => {
    render(<DeckAnalysis report={REPORT} onClose={() => {}} />);
    expect(screen.queryByText("Archetypes")).not.toBeInTheDocument();
  });
});
