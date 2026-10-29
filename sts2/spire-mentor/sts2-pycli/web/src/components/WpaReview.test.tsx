import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import WpaReview from "./WpaReview";
import type { WpaPoint, WpaReview as Review, WpaTurningPoint, WpaVerify } from "../api";

function point(idx: number, value: number, over: Partial<WpaPoint> = {}): WpaPoint {
  return {
    node_id: 100 + idx,
    idx,
    decision: "card_reward",
    floor: idx + 1,
    act: 1,
    value,
    delta_prev: 0,
    taken_label: `Take card ${idx}`,
    ...over,
  };
}

function turningPoint(over: Partial<WpaTurningPoint> = {}): WpaTurningPoint {
  return {
    node_id: 101,
    idx: 1,
    decision: "card_reward",
    floor: 2,
    act: 1,
    taken_label: "Take card 1",
    value_before: 0.52,
    value_after: 0.1,
    swing: -0.42,
    message: "floor 2 (card_reward): after “Take card 1” your outlook dropped -0.420",
    ...over,
  };
}

function review(over: Partial<Review> = {}): Review {
  return {
    available: true,
    points: [point(0, 0.5), point(1, 0.52), point(2, 0.1), point(3, 0.4)],
    turning_points: [turningPoint()],
    summary: { n_points: 4, first_value: 0.5, final_value: 0.4, won: false },
    caveats: ["not a win percentage", "swings mix choice with luck", "AI-relative"],
    ...over,
  };
}

describe("WpaReview dock", () => {
  it("renders as a non-modal region with the curve and turning points", () => {
    render(<WpaReview review={review()} onClose={() => {}} />);
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(screen.getByRole("region", { name: /wpa run review/i })).toBeInTheDocument();
    expect(screen.getByRole("img", { name: /run outlook curve/i })).toBeInTheDocument();
    // Message appears in the row (and again as the curve marker's tooltip).
    expect(screen.getAllByText(/your outlook dropped/).length).toBeGreaterThan(0);
    // The honesty rule is visible: outlook, not a win %.
    expect(screen.getByText(/not a win %/)).toBeInTheDocument();
  });

  it("shows the unavailable reason instead of a curve", () => {
    render(
      <WpaReview
        review={review({ available: false, reason: "no value data", points: [], turning_points: [] })}
        onClose={() => {}}
      />,
    );
    expect(screen.getByText("no value data")).toBeInTheDocument();
    expect(screen.queryByRole("img")).toBeNull();
  });

  it("clicking a turning point seeks there WITHOUT closing the dock", () => {
    const onSeek = vi.fn();
    const onClose = vi.fn();
    render(<WpaReview review={review()} onSeek={onSeek} onClose={onClose} />);
    fireEvent.click(screen.getByText("-0.420"));
    expect(onSeek).toHaveBeenCalledTimes(1);
    expect(onSeek.mock.calls[0][0].node_id).toBe(101);
    expect(onClose).not.toHaveBeenCalled();
  });

  it("verify button runs the counterfactual and renders the verdict", async () => {
    const verify: WpaVerify = {
      node_id: 101,
      decision: "card_reward",
      played: { label: "Take card 1", won: false, over: true, steps: 40, error: null },
      alternative: { label: "Skip", won: true, over: true, steps: 38, error: null },
      verdict: "alternative_better",
      note: "a paired spot check, not a probability",
    };
    const onVerify = vi.fn().mockResolvedValue(verify);
    render(<WpaReview review={review()} live onVerify={onVerify} onClose={() => {}} />);
    fireEvent.click(screen.getByText(/verify/));
    await waitFor(() =>
      expect(screen.getByText(/the alternative won where your line lost/)).toBeInTheDocument(),
    );
    expect(onVerify).toHaveBeenCalledTimes(1);
    expect(screen.getByText(/spot check, not a probability/)).toBeInTheDocument();
    // The button is consumed by the result — no double-verify.
    expect(screen.queryByText(/🔬 verify/)).toBeNull();
  });

  it("hides verify without an onVerify handler (replay mode)", () => {
    render(<WpaReview review={review()} onClose={() => {}} />);
    expect(screen.queryByText(/verify/)).toBeNull();
  });

  it("Escape closes the dock", () => {
    const onClose = vi.fn();
    render(<WpaReview review={review()} onClose={onClose} />);
    fireEvent.keyDown(window, { key: "Escape" });
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
