import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import CardReward from "../CardReward";
import { HintsFixture } from "../../hints/HintsContext";
import type { Step } from "../../api";

const card = (index: number, name: string, type: string, cost = 1) => ({
  index, id: `CARD.${name.toUpperCase()}`, name, type, cost,
  image: null, description: `${name} does a thing.`,
});

function step(state: Record<string, unknown>, chosen: Step["chosen_action"] = null): Step {
  return {
    idx: 0, decision: "card_reward",
    state: {
      decision: "card_reward", can_skip: true, gold_earned: 27,
      cards: [card(0, "Cleave", "Attack"), card(1, "Shrug It Off", "Skill")],
      ...state,
    } as Step["state"],
    scores: [], pv: null, chosen_action: chosen, source: "live",
    meta: { turn: null, step_no: 0, floor: 12, act: 1, act_name: "Act 1" },
  };
}

describe("card reward ledger", () => {
  it("shows the gold the fight paid with the hints switch off", () => {
    // The gold earned is a fact about the run, not advice. It used to live in
    // the advisor rail, which is gated behind a hints switch that is OFF by
    // default — so in ordinary live play the fight's gold was shown nowhere.
    render(
      <HintsFixture on={false}><CardReward step={step({})} onChoose={() => {}} /></HintsFixture>,
    );
    expect(screen.getByText("+27 gold")).toBeInTheDocument();
  });

  it("still shows it with hints on", () => {
    render(
      <HintsFixture on><CardReward step={step({})} onChoose={() => {}} /></HintsFixture>,
    );
    expect(screen.getByText("+27 gold")).toBeInTheDocument();
  });

  it("says what was taken when reviewing a finished run", () => {
    render(
      <HintsFixture on={false}>
        <CardReward step={step({}, { action: "skip_card_reward", args: {} } as Step["chosen_action"])} />
      </HintsFixture>,
    );
    // The Skip button carries its own chosen badge; the ledger line is the one
    // inside the ledger row.
    const ledger = document.querySelector(".gs-cardreward-ledger");
    expect(ledger?.textContent).toContain("skipped");
  });

  it("prints no ledger line when the wire carried no gold", () => {
    render(
      <HintsFixture on={false}>
        <CardReward step={step({ gold_earned: null })} onChoose={() => {}} />
      </HintsFixture>,
    );
    expect(screen.queryByText(/gold/)).toBeNull();
  });
});
