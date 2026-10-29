import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import BranchTree from "../BranchTree";
import type { LiveTimeline } from "../../api";

function node(over: Partial<LiveTimeline["nodes"][number]> & { id: number }) {
  return {
    parent_id: null, idx: 0, decision: "combat_play", label: `n${over.id}`,
    details: [], error: false, child_ids: [], ...over,
  };
}

// A forked tree: 0 → 1 → {2 (active), 3 (other branch)}.
const forked: LiveTimeline = {
  nodes: [
    node({ id: 0, label: "start", child_ids: [1] }),
    node({ id: 1, parent_id: 0, idx: 1, label: "Play Strike", child_ids: [2, 3] }),
    node({ id: 2, parent_id: 1, idx: 2, label: "End turn", child_ids: [] }),
    node({ id: 3, parent_id: 1, idx: 2, label: "Play Defend", child_ids: [] }),
  ],
  active_path: [0, 1, 2],
  active_leaf: 2,
};

describe("BranchTree", () => {
  it("renders nothing without a timeline", () => {
    const { container } = render(<BranchTree timeline={null} onSeek={() => {}} onClose={() => {}} />);
    expect(container.firstChild).toBeNull();
  });

  it("renders a node per tree entry and seeks on click", () => {
    const onSeek = vi.fn();
    render(<BranchTree timeline={forked} onSeek={onSeek} onClose={() => {}} />);
    // Every node appears, including the off-active-path branch.
    ["start", "Play Strike", "End turn", "Play Defend"].forEach((t) =>
      expect(screen.getByRole("button", { name: new RegExp(t) })).toBeInTheDocument(),
    );
    fireEvent.click(screen.getByRole("button", { name: /Play Defend/ }));
    expect(onSeek).toHaveBeenCalledWith(3);
  });

  it("marks the active leaf as the current position", () => {
    render(<BranchTree timeline={forked} onSeek={() => {}} onClose={() => {}} />);
    expect(screen.getByRole("button", { name: /End turn/ })).toHaveAttribute("aria-current", "step");
    expect(screen.getByRole("button", { name: /Play Defend/ })).not.toHaveAttribute("aria-current");
  });

  it("closes on the close button", () => {
    const onClose = vi.fn();
    render(<BranchTree timeline={forked} onSeek={() => {}} onClose={onClose} />);
    fireEvent.click(screen.getByRole("button", { name: /close/i }));
    expect(onClose).toHaveBeenCalled();
  });
});
