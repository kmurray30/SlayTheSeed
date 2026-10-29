import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import RegretReview from "./RegretReview";
import type { DecisionRegret, RegretReview as Review } from "../api";

function decision(over: Partial<DecisionRegret> = {}): DecisionRegret {
  return {
    idx: 4,
    turn: 3,
    floor: 2,
    act: 1,
    played_label: "Strike",
    played_win_prob: 0.58,
    best_label: "Defend",
    best_win_prob: 0.71,
    regret: 0.13,
    drop_pct: 13,
    player_rank: 2,
    n_actions: 5,
    visits: 800,
    matched_best: false,
    is_blunder: true,
    message: "",
    played_mean_hp: 30, best_mean_hp: 42, hp_drop: 12, severity: 0.5,
    verdict: "blunder", best_line: [],
    ...over,
  };
}

function review(over: Partial<Review> = {}): Review {
  const blunder = decision();
  return {
    decisions: [blunder],
    blunders: [blunder],
    summary: { total_decisions: 1, blunder_count: 1, cumulative_drop_pct: 13, worst_drop_pct: 13, cumulative_hp_drop: 12 },
    ...over,
  };
}

describe("RegretReview dock", () => {
  it("renders as a non-modal region, not a dialog", () => {
    render(<RegretReview review={review()} onClose={() => {}} />);
    // A bottom dock, not a modal dialog — nothing should claim role=dialog.
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(screen.getByRole("region", { name: /regret review/i })).toBeInTheDocument();
  });

  it("clicking a decision seeks there WITHOUT closing the dock", () => {
    const onSeek = vi.fn();
    const onClose = vi.fn();
    render(<RegretReview review={review()} onSeek={onSeek} onClose={onClose} />);

    fireEvent.click(screen.getByText(/Strike/).closest("button")!);

    // Passes the whole decision (so the parent can seek by node_id, stable
    // across rewinds) — not just its idx.
    expect(onSeek).toHaveBeenCalledWith(expect.objectContaining({ idx: 4 }));
    expect(onClose).not.toHaveBeenCalled(); // dock stays open — the core fix
  });

  it("re-run button fires onRerun without closing", () => {
    const onRerun = vi.fn();
    const onClose = vi.fn();
    render(<RegretReview review={review()} onRerun={onRerun} onClose={onClose} />);

    fireEvent.click(screen.getByRole("button", { name: /re-run/i }));

    expect(onRerun).toHaveBeenCalledOnce();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("hides the re-run button when no onRerun is provided", () => {
    render(<RegretReview review={review()} onClose={() => {}} />);
    expect(screen.queryByRole("button", { name: /re-run/i })).toBeNull();
  });

  it("closes on the ✕ button and on Escape", () => {
    const onClose = vi.fn();
    render(<RegretReview review={review()} onClose={onClose} />);

    fireEvent.click(screen.getByRole("button", { name: /close/i }));
    expect(onClose).toHaveBeenCalledTimes(1);

    fireEvent.keyDown(window, { key: "Escape" });
    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it("shows the analyzing placeholder until the review arrives", () => {
    const { rerender } = render(<RegretReview review={null} onClose={() => {}} />);
    expect(screen.getByText(/analyzing/i)).toBeInTheDocument();

    rerender(<RegretReview review={review()} onClose={() => {}} />);
    expect(screen.queryByText(/analyzing/i)).toBeNull();
    expect(screen.getByText(/−13 pts/)).toBeInTheDocument();
  });

  it("notes the live line in the header when reviewing a live run", () => {
    render(<RegretReview review={review()} live onClose={() => {}} />);
    expect(screen.getByText(/live run/i)).toBeInTheDocument();
  });

  it("slips are listed by verdict, ties are not", () => {
    const slip = decision({ idx: 5, played_label: "SlipCard", is_blunder: false, verdict: "slip", drop_pct: 5 });
    const tie = decision({ idx: 6, played_label: "TieCard", is_blunder: false, verdict: "tie", drop_pct: 9 });
    const b = decision();
    render(<RegretReview review={review({ decisions: [b, slip, tie] })} onClose={() => {}} />);
    expect(screen.getByText(/SlipCard/)).toBeInTheDocument();
    expect(screen.queryByText(/TieCard/)).toBeNull();
  });

  it("shows cumulative HP lost in the caption", () => {
    render(<RegretReview review={review()} onClose={() => {}} />);
    expect(screen.getByText(/12 HP lost/)).toBeInTheDocument();
  });
  const cleanSummary = { total_decisions: 2, blunder_count: 0, cumulative_drop_pct: 0, worst_drop_pct: 0, cumulative_hp_drop: 0 };
  const noBlunder = { blunders: [] as DecisionRegret[] };

  it("HP-only slip is not a clean run", () => {
    const slip = decision({ idx: 5, verdict: "slip", is_blunder: false, drop_pct: 0, hp_drop: 4 });
    render(<RegretReview review={review({ ...noBlunder, decisions: [slip],
      summary: { ...cleanSummary, cumulative_hp_drop: 4 } })} onClose={() => {}} />);
    expect(screen.queryByText(/clean run/)).toBeNull();
    expect(screen.getByText(/smaller slips/)).toBeInTheDocument();
  });

  it("uncertain decisions are reported as ungraded, not toss-ups", () => {
    const u = decision({ idx: 5, verdict: "uncertain", is_blunder: false, drop_pct: 0, hp_drop: null });
    render(<RegretReview review={review({ ...noBlunder, decisions: [u], summary: cleanSummary })} onClose={() => {}} />);
    expect(screen.queryByText(/clean run/)).toBeNull();
    expect(screen.queryByText(/toss-ups/)).toBeNull();
    expect(screen.getByText(/1 move\(s\) ungraded/)).toBeInTheDocument();
  });

  it("HP-only blunder headline shows HP, and rows sort by severity", () => {
    const a = decision({ idx: 1, played_label: "AAA", drop_pct: 0, hp_drop: 9.5, severity: 0.2, played_win_prob: 1, best_win_prob: 1 });
    const b = decision({ idx: 2, played_label: "BBB", drop_pct: 0, hp_drop: 3, severity: 0.9, played_win_prob: 1, best_win_prob: 1 });
    render(<RegretReview review={review({ decisions: [a, b], blunders: [a, b] })} onClose={() => {}} />);
    expect(screen.getByText(/≈10 HP/)).toBeInTheDocument();
    expect(screen.getAllByText(/100% either way/).length).toBe(2);
    const t = document.body.textContent ?? "";
    expect(t.indexOf("BBB")).toBeLessThan(t.indexOf("AAA"));
  });
});
