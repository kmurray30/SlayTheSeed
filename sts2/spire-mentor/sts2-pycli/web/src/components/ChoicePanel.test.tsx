import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import ChoicePanel from "./ChoicePanel";
import type { Option } from "../api";

const opts: Option[] = [
  { label: "Take Strike", action: "select_card_reward", args: { card_index: 0 }, enabled: true },
  { label: "Locked", action: "choose_option", args: { option_index: 1 }, enabled: false },
];

describe("ChoicePanel", () => {
  it("renders one button per option and disables disabled ones", () => {
    render(<ChoicePanel options={opts} onChoose={() => {}} />);
    expect(screen.getByRole("button", { name: "Take Strike" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Locked" })).toBeDisabled();
  });

  it("fires onChoose with the option's action and args", () => {
    const onChoose = vi.fn();
    render(<ChoicePanel options={opts} onChoose={onChoose} />);
    fireEvent.click(screen.getByRole("button", { name: "Take Strike" }));
    expect(onChoose).toHaveBeenCalledWith("select_card_reward", { card_index: 0 });
  });

  it("numbers only enabled options, 1-based, skipping disabled", () => {
    const mixed: Option[] = [
      { label: "First", action: "a", args: {}, enabled: true },
      { label: "Locked", action: "b", args: {}, enabled: false },
      { label: "Second", action: "c", args: {}, enabled: true },
    ];
    render(<ChoicePanel options={mixed} onChoose={() => {}} />);
    // Badges are aria-hidden, so accessible names stay the bare labels; the
    // number shows up in textContent.
    const first = screen.getByRole("button", { name: "First" });
    const second = screen.getByRole("button", { name: "Second" });
    expect(first).toHaveTextContent("1");
    expect(second).toHaveTextContent("2");
    const locked = screen.getByRole("button", { name: "Locked" });
    expect(locked).toBeDisabled();
    expect(locked).not.toHaveTextContent(/\d/); // disabled option gets no number
  });
});
