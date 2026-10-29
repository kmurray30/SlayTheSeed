import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent, act } from "@testing-library/react";
import type { Bucket, Results, SweepListEntry } from "./api";

// Capture the `traces` prop reference DistChart receives on each render so a
// test can assert it stays referentially stable across the App's sweep-list
// poll. `vi.hoisted` so the mock factory (hoisted above this) can close over it.
const captured = vi.hoisted(() => ({ distTraces: [] as unknown[] }));

// Mock the Plotly-backed chart components so jsdom never touches a canvas. Each
// renders a sentinel <div> so we can assert which tab is active.
vi.mock("./DistChart", () => ({
  DistChart: (props: { traces: unknown }) => {
    captured.distTraces.push(props.traces);
    return <div data-testid="dist-chart" />;
  },
}));
vi.mock("./CdfChart", () => ({
  CdfChart: () => <div data-testid="cdf-chart" />,
}));
vi.mock("./BoxChart", () => ({
  BoxChart: () => <div data-testid="box-chart" />,
}));

import { makeApiMocks, META } from "./testMocks";

function mkBucket(over: Partial<Bucket>): Bucket {
  return {
    n: 100,
    mean_dmg: 10,
    median_dmg: 9,
    p95_dmg: 20,
    max_dmg: 25,
    win_rate: 1,
    mean_turns: 5,
    errors: 0,
    dmg_values: [8, 9, 10, 25],
    kde: { x: [8, 25], y: [0.1, 0.1] },
    mode_dmg: 9,
    worst: 25,
    worst_p: 0.00025,
    box: { q1: 8, median: 9, q3: 12, lo: 5, hi: 22, outliers: [25] },
    ...over,
  };
}

const mocks = vi.hoisted(() => ({ api: null as unknown as ReturnType<typeof import("./testMocks").makeApiMocks> }));
vi.mock("./api", () => ({
  get api() {
    return mocks.api;
  },
}));
mocks.api = makeApiMocks();
const meta = mocks.api.meta;
const list = mocks.api.list as unknown as ReturnType<typeof vi.fn<() => Promise<{ sweeps: SweepListEntry[] }>>>;
const status = mocks.api.status as unknown as ReturnType<typeof vi.fn<(id: string) => Promise<{ status: string; done: number; total: number }>>>;
const results = mocks.api.results as unknown as ReturnType<typeof vi.fn<(id: string, opts?: unknown) => Promise<Results>>>;
const stop = mocks.api.stop;

import App from "./App";

beforeEach(() => {
  meta.mockReset();
  meta.mockResolvedValue(META);
  results.mockReset();
  // Reset status to the default "done" each test so a running/cancelled status
  // from one test can't leak into another (polling calls status repeatedly).
  status.mockReset();
  status.mockResolvedValue({ status: "done", done: 1, total: 1 });
  stop.mockClear();
  captured.distTraces.length = 0;
});

/** Render App and select a sweep so ResultsView mounts. */
function withSweep(entry: SweepListEntry, res: Results) {
  list.mockResolvedValue({ sweeps: [entry] });
  results.mockResolvedValue(res);
}

const SINGLE_DECK: SweepListEntry = {
  sweep_id: "sw1",
  spec: {
    name: "single",
    encounters: ["MAWLER_NORMAL"],
    ascensions: [0],
    strategies: ["block_first", "min_overblock"],
    n_seeds: 100,
    decks: [["starter", ["STRIKE_IRONCLAD"]]],
  },
  status: "done",
  mtime: 1,
};

const MULTI_DECK: SweepListEntry = {
  ...SINGLE_DECK,
  sweep_id: "sw2",
  spec: { ...SINGLE_DECK.spec, name: "multi", decks: [["starter", []], ["aggro", []]] },
};

describe("ResultsView chart tabs", () => {
  it("defaults to Distribution for a single-deck sweep", async () => {
    withSweep(SINGLE_DECK, {
      buckets: {
        "MAWLER_NORMAL/0/starter/block_first": mkBucket({}),
        "MAWLER_NORMAL/0/starter/min_overblock": mkBucket({}),
      },
      errors_total: 0,
    });
    render(<App />);
    fireEvent.click(await screen.findByText(/single ·/));
    expect(await screen.findByTestId("dist-chart")).toBeInTheDocument();
    expect(screen.queryByTestId("box-chart")).toBeNull();
  });

  it("defaults to Box plot for a multi-deck sweep and switches tabs", async () => {
    withSweep(MULTI_DECK, {
      buckets: {
        "MAWLER_NORMAL/0/starter/block_first": mkBucket({}),
        "MAWLER_NORMAL/0/aggro/block_first": mkBucket({}),
      },
      errors_total: 0,
    });
    render(<App />);
    fireEvent.click(await screen.findByText(/multi ·/));
    expect(await screen.findByTestId("box-chart")).toBeInTheDocument();

    fireEvent.click(screen.getByText("CDF"));
    expect(await screen.findByTestId("cdf-chart")).toBeInTheDocument();
    expect(screen.queryByTestId("box-chart")).toBeNull();

    fireEvent.click(screen.getByText("Distribution"));
    expect(await screen.findByTestId("dist-chart")).toBeInTheDocument();
  });

  it("renders the worst cell as 'max · 1 in N'", async () => {
    withSweep(SINGLE_DECK, {
      buckets: {
        "MAWLER_NORMAL/0/starter/block_first": mkBucket({ worst: 25, worst_p: 0.00025 }),
        "MAWLER_NORMAL/0/starter/min_overblock": mkBucket({ worst: 30, worst_p: 0.01 }),
      },
      errors_total: 0,
    });
    render(<App />);
    fireEvent.click(await screen.findByText(/single ·/));
    expect(await screen.findByText("25 · 1 in 4,000")).toBeInTheDocument();
    expect(screen.getByText("30 · 1 in 100")).toBeInTheDocument();
  });

  it("keeps DistChart trace refs stable across the 2s sweep-list poll", async () => {
    // Regression: chart inputs were rebuilt inline each render, so App's 2s
    // list poll (refresh -> setSweeps -> re-render) re-ran DistChart's effect
    // and called Plotly.react, wiping a user's legend toggle ~2s after click.
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      withSweep(SINGLE_DECK, {
        buckets: {
          "MAWLER_NORMAL/0/starter/block_first": mkBucket({}),
          "MAWLER_NORMAL/0/starter/min_overblock": mkBucket({}),
        },
        errors_total: 0,
      });
      // Real api.list() returns freshly-parsed JSON each poll (new array ref),
      // so setSweeps always re-renders App. Mirror that here (mockResolvedValue
      // hands back one stable object, which React would bail out of).
      list.mockImplementation(async () => ({ sweeps: [SINGLE_DECK] }));
      render(<App />);
      fireEvent.click(await screen.findByText(/single ·/));
      await screen.findByTestId("dist-chart");
      const before = captured.distTraces.length;
      const tracesRef = captured.distTraces[before - 1];

      // Fire the App-level 2s list poll, which re-renders ResultsView.
      await act(async () => {
        await vi.advanceTimersByTimeAsync(2100);
      });

      // ResultsView re-rendered (DistChart ran again)...
      expect(captured.distTraces.length).toBeGreaterThan(before);
      // ...but with the SAME traces reference (memoized) — so Plotly.react isn't
      // re-invoked and the legend toggle survives.
      expect(captured.distTraces[captured.distTraces.length - 1]).toBe(tracesRef);
    } finally {
      vi.useRealTimers();
    }
  });

  it("loads a single-strategy (all-mcts) sweep without a baseline 422", async () => {
    // Regression: baseline defaulted to "min_overblock"; an all-mcts sweep has
    // no such bucket, so the first results request 422'd, results stayed null,
    // and the baseline could only be fixed after results loaded — a deadlock.
    const MCTS_ONLY: SweepListEntry = {
      ...SINGLE_DECK,
      sweep_id: "mctsonly",
      spec: { ...SINGLE_DECK.spec, name: "mctsonly", strategies: ["mcts"] },
    };
    list.mockResolvedValue({ sweeps: [MCTS_ONLY] });
    // Mimic the backend: reject any baseline not present in the sweep.
    results.mockImplementation(async (_id, opts) => {
      const o = opts as { baseline?: string; baselineDeck?: string } | undefined;
      if (o?.baseline && o.baseline !== "mcts") {
        throw new Error(`422 Unprocessable Content: baseline '${o.baseline}' not found in results`);
      }
      return {
        buckets: { "MAWLER_NORMAL/0/starter/mcts": mkBucket({}) },
        errors_total: 0,
      };
    });

    render(<App />);
    fireEvent.click(await screen.findByText(/mctsonly ·/));

    // The chart renders (no error card), and no baseline was ever sent that the
    // backend would reject.
    expect(await screen.findByTestId("dist-chart")).toBeInTheDocument();
    expect(screen.queryByText(/^Error:/)).toBeNull();
    for (const call of results.mock.calls) {
      const o = call[1] as { baseline?: string } | undefined;
      expect(o?.baseline === undefined || o?.baseline === "mcts").toBe(true);
    }
  });

  it("shows the deck-baseline select only for multi-deck sweeps", async () => {
    withSweep(SINGLE_DECK, {
      buckets: { "MAWLER_NORMAL/0/starter/block_first": mkBucket({}) },
      errors_total: 0,
    });
    render(<App />);
    fireEvent.click(await screen.findByText(/single ·/));
    await screen.findByTestId("dist-chart");
    expect(screen.queryByText(/Baseline deck:/)).toBeNull();
  });

  it("baseline-deck and baseline-strategy are mutually exclusive in the request", async () => {
    withSweep(MULTI_DECK, {
      buckets: {
        "MAWLER_NORMAL/0/starter/block_first": mkBucket({}),
        "MAWLER_NORMAL/0/aggro/block_first": mkBucket({}),
      },
      errors_total: 0,
    });
    render(<App />);
    fireEvent.click(await screen.findByText(/multi ·/));
    await screen.findByTestId("box-chart");

    // Selecting a deck baseline must send baseline_deck and NOT baseline.
    const deckSelect = screen.getByText(/Baseline deck:/).querySelector("select")!;
    fireEvent.change(deckSelect, { target: { value: "aggro" } });
    await waitFor(() => {
      const lastCall = results.mock.calls[results.mock.calls.length - 1];
      expect(lastCall[1]).toMatchObject({ baselineDeck: "aggro" });
      expect((lastCall[1] as { baseline?: string }).baseline).toBeUndefined();
    });
  });
});

describe("ResultsView stop", () => {
  const RUNNING: SweepListEntry = { ...SINGLE_DECK, sweep_id: "run1", status: "running" };

  it("shows a Stop button while running and calls api.stop on click", async () => {
    status.mockResolvedValue({ status: "running", done: 2, total: 10 });
    list.mockResolvedValue({ sweeps: [RUNNING] });
    render(<App />);
    fireEvent.click(await screen.findByText(/single ·/));

    const btn = await screen.findByRole("button", { name: /stop/i });
    fireEvent.click(btn);
    await waitFor(() => expect(stop).toHaveBeenCalledWith("run1"));
  });

  it("renders partial results with a cancelled note when status is cancelled", async () => {
    status.mockResolvedValue({ status: "cancelled", done: 4, total: 10 });
    withSweep({ ...SINGLE_DECK, sweep_id: "can1", status: "cancelled" }, {
      buckets: { "MAWLER_NORMAL/0/starter/block_first": mkBucket({}) },
      errors_total: 0,
    });
    render(<App />);
    fireEvent.click(await screen.findByText(/single ·/));

    // The (partial) chart renders — cancelled is treated like done for display.
    expect(await screen.findByTestId("dist-chart")).toBeInTheDocument();
    // ...with a visible "partial results" note instead of the live progress header.
    expect(screen.getByText(/partial results/i)).toBeInTheDocument();
  });
});
