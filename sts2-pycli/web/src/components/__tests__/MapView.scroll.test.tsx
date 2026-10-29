import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import MapView from "../MapView";
import { HintsFixture } from "../../hints/HintsContext";
import type { MapRow } from "../../api";

/** A replay recorded without a captured full graph: only the reconstructed
 *  floor rows, which `ReconstructedRows` lays out newest-floor-FIRST. */
const rows: MapRow[] = [1, 2, 3].map((floor) => ({
  idx: floor - 1, act: 1, act_name: "act 1", floor,
  nodes: [{ col: 0, row: floor - 1, type: "Monster", chosen: true }],
}));

describe("map scroll parking", () => {
  it("marks the current floor row so the scroll effect can centre it", () => {
    // The two renderers run opposite ways: the SVG lattice puts row 0 at the
    // BOTTOM, `ReconstructedRows` reverses so the newest floor is at the TOP.
    // Scrolling the reconstructed list to `scrollHeight` therefore landed every
    // graph-less replay on floor 1 with the current row off the top. Centring
    // needs a marker to aim at, and this list had none.
    const { container } = render(
      <HintsFixture on={false}><MapView rows={rows} graphs={[]} currentIdx={2} /></HintsFixture>,
    );
    const marked = container.querySelectorAll("[data-current]");
    expect(marked).toHaveLength(1);
    expect(marked[0].textContent).toContain("floor 3");
  });

  it("marks nothing when the viewed step is not one of the rows", () => {
    const { container } = render(
      <HintsFixture on={false}><MapView rows={rows} graphs={[]} currentIdx={99} /></HintsFixture>,
    );
    expect(container.querySelectorAll("[data-current]")).toHaveLength(0);
  });
});
