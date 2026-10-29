import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import type { Step } from "../api";
import RunEnded from "./RunEnded";

function mkStep(decision: string, extra: Record<string, unknown> = {}): Step {
  return {
    idx: 0,
    decision,
    state: { decision, player: { name: "Silent", hp: 18, max_hp: 50 }, ...extra } as Step["state"],
    scores: [],
    pv: null,
    chosen_action: null,
    source: "live",
    meta: { turn: null, step_no: 0, floor: 12, act_name: "The Reaches" },
  };
}

describe("RunEnded", () => {
  it("summarizes an abandoned mid-run and offers New run", () => {
    const onNewRun = vi.fn();
    render(<RunEnded step={mkStep("map_select")} logPath={null} onNewRun={onNewRun} onReviewReplay={() => {}} />);
    expect(screen.getByText("Run ended")).toBeInTheDocument();
    expect(screen.getByText("Silent")).toBeInTheDocument();
    expect(screen.getByText(/floor 12/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /new run/i }));
    expect(onNewRun).toHaveBeenCalledOnce();
  });

  it("hides Review replay when there is no log path", () => {
    render(<RunEnded step={mkStep("map_select")} logPath={null} onNewRun={() => {}} onReviewReplay={() => {}} />);
    expect(screen.queryByRole("button", { name: /review replay/i })).toBeNull();
  });

  it("offers Review replay when a log path exists", () => {
    const onReviewReplay = vi.fn();
    render(<RunEnded step={mkStep("map_select")} logPath="/logs/run.jsonl" onNewRun={() => {}} onReviewReplay={onReviewReplay} />);
    fireEvent.click(screen.getByRole("button", { name: /review replay/i }));
    expect(onReviewReplay).toHaveBeenCalledOnce();
  });

  it("shows Victory when the run ended on a won game_over", () => {
    render(<RunEnded step={mkStep("game_over", { victory: true })} logPath={null} onNewRun={() => {}} onReviewReplay={() => {}} />);
    expect(screen.getByText("Victory!")).toBeInTheDocument();
  });

  it("shows the ascension level when present", () => {
    render(<RunEnded step={mkStep("game_over", { victory: true, ascension: 15 })} logPath={null} onNewRun={() => {}} onReviewReplay={() => {}} />);
    expect(screen.getByText(/A15/)).toBeInTheDocument();
  });

  it("shows A0 for a base-ascension run", () => {
    render(<RunEnded step={mkStep("game_over", { victory: true, ascension: 0 })} logPath={null} onNewRun={() => {}} onReviewReplay={() => {}} />);
    expect(screen.getByText(/A0/)).toBeInTheDocument();
  });
});
