import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import MapOverlay from "../MapOverlay";
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

describe("MapOverlay", () => {
  it("renders the map graph inside a dialog", () => {
    render(<MapOverlay rows={[]} graphs={[graph()]} currentIdx={0} onClose={() => {}} />);
    const dialog = screen.getByRole("dialog", { name: /map/i });
    expect(dialog).toBeInTheDocument();
    expect(screen.getByText(/The Guardian/)).toBeInTheDocument();
  });

  it("shows a loading state while the map is being fetched", () => {
    render(<MapOverlay rows={[]} graphs={[]} currentIdx={0} loading onClose={() => {}} />);
    expect(screen.getByText(/loading map/i)).toBeInTheDocument();
  });

  it("closes on the close button and on Escape", () => {
    const onClose = vi.fn();
    render(<MapOverlay rows={[]} graphs={[graph()]} currentIdx={0} onClose={onClose} />);
    fireEvent.click(screen.getByRole("button", { name: /close/i }));
    expect(onClose).toHaveBeenCalledTimes(1);
    fireEvent.keyDown(window, { key: "Escape" });
    expect(onClose).toHaveBeenCalledTimes(2);
  });
});
