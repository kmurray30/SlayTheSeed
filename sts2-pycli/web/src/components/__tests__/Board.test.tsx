import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import Board from "../Board";
import fixture from "./fixture-step.json";
import type { Step } from "../../api";

describe("Board", () => {
  const step = fixture as unknown as Step;

  it("renders player, enemy, hp, block and hand card", () => {
    render(<Board step={step} />);
    expect(screen.getByText("Ironclad")).toBeInTheDocument();
    expect(screen.getByText("Scroll of Biting")).toBeInTheDocument();
    expect(screen.getByText("58/72")).toBeInTheDocument();
    expect(screen.getAllByText("Strike").length).toBeGreaterThan(0);
    // intent number is shown
    expect(screen.getByText("12")).toBeInTheDocument();
  });

  it("renders enemy sprite from the resolved image url", () => {
    render(<Board step={step} />);
    const img = screen.getByAltText("Scroll of Biting") as HTMLImageElement;
    expect(img.src).toContain("/assets/monsters/scroll_of_biting.png");
  });

  it("renders Necrobinder's Osty when alive", () => {
    const ostyStep = {
      ...step,
      state: {
        ...step.state,
        osty: { name: "Osty", hp: 25, max_hp: 40, block: 5, alive: true, image: "/assets/monsters/osty.png" },
      },
    } as unknown as Step;
    render(<Board step={ostyStep} />);
    expect(screen.getByText("Osty")).toBeInTheDocument();
    expect(screen.getByText("25/40")).toBeInTheDocument();
    const img = screen.getByAltText("Osty") as HTMLImageElement;
    expect(img.src).toContain("/assets/monsters/osty.png");
  });

  it("does not render Osty for other characters (no osty in state)", () => {
    render(<Board step={step} />);
    expect(screen.queryByText("Osty")).not.toBeInTheDocument();
  });

  it("does not render Osty once it is dead", () => {
    const deadStep = {
      ...step,
      state: { ...step.state, osty: { alive: false } },
    } as unknown as Step;
    render(<Board step={deadStep} />);
    expect(screen.queryByText("Osty")).not.toBeInTheDocument();
  });
});
