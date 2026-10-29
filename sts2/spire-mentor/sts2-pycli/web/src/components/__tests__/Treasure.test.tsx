import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import EventScreen from "../EventScreen";
import type { Step } from "../../api";

function treasureStep(chosen: Step["chosen_action"] = null, act = 1): Step {
  return {
    idx: 0, decision: "event_choice",
    state: {
      decision: "event_choice", event_name: "Treasure",
      event_options: [{ index: 0, title: "Open", is_locked: false }],
    } as unknown as Step["state"],
    scores: [], pv: null, chosen_action: chosen, source: "live",
    meta: { turn: null, step_no: 0, floor: 8, act, act_name: `Act ${act}` },
  };
}

describe("treasure room", () => {
  it("draws the chest room, not a bare option bar", () => {
    // A treasure room used to arrive as a one-button `event_choice` with a
    // blank name, so opening one registered as two lines in the log and
    // nothing on screen.
    const { container } = render(<EventScreen step={treasureStep()} onChoose={() => {}} />);
    expect(container.querySelector(".gs-treasure")).not.toBeNull();
    expect(container.querySelector(".gs-treasure-rig")).not.toBeNull();
    // No event-option plaque list on this screen.
    expect(container.querySelector(".gs-event-option")).toBeNull();
  });

  it("makes the chest itself the button", () => {
    const onChoose = vi.fn();
    render(<EventScreen step={treasureStep()} onChoose={onChoose} />);
    fireEvent.click(screen.getByRole("button", { name: /open the chest/i }));
    expect(onChoose).toHaveBeenCalledWith("choose_option", { option_index: 0 });
  });

  it("reads as opened once it has been", () => {
    const onChoose = vi.fn();
    render(<EventScreen step={treasureStep()} onChoose={onChoose} />);
    fireEvent.click(screen.getByRole("button", { name: /open the chest/i }));
    expect(screen.getByRole("button", { name: /chest opened/i })).toBeInTheDocument();
  });

  it("starts opened when replaying a run that opened it", () => {
    render(<EventScreen step={treasureStep(
      { action: "choose_option", args: { option_index: 0 } } as Step["chosen_action"])} />);
    expect(screen.getByRole("button", { name: /chest opened/i })).toBeInTheDocument();
  });

  it("picks the act's own chest room", () => {
    const { container } = render(<EventScreen step={treasureStep(null, 3)} onChoose={() => {}} />);
    // SpineSprite falls back to a flat <img> until the rig loads, so the rig
    // path is not in the DOM here — the wrapper carries the act instead.
    expect(container.querySelector(".gs-treasure-rig")).toHaveAttribute("data-act", "3");
  });

  it("clamps an out-of-range act rather than asking for art that isn't there", () => {
    const { container } = render(<EventScreen step={treasureStep(null, 7)} onChoose={() => {}} />);
    expect(container.querySelector(".gs-treasure-rig")).toHaveAttribute("data-act", "3");
  });

  it("leaves other events alone", () => {
    const step = treasureStep();
    (step.state as { event_name?: string }).event_name = "Abyssal Baths";
    const { container } = render(<EventScreen step={step} onChoose={() => {}} />);
    expect(container.querySelector(".gs-treasure")).toBeNull();
    expect(container.querySelector(".gs-event-option")).not.toBeNull();
  });
});

// ── the reveal panel ────────────────────────────────────────────────────────
// A treasure room is auto-resolved by the ENGINE on entry (gold paid, relic
// obtained, straight to the map), so it never arrives as a decision and the
// chest screen above is unreachable in a normal run — the room registered only
// as event-log lines. `TreasureReveal` is what makes it visible.

import TreasureReveal from "../TreasureReveal";
import type { RoomReveal } from "../../api";

const HAUL: RoomReveal = {
  kind: "treasure",
  act: 1,
  gold: 43,
  relic: { name: "Venerable Tea Set", description: "Gain 1 [gold]Energy[/gold] on turn 1." },
};

function renderReveal(reveal: RoomReveal, onDismiss = () => {}) {
  return render(<TreasureReveal reveal={reveal} onDismiss={onDismiss} />);
}

describe("treasure reveal", () => {
  it("names what the chest held and what it paid", () => {
    renderReveal(HAUL);
    expect(screen.getByText("What's Inside?")).toBeInTheDocument();
    expect(screen.getByText("Venerable Tea Set")).toBeInTheDocument();
    expect(screen.getByText(/\+43 gold/)).toBeInTheDocument();
  });

  it("shows the act's own chest, already open", () => {
    const { container } = renderReveal({ ...HAUL, act: 3 });
    // SpineSprite falls back to a flat <img> until the rig loads, so the rig
    // path is not in the DOM here — the wrapper carries the act instead.
    expect(container.querySelector(".gs-treasure-rig")).toHaveAttribute("data-act", "3");
  });

  it("clamps an out-of-range act rather than asking for art that isn't there", () => {
    const { container } = renderReveal({ ...HAUL, act: 9 });
    expect(container.querySelector(".gs-treasure-rig")).toHaveAttribute("data-act", "3");
  });

  it("says so when the chest was empty, instead of showing a hole", () => {
    // Silver Crucible vetoes the first chest: no relic, no gold, no rolls.
    renderReveal({ kind: "treasure", act: 1, gold: 0, relic: null });
    expect(screen.getByText(/chest was empty/i)).toBeInTheDocument();
    expect(screen.queryByText(/gold/i)).toBeNull();
  });

  it("dismisses on Continue", () => {
    const onDismiss = vi.fn();
    renderReveal(HAUL, onDismiss);
    fireEvent.click(screen.getByRole("button", { name: /continue/i }));
    expect(onDismiss).toHaveBeenCalled();
  });

  it("dismisses on a click anywhere in the panel", () => {
    const onDismiss = vi.fn();
    const { container } = renderReveal(HAUL, onDismiss);
    fireEvent.click(container.querySelector(".gs-treasure-reveal")!);
    expect(onDismiss).toHaveBeenCalled();
  });
});
