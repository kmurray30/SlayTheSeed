import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import BugReportView from "./BugReportView";
import { api } from "../api";

afterEach(() => vi.restoreAllMocks());

test("renders a fetched dump read-only", async () => {
  vi.spyOn(api, "fetchBugReport").mockResolvedValue({
    schema_version: 1,
    meta: { id: "wb-abc123", mode: "live", character: "Ironclad", captured_at: "2026-06-26T12:00:00Z" },
    repro: { timeline: { active_leaf: 0 } },
    state: { game_state: { floor: 3 }, map: null },
    ui: { screen: "combat", overlay: null },
    note: "enemy hp wrong",
  });
  render(<BugReportView id="wb-abc123" />);
  await waitFor(() => expect(screen.getByText(/wb-abc123/)).toBeInTheDocument());
  expect(screen.getByText(/enemy hp wrong/)).toBeInTheDocument();
  expect(screen.getByText(/combat/)).toBeInTheDocument();
});

test("shows not-found on 404", async () => {
  vi.spyOn(api, "fetchBugReport").mockRejectedValue(new Error("404"));
  render(<BugReportView id="wb-missing" />);
  await waitFor(() => expect(screen.getByText(/not found/i)).toBeInTheDocument());
});

test("static mode: a report that carries a run can be loaded into this workbench", async () => {
  const transport = await import("../transport");
  vi.spyOn(transport, "isStaticMode").mockReturnValue(true);
  const bugreport = await import("../bugreport");
  const load = vi.spyOn(bugreport, "loadReportIntoWorkbench").mockResolvedValue({ kind: "live" });
  vi.spyOn(api, "fetchBugReport").mockResolvedValue({
    schema_version: 1,
    meta: { id: "wb-0123456789abcdef", mode: "live", character: "Silent" },
    repro: { persist: { character: "Silent", nodes: [{ id: 0 }, { id: 1 }] } },
    state: { game_state: { floor: 1 }, map: null },
    ui: { screen: "event", overlay: null },
    note: null,
  });
  render(<BugReportView id="wb-0123456789abcdef" />);
  const button = await screen.findByTestId("bugreport-load");
  expect(button).toHaveTextContent(/Load into this workbench/);
  // The tree's body is summarised, not dumped.
  expect(screen.getByText(/persisted tree, 2 nodes/)).toBeInTheDocument();
  button.click();
  await waitFor(() => expect(screen.getByTestId("bugreport-loaded")).toBeInTheDocument());
  expect(load).toHaveBeenCalledTimes(1);
  expect(screen.getByRole("link", { name: /Open the workbench/ })).toHaveAttribute("href", "/");
});
