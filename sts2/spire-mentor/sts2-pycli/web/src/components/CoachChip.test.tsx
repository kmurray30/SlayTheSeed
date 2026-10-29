import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import CoachChip from "./CoachChip";
import type { DecisionRegret } from "../api";

function regret(over: Partial<DecisionRegret> = {}): DecisionRegret {
  return {
    idx: 4, turn: 3, floor: 2, act: 1,
    played_label: "Strike", played_win_prob: 0.58,
    best_label: "Bash", best_win_prob: 0.71,
    regret: 0.13, drop_pct: 13, player_rank: 2, n_actions: 5, visits: 800,
    matched_best: false, is_blunder: true, message: "",
    played_mean_hp: 30, best_mean_hp: 42, hp_drop: 12, severity: 0.5,
    verdict: "blunder", best_line: ["Bash", "Defend", "end turn"],
    ...over,
  };
}

describe("CoachChip", () => {
  it("blunder shows the AI line and the HP cost", () => {
    const { container } = render(<CoachChip regret={regret()} onDismiss={() => {}} />);
    const t = container.textContent ?? "";
    expect(t).toContain("⚠ blunder");
    expect(t).toContain("you: Strike");
    expect(t).toContain("AI: Bash → Defend → end turn");
    expect(t).toContain("≈ 12 HP more lost");
    expect(t).toContain("win 71% → 58%");
  });

  it("equal win% reads 'either way'", () => {
    const { container } = render(<CoachChip regret={regret({ best_win_prob: 1, played_win_prob: 1, hp_drop: 10 })} onDismiss={() => {}} />);
    const t = container.textContent ?? "";
    expect(t).toContain("win 100% either way");
    expect(t).toContain("≈ 10 HP more lost");
    expect(t).not.toContain("→ 100%");
  });

  it("slip is labelled suboptimal", () => {
    render(<CoachChip regret={regret({ verdict: "slip" })} onDismiss={() => {}} />);
    expect(screen.getByText(/▽ suboptimal/)).toBeInTheDocument();
  });

  it("falls back to best label when best_line is empty", () => {
    const { container } = render(<CoachChip regret={regret({ best_line: [] })} onDismiss={() => {}} />);
    expect(container.textContent).toContain("AI: Bash");
  });

  it("tie shows toss-up and no AI line", () => {
    const { container } = render(<CoachChip regret={regret({ verdict: "tie" })} onDismiss={() => {}} />);
    expect(container.textContent).toContain("toss-up");
    expect(container.textContent).not.toContain("AI:");
  });

  it("best shows best move", () => {
    const { container } = render(<CoachChip regret={regret({ verdict: "best" })} onDismiss={() => {}} />);
    expect(container.textContent).toContain("✓ best move — Strike");
  });

  it("renders nothing for uncertain", () => {
    const { container } = render(<CoachChip regret={regret({ verdict: "uncertain" })} onDismiss={() => {}} />);
    expect(container.firstChild).toBeNull();
  });

  it("omits the HP part when hp_drop is null", () => {
    const { container } = render(<CoachChip regret={regret({ hp_drop: null })} onDismiss={() => {}} />);
    expect(container.textContent).not.toContain("HP more lost");
    expect(container.textContent).toContain("win 71% → 58%");
  });

  it("dismiss fires", () => {
    const f = vi.fn();
    render(<CoachChip regret={regret()} onDismiss={f} />);
    screen.getByRole("button", { name: /dismiss/i }).click();
    expect(f).toHaveBeenCalled();
  });
});
