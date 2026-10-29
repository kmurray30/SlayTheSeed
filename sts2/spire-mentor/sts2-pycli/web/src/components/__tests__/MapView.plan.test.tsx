import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import MapView from "../MapView";
import MiniMap from "../MiniMap";
import MapOverlay from "../MapOverlay";
import { HintsFixture } from "../../hints/HintsContext";
import type { MapGraph } from "../../api";

/** A two-column act with the run-AI's planned route through col 1. */
function graph(): MapGraph {
  return {
    idx: 0, act: 1, act_name: "act 1", floor: 1,
    full_map: {
      rows: [[
        { col: 0, row: 0, type: "Monster", children: [{ col: 0, row: 1 }] },
        { col: 1, row: 0, type: "Event", children: [{ col: 1, row: 1 }] },
      ], [
        { col: 0, row: 1, type: "Rest", children: [] },
        { col: 1, row: 1, type: "Shop", children: [] },
      ]],
      boss: { col: 0, row: 2, type: "Boss", name: "The Guardian" },
      current_coord: { col: 0, row: 0 },
      path_plan: {
        best_col: 1, baseline_col: 0, diverged: true,
        route: [{ col: 1, row: 0 }, { col: 1, row: 1 }],
        columns: [
          { col: 0, score: 0.4, n_paths: 2, min_elites: 1, max_elites: 1, can_skip_elite: false, is_pick: false },
          { col: 1, score: 0.7, n_paths: 5, min_elites: 0, max_elites: 1, can_skip_elite: true, is_pick: true },
        ],
      },
    },
  };
}

const PLAN_LEGEND = /planned route to boss/i;

describe("MapView planned-route overlay", () => {
  it("hides the AI's planned route in live play while hints are off", () => {
    // Drawing a route across the map is the AI answering the map decision you
    // are standing on — the same spoiler as the policy panel, so it follows
    // the same switch.
    const { container } = render(
      <HintsFixture on={false}><MapView rows={[]} graphs={[graph()]} currentIdx={0} live /></HintsFixture>,
    );
    expect(screen.queryByText(PLAN_LEGEND)).toBeNull();
    expect(container.querySelector('[data-testid="plan-edge"]')).toBeNull();
  });

  it("shows it in live play once hints are on", () => {
    const { container } = render(
      <HintsFixture on><MapView rows={[]} graphs={[graph()]} currentIdx={0} live /></HintsFixture>,
    );
    expect(screen.getByText(PLAN_LEGEND)).toBeInTheDocument();
    expect(container.querySelector('[data-testid="plan-edge"]')).not.toBeNull();
  });

  it("still shows it in replay, which is review of a finished run", () => {
    const { container } = render(
      <HintsFixture on={false}><MapView rows={[]} graphs={[graph()]} currentIdx={0} /></HintsFixture>,
    );
    expect(screen.getByText(PLAN_LEGEND)).toBeInTheDocument();
    expect(container.querySelector('[data-testid="plan-edge"]')).not.toBeNull();
  });

  it("hides the route in the live minimap too — same graph, same leak", () => {
    const { container } = render(
      <HintsFixture on={false}>
        <MiniMap graphs={[graph()]} currentIdx={0} collapsed={false}
          onToggleCollapsed={() => {}} onExpand={() => {}} live />
      </HintsFixture>,
    );
    expect(container.querySelector('[data-testid="plan-edge"]')).toBeNull();
  });

  it("hides the route in the live map overlay too", () => {
    const { container } = render(
      <HintsFixture on={false}>
        <MapOverlay rows={[]} graphs={[graph()]} currentIdx={0} onClose={() => {}} live />
      </HintsFixture>,
    );
    expect(container.querySelector('[data-testid="plan-edge"]')).toBeNull();
  });
});

describe("the route carries no caption", () => {
  it("letters nothing beside its first step", () => {
    // r4 wrote "route" in italics next to the first hop. The line is already
    // the only red-pencil mark on a sheet of black print — naming it says
    // nothing the mark does not, and the game's map carries no text at all.
    // The legend slip beside the sheet still explains the pencil.
    const { container } = render(
      <HintsFixture on><MapView rows={[]} graphs={[graph()]} currentIdx={0} /></HintsFixture>,
    );
    expect(container.querySelector('[data-testid="plan-chip"]')).toBeNull();
    // The pencil itself, and the legend that explains it, both stay.
    expect(container.querySelector('[data-testid="plan-edge"]')).not.toBeNull();
    expect(screen.getByText(PLAN_LEGEND)).toBeInTheDocument();
  });
});
