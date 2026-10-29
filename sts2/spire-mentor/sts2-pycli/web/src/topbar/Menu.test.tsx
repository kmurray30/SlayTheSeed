import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import Menu from "./Menu";

function build(over: { onSelect?: () => void; disabledReason?: string; onEnd?: () => void } = {}) {
  return render(
    <Menu label="Run" items={[
      { id: "save", label: "Save", onSelect: over.onSelect ?? (() => {}), disabledReason: over.disabledReason },
      { id: "load", label: "Load", onSelect: () => {} },
      null,
      { id: "end-run", label: "End run", danger: true, onSelect: over.onEnd ?? (() => {}) },
    ]} />,
  );
}

describe("Menu", () => {
  it("is closed until the trigger is clicked, then lists the items with roles", () => {
    build();
    expect(screen.queryByRole("menu")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: /Run/ }));
    expect(screen.getByRole("menu")).toBeInTheDocument();
    expect(screen.getAllByRole("menuitem").map((e) => e.textContent)).toEqual(["Save", "Load", "End run"]);
    expect(screen.getByRole("separator")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Run/ })).toHaveAttribute("aria-expanded", "true");
  });

  it("runs the item and closes on selection", () => {
    const onSelect = vi.fn();
    build({ onSelect });
    fireEvent.click(screen.getByRole("button", { name: /Run/ }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Save" }));
    expect(onSelect).toHaveBeenCalledOnce();
    expect(screen.queryByRole("menu")).toBeNull();
  });

  it("shows a disabled item with its reason and refuses to run it", () => {
    const onSelect = vi.fn();
    build({ onSelect, disabledReason: "no run in progress" });
    fireEvent.click(screen.getByRole("button", { name: /Run/ }));
    const save = screen.getByRole("menuitem", { name: /Save/ });
    expect(save).toHaveAttribute("aria-disabled", "true");
    expect(save).toHaveAttribute("title", "no run in progress");
    fireEvent.click(save);
    expect(onSelect).not.toHaveBeenCalled();
    expect(screen.getByRole("menu")).toBeInTheDocument();
  });

  it("closes on Escape and on a click outside", () => {
    build();
    const trigger = screen.getByRole("button", { name: /Run/ });
    fireEvent.click(trigger);
    fireEvent.keyDown(screen.getByRole("menu"), { key: "Escape" });
    expect(screen.queryByRole("menu")).toBeNull();
    fireEvent.click(trigger);
    fireEvent.mouseDown(document.body);
    expect(screen.queryByRole("menu")).toBeNull();
  });

  it("marks the destructive item", () => {
    build();
    fireEvent.click(screen.getByRole("button", { name: /Run/ }));
    expect(screen.getByRole("menuitem", { name: "End run" }).className).toContain("danger");
  });

  it("renders a checked item as a menuitemcheckbox", () => {
    render(<Menu label="Tools" items={[{ id: "dbg", label: "debug", checked: true, onSelect: () => {} }]} />);
    fireEvent.click(screen.getByRole("button", { name: /Tools/ }));
    expect(screen.getByRole("menuitemcheckbox", { name: /debug/ })).toHaveAttribute("aria-checked", "true");
  });
});
