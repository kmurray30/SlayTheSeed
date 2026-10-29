import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import MiniMap from "../MiniMap";
import type { MapGraph } from "../../api";

function graph(): MapGraph {
  return {
    idx: 0, act: 1, act_name: "act 1", floor: 3,
    full_map: {
      rows: [[{ col: 0, row: 0, type: "Monster", children: [{ col: 0, row: 1 }], visited: true }]],
      boss: { col: 0, row: 2, type: "Boss", name: "The Guardian" },
      current_coord: { col: 0, row: 0 },
    },
  };
}

describe("MiniMap", () => {
  it("renders nothing until a graph is captured", () => {
    const { container } = render(
      <MiniMap graphs={[]} currentIdx={0} collapsed={false}
        onToggleCollapsed={() => {}} onExpand={() => {}} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("renders the map graph when collapsed is false", () => {
    render(
      <MiniMap graphs={[graph()]} currentIdx={0} collapsed={false}
        onToggleCollapsed={() => {}} onExpand={() => {}} />,
    );
    // The boss name appears twice: once in the always-visible header label and
    // once on the boss node inside the rendered SVG body.
    expect(screen.getAllByText(/The Guardian/)).toHaveLength(2);
  });

  it("hides the body but keeps the header (with the next-boss name) when collapsed", () => {
    render(
      <MiniMap graphs={[graph()]} currentIdx={0} collapsed
        onToggleCollapsed={() => {}} onExpand={() => {}} />,
    );
    // The SVG body (and its boss-node label) is hidden, but the header still
    // names the upcoming boss so the destination stays visible when collapsed.
    expect(screen.getByText(/map/i)).toBeInTheDocument();
    expect(screen.getByText(/next boss/i)).toBeInTheDocument();
    expect(screen.getByText(/The Guardian/)).toBeInTheDocument();
  });

  it("names the next boss in the header", () => {
    render(
      <MiniMap graphs={[graph()]} currentIdx={0} collapsed={false}
        onToggleCollapsed={() => {}} onExpand={() => {}} />,
    );
    expect(screen.getByTitle("next boss: The Guardian")).toBeInTheDocument();
  });

  it("names BOTH bosses at A10, in the order they are fought", () => {
    // The final act carries a second boss node above the first, and it is a
    // different encounter — the game's top bar pins its icon beside the act
    // boss's (`NTopBarBossIcon.RefreshBossIcon`).
    const g = graph();
    g.full_map.second_boss = { col: 0, row: 3, type: "Boss", name: "Vantom" };
    render(
      <MiniMap graphs={[g]} currentIdx={0} collapsed
        onToggleCollapsed={() => {}} onExpand={() => {}} />,
    );
    expect(screen.getByTitle("next boss: The Guardian → Vantom")).toBeInTheDocument();
  });

  it("drops the act boss from the header once the run stands on it", () => {
    // `ShouldOnlyShowSecondBossIcon`: on the first boss node, the only boss
    // still ahead is the second one.
    const g = graph();
    g.full_map.second_boss = { col: 0, row: 3, type: "Boss", name: "Vantom" };
    g.full_map.current_coord = { col: 0, row: 2 };
    render(
      <MiniMap graphs={[g]} currentIdx={0} collapsed
        onToggleCollapsed={() => {}} onExpand={() => {}} />,
    );
    expect(screen.getByTitle("next boss: Vantom")).toBeInTheDocument();
  });

  it("omits the next-boss label when the graph has no named boss", () => {
    const g = graph();
    g.full_map.boss = null;
    render(
      <MiniMap graphs={[g]} currentIdx={0} collapsed
        onToggleCollapsed={() => {}} onExpand={() => {}} />,
    );
    expect(screen.queryByText(/next boss/i)).not.toBeInTheDocument();
  });

  it("fires onExpand from the full button and onToggleCollapsed from the caret", () => {
    const onExpand = vi.fn();
    const onToggle = vi.fn();
    render(
      <MiniMap graphs={[graph()]} currentIdx={0} collapsed={false}
        onToggleCollapsed={onToggle} onExpand={onExpand} />,
    );
    fireEvent.click(screen.getByRole("button", { name: /full/i }));
    expect(onExpand).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByTitle(/hide the minimap/i));
    expect(onToggle).toHaveBeenCalledTimes(1);
  });
});
