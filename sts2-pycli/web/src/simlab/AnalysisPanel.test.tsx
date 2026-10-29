import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { makeApiMocks, CATALOG, samplePosition } from "./testMocks";
import type { SweepSpec } from "./api";

const mocks = vi.hoisted(() => ({ api: null as unknown as ReturnType<typeof import("./testMocks").makeApiMocks> }));
vi.mock("./api", () => ({
  get api() {
    return mocks.api;
  },
}));

import AnalysisPanel from "./AnalysisPanel";

beforeEach(() => {
  mocks.api = makeApiMocks();
});

describe("AnalysisPanel", () => {
  it("reports Built once the engine accepts the position", async () => {
    render(<AnalysisPanel position={samplePosition()} catalog={CATALOG} onSubmitted={vi.fn()} />);
    expect(await screen.findByText(/Built\./)).toBeInTheDocument();
    expect(screen.getByText(/nothing to report/)).toBeInTheDocument();
    expect(mocks.api.buildPosition).toHaveBeenCalledTimes(1);
  });

  it("shows the engine's refusal", async () => {
    mocks.api.buildPosition.mockRejectedValue(new Error("422 Unprocessable: $.deck.hand[0]: NOT_A_CARD"));
    render(<AnalysisPanel position={samplePosition()} catalog={CATALOG} onSubmitted={vi.fn()} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("NOT_A_CARD");
  });

  it("adopts an intent the engine rolled for an unnamed enemy", async () => {
    const pos = samplePosition();
    pos.monsters[0].intent = null;
    const rolled = { ...pos, monsters: [{ ...pos.monsters[0], intent: "INHALE" }] };
    mocks.api.buildPosition.mockResolvedValue({ position: rolled, wire: { wire_version: 1 }, notes: [] });
    const onNormalized = vi.fn();
    render(<AnalysisPanel position={pos} catalog={CATALOG} onSubmitted={vi.fn()} onNormalized={onNormalized} />);
    await waitFor(() => expect(onNormalized).toHaveBeenCalled());
    expect(onNormalized.mock.calls[0][0].monsters[0].intent).toBe("INHALE");
  });

  it("submits one start-state cell per first move", async () => {
    const onSubmitted = vi.fn();
    render(<AnalysisPanel position={samplePosition()} catalog={CATALOG} onSubmitted={onSubmitted} />);
    await screen.findByText(/Built\./);
    fireEvent.click(screen.getByText("play it out"));
    await waitFor(() => expect(onSubmitted).toHaveBeenCalledWith("sw1"));
    const spec = mocks.api.submit.mock.calls[0][0] as SweepSpec;
    expect(spec.start_states?.map(([label]) => label)).toEqual(["Play Strike → Fuzzy Wurm Crawler", "Play Bash → Fuzzy Wurm Crawler", "End turn"]);
    expect(spec.positions).toHaveLength(3);
    expect(spec.strategies).toEqual(["min_overblock"]);
    expect(spec.encounters).toEqual([]);
    expect(spec.name).toContain("FUZZY_WURM_CRAWLER_WEAK");
  });

  it("sweeps the position as is when first-move comparison is off", async () => {
    render(<AnalysisPanel position={samplePosition()} catalog={CATALOG} onSubmitted={vi.fn()} />);
    await screen.findByText(/Built\./);
    fireEvent.click(screen.getByLabelText(/compare first moves/));
    fireEvent.click(screen.getByText("mcts"));
    fireEvent.click(screen.getByText("play it out"));
    await waitFor(() => expect(mocks.api.submit).toHaveBeenCalled());
    const spec = mocks.api.submit.mock.calls[0][0] as SweepSpec;
    expect(spec.start_states?.map(([label]) => label)).toEqual(["as is"]);
    expect(spec.strategies).toEqual(["min_overblock", "mcts"]);
    expect(mocks.api.firstMoves).not.toHaveBeenCalled();
  });

  it("runs the MCTS search and highlights the best move", async () => {
    render(<AnalysisPanel position={samplePosition()} catalog={CATALOG} onSubmitted={vi.fn()} />);
    await screen.findByText(/Built\./);
    fireEvent.click(screen.getByText("search this position"));
    expect(await screen.findByText("best")).toBeInTheDocument();
    expect(screen.getByText("91.0")).toBeInTheDocument();
  });
});
