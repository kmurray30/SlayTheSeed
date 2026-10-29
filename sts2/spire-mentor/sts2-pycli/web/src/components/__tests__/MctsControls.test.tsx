import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import MctsControls from "../MctsControls";
import { DEFAULT_MCTS_CONFIG, type MctsConfig } from "../../api";

describe("MctsControls", () => {
  it("applies each knob change immediately via onChange", () => {
    const seen: MctsConfig[] = [];
    const cfg: MctsConfig = { ...DEFAULT_MCTS_CONFIG, sims: 2000 };
    render(<MctsControls config={cfg} onChange={(c) => seen.push(c)} />);

    fireEvent.change(screen.getByLabelText(/sims/i, { selector: "select" }), { target: { value: "3000" } });
    fireEvent.change(screen.getByLabelText(/explore/i, { selector: "input" }), { target: { value: "2.0" } });
    fireEvent.change(screen.getByLabelText(/rollout/i, { selector: "select" }), { target: { value: "uniform" } });

    expect(seen).toHaveLength(3); // no run button — every edit fires
    expect(seen[0].sims).toBe(3000);
    expect(seen[1].c_puct).toBe(2.0);
    expect(seen[2].rollout_policy).toBe("uniform");
  });

  it("defaults dominance pruning on and toggles it off", () => {
    const seen: MctsConfig[] = [];
    render(<MctsControls config={DEFAULT_MCTS_CONFIG} onChange={(c) => seen.push(c)} />);
    const prune = screen.getByLabelText(/prune/i, { selector: "input" });
    expect(prune).toBeChecked();
    fireEvent.click(prune);
    expect(seen[0].dominance_pruning).toBe(false);
  });

  it("disables controls while a search is running", () => {
    render(<MctsControls config={DEFAULT_MCTS_CONFIG} onChange={() => {}} disabled />);
    expect(screen.getByLabelText(/sims/i, { selector: "select" })).toBeDisabled();
  });
});
