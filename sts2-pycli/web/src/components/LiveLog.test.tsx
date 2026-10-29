import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import LiveLog from "./LiveLog";
import type { LiveNode, LiveTimeline } from "../api";

function node(over: Partial<LiveNode>): LiveNode {
  return {
    id: 0, parent_id: null, idx: 0, decision: "map_select", label: "step",
    details: [], error: false, child_ids: [], floor: 1, act: 1, ...over,
  };
}

/** A linear timeline whose active path is every node in order. */
function timeline(nodes: LiveNode[]): LiveTimeline {
  return { nodes, active_path: nodes.map((n) => n.id), active_leaf: nodes[nodes.length - 1].id };
}

describe("LiveLog", () => {
  it("draws an Act divider when the act changes", () => {
    const tl = timeline([
      node({ id: 0, label: "Started", act: 1, floor: 0 }),
      node({ id: 1, parent_id: 0, idx: 1, label: "boss", act: 1, floor: 16 }),
      node({ id: 2, parent_id: 1, idx: 2, label: "act 2 begins", act: 2, floor: 17 }),
    ]);
    render(<LiveLog timeline={tl} onSeek={() => {}} />);
    // Act 1 is labelled at the top; Act 2 gets its own divider mid-log.
    expect(screen.getByLabelText("Act 1")).toBeInTheDocument();
    expect(screen.getByLabelText("Act 2")).toBeInTheDocument();
  });

  it("draws a floor label when only the floor changes within an act", () => {
    const tl = timeline([
      node({ id: 0, label: "floor 1", act: 1, floor: 1 }),
      node({ id: 1, parent_id: 0, idx: 1, label: "still floor 1", act: 1, floor: 1 }),
      node({ id: 2, parent_id: 1, idx: 2, label: "floor 2", act: 1, floor: 2 }),
    ]);
    render(<LiveLog timeline={tl} onSeek={() => {}} />);
    expect(screen.getByLabelText("Floor 2")).toBeInTheDocument();
    // Floor 1 appears only once (no repeat label for the unchanged second step).
    expect(screen.queryByLabelText("Floor 1")).toBeNull(); // floor 1 rides the Act 1 divider
    expect(screen.getByLabelText("Act 1")).toBeInTheDocument();
  });

  it("keeps later steps visible (de-emphasized) after seeking back", () => {
    // Linear run of three steps, but the run is positioned at the middle node:
    // the last step is "ahead" of the current position and must stay reachable.
    const nodes = [
      node({ id: 0, label: "floor 1", child_ids: [1] }),
      node({ id: 1, parent_id: 0, idx: 1, label: "floor 2", floor: 2, child_ids: [2] }),
      node({ id: 2, parent_id: 1, idx: 2, label: "floor 3", floor: 3 }),
    ];
    const tl: LiveTimeline = { nodes, active_path: [0, 1], active_leaf: 1 };
    const seeks: number[] = [];
    render(<LiveLog timeline={tl} onSeek={(id) => seeks.push(id)} />);
    // The forward step is still rendered, and offered as a forward jump.
    const fwd = screen.getByTitle("jump forward to this step");
    expect(fwd).toHaveTextContent("floor 3");
    fwd.click();
    expect(seeks).toEqual([2]);
  });

  it("groups steps under the turn their action was TAKEN in (the parent's round)", () => {
    // Node 2's own state is round 2 (end turn advanced it), but its action was
    // taken in round 1 — it must sit under TURN 1, not open TURN 2.
    const hit = {
      kind: "damage", round: 1, side: "player" as const,
      text: "The Ironclad dealt 6 damage to Nibbit using Strike.",
      verb: "dealt", actor: "The Ironclad", actor_side: "player" as const,
      target: "Nibbit", target_side: "enemy" as const, amount: 6, noun: null, source: "Strike",
    };
    const tl = timeline([
      node({ id: 0, label: "enter combat", turn: 1, child_ids: [1] }),
      node({ id: 1, parent_id: 0, idx: 1, label: "play Strike", turn: 1, child_ids: [2], combat_log: [hit] }),
      node({ id: 2, parent_id: 1, idx: 2, label: "end turn", turn: 2, child_ids: [3] }),
      node({ id: 3, parent_id: 2, idx: 3, label: "play Bash", turn: 2, combat_log: [{ ...hit, amount: 8, round: 2 }] }),
    ]);
    const { container } = render(<LiveLog timeline={tl} onSeek={() => {}} />);
    expect(screen.getByLabelText("Turn 1")).toHaveTextContent("⚔ 6");
    expect(screen.getByLabelText("Turn 2")).toHaveTextContent("⚔ 8");
    // Exactly two turn headers: end turn did not open a spurious group.
    expect(container.querySelectorAll("[aria-label^='Turn']").length).toBe(2);
  });

  it("shows neither view-only rows nor the toggle they would falsely enable", () => {
    // A node's slice carries the view-only `power_flash` rows — the board's
    // effect stream needs them — so the ledger drops them, and a step carrying
    // nothing else counts as having no tape (falling back to its diff lines).
    const flash = {
      kind: "power_flash", round: 1, side: "enemy" as const,
      text: "Nibbit's Thorns triggered.", verb: "triggered",
      actor: null, actor_side: null, target: "Nibbit", target_side: "enemy" as const,
      amount: null, noun: "Thorns", source: null,
    };
    const tl = timeline([
      node({ id: 0, label: "enter combat", turn: 1, child_ids: [1] }),
      node({ id: 1, parent_id: 0, idx: 1, label: "play Strike", turn: 1,
        details: ["Nibbit HP 50→44 (−6)"], combat_log: [flash] }),
    ]);
    const { container } = render(<LiveLog timeline={tl} onSeek={() => {}} />);
    expect(screen.queryByText("Thorns")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("History detail")).not.toBeInTheDocument();
    const details = [...container.querySelectorAll("[data-detail]")].map((d) => d.textContent);
    expect(details).toEqual(["Nibbit HP 50→44 (−6)"]);
  });

  it("omits separators when floor/act are absent (e.g. a stub backend)", () => {
    const tl = timeline([
      node({ id: 0, label: "a", act: null, floor: null }),
      node({ id: 1, parent_id: 0, idx: 1, label: "b", act: null, floor: null }),
    ]);
    const { container } = render(<LiveLog timeline={tl} onSeek={() => {}} />);
    expect(container.querySelectorAll("[aria-label^='Act']").length).toBe(0);
    expect(container.querySelectorAll("[aria-label^='Floor']").length).toBe(0);
  });
});
