import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import SaveLoadDialog from "../SaveLoadDialog";
import type { SaveMeta } from "../../api";

function save(name: string): SaveMeta {
  return {
    name,
    saved_at: "2026-08-25T16:05:00Z",
    character: "Ironclad",
    ascension: 10,
    seed: "C9EXV1KTPH",
    step_count: 783,
    progress: { act: 3, floor: 46, hp: 57, max_hp: 107 },
  };
}

describe("SaveLoadDialog", () => {
  it("loads the clicked save", () => {
    const onLoad = vi.fn();
    render(<SaveLoadDialog saves={[save("A10-win")]} onLoad={onLoad} onClose={() => {}} />);
    fireEvent.click(screen.getByText("A10-win"));
    expect(onLoad).toHaveBeenCalledWith("A10-win");
  });

  // Restoring replays the whole saved line through the engine, so a long run
  // takes tens of seconds. With no feedback the dialog looked dead and each
  // further click started another concurrent restore.
  it("says which save is restoring and stops further clicks", () => {
    const onLoad = vi.fn();
    render(
      <SaveLoadDialog
        saves={[save("A10-win"), save("other")]}
        loading="A10-win"
        onLoad={onLoad}
        onClose={() => {}}
      />,
    );
    expect(screen.getByText(/restoring .*A10-win/)).toBeInTheDocument();
    fireEvent.click(screen.getByText("other"));
    fireEvent.click(screen.getByRole("cell", { name: /A10-win/ }));
    expect(onLoad).not.toHaveBeenCalled();
  });

  it("cannot be dismissed mid-restore, but closes normally otherwise", () => {
    const onClose = vi.fn();
    const { rerender } = render(
      <SaveLoadDialog saves={[save("A10-win")]} loading="A10-win" onLoad={() => {}} onClose={onClose} />,
    );
    fireEvent.click(screen.getByLabelText("Close"));
    fireEvent.keyDown(window, { key: "Escape" });
    expect(onClose).not.toHaveBeenCalled();

    rerender(<SaveLoadDialog saves={[save("A10-win")]} onLoad={() => {}} onClose={onClose} />);
    fireEvent.keyDown(window, { key: "Escape" });
    expect(onClose).toHaveBeenCalled();
  });
});
