import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { CollapseButton, PanelGrip } from "../panelChrome";
import { usePanelFlag, usePanelHeight } from "../../panelPrefs";

/** A panel with the real chrome around a fixed-height body, so a drag/keypress
 *  on the grip is measured the way the live docks measure it. */
function Harness({ grow = "down", rendered = 120 }: {
  grow?: "up" | "down";
  rendered?: number;
}) {
  const [collapsed, setCollapsed] = usePanelFlag("t.collapsed", false);
  const [height, setHeight] = usePanelHeight("t.height", 200, 60, 400);
  return (
    <div>
      <CollapseButton collapsed={collapsed} onToggle={() => setCollapsed(!collapsed)}
        label="the test panel" />
      <div data-testid="body" data-h={height} style={{ maxHeight: height }}>
        {collapsed ? null : "body"}
      </div>
      <PanelGrip grow={grow} label="drag to resize" min={60} max={400}
        measure={() => rendered} onResize={setHeight} />
    </div>
  );
}

beforeEach(() => localStorage.clear());

describe("panel chrome", () => {
  it("folds the body away and remembers it", () => {
    render(<Harness />);
    expect(screen.getByTestId("body")).toHaveTextContent("body");
    fireEvent.click(screen.getByRole("button", { name: /collapse the test panel/ }));
    expect(screen.getByTestId("body")).toBeEmptyDOMElement();
    // Persisted, because a dock someone tucked away must stay tucked away
    // across the reload — that is the whole point of folding it.
    expect(localStorage.getItem("t.collapsed")).toBe("1");
    fireEvent.click(screen.getByRole("button", { name: /expand the test panel/ }));
    expect(localStorage.getItem("t.collapsed")).toBe("0");
  });

  it("drags from the height the panel is RENDERING, not the stored cap", () => {
    // Stored cap 200, actually rendering 120 (short content). Dragging down by
    // 30 must land on 150 — starting from the cap would jump +80 on the first
    // pixel of the drag.
    render(<Harness rendered={120} />);
    const grip = screen.getByRole("separator", { name: "drag to resize" });
    fireEvent.pointerDown(grip, { clientY: 300 });
    fireEvent.pointerMove(window, { clientY: 330 });
    fireEvent.pointerUp(window);
    expect(localStorage.getItem("t.height")).toBe("150");
    // The drag ends with the pointer: further movement must not keep resizing.
    fireEvent.pointerMove(window, { clientY: 500 });
    expect(localStorage.getItem("t.height")).toBe("150");
  });

  it("a bottom-docked panel grows when its top edge is dragged UP", () => {
    render(<Harness grow="up" rendered={120} />);
    fireEvent.pointerDown(screen.getByRole("separator"), { clientY: 300 });
    fireEvent.pointerMove(window, { clientY: 260 });
    fireEvent.pointerUp(window);
    expect(localStorage.getItem("t.height")).toBe("160");
  });

  it("resizes from the keyboard too — the grip is not mouse-only", () => {
    render(<Harness rendered={120} />);
    fireEvent.keyDown(screen.getByRole("separator"), { key: "ArrowDown" });
    expect(localStorage.getItem("t.height")).toBe("136");
  });

  it("clamps a stored height that no longer fits", () => {
    // A height dragged out on a desktop (or hand-edited) must not wedge the
    // dock open past its bounds on the next device to read the preference.
    localStorage.setItem("t.height", "9000");
    render(<Harness />);
    expect(screen.getByTestId("body")).toHaveAttribute("data-h", "400");
  });

  it("falls back to the default when the stored height is junk", () => {
    localStorage.setItem("t.height", "not-a-number");
    render(<Harness />);
    expect(screen.getByTestId("body")).toHaveAttribute("data-h", "200");
  });
});
