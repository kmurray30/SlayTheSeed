import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import HelpOverlay from "./HelpOverlay";

describe("HelpOverlay", () => {
  it("renders nothing when closed", () => {
    const { container } = render(<HelpOverlay open={false} onClose={() => {}} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("lists shortcut groups when open", () => {
    render(<HelpOverlay open onClose={() => {}} />);
    expect(screen.getByText(/Keyboard shortcuts/i)).toBeInTheDocument();
    expect(screen.getByText(/End turn/i)).toBeInTheDocument();
    expect(screen.getByText(/previous \/ next step/i)).toBeInTheDocument();
  });

  it("calls onClose when the backdrop is clicked", () => {
    const onClose = vi.fn();
    render(<HelpOverlay open onClose={onClose} />);
    fireEvent.click(screen.getByTestId("help-backdrop"));
    expect(onClose).toHaveBeenCalled();
  });

  it("calls onClose on Escape while open", () => {
    const onClose = vi.fn();
    render(<HelpOverlay open onClose={onClose} />);
    fireEvent.keyDown(window, { key: "Escape" });
    expect(onClose).toHaveBeenCalled();
  });

  it("lists the play-recommended and autoplay keys", () => {
    render(<HelpOverlay open onClose={() => {}} />);
    expect(screen.getByText(/play the recommended move/i)).toBeInTheDocument();
    expect(screen.getByText(/autoplay the turn/i)).toBeInTheDocument();
  });
});
